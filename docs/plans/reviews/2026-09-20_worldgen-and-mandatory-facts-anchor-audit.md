# Code-anchor 核實審查：worldgen 三類（a）＋必答清單（b）plan v1

- 審查對象（唯一讀本）：`docs/plans/2026-09-20-worldgen-and-mandatory-facts.md`
- md5 驗證：**`f529f50328d0dbe55e72c76ac9530693` ✅ 相符**（`md5sum docs/plans/2026-09-20-worldgen-and-mandatory-facts.md`）
- 審查方式：逐條事實聲稱 → 真 code grep／sed 取**真行號**、真 pack 資料（python zipfile 讀 `packai_sandbox_ftb` 嘅 `mods/*.jar`）、每個數字本輪重跑。
- 硬規則遵守：**READ-ONLY**；本檔係本輪唯一寫入；無 `git add/commit`；無 gradle build（`compileJava`／harness 全部**冇跑**，屬「未核」）；無開遊戲。
- Repo：`super_minecraft_AI_player`，Java 樹 = `forge/1.19.2/src/main/java/com/skps9/packai/logic/`（下文相對路徑皆指此目錄，除另有標明）。

**結論：WRONG 5 條（4 個獨立根因）、未核 2 條、其餘 OK。** 最貴三條見 §2。

---

## §1 Claim ledger（每條一行）

