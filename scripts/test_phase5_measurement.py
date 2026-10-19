import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).with_name('measure-phase5.py')
SPEC = importlib.util.spec_from_file_location('phase5_measurement', SCRIPT)
measurement = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(measurement)


class MeasurementReportTest(unittest.TestCase):
    def test_report_contains_schema_rows_and_selected_history_metrics(self):
        schema = {'database': {'version': 10, 'entities': [
            {'tableName': 'messages', 'fields': [{}, {}], 'indices': [{}], 'foreignKeys': [{}]},
            {'tableName': 'conversations', 'fields': [{}], 'indices': [], 'foreignKeys': []}]}}
        benchmark = {
            'status': 'PASS', 'workload': {'selected_messages_big': 64},
            'runtime': {'python': 'synthetic'}, 'limits': {'rows_exclusive': 10000},
            'methodology': {'timing': 'host observation'}, 'comparisons': {'selected_growth': {'selected_rows_increased': True}},
            'caveats': ['synthetic'], 'errors': [], 'stages': {
                'baseline': {'dataset': {'table_rows': {'conversations': 2, 'messages': 4}, 'rows': 6,
                                          'scalar_bytes': 100, 'sqlite_allocated_bytes': 4096, 'conversations': 2,
                                          'selected_messages': 2, 'unrelated_messages': 2, 'message_body_bytes': 10},
                             'queries': {'getMessages': {'rows': 2, 'bytes': 20, 'fingerprint': 'hash',
                                                         'median_ms': 0.1, 'samples_ms': [0.1],
                                                         'median_vm_steps': 3, 'vm_step_samples': [3]}}}}}
        report = measurement.report_from_benchmark(schema, benchmark)
        self.assertTrue(report['synthetic_only'])
        self.assertEqual(10, report['schema']['room_version'])
        self.assertEqual({'conversations': 2, 'messages': 4}, report['row_counts']['baseline'])
        self.assertEqual(2, report['stages']['baseline']['queries']['getMessages']['rows'])
        self.assertTrue(report['comparisons']['selected_growth']['selected_rows_increased'])


if __name__ == '__main__':
    unittest.main()
