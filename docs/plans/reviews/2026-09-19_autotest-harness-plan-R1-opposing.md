# R1 反方 review — packai「真機自動測試 harness」計畫 v2

> 被審：`docs/plans/2026-09-19-in-game-autotest-harness.md`（commit `3f366d7`，97 行）
> 角色：**R1 反方**。目標＝盡最大努力證明佢唔可行／有窿。**禁令**：只讀唔改（本檔為唯一輸出）。
> 樹：`forge/1.19.2`（primary）；`neoforge/1.21.1` PAUSED。契約：`C:\Users\skps9\AGENTS.md` ＋ 專案 `AGENTS.md`。
> 標記：**【已核實】**＝本輪有工具輸出；**【未能核實】**＝查唔到，明講。
> ⚠️ **證據基準（重要）**：`HEAD` = `3f366d7`（即本 plan），但**工作樹有 118 項未 commit 改動**（`AskService.java` +334、`PackAiConfig.java` +261、`ClientSetup.java` +8；`logic/DailyTokenUsage.java` **untracked**）。本報告全部 `file:line` 係對**工作樹**核，唔係對 HEAD／已部署 jar。凡引「現有 config／現有行為」嘅 claim，實作者要 pinned 樹狀態（`git status --porcelain | wc -l` = 118）先算。

## 載重決定（LD）存活表 — 比分由表計出

| LD | 內容（plan 行） | 反方判定 |
|---|---|---|
| LD1 | 用 Prism CLI `-w` 入世界，「最大風險消失」（§2.0 L19-24） | **死** |
| LD2 | 入口＝`AskService.beginAsk`「同 GUI 一樣」（§2.1 L33） | **死** |
| LD3 | 三重守衛（enabled 預設 false＋magic header）＝濫用近零（§2.1 L28-31） | **半死**（殘留觸發未處理） |
| LD4 | 唔加 settings UI key／唔加 lang key（§2.1 L31） | **存活**（反方此擊落空） |
| LD5 | 成本守衛＝≤20 條＋沿用 `dailyTokenLimit`＋DS peak（§2.5 L48-50） | **半死**（沿用嘅上限預設＝0） |
| LD6 | A5/A1「唔改 production 行為」「零新增紅」（L54/L58） | **死** |
| LD7 | driver 用 `run_hidden.vbs` 零彈窗＋A4 focus_stolen（L39/L57） | **死** |
| LD8 | Plan B＝`onLoggingIn` 後 loadLevel（L24） | **死**（邏輯不可能） |

→ 1 存活 ＋ 2 半死 ＋ 5 死。**正方 2 : 反方 8**（見文末）。

---

## F1 —【CRITICAL】【已核實】`-w` 對 1.19.2 係 no-op，唔係「實測支援」；「最大風險消失」係假

**攻擊**：plan 用「Prism `-w` 實測有 → 唔需要 mod 側入世界（最大風險消失）」做整個 §2.0 的結論。
真相係 Prism **只會對有 `feature:is_quick_play_singleplayer` trait 嘅版本**才傳參數，1.19.2 profile 冇該 trait（quick-play 係 1.20／23w14a 加入）⇒ launcher **靜默唔傳**，遊戲照開喺主選單。呢個唔係「待實測」，係**文件已明寫唔支援**。

**證據**：
- 上游源碼 `launcher/minecraft/MinecraftInstance.cpp::processMinecraftArgs`：
  `else if (!targetToJoin->world.isEmpty() && profile->hasTrait("feature:is_quick_play_singleplayer")) { args << "--quickPlaySingleplayer" << targetToJoin->world; }` ← 條件唔成立＝連 `--server` fallback 都冇。
- 官方 CLI 文件（PrismLauncher command-line 頁）`--world` 條目：**"Requires Minecraft 1.20+ with quick play support. Only valid with `--launch`."**
- 本機安裝 build **亦有**同一 gate（`grep -a -c`）：`prismlauncher.exe`（2025-07-03, 10.6 MB）→ `is_quick_play_singleplayer` = **1**、`quickPlaySingleplayer` = **1**。
- plan 自己 L5 已經寫「1.20 才有官方 quick-play 參數」→ **同 §2.0 L23 自相矛盾**。
- 副：L5 寫「Forge **43.3.5**」，實測 `forge/1.19.2/gradle.properties:8` = `forge_version=43.4.0`（43.3.5 只係「Prism packs may pin」，見 :9 註釋）。

