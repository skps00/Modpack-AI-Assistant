# Plan：標準框架＝出返合成卡 ＋ 清走殘留否定句（v2）

- 日期：2026-09-18（v2 = 同日 code-anchor 核實審查後修訂；v1 內容除修訂點外全部保留）
- 觸發：SK 真機 3 問（09-18 17:26／17:27／18:22）＋SK 指出「but no cards...」＋SK 揀 **option 1**（一個細 plan 一次過修 1＋2＋3）
- 狀態：**待反方 review（gate 正方 ≥8 : 反方 ≤2）**；未開工、未改任何 code／jar
- 範圍：**只改 forge/1.19.2**；`neoforge/1.21.1` 唔郁
- v2 修訂來源：`docs/plans/2026-09-18-frame-standard-card-and-miss-line.audit.md`（code-anchor 審查：26 條事實聲稱 → OK 21／WRONG 4／未核 1）＋ 審查揭出 **9 條漏覆蓋路徑**（§0c）
- 可還原性：v1 全文 = `git show HEAD:docs/plans/2026-09-18-frame-standard-card-and-miss-line.md`（md5 `f3fd06493ea21375eae03ae728dd86ed`，已核）

---

## 0. 今日現狀（SK 真機睇到嘅三個矛盾）

以 09-18 18:22:31「下界合金 锤」（`tetra:modular_double`）為樣本（trace `ask-20260918-182231-tetra_modular_double.jsonl`），玩家實際睇到（**以下引文係 zh_cn 簡體，逐字照 trace**）：

1. 「怎麼來」正確出咗正面句：**「怎么来:合成台（有序合成）：橡木木板 + 木棍 -> modular double。摆放位置请以 JEI 为准。这是空白模组框架合成版本。（同族材料版本共 10 种，其他木板／石料版本一样可以合成）」**
2. **但冇任何配方卡**（`render.cards.final cardsOut=1 item=tetra:modular_double role=input`，只有一張「用作材料」嘅召喚祭壇卡）→ 玩家睇唔到擺位，只能自己開 JEI。
3. 同一答案尾段仍然印：**「目前没有这件物品的取得资料，暂时不确定怎么拿到。」**；【來源】行寫「**JEI（用途卡／框架合成卡已隐藏）**」→ 同第 1 點正面句直接打交。

同一情況在 17:26／18:19（擬態，MODIFIED）同 17:27（切石器，STANDARD）都見到卡被壓（`suppressedFrameOnly n=2`）。

> ⚠️ v1 用繁體寫呢兩句引文；真 trace 係簡體。驗收／grep 一定要用簡體子串。

---

## 0b. 證據（全部可重跑）

**A. trace** `packai/trace/ask-20260918-182231-tetra_modular_double.jsonl`（91 records，259,230 bytes）

- `display.body.final`：len 496；「怎麼來」段＝程式插入（正面句）；miss 句**獨立一行**（前後各一空行）；尾段含 miss 句。
- `model.reply.final`（**len 723**，latest.log 亦記 `raw reply chars=723`）**本身已含** miss 句（第 15 行）⇒ 係模型自寫，唔係程式尾插。
- `send.facts` 6 條：**1×len 0 ＋ 5×len 304**，**完全冇** miss 句 ⇒ 唔係 FACT 注入造成。
- `send.system` 6 條：**1×len 278**（intent 分類器）＋ **5×len 14593**（主 prompt）。14593 版本內已含政策 **#19**（「…若 FACT 写 unknown advancement gate／未索引／无取得路径…明说未知／未索引。」）同 **#23**（「…①标准框架（部件对得上标准配方）→必须讲合成台无序合成…（肯定句）；禁止对标准框架说「未收录」「定制版本」「不能当作取得方式」。…」）⇒ 指令已在，模型**冇跟**。⇒ 呢個要**結構性**保證（SK 規則：唔接受 LLM 選擇性行為）。
- ⚠️ 核實註：「#19／#23」係 plan 作者對 prompt 段落嘅索引，字樣 `#19`／`#23` 喺 trace **出現 0 次**；正文可取證（`未索引` ×10、`How-to-get` ×10、`标准框架` ×25）。政策原文存放喺 `assets/packai/lang/zh_cn.json`（lang 檔，本 plan 唔准郁）。

