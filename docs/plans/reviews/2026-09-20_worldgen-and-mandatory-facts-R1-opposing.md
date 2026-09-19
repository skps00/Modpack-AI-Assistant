# R1【最強反方】review：worldgen 三類（a）＋必答清單（b）— 2026-09-20

- 對象：`docs/plans/2026-09-20-worldgen-and-mandatory-facts.md`
- 版本核對：`md5sum` → `f529f50328d0dbe55e72c76ac9530693` ＝ dispatch 指定值 **一致**，未停手。
- 立場：**Stage-1 最強反方**（第一階段禁止支持）。以下全部預設立場＝plan 錯／行唔通／會退步；只有逐條擋得住攻擊嘅才判「存活」。
- 本輪 READ-ONLY：唯一寫入檔＝本報告。無改 repo、無 commit、無 build、無開遊戲。

---

## §0 本輪親跑證據（先驗事實，再判 plan）

| 代號 | 命令（實跑） | 輸出（原文摘要） |
|---|---|---|
| E1 | `md5sum docs/plans/2026-09-20-worldgen-and-mandatory-facts.md` | `f529f50328d0dbe55e72c76ac9530693` ✅ |
| E2 | `git status --porcelain \| wc -l` ＋ `awk '{print $1}' \| sort \| uniq -c` | `127`；`54 ??`／`2 D`／`71 M` → plan §5 寫 127（71M/54??/2D）**逐字準確** |
| E3 | `for f in tests/check_*.py; do python "$f" \|\| echo FAIL $f; done` | 檔案數 `123`；唯一 `FAIL tests/check_ask_display_leak.py` → 「122 綠＋1 已知紅」**準確** |
| E4 | python `zipfile` 掃 `mods/*.jar` 全部 `data/*/dimension/*.json` | `dimension files= 14`／4 jars；`mapped biomes= 11`；`ambiguous= 1`（`ad_astra:orbit` → 6 dims）→ F5／F6 **逐字準確** |
| E5 | 同上，逐檔印 `generator.biome_source.type` | `minecraft:multi_noise`：mars(3 biomes)／glacio(2)／mercury(1)／venus(2)／createteleporters:pd_biome(1)；`minecraft:fixed`：**6 個都係 orbit**；`type=None`：compactmachines／l2library |
| E6 | python 讀 `ad_astra-forge-1.19.2-1.12.7.jar` 內 3 個 fixture | `placed`：`count=9`＋trapezoid `-80..80` ✅；`configured`：`type=minecraft:ore`、`size=9` ✅；`lunar_wastelands` features 含 `ad_astra:moon_desh_ore` ✅；ad_astra worldgen-ish entries ＝ **140** |
| E7 | `cat -n logic/WorldgenIndex.java`；`grep -n 'enum Kind' WorldgenFacts.java` | `doEnsure` 冪等（`:70-71`）；`scanJarFile` 內 `isWorldgenPath` 關卡在 `:187`；`Kind` enum `:22-30` **七個值、無 DIMENSION** |
| E8 | `sed -n '380,470p' / '540,700p' logic/WorldgenFacts.java` | `formatPlaced` 出 `count=`／`height_range=`（`:427-441`）；`in biome <b>` 係**另一條** line（`:641`／`:646`） |
| E9 | `grep -n 'scanModJars' config/PackAiConfig.java` | `:447 .define("scanModJars", false)` → **預設 OFF**；`:919-921` getter |
| E10 | `read_file AskEngine.java 940-1023` | `:963` 主路徑 `ReplySources.ensure`；`:969` `if (frameKind==STANDARD && frameMatch.recipeIndex()!=null) {`；`:980` **喺呢個 if 內**；主路徑 return ＝ `:1012`／`:1016`／`:1019` |
| E11 | `grep -rn 'jarLoot\|lootTableObtain'` | 人化全部喺**生產側**：`JarLightIndex:293`、`AcquireAskTool:88`、`PackIndex:1318`、`Plainify:212`；定義 `ReplyLang:454`／`:786` |
| E12 | `grep -n -A25 'PLAYER_UNSAFE_MARKERS' AskReplyScrub.java` | `:148-166` **冇** `configured=`／`count=`／`placed_feature`／`[WORLDGEN]`；`[WORLDGEN]` 反而喺 `INTERNAL_SECTION_TOKENS:36`；`PROMPT_SECTION_TAG` `:56-58`；`replaceAll("")` 落 `:921`／`:1084` |
| E13 | `grep -rn 'WorldgenIndex.lookup'` ＋ `sed -n '46,60p' WorldgenFacts.java` | raw 注入 prompt＝`AskEngine:696`＋`WorldgenLookupAskTool:43`；`looksLikeQuery` 中 `ore`／`礦`／`矿` 都命中 |
| E14 | `find src/test -name '*Check.java' \| wc -l`；`grep -c "': 'com.skps9" tmp-check.gradle` | 51 個 harness、tmp-check.gradle 51 tasks（`research/gen_tmp_check.py` 自動註冊）→ A7「基線+2」機制**真存在** |
| E15 | `grep -oh 'ad_astra:[a-z_]*' packai/trace/ask-*.jsonl`；`grep -c 'configured='` | 20 個 trace；`configured=` **0 次**；出現嘅全部係**物品** id（`ad_astra:oxygen_tank`×11 等）——**冇任何 biome id、冇任何 worldgen 答案** |
| E16 | `grep -n 'clipAcquireLines' logic/AskToolContext.java`；`grep -n 'ensureCanonicalQuestLine' logic/AskJeiHints.java` | 真實位置 `AskToolContext:145`（plan 寫 `:115-143`）／`AskJeiHints:263`（plan 寫 `:262-273`）→ 兩處 **file:line 漂移** |

