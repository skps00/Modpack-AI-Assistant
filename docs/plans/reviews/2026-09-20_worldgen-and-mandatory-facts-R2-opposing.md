# R2【有界反方】review：worldgen 三類（a）＋必答清單（b）plan v2 — 2026-09-20

- 對象：`docs/plans/2026-09-20-worldgen-and-mandatory-facts.md`
- 版本核對：`md5sum` → **`3ede89e532ec9ef49b10f94e86fdc23d`** ＝ dispatch 指定值 **一致**（未停手）。
- **本輪係有界輪**：只核 R1／anchor 表列嘅 **8 條 flip condition** 係唔係已 RESOLVED；**唔加新要求**（唯一例外＝v2 自相矛盾／新引入錯誤，見 §2）。
- 硬規則遵守：**READ-ONLY**；本檔係本輪唯一寫入；無 `git add/commit`；**無 gradle build／無開遊戲**（涉及編譯／真機嘅項一律標「未核」，唔當通過）；每條判定附 file:line 或我親跑嘅命令輸出。
- 判分門檻（dispatch 指定）：`正方 ≥8 且 反方 ≤2` 才 pass／go=true。

---

## §0 本輪親跑證據（E1–E12；全部今日、真 code／真 pack）

| 代號 | 命令（實跑） | 輸出（原文摘要） |
|---|---|---|
| E1 | `md5sum docs/plans/2026-09-20-worldgen-and-mandatory-facts.md` | `3ede89e532ec9ef49b10f94e86fdc23d` ✅ |
| E2 | `git status --porcelain \| wc -l` | **127**（＝plan F14）✅ |
| E3 | `for f in tests/check_*.py; do python "$f" \|\| echo FAIL $f; done; ls tests/check_*.py \| wc -l` | `TOTAL=123`；唯一 `FAIL tests/check_ask_display_leak.py` ⇒ **122 綠＋1 已知紅**（＝plan F14／A7）✅ |
| E4 | `find forge/1.19.2/src/test/java -name '*Check.java' \| wc -l`；`grep -c "'run" forge/1.19.2/tmp-check.gradle` | **51／51**（＝A7 基線 51，目標 53）✅ |
| E5 | `python r2_dimscan.py`（zipfile 掃沙盒 **359** jar 全部 `data/*/dimension/*.json`） | `MOD_JARS 359`／`DIM_FILES 14`／`DIM_JARS 4`／`SHAPES {multi_noise:5, fixed:7, None:2}`／`PER_SRC {ad_astra:11, compactmachines:1, createteleporters2:1, l2library:1}`／`MAPPED_OK 10 AMBIG 1`／`lunar_wastelands → {ad_astra:moon}`／`orbit → 6 個 *_orbit` ⇒ plan F5／F6 **逐字準確** ✅ |
| E6 | 同上 script 讀 `ad_astra-forge-1.19.2-1.12.7.jar` fixture | `placed_feature/moon_desh_ore.json`：`feature=ad_astra:moon_desh_ore`、placement `minecraft:count count=9`、`minecraft:height_range` trapezoid `min_inclusive.absolute=-80`／`max_inclusive.absolute=80`；`configured_feature`：`type=minecraft:ore`、`config.size=9`；`biome/lunar_wastelands` features 含該 pf ⇒ plan F7 **逐字真** ✅ |
| E7 | `grep -n 'verticalAnchor' -A14 WorldgenFacts.java` | `:771 private static String verticalAnchor`、`:777 return "absolute " + num(o.get("absolute"))`；`:766 return min + ".." + max` ⇒ 真值 **`absolute -80..absolute 80`**（＝plan F3）✅；repo 自家 mirror 亦用同一格式：`tests/check_worldgen_lookup.py:398` `== "absolute -24..absolute 56"`、`:400` 同一字串 ✅ |
| E8 | `grep -n 'scanModJars' config/PackAiConfig.java`；兩個 instance `config/packai-client.toml` | `:447 .define("scanModJars", false)`（預設 OFF）／`:919` getter；`AI_test_NFWC_DIM` 同 `packai_sandbox_ftb` 兩檔**都係 `:140 scanModJars = true`** ⇒ plan F9 **真** ✅ |
| E9 | `ls sandbox/mods/*.jar \| wc -l`；`ls research/gen_tmp_check.py`；`ls forge/1.19.2/research/gen_tmp_check.py` | **359**（＝plan §3 item 9 更正後數字）；`research/gen_tmp_check.py` **真喺 repo 根**（`-rw-r--r-- 1524 九月 14`），`forge/1.19.2/research/` **唔存在** ⇒ plan §3 item 9 路徑**正確** ✅ |
| E10 | `ls "$TEMP/packai_autotest_run.py"`；`grep -n 'PACKAI_SANDBOX\|PACKAI_WORLD\|PACKAI_CASES\|--force'` | 檔真存在（226 行）；`:16 PACKAI_SANDBOX`／`:26 PACKAI_WORLD`／`:28 PACKAI_CASES`／`:171 --force` ⇒ A8／A9「執行人寫死」**指向真 artefact** ✅ |
| E11 | `grep -n 'stripDuplicateSectionHeaders\|scrubPromptEcho\|stripFactChrome\|scrubInternalFieldEcho\|replaceHowToGetBody\|stripLangMissLine' AskReplyScrub.java` ＋ 全 repo call site | `:857 stripDuplicateSectionHeaders`、`:906 scrubPromptEcho`（`:922 stripFactChrome` → `:923 scrubInternalFieldEcho`）、`:948 replaceHowToGetBody`、`:1079 scrubInternalFieldEcho`、`:1131 stripFactChrome`、`:1645 stripLangMissLine`；現實鏈＝`AskService.java:349 stripDuplicateSectionHeaders` → `AskResult.java:193 scrubPromptEcho` → `Plainify.forMinecraftUi` ⇒ plan A-b3 引用嘅 pipeline **真存在** ✅ |
| E12 | `sha256sum RecipeEmbed.java RecipeCard.java`；6 個關鍵 python 閘逐一跑 | `5126f917…`／`1c174fa3…`；`check_ask_card_fallback`／`check_ask_marker_integrity`／`check_worldgen_lookup`／`check_reply_structure_scrub`／`check_tool_schema_stable`／`check_dual_tree_sync` **全部今日綠** ✅ |

