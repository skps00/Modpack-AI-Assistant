# packai — DSML 偵測對稱化 ＋ Recovery plan **v3**（R2 正方 8:2 → 開工）

- **狀態**：R1 反方 7:3 → v2；**R2 判定 8:2（正方贏）→ 可以開工**（3 處修正已收入 R15）。
- v1 → **v2**：2026-09-13（R1 反方 7:3；反方 2 CRITICAL＋6 HIGH，全部有真 probe 實錘）→ v3（R2 收 R13–R16）
- 前置 research：`docs/plans/2026-09-13-dsml-leak-how-others-solved-it.md`
- 前置工作：T1–T4 已完成（prompt 去矛盾／顯示層 fail-closed／覆蓋清單／收貨工具）

## 0. 第一規則：風險評估
| 項 | 評估 |
|---|---|
| 動作 | 偵測 grammar 對稱化（A）＋ 參數解析修復（B1）＋（有數據才做）hop-limit 出口 recovery（B2） |
| 最壞情況 | ① pattern 改動令 Render thread 卡（**已實測存在**，見 §1 R6）② 解析出錯參數 → 顯示錯卡（純 UI）③ 繞過 `askNativeTools=off` 設定（**明確唔做**，見 §3 F9）|
| 防護 | 用**有界** `{1,4}` 取代 `+`（解 backtracking）；只用 `ALLOWLIST`（13 個內建 tool）唔用 registry；B2 加 explicit recovery budget；全部改動 `git checkout` 可還原 |
| 還原 | ① commit 咗 T1–T4（stable point）② 本次改動未 commit → `git checkout --`；③ 部署前 jar `.bak-<ts>` |
| 判定 | **可做**（零資料損失、可還原） |