| # | claim（引用 plan 原句） | 判定 | 真值／真行號 | 我跑過嘅命令＋關鍵輸出 |
|---|---|---|---|---|
| C1 | §0 F1「`WorldgenIndex` **只**由問題觸發（`AskEngine:695` `looksLikeQuery`），**冇** disk cache」 | OK | `AskEngine.java:695-696` = `if (WorldgenFacts.looksLikeQuery(question)) { appendCapped(facts, WorldgenIndex.lookup(question, gameDir, lang), factCap); }`；`WorldgenIndex.java:18` 註釋「Jar scan is capped (no disk cache)」；`:55 public static List<String> lookup(String query, Path gameDir, String lang)`；`:28 static final int MAX_JARS = 400` | `grep -n 'lookup\|cache\|MAX_JARS' WorldgenIndex.java` → 18,20,28,55,158 |
| C1b | §0 F1 子句「**冇** per-item 查詢 API」 | **WRONG** | 已有 per-item tool 入口：`WorldgenLookupAskTool.java:43` `List<String> lines = WorldgenIndex.lookup(q, dir, lang);`，`q = firstNonBlank(args.itemId, args.question)`（:37），且已註冊（`tests/check_ask_tool_loop.py:174` `assert "worldgen_lookup" in loop`） | `grep -rn 'WorldgenIndex\.' main/java` → `WorldgenLookupAskTool.java:43`；`sed -n '35,50p' WorldgenLookupAskTool.java` |
| C2 | §0 F2 現行輸出係 raw token 行（placed_feature／configured_feature／in biome 三式） | OK（附註） | 三式**真存在**：`formatPlaced :429-443`、`formatConfigured :418-427`、`HEADER + " placed_feature " + pf + " in biome " :641／:646`；`HEADER = "[WORLDGEN]" :20`。**附註：plan 引嘅 `sed -n '389,445p'` 只覆蓋三式之中兩式**——第三式（in biome）實際喺 `:641`／`:646`，唔喺 389–445 | `sed -n '389,445p WorldgenFacts.java`；`grep -rn 'in biome' WorldgenFacts.java` → 641,646 |
| C3 | §0 F3「`ReplyLang` 完全冇 `[WORLDGEN]` 人化（grep 0 hits）」＋「`AskReplyScrub.java:36` 只係 strip 標籤清單」 | OK | `grep -c WORLDGEN ReplyLang.java` → **0**；`AskReplyScrub.java:36` = `"WORLDGEN");`（`INTERNAL_SECTION_TOKENS` :27–36 嘅最尾一項） | `grep -c 'WORLDGEN' ReplyLang.java` → 0；`grep -n '' AskReplyScrub.java \| sed -n '25,50p'` |
| C4 | §0 F4 FTB 沙盒 `ad_astra … moon_desh_ore` 真值（feature／count／height／type／size／biome 含 pf） | OK | `placed_feature/moon_desh_ore.json`：`"feature":"ad_astra:moon_desh_ore"`、`{"type":"minecraft:count","count":9}`、height `trapezoid` `min_inclusive.absolute=-80`／`max_inclusive.absolute=80`；`configured_feature/moon_desh_ore.json`：`"type":"minecraft:ore"`、`config.size=9`；`biome/lunar_wastelands.json` features（巢狀 list-of-list）含 `"ad_astra:moon_desh_ore"` | `python ab_scan.py`（zipfile 讀 ad_astra-forge-1.19.2-1.12.7.jar）→ 逐檔原文輸出 |
| C5 | §0 F5「`data/*/dimension/*.json` 真存在：14 檔／4 jars（**ad_astra × 5** 睇到、createteleporters 亦有）；**兩種形狀**…」 | **WRONG** | 14 檔／4 jar ✅（ad_astra **11**、compactmachines 1、createteleporters2 1、l2library 1）→「ad_astra × 5」**錯**。形狀**唔止兩種**：`biome_source.type` 實測 3 值＝`minecraft:fixed` 7 檔 ＋ `minecraft:multi_noise` 5 檔 ＋ **無 `biome_source` 2 檔**（compactmachines、l2library） | `python ab_scan.py` → `DIM_FILES 14`、`DIM_JARS ['ad_astra…','compactmachines-5.1.0.jar','createteleporters2-1.19.2.jar','l2library-1.10.0.jar']`、`SHAPES {'minecraft:multi_noise': 5, 'minecraft:fixed': 7, 'None': 2}` |
| C6 | §0 F5「實測 pack＝FTB Skies Expert（`packai_sandbox_ftb`，**357 jars**）」＋ §6 風險①「357 jars」 | **WRONG** | `mods/*.jar` 實測 = **359**（非 357；另 mods 內有 1 個非 jar 檔 `autotest-dev-0.2.3.jar.bak-20260920-010100`） | `ls -1 "<instance>/mods"/*.jar \| wc -l` → **359**；`ls -1 \| grep -v '\.jar$'` → 只 1 個 .bak |
| C7 | §0 F6 11 個 biome 有映射（namespace ad_astra／createteleporters）；`lunar_wastelands → ad_astra:moon`；1 個歧義 `orbit → {6 個 orbit}` | OK | 實測 `MAPPED_BIOMES 11`／`AMBIGUOUS 1`；`ad_astra:lunar_wastelands -> ['ad_astra:moon']`；`ad_astra:orbit -> [earth_orbit, glacio_orbit, mars_orbit, mercury_orbit, moon_orbit, venus_orbit]`（6 個，與 plan 逐字同） | `python ab_scan.py`（同一 script，plan 規則重算） |
| C8 | §0 F7「`AcquireAskTool.mergeJarRoutes(itemId, lang, loose)`（`static`…）；`clipAcquireLines` → craft／acquire **12** 行、其餘 **3** 行」；引 `AskToolContext.java:115-143` | OK | `AcquireAskTool.java:65 static List<String> mergeJarRoutes(String itemId, String lang, List<String> loose)`（呼叫點 :57-58）；`AskToolContext.java:31 MAX_ACQUIRE_LINES_FULL = 12`、`:33 MAX_ACQUIRE_LINES_SLIM = 3`、`:120-122 acquireLineBudget`、`:145 clipAcquireLines`；引嘅 115-143 範圍命中（115 wantsFullAcquire／120 budget／125 clipLines） | `grep -n 'mergeJarRoutes\|clipAcquireLines' AcquireAskTool.java`；`grep -n 'MAX_ACQUIRE_LINES_FULL\|MAX_ACQUIRE_LINES_SLIM' AskToolContext.java` |
| C9 | §0 F8「答案最終處理次序（**單一 hook 點**）：`:963 body = ReplySources.ensure(...)` → `:980 body = AskReplyScrub.replaceHowToGetBody(body, line)`」 | OK（行號真）／**「單一 hook 點」不成立** | `AskEngine.java:963` = `body = ReplySources.ensure(body, replySources, lang);` ✅；`:980` = `String replaced = AskReplyScrub.replaceHowToGetBody(body, line);` ✅（在 `if (frameKind == ModularFrameStandard.Kind.STANDARD…)` 條件塊內）。**但** 另有 `:1310 return AskResult.text(ReplySources.ensure(body, List.of(), replyLang));`（`withSideQuests` :1298 早退）＋ :1012／:1016／:1019 三個 return；:963 所屬方法係匿名類嘅 `completeWithTools`（`askNoTools()` :813 內、`@Override` :823）→ 唔覆蓋 offline 分支（:1041+） | `grep -n 'ReplySources.ensure\|replaceHowToGetBody' AskEngine.java` → 963,980,1310；`sed -n '810,826p'`；`sed -n '955,1010p'` |
| C10 | §0 F9「`ReplySources.HEADER = (?m)(【來源】\|【来源】\|\[Sources\])`；`AskJeiHints.ensureCanonicalQuestLine`（`:262-273`）用同一 anchoring 插喺 footer 前」 | OK（範圍偏短） | `ReplySources.java:11` 逐字相符：`Pattern.compile("(?m)(【來源】\|【来源】\|\\[Sources\\])")`；`ensureCanonicalQuestLine` 實際**由 `:263` 起**，`:274 var m = ReplySources.HEADER.matcher(body);` 並喺 match 前插入 canonical（:275-282）→ plan 寫 `:262-273` 起點 −1、終點唔夠長 | `grep -n HEADER ReplySources.java`；`sed -n '255,282p' AskJeiHints.java` |
| C11 | §0 F10「`logic/InfoCompleteness.java` **今日唔存在**」＋「`AskToolEnv` 型別 harness 需要 MC bootstrap（R5 B3 已證）」 | OK | `ls InfoCompleteness.java` → `No such file or directory`；R5 report `:104`「欠 `PackAiConfig.setScanModJars(true)`、欠 MC bootstrap、而 `AskToolEnv` 係第一個要 MC 嘅 logic harness」、`:117`「純函數 seam 路線（B3 flip ②）」 | `ls logic/InfoCompleteness.java`；`grep -n 'B3\|純函數 seam' docs/plans/reviews/2026-09-19_item-info-completeness-plan-R5-opposing.md` |
| C12 | §0 F11 現有可重用 lang key：`packai.reply.loot_table_obtain`／`packai.reply.jar_loot`／`packai.reply.structure_chest_obtain`／`packai.label.src.worldgen` | OK | 三語皆存在：zh_tw／zh_cn＝`掉落表：%s`、`掉落：%s`、`箱子：`、`世界生成資料`／`世界生成资料`；en_us＝`Loot table: %s`、`loot: %s`、`Chests: `、`Worldgen data` | `python -c "json.load(…/lang/{zh_tw,zh_cn,en_us}.json)"` |
| C13 | §1 a-1 掃描落點「`WorldgenIndex.scanJarFile`／`walkTree` 內，凡 entry 匹配 `data/<ns>/dimension/<name>.json` ⇒ parse」 | **未核（實作缺口）** | `WorldgenFacts.kindFromPath :100-128` 只認 `tags/worldgen/`、`worldgen/{structure_set,structure,configured_feature,placed_feature,biome}/`、biome_modifier ⇒ **唔認 `dimension/`**；`isWorldgenPath :130` = `kindFromPath(path) != null` ⇒ `WorldgenIndex.scanJarFile:187`／`walkTree:131` 嘅 `isWorldgenPath` 閘會**直接 skip 所有維度檔**。要行 plan 必須同時改 `Kind` enum（`WorldgenFacts:22`）＋`kindFromPath`（:100）＋`idFromPath` switch（:135+）＋`ingest` switch（:183+），plan 冇寫 | `sed -n '/static Kind kindFromPath/,/^    }/p' WorldgenFacts.java`；`sed -n '/isWorldgenPath/,/^    }/p'`；`grep -n 'isWorldgenPath' WorldgenIndex.java` → 131,187 |
| C14 | §1 a-1「`WorldgenFacts.Store` 新增 `Map<String,String> biomeToDim` ＋ `String dimensionOf(String)`」 | OK | `Store` 係 `public static final class`（欄位 `biomes/structures/structureSets/configured/placed/modifiers/tags` 皆 `LinkedHashMap`），加 field／method 語意可行 | `sed -n '/static final class Store/,/formatMatches/p' WorldgenFacts.java` |
| C15 | §1 a-2「`store.formatMatches(itemId, MAX_ROUTES_PER_ITEM=8)`」 | OK | `WorldgenFacts:543 public List<String> formatMatches(String query, int maxLines)`（公開）；placed 匹 query 於 `:579-588`（`p.id()` 或 `p.configuredId()`）；miss line **唔喺** formatMatches，係 `WorldgenIndex.doLookup:92-93` 加 → plan「過濾 miss line」可行 | `sed -n '543,600p' WorldgenFacts.java`；`sed -n '82,96p' WorldgenIndex.java` |
| C16 | §1 a-3「唔准新工具（`check_tool_schema_stable.py:108` 會紅）」 | OK | `tests/check_tool_schema_stable.py:108` = `assert forge_only == ["knowledge_lookup"], f"CAPABLE_TOOLS forge extras={forge_only}"` → 加 forge-only 新工具即紅（行號精準） | `sed -n '100,115p' tests/check_tool_schema_stable.py` |
| C17 | §2 b-1「**純函數，零 MC 型別**」（簽名卻帶 `ReplyLang lang`） | **未核** | `ReplyLang.java` 唯一 MC 觸點＝`:39 public static String resolveMcLanguageCode(net.minecraft.client.Minecraft mc)`；static 區只讀 lang JSON＋Gson（import 無 `net.minecraft.*`）。「無 bootstrap 可唔可以載入／連結 ReplyLang」靜態讀唔到答案，本審查禁跑 gradle → 未證 | `grep -n '^import\|public static' ReplyLang.java`；`grep -n 'resolveMcLanguageCode' ReplyLang.java` → 39 |
| C18 | §2 b-2 落點 `:980`、input `:686-694`、唔准動 `:826` | OK | `AskEngine:826` 逐字 = `List<String> promptFacts = capable ? List.of() : factsLive;`（capable 清空語意）；`:686-694` = blocks 組裝（`default -> {// craft}`）+ `appendCapped` 迴圈（事實組裝段）；`:980` 位置見 C9 | `sed -n '826p'`；`sed -n '670,700p' AskEngine.java` |
| C19 | §3 白名單 11 項（含新檔） | OK | 存在：`WorldgenFacts.java`／`WorldgenIndex.java`／`AcquireAskTool.java`／`ReplyLang.java`／`AskReplyScrub.java`／`AskEngine.java`／`assets/packai/lang/{en_us,zh_cn,zh_tw}.json`／`research/gen_tmp_check.py`／`code_change_log.md`；新檔（`InfoCompleteness.java`、2 個 harness）確實未存在 | `ls` 各檔 |
| C20 | §4 A-a1 期望「`configured_feature ad_astra:moon_desh_ore`、`count=9`、**`height_range=-80..80`**、`in biome ad_astra:lunar_wastelands`」 | **WRONG** | 真值 = `` height_range=absolute -80..absolute 80 ``（`WorldgenFacts.verticalAnchor:771` 回 `"absolute " + num(...)` → `:755 parseHeightRange` 串 `min + ".." + max`）。`count=9`、`in biome` 兩項 ✅ | Java 逐行：`WorldgenFacts.java:755-770`、`:771-784`；**同格式嘅 repo python mirror** `tests/check_worldgen_lookup.py:398` `assert placed_h["height_range"] == "absolute -24..absolute 56"`、`:400` 同一字串斷言 |
| C21 | §4 A-a2「`ad_astra:lunar_wastelands → ad_astra:moon`；`ad_astra:orbit → null`（歧義）」 | OK | 同 C7 真值支持（11 映射中 orbit 為唯一歧義 ⇒ 整段唔用 = null） | `python ab_scan.py` |
| C22 | §4 A-a4 期望 zh_tw「`世界生成：…｜高度：**-80..80**｜礦脈大小：9｜每區數量：9｜維度：ad_astra:moon`」 | **WRONG** | 同 C20 同一根因：raw 行帶 `absolute -80..absolute 80`，而 plan a-3 只寫「空值整段刪」、**冇寫 strip `absolute `** ⇒ 照 plan 寫法欄位值唔會係 `-80..80` | 同 C20；`tests/check_worldgen_lookup.py:398,400` |
| C23 | §4 A-b3「`check_ask_card_fallback.py`＋`check_ask_marker_integrity.py` 保持綠；`RecipeEmbed.java`／`RecipeCard.java` sha256 不變」 | OK（現況） | 兩 python 檔存在且**本輪綠**；兩 java 檔存在（RecipeEmbed 66578 B／RecipeCard 24068 B） | `ls tests/check_ask_card_fallback.py tests/check_ask_marker_integrity.py`；baseline 見 §4 |
| C24 | §4 A7「python 閘 = 基線（**122 綠＋1 已知紅**，`check_ask_display_leak`）」 | OK | 實跑：`TOTAL=123`、唯一 `FAIL tests/check_ask_display_leak.py` → 122 綠／1 紅，**與 plan 完全相符** | `for f in tests/check_*.py; do python "$f" >/dev/null 2>&1 \|\| echo "FAIL $f"; done; echo "TOTAL=$(ls tests/check_*.py\|wc -l)"` |
| C25 | §4 A7「已註冊 Java harness 逐個任務名全跑（目標 = 基線 +2 個新 harness）」 | OK | 實測 **51** 個 `*Check.java`（`tmp-check.gradle` 亦 51 個 `'run…'` task）→ 基線 51，目標 53 | `find forge/1.19.2/src/test/java -name '*Check.java' \| wc -l` → 51；`grep -c "'run" forge/1.19.2/tmp-check.gradle` → 51 |
| C26 | §5「`git status --porcelain` 項數，今日 **127**：71 M／54 ??／2 D」 | OK | 實測 127（54 ??／2 D／71 M），逐字相符 | `git status --porcelain \| wc -l` → 127；`… \| awk '{print $1}' \| sort \| uniq -c` → 54 ??／2 D／71 M |
| C27 | §5「白名單 11 檔 sha256」＋「嚴禁裸 `git checkout -- .`（127 項 dirty 唔係本 plan 造成）」 | OK | §3 白名單確為 11 項（1–11）；127 項 dirty 已由 C26 證實存在 | 讀 §3；`git status` |
| C28 | §0 版本語境「MC **1.19.2** ＋ Forge **43.4.5**；packai `mod_version=0.2.3`」 | OK（附註） | `gradle.properties:7 minecraft_version=1.19.2`、`:18 mod_version=0.2.3`、`:8 forge_version=43.4.0`（**編譯**用）；instance `mmc-pack.json:32/34 cachedVersion/version = 43.4.5`（**runtime**）→ plan 講 runtime 43.4.5 ✅，但編譯版本係 43.4.0 | `grep -n 'mod_version\|forge_version\|minecraft_version' gradle.properties`；`grep -n '43\.4\.' mmc-pack.json` |
| C29 | 上游引用「v5…**正方 4:反方 6**，R5 報告…；**D0 已由 v6 `344e805` 完成**」＋「§V5.1（dim 資料源）、§V5.3（harness 前提）、§V5.4（預算）、§V5.8（驗收形狀）」 | OK | R5 `:11`／`:229`「正方 4 : 反方 6（go = false）」；R5 `:104` B3、`:117` B3 flip ②、`:159` B7；`git log -1 344e805` = `feat(acquire): route in-memory jar loot/usage refs into acquire tool (v6 P0)`；上游 plan 標題 `:217 V5.1`／`:228 V5.3`／`:233 V5.4`／`:254 V5.8` 全部存在 | `grep -n '^#\{2,4\} .*V5\.' docs/plans/2026-09-19-item-info-completeness-plan.md`；`grep -n '正方\|B3\|B7' …R5-opposing.md`；`git log --oneline -1 344e805` |

