# Plan B v2 — 用量／成本顯示（UTC 日界確認 ＋ 今日已用／上限 ＋ 下次重置 ＋ DeepSeek 高峰提示）

> 狀態：**v2（2026-09-17 12:1x）** — 依 R1 反方 findings 重寫（subagent 3:7 ＋ cursor 4:6，兩邊一致）。
> 開工條件：再跑 review 達 **正方 ≥8 : 反方 ≤2**。SK 已拍板：日界 **保持 UTC**、要「已用／上限」＋「下次重置」＋「DeepSeek 高峰提示」（09-17 10:2x「y」）。

## §0 撤回項（v1 → v2，反方證據）
1. **撤回 B2「monotonic day key（防改鐘）」**：反方實測會造成**永久鎖**——`readMap`（`DailyTokenUsage.java:137-147`）收任何非空 key（例 `zzz`），一旦時鐘跳前一次，配額就鎖到真實日期追過為止；而且 `record()`／`overLimit()` 都經 `todayKey()` → 同 v1 自己寫嘅「唔改 `record()` 語義」條款對撞；配 B4 更會出**假承諾**（顯示「00:00 回復」但實際唔回復）。**價值亦近零**：上限可由 GUI 改、ledger 係玩家可刪可改嘅明文 JSON。→ **本版唔做防改鐘**。
2. **修正行號**：v1 寫「`:169` 已遍歷 keys」＝錯（`:169` 係 last-resort 單 key 寫入）；真正遍歷喺 `:190 trimToFit`（只喺寫入路徑）。
3. **修正落點**：v1 打算把「下次重置／高峰」寫入**描述板**——`SettingsScreenV2.java:1149-1152` 嘅描述板**只**讀靜態 `tooltipKey`（`Entry` 只有 `labelKey`／`tooltipKey`，冇動態 supplier）＋ `DESC_BODY_MAX_LINES=3`（`:56`，zh_tw 現有文案喺 ≤480 邏輯寬已 wrap 足 3 行）→ 動態文案一定被截。**改落 row summary／封頂訊息**。
4. **修正 API 名**：冇 `PackAiConfig.apiBaseUrl()` getter（`grep` = 0），只有 `PackAiConfig.API_BASE_URL.get()`。
5. **修正證據引文**：v1 引「已達每日 token 上限（34235 / 10000）。下一个 UTC 日…」＝繁簡混合、唔存在於任何 bundle；真機證據係 log 原文 `instances/AI_test_NFWC_DIM/minecraft/logs/latest.log` **17Sep2026 08:11:31.955** `Pack AI dailyTokenLimit blocked used=34235 limit=10000`（數字真，時間唔係 08:4x）。`zh_tw.json:261`／`zh_cn.json:257` 先係原文。

## §1 目標（不變）
玩家一眼睇到自己今日用咗幾多、幾時回復、而家係唔係貴時段——**成本意識**，唔係限制。

## §2 三個交付（v2 範圍）
- **B-1 顯示「今日已用／上限」**：`SettingsScreenV2.valueSummary(...)`（`:464-480` 已有 `llm.model` 特例先例）為**唯一組裝點**；`gameDir` 取法先例 `:850-857`（`mc.gameDirectory.toPath()`）；**唔改** `SettingsRegistry` getter（`:217-224` 只回 limit 字串、冇 gameDir）。
  - summary 格式：`UTC 34,235 / 10,000`（limit=0 → `UTC 34,235（不限）`）；用短格式／pin 最大寬（`labelCap = r.w*0.55` ＋ `fitWidth` 會截）。
  - cache：memo 喺 **Screen**（唔落 logic 層；否則污染封頂路徑）；更新時機＝**Settings 開頁期間每秒一次**（`valueSummary` 每 frame 被叫：`renderScreen:918 → renderEntryList:962 → :1037`）。
