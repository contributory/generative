"""Independent, scalar-only SQLite fixtures and metadata expectations.

Database helpers are injected by phase5_sqlite, preserving its _seed/_spec API
without a circular import. Conversations 0/1 retain two messages each for the
benchmark; all extra states remain small and contain no real application data.
"""

from itertools import product


def _spec(table):
    if table == 'conversations':
        return [dict(id='fixture-conversation-' + str(i)) for i in range(9)]
    if table == 'conversation_summaries':
        return [dict(conversationId='fixture-conversation-0', throughMessageId='fixture-message-0')]
    if table == 'conversation_migration_quarantine':
        kinds = ('messages_json', 'summary_json', 'messages_integrity', 'summary_boundary_missing', 'other_issue')
        return [dict(conversationId='fixture-conversation-' + str(i), payloadKind=kind)
                for i, kind in enumerate(kinds, 2)]
    if table == 'model_providers':
        return [{}, {'groupId': None}]
    shapes = {'messages': ('id',), 'message_versions': ('messageId', 'versionIndex'),
              'message_version_files': ('messageId', 'versionIndex', 'fileIndex'),
              'tool_activities': ('messageId', 'versionIndex', 'activityIndex'),
              'tool_activity_files': ('messageId', 'versionIndex', 'activityIndex', 'fileIndex')}
    if table not in shapes:
        return [{}, {}]
    rows = []
    for values in product((1, 0), repeat=len(shapes[table]) + 1):
        row = dict(conversationId='fixture-conversation-' + str(values[0]))
        row.update((key, 'fixture-message-' + str(value) if key in ('id', 'messageId') else value)
                   for key, value in zip(shapes[table], values[1:]))
        if table == 'messages':
            row['sequence'] = values[1]
        rows.append(row)
    return rows


def _summary_without_history(groups, columns):
    candidate = dict(conversationId='fixture-conversation-8', throughMessageId='fixture-message-0')
    for group in groups:
        if group[0][2] != 'messages':
            continue
        nullable = [fk[3] for fk in group if fk[3] != 'conversationId' and not columns[fk[3]][3]]
        if not nullable:
            return []  # A required history boundary forbids a summary-only row.
        candidate[nullable[0]] = None  # Any NULL child column disables a composite FK.
    return [candidate]


def _seed_fixtures(db, entities, pragma, quote, label, problem):
    fixtures, pending = {}, dict(entities)
    while pending:
        ready = False
        for table in list(pending):
            fks = pragma(db, 'foreign_key_list', table)
            if any(fk[2] not in fixtures for fk in fks):
                continue
            columns, rows = pragma(db, 'table_info', table), []
            groups = [[fk for fk in fks if fk[0] == identity] for identity in sorted({fk[0] for fk in fks})]
            specs = _spec(table)
            if table == 'model_providers' and 'groupId' not in {c[1] for c in columns}:
                specs = [{}, {}]
            if table == 'conversation_summaries':
                specs += _summary_without_history(groups, {c[1]: c for c in columns})
            for i, overrides in enumerate(specs):
                row = {c[1]: (i if 'INT' in c[2].upper() else float(i) if c[2].upper() in ('REAL', 'FLOAT', 'DOUBLE')
                              else 'fixture-' + c[1] + '-' + str(i)) for c in columns}
                if not overrides.keys() <= row.keys():
                    raise problem('fixture keys no longer match ' + label(table))
                row.update(overrides)
                for group in groups:
                    if any(fk[3] in overrides and overrides[fk[3]] is None for fk in group):
                        continue
                    parents = [p for p in fixtures[group[0][2]]
                               if all(fk[3] not in overrides or overrides[fk[3]] == p[fk[4]] for fk in group)]
                    if not parents:
                        raise problem('cannot link scalar fixture for ' + label(table))
                    row.update((fk[3], parents[i % len(parents)][fk[4]]) for fk in group)
                db.execute('INSERT INTO ' + quote(table) + ' (' + ','.join(map(quote, row)) + ') VALUES (' +
                           ','.join('?' for _ in row) + ')', tuple(row.values()))
                rows.append(row)
            fixtures[table] = rows
            del pending[table]
            ready = True
        if not ready:
            raise problem('cyclic, self-referencing or missing-parent fixture dependencies')
    return fixtures


def _metadata_flags(fixtures, conversation_id):
    kinds = {row['payloadKind'] for row in fixtures.get('conversation_migration_quarantine', [])
             if row['conversationId'] == conversation_id}
    history = any(row['conversationId'] == conversation_id for row in fixtures.get('messages', []))
    summary = any(row['conversationId'] == conversation_id for row in fixtures.get('conversation_summaries', []))
    return dict(hasMessages=int(history or bool(kinds & {'messages_json', 'messages_integrity'})),
                hasSummary=int(summary or bool(kinds & {'summary_json', 'summary_boundary_missing'})),
                hasMigrationIssue=int(bool(kinds)))
