# 反方 R1 — Follow-up #1 phase plan（框架卡 `[card:N]` 標記消失）— OPPOSING

> 審：`docs/plans/2026-09-19-followup1-frame-card-marker-phase-plan.md`
> 反方 reviewer（本人獨立跑命令核實；只讀唔改）。全部引文係逐字。
> 前提：今日已有 P2／P1 兩 plan 因「症狀重現唔到」撤回 ⇒ 本輪對「症狀是否真成立」加嚴。

---

## ① 驗過事實（真／假／未核實）

| # | 判 | 事實＋證據 |
|---|---|---|
| F1 | **真** | plan §1 引 sword trace 四段**逐字屬實**。`ask-20260919-154158…sword.jsonl` idx62 `model.reply.final` 含 `…这是标准空白模组剑的该材料版本合成。 [card:1]`；idx63 `check.scrub` rules=`scrubPromptEcho`，`before`→`after` 只差 `<!--packai:items=tetra:stonecutter|切石器-->`，`[card:1]` 仍在；idx64 第二次 `check.scrub` rules=`stripDuplicateSectionHeaders`，其 `before` **已經**係 `怎么来:合成台（无序合成）：切石器 + 木棍 -> modular sword。这是空白模组框架合成版本。` 且**冇** `[card:1]`（＝plan 講嘅兩次 scrub 完全對）；idx65 `display.body.final` ＝idx64 after；idx66 `cardsOut=1`；idx67 `recipe_card_markers=[]; emissionRefs=[1]`。 |
| F2 | **假** | plan §1「對照 `minecraft:stone_axe` 同一機制**有** 5 個標記（`[card:1]`…`[card:5]`）」**唔成立**。`.stone_axe.jsonl` idx49 `render.markers` ＝`{"recipe_card_markers":[],"emissionRefs":[1,2]}`；其 `model.reply.final`(idx44) 只有 `[card:1]`、`[card:2]` **兩個**引用；`cardsOut=5`（idx48）。⇒ 把 5 張卡當 5 個標記。 |
| F3 | **假** | plan §2 根因「經 `HonestMiss.insertLineBeforeSources(...)`（`HonestMiss.java:137-199` 一帶）改寫『怎么来』段」**錯檔錯行**。真兇＝`AskReplyScrub.replaceHowToGetBody`（`AskEngine.java:980`），其 javadoc 逐字寫「**Replace how-to-get section body with fill (heading kept)**」（`AskReplyScrub.java:943-948`）。`insertLineBeforeSources` 其實喺 `HonestMiss.java:252`；`:137-199` 係 `ensureAskMissAcquirePlayerVisible`／`frameStandardRecipeLine`／`ensureFrameStandardRecipeVisible`。 |
| F4 | **真（plan 未引，但反証其 attribution）** | 兩條分支行為**相反**。`tetra_modular_double`：model 冇寫 `^怎么来[:：]` heading（寫成「2. 怎么来（合成台）：…」）→ `display.body.final`(idx71) **仍然保留** `[card:1]`，只係喺【来源】前**多插**一行確定性句（＝`insertLineBeforeSources` 路徑，marker 冇失）。⇒ 標記消失**只**發生喺 `replaceHowToGetBody` 分支（sword）。plan 指錯嘅正係「保住 marker」嗰個 function。 |
| F5 | **真** | `frameStandardRecipeLine`（`HonestMiss.java:150-176`）只組 lang 字串（`:163 ReplyLang.tr(...)`、`:167-174` variants 尾接），**唔帶任何 marker**。 |
| F6 | **真** | `[card:N]` 係 prompt **認可** token，N＝tool 發嘅 ref：`lang/en_us.json:424`「each use/recipe is its own numbered step ending in **[card:N] (N from the tool)**」「write **[card:N]** at that step's line end (N = id from render_recipe_cards)… never invent an N the tool did not return」；被禁嘅係 `[[recipe_card:N]]`／`[[recipe_cards…]]`／`[[recipe:…]]`／`{{RECIPE}}`（`:424`、`:392`）。⇒ 修法 A／B **冇**違反「正文禁止卡標記」——該禁令唔覆蓋單括號 `[card:N]`。 |
| F7 | **假（儀器錯）** | `AskTrace.markers()` 掃嘅係 `[[recipe_card:`：`AskTrace.java:759` `String needle = "[[recipe_card:";`——**唔係** `[card:N]`；而 AI 模式會剝雙括號家族（`AskService.java:2444 stripAiRecipeCardMarkers`，pattern `:2184-2185` `\[\[recipe_cards?:[^\]]*]]`）。⇒ `recipe_card_markers` 對 `[card:N]` **結構性盲**，四條 trace（含 double／sword／scissor 全部）一律 `[]`。plan §1「`recipe_card_markers=[]` ← 內文冇引用」係**無效推論**（sword 結論碰巧啱，但只能由 `display.body.final` 讀出）。 |
| F8 | **真** | `[card:N]` 消費端係**位置索引**：`RecipeEmbed.java:804-805` `int idx = n - 1; if (idx >= 0 && idx < cards.size() && !placed[idx])`，whitelist `N∈[1..cards.size()]`（`:774`）。而 refId 係 emission 序：`AskToolEnv.java:83 int refId = pendingEmissions.size() + 1;`、`CardEmission.java:8`「refId is the ask-scope `[card:N]` id (1-based; 0 = unset)」。兩者**只在「shown strip 冇 drop、順序保留」時才一致**。 |
| F9 | **真** | AI 模式**冇** card fallback：`AskService.java:2443-2444`（AI 分支）→ `stripAiRecipeCardMarkers`；`AskCardFallback.ensureCards` 只喺 `:2488` KEYWORDS 分支。⇒ 唯一引用通道＝模型自寫 `[card:N]`（plan 呢點方向啱）。 |
| F10 | **真** | 現有 Python 閘 baseline ＝ **121 綠 / 1 紅**（唯一紅 `tests/check_ask_display_leak.py`，符合 `AGENTS.md` gotcha：要有真機 `latest.log` 內容；現時 `latest.log` mtime 09-19 00:07、內含 0 條 frame-standard）。plan §5 A4「≥121 綠、0 新紅」同 baseline 一致（A4 ✓）。 |
| F11 | **真** | `AskMarkerRepair.collectAllowed` 確實只由 FACT 抽「已出現嘅精確 marker」（`:41-56`；`:53-54`「never synthesize embeds from bare ids (**invent ban**)」）；`ANY_MARKER`（`:21-32`）唔覆蓋 `[card:N]`。⇒ `allowedExact` 真係限於已存在標記（plan 呢點方向啱）。 |
| F12 | **真（plan 冇提）** | 整段取代會**刪走 LLM 段內容**：sword idx64 `before` 已冇「2. 部件来源：本地索引没有收录 切石器 部件的掉落／宝箱／任务路径…」。plan 冇任何條文管呢個資訊損失。 |
| F13 | **未核實** | sword run 當時行咗 `replaced` 定 `appended`：trace jsonl **唔記** `LOGGER` 行；instance `logs/latest.log` 該時段已 rotate（0 條 frame-standard）。間接證據：`docs/plans/2026-09-18-frame-standard-card-and-miss-line.md:39-41` 同 item 記過 `L8967 frame-standard: how-to-get replaced (STANDARD frame)`＋body 形狀（heading 在、段內文變一行、LLM 第 2 步消失）。 |
| F14 | **未核實／描述鬆** | plan §3-A 講「`render.cards.final` 已有 `cardsOut`／`AskService.frameStandardDisplayRefs`」：`cardsOut` 確在（`AskService.java:521-530`）；但 `frameStandardDisplayRefs` 係 **private log helper**（`:2712`，只餵 `cards kept=… refs=…` 呢行 log），**唔在** trace，亦唔可由 `AskEngine` 直接叫。 |

