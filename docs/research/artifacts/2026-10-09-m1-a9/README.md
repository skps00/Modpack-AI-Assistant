# 2026-10-09 M1 面板驅動層 驗收（A9）— packai_sandbox_atm8（ATM8 線）

- jar：同上（`autotest-dev-0.2.3.jar`，sha256 `140b9c0cab35` 開頭）。
- 跑法：`%TEMP%\m1_a9_run.py`（cases.json 用 **`question` 欄位**＝M1 新路徑：開 `AiAssistantScreen` → 預填 → `sendCurrent()`）。
- 結果：**2/2 OK** — `M1_panel_q1`「how do I get diamonds?」（cardsOut=2、body 1,216 字）、`M1_panel_q2`「what is steel made from?」（cardsOut=5、body 1,456 字）；elapsed 103s；成本 +168,865 tokens；零搶焦點；trace 名 `ask-*-none.jsonl`（無 item ＝ 真走面板問題路徑）。
- A9 判準（plan §4b(3)）：trace 有 `display.body.final`（非空）＋ `render.cards.final`（cardsOut ≥1）→ **兩條都滿足**。

## ⚠️ 新發現（同一輪 ATM8 答案，未屬今次 plan 範圍）
- `M1_panel_q2` 玩家 body 出現 **raw item tag id**：`forge:ingots/steel`（兩處：「share the same forge:ingots/steel tag」「a general forge:ingots/steel ingredient in ~92 crafting recipes」）。
- 特性：`namespace:tag/path` 形態、段首 `ingots` 唔喺 fail-closed 網嘅 7 個前綴（`chests|gameplay|entities|inject|structures|spawners|blocks`）→ 照樣出街；`check.post_scrub_drop=0`（網冇撳）。
- 同一輪其他 `x/y` 命中全部係**正常英文字**（`furnace/smelters`／`mining/smelting/crafting`／`loot/trade`／`Minecraft/mod`）——唔係洩漏。
- 影響：promo A3（成品像素掃 raw-token）會撳到呢類 token；建議另開 plan：① 擴闊 fail-closed 網到 `namespace:tag/path` 形態（`:` 喺詞中＝強信號）或 ② 人化 tag（`forge:ingots/steel` → 「steel（通用標籤）」）。
