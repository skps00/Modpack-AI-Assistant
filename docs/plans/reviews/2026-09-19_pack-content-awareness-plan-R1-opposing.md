# 反方 review R1 — packai「Pack 內容感知 + 來源誠實」全計劃 v1

> 被 review：`docs/plans/2026-09-19-pack-content-awareness-full-plan.md`（v1，md5 頭 12 ＝ `96e4a0906b53`，實測 `md5sum` 相符）
> 立場：**反方（opposing）**。目標係盡最大努力指出問題，唔幫佢講好話。
> 方法：只讀。逐條開真檔案核（檔名＋行號＋原文），加 python stdlib 量測。**冇跑 gradle／build／check_*.py**（見 §5 方法限制）。
> 日期：2026-09-19。

---

## 0. 開場結論（一句話）

計劃**方向正確**（pack 自加內容真係答唔到、`kubejs/data` 真係冇讀），但 v1 有三個「一開工就會硬撞」的問題：

1. **Phase 3 同 `tests/check_internal_label_parity.py` 硬衝突** —— 該閘要求 **forge＋neoforge 兩棵樹 token 一致**、而且 label key 要喺 **6 個 lang 檔**（唔係 plan 寫的 3 個）都有；plan §3 第 6 條明文「唔碰 neoforge」→ §5 的「零新增紅」**必破**。呢個係 plan 自己兩條條款互相打架。
2. **Phase 5 的「單一來源」係一個被截斷的來源** —— `JarLightIndex` 每個 jar 只收頭 **200 條 recipe／150 條 loot**，實測 **7,595 條 recipe 被丟**（17 個 jar 超標，`create` 一個 jar 有 2,037 條只收 200）。用一個 ~49% 覆蓋的索引去「對齊」現時靠 JEI 正確的卡路徑，係**回歸風險**，唔係修正。
3. **範圍／部署時機** —— 5 個 phase 綁一個 plan ＋ Phase 0 同 Phase 1 一齊部署，違反 repo `AGENTS.md`「複雜交付物拆開評（小修復同新功能唔好綁同一個 plan）」，而工作樹實際有 **67 個 modified 檔（+4,448/−1,287）未 commit**——plan §6 講嘅「10 檔白名單 backup」覆蓋唔到真正會部署出去的 diff。

---

## 1. 逐條核實計劃 §1 引嘅事實

判定分三級：**【真】**＝原文相符；**【半真／措辭誇大】**＝部分成立但結論推過頭；**【假／自相矛盾】**＝同真檔案相反或同 plan 自己另一段相反。

