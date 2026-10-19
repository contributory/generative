#!/usr/bin/env python3
"""Report bounded synthetic schema, row-count, and query measurements."""

import argparse
import json
from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import phase5_benchmark


APP_DATABASE = 'app/src/main/kotlin/org/starfall/multigateway/shared/storage/database/AppDatabase.kt'
DAO = 'app/src/main/kotlin/org/starfall/multigateway/shared/storage/database/dao/Daos.kt'
SCHEMAS = 'app/schemas/org.starfall.multigateway.shared.storage.database.AppDatabase'
_MAX_FILE = 2_000_000


def _read(root, relative):
    candidate = root / relative
    if candidate.is_symlink() or not candidate.resolve().is_relative_to(root):
        raise ValueError('measurement input is outside the workspace or symlinked')
    path = candidate.resolve()
    with path.open('rb') as stream:
        data = stream.read(_MAX_FILE + 1)
    if len(data) > _MAX_FILE:
        raise ValueError('measurement input exceeds the 2 MB bound')
    return data.decode('utf-8')


def _schema_version(database_source):
    match = re.search(r'\bversion\s*=\s*(\d+)', database_source)
    if not match:
        raise ValueError('cannot determine Room schema version')
    return int(match[1])


def _schema_report(schema):
    entities = schema['database']['entities']
    tables = {}
    for entity in entities:
        table = entity['tableName']
        tables[table] = {
            'columns': len(entity.get('fields', [])),
            'indices': len(entity.get('indices', [])),
            'foreign_keys': len(entity.get('foreignKeys', [])),
        }
    return {
        'room_version': schema['database']['version'],
        'table_count': len(tables),
        'tables': dict(sorted(tables.items())),
    }


def report_from_benchmark(schema, result):
    """Keep report fields explicit and payload-free for stable consumers."""
    stages, row_counts = {}, {}
    for name, stage in result.get('stages', {}).items():
        dataset = stage['dataset']
        row_counts[name] = dict(sorted(dataset['table_rows'].items()))
        stages[name] = {
            'dataset': {
                'rows': dataset['rows'],
                'scalar_bytes': dataset['scalar_bytes'],
                'sqlite_allocated_bytes': dataset['sqlite_allocated_bytes'],
                'conversations': dataset['conversations'],
                'selected_messages': dataset['selected_messages'],
                'unrelated_messages': dataset['unrelated_messages'],
                'message_body_bytes': dataset['message_body_bytes'],
            },
            'queries': {
                method: {
                    'rows': metrics['rows'],
                    'bytes': metrics['bytes'],
                    'fingerprint': metrics['fingerprint'],
                    'median_ms': metrics['median_ms'],
                    'samples_ms': metrics['samples_ms'],
                    'median_vm_steps': metrics['median_vm_steps'],
                    'vm_step_samples': metrics['vm_step_samples'],
                }
                for method, metrics in sorted(stage['queries'].items())
            },
        }
    return {
        'status': result['status'],
        'scope': 'Synthetic in-memory host SQLite only; no user database, credentials, Android, Room mapping, migration, or decryption.',
        'synthetic_only': True,
        'schema': _schema_report(schema),
        'row_counts': row_counts,
        'workload': result.get('workload', {}),
        'runtime': result.get('runtime', {}),
        'limits': result.get('limits', {}),
        'methodology': result.get('methodology', {}),
        'stages': stages,
        'comparisons': result.get('comparisons', {}),
        'caveats': result.get('caveats', []),
        'errors': result.get('errors', []),
    }


def measure(root):
    root = root.resolve()
    database_source = _read(root, APP_DATABASE)
    version = _schema_version(database_source)
    schema = json.loads(_read(root, SCHEMAS + '/' + str(version) + '.json'))
    dao_source = _read(root, DAO)
    if schema['database']['version'] != version:
        raise ValueError('exported schema version differs from AppDatabase')
    return report_from_benchmark(schema, phase5_benchmark.benchmark(schema, dao_source))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1],
                        help='workspace root (default: parent of scripts)')
    parser.add_argument('--json', action='store_true', help='emit the complete JSON report')
    args = parser.parse_args(argv)
    try:
        report = measure(args.root)
    except (OSError, UnicodeError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        report = {
            'status': 'FAIL',
            'scope': 'Synthetic in-memory host SQLite only; no user database, credentials, Android, Room mapping, migration, or decryption.',
            'synthetic_only': True,
            'errors': ['measurement input/check failed (' + type(error).__name__ + ')'],
        }
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print(report['scope'])
        print('Synthetic only: ' + str(report.get('synthetic_only', False)))
        if 'schema' in report:
            schema = report['schema']
            print('Room schema v' + str(schema['room_version']) + ': ' + str(schema['table_count']) + ' tables')
            for stage, counts in report.get('row_counts', {}).items():
                print(stage + ' rows: ' + str(sum(counts.values())))
            for stage, details in report.get('stages', {}).items():
                for method, metrics in details['queries'].items():
                    print(stage + ' ' + method + ': rows=' + str(metrics['rows']) +
                          ', bytes=' + str(metrics['bytes']) + ', median_ms=' + str(round(metrics['median_ms'], 6)) +
                          ', median_vm_steps=' + str(metrics['median_vm_steps']))
        for error in report.get('errors', []):
            print('ERROR: ' + error)
        print('Overall: ' + report['status'])
    return 0 if report['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
