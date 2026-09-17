# Plan A — Settings GUI/UX 批次（值欄右邊距、數字行可輸入、移除重複 tooltip、描述板唔遮內容）

> 狀態：**v1 草稿（2026-09-17 11:1x）** — 未 review（等反方 R1）。開工條件：反方 review 達 **正方 ≥8 : 反方 ≤2**，才派 cursor 實作。
> 來源：SK 2026-09-17 報「GUI still have some logic or UI/UX problem（暫緩、todo）」＋ 3 張截圖 ＋ 後續對話（「I suggest let user input」、「tooltip is the same purpose of this field, so maybe we can remove tooltips?」）。

## 0. 目標（一句）
令 Settings 頁「**睇得清、改得準、唔重複**」——4 個已確認嘅 UI/UX 問題一次過修，唔改任何 config 語義。

## 1. 問題清單（每條都有代碼／實測錨點）

### P1 值欄貼死右邊界（實測）
- 代碼：值文字右對齊 `r.right() - sw - 4`（`client/gui/settings/SettingsScreenV2.java:1043`）、toggle 內縮 16（`:1029`）、edit box `boxX = r.x + labelW + 4`／`boxW = max(40, r.right() - 4 - boxX)`（`:345-346`）。
- 實測（`docs/plans/assets/2026-09-17-settings-gui-screenshot.png`，4 px per GUI px）：panel 內邊框喺 x=2136，值文字右端 x=**2134** → 只有 **<1 GUI px** 邊距；而 toggle 內縮 16 px → **同一頁兩種對齊**。
- 影響：`http://127.0.0.1:11434/v1`、`deepseek-flash（雲端）` 睇落似被切邊；玩家會以為值顯示唔全。

### P2 數字類設定唔可以打字（SK 要求）
- 代碼：數字行只有 preset 清單 `numberOptions()`（`:590-604`）；`DAILY_TOKENS = 0, 10k, 50k, 100k, 250k, 500k, 1M`（`:65-66`）。
- config 實際值域更大：例 `llm.dailyTokenLimit`（`config/PackAiConfig.java:321-330`，`0 = unlimited`，範圍 0–100,000,000）。
- 實測：SK 截圖顯示 `每日 token 上限 = 10000`，而佢要嘅值打唔到。

### P3 hover tooltip 同底部描述板重複（SK 要求移除 tooltip）
- 同一段文字同時出現喺：① 跟 cursor 走嘅 hover tooltip（`WidgetCompat.renderHoveredTips()`，喺 `super.render` 之後畫，所以永遠喺最上層）② 底部固定描述板（`SettingsScreenV2.renderDesc()` `:1129-1165`）。
- row widget 掛 tooltip：value edit box 用 `TipEditBox`／`tip=Component.translatable(e.tooltipKey)`（`:340-360`）。
- 證據：`docs/plans/assets/2026-09-17-settings-tooltip-duplicates-descpanel.png`（中間 tooltip）＋`...-descpanel-docked-bottom.png`（底部描述板）。
- 額外：hover tooltip 係**列表中間浮出**，會蓋住下面幾行（SK 第 3 張截圖）；外框係 `LegendaryTooltips 1.4.0`＋`Iceberg`（instance 實裝）加嘅白框，唔係 packai 自己畫。

### P4 描述板遮住列表內容（成因未證實，實作前要量）
- 代碼：`SettingsLayout.java:110`（`screenH < DESC_OVERLAY_BELOW_H = 260` → overlay）→ `:125-127` 將 `descPanel` 放入 entry list 帶底部 → 蓋住最後幾行。
- ⚠️ SK 係**全螢幕**＋GUI scale 4（`options.txt` `guiScale:4`；截圖實測 4 px/GUI px）→ 未必行 overlay 分支。**未證實**，實作第一步要加一次性診斷（印 `screenH`／`layout.overlay`／`descPanel` 一次）用真數據定案。

## 2. 設計決策

### D1 統一右內縮
- 新常數 `ROW_RIGHT_INSET = 6`（GUI px；同 `PAD` 同級，`SettingsLayout`）→ 所有 row 內容（值文字、toggle、summary、edit box 右邊）一律 `r.right() - ROW_RIGHT_INSET`。
- **唔准**再喺 `SettingsScreenV2` 出現 `r.right() - 4`／`r.right() - 16` 呢類硬編碼（由 gate 掃）。
- label 上限維持 `labelCap = max(24, r.w * 0.55)`（`:1022`）不變；值仍要 `fitWidth` 截斷。

### D2 數字行支援直接輸入
- 數字行（`ControlType.NUMBER`）用同 string 行一樣嘅 `TipEditBox`：click → 開輸入框（`editingPath`）；只收 `0-9`；**Enter 確認、Esc／click 出面取消**；空／非數字 → 還原原值（**唔准**當 0）。
- clamp 上下限**單一來源**：由 `PackAiConfig` spec 讀（`IntegerValue.getMinValue()/getMaxValue()` 或現有 getter）→ 唔准喺 UI 再寫死一組。
- preset 保留：**滾輪**（scroll）＝ preset 微調、`Shift+click`＝preset 微調；**click ＝ 開輸入框**。⚠️ 需要 SK 拍板（見 §7 Q1）。
- ⚠️ **文案（`0＝不限`、重置時間、高峰提示）一律由 Plan B 負責**，本 plan 唔郁任何 lang 文案（避免兩張 plan 撞同一批 key）。

