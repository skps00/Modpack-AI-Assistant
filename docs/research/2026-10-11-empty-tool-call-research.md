# 2026-10-11 — 「工具空手／無用回合」業界點解決（SK 要求：先查網）

**我哋嘅問題（今日實測，7 個 instance 全部 trace）**

| 指標 | 數 |
|---|---|
| 工具呼叫總數 | 699 |
| **回傳空手** | **164（23.5%）** |
| 空手最多 | `jei_lookup` 101／`acquire` 34／`quest_fetch` 16／`consume_use` 11 |
| 每對話平均回合 | 2.43（其中 1.48 個係空手） |
| **至少中一次空手嘅對話** | **99/111（89%）** |
| trace 有無 token 欄位 | **冇** → 成本佔比未量到（唔靠估） |

---

## 1) 業界做法（有 source）

### A. 唔好把全部工具塞入 prompt —— Tool RAG（工具檢索）
Red Hat Emerging Technologies（2025-11-26，`next.redhat.com`）：「Instead of showing the LLM every possible tool, we first retrieve the most relevant ones based on the user's intent, and only then present them to the model」，並引述研究指**智能工具檢索可以令工具選擇準確度 ×3、prompt 長度減半**（原文引 COLT 論文 arXiv 2405.16089）。
→ 對應我哋：capable 模式每回合固定帶 15 個 tool schema（≈1,400 tok）；其實可以按「呢條問題＋焦點物品」只 offer 有機會命中嘅工具。

### B. 主動按需取工具 —— MCP-Zero（arXiv 2506.01056）
指出「inject thousands of tool schemas into prompts, creating massive context overhead」；實例：**GitHub MCP server 26 個工具＝4,600+ tokens**，整個生態可超 **248k tokens**。做法＝① 模型主動講出能力缺口 ② 分層語意路由 ③ 逐步擴展。效果：**APIBank token 消耗減 98%** 而準確度保持。
→ 對應我哋：唔使去到「模型主動要求」，但同一原理——**唔相關就唔出現**。

### C. 唔該叫嘅工具就唔好叫 —— WHEN2TOOL（arXiv 2605.09252，UCSD）
「Tool-augmented LLM agents tend to call tools **indiscriminately**, even when the model can answer directly」，每次無用呼叫都燒 API 費＋延遲。發現：**「有冇需要工具」係可以由模型 hidden state 線性解讀（AUROC 0.89–0.96）**，但模型生成時唔會用呢個資訊。純 prompt 做法（叫佢少啲叫）會連**必要嘅呼叫都抑制**；「先推理後行動」就令難題準確度大跌。佢哋嘅 Probe&Prefill：呼叫減 **48%**、準確度只跌 **1.7%**；真環境 Search-o1 上 API 呼叫減 **20–56%** 而無退化。
→ 對應我哋：我哋**唔控制模型 hidden state**（走 API），但可以做到**同等甚至更強**嘅嘢——用**本地索引事實**決定，而唔係靠模型自覺。

### D. 「資料唔夠」要喺生成之前判定 —— RINSE（arXiv 2609.37469）
「even with an explicit instruction to abstain, 12 generators still answer **40.0–99.3%** of insufficient-evidence questions」（即：**叫模型「冇料就唔好答」係冇用嘅**）。佢哋主張**喺生成之前**判斷證據夠唔夠：3 個 signal 合成，準確分辨 **0.837**（舊方法最好 0.746），本地跑只需 **36.5 ms/題**。
→ 對應我哋：**同 SK 嘅規矩一致**——「唔接受 LLM 選擇性行為，要結構性 fix」。我哋嘅「索引有冇命中」係**確定性事實**，比統計 signal 更硬。

### E. 其他
- **OpenAI Agents SDK — tool guardrails**（`openai.github.io/openai-agents-python/ref/tool_guardrails`）：喺**工具執行之前**檢查參數／狀態，可以**阻止或改寫**該次呼叫。＝我哋想做嘅「唔夠資格就唔執行」嘅官方版。
- **Prompt-Based Abstention Fails Under Misleading Context**（arXiv 2608.22228）：再一次證實「淨係 prompt 講」唔可靠。
- **Zuplo「Stop making your agent poll」**：工具要**講清楚佢會等**，避免 agent 重複叫（同我哋 `[TOOL_MISS]` 提示同源）。
- **n8n 社群**：要求 agent node 加「空結果 fallback」，否則 agent 會停或亂試。

---

## 2) 映射到我哋（設計結論）

| 業界做法 | 我哋已有 | 要補 |
|---|---|---|
| Tool RAG：按意圖只 offer 相關工具 | —（固定 15 個） | **每回合按命中可能性篩選 offer 清單** |
| MCP-Zero：唔相關就唔出現 | 有 `CAPABLE/QUERY` 名單 | 由「靜態名單」升級做「**動態 offer**」 |
| WHEN2TOOL：唔該叫就唔叫 | — | 用**本地索引**做確定性判斷（我哋唔靠 hidden state） |
| RINSE：生成前判資料夠唔夠 | `AskMissFallback`／`toolMissNote`（**已經係生成後**補救） | **前置判定**：索引零命中 → 直接畀確定性答案行，唔叫工具 |
| OpenAI guardrail：執行前攔 | — | 同一位置實作（`AskToolLoop` offer／執行前） |

**預期效果（可量）**：164 次空手 → 目標 ≤40；受影響對話 89% → 目標 <30%。**成本佔比要另外量**（trace 要加 usage 欄位），唔准先報數。

**誠實限制**
1. `jei_lookup` 有部分係**合法探測**（確認物品存唔存在／改名），唔可以一刀切禁。
2. 「索引零命中」唔等於「真係冇」——例如 runtime 生成嘅資料（部分 mod 首次開遊戲才寫 config）→ 需要 fallback 一條「未收錄」而唔係當「冇」。
3. 我哋係 API 場景（無 hidden state），所以 WHEN2TOOL 嘅 probe 方法**抄唔到**，只可以抄「生成前確定性判定」呢個結論。

## 3) 來源清單
- Red Hat Emerging Technologies, *Tool RAG: The Next Breakthrough in Scalable AI Agents*, 2025-11-26
- *MCP-Zero: Active Tool Discovery for Autonomous LLM Agents*, arXiv 2506.01056v3
- *LLM Agents Already Know When to Call Tools — Even Without Reasoning* (WHEN2TOOL), arXiv 2605.09252
- *Relevance Is Not Sufficient Evidence: Detecting Evidence Gaps Before Generation in RAG* (RINSE), arXiv 2609.37469
- *Prompt-Based Abstention Fails Under Misleading Context*, arXiv 2608.22228
- OpenAI Agents SDK docs — tool guardrails；Zuplo blog — Stop making your agent poll
