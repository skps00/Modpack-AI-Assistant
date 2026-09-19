# R2 反方 review — item-info-completeness plan（v2）

- 角色：**反方（adversarial）**。審查對象：`docs/plans/2026-09-19-item-info-completeness-plan.md`（v2，last commit `9141e45`）
- 環境（全部親測）：MC **1.19.2** / Forge **43.4.5** / pack **FTB Skies Expert**（`packai_sandbox_ftb`）/ packai `mod_version=0.2.3`
- 證據：19 條 trace `C:/Users/skps9/AppData/Local/Temp/autotest_results_20260919-231137/`（含 `latest.log` 6.1MB）、`<ftb sandbox>/minecraft/config/packai/jar-cache/`（360 shard / manifest 358 jar）、`forge/1.19.2/src/**`、`tests/check_*.py`
- 結論：**正方 3 : 反方 7（go = false）**
- 本輪只讀；唯一寫入檔＝本報告。

---

## §A 我親核而 v2 成立嘅部分（先講，唔想冤枉）

| v2 主張 | 我嘅獨立核實 |
|---|---|
| §0.2 ALL 161/121 = **75%** | ✅ 實跑 `python tools/check_item_info_coverage.py`：`available=161 covered=121 => 75%` |
| §0.2 DETECTABLE 91/82 = **90%** | ✅ 同一次輸出：`available=91 covered=82 => 90% (excludes fishing,script,tags,trade,worldgen)` |
| §0.1 儀器分子已修成 `\|available ∩ covered\|` | ✅ 讀 `tools/check_item_info_coverage.py:30-34`：`avail` 用 tools+facts，`cov=[c for c in avail if …ans]`，`a2/c2` 再過 `EXCLUDED`（:21）→ 確係交集，唔再係 v1 嘅「答案有嘅類別」。EXCLUDED 已凍結成常數 ✅ |
| §0.2 per-category miss（script16/tags11/guide5/trade2/fishing2/tooltip2/usages1/loot1） | ✅ 逐字重現 |
| §0.2 答案長度 1262–2012、code 層無 cap | ✅ `min/max: 1262 2012` |
| §0.3-1 jar-cache 有 `L\|chests/village/moon/blacksmith` | ✅ shard `9a59d6f03d4d.json`，`jar=ad_astra-forge-1.19.2-1.12.7.jar`，值＝`["L|chests/village/moon/blacksmith", "U|crafting_shaped|ad_astra:oxygen_loader"]`（逐字相同） |
| §0.3-1 **0/19** 含 `[JAR]`、`send.facts` 無 `chests/` | ✅ `grep -l '\[JAR\]'` = 0 檔；`send.facts` 含 `chests/` = 0 次。另核：en_us 標頭真值＝`'[JAR]'`（`en_us.json` `packai.reply.jar_header`），trace `replyLanguage="en_us"` → **唔係字串不匹配造成嘅假陰性** |
| §0.3-1 氧氣罐答「No loot…indexed」 | ✅ `display.body.final` 第 3 行逐字：「No loot, chest, trade or fishing path for it is indexed in the pack」 |
| §0.3-2 `JarLightIndex` grep `structure\|worldgen` = 0 | ✅ 0 hits；`isLootEntry/lootKeyFromPath`（:171-187）只出裸 table key |
| §0.3-3 `acquire` 16/19 空 | ✅ 工具直方圖 `acquire=19` 次呼叫，但 `AcquireAskTool.java:42` `return lines.isEmpty() ? "" : …`、:44 `catch → ""`；氧氣罐 trace 嘅 `acquire` tool.result 係空字串 |
| §0.3-4 caps 值 | ✅ `JarLightIndex.java:56-59` `MAX_LOOT_PER_JAR=150 / MAX_FACTS_PER_ITEM=8 / MAX_ASK_LINES=4`；`AskToolContext.java:31` `MAX_ACQUIRE_LINES_FULL=12`；`PackAiConfig.java:351` `maxFacts=24` |
| §0.3-5 guide_fetch 真 bug | ✅ 5 次呼叫中 3 次回 `worn_notebook/enchantments/how_to_enchant`；根因 `// B2: title search only when item path empty` ✅ |
| §0.3-6 `git status --porcelain` = 126（71M/53??/2D） | ✅ 實跑 126 |
| §0.3-7 `tests/check_tool_schema_stable.py:108` | ✅ `assert forge_only == ["knowledge_lookup"]`（:108）→ 加新工具會變**新紅** |
| §0.3-8「93 個 *Check.java vs 50 註冊」 | ❌ **錯**（見 B6） |
| §3-D2 卡落位引用 | ✅ `RecipeEmbed.java:747` 「Sources footer always last.」；`interleaveEmissionCards` :749-752 ✅；`tests/check_ask_card_fallback.py:309`（doc）＋`:842`（`card marker must not sit inside trailing prose`）✅ |
| §2-D2 anchoring | ✅ `AskJeiHints.java:274` `ReplySources.HEADER.matcher(body)`；`ReplySources.java:11` `HEADER=(?m)(【來源】\|【来源】\|\[Sources\])` |
| §4-D5 `worldgen_lookup` 註冊點 / 0 呼叫 / 注入閘 | ✅ 註冊 `AskEngine.java:48`；19 trace 工具直方圖無 `worldgen_lookup`（0/19）；注入閘 `AskEngine.java:695-696`（`WorldgenFacts.looksLikeQuery` → `WorldgenIndex.lookup`）；觸發詞表實際在 `WorldgenFacts.java:46-63` |
| §4-D5 `Configured(size)` / `Placed(count,countRange,heightRange)` | ✅ `WorldgenFacts.java:38/40` record；parse 在 :253-289 |
| §5-D3.2 `PatchouliGuideLookup` 檢索 bug | ✅ bug 真（但路徑錯，見 B3） |
| §1-D1.2 lang key `packai.reply.loot_table_obtain` | ✅ `en_us.json:472` = `"Loot table: %s"`；`Plainify` 有 `LOOT_TO_TABLE → ReplyLang.lootTableObtain` |
| §8 sampler `--mode` 唔存在、GAME 預設唔係沙盒 | ✅ 只有 `--per-cat/--random-n/--seed/--pinned`（`tools/cardplace_sampler.py:264-267`），`GAME` 預設 `C:/Users/skps9/Documents/packai_dev_game`（:17）。**但** `PACKAI_GAME_DIR` 其實已經支援（:17 `os.environ.get`）→ 見 B9 |
| §7 還原方案 | ✅ 126 項 dirty 非本 plan 造成、逐檔 `git checkout --`、禁用裸 checkout、`RecipeEmbed.java` sha 不變 → 合理 |

