#!/usr/bin/env python3
"""Render-thread apply must not call LlmClient for body repair; bg engine may."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASK = (
    ROOT
    / "forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java"
)
CHAT_ONCE_BASELINE = 2
MARKER = "ensureNonEmptyBody("


def iter_calls(src: str):
    i = 0
    while True:
        idx = src.find(MARKER, i)
        if idx < 0:
            return
        head = src[max(0, idx - 40) : idx + len(MARKER)].replace("\n", " ")
        is_decl = "static String ensureNonEmptyBody(" in head
        j = idx + len(MARKER)
        depth = 0
        while j < len(src):
            c = src[j]
            if c == "(":
                depth += 1
            elif c == ")":
                if depth == 0:
                    call = src[idx : j + 1]
                    if not is_decl:
                        yield idx, call
                    i = j + 1
                    break
                depth -= 1
            j += 1
        else:
            return


def main() -> int:
    src = ASK.read_text(encoding="utf-8")

    apply_begin = src.index("ask.phase=apply_begin")
    apply_end = src.index("ask.phase=apply_end", apply_begin)

    for pos, call in iter_calls(src):
        flat = " ".join(call.split())
        if apply_begin <= pos < apply_end:
            assert ", false" in flat, (
                f"apply block ensureNonEmptyBody must pass false: {flat[:160]}"
            )
        elif ", true" not in flat and ", false" not in flat:
            raise AssertionError(
                f"4-arg ensureNonEmptyBody call not allowed: {flat[:160]}"
            )

    five_start = src.index("boolean allowRemote")
    five_start = src.rfind("static String ensureNonEmptyBody", 0, five_start)
    five_end = src.index("static String withPreservedSourcesFooter", five_start)
    five_body = src[five_start:five_end]
    llm_idx = five_body.index("new LlmClient()")
    window = five_body[max(0, llm_idx - 400) : llm_idx]
    assert "allowRemote" in window, "body-repair LlmClient must be inside if (allowRemote)"

    count = src.count("chatOnce(")
    assert count == CHAT_ONCE_BASELINE, (
        f"chatOnce( count {count} != baseline {CHAT_ONCE_BASELINE}"
    )

    print("check_ask_body_repair_thread OK")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as e:
        print(f"FAIL: {e}", file=sys.stderr)
        raise SystemExit(1)
