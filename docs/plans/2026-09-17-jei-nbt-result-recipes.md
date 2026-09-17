# Plan D v4 — 「點嚟」答錯（石刻類）：令 vanilla 合成配方**入到 fact 同入到 prompt**

> 狀態：**v4（2026-09-17 13:2x）** — 依 R2（4:6）指引重寫：v3 把「log 有 fact」當「prompt 有 fact」＝同類錯誤，RL3/D3 前提唔成立，已**刪**。SK：**a until it 8:2**（繼續審到過關為止）。
> ⛔ 未批准實作。

## §0 事實（每條有原文／行號／命令）
1. **12:24:41 真實流程**（`<instance>/logs/latest.log`，Big5 解碼）：
 - `:8425` `Pack AI recipe cards focus=tetra:modular_sword count=0` → **card 目錄空**。
 - `:8427` `role=OUTPUT uids=[create:automatic_shapeless] titles=[自动搅拌]` → 類別發現**只有 Create**，冇 `minecraft:crafting`（R2 實跑：全 log `grep minecraft:crafting` 喺 `JEI diag` 行 = **0**）。
 - `:8433` `JEI dump len=375` → fact **生成咗**，內容＝「配方（如何制作…）：- [自动搅拌] 共 1 条：- 机器「动力搅拌器、工作盆」：石切器、木棍 → 石刻」。
 - `:8441` `jeiLevel=OUTPUT`。
2. **但 fact 冇入 prompt**（R2 逐條抽 `packai/trace/ask-20260917-122439-*.jsonl` 全部 59 條事件）：`send.user` 四次均**無 `jei` key**、`send.system` **零**次「石刻」、`send.facts` **空**（`LlmClient.java:514-519`：只有 `jei` key 存在才非空）。
 機制：`logic/AskEngine.java:777-787 jeiForLlmSlim()` —— LLM 支援工具時**只送 `[RECIPE_CARDS]` 目錄 ＋ tooltip 提示，唔送 JeiLookup dump**；而當時 catalog 空 ⇒ **375 字 fact 從來冇到模型手上**。
3. **模型唯一接觸 JEI 嘅路係自己叫工具**：trace rec 27 `jei_lookup(dump_level=INFO)` → rec 28 **result 空**。⇒ 模型答「查不到合成」係**忠實反映工具回空**，唔係「否定已知 fact」。
4. **兩條 code path 分歧（v3 冇寫）**：
 - `client/jei/JeiRecipeCards.java:128-144 ensureCoreCraft` → `:769 fromVanillaCrafting`：**已經**掃 `mc.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)` ＋ `JeiFocusMatch.craftingResultMatches(...)`（item-id 匹配）。
 - `client/jei/JeiLookup.java:667-674`：只有 `createRecipeCategoryLookup().limitFocus(List.of(focus)).includeHidden()`，**冇** fallback。
 - ⇒ 09-15 出過 `Crafting 石切器+木棍 -> tetra:modular_sword` 卡（卡路徑救返），fact 文字路徑（JeiLookup）**從來冇救**；所以係**間歇性**。
5. **類別查詢真簽名**（R2 `javap` 真 JEI jar）：`IRecipeCategoriesLookup.limitTypes(Collection<RecipeType<?>>)`、`IRecipeManager.getRecipeType(ResourceLocation)` 存在；但 `RecipeCategoriesLookup.get()` → `getRecipeCategoriesForTypes(recipeTypes, focusGroup, includeHidden)` ⇒ **類別被 focusGroup 過濾**，單加類型名**唔會**令 `minecraft:crafting` 出現 ⇒ 必須同時換走 focus（或複用現成 `focus == null` 分支／`workstationCategories(recipes, stack)` `JeiLookup.java:443`、`:673`）。
6. 配方真相：mod jar `data/tetra/recipes/stonecutter.json`＝`minecraft:crafting_shapeless`（`tetra:stonecutter`＋`minecraft:stick` → `tetra:modular_sword`），result 帶 NBT（含隨機 `id`）。

## §1 根因（兩層，已用 trace 證）
- **R-A 工具路徑（主）**：`jei_lookup`（OUTPUT）**回空** ⇒ 模型冇資料 ⇒ 答案「查不到」。成因＝§0.4 兩路分歧（JeiLookup 冇 vanilla crafting fallback）＋§0.5 focusGroup 過濾。
- **R-B prompt 路徑（協同）**：tool-capable 時 `jeiForLlmSlim()` 唔送 dump，而 catalog 空 ⇒ 就算 dump 有內容，都**唔會**入 prompt。⇒ **兩層都要補**，否則修 predicate 都見唔到效果（R2 原話）。

