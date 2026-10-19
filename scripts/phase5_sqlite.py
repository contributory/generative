"""Bounded, offline SQLite checks; no Kotlin execution, decoding, or decryption.

Public results are (PASS|FAIL|REVIEW, diagnostic) pairs. Only literal execSQL
DDL and literal @Query SQL are interpreted. Diagnostics never include SQL,
exception messages, defaults, or row values. Callers supply snapshots of files.
"""

from contextlib import closing
import re
import sqlite3
import time

if __package__:
    from .phase5_fixtures import _spec, _seed_fixtures, _metadata_flags
else:
    from phase5_fixtures import _spec, _seed_fixtures, _metadata_flags

_IDENT = r'(?:`[^`]+`|"(?:""|[^"])+"|\[[^\]]+\]|[A-Za-z_]\w*)'
_DDL = re.compile(r'CREATE\s+(?:UNIQUE\s+)?(TABLE|INDEX)\s+'
                  r'(?:IF\s+NOT\s+EXISTS\s+)?(' + _IDENT + r')', re.I)
_TOKEN = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'
                    r"'(?:\\.|[^'\\])*'|[A-Za-z_]\w*|[^\s]")
_HISTORY = dict(getMessages='messages', getMessageVersions='message_versions',
                getMessageVersionFiles='message_version_files',
                getToolActivities='tool_activities', getToolActivityFiles='tool_activity_files',
                getSummary='conversation_summaries', getMigrationIssues='conversation_migration_quarantine')
_LISTS = {'getConversationListItems', 'getAllConversations', 'getAllProfiles',
          'getAllProviders', 'getProvidersByGroup', 'getUngroupedProviders',
          'getAllServers', 'getAllSpeechServices', 'getAllGroups', 'getAllGroupsOnce'}


class _Problem(Exception):
    pass


def _label(value):
    return re.sub(r'[^A-Za-z0-9_.-]', '?', str(value)[:96])


def _q(name):
    if not isinstance(name, str) or not name or len(name) > 128 or '\0' in name:
        raise _Problem('invalid or oversized SQL identifier')
    return '"' + name.replace('"', '""') + '"'


def _unquote(name):
    return name[1:-1].replace('""', '"') if name[0] in '`"[' else name


def _error(stage, error):
    detail = str(error) if isinstance(error, _Problem) else getattr(error, 'sqlite_errorname', type(error).__name__)
    return 'FAIL', stage + ': ' + detail


def _connect():
    db = sqlite3.connect(':memory:', isolation_level=None, cached_statements=0)
    for pragma in ('foreign_keys=ON', 'temp_store=MEMORY', 'cache_size=-1024', 'max_page_count=4096'):
        db.execute('PRAGMA ' + pragma)
    if hasattr(db, 'setlimit'):
        for limit, value in (('SQLITE_LIMIT_LENGTH', 262144), ('SQLITE_LIMIT_SQL_LENGTH', 65536),
                             ('SQLITE_LIMIT_COLUMN', 256), ('SQLITE_LIMIT_ATTACHED', 0)):
            db.setlimit(getattr(sqlite3, limit), value)
    budget, deadline = [10000], time.monotonic() + 5

    def progress():
        budget[0] -= 1
        return int(budget[0] <= 0 or time.monotonic() > deadline)

    db.set_progress_handler(progress, 1000)
    return db


def _entities(schema):
    entities = schema['database']['entities']
    if not isinstance(entities, list) or not 0 < len(entities) <= 128:
        raise _Problem('database.entities must contain 1..128 entities')
    names = [entity['tableName'] for entity in entities]
    for name in names:
        _q(name)
    if len({name.lower() for name in names}) != len(names):
        raise _Problem('duplicate exported table name')
    return {entity['tableName']: entity for entity in entities}