---

## §2 WRONG 詳解（5 條／4 個獨立根因）

### W1（C1b）「`WorldgenIndex` … **冇** per-item 查詢 API」— 錯
- 真 code：`logic/WorldgenLookupAskTool.java:43`，`q = firstNonBlank(args.itemId, args.question)`（:37），即**已存在**一個以 `item=mod:id` 為入口、直達 `WorldgenIndex.lookup` 嘅 per-item 世界生成查詢；且 `tests/check_ask_tool_loop.py:174` 斷言它已註冊入 tool loop。
- 影響：plan a-2 新開 `routesForItem` 唔係「首個 per-item 入口」；更關鍵係呢條既有 tool 路徑會**原封不動**把 `[WORLDGEN]` raw 行餵去 LLM（`clipChars 1600`），與 a-3「唔准入 raw `configured=`／`count=` 落玩家文字」直接矛盾（見 §3 L3）。

### W2（C5）「ad_astra × 5 睇到」＋「兩種形狀」— 兩個數字都錯
- 真值：14 檔中 ad_astra 佔 **11**（mars／glacio／mercury／venus／moon ＋ 6 個 `*_orbit`）；`biome_source.type` 實測 **3 種**：`minecraft:fixed` 7、`minecraft:multi_noise` 5、**無 `biome_source` 2**。
- 「兩種形狀」若理解為「`fixed`+`biome` 單一」／「`biomes[]` 陣列」係**結構上勉強**講得通（陣列式實際掛喺 `multi_noise`，`fixed` 檔用 `biome`），但 plan 完全冇提兩個「無 biome_source」檔，亦冇提 `multi_noise` 呢個 type 名 —— 實作者若照字面寫 `type == minecraft:fixed → 讀 biome`，會以為第二式都係 `fixed`。
- 好消息：用 plan a-1 嘅**實際規則**（fixed→`biome`；有 `biomes[]`→逐個）重算，得出 **11 映射／1 歧義**，同 F6 一致 ⇒ a-1 規則本身可寫出正確結果，只係 F5 嘅描述與數字錯。

