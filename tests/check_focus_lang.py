#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Three-lang parity for focus keys (7 keys, all locales)."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FOCUS_KEYS = (
    "packai.focus.current",
    "packai.focus.item",
    "packai.focus.block",
    "packai.focus.no_item",
    "packai.focus.no_block",
    "packai.focus.no_entity",
    "packai.focus.none",
    "packai.focus.too_far",
    "packai.focus.entity_gone",
    "packai.focus.block_no_item",
    "packai.status.focus_entity_range",
    "key.packai.focus",
)
LANGS = ("en_us", "zh_cn", "zh_tw")


def load(lang: str) -> dict:
    path = ROOT / f"forge/1.19.2/src/main/resources/assets/packai/lang/{lang}.json"
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    maps = {lang: load(lang) for lang in LANGS}
    for lang in LANGS:
        for k in FOCUS_KEYS:
            assert k in maps[lang], f"{lang} missing {k}"
            val = maps[lang][k]
            assert isinstance(val, str) and val.strip(), f"{lang} empty {k}"

    # current needs 3 %s placeholders; item/block one %s each
    for lang in LANGS:
        cur = maps[lang]["packai.focus.current"]
        assert cur.count("%s") == 3, f"{lang} packai.focus.current needs 3 %s"
        assert maps[lang]["packai.focus.item"].count("%s") == 1, lang
        assert maps[lang]["packai.focus.block"].count("%s") == 1, lang
        no_item = maps[lang]["packai.focus.block_no_item"]
        assert "%s" in no_item, f"{lang} block_no_item needs %s"
        for k in ("packai.focus.no_item", "packai.focus.no_block", "packai.focus.no_entity"):
            assert "%s" not in maps[lang][k], f"{lang} {k} must be literal"

    # same key set across langs (focus subset)
    sets = [{k for k in FOCUS_KEYS if k in maps[lang]} for lang in LANGS]
    assert sets[0] == sets[1] == sets[2]

    print("check_focus_lang OK")


if __name__ == "__main__":
    main()
