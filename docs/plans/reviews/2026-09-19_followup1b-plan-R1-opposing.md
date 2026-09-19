# Follow-up #1b **v5（形狀無關 marker fallback）** — 反方 review **R1（新機制第一輪）**

> 審：`docs/plans/2026-09-19-followup1b-card-marker-fallback-phase-plan.md`（v5）。
> 反方自跑命令核實（只讀唔改）；引文逐字。行號一律指 `forge/1.19.2/src/main/java/com/skps9/packai/`，測試／trace 另標全路徑。
> 舊線 R1 3:7 → R2 4:6 → R3 5:5 → R4 6:4 未達 8:2；本輪＝**新機制新一輪**，唔繼承舊比分。

## ① 驗過嘅事實（真／假／未核實）

**真**

1. **兩條路徑「共用 helper」技術上做得到，但第二條係死碼。** `askBlocking` 全 repo（排除 `neoforge/`、`.hermes/backups/`）**零 production caller**——只有自身 overload（`AskService.java:2262/2268/2275`）＋文件／測試文字引用。Harness 路徑係 GUI：`AutoTestHarness.java:419 AiAssistantScreen.openAndAskAbout(stack)` → `AiAssistantScreen.java:127 screen.askAboutStack(stack)` → `:347 ChatSession.setPendingItems(...)` → `:303 startAsk(...)` ⇒ **只行 `runAsk`（askAsync）**。
2. **卡嘅最終清單兩條路徑都係 `cardsOut`**（`:400`／`:2479`、`:2497`）；strip **硬編碼 true**：`:403-404 .withRecipeCards(cardsOut, true)` ／ `:2483-2484`；非 AI 分支才 `.withRecipeCards(cardsOut)`（`:446`／`:2503` → `AskResult.java:79-81` 繼承 `cardStrip`）。⇒ **AI 分入面根本冇 `cardStrip` 變數可判**。
3. **補 marker 必須喺 `withScrollMaterialInline(...).withRecipeCards(...)` 之前**（`:403-404`／`:2483-2484`）：`shown` 由該處導出，`finishAskTrace`（`:503`、body 喺 `:509-512`）只記 `shown.answer()`；`:405 dedupeQuestChatWhenCardShows`（`:661-673`）同 `:406 logDisplayBody` 都用 `shown`。plan 寫「卡已算好之後、寫 trace 之前」＝ **:400→:407 一整段**，落喺 :406 就零 UI 效果。
4. **A1/A2 統計唔需要改 harness**：`finishAskTrace` 已 emit `display.body.final{src,body}`（`:509-512`）＋ `render.cards.final{cardsOut,role,item,primaryOutputId}`（`:521-527`），`AutoTestHarness.java:658-663` 亦已讀兩者；status json 有 `traceFile`（`:763`，欄位＝`id/status/elapsedMs/cardsOut/traceFile`）。
5. **消費端 X＝cardsOut 1-based 位置，語意正確**：`RecipeEmbed.java:804-805 int idx = n - 1; if (idx >= 0 && idx < cards.size() && !placed[idx])`；UI 傳嘅 `cards`＝`msg.recipeCards()`（`AiAssistantScreen.java:839 interleaveEmissionCards(cleaned, cards)`）；token 一定被消費／剝走（`:768 stripCardRefTokens` 無條件跑）。⇒ plan 棄用 `refId`（`AskToolEnv.java:83 int refId = pendingEmissions.size() + 1;`）係**正確決定**。
6. **`[card:N]` 生還全鏈到 UI**：7 份 trace 中 5 份 `display.body.final`（＝post-`withRecipeCards` 嘅 body）仍帶 `[card:1]`／`[card:2]` ⇒ withRecipeCards 三個 scrub＋`finalizeAnswer` 都唔剝 token。
7. **「隨機」有真機證據**：同一物品 `tetra:modular_double` 兩次 run——`ask-20260919-140842` body **零** token、`ask-20260919-154132` body 有 `[card:1]`（兩者 `cardsOut=1`）；5 個沙盒 log 共 16 條 `Pack AI display body ver=` 中 7 條有 `card:`、9 條冇（`zcat debug-*.log.gz`）⇒ 觸發條件會發生，頻率唔低。
8. **kept≥2 真實存在**：`debug-2`／`debug-4` 各 1 次 `frame-standard: cards kept=2 dropped=0 refs=1,2`，`kept=1` 共 8 次（10 次 STANDARD frame ask 中 2 次＝20%）；但 `ModularFrameCards.java:120-137` **保留原本次序**（只 skip 非 chosen 嘅 frame 卡）⇒ output 卡排第 1 **冇結構保證**。
9. **落點等效性（本輪關鍵）**：role=output 卡 `wantSec=0`，冇 token 時 `:920-925` → `sectionLastAfter(blocks,0)`（`:1010-1042`）＝插喺 obtain 段**最後一個 block 之後**；有 token 喺 obtain 行時 `:819 skipCardsAfter(blocks, i+1)`＝插喺**該 block 之後**。obtain 段只有一個 block 時**兩者同一 index**——真機 140842／154158/140904 嘅 obtain 段正是單一 block（body 第 3 行＝`怎么来:…`，跟住即 `怎么用:`／`【来源】`）。
10. **唔會絆倒 `tests/check_ask_display_leak.py`**：`:62 CARD_PARAM_VALUES = ("ino_dlc_build:cross_z_build_full_bottle",)`、`:63 BARE_ID_RE`、`:64 ROLE_EQ_RE` 三個都唔中 `[card:1]`；該閘只 parse `Pack AI display body ver=` 前綴行（`:45 MARKER`）⇒ plan 新 LOGGER 行（唔係該前綴）不受影響。122 閘實數＝**122**（`ls tests/check_*.py | wc -l`）。
11. **冇現成「AI interleave 位置」測試可補**：`RecipeEmbedCheck.java` 完全唔 construct `RecipeCard`／唔 call `interleaveEmissionCards`（`:10-11` 註明「Does not construct RecipeCard (needs Minecraft registry)」）；AI interleave 現時只由 Python **讀 Java 源碼**式斷言覆蓋（`tests/check_recipe_embed.py:502-503 assert "interleaveEmissionCards" in screen/embed`）。`AskCardPlacementCheck.java` 測嘅係另一套 `[[recipe_card:N]]` fallback。

