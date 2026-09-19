# Follow-up #1 plan v3（方案 S）— 反方 review R3（最後一輪）

> 審：`docs/plans/2026-09-19-followup1-frame-card-marker-phase-plan.md`（v3）
> 反方 reviewer 自己跑命令核實（只讀唔改）；引文逐字。R1 3:7 → R2 4:6 → 本輪 R3（SK 上限 3–4 輪，到頂）。
> 行號一律指 `forge/1.19.2/src/main/java/com/skps9/packai/`。

## ① 驗過嘅事實（真／假／未核實）

**真**
1. **v3 §3① 成立**：兩條 ask 路徑都經 `AskEngine.INSTANCE.ask` — `client/service/AskService.java:320`（`runAsk`，public 入口 `askAsync :126`）／`:2428`（`askBlocking`）逐字 `AskEngine.INSTANCE.ask(`。⇒ 只改 AskEngine 確可一處覆蓋兩者；`runAsk` **冇**獨立取代步驟（`:348-449` 只有 stripDuplicateSectionHeaders／stripAiRecipeCardMarkers／withRecipeCards）。
2. **v3 §2 機制逐字屬實**：`logic/AskEngine.java:980` `String replaced = AskReplyScrub.replaceHowToGetBody(body, line);`；`logic/AskReplyScrub.java:948-965` 方法體、`:943-947` javadoc「Replace how-to-get section body with fill (heading kept).」；早退 `:953-955`；span = 標題 end → 最早（`HOW_TO_USE_HEAD`／`HOW_TO_UPGRADE_HEAD`／`AS_MATERIAL_HEAD`／`ReplySources.HEADER`）`:956-961`；append 分支 `:986-988`。
3. **v3 §3② 前半（body 到 :980 仍帶 LLM 嘅 `[card:N]`）= 真**：`AskEngine.java:881-884` 自己就 emit `check.scrub`(rules=scrubPromptEcho)；sword trace idx63 嘅 before/after **都仍有** `[card:1]`；其後 `:963-966`（`ReplySources.ensure`／`AskMarkerRepair.repair`）冇剝 `[card:N]`（`AskMarkerRepair.ANY_MARKER` 唔覆蓋單括號）⇒ 同一 `body` 到 `:980` 仲有 token（double case 走 append 分支出到 `[card:1]`，同一前缀路徑可證）。
4. **v3 §3③（`[card:N]` 唔會被任何 scrub 剝走）= 真，且係實證**：double idx69/70/71、stone_axe idx45/46/47、scissor idx62/63/64 嘅 `display.body.final` 全部仍有 `[card:`。覆蓋 `AskEngine:881` scrubPromptEcho、`AskService:2434`+`:2440` stripDuplicateSectionHeaders、`:2444` stripAiRecipeCardMarkers（pattern `:2184-2185` = `\[\[recipe_cards?:[^\]]*]]`）、`logic/AskResult.java:84-97` `withRecipeCards`（內含 `RecipeCardsMode.scrubMarker :185-192`，只 match MARKER `:33-35`）。
5. **§1 log 證據真身存在**：`/c/Users/skps9/Documents/packai_dev_game/logs/debug-1.log.gz` 內 15:41:57.741 `frame-standard: how-to-get heading not found -> appended`、15:42:23.735 `frame-standard: how-to-get replaced (STANDARD frame)`、15:41:57.750 `cards kept=1 dropped=0 refs=1`。sword trace 15:42:23.738 嘅 stripDuplicateSectionHeaders `before` 已無 `[card:1]`、body 形狀 = 標題＋單行 fill（LLM 第 2 步「部件來源」消失）⇒ 同 replaced log 完全一致（appended／replaced 兩 case 分野確認）。
6. **貼 marker 位置要求**：`logic/RecipeEmbed.java:749-771` → `splitTextIntoStepBlocks :760`（分段只看標題／`NUMBERED_STEP_LINE :71-72`，唔會因 `[card:N]` 改變）→ `placeEmissionCardsByRef :778-825`：卡係插喺**成個 block 之後**（`int insertAt = skipCardsAfter(blocks, i+1) :819-823`），唔跟 token 偏移 ⇒ 行尾／行首／另起一行等效（只要同一 block）；whitelist `:804-805`、殘留 token 一律剝 `:827-842`（`:768` 無條件跑）。`CARD_REF_TOKEN :77-78`。
7. A6 檔數：`ls tests/check_*.py | wc -l` = **122**（我實數）；baseline 121 綠／1 紅由 R1/R2 實測（我未重跑）。
8. **非 strip 渲染路徑唔剝 token**：`client/gui/AiAssistantScreen.java:835-842` — 只有 `cardStrip=true` 才 `interleaveEmissionCards`；`cardStrip=false` 走 `RecipeEmbed.parts()`，而 `CARD_REF_TOKEN` 只喺 interleave 家族 `:789/:837/:840` 用過 ⇒ `parts()` 唔剝 `[card:N]`。