**B. latest.log（09-18，同一次 ask；真行號）**

```
L8965  frame-miss: branch kind=STANDARD acquireEmpty=false
L8966  frame-standard: branch entered recipe=in=[minecraft:oak_planks, minecraft:stick] out=tetra:modular_double
L8967  frame-standard: how-to-get replaced (STANDARD frame)
L8968  frame-standard: recipe line inserted (STANDARD frame)
L8969  frame-standard: final check present=true
L8942  renderCards item=tetra:modular_double role=output scannedCats=20 foundOutput=20 afterFilter=20
L8943  renderCards suppressedFrameOnly n=6
L8984  cards emitted=1        （只有召喚祭壇 input 卡）
```
MODIFIED／其他樣本 `suppressedFrameOnly n=2`：**L8185**（17:26:55）、**L8424**（17:27:40）、**L8772**（18:19:52）。

**C. 卡被壓嘅 code 機制（v2 已逐條對真 code 核實，行號為真值）**

| # | 位置（真行號） | 行為 |
|---|---|---|
| 1 | `logic/ModularFrameCards.java:18–34` `shouldDropFrameCard(dropId, primaryOutputId, inputUse, trailingOptional)` | dropId 非空 ＋ 非 input-use／trailing-optional ＋ 主產物 == dropId（`equalsIgnoreCase`，L33）→ **一律 drop**；**簽名冇 kind 參數** ⇒ 卡閘完全唔知 kind |
| 2 | `logic/AskToolEnv.java:35`（欄位）／`:61–82 offerEmission`／`:67` 呼叫／`:89–99 rejectFrameCard` | tool 發卡路徑讀 **env 欄位** `modularFrameDropId`（**唔係**即時叫 `loop.modularFrameDropId()`）；loop→env 抄寫點係 `logic/AskEngine.java:1678–1679` |
| 3 | `client/service/AskService.java:2643–2668` 定義；**呼叫點 6 個**：`:179`、`:397`、`:418`、`:2324`、`:2479`、`:2495` | catalog 路（179／397／418／2324）＋ AI 路（2479／2495）都要修；用 `AskService.modularFrameDropId`（定義 `:2636–2641`） |
| 4 | `logic/RenderRecipeCardsAskTool.java:163–175` | `if (emitted.isEmpty())`（L163）→ 上限早退（L164–166）→ `if (env.suppressedFrameOffers > 0)`（**L168**）→ log（L169–171）→ **L172** 回字串「框架合成卡已隱藏（非本工具取得途徑）」→ 模型照抄入【來源】 |
| 5 | `logic/AskEngine.java:351` `frameMatch = ModularFrameStandard.classifyDetailedInstalled(toolBuild)`；`:969–1000` STANDARD 分支 | **kind 喺呢度才算**（L351–352）；`missPin=false`（L363–365）；配方句插入 L978–991；`final check present` L996–999；卡閘（#1–#3）完全唔知 kind |
| 6 | `logic/ModularFrameStandard.java:220–223` `classifyInstalled(String)` | **已經存在**（v1 誤當要新加）；同族入口另有三個：`classifyDetailedInstalled:225`、`classify:233`、`classifyDetailed:255` |

⇒ 根因不變：**Plan A 只改咗文字層，卡閘仍按舊政策（「框架卡＝非本工具取得途徑」）一律隱藏** → 文字／卡／來源三者打交。

**D. 同類風險（順手 review 出嘅，唔屬於本 plan 範圍，只記錄）**

- 任何「程式決定性插入」同「模型自由散文」並存嘅位置，都有機會出現同款自相矛盾；本次只收窄到 STANDARD frame 呢一條（唔准擴範圍）。
- 已點名嘅**程式插入否定句**位置（本 plan 唔改，但 review 時要知）：`logic/HonestMiss.java:137` `ensureAskMissAcquirePlayerVisible`；`logic/AskEngine.java:932`（`forceHonestMiss` 定義，只 MODIFIED）＋`:940`（呼叫）；`:947–956` `looksLikeAcquireMissPin`／`ensureHowToGetBody(…, ReplyLang.obtainUnknown)`；`:961` `JeiInfoFacts.stripUnspecifiedMiss`（`JeiInfoFacts.java:409`）。
- `RenderRecipeCardsAskTool.java:172` 個中文字串係 tool-result（唔顯示玩家）；**唔屬於**「玩家文字必用 lang key」違規，但改動時唔准動其他字串。

