# 2026-09-19 — autotest harness **Pass 3（第二輪驗證式 review：E1–E9 修正是否真解決）**

- 範圍：**MC 1.19.2 + Forge 43.4.0**（`forge/1.19.2`）；`neoforge` PAUSED，不審。只審 3 個改動檔 ＋ 其 caller／部署鏈。
- 紀律：**只讀**（唯一寫入＝本報告）。每項附 `檔案:行`＋引文；未核實明文標示。
- 本輪**親跑**（唔係採納 Hermes 講法）：
  `python tests/check_autotest_flag.py` → `OK` RC=0；`for f in tests/check_*.py` → **122 個、1 個非零**（`check_ask_display_leak.py` rc=2，＝缺真機 log skip）＝與 Hermes 報嘅 121+1 完全吻合。
  jar 內容：`zipfile` 開 `build/libs/packai-0.2.3.jar`（15:18:48，342 entries）→ **無 `*.flag`**；`%LOCALAPPDATA%\Temp\packai_flagged2.jar`（15:18，sha `f551dd94…`）→ **有 `packai-autotest.flag`**。
  真機 artifact：`%LOCALAPPDATA%\Temp\autotest_results_20260919-151904\`（status + 3 份 trace + latest.log）。
  bytecode：`javap -p -c` 反編譯 1.19.2 `client.jar` 嘅 `ekd`(BackupConfirmScreen)／`elm`(Screen)；`assets/minecraft/lang/en_us.json`。
  靜態閘負控：**in-memory**（import 模組、monkeypatch／餵 synthetic source，唔改 repo 檔）11 項。
- **未重跑**（跑 gradle 會寫 `build/`，違只讀）：`compileJava`、49 個 Java 測試 → 採納 Hermes RC=0；側證＝15:18:48 產物存在且含 `AutoTestHarness.class`。

---

## 逐條判定

| # | 宣稱 | 判定 | 關鍵證據 |
|---|---|---|---|
| E1 | 判定綁同一 trace（要 `render.cards.final.item` 對得上）＋記 `cardsOut` | **真解決（主假綠封死）＋1 個脆弱 proxy** | 見下 E1 |
| E2 | 全部 catch 加 `LOGGER.warn(...,t)` | **真解決** | 17/17 catch 全部 `warn(...,t)` |
| E3 | cases.json 讀完改名 `.consumed-<ts>` | **真解決（1 條殘路）** | 沙盒實物 `cases.json.consumed-20260919-152004` |
| E4 | flag build jar 改名 `packai-0.2.3-autotest.jar` | **真解決（檔名部分未直接核實）** | 見下 E4 |
| E5 | `active()` 快取 | **真解決** | `:73,83-99` |
| E6 | cases.json 只讀一次 | **真解決（引入 1 個新洞 N3）** | `:75,123-124,142` |
| E7 | 開畫面要驗，否則 `NO_SCREEN` | **表面（busy 早退照樣繞過）** | 見下 E7 |
| E8 | `BackupConfirmScreen` 唔准按會備份嘅掣 | **表面（backup 真封；generic `ConfirmScreen` 未封＝CRITICAL 殘留）** | 見下 E8 |
| E9 | 靜態閘補 6 洞 | **部分**（3 真封、2 部分、1 未封；另引入 2 個閘洞） | 見下 E9 |

**E1 — 真解決。** `sawFinal()` 已換成 `judge()`→`scanTraces()`→`scanFile()`：要求**同一檔**同時有 `display.body.final` 及 `render.cards.final`，後者要 `itemId.equals(text(o,"item"))`（`:600-605`），並記 `cardsOut`（`:604`）。`newer()` 已改用 Gson（`:610-621`，修 pass1 M7），比較為嚴格 `ts.isAfter(since)`（`:616`）。
真機證據：`status-20260919-152129.json` 每 case 綁實 `traceFile` ＋ `cardsOut`（2／1／5），`NEG_bedrock` → `NO_SAMPLE`；trace 內 `{"event":"render.cards.final","ts":"2026-09-19T15:21:05.2927102","cardsOut":2,"item":"tetra:modular_double",…}`。
error／miss 假綠已封：`AskService.java:334,344` 用 `AskResult.text(...)` → `AskResult.java:47-48 fromRaw(answer,List.of(),List.of())` ⇒ `cardsOut=0`、`item=""`（`:521-523`）⇒ 判 **NO_CARDS**，唔會 OK。✔
**脆弱 proxy（MED，N8）**：`item` 係 **第一張卡嘅 `sourceItemId`**（`AskService.java:523`），而且 `RecipeCard.java:64` 會 `toLowerCase()`；唔係「被問嘅 item」。第一張卡係 guide／quest／`role=input` 卡時 → 永遠 NO_CARDS＝**假紅**（pass2 §1.4 已預告 `role=input` 情況；今輪真機 3 條都係 `role:"output"` 才綠）。

**E2 — 真解決。** `:94,115,137,154,205,247,450,464,520,524,548,573,592,617,629,652,659` 共 17 個 catch **全部** `PackAiMod.LOGGER.warn(..., t)`；`failReason` 改 `errorText(t)`＝`class + ": " + message`（`:635-642`）。pass1 H2 全消。

**E3 — 真解決。** `:151-156`：`parse()` 成功後 `Files.move(file, file.resolveSibling("cases.json.consumed-"+stamp))`，失敗只 warn 而**繼續跑**（唔會卡死）。真機沙盒 `/packai/autotest/` 只剩 `cases.json.consumed-20260919-152004` ＋ status → **冇 cases.json 剩**，pass2 §3.4「下次開 game 自動重跑」嘅**重複**部分已封。
殘路（MED，N4）：rename 一失敗 → 檔仍在、而 `casesRead` 已 true（`:142`）⇒ 本 session 唔再讀，但**下一個 JVM 會整批重跑（重複付費 ask）**，只有一行 warn、冇 fallback。

**E4 — 真解決（主要性質）。** `build.gradle:113-116`：`if (project.hasProperty('packaiAutotest')) { sourceSets.main.resources.srcDir 'src/autotest/resources'; jar.archiveClassifier.set('autotest') }`。關鍵對照：13:14 嘅 `packai-0.2.3.jar` sha `addb2784…` **含 flag**（＝pass2 §3.3 事故），15:18:48 同名 jar **已無 flag**；flag 版另存 `%TEMP%\packai_flagged2.jar`（sha `f551dd94…`）。
**未核實**：檔名 `packai-0.2.3-autotest.jar` 本身（build/libs 只剩 plain jar、find 全機無 `-autotest` jar）＋`finalizedBy 'reobfJar'`（`:81`）會否保留 classifier（跑 build 才可證，本輪只讀）。

**E5 — 真解決。** `activeCache`（`:73,83-99`），`active()` 只讀一次 classpath resource；一個 build-time 資源唔會 runtime 變 → **無「改狀態唔再讀」問題**。冇 cache 而唔改閘（pass1 M3 警告）呢點都做咗。

**E6 — 真解決，但新洞。** `casesRead`（`:75`）＋ `idle()` 一見到 file 就 `return`（`:123-124`）。20 Hz full read／stat 已消（pass1 M4）。**新洞 N3（MED）**：`:142 casesRead = true;` 寫喺 `:145` 魔術前綴檢查**之前** ⇒ 一個 malformed／寫一半／非本 harness 嘅 `cases.json` 會**永久停用本 session 嘅 harness**（log 只一行）；修法＝驗證通過才 set。另：`finished`（`:55`）＋ `casesRead` 都係終態 ⇒ 同 JVM 永遠只跑一批（pass2 §1.6 未變）。

**E7 — 表面。** 加咗 `armScreenCheck`（`:77,388`）→ 下一 tick 驗 `mc.screen instanceof AiAssistantScreen`，否則 `NO_SCREEN`（`:394-401`）：`mc.setScreen()` 在 1.19.2 係同步，所以呢個檢查對「screen 開唔到」有效。**但**`AiAssistantScreen.openAndAskAbout()` 係**先 `mc.setScreen(new AiAssistantScreen())` 再** `if (ChatSession.isBusy()) return;`（`AiAssistantScreen.java:108-126`）⇒ busy early-return 照樣已經開咗畫面 ⇒ screen 檢查**過關**，然後白等 180s 報 `TIMEOUT`。pass1 M5 嘅「靜默 no-op 仍等 180s」只封一半。配 pass2 §2.1（START 無 per-case timeout）＝**未解 HIGH**。

**E8 — 表面（本輪 bytecode 親核）。** `ekd`(BackupConfirmScreen) `extends elm`(Screen)；`ekd.b()`(init) 依序加掣：`selectWorld.backupJoinConfirmButton`（offset 73）→ `backupJoinSkipButton`（offset 119）→ 第三粒用 static field（`CommonComponents.GUI_CANCEL`）＋ `ehr`(`Checkbox`,「Erase cached data」)。即 **backup 掣永遠唔係最尾**，而 `isSkipOrCancel("I know what I'm doing!")` 命中 → `pressSkipBackup` 按 skip（`:331-344`）⇒ **寫 `<gameDir>/backups/*.zip` 嘅路已封**（pass2 §2.2(b)、§3.2③）✔。fallback（`:297-299` 取最尾、`:300-309` 反向搵非 backup）都會落 cancel → 安全方向（最壞係 `WORLD_TIMEOUT`，唔會備份）。
**但 pass2 §2.2(a) CRITICAL 未封**：`pressFirst()`（`:239-241,255-269`）對**任何** `ConfirmScreen` 零 label 檢查、直接 `button.onPress()` 第一粒＝**accept**。第三方 mod／SK 彈嘅確認框（刪檔／下載／停用）照樣被代按。→ **N2**。
另：**E8 未經真機驗證**（今輪 run 嘅 `latest.log` grep `BackupConfirm|backupJoin` ＝ **0 命中**，對話框從未出現）；zh 字串（`:324,327,337,343`）係硬編，而 1.19.2 `client.jar` **只 ship `en_us.json`** ⇒ 中文 label 未對過真 artifact（安全靠 fallback，非靠字串命中）。

**E9 — 部分（in-memory 負控，逐個驗）。**
1. flag 只准一個資源根 → **真封**：`check_flag()`（`:98-111`）掃 `src/main` rglob ＋ `src` 下唯一 `*autotest*.flag`。負控：`src/main/resources` 放同名檔 → RC=1（Hermes 真檔負控亦為 rc=1／rc=0）。caveat：**只掃 `FORGE/src`**，`neoforge/1.21.1/src` 唔掃。
2. `gradle.properties` 有 `packaiAutotest` → **真封**（`:92-95`；負控 RC=1）。
3. `WRITE_RE` 補洞 → **部分**：`Files.delete(d.resolve("x"))` ／ `new PrintWriter(x)` 已 RC=1 ✔；但 **`new FileWriter("C:/evil.txt")`、`RandomAccessFile`、`FileChannel.open` 配任何一個合法寫入仍 RC=0**（`:28-33` 唔含）。
4. 「順手阻擋正當重構」→ **未封**：`Files.writeString(out, body)`（helper／變數路徑）仍 RC=1（`:171-174` 只認字面 `resolve("autotest")`）⇒ pass1 L1 想抽 `autotestDir(mc)` 仍然會被閘擋。
5. `..` 穿越 → **部分＋新假紅**：`:159-161` 改為掃**所有**字串字面值，`"loading..."`（省略號）→ RC=1（實測）。＝任何 log 打 `...` 就會爆閘。
6. tick 雙跑／call 位置 → **真封**：`END_GUARD`／`END_BLOCK`（`:34-41`）對真 `ClientSetup.java:127-131` 命中；把 `AutoTestHarness.tick()` 搬到 `if (event.phase != END)` **之前** → 兩個 regex 都唔 match → RC=1（實測）✔。旗標名 → **真封**：`check_flag_name()`（`:114-120`）要求恰一個 `getResourceAsStream` 且名＝`packai-autotest.flag`；`active(){return true;}` → RC=1（實測）✔。
額外新洞 N10：`check_gradle()`（`:62-89`）唔 strip 註解 → 真 block **之前**有一行**註解咗**嘅 `if (project.hasProperty('packaiAutotest')) {` → RC=1 且訊息誤導（實測）。

---

## 新問題（本輪修正本身引入／未封）

- **N1【HIGH】** 部署鏈可以直接把 flag jar 推入 **live instance**，並且**改成生產檔名**：`mc_mod_deploy_jar.py:92` `src = Path(args.jar) if args.jar else …` — **`--jar` 完全無白名單**（只驗 `is_file`＋zip 完整）；`:122-129` `existing = sorted(mods.glob(glob_pat))` … `elif len(existing)==1: target_name = existing[0].name`。實測 live `mods/` 只有一個 `packai-0.2.3+mc1.19.2-forge.jar` ⇒ `--target packai --jar build/libs/packai-0.2.3-autotest.jar` 會**成功部署**，落地檔名仍係 `packai-0.2.3+mc1.19.2-forge.jar` —— E4 想避免嘅「旗標 jar 用正常名入 live」由部署腳本重新造出。`mc_mod_jar_guard.json` 只有**事後告警**（`sha` 變咗且無對應 backup）且 `known_jars` 仍係 0.2.1／dist（`mc_mod_jar_guard.py:120-122`）⇒ 連正當 0.2.3 部署都會報「來源不明」。**必須**加：腳本拒絕含 `packai-autotest.flag` 嘅來源 jar（或 `--target` 白名單比對 sha／檔名 classifier `-autotest`）。
- **N2【HIGH】** `pressFirst()` 盲按第一粒掣（`:239-241,255-269`）＝pass2 §2.2(a) **CRITICAL 未封**：任何第三方 `ConfirmScreen` 被代按 accept。E8 只做 `BackupConfirmScreen`。
- **N3【MED】** E6：`casesRead=true` 早過驗證（`:142` 對 `:145`）⇒ malformed／半寫／外來 `cases.json` 永久靜默停用 harness。
- **N4【MED】** E3：rename 失敗 → `cases.json` 留低＋`casesRead` 已 true ⇒ 下次開 game **整批重跑**（重複付費），只有 warn、無 fallback（`:151-156`）。
- **N5【MED】** 閘：`sibling_move = stmt.lstrip().startswith("Files.move")`（`check_autotest_flag.py:172-174`）豁免**任何** `Files.move` 目的地 → `Files.move(file, traceDir.resolve("x"))` RC=0（實測）。E3 為咗讓路而開嘅洞。
- **N6【MED】** 閘：`:159-161` `..` 掃全部字串字面值 ⇒ `"..."` 省略號 → RC=1（實測）；今後任何 log 打省略號都爆閘（假紅）。
- **N7【MED】** 閘：`WRITE_RE`（`:28-33`）仍漏 `new FileWriter`／`RandomAccessFile`／`FileChannel`（實測配合法寫入仍 RC=0）⇒ H3-3 只補一半。
- **N8【MED】** E1 proxy 脆弱位（見上）：`item` ＝ first card `sourceItemId`（lowercased），唔係被問 item ⇒ 非 output-first 卡序時假 `NO_CARDS`（`AutoTestHarness.java:602`＋`AskService.java:523`＋`RecipeCard.java:64`）。
- **N9【LOW】** E3 產生嘅 `cases.json.consumed-*` 冇人清：driver `finally: os.remove(CASES)`（`%TEMP%\packai_autotest_run.py:220`）**永遠 FileNotFoundError**（被 `except OSError` 吞），沙盒實物已積一個。
- **N10【LOW】** 閘 `check_gradle()` 唔去註解 → decoy 註解行令 RC=1 兼訊息誤導（實測）。
- **N11【LOW】** `check_tick_guard()`（`:123-129`）只查 `tick()` 頭 5 行含 `active()` 同 `return`，唔查次序：`if (true) return; if (!active()) return;` → RC=0（實測）。
- **N12【LOW】** `build/resources/main/packai-autotest.flag`（15:18:50）**仍然存在**（stale 產物未清）；今次 plain jar 乾淨係因為 plain build 在 15:18:48、flagged 在 15:18:50 —— 「plain build 會唔會带 stale flag」**未核實**（唔准跑 build）。

## 剩餘風險（pass1／pass2 未處理，逐條比對）

1. **pass2 §2.2(a) CRITICAL**：`pressFirst` 盲 accept 第三方確認框 → **仍在**（＝N2）。
2. **pass2 §4／§3.3**：閘**仍然冇 jar 內容斷言**（Hermes 今次嘅 jar 負控係人手做，唔係閘）；`build.gradle` 亦冇「jar 前清 flag」步驟 → stale 殘留（N12）。
3. **pass2 §2.1 HIGH**：START 階段無 per-case timeout（`ChatSession.isBusy()` 卡住＝食盡 20 分鐘、pending case 連一行結果都冇）→ **仍在**（`AutoTestHarness.java:372-374`）。
4. **pass2 §1.5／§2.3／§2.4**：世界名靠硬編、`writeStatus` 失敗被吞、無 `runId`／idempotence → **全部仍在**。
5. **pass1 M2**：class javadoc 仍寫「Writes only `<gameDir>/packai/autotest/`」（`AutoTestHarness.java:37-39`），未列 trace／usage／backups 副作用 → **仍在**。
6. **pass1 M6**：driver ＋ `judge_autotest.py` 仍在 `%TEMP%`（非版控），狀態檔 `consume` 語意兩邊唔一致 → **仍在**。
7. **pass1 L1／L2／L4**：`autotestDir()` 未抽（`:130,704,706`）、`case DONE -> {}` 死碼（`:112-113`）、`WORLD_TIMEOUT_TICKS` 無註釋、`reason="world_timeout"` vs `status="WORLD_TIMEOUT"` 大小寫不一（`:231-232`）、log 前綴 `"packai autotest"` 未跟 `Pack AI`（`:655`）→ **全部仍在**（LOW）。
8. **未核實清單**：`-autotest.jar` 實際檔名；`reobfJar` 會否保留 classifier；49 Java 測試與 compile（採納 Hermes RC=0）；E8 中文 label（無真機對話框，lang pack 未核）；1.20.5+ 版本矩陣（pass2 已標）。

## 總結

E1／E2／E3／E4／E5／E6 ＝**真解決**（各有 1 條殘路或未核實項）；E7 ＝**表面**（busy 早退繞過）；E8 ＝**表面**（backup 路真封、generic 確認框未封）；E9 ＝**部分**（6 洞：3 真封、2 部分、1 未封，另引入 2 個閘洞）。
**最重 3 件**：①N1（部署腳本可把 flag jar 以生產檔名推入 live）；②N2＝pass2 §2.2(a) CRITICAL（`pressFirst` 盲 accept 任何第三方確認框）；③N3＋N4（E6 早 set／E3 rename 失敗＝同一批 test 重跑或靜默停用，兩者都係付費／零訊號）。E4／E1 係本輪質素最高嘅兩項修正：**plain jar 已證無 flag**、**miss／error 已證唔再假 OK**。
