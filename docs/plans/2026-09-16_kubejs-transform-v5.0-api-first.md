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

### 1.1 Recipe 類（**唔使做新嘢**）
- 覆蓋來源 = **現成 JEI by-output 查詢**（`JeiRecipeCards` 逐 category `createRecipeLookup` ＋ `JeiFocusMatch.roleMatchesFocus`）→ KubeJS 註冊嘅 recipe 一樣經 JEI 見到 ✓
- 只加**一條診斷 log**（cheap）：`PackAI jei-output focus=<id> categories=<N> hits=<H>` → 用嚟量 coverage，唔做斷言、唔建新通道
- ~~RecipeManager sweep~~、~~route B（JsonRecipeJS）~~ **取消**（SK 09-16：KubeJS recipe JEI cover 到）

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
| S2 | 負對照（有牙）：`entity_hurt.js:36` 嘅 `silver_ring` 唔可以令 `kubejs:friend_to_the_end` 變可取得；**用 poisoned fixture 證明規則真會咬**（照 α 嘅 A/B 紅證據做法） | 守門（今日 trivial pass）→ 靠 poisoned fixture 證有牙 |
| S3 | 真機 trace：answer 內出現**新 render label**（同 `ReplyLang` 輸出逐字比對）；raw marker 只做 debug | **紅** ✓ |
| S4 | 冇 kubejs 目錄／空目錄／讀唔到檔：唔准 crash、唔准出錯 edge | 守門 |
| S5 | caps：站點／檔案大小上限（config）＋超限只 log 唔爆 | 守門 |
| S6 | **站點真相**：`Item.of('kubejs:god_bless_full_necklace')` 全包 = 2 處，但**只有 1 個係產出側**（`entity_death.js:26`）；`goety_ritual.js:142` 係**激活側**（消耗）→ parser 唔准由 :142 出 edge | **紅**（今日冇 parser；加 fixture 可證）✓ |
| S7 | 診斷 log（JEI by-output `categories/hits`）入 trace，`hits==0` 要 loud note（唔准靜默） | **紅** ✓ |

## 3. 開關／兼容（多 pack）
- kill-switch：`packai-server.toml`（toml-only，唔郁 Settings／lang）→ `jsEffectSites`（唯一解析通道）＋ `diagLog`（診斷 log）兩個獨立開關
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
