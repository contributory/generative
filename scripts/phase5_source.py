"""Conservative source guards, not a Kotlin compiler or runtime auth proof."""

import re

_LITERALS = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
_MEMBER = re.compile(r'(?m)^([ \t]*)(?:(?:private|internal|public|protected|suspend|override|inline|tailrec)\s+)*'
                     r'(?:fun\s+(?:[\w<>?]+\.)?(\w+)|(?:val|var)\s+(\w+))\b')
_SECRET = re.compile(r'\b(?:getAuth|decrypt|decryptStrict|decodeEndpointAuth|resolveSecrets|resolveServer|resolveProvider)\s*(?:<[^>\n]+>)?\s*\(')
_SERIALIZE = re.compile(r'\bencodeToString\s*(?:<[^>\n]+>)?\s*\(')
_QUARANTINE = {'conversation_migration_quarantine', 'configuration_migration_quarantine'}


def _mask(source):
    return _LITERALS.sub(lambda m: re.sub(r'[^\n]', ' ', m[0]), source)


def _members(source):
    masked = _mask(source)
    matches = list(_MEMBER.finditer(masked))
    result = {}
    for index, match in enumerate(matches):
        indent = len(match[1].expandtabs(4))
        end = next((m.start() for m in matches[index + 1:] if len(m[1].expandtabs(4)) <= indent), len(source))
        result[match[2] or match[3]] = masked[match.end():end]
    return result


def _encrypted_ranges(source):
    masked, ranges = _mask(source), []
    for match in re.finditer(r'\bSecretCipher\.encrypt\s*\(', masked):
        depth = 1
        for pos in range(match.end(), len(masked)):
            depth += (masked[pos] == '(') - (masked[pos] == ')')
            if depth == 0:
                ranges.append((match.end(), pos))
                break
    return ranges


def _schema_json(schema):
    reports = []
    for entity in schema['database']['entities']:
        table = entity['tableName']
        for field in entity['fields']:
            column = field['columnName']
            if column.lower().endswith('json'):
                if table in _QUARANTINE:
                    reports.append(('PASS', table + '.' + column + ': recovery-only legacy payload, not canonical persistence'))
                else:
                    reports.append(('FAIL', table + '.' + column + ': JSON-backed column remains in exported Room schema'))
    if not any(status == 'FAIL' for status, _ in reports):
        reports.append(('PASS', 'Exported schema contains no normal *Json blob columns (name-based guard).'))
    return reports


def _serialization(sources):
    reports = []
    for path, source in sorted(sources.items()):
        if '/shared/storage/' not in path:
            continue
        masked = _mask(source)
        ranges = _encrypted_ranges(source)
        for match in _SERIALIZE.finditer(masked):
            location = path + ':' + str(source.count('\n', 0, match.start()) + 1)
            if '/preferences/' in path:
                reports.append(('PASS', location + ': preference boundary, outside the Room migration'))
            elif path.endswith('/SecretCipher.kt') and 'Base64.getEncoder()' in source:
                reports.append(('PASS', location + ': Base64 cipher envelope, not JSON object persistence'))
            elif any(start <= match.start() < end for start, end in ranges):
                reports.append(('REVIEW', location + ': encrypted serialization; confirm payload contains only auth/private endpoints'))
            elif '/database/' in path and 'Migration' in path:
                reports.append(('REVIEW', location + ': migration compatibility serialization; inspect destination and encryption'))
            else:
                reports.append(('FAIL', location + ': unclassified object serialization in Room storage; review before release'))
    if not reports:
        reports.append(('REVIEW', 'No storage encodeToString calls recognized; inspect alternate serializers manually.'))
    return reports