**後果**：照 plan 實作 → harness 永遠唔觸發 → driver 每次白等 15 分鐘 timeout → A3 永久紅；而 plan 會誤診為「mod 側守衛寫錯」。
**Flip condition**：Prism 對 **1.19.2** profile 真會傳 `--quickPlaySingleplayer`（實測法：`-l … -w …` 開一次，睇 Prism log 嘅 java command line 或 MC log 嘅 `Completely ignored arguments`），**或** plan 改成「預設唔靠 `-w`，第一步就做 mod 側入世界」。**【未能核實】**：安裝 build 對 1.19.2 profile 逐字行為（未跑 game／未讀 version json）。

---

## F2 —【CRITICAL】【已核實】Plan B 邏輯上唔可能：`LoggingIn` 只喺「已經入咗世界」之後才 fire

**攻擊**：plan 的 fallback 寫「mod 側 `onLoggingIn` 後自動 `loadLevel`」（L24）。但 `LoggingIn` 係喺 `ClientPacketListener.handleLogin` 內 post，即**連線／LocalPlayer 已建立**之後；單人遊戲亦要經整合伺服器。⇒ 「等 LoggingIn，然後入世界」係雞蛋問題，用喺呢個位係**死碼**；真正 hook 只可以係 client tick（`mc.level == null` 時才叫 loadLevel）。

**證據**：
- `forge-1.19.2-43.4.0-sources.jar` → `patches/net/minecraft/client/multiplayer/ClientPacketListener.java.patch:7`：
  `+ net.minecraftforge.client.ForgeHooksClient.firePlayerLogin(this.f_104888_.f_91072_, this.f_104888_.f_91074_, …);`（喺 `handleLogin` 內）
- 同 jar `net/minecraftforge/client/ForgeHooksClient.java:936-937`：
  `public static void firePlayerLogin(…) { MinecraftForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggingIn(…)); }`
- 反向（對 plan 有利、老實報）：**fallback 嘅 API 真存在**（javap 真 jar）——
  `Minecraft.createWorldOpenFlows()` ✅、`WorldOpenFlows.loadLevel(net.minecraft.client.gui.screens.Screen, java.lang.String)` ✅（1.19.2）。
- 但成本唔係「幾行」：`WorldOpenFlows` 另有 `askForBackup`／`promptBundledPackLoadFailure` 互動路徑、參數係 **存檔夾名**（`新的世界 (1)`）唔係顯示名，且要喺 client thread 安全時機叫。
**Flip condition**：plan 改成 tick-gated 入世界（含「已有 screen 開住／上次 load 未完成」守衛）＋明寫用存檔夾名 ＋ 列出互動 prompt 路徑點處理。

---

## F3 —【HIGH】【已核實】入口點 `AskService.beginAsk` 唔存在；「同 GUI 一樣嘅入口」唔成立

**攻擊**：plan L33 寫「經**同 GUI 一樣嘅入口**（`AskService.beginAsk`）」。全 repo **冇** `beginAsk` 方法（只有 `beginAskLoop`／`beginAskSession`）。真入口係 public `askAsync(...)`；而 GUI 同 harness 嘅**輸入語義唔同**：GUI 傳 `stripFocus`（assistant strip 嘅 contextStack）＋ 先設 JEI pin；harness 冇 screen、冇 strip、冇 pin，只會落 `resolveStable(question)`（id-in-question／手持）。即「同一 method、唔同入參」，S5a「手持／focus 木錘」喺 GUI 以外根本復現唔到 strip 語義。

