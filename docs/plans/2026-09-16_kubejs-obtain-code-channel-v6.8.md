# Plan v6.8 — 腳本產出通道（code-only；觸發／前置器官／手持分開寫；硬閘＝單一可重跑清單）

> 2026-09-17｜v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3 → **R5 8:2** → SK 糾正①（配方交 JEI）②（器官 gate）→ R6 **4:6** → v6.7 → **R7 8:2 達標** → **SK 糾正③：`觸發：發酵桶右鍵` 講錯** → **v6.8 修 trigger 模型**
> 狀態：**R8 有界輪進行中**（只核 v6.8 新增嘅 trigger 模型；SK 明示 review 做到 正方 ≥8 : 反方 ≤2）
> fixture（repo）：`docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json`（40.5 KB）

## 0. 目標（有界寫法；答案四段）

玩家問「X 點取得」，而 KubeJS code 有寫產出 X 嘅路時，答案要講得出四樣、**分開講清楚**：
1. **觸發**＝真事件（右鍵／玩家攻擊命中／方塊右鍵 神像／狼死亡…）
2. **前置器官**＝要先植入嘅器官（`b_a_d:keg`＝发酵桶）——**器官唔係觸發**（SK 09-17 糾正③：`觸發：發酵桶右鍵` 係錯）
3. **需要手持**（主／副手字面）
4. **條件字面**（機率）＋ **產出**
**fact 只由 code 讀**；**KubeJS 配方唔做**（JEI 已覆蓋）；JEI 信息頁／tooltip 唔准當 fact。
承諾＝**A_hard 10 件硬閘**＋索引能力範圍 166（抽樣 ≥20）。

## 1. 範圍（可重跑）

掃 `<instance>/kubejs/{server,startup}_scripts/**/*.js`，排除 `/client_scripts/`、`/assets/`、`/data/`（實掃 **598** 檔）。

**ChestCavity 器官機制（v6.8 補完；實測）**：器官＝物品＋**tag**（`registerOrgan(new Organ('b_a_d:keg')…).tag('kubejs:rclick_only')` @ `startup_scripts/b_a_d/b_a_d_item_register.js:2050`）；**48 個 dispatcher 檔**註冊真事件 → 讀 `getPlayerChestCavityTypeMap(player)`（tag→玩家身上器官）→ 叫 `STRATEGY[organ.id](event, organ)`。

| organ tag | dispatcher（真事件） |
|---|---|
| `kubejs:rclick_only`／`rclick` | `server_scripts/organ/item_right.js:2` = **ItemEvents.rightClicked** |
| `kubejs:damage_only`／`damage` | `server_scripts/event_stream.js:17`（`global.LivingHurtByPlayer`）→ `organ/player_damage.js:8` |
| `kubejs:key_pressed` | `organ/key_bind.js:2` = `NetworkEvents.dataReceived('ogran_key_pressed')` |
| `kubejs:active_only`／`active` | `organ/active_effect.js:25`（**開胸腔激活**，唔係玩家動作） |
| `kubejs:break_only`／`break` | `organ/block_broken.js:2` = `BlockEvents.broken` |
| `kubejs:food`／`eat_effect_only` | `organ/food_eaten.js:2` = `ItemEvents.foodEaten` |
| `kubejs:spellcast_only` | `mfdlc/organ/mfdlc_spellcast.js:1` = `PlayerEvents.spellOnCast` |
| `kubejs:player_tick_only` | `organ/player_tick.js:2` = `PlayerEvents.tick` |

**handler 寫法兩種都要認**：① 物件表 `const NAME = { 'ns:id': function(event, organ…){…} }`（286 張表／160 含 organ key／1173 key）；② 直接賦值 `STRATEGY['ns:id'] = function(event, organ, data){…}`（例 `b_a_d/b_a_d_player_damage.js:1`）。
**trigger 規則（v6.8）**：`trigger` ＝ dispatcher 註冊嘅真事件（＋檔:行）；**唔准**用器官名／器官 tag 當觸發。

**gate 三類**：TABLE_KEY（表 key＝器官）／BODY_CHECK（只認 `getPlayerChestCavityItemMap(...).has('id')`、`itemMap.has('id')`、`organScores.get(new ResourceLocation('chestcavity','x'))` 三形；唔准裸 `getChestCavityInstance()`／非 give 控制流 `isEquippedCurio`）／NONE（負對照 `golden_age:believe`、`ino_dlc_build:pandora_box_blue`）。
**heldItem**：由包住 sink 行嘅最近 if 條件抽（dominant branch；5 形；唔准 ±N 掃描）。

### A_hard 10（gate／held／trigger 逐件實測；1-based）