**未核實**：49 Java 測試數（未跑）；閘 121 綠 baseline（繼承前輪）；A1「3 次同一物品必有 1 次失敗」嘅機率（樣本 2 次得 1 次，n 太小）。

## ② 問題（severity ＋ 證據）

- **O1 HIGH（新）— 驗收只量「marker 存在」，唔量「卡落點」；而唯一有真機樣本嘅情境預期係零可見變化。** 事實 9：kept=1＋role=output（STANDARD 8/10 次）時，token 落點同今日 fallback 落點同一個 index ⇒ 修完 A2（`hasMarker=false` 次數＝0）**自動成立**，同 R4 O3「判準恆真」同類。A3 對照 `minecraft:stone_axe` 本身已有 token（154224 `tokens=['1','2']`）⇒ 唔會觸發 helper，只算「已有 marker 唔碰」負控。**全綠唔等於症狀修好**（R4 O1 同一陷阱換一輪再現）。
- **O2 HIGH（新）— 注入 token 會令卡退出 fallback，產生「比今日更差」嘅新失敗模式。** `:805-809 placed[idx]=true`＋`:766 disperseUnplacedEmissionCards` ⇒ 貼錯行嘅卡唔會再由 fallback 兜。plan §6 嘅「冇該標題 ⇒ 最後一行 before 【来源】」分支會命中 uses 段——真機 sword body（154158）`【来源】` 前最後一行係 `3. 改造方向…`（屬 怎么用 段）⇒ 卡會被拉入 uses 段。plan §6 只寫「A3＋人眼核」，A3 測唔到（stone_axe 唔觸發），零 fixture。
- **O3 MED-HIGH（新）— X 回退「比唔中則 1」不安全，且比對欄位未寫死。** 事實 8：keep≠空時次序＝LLM 發射次序，output 卡冇結構保證喺 index 1；真機 7/7 output 卡在 index 1（140911：card#1＝output `tetra:modular_single`、card#2＝input `mrqx_extra_pack:mystery_craftsmanship`）**只係樣本細**。「identity 比對 item id」亦冇指明用 `sourceItemId`（每張卡都＝焦點物）定 `primaryOutputId`（output 身份）——只有後者對得上 7/7 trace。
- **O4 MED（新）— 定位 how-to-get 行要第**三**份 regex，而兩份現有嘅都係 private。** `AskReplyScrub.HOW_TO_GET_HEAD`（`:78-79`，private，終結符 `(?:[:：]|\s|\z)`）／`RecipeEmbed.HOW_TO_GET_HEAD`（`:65-66`，private，`(?:[ \t]*[:：].*)?$`）；§4 白名單**唔准改 `AskReplyScrub.java`**，而 `AskService` 已有第三份**唔同**嘅 uses 版（`:1668-1669 AUTO_EMIT_USES_HEAD`）。三份 label 集合唔一致（例：`取得方式/获取方式` 兩份有、`怎麼取得/怎樣獲得` 只有 RecipeEmbed 有）⇒ 同義詞唔命中就靜默跌落 O2 條分支。plan 冇指明抄邊份。
- **O5 MED（新）— 注入點未 pinned。** 事實 3：唯一有效區間係 `:400` 之後、`:403` 之前（且要 `finalResult = finalResult.withAnswer(injected)`，多行一次 `finalizeAnswer`）。落喺 :406 會「marker 只入 log／trace、UI 零效果」，而 A2 照樣綠（併 O1 更危險）。
- **O6 MED — 覆蓋比症狀窄**：「有 marker 但 obtain 行冇被引用」完全唔碰（真機 140911 `cardsOut=2` 只 1 個 token；154224 `cardsOut=5` 只 2 個；140904 唯一 token 喺 uses 段）。SK 講嘅「卡走位」最可能就係呢類。
- **O7 LOW-MED — A6 冇可證偽性**：「injected 次數＝(A1 baseline 失敗次數之期望) 相符」冇判準；新 log 行亦冇記 `X` 同「搵到標題 vs 跌落最後一行」分支 ⇒ O2／O3 兩條風險在 log 上不可見。
- **O8 LOW — 「唔准只改一條」係死碼要求**（事實 1）：`askBlocking` 零 caller、harness 亦只行 runAsk ⇒ 改第二條只擴大 diff、零新證據。
- **正方收貨點**：觸發條件真係形狀無關（事實 7 同一物品一次有／一次冇）；層揀得對（有 `cardsOut` 嗰層）；X 語意同消費端對齊（事實 5）＋正確棄用 `refId`（繞開 R2/R3 O1 遺留）；`[card:N]` 生還全鏈（事實 6）；統計唔使改 harness（事實 4）；leak gate 唔受影響（事實 10）。

