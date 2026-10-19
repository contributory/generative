"""Self-contained fixtures: no app writes, Android runtime, or real payloads."""

import copy
from contextlib import closing
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from phase5_sqlite import exercise_queries, validate_schema, _sensitive
import phase5_sqlite as checks


def entity(table, declaration, indexes=()):
    return {'tableName': table,
            'createSql': 'CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (' + declaration + ')',
            'indices': [{'name': name, 'unique': unique, 'columnNames': columns.split(', '),
                         'orders': [], 'createSql': 'CREATE ' + ('UNIQUE ' if unique else '') +
                         'INDEX IF NOT EXISTS `' + name + '` ON `${TABLE_NAME}` (' + columns + ')'}
                        for name, columns, unique in indexes]}


def schema_fixture():
    conversation_fk = 'FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE'
    message_fk = 'FOREIGN KEY(conversationId, messageId) REFERENCES messages(conversationId, id) ON DELETE CASCADE'
    version_fk = ('FOREIGN KEY(conversationId, messageId, versionIndex) '
                  'REFERENCES message_versions(conversationId, messageId, versionIndex) ON DELETE CASCADE')
    return {'formatVersion': 1, 'database': {'version': 11, 'identityHash': 'synthetic', 'entities': [
        entity('conversations', 'id TEXT NOT NULL, title TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id)',
               [('index_conversations_updatedAt', 'updatedAt, id', False)]),
        entity('messages', 'conversationId TEXT NOT NULL, id TEXT NOT NULL, sequence INTEGER NOT NULL, '
               'role TEXT NOT NULL, PRIMARY KEY(conversationId, id), ' + conversation_fk,
               [('index_messages_sequence', 'conversationId, sequence', True)]),
        entity('message_versions', 'conversationId TEXT NOT NULL, messageId TEXT NOT NULL, '
               'versionIndex INTEGER NOT NULL, content TEXT NOT NULL, reasoningContent TEXT, '
               'PRIMARY KEY(conversationId, messageId, versionIndex), ' + message_fk,
               [('index_versions_message', 'conversationId, messageId', False)]),
        entity('message_version_files', 'conversationId TEXT NOT NULL, messageId TEXT NOT NULL, '
               'versionIndex INTEGER NOT NULL, fileIndex INTEGER NOT NULL, reference TEXT NOT NULL, '
               'PRIMARY KEY(conversationId, messageId, versionIndex, fileIndex), ' + version_fk),
        entity('tool_activities', 'conversationId TEXT NOT NULL, messageId TEXT NOT NULL, '
               'versionIndex INTEGER NOT NULL, activityIndex INTEGER NOT NULL, arguments TEXT NOT NULL, '
               'response TEXT NOT NULL, summary TEXT NOT NULL, '
               'PRIMARY KEY(conversationId, messageId, versionIndex, activityIndex), ' + version_fk),
        entity('tool_activity_files', 'conversationId TEXT NOT NULL, messageId TEXT NOT NULL, '
               'versionIndex INTEGER NOT NULL, activityIndex INTEGER NOT NULL, fileIndex INTEGER NOT NULL, '
               'reference TEXT NOT NULL, PRIMARY KEY(conversationId, messageId, versionIndex, activityIndex, fileIndex), '
               'FOREIGN KEY(conversationId, messageId, versionIndex, activityIndex) '
               'REFERENCES tool_activities(conversationId, messageId, versionIndex, activityIndex) ON DELETE CASCADE'),
        entity('conversation_summaries', 'conversationId TEXT NOT NULL, content TEXT NOT NULL, '
               'throughMessageId TEXT NOT NULL, PRIMARY KEY(conversationId), ' + conversation_fk + ', '
               'FOREIGN KEY(conversationId, throughMessageId) REFERENCES messages(conversationId, id) ON DELETE CASCADE'),
        entity('conversation_migration_quarantine', 'conversationId TEXT NOT NULL, payloadKind TEXT NOT NULL, '
               'rawJson TEXT, errorCode TEXT NOT NULL, PRIMARY KEY(conversationId, payloadKind), ' + conversation_fk),
        entity('provider_auth', 'id TEXT NOT NULL, encryptedPayload TEXT NOT NULL, PRIMARY KEY(id)'),
        entity('model_providers', 'id TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, '
               'authId TEXT, groupId TEXT, baseUrl TEXT NOT NULL, PRIMARY KEY(id), '
               'FOREIGN KEY(authId) REFERENCES provider_auth(id) ON UPDATE CASCADE ON DELETE SET NULL',
               [('index_model_providers_auth', 'authId', False)]),
        entity('chat_profiles', 'id TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id)'),
        entity('chat_profile_configs', 'profileId TEXT NOT NULL, systemPrompt TEXT NOT NULL, PRIMARY KEY(profileId), '
               'FOREIGN KEY(profileId) REFERENCES chat_profiles(id) ON DELETE CASCADE'),
    ]}}


