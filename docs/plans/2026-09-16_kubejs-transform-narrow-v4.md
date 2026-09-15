# KubeJS 取得途徑 — **窄版 v4**（SK 2026-09-16 批准「1＋2」）

> 前置：一般化方案（v1–v3）已停手（R1 3:7／2:8、R2 5:5、R3 4:6／3:7）；研究結論（§8）＝只有「事件式轉換」係 viewer 覆蓋唔到嘅類別，所以只補呢一類。
> 本 plan ＝ **範圍收窄後嘅新 plan**（唔係第 4 輪 review），review 預算重新計（1–3 輪）。

## 1. 目標（一句）
令答案層可以講出「**由腳本事件轉換取得**」嘅途徑（例：戴住「神恩項鍊（空）」打死 boss → 變「满溢神恩项链」），**只認兩個 exact pattern**，其餘一律唔講；每個 edge 附 `file:line` 證據。

## 2. 為何唔需要改 answer layer（已核實）
- acquire 段本身**已經係由腳本＋圖邊建立**：`AskEngine:287` → `PackIndex.acquireFactsDetailed`（`PackIndex.java:1214`）→ 讀 `acquirePathsByItem`／`jeiInfoScriptRels` 嘅腳本檔 → `ingestAcquireEdges(rel, text, "fish"|"loot"|"trade")`（`:1677/1679/1681`）產生 `item:<id> -[label]-> …` 圖邊。
- 顯示層對**未認識嘅 label** 有 fallback：`ReplyLang.kindLabel`（`:1181-1203`）— `default -> packData(code)`（即「整合包資料」）。→ 新 edge 唔會被剝走、唔會爆。
- ⇒ **只要新途徑以同一種圖邊形式入 `acquireFactsDetailed` 嘅 bundle**，`acqShot`（`AskEngine:346`）→ HonestMiss 判斷（`:351/:458`）→ 顯示全部自動帶到。**Phase 2（改 answer layer 決策）唔需要。**
- 唯一可選嘅 cosmetic：喺 `kindLabel` switch 加 1 行（例如 `case "transform" -> transformKind(code)`）＋3 個語言檔各 1 條 key，令文字由「整合包資料」變「由…轉化」。**唔加都可以出**（走 generic fallback）。

## 3. 範圍（只做兩件事）
**P1 — 檔案層 map 變身**（旗艦個案）
```
const <anyName> = {
    'kubejs:source_item': function (event, curios, slot, item) {
        ...
        <recv>.setStackInSlot(slot, Item.of('kubejs:out_item' /*, NBT*/));
    },
}
```
→ edge：`item:<out_item> -[transform]-> 由「<source 官方顯示名>」於腳本事件轉換（<rel>:<line>）`
必要條件（全部要成立，否則零 fact）：
1. `const` 物件字面值，**property value 係 function**，key 係**單一 item id 字串**
2. body 內有 `<任何接收者>.setStackInSlot(<任何>, Item.of('<out>'))`（`Item.of` 第 2 個 arg 係 NBT/count → 唔理）
3. key id ≠ out id（自賦值唔算）
**必紅反例**（同形狀但唔係變換）：`utils/constdef.js:253 machineChestLootTable`／`:255 warpFoodMap`／`:274 tagWorth` → 零 fact（因為 value 唔係 function／body 冇 setStackInSlot）

**P2 — 鏈式祭壇產出**
```
event.recipes.summoningrituals .... .altar(X|.input(...)) ... .itemOutput(Item.of('out'))
```
→ edge：`item:<out> -[ritual]-> 祭壇儀式（<rel>:<line>）`；**同鏈嘅 `.altar(X)`／`.input(...)` 一律標 CONSUMES（唔可以為 X 出取得 edge）**
必要條件：同一 statement 鏈（接收者運算式到 `;`，容許註解行）內同時見到 `.itemOutput(` 同 `.altar(`/`.input(`；`Item.of('')`／`${…}` 動態 id／註解內 id 一律丟。

**另加（SK 批准嘅 2）— 修現有真 bug**：`collectPeers`（`OfficialDisplay:144-159`）掃成條 fact 文字 → 材料 id／`kubejs/…js:26` 路徑會變「同 tag 其他成員」漏落玩家畫面（真 trace `ask-20260915-230340-…` event[27]／[54] 已見）。修法：peer 掃描剔除 `evidence=`／`source:` 尾段同 `.js:<n>` 形 token；加斷言「peer 行唔准含 `.js` 路徑或 `js:<n>`」；**先寫紅（現況）再修到綠**。