### D3 移除 row hover tooltip
- Settings 只保留：① 底部描述板 ② `Shift` 全文（`shiftTipLines()` `:1189-1203`）。
- row widget（`TipEditBox` 等）唔再掛 `tooltipKey`；`WidgetCompat.renderHoveredTips` **保持不動**（Ask screen 都用）。
- C-0 gate（`tests/check_settings_render_order.py`，現時 `POST_SUPER_ALLOWED = {"WidgetCompat.renderHoveredTips"}`）要改成「**post-super 零呼叫**」＋**新增反向對照**（故意加一行 post-super call → gate 必須紅）→ 證明閘真會咬。

### D4 描述板唔遮內容
- 先做結構性方案 (c)：`entryList` 高度收到 `descPanel.y - 2`（即列表永遠唔會畫到描述板範圍）；overlay 分支保留做 fallback（極細視窗）。
- 加斷言（S7）：3 個 GUI 高度（320／360／240）下，`entryRowRects(layout, n)` 最低一行 bottom ≤ `descPanel.y - 2`。
- ⚠️ 若 P4 診斷顯示**唔係** overlay 分支，就要另找成因（唔准當 (c) 一定修好）。

## 3. 驗收（S1–S9）

| # | 斷言 | 今日 |
|---|---|---|
| S1 | python gate 掃 `SettingsScreenV2.java`：row 內容繪畫唔准有 `r.right() - 4`／`- 16` 硬編碼；必須用 `ROW_RIGHT_INSET` | **紅**（今日有硬編碼） |
| S2 | edit box 右邊 == `r.right() - ROW_RIGHT_INSET`；`boxX` 公式不變 | **紅** |
| S3 | Java harness（新 `NumberRowInputCheck`）：輸入 `50000` → `dailyTokenLimit()==50000`；輸入 `999999999` → clamp 到 spec 上限；輸入 `abc`／空 → 原值不變 | **紅** |
| S4 | clamp 上下限 == `PackAiConfig` spec 讀出嘅 min/max（單一來源；兩處唔准寫死） | **紅** |
| S5 | C-0 gate 改成 post-super 零呼叫 ＋ 反向對照（加 call → 紅） | **紅** |
| S6 | `shiftTipLines()` 對 highlight row 仍回 label＋body（≥2 行） | 綠（regression guard） |
| S7 | `entryRowRects` 最低 bottom ≤ `descPanel.y - 2`（320／360／240 GUI 高 ×3） | **紅** |
| S8 | `check_settings_registry.py`／`check_settings_setters.py`／`check_settings_c1.py`／`check_settings_model_picker.py` 維持綠 | 綠（regression） |
| S9 | 真機（SK 手動）：值欄有右邊距、數字打到 `50000`、冇 hover tooltip、描述板唔遮行 → 4/4 | 人手 |

## 4. 影響面／還原
- 只改：`client/gui/settings/*`（ScreenV2／Layout／新 harness）＋`client/gui/WidgetCompat.java`（Settings 內唔掛 tooltip）＋`tests/check_settings_render_order.py`（更新）＋3 個 lang 檔（如需新文案：`0＝不限`、輸入提示）。
- 無新 config key（純 UI 行為）；**還原** = `git revert` 單一 commit；jar 由 Hermes 用 `python %LOCALAPPDATA%\hermes\scripts\mc_mod_deploy_jar.py --target packai` 部署（會 backup＋sha256 覆核）。
- 風險：D2 改 click 語義（原本 click＝cycle preset）→ 要 SK 拍板（§7 Q1）。

## 5. Review 狀態
- R1：**未跑**（反方）。計劃：cursor-agent 反方（READ-ONLY，Stage 1 只攻擊）→ 修 → 必要時 R2／R3（上限 3–4 輪，未達 8:2 停手交 SK）。

## 6. 已知脆弱位（實作時要記）
- GUI scale 唔同（1／2／4）下 `ROW_RIGHT_INSET` 唔需要乘（MC screen coords 已係 scaled）→ 但 `fitWidth` 截斷要保留。
- `nearest()`（`:606`）係 preset 用嘅「就近」邏輯，改 D2 時唔准拆。
- 描述板文字本身用 `wrapTooltipBody()`（`:1170-1185`）→ D3 之後描述板係**唯一**長文案出口，要確保 3 行 cap 唔會切走重要資訊（保留 Shift 全文）。

## 7. 開工前要 SK 拍板（1 條）
- **Q1 點擊語義**：(a) click＝開輸入框、滾輪／Shift+click＝preset 微調（我建議）／(b) click 仍＝cycle preset、另加一顆「✎」掣開輸入框。