| # | plan §1 聲稱 | 核實結果 | 證據（檔名:行：原文） |
|---|---|---|---|
| F1 | `PackAiConfig` 明文「Never scans kubejs/assets or kubejs/data」 | **【半真】** 字面真，但係 **`mechanicScanMaxFiles` 呢個上限的註解**，唔係一條「禁止」條款；plan 用「明文」＋列為「缺口」暗示係設計禁令，措辭誇大 | `config/PackAiConfig.java:482-484`：`"Never scans kubejs/assets or kubejs/data. Default 400. Range 1–10000."` 緊接 `.defineInRange("mechanicScanMaxFiles", 400, 1, 10000);` |
| F2 | KubeJS 內容「**只掃** `kubejs/{startup,server,client}_scripts`」 | **【假】** `TetraMaterialItems` 早就掃 `kubejs/data`；所以「kubejs/data 從未被讀」係錯的——準確講係「**只有 Tetra 一個 channel 讀 kubejs/data**」 | `logic/TetraMaterialItems.java:255`：`scanTree(gameDir.resolve("kubejs").resolve("data"), map, reverse);` |
| F3 | 上限 400 檔／8 MiB／8s | **【真】** | `PackAiConfig.java:484`（400）、`:488`（`8_388_608`）、`:492`（`8000`） |
| F4 | `JarLightIndex` 已掃 jar zip 目錄（R/U/L facts → `config/packai/jar-cache/`） | **【真】** | `logic/JarLightIndex.java:46`（Fact codes R/U/L）、`:81`（`config/packai/jar-cache`）、`:164-175`（isRecipeEntry/isLootEntry） |
| F5 | `JarLightIndex` **只讀** recipe/loot facts | **【真】**（但漏講上限，見 O3） | `JarLightIndex.java:364-366`：`boolean recipe = isRecipeEntry(pl); boolean loot = isLootEntry(pl); if (!recipe && !loot) continue;` |
| F6 | `ModScanners` 偵測 kubejs／crafttweaker／groovyscript／ftbquests | **【真】**（另有 `heracles`、`datapacks`） | `logic/ModScanners.java:24-40` |
| F7 | Java 樹 `grep "crafting_shaped"` ＝ 0 | **【真】**（`.java` 樹實測 0 hit） | `grep -rn "crafting_shaped" --include=*.java .` → `0` |
| F8 | 由 F7 推「Java 樹**完全冇 parse 配方 JSON**，配方一律經 JEI」 | **【假／自相矛盾】** Java 樹**有** parse 配方 JSON：`JarLightIndex.parseRecipeJson()` 讀 jar 內 `data/*/recipes/*.json`。F7＝0 只因為佢唔靠 `type` 字面 match（走泛型 `type` 欄位）。而且**同 plan §1 自己第二行**（「已掃 jar … R/U/L facts」）直接矛盾 | `JarLightIndex.java:189-228`（`parseRecipeJson`，`:203-206` 讀 `o.get("type")`）；`:351-392` `scanJarFile` 逐 entry parse。實測 jar 內 recipe JSON **12,799 條** |
| F9 | Tetra 掃 materials／schematics | **【真】** | `TetraMaterialItems.java:270-271`：`scanTree(..., "/tetra/materials/", true); scanTree(..., "/tetra/schematics/", false);` |
| F10 | `modules`／`improvements`／`repairs`／`synergies` **完全冇讀** | **【半真／誇大】** `modules`／`repairs`／`synergies` 目錄確實冇讀（grep 冇任何 `"/modules/"`／`"/repairs/"`／`"/synergies/"` 字面）；但 **`improvements` 有讀**（只限 schematic `outcomes[]` 內嵌者） | 有讀：`TetraMaterialItems.java:431-442`（`firstImprovementKey`，`:432` `outcome.has("improvements")`）；`TetraSchematicText.java:245`（`formatImprovements(o.get("improvements"))`）。冇讀：全樹 grep `"/improvements/"`＝0 |
| F11 | `AskResult.displaySrc`（`DISPLAY_SRC_UNKNOWN`）＋`packai.label.src.*`（例 scroll／tetra_use） | **【真】** | `logic/AskResult.java:13,15,123`；`lang/zh_tw.json:488-498`（11 個 `label.src.*`） |
| F12 | 冇「kubejs（pack 自加）」／「jar（原廠）」之分 | **【真】** 現有 label 集合無此二值 | `lang/zh_tw.json:488-498` 全列；`AskReplyScrub.java:27-39` token 集 |
| F13 | `HonestMiss`＋`unknown_items.jsonl`（玩家零動作）；`KnowledgeRemote`（KB-2，預設 url 已設） | **【真】**（但 `knowledgeRemote` 本身預設 **false**） | `logic/HonestMiss.java:1-60`；`PackAiConfig.java:504`：`.define("knowledgeRemote", false);`；`:513-514` `knowledgeUrl` 有預設值 |
| F14 | 亞巴頓註冊喺 `kubejs/startup_scripts/golden_age/dlc_template_item_register.js` | **【假】** 真檔係 `.../golden_age/**ink_register.js:97**`。被引的 `dlc_template_item_register.js` **唔含** `archotech_void_scythe`（研究檔 line 117 亦抄咗同一個錯） | `grep -rln archotech_void_scythe` 命中 `startup_scripts/golden_age/ink_register.js`，**冇** `dlc_template_item_register.js`；該行：`ink_register.js:97: event.create('golden_age:archotech_void_scythe').group("kubejs.golden_age")…` |
| F15 | 32 個 kubejs-only 命名空間 | **【半真／數目對唔上】** 我複核得 **28** | 方法＝(jar 內任一 entry 首段 ∈ {data,assets}) 的 ns 集合 vs `kubejs/{data,assets}` 目錄集合。結果：jar ns 224、kubejs ns 55、**kubejs-only 28**、重疊 27。28 ≠ 32（亦試過只計 jar `data/`＝29，仍 ≠ 32） |
| F16 | `data/tetra` 2,420 檔；231 個 jar；kubejs 內容全在此兩處 | **【真】** | 實測 `find kubejs/data/tetra -type f`＝**2,420**；`ls mods/*.jar`＝**231** |
| F17 | 實錘證據：`kubejs/assets/golden_age_tetra/lang/zh_cn.json`、`client_scripts/golden_age/item_tooltips_1.js`、`data/tetra/{modules,improvements,repairs,schematics,synergies}` 各路徑 | **【真】** 全部存在（但 F14 的 register 檔除外） | `grep -rln archotech_void_scythe kubejs` 命中 `assets/golden_age_tetra/lang/zh_cn.json`、`client_scripts/golden_age/item_tooltips_1.js`、`data/tetra/modules/single/archotech_void_scythe.json`、`data/tetra/improvements/archotech_void/{epitaph,sonic,void_judgement}.json`、`data/tetra/repairs/single/archotech_scythe.json`、`data/tetra/schematics/single/archotech_void_scythe.json`、`data/tetra/synergies/single/archotech_void_scythe.json` |
| F18 | §4 config key `contentIndex.*` vs §2 Phase 1 `providerIndex.*` | **【自相矛盾】** 同一組上限有兩個唔同 key 名 | plan §2 第 3 點：`providerIndex.maxJars＝300、providerIndex.maxEntries＝20000…`；plan §4 表：`contentIndex.maxJars / maxEntries / maxMb / timeBudgetMs` |
| F19 | §5 baseline「121 檔＝120 綠＋1 資料不足」 | **【未能複核】** `tests/check_*.py` 實測 **121** 個檔（數目相符）；但**我冇跑**（見 §5 方法限制），120/1 綠紅分佈**未經獨立驗證** | `ls tests/check_*.py \| wc -l` → `121` |
| F20 | §2 Phase 0「10 檔白名單＋`RecipeCard.java`」「未 deploy、未 commit」 | **【半真／嚴重低估】** 「未 commit」真（HEAD＝plan commit `3572a82`）；但工作樹 **67 個 tracked modified ＋ 2 個 deleted＋125 untracked，+4,448/−1,287**，遠超「10 檔」 | `git status --short`（67 行）、`git diff --stat` 尾行：`67 files changed, 4448 insertions(+), 1287 deletions(-)`；`D …PackAiSettingsScreen.java`、`D …RecipeCategoryScreen.java` |

**小結**：20 條中 **【真】10、【半真／誇大】5、【假／自相矛盾】4**、未能複核 1。核心方向（kubejs 內容答唔到）成立；但 **4 條硬錯**（F2、F8、F14、F18）＋ **F20 的規模低估**會直接影響 phase 設計同驗收。

---

## 2. 攻擊點（逐條：severity ＋ 攻擊 ＋ 證據 ＋ flip condition）

### O1 【CRITICAL】Phase 3 標籤落地 必破 `tests/check_internal_label_parity.py`；同 §3「唔碰 neoforge」死鎖

**攻擊**：plan §5 定死「`tests/check_*.py` 相對 baseline **零新增紅**」，但 §3 第 6 條又定死「**唔碰** `neoforge/1.21.1`」。而 Phase 3 要「新增 label 值（`pack`, `mod`, `packJar`, `runtime`, `docs`）」（§2 Phase 3），呢批值要經 `srcLabelKey()` 出 label，就必須擴 `INTERNAL_SECTION_TOKENS`／`EXTRA_BARE_SECTION_TOKENS`（或改 `srcLabelKey` 語義）。呢兩樣嘢被 `check_internal_label_parity.py` 用三重鎖夾住：

1. **雙樹 token 必須逐字一致**；
2. label key 必須喺 **6 個 lang 檔**（forge 3 ＋ neoforge 3）都存在；
3. 反之 neoforge 冇跟改 → 直接 FAIL。

所以「只改 forge ＋ 3 檔 lang」**數學上過唔到**。plan §4 寫「Settings：…（**3 檔 lang 同步**）」、§3 第 4 條寫「新增玩家文字一律入 **3 個 lang 檔**」——同閘要求的 **6 檔**唔一致。

