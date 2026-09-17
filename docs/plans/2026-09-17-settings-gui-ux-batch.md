# Plan A v5 — 設定頁改善（數字可打字、移除重複跟滑鼠提示、說明板跟滑鼠）

> 狀態：**v6（2026-09-17 13:2x）** — 第 5 輪 6:4 後再修（F2②／F4／F5①③），交第 6 輪有界審查（只核呢幾條）。
> SK 已決定：**1a**（撳落去開輸入框；Ctrl+滑鼠輪揀預設值）、**2y**（細視窗遮住行要順手修）。
> 說明：v1 講「說明板遮住行」係睇錯圖；但 SK 已用 **2y** 覆寫 → **照修**（唔係回滾）。

## §1 交付（3 樣）
1. 數字設定可直接打字（1a）。
2. 設定頁每行唔再彈跟滑鼠提示（同下面說明板重複）＋ 說明板**跟滑鼠指住嗰行**。
3. 細視窗時說明板唔遮住設定行（2y）。

## §2 設計（全部寫死）
### D1 數字輸入（11 個設定，界線表）
接駁方式：11 處 `parseInt(v, fallback)` **全部**改成 `parseNumberInput(v, 現值, min, max)`；非法輸入（空／非數字／負數）→ **保留舊值**。
> 界線表（**唯一真相＝`PackAiConfig` spec**；下表由新檢查對照，唔准兩份走樣）：

| 設定 | 界線 | 來源 |
|---|---|---|
| dailyTokenLimit | 0 – 100,000,000 | `PackAiConfig.java:331-334` |
| maxJeiChars | 1000 – 12000 | `:340` |
| historyTurns | 0 – 16 | `:343` |
| maxFacts | 4 – 32 | `:346` |
| askMaxToolRounds | **1 – 8**（字面；`MAX_LLM_ROUNDS`=3 只係 **default**，唔係界線） | `PackAiConfig.java:324-327`（註釋 `:323` 自寫「Range 1–8」）＋`AskToolLoop.java:31` |
| recipeCardsPerItem | 1 – 8 | `:421` |
| recipeCardsPerItemUse | 1 – 8 | `:425` |
| knowledgeCacheMaxMb | 1 – 512 | `:508` |
| askTraceKeepFiles | `AskTrace.KEEP_MIN`(1) – `KEEP_MAX`(500) | `:309-312`＋`AskTrace.java:33-34` |
| askTraceKeepDays | `KEEP_DAYS_MIN`(0) – `KEEP_DAYS_MAX`(365) | `AskTrace.java:37-38` |
| packIndexClipRadius | 5 – 100 | `:533` |

接駁點（今日）：`SettingsRegistry.java:224`（fallback 0）、`:234`（12000）、`:243`（8）、`:252`（24）、`:270`（3）、`:329`（3）、`:338`（3）、`:467`（32）、`:522`（50）、`:531`（3）、`:558`（30）；helper 在 `:655-664`（今日 `Integer.parseInt(v.trim())`）。

### D2 移除跟滑鼠提示（1a 安全做法）
- `WidgetCompat.java:100-112`（`TipEditBox`；`getTooltip` `:108-111`；ctor `:104` 要 Minecraft 實例）：**加 no-tip 工廠 `editBoxNoTip(...)`**，其 `getTooltip()` 回 `List.of()`；並且 `WidgetCompat.java:42-44` 嘅 `tipLines(Component tip)` **加 null guard**（`tip == null → List.of()`）——雙保險，因為傳 `null` 會直接爆。
- row 掛 tip 位＝`SettingsScreenV2.java:353`（改用工廠）。
- **保留（白名單）**：`:123-137`（搜尋框）、`:146-193`（Done／Reset All／Reset Page／Knowledge Test／Clear Cache）、`:299`（搜尋比對）、`:1149-1152`（說明板）、`:1199`（Shift 全文）。

### D3 說明板跟滑鼠
- 新增 `mouseMoved`（今日全檔 0 個）→ 更新高亮行；並喺**每幀**依最後滑鼠位置重算（因為捲動 `:1296-1309` 只改 `scrollOffset`，唔會 fire 滑鼠移動）。

### D6 細視窗
- **只限 overlay 分支**（`SettingsLayout.java:110`，`screenH < 260`）：`entryList.height = descPanel.y - 2 - entryList.y`（唔可以寫成 `descPanel.y - 2`）。算術：240 高 7 行→**4** 行、256 高 8→**5** 行；`SettingsLayoutCheck.java:36` 下限 `>= 4` 仍成立（餘量 6px）、`:48` `!overlaps` 成立。

## §3 還原點（已做）
`.hermes/backups/2026-09-17_settings_gui/`（3 java ＋ `md5sums.txt`，已核對一致）。

