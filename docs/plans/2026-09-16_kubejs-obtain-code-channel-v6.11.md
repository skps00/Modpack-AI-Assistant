# Plan v6.11 — 腳本產出通道（label 顯示名分流：item/block 走 OfficialDisplay、entity 走 I18n）

> 2026-09-17｜v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3 → **R5 8:2** → R6 4:6 → R7 8:2 →（SK 糾正③）→ R8 5:5 → R9 6:4 → R10 5:5（H5 RESOLVED／H2 剩 entity 解析通道）→ **v6.11 收 H2** → **R11 進行中**
> 狀態：**R11 有界輪**（只核 H2：entity／block label 解析通道；SK 明示做到 正方 ≥8 : 反方 ≤2）
> fixture（repo）：`docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json`（46.0 KB）

## 0. 目標（答案四段；**label 一律官方顯示名**）

1. **觸發**＝真事件；**label 由官方顯示名機械生成**（R10 修正；見 §2.7）——唔准自創詞、唔准用 item 名代替方塊名、唔准含器官／tag／手持物
2. **前置器官**＝要先植入嘅器官（`b_a_d:keg`＝发酵桶）
3. **需要手持**（主／副手字面）
4. **條件字面**＋**產出**
**fact 只由 code 讀**（＋官方名由 `OfficialDisplay`）；**KubeJS 配方唔做**（JEI 覆蓋）；JEI 文案唔准當 fact。承諾＝A_hard 10 硬閘＋索引範圍 166（抽樣 ≥20）。

## 1. 範圍（可重跑）

掃 `kubejs/{server,startup}_scripts/**/*.js`，排除 `/client_scripts/`、`/assets/`、`/data/`（實掃 598 檔）。

**ChestCavity 器官機制（實測）**：器官＝物品＋tag（`registerOrgan(new Organ('b_a_d:keg')…).tag('kubejs:rclick_only')` @ `startup_scripts/b_a_d/b_a_d_item_register.js:2050`）；**48 個 dispatcher 檔**註冊真事件 → `getPlayerChestCavityTypeMap(player)`（tag→身上器官）→ `STRATEGY[organ.id](event, organ)`。

| organ tag | dispatcher＝真事件 |
|---|---|
| `kubejs:rclick_only`／`rclick` | `server_scripts/organ/item_right.js:2` = **ItemEvents.rightClicked** |
| `kubejs:damage_only`／`damage` | `startup_scripts/entity_hurt.js:6`（ForgeEvents.onEvent(**LivingHurtEvent**)）→ `server_scripts/event_stream.js:17` → `organ/player_damage.js:8` |
| `kubejs:key_pressed` | `organ/key_bind.js:2` = `NetworkEvents.dataReceived('ogran_key_pressed')` |
| `kubejs:active_only`／`active` | `organ/active_effect.js:25`（開胸腔激活，唔係玩家動作，本單唔 emit） |
| `kubejs:break_only`／`break` | `organ/block_broken.js:2` = `BlockEvents.broken` |
| `kubejs:eat_effect_only`／`eat_effect` | `organ/food_eaten.js:2` = `ItemEvents.foodEaten` |
| `kubejs:spellcast_only` | `mfdlc/organ/mfdlc_spellcast.js:1` = `PlayerEvents.spellOnCast` |
| `kubejs:player_tick_only` | `organ/player_tick.js:2` = `PlayerEvents.tick` |

（`kubejs:food` 唔屬以上族；真消費者 6 處：`hpdlc_playerticks.js:366`／`mfdlc_organ_active.js:368`／`mrqx_food_eaten.js:12,21,44`／`organ/active_effect.js:427`。）
**handler 兩種寫法**：①物件表（286 表／160 含 organ key／1173 key）②直接賦值 `STRATEGY['ns:id'] = function(event, organ…)`。
**gate 三類**：TABLE_KEY／BODY_CHECK（白名單三形）／NONE（負對照 believe、pandora）。**heldItem**：dominant-branch 5 形。

### A_hard 10（1-based；觸發 label＝官方顯示名規則輸出）

