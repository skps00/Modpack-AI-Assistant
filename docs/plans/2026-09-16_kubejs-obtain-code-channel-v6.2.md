# Plan v6.2 — 腳本產出通道（code-only；硬範圍＝可重跑清單）

> 2026-09-16｜v6 (R1 正方3:反方7) → v6.1 (R2 5:5) → **v6.2（收 FC1′–FC3′＋F5/F6）**｜狀態：待 R3 review
> fixture（已入 repo）：`docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json`
> 反方報告：`reviews/2026-09-16_plan-v6-obtain-code-channel-R1-opposing.txt`、`reviews/2026-09-16_plan-v6.1-obtain-code-channel-R2-opposing.txt`

## 0. 目標與硬規則

玩家問「X 點嚟／點取得」，只要 KubeJS **code** 有寫產出 X 嘅路（觸發＋條件字面＋產出），答案就要講得出。**fact 一律由 code 讀**；JEI 信息頁／tooltip 文案**唔准**當 fact 來源、判準或 append 內容（v6.1 嘅「包說明 append」已刪）。玩家名用官方顯示名，唔准意譯 id。

## 1. 範圍（可重跑；fixture 已 commit）

掃 `<instance>/kubejs/{server,startup}_scripts/**/*.js`，**排除 `/client_scripts/`、`/assets/`、`/data/`**（`PackIndex.isScriptPath` `:2571` 係任何 `.js/.zs/.groovy` → gate 要自己收窄）。
量法：handler 4 註冊形（物件表／賦值 `Xxx['ns:id'] = function`／`ItemEvents.*`／`BlockEvents.*`／`EntityEvents.*`）× body 內字面 sink 3 類（`give`／`loot`／`setSlot`）；sink regex 要 word-boundary `\.(give|addItem|insertItem)\b`（實測 `player.addItemCooldown(...)` ≥5 處會假陽）。

| 項目 | 數 | 出處 |
|---|---|---|
| 產出 handler 站點 | 95 | fixture `counts.handler_sites` |
| 非 vanilla 產出物品（**索引能力範圍**） | 166 | 同上 |
| A 組（冇 recipe／loot／quest，純腳本） | 15 | fixture `A_group_all15` |
| ├ 踢走 projecte 5 件（**雙向互換**，唔係從零取得） | **A_hard = 10** | fixture `A_hard_scope12` |
| └ `setSlot` 換物（旗艦） | **2** | `curios/entity_death.js:22→26`、`curios/entity_hurt.js:31→36` |
| **單 1 硬驗收清單** | **12 件**（A10＋setSlot2） | A_hard_scope12 ＋ setSlot sites |
| B 組（有 JEI 文案、冇 recipe/loot/quest）→ **單 2** | 11（`dream_*` 10＋`b_a_d:shattered_bubble`） | fixture `B_group` |
| 互換家族（另 label `js_swap`，唔當取得） | 5（projecte 5 件 ↔ golden_age 對應物） | fixture `swap_family_excluded` |

A_hard 10 件：`b_a_d:vodka`／`mead`／`rice_wine`／`egg_grog`（`b_a_d_rclick.js:73` 發酵桶）、`b_a_d:sliced_meat`／`eyeball_sacrifice`（`b_a_d_player_damage.js:2267`）、`b_a_d:picture`（`effect/mob_effect.js:731`）、`alexsmobs:shrimp_fried_rice`（`server_scripts/b_a_d/item/b_a_d_item.js:493`）、`golden_age:believe`（`golden_age/events.js:1014`）、`ino_dlc_build:pandora_box_blue`（`ino_dlc_build_block_broken.js:1`）。

**「166」定位（FC1′ 修正）**：= 索引解析能力範圍（會建入索引）；**唔係**交付承諾、**唔係**驗收口號。防洗版：每 focus 最多 3 條；只喺取得意圖（`wantsFullAcquire`）出；`js_swap` 同 `js_produce` 分 label。

## 2. 設計

