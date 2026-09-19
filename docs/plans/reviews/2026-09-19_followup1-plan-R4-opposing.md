# Follow-up #1 plan **v4（極簡版）** — 反方 review **R4（第 4 輪＝上限）**

> 審：`docs/plans/2026-09-19-followup1-frame-card-marker-phase-plan.md`（v4 喺頂、v3 附錄）。
> R1 3:7 → R2 4:6 → R3 5:5 → 本輪 R4（SK 規則 3–4 輪上限，到頂）。
> 反方自跑命令核實（只讀唔改）；引文逐字。行號一律指 `forge/1.19.2/src/main/java/com/skps9/packai/`，測試／trace 另標全路徑。

## ① 驗過嘅事實（真／假／未核實）

**真**
1. **v4 §0「span 喺 method 內部算得到」＝真**：`logic/AskReplyScrub.java:956` `int bodyStart = head.end();`、`:957-961` `bodyEnd = Math.min(...)` 連續 4 個 `findAnchoredStart`、`:962-963` `before/after = answer.substring(...)`、`:964` `tidyNewlines(before + fill + "\n" + after)`。可抽範圍＝`answer.substring(bodyStart, bodyEnd)`，唔需要改取代行為。**但**：`HOW_TO_GET_HEAD` 尾段 `(?:[ \t]*[:：].*)?$`（`logic/RecipeEmbed.java:65-66` 同構；`AskReplyScrub` 同名 pattern）會吞**整行** ⇒ 標題行（冒號後嘅字）屬 head match **內**、唔屬 span（見 O2）。
2. **A1 真機 case 存在，且 marker 確喺 span 內**：`docs/research/artifacts/2026-09-19-autotest-run-v2/ask-20260919-154158-tetra_modular_sword.jsonl` `check.scrub`:63（15:42:23.734）`before` = `怎么来:\n1. 工作台：[[item:tetra:stonecutter]] … → 切石器，…合成。 [card:1]\n2. 部件来源：…\n\n怎么用:`。`怎么来:` 單獨一行 ⇒ `head.end()` 喺冒號後、`bodyEnd` = `怎么用:` 起點 ⇒ **`[card:1]` 確實落喺 span 內**。log `debug-1.log.gz` 15:42:23.735 `how-to-get replaced (STANDARD frame)`、15:42:23.738 `cards kept=1 dropped=0 refs=1 matchedById=1`、`render.cards.final role=output cardsOut=1`。⇒ v4 抽得到、N=1 對得上唯一卡。
3. **A2 fixture 真**：`docs/research/artifacts/2026-09-19-autotest-run/ask-20260919-140911-tetra_modular_single.jsonl` `display.body.final`（14:09:48.955）＝`…怎么来:合成台（有序合成）：凿地器 + 木棍 -> modular single。摆放位置请以 JEI 为准。这是空白模组框架合成版本。\n怎么用:\n…\n4. 作为材料：…（{{item:mrqx_extra_pack:mystery_craftsmanship}}）…以 JEI 该配方为准。[card:2]`；同 log 14:09:48.941 `replaced`、14:09:48.953 `cards kept=2 dropped=0 refs=1,2`。該 `[card:2]` 喺 怎么用 第 4 步尾、即 `bodyEnd` 之後 ⇒ **span 外**，v4 保得住。A2 係有效負控。
4. **跨呼叫者零回歸**：全 repo `replaceHowToGetBody` = 1 個 main caller（`logic/AskEngine.java:980`）＋ 測試 `forge/1.19.2/src/test/java/com/skps9/packai/logic/FrameStandardRecipeLineCheck.java:114/133/149/160/175/201`；後者 6 個 fixture `grep -n "card:"` **零命中** ⇒ 新行為對 49 Java 測試零影響，v4「冇抽到⇒行為同今日一樣」成立。
5. **越界 token 會被剝＝真**：`logic/RecipeEmbed.java:804-805` `int idx = n - 1; if (idx >= 0 && idx < cards.size() && !placed[idx])`；`:812` `m.appendReplacement(sb, "")`；`:827-842` `stripCardRefTokens`；`:768` 無條件跑。⇒ v4 §0「最壞＝回到現狀」成立。
6. **R3 O4／Fc5（`cardStrip=false` 外露）由現有 code 解，但 v4 冇 claim**：`client/service/AskService.java:362` `if (cardsMode == RecipeCardsMode.AI && RecipeCardsMode.llmExpected())` 呢個分支才經 `AskEngine.INSTANCE.ask`（`:320`／`:2428`），並硬傳 strip=**true**（`:404`、`:2484` `.withRecipeCards(cardsOut, true)`）；另一分支（`:446`、`:2503` `.withRecipeCards(cardsOut)`＝繼承 false）**唔會行到 `replaceHowToGetBody`**。⇒ v4 唔可能令非 strip 模式新增外露。
7. **貼行尾／行中對落點等效**：canonical 句同標題**同一行**（實證 #3 `怎么来:合成台…版本。`）；`splitTextIntoStepBlocks`（`:848-882`）以行邊界切塊，`emissionSectionTypeOf`（`:1240-1258`）用 `.matches()`（`.*` 照吞 ` [card:1]`）⇒ 該行仍判 section 0，卡插喺成個 block 之後（`:819-823`）。shaped 版「摆位以 JEI 为准。」尾巴唔影響。
8. **重覆 token 去重符合 UI 期望**：同一 block 兩個 `[card:1]`，`placeEmissionCardsByRef` 自帶 `placed[idx]` 去重（`:805-809`）、殘餘一律剝（`:812`）⇒ 一卡一次，v4 嘅 span 內去重一致。