## 1. R1 揭發嘅硬事實（全部有真 probe；我原文嘅錯要認）
| # | 事實 | 來源 |
|---|---|---|
| R1 | **真機 `｜｜`（雙 U+FF5C）對偵測層同 parser 完全隱形**：真 body 604 chars、32 段 pipe-run 全部雙 pipe；`hasLeakedToolXml=false`、`parsed calls=0`；把 `｜｜` 正規化做單 `｜` → 即變 true／1 call | 正方 C1b（Java `RealProbe` 直餵真 log bytes） |
| R2 | `:373` 守衛 `hasEmbeddedToolDump` 對雙 pipe 回 false → 跌到 `:389 return round.content()`（原封 leaked 內容）；`:374/:488/:506` 嘅 recovery **真會被執行**（`runCall→run→tool.run`，已有 dedupe／allowlist／budget） | 正方 C2/C2b／NIT-3 |
| R3 | `callFromDsmlParams`（`:674`）**唔讀 `item_id`／`role`／`machine`** → 真實後果：**2 個唔同 invoke 因為 dedupe key `(name,"","")` 相同而塌成 1 個 call**，`argsJson` 空 | 正方 C4（真 probe） |
| R4 | 「只加一輪」冇 budget backing：`hops`（local）同 `AskLoopState.llmRounds` 係兩個 counter，`countSuccessfulLlm()` 只在 `AskEngine:806` 叫一次 → `canLlm()` 幾乎永遠 true | 反方 F3 |
| R5 | 「idempotency 閘」係假：native path 帶完整 `argumentsJson`，recovered path `canonicalizeCall(...,"","")` → fingerprint 唔同，跨路徑 dedupe **擋唔住**（而同一路徑內又會過度 dedupe 塌 call） | 反方 F4 ＋ 正方 C4 |
| R6 | ⚠️ **`+`（多條豎線）會 catastrophic backtracking（已實測）**：Python mirror 20k 豎線 3319ms；**Java 實測 40k 豎線 `scrubLeakedToolXml` 5623ms**（而 `abbc698` 前同輸入 0ms）—— 而 `AskReplyScrub` 係喺 **Render thread** 行（`AskService:374` 同一條 path）。**即係呢個係我哋自己 earlier fix（`+`）引入嘅現存 bug，唔止係 plan 風險** | 反方 F6（`R1ScrubProbe`） |
| R7 | `:335`（`!offer`）係「**系統／用戶明確決定唔用 tools**」嘅出口（PURPOSE／`off`／400 remembered）→ 喺該處「解析文字再執行」＝**繞過 native-tools gate**（`off` 模式會靜靜變返會用 tools） | 反方 F9 |
| R8 | **驗收 fixture 冇 DSML**：`tests/fixtures/ask_display_leak_2026-09-13.txt` 係**已 scrubbed 顯示本文**（U+FF5C=0、`<`=0、invoke=0）→ plan v1 驗收 #2 根本跑唔到 | 反方 F1 ＋ 正方 NIT-1 |
| R9 | **真機症狀量唔到**：T4 gate 對我抽出嘅**真實** scrub 輸出（`\n\nino_dlc_build:cross_z_build_full_bottle\noutput\n…` 裸 id＋role）→ **rc=0 綠燈**；即「零 markup」assertion **捉唔到玩家見到嘅垃圾** | 反方 F10 |
| R10 | **T1 之後零真機數據**：整個 `latest.log` 只有 1 次 leak（`:563`，07:51），deployed jar（09-12 07:07）早過 HEAD → 「T1 後仲會唔會漏」**冇數據** | 反方 F11 |
| R11 | baseline 實況 = **PASS 95 / FAIL 5**（3 baseline ＋ `check_ask_display_leak.py`(2) ＋ `check_jar_contains_fix.py`） | 反方 F12 |
| R12 | 其他次要：pattern 真名係 `DSML_PARAM`（唔係 `DSML_PARAMETER`）；K 系列只有 K1–K4／K20／K21；`dropResidualDsmlLines` 對真 leak 零影響（over-match 只係含字面「DSML」嘅正常行，LOW）；白名單應該寫死 `ALLOWLIST` 而**唔係** registry（`registerExternal` 會存入 registry 但設計上唔准 exec） | 正方 NIT-4/5/6、反方 F5／F8 |
| R13 | ⭐ **端到端實證（R2，真 bytes 直入真 `capableLoop`，via `R2FlowProbe`）**：**現況**：`hasEmbeddedToolDump=false`、`parseEmbeddedToolCalls=0`、in-loop 情境 **`result == 泄漏原文`（垃圾原封到玩家）＋ `toolRuns=0`**；**P1 之後**：`true`、`parse=1`、**`result=FINAL_ANSWER`＋`toolRuns=1`（真執行）**。→ **推翻 R1-F2**（主路徑確實「行動蒸發」，唔係「卡照出」）。 | R2 probe（我親自跑） |
| R14 | `{1,4}` 足夠：真機 32 段 pipe-run **全部長度 2**（最大 run=2）→ 有 2× 餘裕；run=5/6 會 miss（未觀察到）；純豎線 2000/20000/40000 都 <1ms（唔係病態），病態形狀係 `<\|N DSML \|N`（n≥20k） | 正方 §1／§6、反方 r2_regex_out |
| R15 | 三處修正：① **指紋一致唔可以只改 recovered 側**（native `argsJson` 係模型原文，key 次序／空格都會變 → 要**共同 canonical builder**，兩邊都行）；② 效能 assertion 輸入要病態形狀（plain 2000 豎線 = 15ms，冇區分力）；③ `AskToolLoopCheck.java` **對 HEAD 唔 compile**（`:483`／`:703` 叫已移除嘅 `LlmClient.toolSchemaDescription`）→ 加 case 前要先修 | 正方 W1/W2/W3 |
| R16 | 位置修正：`canonicalizeCall` 真位置 `AskToolLoop.java:588`（唔係 :236）；`AskReplyScrub.DSML_PIPE:65` **未被使用**（全部 pattern 用 `:68` `DSML_PIPE_RUN`）→ P1 喺該檔只需改 **1 行** | 正方 W1/N1/N3 |