判斷：v2 把 v1 嘅假數字、假 mapping、假 sampler 全部修好，**儀器 bug 確實修好**，D1 縮到 table-id 級亦係對嘅方向。以下係仍然過唔到嘅位。

---

## §B Blockers（按嚴重度，只列真正卡死嘅）

### B1（CRITICAL，linchpin 嘅真身）D0／D5 要修嘅「facts 管道」在實測配置下**根本冇接通**——加料入 `facts` 到唔到模型

- 代碼：`AskEngine.java:823-830`（`completeWithTools`）：

  ```java
  boolean capable = capableForTools();                       // :825
  List<String> promptFacts = capable ? List.of() : factsLive; // :826  ← capable ⇒ 整個 facts 牆變空
  return llm.completeRound(question, held, hotbarRefs, focus, promptFacts, …);  // :827-830
  ```
  `capableForTools()` = `LlmClient.toolsOffered(llm.lastBase())`（`AskEngine.java:787-789`）；而 `firstAsk` 一旦 offer 就用 `capableLoop` → `completeWithTools`（`AskToolLoop.java:391-402, 408-409`）。
- 實測：`latest.log` 有 **76 行 `toolsOffered=true sendTools=true`**（＝76 次 LLM 呼叫全部走 capable 路徑）；19/19 trace 每條有 3–9 次 `tool.call`。
- **煙槍**：`send.facts` 內嘅 `graphFacts` 欄位係 `LlmClient` 收到嘅第 5 個參數（簽名 `LlmClient.java:284-296` 第 5 個係 `List<String> graphFacts`），即**就係 AskEngine 組裝出嘅 facts 牆**（capable 時＝空）。我逐條 trace 逐 round 數：**`graphFacts` 尺寸 19/19、每一 round 都係 `[]`**（例：`ask-20260919-231546-ad_astra_oxygen_tank.jsonl` rounds=4 → `[0,0,0,0]`）。
- 意思：v2 §0.3-3 只把「graphFacts 19/19 空」當成另一條獨立缺陷，無睇出**佢就係「facts 牆在真實配置下永遠係空」嘅證據**。所以：
  - **D0.2**（「保證每件 item 至少 top-N 條 `L|`／`U|`／`R|` 入 facts」）在 capable 配置下**零效果**——啲 line 加咗都會喺 :826 被丟；
  - **D5.1**（「無論問題點問都注入」worldgen facts）同樣被 :826 丟；
  - **A0**（「fixture item prompt **必須含** `chests/village/moon/blacksmith`」）如果 headless harness 走 no-tools 路徑，會**綠**，而真機仍然盲——正正就係 plan 自己講要防嘅假綠（D0.4）；A0b／A5 真機必然失敗，2×12 分鐘真機 run 白做。
