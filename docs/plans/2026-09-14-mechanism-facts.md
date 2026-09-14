# 2026-09-14 — 機制事實層（Mechanism Facts）計畫 v1

> SK 需求（2026-09-14 10:4x–11:0x）：
> 1. 「右鍵／長按會開介面做嘢」——**唔止 KubeJS，部分 mod 都有同類功能**，AI 要知。
> 2. 「有啲物品要**佩戴住**（或**用手上嗰件**擊殺）才會掉」——AI 要答得到（mod 同 KubeJS 都會出現）。
> 3. **M1 指示**：先查／學 KubeJS 點運作、點讀。
> 4. SK 提議：寫死喺 mod Java 嘅機制，**可否由 FTB Quests 文字查**？
>
> 狀態：**計畫（未實作）**，等 SK 批批次。範圍：只改 `forge/1.19.2`。
> 相關既有機制：`JeiInfoFacts`（JEI info＋KubeJS `JEIEvents.information` → `via:jei_info`）、`TooltipCapture`（含 Shift 隱藏行，`AskService` L580 已餵 focus item）、`LootForwardIndex`（loot table／gateways，未抽條件語意）、`PackIndex`（已把 `kubejs/`、`config/ftbquests` 列為 index root）、`CuriosBridge`（只喺 picker 用）、`ItemConsumeUseFacts`（只掃 datapack `minecraft:consume_item`）、`HonestMiss`。

## 0. 實證（SK 兩個例子，親自查）

- `mrqx_extra_pack:page_of_future`：**長按右鍵**行為寫喺 `kubejs/server_scripts/mrqx_extra_pack/mrqx_common/mrqx_events.js`（`PlayerEvents.tick` ＋ `player.getUseItem()` ＋ `player.ticksUsingItem` → 顯示「未來提示 N/4」）。→ 屬「KubeJS server script 機制」。
- `momo_dlc:t-02-99`（空虛之夢，Curios Feet）：掉落寫喺 `kubejs/server_scripts/momo_dlc/entity/momo_dlc_entity_death.js`（`EntityEvents.death('minecraft:skeleton'|'villager'|'zombie'…)` → 檢查 `player.nbt.ForgeCaps['curios:inventory']` 含 `momo_dlc:t-02-99` → `Math.random()*Math.random()*100 <= 1` → `player.give('momo_dlc:dream_bone')`）。**同時** `kubejs/client_scripts/momo_dlc/momo_dlc_jei.js` 用 `JEIEvents.information` 寫明文字（「攜帶T-02-99擊殺凋靈骷髏1%概率獲得」）。→ 屬「KubeJS server script 機制」＋「JEI info 文字」。
- FTB Quests：`config/ftbquests/quests/chapters/momodlc.snbt` **真係有 `t-02-99` 文字** → SK 提議成立（任務描述係機制文件來源）。

## 1. KubeJS 點運作（M1 研究結果，來源：kubejs.com/wiki/events、wiki.latvian.dev List of Events）

- **三個資料夾＝三種生命週期**：`startup_scripts/`（開機一次；註冊／改物品屬性）、`server_scripts/`（世界行為；`/reload` 生效）、`client_scripts/`（每客戶端；`F3+T`；JEI／tooltip）。
- **兩種寫法**：新式 `ItemEvents.rightClicked('ns:id', e => {…})`；舊式 `onEvent('item.right_click', e => {…})`（老 pack 仍常見）。
- **要覆蓋嘅事件類別**（同本需求相關）：
  - 使用行為：`ItemEvents.rightClicked`／`firstRightClicked`／`clientRightClicked`／`entityInteracted`／`dropped`／`foodEaten`／`canPickUp`；`BlockEvents.rightClicked`；`PlayerEvents.tick`（長按／`getUseItem`／`ticksUsingItem` 型）
  - 條件掉落：`EntityEvents.death`／`hurt`；`ServerEvents.entityLootTables`／`genericLootTables`／`chestLootTables`；`ItemEvents.crafted`／`smelted`
  - 說明文字：`JEIEvents.information`（= 已支援）、`ItemEvents.tooltip`／`modifyTooltips`／`dynamicTooltips`
  - 佩戴／屬性：`ItemEvents.modification`（改 maxStackSize／食物等）、`StartupEvents.registry`（自訂物品）
