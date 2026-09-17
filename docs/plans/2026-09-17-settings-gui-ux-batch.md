# Plan A v3 — Settings GUI/UX（數字行可直接輸入、移除重複 hover tooltip ＋ 描述板跟 hover）

> 狀態：**v3（2026-09-17 12:3x）** — 依 R2 反方（正方 6 : 反方 4）逐條修；R3 再評，目標 **正方 ≥8 : 反方 ≤2**。
> R1 3:7 → R2 6:4（7 條 FC：5 條真修好、2 條部分；新洞 N1–N8 已全部處理，見 §0）。

## §0 R2 → v3 修正（逐條）
- **N1（HIGH，NPE）**：v2 寫「row widget 嘅 tip 傳 `null`」會被 `WidgetCompat.java:81-93 TipEditBox.getTooltip()` 直接餵入 `mc.font.split(null, …)`（真 Forge bytecode：`Font.split`→`StringSplitter.splitLines` 零 null check，第一個動作就 `invokeinterface FormattedText.visit`）⇒ hover 即 NPE。
  **修法**：`WidgetCompat` 加 **no-tip factory**（`editBoxNoTip(...)`，內部 `getTooltip()` 回 `List.of()`）＋**唔准**傳 null；加斷言「hover row 輸入框唔 NPE」。
- **N2**：v2 嘅 S5 predicate（全檔掃 `tooltipKey`）會撞合法用途 `SettingsScreenV2.java:299`（`matchesSearch` 用 `tooltipKey` 比對）。
  **修法**：predicate 改為「**row EditBox 建立點**唔傳 tip」＋明列白名單（`matchesSearch :299`、描述板 `:1149-1152`、搜尋框 `:123-137`、5 粒掣 `:146-193`）。
- **N3（MED-HIGH）**：v2 假設「抽走 row tip 後描述板接得上」——但 `highlightIndex` 只喺 filter／reset／**click** 賦值（`:290/:325/:500/:1236`），**全檔冇 `mouseMoved`**：即讀某行說明一定要 click，而 click 有副作用（TOGGLE 會 flip）。
  **修法**：**新增 `mouseMoved` → `highlightIndex`（描述板跟 hover）**；驗收＝「hover 任意行 → 描述板出該行說明，零副作用」。
- **N4**：`("-5",…) → 0` 係最壞方向（0＝不限）。
  **修法**：負數同空／非數字一樣 → **保留舊值**（S3 改寫）。
- **N5**：S8 係「可選功能」嘅**無條件紅閘**；D3 措辭寫錯（`descPanel.y-2` 會被讀成高度）。
  **修法**：S8 標明「**只喺 Q2＝做 時生效**」；D3 寫清 `entryList.height = descPanel.y - 2 - entryList.y`，並明寫 overlay 240 可見行 **7 → 4**（貼近 `SettingsLayoutCheck.java:36` 嘅 `>= 4` 下限）→ 要同 commit 更新期望。
- **N6**：「唯一 clamp 執行點」唔成立（`parseNumberInput` 自己都會 clamp）。
  **修法**：明寫**兩層**：輸入層 `parseNumberInput`（拒絕非法輸入）＋設定層 `setDailyTokenLimit`（最終上下限，防禦）；並**明寫 11 條 NUMBER 行嘅 (min,max) 來源**＝每個 NUMBER entry 自帶常數、集中喺 `SettingsRegistry` 新增嘅 spec 表（唔讀 `ForgeConfigSpec` tree）。
- **N7**：`Shift` 一鍵兩義（全文 vs preset 微調）。
  **修法**：`Shift` 保留「睇全文」；preset 微調改用 **`Ctrl+滾輪`**（新增；普通滾輪維持捲清單）。
- **N8**：文件不一致（標題 S1–S9 vs 實際 S10；備份路徑寫成「未做」）。
  **修法**：本版更正；還原點**已做** = `.hermes/backups/2026-09-17_settings_gui/`（3 個 java ＋ `md5sums.txt`，md5 已對上）。
- **撤回 S1**：S1 為已撤回嘅 P1（值欄貼死）造閘 → 刪（右內縮只留「偏好備註」，唔入驗收）。

## §1 範圍（最終）
- **A-1 數字類設定可以直接打字**（SK 要求）。
- **A-2 移除 row 重複 hover tooltip**（SK 要求）＋ **描述板跟 hover**（N3 必要配套）。
- **A-3（可選）** overlay（`screenH < 260`）描述帶遮 list 帶 → 只喺 SK 答 Q2＝做 時生效。

