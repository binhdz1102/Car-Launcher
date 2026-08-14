#!/usr/bin/env python3
"""Fail-closed check for the tracked public library compatibility seam."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument(
        "--lock", type=Path, default=Path("contract/api-compat.lock.json")
    )
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    lock_path = (root / args.lock).resolve() if not args.lock.is_absolute() else args.lock
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    libraries = []
    passed = True
    for library in lock["libraries"]:
        files = []
        text = ""
        for relative in library["candidateFiles"]:
            path = root / relative
            exists = path.is_file()
            files.append({"path": relative, "exists": exists})
            if exists:
                text += path.read_text(encoding="utf-8-sig", errors="replace") + "\n"
            else:
                passed = False
        symbols = []
        for symbol in library["symbols"]:
            present = re.search(rf"(?m)\b{re.escape(symbol)}\b", text) is not None
            symbols.append({"symbol": symbol, "present": present})
            if not present:
                passed = False
        libraries.append(
            {
                "name": library["name"],
                "status": library["status"],
                "sourceFiles": library["sourceFiles"],
                "portedFiles": library["portedFiles"],
                "files": files,
                "symbols": symbols,
            }
        )
    result = {"schemaVersion": 1, "passed": passed, "libraries": libraries}
    rendered = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
