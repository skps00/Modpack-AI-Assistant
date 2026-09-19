# Follow-up #1b **v6（儀器先行＋按 section 補 marker）** — 反方 review **R1（新機制第一輪）**

> 審：`docs/plans/2026-09-19-followup1b-card-marker-fallback-phase-plan.md`（v6 段，第 1–42 行）。
> 只讀唔改。行號一律指 `forge/1.19.2/src/main/java/com/skps9/packai/`；另標全路徑。
> 舊線（v1–v5）R1 3:7 → R2 4:6 → R3 5:5 → R4 6:4 → 1b-v5 R1 **正方 4 : 反方 6**；本輪＝新機制，唔繼承舊比分。

## ① 驗過嘅事實（真／假／未核實）

**真（親跑／親讀）**

1. **`RecipeEmbed.java` 真嘅零 LOGGER**：`grep -n "LOGGER|Logger|LOG\.|println"` → **0 命中**。加 log 有先例：`logic/AskEngine.java:14 import com.skps9.packai.PackAiMod;`（`:354` 等 10+ 處）、`logic/AskReplyScrub.java:850`。⇒ §1「零行為」**可行**（要 pin「只加 `LOGGER.info`，唔可以條件分支」）。
2. **落點行號對**：`AskService.java:403-404 AskResult withCards = withScrollMaterialInline(...).withRecipeCards(cardsOut, true);`；`:2483-2484 AskResult shown = dedupeQuestChatWhenCardShows(withScrollMaterialInline(...) .withRecipeCards(cardsOut, true));`；`:405 dedupeQuestChatWhenCardShows`、`:406 logDisplayBody(shown...)`、`:407 finishAskTrace(loop, shown, "ok")` ⇒ **`:406` 之後注入＝零 UI 效果，句句為真**。
3. **`emissionSectionOf` 真身 `:1121-1136`，但係 `private static`**，回傳 **4 個值**：`0=GET, 1=USE, 2=UPGRADE, -1=dump before sources`（`:1120 /** 0=GET, 1=USE, 2=UPGRADE; −1 = dump before sources. */`、`:1125 isUpgrade()→2`、`:1128 isInputUse()→1`、`:1131 isMaintenance()→-1`、`:1135 return 0`）。**plan §2 寫「回 wantSec 0=obtain／1=use」＝漏 2 同 −1。**
4. **`HOW_TO_GET_HEAD` `:65-66` 係 `private static final`**；再用 `grep` 見 `emissionSectionTypeOf`(:1240)／`isEmissionSectionHeading`(:1260) 全部 private；`AskService.java:1668 AUTO_EMIT_USES_HEAD` 係**另一份**用語版。⇒ 由 `AskService` 層揀 section 要（a）改 `RecipeEmbed` 可見性，或（b）第 3/4 份 heading regex。
5. **線號真身對得上**：`disperseUnplacedEmissionCards :889-982`；plan 引嘅 `:919-925` 只是 `cand.isEmpty()` 分支；**同一個 method 另有 3 個插入位**：`:907-909 if (wantSec < 0) { blocks.add(Part.card(i)); continue; }`（絕對最尾）、`:921-922 insertAt = blocks.size()`（section 唔存在＝最尾）、`:954-967 no needle match → sectionLastAfter`（`chosenStep=-1`）。`sectionLastAfter :1010-1042` ✓。
6. **Fallback 實際基線（我即場量，7 份真 trace）**：`cardsOut` 同 `[card:N]` 數 → fallback 卡數 = **1,0,1,0,1,3,0**（4/7 次 >0，57%）；`ask-20260919-154224-minecraft_stone_axe` **cardsOut=5、只得 2 個 marker ⇒ 3 張走 fallback**。⇒ A1「預期 >0」**用現成產物已可證**，唔需要真機跑都知會 >0。
7. **`[card:N]` 真身可以生還全鏈**：`AskResult.java:84-97 withRecipeCards` 會過 `AskJeiHints.scrubAbsenceClaimsWhenCards` → `RecipeCardsMode.scrubMarker`（`:185-191` 只剝 `MARKER`＝`[[recipe_cards:on|off]]`，`:30-34`）→ `AskReplyScrub.scrubPromptEcho`；實測 trace `display.body.final`（＝post-`withRecipeCards`）**仍帶 `[card:1]`／`[card:2]`**；`AskService:2444 stripAiRecipeCardMarkers` 只剝 `AI_RECIPE_CARD_MARKER = "\\[\\[recipe_cards?:[^\\]]*]]"`（`:2184-2185`）＝雙括號，**唔中單括號 `[card:N]`**。
8. **A5 baseline 我親跑**：`ls tests/check_*.py | wc -l` → **122**；全跑 → **121 綠 ＋ 1 紅**（`tests/check_ask_display_leak.py` RC=2，輸出 `NO LOG LINES (need real-machine smoke)`；docstring `:20-21` 明寫 **2 = 唔算 pass**）。Java 測試檔 **49**（`find src/test -name "*.java" | wc -l`）⇒ plan A5 數字對得上。
9. **新 log 唔會絆倒 leak 閘**：`check_ask_display_leak.py:46-48` 只 parse 兩個前綴 `Pack AI display body ver=`／`Pack AI ask reply before ensureCards:`；plan 新行 `packai card-place:` 唔中。**但 `:100` 已有 per-card 行 `Pack AI card {}` 嘅斷言（`tests/check_ask_cards_debug_log.py`）** ⇒ 改 AskService 既有 log 才有風險。
10. **第二條路徑係死碼**：`grep "askBlocking"` 全 `src` 只有 `:2262`（定義）＋`:2268/:2275`（overload 互 call）；harness 走 GUI（`client/autotest/AutoTestHarness.java:419 AiAssistantScreen.openAndAskAbout(stack)`）。⇒ 「兩條路徑共用 helper」改第二條＝純 diff、零證據。
11. **Python 閘冇能力重現 `RecipeEmbed` 行為**：`tests/check_ask_card_fallback.py` 等係**讀 Java 源碼文字**式斷言（如 `tests/check_recipe_embed.py:476-503 assert "interleaveEmissionCards" in embed`）；`src/test/**` **冇一個 test 建 `RecipeCard`**（`AskCardPlacementCheck.java:9 "Does not construct RecipeCard (needs Minecraft registry)"`；grep 只有 `RecipeCardAlign.Fingerprint`）⇒ 新 `tests/check_card_placement_fallback.py` 只能做 source-text 斷言，**測唔到落位**。
12. **maintenance 卡真嘅會入 cardsOut**：`AskService.java:2108-2158 collectAskRecipeCards`：`:2123/:2141 maint.addAll(parts.maintenance())` → `:2157 out.addAll(maint); return List.copyOf(out);`。

