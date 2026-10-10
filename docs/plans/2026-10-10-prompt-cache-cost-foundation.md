# Plan：成本地基 — cache 命中量度 ＋ system prompt byte-stable（2026-10-10）

- 觸發：SK「而家噉樣做消耗太多 token，而且我相信有更好嘅方法」。實測：**82.4% token ＝每輪重送同一份 system prompt ＋ 15 個 tool schema**（固定 51,758 tok／ask，總 62,818；`docs/research/2026-10-10-cheap-capability-lookup-research.md` §29-31）。
- 業界研究（`docs/research/2026-10-10-harness-prompt-cache-comparison.md`）：**OpenClaw**（stable prefix／volatile suffix＋`cacheRead` 正規化＋live cache regression）、**Hermes**（`system_and_3`，4 個 cache breakpoint）、**DeepSeek Harness**（token meter）、**arXiv 2601.06007**（strategic block control 省 41–80% cost）。**冇一家人做 per-ask 歸屬** —— 全部係「逐 request 讀 provider 嘅 cache 計數器」＋「保持前綴 byte 一致」。

## 1. 親核現況（兩個真問題）

1. **睇唔到**：`logic/TokenUsage.java:20-29` `fromResponse` 只讀 `prompt_tokens`／`completion_tokens`／`total_tokens`，**掉咗 DeepSeek 嘅 `prompt_cache_hit_tokens`／`prompt_cache_miss_tokens`**；13 條真機 trace **零** usage 記錄。
2. **前綴唔穩（cache bomb）**：`logic/LlmClient.java:379-380` 逐條問題計 `style`／`rules`（`llmRules(lang, questOverride, questConflict, policy)` **5 分支**；`policy` 逐題計），而 `:478-481` 直接拼入 **system message（messages[0]，位置 0）** ⇒ 前綴喺**極早位置**就分叉，後面嘅 tool schema ＋ history **一齊 miss**。

## 2. 設計 A：cache 命中量度（**零行為改動**）

- `TokenUsage`：新增 `promptCacheHitTokens`／`promptCacheMissTokens`（DeepSeek 欄位名）；fallback：`prompt_tokens_details.cached_tokens`（OpenAI 相容）。
- `LlmClient` 兩個解析點（`:229-233` chatOnce、`:552-557` completeRound）各 log 一行：`Pack AI cache hit=<n> miss=<n> prompt=<n>`。
- `AskService` usage 行（`:606-613`）加 cache 欄；跨回合累加，同 `resetUsageAccumulator`（`LlmClient:47-49`）一齊清零。
- **唔改** prompt、唔改 model 行為、唔加 request。

## 3. 設計 B：system prompt byte-stable（**內容逐字保留，只換位置**）

- 把**逐問題變**嘅 `rules` 由 system message 搬去 **user payload 新 key `rules`**（同 `jei` 並排 → `LlmClient.java:440` 一帶；`user` 無條件 `GSON.toJson` 入 messages，`:494-497` ⇒ 兩種模式都送，已親核）。
- `style`（`llmStyle(code, offered)`）只保留唔隨問題變嘅部分；若含 per-question 成分，同樣搬去 `rules` key。
- 結果：同一 (**pack, lang, config**) 之下 system message 應該 **byte-identical** ⇒ 跨 ask 可以重用 system＋tools 前綴。
- **風險**：指令由 system role 搬去 user role ⇒ 有可能影響遵守度 ⇒ 必須用 V4 前後對照證明唔變差；如果變差，退回「只做設計 A」。

## 4. 落地位（全部要親核，漏一項即靜默或紅）

1. `logic/TokenUsage.java`（新欄位＋解析）。
2. `logic/LlmClient.java`（兩解析點、user payload 新 key、`completeRound` 簽名）。
3. `client/service/AskService.java`（usage 行）。
4. `logic/ReplyLang.java`（`rules` 拆出「穩定部分／逐題部分」）。
5. `tests/check_token_usage.py`（mirror 三欄 → 五欄）。
6. `tests/check_ask_capable_slim.py`（payload key 集）。
7. `tests/check_dual_tree_sync.py` `TREE_SPECIFIC`（forge-only 改動；`neoforge` PAUSED 只 WARN）。

**唔准郁**：卡落位、`AskReplyScrub`、tool 三張名單、lang 檔、system prompt 內其他文字、`neoforge` 樹。

## 5. 驗收標準

- **V1** `compileJava compileTestJava` RC=0；現有 harness 全綠。
- **V2** **真機（沙盒）**：連續問兩條問題 → log **真係見到** `cache_hit > 0`（首輪必 miss 屬機制正常）。呢個係「有冇白付」嘅**實錘**，之前 13 條 trace 一條都冇。
- **V3** 全部 `tests/check_*.py` 冇新增紅（baseline 以**當日實跑**為準）。
- **V4** **結構前後對照**：同一批題目（沙盒）設計 B 前／後答案並列，**唔可以變差**；另貼 trace 證明 system message 逐 ask byte-identical（搬走 `rules` 之後）。
- **V5** `git status` 只准預期檔；`neoforge/` 零改動。

## 6. 風險／還原

- 風險：中低（設計 A 零行為改動；設計 B 有指令位置改動 ⇒ V4 把關）。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\` 還原。
- 唔准：hot-copy jar、真 instance 自動部署、`git add -A`。

## 7. 執行

- 實作＝**cursor-agent**；Hermes 只做 plan／派工／親驗。
