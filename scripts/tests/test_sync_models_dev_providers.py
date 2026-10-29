"""Offline regression checks for GitHub Pages provider-catalog fallback."""

import contextlib
import importlib.util
import io
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "sync-models-dev-providers.py"
spec = importlib.util.spec_from_file_location("provider_catalog_sync", SCRIPT)
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


def fake_api_response():
    return {
        f"service-{n}": {
            "name": f"Provider {n}",
            "api": f"https://api-{n}.example.com/v1",
            "npm": "@ai-sdk/openai-compatible",
            "models": {"test-model": {}},
        }
        for n in range(12)
    }


class ProviderSyncTest(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory()
        self.addCleanup(self.tempdir.cleanup)
        self.path = Path(self.tempdir.name) / "providers-data.json"
        patcher = patch.object(sync, "DEST", self.path)
        patcher.start()
        self.addCleanup(patcher.stop)
        self.cached = sync.build_catalog(fake_api_response())
        self.path.write_text(json.dumps(self.cached), encoding="utf-8")
        self.original = self.path.read_bytes()

    def run_sync(self, result):
        with patch.object(sync, "fetch_catalog", side_effect=result if isinstance(result, Exception) else None, return_value=None if isinstance(result, Exception) else result):
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                status = sync.main()
        return status, output.getvalue()

    def test_success_replaces_cached_data(self):
        fresh = fake_api_response()
        fresh["service-0"]["name"] = "Renamed provider"
        status, logs = self.run_sync(fresh)
        self.assertEqual(status, 0)
        self.assertIn("Wrote 12 providers", logs)
        self.assertNotEqual(self.path.read_bytes(), self.original)
        payload = json.loads(self.path.read_text())
        self.assertEqual(payload["count"], 12)
        self.assertEqual(payload["providers"][0]["name"], "Provider 1")
        self.assertTrue(any(p["name"] == "Renamed provider" for p in payload["providers"]))

    def test_http_error_uses_cached_catalog(self):
        status, logs = self.run_sync(urllib.error.HTTPError(sync.SOURCE, 503, "Unavailable", None, None))
        self.assertEqual(status, 0)
        self.assertIn("::warning::", logs)
        self.assertIn("cached catalog", logs)
        self.assertEqual(self.path.read_bytes(), self.original)

    def test_network_timeout_uses_cached_catalog(self):
        status, logs = self.run_sync(TimeoutError("Connection timed out"))
        self.assertEqual(status, 0)
        self.assertIn("cached catalog", logs)
        self.assertEqual(self.path.read_bytes(), self.original)

    def test_bad_response_uses_cached_catalog(self):
        for invalid in ({}, [], {"unexpected": 42}, {"provider": {"name": "Invalid"}}):
            with self.subTest(data=invalid):
                status, logs = self.run_sync(invalid)
                self.assertEqual(status, 0)
                self.assertIn("cached catalog", logs)
                self.assertEqual(self.path.read_bytes(), self.original)

    def test_invalid_json_uses_cached_catalog(self):
        status, logs = self.run_sync(json.JSONDecodeError("Bad JSON", "{", 1))
        self.assertEqual(status, 0)
        self.assertIn("cached catalog", logs)
        self.assertEqual(self.path.read_bytes(), self.original)

    def test_missing_fallback_reports_failure(self):
        self.path.unlink()
        status, logs = self.run_sync(urllib.error.URLError("Offline"))
        self.assertEqual(status, 1)
        self.assertIn("no usable cached catalog", logs)
        self.assertFalse(self.path.exists())

    def test_corrupted_fallback_reports_failure(self):
        self.path.write_text("not JSON", encoding="utf-8")
        status, logs = self.run_sync(TimeoutError("Offline"))
        self.assertEqual(status, 1)
        self.assertIn("no usable cached catalog", logs)
        self.assertEqual(self.path.read_text(), "not JSON")


if __name__ == "__main__":
    unittest.main()
