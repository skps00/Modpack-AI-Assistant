# 2026-09-19 — packai「真機自動測試」計畫 **v4**（縮 scope 版）

> 狀態：**計畫（未實作）**。範圍只限 `forge/1.19.2`（`neoforge` PAUSED）。
> 版本聲明：只適用 **MC 1.19.2 + Forge 43.x**（本機實測）。
> 演進：v1（config-gated 沉睡）→ v2（Prism `-w` 入世界）→ v3（dev 環境）→ **v4（反方 R1 判 2:8 後大幅縮 scope）**；R1 報告 `docs/plans/reviews/2026-09-19_autotest-harness-plan-R1-opposing.md`。

## 0. 一句話（v4）

**一條 dormant trigger ＋ 一個 driver**：dev 環境（已建）啟動時，如果見到觸發檔，就自動開指定世界、**用你 mod 已經有嘅 `/ai <問題>` 客戶端指令**逐條問（＝同玩家打字完全一樣嘅路徑），答案照舊寫入**現有嘅 trace**；driver 讀現有 trace 判 PASS／FAIL。**唔新增 collector、唔新增 config key、唔碰正式版**。

## 0b. Dev 環境（已建，實測）

| 項 | 結果 |
|---|---|
| dev game dir | `C:\Users\skps9\Documents\packai_dev_game`（live instance 完整副本：231 mods／3.5 GB） |
| `build.gradle` | `runs.client.workingDirectory` 支援 `-PpackaiDevGameDir=<path>`，**預設不變**；改前已備份（md5 一致） |
| gradle 語法 | `gradlew help` rc=0 ✓ |
| 煙測 | **待做**（要開一次 MC 窗口；等 SK 批；要確認 231 mod 全部載入） |

⇒ dev 環境成立 ⇒ **唔需要**「config-gated 沉睡設施」（正式版零殘留），亦**唔需要**依賴未 commit 嘅 config key（反方 F11）。

## 1. 反方 R1 戳穿嘅兩件事（我承認）

1. **Prism `-w` 對 1.19.2 係 no-op**（上游只喺 profile 有 `feature:is_quick_play_singleplayer` 才傳 `--quickPlaySingleplayer`；1.20+ 才有）→ v2 嘅「最大風險消失」係錯。
2. **`AskService.beginAsk` 根本唔存在**；而且「同 GUI 一樣嘅入口」唔成立（GUI 會帶 strip focus ＋ JEI pin）。**但**：`/ai <問題>` 客戶端指令**一直都存在**（`AiClientCommands.java:20-27`）＝現成、真實、非 GUI 入口。

## 2. 設計（v4；3 件，全部細）

### 2.1 觸發（mod 側，**只一個新檔** `logic/DevAutoTest.java`）
- 生效條件（全部要中）：`FMLEnvironment.production == false`（dev 環境）＋ `<gameDir>/packai/autotest.txt` 第一行係 `#packai-autotest v1`。
- 流程：喺**標題畫面**（`onClientTick` 見到 `screen instanceof TitleScreen`）→ `mc.createWorldOpenFlows().loadLevel(titleScreen, <存檔夾名>)`（1.19.2 真存在，已由反方用 javap 核實）→ 世界載入後逐條讀問題 → **送 `/ai <問題>`**（＝玩家路徑）→ 每條之間隔固定 tick。
- 上限：≤20 條／單條 120 秒／總 15 分鐘；超時停手並寫 marker。
- **唔做**：唔自寫結果 JSONL（trace 已有，反方 F9）、唔加 config key、唔加 settings UI、唔加 lang key、唔碰 prompt／卡／trace 格式。
- ⚠️ 已知互動風險（反方標為最貴未知）：`loadLevel` 可能彈「備份提示／內建包載入失敗」對話框 → **第一步做 dry run 確認**；若會彈 → 改用「driver 只開 game，靠 SK 一句手動入世界」或研究繞過。

### 2.2 Driver（`%LOCALAPPDATA%\hermes\scripts\mc_dev_autotest.py`）
- 前置：① 冇 game 進程 ② activity gate 唔係 `playing/using`（實作要用 `bg_launch.py` 同一套讀法）③ 讀 `ds_peak_hours.py`（高峰唔跑）。
- 流程（**finally 一定做清理**，反方 F7）：
  1. **備份** `<devgame>/packai/trace/` ＋ `<devgame>/logs/latest.log`（因為跑 20 條會令舊 trace 被輪替、latest.log 被覆蓋 → 反方 F5）
  2. 寫 `autotest.txt`（含 magic header ＋ 世界夾名 ＋ 問題清單）
  3. 開 dev client：`gradlew runClient -PpackaiDevGameDir=…`，經 `bg_launch.py --minimized` 等價方式（**唔用** `run_hidden.vbs`——本機冇呢個檔，反方 F6）；MC 窗口出現後**獨立量度**前景（唔靠 bg_launch 自報）
  4. 等 marker（timeout 15 分鐘）
  5. 讀 trace ＋ `latest.log` → 出報告（每 case PASS／FAIL ＋ 證據行）
  6. **finally**：刪 `autotest.txt`、關 game、還原 trace 備份
- **私隱**：driver 唔讀、唔打印任何 config TOML（含 API key）。