**未核實**
9. kept≥2 時 `refId`（`AskToolEnv` `refId = pendingEmissions.size()+1`）≡ `RecipeEmbed` 1-based shown index —— **仍然零真機證據**（R3 O1 未解，v4 用「唔理 cardsOut」繞開）。

## ② 問題（severity ＋ 證據）

- **O1 HIGH（新）— A1 用嘅 fixture 上，v4 嘅 part 次序同今日「逐位相同」；驗收判準分辨唔到有冇修好。** Code trace：sword 卡 role=output ⇒ `emissionSectionOf`（`RecipeEmbed.java:1121-1136`）回 wantSec=**0**；post-replace body 得一個 section-0 block（`怎么来:合成台…版本。`，`:1248-1250`）。今日無 token ⇒ `disperseUnplacedEmissionCards` `:919-925` 走 `insertAt = sectionLastAfter(blocks, 0)`（`:1010-1042`，`:1030-1035` `last = i + 1`）→ 插喺 怎么来 block 之後；v4 有 token ⇒ `placeEmissionCardsByRef` `:819` `int insertAt = skipCardsAfter(blocks, i + 1)` → **同一 index**。兩者都＝「怎么来 行之後、`怎么用:` 之前」。⇒ **A1 可以全綠而症狀照舊**。（跑法：1 次 Java fixture 斷言 part 次序，或用 14:09 sword body 兩版對照。）真正會變嘅係 wantSec=1（`:1128-1130` `isInputUse()`，真機例 14:09 `renderCards … role=uses`）或 needle 命中別段（`:943-957`）嘅情形 —— 而 v4 冇為呢類寫判準。
- **O2 MEDIUM-HIGH（新）— span 邊界行敏感：標題行內容屬 head match，唔屬 span。** 真機 #3 顯示當 LLM 寫 `怎么来:<內容>`（內容同標題同一行）時 span 收縮到**一個 newline**；而 LLM 確實會喺同一行尾放 token（真機 `ask-20260919-154132-tetra_modular_double.jsonl` `display.body.final` item 2 `2. 怎么来（合成台）：… [card:1]` 同源手法）。⇒ 呢類 token v4 **永遠抽唔到**、靜默零效果；而 §0 第 4 點「冇抽到⇒零回退」＋ A3「span 內冇 marker ⇒ 冇 token」會把同一輸出當**綠**。
- **O3 MEDIUM — §2 表頭「全部程式可保證」同 A1／A5 矛盾。** A1 要「**人眼**核 trace 原文該行」、A5 要 SK 目視；而 O1 已證連人眼核「N 對得上」都分辨唔到落點。A3（span 內冇 marker ⇒ 結果冇 `[card:N]`）係**恆真**（span 本就被整段換走），只算負控、唔算證據。
- **O4 MEDIUM（承 R3 O1）— 多卡／input-use 卡零立場。** kept≥2 真機有（#3 `kept=2 refs=1,2`）。若 span 內 token 係 `[card:2]`（input-use）而 v4 照搬去 怎么来 行，卡就由 怎么用 段（wantSec=1）搬去 obtain 段 —— v4 對「係唔係想要咁」**冇立場、冇 fixture、冇 flip**。
- **O5 LOW-MEDIUM — 新閘形式未定。** §1 只寫「新增 `tests/check_frame_card_marker_preserve.py`（span 內抽／貼…）」；repo 122 閘全屬 Python 靜態／fixture 型 ⇒ 要喺 Python **重寫** span 邊界＋regex 邏輯 ＝第二套實作（divergence 源），而行敏感邊界（O2）正係最容易兩邊唔一致嘅位。
- **正方收貨點**：Fc2（span 內限定）已寫入 §0＋A2 且我核過真機 fixture；Fc3（span 定義）＝`:956-964` 真實存在；Fc5 由 call-site gate 解決（事實 6）；1 檔改動、跨 caller 零回歸（事實 4）；越界 token 安全剝（事實 5）；O2/O3（R3）確實被 v4 設計繞開。

