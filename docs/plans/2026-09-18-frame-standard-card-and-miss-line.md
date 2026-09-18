# Plan：標準框架＝出返合成卡 ＋ 清走殘留否定句（v4.1）

- 日期：2026-09-18（**v4 = R1＋R2 反方 review 後重寫；v4.1 = R3 polish 3 條**；v1／v2／v3 可還原，見檔尾）
- 觸發：SK 真機 3 問（09-18 17:26／17:27／18:22）＋SK 指出「but no cards...」＋SK 揀 **option 1**（一個細 plan 一次過修 1＋2＋3）
- 狀態：**✅ review gate 達標（R3 ＝ 正方 8 : 反方 2）→ 可開工**；未改任何 code／jar
- 範圍：**只改 forge/1.19.2**；`neoforge/1.21.1` 唔郁
- Review 記錄：**R1＝正方 3 : 反方 7**／**R2＝正方 7 : 反方 3**（3 條新 HIGH 全部接受）／**R3＝正方 8 : 反方 2（達標）**（報告 `docs/plans/reviews/2026-09-18_frame-standard-card-R{1,2,3}-opposing.txt`）→ 逐條回應見 **§8**
- 舊版本還原：v3 md5 `67d44f0e3ca1548d0104ace51dd1b941`（＝ commit `c5e2813`）／v2 md5 `376c733a6f4846a09673aaa47d20429c`（＝ `8974d9f`）／v1 md5 `f3fd06493ea21375eae03ae728dd86ed`（＝ `533f96d`）

---

## 0. 今日現狀（SK 真機睇到嘅三個矛盾）

以 09-18 18:22:31「下界合金 锤」（`tetra:modular_double`）為樣本（trace `ask-20260918-182231-tetra_modular_double.jsonl`），玩家實際睇到（**引文係 zh_cn 簡體，逐字照 trace**）：

1. 「怎麼來」正確出咗正面句：**「怎么来:合成台（有序合成）：橡木木板 + 木棍 -> modular double。摆放位置请以 JEI 为准。这是空白模组框架合成版本。（同族材料版本共 10 种，其他木板／石料版本一样可以合成）」**
2. **但冇任何配方卡**（`render.cards.final cardsOut=1 item=tetra:modular_double role=input`，只有一張「用作材料」嘅召喚祭壇卡）→ 玩家睇唔到擺位，只能自己開 JEI。
3. 同一答案尾段仍然印：**「目前没有这件物品的取得资料，暂时不确定怎么拿到。」**；【來源】行寫「**JEI（用途卡／框架合成卡已隐藏）**」→ 同第 1 點正面句直接打交。

同一情況在 17:26／18:19（擬態，MODIFIED）同 17:27（切石器，STANDARD）都見到卡被壓（`suppressedFrameOnly n=2`）。

> ⚠️ 驗收／grep 一律用**簡體 zh_cn** 子串（繁體寫法唔會命中）。

---

## 0b. 證據（全部可重跑）

**A. trace** `packai/trace/ask-20260918-182231-tetra_modular_double.jsonl`（91 records，259,230 bytes）

- `display.body.final`：len 496；「怎麼來」段＝程式插入（正面句）；miss 句**獨立一行**（前後各一空行）；尾段含 miss 句。
- `model.reply.final`（len 723）**本身已含** miss 句（第 15 行）⇒ 係模型自寫，唔係程式尾插。
- `send.facts` 6 條：**1×len 0 ＋ 5×len 304**，**完全冇** miss 句 ⇒ 唔係 FACT 注入造成。
- `send.system` 6 條：**1×len 278**（intent 分類器）＋ **5×len 14593**（主 prompt）；政策段已在（`未索引` ×10、`How-to-get` ×10、`标准框架` ×25）⇒ 模型**冇跟** ⇒ 要**結構性**保證（SK 規則：唔接受 LLM 選擇性行為）。

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

- **R1 新增證據（反方獨立跑，我覆核過行號）**：`L8915` `dedupeMirror no-drop but shared-output bucket c|tetra:modular_double#1; -> [ granite#2;stick#2;o:tetra:modular_double#1;… , spruce_planks… , cobblestone… , oak_planks… , diorite… , dark_oak_planks… , birch_planks… , andesite… , jungle_planks… , acacia_planks ]` ⇒ **同一出物有 10 個材料變體**，`afterFilter=20` 係 `coalesceMirrorEmission`（`RenderRecipeCardsAskTool.java:101`→`:110-113`）**之後**嘅數。
- **R1 新增證據（gate 時序）**：`L8858 [18:22:33.844] frame-classify: kind=STANDARD recipeIndex=2`（`AskEngine:351`）vs `L8943 [18:22:39.451] suppressedFrameOnly n=6`（tool loop 內）⇒ classify 比卡閘**早 5.6 秒**，「提前算 kind 餵 loop」方向**唔係 no-op**。
- MODIFIED／其他樣本 `suppressedFrameOnly n=2`：**L8185**（17:26:55）、**L8424**（17:27:40）、**L8772**（18:19:52）。

**C. 卡被壓嘅 code 機制（行號全部由 R1 反方逐條 re-grep 覆核，正確）**

| # | 位置（真行號） | 行為 |
|---|---|---|
| 1 | `logic/ModularFrameCards.java:18–34` `shouldDropFrameCard(dropId, primaryOutputId, inputUse, trailingOptional)` | dropId 非空 ＋ 非 input-use／trailing-optional ＋ 主產物 == dropId（`equalsIgnoreCase`，L33）→ **一律 drop**；**簽名冇 kind 參數** ⇒ 卡閘完全唔知 kind |
| 2 | `logic/AskToolEnv.java:35`（欄位）／`:61–82 offerEmission`／`:89–99 rejectFrameCard` | tool 發卡路徑讀 **env 欄位** `modularFrameDropId`；loop→env 抄寫點＝`logic/AskEngine.java:1670–1681`（`:1677–1678` 抄 dropId） |
| 3 | `client/service/AskService.java:2643–2668` 定義；**呼叫點 6 個**：`:179`、`:397`、`:418`、`:2324`、`:2479`、`:2495` | catalog 路（179／397／418／2324）＋ AI 路（2479／2495）都要修；內部 `:2647` 自己叫 `modularFrameDropId`（定義 `:2636–2641`） |
| 4 | `logic/RenderRecipeCardsAskTool.java:163–175` | `if (emitted.isEmpty())`（L163）→ 上限早退 → `if (env.suppressedFrameOffers > 0)`（**L168**）→ log（L169–171）→ **L172** 回字串「框架合成卡已隱藏（非本工具取得途徑）」→ 模型照抄入【來源】 |
| 5 | `logic/RenderRecipeCardsAskTool.java:101`（coalesce）／**`:134–141` 截斷**（`PER_CALL_CAP=6`，定義 `:22`；output 路 `:140 subList(0,6)`）／`:152–162` 逐張 `offerEmission` | **cap 發生喺任何 card-gate 之前** ⇒ 只放行「1 張」必須喺 cap 上游做（R1 CRITICAL，已獨立核實行號） |
| 6 | `logic/AskEngine.java:351` `classifyDetailedInstalled(toolBuild)`；`:969–1000` STANDARD 分支 | kind 喺呢度才算；`missPin=false`（L363–365）；配方句插入 L978–991；卡閘（#1–#3）完全唔知 kind |
| 7 | `logic/ModularFrameStandard.java:34–63 FrameRecipe(parts, ingredientItemIds, resultItemId, shapeless, variantCount)`；`:211 recipeAt(index)`；`:220 classifyInstalled`；`:225 classifyDetailedInstalled`→`Match(kind, recipeIndex)`；`:294 partsFromToolBuildText` | `modular_double` 條目＝`ingredientItemIds=[minecraft:oak_planks, minecraft:stick]`、`resultItemId=tetra:modular_double`、`variantCount=10` |
| 8 | `logic/RecipeCard`（真 record，`RecipeCard.java:20–58`） | 欄位＝`grid／inputs／catalysts／outputs`（`List<ItemStack>`）＋ `primaryOutputId()` 等；**冇** `cardInputIds`（v2 用嘅名係自創，已撤回） |
| 9 | `logic/AskLoopState.java:520 cardEmissions()`（v2 寫 `:518`，更正）／**`:528–530 setModularFrameDropId`**（R2 再更正：v3 寫 `:526–528`）／`:532 suppressedFrameOffers()`／`:537 noteSuppressedFrameOffer`／`:545–547 shouldSkipAutoEmit` | `shouldSkipAutoEmit = suppressedFrameOffers>0 && cardEmissions.isEmpty()`；auto-emit 出口＝`AskService.java:367–368`／`:2450–2451`（`autoEmitCatalogCards`，`:1586 cap=4`） |
| 10 | `tests/check_card_emission_suppression.py:70–110`（硬 assert）／`tests/check_ask_card_fallback.py:1360–1385` | reject body 必須含 `ModularFrameCards.shouldDropFrameCard(`；`offerEmission` 簽名不變；suppress body 必須含 `shouldDropFrameCard(` 且**唔准**出現 `equalsIgnoreCase`；`:1366–1379` 用 regex 釘死兩條 ask path 嘅 `ensureCards(scrubbed, cardsCollected|collected, null, modularFrameDropId(cardFocus))` 4 參形式 |

