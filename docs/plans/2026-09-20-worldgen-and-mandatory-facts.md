# Plan v3：世界生成三類（a）＋ 必答清單（b）— 2026-09-20

- 作者：Hermes／日期：2026-09-20／狀態：**v3（吸收 R1 3:7 → R2 7:3 剩兩條），待 R3 有界反方 review**，未改任何 code
- 範圍授權：**SK 2026-09-20 06:5x 明示「a+b」**（a＝維度／生態域／礦物分佈；b＝每件物品必答清單）
- 語境：MC **1.19.2** ＋ Forge 43.4.5；packai `mod_version=0.2.3`；實測 pack＝**FTB Skies Expert**（`packai_sandbox_ftb`，**359** jars）。其他 pack／版本結論唔准套用。
- 上游 corpus：`docs/plans/2026-09-19-item-info-completeness-plan.md`（v5）＋ R5 報告。**沿用**其 §0 基線、§V5.1／§V5.4／§V5.8 形狀；**明文取代** D0（v6 `344e805` 已完成）／D1／D3／coverage 儀器／deny-list／worldgen disk cache。
- v1→v2→v3 因由：R1 反方 **正方 3 : 反方 7**（`reviews/2026-09-20_worldgen-and-mandatory-facts-R1-opposing.md`）＋ code-anchor 審查 **5 條 WRONG**（`reviews/2026-09-20_worldgen-and-mandatory-facts-anchor-audit.md`）。；R2 有界輪 7:3（餘 2 條）→ v3 補完（見 §8）。**每版只改被點名嘅項**。

## §0 本輪親跑事實（真 code／真 pack；行號係今日實測，改動後要重讀）

