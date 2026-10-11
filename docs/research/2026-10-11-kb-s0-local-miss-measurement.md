# KB S0（部分）— 本地「答唔到」率量度（用現存真 trace）· 2026-10-11

- 目的：`docs/plans/2026-10-11-kb-github-guidebook-questbook.md` §2 S0 閘門 —— 量「本地答唔到幾多」同「KB 能唔能補」。
- 本檔＝**S0a（只用現存 trace）**；**唔係**完整 S0（完整版要另造 30 條 guide／quest 探針，未做）。
- 資料來源（唯讀）：7 個 Prism instance 嘅 `minecraft/packai/trace/ask-*.jsonl`，共 **141 個 ask session**、541 個 `send.user` record、**699 次 tool call**。
- 判定規則（機械、寫死）：
  - 一個 `ask-*.jsonl` 檔＝**一次 ask**（session）；`send.user.content` 若係 JSON 就取 `question` 欄。
  - 「本地答唔到」＝最終答案 body（`display.body.final.body`）含以下任一 marker（en＋zh 三語實讀）：`No obtain data for this item in pack facts`／`No obtain path found in this pack`／`No summon data for this name`／`unknown item`／`Query incomplete`／對應中文句。
  - 「guide／quest 題」＝問題含 `指南/手册/手冊/任务/任務/章节/章節/guide/quest/patchouli/book/chapter`（case-insensitive）。

## 1. 量到嘅數字

| 項 | 結果 |
|---|---|
| ask session 總數 | **141**（AI_test 13／sandbox 9／atm8 9／e9e 11／**ftb 73**／startech 12／universio 14） |
| 問題句式 | **全部同一模板**：`“<顯示名>”（<item id>）在这个整合包有什么用途、配方和取得方式？` ⇒ **呢批係測試 harness 造嘅題，唔係真玩家問題** |
| guide／quest 型問題（按上面規則） | **1 / 141（0.7%）**（唯一一條：`What is Ender Book (cyclic:ender_book)…`） |
| 答案 body 含「冇資料」marker | **11 / 141 = 7.8%** |
| 零卡片嘅 ask | **0 / 141** |
| body 長度中位數 | 1,141 字（min 45／max 2,676） |

### 1b. tool 層（699 次呼叫）

| tool | 呼叫次數 | 結果空手（長度 0） | 備註 |
|---|---|---|---|
| `render_recipe_cards` | 214 | 0 | |
| `jei_lookup` | 144 | **93（65%）** | 大量空結果 |
| `acquire` | 110 | **34（31%）** | |
| `purpose_lookup` | 106 | 0 | |
| `quest_fetch` | 37 | **16（43%）** | 任務書覆蓋有缺口 |
| `worldgen_lookup` | 35 | 0 | |
| `item_search` | 23 | 0 | |
| `consume_use` | 11 | **11（100%）** | 值得跟（可能係設計如此） |
| `knowledge_lookup` | **8** | **8／8 空（median 3 字）** | **KB 係空嘅實錘**：庫冇 entry ⇒ 每次呼叫都空手 |
| `guide_fetch` | 7 | 1（14%） | 指南書命中率高 |
| `tool_build` | 3 | 0 | |
| `tetra_use` | 1 | 1 | |

## 2. 對 S0 閘門嘅裁決（用事先寫死嘅門檻：本地 miss ≥15% 且 KB 增量 ≥30%）

- **本地 miss ＝ 7.8%（< 15%）** ⇒ **唔過閘**。
- 「KB 增量」**冇得量**：庫 0 entry，且 trace 內幾乎冇 guide／quest 題（0.7%）。
- ⇒ **按我自己 pre-register 嘅規則：唔應該做 S2（mod 去 GitHub 查）。**

## 3. 限制（誠實）

1. 呢批 trace **唔係真玩家流量**（全部測試模板題），所以「真問題分佈」仍然**未知**——包括 guide／quest 題嘅真實比例。
2. `jei_lookup` 65% 空結果可能係「呢件物品真係冇配方」而唔係故障 ⇒ tool 空結果 ≠ 答唔到，本量度只可以做**上界估計**。
3. `consume_use` 11/11 空、`quest_fetch` 43% 空 —— 屬候選缺口，但要逐條核（未做）。
4. 完整 S0（造 30 條 guide／quest 探針＋抽樣 KB 草稿量增量）**未做**。

## 4. 結論／建議

1. **唔做 S2**（mod 去 GitHub 查）——現時證據下唔過閘。
2. **做 S1**（自動由本地 jar／pack 抽出事實＋機制，起草 entry → SK 審批 → 上 KB）：呢個正正係 SK 原意（「mod 資料＋skill 知識上 GitHub」），而且**唔影響任何現有答案**（未上庫前零風險）。
3. 要 S2 翻身，需要：① KB 有 ≥200 條內容（S1 產出）② 真玩家 guide／quest 問題樣本（現時冇）——到時再量。
