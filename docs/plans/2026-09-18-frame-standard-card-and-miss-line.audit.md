# Code-anchor 核實審查：`2026-09-18-frame-standard-card-and-miss-line.md`

- 審查日期：2026-09-18
- 被審 plan：`docs/plans/2026-09-18-frame-standard-card-and-miss-line.md`（md5 `f3fd06493ea21375eae03ae728dd86ed`，154 行）
- 方法：逐條 grep／read_file 真 code（forge/1.19.2 工作樹 HEAD `533f96d`）＋ 真 trace／latest.log 原文 ＋ 親跑 python 閘
- 未改任何 code、未 commit

回報格式：`OK`＝claim 同真 code 一致；`WRONG`＝唔一致（附真行號）。

---

## A. §0b C「卡被壓嘅 code 機制」5 行逐條核

| # | Plan 聲稱 | 判 | 真 anchor（forge/1.19.2/src/main/java/com/skps9/packai/…） |
|---|---|---|---|
| 1 | `ModularFrameCards.shouldDropFrameCard(dropId, primaryOutputId, inputUse, trailingOptional)`；focus 係 modular tool 且卡主產物 == focus id、非 input-use／trailing → 一律 drop | **OK** | `logic/ModularFrameCards.java` **L18–34**（sig L18–23；三個早退 L24–32；比對 L33 `dropId.equalsIgnoreCase(primaryOutputId)`）。⚠️ modular-focus 判定**唔喺呢個檔**，係 caller `client/service/AskService.java:2568 isModularToolFocus` |
| 2 | `AskToolEnv.offerEmission → rejectFrameCard`（tool 發卡路徑）**用 `loop.modularFrameDropId()`** | **WRONG** | `logic/AskToolEnv.java`：`offerEmission` **L61**、呼叫 `rejectFrameCard` **L67**、`rejectFrameCard` 定義 **L89–99**；佢讀嘅係 **env 欄位** `modularFrameDropId`（欄位 L35、用喺 L91），**唔係** `loop.modularFrameDropId()`。loop→env 嘅抄寫點係 `logic/AskEngine.java` **L1678–1679**（bind 時） |
| 3 | `AskService.suppressModularFrameCards(cardFocus, cards)` **L179／L397／L2487** | **WRONG（唔完整）** | `AskService.java`：定義 **L2643–2668**；呼叫點喺 forge 有 **6 個**：**L179、L397、L418、L2324、L2479、L2495**。另：**L2487 唔係 suppress 呼叫**，係 `AskCardFallback.ensureCards(scrubbed, collected, null, modularFrameDropId(cardFocus))`（同 L408 一對） |
| 4 | 用 `AskService.modularFrameDropId(cardFocus)` | **OK** | 定義 `AskService.java` **L2636–2641**（plan 寫 L2636 ✓） |
| 5 | `RenderRecipeCardsAskTool` **L163–174**：全部被 drop → tool 回字串 | **OK（±1）** | `logic/RenderRecipeCardsAskTool.java`：`if (emitted.isEmpty())` **L163**、`if (env.suppressedFrameOffers > 0)` **L168**、log L169–171、回字串 **L172**、`missEmpty` **L174** ⇒ 分支實際 **L163–175** |
| — | `AskEngine` **L351** `frameMatch = ModularFrameStandard.classifyDetailedInstalled(toolBuild)` | **OK** | `logic/AskEngine.java` **L351** 逐字相同（`final ModularFrameStandard.Match frameMatch = …`） |
| — | `AskEngine` **L969–1000** STANDARD 分支 | **OK** | 條件 **L969**（`frameKind == STANDARD && frameMatch.recipeIndex() != null`）；`final check present` log **L999**；分支收 **L1000** |
| — | D1「抽共用 helper（例如 `ModularFrameStandard.classifyInstalled(toolBuild)` 單一入口）」 | **已存在，唔係新加** | `logic/ModularFrameStandard.java`：`classifyInstalled` **L220–223**（已有）、`classifyDetailedInstalled` **L225**、`classify(...)` **L233**、`classifyDetailed(...)` **L255** ⇒ 現實係**四個**入口，唔係「一個」。D1 要指明用邊個＋另外三個點算 |

