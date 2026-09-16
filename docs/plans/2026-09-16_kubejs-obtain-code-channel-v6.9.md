# Plan v6.9 — 腳本產出通道（觸發／器官／手持三段分家；Trigger schema 單一化）

> 2026-09-17｜v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3 → **R5 8:2** → R6 4:6 → v6.7 → **R7 8:2** → SK 糾正③（觸發≠器官）→ v6.8 → **R8 5:5（H1–H4）** → **v6.9 收 H1–H4**
> 狀態：**R9 有界輪進行中**（只核 H1–H4 是否 RESOLVED；SK 明示 review 做到 正方 ≥8 : 反方 ≤2）
> fixture（repo）：`docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json`（42.5 KB）

## 0. 目標（答案四段，互不混寫）

1. **觸發**＝真事件（右鍵／玩家攻擊命中／神像右鍵／狼死亡…）——**唔准**含器官、tag 或手持物
2. **前置器官**＝要先植入嘅器官（`b_a_d:keg`＝发酵桶）——器官**唔係**觸發（SK 糾正③）
3. **需要手持**（主／副手字面）——唔准寫入觸發段（R8 H1）
4. **條件字面**＋**產出**
**fact 只由 code 讀**；**KubeJS 配方唔做**（JEI 覆蓋）；JEI 文案唔准當 fact。承諾＝A_hard 10 硬閘＋索引範圍 166（抽樣 ≥20）。

## 1. 範圍（可重跑）

掃 `kubejs/{server,startup}_scripts/**/*.js`，排除 `/client_scripts/`、`/assets/`、`/data/`（實掃 598 檔）。

**ChestCavity 器官機制（實測）**：器官＝物品＋tag（`registerOrgan(new Organ('b_a_d:keg')…).tag('kubejs:rclick_only')` @ `startup_scripts/b_a_d/b_a_d_item_register.js:2050`）；**48 個 dispatcher 檔**註冊真事件 → `getPlayerChestCavityTypeMap(player)`（tag→身上器官）→ `STRATEGY[organ.id](event, organ)`。

| organ tag | dispatcher＝真事件 |
|---|---|
| `kubejs:rclick_only`／`rclick` | `server_scripts/organ/item_right.js:2` = **ItemEvents.rightClicked** |
| `kubejs:damage_only`／`damage` | **`startup_scripts/entity_hurt.js:6` = ForgeEvents.onEvent(LivingHurtEvent)** → `server_scripts/event_stream.js:17`（`global.LivingHurtByPlayer`）→ `organ/player_damage.js:8` |
| `kubejs:key_pressed` | `organ/key_bind.js:2` = `NetworkEvents.dataReceived('ogran_key_pressed')` |
| `kubejs:active_only`／`active` | `organ/active_effect.js:25`（**開胸腔激活**，唔係玩家動作） |
| `kubejs:break_only`／`break` | `organ/block_broken.js:2` = `BlockEvents.broken` |
| `kubejs:eat_effect_only`／`eat_effect` | `organ/food_eaten.js:2` = `ItemEvents.foodEaten` |
| `kubejs:spellcast_only` | `mfdlc/organ/mfdlc_spellcast.js:1` = `PlayerEvents.spellOnCast` |
| `kubejs:player_tick_only` | `organ/player_tick.js:2` = `PlayerEvents.tick` |

⚠️ R8 H4 更正：① 傷害鏈係 **Forge `LivingHurtEvent`**（`EntityEvents.hurt` 已被 `entity_hurt.js:3-4` 明文取代，唔准再寫 `EntityEvents.hurt`）；② `kubejs:food` **唔**由 `food_eaten.js` 消費——真消費者 6 處：`hpdlc/hpdlc_playerticks.js:366`／`mfdlc/organ/mfdlc_organ_active.js:368`／`mrqx_extra_pack/mrqx_organ_effect/mrqx_food_eaten.js:12,21,44`／`organ/active_effect.js:427`。

**handler 寫法兩種都要認**：① 物件表 `const NAME = { 'ns:id': function(event, organ…){…} }`（286 表／160 含 organ key／1173 key）；② 直接賦值 `STRATEGY['ns:id'] = function(event, organ, data){…}`（例 `b_a_d/b_a_d_player_damage.js:1`）。
**gate 三類**：TABLE_KEY／BODY_CHECK（白名單三形）／NONE（負對照 `golden_age:believe`、`ino_dlc_build:pandora_box_blue`）。**heldItem**：dominant-branch 5 形（唔准 ±N 掃描）。

### A_hard 10（1-based；觸發＝事件）

