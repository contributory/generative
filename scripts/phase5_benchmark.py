"""Opt-in synthetic host SQLite evidence. Importing this module runs no workload.

The parent CLI calls benchmark() only for --benchmark. No user databases, files,
Kotlin, Room mapping, or decryption are executed. Byte counts are scalar payload
sizes, not Python object sizes; SQLite page allocation is reported separately.
"""

from contextlib import closing
import hashlib
import re
import sqlite3
import statistics
import struct
import sys
import time

if __package__:
    from . import phase5_sqlite as checks
else:
    import phase5_sqlite as checks

_REPEATS = 5
_MAX_BYTES = 8 * 1024 * 1024
_MAX_ROWS = 10000
_SECONDS = 8.0
_MAX_STEPS = 2_000_000
_BODY_BYTES = 2048
_UNRELATED = 512
_SELECTED_FINAL = 64
_METHODS = ('getConversationListItems', 'getMessages')


class _Budget:
    def __init__(self):
        self.started = time.monotonic()
        self.deadline = self.started + _SECONDS
        self.steps = self.query_steps = 0
        self.interval = 1000

    def check(self):
        if time.monotonic() >= self.deadline:
            raise checks._Problem('eight-second benchmark time budget exhausted; workload stopped')
        if self.steps >= _MAX_STEPS:
            raise checks._Problem('benchmark VM-step budget exhausted; inspect DAO query scope/indexes')

    def progress(self):
        self.steps += self.interval
        self.query_steps += self.interval
        return int(time.monotonic() >= self.deadline or self.steps >= _MAX_STEPS)

    def install(self, db, interval=1000):
        self.check()
        self.interval, self.query_steps = interval, 0
        db.set_progress_handler(self.progress, interval)


def _payload(value):
    if value is None:
        return b'n', b'', 0
    if isinstance(value, int):
        return b'i', str(value).encode('ascii'), 8
    if isinstance(value, float):
        return b'f', struct.pack('!d', value), 8
    if isinstance(value, bytes):
        return b'b', value, len(value)
    if isinstance(value, str):
        encoded = value.encode('utf-8')
        return b's', encoded, len(encoded)
    raise checks._Problem('unsupported synthetic scalar type')


def _row_bytes(row):
    return sum(_payload(value)[2] for value in row.values())


def _queries(source):
    calls, reviews = checks._calls(source, 'Query')
    queries = {}
    for method, table, parameters in zip(_METHODS, ('conversations', 'messages'), (set(), {'conversationId'})):
        matches = [sql for name, sql in calls if name == method]
        if len(matches) != 1:
            raise checks._Problem(('Missing literal @Query SQL for ' if not matches else 'Ambiguous duplicate @Query methods for ') +
                                  method + '; supply DAO text with exactly one literal SELECT for this method')
        sql = matches[0]
        if len(sql) > 65536 or not re.match(r'SELECT\b', sql, re.I) or checks._root(sql)[0] != table:
            raise checks._Problem(method + ' must be a literal SELECT rooted at ' + table)
        code = re.sub(r"'(?:''|[^'])*'|--[^\n]*|/\*[\s\S]*?\*/", '', sql)
        if set(re.findall(r':([A-Za-z_]\w*)', code)) != parameters:
            raise checks._Problem(method + (' must bind only :conversationId' if parameters else ' must not require SQL parameters'))
        queries[method] = sql
    return queries, [message for _, message in reviews]


