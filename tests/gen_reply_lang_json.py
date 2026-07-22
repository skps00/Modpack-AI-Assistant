#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Merge ReplyLang strings into assets/packai/lang/*.json."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "mod" / "src" / "main" / "resources" / "assets" / "packai" / "lang"


def main() -> None:
    zh: dict[str, str] = {}
    en: dict[str, str] = {}

    def add(key: str, z: str, e: str) -> None:
        zh[key] = z
        en[key] = e

    add("packai.reply.source_header", "【來源】", "[Sources] ")
    add("packai.reply.source_join", "、", ", ")
    add("packai.reply.quote", "「%s」", '"%s"')
    add("packai.reply.label.quest_book", "整合包任務書", "pack quest book")
    add("packai.reply.label.local_recipes", "整合包本地配方", "pack local recipes")
    add("packai.reply.label.acquire", "整合包掉落表／釣魚／交易", "pack loot / fishing / trades")
    add("packai.reply.label.web", "網搜（模組資料）", "web search (mod docs)")
    add(
        "packai.reply.label.ai_only",
        "AI 模型（未引用 JEI／任務書／本地資料）",
        "AI model (no JEI / quest / local data cited)",
    )
    add("packai.reply.label.ai_model", "AI 模型", "AI model")
    add("packai.reply.label.none", "無（離線／未找到資料）", "none (offline / no data)")
    add(
        "packai.reply.label.acquire_offline",
        "整合包掉落表／釣魚／交易／本地腳本",
        "pack loot / fishing / trades / local scripts",
    )
    add("packai.reply.unnamed_quest", "未命名任務", "Untitled quest")
    add("packai.reply.unnamed_chapter", "（未命名）", "(unnamed)")
    add("packai.reply.related_quest", "%s相關任務", "%s related quest")
    add("packai.reply.chapter_quest", "%s任務", "%s quest")
    add("packai.reply.unknown_item", "（未知物品）", "(unknown item)")
    add("packai.reply.pack_script", "整合包腳本", "pack script")
    add("packai.reply.pack_config", "整合包設定", "pack config")
    add("packai.reply.pack_data", "整合包資料", "pack data")
    add(
        "packai.reply.note_pack_specific",
        "\n【注意】此為本包設定，可能與通用 wiki 不同。",
        "\n[Note] This is pack-specific and may differ from generic wikis.",
    )
    add(
        "packai.reply.friendly_offline",
        "目前無法用 AI 詳細說明（離線或未設定模型）。\n建議：打開任務書查看相關任務，或用 JEI／EMI 查手上物品的配方。\n",
        "AI detail is unavailable (offline or no model configured).\nTip: check the quest book, or look up the held item in JEI/EMI.\n",
    )
    add("packai.reply.your_question", "你的問題：", "Your question: ")
    add(
        "packai.reply.shaped_recipe",
        "【作法】用工作台（有序）合成%s\n【材料】%s\n【步驟】1. 打開工作台 2. 依配方擺放 3. 取出%s",
        "[How] Crafting table (shaped): %s\n[Materials] %s\n[Steps] 1. Open crafting table 2. Place pattern 3. Take %s",
    )
    add(
        "packai.reply.shapeless_recipe",
        "【作法】用工作台（無序）合成%s\n【材料】%s\n【步驟】1. 打開工作台 2. 放入材料 3. 取出%s",
        "[How] Crafting table (shapeless): %s\n[Materials] %s\n[Steps] 1. Open crafting table 2. Add materials 3. Take %s",
    )
    add(
        "packai.reply.removed_recipe",
        "【作法】整合包已移除或封鎖某些原版／模組配方\n【步驟】請改看任務書，或用 JEI／EMI 查還有哪些可用作法",
        "[How] The pack removed or blocked some vanilla/mod recipes\n[Steps] Check the quest book, or JEI/EMI for remaining options",
    )
    add("packai.reply.pattern_fallback", "（請用 JEI／EMI 對照配方格子）", "(check the crafting grid in JEI/EMI)")
    add("packai.reply.query_failed", "查詢失敗：%s", "Query failed: %s")
    add("packai.reply.llm_call_failed", "AI 呼叫失敗%s", "AI call failed%s")
    add(
        "packai.reply.cloud_no_key",
        "目前為 cloud 模式，但未設定 API key。請在 config/packai-client.toml 填 llm.apiKey，或設環境變數 PACKAI_API_KEY；也可改 llm.mode 為 auto / ollama / offline。",
        "Cloud mode is on but no API key is set. Put llm.apiKey in config/packai-client.toml, or set PACKAI_API_KEY; or switch llm.mode to auto / ollama / offline.",
    )
    add(
        "packai.reply.ollama_down",
        "目前為 ollama 模式，但連不上本機 Ollama（%s）。請先啟動 Ollama 並 pull 模型，或改 llm.mode。",
        "Ollama mode is on but cannot reach Ollama (%s). Start Ollama and pull a model, or change llm.mode.",
    )
    add(
        "packai.reply.tip_offline_quest",
        "\n\n提示：目前為 offline 模式，以上為任務書內容（未呼叫 LLM）。",
        "\n\nNote: offline mode — quest book summary only (no LLM).",
    )
    add(
        "packai.reply.tip_quest_summary_no_ai",
        "\n\n提示：AI 未回覆，以上為任務書摘要。",
        "\n\nNote: AI did not reply; quest book summary above.",
    )
    add(
        "packai.reply.tip_offline_empty",
        "\n\n提示：目前為 offline 模式（不呼叫 LLM）。未找到可顯示的任務內容；可改 llm.mode 或確認已安裝任務模組。",
        "\n\nNote: offline mode (no LLM). No quest content found; change llm.mode or install a quest mod.",
    )
    add(
        "packai.reply.tip_need_llm",
        "\n\n提示：在 Mods → Packai 設定 llm.mode 與 API key，或安裝並啟動 Ollama。",
        "\n\nTip: set llm.mode and API key under Mods → Packai, or install and start Ollama.",
    )
    add(
        "packai.reply.quest_override_notice",
        "【注意：已略過任務書導引（你表示任務可能有誤）】\n",
        "[Note: skipped quest-book guide (you said the quest may be wrong)]\n",
    )
    add("packai.reply.quest_fact_prefix", "任務書：%s", "Quest: %s")
    add(
        "packai.reply.guide_header_rich",
        "【任務內容】根據整合包任務書：\n",
        "[Quest content] From the pack quest book:\n",
    )
    add(
        "packai.reply.guide_header",
        "【任務導引】任務書裡有相關內容。可點下方按鈕開啟任務：\n",
        "[Quest guide] Related quests found. Use the buttons below to open them:\n",
    )
    add("packai.reply.guide_chapter_quest", "%s. 章節：%s　任務：%s\n", "%s. Chapter: %s  Quest: %s\n")
    add("packai.reply.guide_desc", "   說明：%s\n", "   Summary: %s\n")
    add(
        "packai.reply.guide_desc_fallback",
        "   說明：請點下方按鈕在任務書中查看。\n",
        "   Summary: open the quest book via the button below.\n",
    )
    add("packai.reply.guide_needs", "   可能需要：", "   May need: ")
    add("packai.reply.guide_etc", "等", "…")
    add(
        "packai.reply.guide_more",
        "還有其他相關任務，可在任務書裡搜尋。\n",
        "More related quests may exist — search in the quest book.\n",
    )
    add(
        "packai.reply.guide_stuck",
        "若只是卡住／看不懂，請先照任務說明做。\n",
        "If you are stuck, follow the quest instructions first.\n",
    )
    add(
        "packai.reply.guide_conflict",
        "\n【警告】任務內容可能已過時，以下依整合包實際配方：\n",
        "\n[Warning] Quest text may be outdated; pack recipes below take priority:\n",
    )
    add("packai.reply.fishing", "釣魚：", "Fishing: ")
    add("packai.reply.loot", "掉落：", "Loot: ")
    add("packai.reply.trade", "交易：", "Trade: ")
    add("packai.reply.fishing_kind", "釣魚", "Fishing")
    add("packai.reply.loot_kind", "掉落表", "Loot table")
    add("packai.reply.trade_kind", "交易", "Trade")
    add("packai.reply.script_needs", "腳本配方需要：%s", "Script recipe needs: %s")
    add(
        "packai.reply.script_removed",
        "腳本已移除原配方（整合包有改動）",
        "Script removed the original recipe (pack change)",
    )
    add(
        "packai.reply.compact_cycle",
        "壓縮循環（材料↔磚塊，不是主要取得方式）：與%s互轉",
        "Compression cycle (storage packing, not a real obtain path): swaps with %s",
    )
    add("packai.reply.local_acquire_header", "【本地獲取】%s", "[Local acquire] %s")
    add(
        "packai.reply.web_header_mixed",
        "【網搜｜僅 Minecraft mod；整合包其他部分可能已魔改，此題目未見本地覆寫，僅供參考】\n",
        "[Web｜Minecraft mods only; other pack areas may be modified; no local override for this topic — reference only]\n",
    )
    add(
        "packai.reply.web_header_strict",
        "【網搜｜僅 Minecraft mod 相關，整合包魔改時可能不準】\n",
        "[Web｜Minecraft mod related only; pack changes may differ]\n",
    )
    add(
        "packai.reply.web_header_local",
        "【網搜｜補充用】此物品有本地腳本／掉落／任務資料：衝突一律以本地為準；僅在本地沒寫到時可參考。\n",
        "[Web｜supplement] Local script/loot/quest data exists: local always wins on conflict; use web only for gaps.\n",
    )
    add("packai.reply.jei_no_recipes", "【JEI】手上物品無 JEI 配方資料。\n", "[JEI] No JEI recipe data for the held item.\n")
    add(
        "packai.reply.jei_hint_empty",
        "【JEI 提示】未持物品：在問題中寫 mod:id，或開 JEI 把游標停在物品上再提問。\n",
        "[JEI tip] No held item: type mod:id in the question, or hover an item in JEI then ask.\n",
    )
    add(
        "packai.reply.jei_header",
        "【JEI 資料】物品%s%s（已完整掃描；已略過%s）\n",
        "[JEI] Item %s%s (full scan; skipped %s)\n",
    )
    add(
        "packai.reply.jei_empty",
        "【JEI 資料】%s目前沒有可顯示的配方、用途或機器配方。",
        "[JEI] %s has no showable recipes, uses, or machine recipes.",
    )
    add("packai.reply.jei_section_recipes", "配方（如何製作，等同 JEI 按 R）", "Recipes (how to make; JEI R)")
    add("packai.reply.jei_section_uses", "用途（用在何處，等同 JEI 按 U）", "Uses (where used; JEI U)")
    add(
        "packai.reply.jei_section_catalyst",
        "作為機器／工作站的配方（JEI 催化劑；特殊合成多在此）",
        "As machine/workstation (JEI catalyst)",
    )
    add(
        "packai.reply.jei_zero_useful",
        "（有用配方 0 筆；已略過通用配方 %s 筆）\n",
        "(0 useful recipes; skipped %s generic)\n",
    )
    add("packai.reply.jei_totals", "【JEI 掃描合計】有用 %s 筆", "[JEI scan] useful %s")
    add("packai.reply.jei_totals_skipped", "；略過通用 %s 筆", "; skipped generic %s")
    add(
        "packai.reply.jei_truncated",
        "…\n（文字過長已截斷；有用 %s 筆，請在 JEI 查看細節）",
        "…\n(truncated; %s useful — see JEI for details)",
    )
    add("packai.reply.jei_skipped", "• [%s] 略過 %s 筆（%s）\n", "• [%s] skipped %s (%s)\n")
    add("packai.reply.jei_cat_count", "• [%s] 共 %s 筆", "• [%s] %s recipes")
    add("packai.reply.jei_cat_unique", "（去重後 %s 種）", " (%s unique)")
    add("packai.reply.jei_cat_spam", "；另略過通用 %s 筆", "; also skipped %s generic")
    add("packai.reply.jei_cat_cap", "（已達單分類掃描上限 %s）", " (hit per-category scan cap %s)")
    add("packai.reply.jei_no_mats", "（無材料）", "(no inputs)")
    add("packai.reply.jei_no_out", "（無產物）", "(no outputs)")
    add("packai.reply.jei_machine_line", "機器%s： %s → %s", "Machine %s: %s → %s")
    add(
        "packai.reply.spam_skip_label",
        "外觀／包覆類（facade、framed_*、cover 等，幾乎套用所有方塊）",
        "cosmetic/facade/framed covers (apply to almost every block)",
    )
    add(
        "packai.reply.craft_pref_base",
        "推薦合成／取得時，簡易工作站優先於複雜機器；同類多條路線時優先較快／產量較高的。",
        "Among obtain/craft routes prefer simpler stations over complex machines; when tied prefer faster/higher yield.",
    )
    add(
        "packai.reply.craft_pref.quest",
        "玩家偏好優先顯示【任務】獲取途徑（任務獎勵／任務書），再補合成與其他。",
        " Player prefers showing [quest] obtain paths first (rewards / quest book), then craft and others.",
    )
    add(
        "packai.reply.craft_pref.loot",
        "玩家偏好優先顯示【掉落／釣魚／交易】等探索獲取，再補合成與任務。",
        " Player prefers showing [loot/fish/trade] obtain paths first, then craft and quests.",
    )
    add(
        "packai.reply.craft_pref.balanced",
        "各獲取途徑（合成、任務、掉落等）均衡呈現，依資料自然穿插。",
        " Present obtain paths (craft, quest, loot, …) evenly; interleave naturally.",
    )
    add(
        "packai.reply.craft_pref.craft",
        "玩家偏好優先顯示【合成／JEI】配方途徑；任務獎勵最後才提（除非玩家明確問任務）。",
        " Player prefers showing [craft/JEI] recipe paths first; mention quest rewards last unless asked about quests.",
    )
    seasons = [
        ("初春", "Early Spring"),
        ("仲春", "Mid Spring"),
        ("晚春", "Late Spring"),
        ("初夏", "Early Summer"),
        ("仲夏", "Mid Summer"),
        ("晚夏", "Late Summer"),
        ("初秋", "Early Autumn"),
        ("仲秋", "Mid Autumn"),
        ("晚秋", "Late Autumn"),
        ("初冬", "Early Winter"),
        ("仲冬", "Mid Winter"),
        ("晚冬", "Late Winter"),
    ]
    for i, (z, e) in enumerate(seasons):
        add(f"packai.reply.season_sub.{i}", z, e)
    add(
        "packai.reply.season_serene",
        "【季節】Serene Seasons 估算：%s（第 %s 天；種植請對照遊戲內季節日曆）\n",
        "[Season] Serene Seasons estimate: %s (day %s; check in-game season calendar)\n",
    )
    add(
        "packai.reply.season_fd",
        "【季節】Farmer's Delight 部分作物受季節影響；請結合上方季節說明可否種植。\n",
        "[Season] Some Farmer's Delight crops are seasonal; use the season above for plantability.\n",
    )
    add(
        "packai.reply.psi_addon",
        "【Psi】玩家想設計 Psi 術式：用與回答相同的語言說明 trick 組合思路（向量、實體、運動、偵測等），列出建議的 trick 名稱與順序；提醒在 CAD 中組裝與測試 PSI 消耗。勿捏造不存在的 trick 名稱；不確定時建議查 JEI 的 Psi 分類。",
        "[Psi] Player wants a Psi spell: explain trick composition (vectors, entities, motion, sensors) in the same language as the answer; list suggested trick names/order; remind to assemble in CAD and test PSI cost. Do not invent nonexistent tricks; if unsure, suggest JEI Psi categories.",
    )
    add(
        "packai.reply.sources_instruction",
        "每則回答結尾必須另起一行寫【來源】，列出你實際引用的資料（至少一項），例如：JEI、整合包任務書、整合包本地配方、網搜（模組資料）、AI 推論；不可省略【來源】。【來源】標籤與標籤內容都必須使用與主文相同的語言。",
        "End every answer with a new line starting with [Sources], listing what you used (at least one), e.g. JEI, pack quest book, pack local recipes, web search (mod docs), AI inference. Do not omit [Sources]. Write the [Sources] header and labels in the same language as the answer body.",
    )
    add(
        "packai.reply.llm_style",
        "主文必須白話（作法／材料／步驟），給 Minecraft 遊戲內純文字顯示。絕對禁止：emoji／表情符號、Markdown（不要用 # ** ` - 列表標題）、物品ID、檔案路徑、KubeJS／腳本、JSON。材料與物品只用可讀名稱。%s若有 jei 欄位：優先用它說明合成配方與用途（等同遊戲內 JEI 按 R／U），以及「作為機器／工作站」的特殊配方（JEI 催化劑）；JEI 列表已依推薦順序排序。若資料含本地獲取／掉落／釣魚／交易／腳本配方：必須一併說明 JEI 可能沒列出的取得方式；與 JEI 衝突時標明來源差異。若出現「壓縮循環」或 9 合 1 磚塊再拆回材料：那只是收納互轉，絕對不要當成主要取得／合成進度；除非玩家在問壓縮或空間。提到任務時只用任務名稱／章節名稱，絕對不要寫出任務 ID（例如一長串十六進位）。推薦物品時，回答最末另起一行機器標記（勿寫進正文）：<!--packai:items=mod:id|顯示名稱,mod:id2|另一名稱--> ；顯示名稱必須與遊戲內物品名一致。若多個物品共用同一 registry id、靠顯示名／NBT 區分，標記時一定要寫 |顯示名稱，否則圖示會錯。同名不同模組的物品請都列出以便玩家辨識。若有【網搜】：只可引用其中與 Minecraft／模組相關的內容；與 JEI／任務書／本地腳本衝突時一律以本地為準，並可提醒整合包可能已魔改。%s",
        "Write plain steps (how / materials / steps) for in-game Minecraft text. Never use emoji, Markdown (# ** ` - headers), item IDs, file paths, KubeJS/scripts, or JSON. Use readable item names only. %s If jei is present: prefer it for recipes/uses (like JEI R/U) and catalyst/machine recipes; JEI list is already preference-sorted. If local acquire/loot/fish/trade/script facts exist, include obtain paths JEI may miss; note conflicts with JEI. Compression 9↔1 packing is storage only — never treat as main obtain/progression unless asked. For quests use quest/chapter names only — never hex quest IDs. When recommending items, end with machine marker only: <!--packai:items=mod:id|Display Name,mod:id2|Other Name-->. Display names must match in-game item names. If several items share one registry id and differ by display name/NBT, the marker MUST include |Display Name. List same-name items from different mods. If [Web] is present: only Minecraft/mod content; local JEI/quests/scripts win on conflict. %s",
    )
    add(
        "packai.reply.llm_rules.override",
        "玩家表示任務書有誤：依本地事實回答，標明任務可能有誤。",
        "Player says the quest book is wrong: answer from local facts and mark that the quest may be wrong.",
    )
    add("packai.reply.llm_rules.conflict", "任務可能過時：以本地魔改為準。", "Quest may be outdated: prefer pack modifications.")
    add(
        "packai.reply.llm_rules.local_only",
        "此物品／題目有本地覆寫：以本地／JEI／任務為準；網搜可作補充（例如本地沒寫的用途），與本地衝突時忽略網搜。",
        "This topic has local overrides: prefer local/JEI/quests; web is OK as a supplement for gaps — ignore web on conflict.",
    )
    add(
        "packai.reply.llm_rules.mixed",
        "整合包可能只魔改部分內容；此題目未見針對該物品的本地覆寫，可參考網搜模組資料，仍以 JEI／本地為準。",
        "The pack may only modify some content; no local override for this item — web mod docs ok, but JEI/local win.",
    )
    add(
        "packai.reply.llm_rules.default",
        "可結合常識與網搜模組資訊，但與本地衝突時以本地為準。",
        "You may use common sense and web mod info, but local data wins on conflict.",
    )
    add(
        "packai.reply.llm_system_lead",
        "你是 Minecraft 整合包助手。請用%s回答（遊戲語系：%s）。無論問題或對話歷史是什麼語言，主文與【來源】都必須用%s。若有先前對話，請延續上下文回答。",
        "You are a Minecraft modpack assistant. Answer in %s (game language: %s). Regardless of the language of the question or chat history, write the answer body and [Sources] in %s. Continue prior conversation context when present.",
    )
    add(
        "packai.reply.fact_check",
        "你必須先辨識玩家問的物品：優先使用 question／heldItem.id／jei 標題中的 registry id 與可讀名稱。若已明確知道是哪個物品（有 id 或明確名稱），不要回答「無法確定是什麼物品」。事實檢查：不可捏造整合包獨有配方／任務；若 jei 有資料，必須逐字依 jei 的材料與產物標籤列出，包含括號內任何 NBT／元件／附魔／數值要求，不可省略；同 registry id 但 NBT 不同視為不同物品。不可把多條配方混成一條，也不可自行宣稱「無序／有序」或 jei 未列出的機台。若 jei／本地沒有該物品的整合包覆寫，可用原版／該模組的標準知識說明用途與常見取得方式，並標明「通用知識（非本包覆寫）」。只有在連物品身份都無法從 question／heldItem／jei 辨識時，才說「無法確定」。提到任務只用任務名稱，不要寫任務 ID。最終原則：寧可標明依據不足，不可捏造本包獨有內容；但已知物品不可裝傻。",
        "First identify the item from question / heldItem.id / JEI header (registry id + name). If the item identity is clear, never say you cannot tell what the item is. Do not invent pack-unique recipes or quests. If JEI data is present, list ingredients and outputs using JEI labels verbatim — including any parenthesized NBT/component/enchantment/numeric requirements; same registry id with different NBT is a different item. Do not merge recipes or invent shapeless/shaped or machines not listed in JEI. If JEI/local has no pack override for that item, you may use standard vanilla/mod knowledge for uses and common obtain methods, labeled as general knowledge (not a pack override). Only say uncertain when the item identity itself cannot be resolved from the request. Use quest names only, never quest IDs. Blank beats fabricating pack-unique content, but do not pretend a known item is unknown.",
    )
    add(
        "packai.reply.llm_api_key_hint",
        "\n提示：curl 若成功，請直接改實例 config/packai-client.toml 的 llm.apiKey（完整 sk-…），或設環境變數 PACKAI_API_KEY；遊戲內設定框可能存錯。目前 key 長度=%s",
        "\nTip: if curl works, set llm.apiKey in config/packai-client.toml (full sk-…), or PACKAI_API_KEY; the in-game field may be wrong. Current key length=%s",
    )
    add(
        "packai.reply.jei_skipped_generic",
        "• [%s] 略過 %s 筆通用配方\n",
        "• [%s] skipped %s generic recipes\n",
    )
    add("packai.reply.detect.llm_failed", "AI 呼叫失敗", "AI call failed")
    add("packai.reply.detect.cloud", "目前為 cloud 模式", "Cloud mode is on")
    add("packai.reply.detect.ollama", "目前為 ollama 模式", "Ollama mode is on")
    add("packai.reply.detect.script_needs", "腳本配方需要：", "Script recipe needs:")
    add("packai.reply.detect.script_removed", "腳本已移除", "Script removed")

    for name, data in (("zh_tw.json", zh), ("en_us.json", en)):
        path = ROOT / name
        existing = json.loads(path.read_text(encoding="utf-8"))
        existing = {k: v for k, v in existing.items() if not k.startswith("packai.reply.")}
        existing.update(data)
        path.write_text(json.dumps(existing, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(name, "total", len(existing), "reply", len(data))


if __name__ == "__main__":
    main()
