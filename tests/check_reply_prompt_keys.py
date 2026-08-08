#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Ensure packai.reply.llm_style / fact_check / reply_pattern exist in all lang JSONs."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KEYS = (
    "packai.reply.llm_style",
    "packai.reply.fact_check",
    "packai.reply.reply_pattern",
)
TREES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
)
LANGS = ("en_us.json", "zh_tw.json", "zh_cn.json")


def main() -> None:
    for tree in TREES:
        for name in LANGS:
            path = tree / name
            data = json.loads(path.read_text(encoding="utf-8"))
            for key in KEYS:
                assert key in data, f"missing {key} in {path}"
                val = data[key]
                assert isinstance(val, str) and val.strip(), f"empty {key} in {path}"
                n = val.count("%s")
                if key.endswith("llm_style"):
                    assert n == 2, f"{path} {key} expected 2 %s, got {n}"
                else:
                    assert n == 0, f"{path} {key} expected 0 %s, got {n}"
            # layout markers must live in reply_pattern (output contract)
            assert "[[item:" in data["packai.reply.reply_pattern"]
            assert "[[recipe:" in data["packai.reply.reply_pattern"]
            # style must keep inject slots; fact_check must keep no-invent + grid truth
            assert "Purpose" in data["packai.reply.llm_style"] or "用途" in data["packai.reply.llm_style"]
            style = data["packai.reply.llm_style"]
            fc = data["packai.reply.fact_check"]
            assert "3×3" in fc or "3x3" in fc
            assert "SHAPED" in fc
            # multi-select = candidates; generic action ≠ selected sibling is mandatory
            assert "alsoSelected" in fc
            assert (
                "candidates" in fc
                or "候選" in fc
                or "候选" in fc
            ), f"{path} fact_check missing candidates wording"
            assert (
                "solely because" in fc
                or "just because" in fc
                or "只因" in fc
            ), f"{path} fact_check missing wrong=selected⇒required wording"
            assert (
                "mandatory" in fc
                or "必備" in fc
                or "必备" in fc
            ), f"{path} fact_check missing mandatory-tool rule"
            assert (
                "no direct use" in fc
                or "Drinkable" in fc
                or "沒有直接使用" in fc
                or "没有直接使用" in fc
            ), f"{path} fact_check missing no-direct-use guard"
            assert (
                "does not list" in fc
                or "沒有列出" in fc
                or "没有列出" in fc
            ), f"{path} fact_check missing JEI-does-not-list guard"
            assert (
                "[VARIANT]" in fc and "schematic" in fc
            ), f"{path} fact_check missing NBT variant / schematic rule"
            assert (
                "JEI may mix NBT variants sharing id" in fc
                or "JEI 可能混入同 id" in fc
            ), f"{path} fact_check missing JEI NBT-variant distrust"
            assert "packai.reply.jei_variant_caution" in data, f"{path} missing jei_variant_caution"
            caution = data["packai.reply.jei_variant_caution"]
            assert (
                "JEI may mix NBT variants sharing id" in caution
                or "JEI 可能混入同 id" in caution
            ), f"{path} jei_variant_caution missing mix wording"
            assert (
                "JEI may mix sibling" in style
                or "JEI 可能混入同 item id" in style
            ), f"{path} llm_style missing VARIANT JEI soft-trust note"
            assert (
                "Drinkable" in style
                or "Edible" in style
                or "food" in style
            ), f"{path} llm_style missing food/drink PURPOSE hint"
            assert (
                "pack-local script" in style
                or "包內腳本" in style
                or "包内脚本" in style
            ), f"{path} llm_style missing allow pack-local script facts"
            assert (
                "cannot read mod source" in style
                or "無法讀取模組源碼" in style
                or "无法读取模组源码" in style
            ), f"{path} llm_style missing forbid can't-read-source claim"
            assert (
                "unable to read mod source" in fc
                or "無法讀取模組源碼" in fc
                or "无法读取模组源码" in fc
            ), f"{path} fact_check missing script-facts rule 14"
            assert (
                "one option" in style.lower()
                or "one possible" in fc.lower()
                or "其中一種" in style
                or "其中一种" in style
                or "候選" in style
                or "候选" in style
            ), f"{path} llm_style missing candidate-tool guidance"
            # pack-agnostic: no hard-coded pack item/action examples
            for bad in ("open chest", "chestopener", "surgery", "开胸", "開胸", "手术", "手術"):
                assert bad not in style.lower() and bad not in fc.lower(), (
                    f"{path} pack-specific wording leaked: {bad}"
                )
    print("check_reply_prompt_keys OK")


if __name__ == "__main__":
    main()
