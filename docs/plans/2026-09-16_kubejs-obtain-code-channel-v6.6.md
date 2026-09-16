# Plan v6.6 — 腳本產出通道（code-only；有 gate 就要寫 gate；硬閘＝單一可重跑清單）

> 2026-09-17｜v6 R1 正方3:反方7 → v6.1 5:5 → v6.2 6:4 → v6.3（收 FC1″／FC2″）→ v6.4（pin 行號）→ **R4 7:3**（N1 HIGH）→ v6.5（收 N1／N2）→ **R5 正方 8 : 反方 2（達標）**
> → **SK 2026-09-17 兩條糾正**：① **KubeJS 配方一律唔做**（JEI 已覆蓋）② **伏特加真正條件係「裝住器官」**（ChestCavity 系統）→ 本版 **v6.6** 加 **organGate** ＋ 修正 handler taxonomy（addon 策略表）
> 狀態：**R6 有界輪進行中**（只核 v6.6 新增嘅 gate 模型；SK 明示 review 做到 正方 ≥8 : 反方 ≤2）
> fixture（repo）：`docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json`（27.7 KB）

## 0. 目標（有界寫法）

玩家問「X 點取得」，而 KubeJS code 有寫產出 X 嘅路時，答案要講得出**（a）觸發**（要裝住邊個器官／右鍵邊個方塊／打死邊隻怪）**（b）條件字面**（機率／手中物品）**（c）產出**。**fact 只由 code 讀**；**KubeJS 配方唔做**（SK 09-17 定案：JEI 已覆蓋）；JEI 信息頁／tooltip 文案唔准當 fact／判準／append。
此單承諾＝**A_hard 10 件硬閘**（含 gate；下）＋索引能力範圍 166（抽樣實證 ≥20）；唔承諾 166 全數答得出。

## 1. 範圍（可重跑；fixture 已 commit）

掃 `<instance>/kubejs/{server,startup}_scripts/**/*.js`，排除 `/client_scripts/`、`/assets/`、`/data/`（本包實掃 **598** 檔）。
**handler taxonomy（v6.6 修正；唔硬編碼 mod 名，全部構造式判定）**：

| 家族 | 形狀（predicate） | gate 來源 |
|---|---|---|
| A. vanilla／常用事件 | `XxxEvents.<event>(…)`（實測 863 個註冊 call；top：`PlayerEvents.tick` 151／`ItemEvents.rightClicked` 137／`EntityEvents.death` 40／`BlockEvents.rightClicked` 36／`EntityEvents.hurt` 31／`ForgeEvents.onEvent` 50／`NetworkEvents.dataReceived` 10／`ChestCavityEvents.*` 3） | body 內檢查（家族 B 以外） |
| B. **物件表（addon 策略表）** | `const/var NAME = { 'ns:id': function(event, organ…){…} }`（實測 **286** 張表、**1173** 個 key） | **key ＝ gate**：表名／參數含 organ ⇒ key 係**器官 id**（例 `BADOrganRightClickedOnlyStrategies['b_a_d:keg']`）→ 玩家**要先植入該器官**先觸發 |
| C. 低階 Forge | `ForgeEvents.onEvent/ForgeModEvents.onEvent` | body 內檢查＋event class |
| D. 其他 addon | `PlayerEvents.spellOnCast`／`ChestCavityEvents.updateOrganScore`／`ServerEvents.entityLootTables`… | body 內檢查 |

**gate 兩類（都必須入 fact 文字）**：
- **table-key gate**（器官策略表 key）→ `organGate`
- **body-check gate**（`getPlayerChestCavityItemMap(…).has('organ')`／`organScores.get(new ResourceLocation('chestcavity','X'))`／`getChestCavityInstance()`／`isEquippedCurio(…)`）

