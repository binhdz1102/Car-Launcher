#!/usr/bin/env python3
"""Compare the complete launcher APK contract captured from an AVD run."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Callable


MANIFEST_FILE = "manifest.xmltree.txt"
RESOURCE_FILE = "resources.txt"
UI_FILE = "window.xml"
ROUTE_FILES = (
    "home-resolution.txt",
    "app-grid-resolution.txt",
    "quickstep-resolution.txt",
)

BUILD_SPECIFIC = re.compile(
    r"(android:version(?:Code|Name)|android:compileSdkVersion(?:Codename)?|"
    r"android:minSdkVersion|android:targetSdkVersion|platformBuildVersion(?:Code|Name))"
    r"(?:\([^)]*\))?(?:@resource)?=.*"
)
LINE_NUMBER = re.compile(r"\s+\(line=\d+\)")
RESOURCE_ID = re.compile(r"@0x[0-9a-fA-F]+|\(0x[0-9a-fA-F]+\)")
RESOURCE_NAME = re.compile(r"\bresource\s+0x[0-9a-fA-F]+\s+([^\s]+)")


def normalize_manifest(content: str) -> list[str]:
    """Normalize only build-generated noise; preserve all contract attributes."""
    normalized: list[str] = []
    for raw_line in content.splitlines():
        line = LINE_NUMBER.sub("", raw_line.rstrip())
        line = RESOURCE_ID.sub("@resource", line)
        line = BUILD_SPECIFIC.sub(r"\1=<build-specific>", line)
        line = re.sub(r"\s+", " ", line).strip()
        if line:
            normalized.append(line)
    return sorted(normalized)


def normalize_resources(content: str) -> list[str]:
    """Reduce aapt2's resource dump to stable type/name declarations."""
    names: list[str] = []
    for raw_line in content.splitlines():
        match = RESOURCE_NAME.search(raw_line)
        if match:
            names.append(match.group(1))
    return sorted(names)


def normalize_route(content: str) -> list[str]:
    return [line.strip() for line in content.splitlines() if line.strip()]


def normalize_ui(content: str) -> list[str]:
    """Compare resource-addressable semantic nodes and their rendered bounds.

    uiautomator assigns volatile indexes and drawing orders, so those fields are intentionally
    excluded. Resource ids, class, text/content descriptions and interaction flags are part of the
    public UI contract and must remain stable. Nodes without a resource id are framework wrappers
    and are not useful parity anchors.
    """
    root = ET.fromstring(content)
    nodes: list[str] = []
    for node in root.iter("node"):
        attributes = node.attrib
        resource_id = attributes.get("resource-id", "")
        if not resource_id:
            continue
        if ":id/" in resource_id:
            resource_id = resource_id.split(":id/", 1)[1]
        fields = (
            resource_id,
            attributes.get("class", ""),
            attributes.get("text", ""),
            attributes.get("content-desc", ""),
            attributes.get("bounds", ""),
            attributes.get("enabled", ""),
            attributes.get("focusable", ""),
            attributes.get("clickable", ""),
            attributes.get("scrollable", ""),
        )
        nodes.append("|".join(fields))
    return sorted(nodes)


def compare_file(
    baseline_dir: Path,
    candidate_dir: Path,
    name: str,
    normalizer: Callable[[str], list[str]],
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
    baseline_only = [line for line in baseline if line not in candidate]
    candidate_only = [line for line in candidate if line not in baseline]
    passed = not baseline_only and not candidate_only
    result: dict[str, object] = {
        "file": name,
        "passed": passed,
        "baselineCount": len(baseline),
        "candidateCount": len(candidate),
        "baselineOnlyCount": len(baseline_only),
        "candidateOnlyCount": len(candidate_only),
    }
    if not passed:
        result["baselineOnly"] = baseline_only[:2000]
        result["candidateOnly"] = candidate_only[:2000]
    return result


def read_capture_status(directory: Path) -> dict[str, object]:
    path = directory / "capture.json"
    if not path.exists():
        return {"status": "INVALID", "reason": "missing capture.json"}
    metadata = json.loads(path.read_text(encoding="utf-8-sig"))
    if metadata.get("schemaVersion") != 2:
        return {
            "status": "INVALID",
            "reason": f"unsupported capture schemaVersion={metadata.get('schemaVersion')!r}",
        }
    if metadata.get("status") != "PASS":
        return {
            "status": "INVALID",
            "reason": metadata.get("statusReason") or metadata.get("status"),
        }
    return {"status": "PASS"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline_dir", type=Path)
    parser.add_argument("candidate_dir", type=Path)
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()

    baseline_status = read_capture_status(arguments.baseline_dir)
    candidate_status = read_capture_status(arguments.candidate_dir)
    comparisons = [
        compare_file(arguments.baseline_dir, arguments.candidate_dir, MANIFEST_FILE, normalize_manifest),
        compare_file(arguments.baseline_dir, arguments.candidate_dir, RESOURCE_FILE, normalize_resources),
        compare_file(arguments.baseline_dir, arguments.candidate_dir, UI_FILE, normalize_ui),
        *[
            compare_file(arguments.baseline_dir, arguments.candidate_dir, route, normalize_route)
            for route in ROUTE_FILES
        ],
    ]
    result = {
        "schemaVersion": 2,
        "baselineDir": str(arguments.baseline_dir),
        "candidateDir": str(arguments.candidate_dir),
        "baselineCapture": baseline_status,
        "candidateCapture": candidate_status,
        "comparisons": comparisons,
        "passed": (
            baseline_status["status"] == "PASS"
            and candidate_status["status"] == "PASS"
            and all(item["passed"] for item in comparisons)
        ),
    }
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if arguments.output:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