---

## ② 問題（severity ＋ 證據）

**P1 CRITICAL — 驗收儀器錯，A1 可能永遠紅、A2 baseline 從未存在。**
A1/A2 判準用 `render.markers.recipe_card_markers`，但該欄位掃 `[[recipe_card:`（F7），fix A 寫嘅係 `[card:N]` ⇒ **寫咗都會係空**（false red）；反過來若為咗滿足欄位而寫 `[[recipe_card:N]]`，`AskService.java:2444` 會 strip 佢、`AiAssistantScreen.java:838` 再 strip 一次、仲違反 prompt「forbidden」（F6）⇒ 卡根本唔會被引用。A2 講「stone_axe 5 卡 5 標記、唔准退化」係**從未存在嘅 baseline**（實測 `[]`＋2 個 `[card:N]`，F2）。
**改法**：A1/A2 改量 `display.body.final` 內 `[card:N]`（＋卡實際落點），或先改 `AskTrace.markers()` 掃埋 `[card:N]`（順手把 `emissionRefs` 同 body token 對比）。

**P2 CRITICAL — 根因錯 attribution ⇒ 修法 B 係 no-op、白名單漏真兇檔。**
真兇＝`AskReplyScrub.replaceHowToGetBody`（`AskReplyScrub.java:943-965`，經 `AskEngine.java:980`），唔係 `HonestMiss.insertLineBeforeSources`（F3）；而後者正係 double case 中**保住** marker 嘅路（F4）。後果：
(a) plan §4 白名單冇 `logic/AskReplyScrub.java`，但全部風險落喺該檔（`replaceHowToGetBody` 嘅起／收邊界＝`HOW_TO_GET_HEAD`…`HOW_TO_USE_HEAD`／`AS_MATERIAL_HEAD`／`ReplySources.HEADER`）。
(b) 修法 B「喺 `AskMarkerRepair` 插入」嘅實際位置係 `AskEngine.java:965`，**早過** `:980` 嘅整段取代 ⇒ 插入嘅 `[card:1]` 會即刻被換走（同 `:967-968` 註釋自述 fix1 下場一樣：「fix1 early insert was overwritten by obtain-unknown rewrite」）⇒ B 無效。要 B 有效，必須擺喺 `:992` 之後。

