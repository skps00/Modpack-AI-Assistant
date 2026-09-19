# 反方報告（R3 opposing）— P0 `QuestGuide.stripQuestIcons` 嵌套 icon 崩潰修法計劃 **v3**

- 對象：`docs/plans/2026-09-19-p0-questguide-stripicons-crash.md`（**v3**）＝ commit `72decfa`
  （`git log -1 --oneline` → `72decfa docs(plan): P0 questguide v3 - fix A4 denominator (19/19), drop unsound A5 numeric band …`）
- 我上一輪（R2）：`docs/plans/reviews/2026-09-19_p0-questguide-stripicons-crash-R2-opposing.md`（7:3、go=false、7 條 flip conditions）
- Reviewer：Hermes（反方）／日期：2026-09-19 深夜／輪次：**R3**（SK 規則上限 3–4 輪 → **本輪已到上限**，見尾段「停手報告」）
- 版本界線：`forge/1.19.2`（Forge 43.4.5、MC 1.19.2）；`neoforge/1.21.1` PAUSED，不審。
- **只讀聲明**：本輪我**冇改、冇新增、冇刪除 repo 內任何檔案**（唯一新增 = 本報告）。
  我**冇**跑 gradle／**冇** build／**冇** commit／**冇**部署／**冇**開遊戲／**冇**動沙盒。
  我**有**跑：(a) `tests/check_*.py` 全量 123 個（唯讀；跑完 `git status --porcelain | wc -l` 仍然 **127**，證明未寫入 repo，輸出喺 `%TEMP%\r3_gate_check.txt`）；
  (b) 我自寫嘅 Java probe 同 fuzz，喺 `%TEMP%\r3_java\`（repo 外、JDK 21 Temurin `eclipse_adoptium-21-amd64-windows.2`，**唔屬 build**）；
  (c) 三個沙盒 pack 嘅 `config/ftbquests/quests` **唯讀**重掃。
- 已核實／推斷分開標示；跑唔到嘅一律寫 **NOT RUN**。

---

## 總分

**正方 6 : 反方 4 ｜ go = false**

一句話：**v3 有真功課**——A4 分母我對住真 status json 核到**完全正確**、A7 嘅 `%TEMP%` baseline＋sha256、T2f 更正、幽靈檔刪除＋「唔准開第二個 harness」規則、`tools/analyze_cardplace.py` 入庫後**真係重現到今晚嘅 `ANALYSIS.txt`**——呢五樣全部係實質進展。
**但我仍然唔可以開 go**，因為 v3 自己嘅 headline 有兩條**唔係佢份文嘅事實**，而且 R2 三個 blocker 主體全部仍然企喺度：

1. **A8(c) 冇改過** —— v3 標題寫「(c) 真機層（**v3 具體化**）」，但 clause 一字不變，仍然係「**≥1 個 case 嘅 trace quest hits 非空**」。我實測：19 條 ask trace ＋ `index.jsonl` 嘅 key 集合**完全冇 quest／hit 欄位**，案例全係 item 問題 ⇒ **依然無法量度**。（R2 N2 原封不動。）
2. **counter API 依然未定義** —— §5 尾註仍然寫「（**R2 請裁決用邊種**）」（R2 已裁決！），而 §6 白名單 #1 仍然寫 `lastSkippedFiles()` accessor（＝暗示 static，同尾註「唔准 static 欄位」直接矛盾）。全文**冇任何** overload 簽名 ⇒ 實作者寫唔出 A8(b)(i) 嘅 assert。
3. **D1／D2 矛盾未清** —— §3 D2 表格「測試」列**仍然寫**「放 1 個壞 `.snbt`（**嵌套 icon**）＋1 個好檔 ⇒ 斷言 `skippedFiles=1`」，同 D1（嵌套唔再拋）＋A8(b)（改咗）＋§7.4（唔准開第二個 harness）三邊衝突。實作者照 §3 做 → 卡死在一個**必紅**嘅 assert，或者開多一個 `*Check.java` → A2 由 50 變 51、A7 見到非白名單 `??` ⇒ FAIL。
4. 另外 v3 **新引入**一個唔成立嘅驗收字句：A5(ii) 用 `git diff` 證「`RecipeCard.java` 零改動」——但 `RecipeCard.java` **動手前已經係 M**（37 行未提交 insertion，內容正正係 `layoutInputStacks()`／`layoutInputIds()` 嘅**卡落位**邏輯）。

呢四條全部係**改 plan 文字（3 條）／加 6 行 code 決定（1 條）**可以修，唔使推翻 D1 設計。但未修就唔可以開工——尤其 A8(c)：一個聲稱「已具體化」而實際量唔到嘅臂，喺一份主打「反假綠」嘅 plan 入面係最唔應該出現嘅東西。

---

## 我親手做過嘅驗證（evidence base，全部可重跑）

| # | 動作 | 結果 |
|---|---|---|
| E1 | `git log -1 --oneline` | HEAD ＝ **`72decfa`**，plan v3 已 commit ✔ |
| E2 | 讀 `%TEMP%\autotest_results_20260919-213821\status-20260919-134134.json` | `caseCount=20`、`ok=19`、`capped=true`、`cases` len **20**；**19 個有 `traceFile`**；第 20 個 `{"id":"survival_random_8","status":"NO_SAMPLE","traceFile":""}` ⇒ **「20 派 / 19 有 trace」屬實** ✔（A4 分母正確） |
| E3 | `ls` 該 run 目錄 | **19** 條 `ask-*.jsonl` ＋ `index.jsonl`（19 行）＋ `latest.log` ＋ status json（共 22 檔） |
| E4 | 19 條 ask trace 嘅 key 聯集 | `after, attachCards, before, body, cardsOut, category, content, dumpLevel, emissionRefs, event, filterVariant, hasCards, hasVariant, intent, item, maint, maintCards, outputsSize, placement, primaryOutputId, question, reason, recipe_card_markers, role, rules, sourceItemId, src, tools, ts, turns, upgradeCards`（31 個）⇒ **冇 quest／hit／index 欄位**；`index.jsonl` 亦只有 `ts/question/focusId/file/rounds/cardsOut/status` |
| E5 | 主包 run 211621 逐 trace 數 `model.reply*` 事件 | **18/18** 條 trace 各有 ≥1 個（`model.reply.round1…final`）；`latest.log` `Query failed`=**0**、`AskEngine failed`=**0** |
| E6 | E9E run 213228 同上 | **11/11** 條有 `model.reply`；`Query failed`=0 |
| E7 | FTB run 213821 同上 | **0/19** 條有 `model.reply`；`Query failed` 行 = **38**；`AskEngine failed` = **19** ⇒ 症狀描述準確 |
| E8 | `git ls-files tools/` ＋ 跑 `python tools/analyze_cardplace.py <211621>` | 腳本**已入庫**（commit `72decfa`）；輸出 = 15「疑似真症狀」／3「clean」，同 `docs/research/artifacts/2026-09-19-cardplace-run/ANALYSIS.txt` **逐字一致** ⇒ **係今晚同一支腳本** ✔ |
| E9 | 跑同一腳本於 213228 / 213821 | 213228（E9E）= **0/11 case** 有相鄰；213821（FTB）= 16/20 疑似 ⇒ **腳本輸出單位係 per-case**，唔係 plan 引用嘅 **54/316、0/73（log 行數）** |
| E10 | 跑同一腳本於 212833 | 該 run `Pack AI cardplace:` 行數 = **0**（instrumentation 冇開），但腳本照印「**11 clean**」⇒ 腳本分唔開「冇數據」同「乾淨」 |
| E11 | 逐 run 數 `cards=` / `n=` / `adjacentCardPairs≠0` 行 | 211621 = 316/316/**54**；213228 = 73/73/**0**；213821 = 202/202/**143** ⇒ 54/316、0/73 嘅來源係**log 行數**（R2 N4 屬實） |
| E12 | 我自寫 Java probe：逐字拷貝 `QuestGuide.java:1529-1589`（`Probe3.java`，JDK 21）＋ D1 guard flag；corpus = **167,404 條唯一輸入**（plan 自稱核心集 5 token 全長 1–7 窮舉 ＋ `icon: ` 變體全長 1–6 窮舉 ＋ 6 萬條來自 **37-token** 豐富集嘅隨機串（含 NBSP／U+2007／U+202F／U+3000／combining／`é`／astral／CR／轉義）＋ 手寫 depth-40 嵌套／孤兒 surrogate 案例） | **current（無守衛）：Java 拋 24,674 次**（正控成立）；**D1 守衛：Java 拋 0 次**；Python port 守衛版亦 0 次；孤兒 surrogate 案例亦 0 次 ⇒ **反證失敗**：我搵唔到 post-D1 嘅 counterexample |
| E13 | 同上，Java vs Python port 交叉比對 | **Java 拋但 port 走漏 = 996**（Java 24,674 次拋之中 4.0%）；守衛後輸出 **Java ≠ port = 19,209 / 167,404（11.5%）**；只計 whitespace predicate 差異 = 5,612（3.4%） |
| E14 | 重跑 plan 自己嘅文法（8 token × 長度 1–4） | combos = **4,680**、post-D1 crash = **0** ⇒ 作者嘅數字我**重現到**，唔係作大 |
| E15 | 我自寫 **Java 語意**（`\b`／`\s` 純 ASCII）port 重掃三包 | FTB **2/755 slice**（`getting_started.snbt#26 len=1214 (742,696)`、`#81 len=780 (466,415)`）、E9E **0/1328**、主包 **0/2517** ⇒「2/755」成立 ✔ |
| E16 | `Probe4.java`：`Files.readString(p, UTF_8)` 讀非法 UTF-8 檔 | `java.nio.charset.MalformedInputException`、**isIOException=true／isRuntimeException=false** ⇒ 佢落入現有 `:150 catch (IOException ignored)`（**無 log、無 counter**） |
| E17 | `git status --porcelain -- RecipeCard.java …` ＋ `git diff` | `RecipeCard.java` = **M、37 insertions**，內容 = `layoutInputStacks()`／`layoutInputIds()`（註釋寫「layout-aware… **Gate 同 display 一定要用呢個**」）；`RecipeEmbed.java`／`client/service/AskService.java` = 乾淨 |
| E18 | `find . -iname "*REMAINING*"` ＋ `git ls-files \| grep -i remaining` | **兩個都空** ⇒ `REMAINING_WORK.md` **仍然唔存在**，但 plan 有 **5 處**（行 67／139／151／154／183）當佢係記錄目標 |
| E19 | `git status --porcelain \| awk` 分類 | **127 = 72 M ＋ 53 ?? ＋ 2 D**（plan §7 仍然寫 **128 項**） |
| E20 | 逐行 grep plan | §6.1（行 147）**仍然**寫 `lastSkippedFiles()` accessor；§5 尾註（行 143）仍然寫「（**R2 請裁決用邊種**）」＋「唔准 static」；全文**冇** overload 簽名 |
| E21 | 逐行讀 §3 D2 表（行 79） | **仍然**寫「**新增 fail-soft harness**：fixture gameDir 內放 1 個壞 `.snbt`（**嵌套 icon**）＋1 個好檔 ⇒ 斷言… `skippedFiles=1`」 |
| E22 | grep plan：`JDK17`／`唔可以入 assert` | **0 命中** ⇒ R2 N12（`CRASH(x,y,z)` 係 JDK 相關訊息、唔可以入 assert）**未寫入**；`T3c` 只出現喺 §4 T 表（行 116），**唔喺** §4 負控清單（行 125） |
| E23 | 全量跑 `tests/check_*.py`（唯讀） | **123 檔 = 122 rc=0 ＋ 1 rc=2（`check_ask_display_leak.py`）** ⇒ A3 嘅 baseline 講法**正確且可重現** ✔；跑完 `git status` 仍然 127 |
| E24 | `find forge/1.19.2/src/test -name "*Check.java" \| wc -l` ＋ grep `tmp-check.gradle` | **49**；`QuestGuideIdCheck` 已註冊 ⇒ A2「49→50」可行 ✔ |
| E25 | 我嘅 Java-faithful port 跑 T2f 兩條字串 | `icon: "a\"b" item: "minecraft:stone"` → **`' item: "minecraft:stone"'`** ⇒ v3 更正後嘅期望值**正確** ✔ |
| E26 | `grep -rn QuestIndexFailSoftCheck` ＋ `find -name QuestIndexFailSoftCheck.java` | 只有 plan §7.4 一句「v3 修：刪走…」；**repo 內外都冇此檔** ✔ 幽靈檔已清 |
| E27 | 讀 `tests/check_quest_strip_icons.py:1-70` | mirror **無守衛**，而且 Python slice 語意令嵌套情況**回 `""`（唔會拋）** ⇒ 佢唔可以當 Java oracle |
| E28 | 讀 `QuestGuide.java:137-152` | 現有 `catch (IOException ignored)` 在 **:150-152**；另 **:138-140** `Files.size(p) > 500_000 → return`（**靜默跳過、無 log、無 counter**） |
| E29 | `ls <instance>/config/packai-usage.json` 三個沙盒 | `packai_sandbox_ftb` **冇**（只有 `packai`、`packai-client.toml`）；`packai_sandbox_e9e` = `{"2026-09-19":518136}`；`packai_sandbox` 有 |
| E30 | 讀 plan §1 行 31 原文 | 聲稱「**現實中冇『確定性可觸發』嘅 post-D1 例外輸入**」——但該 fuzz 只覆蓋 `stripQuestIcons` **一個方法**，唔覆蓋 `Files.size`／`Files.readString`／`parseFile` 鏈 |