## B. §0b A「trace 證據」逐條核

trace：`packai/trace/ask-20260918-182231-tetra_modular_double.jsonl`（真存在，259,230 bytes，91 records）

| # | Plan 聲稱 | 判 | 真值 |
|---|---|---|---|
| 6 | `display.body.final` 怎麼來段＝程式插入；尾段含 miss 句 | **OK** | rec `display.body.final`，body len **496**；miss 句係**獨立一行**（前一行 L9 空、後一行 L11 空） |
| 7 | `model.reply.final`（len 723）**本身已含** miss 句 | **OK** | rec `model.reply.final`，len **723** ✓；miss 句喺第 15 行（idx 14）✓ ⇒ 真係模型自寫 |
| 8 | `send.facts` 5 輪 len≈304，完全冇 miss 句 | **OK** | 6 條 `send.facts`：**1×0 ＋ 5×304**；5 條 non-empty 全部冇 miss 句 ✓ |
| 9 | `send.system`（14593 chars）已含 policy #19／#23 | **OK（但編號不可核）** | 6 條 `send.system`：**1×278**（intent 分類器）＋ **5×14593**（主 prompt）✓。⚠️ 字樣 `#19`／`#23` 喺整份 trace **出現 0 次**——編號係 plan 作者自編索引；正文確實存在（`未索引` ×10、`How-to-get` ×10、`标准框架` ×25） |

## C. §0 敘述層引文

| # | Plan 寫法 | 判 | 真值 |
|---|---|---|---|
| 10 | 引「**目前沒有這件物品的取得資料，暫時不確定怎麼拿到。**」／【來源】「**框架合成卡已隱藏**」 | **WRONG（繁簡）** | 真 trace 係**簡體 zh_cn**：`目前没有这件物品的取得资料，暂时不确定怎么拿到。`；`【来源】JEI（用途卡／框架合成卡已隐藏）`。當引文用（grep／lang 比對）會 miss |

## D. §0b B「latest.log 證據」逐條核

`instances/AI_test_NFWC_DIM/minecraft/logs/latest.log`（3,017,146 bytes）

| # | Plan 寫嘅行 | 判 | 真行號 |
|---|---|---|---|
| 11 | `frame-miss: branch kind=STANDARD acquireEmpty=false` | **OK** | **L8965** |
| 12 | `frame-standard: branch entered recipe=in=[minecraft:oak_planks, minecraft:stick] out=tetra:modular_double` | **OK** | **L8966** |
| 13 | `how-to-get replaced (STANDARD frame)` | **OK** | **L8967**（另有 **L8968** `recipe line inserted (STANDARD frame)`，plan 未列） |
| 14 | `final check present=true` | **OK** | **L8969** |
| 15 | `renderCards item=tetra:modular_double role=output scannedCats=20 foundOutput=20 afterFilter=20` | **OK** | **L8942** |
| 16 | `renderCards suppressedFrameOnly n=6` | **OK** | **L8943** |
| 17 | §0「17:26／18:19 同 17:27 都見到卡被壓（`suppressedFrameOnly n=2`）」 | **OK** | **L8185**（17:26:55）、**L8424**（17:27:40）、**L8772**（18:19:52） |
| 18 | `cards emitted=1`（只有召喚祭壇 input 卡） | **OK** | **L8984**（18:22:57.893，同一次 ask）；對應 `AskService.java:2675 logCardsEmitted` |

## E. §4 還原點

