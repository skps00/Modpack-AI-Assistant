# Plan v6.3 — 腳本產出通道（code-only；硬閘＝單一可重跑清單）

> 2026-09-16｜v6 R1 正方3:反方7 → v6.1 R2 5:5 → v6.2 R3 6:4 → **v6.3（收 FC1″／FC2″＋F5/F6）**
> 狀態：**已到 3 輪硬上限、未達 8:2 → 停手交 SK 拍板**（唔會再自開第 4 輪）
> fixture（repo）：`docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json`（11.8 KB；含 `ids_166` 全清單、`A_hard_gate`、swap／transform 家族）

## 0. 目標（有界寫法）

玩家問「X 點取得」，而 KubeJS code 有寫產出 X 嘅路（觸發＋條件字面＋產出）時，答案要講得出嗰條路。**fact 只由 code 讀**；JEI 信息頁／tooltip 文案唔准當 fact／判準／append。此單承諾＝**A_hard 10 件硬閘**（下）＋索引能力範圍 166（抽樣實證 ≥20）；唔承諾 166 全數答得出。

## 1. 範圍（可重跑；fixture 已 commit）

掃 `<instance>/kubejs/{server,startup}_scripts/**/*.js`，排除 `/client_scripts/`、`/assets/`、`/data/`。
量法：handler 4 形（物件表／賦值 `Xxx['ns:id'] = function`／`ItemEvents.*`／`BlockEvents.*`／`EntityEvents.*`）× body 字面 sink 3 類（`give`／`loot`／`setSlot`），word-boundary（`addItemCooldown` 假陽實測 ≥5 處）。

| 類 | 內容 | fixture 鍵 |
|---|---|---|
| **硬閘（單 1 唯一驗收清單）** | **A_hard 10 件**（純腳本產出、冇 recipe／loot／quest、非互換） | `A_hard_gate` |
| 真機 smoke（唔計數） | `kubejs:god_bless_full_necklace`（`entity_death.js:22→26`）＝**充能／轉換**家族（label `js_transform`） | `transform_family_setSlot` |
| 排除（有 recipe／係消耗代價） | `irons_spellbooks:silver_ring`（`entity_hurt.js:31→36`；`recipes/common.js:197` 已有 shaped）→ **唔入硬閘、唔 assert** | 同上 |
| 排除（雙向互換） | `projecte:*` 5 件 ↔ `golden_age:*`（`dlc_template_rclick.js:112-168`）→ label `js_swap` | `swap_family_excluded` |
| 索引能力範圍（抽樣） | 166 件非 vanilla 產出 | `ids_166` |
| 另一單（單 2） | B 11 件（`dream_*` 10＋`shattered_bubble`）＝有 JEI 文案、冇 recipe／loot／quest | `B_group` |

A_hard 10：`b_a_d:vodka`／`mead`／`rice_wine`／`egg_grog`（`b_a_d_rclick.js:73`）、`b_a_d:sliced_meat`／`eyeball_sacrifice`（`b_a_d_player_damage.js:2267`）、`b_a_d:picture`（`effect/mob_effect.js:731`）、`alexsmobs:shrimp_fried_rice`（`server_scripts/b_a_d/item/b_a_d_item.js:493`）、`golden_age:believe`（`golden_age/events.js:1014`）、`ino_dlc_build:pandora_box_blue`（`ino_dlc_build_block_broken.js:1`）。

防洗版：每 focus 最多 **3** 條；只喺取得意圖（`wantsFullAcquire`）出；三種 label 分開。

## 2. 設計（唔變部分照舊）

1. **掃描 pass**：`PackIndex.build` walk `:206-246` → `indexScriptItems(rel)` `:1568-1606` 末尾（已收 rel／已 readText）。**唔可以**放 `KubeJsMechanicScan`（400 檔上限 `:89`；本包 648 檔、startup 0 入選）。Gate 明文收窄。
2. **Parser** `logic/JsObtainSites.java`：`record Site(outId, triggerId, mechanism, rel, line, cond, kind)`；`kind ∈ {PRODUCE, SWAP, TRANSFORM}`；純函數、fixture 可測。
   - `SWAP`：同一 handler 內**雙向** give 同 id（A→B ∧ B→A）。
   - `TRANSFORM`：`setStackInSlot(…, Item.of('other:id'))`（單向置換；**唔當從零取得**）。
   - `cond` 規格（字面 ≤40 字）：`%`／`概率|機率|几率`／`Math.random\(\)`／`random\s*[<>]=?\s*[0-9.]+`／`\.age\s*%\s*[0-9]+`／`hasEffect\(|hasNBT\(|isUnderWater\(|getDifficulty\(|persistentData`；唔解釋、唔自創動詞。
