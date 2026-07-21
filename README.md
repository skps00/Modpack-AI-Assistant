# Pack AI Assistant

單一 **NeoForge 1.21.1 客戶端模組**：把 jar 丟進 `mods` 即可。不需另外跑 Python Bridge。

倉庫：https://github.com/skps00/super_minecraft_AI_player

## 玩家怎麼用

1. 編譯後把 `mod/build/libs/packai-*.jar` 放到整合包 `mods/`
2. （強烈建議）安裝 **JEI**，配方／用途會與遊戲內 R／U 一致
3. （選用）Mods → **Packai** 設定頁：貼上完整雲端 API key、Base URL；或本機 [Ollama](https://ollama.com) + `ollama pull …`
4. 遊戲內按 **`]`** 開助手（`/ai` 為備援）
5. 模型下拉會自動從 Cloud `/models` 或 Ollama `/api/tags` 更新；可按 **重整** 強制刷新

沒有 API key、Ollama 也沒開時：仍可用任務書導引與本地配方白話說明。有 JEI 時離線也可顯示 JEI 摘要。

### `llm.mode`

在 `config/packai-client.toml` 或 Mods → Packai 設定：

| 值 | 說明 |
| --- | --- |
| `auto`（預設） | 有 key → 雲端；否則試 Ollama；否則純本地 |
| `cloud` | 只用雲端（沒 key 會提示錯誤） |
| `ollama` | 只用本機 Ollama（忽略 apiKey） |
| `offline` | 不呼叫 LLM；任務書／JEI／本地白話仍可用 |

## 行為摘要

- **JEI（選用）**：詢問時對手持物品查配方（等同 R）與用途（等同 U），摘要優先餵給 AI
- **物品描述**：送出玩家畫面上的完整 tooltip（含需按 Shift／Ctrl 才顯示的行）
- 答案以白話作法／材料為主，不貼物品 ID、檔案路徑或 KubeJS
- FTB Quests／Heracles：相關任務可點 **開啟任務**；最多約 3 筆
- 非 `offline` 時：即使命中任務書也會呼叫 LLM（任務內容當上下文）；「任務書不對」才略過任務導引
- 快捷欄：按「手上物品下一步？」時會一併傳入

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
| `llm.apiBaseUrl` | DeepSeek：`https://api.deepseek.com`；OpenAI：`https://api.openai.com/v1` |
| `llm.model` | 雲端模型 id（清單可由 API 自動更新） |
| `llm.ollamaBaseUrl` | 預設 `http://127.0.0.1:11434/v1` |
| `llm.ollamaModel` | Ollama 模型名（清單可由 `/api/tags` 更新） |

環境變數 `PACKAI_API_KEY`（或 `DEEPSEEK_API_KEY`）會覆寫設定檔中的 key。

`bridge/` 目錄僅供參考／舊測試，玩家不必安裝。