def _read(db, sql, bindings, expected_ids, conversation, budget):
    cursor = db.execute(sql, bindings)
    columns = [column[0] for column in cursor.description]
    required = {'id', 'conversationId'} if conversation is not None else {'id'}
    if not required <= set(columns) or len(set(columns)) != len(columns):
        raise checks._Problem('DAO projection must expose unique id' + (' and conversationId' if conversation is not None else '') + ' columns')
    digest, count, size, seen = hashlib.sha256(), 0, 0, set()
    digest.update(repr(columns).encode('utf-8'))
    for row in cursor:
        budget.check()
        count += 1
        if count >= _MAX_ROWS:
            raise checks._Problem('query result exceeded the exclusive 10000-row bound')
        item_id = row[columns.index('id')]
        if item_id not in expected_ids or item_id in seen:
            raise checks._Problem('DAO query returned unexpected/duplicate conversation or message rows; inspect query scope')
        if conversation is not None and row[columns.index('conversationId')] != conversation:
            raise checks._Problem('getMessages query scope includes an unrelated conversation; require a targeted :conversationId predicate')
        seen.add(item_id)
        digest.update(b'row')
        for value in row:
            tag, encoded, scalar_bytes = _payload(value)
            size += scalar_bytes
            if size >= _MAX_BYTES:
                raise checks._Problem('query result exceeded the exclusive 8 MiB scalar-data bound')
            digest.update(tag + len(encoded).to_bytes(8, 'big'))
            digest.update(encoded)
    if seen != expected_ids:
        raise checks._Problem('DAO query omitted synthetic rows; remove unintended filters/LIMIT or update fixture support')
    return {'rows': count, 'bytes': size, 'fingerprint': digest.hexdigest()}


def _measure(db, method, sql, expected_ids, conversation, budget):
    bindings = {} if conversation is None else {'conversationId': conversation}
    denied, times, steps = [], [], []
    db.set_authorizer(checks._authorizer(conversation is None, denied))
    try:
        budget.install(db)
        reference = _read(db, sql, bindings, expected_ids, conversation, budget)  # One warmup.
        for _ in range(_REPEATS):
            budget.install(db)
            start = time.perf_counter_ns()
            measured = _read(db, sql, bindings, expected_ids, conversation, budget)
            times.append((time.perf_counter_ns() - start) / 1_000_000)
            budget.install(db, 1)  # Separate instrumentation run; not part of timed sample.
            counted = _read(db, sql, bindings, expected_ids, conversation, budget)
            steps.append(budget.query_steps)
            if measured != reference or counted != reference:
                raise checks._Problem(method + ' produced inconsistent synthetic results across repeats')
        return dict(reference, median_ms=statistics.median(times), samples_ms=times,
                    median_vm_steps=statistics.median(steps), vm_step_samples=steps,
                    vm_progress_interval=1, repeats=_REPEATS)
    except sqlite3.Error:
        if denied:
            raise checks._Problem(method + ' forbidden metadata read (SQLite authorizer): ' + ', '.join(denied)) from None
        budget.check()
        raise
    finally:
        db.set_authorizer(None)
        budget.install(db)


def _insert(db, table, row):
    db.execute('INSERT INTO ' + checks._q(table) + ' (' + ','.join(map(checks._q, row)) + ') VALUES (' +
               ','.join('?' for _ in row) + ')', tuple(row.values()))


def _grow(db, fixtures, conversation, count, label, body, budget):
    templates = [row for row in fixtures['messages'] if row['conversationId'] == conversation]
    template = templates[0]
    version = next(row for row in fixtures['message_versions']
                   if row['conversationId'] == conversation and row['messageId'] == template['id'])
    start = max(row['sequence'] for row in templates) + 1
    added_bytes, ids = 0, set()
    with db:  # Explicit transaction below: helper connections use autocommit.
        db.execute('BEGIN')
        for number in range(count):
            budget.check()
            identity = 'benchmark-' + label + '-' + str(number)
            message = dict(template, conversationId=conversation, id=identity, sequence=start + number)
            if 'activeVersionIndex' in message:
                message['activeVersionIndex'] = 0
            content = dict(version, conversationId=conversation, messageId=identity, versionIndex=0, content=body)
            _insert(db, 'messages', message)
            _insert(db, 'message_versions', content)
            added_bytes += _row_bytes(message) + _row_bytes(content)
            ids.add(identity)
    return added_bytes, ids


