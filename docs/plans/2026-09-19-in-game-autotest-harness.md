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

## 2. 設計（三件嘢；v2 按實測修正）

### 2.0 入世界：**用 Prism 自己嘅 CLI（實測支援）** ✅
```
prismlauncher.exe -l "<instance ID>" -w "<world>"
```
`--help` 實測有 `-l/--launch <instance>`、`-w/--world <world>`。⇒ **唔需要** mod 側自動載入世界（原本設計嘅最大風險消失）。
- ⚠️ 待實測假設：1.19.2 冇官方 quick-play，Prism 嘅 `-w` 可能靠自己機制（或靜默無效）→ **第一步先做一次 dry test**（開 game 睇有冇入到世界）；若無效 → fallback＝mod 側 `onLoggingIn` 後自動 `loadLevel`（保留為 Plan B）。
- 本機存檔：`新的世界`、`新的世界 (1)`（`saves/` 兩個）。**用邊個要 SK 講**（我建議用 `新的世界 (1)`，或者我另開一個乾淨測試世界）。

### 2.1 Mod 側：`logic/AutoTestHarness.java`（**dormant by default**）
- **觸發**：`config/packai/autotest.txt` 存在，且**第一行必須係 magic header `#packai-autotest v1`**（防隨機檔誤觸）。
- ⚠️ **守衛設計更正（v1 寫錯咗）**：原設計用 `FMLEnvironment.production == false`；但**部署去 Prism instance 嘅 jar 係 production 環境**，即係嗰個守衛會令 harness **永遠唔行** → 改成：
  ① `autotest.enabled=true`（config，**預設 false**）② magic header 檔案存在 ③（可選）世界名符合清單。
  - **代價（老實講）**：即係正式版 jar 會帶住呢個沉睡設施。風險評估：預設關、只讀本機檔、只寫本機結果、**零網絡**、唔會問玩家任何嘢 → 濫用面近乎零；唔加 settings UI key、唔加 lang key（避開兩樹 parity 閘）。
  - 若 SK 要求「連沉睡都唔准入正式版」→ 另一條路＝用 gradle build flag（`-Ppackai.autotest`）編譯期剔走；成本較高、要改 build.gradle，另案。
- **流程**：世界載入後 → 逐條問題經**同 GUI 一樣嘅入口**（`AskService.beginAsk`）→ 收集（問題／facts／模型原文／最終顯示／卡片）→ 寫 `<instance>/packai/autotest/<timestamp>.jsonl` → 寫 `DONE` 標記 → 「自動關 game」由 driver 決定（`autotest.quit`）。
- **上限**：≤20 條／單條 120s／總 15 分鐘；超時寫 `TIMEOUT` 即收工。
- **絕對唔准**：改 trace 事件名／欄位語義、改 prompt／卡行為、動 `neoforge`。

### 2.2 Hermes driver：`%LOCALAPPDATA%\hermes\scripts\mc_autotest_run.py`
- 前置檢查：① 冇 java 進程 ② instance 冇開 ③ 讀 SK activity gate（`playing/using` → **拒絕開**）。
- 流程：寫 `autotest.txt` → 用 `run_hidden.vbs` 零彈窗開 `prismlauncher.exe -l "AI_test_NFWC_DIM"`（**背景、唔搶焦點**，跟 AGENTS.md 規則）→ 等 `DONE`（有 timeout）→ 讀 JSONL → 關 game（如未自動關）→ 出報告（print + 存檔）。
- 結果唔含 secrets（同 trace 一樣只係遊戲內容）。

### 2.3 斷言層：`tests/check_autotest_results.py`
- 讀結果 JSONL，按每個 case 嘅規則判 PASS／FAIL（例如 fix A：木錘 → 恰好 1 張合成台卡、尾段冇「已隐藏」、冇簡體 miss 句；來源標籤：亞巴頓 → 「本包自加」）。
- 規則寫喺一個 JSON（case → 斷言），**唔准寫死喺 code**，方便加 case。

## 3. 驗收標準（開工前定稿）

### 2.5 成本守衛（**必要**，因為每個 case 都係真 LLM call＝真金白銀）
- 每次跑 **≤ 20 條**（硬上限）＋沿用玩家既有 `llm.dailyTokenLimit`（唔准繞過）＋跑之前讀 `scripts/ds_peak_hours.py`：**DS 高峰時段唔准跑重活**（SK 09-18 規則）。
- 每次跑之前 driver 印預估成本（case 數 × 每條大概 token）＋SK 可見；跑完寫實際用量入報告。

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

## 6. 待 SK 決定（v2 更新）

1. ~~批唔批做~~ → **SK 09-19 覆 `go` ＋「u test fix a also」＝批准，且 fix A 由 Hermes 自己驗** ✅
2. **測試世界**：用 `新的世界 (1)`？定叫我另開乾淨測試世界？（未答 → 我可以先用 `新的世界 (1)`）
3. **自動關 game**：跑完自動關（省電）／留住俾你睇？（預設：**留住**，因為你打機／用機時我根本唔會開）

## 7. v1 → v2 改動（實測後修正）

| # | v1 寫法 | v2 修正 | 依據 |
|---|---|---|---|
| 1 | mod 側自動入世界（`onLoggingIn` → 載入世界） | **改用 Prism CLI `-l <instance> -w <world>`**；mod 側載入留作 Plan B | `prismlauncher.exe --help` 實測有 `-w/--world` |
| 2 | 守衛用 `FMLEnvironment.production == false` | **改為 config `autotest.enabled`（預設 false）＋ magic header 檔** | 部署去 instance 嘅 jar 本身就係 production → 原守衛會令 harness 永遠唔行（設計錯） |
| 3 | 冇提部署次序 | **一次 build／一次 deploy 包含 fix A ＋ harness** | 兩者都要入同一個 jar，避免部署兩次 |
| 4 | 冇列世界 | 實查 `saves/` 有 `新的世界`、`新的世界 (1)` | 檔案列表實測 |

## 8. 第一個用途：Hermes 自己驗 fix A（SK 09-19 指派）

`autotest_cases.json` 第一批 case（＝fix A 驗收 S5，同 HANDOFF 一致）：

| case | 問題 | 斷言 |
|---|---|---|
| S5a | 手持／focus 木錘，問「木錘點嚟」 | 恰好 **1 張**「合成台」卡；卡相鄰有「怎麼來」文字；**尾段冇**「已隐藏／已被隐藏」字樣；答案含**有序合成**（唔准寫「無序」） |
| S5b | 問擬態（特製版） | 維持現狀＝老實講「未收錄」；**唔准**用空白框架配方頂替 |
| S6 | 問「下界合金背包點嚟」 | 卡與文字**同一結論**（鍛造台：鑽石背包＋下界合金錠）← fix A 之後第一批回歸 |

⇒ 呢三個 case 通過＝fix A 真機驗收完成，之後可以 bump 版本一次過 commit（跟 SK「等一批」嘅既有定案）。
