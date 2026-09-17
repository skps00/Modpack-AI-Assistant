# Plan D v1 — 修「合成結果帶 NBT 嘅配方查唔到」（Tetra 類模組件盲點）

> 狀態：**v1（2026-09-17 12:4x）** — 等反方 review 達標（正方 ≥8 : 反方 ≤2）才動手。
> SK go：2026-09-17（「go」）＝**開計畫**，唔係批准實作。

## §0 問題（有真檔證據）
- 12:24 SK 問「石刻（`tetra:modular_sword`）點嚟」→ trace `packai/trace/ask-20260917-122439-tetra_modular_sword.jsonl`：
 `tool.result` 顯示 `acquire` **空**、`jei_lookup` **空** → AI 只能答「本包索引查不到合成…取得方式無法確定」＝**錯**。
- 真相（mod jar 內 `data/tetra/recipes/stonecutter.json`）：`minecraft:crafting_shapeless`，材料 `tetra:stonecutter`（石刀）＋ `minecraft:stick`，產出 `tetra:modular_sword`，**但 result 帶 NBT**：
 `sword/blade=sword/stonecutter`、`sword/hilt=sword/basic_hilt`、`sword/stonecutter_material=stonecutter/stonecutter`、`sword/basic_hilt_material=basic_hilt/stick`、`id=<UUID>`。
- 12:21「亞巴頓（`tetra:modular_single`）」同理。

## §1 根因（兩條路都撞 NBT）
1. **JEI 路**：`client/jei/JeiLookup.java:737-741`（OUTPUT 角色）用 `createRecipeLookup(type).limitFocus(List.of(focus))` —— **focus 用 ItemStack 比對，NBT 敏感**；Tetra 配方產出嘅 NBT 每次都有隨機 `id` → 永遠配唔上 → 回空。
2. **自建索引路**：`logic/PackIndex.java:183-184` 只掃 `<gameDir>/kubejs`（＋少量來源），**唔掃 mod jar 內 `data/*/recipes/*.json`** → Tetra 配方從來冇入過索引。
3. Tetra API（1.19.2 jar 實證存在）：`se/mickelus/tetra/module/ModuleRegistry`、`se/mickelus/tetra/data/MaterialStore`、`se/mickelus/tetra/items/modular/IModularItem` → 可將 NBT 內嘅模組／材料 id 譯成人睇得明嘅名（`sword/stonecutter`→石刀、`basic_hilt/stick`→木棍）。

## §2 修法（只做 JEI 路，最小 diff）
- **D1 NBT 容忍匹配**：OUTPUT 角色時，若 `limitFocus(focus)` 回空 → 追加一次**唔 focus**嘅掃描（上限沿用 `MAX_SCAN_PER_CAT`），逐條抽 `RecipeIngredientRole.OUTPUT` 嘅 item stack，**只比對 `Item.getItem()`（忽略 NBT）** 等於 focus 嘅 item → 命中就輸出。
- **D2 只補、唔取代**：focus 命中結果優先；NBT 容忍結果只在**完全冇 focus 命中**時才加，並且每類別最多 3 行（同現有 acquire 預算一致）。
- **D3 材料可讀化（可選，後加）**：命中嘅配方若 result 有 NBT，用 Tetra API（`ModuleRegistry`／`MaterialStore`，**用反射／軟依賴**，唔加 hard dependency）譯成「石刀＋木棍」；譯唔到 → 只寫物品名（fail-closed，唔准自創）。
- **D4 診斷**：新 diag 計數 `nbtTolerantHits`／`nbtTolerantScans`，寫入現有 ask diag（`PackAiConfig` 嘅 diag 開關；**唔准**默認 spam）。
- ⛔ 唔准做：改 `PackAiConfig` 預設值、改語言檔既有 key、改 trace 事件語義、加 hard dependency 去 Tetra、掃 mod jar（留待另一計畫）。

## §3 驗收（S1–S8）
- **S1** 真機：問「石刻（`tetra:modular_sword`）點嚟」→ 答案要出「合成台：石刀 ＋ 木棍 → 石刻」（唔准再寫「查不到合成」）。
- **S2** `jei_lookup` 對 `tetra:modular_sword` 非空（trace `tool.result` 有內容）。
- **S3** 負控 1：普通配方（例 `minecraft:stick`）唔可以有重複／額外行（focus 命中優先，唔加容忍行）。
- **S4** 負控 2：一條 result 帶 NBT 但**唔同 item id** 嘅配方，唔准算落 focus（只比 item，唔比 NBT ≠ 亂認）。
- **S5** 效能：非 focus 掃描只喺 focus 回空時做，且每類別 <= `MAX_SCAN_PER_CAT`；用 log 量一次 ask 耗時（前後對比）。
- **S6** 純函數級：新增 harness（檔名寫死，例 `src/test/java/com/skps9/packai/client/gui/JeiNbtToleranceCheck.java`；命令 `./gradlew.bat -I tmp-check.gradle runJeiNbtToleranceCheck`）測匹配 predicate 3 個 case。
- **S7** 回歸：`tests/check_*.py` 全綠（今日基線 118 PASS／`check_ask_display_leak.py` RC=2 需真機 log）；既有 harness 全綠。
- **S8** 真機 trace：`check_ask_display_leak.py --trace <instance>/packai/trace --since <日期> --min-annotations 0` RC=0。

## §4 影響面／還原
- 檔案：`client/jei/JeiLookup.java`（主）、可能 `client/jei/JeiRecipeCards.java`（同一 predicate 共用時）、新 harness、`tests/check_*`（如加）。
- 還原點：改動前 copy 去 `.hermes/backups/<日期>_jei_nbt/` ＋ `md5sums.txt`（同今日 settings 做法）。
- 唔郁：`PackIndex`（另一計畫）、`PackAiConfig` 預設、語言檔。

## §5 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| 1 | v1（本檔） | 待跑 | — |
