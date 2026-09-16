# Plan β — v5.0（API-first；SK 方向：KubeJS API 可取處全部走 API）

> 由來：R4（v4.3 最後一輪）＝ **正方 6 : 反方 4**（兩個 reviewer 一致）。反方判語最致命一條：
> **靜態解析 recipe 構造 = 382/895 條方向反轉**（把 `Ingredient.of(...)` 當產出），Sink B owner 73/217 無解 → 呢部分唔可以靠 text parsing。
> 而 SK 指嘅 **KubeJS API 路線正好剔除呢部分**：recipe 經 `event.custom(...)` 入 live RecipeManager，用 by-output 查詢就有正確方向（JEI 卡本身就帶 `role=input/output`）✓

## 0. 設計原則（一句）
**Recipe 類 → API；只有「唔係 recipe 嘅 JS 副作用」才解析腳本。**（可解析 surface 由 895 條 site → 5 個 site）

## 1. 兩條通道

> **§1.0 更正（v5.1，我自己核實）**：`goety_ritual.js:142` 嘅 `GoetyRitualRecipe(craftType, ingredients, activation_item, output)` —— 項鍊係**第 3 個參數＝激活材料（會被消耗）**，產出係 `gateways:gate_pearl`。
> ⇒ **項鍊本身冇任何 recipe 生產佢**（之前答案講「JEI 冇產出配方」係**對嘅**）；Goety 有 JEI plugin（`GoetyJeiPlugin`／`JeiRecipeTypes`）⇒ **KubeJS recipe 本身 JEI cover 到** ⇒ **RecipeManager sweep 取消**（只保留一條便宜診斷 log）。
> ⇒ 旗艦物品唯一取得途徑 = **JS 事件**（打死 BoMD boss 時空項鍊變滿項鍊）→ **§1.2 就係成個 feature 嘅主體**。

### 1.1 Recipe 類（**唔使做新嘢；用 JEI，唔用 sweep**）
- 覆蓋來源 = **現成 JEI layout role 讀取**（`JeiLookup.java:809 roleMatchesFocus`／`JeiRecipeCards.java:315-317`／`JeiInfoPages.java:99`）→ 呢個來源**已證明**對自訂 type 都見到產出（真 trace `check.cards`：`召唤祭坛 → kubejs:god_bless_empty_necklace`；`魔法仪式 · 黑暗祭坛` category 都在）✓
- ~~RecipeManager sweep~~ **取消（R5 反方 B1/B3 實證）**：
  - `Recipe#getResultItem(RegistryAccess)` **1.19.2 唔存在**（只有 `getResultItem()`；RegistryAccess overload 係 1.19.4+）→ 照字面寫會 compile 唔到
  - `summoningrituals` 嘅 `AltarRecipe.getResultItem()` **返 `ItemStack.EMPTY`**（javap -c 實證）→ sweep 對呢個最大自訂家族（**217 site／12 檔**）等於零收穫
  - 而 JEI **睇得到**呢啲 recipe ✓ → 所以正路係 JEI，唔係 sweep
- **誠實 coverage 聲明（唔准再寫「890 site 交咗俾 API」）**：
  - 已覆蓋 = **JEI 有 category 嘅 recipe 家族**（本包實例：goety ritual、summoning ritual/祭壇 ✓）
  - **未覆蓋** = JEI 冇 category 嘅 type（唔知有幾多）＋ `maodlc_key_pressed.js:288-291` 呢類非 recipe 副作用（除 Sink A 形狀以外嘅）
  - 要 sweep 嘅話：**先量度再決定**——診斷 log 出「JEI-invisible recipe type 清單＋每 type recipe 數」→ 有數才做，唔准當佢存在
- 診斷 log（cheap，唔做斷言）：`PackAI jei-output focus=<id> categories=<N> roleOutput=<K> roleInput=<M>`

### 1.2 JS 副作用通道（解析；窄）
- 規則 P-A（唯一保留嘅解析規則）：`setStackInSlot(<any>, Item.of('<out>'))` 型事件效果
  - 全 pack 實測：`setStackInSlot` **5 次／4 檔**，其中 **2 個係真 sink**（`curios/entity_death.js:26` 產出 `kubejs:god_bless_full_necklace`、`curios/entity_hurt.js:36` 產出 `irons_spellbooks:silver_ring`）；其餘 3 個（`new ItemStack(key,max)`／變數）按規則正確丟棄
  - **來源 key 要由「外層物件定義」取，唔係命中 statement**（反方指正：key 喺 :22、命中喺 :26）→ 解析時要回溯外層 map literal
  - 動態 key／spread：本包 **0 實例** → **明文聲明「本包已證、其他包未證」**（唔可以寫「換 pack 都成立」）
  - 方向閘：edge 方向必須同 **JEI 卡 role** 交叉核對（對唔上 → 唔出 edge，log 一行）
- 索引：`jsEffectSitesByOutput: outId → [(rel,line)]`（由 ingest 同一 pass 建；**唔靠 `inverted`**（120 id 上限有損）、唔爭 10-rel 名額）→ ask 期 O(1)
  - 成本：反方實測 python 全掃 649 檔 = **5.5–19.2 ms**（零額外 IO；只加一次 regex pass）✓ 可接受