1. **掃描 pass**：`PackIndex.build` walk `:206-246` → `indexScriptItems(rel)` `:1568-1606` 末尾（已收 rel／已 readText，零額外 IO）。**唔可以**放 `KubeJsMechanicScan`（`DEFAULT_SCAN_MAX_FILES=400` `:89`；本包 648 檔、startup 0 檔入選）。Gate：`kubejs/` ＋ `.js` ＋ 唔准 `/client_scripts/`、`/assets/`、`/data/`。
2. **Parser**：新 `logic/JsObtainSites.java`
   - `record Site(String outId, String triggerId, String mechanism, String rel, int line, String cond, boolean swap)`
   - `static List<Site> parse(String text, String rel)`（純函數、fixture 可測；key 要 `ns:path`）
   - 4 形 × 3 sink；body quote/comment-aware brace match（上限 6000 字，寫入 Javadoc 盲點）
   - **`swap` 判定**：同一 handler 內對同一對 id 出現**雙向 give**（A→B 且 B→A）＝ `swap=true`（實例：`golden_age/dlc_template_rclick.js:112-168` `golden_age:philosophers_stone` 5 對）
   - **`cond` 抽取規格（FC3′，寫死）**：抽**原文片段**（≤40 字），命中以下任一：`%`／`概率|機率|几率`／`Math.random\(\)`／`random\s*[<>]=?\s*[0-9.]+`／`\.age\s*%\s*[0-9]+`／`hasEffect\(|hasNBT\(|isUnderWater\(|getDifficulty\(|persistentData`；只抽字面、唔解釋、唔准自創動詞。例：`b_a_d_rclick.js:73` body → `random < 0.2/0.4/0.6/0.8`（**唔係** `%`）。
3. **索引**：`PackIndex` 加 `Map<String,List<Site>> jsObtainByOutput`；`build()` 清理區 `:160-172` clear；**唔准**喺 `beginAskSession()` `:386-389` clear。
4. **可見性（強制 admission，唔靠 append）**：於 `acquireFactsDetailed` 內 `:1259` ranked 建構之後、`:1265` graphFacts 迴圈之前，插 pre-pass：focus 嘅每個 site → `ranked.add(new RankedAcquire(2, seq++, ReplyLang.jsProduce(...)))`（`swap=true` → `ReplyLang.jsSwap(...)`）。**唔**交俾 graphFacts 迴圈（避免重複；cap `:1266` 只箍 graphFacts 部分）。band=2，`:1398` legend 補 `js_produce`／`js_swap`。
5. **保險 branch**（可選，非主路）：`:1378` 之後加 `- [js_produce]->`／`- [js_swap]->` 分支。
6. **`AskEngine.graphLines` 唔加 branch**：`retrieve()`（`AskEngine.java:270`）先拍 `RetrieveResult`（`PackIndex:563→:570`），`acquireFactsDetailed`（`:287`）之後才跑 → 注入 fact 永遠唔喺 `retrieved.graphFacts()`（mirror `quest_obtain`）。
7. **文字**：`ReplyLang.jsProduce(String code, String outName, String outId, String triggerName, String triggerId, String cond, String src)`／`jsSwap(…)`（簽名統一）。模板：`【腳本產出】<outName>（<outId>）：<cond?> 觸發 <triggerName>（<triggerId>）→ 產出 ｜ <src>`；`swap` 用 `【腳本互換】…（雙向）`。玩家名走 `OfficialDisplay`。**唔 append 任何包文案**。
8. **開關**：`PackAiConfig` 加 `JS_OBTAIN_CHANNEL`（`define("jsObtainChannel", true)`）＋ `JS_OBTAIN_DIAG_LOG`（`define("jsObtainDiagLog", false)`）；kill-switch 包 index／pre-pass／branch 三處。上限：`AskToolContext` 加 `MAX_JS_OBTAIN_LINES = 3`。
9. **交付拆 3 單**：單 1＝本 plan；單 2＝包文字通道（`KubeJsMechanicScan.factsFrom :924-965` note 收窄至 focus 自己 key，**唔准**改 cache；`AskService.appendMechanicBehavior :726-780` 文字解析；B 11 件歸呢單）；單 3＝plan／文件修正（真路徑 `server_scripts/b_a_d/item/b_a_d_item.js`、`115 → 114 PASS/1 FAIL`、895/649 殘留、雙 §3）。