**反方先認一筆**：plan §0 十一條事實（F1–F14）我逐條親核，**F3／F5／F6／F7／F9／F13／F14 逐字準確**，v2 更正嘅三處（`absolute` 前綴、`ad_astra` 11 檔、359 jars）**全部改對**；白名單紀律（撤 `AskReplyScrub.java`、唔加兩個檔並附理由、`research/` 路徑）亦**真做咗**。以下扣分全部係設計／規格面，唔係事實面。

---

## §1 8 條 flip condition 逐條判定

| # | flip condition（dispatch 原文） | 判定 | 證據（file:line ／我親跑） |
|---|---|---|---|
| 1 | **a-1**：plan 寫死新 `Kind.DIMENSION` ＋ 4 個 `Kind` 使用點，並聲稱 `idFromPath` 已核無需改、`WorldgenIndex` 兩入口靠 `isWorldgenPath` 自動覆蓋 | **NOT-RESOLVED** | ◆ 「兩入口自動覆蓋」**真**：`WorldgenIndex.java:131`（`walkTree`）／`:187`（`scanJarFile`）都係 `if (!WorldgenFacts.isWorldgenPath(path)) continue;`，而 `isWorldgenPath:129-131` = `kindFromPath(path) != null` ⇒ 加 branch 即通（E5 全 repo grep `isWorldgenPath\|kindFromPath` 只有 `WorldgenFacts`（定義）＋`WorldgenIndex`（2 處）＋`WorldgenFactsCheck:79-80`）。<br>◆ **但「只有 4 處、idFromPath 無需改」係事實錯**：`WorldgenFacts.java:134 idFromPath` 內部 `:155 String folder = switch (kind) { case BIOME…; default -> ""; }`，`:163 if (folder.isEmpty() \|\| !after.startsWith(folder)) return "";` ⇒ 對 `dimension/moon.json`，`folder=""` ⇒ **回空字串** ⇒ `ingest:184-187`（`id = idFromPath(...)`；`if (kind == null \|\| id.isEmpty()) return;`）**直接丟棄所有維度檔** ⇒ `biomeToDim` 恆空、`dimensionOf` 恆 null、A-a1b／A-a3 必紅。即需要第 **5** 個改動點（`case DIMENSION -> "dimension/"`；或 `<ns>:<檔名>` 特判），plan §1 a-1 item 3（`:34`）明文寫「✓（無需特別處理，但要明確寫『已核無需改』）」＝ **錯**。 |
| 2 | **a-2**：`routesForItem` 過濾 rules（丟 miss line、只留 placed／configured(`type=minecraft:ore`)／in biome、回 `List.of()`）＋`lookup()` 語意零改 | **RESOLVED** | 行格式同 filter 面全部真：`formatPlaced:429-443`（`configured=`／`count=`／`height_range=` 一條行）、`formatConfigured:418-427`（`type=`／`size=`）、`in biome` 係另一條行 `:641`／`:646`；`formatMatches:543-671` 會拖 structure（`:560`）／structure_set（`:569`）／biome（`:553`）／modifier（`:593`）／tag（`:601`）**五類噪音** ⇒ plan 明文 filter 走，**有實質作用**（唔係 R1 講嘅純 padding）。miss 只在 `WorldgenIndex.doLookup:85`／`:93`，`formatMatches` 從來唔出 miss ⇒「永遠回 `List.of()` 表無料」可行；`doEnsure:70-71`（`if (ready && abs.equals(loadedDir)) return;`）冪等、可重用；`missLine` 係 `WorldgenFacts:91 public static` ⇒ WorldgenIndex 叫得到；`lookup():55`／raw 格式零改動與 F2 一致。 |
| 3 | **a-3**：閘保留 `PackAiConfig.scanModJars()`（兩 instance 都 true）、單一 budget `clipAcquireLines`、純函數 seam `mergeRoutes` 次序 jar→worldgen→loose | **RESOLVED** | `AcquireAskTool.java:65 static List<String> mergeJarRoutes(...)`、`:68 if (PackAiConfig.scanModJars()) {` 真存在；`PackAiConfig:447` 預設 false、`:919` getter，兩 instance `packai-client.toml:140 = true`（E8）⇒「唔加 config key、閘包住兩條通道」語意成立、行為同 jar routes 對齊。單一預算：`AcquireAskTool:57-58` 生產路徑就係 `AskToolContext.clipAcquireLines(mergeJarRoutes(...), args.question)`，plan 明寫「唔准再引入第二個 budget 常數」＋ A-a2 負控 `budget=3` ⇒ 對齊。seam 可行：`mergeJarRoutes` 讀閘收集三組、`mergeRoutes` 純排序（`LinkedHashSet` 手法＝現有 `:66-67`／`:97-102 addLine`），次序 jar→worldgen→loose 可寫死。 |
| 4 | **a-4**：人化搬去**生產側** `AcquireAskTool.humanWorldgenRoute`（同 `humanJarRoute` 同層）＋ ReplyLang 新 key ×3 語言 ＋ 只剝 `absolute ` 前綴 ＋ 工具輸出零 raw 欄位斷言 | **RESOLVED** | ◆「`humanJarRoute` 真喺生產側」**真**：`AcquireAskTool.java:85 private static String humanJarRoute(String lang, String code)`（同一檔、同層，`:88` 用 `ReplyLang.lootTableObtain`）；ReplyLang 側 `lootTableObtain:454`／`jarLoot:786`（E11）。<br>◆「`AskReplyScrub` 真無人化」**真**：全檔 grep `WORLDGEN` **只有 1 hit** ＝ `AskReplyScrub.java:36`（`INTERNAL_SECTION_TOKENS` 尾項，屬 strip 清單）；`configured=`／`count=`／`placed_feature` **0 hit**；`ReplyLang` 人化 call 全部係 source-label／role label（`:726-768`／`:820`／`:838`），**冇** raw token→人化 ⇒ plan「唔喺 AskReplyScrub 做人化」**正確**。<br>◆「只剝 `absolute `」同 E7 真值＋A-a3 期望（`高度：-80..80`）自洽；`above_bottom `/`below_top ` 保留規則與 `verticalAnchor:779-783` 對應。<br>◆ 工具輸出零 raw 斷言在 A-a3 期望欄逐字列明。 |
| 5 | **b-1**：gap 行由生產側提供（唔准判定側即場造）＋ token regex 寫死 ＋ 冇現成 key 嘅類（tooltip）明文剔除 | **RESOLVED** | §2 b-1（`:70-74`）逐項寫死：① 「**Gap line 由生產側產生**」＋ loot→`ReplyLang.lootTableObtain`、worldgen→`AcquireAskTool.humanWorldgenRoute` 嘅同一行、usages／craft→該類現成 key；② 「**冇現成 key 嘅類（tooltip）⇒ 唔准自己發明字串，直接唔計入 gaps**（寫入已知限制）」＝明文剔除；③ token regex 逐字寫死 `[a-z0-9_.-]+:[a-z0-9_./-]+`、切法（`|`／`=`／空白）、命中語意（任何一個）、判定用 `answer.toLowerCase(Locale.ROOT)`。另 `append` 簽名明文改用 `final String langCode` 避 MC bootstrap（同 F13 一致）。 |
| 6 | **b-2**：hook 落點在主路徑（STANDARD block 之後、`if (override) return …` 之前）＋ 明文寫出已知未覆蓋早退路徑 | **RESOLVED（落點真喺主路徑）／覆蓋聲明仍不完整，見 §2②** | 真 code 核實插入點＝**`AskEngine.java:1010`（`frameKind == … STANDARD && frameMatch.recipeIndex()!=null` 塊收口）與 `:1011`（`if (override) {`）之間**：`:963 body = ReplySources.ensure(...)` → `:969-1010` STANDARD 塊 → `:1011-1013 override return` → `:1015-1017 quest return` → `:1019 withSideQuests return` ⇒ 落點**真喺三條主 return 之前**（R1 LD6「anchor 落喺 `:980` 條件分支內」已修好）。plan `:79` 亦明文寫出 1 條已知未覆蓋早退 `AskResult.text(ReplySources.ensure(body, List.of(), replyLang))`（真行＝`:1310`，`withSideQuests` 內 `if (override \|\| allQuests.isEmpty())`）⇒ 兩項要求（落點＋明文聲明）**都齊**。惟聲明只寫「**另一**早退」（單數）而實際同一方法仲有 ≥4 條出口繞過 hook（見 §2②），屬覆蓋聲明不足。 |
| 7 | **驗收表**：逐字 expected（含 `height_range=absolute -80..absolute 80`）＋真負控（budget=3、次序 swap、token 改名）＋A8/A9 寫死執行人 ＋ A-b3 落位存活 | **RESOLVED** | ① A-a1a（`:101`）逐字含 `height_range=absolute -80..absolute 80`（E7 真值）、`count=9`、`size=9`、`in biome ad_astra:lunar_wastelands`，並改為逐行 `contains`／唔准斷行序 ⇒ R1「冇任何單一 line 同時含四項」嘅自相矛盾**已修**；② 真負控三條齊：`budget＝3`（`:104`）、次序（`:104`「把 loose 排前」）、token 改名（`:107`）；③ A8／A9 執行人寫死 `%TEMP%\packai_autotest_run.py` ＋ `PACKAI_SANDBOX`／`PACKAI_WORLD`／`PACKAI_CASES` ＋ `--force`（`:113`）＝ **E10 真 artefact**；④ A-b3（`:108`）引用嘅 pipeline 三個函數 **E11 全部真存在**且真喺答案鏈上（`AskService:349` → `AskResult:193`）⇒ 落位存活斷言**可執行、非空談**。 |
| 8 | **白名單**：撤 `AskReplyScrub.java`、唔加 `JarLightIndex.java`／`WorldgenLookupAskTool.java`（附寫死理由）、`research/gen_tmp_check.py` 路徑改 repo 根、jar 數 359 | **RESOLVED** | §3（`:94`）明文「**唔准郁**：… `logic/AskReplyScrub.java` …」＝已撤出白名單（白名單 11 項逐項核：1–11 全部真存在／新檔標明）；`:95` 附寫死理由（「raw worldgen 行由 `WorldgenIndex` 出、人化喺 `AcquireAskTool`，唔經 jar-cache shard／唔改工具 schema ⇒ 兩檔今日真無需改；若發現要改即停手報告」）；`research/gen_tmp_check.py` **E9 證實真喺 repo 根**；jar 數 **359**（E5／E9）✅。 |