**證據**：
- `client/service/AskService.java:100` `public void askAsync(String question, Consumer<AskResult> onResult)`；`:129` `private void runAsk(`；`:620` `static AskLoopState beginAskLoop(`；repo 全域 grep `beginAsk\b`（排除 beginAskLoop/Session）= **0 命中**。
- 已有非 GUI 入口先例：`client/command/AiClientCommands.java:20-27` `Commands.literal("ai")… .executes(… ClientSetup.askService().askAsync(q, result -> …))` ⇒ 「唔經 GUI 嘅入口」早已存在，plan 當新發明。
- `AskService.java:676-681` `/** Prefer strip focus; else resolveStable(question) — no live JEI hover. */`；`:144` `JeiTargetResolver.clearPin();`
- `client/jei/JeiTargetResolver.java:74-80` `/** Pin / id-in-question only — no live JEI / slot hover. */`
**Flip condition**：plan 寫死 harness 傳咩 `stripFocus`（例如由 case JSON 指定 item id）＋明文承認「focus 語義 ≠ GUI strip」，並改掉 `beginAsk` 之名。

---

## F4 —【HIGH】【已核實】成本守衛係空嘅：`dailyTokenLimit` 預設 0 ＝ 完全唔封

**攻擊**：plan L48-50 唯一硬上限係「≤20 條」，其餘靠「沿用玩家既有 `llm.dailyTokenLimit`（唔准繞過）」。但該 key 嘅 default 就係 **0＝唔限**，而 pipeline 嘅判定係 `limit <= 0 → 直接放行`。⇒ 預設配置下，harness 20 條真 LLM call **零上限**；玩家亦可能故意設 0（唔限）→ 守衛等於冇。另：DS peak gate 只係「跑之前讀一次」，但單次跑跨度 15 分鐘，跨 09:00／14:00／18:00 邊界冇處理；亦完全冇計「開 game 次數」成本（A2 負控每次都要開一次 game）。

**證據**：
- `logic/DailyTokenUsage.java:29` `public static final int DEFAULT_LIMIT = 0;`
- `client/service/AskService.java:563-567` `static AskResult dailyTokenBlockOrNull(…){ int limit = PackAiConfig.dailyTokenLimit(); if (limit <= 0 || gameDir == null) { return null; } … }`（`:308` 呼叫，**確實喺 Ask pipeline 內**，唔止 GUI ⇒ plan「會被檢查」呢半句成立）
- `config/PackAiConfig.java:331-332` `defineInRange("dailyTokenLimit", com.skps9.packai.logic.DailyTokenUsage.DEFAULT_LIMIT, …)`（default 帶入 = 0）
**Flip condition**：plan 加 driver 側獨立成本上限（例如跑前讀 `packai-usage.json` 並 assert 剩餘額度 ≥ 預估），**唔可以**只靠玩家 config。

---

## F5 —【HIGH】【已核實】跑一次會輪替掉現有 trace、覆寫 `latest.log` ⇒ 「零新增紅／唔改 production 行為」係假

**攻擊**：harness 行真 pipeline ⇒ 每條問題都開一個 `AskTrace` session（`<gameDir>/packai/trace/ask-*.jsonl`），而 trace 有 50 檔上限輪替；現況 39 檔 → 一次 20 條 = 59 > 50 ⇒ **最舊約 9 條真 trace 被刪**。同時每次開 game 都會輪替 `<instance>/logs/latest.log`（舊檔變 .gz），而 `tests/check_ask_display_leak.py` **預設就係讀 latest.log**、`--trace` 預設掃「今日」嘅 trace ⇒ harness 直接換走現有綠閘嘅輸入，甚至令佢 return 2（非 0）→ 在 `for f in tests/check_*.py; … || echo FAIL` 之下算**新增紅**。A5 亦只係「檔案白名單 diff」聲稱，冇任何「trace 事件名／欄位語義不變」嘅量測。

**證據**：
- `logic/AskTrace.java:32` `public static final int DEFAULT_KEEP_FILES = 50;`；`:439` `rotate(s.gameDir, s.keepFiles, configKeepDays());`；`:312-313` `traceDir → gameDir.resolve("packai").resolve("trace")`
- 實測現況：`ls <instance>/minecraft/packai/trace | wc -l` = **39**
- `tests/check_ask_display_leak.py:34-45` `PRISM_LOG = … / "logs" / "latest.log"`；`:16` 「`--trace` scans ask-YYYYMMDD-*.jsonl …（default = today）」；`:18` 「Exit … 2 = no log lines / no traces in scope / … (need real-machine smoke)」
- 專案 `AGENTS.md` baseline 註明該 check「要有真機 `latest.log` 內容才過」
**Flip condition**：plan 明寫 (a) 跑前 backup trace dir／跑後還原，(b) 明列跑一次之後邊幾個 check 會轉紅並預先宣告為「已知非 regress」，(c) 提供「trace 事件 schema 前後 diff」嘅量測方法（否則 A5 唔可證偽）。

