#!/usr/bin/env python3
"""Fail-closed checks for the migration's Clean Architecture module boundaries.

The check intentionally validates only boundaries that are mechanically observable from the
repository. Runtime parity remains the responsibility of the AVD runner; this script prevents a
new feature slice from silently reintroducing Android dependencies into domain code or a
presentation-to-data edge.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


PROJECT_DEPENDENCY = re.compile(r'project\("(:[^"\)]+)"\)')


def project_dependencies(path: Path) -> set[str]:
    return {
        match.group(1)
        for match in PROJECT_DEPENDENCY.finditer(path.read_text(encoding="utf-8"))
    }


def domain_android_imports(root: Path) -> list[str]:
    violations: list[str] = []
    for path in root.joinpath("feature").glob("*/domain/src/**/*.kt"):
        text = path.read_text(encoding="utf-8", errors="replace")
        if re.search(r"^\s*import\s+(?:android|androidx)\.", text, re.MULTILINE):
            violations.append(path.relative_to(root).as_posix())
    return sorted(violations)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()

    settings = root / "settings.gradle.kts"
    settings_text = settings.read_text(encoding="utf-8")
    forbidden_common = ":core:common" in settings_text
    presentation_data_edges: list[str] = []
    for path in root.joinpath("feature").glob("*/presentation/build.gradle.kts"):
        if any(
            dependency.endswith(":data")
            for dependency in project_dependencies(path)
        ):
            presentation_data_edges.append(path.relative_to(root).as_posix())

    result = {
        "schemaVersion": 1,
        "rules": {
            "coreCommonModuleRemoved": not forbidden_common,
            "presentationDoesNotDependOnData": not presentation_data_edges,
            "domainDoesNotImportAndroid": not domain_android_imports(root),
        },
        "violations": {
            "coreCommonModule": ["settings.gradle.kts"] if forbidden_common else [],
            "presentationDataEdges": sorted(presentation_data_edges),
            "domainAndroidImports": domain_android_imports(root),
        },
    }
    result["passed"] = all(result["rules"].values())
    rendered = json.dumps(result, indent=2) + "\n"
    if args.output:
        output = args.output if args.output.is_absolute() else root / args.output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