⇒ 根因不變：**Plan A 只改咗文字層，卡閘仍按舊政策（「框架卡＝非本工具取得途徑」）一律隱藏** → 文字／卡／來源三者打交。

**D. 同類風險（唔屬於本 plan 範圍，只記錄）**

- 任何「程式決定性插入」同「模型自由散文」並存嘅位置都有同款風險；本次只收窄到 STANDARD frame 呢一條（唔准擴範圍）。
- 已點名嘅**程式插入否定句**位置（本 plan 唔改）：`logic/HonestMiss.java:137`；`logic/AskEngine.java:932`（`forceHonestMiss` 定義，只 MODIFIED）＋`:940`（呼叫）＋`:947–956`（`ensureHowToGetBody(…, ReplyLang.obtainUnknown)`）；`:961` `JeiInfoFacts.stripUnspecifiedMiss`（`JeiInfoFacts.java:409`）。
- `RenderRecipeCardsAskTool.java:172` 個中文字串係 tool-result（唔顯示玩家）；**唔屬於**「玩家文字必用 lang key」違規，但改動時唔准動其他字串（python 閘釘住佢要存在）。

---

## 0c. 相關 code 路徑清單（v2 嘅 G1–G9 保留；G6 已更正）

| # | 路徑 | 本 plan 如何處置 |
|---|---|---|
| G1 | `logic/AskCardFallback.java:441–452 collectOutputQuestIndices`／`:455–461 isFocusFrameOutput`；傳入 `AskService.java:408`、`:2487` | 第三條放行路徑。**v3 唔改 `modularFrameDropId()` 回值語義**（呢條路徑行為不變）；keep-1 只用新 allow-list 表達，唔靠改回值 |
| G2 | suppress 呼叫點 **6 個** | v3 全部改用 keep-aware 版本（§2 D1 第 4 點） |
| G3 | `AskLoopState:545–547 shouldSkipAutoEmit`；呼叫點 `AskService:367`／`:2450` | STANDARD 唔再被 drop ⇒ `suppressedFrameOffers=0` ⇒ 呢道門唔觸發；**但 auto-emit 出口同時打開**（R1 MEDIUM，見 §6 R10），要驗收覆蓋 |
| G4 | `client/jei/JeiRecipeCards.java:1812 coalesceMirrorEmission`；呼叫點 `RenderRecipeCardsAskTool.java:101`、`AskService.java:1659`、`:1754` | D2 見到嘅 `matched` 已係合併後清單（今次 20→20，唔可當必然） |
| G5 | `client/gui/AiAssistantScreen.java:833–869`（`RecipeEmbed.indexBeforeSources`，L867）＋ `logic/RecipeEmbed.java:707／716／1280` | 卡泊位決定（唔准孤兒卡），兩個檔唔在白名單 → S6 要**抽查落點** |
| G6 | **（v2 假 claim，已撤回）** v2 寫「`:631` 同 suppress 呼叫點喺**同一個 method** 內（已核）⇒ 唔需要改 `beginAskLoop` 簽名」 | **事實錯**：`:631` 喺 `beginAskLoop`（`:614–649`，由 `:299`／`:2416` 呼叫），`:179` 喺 `runAsk`（`:128–461`）、`:2324` 喺第二個 ask method（`:2276–2509`）。**v3 明寫落地方案 (b)**：`beginAskLoop` 簽名唔改，改喺 `:299`／`:2416` **返回後即場 `askLoop.setFrameStandardKeep(...)`**（public setter 先例：`AskLoopState:528–530`） |
| G7 | `tests/check_card_emission_suppression.py:70–110` | 硬 assert 清單見 §0b C#10；v3 設計**保留全部 token** |
| G8 | `tests/check_dual_tree_sync.py`／`check_dual_tree_diff_symmetry.py`；neoforge 孿生（`AskService.java:165／374／393／2145／2292／2306`，定義 `:2412`） | neoforge PAUSED ⇒ forge-only 只 **WARN（exit 0）**；**唔准**加 allowlist entry |
| G9 | `logic/ModularFrameStandard.java:294 partsFromToolBuildText` 逐行掃、**唔分 block** | 有 `alsoSelected` extras 時 extras 嘅 `part …` 行會混入 held map → 可能由 STANDARD 變 MODIFIED／UNKNOWN（**既有行為**，非本 plan 引入）→ §6 R6（本階段唔修，明文記錄） |
| G10（R1 新增） | `logic/AskJeiHints.java:110–139 scrubAbsenceClaimsWhenCards`（由 `AskResult.withRecipeCards` → `:84–97` 觸發） | 今日 `hasCards=false` 唔觸發；**D1 之後 STANDARD 有卡 ⇒ 會開始刪 `looksLikeAbsenceClaim` 行**（既有行為，非本 plan 新增）→ **落點＝本表記錄＋§6 R12**（R2 指出 v3 寫嘅「§2 D4 註／S6 抽查」係 ghost 落點，已刪）。R1 已查證：`looksLikeAbsenceClaim:39–78` **唔覆蓋**目標 miss 句 ⇒ D4 唔係重複施工 |
| G11（R1 新增） | `tests/check_ask_card_fallback.py:1366–1379` regex 釘死 4 參 `ensureCards(..., modularFrameDropId(cardFocus))` | 改 `modularFrameDropId()` 回值語義會令呢個**唔准改**嘅閘紅 ⇒ 反證「唔改語義」係**被測試強制**嘅正確選擇（v3 引用為依據，唔止靠推理） |

---