| # | 事實 | 證據 |
|---|---|---|
| F1 | `WorldgenIndex` 只由問題觸發（`AskEngine` `WorldgenFacts.looksLikeQuery`），**冇** per-item API、**冇** disk cache、`MAX_JARS=400` | `grep -n 'lookup\|No disk cache\|MAX_JARS' logic/WorldgenIndex.java` |
| F2 | 現行 raw 行格式（**唔改**）：`[WORLDGEN] placed_feature <pf> configured=<c> count=<n> height_range=<a>..<b>`／`[WORLDGEN] configured_feature <c> type=<t> size=<n>`／`[WORLDGEN] placed_feature <pf> in biome <b>` | `sed -n '389,445p' logic/WorldgenFacts.java` |
| F3 | ⚠️ **`height_range` 帶錨前綴**：`verticalAnchor` 回 `"absolute " + n` ⇒ 真值係 **`absolute -80..absolute 80`**（唔係 `-80..80`） | `logic/WorldgenFacts.java` `verticalAnchor()`（`absolute`／`above_bottom`／`below_top` 三種前綴）；repo 自己 `tests/check_worldgen_lookup.py` 用同一格式 |
| F4 | ⚠️ **`kindFromPath` 唔認 `dimension/`** ⇒ `scanJarFile`／`walkTree` 用 `isWorldgenPath` 過濾 ⇒ **維度檔今日全部被 skip** | `logic/WorldgenFacts.java` `kindFromPath()` 7 個 branch 全無 `dimension/`；`logic/WorldgenIndex.java` `walkTree`／`scanJarFile` 都先過 `isWorldgenPath` |
| F5 | 維度資料真存在：**14 檔／4 jars**；`ad_astra` 佔 **11** 檔；**3 種形狀**：① `biome_source.type=minecraft:fixed` ＋ `biome`（單一字串）② `biomes[]` 陣列 ③ 其他（唔認） | 本輪 python `zipfile` 掃 `mods/*.jar` |
| F6 | biome→dim 真值：認①②⇒ **11 個 biome 有映射**，namespace＝`ad_astra`／`createteleporters`；`ad_astra:lunar_wastelands → ad_astra:moon` ✅；**歧義 1 個**：`ad_astra:orbit → {earth_orbit,glacio_orbit,mars_orbit,mercury_orbit,moon_orbit,venus_orbit}` ⇒ 依規則**唔填** | 本輪 script 輸出（只認①⇒1 個 biome；①②⇒11） |
| F7 | 真值 fixture：`data/ad_astra/worldgen/placed_feature/moon_desh_ore.json` → `feature=ad_astra:moon_desh_ore`／`count=9`／height trapezoid `absolute -80..absolute 80`；`configured_feature/moon_desh_ore.json` → `type=minecraft:ore`／`size=9`；biome `ad_astra:lunar_wastelands` features 含 `ad_astra:moon_desh_ore` | 本輪 python 逐檔讀 |
| F8 | `acquire` 生產側人化**已有前例**：`AcquireAskTool.humanJarRoute(lang, code)`（`L\|` → `ReplyLang.lootTableObtain`；`R\|`／`U\|` → `JarLightIndex.formatFact`）；`AskReplyScrub` **冇** token 人化（佢只 strip chrome／tag） | `logic/AcquireAskTool.java` `humanJarRoute`；`grep -n 'WORLDGEN' logic/ReplyLang.java` → 0 hits |
| F9 | **閘**：`AcquireAskTool.mergeJarRoutes` 內 `if (PackAiConfig.scanModJars())` 包住 jar routes；兩個 instance（真 instance `AI_test_NFWC_DIM` 同 FTB 沙盒）`config/packai-client.toml:140` 都係 **`scanModJars = true`**；`PackAiConfig` 預設 false | `sed -n '65,90p' logic/AcquireAskTool.java`；`grep -n scanModJars` 兩個 instance config |
| F10 | `acquire` 預算**單一來源**＝`AskToolContext.clipAcquireLines(lines, question)`（craft／acquire 問句 12、其餘 3） | `logic/AskToolContext.java` `acquireLineBudget()` |
| F11 | `AskEngine` 答案定稿次序（真行）：`ensureHowToGetBody`（intent≠PURPOSE）→ `stripUnspecifiedMiss` → **`ReplySources.ensure`（加 footer）** → `AskMarkerRepair.repair` → `if (frameKind == STANDARD …) {…}` → `if (override) return AskResult.text(body)…` → 之後先入 quest 分支；**另有早退** `AskResult.text(ReplySources.ensure(body, List.of(), replyLang))` | `sed -n '940,1030p' logic/AskEngine.java` |
| F12 | `AskService`（**client/service/AskService.java**，唔喺 `logic/`）之後仲會 `stripDuplicateSectionHeaders`（`:349`）→ `AskCardFallback.ensureCards`（`:411`）→ `display.body.final` | `sed -n '344,356p;405,418p' client/service/AskService.java` |
| F13 | `logic/InfoCompleteness.java` **今日唔存在**；`AskToolEnv` 型別 harness 要 MC bootstrap ⇒ **一律用唔需要 env 嘅純函數 seam** | `ls logic/InfoCompleteness.java`；R5 B3 |
| F14 | 白名單檔 sha 基線（改動前）：`WorldgenFacts.java 6863b7b9…`／`WorldgenIndex.java fd706fa7…`／`AcquireAskTool.java e9c3a6f6…`／`ReplyLang.java 335218bb…`／`AskEngine.java da4fa7b6…`／lang 三檔 `6a9e6fac…`／`d33f978f…`／`39f05c30…`；`git status --porcelain`＝**127** 項；python 閘＝123 檔（122 綠＋`check_ask_display_leak` 1 紅）；已註冊 Java harness＝**51** | `%TEMP%\ab_baseline_20260920_0738.txt`／`ab_sha_20260920_0738.txt`／`ab_gate_baseline_20260920.txt` |

## §1 (a) 世界生成三類

