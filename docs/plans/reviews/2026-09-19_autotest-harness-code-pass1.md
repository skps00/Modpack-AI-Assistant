# Pass 1（即時質量／重構）Review — packai autotest harness（Plan B）

- 日期：2026-09-19｜版本界線：**MC 1.19.2 + Forge 43.4.0**（只審 `forge/1.19.2`；neoforge 樹 PAUSED，不審）。
- 白名單：`forge/1.19.2/build.gradle`、`src/autotest/resources/packai-autotest.flag`、`client/autotest/AutoTestHarness.java`(570 行)、`client/ClientSetup.java`、`tests/check_autotest_flag.py`。
- 相關 caller 已讀：`AiAssistantScreen.java:108-128`、`AskService.java:503-530`（`display.body.final` 唯一寫入點）、`RecipeCard`(`outputs()`/`FocusRole.OUTPUT` 存在)、`JeiRecipeCards.forItem(ItemStack,int,int)`、`ChatSession.isBusy`。
- 證據基準（本輪）：`python tests/check_autotest_flag.py` → `check_autotest_flag: OK`（RC=0）；`grep -rn AutoTestHarness`（源碼只有 harness 自身 ＋ `ClientSetup.java:7,131`）；`find` 見 `forge/1.19.2/build/resources/main/packai-autotest.flag` 仍在（13:14，flag build 產物）；driver ＝ `%LOCALAPPDATA%\Temp\packai_autotest_run.py`（227 行，**非版控**）。
- 已核實／推斷已分開：標「**未驗證**」者為推斷，其餘每條有 citation。
- **非問題（已核，避免誤報）**：`ponytail:` 前綴係 repo 既有慣例（`code_change_log.md:1098`）；唯一 log 行純 ASCII ✔；harness 自身寫入確在 `<gameDir>/packai/autotest/` ✔；`AutoTestHarness.java:322-323` 講「冇 `forItem(String)`」屬實 ✔；`MAX_OUTPUT_CARDS` 註釋與 `forItem(bare,12,0)` 一致 ✔。

---

## H1【HIGH】`OK` ＝「見到一個新 `display.body.final`」，同 case 冇綁定 → 假綠
- 位置：`AutoTestHarness.java:294,433-456`；寫入端 `AskService.java:503-512`。
- 現狀引文：`if (dir != null && sawFinal(dir, caseStartTs)) { results.add(new CaseResult(spec.id(), "OK", ...)) }`；判定＝`text.indexOf("display.body.final", from)` ＋ `eventNewer(...)`（`!ts.isBefore(since)`）。
- 為何係假綠：`finishAskTrace` 對**任何非 null `shown`** 都寫此 event —— 包括 error／miss 分支（`AskService.java:335-336`、`343-345`）⇒ LLM 掛／未收錄（`cardsOut=0`）一樣算 `OK`；亦冇核對 `item`。同一 function 仲寫 `render.cards.final`（`:521-522` 帶 `cardsOut` 同 `item`）。
- 最小 diff：`sawFinal` 改為同時要求同檔有 `render.cards.final` 且 `item == spec.item()`，並把 `cardsOut` 寫入 status；唔想改語意就將字面 `OK` 改 `FINAL`。
- 唔改又會點：`status-*.json` 嘅 `ok` 長期高估；今次靠 VERDICT 另讀 trace 才知真卡數，若日後 status 成唯一訊號，miss 會當 PASS 發佈。

## H2【HIGH】12／13 個 `catch` 零 log；exception 從未入 log（無 stack）
- 位置：`AutoTestHarness.java:85,123,182,223,332,345,392,401,437,479,492,499`（全部 `catch`）＋唯一 log 行 `:495`。
- 現狀引文：`:105-107 catch (Throwable t) { failReason = t.getClass().getSimpleName(); finish(...,"ABORT"); }`（只留 class 名）；`:492 catch (Throwable ignored) {}`（`writeStatus` 失敗即永久靜默）；`:123 catch (IOException e) { return; }`（cases.json 讀唔到就永遠唔開跑，零痕跡）。
- 最小 diff：全 harness 加一次 `PackAiMod.LOGGER.warn("packai autotest fail at {}", phase, t)`（或每 catch 各一行）；`failReason` 由 class 名改成 `class+message`。
- 唔改又會點：一個「設計嚟做診斷」嘅 harness 一失敗就變黑盒 —— driver 只會印 `TIMEOUT: no status json`（driver:200），再查就要靠猜；同專案「唔准 fake success」契約相衝。