| # | Plan 聲稱 | 判 | 真值 |
|---|---|---|---|
| 19 | 現況 jar 部署版 `06b5b129a114`、mods 內只有 1 個 packai jar | **OK** | `mods/packai-0.2.3+mc1.19.2-forge.jar`，sha256 頭 12 = **06b5b129a114**；mods 內確只有 1 個 |
| 20 | 原 backup 喺 `%TEMP%\deploy_backup_20260918_0712\` | **未核** | 本次審查未獨立驗證該目錄（唔喺讀取範圍）。要核就先講 |

## F. §5 驗收標準（實作前嘅現狀）

| # | Plan 聲稱 | 判 | 真值 |
|---|---|---|---|
| 21 | S3 baseline：三個 python check 全綠 | **OK** | `check_modular_frame_standard.py`／`check_frame_standard_recipe_line.py`／`check_card_emission_suppression.py` 全部 rc=0 |
| 22 | S3／HANDOFF「118 綠 ＋ 1 已知紅（`check_ask_display_leak` RC=2）」 | **WRONG** | 我親跑 `tests/check_*.py`：**120 檔，FAIL=0**（TOTAL=120 FAIL=0）；`check_ask_display_leak.py` 單獨 rc=0。⇒ 呢個 baseline 數字**過時**，「零新增紅」判準要用 **120/0** 重寫 |
| 23 | S4 lang 3 檔 key 數 513 | **OK** | en_us 513／zh_cn 513／zh_tw 513 |
| 24 | S1 harness `ModularFrameCardsCheck` 要驗 `dropIdFor`／`isStandardRecipeCard` | **OK（一致）** | `src/test/.../ModularFrameCardsCheck.java` 79 行、3 個方法（`dropCoreCases`／`skipAutoEmit`／`crossLayerSharedCore`）；**現時冇** 新函式測試（因為函式未存在）✓ 與「擴充」一致 |
| 25 | S2 harness `FrameStandardRecipeLineCheck` 要驗 strip | **OK（一致）** | `FrameStandardRecipeLineCheck.java` 245 行、main L15–39 呼叫 **12 個** 測試方法（對得住 HANDOFF「12 項全 OK」）；**現時冇** `stripLangMissLine` 測試（函式未存在）✓ |
| 26 | §0b D「`RenderRecipeCardsAskTool` L172 個中文字串係 tool-result，唔屬 lang-key 違規」 | **OK** | L172 係 `return "框架合成卡已隱藏（非本工具取得途徑）"`（tool 回傳字串，唔經 UI lang 層） |

**總結**：26 條中 **OK 21／WRONG 4／未核 1**。WRONG 全部係「行號／引文／baseline 數字」層，冇一條推翻 plan 嘅**根因判斷**（卡閘唔知 kind）。

---

## G. Plan 漏掉嘅相關 code 路徑

### G1. 第三條「放行框架卡」路徑（D1 會間接打開，plan 冇講）
`logic/AskCardFallback.java` **L441–452 `collectOutputQuestIndices`** ＋ **L455–461 `isFocusFrameOutput`** 收 `dropFocusOutputId`；傳入點係 `AskService.java` **L408** 同 **L2487**（`ensureCards(…, modularFrameDropId(cardFocus))`）。
D1 令 STANDARD 回 `""` ⇒ `isFocusFrameOutput` 永遠 false ⇒ **框架 output 卡可以經 ensureCards 被塞返答案**。呢個檔案**唔在白名單**，但行為會被間接改變（可能同 tool 路徑出嘅卡重複／爭 cap）。

### G2. suppress 呼叫點係 6 個，唔係 3 個
`AskService.java` L179／L397／L418／L2324／L2479／L2495。其中 L418／L2495 喺 **keyword-fallback 路徑**（`cardsMode.resolveAttach` 之後），L179／L397／L2324／L2479 喺 catalog／tool-emission 路徑。D1 要確認**六條都通**。

### G3. 卡閘第二道門：`shouldSkipAutoEmit`
`logic/AskLoopState.java` **L545–547**（`suppressedFrameOffers > 0 && cardEmissions.isEmpty()`）、counter 欄位 **L82**、`noteSuppressedFrameOffer` **L537–539**、`suppressedFrameOffers()` **L532**；呼叫點 `AskService.java` **L367** 同 **L2450**（`!askLoop.shouldSkipAutoEmit()`）。STANDARD 唔再被 drop 之後呢道門自然唔觸發（唔需改），但 plan 完全冇提——而佢係「STANDARD 出得返卡」嘅必要條件之一。

### G4. mirror 合併喺卡閘之前
`client/jei/JeiRecipeCards.java` **L1812 `coalesceMirrorEmission`**；呼叫點 `RenderRecipeCardsAskTool.java` **L101**、`AskService.java` **L1659／L1754**。
⇒ D2 嘅 `isStandardRecipeCard` 見到嘅 `matched` 已經係**合併後**清單；「20 張 → 幾張」嘅實際輸入未定。plan 冇提 coalesce 次序。

### G5. 卡版面 placement（G1「唔准孤兒卡」正正在此決定）唔在白名單
`client/gui/AiAssistantScreen.java` **L833–869**（`partsi = RecipeEmbed.parts(body, origCards)`、`cardStrip ? indexBeforeSources(parts) : insertObtainClusterAt(parts)`、`splitTrailingSources`）；
`logic/RecipeEmbed.java` **L707 `indexBeforeSources`**、**L716 `splitTrailingSources`**、**L1280 `insertObtainClusterAt`**、**L744** comment 區（interleave 規則）。
D2 寫「卡歸**取得方式**段（同文字相鄰）」＋G1 寫「唔准孤兒卡」——**兩者都由呢幾個檔決定**，但佢哋唔在 7 檔白名單內。要麼確認「現有 interleave 自動做到」（就要列入驗收 S5 抽查點），要麼要開白名單。

### G6. D1 嘅時序／簽名問題（plan R1 未涵蓋）
- `toolBuild` 喺 **`AskService.java` L276**／**L2399** 由 `mergeExtrasToolBuild(jeiTarget, extras)` 算出（真值：`mergeExtrasToolBuild` 定義 **L1033**）。
- `beginAskLoop` 定義 **L614**，簽名 `(question, focusItem, cardFocus, jeiLevel, jeiSummary)`——**冇 toolBuild 參數**；呼叫點 **L299／L2416**，兩處都喺 toolBuild 計算（L276／L2399）**之後**。
⇒ 好消息：D1 要嘅「同 AskEngine L351 逐字同一份文本」**技術上做得到**（call site 有 toolBuild 在 scope）；但要**改 `beginAskLoop` 簽名**（或喺 call site 先算 dropId 再傳入），plan 冇寫呢一步。R1 只講「兩處輸入唔一致」，冇講「時序／簽名」呢層。

### G7. 靜態閘釘住 suppress body（改 D1 會撞）
`tests/check_card_emission_suppression.py` **L84–108** 硬 assert：
- `ModularFrameCards.shouldDropFrameCard(` 必須出現喺 `suppressModularFrameCards` body 內；
- 該 body 內**唔准** `equalsIgnoreCase`；
- `AskService` 內 `setModularFrameDropId(`、`shouldSkipAutoEmit()`（count ≥2）必須仍在；
- `AskEngine` `bindAskToolEnv(` ≥3；`AskLoopState` 要有 `shouldSkipAutoEmit`／`noteSuppressedFrameOffer`／`modularFrameDropId`；`RenderRecipeCardsAskTool` 要有 `suppressedFrameOnly`。
⇒ D1 若改成「先 classify 再 drop」，**唔可以整走上面任何一個 token**。此檔唔在白名單 ⇒ 改壞就即紅。

### G8. 雙樹閘（只改 forge 唔會紅，但有邊界）
`tests/check_dual_tree_sync.py`（allowlist 機制、新 entry 嚴格 FAIL）＋ `tests/check_dual_tree_diff_symmetry.py`（+line 對稱）。`neoforge/README_PAUSED.md` 存在 ⇒ forge-only 缺 twin／非 allowlist 漂移屬 **WARN（exit 0）**。但 **allowlist hygiene 仍然 strict** ⇒ 唔准為咗 forge-only 改動加 allowlist entry。neoforge 樹同樣有 `suppressModularFrameCards` 6 個呼叫點（L165／374／393／2145／2292／2306）＋ `modularFrameDropId` **L2405**，未郁係對嘅（scope）。

### G9. 同類「程式插入否定句」路徑（§0b D 只記錄、未點名）
- `logic/HonestMiss.java` **L137 `ensureAskMissAcquirePlayerVisible(body, lang, force)`**；呼叫點 `AskEngine.java` **L940**，`forceHonestMiss` 定義 **L932**（只 MODIFIED 且 acquire empty）⇒ **程式插入**「未收錄」句嘅地方。
- `AskEngine.java` **L947–949 `looksLikeAcquireMissPin(obtainFill, lang)`**、**L951–956 `AskReplyScrub.ensureHowToGetBody(…, ReplyLang.obtainUnknown(lang))`** ⇒ 另一條**程式插入**否定句路徑（喺 STANDARD 分支 L969 **之前**跑）。
- `AskEngine.java` **L961 `JeiInfoFacts.stripUnspecifiedMiss(body)`**（`JeiInfoFacts.java:409`）。
⇒ 呢幾處正正係 §0b D 講嘅「程式決定性插入 vs 模型散文並存」class，而佢哋**都喺 D4 目標句之外**：D4 只刪「整行 == lang 模板」嘅句。今次 trace 嘅 miss 句係模型自寫（已證 #7），但上面三處係**程式**寫入，D4 唔覆蓋 ⇒ 建議 D4 明確寫「程式寫入路徑（L940／L951–956）喺 STANDARD 已由 L969 覆寫，故唔需要 strip」，否則實作時會有歧義。

---

## H. 額外風險（plan 未列）

1. **繁簡引文**（#10）：plan §0 引文係繁體、真 trace 係簡體。若 S5 驗收用 plan 引文去 grep，會假紅。
2. **D4 只刪「整行相等」**：模型自寫嘅係模板句，但若加咗全角標點／前後空白／前綴（如「目前」前多個字），strip 就 miss → G2 唔會 100%。建議 S2 harness 加「同一句＋前後空白／全角句號／行內有前綴」三種 case。
3. **真假兩條 display 路徑**：真機今次由 tool-emission 路徑出（log `suppressedFrameOnly n=6`＋`cards emitted=1` 同一次 ask）；keyword-fallback 路徑（L2479 一帶）**冇** `suppressedFrameOnly` log ⇒ 唔可以用同一條 log 斷定 fallback 路徑都修好。
4. **D5 log 位置**：plan 話喺 `AskEngine` STANDARD 分支（L969–1000）加 `frame-standard: card kept recipe=… ref=…`。但卡片 refId 係 tool call 期間由 `AskToolEnv.offerEmission`（L61–82）派；呢個分支讀得到嘅係 loop 層狀態（`loop.suppressedFrameOffers()` L532／`loop.cardEmissions()`）。要確認 D5 兩行 log 嘅**資料來源**（推論：應讀 loop 而非 env）。此點 plan 未寫。
5. **baseline 數字要改**（#22）：`零新增紅` 要對 **120/0**，唔係 118+1。
6. **§0b C 表係實作輸入**（cursor-agent 會照行號改）：表內 3 條行號唔準 ⇒ 有機會改錯位，要在派工前先修表。

---

## 附：本次審查用過嘅真指令（可重跑）

```bash
# python 閘 baseline
cd <repo> && for f in tests/check_*.py; do python "$f" >/dev/null 2>&1 || echo "FAIL $f"; done   # → 0 FAIL / 120 檔

# java anchors
grep -rn "shouldDropFrameCard\|suppressModularFrameCards\|modularFrameDropId\|rejectFrameCard" --include=*.java forge/1.19.2/src/main/java

# trace 逐 event 長度／引文
python -c "import json;p=r'<instance>/packai/trace/ask-20260918-182231-tetra_modular_double.jsonl';[print(i,json.loads(l)['event'],len(json.loads(l).get('content') or '')) for i,l in enumerate(open(p,encoding='utf-8'))]"

# latest.log（cp950/ASCII，用 grep -a）
grep -a -n "frame-standard:\|frame-miss: branch\|suppressedFrameOnly\|cards emitted=" <instance>/logs/latest.log
```