**反方先認一筆**：§0 十一條事實（F1–F11）我逐條親核，**除兩處 line drift（E16）外全部成立**，F4／F5／F6／§5 baseline／§4 A7 基線數字係**逐字準確**。呢個 credible 嘅事實底反而令下面嘅設計缺陷更致命——fact 真，但設計接唔上 fact。

---

## §1 逐條載重決定（LD）判定

### LD1 — a-1 維度映射：**死**

事實面（E4／E5）plan 講得對，**設計面接唔上真 code**：

1. **掃唔到**。`WorldgenFacts.kindFromPath`（`:97-127`）**冇 `dimension/` 分支**，`Kind` enum（`:22-30`）**冇 DIMENSION**；而 `isWorldgenPath`（`:129`）係 `scanJarFile` 嘅**前置關卡**：
   - `WorldgenIndex.java:187` `if (!WorldgenFacts.isWorldgenPath(path)) { continue; }`
   - `WorldgenIndex.java:131`（loose 側）同款。
   plan §1 a-1 只寫「掃描：`WorldgenIndex.scanJarFile`／`walkTree` 內，凡 entry 路徑匹配 `data/<ns>/dimension/<name>.json` ⇒ parse」——照字面做，dimension 檔喺 `:187` 已經 `continue` 走，永遠到唔到新 code；`ingest`（`:179-212`）亦只 `switch (kind)`，kind==null 直接 return。plan **冇一句**寫要改 `kindFromPath`／`Kind`／`ingest`。實作者照 plan 落手 ⇒ `biomeToDim` 恆空 ⇒ A-a2 紅。
