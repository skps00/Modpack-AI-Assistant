# Plan v6.7 — 腳本產出通道（code-only；gate 抽取規格已 pin；硬閘＝單一可重跑清單）

> 2026-09-17｜v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3 → **R5 正方 8 : 反方 2（達標）** → SK 09-17 糾正（器官 gate／配方交 JEI）→ v6.6 加 organGate → **R6 正方 4 : 反方 6（N1–N4）** → **v6.7 收 N1–N4**
> → **R7 正方 8 : 反方 2 → 達標（go=true）**；R7 只餘 2 個 LOW（P1 fixture `held_item_evidence` 殘留兄弟分支、P2 pandora slot 名）→ **已即場修（v6.7.2）**，唔需第 8 輪
> 狀態：**review 達標（正方 8 : 反方 2）→ 等 SK go 派 cursor 實作（單 1）**；開工前風險評估／還原點見 §4
> fixture（repo）：`docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json`（35.0 KB）

## 0. 目標（有界寫法）

玩家問「X 點取得」，而 KubeJS code 有寫產出 X 嘅路時，答案要講得出 **（a）觸發**（要裝住邊個器官／右鍵邊個方塊／打死邊隻怪）**（b）需要手持**（主／副手字面）**（c）條件字面**（機率）**（d）產出**。**fact 只由 code 讀**；**KubeJS 配方唔做**（SK 09-17 定案：JEI 已覆蓋）；JEI 信息頁／tooltip 文案唔准當 fact／判準／append。
承諾＝**A_hard 10 件硬閘**（含 organGate＋heldItem；下）＋索引能力範圍 166（抽樣 ≥20）；唔承諾 166 全數答得出。

## 1. 範圍（可重跑；fixture 已 commit）

掃 `<instance>/kubejs/{server,startup}_scripts/**/*.js`，排除 `/client_scripts/`、`/assets/`、`/data/`（本包實掃 **598** 檔）。
**handler taxonomy（構造式，唔硬編碼 mod 名）**：

| 家族 | 形狀（predicate） | gate 來源 |
|---|---|---|
| A. 事件註冊 | `XxxEvents.<event>(…)`：實測 **863** 個 call（`\b([A-Z]\w+Events)\.(\w+)\s*\(`；top：PlayerEvents.tick 151／ItemEvents.rightClicked 137／StartupEvents.registry 96／ServerEvents.recipes 73／ForgeEvents.onEvent 50／EntityEvents.death 40／BlockEvents.rightClicked 36／EntityEvents.hurt 31／NetworkEvents.dataReceived 10／ChestCavityEvents.* 3） | body-check |
| B. **物件表（addon 策略表）** | `^(const\|let\|var) NAME = {`（全部 **286** 張）；其中含 organ key 嘅表 **160** 張、key 合共 **1173** 個 | **key ＝ 器官 id** |
| C. 低階 Forge | `ForgeEvents.onEvent`／`ForgeModEvents.onEvent` | body-check＋event class |
| D. 其他 addon | `PlayerEvents.spellOnCast`／`ChestCavityEvents.updateOrganScore`／`ServerEvents.entityLootTables`… | body-check |

⚠️ 三個表數 predicate 唔同、唔可以併成一句：**286**（所有 `const/let/var NAME = {`）／**160**（內含 organ key 嘅表）／**1173**（該 160 表嘅 key 數）；另「`'ns:id': function(…)` 且**第二參數**含 `organ`」實測 **1926** 個（**首參數含 organ ＝ 0**，v6.6 寫錯，v6.7 修）。