def _build(db, entities):
    for table, entity in entities.items():
        sql = entity['createSql'].replace('${TABLE_NAME}', table)
        match = _DDL.match(sql.strip())
        if len(sql) > 65536 or not match or match[1].upper() != 'TABLE' or _unquote(match[2]) != table:
            raise _Problem('invalid exported CREATE TABLE for ' + _label(table))
        db.execute(sql)
        indices = entity.get('indices', [])
        if len(indices) > 64:
            raise _Problem('too many exported indexes for ' + _label(table))
        for index in indices:
            sql = index['createSql'].replace('${TABLE_NAME}', table)
            match = _DDL.match(sql.strip())
            if len(sql) > 65536 or not match or match[1].upper() != 'INDEX' or _unquote(match[2]) != index['name']:
                raise _Problem('invalid exported CREATE INDEX for ' + _label(table))
            db.execute(sql)


def _tokens(source):
    if not isinstance(source, str) or len(source) > 2_000_000:
        raise _Problem('source must be text of at most 2000000 characters')
    tokens, pos = [], 0
    while pos < len(source):
        if source.startswith('//', pos):
            end = source.find('\n', pos)
            pos = len(source) if end < 0 else end + 1
        elif source.startswith('/*', pos):
            depth, pos = 1, pos + 2
            while depth:
                mark = re.compile(r'/\*|\*/').search(source, pos)
                if not mark:
                    raise _Problem('unterminated Kotlin comment')
                depth += 1 if mark[0] == '/*' else -1
                pos = mark.end()
        elif source[pos].isspace():
            pos += 1
        else:
            match = _TOKEN.match(source, pos)
            if not match:
                raise _Problem('unsupported Kotlin token')
            tokens.append(match[0])
            pos = match.end()
            if len(tokens) > 100000:
                raise _Problem('source token budget exceeded')
    return tokens


def _literal(token):
    if not token.startswith('"'):
        raise _Problem('nonliteral SQL requires source review')
    raw = token.startswith('"""')
    text = token[3:-3] if raw else token[1:-1]
    if re.search(r'(?<!\\)\$', text) or (raw and '$' in text):
        raise _Problem('interpolated SQL requires source review')
    escapes = {'n': '\n', 'r': '\r', 't': '\t', 'b': '\b', '"': '"', "'": "'", '\\': '\\', '$': '$'}

    def decode(match):
        key = match[1]
        if key.startswith('u') and len(key) == 5:
            return chr(int(key[1:], 16))
        if key not in escapes:
            raise _Problem('unsupported Kotlin string escape')
        return escapes[key]

    return text if raw else re.sub(r'\\(u[0-9a-fA-F]{4}|.)', decode, text)


def _calls(source, kind):
    tokens, calls, reports = _tokens(source), [], []
    for i, token in enumerate(tokens):
        if token != kind or i == 0 or tokens[i - 1] != ('@' if kind == 'Query' else '.'):
            continue
        try:
            if tokens[i + 1] != '(':
                raise _Problem('unsupported SQL call syntax')
            sql, end = _literal(tokens[i + 2]), i + 3
            if tokens[end:end + 4] in (['.', 'trimIndent', '(', ')'], ['.', 'trimMargin', '(', ')']):
                if tokens[end + 1] == 'trimMargin':
                    sql = re.sub(r'(?m)^\s*\|', '', sql)
                end += 4
            if tokens[end] not in (')', ','):
                raise _Problem('composed SQL requires source review')
            name = kind
            if kind == 'Query':
                if tokens[end] != ')':
                    raise _Problem('unsupported @Query annotation arguments')
                end = tokens.index('fun', end + 1, min(len(tokens), end + 80))
                name = tokens[end + 1]
            calls.append((name, sql.strip()))
        except (_Problem, IndexError, ValueError) as error:
            detail = str(error) if isinstance(error, _Problem) else 'could not associate literal with a DAO method'
            reports.append(('REVIEW', kind + ': ' + detail))
    return calls, reports


def _pragma(db, kind, name):
    return db.execute('PRAGMA ' + kind + '(' + _q(name) + ')').fetchall()