2. **規則自相矛盾**。LD1 描述「只認 `minecraft:fixed` 嘅 biome 同 `biomes[]` 陣列」，a-1 條文係兩條 bullet 並列（`type==fixed → biome`；`有 biomes[] ⇒ 逐個 biome`）。E5 實測：11 個映射中有 **5 個嚟自 `minecraft:multi_noise`**（mars／venus／mercury／glacio／createteleporters）。兩個讀法結果差天共地：
   - 讀法 A（biomes[] 不限 type）⇒ 11 個 biome（我實測值）。
   - 讀法 B（兩個分支都要 fixed）⇒ 只剩 `ad_astra:lunar_wastelands`（因為 6 個 fixed dim 全部係 orbit，再被歧義規則整條棄用）。
   **plan 揀邊個冇講**，而 §4 驗收只抽 2 個點（`lunar_wastelands→moon`、`orbit→null`）——**兩個讀法都會過 A-a2**，即驗收設計**冇能力**分辨呢個歧義（見 LD7）。
3. 歧義規則本身方向正確（`ad_astra:orbit` 對 6 dim 確實冇單一答案），但同一規則令「fixed」路線淨收益≈0，plan 冇交代呢個 trade-off，亦冇 counter 斷言。

### LD2 — a-2 `WorldgenIndex.routesForItem`：**存活（弱）**

只有呢條擋得住。親核：
- 「in-memory、無 re-scan」＝ `doEnsure` 真係冪等（`WorldgenIndex.java:70-71` `if (ready && abs.equals(loadedDir)) return;`）✅
- 「回 `List.of()` 表無料」＝ 同 `doLookup` 嘅 miss 行為切割清楚（`:85`／`:93` 係 missLine **唯一**出處，`formatMatches` 從來唔出 miss）✅
- 「唔准改 `lookup()` 語意／raw 格式唔變」＝ 同 F2 一致 ✅
- `store.formatMatches(itemId, 8)` 簽名真存在（`WorldgenFacts:543`），`matches()`（`:333`）係 exact／stem／`endsWith` 語意 ⇒ A-a1 負控 `minecraft:bedrock` 回空**合理** ✅

兩處扣分（未至於死，但 plan 當佢係 sell point 有誤導）：
- **「過濾 miss line」係空要求**：`formatMatches` 永遠唔會回 miss line，呢條 filter 恆真、測唔到、係 padding。
- **`formatMatches` 係 free-text QUERY 匹配器**，唔係 per-item API：query 命中時會順帶拖 BIOME／STRUCTURE／STRUCTURE_SET／MODIFIER／TAG 各類 line，亦會為每個 hitPlaced 掃**全部** biome 出 `in biome`（`:606-668`）。plan 只 filter「`type!=minecraft:ore` 嘅 configured_feature」，**冇 filter 其餘四類噪音**，A-a1 亦冇任何「唔准出現 X」斷言 ⇒ 呢條 per-item API 嘅純度冇被驗收鎖住。

### LD3 — a-3 併入 acquire（`mergeRoutes` seam）：**死**

1. **會退步（最貴嗰條）**。現行 `mergeJarRoutes`（`AcquireAskTool.java:65`）內部有硬閘：
   `:68 if (PackAiConfig.scanModJars()) { ... JarLightIndex.INSTANCE.routeLinesForItem(itemId) ... }`
   而 `PackAiConfig.java:447` `.define("scanModJars", false)` ⇒ **v6 交付嘅 jar loot routes 預設係 OFF**。
   plan 要改成「`mergeRoutes(itemId, lang, loose, jarRoutes, worldgenRoutes, budget)`」——`jarRoutes` 變成**由 caller 傳入**。plan **冇一句**交代個閘去邊。閘一係留在 caller（plan 未寫）、一係漏（v6 行為突變：預設 OFF 用戶開始見到 jar routes）。同時 worldgen routes 由 `routesForItem` 直接產生，**根本冇對應 config**；而 §3 明文「**唔准郁** `config/PackAiConfig.java`（唔加 config key）」⇒ **worldgen routes 不可能被閘住**，即新行為**無條件 default-ON**，同 jar routes 嘅 default-OFF 平排存在。呢個係「新功能未落地就先改咗已交付路徑嘅默認行為」。
