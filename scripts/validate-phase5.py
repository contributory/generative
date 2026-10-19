#!/usr/bin/env python3
"""Read-only Phase 5 checks. Never invokes Gradle, Android, network, or user DBs."""

import argparse
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import sys
from types import ModuleType

# Identify the code the interpreter actually loaded, not a later disk revision.
_DRIVER_CODE = sys._getframe().f_code

APP = 'app/src/main/kotlin/org/starfall/multigateway'
DATABASE = APP + '/shared/storage/database/AppDatabase.kt'
DAO = APP + '/shared/storage/database/dao/Daos.kt'
SCHEMAS = 'app/schemas/org.starfall.multigateway.shared.storage.database.AppDatabase'
MEASUREMENT = 'scripts/measure-phase5.py'
_MAX_FILE = 2_000_000
_MAX_TOTAL = 32_000_000


def read_bounded(path):
    with path.open('rb') as stream:
        data = stream.read(_MAX_FILE + 1)
    if len(data) > _MAX_FILE:
        raise ValueError('Source input exceeds 2 MB bound.')
    return data


def paths_for(root, version):
    paths = {path for folder in ('app/src/main/kotlin', 'app/src/test', 'app/src/androidTest')
             for path in (root / folder).rglob('*.kt')}
    paths.update((root / 'scripts').glob('phase5_*.py'))
    paths.add(root / 'scripts/validate-phase5.py')
    if (root / MEASUREMENT).exists():
        paths.add(root / MEASUREMENT)
    paths.add(root / SCHEMAS / (str(version) + '.json'))
    if version > 1:
        paths.add(root / SCHEMAS / (str(version - 1) + '.json'))
    if len(paths) > 2000:
        raise ValueError('Source snapshot exceeds 2000-file bound.')
    return sorted(paths)


def capture(root):
    database = read_bounded(root / DATABASE).decode('utf-8')
    match = re.search(r'\bversion\s*=\s*(\d+)', database)
    if not match:
        raise ValueError('Cannot determine current Room schema version.')
    version = int(match[1])
    sources, hashes, total = {}, {}, 0
    for path in paths_for(root, version):
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError('Snapshot refuses symlinked inputs or paths outside the workspace.')
        if path.stat().st_size > _MAX_FILE:
            raise ValueError('Source input exceeds 2 MB bound.')
        data = read_bounded(path)
        total += len(data)
        if len(data) > _MAX_FILE or total > _MAX_TOTAL:
            raise ValueError('Source snapshot exceeds size bounds.')
        name = path.relative_to(root).as_posix()
        sources[name] = data.decode('utf-8')
        hashes[name] = hashlib.sha256(data).hexdigest()
    if sources.get(DATABASE) != database:
        raise ValueError('AppDatabase changed while capturing inputs; retry after edits settle.')
    return version, sources, hashes


def unchanged(root, version, hashes):
    current = {path.relative_to(root).as_posix() for path in paths_for(root, version)}
    if current != set(hashes):
        return False
    for path, expected in hashes.items():
        file = root / path
        if file.is_symlink() or not file.resolve().is_relative_to(root) or file.stat().st_size > _MAX_FILE or hashlib.sha256(read_bounded(file)).hexdigest() != expected:
            return False
    return True


def migration_source(sources, version):
    """Join the helper set for the current Room version transition.

    v9 -> v10 is split between conversation/configuration dispatchers and the
    ConfigurationMigration10* helpers. Keep the selection version-aware so a
    future transition does not accidentally validate an empty or stale helper
    set.
    """
    if version <= 1:
        return ''
    transition = str(version - 1) + 'To' + str(version)
    names = {
        'ConversationMigration' + transition + '.kt',
        'ConfigurationMigration' + transition + '.kt',
    }
    prefix = 'ConfigurationMigration' + str(version)
    selected = [source for path, source in sorted(sources.items())
                if '/shared/storage/database/' in path and (filename := path.rsplit('/', 1)[-1]) and
                (filename in names or
                 filename.startswith(prefix) and path.endswith('.kt') and
                 not re.fullmatch(r'ConfigurationMigration\d+To\d+\.kt', filename))]
    return '\n'.join(selected)


