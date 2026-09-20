#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""S9: STANDARD frame keep-1 wiring (forge-only)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai"


def read(rel: str) -> str:
    p = FORGE / rel
    assert p.is_file(), f"missing {p.as_posix()}"
    return p.read_text(encoding="utf-8")


def line_of(src: str, needle: str) -> int:
    i = src.find(needle)
    assert i >= 0, f"missing {needle!r}"
    return src.count("\n", 0, i) + 1


def method_body(src: str, sig: str) -> str:
    i = src.find(sig)
    assert i >= 0, f"missing {sig!r}"
    brace = src.find("{", i)
    assert brace >= 0, f"no brace after {sig!r}"
    depth = 0
    for j in range(brace, len(src)):
        c = src[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[brace : j + 1]
    raise AssertionError(f"unbalanced {sig!r}")


CALL_RE = re.compile(r"suppressModularFrameCards\s*\(")
# 宣告（有 return type／static 喺前面）唔算呼叫；唔靠「單行字面」，避免簽名換行假紅。
DECL_HEAD_RE = re.compile(r"(?:static\s+)?List<RecipeCard>\s*$")


def split_args(arg_block: str) -> list[str]:
    """Top-level comma split（ignore () [] {} 內嘅 comma）。"""
    out: list[str] = []
    depth = 0
    cur: list[str] = []
    for ch in arg_block:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            out.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
    if cur:
        out.append("".join(cur).strip())
    return out


def suppress_calls(src: str) -> list[tuple[int, str, list[str]]]:
    """每個 `suppressModularFrameCards(` 呼叫 → (行號, 呼叫原文, 實參清單)。

    跳過**宣告**：match 之前 80 字內以 `List<RecipeCard>`／`static …` 結尾者。
    match `(` 之後用括號配對抓實參區塊（唔靠 `;`／單行假設）。
    """
    out: list[tuple[int, str, list[str]]] = []
    for m in CALL_RE.finditer(src):
        head = src[max(0, m.start() - 80): m.start()]
        if DECL_HEAD_RE.search(head):
            continue
        line_no = src.count("\n", 0, m.start()) + 1
        open_i = m.end() - 1
        depth = 0
        end = -1
        for j in range(open_i, len(src)):
            ch = src[j]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    end = j
                    break
        assert end > 0, f"unbalanced suppressModularFrameCards at line {line_no}"
        out.append((line_no, src[m.start(): end + 1], split_args(src[open_i + 1:end])))
    return out


def call_lines(src: str, needle: str) -> list[int]:
    return [src.count("\n", 0, m.start()) + 1 for m in re.finditer(re.escape(needle) + r"\s*\(", src)]


def main() -> int:
    svc = read("client/service/AskService.java")
    calls = suppress_calls(svc)
    print("suppressModularFrameCards call lines:", [n for n, _, _ in calls])
    if len(calls) != 6:
        print(f"FAIL: expected 6 suppress calls, got {len(calls)}")
        return 1
    for n, text, args in calls:
        # 閘 ①：唔准靠變數名 `frameKeep`；改成「第三個實參唔係 null／空」
        if len(args) < 3:
            print(f"FAIL: line {n} has {len(args)} args, expected >= 3 (keep spec missing)")
            print(text)
            return 1
        third = args[2].strip()
        if third in ("", "null"):
            print(f"FAIL: line {n} third arg is {third!r}, keep spec missing/not wired")
            print(text)
            return 1

    env = read("logic/AskToolEnv.java")
    reject = method_body(env, "boolean rejectFrameCard(")
    if "ModularFrameCards.isStandardKeepCard(" not in reject:
        print("FAIL: rejectFrameCard missing isStandardKeepCard")
        return 1
    if "ModularFrameCards.shouldDropFrameCard(" not in reject:
        print("FAIL: rejectFrameCard missing shouldDropFrameCard")
        return 1

    render = read("logic/RenderRecipeCardsAskTool.java")
    keep_line = line_of(render, "keepOnlyStandardRecipeCard(")
    cap_line = line_of(render, "matched.subList(0, PER_CALL_CAP)")
    print(f"keepOnly line={keep_line} subList line={cap_line}")
    if not keep_line < cap_line:
        print("FAIL: keep-1 is not before cap")
        return 1

    engine = read("logic/AskEngine.java")
    bind = method_body(engine, "static AskToolEnv bindAskToolEnv(")
    if "frameStandardKeepOutputId" not in bind or "frameStandardKeepInputIds" not in bind:
        print("FAIL: bindAskToolEnv missing keep field copy")
        return 1

    loop = read("logic/AskLoopState.java")
    if "setFrameStandardKeep(" not in loop:
        print("FAIL: AskLoopState missing setFrameStandardKeep")
        return 1

    # 閘 ⑤（強）：兩條 ask method 都真係有呼叫 setter；唔准只驗定義檔。
    setter_lines = call_lines(svc, "setFrameStandardKeep")
    begin_lines = call_lines(svc, "beginAskLoop")
    print(f"setFrameStandardKeep lines={setter_lines} beginAskLoop lines={begin_lines}")
    if len(setter_lines) != 2:
        print(f"FAIL: AskService setFrameStandardKeep calls={setter_lines}, expected 2")
        return 1
    if len(begin_lines) != 3:  # def + 2 call sites
        print(f"FAIL: AskService beginAskLoop lines={begin_lines}, expected def + 2 calls")
        return 1
    for ln in setter_lines:
        if not any(b < ln for b in begin_lines):
            print(f"FAIL: setFrameStandardKeep line {ln} is not after any beginAskLoop call")
            return 1

    print("check_frame_standard_card_keep OK")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as e:
        print(f"FAIL: {e}")
        raise SystemExit(1)