**未核實／推論**（已標明）

- U-a：**「卡去咗最尾」喺 7 份真 trace 冇一個實例**（我 parse 全部 7 條 `display.body.final`，冇卡落喺【来源】前最後一行）。「堆埋」有實例（stone_axe 3 張），「去最尾」**未有 artifact 證據**。
- U-b：以下 ②-O1 嘅 index 等同性係我**用 Python mirror 跑真 body**（port `splitTextIntoStepBlocks`／`sectionLastAfter`／first-line 判定，`GET/USE/UPG/NUM` regex 逐字抄 `:63-72`）得出，**未跑真 Java**：stone_axe `sectionLastAfter(0)=4`、section-0 最後一行所屬 block=3 ⇒ insert-after=4 ⇒ **兩者同一個 index**。

## ② 問題（severity ＋ 證據）

- **O1 CRITICAL（新）—「補 marker 落 section 最後一行」同今日 fallback 落**同一個 index**，改完位置零變化，但判準會變綠。** 進行流程：`placeEmissionCardsByRef :819 skipCardsAfter(blocks, i+1)` 插喺「**含該 marker 行嘅 block**」之後；而 `sectionLastAfter :1010-1042` 插喺「**wantSec 最後一個 block**」之後。`splitTextIntoStepBlocks :848-882` 只喺 heading／numbered 處切 block（`:864 boundary = isEmissionSectionHeading(line) || isNumberedStepLine(line)`）⇒ **section 最後一行一定住在該 section 最後一個 block 裡面** ⇒ 兩個 anchor 係同一個 block，兩邊都行 `skipCardsAfter` ⇒ **index 完全相同**。真實量到：stone_axe（`ask-20260919-154224`）5 卡 3 張 fallback，`sectionLastAfter(0)=4`，marker-on-last-line 亦 = 4（U-b）。⇒ §2 對「卡堆埋」**零修復**，但 §4 A2（`byFallback=0`）會綠 ⇒ **驗收假綠**（同 v5 O1、R4 O1 同一族，換咗一層）。
- **O2 CRITICAL（新）— 對今日已經 needle-disperse 到正確步驟嘅卡，§2 係**回歸**。** `:926-967`（needle score>0 分支）會把 fallback 卡**散落到真正提到該配方嘅 step 之後**，`:884-887` 註釋原話：「place leftover emission cards on distinct numbered steps … **so multi-card uses do not sticky-cluster at section end**」——即 code 本身**已經**係為「唔堆埋」而設。§2 改成一律拉去 section 尾＝**由散落變堆埋**（同一 section ≥2 張卡全部貼同一行 ⇒ 同一 anchor）。⇒ 修法「永遠中性或更差」，冇一個 shape 會變好。
- **O3 HIGH（新）— 判準唔係等價於症狀。** `byFallback>0` 只＝「有一張卡冇有效 `[card:N]`」。反例（真）：`ask-20260919-140842`（cardsOut=1、0 token、fallback=1）身體係 `怎么来:合成台（有序合成）：…` **單行單 block**，`sectionLastAfter(0)=2` ⇒ 卡落喺 **`怎么来` block 之後、`怎么用` 之前＝正確位**。⇒ 「fallback>0 ⇒ 堆埋／去最尾」**假**；用今日 7 份 trace 計，4/7 次 byFallback>0 之中至少 1 次係正確落位。
- **O4 HIGH（新）— A2 目標 `byFallback=0` 結構上**不可能**達到。** ① `:907-909 if (wantSec < 0) { blocks.add(Part.card(i)); continue; }`：`emissionSectionOf` 對 maintenance 回 −1（`:1131-1133`），而 maintenance 卡真嘅會入 cardsOut（事實 12）⇒ **有 maintenance 卡 ⇒ byFallback 恆 ≥1，任何 marker 都救唔到**。② 「去最尾」嘅真身係 section heading **唔存在**（`sectionLastAfter` 回 −1 → `:922 insertAt = blocks.size()`）；§2 寫「喺佢自己嗰個 section 嘅最後一行」——該 section 唔存在時**冇定義** ⇒ byFallback 留 >0 ⇒ A2 紅。⇒ 判準同症狀兩個 half 都對唔上，而 A2 會逼實作去湊數（例如偷偷唔記 `wantSec<0`）。
- **O5 HIGH（新）— §2 引用嘅 API 喺落點**拿唔到**，兼且語意寫錯。** `emissionSectionOf` `private static`（事實 3）＋ heading regex 全 private（事實 4）⇒ 由 `AskService` 揀 section 必須（a）擴白名單改 `RecipeEmbed` 可見性，或（b）抄第 3/4 份 regex（v5 O4 已捉過同款）。plan §2 只寫「用現成 `emissionSectionOf`」＋白名單只寫「`RecipeEmbed`（儀器 log）」⇒ **白名單同修法自相矛盾**。
- **O6 MED-HIGH（新）— A3 係 tautology。** `blocks.add(insertAt, Part.card(i))`（`:972`）每插一張就令 list 長一格 ⇒ 卡嘅 **block index 天然互不相同**。「每張卡 idx 互不相同」**點都綠**，冇鑑別力；而「各自落喺對應 section」只驗 counter 唔驗**同一 anchor 有幾張**（堆埋嘅真身）。要有距離式判準（見 Flip conditions）。
- **O7 MED（新）— 儀器詞彙表覆蓋唔到實際狀態機。** plan log 只有 `mode=<marker|fallback>` ＋ `section=<obtain|use|none>`：實際有 **4 個插入位**（O4 事實 5）＋ section 有 **4 值**（0/1/2/−1，事實 3）⇒ `section=none` 混埋 `upgrade` 同 `maintenance`，而兩者處置完全唔同。另外 `item=<id>` 未寫死用邊個欄位（`RecipeCard.primaryOutputId()` `:437` vs `withSourceItemId` `:133`／`sourceItemId`）——v5 O3 同一坑。
- **O8 MED（新）— A1/A2 樣本量不足（實算）。** n=3：0 失敗時 95% 上界 p≤**0.632**；n=6：p≤**0.393**；要 p≤0.10 需 **n≥29**（0 失敗）或 n≥46（准 ≤1 失敗）。假過率：真 p=0.20 時，n=3 有 **51%**、n=6 有 **26%** 機率「零失敗」而誤判達標。而且 `AutoTestHarness.java:43 MAX_CASES = 20`、`:49 BUDGET_MS = 20 分鐘` ⇒ **一次 run 已可做 20 case**，冇理由做 3＋3。
- **O9 MED（新）— 新閘宣稱驗唔到行為（事實 11）。** 「fixture：冇 marker＋section 已知 ⇒ 補喺正確段」用 python 做唔到（唔可能跑 `interleaveEmissionCards`），用 Java 亦做唔到（冇 test 建 `RecipeCard`）⇒ 出到嘅只有 source-text 斷言（＝測你自己寫嘅 mirror／字串）。要嘛明文承認係「源碼形狀閘」，要嘛先建 MC bootstrap harness（新成本，plan 未寫）。
- **O10 LOW-MED — 第二路徑改動係零證據 diff（事實 10）**；另 A4「cardStrip 關 ⇒ 唔補」測唔到生產態：AI 分支**硬編碼 true**（`:404`／`:2484`），非 AI 分支根本入唔到 helper（v5 事實 2）。
- **正方收貨點**：§1 儀器**可行且值得**（LOGGER 先例齊、真嘅係量到落位嘅唯一方法）；落點行號／`:406` 之後零效果**句句真**（事實 2）；`[card:N]` 生還全鏈有真 trace 支撐（事實 7）；A5 數字對得上實測 baseline（事實 8）；leak 閘唔受影響（事實 9）；A1「>0」用現成 7 份 trace 已可證（事實 6）——**即係 §1 唔需要真機跑都交到第一個數**。

