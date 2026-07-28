# Pack AI Assistant

客戶端模組：把 jar 丟進 `mods` 即可。不需另外跑 Python Bridge。

| 線 | Loader | 狀態 |
| --- | --- | --- |
| `neoforge/1.21.1/` | NeoForge 1.21.1 | Supported |
| `forge/1.19.2/` | Forge 1.19.2 | Supported（Parity） |

倉庫：https://github.com/skps00/Modpack-AI-Assistant  
多版說明：[`docs/VERSIONS.md`](docs/VERSIONS.md)

## 玩家怎麼用

1. 編譯後取 jar（或 repo 根目錄 `dist/` 副本）：
   - Neo：`neoforge/1.21.1/build/libs/packai-*.jar` → `dist/packai-1.21.1-neoforge.jar`
   - Forge：`forge/1.19.2` 用 `.\build-jdk17.bat build` → `dist/packai-1.19.2-forge.jar`
2. **換 jar 前請完全關閉遊戲**，避免語言檔／類別載入失敗
3. （強烈建議）安裝 **JEI**，配方／用途會與遊戲內 R／U 一致
4. Mods → **Pack AI** 設定（**四頁籤**，控件有 tooltip）：
   - **連線**：API key、Base URL、模式、模型、網搜入口
   - **Ask**：JEI 字數／歷史／事實、側欄、材料 NBT／tooltip 門檻
   - **配方**：優先獲取途徑、配方類別、隱藏升級配方
   - **任務**：顯示隱藏任務、附加相關任務、勾選物比對任務
5. 或本機 [Ollama](https://ollama.com) + `ollama pull …`
6. 遊戲內按 **`]`** 開助手（`/ai <問題>` 為備援）
7. 側欄 **選物品**：多選熱鍵欄／背包／盔甲／副手（有 **Curios** 時 Forge／Neo 線也會列出飾品格）；輸入列顯示多圖示 + `Picked: N`
8. 點 **模型** 開啟搜尋選擇；或按 **重整** 更新清單

語系：`en_us`／`zh_tw`／`zh_cn`。選簡中需 jar 含 `zh_cn.json`（缺檔會回落英文，**不會**自動用繁中）。

沒有 API key、Ollama 也沒開時：仍可用任務書導引與本地配方白話說明。有 JEI 時離線也可顯示 JEI 摘要。

## 整合包作者自訂 AI

可在遊戲目錄放 `config/packai/AGENTS.md`，用 Markdown 寫本包玩法／優先途徑／常見坑；Pack AI 會注入 LLM 提示。

詳見 [`docs/PACK_AUTHOR.md`](docs/PACK_AUTHOR.md)，範例：[`docs/examples/packai_AGENTS.md`](docs/examples/packai_AGENTS.md)。  
物品來源離線 SOP（給作者／Agent 查檔，非遊戲內全自動掃 jar）：[`docs/ITEM_SOURCE_LOOKUP.md`](docs/ITEM_SOURCE_LOOKUP.md)。

### JEI／背包「按住思考」（Create Ponder 風格）

1. 開 **背包或 JEI**（按 E）
2. 游標停在物品上 — tooltip 會顯示「按住 **Y** 來用 Pack AI 思考此物品」
3. **按住 Y** — 「Hold Y…」那一行會變成 Create Ponder 風格的 `|` 文字進度條（約 1 秒）
4. 進度滿格 → 自動開啟助手並對該物品提問（釘選目標）

思考中不可再按住提問（tooltip 會提示等待）。可在 **設定 → 控制 → 整合包 AI 助手** 更改 Y 鍵。

### `llm.mode`

在 `config/packai-client.toml` 或 Mods → Pack AI 設定：

| 值 | 說明 |
| --- | --- |
| `auto`（預設） | 有 key → 雲端；否則試 Ollama；否則純本地 |
| `cloud` | 只用雲端（沒 key 會提示錯誤） |
| `ollama` | 只用本機 Ollama（忽略 apiKey） |
| `offline` | 不呼叫 LLM；任務書／JEI／本地白話仍可用 |

## 行為摘要

### 問答與 UI

- **多輪對話**：上方聊天紀錄可滾輪；下方固定輸入（Enter 送出）；動作按鈕在側欄（設定可選左／右）
- **選物品**：明確勾選要送進 Ask 的物品（最多 8）；**不再**自動附帶手上／整排熱鍵欄
- **輸入列**：JEI 焦點 → `Targeted: …`；有勾選 → 多圖示 + `Picked: N`
- **目標下一步**：預勾熱鍵欄（＋手上）後再問「下一步」
- **跳到最新**／**重新生成**／**清除對話**；關助手畫面不丟紀錄，離開世界會清
- **關閉助手時等回覆**：AI 思考中可關畫面，完成後 toast + 聊天提示；任務連結仍可點
- **推薦物品**：回答後顯示可點圖示
- **配方卡**：最多 3 張（合成 3×3 或機器流程；含液體／氣體等 JEI 渲染）
- **任務按鈕**：側欄最多 1 個任務 +「換任務」
- **【來源】**：每則回答結尾列出資料來源
- **語系**：回答語言在提問當下鎖定；動態文案在 `assets/packai/lang`（`packai.reply.*`）

### JEI 與物品上下文

- **焦點**：JEI pin／懸停／問題內 `mod:id` 優先；否則第一個勾選物；**不**自動用主手當焦點
- **完整 JEI 掃描**：R／U／催化劑；過濾 facade／cover／camo 等 spam
- **隱藏升級配方**（預設開）：焦點同 registry id 同時出現在 INPUT＋OUTPUT 的配方略過（如奧術鐵砧插 gem）；設定可關
- **材料標籤**：附魔與 custom NBT（`key≥值`）等
- **配方類別**：可開關顯示並**拖曳**優先序；未自訂時用啟發式排序
- **JEI 以外**：掃描 datapack／KubeJS 掉落、釣魚、交易、腳本配方（細節見 ITEM_SOURCE_LOOKUP；非完整離線 SOP）

### 任務與整合包

- FTB Quests／Heracles：相關任務可點 **開啟任務**；`/packai quest <id>` 也可開
- **附加相關任務**／**任務比對勾選物** 可在設定開關（預設不拿熱鍵欄亂配任務）
- **Serene Seasons**／**Psi**：有安裝且問題相關時附加提示

### 網搜

- 預設開：無 key 時查 **Modrinth** + **Minecraft Wiki**；有 Tavily／Serper 則優先
- 設定 **網搜** 子頁可開關並貼金鑰
- 有本地覆寫時**仍可搜**，衝突以本地／JEI／任務為準

### 其他

- 答案以白話作法／材料為主，不貼物品 ID、檔案路徑或腳本

## 按鍵

| 鍵 | 說明 |
| --- | --- |
| `]` | 開啟 Pack AI 助手（遊戲中） |
| `Y` | 按住思考 JEI／背包懸停物品（GUI 內） |

可在 **設定 → 控制 → 整合包 AI 助手** 自訂。

## 相容

- **可選依賴**：JEI（無 JEI 時略過 R／U）
- **可選依賴**：Curios（Forge 1.19.2／Neo 1.21.1 InvPick 可列飾品格；soft-dep，缺模組不崩潰）
- **可選（非依賴）**：**Untranslated Items**（modid `untranslateditems`）：強制物品顯示次要語系名稱（預設 `en_us`）。Pack AI 讀 `getHoverName()`，與之相容、無硬依賴。主語系為中文且該模組開啟 `replaceItemNames` 時，助手 strip／標籤也可能變英文；若要保留中文主名稱，設 `replaceItemNames=false`
- 小量 Mixin：僅在抓 tooltip 時短暫假裝按住 Shift／Ctrl／Alt
- 索引／LLM／模型清單在背景執行緒

## 開發編譯

### NeoForge 1.21.1（完整功能）

```powershell
# 倉庫根目錄
.\gradlew.bat :neoforge-1.21.1:build
# 產出：neoforge/1.21.1/build/libs/packai-0.1.0.jar
.\gradlew.bat :neoforge-1.21.1:runClient
```

### Forge 1.19.2（Supported / Parity）

```powershell
cd forge\1.19.2
.\build-jdk17.bat build
# 需 JDK 17；產出：forge/1.19.2/build/libs/packai-*.jar
# 遊戲內：`]` 開助手；GUI 按住 Y 思考；JEI 可選（11.8.x）
```