## 2. 修正後方案（**刪走 v1 大部分**，只保留有 evidence 支持嘅）
### P1（核心，最細）grammar 對稱化 —— 有界，唔用 `+`
- `AskToolLoop.java:56` `DSML_PIPE`：`[…]` → **`[…]{1,4}`**（有界 run）。
- `AskReplyScrub.java:68` `DSML_PIPE_RUN`：`+` → **`{1,4}`**（**修 R6 現存 Render-thread 卡頓 bug**；`:65` `DSML_PIPE` 未被使用 → 只改 1 行）。
- 三個 `AskToolLoop` pattern（`DSML_INVOKE`／`DSML_PARAM`／`DSML_TOKEN`）同步。
- 新 check `tests/check_dsml_grammar_sync.py`：機械斷言兩邊字串**語意一致**（唔抽 class —— 抽 class 會撞雙樹 added-lines gate，反方 F7）。
- 效果（**R13 實證**）：真機 `｜｜` 由「完全隱形」變「偵測到」→ `:373` 守衛通過 → 既有 `:374` recovery 真 fire → **真執行＋乾淨答案**（`FINAL_ANSWER`）＋`{1,4}` 順手解 R6 卡頓（4134ms → 19ms）。
### P2（細，真 bug）參數解析修復
- `callFromDsmlParams`（`:674-695`）補讀 **`item_id`／`role`／`machine`**（`switch` 加 case；`string="true"` 已有）。
- **重建完整 `argsJson`** 令 `canonicalizeCall`（真位置 **`:588`**）fingerprint 同 native path 一致 —— **必須用共同 canonical builder，同時改 native 側 `LlmClient.java:652-656`**（唔可以只改 recovered 側：native `argsJson` 係模型原文，key 次序／空格一變就唔等 → R15①）。解 R5／R3；驗證要含「同 item 唔同 role／machine → 指紋必須唔同」。
- 工具閘沿用 **`ALLOWLIST`**（`Set.copyOf(CAPABLE_TOOLS)`，13 個）；**唔准**用 registry。
### P3（**有條件**：等 T5 數據）hop-limit 出口 recovery
- 条件：T5 真機數據顯示「T1＋P1 之後**仍然**有 DSML 漏出（≥2/10）」才做。
- 做嘅時候：只在 `:364-366`／`:386`（hop-limit）加 recovery，**唔做 `:335`（`!offer`）**（R7 繞閘）。
- 必須加 **explicit budget**：新 `state.incRecoveries()`（或重用 `countSuccessfulLlm()`）＋上限 1 次；並補 R4 嘅 counter 混亂（寫明 `hops` vs `llmRounds` 關係）。
### P4（**唔做**）C 段
- `dropResidualDsmlLines` 對真 leak 零影響（R12）→ **刪走 v1 §3C**（最小 diff 原則）。
- 兩個 over-match 風險（含字面「DSML」行被丟；`leftoverToolMarkup` fail-closed 可以清空含「DSML」字樣嘅正常答案）→ 只**記錄**為 future item＋加 2 條 harness case 守住，唔改行為。
### P5 驗收（全部改真數據驅動）
1. **新 fixture 由 log 程式抽**：`latest.log:563` 真 bytes → `tests/fixtures/dsml_real_doubled_2026-09-13.txt`（附抽取腳本 `tools/extract_dsml_fixture.py`，可重現）。
2. **解析斷言**（Java harness，真 bytes；**前置：`AskToolLoopCheck.java` 對 HEAD 唔 compile —— `:483`／`:703` 叫已移除嘅 `LlmClient.toolSchemaDescription`，要先修，R15③**）：① 偵測 true；② 解析出 **2 個** call（唔可以塌成 1）；③ 每個 call 有 `item_id`／`role`（OUTPUT／uses）／`machine`；④ 執行路徑 = `runCall`（用既有 `alreadyRan` 驗證去重，含 native→recovered 跨路徑）。
3. **顯示層 junk assertion（R9）**：`tests/check_ask_display_leak.py` 加：body **唔准**含 `render_recipe_cards` 參數值／`role=output|uses`／`^[a-z0-9_]+:[a-z0-9_/]+$` 裸 id 行（用**真 probe 抽出嘅 scrub 輸出**做 fixture 證明會 FAIL）。
4. **效能 assertion（R6／R15②）**：輸入用**病態形狀** `<|…DSML…|…`（N≥20000；實測現況 4134ms、N=50000 36061ms），`AskReplyScrub.scrubPromptEcho` 同 `AskToolLoop.hasLeakedToolXml` 都要 **<200ms**；**唔准**用 plain 2000 豎線（實測 15ms，冇區分力）。
5. 全量 `tests/check_*.py`：基線重寫成 **PASS 95 / FAIL 5（3 baseline＋2 pending）**，只准唔多過。
6. 雙樹：`check_dual_tree_diff_symmetry.py` RC=0；`check_dsml_grammar_sync.py` RC=0。
7. 真機煙測（T5）：同一問題 ×10，`toolCards` 唔少過改前；log 見 `dsml_recovered`（如有 trigger）。
## 3. 明確唔做（反方建議採納）
- 唔喺 `:335`（`!offer`）做 recovery（繞過 `askNativeTools` 設定）。
- 唔抽 `DsmlGrammar` class（撞雙樹 gate）。
- 唔加 v1 嗰 4 道「閘」（閉合 tag 已由 pattern 強制；白名單／cap8 已存在；只有 idempotency 要修 → 併入 P2）。
- 唔再 metrics 未量就先動 hop-limit 邏輯（P3 有條件）。
## 4. 工序
1. **先 commit T1–T4**（已驗收，做 stable point；否則 A 疊喺未驗收 baseline 上——反方 F7）。
2. P1＋P2＋P5 ②③④（一個 cursor task）。
3. 收貨：harness／新 checks／雙樹／全量。
4. T5 真機煙測 → 用數據決定 P3。
## 5. 反轉條件
- P1 之後真機仍然漏 → P3 開做；仍然漏且 recovery 救唔到 → 考慮「刪走文字協議」（v1 研究 §3 嘅公開線建議）。
- 效能 assertion 過唔到（`{1,4}` 都唔夠）→ 改成「先規整化豎線再跑 pattern」。
- P2 之後 harness 有既有 case 轉紅 → 代表 fingerprint 改變有副作用，立即回退並重新評估。