def _signature(db, table):
    columns = {row[1]: (row[2].upper(), row[3], row[4], row[5]) for row in _pragma(db, 'table_info', table)}
    indexes = {}
    for row in _pragma(db, 'index_list', table):
        if row[3] != 'pk':
            indexes[row[1]] = (row[2], row[4], tuple(x[2] for x in _pragma(db, 'index_info', row[1])),
                               tuple(x[3:5] for x in _pragma(db, 'index_xinfo', row[1]) if x[5]))
    groups = {}
    for row in _pragma(db, 'foreign_key_list', table):
        groups.setdefault(row[0], []).append(row)
    foreign = sorted((rows[0][2], rows[0][5], rows[0][6], rows[0][7],
                      tuple((r[3], r[4]) for r in sorted(rows, key=lambda r: r[1]))) for rows in groups.values())
    return columns, indexes, foreign


def _coverage(exported, entities, created, calls, previous_schema):
    missing, reports, altered = entities.keys() - created, [], set()
    if previous_schema is None:
        return [('REVIEW', _label(table) + ': uncovered exported table; supply previous_schema to determine required CREATE/ALTER coverage')
                for table in sorted(missing)]
    for _, sql in calls:
        match = re.match(r'ALTER\s+TABLE\s+(' + _IDENT + ')', sql, re.I)
        if match:
            altered.add(_unquote(match[1]))
            rename = re.match(r'\s+RENAME\s+TO\s+(' + _IDENT + ')', sql[match.end():], re.I)
            if rename:
                altered.add(_unquote(rename[1]))
    with closing(_connect()) as previous:
        old = _entities(previous_schema)
        _build(previous, old)
        for table in sorted(missing):
            expected = _signature(exported, table)
            before = _signature(previous, table) if table in old else None
            index_sql = []
            for _, sql in calls:
                create = _DDL.match(sql)
                target = re.search(r'\bON\s+(' + _IDENT + ')', sql, re.I) if create and create[1].upper() == 'INDEX' else None
                drop = re.match(r'DROP\s+INDEX\s+(?:IF\s+EXISTS\s+)?(' + _IDENT + ')', sql, re.I)
                if (target and _unquote(target[1]) == table) or (drop and before and _unquote(drop[1]) in before[1]):
                    index_sql.append(sql)
            if before == expected and not index_sql and table not in altered:
                continue
            if table in altered:
                reports.append(('REVIEW', _label(table) + ': missing literal CREATE; ALTER path requires migration verification against the new export'))
            elif before is None or before[0] != expected[0] or before[2] != expected[2]:
                reports.append(('FAIL', _label(table) + ': ' + ('new' if before is None else 'changed') +
                                ' exported signature; missing required CREATE TABLE or a reviewable literal ALTER path'))
            else:
                for sql in index_sql:
                    try:
                        previous.execute(sql)
                    except sqlite3.Error as error:
                        reports.append(_error('standalone index DDL for ' + _label(table), error))
                if _signature(previous, table) != expected:
                    reports.append(('FAIL', _label(table) + ': changed index signature; missing required CREATE/DROP INDEX DDL or index mismatch'))
                else:
                    reports.append(('PASS', _label(table) + ': standalone index DDL matches exported index signature'))
    return reports


