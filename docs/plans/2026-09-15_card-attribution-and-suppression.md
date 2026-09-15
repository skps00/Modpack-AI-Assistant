# 2026-09-15 — 卡片錯配 + 卡片缺失（SK 真機回報）

> 狀態：**未開工**。依 AGENTS.md 規則：plan → 反方 review 到 8:2 或 9:1 → 才派 cursor 實作。
> 全部物品名照官方顯示名（`§` 前綴係遊戲原文，照抄）。

## 0. 證據（今晚真機，非推測）

**觸發時間**：2026-09-15 17:18（`golden_age:infinity_sword_organ`）＋ 17:37（`tetra:modular_single`）。
**SK 兩張截圖**：
- organ 案：答案「怎么用 / 作為材料」段落下面顯示 `用作材料 : Crafting` 卡——卡內係「肾脏 + 治愈性原液 → 被净化的器官」，**唔係問嗰把劍**；而「怎麼來／取得」卡片**冇出現**。
- tetra 案：文字寫到「零件：」但**下面一張卡都冇**（section header 空）。

**Trace 證據（`packai/trace/ask-*.jsonl`）**

organ 案（`ask-20260915-171805-golden_age_infinity_sword_organ.jsonl`）：
```
check.cards  category="动力合成 · 23 slots · 动力合成器" primaryOutputId="golden_age:infinity_sword_organ"  reason="role=OUTPUT"  placement="catalog_collect"  ✓ 正確
check.cards  category="篡夺上帝的力量"                  primaryOutputId="golden_age:infinity_ring"          reason="role=OUTPUT"  placement="catalog_collect"  ← 另一件物品
check.cards  category="上帝方块"                        primaryOutputId="golden_age:infinity_sword_organ"  reason="role=OUTPUT"  placement="catalog_collect"  ✓
check.cards  category="Crafting"                        primaryOutputId="chestcavity:appendix"             reason="role=INPUT"   placement="catalog_collect"  ← 另一件物品
check.cards  category="Crafting"                        primaryOutputId="golden_age:instable_tumor"        reason="role=INPUT"   placement="catalog_collect"  ← 另一件物品
check.cards  category="Crafting"                        primaryOutputId="kubejs:organ_charm"               reason="role=INPUT"   placement="catalog_collect"  ← 另一件物品
render.cards.final  cardsOut=4  item="golden_age:infinity_sword_organ"  outputsSize=1
render.markers      recipe_card_markers=[]  emissionRefs=[]
```
**連模型自己都喺 reasoning 寫住**（trace `send.history` 原文）：
> 「the listed card "肾脏, 治愈性原液 → 被净化的器官" **doesn't include our item**. These are probably unrelated/mixed.」

tetra 案（`ask-20260915-173716-tetra_modular_single.jsonl` + `latest.log`）：
```
57:xx  Pack AI toolCards emission=2 cardsOut=2
57:xx  Pack AI frameCardsSuppressed item=tetra:modular_single n=2
render.cards.final  cardsOut=0      ← 2 張卡被剷光，只剩文字「零件：」
```

## 1. 缺陷 1：卡片「張冠李戴」（用作材料卡屬於另一件物品）

**機制（已定位到 code）**
1. 「用作材料」卡由 `JeiRecipeCards.fromVanillaCraftingUses()`（`:812-856`）產生；過濾條件係
   `JeiFocusMatch.craftingInputsAccept(recipe, focus)`（`:826`）→ 骨架 `:279-292`：
   **只要 recipe 任何一個 ingredient `test(focus)` 過就當 true**。
2. `JeiFocusMatch.ingredientMentions()`（`:315-324`）= `ingredient.test(focus)` → **tag ingredient 會中**：
   任何 `#...:organs` 之類**共用 tag**，都會令「肝脏／肾脏 → 被净化的器官」呢類**其他物品嘅配方**
   被當成焦點物品嘅「用作材料」卡。
3. 之後 `AskService.isAutoOutputLike()`（`:1825-1828`）**只睇 `promptRole()` 係唔係 output／quest**、
   `autoEmitCatalogCards()`（`:1481+`）接受 `c.isInputUse()` 時**冇再驗「卡入面真係有焦點物品」** → 錯卡直接被 emit。
4. 顯示層：卡片 group header 用 `用作材料 : Crafting`（`RecipeCard` 分類 + role），玩家睇到就係
   「呢把劍嘅材料卡」——但卡內容係另一件物品。

**未 100% 確定嘅一環（要實錘）**：到底係「tag 命中（技術上真，但無用）」抑或「ingredient 判斷本身錯」。
→ §4 修法含「加永久 debug log」一步，跑一次真機就分清（唔准靠估）。