**gate 兩類（兩者都要入 fact；冇 gate 就 NONE）**：
- **TABLE_KEY**：表 key ＝ 器官 id ⇒ 玩家**要先植入該器官**先觸發（例 `BADOrganRightClickedOnlyStrategies['b_a_d:keg']` @ `b_a_d_rclick.js:73`）。
- **BODY_CHECK**：**只認呢三形**（＋抽 id 字面）：`getPlayerChestCavityItemMap(...).has('<id>')`／`itemMap.has('<id>')`／`organScores.get(new ResourceLocation('chestcavity','<x>'))`。
  **唔准**當 gate：裸 `getChestCavityInstance()`、非 give 控制流嘅 `isEquippedCurio(...)`（負對照：`golden_age:believe` 有 instance@1016／curio@1020,1044，但 give@1043 只靠 mainHand＋`warpfree` ⇒ **必須判 NONE**）。
- **heldItem（v6.7 新）**：由**包住 sink 行嘅最近 if 條件字面**抽（dominant branch）；認 5 形：`item == '<id>'`／`getMainHandItem() == Item.of('<id>')`／`getOffHandItem() == '<id>'`／`mainitem?.id == '<id>'`／`BlockEvents.rightClicked('<block>')`（方塊觸發）。**唔准** ±N 行掃描（實測 vodka ±30 行會多吸 `minecraft:wither_skeleton_skull`@58／`graveyard:dark_iron_block`@62／`goety:cursed_metal_block`@66＝同 handler 其他分支）。

| 類 | 內容 | fixture 鍵 |
|---|---|---|
| **硬閘** | **A_hard 10 件**（純腳本產出、冇 recipe／loot／quest、非互換）＋逐件 `organ_gate`／`organ_gate_kind`／`held_item` | `A_hard_gate` |
| TRANSFORM 家族 | 18 站點＝`setStackInSlot` 2＋`setItemSlot` 16（10 outId） | `transform_family` |
| 排除（有 recipe／消耗代價） | `irons_spellbooks:silver_ring` | `transform_family_setSlot` |
| 排除（雙向互換） | `projecte:*` 5 件 | `swap_family_excluded` |
| 索引能力範圍（抽樣） | 166 件非 vanilla 產出（完整 id 陣列） | `ids_166` |
| 另一單（單 2） | B 11 件（`dream_*` 10＋`shattered_bubble`） | `B_group` |
| gate 掃描 | 265 個「字面 sink 出現嘅 id」中 134 個屬器官表（heuristic 上界）、160 表／1173 key | `organ_gate_scan` |
| 抽取器規則 | body-check 白名單、held 5 形、負對照 | `body_check_extractor`／`held_item_extractor` |
| 名對照 | fixture `organ_gate_kind ∈ {table-key,body-check,none}` ↔ Java `Site.gateKind ∈ {TABLE_KEY,BODY_CHECK,NONE}` | `gate_kind_mapping` |

### A_hard 10（v6.7：gate＋held 逐件實測；`sink`＝真 give 行；1-based）

| outId | rel | entry | sink | organGate（官方 zh） | **heldItem** | kind |
|---|---|---|---|---|---|---|
| `b_a_d:vodka` | `b_a_d_rclick.js` | 73 | **87** | `b_a_d:keg`＝发酵桶（table-key） | `minecraft:glass_bottle` @79 | organ-table |
| `b_a_d:mead` | 同上 | 73 | **91** | `b_a_d:keg`＝发酵桶 | 同上 | organ-table |
| `b_a_d:rice_wine` | 同上 | 73 | **95** | `b_a_d:keg`＝发酵桶 | 同上 | organ-table |
| `b_a_d:egg_grog` | 同上 | 73 | **99** | `b_a_d:keg`＝发酵桶 | 同上 | organ-table |
| `b_a_d:eyeball_sacrifice` | `b_a_d_player_damage.js` | 2267 | **2280** | `b_a_d:taiji`＝子（table-key） | `b_a_d:hammer` @2272 | organ-table |
| `b_a_d:sliced_meat` | 同上 | 2267 | **2294** | `b_a_d:taiji`＝子 | `b_a_d:slice_knife` @2284 | organ-table |
| `b_a_d:picture` | `effect/mob_effect.js` | 731 | **737** | `b_a_d:curse_letter_organ`＝链式信件（body-check @736） | offhand `biomancy:despoil_sickle` @736 | vanilla-event |
| `alexsmobs:shrimp_fried_rice` | `item/b_a_d_item.js` | 493 | **497** | `kubejs:mantis_shrimp_fist`＝螳螂虾拳（body-check @495） | `farmersdelight:cooked_rice` @495 | vanilla-event |
| `golden_age:believe` | `golden_age/events.js` | 1014 | **1043** | **NONE**（負對照） | `golden_age:old_ones_heart_warp` @1038 | vanilla-event |
| `ino_dlc_build:pandora_box_blue` | `ino_dlc_build_block_broken.js` | 1 | **7 及 20** | **NONE**（負對照） | `ino_dlc_build:pandora_box` @1（site#2 → `genius_full_bottle_blank` @14） | vanilla-event |

