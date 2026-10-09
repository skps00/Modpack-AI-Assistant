# 三個 harness 點解決「重複 payload 燒錢」＋對 packai 嘅啟示（2026-10-10）

> 觸發：SK「check how openclaw and deepseek harness and hermes solve it」。
> 全部有 URL；查證日 2026-10-10。親核部分註明「Hermes 親核」。

## 0. 一句總結

三家人做嘅係**同一件事**，而且做法高度一致：

1. **逐個 request 讀 provider 回嘅 cache 計數器**，正規化成 `cacheRead` / `cacheWrite`（或 token meter）並顯示／記錄；
2. **把 prompt 切成「穩定前綴 / 易變尾巴」**，令前綴逐 byte 一致（stable-first、volatile-last）；
3. **用 live regression test ＋ no-cache 對照組實測**命中；
4. **冇任何一家人做「per-ask／per-round 歸屬」** —— 佢哋量嘅係「cache 命中率」呢個 ratio，唔係「邊條問題、邊一輪」。

## 1. OpenClaw（openclaw/openclaw，MIT，OpenClaw Foundation）

- **`docs.openclaw.ai/reference/prompt-caching`**：*"OpenClaw normalizes provider usage into `cacheRead` and `cacheWrite` wherever the upstream API exposes those counters."* Usage summaries（`/status` 等）會顯示；live 值贏 fallback。
- **System-prompt cache boundary**（同頁）：system prompt 切開 **stable prefix** / **volatile suffix**；boundary **以上**（tool definitions、skills metadata、workspace files）**要保持 byte-identical**；**以下**（runtime timestamps、per-turn metadata）可以變而唔會令前綴失效。
- **Provider 差異逐個處理**：Anthropic → `cache_control` ephemeral（`short`＝5 min／`long`＝1 h），native 回 `cache_read_input_tokens` / `cache_creation_input_tokens`；OpenAI → 自動 cache ＋ 送 `prompt_cache_key` **穩定 routing**，命中由 `usage.prompt_tokens_details.cached_tokens` 讀；Gemini → 自動管理 `cachedContents`。
- **CLI harness**：JSONL usage parser 認多種欄位名（包括單純 `cached` → map 做 `cacheRead`），冇 input 欄位就 `input_tokens - cached` 推。
- **TTL 管理**：可選 **cache-ttl pruning**（TTL 過期後 prune session 並重設 cache window）；**heartbeat 保溫**（interval 略短於 TTL，例：TTL 1 h → heartbeat 55 min）。
- **live cache regression gate**（同頁）：一條 gate 蓋「repeated prefixes／tool turns／image turns／MCP-style tool transcripts」＋ **Anthropic no-cache control**（負控）。
- **`diagnostics.cacheTrace`**：默認 `false`；開咗寫 `$OPENCLAW_STATE_DIR/logs/cache-trace.jsonl`（含 messages／prompt／system prompt）。
- **`docs.openclaw.ai/reference/token-use`**：`/usage tokens` 顯示 turn token/**cache** details；`messages.usageTemplate` 可插 cache 欄位；並且**刻意分開**「provider usage 記帳（含 cached input、多輪 tool loop）」同「live context snapshot」——前者做 cost/telemetry，後者做 context 顯示。

## 2. DeepSeek Harness（`dsh`，DeepSeek AI，MIT，architecture＝「everything is a plugin」，built on Cordis）

- **內建 token meter**：官方對比文（atlascloud.ai）列 dsh 嘅 token accounting ＝「Token meter with context pressure and breakdown projections」，TUI 有 per-session token/cost tracking。
- **持久化用量分析**：社群插件 **`dsh-token-usage`**（MIT）——GitHub discussion #1530「Proposal: integrate persistent token-usage tracking into DeepSeek Harness」（2026-08-14）。
- **社群實測**：`reddit.com/r/DeepSeek` 討論「how is the cache hit rate at DeepSeek Harness so [high]」——回應指 **DeepSeek 係 server-side、disk-backed KV prefix cache，用家報告 98–99% cache hit、跨數十億 token**。
- ⇒ 即係「高命中率」係 DeepSeek 嘅**常態**；命中率低＝**自己嘅 prompt 結構有問題**，唔係平台唔 cache。

## 3. Hermes（Nous Research，本家）

- **`agent/prompt_caching.py`**：Anthropic prompt caching，單一 layout **`system_and_3`** —— **4 個 `cache_control` breakpoints：system prompt ＋ 最後 3 條非 system message**，同一 TTL（5 m 或 1 h）。檔頭自述「reduces input token costs by ~75% on multi-turn conversations within a single session」。
- **`docs/developer-guide/prompt-assembly`**：刻意分開 **cached system prompt state** 同 **ephemeral API-call-time additions**，並明言呢個係「one of the most important design choices」，因為佢影響 token usage／**prompt caching effectiveness**／session continuity。Cached system prompt 分**三層階梯**（stable → identity(`SOUL.md`) → tool/mod 指引 → …）；**唔入 cache** 嘅有：`ephemeral_system_prompt`、prefill、gateway overlays、later-turn external recall（呢啲注入**當前 user message**）。
- 另有 **dual compression（context compression）＋ caching**：`website/docs/developer-guide/context-compression-and-caching.md`。
- PR #23828：**cross-session 1 h prefix cache**（Claude）——首次 turn input cost 可降 ~85–90%。

## 4. 學術與業界數據（呢條問題有人做過 benchmark）

- **arXiv 2601.06007**「Don't Break the Cache: An Evaluation of Prompt Caching for Long-Horizon Agentic Tasks」（Lumer 等，v1 2026-01-09、v2 2026-01-31）：
  - 3 個 provider（OpenAI／Anthropic／Google）、DeepResearch Bench、**500+ agent sessions**、**10,000-token system prompt**。
  - **prompt caching 省 API cost 41–80%、TTFT 改善 13–31%**。
  - **策略性分塊**（動態內容擺去 system prompt **尾**、避免 dynamic function calling、排除 dynamic tool results）**一致好過** naive full-context caching —— 後者有時候**反而拖慢**。
- **The New Stack**（thenewstack.io/agent-harness-token-costs）：同一個 model、唔同 harness，token 用量差 **70 倍**；Composio 披露 **Claude Code 只有 1.5% input token 來自 cache**，對比 **Codex 約 70%**、OMP 57%。
- 社群 build log（niteagent.com）：naive agent loop 只有 **12–15%** 命中；改成 stable prefix ＋ tool schema 攤銷 ＋ 固定窗口之後上到 **83–97%**。

## 5. 對 packai 嘅啟示（Hermes 親核）

**packai 中咗文獻講嘅頭號 cache bomb。**

- `forge/1.19.2/src/main/java/com/skps9/packai/logic/LlmClient.java:379-380`：
  ```java
  String style = ReplyLang.llmStyle(langCode, offered);
  String rules = ReplyLang.llmRules(langCode, questOverride, questConflict, policy);
  ```
- 兩者喺 **`:478-481`** 直接拼入 **system message**：
  `ReplyLang.llmSystemLead(...) + ReplyLang.factCheck(..., offered) + PackAuthorAgents.systemAddon(...) + style + rules`
- `questOverride` / `questConflict` / `policy` 係**逐條問題**計（`logic/AskEngine.java` 約 259／272／296-303）；`offered` 亦會隨 tool 提供情況變。
- ⇒ **system message 逐條問題唔同**，而 system 就係 `messages[0]`（位置 0）⇒ 前綴喺極早位置就分叉 ⇒ **tools schema ＋ 成段 history 一齊 miss**。呢個正正係 OpenClaw 講嘅「stable prefix 要保持 byte-identical」要避免嘅事，亦係 arXiv 篇文嘅核心發現。
- 另外：**三個 harness 都係靠讀 provider 嘅 cache 計數器做量度**，冇人做 per-ask 歸屬 ⇒ 之前「停手報告」講嘅 attribution 死結係**我哋自製嘅**；業界正解就係「逐 request 讀兩個數字＋log ratio」。

## 6. 建議（取代 plan v3 嘅 M1 proxy 路線）

1. **instrument 先**（3 行）：讀 `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens` / `prompt_tokens`，逐 request log —— 等同 OpenClaw 嘅 `cacheRead` 正規化。零風險、即刻有真數字。
2. **改 prompt 結構**（對齊 OpenClaw / Hermes）：把逐問題變嘅 `style` / `rules` 由 **system prompt 搬去 user message 尾**，令 `system + tools` 變成 byte-stable；history 保持 append-only。
3. **加 live regression test ＋ no-cache 對照組**（學 OpenClaw）：同一 ask 跑兩次，斷言第二次有 cache hit；同時跑一次「明知唔應該命中」嘅對照。
4. 之後才考慮 TTL／keep-warm（packai 係 GUI 問答，手動節奏，未必需要 heartbeat）。
