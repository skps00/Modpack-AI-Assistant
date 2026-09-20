#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""b-2: InfoCompleteness.append is once, after the STANDARD block, before if (override)."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java"
A1 = "if (frameKind == ModularFrameStandard.Kind.STANDARD && frameMatch.recipeIndex() != null) {"
A2 = "if (override) {"
NEEDLE = "InfoCompleteness.append("


def _skip_quoted(src: str, i: int, quote: str) -> int:
    """Index just after a Java string or char literal that starts at src[i] == quote."""
    i += 1
    n = len(src)
    while i < n:
        if src[i] == "\\":
            i += 2
            continue
        if src[i] == quote:
            return i + 1
        i += 1
    return i


def standard_block_end(src: str, a1: int) -> int:
    """Index of `}` closing the STANDARD if.

    A1 already ends with `{`, so the opener is the first `{` at/after a1 (not a nested brace).
    Depth: `{` +1, `}` -1. Skip strings, char literals, // and /* */ — format holes like
    recipe={} must not move depth.
    ponytail: no text-block (\"\"\") scan; none in this block. Upgrade if one appears.
    """
    i = src.find("{", a1)
    if i < 0:
        print("FAIL STANDARD open brace missing")
        sys.exit(1)
    depth = 0
    n = len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            i += 2
            while i < n and src[i] != "\n":
                i += 1
            continue
        if c == "/" and nxt == "*":
            i += 2
            while i + 1 < n and not (src[i] == "*" and src[i + 1] == "/"):
                i += 1
            i += 2
            continue
        if c == '"':
            i = _skip_quoted(src, i, '"')
            continue
        if c == "'":
            i = _skip_quoted(src, i, "'")
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    print("FAIL STANDARD block end not found")
    sys.exit(1)


def main() -> None:
    src = SRC.read_text(encoding="utf-8")
    c1 = src.count(A1)
    c2 = src.count(A2)
    cn = src.count(NEEDLE)
    if c1 != 1 or c2 != 1:
        print(f"FAIL anchor count A1={c1} A2={c2}")
        sys.exit(1)
    if cn != 1:
        print(f"FAIL InfoCompleteness.append count={cn}")
        sys.exit(1)
    i = src.index(NEEDLE)
    a1 = src.index(A1)
    a2 = src.index(A2)
    if not (a1 < i < a2):
        print(f"FAIL append index {i} not between A1 {a1} and A2 {a2}")
        sys.exit(1)
    # 2026-09-20 負控：把呼叫搬到 A1 的 `{` 之後、STANDARD 區塊內部，
    # 舊檢查只比 index(A1)<index(NEEDLE)<index(A2)，RC 仍係 0（假綠）。
    # 埋喺分支內 ⇒ 其他 frame 永遠唔行。呼叫必須喺配對到嘅 blockEnd 之後。
    block_end = standard_block_end(src, a1)
    if not (i > block_end):
        print("FAIL hook inside STANDARD block")
        sys.exit(1)
    print("check_info_completeness_hook_order OK")


if __name__ == "__main__":
    main()
