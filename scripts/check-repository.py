#!/usr/bin/env python3
"""Fail CI when repository contents contain common secrets or generated output."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_PARTS = {".secrets", ".local", "node_modules", "target", "__pycache__"}
SECRET_PATTERNS = {
    "private key": re.compile(
        "-----BEGIN " + r"PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{40,}",
        re.MULTILINE,
    ),
    "classic GitHub token": re.compile(r"ghp_[A-Za-z0-9]{30,}"),
    "fine-grained GitHub token": re.compile(r"github_pat_[A-Za-z0-9_]{40,}"),
}


def tracked_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        capture_output=True,
    )
    return [ROOT / item.decode("utf-8") for item in result.stdout.split(b"\0") if item]


def main() -> int:
    failures: list[str] = []
    for path in tracked_files():
        relative = path.relative_to(ROOT)
        if FORBIDDEN_PARTS.intersection(relative.parts):
            failures.append(f"generated or private path is tracked: {relative}")
            continue
        if not path.is_file() or path.stat().st_size > 2 * 1024 * 1024:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for name, pattern in SECRET_PATTERNS.items():
            if pattern.search(text):
                failures.append(f"possible {name} in {relative}")

    if failures:
        print("Repository security check failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    print("Repository security check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
