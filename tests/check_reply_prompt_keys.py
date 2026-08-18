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
    "packai.reply.acquire_index_miss",
    "packai.reply.obtain_unknown",
    "packai.reply.structure_chest_obtain",
    "packai.reply.summon_index_miss",
    "packai.reply.unknown_advancement_gate",
    "packai.reply.unlock_done",
    "packai.reply.unlock_not_done",
    "packai.reply.unlock_unreadable",
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
                if key.endswith("acquire_index_miss"):
                    low = val.lower()
                    assert "not indexed" in low or "未索引" in val, path
                    assert "do not invent" in low or "禁止捏造" in val, path
                if key.endswith("obtain_unknown"):
                    assert (
                        "No obtain path found" in val
                        or "本包找不到取得方式" in val
                    ), path
                if key.endswith("unknown_advancement_gate"):
                    assert "unknown" in val.lower() or "未知" in val, path
                if key.endswith("unlock_done"):
                    assert "done" in val.lower() or "完成" in val, path
                    assert val.strip().startswith("["), path
                if key.endswith("unlock_not_done"):
                    assert "not done" in val.lower() or "未完成" in val, path
                if key.endswith("unlock_unreadable"):
                    assert (
                        "unable to read" in val.lower()
                        or "無法讀取" in val
                        or "无法读取" in val
                    ), path
            # layout markers must live in reply_pattern (output contract)
            assert "[[item:" in data["packai.reply.reply_pattern"]
            assert (
                "[[recipe_card:" in data["packai.reply.reply_pattern"]
                or "[[recipe:" in data["packai.reply.reply_pattern"]
            ), f"{path} reply_pattern missing recipe marker contract"
            assert "packai.reply.recipe_cards_catalog" in data
            assert "[[recipe_card:" in data["packai.reply.recipe_cards_catalog"]
            cat = data["packai.reply.recipe_cards_catalog"]
            pf = data["packai.reply.ask_purpose_order.purpose_first"]
            style = data["packai.reply.llm_style"]
            assert "role=input" in cat
            assert (
                "not How to get" in cat
                or "不是怎么来" in cat
                or "不是怎麼來" in cat
            ), f"{path} catalog missing input≠obtain"
            assert (
                "only role=input" in cat
                or "只有 role=input" in cat
            ), f"{path} catalog missing input-only skip How to get"
            assert (
                "NOT obtain" in pf
                or "≠取得" in pf
            ), f"{path} purpose_first missing input≠obtain"
            assert (
                "NOT obtain" in style
                or "≠取得" in style
            ), f"{path} llm_style missing input≠obtain"
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
                "sample card" in caution
                or "样本卡" in caution
                or "樣本卡" in caution
            ), f"{path} jei_variant_caution missing sample-card wording"
            assert (
                "JEI R on this item" in caution
                or "对该物按 R" in caution
                or "對該物按 R" in caution
            ), f"{path} jei_variant_caution missing press-R-on-this-item"
            assert (
                "sample card" in fc
                or "样本卡" in fc
                or "樣本卡" in fc
            ), f"{path} fact_check missing sample-card wording"
            assert (
                "JEI R on this item" in fc
                or "对该物按 R" in fc
                or "對該物按 R" in fc
            ), f"{path} fact_check missing press-R-on-this-item"
            assert "dump_level=INFO" in fc, f"{path} fact_check missing jei_lookup dump_level=INFO"
            assert "jei_info_use" in fc, f"{path} fact_check missing jei_info_use"
            assert (
                "未标明" in fc
                or "does not specify" in fc.lower()
            ), f"{path} fact_check missing 未标明 / does not specify forbid"
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
                or "how to get order" in fc.lower()
                or "「怎麼來」順序" in fc
                or "「怎么来」顺序" in fc
                or "one-shot quest" in fc.lower()
                or "一次性任務" in fc
                or "一次性任务" in fc
                or "rule 16" in fc.lower()
                or "16. When facts include JEI" in fc
                or "16. 當事實含" in fc
                or "16. 当事实含" in fc
                or "16. How to get order" in fc
                or "16. 「怎麼來」順序" in fc
                or "16. 「怎么来」顺序" in fc
            ), f"{path} fact_check missing JEI-vs-quest-reward rule 16"
            assert "role=quest" in fc, f"{path} fact_check missing role=quest obtain carve-out"
            assert (
                "optional progression note" in style.lower()
                or "可選進度備註" in style
                or "可选进度备注" in style
                or "loot/chest/fish" in style.lower()
                or "掉落／寶箱／釣魚" in style
                or "掉落／宝箱／钓鱼" in style
                or "one-shot mission" in style.lower()
                or "一次性任務" in style
                or "一次性任务" in style
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
            assert "packai.reply.tool_build" in data, f"{path} missing packai.reply.tool_build"
            tb = data["packai.reply.tool_build"]
            assert "[TOOL_BUILD]" in tb, f"{path} tool_build missing [TOOL_BUILD]"
            assert (
                "empty-frame" in tb.lower()
                or "empty modular" in tb.lower()
                or "空白模組" in tb
                or "空白模组" in tb
            ), f"{path} tool_build missing blank modular-frame wording"
            assert (
                "how this customized" in tb.lower()
                or "empty-frame" in tb.lower()
                or "禁止當成這把" in tb
                or "禁止当成这把" in tb
            ), f"{path} tool_build missing not-this-instance obtain ban"
            assert "[TOOL_BUILD]" in style, f"{path} llm_style missing [TOOL_BUILD]"
            assert "[TOOL_BUILD]" in fc, f"{path} fact_check echo list missing [TOOL_BUILD]"
            assert "packai.reply.tetra_use" in data, f"{path} missing packai.reply.tetra_use"
            tu = data["packai.reply.tetra_use"]
            assert "[TETRA_USE]" in tu, f"{path} tetra_use missing [TETRA_USE]"
            assert (
                "workbench" in tu.lower()
                or "工作台" in tu
            ), f"{path} tetra_use missing Tetra workbench wording"
            assert (
                "not a finished" in tu.lower()
                or "[TOOL_BUILD]" in tu
                or "不是成品" in tu
                or "不是成品" in tu
            ), f"{path} tetra_use missing not-finished-tool contrast"
            assert "[TETRA_USE]" in style, f"{path} llm_style missing [TETRA_USE]"
            assert "[TETRA_USE]" in fc, f"{path} fact_check echo list missing [TETRA_USE]"
            assert "packai.reply.purpose_chrome" in data, f"{path} missing packai.reply.purpose_chrome"
            pc = data["packai.reply.purpose_chrome"]
            assert (
                "Hold-Y" in pc
                or "hold Y" in pc.lower()
                or "按住 Y" in pc
                or "按住Y" in pc
            ), f"{path} purpose_chrome missing Hold-Y"
            assert (
                "in-game" in pc.lower()
                or "游戏内" in pc
                or "遊戲內" in pc
            ), f"{path} purpose_chrome missing in-game use"
            assert (
                "Pack AI" in pc
                or "packai.screen" in pc
            ), f"{path} purpose_chrome missing Pack AI chrome ban"
            assert (
                "Hold-Y" in style
                or "hold Y" in style.lower()
                or "按住 Y" in style
                or "Pack AI" in style and ("keybind" in style.lower() or "按键" in style or "按鍵" in style)
                or "怎么用只写游戏内" in style
                or "怎麼用只寫遊戲內" in style
                or "How-to-use = in-game use" in style
            ), f"{path} llm_style missing Pack AI keybind ≠ purpose"
            assert (
                "how-to-use MUST cover Tetra workbench" in fc
                or "怎么用必须写 Tetra 工作台" in fc
                or "怎麼用必須寫 Tetra 工作台" in fc
            ), f"{path} fact_check missing TETRA_USE how-to-use pin"
            assert (
                "18" in fc
                and (
                    "Never echo prompt section tags" in fc
                    or "禁止把以 [SCROLL_" in fc
                )
            ), f"{path} fact_check missing rule 18 no-echo SCROLL_/PURPOSE tags"
            assert (
                "20." in fc
                and "{{item:ns:id}}" in fc
                and (
                    "MUST begin with that exact" in fc
                    or "必須以該 {{item:ns:id}}" in fc
                    or "必须以该 {{item:ns:id}}" in fc
                )
            ), f"{path} fact_check missing strengthened rule 20 lead-{{item}} pin"
            assert (
                "19." in fc
                and (
                    "not indexed" in fc.lower()
                    or "未索引" in fc
                    or "unknown advancement gate" in fc.lower()
                )
                and (
                    "stage" in fc.lower()
                    or "GameStages" in fc
                    or "成就" in fc
                )
            ), f"{path} fact_check missing WP2 rule 19 honest miss / no invent stage-adv"
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
                "[[recipe_card:" in pat
                or "1. 2. 3." in pat
                or "numbered steps" in pat.lower()
                or "短步驟編號" in pat
                or "短步骤编号" in pat
            ), f"{path} reply_pattern missing recipe_card / numbered-step contract"
            assert (
                "Few-shot" in pat
                and (
                    "{{item:ns:id}}" in pat
                    or "{{item:ns:id{SNBT}}}" in pat
                    or "gateways:gate_pearl{gateway:" in pat
                )
                and (
                    "Wrong:" in pat
                    or "錯：" in pat
                    or "错：" in pat
                )
            ), f"{path} reply_pattern missing {{item}} few-shot wrong/right"
            # pack-agnostic: no hard-coded pack item/action examples
            for bad in ("open chest", "chestopener", "surgery", "开胸", "開胸", "手术", "手術"):
                assert bad not in style.lower() and bad not in fc.lower(), (
                    f"{path} pack-specific wording leaked: {bad}"
                )
    print("check_reply_prompt_keys OK")


if __name__ == "__main__":
    main()
