# 反方報告（R2 opposing）— P0 `QuestGuide.stripQuestIcons` 嵌套 icon 崩潰修法計劃 **v2**

- 對象：`docs/plans/2026-09-19-p0-questguide-stripicons-crash.md`（**v2**）＝ commit `15bd22c`
  （`git log -1 --oneline -- <plan>` → `15bd22c docs(plan): P0 questguide v2 - addresses all 10 R1 flip conditions ...`）
- 我上一輪（R1）：`docs/plans/reviews/2026-09-19_p0-questguide-stripicons-crash-R1-opposing.md`（6:4、go=false、10 條 flip conditions）
- Reviewer：Hermes（反方）／日期：2026-09-19 深夜／輪次：**R2**（上限 3–4 輪，未到停手線）
- 版本界線：`forge/1.19.2`（Forge 43.4.5、MC 1.19.2、`mod_version=0.2.3`）；`neoforge/1.21.1` PAUSED，不審。
- **只讀聲明**：本輪我**冇改、冇新增、冇刪除 repo 內任何檔案**（唯一新增 = 本報告）。我**冇**跑 gradle／**冇** build／**冇** commit／**冇**部署／**冇**開遊戲／**冇**動沙盒。
  我有**跑** `python tests/check_*.py`（唯讀閘，123 個，輸出寫去 `%TEMP%`）。
  Java 語意我**冇**用 gradle：我喺 `%LOCALAPPDATA%\Temp\r2_java_probe\` 寫咗兩個 standalone probe（`Probe.java`／`Probe2.java`，**逐字拷貝** `stripQuestIcons`）用 JBR 21 `javac/java` 跑（repo 外，唔屬 build）。
- 已核實／推斷分開標示；跑唔到嘅一律寫 **NOT RUN**。

---

## 總分

**正方 7 : 反方 3 ｜ go = false**

一句話：**v2 真係做咗功課** —— R1 10 條入面 4 條完全滿足（§7 還原重寫、§3 語意收窄＋診斷＋T3a、A2 harness 契約、F8/F12/F13/F14），另外 6 條係「部分」而唔係「冇做」；而且 A8(a) 係**真嘅防假綠閘**（我用真 Java 驗到判別力）。
但**我不能開 go**，因為 v2 用嚟答 R1 F1 嘅嗰條閘，**三個臂有兩個係壞嘅**：

1. **A8(b)／D2 測試列自相矛盾** —— 「壞檔」定義為「嵌套 icon」嘅檔；但 **D1 一上，嵌套 icon 就唔再拋** ⇒ `skippedFiles` 永遠 0，斷言 `==1` **必紅**（實作者根本做唔到綠）。
2. **A8(c) 冇得量** —— trace schema **完全冇 quest 欄位**（我列咗全部 27 個 key）；三個 run 共 **48/48** 條 trace 嘅 `send.facts` 都係空 ⇒「≥1 個 case 嘅 trace quest hits 非空」**無法評估**。
3. **A5 條帶唔成立** —— 316／73 係 **log 行數**（每次 body 更新一行），唔係 case 數；量佢仲要**手動開**預設 `false` 嘅 `cardPlacementDiagLog`；「同一分析腳本」**唔存在**；而 `tools/cardplace_sampler.py` 明文寫「每次 run 必須重新隨機抽」⇒ 分子分母兩邊都會郁。

呢三條全部係**改 plan 文字**可修（唔使重開 D1 設計），所以係「差一輪」而唔係「差好多」——但唔修就唔可以開工。

---

## 我親手做過嘅驗證（evidence base，全部可重跑）

| # | 動作 | 結果 |
|---|---|---|
| W1 | `for f in tests/check_*.py; do python "$f"; done` → `%TEMP%\r2_gate_check_215915.txt` | **123 檔 = 122 rc=0 ＋ 1 rc=2（`tests/check_ask_display_leak.py`）** |
| W2 | `diff <(sort %TEMP%\gate_baseline_20260919.txt) <(sort <我份>)` | **完全一致（無 output）** ⇒ A3 嘅 baseline 講法**正確且可重現** ✔ |
| W3 | `git status --porcelain \| wc -l` ＋ 分類 | **127 = 72 M ＋ 53 ?? ＋ 2 D**（v2 §7 寫 **128 項**） |
| W4 | 白名單檔狀態 | `QuestGuide.java`**乾淨**、`QuestGuideIdCheck.java`**乾淨**、`tests/check_quest_strip_icons.py`**乾淨**；`AskReplyScrub.java` = **M**；`OfficialDisplay.java` = **??**；`tmp-check.gradle` = **??** |
| W5 | `git status --porcelain \| grep '^ D'` | `client/gui/PackAiSettingsScreen.java`、`client/gui/RecipeCategoryScreen.java` |
| W6 | `git status --porcelain .hermes/plans/HANDOFF.md code_change_log.md docs/plans/` | **`M code_change_log.md`**（**已經 dirty**、已 tracked）；HANDOFF／docs/plans 乾淨 |
| W7 | `find . -iname "*REMAINING*"` ／ `git ls-files \| grep -i remaining` | **兩個都空** ⇒ 本 repo **冇** `REMAINING_WORK.md` |
| W8 | `research/gen_tmp_check.py` 逐字讀 | 任務名 = **`"run" + <ClassName>`**、`rglob("*Check.java")` 自動收集、`jvmArgs '-ea'` ⇒ A2 嘅 `runQuestGuideStripIconsCheck`／49→50 **命名正確** ✔；`grep -n QuestGuideIdCheck tmp-check.gradle` → **:47 已註冊** ✔ |
| W9 | 8 個 `QuestGuide.index` 呼叫點 grep | forge：`AskEngine.java:263`、`QuestFetchAskTool.java:48`、`AcquireFactsCheck.java:442`、`QuestGuideIdCheck.java:59/115/160/223`、`QuestLocalePreferCheck.java:53` |
| W10 | `AskService.java:93,314` | 註釋「run AskEngine **off-thread**」＋ `CompletableFuture.supplyAsync(...)` → **`AskEngine.ask` 喺 worker thread 跑** ⇒ static 計數器 race 係真 |
| W11 | `grep PackAiMod\|LOGGER QuestGuide.java` | **目前 0 個** ⇒ D1 診斷要**新增 import**（同檔，冇 A7 問題，但 plan 冇提） |
| W12 | 我**自己重寫** Python port（`topLevelObjects` ＋ `stripQuestIcons`，含「`last > m.start()` ＝拋」判別）掃 `packai_sandbox_ftb/.../config/ftbquests` | **51 候選檔、755 個 top-level slice、2 個崩**：`getting_started.snbt#26` `len=1214` `id=4EFD411CA5975754` `title="Starting Your Island"`、`#81` `len=780` `id=4697678CA1F15CD6` ⇒ **獨立重現 R1 V6/V7 同 v2 §1「2/755」** ✔ |
| W13 | Java probe #1（真 `javac`／JBR 21，逐字拷貝 `stripQuestIcons`）跑 v2 §4 全部 T 字串 | 見 §「T1–T5 逐字核對」：**18/19 正確、T2f 錯** |
| W14 | Java probe #1 讀非法 UTF-8 檔 | `Files.readString(p, UTF_8)` → **`java.nio.charset.MalformedInputException`（isIOException=true、isRuntimeException=false）** |
| W15 | Java probe #2：405 條隨機／指定字串，Java(guarded) 對 Python mirror 做差分 | mirror **加守衛後**只 2 條唔同（`U+00A0`／`U+2007`，即已知 whitespace 差異）；mirror **現狀（無守衛）** 有 **8 條**唔同 |
| W16 | `%TEMP%\autotest_results_20260919-{211621,213228,213821}` 全量解析 | 見下方「A4／A5 實測」 |
| W17 | grep `adjacentCardPairs` 全 repo（排除 backups） | 只出現喺 `AiAssistantScreen.java:876-910`（**log 產生端**）、`HANDOFF.md`、plan、R1 ⇒ **冇任何分析腳本** |
| W18 | `PackAiConfig.java:475` | `.define("cardPlacementDiagLog", false)` ⇒ **預設關**；`tests/check_cardplace_instrument.py` 仲係**釘死**呢個 default-false 行為 |
| W19 | `tools/cardplace_sampler.py` docstring | 「SK requirement (2026-09-19): every test run must **randomly re-draw items per class**, so a run never reuses the previous run's items.」 |
| W20 | trace schema key 掃描（FTB run） | 全部 key：`after, attachCards, before, body, cardsOut, category, content, dumpLevel, emissionRefs, event, filterVariant, hasCards, hasVariant, intent, item, maint, maintCards, outputsSize, placement, primaryOutputId, question, reason, recipe_card_markers, role, rules, sourceItemId, src, tools, ts, turns, upgradeCards` ⇒ **冇 quest／hit／index 類欄位** |

