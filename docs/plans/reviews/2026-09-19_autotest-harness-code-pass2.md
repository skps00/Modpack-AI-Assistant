# 2026-09-19 — packai autotest harness **Pass 2（前瞻／三個月後脆弱位）**

> 範圍：**MC 1.19.2 + Forge 43.x**（`forge/1.19.2`）。`neoforge` 樹 PAUSED，唔郁。
> 對象：`build.gradle`、`src/autotest/resources/packai-autotest.flag`、`client/autotest/AutoTestHarness.java`、
> `ClientSetup.java`（+1 行）、`tests/check_autotest_flag.py`。
> 紀律：**只讀**。每條＝severity ＋ `檔案:行` ＋引文 ＋「三個月後會點爆」＋加固建議。**未核實嘅嘢明文標示**。
> 本輪核實工具：真機 artifact（`docs/research/artifacts/2026-09-19-autotest-run/`、`%TEMP%\autotest_run_out*.log`）、
> `javap` 反編譯 MC 1.19.2 `client.jar`、**本機快取嘅 MC 1.21.1 官方 client mappings**、
> NeoForge 21.1.241 sources jar、沙盒／live instance 檔案核對、jar zip listing。

---

## 1. 會靜默失效嘅假設

**1.1 CRITICAL — 「最新 ask 檔有無新 final」＝本 case 成功，唔綁 case**
`AutoTestHarness.java:294` `if (dir != null && sawFinal(dir, caseStartTs))`；`414-431` `newestAsk()` 只按 **mtime 揀最新 `ask-*.jsonl`**；`291-295` 見到就寫 `"OK"`。
三個月後：任何**唔屬於當前 case** 嘅 ask 產生更新嘅 `display.body.final`（SK 自己問一句、未來新功能背景 ask、前一個 case 遲到嘅尾寫入）→ 當前 case 即刻 **假 PASS**，`elapsedMs` 異常短。
假訊號：`status-*.json` 出現 `{"status":"OK","elapsedMs":<細>}`——冇任何 error，assertion 層只會見到「卡數唔對」而查錯方向。
加固：用檔名綁定（`AskTrace.java:279-283` = `ask-<stamp>-<sanitizedItemId>.jsonl`，`371-389` 有 sanitizer）斷言 `newest.getFileName().contains(sanitize(case.item))`，或讀 trace 內 focus 欄位比對；唔匹配就繼續等。

**1.2 HIGH — 「冇 `display.body.final`」被一律當成超時**
`433-457` `scan()` 只認字面 `"display.body.final"`（真來源 `AskService.java:509`）。trace 關掉、ask 起唔到、模型 error、miss 分支唔寫 final ⇒ 全部走 `301-306` 嘅 `TIMEOUT`。
三個月後：內容／流程失敗被誤診成「模型慢」，driver 只會報 TIMEOUT，人手去查 API key／網速。
假訊號：**全部 case TIMEOUT**（最貴嘅錯方向）。
加固：timeout 分類——`packai/trace` 冇新檔＝`NO_TRACE`、有檔冇 final＝`NO_FINAL`、有 final 但唔屬本 case＝`CROSS_TALK`；status 寫明。

**1.3 HIGH — 硬編 `ISO_LOCAL_DATE_TIME` 對 trace ts**
`51` `TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME`、`459-482` `eventNewer()`；ts 由 `AskTrace.java:44/141`（`TS_ISO.format(clock.get())`）寫。
三個月後：AskTrace 一改（`Instant`／帶 offset／epoch millis／換 clock／時區）→ `LocalDateTime.parse` 拋 → `return false` → **所有 case 靜默 TIMEOUT**（今日兩邊都係 `LocalDateTime.now()` 所以綠）。
假訊號：全線 TIMEOUT，同 1.2 一模一樣，最難查。
加固：harness 用 `AskTrace` 同一個 formatter（或接受多格式）；更穩：改用 `Files.getLastModifiedTime(file) ≥ caseStart` 做粗篩，ts 只做次級斷言。