### a-1 維度映射（宿主：`logic/WorldgenFacts.java` ＋ `logic/WorldgenIndex.java`）
- **必改第一步（R1 死因）**：`WorldgenFacts.kindFromPath()` 加新 branch `dimension/<name>.json` ⇒ **新 `Kind.DIMENSION`**；同步要處理嘅**全部** `Kind` 使用點（今日實測只有 4 處，逐處寫死）：
  1. `kindFromPath()` 新 branch
  2. `ingest()`/`addFromJson` 分派（現有 `if (kind == Kind.TAG)`／`else if (kind == Kind.MODIFIER)` 之後）⇒ 加 DIMENSION 分支
  3. ⚠️ **（R2 捉到 v2 前提錯，我睇漏）** `idFromPath()` 內部有 `switch (kind)`（`case BIOME/STRUCTURE/…; default -> "";`），DIMENSION 落 `default -> ""` ⇒ `ingest()` 因 `id.isEmpty()` **靜默丟棄全部維度檔**（即使 `kindFromPath` 認得）⇒ **必須加 `case DIMENSION -> "dimension/";`**（一行），id 才會係 `<ns>:<檔名>`（同 A-a1c 斷言一致）。**唔准**再寫「無需改」。
  4. `WorldgenFacts.Store` 新增 `Map<String,String> biomeToDim` ＋ `String dimensionOf(String biomeId)`
  - `WorldgenIndex` **兩個掃描入口都靠 `isWorldgenPath`**（`walkTree`／`scanJarFile`）⇒ 加咗 branch 就自動覆蓋；**唔准**改掃描上限語意（`MAX_JARS=400`／`MAX_FILES_PER_JAR=250`／`MAX_LOOSE_FILES=4000`）。
- 形狀規則（逐字寫死）：`generator.biome_source.type == "minecraft:fixed"` ⇒ 讀 `biome`（單一字串）；`biome_source.biomes[]` 存在 ⇒ 逐個 element 讀 `biome`；**其他形狀 ⇒ 唔記錄**。
- 歧義規則：同一 biome 映射到 >1 dim ⇒ **該 biome 整個唔用**（唔准揀一個／唔准「其中一個」）；無映射 ⇒ `dimensionOf` 回 `null`、**唔出 miss line**。
- 上限：`MAX_DIM_FILES = 200`（新增常數）；超出要記 author-only counter（唔准當已覆蓋）。

### a-2 per-item worldgen routes（宿主：`logic/WorldgenIndex.java`）
- 新增 `public static List<String> routesForItem(String itemId, Path gameDir)`：
  - `doEnsure(gameDir)`（沿用 idempotent 語意：同一 gameDir 只掃一次）→ `store.formatMatches(itemId, MAX_ROUTES_PER_ITEM = 8)`；
  - **過濾 rules（寫死）**：丟 `WorldgenFacts.missLine(...)`；只保留 `placed_feature`／`configured_feature`（`type=minecraft:ore`）／`in biome` 三種行；其他 kind（structure／structure_set／biome／modifier／tag）**唔出**（本輪 scope 只要礦物分佈＋生態域）；
  - 回 `List.of()` 表「無料」，**永遠唔回 miss line**（R1 LD2 存活，保留）。
- `lookup()`（問題觸發路徑）**語意零改動**；raw 格式唔變（F2）。

### a-3 併入 acquire（宿主：`logic/AcquireAskTool.java`）
- **閘**：維持現狀 `if (PackAiConfig.scanModJars())` 包住**兩條 jar/worldgen 通道**（F9；兩個 instance 都 `true`，所以行為唔變）；**唔加 config key**。
- **純函數 seam（harness 用，唔讀 config／唔要 env）**：
  `static List<String> mergeRoutes(String itemId, String lang, List<String> loose, List<String> jarRoutes, List<String> worldgenRoutes)`
  - 次序**寫死**：① jarRoutes（已人化）→ ② worldgenRoutes（已人化）→ ③ loose；`LinkedHashSet` 穩定去重（沿用現有 `addLine`／`seen` 手法）。
  - 呼叫方（`run()`）維持 `clipAcquireLines(..., args.question)`＝**唯一預算來源**（F10）；**唔准**再引入第二個 budget 常數。
  - `mergeJarRoutes(...)` 保留做薄 wrapper：讀閘 → 收集三組 → 叫 `mergeRoutes`（現有 call site 行為不變）。

### a-4 玩家可見人化（**生產側**，唔喺 `AskReplyScrub`）
- `logic/AcquireAskTool.java` 新增 `humanWorldgenRoute(lang, rawLine)`（同 `humanJarRoute` 同層、同手法）；`logic/ReplyLang.java` 新增 key（三語同步）：
  - `packai.reply.worldgen_ore` ＝`礦物分佈：%s｜生態域：%s｜高度：%s｜礦脈大小：%s｜每區數量：%s｜維度：%s`
  - 空值規則（機械）：數值／字串為 null 或空 ⇒ **連標籤整段刪**；`height_range` 顯示前**只**去掉字面 `"absolute "` 前綴（`above_bottom `／`below_top ` 保留）。
  - 三行 raw → 合併成**一行**人化輸出：`placed_feature` 提供 id／count／height；`configured_feature` 提供 size；`in biome` 提供 biome；`dimensionOf(biome)` 提供 dim（無 ⇒ 該欄消失）。