## H3【HIGH】靜態閘有具體漏網（列得出、可補）
- 位置：`tests/check_autotest_flag.py:22,27-30,61-65,76-83,106-116`。
- 現狀／漏網：
  1. **只驗 flag 唔在 `src/autotest`**（`:68-73`）；有人把 `packai-autotest.flag` 複製一份入 `src/main/resources/`（「點解冇效」最常見的錯）→ 閘照綠，但出廠 build 永遠係 autotest 版。閘**冇任何**檢查同名 flag 檔在其他 resources 根。
  2. `hasProperty('packaiAutotest')` 對 **gradle.properties** 亦為真 → 一行 `packaiAutotest=` 就可令官方 build 帶 flag，閘仍然綠（現時 `gradle.properties` 確無此 key ✔）。
  3. `WRITE_RE`（`:27-30`）只認 `Files.*` 同一 statement 內嘅字面路徑：`Files.delete/deleteIfExists`、`PrintWriter`、`newBufferedWriter(new FileWriter(..))` 走漏；`resolve("autotest").resolve("../../foo")` 之類 `..` 穿越亦過關（`:111-114` 只查 substring）。
  4. 反向：抽出 `autotestDir(mc)` helper 後 `Files.writeString(dir.resolve(name), ...)` 即刻 **fail**（`:111`）—— 閘會阻擋本報告第 L1 條嘅正當重構；`:109 "harness has no file write"` 亦然。
  5. `:115-116` 全檔掃 `FileWriter/FileOutputStream`（連註釋／字串都算）＝ false positive 來源。
  6. `:81` 死鎖 `tick()` 第一句字面；`:96-99` 只要求 `AutoTestHarness.tick()` 出現在 ClientSetup —— 把 call 移到 `event.phase != END` 之前（`ClientSetup.java:127-131`）會令 harness **每 tick 跑兩次**，閘照綠。亦冇驗 flag resource 名要等於 `active()` 讀嘅 `/packai-autotest.flag`。
- 最小 diff：加 3 條 —（a）`rglob` 掃兩個 resources 根，同名 flag 只准一處；（b）讀 `gradle.properties`，assert 無 `packaiAutotest`；（c）`ticks = src.count("AutoTestHarness.tick(")` ＋ assert 該行在 `phase != END` 之後。WRITE_RE 加 `Files.delete|deleteIfExists`，並改為「同一 method 內」判定而非同一 statement。
- 唔改又會點：閘嘅賣點係「阻止未來回歸」，但上述任何一條都令 flag 靜靜地進入正式 jar／令 harness 雙跑，而 CI 仍報綠。

## M1【MED】`cases.json` 冇「已消費」標記：殘留檔會自動 load 世界＋自動關 game
- 位置：`AutoTestHarness.java:111-143`（每 tick 見到檔就開跑）、`:496-499`（`mc.stop()`）。
- 現狀引文：`if (!Files.isRegularFile(file)) return; ... if (!first.startsWith(MAGIC)) return;`（只認魔術前綴，無 run id、跑完唔刪／唔改名）；清理靠 driver `finally`（driver:219-223），而 driver 一 crash／被 taskkill 就唔會執行（今次已有 4 次 backup runner 痕跡）。
- 最小 diff：`parse()` 成功後即 `Files.move(file, file.resolveSibling("cases.done.json"))`（仍在允許目錄內），或要求 `cases.json` 帶 `"runId"` 並寫入 status 供 driver 對帳。
- 唔改又會點：任何用 flag build 嘅 instance 一旦有殘留檔，下次開 game 就會自己入世界、跑 ask、燒 token，然後 `quitWhenDone` 自動關 game —— 玩家（SK）無法預期，且閘完全捉唔到（`:68-116` 唔讀 cases.json）。

## M2【MED】技術宣告「只寫 `<gameDir>/packai/autotest/`」比實況窄
- 位置：`AutoTestHarness.java:37-38`（class javadoc）；`AskService.java:576-577`；`DailyTokenUsage.java:23-27`；`AutoTestHarness.java:215-217`。
- 現狀引文：javadoc "Writes only `<gameDir>/packai/autotest/`"；但同一 run 經正常 Ask 路徑會寫 `<gameDir>/packai/trace/ask-*.jsonl`＋`index.jsonl`、`<gameDir>/config/packai-usage.json`，而 `pressFirst(backup,false)` 係**盲按 `BackupConfirmScreen` 第一粒 Button** —— plan 自己已記「第一粒 Button 會觸發真世界備份寫入」（`docs/plans/2026-09-19-in-game-autotest-harness.md:124`、`reviews/…R3-opposing.md:70`），即會寫到 `packai/autotest/` 以外。
- 最小 diff：javadoc 改成「harness **自身**只寫 …；經玩家入口間接寫 trace／usage；Backup 對話框會觸發 vanilla 世界備份」＋在 press 前後各加一行 log（label）。
- 唔改又會點：未來有人用「只寫 sandbox 目錄」做安全論據（例如直接跑 live instance）會踩空；Backup 備份係隱形副作用，無 log 就無法事後查證。

