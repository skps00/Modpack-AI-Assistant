# Plan：Ask 答案層修復（JEI 真資料被 TOOL_MISS 壓過 ＋ 失敗要可見 ＋ 提示再問）（2026-09-14）

狀態：**待 SK 批准**（批准後只經 cursor-agent 實作，只改 `forge/1.19.2`）

## 背景（實機證據，21:18–22:32）
- JEI 對 `golden_age:infinity_sword` 有 **11 條巫師鐵砧改造配方**（`uid=irons_spellbooks:arcane_anvil`，`droppedSelfIO=11 useful=0`）→ 已加 fallback（jar `0e1a35d1`，harness 已驗）
- 提問訊息本身帶 JEI 資料（`jeiLevel=OUTPUT`，740 字），但模型**再自己叫** `jei_lookup(INFO)` → 空 → 系統加註
  `[TOOL_MISS] … the item has no JEI recipe/use listed. Do not invent.` → 模型照跟 → 答「不知道」
- 最終 `display body src=playerfacts`（兜底成事實面板）→ 玩家睇到「唔似答案」
- 同時 `dsmlRecovered=2`（DSML 救援已生效 ✅）

## 四項改動

### 1. 完整 JEI dump 入 log（臨時，有界）
`JeiLookup`／`AskJeiClient` 輸出後印一行 INFO（**每次 ask 最多一次**）：
`Pack AI JEI dump item=<id> len=<n> sha=<8hex> tail=<最後 300 字>`
（`tail` 用嚟證實「（同物品改造／升級配方）」等尾段行有冇入 prompt；唔准印全份避免爆 log）

### 2. 修「TOOL_MISS 壓過真資料」
`LlmClient.toolMissNote(name,item)`／`JeiLookupAskTool.toolMissNote`：若**本 ask 上下文已有非空 JEI dump**（用 `AskLoopState` 記錄嘅旗標）→ 改寫成：
`[TOOL_MISS] jei_lookup(INFO) empty — INFO 只覆蓋資訊頁；本物品嘅配方／用途資料已在上文 JEI dump，唔准講「查唔到」，需要更詳細就再叫 dump_level=OUTPUT`
→ 冇 JEI dump 時維持原字句（避免 regression）

### 3. 顯示層兜底升級（模型 miss 時用資料，唔止出 facts）
`HonestMiss`／顯示層：當最終文字係 miss（空／過短／純否認）：
1. 若本 ask 有 JEI 行（含改造行）→ 確定性回覆**先引 JEI 行**（≤3 條），後接 facts
2. 若冇 JEI 行 → 維持現行 facts 兜底
3. `display body` log 加 `src=jei+facts|facts`

### 4. 失敗要**可見**＋提示玩家再問一次（SK 2026-09-14 要求）
當 ask 以 miss／工具全空／模型無回應 收尾：
- **畫面**：顯示一行（走 `ReplyLang`，zh_cn／zh_tw／en）
  `⚠ 查詢未完成（%s）。可能係模型或工具出錯，麻煩你再問一次。`（%s = 短原因，例如 `jei_lookup 空`／`模型無回應`）
- **log**：一行結構化 `Pack AI ask fallback reason=<miss|empty|llm_error> codes=[tool:level, …] rounds=<n> dsmlRecovered=<n>`
- 唔准把技術細節（raw 錯誤／stacktrace）顯示畀玩家；log 照記
- 一次性提示，唔准自動重試（避免重複收費）；玩家再問一次即可

## 驗收標準
1. `compileJava compileTestJava` RC=0
2. 新 harness `AskMissNoticeCheck`：miss＋有 JEI 行 → 出「JEI 行 + 提示句」；miss＋冇 JEI 行 → 出「facts + 提示句」；正常答案 → **唔出**提示（negative control）
3. 既有 harness 全綠（`runItemRefCheck` 係 pre-existing gap，排除）
4. 真機：問「寰宇支配之劍」→ ① log 見到 `JEI dump … tail=` 含改造行 ② 答案引到巫師鐵砧改造 ③ 若模型仍 miss → 畫面出「⚠ 查詢未完成…再問一次」＋facts
5. 真機 regression：問「鐵鎬」→ 正常答案、**無**提示句、**無**改造噪音

## 風險 / 還原
- 風險：低（顯示層加字＋log；唯一行為改動係 miss 時多一行提示）
- 還原：`git revert`；jar 用 `%TEMP%\packai_deploy_backup_*`

## 唔喺今次範圍（另開線）
- Token 成本：一次 ask 4 輪 ≈ 40–42k input
- KubeJS bridge `matched=0`（仍 `mode=scan`）
- `runItemRefCheck` bootstrap 缺口