- D0.1 嘅 debug 打點位置（`JarLightIndex.factsForAsk`、讀 :272/292、組裝 :481、注入 :692-693）**唔包含 :823-830**，所以「診斷先行」都診唔到這個真因。
- **FLIP**：任一成立即撤回 → ① 示出一條 trace 其 `graphFacts` 非空（證明 facts 牆真到過 capable 模型）；或 ② 把 D0.2 改成**經現有 tool**（`acquire` 或 `purpose_lookup`）交付 loot refs（並寫明改邊個 tool、預算幾多字），同時把 D0.1 打點加上 `AskEngine.java:823-830`；A0 加一條**capable 模式**負控（tools on ⇒ 舊行為必紅）。

### B2（CRITICAL）儀器「修好分子」但**分母仍然由 prompt 樣板／問題文字餵**——90% 同 A4 逐類別目標唔係量度「pack 資料送到玩家」

`avail` 由 `tools + facts` blob 掃出（`check_item_info_coverage.py:30`），而 `facts` blob ＝ `send.facts` content，**包含客戶端自己嘅提示文字同問題文字**。逐類別追觸發字串（實測）：

| 類別 | 觸發來源 | 反方判斷 |
|---|---|---|
| `recipe` | `[RECIPE_CARDS] UI cards in this order…`（19/19，係提示 lead，唔係 pack 資料） | 分母被樣板撐大 |
| `usages` | 問題字串「…**used for** in this pack?」（19/19，問題模板本身） | 同上 |
| `loot` | 提示字串「If local acquire lists **loot/chest/fish** before quest…」（19/19） | 同上 ⇒「loot 1→0」係量度模型有冇覆述提示詞 |
| `quest` | 提示字串「role=**quest** is a quest reward/task」（19/19） | 同上 |
| `tags` | `Hold §e§oCtrl§r§7 for **Tags**`（JEI 提示）＋ `NBT: 1 **tag(s)**`（NBT 標籤**數量**，唔係 item tag）13/19；全 corpus 真正 item tag id 只有 **1 個**：`#minecraft:piglin_loved`（`...advancedperipherals_inventory_manager.jsonl` 嘅 JEI 輸入行） | 「tags available 13 / miss 11」係**幻影** |
| `guide` | 工具自身回覆頭 `[Quest guide] Related quests found`（把「任務書」同 Patchouli guide 混為一類） | 部分失真 |

旁證：用 **tool.result only**（剔除 `send.facts` 樣板）重算 → `72/80 = 90%`，但成分唔同（miss = usages 1 / guide 4 / tooltip 3，loot 只喺 tetra 一條真出現過且有覆蓋）。即係話：**同一句「90%」可以係兩組唔同事實**，plan 揀嘅嗰組係被樣板污染嘅。
- 影響：A4（「detectable 82/91 → 100%；tags 11→0、loot 1→0、usages 1→0」）可以用「叫模型覆述提示詞」達標，而玩家冇多拎到任何 pack 資料 ⇒ R1 嘅「A1 不可證偽」**未真正解決**，只係由「分母任縮」變成「分母混入樣板」。
- 另外 A4 自相矛盾：`tags` 已被 `EXCLUDED` 剔出 detectable 子集（:21），但 A4 又同時 pre-register「tags 11→0」做目標——**一個被排除出主指標嘅類別，唔可能貢獻「detectable → 100%」**。
- **FLIP**：① 把 `avail` 觸發源凍結成「只掃 tool.result，且觸發字串必須係 pack 資料形狀」（例如真 table id／真 `#ns:tag`）；② 用新定義重出 baseline 並示範 A4 **現時係紅**；③ A4 明寫哪些類別入／唔入主指標，刪走 `tags` 目標或把它升回主指標。