**小結：8 條之中 7 條 RESOLVED、1 條（a-1）NOT-RESOLVED。**

---

## §2 新發現（dispatch 允許嘅唯一例外：v2 自相矛盾／前提錯）

### ①【硬傷，扣分主因】**a-1 內部自相矛盾 ＋ 前提寫錯**（＝上表 #1）

- 錯句：plan `:34`「`idFromPath()`（`kind == Kind.TAG ? "#" + id : id`）⇒ DIMENSION 用 `<ns>:<檔名>` ✓（**無需特別處理**，但要明確寫「已核無需改」）」。
- 真 code：`WorldgenFacts.java:155-162` 有一個 `switch (kind)`（`BIOME`／`STRUCTURE`／`STRUCTURE_SET`／`CONFIGURED`／`PLACED`／`default -> ""`），`:163-165` 對空 folder **直接 `return ""`**；`:176` 嘅 `kind == Kind.TAG ? "#" + id : id` 根本 **reach 唔到**（`Kind.DIMENSION` 喺 `:163` 已經 early-return）。⇒ `ingest:185` 的 `id.isEmpty()` guard 令**所有維度檔靜默丟棄**：`biomeToDim` 恆空 ⇒ A-a1b（`lunar_wastelands → ad_astra:moon`）必紅、A-a3「維度：」欄**永遠消失**。
- 同時係 **v2 自我矛盾**：plan `:103` A-a1c 明文斷言「`idFromPath` 回 `ad_astra:moon`」——同 `:34`「無需改」**直接衝突**（今日真值＝`""`，E5 由 code 逐行推得；anchor 報告 L4 亦早已點名「`idFromPath` 之 `switch (kind)`（`:135` 起）」要改，v2 唔單止冇改，反而寫死「已核無需改」）。
- 影響：照 §1 落手 ⇒ 本 plan 招牌功能（a-1 維度映射／a-3「維度：」欄）**整條靜默失效**，但唔會 compile error、亦唔會拋異常 ⇒ 屬「靜默錯」而非「爆」。唯一幸運之處：plan 自家 A-a1c 會跑紅（實作時可被捉到），所以係「規格錯、唔係設計錯」。