---

## R2 blocker 對帳表（7 條）

> 判準：**只認 v3 文字**。括號內係 R2 條目號。

| # | R2 要求 | 滿足？ | v3 文字（引文） | 反方 verdict |
|---|---|---|---|---|
| 1 | **(N1) 修 D2 測試列 ＋ A8(b)**：壞檔要「可重現構造」，唔可以用嵌套 icon（D1 已修）；或**計 IOException**、用非法 UTF-8 檔 | **部分** | §5 A8(b)「(i) fixture 全好檔 → assert `skippedFiles == 0`…(ii) **明文承認** catch 分支無法用單元測試確定性觸發…(iii) 真機層以 `skippedFiles == 0` 做觀察點」；**但** §3 D2 表格（行 79）**原文不動**：「放 1 個壞 `.snbt`（**嵌套 icon**）＋1 個好檔 ⇒ 斷言…`skippedFiles=1`」 | A8(b) 改咗 ✔，但 **D2 表格冇改** ⇒ 同一份 plan 兩處互相否定（D1 上咗之後嵌套檔唔會拋，`==1` 必紅）。而且 (b)(i)「全好檔 ⇒ 0」係**零判別力**嘅 null test（counter 寫死 0 都會綠），(b)(ii) 亦冇採納 R2 提供嘅**真實可重現**壞檔（非法 UTF-8）。**唔收貨** |
| 2 | **(N2) 修 A8(c)**：刪走「trace quest hits 非空」，或指名要加邊個 trace 事件＋同步加白名單 | **未滿足** | §5 A8(c)「真機層（**v3 具體化**）：…**≥1 個 case 嘅 trace quest hits 非空**（用該次 run 自己嘅 quest 檔內容對，避免硬編版本 id）」 | **一字未改**（E4 實測：31 個 key ＋ `index.jsonl` 8 個 key 都冇 quest／hit；案例全係 item 問題）。v3 標題卻聲稱「(c) v3 具體化」⇒ 呢個係**宣稱同文字不符**。**唔收貨** |
| 3 | **(N3) 寫死 counter 機制**：刪 `lastSkippedFiles()`、寫死新 overload 簽名、禁 static、禁改非白名單呼叫點 | **未滿足** | §5 尾註「counter **必須 per-invocation**（例如 `index(...)` 加一個 out-param／回傳小 result 物件）…（**R2 請裁決用邊種**）」；§6.1「D1＋D2＋**`lastSkippedFiles()` accessor**」；§3 D2「`index()` 回傳值或 out-param **皆可**」 | 方向對（per-invocation）但**仍然冇名冇簽名**：實作者要自己揀 API，而 A8(b)(i) 嘅 assert 又唔知去邊度讀 count（`AskTrace` 事件？harness 讀唔到）。白名單名 `lastSkippedFiles()` 同「唔准 static」矛盾；「R2 請裁決」係**過期請求**（R2 已裁決：5 參數 overload、現有 3 個簽名一字不改、傳 `null` 即唔要 count）。**唔收貨** |
| 4 | **(N4／N11) 重寫 A4／A5 數值** | **部分（A4 收貨、A5 唔收貨）** | A4「**19/19 有 trace 嘅 case 各自 ≥1 `model.reply`** ＋ tokens 增量 > 0 ＋ body **零** `Query failed`（**v3 更正分母**：…分母寫死 19）」；A5「(i) 有效性…(ii) 零改動證明：`git diff` 顯示落位相關檔（`RecipeEmbed.java`／`AskService.java`／`RecipeCard.java`）**零改動**…(iii) 描述性記錄…同今晚 baseline（主包 **54/316**、E9E **0/73**）並列」 | **A4 收貨**：分母屬實（E2）、量法可執行（E5/E6/E7 用 trace event `model.reply.*`）。**但** tokens 仍然**冇指名來源**（E29：`packai_sandbox_ftb` 根本冇 `config/packai-usage.json`）⇒ 呢半句今日量唔到。<br>**A5 唔收貨**：(ii) `RecipeCard.java` **已經係 M**（E17）⇒ `git diff` 唔可能係零；(iii) 引用嘅 baseline 係**log 行數**，而已入庫腳本輸出係 **per-case**（E9），兩種單位冇得「並列」；而且從未提及要開 `cardPlacementDiagLog`（plan 全文 0 命中），默認 false 下腳本照印「clean」（E10）⇒ 呢條記錄**可以全綠而零資訊**。 |
| 5 | **(N5) 修 A7 ＋ 白名單**：(a) baseline 寫 `%TEMP%`；(b) **每個 dirty 檔**記 sha256；(c) 白名單補 HANDOFF／`code_change_log.md` 或明文例外 | **部分** | §5 A7「(a) 動手前 `git status --porcelain > %TEMP%\baseline_<ts>.txt`（**唔准寫入 repo**）；(b) **同時**記錄**白名單每個檔**嘅 **sha256**…；(c) 收工後：`status` 逐行 diff **只准**白名單檔 ＋ `docs/`（HANDOFF／plan／review）＋ baseline 自身」 | (a) **滿足** ✔（同 §7 一致）。(b) **部分**：只記**白名單**檔 ⇒ 另外 **71 個已 dirty 檔**（含 `code_change_log.md`、`RecipeCard.java`、`tools/kubejs_*.py`）依然「任意改都 A7 全綠」。(c) **未滿足**：`.hermes/plans/HANDOFF.md` **唔喺 `docs/` 之下**（佢喺 §6.8 白名單），條文括號卻寫「`docs/`（HANDOFF／plan／review）」；`code_change_log.md`（**動手前已經 M**、packai `AGENTS.md` 流程第 7 點強制更新）**兩邊都冇**⇒ 實作者一守契約就 `M code_change_log.md` ⇒ **A7 FAIL**。另外尾句「＋ baseline 自身」係 v2 遺留（baseline 已搬 `%TEMP%`，唔會出現喺 git status）。**唔收貨** |
| 6 | **(N9) 改 T2f 期望值** | **滿足** | §4 T2f「`' item: "minecraft:stone"'`（**v3 更正**：icon 值（含轉義引號）被剝走…v2 寫「不變」係錯）」 | 我用 Java-faithful port 實測得到同一個值（E25）✔ **收貨** |
| 7 | **(N6／N7／N8／N10) 一次過掃** | **部分** | §7.4「**v3 修：刪走 v2 §7.4 提到嘅幽靈檔 `QuestIndexFailSoftCheck.java`**」＋「fail-soft 案例放喺 `QuestGuideIdCheck.java`，**唔准開第二個 harness／第二個檔**」 | **N6 滿足** ✔（E26 全 repo 無此檔；規則寫得清楚）。**N7 未滿足**（§7 仍寫 128；實測 127）。**N8 未滿足**（`REMAINING_WORK.md` 仍然唔存在，5 處引用）。**N10 未滿足**（§4 負控清單仍缺 `T3c`）。**部分收貨** |