| outId | rel | sink | 觸發（label） | label 來源 | 前置器官 | 手持 |
|---|---|---|---|---|---|---|
| `b_a_d:vodka` | `b_a_d_rclick.js` | 87 | 右鍵 | 固定字串 | `b_a_d:keg`＝发酵桶 | 玻璃瓶 @79 |
| `b_a_d:mead` | 同上 | 91 | 右鍵 | 同上 | 同上 | 同上 |
| `b_a_d:rice_wine` | 同上 | 95 | 右鍵 | 同上 | 同上 | 同上 |
| `b_a_d:egg_grog` | 同上 | 99 | 右鍵 | 同上 | 同上 | 同上 |
| `b_a_d:eyeball_sacrifice` | `b_a_d_player_damage.js` | 2280 | 玩家攻擊命中 | 固定字串 | `b_a_d:taiji`＝子 | `b_a_d:hammer` @2272 |
| `b_a_d:sliced_meat` | 同上 | 2294 | 玩家攻擊命中 | 同上 | 同上 | `b_a_d:slice_knife` @2284 |
| `b_a_d:picture` | `effect/mob_effect.js` | 737 | **狼死亡** | vanilla `entity.minecraft.wolf`＝狼（**I18n**，唔係 OfficialDisplay） | `b_a_d:curse_letter_organ`＝链式信件（body） | offhand `biomancy:despoil_sickle` @736 |
| `alexsmobs:shrimp_fried_rice` | `item/b_a_d_item.js` | 497 | **营火右鍵** | vanilla `block.minecraft.campfire`＝营火 | `kubejs:mantis_shrimp_fist`＝螳螂虾拳（body） | `farmersdelight:cooked_rice` @495 |
| `golden_age:believe` | `golden_age/events.js` | 1043 | **邪异泥塑右鍵** | `kubejs/assets/golden_age/lang/zh_cn.json` → `block.golden_age.idol` | NONE | `golden_age:old_ones_heart_warp` @1038 |
| `ino_dlc_build:pandora_box_blue` | `ino_dlc_build_block_broken.js` | 7 及 20 | **潘多拉魔盒右鍵** | `kubejs/assets/ino_dlc_build/lang/zh_cn.json` → `block.ino_dlc_build.pandora_box` | NONE | site#1 方塊；site#2 `genius_full_bottle_blank` @14 |

⚠️ **R9 H5 更正（我自創／用錯名）**：舊寫「神像右鍵」＝**自創詞**（pack 全樹零「神像」；真名＝**邪异泥塑**）；舊寫「潘多拉嵌板右鍵」＝**用咗產出 item 名**（`item.ino_dlc_build.pandora_box_blue`＝潘多拉嵌板(蓝)），觸發**方塊**名係 **潘多拉魔盒**。兩者已入 fixture `trigger_zh_negative_controls`。

## 2. 設計

1. **掃描 pass**：`PackIndex.build` walk `:206-246` → `indexScriptItems(rel)` `:1568-1606` 末尾（唔可以放 `KubeJsMechanicScan`，400 檔上限 `:89`）。
2. **Parser** `logic/JsObtainSites.java`：
   ```java
   record Trigger(TriggerKind kind, String event, String dispatcherRel, int dispatcherLine, String organTag) {}
   record Site(String outId, Trigger trigger, String rel, int line, int entryLine, String cond,
               Kind kind, String organGate, GateKind gateKind, String heldItem, String heldSlot) {}
   enum TriggerKind { RIGHT_CLICK, HURT_BY_PLAYER, ENTITY_DEATH, BLOCK_RIGHT_CLICK }
   ```
   **fixture↔Java mapping（唯一真值）**：`kind`→`TriggerKind`；`event`→`event`；`dispatcher`（`檔:行`，可含 `→` 鏈→**只取鏈首**）→`dispatcherRel`／`dispatcherLine`；`organ_tag`→`organTag`；**`zh`／`zh_source`／`zh_rule`／`organ_tag_src`＝display-only／audit，唔入 Java record**。
   **`dispatcherRel` 路徑規則（R9 可選項）**：fixture `dispatcher` 帶 `kubejs/` 前綴（相對 instance）；Java `dispatcherRel` ＝ strip `kubejs/` 前綴（＝同 fixture `rel` 同基準）。
   `line`＝sink 行；`entryLine`＝handler 註冊行；1-based。