**1.4 HIGH — 樣本抽取鏈（`Registry.ITEM` ＋ JEI OUTPUT 卡）**
`335` `Registry.ITEM.containsKey(id)`、`338` `Registry.ITEM.get(id)`、`353-373` 只接受 `card.focusRole()==OUTPUT` 且 id 命中嘅卡；`JeiRecipeCards.java:100` 以 `ModList.get().isLoaded("jei")` 為分支。
三個月後：JEI 被停用／pack 換 JEI 版本／目標物品只有 INPUT 卡（今日木錘 `tetra:modular_double` 嘅真卡就係 `role=input`）→ 每條 case `NO_SAMPLE`，但 `:270` 照寫 `DONE`、`ok=0`。
假訊號：`DONE` ＋ 全部 `NO_SAMPLE` ——睇落似「run 完成、測試全 fail」，實情係抽取管線死。**唔會 crash、唔會 warn**。
加固：status 加 `noSample` 計數；連續 N 條 NO_SAMPLE → `finish(mc,"SAMPLE_PIPELINE_DEAD")`；並在 `packai-autotest.flag` 內容或 status 寫 `jeiLoaded`。

**1.5 MEDIUM — 靠世界名存在**
`:219-227` `mc.createWorldOpenFlows().loadLevel(title, world)`；`loadSent` 令失敗**永不重試** → 等 `WORLD_TIMEOUT_TICKS=6000`（`:41`，約 5 分鐘）才報 `WORLD_TIMEOUT`。
三個月後：沙盒世界改名／被刪（driver `packai_autotest_run.py:26` 硬編 `WORLD="新的世界 (1)"`）→ 5 分鐘白等＋一次付費啟動。
假訊號：`WORLD_TIMEOUT`（似「載入慢」）。
加固：`loadLevel` 前查 `saves/<world>/level.dat` 存在；driver 由 saves 目錄動態揀世界名。

**1.6 MEDIUM — `finished` 靜態旗標：同一 JVM 內第二批永遠唔跑**
`:54` `private static boolean finished;`、`:92` `if (!active() || finished) return;`、`:484-489`。
三個月後：driver 重試／SK 想即刻再跑第二批（game 未關）→ harness 靜默 ignore，driver 等 25 分鐘。
假訊號：`TIMEOUT: no status json`（似 harness 冇起動，其實係「已經跑過、唔收第二單」）。
加固：finish 時 status 寫 `finishedAt`；driver timeout 分支**必 grep** `latest.log` 嘅 `packai autotest status=`（`:495` 有寫）。

---

## 2. 狀態機（IDLE→OPEN_WORLD→RUN_CASES→DONE）

**2.1 HIGH — START 階段完全冇 per-case timeout**
`:273-277` 先 `if (ChatSession.isBusy()) return;` **之後**才 `caseStartMs = …`（277）；`CASE_TIMEOUT_MS` 只在 `:301`（POLL）檢查。而 `:250-253` 嘅補結果列只喺 `sub == Sub.POLL` 才加。
三個月後：`ChatSession.isBusy()` 卡住 true（前一次 ask 異常未 clear）→ 一路乾等至 `BUDGET_MS`（20 分鐘）；卡喺 START 嘅 pending case **連一行結果都冇**（status `caseCount` 少一條，易被當成「跑得少」）。
假訊號：`BUDGET_EXCEEDED`，但真正原因係 busy 未清。
加固：START 用獨立 `waitStartMs` ＋ `START_TIMEOUT_MS`（例 60 s）→ `CASE_STUCK`；`finish()` 時把未完成 case 一律補 `status="NOT_RUN"`。