def validate_schema(schema: dict, migration_source: str, previous_schema: dict | None = None) -> list[tuple[str, str]]:
    """Compare literal DDL with Room; use the previous export to audit coverage.

    Uncovered tables without a previous export and literal ALTER paths need REVIEW.
    """
    reports = [('REVIEW', 'Literal DDL comparison only; Kotlin migration, copying and decryption are not executed.')]
    try:
        entities = _entities(schema)
        calls, extraction = _calls(migration_source, 'execSQL')
        reports.extend(extraction)
        ddl = [(match[1].upper(), _unquote(match[2]), sql) for _, sql in calls if (match := _DDL.match(sql))]
        with closing(_connect()) as exported, closing(_connect()) as literal:
            _build(exported, entities)
            tables = {name for kind, name, _ in ddl if kind == 'TABLE'}
            reports.extend(_coverage(exported, entities, tables, calls, previous_schema))
            if not tables:
                reports.append(('REVIEW', 'No literal CREATE TABLE statements extracted from migration.'))
            for kind in ('TABLE', 'INDEX'):
                for item_kind, name, sql in ddl:
                    if item_kind == kind:
                        target = re.search(r'\bON\s+(' + _IDENT + ')', sql, re.I) if kind == 'INDEX' else None
                        if target and _unquote(target[1]) not in tables:
                            reports.append(('REVIEW', 'Index on a table not created by literal migration DDL: ' + _label(name)))
                            continue
                        try:
                            literal.execute(sql)
                        except sqlite3.Error as error:
                            reports.append(_error('literal ' + kind + ' ' + _label(name), error))
            for table in sorted(tables):
                if table not in entities:
                    reports.append(('FAIL', 'migration creates table absent from export: ' + _label(table)))
                    continue
                expected, actual = _signature(exported, table), _signature(literal, table)
                for part, left, right in zip(('columns', 'indexes (excluding PK origin)', 'foreign keys'), expected, actual):
                    if left != right:
                        names = sorted(set(left) ^ set(right) | {k for k in left.keys() & right.keys() if left[k] != right[k]}) if isinstance(left, dict) else []
                        reports.append(('FAIL', _label(table) + ': ' + part + ' mismatch' +
                                        (' [' + ', '.join(map(_label, names)) + ']' if names else '')))
                if expected == actual:
                    reports.append(('PASS', _label(table) + ': columns, indexes and foreign keys match exported schema'))
                if any(index[1] or None in index[2] for index in expected[1].values()):
                    reports.append(('REVIEW', _label(table) + ': partial predicates/index expressions are outside PRAGMA equivalence checks'))
    except (sqlite3.Error, _Problem, KeyError, TypeError, ValueError, AttributeError) as error:
        reports.append(_error('schema comparison', error))
    return reports


def _seed(db, entities):
    return _seed_fixtures(db, entities, _pragma, _q, _label, _Problem)


def _foreign_checks(db, fixtures):
    if db.execute('PRAGMA foreign_key_check').fetchone():
        raise _Problem('foreign_key_check found an invalid scalar fixture (row details withheld)')
    count = 0
    for table, rows in fixtures.items():
        columns = _pragma(db, 'table_info', table)
        keys = [c[1] for c in columns if c[5]] or list(rows[0])
        fks = _pragma(db, 'foreign_key_list', table)
        for fk in (r for r in fks if r[1] == 0):
            rejected = False
            db.execute('SAVEPOINT fk_probe')
            try:
                db.execute('UPDATE ' + _q(table) + ' SET ' + _q(fk[3]) + '=? WHERE ' +
                           ' AND '.join(_q(k) + ' IS ?' for k in keys),
                           ('fixture-absent-parent',) + tuple(rows[0][k] for k in keys))
                db.execute('RELEASE fk_probe')
            except sqlite3.IntegrityError as error:
                rejected = getattr(error, 'sqlite_errorcode', None) == 787 or str(error) == 'FOREIGN KEY constraint failed'
            finally:
                if db.in_transaction:
                    db.execute('ROLLBACK TO fk_probe')
                    db.execute('RELEASE fk_probe')
            if not rejected:
                raise _Problem('orphan update not rejected by declared FK on ' + _label(table) + '.' + _label(fk[3]))
            count += 1
    return 'PASS', 'Foreign keys: clean foreign_key_check; ' + str(count) + ' declared orphan-update probes rejected'