| outId | rel | sink | 觸發（真事件，dispatcher） | 前置器官（官名） | 手持 |
|---|---|---|---|---|---|
| `b_a_d:vodka` | `b_a_d_rclick.js` | 87 | **右鍵**（ItemEvents.rightClicked @ `organ/item_right.js:2`） | `b_a_d:keg`＝发酵桶 | 玻璃瓶 @79 |
| `b_a_d:mead` | 同上 | 91 | 同上 | 同上 | 同上 |
| `b_a_d:rice_wine` | 同上 | 95 | 同上 | 同上 | 同上 |
| `b_a_d:egg_grog` | 同上 | 99 | 同上 | 同上 | 同上 |
| `b_a_d:eyeball_sacrifice` | `b_a_d_player_damage.js` | 2280 | **玩家攻擊命中**（`event_stream.js:17`→`player_damage.js:8`） | `b_a_d:taiji`＝子 | `b_a_d:hammer` @2272 |
| `b_a_d:sliced_meat` | 同上 | 2294 | 同上 | 同上 | `b_a_d:slice_knife` @2284 |
| `b_a_d:picture` | `effect/mob_effect.js` | 737 | **狼死亡**（EntityEvents.death @731） | `b_a_d:curse_letter_organ`＝链式信件（body） | offhand `biomancy:despoil_sickle` @736 |
| `alexsmobs:shrimp_fried_rice` | `item/b_a_d_item.js` | 497 | **營火右鍵**（BlockEvents.rightClicked @493） | `kubejs:mantis_shrimp_fist`＝螳螂虾拳（body） | `farmersdelight:cooked_rice` @495 |
| `golden_age:believe` | `golden_age/events.js` | 1043 | **神像右鍵**（BlockEvents.rightClicked @1014） | NONE | `golden_age:old_ones_heart_warp` @1038 |
| `ino_dlc_build:pandora_box_blue` | `ino_dlc_build_block_broken.js` | 7 及 20 | **潘多拉嵌板右鍵**（@1） | NONE | site#1 方塊；site#2 `genius_full_bottle_blank` @14 |

## 2. 設計

1. **掃描 pass**：`PackIndex.build` walk `:206-246` → `indexScriptItems(rel)` `:1568-1606` 末尾。唔可以放 `KubeJsMechanicScan`（400 檔上限 `:89`）。
2. **Parser** `logic/JsObtainSites.java`：`record Site(outId, trigger, rel, line, entryLine, cond, kind, organGate, gateKind, heldItem, heldSlot)`；`record Trigger(kind, event, dispatcherRel, dispatcherLine, organTag)`；`kind ∈ {PRODUCE, SWAP, TRANSFORM}`；`gateKind ∈ {TABLE_KEY, BODY_CHECK, NONE}`；`heldSlot ∈ {mainhand, offhand, trigger_block}`。純函數、fixture 可測。
   - `line` ＝ sink 行；`entryLine` ＝ handler／事件註冊行；1-based。
   - TABLE_KEY gate 由「表宣告＋**參數列表含 organ**（本包係第 2 個參數；首參數實測 0 命中）」判；器官 tag 由 `startup_scripts` `registerOrgan(...).tag('kubejs:…')` 抽（可選，用於解釋機制）。
   - `cond` 字面（≤40 字）：`%`／`概率|機率|几率`／`Math.random\(\)`／`random\s*[<>]=?\s*[0-9.]+`／`\.age\s*%\s*[0-9]+`／`hasEffect\(|hasNBT\(|isUnderWater\(|getDifficulty\(|persistentData`。唔解釋、唔自創動詞。
3. **索引**：`Map<String,List<Site>> jsObtainByOutput`；`build()` `:160-172` clear；唔准喺 `beginAskSession()` `:386-389` clear。
4. **可見性（強制 admission）**：`acquireFactsDetailed` `:1259` ranked 建構後、`:1265` 迴圈前 pre-pass 入 ranked（`RankedAcquire(2, seq++)`）；cap（`:1266`）只箍迴圈；`MAX_JS_OBTAIN_LINES=3` 截斷點＝pre-pass 內排序後取頭 3。
5. 保險 branch（可選）：`:1379` 加 `-[js_produce|js_swap|js_transform]->`。
6. **`AskEngine.graphLines` 唔加 branch**（死碼）：`retrieve()` `:270` 先拍 snapshot，`acquireFactsDetailed` `:287` 後跑。
7. **文字**：`ReplyLang.jsProduce/jsSwap/jsTransform`，簽名 `(code, outName, outId, triggerLabel, cond, src, organName, organId, heldName, heldId)`；模板
   `【腳本產出】<outName>（<outId>）｜觸發：<triggerLabel>｜前置器官：<organName>（<organId>）｜需要手持：<heldName>｜條件：<cond 原文>｜src:<rel>:<line>`
   - `<triggerLabel>` ＝**事件**（例「右鍵」「玩家攻擊命中」「神像右鍵」），**唔係**器官名；organName／heldName 空（NONE／null）⇒ **成段唔出**（唔出空標籤）。玩家名走 `OfficialDisplay`；器官名走 lang（`item.<ns>.<path>`；缺名／壞檔回 id）。