2. **兩個 budget 來源打交**。新 `mergeRoutes` 收 `int budget`；但生產路徑照舊 `AskToolContext.clipAcquireLines`（`:145`）→ `acquireLineBudget(question)` → 12／3（`:139`）。A-a3 只對新函數嘅 `budget` 參數落斷言，**測唔到**生產 wiring（誰傳乜、12／3 有無被繞過）。
3. **假負控**。A-a3「負控：把 loose 排前 ⇒ 次序斷言紅」＝為咗令斷言紅而改實作本身，唔係輸入驅動嘅負控（對照 `negative-control-verification`：要用壞輸入證明守門會 fail）。呢條唔算負控。
4. 輕微：`clipAcquireLines` 真實位置 `:145`，plan 寫 `:115-143`（E16）。

### LD4 — a-3 玩家可見人化：**死**

1. **落點唔存在**。plan 寫「落點：`AskReplyScrub` 現行 token 人化路徑（同 `packai.reply.jar_loot` 同一手法）」。親核（E11）：`jar_loot` 人化係**生產側**發生——`JarLightIndex.java:293 case 'L' -> ReplyLang.jarLoot(lang, rest)`、`AcquireAskTool.java:88 ReplyLang.lootTableObtain(...)`、`ReplyLang.java:454`／`:786` 定義。`AskReplyScrub` **冇任何 token→人化文字**機制；佢只有 source label 映射（`lookupLabel`，`:726-768`）同**fail-closed 過濾** `playerSafeFacts`（`:1540`）。plan 叫人去一個唔做呢件事嘅檔做呢件事。
2. **過濾器擋唔到**。`PLAYER_UNSAFE_MARKERS`（`:148-166`）逐個核：`render_recipe_cards`／`[RECIPE_CARDS]`／`【JEI`／`注意：JEI`／`已完整掃描`／`推荐…`／`role=`／`必须`／`禁止`／`不要用`／`请明说`／`DSML`／`<invoke`／`<tool_calls`／`[[tools]]{`——**冇** `configured=`、**冇** `count=`、**冇** `placed_feature`。即 raw worldgen body **會**穿過 `playerSafeFacts`。
3. **更毒：個 tag 喺 scrub 途中被自己拆走**。`[WORLDGEN]` 列入 `INTERNAL_SECTION_TOKENS`（`:27-36`，第 36 行），而 `PROMPT_SECTION_TAG` 只 match `\[\s*WORLDGEN\s*\]`（`:56-58`），套用方式係 `replaceAll("")`（`:921`／`:1084`）⇒ **只拆 tag，留返 ` placed_feature … configured=… count=… height_range=…` 屍骸**。plan 新 lang key「以 token 人化」嘅方案，想 key 住個 token——但 token 到嗰時**已經無咗 tag**，變成匿名 raw line。
4. **漏源頭冇堵，反而明文凍結**。`AskEngine.java:696` `appendCapped(facts, WorldgenIndex.lookup(question, gameDir, lang), factCap)` ＋ `WorldgenLookupAskTool.java:43` 兩處將 raw `[WORLDGEN] … configured= … count= …` **直接餵入 prompt**；`WorldgenFacts.looksLikeQuery`（`:46-60`）對 `ore`／`礦`／`矿` 都命中 ⇒ 問乜礦物都會觸發。plan §1 a-2 明文「**唔准改** `lookup()` 語意；`[WORLDGEN]` raw 格式**唔變**（F2 保持）」——即**故意保留漏源**，再靠一個後置 scrub 去追。模型見到 `count=9` 之後用中文寫「每區 9 個」，任何 regex 都冇保證追得返。A8 斷言「唔准 raw `configured=`／`count=`」＝**結構上保證唔到**。

### LD5 — b-1 `InfoCompleteness.append`：**死**