## 1. 目標（SK option 1）

- **G1**：STANDARD frame → **要出返 1 張對應嘅合成台卡**（睇得到擺位），同「怎麼來」文字相鄰（唔准孤兒卡）；**模型 digest 同玩家顯示都要係同一張**（唔准「模型見 N 張／玩家見 1 張」）。
- **G2**：STANDARD frame 答案 → 唔准再出現「未收錄／不確定怎麼拿到」類否定句。**保證範圍明文界定**：程式保證覆蓋「整行（去前後空白）等於 `ReplyLang.askMissAcquirePlayer(lang)`／`obtainUnknown(lang)`」嘅情況（＝今日真機樣本）；其他自由變體唔喺程式保證內（§6 R11 記錄未覆蓋情形）。
- **G3**：STANDARD frame 答案【來源】→ 唔准再寫「框架合成卡已隱藏（非本工具取得途徑）」。
- **G4**：**MODIFIED（特製版）／UNKNOWN → 100% 維持現狀**（唔出框架卡、老實講未收錄、來源照舊）。硬約束。

---

## 2. 設計（最小 diff；全部可 harness）

### D1 kind 單一來源 ＋ 三個落腳點（v3 重寫，回應 R1 CRITICAL ×2）

1. **算 kind（兩個 ask method 內，緊接提前嘅 toolBuild）**
   - `AskService`：`final String toolBuild = mergeExtrasToolBuild(jeiTarget, extras);`（現 `:276`／`:2399`）**提前**到 `suppressModularFrameCards`（`:179`）／對應位（`:2324` 之前）；兩個 method 內嘅 `jeiTarget`（`:142`／`:2289`）同 `extras`（`:160`／`:2292`）**已存在**（R1 已核），`mergeExtrasToolBuild` 無 I/O／無 log／無狀態（`AskService:1033–1043`）⇒ 提前計算**零副作用**。
   - 即場：`ModularFrameStandard.Match fm = ModularFrameStandard.classifyDetailedInstalled(toolBuild);`
     `final ModularFrameStandard.FrameRecipe keep = (fm != null && fm.kind() == Kind.STANDARD) ? ModularFrameStandard.recipeAt(fm.recipeIndex()) : null;`
     **唔用 `classifyInstalled`**（佢只回 Kind，冇 `recipeIndex` ⇒ D2 冇資料；R1 HIGH #3 接受）。
   - `AskEngine:351` 嘅 classify **保持不動**（同一份 `toolBuild` 文字 ⇒ 兩處唔會 drift；R1 要靠 harness 覆蓋）。
2. **餵 loop（更正 G6）**：`beginAskLoop(...)` 簽名**唔改**；喺 `:299`／`:2416` 呼叫**返回之後**加入：
   `askLoop.setFrameStandardKeep(keep);`（同場已有 `askLoop.setRecipeCardLines(...)`／`setCatalogCards(...)` 先例）
   - `AskLoopState` 新增：`private String frameStandardKeepOutputId = "";`、`private List<String> frameStandardKeepInputIds = List.of();`、`public void setFrameStandardKeep(FrameRecipe r)`（null → 兩個都清空）、`public String frameStandardKeepOutputId()`、`public List<String> frameStandardKeepInputIds()`。
   - `AskEngine.bindAskToolEnv`（`:1670–1681`）新增兩行：把 loop 兩個欄位抄落 env（同 dropId 抄寫同位置）。
3. **tool 卡閘 allow-list**：`AskToolEnv` 新增兩個欄位（`public String frameStandardKeepOutputId = "";`／`public List<String> frameStandardKeepInputIds = List.of();`）；`rejectFrameCard` 改簽名為 `private boolean rejectFrameCard(RecipeCard card)`：
   - 第一句：`if (ModularFrameCards.isStandardKeepCard(card.primaryOutputId(), cardInputIds(card), frameStandardKeepOutputId, frameStandardKeepInputIds)) return false;`（＝放行；**純字串 API**，RecipeCard→id 嘅映射由 caller 做，見 D2「資料映射」）
   - 之後**保留**現有 `if (!ModularFrameCards.shouldDropFrameCard(modularFrameDropId, card.primaryOutputId(), card.isInputUse(), card.isTrailingOptional())) return false;` ＋計數（`suppressedFrameOffers++`／`loop.noteSuppressedFrameOffer()`）
   - `offerEmission` 內 `rejectFrameCard(card.primaryOutputId(), card.isInputUse(), card.isTrailingOptional())` → `rejectFrameCard(card)`（**pred → refId → add 次序不變**；G7 token 全保留）。
   - **`modularFrameDropId` 語義同 loop 值唔改**（STANDARD 仍然係 focusId）⇒ G1 路徑＋auto-emit 保護＋4 參 `ensureCards` 閘全部不受影響。
4. **display 卡閘**：`AskService.suppressModularFrameCards(ItemStack focus, List<RecipeCard> cards)` → 加第三參 `ModularFrameStandard.FrameRecipe keep`（**只加參數**，`tests/check_card_emission_suppression.py:91` 用前綴抓 body，相容）；body 邏輯：
   - `keep != null` → 用 `ModularFrameCards.keepOnlyStandardRecipeCard(cards, keep.resultItemId(), keep.ingredientItemIds())`（**比較邏輯全部住喺 `ModularFrameCards`**，suppress body 內**唔准**出現 `equalsIgnoreCase`）
   - `keep == null` → **現狀**（`shouldDropFrameCard` 逐張判）
   - 6 條呼叫點（`:179／:397／:418／:2324／:2479／:2495`）全部傳 `keep`（同一 method 內嘅 local 變數；`:397／:418` 喺 `runAsk` 內部、`:2479／:2495` 喺第二個 method 內部）。
   - `AskCardFallback` 路徑（`:408／:2487`）**維持現狀**（已知限制，§6 R7）。

### D2 唯一放行點喺 **cap 之前**（v4：加守衛 —— 解 R2 HIGH #3）

- **純函式 API（零 MC 型別，住喺 `ModularFrameCards`）**：`ModularFrameCardsCheck` 檔頭明文寫「No AskToolEnv／RecipeCard（ItemStack bootstrap）」⇒ 呢兩個函式**唔准**收 `RecipeCard`／`ItemStack`：
  - `public static boolean isStandardKeepCard(String primaryOutputId, java.util.Collection<String> cardInputIds, String keepOutputId, java.util.Collection<String> keepInputIds)`
  - `public static <T> KeepResult<T> keepOnlyStandardRecipeCard(List<T> matched, java.util.function.Function<T,String> outIdOf, java.util.function.Function<T,List<String>> inIdsOf, java.util.function.Predicate<T> mustKeep, String keepOutputId, java.util.Collection<String> keepInputIds)`
  - ＋ nested `record KeepResult<T>(List<T> kept, int dropped, boolean fallback)`（純 Java 型別）