## 6. T6 實作驗收（2026-09-13，Hermes 親自跑，全部真執行）

| 驗證 | 結果 | 證據 |
|---|---|---|
| P1 `{1,4}` 兩樹 | ✅ `AskToolLoop:58`／`AskReplyScrub:68` 都係 `{1,4}` | grep |
| 真 bytes 偵測 | ✅ `hasLeakedToolXml=true`（原 false）、`parseEmbeddedToolCalls=2`（原 0，唔再塌） | 真 class probe |
| 端到端主路徑 | ✅ `result=FINAL_ANSWER`（原＝泄漏原文）、兩個 call 真執行（`lvl=OUTPUT`／`uses`＋`machine`） | capableLoop probe |
| 跨路徑去重 | ✅ native＋recovered 只執行 **2** 次（冇 P2 會係 3） | T6DedupeProbe |
| 效能（R6 修正） | ✅ 病態 `<|×20k DSML |×20k`：**25.5ms**（原 4134ms）；n=50k：**0.6ms**（原 36061ms） | T6PerfProbe |
| 顯示層 | ✅ 真 leak body → `scrubPromptEcho` = 0 字（fail-closed 不變） | R2RegexProbe |
| 全量 checks | ✅ 96 PASS／5 FAIL ＝ baseline（3 baseline＋2 pending） | 101 檔 |
| 雙樹對稱 | ✅ 4 個改動檔 added-lines 逐字相同 | git diff |
| 禁區 | ✅ `!offer` 出口／`dropResidualDsmlLines`／`leftoverToolMarkup` 完全冇改 | git diff |
| grammar sync check | ✅ `check_dsml_grammar_sync.py` PASS；junk assertion 負對照 OK | 跑過 |

**未驗（老實講）**：① `AskToolLoopCheck` K30–K34 **未跑**（headless javac 追唔完依賴圖 —— 要 gradle classpath，留 T5 一併跑）；② mod 全量 compile 未跑；③ 真機煙測未做。
**已知殘留**：hop-limit 出口（`:364-366`）過 P1 後**仍然**原封吐回 leak（probe scenario B）→ 屬 P3，等 T5 數據決定。
