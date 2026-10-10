# Plan v2：成本地基 — cache 命中量度 ＋ system prompt byte-stable（2026-10-10）

- 觸發：SK「而家噉樣做消耗太多 token，而且我相信有更好嘅方法」。實測：**82.4% token ＝每輪重送同一份 system prompt ＋ 15 個 tool schema**（固定 51,758 tok／ask，總 62,818；**數字由 `%TEMP%\token_breakdown_20261010.py` 重跑重現**，出處＝`docs/research/2026-10-10-cheap-capability-lookup-research.md` **第 29／31 行**（該檔只有 §0–§5，冇 §29–31）。
- ⚠️ **本 plan 取代** `docs/plans/2026-10-10-cache-hit-measurement.md`（v1–v3 已 R1–R3、停在等 SK 揀甲／乙／丙／丁）。兩份唯一衝突：舊 plan §P 寫死「**唔改 `TokenUsage`／`AskResult` record 簽名**」——本 v2 **明確推翻**，理由見 §4.1（舊約束係為「3 行加 log、零足印」而設；本階段目標係成本地基＋**per-ask 歸屬入 trace**，唔改 record 就做唔到結構化累加）。
- 業界（`docs/research/2026-10-10-harness-prompt-cache-comparison.md`）：OpenClaw（stable prefix／volatile suffix＋`cacheRead` 正規化＋live regression）／Hermes（`system_and_3`，4 breakpoint）／DeepSeek Harness（token meter）／arXiv 2601.06007（省 41–80% cost）。**冇一家人做 per-ask 歸屬** ⇒ 我哋要自己加。

## 0. R1 review 結果（正方 5.5 : 反方 4.5，未過 8:2）→ 本 v2 逐條裁決

| # | 指控（證據） | v2 修法 |
|---|---|---|
| **A1（high）** | V2「連問 2 條就見到 cache_hit>0」同官方 Example 2／我哋自己研究矛盾：同 prefix 唔同尾段嘅**第 1、2 次都唔中，第 3 次先中**；而且 plan 冇寫 hit=0 分支 | V2 改為**連問 3 條**（並寫明第 1 條必 miss 屬正常）；加 **hit 恆 0 診斷分支**（先查 §3 前置實驗結果，再查 provider／prefix 穩定性） |
| **A2（med）** | 「tools 入唔入 cache prefix」**官方冇講**，plan 當咗已知去計省錢 | 明寫為**未證未知**、**唔計入省錢承諾**；用 §3 前置實驗（tools on／off 各 3 次）實測 |
| **A3（med）** | 搬走 `rules` 之後 messages[0] **仍然**可變：`offered`（HTTP 400 會令 notools 變體永久翻 false，LlmClient:270-277／541-545）、`style` 含 `RecipeCardsMode.current()`、`systemAddon` 每個 ask reload pack `AGENTS.md` | V4 加「**前置條件快照**」：量 byte-identity 同時記錄 offered／preferObtain／RecipeCardsMode／lang／pack AGENTS.md；條件一變就唔跨該邊界比對 |
| **A4（med）** | `TokenUsage` 係 record（:12），加欄位＝改 canonical ctor ⇒ 8 個建構點＋`DailyTokenUsageCheck.java` 5 處；且推翻舊 plan §P 約束冇交代 | §4.1 明列全部改動點（`TokenUsage.java` NONE/fromResponse/plus＋5 處 Java 測試）＋寫明推翻理由；`plus()` 必須同步加總兩個新欄 |
| **A5（med）** | 落地位 #7 寫錯機制：`TokenUsage`／`LlmClient` 係**兩樹都有**嘅檔，屬 byte-drift（paused 模式下 WARN），**唔係** `TREE_SPECIFIC`（只管單邊存在嘅檔） | §4 改為「**唔郁 gate**，接受 paused 下 WARN（`--no-paused` 本來就會 RC=1，屬已知）」；**唔會**把純邏輯檔塞入 ALLOWLIST |
| **A6（med）** | V4「唔可以變差」冇指標、冇樣本數、冇門檻；system 比對冇講方法 | §5 V4 寫死機械判準（題數／body 長度帶／card 數／無新 fail-closed 外洩）＋system 比對用 `sha256(send.system)` |
| **A7（med）** | 落地位漏：cache 數字只落會 rotate 嘅 `latest.log`（+`AskService` 一行），**冇入 AskTrace** ⇒ per-ask 歸屬做唔到；又冇提同日期舊 plan | §4 加 **`AskTrace` usage 事件**（ask 尾寫 hit/miss/prompt）；§2 已寫明 supersede 舊 plan |
| **A8（low）** | 「兩種模式都送」措辭唔準：只有 `completeRound`（:494-497）無條件加 user map；`chatOnce`（:150-246）用另一條 body | 改寫為「**tools／no-tools 兩種 completeRound 模式都送**」 |
| **A9（low）** | 改 `completeRound` 簽名屬無必要 scope creep | 刪：`rules` 喺 `LlmClient` 內部已有（:380），**唔改任何 method 簽名** |
| **A10（low）** | 還原方案唔夠具體（冇 pin 工作樹／guard cron 係 paused／部署要關 java） | §6 補：改動前記 `HEAD`＋`git status` baseline；還原後核 jar sha256＋跑 harness；**明寫 guard cron 自 2026-09-15 起 paused**（人手紀律） |
| 數字核 | **全部行號／數字 TRUE**（TokenUsage:20-29、LlmClient:229-233／552-557／47-49／379-380／478-481／440／494-497、`new LlmClient()` 恰 3 處、ReplyLang llmRules 5 分支 :1310-1324、127 檔 1 紅實跑一致） | 只改：`§29-31`→「第 29／31 行」；14 條（token 量度，來自 `%TEMP%\keybind_run*`）vs 13 條（instance trace 零 usage）**分開寫明**；`check_ask_capable_slim.py` **唔係** payload-key 閘（佢查 `AskEngine` 源碼）→ 由落地位刪走 |