## §4 驗收（S1–S10，逐條寫死 predicate）
- **S1** 純函數 `parseNumberInput`：`("50000",10000)→50000`；`("999999999",10000)→100000000`；`("",10000)`／`("abc",10000)`／`("-5",10000)`→**10000（舊值）** → 今日紅（函數未存在）。
- **S2** ① predicate：`SettingsRegistry.java` 內 **11 個 NUMBER entry** 嘅 setter **唔准出現 `parseInt(`（含 `Integer.parseInt(` 繞路）**（今日 11 處 `:224,234,243,252,270,329,338,467,522,531,558` 全中 → 紅）② 新檢查 `tests/check_settings_number_bounds.py`：**parse** `PackAiConfig.java` 11 組 `defineInRange(` 界線（先例 `tests/check_settings_c1.py:70-72`）**同** `SettingsRegistry` 新表逐組比對（唯一真相＝PackAiConfig；`.py` **唔准**手抄界線）；符號常數只准用喺 `AskTrace.KEEP_MIN/MAX`／`KEEP_DAYS_MIN/MAX`，其餘寫字面。
- **S3** ① source 級：`WidgetCompat.tipLines` 含 null guard（`tip == null` → `List.of()`）② `tipLines((Component) null)`（**必加 cast**：`:41 tipLines(Component)`／`:47 tipLines(String)` overload ambiguous，唔 cast 唔 compile）＋ no-tip 工廠路徑 → 新 harness `src/test/java/com/skps9/packai/client/gui/WidgetCompatTipCheck.java`，命令 `./gradlew.bat -I tmp-check.gradle runWidgetCompatTipCheck`；⚠️ headless 下 `mc == null` ⇒ 行為斷言**恆真**（今日已綠）→ **紅綠由 ① source 級承擔**，② 只算 compile 級 smoke ③ `grep -c editBoxNoTip src/` ≥ 1 → 今日 0（紅）。
- **S4** row 建立點唔傳 tip＋白名單齊（掃 `tooltipKey` 出現位置只准 5 處）→ 今日紅。
- **S5** 6 個具名提示仍在：`SettingsScreenV2.java:130-133`（search）、`:153`（done）、`:162`（reset_all）、`:171`（reset_page）、`:184`（knowledge_test）、`:193`（knowledge_clear_cache）逐個 grep；**唔准**重複 `tests/check_settings_render_order.py:144-149,250-252` 嘅斷言 → 今日綠（防回歸）。
- **S6** ① 說明板 render 嘅行 == 滑鼠指住嗰行（含捲動後）② hover 期間**零設定寫入**（setter 呼叫計數 == 0）→ 今日紅。
- **S7** `Shift` 只做全文；`Ctrl+滑鼠輪` 只改數值；**普通滑鼠輪只改 `scrollOffset`**（`:1296-1309`）→ 今日紅（Ctrl 路徑未存在；普通滾輪部分今日已綠）。
- **S8** 只限 overlay：240／256 最後一行 bottom ≤ `descPanel.y-2`；另加 **270／276** 防回歸（基線由實作寫死）→ 240 今日紅。
- **S9** 一次性機讀診斷：`SettingsScreenV2.renderScreen`（`:964`，由 `:1251` 每幀呼叫）加一行（開頁一次，static 旗標；欄位必須逐字：`this.height`／`this.layout.entryList.h`／`this.layout.descPanel.y`／`this.layout.maxVisibleEntryRows`（`SettingsLayout.java:78`，`maxRows` 唔存在）＋ `import com.skps9.packai.PackAiMod;`）
 `Pack AI settingsLayout screenH={} descAsOverlay={} entryListH={} descY={} maxRows={}`
 讀取：`grep -o "Pack AI settingsLayout.*" <instance>/logs/latest.log`（今日 settings 套件零 log → 紅，實作後綠）。
- **S10** 真機（SK）：① 打 `30000` 生效、`abc` 唔會變 0 ② 每行冇跟滑鼠提示、滑鼠指住即刻見說明 ③ `Shift` 全文 ④ 細視窗唔遮行。

## §5 唔准做
- 唔准傳 `null` 做提示（爆）；唔准改 6 個具名提示；唔准改語言檔／設定檔；唔准用「畫面 super 之後零繪畫」做 gate；唔准全情況套用 D6（只限 overlay）。

## §6 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| 1 | v1 | 3 : 7 | 2 個前提撤回、6 條檢查無效 |
| 2 | v2 | 6 : 4 | 捉到「傳空白提示會爆」「說明板唔跟滑鼠」 |
| 3 | v3 | 7 : 3 | 5 條檢查寫法 |
| 4 | v4 | 7 : 3 | 3 條未寫死（SK 揀 A2 繼續） |
| 5 | v5 | 6 : 4 | F4 修好；F2② 假綠＋唔 compile、F5① `askMaxToolRounds` 界線寫錯（3 vs 真 8） |
| 6 | v6（本檔） | 待跑 | 已修 F2②（cast＋harness 名）／F5①（界線 1–8）／F5③（parse 對照）／F4 欄位名 |