### B3（HIGH）§6 白名單**仍然唔閉合**，而 v2 明文聲稱「R1 點名嘅缺口已補齊」——D1/D2/D3/D5 有幾項在「只准改以下檔案」下**做唔到**

| 缺口 | 證據 |
|---|---|
| `logic/PurposeLookupAskTool.java` **唔在白名單** | D3.1「擴充現有 `purpose_lookup` 輸出」＝要改此檔（實作在 `logic/PurposeLookupAskTool.java:33-46`，輸出只由 `env.purposeTooltip` + `AskPurposeContext.buildPurposeBlock` 砌） |
| **tags 資料源**唔在白名單 | 全 repo 唯一讀 item tag 嘅地方係 `client/jei/IngredientReqHints.java:24,204-251`（`TagKey<Item>`）。冇任何 whitelisted 檔可以拎到 item tag 清單 |
| `logic/PatchouliGuideLookup.java` **路徑唔存在** | 真檔在 `client/patchouli/PatchouliGuideLookup.java`（`// B2` 在 :54-63）。§0.3 話「我逐條親核全部成立」但把 R1 漏掉嘅目錄前綴補成**錯嘅** `logic/` → 實作者會去改一個唔存在嘅檔 |
| `logic/ReplyLang.java` 唔在白名單 | D2.4「所有新字串走 lang key」＋D2.2 生成「補充」段 → 需要新 key **同** 讀 key 嘅 method（現有 pattern 全部在 `ReplyLang.java`，例 :773-787）；只加 lang json 唔夠 |
| `research/gen_tmp_check.py` 唔在白名單 | §6-10 加新 harness `AskFactsRoutesCheck.java`，但註冊係靠 `tmp-check.gradle`（生成器 `research/gen_tmp_check.py`，見 `tmp-check.gradle:1` AUTO-GENERATED）；§9 又註明 `tmp-check.gradle` 唔可以入 repo → **A0/A1/A2/A6 跑唔到**（除非明示批准重生） |
| `LootForwardIndex.java`（R1 已點名）仍被排除 | D0.2 賣點「`blocks/*`＝**挖方塊掉落**」直接受 `LootForwardIndex.isTrivialBlockSelfLoot`（`logic/LootForwardIndex.java:106-120`）管制：**方塊掉自己**嗰啲 ref 係被刻意剔走（:126-128）→ 賣點同實作相反 |
- **FLIP**：把上述 6 條逐一路徑寫入 §6（或刪走相應 deliverable），並重新聲明白名單閉合。

### B4（HIGH）D3.1「唔加新 tool 就夠」係錯命題——缺嘅唔係工具，係**資料源**

`purpose_lookup` 今日回嘅「tags」內容＝JEI 提示字串 + NBT tag 數量（例：`[PURPOSE] … NBT: 1 tag(s) … Hold §e§oCtrl§r§7 for Tags`），唔係 item tag id。要出真 tag 清單，需要一個 client 側 tag provider（`TagKey<Item>`／`IngredientReqHints` 路徑），而該檔唔在白名單（B3）；同時「tags 11→0」本身係幻影（B2）。§5 把「避免 schema gate 變紅」當成主要約束，反而掩蓋咗真 constraint。
- **FLIP**：指出一個**白名單內**嘅現成 API 可以拎到某 item 嘅 item tag ids，並示範它在 ≤N 行／≤X 字下回得出（連 3 個真例）。

### B5（MED）D1.1 嘅 kind／table schema 對唔上真實 ref 空間（覆蓋率約 8 成，且 `gameplay/*` 標錯）

我對 FTB jar-cache 全量統計（19,292 條 ref；其中 `L|` 5,160 條）：
`blocks 2989 / chests 727 / entities 430 / inject 279 / gameplay 162 / actions 139 / archaeology 90 / extractor 69 / spoils 47 / forged 47 / misc 42 / artifact 35 / custom 33 / … 另有冇斜線嘅裸 key（`bastion_scrolls`、`holosphere_reward`、`artifact`）`。
- D1.1 只列 `chests/* / blocks/* / entities/* / gameplay/fishing` → 約 **82%** L refs 有標籤，其餘（`inject/*` 279、`actions/*`、`archaeology/*`、`extractor/*`、`spoils/*`、`forged/*`…）冇 kind，會 fall through 或者亂標。
- `gameplay/*` ≠ 釣魚：實際多數係 `gameplay/piglin_bartering` 之類（162 條）；只有 `gameplay/fishing` 係釣魚。
- `inject/chests/end_city_treasure` 經 D1.2 嘅 `en_us.json:472`「Loot table: %s」人化後＝**「Loot table: inject/chests/end_city_treasure」**——玩家可讀性差，但 plan 冇為此定標準。
- **FLIP**：列出完整 prefix 集合 + 一個 default kind fallback，並貼出每個 prefix 至少 1 條人化後字串（玩家視角）供 SK 判收貨。

