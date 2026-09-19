# P1 phase plan — 反方 review（R1）｜正方 3 : 反方 7（未達 8:2，唔准開工）

> 受審檔：`docs/plans/2026-09-19-p1-namespace-source-index-phase-plan.md`（58 行，v1）
> 母 plan：`docs/plans/2026-09-19-pack-content-awareness-full-plan.md` §2 P1、§4、§6
> 紀律：只讀唔改；本檔係唯一寫入。每項附 `檔案:行`／命令輸出；未核實者明寫「未核實」。
> 上一輪同樣做法（P2）判 **正方 2 : 反方 8**（`reviews/2026-09-19_p2-plan-R1-opposing.md`）——本輪特別查 P1 有冇同族病（旗艦症狀冇 runtime artifact／引文截斷）。

## ① 驗過嘅事實（真／假／未核實）

| # | 聲稱（plan 行） | 判定 | 證據（我自己跑） |
|---|---|---|---|
| F1 | plan:12 **jar ns 224／kubejs ns 55／overlap 27／kubejs-only 28** | **真（我獨立重跑，數字完全一致）** | 我自己寫 `%TEMP%\p1_review\overlap_ns_mine.py`（唔信 plan 嘅 `overlap_ns.py`）：`jars=231 unreadable=0 / jar ns=224 / kubejs data=19 assets=47 union=55 / overlap=27 / kubejs-only=28`。predicate＝jar 側掃 `mods/*.jar` 內 `^(data\|assets)/<ns>/`；kubejs 側只取 `kubejs/{data,assets}/` **目錄名** |
| F2 | plan:11 量法檔 `%TEMP%\overlap_ns.py` | **真（但 35 行，唔係 36；`mods/` 231 個檔全部係 `.jar`，0 個 disabled ⇒「已載入 jar」成立）** | `ls -la …/Temp/overlap_ns.py`（1264 B）＋`read_file` 逐行核；`ls mods \| wc -l`＝231、`grep -c disabled`＝0 |
| F3 | plan:13 「`PackAiConfig.java:482` 明文 Never scans…」 | **真（引文無截斷）** | `grep -n "Never scans" config/PackAiConfig.java` → `482: "Never scans kubejs/assets or kubejs/data. Default 400. Range 1–10000."`（**係 `mechanicScanMaxFiles` 一個 key 嘅註解**） |
| F4 | plan:13 「⇒ 本包內容**根本冇入索引**」 | **假（過廣）** | 同 repo 已有 3 條路徑讀 `kubejs/data`：`WorldgenIndex.java:33`（`LOOSE_ROOTS` 含 `"kubejs/data"`）、`TetraMaterialItems.java:255`（`scanTree(gameDir.resolve("kubejs").resolve("data"), …)`）、`TetraSchematicLookup.java:73`（`tryLoadFromDiskTree(..., "kubejs", "data")`）。真身只係「冇 **ns→提供者** 索引」 |
| F5 | plan:14 `AskReplyScrub.java:810-812` → `packai.label.src.<token>` | **真** | `sed -n '804,813p'`：`:810 return "packai.label.src.scroll";`／`:812 return "packai.label.src." + u.toLowerCase(...)`（key 查無 → `srcLabel()` `:815-825` 回 null＋log） |
| F6 | plan:19 `JarLightIndex.java:144-145` ＝ jar 側枚舉法，且「jar 清單重用 JarLightIndex 已有 **cache**」 | **前半真、後半假** | `:142-162` 係 `static String fingerprintZip(Path jar)`（`package-visible for tests / python mirror`，`:144 try (ZipFile zf…)`／`:145 Enumeration… entries()`）——係**逐 jar 指紋**，唔係 jar 清單。`grep -n "public "`：全類只有 `ensure(:70)／reset(:98)／isReady(:106)／factsForAsk(:114)`，**冇** jar 清單／ns API |
| F7 | plan:20「照 `KubeJsMechanicScan` 嘅掃描骨架…只讀目錄／檔名」 | **假（骨架正正排除 P1 要掃嘅嘢）** | `KubeJsMechanicScan.java:43-45` `SCRIPT_FOLDERS = {startup_scripts, server_scripts, client_scripts}`；`:454-462 isScriptJs` 尾行 `return !n.contains("/assets/") && !n.contains("/data/");` |
| F8 | plan:49「231 jar ＝ 0.34 秒」 | **真（量級對；我量 0.45 s）** | 我自己重跑：`jar enumerate wall time: 0.45 s`（109,112 條 data/assets entry） |
| F9 | plan:12/15「kubejs 9,144 檔」 | **真** | 我自己數：`kubejs files: 9144  total 352.7 MiB` |
| F10 | plan:15「`golden_age` 1,204 檔」 | **真（但 predicate 未寫；1,204 ＝ assets 1,120 ＋ data 84）** | `kubejs/assets/golden_age`＝1120 檔、`kubejs/data/golden_age`＝84 檔（＋startup 18／server 58／client 4 唔計） |
| F11 | plan:15「亞巴頓…AI 只會當『普通物品』」 | **假（被 2 條真 trace 反駁）** | `trace/ask-20260915-202535-golden_age_infinity_sword.jsonl` 答案有 4 條取得鏈＋器官化效果；`ask-20260916-132352-golden_age_infinity_blade.jsonl` 有「動力合成器（16 槽）：无尽锭×16 → 寰宇之刃」。另外「亞巴頓」**全 pack 0 命中**，真身係簡體 `亚巴顿`（`kubejs/assets/golden_age_tetra/lang/zh_cn.json:1777-1782`，即 `golden_age:archotech_void_scythe` 嘅 Tetra variant 名） |
| F12 | plan:41 A4 負控「`minecraft:` ＝ jar 有、kubejs 冇 → `state=mod`」 | **假（永遠紅）** | `kubejs/assets/minecraft/` **存在**（2 檔：`textures/gui/options_background.png`、`textures/gui/widgets.png`）⇒ 按 plan 自己規則 `minecraft` ∈ kubejs ns ⇒ `state=both` |
| F13 | plan:39 A2「trace `state=pack`」 | **真（ns 層可行）** | 我量 `golden_age in jar_ns: False \| in kubejs: True`；item id `golden_age:archotech_void_scythe`（`kubejs/startup_scripts/golden_age/ink_register.js:97`） |
| F14 | plan:40 A3「`tetra:modular_double` ＝ both」 | **真（ns 層）** | 我量 `tetra in jar_ns: True \| in kubejs: True` |
| F15 | plan:43 A6「≥121 綠」 | **真** | 親跑：`ls tests/check_*.py \| wc -l`＝122；只有 `tests/check_ask_display_leak.py` FAIL（RC=2「NO LOG LINES (need real-machine smoke)」） |
| F16 | plan:44 A7「49 Java 測試全綠」 | **半真／未核實** | `find forge/1.19.2/src/test -name '*.java' \| wc -l`＝**49** ✓；**未跑 gradle** ⇒「全綠」未核實 |
| F17 | A6 擔心撞 `check_ask_display_leak.py`／`check_internal_label_parity.py` | **未成立（對我哋有利）** | `check_ask_display_leak.py:540 if event == "send.facts"`／`:558 elif event == "tool.result"` ⇒ 新 `ns.source` 事件唔會被掃；parity 只解 `AskReplyScrub` 嘅 label 常數（`:1` docstring）而 P1 零 lang key |
| F18 | plan:50 還原法（`%TEMP%\p1_backup_<ts>\`＋md5，唔用 `git checkout`） | **真（方向正確）** | `git ls-files` 4 檔全部 tracked；`git status --porcelain` → `AskEngine.java`＝` M`、`PackAiConfig.java`＝` M`、`JarLightIndex.java` 乾淨 ⇒ 混 tracked/modified，checkout 會吞埋 worktree 其他改動（全樹 120 條 dirty） |
| F19 | plan:51「其他樹（1.20.1／1.21）」 | **假（repo 冇 1.20.1 樹）** | `ls -d forge/*` → 只有 `forge/1.19.2`；`neoforge/1.21.1`。母 plan 亦從未提 1.20.1 |

## ② 問題清單（severity ＋ 證據）

- **O1 CRITICAL — 粒度不成立：核心家族永遠答「both」**（F12/F14＋實測）。我用 `%TEMP%\p1_review\granularity2.py` 抽 `kubejs/startup_scripts/**` 內 `.create('ns:path')`／`new Organ('ns:path')`：**3,308 個 id，其中 422 個（12.8%）落喺 jar 亦有檔嘅 ns（絕大多數係 `kubejs:` 器官），ns 層一律判 `both`**（`kubejs-forge-1902.6.2-build.73.jar` 本身帶 27 條 `assets\|data/kubejs/` entry，`kubejs/data/kubejs` 亦有 265 檔）。同一機制令 **所有 `minecraft:` 物品** 都係 `both`（F12）。即係「pack 最活躍嘅兩個 ns（`kubejs`／`minecraft`）永遠得唔到答案」，同 plan §1 目標（答到「原廠 mod 定本包自加」）直接對唔上；`both` 只係誠實，唔係答案。
- **O2 CRITICAL — 成本護欄同設計自相矛盾（5.4×／6×超標）**（plan:24、A5:42）。我實測（`cost.py`）：231 jar 內 `data/assets` entry **109,112 條**，**distinct `(ns,path)` ＝ 107,337 條**；plan 自己定義 key ＝ `ns + "\u0000" + relPath`（plan:24）⇒ key bytes **5.59 MiB**、JSON array **6.51 MiB**。plan 上限係 **20,000 路徑／1 MiB**。單係 `create`(8,749)＋`lightmanscurrency`(7,855)＋`extradelight`(6,699) 三 ns 已 23,303 條 > 20,000 ⇒ 唔止超標，**截斷方向係「jar 側先爆」**：被截走嘅 jar-ns 會誤判成 `pack` 或 `unknown`（＝把原廠 mod 內容講成本包自加，正正係要修嘅病嘅反面）。A5 照字面跑必然紅。
- **O3 CRITICAL — 驗收嘅「答案」半邊冇 mechanism（同 P2 同族）**（plan:23 vs A2:39/A3:40）。plan §3（:23）定明曝露面只有 trace 一條 `ns.source` ＋ `AskResult` 一個欄位，「**唔加 lang key**」；§4 白名單亦冇任何 prompt／facts 注入點。但 A2 要求「答案唔准當佢係原廠 mod 內容」、A3 要求「答案要保留『未確定／兩邊都有』語意」。我掃 39 條 trace 嘅 `display.body.final`：含 mod-vs-pack 歸屬措辭（原厂／原廠／模组自带／本体…）＝ **0 條** ⇒ A2 係**假綠**（今日已經「唔會講錯」，修法前後同一結果，零鑑別力）；A3 係**今日紅且 P1 令唔到佢綠**——`ask-20260918-182231-tetra_modular_double.jsonl` 真 body 逐字：「这是 Tetra 的模块化双头工具…怎么来:合成台（有序合成）：橡木木板 + 木棍 -> modular double」，全文冇「未確定／兩邊都有」。
- **O4 HIGH — 負控 A4 冇綠狀態**（見 F12）。`kubejs/assets/minecraft/` 兩張 GUI 貼圖就令 `minecraft` ＝ `both`；A4 寫「`state=mod`」係**永遠紅**，而「改壞三態邏輯要紅」嘅負控設計（plan:56）亦因此失去鑑別力。
- **O5 HIGH — 「重用」兩處都係假重用**（plan:19-20）。① `JarLightIndex` 冇 jar 清單 API（F6），而且 `ensure()` `:71-73 if (!PackAiConfig.scanModJars()) return;`，`scanModJars` 預設 **false**（`PackAiConfig.java:442 .define("scanModJars", false)`）⇒ 照 plan 字面重用，jar 側**0 個 jar**；母 plan §4 明文要求 `contentIndex.enabled`「與 `scanModJars` **完全解耦**」，phase plan 冇承接呢條。② mechanic scan 骨架 `SCRIPT_FOLDERS` 只 3 個 scripts 目錄、`isScriptJs` 明文排除 `/assets/`、`/data/`（F7）＝正正係 P1 要行嘅樹；`ensureStart(gameDir, maxFiles, maxBytes, maxMs)`（`:238/:241`）連 cap 語意都係「**檔數／bytes**」（`buildIndex:341`），同 plan 嘅「**路徑**數」唔同一個度量。
- **O6 MED — A6/A7 混用門檻，A7 未核實**（plan:43-44＋F15/F16）。A6「≥121 綠」對（122 檔、1 紅）；A7「49 Java 測試全綠」只核到數目，未跑 gradle；§7 step 3 又寫「122＋新閘」（同 A7 疊加但未講新閘係第 123 條）。
- **O7 MED — 還原點路徑同母 plan 唔一致 + 跨版本描述失實**（plan:50-51＋F18/F19）。phase plan 用 `%TEMP%\p1_backup_<ts>\`，母 plan §6 明寫 `.hermes/backups/2026-09-19_<phase>/`；`%TEMP%` 唔屬 repo、易被清，兩份文件唔同步。另 repo 只有 `forge/1.19.2`＋`neoforge/1.21.1`，plan:51 提「其他樹（1.20.1…）」＝幽靈樹。
- **O8 MED — 引文／命名衛生（同 P2 同族，未改）**。plan:15 用繁「亞巴頓」，artifact 係簡「亚巴顿」（4 命中，1 個檔）；plan:11 寫 `overlap_ns.py`「36 行」真身 35 行；plan:15「1,204 檔」冇寫 predicate（＝assets+data，唔含 scripts）。呢類係下一輪 reviewer 一 grep 就打嘅 claim。
- **O9 LOW — `AskResult` 加欄位未評估 record 影響**（plan:23）。`AskResult.java:6-14` 係 **7 component record**（`answer/quests/suggestedItemIds/recipeCards/tokenUsage/cardStrip/displaySrc`），`new AskResult(` 有 **7 個呼叫點**、已有 2 個 backward-compat overload（`:25/:36`）。加 component 會動 `equals`／要再補 overload；plan 冇寫（同 skill 嘅 RecipeCard 18 欄教訓同族）。
- **O10 LOW — 「部分重複造輪」未申報**。`PackIndex.java:605 namespaceOf(String itemId)` 已抽 ns；`WorldgenIndex`／`TetraMaterialItems`／`TetraSchematicLookup` 已讀 `kubejs/data`（F4）⇒ plan 應明寫「我唔重做邊啲、我加嘅係 ns→提供者三態」，否則容易同現有路徑語意打架。

## ③ 載重決定存活表 ＋ 比分 ＋ flip conditions

| LD | 載重決定 | 判定 |
|---|---|---|
| LD1 | 問題陳述（capability gap 真存在） | **有保留**：ns→提供者索引確實冇（真 gap），但「本包內容根本冇入索引」過廣（F4）、「AI 只會當普通物品」被 2 條真 trace 反駁（F11）；**plan 全文冇一條 runtime artifact 顯示玩家實際見到錯答案**（P2 同族風險） |
| LD2 | 量度（224/55/27/28） | **存活**（F1／F2：獨立重跑完全一致、predicate 可複述） |
| LD3 | ns 層三態 → 判「某件物品係邊個提供」 | **死**（O1：428 個 KubeJS-created id 落非 pack-only ns，422 個係 `kubejs:`；`minecraft:` 全族 both） |
| LD4 | 驗收 A2／A3 嘅「答案」半邊 | **死**（O3：零注入機制；A2 假綠、A3 今日紅且無法綠） |
| LD5 | 驗收 A4 負控 | **死**（O4／F12：`kubejs/assets/minecraft/` 令 `minecraft`＝both） |
| LD6 | 成本護欄（20k 路徑／1 MiB／2 s） | **死**（O2：實測 107,337 條／5.59 MiB keys／6.51 MiB JSON；截斷方向會製造假 `pack`） |
| LD7 | 重用 JarLightIndex／KubeJsMechanicScan | **死**（O5：冇 jar 清單 API、`scanModJars` 預設 false、骨架排除 data/assets） |
| LD8 | 還原／範圍（只做 1.19.2、唔郁 neoforge） | **有保留**（F18 還原方向正確；但路徑同母 plan 唔一致、1.20.1 幽靈樹） |

**比分：正方 3 : 反方 7**（LD2 存活、LD1／LD8 有保留；LD3–LD7 五條死，全部有親跑數字或 `file:line`）⇒ 遠低於 8:2，**唔准開工**。對比 P2 嘅 2:8：P1 贏在**四個數字係真**（唔似 P2 旗艦症狀係假），輸在**設計同驗收同真實資料規模／粒度對唔上**。

**Flip conditions（要咩證據才能反轉；＝下一版 acceptance list）**
- FC1（對 O1）：寫死 **item 級**判定：`NsSource.forItem` 要指明用邊個 predicate 配對到 `assets/<ns>/{models/item,textures/item,lang}/**` 或 `data/<ns>/**` 嘅**具體路徑**（今日真身係 `kubejs/assets/kubejs/textures/item/organs/food/chicken_heart.png`——**多一層 `organs/food/` infix**，單純 `assets/<ns>/textures/item/<path>.png` 猜唔到），並交一個 `kubejs:` ns 嘅真例證明可判 `pack`。或者明文撤回「三態答得到邊個提供」，改寫目標為「ns 層統計」。
- FC2（對 O2）：把護欄同真實數字對齊：實測 **109,112 entry／107,337 distinct path／5.59 MiB key／6.51 MiB JSON**（命令可重跑）——要麼改成只存 `ns → (count, jarNames)` 聚合（並寫死 item 級 refine 唔靠全路徑表），要麼把上限提到 ≥110,000 路徑／≥8 MiB，並寫明**截斷策略唔准令 jar-ns 誤判 `pack`**。
- FC3（對 O3）：指名 P1 用邊個變數／事件令「答案」用得到 ns state（facts 注入點／prompt 欄位／或明寫「P1 唔改答案，A2/A3 答案半邊降級為 P4 驗收」）；並交出「修法前紅 → 修法後綠」嘅 trace 對照，否則 A2 要換 case。
- FC4（對 O4）：換一件真 jar-only、kubejs 零檔嘅 ns 做負控（`minecraft` 已被 `kubejs/assets/minecraft/` 2 檔污染），並附「今日跑 A4 會紅」嘅證據。
- FC5（對 O5）：寫死 jar 清單來源（新枚舉 or `JarLightIndex` 公開 accessor）＋明寫同 `scanModJars` 解耦；並指明 mechanic 骨架要改邊幾個 predicate（`SCRIPT_FOLDERS`／`isScriptJs`）／證明唔會同 mechanic index 共用 cap。
- FC6（對 O6–O10）：A7 附真 gradle 輸出；還原路徑同母 plan §6 二選一；刪「1.20.1」；`AskResult` 加欄位附 overload／equals 影響；用簡體 artifact 原文取代自造繁中名。
- ⛔ 唔准「換 prompt 再擲骰」當新一輪；每輪必須有實質修改（新數據／改設計／縮範圍）。

## ④ 最貴嘅未知

1. **P1 究竟要 ns 層定 item 層答案？** 呢個係唯一載重未知：item 層要保留全路徑（107k 條）＋寫死 refine predicate（今日 428/3,308 個 KubeJS-created id 落 `both`）；ns 層做得成但答唔到「邊件嘢係邊個提供」。解法成本低：拿 `kubejs:chicken_heart`／`minecraft:stone` 各跑一次 refine，即刻知要唔要改結構。
2. **SK 實際見到咩症狀？** plan 全文零 runtime artifact；39 條 trace 冇一條有 mod-vs-pack 措辭（0 條），亦冇一條顯示 AI 講錯提供者。P2 就係死在「旗艦症狀冇 artifact」——解開方法＝SK 提供佢實際嗰次問句／物品，或跑一批 `kubejs:` 器官／`golden_age:` DLC 新 case，成本遠低於再開一輪 review。
3. **P4 前答案鏈點接？** 現有 11 個 `packai.label.src.*` 全部係**資料源**標籤，冇 provider 標籤；母 plan P4 又寫「零新 lang key」。如果唔注入 facts、又唔加 lang key，玩家永遠睇唔到 P1 嘅成果——即 P1 完成後可能係「加咗一個冇人讀嘅索引」。