### W3（C6）「357 jars」— 實測 359（§0 line 5 與 §6 line 109 兩處都寫 357）
- `ls -1 "<instance>/mods"/*.jar | wc -l` → **359**。可能係 plan 寫作後 mods 有變動（mods 內另見 `autotest-dev-0.2.3.jar.bak-20260920-010100`，時間戳 2026-09-20 01:01），但**以今日真值論，plan 數字錯**；§6 用「357 jars 同步掃描」去估首次 acquire 成本 ⇒ 成本估算基礎偏 2 個 jar（影響細，但屬事實錯）。

### W4（C20 ＋ C22，同一根因）`height_range=-80..80`／`高度：-80..80` — 真值帶 `absolute ` 前綴
- 真 code：`WorldgenFacts.verticalAnchor:771-784` 對 `{"absolute":-80}` 回 `"absolute -80"`；`parseHeightRange:755-770` 回 `min + ".." + max` ⇒ `height_range=absolute -80..absolute 80`。
- **repo 內部已有一致證據**：`tests/check_worldgen_lookup.py:395-400` 用同一 fixture 斷言 `height_range == "absolute -24..absolute 56"`。
- 影響：A-a1（acceptance 斷言字串）同 A-a4（玩家可見人化期望）**照 plan 寫會直接紅**；b-1 b-1 又明文「原文不動」⇒ 除非 a-3 額外加「strip `absolute `」規則（plan 冇寫），否則人化欄位值唔會係 `-80..80`。**建議**：要麼把 expected 改成 `absolute -80..absolute 80`，要麼在 a-3 明寫 「`height_range` 人化時剝 `absolute ` 前綴、`above_bottom`／`below_top` 對應語意」——二者必須揀一個寫死。