**證據**：
- `tests/check_internal_label_parity.py:26-30`：`LANG_TREES = (forge…lang, neoforge…lang)`；`LANG_FILES = ("en_us.json","zh_cn.json","zh_tw.json")` → 6 檔。
- 同檔 `:99-106`：`if len(token_sets) == 2 and token_sets[0] != token_sets[1]: failed.append("INTERNAL_SECTION_TOKENS differ…")`（`EXTRA_BARE_SECTION_TOKENS`、`INTERNAL_ROLE_VALUES`、`SCROLL_SECTION_REGEX` 同理）。
- 同檔 `:129-135`：逐 key 檢查 6 檔必須有 `packai.label.src.<token>`。
- `AskReplyScrub.java:27-39`（forge token 集）、`:804-812`（`srcLabelKey`）；`neoforge/1.21.1/.../AskReplyScrub.java` token 集**逐字相同**（實測）；兩樹 `zh_tw.json` `label.src` 各 **11** key。
- 該閘**冇 pause 逃生門**（`check_dual_tree_sync.py:10-14` 有，此檔冇）。

**Flip condition（撤回條件）**：
(a) show 到一個**唔改** `INTERNAL_SECTION_TOKENS`／`EXTRA_BARE_SECTION_TOKENS` 又出得到新 label 的機制（例如新 label 走獨立欄位而非 token，且 `isKnownSrcLabel`／`collapseLabelDuplication` 唔會當佢未知）；**或**
(b) plan 明寫會 update `neoforge` 的 3 個 lang 檔（並解釋點解唔算違反 pause），並證明「雙樹 token 一致」仍成立（＝neoforge 的 `AskReplyScrub.java` 都要改，直接撞 §3 第 6 條）；**或**
(c) 明確為該閘加 allowlist／pause 豁免，並把 baseline 由「零新增紅」改為「零新增紅（除 check_internal_label_parity）」，即唔再當佢係硬閘。

---

### O2 【HIGH】JarLightIndex 的 per-jar 上限令 Phase 5 的「單一來源」只有 ~49% 覆蓋 → 對齊會 regress 現時正確的卡路徑

**攻擊**：Phase 5 講「以 Phase 1 provider index ＋ **現有 JEI 結果**做單一來源，`AcquireAskTool`／`AskGrounding` 文字路徑同 `RecipeCard` 卡路徑對齊（**同一結論、同一 id 集合**）」。但 `JarLightIndex` 有硬上限，實測丟掉大量 recipe：

- `MAX_RECIPES_PER_JAR = 200`、`MAX_LOOT_PER_JAR = 150`、`MAX_FACTS_PER_ITEM = 8`。
- 實測：**17 個 jar 超標**；**7,595 條 recipe 被丟**（jar 內 recipe 總數 12,799）；loot 亦丟 1,573。最極端 `create-1.19.2-0.5.1.i.jar`：2,037 條 recipe **只收 200**（丟 1,837）。
- 總覆蓋：12,799＋5,047＝17,846 條 entry，實際只保留 8,678（**≈ 48.6%**）。

若 Phase 5 真的「以 jar facts 為單一來源」去覆寫／對齊現在靠 JEI 得出的卡結論，就會出現：**JEI 有卡、jar index 因被截斷而冇 → 文字路徑反過來否認卡**（即製造新的卡／文字不一致，正是 Phase 5 想修的病）。plan §1 完全冇提過呢批上限。

**證據**：
- `JarLightIndex.java:55-60`（三個上限常數）、`:369-374`（超標即 `continue`，靜默丟）。
- 我的量測（python stdlib，逐 zip `namelist()` 過濾 `data/*/recipes/*.json` 及 `data/*/loot_tables/*.json`）：recipe 12,799、loot 5,047；超標 jar 17 個；recipe 丟 7,595。
- `JarLightIndex.java:522-534` `extractIngredients` 只收 `ingredient/ingredients/key/input/inputs`。

**Flip condition**：
(a) plan 提高／移除上限（或改為按 item 而非按 jar 做 fairness 截斷）並量測新覆蓋率 ≥ ~95%；**或**
(b) Phase 5 明文「jar index **只加不減**」——只補文字路徑缺的，**絕不**用 jar 覆寫 JEI 已有結論；**或**
(c) 提供 2–3 個具體超標 jar（如 `create`、`extradelight`）的真機對照，證明對齊後卡／文字仍一致。

---

### O3 【HIGH】Phase 5 的病灶有 4 行代碼的解法，但 plan 揀咗最貴的機械

**攻擊**：plan §1 斷言「`minecraft:smithing`／mod 自訂 type **只能靠 JEI 類別**」。實測**唔成立**：jar 內有 **96 條 smithing recipe**，全部欄位係 `['addition','base','result','type']`。而 `JarLightIndex.extractIngredients()` 只睇 `ingredient/ingredients/key/input/inputs`——所以佢**答得出 result、答唔出材料**（`R|smithing|` 後面空）。這正是 SB 背包「文字 miss」的**根因**，而且修法係喺 `extractIngredients` 加收 `base`／`addition`／`template`（**數行**），唔需要 Phase 5 嗰套 card／text 對齊基建 ＋ trace 欄位 ＋ provider index。plan 冇評估呢條更短路徑，違反 repo `AGENTS.md`「偏好簡單、穩健、慣用嘅方案」。

**證據**：
- 量測：jar 內 smithing recipe 96 條，逐條 keys ＝ `['addition','base','result','type']`（例：`FarmersDelight-1.19.2-1.2.4.jar → data/farmersdelight/recipes/netherite_knife_smithing.json`）。
- `JarLightIndex.java:522-534`（`extractIngredients` 收的欄位清單）。
- `JarLightIndex.java:207-210`（result 讀 `result`／`output`）。

**Flip condition**：
(a) 證明「文字路徑」唔係經 `JarLightIndex`（例如默認 `scanModJars=false` 令佢根本唔行），故 4 行改動無效；**或**
(b) 真機證明加 `base/addition` 後 SB 條文字答案仍 miss（例如被上游 gate 擋）。

---

### O4 【HIGH】計劃靠 `JarLightIndex`，但佢默認關閉；plan 由頭到尾冇提

**攻擊**：`scanModJars` **默認 false**，而 `JarLightIndex.ensure()` 第一句就係 `if (!PackAiConfig.scanModJars()) return;`——即係話 **默認情況下 jar 完全冇掃、`jar-cache` 可能係舊的**。但 Phase 1 講「重用現有基建」、Phase 5 講「現有 JEI 結果做單一來源」，兩者都建基於 jar facts 存在。同時 plan §4 為**新** provider index 定 `contentIndex.enabled` **默認 true**——即係話新功能默認開、而它所依賴的舊基建默認關。呢個「默認值互相打架」＋「唔講邊個觸發掃描」係設計漏洞（我實測 instance 內 `jar-cache` 有 1.5 MB，證明**開過**，但唔代表玩家默認體驗）。