| 類 | 內容 | fixture 鍵 |
|---|---|---|
| **硬閘（單 1 唯一驗收清單）** | **A_hard 10 件**（純腳本產出、冇 recipe／loot／quest、非互換）**＋ 逐件 organGate** | `A_hard_gate` |
| TRANSFORM 家族（index ＋ emit，label `js_transform`） | **18 站點**＝`setStackInSlot` 2＋`setItemSlot` 16（10 個 outId） | `transform_family` |
| 排除（有 recipe／消耗代價） | `irons_spellbooks:silver_ring` → 唔入硬閘 | `transform_family_setSlot` |
| 排除（雙向互換） | `projecte:*` 5 件 ↔ `golden_age:*` → label `js_swap` | `swap_family_excluded` |
| 索引能力範圍（抽樣） | 166 件非 vanilla 產出（完整 id 陣列） | `ids_166` |
| 另一單（單 2） | B 11 件（`dream_*` 10＋`shattered_bubble`） | `B_group` |
| gate 掃描（新） | 265 個「字面 sink 出現嘅 id」中 **134** 個屬器官表（heuristic 上界） | `organ_gate_scan` |
| addon 家族統計（新） | 863 註冊 call／286 表／1173 器官式 key | `addon_families` |

### A_hard 10（v6.6：gate 逐件實測；`sink`＝真 give 行；1-based）

| outId | rel | entry | sink | **organGate（官方 zh）** | kind |
|---|---|---|---|---|---|
| `b_a_d:vodka` | `b_a_d_rclick.js` | 73 | **87** | **`b_a_d:keg`＝发酵桶**（table-key） | organ-table |
| `b_a_d:mead` | 同上 | 73 | **91** | `b_a_d:keg`＝发酵桶 | organ-table |
| `b_a_d:rice_wine` | 同上 | 73 | **95** | `b_a_d:keg`＝发酵桶 | organ-table |
| `b_a_d:egg_grog` | 同上 | 73 | **99** | `b_a_d:keg`＝发酵桶 | organ-table |
| `b_a_d:eyeball_sacrifice` | `b_a_d_player_damage.js` | 2267 | **2280** | **`b_a_d:taiji`＝子**（table-key） | organ-table |
| `b_a_d:sliced_meat` | 同上 | 2267 | **2294** | `b_a_d:taiji`＝子 | organ-table |
| `b_a_d:picture` | `effect/mob_effect.js` | 731 | **737** | **`b_a_d:curse_letter_organ`＝链式信件**（body-check） | vanilla-event |
| `alexsmobs:shrimp_fried_rice` | `item/b_a_d_item.js` | 493 | **497** | **`kubejs:mantis_shrimp_fist`＝螳螂虾拳**（body-check） | vanilla-event |
| `golden_age:believe` | `golden_age/events.js` | 1014 | **1043** | 無 organGate（idol 右鍵＋`persistentData` warpfree） | vanilla-event |
| `ino_dlc_build:pandora_box_blue` | `ino_dlc_build_block_broken.js` | 1 | **7 及 20** | 無 organGate（pandora 方塊右鍵） | vanilla-event |

**「冇其他通道」已機械核實**：10 件 `js_recipe_defs=0`（598 js 逐行掃 `event.(shaped|…)|\.recipes\.|registerCustomRecipe|event\.custom\(` 且同行含該 id）；正對照 `silver_ring` → `recipes/common.js:197`。

防洗版：每 focus 最多 **3** 條；只喺取得意圖（`wantsFullAcquire`）出；三種 label 分開。

## 2. 設計