def _dimensions(db, entities, scalar_bytes, selected, unrelated, budget):
    budget.check()
    counts = {table: db.execute('SELECT count(*) FROM ' + checks._q(table)).fetchone()[0] for table in entities}
    allocated = db.execute('PRAGMA page_count').fetchone()[0] * db.execute('PRAGMA page_size').fetchone()[0]
    if sum(counts.values()) >= _MAX_ROWS or max(allocated, scalar_bytes) >= _MAX_BYTES:
        raise checks._Problem('synthetic dataset exceeded exclusive 10000-row/8 MiB bounds; schema needs a smaller fixture')
    scoped = lambda cid: db.execute('SELECT count(*) FROM messages WHERE conversationId=?', (cid,)).fetchone()[0]
    if db.execute('PRAGMA foreign_key_check').fetchone():
        raise checks._Problem('synthetic workload failed foreign_key_check; inspect schema/fixture compatibility')
    return {'rows': sum(counts.values()), 'scalar_bytes': scalar_bytes, 'sqlite_allocated_bytes': allocated,
            'table_rows': counts, 'conversations': counts['conversations'],
            'selected_messages': scoped(selected), 'unrelated_messages': scoped(unrelated),
            'message_body_bytes': db.execute('SELECT coalesce(sum(length(CAST(content AS BLOB))), 0) FROM message_versions').fetchone()[0]}


