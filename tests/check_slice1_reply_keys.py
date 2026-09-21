#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Slice 1 reply keys: three langs, %s counts, banned tokens."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG_DIR = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang"
)
LANGS = ("en_us.json", "zh_cn.json", "zh_tw.json")
KEYS = (
    "packai.reply.loot_table_block",
    "packai.reply.loot_table_generic",
    "packai.reply.info_gap_related",
    "packai.reply.info_gap_more",
    "packai.reply.tool_build_canonical",
    "packai.reply.kubejs_tooltip_hint",
)
ONE_PERCENT_S = {
    "packai.reply.loot_table_block",
    "packai.reply.info_gap_related",
    "packai.reply.info_gap_more",
    "packai.reply.tool_build_canonical",
}
ZERO_PERCENT_S = {
    "packai.reply.loot_table_generic",
    "packai.reply.kubejs_tooltip_hint",
}
BANNED = (
    "blocks/",
    "kubejs.tooltips.",
    "chests/",
    ".json",
    "未索引",
    "not indexed",
    "没有取得路径",
    "請明說",
    "请明说",
)


def main() -> None:
    failed = False
    for name in LANGS:
        path = LANG_DIR / name
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except OSError as e:
            print(f"FAIL read {path}: {e}")
            failed = True
            continue
        except json.JSONDecodeError as e:
            print(f"FAIL json {path}: {e}")
            failed = True
            continue
        for key in KEYS:
            if key not in data:
                print(f"FAIL missing {key} in {path}")
                failed = True
                continue
            val = data[key]
            if not isinstance(val, str):
                print(f"FAIL {key} not str in {path}")
                failed = True
                continue
            n = val.count("%s")
            if key in ONE_PERCENT_S:
                expect = 1
            elif key in ZERO_PERCENT_S:
                expect = 0
            else:
                print(f"FAIL {key} has no %s rule")
                failed = True
                continue
            if n != expect:
                print(f"FAIL {path} {key} expected {expect} %s, got {n}")
                failed = True
            for bad in BANNED:
                if bad in val:
                    print(f"FAIL {path} {key} contains banned {bad!r}")
                    failed = True
    if failed:
        sys.exit(1)
    print("check_slice1_reply_keys OK")


if __name__ == "__main__":
    main()