**對帳結果：2 條滿足（#6、#7 部分中嘅 N6）｜4 條部分（#1、#4A4、#5、#7）｜2 條未滿足（#2、#3）｜1 個新引入缺陷（A5(ii)）。**
v3 自稱「已回應 R2 7:3」是準確嘅；但「**⑦ catch scope 三項（…／壞檔定義／harness 定義）已對齊**」**唔係事實**：壞檔定義（§3 D2）同 harness 定義（§7.4／A8）仲係打架，#3 counter API 亦未寫死。

---

## 新問題 findings（v3 自己引入）

### F-R3-1【BLOCKER / HIGH，bookkeeping】A8(c) 聲稱「v3 具體化」但文字一字未改，仍然無法量度
- 證據：E4（19 條 ask trace 嘅 31 個 key ＋ `index.jsonl` 嘅 8 個 key，**冇** quest／hit／index 欄位）；`index.jsonl` 每條只記 `question/focusId/cardsOut/status`；案例問題全部係 `<item> used for in this pack?`。
- 反方主張：喺一份自己寫「A8 係 P0 修法嘅**唯一防假綠閘**」嘅 plan 入面，一條**量唔到**嘅臂比冇更危險（會令人以為「真檔覆蓋已交付」）。**必須**換成 headless 真檔斷言（R2 flip #2(i)，我今日已再確認沙盒存在、2/755 崩點仍在（E15）⇒ 可做），或者明文寫死要加嘅 trace 事件＋同步加 `AskEngine.java`／`AskTrace.java` 入白名單＋加一個**會問任務**嘅 autotest 案例。