**P3 HIGH — 修法 A 嘅 refId→位置換算未證，會「靜默無效」或「錯卡」。**
消費端係位置索引（`RecipeEmbed.java:804-805`，whitelist＝`cards.size()`，F8）；STANDARD 路徑會 **drop 卡**（`AskService.java:2654-2661 keepOnlyStandardRecipeCard`；09-18 log `suppressedFrameOnly n=6`）。若前段 ref 被 drop，倖存卡位置前移：寫 `[card:3]` → 出 whitelist → token 被剝（`:812`、`:828-840`）→ **靜默無效**（body 仍有 token，A1 照綠＝**false green**）；寫 `[card:2]` → 解析到另一張卡（**錯卡**）。而決定最終 shown strip 嘅係 `AskService.java:2479`（遲過 `AskEngine:978`）⇒ 喺 `:978` 根本算唔到正確 N。

**P4 HIGH — 症狀嘅「玩家可見性」未證。**
plan 只證「body 冇 `[card:1]`」，冇證「玩家睇到有問題」：卡照樣 `cardsOut=1`，`RecipeEmbed` 有 `disperseUnplacedEmissionCards` 兜底（`:765-766`「R7: disperse unplaced cards across numbered steps」），AI 模式亦冇退化成無卡（F9）。trace 冇 UI parts 事件（只有 `display.body.final`／`render.cards.final`／`render.markers`）。⇒ 未過「症狀真成立」門檻。