## 3. 驗收（只認 code 掃描＋真機；唔用截圖／JEI 文案）

| # | artefact | 今日 | 實作後 |
|---|---|---|---|
| S1 | 索引 site（harness 讀真 code，唔准 reimplement） | 紅 | `runAcquireFactsCheck`：真 snippet（`b_a_d_rclick.js:73`／`dlc_template_rclick.js:112`／`curios/entity_death.js:22-26`）→ assert site 數、`src:` 行號、`swap` 旗標 |
| S2 | **A_hard 10＋setSlot 2 = 12 件**逐件 assert 出 `js_produce`／`js_swap` 行（唔准只 assert「非空」） | 紅 | 12 件硬閘；負控 `minecraft:stone`、只喺註解出現嘅 id → 0 site |
| S3 | 真機 trace `tool.result name=acquire` 逐字含新 label | 紅（旗艦 acquire `""`） | 真機問 `b_a_d:vodka`、`b_a_d:sliced_meat`、`kubejs:god_bless_full_necklace` → grep trace |
| S4 | **旗艦條件字面**：vodka 答案必須含 code 原文片段（`0.2`／`0.4`） | 紅 | harness assert 渲染字串含 `0.2`；S7 白名單同步驗 |
| S5 | 4 註冊形 parser 能力（fixture）＋標明本包 95 站點**冇** assign 形（唔當產品覆蓋） | 紅 | fixture 4 形各 1 例 |
| S6 | busy focus（≥12 條更早 edge）仍見到 js 行 | — | cap fixture＋同 id site → assert 出現；`jsObtainChannel=false` → 0 條 |
| S7 | 文字：機械式＋官方名＋無自創動詞 | — | 字串比對（白名單動詞） |
| S8 | `addItemCooldown` 唔出 site | 紅（若裸 regex） | fixture 貼 `b_a_d_constdef.js:572` 真句 → 0 site |
| S9 | 抽樣閘：**≥20/166** 非 vanilla 產出物品有 `js_produce`／`js_swap` 行 | 紅 | harness 用 fixture 166 清單抽樣 assert；S9 過＝「索引能力範圍」有實證 |
| S10 | 迴歸：開關關 → 0 新行、其他取得行 byte-identical | — | 跑兩次比對 |

入口：`cd forge/1.19.2 && ./gradlew.bat -I tmp-check.gradle runAcquireFactsCheck -Dorg.gradle.java.home="…/eclipse_adoptium-17-amd64-windows.2"`（今日 11s OK）＋ `runAskMechanicFactsCheck`。

## 4. 回滾

新 class ＋ `PackAiConfig` ×2 ＋ `acquireFactsDetailed` pre-pass ＋ `ReplyLang` ×2 ＋ 3 檔 lang ＋ `AskToolContext` 常數。索引 in-memory（無 cache 檔／schema bump）。`git revert` 單 commit；開關即時失效。**唔宣稱純新增**。

## 5. 未知／盲點

1. 166 係掃描結果（A 級可重跑），盲點：變數 id、動態註冊、body >6000 字 → 實際可能更多。
2. `cond` 只抽字面 → 答案講「條件：random < 0.2」級數，唔會翻譯玩家語言。
3. busy focus 真實分佈未量（要真機 trace 睇 cap 命中）。
4. B 11 件嘅 JEI 通道今日 trace 0 次＝未證通（單 2 真機驗）。
5. `I18n.get("kubejs.tooltips.*")` 能否解析（單 2 依賴）。
6. swap 家族可能唔止 projecte 5 件（只掃到雙向 give 同 handler 內；跨 handler 嘅互換未掃）。

## 6. Review 狀態

v5.5 線：R1 2:8 → R2 5:5 → R3 6:4 → R4 6:4 → R5 4:6／正方 8:2 → 裁判 7:3 → R6 5:5 → R7 supporting 6:4、反方 4:6 → 硬上限 → SK 改 code-first。
**v6 線：R1 正方3:反方7 → v6.1 R2 正方5:反方5 → v6.2（本版）待 R3。** 目標 正方 ≥8:2；R3 未達即停手問 SK（唔准第 4 輪以上）。