---

## §3 plan 漏掉的相關 code 路徑（逐條 file:line ＋ 影響）

- **L1（最重要）第二條 render／後處理通道 ＝ `client/service/AskService.java`。**
  - `:349`／`:2434` `AskReplyScrub.stripDuplicateSectionHeaders(result.answer())`；`:411`／`:2488` `AskCardFallback.ensureCards(...)`；`:1889`／`:1951`／`:1980`／`:2023` 用 `ReplySources.HEADER` 喺 footer 前後再切／再插；`:1886` `ReplyLang.tr(lang, "packai.reply.uses_supplement_leadin", …)` 係「footer 前插一行」嘅**既有先例**（同 plan b-1 同一手法）。
  - 影響：**plan b-1 喺 AskEngine 插嘅 gap header 會再經 AskService 呢層 scrub** —— 若 gap header 撞任何 `PURE_SECTION_HEADER`（怎麼來／怎麼用／用途…），`stripDuplicateSectionHeaders` 會當重複刪走（`tests/check_reply_structure_scrub.py:166-179` 正是此語意）；而 `tests/check_frame_standard_card_keep.py:105/152/155` 對 AskService 有**硬性 call 數斷言**（`suppressModularFrameCards`=6、`setFrameStandardKeep`=2、`beginAskLoop`=3）⇒ 一旦要改 AskService 配合，就同時越出 plan §3 白名單（白名單**冇** AskService.java）＋打紅該閘。