**證據**：
- `PackAiConfig.java:442`：`.define("scanModJars", false);`；`:910` `public static boolean scanModJars()`。
- `JarLightIndex.java:69-73`（`if (!PackAiConfig.scanModJars()) return;`）。
- plan §4 表：`contentIndex.enabled | true`；plan §2 Phase 1 冇寫觸發時機（first Ask？背景？）。
- 實測 `<instance>/config/packai/jar-cache` ＝ **1.5 MB**。

**Flip condition**：
(a) plan 明寫 provider index 的觸發點（同 `JarLightIndex` 一致：first Ask），並明寫佢**唔依賴** `scanModJars`（完全獨立讀 zip）；**或**
(b) 把 `scanModJars` 一齊默認開（連同成本論證）。

---

### O5 【HIGH】Phase 0 規模嚴重低估：工作樹 67 檔未 commit，「10 檔 backup」覆蓋唔到真正 diff；Phase 0＋1 合併部署＝一次部署未知數量的改動

**攻擊**：plan §2 Phase 0 講「內容：… ＝白名單 10 檔＋`RecipeCard.java`」「未 deploy、未 commit」，§6 講「每 phase 開工前：`.hermes/backups/2026-09-19_<phase>/`（逐檔 timestamped copy）」「回滾：code 由 backup dir copy 返」。

但實測工作樹相對於 HEAD（＝plan commit `3572a82`）係：

- **67 個 tracked 檔 modified**、**2 個 deleted**（`PackAiSettingsScreen.java`、`RecipeCategoryScreen.java`）、125 untracked；
- **+4,448／−1,287 行**。

即係話：真正會 `.jar` 出去嘅唔止「10 檔 fix A」，而係**成個未 commit 的 67 檔集合**。plan §6 的還原方案只覆蓋白名單 → **還原唔到一個已驗收過嘅狀態**（因為根本冇一個 commit 記錄「驗收過嘅狀態」係咩）。加上 §6 主張「Phase 0＋1 一併部署」，一次部署就會把 fix A ＋ 57 個其他未 commit 改動 ＋ Phase 1 新功能一齊推上真機。

**證據**：
- `git log --oneline -1` → `3572a82 docs(plan): v1 full plan …`（2026-09-19 10:19）。
- `git status --short` → 67 行（含 `D` 兩個 GUI screen、`M` 大量 logic／lang／`PackAiMod.java`／`gradle.properties`）。
- `git diff --stat` 尾行：`67 files changed, 4448 insertions(+), 1287 deletions(-)`。

**Flip condition**：
(a) SK 先把工作樹 commit（或至少 commit 一個「Phase 0 驗收態」）令還原點有 SHA 可指；**或**
(b) plan 明列**全部 67 檔**為部署範圍並為每一檔提供還原依據（唔止 10 檔）；**或**
(c) 改為 Phase 1 **獨立**部署（唔同 fix A 綁），令 fix A 的回歸可歸因。

---

### O6 【HIGH】S1–S6 幾乎全部綁「SK 開 game 一次過驗」——單一真機 session 要同時驗 4 個重疊改動，fail 就不可歸因

**攻擊**：§5 寫「真機（SK 開 game 時**一次過驗**）：S5（fix A）＋ S2（亞巴頓）＋ S6（SB 背包）＋ S1（木錘來源標籤）」。呢 4 項分屬 Phase 0／1／2／5 四個 phase，而 §6 主張 Phase 0＋1 一齊部署、其後每 phase 獨立。結果係：**唯一的人類驗收機會被綁喺一個 session**，而該 session 同時承載 fix A（已驗收，但有回歸風險）、provider index（全新）、Tetra 六層（全新）、來源標籤（全新）。任何一項 fail，**歸因唔到係邊個改動造成**，而 rollback 係「整個 jar 換返」→ 一齊丟掉全部。

另外 S1 的驗收本身有內部矛盾：「對 `golden_age:archotech_void_scythe` 回『**kubejs（本包自加）**』」（§2 Phase 1 驗收）vs Phase 1 第 4 點「註冊來源…抽唔到就標 `runtime-registered`」——而 `archotech_void_scythe` **正正係 runtime 註冊**（`ink_register.js:97 event.create('golden_age:archotech_void_scythe')`）。若 regex 抽唔到（見 O8），呢個案例會落 `runtime-registered`，即 S1 的期望值（「kubejs（本包自加）」）同機制輸出（`runtime`）**唔一致**。plan 冇定義兩者關係。

**證據**：
- plan §5（真機一次過驗 4 項）、§2 Phase 1 驗收（S1 期望值）、§2 Phase 1 第 4 點（fallback `runtime-registered`）。
- `kubejs/startup_scripts/golden_age/ink_register.js:97`（實錘：runtime 註冊）。

**Flip condition**：
(a) plan 為每個真機項提供**獨立、可重跑**的驗收步驟（唔綁一次 session），並明寫 fail 時的歸因方法；**或**
(b) plan 定義 `runtime-registered` 係「kubejs 子類」而 S1 期望值改為接受兩者之一；**或**
(c) 先單獨部署 Phase 0 驗 S5，過咗才上 Phase 1。

---

### O7 【HIGH】範圍過大：5 phase 綁一個 plan，直接違反 repo 契約

**攻擊**：repo `AGENTS.md`（專案契約）明文：「複雜交付物**拆開評**（**小修復同新功能唔好綁同一個 plan**）」。呢個 plan 同時載：① 一個已驗收的 bug fix（Phase 0）；② 全新 provider index；③ Tetra 六層內容讀取；④ 來源標籤（會碰 scrub／lang／雙樹閘）；⑤ fallback 階梯；⑥ 卡／文字一致性。起碼 ③④⑤⑥ 係**獨立可交付物**，而且 ④ 已知會撞雙樹閘（O1）。另外 plan §7 自己引「門檻 8:2；每輪必須有實質修改」——範圍咁大，任何一輪反方只要攻一個 phase 就足以令比分上唔到 8:2，變成**用範圍換輪數**。

**證據**：`AGENTS.md`（repo 根）開發流程 §2「複雜交付物拆開評（小修復同新功能唔好綁同一個 plan）」；plan §2（Phase 0–5）、§7。

**Flip condition**：
(a) 拆成 ≥2 個 plan（例：P-A＝Phase 0＋1＋3；P-B＝Phase 2＋4＋5），各自獨立 review 到 8:2；**或**
(b) 論證 6 個 phase 有**不可分割的技術依賴**（例如 Phase 5 數學上必須 Phase 1 先有）並逐條列出。

---

### O8 【MED-HIGH】Phase 2 冇硬上限——違反 plan 自己 §3 第 2 條；而 kubejs 樹係 374 MB／9,144 檔