- **條件常見寫法**（掃描器要識）：`event.player.mainHandItem.id`／`offHandItem`／`player.curios` 或 `player.nbt.ForgeCaps['curios:inventory']`／`player.hasEffect(...)`／`player.ticksUsingItem`／`event.entity.type`／`event.block.id`／`event.level.dimension`。
- **效果常見寫法**：`player.give(...)`／`event.entity.drop(...)`／`player.addEffect(...)`／`player.tell(...)`／`player.openMenu(...)`／`player.runCommand(...)`／`level.spawnLightning(...)`。
- **機率寫法**：`Math.random() * Math.random() * 100 <= 1`（1%）、`Math.random() < 0.01`、`chance`／`weight` 欄位、文字「1%概率」。

## 2. 計畫（分批）

### 批一（建議先做）
- **M1 `KubeJsMechanicScan.java`**（新）：掃 `kubejs/{startup,server,client}_scripts/**.js`：
  1. 認兩種註冊語法＋事件名（上表）；
  2. 由第一參數抽 item / mob / block id（支援 `'ns:id'`、陣列、`#tag`）；
  3. **平衡大括號**抽 handler body（唔可以用正則一刀切）；
  4. body 內抽：條件（上列）／效果（上列）／機率（上列）／字面文字（`Text.black('…')` 等）；
  5. 產生雙向 fact：
     - `item:X -[use]-> 長按右鍵：<文字/效果>`
     - `item:X -[wear]-> 佩戴時（Curios <slot>）：<文字>`
     - `item:X -[drops]-> item:Y 當 擊殺:<mob> 且 條件:<…> 機率:<n>%`
  6. 未認得嘅 handler → 只記「有腳本處理此物品（檔案:行）」＋原文 clip（誠實，唔作結論）。
- **M5 驗收**：harness fixtures（4 個真實例子：`page_of_future`／`t-02-99`／`wizard_water_ring_dragon`／血肉掠夺者）＋真機問同一批物品（harness 可離線跑，唔靠遊戲）。
- **M6 `QuestMechanicFacts.java`**（新）：由 `config/ftbquests/quests/**/*.snbt` 抽 `description`／`title`／`tasks` 內提到 item id 嘅文字 → `item:X -[quest_text]-> <描述>`；`PackIndex` 已 index 此目錄，只差 deterministic 抽取＋注入。
- **M4 誠實缺漏**：三個來源都冇 → `HonestMiss`：「本包冇列出（可能寫死喺 mod 代碼）」。

### 批二
- **M2 loot table 條件語意**：擴充 `LootForwardIndex`——抽 `type: match_tool`／`entity_properties`（含 `random_chance`）／`killed_by_player` → `item:Y -[drops_from]-> mob:<id> 需 手持/攜帶 <tool>`；覆蓋 jar 內 loot table＋kubejs `ServerEvents.*LootTables`。
- **M3 佩戴效果**：新 `WornEffectFacts.java`——(a) `ItemStack.getAttributeModifiers(EquipmentSlot)`；(b) Curios 槽（`CuriosBridgeImpl` 已可讀 stack）加成；(c) tooltip 含「佩戴／装备时／worn／戴上／攜帶」字樣行 → `item:X -[worn]-> +1880 最大法力值 …`。

## 3. 風險／限制（要老實講）

- **寫死 Java 且冇任何文件**（tooltip／JEI info／loot table／腳本／任務文字全部冇）→ 永遠查唔到；只能用 HonestMiss。**反編譯**係最後手段（SK 規則）。
- KubeJS 腳本可以任意複雜（函數封裝、動態 id、`global.*` 表）→ 掃描器只保證「literal / 常見 pattern」；其餘標「有腳本處理，未自動解讀」＋原文 clip（唔作猜測）。
- 掃描成本：`kubejs/` 可能幾百 KB～數 MB 文本 → 只掃 `*_scripts/**`，加 mtime／hash cache（同 `PackIndex` 一致做法）。
- 大量 fact 會撐爆 prompt → 只注入「被問物品」相關 fact（既有 `AskPurposeContext`／facts 機制），每物品上限 N 行。

## 4. 驗收（做完成點）

1. harness：`AskMechanicFactsCheck`（4 個真實例子斷言關鍵字：`長按右鍵`／`curios:inventory`＋`t-02-99`／`1%`／屬性加成）＋負向控制（一個寫死 Java 物品要行 HonestMiss 路徑，唔准亂作）。
2. python 鏡像 check（`tests/check_mechanic_facts.py`）：關鍵 pattern 清單＋「唔准刪 assert」。
3. 真機：問 `page_of_future 怎麼用`／`空虛之夢 點嚟`／戒指「佩戴有咩效果」／血肉掠夺者「點樣掉戰利品」→ 答案要引到來源（腳本檔名／JEI info／任務描述），查唔到嘅要明講。
4. 兩次 code review（pass1 重構／pass2 三個月後脆弱位）。
