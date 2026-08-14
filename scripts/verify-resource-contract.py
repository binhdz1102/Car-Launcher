#!/usr/bin/env python3
"""Verify that the AOSP overlayable resource contract is preserved."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


ITEM_PATTERN = re.compile(r'<item\s+type="([^"]+)"\s+name="([^"]+)"\s*/>')


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def item_count(path: Path) -> int:
    return len(ITEM_PATTERN.findall(path.read_text(encoding="utf-8")))


def verify_file(root: Path, reference_root: Path | None, entry: dict[str, object]) -> dict[str, object]:
    candidate = root / str(entry["candidate"])
    expected = {
        "path": str(entry["candidate"]),
        "expectedItemCount": int(entry["itemCount"]),
        "expectedSha256": str(entry["sha256"]),
        "exists": candidate.exists(),
    }
    if candidate.exists():
        expected["itemCount"] = item_count(candidate)
        expected["sha256"] = digest(candidate)
    if reference_root is not None:
        source = reference_root / str(entry["source"])
        expected["sourceExists"] = source.exists()
        if source.exists():
            expected["sourceItemCount"] = item_count(source)
            expected["sourceSha256"] = digest(source)
    expected["passed"] = (
        candidate.exists()
        and expected.get("itemCount") == expected["expectedItemCount"]
        and expected.get("sha256") == expected["expectedSha256"]
    )
    if reference_root is not None and (reference_root / str(entry["source"])).exists():
        expected["passed"] = expected["passed"] and (
            expected.get("sourceItemCount") == expected["expectedItemCount"]
            and expected.get("sourceSha256") == expected["expectedSha256"]
        )
    return expected


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."), help="Candidate repository root")
    parser.add_argument("--reference-root", type=Path, help="Ignored AOSP Launcher root")
    parser.add_argument(
        "--lock",
        type=Path,
        default=Path("contract/aosp-overlayable.lock.json"),
    )
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()
    lock = json.loads(arguments.lock.read_text(encoding="utf-8"))
    results = [
        verify_file(arguments.root, arguments.reference_root, entry)
        for entry in lock["files"]
    ]
    result = {
        "lock": str(arguments.lock),
        "candidateRoot": str(arguments.root),
        "referenceRoot": str(arguments.reference_root) if arguments.reference_root else None,
        "files": results,
        "passed": all(item["passed"] for item in results),
    }
    rendered = json.dumps(result, indent=2, ensure_ascii=False)
    if arguments.output:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