## M3【MED】`active()` 每 tick 開一次 jar resource（20/s，跑完仍然繼續）
- 位置：`AutoTestHarness.java:79`（`getResourceAsStream("/packai-autotest.flag")`）、`:92`（`if (!active() || finished) return;`）。
- 最小 diff：`private static final boolean ACTIVE = readFlag();` 然後 `tick()` 第一句改 `if (!ACTIVE || finished) return;`（同時要改 `check_autotest_flag.py:81` 嘅 `expect` 字面，否則閘會紅）。**注意**：唔可以只加 cache 而唔改閘。
- 唔改又會點：flag build 玩家每個 tick 一次 classloader 資源查詢＋stream 建立（渲染執行緒），係長期無謂成本；亦令人以為 harness 在跑。

## M4【MED】IDLE 期間每 tick 讀整份 `cases.json`（20 Hz full read）
- 位置：`AutoTestHarness.java:111-133`（`idle()` 由 `tick()` 每 tick 呼叫）。
- 現狀引文：`if (!Files.isRegularFile(file)) return; ... raw = Files.readString(file, UTF_8); ... if (!first.startsWith(MAGIC)) return;`。
- 最小 diff：加 `idlePoll` 計數，每 20 tick 才 `isRegularFile`；或只讀首行（`BufferedReader.readLine()`）判魔術前綴，成功才 `readString` 全文。
- 唔改又會點：有殘留／長期 cases.json 時（配合 M1）＝永久 20 Hz 讀檔；即使無檔亦係每 tick 一次 stat。

## M5【MED】`startCase` 無法得知「真係問咗」→ 白等 180 秒
- 位置：`AutoTestHarness.java:287-288`；`AiAssistantScreen.java:114-125`（`openAndAskAbout` 有多個靜默 early-return）。
- 現狀引文：`AiAssistantScreen.openAndAskAbout(stack); sub = Sub.POLL;`（無回傳值、無事後檢查）；`openAndAskAbout` 內 `if (!(mc.screen instanceof AiAssistantScreen created)) return;`／`if (ChatSession.isBusy()) return;`。
- 最小 diff：呼叫後即驗 `mc.screen instanceof AiAssistantScreen && ChatSession.isBusy()`，否則 `results.add(new CaseResult(spec.id(), "NO_ASK", ...)); caseIndex++; return;`。
- 唔改又會點：任何入口被上游改動／screen 被其他 mod 佔用，個 case 就靜靜等足 180s，status 只寫 `TIMEOUT`（無原因），A3 紅會被誤診為 LLM 慢。

## M6【MED】trace 格式知識重複三份，其中兩份唔入版控
- 位置：`AutoTestHarness.java:413-482`（substring 掃 `display.body.final`＋手動抽 `ts`）；driver `packai_autotest_run.py:34,120-140`（MAGIC 硬寫，註釋指 `AutoTestHarness`）；判卡腳本（`%TEMP%\judge_autotest.py`／`verify_autotest.py`）。
- 最小 diff：status JSON 每 case 加 `traceFile` ＋ `cardsOut`（Java 端已識搵 `render.cards.final`，只差讀 `cardsOut`），driver 只需信 status；並把 driver 搬入 repo（例如 `tests/` 或 `tools/`）令契約可被閘驗。
- 唔改又會點：`AskTrace` schema／`display.body.final` 一改名，Java 端會靜默 TIMEOUT、driver 另邊又判 FAIL，而 `%TEMP%` 嘅 driver 重開機就可能蒸發 —— 到時只剩「今次跑過」嘅記憶。

## M7【MED】`eventNewer` 用字串手術抽 `ts`（非 JSON parse）
- 位置：`AutoTestHarness.java:459-482`（`line.indexOf("\"ts\"")` → `:` → 兩個 `"` → `LocalDateTime.parse`）。
- 最小 diff：改用已 import 嘅 Gson：`JsonParser.parseString(line).getAsJsonObject().get("ts").getAsString()`，外層 try/catch（`:479` 已有）。
- 唔改又會點：event 加 key、改 key 次序、或 `body` 內文含 `"ts"` 字面就會抽錯／抽唔到 → 情況同 M6（靜默 TIMEOUT）。

## L1【LOW】重複碼／可抽嘅函數
- `AutoTestHarness.java:116,541,543` 三處重建 `dir.resolve("packai").resolve("autotest")`；`:196-200` 與 `:249-258` 兩段 budget 早退幾乎相同；`:197-198`／`:255-256` 令 `status` 與 `reason` 同值重複。
- 最小 diff：加 `autotestDir(mc)`（連查 null）＋ `abortBudget(mc)`；`writeStatus` 一次過 `Path out = autotestDir(mc)`。
- 唔改又會點：改 sandbox 目錄只需改 3 處，漏一處＝寫錯位（而閘只認字面，更難察覺）。

