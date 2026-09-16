# Plan v6.5 — 腳本產出通道（code-only；硬閘＝單一可重跑清單；sink／TRANSFORM 語意已 pin）

> 2026-09-17｜v6 R1 正方3:反方7 → v6.1 R2 5:5 → v6.2 R3 6:4 → v6.3（收 FC1″／FC2″）→ v6.4（pin 行號語意）
> → **R4 正方 7 : 反方 3**（FC1″／FC2″ 全 RESOLVED；新洞 **N1 HIGH**＝predicate 收 `setItemSlot` 但 TRANSFORM 冇跟上）→ **v6.5 收 N1／N2**
> → **R5 正方 8 : 反方 2 → 達標（go=true）**；R5 只捉到 1 個 **LOW（N3）**＝文件／fixture 精度（行號例子寫錯、`entity.setItemSlot` 單引號 slot 攞唔到）→ **已即場修（v6.5.1）**，唔需第 6 輪
> 狀態：**review 達標（正方 8 :反方 2）→ 等 SK go 派 cursor 實作（單 1）**；開工前風險評估／還原點見 §4
> fixture（repo）：`docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json`（20.0 KB；含 `ids_166`、`A_hard_gate`（每件 sink/entry 行）、`transform_family`（18 站點）、`line_semantics`）

## 0. 目標（有界寫法）

玩家問「X 點取得」，而 KubeJS code 有寫產出 X 嘅路（觸發＋條件字面＋產出）時，答案要講得出嗰條路。**fact 只由 code 讀**；JEI 信息頁／tooltip 文案唔准當 fact／判準／append。此單承諾＝**A_hard 10 件硬閘**（下）＋索引能力範圍 166（抽樣實證 ≥20）；唔承諾 166 全數答得出。

## 1. 範圍（可重跑；fixture 已 commit）

掃 `<instance>/kubejs/{server,startup}_scripts/**/*.js`，排除 `/client_scripts/`、`/assets/`、`/data/`（本包實掃 **598** 檔）。
量法：handler 4 形（物件表／賦值 `Xxx['ns:id'] = function`／`ItemEvents.*`／`BlockEvents.*`／`EntityEvents.*`）。

**Sink 定義（v6.5 pin，R4 N1 修正）**：
- **PRODUCE sink**（唯一可出 `-[js_produce]->`）＝ `player.give(Item.of('ns:id'))`／`give('ns:id')`／`popItem(…)`／`loot` 系。
- **TRANSFORM sink**（一律 `kind=TRANSFORM`，出 `-[js_transform]->`，**永不出 `js_produce`**）＝ `setStackInSlot(slot, Item.of('ns:id'))` **及** `setItemSlot(slot, Item.of('ns:id'))`（單向置換，語意相同）。
- 變數形式（`ItemStack(key,…)`／`newItem` 等）唔 match 任何 sink（唔入 index）。
- word-boundary（`addItemCooldown` 假陽實測 ≥5 處）。

| 類 | 內容 | fixture 鍵 |
|---|---|---|
| **硬閘（單 1 唯一驗收清單）** | **A_hard 10 件**（純腳本產出、冇 recipe／loot／quest、非互換） | `A_hard_gate`（每件：`rel`／`entry_line`／`sink_lines`／`handler`／`sink`／`js_recipe_defs`） |
| TRANSFORM 家族（index ＋ emit，label `js_transform`） | **18 站點**＝`setStackInSlot` 2（`god_bless_full_necklace`／`silver_ring`）＋`setItemSlot` **16**（10 個 outId，`ino_dlc_build:*` 9＋`mrqx_extra_pack:fox_soul`） | `transform_family`＋`transform_family_setSlot` |
| 真機 smoke（唔計數） | `kubejs:god_bless_full_necklace`（`entity_death.js:22→26`） | 同上 |
| 排除（有 recipe／係消耗代價） | `irons_spellbooks:silver_ring`（`entity_hurt.js:31→36`；`recipes/common.js:197` 已有 shaped）→ **唔入硬閘、唔 assert** | 同上 |
| 排除（雙向互換） | `projecte:*` 5 件 ↔ `golden_age:*`（`dlc_template_rclick.js:112-168`）→ label `js_swap` | `swap_family_excluded` |
| 索引能力範圍（抽樣） | 166 件非 vanilla 產出（**完整 id 陣列已入 fixture**） | `ids_166`（len 166／unique 166） |
| 另一單（單 2） | B 11 件（`dream_*` 10＋`shattered_bubble`）＝有 JEI 文案、冇 recipe／loot／quest | `B_group` |

