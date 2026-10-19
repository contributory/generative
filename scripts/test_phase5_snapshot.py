"""Captured checkers must be the code executed, not earlier sys.modules entries."""

import importlib.util
from pathlib import Path
import sys
from types import ModuleType
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name('validate-phase5.py')
SPEC = importlib.util.spec_from_file_location('phase5_driver_tests', SCRIPT)
driver = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(driver)


def sources():
    return {
        'scripts/validate-phase5.py': SCRIPT.read_text(),
        'scripts/phase5_fixtures.py': 'marker = "captured fixture"',
        'scripts/phase5_sqlite.py': 'import phase5_fixtures\nmarker = phase5_fixtures.marker',
        'scripts/phase5_source.py': 'def audit_sources(*args): return [("FAIL", "captured checker")]'}


class SnapshotCheckerTest(unittest.TestCase):
    def test_executes_captured_code_not_preimported_revision(self):
        stale = ModuleType('phase5_source')
        stale.audit_sources = lambda *args: [('PASS', 'wrong revision')]
        with patch.dict(sys.modules, {'phase5_source': stale}):
            with driver.snapshot_checkers(SCRIPT.parents[1], sources()) as modules:
                self.assertEqual([('FAIL', 'captured checker')], modules['phase5_source'].audit_sources())
                self.assertEqual('captured fixture', modules['phase5_sqlite'].marker)
            self.assertIs(stale, sys.modules['phase5_source'])

    def test_different_checkout_uses_that_checkouts_captured_helpers(self):
        captured = sources()
        captured['scripts/phase5_fixtures.py'] = 'marker = "different checkout"'
        with driver.snapshot_checkers(Path('another-checkout'), captured) as modules:
            self.assertEqual('different checkout', modules['phase5_sqlite'].marker)

    def test_changed_driver_requires_restart(self):
        captured = sources()
        captured['scripts/validate-phase5.py'] = captured['scripts/validate-phase5.py'].replace('_MAX_FILE = 2_000_000', '_MAX_FILE = 3_000_000')
        with self.assertRaises(ValueError):
            with driver.snapshot_checkers(SCRIPT.parents[1], captured):
                self.fail('Must reject a changed driver')

    def test_module_registry_restored_after_load_error(self):
        captured = sources()
        captured['scripts/phase5_sqlite.py'] = 'raise ValueError("fixture loader failure")'
        previous = {name: sys.modules.get(name) for name in ('phase5_fixtures', 'phase5_sqlite', 'phase5_source')}
        with self.assertRaises(ValueError):
            with driver.snapshot_checkers(SCRIPT.parents[1], captured):
                self.fail('Must reject a failing checker')
        self.assertEqual(previous, {name: sys.modules.get(name) for name in previous})

    def test_benchmark_not_loaded_unless_requested(self):
        captured = sources()
        captured['scripts/phase5_benchmark.py'] = 'raise ValueError("benchmark executed")'
        with driver.snapshot_checkers(SCRIPT.parents[1], captured) as modules:
            self.assertNotIn('phase5_benchmark', modules)
        with self.assertRaises(ValueError):
            with driver.snapshot_checkers(SCRIPT.parents[1], captured, True):
                self.fail('Opted-in benchmark source must be used')


if __name__ == '__main__':
    unittest.main()
