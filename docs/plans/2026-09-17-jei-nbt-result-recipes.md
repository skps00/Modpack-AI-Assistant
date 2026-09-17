# Plan D v3 — 「點嚟」答錯（石刻類）＝ 類別發現漏合成台 ＋ 模型否定已知 fact

> 狀態：**v3（2026-09-17 13:1x）** — v1／v2 根因都**唔完整**；今次用 log 原文更正。R1 反方 2:8（已吸收）。等反方達標（正方 ≥8 : 反方 ≤2）才動手。
> SK go：2026-09-17（開計畫用）；**未批准實作**。

## §0 事實（全部原文，唔靠推論）
1. **12:24:41 真 log**（`<instance>/logs/latest.log:8427-8433`，Big5 解碼）：
 - `:8427` `role=OUTPUT uids=[create:automatic_shapeless] titles=[自动搅拌]` → 類別發現**只有 Create**，**冇** `minecraft:crafting`。
 - `:8433` JEI fact **真係有內容**（len=375）：
 「配方（如何制作，等同 JEI 按 R）：- [自动搅拌] 共 1 条：- 机器「动力搅拌器、工作盆」：石切器、木棍 → 石刻」
 → **配方搵到，但只以「Create 機器」形式呈現，冇「合成台」版本**。
 - `:8441` `Ask replyLang=zh_cn jeiLevel=OUTPUT` → 呢段 fact 有入 prompt。
2. 但 AI 最終答：「**本包索引里查不到它的合成**、掉落、交易、任務或腳本取得路徑，**取得方式無法確定**」＋把該配方講成「空白的模組劍框架自身那個**切石機合成**只是空框架」→ **有 fact 但被否定／錯誤歸因**。
3. 配方真相（mod jar `data/tetra/recipes/stonecutter.json`）：`minecraft:crafting_shapeless`（`tetra:stonecutter`＋`minecraft:stick` → `tetra:modular_sword`），result 帶 NBT（`sword/blade`／`sword/hilt`／材料／隨機 `id`）。
4. 12:21（亞巴頓）同型：`:8290` `uids=[create:automatic_shaped]`；`focusFail=1 focusOk=0`。
5. `acquire` 空 —— 因為 `logic/PackIndex.java:183-184` 只掃 `kubejs`／scripts／datapacks，**唔掃 mod jar**（另一計畫，唔喺本範圍）。

## §1 根因（三層，按「邊層令答案錯」排）
- **RL1 類別發現漏合成台（事實層，主）**：`client/jei/JeiLookup.java:666-669` 用 `recipes.createRecipeCategoryLookup().limitFocus(List.of(focus)).includeHidden()`（focus 比對＝NBT 敏感）→ 對 NBT 變體物品只回 Create 類別 ⇒ vanilla `minecraft:crafting` 從未被掃 ⇒ fact 只出「Create 機器」版本。
- **RL2 匹配嚴格比對 NBT**：`client/jei/JeiFocusMatch.java:59`／`:240` 用 `ItemStack.isSameItemSameTags(...)`；即使掃到 crafting，帶隨機 `id` 嘅產出都配唔上。現成 helper `JeiLookup.java:1022-1034 sameItemDifferentTags(...)` 未用喺 OUTPUT 路徑。
- **RL3 答案否定已知 fact（答案層，直接令 SK 見到錯）**：prompt 已有 `role=output` 配方，答案仍寫「查不到合成／取得方式無法確定」，並把配方錯講成「空框架嘅切石機合成」。**呢層今日冇任何 gate 攔**。

## §2 修法（predicate 層最小修 + 一條答案 gate）
- **D1（RL1）**：OUTPUT 角色時，若類別清單**唔含任何「合成／工作站」類別**（或清單為空）→ 追加**內建優先短名單**（`minecraft:crafting`、`minecraft:smelting`、`minecraft:blasting`、`minecraft:stonecutting`、`minecraft:smithing`＋`CraftPriority`／`PackAiConfig.recipeCategoryOrder()` 現有條目），**唔 focus** 掃；每類別上限 `MAX_SCAN_PER_CAT`（2000）、整次 ask 最多 4 個 fallback 類別。
- **D2（RL2）**：fallback 路徑用「**item id 相同**」匹配（重用 `sameItemDifferentTags` 語義）；**必須同時標示**為「同 id 不同 NBT（材料／schematic 版本）」——跟 prompt 既有規則 1c（`[VARIANT]`），**唔准**當成同一件物品混過去。focus 主路徑輸出**唔改**。
- **D3（RL3 答案 gate）**：新增檢查（harness 或 python，檔名寫死）：**當 fact 內存在 `role=output` 配方**時，最終答案**唔准**出現「查不到合成／取得方式無法確定／沒有收錄」等否定措辭；必須引述配方（配件數最少）。只針對「已有配方式 fact」情境，唔改一般無資料情境。
- **D4 診斷**：加 `fallbackCats`／`nbtTolerantHits`／`factHasOutputButAnswerDenies` 計數，寫入現有 `Pack AI JEI diag`（默認只喺 diag 開關開時輸出）。
- ⛔ 唔准：改 `PackAiConfig` 預設、改語言檔 key、改 trace 事件語義、加 hard dependency、掃 mod jar、改 focus 主路徑輸出。

## §3 驗收（S1–S9，全部要可跑／有鑑別力）
- **S1** 真機：問「石刻（`tetra:modular_sword`）點嚟」→ 答案要出「**合成台**：石切器（石刀）＋ 木棍 → 石刻」，且**唔准**再出現「查不到合成」。
- **S2** log：`role=OUTPUT` 類別清單**包含** `minecraft:crafting`（今日只 `create:automatic_shapeless`）。
- **S3** 答案 gate 負控：用今日 `ask-20260917-122439` 真 trace 做**紅樣本**（答案有「查不到」而 fact 有 output 配方）→ gate 必須紅；改完後同型新 trace 必須綠。
- **S4** 負控 1：普通物品（例 `minecraft:stick`）輸出行數**唔准**增加（focus 命中優先）。
- **S5** 負控 2：result 帶 NBT 但**唔同 item id** 嘅配方唔准算落 focus。
- **S6** 成本：fallback 只喺需要時做；log 量單次 ask 前後耗時（要貼實際數字）。
- **S7** harness（檔名寫死）：`src/test/java/com/skps9/packai/client/gui/JeiCategoryFallbackCheck.java`；命令 `./gradlew.bat -I tmp-check.gradle runJeiCategoryFallbackCheck`。
- **S8** 回歸：`tests/check_*.py` 全綠（今日基線 **118 PASS**；`check_ask_display_leak.py` RC=2＝需真機 log）＋既有 harness 全綠。
- **S9** trace 洩漏複核：`check_ask_display_leak.py --trace <instance>/packai/trace --since <日期> --min-annotations 0` → RC=0。

## §4 影響面／還原
- 檔案：`client/jei/JeiLookup.java`（主）、`client/jei/JeiFocusMatch.java`（只加容忍分支）、答案 gate（新檢查）、新 harness。
- 還原點：改動前 copy 去 `.hermes/backups/<日期>_jei_nbt/` ＋ `md5sums.txt`。
- 唔郁：`PackIndex`、`PackAiConfig` 預設、語言檔、trace 格式。

## §5 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| 1 | v1/v2 | **2 : 8** | 反方指出：旗艦個案 fact 其實已有（log `:8433`），plan 主因同證據矛盾；D2 撞 prompt 規則 1c；9 條驗收無鑑別力 → v3 更正 |
| 2 | v3（本檔） | 待跑 | — |