**攻擊**：§3 第 2 條寫「寫入一律設定 gate＋硬上限＋key 去重；**唔准背景全包無上限掃描**」、第 7 條寫「掃描全部 async＋可中斷＋有時間／記憶體預算」。但 Phase 2（加讀 `kubejs/data/tetra/**` **同** `data/tetra/**` 的 modules／improvements／repairs／synergies，＋ `kubejs/assets/*/lang/*.json`，＋ `kubejs/client_scripts/**` tooltip）**完全冇定任何檔案數／位元組／毫秒上限**。§4 的新 config 表亦只覆蓋 `contentIndex.*`（Phase 1）＋ `packContent.lang`（一個 bool，唔係上限）＋ `provenance.enabled`＋`docsLookup.network`。

對照量測：`kubejs` 樹＝ **9,144 檔／374 MB**；`kubejs/data/tetra`＝ 2,420 檔；`kubejs/startup_scripts`＝ 153 檔。而 `TetraMaterialItems` 自己已有 `MAX_FILES = 4000`、`MAX_JSON_BYTES = 128_000`（即現有基建**有**上限，plan 的擴充反而冇講）。

**證據**：
- plan §3 第 2／7 條；plan §2 Phase 2；plan §4 表（無 Phase 2 上限 key）。
- 量測：`du -sh kubejs` ＝ **374 M**；`find kubejs -type f` ＝ **9,144**；`find kubejs/data/tetra -type f` ＝ **2,420**。
- `TetraMaterialItems.java:38-39`：`MAX_FILES = 4000; MAX_JSON_BYTES = 128_000;`。

**Flip condition**：plan 為 Phase 2 明列 `maxFiles／maxBytes／timeBudgetMs`（連新 config key），並論證 2,420＋9,144 檔下嘅實際耗時。

---

### O9 【MED-HIGH】「modId ≠ 命名空間」根本未解決；plan 舉的例子選錯

**攻擊**：Phase 1 第 1 點講「jar 內 `META-INF/mods.toml` 嘅 `modId` 亦記入（處理『modId ≠ 命名空間』情況，例 `golden_age_mod` vs `golden_age`）」。

但實測：`golden_age_mod` 同 `golden_age` **係兩個真命名空間**（`goldenage-mod.jar` 提供 `data/golden_age_mod`／`assets/golden_age_mod`；`golden_age` 由 kubejs 提供）——**唔係** modId≠ns 的例子，而係「一個 jar 提供多個 ns」＋「一個 ns 由 kubejs 提供」。真正需要處理的 modId≠ns 例子係：

實測 **10 個 jar：冇任何宣告 modId 對得上任何 data/assets ns**：
- `oculus-mc1.19.2-1.6.9.jar`：modId `oculus`，ns `iris`
- `dynamiclightsreforged-1.19.2_v1.4.0.jar`：modId `dynamiclightsreforged`，ns `lambdynlights`
- `cloth-config-8.3.134-forge.jar`：modId `cloth_config`，ns `cloth-config2`
- `rubidium-extra-…jar`：modId `rubidium_extra`，ns `sodium-extra`
- `konkrete_forge_1.8.0_MC_1.19-1.19.2.jar`：modId `konkrete`，ns `keksuccino`
- 另有 `RuOK`、`Tweakerge`、`ftbbackups2`、`konkrete`、`lios_overhauled_villages`、`tetrajs`

「記入 modId」對呢類**完全無用**——`oculus`≠`iris`，除非讀 jar 檔名啟發式或維護硬編碼對照表（後者違反 §3 第 3 條「零硬編碼」）。而且 plan 冇講「記入 modId」之後**語義係咩**：modId 唔係命名空間，混入 `ns → provider` 表會令以 mod 名問問題時指向錯的東西。

**證據**：
- 量測（python）：逐 jar 抽 `data/`／`assets/` 首段 ns 集合 vs `META-INF/mods.toml` 的 `modId = "…"`；結果 10 個 jar 交集為空，逐個列出如上。
- plan §2 Phase 1 第 1 點；plan §3 第 3 條（零硬編碼）。

**Flip condition**：
(a) plan 明確定義 modId 在索引中係「alias 但唔等於 ns」，並示範 `iris`／`lambdynlights` 類 case 點答；**或**
(b) plan 承認此類 case 標「未知」並列出受影響的 10 個 jar 作已知限制。

---

### O10 【MED-HIGH】runtime 註冊的 regex 抽取不可靠；計劃引的註冊檔名係錯的

**攻擊**：Phase 1 第 4 點「用**輕量 regex** 由 `kubejs/startup_scripts/**` 抽 `ns:id` 字面」。實測：

- `startup_scripts`：**153 檔**、**86 個檔**用 `StartupEvents.registry`、**1,693 次** `event.create(`，其中帶 `'...'` 字面 **1,445**、帶 `ns:` 字面 **1,178**（≈ 70%）。
- 最關鍵：plan §1 引為「實錘」的註冊檔（`dlc_template_item_register.js`）**用變數**註冊：`let builder = event.create(organ.itemID)...`（id 由 function 參數／物件欄位傳入，**冇字面**）→ regex 必抽唔到。
- 而且該檔**根本唔係** `archotech_void_scythe` 的註冊點（真檔係 `ink_register.js:97`，見 F14）——plan 連自己舉的核心案例都指錯檔。

**證據**：
- 量測（grep -c / grep -o）：如上數字。
- `kubejs/startup_scripts/golden_age/dlc_template_item_register.js` 前 20 行：`function registerOrgan(organ) { … event.create(organ.itemID) … }`。
- `kubejs/startup_scripts/golden_age/ink_register.js:97`（真註冊點）。

**Flip condition**：plan 提出可量化的抽取率（例如「1181/1693 ＝ 70% 可抽，其餘標 runtime」）並接受「30% 落 fallback」；或改用 KubeJS 已有的 `KubeJsApiBridge`（reflection bridge）而唔係 regex。

---

### O11 【MED】「32 個 kubejs-only 命名空間」複核唔到（得 28）

**攻擊**：plan §1 寫「32 個 kubejs-only 命名空間」，研究檔 §6 亦寫 32。我用可重跑方法複核得 **28**（試過 3 個變體：jar-any／jar-data-only／union 都唔係 32）。此數係 Phase 1 的**工作量依據**（要答幾多 ns），亦係 S1 的範圍依據。