1. **token 集同期望輸出唔匹配**。§2 b-1 第 60 行定：loot→「table id **或人化名**」、worldgen→「**biome id／dim id**」（純 raw id）。但 §4 A-a4／A8 期望玩家文字係**人化**（`生態域：ad_astra:lunar_wastelands`；A8 只要求「提到生態域＋高度＋礦脈大小」）。中文答案寫「月球荒地」而唔重打 `ad_astra:lunar_wastelands` ⇒ token 零命中 ⇒ **吐假 gap line**。同一份 plan 對 loot 收人化名、對 worldgen 收 raw id，**自己唔一致**。
2. **未有證據支持「模型會講 raw biome id」**。E15 親掃 20 個真 trace：`configured=` **0 次**；`ad_astra:` 命中全部係**物品** id；**冇任何 biome id、冇任何 worldgen 答案**。即「答案含 raw biome id」呢個前提**今日完全未經量測**（見 §2 ②）。
3. `tooltip→?` 喺 plan 係**字面一個問號**（第 60 行）＝ token 集未定；而 A-b1 要「逐字」expected（第 94 行）⇒ **pre-register 唔完整**：tooltip 類嘅逐字期望根本未寫出嚟，實作者必須自己發明。
4. 插 footer 之前嘅 anchoring 面係通嘅（`ReplySources.java:11` HEADER；前例 `AskJeiHints.java:263`）——但 plan 寫 `:262-273`（E16 漂移），而真正風險係 §2 b-2 嘅 hook 位（LD6）。「原文零改動」＋「冇 footer ⇒ append」＝fail-open，方向正確。
5. 呢條嘅失敗模式係**玩家可見噪音**（答案已答對但仍多一段「資訊缺口」），正是 R5 反方 6:4 卡死同一類問題——plan 冇加「唔准吐假 gap」嘅負控。

### LD6 — b-2 單一 hook 點：**死**

**最硬、可以即刻證伪嘅一條**。plan §2 b-2 寫「落點＝`:980`（`replaceHowToGetBody`）**之後**、回傳前，呼叫一次」。親核 `read_file AskEngine.java 940-1023`：

- `:963     body = ReplySources.ensure(body, replySources, lang);` ← 主路徑
- `:969     if (frameKind == ModularFrameStandard.Kind.STANDARD && frameMatch.recipeIndex() != null) {`
- `:980             String replaced = AskReplyScrub.replaceHowToGetBody(body, line);` ← **縮排深 2 級，喺 `:969` 個 if 內**
- `:1010         }` ← 該 if 收口
- `:1012         if (override) { return AskResult.text(body)... }`
- `:1016         if (!questHits.isEmpty()) { return AskResult.of(body, questHits)... }`
- `:1019         return withSideQuests(body, ...)...`
- `:1023     }` ← 個 `if (!visibleAnswer.isBlank())` 收口

⇒ 「`:980` 之後」只喺 **STANDARD frame ＋ recipeIndex()!=null** 才會執行。其餘所有 frame（包括最常見嘅普通 ask）**`InfoCompleteness` 永遠唔會被呼叫** ⇒ b-1／b-3 交付物**出唔到街**。正確 anchor 只可以喺 `:963`（主路徑）之後、`:1012` 之前。plan 用「單一 hook 點」做賣點，但個 anchor 揀咗一個條件分支內部。

次要：plan 寫 missing「由 facts 組裝期（`:686-694` 同一段）收集」；實測 worldgen facts 嘅 append 喺 `:695-696`（喺 `switch`／block 迴圈**之外**，`:693` 係 block 迴圈收口），而 a-2 新增嘅 `routesForItem` 係**另一條來源**，根本唔喺 `:686-694` 呢段。plan 冇講呢條來源點入 missing。

### LD7 — 驗收設計：**死**

正面（反方照認）：fixture 真、機制真。E6 證明 A-a1／A-a4 引用嘅三行 raw **今日真到逐字**（`count=9`、`-80..80`、`type=minecraft:ore`、`size=9`、biome features 含該 feature）；E14 證明 `research/gen_tmp_check.py` 自動註冊機制真、51 個 harness（+2=53 可行）、新 harness 命名 `*Check.java` 合規；E2／E3 證明 §5 baseline 同 §4 A7 數字**逐字準確**。

