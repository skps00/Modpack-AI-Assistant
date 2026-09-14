# 2026-09-14 — 多物品回覆排版（混亂）修正計畫 v2

> **SK 2026-09-14 10:1x 定調：「not just card wrong, everything is wrong」** → 範圍＝**成個多物品回覆排版一齊修**（唔止卡）；下方 P1–P6 全做。
> 觸發：SK 真機測試（3 件物品同問）——「數字錯，資訊錯，所有東西混在一起來講」。
> 狀態：**計畫（未實作）**。等 SK 揀 Q1／Q2 後 → adversarial review → 才派 cursor。
> 相關檔：`logic/RecipeEmbed.java`（`parts`／`splitTextIntoStepBlocks`／`interleaveEmissionCards`）、`client/gui/AiAssistantScreen.java`（`flushInlineParts`／`appendRecipeCardCaption`）、`logic/RecipeCard.java`（`sourceItemId`）、prompt `packai.reply.llm_style`（lang ×3）。
> ⛔ **2026-09-14 擱置（backlog）**：SK 決定改用「Picker 一次只准選一件物品」（見 `docs/plans/2026-09-14-single-item-selection.md`）→ 多物品回覆唔會再由 UI 產生，本計畫唔執行；若日後重新開放多選，先翻出本檔（P1 區塊邊界／P2 重新編號／P3 空標題／P4 卡歸屬／P5 prompt 收緊）。

## 1. 實證（截圖＋trace，唔靠估）

真機：`ask-20260914-095621-ino_dlc_wizard_wizard_water_ring_dragon.jsonl`（問 `how to craft those?`，選 3 件：流水魔龍戒指／`tetra:modular_sword`／`mrqx_extra_pack:atomic_disassembler`）。

**(a) 物品標題黏喺上一段最後一行**（截圖可見：`…不是主要用途）。　突变守望（Tetra 模组剑）`；下一段結尾又黏 `🔧 资源奥秘·原子分解机`）
**根因（已讀 code 確認）**：`RecipeEmbed.splitTextIntoStepBlocks`（:848）只喺 `isEmissionSectionHeading(line)`／`isNumberedStepLine(line)` 斷 block — **行首係 `[[item:id]] 名稱` 嘅標題行唔算邊界**，所以佢併入上一個 block，render 時 ITEM glyph 就 inline 黏咗喺上一句尾。

**(b) 編號亂**：截圖見到 `2.` `3.` →（空）`1. 怎么来：` → `4.`；即係每個物品區塊**冇由 1 重新編號**，而模型自己插咗個 `1. 怎么来：` 標題（下面冇內容）。

**(c) 資訊缺**：嗰個 `1. 怎么来：` 之後冇內容（該件物品嘅取得途徑實際消失），同 (b) 同源。

**(d) 卡面冇歸屬**：`[card:1]`／`[card:2]` 依 `placeEmissionCardsByRef` 插喺引用步驟之後，卡 caption 只係卡自己（配方／機台），**冇寫屬邊件物品** → 3 件物品時分唔清邊幅卡係邊件。

## 2. 修正方案（全部 deterministic／先做，prompt 只作補充）

**P1（主修，render/layout）**：`splitTextIntoStepBlocks` 加邊界條件 —— **行首（strip 後）以 `[[item:` 或 `{{item:` 開頭嘅行＝新 block 起點**；`AiAssistantScreen` 每個新 block 前 `ensureChatBlankLine`（或加淡色分隔）。→ 標題一定另起一行，唔會再黏。
**P2（render/layout）**：區塊內 numbered step **重新由 1 連續編號**（只改行首 `^\s*\d+[.、)]` 嘅數字，唔動文字）。
**P3（render/layout）**：`N. 怎么来：`／`N. 怎么用：` 呢類「淨標題、冇內容」行 → 去掉編號，變成純標題行（保留誠實資訊，唔刪內容）；若整個區塊冇內容 → 原樣保留（唔假造）。
**P4（卡歸屬）**：`appendRecipeCardCaption` 用 `RecipeCard.sourceItemId` 對應顯示名，caption 前面加「<物品名> · 」（例：`突变守望 · 配方：…`）；卡屬焦點以外物品就用嗰件名。
**P5（prompt，補充）**：lang ×3 加明規則 —— 每件物品一個 `[[item:id]] 名称` 獨立區塊、區塊內由 1 重新編號、禁止 `N. 怎么来：` 空行、禁止把下一件標題寫在同一段。

## 3. 非目標（今輪唔郁）

唔改卡數量／卡內容、唔改 `[card:N]` 機制、唔改 AskCardFallback 擺位、唔改 trace 事件、唔改 scrub／footer、唔改 JEI 查詢。

## 4. 驗收（要點）

1. Java harness（headless，新或擴充 `AskCardPlacementCheck`）：餵一段 3 物品 body（照抄今次真機 body）→ 斷言 (i) `[[item:` 行一定係 block 首行、(ii) 每區塊編號 1..n 連續、(iii) `1. 怎么来：` 空行無編號、(iv) 每個 CARD part 嘅 caption 帶正確 `sourceItemId` 名。
2. python 鏡像（如 `tests/check_ask_card_fallback.py` 有對應 mirror）加同款 case；唔准放寬。
3. SK 真機：同一個 3 物品問題再問一次 → 我讀 trace + 截圖對照。

## 5. 風險／還原

風險：P1 改 block 邊界可能影響單物品排版（卡位／換行）→ 用現有 4 個 harness + `card_placement_test.py`（sulfur／iron／mixed）＋真機再驗；`git revert` 單 commit 可還原（改動集中 2 個檔）。
