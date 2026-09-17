# Plan B — 用量／成本顯示批次（UTC 日界確認 ＋ 今日已用／上限 ＋ 下次重置 ＋ 防改鐘 ＋ DeepSeek 高峰提示）

> 狀態：**v1 草稿（2026-09-17 11:2x）** — 未 review（等反方 R1）。開工條件：反方 review 達 **正方 ≥8 : 反方 ≤2**。
> SK 已拍板（09-17 10:2x「y」）：① 日界保持 **UTC** ② 加「今日已用／上限」＋「下次重置」 ③ 日界**只准前進** ④ **加** DeepSeek 高峰提示。

## 0. 目標（一句）
玩家一眼睇到「**今日用咗幾多、幾時回復、而家係唔係貴時段**」——目標係**成本意識**（SK 原話：the limit is for player to know their token cost），唔係加限制。

## 1. 現況（全部有錨點）
- 日界寫死 **UTC**：`logic/DailyTokenUsage.java:35`（`LocalDate.now(ZoneOffset.UTC)`）、`:106-107`（`todayKey()`）；讀 `:51-…`（`todayUsed`）、寫 `:63-…`（`record`）、舊日修剪 `:169`。
- `0 = 不限`：`config/PackAiConfig.java:321-330`（`"Soft daily LLM token budget (prompt+completion). 0 = unlimited."`、ledger `config/packai-usage.json` UTC day）；`DailyTokenUsage.overLimit :44-49`（`limit <= 0` → 永不禁）。
- 封頂行為：`client/service/AskService.java:558-567`（讀 `limit`／`used` → log「Pack AI dailyTokenLimit blocked used={} limit={}」→ 回 `ReplyLang.dailyTokenLimitReached(...)`）。真機（09-17 08:4x）見到嘅訊息：`已達每日 token 上限（34235 / 10000）。下一个 UTC 日才會恢復，或在設置中提高上限。` → **冇講幾時回復**。
- Settings 行：`client/gui/settings/SettingsRegistry.java:217-223`（`llm.dailyTokenLimit`，getter `Integer.toString(PackAiConfig.dailyTokenLimit())`）→ 只顯示值，**冇顯示今日已用**。
- 業界查證（本日研究，全部官方文件）：OpenAI usage dashboard／Costs＋Usage API＝**UTC**；OpenRouter `usage_daily`＝current **UTC day**、key limit「midnight UTC」；Anthropic Usage API＝UTC；**DeepSeek 官方英文 pricing footnote**：「Peak hours are **01:00 - 04:00 and 06:00 - 10:00 UTC**, Monday through Friday」（中文版用北京時間表述同一窗口）。

## 2. 設計

### B1 顯示「今日已用 / 上限」
- 喺 `llm.dailyTokenLimit` 行嘅 summary（右邊，跟 Plan A 嘅 `ROW_RIGHT_INSET` 對齊）顯示：`今日 34,235 / 10,000`；`limit == 0` → `今日 34,235（不限）`。
- 更新時機：Settings 開頁、Ask 完成後（**唔做** per-frame 讀檔）。
- 讀取策略：`todayUsed()` 每次讀檔（64 KiB cap）→ 加 per-second 記憶（同一秒唔重讀），避免 render 期間重複 IO。

### B2 下次重置（只做顯示，唔改日界）
- 描述板（Plan A 之後係唯一長文案出口）＋封頂訊息加：`下次重置：00:00 UTC（本地 08:00）`。
- 本地時間由 `ZoneId.systemDefault()` 換算（同一 `Instant`）；**唔准**因此改 `todayKey()` 嘅日界。

### B3 防改鐘（monotonic day key）
- `todayKey()` 改為 `max(usage 檔已記錄最大 day, 今日 UTC)`：系統時鐘／時區倒退時**唔會**多送額度，亦唔會另開新 key。
- 實作：新增讀取「已記錄最大 day」（`:169` 已遍歷 keys，重用）；**唔准**改 usage 檔 schema／key 格式。
- 邊緣：全新檔（無 keys）→ 用今日 UTC；檔損壞／讀唔到 → 用今日 UTC 並照舊 fail-open（唔准 crash）。

