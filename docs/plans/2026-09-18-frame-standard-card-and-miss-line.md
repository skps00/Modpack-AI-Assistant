# Plan：標準框架＝出返合成卡 ＋ 清走殘留否定句（v1）

- 日期：2026-09-18
- 觸發：SK 真機 3 問（09-18 17:26／17:27／18:22）＋SK 指出「but no cards...」＋SK 揀 **option 1**（一個細 plan 一次過修 1＋2＋3）
- 狀態：**待反方 review（gate 正方 ≥8 : 反方 ≤2）**；未開工、未改任何 code／jar
- 範圍：**只改 forge/1.19.2**；`neoforge/1.21.1` 唔郁

---

## 0. 今日現狀（SK 真機睇到嘅三個矛盾）

以 09-18 18:22:31「下界合金 锤」（`tetra:modular_double`）為樣本，玩家實際睇到：

1. 「怎麼來」正確出咗正面句：**「合成台（有序合成）：橡木木板 + 木棍 -> modular double。摆放位置请以 JEI 为准。这是空白模组框架合成版本。（同族材料版本共 10 种，其他木板／石料版本一样可以合成）」**
2. **但冇任何配方卡**（答案只有一張「用作材料」嘅召喚祭壇卡）→ 玩家睇唔到擺位，只能自己開 JEI。
3. 同一答案尾段仍然印：**「目前沒有這件物品的取得資料，暫時不確定怎麼拿到。」**；【來源】行寫「**框架合成卡已隱藏**」→ 同第 1 點正面句直接打交。

同一情況在 17:26／18:19（擬態，MODIFIED）同 17:27（切石器，STANDARD）都見到卡被壓（`suppressedFrameOnly n=2`）。

---

## 0b. 證據（全部可重跑）

**A. trace** `packai/trace/ask-20260918-182231-tetra_modular_double.jsonl`

- `display.body.final`：怎麼來段＝程式插入（正面句）；尾段含 miss 句。
- `model.reply.final`（len 723）**本身已含** miss 句 ⇒ 係模型自寫，唔係程式尾插。
- `send.facts` 5 輪 len≈304，**完全冇** miss 句 ⇒ 唔係 FACT 注入造成。
- `send.system`（14593 chars）已含 policy **#19**（「若 FACT 寫…未索引 → 明說未知」）同 **#23**（「標準框架 → 必須講合成台…肯定句；**禁止**對標準框架說『未收錄』」）⇒ 指令已在，模型**冇跟**。⇒ 呢個要**結構性**保證（SK 規則：唔接受 LLM 選擇性行為）。

**B. latest.log（09-18，同一次 ask）**

```
frame-miss: branch kind=STANDARD acquireEmpty=false
frame-standard: branch entered recipe=in=[minecraft:oak_planks, minecraft:stick] out=tetra:modular_double
frame-standard: how-to-get replaced (STANDARD frame)
frame-standard: final check present=true
renderCards item=tetra:modular_double role=output scannedCats=20 foundOutput=20 afterFilter=20
renderCards suppressedFrameOnly n=6
cards emitted=1        （只有召喚祭壇 input 卡）
```

**C. 卡被壓嘅 code 機制（code review 結果）**

| # | 位置 | 行為 |
|---|---|---|
| 1 | `ModularFrameCards.shouldDropFrameCard(dropId, primaryOutputId, inputUse, trailingOptional)` | focus 係 modular tool 且卡片主產物 == focus id、又唔係 input-use／trailing-optional → **一律 drop**（唔理 kind） |
| 2 | `AskToolEnv.offerEmission → rejectFrameCard`（tool 發卡路徑） | 用 `loop.modularFrameDropId()`（B11） |
| 3 | `AskService.suppressModularFrameCards(cardFocus, cards)` L179／L397／L2487（catalog ＋ display 兩條路） | 用 `AskService.modularFrameDropId(cardFocus)` |
| 4 | `RenderRecipeCardsAskTool` L163–174 | 全部被 drop → tool 回字串「框架合成卡已隱藏（非本工具取得途徑）」→ 模型照抄入【來源】 |
| 5 | `AskEngine` L351 `frameMatch = ModularFrameStandard.classifyDetailedInstalled(toolBuild)`；L969–1000 STANDARD 分支 | **kind 喺呢度才算**；卡閘（#1–#3）完全唔知 kind |