---

## F6 —【HIGH】【已核實】`run_hidden.vbs` 唔存在；A4 嘅 `focus_stolen` 量唔到真兇（MC 係 java 子進程）

**攻擊**：plan L39 叫 driver「用 `run_hidden.vbs` 零彈窗開 `prismlauncher.exe`」。本機 hermes home **冇** `run_hidden.vbs`（只有 `gateway-service/*.vbs` 同 `scripts/hermes-gateway-*.vbs`）。契約指定嘅係 `bg_launch.py`。更致命：`bg_launch.py` 嘅 `focus_stolen` 只判「新前景窗屬唔屬於**佢 launch 嘅 exe**」——Prism 開出嘅 MC 係 **javaw/java 子進程**，其窗搶焦點**唔會被量到** ⇒ A4「driver 自報 focus_stolen 實測值」有結構性盲區（假綠）。

**證據**：
- `find %LOCALAPPDATA%\hermes -iname "run_hidden*"` → **0 命中**；`bg_launch.py` 存在（`%LOCALAPPDATA%\hermes\scripts\bg_launch.py`）。
- 主契約 `AGENTS.md`：「`python "$LOCALAPPDATA/hermes/scripts/bg_launch.py" <app>`；`SW_SHOWNOACTIVATE` ＋開完自驗前景」「JARVIS 自己主動開 → 一律 `bg_launch.py --minimized`」。
- `bg_launch.py:476` `focus_stolen = (before["hwnd"] != after["hwnd"]) and belongs_to_exe(after, exe)`；`:22` docstring「`--minimized` is the JARVIS unattended path」。
**Flip condition**：plan 改用 `bg_launch.py --minimized`（或明寫用邊個機制）＋ 加「MC 窗出現後再量一次前景」嘅獨立量度（唔可以只用 bg_launch 自報）。

---

## F7 —【MEDIUM-HIGH】【已核實】殘留觸發：跑完冇清理 path／冇還原 config

**攻擊**：§2.2 流程（L39）只到「等 DONE → 讀 JSONL → 關 game」，**冇**「刪 `autotest.txt`」／「還原 `autotest.enabled=false`」。而 driver 必須先開 enabled（§2.1 L30）。⇒ 跑完之後檔案與 flag 仍在：下次 SK 自己開同一個 instance（正常玩）就會**再跑一次 harness**——花真錢、可能按 `autotest.quit` 關掉 SK 嘅遊戲、並且污染 trace。而且 driver 要改嘅係 `config/packai-client.toml`，該檔**含 API key**（專案契約明文），surgical 編輯 vs Forge 重寫整個檔（`SPEC.save()`）之間要寫清楚。

**證據**：plan L39 流程無清理步；plan L30 需要 `autotest.enabled=true`；專案 `AGENTS.md`：「Secrets 只存在 `<instance>/config/packai-client.toml`；唔准寫入 repo／HANDOFF／計畫書」；`config/PackAiConfig.java` 每個 setter 尾 `SPEC.save()`（專案 Gotchas 明文）。
**Flip condition**：plan 加「跑完（含 crash／timeout 路徑）finally 刪檔＋還原 flag」，並聲明 driver 唔准讀／打印 TOML 內容。

---

## F8 —【MEDIUM】【已核實】還原清單唔齊；A2 第三個負控係空轉；A2/A3 冇 pin 觀察通道