### F-R3-2【BLOCKER / MED-HIGH，bookkeeping】counter API 未定案：白名單名 `lastSkippedFiles()`（static 味）＋尾註「R2 請裁決」，兩者矛盾且 A8(b)(i) 無從實作
- 證據：E20（§6.1 行 147 vs §5 尾註行 143；全文無 overload 簽名）。
- 反方主張：寫死「新增 5 參數 overload（第 5 個係 `int[] skippedOut`），現有 `:87`／`:94`／`:101` 簽名一字不改、傳 `null` 即唔要 count；刪 `lastSkippedFiles()` 呢個名；唔准 static；唔准改 `AskEngine.java:263`／`QuestFetchAskTool.java:48`」。否則實作階段一定要即場做一個未評審嘅設計決定（而 A7 只准改白名單檔，改錯就 FAIL）。

### F-R3-3【BLOCKER / MED-HIGH，bookkeeping】§3 D2「測試」列仍然用「嵌套 icon 壞檔 ＋ `skippedFiles=1`」⇒ 同 D1／A8(b)／§7.4 三邊衝突
- 證據：E21（行 79 原文）＋ §7.4「唔准開第二個 harness／第二個檔」＋ D1（嵌套唔再拋；我 Java 實測守衛後 167,404 條 **0 拋**，E12）。
- 反方主張：照 §3 做 = 卡死喺必紅 assert；照字面「**新增** fail-soft harness」= 多開一個 `*Check.java` ⇒ A2 變 51/51、A7 見非白名單 `??` ⇒ FAIL。要改成同 A8(b) 一致嘅文字（見 flip conditions）。