## 2. 缺陷 2：正確卡片唔出（缺失）

兩個唔同成因，要分開修：
- **2a `tetra:modular_single`（組裝工具）**：卡片本來搵到（`render.cards` outputsSize=1、emission=2），
  但 `suppressModularFrameCards(cardFocus, emitted)`（`AskService:385`／`:2377`）見焦點係**模組化框架**
  就全部剷 → 0 張。設計原意係「唔好顯示空白框架自己嘅配方」，但**連零件／組裝卡都殺埋**。
- **2b organ 案**：catalog 明明有 `role=output`（動力合成）同 `role=quest`（上帝方块）兩張正確卡，
  最後 `cardsOut=4` 全部係**錯配嘅 input 卡**；正確嘅 output／quest 卡**冇出現** → emission 選卡次序／
   `autoEmitCatalogCards` 嘅 cap（`cap=4`、`maxUses=2`）令錯卡食晒配額（要 log 實錘，見 §4）。

## 3. 缺陷 3（外觀）：空 section header

tetra 案顯示「零件：」header 但下面冇卡 → 卡片 group 應該「冇卡就唔畫 header」（同一頁兩處：`零件`／`用作材料`）。

## 4. 修法方向（要 SK 揀；唔准未揀就派工）

| 選項 | 內容 | 好處 | 風險 |
|---|---|---|---|
| **F1（我建議）** | 「用作材料」卡**只接受 exact item 命中**：`ingredient` 內必須有**同一個 item id**（唔接受純 tag 命中）；tag-only 命中嘅卡→丟棄（或降級為文字一行「同類材料可用」） | 直接消除張冠李戴；語意清晰 | 會少一啲「技術上真」嘅卡（但 SK 已表明呢啲係錯卡） |
| F2 | 保留 tag 命中，但卡片標題改寫成「同類材料（#tag）」並排最後 | 保留資訊 | 玩家仍然要自己判斷，未解決「睇落係呢把劍嘅卡」 |
| F3 | emit 前加硬閘：**卡必須含焦點物品**（exact id）先准入任何 card list（output／input 都一樣） | 最穩、可 headless 斷言 | 要小心唔好殺埋 quest／multi-output 卡（見下） |
| F4（缺陷 2a） | `suppressModularFrameCards` 收窄：只剷「框架本身配方」卡；**零件／組裝類卡保留** | 修返 tetra「一張卡都冇」 | 要定義「零件卡」判定（`focusRole`＋category） |
| F5（缺陷 2b） | emission 選卡次序改成 **output/quest 優先、uses 補位**，並寫 log 列明每張卡（category／primaryOutputId／role） | 正確卡唔會再被錯卡搶配額 | 要訂 cap 政策（現 `cap=4`, `maxUses=2`） |
| F6（缺陷 3） | 空 group 唔畫 header | 外觀 | 低 |

**必做（唔屬於選項，係 SK 規則）**：加**永久 debug log**（`Pack AI cards emitted: N` ＋ 每張 `cat=… out=… role=… src=…`），
令下次任何卡片問題一睇 log 就有實錘（呼應 SK 2026-09-14 規則）。

## 5. 驗收（真機逐項）

1. 問「寰宇支配之剑」（`golden_age:infinity_sword_organ`）：**唔准**出現「肾脏＋治愈性原液 → 被净化的器官」類錯卡；
   要出**動力合成（→ 寰宇支配之剑）**同／或**上帝方块**取得卡。
2. 問「亚巴顿」（`tetra:modular_single`）：「零件：」下面**要有卡**（唔准空 header），或者索性唔出 header。
3. 其餘三個今晚已正常嘅 ask（`eccentrictome:tome`）**唔准退化**。
4. Headless：新增／擴充 harness 斷言「卡集合內每張卡要嘛 exact-含焦點物品，要嘛明確標示為 tag 同類」＋
   「空 group 唔輸出 header」。要交**紅→綠證明**（現有 code 餵今次真機數據要紅）。
5. `tests/check_*.py` 110 條**冇新增紅**（2026-09-15 實測 baseline 全綠）。

## 6. 風險／回滾

- 全部係 client 顯示層 + 選卡邏輯，**唔碰存檔／世界**；jar 回滾 = `%TEMP%\deploy_backup_*`（見 skill `minecraft-mod-jar-deploy`）。
- 最大風險：F5 改選卡次序會影響**所有** ask 嘅卡片組合 → 屬「行為改動」，要獨立一批＋真機逐條對比今晚 4 個案例。
- 切批建議：**B1**＝缺陷 3＋debug log（零風險）→ **B2**＝缺陷 1（F1/F3）→ **B3**＝缺陷 2a（F4）→ **B4**＝缺陷 2b（F5）。