## ③ 比分 ＋ flip conditions

**正方 6 ： 反方 4**（未達 8:2；R4＝第 4 輪，依 SK 規則**唔再開新輪**，交 SK 決定。U1 已由 SK 親口解 ⇒ 建議**可批准實作**，但列 Fc1 為必改。）

- 正方得分：span 可算且唔改取代行為（`:956-964` 實證）；機制喺真機 symptom fixture 上抽得到 token（事實 2）；span 限定保住 怎么用 段合法 marker（事實 3 真機）；跨 caller 零回歸（事實 4）；越界安全（事實 5）；`cardStrip=false` 風險由 call-site gate 消失（事實 6）；去重合 UI（事實 8）。
- 反方得分：O1（A1 判準無法區分「修好」與「今日」）；O2（標題行 token 盲區＋會被當綠）；O3（表頭同 A1／A5 矛盾、A3 恆真）；O4（多卡／input-use 零立場）。

**Flip conditions**
- **Fc1（必改）**：A1 由「body 含 `[card:N]`、N 對得上」改成**落點判準**：斷言 interleave 後 part 次序 = 卡緊接 `怎么来` block 之後、`怎么用:` 之前；並用 `ask-20260919-154158` 真實 body 造**有／冇 token 兩版** golden fixture，明文寫「同一 part 次序 ⇒ 本 fix 對該 case 唔生效，需另找驅動 case（例 role=uses）」。
- **Fc2**：span 邊界定死 `answer.substring(head.end(), bodyEnd)`，並明文列**已知盲區**「`:：` 之後同一行嘅 token 唔會被抽」（附 14:09 body 做 fixture）；或收窄 `HOW_TO_GET_HEAD` 嘅 `.*` 令標題行內容歸入 span —— 後者要先證「取代輸出零變化」。
- **Fc3**：為 input-use（wantSec=1）／kept≥2 加 fixture，明文 v4 效果＝卡由 怎么用 段搬到 怎么来 行，並由 SK 表態接受邊個。
- **Fc4**：A3 標明為恆真負控（只證「冇無中生有」），刪 §2「全部程式可保證」，或把 A1 拆成「程式部分（token 存在／次序）」＋「人眼部分」。
- **Fc5**（承 R3 Fc5，v4 未寫）：plan 補一句「`cardStrip=false` 唔會行到本路徑」，引 `AskService.java:362`／`:404`／`:2484` vs `:446`／`:2503`。

## ④ 最貴未知

- **U1（新，最貴，未核實）**：SK 見到嘅「卡走位」係**邊個 case**。Code trace 顯示 sword（replaced、kept=1、role=output）**冇 token 都已經落對位**（O1）⇒ 若 SK 睇嘅係 sword，v4 可能**零效果**；若係 14:09 那類（kept=2、role=uses）就可能有分別。取得成本：1 次臨時加 `RecipeEmbed` interleave log 嘅 build（會擴大本輪白名單）或 SK 空閒時 1 次截圖比對。**唔解Ｕ1，A1 全綠都證明唔到症狀修好。**
- **U2**：kept≥2 時 `refId ≡ shown index` 仍零覆蓋（R3 O1 未解，v4 只係繞開）。
- **U3**：新 Python 閘同 Java 邏輯會唔會 diverge（形式未定，未核實）。