## §2 設計
- **D1（A-1）**：NUMBER 行 click → 開輸入框（`startEdit` 基建 `:499-528`）；commit 經新純函數 `parseNumberInput(String raw, int old, int min, int max)`：
  - 空／非數字／**負數** → **唔 call setter、保留舊值**；
  - 數字 → clamp 後 set（上限由 entry 常數決定）；
  - preset 保留＝`Ctrl+滾輪` 微調（新增）；**普通滾輪＝捲清單（唔准搶）**。
- **D2（A-2）**：row 嘅 EditBox 用 **`WidgetCompat.editBoxNoTip(...)`**（`getTooltip()` 回 `List.of()`）；**唔准**傳 null；**保留**搜尋框＋5 粒掣嘅 tip；**唔改** `renderHoveredTips` 行為。
- **D3（N3）**：新增 `mouseMoved` override → 更新 `highlightIndex`（同 click 更新嘅係同一個欄位，click 仍照舊）→ 描述板跟 hover；`Shift` 行為不變（全文）。
- **D4**：本 plan **零 lang／零 config 改動**。
- **D5**：影響面＝`client/gui/settings/SettingsScreenV2.java`、`client/gui/settings/SettingsRegistry.java`（NUMBER 常數表＋validator）、`client/gui/WidgetCompat.java`（**只加** no-tip factory）、`tests/check_settings_*.py`。

## §3 還原點（已做）
`.hermes/backups/2026-09-17_settings_gui/settings/*.java` ＋ `md5sums.txt`（3/3 md5 已對上；`git ls-files` 該目錄＝0 → 唔可以靠 git revert）。還原＝copy 返 ＋ md5 對比。

## §4 驗收（S1–S10）
- **S1** `parseNumberInput` 4 case：`("50000",10000)→50000`；`("999999999",10000)→100_000_000`；`("",…)`／`("abc",…)`／`("-5",…)`→**舊值** → 今日紅。
- **S2** NUMBER commit 唔會變 0：source 級掃「NUMBER 路徑唔准出現 `parseInt(v, 0)`」＋validator 存在 → 今日紅。
- **S3** hover row 輸入框**唔 NPE**：`-ea` harness（造 no-tip editBox → `getTooltip()`）＋真機 hover → 今日紅（factory 未存在）。
- **S4** row 唔掛 tip（白名單 predicate）：掃 **row EditBox 建立點**（唔係全檔掃 `tooltipKey`）→ 今日紅。
- **S5** 6 個非重複 tip 保住：斷言 `renderHoveredTips` 仍被呼叫（search／5 粒掣）→ 今日綠（regression）。
- **S6** 描述板跟 hover：斷言 `mouseMoved` override 存在 ＋ hover 行 → `highlightIndex` 更新（offline 幾何／狀態斷言）→ 今日紅。
- **S7** Shift 一鍵一義：`Shift`＝全文（保留）、`Ctrl+滾輪`＝preset；斷言普通滾輪唔改數值 → 今日紅。
- **S8（條件式）** overlay（240/256）`entryRowRects` 最低 bottom ≤ `descPanel.y-2`；**只喺 Q2＝做 時生效**；另加 270/276 防回歸 → 240 現況紅。
- **S9** `tests/check_settings_*.py` 5 個全綠（baseline 5×RC=0）＋一次性機讀 diag（`screenH`／`descAsOverlay`／inset）→ 綠。
- **S10** 真機：① 數字行打得入（50000 生效、`abc` 唔會變 0）② row 冇 hover tooltip、hover 行即刻見描述板 ③ `Shift` 全文照出 → 人手最終確認（配合 S9 diag）。

## §5 唔准做
- 唔准傳 `null` tip（NPE）；唔准改 `renderHoveredTips`／5 粒掣＋搜尋框嘅 tip；唔准改 lang／config；唔准用 post-super 零呼叫做 gate。

## §6 開工前要 SK 拍板
- **Q1 click 語義**：**a)** click＝開輸入框＋`Ctrl+滾輪`微調（推薦）／**b)** click 照舊換 preset＋另加 ✎ 掣。
- **Q2** overlay（細視窗）潛在遮蓋要唔要順手修？（推薦 y）

## §7 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| R1 | v1 | 3 : 7 ／ 3 : 7 | ❌ 2 個前提撤回、6 條驗收死 |
| R2 | v2 | 6 : 4 | ❌ N1 NPE、N2 predicate、N3 描述板≠hover、N4–N8 |
| R3 | v3（本檔） | 待跑 | — |