## §2 修法（**複用現成機制**，唔新寫短名單／上限）
- **F1（R-A，主修；約 10 行）**：`JeiLookup` OUTPUT 段當 `categories` **不含任何核心合成類別**時，**複用** `JeiRecipeCards.java:128 ensureCoreCraft`／`:769 fromVanillaCrafting` 嘅現成 vanilla-crafting 掃描（`getAllRecipesFor(CRAFTING)` ＋ `JeiFocusMatch.craftingResultMatches` item-id 匹配）→ 令 `jei_lookup` 對 `tetra:modular_sword` **非空**。
- **F2（R-B）**：tool-capable 且 `recipeCatalogForLlm()` **空** 但 JeiLookup fact 有 output 配方時 → `jeiForLlmSlim()` 改為送**一段精簡 output 配方行**（上限 3 行，複用現有 slim 機制）；catalog 非空時行為不變。
- **F3（答案層，複用）**：擴 `logic/AskJeiHints.java:23 isJeiAbsenceSummary`／`:110 scrubAbsenceClaimsWhenCards` 嘅**觸發條件**（現卡喺 `hasCards`）→ 當 prompt 內有 output 配方時，答案出現「查不到合成／無法確定」類措辭要 scrub。**唔新寫 gate**。
- **F4（NBT 容忍，細）**：`JeiLookup.java:1022-1034 sameItemDifferentTags` 係 `private` 且註「Diagnostic only」→ 重用要先改可見性；**只喺 F1 路徑**用，且**唔加**新標示要求（今日 `hasVariant=false` 常態，標示形同擺設）。
- **F5 診斷**：加 `fallbackCraftScans`／`fallbackCraftHits`／`slimInjectedRecipeLines` 計數（默認只喺 diag 開關開時輸出）。
- ⛔ 唔准：改 `PackAiConfig` 預設、改語言檔 key、改 trace 事件語義、加 hard dependency、掃 mod jar、改 focus 主路徑既有輸出、新寫類別短名單。

## §3 驗收（S1–S8；逐條寫死可跑 predicate）
- **S1（可跑，machine）**：修完後真機問「石刻」→ **新 trace** 內 `jei_lookup` 嘅 `tool.result` **包含** `石刻` 且包含 `木棍`（字串斷言，非人眼）。
- **S2（可跑）**：`grep -c "role=OUTPUT.*minecraft:crafting" <instance>/logs/latest.log` **≥1**（今日 = 0）。
- **S3（prompt 真有料）**：新 trace 內 `send.user` 有 `jei` key **或** `send.facts` 非空（今日兩者皆無）＝F2 生效證據。
- **S4（負控・真無資料）**：問一件**全包冇配方**嘅物品（例 `minecraft:bedrock`）→ 答案**仍准**講「查不到」，且 S1 斷言唔准誤觸。
- **S5（負控・唔准亂認）**：用 harness 斷言「輸出 result item-id ≠ focus 嘅配方」**唔准**被 F1 收錄（Tetra 同 id 不同 NBT 視為**同一 item-id 命中**，唔算亂認）。
- **S6（成本，可跑）**：比較修前後**同一問句**（石刻）嘅 `Pack AI LLM usage prompt=` 數字（今日 11852／12384）＋單次 ask 由 log 時間戳算耗時，貼實際數字，prompt 增幅 **≤ 400 字**。
- **S7（harness）**：`src/test/java/com/skps9/packai/client/gui/JeiCraftFallbackCheck.java`；命令 `./gradlew.bat -I tmp-check.gradle runJeiCraftFallbackCheck`（`research/gen_tmp_check.py:13` 規則 `run`+cls 已對）。
- **S8（回歸／洩漏）**：`tests/check_*.py` 118 個（今日基線，`check_ask_display_leak.py` RC=2＝需真機 log）＋既有 harness 全綠；`check_ask_display_leak.py --trace <instance>/packai/trace --since <日期> --min-annotations 0` → RC=0（argparse 三參數實測存在 `check_ask_display_leak.py:862-864`）。

## §4 影響面／還原
- 檔案：`client/jei/JeiLookup.java`（主）、`logic/AskEngine.java:777-787`（F2）、`logic/AskJeiHints.java`（F3）、可能 `client/jei/JeiFocusMatch.java`（可見性）、新 harness。
- 還原點：改動前 copy 去 `.hermes/backups/<日期>_jei_craft/` ＋ `md5sums.txt`。
- 唔郁：`PackIndex`、`PackAiConfig` 預設、語言檔、trace 格式。

## §5 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| 1 | v1/v2 | 2 : 8 | 反方用 `:8433` 打死「冇 fact」前提 |
| 2 | v3 | 4 : 6 | 反方證「fact 冇入 prompt」（trace 59 條事件）＋`jeiForLlmSlim` 機制；指 D1 單加類別唔夠（focusGroup 過濾）、D3 前提錯 → **v4 重寫：F1 複用 `ensureCoreCraft`／`fromVanillaCrafting`、F2 修 slim 路徑、F3 複用 `AskJeiHints`** |
| 3 | v4（本檔） | 待跑 | — |