## 1. 親核現況（兩個真問題）

1. **睇唔到**：`logic/TokenUsage.java:20-29` `fromResponse` 只讀 `prompt_tokens`／`completion_tokens`／`total_tokens`，丟棄 DeepSeek 嘅 `prompt_cache_hit_tokens`／`prompt_cache_miss_tokens`（**官方欄位名已核，查證日 2026-10-10，https://api-docs.deepseek.com/guides/kv_cache**）；instance trace **13 條全部零 usage 記錄**。
2. **前綴唔穩（cache bomb）**：`logic/LlmClient.java:379-380` 逐條問題計 `style`／`rules`（`llmRules(lang, questOverride, questConflict, policy)` **5 分支**，policy 逐題計），而 `:478-481` 直接拼入 **system message（messages[0]，位置 0；`:482` add 喺 history／user 之前）** ⇒ 前綴喺極早位置分叉，後面 tool schema ＋ history 一齊 miss。

**Provider 前提（A 嘅適用範圍）**：只有 provider 回 DeepSeek 欄位先有數；`PackAiConfig.java:273-281` 預設係 OpenAI（`apiBaseUrl` 純 String、**冇 scheme／provider validator**）。三個實例 config 已核實全指 `https://api.deepseek.com`（10-10）。fallback `usage.prompt_tokens_details.cached_tokens`（OpenAI 相容，DeepSeek 文檔明寫「Same as prompt_cache_hit_tokens」）**只補到 hit，補唔到 miss** ⇒ miss 記算式＝`prompt_tokens − cache_hit`（唔係 `-1`）。

## 2. 三件工作（次序＝0 → A → B）

### 設計 0（新增，零改碼前置實驗，≈US$0.02）

用真 DeepSeek 直接打 HTTP（唔經遊戲、唔經 code）驗三條假設，**先驗後寫**：
1. 同一份 byte-identical body 連發 3 次 ⇒ 第 3 次真的 hit？（驗官方 Example 2 嘅適用性）
2. `tools` 陣列入唔入 cache prefix？（同一 system，tools on／off 各連發 3 次）
3. 回覆真係有 `prompt_cache_hit_tokens`／`prompt_cache_miss_tokens` 兩欄？
**結果寫入 plan §0 附錄**，直接決定設計 B 值唔值得做。

### 設計 A：cache 命中量度（**唔改 prompt、唔加 request**）

- `logic/TokenUsage.java`：加 `promptCacheHitTokens`／`promptCacheMissTokens`（`-1`＝未知）；`fromResponse` 讀兩欄＋fallback 記算；`plus()` 同步加總；`NONE` 一齊改。
- `logic/LlmClient.java` 兩個解析點（`:229-233` chatOnce／`:552-557` completeRound）各 log 一行 `Pack AI cache hit=<n> miss=<n> prompt=<n>`。
- `client/service/AskService.java:606-608` usage 行加 cache 欄。
- **新**：ask 尾加 **AskTrace usage 事件**（hit／miss／prompt），令 per-ask 歸屬入 trace（唔再只靠會 rotate 嘅 `latest.log`）。

### 設計 B：system prompt byte-stable（**內容逐字保留，只換位置**）