@contextmanager
def snapshot_checkers(root, sources, include_benchmark=False):
    """Execute exactly the captured public checker code, including its helpers."""
    driver = compile(sources['scripts/validate-phase5.py'], _DRIVER_CODE.co_filename, 'exec', dont_inherit=True)
    if driver != _DRIVER_CODE:
        raise ValueError('Running validator differs from the snapshotted driver; restart it.')
    names = ['phase5_fixtures', 'phase5_sqlite', 'phase5_source']
    if include_benchmark:
        names.append('phase5_benchmark')
    previous = {name: sys.modules.get(name) for name in names}
    loaded = {}
    try:
        for name in names:
            filename = 'scripts/' + name + '.py'
            module = ModuleType(name)
            module.__file__, module.__package__ = str(root / filename), ''
            sys.modules[name] = module
            exec(compile(sources[filename], module.__file__, 'exec', dont_inherit=True), module.__dict__)
            loaded[name] = module
        yield loaded
    finally:
        for name, module in previous.items():
            if module is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = module


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1], help='workspace root (default: parent of scripts)')
    parser.add_argument('--json', action='store_true', help='print JSON including the SHA-256 input manifest; writes no files')
    parser.add_argument('--benchmark', action='store_true', help='opt in to bounded synthetic in-memory host SQLite timings')
    args = parser.parse_args(argv)
    root = args.root.resolve()
    result = {'scope': 'Offline source/SQL checks only; not Android/Room migration or device-performance acceptance.',
              'runtime': {'python': sys.version.split()[0], 'sqlite': sqlite3.sqlite_version},
              'checks': [], 'benchmark': None, 'input_hashes': {}}
    try:
        version, sources, hashes = capture(root)
        result['schema_version'] = version
        result['input_hashes'] = hashes
        result['snapshot_sha256'] = hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()
        schema = json.loads(sources[SCHEMAS + '/' + str(version) + '.json'])
        if schema['database']['version'] != version:
            raise ValueError('Exported schema version differs from AppDatabase version.')
        # The current v9 -> v10 migration is split across dispatchers and
        # ConfigurationMigration10* helpers. Select it from the captured source
        # snapshot instead of assuming a single migration file.
        migration = migration_source(sources, version)
        previous_schema = json.loads(sources[SCHEMAS + '/' + str(version - 1) + '.json']) if version > 1 else None
        with snapshot_checkers(root, sources, args.benchmark) as modules:
            checks = modules['phase5_source'].audit_sources(sources, schema)
            checks += modules['phase5_sqlite'].validate_schema(schema, migration, previous_schema)
            checks += modules['phase5_sqlite'].exercise_queries(schema, sources[DAO])
            if args.benchmark:
                result['benchmark'] = modules['phase5_benchmark'].benchmark(schema, sources[DAO])
                checks.append((result['benchmark']['status'], 'Opt-in synthetic host SQLite benchmark (not Android timings).'))
        checks.append(('REVIEW', 'Not run: Kotlin/Room compilation, repository/instrumentation suites, real migration fidelity, cold startup and first-send timings. Gradle prohibited.'))
        if unchanged(root, version, hashes):
            checks.append(('PASS', 'All captured inputs unchanged at end of check; SHA-256 manifest identifies this snapshot.'))
        else:
            checks.append(('FAIL', 'Concurrent edits detected: validation snapshot is stale. Rerun after relevant edits settle.'))
        result['checks'] = [dict(status=status, message=message) for status, message in checks]
    except (OSError, UnicodeError, ValueError, KeyError, TypeError, SyntaxError) as error:
        # Never emit an exception's text: it can contain source or SQL payloads.
        result['checks'].append(dict(status='FAIL', message='Input snapshot/check failed (' + type(error).__name__ + '); verify expected files and retry after concurrent edits settle.'))
    statuses = {check['status'] for check in result['checks']}
    result['status'] = 'FAIL' if 'FAIL' in statuses else 'REVIEW' if 'REVIEW' in statuses else 'PASS'
    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print(result['scope'])
        print('Snapshot SHA-256: ' + result.get('snapshot_sha256', 'unavailable'))
        for check in result['checks']:
            print('[' + check['status'] + '] ' + check['message'])
        if result['benchmark'] is not None:
            print('Synthetic host SQLite benchmark (use --json for full samples/limits):')
            for name, stage in result['benchmark']['stages'].items():
                dimensions = stage['dataset']
                print('  ' + name + ': ' + str(dimensions['conversations']) + ' conversations, ' +
                      str(dimensions['message_body_bytes']) + ' body bytes')
                for method, metrics in stage['queries'].items():
                    print('    ' + method + ': rows=' + str(metrics['rows']) + ', bytes=' + str(metrics['bytes']) +
                          ', median_ms=' + str(round(metrics['median_ms'], 6)) + ', VM steps=' + str(metrics['median_vm_steps']))
            for error in result['benchmark']['errors']:
                print('  ' + error)
        print('Overall: ' + result['status'] + ' (0=PASS, 1=FAIL, 2=REVIEW; not release approval)')
    return 1 if result['status'] == 'FAIL' else 2 if result['status'] == 'REVIEW' else 0


if __name__ == '__main__':
    raise SystemExit(main())