**「冇其他通道」已機械核實**：10 件 `js_recipe_defs=0`；正對照 `silver_ring` → `recipes/common.js:197`。

防洗版：每 focus 最多 **3** 條；只喺取得意圖（`wantsFullAcquire`）出；三種 label 分開。

## 2. 設計

1. **掃描 pass**：`PackIndex.build` walk `:206-246` → `indexScriptItems(rel)` `:1568-1606` 末尾。**唔可以**放 `KubeJsMechanicScan`（400 檔上限 `:89`；本包 598 檔）。
2. **Parser** `logic/JsObtainSites.java`：`record Site(outId, triggerId, mechanism, rel, line, entryLine, cond, kind, organGate, gateKind, heldItem, heldSlot)`；`kind ∈ {PRODUCE, SWAP, TRANSFORM}`；`gateKind ∈ {TABLE_KEY, BODY_CHECK, NONE}`；純函數、fixture 可測。
   - **行號**：`line` ＝ sink 行（fact `src:` 印呢個）；`entryLine` ＝ handler／事件註冊行；1-based。
   - **organGate**：TABLE_KEY＝**key 本身**（表宣告＋**參數列表含 `organ`**，本包係第 2 個參數）；BODY_CHECK＝白名單三形之一嘅 id 字面；其餘 NONE。
   - **heldItem／heldSlot**：dominant branch 5 形；`heldSlot ∈ {mainhand, offhand, trigger_block}`。
   - **`cond` 字面（≤40 字）**：`%`／`概率|機率|几率`／`Math.random\(\)`／`random\s*[<>]=?\s*[0-9.]+`／`\.age\s*%\s*[0-9]+`／`hasEffect\(|hasNBT\(|isUnderWater\(|getDifficulty\(|persistentData`／（body-check 已另欄，唔重複入 cond）。唔解釋、唔自創動詞。
3. **索引**：`Map<String,List<Site>> jsObtainByOutput`；`build()` `:160-172` clear；**唔准**喺 `beginAskSession()` `:386-389` clear。
4. **可見性（強制 admission）**：`acquireFactsDetailed` `:1259` ranked 建構後、`:1265` 迴圈前 pre-pass 入 ranked（`RankedAcquire(2, seq++)`）；cap（`:1266` `ranked+cycles>=12`）只箍迴圈；`MAX_JS_OBTAIN_LINES=3` 截斷點＝pre-pass 內排序後取頭 3。
5. 保險 branch（可選）：`:1379` 加 `-[js_produce|js_swap|js_transform]->`。
6. **`AskEngine.graphLines` 唔加 branch**（死碼）：`retrieve()` `:270` 先拍 snapshot，`acquireFactsDetailed` `:287` 後跑；宣告喺 `:469`。
7. **文字**：`ReplyLang.jsProduce/jsSwap/jsTransform`，簽名 `(code, outName, outId, triggerName, triggerId, cond, src, organName, organId, heldName, heldId)`；模板
   `【腳本產出】<outName>（<outId>）｜觸發：<triggerName>｜前置器官：<organName>（<organId>）｜需要手持：<heldName>｜條件：<cond 原文>｜src:<rel>:<line>`
   **空欄行為（v6.7 pin）**：organGate＝NONE ⇒ **成段唔出**（唔出「前置器官：—」）；heldItem＝null ⇒ 同樣唔出；對應 Java `jsProduce(code, …, null organ, null held)` 要由 ReplyLang 內部分支組字，唔准出空標籤。玩家名走 `OfficialDisplay`；器官名走同一 lang 通道（`item.<ns>.<path>`；缺名／壞檔就出 id，唔自創）。