### ②【次要】**b-2 覆蓋聲明不完整 ＋ 缺「所有出口都會跑到」harness**

- plan `:79` 只列 **1 條** 未覆蓋早退，並用單數「**另一**早退」。實測同一方法（`:923 if (!visibleAnswer.isBlank())` 塊之後）仲有 ≥4 條出口**繞過** hook 位置（`:1010/:1011`）而 plan 未列：`AskEngine.java:1029`（quest-only，`:1025 if (!questHits.isEmpty() && !override)`）、`:1038`（plain）、`:1096`（offline quest）、`:1107`（offline friendly）；另 `:1010` 之前已有 `:396`／`:875` 兩個早退。⇒「本 hook 覆蓋 AskEngine 主路徑」嘅**未覆蓋清單**唔夠，實作者會誤以為只漏 1 條。
- R1 嘅 F-LD6 後半（「要求 harness 證明**所有 frame kind** 都會跑到（例如 counter／log 斷言）」）v2 **冇**落實（§4 驗收表冇任何 frame-kind 覆蓋斷言）。此項屬 R1 flip condition 原文要求，非本輪新加。

---

## §3 驗收表逐條：「今日（實作前）跑一次會唔會紅」＋「引用嘅 fixture／artefact 今日真唔真存在」

| 行 | 今日跑會點 | 引用 fixture／artefact 今日真存在？ |
|---|---|---|
| A-a1a（`routesForItem`） | **今日唔可跑**：方法唔存在 ⇒ 屬 compile-fail，**唔係「斷言紅」** | ✓ 真：三行 raw 內容同 F7 逐字相符（E6）；沙盒 359 jar 在（E5） |
| A-a1b（`Store.dimensionOf`） | 唔可跑（方法唔存在 ⇒ compile-fail） | ✓ 真：11 映射／`orbit` 6 dims／`lunar_wastelands→moon` 今日真值（E5） |
| A-a1c（`kindFromPath`） | **今日真會紅**（正面斷言）：`kindFromPath("data/ad_astra/dimension/moon.json")` 今日回 `null`（`:100-127` 7 個 branch 全無 `dimension/`）⇒ plan 寫「實作前跑一次記紅→綠」**準確、可跑** | ✓ 真：`ad_astra` jar 內 `data/ad_astra/dimension/moon.json` 存在（E5） |
| A-a2（`mergeRoutes`） | 唔可跑（唔存在 ⇒ compile-fail） | ✓ 真：`AddLine`／`clipAcquireLines` 手法在（`:97-102`／`:57`） |
| A-a3（人化輸出） | 唔可跑＋**key 未存在**（`packai.reply.worldgen_ore` 今日唔存在） | ✓ 真：F7 三行（E6）；`absolute` 前綴（E7） |
| A-b1／A-b2（`InfoCompleteness`） | 唔可跑（檔唔存在 ⇒ compile-fail，≠ 斷言紅） | ✓ 真：`ReplySources.HEADER` anchoring（`ReplySources.java:11`）在 |
| A-b3（落位存活） | **部分可跑**：scrub pipeline 三個函數今日真存在（`AskReplyScrub:857/:1131/:1079`；現實鏈 `AskService:349`→`AskResult:193`）⇒「現有答案過 pipeline 後卡片 marker 不變」今日可測；但 **gap 區塊存活部分今日唔可跑**（`InfoCompleteness` 未存在） | ✓ 真（E11） |
| A-b4（卡落位回歸） | **今日綠**（我親跑）：`check_ask_card_fallback`／`check_ask_marker_integrity` GREEN；兩 Java 檔 sha256 今日＝`5126f917…`／`1c174fa3…`（改動後要對） | ✓ 真（E12） |
| A7（全回歸） | **今日可跑、已跑**：123 檔／122 綠＋1 紅（`check_ask_display_leak`）；harness 51／tmp-check.gradle 51 tasks | ✓ 真（E3／E4） |
| A8／A9（真機） | 今日唔跑（本輪禁開遊戲；亦未有新 seed cases 檔） | ✓ 執行人真存在：`%TEMP%\packai_autotest_run.py`（226 行）＋ `PACKAI_SANDBOX:16`／`PACKAI_WORLD:26`／`PACKAI_CASES:28`／`--force:171`（E10） |