## ③ 比分 ＋ flip conditions

**正方 4 ： 反方 6**（未達 8:2；R1＝新機制第一輪，依 SK 規則可再改再審，但**O1／O2 必須先改**才值得進 R2）

- 正方得分：形狀無關觸發（真機證據支持）；層／變數揀對（`cardsOut`）；X 1-based 語意核實正確；棄 `refId` 正確；token 生還到 UI；統計免改 harness；1 檔＋1 新閘最小 diff。
- 反方得分：O1（量度代理而非落點，真機樣本下預期零變化、判準自動綠）；O2（新失敗模式，零判準）；O3（回退 1 不安全＋欄位未寫死）；O4（第三份 regex＋白名單互斥）；O5（注入點精度）；O6（覆蓋窄於症狀）。

**Flip conditions**
- **Fc1（必改，回應 O1）**：A1/A2 加**位置判準**——Python 讀 `display.body.final`，斷言 `[card:N]` 所在行＝obtain 標題行（string-level，唔需要重寫 `splitTextIntoStepBlocks`）；並明文「若今日 fallback 已落同一 block ⇒ 本 fix 對該 case 零可見效果」，要求至少 1 個「fallback ≠ token 落點」嘅 drive case（obtain 段多 block，或 role=uses）。
- **Fc2（必改，回應 O2）**：落點規則收窄——**只准喺 obtain 標題行存在時貼**；冇標題⇒明文**唔補**（刪「最後一行 before 【来源】」）；或加 fixture：body 含 怎么用 段＋【来源】，斷言 marker 唔准落 uses 段。
- **Fc3（回應 O3）**：刪「比唔中則 1」，改「比唔中 ⇒ 唔補」或「貼前先斷言 `cardsOut.get(0)` 係 output」；寫死比對欄位＝`RecipeCard.primaryOutputId()`（用 7 份 trace 做素材），並加 kept=2 fixture（`debug-2`／`debug-4`）。
- **Fc4（回應 O4）**：明文抄邊份（建議 RecipeEmbed 版，因 `.*$` 同「行尾」定義一致）＋列已知同義詞缺口；或改為喺 `RecipeEmbed` 加 public helper（要擴白名單，但消滅重複源）。
- **Fc5（回應 O5）**：注入點寫死「`withScrollMaterialInline(...).withRecipeCards(...)` 之前」並引 `:403-404`／`:2483-2484`，明文「:406 後注入＝零 UI 效果」。
- **Fc6（回應 O7）**：log 加 `x=<> matched=<bool> anchor=<head|lastLine|none>`。
- **Fc7**：白名單寫明統計 script 讀 trace、唔改 `AutoTestHarness.java`（或明文由 Hermes 即場跑）。

## ④ 最貴未知

- **U1（最貴）**：「零 marker」時卡實際落去邊、同 token 版差幾多——`RecipeEmbed` 現時**零 interleave log**（R2 已核）＋harness 冇截圖。唔解 U1，A1/A2 全綠都證明唔到玩家症狀修好（O1）。取得成本：1 個擴大本輪白名單嘅臨時 build（加 interleave log），或 SK 空閒時 1 次目視。
- **U2**：kept≥2 時 output 卡係唔係恆在 index 1（現時 7/7 trace ＋ 2/2 log，但 `ModularFrameCards:120-137` 零結構保證）——要 1 次針對性真機跑（同款含 input-use 卡問法）。
- **U3**：`cardStrip=false`（KEYWORDS／ALWAYS）會唔會行到新 helper——事實 2 顯示 AI 分支 hardcode true、非 AI 分支永遠入唔到 helper，所以真正要驗嘅係「helper 冇被誤 call 出 AI 分支」＝靜態閘可覆蓋；plan 寫嘅 fixture「cardStrip 關 ⇒ 唔補」測唔到生產路徑（成本低，但屬未核實）。