**2.2 CRITICAL — `pressFirst()` 盲按對話框第一個 `Button`（跨 mod、跨版本通用能力）**
`:211-218`（`instanceof ConfirmScreen` / `BackupConfirmScreen` → `pressFirst`）、`:230-246`（`for (GuiEventListener child : screen.children()) if (child instanceof Button) button.onPress();`）。
**本輪以 bytecode 核實**：1.19.2 `BackupConfirmScreen` 第一個掣＝`selectWorld.backupJoinConfirmButton`（`javap`：`ekd.b()` 加掣次序 confirm→skip→cancel）→ handler `ekd.c` → `proceed(true, …)` → `WorldOpenFlows` `lambda$askForBackup$9` → `if (backup) EditWorldScreen.makeBackupAndShowToast(levelSource, world)` → `LevelStorageSource.getBackupPath()` ＋ `Files.createDirectories`。
三個月後：(a) 任何第三方 mod／SK 手動彈嘅 **ConfirmScreen 都會被代按 accept**（`ConfirmScreen` 第一個掣＝`accept(true)`），包括「刪檔／下載／停用」類對話；(b) 每次需要版本確認就寫一個世界備份 zip 落 **`<gameDir>/backups/`**（越界，見 §3.2）。
假訊號：冇。純靜默（只多 zip／改狀態）。
加固：**唔准盲按**——只認 title 白名單（backup 場景優先按 `selectWorld.backupJoinSkipButton`），遇未知對話框 → status `UNKNOWN_DIALOG` ＋ ABORT，等人睇。

**2.3 HIGH — `finish()` 唯一輸出係 status 檔，而寫入失敗被吞**
`:484-494` `try { writeStatus(...) } catch (Throwable ignored) {}`；`:505-508` `if (dir == null) return;`。若 IO 失敗（dir null／磁碟已滿／lock），**`ABORT` 永遠唔落檔**，driver 等足 25 分鐘。
假訊號：driver「no status json」，但 `latest.log` 其實有 `packai autotest status=ABORT`（`:495`）。
加固：driver timeout 時 fallback grep `latest.log`；或 harness 失敗時寫 gameDir 根（有權限）作次級通道。

**2.4 MEDIUM — crash／重開冇 idempotence**
進度全在記憶體（`:62-74`）；無 run id 概念。遊戲 crash 重開 → `finished=false` 而 `cases.json` 仍在（`packai_autotest_run.py:216-223` 嘅 finally 唔會執行，今日就係咁）→ **由頭再跑一次**（重複付費 ask）；反過來 driver 亦分辨唔到「新一輪」定「舊一輪」。
加固：`cases.json` 帶 `runId`，status 帶 `runId`；harness 開跑前查 `status-*.json` 有無同 `runId` 已完成 → 有就即刻寫 `SKIPPED_ALREADY_DONE`。**今日完全冇呢個概念**。

**2.5 LOW — tick 重入／例外**
`:91-108` 每次 `onClientTick`（`ClientSetup.java:127-131`，END phase）呼叫；`button.onPress()` 可同步再入 `loadLevel`。`catch (Throwable)` → `ABORT`（可接受），但與 2.3 疊加會變「無輸出」。加固：`tick()` 開頭加 `if (inTick) return; inTick=true; try{…} finally{inTick=false;}`。

---

## 3. 隔離邊界（只准 `<gameDir>/packai/autotest/`）

**3.1 ✅ 直接寫入合規（已核實）**：唯一寫入點 `:504-546`，`Files.createDirectories(dir/packai/autotest)`（`:541`）＋ `Files.writeString(dir/packai/autotest/status-*.json)`（`:542-545`）。冇其他 `Files.write*`。

**3.2 ❌ 但「harness 觸發嘅寫入」三條全部越界**（唔喺 harness 源碼可見，`check_write_paths` 睇唔到）：
1. `<gameDir>/config/packai-usage.json` — `DailyTokenUsage.java:39-40`＋`:175`（每次 ask 寫），driver 冇還原。
2. `<gameDir>/packai/trace/ask-*.jsonl` — 設計接受（`AskTrace.java:312-345`）。
3. **`<gameDir>/backups/*.zip`**（§2.2 bytecode 核實路徑）— driver 只 backup/restore trace ＋ latest.log（`packai_autotest_run.py:53-73`）⇒ **每跑一次多一個世界備份 zip，冇清理**（沙盒 gameDir 係 junction 去 `Documents\packai_dev_game`，食 SK 磁碟）。
⇒ `AutoTestHarness.java:38`「Writes only `<gameDir>/packai/autotest/`」對**直接寫入**成立、對**整體效果唔成立**，會誤導下一個 agent。加固：Javadoc 改寫成「direct writes only」＋明列副作用清單；driver 加 `backups` 前後 diff。