| outId | rel | sink | 觸發（事件） | dispatcher | 前置器官 | 手持 |
|---|---|---|---|---|---|---|
| `b_a_d:vodka` | `b_a_d_rclick.js` | 87 | **右鍵** | `organ/item_right.js:2` | `b_a_d:keg`＝发酵桶 | 玻璃瓶 @79 |
| `b_a_d:mead` | 同上 | 91 | 右鍵 | 同上 | 同上 | 同上 |
| `b_a_d:rice_wine` | 同上 | 95 | 右鍵 | 同上 | 同上 | 同上 |
| `b_a_d:egg_grog` | 同上 | 99 | 右鍵 | 同上 | 同上 | 同上 |
| `b_a_d:eyeball_sacrifice` | `b_a_d_player_damage.js` | 2280 | **玩家攻擊命中** | `entity_hurt.js:6`→`event_stream.js:17`→`player_damage.js:8` | `b_a_d:taiji`＝子 | `b_a_d:hammer` @2272 |
| `b_a_d:sliced_meat` | 同上 | 2294 | 玩家攻擊命中 | 同上 | 同上 | `b_a_d:slice_knife` @2284 |
| `b_a_d:picture` | `effect/mob_effect.js` | 737 | **狼死亡** | `EntityEvents.death` @731 | `b_a_d:curse_letter_organ`＝链式信件（body） | offhand `biomancy:despoil_sickle` @736 |
| `alexsmobs:shrimp_fried_rice` | `item/b_a_d_item.js` | 497 | **營火右鍵** | `BlockEvents.rightClicked` @493 | `kubejs:mantis_shrimp_fist`＝螳螂虾拳（body） | `farmersdelight:cooked_rice` @495 |
| `golden_age:believe` | `golden_age/events.js` | 1043 | **神像右鍵** | `BlockEvents.rightClicked` @1014 | NONE | `golden_age:old_ones_heart_warp` @1038 |
| `ino_dlc_build:pandora_box_blue` | `ino_dlc_build_block_broken.js` | 7 及 20 | **潘多拉嵌板右鍵** | `BlockEvents.rightClicked` @1 | NONE | site#1 方塊；site#2 `genius_full_bottle_blank` @14 |

## 2. 設計

1. **掃描 pass**：`PackIndex.build` walk `:206-246` → `indexScriptItems(rel)` `:1568-1606` 末尾（唔可以放 `KubeJsMechanicScan`，400 檔上限 `:89`）。
2. **Parser** `logic/JsObtainSites.java`：
   ```java
   record Trigger(TriggerKind kind, String event, String dispatcherRel, int dispatcherLine, String organTag) {}
   record Site(String outId, Trigger trigger, String rel, int line, int entryLine, String cond,
               Kind kind, String organGate, GateKind gateKind, String heldItem, String heldSlot) {}
   enum TriggerKind { RIGHT_CLICK, HURT_BY_PLAYER, ENTITY_DEATH, BLOCK_RIGHT_CLICK }
   enum Kind { PRODUCE, SWAP, TRANSFORM }   enum GateKind { TABLE_KEY, BODY_CHECK, NONE }
   ```
   **fixture ↔ Java mapping（R8 H2；唯一真值）**：`trigger.kind`→`TriggerKind`（`right_click`↔RIGHT_CLICK／`hurt_by_player`↔HURT_BY_PLAYER／`entity_death`↔ENTITY_DEATH／`block_right_click`↔BLOCK_RIGHT_CLICK）；`trigger.event`→`event`；`trigger.dispatcher`（`檔:行` 字串，可含 `→` 鏈→**只在鏈首取 rel+line**）→`dispatcherRel`／`dispatcherLine`；`trigger.organ_tag`→`organTag`；**`trigger.zh`／`zh_rule`／`organ_tag_src`＝display-only／audit，唔入 Java record**（`zh` 只做 harness cross-check）。
   - `line` ＝ sink 行；`entryLine` ＝ handler 註冊行；1-based。
   - TABLE_KEY：表宣告＋**參數列表含 organ**（本包第 2 個參數；首參數實測 0 命中）。BODY_CHECK：白名單三形（唔准裸 `getChestCavityInstance()`／非 give `isEquippedCurio`）。heldItem：dominant branch。
   - `cond` 字面（≤40 字）：`%`／`概率|機率|几率`／`Math.random\(\)`／`random\s*[<>]=?\s*[0-9.]+`／`\.age\s*%\s*[0-9]+`／`hasEffect\(|hasNBT\(|isUnderWater\(|getDifficulty\(|persistentData`。
3. **索引**：`Map<String,List<Site>> jsObtainByOutput`；`build()` `:160-172` clear；唔准喺 `beginAskSession()` `:386-389` clear。
4. **可見性**：`acquireFactsDetailed` `:1259` 後、`:1265` 前 pre-pass 入 ranked（`RankedAcquire(2, seq++)`）；cap（`:1266`）只箍迴圈；`MAX_JS_OBTAIN_LINES=3` 截斷點＝pre-pass 內排序後取頭 3。
5. 保險 branch（可選）：`:1379`。
6. **`AskEngine.graphLines` 唔加 branch**（死碼）。
7. **文字**：`ReplyLang.jsProduce/jsSwap/jsTransform`，簽名 `(code, outName, outId, triggerLabel, cond, src, organName, organId, heldName, heldId)`；
   `【腳本產出】<outName>（<outId>）｜觸發：<triggerLabel>｜前置器官：<organName>（<organId>）｜需要手持：<heldName>｜條件：<cond 原文>｜src:<rel>:<line>`
   - **`triggerLabel` 來源（R8 H2/H1）**：Java 內建 map `TriggerKind→中文事件名`（RIGHT_CLICK→「右鍵」／HURT_BY_PLAYER→「玩家攻擊命中」／ENTITY_DEATH→「狼死亡」需 event 內 entity 名／BLOCK_RIGHT_CLICK→「<方塊官方名>右鍵」）；fixture `trigger.zh` **只做 harness cross-check**，唔做 runtime 來源。**organName／heldName 空 ⇒ 成段唔出**（唔出空標籤）。玩家名走 `OfficialDisplay`；器官名走 lang（缺名回 id）。