動態回覆／LLM 提示字串：各樹 `src/main/resources/assets/packai/lang/{en_us,zh_tw,zh_cn}.json` 的 `packai.reply.*`。  
可用 `tests/gen_reply_lang_json.py` 批次產生／合併該前綴鍵。

## 設定（`config/packai-client.toml`）

也可在 Mods → **Pack AI** 設定頁調整大部分項目（四頁籤；控件有 tooltip）。

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
| `hideUpgradeRecipes` | `true` | 略過「焦點同 id 當輸入又當輸出」的升級配方 |
| `attachRelatedQuests` | （見設定） | 是否附加相關任務 |
| `questMatchHotbar` | `false` | 是否用勾選 extras 對任務計分 |
| `scanModJars` | `false` | 可選 light jar 索引（見下）；**預設關閉** |

### 可選：`scanModJars`（light jar index）

- **預設 `false`**（Forge／Neo 皆同）— 大包全掃 `mods/*.jar` 可能慢／占磁碟
- 開啟：Mods → Pack AI → **Ask**「掃描模組 jar」，或 toml 設 `scanModJars = true`
- 行為：背景只讀 zip 條目（`data/**/recipes`、`loot_tables`，**不反編譯**）→ 快取 `config/packai/jar-cache/`；Ask 焦點物可注入短 `[JAR]` 提示
- 指紋＝zip **中央目錄**（名稱＋CRC＋size）SHA-256；未變 jar 跳過重掃；單條目／每 jar／每物品有 cap

`bridge/` 僅供參考／舊測試，玩家不必安裝。
