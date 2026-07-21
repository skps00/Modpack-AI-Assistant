# Pack AI Assistant

單一 **NeoForge 1.21.1 客戶端模組**：把 jar 丟進 `mods` 即可。不需另外跑 Python Bridge。

倉庫：https://github.com/skps00/super_minecraft_AI_player

## 玩家怎麼用

1. 編譯後把 `mod/build/libs/packai-*.jar` 放到整合包 `mods/`
2. **換 jar 前請完全關閉遊戲**，避免語言檔載入失敗
3. （強烈建議）安裝 **JEI**，配方／用途會與遊戲內 R／U 一致
4. （選用）Mods → **Packai** 設定頁：貼上完整雲端 API key、Base URL；或本機 [Ollama](https://ollama.com) + `ollama pull …`
5. 遊戲內按 **`]`** 開助手（`/ai <問題>` 為備援）
6. 點 **模型：…** 開啟搜尋選擇畫面（可輸入關鍵字篩選）；或按 **重整** 強制更新清單

沒有 API key、Ollama 也沒開時：仍可用任務書導引與本地配方白話說明。有 JEI 時離線也可顯示 JEI 摘要。

### JEI／背包「按住思考」（Create Ponder 風格）

1. 開 **背包或 JEI**（按 E）
2. 游標停在物品上 — tooltip 會顯示「按住 **Y** 來用 Pack AI 思考此物品」
3. **按住 Y** — 「Hold Y…」那一行會變成 Create Ponder 風格的 `|` 文字進度條（約 1 秒）
4. 進度滿格 → 自動開啟助手並對該物品提問

可在 **設定 → 控制 → 整合包 AI 助手** 更改 Y 鍵。

### `llm.mode`

在 `config/packai-client.toml` 或 Mods → Packai 設定：

| 值 | 說明 |
| --- | --- |
| `auto`（預設） | 有 key → 雲端；否則試 Ollama；否則純本地 |
| `cloud` | 只用雲端（沒 key 會提示錯誤） |
| `ollama` | 只用本機 Ollama（忽略 apiKey） |
| `offline` | 不呼叫 LLM；任務書／JEI／本地白話仍可用 |

## 行為摘要

### 問答與 UI

- **多輪對話**：上方聊天紀錄可滾輪；下方固定輸入（Enter 送出）
- **重新生成**：對上一題重新回答
- **清除對話** 清空歷史；關助手畫面不丟紀錄，離開世界會清
- **關閉助手時等回覆**：AI 思考中可關畫面，完成後 toast + 聊天提示；任務連結仍可點
- **推薦物品**：AI 回答後顯示物品圖示；同名不同模組會標 mod:id 消歧
- **【來源】**：每則回答結尾列出資料來源（JEI、任務書、本地腳本、網搜等）
- **語系**：回答語言在提問當下鎖定，思考中切換語言不影響該次回覆

### JEI 與物品上下文

- **手持優先**：主手有物品時以主手為上下文
- **空手 JEI**：可在 JEI 懸停、背包格子、或問題中寫 `mod:id` 指定物品
- **完整 JEI 掃描**：R／U／催化劑配方全掃（含特殊機器配方）；過濾 facade／framedblocks 等通用 spam
- **合成路徑排序**：合成台 > 熔爐 > 加工台 > … > 自動攪拌 > Minecolonies；並傾向高速路線
- **JEI 以外**：掃描整合包 datapack／KubeJS 掉落表、釣魚、交易、腳本配方，補 JEI 沒列的取得方式

### 任務與整合包

- FTB Quests／Heracles：相關任務可點 **開啟任務**；`/packai quest <id>` 也可開
- 任務標題依遊戲語系合併，避免混用英文／西班牙文
- **Serene Seasons**（整合包 mod 列表有 `sereneseasons` 且問題／物品與農作物相關）：估算季節並提示種植
- **Psi**（若安裝且問題相關）：附加術式設計提示（目前僅 LLM 描述，未寫入 CAD）

### 網搜（選用）

設 Tavily 或 Serper key 後可查 **Minecraft mod** 相關網頁；若手上物品有本地腳本／移除覆寫則不搜。

- `config/packai-client.toml` → `[web]`：`allowWebSearch`、`tavilyApiKey`／`serperApiKey`
- 或環境變數：`TAVILY_API_KEY`／`SERPER_API_KEY`（或 `PACKAI_TAVILY_API_KEY`／`PACKAI_SERPER_API_KEY`）

### 其他

- **物品描述**：送出玩家畫面上的完整 tooltip（含需按 Shift／Ctrl 才顯示的行）
- 答案以白話作法／材料為主，不貼物品 ID、檔案路徑或 KubeJS
- 非 `offline` 時：即使命中任務書也會呼叫 LLM（任務內容當上下文）；「任務書不對」才略過任務導引
- 快捷欄：按「手上物品下一步？」時會一併傳入

## 按鍵

| 鍵 | 說明 |
| --- | --- |
| `]` | 開啟 Pack AI 助手（遊戲中） |
| `Y` | 按住思考 JEI／背包懸停物品（GUI 內） |

可在 **設定 → 控制 → 整合包 AI 助手** 自訂。

## 相容

- **可選依賴**：JEI（無 JEI 時略過 R／U，其餘功能正常）
- 小量 Mixin：僅在抓 tooltip 時短暫假裝按住 Shift／Ctrl／Alt
- 索引／LLM／模型清單在背景執行緒；可與 Embeddium、ModernFix、EMI 等共存

## 開發編譯

```powershell
cd mod
.\gradlew.bat jar
# 產出：mod/build/libs/packai-0.1.0.jar
# 開發啟動：
.\gradlew.bat runClient
```

## 設定（client config）

| 鍵 | 說明 |
| --- | --- |
| `llm.mode` | `auto` / `cloud` / `ollama` / `offline` |
| `llm.apiKey` | 雲端 key（建議 Mods → Packai 設定頁貼上；或 env `PACKAI_API_KEY`） |
| `llm.apiBaseUrl` | **只要到 `/v1` 為止**，不要含 `/chat/completions`。例：OpenRouter `https://openrouter.ai/api/v1`；DeepSeek `https://api.deepseek.com`；OpenAI `https://api.openai.com/v1` |
| `llm.model` | 雲端模型 id（清單可由 API 自動更新） |
| `llm.ollamaBaseUrl` | 預設 `http://127.0.0.1:11434/v1` |
| `llm.ollamaModel` | Ollama 模型名（清單可由 `/api/tags` 更新） |

環境變數 `PACKAI_API_KEY`（或 `DEEPSEEK_API_KEY`）會覆寫設定檔中的 key。

`bridge/` 目錄僅供參考／舊測試，玩家不必安裝。
