#!/usr/bin/env python3
"""Small stdlib regression tests for the launcher contract normalizer."""

from __future__ import annotations

import importlib.util
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


if __name__ == "__main__":
    unittest.main()
