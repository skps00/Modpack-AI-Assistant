# 反方 review R2 — packai「Pack 內容感知 + 來源誠實」全計劃 **v3（roadmap）**

> 被 review：`docs/plans/2026-09-19-pack-content-awareness-full-plan.md`（v3；commit `b157966`；實測 `md5sum` 頭 12 ＝ **`ee50725cfd67`**）
> 前一輪：R1 判 **正方 3 : 反方 7**（18 條 O1–O18，報告 `docs/plans/reviews/2026-09-19_pack-content-awareness-plan-R1-opposing.md`）
> 立場：**反方（opposing）**。目標＝盡最大努力證明 v3 仍未達標。
> 方法：**只讀**。逐條開真檔核（檔名＋行號＋原文）＋ python stdlib／`zipfile` 唯讀量測 ＋ 讀真機 trace／jar-cache。**冇跑 gradle／build**；**冇改任何 repo 檔**（唯一寫入＝本報告；scratch 落 `%LOCALAPPDATA%\Temp\`）。
> 日期：2026-09-19。

---

## 0. 開場結論（人話）

v3 嘅**架構改正確**（降格 roadmap ＋ 每 phase 獨立 plan／review／部署，真正解決 O7；O1 嘅 parity 死鎖亦確認可以用「唔加 token／role key」繞過——我親手核過閘嘅實作）。比分由 R1 嘅 3:7 升到 **4:6**。

但**兩個新 blocker 令本輪仍然唔可以開工**：

1. **v3 排最前、自稱「成本最低、可即時回報」嘅 P2，實作完唔會生效。** `JarLightIndex` 嘅 jar-cache **冇解析器版本**（`manifest.json` 嘅 `"v"` 只寫唔讀），jar 指紋一符就跳過重解析 → 改 `extractIngredients` 之後，**機器上已有嘅 cache 照用舊解析**；SK 嘅 instance 實測正正有噉嘅 cache，而 `sophisticatedbackpacks:netherite_backpack` 條 fact 係 **`R|smithing_backpack_upgrad|`（材料空 ＋ type 被截字）**。即係照 v3 字面做，P2 嘅驗收問題（SB 背包）**極可能照樣 miss**，然後被誤讀成「修法唔 work」。
2. **「ns → 提供者」二元模型對本 pack 最大嘅內容區會系統性講錯。** 實測 **27 個命名空間同時由 jar 同 kubejs 樹提供**，其中 **`tetra` 有 3,390 個 kubejs 檔**（`modules`／`schematics`／`improvements`／`repairs`／`synergies` 全部）。即係 P3 要讀嘅 Tetra 六層、同旗艦案例「亞巴頓」嘅資料檔，全部坐落喺一個「jar ns」＝會被判成**原廠**。而一旦 jar 掃描被 `maxJars`／`timeBudgetMs` 截斷，錯嘅方向係**把原廠內容講成「本包自加」**（無聲、無 log）。

另加：v3 自稱 §1／§4 數字「全部親手重測」，但 **3 個數字對唔上**（`222 個 golden_age id`、`96 條 smithing`、`maxMb=4`），而我重測到嘅係另一批數（109／95／實際只需幾 KB）。

---

## 1. 逐條判定 O1–O18

判定三級：**【真解決】**＝v3 嘅修法已解除該異議（我已核）；**【表面解決】**＝v3 有回應但只改一半／數值或行為未 pin；**【未解決】**＝只係口頭聲明或推遲，異議仍活。

| # | R1 異議 | v3 點改 | R2 判定 | 關鍵證據（本輪親手核） |
|---|---|---|---|---|
| O1 | 新 label 必破 parity 閘、同「唔碰 neoforge」死鎖（CRITICAL） | P4 改 `AskResult.provenance` 欄位（零新 lang key） | **表面解決** | **死鎖真**：`tests/check_internal_label_parity.py:26-30`（6 檔）、`:99-106`（兩樹 token 相等）、`:129-135`（逐 key）。**但機制真可繞**：該閘只驗 **token／role 派生**嘅 key；orphan 檢查只有 `packai.label.role.*`（`:151`）。→ 欄位路線 OK；不過 v3 自己 §5 加咗「兩棵樹 lang key 數一致」，而**冇任何閘強制**（我 grep `tests/*.py` 冇 set 相等檢查）→ 交付物變成玩家睇唔到（見 §2.3、N5） |
| O2 | per-jar 上限令「單一來源」只 ~49% 覆蓋（HIGH） | 刪「大對齊」；P2 只做根因修＋SB 回歸 | **真解決**（就異議本身） | 上限仍在：`logic/JarLightIndex.java:55-56`（200／150）✅ v3 §1 引述正確；plan 已**唔再**用 jar facts 做「單一來源」⇒ 異議前提消失 |
| O3 | smithing 有 4 行解法，plan 揀最貴機器（HIGH） | P2 提前；`extractIngredients` 加收 `base/addition/template` | **真解決（方向）＋新阻礙** | 我實測：**95 條 `minecraft:smithing`**，加 key 之後 **94/95** 攞得到 ≥1 個具體材料（樣本 `FarmersDelight…netherite_knife_smithing.json` → `[farmersdelight:diamond_knife, minecraft:netherite_ingot]`）。**但**：SB 個案嘅 type 係 `sophisticatedbackpacks:smithing_backpack_upgrade`（唔屬呢 95）、且 cache 令修正唔生效 → 見 N1／N3 |
| O4 | 靠預設關閉嘅 `JarLightIndex`（HIGH） | P1 明寫解耦＋觸發＝首次開 Ask＋失敗靜默 | **真解決（roadmap 級）** | `PackAiConfig.java:442` `.define("scanModJars", false)`✅；`JarLightIndex.java:71` 早退✅；`AskEngine.java:251-252` 只在 on 時 `ensure`；`AskResult` 之外 `factsForAsk` 亦 gate（`:115-116`）。v3 §9 明寫解耦 ⇒ 方向對；細節留 phase plan（可接受） |
| O5 | P0 規模低估（67 未 commit 檔；「10 檔 backup」覆蓋唔到）（HIGH） | P0 加前置：全樹歸屬清單＋md5＋SK 確認；明文部署＝整樹 | **未解決** | 自 R1 之後工作樹**冇變**：`git diff --stat` 尾行仍然 `67 files changed, 4448 insertions(+), 1287 deletions(-)`；`git status --short` 116 行，其中 **33 個 `tests/` 檔（包括閘本身 `check_dual_tree_sync.py`／`check_dual_tree_diff_symmetry.py`／`check_recipe_cards_mode.py`）未 commit**。⇒ §5「零新增紅」嘅 baseline **冇 SHA 可指、唔可重跑**；v3 只寫「要交清單」，清單本身未存在 |
| O6 | S1–S6 綁一次真機 session，fail 唔可歸因（HIGH） | 真機驗收拆成每 phase ≤2 問 | **真解決** | plan §2 roadmap 表每個 phase 一列「1 問／2 問／負控」；§5 明寫「每 phase 最多 2 問」 |
| O7 | 5 phase 綁一個 plan 違反 repo 契約（HIGH） | 本檔降格 roadmap；每 phase 另出 plan＋獨立閘 | **真解決** | plan §2 標題「每 phase 實作前另出 phase plan、獨立 review、獨立部署」；§7 同 |
| O8 | P2 冇硬上限（kubejs 374 MB／9,144 檔）（MED-HIGH） | 新 key 附檔數／MB／時間上限；超即停 | **表面解決** | 我重測：kubejs 樹 **9,144 檔／352.7 MB**（`du` 374 MB 係 block size，predicate 差異）。但 v3 §4 只列 `contentIndex.packContentLang=true` ＋括號「kubejs/lang 檔數上限 4000／8 MB」，**呢兩個數係抄現有常數**（`TetraMaterialItems` `MAX_FILES=4000`；`PackAiConfig.java:488` `8_388_608`），而**實測只需 44 檔／2.0 MB**（`kubejs/assets/*/lang/*.json`）⇒「按實測定上限」唔成立；亦冇講 4000/8MB 係唔係 config key |
| O9 | modId ≠ ns 未解決，例子選錯（MED-HIGH） | jar ns（權威）同 modId 分開記錄，對唔上標 `unknown` | **表面解決** | 冇處理**第三態**：同一個 ns **同時**由 jar 同 kubejs 樹提供 → 實測 **27 個**（`jar ns ∩ kubejs ns`），其中 `tetra` **3,390 檔**、`kubejs` 795、`mvs` 114…⇒ 呢啲 ns 會被判「原廠」，而 pack 明明改咗（見 §3 N2） |
| O10 | runtime 註冊 regex 唔可靠；引嘅註冊檔名錯（MED-HIGH） | 改 runtime registry 為準；檔案只旁證；regex 只 fallback。反駁：「`dlc_template_item_register.js` 確實有 `registry('item')`＋222 個 id」 | **真解決（＋v3 反駁半對）** | **反駁核實**：該檔第 3 行真係 `StartupEvents.registry('item', event => {`（substring `registry('item'` 命中 **1**）✅；但 **「222 個 id」複核唔到**——我三個 predicate：`.create('golden_age:X'`＝**109**、引號內 `golden_age:*` unique＝**403**、總出現＝434；`archotech_void_scythe` **13 個 kubejs 檔**✅（v3 對）。另：runtime registry（`client/knowledge/ItemIndex.java:266` `Registry.ITEM.entrySet()`）**只證明存在**，唔含「邊度註冊」→ attribution 仍要 ns→provider 索引（帶 N2 嘅洞） |
| O11 | 「32 個 kubejs-only ns」得 28（MED） | §1 已改正 | **真解決** | 我重測：jar ns **224**、kubejs ns **55**、kubejs-only **28**、overlap **27** ✅ 同 v3 §1 完全一致 |
| O12 | 成本／上限冇量測依據（MED） | §4 上限全部按實測重定 | **表面解決** | 「231 jar 列舉」我重測 **0.286 s**（v3 0.34 s，同量級✅）；**額外量測**：連讀 + `json.loads` 全部 17,846 條 recipe/loot 都只 **0.53 s** ⇒ `timeBudgetMs=2000` 站得住（我原本想攻「預算由錯量度定」，實測後**撤回**）。但 **`contentIndex.maxMb=4` 冇任何依據**（索引只存 ns→provider 字串，224+55 條 ≈ 幾 KB），而 R1 已經點名過呢個 4 MB，v3 **照留原值**卻寫「全部按實測重定」 |
| O13 | Fallback 級 2 空洞（patchouli 屬級 1；「標版本」冇機制）（MED） | 級 2 重定義＝只用本機 jar 內文件；上網另案（預設關） | **真解決** | plan §2 P5 明寫級 2 只用本機 jar 文件；§4 `docsLookup.network=false`；patchouli 掃描早已存在（R1 證據）⇒ 階梯定義一致 |
| O14 | 私隱論述不完整（`allowWebSearch` 預設 true 等）（MED） | §1 加網絡現況；新功能預設關＋獨立同意 | **表面解決** | `PackAiConfig.java:353` `.define("allowWebSearch", true)`✅ 仍在、`:504` `knowledgeRemote=false`、`:513` `knowledgeUrl` 有預設。v3 §9 寫「答案唔准洩路徑／內部 id」，但**冇**明文撤回 R1 嘅分類糾正（「只送 item id」唔係隱私緩解，`KnowledgeRemote.java` URL 直接含 item id） |
| O15 | 另起爐灶＋冇 python 鏡像（MED） | P1 改擴充現有基建；每新閘要有 python 鏡像 | **表面解決** | P1 方向 ✅；但 **P2 係改 `JarLightIndex`**，而 repo **已有鏡像** `tests/check_jar_light_index.py`（`:11-127` 有 `short_type`／`collect_items`／`parse_recipe_json`），**冇 cross-check Java**（我 grep 全文 0 個 `.java` 引用）⇒ Java 改、鏡像唔改，閘照綠＝**假綠**（v3 冇提） |
| O16 | key 命名不一（`providerIndex.*` vs `contentIndex.*`）（MED） | 統一 `contentIndex.*` | **真解決** | plan §4 全表 `contentIndex.*`＋`docsLookup.*`，全文再無 `providerIndex` |
| O17 | 誤解 `check_settings_registry.py` 約束（LOW-MED） | 「開工前先讀該閘真實規則再定」 | **未解決（推遲）** | 我實跑該閘：`OK: registry_entries=46 categories=7 toml_paths=60 setters_checked=46 excluded=0 control_types=4` ⇒ **實測 60 個 config path 對 46 個 registry entry＝14 個 key 合法地唔上 UI**，證明閘係**單向**（R1 講嘅對）。v3 只寫「先讀」＝把異議推入 phase plan，未解 |
| O18 | `maxJars=300` headroom 只 1.3×（LOW） | 改 600（2.6×） | **表面解決** | flip condition 係「**定義超限行為**」；v3 只改數字，**行為仍未定義**（截斷？log？繼續？）。而超限／超時嘅錯法係「jar ns 集不完整 → 把原廠內容判成本包自加」（見 N2），唔止「小咗 coverage」 |

**小結**：18 條 → **真解決 9**（O2／O3方向／O4／O6／O7／O10／O11／O13／O16）、**表面解決 7**（O1／O8／O9／O12／O14／O15／O18）、**未解決 2**（O5／O17）。

---

## 2. 特別核實（任務點名三項）

### 2.1 「28 個 kubejs-only ns」＋「jar listing 0.34 s」— **我自己重測，兩項都成立**

命令（唯讀，stdout 直出，冇寫任何 scratch 入 repo）：

```python
# jar ns = 任何 entry 首段 ∈ {data,assets} 之後嘅第一段；kubejs ns = kubejs/{data,assets}/<dir>
jar_ns = ...   # 231 個 jar
kj_ns  = ...
only   = kj_ns - jar_ns
```
實測輸出：`jar ns: 224  kubejs ns: 55  kubejs-only: 28  overlap: 27`
28 個名單：`b_a_d, deeperdarker, dlc, dnl, golden_age, golden_age_tetra, gud_toolkit, hpdlc, ino_dlc_build, ino_dlc_ghost, ino_dlc_saber, ino_dlc_wizard, luna_flesh_reforged, maodlc, mayacraftdlc, mfdlc, momo_dlc, mota_dlc, mrqx_disc_pack, mrqx_extra_pack, numismaticoverhaul, ponderjs_generated, stray_expansion, sudlc, sydlc, tetranomicon, weapon_master, world_of_bosses`

計時：231 jar 全量 `namelist()` ＝ **179,114 entries**（`data`+`assets` 109,396），wall ＝ **0.286 s**（v3 報 0.34 s；同一量級，接受）。
**額外（v3 冇量嘅）**：連讀檔 ＋ `json.loads` 全部 **17,846 條** recipe/loot ＝ **0.53 s**（python；Java 只會更快）⇒ §4 `timeBudgetMs=2000` 係保守嘅，**我撤回原本想攻擊「預算建基於錯量度」嘅一條**。
`kubejs` 樹：**9,144 檔／352.7 MB**（`du -sh` 報 374 MB＝block size；predicate 差異，唔算錯）。

> 判定：**已核實（v3 對）**。同時確立另一件事：**overlap 27 個 ns 完全冇被處理**（v3 §1 只報 28，唔講 27）→ N2。

### 2.2 v3 對 O10 嘅反駁 — **半對**

| v3 聲稱 | 我嘅量測（predicate 寫死） | 判定 |
|---|---|---|
| `dlc_template_item_register.js` 有 `registry('item')` | `StartupEvents.registry('item', event => {`（substring `registry('item'` ＝ 1；字面連右括號 `registry('item')` ＝ **0**，因為真碼係 `('item', event => {`) | **實質對，字面引文錯** |
| 該檔有 **222 個** `golden_age:` id | `.create('golden_age:X'` unique ＝ **109**；引號內 `golden_age:*` unique ＝ **403**（總 434） | **✗ 對唔上（三個 predicate 都唔係 222）** |
| `archotech_void_scythe` 只喺 **13 個** kubejs 檔（Tetra 資料檔為主） | 實測 **13** ✅（`assets/golden_age_tetra/lang/zh_cn.json`(13)、`client_scripts/golden_age/item_tooltips_1.js`(2)、`data/tetra/modules/single/archotech_void_scythe*.json`、`…/repairs/single/archotech_scythe*.json`、`…/schematics/**`、`…/synergies/single/…`、`server_scripts/golden_age/ink_create.js`、`startup_scripts/golden_age/ink_register.js`） | **✅ 對**（R1 講嘅註冊點 `ink_register.js` 亦確認） |

⇒ v3 嘅**結論**（改 runtime registry 為準）成立；但**支撐數字一個錯**，而「222」正是 v3 用嚟反駁 R1 嘅唯一新數字。同一段文字嘅 §1 又寫「`dlc_template_item_register.js`：`registry('item')` ＋ 222 個 `golden_age:` id 字面」——**兩個數字都要改**（建議改寫成 predicate 可重跑嘅形式）。

### 2.3 `AskResult.provenance` 欄位方案 — **機制真可以繞過 parity 閘，但 v3 幫自己造咗一個唔存在嘅約束，結果交付物玩家睇唔到**

**(a) 繞過 parity 閘：成立。** 我讀 `tests/check_internal_label_parity.py` 全文：
- 佢只檢查 **`INTERNAL_SECTION_TOKENS`／`EXTRA_BARE_SECTION_TOKENS`／`INTERNAL_ROLE_VALUES`／`SCROLL_SECTION_REGEX` 派生**嘅 key（`:54-58` `expected_keys()`），加一個 **只針對 role** 嘅 orphan 檢查（`:145-152`）。**`packai.label.src.*` 冇 orphan 檢查**。
- 所以：**唔加 token／role key** 就完全繞過呢個閘（R1 O1 嘅「必破」只成立於「新 label 走 token 路線」）。
- 加 component 落 `AskResult`（record）會令兩樹 `logic/AskResult.java` byte drift：我實跑 `tests/check_dual_tree_sync.py` 嘅 pause 語意（`:10-12`、`:203-242`、`:276-281` `SUMMARY paused=True fail=0`）＋ `neoforge/README_PAUSED.md` 存在 ⇒ **non-allowlist byte drift 變 WARN（唔係紅）**，而 `AskResult.java` **唔在** ALLOWLIST（grep `AskResult` in `check_dual_tree_sync.py` ＝ 0）⇒ 照 v3 做 **唔會**打紅呢個閘。成本：forge main 有 **7 個 `new AskResult(` call site**＋全部 `with*` 方法（`AskResult` 在 forge main 出現 **81 次**）。python 閘係 **substring 斷言**（`tests/check_card_tool_emission.py:42-47`、`check_recipe_cards_mode.py:125-130`、`check_ask_player_tool.py:47-50` 讀兩樹 `AskResult.java` 但只 assert 字串存在）⇒ 加 component **唔會**打紅佢哋。

**(b) 顯示路徑：我搵唔到，而 v3 亦冇指名。** 現時 `displaySrc` **只**去 log 同 trace，冇任何 UI consumer：
- `client/service/AskService.java:406`、`:448` → `logDisplayBody(shown.displaySrc(), shown.answer())`；`:469` 只係 log tag；`:510` 寫 trace `display.body.final` 嘅 `src`。
- 我 grep 全 client 樹：`AskResult` 喺 `client/gui/AiAssistantScreen.java` 只被用來攞 `recipeCards()`／`tokenUsage()`（`:438`、`:441`），**冇** `displaySrc`。
- 玩家睇到嘅「來源」係 **答案正文尾嘅【來源】footer**（`AiAssistantScreen.java:861-867` `RecipeEmbed.splitTrailingSources`／`indexBeforeSources`；`:1228` 註釋「【來源】… footers」）——即係**答案文字**，要語言無關就必須 lang key。

**(c) 所以 v3 自相矛盾（我判「表面解決」嘅原因）**：
- P4 row 寫「**零新 lang key**」⇒ 只能係 trace／log 診斷欄位 ⇒ **玩家見唔到任何「來源標籤」**，即 P4 嘅交付物（令玩家分得清 pack 自加／原廠）**冇交付面**。
- 要玩家見到 ⇒ 要文字 ⇒ 要 lang key ⇒ v3 §8 問 SK「要唔要動 neoforge 只加 lang 字串」。
- 但**呢個兩難係 v3 自己造出嚟嘅**：我 grep 全部 `tests/*.py`，**冇任何閘要求 forge 同 neoforge 嘅 lang key 集合相等**（每個閘只查自己點名嘅 key；parity 閘只查 token／role 派生 key）。真正鎖住 v3 嘅係 **plan 自己 §5 寫嘅「兩棵樹 lang key 數一致」**（自加驗收項），而 neoforge 樹係 PAUSED（`neoforge/README_PAUSED.md`）。
⇒ **Flip condition**：(a) 正方示範一條「玩家睇得到 provenance 但零 lang key」嘅顯示路徑（例如純圖示／數字 badge，附檔＋行號）；**或** (b) 撤回 §5 嘅「兩棵樹 lang key 數一致」自設約束，改用 **forge-only 新 lang key**（現有閘全部唔會紅），P4 即刻有可見交付物；**或** (c) 明寫 P4 交付物係「trace 診斷欄位」（唔係玩家標籤），並把 §8 第 2 項刪走。

---

## 3. v3 新引入／延續嘅問題（severity ＋ 攻擊 ＋ 證據 ＋ flip condition）

### N1 【HIGH】**P2 實作完唔會生效**：jar-cache 冇解析器版本；type 又被 24 字截斷；python 鏡像冇 cross-check

**已核實。** 三個獨立缺陷疊埋令「P2 ＝ 幾行代碼、成本最低、可即時回報」呢個**排序理據**唔成立：

1. **Cache 無效化缺失**：`logic/JarLightIndex.java:316-330` `scanOneJar()` —— 只要 `manifest.json` 內該 jar 名嘅 `fp` 同新指紋一樣、且有 `shard` 就 `return false`（跳過重解析）；而 manifest 嘅版本號只係**寫**（`:307` `manifest.addProperty("v", 1)`），**全檔冇任何地方讀 `"v"`**（我 grep `"v"`／`get("v")`：只有 :307）。
   實測 SK instance 真況：`config/packai/jar-cache/manifest.json` ＝ `{"v":1,"jars":{…230 個…}}`，shard `540aec233f22.json` 內 `sophisticatedbackpacks:netherite_backpack` ＝ `["R|smithing_backpack_upgrad|", "L|blocks/netherite_backpack"]` ⇒ **材料清單空**（＝R1 講嘅 miss 真身）＋**type 被截**。
2. **`shortType` 24 字截斷**：`:490-501` —— `if (s.length() > 24) s = s.substring(0, 24)`。`smithing_backpack_upgrade` ＝ 25 字 ⇒ player-visible 行會出 `smithing_backpack_upgrad`。P2 冇提要修（新 fact 生成更好嘅 type 字串時，同一截斷會令「鍛造背包升級」變成半截字，而 `ReplyLang.jarCraft(lang, type, ings)` 係直接餵玩家睇）。
3. **鏡像閘假綠**：`tests/check_jar_light_index.py` 係 Java 邏輯嘅 python 鏡像（`:38 short_type`、`:59 collect_items`、`:94 parse_recipe_json`），**冇** Java↔python cross-check（grep `.java` ＝ 0 命中）。只改 Java、唔改鏡像 ⇒ `tests/check_*.py` **照綠**，而實際行為已分家。

**影響**：P2 嘅唯一驗收（「1 問（SB 背包）」）照 v3 字面做，**大概率仍然 miss**，而且係「靜默 miss」（唔會紅、唔會 log）→ 下一個 session 會誤判成「4 行修法唔 work／真因唔喺度」，然後去改更貴嘅層。
**Flip condition**：(a) P2 明文加入「cache 版本／invalidate（刪 `jar-cache` 或讀 `"v"` 做 re-parse）」＋「同步 `tests/check_jar_light_index.py`」＋「`shortType` 截斷處理（或證 24 字夠）」，三樣都要寫落 phase plan；**或** (b) 提供一條能喺**已有 cache** 嘅機器上、無需人手清 cache 都測到改動生效嘅驗收步驟（附命令）。

### N2 【HIGH】「ns → 提供者」二元模型會把 pack 自加內容講成原廠；而截斷會反向誤判（無聲）

**已核實。** `kubejs` 樹同 jar 重疊 **27 個 ns**，規模最大嘅係 **`tetra`（kubejs 側 3,390 檔）**，另外 `kubejs` 795、`mvs` 114、`unusualprehistory` 101、`create` 33、`repurposed_structures` 27、`dimdungeons` 23、`ctov` 21、`graveyard` 18、`iceandfire` 15、`chestcavity` 7、`bygonenether` 6、`biomancy` 4、`irons_spellbooks` 4、`twilightforest` 3…

- v3 嘅模型係**命名空間級**（§2 P1「**命名空間**來源索引」；§1「冇『ns → 提供者』對應」）。
- 但 P3 要讀嘅 Tetra 六層（modules／improvements／repairs／synergies／schematics／materials）**全部**喺 `kubejs/data/tetra/**`（研究檔 §6 自己列出：`data/tetra/modules/single/archotech_void_scythe.json`、`data/tetra/improvements/archotech_void/*.json`、`data/tetra/repairs/single/archotech_scythe.json`、`data/tetra/schematics/single/…`、`data/tetra/synergies/single/…`）——而 `tetra` 係 **jar ns** ⇒ 呢批 pack 作者內容會被講成「原廠（jar）」。
- 反向亦錯：一旦 jar 掃描被 `contentIndex.maxJars`（600）或 `timeBudgetMs`（2000）截斷，jar ns 集**唔完整** ⇒ 原廠 ns 會**被當成 kubejs-only** ⇒ 系統主動同玩家講「呢件係本包自加」。呢個係錯方向、無 log、無紅燈（v3 冇定義截斷行為 → O18 只係表面解決）。
- P1 嘅驗收（「1 問（亞巴頓↔木棍來源）」）**正正落喺呢個洞**：亞巴頓條 item 由 `kubejs/startup_scripts/golden_age/ink_register.js` 註冊（ns `golden_age`＝kubejs-only ✅），但佢全部 Tetra 資料檔喺 ns `tetra`（jar ns）＋顯示名喺 `assets/golden_age_tetra`（kubejs-only）。即係同一件嘢一半「本包」一半「原廠」。

**Flip condition**：(a) 索引粒度由 ns 改成 **檔案／路徑級**（`jar vs kubejs/`，即 provenance 記「呢個 fact 邊個來源出」），並明寫「同一 ns 兩邊都有」時嘅答法；**或** (b) 保留 ns 級但加**第三態 `both`**＋規定「`both` 一律唔准講『原廠』」，並明文列出 27 個 `both` ns（含 `tetra`）＋寫明「截斷一律降級成 `unknown`，唔准降級成 `kubejs-only`」；**或** (c) 示範 P1/P3 對亞巴頓嘅 6 層逐層輸出（每層講得出邊個來源），證明 ns 級足夠。

### N3 【MED-HIGH】P2 嘅根因聲稱缺 runtime artifact 證據；現存 trace 指向另一個病（pack KubeJS 通道）

**已核實（有真 trace）＋部分懷疑。** plan §1 寫「**冇收 `base/addition/template`** ⇒ smithing 類（jar 內 96 條）只有 result 冇材料＝**SB 文字 miss 真因**」。逐項核：

- 數字：實測 `minecraft:smithing` ＝ **95 條**（唔係 96）；而 **SB 背包條 recipe 嘅 type 係 `sophisticatedbackpacks:smithing_backpack_upgrade`**（`data/sophisticatedbackpacks/recipes/netherite_backpack.json`：`addition=minecraft:netherite_ingot`、`base=sophisticatedbackpacks:diamond_backpack`、`result=…netherite_backpack`）——**唔屬嗰 95 條**。⇒ 「96 條 smithing ＝ SB miss 真因」呢句因果，數字上都接唔上。
- 真機 trace（本機實存）：`<instance>/packai/trace/ask-20260917-091302-sophisticatedbackpacks_netherite_backpack.jsonl`（56 行）：
  - `tool.call acquire` → `tool.result` ＝ **`""`（空）**
  - `tool.call jei_lookup` → **`""`（空）**
  - `tool.call render_recipe_cards` → `[card:1] 锻造台 钻石背包+下界合金锭+下界合金背包 -> sophisticatedbackpacks:netherite_backpack role=output`（**卡路徑正常**）
  - `display.body.final` `src=prose`：答案已寫「锻造台：用钻石背包＋下界合金锭升级而成…【来源】JEI 配方（锻造台）」
  - **全文 `[JAR]` 出現 0 次** ⇒ `JarLightIndex` facts **根本冇入過 prompt**（呢個 trace 亦解釋唔到「文字 miss」係邊一句）。
  - 而答案寫「本包索引没有收录它的掉落、宝箱、钓鱼、交易或任务等取得路径」——但 pack **自己**喺 `kubejs/server_scripts/utils/wares_model.js:137-138` 用 **1 個 `lightmanscurrency:coin_diamond`** 賣 `sophisticatedbackpacks:netherite_backpack`（黑市）。
- ⇒ **懷疑（未能收口）**：真正嘅「miss」可能係 **pack 自加通道（KubeJS server script 黑市）冇被索引**，而唔係 jar smithing 材料 key。若係噉，P2 修完 (`extractIngredients`) 對 SB 個案**零作用**，白食一個 phase。
**Flip condition**：提供 SK 當時投訴嘅**原句／截圖／trace 檔名**，指明「miss」係邊一句；若係「黑市／交易」類通道 ⇒ P2 要改成（或先做）pack 通道索引，並且 §1 要撤回「＝SB 文字 miss 真因」呢句因果。

### N4 【MED】其他 recipe type 嘅 key 名：P2 只補 3 個材料 key，但更大量嘅缺口係 **output key**

**已核實。** 用 Java 同款 predicate（`isRecipeEntry`：`data/` 開頭、含 `/recipes/`、`.json`、排除 `/advancements/`）實測 **12,798** 條 recipe，其中：
- **完全冇 output key**（`result`／`output` 皆無）而**根本冇 fact**：`create:cutting ×548`、`create:mixing ×445`、`create:milling ×226`、`create:crushing ×209`、`biomancy:decomposing ×184`、`create:filling ×126`、`create:splashing ×80`、`create:compacting ×62`、`create:emptying ×62`、`create:pressing ×44`、`create:deploying ×42`、`create:haunting ×24`…（`results` 係 plural）。另 `forge:conditional ×1,182` 亦冇 output（recipe 包喺 conditional 內）⇒ **呢批唔止「冇材料」，係一條 fact 都冇**。
- **有 output 但材料抽唔到**（現況）：`minecraft:crafting_shapeless ×384`、`stonecutting ×281`（其中大量係 `{"tag": …}`）、`crafting_shaped ×185`、`ars_nouveau:enchanting_apparatus ×120`（用 `reagent`／`pedestalItems`）、`ars_nouveau:glyph ×105`、`smithing ×95`…
- `collectItems` **明文跳過 tag**（`:562-563` `else if (o.has("tag")) { /* skip tags — not a concrete id */ }`）⇒ 用 tag 寫材料嘅 recipe 永遠抽唔到；而 `base/addition/template` 三個 key **只救 smithing 一族**（我實測 94/95 有救，1 個 lightmanscurrency 用 tag 仍然零）。
**攻擊**：v3 §1 用「只有 result 冇材料」做框架，令人以為缺口係單一（材料 key）；實際上同一支 `parseRecipeJson` 有兩族缺口，而 P2 只補一族。呢個唔係要 P2 加 scope，而係**plan 嘅問題陳述同 acceptance 唔對稱**：日後「SB 文字 miss」以外嘅同類投訴（例如 Create 機器）會再嚟一次。
**Flip condition**：§1 補一句「本階段只覆蓋 `<3 個材料 key>`；`results`/plural output、`forge:conditional`、tag-only 材料列為**已知未覆蓋**（附條數）」，並把 P2 嘅 acceptance 收窄到「smithing 一族」；或把 P2 擴成「output key 家族 ＋ 材料 key 家族」並重估成本。

### N5 【MED】P4 自我矛盾（詳見 §2.3）——交付物無顯示路徑

**已核實。** 摘要：parity 閘可以用「唔加 token／role key」繞過（真），但「零新 lang key」⇒ 玩家睇唔到；要玩家睇到就要 lang key，而 v3 自己被 §5「兩棵樹 lang key 數一致」（**無任何閘強制**，我 grep 過）逼到要問 SK 改 neoforge。
**Flip condition**：見 §2.3 (a)/(b)/(c)。

### N6 【LOW-MED】`collectItems` 40 件上限 ＋ 插入次序未 pin

**已核實。** `:537`／`:550` 用 `out.size() >= 40` 做上限（**先檢後加**，所以實際可到 40），而 `MAX_INGS=6`（`:58`）令 R fact 只保留**插入次序頭 6 個**（`:215` `subList(0, min(size, MAX_INGS))`）。對 smithing 唔係瓶頸（本身 ≤3 個 key），但：
- v3 只寫「加收 `base/addition/template`」，**冇 pin 插入次序**。若實作把三個 key 插喺 `ingredient`／`key` **之前**，會擠走原本頭 6 位嘅材料 → **令 shaped 配方材料變少**（回歸）。
- 40／6 呢兩個常數**同時**係 python 鏡像 `check_jar_light_index.py`（`MAX_INGS=6`、`MAX_FACTS_PER_ITEM=8`）嘅斷言對象 ⇒ 改 Java 唔改鏡像＝假綠（同 N1(3) 同一洞）。
**Flip condition**：phase plan 寫死「新 key 一律 append 落尾（唔准前置）」＋「改動同步鏡像」＋一個負控（改前／改後同一條 shaped 配方嘅材料清單唔准變短）。

### N7 【LOW】§4 仍未定義超限／超時行為（截斷）——O18 只係表面解決

**已核實（文字層）。** `contentIndex.maxJars=600`、`timeBudgetMs=2000` 有數值，但 §2／§3／§4 全文冇講超限時：截斷？記 log？標 `unknown`？而呢個決定直接控制 N2 嘅錯方向。
**Flip condition**：明文「掃唔完 ⇒ 索引狀態標 `partial`，`partial` 期間**唔准**答『本包自加』（只可答『未知來源』）＋記一行 ASCII log」。

### N8 【LOW】P0 前置清單仍未存在；`tests/check_*.py` baseline 冇 SHA（延伸 O5）

**已核實。** v3 §2 P0「逐檔 md5 清單交 SK」／§6「還原點：`.hermes/backups/2026-09-19_<phase>/`」。實況：工作樹自 R1 之後**零變**（`67 files changed, +4,448/−1,287`）；更麻煩嘅係 **33 個 `tests/` 檔（包括三個閘自己）未 commit** ⇒ §5「`tests/check_*.py` 零新增紅（baseline 121＝120 綠＋1）」係**對住一組無 SHA 嘅閘**量出嚟嘅數。
我本輪只可核 3 條（唯讀、唔寫檔）：`check_internal_label_parity` → `OK` RC=0；`check_jar_light_index` → `OK` RC=0；`check_settings_registry` → `OK: …46/46…` RC=0。（**冇**跑會寫檔嘅 `check_ask_display_leak.py`／`check_jar_contains_fix.py`，見 §6。）
**Flip condition**：SK 先 commit 一個「P0 驗收態」SHA（或開 worktree baseline），並喺 plan 寫死該 SHA；否則「零新增紅」唔可重跑。

---

## 4. 載重決定存活表（比分由表計出）

| LD | 載重決定 | 反方判定 | 依據 |
|---|---|---|---|
| LD1 | 交付物拆分：本檔降格 roadmap，每 phase 獨立 plan／review／部署 | **存活** | §2／§7；真解決 O7 |
| LD2 | P2 係「便宜、可即時回報」嘅第一個 phase（4 行修 `extractIngredients`） | **死** | N1（cache 無效化缺失＋type 截斷＋鏡像假綠）＋N3（SB 真 miss 可能唔喺呢層） |
| LD3 | ns→provider 索引足以分出「pack 自加 vs 原廠」 | **死** | N2（27 個 overlap ns；`tetra` 3,390 檔；截斷反向誤判；P3 六層全落喺 jar ns） |
| LD4 | P4 用 `AskResult.provenance` 欄位繞過 parity 閘（零新 lang key） | **死（機制活、交付物死）** | §2.3：閘確實繞得過，但「零 lang key」⇒ 玩家睇唔到 ⇒ 「來源標籤」冇交付面；而兩難係 §5 自設約束造出嚟 |
| LD5 | §1／§4 數字同上限「全部親手重測」 | **半死** | 對：28 ns／27 overlap／231 jar／0.286 s／13 檔／`scanModJars:442`／上限 `:55-56`；錯：`222`（實測 109/403）、`96`（實測 95）、`maxMb=4`（無依據）、packContentLang 4000/8MB（抄常數，實測 44 檔/2.0 MB） |
| LD6 | 驗收設計：每 phase ≤2 問 ＋ 零新增紅 ＋ 每新閘有 python 鏡像 | **半死** | 拆問 ✅；但 P2 驗收受 cache 影響（N1）、P4 「1 問＋trace」對住一個玩家睇唔到嘅欄位（N5）、既有鏡像無 cross-check（N1(3)）、baseline 無 SHA（N8） |

**載重決定 6 條：存活 1（LD1）、死 3（LD2／LD3／LD4）、半死 2（LD5／LD6）。**

### 反方估計比分：**正方 4 : 反方 6**

理由：
- **正方攞到嘅**：O7（拆分）真正落地，把 R1 最貴嘅「範圍」異議解除；O2／O4／O6／O13／O16／O11／O10(方向) 一併處理；`timeBudgetMs=2000` 我用獨立量測（0.53 s 全解析）**幫佢證實**、並主動撤回我一條預備嘅攻擊；P2 嘅修法方向（加 `base/addition/template`）我用 94/95 條實測確認**有效**；方向（pack 感知 ＋ 誠實）一字未變。
- **反方攞到嘅**：LD2／LD3 兩條新 HIGH 令「照 plan 開工就會中」——一個係**修完唔生效**（cache），一個係**會主動講錯話**（ns 誤判）。呢兩條唔係文筆，係設計／機制層；加上 P4 交付物無顯示面、§1 反駁數字錯、3 條閘未 commit 令 baseline 不可重跑。
- **未達 8:2 嘅原因**：LD2／LD3 需要**設計改動**（cache 版本化＋同步鏡像；provenance 粒度改檔案級或加 `both` 態）＋需要 SK 一句（P0 baseline SHA／P4 顯示面）。v3 一項都未做——但相對 R1（3:7），架構拆細已令比分實質回升，故 **4:6**，唔係原地踏步。

> 依 repo 契約：本輪屬 R2（第 2 輪，上限 3–4 輪未到）。**反方 flip condition 一旦被下一輪證據推翻，該輪即失效**，唔可以同一組異議重複計輪。

---

## 5. 最貴嘅 3 個未知（要咩才解得開）

1. **「SB 文字 miss」嘅原始驗收記錄（邊個 run、缺邊句）** — 我有 09-17 嘅 trace，但佢顯示嘅係 `acquire` 空、`[JAR]` 0 次、卡路徑正常、答案已講鍛造材料；而 pack 自己仲有 `wares_model.js:137` 黑市通道。**若真 miss 係 pack 通道，P2（＋§1 嘅因果）就係修錯層，整個「最便宜先行」嘅排序崩掉。** 解開方式：SK 貼當時嗰句／截圖，或講清「SB 背包文字 miss」係指邊一次問答。（成本：一句；影響：P2 整個 phase。）
2. **「pack 自加」判定嘅粒度應該以咩為準** — 27 個重疊 ns（`tetra` 3,390 檔）只有 pack 作者／SK 知邊啲係佢覆寫、邊啲係原廠 mod 自帶；而 P3 想讀嘅六層全部喺 `kubejs/data/tetra/**`。解開方式：SK 一句 predicate（例：「凡喺 `kubejs/` 樹下嘅檔＝本包」／「要 per-file」／「`both` 就答未知」）。
3. **「零新增紅」嘅 baseline SHA** — 3 個閘自己未 commit、工作樹 67 檔未 commit；任何「改完係唔係 regress」嘅比較今日**唔可重跑**。解開方式：SK 批准 commit（或開 worktree baseline）並把 SHA 寫入 plan。

---

## 6. 方法限制與本輪跑過嘅命令（誠實聲明）

**做咗（全部唯讀）**
- 讀真檔：`logic/JarLightIndex.java`（全檔重點段）、`logic/AskResult.java`、`client/service/AskService.java:406-520`、`logic/AskEngine.java:240-300,880-930`、`config/PackAiConfig.java:353/442/482-492/504/513`、`client/knowledge/ItemIndex.java:250-300`、`logic/AskReplyScrub.java:20-60,660-820`、`client/gui/AiAssistantScreen.java`（grep）；`tests/check_internal_label_parity.py`（全文）、`tests/check_dual_tree_sync.py`、`tests/check_settings_registry.py`、`tests/check_jar_light_index.py`、`check_card_tool_emission.py`／`check_recipe_cards_mode.py`／`check_ask_player_tool.py`（相關段）。
- 量測（python stdlib／`zipfile`，scratch 只落 `%LOCALAPPDATA%\Temp\`）：jar 列舉 179,114 entries＝0.286 s；全讀＋`json` 17,846 條＝0.53 s；12,798 條 recipe 逐 type 統計（output key／材料 key／tag）；95 條 smithing 逐條 key；`results` plural 2,086 條；ns 集合（224／55／28／27）；kubejs 檔數 9,144／352.7 MB；`assets/*/lang/*.json` 44 檔／2.0 MB；`archotech_void_scythe` 13 檔；`dlc_template_item_register.js` 各數字。
- 讀 runtime artifact（唯讀）：真機 trace `ask-20260917-091302-sophisticatedbackpacks_netherite_backpack.jsonl`（56 行，含 tool.call／tool.result／display.body.final）；`config/packai/jar-cache/manifest.json` ＋ shard `540aec233f22.json`；`config/packai-client.toml`（`scanModJars = true`）；`kubejs/server_scripts/utils/wares_model.js:137-138`。
- 跑 3 個**唔寫檔**嘅閘：`check_internal_label_parity.py` RC=0、`check_jar_light_index.py` RC=0、`check_settings_registry.py` RC=0。
- git：`git log -1`（HEAD＝`b157966`）、`git status --short`（116 行）、`git diff --stat`（67 files, +4448/−1287）、`md5sum` plan＝`ee50725cfd67…`。

**冇做（同 R1 一樣，唔當事實）**
- 冇跑 gradle／build／harness（禁令）；**冇跑**會寫檔嘅 `check_ask_display_leak.py`（`write_text`／`mkdir`）同 `check_jar_contains_fix.py`（`rmtree`／`mkdir`）⇒ §5 講嘅「120 綠＋1」我只核到 3 條綠，其餘**未經獨立驗證**。
- 冇改任何 repo 檔（唯一寫入＝本報告）；冇 commit、冇部署、冇開遊戲。
- 「SB miss 嘅原句」「SK 想嘅 provenance 粒度」「更大 pack 嘅掃描耗時」屬**懷疑／未知**（見 §5）。

---

## 7. 反方對「正方可能反駁」嘅預判（先寫落，等正方打）

| 正方會講 | 反方回應 |
|---|---|
| 「N1 唔算數，玩家可以喺設定頁熄／開一次 `scanModJars` 逼 re-scan」 | 熄／開只觸發 `reset()` 清記憶體（`JarLightIndex.java:97-103`），**唔會**清 `jar-cache`；`scanOneJar` 仍然憑指紋跳過 → 照樣舊資料。要證明有效，請喺**已有 cache** 嘅機器上跑一次（附命令＋輸出），唔好推理。 |
| 「N2 唔緊要，亞巴頓條 item id 嘅 ns 本身就係 `golden_age`（kubejs-only）」 | 噉就等於承認「Tetra 六層（modules／improvements／repairs／synergies）全部喺 `tetra` ns」會被講成原廠——而 P3 嘅交付物正是嗰六層。請直接示範 P3 對亞巴頓六層嘅逐層輸出文字（附 trace 行）。 |
| 「P4 有 UI 架，只係未指名」 | 我 grep 全 client 樹，`displaySrc` 零 UI consumer（只有 `AskService:406/448/469/510` 嘅 log／trace）；請指名 class＋行號，或者接受「trace-only 診斷欄位」嘅定位並修 §8。 |
| 「v3 §9 已經逐條回應 O1–O18，算解決」 | 「逐條回應」≠「逐條解決」：本輪 18 條只有 9 條真解決，7 條表面、2 條未解決（§1 表），而且**回應段落自己帶住 3 個錯數字**（§2.2）。 |
| 「`timeBudgetMs` 你都想打？」 | 我**撤回**：我獨立量測全解析 0.53 s，2,000 ms 站得住；我照規則撤回，並請正方同樣處理 §1 嘅 `222`／`96` 兩個數。 |
