# Plan β — v5.0（API-first；SK 方向：KubeJS API 可取處全部走 API）

> 由來：R4（v4.3 最後一輪）＝ **正方 6 : 反方 4**（兩個 reviewer 一致）。反方判語最致命一條：
> **靜態解析 recipe 構造 = 382/895 條方向反轉**（把 `Ingredient.of(...)` 當產出），Sink B owner 73/217 無解 → 呢部分唔可以靠 text parsing。
> 而 SK 指嘅 **KubeJS API 路線正好剔除呢部分**：recipe 經 `event.custom(...)` 入 live RecipeManager，用 by-output 查詢就有正確方向（JEI 卡本身就帶 `role=input/output`）✓

## 0. 設計原則（一句）
**Recipe 類 → API；只有「唔係 recipe 嘅 JS 副作用」才解析腳本。**（可解析 surface 由 895 條 site → 5 個 site）

## 1. 兩條通道

### 1.1 API 通道（recipe 類；包無關）
- **現成**：`JeiRecipeCards`（逐 category `createRecipeLookup(type)` ＋ `JeiFocusMatch.roleMatchesFocus(role=OUTPUT/INPUT)`）已做 by-output 查詢 ✓（真 trace 有 `[card:2] 召唤祭坛 … role=uses` 實證）
- **要加**：JEI 冇 category 嘅 recipe type（如 `goety:ritual` 等自訂 type）→ **RecipeManager sweep**：
  - `level.getRecipeManager()` 枚舉（client 有 `mc.level`；`JeiRecipeCards.java:774/820` 已有同類用法）
  - 輸出抽取：`Recipe#getResultItem(RegistryAccess)`；**自訂 type 可能返 EMPTY** → fallback：該 type 有冇 `result` 欄位（經 `toNetwork`/codec）→ 抽唔到就 **只 log 不抽**（唔好估）
  - **Permanent diagnostic log**（SK 規則：要實錘）：`PackAI recipe-sweep types=<N> recipes=<M> withResult=<K> empty=<E> focus=<id> hits=<H>`；每 type 一行。開一次遊戲就有 coverage 數據，決定 API 夠唔夠
- **方向**：API 自帶方向（output vs input role）→ **唔存在 v4.3 嘅方向反轉問題** ✓
- **已知限制（要寫入 docs）**：JEI/RecipeManager 都睇唔到嘅 runtime 效果 → 交去 1.2

### 1.2 JS 副作用通道（解析；窄）
- 規則 P-A（唯一保留嘅解析規則）：`setStackInSlot(<any>, Item.of('<out>'))` 型事件效果
  - 全 pack 實測：`setStackInSlot` **5 次／4 檔**，其中 **2 個係真 sink**（`curios/entity_death.js:26` 產出 `kubejs:god_bless_full_necklace`、`curios/entity_hurt.js:36` 產出 `irons_spellbooks:silver_ring`）；其餘 3 個（`new ItemStack(key,max)`／變數）按規則正確丟棄
  - **來源 key 要由「外層物件定義」取，唔係命中 statement**（反方指正：key 喺 :22、命中喺 :26）→ 解析時要回溯外層 map literal
  - 動態 key／spread：本包 **0 實例** → **明文聲明「本包已證、其他包未證」**（唔可以寫「換 pack 都成立」）
  - 方向閘：edge 方向必須同 **JEI 卡 role** 交叉核對（對唔上 → 唔出 edge，log 一行）
- 索引：`jsEffectSitesByOutput: outId → [(rel,line)]`（由 ingest 同一 pass 建；**唔靠 `inverted`**（120 id 上限有損）、唔爭 10-rel 名額）→ ask 期 O(1)
  - 成本：反方實測 python 全掃 649 檔 = **5.5–19.2 ms**（零額外 IO；只加一次 regex pass）✓ 可接受

## 2. 驗收（S1–S7；每條都要「今日會紅」）
| # | 斷言 | 今日會紅？ |
|---|---|---|
| S1 | harness：P-A fixture（真 snippet）→ 出 `item:kubejs:god_bless_full_necklace -[transform]-> from:kubejs:god_bless_empty_necklace src:curios/entity_death.js:26`；負對照（silver_ring 唔可以令 `kubejs:friend_to_the_end` 變可取得）；**方向同 JEI 卡 role 一致** | 紅（新 edge 唔存在） |
| S2 | API 通道：focus = 满溢神恩项链 → sweep log 出 `hits>=1`（若 0 → 記 `api_gap`，唔准靜默）；且**唔可以**再由 P-A 規則出 recipe edge | 紅 |
| S3 | 真機 trace：answer 內出現**新 render label**（`ReplyLang` 同一函式輸出比對；pack／語言無關）；raw marker 只做 debug，**唔做斷言** | 紅 |
| S4 | 冇 kubejs 目錄／冇 JEI 嘅 pack：唔准 crash、唔准出錯 edge（graceful） | 紅（未實作） |
| S5 | caps：站點／檔案大小上限（config）＋超限只 log 唔爆 | 紅 |
| S6 | 站點唯一性：全 pack `Item.of('kubejs:god_bless_full_necklace')` = 2 處（`:26` 真、`goety_ritual.js:142` 屬 recipe 側 → 由 API 覆蓋）→ 新增 site 唔可以靜靜綠 | 紅 |
| S7 | **API coverage 閘**：`recipe-sweep` 統計要入 trace；若 `withResult==0` 或 `empty>0` 要出 loud note（唔准靜默） | 紅 |

## 3. 開關／兼容（多 pack）
- kill-switch：`packai-server.toml`（toml-only，唔郁 Settings／lang）＋ `jsEffectSites` 同 `recipeSweep` 兩個獨立開關
- parser 版本：`jsEffectSites` 建索引時寫 `version`；版本變 → 索引重建（cache salt）
- 範圍：只掃 `kubejs/**/*.js`（`Files::isRegularFile` 已過濾目錄）；大檔（>400 KB）／讀唔到 → skip + log

## 4. 我親核過嘅事實（寫落 plan 唔靠估）
- `registerCustomRecipe` = 包 glue（`b_a_d/recipe/goety_ritual.js:32`）→ 內部 `event.custom(...)` = **KubeJS 官方 API** ✓
- 所以 recipe 全部喺 live RecipeManager ✓（唔使解析）
- `JeiRecipeCards` 已有 by-output 查詢（`createRecipeLookup` + `roleMatchesFocus(OUTPUT)`）✓
- 反方 R4 實測：sink 分類 895 條中 recipe 類 890 條（B 217 + C 566+）→ 全部交 API；P-A 只剩 5 個 site ✓

## 5. 還原
- 純新增（新索引、新 label、新 log）＋ toml 開關；revert = `git revert`（檔案已 track）＋清 KubeJS mechanic cache
- 驗證還原：`AskMarkerIntegrityCheck`（新 label 含 `-[…]->`）＋ 115 check 全綠

## 6. R1–R4 比分（歷史）
R1 2:8／3:7 → R2 5:5 → R3 6:4／6:4 → R4 6:4／6:4 → **本檔改變設計路線（API-first）→ 屬新方案，重開 review 預算（上限 3–4 輪）**