### F-R3-4【BLOCKER / MED，bookkeeping】A5(ii)「`RecipeCard.java` 零改動」係假陳述（**v3 新增**）
- 證據：E17 — `RecipeCard.java` 動手前已經 **M、+37 行**，內容係 `layoutInputStacks()`／`layoutInputIds()`，註釋明寫「layout-aware…**Gate 同 display 一定要用呢個**」⇒ 正正係「卡落位」路徑。`git diff` 對呢個檔**永遠唔會係零**。
- 反方主張：A5(ii) 嘅判準要改成「同 baseline 比 sha256 **不變**」（或者索性剔除 `RecipeCard.java`）。用 `git diff` 證「零改動」喺一個有 72 個已 M 檔嘅工作樹上係**邏輯上做不到**嘅事（R2 N5 已講過同一個結構問題，v3 只修咗 A7 嘅一半，A5(ii) 又再犯一次）。

### F-R3-5【MED，bookkeeping】A5(iii) 單位唔對（per-case vs log 行數）＋冇寫要開 `cardPlacementDiagLog` ⇒ 記錄可以「全綠但零資訊」
- 證據：E9（腳本輸出 = per-case：主包 15/18、E9E 0/11、FTB 16/20；plan 引用 = 54/316、0/73 = **log 行數**）；E10（run 212833 cardplace 行數 = 0，腳本照印「11 clean」）；plan 全文無 `cardPlacementDiagLog`（`PackAiConfig.java:475` 預設 false）。
- 反方主張：明文寫「跑前確認 `cardPlacementDiagLog=true`；行數 = 0 ⇒ 記 **N/A**，唔准當 clean；判準用 **per-case**（同 `ANALYSIS.txt` 同單位）；54/316 只准入附錄」。

### F-R3-6【MED，design-lite】D2 只計 `RuntimeException` ⇒ 兩條現存「靜默缺檔」路徑仍然靜默，D2 自己嘅目標句唔成立
- 證據：E28（`:138-140` `Files.size(p) > 500_000 → return`，無 log／無 counter；`:150-152` `catch (IOException ignored)`）；E16（非法 UTF-8 → `MalformedInputException`，isIOException=true）⇒ 我今日用真 Java **重新證實** R2 W14。
- 反方主張：D2 條文寫「⇒『**靜默缺檔**』永遠可被發現（SK『唔准 fake success』）」——今日**唔成立**。兩條路：**(a)** counter 計三條路徑（size-cap／IOException／RuntimeException），WARN 各自最多一次 —— 成本 ~6 行，順帶令 fail-soft 有**確定性可觸發**嘅測試輸入（非法 UTF-8 檔，已實測）；**(b)** 保持 P0 最小改動，但**刪走**該句目標宣稱，並在 plan 明文寫「IOException 路徑嘅靜默性係既有行為、唔喺 P0 範圍」。二選一，唔可以留一句唔成立嘅承諾。
- （注意：A8(b)(i)「全好檔 ⇒ `skippedFiles == 0`」在兩種路都值得保留，但佢係**零判別力** null test，唔可以當 fail-soft 嘅回歸保護；真正嘅判別力要嚟自 (a) 嘅 `==1` 案例。）

### F-R3-7【MED，bookkeeping】A7 只 sha256 白名單檔 ⇒ 71 個已 dirty 檔嘅內容改動仍然睇唔到；`code_change_log.md` 唔喺白名單但契約強制要改
- 證據：E19（127 = 72 M ＋ 53 ?? ＋ 2 D）；`code_change_log.md` = **M** 且唔喺 §6 白名單；packai `AGENTS.md` 開發流程第 7 點「HANDOFF／`code_change_log.md` 更新」＋根 `AGENTS.md`「每次 session 完結…必須先 hand off」；§6.8 有 `.hermes/plans/HANDOFF.md` 但 A7(c) 只放行 `docs/`。
- 反方主張：baseline **記全部** dirty／untracked 檔嘅 sha256（唔止白名單）；A7(c) 明確列出允許路徑（`§6 白名單 ＋ docs/** ＋ .hermes/plans/HANDOFF.md ＋ code_change_log.md`），或者明文寫「今次唔改 HANDOFF／code_change_log（例外需 SK 批）」。

