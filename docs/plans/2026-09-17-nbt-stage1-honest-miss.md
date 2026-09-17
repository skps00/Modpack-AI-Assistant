# Plan F v1（第 1 階段）— 特製版工具：老實講「未收錄」，唔再用空框架配方頂替

> 狀態：**v1（2026-09-17 16:0x）** — SK `go`（批准開始第 1 階段＝先前嘅 `B`）。範圍**只做一件**。⛔ 未實作；先過一次挑錯。

## §1 目標（一件）
手持工具嘅 **NBT 部件／材料**對唔上任何 Tetra 官方配方產出 → 答案**唔准**用「空框架合成（切石機＋木棍）」嚟答，亦唔准出「只是空框架，不是取得方式」式否定；改為**老實講**「未收錄呢個版本嘅取得途徑」＋（可選）一句「似乎同任務內容有關」（**唔指名**任務、**唔講**秘密任務內容）。

判定分兩類：
- **對得上**（NBT 部件／材料組合 == jar 內 `data/tetra/recipes/*.json` 某配方結果嘅同組欄位）→ 當標準框架 → **照舊**講 Tetra 配方。
- **對唔上** → 特製版 → 走上面「老實講」路線。

## §2 技術落點（全部**重用**現成，唔准新寫 predicate）
1. **保留手持 NBT**：`logic/JeiLookupAskTool.java:49-57` 現時用 `ItemResolver.stackFromId(args.itemId)` → 無 SNBT 時 `new ItemStack(item)` **掉 NBT**。改：當 `args.itemId` 同 `env.stack` 同 registry id → **唔覆蓋**，直接用 `env.stack`；需要 ref 時用 `ItemResolver.stackFromRef`（`:231-239`；ref 來源 `AskToolEnv.java:17`）。相關坑：`ItemResolver.java:220-226`（自註）。
2. **NBT 欄位判斷**：`ToolBuildFacts.SKIP_KEYS`（`ToolBuildFacts.java:24`，現為 `private`）、`looksLikeUuid`／`isMaterialKey`／`isSlotKey`／`isImprovementKey`（package-private，`:220-266`）→ 新 code **必須留喺 `com.skps9.packai.logic`**；要放寬可見性就逐個列出。
3. **文字版比對**（任務／配方文字）：`ItemVariantKeysText.mentionsAny`（`:208`）；NBT 文字解析用 `TagParser.parseTag → ModularToolScan.fromTag`（`ModularToolScan.java:118`）。
4. **答案出口（唯一）**：`logic/HonestMiss.java:109/121` → `ReplyLang.acquireIndexMiss`／`askMissAcquirePlayer`（`ReplyLang.java:1169/1187`；lang key **`packai.reply.acquire_index_miss`**）。`AskEngine.java:350/457/1010` 用 `HonestMiss.shouldPinAcquireMiss(...)`（pin 條件＝acquire 空）→ **本階段要改嘅就係呢條閘嘅輸入**。
5. **唔郁**：`PackAiConfig` 預設（含 `showHiddenQuests=false`）、`QuestGuide.SPOILER_BOOL_KEYS:44`／`isSpoilerHiddenQuestObject:1159`、卡抑制政策、trace 格式、語言檔（**本階段唔改政策文字**——留待第 2 階段）。

## §3 驗收（全部機檢；樣本用**兩個包嘅真數據**）
- **S1（核心）**：真機問「石刻點嚟」（手持特製版：NBT 部件唔對應官方配方）→ 答案**必須**含 lang key `packai.reply.acquire_index_miss` 嘅文案（「未收錄」式），**唔准**含「只是空框架」／「不是取得方式」字樣。
- **S2（唔可以退步）**：手持**標準框架**（NBT == 官方配方結果）→ 答案照舊講切石機＋木棍。
- **S3（負控）**：`minecraft:bedrock` → 仍准講「查唔到」，唔准亂引。
- **S4（fixture，跨包真數據）**：
 - AI_test_NFWC_DIM：`goldenagetetra.snbt:194-210`（帶 NBT 獎勵）、`tetra.snbt:3466-3483`（**secret** → 唔准指名）。
 - FTB Skies Expert：`tetra.snbt:110-120`（**帶 NBT 任務要求**）、`tetra.snbt:441-450`（帶 NBT 獎勵）、`tetra.snbt:34-36`／`:153-155`（純圖案 → 唔出聲）。
 - 若任何樣本被證明「現行程式本身已擋」（假綠）→ 該條**唔計**，要換真樣本（做之前先驗）。