- 把逐題變嘅 `rules`（`:380`）由 system message 搬去 **user payload 新 key `rules`**（同 `jei`（`:439-441`）並排）；`user` map 喺 `completeRound` 內部組裝、`:494-497` 無條件送出 ⇒ **tools／no-tools 兩種 completeRound 模式都送**（chatOnce 另一條 body，唔受影響）。
- **唔改任何 method 簽名**（`rules` 已喺 method 內計）。
- 結果：同一（pack, lang, config, offered）之下 system message 應該 byte-identical ⇒ 跨 ask 可重用 **system** 前綴（**tools 是否一齊入 prefix 屬未證，見設計 0**）。
- **風險**：指令由 system role 搬去 user role ⇒ 可能影響遵守度 ⇒ V4 前後對照把關；變差即退回「只做設計 A」。

## 3. 落地位（漏一項即靜默或紅）

| # | 檔案 | 改動 |
|---|---|---|
| 1 | `logic/TokenUsage.java` | record 加 2 component（:12）；`NONE`(:13)／`fromResponse`(:20-29)／`plus`(:64) |
| 2 | `logic/LlmClient.java` | 兩解析點 log；(user payload 加 `rules` key) |
| 3 | `client/service/AskService.java` | usage 行（:606-608）加 cache 欄 |
| 4 | `logic/AskTrace.java` | 新增 usage 事件（hit／miss／prompt） |
| 5 | `src/test/.../DailyTokenUsageCheck.java` | **5 個 `new TokenUsage(...)` 要補新參數**（唔改＝`compileTestJava` 紅） |
| 6 | `tests/check_token_usage.py` | mirror 3 欄 → 5 欄 |
| 7 | `tests/check_ask_trace*.py`（如有）＋新 check | trace 新事件格式 |

**dual-tree**：`TokenUsage.java`／`LlmClient.java` 兩樹都有 ⇒ forge-only 改動會 byte-drift ⇒ paused 模式下 **WARN（RC 仍 0）**，屬預期；**唔郁 gate**（`TREE_SPECIFIC` 只管單邊存在嘅檔，唔關事；ALLOWLIST 只收「純邏輯要 lockstep」嘅例外）。

**唔准郁**：卡落位（`RecipeEmbed`／`RecipeCard`）／`AskReplyScrub`／`AskToolLoop` 三張名單／lang 檔／system prompt 內其他文字／`neoforge` 樹／`completeRound` 簽名。

## 4. 驗收標準

- **V1** `compileJava compileTestJava` RC=0（含上面 #5）；現有 harness 全綠。
- **V2** 沙盒真機**連問 3 條**問題 → log 見到 `cache hit>0`（第 1 條必 miss 屬正常）。**若 3 條都 0**：唔准當「設計錯」草率收工 —— 先回 §0 設計 0 結果對照（prefix 是否真 stable／provider 是否 DeepSeek／是否 tools 唔入 prefix），再出診斷報告。
- **V3** 全部 `tests/check_*.py` 冇新增紅（baseline＝**當日實跑**：127 檔、1 知名紅 `check_ask_display_leak.py`（需真機 `latest.log`））。
- **V4** **結構前後對照（機械判準）**：同一批題（≥6 條，固定清單，沙盒）設計 B 前／後並列，逐條比：(a) body 長度喺 ±30% 內；(b) item／card 數相同或 ±1；(c) 【來源】行齊全程度唔跌；(d) 零新 fail-closed 外洩 token（用真機 body 親掃）。**另**：由 trace 抽 `send.system` 逐 ask 計 `sha256`，證明同條件下**逐 ask 一致**；同時快照 `offered`／`preferObtain`／`RecipeCardsMode`／`lang`／pack `AGENTS.md`——條件一變就唔跨邊界比對。
- **V5** `git status` 只准預期檔（改動前已記 baseline；`forge/1.19.2/logs/`／`logs/` 本身 untracked，唔准 `git add -A`）；`neoforge/` 零改動。

## 5. 風險／還原

- 風險：中低（設計 A 唔改 prompt；設計 B 有指令位置改動 ⇒ V4 把關）。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\` 還原（部署**只准** `mc_mod_deploy_jar.py --target packai`，要關 java）。
- ⚠️ **jar guard cron 自 2026-09-15 20:25 起 paused**（冇自動守門）⇒ 部署／還原要人手核 sha256。
- 改動前：記 `git rev-parse HEAD`＋`git status --short` 全文（本 repo 長期有未 commit 檔，唔記就分唔清邊個係自己改）。

## 6. 執行

- 實作＝**cursor-agent**（唔准 commit、唔准 hot-copy jar、只改 §3 列明範圍）；Hermes 做 plan／派工／**親驗**（compile／harness／check／diff，唔信自報）。完成後跑兩輪 code review。