---

## 0c. Code-anchor 審查揭出嘅 **9 條漏覆蓋路徑**（v2 新增；全部已對真行號）

| # | 路徑 | 為何要管（本 plan 如何處置） |
|---|---|---|
| G1 | `logic/AskCardFallback.java:441–452 collectOutputQuestIndices`／`:455–461 isFocusFrameOutput`；傳入點 `AskService.java:408`、`:2487` | **第三條放行路徑**。若 D1 改成「改寫 `modularFrameDropId()` 回值語義」，STANDARD 會回 `""` ⇒ `isFocusFrameOutput` 永遠 false ⇒ 框架 output 卡可經 ensureCards 塞返答案（無 log、同 tool 路徑卡可能爭位）。→ **v2 對策：D1 唔改 `modularFrameDropId(ItemStack)` 語義**（見 §2 D1），呢條路徑本階段**行為不變** |
| G2 | suppress 呼叫點有 **6 個**（見 C#3） | v1 只列 3 個（其中 L2487 更係錯認）。→ D1 要確認六條都 kind-aware；`:418`／`:2495` 屬 keyword-fallback 路徑，唔可以用 `suppressedFrameOnly` log 斷定 |
| G3 | `logic/AskLoopState.java:545–547 shouldSkipAutoEmit`（counter `:82`、`noteSuppressedFrameOffer :537`、`suppressedFrameOffers() :532`）；呼叫點 `AskService.java:367`、`:2450` | 卡閘第二道門。STANDARD 唔再被 drop 後呢道門**自然唔觸發**（唔需改），但佢係「STANDARD 出得返卡」嘅必要條件；驗收要知佢存在 |
| G4 | `client/jei/JeiRecipeCards.java:1812 coalesceMirrorEmission`；呼叫點 `RenderRecipeCardsAskTool.java:101`、`AskService.java:1659`、`:1754` | mirror 合併喺卡閘**之前** ⇒ D2 見到嘅 `matched` 已係合併後清單（今次 log 係 20→20，但唔可當必然） |
| G5 | `client/gui/AiAssistantScreen.java:833–869`（`cardStrip ? RecipeEmbed.indexBeforeSources : insertObtainClusterAt`，L867）＋ `logic/RecipeEmbed.java:707／716／1280` | **G1 目標「卡同文字相鄰、唔准孤兒卡」正正在此決定**，而呢兩個檔唔在 7 檔白名單 → 驗收要**抽查卡嘅落點**，唔可以假設自動正確 |
| G6 | `AskService.java:614 beginAskLoop` 簽名（無 toolBuild 參數）；呼叫點 `:299`、`:2416`；`toolBuild` 喺 `:276`／`:2399` 才算 | D1 要「同 AskEngine:351 逐字同一份文本」⇒ 時序／簽名要處理。**好事實**：`modularFrameDropId` 嘅呼叫點（`:631`）同 suppress 呼叫點（`:179`／`:2324`）**同 toolBuild 喺同一個 method 內**（已核）⇒ 提前算 toolBuild 即可，唔需要改 `beginAskLoop` 簽名 |
| G7 | `tests/check_card_emission_suppression.py:84–108` | 硬 assert 釘住：`shouldDropFrameCard(` 必須喺 suppress body 內、body 內**唔准** `equalsIgnoreCase`、`setModularFrameDropId(`／`shouldSkipAutoEmit()`（≥2）必須留、`AskEngine` `bindAskToolEnv(` ≥3、`AskLoopState` 三個 token、`RenderRecipeCardsAskTool` `suppressedFrameOnly`。→ D1 改法**唔准**整走上面任何 token |
| G8 | `tests/check_dual_tree_sync.py`（allowlist hygiene 仍 strict）／`check_dual_tree_diff_symmetry.py`；neoforge 孿生（`neoforge/.../AskService.java:165／374／393／2145／2292／2306`，定義 `:2412`） | neoforge `README_PAUSED.md` 存在 ⇒ forge-only 缺 twin 只 **WARN（exit 0）**，唔會紅、**亦唔會有人提醒** → 唔准為咗 forge-only 改動加 allowlist entry；neoforge 唔郁係刻意決定 |
| G9 | `logic/ModularFrameStandard.java:220 classifyInstalled` **已存在**；`:294 partsFromToolBuildText` 逐行掃 part 行、**唔分 block** | (a) 唔需要「新增單一入口」，只需改用；(b) **新風險**：`AskEngine:351` 傳入嘅係 `mergeExtrasToolBuild(jeiTarget, extras)`（`AskService.java:1033`，＝ `ModularToolScan.purposeLines(focus)` ＋ extras block）⇒ 有 `alsoSelected` extra 時，extras block 嘅 `part …` 行會混入 held map → 可能由 STANDARD 變 MODIFIED／UNKNOWN（既有行為，非本 plan 引入，但屬最貴未知之一，見 §6 R6） |