---

## 逐條 flip condition 對帳表（10 行）

> 判準：**只認 v2 文字**，唔認 commit message 嘅 summary（commit message 只係「聲稱」）。

| # | R1 condition | 滿足？ | v2 文字（引文） | 反方 verdict |
|---|---|---|---|---|
| 1 | **F1**：加 A8 防假綠（headless index 斷言：**真檔兩個 id** ＋ items；真機層 ≥1 case quest hits 非空；明文 `status.ok`／`cardsOut` 唔算證據） | **部分** | §5 A8：「**headless index 內容斷言**（實作落點：擴充已存在嘅 `QuestGuideIdCheck.java`）… (a) fixture gameDir 放**合成** quest 檔（含嵌套 `icon:{…Icon:…}`）→ assert 返回到該 quest id ＋ items 含指定 id…（b）fail-soft… `QuestGuide.lastSkippedFiles() == 1`…（c）真機層：≥1 個 case 嘅 trace quest hits 非空」；§5 尾註「A8 係 P0 修法嘅**唯一防假綠閘**」 | (a) **成立且具判別力**（我用真 Java 證：嵌套輸入令現行碼拋 IOBE ⇒ 只加 D2 時該檔被跳過 ⇒ id 唔會入 index ⇒ 斷言紅 ✔）。(b) **壞**（見 N1）。(c) **無法量度**（見 N2）。另外 R1 要求嘅**兩個真 id 冇任何一條 assertion 覆蓋**。**唔收貨** |
| 2 | **F2**：§7 重寫（baseline 另存、白名單檔先備份、禁裸 `git checkout --`） | **滿足** | §7「實況（今晚親核）：工作樹**唔乾淨**…『回到現狀 ≠ `git checkout --`』」「1. 動手前：`git status --porcelain > %TEMP%\baseline-<ts>.txt`（另存）＋記 `git rev-parse HEAD`。2. 每個白名單檔**先 copy 一份**到 `%TEMP%\p0_backup_<ts>\`… 3. **唔准**對『已 M』或『untracked』嘅白名單檔用裸 `git checkout --`」 | **方向完全正確**，R1 三點（M／??／dirty-tree 概念）全部答到。兩個小錯：**128 vs 127**（N7）、**幽靈檔 `QuestIndexFailSoftCheck.java`**（N6） |
| 3 | **F3**：A7 改 baseline 逐行 diff、覆蓋 `tests/` | **部分** | §5 A7：「`git status --porcelain > baseline`，收工後 diff **只准**出現白名單檔（＋baseline 自身）之任何變動；其他任何 M/??/D = FAIL。**覆蓋範圍包括 `tests/`**」 | 意圖對、`tests/` 有講 ✔。但三個洞：**baseline 會被寫入 repo 根**（自我觸發 FAIL）、**status 碼睇唔到「已經 dirty」檔嘅內容改動**（baseline 已有 72 個 M）、**HANDOFF.md 唔在白名單但 AGENTS.md 強制要更新**（見 N5）。**唔收貨** |
| 4 | **F5＋F6**：T1–T5 每格**逐字 expected string**；字串兩條標「既有缺陷」；負控逐條點名 | **部分** | §4「**v2：逐字 expected string**」＋「**負控（v2：逐條點名，回應 F6）**：移除 D1 後**必須翻紅**嘅 assertion：T1a／T1b／T1c／T1d／T1e／T3a、T4、A8 嘅 Java 層」＋「**明文記錄**：T2*、T3b–T3e、T5 **本來就唔會翻紅**」 | 我逐字核過 **19 格**（真 Java）：**T1a `""`／T1b `""`／T1c `""`／T1d `"{  }"`／T1e `"pre  post"`／T2 `" tail"`／T2b `' item: "minecraft:stone" '`／T2c `""`／T2d `"}"`／T2e `'5 item: "minecraft:stone"'`／T3a `"head "`／T3b `"head "`／T3c `"   \n\t "`／T3d `'desc: "text more" item: "minecraft:stone"'`／T3e `'desc: "see '`／T4 `"pre  post"`／T5 三條 —— 全部正確** ✔。<br>**T2f 錯**（N9）：v2 寫期望「不變（escape 正確）」，真 Java 輸出係 `' item: "minecraft:stone"'`（`icon: "a\"b"` **真係被剝走**），而且「不變」本身就違反 v2 自己嘅「逐字」政策。<br>負控清單**漏咗 T3c**（佢同樣有嵌套 `Icon:`、同樣會翻紅），危害低但唔完整 |
| 5 | **F4**：§3 承認 unterminated `{` 截尾 ＋ 加診斷 ＋ T3 加案例 | **滿足** | §3「⚠️ **畸形 SNBT（unterminated `{`）**：會令尾部內容被靜默截走…**對策（v2 新增）**：跳過時加 **一行診斷**（`PackAiMod.LOGGER.debug`，帶 `last`／`m.start()`／`text.length()`，只喺 `last > m.start()` 時出…）」；T3a 期望 `"head "` | 三點全中，期望值我 Java 驗過 ✔（`"head "`）。唯一瑕疵：診斷要**新 import `PackAiMod`**（現時 QuestGuide 冇用 LOGGER，W11），同埋要寫入嘅 `REMAINING_WORK.md` **根本唔存在**（N8）。**收貨** |
| 6 | **F7**：D2 寫死 catch 位置／類型／logging／counter／partial state ＋ 加 fail-soft 驗收項 | **部分** | §3 D2 表：「catch 位置：**`QuestGuide.java:137-152` 個 lambda body 內部**（唔准喺 `:95`／`:123`…)」「catch 類型：只 `catch (RuntimeException e)`；**唔准** `Throwable`」「logging：**每檔最多一次 `WARN`**… stack 落 `DEBUG`」「可數性：加 **`skippedFiles` counter**（例：`AskTrace` event…；`QuestGuide.index()` 回傳值或 out-param 皆可）」「partial state 交代…**v2 明文寫入 plan**」「測試：**新增 fail-soft harness**：fixture gameDir 內放 1 個壞 `.snbt`（**嵌套 icon**）＋1 個好檔 ⇒ 斷言…`skippedFiles=1`」 | R1 要求嘅 scope／類型／每檔一次 WARN／partial state **全部答到而且正確**（`:137-152` 個 lambda 係啱嘅層）✔。但兩處致命：(i) 「壞檔（嵌套 icon）」**同 D1 直接矛盾**（N1）；(ii) counter API 自我矛盾（A8(b) 要 static accessor、§5 尾註又禁 static）＋ out-param 會撞 8 個呼叫點（N3）。**唔收貨** |
| 7 | **F10**：加 checked-in fixture（兩個真 slice）＋三包 sha256；§1 列全部簽名；單位改 2/755 | **部分** | §1：「三種簽名…`(742,696,1214)`×1、**`(758,710,1259)`×18**、`(466,415,780)`」「**正確指標（v2）**：**2 / 755 個任務 slice 崩**」；§4「**fixture 政策（回應 F10）**：**唔會** commit FTB Skies Expert 嘅任務原文（第三方內容）；改用**同等結構嘅合成 fixture**…＋『真檔驗證』擺喺真機層 A8（用 sandbox 現成檔案，run 時記錄 3 個 quest 檔 sha256）」 | 簽名全列 ✔、單位改對 ✔，而且我**獨立**掃沙盒重現 **2/755 ＋ 兩個 quest id ＋ 兩個 len（1214／780）**（W12）⇒ 呢部分可信。<br>但 (i) **兩個真 slice 冇入庫**：合成 fixture 只證「同一機制」，唔證「呢兩個真任務唔再靜默消失」；(ii) 承諾補呢個洞嘅 **A8(c) 冇得量**（N2）⇒ 真檔驗證實際上**冇交付**。**唔收貨** |
| 8 | **F11**：A4 寫死 20/20 ＋ `model.reply` ≥1/case ＋ tokens > 0 ＋ 零 `Query failed`；A5 寫數值帶 ＋ 先訂 E9E baseline | **未滿足** | §5 A4「真機 FTB：**20/20 每個 case 嘅 trace ≥1 個 `model.reply`** ＋ tokens 增量 > 0 ＋ body **零** `Query failed`」；A5「主包 `cases_main.json` 重跑 → … `adjacentCardPairs` 樣本數 **∈ [48, 60] / 316**（今晚 baseline 54）；E9E 重跑 → 樣本數 **73**、`adjacentCardPairs` **= 0**」 | 文字係「數值」咗 ✔（R1 要嘅形式答到），但**三個數全部唔成立**：<br>**A4**：baseline run 係 `caseCount=20` 但**只有 19 條 trace**（`ok=19`、`capped=true`）⇒ 「20/20」**照住同一 harness 做唔到**；而且 20/20 會被 LLM／網絡抖動搞紅（同 crash 無關）。<br>**A5**：316／73 係 **log 行數**、要**手動開預設 false 嘅 flag**、**冇分析腳本**、抽樣器**設計上每次重抽**。詳見 N4。**唔收貨** |
| 9 | **F9**：A2 指名 task／重生 generator／harness 契約／local-only 聲明 | **滿足** | §5 A2「Java harness **50/50 全綠**（原 49 ＋ `runQuestGuideStripIconsCheck`）；跑前**先 `python research/gen_tmp_check.py` 重生 `tmp-check.gradle`**」；§4「Task 名：**`runQuestGuideStripIconsCheck`**；harness 契約（house style）：`public static void main` ＋ `assert`（靠 `jvmArgs '-ea'`）＋失敗拋 `AssertionError` ＋尾行 `System.out.println("… OK")`（無 junit 依賴）」；§9「Java harness 係 **local-only**…**已記入 REMAINING_WORK**」 | 我逐項核 generator（W8）：任務名 **真係 `"run"+stem`**、`-ea` 真係 generator 加、`QuestGuideIdCheck` 真係已註冊 ⇒ 命中率 49→50 講得通 ✔。唯一瑕疵同 #5 一樣：`REMAINING_WORK.md` 唔存在（N8）。**收貨** |
| 10 | **F8**（mirror）／**F12**（D3）／**F13**（版本推論）／**F14**（port 保真） | **滿足** | §6.5「**`tests/check_quest_strip_icons.py`（v2 新增，回應 F8）**…決定：**同步**加守衛 ＋ 加嵌套 assert」；§3 D3「**v2 改成 repo-wide grep ＋ 逐條 verdict**」＋「**D3 完成定義（v2 新增）**：(a) 已跑上述兩條 grep…(c) 任何『要改』項都有可重現輸入」；§1「**FTB Skies Expert 與 E9E 用同一支 `ftb-quests-forge-1902.5.10-build.497.jar`**…判別因素係 pack 內容…**唔可以**讀成『1902.5.9 安全、1902.5.10 中招』」；§1「port 保真度（v2 註明）…NBSP `\u00a0`：port 報 CRASH、**Java 唔會**…」 | 四條全部有實質動作 ✔。我另外驗到：**同步係可行嘅**（mirror＋守衛 對 Java 差分 403/405 一致，只有 `U+00A0`／`U+2007` 兩條係已知 whitespace 差異，W15）——**但「同步」唔等於「偵測漂移」**（冇任何測試交叉比對 Java↔Python），所以 v2 呢句「否則…A3 永遠測唔到」嘅理由要收窄成「對齊現狀」（N-finding，非阻塞）。**收貨** |

對帳結果：**4 條滿足（#2、#5、#9、#10）｜5 條部分（#1、#3、#4、#6、#7）｜1 條未滿足（#8）**。作者聲稱「10/10 addressed」，準確講法係「**10/10 有回應、6/10 未達標**」。

---

## T1–T5 逐字核對（真 Java，唔係 port）

方法：`%TEMP%\r2_java_probe\Probe.java` = **逐字拷貝** `QuestGuide.java:1529-1589`，加一個 `guard` flag（＝v2 §3 D1 嘅 `if (m.start() < last) continue;`），JBR 21 跑。

| ID | v2 期望 | 真 Java（D1 版） | 判 |
|---|---|---|---|
| T1a | `""`（current：CRASH(19,8,19)） | `""`；current = **throw IndexOutOfBounds**（Range [19, 8) / length 19） | ✔ |
| T1b | `""`（CRASH(15,6,15)） | `""`；current throw（Range [15, 6) / 15） | ✔ |
| T1c | `""` | `""`；current throw（Range [19, 8) / 19） | ✔ |
| T1d | `"{  }"` | `"{  }"`；current throw（Range [19, 9) / 21） | ✔ |
| T1e | `"pre  post"`（雙空格） | `"pre  post"`；current throw（Range [79, 57) / 84） | ✔ |
| T2 | `" tail"` | `" tail"`（current 同） | ✔ |
| T2b | `' item: "minecraft:stone" '` | 同 | ✔ |
| T2c | `""` | `""` | ✔ |
| T2d | `"}"` | `"}"` | ✔ |
| T2e | `'5 item: "minecraft:stone"'` | 同 | ✔ |
| **T2f** | **「不變（escape 正確）」** | **`' item: "minecraft:stone"'`（`icon: "a\"b"` 被剝走）** | **✖ 錯**（N9） |
| T3a | `"head "` | `"head "`；current throw（Range [82, 30) / 82） | ✔ |
| T3b | `"head "` | `"head "`（current 同） | ✔ |
| T3c | `"   \n\t "` | `"   \n\t "`；current throw（Range [19, 8) / 25） | ✔ |
| T3d | `'desc: "text more" item: "minecraft:stone"'` | 同（既有缺陷） | ✔ |
| T3e | `'desc: "see '` | 同（既有缺陷） | ✔ |
| T4 | `"pre  post"`（＋零 `Icon:`／`custom_icon` 殘留） | `"pre  post"`；current throw（Range [85, 57) / 90） | ✔ |
| T5 | `null→""`、`""→""`、`"plain"→"plain"` | 三條都對 | ✔ |

**兩個要寫入 plan 嘅技術細節**：
- 例外**訊息**係 JDK 版本相關：JDK17（mod 實際 runtime，`AGENTS.md` 指 eclipse_adoptium-17）係 `StringIndexOutOfBoundsException: start 758, end 710, length 1259`（真 log）；JDK21 係 `IndexOutOfBoundsException: Range [758, 710) out of bounds for length 1259`（我 probe）。⇒ harness **只可以 assert「拋出」，唔准 assert message**；v2 表格括號內嘅 `CRASH(19,8,19)` 只可以當**註釋**，唔可以當斷言（N12）。
- NBSP：真 Java 輸出 `"\u00a0{  } item: \"minecraft:stone\""`、**唔會拋** ⇒ 確認 v2 §1 嘅 port 保真度註明 ✔，亦係 R1 F14 嘅結論。

**fixture 完整性**：v2 §4 已明寫「唔准用省略號」「所有 fixture **逐字寫全**」 ✔（我用 §4 表格嘅字串直接跑得出上面結果，即表格本身可當 fixture 用）。**但 T2f 一格例外**：佢嘅「期望」唔係字串，實作者若照抄落 `assert`，就會用一個錯期望去測一段**正確**嘅 code（紅色會出現喺冇問題嘅地方）。

---

## 新問題 findings（v2 自己引入）

### N1【BLOCKER / HIGH】D2 測試列 ＋ A8(b) **自相矛盾**：壞檔用「嵌套 icon」，但 D1 已經令嵌套 icon 唔再拋
- v2 文字：§3 D2 表「測試：**新增 fail-soft harness**：fixture gameDir 內放 1 個壞 `.snbt`（**嵌套 icon**）＋1 個好檔 ⇒ 斷言：好檔照樣索引到、`skippedFiles=1`」；§5 A8(b)「另放 1 個壞檔 → assert 好檔照樣索引到 ＋ `QuestGuide.lastSkippedFiles() == 1`」。
- 證據：D1（v2 §3）＝ `if (m.start() < last) continue;`，佢**唯一作用**就係消除 `getting_started.snbt` 嗰條 IOBE（我 W12 掃 755 slice：**崩點只有 stripQuestIcons 一條路**；QuestGuide.java 全文 **0 個 `throw`**、`itemsInRange` 有 clamp（`:1467-1471`））。所以 **D1＋D2 一齊上之後，嵌套 icon 檔唔會再拋** ⇒ `skippedFiles` 保持 **0** ⇒ `== 1` **必紅**，實作者做唔到綠。
- 另一邊：**現實世界真正會靜默缺檔嘅路徑 v2 冇計**。`Files.readString(p, UTF_8)` 對非法 UTF-8 會拋 **`MalformedInputException`（`isIOException=true`、`isRuntimeException=false`，W14 實測）**，而佢落到 **現有** `catch (IOException ignored) { // skip }`（`:150-152`）——即係**冇 log、冇 counter**。v2 只 catch `RuntimeException` ⇒ D2 自己嘅目標句「⇒『靜默缺檔』永遠可被發現（SK『唔准 fake success』）」**達唔到**。
- 反方主張：呢條係 v2 最硬嘅自相矛盾——用嚟證明 fail-soft 嘅驗收項，正好被 D1 廢掉。**唔可以開工**（做完會卡死在 A8(b)）。

### N2【BLOCKER / HIGH】A8(c) 冇得量：trace schema **完全冇 quest 欄位**，而且案例本身唔問任務
- 證據（W20）：FTB run 19 條 trace 嘅**全部** key：`after, attachCards, before, body, cardsOut, category, content, dumpLevel, emissionRefs, event, filterVariant, hasCards, hasVariant, intent, item, maint, maintCards, outputsSize, placement, primaryOutputId, question, reason, recipe_card_markers, role, rules, sourceItemId, src, tools, ts, turns, upgradeCards` ⇒ **冇 quest / hit / index 任何欄位**。
- 證據：三個 run 共 **48/48** 條 trace 嘅 `send.facts` `content` 都係 **空字串**（W16），事件類型亦只有 `send.* / check.* / model.reply.* / tool.* / render.* / display.body.final`——**冇任何一條記錄索引命中**。
- 證據：autotest 案例係「item 用途／配方」題（e.g. `What is End Portal Frame (minecraft:end_portal_frame) used for in this pack?`），**唔會問任務** ⇒ 就算加咗欄位，「≥1 case quest hits 非空」都可能因為案例設計而永遠空。
- 反方主張：A8(c) 係 v2 補 R1 F10／F1「真檔驗證」嘅**唯一**承諾，但佢**唔可執行**。而且條文冇講要加 trace 事件；真係要加就會踩到 `AskEngine.java`／`AskTrace.java`（**唔在白名單**）→ 直接違反 A7。

### N3【BLOCKER / MED-HIGH】counter API 自我矛盾：A8(b) 要 static accessor，§5 尾註又禁 static；out-param 方案會撞 8 個呼叫點（其中 2 個唔在白名單）
- v2 文字：A8(b)「`QuestGuide.lastSkippedFiles() == 1`（**新增 public static accessor**）」 vs §5 尾註「⚠️ **可數性設計約束**：counter **必須 per-invocation**（例如 `index(...)` 加一個 out-param／回傳小 result 物件），**唔准**用可變 static 欄位——`AskEngine.ask` 可能喺 worker thread 跑…（R2 請裁決用邊種）」。
- 證據：R1 要求嘅裁決我可以直接落判——`AskEngine.ask` **真係喺 worker thread 跑**：`AskService.java:93` 註釋「capture item text on the game thread, then run AskEngine **off-thread**」＋ `CompletableFuture.supplyAsync(...)`（`:314`）→ 共用 ForkJoinPool ⇒ **可變 static counter 有 race，唔可以用**（v2 嘅擔心成立）。
- 證據（簽名／呼叫點，W9）：`index` 有 **3 個 overload**（`:87`／`:94`／`:101`）＋ `indexAndMatch(:83)`；forge 呼叫點 **8 個**，其中 `AskEngine.java:263`、`QuestFetchAskTool.java:48` **兩個檔唔在白名單** ⇒ 若照字面「`index(...)` 加 out-param／改回傳 result 物件」，就一定要改非白名單檔 → **A7 FAIL**；若用裸 static 欄位 → race。
- 反方主張（**裁決**）：**新增一個 5 參數 overload**（`index(gameDir, scanners, lang, filterHidden, int[] skippedOut)` 或 `AtomicInteger`／小 record），**現有 3 個 overload 簽名一字不改**、內部 delegate（`null` ＝唔要 count）；harness 只叫新 overload。呢個係**唯一同時滿足**「per-invocation、無 race、零呼叫點改動、零新 public API 汙染」嘅做法。**刪除 `lastSkippedFiles()` 呢個名字**（佢暗示 static）。

### N4【BLOCKER / MED】A5 條帶建基於「log 行數」，而且 flag、腳本、抽樣三樣都唔支持
- 證據（W16）：`Pack AI cardplace: cards=` 行數：主包 run **316**、E9E run **73**、FTB run **202**；而 `Pack AI cardplace: n=` 行數**同樣係 316／73／202** ⇒ 每次 layout **出兩行**，「316 樣本」＝ **layout 被叫咗 316 次**（每次 body 更新一次），**唔係 316 個案例**。主包 18 條 trace ／20 個 case 分到 **316 行**；同一批行嘅 `adjacentCardPairs` 值分佈 = `{0:262, 1:54}`（即「54 = 316 行入面有 54 行 transient 值 ≥1」）。
- 證據（W18）：`PackAiConfig.java:475` `.define("cardPlacementDiagLog", false)` ⇒ **預設關**；`tests/check_cardplace_instrument.py` 仲要**釘死** false。v2 A5 **冇講要開呢個 flag** ⇒ 照預設重跑，行數 = **0**，條帶根本量唔到。
- 證據（W17）：全 repo 只有 `AiAssistantScreen.java:876-910` 產生呢兩行；**冇任何 .py／工具會計 `adjacentCardPairs`** ⇒ v2 講嘅「真機 + **同一分析腳本**」**唔存在**（`docs/research/artifacts/2026-09-19-cardplace-run/ANALYSIS.txt` 係人手輸出，同目錄 `cardplace_lines.txt` 係 **0 bytes**）。
- 證據（W19）：`tools/cardplace_sampler.py` docstring：「every test run must **randomly re-draw items per class**, so a run never reuses the previous run's items」＋ `draw` 模式係「draw a **FRESH** random sample per category for each test run (seed recorded)」⇒ **重跑唔會同一批 item**，而卡嘅數目／次序係 LLM 產生 ⇒ 分子（54）同分母（316）**兩邊都會郁**。
- 證據：FTB 嗰個 **全失敗**（`model.reply=0`、19/19 條 `Query failed`）嘅 run，**202 行入面有 143 行 `adjacentCardPairs ≥ 1`** ⇒ 呢個指標同「有冇真答案」**冇單調關係**，唔可以當回歸訊號。
- 反方主張：`[48,60] / 316`（±11%）係**單次觀測**、**零重複測量**、**分母定義同真實單位唔對應**。正確做法：單位改成 **per-case 最終 layout**（即 `ANALYSIS.txt` 嗰種聚合：主包 baseline = 18 案例中 **15 個 case 最終 layout 有 ≥1 對相鄰卡**、3 個 0；E9E = 11 案例 0），並：①明文寫「跑前手動開 `cardPlacementDiagLog=true`（client toml；預設 false）」；②明文「用 `cases_main.json`（`seed=978047581`）**唔行重抽**，或者接受案例唔同就唔可以比數」；③**入庫**分析腳本（`tools/` 或 `tests/`）；④容許 LLM 抖動：寫成「case 級相鄰卡 **≤ baseline ＋ 2**／相鄰案例數 ≤ baseline＋2」，或者**降級為資訊性、唔做閘**（真閘留 A1–A3＋A8(a)）。E9E 嘅「樣本數 = 73」同樣要改（73 係行數；E9E `caseCount=16` 但只有 11 條 trace ⇒ 重跑 trace 數一變，行數跟住變）。

### N5【BLOCKER / MED】A7 三個洞：baseline 寫入 repo、status 碼睇唔到「已 dirty 檔」嘅內容改動、HANDOFF.md 未入白名單但專案契約強制改
- 證據：A7 條文寫 `git status --porcelain > baseline`（**相對路徑**）⇒ 喺 repo 根**新建一個 untracked `baseline` 檔**，而佢**唔在白名單** ⇒ 收工 diff 必然見到 `?? baseline` ⇒ **A7 自我觸發 FAIL**（§7.1 寫嘅係 `%TEMP%\baseline-<ts>.txt`，同 A7 條文唔一致）。
- 證據（W3／W6）：baseline 已經有 **72 個 M**，當中 **`code_change_log.md` 已經係 M**（而佢唔在白名單）、`AskReplyScrub.java` 亦已 M。`git status --porcelain` 只比**狀態碼**，唔比內容 ⇒ 呢批檔**可以任意再改而 A7 全綠**。A7 自稱要證「白名單外零改動」，實際上證明唔到。
- 證據：`.hermes/plans/HANDOFF.md` 已 tracked、**現時乾淨**、**唔在白名單**；而 packai `AGENTS.md`「開發流程」第 7 點明文要求「**HANDOFF／`code_change_log.md` 更新**」＋根 `AGENTS.md`「每次 session 完結／開新 session 前，必須先 hand off」⇒ 實作者一遵守契約就會整出 `M .hermes/plans/HANDOFF.md` ⇒ **A7 FAIL**。要麼補白名單，要麼明文寫「本 plan 唔更新 HANDOFF（另開）」——二選一，唔可以兩邊都靜默。
- 反方主張：A7 要 (a) baseline **寫 `%TEMP%`**（同 §7 一致）；(b) baseline 除 `git status --porcelain` 外，**同時記每個 dirty 檔嘅 sha256**（或 `git diff --stat` ＋逐檔 hash），收工**逐檔比 hash**；(c) 白名單補 `.hermes/plans/HANDOFF.md`／`code_change_log.md`，或明文列「唔准改」並承擔同 AGENTS.md 嘅衝突。

### N6【MED】§7 出現**幽靈檔** `QuestIndexFailSoftCheck.java`，而 A2 假設「只加一個 harness」
- 證據：§7.4「還原 = 還原 backup ＋ `rm` 新增檔（`QuestGuideStripIconsCheck.java`／**`QuestIndexFailSoftCheck.java`** ＋若同步過嘅 mirror 還原）」；但 §6 白名單**冇**呢個檔，§5 A8 又寫 fail-soft 係「落 `QuestGuideIdCheck.java`」。
- 證據：`gen_tmp_check.py` 係 `rglob("*Check.java")` **自動收集**（W8）⇒ 若實作者照 D2 表「**新增 fail-soft harness**」真係開一個新檔：A2 會變 **51/51（唔係 50/50）→ FAIL**；A7 亦會見到一個**唔在白名單**嘅 `??` → FAIL。
- 反方主張：明文寫死「**fail-soft 案例放 `QuestGuideIdCheck.java`，唔准開第二個 harness／第二個檔**」，並且 §7.4 刪走 `QuestIndexFailSoftCheck.java`。

### N7【LOW】§7「128 項」同實況差 1
- 證據（W3）：`git status --porcelain | wc -l` = **127**（72 M ＋ 53 ?? ＋ 2 D）。v2 §7 寫「**今晚親核**…**128 項**」。差別來源推斷＝寫 plan 當時 `docs/plans/2026-09-19-p0-questguide-stripicons-crash.md` 本身係 `??`，commit `15bd22c` 之後變 127（呢個係推斷，未證實）。
- 反方主張：改 127 並寫「（commit 後）」，或者索性寫「以動手前實跑為準，唔寫死數字」。

### N8【LOW】`REMAINING_WORK.md` **唔存在** ⇒ §9「已記入 REMAINING_WORK」不可驗
- 證據（W7）：`find . -iname "*REMAINING*"` 空；`git ls-files | grep -i remaining` 空（rc=1）⇒ 本 repo **冇亦冇 track** 呢個檔。packai `AGENTS.md` 只提 `.hermes/plans/HANDOFF.md`（現況）＋ `code_change_log.md`，**冇** REMAINING_WORK（嗰個係 jarvis-pc 嘅慣例）。
- 反方主張：改寫成「記入 `.hermes/plans/HANDOFF.md` 當日 section（一行）」或者「新建 `docs/plans/REMAINING_WORK.md` 並**列入白名單**」；唔可以留一句「已記入」而檔案唔存在（SK「唔准 fake success」）。

### N9【LOW】T2f 期望錯（且違反 v2 自己嘅「逐字」政策）
- 證據（W13，真 Java）：輸入 `icon: "a\"b" item: "minecraft:stone"` → 輸出 **` item: "minecraft:stone"`**（`icon: "a\"b"` 整段被剝走），**current 同 D1 一樣**。v2 寫「不變（escape 正確）」。
- 反方主張：改成逐字 `' item: "minecraft:stone"'`，並保留「escape 掃描正確（`\"` 唔會提早收 string）」做註釋。

### N10【LOW】負控清單漏 `T3c`
- 證據（W13）：`T3c`（`icon: { Icon: "a" }   \n\t `）current **throw**（Range [19, 8) / 25）⇒ 移除 D1 之後佢**同樣會翻紅**，但 v2 §4 負控清單只列「T1a／T1b／T1c／T1d／T1e／T3a、T4、A8 嘅 Java 層」。
- 反方主張：清單補 `T3c`（唔補亦唔會出錯，但「逐條點名」係 v2 自己承諾嘅紀律）。

### N11【LOW】A4 冇寫 tokens 從邊度量、分母寫錯
- 證據（W16）：`status-*.json` 嘅欄位只有 `{packaiAutotest, status, elapsedMs, caseCount, quitWhenDone, world, capped, ok, cases}` ⇒ **冇 token 欄位**；帳本係 `config/packai-usage.json`（`PackAiConfig.java:63`「Ledger: `config/packai-usage.json`」、UTC 日結）⇒ 「tokens 增量」要**跑前跑後各讀一次 instance 內嘅 ledger**，v2 冇指名。
- 證據：FTB baseline `caseCount=20`、**trace 19 條**、`ok=19`、`capped=true`；主包 `20 案例／18 trace`；E9E `16 案例／11 trace` ⇒ 分母 20 唔係「有 trace 嘅 case 數」。
- 反方主張：A4 改成「**全部成功產生 trace 嘅 case：N/N**（baseline FTB = 19；N 唔可以寫死 20），每個 trace ≥1 `model.reply`；`Query failed` 出現次數 **= 0**；token 增量以 `<sandbox>/config/packai-usage.json` 前後差值 > 0 為證」，並明寫「`capped=true` 或 trace 數 < N 即**重新跑**，唔可以直接當 FAIL／PASS」。

### N12【INFO】崩潰訊息數字唔可以當斷言
- 證據（W13）：同一輸入 JDK17 = `StringIndexOutOfBoundsException: start 758, end 710, length 1259`（真 log），JDK21 = `IndexOutOfBoundsException: Range [758, 710) out of bounds for length 1259`。
- 主張：harness 只 assert「拋 RuntimeException（或唔拋）」，,`CRASH(19,8,19)` 類數字留做 plan 註釋。

### N13【INFO】D1 診斷要新 import；D3 表已覆蓋 R1 點名嘅同形站點
- 證據（W11）：`QuestGuide.java` 目前 **0 個** `PackAiMod`／`LOGGER` 引用 ⇒ 加 `PackAiMod.LOGGER.debug` 要加 import（同檔，A7 冇問題）。
- 證據：D3 表現時列 `AskReplyScrub:1433/1420/1227`、`OfficialDisplay:175`、`RecipeEmbed:405/418/560/600/1655/1665` ⇒ 同 R1 嘅 grep 結果一致 ✔（唔阻塞，只係要記住 import 一行）。

---

## Flip conditions（要改成咩，我就翻 go = true）

> 全部係**改 plan 文字／加驗收**，唔使改 D1 設計（D1 我 R1 已實證、v2 今次亦冇改）。估計 20–30 分鐘。

1. **【N1，必須】修 D2 測試列 ＋ A8(b)**：明文寫死「壞檔」嘅**可重現構造**，而唔可以係嵌套 icon（D1 已修）。建議採用我實測可行嘅一種：**counter 同時計 `IOException` 同 `RuntimeException` 兩種跳過**，壞檔 = **寫入非法 UTF-8 byte 嘅 `.snbt`**（已實測 `Files.readString(...,UTF_8)` → `MalformedInputException`，屬 `IOException`），並明寫「呢條同時補返 `:150-152` 舊有靜默路徑」。若堅持只計 `RuntimeException`：就要**指名**一個 D1 之後仍然會拋 `RuntimeException` 嘅輸入，並附你自己嘅實測（`grep -n "throw " QuestGuide.java` = 0、`itemsInRange` 有 clamp ⇒ 現時我搵唔到，唔准靠估）。斷言維持「好檔照樣索引到 ＋ skipped == 1」。
2. **【N2，必須】修 A8(c)**：二選一（建議 (i)）：<br>**(i)** 刪走「trace quest hits 非空」，改成 **headless 真檔斷言**：「`QuestGuide.index(<sandbox_ftb>/minecraft, List.of("ftbquests"), null, false)`（**唯讀**）→ assert 命中 `4EFD411CA5975754`（items 含 `ars_nouveau:annotated_codex`）同 `4697678CA1F15CD6`」。**呢條我今日已獨立驗到仍成立**（沙盒 755 slice、2 崩、1 檔，W12），而且佢係**唯一**真正覆蓋 R1 F1「兩個真任務唔好靜默消失」嘅斷言。<br>**(ii)** 或者明文寫死要加邊個 trace 事件／欄位（例：`packai.index.skippedQuestFiles`），並**同時**把 `AskEngine.java`／`AskTrace.java` 加入白名單，同埋明講「要加一個**會問任務**嘅 autotest case」（現有案例唔會產生 quest hits）。
3. **【N3，必須】寫死 counter 機制（R2 裁決）**：刪 `QuestGuide.lastSkippedFiles()`；改為「新增 **5 參數 overload** `index(gameDir, scanners, lang, filterHidden, int[] skippedOut)`（或 `AtomicInteger`），**現有 `:87`／`:94`／`:101` 三個 overload 簽名一字不改**，內部 delegate、傳 `null` 即唔要 count；harness 只叫新 overload」；並明寫「**唔准**用可變 static 欄位（`AskEngine.ask` 喺 `CompletableFuture` worker 跑，`AskService.java:93/314`）、**唔准**改 `AskEngine.java:263` 或 `QuestFetchAskTool.java:48`（唔在白名單）」。
4. **【N4／N11，必須】重寫 A4／A5 數值**：<br>A4 → 「**全部成功產生 trace 嘅 case：N/N**（baseline FTB = **19**；`caseCount=20` 但 `ok=19`、`capped=true` ⇒ 分母寫死 20 係錯），每個 trace ≥1 `model.reply`；`Query failed` = **0 次**；token 增量 = `<sandbox>/config/packai-usage.json`（`PackAiConfig.java:63`）前後差值 > 0；`status.ok`／`cardsOut` 唔算證據」。<br>A5 → 「**單位改成 per-case 最終 layout**（唔用 log 行數）：用 `tools/cardplace_sampler.py` 記錄嘅 `cases_main.json`（`seed=978047581`）**同一個案清單**跑，**唔行重抽**；跑前手動設 `cardPlacementDiagLog=true`（`PackAiConfig.java:475` 預設 false，`tests/check_cardplace_instrument.py` 釘住）；分析腳本**入庫**（`tools/` 下），輸出 per-case `cards / adjacentCardPairs / final seq`；判準「case 級相鄰卡數 **≤ baseline（主包 15/18 case 有相鄰、合計 54 行；E9E 0/11）＋ 2**」，或者**降級為資訊性、唔做閘**。原始行數（316／73）只准做附錄，唔准入判準」。
5. **【N5，必須】修 A7 ＋ 白名單**：(a) baseline 明確寫 `%TEMP%\baseline-<ts>.txt`（刪走相對路徑 `baseline`）；(b) baseline **同時記每個 dirty 檔嘅 sha256**，收工**逐檔比 hash**（否則 72 個已 M 檔嘅內容改動睇唔到）；(c) 白名單二選一併寫落 §6：「補 `.hermes/plans/HANDOFF.md` 同 `code_change_log.md`」，**或**明文寫「今次唔改 HANDOFF／code_change_log（同 AGENTS.md 第 7 點嘅例外，由 SK 批）」。
6. **【N9，必須】改 T2f 期望**：`icon: "a\"b" item: "minecraft:stone"` → 期望逐字 `' item: "minecraft:stone"'`（現寫「不變」係錯）。
7. **【N6／N7／N8／N10，應該】一次過掃**：§7.4 刪 `QuestIndexFailSoftCheck.java` 並明文「fail-soft 案例只放 `QuestGuideIdCheck.java`，唔准開第二個 harness（否則 A2 變 51）」；§7「128 項」改 127（或寫「以動手前實跑為準」）；`REMAINING_WORK.md` 改成真實存在嘅路徑（`.hermes/plans/HANDOFF.md` 或新建檔＋入白名單）；§4 負控清單補 `T3c`；§4 T 表註明「`CRASH(x,y,z)` 係 JDK17 訊息、唔可以入 assert」。

---

## 我認為正確、唔會改（正方立場）

1. **根因＋D1 完全不變、而且我今次用真 Java 再確認一次**：嵌套 `icon:{…Icon:…}` → `last` 越過下一個 match → `out.append(text, last, m.start())` 拋 IOBE（`Probe.java`：`Range [19, 8) / length 19` 等 6 條）。**D1 之後 405 條隨機字串 0 個 throw**（W13/W15）。沙盒真檔亦獨立重現 **2/755 slice、1 檔**（W12）。
2. **A8 嘅「內容斷言」思路係對嘅，而且 (a) 真係有判別力**：我驗到只加 D2 時該 fixture 檔會被跳過 ⇒ quest id 唔入 index ⇒ 斷言紅；D1＋D2 才綠。呢個係 R1 最想要嘅防假綠機制，方向唔會改（只係 (b)／(c) 兩臂要修）。
3. **§7 還原方案嘅新方向 100% 正確**：`%TEMP%` baseline、逐檔備份、禁裸 `git checkout --`（已有 M／untracked）——R1 三點全部答到；`QuestGuide.java` 乾淨所以仲可以用 `git checkout --` 亦係事實（W4）。
4. **A3 嘅 baseline 講法我親自跑過，完全正確**：123 檔 = 122 rc=0 ＋ 1 rc=2（`check_ask_display_leak.py`），同 `%TEMP%\gate_baseline_20260919.txt` **逐行一致**（W1/W2）。呢條係 v2 最紮實嘅一條。
5. **A2 嘅 harness 契約／任務名／重生流程全部對得上 generator 實作**（W8）——`runQuestGuideStripIconsCheck`、`-ea`、49→50、`QuestGuideIdCheck` 已註冊 ⇒ 落地可行。
6. **§3 §4 嘅語意收窄、T3a、逐字表、負控點名**：除 T2f 一格，18 格逐字值我**用真 Java 全部核對正確**（呢個係 v2 最大進步）。
7. **D3 改成 repo-wide grep ＋ 逐條 verdict ＋ 完成定義**：同我 R1 F12 嘅要求一致，而且佢列出嘅站點同我 grep 結果吻合。
8. **版本推論方向已改正**（「同一支 1902.5.10-build.497、內容相關」）＋ **port 保真度註明**（NBSP／UTF-16）——我實測 NBSP 分歧成立（W13）。
9. **Python mirror「同步」係可行嘅**（唔係口號）：mirror＋守衛 對 Java(guarded) 喺 403/405 條 ASCII 輸入**完全一致**（W15）。我唯一要求係**唔可以**宣稱佢「偵測漂移」（冇交叉比對），同**唔准**放 NBSP 案例入 mirror（會因 Python `isspace()` 而紅——唔關 Java 事）。
10. **邊界（唔准 hot-copy jar、唔准部署真 instance、沙盒 only、唔准 commit、`status.ok` 唔算證據）寫得清楚**，我唔會改。

---

## NOT RUN（我冇做／做唔到，唔會當已驗）

- **冇跑** `./gradlew.bat compileJava compileTestJava`、**冇跑**任何 Java harness（`runQuestGuideIdCheck`／`runQuestGuideStripIconsCheck` 都**未存在**）⇒ **A1／A2／A6 未驗**；我亦**冇**辦法確認「現時 49/49 harness 全綠」（呢個係 v2 A2 嘅前提，**未經任何人驗證**）。
- **冇**改任何 repo 檔、**冇** build、**冇** commit、**冇**部署、**冇**開遊戲、**冇**動沙盒（沙盒只**唯讀**掃檔，W12）。
- **冇**跑真機（A4／A5 未驗）；`(758,710,1259)` 簽名**仍然 NOT REPRODUCED**（我今次掃沙盒只出 1214／780 兩個 slice，同 R1 一樣；v2 §1 已老實標「未證實」，我同意保留該標示）。
- **冇**驗 `AskReplyScrub`／`OfficialDisplay` 嘅反編譯 jar（D3／白名單 #6 嘅先決條件）；`OfficialDisplay.java` 係 untracked，我只讀過工作樹版本。
- **冇**驗 `neoforge` 樹（PAUSED）；`tests/check_dual_tree_*` 喺 pause 模式（`neoforge/README_PAUSED.md` 存在）下 forge-only 改動會被降為 WARN ⇒ 我**推斷** D1 唔會撞雙樹閘，但**未實跑**。
- **冇**跑 `research/gen_tmp_check.py`（會**寫** `tmp-check.gradle` ⇒ 超出「只讀」授權）；A2 嘅「49→50」係由讀 generator 原始碼推得，**未實跑**。
- **A5 嘅「同一分析腳本」冇得驗**（唔存在）；我只重算咗行數同值分佈。

---

> 方法附註：所有 Java 語意結論來自 `%LOCALAPPDATA%\Temp\r2_java_probe\{Probe.java,Probe2.java}`（逐字拷貝 `stripQuestIcons`、JBR 21 `javac/java`）；沙盒掃描用我自寫 Python port（`topLevelObjects` ＋ `stripQuestIcons` ＋「`last > m.start()`＝拋」判別）。所有中間輸出檔（`cases.txt`／`java_out.txt`／`r2_gate_check_215915.txt`）都喺 `%TEMP%`，可重跑。