致命缺陷：
1. **A-a1 照字面無法滿足**。「對 `ad_astra:moon_desh_ore` 回 ≥1 行**且含** `configured_feature …`、`count=9`、`height_range=-80..80`、`in biome ad_astra:lunar_wastelands`」——E8 證明前三項喺 `formatPlaced` 一條 line（`:427-441`），第四項 `in biome <b>` 係**另一條** line（`:641`／`:646`）。**冇任何單一 line 同時含四項** ⇒ 逐字斷言自相矛盾（要麼改讀成「回傳 list 含四項」）。
2. **A-a3 負控係假負控**（見 LD3.3）。
3. **A8／A9 冇執行人**。§3 明文「**唔准** commit／deploy／**開遊戲**（實作者）」，但 §4 A8／A9 係**真機**驗收（FTB 沙盒抽 5 件、主包 3 件、`focus_stolen=False`）。plan 冇指派誰跑、幾時跑 ⇒ 兩個最貴嘅驗收項**無人認領**（AGENTS.md 亦要求驗收過先 commit）。
4. **「今日跑會點」**：A-a1～a4／A-b1～b4 全部引用**今日唔存在**嘅 class（`InfoCompleteness` `ls` → No such file；`routesForItem`／`dimensionOf`／`mergeRoutes` 未存在）⇒ 今日係 **compile-fail**，唔係「紅測試」。真正今日可跑嘅只有 A7（我已跑：123 檔／122 綠＋1 紅，＝基線）。plan 冇區分「未有 class」同「斷言紅」，令 A7 以外嘅「今日紅」問題失去診斷價值。
5. **A-a2 冇能力分辨 LD1 歧義**（見 LD1.2）——兩個唔同讀法都過，等於冇鎖。
6. A-a4 負控「傳 null biome ⇒ 該段消失」方向對（同 §1 空值規則一致），但 `每區數量：9` = `count=9` 而 `礦脈大小：9` = `size=9`——兩個 9 對兩個唔同語意，A-a4 逐字 expected 會**偶爾同值而分唔開**（本 fixture 剛好兩者皆 9），冇獨立變體鎖死。

### LD8 — 白名單／唔做清單邊界：**死**

- **白名單漏檔**：按 LD3，`PackAiConfig` 嘅閘要麼搬要麼留，涉及 `AcquireAskTool` 已經 whitelist（3）——但按 LD4 要真正堵漏源，必須動 `WorldgenLookupAskTool.java`（plan 只寫「唔准郁 schema」，**冇** whitelist 佢嘅輸出人化）同 `JarLightIndex.java`（**完全冇出現喺白名單**）。即：**設計必要嘅檔有兩個喺白名單外**。
- **白名單包住咗一個錯誤前提**：`AskReplyScrub.java`（5）被列入，理由係「token 人化新增」——但 E11 證明人類人化根本唔喺嗰度（LD4.1）。白名單把 LD4 嘅誤診**固化**成契約。
- **白名單內但唔需要改**：`code_change_log.md`（11）係記錄檔、唔屬設計交付物（同 AGENTS.md「HANDOFF 唔可以代替 commit」同調，但列入白名單屬降噪失敗）。`research/gen_tmp_check.py`（10）只有喺「新 harness 已存在」時才需要重生，屬程序步驟而非交付物。
- 正面：「唔准郁」清單方向正確——`RecipeEmbed.java`／`RecipeCard.java`（A3 sha256 零改動）／`neoforge/`／`PackAiConfig.java`（唔加 config key）／`WorldgenLookupAskTool.java` schema 都核對得上真 code（`AcquireAskTool.java:14` 註釋確認 `blocks/*` 留低，同 §7 一致）✅

---

## §2 必答四題

### ① 呢個設計會唔會令**已經交咗嘅嘢**退步？