---

## 1. 目標（SK option 1）

- **G1**：STANDARD frame（空白框架＝jar 內對應合成配方嗰種）→ **要出返 1 張對應嘅合成台卡**（睇得到擺位），同「怎麼來」文字相鄰（唔准孤兒卡）。
- **G2**：STANDARD frame 答案 → **唔准**再出現「未收錄／不確定怎麼拿到」類否定句（由程式保證，唔靠 prompt）。
- **G3**：STANDARD frame 答案【來源】→ 唔准再寫「框架合成卡已隱藏（非本工具取得途徑）」。
- **G4**：**MODIFIED（特製版）／UNKNOWN → 100% 維持現狀**（唔出框架卡、老實講未收錄、來源照舊）。呢條係硬約束。

---

## 2. 設計（最小 diff；全部可 harness）

### D1 統一 kind-aware 卡閘（解 G1 前提；v2 重寫）

**決定：唔改 `AskService.modularFrameDropId(ItemStack)` 嘅回值語義**（避免 G1 副作用：該回值同時係 `AskCardFallback.ensureCards` 嘅 `dropFocusOutputId`，改咗會間接打開第三條放行路徑）。

- 新增純函式 `ModularFrameCards.dropIdFor(String focusId, ModularFrameStandard.Kind kind)`：
  - `STANDARD` → 回 `""`（＝唔 drop，卡可以出）
  - `MODIFIED`／`UNKNOWN`／`null`／`focusId` 空 → 回 `focusId`（＝**現狀**）
- **kind 來源＝單一入口**：兩個 call site 所在 method 內，將 `toolBuild` 計算（現 `AskService.java:276`／`:2399` `mergeExtrasToolBuild(jeiTarget, extras)`）**提前**到 suppress 呼叫（`:179`）之前（同一 method 內、純函式、無副作用），再 `ModularFrameStandard.classifyInstalled(toolBuild)` → `dropIdFor(cardFocusItemId(cardFocus), kind)`。
- 攞到嘅 dropId 取代原本喺 `AskService.java:631` `loop.setModularFrameDropId(...)` 餵入嘅值；**六條** display suppress 呼叫點（`AskService.java:179／397／418／2324／2479／2495`）改用同一 kind 判斷。
- `AskEngine.java:351` 嘅 classify **保持不動**（同一份 `toolBuild` 文本 ⇒ 兩處唔會 drift）。R1 要靠 harness 覆蓋。
- **已知限制（本階段明確唔改）**：`AskCardFallback` 路徑（`AskService.java:408`／`:2487`）仍傳原義 `modularFrameDropId()` ⇒ STANDARD 在 **keyword／非 AI 卡模式**下仍然被擋。要寫入 §5 驗收備註＋§6 R7。

### D2 STANDARD 只放行「對應 recipe 嗰 1 張」卡（解 G1 洗版）

- 新增純函式 `ModularFrameCards.isStandardRecipeCard(List<String> recipeIngredientIds, String recipeResultId, String cardPrimaryOutputId, List<String> cardInputIds)`：
  - 命中＝`cardPrimaryOutputId == recipeResultId` 且 `recipeIngredientIds ⊆ cardInputIds`（id 比對大小寫不敏感）
- 命中嗰張 → 放行；**同族 10 變體／mirror 卡照舊 drop**（文字已用「同族材料版本共 N 種」交代）。
- ⚠️ 輸入為 **G4 已合併**（`RenderRecipeCardsAskTool.java:101 coalesceMirrorEmission`）之後嘅 `matched`；唔准假設 20 張原封不動。
- ⚠️ 卡歸邊段由 **G5（`AiAssistantScreen.java:867` ＋ `RecipeEmbed`）** 決定，唔在白名單 ⇒ S5 必須**抽查卡嘅落點**（唔准孤兒卡）；用途卡（input-use）行為不變。