### A_hard 10（`sink`＝真 give 呼叫行，`entry`＝handler 行；1-based）

| outId | rel | entry | sink |
|---|---|---|---|
| `b_a_d:vodka` | `server_scripts/b_a_d/b_a_d_rclick.js` | 73（`b_a_d:keg`） | **87** |
| `b_a_d:mead` | 同上 | 73 | **91** |
| `b_a_d:rice_wine` | 同上 | 73 | **95** |
| `b_a_d:egg_grog` | 同上 | 73 | **99** |
| `b_a_d:eyeball_sacrifice` | `server_scripts/b_a_d/b_a_d_player_damage.js` | 2267（`b_a_d:taiji`） | **2280** |
| `b_a_d:sliced_meat` | 同上 | 2267 | **2294** |
| `b_a_d:picture` | `server_scripts/b_a_d/effect/mob_effect.js` | 731（`EntityEvents.death` wolf） | **737** |
| `alexsmobs:shrimp_fried_rice` | `server_scripts/b_a_d/item/b_a_d_item.js` | 493（`BlockEvents.rightClicked`） | **497** |
| `golden_age:believe` | `server_scripts/golden_age/events.js` | 1014（`BlockEvents.rightClicked` idol） | **1043** |
| `ino_dlc_build:pandora_box_blue` | `server_scripts/ino_dlc_build/ino_dlc_build_block_broken.js` | 1 | **7 及 20（兩個 site）** |

**「冇其他通道」已機械核實（predicate 寫死）**：對 598 個 js 逐行掃 `event.(shaped|shapeless|smelting|blasting|smoking|campfireCooking|stonecutting|smithing)|\.recipes\.|registerCustomRecipe|event\.custom\(` 且同行含該 id → **10 件全部 0 命中**（`js_recipe_defs=0`）；相反 `silver_ring` 命中 `recipes/common.js:197`（故踢出硬閘）。

防洗版：每 focus 最多 **3** 條；只喺取得意圖（`wantsFullAcquire`）出；三種 label 分開。

## 2. 設計（唔變部分照舊）

1. **掃描 pass**：`PackIndex.build` walk `:206-246` → `indexScriptItems(rel)` `:1568-1606` 末尾（已收 rel／已 readText）。**唔可以**放 `KubeJsMechanicScan`（400 檔上限 `:89`；本包 598 檔、startup 0 入選）。Gate 明文收窄。
2. **Parser** `logic/JsObtainSites.java`：`record Site(outId, triggerId, mechanism, rel, line, entryLine, cond, kind)`；`kind ∈ {PRODUCE, SWAP, TRANSFORM}`；純函數、fixture 可測。
   - **行號語意（v6.4 pin）**：`line` ＝ **sink 呼叫行**（fact `src:<rel>:<line>` 印呢個）；`entryLine` ＝ handler／事件註冊行（只入 site／log，唔入玩家字串）。同一 outId 可以有多個 Site（例 `pandora_box_blue` sink **7 及 20**；`ino_dlc_build_item_work.js` setItemSlot **252／269＝`full_bottle_buster_gun`**、**371＝`full_bottle_buster_sword`**（N3 更正：舊文寫「gun 269／371」係 gun／sword 混合））。
   - 行號 1-based（enumerate 計）；fixture 同一 predicate。
   - **`SWAP`**：同一 handler 內**雙向** give 同 id（A→B ∧ B→A）。
   - **`TRANSFORM`（v6.5 修 R4 N1）**：`setStackInSlot(slot, Item.of('other:id'))` **或** `setItemSlot(slot, Item.of('other:id'))`（單向置換，**唔當從零取得**）。**PRODUCE 判定只認 give／loot**——`setItemSlot` 永不出 `js_produce`（呢條係 R4 捉到嘅自相矛盾：舊 predicate 收 `setItemSlot` 做 sink，但 TRANSFORM 只寫 `setStackInSlot` → 16 站點會 fallthrough 成 PRODUCE）。
   - `cond` 規格（字面 ≤40 字）：`%`／`概率|機率|几率`／`Math.random\(\)`／`random\s*[<>]=?\s*[0-9.]+`／`\.age\s*%\s*[0-9]+`／`hasEffect\(|hasNBT\(|isUnderWater\(|getDifficulty\(|persistentData`；唔解釋、唔自創動詞。