## ③ 比分 ＋ flip conditions

**正方 3 ： 反方 7**（遠未達 8:2；載重決定存活表：LD1 判準**死**、LD2 儀器**半**、LD3 機制**死**、LD4 落點**死**、LD5 白名單／行號**存活**、LD6 驗收**多數死**、LD7 回歸**半** ⇒ 4/7 死）

**Flip conditions（下一輪 checklist；全部可即場寫死）**
- **Fc1（回應 O3/O1，必改）**：判準改成**落點式**，唔係 marker 式。每張卡除 `mode/section/idx` 外，**必須**記 `anchor=<step:<n>|secTail|absEnd>` ＋ `dist`（= `idx` 同「needle 命中嘅 step block index」之差）；症狀 predicate 改成 `anchor==absEnd` **或**（同一 anchor 上 ≥2 張卡 且 該 anchor 唔係任何一張卡嘅 match step）。並明文：`byFallback>0` **唔等於**症狀（附 140842 反例）。
- **Fc2（回應 O2，必改）**：明文寫「**唔准**把 fallback 卡一律拉去 section 尾」；修法要保留 `:926-967` needle dispersal 嘅判決，只在（i）無 step 可選 或（ii）`absEnd` 時才介入。並寫明「本修法對 O1 證明同 index 嘅 shape **預期零變化**，唔可以當已修」。
- **Fc3（回應 O4，必改）**：A2 目標由「`byFallback=0`」改成可達命題——例如「`anchor∈{absEnd}` 嘅卡 = 0」＋「`wantSec<0`（maintenance）同 section heading 唔存在嘅卡**明文剔出**，另立 log 標記 `sec=upgrade|maint|missing`」。若堅持 `=0`，要交一條「section heading 唔存在時點造位」嘅規則（含 heading 缺失時不准改文案）。
- **Fc4（回應 O5，必改）**：白名單二選一寫死——(a) `RecipeEmbed` 加 **public static** helper（同時列出可見性改動），或 (b) 明寫 section 判定位一律**落喺 `RecipeEmbed` 內部**（即改 UI／interleave 層，唔改 `AskService` 文字），並刪走 §2「AskService 用 `emissionSectionOf`」句。§2 尾句「二選一由 review 定」**要自己揀一個**（plan 唔可以留兩條路）。
- **Fc5（回應 O6）**：A3 由「idx 互不相同」改成「**anchor 相同嘅卡數 ≤1**」＋「每張卡 `dist ≤ 1`（或落喺自己 match 嘅 step 之後）」。
- **Fc6（回應 O7）**：log 詞彙寫死：`section=0|1|2|-1`（或 `obtain|use|upgrade|maint`）、`item=` 用邊個 accessor、`n=` 用 1-based、`idx=` 用 `blocks` 內 index（並講明唔係 UI 行號）。
- **Fc7（回應 O8）**：A1/A2 改成 n≥29（0 失敗，p≤0.10）或明文寫「n=6 ⇒ 只可聲稱殘餘率 ≤39%」；改用 `AutoTestHarness` 一次 20 case 跑；並明文「baseline 樣本會漂移，每輪重跑」。
- **Fc8（回應 O9）**：新閘明文標「**源碼形狀閘**（唔驗行為）」＋加一條**負控**（打亂 `sectionLastAfter` 或 `disperseUnplacedEmissionCards` 應該要紅），否則唔算閘。
- **Fc9（回應 O10）**：第二條路徑要嘛明文「唔改（死碼，零 production caller）」，要嘛列一樣可證嘅理由。

## ④ 最貴未知

- **U1（最貴）**：**SK 講嘅「卡去咗最尾」今日到底由邊條路徑產生？** 7 份真 trace 冇實例，「堆埋」有實例但落點同 marker 版**同 index**（O1）⇒ 照 v6 做完，你可能見到 `byFallback=0` 而畫面一模一樣。解開成本最低：**1 次 `sectionLastAfter` 回 −1／`wantSec<0` 嘅真 trace**（即揾一條卡落喺【来源】前最後一行嘅 ask），或加 §1 log 時順手用 `anchor=absEnd` 見到就知。
- **U2**：`mode=marker` 但 N 指向**錯 step** 嘅比例（走位嘅另一半）——`byMarker=cards` 全綠都證明唔到落位對，要 Fc1 嘅 `dist` 指標。
- **U3**：`AutoTestHarness` 20 case 嘅 case 表由邊個供（`tmp-check.gradle`／config？）——A1/A2 擴大樣本前要 pin 落 plan，否則實作者會用預設 3 個。
- **U4（實證缺口）**：maintenance 卡實際出現率（我未跑真機）——若常見，O4 直接令 A2 永遠紅。