### 2.3 斷言（`tests/check_autotest_results.py`）
- 讀結果（trace ＋ latest.log），按 `autotest_cases.json`（case → 問題文字 → 斷言）判 PASS／FAIL。
- 首批 = fix A 驗收（見 §5）。

## 3. 成本守衛（反方 F4 修正）

- 硬上限 **≤20 條／次**（自己數，唔靠 config）。
- `llm.dailyTokenLimit` **唔可以當護欄**：`DailyTokenUsage.DEFAULT_LIMIT = 0`，而 `AskService.dailyTokenBlockOrNull` 遇 `limit<=0` 直接放行（反方核實）→ driver 跑前跑後讀 usage 檔比對，超預算即停。
- DS 高峰：跑前 + **每條之前**都查（跨邊界）。

## 4. 驗收標準（v4）

| # | 條件 |
|---|---|
| A1 | `compileJava compileTestJava` BUILD SUCCESSFUL；49 harness 綠；`tests/check_*.py` **零新增紅**（**跑完真機後要重跑**：因為 trace／latest.log 被換，`check_ask_display_leak.py` 可能返 2 → 用備份還原後重跑，反方 F5） |
| A2 | 負控：`production=true`（正式 jar）／冇 magic header → **完全唔行**；觀察通道 = `latest.log` 冇 dev-autotest marker 行 且 `packai/trace` 冇新增 |
| A3 | 真機：≥3 條 case（對齊 §5）跑完，trace 有對應問答，判 PASS |
| A4 | 搶焦點：MC 窗口出現後**獨立量度**前景；有搶 → 記錄並還原 |
| A5 | 清理：跑完（含 crash／timeout）`autotest.txt` 已刪、trace 已還原、冇殘留 flag |
| A6 | 唔改 production：`neoforge` 零改動、lang 零改動、prompt／卡／trace schema 零改動 |

## 5. 首批 case（＝fix A 驗收）

| case | 問題 | 斷言 |
|---|---|---|
| S5a | 「木錘點嚟」 | 恰好 1 張「合成台」卡；有「怎麼來」文字；**冇**「已隐藏」；寫**有序**合成（唔准「無序」） |
| S5b | 「擬態點嚟」（特製版） | 維持老實「未收錄」，唔准用空白框架頂替 |
| S6 | 「下界合金背包點嚟」 | 卡同文字**同一結論**（鍛造台：鑽石背包＋下界合金錠） |

## 6. 還原點與白名單

- 開工前：`.hermes/backups/2026-09-19_autotest/`（逐檔 copy＋md5）。
- 白名單（**全部新檔要列齊**，反方 F8）：新 `logic/DevAutoTest.java`、改 `client/ClientSetup.java`（掛一個 tick 檢查）、**新** `tests/check_autotest_results.py`、**新** `tests/autotest_cases.json`、`docs/plans/*`（文件）。**唔改** `PackAiConfig`（v4 唔用 config key）。

## 7. Review 與流程

反方 review 到 8:2（上限 3–4 輪，每輪要有實質修改）→ 派 cursor 實作 → Hermes 親驗（compile／49／121／負控）→ 兩輪 code review → 真機自動跑（driver）→ 報告 SK。

## 8. 待 SK 決定

1. **煙測窗口**：幾時可以開一次 MC 窗口（約 1–3 分鐘）確認 231 mod 載入到？（最重要，因為整個方案靠佢）
2. 測試世界：`新的世界 (1)`？（dev game dir 係副本，玩壞都唔影響你原存檔）
3. 跑完自動關 game？（建議：關，因為 dev 環境只用嚟測）

## 9. R1（11 條）→ v4 回應

| 反方 | v4 點改 |
|---|---|
| F1 CRITICAL（Prism `-w` 對 1.19.2 no-op） | 刪走 Prism `-w`；改用 mod 側喺標題畫面 `loadLevel`（1.19.2 API 已核實存在） |
| F2 CRITICAL（`LoggingIn` 之後才入世界＝雞蛋問題） | 改用**標題畫面**觸發（唔用 `LoggingIn`） |
| F3 HIGH（`beginAsk` 唔存在＋「同 GUI 一樣」唔成立） | 改用**現成 `/ai <問題>` 客戶端指令**（真實玩家路徑） |
| F4 HIGH（token 護欄係空） | §3：自帶 ≤20 條硬上限＋跑前後比對 usage；DS 每條前查 |
| F5 HIGH（trace／latest.log 被換 → 假零紅） | §2.2 加「跑前備份 trace＋latest.log、跑後還原後重跑閘」 |
| F6 HIGH（`run_hidden.vbs` 唔存在；focus 量度有盲區） | 刪走該檔引用；改用 `bg_launch.py` 等價路徑；**獨立**量度前景 |
| F7 MED-HIGH（冇清理） | §2.2 finally 清理＋唔讀 TOML |
| F8 MED（白名單／負控／數量唔齊） | §6 白名單補齊；A2 刪空轉項＋寫死觀察通道；A3 對齊 3 條 case |
| F9 MED（collector 重複建設） | **砍走自寫 JSONL**；只讀現有 trace |
| F10 LOW（節號／路徑／retention） | 統一 `<gameDir>/packai/`；本版已重排節號 |
| F11 MED-HIGH（依賴未 commit 嘅 key） | **v4 完全唔用 config key**（dev-only），唔依賴未 commit 批次 |
