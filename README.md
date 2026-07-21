# Pack AI Assistant

單一 **NeoForge 1.21.1 客戶端模組**：把 jar 丟進 `mods` 即可。不需另外跑 Python Bridge。

## 玩家怎麼用

1. 編譯後把 `mod/build/libs/packai-*.jar` 放到整合包 `mods/`
2. （選用）Mods 設定 → Pack AI：填雲端 API key；或本機安裝 [Ollama](https://ollama.com) 並 `ollama pull llama3.2`
3. 遊戲內：**Mods → Packai → Config** 可貼完整 API key；按 **`]`** 開助手提問（可切模式／模型）



沒有 API key、Ollama 也沒開時：仍可用任務書導引與本地配方白話說明。

在 `config/packai-client.toml`（或 Mods → Packai → Llm）設定 **`llm.mode`**：

| 值 | 說明 |
| --- | --- |
| `auto`（預設） | 有 key → 雲端；否則試 Ollama；否則純本地 |
| `cloud` | 只用雲端（沒 key 會提示錯誤，不改走 Ollama） |
| `ollama` | 只用本機 Ollama（忽略 apiKey） |
| `offline` | 不呼叫 LLM，只任務／白話配方 |

## 行為摘要

- 答案以白話作法／材料為主，不整段貼 KubeJS
- FTB Quests / Heracles：最多列 **3** 個相關任務，請打開任務書
- 「卡住」≠ 略過任務；只有明確說任務書有誤（或按「任務書不對」）才走配方／本地路徑

## 相容

無 Mixin、無 JEI 注入；tick 只讀快捷鍵；索引／LLM 在背景執行緒。可與 Embeddium、ModernFix、EMI 等共存。

## 開發編譯

```powershell
cd mod
.\gradlew.bat build
# 或開發啟動
.\gradlew.bat runClient
```

## 設定（client config）

| 鍵 | 說明 |
| --- | --- |
| `llm.mode` | `auto` / `cloud` / `ollama` / `offline` |
| `llm.apiKey` | 雲端 key（建議直接改 toml；或環境變數 `PACKAI_API_KEY`） |
| `llm.apiBaseUrl` | DeepSeek：`https://api.deepseek.com`；OpenAI：`https://api.openai.com/v1` |
| `llm.model` | 例如 `deepseek-v4-flash` / `gpt-4o-mini` |
| `llm.ollamaBaseUrl` | 預設 `http://127.0.0.1:11434/v1` |
| `llm.ollamaModel` | 預設 `llama3.2` |

環境變數 `PACKAI_API_KEY`（或 `DEEPSEEK_API_KEY`）會覆寫設定檔中的 key。

`bridge/` 目錄僅供參考／舊測試，玩家不必安裝。