- 斷言：`acquire` 工具輸出（＝餵模型嘅文字）**零** `[WORLDGEN]`／`configured=`／`count=`／`height_range=` raw 欄位。

## §2 (b) 必答清單

### b-1 `logic/InfoCompleteness.java`（新檔；純函數、零 MC 型別）
```java
public static String append(String answer, List<Gap> gaps, ReplyLang lang)   // 可能需要 lang code 字串而非 ReplyLang 物件 ⇒ 實作者用 final String langCode（避免 bootstrap）
public record Gap(String line) {}   // line ＝ **已經人化**嘅 fact 行（由生產側提供，唔准即場重推）
```
- **Gap line 由生產側產生**（R1 LD5 死因：唔准喺判定側即場由 raw id 造人化字串）：
  - loot gap → `ReplyLang.lootTableObtain(lang, tableId)`（或該類現成 key）；
  - worldgen gap → `AcquireAskTool.humanWorldgenRoute(...)` 嘅同一行；
  - usages／craft gap → 該類現成 key；**冇現成 key 嘅類（tooltip）⇒ 唔准自己發明字串，直接唔計入 gaps**（寫入「已知限制」）。
- 判定「答案有冇覆蓋」＝ **token 命中**：該行內抽 1–3 個 salient token（table id／biome id／dim id／station id／配方產出 id）；`answer.toLowerCase(Locale.ROOT)` 含**任何一個** ⇒ 當已覆蓋。token 抽取規則寫死：以 `|`／`=`／空白切出嘅 `ns:path` 形態字串（regex 寫死 `[a-z0-9_.-]+:[a-z0-9_./-]+`），唔准用自然語言詞。
- 輸出：原答案 **零字改動** ＋ 一個 gap 區塊（新 lang key `packai.reply.info_gap_header` ＋各 gap 行），**插喺 footer 之前**（`ReplySources.HEADER` anchoring；無 footer ⇒ append）。**唔准**含 `[[recipe_card:`／`[card:`；gaps 為空 ⇒ **byte-identical 回傳**。

### b-2 單一 hook 點（宿主：`logic/AskEngine.java`）
- 落點**寫死**：主路徑 `if (frameKind == ModularFrameStandard.Kind.STANDARD && …) { … }` **之後**、`if (override) { return AskResult.text(body)… }` **之前**（一個呼叫，覆蓋：override 早退、quest 分支、以及之後 `AskService` 嘅 scrub／card 階段）。
- **覆蓋聲明（唔准 overclaim）——R2 要求逐條列全部出口**（今日實測 `AskEngine` 呢個方法嘅出口）：
  | 出口（真行） | 回嘅 body 來源 | gap 段適唔適用 |
  |---|---|---|
  | `:1012` `if (override) return AskResult.text(body)` | `body`（LLM 答案，已含 gap） | ✅ 覆蓋 |
  | `:1016` `if (!questHits.isEmpty()) return AskResult.of(body, …)` | 同上 | ✅ 覆蓋 |
  | `:1019` `return withSideQuests(body, …)`（→ 內部 `:1310` 早退都用同一 `body`） | 同上 | ✅ 覆蓋 |
  | `:1029` quest-guide-only 回答 | `QuestGuide.formatGuide(...)`（純任務內文） | ❌ **不適用**（唔係 LLM 答案、冇 facts 清單） |
  | `:1038` `plain != null` | `Plainify.plainify(...)`（冇 AI 嘅檢索原文） | ❌ **不適用**（offline 純檢索，本身已列原文） |
  | `:1066`／`:1075`／`:1083` | offline JEI／acquire dump／honest-miss | ❌ **不適用**（offline 路徑已**直接列出 facts 原文**；再補 gap 段＝噪音） |
  | `:1096`／`:1107` | offline 任務／friendly-offline 空 | ❌ **不適用**（明示查唔到） |
  ⇒ 一句總結：**gaps 只喺「有 LLM 答案」嘅三條出口生效**（`:1012`／`:1016`／`:1019`），其餘 6 條係 offline／純檢索路徑，明文列做**已知不適用**（唔准當「漏」）。
