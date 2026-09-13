# packai 架構評估：**Fork Hermes 成 MC mod** vs 輕量方案

- 日期：2026-09-13
- 提出人：SK（原話：「maybe we fork deepseek hermes to minecraft as mod? IDK, review the mod with cursor (with diff model? IDK, find the best model)」）
- 背景：packai（Forge 1.19.2 + NeoForge 1.21.1 雙樹，CurseForge 已發佈 0.2.1）第 3 次出現「模型把 tool-call 標記（`<｜DSML｜…>`）當文字掟出嚟，玩家見到 prompt 樣文字」。
- 本檔目的：決定**架構方向**（唔係即刻改 code）。

---

## 0. 結論（先講）

| 問題 | 答案 |
|---|---|
| 應唔應該 fork Hermes 成 MC mod？ | **唔應該**（作為 packai 產品線）。三個硬理由：**發佈可行性**、**維護成本**、**可靠性唔係靠包多層解決** |
| 咁「Hermes 級 agent」想要，點做？ | 走 **sidecar**（Hermes 本機跑，mod 做 thin client）——**只供 SK 自用**，唔影響公開 mod |
| 公開 mod 應該做咩？ | **輕量硬化**：mod 內保留 bounded agent loop，但協議改為 **只信 native `tool_calls`／strict JSON**，加**渲染前硬閘**（deterministic），任何 markup 永不可入答句 |
| 最脆弱位（3 個月後會再爆） | 只要「文字協議（DSML）」同「native tool call」兩條路並存，就一定會再爆。要**刪走一條**。 |

---

## 1. 四個選項（成本／效益／風險）

| 選項 | 內容 | 成本 | 效益 | 風險 | 發佈可行 |
|---|---|---|---|---|---|
| **A. Fork Hermes 入 mod** | 將 Hermes（Python agent runtime）搬入 Java mod，或喺 JVM 內嵌 Python | **極高**：Hermes 係 Python，MC mod 係 Java；要 reimplement 或嵌 GraalPy/Jython；雙樹（Forge+NeoForge）要同步 | 玩家可享「完整 agent」（tools／memory／多步） | agent runtime 同遊戲 tick／render thread 混住；每個 MC／Forge 版本更新都要重做；玩家要裝一大堆嘢 | ❌ **玩家唔會為咗一個 mod 裝 agent runtime**（見 §2 證據） |
| **B. Sidecar（Hermes 本機 + mod thin client）** | mod 唔變；本機開 Hermes（已有 `hermes mcp serve`／api_server），mod 用 HTTP／MCP 問 | 低—中（一次性接線 + 本機常駐程序） | SK 自己可以有「真 agent」：長記憶、多工具、跨 app | 只對 SK 自己有用；**唔可能發佈**（其他玩家冇 Hermes）；sidecar 要常駐（你已經有 gateway watchdog 經驗） | ✅ 僅自用 |
| **C. 輕量硬化（公開線）** | 保留現有 mod 內 agent loop；**刪走文字協議**，只信 native `tool_calls`；答句渲染前加 deterministic 硬閘（含 markup 就唔出／當 tool call） | 低（1–2 輪 fix） | 直接解決第 3 次嘅 bug 類；對玩家零要求 | 要改 prompt／解析／顯示三處，雙樹同步 | ✅ 現有形態 |
| **D. C ＋ 本地模型（省錢線）** | C 之上，語言層可揀 Ollama／本機模型（packai 已支援 Ollama 模式；SK 有 5090 32G） | 中（VRAM 同 MC 共用；要量延遲） | 免費、私隱、唔受 API 限流（`:free` model 常 429） | MC＋模型同時跑要管 VRAM；本地模型 tool-calling 可靠性通常**更低**（同 bug 類會更多） | ✅（玩家可選） |

---

## 2. 證據（點解 A 唔可行）