3. **索引**：`Map<String,List<Site>> jsObtainByOutput`；`build()` `:160-172` clear；唔准喺 `beginAskSession()` `:386-389` clear。
4. **可見性**：`acquireFactsDetailed` `:1259` 後、`:1265` 前 pre-pass 入 ranked（`RankedAcquire(2, seq++)`）；cap（`:1266`）只箍迴圈；`MAX_JS_OBTAIN_LINES=3`＝pre-pass 排序後頭 3。
5. 保險 branch（可選）：`:1379`。
6. **`AskEngine.graphLines` 唔加 branch**（死碼）。
7. **文字**：`ReplyLang.jsProduce/jsSwap/jsTransform`，簽名 `(code, outName, outId, triggerLabel, cond, src, organName, organId, heldName, heldId)`；
   `【腳本產出】<outName>（<outId>）｜觸發：<triggerLabel>｜前置器官：<organName>（<organId>）｜需要手持：<heldName>｜條件：<cond 原文>｜src:<rel>:<line>`
   **`triggerLabel` 單一生成規則（v6.11；runtime 同 harness 同一條函數）**：
   | TriggerKind | label | 顯示名解析通道 |
   |---|---|---|
   | RIGHT_CLICK | `右鍵`（固定） | 無 |
   | HURT_BY_PLAYER | `玩家攻擊命中`（固定） | 無 |
   | ENTITY_DEATH | `<實體顯示名>死亡` | **render 期 `I18n.get(EntityType.getDescriptionId())`**（例 `entity.minecraft.wolf`）；**唔准用 `OfficialDisplay`／`Registry.ITEM`**（R10 fact：`OfficialDisplay.hoverLookup` → `ItemResolver.stackFromId` → 只認 item，`minecraft:wolf` 解唔到）；失敗 → `<id>死亡` |
   | BLOCK_RIGHT_CLICK | `<方塊顯示名>右鍵` | `OfficialDisplay`（block item）；失敗 → `I18n.get("block.<ns>.<path>")`；再失敗 → `<id>右鍵` |
   - **禁用**：自創詞（pack lang 零命中，例「神像」）、用 item 名代替方塊名（例「潘多拉嵌板」）。
   - fixture `trigger.zh` ＝**該規則輸出**；fixture 另存 `zh_resolver ∈ {fixed, official_display_item_block, i18n_entity}`＋`zh_resolver_rule`／`zh_source`。**harness 同 runtime 用同一函數**（test hook 機制同 `OfficialDisplay.lookup`），**斷言唔准 hardcode 顯示名做唯一真值**（要 assert「label == 同一函數輸出」，無 lang 環境下 assert fallback 形）。organName／heldName 空 ⇒ 成段唔出；玩家名／器官名／held 名皆走 `OfficialDisplay`（器官／held 都係 item）。
8. **開關**：`PackAiConfig.JS_OBTAIN_CHANNEL`（預設 true）＋ `JS_OBTAIN_DIAG_LOG`；kill-switch 包三處。
9. **拆單**：單 1＝本 plan；單 2＝包文字通道（B 11 件）；單 3＝文件修正。

## 3. 驗收

S1–S15 同 v6.7–v6.9（索引／A_hard 10／trace／0.2／家族／busy focus／文字／`addItemCooldown`／抽樣 166／迴歸／TRANSFORM 18／gate／held／器官名／鑑別力）。

