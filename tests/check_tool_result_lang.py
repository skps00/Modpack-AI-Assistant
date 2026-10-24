#!/usr/bin/env python3
"""CJK string literals in *AskTool.java must be allowlisted (grandfathered) only."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"
LANG_DIR = ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang"
ALLOWLIST = ROOT / "tests/tool_result_lang_allowlist.txt"
LANGS = ("en_us", "zh_cn", "zh_tw")
ITEM_SEARCH_KEY = "packai.reply.tool.item_search_miss"

CJK_IN_STRING = re.compile(
    r'"((?:[^"\\]|\\.)*[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff](?:[^"\\]|\\.)*)"'
)


def load_allowlist() -> set[str]:
    if not ALLOWLIST.is_file():
        return set()
    out: set[str] = set()
    for line in ALLOWLIST.read_text(encoding="utf-8").splitlines():
        s = line.rstrip("\r\n")
        if not s or s.startswith("#"):
            continue
        out.add(s)
    return out


def cjk_literals(path: Path) -> list[tuple[int, str]]:
    hits: list[tuple[int, str]] = []
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if line.strip().startswith("//"):
            continue
        for m in CJK_IN_STRING.finditer(line):
            hits.append((i, m.group(1)))
    return hits


def main() -> None:
    fails: list[str] = []
    allow = load_allowlist()

    item_src = (LOGIC / "ItemSearchAskTool.java").read_text(encoding="utf-8")
    if "ReplyLang.itemSearchMiss" not in item_src:
        fails.append("ItemSearchAskTool must call ReplyLang.itemSearchMiss")
    if "找不到" in item_src and "itemSearchMiss" not in item_src:
        fails.append("ItemSearchAskTool still has hardcoded CJK miss text")

    lang_maps = {lang: json.loads((LANG_DIR / f"{lang}.json").read_text(encoding="utf-8")) for lang in LANGS}
    for lang in LANGS:
        if ITEM_SEARCH_KEY not in lang_maps[lang]:
            fails.append(f"{lang}.json missing {ITEM_SEARCH_KEY}")

    seen_allow: set[str] = set()
    for path in sorted(LOGIC.glob("*AskTool.java")):
        for line_no, literal in cjk_literals(path):
            entry = f"{path.name}:{line_no}:{literal}"
            seen_allow.add(entry)
            if entry not in allow:
                fails.append(f"unallowlisted CJK literal: {entry[:120]}")

    stale = allow - seen_allow
    for entry in sorted(stale):
        fails.append(f"stale allowlist entry (remove): {entry[:120]}")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_tool_result_lang OK")


if __name__ == "__main__":
    main()