8. **開關**：`PackAiConfig.JS_OBTAIN_CHANNEL`（`jsObtainChannel=true`）＋ `JS_OBTAIN_DIAG_LOG`；kill-switch 包三處。
9. **拆單**：單 1＝本 plan；單 2＝包文字通道（B 11 件）；單 3＝文件修正。

## 3. 驗收

| # | artefact | 今日 | 實作後 |
|---|---|---|---|
| S1 | 索引 site（讀真 code） | 紅 | `runAcquireFactsCheck` 真 snippet → assert site／`src:` ＝ fixture `sink_lines`／`kind`／`entryLine` |
| S2 | **A_hard 10 件**逐件出 `-[js_produce]->`（唔含 silver_ring／god_bless；pandora 兩個 site） | 紅 | 10 件 assert；負控 `minecraft:stone`、註解 id → 0 site |
| S3 | 真機 trace 含新 label（smoke：vodka／sliced_meat／god_bless） | 紅 | grep trace |
| S4 | vodka 行含 `0.2`（真 code `b_a_d_rclick.js:82`） | 紅 | harness assert |
| S5 | 4 家族 parser 能力 | 紅 | fixture |
| S6 | busy focus 仍見 js 行；開關關 → 0 行 | — | cap fixture |
| S7 | 文字：機械式＋官方名＋無自創動詞 | — | 白名單動詞比對 |
| S8 | `addItemCooldown` 唔出 site | 紅 | 真句 fixture |
| S9 | 抽樣 `ids_166` ≥20 件出 js 行 | 紅 | fixture |
| S10 | 迴歸：其他取得行 byte-identical | — | 開／關對跑 |
| S11 | TRANSFORM 18 站點 → kind=TRANSFORM、零 `js_produce`（正對照 vodka give） | 紅 | 18 站點 assert |
| **S12** | **TABLE_KEY gate**：vodka／mead／rice_wine／egg_grog 行必含 **`b_a_d:keg` 或「发酵桶」**＋**「玻璃瓶」** | **紅** | 4 件 assert；負控：剝走 gate ⇒ 必紅 |
| **S13** | taiji 系 2 件含 `b_a_d:taiji`／「子」＋**`b_a_d:hammer`／`b_a_d:slice_knife`**；picture 含「链式信件」＋**offhand `biomancy:despoil_sickle`**；shrimp 含「螳螂虾拳」＋**`farmersdelight:cooked_rice`**；believe 含 **`golden_age:old_ones_heart_warp`** | **紅** | 逐件 assert（held 唔准缺；同 S12 對齊） |
| **S14** | 器官名解析：4 個 gate id 出到 zh_cn 名；**負對照**：BOM 檔 `golden_age_tetra/lang/zh_cn.json`、壞 JSON `ino_dlc_build/lang/zh_cn.json:543`、缺鍵 → 回 id 唔 crash | 紅 | harness＋3 負對照（zh_cn 檔數 predicate 寫明：`glob(assets/**/lang/zh_cn.json)`） |
| **S15** | **gate／held 抽取器鑑別力**：① 首參數 organ＝0 命中之下，keg／taiji 兩個 table-key 仍要抽到；② `golden_age:believe`／`pandora_box_blue` → `gateKind=NONE`（負控）；③ held 抽取唔准吸入兄弟分支（vodka 只准 `minecraft:glass_bottle`；負控用 fixture `A_hard_gate[*].held_item_sibling_traps`＝`wither_skeleton_skull`@58／`dark_iron_block`@62／`goety:cursed_metal_block`@66） | **紅** | harness 3 組 assert（真 snippet） |