def migration_fixture(schema):
    statements = []
    for item in schema['database']['entities']:
        statements.append(item['createSql'].replace('${TABLE_NAME}', item['tableName']))
        statements.extend(index['createSql'].replace('${TABLE_NAME}', item['tableName']) for index in item['indices'])
    return '\n'.join('db.execSQL("""\n' + sql + '\n""".trimIndent())' for sql in statements)


_METADATA = '''
    SELECT c.id, c.title, c.updatedAt,
        EXISTS(SELECT 1 FROM messages m WHERE m.conversationId = c.id)
            OR EXISTS(SELECT 1 FROM conversation_migration_quarantine q WHERE q.conversationId = c.id
                      AND q.payloadKind IN ('messages_json', 'messages_integrity')) AS hasMessages,
        EXISTS(SELECT 1 FROM conversation_summaries s WHERE s.conversationId = c.id)
            OR EXISTS(SELECT 1 FROM conversation_migration_quarantine q WHERE q.conversationId = c.id
                      AND q.payloadKind IN ('summary_json', 'summary_boundary_missing')) AS hasSummary,
        EXISTS(SELECT 1 FROM conversation_migration_quarantine q WHERE q.conversationId = c.id) AS hasMigrationIssue
    FROM conversations c ORDER BY c.updatedAt DESC, c.id ASC
'''


def query(method, sql):
    return '@Query("""' + sql + '""")\nsuspend fun ' + method + '(): List<ScalarFixture>\n'


def dao_fixture(metadata=_METADATA):
    queries = [query('getConversationListItems', metadata),
               query('getAllProviders', 'SELECT * FROM model_providers ORDER BY sortOrder ASC, id ASC'),
               query('getAllProfiles', 'SELECT * FROM chat_profiles ORDER BY sortOrder ASC, id ASC')]
    for method, table, suffix in [
        ('getMessages', 'messages', 'ORDER BY sequence ASC'),
        ('getMessageVersions', 'message_versions', 'AND messageId = :messageId ORDER BY versionIndex ASC'),
        ('getMessageVersionFiles', 'message_version_files',
         'AND messageId = :messageId AND versionIndex = :versionIndex ORDER BY fileIndex ASC'),
        ('getToolActivities', 'tool_activities',
         'AND messageId = :messageId AND versionIndex = :versionIndex ORDER BY activityIndex ASC'),
        ('getToolActivityFiles', 'tool_activity_files',
         'AND messageId = :messageId AND versionIndex = :versionIndex AND activityIndex = :activityIndex ORDER BY fileIndex ASC'),
        ('getSummary', 'conversation_summaries', ''),
        ('getMigrationIssues', 'conversation_migration_quarantine', ''),
    ]:
        queries.append(query(method, 'SELECT * FROM ' + table + ' WHERE conversationId = :conversationId ' + suffix))
    return '\n'.join(queries)