**證據**：方法＝(jar 內任一 entry 首段 ∈ {`data`,`assets`}) 的 ns 集 vs `kubejs/{data,assets}` 目錄集；結果 jar ns 224、kubejs ns 55、**only 28**、overlap 27。研究檔 line 107 寫「32 個命名空間『喺 kubejs 但唔喺任何 jar』」。

**Flip condition**：給出研究檔當時的掃描器（`%TEMP%` script）同過濾規則，令 32 可重現；否則改用可重跑的 28。

---

### O12 【MED】成本／上限數字冇任何量測依據（我實測：231 jar 目錄列舉只需 0.31 s）

**攻擊**：plan §2 Phase 1 風險欄寫「231 個 jar × namelist 成本 → 必須 async＋快取＋上限」；上限定 `maxJars=300／maxEntries=20000／maxMb=4／timeBudgetMs=4000`。實測：

- 231 jar 全量 `namelist()` 列舉 ＝ **179,114 entries**（其中 `data/`+`assets/` ＝ **109,396**），wall ＝ **0.31 s**（再跑 0.28 s）。
- 連 `JarLightIndex.fingerprintZip` 式（逐 entry 餵 name＋CRC＋size 入 SHA-256）都只係 **0.36 s**。
- `mods` 總大小 476.4 MiB；現有 `jar-cache` 只 **1.5 MiB**（全部 231 jar 的 R/U/L facts）。

即係話：**4,000 ms 預算比實測需求大 10 倍以上**，而 **4 MB 上限比現有同類 cache 大 2.7 倍**——兩個數字都唔似由量測得出。而 `maxEntries=20000` 對比實際 **109,396** 個 data/assets entry 更係只能覆蓋 **18%**（除非「entry」係指 ns，但 ns 只有 224，又解釋唔到 20000）。plan 冇定義「entry」係咩。

**證據**：我的三個 python 量測（如上）；`mods` ＝ 231 jar／476.4 MiB；`jar-cache` ＝ 1.5 MiB；plan §2 Phase 1 第 3 點＋§4 表。

**Flip condition**：(a) plan 定義「entry」語義並示範 20000 如何足夠；或 (b) 用實測重定上限（例如 `timeBudgetMs=1000`、`maxMb=1`）並解釋 4 MB 從何而來。

---

### O13 【MED】Fallback 級 2 空洞化：patchouli 係本機檔（屬級 1）；「標版本」冇取得機制

**攻擊**：Phase 4 講「級 2 **上游 mod 文件**：只喺級 1 全空時用；**必須標版本**（例『Tetra 1.19.2-5.6.0 官方 wiki』）… 實作：`docs` 來源層，**預設唔上網**，先用**本機 jar 內自帶文件（patchouli／lang／advancement desc）**」。呢個係**自相矛盾**：級 2 叫做「上游文件」，但實作內容係**玩家本機的** patchouli／lang／advancement——即係**級 1**。而 patchouli 掃描**已經存在**（`PatchouliEntryScan.java`、`client/patchouli/PatchouliGuideLookup.java`、`guidebook-index` cache 實測 7.1 MB）。所以「級 2」在預設（唔上網）下**冇任何新內容** → Phase 4 實質係「多一個 bool ＋ 一個版本字串」。

而「必須標版本」亦冇機制：patchouli book 喺 `assets/<mod>/patchouli_books/`，要出「Tetra 1.19.2-5.6.0」就要讀 jar 內 `META-INF/mods.toml` 的 version（plan 冇提）。plan §3 第 1 條要求「錯誤靜默」，但「標唔到版本」係**靜默地唔標**（違反「鐵律：唔准用最新版資料答舊版」的**意圖**）。

**證據**：
- `logic/PatchouliEntryScan.java`、`client/patchouli/PatchouliGuideLookup.java`（已存在）；`guidebook-index` ＝ **7.1 MB**（實測）。
- plan §2 Phase 4；plan §3 第 1 條。

**Flip condition**：
(a) plan 重新定義階梯（級 1 ＝ 本機含 patchouli；級 2 ＝ 僅 network）並把 patchouli 移返級 1；**或**
(b) 明寫版本字串的取得來源（`mods.toml` version）同「取唔到就唔標」的處理。

---

### O14 【MED】隱私論述不完整（`web.allowWebSearch` 默認 **true**；`knowledgeUrl` 指向 SK 公開 GitHub；item id 本身係敏感資料）

**攻擊**：plan 反覆用「維持離線優先＋**私隱**」為 `docsLookup.network=false` 的理據。但實測現狀：

- `knowledgeRemote` 默認 false（真），**但** `web.allowWebSearch` 默認 **true**（`PackAiConfig.java:353`）——即係話「離線優先」唔係現行姿態。
- `knowledgeUrl` 默認指向 `https://raw.githubusercontent.com/skps00/packai-knowledge/main`（`PackAiConfig.java:513-514`）；URL scheme 係 `<base>/items/<ns>__<path>.json`，即 **item id 明文出現喺 URL path**。對 pack 私有內容（`golden_age:archotech_void_scythe`）而言，**item id 本身就係要保護嘅資訊**（研究檔已證 GitHub code search 對呢個 id 零結果 ＝ 上游根本冇呢個知識）。所以「只送 item id」**唔係**一個保護——plan 把「只送 item id」當成緩解措施係錯的分類。

**證據**：
- `PackAiConfig.java:353`（`.define("allowWebSearch", true)`）、`:504`（`knowledgeRemote=false`）、`:513-514`（`knowledgeUrl` 預設）。
- `logic/KnowledgeRemote.java:19`（`URL scheme: <knowledgeUrl>/items/<ns>__<path>.json (item id only; no query)`）、`:89`（`return root + "/items/" + stem + ".json";`）、`:108`（`sanitizeSegment(ns) + "__" + sanitizeSegment(path)`）。

**Flip condition**：
(a) plan 把「只送 item id」由「隱私緩解」降級為「已存在的既成風險」，並明確 `docsLookup.network` 決策要連 `allowWebSearch` 一齊檢視；**或**
(b) 對「pack 私有 ns」加本地黑名單（唔外送 kubejs-only ns）。

---

### O15 【MED】新 index 另起爐灶（已有 `PackIndex` #5 universal KubeJS 抽取 ＋ python 鏡像），而且冇 python 鏡像