def _sensitive(table, column):
    table, column = (table or '').lower(), re.sub('[^a-z0-9]', '', (column or '').lower())
    # This is a public Boolean policy flag, not the API-key values themselves.
    if table == 'model_providers' and column == 'multipleapikeys':
        return False
    return bool(re.search(r'(^|_)(auth|credentials?|secrets?)(_|$)', table) or
                (column not in {'payloadkind', 'payloadversion'} and
                 re.search(r'content|payload|encrypted|apikey|password|secret|signature|authorization|credential|accesstoken|refreshtoken|privatekey', column)) or
                column in {'arguments', 'response', 'summary', 'rawjson', 'rawvalue', 'scalarvalue',
                           'auth', 'token', 'headers', 'systemprompt', 'reference', 'messagesjson', 'summaryjson', 'configjson',
                           'authjson', 'headersjson', 'cachedtoolsjson', 'modelsjson', 'optionsjson'})


def _authorizer(metadata, denied):
    def authorize(action, first, second, _database, _trigger):
        if action == sqlite3.SQLITE_READ and metadata and _sensitive(first, second):
            denied.append(_label(first) + '.' + _label(second or '*'))
            return sqlite3.SQLITE_DENY
        if action == sqlite3.SQLITE_FUNCTION and (second or '').lower() in ('load_extension', 'readfile', 'writefile'):
            return sqlite3.SQLITE_DENY
        return sqlite3.SQLITE_OK if action in (sqlite3.SQLITE_SELECT, sqlite3.SQLITE_READ,
                                              sqlite3.SQLITE_FUNCTION, sqlite3.SQLITE_RECURSIVE) else sqlite3.SQLITE_DENY
    return authorize


def _root(sql):
    tokens = re.findall(r"'(?:''|[^'])*'|--[^\n]*|/\*[\s\S]*?\*/|" + _IDENT + r'|[^\s]', sql)
    depth = 0
    for i, token in enumerate(tokens[:-1]):
        depth += (token == '(') - (token == ')')
        if depth == 0 and token.upper() == 'FROM':
            table, tail = _unquote(tokens[i + 1]), tokens[i + 2:]
            if tail and tail[0].upper() == 'AS':
                tail = tail[1:]
            alias = _unquote(tail[0]) if tail and re.fullmatch(_IDENT, tail[0]) and tail[0].upper() not in ('WHERE', 'ORDER', 'LIMIT', 'JOIN', 'UNION', 'LEFT', 'INNER', 'GROUP', 'INDEXED', 'NOT', 'CROSS') else table
            return table, alias
    raise _Problem('unsupported SELECT shape; no top-level FROM')


def _query(db, fixtures, name, sql, metadata):
    table, alias = _root(sql)
    if table not in fixtures:
        raise _Problem('query root absent from exported schema: ' + _label(table))
    target = dict(conversationId='fixture-conversation-0', messageId='fixture-message-0', versionIndex=0, activityIndex=0)
    if table == 'message_versions':
        target.pop('versionIndex')
    if table != 'tool_activity_files':
        target.pop('activityIndex')
    if table == 'conversation_migration_quarantine':
        target['conversationId'] = 'fixture-conversation-2'
    code = re.sub(r"'(?:''|[^'])*'|--[^\n]*|/\*[\s\S]*?\*/", '', sql)
    params = set(re.findall(r':([A-Za-z_]\w*)', code))
    sample = fixtures[table][0]
    if not params <= sample.keys() | target.keys():
        raise _Problem('no scalar binding for parameter(s): ' + ', '.join(map(_label, sorted(params - sample.keys() - target.keys()))))
    bindings = {key: target[key] if key in target else sample[key] for key in params}
    expected = [r.copy() for r in fixtures[table] if all(r[k] == v for k, v in
                (bindings if metadata else target).items() if k in r)]
    if name == 'getUngroupedProviders':
        expected = [r for r in expected if r.get('groupId') is None]
    if name == 'getConversationListItems':
        for row in expected:
            row.update(_metadata_flags(fixtures, row['id']))
    order = next((key for key in ('fileIndex', 'activityIndex', 'versionIndex', 'sequence') if key in sample), None)
    if metadata:
        expected.sort(key=lambda r: ((-r['updatedAt'] if table == 'conversations' else r.get('sortOrder', 0)), r.get('id', '')))
    elif order:
        expected.sort(key=lambda r: r[order])
    denied = []
    db.set_authorizer(_authorizer(metadata, denied))
    try:
        cursor = db.execute(sql, bindings)
        actual, columns = cursor.fetchmany(257), [c[0] for c in cursor.description]
        if denied:
            raise _Problem('metadata forbidden read: ' + ', '.join(denied))
        if expected and any(c not in expected[0] for c in columns):
            raise _Problem('projection needs explicit scalar-fixture expectations')
        wanted = [tuple(row[c] for c in columns) for row in expected]
        if (actual != wanted if metadata or order else sorted(actual) != sorted(wanted)):
            raise _Problem('scalar result mismatch (scope, ordering, flags or projection; values withheld)')
        if not metadata:
            plan = db.execute('EXPLAIN QUERY PLAN ' + sql, bindings).fetchall()
            lookup = r'\bSEARCH\s+' + re.escape(alias) + r'\s+USING\s+(?:COVERING\s+)?(?:INDEX\s+\S+|(?:INTEGER\s+)?PRIMARY\s+KEY)'
            if not any(re.search(lookup, p[3], re.I) and 'conversationid=?' in re.sub(r'[\s`"\[\]]', '', p[3].lower()) for p in plan):
                raise _Problem('targeted query plan lacks exported-index SEARCH on conversationId')
    except sqlite3.Error as error:
        if denied:
            raise _Problem('metadata forbidden read (SQLite authorizer): ' + ', '.join(denied)) from None
        raise error
    finally:
        db.set_authorizer(None)
    return 'PASS', _label(name) + (': metadata @Query executed under content/auth authorizer' if metadata else ': targeted @Query results and indexed conversation lookup verified')


