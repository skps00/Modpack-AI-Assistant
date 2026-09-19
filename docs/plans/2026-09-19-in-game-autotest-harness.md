# 2026-09-19 — packai「真機自動測試」（in-game autotest harness）計畫 v1

> 狀態：**計畫（未實作）**。範圍只限 `forge/1.19.2`（`neoforge/1.21.1` PAUSED，唔准郁）。
> 目標（SK 09-19）：**令 Hermes 可以自己跑真機測試**——唔需要 SK 打字、唔需要人手記結果。
> 版本聲明：只適用 **MC 1.19.2 + Forge 43.3.5**（本機實測；1.20 才有官方 quick-play 參數）。

## 0. 一句話

寫一個 **dev-only** 嘅自動問答鉤子：見到 `config/packai/autotest.txt`（問題清單）就自動入指定世界、逐條行**真嘅 Ask pipeline**（真 JEI／真 registry／真模型）、寫結果 JSONL＋完結標記；Hermes 用一支 driver 腳本開 game（背景、零搶焦點）→ 讀結果 → 逐條判 PASS／FAIL → 關 game → 出報告。

## 1. 為咩要（現況痛點，實測）

- 現時真機驗收＝**人手**：SK 開 game、打字問、Hermes 讀 trace（今日 fix A 就卡咗半日等呢一步）。
- 已有 121 個 python 靜態閘＋49 個 Java 測試＋1 個 mirror 煙測（`tools/card_placement_test.py`），但**全部都唔會執行真 pipeline**（mirror 會漂移）。
- 今日 trace 實錘：文字路徑同卡路徑對同一件物品可以講唔同話（SB 背包），呢類 bug 只有真機 trace 睇得到。

## 2. 設計（三件嘢）

### 2.1 Mod 側：`logic/AutoTestHarness.java`（**dev-only**）
- **觸發**：`config/packai/autotest.txt` 存在（一行一條問題）。**檔案驅動，唔加 settings UI key、唔加 lang key**（避免撞兩棵樹 lang parity 閘）。
- **守衛（三重，任何一個唔符即完全唔行）**：① `FMLEnvironment.production == false`（正式版 jar 永遠唔會行）② 檔存在 ③ `autotest.enabled`（config，預設 false；只為咗俾我關）。
- **流程**：`ClientSetup.onLoggingIn`（已存在嘅鉤）→ 等世界載入（tick 計時器）→ 逐條問題經**同 GUI 一樣嘅入口**（`AskService.beginAsk`）行 → 收集（問題／facts／模型原文／最終顯示／卡片）→ 寫 `<instance>/packai/autotest/<timestamp>.jsonl` → 寫 `DONE` 標記 → 可選 `autotest.quit=true` 自動關 game。
- **上限**：最多 20 條／次、每條 timeout 120s、總預算 15 分鐘；超時即寫 `TIMEOUT` 並收工（**唔准**無限等）。
- **絕對唔准**：改 trace 事件名／欄位語義、改 prompt／卡行為、喺 production 有任何效果。

### 2.2 Hermes driver：`%LOCALAPPDATA%\hermes\scripts\mc_autotest_run.py`
- 前置檢查：① 冇 java 進程 ② instance 冇開 ③ 讀 SK activity gate（`playing/using` → **拒絕開**）。
- 流程：寫 `autotest.txt` → 用 `run_hidden.vbs` 零彈窗開 `prismlauncher.exe -l "AI_test_NFWC_DIM"`（**背景、唔搶焦點**，跟 AGENTS.md 規則）→ 等 `DONE`（有 timeout）→ 讀 JSONL → 關 game（如未自動關）→ 出報告（print + 存檔）。
- 結果唔含 secrets（同 trace 一樣只係遊戲內容）。

### 2.3 斷言層：`tests/check_autotest_results.py`
- 讀結果 JSONL，按每個 case 嘅規則判 PASS／FAIL（例如 fix A：木錘 → 恰好 1 張合成台卡、尾段冇「已隐藏」、冇簡體 miss 句；來源標籤：亞巴頓 → 「本包自加」）。
- 規則寫喺一個 JSON（case → 斷言），**唔准寫死喺 code**，方便加 case。

## 3. 驗收標準（開工前定稿）

| # | 條件 |
|---|---|
| A1 | `compileJava compileTestJava` BUILD SUCCESSFUL；49 harness 全綠；`tests/check_*.py` **零新增紅** |
| A2 | 新增 harness case：`autotest.txt` 唔存在／`enabled=false`／production 模擬 → **三者都完全唔行**（負控） |
| A3 | 真機：寫 1 條已知問題（例如「木棍點嚟」）→ 跑一次 → 結果 JSONL 有問題／facts／顯示三段，driver 判 PASS |
| A4 | 全程**零搶焦點**（driver 自報 `focus_stolen` 實測值）；SK 用緊機時 driver 拒絕執行 |
| A5 | 唔改 production 行為：`neoforge` 零改動、lang 檔零改動、`shouldDropFrameCard`／prompt 零改動 |
| A6 | 上限生效：>20 條／單條 timeout → JSONL 寫 `TIMEOUT`／截斷，唔會 hang |

## 4. 風險與還原

- **風險**：① 自動開 game 會食 GPU／CPU（SK 打機時**禁止**：driver 先讀 activity gate）② 自動關 game 可能丟未存檔進度 → **預設只入指定測試世界**、`autotest.quit` 預設 false（由 driver 決定）③ dev-only 守衛寫錯會令正式版都跑 → 用三重守衛＋負控 A2 釘死 ④ 世界名係 SK 嘅存檔名 → plan 要 SK 提供（或我用一個新空白世界）。
- **還原**：code 改動＝白名單（`logic/AutoTestHarness.java` 新檔、`client/ClientSetup.java` 掛鉤、`config/PackAiConfig.java` 加 `autotest.*`）；開工前 backup 到 `.hermes/backups/2026-09-19_autotest/`；唔動 jar（要 build 先，由 SK 批）。
- **私隱**：結果檔只喺本機，唔上網。

## 5. Review 與流程

- 本 plan → 反方 review（門檻 8:2，上限 3–4 輪）→ 過閘才派 cursor 實作（Hermes 親驗＋兩輪 code review）。
- **實作前要 SK 答 2 樣**：① 批呢個 dev-only 鉤子（正式版零影響）② 測試世界名（或「幫我開個新世界做測試場」）。

## 6. 待 SK 決定

1. 批唔批做（`go`／`no`）。
2. 測試世界：現成存檔名／叫我開新世界。
3. 自動關 game：跑完自動關（省電）定留住俾你睇？
