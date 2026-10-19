"""Synthetic, bounded benchmark tests; timings and timing ratios are not gates."""

import copy
import json
import math
from pathlib import Path
import sqlite3
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import phase5_benchmark as subject


def _entity(table, declaration, indexes=()):
    return {'tableName': table, 'createSql': 'CREATE TABLE `${TABLE_NAME}` (' + declaration + ')',
            'indices': [{'name': name, 'columnNames': columns.split(', '), 'unique': unique, 'orders': [],
                         'createSql': 'CREATE ' + ('UNIQUE ' if unique else '') +
                         'INDEX `' + name + '` ON `${TABLE_NAME}` (' + columns + ')'}
                        for name, columns, unique in indexes]}


def _schema():
    fk = 'FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE'
    return {'formatVersion': 1, 'database': {'version': 11, 'identityHash': 'synthetic-only', 'entities': [
        _entity('conversations', 'id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, '
                'createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, tokenCount INTEGER, '
                'providerId TEXT NOT NULL, modelId TEXT NOT NULL, profileId TEXT, reasoningEffort TEXT',
                [('index_conversations_updatedAt', 'updatedAt, id', False)]),
        _entity('messages', 'conversationId TEXT NOT NULL, id TEXT NOT NULL, sequence INTEGER NOT NULL, '
                'role TEXT NOT NULL, activeVersionIndex INTEGER NOT NULL, PRIMARY KEY(conversationId, id), ' + fk,
                [('index_messages_conversation_sequence', 'conversationId, sequence', True)]),
        _entity('message_versions', 'conversationId TEXT NOT NULL, messageId TEXT NOT NULL, '
                'versionIndex INTEGER NOT NULL, content TEXT NOT NULL, timestamp TEXT NOT NULL, '
                'reasoningContent TEXT, generationInterrupted INTEGER NOT NULL, '
                'PRIMARY KEY(conversationId, messageId, versionIndex), '
                'FOREIGN KEY(conversationId, messageId) REFERENCES messages(conversationId, id) ON DELETE CASCADE',
                [('index_versions_conversation_message', 'conversationId, messageId', False)]),
        _entity('conversation_summaries', 'conversationId TEXT NOT NULL PRIMARY KEY, content TEXT NOT NULL, '
                'throughMessageId TEXT NOT NULL, ' + fk + ', '
                'FOREIGN KEY(conversationId, throughMessageId) REFERENCES messages(conversationId, id) ON DELETE CASCADE'),
        _entity('conversation_migration_quarantine', 'conversationId TEXT NOT NULL, payloadKind TEXT NOT NULL, '
                'rawJson TEXT, errorCode TEXT NOT NULL, PRIMARY KEY(conversationId, payloadKind), ' + fk),
        _entity('provider_auth', 'id TEXT NOT NULL PRIMARY KEY, encryptedPayload TEXT NOT NULL'),
    ]}}


_METADATA = '''
    SELECT c.id, c.title, c.createdAt, c.updatedAt, c.tokenCount, c.providerId, c.modelId,
        c.profileId, c.reasoningEffort,
        EXISTS(SELECT 1 FROM messages m WHERE m.conversationId = c.id)
            OR EXISTS(SELECT 1 FROM conversation_migration_quarantine q WHERE q.conversationId = c.id
                AND q.payloadKind IN ('messages_json', 'messages_integrity')) AS hasMessages,
        EXISTS(SELECT 1 FROM conversation_summaries s WHERE s.conversationId = c.id)
            OR EXISTS(SELECT 1 FROM conversation_migration_quarantine q WHERE q.conversationId = c.id
                AND q.payloadKind IN ('summary_json', 'summary_boundary_missing')) AS hasSummary,
        EXISTS(SELECT 1 FROM conversation_migration_quarantine q WHERE q.conversationId = c.id) AS hasMigrationIssue
    FROM conversations c ORDER BY c.updatedAt DESC, c.id ASC
'''
_MESSAGES = 'SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sequence ASC'


def _query(method, sql):
    return '@Query("""' + sql + '""")\nsuspend fun ' + method + '(): List<ScalarFixture>\n'


def _dao(metadata=_METADATA, messages=_MESSAGES):
    return _query('getConversationListItems', metadata) + _query('getMessages', messages)


