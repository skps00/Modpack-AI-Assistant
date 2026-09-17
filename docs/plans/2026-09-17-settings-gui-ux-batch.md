# Plan A v2 — Settings GUI/UX（數字行可直接輸入、移除重複 hover tooltip）＋可選 overlay 修正

> 狀態：**v2（2026-09-17 12:0x）** — 依 R1 反方 findings 重寫（subagent 3:7 ＋ cursor 3:7，兩邊一致）。
> 開工條件：再跑 review 達 **正方 ≥8 : 反方 ≤2**。
> ⚠️ **v1 有兩個前提係錯，已撤回**（詳 §0）。

## §0 撤回項（v1 → v2，反方證據）
1. **撤回「值欄貼死右邊／toggle 兩種對齊」**：實測同一張圖 toggle 綠填右緣 `xmax=2110`、值文字 `2112`、panel 內框 `2135-2138` → **同一右緣（`right-4`）**，邊距約 **5.75 GUI px**；`SettingsScreenV2.java:1029` 嘅 `-16` 係 **12px 控件定位**（`tx+12`），唔係文字內縮。v1 量到嘅「貼邊」係量錯個**白色框＝hover tooltip 邊框**。
2. **撤回「描述板遮住列表行（SK 症狀）」**：`docs/plans/assets/2026-09-17-settings-descpanel-overlap.png` 個白框實測 **306.75×25.75 GUI px、文字純白 (252,252,252)、零 ACCENT/MUTED**；描述板必然出 ACCENT（`GuiShell.java:20`）＋MUTED（`:23`）→ 佢係 **hover tooltip**＝同 §2 第 2 項同一件事。SK 實機（`options.txt guiScale:4`，視窗 ~2200×1105 → 邏輯 ~555×276 或 480×270，**≥260**）行 **docked**，今日唔會走 overlay。
3. **撤回「C-0 gate 要新增反向對照」**：`tests/check_settings_render_order.py:222 negative_control_paint_after_super()` **已存在**（實跑 baseline 綠）。
4. **撤回「還原 = `git revert` 單一 commit」**：`git ls-files …/client/gui/settings/` = **0**（成套未入 git）→ 要先建還原點（見 §4）。
5. **撤回「`IntegerValue.getMinValue()/getMaxValue()`」**：Forge 1.19.2 `javap` 實證冇（NeoForge 命名）。

## §1 目標
令玩家（SK）① **數字類設定可以直接打字**（唔止揀 preset）② **同一個機制唔會講兩次**（row tooltip 同底部描述板重複）。

## §2 兩個真問題（v2 範圍）
- **A-1 數字行唔可以打字**：`SettingsScreenV2.java:505-507` `case NUMBER → cycleNumber(...)`（只有 preset）；preset 清單 `numberOptions()` `:590-604`、`DAILY_TOKENS` `:65-66`；config 值域 `PackAiConfig.java:331-334`（`0..100_000_000`）。
  - ⚠️ **危險位（反方 HIGH）**：`SettingsRegistry.java:224` `v -> PackAiConfig.setDailyTokenLimit(parseInt(v, 0))` → 空／非數字會變 **0 ＝ unlimited**（最壞方向：打錯字反而解除上限）。`dailyTokenLimit()` getter `PackAiConfig.java:1232-1240` 吞 `Throwable` 回 `DEFAULT_LIMIT`。
- **A-2 tooltip 同描述板重複**：`SettingsScreenV2.java:353` `TipEditBox` 掛 `Component.translatable(e.tooltipKey)` ←→ 描述板 `:1149-1152` 讀**同一個 key**；hover tooltip 由 `WidgetCompat.renderHoveredTips` 喺 `super.render` 之後畫（`:933`、`:974`）。
  - ⚠️ **唔可以一刀切**：另外 6 個 tooltip **唔重複**——搜尋框 `:123-137`、5 粒掣 `:146-193`（Done／Reset All／Reset Page／Knowledge Test／Clear Cache，含**破壞性** Reset All）→ 要保留。
- **A-3（可選，唔係 SK 報嘅）overlay 分支潛在遮蓋**：`SettingsLayout.java:110`（`screenH < 260`）＋`:125-127`；模擬 h=240 時 `desc_y=156 < last_row_bottom=202` → overlay 設計上佔 list 帶。SK 實機唔受影響。**做唔做由 SK 話事**（我建議做，因為公眾玩家有細視窗）。

## §3 設計
- **D1（A-1）**：NUMBER 行 click → 開輸入框（`startEdit` 基建 `:499-528` 已有）；commit 走**新純函數** `parseNumberInput(String raw, int old, int min, int max)`：
  - 空／非數字 → **唔 call setter**、保留舊值（**唔准** fallback 0）；
  - 數字 → clamp 後 set；clamp 執行點＝現有 `PackAiConfig.setDailyTokenLimit`（`Math.max(0,Math.min(100_000_000,n))`）——**唔聲稱**由 spec 讀 min/max（spec tree 讀取要另開交付物，見 §6）。
  - preset 保留方式：**`Shift+滾輪`／`Shift+點擊`** 做 preset 微調（**新增**；今日兩者都冇：`:1296-1310` 普通滾輪＝捲清單，零 Shift 處理）→ **普通滾輪維持捲清單，唔准搶**。
