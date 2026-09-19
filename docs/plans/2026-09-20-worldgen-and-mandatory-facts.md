# Plan v1：世界生成三類（a）＋ 必答清單（b）— 2026-09-20

- 作者：Hermes／日期：2026-09-20／狀態：**v1，待 R1 反方 review**，未改任何 code
- 範圍授權：**SK 2026-09-20 06:5x 明示「a+b」**（a＝維度／生態域／礦物分佈；b＝每件物品必答清單）
- 版本語境：MC **1.19.2** ＋ Forge 43.4.5；packai `mod_version=0.2.3`；實測 pack＝**FTB Skies Expert**（`packai_sandbox_ftb`，357 jars）。其他 pack／版本結論唔准套用。
- 上游 corpus：`docs/plans/2026-09-19-item-info-completeness-plan.md`（v5；5 輪 review 停在 **正方 4 : 反方 6**，R5 報告 `docs/plans/reviews/2026-09-19_item-info-completeness-plan-R5-opposing.md`）。**本 plan 沿用**佢 §0 基線、§V5.1（dim 資料源）、§V5.3（harness 前提）、§V5.4（預算）、§V5.8（驗收形狀）；**明文取代**佢嘅 D0（已由 v6 `344e805` 完成）、D1、D3、coverage 儀器、deny-list、disk cache。

## §0 本輪親跑事實（每條附命令；唔准靠上一輪 carry）

| # | 事實 | 證據（本輪實跑） |
|---|---|---|
| F1 | `WorldgenIndex` **只**由問題觸發（`AskEngine:695` `looksLikeQuery`），**冇** per-item 查詢 API、**冇** disk cache | `grep -n 'lookup\|cache' logic/WorldgenIndex.java`→`:55 lookup(query,gameDir,lang)`、`:18 註釋 No disk cache`、`MAX_JARS=400:28` |
| F2 | 現行輸出係 raw token 行：`[WORLDGEN] placed_feature <pf> configured=<c> count=<n> height_range=<a>..<b>`／`[WORLDGEN] configured_feature <c> type=<t> size=<n>`／`[WORLDGEN] placed_feature <pf> in biome <b>` | `sed -n '389,445p' logic/WorldgenFacts.java` |
| F3 | **`ReplyLang` 完全冇 `[WORLDGEN]` 人化**（grep 0 hits）⇒ 若把 raw 行塞入答案，玩家會見到 `configured=`／`count=` 內部欄位 | `grep -n 'WORLDGEN' logic/ReplyLang.java` → 0；`AskReplyScrub.java:36` 只係 strip 標籤清單 |
| F4 | 真值 fixture（FTB 沙盒，`ad_astra-forge-1.19.2-1.12.7.jar`）：`data/ad_astra/worldgen/placed_feature/moon_desh_ore.json` → `feature=ad_astra:moon_desh_ore`、`count=9`、`height_range` trapezoid `-80..80`；`configured_feature/moon_desh_ore.json` → `type=minecraft:ore`、`size=9`；biome `ad_astra:lunar_wastelands` 嘅 features 含 `ad_astra:moon_desh_ore` | 本輪 python zipfile 逐檔讀（輸出見 review dispatch 附錄） |
| F5 | **`data/*/dimension/*.json` 真存在**：14 檔／4 jars（`ad_astra` × 5 睇到、`createteleporters` 亦有）；兩種形狀：`generator.biome_source.type=minecraft:fixed` ＋ `biome`（單一）／`biomes[].biome`（陣列） | 本輪 python 掃 `mods/*.jar` |
| F6 | biome→維度映射真值：**11 個 biome 有映射**（namespace `ad_astra`／`createteleporters`）；`ad_astra:lunar_wastelands → ad_astra:moon` ✅；**1 個歧義**：`ad_astra:orbit → {earth_orbit,glacio_orbit,mars_orbit,mercury_orbit,moon_orbit,venus_orbit}` | 同上（同一 script 輸出 11／ambiguous=1） |
| F7 | `acquire` 已有 v6 靜態 seam：`AcquireAskTool.mergeJarRoutes(itemId, lang, loose)`（`static`，唔需要 `AskToolEnv`）；預算：`clipAcquireLines` → craft／acquire 問句 **12** 行、其餘 **3** 行 | `grep -n 'mergeJarRoutes\|clipAcquireLines' logic/AcquireAskTool.java`；`logic/AskToolContext.java:115-143` |
| F8 | 答案最終處理次序（單一 hook 點）：`:963 body = ReplySources.ensure(body, replySources, lang)`（加 footer）→ `:980 body = AskReplyScrub.replaceHowToGetBody(body, line)` | `grep -n 'ReplySources.ensure\|replaceHowToGetBody' logic/AskEngine.java` |
| F9 | footer 定位 seam ＝ `ReplySources.HEADER = (?m)(【來源】\|【来源】\|\[Sources\])`；已有前例 `AskJeiHints.ensureCanonicalQuestLine`（`:262-273`）用同一 anchoring 插喺 footer 前 | `logic/ReplySources.java:11`、`logic/AskJeiHints.java:262-273` |
| F10 | `logic/InfoCompleteness.java` **今日唔存在**；`AskToolEnv` 型別 harness 需要 MC bootstrap（R5 B3 已證）⇒ **本 plan 一律用「唔需要 env 嘅純函數 seam」路線**（R5 B3 flip ②） | `ls logic/InfoCompleteness.java` → No such file |
| F11 | 現有可重用 lang key：`packai.reply.loot_table_obtain`（掉落表：%s）／`packai.reply.jar_loot`（掉落：%s）／`packai.reply.structure_chest_obtain`／`packai.label.src.worldgen` | `python json.load(zh_tw.json)` 逐 key 印 |