## L2【LOW】死碼與單位／命名不一致
- `:102-103 case DONE -> {}`（`finish()` 已設 `finished=true`，`:92` 先 return ⇒ 永不執行）、`:263-265 default -> {}`（3 個 enum 值已盡列）。
- 單位混用：`:41 WORLD_TIMEOUT_TICKS = 6000`（tick，無「＝5 分鐘」註釋）vs 其餘 `_MS`；狀態大小寫不一：`:207 failReason = "world_timeout"` vs `status = "WORLD_TIMEOUT"`；log 前綴 `":495 "packai autotest ..."` 與 repo 慣例 `"Pack AI ..."`（`ClientSetup.java:87,90`、`AskService.java:471`）唔一致。
- 最小 diff：刪兩處空 case；`WORLD_TIMEOUT_TICKS` 改成 `WORLD_TIMEOUT_MS = 300_000L` 或補 `// 6000 ticks = 5 min`；`reason` 統一用 `status` 同一個字串；log 前綴跟 `Pack AI` 慣例（仍全 ASCII）。
- 唔改又會點：下一個 driver 寫 case-sensitive 比對時會踩到大小寫；tick 制超時在沙盒 lag／TPS 低時實際遠超 5 分鐘。

## L3【LOW】註釋與碼／文件漂移
- `:321-324` javadoc 內容係「API 實況查核」痕跡（「No `forItem(String)`」）—— 事實正確，但屬 review 產物，唔係 API 文件；`plan:34` 寫 `cases.json` 有 `{id, item, nbt?, question?}`，但 `AutoTestHarness.java:165-167` 只讀 `id`／`item`，driver `CASES_SPEC`（driver:27-32）亦從不寫 nbt／question ⇒ 兩個欄位係**靜默 no-op**（plan 自身 R2 review 已建議刪 `question?`）。
- 最小 diff：javadoc 收成一句「樣本由 JEI OUTPUT 卡取、優先帶 NBT」；plan §2.2 改成 `{id, item}` 或把 `nbt`／`question` 標「未實作（v1 不收）」。
- 唔改又會點：下一個寫 driver 嘅人照 plan 填 `question`，會得到「問題冇改、答案照舊」而零錯誤訊息。

## L4【LOW】`pressFirst(..., boolean)` 盲按 ＋ 兩個 counter 同一邏輯
- 位置：`AutoTestHarness.java:211-246`（`pressFirst(confirm,true)`／`pressFirst(backup,false)`；差別只係 `confirmPresses` vs `backupPresses` 兩個 counter）。
- 最小 diff：合併為 `pressFirst(Screen)`＋單一 `dialogPresses` counter（上限共用），並 log 被按 Button 嘅 label。
- 唔改又會點：兩個 counter 令同一上限語意重複；「按第一粒」對 `BackupConfirmScreen` 嘅安全假設**未驗證**（今次 run 未彈過該框，見 VERDICT），一旦順序反了會按到取消／備份，而 log 冇任何痕跡。

---

## 5 行總結（最重 3 項）
1. **H1**：`OK` 只代表「見到新 `display.body.final`」，error／miss 一樣算 —— 假綠風險最高，建議同時要求同檔 `render.cards.final` 且 `item` 對得上，並把 `cardsOut` 寫入 status。
2. **H2**：13 個 `catch` 有 12 個零 log、exception 從未入 log ⇒ 一個診斷工具失敗時零證據；每個 catch 至少 `LOGGER.warn(..., t)`，`failReason` 帶 message。
3. **H3**：閘有 6 個具體漏網（flag 可放 `src/main/resources`、`gradle.properties` 加 key、`Files.delete` 不在 WRITE_RE、`..` 穿越、tick 雙跑、旗標名唔對）＋ 反而阻擋正當 DRY 重構；先補 3 條最貴（resources 全掃／properties 檢查／call 位置）。
4. 其餘 MED：cases.json 無「已消費」標記（殘留即自動入世界＋關 game）、javadoc 寫入界線比實況窄（trace／usage／world backup）、`active()` 每 tick 開資源、IDLE 每 tick 讀整份 cases.json、`openAndAskAbout` 靜默 no-op 仍等 180s、trace 格式知識重複三份（兩份在 `%TEMP%`）、`ts` 用字串手術抽。
5. 基線有據：本輪 `tests/check_autotest_flag.py` RC=0；`grep` 證 harness 只被 `ClientSetup.java:7,131` 引用；harness 自身寫入確在 `<gameDir>/packai/autotest/`；`ponytail:` 註釋與 ASCII log 合規，非缺陷。以上 H／M 各條最小 diff 均可獨立落，唔需改架構。