1. **掃描 pass**：`PackIndex.build` walk `:206-246` → `indexScriptItems(rel)` `:1568-1606` 末尾（已收 rel／已 readText）。**唔可以**放 `KubeJsMechanicScan`（400 檔上限 `:89`；本包 598 檔）。
2. **Parser** `logic/JsObtainSites.java`：`record Site(outId, triggerId, mechanism, rel, line, entryLine, cond, kind, organGate, gateKind, heldItem)`；`kind ∈ {PRODUCE, SWAP, TRANSFORM}`；`gateKind ∈ {TABLE_KEY, BODY_CHECK, NONE}`；純函數、fixture 可測。
   - **行號語意**：`line` ＝ **sink 呼叫行**（fact `src:<rel>:<line>` 印呢個）；`entryLine` ＝ handler／事件註冊行（只入 site／log）。1-based。
   - **`organGate`（v6.6 新增，load-bearing）**：① 表 key 型 → 由**同一檔嘅表宣告位置**（最近嘅 `const/var NAME = {`，且 NAME／函數首參數含 `organ`）判 key 係器官；② body 型 → 由 `getPlayerChestCavityItemMap|organScores.get|getChestCavityInstance|isEquippedCurio` 語句抽 id；**兩者都要有**：答案冇講 gate ＝ 玩家照做唔到（`b_a_d:keg` 未植入 ⇒ 玻璃瓶右鍵永遠冇反應）。
   - **`heldItem`／觸發對象**：body 內 `getMainHandItem()/getOffHandItem()/event.item/event.block` 字面係 fact 必備欄（例 vodka 要「手持玻璃瓶」、campfire 要「手持熟飯」）。
   - **`cond` 規格（字面 ≤40 字，v6.6 擴）**：加 `itemMap\.has\(|getPlayerChestCavityItemMap|organScores\.get\(|getChestCavityInstance\(|isEquippedCurio\(`；原有 `%`／`概率|機率|几率`／`Math.random\(\)`／`random\s*[<>]=?\s*[0-9.]+`／`\.age\s*%\s*[0-9]+`／`hasEffect\(|hasNBT\(|isUnderWater\(|getDifficulty\(|persistentData`。唔解釋、唔自創動詞。
   - `SWAP`：同一 handler 內雙向 give 同 id。`TRANSFORM`：`setStackInSlot(slot, Item.of('id'))` **或** `setItemSlot(slot, Item.of('id'))`（**PRODUCE 只認 give／loot**）。
3. **索引**：`Map<String,List<Site>> jsObtainByOutput`；`build()` `:160-172` clear；**唔准**喺 `beginAskSession()` `:386-389` clear。
4. **可見性（強制 admission）**：`acquireFactsDetailed` `:1259` ranked 建構後、`:1265` graphFacts 迴圈前 pre-pass 入 ranked（`RankedAcquire(2, seq++)`）；cap（`:1266` `ranked+cycles>=12`）只箍迴圈；`MAX_JS_OBTAIN_LINES=3` 截斷點＝pre-pass 內排序後取頭 3。
5. 保險 branch（可選）：`:1379`（迴圈尾）加 `-[js_produce|js_swap|js_transform]->`。
6. **`AskEngine.graphLines` 唔加 branch**（死碼）：`retrieve()` `:270` 先拍 snapshot，`acquireFactsDetailed` `:287` 後跑；宣告喺 `:469`。
7. **文字**：`ReplyLang.jsProduce/jsSwap/jsTransform`，簽名 `(code, outName, outId, triggerName, triggerId, cond, src, organName, organId, heldName)`；模板（**gate 有就要出**）：
   `【腳本產出】<outName>（<outId>）｜觸發：<triggerName>（<triggerId>）｜前置器官：<organName>（<organId>）｜需要手持：<heldName>｜條件：<cond 原文>｜src:<rel>:<line>`
   — 玩家名走 `OfficialDisplay`；器官名走**同一 lang 通道**（`item.<ns>.<path>` → `b_a_d:keg`＝发酵桶；**實測 44 個 zh_cn lang 檔，兩個檔有 BOM／壞 JSON ⇒ 要容錯**）；缺名就出 id（機械式，唔自創）。
8. **開關**：`PackAiConfig.JS_OBTAIN_CHANNEL`（`jsObtainChannel=true`）＋ `JS_OBTAIN_DIAG_LOG`；kill-switch 包三處。
9. **拆單**：單 1＝本 plan；單 2＝包文字通道（B 11 件）；單 3＝文件修正。

## 3. 驗收