**攻擊**：plan §2 Phase 1 講「**重用現有基建，唔另起爐灶**」，但第 4 點卻提議喺新檔 `NamespaceProviderIndex` 內**另寫**一組 regex 去抽 `StartupEvents.registry` 的 `ns:id`。而 repo 已經有同類東西：`PackIndex` 的 universal KubeJS scan（`ITEM_CREATE = re.compile(r"\.create\(\s*['\"]([a-z0-9_.:/-]+)['\"]\s*\)")` 等），而且有 **python 鏡像閘** `tests/check_kubejs_universal_scan.py` 保持兩邊同步。新加一組 regex ＝ 兩份真相，而新檔**冇**對應的 `tests/check_*.py` 鏡像（plan 只提 Java harness `NamespaceProviderIndexCheck`）。repo 慣例係成對（`JarLightIndex.java` ↔ `tests/check_jar_light_index.py`）。

**證據**：
- `tests/check_kubejs_universal_scan.py:1-30`（docstring：「Mirror PackIndex #5 universal KubeJS scan」；`ITEM_CREATE` regex）。
- `tests/check_jar_light_index.py`（既有鏡像慣例）。
- plan §2 Phase 1 第 1／4 點；plan §5（新增驗收只列 Java harness）。

**Flip condition**：plan 明寫「Phase 1 抽 id 重用 `PackIndex` 的既有 regex（唔新寫）」＋ 加 `tests/check_namespace_provider_index.py` 鏡像。

---

### O16 【MED】`providerIndex.*` vs `contentIndex.*` key 名前後不一（G)

同 F18。同一個上限集有兩個名，直接影響 §4「Settings：ADVANCED 頁加對應 key」的實作（加錯名 → 閘／UI 對唔上）。

**Flip condition**：plan 統一命名（任一）。

---

### O17 【LOW-MED】`check_settings_registry.py` 的約束被 plan 誤解（但唔致命）

**攻擊**：plan §4「Settings：ADVANCED 頁加對應 key（3 檔 lang 同步）＋ `tests/check_settings_registry.py` 綠」。該閘係**單向**的：只驗「registry 有列 → config 有 define → 有 setter → setter body 有 `SPEC.save()` → 3 個 forge lang 有 label＋tooltip」。它**唔要求**每個 config key 都上 registry。所以「加 config 但唔加 UI」係合法的——plan 冇講清 5 個新 key 邊個上 UI、邊個唔上，亦冇提「加 registry entry 就要埋 `SettingsScreenV2` 的 ControlType 分支」（`REQUIRED_CONTROL_TYPES`）。

**證據**：`tests/check_settings_registry.py:131-143`（單向斷言鏈）、`:171-179`（`SettingsScreenV2` 必須有 4 種 ControlType 分支）、`:28-36`（`UI_CATEGORIES` 必須全部非空）。

**Flip condition**：plan 明列每個新 key 是否上 UI；若上，附 label／tooltip key 命名。

---

### O18 【LOW】`maxJars=300` 對 231 個 jar 只餘 1.3× headroom

**攻擊**：plan 為自己講「大 pack（NFWC）要實測耗時」——但 `maxJars=300` 對本機 **231** 只餘 **1.3 倍**。研究檔顯示同一 pack 可有 4–5 個衍生版本同時活躍（NFWC-2 係 1.20.1 另一條線），而公開 repo（E6／E9）KubeJS 檔數去到 800+。任何比本機大 30% 的 pack 就會靜默截斷 provider 覆蓋，而 plan 冇講截斷時的行為（靜默？log？）。

**Flip condition**：plan 定義超限行為（例如「超出唔截斷、只延長時間預算」）或提供量測證明 300 足夠。

---

## 3. 事實 vs 推測（分清）

**已核實（本輪工具／檔案證據）**
1. `PackAiConfig.java:482-484` 的「Never scans kubejs/assets or kubejs/data」原文（惟係上限註解）。
2. `PackAiConfig.java:442` `scanModJars` 默認 **false**；`:353` `allowWebSearch` 默認 **true**；`:504` `knowledgeRemote` 默認 **false**；`:513-514` `knowledgeUrl` 預設值。
3. `JarLightIndex.java` 只讀 recipe/loot（`:364-366`）；上限 `200/150/8`（`:55-60`）；`extractIngredients` 唔收 `base/addition/template`（`:522-534`）。
4. Java 樹 `crafting_shaped` ＝ 0（grep）。
5. `TetraMaterialItems.java:255` **已**掃 `kubejs/data`；`:270-271` 只掃 materials/schematics；`:431-442` 有讀 outcome 內嵌 `improvements`。
6. `TetraSchematicLookup.java:72-79` 註解自認「NFWC: kubejs datapack first（confirmed ~2700 tetra data files）」。
7. `tests/check_internal_label_parity.py` 要求雙樹 token 一致（`:99-106`）＋ **6 個 lang 檔**（`:26-30`）。
8. `tests/check_settings_registry.py` 只驗 forge 3 lang、單向鏈。
9. `tests/check_dual_tree_sync.py:10-14` 有 pause（WARN）；`check_internal_label_parity.py` **冇** pause。
10. 量測：231 jar／179,114 entries／109,396 data+assets／479 MiB／列舉 0.31 s／fingerprint 式 0.36 s。
11. 量測：jar 內 recipe 12,799＋loot 5,047；超標 jar 17；**丟 7,595 recipe**；`create` 2,037→200。
12. 量測：smithing recipe **96** 條，keys 全係 `[addition, base, result, type]`。
13. 量測：`kubejs` 9,144 檔／374 MB；`data/tetra` 2,420 檔；`startup_scripts` 153 檔；`event.create(` 1,693 次（帶 ns 字面 1,178）。
14. 量測：kubejs-only ns ＝ **28**（plan 寫 32）；overlap 27。
15. 量測：10 個 jar 無任何 modId 對得上 ns（`oculus`/`iris`、`dynamiclightsreforged`/`lambdynlights`、`cloth_config`/`cloth-config2`、`rubidium_extra`/`sodium-extra`、`konkrete`/`keksuccino`，＋`RuOK`、`Tweakerge`、`ftbbackups2`、`lios_overhauled_villages`、`tetrajs`）。
16. `archotech_void_scythe` 註冊點 ＝ `kubejs/startup_scripts/golden_age/ink_register.js:97`（唔係 plan 引的 `dlc_template_item_register.js`）。
17. Git：HEAD ＝ `3572a82`；工作樹 **67 modified／2 deleted／125 untracked**；`+4,448/−1,287`。
18. `tests/check_*.py` ＝ **121** 個；`src/test/**/*.java` ＝ **49** 個。
19. lang 3 檔各 **513** key；`label.src.*` 各 11 個；neoforge 3 檔亦各 11 個。
20. instance 內 cache：`item-index` 26 MB、`guidebook-index` 7.1 MB、`jar-cache` 1.5 MB、`mechanic-cache` 1.9 MB。

