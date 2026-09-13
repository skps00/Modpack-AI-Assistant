# DSML 漏出：業界點做？（2026-09-13 研究，SK 要求 "search online see how other people done it"）

## 0. 一句總結
**主流做法 = 偵測 → 解析返個 tool call → 真係執行 → 同時把 markup 由玩家文字剝走。**
冇人靠「見到 markup 就丟」——因為丟咗就等於「模型想做嘅事永遠冇發生」（我哋 log 見到嘅 `toolCards emission=0`／靠 autoEmission 補卡，正是同一症狀）。

## 1. 實錘來源

| # | 來源 | 內容（原文重點） |
|---|---|---|
| 1 | **Cline issue #13348**（api.cline.bot，付費 key 直連 curl 重現）<https://github.com/cline/cline/issues/13348> | DeepSeek-V4 嘅 DSML markup 會漏入 `delta.content`：**約 1/4 帶工具嘅 streaming run** 會中（U+FF5C 全角 `｜` 同 ASCII `|` 兩種都見過）。原文：「serving stacks must parse it explicitly（**vLLM/SGLang: `--tool-call-parser deepseek_v4`**）。Without that parser the markup passes through verbatim.」→ **正解係「有 parser 去解析」，唔係過濾**。仲有一點：漏出時 `tool_calls` 可能**存在但係空殼**（`arguments: "{}"`），真 call 只喺文字裡面 → 「信 `tool_calls` 嘅 client 會攞到冇參數嘅 call，render `content` 嘅 client 會見到垃圾」。 |
| 2 | **OpenClaw issue #85918**（2026-05 開，仍未修完）<https://github.com/openclaw/openclaw/issues/85918> | 原文指出 OpenClaw 現況：`createDeepSeekTextFilter()` **只剝走可見文字**，`processOpenAICompletionsStream()` 仍然**只由 native `choiceDelta.tool_calls` 建真 call** → 「if DeepSeek emits DSML tool markup as plain text, **OpenClaw currently strips/streams text but does not promote it to `toolCall`**」＝**未做 recovery 就係 bug**。 |
| 3 | **ElizaOS PR #9278**（已 merge 之生產代碼）<https://github.com/elizaOS/eliza/pull/9278/files> | 正解示範：非 JSON 輸出時 `toolCalls: recoverEmbeddedToolCalls(trimmed)` ＋ `messageToUser: sanitizePlannerMessage(trimmed)`；註釋原文：「**Recover the call it meant to make and strip the markup from the user-facing text instead of leaking it**」，並要求「**a recovered call never double-shows as prose**」。 |
| 4 | **toolcall-rescue**（MIT、零依賴、PyPI，專門做呢件事）<https://github.com/MUSE-CODE-SPACE/toolcall-rescue> | 「Recover tool/function calls that local & small LLMs emit as **plain text** — when the structured `tool_calls` field comes back empty.」用法：`calls, residual = extract_tool_calls(content)`，**只喺 `tool_calls` 為空時做 fallback**。 |
| 5 | **DeepSeek 官方 V4 encoding README**（HuggingFace）<https://huggingface.co/deepseek-ai/DeepSeek-V4-Pro/blob/main/encoding/README.md> | DSML（`<｜DSML｜invoke …>`／`<｜DSML｜parameter …>`）係**模型自己嘅 tool-call 編碼格式**；官方明寫 `parse_message_from_completion_text`「**does not attempt to correct or recover from malformed output**…**For production use, additional error handling is recommended**」→ 官方都話 production 要自己加 recovery。 |
| 6 | **strix PR #901**（local endpoint 文件修正）<https://github.com/usestrix/strix/pull/901/files> | 「If your inference server returns the tool call as plain assistant text instead of a structured `tool_calls` field, the agent **never sees a call it can execute**, so the agent makes no real progress」——講清「唔做 recovery」嘅代價。 |
| 7 | **NVIDIA 開發者論壇**（DeepSeek API 漏 DSML token）<https://forums.developer.nvidia.com/t/deepseek-api-usage-reports-extremely-inflated-token-counts-and-leaks-dsml-tool-call-markers/367901> | 同類回報（供應商層面亦漏）。 |
| 8 | 已存 skill `llm-tool-calling-reliability` → `references/tool-call-markup-leaks.md`（2026-09-11 己記錄 OpenClaw PR #128882／Hermes adapter 做法） | Hermes 做法＝漏出即當回合 `incomplete` → 清 `final_text` → 重試要模型用正式 `tool_calls`（fail-**incomplete**，唔當成功）。 |

## 2. 對照表（我哋三個選項 vs 業界）

| | 偵測（認唔認得變體） | 認到之後 | 業界共識 |
|---|---|---|---|
| OpenClaw（未修完） | 有 filter（明列 `"|"`／`｜`／`｜｜`） | **只剝，唔執行** ← 佢自己 issue 講明係 bug | ❌ 唔夠 |
| Cline 建議 | 要 parser | **vLLM/SGLang `--tool-call-parser deepseek_v4`** 解析返 | ✅ 解析 |
| ElizaOS | `recoverEmbeddedToolCalls` | **解析 + 真執行 + 文字剝走** | ✅ 正解 |
| toolcall-rescue | `extract_tool_calls()` | 回 `(calls, residual)`，只喺 `tool_calls` 空時用 | ✅ 正解（library 級） |
| DeepSeek 官方 | — | 「production 要自己加 error handling」 | ⚠️ 自己負責 |
| **PackAI 現況** | `DSML_TOKEN` **只認單豎線** → 連偵測都 miss | **淨刪唔執行** → 行動蒸發 | ❌ 兩個都缺 |

## 3. 結論／建議（改我上一個建議）
研究完，**A+B+C 一齊做**（我上次保守建議 A+C，係因為未查證；查完之後 evidence 支持做齊 B）：
- **A（前置，一定要）**：偵測器認齊 `|`／`｜`／`｜｜`／`¦`／`│` 等變體 —— 唔做 A，B 連 trigger 都唔會 trigger。
- **B（核心）**：認到 → **解析返做真 tool call 並執行**（ElizaOS 模式），加：
  - 只認**閉合**嘅 block（開／閉 tag 配對；配唔到就唔准執行 → 防 execute 半截 call）；
  - **只准白名單工具名**（防止「幻覺工具」被執行）；
  - **idempotency**：同一 call 唔可以執行兩次（native `tool_calls` 已經有嘅唔再執行）；
  - audit log 一行（`dsml_recovered tool=<name> ok/deny reason=`）。
- **C（框架）**：close-tag 配對 + 大小上限，取代鈍刀 `dropResidualDsmlLines()`（順手清 over-match false positive）。
- 玩家文字層：`scrubPromptEcho` 嘅 fail-closed（今日 T2 已做）繼續做最後防線；recovery 成功嘅 call **唔可以**再以 prose 形式出現（ElizaOS 明寫）。

## 4. 驗收（可斷言）
1. 用今日真機 log 嘅 `latest.log:562` 原始 DSML bytes 做 fixture → 解析出 `render_recipe_cards(role=output, …)` 等真參數（**唔係空殼**）。
2. 同一 fixture 餵落 `AskReplyScrub` → 玩家文字 0 條含 markup。
3. 雙樹 `AskReplyScrubCheck -ea` 新 case（配對失敗／白名單外工具／重複 call 三種都要 deny）。
4. 真機：同一問題 ×10，`toolCards` 唔可以少過改前，且 log 要見到 `dsml_recovered`（如果有 trigger）。