**攻擊**：
1. §4 白名單只得 3 個檔（`logic/AutoTestHarness.java`、`ClientSetup.java`、`PackAiConfig.java`），但 §2.3 要新 `tests/check_autotest_results.py` ＋ 一個 case→斷言 JSON（L42-44），§8 又要 `autotest_cases.json`（L89）⇒ **至少 2-3 個新檔唔在還原清單**。另外白名單漏咗「ClientSetup 要用邊個 hook」嘅決策（見 F2）。
2. A2（L55）寫「`autotest.txt` 唔存在／`enabled=false`／**production 模擬** → 三者都完全唔行」——但部署環境本身就係 production，③ 同 ② 係同一件事，第三個負控**空轉**；亦冇寫「完全唔行」用邊個 channel 觀察（log？trace？），亦冇計「每個負控要開一次 game」嘅成本。
3. A3（L56）只用 1 條 case（木棍），§8 首批 3 條 ⇒ 範圍唔一致。
**證據**：plan L64 vs L42-44/L89；plan L55；plan L56 vs L89-96。
**Flip condition**：白名單補齊全部新檔 ＋ A2 刪走空轉項並寫死觀察通道（例：`latest.log` 冇 harness marker 行／`packai/autotest/` 冇新檔）＋ A3 對齊 §8 數量。

---

## F9 —【MEDIUM】機會成本：output 通道（trace）已經存在，plan 三件嘢之中有一件係重複建設

**攻擊**：plan §2.0 要 harness 自寫「問題／facts／模型原文／最終顯示」JSONL（L33）。呢啲**已經**由 `AskTrace` 寫入 `<instance>/packai/trace/ask-*.jsonl`（`display.body.final`／facts／工具結果），而 gate `check_ask_display_leak.py --trace` 已經識讀。⇒ plan 真正新增嘅只有「唔用人手打字去觸發」呢一件；另外嘅 collector + 斷言層屬可選。反方主張：先縮 scope 到「一個 dormant trigger + driver 讀 trace」，唔好一次過起三件（亦符合專案契約「複雜交付物拆開評」）。
**Flip condition**：plan 證明自家 JSONL 有 trace 冇嘅欄位（例如 case id 對應／PASS-FAIL 前置狀態），否則砍走 collector。

---

## F10 —【LOW-MEDIUM】其他

- 【已核實】節號倒錯：`## 3. 驗收標準`（L46）之下掛住 `### 2.5 成本守衛`（L48）→ 引 `§2.5` 嘅人會搵錯節。
- 【已核實】觸發檔路徑唔一致：結果寫 `<instance>/packai/autotest/`（此目錄已存在 `packai/`，實測只有 `trace/`），但觸發檔喺 `config/packai/autotest.txt`（`config/` 下**冇** `packai/` 子目錄，要新建）。建議統一喺 `<instance>/packai/`。
- 【已核實】`<timestamp>.jsonl` 格式冇定義精度／碰撞處理，亦冇 retention／上限（同 repo 慣例相反：`AskTrace` 有 `DEFAULT_KEEP_FILES=50`、`DailyTokenUsage.FILE_MAX_BYTES=64*1024`）。
- 【已核實】目錄寫入方式：現有 code 用 `mc.gameDirectory.toPath()`（`AskService.java:138`、`:642`）⇒ plan 講「`<instance>/packai/autotest/`」技術上可行 ✅（反方此項落空）。

---

## F11 —【MEDIUM-HIGH】【已核實】成本守衛引用一個 **HEAD 唔存在** 嘅 key；整個計劃建喺未 commit 地基上

**攻擊**：plan L49 寫「沿用玩家**既有** `llm.dailyTokenLimit`」。但 `git show HEAD:…/PackAiConfig.java | grep -c dailyTokenLimit` = **0**，`logic/DailyTokenUsage.java` **完全 untracked**（`git ls-files` 都搵唔到）。即呢個 key 屬**未 commit 嘅 in-flight 工作**，唔在任何已部署 jar 內 ⇒ (a) 「既有」係錯述；(b) plan §7#3 要「一次 build／一次 deploy 包含 fix A ＋ harness」⇒ harness 會被綁上一批未 commit 改動，一旦該批要 revert／重做，harness 嘅驗收基準全部要重算；(c) LD4 嘅「No Settings UI」先例部分亦來自同一批未 commit 檔。
**證據**：`git status --porcelain | wc -l` = **118**；`git show HEAD:…/logic/DailyTokenUsage.java` → `fatal: … exists on disk, but not in 'HEAD'`；`git log --oneline -1` = `3f366d7`。
**Flip condition**：plan 寫死「harness 依賴嘅 in-flight 批次（Batch B：`DailyTokenUsage`／`dailyTokenLimit`／SettingsRegistry V2）已 commit＋已部署」為前置條件，或改為唔依賴該 key。

