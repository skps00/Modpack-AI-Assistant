# Plan E v4 — NBT 感知查詢（併入 E-R3 ＋ SK 兩條決定）

> 狀態：**v4（2026-09-17 16:4x）** — E-R1 3:7 → E-R2 6:4 → E-R3 4:6；SK 已答兩條產品決定。⛔ 未實作；目標 正方 ≥8 : 反方 ≤2。

## §0 SK 決定（2026-09-17）
1. **秘密任務獎勵（`secret: true`）＝唔講**（唔指名任務、唔列獎勵內容）＝ `1b`；但**可以少量提示** ⇒ 答案格式＝「未收錄呢個版本嘅取得途徑」＋最多一句「似乎同任務內容有關」。實作：**secret reward 唔注入**（`tetra.snbt:3484 secret: true` 屬此類）。
2. **只作圖案（`icon:`）嘅個案＝唔出聲**（`2a`）；由圖案推論「有得攞」係錯 ⇒ **一律唔注入、唔提示**。

## §1 判準（重用現成；R3 更正）
- **「圖案唔算取得途徑」已經有實作**：`QuestGuide.stripQuestIcons`（`QuestGuide.java:1529`，註解：decorative FTB icons are not treated as task/reward item ids）＋ `itemsInRange`（`:1465`）＋專屬鏡像測試 `tests/check_quest_strip_icons.py` ⇒ **唔准新寫第二套**。
- **文字版孿生類**：`ItemVariantKeysText`（`:208 mentionsAny`）—— 現行 `emitQuestAcquireEdges` 正是用佢（`PackIndex.java:2534`）；E v3 只列 `ItemVariantKeys`（要 `ItemStack`）＝錯。
- **唔可以當文字來源**：`ModularToolScan`（只有 `scan(ItemStack)`／`fromTag`，`:96/:118`）、`TetraMaterialItems`（client `ResourceManager`，`:472`）⇒ 兩者食唔到 quest SNBT 文字。
- **可見性**：`ToolBuildFacts.SKIP_KEYS` 係 `private`（`ToolBuildFacts.java:24`）；`looksLikeUuid`／`isMaterialKey`／`isSlotKey`／`isImprovementKey` 係 package-private（`:220-266`）⇒ 新 code 必須留喺 `com.skps9.packai.logic`；要調 visibility 就明確列出。
- **uuid**：`looksLikeUuid` 只認 36 字 4 dash ⇒ `6C426604A2A3C3A4`（16 hex）**唔會**命中，擋佢嘅係 `skipKey("id")`；真 uuid 例＝`c2ffc027-86aa-4b6b-813c-27ce53472a7f`。
- **分類**：focus 部件／材料欄位 vs jar 內 Tetra 配方產出同組欄位 → 相符＝標準框架（照講 Tetra 配方）；唔符＝特定版本 → 查 `rewards:`（**排除 `secret: true`**）；搵唔到 → 老實講「未收錄」＋（可選）一句任務相關提示。
- ⚠️ **雙真相源風險**：空框架政策已有兩源（`ModularFrameCards.shouldDropFrameCard`＋`ModularFrameCardsCheck.java`／`AskService.suppressModularFrameCards:2643`／prompt pin `check_reply_prompt_keys.py:376-392`）→ 新判準會變第三源，要明確寫「只加資料來源，唔改政策」。

## §2 資料來源（只補 `rewards:`）
- 已存在：`PackIndex.java:198-199`（roots 加 `config/ftbquests`）、`:2476 emitQuestAcquireEdges(...)`（walk `quests:[]`→`tasks:[]`→`type:"item"`）；`grep REWARDS` = **0** ⇒ 只補同款 `rewards:` walk。
- `reward_tables/` **維持刪除**（`QuestGuide.java:179-190 isSkippedQuestPath()` 明文跳過；本包 8 檔 0 個含 `tetra:modular_`）。
- 查詢 stack：`logic/JeiLookupAskTool.java:49-57`（無 SNBT 時掉 NBT）；`ItemResolver.java:190-207`／`:220-226`／`:231-239 stackFromRef`（ref＝`AskToolEnv.java:17`）。
- 刪「機器產出」（無真證據）。

## §3 驗收
- **S1（收窄，R3 建議 B）**：真機問石刻／亞巴頓 → 若手持 NBT 對唔上標準配方 → 答案必須「老實講未收錄」（＋可選一句任務相關提示），**唔准**出「只是空框架，不是取得方式」式否定；同時保留 `tests/check_reply_prompt_keys.py:376-392` token。
- **S2**：標準框架 → 照講切石機＋木棍。
- **S3**：正控＝真 item task（`tetra.snbt:462/851/865/3490`、`1.snbt:413`）→ 引任務名；**負控 1**＝`205729B68F50DC1C.snbt:103-229`（gamestage＋只作 `icon:`）→ **唔准**引；**負控 2**＝`tetra.snbt:3466-3483` 嗰個 **secret reward** → **唔准**指名。
- **S4**：`minecraft:bedrock` → 仍准講「查唔到」。
- **S5**：uuid 唔准做比對條件（同部件、唔同 `id` → 都要命中）。
- **S6**：`tests/check_*.py`（118）；具名紅樣本 `--trace "<instance>/packai/trace" --since 20260901` → RC=1（`ask-20260915-170823-eccentrictome_tome.jsonl:25`）。
- **S7**：harness 名＋命令寫死（重用 `ToolBuildFactsCheck`）。
- **S8**：掃描上限＋失敗回退；分開量 index build vs ask，寫明上限數字。

## §4 與 Plan D 嘅關係
- Plan D v11 只保留 §1（政策文字 9 處）併入本計畫；其餘唔做；D v11 S5 併入本檔 S1。
- 還原：改動前 copy 去 `.hermes/backups/2026-09-17_nbt_lookup/` ＋ `md5sums.txt`；jar 由 `mc_mod_deploy_jar.py` 自動備份。
- 唔郁：trace 事件／欄位、`PackAiConfig` 預設、卡抑制政策。

## §5 Review 記錄
| 輪 | 比分 | 關鍵 |
|---|---|---|
| 1 | 3 : 7 | 我引嘅任務證據全係 `icon:`；「空框架＝冇 NBT」被 jar 推翻 |
| 2 | 6 : 4 | reward 引用對；統計算錯；`reward_tables` 零樣本；現成 predicate 要重用 |
| 3 | 4 : 6 | 「圖案唔算取得」原來早已實作；`ItemVariantKeysText` 漏；可見性／runtime 耦合唔相容 → 交 SK |
| 4 | 本檔 | SK 兩決定已入 §0；等 R4 |