- **frame-kind 覆蓋嘅機械證明（R2 要嘅 harness）**：新閘 `tests/check_info_completeness_hook_order.py`（同 repo 前例 `check_settings_render_order.py` 同族）——解析 `logic/AskEngine.java` 源碼，斷言：① `InfoCompleteness.append(` **恰好出現一次**；② 佢嘅位置**後於** `ModularFrameStandard.Kind.STANDARD` 區塊、**先於** `if (override) {`；③ 位置喺 `AskResult.text(body)` 之前。**負控**：把呼叫移入 STANDARD 區塊內（或用 `sed` 暫時改成兩次呼叫）⇒ 閘**必紅**；還原後 rc=0（三語 lang key 唔關事）。
- gaps 由 facts 組裝期同一段（`AskEngine` 內 jar／worldgen／JEI／quest 各 block 已存在嘅地方）收集：**只計「該類有料而答案冇提及」**；冇料 ⇒ 永遠唔入 gaps。

## §3 白名單（只准改以下；新檔要入）
1. `logic/WorldgenFacts.java`（a-1 Kind.DIMENSION＋store map）
2. `logic/WorldgenIndex.java`（a-1 掃描上限／a-2 `routesForItem`）
3. `logic/AcquireAskTool.java`（a-3 seam／a-4 人化）
4. `logic/ReplyLang.java`（a-4／b-1 新 key ×3 語言）
5. `logic/AskEngine.java`（**只准**：1 個 hook 呼叫 ＋ gaps 收集；**唔准**動 `AskEngine` 內 capable facts 清空語意）
6. **新** `logic/InfoCompleteness.java`
7. `assets/packai/lang/en_us.json`／`zh_cn.json`／`zh_tw.json`
8. **新** harness：`src/test/java/com/skps9/packai/logic/WorldgenRoutesCheck.java`、`InfoCompletenessCheck.java`
9. `research/gen_tmp_check.py`（**repo 根 `research/`**，唔係 `forge/1.19.2/research/`——v1 寫錯）
10. **新** `tests/check_info_completeness_hook_order.py`（b-2 源碼順序閘；見 §2 b-2）
11. `code_change_log.md`
- **明文唔准郁**：`logic/RecipeEmbed.java`／`logic/RecipeCard.java`／`logic/AskReplyScrub.java`／`logic/JarLightIndex.java`／`logic/WorldgenLookupAskTool.java`／`config/PackAiConfig.java`／`neoforge/` 樹／`client/service/AskService.java`。
  - **為何 `JarLightIndex.java`／`WorldgenLookupAskTool.java` 唔入白名單**（R1 LD8 指漏）：本設計嘅 raw worldgen 行由 `WorldgenIndex` 出、人化喺 `AcquireAskTool`（同 `humanJarRoute` 同層），**唔經** jar-cache shard、**唔改**工具 schema ⇒ 兩個檔今日真係無需改（若實作時發現要改，**即停手報告**，唔准自行擴大範圍）。
- 實作者：**唔准** commit／deploy／開遊戲；**唔准**加 CJK 字串 literal 落 Java（新字串一律 lang key ×3）。