### F-R3-8【LOW，bookkeeping】三處過期／冗餘文字
- E18：`REMAINING_WORK.md` **唔存在**（本 repo 無此檔、亦無 track；packai 慣例係 `.hermes/plans/HANDOFF.md`）但被引 5 次（含 A8(b)(ii)「理由寫入…`REMAINING_WORK.md`」）⇒ 違反「唔准 fake success」嘅記錄紀律。
- E19：§7「**128 項**」vs 實測 **127**。
- E22：§4 負控清單缺 `T3c`；T 表冇註明 `CRASH(x,y,z)` 係 JDK 相關訊息、唔可以入 assert。
- A7(c) 尾句「＋ baseline 自身」係 v2 遺留（baseline 已搬 `%TEMP%`）；§6.4 同 §6.9 重複列 `tmp-check.gradle`（同一檔兩個白名單項）。

### F-R3-9【MED，evidence-quality（非阻塞設計）】plan 用嚟支撐「post-D1 零 crash」嘅 fuzz **唔夠力**（但結論我獨立確認為真）
- 證據：E12／E13／E14 —— plan 嘅 fuzz 係 (i) Python port（同 Java 語意有分歧）、(ii) token 集 8 個、長度 ≤4、(iii) 只覆蓋 `stripQuestIcons`、無 Java 交叉驗證。
  我嘅量測：**Java 拋但 port 走漏 996/24,674（4.0%）**；守衛後輸出 Java≠port **11.5%**。差異源 = Java 嘅 `\b`／`\s` **預設係 ASCII**，Python `re` 係 Unicode-aware（例：`éicon:\"a\"` Java 會 match、port 唔會；`icon\u00a0:` port 會 match、Java 唔會）。
- 反方主張：**結論照收**（我用真 Java 167,404 條含 depth-40／unicode／escape／孤立 surrogate **反證唔到**任何 post-D1 crash），**但**§1「port 保真度」註明要補 `\b` 一項（現時只寫 whitespace／UTF-16 offset），而且 plan 唔應該再由 port 單獨支撐任何「冇例外輸入」嘅普遍宣稱——尤其該宣稱嘅**範圍**（line 31 寫「現實中冇確定性可觸發嘅 post-D1 例外輸入」）大過實測範圍（只 fuzz 咗一個方法；`parseFile` 鏈同 `readString` 從未被 fuzz，而後者**確實有**可觸發嘅 `MalformedInputException`，E16）。
- 附註：plan §1 行 31「⇒ 現實中冇…」係由單方法 fuzz 推出全鏈結論，屬**範圍過寬**；建議改成「`stripQuestIcons` 於 N 條隨機／窮舉輸入下 post-D1 零拋；其他鏈路未 fuzz；已知 `readString` 對非法 UTF-8 拋 `MalformedInputException`（IOException）」。

---

## Flip conditions（要點改我就翻 go = true）

> 全部係**改 plan 文字／加一個小設計決定**，唔使改 D1 設計（D1 我 R1 已實證、v3 冇改，我今次用真 Java 再確認）。估計 25–35 分鐘。

1. **【F-R3-1，必須】A8(c) 改文字＋加 (d) 真檔斷言**：
   - 刪：`**≥1 個 case 嘅 trace quest hits 非空**（用該次 run 自己嘅 quest 檔內容對，避免硬編版本 id）`
   - 加：`**(d) headless 真檔斷言（唯讀）**：`QuestGuide.index(<sandbox_ftb>/minecraft, List.of("ftbquests"), null, false)` → assert 命中 `4EFD411CA5975754`（items 含 `ars_nouveau:annotated_codex`）同 `4697678CA1F15CD6`（items 含 `farmersdelight:kelp_roll_slice`）；呢條係「兩個真任務唔再靜默消失」嘅唯一覆蓋。`
   - 理由寫入 plan：ask trace 31 個 key ＋ `index.jsonl` 8 個 key 都無 quest／hit 欄位（本輪實測），「trace quest hits 非空」無法評估。
2. **【F-R3-2，必須】寫死 counter API**：§6.1 改成「`QuestGuide.java`（D1＋D2；**新增 5 參數 overload** `index(Path, List<String>, String, boolean, int[] skippedOut)`；現有 `:87`／`:94`／`:101` **簽名一字不改**、內部 delegate、`null` = 唔要 count；**刪除** `lastSkippedFiles()` 呢個名）」；§5 尾註刪走「（R2 請裁決用邊種）」並補「R2 已裁決：out-param（`int[]`／小 record）＋ 新 overload；**唔准** static 欄位；**唔准**改 `AskEngine.java:263`／`QuestFetchAskTool.java:48`」；A8(b)(i) 明確寫「harness 用新 overload 讀 `skippedOut[0]`」。
3. **【F-R3-3，必須】§3 D2「測試」列改成**：
   `| 測試 | **喺 `QuestGuideIdCheck.java` 加 fail-soft 案例（唔准開第二個 harness／第二個檔）**：(i) 全好檔 ⇒ `skipped == 0`；(ii) 放 1 個**非法 UTF-8 byte** 嘅 `.snbt` ＋1 個好檔 ⇒ 好檔照樣索引到、`skipped == 1`、每檔最多一次 `WARN`。**唔准用「嵌套 icon」當壞檔**（D1 已令佢唔再拋）。 |`