### D3 G3 自然解

- D1 之後，STANDARD ask 唔會再全部卡被 drop ⇒ `RenderRecipeCardsAskTool` 唔會再回「框架合成卡已隱藏…」字串 ⇒ 模型冇得照抄。
- **驗收要抽查（用簡體子串）**：STANDARD 真機 trace 嘅 `tool.result` 內**冇**「框架合成卡已隱藏」、【來源】行**冇**「已隐藏」（簡體；v1 用繁體寫係錯）。

### D4 G2 結構性清除殘留否定句

- `AskEngine` STANDARD 分支（`L969–1000` 之後、`ReplySources.ensure`（L963）之後）加一步：
  `body = AskReplyScrub.stripLangMissLine(body, lang)` —— **只刪整行 == `ReplyLang.askMissAcquirePlayer(lang)`（`ReplyLang.java:1187`）或 `ReplyLang.obtainUnknown(lang)`（`:464`）**（lang 驅動 ⇒ 零新增中文字面量）。
- **只喺 STANDARD 分支呼叫**（MODIFIED 唔准 strip）；保留配方句、來源行、用途段。
- 唔准用 regex 掃自由散文（避免誤刪）；模型其他自由措辭靠 D1 令 prompt／FACT 唔再指向「未收錄」去自然收斂。
- **v2 補**：程式插入否定句路徑（`AskEngine:940`／`:947–956`）喺 STANDARD 已被 L969 覆寫，**唔需要 strip**（要喺 plan 寫死，免實作歧義）。
- **harness 必須覆蓋變體**：整行相等（正例）＋行前後多空白＋全角句號＋行內有前綴（皆須**唔**刪）—— 否則模型寫變體時 G2 唔會 100%。

### D5 診斷 log（真機可核）

- STANDARD 分支新增兩行：`frame-standard: card kept recipe=… ref=…`、`frame-standard: miss line stripped=true/false`。
- **v2 補（資料來源）**：兩行都讀 **loop 層狀態**（`AskLoopState.suppressedFrameOffers()` L532／`cardEmissions()` L518），**唔准**讀 tool-env（env 每次 bind 重建）；refId 由 `AskToolEnv.offerEmission`（L61–82，`pendingEmissions.size()+1`）派發。

---

## 3. 改動白名單（實作只准改呢 7 個檔）

