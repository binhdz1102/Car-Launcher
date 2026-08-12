#!/usr/bin/env python3
"""Fail when a candidate changes the launcher manifest or system entry-point contract."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


MANIFEST_FILE = "manifest.xmltree.txt"
ROUTE_FILES = (
    "home-resolution.txt",
    "app-grid-resolution.txt",
    "quickstep-resolution.txt",
)


def normalize_manifest(content: str) -> list[str]:
    """Keep semantic XML tree text while removing line/resource-id build noise."""
    normalized: list[str] = []
    for raw_line in content.splitlines():
        line = re.sub(r" \(line=\d+\)", "", raw_line.rstrip())
        line = re.sub(r"@0x[0-9a-fA-F]+", "@resource", line)
        line = re.sub(r"\(0x[0-9a-fA-F]+\)", "(resource-id)", line)
        line = re.sub(r"android:version(Code|Name).*", "android:version<build-specific>", line)
        if line:
            normalized.append(line)
    return normalized


def normalize_route(content: str) -> list[str]:
    return [line.strip() for line in content.splitlines() if line.strip() and line.strip() != "No result found"]


def compare_file(
    baseline_dir: Path,
    candidate_dir: Path,
    name: str,
    normalizer,
) -> dict[str, object]:
    baseline_path = baseline_dir / name
    candidate_path = candidate_dir / name
    if not baseline_path.exists() or not candidate_path.exists():
        return {
            "file": name,
            "passed": False,
            "reason": "missing artifact",
            "baselineExists": baseline_path.exists(),
            "candidateExists": candidate_path.exists(),
        }
    baseline = normalizer(baseline_path.read_text(encoding="utf-8-sig", errors="replace"))
    candidate = normalizer(candidate_path.read_text(encoding="utf-8-sig", errors="replace"))
    if baseline == candidate:
        return {"file": name, "passed": True}
    return {
        "file": name,
        "passed": False,
        "baselineOnly": [line for line in baseline if line not in candidate],
        "candidateOnly": [line for line in candidate if line not in baseline],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline_dir", type=Path)
    parser.add_argument("candidate_dir", type=Path)
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()

    comparisons = [
        compare_file(arguments.baseline_dir, arguments.candidate_dir, MANIFEST_FILE, normalize_manifest),
        *[
            compare_file(arguments.baseline_dir, arguments.candidate_dir, route, normalize_route)
            for route in ROUTE_FILES
        ],
    ]
    result = {
        "baselineDir": str(arguments.baseline_dir),
        "candidateDir": str(arguments.candidate_dir),
        "comparisons": comparisons,
        "passed": all(item["passed"] for item in comparisons),
    }
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if arguments.output:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