### B4 DeepSeek 高峰提示
- 條件（兩者同時）：`apiBaseUrl` 含 `deepseek`（case-insensitive）＋ UTC 時間落喺高峰窗（**週一至五 01:00-04:00、06:00-10:00 UTC**）。
- 顯示位置：Settings 描述板 ＋ 封頂訊息；文案：`而家係 DeepSeek 高峰時段（價錢較高）`。
- 其餘 provider／其他時間 → 唔顯示（零噪音）。

### B5 文案（3 lang：en_us／zh_cn／zh_tw）
- 新增 4 組：`今日已用／上限`、`（不限）`、`下次重置（UTC ＋ 本地）`、`DeepSeek 高峰`。
- 硬規則：新字串一律唔准含檔名／行號（`logic/JsObtainSites.PLAYER_VISIBLE_FORBIDDEN_RE`，同 packai 既有規矩）。

## 3. 驗收（S1–S9）

| # | 斷言 | 今日 |
|---|---|---|
| S1 | `DailyTokenUsage` harness：注入 day=`2026-09-17` → `record(1000)` → `todayUsed()==1000`；注入 `2026-09-18` → `todayUsed()==0` | 綠（若有覆蓋）／如無就 **紅** |
| S2 | **monotonic**：注入時鐘倒退（`2026-09-16`）→ `todayUsed()` 仍係 `2026-09-17` 嘅數（唔歸零、唔開新 key） | **紅** |
| S3 | usage 檔格式不變（key＝`yyyy-MM-dd`、唔加新欄位；`FILE_MAX_BYTES` 64 KiB 行為不變） | 綠（regression） |
| S4 | Settings summary 文字：注入 usage → 顯示 `今日 34,235 / 10,000`；`limit==0` → `今日 34,235（不限）` | **紅** |
| S5 | `ReplyLang.dailyTokenLimitReached` 輸出含 `00:00 UTC` 及本地時間（注入固定 TZ 測） | **紅** |
| S6 | 高峰判定：Mon 02:00 UTC → 顯示；Mon 12:00 UTC → 唔顯示；**Sat 02:00 UTC → 唔顯示**；provider 非 deepseek → 唔顯示 | **紅** |
| S7 | 新字串全部 `PLAYER_VISIBLE_FORBIDDEN_RE` 零命中 | **紅** |
| S8 | python 閘全綠（`tests/check_*.py`，含 settings registry／3 lang 對齊／`check_settings_c1.py`） | 綠（regression） |
| S9 | 真機（SK）：Settings 見到「今日已用／上限」；封頂訊息有「下次重置」；高峰時段見到提示 | 人手 |

## 4. 影響面／還原
- 改：`logic/DailyTokenUsage.java`、`client/service/AskService.java`（文案組裝）、`client/gui/settings/SettingsRegistry.java`＋`SettingsScreenV2.java`（summary 文字）、`logic/ReplyLang.java`、3 個 lang 檔。
- 無新 config key（沿用 `llm.dailyTokenLimit`）；**還原** = `git revert` 單 commit；jar 由 Hermes 用 `mc_mod_deploy_jar.py --target packai` 部署。
- ⚠️ **同 Plan A 共用** `SettingsScreenV2.java`／`ReplyLang.java`／3 lang → 兩張 plan **唔可以同時**派工（會撞檔）。建議次序：**Plan A → Plan B**。

## 5. Review 狀態
- R1：**未跑**（反方）。上限 3–4 輪；未達 8:2 停手交 SK。

## 6. 已知脆弱位
- `todayUsed()` 讀檔頻率（render 期）→ B1 已定 per-second 記憶；實作要量真機影響。
- DST／半小時時區（如印度 UTC+5:30）→ 用 `ZoneId.systemDefault()` 處理，唔准自己加減秒數。
- 高峰窗係 **UTC 週一至五**：玩家本地可能係週六 → 判定必須用 UTC 嘅 day-of-week（唔准用本地）。
- 唔准因為顯示用途而改動 `record()`／`billable()`（成本計算語義凍結）。