⇒ 根因：**Plan A 只改咗文字層，卡閘仍按舊政策（「框架卡＝非本工具取得途徑」）一律隱藏** → 文字／卡／來源三者打交。

**D. 同類風險（順手 review 出嘅，唔屬於本 plan 範圍，只記錄）**

- 任何「程式決定性插入」同「模型自由散文」並存嘅位置，都有機會出現同款自相矛盾；本次只收窄到 STANDARD frame 呢一條（唔准擴範圍）。
- `RenderRecipeCardsAskTool` L172 個中文字串係 tool-result（唔顯示玩家）；**唔屬於**「玩家文字必用 lang key」違規，但改動時唔准動其他字串。

---

## 1. 目標（SK option 1）

- **G1**：STANDARD frame（空白框架＝jar 內對應合成配方嗰種）→ **要出返 1 張對應嘅合成台卡**（睇得到擺位），同「怎麼來」文字相鄰（唔准孤兒卡）。
- **G2**：STANDARD frame 答案 → **唔准**再出現「未收錄／不確定怎麼拿到」類否定句（由程式保證，唔靠 prompt）。
- **G3**：STANDARD frame 答案【來源】→ 唔准再寫「框架合成卡已隱藏（非本工具取得途徑）」。
- **G4**：**MODIFIED（特製版）／UNKNOWN → 100% 維持現狀**（唔出框架卡、老實講未收錄、來源照舊）。呢條係硬約束。

---

## 2. 設計（最小 diff；全部可 harness）

**D1 統一 kind-aware 卡閘（解 G1 前提）**

- 新增純函式 `ModularFrameCards.dropIdFor(String focusId, ModularFrameStandard.Kind kind)`：
  - `STANDARD` → 回 `""`（＝唔 drop，卡可以出）
  - `MODIFIED`／`UNKNOWN`／`null`／`focusId` 空 → 回 `focusId`（＝**現狀**）
- `AskService.modularFrameDropId(cardFocus)` 由「識別 focus 就回 id」改成「識別 focus → classify → dropIdFor」。
- **classify 輸入必須同 `AskEngine` L351 逐字同一份 `[TOOL_BUILD]` 文本** ⇒ 抽共用 helper（例如 `ModularFrameStandard.classifyInstalled(toolBuild)` 單一入口），避免兩處 classify drift（R1）。

**D2 STANDARD 只放行「對應 recipe 嗰 1 張」卡（解 G1 洗版）**

- 新增純函式 `ModularFrameCards.isStandardRecipeCard(List<String> recipeIngredientIds, String recipeResultId, String cardPrimaryOutputId, List<String> cardInputIds)`：
  - 命中＝`cardPrimaryOutputId == recipeResultId` 且 `recipeIngredientIds ⊆ cardInputIds`（id 比對大小寫不敏感）
- 命中嗰張 → 放行；**同族 10 變體／mirror 卡照舊 drop**（文字已用「同族材料版本共 N 種」交代；避免一次過 20 張洗版，亦符合 SK「唔要重複卡」偏好）。
- 卡歸「**取得方式**」段（同文字相鄰）；用途卡（input-use）行為不變。

**D3 G3 自然解**

- D1 之後，STANDARD ask 唔會再全部卡被 drop ⇒ `RenderRecipeCardsAskTool` 唔會再回「框架合成卡已隱藏…」字串 ⇒ 模型冇得照抄。
- **驗收要抽查**：STANDARD 真機 trace 嘅 tool.result 內**冇**該字串、【來源】行**冇**「已隱藏」。

**D4 G2 結構性清除殘留否定句**

- `AskEngine` STANDARD 分支（L969–1000 之後、`ReplySources.ensure` 之後）加一步：
  `body = AskReplyScrub.stripLangMissLine(body, lang)` —— **只刪整行 == `ReplyLang.askMissAcquirePlayer(lang)` 或 `ReplyLang.obtainUnknown(lang)`**（lang 驅動 ⇒ 零新增中文字面量）。
