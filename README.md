# Pack AI Assistant

單一 **NeoForge 1.21.1 客戶端模組**：把 jar 丟進 `mods` 即可。不需另外跑 Python Bridge。

倉庫：https://github.com/skps00/Modpack-AI-Assistant

## 玩家怎麼用

1. 編譯後把 `mod/build/libs/packai-*.jar` 放到整合包 `mods/`
2. **換 jar 前請完全關閉遊戲**，避免語言檔載入失敗
3. （強烈建議）安裝 **JEI**，配方／用途會與遊戲內 R／U 一致
4. Mods → **Packai** 設定頁：
   - 貼上完整雲端 API key、Base URL
   - 調整 **JEI 字數／歷史則數／事實條數**（省 token）
   - **優先獲取途徑**、**配方類別**（顯示／拖曳排序）、**網搜設定**
   - 或本機 [Ollama](https://ollama.com) + `ollama pull …`
5. 遊戲內按 **`]`** 開助手（`/ai <問題>` 為備援）
6. 點 **模型：…** 開啟搜尋選擇畫面；或按 **重整** 更新清單

沒有 API key、Ollama 也沒開時：仍可用任務書導引與本地配方白話說明。有 JEI 時離線也可顯示 JEI 摘要。

### JEI／背包「按住思考」（Create Ponder 風格）

1. 開 **背包或 JEI**（按 E）
2. 游標停在物品上 — tooltip 會顯示「按住 **Y** 來用 Pack AI 思考此物品」
3. **按住 Y** — 「Hold Y…」那一行會變成 Create Ponder 風格的 `|` 文字進度條（約 1 秒）
4. 進度滿格 → 自動開啟助手並對該物品提問

思考中不可再按住提問（tooltip 會提示等待）。可在 **設定 → 控制 → 整合包 AI 助手** 更改 Y 鍵。

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

- **多輪對話**：上方聊天紀錄可滾輪；下方固定輸入（Enter 送出）；動作按鈕在側欄（設定可選左／右）
- **跳到最新**：側欄按鈕可立刻回到最新回覆
- **重新生成**：對上一題重新回答
- **清除對話** 清空歷史；關助手畫面不丟紀錄，離開世界會清
- **關閉助手時等回覆**：AI 思考中可關畫面，完成後 toast + 聊天提示；任務連結仍可點
- **推薦物品**：回答後顯示可點圖示（依顯示名對應；同 id 不同名稱／NBT 會帶 `|顯示名`）
- **配方卡**：回答可附最多 3 張卡（合成 3×3 或機器流程；含液體／氣體等 JEI 渲染）；僅客戶端顯示
- **任務按鈕**：側欄最多顯示 1 個任務 +「換任務」切換
- **【來源】**：每則回答結尾列出資料來源
- **語系**：回答語言在提問當下鎖定；動態文案在 `assets/packai/lang`（`packai.reply.*`）

### JEI 與物品上下文

- **手持／釘選／顯示名**：優先保留完整 ItemStack（含 NBT）；問題裡的 `mod:id` 不會再用「空 NBT 預設物」蓋掉變體
- **完整 JEI 掃描**：R／U／催化劑全掃；過濾 facade／cover／camo 等通用 spam（依 path，非模組品牌名單）
- **材料標籤**：附上附魔與 custom NBT 數值等（`key≥值`），避免漏講鍛造／附魔等要求；prompt 要求逐字轉述、不針對單一模組
- **配方類別**：設定頁可開關顯示並**拖曳**優先序（影響摘要與配方卡）；未自訂時用通用關鍵字啟發式（工作台 > 熔煉 > 機器…），任務類依 `preferObtain`
- **JEI 以外**：掃描 datapack／KubeJS 掉落、釣魚、交易、腳本配方

### 任務與整合包

- FTB Quests／Heracles：相關任務可點 **開啟任務**；`/packai quest <id>` 也可開
- **Serene Seasons**／**Psi**：有安裝且問題相關時附加提示

### 網搜

- 預設開：無 key 時查 **Modrinth** + **Minecraft Wiki**；有 Tavily／Serper 則優先
- 設定頁 **網搜設定** 可開關並貼金鑰
- 有本地覆寫時**仍可搜**，衝突以本地／JEI／任務為準

### 其他

- 答案以白話作法／材料為主，不貼物品 ID、檔案路徑或腳本
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
- 索引／LLM／模型清單在背景執行緒

## 開發編譯

```powershell
cd mod
.\gradlew.bat jar
# 產出：mod/build/libs/packai-0.1.0.jar
.\gradlew.bat runClient
```

動態回覆／LLM 提示字串：`mod/src/main/resources/assets/packai/lang/{zh_tw,en_us}.json` 的 `packai.reply.*`。  
可用 `tests/gen_reply_lang_json.py` 批次產生／合併該前綴鍵。

## 設定（`config/packai-client.toml`）

也可在 Mods → **Packai** 設定頁調整大部分項目（控件有 tooltip）。

### `[llm]`

| 鍵 | 說明 |
| --- | --- |
| `mode` | `auto` / `cloud` / `ollama` / `offline` |
| `apiKey` | 雲端 key（建議設定頁貼上；或 env `PACKAI_API_KEY`） |
| `apiBaseUrl` | **只要到 `/v1` 為止**，不要含 `/chat/completions` |
| `model` | 雲端模型 id |
| `ollamaBaseUrl` | 預設 `http://127.0.0.1:11434/v1` |
| `ollamaModel` | Ollama 模型名 |

### `[token]`

| 鍵 | 預設 | 範圍 | 說明 |
| --- | --- | --- | --- |
| `maxJeiChars` | 12000 | 1000–12000 | JEI 文字上限 |
| `historyTurns` | 8 | 0–16 | 聊天歷史則數（`0`＝不帶） |
| `maxFacts` | 24 | 4–32 | 事實條數 |

### `[web]`

| 鍵 | 說明 |
| --- | --- |
| `allowWebSearch` | 預設 `true`；有本地覆寫時仍可搜，衝突以本地為準 |
| `tavilyApiKey` | 選用（或 env） |
| `serperApiKey` | 選用（或 env） |

### `[ui]`

| 鍵 | 預設 | 說明 |
| --- | --- | --- |
| `sidebarSide` | `right` | 側欄 `left` / `right` |
| `preferObtain` | `craft` | `craft` / `quest` / `loot` / `balanced` |
| `recipeCategoryOrder` | （空） | JEI 類別 UID 優先序（`;` 分隔） |
| `recipeCategoryHidden` | （空） | 隱藏的類別 UID |

`bridge/` 僅供參考／舊測試，玩家不必安裝。
