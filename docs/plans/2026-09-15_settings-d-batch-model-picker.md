# 2026-09-15 — D 批：模型選擇合併成一個掣 ＋ 長文字唔夠位（Settings V2）

> 狀態：**code 完成**（2026-09-15；forge-only）。待 jar 部署＋SK 真機驗收。
> 依 AGENTS.md：plan → 反方 review（交付物 A 級 9:1）→ 才派 cursor 實作。
> 前置：**C-1（設定 4/5/6）跑完先派呢批**（避免兩個 agent 同時改同一批檔）——已滿足。

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
1. 只剩**一行「模型」**，顯示當前模型 ＋（雲端／本機／停用／雲端・未設 key，睇 §5A.5）
2. 打開 → **兩個分節**；揀雲端 → `model` 變；揀本機 → `ollamaModel` 變；**重開 highlight 要喺正確嗰節**（唔准跟 mode 走）
3. 搜尋框有效；**空節唔顯示**；fetch 失敗／未連 Ollama 時出**狀態文案**（唔准出假清單）
4. **文字夠唔夠位（可證偽，R2 修正後）**：
   - 描述板：title ＋ **最多 3 行 wrap**，截斷時尾隨 `…（Shift 睇全部）`
   - **按住 Shift** → 用 `Font.split` 先切好嘅**完整內容**以 vanilla 風格 tooltip 顯示；**最長 tooltip（en 519 字元）必須 Shift 下全顯示**
   - 量測門檻（實作後由 Hermes 用同一個計法核）：46 個 `packai.settings.tooltip`，喺 **240px／en** 同 **240px／zh_tw** 之下，panel 睇得晒嘅比例要**寫實際數字**（en 預期 ≈28% 完全顯示、其餘 Shift）——唔准寫「全部睇得晒」呢類空話
   - 編輯中嘅行：輸入框**由 label 之後開始**（`boxX = r.x + labelW + 4`），**label 一定睇得到**；長值（URL／pattern）喺框內橫向滾動可讀
   - 標籤同值**唔准疊**（值按實際像素寬截斷）

**機械閘（cursor 要加，Hermes 親驗）**
- `tests/check_settings_setters.py` **唔准改弱**（UI_KEYS 仍要見到 `ollamaModel`）；`check_settings_registry.py` **唔准加 EXCLUDED**
- 新閘：picker 源碼必須**同時**有 `setCloudModel` 同 `setOllamaModel`；`hiddenInUi` entry 唔會被 render／search 命中，但 reset 仍覆蓋（harness）
- 幾何閘（`SettingsLayoutCheck`）加**行數 assert**（240／256／270 可見行 ≥4）＋**唔重疊／唔出界**（三解析度）
- `tests/check_settings_render_order.py` **升級成 allowlist**（5B.6）＋改名負對照
- 全量 `tests/check_*.py` 相對 baseline（**113 檔**）冇新增紅；compile ＋ harness OK
- **負對照**：改壞每個新閘／升級後嘅閘要見到紅

## 3. 風險／回滾

- 全部 client-only、無資料遷移；`ollamaModel` 只係**唔喺 UI 顯示**，值唔會消失（TOML 仍可改）→ 回滾＝還原 registry/picker 兩處
- 描述板加高會**影響落稿高度**（240／256／270）→ 一定要跑幾何閘＋真機三高度，避免新症狀
- **唔准動**：`super.render` 次序契約（C-0 建立嘅「自繪 → widgets → tips 最後」）、JEI 拖曳排序。
  （⚠️ R2 修正：`tests/check_settings_render_order.py` **要升級**（5B.6 allowlist），唔係「唔准動」——唔准嘅係**改弱**佢。）

## 4. 派工（C-1 完成後）
- cursor-agent 實作（只 forge 樹；**唔准 commit**）；Hermes 親驗（compile／harness／全量 check／負對照）→ 部署 → SK 真機驗收

---

## 6. D 批落地紀錄（2026-09-15 21:2x，Hermes 親驗）

**實作**（cursor D ＋ D2）
- `SettingsRegistry`：加 `hiddenInUi`；`SettingsScreenV2.applyFilter :278-286` 跳過 hidden（render／maxScroll／search 全部由 `filtered` 派生）；
  `llm.ollamaModel` entry **保留**（reset／setters 閘照覆蓋）
- `ModelPickerScreen`：`Target{CLOUD,LOCAL}` ＋ `Row{HEADER,STATUS,MODEL}`；section 標題／空節邏輯；**寫入用 `setCloudModel`／`setOllamaModel`**（`setUiModel` 已離場）；
  highlight 用該節當前值；搜尋 filter 保留 header、丟空節；footer 同 tag 同源