8. **開關**：`PackAiConfig.JS_OBTAIN_CHANNEL`（預設 true）＋ `JS_OBTAIN_DIAG_LOG`；kill-switch 包三處。
9. **拆單**：單 1＝本 plan；單 2＝包文字通道（B 11 件）；單 3＝文件修正。

## 3. 驗收

S1–S11 同 v6.7（索引／A_hard 10／真機 trace／vodka 0.2／家族／busy focus／文字／`addItemCooldown`／抽樣 166／迴歸／TRANSFORM 18 站點）。

| # | artefact | 今日 | 實作後 |
|---|---|---|---|
| S12 | **TABLE_KEY gate**：vodka／mead／rice_wine／egg_grog 行必含 `b_a_d:keg`／「发酵桶」＋「玻璃瓶」 | 紅 | 4 件 assert；負控：剝走 gate ⇒ 必紅 |
| S13 | taiji 系含 `b_a_d:taiji`／「子」＋hammer／slice_knife；picture 含「链式信件」＋offhand despoil_sickle；shrimp 含「螳螂虾拳」＋cooked_rice；believe 含 heart_warp | 紅 | 逐件 assert |
| S14 | 器官名解析（4 id → zh_cn 名；BOM／壞 JSON／缺鍵 3 負對照回 id 唔 crash） | 紅 | harness |
| S15 | gate／held 抽取器鑑別力：① 首參數 0 命中仍抽到 keg／taiji；② believe／pandora → NONE；③ held 唔准吸入 `held_item_sibling_traps` | 紅 | harness 3 組 |
| **S16** | **trigger 分家（v6.8 核心）**：① vodka 行必須出**「右鍵」**（事件）而**唔准**將器官當觸發（負控字串：答案唔准含「發酵桶右鍵」「器官右鍵」等把器官當動作嘅寫法）；② taiji 系出**「玩家攻擊命中」**；③ picture 出**「狼死亡」**；④ `triggerLabel` 唔准＝organGate 名或 organTag | **紅** | harness：4 組正斷言＋3 個負字串斷言（用真 fixture 字串） |

入口：`runAcquireFactsCheck`＋`runAskMechanicFactsCheck`（`-I tmp-check.gradle`，JDK 17）。

## 4. 回滾

新 class＋config×2＋pre-pass＋`ReplyLang`×3＋3 檔 lang＋`AskToolContext` 常數；索引 in-memory。`git revert` 單 commit；開關即時失效。

## 5. 未知／盲點

166 係掃描值；`cond` 只做字面；busy focus 分佈未量；B 11 件 JEI 通道未證通；swap 家族可能跨 handler。
**器官／trigger（v6.8 新）**：dispatcher **48 檔未窮舉分類**（`active` 類＝開胸腔激活，唔係玩家動作，本單唔 emit）；`organ_gate_scan` 134/265 係 heuristic 上界；held 抽取靠 dominant branch；`b_a_d:taiji` zh 名係單字「子」（用 id 兜底）。

## 6. Review 狀態

- **v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3 → R5 8:2 → R6 4:6 → R7 8:2（達標）→ SK 糾正③ → v6.8 → R8 進行中**。
- **SK 09-17 三條糾正**：① KubeJS 配方唔做（JEI 覆蓋）② 前置係器官（ChestCavity addon）③ **`觸發：發酵桶右鍵` 錯**——器官唔係觸發；真觸發係 dispatcher 註冊嘅事件（右鍵）。
- **v6.8 修法**：① 新增 `Trigger(kind, event, dispatcher, organTag)`＋§1 dispatcher 對照表（8 族實測）；② §2.7 模板把「觸發」同「前置器官」分成兩段；③ 新增 **S16**（trigger 唔准＝器官；3 個負字串斷言）；④ fixture A_hard 加 `trigger`（含 `zh`）＋`organ_dispatch` 機制圖、刪舊 `trigger_zh`／`handler` 欄。
- ⚠️ 記錄：R5 輪 cursor 冇寫自己 report 檔（stdout 亂碼）→ R5 record 係 Hermes 重建（檔頭已標）。
