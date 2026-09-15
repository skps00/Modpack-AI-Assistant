# KubeJS 取得途徑 facts — plan **v2**（SK 2026-09-15 23:2x：「there will be many way to craft or change, make sure it can check them all」）

> v1 嘅 3-pattern 設計（§5）**作廢**——實測本 pack 嘅機制數量遠超 3 個。以下是**由真 pack 掃出嚟嘅機制清單**（機械抽取，可重跑），同相應嘅**資料驅動**設計。
> 掃描工具（臨時）：`%TEMP%\kubejs_inventory.py`（讀 `AI_test_NFWC_DIM/minecraft/kubejs`，648 js／3673 json，幾秒跑完）→ 落地時要變成 repo 內 `tools/kubejs_mechanism_audit.py`（見 §12）。

## 10. 機制清單（本 pack 實測；唔係估）

| 類別 | 實測數字 | 例 |
|---|---|---|
| JS 檔 | **648**（server 445／startup 153／client 50） | — |
| JSON（recipes／loot_tables） | **3673**（`data/**/recipes` 147、`data/**/loot_tables` 56，其餘係 lang／tags／其他） | — |
| `Item.of('<id>')` 出現過嘅**不同物品** | **1723** | `lightmanscurrency:coin_gold` 57 次、`gateways:gate_pearl` 53、`minecraft:honey_bottle` 39… |
| **自訂 recipe class**（`new *Recipe(...)`） | **≥13 種**：`GoetyRitualRecipe` 170、`BioForgingRecipe` 131、`DragonForgeRecipe` 45、`MomoCookingRecipe` 39、`MixingCauldronRecipe` 27、`CookingRecipe` 26、`BioBrewingRecipe` 16、`mrqxGoetyRitualRecipe` 14、`WeaponInfusionRecipe` 9、`DecomposingRecipe` 7、`mrqxBioForgingRecipe` 6、`DigestingRecipe` 5、`BADCookingRecipe` … | 呢啲全部**唔經 vanilla recipe manager → JEI 見唔到**（除非 mod 自己寫 JEI plugin） |
| **事件家族**（`XxxEvents.yyy(`） | **25 種**：`PlayerEvents.tick` 152、`ItemEvents.rightClicked` 138、`StartupEvents.registry` 96、`ServerEvents.recipes` 73、`ForgeEvents.onEvent` 50、`EntityEvents.death` 40、`BlockEvents.rightClicked` 37、`EntityEvents.hurt` 31、`ServerEvents.highPriorityData` 26、`ItemEvents.foodEaten` 25、`ItemEvents.tooltip` 20、`ServerEvents.tags` 19、`BlockEvents.broken` 19、`EntityEvents.spawned` 17、`NetworkEvents.dataReceived` 16、`PlayerEvents.spellOnCast` 16、`PlayerEvents.loggedIn` 14、`ItemEvents.firstLeftClicked` 12、`JEIEvents.information` 11、`PlayerEvents.inventoryClosed` 9、`ClientEvents.tick` 8、`PlayerEvents.loggedOut` 8、`PlayerEvents.respawned` 7、`ForgeModEvents.onEvent` 6、`StartupEvents.init` 6 |
| **「產生／改變物品」API**（同 `Item.of` 同行） | `event.player.give(…)` 176、`block.popItem(…)` 40、`setItem(…)` 23、`add(…)` 16、`setStackInSlot(…)` 2、`addDrop` 4、`addItem(…)` 3、`player.setItemSlot` 15、`e.setMainHandItem` 8、`instance.inventory.setItem` 7、`TradeItem.of` 7、`event.addLoot` 14、`MysteriousItemConversionCategory.RECIPES.add` 14 … | 同一個「輸出」語意有**多種寫法** |
| **LootJS** | **15 個檔**（`LootJS.modifiers` 等） | `b_a_d_world_loot.js`、`common/loot.js`、`dlc_loot.js`、`golden_age/loot_sr.js`… |
| KubeJS recipe API（JEI 可見，勿重複） | `ServerEvents.recipes` 73 檔、`event.recipes.kubejs.shaped` 11、`event.recipes.create.mixing` 8 … | 呢啲 JEI **見到** → 唔准出重複 fact |

**結論**：3-pattern 必漏（起碼漏 13 種 recipe class、7 種 transform 寫法、LootJS 15 檔）。要 SK 講嘅「check them all」，唯一可行路線＝**API-agnostic site 收集 ＋ 可審計分類表 ＋ coverage 審計**。

## 11. 設計（v2：唔硬編碼機制名）

**S1｜Site 收集（API-agnostic，保證唔漏）**
對每個 JS／JSON 檔，搵**所有 item id 字面值**（`['"]#?[a-z0-9_]+:[a-z0-9_./-]+['"]`），每個 occurrence 記低：
- `mechanism` ＝ 最近嘅外層 call 名（`event.player.give`／`new GoetyRitualRecipe`／`block.popItem`／`MysteriousItemConversionCategory.RECIPES.add`…）＋（如有）所屬事件家族（`EntityEvents.death`…）
- `argIndex` ＝ 該 occurrence 喺該 call 嘅**第幾個 top-level 參數**
- `evidence` ＝ 檔名:行號 ＋ 截句 ≤200
- `fileKind` ＝ js／json-loot／json-recipe／lang