1. **業界 SOTA（Voyager，MineDojo）本身都係「agent 喺遊戲外」**：Voyager 用 **Mineflayer（Node）做外部 agent**，透過 API 控制 MC，而唔係將 agent 塞入 mod。論文／repo：<https://github.com/MineDojo/Voyager>、<https://voyager.minedojo.org>、HN 討論 <https://news.ycombinator.com/item?id=36085936>。→ 連最強嘅研究路線都選擇「外部 agent + 遊戲內 thin 接入」。
2. **同類 CurseForge mod 全部係 thin client + 用戶自備 key**：CreatureChat（19.48 萬下載）、Aria's Chat To AI（「The mod will confirm key registration」）、Nexa AI Chat、Chat_AI_Assistant、AI Talk Mobs GPT（「Please issue an OpenAI API key」）。→ 市場現實：玩家接受「自備 key」，唔接受「自備 agent runtime」。
3. **packai 自己已經係呢個模式**：README：「雲端需自備 API key；也可用 Ollama 或 `offline`」。
4. **Hermes 已經有現成外部接口**：`hermes mcp serve`（Hermes 做 MCP server）／`hermes acp`／api_server（本專案 JARVIS 就係咁用）。→ 「要 Hermes 能力」根本唔需要 fork，只需要接線（= 選項 B）。

---

## 3. 建議路線（分兩條，唔互相阻塞）

### 公開線（立刻做）= 選項 C
1. **協議單一化**：刪走「文字協議（DSML）」這條路；模型只可以用 native `tool_calls`。若 provider 唔可靠 → 改用 **strict JSON**（單一 JSON schema，唔係自由文字）並加 `response_format`／等價機制。
2. **渲染硬閘（deterministic，唔靠 LLM 自律）**：任何要顯示／TTS 嘅文字，先過一個「唔可以有 markup／角括號標記／`｜` 全角變體」嘅閘；中咗就**當 tool call 處理或直接丟**，並寫 audit log（一行）。(SK 原則：唔接受選擇性行為，要結構性 fix。)
3. **驗收（可斷言）**：同一問題連續問 N 次（N≥10，含 SK 嗰條「猛者瓶」），log 斷言：`玩家可見文字 == 0 條含 markup`，而且 `toolCards` 冇少。
4. 雙樹（Forge＋NeoForge）同步改，兩邊各自 build + grep 驗證（既有規矩）。

### 自用線（並行、唔急）= 選項 B
- 目標：SK 自己喺 MC 內問嘢時，可以有「Hermes 級」能力（跨 app 記憶、真工具、長上下文）。
- 做法：mod 唔改架構，只加一個 optional「外部 agent endpoint」；本機跑 Hermes（已有 MCP／HTTP），失敗就 fall back 返 C 嘅本地流程。
- 呢條**唔應該**影響公開版本（玩家冇 endpoint → 自動走本地）。

---

## 4. 未解／要再研究先答得準（誠實標明）

- **A 嘅技術可行性細節**：JVM 內嵌 Python（GraalPy／Jython）跑 Hermes 是否可行、要幾多記憶體、能否同 Forge classloader 共存 → **未實測**，屬推論。
- **本地模型 tool-calling 可靠性**：未量（要跑同一個「猛者瓶」case × N 次對比 deepseek-v4-flash vs 本機 7B/14B）。呢個數字會直接影響 D 值唔值。
- **競品 Nexa AI（聲稱 "adds an AI agent into Minecraft"）嘅架構**：未讀佢 docs，可作為 C／D 嘅參考。
- **延遲預算**：MC client thread 唔可以 block；現時 async 已經 OK，但加「strict JSON＋retry」會加多一輪 round-trip，要量 p95。

---

## 5. 一句總結

> **唔好 fork。** 公開 mod 走「輕量硬化＋單一協議」（C），SK 自用嘅「真 agent」走 sidecar（B）。Fork 只會令我哋同時養兩邊唔討好：玩家裝唔到、我哋要維護多一套 runtime，而 bug 類（模型唔聽話）一個都唔會少。
