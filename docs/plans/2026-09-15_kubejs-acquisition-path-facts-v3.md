# KubeJS 取得途徑 facts — plan **v3**（R1 反方結果修正版）

> R1 兩個反方（deleg_3ddb0c87）：**v1 §5 正方 3 : 反方 7**／**v2 架構 正方 5 : 反方 5**／**v1 §6-9（整合）正方 2 : 反方 8** → 全部未達 8:2。
> v1、v2 §5→§9 **作廢**。以下係逐條修正後嘅 v3（第 3 輪＝上限前最後一輪，未達標即停手問 SK）。

---

## 0. 重大更正（我自己嘅錯，必須記錄）

| 我先前嘅講法 | 真相（實錘） |
|---|---|
| 「② Goety 儀式**合成**產出满溢神恩项链」 | **方向相反**：`goety_ritual.js:1` 簽名 `GoetyRitualRecipe(craftType, ingredients, activation_item, output)`、`:7 this.result = output` → line 142 第 3 個 `Item.of('kubejs:god_bless_full_necklace')` 係 **activation_item（被消耗）**，真正產出係第 4 個 `Item.of('gateways:gate_pearl')`。即係**滿溢項鍊係祭品**，唔係產物 |
| （同上類）`weapon_infusion.js:14` | `WeaponInfusionRecipe(base, addition, output)` → 第一個 `Item.of` 係 **base（被消耗）**，唔係產出 |
| `summoning_rituals.js:435-439` | `.altar('kubejs:god_bless_full_necklace')` = **催化／消耗**；`.itemOutput(...empty...)` 係產出（空項鍊） |

**满溢神恩项链（full）真正取得途徑（核實後）**：
1. **充能（唯一）**：`server_scripts/curios/entity_death.js:21-27` — `const curiosDeathStrategies = { 'kubejs:god_bless_empty_necklace': function(...) { … curios.setStackInSlot(slot, Item.of('kubejs:god_bless_full_necklace')); } }` → 戴住**空**項鍊打死 **4 王之一**（虛空之花／暗夜巫師／黑曜巨石柱／下界鐵掌）→ 變身
2. **空項鍊來源**：`kubejs/data/dimdungeons/loot_tables/chests/chestloot_2.json`（1 個 `kubejs:god_bless_empty_necklace`）→ **已有 loot 索引覆蓋**；JEI info（lang `:857`）＋ tooltip（lang `:1093`）都講充能

→ 即係：**AI 答「查唔到任何途徑」確實係錯**（充能途徑存在，連 tooltip 都寫住），但**「3 條合成途徑」係我講錯**（其中 2 條係消耗）。

---

## 1. 目標（一句）

令答案層可以講出「**只喺整合包腳本出現**嘅取得途徑」，每條附 `file:line` 證據；**方向錯／機制未分類 → 一律唔講**（寧願 miss）。

## 2. R1 反方逼出嘅硬性修正（v3 全部採納）

### 2.1 抽取層（site 掃描）
- **一定要喺檔案層掃**，唔可以只下降白名單 handler body：旗艦個案係 top-level `const map = { 'id': function(){} }`（`entity_death.js:21-30`），現有 `KubeJsMechanicScan.parseHandlers` 永遠見唔到。
- **參數位置（argument position）才係方向嘅唯一來源**：需要由 call 名開始做**括號平衡掃描**，數出每個 item literal 係第幾個 arg（支援 `[...]` 陣列、`{...}` 物件、`'16x ns:id'`、`{count:n,item:id}`）。
- **禁止**任何「statement 內第一個 `Item.of` = 產出」式規則（R1 已證 2/3 錯）。