**3.3 CRITICAL — 「無 flag ⇒ 零殘留」今日已經唔成立（jar 內容核實）**
`build.gradle:108-111` 只「加」srcDir，`if (project.hasProperty('packaiAutotest'))`；**冇任何移除／清理**。
本輪實測：
- `forge/1.19.2/build/libs/packai-0.2.3.jar`（09-19 13:14）**zip listing 有 `packai-autotest.flag`**，sha256 `addb2784fbdb5b075dc46b6b0aeef8308f49091495dfd9e4054759449fc78ea2`；同沙盒 deployed `packai-autotest-dev.jar`、`%TEMP%\packai_flagged.jar` **同一 hash**。
- `build/resources/main/packai-autotest.flag` **仍然存在**（13:14）。
- 推斷（強，未跑對照 build）：Gradle `ProcessResources`（`Copy` 系）**唔會清走唔再由輸入產生嘅 stale output** ⇒ 之後冇 `-PpackaiAutotest` 嘅 `jar` 亦可能照樣打包 flag；A1「無 flag build ⇒ jar 冇 flag」在真實 pipeline 上冇保證。（要證實：`gradlew clean jar` vs 「flagged build 之後直接 `gradlew jar`」比 zip listing。**未跑，因本輪只讀。**）
- 洩漏通道：`%LOCALAPPDATA%/hermes/state/mc_mod_jar_guard.json:8` 第一個 jar 係舊路徑 `build/libs/packai-0.2.1.jar`；人手 `--jar build/libs/packai-0.2.3.jar` 就係把 flag jar 推入 **live instance**。
- 今日 live instance 仍然乾淨（jar sha256 `06b5b129a114…`、mtime 09-18 07:12、**`packai/autotest/` 唔存在**⇒唔會自動跑）✅。
加固：①flag build 出另一個 output dir（或 `jar` 前 `doFirst { delete build/resources/main/packai-autotest.flag }`）；②CI/測試加 jar 內容負控制（見 §4）；③`mc_mod_jar_guard.json` 更新到 0.2.3 並 pin `flag` 檢查。

**3.4 HIGH — 殘留 trigger 係真嘅（今日就中）**
沙盒 `instances/packai_sandbox/minecraft/packai/autotest/cases.json` **仍然存在**（mtime 09-19 14:50，`quitWhenDone:true`，4 條 case）＋沙盒 `mods/packai-autotest-dev.jar` 仍在（`autotest_run_out4.log` 停喺 step 5＝driver 被中斷、finally 冇跑）。
⇒ 下次開 `packai_sandbox` 就會**自動入世界 → 跑 4 條付費 ask → 自動關 game**，冇人知。
加固：`cases.json` 加 `expiresAt`（harness 過期即刪檔唔跑）；driver 開頭先掃 stale `packai/autotest/`；`quitWhenDone` 改為必須同時有 `runId` 匹配。

---

## 4. 測試基建盲區（`tests/check_autotest_flag.py`）

只做文字 grep：唔編譯、唔開 jar、唔跑真 run。**三個月後最大盲區＝出貨 jar 內容完全冇斷言**——§3.3 就係咁漏（A1 在 check 上「假綠」）。
其他盲區：`WRITE_RE`（`:27-30`）只認 `Files.write*` 字面（helper／變數路徑／`newOutputStream` 包裝即漏）；`check_tick_guard`（`:76-83`）只釘 `tick()` 第一句（`active()` 改成 `return true` 仍然綠）；`check_call_sites`（`:86-103`）要求全樹「恰一個 tick 呼叫」→ Neo 樹一旦解 PAUSED 加同一 hook 即紅，新人會去改測試而唔係改設計。
另外：**driver 只存在 `%TEMP%\packai_autotest_run.py`（未入 git）**，Temp 清理／換機就冇得重跑 → 建議 commit 入 `research/` 或 `tools/`。