**假**
9. **v3 §3②「標準框架經 keep-only 後只剩 1 張卡 ⇒ 正確 N 恆為 1，唔需要 index 換算」= 假（真機反例）**：`debug-2.log.gz:63692-63696`（15:21:05，同一 ask：`:63677` `how-to-get replaced` @15:21:05.252）：`Pack AI toolCards emission=2 cardsOut=2`、`cards kept=2 dropped=0 refs=1,2 matchedById=2`、`card #0 … ref=[card:1]`（output `tetra:modular_double`）／`card #1 … ref=[card:2]`（input `mrqx_extra_pack:mystery_craftsmanship`）；`debug-4.log.gz` 14:09:48.95 同一形狀（replaced + kept=2）。⇒ kept 可以係 2（結構原因見 #12）。
10. **v3 §3「取代前抽出 body 內原 `[card:N]`」若「body 內」＝全 body = 錯（真機反例）**：`debug-4.log.gz` 14:09:48.94 係 **replaced** 路徑 + kept=2，其 `display body` **仍然有** `[card:2]`，位置喺 **怎么用** 第 4 步尾（`4. …{{item:mrqx_extra_pack:mystery_craftsmanship}}…JEI…。[card:2]`）＝取代 span **之外**。⇒ ①取代唔一定食 marker（symptom 只在 marker 落喺怎么来 span 內時發生）；②由全 body 抽就會把這類合法 marker 搬去配方行。

**未核實**
11. harness 用邊條路徑：`client/autotest/AutoTestHarness.java` 內搵唔到 `askAsync／askBlocking` 呼叫點 ⇒ 「A1 覆蓋兩條路徑」未證（可能只有其中一條有真機證據）。
12. kept 上限：`logic/ModularFrameCards.java:82-137` — `mustKeep`（`AskService.java:2659` 傳 `c -> c.isInputUse() || c.isTrailingOptional()`）係 **pin**：`:130-131` `if (frame && !pin && i != chosen) { dropped++; continue; }` ⇒ pin 卡同一律保留、非 frame 卡一律保留 ⇒ kept **可以 >1**，同 #9 一致；v3 嘅「恆為 1」冇結構保證。

## ② 問題（severity ＋ 證據）

- **O1 HIGH（新）— 兩個「程序可保證」判準建基於已被真機推翻嘅不變量。** v3 §3② 用「keep-only 後只剩 1 張」正當化「N 恆為 1／唔需要 index 換算（解 R2 O1）」，但 #9 兩條真機 log（含 `how-to-get replaced`）都係 `cardsOut=2 / kept=2 / refs=1,2`。後果：cardsOut≥2 時，`refId`（`AskToolEnv.java:83` `refId = pendingEmissions.size()+1`）同 `RecipeEmbed` 嘅 1-based **shown index**（`:804-805`）冇被證明對齊；R2 O1 嘅「靜默無效／錯卡」風險原封未動，而 A1 判準（`N=1 ≤ cardsOut`）＋「N>cardsOut ⇒ 夾 1」只覆蓋單卡世界 ⇒ 覆蓋率不足，且夾 1 在 kept≥2 時有機會指去另一張卡（card #0 係 output、card #1 係 input，順序唔一定等於 LLM 意圖）。
- **O2 HIGH（新）— v3 自己嘅確定性規則喺指定實作位置算唔到。** `cardsOut` 係 `AskService.java:2479 suppressModularFrameCards`（→`:2647-2684` `ModularFrameCards.keepOnlyStandardRecipeCard`）之後才定，並且 `:2450 autoEmitCatalogCards`／`:2459 supplementMissingUsesCards` 仲會喺 AskEngine **之後**加卡。v3 §3 卻寫「貼回時若原 N **> 最終 cardsOut** → 夾到 1；**cardsOut=0 ⇒ 唔准貼**」，而 §4 白名單只准改 `logic/AskEngine.java` ⇒ 要麼規則做唔到（A3 變真空斷言），要麼喺 AskEngine 重覆一套 keep-only：`AskService.java:2652-2653` 註釋正正警告「兩套 drop 政策並存…日後新增任何 drop 規則必須兩邊都改」＝ 制造 divergence 源。
- **O3 MEDIUM-HIGH（新）— 抽 marker 嘅範圍冇界定 ⇒ 有搬位／重覆風險，且冇判準測得到。** #10 真機反例（怎麼用 步驟尾 `[card:2]`）證明 span 外 marker 本來就安全；若 S 掃全 body，會把 span 外 marker 搬去配方行（原位置少一個、配方行多一個 → 卡落錯段）。v3 §6 只寫「貼回位置錯 ⇒ A1 要人眼核該行係配方行」，A1 只數「文字 `[card:N]` ≥1」⇒ 搬位（count 不變）**通過**。
- **O4 MEDIUM（新）— `cardStrip=false` 渲染路徑會外露 token，plan 冇判準。** #8：`RecipeEmbed.parts()` 唔剝 `[card:N]`；S 係**程序性**寫入 token（唔再靠 LLM 自願），而 §4 白名單禁止改 `AskService`（即 `withRecipeCards(cardsOut, strip)` 嘅 strip 值）⇒ 非 AI／KEYWORDS + cloud key 配置下玩家有機會見到字面 `[card:1]`（`AskResult.fromRaw` cardStrip=false，`:167-177` 一帶）。
- **O5 MEDIUM（承 R2）— A1 真機覆蓋只有一條路徑、A5 仍然 0 UI 證據。** #11：harness 觸發點未確認；`RecipeEmbed` 仍然冇 interleave log（R2 已核）。A5 明文降級符合 R2 Fc6（算收貨），但代價係本輪**完全冇**「玩家真係睇到卡落對位」嘅證據 —— O1/O3 兩種落錯位情境正好係 UI-only。
- **O6 LOW-MEDIUM — A5 判準同 v3 目標唔對齊**：A1 只保證「token 存在」，唔保證「卡插喺配方行之後」；而 `disperseUnplacedEmissionCards :765-766` 兜底仍然存在 ⇒ 就算 token 冇貼返，卡都會落某處（R1 P4 未被推翻）。
- **正方收貨點**：R2 Fc1（A2 改 baseline 量度）／Fc2（斷言 scrub 唔剝，我實證）／Fc3（兩路徑，已證）／Fc4（棄 F2）／Fc6（A5 明文降級）／Fc7（三方案 diff 表）大致齊；§1／§2 事實核對全中；S 係 1 檔、方向正確、無新依賴。

