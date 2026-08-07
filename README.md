# Pack AI Assistant

客戶端模組：把 jar 丟進 `mods` 即可。不需另外跑 Python Bridge。

| 線 | Loader | 狀態 |
| --- | --- | --- |
| `neoforge/1.21.1/` | NeoForge 1.21.1 | Supported |
| `forge/1.19.2/` | Forge 1.19.2 | Supported（Parity） |

倉庫：https://github.com/skps00/Modpack-AI-Assistant  
多版說明：[`docs/VERSIONS.md`](docs/VERSIONS.md) · 找碼：[`docs/SOURCE_MAP.md`](docs/SOURCE_MAP.md)

## 去哪找碼

| 線 | 路徑 |
| --- | --- |
| Forge 1.19.2 | `forge/1.19.2/src/main/java/com/skps9/packai/` |
| NeoForge 1.21.1 | `neoforge/1.21.1/src/main/java/com/skps9/packai/` |

關鍵套件（兩線同構）：`client/knowledge`（**PackKnowledge**）、`client/service`、`client/jei`、`logic`。

瀏覽略過：`bridge/`（LEGACY，見 `bridge/README.md`）、`common/shared/`（空殼 — **禁止**未核准抽 shared）、`mezz/`、`dist/`、`**/build`、`**/run`、`.codegraph`。

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
5. 接 LLM：見下方 **免費／低成本模型怎麼接**；或付費 OpenAI 相容；或本機 [Ollama](https://ollama.com)
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
2. 游標停在物品上 — tooltip 會顯示「按住 **Y** 單獨詢問此物品（會清除多選）」
3. **按住 Y** — 「Hold Y…」那一行會變成 Create Ponder 風格的 `|` 文字進度條（約 1 秒）；完成後只問該物，不帶 InvPick 多選（聊天紀錄保留）
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

## 免費／低成本模型怎麼接（OpenAI 相容）

Pack AI 雲端模式打的是標準 **`POST {apiBaseUrl}/chat/completions`**（Base URL **只要到 `/v1`**）。任何提供這協定的免費層都能用：設 `mode=cloud`（或 `auto`＋有 key）、貼 key、填 Base URL、填模型 id。

> 免費額度／模型名會變；以下為常見接法。金鑰勿提交 git；優先設定頁或 env `PACKAI_API_KEY`。

### 本機免費：Ollama（推薦起步）

1. 裝 [Ollama](https://ollama.com)，執行 `ollama pull llama3.2`（或其他）
2. Pack AI：`mode=ollama`（或 `auto` 且不設雲端 key）
3. 預設 `ollamaBaseUrl=http://127.0.0.1:11434/v1`，`ollamaModel`＝你 pull 的名字  
完全離線、無限額；要夠力模型需本機 GPU／RAM。

### 雲端免費層（直接填 Pack AI）

| 供應商 | 申請 | `apiBaseUrl` | `model` 示例 | 備註 |
| --- | --- | --- | --- | --- |
| [Groq](https://console.groq.com/keys) | 免信用卡（條款以官網為準） | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` 等 | 快；有 RPM／日額 |
| [Google AI Studio / Gemini](https://aistudio.google.com/app/apikey) | 免費額度 | 看官方 **OpenAI 相容** 端點文件（若提供）或用下方閘道 | `gemini-2.0-flash` 等 | 部分產品要閘道才長得像 OpenAI |
| [OpenRouter](https://openrouter.ai/keys) | 有 `:free` 模型 | `https://openrouter.ai/api/v1` | `…:free`（清單見官網 Models） | 日請求少；可當備援 |
| [Cerebras](https://cloud.cerebras.ai/) | 免費層常見 | 官方 OpenAI 相容 base | 官網列出的 chat 模型 | 日 token 額高、context 可能較短 |
| [Mistral](https://console.mistral.ai/) | Experiment／免費層 | 官方 `/v1` | 官網 chat 模型 | RPM 低 |

設定頁：**連線** → Base URL + API key + 模型 → 重整模型清單試連線。  
`apiBaseUrl` **不要**寫到 `/chat/completions`。

### 一鍵聚合多個免費額度（進階）

若要輪流打 Groq／Gemini／OpenRouter／…，可在本機跑 **OpenAI 相容閘道**，再把 Pack AI 指過去，例如：

- [Freeloader](https://github.com/Arnav8452/freeloader)、[RelayFreeLLM](https://github.com/msmarkgu/relayfreellm)、[freellmapi](https://github.com/ACN1987/freellmapi) 等（社群專案，自行評估安全）

典型：`apiBaseUrl=http://127.0.0.1:<port>/v1`，`apiKey` 填閘道要求的任意／自訂 key，`model` 填閘道文件寫的虛擬名（如 `auto`）。

### 實務建議

1. 先 **Ollama** 驗證模組；再換雲端免費層。  
2. 問句上下文偏大（見下節）→ 免費層易撞 rate limit／截斷；把 `maxJeiChars` 降到 **2000–4000**、`historyTurns` 降到 **0–4**。  
3. 小模型請選指令跟隨較好的；回覆格式（`[[item:]]`／`[[recipe:]]`）靠 system prompt，過弱模型會漏標記（配方卡仍由客戶端掛）。

## 行為摘要

### 問答與 UI

- **多輪對話**：上方聊天紀錄可滾輪；下方固定輸入（Enter 送出）；動作按鈕在側欄（設定可選左／右）
- **選物品**：明確勾選要送進 Ask 的物品（最多 8）；**不再**自動附帶手上／整排熱鍵欄
- **輸入列**：JEI 焦點 → `Targeted: …`；有勾選 → 多圖示 + `Picked: N`
- **目標下一步**：預勾熱鍵欄（＋手上）後再問「下一步」
- **跳到最新**／**重新生成**／**清除對話**；關助手畫面不丟紀錄，離開世界會清
- **關閉助手時等回覆**：AI 思考中可關畫面，完成後 toast + 聊天提示；任務連結仍可點
- **推薦物品**：回答後顯示可點圖示
- **配方卡**：每物品最多 `recipeCardsPerItem` 張（預設 3；設定可改 1–8）；總預算 ≈ 選中數 × 每物品。大格（Create 動力合成等）用流程卡列槽位，不硬塞 3×3
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
| `maxJeiChars` | 12000 | 1000–12000 | JEI 文字上限（**最大 token 成本**） |
| `historyTurns` | 8 | 0–16 | 聊天歷史則數（`0`＝不帶） |
| `maxFacts` | 24 | 4–32 | 事實條數 |

#### 每次 Ask 送進 LLM 什麼？（token 粗估）

協定：`messages` = **1× system** + **history 輪** + **1× user（JSON 字串）**。  
配方**卡圖不送模型**（客戶端渲染）；只送文字。估算：英文 ≈ chars÷4；中文常再 ×1.5–2。數字會隨語系／回覆長度變動。

| 區塊 | 內容 | Cap／來源 | ~token（EN） |
| --- | --- | --- | --- |
| **system** | `llm_system_lead` | lang | ~60 |
| | `fact_check` | lang | ~670 |
| | `llm_style` + craft hint + sources + `reply_pattern` | lang | ~670 |
| | `llm_rules.*` | 短 | ~50–80 |
| | 可選 `config/packai/AGENTS.md` | ≤4000 字 | 0–1000 |
| **history** | 最近對話 | `historyTurns` 預設 8 | 常 1k–4k（可變） |
| **user JSON** | `question` | 玩家輸入 | ~20–100 |
| | `heldItem`／`selectedItems`／`alsoSelected` | ≤8 物 | ~50–200 |
| | `jei`（焦點） | `maxJeiChars` 預設 **12000** | ≤~3000 |
| | `jei` extras（多選） | **1800×7 ≈ 12600** 字 | ≤~3150 |
| | `purpose`（tooltip／燃料／ToolAction／`[GUIDE]`＋extras） | 實務數 k 字 | ~0.5k–2k |
| | `graphFacts`（任務／掉落／網等） | `maxFacts` 預設 24 行 | ~200–500 |
| | `sources`／`focusMods` | 小 | ~50 |

**合計（input，粗估）**

| 情境 | ~tokens |
| --- | --- |
| 單物、典型（JEI 2–4k、history 中等） | **~4k–8k** |
| `maxJeiChars=12000`、單物灌滿 | **~8k–12k** |
| 8 選最糟（焦點＋extras JEI 滿＋purpose＋history） | **~12k–20k+** |

Output（模型回覆）另計，通常數百～2k。

**省錢：** 設定把 `maxJeiChars` → 2000–4000、`historyTurns` → 0–4、`maxFacts` 降低；少多選；關網搜／相關任務。多選 extras JEI 上限在程式內（非 toml 鍵）。

提示字串檔：`assets/packai/lang/{en_us,zh_tw,zh_cn}.json` 的 `packai.reply.llm_style`／`fact_check`／`reply_pattern`（可用 `tests/update_reply_prompts.py` 維護）。

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
| `recipeCardsPerItem` | `3` | 每物品最多幾張配方卡（1–8）；總預算 ≈ 選中數 × 此值 |
| `attachRelatedQuests` | （見設定） | 是否附加相關任務 |
| `questMatchHotbar` | `false` | 是否用勾選 extras 對任務計分 |
| `scanModJars` | `false` | 可選 light jar 索引（見下）；**預設關閉** |
| `unpackStoredItems` | `false` | Ask PURPOSE 展開潛影盒／捆包等容器內容為 `[CONTAINED]`；**預設關閉** |

### 可選：`scanModJars`（light jar index）

- **預設 `false`**（Forge／Neo 皆同）— 大包全掃 `mods/*.jar` 可能慢／占磁碟
- 開啟：Mods → Pack AI → **Ask**「掃描模組 jar」，或 toml 設 `scanModJars = true`
- 行為：背景只讀 zip 條目（`data/**/recipes`、`loot_tables`，**不反編譯**）→ 快取 `config/packai/jar-cache/`；Ask 焦點物可注入短 `[JAR]` 提示
- 指紋＝zip **中央目錄**（名稱＋CRC＋size）SHA-256；未變 jar 跳過重掃；單條目／每 jar／每物品有 cap

`bridge/`：**LEGACY** — 非 runtime Pack AI；僅供參考／舊測試，玩家不必安裝（見 `bridge/README.md`）。