## §1 (a) 世界生成三類：交付物

### a-1 維度映射（宿主檔：`logic/WorldgenFacts.java` ＋ `logic/WorldgenIndex.java`）
- `WorldgenFacts.Store` 新增 `Map<String,String> biomeToDim`（biomeId → dimId）＋ `String dimensionOf(String biomeId)`。
- 掃描：`WorldgenIndex.scanJarFile`／`walkTree` 內，凡 entry 路徑匹配 `data/<ns>/dimension/<name>.json` ⇒ parse `generator.biome_source`：
  - `type == minecraft:fixed` → 讀 `biome`（單一字串）；
  - 有 `biomes[]` 陣列 → 逐個 `biome` 欄位；
  - 其他形狀 ⇒ **唔記錄**。
  - dimId ＝ `<ns>:<file 名去掉 .json>`。
  - **歧義規則**：同一 biome 映射到 >1 個 dim ⇒ **整個映射唔用**（唔准揀一個）；無映射 ⇒ 唔填、**唔出 miss line**。
- 上限：`MAX_DIM_FILES=200`（同 `MAX_JARS` 同級，防爆）；超出唔准靜靜當已覆蓋 ⇒ 記 counter。

### a-2 per-item worldgen routes（宿主檔：`logic/WorldgenIndex.java`）
- 新增 `public static List<String> routesForItem(String itemId, Path gameDir)`：
  - `doEnsure(gameDir)`（**沿用現有 idempotent 語意**；同一 gameDir 只掃一次）→ `store.formatMatches(itemId, MAX_ROUTES_PER_ITEM=8)`；
  - **過濾 miss line**（唔准 `WorldgenFacts.missLine` 出現喺 routes）＋ 過濾 `type!=minecraft:ore` 嘅 configured_feature；
  - 回 `List.of()` 表「無料」，**唔准**回 miss line。
- 唔准改 `lookup()`（問題觸發路徑）語意；`[WORLDGEN]` raw 格式唔變（F2 保持）。

### a-3 併入 acquire ＋ 玩家可見人化
- `logic/AcquireAskTool.java`：`mergeJarRoutes` 擴充／新增同層靜態 `mergeRoutes(String itemId, String lang, List<String> loose, List<String> jarRoutes, List<String> worldgenRoutes, int budget)`——
  - 次序**寫死**：① jar loot refs（`L|`／`U|`／`R|`）→ ② worldgen routes → ③ loose；同類穩定去重；
  - 繼續經 `clipAcquireLines`（budget 12／3，F7）；
  - **唔准**改工具 schema、唔准新工具（`check_tool_schema_stable.py:108` 會紅）。
- 玩家可見文字：`logic/ReplyLang.java` 新增
  - `packai.reply.worldgen_route`（三語）＝`世界生成：%s｜生態域：%s｜高度：%s｜礦脈大小：%s｜每區數量：%s｜維度：%s`
  - **空值規則（機械、可測）**：欄位值 null／空 ⇒ **連該標籤一齊整段刪**（同一 format 只保留有值欄位，唔准寫 `?`／`unknown`）。
  - 落點：`AskReplyScrub` 現行 token 人化路徑（同 `packai.reply.jar_loot` 同一手法），**唔准入 raw `configured=`／`count=` 落玩家文字**。