4. **【F-R3-4，必須】A5(ii) 改成 sha256 判準**：
   `(ii) **零改動證明**：收工以 `sha256sum -c` 對 baseline 逐檔比對；判準 = `RecipeEmbed.java`／`client/service/AskService.java`／`RecipeCard.java` 嘅 sha256 同 baseline **完全一致**（**唔用 `git diff`**——`RecipeCard.java` 動手前已經係 `M`（+37 行 `layoutInputStacks()`／`layoutInputIds()`），`git diff` 永遠唔係零）。`
5. **【F-R3-5，必須】A5(iii) 改成**：
   `(iii) **描述性記錄（唔做閘）**：跑前確認 sandbox `config/packai-client.toml` 嘅 `cardPlacementDiagLog=true`（`PackAiConfig.java:475` 預設 false）；用 `tools/analyze_cardplace.py <run_dir>` 記 **per-case** 結果（主包 15/18 case、E9E 0/11——同 `docs/research/artifacts/2026-09-19-cardplace-run/ANALYSIS.txt` 同一單位）；**cardplace log 行數 = 0 ⇒ 記 `N/A`，唔准當 clean**（實測 run 212833 為零行仍印 11 clean）。原 log 行數 54/316、0/73 只准入附錄。`
6. **【F-R3-7，必須】A7 補兩處**：(b) 改「記錄**全部** dirty／untracked 檔（`git status --porcelain` 全量 127 個，72 M ＋ 53 ?? ＋ 2 D）嘅 sha256，唔止白名單」；(c) 改「只准以下路徑：§6 白名單 ＋ `docs/**` ＋ `.hermes/plans/HANDOFF.md` ＋ `code_change_log.md`；任何其他 M/??/D = FAIL」，並刪走尾句「＋ baseline 自身」。
7. **【F-R3-6，必須但可二選一（design 決定）】**：**(a)** D2「可數性」列改成「`skippedFiles` 計**三條**靜默路徑：`:138` size-cap skip、`:150` `catch (IOException)`（含非法 UTF-8）、新 `catch (RuntimeException)`；三者都 increment ＋ 各自最多一次 `WARN`」；**或 (b)** 保留只計 `RuntimeException`，但**刪走**「⇒『靜默缺檔』永遠可被發現」呢句，並明文寫「IOException／size-cap 靜默性屬既有行為，唔喺 P0 範圍（記入 HANDOFF 一行）」。
8. **【F-R3-8，應該】一次過清**：5 處 `REMAINING_WORK.md` → `.hermes/plans/HANDOFF.md`（或新建 `docs/plans/REMAINING_WORK.md` 並入白名單）；§7「128 項」→「127 項（72 M＋53 ??＋2 D，2026-09-19 實測）」；§4 負控清單補 `T3c`；§4 T 表加一句「`CRASH(x,y,z)` 係 JDK17 訊息，harness 只可 assert『拋／唔拋』，唔准 assert 內容」；§6 去重 `tmp-check.gradle`。
9. **【F-R3-9，應該】§1 補 port 保真度**：加「Python `re` 嘅 `\b`／`\s` 係 Unicode-aware、Java 預設係 ASCII ⇒ port 對 `éicon:`／`icon\u00a0:` 會得出同 Java 唔同嘅 match（本輪實測：Java 拋而 port 走漏 996/24,674；守衛後輸出 Java≠port 11.5%）」；並把行 31 嘅宣稱範圍收窄到 `stripQuestIcons`。

---

## 我認為正確、唔會改（正方立場）

1. **根因 ＋ D1 完全不變，而且我今次用真 Java（唔係 port）再確認**：逐字拷貝 `:1529-1589`，current 版喺我 167,404 條 corpus 上拋 **24,674** 次；加 D1 `if (m.start() < last) continue;` 之後 **0** 次（含 depth-40 嵌套、NBSP／U+2007／U+202F／U+3000、combining、astral、CR、轉義、孤立 surrogate）⇒ **D1 係充分嘅**，R2 嘅「結構性 ⇒ 唔可能再 `start < last`」亦成立。
2. **我接受 v3 嘅前提「post-D1 冇確定性可觸發嘅（RuntimeException）輸入」**——我攻擊失敗（0 counterexample）；所以我**撤回** R2 flip #1 中「要指名一個 D1 後仍拋 RuntimeException 嘅輸入」呢個分支要求，改成「**改用非法 UTF-8 檔測 `skipped`（IOException 路徑）**」。呢個係我本輪唯一因為新證據而改變嘅立場。
3. **A4 分母 19/19 完全正確**（status json 實錘：`caseCount=20`、19 個 `traceFile`、1 個 `survival_random_8` = `NO_SAMPLE`），而且**量法可執行**：我用 trace event `model.reply.*` 實測主包 18/18、E9E 11/11、FTB 0/19。呢條係 v3 最紮實嘅修正。
4. **A8(a) 內容斷言有判別力**（R2 已獨立驗；我無異議）——仍然係 P0 最有效嘅防假綠閘。
5. **T2f 新期望值正確**（我嘅 Java-faithful port 得 `' item: "minecraft:stone"'`）。
6. **幽靈檔已清 ＋「唔准開第二個 harness／第二個檔」規則寫得清楚**（全 repo `find`／`grep` 確認無此檔）。
7. **`tools/analyze_cardplace.py` 入庫係對嘅**：我跑 211621 得 15「疑似」／3「clean」，同 `ANALYSIS.txt` 逐字一致 ⇒ 量法真係可重現（問題只係 A5(iii) 引用嘅 baseline 單位同 flag）。
8. **A3 嘅 baseline 我全量實跑**：123 = 122 rc=0 ＋ 1 rc=2（`check_ask_display_leak.py`），同 plan 一致；跑完 git status 仍 127（閘係唯讀）。
9. **A5 取消絕對數值帶係正確判斷**（`tools/cardplace_sampler.py` 明文每次重抽；分子分母必變）。
10. **A2 可行性**：`find` = 49 個 `*Check.java`、`QuestGuideIdCheck` 已註冊、generator 契約（`run`+stem、`-ea`）同 plan 一致 ⇒ 50/50 講得通。
11. **2/755 我獨立重現**（用 **Java 語意** port：`\b`／`\s` 純 ASCII）：FTB 2/755（`#26 len=1214 (742,696)`、`#81 len=780 (466,415)`）、E9E 0/1328、主包 0/2517 ⇒ 單位同數字都站得住。
12. **邊界（唔准 hot-copy jar／唔准部署真 instance／沙盒 only／唔准 commit／`status.ok` 唔算證據）** 寫得清楚，我唔會改。