- `ModelCatalog`：picker 開 → `refreshAsync(true, …)` 兩個後端；CAS 失敗 → `pendingDone` 暫存 callback、完成後再跑一次（`ModelCatalog:52/114/139`）
- **tag 單一來源**：`PackAiConfig.effectiveModelTagKey()`（offline→停用／ollama→本機／否則雲端／空 key→雲端・未設 key）；
  `SettingsScreenV2:470` ＋ `ModelPickerScreen:361` 同源；lang `model_tag.{offline,local,cloud,cloud_no_key}` × 3 語言
- 描述板：`Font.split` 分段（先按 `\n` 分段、**唔再** `replace('\n',' ')`）＋ 最多 3 行（`DESC_BODY_MAX_LINES`）＋`…（Shift 睇全部）`；
  Shift 全文 → 新 `ShiftTipHost`＋`WidgetCompat` 內 `split(...)`，**行返 `renderHoveredTips()` 同一 post-super 路徑**（唔撞 allowlist）
- 幾何：`DESC_DOCK_H 36 → 56`；`SettingsLayoutCheck` 加 **可見行數 ≥4**（240／256／270）；`statusOk` 由 `height-48` 移走（修舊重疊）
- 編輯框：`labelCap = 0.55*r.w`、`labelW = min(font.width(label), labelCap)`、`boxX = r.x + labelW + 4`；label 永遠照畫；值按像素寬截斷（唔疊 label）
- 閘：`check_settings_render_order.py` **升級成 allowlist**（post-super 只准 `renderHoveredTips`，fallback 額外准 `mutedCentered`；**閘內自帶注入式負對照**）；
  新 `tests/check_settings_model_picker.py`（兩 backend setter／hiddenInUi／Row 結構／boxX 三式＋兩禁式／tag 單一來源＋四 key 三語）；
  新 harness `ModelPickerRowsCheck`（6 子斷言）

**Hermes 親驗**
| 項 | 結果 |
|---|---|
| `compileJava compileTestJava --rerun-tasks` | **BUILD SUCCESSFUL** |
| harness ×7（＋`ModelPickerRowsCheck`） | **全部 OK** |
| 全量 `tests/check_*.py` | **114 檔全綠** |
| 負對照 A：`setOllamaModel` → `setCloudModel` | **紅**：`missing PackAiConfig.setOllamaModel(` |
| 負對照 B：`boxX = r.x` | **紅**：`must use boxX = r.x + labelW + 4` ＋ `must not use boxX = r.x` |
| 負對照 C：次序閘**自帶**（注入 `paintDescPanel` 於 super 後須紅） | 通過 → 改名繞路已封 |
| 還原後 | md5 一致（`af1d052f…`／`176040fe…`／`f7abe06d…`）、閘回綠 |

**⚠️ 我（Hermes）第一次檢查出錯，已更正**：我 grep 中文字面（本機／停用）而唔中 tag，一度報「tag 未實作」；
實際上 tag 係 **lang key 間接**（`model_tag.*`）→ 已實作。教訓：核 lang-key 間接，唔准只 grep 中文字面。

**jar**：`57e59a060ba40062` 已部署（含 C-0＋B1 卡 log＋C-1＋D）。待 SK 真機驗收 §2 四項。

---

## 5. R1 反方吸收（v2；比分 P1 反方7:正方3、P2 反方6:正方4 → 未達標，以下逐條改成可執行規格）

**反方全部指控我都用實檔核實過（唔係照抄）**：`check_settings_setters.py:92-97` UI_KEYS 確含 `"ollamaModel"` ＋ `:170-173` 斷言字串要喺 `SettingsScreenV2.java`；
`SettingsLayoutCheck:34-35` 確有 `assert h240.descAsOverlay` ＋ `descPanel.h == DESC_DOCK_H`；
`ModelCatalog.refreshAsync(false)` 喺 mode=cloud／offline 時**唔會** fetch ollama（`:78-88`）；`statusOk` 畫喺 `height-48`（`:946`）＝同 docked desc panel 區重疊（舊症）。

### 5A. P1 修正

1. **唔移除 registry entry（推翻 §1 步 1）**——`llm.ollamaModel` **保留**喺 `SettingsRegistry`，改用**新欄位 `hiddenInUi`**：
   Screen 嘅 render／search filter／`maxScroll` 全部**跳過 hidden entry**。
   理由（R1 O1/O2/O3 三條同時解）：① `check_settings_registry.py` 冇能力偵測移除（EXCLUDED 只係永久豁免，違反 fail-closed）② 真正會紅嘅係
   `check_settings_setters.py` 嘅 UI_KEYS（`ollamaModel` 必須仍喺 `SettingsScreenV2.java` 出現）③ `resetPage()`／`resetAll()` 只走 registry →
   移除行之後**重設唔會再還原 `OLLAMA_MODEL`**（靜默退化）。**唔准**改 EXCLUDED 清單。
