# Plan E v3 — NBT 感知查詢（併入 E-R2 裁決；重用現成 predicate）

> 狀態：**v3（2026-09-17 16:1x）** — E-R1 3:7 → **E-R2 6:4**。SK `go`。⛔ 未實作；目標 正方 ≥8 : 反方 ≤2。

## §0 證據（行號經 R2 逐行核）
**真 reward（2 條）**
- `config/ftbquests/quests/chapters/tetra.snbt:3466-3483`：`3466 rewards: [{`、`3467 id:"6C426604A2A3C3A4"`、`3470 id:"tetra:modular_sword"`、`3474 "sword/blade":"sword/twilight_eye"`、`3475 "sword/guard":"sword/twilight_beak"`、`3482 type:"item"`、`3483 }]`；**`:3484 secret: true`**（同一 quest `1AE7378F1EEBC197`）。
- `goldenagetetra.snbt:194（rewards: [）→ :201 id → :204 id:"tetra:modular_sword" → :206-209 Damage/HideFlags/honing_progress/id uuid → :210 "sword/blade":"sword/clover"`。
**統計（R2 更正）**：50 檔共 **95** 個 `tetra:modular_*` = **icon 80**（77 quest-level ＋ 3 chapter-level：`tetra.snbt:8`、`goldenagetetra.snbt:8`、`tetra_2.snbt:8`）＋ **tasks 13**（其中 **8 個係 gamestage，tetra 只出現喺 task 嘅 `icon:`** → 按新判準唔算取得）＋ **reward 2**。真 item／itemfilter 需求只有 **5 個**：`1.snbt:413`、`tetra.snbt:462`、`:851`、`:865`、`:3490`。
**包自己點分辨版本**：`kubejs/server_scripts/b_a_d/b_a_d_player_damage.js:366/436/1415/1499/1652`、`b_a_d_key_bind.js:604`。
**空框架帶 NBT**（jar 原文）：`data/tetra/recipes/stonecutter.json` 輸出 `sword/blade:sword/stonecutter`＋`sword/hilt:sword/basic_hilt`＋材料欄位 ⇒ 判準唔可以係「有冇 NBT」。

## §1 判準（**重用現成 code，唔准新寫第二套 predicate** — R2 洞）
- 黑名單：**`ToolBuildFacts.SKIP_KEYS`**（已存在）。
- uuid 排除：**`looksLikeUuid(val)`**（已存在；reward 樣本嘅 `id: c2ffc027-…`／`6C426604A2A3C3A4` 正係此類）。
- 部件／材料白名單：**`isMaterialKey`／`isSlotKey`／`isImprovementKey`**（結構白名單，已存在）。
- 零件／材料解析：**`ModularToolScan`／`ItemVariantKeys`／`TetraMaterialItems`**（後者讀 `data/tetra/materials/*.json`，113 檔）。
- 已存在測試：`ToolBuildFactsCheck.java`、`tests/check_tetra_tool_build.py` → 新邏輯要同佢哋對齊（唔准出雙真相源）。
- **分類**：focus 嘅部件／材料欄位同 jar 內 Tetra 配方產出嘅同組欄位比對：相符 → 標準框架（照講 Tetra 配方）；唔符 → 特定版本 → 查 `rewards:`；搵唔到 → 老實講「未收錄」。

## §2 資料來源（只補一件）
- 現行 ingest **已存在**：`logic/PackIndex.java:198-199`（`roots.add(gameDir.resolve("config/ftbquests"))`）、`:2476 private int emitQuestAcquireEdges(String rel, String text, String onlyItemId, boolean forced, List<String> variantTokens, boolean dryRun)`（walk `quests:[]`→`tasks:[]`→`type:"item"`）；`grep REWARDS` = **0** ⇒ 真缺口＝**只補 `rewards:`**（仿 `emitQuestAcquireEdges` 加同款 walk）。
- **刪 `reward_tables/`**：`QuestGuide.java:179-190 isSkippedQuestPath()` 明文跳 `/reward_tables/`（2026-09-15 刻意設計；`PackIndex.isQuestPath():2706` 引用）；本包 8 個 `reward_tables/*.snbt` **0 個**含 `tetra:modular_`（全部 command 18／gamestage 4、冇 `type:"item"`）⇒ 今日零收益、零樣本。若日後要做，另開工單。
- 查詢 stack：`logic/JeiLookupAskTool.java:49-57`（`ItemResolver.stackFromId` → 無 SNBT 時 `new ItemStack(item)` **掉 NBT**）；`ItemResolver.java:190-207`（支援 `id{…}`）、`:220-226`（自註此坑）、`:231-239 stackFromRef`（ref 來源 `AskToolEnv.java:17`）。
- **刪「機器產出」**（無真證據）。

## §3 驗收
- **S1**：真機問 亞巴頓（`tetra:modular_single`）／石刻（`tetra:modular_sword`）→ 答案分開講「特定版本 vs 空框架」，含「合成台／切石機＋木棍」＋「空白模組」；唔准再出「只是空框架，不是取得方式」式否定；**同時**保留 `tests/check_reply_prompt_keys.py:376-392` 嘅 token 約束。
- **S2**：問標準框架（Tetra 配方產出嘅組合）→ 照講切石機＋木棍。
- **S3（正控改用真 item task）**：`tetra.snbt:462／851／865／3490`、`1.snbt:413` 之一 → 引任務名／章節；**負控**：`205729B68F50DC1C.snbt:103-229`（gamestage 任務、tetra 只作 `icon:`）→ **唔准**引。
- **S4**：`minecraft:bedrock` → 仍准講「查唔到」。
- **S5**：**uuid 唔准做比對條件**（fixture：兩件同部件、唔同 uuid → 都要命中）。
- **S6**：`tests/check_*.py`（118 個）；具名紅樣本 `--trace "<instance>/packai/trace" --since 20260901` → RC=1（`ask-20260915-170823-eccentrictome_tome.jsonl:25`）。
- **S7**：新／改 harness 名＋命令寫死（含重用 `ToolBuildFactsCheck`）。
- **S8**：掃描上限＋失敗回退；**分開量**（index build 時間 vs ask 時間），寫明上限數字。

## §4 與 Plan D 嘅關係
- Plan D v11 **只保留 §1（政策文字 9 處）**併入本計畫第 4 件；D v11 §2（提示行）與 F-C 一律唔做；D v11 S5 併入本檔 S1。
- 還原：改動前 copy 去 `.hermes/backups/2026-09-17_nbt_lookup/` ＋ `md5sums.txt`；jar 由 `mc_mod_deploy_jar.py` 自動備份。
- 唔郁：trace 事件／欄位、`PackAiConfig` 預設、卡抑制政策。

## §5 Review 記錄
| 輪 | 比分 | 關鍵 |
|---|---|---|
| 1 | 3 : 7 | 我引嘅任務證據全係 `icon:`；「空框架＝冇 NBT」被 jar 推翻 |
| 2 | 6 : 4 | reward 引用對；統計算錯；`reward_tables` 零樣本；**現成 predicate 要重用** |
| 3 | 本檔 | — |