**懷疑／未確認（需要咩數據才確認）**
1. **S1–S6 的真機行為**：需要 SK 開 game 跑一次（S5 木錘、S2 亞巴頓、S6 SB 背包、S1 木錘標籤）＋ 讀 `<instance>/packai/trace/ask-*.jsonl`。
2. **120/121 綠的分佈**：需要跑 `tests/check_*.py` 對 baseline（我**刻意冇跑**，因為 `check_ask_display_leak.py:676`／`check_jar_contains_fix.py:59-61` 會寫檔／建目錄，違反本次「只讀」禁令）。plan 講「1 條＝要真機 log」的講法**未經我獨立驗證**。
3. **`create:schematic` 卡／文字不一致的實錘**：plan §2 Phase 5 講「卡同文字都講『冇呢個物品…』」——需要真機 trace 或 JEI 側證據；我無法離線確認。
4. **NFWC 更大的 pack 上 provider scan 耗時**：需要一個 >231 jar 的 instance（本機 51 個 instance 中最大者未量）。
5. **plan §2 Phase 0「親驗 BUILD SUCCESSFUL／49/49／120/121」**：需要獨立重跑 gradle（本次禁跑）＋ 需要 baseline worktree 對照。
6. **研究檔「32 個 ns」的原始掃描器**：需要 `%TEMP%` 的 `mp_instance_matrix_scan.py` 或其過濾規則。

---

## 4. 反方整體評分（estimate）

**正方 3 : 反方 7**

理由：
- **反方勝的**：O1（CRITICAL，plan 兩條自訂條款互相打架，必破自己 §5 驗收）、O2／O3（Phase 5 的前提同「單一來源」被實測數據否證，且存在更短解法）、O5（部署範圍嚴重低估，還原點無效）、O7（違反 repo 契約「拆開評」）。
- **正方仍得的**：核心方向真確（F1／F2 一部分、F15 大部分、F17 全中）；plan 有 baseline／還原／lang 同步／trace 語義凍結等好習慣；範圍聲明（1.19.2 only、唔碰 neoforge）清晰；`fix A` 已有負控記錄。
- **未到 8:2 的原因**：O1／O2 需要**設計改動**（唔係純措辭修正）才可以化解；O5／O6 需要 SK 決定部署次序。三樣都係「實質修改」，但 v1 一項都未做。

> 依 repo 契約，本輪屬第 1 輪（R1）。**反方 flip condition 一旦被下一輪證據推翻，該輪即失效**，唔可以同一組異議重複計輪。

---

## 5. 最貴的 3 個未知（要咩才解得開）

1. **`tests/check_*.py` baseline 的真實綠紅分佈（尤其 `check_internal_label_parity`／`check_ask_display_leak`）**
   解開方式：喺一個**乾淨 worktree**（`git worktree add`，唔郁主樹）跑全部 121 個 `tests/check_*.py`，記 `fail` 清單。冇呢個數，§5「零新增紅」係一句**無法驗證**的驗收，亦無法定 O1 的嚴重度係「必 FAIL」定「已 FAIL」。
   （我本次**冇跑**：`check_ask_display_leak.py` 同 `check_jar_contains_fix.py` 會寫檔／建目錄，撞本次「只讀唔改」禁令。）

2. **工作樹 67 個未 commit 檔的內容歸屬**
   解開方式：`git stash`／建 worktree 後逐檔對「邊個係 fix A、邊個係更早未 commit 的工作」。冇呢個，Phase 0 的「10 檔」同 §6 的還原點都係**空的**，而且「Phase 0＋1 一齊部署」等於部署一個**未描述過的狀態**。

3. **真機 S1–S6 的實際結果（尤其 S6 SB 背包 同 S1 木錘標籤）**
   解開方式：SK 開 game，逐項問，之後我讀 `<instance>/packai/trace/ask-*.jsonl` ＋ `latest.log`（唔靠肉眼印象）。呢個同時係 O2（對齊會唔會反而 regress）、O3（4 行修法夠唔夠）、O6（一次過驗可否歸因）三條的唯一裁判。

---

## 6. 方法限制（誠實聲明）

- **冇跑 gradle／build／任何 `tests/check_*.py`**：受本次任務「只讀唔改」禁令約束（`check_ask_display_leak.py:676` `write_text`、`:910-916` `mkdir`＋`write_text`、`check_jar_contains_fix.py:59-61` `rmtree`／`mkdir` 會寫檔）。所以所有「baseline 綠紅」相關的異議都以**【未能複核】／【懷疑】**標示，冇當事實。
- 量測腳本全部為 python stdlib 唯讀（`zipfile.namelist()`／`infolist()`、`os.listdir`、`json.loads`），即在 `%LOCALAPPDATA%\Temp%` 亦無寫任何 scratch 檔（全部用 heredoc 直接執行）。
- 唯一寫入檔：本報告。
- `md5sum` 複核：`96e4a0906b53dc6cbe60b243f2008f2f` ✅ 同任務給的頭 12 相符。

---

## 7. 反方對「正方可能反駁」的預判（先寫落，等正方打）

| 正方會講 | 反方回應 |
|---|---|
| 「O1 只係加幾個 label，唔一定要入 `INTERNAL_SECTION_TOKENS`」 | 咁就要示範一個唔經 token 又令 `isKnownSrcLabel`／`collapseLabelDuplication` 認得新 label 的路徑（`AskReplyScrub.java:723-741, 804-812`）。如果新 label 落 `logUnlabeled` 分支，就要交代會唔會令 `check_*` 或真機 log 出 unknown label。 |
| 「O2 的上限係保護，唔係 bug」 | 保護係對的，但 Phase 5 用佢做『單一來源』就會把 51% 缺失**當事實**輸出（會出「冇材料」而其實有）。要麼提上限，要麼明寫「只加不減」。 |
| 「O3 唔成立，因為默認 `scanModJars=false`」 | 咁 O4 就成立（依賴默認關閉的基建），而且 Phase 5 更應該講清「jar facts 存在與否」的前置條件。兩者只可以否證一個。 |
| 「O5 的 67 檔係歷史遺留，唔關本 plan」 | 咁就更加要 commit 一個基線 SHA；「唔關本 plan」唔等於「部署時唔會一齊出去」。 |
| 「O7 已經有 §7 講逐 phase 驗收」 | §7 講 review 流程，冇講「拆 plan」；repo 契約原文係「拆開**評**」，即 review 單位要拆，唔係只係實作單位要拆。 |