| # | artefact | 今日 | 實作後 |
|---|---|---|---|
| **S16** | **trigger 分家＋label 真值（v6.11）**：① 正斷言 6：vodka＝「右鍵」、taiji 系＝「玩家攻擊命中」、picture＝`entityLabel('minecraft:wolf')`（**同一函數**；有 lang ⇒「狼死亡」、無 lang ⇒「minecraft:wolf死亡」）、believe＝`blockLabel('golden_age:idol')`＋「右鍵」、pandora＝`blockLabel('ino_dlc_build:pandora_box')`＋「右鍵」、shrimp＝`blockLabel('minecraft:campfire')`＋「右鍵」——**斷言綁函數輸出，唔准 hardcode 顯示名**；② **負字串 8 條（fixture `trigger_zh_negative_controls`，唔准寫「等」）**：`发酵桶右鍵`／`發酵桶右鍵`／`器官右鍵`／`觸發：發酵桶`／`觸發：右鍵（手持`／`觸發：右鍵（器官`／`神像右鍵`／`潘多拉嵌板右鍵`；③ `triggerLabel ≠ organGate id／官方名`；④ 觸發段唔准含 held 名／id；⑤ ENTITY_DEATH 唔准行 `OfficialDisplay`（注入 stub 令 `OfficialDisplay` throw ⇒ label 仍要正確） | **紅** | harness：6 正 assert（綁函數）＋8 負字串＋3 結構 assert |

入口：`runAcquireFactsCheck`＋`runAskMechanicFactsCheck`（`-I tmp-check.gradle`，JDK 17）。

## 4. 回滾

新 class＋config×2＋pre-pass＋`ReplyLang`×3＋3 檔 lang＋`AskToolContext` 常數；索引 in-memory。`git revert` 單 commit；開關即時失效。

## 5. 未知／盲點

166 係掃描值；`cond` 只做字面；busy focus 未量；B 11 件 JEI 通道未證通；swap 家族可能跨 handler。
**器官／trigger**：48 dispatcher 未窮舉；`kubejs:food` 6 消費者未分類；`organ_gate_scan` 134/265 係 heuristic；held 抽取靠 dominant branch；`b_a_d:taiji` zh 名單字「子」；vanilla 名（狼／营火）靠遊戲 lang（唔喺 kubejs assets）。

## 6. Review 狀態

- **v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3 → R5 8:2 → R6 4:6 → R7 8:2 →（SK 糾正③）→ R8 5:5 → R9 6:4 → R10 5:5 → v6.11 → R11 進行中**。
- **R10 判定**：**H5 RESOLVED**（BLOCK label 改用官方名：邪异泥塑／潘多拉魔盒；S16 加 BLOCK 正斷言；8 條負控逐字對齊）；**H2 NOT-RESOLVED**＝新 fact：`OfficialDisplay.hoverLookup` → `ItemResolver.stackFromId` → **只認 `Registry.ITEM`**，`minecraft:wolf` 解唔到 → ENTITY_DEATH label 會變 `minecraft:wolf死亡`（S16 picture 斷言永紅／要 hardcode 假綠）。pack lang 亦零 `entity.minecraft.wolf`（lang 路徑唔同：`Language`／`EntityType.getDescriptionId()`）。
- **v6.11 修法**：① §2.7 label 表加**顯示名解析通道**欄——ENTITY_DEATH 走 **render 期 `I18n.get(EntityType.getDescriptionId())`**、明文禁 `OfficialDisplay`／`Registry.ITEM`；BLOCK 走 `OfficialDisplay`（block item）→ fallback `I18n.get("block.<ns>.<path>")` → `<id>右鍵`；ENTITY fallback ＝ `<id>死亡`；② fixture 加 `trigger_label_rules`＋逐件 `zh_resolver`／`zh_resolver_rule`；③ S16 正斷言改為**綁同一函數輸出**（`entityLabel(...)`／`blockLabel(...)`，唔准 hardcode 顯示名）＋新增第 ⑤ 條：stub `OfficialDisplay` throw 之下 ENTITY_DEATH label 仍要正確。
- **R9 premise errors（我錯）**：① 「神像」係自創詞（pack 零命中；真名 **邪异泥塑**）；② 「潘多拉嵌板」係**產出 item 名**，觸發**方塊**名係 **潘多拉魔盒**。
- ⚠️ 記錄：R5 輪 cursor 冇寫自己 report 檔（stdout 亂碼）→ R5 record 係 Hermes 重建（檔頭已標）。