def exercise_queries(schema: dict, dao_source: str) -> list[tuple[str, str]]:
    """Execute actual extracted DAO SELECTs on bounded, synthetic scalar fixtures."""
    reports = [('REVIEW', 'Synthetic SQLite SQL checks only; Room mapping, Kotlin migrations and decryption are not executed.')]
    try:
        entities = _entities(schema)
        calls, extraction = _calls(dao_source, 'Query')
        reports.extend(extraction)
        with closing(_connect()) as db:
            _build(db, entities)
            try:
                fixtures = _seed(db, entities)
            except (sqlite3.Error, _Problem, KeyError) as error:
                _, detail = _error('scalar fixture setup needs review', error)
                return reports + [('REVIEW', detail)]
            reports.append(_foreign_checks(db, fixtures))
            covered, metadata_count = set(), 0
            for name, sql in calls:
                if not re.match(r'SELECT\b', sql, re.I):
                    continue
                try:
                    metadata = name in _LISTS or bool(re.search(r'ListItems|Metadata', name))
                    if not metadata and name not in _HISTORY and ':conversationId' not in sql:
                        continue
                    try:
                        table, _ = _root(sql)
                    except _Problem:
                        if metadata or name in _HISTORY:
                            raise
                        continue
                    history = name in _HISTORY or (table in _HISTORY.values() and ':conversationId' in sql)
                    if not (metadata or history):
                        continue
                    metadata_count += metadata
                    if history:
                        covered.add(table)
                    reports.append(_query(db, fixtures, name, sql, metadata))
                except (sqlite3.Error, _Problem, KeyError, TypeError, ValueError) as error:
                    reports.append(_error('DAO ' + _label(name), error))
            if not metadata_count:
                reports.append(('REVIEW', 'No metadata-list @Query methods recognized; DAO source needs review.'))
            if 'conversations' in entities and not any(name == 'getConversationListItems' for name, _ in calls):
                reports.append(('REVIEW', 'Conversation metadata-list method missing or renamed; flag expectations need review.'))
            for table in sorted(set(_HISTORY.values()) & entities.keys() - covered):
                reports.append(('REVIEW', 'No targeted-history @Query recognized for ' + _label(table)))
    except (sqlite3.Error, _Problem, KeyError, TypeError, ValueError, AttributeError) as error:
        reports.append(_error('query exercise', error))
    return reports