- **B-2 封頂訊息加「下次重置 00:00 UTC（本地 HH:MM）」**：由 Java 側 **append 新 key**（`ReplyLang.java:317-319` ＋新 key，前綴 **`packai.reply.*`**，`ReplyLang` 只載 `packai.reply.*`／`packai.label.*`，`:176-186`）；**唔郁** 現有模板（`tests/check_settings_c1.py:171-174` 硬 pin 2 個 `%s`）。
- **B-3 DeepSeek 高峰提示**：新 logic 純函數 `isDeepSeekPeak(Instant now, String apiBase, boolean usingLocal)`：
  - `apiBase` = `PackAiConfig.API_BASE_URL.get()`；`usingLocal` = `PackAiConfig.uiUsesOllamaModel()`（`:1458-1466`，避免 ollama／本機模式假響）；
  - 窗口＝週一至五 **01:00-04:00 / 06:00-10:00 UTC**（DeepSeek 官方英文 pricing 註腳）；
  - **test seam**：`static Supplier<Instant> nowUtc`（新 logic 類自己的 seam；**唔改** `DailyTokenUsage`）；
  - 顯示落點＝row summary（同 B-1 同一切換點）或封頂訊息。

## §3 影響面／還原
- 改：`client/gui/settings/SettingsScreenV2.java`（summary switch）、`logic/ReplyLang.java` ＋ `client/service/AskService.java`（B-2 文案）、`logic/CostWindow.java`（新，B-3 純函數＋seam）、3 lang（新 key）、新 check ＋重生 `tmp-check.gradle`（唔 commit）。
- **唔改**：`logic/DailyTokenUsage.java`（日界／record 語義凍結）、`PackAiConfig.java`（setter／clamp 唔郁——Plan A 負責數字輸入）。
- 還原點：同 Plan A §4（settings 包 untracked → timestamped copy＋md5）。

## §4 驗收（S1–S9）
- **S1** 換日：注入 `dayClock`（`DailyTokenUsage.java:35`）→ record 1000 → `todayUsed()==1000`；換日 → `0`（**必寫，唔准 hedge**；現有 `DailyTokenUsageCheck` 冇呢個 case）→ 今日紅（需補 case）。
- **S2** 純函數 `isDeepSeekPeak`：注入 `Instant` 三案例（Mon 02:00 UTC→true；Mon 12:00→false；Sat 02:00→false）＋ `usingLocal=true`→false → 今日紅（函數未存在）。
- **S3** summary 格式：離線斷言純函數（抽 `static String usageSummary(int used, int limit)`）：`(34235,10000)→"UTC 34,235 / 10,000"`、`(x,0)→"…（不限）"` → 今日紅。
- **S4** summary 寬度：斷言最大寬內唔被截（`listW=426`／`valueMax≈340px` 場景）→ 今日紅。
- **S5** 封頂訊息：斷言輸出含「00:00 UTC」＋本地時間（注入 TZ）→ 今日紅。
- **S6** 唔動模板：`tests/check_settings_c1.py` 2-`%s` pin 仍綠（新 key 另加）→ 今日綠（regression）。
- **S7** 新字串零 `PLAYER_VISIBLE_FORBIDDEN`：**只掃本 plan 新增 `packai.reply.*` key**（唔掃既有 tooltip——`lang/zh_tw.json:177` 本身含 `packai-usage.json`）→ 今日紅。
- **S8** `tests/check_*.py` 全綠（今日實跑 **116 PASS / 0 FAIL**）＋ settings 5 個 check 全綠 → 綠。
- **S9** 真機：SK 開 Settings 見「今日（UTC）已用／上限」；封頂時見到重置時間；高峰時見到提示 → 人手最終確認。

## §5 唔准做／唔聲稱
- 唔准改 `DailyTokenUsage`（日界／record／billable 語義凍結）；唔准改 clamp／setter。
- 唔准把動態文案塞入描述板（冇插槽，見 §0.3）。
- 唔准用「今日」唔標 UTC（UTC+8 玩家早場會誤解）。

## §6 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| R1 | v1 | 3 : 7（subagent）／4 : 6（cursor 第三評審） | ❌ 未過；B2 撤回、落點／行號／API／引文全部修正 |
| R2 | v2（本檔） | 待跑 | — |