class Phase5SqliteTests(unittest.TestCase):
    def test_public_multiple_key_policy_is_not_a_secret(self):
        self.assertFalse(_sensitive('model_providers', 'multipleApiKeys'))
        self.assertTrue(_sensitive('provider_auth', 'multipleApiKeys'))
        self.assertTrue(_sensitive('model_providers', 'apiKeys'))
        self.assertTrue(_sensitive('model_providers', 'encryptedPayload'))

    def setUp(self):
        self.schema = schema_fixture()
        self.migration = migration_fixture(self.schema)

    def failures(self, reports):
        self.assertIsInstance(reports, list)
        for status, message in reports:
            self.assertIn(status, ('PASS', 'FAIL', 'REVIEW'))
            self.assertIsInstance(message, str)
        return [message for status, message in reports if status == 'FAIL']

    def test_valid_schema_and_primary_key_origin_indexes(self):
        migration = self.migration.replace('id TEXT NOT NULL, encryptedPayload TEXT NOT NULL, PRIMARY KEY(id)',
                                            'id TEXT NOT NULL PRIMARY KEY, encryptedPayload TEXT NOT NULL')
        reports = validate_schema(self.schema, migration)
        self.assertEqual([], self.failures(reports))
        self.assertEqual(12, sum(status == 'PASS' for status, _ in reports))
        self.assertTrue(any(status == 'REVIEW' and 'Kotlin' in text for status, text in reports))

    def test_removed_new_table_create_cannot_silently_lose_coverage(self):
        previous = copy.deepcopy(self.schema)
        previous['database']['entities'] = [e for e in previous['database']['entities'] if e['tableName'] != 'provider_auth']
        migration = migration_fixture(previous)
        reports = validate_schema(self.schema, migration, previous)
        self.assertTrue(any('provider_auth' in msg and 'missing required CREATE' in msg for msg in self.failures(reports)))
        reports = validate_schema(self.schema, migration)
        self.assertEqual([], self.failures(reports))
        self.assertTrue(any(status == 'REVIEW' and 'provider_auth' in msg and 'uncovered' in msg for status, msg in reports))

    def test_previous_export_excludes_unchanged_tables_from_required_creates(self):
        previous = copy.deepcopy(self.schema)
        previous['database']['entities'] = [e for e in previous['database']['entities'] if e['tableName'] != 'provider_auth']
        new_only = copy.deepcopy(self.schema)
        new_only['database']['entities'] = [e for e in new_only['database']['entities'] if e['tableName'] == 'provider_auth']
        reports = validate_schema(self.schema, migration_fixture(new_only), previous_schema=previous)
        self.assertEqual([], self.failures(reports))
        self.assertFalse(any('uncovered' in msg or 'missing required CREATE' in msg for _, msg in reports))

    def test_changed_column_fk_or_index_signatures_require_migration_ddl(self):
        omitted = copy.deepcopy(self.schema)
        omitted['database']['entities'] = [e for e in omitted['database']['entities'] if e['tableName'] != 'model_providers']
        for part in ('column', 'foreign key', 'index'):
            with self.subTest(part=part):
                previous = copy.deepcopy(self.schema)
                provider = next(e for e in previous['database']['entities'] if e['tableName'] == 'model_providers')
                if part == 'column':
                    provider['createSql'] = provider['createSql'].replace('name TEXT NOT NULL', 'name INTEGER NOT NULL')
                elif part == 'foreign key':
                    provider['createSql'] = provider['createSql'].replace('ON UPDATE CASCADE', 'ON UPDATE NO ACTION')
                else:
                    provider['indices'] = []
                reports = validate_schema(self.schema, migration_fixture(omitted), previous)
                self.assertTrue(any('model_providers' in msg and ('missing required CREATE' in msg or 'index' in msg)
                                    for msg in self.failures(reports)))

    def test_alter_paths_are_actionable_review_not_literal_create_proof(self):
        previous = copy.deepcopy(self.schema)
        provider = next(e for e in previous['database']['entities'] if e['tableName'] == 'model_providers')
        provider['createSql'] = provider['createSql'].replace('name TEXT NOT NULL', 'name INTEGER NOT NULL')
        reports = validate_schema(self.schema, 'db.execSQL("ALTER TABLE `model_providers` RENAME COLUMN name TO oldName")', previous)
        self.assertEqual([], self.failures(reports))
        self.assertTrue(any(status == 'REVIEW' and 'model_providers' in msg and 'ALTER' in msg for status, msg in reports))

    def test_index_only_change_accepts_matching_standalone_index_ddl(self):
        previous = copy.deepcopy(self.schema)
        provider = next(e for e in previous['database']['entities'] if e['tableName'] == 'model_providers')
        provider['indices'] = []
        sql = 'db.execSQL("CREATE INDEX index_model_providers_auth ON model_providers(authId)")'
        self.assertEqual([], self.failures(validate_schema(self.schema, sql, previous)))
        self.assertTrue(self.failures(validate_schema(self.schema, '', previous)))

    def test_altered_columns_report_names_without_defaults(self):
        for change, sql in [('type', 'name INTEGER NOT NULL'), ('nullability', 'name TEXT'),
                            ('default', "name TEXT NOT NULL DEFAULT 'PRIVATE_DEFAULT'")]:
            with self.subTest(change=change):
                migration = self.migration.replace('name TEXT NOT NULL', sql, 1)
                messages = self.failures(validate_schema(self.schema, migration))
                self.assertTrue(any('columns mismatch' in msg and 'name' in msg for msg in messages))
                self.assertNotIn('PRIVATE_DEFAULT', ' '.join(messages))

    def test_foreign_key_actions_and_composite_order_mismatches(self):
        for old, new in [('ON UPDATE CASCADE ON DELETE SET NULL', 'ON UPDATE NO ACTION ON DELETE CASCADE'),
                         ('REFERENCES messages(conversationId, id)', 'REFERENCES messages(id, conversationId)')]:
            with self.subTest(old=old):
                reports = validate_schema(self.schema, self.migration.replace(old, new))
                self.assertTrue(any('foreign keys mismatch' in msg for msg in self.failures(reports)))

    def test_index_unique_column_order_and_direction_mismatches(self):
        original = 'CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_sequence` ON `messages` (conversationId, sequence)'
        variants = [original.replace('UNIQUE ', ''), original.replace('(conversationId, sequence)', '(sequence, conversationId)'),
                    original.replace('(conversationId, sequence)', '(conversationId, sequence DESC)')]
        for variant in variants:
            with self.subTest(variant=variant):
                reports = validate_schema(self.schema, self.migration.replace(original, variant))
                self.assertTrue(any('indexes' in msg and 'index_messages_sequence' in msg for msg in self.failures(reports)))

    def test_extra_missing_tables_and_indexes_are_explicit(self):
        for extra, wanted in [('db.execSQL("CREATE TABLE extra (id TEXT)")', 'absent from export'),
                              ('db.execSQL("CREATE INDEX extra ON model_providers(name)")', 'indexes')]:
            with self.subTest(extra=extra):
                self.assertTrue(any(wanted in msg for msg in self.failures(validate_schema(self.schema, self.migration + extra))))
        migration = self.migration.replace('CREATE INDEX IF NOT EXISTS `index_model_providers_auth` ON `model_providers` (authId)',
                                            'SELECT 1')
        self.assertTrue(any('indexes' in msg for msg in self.failures(validate_schema(self.schema, migration))))

    def test_comments_non_calls_and_dynamic_sql_do_not_become_ddl(self):
        source = self.migration + '''
        /* outer /* nested */ db.execSQL("CREATE TABLE ghost (id TEXT)") */
        // db.execSQL("CREATE TABLE ghost (id TEXT)")
        val ignored = "CREATE TABLE ghost (id TEXT)"
        db.execSQL(dynamicSql)
        db.execSQL("CREATE TABLE $table (id TEXT)")
        '''
        reports = validate_schema(self.schema, source)
        self.assertEqual([], self.failures(reports))
        self.assertTrue(any(status == 'REVIEW' and 'nonliteral' in msg for status, msg in reports))
        self.assertTrue(any(status == 'REVIEW' and 'interpolated' in msg for status, msg in reports))

    def test_valid_queries_check_flags_scopes_indexes_and_foreign_keys(self):
        reports = exercise_queries(self.schema, dao_fixture())
        self.assertEqual([], self.failures(reports))
        self.assertEqual(10, sum(status == 'PASS' and '@Query' in msg for status, msg in reports))
        self.assertTrue(any(status == 'PASS' and 'orphan-update probes rejected' in msg for status, msg in reports))
        self.assertFalse(any('No targeted' in msg for _, msg in reports))

    def test_forbidden_metadata_content_and_auth_reads(self):
        cases = [('(SELECT content FROM message_versions LIMIT 1)', 'message_versions.content'),
                 ('(SELECT reasoningContent FROM message_versions LIMIT 1)', 'message_versions.reasoningContent'),
                 ('(SELECT rawJson FROM conversation_migration_quarantine LIMIT 1)', 'conversation_migration_quarantine.rawJson'),
                 ('(SELECT encryptedPayload FROM provider_auth LIMIT 1)', 'provider_auth.encryptedPayload'),
                 ('(SELECT count(*) FROM provider_auth)', 'provider_auth'),
                 ('(SELECT systemPrompt FROM chat_profile_configs LIMIT 1)', 'chat_profile_configs.systemPrompt')]
        for expression, location in cases:
            with self.subTest(location=location):
                metadata = _METADATA.replace('SELECT c.id,', 'SELECT ' + expression + ' AS leaked, c.id,', 1)
                messages = self.failures(exercise_queries(self.schema, dao_fixture(metadata)))
                self.assertTrue(any('authorizer' in msg and location in msg for msg in messages))
                self.assertNotIn('fixture-content-', ' '.join(messages))

    def test_metadata_where_and_join_reads_are_also_authorized(self):
        metadata = _METADATA.replace('FROM conversations c ORDER',
                                     'FROM conversations c WHERE EXISTS(SELECT 1 FROM provider_auth a WHERE a.id = c.id) ORDER')
        self.assertTrue(any('forbidden read' in msg for msg in self.failures(exercise_queries(self.schema, dao_fixture(metadata)))))
        providers = query('getAllProviders', 'SELECT p.id FROM model_providers p '
                          'JOIN provider_auth a ON p.authId = a.id ORDER BY p.sortOrder, p.id')
        self.assertTrue(any('provider_auth' in msg for msg in self.failures(exercise_queries(self.schema, providers))))

    def test_targeted_queries_reject_cross_conversation_and_sibling_leaks(self):
        cases = [('getMessages', 'messages', 'WHERE 1=1 ORDER BY sequence ASC'),
                 ('getMessageVersions', 'message_versions', 'WHERE conversationId = :conversationId ORDER BY versionIndex ASC'),
                 ('getMessageVersionFiles', 'message_version_files',
                  'WHERE conversationId = :conversationId AND messageId = :messageId ORDER BY fileIndex ASC'),
                 ('getToolActivityFiles', 'tool_activity_files',
                  'WHERE conversationId = :conversationId AND messageId = :messageId AND versionIndex = :versionIndex ORDER BY fileIndex ASC')]
        for method, table, suffix in cases:
            with self.subTest(method=method):
                reports = exercise_queries(self.schema, query(method, 'SELECT * FROM ' + table + ' ' + suffix))
                self.assertTrue(any(method in msg and 'result mismatch' in msg for msg in self.failures(reports)))

    def test_metadata_flags_and_history_order_are_checked(self):
        broken_flags = _METADATA.replace(
            'EXISTS(SELECT 1 FROM conversation_migration_quarantine q WHERE q.conversationId = c.id) AS hasMigrationIssue',
            '0 AS hasMigrationIssue')
        self.assertTrue(any('result mismatch' in msg for msg in self.failures(exercise_queries(self.schema, dao_fixture(broken_flags)))))
        reversed_history = dao_fixture().replace('ORDER BY sequence ASC', 'ORDER BY sequence DESC')
        self.assertTrue(any('getMessages' in msg and 'result mismatch' in msg
                            for msg in self.failures(exercise_queries(self.schema, reversed_history))))

    def test_independent_summary_and_quarantine_flags_reject_cross_wiring(self):
        wrong_summary = _METADATA.replace('FROM conversation_summaries s WHERE s.conversationId = c.id',
                                         'FROM messages s WHERE s.conversationId = c.id')
        wrong_kinds = _METADATA.replace("('summary_json', 'summary_boundary_missing')", "('messages_json', 'messages_integrity')")
        for metadata in (wrong_summary, wrong_kinds):
            with self.subTest(metadata=metadata):
                self.assertTrue(any('getConversationListItems' in msg and 'result mismatch' in msg
                                    for msg in self.failures(exercise_queries(self.schema, dao_fixture(metadata)))))

    def test_ungrouped_provider_query_must_return_actual_null_group_rows(self):
        valid = query('getUngroupedProviders', 'SELECT * FROM model_providers WHERE groupId IS NULL ORDER BY sortOrder ASC, id ASC')
        self.assertEqual([], self.failures(exercise_queries(self.schema, valid)))
        broken = valid.replace('groupId IS NULL', '0')
        self.assertTrue(any('getUngroupedProviders' in msg and 'result mismatch' in msg
                            for msg in self.failures(exercise_queries(self.schema, broken))))

    def test_fixture_states_are_independent_and_preserve_benchmark_populations(self):
        with closing(checks._connect()) as db:
            entities = checks._entities(self.schema)
            checks._build(db, entities)
            fixtures = checks._seed(db, entities)
            history = {r['conversationId'] for r in fixtures['messages']}
            summaries = {r['conversationId'] for r in fixtures['conversation_summaries']}
            self.assertTrue(history - summaries)
            for cid in ('fixture-conversation-0', 'fixture-conversation-1'):
                self.assertEqual(2, sum(r['conversationId'] == cid for r in fixtures['messages']))
            kinds = {}
            for row in fixtures['conversation_migration_quarantine']:
                kinds.setdefault(row['conversationId'], set()).add(row['payloadKind'])
            self.assertTrue(any(values == {'messages_json'} for values in kinds.values()))
            self.assertTrue(any(values == {'summary_json'} for values in kinds.values()))
            groups = [row['groupId'] for row in fixtures['model_providers']]
            self.assertIn(None, groups)
            self.assertTrue(any(group is not None for group in groups))
            self.assertEqual([], db.execute('PRAGMA foreign_key_check').fetchall())

    def test_summary_without_history_is_seeded_where_fk_permits(self):
        for nullable in (False, True):
            with self.subTest(nullable=nullable), closing(checks._connect()) as db:
                schema = copy.deepcopy(self.schema)
                summary = next(e for e in schema['database']['entities'] if e['tableName'] == 'conversation_summaries')
                if nullable:
                    summary['createSql'] = summary['createSql'].replace('throughMessageId TEXT NOT NULL', 'throughMessageId TEXT')
                else:
                    summary['createSql'] = summary['createSql'].replace(
                        ', FOREIGN KEY(conversationId, throughMessageId) REFERENCES messages(conversationId, id) ON DELETE CASCADE', '')
                entities = checks._entities(schema)
                checks._build(db, entities)
                fixtures = checks._seed(db, entities)
                history = {r['conversationId'] for r in fixtures['messages']}
                self.assertTrue(any(r['conversationId'] not in history for r in fixtures['conversation_summaries']))
                self.assertEqual([], db.execute('PRAGMA foreign_key_check').fetchall())
                self.assertEqual([], self.failures(exercise_queries(schema, dao_fixture())))

    def test_same_results_with_unindexed_scan_fail_plan_check(self):
        sql = "SELECT * FROM messages WHERE conversationId || '' = :conversationId ORDER BY sequence ASC"
        messages = self.failures(exercise_queries(self.schema, query('getMessages', sql)))
        self.assertTrue(any('query plan lacks exported-index SEARCH' in msg for msg in messages))

    def test_aliases_and_regular_escaped_kotlin_strings(self):
        sql = 'SELECT * FROM messages AS m WHERE m.conversationId = :conversationId ORDER BY m.sequence ASC'
        dao = '@Query("' + sql + '")\nsuspend fun getMessages(): List<Row>'
        self.assertEqual([], self.failures(exercise_queries(self.schema, dao)))
        source = self.migration.replace('db.execSQL("""\nCREATE TABLE IF NOT EXISTS `provider_auth` '
                                        '(id TEXT NOT NULL, encryptedPayload TEXT NOT NULL, PRIMARY KEY(id))\n""".trimIndent())',
                                        r'db.execSQL("CREATE TABLE IF NOT EXISTS \"provider_auth\" '
                                        r'(id TEXT NOT NULL, encryptedPayload TEXT NOT NULL, PRIMARY KEY(id))")')
        self.assertNotEqual(source, self.migration)
        self.assertEqual([], self.failures(validate_schema(self.schema, source)))

    def test_mutating_dao_queries_are_not_executed(self):
        source = '@Query("DELETE FROM conversations")\nsuspend fun deleteAll()\n' + dao_fixture()
        self.assertEqual([], self.failures(exercise_queries(self.schema, source)))

    def test_unrelated_scalar_and_file_reference_queries_are_ignored(self):
        source = dao_fixture() + query('mentionsFile', 'SELECT EXISTS(SELECT 1 FROM message_version_files WHERE reference = :name)')
        source += query('getAllFileReferences', 'SELECT reference FROM message_version_files UNION ALL SELECT reference FROM tool_activity_files')
        source += query('hasHistory', 'SELECT EXISTS(SELECT 1 FROM messages WHERE conversationId = :conversationId)')
        reports = exercise_queries(self.schema, source)
        self.assertEqual([], self.failures(reports))
        self.assertEqual(10, sum(status == 'PASS' and '@Query' in msg for status, msg in reports))

    def test_missing_or_dynamic_sources_require_review(self):
        self.assertTrue(any(status == 'REVIEW' and 'No literal CREATE TABLE' in msg
                            for status, msg in validate_schema(self.schema, 'db.execSQL(sql)')))
        reports = exercise_queries(self.schema, '@Query(SQL_CONSTANT)\nfun getConversationListItems()')
        self.assertEqual([], self.failures(reports))
        self.assertTrue(any(status == 'REVIEW' and 'No metadata-list' in msg for status, msg in reports))
        self.assertTrue(any('No targeted-history' in msg for _, msg in reports))

    def test_bad_inputs_and_sql_errors_do_not_echo_private_literals(self):
        private = 'NEVER_ECHO_PRIVATE_PAYLOAD'
        for schema in (None, {}, {'database': {'entities': []}}, {'database': {'entities': [None]}}):
            with self.subTest(schema=schema):
                self.assertTrue(self.failures(validate_schema(schema, private)))
                self.assertTrue(self.failures(exercise_queries(schema, private)))
        reports = validate_schema(self.schema, 'db.execSQL("CREATE TABLE invalid (id TEXT DEFAULT \'' + private + '\',)")')
        self.assertTrue(self.failures(reports))
        self.assertNotIn(private, str(reports))
        bad_query = query('getMessages', "SELECT missing_column FROM messages WHERE conversationId = :conversationId AND '" + private + "' != ''")
        reports = exercise_queries(self.schema, bad_query)
        self.assertTrue(self.failures(reports))
        self.assertNotIn(private, str(reports))

    def test_unsupported_fixture_shapes_are_review_not_false_pass(self):
        schema = copy.deepcopy(self.schema)
        schema['database']['entities'].append(entity('cycle', 'id TEXT NOT NULL PRIMARY KEY, parent TEXT, '
                                                    'FOREIGN KEY(parent) REFERENCES cycle(id)'))
        reports = exercise_queries(schema, dao_fixture())
        self.assertEqual([], self.failures(reports))
        self.assertTrue(any(status == 'REVIEW' and 'fixture' in msg and 'cyclic' in msg for status, msg in reports))
        self.assertFalse(any(status == 'PASS' and '@Query' in msg for status, msg in reports))

    def test_unknown_bindings_are_diagnostic(self):
        reports = exercise_queries(self.schema, query('getMessages',
                                   'SELECT * FROM messages WHERE conversationId = :unknownParameter'))
        self.assertTrue(any('unknownParameter' in msg for msg in self.failures(reports)))

    def test_partial_index_equivalence_limit_is_visible(self):
        schema = copy.deepcopy(self.schema)
        index = schema['database']['entities'][0]['indices'][0]
        index['createSql'] += ' WHERE updatedAt > 0'
        reports = validate_schema(schema, migration_fixture(schema))
        self.assertEqual([], self.failures(reports))
        self.assertTrue(any(status == 'REVIEW' and 'partial predicates' in msg for status, msg in reports))

    def test_resource_limits_and_exported_index_sql_validation(self):
        self.assertTrue(any('at most' in msg for msg in self.failures(validate_schema(self.schema, 'x' * 2_000_001))))
        schema = copy.deepcopy(self.schema)
        schema['database']['entities'][0]['indices'][0]['createSql'] = "ATTACH DATABASE 'PRIVATE_PATH' AS external"
        reports = exercise_queries(schema, dao_fixture())
        self.assertTrue(any('invalid exported CREATE INDEX' in msg for msg in self.failures(reports)))
        self.assertNotIn('PRIVATE_PATH', str(reports))
        for name in ('phase5_sqlite.py', 'phase5_fixtures.py', 'test_phase5_sqlite.py'):
            self.assertLessEqual(len(Path(__file__).with_name(name).read_text().splitlines()), 500)


if __name__ == '__main__':
    unittest.main()