**P5 MEDIUM — 「更簡單做法＝唔好整段取代、保留 LLM 原文」係反轉已批決定，唔算 option。**
母 plan A 已定（`docs/plans/2026-09-18-frame-standard-affirmative-answer.md:45`）「STANDARD 框架…該段內文＝**程式決定性產生嘅配方句**，並且唔准再出現…否定句」，成因就係 LLM 自寫「冇可斷言嘅確切取得步驟」令玩家睇到自相矛盾（同檔 `:20`）；`§2.4` 亦 pin 住 `:978` 呼叫（`tests/check_modular_frame_standard.py:76`）。⇒ 刪改寫步驟＝推翻 plan A，須返 plan A／SK。真正較窄嘅選項係：**只換「唔帶 marker 嘅純文字步驟」**，或先把儀器修好再評估係唔係 bug。

**P6 MEDIUM — 資訊損失／新矛盾冇人管。**
取代令 LLM 第 2 步（部件來源未收錄）消失（F12）；double case 出現新矛盾：LLM 講「花岗岩」版、程式句講「橡木木板 + 木棍 -> modular double」（idx71）。plan §5 A6 只查「唔准同時兩條怎么来行」，兩者都唔覆蓋。

---

## ③ 比分 ＋ flip conditions

**正方 3 ： 反方 7**（未達 8:2 ⇒ 唔准開工，須修 plan 再審）
- 正方得分：① sword 症狀逐字屬實（F1）；② `[card:N]` 係合法通道、AI 模式確實冇 fallback（F6／F9）；③ `AskMarkerRepair` 只修復已存在標記、`frameStandardRecipeLine` 確實唔帶 marker（F5／F11）；A4 baseline 與實測一致（F10）。

**Flip conditions（任一被新證據推翻 ⇒ 該條不計入反方）**
- **Fc1**（解 P1）：`AskTrace.markers()` 加掃 `[card:N]`（或 A1/A2 改量 `display.body.final`），並把 A2 改成實測 baseline（stone_axe `recipe_card_markers` 本來就係空、只有 2 個 `[card:N]`）。
- **Fc2**（解 P2）：plan §2／§4 改認 `AskReplyScrub.replaceHowToGetBody`（`AskReplyScrub.java:943-965`）為真兇、白名單加入 `logic/AskReplyScrub.java`，並把修法 B 移到 `AskEngine:992` 之後（整段取代之後）。
- **Fc3**（解 P3）：提供 STANDARD 路徑**有 drop** 情境（例如 emission refs 前段被 keep-1 砍走）下「refId ≡ 最終 shown strip 位置索引」嘅證明，並把落 marker 點移到 `AskService:2479` 之後（唔係 `AskEngine:978`）。
- **Fc4**（解 P4）：有 UI 側證據（截圖或 interleave parts／debug log）顯示「冇 `[card:N]`」令卡真放錯位、且玩家睇得到。
- **Fc5**（解 P5）：SK 明確批准推翻 plan A §1「決定性段落」。
- **Fc6**（解 P6）：plan 加一條驗收「改寫前後資訊唔准減少」，並覆蓋 double 式新矛盾句。

---

## ④ 最貴未知

- **U1（最貴）**：玩家實際睇到咩。trace 冇 UI parts，唯一兜底 `disperseUnplacedEmissionCards` 嘅落點只能靠真機截圖或 UI log 驗 ⇒ 未買到就無法判定係唔係「值得改 production code ＋ 跑 10-15 萬 token 真機 harness」。取得成本：1 次真機＋截圖，或 1 次帶 interleave parts 嘅 log 抓取。
- **U2**：refId 與位置索引嘅實際對齊率。現有 4 條 trace 全部係 `emissionRefs=[1]` 或 `[1,2]` 而 `cardsOut ≥ refs`，**完全冇覆蓋**「前面 ref 被 drop」呢個 fix A 最易錯嘅情境 ⇒ 需要一次針對性 STANDARD 跑（多 ref ＋ keep-1 砍前段）才買得到。
- **U3**：sword run 到底行 `replaced` 定 `appended`（F13）。`latest.log` 該時段已 rotate，只剩 body 形狀＋09-18 同 item 舊 log 做間接證據 ⇒ 要 fresh run 留 log 才確認。