3. **索引**：`Map<String,List<Site>> jsObtainByOutput`；`build()` `:160-172` clear；**唔准**喺 `beginAskSession()` `:386-389` clear。
4. **可見性（強制 admission）**：`acquireFactsDetailed` `:1259` ranked 建構後、`:1265` graphFacts 迴圈前，pre-pass 加入 focus 嘅 site（`RankedAcquire(2, seq++)`）。**cap（`:1266`）只箍 graphFacts 部分**；`MAX_JS_OBTAIN_LINES=3` 嘅截斷點＝**pre-pass 內、band/seq 排序之後取頭 3**（F6 已釘）。
5. 保險 branch（可選）：`:1378` 後加 `-[js_produce|js_swap|js_transform]->`。
6. **`AskEngine.graphLines` 唔加 branch**（死碼）：`retrieve()` `AskEngine.java:270` 先拍 snapshot，`acquireFactsDetailed` `:287` 後跑。
7. **文字**：`ReplyLang.jsProduce(…)`／`jsSwap(…)`／`jsTransform(…)`（簽名統一，簽名＝`(code, outName, outId, triggerName, triggerId, cond, src)`）。模板含「【腳本產出／互換／轉換】…條件：<cond 原文>…src:<rel>:<line>」；玩家名走 `OfficialDisplay`。
8. **開關**：`PackAiConfig.JS_OBTAIN_CHANNEL`（`jsObtainChannel=true`）＋ `JS_OBTAIN_DIAG_LOG`；kill-switch 包三處。
9. **拆單**：單 1＝本 plan；單 2＝包文字通道（`KubeJsMechanicScan.factsFrom :924-965` note 收窄＋`AskService.appendMechanicBehavior :726-780` 解析；B 11 件）；單 3＝文件修正。

## 3. 驗收

| # | artefact | 今日 | 實作後 |
|---|---|---|---|
| S1 | 索引 site（讀真 code） | 紅 | `runAcquireFactsCheck` 真 snippet → assert site／`src:` 行號／`kind` |
| S2 | **A_hard 10 件**逐件出 `-[js_produce]->`（唯一硬閘；唔含 silver_ring） | 紅 | 10 件 assert；負控 `minecraft:stone`、註解 id → 0 site |
| S3 | 真機 trace 逐字含新 label（**smoke**：`b_a_d:vodka`、`b_a_d:sliced_meat`、`kubejs:god_bless_full_necklace`） | 紅 | grep `packai/trace/ask-*.jsonl` |
| S4 | 旗艦條件字面：vodka 行必須含 `0.2`（真 code 原文） | 紅 | harness 字串 assert |
| S5 | 4 形 parser 能力（fixture；標明本包冇 assign 站點） | 紅 | fixture 4 形 |
| S6 | busy focus（≥12 更早 edge）仍見到 js 行；開關關 → 0 行 | — | cap fixture 對比 |
| S7 | 文字：機械式＋官方名＋無自創動詞 | — | 白名單動詞比對 |
| S8 | `addItemCooldown` 唔出 site | 紅 | 真句 fixture |
| S9 | 抽樣：`ids_166` 中 **≥20** 件出 js 行；重掃 count ∈ [160,170] | 紅 | fixture `ids_166`（已 commit） |
| S10 | 迴歸：其他取得行 byte-identical | — | 開／關兩跑比對 |

入口：`runAcquireFactsCheck`＋`runAskMechanicFactsCheck`（`-I tmp-check.gradle`，JDK 17；今日 11s OK）。

## 4. 回滾

新 class＋config×2＋pre-pass＋ReplyLang×3＋3 檔 lang＋AskToolContext 常數；索引 in-memory。`git revert` 單 commit；開關即時失效。唔宣稱純新增。

## 5. 未知／盲點

166 係掃描值（變數 id／動態註冊／body >6000 字未覆蓋 → 可能更多）；`cond` 只做字面（唔翻譯玩家語言）；busy focus 分佈未量；B 11 件 JEI 通道未證通；`I18n.get("kubejs.tooltips.*")` 未證；swap 家族可能唔止 projecte（跨 handler 未掃）。

## 6. Review 狀態（停手點）

- v5.5 線：R1 2:8 → R2 5:5 → R3 6:4 → R4 6:4 → R5 4:6／8:2 → 裁判 7:3 → R6 5:5 → R7 supporting 6:4、反方 4:6 → SK 改 code-first。
- **v6 線：R1 3:7 → R2 5:5 → R3 6:4（已收 FC1″／FC2″：硬閘只 A_hard 10、silver_ring 踢出、`ids_166` 入 fixture、`js_transform` 分 label、cap 截斷點釘死、§0 口號收窄）。**
- **契約：3–4 輪上限已到（第 3 輪）、6:4 < 8:2 → 停手交 SK**：① 批准以「已修好嘅 v6.3」當達標開工；② 明示破上限、再派一輪**只核 FC1″／FC2″**；③ 叫停。
