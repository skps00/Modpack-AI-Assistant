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
**⚠️ SK 2026-09-15 補充：有啲物品係靠 NBT 區分** → 「同一件物品」嘅判定**唔可以只比 item id**，
必須 **id 相同 ＋ 變體／NBT 相容**（repo 已有現成機制：`logic/ItemVariantKeys` 嘅
`hasVariantKeys()`／`preferTokens()`／`mentions()`／`schematics()`；`JeiFocusMatch` 其他地方已經用）。
→ §4 修法含「加永久 debug log」一步，跑一次真機就分清（唔准靠估）。

**配額數學（可以計得清）**：`autoEmitCatalogCards()`（`:1481-1567`）`cap=4`；先跑 output／quest pass
（接受 `isAutoOutputLike()`）：catalog 順序 = 動力合成(輸出=organ) → 篡夺上帝的力量(quest, 輸出=infinity_ring)
→ 上帝方块(quest, 輸出=organ) ＝ **3 張**；因為回覆有「怎么用／用作材料」段（`replyHasUsesSection`）
再跑 uses pass（`maxUses=2`），但 **cap 係共用** → 只剩 1 格 → 嗰格就係**錯配嘅 input 卡**
（`check.cards` 顯示 `role=INPUT`、`primaryOutputId=chestcavity:appendix` 等）→ `count=4`。
即係：**正確卡（3 張）理論上入咗 list，但錯卡亦佔咗第 4 格**；至於 4 張實際顯示喺邊個 section、
有冇被 dedupe／renderer 吞掉，**今日 log 冇記錄 → 未實錘**（見 §4 debug log）。

## 2. 缺陷 2：正確卡片唔出（缺失）

> **⚠️ R1 反方已用 log 否證咗本節初稿嘅機制，見 §7**（2b「錯卡食晒配額」＝錯；2a「suppress 殺咗零件卡」＝錯）。
> 以下保留原文只作對照，**實作一律以 §7 為準**。

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
| **F1（SK 2026-09-15 修正版；我建議）** | 「用作材料」卡只接受**同一件物品**命中：`item id 相同` **＋ 變體／NBT 相容**（用 `ItemVariantKeys.hasVariantKeys／preferTokens／mentions`；**唔可以只比 id**——SK 指出有啲物品靠 NBT 區分）。純 tag 命中（唔含同一 id）→ 丟棄，或降級為文字一行「同類材料（tag）」 | 直接消除張冠李戴；語意清晰；同 repo 現有 variant 機制一致 | 會少一啲「技術上真」嘅卡（SK 已表明呢啲係錯卡） |
| F2 | 保留 tag 命中，但卡片標題改寫成「同類材料（#tag）」並排最後 | 保留資訊 | 玩家仍要自己判斷，未解決「睇落係呢件嘢嘅卡」 |
| F3 | emit 前加硬閘：**卡必須含焦點物品（id ＋ 變體相容）**先准入任何 card list（output／input 都一樣） | 最穩、可 headless 斷言 | 要小心唔好殺埋 quest／multi-output 卡（見下） |
| F4（缺陷 2a，**SK 揀 a**） | `suppressModularFrameCards` 收窄：只剷「框架本身配方」卡；**零件／組裝類卡保留**（冇卡就唔畫 header） | 修返 tetra「一張卡都冇」 | 要定義「零件卡」判定（`focusRole`＋category） |
| F5（缺陷 2b，**暫緩**） | 改 emission 優先序／cap 政策 | — | **未實錘唔改**：output pass 本來已經先行，問題係共用 cap ＋ 錯卡入圍 → 先靠 debug log 睇清 4 張出咗乜／顯示喺邊，再定（唔盲改） |
| F6（缺陷 3，**SK 揀 a**） | 空 group 唔畫 header（同一頁兩處：`零件`／`用作材料`） | 外觀 | 低 |

**必做（唔屬於選項，係 SK 規則）**：加**永久 debug log**，每次 ask 出：
1. `Pack AI cards emitted=N` ＋ 每張一行 `#i cat=<分類> out=<primaryOutputId> role=<output/quest/input> src=<sourceItemId> section=<實際顯示位置：取得／怎麼來／零件／用作材料> ref=<[card:N] 或 auto>`
2. 被丟棄／被抑制嘅卡＋原因（例：`suppressed frame=… n=2`、`dropped tag-only n=1`）
3. **SK 2026-09-15 明確要求：log 要記錄「卡顯示喺邊」**（唔止出咗幾張）

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

---

## 7. R1 反方 review（2026-09-15）吸收 ＋ v3 修正

**比分**：整體 **反方 6:4**（未達 8:2 → B2–B4 唔可以照原 plan 出）；**B1 單獨 9:1（反方支持）** → B1 可開工。

### 7.1 我寫錯、已被 log 否證（誠實更正）