| # | artefact | 今日 | 實作後 |
|---|---|---|---|
| S1 | 索引 site（讀真 code） | 紅 | `runAcquireFactsCheck` 真 snippet → assert site／`src:` ＝ fixture `sink_lines`／`kind`／`entryLine` |
| S2 | **A_hard 10 件**逐件出 `-[js_produce]->`（唔含 silver_ring／god_bless；`pandora_box_blue` 兩個 site） | 紅 | 10 件 assert；負控 `minecraft:stone`、註解 id → 0 site |
| S3 | 真機 trace 含新 label（smoke：vodka／sliced_meat／god_bless） | 紅 | grep `packai/trace/ask-*.jsonl` |
| S4 | 旗艦條件字面：vodka 行含 `0.2`（真 code `b_a_d_rclick.js:82`） | 紅 | harness 字串 assert |
| S5 | 4 家族 parser 能力（fixture；本包冇 assign 站點） | 紅 | fixture 家族 |
| S6 | busy focus（≥12 更早 edge）仍見 js 行；開關關 → 0 行 | — | cap fixture 對比 |
| S7 | 文字：機械式＋官方名＋無自創動詞（含器官名） | — | 白名單動詞比對 |
| S8 | `addItemCooldown` 唔出 site | 紅 | 真句 fixture |
| S9 | 抽樣：`ids_166` ≥20 件出 js 行；重掃 count ∈ [160,170] | 紅 | fixture |
| S10 | 迴歸：其他取得行 byte-identical | — | 開／關兩跑比對 |
| S11 | **TRANSFORM 鑑別力**：18 站點真 snippet → kind=TRANSFORM、零 `js_produce`（正對照 vodka give→PRODUCE） | 紅 | 18 站點 assert |
| **S12** | **table-key gate（v6.6 核心）**：vodka 行必須含 **`b_a_d:keg`／「发酵桶」**＋「手持玻璃瓶」；mead／rice_wine／egg_grog 同樣 | **紅** | harness 對 4 件 assert；負控：去掉 gate 嘅版本必須紅 |
| **S13** | taiji 系 2 件必須含 **`b_a_d:taiji`／「子」**；`b_a_d:picture` 含 **「链式信件」**；`shrimp_fried_rice` 含 **「螳螂虾拳」** | **紅** | 4 件 assert（body-check 型亦要出） |
| **S14** | 器官名解析：`item.<ns>.<path>` 對 4 個 gate id 出到 zh_cn 名（`发酵桶`／`子`／`链式信件`／`螳螂虾拳`）；lang 檔壞／缺 → 回 id 唔准 crash | 紅 | harness＋2 個負對照（BOM 檔、缺鍵） |

入口：`runAcquireFactsCheck`＋`runAskMechanicFactsCheck`（`-I tmp-check.gradle`，JDK 17）。

## 4. 回滾

新 class＋config×2＋pre-pass＋ReplyLang×3＋3 檔 lang＋`AskToolContext` 常數；索引 in-memory。`git revert` 單 commit；開關即時失效。

## 5. 未知／盲點

166 係掃描值（變數 id／動態註冊／body >6000 字未覆蓋）；`cond` 只做字面（唔翻譯）；busy focus 分佈未量；B 11 件 JEI 通道未證通；swap 家族可能跨 handler。
**gate（v6.6 新）**：`organ_gate_scan` 134/265 係**heuristic 上界**（未逐個手核；「最近嘅表 key」可能跨表誤配）；addon 家族只覆蓋實測到嘅 863 個註冊 call（**其他 addon 未窮舉**，例如 `ServerEvents.entityLootTables`／`PlayerEvents.spellOnCast` 嘅產出未分類）；器官 `b_a_d:taiji` 嘅 zh_cn 名係單字「子」（疑 pack lang 未寫完，要用 id 兜底）。
**TRANSFORM 家族**：18 站點只有 `god_bless_full_necklace` 做真機 smoke；6/10 個 outId 另有 recipe 命中（transform 只係額外路徑，唔聲稱唯一）。

## 6. Review 狀態

- v5.5 線：R1 2:8 → R2 5:5 → R3 6:4 → R4 6:4 → R5 4:6／8:2 → 裁判 7:3 → R6 5:5 → R7 → SK 改 code-first。
- **v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3（N1 HIGH／N2 LOW）→ R5 正方 8 : 反方 2（達標，N3 LOW 已修）**。
- **R6（本輪）**：SK 09-17 兩條糾正 → ① KubeJS 配方唔做（已在 §0／§1）；② **organ gate 必須入 fact**（我原本連 taxonomy 都錯：把 addon 器官策略表當「物品右鍵」，10 件硬閘有 **8 件**其實要器官／體內檢查）。v6.6 加 `organGate`＋`heldItem`＋S12–S14；R6 只核呢批新增，唔准加新要求。
- ⚠️ 記錄：R5 輪 cursor 冇寫自己 report 檔（stdout 中文亂碼）→ R5 record 係 Hermes 重建（檔頭已標）。