3. **索引**：`Map<String,List<Site>> jsObtainByOutput`；`build()` `:160-172` clear；**唔准**喺 `beginAskSession()` `:386-389` clear。
4. **可見性（強制 admission）**：`acquireFactsDetailed` `:1259` ranked 建構後、`:1265` graphFacts 迴圈前，pre-pass 加入 focus 嘅 site（`RankedAcquire(2, seq++)`）。**cap（`:1266` `ranked+cycles>=12`）只箍 graphFacts 迴圈**（pre-pass 已入 ranked，唔會被 break 剔走，但計入 12 額）；`MAX_JS_OBTAIN_LINES=3` 嘅截斷點＝**pre-pass 內、band/seq 排序之後取頭 3**（band 2 同 band 2 interact 行並排，js 行 seq 較細＝先出）。
5. 保險 branch（可選）：`:1379`（graphFacts 迴圈尾）加 `-[js_produce|js_swap|js_transform]->`。
6. **`AskEngine.graphLines` 唔加 branch**（死碼）：`retrieve()` `AskEngine.java:270` 先拍 snapshot，`acquireFactsDetailed` `:287` 後跑；`graphLines` 宣告喺 `:469`。
7. **文字**：`ReplyLang.jsProduce(…)`／`jsSwap(…)`／`jsTransform(…)`（簽名統一＝`(code, outName, outId, triggerName, triggerId, cond, src)`）。模板含「【腳本產出／互換／轉換】…條件：<cond 原文>…src:<rel>:<line>」；玩家名走 `OfficialDisplay`。
8. **開關**：`PackAiConfig.JS_OBTAIN_CHANNEL`（`jsObtainChannel=true`）＋ `JS_OBTAIN_DIAG_LOG`；kill-switch 包三處。
9. **拆單**：單 1＝本 plan；單 2＝包文字通道（`KubeJsMechanicScan.factsFrom :924-965` note 收窄＋`AskService.appendMechanicBehavior :726-780` 解析；B 11 件）；單 3＝文件修正。

## 3. 驗收

| # | artefact | 今日 | 實作後 |
|---|---|---|---|
| S1 | 索引 site（讀真 code） | 紅 | `runAcquireFactsCheck` 真 snippet → assert site／**`src:` 行號 = fixture `sink_lines`**／`kind`／`entryLine` 另存 |
| S2 | **A_hard 10 件**逐件出 `-[js_produce]->`（唯一硬閘；唔含 silver_ring／god_bless） | 紅 | 10 件 assert（`pandora_box_blue` 兩個 site 都要出）；負控 `minecraft:stone`、註解 id → 0 site |
| S3 | 真機 trace 逐字含新 label（**smoke**：`b_a_d:vodka`、`b_a_d:sliced_meat`、`kubejs:god_bless_full_necklace`） | 紅 | grep `packai/trace/ask-*.jsonl` |
| S4 | 旗艦條件字面：vodka 行必須含 `0.2`（真 code 原文，`b_a_d_rclick.js:82`） | 紅 | harness 字串 assert |
| S5 | 4 形 parser 能力（fixture；標明本包冇 assign 站點） | 紅 | fixture 4 形 |
| S6 | busy focus（≥12 更早 edge）仍見到 js 行；開關關 → 0 行 | — | cap fixture 對比 |
| S7 | 文字：機械式＋官方名＋無自創動詞 | — | 白名單動詞比對 |
| S8 | `addItemCooldown` 唔出 site | 紅 | 真句 fixture |
| S9 | 抽樣：`ids_166`（fixture 已 commit 166 個 id）中 **≥20** 件出 js 行；同一生成腳本重掃 count ∈ [160,170] | 紅 | fixture `ids_166`＋`counts.produced_non_vanilla` |
| S10 | 迴歸：其他取得行 byte-identical | — | 開／關兩跑比對 |
| **S11** | **TRANSFORM 鑑別力（R4 N1 修正）**：fixture `transform_family` **18 站點**真 snippet 餵 parser → assert **kind=TRANSFORM**；並且**冇任何一個**出 `-[js_produce]->`（負斷言用同一批真句） | **紅**（parser 未存在） | 18 站點逐個 assert；另加 1 條正對照（`b_a_d:vodka` give 句 → kind=PRODUCE） |

