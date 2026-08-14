#!/usr/bin/env python3
import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify-architecture.py")
SPEC = importlib.util.spec_from_file_location("verify_architecture", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class VerifyArchitectureTest(unittest.TestCase):
    def test_project_dependencies_are_parsed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "build.gradle.kts"
            path.write_text(
                'implementation(project(":core:model"))\n'
                'api(project(":feature:home:domain"))\n',
                encoding="utf-8",
            )
            self.assertEqual(
                {":core:model", ":feature:home:domain"},
                MODULE.project_dependencies(path),
            )

    def test_real_tree_has_no_boundary_violations(self) -> None:
        root = SCRIPT.parents[1]
        self.assertFalse(MODULE.domain_android_imports(root))


if __name__ == "__main__":
    unittest.main()