- **L2 AskEngine 有第二個 footer 出口，繞過 plan 指定嘅 `:980`。**
  - `:1310 return AskResult.text(ReplySources.ensure(body, List.of(), replyLang));`（`withSideQuests :1298` 早退：override 或無任務）＋ `:1012`／`:1016`／`:1019` 三個 return；`:963` 所屬係匿名類 `completeWithTools`（`askNoTools()` :813 內、`@Override` :823），而 offline 分支喺 `:1041+`。
  - 影響：b-2 若照「`:980` 之後、回傳前」字面落點，gap line **只會覆蓋 STANDARD-frame 分支**；offline／無任務／override 路徑冇 gap line —— 直接違反 plan b-1 自己寫嘅「冇 footer ⇒ 直接 append（fail-open，唔准靜默唔出）」。要 fail-open，hook 應擺喺所有 return 之前嘅共同點（例如 :1010 之後、:1011 之前），plan 未釐清。
- **L3 `logic/WorldgenLookupAskTool.java:43` 係既有 per-item worldgen 入口，plan 只寫「唔准郁 schema」而冇納入 a-2／a-3 設計。**
  - 影響：加完 `routesForItem` 之後，LLM 仍可經 `worldgen_lookup` tool 拎到 raw `[WORLDGEN]` 行（`:52 clipChars(…, 1600)`）；與 a-3 嘅「唔准入 raw `configured=`／`count=` 落玩家文字」衝突（tool 輸出 → LLM → 答案）。
