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
                "Quest vs focus" in fc
                or "任務 vs 焦點" in fc
                or "任务 vs 焦点" in fc
                or "separately related quest" in fc
                or "另有相關任務" in fc
                or "另有相关任务" in fc
            ), f"{path} fact_check missing quest-vs-focus rule 15"
            assert (
                "optional progression" in fc.lower()
                or "可選進度" in fc
                or "可选进度" in fc
                or "rule 16" in fc.lower()
                or "16. When facts include JEI" in fc
                or "16. 當事實含" in fc
                or "16. 当事实含" in fc
            ), f"{path} fact_check missing JEI-vs-quest-reward rule 16"
            assert (
                "optional progression note" in style.lower()
                or "可選進度備註" in style
                or "可选进度备注" in style
            ), f"{path} llm_style missing JEI-primary vs quest-optional guidance"
            assert (
                "Quest item tasks are not trades" in fc
                or "任務物品任務≠交易" in fc
                or "任务物品任务≠交易" in fc
            ), f"{path} fact_check missing quest≠trade pin (rule 17)"
            assert (
                "quest_submit" in fc and ("quest_obtain" in fc or "Obtain" in fc or "取得" in fc)
            ), f"{path} fact_check missing quest_submit/obtain wording"
            assert (
                "do NOT invent Submit" in fc
                or "prefer null over wrong submit" in fc
                or "禁止臆測繳交" in fc
                or "禁止臆测缴交" in fc
            ), f"{path} fact_check missing no-invent-submit guard"
            assert (
                "forbid exchange" in fc.lower()
                or "exchange/redeem" in fc.lower()
                or "convert" in fc.lower()
                or "禁止「兌換" in fc
                or "禁止「兑换" in fc
                or "禁止「轉換" in fc
                or "禁止「转换" in fc
                or "轉換／換成" in fc
                or "转换／换成" in fc
                or "兌換／繳交" in fc
                or "兑换／缴交" in fc
            ), f"{path} fact_check missing obtain-only forbid 轉換/兌換/convert (rule 17)"
            assert (
                "holding in inventory completes" in fc.lower()
                or "背包持有即可完成" in fc
                or "QUEST_STATUS" in fc
                or "copy verbatim" in fc.lower()
                or "原樣抄寫" in fc
                or "原样抄写" in fc
                or "Positive example" in fc
                or "正例" in fc
            ), f"{path} fact_check missing obtain-only canonical / verbatim (rule 17)"
            assert (
                "holding in inventory completes" in style.lower()
                or "背包持有即可完成" in style
                or "QUEST_STATUS" in style
                or "canonical" in style.lower()
                or "Positive example when quest_obtain" in style
                or "正例（FACT 有 quest_obtain" in style
                or "正例（canonical）" in style
            ), f"{path} llm_style missing obtain-only canonical example"
            assert (
                "hold" in fc.lower()
                or "持有" in fc
                or "偵測" in fc
                or "侦测" in fc
                or "inventory" in fc.lower()
            ), f"{path} fact_check missing obtain=hold/detect wording"
            assert (
                "Quest item wording" in style
                or "任務物品用語" in style
                or "任务物品用语" in style
                or "hold / inventory" in style.lower()
                or "持有／背包" in style
            ), f"{path} llm_style missing quest obtain wording pin"
            assert (
                "heldItem.id" in style
                or "tasks/rewards" in style
                or "tasks／rewards" in style
            ), f"{path} llm_style missing quest-vs-focus obtain ban"
            assert (
                "one option" in style.lower()
                or "one possible" in fc.lower()
                or "其中一種" in style
                or "其中一种" in style
                or "候選" in style
                or "候选" in style
            ), f"{path} llm_style missing candidate-tool guidance"
            assert (
                "1. 2. 3." in style
                or "numbered steps" in style.lower()
                or "短步驟編號" in style
                or "短步骤编号" in style
            ), f"{path} llm_style missing short numbered-step guidance"
            assert (
                "[SCROLL_MECH]" in style
                and (
                    "right-click/RMB to learn" in style
                    or "右鍵／RMB 學習" in style
                    or "右键／RMB 学习" in style
                )
            ), f"{path} llm_style missing Tetra SCROLL_MECH / no-RMB-learn pin"
            assert (
                "13c" in fc
                and "[SCROLL_MECH]" in fc
                and (
                    "right-click/RMB to learn" in fc
                    or "右鍵／RMB 學習" in fc
                    or "右键／RMB 学习" in fc
                )
            ), f"{path} fact_check missing Tetra 13c SCROLL_MECH ban"
            assert (
                "13d" in fc
                and "[SCROLL_UNLOCK]" in fc
                and "[SCROLL_MATERIALS]" in fc
            ), f"{path} fact_check missing Tetra 13d SCROLL_UNLOCK/MATERIALS"
            assert (
                "[SCROLL_UNLOCK]" in style and "[SCROLL_MATERIALS]" in style
            ), f"{path} llm_style missing SCROLL_UNLOCK/MATERIALS pin"
            assert (
                "18" in fc
                and (
                    "Never echo prompt section tags" in fc
                    or "禁止把以 [SCROLL_" in fc
                )
            ), f"{path} fact_check missing rule 18 no-echo SCROLL_/PURPOSE tags"
            assert (
                "Never echo prompt section tags" in style
                or "禁止把以 [SCROLL_" in style
            ), f"{path} llm_style missing no-echo SCROLL_/PURPOSE hard limit"
            assert "packai.reply.tetra_scroll_mech" in data, f"{path} missing tetra_scroll_mech"
            mech_pin = data["packai.reply.tetra_scroll_mech"]
            assert (
                "5x5x5" in mech_pin
                and (
                    "not right-click" in mech_pin.lower()
                    or "禁止右鍵" in mech_pin
                    or "禁止右键" in mech_pin
                )
            ), f"{path} tetra_scroll_mech missing placement / no-RMB"
            pat = data["packai.reply.reply_pattern"]
            assert (
                "1. 2. 3." in pat
                or "numbered steps" in pat.lower()
                or "短步驟編號" in pat
                or "短步骤编号" in pat
            ), f"{path} reply_pattern missing short numbered-step contract"
            # pack-agnostic: no hard-coded pack item/action examples
            for bad in ("open chest", "chestopener", "surgery", "开胸", "開胸", "手术", "手術"):
                assert bad not in style.lower() and bad not in fc.lower(), (
                    f"{path} pack-specific wording leaked: {bad}"
                )
    print("check_reply_prompt_keys OK")


if __name__ == "__main__":
    main()