---

## 反方攻擊落空項（老實報，唔准砌）

1. **LD4「唔加 settings key／唔加 lang key」＝冇違反 repo 慣例**（反方原打算打死，查完撤回）：`SettingsRegistry.java:45` 明寫「Fail-closed: only keys with a **set\* accessor** are listed」；`tests/check_settings_registry.py:26` `EXCLUDED: set[str] = set()`、`:131` 只驗 **registry → config 單向**；`PackAiConfig.java` 已有一批 key 明寫「**No Settings UI** — packai-client.toml [ui]」（L160/165/179/184/189/194/457/483/487/491/496）⇒ 唔加 registry／3 個 lang key 係既有做法，冇機器閘會紅。（前提：新 key 唔加 public setter；若加 setter 就要跟 :45 慣例。）
2. **hook 點存在**：`ClientSetup.java:64-67` 已註冊 `MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick)` 同 `::onLoggingIn` ⇒ plan 要嘅兩個 hook 都真存在 ✅。（順帶更正 plan 嘅隱含假設：`modBus.addListener` 只收 static method ref 係**唔成立**——`IEventBus.addListener` 收 method ref／lambda 都得，兩者喺同一個檔並用。）
3. **plan 嘅基準數字全部對**：`ls tests/check_*.py | wc -l` = **121** ✅；`find forge/1.19.2/src/test -name "*.java" | wc -l` = **49** ✅；`saves/` 恰兩個世界（`新的世界`、`新的世界 (1)`）✅；instance ID `name=AI_test_NFWC_DIM` ✅；`.hermes/` 已被 `.gitignore:41` 覆蓋 ⇒ `backup 到 .hermes/backups/2026-09-19_autotest/` 係安全慣例 ✅。
4. **token limit 真係喺 pipeline 內檢查**（唔止 GUI）：`AskService.java:308` 呼叫 `:563` ✅——plan 呢半句成立（死嘅係 default 值，見 F4）。

---

## 最貴嘅未知（要咩數據才解得開）

1. **安裝 build（Prism portable 8.0 / exe 2025-07-03）對 1.19.2 profile 逐字行為**：最便宜解法＝開一次 `-w`，讀 Prism log 嘅 java command line（或 MC log 嘅 `Completely ignored arguments`）。呢一條未解之前，LD1／LD8／A3 全部企唔穩。
2. **`WorldOpenFlows.loadLevel` 喺無 GUI 情況下會唔會彈互動框（`askForBackup`／pack-load 失敗）**：要一次真機 dry run 才知道 Plan B 可唔可以無人跑。
3. **`tests/check_ask_display_leak.py` 對 harness 產生嘅 trace／latest.log 嘅實際判定**（同一日跑，會唔會 return 2）——直接決定 A1「零新增紅」可唔可以維持。

## 反方 flip conditions（＝下一版 acceptance checklist，最多 3 項必修）

- **FC1**：刪走／改寫「`-w`＝最大風險消失」段；預設路徑改為 mod 側入世界（tick-gated，**唔可以**用 `LoggingIn`），或提供實測 log 證明 1.19.2 真收到 `--quickPlaySingleplayer`。
- **FC2**：成本守衛加 driver 側獨立上限（唔靠玩家 config）；補 driver 跑完的清理／還原步驟（含 crash／timeout 分支）。
- **FC3**：A5 改成可量測嘅行為不變斷言（trace schema diff＋跑前 backup／跑後還原），並明列跑一次之後邊幾個現有 check 會被影響。

---

## 裁決

方向（真機自動化、唔靠人手打字）**站得住**，而且 plan 有多項細心設計（負控意識、成本意識、守衛分層）——但**載重決定大面積死亡**：入世界機制（F1／F2）、入口錨點（F3）、成本上限（F4）、驗收完整性（F5）、啟動機制同焦點證據（F6）、殘留清理（F7）、還原清單（F8）。呢啲改動唔係補字，係要重寫 §2.0、§2.2 launch 段、§3 A1/A2/A5、§4 白名單。

**正方 2 : 反方 8**（未達 8:2 門檻 → 唔批准開工；餘項見 FC1–FC3）