def _metadata(sources):
    files = {path.rsplit('/', 1)[-1]: (path, source, _members(source)) for path, source in sources.items()
             if '/shared/storage/' in path and '/database/' not in path and '/preferences/' not in path}
    reports = []
    for filename, root in (('ModelRepository.kt', 'storedProviders'), ('McpRepository.kt', 'stored')):
        if filename not in files or root not in files[filename][2]:
            reports.append(('REVIEW', filename + ': metadata flow shape changed; source inspection required'))
            continue
        pending, seen, failures, unknown = [(filename, root)], set(), [], set()
        while pending:
            file, name = pending.pop()
            if (file, name) in seen:
                continue
            seen.add((file, name))
            path, source, members = files[file]
            body = members[name]
            if _SECRET.search(body):
                failures.append(path + ':' + name)
            for receiver, called in re.findall(r'(?:(\b\w+)\.)?\b(\w+)\s*(?:<[^>\n]+>)?\s*\(', body):
                if called in members and receiver in ('', 'this'):
                    pending.append((file, called))
                elif receiver == 'mapper':
                    mapper = re.search(r'\bval\s+mapper\s*=\s*(\w+)\s*\(', _mask(source))
                    target = mapper[1] + '.kt' if mapper else ''
                    if target in files and called in files[target][2]:
                        pending.append((target, called))
                    else:
                        unknown.add('mapper.' + called)
            for reference in re.findall(r'::(\w+)', body):
                if reference in members:
                    pending.append((file, reference))
        if failures:
            reports.append(('FAIL', filename + ': list mapping reaches credential reads/decryption: ' + ', '.join(sorted(failures))))
        elif unknown:
            reports.append(('REVIEW', filename + ': unresolved metadata mapper calls: ' + ', '.join(sorted(unknown))))
        else:
            reports.append(('REVIEW', filename + ': no credential reads found in recognized metadata mapping; verify with instrumented repository tests'))
    return reports


def _migration_chain(sources, version):
    database = next((source for path, source in sources.items() if path.endswith('/database/AppDatabase.kt')), '')
    code = _mask(database)
    defined = set(re.findall(r'val\s+(MIGRATION_\d+_\d+)\s*=', code))
    registered = set()
    for match in re.finditer(r'\.addMigrations\(([^)]*)\)', code, re.S):
        registered.update(re.findall(r'\bMIGRATION_\d+_\d+\b', match[1]))
    expected = {'MIGRATION_' + str(i) + '_' + str(i + 1) for i in range(1, version)}
    missing = expected - (defined & registered)
    reports = [('FAIL', 'Sequential migration registration missing: ' + ', '.join(sorted(missing)))] if missing else [
        ('PASS', 'Sequential migrations v1 through v' + str(version) + ' are declared and registered (source check).')]
    if re.search(r'\.fallbackToDestructiveMigration\w*\s*\(|\.allowMainThreadQueries\s*\(', code):
        reports.append(('FAIL', 'AppDatabase enables a destructive migration fallback or main-thread queries.'))
    for path, source in sources.items():
        if path.endswith('/database/AppDatabase.kt'):
            continue
        if 'AppDatabase.encryptLegacySecrets(' in _mask(source) and 'encryptLegacySecrets' not in code:
            reports.append(('FAIL', path + ': caller still uses removed encryptLegacySecrets API'))
    return reports


def audit_sources(sources: dict[str, str], schema: dict) -> list[tuple[str, str]]:
    """Check snapshots of public project sources. Results contain no source payloads."""
    reports = _schema_json(schema) + _serialization(sources) + _metadata(sources)
    reports += _migration_chain(sources, schema['database']['version'])
    repository = next((source for path, source in sources.items() if path.endswith('/storage/ConversationRepository.kt')), '')
    members = _members(repository)
    save = members.get('saveConversation', '')
    if re.search(r'\bdeleteMessages\s*\(', save):
        reports.append(('FAIL', 'ConversationRepository.saveConversation still deletes/rebuilds the entire message history; targeted append acceptance remains open.'))
    elif not save:
        reports.append(('REVIEW', 'Conversation write path was renamed/moved; targeted append needs inspection.'))
    rename = members.get('renameConversation', '')
    if re.search(r'\binsertOrUpdate\s*\(', rename):
        reports.append(('FAIL', 'Conversation rename replaces its parent row; cascading history loss is possible.'))
    for path, source in sources.items():
        if '/shared/storage/' not in path and re.search(r'\b(?:conversationRepo|conversationRepository)\.allConversations\b', _mask(source)):
            reports.append(('REVIEW', path + ': full-history compatibility consumer; ensure explicit export only, not startup/list state'))
    reports.append(('REVIEW', 'Source guards are heuristic: alternate JSON serializers, dynamic dispatch, endpoint privacy and selected-operation auth resolution require runtime tests.'))
    return reports