## 4. 角色表（≤3 行，全部附真例）
| pattern | 角色 | 真例 |
|---|---|---|
| `const map = { 'srcId': function(){… setStackInSlot(slot, Item.of('outId')) } }` | srcId=CONSUMES、outId=OUTPUT | `server_scripts/curios/entity_death.js:21-27`、`entity_hurt.js:30-36` |
| 鏈式 `.itemOutput(out)` | out=OUTPUT | `server_scripts/b_a_d/ritual/summoning_rituals.js:434-440` |
| `.altar(X)` / `.input(...)`（同鏈） | CONSUMES（**永不出取得 edge**） | 同上 `:435` |

## 5. 落地（Phase 1 only，唔碰 answer layer 決策）
- `PackIndex`：新增 `ingestAcquireEdges(rel, text, "transform")` 一次呼叫（`PackIndex.java:1677` 附近）＋ 純函式 parser（可單元測試）；新 edge 加入現有 `acquirePathsByItem` / bundle lines。
- **唔改**：`AskEngine` acquire 決策、`HonestMiss`、`AskPurposeContext`、`AgentService` enrichFacts、`withItemBehavior`（＝R1 反方要求嘅「零 answer layer 改動」）。
- 上限：沿用現有 `MAX_FACTS`／`clip` 前先砌 evidence（`formatUse:1017-1023`／`formatDrop:1025-1037` 現時 evidence 最後 append 會被 400 字截走 → 新 edge 自己格式，evidence 固定放最前）。
- cache：改動 `PackIndex` 解析 → **revert／改動後要刪 `config/packai/mechanic-cache/`**（`kjs-*.json` key 冇 parser 版本；`INDEX_SCHEMA_VERSION` 只廢 `index.json`）。

## 6. 驗收（可證偽）
- **S1 harness**（Java，fixture = 真 snippet 字串）：P1 兩個真例出到正確 edge（方向、`file:line`）；P2 出到 `-[ritual]->` 而 `.altar(X)` 零取得 edge。
- **S2 負對照（9 條，全部要紅→綠）**：① `constdef.js:253/255/274` 三個 non-transform map → 零 fact ② 註解內 id（`constdef.js:60 yeti_alpha`）→ 零 fact ③ `${…}` 動態 id → 零 fact ④ `Item.of('')` → 零 fact ⑤ `Item.of('id','{NBT}')` 第 2 arg 唔准當 id ⑥ `WeaponInfusionRecipe` arg0／`GoetyRitualRecipe` arg2（消耗側）→ 零取得 edge ⑦ `event.addItem`／`tooltip.*`／`scene.world.setBlock` → 零 fact ⑧ `.id('ns:path')` → 零 fact ⑨ 剔除角色表一行 → 閘必須紅。
- **S3 真機**：問「满溢神恩项链」→ 必須講到「由神恩項鍊（空）於擊敗 boss 後轉換取得」＋`entity_death.js:26` 證據；**唔要求 4 王名**（entity 名解析唔做，明示限制）。負對照：問一件只被消耗嘅物品（如 `kubejs:heart_template`）→ 零 transform edge；問一件普通物品 → 新 edge 數 0。
- **S4 修 bug 驗收**：peer 行唔准含 `.js` 路徑／`js:<n>`（現況紅 → 修後綠）；`check_ask_display_leak.py` 綠。
- **S5 回歸**：現有 115 check 全綠；`check_mechanic_facts.py`／`check_kubejs_universal_scan.py` 只准加斷言。

## 7. 最壞情況／還原
- 最壞：誤配 → 講出唔存在嘅轉換途徑。緩解：兩個 pattern 都要**同時**滿足多個必要條件＋9 條負對照＋未分類靜默。
- 還原：`git revert`（單一 commit，含 harness＋gate）＋**刪 `config/packai/mechanic-cache/`**；answer layer 未改，唔需要額外 rollback。

## 8. 交付方式
- Code 一律經 **cursor-agent**（唔准 commit、唔准動其他未 commit 檔、唔准 hot-copy jar）。
- Hermes 親驗：compile／harness／115 check／負對照／diff 對 baseline（AGENTS.md 規則）。