### B6（MED）§0.3「逐條親核，全部成立」至少兩條**核錯**——包括一條係 v2 自己加嘅「95/50」糾正

1. §0.3-8「93 個 `*Check.java` 但只註冊 50 個任務」：實測 **forge/1.19.2/src 有 50 個 `*Check.java`，`tmp-check.gradle` 註冊 50 個 unique task → 50/50，冇 gap**。93 係把**暫停中嘅 neoforge 樹（41 個）**＋ forge（50）＋2 個 `.hermes/backups` 一齊數出嚟（41+52=93）。→ A7「50/50」**唔需要改寫**，該條「糾正」係無中生有。
2. §0.3-4「224/6842 物品已超 8 refs」：對住 plan 自己指向嘅 FTB jar-cache 重算＝**9,840 items，其中 476 個 ≥8 refs（19,292 refs 總數）**；數字唔重現（方向（有唔少 item 超 cap）成立，但量級／基數唔同）。
- 影響： §0.3 係 v2 全部設計嘅地基（「我親核過所以唔再爭」），但其中至少 2 條唔成立 ⇒ 其餘「已核」結論亦唔應享有免檢待遇。
- **FLIP**：公佈產生呢兩個數字嘅**確切快照＋腳本＋時間戳**。

### B7（MED）D2 插入點：plan 只約束**模型**唔准放 card marker，但 `RecipeEmbed` 會自己派卡入去嗰一段

`interleaveEmissionCards`（`RecipeEmbed.java:749-768`）＝ `splitTrailingSources` → `placeEmissionCardsByRef`（:774-818）→ **`disperseUnplacedEmissionCards`**（:766-767）→ `blocks.addAll(sourceParts)`（:768）。即：就算「補充」段完全冇 `[card:N]`，剩餘卡仍可能被 disperse 落去該段（編號步驟區）。plan 嘅規則（D2.3「唔准含 card marker」）只管模型輸出，管唔到這個 fallback。
A3 嘅證據（`tests/check_ask_card_fallback.py` 保持綠）只覆蓋兩個固定 fixture（:826-842、:854-858），**冇一個 fixture 含 D2 新增段落** ⇒ A3 可以綠而真機視覺仍然 regress。
- **FLIP**：新增一個「有卡 + 有補充段」fixture，斷言卡次序／補充段位置（或由 `RecipeEmbed` 側明文排除該段並寫入測試）。

### B8（LOW-MED）D5 嘅資料源論述指向錯嘅元件；且 D5 命中同一死管（B1）

`JarLightIndex` 只食 `recipes` + `loot_tables`（`JarLightIndex.java:164-175, 364-368`），**但 worldgen 係另一條路**：`WorldgenIndex` 自己掃 `mods/*.jar`（`logic/WorldgenIndex.java:155-205`，`MAX_JARS=400` :28）＋鬆散 datapack（:113-127）。所以 §4「核 jar 掃描範圍真係包 worldgen（config 只寫 recipes|loot_tables）」係把兩個元件混為一談；`PackAiConfig.java:444` 嗰段描述只講 jar-cache。觸發詞亦唔在 `WorldgenIndex.lookup`，而在 `WorldgenFacts.looksLikeQuery`（:46-63）。
即使 D5.1 改啱閘，注入路徑仍受 B1 影響（facts ⇒ capable 丟棄）⇒ A5 真機同樣會紅。
- **FLIP**：先貼出沙盒 `WorldgenIndex` 實際索引到幾多條 entry（我所有探針都見唔到 worldgen 資料），再講 D5 點交付。

### B9（LOW）兩個次要失真

- §8 講要「新增支援 `PACKAI_GAME_DIR`」：其實 `tools/cardplace_sampler.py:17` **已經支援**（`os.environ.get("PACKAI_GAME_DIR", …)`）⇒ 只剩 `--mode loot-only` 係新工作（claim 誇大工作量）。
- §9 註腳寫「python 閘 = baseline 122 綠＋1 已知紅（123 檔）」但表內又留「（113? 見註）」——pre-registered 表唔應該有未決數字；`%TEMP%\gate_baseline_20260919.txt` 存在（123 行）✅，請寫死。