- **D2（A-2）**：只將 **row widget** 嘅 tip 傳 `null`（TextEditBox／TipEditBox 唔傳 `tooltipKey`）→ row 就唔會有 hover tooltip；**保留**搜尋框＋5 粒掣嘅 tip；**唔改** `WidgetCompat.renderHoveredTips` 行為。
  - 描述板＋`Shift` 全文（`shiftTipLines` `:1186-1201`）成為唯一出口。
- **D3（A-3，可選）**：`entryList` 高度收窄到 `descPanel.y - 2`（同 overlay 分支一致）；接受「overlay 時可視行數減少」。
- **D4**：本 plan **零 lang／零 config 改動**（文案一律 Plan B 負責）。
- **D5**：影響面＝`client/gui/settings/SettingsScreenV2.java`、`client/gui/settings/SettingsRegistry.java`（NUMBER validator）、`tests/check_settings_*.py`（閘擴充）；**唔改** `WidgetCompat.java`／`PackAiConfig.java`／lang。

## §4 還原點（先做）
1. `git status` 確認 `client/gui/settings/` 係 untracked → **先**做兩種其一：(a) timestamped copy：`cp -r …/settings/ backups/2026-09-17_settings_<HHMM>/` ＋ `md5sum` 清單；(b) 或 commit 一次（需要 SK 批准，因為唔屬 0.2.2 批次）。
2. 還原步驟寫明「copy 返 ＋ md5 對比」。

## §5 驗收（S1–S9）
- **S1** 文字／edit inset 用常數：source 級斷言（`:346`、`:1043` 表達式含 `ROW_RIGHT_INSET`）；**唔准**盲禁 `:1029` 嘅 `-16` → 今日紅。
- **S2** toggle 幾何**不變**（防誤殺）：斷言 toggle 右緣 == `r.right()-4`（擴 `SettingsLayoutCheck.java:88-93` 風格）→ 今日綠（regression）。
- **S3** `parseNumberInput` 純函數 4 case：`("50000",10000)→50000`、`("999999999",10000)→100_000_000`、`("",…)`／`("abc",…)`→**舊值**、`("-5",…)→0` → 今日紅（函數未存在）。
- **S4** NUMBER commit 唔會變 0：source 級掃「`parseInt(v, 0)` 唔准出現喺 NUMBER 路徑」＋斷言 validator 存在 → 今日紅。
- **S5** row widget 唔掛 tip：python 掃 `tooltipKey` 只准出現喺 descPanel／Shift 路徑；擴現有 `negative_control_paint_after_super()` 覆蓋（唔偽稱從零加）→ 今日紅。
- **S6** 6 個非重複 tooltip 保住：斷言 `renderHoveredTips` 仍被呼叫（search／buttons）→ 今日綠→改後仍綠。
- **S7** Shift 全文可測：抽 static `shiftTipBody(label, body, maxW)` 純函數，斷言 ≥2 行 → 今日紅。
- **S8** overlay（240/256）`entryRowRects` 最低 bottom ≤ `descPanel.y-2`；另加 270/276 兩行防回歸 → 今日紅（240 現況 False）。
- **S9** `tests/check_settings_*.py` 5 個全綠（baseline 實跑 5×RC=0）＋ 一次性機讀 diag（印 `screenH`／`descAsOverlay`／inset 實際值）→ 綠。
- **S10** 真機：SK 睇 ① 數字行打得入（打 50000 生效）② row 冇 hover tooltip、描述板照出 ③ Shift 睇全文 → 人手最終確認（配合 S9 嘅 diag log）。

## §6 唔准做／唔聲稱
- 唔准聲稱讀到 spec min/max（要另開「config range 讀取 helper」交付物）。
- 唔准用「post-super 零呼叫」做 gate 目標（會令現行正確碼變紅）。
- 唔准改 `WidgetCompat` 共用行為、唔准改 lang／config。

## §7 開工前要 SK 拍板（1 條）
- **Q1 click 語義**：數字行 click ＝ **開輸入框**（推薦）＋ `Shift+滾輪/點擊` 做 preset 微調；定係「click 維持換 preset、另加 ✎ 掣開輸入框」？
- **Q2（A-3）**：overlay（細視窗）潛在遮蓋要唔要順手修？（推薦要，公眾玩家細視窗；但唔係你報嘅症狀。）

## §8 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| R1 | v1 | 3 : 7（subagent）／3 : 7（cursor 第三評審） | ❌ 未過；2 個前提撤回、6 條驗收死 |
| R2 | v2（本檔） | 待跑 | — |