---

## §4 記錄性殘餘（**唔計反方分**，避免「加新要求」；供實作／後續工單參考）

1. R1 F-LD5 第三項（「答案只有人化名、冇 raw id ⇒ 唔准出 gap line」負控）未落實：token 集仍只收 raw id（plan `:74`），A-b2 只有「唔存在 id ⇒ 行返嚟」負控。今日亦**零** trace 量測支持「模型會講 raw biome id」（R1 E15 已證 20 個 trace 冇任何 biome id）。此為 R1 已提項，非本輪新要求。
2. R1 F-LD7 第四項（A-a4 加「兩值相異」變體）未落實：F7 fixture `count=9` 與 `size=9` 同值（E6），A-a3 期望 `礦脈大小：9｜每區數量：9` 分唔開兩個語意。
3. R1 F-LD1 第三項（「映射**總數＝11 個 biome（附清單）**」斷言）未落實：A-a1b 只寫「映射數由 11 跌」敘述＋抽 2 點，冇 11 項清單斷言（真值 10 可判＋1 歧義＝11，E5 與 plan F6 相符，故只屬「鎖唔實」）。
4. `WorldgenLookupAskTool.java:43` 仍會把 raw `[WORLDGEN]` 行餵模型（anchor L3；`AskEngine:695-696` 同理）。v2 已用 §3 `:95` 寫死理由（唔改工具 schema）＝本輪 item 8 判定範圍內已回應；惟 a-4 嘅「零 raw」斷言只覆蓋 `acquire` 工具輸出，唔覆蓋 `worldgen_lookup` 路徑，屬**範圍自我限定**（非矛盾）。
5. 文字精確度（唔扣分）：`mergeRoutes`／`humanWorldgenRoute` 需唔需 package-private（現 `humanJarRoute:85` 係 `private static`，而 §2 b-1 叫 `AcquireAskTool.humanWorldgenRoute(...)`）——實作時釘死可見性即可；A-a2 負控「把 loose 排前」措辭建議改為「**傳入參數換位**」以免同「改實作」混淆。