def benchmark(schema: dict, dao_source: str) -> dict:
    """Measure supplied DAO SELECTs on synthetic data, only when explicitly called.

    Returns JSON-serializable metrics or status=FAIL with actionable, payload-free
    errors. Timings/VM steps are observations, never performance pass thresholds.
    """
    budget = _Budget()
    result = {
        'status': 'FAIL', 'scope': 'opt-in synthetic host SQLite evidence; not Android performance',
        'runtime': {'python': sys.version.split()[0], 'python_implementation': sys.implementation.name,
                    'sqlite': sqlite3.sqlite_version, 'platform': sys.platform},
        'limits': {'data_bytes_exclusive': _MAX_BYTES, 'rows_exclusive': _MAX_ROWS,
                   'time_budget_seconds': _SECONDS, 'vm_step_budget': _MAX_STEPS, 'repeats': _REPEATS},
        'methodology': {
            'timing': 'one warmup then five execute/fetch/scalar-accounting wall-time samples; median milliseconds',
            'vm_steps': 'five separate progress-handler runs per query/stage, interval=1; prepare/fetch callbacks counted',
            'bytes': 'UTF-8 TEXT/BLOB lengths, 8 bytes per numeric scalar, zero per NULL; framing/object overhead excluded',
            'fingerprints': 'ordered synthetic result identity including column names; no row values returned'},
        'caveats': [
            'Synthetic in-memory host SQLite only; no user databases, Android, Room mapping, Kotlin migration or decryption.',
            'Host scheduling, warm caches, Python accounting and SQLite versions affect timings; no timing-ratio assertions.',
            'Bodies are in message_versions.content; the actual getMessages DAO may return only message envelopes.',
            'SQLite progress steps measure instrumented VM work, not Android CPU, I/O, memory or UI latency.',
            'The eight-second cooperative deadline and VM/page/row limits abort incompatible or excessive workloads.'],
        'stages': {}, 'comparisons': {}, 'errors': []}
    stage = 'DAO extraction'
    try:
        queries, result['source_reviews'] = _queries(dao_source)
        budget.check()
        stage = 'schema/fixture setup'
        entities = checks._entities(schema)
        required = {'conversations': {'id'}, 'messages': {'id', 'conversationId', 'sequence'},
                    'message_versions': {'conversationId', 'messageId', 'versionIndex', 'content'}}
        for table in required:
            if table not in entities:
                raise checks._Problem('benchmark requires exported table ' + table + '; supply the current relational Room schema')
        with closing(checks._connect()) as db:
            budget.install(db)
            page_size = db.execute('PRAGMA page_size').fetchone()[0]
            db.execute('PRAGMA max_page_count=' + str((_MAX_BYTES - 1) // page_size))
            checks._build(db, entities)
            for table, columns in required.items():
                if not columns <= {row[1] for row in checks._pragma(db, 'table_info', table)}:
                    raise checks._Problem('benchmark fixture requires ' + table + ' columns: ' + ', '.join(sorted(columns)))
            fixtures = checks._seed(db, entities)
            body = 'B' * _BODY_BYTES
            db.execute('UPDATE message_versions SET content=?', (body,))
            for row in fixtures['message_versions']:
                row['content'] = body
            populated = {row['conversationId'] for row in fixtures['messages']}
            candidates = [row['id'] for row in fixtures['conversations'] if row['id'] in populated]
            if len(candidates) < 2:
                raise checks._Problem('benchmark needs two already-populated synthetic conversations; update scalar fixture support')
            selected, unrelated = candidates[:2]
            conversation_ids = {row['id'] for row in fixtures['conversations']}
            selected_ids = {row['id'] for row in fixtures['messages'] if row['conversationId'] == selected}
            selected_added = _SELECTED_FINAL - len(selected_ids)
            if selected_added <= 0:
                raise checks._Problem('small selected fixture is already too large; update benchmark fixture dimensions')
            scalar_bytes = sum(_row_bytes(row) for rows in fixtures.values() for row in rows)
            if sum(map(len, fixtures.values())) + 2 * (_UNRELATED + selected_added) >= _MAX_ROWS:
                raise checks._Problem('planned synthetic workload exceeds the exclusive 10000-row bound')
            result['workload'] = {'conversations': len(conversation_ids), 'body_bytes_per_version': _BODY_BYTES,
                                  'unrelated_messages_added': _UNRELATED, 'selected_messages_added': selected_added,
                                  'selected_messages_small': len(selected_ids), 'selected_messages_big': _SELECTED_FINAL,
                                  'new_versions_per_new_message': 1}

            def snapshot(name):
                budget.check()
                dimensions = _dimensions(db, entities, scalar_bytes, selected, unrelated, budget)
                measurements = {
                    _METHODS[0]: _measure(db, _METHODS[0], queries[_METHODS[0]], conversation_ids, None, budget),
                    _METHODS[1]: _measure(db, _METHODS[1], queries[_METHODS[1]], selected_ids, selected, budget)}
                result['stages'][name] = {'dataset': dimensions, 'queries': measurements}

            stage = 'baseline queries'
            snapshot('baseline')
            stage = 'unrelated-message growth'
            added, _ = _grow(db, fixtures, unrelated, _UNRELATED, 'unrelated', body, budget)
            scalar_bytes += added
            snapshot('after_unrelated_growth')
            stage = 'selected-conversation growth'
            added, identities = _grow(db, fixtures, selected, selected_added, 'selected', body, budget)
            scalar_bytes += added
            selected_ids.update(identities)
            snapshot('after_selected_growth')
            baseline, grown, bigger = [result['stages'][key] for key in
                                        ('baseline', 'after_unrelated_growth', 'after_selected_growth')]
            unchanged = {}
            for method, label in zip(_METHODS, ('metadata', 'selected')):
                before, after = baseline['queries'][method], grown['queries'][method]
                for metric in ('rows', 'bytes', 'fingerprint'):
                    unchanged[label + '_' + metric + '_unchanged'] = before[metric] == after[metric]
            result['comparisons']['unrelated_growth'] = unchanged
            small, big = grown['queries'][_METHODS[1]], bigger['queries'][_METHODS[1]]
            result['comparisons']['selected_growth'] = {
                'metadata_rows_unchanged': grown['queries'][_METHODS[0]]['rows'] == bigger['queries'][_METHODS[0]]['rows'],
                'selected_rows_small': small['rows'], 'selected_rows_big': big['rows'],
                'selected_rows_increased': big['rows'] > small['rows'],
                'selected_result_bytes_increased': big['bytes'] > small['bytes']}
            if not all(unchanged.values()):
                raise checks._Problem('metadata/selected results changed after unrelated growth; inspect DAO scoping and metadata projection')
            if not result['comparisons']['selected_growth']['selected_rows_increased']:
                raise checks._Problem('getMessages did not scale to the larger selected conversation; inspect DAO filters/LIMIT')
            budget.check()
            result['status'] = 'PASS'
    except (checks._Problem, sqlite3.Error, KeyError, TypeError, ValueError, AttributeError, StopIteration) as error:
        if isinstance(error, sqlite3.Error):
            try:
                budget.check()
            except checks._Problem as stopped:
                error = stopped
        _, diagnostic = checks._error('benchmark ' + stage, error)
        result['errors'].append(diagnostic)
    result['elapsed_seconds'] = time.monotonic() - budget.started
    return result