## §4 驗收表（pre-registered；逐字；唔准事後改）
| # | 斷言對象 | fixture | expected（逐字） | 負控 |
|---|---|---|---|---|
| A-a1a | `WorldgenIndex.routesForItem`（純函數、無 env） | 真 FTB index（gameDir＝沙盒）＋`ad_astra:moon_desh_ore` | 回 ≥3 行且**逐字含**：`[WORLDGEN] placed_feature ad_astra:moon_desh_ore configured=ad_astra:moon_desh_ore count=9 height_range=absolute -80..absolute 80`、`[WORLDGEN] configured_feature ad_astra:moon_desh_ore type=minecraft:ore size=9`、`[WORLDGEN] placed_feature ad_astra:moon_desh_ore in biome ad_astra:lunar_wastelands`（斷言逐行 `contains`，唔用 full-equality；行序唔准斷言） | 無關 id `minecraft:bedrock` ⇒ **回空 list**（唔准 miss line） |
| A-a1b | `Store.dimensionOf` | 真 index | `ad_astra:lunar_wastelands → ad_astra:moon`；`ad_astra:orbit → null` | 拆 map ⇒ null；只認形狀①⇒ 映射數由 11 跌 → 斷言「**形狀①②都要認**」（用兩個真 biome：一個 fixed、一個 `biomes[]`） |
| A-a1c | `kindFromPath` | 真檔路徑 `data/ad_astra/dimension/moon.json` | 回 `Kind.DIMENSION`；`idFromPath` 回 `ad_astra:moon` | 未加 branch ⇒ **今日回 null**（實作前跑一次記紅→綠） |
| A-a2 | `AcquireAskTool.mergeRoutes`（純函數） | 手造三組輸入（jar 2 行／worldgen 2 行／loose 2 行） | 次序＝jar→worldgen→loose；重複行只出一次 | **真負控**：把 loose 排前 ⇒ 次序斷言**紅**；budget＝3 ⇒ 只出頭 3 行（同一 fixture 再跑一次） |
| A-a3 | 人化輸出 | F7 raw 三行 | zh_tw ＝`礦物分佈：ad_astra:moon_desh_ore｜生態域：ad_astra:lunar_wastelands｜高度：-80..80｜礦脈大小：9｜每區數量：9｜維度：ad_astra:moon`；en_us／zh_cn key 非空；**冇** `absolute`／`configured=`／`count=`／`[WORLDGEN]` | 用**無 dim 映射**嘅 fixture（`ad_astra:orbit`）⇒ 輸出**冇** `維度：` 字樣 |
| A-b1 | `InfoCompleteness.append`（純函數） | answer（只講合成）＋gaps＝3 行（逐字提供） | 輸出＝原 answer ＋ header ＋ 3 行；三語各跑一次；header 用 `packai.reply.info_gap_header` | gaps＝空 ⇒ **byte-identical** |
| A-b2 | 同上 | answer **已含** loot table id | 輸出**冇** loot gap 行（token 命中） | 把 token 改成唔存在嘅 id ⇒ 該行**返嚟**（證明判定係真做嘢） |
| A-b3 | 落位存活 | fixture answer ＋ gap 區塊 | 過 `AskReplyScrub` 現實 pipeline（`stripDuplicateSectionHeaders`／`stripFactChrome`／`scrubInternalFieldEcho`）之後 gap 區塊**仍在**、卡片 marker 數目不變 | 拆走 gap ⇒ 對照答案 |
| A-b4 | 卡落位回歸 | 現有 fixture | `tests/check_ask_card_fallback.py`／`check_ask_marker_integrity.py` 綠；`RecipeEmbed.java`／`RecipeCard.java` sha256 不變 | — |
| A-b5 | hook 位置（靜態） | `logic/AskEngine.java` 源碼 | `tests/check_info_completeness_hook_order.py` 綠：`InfoCompleteness.append(` 恰好 1 次、位置後於 STANDARD 區塊、先於 `if (override) {` | 移入 STANDARD 區塊內 或 改成 2 次呼叫 ⇒ **必紅**，還原後綠 |
| A7 | 全回歸 | — | 已註冊 Java harness **逐個任務名**全跑（目標＝51＋2 新＝53，0 FAILED）；python 閘＝baseline（123 檔／1 已知紅，**冇新增紅**） | — |
| A8 | 真機 FTB 沙盒 | **每次新 seed 隨機抽 5 件 mod 礦物**（記 seed） | 答案提生態域／高度／礦脈大小；有映射者提維度；**零** raw `configured=`／`count=`／`[WORLDGEN]`；`focus_stolen=False` | — |
| A9 | 真機對照 pack（主包） | 3 件（新 seed） | 唔准爆；無料類**唔准**出 gap header | — |
- **A8／A9 執行人寫死**：`%TEMP%\packai_autotest_run.py`（harness，SK 授權路線）＋`PACKAI_SANDBOX=<instance>`／`PACKAI_WORLD`／`PACKAI_CASES`（新 seed 嘅 cases 檔）＋`--force`；驗收讀 trace `display.body.final` 同 `tool.result`，唔靠肉眼。