**S2｜方向分類表（可審計、可擴充；未知＝唔講）**
`mechanism → role`：`OUTPUT`（產出）／`INPUT`（材料）／`CONSUME`（消耗，例如 `.altar('<id>')`）／`TRANSFORM`（A→B，需 handler key）／`CONDITION`（條件比對，唔算途徑）／`REMOVE`（`event.remove` → 唔算）。
- **未知 mechanism → 標 `unclassified`，只入 log（`mechanic:unknown:<name>`）＋ coverage 報告；答案層永遠唔准用 unclassified 去講「取得途徑」**（fail-safe：miss > 假）。
- 分類表 seed 由 §10 inventory 產生（每條要寫「為何係呢個 role」＋一個真例 file:line）。

**S3｜Coverage 審計（「check them all」嘅機械保證）**
- `tools/kubejs_mechanism_audit.py`（新）：輸出 `docs/kubejs-mechanism-coverage.md`——**所有** 出現過嘅 mechanism × 次數 × 已分類？× 例子檔案；跑一次就可以睇到「有邊啲機制未分類」。
- 閘：`tests/check_kubejs_mechanism_coverage.py`（forge-only）——斷言 (a) 報告檔存在且非空 (b) 每個 `OUTPUT` mechanism 都有 ≥1 真例 (c) `unclassified` 數目有硬上限（例如 ≤20）＋每加一個新 mechanism 必須喺表內（負對照：刪一行 → 紅）。
- pack 更新／reload：沿用 `KubeJsApiBridge.invalidateOnReload`；審計可重跑 → 新機制自動現形。

**S4｜出口（沿用現有鏈，唔改簽名）**
`logic/KubeJsMechanicScan` 加新 fact kind（`SCRIPT_PATH`／`SCRIPT_TRANSFORM`／`SCRIPT_CONSUME`／`SCRIPT_UNKNOWN`）→ `AskService:754-776`（`ensureStart`→`factsForItem`→`honestMerge`）；文字一律經 `logic/OfficialDisplay` 出**官方顯示名**；上限沿用（`MAX_FACTS_PER_ITEM=8`、`MAX_CLIP=200`、cache 500 檔／5 MB）。

**S5｜保守規則（硬性，全部要有負對照）**
1. 先剝註解；**字串內** pattern 唔算（除 pattern 要嘅參數字串）。
2. `event.remove(`／`.remove(` → `REMOVE`，唔算取得。
3. `.altar('<id>')` → `CONSUME`（唔算取得，除非同 statement 另有 `itemOutput`）。
4. 冇 `Item.of`／`Ingredient.of` 上下文 → 丟（唔猜）。
5. `ServerEvents.recipes`／`event.recipes.*`（JEI 可見）→ **唔出 fact**（避免同 JEI 重複）。
6. LootJS 15 檔：先核 `PackIndex` 有冇覆蓋（v1 §4 假設「已覆蓋」未證）；冇就要一併處理。

## 12. 驗收（v2）

- **A1** `tools/kubejs_mechanism_audit.py` 跑真 pack → 產生報告（要含 §10 全部類別；報告要列出 unclassified 清單）。
- **A2** harness（fixture 字串，唔靠 MC）：≥ 8 個真 snippet（GoetyRitual／BioForging／DragonForge／`give(`／`popItem(`／`setStackInSlot(`／LootJS／`event.remove`）→ 期待 role ＋ evidence 行號。
- **A3** 負對照 ≥6：① 註解內 → 無 ② 字串內 → 無 ③ `event.remove` → REMOVE ④ 只有 `altar` → CONSUME ⑤ JEI 可見 recipe → 唔出 fact ⑥ 未知 mechanism → `unclassified` 且**答案層唔引用**（呢個係最貴嘅安全閘）。
- **A4** 真機：問「满溢神恩项链」→ 要出 ① 充能（打死 4 王之一）② Goety 儀式（材料＋靈魂 666／30 秒）；**負對照**問「寰宇支配之剑」（JEI 已有配方）→ 唔應多假 fact。
- **A5** 115 現有 check 全綠＋compile／harness 全綠；新閘只查 forge 樹（NeoForge PAUSED）。

## 13. 風險／還原（v2 補充）

- 新風險：**unclassified 太多** → 報告會嘈；處理＝硬上限＋每次 pack 更新後跑審計（人工 review 新機制，唔自動當答案）。
- 新風險：`give(`／`add(` 呢類**太常見**（`add(` 2850 次）→ 誤配成本高；規則＝必須**同 statement 內有 `Item.of('<id>')`** 才算 site，並且 role 靠分類表（唔靠猜）。
- 還原：純新增 fact kind（無資料遷移）；`git revert` ＋ 刪 cache 檔即可；審計報告係新檔，刪咗唔影響運行。