- **守衛（R2 HIGH #3＋R3 #2，硬性）**：① **`keepOutputId` 空白／null ⇒ 即刻原樣回傳**（＝非 STANDARD ask 完全走舊路；R3 實測若冇呢句，空 out 卡會令 pool 2→1，同 G4 打交）；② 函式**只可以**過濾「本身就係框架 output 卡」嘅卡（`outIdOf(c)` 大小寫不敏感 == `keepOutputId`）；③ `mustKeep.test(c)` 為真（caller 傳 `c -> c.isInputUse() || c.isTrailingOptional()`）嘅卡**永遠保留**；④ 池內**冇任何**框架 output 卡（例如 `role=uses` 池——今日真 trace 係召喚祭壇卡 `mrqx_extra_pack:mystery_craftsmanship`）⇒ **原樣回傳、唔改、唔 log**（`dropped == 0`）。⇒「靜默刪走用途卡 ＋ 誤導 log」結構上做唔到。
- **資料映射（MC → 字串）由 caller 做，唔入純函式**：`AskToolEnv`／`RenderRecipeCardsAskTool`／`AskService` 各自一個 helper，用 `Registry.ITEM.getKey(stack.getItem()).toString()` 由 `card.primaryOutputId()`／`card.inputs()` 取 id（`AskCardsDebug.itemIdSafe` 係 **private**（`:248`），唔可以借用；`stackIdsBrief`（`:47`）回 bracket brief，唔啱做 id 比對）；`AskService` 可直接用已存在嘅 `cardInputStacks(RecipeCard)`（`:1368`）拿 stack list。id 比對一律 `equalsIgnoreCase`（同 `shouldDropFrameCard` 一致）。
  - ⚠️ **方法參照寫法（R3 真 javac 17 實測）**：若 helper 係 `private static`，**唔准**寫 `this::cardInputIds`（會報 `unexpected static method … found in bound lookup`）；要寫 `RenderRecipeCardsAskTool::cardInputIds`，或者 helper 改 instance method 配 `this::`（兩者揀一，實作者自己一致就得）。
- **插入點**：`logic/RenderRecipeCardsAskTool.java` 喺 `if (matched.size() > PER_CALL_CAP)`（**真行號 `:134`**）**之前**：
  `KeepResult<RecipeCard> kr = ModularFrameCards.keepOnlyStandardRecipeCard(matched, RecipeCard::primaryOutputId, RenderRecipeCardsAskTool::cardInputIds, c -> c.isInputUse() || c.isTrailingOptional(), env == null ? "" : env.frameStandardKeepOutputId, env == null ? List.of() : env.frameStandardKeepInputIds); matched = kr.kept();`（helper 係 static 就用 `類名::`；見上面 ⚠️）
  ⇒ 命中嗰張係**唯一**候選，唔受 `subList(0, PER_CALL_CAP)` 影響（「oak 排第 10 就一張都冇」風險消失）。
  log（**只喺 `kr.dropped() > 0` 時**）：`frame-standard: keep-only kept=<outId> dropped=<n> mode=<exact|fallback>`。
- **fallback（決定性）**：冇精確命中（input 集合唔對，例如 tag↔具體 id）→ 取**第一張**框架 output 卡（任何材料變體都係合法框架配方）＋ `mode=fallback`。
- `AskTrace.renderCards(... total ...)` 同逐張 `AskTrace.card("check.cards", …)` 維持記錄**過濾前**清單（診斷用；**驗收唔准用呢個事件判卡數** —— R2 證實今日已 20 條，見 §5 S6 禁用欄位）。
- 「同族 10 變體／mirror 卡」＝ 被呢個函式擋喺 offer 之前（唔再靠 dropId 逐張 drop），文字已用「同族材料版本共 10 種」交代。
- `AskService.suppressModularFrameCards` 內用**同一個純函式**（同一守衛語意），唔另寫一套。

### D3 G3 自然解

- D1／D2 之後，STANDARD ask 至少 1 張卡唔再被 drop ⇒ `RenderRecipeCardsAskTool` 唔會回「框架合成卡已隱藏…」⇒ 模型冇得照抄。
- **驗收用機械斷言**（簡體子串）：STANDARD trace 嘅 `tool.result` 內**冇**「框架合成卡已隐藏」、【來源】行**冇**「已隐藏」。

### D4 G2 結構性清除殘留否定句（v3 收窄定義）

- `AskEngine` STANDARD 分支（`L969–1000` 之後、`ReplySources.ensure`（L963）之後）加一步：
  `body = AskReplyScrub.stripLangMissLine(body, lang)` —— **只刪「去前後空白後整行 == `ReplyLang.askMissAcquirePlayer(lang)`（`ReplyLang.java:1187`）或 `ReplyLang.obtainUnknown(lang)`（`:464`）」嘅行**（lang 驅動 ⇒ 零新增自然語言 literal）。
  - **v3 更正**：用 `line.strip()` 比對 ⇒ **前後空白／全角空白漂移都算命中**（撤回 v2「行前後多空白唔准刪」）；負例只保留「行內有其他內容（前綴／後綴／同句混寫）」。
  - **只喺 STANDARD 分支呼叫**（MODIFIED／UNKNOWN 唔准 strip）；保留配方句、來源行、用途段。
  - 唔准用 regex 掃自由散文（避免誤刪）。
  - 程式插入否定句路徑（`AskEngine:940`／`:947–956`）喺 STANDARD 已被 L969 覆寫 ⇒ **唔需要 strip**（明文寫死）。
  - 其他語言：`ReplyLang.tr`（`:122–143`）缺 bundle 時 fallback `en_us`（`bundleLang:114–119` 只認 3 bundle）⇒ strip 同 prompt **讀同一個來源**，ja_jp 等會一齊用 en_us 串 ⇒ 唔構成新缺口（R1 附註嘅反駁理由，記錄在案）。
- **harness 覆蓋**：整行相等（正例）＋前後空白漂移（正例，v3 新增）＋全角句號（正例）＋行內有前綴／同句混寫（**唔准**刪）。

### D5 診斷 log（真機可核；v3 分層）

- `AskEngine` STANDARD 分支：`frame-standard: miss line stripped=<true|false>`（strip 真正發生嘅層）。
- `AskService`（兩條 ask path，`withRecipeCards(...)` **之前**）：`frame-standard: cards kept=<n> dropped=<n> refs=<...>` —— 讀**真 display 清單**（唔讀 emission 層；R1 MEDIUM）。
- `RenderRecipeCardsAskTool`：`frame-standard: keep-only kept=<outId> dropped=<n> mode=<exact|fallback>`（**只喺 `dropped > 0` 時**；見 D2；v3 嘅 `keep-miss` log 已撤回）。
- log 資料來源：loop 層狀態（`AskLoopState.cardEmissions():520`／`suppressedFrameOffers():532`）；**唔准**讀 tool-env（env 每次 bind 重建）。refId 由 `AskToolEnv.offerEmission`（`:61–82`，`pendingEmissions.size()+1`）派發。

---

## 3. 改動白名單（實作只准改呢 10 個檔）

