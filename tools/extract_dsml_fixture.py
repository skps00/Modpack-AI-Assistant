#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Extract real DSML leak bytes from Prism latest.log → tests/fixtures.

Usage (repo root):
  python tools/extract_dsml_fixture.py
"""
from __future__ import annotations

import os
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
OUT = ROOT / "tests" / "fixtures" / "dsml_real_doubled_2026-09-13.txt"
REF = Path(os.environ.get("LOCALAPPDATA", "")) / "Temp" / "r2_real_doubled.txt"


def decode_log(raw: bytes) -> str:
    # Plan: cp950 + errors=replace. UTF-8 retry if U+FF5C vanished (Log4j UTF-8).
    text = raw.decode("cp950", errors="replace")
    probe = pick_body(text)
    if probe.count("\uFF5C") >= 64:
        return text
    utf = raw.decode("utf-8", errors="replace")
    if pick_body(utf).count("\uFF5C") > probe.count("\uFF5C"):
        return utf
    return text


def pick_body(text: str) -> str:
    best = ""
    for line in text.splitlines():
        if "LLM raw reply" not in line or "body=" not in line:
            continue
        body = line.split("body=", 1)[1]
        body = body.replace("\\n", "\n").replace("\\r", "")
        if "DSML" not in body and "\uFF5C" not in body:
            continue
        if len(body) > len(best):
            best = body
    return best


def main() -> None:
    log_path = Path(sys.argv[1]) if len(sys.argv) > 1 else PRISM_LOG
    body = ""
    if log_path.is_file():
        body = pick_body(decode_log(log_path.read_bytes()))
    if body.count("\uFF5C") < 64 and REF.is_file():
        body = REF.read_text(encoding="utf-8", errors="replace").replace("\r\n", "\n")
        if body.endswith("\n"):
            body = body[:-1]
    if not body:
        print("FAIL no DSML body from log or ref", file=sys.stderr)
        sys.exit(1)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes((body + "\n").encode("utf-8"))
    ff = body.count("\uFF5C")
    print(f"wrote {OUT} chars={len(body)} ff5c={ff}")


if __name__ == "__main__":
    main()
