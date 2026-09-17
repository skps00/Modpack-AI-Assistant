# Plan B v3 — 用量／成本顯示（UTC 日界 ＋ 今日已用／上限 ＋ 下次重置 ＋ DeepSeek 高峰提示）

> 狀態：**v3（2026-09-17 12:4x）** — **R2 已達 正方 8 : 反方 2（通過）**；再依 R2 餘下 5 條 flip conditions（全部屬「補規格」）寫入本版，即為實作版。
> SK 已拍板：日界 **保持 UTC**、要「已用／上限」＋「下次重置」＋「DeepSeek 高峰提示」（09-17 10:2x「y」）。

## §0 R2 → v3 補規格
1. **apiBase 判定規則寫死（原 v2 漏）**：`apiBase != null && apiBase.toLowerCase(Locale.ROOT).contains("deepseek")`（兼容 SK 現值 `https://api.deepseek.com`，冇 `/v1`）；**S2 加負控**：`https://api.openai.com/v1` → `false`。
2. **數字格式重用既有實作**：用 `TokenUsage.java:44-57 formatCount(...)`（`Locale.ROOT`，出 `400`／`1.2k`／`15k`；同 `AiAssistantScreen.java:770` 一致）→ summary 形如 `UTC 34k / 10k`（天然解決寬度）；**唔准**自創 `%,d` 逗號格式（`String.format` 唔帶 `Locale.ROOT` 會跟 JVM locale）。
3. **TZ／時鐘注入點指名**：`CostWindow.nextResetLocal(long nowEpochMillis, ZoneId zone)` 顯式傳入（**唔用** `ZoneId.systemDefault()`、**唔用** `TimeZone.setDefault`，避免 process-wide 副作用）。
4. **新 lang key 全名 + S7 掃描範圍**：
   - `packai.settings.usage.summary`（B-1，Settings row summary）
   - `packai.settings.usage.peak`（B-3，高峰提示）
   - `packai.reply.daily_token_limit_reset`（B-2，封頂訊息追加句）
   新玩家可見 key = 上述 3 個（＋ 各自 tooltip 如有）；S7 掃「本 plan 新增嘅**全部**玩家可見 key」（唔止 `packai.reply.*`）。
5. **刪走未核實斷言**（v2 §0.3 嘅「zh_tw 現有文案已 wrap 足 3 行」）→ 只保留已核實部分：`Entry` 冇動態 supplier（`SettingsRegistry.java:75,:115-116`）＋ `DESC_BODY_MAX_LINES=3`（`:56`）。
6. **S9 措辭對齊 B-1**：寫 `UTC 34k / 10k（下次重置 00:00 UTC ~ 本地 08:00）`；**定義 fallback**：`todayUsed` 讀唔到（檔缺／損壞）→ 顯示 `今日用量未能讀取`（新 key `packai.settings.usage.unreadable`），**唔准**靜默顯示 0。

## §1 目標
玩家一眼睇到今日用咗幾多、幾時回復、而家係唔係貴時段（成本意識，唔係限制）。

## §2 三個交付
- **B-1 顯示「今日已用／上限」**：唯一組裝點＝`SettingsScreenV2.valueSummary(...)`（`:464-480` 有 `llm.model` 特例先例；呼叫點 `:1037`）；`gameDir` 用 `mc.gameDirectory.toPath()`（先例 `:849-857`）；**唔改** `SettingsRegistry` getter（`:217-224`）。cache：memo 喺 **Screen**（每秒一次；唔落 logic 層）。
- **B-2 封頂訊息加「下次重置 00:00 UTC（本地 HH:MM）」**：`AskService.java:557-568` ＋ `ReplyLang.java:317-319` **加新 key 追加**（**唔郁** 現有模板——`tests/check_settings_c1.py:171-174` pin 2 個 `%s`）。
- **B-3 DeepSeek 高峰提示**：新 logic 純函數 `isDeepSeekPeak(Instant now, String apiBase, boolean usingLocal)`；`apiBase = PackAiConfig.API_BASE_URL.get()`（`PackAiConfig.java:20,:268-269`；**冇** `apiBaseUrl()` getter）；`usingLocal = PackAiConfig.uiUsesOllamaModel()`（`:1457-1466`）；窗口＝週一至五 **01:00-04:00／06:00-10:00 UTC**（DeepSeek 官方英文 pricing 註腳）；test seam `static Supplier<Instant> nowUtc`；顯示落點＝row summary（同 B-1）或封頂訊息。

## §3 影響面／還原
- 改：`client/gui/settings/SettingsScreenV2.java`、`logic/ReplyLang.java`＋`client/service/AskService.java`、`logic/CostWindow.java`（新）、3 lang（新增 key）、新 check ＋重生 `tmp-check.gradle`（唔 commit）。
- **唔改**：`logic/DailyTokenUsage.java`（日界／record／billable 凍結）、`config/PackAiConfig.java`（setter／clamp 留 Plan A）。
- 還原：`.hermes/backups/2026-09-17_settings_gui/`（settings 包）＋ 其餘檔以 git revert 正常處理。

## §4 驗收（S1–S9）
- **S1** 換日（必寫，唔准 hedge）：注入 `dayClock`（`DailyTokenUsage.java:35`）→ record 1000 → `todayUsed()==1000`；換日 → `0` → 今日紅（現有 `DailyTokenUsageCheck` 只有 5 case，冇換日）。
- **S2** `isDeepSeekPeak` 5 案例（注入 Instant）：Mon 02:00 UTC→true；Mon 12:00→false；Sat 02:00→false；`usingLocal=true`→false；**負控** `apiBase="https://api.openai.com/v1"`→false → 今日紅。
- **S3** `usageSummary(int used, int limit)` 離線斷言：`(34235,10000)` 含 `34k`／`10k`；`(34235,0)` 含「不限」→ 今日紅。
- **S4** 寬度：`formatCount` 輸出喺 `valueMax≈340px` 內唔被截 → 今日紅。
- **S5** 封頂訊息：注入固定 epoch＋`ZoneId` → 輸出含 `00:00 UTC` ＋本地 `08:00`（`Asia/Shanghai`）→ 今日紅。
- **S6** 唔動模板：`tests/check_settings_c1.py` 2-`%s` pin 仍綠 → 今日綠（regression）。
- **S7** 本 plan 新增全部玩家可見 key 零 `PLAYER_VISIBLE_FORBIDDEN`（`(src:)|(\.js\b)|(\.json\b)|(kubejs[/\\])`）→ 今日紅。
- **S8** `tests/check_*.py` 全綠（今日實跑 **116 PASS / 0 FAIL**）＋ settings 5 個 check 綠 → 綠。
- **S9** 真機：Settings 見到 `UTC 34k / 10k`；封頂訊息有重置時間；高峰時段見到提示 → 人手最終確認。

## §5 唔准做
- 唔准改 `DailyTokenUsage`、唔准改 clamp／setter、唔准動描述板（冇插槽）、唔准自創數字格式、唔准用 `TimeZone.setDefault`。

## §6 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| R1 | v1 | 3 : 7 ／ 4 : 6 | ❌ B2 撤回、落點／行號／API／引文修正 |
| R2 | v2 | **8 : 2** | ✅ 通過；餘 5 條補規格 → 已寫入 v3 |