---

## §C §9 驗收表逐項可證偽性（R1 核心指控嘅現況）

| 項 | 可證偽？ | 反方判斷 |
|---|---|---|
| A0 | ⚠️ 部分 | 冇分 tools-on／tools-off 兩條路 ⇒ 可以「用 no-tools 路徑綠」掩蓋真機（B1）。負控（cap=1 必跌）有效但只驗 cap |
| A0b | ✅ | 真機、字串明確（「Moon village blacksmith chest」＋唔准再出「no loot…indexed」）——**但按現設計必然紅**（B1） |
| A1／A2 | ✅ | 逐字 expected，可以 |
| A3 | ⚠️ | 只有 2 個舊 fixture，唔含新段落（B7） |
| A4 | ❌ | 分母混樣板（B2）＋`tags` 自相矛盾（EXCLUDED 又做目標） |
| A5 | ⚠️ | 「每次重新隨機抽」設計正確；但依賴同一死管道（B8/B1） |
| A6 | ✅ | 無關查詢要回空，可判 |
| A7 | ⚠️ | 數字錯（B6：真值 50/50；123 檔 1 已知紅 ✅）＋需重生 tmp-check.gradle（B3） |
| A8 | ❌ | 「拆 D2 ⇒ coverage 必跌」係自指涉：coverage 由答案文字 regex 量度，拆 D2 只證明「儀器量到 D2」，唔證明 D2 有載荷（R1 原話，v2 未解決） |

**仍有一條 A 項唔可證偽（A4）＋一條自指涉負控（A8）** ⇒ R1 嘅核心指控**未過**。

---

## §D Flip conditions 總表（每個 blocker 一條，收窄後即可翻）

1. **B1**：示出 `graphFacts` 非空嘅 trace；或 D0.2/D5.1 改為「經現有 tool 交付」＋D0.1 加 `AskEngine.java:823-830` 打點＋A0 增 capable 模式負控。
2. **B2**：`avail` 只掃 tool.result 且要求 pack-資料形狀觸發；重出 baseline 並展示 A4 現時紅；A4 類別集合與 EXCLUDED 一致。
3. **B3**：白名單加 `logic/PurposeLookupAskTool.java`、tag 資料源檔、`client/patchouli/PatchouliGuideLookup.java`（改啱路徑）、`logic/ReplyLang.java`、`research/gen_tmp_check.py`、`logic/LootForwardIndex.java`（或刪對應 deliverable）。
4. **B4**：舉出白名單內現成 API + 3 個真 item tag 輸出例。
5. **B5**：補齊 L prefix → kind 映射（含 default）＋遊戲內人化字串樣本。
6. **B6**：公開 93／224／6842 嘅產生腳本與快照；或撤回該兩條「已核」。
7. **B7**：加「卡 + 補充段」fixture 並斷言落位。
8. **B8**：貼出沙盒 worldgen index 實測 entry 數，並講清 D5 走 tool 定 facts。
9. **B9**：改寫 §8 描述；§9 註腳寫死數字。

---

## §E 評分與建議

- **正方 3 : 反方 7 → go = false**（相對 R1 嘅 2:8 有進步：儀器真修好、D1 收窄到 table-id 級、sampler/acquire/caps 數字糾正、還原方案可還原）。
- 卡死嘅**載重決定**：D0／D5 嘅注入通道（facts）在實測配置（native tools on，76/76 呼叫）下係死路；而 A0 嘅 headless 閘測唔到呢件事。
- 最貴嘅未知（要拎咩數據才解得開）：① 一次 capable 模式下 `facts` 有料嘅 trace（證明管道通）；② 沙盒 worldgen index 實際 entry 數；③ 白名單內可取 item tag 嘅 API。
- 建議（唔係開新一輪）：把 plan **拆兩件**——(a)「D0 管道修復（含 capable 模式）+ 儀器/指標重定義」先做，因為佢係 P0 事實（19 條 trace JAR 零輸出）且範圍細；(b)「D1 route 清單／D2 必答清單／D3 tags／D5 worldgen」等功能留返拆細再評。按 repo 規則（`AGENTS.md`「複雜交付物拆開評」＋ plan review 上限 3–4 輪），v2 已用第 2 輪，若 (a) 單獨出 v3 並補齊 §D 1/2/3 三條 flip，值得再評一次；否則應停手交 SK 定。