2. **picker 兩節要強制 fetch 兩個 backend**（R1 O4）：開 picker 時用 `refreshAsync(true, …)`（force → 兩個都 fetch），
   **唔准**只讀 `cachedOrFallback(true)`（mode=cloud/offline 時佢只回硬編 `OLLAMA_FALLBACK`＝假清單）。
3. **fetch 失敗／空清單唔准當清單**：每節顯示狀態文案 + **當前已配置嗰個值永遠列喺頂**（可再揀返）；
   本機節文案要提 `ollamaBaseUrl`；雲端節空白要提 API key／base URL。
4. **refresh 併發吞 callback（R1 O5）**：CAS 失敗時要**記 pending、完成後再跑一次**（唔准靜默丟），並 log 一行。
5. **tag 語意寫死（R1 O6）**：mode=offline → `模型：<值>（停用）`；mode 用 ollama → `（本機）`；否則 `（雲端）`，**雲端而 apiKey 空白** → `（雲端・未設 key）`；
   picker 底部一行提示跟同一來源（唔准各處自己判斷）。
6. **行模型要跟分節（R1 O7）**：picker 內部改成 `List<Row>`（`HEADER` 或 `MODEL{target,modelId}`），
   `mouseClicked`／`visibleRows`／`maxScroll`／`applyFilter` 全部改行 row list；**header 唔可以被揀**；加閘測 index 對應。
7. **閘要正向（R1 O8）**：① picker 源碼必須**同時**含 `setCloudModel` 同 `setOllamaModel`（唔准只用「唔准有 setUiModel」呢種負向 grep）
   ② harness 測 `Target` 對映（local row → `OLLAMA_MODEL`、cloud row → `MODEL`）③ highlight 必須用**該節對應嘅當前值**（結構性斷言唔准用 `uiModel()`）。

### 5B. P2 修正

1. **編輯框唔准蓋 label（R1 P2-A / flip③）**：`boxX = r.x + labelW + 4`（`labelW = min(font.width(label), 0.55*r.w)`）；label **永遠照畫**；
   閘要 assert `boxX >= r.x + labelW + 4`。唔准改成「畫喺 widgets 之後」（會撞 C-0 次序契約）。
2. **3 行 cap 要有出路（flip②）**：panel 顯示 title ＋ 最多 3 行 wrap ＋ `…（Shift 睇全部）`；
   **按住 Shift = 顯示完整內容 tooltip**。⚠️ **R2 更正**：1.19.2 嘅 `Screen.renderTooltip(PoseStack, Component, int, int)`
   **唔會按寬度斷行**（實測 javap：只 `Arrays.asList(text.getVisualOrderText())` → 單行）→ **必須先自己 `Font.split(tip, wrap)`**
   （repo 已有先例 `WidgetCompat.tipLines:44`）；而且嗰個 tooltip **要喺 `renderHoveredTips()` 同一路徑／同一 post-super 位置畫**，
   否則會撞 5B.6 嘅 allowlist（post-super 只准 `renderHoveredTips`）。
3. **段落要保留（flip④）**：`\n` 唔再 `replace('\n',' ')` → 先按 `\n` 分段，每段各自 wrap。
4. **高度要寫死上限＋harness 加 assert（flip①）**：docked desc 高度 = title ＋ 3 行 ＋ padding（約 46–48）；
   **D 唔准令 240／256／270 嘅可見行數少過 4**；`SettingsLayoutCheck` 除 overlap 外要加**行數 assert**；
   **`DESC_OVERLAY_BELOW_H` 唔准升過 270**（升就會令 `assert h270` 反轉 → 自我指涉閘）。
5. **順手修舊症**：`statusOk`（`:946` 畫喺 `height-48`）同 docked panel 重疊 → 移去 panel 上方／panel footer，並加閘。
6. **C-0 次序閘要加強（R1 P2-C，我確認係真 gap）**：`check_settings_render_order.py` 而家只認**封閉 name set** → 改名（例 `paintDescPanel`）放喺 `super.render` 之後仍全綠。
   改成 **allowlist**：`super.render` 之後**只准** `renderHoveredTips`（fallback 分支額外准 `mutedCentered`），任何其他 draw call 一律紅；
   負對照：新增 `paintDescPanel(...)` 放喺 `super.render` 之後 → 要紅。
7. **驗收改成可證偽**：寫明量測（46 個 `packai.settings.tooltip`：240px／en 同 zh_tw 各要有幾多個喺 panel 睇得晒、其餘全部要用 Shift 睇得晒；最長 519 字元必須 Shift 全顯示），
   唔准寫「多行睇得晒（最多 3 行）」呢種自相矛盾句。

**下一輪（R2）只核 5A 七條 ＋ 5B 七條，唔准加新要求。**