- **S5**：`tests/check_*.py` 全跑（今日實測 **118 個、0 個失敗**）；已知紅樣本要帶 `--trace "<instance>/packai/trace" --since 20260901`（RC=1）。
- **S6**：玩家可見文字零 `file:line`／`.js`／內部 id。
- **S7**：新／改 harness 名＋命令寫死（含重用 `ToolBuildFactsCheck`／`tests/check_tetra_tool_build.py`）。
- **S8**：唔准令 ask 變慢（量 before/after，寫明上限）；任何新掃描要 fail-open。
- **S9**：唔准改動 `showHiddenQuests` 或 spoiler 規則（本階段唔碰政策）。

## §4 風險／還原
- 最壞情況：答案由「講錯」變「唔肯講」→ S2 擋（標準框架照講）；或者新閘令正常物品也走 miss 文案 → S3＋S5 擋。
- 還原點：改動前 copy 到 `.hermes/backups/2026-09-17_stage1_honest_miss/` ＋ `md5sums.txt`；jar 由 `mc_mod_deploy_jar.py` 自動備份。
- 檔案（預估）：`logic/JeiLookupAskTool.java`、`logic/HonestMiss.java`（或新窄判斷）、`client/service/AskService.java`、`logic/AskEngine.java`、新 harness。

## §5 與後續關係
- 第 2 階段（未批准）：任務要求都要比對 NBT、新增任務**獎勵**來源、政策文字 9 處。本階段過關＋真機驗收通過才開。

## §6 Review 記錄
| 輪 | 比分 | 關鍵 |
|---|---|---|
| 1 | 待跑 | — |

## §7 v2 修正（依 F-R1；5:5 → 目標 8:2）
**A. 範圍更正（最關鍵）**：第 1 階段**必須同時改政策文字**——原本寫「唔郁語言檔」同 S1「答案唔准再講空框架否定」**自相矛盾**（嗰句由政策文字產生）。
- 政策文字 = **9 處**：`assets/packai/lang/{zh_cn,en_us,zh_tw}.json` × `llm_style`／`llm_style_notools`／`tool_build`（行號 zh_cn `388/389/484`、en_us `392/393/484`、zh_tw `392/393/484`）。
- 兩個閘要同時滿足：`tests/check_reply_prompt_keys.py:376-392`（保留 `empty-frame`／`empty modular`／`空白模組`繁簡＋ban token、`:391` `[TOOL_BUILD]` 留在 style）、`tests/check_prompt_notools_no_toolwords.py`（`llm_style_notools` 內唔准提工具名）。
- 措辭要貼包原文（「切石机＋木棍」）＋標明「空白模組劍合成／材料版本」；**唔准**剷走訊息（S2 靠佢）。
**B. 錨點更正（R1 逐條核出）**
- `AskToolEnv.java`：`ItemRef held` 係 **:15**（我原寫 :17 係 `jeiStationTemplate`）。
- `ItemResolver.java:220-226` 實為 `bareRegistryId` 嘅 **brace 截斷邏輯**（唔係「自註此坑」）。
- pin 條件**唔止**「acquire 空」：`AskEngine.java:350` 另要 `!JeiInfoFacts.hasAny(jeiSummary) && jeiInfo.isEmpty()`；`:457` 要 `loop.missPin()`；`:1010` 用 `obtainRecipes`（offline 路）。
- `HonestMiss.java:109` 同時出 `localAcquireHeader`（補列）。
**C. 刪減**：刪「（可選）一句任務相關提示」（屬第 2 階段；第 1 階段做唔到）；`client/service/AskService.java` 若無真落點就**唔列入改動檔**。
**D. 樣本更正**：FTB Skies Expert `tetra.snbt:110-120` 實為**獎勵**（`rewards: [` 在 `:82`、`tasks: [` 在 `:130`），唔係任務要求 → 標明；每條樣本要寫**紅／綠條件**；`minecraft:bedrock` 今日已綠 ⇒ 唔算樣本（保留作 S3 弱斷言）。
**E. 已知限制（明寫）**：jar `data/tetra/recipes/` 只有 **27 個 json**（13 個提及 modular）⇒「對得上」判定覆蓋面窄，jar 以外（kubejs／其他 datapack）一律唔在範圍。
**F. 驗收收口**：S7 寫死新 harness 名＋命令（重用 `ToolBuildFactsCheck.java`／`tests/check_tetra_tool_build.py`）；S8 寫明**上限數字**同 fail-open 條件；S9 用 `git diff --stat` 機檢（唔准碰 `showHiddenQuests`／spoiler 規則）。