8. **開關**：`PackAiConfig.JS_OBTAIN_CHANNEL`（預設 true）＋ `JS_OBTAIN_DIAG_LOG`；kill-switch 包三處。
9. **拆單**：單 1＝本 plan；單 2＝包文字通道（B 11 件）；單 3＝文件修正。

## 3. 驗收

S1–S15 同 v6.7／v6.8（索引／A_hard 10／trace／0.2／家族／busy focus／文字／`addItemCooldown`／抽樣 166／迴歸／TRANSFORM 18／gate／held／器官名／抽取器鑑別力）。

| # | artefact | 今日 | 實作後 |
|---|---|---|---|
| **S16** | **trigger 分家（R8 H1/H3 加固）**：① 正斷言：vodka 行觸發段＝「右鍵」、taiji 系＝「玩家攻擊命中」、picture＝「狼死亡」（值由 fixture `trigger.zh` 機械導出）；② **負字串 ≥6（全部要釘死，唔准寫「等」）**：`发酵桶右鍵`／`發酵桶右鍵`／`器官右鍵`／`觸發：發酵桶`／`觸發：右鍵（手持`／`觸發：右鍵（器官`（清單存 fixture `trigger_zh_negative_controls`）；③ `triggerLabel ≠ organGate 官方名／id`；④ **觸發段唔准含 held 名／id**（負控：`觸發：右鍵（手持玻璃瓶）` 必紅） | **紅** | harness：3 正 assert＋6 負字串＋2 結構 assert |

入口：`runAcquireFactsCheck`＋`runAskMechanicFactsCheck`（`-I tmp-check.gradle`，JDK 17）。

## 4. 回滾

新 class＋config×2＋pre-pass＋`ReplyLang`×3＋3 檔 lang＋`AskToolContext` 常數；索引 in-memory。`git revert` 單 commit；開關即時失效。

## 5. 未知／盲點

166 係掃描值；`cond` 只做字面；busy focus 分佈未量；B 11 件 JEI 通道未證通；swap 家族可能跨 handler。
**器官／trigger**：48 dispatcher **未窮舉分類**（`active` 類＝開胸腔激活，本單唔 emit）；`kubejs:food` 6 個消費者未分類；`organ_gate_scan` 134/265 係 heuristic 上界；held 抽取靠 dominant branch；`b_a_d:taiji` zh 名係單字「子」。

## 6. Review 狀態

- **v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3 → R5 8:2 → R6 4:6 → R7 8:2 → SK 糾正③ → R8 5:5 → v6.9 → R9 進行中**。
- **R8 捉到（我已親核）**：**H1 HIGH** fixture `trigger.zh` 寫成「右鍵（手持玻璃瓶）」＝手持混入觸發（同 §0 四段分家打架）；**H2 HIGH** `Trigger` 三寫法互斥（§2.2／§6／fixture 無 mapping）；**H3 MED** S16 負控繁簡唔對＋「3 個」只列 2＋冇打 held-in-trigger；**H4 LOW** ① 傷害鏈真身係 **Forge `LivingHurtEvent`**（唔係 `EntityEvents.hurt`）② `kubejs:food` 唔屬 `food_eaten.js` 族（真消費者 6 處）。
- **v6.9 修法**：① H1 → fixture `trigger.zh` 改純事件（`右鍵`／`玩家攻擊命中`／`狼死亡`／`神像右鍵`／`營火右鍵`／`潘多拉嵌板右鍵`）＋`zh_rule`；（H2）plan 寫死 **單一 `Trigger` record ＋ fixture↔Java mapping 表**（`zh`／`organ_tag_src`＝display-only 唔入 record）；③ H3 → S16 負字串釘死 **6 條**（存 fixture）＋加「觸發段唔准含 held」＋`triggerLabel ≠ organGate` 結構斷言；④ H4 → §1 更正傷害鏈同 `kubejs:food`，fixture A_hard taiji `trigger.event` 改 Forge 版並註明唔准寫 `EntityEvents.hurt`。
- ⚠️ 記錄：R5 輪 cursor 冇寫自己 report 檔（stdout 亂碼）→ R5 record 係 Hermes 重建（檔頭已標）。