| 已交付物 | 風險 | 依據 |
|---|---|---|
| **v6 jar loot routes（`344e805`）** | **高／會退步** | `AcquireAskTool.java:65-75` 內部硬閘 `PackAiConfig.scanModJars()`（`PackAiConfig.java:447` **default false**）。plan 改簽名為傳入 `jarRoutes`，**冇寫個閘跟去邊**；同時 §3 釘死「唔加 config key」⇒ worldgen routes **無閘、default-ON**，同 jar routes default-OFF 並存 ⇒ 默認配置用戶嘅 acquire 輸出**實質改變** |
| **卡落位 `RecipeEmbed`／`RecipeCard`** | **低** | plan §3 禁改 ＋ A-b3 釘 sha256；`InfoCompleteness` 只插文字、`唔准含 card marker`。殘餘：`AskReplyScrub` 喺白名單內，而 `AskCardPlacementCheck` 依賴 scrub 行為 ⇒「只限新增、唔准改既有 regex」係自證、diff 睇唔出 |
| **python 閘** | **中低** | 基線實測 123 檔／1 已知紅（E3）。新 lang key 落 3 個 lang 檔同 `check_settings_registry.py` 唔同 domain（settings vs reply），大致安全；但**已知紅恰好係 `check_ask_display_leak`**，而本 plan 係**新增玩家可見行** ⇒ 擴大同一 leak 面而**冇新增任何閘**去守 |
| **既有 harness（51 個）** | **中** | E14 確認註冊機制。但 b-2 係喺答案**尾部插一段**，而 `AskMarkerIntegrityCheck`／`AskMarkerRepairCheck`／`stripLangMissLine`（`AskEngine:1004`）／`AskMissNoticeCheck` 都對答案尾部／miss line 落斷言 ⇒ 交互風險真實存在，而 A7 只寫「全跑、目標基線+2」，**冇**逐個講明呢 4 個係高危 |
| **`[WORLDGEN]` 既有行為** | **中** | plan 明文凍結 F2 raw 格式（唔改 `lookup()`）⇒ 現有 prompt 行為不變（好事），但亦令 LD4 嘅漏源永久化 |

### ② 最貴嘅未知（要咩量測才解得開）

**一條：「模型喺見到 raw `[WORLDGEN] count=9 height_range=-80..80` 之後，玩家可見文字實際上會唔會帶 raw id／raw 欄位？」**
- 為何最貴：同一條量測**同時**決定 LD5（token 集可唔可以只靠 raw id）同 LD4（後置 scrub 追唔追得切）——即兩條死因共用一個未知。
- 今日證據（E15）：20 個真 trace，`configured=` **0 次**，**零** biome id，**零** worldgen 答案 ⇒ 現有數據**答唔到**，因為根本冇跑過 A8 類問題。
- 具體儀器（最便宜）：用 §4 A8 同一組（FTB 沙盒、mod 礦物、每次新 seed、記 seed）跑 N≥20 次，對 `packai/trace/ask-*.jsonl` 做逐字 substring 統計三件事：(a) 答案含 `configured=`／`count=` 比率；(b) 答案含完整 `ad_astra:…` biome id 比率；(c) 答案含人化生態域名（「月球荒地」等）比率。判讀：若 (b) 低而 (c) 高 ⇒ LD5 token 集必須收人化名（即 plan 現版本會吐假 gap）；若 (a) >0 ⇒ LD4 後置 scrub 路線**當場證伪**，必須改去堵 prompt 源頭。
- 第二貴：世界生成索引首次**同步掃 359 jar** 嘅 wall-clock（plan §6① 自認但無量測、無閘、§3 又禁加 config key）。儀器：harness 內量 `doEnsure` elapsedMs（plan 已寫要記，但**冇任何閘**，即用戶無法關掉）。

### ③ Flip conditions（反方收手嘅最低限度，逐條可執行）

