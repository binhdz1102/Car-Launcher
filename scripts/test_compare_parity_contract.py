#!/usr/bin/env python3
"""Small stdlib regression tests for the launcher contract normalizer."""

from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("compare-parity-contract.py")
SPEC = importlib.util.spec_from_file_location("compare_parity_contract", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ManifestNormalizerTest(unittest.TestCase):
    def test_ignores_support_library_entries_and_resource_ids(self) -> None:
        stock = """
        E: activity (line=1)
          A: android:name(0x01010003)="com.android.car.carlauncher.CarLauncher"
          E: meta-data (line=2)
            A: android:name(0x01010003)="com.android.car.ui.core.CarUiInstaller"
            A: android:value(0x01010024)=@0x7f010001
        """
        candidate = stock.replace("(line=1)", "(line=99)").replace("@0x7f010001", "@0x7f090001")
        self.assertEqual(MODULE.normalize_manifest(stock), MODULE.normalize_manifest(candidate))

    def test_keeps_launcher_owned_contract_changes(self) -> None:
        baseline = 'A: android:name="com.android.car.carlauncher.WidgetHostActivity"'
        candidate = 'A: android:name="com.android.car.carlauncher.OtherActivity"'
        self.assertNotEqual(MODULE.normalize_manifest(baseline), MODULE.normalize_manifest(candidate))

    def test_capture_schema_is_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            capture_path = Path(directory) / "capture.json"
            capture_path.write_text(json.dumps({"status": "PASS", "schemaVersion": 1}))
            status = MODULE.read_capture_status(Path(directory))
            self.assertEqual(status["status"], "INVALID")

    def test_contract_file_comparison_reports_added_and_removed_nodes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            baseline_dir = Path(directory) / "baseline"
            candidate_dir = Path(directory) / "candidate"
            baseline_dir.mkdir()
            candidate_dir.mkdir()
            (baseline_dir / "capture.json").write_text(json.dumps({"status": "PASS", "schemaVersion": 2}))
            (candidate_dir / "capture.json").write_text(json.dumps({"status": "PASS", "schemaVersion": 2}))
            (baseline_dir / "contract.txt").write_text("resource 0x7f010001 string/title\n")
            (candidate_dir / "contract.txt").write_text("resource 0x7f010001 string/title\nresource 0x7f010002 string/extra\n")
            result = MODULE.compare_file(
                baseline_dir,
                candidate_dir,
                "contract.txt",
                MODULE.normalize_resources,
            )
            self.assertFalse(result["passed"])
            self.assertEqual(result["candidateOnlyCount"], 1)


if __name__ == "__main__":
    unittest.main()
