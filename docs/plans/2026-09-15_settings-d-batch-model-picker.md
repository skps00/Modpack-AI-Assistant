# 2026-09-15 — D 批：模型選擇合併成一個掣 ＋ 長文字唔夠位（Settings V2）

> 狀態：**未開工**。SK 2026-09-15 覆「**1 a**」＋「**用一個掣 cover 模型選擇**」＋「**go**」（批准下列設計）。
> 依 AGENTS.md：plan → 反方 review（交付物 A 級 9:1）→ 才派 cursor 實作。
> 前置：**C-1（設定 4/5/6）跑完先派呢批**（避免兩個 agent 同時改同一批檔）。

## 0. 問題（SK 真機回報 ＋ 我讀碼核實）

**P1 兩行「模型」開出嚟一模一樣（真 bug，寫錯欄）**
- `SettingsScreenV2.openModelPicker()`（`:543-546`）**冇帶目標**；picker 唯一 caller 就係呢度（全 repo `new ModelPickerScreen` 只有 1 處）
- `ModelPickerScreen.select()`（`:114`）一律 `PackAiConfig.setUiModel(model)`；而 `setUiModel()`（`PackAiConfig:1346-1352`）係**跟 mode 寫**：
  mode 用 ollama → 寫 `ollamaModel`，否則寫 `model`
- `ModelCatalog.optionsForUi()`（`:54-56`）同 highlight（`ModelPickerScreen:140` `PackAiConfig.uiModel()`）**一樣係 mode 驅動**
- 後果（SK config：`mode="auto"`、`model=deepseek-flash`、`ollamaModel=llama3.2`）：開「Ollama 模型」→ 出**雲端清單**＋highlight 雲端模型＋揀咗係寫**雲端**欄 → 兩行睇落一樣，而且**根本改唔到本機模型**

**P2 長文字睇唔到（4 個位，全部有 line）**
| 位 | 現狀 | 後果 |
|---|---|---|
| 行標籤 | `labelMax = r.w/2 - 8` 就 `...`（`renderEntryList`） | 「Ollama 模型」等長標籤被切 |
| 行值 summary | 超過 18 字 → `substring(0,16)+"..."`（`valueSummary`），**右對齊** | 太闊會**疊住標籤**；長值睇唔到 |
| 編輯中輸入框 | 由 `r.x + r.w/2` 起（`:303` 附近） | **得半行闊**，長 URL／pattern 睇唔到全貌 |
| 描述板 tooltip | **只畫一行**，超闊 `plainSubstrByWidth + "..."`（`renderDesc`） | 長 tooltip（唔少）睇唔到 |

## 1. 設計（SK 批准；參考業界做法）

**業界來源**（Research-first，實查）：
- CSuite：**一個掣覆蓋多個後端**——單一目錄，每個模型聲明可以喺邊度跑（csuite.so/blog/cloud-api-vs-local-runtime）
- Rogo Design System：模型選擇器＝**分節清單（群組標題）＋搜尋**、**當前已選顯示喺掣上**、**選項 >10 就要搜尋**、**空群組標題唔准顯示**（design.rogo.ai/system/selectors）
- Jan：雲／本機切換就係一個下拉（scored.tools/blog/jan-local-ai-desktop-app-review-2026）
- MC 生態：長文字要**按寬度斷行**（1.19.2 `Font.split(...)`；社群有 ToolTipFix／Adaptive Tooltips 專門修呢個通病）

**P1 修法（合併成一個掣）**
1. UI 只留**一行「模型」**（`llm.model` 嗰行），**移除 `llm.ollamaModel` 行**（config key 保留，仍可由 TOML 改）；
   行顯示：`<當前生效模型> （雲端｜本機）`——tag 由 mode 決定（`uiUsesOllamaModel()`）
2. Picker 改成**一個清單兩個分節**：`☁ 雲端`／`💻 本機`（各節標題；**空節唔顯示**；可搜尋；當前已選 highlight）
   - 雲端節 → `ModelCatalog` 雲端清單（`cachedOrFallback(false)`）→ 揀＝`PackAiConfig.setCloudModel(...)`
   - 本機節 → Ollama 清單（`cachedOrFallback(true)`，**唔理 mode**）→ 揀＝`setOllamaModel(...)`
   - **唔准**再呼叫 `setUiModel()`（mode 驅動）做寫入
3. `ModelPickerScreen` 加**明確目標**（構造參數或每列自帶 target），唯一 caller 傳入；`uiModel()` 只留作「邊個生效」判斷
4. 註冊閘要跟：`llm.ollamaModel` 由 registry 移除時，`tests/check_settings_registry.py` 嘅「排除清單」要同步（**要有紅→綠證明**，唔准放寬其他 assert）

**P2 修法**
- 描述板：用 `Font.split` **多行 wrap**（標題 1 行 ＋ 內容 **最多 3 行**，超出才 `...`）→ `SettingsLayout` 描述板高度要跟住加大（docked 由 `DESC_DOCK_H=36` 調到夠放 4 行；overlay 同理），**並更新幾何閘** 同高度門檻（`HEIGHT_MIN/MED/COMFORT`、`DESC_OVERLAY_BELOW_H`）免得撞其他元素
- 行值 summary：改成**按實際像素寬截斷**（`font.width`），並且**先扣掉標籤佔用寬度** → 保證**唔會疊住標籤**
- 編輯中 valueBox：**橫跨全行**（由 `r.x + 4` 到 `r.right() - 4`）→ 長值睇得晒
- 標籤：容許到行闊 **55%**，仍然超長才 `...`（+ 完整標題喺描述板第一行，永遠睇得到）

## 2. 驗收（真機 ＋ 機械閘）

**真機（SK）**
1. 只剩**一行「模型」**，顯示當前模型 ＋（雲端／本機）
2. 打開 → **兩個分節**；揀雲端 → `model` 變；揀本機 → `ollamaModel` 變；重開 highlight 正確
3. 搜尋框有效；**空節唔顯示**
4. 揀一個**長 tooltip** 嘅設定 → 描述板**多行睇得晒**（最多 3 行）；長值（URL／pattern）編輯時**橫跨全行睇得晒**；標籤同值**唔疊**

**機械閘（cursor 要加，Hermes 親驗）**
- `tests/check_settings_registry.py` 更新（排除清單）＋**紅→綠**
- 新閘：picker 寫入目標唔准用 `setUiModel`；`llm.ollamaModel` 行唔應該再喺 UI 出現；`Font.split` 有被描述板用到
- 幾何閘（`SettingsLayoutCheck`）＋新高度：**唔重疊／唔出界**（240／256／270／360 × 三解析度）
- 全量 `tests/check_*.py` 相對 baseline（**112 檔**）冇新增紅；compile ＋ harness OK
- **負對照**：改壞每個新閘要見到紅

## 3. 風險／回滾

- 全部 client-only、無資料遷移；`ollamaModel` 只係**唔喺 UI 顯示**，值唔會消失（TOML 仍可改）→ 回滾＝還原 registry/picker 兩處
- 描述板加高會**影響落稿高度**（240／256／270）→ 一定要跑幾何閘＋真機三高度，避免新症狀
- 唔准動：`super.render` 次序契約（C-0 剛建立）、`tests/check_settings_render_order.py`、JEI 拖曳排序

## 4. 派工（C-1 完成後）
- cursor-agent 實作（只 forge 樹；**唔准 commit**）；Hermes 親驗（compile／harness／全量 check／負對照）→ 部署 → SK 真機驗收