- **F-LD1**：plan 明寫改動點 = `WorldgenFacts.kindFromPath` 加 `dimension/` 分支 ＋ `Kind` 加 `DIMENSION` ＋ `ingest` 加 arm（或改喺 `WorldgenIndex` 前置關卡之前獨立掃）；**並且**二選一講死 type 規則；**並且**新增一條驗收斷言 FTB 沙盒映射**總數 = 11 個 biome**（附清單），唔止抽 2 點。
- **F-LD3**：plan 明寫 `mergeRoutes` 保留 `PackAiConfig.scanModJars()` 閘（jar 同 worldgen 同一閘；若要新 key ⇒ 要 SK 批准，因為 §3 現時禁止）；刪走 `int budget` 重複來源，改成單一 `clipAcquireLines` 來源；A-a3 負控改成**輸入驅動**（例如 `worldgenRoutes=List.of()` ⇒ 斷言前兩組次序）；並要求 diff 後 `AcquireJarRoutesCheck` 行為不變。
- **F-LD4**：人化搬到**生產側**（`routesForItem` 內／新增 `ReplyLang.formatWorldgenRoute(lang, …)`，比照 `JarLightIndex:293`／`ReplyLang:786` 手法）；或解凍 F2 連 `lookup()` 一齊人化。**並新增驗收斷言：prompt 文字（唔止答案）內零 `configured=`／`count=`**。
- **F-LD5**：token 集對 worldgen 亦收「人化名」（同 loot 一致）；`tooltip→?` 要麼定義清楚要麼刪；新增負控「答案只有人化名、冇 raw id ⇒ **唔准**出 gap line」。
- **F-LD6**：hook 由 `:980` 改去 `:963` 之後、`:1012` 之前嘅**主路徑**；並要求 harness 證明**所有 frame kind** 都會跑到（例如 counter／log 斷言）。
- **F-LD7**：A-a1 改成「回傳 list 含全部四項」；A8／A9 指名執行人（§3 禁實作者開遊戲 ⇒ 要 SK 或排程真機 run）；明記 A-b*／A-a* 今日係 compile-fail 而唔係斷言紅；A-a4 加「兩值相異」變體（唔好得 `size=9`／`count=9` 同值）。
- **F-LD8**：白名單補 `logic/JarLightIndex.java`（或明文宣佈 F-LD4 選生產側後由 `WorldgenFacts` 承擔）＋ 講明 `WorldgenLookupAskTool.java`「schema 凍結、輸出可人化」；若 F-LD4 未落實，先由白名單剔走 `AskReplyScrub.java`。

### ④ 最後比分行

**正方 3 : 反方 7**

比分依據：正方得分＝① 載重決定 LD2 存活（唯一擋得住嘅機制）；② §0 事實底經親核 11/11 成立、F4／F5／F6／§5／§4-A7 數字逐字準確（credible，唔係靠估）；③ ranged scope 紀律好（§7 剔得清、唔改 schema、唔加 config key、無 disk cache 且同 R5 B7 一致）。反方得分＝LD1／LD3／LD4／LD5／LD6／LD7／LD8 **七條死**，其中 LD6（anchor 落喺條件分支內）同 LD3（v6 交付默認行為會被改）係**可以即刻證伪／即刻會退步**嘅硬傷。

門檻（正方 ≥8 且反方 ≤2 才 pass，go=true）：**3:7 ⇒ 唔過，go=false**。

> 附註（唔係軟化，係記數）：若比分只按 LD 計，係 **1:7**——上面 3 分已經計埋「事實底準確」同「範圍紀律」兩項 plan 確實做對嘅嘢。

---

## §3 反方立場聲明

本輪**冇**為 plan 翻案；所有「plan 做對」嘅陳述都係親跑證據支持下嘅事實記錄（E2／E3／E4／E6／E14），唔構成對設計嘅支持。R1 反方結論：**唔准開工**，先做 §2 ③ 嘅七條 flip conditions（其中 F-LD1／F-LD3／F-LD6 係最低限度，必須先改）。