| 我原本寫 | 事實（反方實證，我已覆核） | 證據 |
|---|---|---|
| 2b「錯卡食晒配額，正確卡冇出」 | **錯**。emitted set 必然係〔动力合成→`golden_age:infinity_sword_organ`、篡夺上帝的力量（quest，primaryOutput=`golden_age:infinity_ring`）、上帝方块（quest，primaryOutput=organ）、Crafting（肾脏+治愈性原液→被净化的器官）〕＝**3 張正確 + 1 張錯**；output pass 本來已經先行（`AskService:1518-1528`），錯嗰張係**最後補嘅 uses**（`:1539-1560`，`isInputUse` 取 catalog 第 3 個）→ 正好對上 SK 截圖嘅 `用作材料 : Crafting` | `latest.log:7175 autoEmission role=output+uses count=4`；`AskService:1518-1560`；catalog 次序＝trace facts `[RECIPE_CARDS]` 行 0-5 |
| 2a「suppress 殺咗零件／組裝卡」 | **錯**。tetra 案嘅 catalog **由頭到尾係空**（`Pack AI recipe cards focus=tetra:modular_single count=0`；`JEI diag … role=OUTPUT … focusFail=1 focusFailTagOnly=1 focusOk=0`、INPUT 一樣），被剷嘅 2 張**正是框架自己嘅配方**（地穿器+2木棍→空白模组单件，`placement=tool_emit`）＝設計上本來就要剷。→ **F4 對本案係 no-op（B3 白做）** | 我親自 grep：`latest.log:7213`＋`17:37:18.845 focusFail=1 focusFailTagOnly=1` |
| 缺陷 3「group 空就唔畫 header」 | **前提可疑**：`零件：` 全 repo 只喺 `packai.screen.tool_parts`，唯一用家 `AiAssistantScreen:691-698 toolPartsStrip()`，而佢喺 `ModularToolScan.partItemStacks()` 空時 `return null` → 空 parts 理論上畫唔出該 header。**要先確認你截圖嗰個「零件：」係模型正文抑或 strip** → 列入 B1 嘅 log／實錘項 | 反方 grep；要真機重現 |

### 7.2 F1／F3 會殺死合法卡（必須改）

- **F1（丟 tag-only 命中）＝ over-kill，有實錘**：3 張「錯卡」其實係**器官類通用真配方**（`kubejs:organ` tag）：
  `recipes/common.js:171` 抗排异（ingredient = `Ingredient.of(['@chestcavity','#kubejs:organ'])`，output 被净化的器官）、
  `:73` organ_recycler、`dlc_recipe.js:20` chaos_tumor、`curios/charm_recipes.js:3` organ_charm 等；
  而焦點物品**本身就係 tag 成員**（`startup_scripts/golden_age/dlc_template_item_register.js:14 .tag('kubejs:organ')`）。
  → 真器官嘅「用作材料」段**本來就應該有呢啲卡**；**真正缺陷係顯示樣本**（JEI 擺咗 tag 另一個成員「腎脏」入格），唔係 attribution。
- **F3 實作陷阱**：唔可以用 `primaryOutputId == focus` 做閘（篡夺上帝的力量 `primaryOutputId=golden_age:infinity_ring`、`outputsSize=4`，焦點**係其中一個 output**）→ 必須用 **`outputs[]` 成員檢查**；input 卡要用 **grid／ingredient 成員**檢查（tag 成員算數），而且**唔可以靠 `Ingredient.getItems()`**（Forge `TagIngredient#getItems` 回 EMPTY）→ 要比 layout stack。

**→ v3 修法方向（取代 F1/F3）**：
- **F1'**：保留 tag 命中卡，但**顯示層要老實**——tag slot 用**焦點 stack 做樣本**（或標題標「同類材料（#tag）」），令玩家睇到嘅係自己問嗰件嘢。
- **F3'**：emit 前硬閘用「卡嘅 outputs[] 或 grid／ingredient 成員含焦點（id＋變體／NBT 相容）」。

### 7.3 驗收要加料（原 §5 太弱）

- §5 只點名 `eccentrictome:tome`（exact-id OUTPUT 路徑＝最唔受影響）；要加入**受影響路徑嘅真實案例集**（2026-09-14 trace 有紀錄）：
  `golden_age:infinity_sword_organ`(4／6 卡、8 個 uses 候選含 tag 卡)、`donut`(2)、`mrqx_extra_pack:page_of_future`(3–4)、
  `atomic_disassembler`(4)、`wizard_water_ring_dragon`(6)、`golden_age:archotech_void_ingot`(3)。
- **per-card log 欄位要加 `outputs[]` 同 `grid[]`**（否則睇唔到「卡內有冇焦點」，亦會誤判篡夺卡為錯卡）。
- 靜態 110 綠證明唔到選卡語意 → headless 斷言要由**卡物件 dump**驅動，唔可以只讀 trace count。

### 7.4 最貴未知（解開先可以評 B2–B4）

**17:18 organ ask 遊戲 UI 實際渲染咗邊 4 張卡、喺邊個 section**——trace 只有 `cardsOut=4`，冇 per-card 內容。
成本＝B1 log ＋ 重跑一次真機（或 SK 原截圖全圖）即可解開：
- (a) 若 4 張＝3 正確 + 1 錯 → **F5 刪**，2b 改成「uses 段要先取 exact-id 用途卡」；
- (b) 若 4 張全錯 → 7.1 第一行結論倒，F5 保留。

**批次（v3）**：**B1（log ＋ 外觀／`零件：` 路徑確認）＝ 9:1 可即做**；
**B2–B4 待 B1 收集 per-card 事實後重評**（每項都要再過 8:2）。