---

## NOT RUN（我冇做／做唔到，唔會當已驗）

- **冇跑** `./gradlew.bat compileJava compileTestJava`、**冇跑**任何 Java harness（`runQuestGuideStripIconsCheck` 未存在）⇒ **A1／A2／A6 未驗**；我亦**冇**驗「現時 49/49 harness 全綠」（v3 A2 嘅前提，仍然**未經任何人實跑**）。
- **冇**跑 D3 嘅兩條 `grep` 全量掃描（R2 已跑，v3 未改 D3；我無異議）。
- **冇**反編譯已部署 jar（A9／jar 內含 `stripQuestIcons`）——只沿用 R1/R2 嘅 sha 頭 16 位 `06b5b129a114a233` 記錄。
- **冇**跑真機（A4／A5 未驗）⇒ `skippedFiles == 0` 嘅真機觀察、tokens 增量、`Query failed` 為零，全部**未驗**。
- **冇**改任何 repo 檔（除本報告）、**冇** build／commit／部署／開遊戲／動沙盒；沙盒只**唯讀**讀檔掃檔（E15）＋唯讀讀 `config/packai-usage.json`（E29）。
- **冇**驗 Java 側 `stripQuestIcons` 以外嘅方法鏈（`parseFile`／`parseQuestsArray`／`itemsInRange`／`depth1Field`）嘅例外行為——我只做咗**靜態**讀碼（`:938-1053`、`:1160-1300`、`:1458-1477`，所有 index 都係 clamp／derived；全檔 `throw` 數 = 0）＋ 對 `readString` 嘅實測（E16）。呢個係 F-R3-6 嘅證據基礎，但**唔係**完整 fuzz。
- **A5(iii) 新量法**未真跑（要開 flag ＋ 真機 run）⇒ 我只有「腳本可重現 ANALYSIS.txt」同「零行仍印 clean」兩個觀察。
- **`(758,710,1259)` 簽名**仍然 **NOT REPRODUCED**（我掃三包只出 1214／780 兩個 slice，同 R1／R2 一樣）；v3 §1 已老實標「未證實」，我同意保留。

---

## 停手報告（SK 規則：第 3–4 輪仍未 8:2 → 停手問 SK）

- **逐輪比分**：R1 **6:4**（go=false）→ v2 → R2 **7:3**（go=false）→ v3 → **R3 6:4（go=false）**。
- **卡死嘅載重決定（邊幾條、點解）**：
  1. **A8(c) 用邊種方法覆蓋「真檔兩個任務唔再靜默消失」** — headless 真檔 `index()` 斷言（R2／我建議、可即做）vs 加 trace 欄位＋新案例（要擴白名單＋改非白名單檔）。**技術上仲未揀定**，而呢條係全 plan 唯一覆蓋「真檔」嘅閘。
  2. **D2 counter 嘅範圍**：計唔計 `IOException`／size-cap 兩條**既有**靜默路徑？（＝P0 範圍爭議，唔係技術未知。）
- **最貴嘅未知（要咩數據才解得開）**：① 修好之後真機 FTB 跑一次，`skippedFiles` 係唔係 **0**、19/19 case 有唔有 `model.reply`（＝要 SK 唔打機 10 分鐘 ＋ ~50 萬 tokens）；② A5(iii) 新量法喺真機下嘅 per-case 數值；③ 現時 49/49 Java harness 係唔係真綠（要跑 gradle）。
- **我嘅建議**：**收窄範圍、拆細**——P0 只做 **D1 ＋ A8(a/d) ＋ A5（效性／sha256／描述性）＋ A7 修正 ＋ A4**，即「修好崩潰 ＋ 有得驗」；其餘兩件（counter 計三條路徑、Python mirror 同步）**降級去 P1**（各自有獨立價值、唔阻 P0）。如果 SK 想一次過，最省時係批准上面 **flip 1–7 嘅精確文字改動**（純文字＋6 行 code 決定），改完我**唔需要**再開 R4，可以直接照 plan 開工。

---

> 方法附註：Java 語意結論全部來自 `%LOCALAPPDATA%\Temp\r3_java\{Probe3.java,Probe4.java}`（`Probe3` 逐字拷貝 `QuestGuide.java:1529-1589` ＋ D1 guard；JDK 21 Temurin `javac/java`；corpus `corpus_b64.txt` 167,404 條、結果 `java_out.tsv`／`java_hard.tsv`）；三包重掃用我自寫 **Java 語意** port `r3_pack_scan.py`（`\b`／`\s` 純 ASCII、`isJavaWhitespace`）。Python 閘輸出 `%TEMP%\r3_gate_check.txt`。以上全部喺 repo 外，可重跑。
