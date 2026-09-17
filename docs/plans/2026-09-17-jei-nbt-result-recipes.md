# Plan D v2 — 修「查唔到合成配方」（Tetra 類模組件）：類別發現 + NBT 容忍匹配

> 狀態：**v2（2026-09-17 13:0x）** — v1 嘅根因判斷**唔完整**（我原本以為只係 NBT 比對）；查 log 後更正：**主因係「類別發現」漏咗 vanilla 合成台**。等反方 review 達標才動手。
> SK go：2026-09-17（開計畫用）；**未批准實作**。

## §0 證據（全部真 artifact）
- 12:24 SK 問「石刻（`tetra:modular_sword`）點嚟」→ 答案「查不到合成…無法確定」＝錯。
- **log 實證**（`<instance>/logs/latest.log`）：
 - `:8427` `Pack AI JEI diag cats item=tetra:modular_sword role=OUTPUT uids=[create:automatic_shapeless]` → **只發現 Create 一個類別**，**冇** `minecraft:crafting`。
 - `:8290` 亞巴頓：`role=OUTPUT uids=[create:automatic_shaped]` → 同樣冇 crafting。
 - `:8140-8143` 12:14 另一件：`cats=0`（連一個類別都冇）。
 - 之後 `focusFail=1 focusFailTagOnly=1 focusOk=0` → 掃過嘅 1 條配方配唔上。
- 配方真相：mod jar `data/tetra/recipes/stonecutter.json`＝`minecraft:crafting_shapeless`（`tetra:stonecutter`＋`minecraft:stick` → `tetra:modular_sword`），**result 帶 NBT**（`sword/blade`、`sword/hilt`、材料、隨機 `id` UUID）。

## §1 根因（兩層，主次分明）
1. **主因：類別發現漏 vanilla 合成台** —— `client/jei/JeiLookup.java:666-669` 用 `recipes.createRecipeCategoryLookup().limitFocus(List.of(focus)).includeHidden()`（**focus 比對＝NBT 敏感**）→ 對呢啲 NBT 變體物品，JEI 回嘅類別清單只有 Create 之類，**唔含 `minecraft:crafting`** → 條配方**從來冇被掃過**。
2. **次因：focus 匹配嚴格比對 NBT** —— `client/jei/JeiFocusMatch.java:59`／`:240` 用 `ItemStack.isSameItemSameTags(...)`；即使掃到 crafting，帶隨機 `id` 嘅產出都配唔上。（現成 helper：`JeiLookup.java:1022-1034 sameItemDifferentTags(...)` 已存在，只係 OUTPUT 路徑未用。）
3. 附註：`logic/PackIndex.java:183-184` 自建索引只掃 `kubejs`／scripts／datapacks，**唔掃 mod jar** → 所以 `acquire` 亦空（另一個問題，唔喺本計畫範圍）。

## §2 修法（最小 diff，兩層都補）
- **D1 類別發現加 fallback（主修）**：OUTPUT 角色時，若 focus 類別清單**冇**任何「合成／工作站」類別（或清單為空）→ **追加**一份**內建優先短名單**（`minecraft:crafting`、`minecraft:smelting`、`minecraft:blasting`、`minecraft:stonecutting`、`minecraft:smithing` ＋ `CraftPriority`／`PackAiConfig.recipeCategoryOrder()` 內現有條目），用 `createRecipeLookup(type)` **唔 focus** 掃，逐條抽 OUTPUT slot stack 比對（見 D2）。**上限**：每個類別 `MAX_SCAN_PER_CAT`（現值 2000）＋整次 ask 最多 4 個 fallback 類別。
- **D2 匹配容忍 NBT（次修）**：fallback 路徑用「**item id 相同即可**」（重用 `sameItemDifferentTags` 語義，唔理 NBT）；**主路徑（focus 命中）行為不變**，避免改動既有輸出。
- **D3 產出可讀化（可選，後加）**：命中配方嘅 result 帶 NBT 時，用 Tetra API（`ModuleRegistry`／`MaterialStore`／`IModularItem`，**反射／軟依賴**）譯成「石刀＋木棍」；譯唔到 → 只寫物品名（fail-closed，唔准自創）。
- **D4 診斷**：新計數 `fallbackCats`／`nbtTolerantHits`，寫入現有 `Pack AI JEI diag` 行（**默認只喺 diag 開關開時輸出**）。
- ⛔ 唔准做：改 `PackAiConfig` 預設、改語言檔 key、改 trace 事件語義、加 hard dependency、掃 mod jar（另一計畫）、改 focus 主路徑輸出。

## §3 驗收（S1–S9）
- **S1** 真機：問「石刻（`tetra:modular_sword`）點嚟」→ 答案出「**合成台**：石刀 ＋ 木棍 → 石刻」（唔准再寫「查不到合成」）。
- **S2** log 要見到 `role=OUTPUT` 類別清單**包含** `minecraft:crafting`（今日只 `create:automatic_shapeless`）。
- **S3** trace `tool.result`：`jei_lookup` 對 `tetra:modular_sword` **非空**。
- **S4** 負控 1：普通物品（例 `minecraft:stick`）輸出**唔准**有重複行（focus 命中優先）。
- **S5** 負控 2：result 帶 NBT 但**唔同 item id** 嘅配方唔准算落 focus（只比 item id，唔准亂認）。
- **S6** 成本：fallback 只喺 focus 類別空／無合成類別時做；log 量前後單次 ask 耗時（差異 <20% 為合格，寫實際數字）。
- **S7** 純函數 harness（檔名寫死）：`src/test/java/com/skps9/packai/client/gui/JeiNbtToleranceCheck.java`；命令 `./gradlew.bat -I tmp-check.gradle runJeiNbtToleranceCheck`；測 3 case（同 id 同 NBT／同 id 異 NBT／異 id）。
- **S8** 回歸：`tests/check_*.py` 全綠（今日基線 118 PASS；`check_ask_display_leak.py` RC=2＝需真機 log）＋既有 harness 全綠。
- **S9** 真機 trace 洩漏複核：`check_ask_display_leak.py --trace <instance>/packai/trace --since <日期> --min-annotations 0` → RC=0。

## §4 影響面／還原
- 檔案：`client/jei/JeiLookup.java`（主）、`client/jei/JeiFocusMatch.java`（只加容忍分支）、新 harness、可能 `tests/check_*`。
- 還原點：改動前 copy 去 `.hermes/backups/<日期>_jei_nbt/` ＋ `md5sums.txt`。
- 唔郁：`PackIndex`、`PackAiConfig` 預設、語言檔、trace 格式。

## §5 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| 1 | v1 | （評審中提到）根因唔完整；log 顯示類別發現漏 crafting | → v2 更正 |
| 2 | v2（本檔） | 待跑 | — |