## §5 還原方案（已做）
- baseline／sha／逐檔備份已存 `%TEMP%\ab_baseline_20260920_0738.txt`／`ab_sha_20260920_0738.txt`／`ab_backup_20260920_0738\`（F14）。
- 還原＝逐檔 copy 返（tracked 檔）＋`rm` 新檔（`InfoCompleteness.java`／兩個 Check）＋`python research/gen_tmp_check.py` 重生 `tmp-check.gradle`；驗證＝`git status --porcelain` 回到 **127** 項 ＋ 白名單 sha 逐個相同。**嚴禁裸 `git checkout -- .`**。

## §6 成本／風險
- 成本：cursor 實作 ＋ 本地全驗（gradle 全跑 ~45s ×N）＋真機 2 輪（~15 分鐘遊戲）。**DS 空閒時段**（今日週日＝半價）。
- 風險：① `WorldgenIndex.ensure` 首次係**呼叫 thread 同步掃描**（359 jars）⇒ 首次 `acquire` 可能慢；**本 plan 唔加 cache**（R5 B7 已否證「per-call 掃」前提）→ A8 記 `elapsedMs` 入 HANDOFF 做已知成本。② 人化漏欄位 ⇒ A-a3。③ gap 區塊被 scrub／卡階段食走 ⇒ A-b3。④ `Kind.DIMENSION` 影響面：`data/*/dimension/*.json` 亦可能被其他 consumer 見到（今日 `isWorldgenPath` 只服務 worldgen index）⇒ 實作後要 `grep -n 'isWorldgenPath\|kindFromPath'` 全 repo 核 consumer 數目（今日 2 個檔案）。

## §7 明文唔做（剔走，唔係漏）
`tags_lookup`／tags 資料源（缺）；`guide_fetch` 檢索修；`blocks/*` 寫入側與 deny-list（屬路徑側，v6 已交付）；coverage 儀器；worldgen disk cache；任何 prompt 文案改動；structure／structure_set／biome／modifier 入 acquire。

## §8 Review record
| 輪 | 比分（正方:反方） | 卡死／餘項 | 該輪改咗咩 |
|---|---|---|---|
| R1 | **3 : 7**（go=false） | LD1 維度掃描落點唔成立／LD3 閘同 budget 未寫死／LD4 人化擺錯層／LD5 gap 行來源／LD6 hook 唔喺主路徑／LD7 驗收不可證偽／LD8 白名單 | v1 初稿 |
| anchor | — | **5 WRONG**：`height_range` 值、`dimension/` 未被 `kindFromPath` 認、`357`→359、`ad_astra ×5`→11、hook 唔係單一 | — |
| **v2 改動（對應上面）** | — | ① 所有 `height_range` 期望值改 `absolute -80..absolute 80` ＋ 顯示剝前綴規則；② a-1 加 `Kind.DIMENSION` ＋ 4 個使用點寫死；③ 閘保留 `scanModJars()`、刪第二 budget；④ 人化搬去 `AcquireAskTool`（生產側）＋新 key ×3；⑤ gap 行由生產側提供（唔准即場造）；⑥ hook 搬主路徑（STANDARD 後／override 前）＋明示未覆蓋路徑；⑦ 驗收全改逐字＋真負控＋寫死真機執行人；⑧ 白名單修正（加／撤檔位理由寫明）、jar 數更正 359 | — |
| R2 | **7 : 3**（go=false；8 條之中 7 條 RESOLVED） | ① `idFromPath` 嘅 `default -> ""` 令維度檔被靜默丟棄（v2 寫「無需改」＝前提錯，同自家 A-a1c 自相矛盾）② b-2 未覆蓋清單只列 1 條，實測 ≥5 條出口繞過；缺 frame-kind harness | — |
| **v3 改動** | — | ① a-1 加 `idFromPath` 嘅 `case DIMENSION -> "dimension/";`（一行）＋明文撤回「無需改」；② b-2 補**全部 9 條出口**嘅覆蓋表（3 條覆蓋／6 條不適用附理由）＋新閘 `check_info_completeness_hook_order.py`（含真負控）＋白名單第 10 項指名 | — |
| R3 | 待跑（有界：只核上面 2 條） | — | — |