## §2 (b) 必答清單：交付物

### b-1 `logic/InfoCompleteness.java`（新檔；純函數，零 MC 型別）
```java
public static String append(String answer, List<Missing> missing, ReplyLang lang)
public record Missing(String categoryKey, List<String> tokens, String humanizedLine) {}
```
- 判定「答案有冇覆蓋某類」＝ **token 命中**：該類 fact 行內抽 1–3 個 salient token（loot→table id 或人化名；worldgen→biome id／dim id；usage→station id；craft→station 名；tooltip→?；quest→任務 id）；答案全文（`toLowerCase(Locale.ROOT)`）含**任何一個** token ⇒ 當已覆蓋。
- 缺失類 ⇒ 生成 **一行**（用人化行，原文不動），整批加一個 header（lang key），**插喺 footer 之前**（`ReplySources.HEADER` anchoring，F9）；冇 footer ⇒ 直接 append（fail-open，唔准靜默唔出）。
- **唔准**含 card marker（`[[recipe_card:`／`[card:`）；**唔准**改動答案原文任何字（只插入）。
- 新 lang keys（zh_tw／zh_cn／en_us 三語同步）：
  - `packai.reply.info_gap_header`
  - `packai.reply.info_gap_loot`／`_worldgen`／`_usages`／`_craft`／`_tooltip`／`_quest`

### b-2 單一 hook 點（宿主檔：`logic/AskEngine.java`）
- 落點＝`:980`（`replaceHowToGetBody`）**之後**、回傳前，呼叫一次 `InfoCompleteness.append(...)`。
- 輸入 `missing` 由 facts 組裝期（`:686-694` 同一段）收集：**只計「該類有料而答案冇提及」**；冇料嘅類**永遠唔准入 missing**（唔准對玩家喊「欠 X」）。
- 唔准動 `:826` capable 清空語意、唔准改 prompt 文案。