## ③ 比分 ＋ flip conditions

**正方 5 ： 反方 5**（未達 8:2；R3 = 第 3 輪，依 SK 規則 3–4 輪上限 **停手交 SK 決定**）
- 正方得分：S 方向對＋1 檔最小 diff（Fc4／Fc7 收貨）；§1 §2 根因／log／行號核對全中；token 存活到 UI 由真機 trace 實證（Fc2 超額完成）；兩路徑一處覆蓋（Fc3 真）。
- 反方得分：O1（不變量被真機 log 推翻，A1 判準覆蓋不足）；O2（規則喺指定位置算唔到，A3 真空）；O3（span 範圍未界定＋搬位測唔到）；O4（strip=false 外露）；O5（真機／UI 覆蓋不足）。

**Flip conditions（R4 若 SK 批准再開，或直接寫入實作規格）**
- Fc1：刪「N 恆為 1」，改為「N = 1-based shown index」並引 `RecipeEmbed.java:804-805`；A1 判準寫死覆蓋 `cardsOut≥2`（附 `ModularFrameCards.java:130-131` 同兩條 kept=2 真機 log 做 fixture 素材）。
- Fc2：「cardsOut=0 ⇒ 唔准貼」／「N>cardsOut ⇒ 夾 1」二擇：(a) 落喺真有 `cardsOut` 嘅位置（要動 AskService／或把 helper 參數化）；或 (b) 明文降級為「只重貼 span 內原 token、唔理 cardsOut」，並引 `RecipeEmbed.java:768`＋`:827-842` 證明無卡時 token 會被安全剝走。
- Fc3：抽 marker 範圍寫死 = `AskReplyScrub.replaceHowToGetBody` 嘅 `bodyStart..bodyEnd`（唔准掃全 body）；加 fixture：怎麼用 步驟尾 marker（用 `debug-4.log.gz` 14:09:48 真機 body 做素材）必須原封不動。
- Fc4：A3 重新設計成可餵真值（新 helper 收 `expectedCards` 參數，閘內直接餵 0／1／2），唔准叫佢「cardsOut=0」。
- Fc5：加判準覆蓋 `cardStrip=false`（或明文：`[card:N]` 只准喺 AI strip 模式出現，並證 STANDARD frame 必屬 AI 模式）。
- Fc6：A1 明文寫 harness 只覆蓋邊條路徑（或補 `runAsk` 覆蓋）。

## ④ 最貴未知

- **U1（最貴）**：STANDARD frame 出現 `cardsOut≥2` 嘅頻率，同 kept≥2 時 `refId ≡ shown index` 係唔係真成立。現有真機證據兩個方向都有（kept=1 與 kept=2 各 2 次），而 fix 嘅唯一危險情境（錯卡／靜默無效）**只**喺 kept≥2 出現。取得成本：1 次針對性真機跑（同款含 `mrqx_extra_pack:mystery_craftsmanship` input 卡嘅問法）＋ 逐 ask 對 `cards kept=… refs=…` 同 body token，約 10–15 萬 token。
- **U2**：玩家實際睇到咩（有／無 marker 時卡嘅落點差）。`RecipeEmbed` 冇 interleave log、harness 冇截圖，`sk_activity.json` 為 `playing / counter-strike 2 / fullscreen` 期間禁開窗 ⇒ 要 SK 空閒時 1 次截圖或 1 次加 interleave log 嘅臨時 build（後者要改 `logic/RecipeEmbed.java`，會擴大本輪白名單）。
- **U3**：`cardStrip=false`（KEYWORDS／ALWAYS + cloud key）配置下 `[card:N]` 有冇外露 —— 需要 1 次該配置真機跑，之前所有 trace 都係 AI 模式，零覆蓋。