- **L4 `WorldgenFacts.kindFromPath :100`／`isWorldgenPath :130` 唔認 `dimension/`（見 C13）。**
  - 影響：a-1 照抄掃描落點會掃到 **0 個維度檔**。要行必須同時改 `Kind` enum（`WorldgenFacts:22`）＋`kindFromPath`（:100）＋`idFromPath` 之 `switch (kind)`（:135 起）＋`ingest` 之 `switch (kind)`（:183 起）＋ `WorldgenIndex` 嘅 `isWorldgenPath` 閘（scanJarFile:187／walkTree:131）。白名單有 `WorldgenFacts.java` ⇒ 語意上合法，但 plan §1 a-1 完全冇提，亦冇為新增 `Kind.DIMENSION` 加對應 python mirror 更新。
- **L5 既有 python 閘會被新改動打紅／有 mirror 同步義務（plan §4 A7 只列「基線」而冇逐條風險）。**
  - `tests/check_worldgen_lookup.py`：mirror `WorldgenFacts` parse/format（`:12-42` fixtures、`:279-341` format mirror、`:398/400` height_range 字串）→ a-1／a-2 改 parse 或 format 語意，此 mirror 會漂。
  - `tests/check_reply_structure_scrub.py`：`:851-974 check_source()` 讀 **`AskReplyScrub.java` 原文**並硬性比對 `INTERNAL_SECTION_TOKENS`（:882-884）、`EXTRA_BARE_SECTION_TOKENS`（:886）、`SCROLL_SECTION_REGEX`（:889）、`INTERNAL_ROLE_VALUES`（:893）同 Python 端 mirror（`:56-69`）；`:977-989 check_sources_ensure()` 讀 `ReplySources.java`；`:1000-1009 check_wiring()` 讀 `AskService.java`（要求 `stripDuplicateSectionHeaders` 出現 ≥2 次、且喺 `AskCardFallback.ensureCards` 之前）。plan §3 item 5 講「AskReplyScrub 只限 token 人化新增、唔准改既有 regex 行為」——方向對，但**冇提此閘有 mirror 同步義務**：加 token／改 scrub 語意即紅。
  - `tests/check_internal_label_parity.py`：`:90+` 逐 key 驗 **6 個 lang 檔**（2 樹 × 3 語）＋ orphan role key 檢查 ⇒ plan 用 `packai.label.*`／role key 就會被要求 6 檔齊；plan 新 key 用 `packai.reply.*` **故安全**，但此閘是「新 label key」嘅隱形閘，plan 未提。
  - `tests/check_dual_tree_sync.py`／`check_dual_tree_diff_symmetry.py`：`neoforge/README_PAUSED.md` 存在 ⇒ pause 模式，forge-only 新檔（`InfoCompleteness.java`）只 **WARN** 唔 FAIL（兩者本輪皆綠）——**唔會紅，但 plan 未交代呢個豁免**（唔講清楚，實作者／後續 review 會誤以為唔可以新增 forge-only 檔）。
  - `tests/check_tool_chat_turn.py:48-52` 讀 `AcquireAskTool.java` 並斷言含 `additionalProperties`／`toolMissNote`／`acquire empty`；`tests/check_ask_tool_context.py:31` 斷言 `clipAcquireLines(` 喺 `AskToolContext`、`:44` 斷言 `AskToolContext.clipAcquireLines` 喺 `AskEngine`。a-3 改 `mergeJarRoutes` 時必須保住呢啲字面。