1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/ModularFrameCards.java`（新增 `isStandardKeepCard`／`keepOnlyStandardRecipeCard`＋nested `KeepResult`；**零 MC 型別**（harness 限制）；`shouldDropFrameCard` 簽名**唔准改**）
2. `.../logic/AskToolEnv.java`（新增 2 個 keep 欄位；`rejectFrameCard` 改簽名加 allow-list）
3. `.../logic/AskLoopState.java`（新增 keep 欄位＋setter／getter）
4. `.../logic/RenderRecipeCardsAskTool.java`（**cap 之前**加 keep-1 過濾；**唔准**刪 `suppressedFrameOnly`／L172 字串／`missEmpty`）
5. `.../client/service/AskService.java`（提前 toolBuild／算 keep／返回後 setter／6 條呼叫點／D5 log）
6. `.../logic/AskEngine.java`（`bindAskToolEnv` 抄 2 個 keep 欄位；STANDARD 分支 D4 strip ＋ D5 log）
7. `.../logic/AskReplyScrub.java`（只加 `stripLangMissLine`）
8. `.../src/test/java/com/skps9/packai/logic/ModularFrameCardsCheck.java`
9. `.../src/test/java/com/skps9/packai/logic/FrameStandardRecipeLineCheck.java`
10. `tests/check_frame_standard_card_keep.py`（**新建**；現時 `tests/check_*.py` 冇同名檔——建檔後 baseline 由 120 → 121，見 §5 S3）

**明確唔改（v3 更新）**

| 檔／範圍 | 理由 |
|---|---|
| `logic/ModularFrameStandard.java` | **只讀**（用已存在嘅 `classifyDetailedInstalled`／`recipeAt`／`FrameRecipe`）；冇需要新增入口 |
| `logic/AskCardFallback.java` | 本階段保留 keyword 路徑現狀（§6 R7）；`:1366–1379` python 閘亦釘死 4 參形式 |
| `client/gui/AiAssistantScreen.java`、`logic/RecipeEmbed.java` | 卡泊位（G5）：本 plan 只**驗收抽查**，唔改版面 |
| `logic/ModularFrameCards.shouldDropFrameCard` 簽名 | harness `dropCoreCases` ＋ python 閘依賴；keep 邏輯用**新函式**表達 |
| `logic/HonestMiss.java` | 唔關 STANDARD 事（程式插入路徑已被 L969 覆寫） |
| prompt／lang 檔（含 `assets/packai/lang/zh_cn.json` 政策段） | **唔加 key**、唔改字 |
| 卡版面 rendering／trace 事件名／renderCards digest 格式 | 唔准郁 |
| `tests/check_*.py` | 唔准為咗綠而改 assert；G7 列出**唔准破壞**嘅 token |
| `neoforge` 樹 | PAUSED（G8） |
| jar／部署 | 實作階段唔部署、唔 commit code；**S5 真機驗收例外**（需 build ＋ `mc_mod_deploy_jar.py --jar` 部署，見 §5；R2 更正：v3 誤標 S6） |

版本字樣：現 `forge/1.19.2/gradle.properties` `mod_version=0.2.3`（唔再寫 0.2.2）；commit／push 時機等 SK 定。

---

## 4. 還原點（實作前必做；v3 重寫，回應 R1 HIGH #6／MEDIUM #7）

- **實作前**建立 `.hermes/backups/2026-09-18_frame_standard_card/`（**現時唔存在**，只 `..._frame_standard_answer` 在），內容：
  - 白名單 10 檔嘅 timestamped copy ＋ `md5sum` 清單（逐檔列 `tracked／untracked`）
  - 已核 tracking 現況：**UNTRACKED**＝`ModularFrameCards.java`、`ModularFrameStandard.java`、`ModularFrameCardsCheck.java`、`FrameStandardRecipeLineCheck.java`；**TRACKED（已有多處未 commit 改動）**＝`AskToolEnv.java`、`AskLoopState.java`、`RenderRecipeCardsAskTool.java`、`AskService.java`、`AskEngine.java`、`AskReplyScrub.java`
  - ⇒ **回滾＝由呢個 backup dir copy 返**（因為 4 檔 untracked，`git revert`／`git checkout` 救唔到）；**撤回 v2「單 commit revert」講法**
- plan 舊版還原：v2＝`git show 8974d9f:docs/plans/2026-09-18-frame-standard-card-and-miss-line.md`（md5 `376c733a…`）；v1＝`git show 533f96d:…`（md5 `f3fd0649…`，已實測）
- 現況 jar：`packai-0.2.3+mc1.19.2-forge.jar` sha256 頭 12 `06b5b129a114`（`mods/` 內只有 1 個 packai jar，已核）；舊 jar backup `%TEMP%\deploy_backup_20260918_0712\`（R1 已核**存在**）⇒ jar 回滾＝關遊戲後 copy 返 `mods/`
- 驗證還原成功：`py_compile`＋`compileJava compileTestJava` BUILD SUCCESSFUL＋`tests/check_*.py` 相對 baseline 零新增紅

---

## 5. 驗收標準（逐項跑；v4：全部機械化、今日可判紅綠）

- **S1** harness `ModularFrameCardsCheck`（現 79 行；**檔頭明文「No AskToolEnv／RecipeCard（ItemStack bootstrap）」** ⇒ 只可以測**零 MC 型別**純函式）：`shouldDropFrameCard` 現有 cases 全保留；新增 `isStandardKeepCard`（命中／大小寫／input 缺／keepOut 空）＋`keepOnlyStandardRecipeCard`（精確命中 1 張／fallback 取第一張／**池內冇框架卡 → 原樣回傳**／input-use 卡永遠保留）——泛型參數用測試替身（`String[]` 或本地 record），**唔准**構造 `RecipeCard`／`ItemStack`＋**負控**：拿掉 allow-list → 目標卡被 drop（紅）。
- **S2** harness `FrameStandardRecipeLineCheck`（現 245 行、12 項）：擴充 strip 斷言——整行相等／前後空白／全角句號（正例）＋行內前綴／同句混寫（**唔准**刪）＋MODIFIED body 呼叫不到 strip。
- **S3** `python tests/check_*.py` 相對 baseline **零新增紅**。**Baseline（2026-09-18 兩次獨立親跑實測）＝ TOTAL=120 FAIL=0**（Hermes 一次／R1 反方一次，同一結果）；`check_ask_display_leak.py` 單跑 rc=0。本 plan 新增 **1 個** check 檔（§3 第 10 項）⇒ 收貨時預期 **TOTAL=121 FAIL=0**（多咗嗰個係新閘本身，唔算新增紅；如有任何 FAIL 即當 regress 查）。
- **S4** `compileJava compileTestJava` BUILD SUCCESSFUL；3 個 lang 檔 key 數不變（**513／513／513**）；改動 Java 檔新增 CJK literal = 0（機械 grep）。
- **S5** 真機（build ＋ `mc_mod_deploy_jar.py --jar <新 jar>` 部署 ＋ 重開 MC；**SK 動作＝答 2 條問題**）：
  - 木錘（STANDARD）→（a）**恰好 1 張**「合成台」卡、落點同「怎麼來」相鄰（G5 抽查）；（b）尾段冇簡體 miss 句；（c）來源行冇「已隐藏」
  - 擬態（MODIFIED）→ 三項**維持現狀**（唔出框架卡、照舊講未收錄）
- **S6（v4 重寫：只用「今日會紅、修完可綠」嘅面）**：
  - **STANDARD（指名樣本：木錘 `tetra:modular_double`）**：① trace `tool.call`（`name=render_recipe_cards` 且 `args.role` 大小寫不敏感 == `OUTPUT`）配對嘅 `tool.result` 內 `[card:N]` 出現次數 **== 1** 且該行含 `tetra:modular_double`（**今日＝0**，因為回「框架合成卡已隱藏」⇒ 鑑別力成立）② `latest.log` 有 `frame-standard: keep-only kept=tetra:modular_double dropped=<n≥1>`（今日 0 條）③ 同一 ask 嘅 trace `render.cards.final.cardsOut` **≥1**（per-ask trace 本身 ask-scoped；用途卡唔准消失）
  - **MODIFIED（指名樣本：擬態 `tetra:modular_sword`，172644／172724／181945）**：① **冇** `frame-standard: keep-only` log ② `tool.result`(role=OUTPUT) 內框架卡 `[card:N]` 數 **== 0**（今日回隱藏字串 ⇒ 以「字串或空 digest」判，**唔准**硬編 cardsOut）③ `render.cards.final.cardsOut` 同今日一致（**＝1**，用途卡）——唔准變 0 或變 2（R2 HIGH #1：v3 寫嘅 `cardsOut==0` 同真樣本／G4 打交，永遠紅，已撤回）
  - **命令（寫死，可重跑）**：`python` 讀 `<instance>/packai/trace/ask-*.jsonl`——**按出現次序配對**（第 N 個 `render_recipe_cards` 嘅 `tool.call` ↔ 第 N 個同名 `tool.result`；⚠️ **唔准按 `ts`**：真 trace `tool.result.ts` 恆等於**下一個** `tool.call.ts`（實測 18:22:39.3942924 兩邊同時出現）），用 `re.findall(r"\[card:\d+\]", result)` 數；再讀 `logs/latest.log`（cp950，`grep -a`）抽 `frame-standard:` 行（**唔准**用 `Pack AI cards emitted=` 判 ask 級結果——佢唔分 ask，會恆真）
  - ⚠️ **禁用欄位（R2 證實會假紅）**：`check.cards`（D2 明寫記錄**過濾前**清單；今日已 22 條、其中 20 條 `primaryOutputId==tetra:modular_double`）⇒ **唔准**用佢判「顯示／digest 卡數」
- **S7（回歸前提，非獨立閘）**：`check_card_emission_suppression.py`／`check_modular_frame_standard.py`／`check_frame_standard_recipe_line.py`／`check_ask_card_fallback.py` 全綠（今日已綠 ⇒ **只當回歸**，唔當新功能證明）。
- **S8（v4 改判讀面）**：**`role=uses` 路徑唔受影響（＝用途卡唔准消失）** —— 同一 ask 嘅 `tool.result`(role=uses) 內 `[card:N]` 數 == 1（召喚祭壇卡）且**冇**新增 `frame-standard: keep-only` log（今日 1 → 修完仍 1；守衛令 uses 池原樣回傳）。keyword 路徑（`AskCardFallback`）**維持現狀**並記錄（R7）：同一問句用 `cardsMode=keywords` 跑一次，`cardsOut==0` 屬**已知限制**，唔當 regress。
- **S9** 六條 suppress 呼叫點逐條回歸（`:179／:397／:418／:2324／:2479／:2495`）：**新 python 閘 `check_frame_standard_card_keep.py`** 做靜態斷言——① 六個呼叫點全部帶 keep 參數（`grep -n` 命中數 == 6，並列出行號）② `AskToolEnv.rejectFrameCard` body 內**同時**存在 `ModularFrameCards.isStandardKeepCard(` 同 `ModularFrameCards.shouldDropFrameCard(` ③ `RenderRecipeCardsAskTool` 嘅 keep-1 呼叫行號 **<** 截斷行號（needle **寫死** `matched.subList(0, PER_CALL_CAP)`；唔准用 `line_of(src,"PER_CALL_CAP")`——宣告喺 `:22` 會假紅）④ `AskEngine.bindAskToolEnv` body 內有 2 個 keep 欄位抄寫 ⑤ `AskLoopState` 有 `setFrameStandardKeep` **負控**：把 ① 嘅其中一條呼叫點嘅 keep 參數拿掉 → 閘必須轉紅（紅→綠證明寫入報告）。
- **S10** auto-emit 出口（R10）：STANDARD 樣本 `cardEmissions` 非空 ⇒ `autoEmitCatalogCards` 唔觸發；另加一個「模型冇叫 render_recipe_cards」樣本，斷言 `cardsOut ≤ 1`。

---

## 6. 未解／風險（要 R3 反方逐條打）

- **R1** classify 兩處輸入唔一致 → kind 分歧。v3：兩處都用同一份提前計算嘅 `toolBuild`；harness 覆蓋「同一輸入 ⇒ 同一 kind」。
- **R2** 匹配靠 id 集合：若 JEI 卡 input id 同 `FrameRecipe.ingredientItemIds` 形狀唔同（tag／流體／NBT 變體）⇒ 走 fallback（取第一張框架 output 卡）＋ log `mode=fallback`；池內冇框架 output 卡 ⇒ **原樣回傳（唔關 fallback 事、唔 log）**（v4 守衛語意）。
- **R3**（v4 重寫，回應 R2 HIGH #3）**`role=uses` 池唔會俾 keep-1 誤刪**：守衛令「池內冇框架 output 卡 ⇒ 原樣回傳」（今日 uses 池＝召喚祭壇卡 `mrqx_extra_pack:mystery_craftsmanship`，同 `keepOutputId` 唔同）⇒ 用途卡照舊顯示、照舊入 digest、**冇** `keep-only` log；`MAX_CARD_EMISSIONS`（`logic/AskLoopState.java:73`，＝8）壓力反而下降（output 池候選 20 → 1）；S6③／S8 覆蓋。
- **R4** MODIFIED 回歸（硬約束 G4）：keep 規格只喺 STANDARD 分支產生 ⇒ 非 STANDARD 完全走舊路；六條呼叫點逐條驗（S9）。
- **R5** UNKNOWN（fail-open）保持現狀。
- **R6**（**最貴未知之一**）`AskEngine:351` 嗰份 `toolBuild` 係 `mergeExtrasToolBuild`（focus ＋ `--- alsoSelected: … ---` extras block），而 `partsFromToolBuildText`（`:294`）逐行掃唔分 block ⇒ 有 extras 時可能由 STANDARD 變 MODIFIED／UNKNOWN（既有行為）。**本階段唔修**，明文記錄＋S 加「有 extras 樣本」記錄觀察值。
- **R7** `AskCardFallback` 路徑（`:408`／`:2487`）唔改 ⇒ keyword／非 AI 卡模式下 STANDARD 仍然被擋（已知限制 → 寫入 HANDOFF＋S8 記錄）。
- **R8** baseline 數字：v2／舊 HANDOFF 寫「118 綠＋1 紅」已過時；一律用 **120／0**。
- **R9**（v4 修正）keep 規格係「單一候選」：若 `matched` 內根本冇 `primaryOutputId == keepOutId` 嘅卡 ⇒ **原樣回傳（唔當錯誤、唔 log）**；只有「真有框架卡但全部被過濾」嘅情況才會有 `keep-only … dropped=n`。真「JEI 冇收錄該配方類別」屬另案，唔會再誤標（v3 嘅 `keep-miss` 語意已撤回）。
- **R12**（v4 新增，R2 指漏）`logic/AskJeiHints.scrubAbsenceClaimsWhenCards`（`AskJeiHints.java:110–139`，由 `AskResult.withRecipeCards` → `AskResult.java:84–97` 觸發）：今日 `hasCards=false` 唔觸發；D1／D2 之後 STANDARD **有卡** ⇒ 會開始刪「未有 JEI 配方」類行（**既有行為，非本 plan 新增**）→ 本階段只**記錄**，唔改；驗收只核「目標 miss 句被 D4 strip」（G10）。
- **R10**（v3 新增，回應 R1 MEDIUM）STANDARD 唔再被 drop ⇒ `suppressedFrameOffers=0` ⇒ `shouldSkipAutoEmit`（G3）失效；`AskService:367`／`:2450` 嘅 auto-emit 出口（cap 4）只在 `emitted.isEmpty()` 時開 → S10 覆蓋。
- **R11**（v3 新增，回應 R1 MEDIUM）G2 保證範圍＝整行（`strip()` 後）相等；模型自由改寫（加前綴／改標點／改寫成同義句）**唔喺保證內**。緩解：D1 之後 prompt／FACT 唔再指向「未收錄」，模型自寫機率下降（今日樣本證明佢係照模板抄）。**明確記錄未覆蓋**，唔當已解決。

---

## 7. 實作方式

- 一律 **cursor-agent**，寫死禁令（唔准 commit／唔准動範圍外檔／唔准 hot-copy jar／唔准改 prompt／**唔准改 `tests/check_*.py`**／**唔准改 `shouldDropFrameCard` 簽名**／**suppress body 內唔准出現 `equalsIgnoreCase`**／**唔准加 dual-tree allowlist entry**）。
- Hermes 只做：plan、派工、**親驗**（S1–S4、S6–S10 自己跑，S5 讀 trace／log 逐字核）。
- 派工前先確認白名單 10 檔嘅行號（`grep -n` 即場重讀）——plan 內行號係工作樹 HEAD `b931e2a` 時點，會漂。

---

## 8. Review record — R1（反方，2026-09-18）逐條裁決

| R1 finding | 裁決 | v3 落點 |
|---|---|---|
| CRITICAL：D2「只放行 1 張」喺白名單內做唔到（真卡閘／cap 喺白名單外） | **接受** | §2 D1(3)(4)＋D2（cap 之前過濾）＋§3 白名單加 `AskToolEnv`／`RenderRecipeCardsAskTool` |
| CRITICAL：§0c G6「同一 method／唔需要改簽名」係假 claim | **接受** | §0c G6 更正＋§2 D1(2)（返回後 `setFrameStandardKeep`） |
| HIGH：`classifyInstalled` 交唔到 recipe metadata | **接受** | §2 D1(1)（`classifyDetailedInstalled`＋`recipeAt`） |
| HIGH：命中靠 JEI 次序（oak 未必喺前 6） | **接受** | §2 D2（cap 之前過濾 ⇒ 候選唯一）＋§5 S6 |
| HIGH：驗收零覆蓋（S6 永遠綠／S7 今日已綠／S8 無 artifact） | **接受** | §5 S6／S7／S8 重寫＋S9／S10 新增 |
| HIGH：§4 還原法做唔到（4 檔 untracked＋唔 commit＋backup dir 未建） | **接受** | §4 全部重寫 |
| MEDIUM：v1 全文 claim 錯（HEAD 已係 v2） | **接受** | §4 改 `git show 533f96d:` |
| MEDIUM：D4 變體定義令 G2 唔可能 100% | **部分接受** | §5 D4 改 `strip()` 比對；G2 範圍明文收窄＋§6 R11 記錄未覆蓋（**唔**假稱 100%） |
| MEDIUM：D5 log 寫喺判決之前 | **接受** | §2 D5 分層（`AskService` 側讀真 display 清單） |
| MEDIUM：`AskJeiHints.scrubAbsenceClaimsWhenCards` 新觸發未提 | **接受** | §0c G10＋§2 D4 註＋S6 抽查 |
| MEDIUM：G3 解除後 auto-emit 出口打開 | **接受** | §6 R10＋S10 |
| MEDIUM：版本字樣 0.2.2 vs 實際 0.2.3 | **接受** | §3 尾段 |
| MEDIUM：§3「唔部署」vs S5「要部署」矛盾 | **接受** | §3 表格備註 |
| LOW：audit 引用 HEAD drift | **接受** | §7 註明行號時點 |
| 附註：`AskLoopState.cardEmissions()` 實為 `:520` | **接受** | §0b C#9 更正 |
| 附註：`modularFrameDropId` 語義唔改係被 `check_ask_card_fallback.py:1366–1379` 強制 | **接受（補充證據）** | §0c G11 |

**撤回清單（v2 → v3，明文唔准復活）**

| 原聲稱 | 否證證據 | 因為撤回而避開嘅副作用 |
|---|---|---|
| §0c G6「`:631` 同 suppress 呼叫點同一個 method／唔需要改 `beginAskLoop` 簽名」 | `:631` 實際喺 `beginAskLoop`（`:614–649`，由 `:299`／`:2416` 呼叫）；`:179` 喺 `runAsk`、`:2324` 喺第二個 ask method（`grep -n` 三處行號） | 實作者會照錯前提做 → 卡閘永遠見到舊 dropId → 症狀唔消失而驗收全綠 |
| §2 D2「`cardInputIds`」 | `RecipeCard` 真 record 只有 `grid／inputs／catalysts／outputs`（`RecipeCard.java:20–58`） | 照字面寫 compile 唔到／要自創 API → 白做一輪 |
| §2 D1「用 `classifyInstalled`」 | 該函式只回 `Kind`（`:220–222`），冇 `recipeIndex` | D2 冇 recipe metadata 可用 |
| §5 S6「cardsOut ≤ 上限」 | 「1 ≤ 6」恆真 ⇒ 零鑑別力（R1 親跑證） | 假綠：卡數唔止 1 張都會過 |
| §4「單 commit revert」 | 白名單 4 檔 untracked（`git ls-files --error-unmatch`）＋plan 自己禁 commit | 還原唔到 → 出事要重寫 |
| §4「backup dir `.hermes/backups/2026-09-18_frame_standard_card/`」 | `ls -d` → No such file（只有 `..._frame_standard_answer`） | 誤以為已有備份 |
| §2 D4「行前後多空白唔准刪」 | 真機模型係照模板抄（trace 第 10 行 byte 級相等），空白漂移屬常見 ⇒ 唔刪等於留殘句 | G2 實際唔成立 |

---

### R2（反方，有界輪）逐條裁決 — 正方 7 : 反方 3

| R2 finding | 裁決 | v4 落點 |
|---|---|---|
| CRITICAL 級冇（A 節 3 條 MUST-FIX 全 RESOLVED；B 節 12 條全 RESOLVED） | — | — |
| HIGH：S6 MODIFIED `cardsOut==0` 同真樣本（172644 等 `cardsOut=1` 用途卡）及 G4 打交 ⇒ 永遠紅 | **接受**（我親讀 trace 覆核） | §5 S6 改寫（改判「框架卡 `[card:N]` 數 == 0」＋cardsOut 同今日一致）＋撤回清單 |
| HIGH：S8 用 `check.cards` 判讀，但 D2 明寫佢記過濾前清單（今日已 20 條）⇒ 永遠紅 | **接受**（我 python 親數：`check.cards` 22 條，20 條 out=modular_double） | §5 S6 禁用欄位＋S8 改判 `tool.result`(role=uses) |
| HIGH：D2 keep-1 無 role／input-use 守衛 ⇒ 靜默刪走今日唯一顯示嘅用途卡＋誤導 log | **接受** | §2 D2 加硬守衛（只過濾框架 output 卡、`mustKeep` 永遠保留、池內冇框架卡即原樣回傳）＋§6 R3／R9 重寫＋S8 新增 uses 斷言 |
| LOW：S1 喺 `ModularFrameCardsCheck` 測 `List<RecipeCard>` 同 harness 自身約束衝突 | **接受** | §2 D2 改純字串泛型 API＋§5 S1 明文「唔准構造 RecipeCard／ItemStack」 |
| LOW：S9③ 行號 needle 易假紅（`PER_CALL_CAP` 宣告喺 `:22`） | **接受** | §5 S9③ 寫死 needle `matched.subList(0, PER_CALL_CAP)` |
| LOW：§0b C#9 setter 行號錯（`:526–528` → 真 `:528–530`） | **接受** | §0b C#9／§0c G6 更正 |
| LOW：§8／G10 交叉引用指向 ghost 落點 | **接受** | §0c G10 改「本表記錄＋§6 R12」；§2 D4 維持不變 |
| LOW：§3 部署備註標錯閘名（S6 → 真 S5） | **接受** | §3 表更正 |
| R2 自己嘅 FC-A／FC-C（uses 卡被取代／樣本量） | **不接受為開放項** | v4 用守衛令「用途卡被取代」唔會發生（FC-A 前提消失）；FC-C 樣本量由 S6／S8 兩條獨立斷言覆蓋 |

**v4 撤回清單（v3 → v4）**

| 原聲稱 | 否證證據 | 因為撤回而避開嘅副作用 |
|---|---|---|
| §5 S6「MODIFIED `render.cards.final.cardsOut == 0`」 | 真 trace 172644／172724／181945 `cardsOut=1`（`role=input`，用途卡） | 永遠紅 → 實作者為求綠而改 MODIFIED 行為（違反硬約束 G4） |
| §5 S8「用 `check.cards` 計框架卡 == 1」 | `check.cards` 記**過濾前**清單（今日 22 條／20 條 out=modular_double） | 永遠紅／造假綠 |
| §2 D2「keep-1 冇 role 條件」 | 真 trace `role=uses` 池（召喚祭壇卡）⇒ 會被全刪 | 刪走玩家今日唯一睇到嘅卡＋噴誤導 log |
| §2 D2「`keep-miss` log」語意 | 同一個 log 會將「池內冇框架卡」誤報成「JEI 冇資料」 | 診斷誤導 |

### R3（反方，有界輪）逐條裁決 — 正方 8 : 反方 2 ✅ 達標

| R3 finding | 裁決 | v4.1 落點 |
|---|---|---|
| A 節（R2 3 條 HIGH）／B 節（5 條 LOW）全部 RESOLVED（每條附 file:line＋命令） | — | — |
| polish #1：D2「private static helper」配 `this::cardInputIds` 真 javac 17 唔 compile | **接受**（我親核 `RenderRecipeCardsAskTool` 係 instance class；helper 若 static 就唔可以用 `this::`） | §2 D2 加 ⚠️ 寫法指引＋插入點樣本改 `RenderRecipeCardsAskTool::cardInputIds` |
| polish #2：D2 漏「`keepOutputId` 空白 ⇒ 原樣回傳」（v3 有、v4 漏）⇒ 非 STANDARD ask 遇空 out 卡會縮池（實測 2→1），同 G4 打交 | **接受** | §2 D2 守衛 ① 明文加入（37 條 trace 未觀察到，屬 latent，仍然封） |
| polish #3：S6 命令「按 `ts` 配對」真 trace 配唔到（`result.ts` ＝ **下一個** `call.ts`，實測兩者都係 `18:22:39.3942924`）；`cards emitted=` 唔分 ask 會恆真 | **接受**（我親核） | §5 S6 命令改「按出現次序配對」＋③ 改讀 per-ask trace `render.cards.final.cardsOut`＋明文禁用 `cards emitted=` 判 ask 級結果 |
| R3 自己嘅 FLIP：3 條 polish 修好 ⇒ 維持 ≥8:2、**唔應再開第 4 輪** | **接受** | v4.1 改完直接進 §7 實作（唔再開 review 輪） |

---

## 附錄：v2 → v3 改動清單

| # | 改咗咩 | 理由 |
|---|---|---|
| 1 | D1 由 1 個落腳點 → **3 個**（算 kind／餵 loop／env allow-list）＋6 條 suppress 傳 keep | R1 CRITICAL（白名單鎖死） |
| 2 | D2 定位到 **`RenderRecipeCardsAskTool` cap 之前** | R1 CRITICAL＋HIGH（次序決定成敗） |
| 3 | 白名單 7 → **10 檔**（加 `AskToolEnv`／`AskLoopState`／`RenderRecipeCardsAskTool`） | 同 1 |
| 4 | G6 更正（撤回「同一 method」）＋明寫 setter 方案 | R1 CRITICAL |
| 5 | 驗收 S1–S8 → **S1–S10**（機械斷言／負控／auto-emit／六條呼叫點） | R1 HIGH（驗收零覆蓋） |
| 6 | §4 還原點重寫（backup dir 必建、逐檔 tracking、撤回單 commit revert） | R1 HIGH |
| 7 | D4 改 `strip()` 比對＋G2 範圍明文收窄＋R11 未覆蓋記錄 | R1 MEDIUM |
| 8 | D5 log 分層＋`AskService` 側讀真 display 清單 | R1 MEDIUM |
| 9 | §0c 新增 G10（`scrubAbsenceClaimsWhenCards`）／G11（`check_ask_card_fallback` 釘死 4 參） | R1 MEDIUM＋附註 |
| 10 | 新增 §8 Review record（逐條裁決＋撤回清單） | 收斂紀律（唔靠 session 記憶） |

---

## 附錄二：v3 → v4 改動清單

| # | 改咗咩 | 理由（R2 finding） |
|---|---|---|
| 1 | D2 純函式改成**零 MC 型別泛型 API**＋nested `KeepResult`（`outIdOf`／`inIdsOf`／`mustKeep` 由 caller 傳） | R2 HIGH #3（守衛）＋LOW（harness 約束） |
| 2 | D2 加**硬守衛**：只過濾框架 output 卡；`mustKeep`（input-use／trailing-optional）永遠保留；池內冇框架卡 ⇒ 原樣回傳、唔 log | R2 HIGH #3 |
| 3 | 刪 `keep-miss` log 語意，改成只在 `dropped > 0` 時出 `keep-only kept=… dropped=… mode=exact|fallback` | R2 HIGH #3＋撤回清單 |
| 4 | S6 重寫：STANDARD 改判 `tool.result`(role=OUTPUT) 嘅 `[card:N]`==1＋`keep-only` log＋`cards emitted ≥1`；MODIFIED 改判「框架卡 == 0」＋cardsOut 同今日一致 | R2 HIGH #1／#2 |
| 5 | S6 新增**禁用欄位**：`check.cards` 唔准用（記過濾前清單） | R2 HIGH #2 |
| 6 | S8 改判 `tool.result`(role=uses) 張數 == 1（用途卡唔准消失） | R2 HIGH #3 |
| 7 | S1 明文「唔准構造 `RecipeCard`／`ItemStack`」（用測試替身） | R2 LOW |
| 8 | S9③ needle 寫死 `matched.subList(0, PER_CALL_CAP)` | R2 LOW |
| 9 | §0b C#9／§0c G6 setter 行號 `:526–528` → `:528–530`；§3 部署備註 S6 → S5；G10 刪 ghost 落點、改指 §6 R12 | R2 LOW ×3 |
| 10 | §6 R3／R9 重寫＋新增 R12（`scrubAbsenceClaimsWhenCards` 既有行為） | R2（R9／R12） |
| 11 | §8 新增 R2 逐條裁決＋ v4 撤回清單（4 條） | 收斂紀律 |