### 2.2 角色分類表（mechanism → arg role）
- 必須逐 API 標明參數語意，並附 **真例 `file:line`**；未標 → `unclassified` → **永不輸出**。
- 已知必須入表（R1 提供嘅實例）：
  | API | 參數語意 |
  |---|---|
  | `GoetyRitualRecipe(craftType, ingredients, activation_item, output)` | arg1=材料、**arg2=消耗(activation)**、arg3=產出 |
  | `WeaponInfusionRecipe(base, addition, output)` | **arg0=消耗(base)**、arg1=材料、arg2=產出 |
  | `BioForgingRecipe([{count,item}], output)` | arg0=材料、arg1=產出 |
  | `.altar(X)` | **一律 CONSUMES**（取消 v1「除非另有 itemOutput」嘅 carve-out——真案例會出假途徑） |
  | `.itemOutput(X)` / `.itemInput(X)` | 產出／材料（`.itemInput(` 本 pack 0 命中；祭壇材料寫法係 `.input('16x ns:id')`） |
  | `curios.setStackInSlot(slot, Item.of(Y))` 喺 key = X 嘅 strategy 物件內 | **TRANSFORM：X=消耗、Y=產出**（`entity_death.js:21-27` 為標準例） |
  | `LootEntry.of` / `pool.addItem` / `ore.addTarget` / `allthemods.add` / `create.*` | 待逐個核（ATM10 等 pack 嘅真例）；未核＝unclassified |
- **`#tag` 材料唔准直接印**（違反 SK 官方顯示名規則）；`'16x ns:id'`／`{count,item}` 要解析數量，保留唔到數量要明講「（數量未明）」。

### 2.3 facts 出口（fail-safe 真守得住）
- **方向過濾**：新 fact 只喺 **focus id 落喺產出側** 時輸出；單純「被提及」唔算（否則材料會被講成可取得）。
- 出口走現有 `factsFrom`，但要**自己**做 key 去重（`kind+from+to+out+evidence`）——**R1 證 `honestMerge` 完全冇去重**（`KubeJsMechanicScan:560-571` 純 addAll），v1 寫「已有去重語意」係事實錯誤。
- **上限**：新增具名 per-kind 上限（每 item 腳本 fact 數、每 fact 材料數），**唔准**偷偷加大 `MAX_FACTS_PER_ITEM=8`；**先砌 evidence 後截材料**（現時 `clip(400)` 喺 sourceTag 之後 append，會截走 `file:line`）。
- 文字標籤（`from=`／`to=`／`consumes`）要同步入 `OfficialDisplay.annotatableNamespace` 拒絕名單（182-191）＋ `AskReplyScrub` 清洗規則，否則會被半標註成官方名。
- 材料 id 要排除出 `collectPeers`（145-159），否則會出假「同 tag 其他成員」行。

### 2.4 整合（**必須另審**，Phase 2）
- R1 證：KubeJS facts 現時只流入「**怎麼用／HOW TO USE**」段（`AskService:775-779 → withItemBehavior → purposeTooltip → AskEngine:549-559`）；而 SK 見到嘅「未找到產出它的配方…無法確認」係 `acquire` 段空 → **`HonestMiss.shouldPinAcquireMiss`（:22-38）釘出**，佢**睇唔到 KubeJS facts**。
- ⇒ 要令旗艦個案答對，**必須改 answer layer**：令 `AskEngine` 建 acquire（~445-470）／HonestMiss 知道「本品有腳本途徑」。呢個係**另一個更貴嘅改動**（要同步 `check_ask_purpose_context.py` 鏡像閘）→ **拆做 Phase 2，單獨 plan＋單獨 review**。

### 2.5 還原／cache
- cache 路徑：`<gameDir>/config/packai/mechanic-cache/`（＋`index.json`，`KubeJsMechanicScan:180/303`）→ 寫入 plan 落地紀錄。
- revert 要**連 schemaVersion 升**（`:85`）或者**刪 cache 目錄**，否則新 kind 嘅 stale fact 會被沿用；`KubeJsApiBridge` snapshot 係記憶體、reload 重建（`:458/478`）。

---

## 3. 分階段（令 review 可行、成本可控）