入口：`runAcquireFactsCheck`＋`runAskMechanicFactsCheck`（`-I tmp-check.gradle`，JDK 17；今日 11s OK）。

## 4. 回滾

新 class＋config×2＋pre-pass＋ReplyLang×3＋3 檔 lang＋AskToolContext 常數；索引 in-memory。`git revert` 單 commit；開關即時失效。唔宣稱純新增。

## 5. 未知／盲點

166 係掃描值（變數 id／動態註冊／body >6000 字未覆蓋 → 可能更多）；`cond` 只做字面（唔翻譯玩家語言）；busy focus 分佈未量；B 11 件 JEI 通道未證通；`I18n.get("kubejs.tooltips.*")` 未證；swap 家族可能唔止 projecte（跨 handler 未掃）。
**TRANSFORM 家族**：18 站點全部搵到，但**只有 `god_bless_full_necklace` 會做真機 smoke（S3）**；其餘 16 個 `setItemSlot` 站點嘅**條件字面**（部分要主手物品／curios slot）抽唔抽得到，只有 S11 解析器級斷言，冇真機驗；其中 10 個 outId 有 6 個本身另有 recipe 命中（`js_recipe_defs>0`，fixture 逐個記錄）→ transform 行只係**額外路徑**，唔聲稱唯一。

## 6. Review 狀態

- v5.5 線：R1 2:8 → R2 5:5 → R3 6:4 → R4 6:4 → R5 4:6／8:2 → 裁判 7:3 → R6 5:5 → R7 supporting 6:4、反方 4:6 → SK 改 code-first。
- **v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3 → R5 正方 8 : 反方 2（達標）**。
- **R4**：FC1″／FC2″ 全 **RESOLVED**；新洞 **N1 HIGH**＝`line_semantics.predicate` 收 `setItemSlot` 但 §2.2 TRANSFORM 只寫 `setStackInSlot` → 16 站點會變 PRODUCE／**N2 LOW**＝本節殘句「今日 fixture 只有 entry」同 fixture 實況矛盾。
- **R4 修法（v6.5）**：① N1 → §1 §2.2 pin 死「PRODUCE sink 只認 give／loot；`setStackInSlot`＋`setItemSlot` 一律 TRANSFORM」，fixture 加 `transform_family`（18 站點，setItemSlot 16／setStackInSlot 2）＋新 **S11** 解析器級紅→綠斷言（含正對照）；② N2 → 本節改寫。
- **R5（有界輪）：N1／N2 全 RESOLVED → 正方 8 : 反方 2、go=true**；唯一新項 **N3 LOW**（§2.2 例子行號 gun／sword 混合＋fixture `entity.setItemSlot` 單引號 slot 攞唔到）→ **已即場修（v6.5.1）**：§2.2 行號更正、fixture `transform_family` 加 `receiver`＋修 `slot_arg`（收單／雙括號、容 `entity`／`curios` 變數 slot）、`line_semantics` 加 `slot_arg`／`js_recipe_note`。**達標後唔再開新輪**（N3 屬文件級）。
- ⚠️ 記錄：R5 輪 cursor **冇寫自己嘅 report 檔**（只出 stdout，且經 PS console cp950 → 中文亂碼不可逆）→ `reviews/2026-09-16_plan-v6.5-obtain-code-channel-R5-opposing.txt` 係 Hermes 由可辨識碎片＋自己親跑命令重建（檔頭 `provenance` 已誠實標明）。
- **SK 2026-09-16 明示**：揀選項 **2**（破上限）＋「**until 8:2**」→ 續派有界輪（R4…），每輪只核上一輪 flip conditions＋自帶新洞，直到 **正方 ≥8 : 反方 ≤2**。