- **只喺 STANDARD 分支呼叫**（MODIFIED 唔准 strip）；保留配方句、來源行、用途段。
- 唔准用 regex 掃自由散文（避免誤刪）；模型其他自由措辭靠 D1 令 prompt/FACT 唔再指向「未收錄」去自然收斂。

**D5 診斷 log（真機可核）**

- STANDARD 分支新增兩行：`frame-standard: card kept recipe=… ref=…`、`frame-standard: miss line stripped=true/false`。

---

## 3. 改動白名單（實作只准改呢 7 個檔）

1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/ModularFrameCards.java`
2. `forge/1.19.2/src/main/java/com/skps9/packai/logic/ModularFrameStandard.java`（只加 classify 單一入口）
3. `forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java`
4. `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java`
5. `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskReplyScrub.java`（只加 `stripLangMissLine`）
6. `forge/1.19.2/src/test/java/com/skps9/packai/logic/ModularFrameCardsCheck.java`
7. `forge/1.19.2/src/test/java/com/skps9/packai/logic/FrameStandardRecipeLineCheck.java`

**唔准郁**：prompt／lang 檔（唔加 key）／卡版面 rendering／trace 事件名／renderCards digest 格式／`neoforge` 樹／其他未 commit 檔／jar。

---

## 4. 還原點（實作前必做）

- 檔備份：`.hermes/backups/2026-09-18_frame_standard_card/`（7 檔 md5 ＋ `git rev-parse HEAD`）
- 現況 jar：部署版 `06b5b129a114`（原 backup `%TEMP%\deploy_backup_20260918_0712\`）
- 回滾：單 commit revert（或 copy 返 backup）→ compile → 需要時重部署舊 jar。**實作階段唔部署、唔 commit code**（跟 SK 定案：等 bump `0.2.2`）。

---

## 5. 驗收標準（逐項跑，唔准靠印象）

- **S1** harness `ModularFrameCardsCheck`：`dropIdFor` 三分支；`isStandardRecipeCard` 正例＋反例（變體／mirror／input-use 唔准放行）。
- **S2** harness `FrameStandardRecipeLineCheck`：body＝〔配方句＋miss 句＋來源行〕→ strip 後 miss 句冇、其餘逐字保留；MODIFIED body 呼叫不到 strip。
- **S3** python：`check_modular_frame_standard.py`、`check_frame_standard_recipe_line.py`、`check_card_emission_suppression.py` 全綠；全套 `tests/check_*.py` 相對 baseline **零新增紅**。
- **S4** `compileJava compileTestJava` BUILD SUCCESSFUL；3 個 lang 檔 key 數不變（513）；改動 Java 檔新增 CJK literal = 0。
- **S5** 真機（build＋`mc_mod_deploy_jar.py --jar` 部署後，SK 出 2 問）：
  - 木錘（STANDARD）→（a）有「取得方式」卡（b）尾段冇 miss 句（c）來源行冇「已隱藏」
  - 擬態（MODIFIED）→ 三項**維持現狀**
- **S6** cardsOut ≤ 上限、用途卡仍在（唔准搶位）。

---

## 6. 未解／風險（要 review 反方逐條打）

- **R1** classify 兩處輸入唔一致 → kind 分歧（D1 靠共用 helper，harness 要覆蓋）。
- **R2** RecipeCard 匹配靠 input ids；若 JEI 卡冇 input id ⇒ 退回「只放行 1 張 output 對應卡」，要 harness 覆蓋。
- **R3** 出卡後會唔會擠走用途卡／超 `MAX_CARD_EMISSIONS`。
- **R4** MODIFIED 回歸（硬約束 G4）。
- **R5** UNKNOWN（fail-open）保持現狀＝照舊唔出卡；會唔會同「標準框架」判定爭位（classify 樣本不足）。

---

## 7. 實作方式

- 一律 **cursor-agent**，寫死禁令（唔准 commit／唔准動範圍外檔／唔准 hot-copy jar／唔准改 prompt）。
- Hermes 只做：plan、派工、**親驗**（S1–S4 自己跑，S5 讀 trace 逐字核）。