**Phase 1（本 plan 主體）**：抽取層 + 角色表 + facts 出口（唔碰 answer layer）
- 交付：`KubeJsMechanicScan` 新 kind、角色表（由 `docs/kubejs-mechanism-coverage.json` 402 候選生成骨架）、per-kind 上限、去重、S1/S2 閘
- 可觀察效果：debug log／trace 見到新 facts（例如 `[kubejs] TRANSFORM empty→full evidence=…:26`）

**Phase 2（另寫 plan）**：acquire 段整合（HonestMiss 唔再釘「無法確認」）
- 前置：Phase 1 已完成且真機見到 facts；Phase 2 需另過 8:2

## 4. 驗收（可證偽）

- **S1 harness**：指名真 class＋註冊位（照 `AskMechanicFactsCheck` ＋ `tests/check_mechanic_facts.py:55-56` 模式）；fixture 必含 ① 檔案層 map 個案（`entity_death.js:21-30`）② 多 `Item.of` statement（Goety／WeaponInfusion／BioForging）③ 多行鏈式 builder（`summoning_rituals.js:434-440`）
- **S2 負對照（8 條，全部要紅→綠）**：
  1. `.altar(X)` ＋ 同 statement `.itemOutput(Y)` → X 必須 **CONSUMES**（唔准當取得）
  2. `WeaponInfusionRecipe` arg0／`GoetyRitualRecipe` arg2 → 唔准當產出
  3. 只有 `{count,item}` 物件材料 → 唔准出「冇材料」嘅空 fact
  4. `Ingredient.of('#tag')` → 唔准印 raw tag id
  5. 未分類機制（例如 `typeMap.has`）→ **零 fact**
  6. 除掉角色表一行（例如 `curios.setStackInSlot`）→ 閘必須紅
  7. `LootJS` 出現但 PackIndex 唔覆蓋 → 老實 unknown，唔准砌假途徑
  8. 一件「只被消耗」嘅物品（＝滿溢項鍊本身！）→ 答案／facts **唔可以**講成可取得
- **S3 真機**（Phase 2 完成後才可能全綠）：問「满溢神恩项链」→ 必須含「充能 + 4 王名」；**唔准**含「未找到產出它的配方」／「無法確認」；負對照：問一件唔受影響嘅物品 → 新 kind fact 數 = 0
- **S4 閘**：新閘要**鏡像 Java 抽取邏輯**（照 `tests/check_kubejs_universal_scan.py:130-174`）而唔係 token grep；`tests/` 係共用目錄 → 要自帶 forge 路徑判定；`check_ask_display_leak.py` 需真 Prism log
- **S5 回歸**：現有 115 檢查全綠；`check_mechanic_facts.py`、`check_kubejs_universal_scan.py` 唔可以放寬（只准加斷言）

## 5. 最壞情況／還原

- **最壞**：角色表錯 → 講出唔存在嘅取得途徑（比「唔知」更差）。緩解：方向過濾＋未分類即靜默＋8 條負對照＋真機負對照。
- **還原**：`git revert` 一個 commit（抽取＋facts 出口都喺同一 commit 內）＋刪 `config/packai/mechanic-cache/`（或 schemaVersion 升）；answer layer 未改（Phase 2 獨立）。

## 6. 未解（要 R2 答／或要 SK 定）

1. 402 候選入面，**邊啲真係「產出側」**——要唔要 Phase 1 先只做「已核實嘅 8 個 API」（Goety／WeaponInfusion／BioForging／DragonForge／MomoCooking／altar／itemOutput／setStackInSlot-map）＋其餘標 unclassified？
2. Phase 1 完成但 Phase 2 未做時，玩家**仍然會見到「無法確認」**（facts 只喺 debug log）→ 要唔要先講清楚（避免 SK 以為冇效）？
3. `allthemods.add`（ATM10，2850 站點）呢類高頻 API 嘅參數語意未核 → 係唔係要逐個 pack 核先入表（成本）。
