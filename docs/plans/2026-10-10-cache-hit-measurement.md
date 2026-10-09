# Plan：LLM cache 命中量度（2026-10-10，SK 揀「1+4」）

- 觸發：SK「而家噉樣做消耗太多 token，而且我相信有更好嘅方法」→ 研究報告 §1 量到 **82.4% token 係每輪重送同一份 system prompt＋tool schema**，而 DeepSeek context caching 預設已開（hit US$0.0028/M vs miss US$0.14/M＝**50 倍**）；但 packai **睇唔到**有冇中 cache。
- 目標（一句）：令 packai **每次 LLM 回合都記低 cache 命中／未命中 token**，用真數據答「我哋係唔係白白重複付錢」。
- 範圍：**只加量度**。唔改 prompt、唔改輪數、唔改價錢邏輯、唔改帳本格式。

## 1. 現況（已親核）

| 事實 | 證據 |
|---|---|
| usage 只讀 3 個欄位 | `logic/TokenUsage.java:20-29`（`prompt_tokens`／`completion_tokens`／`total_tokens`） |
| 每個 LLM 回合都有 usage 可讀 | `logic/LlmClient.java:229-233`（chatOnce）＋`:552-556`（native tools 路徑） |
| 每次 ask 只 log 一行總數 | `client/service/AskService.java:606-613`（`Pack AI usage billed=…`） |
| trace 完全冇 usage | 13 個真 trace（`AI_test_NFWC_DIM\minecraft\packai\trace\ask-*.jsonl`）**0 個**含 `usage`／`cache` |
| DeepSeek 欄位名（官方） | `https://api-docs.deepseek.com/guides/kv_cache`：`prompt_cache_hit_tokens`／`prompt_cache_miss_tokens` |

## 2. 改動（最小 diff）

1. **`logic/TokenUsage.java`**：加 `public static int[] cacheTokens(JsonObject root)`
   - 讀 DeepSeek：`usage.prompt_cache_hit_tokens`、`usage.prompt_cache_miss_tokens`。
   - 讀 OpenAI 風格 fallback：`usage.prompt_tokens_details.cached_tokens`（存在就當 hit）。
   - 讀唔到 → `{-1, -1}`（唔會影響現有 3 欄位／帳本）。
   - **唔改 record 簽名**（避免波及 `DailyTokenUsage`／tests／call sites）。
2. **`logic/LlmClient.java`**：兩個解析點之後各加一行
   `PackAiMod.LOGGER.info("Pack AI cache hit={} miss={} prompt={} completion={} base={}", …)`
   —— permanent debug log（SK 規則：診斷要實錘）。
3. **`client/service/AskService.java`**：`Pack AI usage billed=…` 尾加 `cache_hit=…`（該 ask 累計）。
   - 需要 `LlmClient` 暴露累計 cache（加一個 `cumulativeCacheHit()`，同樣**唔改 record**）。

## 3. 驗收標準（先寫好，做完逐項跑）

- **V1** 合成 JSON（含 `prompt_cache_hit_tokens`／`prompt_cache_miss_tokens`）→ `cacheTokens()` 回正確值；缺欄位 → `{-1,-1}`（負控）。
- **V2** 合成 JSON（OpenAI 風格 `prompt_tokens_details.cached_tokens`）→ 當 hit。
- **V3** harness：`runAskMarkerIntegrityCheck` 等現有 harness **60/60 綠**；`compileJava compileTestJava` RC=0。
- **V4** python 靜態閘：只准「冇新增紅」（baseline＝125 檔 1 已知紅）。
- **V5** 真機（沙盒）：跑一次真 ask → `latest.log` 出現 `Pack AI cache hit=…` 行數＝LLM 回合數；用嗰行算「hit／(hit+miss)」＝**cache 命中率** → 直接答 SK「有冇白付」。
  - 若沙盒／jar 換唔到（java 進程佔住）→ **照實講未量到**，唔准當成功。

## 4. 風險／還原

- 風險：**極低**——純新增 log ＋ 新增 static 讀取；唔觸碰卡落位／AskEngine capable 清空語意／帳本／prompt。
- 還原：`git revert <commit>`（單一 commit）＋ 由 `%TEMP%\deploy_backup_*\` 還原舊 jar。
- 唔准郁：`neoforge` 樹、`AGENTS.md`、帳本檔格式。

## 5. 執行

- 實作＝**cursor-agent**（packai 鐵則）；Hermes 只做 plan／派工／親驗。
- 落點同 keybind v5 一樣係細 slice，一次過驗收。