class Phase5BenchmarkTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.success = subject.benchmark(_schema(), _dao())

    def assertFailure(self, result, text):
        self.assertEqual('FAIL', result['status'], result)
        self.assertTrue(any(text in error for error in result['errors']), result['errors'])

    def test_success_and_hard_dataset_bounds(self):
        result = self.success
        self.assertEqual('PASS', result['status'], result['errors'])
        self.assertEqual([], result['errors'])
        self.assertLess(result['elapsed_seconds'], 10)
        self.assertLess(result['limits']['time_budget_seconds'], 10)
        self.assertEqual(3, len(result['stages']))
        for stage in result['stages'].values():
            dataset = stage['dataset']
            self.assertLess(dataset['rows'], 10000)
            self.assertEqual(dataset['rows'], sum(dataset['table_rows'].values()))
            self.assertLess(dataset['scalar_bytes'], 8 * 1024 * 1024)
            self.assertLess(dataset['sqlite_allocated_bytes'], 8 * 1024 * 1024)
            self.assertGreater(dataset['message_body_bytes'], 0)
            self.assertLessEqual(dataset['message_body_bytes'], dataset['scalar_bytes'])

    def test_unrelated_body_growth_preserves_metadata_and_selected_results(self):
        before = self.success['stages']['baseline']
        after = self.success['stages']['after_unrelated_growth']
        self.assertTrue(all(self.success['comparisons']['unrelated_growth'].values()))
        self.assertEqual(before['dataset']['conversations'], after['dataset']['conversations'])
        self.assertEqual(before['dataset']['selected_messages'], after['dataset']['selected_messages'])
        self.assertEqual(512, after['dataset']['unrelated_messages'] - before['dataset']['unrelated_messages'])
        self.assertEqual(512 * 2048, after['dataset']['message_body_bytes'] - before['dataset']['message_body_bytes'])
        self.assertEqual(1024, after['dataset']['rows'] - before['dataset']['rows'])
        for method in ('getConversationListItems', 'getMessages'):
            for metric in ('rows', 'bytes', 'fingerprint'):
                self.assertEqual(before['queries'][method][metric], after['queries'][method][metric])

    def test_bigger_selected_conversation_returns_more_rows(self):
        small = self.success['stages']['after_unrelated_growth']
        big = self.success['stages']['after_selected_growth']
        self.assertEqual(2, small['queries']['getMessages']['rows'])
        self.assertEqual(64, big['queries']['getMessages']['rows'])
        self.assertEqual(64, big['dataset']['selected_messages'])
        self.assertGreater(big['queries']['getMessages']['bytes'], small['queries']['getMessages']['bytes'])
        self.assertEqual(small['dataset']['unrelated_messages'], big['dataset']['unrelated_messages'])
        self.assertTrue(all(value for key, value in self.success['comparisons']['selected_growth'].items()
                            if key.endswith(('unchanged', 'increased'))))

    def test_repeated_timing_and_vm_observations_have_no_ratio_gate(self):
        for stage in self.success['stages'].values():
            for measurement in stage['queries'].values():
                self.assertEqual(5, measurement['repeats'])
                self.assertEqual(5, len(measurement['samples_ms']))
                self.assertEqual(5, len(measurement['vm_step_samples']))
                self.assertEqual(1, measurement['vm_progress_interval'])
                self.assertTrue(all(math.isfinite(value) and value >= 0 for value in measurement['samples_ms']))
                self.assertTrue(all(isinstance(value, int) and value > 0 for value in measurement['vm_step_samples']))
                self.assertIn(measurement['median_ms'], measurement['samples_ms'])
                self.assertIn(measurement['median_vm_steps'], measurement['vm_step_samples'])

    def test_runtime_and_caveats_are_serializable_and_payload_free(self):
        self.assertEqual(sys.version.split()[0], self.success['runtime']['python'])
        self.assertEqual(sqlite3.sqlite_version, self.success['runtime']['sqlite'])
        self.assertIn('not Android performance', self.success['scope'])
        serialized = json.dumps(self.success)
        self.assertNotIn('fixture-conversation-', serialized)
        self.assertNotIn('B' * 128, serialized)
        self.assertTrue(any('envelopes' in caveat for caveat in self.success['caveats']))

    def test_missing_queries_have_actionable_method_names(self):
        for method, source in [('getConversationListItems', _query('getMessages', _MESSAGES)),
                               ('getMessages', _query('getConversationListItems', _METADATA))]:
            with self.subTest(method=method):
                result = subject.benchmark(_schema(), source)
                self.assertFailure(result, 'Missing literal @Query SQL for ' + method)
                self.assertEqual({}, result['stages'])
                self.assertIn('supply DAO text', ' '.join(result['errors']))

    def test_dynamic_and_duplicate_queries_are_not_guessed(self):
        dynamic = _query('getConversationListItems', _METADATA) + '@Query(SQL_CONSTANT)\nfun getMessages()'
        self.assertFailure(subject.benchmark(_schema(), dynamic), 'getMessages')
        duplicate = _dao() + _query('getMessages', _MESSAGES)
        self.assertFailure(subject.benchmark(_schema(), duplicate), 'Ambiguous duplicate')

    def test_actual_query_limit_and_scope_changes_are_exercised(self):
        self.assertFailure(subject.benchmark(_schema(), _dao(messages=_MESSAGES + ' LIMIT 1')), 'omitted synthetic rows')
        unscoped = 'SELECT * FROM messages WHERE :conversationId IS NOT NULL ORDER BY sequence ASC'
        self.assertFailure(subject.benchmark(_schema(), _dao(messages=unscoped)), 'query scope')
        renamed_parameter = _MESSAGES.replace(':conversationId', ':anotherId')
        self.assertFailure(subject.benchmark(_schema(), _dao(messages=renamed_parameter)), 'bind only :conversationId')

    def test_valid_alias_projection_uses_actual_dao_sql(self):
        selected = 'SELECT m.id, m.conversationId FROM messages AS m WHERE m.conversationId = :conversationId ORDER BY m.sequence DESC'
        result = subject.benchmark(_schema(), _dao(messages=selected))
        self.assertEqual('PASS', result['status'], result['errors'])
        projected = result['stages']['baseline']['queries']['getMessages']
        original = self.success['stages']['baseline']['queries']['getMessages']
        self.assertEqual(original['rows'], projected['rows'])
        self.assertLess(projected['bytes'], original['bytes'])

    def test_forbidden_metadata_reads_are_rejected(self):
        for expression in ('(SELECT content FROM message_versions LIMIT 1)',
                           '(SELECT encryptedPayload FROM provider_auth LIMIT 1)'):
            with self.subTest(expression=expression):
                metadata = _METADATA.replace('SELECT c.id,', 'SELECT ' + expression + ' AS leaked, c.id,', 1)
                self.assertFailure(subject.benchmark(_schema(), _dao(metadata=metadata)), 'forbidden metadata read')

    def test_schema_errors_are_actionable_without_private_literals(self):
        schema = _schema()
        schema['database']['entities'] = [item for item in schema['database']['entities'] if item['tableName'] != 'message_versions']
        self.assertFailure(subject.benchmark(schema, _dao()), 'requires exported table message_versions')
        schema = _schema()
        schema['database']['entities'][2]['createSql'] = schema['database']['entities'][2]['createSql'].replace('content TEXT', 'renamedBody TEXT')
        self.assertFailure(subject.benchmark(schema, _dao()), 'requires message_versions columns')
        private = 'NEVER_RETURN_PRIVATE_SQL_LITERAL'
        bad_sql = "SELECT missing_column AS id FROM messages WHERE conversationId=:conversationId AND '" + private + "' != ''"
        result = subject.benchmark(_schema(), _dao(messages=bad_sql))
        self.assertEqual('FAIL', result['status'])
        self.assertNotIn(private, json.dumps(result))

    def test_cooperative_time_and_vm_budgets_stop_with_diagnostics(self):
        with patch.object(subject.time, 'monotonic', side_effect=(100.0, 109.0, 109.0)):
            result = subject.benchmark(_schema(), _dao())
        self.assertFailure(result, 'time budget exhausted')
        self.assertEqual({}, result['stages'])
        with patch.object(subject, '_MAX_STEPS', 1):
            result = subject.benchmark(_schema(), _dao())
        self.assertFailure(result, 'VM-step budget exhausted')

    def test_inputs_unchanged_and_files_within_line_limit(self):
        schema = _schema()
        before = copy.deepcopy(schema)
        result = subject.benchmark(schema, _dao())
        self.assertEqual('PASS', result['status'], result['errors'])
        self.assertEqual(before, schema)
        for filename in ('phase5_benchmark.py', 'test_phase5_benchmark.py'):
            self.assertLessEqual(len(Path(__file__).with_name(filename).read_text().splitlines()), 500)


if __name__ == '__main__':
    unittest.main()