1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/ModularFrameCards.java`
2. `forge/1.19.2/src/main/java/com/skps9/packai/logic/ModularFrameStandard.java`（只用已存在嘅 `classifyInstalled`；**唔需要**新增入口）
3. `forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java`
4. `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java`
5. `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskReplyScrub.java`（只加 `stripLangMissLine`）
6. `forge/1.19.2/src/test/java/com/skps9/packai/logic/ModularFrameCardsCheck.java`
7. `forge/1.19.2/src/test/java/com/skps9/packai/logic/FrameStandardRecipeLineCheck.java`

**明確唔改（v2 新增，附理由）**

| 檔／範圍 | 理由 |
|---|---|
| `logic/ModularFrameCards` 以外嘅 `logic/AskToolEnv.java`、`logic/AskLoopState.java` | emission 閘讀 env 欄位、值由 loop 傳入 ⇒ 改 D1 唔需要動佢哋；`shouldSkipAutoEmit` 自然失效（G3） |
| `logic/AskCardFallback.java` | 本階段保留 keyword 路徑現狀（§2 D1 已知限制）；改佢 = 擴範圍 |
| `client/gui/AiAssistantScreen.java`、`logic/RecipeEmbed.java` | 卡泊位路徑（G5）；本 plan 只**驗收抽查**，唔改版面 |
| `logic/RenderRecipeCardsAskTool.java` | D3 靠「唔再被 drop」自然解，唔需要改；字串／log 一律唔動 |
| prompt／lang 檔（含 `assets/packai/lang/zh_cn.json` 內政策 #19／#23） | **唔加 key**、唔改字 |
| 卡版面 rendering／trace 事件名／renderCards digest 格式 | 唔准郁 |
| `tests/check_*.py`（python 閘） | 唔准為咗綠而改 assert；G7 列出**唔准破壞**嘅 token |
| `neoforge` 樹 | PAUSED；G8 講明唔准為 forge-only 改動加 dual-tree allowlist entry |
| jar／部署 | 實作階段唔部署、唔 commit code（等 bump `0.2.2`） |

---

## 4. 還原點（實作前必做）

- 檔備份：`.hermes/backups/2026-09-18_frame_standard_card/`（7 檔 md5 ＋ `git rev-parse HEAD`）
- plan v1 全文備份：`git show HEAD:docs/plans/2026-09-18-frame-standard-card-and-miss-line.md`（md5 `f3fd06493ea21375eae03ae728dd86ed`）＋ 本審查檔 `.audit.md`
- 現況 jar：部署版 `06b5b129a114`（`mods/` 內只有 1 個 `packai-0.2.3+mc1.19.2-forge.jar`，已核 sha256 頭 12；原 backup `%TEMP%\deploy_backup_20260918_0712\`，**未獨立核**）
- 回滾：單 commit revert（或 copy 返 backup）→ compile → 需要時重部署舊 jar。**實作階段唔部署、唔 commit code**（跟 SK 定案：等 bump `0.2.2`）。

---

## 5. 驗收標準（逐項跑，唔准靠印象）

- **S1** harness `ModularFrameCardsCheck`（現 79 行，3 方法：`dropCoreCases`／`skipAutoEmit`／`crossLayerSharedCore`）：擴充 `dropIdFor` 三分支；`isStandardRecipeCard` 正例＋反例（變體／mirror／input-use 唔准放行）。
- **S2** harness `FrameStandardRecipeLineCheck`（現 245 行、12 項）：擴充 strip 斷言——整行相等（正例）＋行內前綴／全角句號／前後空白（**唔准**刪）＋MODIFIED body 呼叫不到 strip。
- **S3** python：`check_modular_frame_standard.py`、`check_frame_standard_recipe_line.py`、`check_card_emission_suppression.py` 全綠；全套 `tests/check_*.py` 相對 baseline **零新增紅**。
  - **baseline（v2 更新，2026-09-18 親跑實測）＝ 120 檔 0 紅（TOTAL=120 FAIL=0）**；`check_ask_display_leak.py` 單跑 **rc=0**。
  - ⚠️ v1／HANDOFF 寫嘅「118 綠 ＋ 1 已知紅（`check_ask_display_leak` RC=2）」**已過時，唔准再當 baseline**。
- **S4** `compileJava compileTestJava` BUILD SUCCESSFUL；3 個 lang 檔 key 數不變（**513／513／513**，已核）；改動 Java 檔新增 CJK literal = 0。
- **S5** 真機（build＋`mc_mod_deploy_jar.py --jar` 部署後，SK 出 2 問）：
  - 木錘（STANDARD）→（a）有「取得方式」卡**且落點同文字相鄰（唔准孤兒卡，G5 抽查）**（b）尾段冇簡體 miss 句「目前没有这件物品的取得资料」（c）來源行冇「已隐藏」
  - 擬態（MODIFIED）→ 三項**維持現狀**
  - 字串比對一律用**簡體 zh_cn**（plan §0 引文已改）
- **S6** cardsOut ≤ 上限、用途卡仍在（唔准搶位）。
- **S7（v2 新增）** 靜態閘 token 逐項核（G7）：`check_card_emission_suppression.py` 84–108 全部 assert 仍綠；`RenderRecipeCardsAskTool` 內 `suppressedFrameOnly` 及 L172 字串仍在（本 plan 唔刪）。
- **S8（v2 新增）** 六條 suppress 呼叫點逐條回歸（G2）：AI 路（2479／2495）＋catalog 路（179／397／418／2324）行為一致；keyword 路徑（408／2487）**維持現狀**並記錄（R7）。

---

## 6. 未解／風險（要 review 反方逐條打）

- **R1** classify 兩處輸入唔一致 → kind 分歧。v2 已收窄：兩處都用同一份 `toolBuild`（提前計算）；harness 要覆蓋「同一輸入 ⇒ 同一 kind」。
- **R2** RecipeCard 匹配靠 input ids；若 JEI 卡冇 input id ⇒ 退回「只放行 1 張 output 對應卡」，要 harness 覆蓋。
- **R3** 出卡後會唔會擠走用途卡／超 `MAX_CARD_EMISSIONS`（`AskToolEnv.java:70`）＋ 撞 G4 coalesce 後嘅張數。
- **R4** MODIFIED 回歸（硬約束 G4）——v2 因「唔改 `modularFrameDropId` 語義」而降險，但六條呼叫點改動仍要逐條驗。
- **R5** UNKNOWN（fail-open）保持現狀＝照舊唔出卡。
- **R6（v2 新增，最貴未知之一）** `AskEngine:351` 嗰份 `toolBuild` 係 `mergeExtrasToolBuild`（＝focus 嘅 `[TOOL_BUILD]` ＋ `--- alsoSelected: … ---` extras block）；而 `ModularFrameStandard.partsFromToolBuildText`（`:294`）**逐行掃、唔分 block** ⇒ 有 `alsoSelected` 時 extras 嘅 `part …` 行會混入 held map → classify 可能變 MODIFIED／UNKNOWN（既有行為，唔係本 plan 引入）。今次樣本冇 extras 故未中。**要 decide**：本 plan 內修（要動 `ModularFrameStandard`，在白名單內）定另開 plan。
- **R7（v2 新增）** `AskCardFallback` 路徑（`AskService:408`／`:2487`）唔改 ⇒ keyword／非 AI 卡模式下 STANDARD 仍然被擋（已知限制，要寫入 HANDOFF 同驗收備註）。
- **R8（v2 新增）** baseline 數字錯（v1 118＋1 紅 vs 真 120／0）⇒「零新增紅」判準必須用 120／0 重寫，否則第一次跑就會誤判 regress。

---

## 7. 實作方式

- 一律 **cursor-agent**，寫死禁令（唔准 commit／唔准動範圍外檔／唔准 hot-copy jar／唔准改 prompt／**唔准改 `tests/check_*.py`**／**唔准加 dual-tree allowlist entry**）。
- Hermes 只做：plan、派工、**親驗**（S1–S4＋S7 自己跑，S5／S8 讀 trace／latest.log 逐字核）。
- 派工前先修 plan §0b C 表（已修）——該表係 cursor 嘅行號依據。

---

## 附錄：v1 → v2 改動清單（本審查 4 條 WRONG 全數修正）

| # | v1 內容 | v2 修正 |
|---|---|---|
| 1 | §0b C#2「`AskToolEnv.offerEmission → rejectFrameCard`…用 `loop.modularFrameDropId()`」 | 改為「讀 **env 欄位** `AskToolEnv.java:35`，用喺 `:91`；loop→env 抄寫點 `AskEngine.java:1678–1679`」 |
| 2 | §0b C#3「`suppressModularFrameCards` L179／L397／L2487」 | 改為「定義 `:2643`；呼叫點 **6 個** `:179／397／418／2324／2479／2495`」；L2487 更正為 `ensureCards` 之 `modularFrameDropId` 傳參 |
| 3 | §0 引文用繁體（「目前沒有這件物品的取得資料…」／「框架合成卡已隱藏」） | 改為 **zh_cn 簡體逐字**（「目前没有这件物品的取得资料」／「已隐藏」）＋ 加註 |
| 4 | §5 S3 baseline「118 綠 ＋ 1 已知紅」 | 改為 **120 檔 0 紅**（親跑實測）＋ 加警告 |
| 5 | D1「改 `AskService.modularFrameDropId`」 | 重寫：**唔改函式語義**（避 G1 副作用），改喺 call site 用提前計算嘅 `toolBuild` → `dropIdFor` |
| 6 | §3 白名單無「唔改」清單 | 新增「明確唔改」表（含 G7／G8 邊界） |
| 7 | §5 只有 S1–S6 | 新增 S7（靜態閘 token）、S8（六條呼叫點回歸）＋ S5 加落點抽查／簡體字串 |
| 8 | §6 R1–R5 | 新增 R6（extras block 混入 held map）、R7（AskCardFallback 路徑未覆蓋）、R8（baseline 數字） |
| 9 | 無 code-anchor 覆蓋章節 | 新增 §0c（G1–G9）＋ §0b C#6（`classifyInstalled` 已存在） |