## 2. 驗收（S1–S7；誠實標示邊條「今日會紅」、邊條係守門）
| # | 斷言 | 今日會紅？ |
|---|---|---|
| S1 | harness：P-A fixture（真 snippet）→ 出 `item:kubejs:god_bless_full_necklace -[transform]-> from:kubejs:god_bless_empty_necklace src:curios/entity_death.js:26`（條件：BoMD boss 死亡） | **紅**（edge 唔存在）✓ |
| S2 | **role 政策**：只准 `role=OUTPUT` 當取得途徑；`kubejs:god_bless_full_necklace` **唔可以被當成任何 recipe 嘅產出**（`:142` 係 `activation_item`、`result=gateways:gate_pearl`）→ harness 斷言「零個錯誤 OUTPUT claim」 | **紅** ✓（今日會出錯 claim／或零 claim） |
| S3 | 真機 trace：answer 內出現**新 render label**（同 `ReplyLang` 輸出逐字比對）；raw marker 只做 debug | **紅** ✓ |
| S4 | 冇 kubejs 目錄／空目錄／讀唔到檔：唔准 crash、唔准出錯 edge | 守門 |
| S5 | caps：站點／檔案大小上限（config）＋超限只 log 唔爆 | 守門 |
| S6 | **站點真相表**：全包 `Item.of('kubejs:god_bless_full_necklace')` = 2 處（**1 產出** `entity_death.js:26`、**1 激活** `goety_ritual.js:142`）＋ **4 處輸入側提及**（`peifang.js:219`、`lunasexrecipes.js:997`、`summoning_rituals.js:435`、`goety_ritual.js:142`）→ parser **只准**由 `:26` 出 edge | **紅** ✓ |
| S7 | **每家族實測 yield**：命名家族（goety ritual／summoning 祭壇）各要 ≥1 條 edge 或記錄 `family_gap`（唔可以只斷言「log 存在」） | **紅** ✓ |

## 3. 開關／兼容（多 pack）
- kill-switch：**`packai-client.toml`**（packai 只有 CLIENT spec：`PackAiMod.java:32`；R5 反方指正——唔係 server toml，唔郁 Settings／lang）→ `jsEffectSites`（唯一解析通道）＋ `diagLog`（診斷 log）兩個獨立開關
- 負對照（S2 配套）：`entity_hurt.js:36` 嘅 `silver_ring` 唔可以令 `kubejs:friend_to_the_end` 變可取得；**用 poisoned fixture 證明規則真會咬**（照 α 嘅 A/B 紅證據做法）
- parser 版本：`jsEffectSites` 建索引時寫 `version`；版本變 → 索引重建（cache salt）
- 範圍：只掃 `kubejs/**/*.js`（`Files::isRegularFile` 已過濾目錄）；大檔（>400 KB）／讀唔到 → skip + log

## 4. 我親核過嘅事實（寫落 plan 唔靠估）
- `registerCustomRecipe` = 包 glue（`b_a_d/recipe/goety_ritual.js:32`）→ 內部 `event.custom(...)` = **KubeJS 官方 API** ✓
- **`goety_ritual.js:142` 項鍊係 `activation_item`（第 3 參數、被消耗），產出係 `gateways:gate_pearl`** → 項鍊冇 recipe ✓（之前答案嘅「JEI 冇產出配方」係對嘅）
- **Goety 有 JEI plugin**（`Goety-2.34.2.jar`：`GoetyJeiPlugin`／`JeiRecipeTypes`／`CursedInfuserCategory`／`ModBrazierCategory`）→ KubeJS recipe 經 JEI 見到 ✓（SK 判語正確）
- `JeiRecipeCards` 已有 by-output 查詢（`createRecipeLookup` + `roleMatchesFocus(OUTPUT)`）✓
- KubeJS jar（`kubejs-forge-1902.6.2-build.73.jar`）有 `recipe/RecipesEventJS`、`recipe/JsonRecipeJS`、`server/ServerScriptManager`、`util/KubeJSPlugins`（如需 fallback 之用，但已取消）
- 全包 `Item.of('kubejs:god_bless_full_necklace')` = **2 處**：`curios/entity_death.js:26`（**產出側** ✓）＋ `goety_ritual.js:142`（**激活側** ✗）
- `setStackInSlot` 全包 5 次／4 檔；`Item.of` 型 sink 只有 2 個（`entity_death.js:26`、`entity_hurt.js:36`）；其餘 3 個係 `new ItemStack(key,max)`／變數 → 按規則正確丟棄

## 5. 還原
- 純新增（新索引、新 label、新 log）＋ toml 開關；revert = `git revert`（檔案已 track）＋清 KubeJS mechanic cache
- 驗證還原：`AskMarkerIntegrityCheck`（新 label 含 `-[…]->`）＋ 115 check 全綠

## 6. R1–R4 比分（歷史）
R1 2:8／3:7 → R2 5:5 → R3 6:4／6:4 → R4 6:4／6:4 → **本檔改變設計路線（API-first）→ 屬新方案，重開 review 預算（上限 3–4 輪）**