入口：`runAcquireFactsCheck`＋`runAskMechanicFactsCheck`（`-I tmp-check.gradle`，JDK 17）。

## 4. 回滾

新 class＋config×2＋pre-pass＋ReplyLang×3＋3 檔 lang＋`AskToolContext` 常數；索引 in-memory。`git revert` 單 commit；開關即時失效。

## 5. 未知／盲點

166 係掃描值（變數 id／動態註冊／body >6000 字未覆蓋）；`cond` 只做字面；busy focus 分佈未量；B 11 件 JEI 通道未證通；swap 家族可能跨 handler。
**gate**：134/265 係 heuristic 上界（未逐個手核；「最近表 key」可能跨表誤配）；addon 家族只覆蓋實測 863 個 call（**其他 addon 未窮舉**）；held 抽取靠 dominant-branch（用真 snippet 測，兄弟分支係已知陷阱）；`b_a_d:taiji` 嘅 zh_cn 名係單字「子」（疑 pack lang 未寫完，用 id 兜底）。

## 6. Review 狀態

- v5.5 線：R1 2:8 → … → R7 → SK 改 code-first。
- **v6 線：R1 3:7 → R2 5:5 → R3 6:4 → R4 7:3 → R5 正方 8 : 反方 2（達標）→ SK 09-17 糾正 → R6 正方 4 : 反方 6**。
- **R6 捉到（全部我已親核）**：**N1 HIGH** §2.2「函數首參數含 organ」＝假前提（實測首 0／第二 1926）→ 照做 table-key 全死；**N2 HIGH** body-check 認裸 `getChestCavityInstance`／`isEquippedCurio` → `believe` 被誤標（真 code :1016／:1020／:1044）；**N3 MED** heldItem 寫成必備但 fixture／S13 冇 pin ⇒ 假綠；**N4 LOW** 「286 表／1173 key」係兩套 predicate 混寫＋lang 檔數漂移。
- **v6.7 修法**：① N1 → §1「參數列表含 organ（本包第 2 個參數）」＋S15①；② N2 → body-check 白名單三形＋明確排除＋believe／pandora 負控＋S15②；③ N3 → fixture `held_item`（人手核實 dominant branch，附 `line`／`cond_predicate`）＋S12／S13 加 held assert＋S15③；④ N4 → stats 三拆（286／160／1173）＋lang predicate 寫明＋S14 負對照；另欄位名統一（`organ_gate_kind`↔`gateKind`、`handler_table`、`heldItem`／`heldSlot`）＋模板空欄行為 pin。
- **R7（有界輪，只核 N1–N4）：全部 RESOLVED → 正方 8 : 反方 2、go=true（達標）**。R7 只剩 2 個 LOW：**P1** fixture `held_item_evidence` 仍有 ±N 殘渣（同 S15③ 打架）→ 已把兄弟分支搬去新欄 `held_item_sibling_traps`（S15③ 改用佢做負控）；**P2** `pandora_box_blue.held_item.slot` 應係 `trigger_block`（唔係 mainhand）→ 已改，site#2 嘅 mainhand 另記 `held_item_site2`。另 R7 重跑 `second-param organ`＝1941（我量 1926）＝**語料漂移**，predicate 相同、結論不變。
- 開工前 checklist（SK 第一規則）：風險＝新增 class／config／3 檔 lang／`PackIndex` pre-pass（現有取得行可能被 JS 行擠出 cap）；最壞＝取得行被頂走或 lang 檔壞；還原＝**單 commit revert ＋ `jsObtainChannel=false` 即時失效**（索引 in-memory，無資料檔），閘＝`runAcquireFactsCheck`＋全量 `tests/check_*.py` 對 baseline（今日 113 PASS／2 FAIL，只准零新增紅）。
- ⚠️ 記錄：R5 輪 cursor 冇寫自己 report 檔（stdout 亂碼）→ R5 record 係 Hermes 重建（檔頭已標）。