## §3 白名單（只准改以下檔案；新檔要入）
1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/WorldgenFacts.java`
2. `logic/WorldgenIndex.java`
3. `logic/AcquireAskTool.java`
4. `logic/ReplyLang.java`
5. `logic/AskReplyScrub.java`（只限 token 人化新增；唔准改既有 regex 行為）
6. `logic/AskEngine.java`（**只准**加 1 個 hook 呼叫 ＋ missing 收集；唔准動 `:826`）
7. **新** `logic/InfoCompleteness.java`
8. `assets/packai/lang/en_us.json`／`zh_cn.json`／`zh_tw.json`（三語同步）
9. **新** harness：`src/test/java/com/skps9/packai/logic/WorldgenRoutesCheck.java`、`InfoCompletenessCheck.java`
10. `research/gen_tmp_check.py`（重生 `tmp-check.gradle`）
11. `code_change_log.md`
- **唔准郁**：`logic/RecipeEmbed.java`／`logic/RecipeCard.java`（A3 要 sha256 零改動）／`neoforge/` 樹／`config/PackAiConfig.java`（唔加 config key）／`WorldgenLookupAskTool.java` schema。
- **唔准** commit／deploy／開遊戲（實作者）；**唔准**加 CJK 字串 literal 落 Java。

## §4 驗收表（pre-registered；逐字；唔准事後改）
| # | 斷言對象 | fixture | expected（逐字） | 負控 |
|---|---|---|---|---|
| A-a1 | `WorldgenIndex.routesForItem` | 真 FTB 沙盒 index（`gameDir` 指沙盒） | 對 `ad_astra:moon_desh_ore` 回 ≥1 行且含 `configured_feature ad_astra:moon_desh_ore`、`count=9`、`height_range=-80..80`、`in biome ad_astra:lunar_wastelands` | 傳無關 id（`minecraft:bedrock`）⇒ **回空 list**（唔准 miss line） |
| A-a2 | `WorldgenFacts.Store.dimensionOf` | 同上 | `ad_astra:lunar_wastelands → ad_astra:moon`；`ad_astra:orbit → null`（歧義） | 拆 map ⇒ null |
| A-a3 | `AcquireAskTool.mergeRoutes`（純函數） | 手造 loose ＋ jar ＋ worldgen 三組 | 次序 jar→worldgen→loose；budget 3 ⇒ 頭 3 行；budget 12 ⇒ 全出 | 把 loose 排前 ⇒ **次序斷言紅** |
| A-a4 | `ReplyLang` 人化 | fixture：F4 三行 raw | zh_tw ＝`世界生成：ad_astra:moon_desh_ore｜生態域：ad_astra:lunar_wastelands｜高度：-80..80｜礦脈大小：9｜每區數量：9｜維度：ad_astra:moon`；en_us／zh_cn 對應 key 唔可空；**空值欄位唔可出現**（用無 dim 嘅 fixture 斷言「冇 `維度：` 字樣」） | 傳 null biome ⇒ 該段消失 |
| A-b1 | `InfoCompleteness.append`（純函數） | answer（只講合成）＋ missing＝[loot, worldgen, usages] | 輸出＝原 answer ＋ header ＋ **3 行**（逐字，用 b-1 定義嘅 key 內容）；三語各跑一次 | 停用（missing=[]）⇒ 冇 header（A-b3） |
| A-b2 | 同一函數 | answer **已**含 loot table id | 輸出 **唔可以**再出 loot 行（token 命中判定） | — |
| A-b3 | 卡落位回歸 | 現有 fixture | `tests/check_ask_card_fallback.py`＋`check_ask_marker_integrity.py` 保持綠；`RecipeEmbed.java`／`RecipeCard.java` sha256 不變 | — |
| A-b4 | 無料類唔准喊 | missing 空 | 輸出 == 輸入（byte 相同） | — |
| A7 | 全回歸 | — | 已註冊 Java harness **逐個任務名**全跑（目標 = 基線 +2 個新 harness）、python 閘 = 基線（122 綠＋1 已知紅，`check_ask_display_leak`）| — |
| A8 | 真機（FTB 沙盒） | 隨機抽 5 件 mod 礦物（**每次新 seed**，記 seed） | 答案提到生態域＋高度＋礦脈大小；有 dim 映射者講維度；**唔准** raw `configured=`／`count=`；`focus_stolen=False` | — |
| A9 | 真機（對照 pack） | 主包 1 輪 3 件 | 唔准爆；無料類唔准出現 header | — |

## §5 還原方案（改動前做）
- baseline：`%TEMP%\ab_baseline_<ts>.txt`（`git status --porcelain` 項數，今日 **127**：71 M／54 ??／2 D）＋ `%TEMP%\ab_sha_<ts>.txt`（白名單 11 檔 sha256）。
- 逐檔備份 `%TEMP%\ab_backup_<ts>\`；**嚴禁裸 `git checkout -- .`**（127 項 dirty 唔係本 plan 造成）。
- 還原＝逐檔 `git checkout -- <file>`（**新檔** `InfoCompleteness.java`／兩個 harness ＝ `rm`＋由備份還原）＋ `python research/gen_tmp_check.py` 重生 `tmp-check.gradle`；驗證＝`git status` 回到 127 項 ＋ `RecipeEmbed.java` sha 不變。

## §6 成本／風險
- 成本：實作（cursor）＋本地全驗（~45 秒 gradle 全跑 ×N）＋真機 2 輪（FTB 5 件／主包 3 件，~15 分鐘遊戲、~50 萬 tokens）。**DS 空閒時段做**（今日週日＝半價）。
- 風險：① `WorldgenIndex.ensure` 首次係**同步掃描**（呼叫 thread；357 jars）→ 首次 acquire 可能慢；本 plan **唔加 cache**（R5 B7 已否證「per-call 掃」前提），改為喺 A8 記 `elapsedMs` 並寫入 HANDOFF 做已知成本。② `[WORLDGEN]` 人化漏欄位 ⇒ A-a4 負控。③ D2 補完行落喺「怎麼用」段內被 `replaceHowToGetBody` 食走 ⇒ hook 擺喺 `:980` 之後（F8）＋ A-b1／b3 覆蓋。

## §7 明文唔做（唔係漏，係剔）
- `tags_lookup`／tags 資料源（缺）；`guide_fetch` 檢索修（D3.2）；`blocks/*` 出唔出／deny-list（屬路徑側，已由 v6 交付；本輪唔改寫入側）；coverage 儀器升級；worldgen disk cache（R5 B7）；任何 prompt 文案改動。

## §8 Review record
| 輪 | 反方比分（正方:反方） | 卡死／餘項 | 該輪改咗咩 |
|---|---|---|---|
| R1 | 待跑 | — | v1 初稿 |