**建議補強斷言（一條、可自動化、今日會紅＝證明有鑑別力）**：
```python
# 出貨 jar 負控制：build/libs/packai-*.jar 唔准含 autotest flag
import glob, zipfile
jars = sorted(glob.glob(str(FORGE / "build/libs" / "packai-*.jar")))
if not jars:
    fail("先跑 gradlew jar（無產物＝驗唔到 A1，唔准 skip）")
for j in jars:
    if any(n.endswith("packai-autotest.flag") for n in zipfile.ZipFile(j).namelist()):
        fail(f"shipped jar contains autotest flag: {j}")
```
（配套 build 修正見 §3.3①。可選第二條：`-PpackaiAutotest` 版本**必須**含 flag ＝ negative control。）

---

## 5. 升級／跨版本（1.19.2 結論；跨版本以本機 mappings 核實）

| 依賴 | 1.19.2（現況） | 1.21.1（**本機核實**：MC 官方 client mappings ＋ NeoForge 21.1.241 sources） | 最先要改 |
|---|---|---|---|
| `WorldOpenFlows.loadLevel(Screen,String)` | 有（mappings `57:58`） | **冇**；改 `openWorld(String, Runnable)`（`238:244`） | `AutoTestHarness.java:221` |
| `Registry.ITEM` | `net.minecraft.core.Registry` 欄位 | 移到 `net.minecraft.core.registries.BuiltInRegistries.ITEM` | `:32` import、`:335`、`:338`、`:380` |
| `ItemStack.hasTag()` | 有（mappings `568:568`） | **冇**（components；`getComponentsPatch()` 等） | `:365` |
| `BackupConfirmScreen` | ctor `(Screen, Listener, Component, Component, boolean)` | ctor 變 `(Runnable onCancel, Listener, …)`，多 `Checkbox eraseCache` | `:28`（`instanceof` 仍可編，但「第一個 Button」語義變 → §2.2） |
| `ConfirmScreen` | 有 | 有（ctor 家族改 BooleanConsumer／含 onCancel） | `:29`、`:211` |
| `TitleScreen` | 有 | 有 | `:31`、`:219` |
| `ClientTickEvent` hook | Forge 43.x：`event.phase == END`（`ClientSetup.java:128`） | NeoForge：`ClientTickEvent` abstract **Pre/Post，冇 `phase`** | `ClientSetup.java:128`（hook 唔可以照抄） |
| `createWorldOpenFlows()` / `Minecraft.stop()` / `getInstance()` | 有 | 有（mappings `2138` / `1704` / `2578`） | `:221` / `:498` / `:94` |

- **1.20.5+（components 令 `hasTag` 消失）未核實**：本機無 1.20.x mappings。標「未核實」，要升級時先核。
- 建議：把 `Registry.ITEM` 用法收成單一 helper（例 `itemRoot()`），並在檔頭寫版本矩陣註釋（`1.19.2 ✅ / 1.21.1 已核實後改 / 其餘未核實`），令升級只需改一處。

---

## 最脆弱 3 位
1. **出貨 jar 唔乾淨**：`build/resources/main` 同 `build/libs/packai-0.2.3.jar` 今日都含 `packai-autotest.flag`（sha256 `addb2784…`）；`build.gradle:108-111` 只加唔清 ⇒「零殘留」失效，且有直通 live instance 嘅通道。
2. **Trace 判 OK 唔綁 case**（`AutoTestHarness.java:294`＋`414-431`）⇒ 假 PASS，冇 error 冇 warn，最難查。
3. **`pressFirst()` 盲按對話框**（`:211-246`）⇒ 可代按任何 ConfirmScreen、已核實會寫 `<gameDir>/backups`（越界）。

**下一個 agent 最快撞到嘅坑**：沙盒仍有 `cases.json`＋`packai-autotest-dev.jar`，一開沙盒就自動跑舊 case 併自動關 game；而 `finished` 係 static、唔收第二批，於是「再跑一次」變成 driver 等 25 分鐘報 `no status json` ——真正原因要 grep `latest.log` 嘅 `packai autotest status=` 才睇得到。
