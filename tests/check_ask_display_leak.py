#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assert Pack AI display-body log lines contain no model/tool leak strings.

Usage (repo root):
  python tests/check_ask_display_leak.py
  python tests/check_ask_display_leak.py --ver 0.2.1
  python tests/check_ask_display_leak.py --fixture tests/fixtures/ask_display_leak_2026-09-13.txt
  python tests/check_ask_display_leak.py --log <path>

Exit 0 = pass; 1 = leak/empty-all; 2 = no log lines (need real-machine smoke).
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRISM_LOG = (
    Path.home()
    / "Documents"
    / "PrismLauncher-Windows-MSVC-Portable-8.0"
    / "PrismLauncher-Windows-MSVC-Portable-8.0"
    / "instances"
    / "AI_test_NFWC_DIM"
    / "minecraft"
    / "logs"
    / "latest.log"
)
MARKER = "Pack AI display body ver="
LINE_RE = re.compile(r"Pack AI display body ver=(\S+) src=(\S+) (.*)$")
ENSURE_PREFIX = "Pack AI ask reply before ensureCards:"
FORBIDDEN = (
    "render_recipe_cards",
    "[RECIPE_CARDS]",
    "【JEI",
    "注意：JEI",
    "role=",
    "已完整扫描",
    "已完整掃描",
    "DSML",
    "<invoke",
    "<tool_calls",
)


def read_log_text(path: Path) -> str:
    raw = path.read_bytes()
    for enc in ("utf-8", "cp950", "cp1252"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def parse_display_lines(text: str) -> list[tuple[str, str, str]]:
    out: list[tuple[str, str, str]] = []
    for line in text.splitlines():
        idx = line.find(MARKER)
        if idx < 0:
            continue
        m = LINE_RE.search(line[idx:])
        if not m:
            # Fallback: take everything after marker as body, ver/src unknown.
            rest = line[idx + len(MARKER) :]
            out.append(("", "", rest))
            continue
        out.append((m.group(1), m.group(2), m.group(3)))
    return out


def load_fixture_bodies(path: Path) -> list[str]:
    bodies: list[str] = []
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        bodies.append(line)
    return bodies


def assert_bodies(bodies: list[str], label: str) -> None:
    if not bodies:
        print("NO LOG LINES (need real-machine smoke)")
        sys.exit(2)
    nonempty = 0
    for i, body in enumerate(bodies):
        if body.strip():
            nonempty += 1
        for word in FORBIDDEN:
            if word in body:
                print(f"FAIL {label}[{i}] contains {word!r}")
                print(body[:500])
                sys.exit(1)
    if nonempty == 0:
        print(f"FAIL {label}: all bodies empty")
        sys.exit(1)
    print(f"OK {label} lines={len(bodies)} nonempty={nonempty}")


def dump_ensurecards(text: str) -> list[str]:
    """Join multi-line 'before ensureCards' blocks into one body each."""
    bodies: list[str] = []
    cur: list[str] | None = None
    ts = re.compile(r"^\[\d{1,2}\w{3}\d{4} ")
    for line in text.splitlines():
        idx = line.find(ENSURE_PREFIX)
        if idx >= 0:
            if cur:
                bodies.append("\\n".join(cur))
            start = idx + len(ENSURE_PREFIX)
            first = line[start:].lstrip()
            cur = [first] if first else []
            continue
        if cur is None:
            continue
        if ts.match(line):
            bodies.append("\\n".join(cur))
            cur = None
            continue
        cur.append(line)
    if cur:
        bodies.append("\\n".join(cur))
    return bodies


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ver", default="", help="filter display-body lines by buildId")
    ap.add_argument("--fixture", default="", help="one body per line (comments #)")
    ap.add_argument("--log", default="", help="latest.log path")
    ap.add_argument(
        "--dump-ensurecards",
        default="",
        help="write extracted before-ensureCards bodies to this file and exit",
    )
    args = ap.parse_args()

    if args.dump_ensurecards:
        log_path = Path(args.log) if args.log else PRISM_LOG
        text = read_log_text(log_path)
        bodies = dump_ensurecards(text)
        dest = Path(args.dump_ensurecards)
        dest.parent.mkdir(parents=True, exist_ok=True)
        header = (
            f"# Source: {log_path}\n"
            f"# Extracted Pack AI ask reply before ensureCards bodies "
            f"(pre-T4 leak sample). Newlines escaped as \\n.\n"
        )
        dest.write_text(header + "\n".join(bodies) + "\n", encoding="utf-8")
        print(f"wrote {dest} bodies={len(bodies)}")
        return

    if args.fixture:
        bodies = load_fixture_bodies(Path(args.fixture))
        assert_bodies(bodies, args.fixture)
        print("check_ask_display_leak OK")
        return

    log_path = Path(args.log) if args.log else PRISM_LOG
    if not log_path.is_file():
        print("NO LOG LINES (need real-machine smoke)")
        sys.exit(2)
    parsed = parse_display_lines(read_log_text(log_path))
    if args.ver:
        parsed = [p for p in parsed if p[0] == args.ver]
    parsed = parsed[-5:]
    bodies = [p[2] for p in parsed]
    assert_bodies(bodies, str(log_path))
    print("check_ask_display_leak OK")


if __name__ == "__main__":
    main()