- **L6 `AskEngine:346` 同 `:465` 亦各自直接嗌 `AskToolContext.clipAcquireLines`**（唔經 `AcquireAskTool.mergeJarRoutes`）。
  - `:346 String acqShot = acquire.isEmpty() ? "" : String.join("\n", AskToolContext.clipAcquireLines(acquire, question));`（shot0 記錄）；`:465 List<String> clippedAcquire = AskToolContext.clipAcquireLines(acquire, question);`（直接組 acquireLines 落 答案）。
  - 影響：plan a-3 只喺 `AcquireAskTool` 內加 worldgen routes ⇒ **呢兩條路拎唔到 worldgen routes**（即玩家經 non-tool 路徑問「點攞」時，世界生成資訊唔會出現）。若要覆蓋，就要改 AskEngine —— 直接衝突 plan §3 item 6「AskEngine **只准**加 1 個 hook 呼叫 ＋ missing 收集」。
- **L7 `AcquireAskTool` 其他消費者（改動要保住）。**
  - Java harness：`src/test/java/com/skps9/packai/logic/AcquireJarRoutesCheck.java:95`（`new AcquireAskTool()`）、`:111 private static String run(AcquireAskTool tool, …)`；`AskToolLoopCheck.java:494-495`（`new AcquireAskTool()` 用其 `description()`）。
  - Python 硬性檔名清單：`tests/check_ask_tool_loop.py:119`、`tests/check_ask_player_tool.py:20`。
  - 影響：`mergeJarRoutes` 改簽名／改語意會連帶影響 `AcquireJarRoutesCheck`（plan 未列入驗收）。

---

## §4 我實跑嘅 baseline 數字（本輪，2026-09-20）

| 項 | 命令 | 實測值 |
|---|---|---|
| Python 靜態閘 | `cd <repo> && for f in tests/check_*.py; do python "$f" >/dev/null 2>&1 \|\| echo "FAIL $f"; done; echo "TOTAL=$(ls tests/check_*.py\|wc -l)"` | **TOTAL=123，1 FAIL = `tests/check_ask_display_leak.py`** ⇒ 122 綠／1 紅（與 plan C24 相符） |
| Git 工作樹 | `git status --porcelain \| wc -l` ＋ `awk '{print $1}' \| sort \| uniq -c` | **127**（54 `??`／2 `D`／71 `M`）（與 plan C26 相符） |
| Java harness 註冊數 | `find forge/1.19.2/src/test/java -name '*Check.java' \| wc -l`；`grep -c "'run" forge/1.19.2/tmp-check.gradle` | **51／51** |
| 沙盒 mod jar 數 | `ls -1 "<instance>/mods"/*.jar \| wc -l` | **359**（plan 寫 357 ⇒ 見 W3） |
| 沙盒 jar-cache 檔數 | `ls -1 "<instance>/config/packai/jar-cache/" \| wc -l` | **361** |
| 維度檔／jar／形狀 | `python ab_scan.py`（zipfile 讀 359 jar） | 14 檔／4 jar；`fixed` 7／`multi_noise` 5／無 `biome_source` 2 |
| biome→dim 映射 | `python ab_scan.py` | 11 映射／1 歧義（`ad_astra:orbit` → 6 個 `*_orbit`） |

**未跑（本審查禁跑，標「未核」，非「通過」）**：`./gradlew compileJava compileTestJava`、任何 Java harness（含 `AskMarkerIntegrityCheck`）、真機 trace／`latest.log` 核對。⇒ C17（純函數 harness 能否無 MC bootstrap 載入 `ReplyLang`）與 C13（實作缺口）屬**推論級**結論，最終以實作後 gradle 為準。

---

## §5 給上游（R1 反方）嘅重點

1. **必改**：A-a1／A-a4 嘅 `height_range`／`高度` 期望值（W4），否則新 harness 必紅。
2. **必補**：a-1 要明寫「新增 `Kind.DIMENSION`（`kindFromPath`／`isWorldgenPath`／`idFromPath`／`ingest` 四處）」（L4）；F5 形狀／數字更正（W2）；jar 數更正（W3）。
3. **必答**：b-2 hook 嘅 fail-open 落點（L2）＋ 世界生成 routes 到底走唔走 `AskEngine:346/465` 兩條既有 acquire 路徑（L6）＋ AskService 第二通道要唔要配合（L1，涉及白名單擴張）。
4. F1「冇 per-item API」改寫（W1），並把 `WorldgenLookupAskTool` 嘅 raw line 衝突（L3）寫入 §7 明文唔做或交付物。