---

## §5 最後比分行

**正方 7 : 反方 3**

- **正方 7**＝8 條 flip condition 之中 **7 條真 RESOLVED**（a-2／a-3／a-4／b-1／b-2 落點／驗收表／白名單），每條都有本輪 file:line 親核支持。
- **反方 3**＝① **a-1 NOT-RESOLVED**（`idFromPath:155-165` 需第 5 個改動點；plan `:34` 寫「已核無需改」係前提錯，且同自家 A-a1c `:103` 自相矛盾）；② 由①衍生：plan 對「全部 `Kind` 使用點」嘅**覆蓋清單唔可信**（同一類錯正是 R1 嘅致命 LD1）；③ **b-2 覆蓋聲明不完整**（只列 1 條未覆蓋早退，實測 ≥5 條出口繞過 hook）＋缺 F-LD6 要求嘅「所有 frame kind 到得到」harness。
- 另加認分（已計入正方 7 內、非額外加）：v2 更正過嘅事實（`absolute` 前綴／`ad_astra` 11 檔／359 jars）**全部改對**、白名單紀律**真做**、F3／F5／F6／F7／F9／F13／F14 **逐字準確**（E2–E12）；R1 七條死因之中六條（LD3／LD4／LD5／LD6／LD7／LD8）**已實質修好**。

**門檻（正方 ≥8 且 反方 ≤2 才 pass、go=true）⇒ `7:3` ＝ 唔過，go=false。**

**唯一卡住嘅項（1 條，修正成本＝一行）**：`WorldgenFacts.idFromPath()` 內加 `case DIMENSION -> "dimension/"`（或等價特判），並把 plan `:34`「已核無需改」改寫為「**需加 folder arm**」；順手把 b-2 `:79` 未覆蓋清單補上 `:1029`／`:1038`／`:1096`／`:1107`（＋可選加一個 frame-kind counter harness）。做完呢兩點，本反方**冇其他 blocker**，比分會變 **9:1**。另提醒：呢個缺陷 plan 自家 A-a1c 會跑紅（實作期可捉），所以風險屬「規格錯、可即時修」，唔屬「設計唔通」。

---

## §6 反方立場聲明

本輪**冇**為 plan 翻案；所有「plan 做對」嘅陳述都係 E2–E12 親跑證據支持下嘅事實記錄，唔構成對設計嘅額外支持。R2（有界）結論：**7:3，唔准開工**；最低限度先修 §5 兩點（一行 code 規格 ＋ 一段未覆蓋清單），之後即可按 §4 驗收表落地。
