# 2026-09-15 — 卡片錯配 + 卡片缺失（SK 真機回報）

> 狀態：**B1 已做**（永久 debug log ＋ `零件：` 路徑確認 log；F6 strip 空本已唔畫）。B2–B4 待 B1 真機 per-card 事實後重評。
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

---

## 8. B1 落地紀錄（2026-09-15 18:0x，Hermes 親驗）

- 實作（cursor）：新檔 `logic/AskCardsDebug.java`（218 行，純格式化，無行為）、`logic/AskCardsDebugCheck.java`（harness）、
  `tests/check_ask_cards_debug_log.py`（閘）；改 `AskService.java`（＋新增 `logCardsEmitted()`，4 個 call site）、
  `AiAssistantScreen.java`（＋ log block）。**只加 log，冇改選卡／顯示行為**（我逐 hunk 睇過 diff：`missingByIdentity()` 係純函數）。
- **我親自跑嘅驗證**：`compileJava compileTestJava` → BUILD SUCCESSFUL；`-I tmp-check.gradle runAskCardsDebugCheck`
  → 6 個子斷言 + `AskCardsDebugCheck OK`；`tests/check_*.py` → **111 檔全綠**（110 + 新增 1）。
- **jar**：`forge/1.19.2/build/libs/packai-0.2.1.jar` sha256 前 16 位 **`724d298aadb6be89`**（18:02，1,180,109 B）。
  ⚠️ 要收集 per-card 事實 → 一定要**部署呢個 build**（`mc_mod_deploy_jar.py --target packai`，需熄遊戲）。
- **負對照捉到閘嘅弱點（要修）**：我將真 logger 呼叫 `.info("Pack AI cards emitted={}")` 改成 `cardsX` 之後，
  `check_ask_cards_debug_log.py` **仍然綠** —— 因為 `assert "Pack AI cards emitted=" in svc` 撞正同一檔嘅
  **javadoc 註解**（`{@code Pack AI cards emitted=N}`）都含該字串。→ 下一個 cursor 批次要收緊成**精確呼叫形式**
  （例：`LOGGER.info("Pack AI cards emitted={}"`）並補 python 側紅→綠證明。探針已還原（md5 前 `a49edee1…` 一致）。

### 7.5 B1 落地（2026-09-15）

- 新 `AskCardsDebug`：`sectionLabel`／`formatEmittedLine`／`bodyHasPartsHeading`／`toolPartsPath`
- `AskService.logCardsEmitted`：4 條 ask 完成路徑（async/blocking × AI/KEYWORDS）→
  `Pack AI cards emitted=` ＋ `Pack AI card #i cat=… out=… role=… src=… section=… ref=… outputs=… grid=…` ＋
  `Pack AI cards suppressed reason=frame …`
- `AiAssistantScreen`：`Pack AI toolParts path=strip|body_text|none`（strip 空已唔畫；`body_text`＝模型寫咗「零件：」）
- Guard：`AskCardsDebugCheck`＋`tests/check_ask_cards_debug_log.py`
- **未做**：B2–B4；唔 scrub 模型正文「零件：」；真機重跑 organ／tetra 收 per-card 事實

---

## 8. 真機實錘（2026-09-15 20:25–20:30，jar `724d298aadb6`＝含 B1 log）＋ B2–B4 收斂

**三個 ask（SK 實跑，我讀 `latest.log` ＋ trace）**

| ask | catalog | emitted | 逐卡事實 |
|---|---|---|---|
| `golden_age:infinity_sword`（寰宇支配之剑） | `count=0` | **0 卡** | **正確**：真係冇配方（JEI `foundOutput=0`） |
| `golden_age:infinity_sword_organ` | `count=6` | **4 卡** | #0 `out=golden_age:infinity_sword_organ role=output src=organ section=取得` ✅；#1 quest（outputs 含 organ）✅；#2 quest（outputs 含 organ）✅；**#3 `role=input out=chestcavity:appendix`，`grid=[chestcavity:raw_rich_sausage, biomancy:healing_additive]`** ❌ |
| `tetra:modular_sword`（亚巴顿） | `count=0` | **1 卡** | #0 quest（outputs 含 modular_sword）合理；`suppressed reason=frame ×3`（frame 自己配方，設計要剷）；`toolParts path=strip stripDrawn=true` |

**#3 係鐵證（錯卡）**：卡片顯示嘅 grid **兩個樣本都唔係焦點物品**（sausage／healing additive），但因為某個 ingredient 係 **tag** 而 tag 內含焦點 → `ingredient.test(focus)` 為真 → 出卡。用戶睇到嘅係「一啲唔相關嘅嘢」，同 SK 講嘅「wrong cards」完全對上。
（`kubejs/server_scripts/curios/charm_recipes.js:14` `event.shapeless('chestcavity:appendix', ['kubejs:organ_charm'])` 係同 family 嘅器官相關配方；tag 名由 `Ingredient` 讀，實作時直接取。）

**收斂後嘅 B2–B4（本輪 R2 要再過 8:2）**
- **B2 硬閘（核心）**：`role=input/uses` 卡 → **焦點 id 必須出現喺卡片顯示用嘅 ingredient sample 集合**（id 級比對）；
  **只有 tag 命中**（焦點唔喺 samples）→ **唔出卡**，改出一行文字「**同類材料（#tag）**」（tag id 由 `Ingredient` 讀；樣本用**焦點 stack**）
- **B3 顯示樣本**：凡 tag slot 一律用**焦點 stack** 做樣本（唔准用 JEI 隨機成員）——即舊 F1'
- **B4 frame 抑制**：保留抑制（框架自己配方剷咗係設計），但**唔准出現空標題**：suppressed 後該 section 零卡 → 唔畫標題；
  零件資訊走 strip（已 `stripDrawn=true`）／文字，唔靠被剷嘅卡
- 驗收：真機重問三個 ask；`#3` 唔准再出卡（或降級成文字行）；`tetra` 唔准有空標題；`infinity_sword` 維持 0 卡但要有「查唔到配方」嘅文字交代（唔准靜默空白）

---

## 9. R2 反方吸收（v5）——**診斷翻轉**：錯卡係「顯示樣本」bug，唔係 attribution bug

**R2 比分**：B2＋B3 **反方 8 : 正方 2**（B3 判不可實作）、B4＋gate **反方 7 : 正方 3**。反方全部指控我**逐條對真 artifact 核實**，成立 → 原本 §8 嘅 B2／B3／B4 **全部推翻**，唔派工。

### 9.1 反方推翻咗我嘅診斷（已核實）

| 反方指控 | 我核實（真檔） | 結論 |
|---|---|---|
| **#3 其實 attribution 正確** | `kubejs/server_scripts/recipes/common.js:171`：`shapeless(appendix, [Ingredient.of(['@chestcavity','#kubejs:organ']), 'biomancy:healing_additive'])`；焦點真係 tag 成員：`startup_scripts/golden_age/dlc_template_item_register.js:14` `.tag('kubejs:organ')` ＋ `:422 registerOrgan('golden_age:infinity_sword_organ')` | **我錯**：「寰宇器官」**真係**「用作材料」卡嘅合法材料（+ 治療原液 → appendix）。B2 硬閘會**殺死一整族合法器官卡** |
| **B3 不可實作** | `RecipeCard` 欄位冇 ingredient／recipe provenance（`jeiLayout` 明文 opaque）；`AiAssistantScreen:1616-1619` 實際由 **JEI drawable** 畫格 → 改 card list 對有 jeiDrawable 嘅卡（即目標卡）**係 no-op** | **B3 廢**，改喺 **collector 層**修 |
| **樣本 bug 喺 vanilla 路徑** | `JeiRecipeLayoutCollector.firstItemInSlot(slot, prefer)` **已有 prefer**（`:238-252`：`isSameItemSameTags(stack,prefer) || stack.is(prefer.getItem())` → 回焦點）；但 `JeiRecipeCards.tryCrafting` 用 `firstOf(ingredient)` ＝ `getItems()[0]`（`:1491-1500`），**完全冇 prefer** | **真根因搵到**：#3 走 vanilla 路徑 → 樣本變 `chestcavity:raw_rich_sausage`（compound 第一個 value）→ 用戶睇到「唔相關嘅嘢」 |
| **B4 打空氣** | 卡片 caption 係**每卡自畫**（`appendRecipeCardCaption:1269-1322`，唯一呼叫點 `:903` 已 guard 空卡）→ 零卡＝零 caption，**根本冇「空標題」可唔畫**；玩家見到嘅「零件：」係**模型正文**（`isSectionHeader:1220-1234`），同卡 list **冇 data link**；tetra 真機證據 `stripDrawn=true partsEmpty=false`（`:5176`）＝caption 同非空 strip 一齊畫 | **B4 廢**（§8 §B4 撤回） |
| **「查唔到配方」文字** | 會成第 4 套機制，撞 `HonestMiss.shouldPinAcquireMiss:26-42`／`AskMissFallback.isMissAnswer:18-27`／`RecipeGetMarks.NO_RECIPE_UI`；而且 `查唔到` 本身係 denial token（`AskMissFallback:37`）→ 自我反噬；`isMissAnswer` 只對 ≤192 字有效＝長答必失效 | **唔做**，入 backlog（另批，要接現有機制） |
| **改 sample 會撞其他系統** | `coalesceMirrorEmission` signature 由 `grid/inputs/outputs` multiset 建（`:1897-1940`）；`mentionKeys()` 係插卡 needle（`RecipeEmbed:1148`）；`catalogLines` index 對 `[[recipe_card:N]]`（`AskService:1133-1151`） | **因為 B2 撤回（唔刪卡）→ index space／mirror／needle 全部唔受影響** ✅ |

### 9.2 v5 修法（最小、可實作、可證偽）

- **B3'（核心，唯一行為改動）**：把 vanilla 路徑嘅樣本選擇**對齊 JEI 路徑已有嘅 prefer 規則**：
  `JeiRecipeCards.tryCrafting`（＋同族 vanilla 收集器）由 `firstOf(ingredient)` 改成 `firstOf(ingredient, preferStack)` ——
  `getItems()` 內若有 **same item（同 tags）**＝焦點 → 用焦點做樣本；否則 fallback `getItems()[0]`（**唔准**「唔命中就唔出卡」）。
  compound ingredient（`@chestcavity` ＋ `#kubejs:organ`）嘅 `getItems()` 本來就含焦點 → 修完**卡面就係用戶手上嗰件**。
- **B5（log-only，幫助真機核實）**：per-card log 加 `matchedSlot=i sampleIsFocus=true|false ingredientKind=item|tag|compound`（純格式化，零行為）。
- **B7（獨立一批，閘收緊）**：`tests/check_ask_cards_debug_log.py`
  ① 先用 `strip_strings_comments()`（`check_settings_render_order.py:79-83` 已有 primitive）剝註解／字串 → 唔准被 javadoc 騙；
  ② pin **真實 5 條完成路徑**（`AskService:307 blocked／:333 error／:342 miss／:403 AI／:443 KEYWORDS`）中 4 條有 `logCardsEmitted` 嘅**精確 call 形式**；
  ③ **pin 玩家可見 artifact**（`packai.screen.recipe_use`／`AskService` 嘅「作为材料」），唔准 pin debug-only 標籤（現時 `tests/check_ask_cards_debug_log.py:74` pin 錯）；
  ④ 加 **self-injection 負對照**（照 `check_settings_render_order.py:221-238` 模式：注入 `emittedX=` 必須令 checker FAIL）；
  ⑤ pin `suppressed reason=frame` 語意（現時硬編碼於 `AskService:2694-2696`）。
- **撤回**：B2（硬閘／丟 tag-only 卡）、B3（改 card 樣本）、B4（空標題）、`查唔到配方` 文字 —— 全部唔做（有否證證據）。
- **文件導覽修正**：本檔有**兩個 `## 8`**（`## 8`＝B1 落地、第二個 `## 8`＝真機實錘）＋ `## 7.5` 位置錯；引用一律用**節標題**（「真機實錘」／「B1 落地」）避免再對錯節。

### 9.3 驗收（可證偽）
- 真機重問 `寰宇支配之剑器官` → 卡面材料格**必須顯示「寰宇支配之剑器官」本體**（唔准顯示 `chestcavity:raw_rich_sausage`）；
- 同族合法卡**數量唔可以少**（正對照：`#3` 呢張卡**仍要出**，因為佢 attribution 正確）；
- log 見到 `sampleIsFocus=true`（修好）＋ `ingredientKind=compound`；
- `tetra:modular_sword` 令 `零件` 資訊**照舊**走 strip；`infinity_sword` 維持 0 卡（**唔准**加假聲明文字）；
- B7 閘：self-injection 必須紅（RC≠0）、還原後綠；全量 `tests/check_*.py` 冇新增紅。

---

## 10. SK 澄清（2026-09-15 21:5x）＋ B8：**正文仍然講緊被隱藏嘅卡**

SK 原話：「**the text still said about the card that we hidden**」→ 唔係卡面樣本問題，係**正文／卡唔一致**。

**實錘（`ask-20260915-203013-tetra_modular_sword.jsonl`）**
- `render.cards.final`：`cardsOut=1`（只剩 1 張任務卡）——3 張 frame 卡被 `suppressModularFrameCards`（`AskService:396`）剷走；
- 但 `display.body.final`（**玩家真係睇到嘅正文**）仍然寫：
  - 「这把剑怎么砌出来：1. 剑刃…6. 用 Tetra 工作台把这些零件／材料依次装到剑上即可。」
  - 「怎么来：2. **空白模组剑的框架合成：切石机＋木棍可做出空白的模组剑**」← **正正就係被剷嗰張卡嘅內容**
- `render.markers`：`recipe_card_markers=[]` 但 **`emissionRefs=[1,2,3,4]`** ← 卡只剩 1 張，refs 仍然列 4 個＝**dangling refs**（指向被剷嘅卡）。

**B8 範圍（要過 review 才做）**
1. 抑制卡之後，**屬於該卡嘅正文內容要一齊處理**（刪行／改寫成「（此配方卡已隱藏）」或至少有明確交代）——唔准「卡無、字照講」；
2. **dangling refs 要清**：`emissionRefs`／`[card:N]` 唔准指向已剷嘅卡（重編號或剔除）；
3. 若 scrubbing 之後某 section **變空** → 該 section 標題一齊刪（呢點要同 `RecipeEmbed` 既有「唔准填空 section」邏輯（`sectionByOutputs:315/:522`、javadoc `:311-313` 提過 `axe` off-by-one）**一齊設計**，唔准亂動）；
4. 實作落點：`AskService:396` 抑制之後、`RecipeEmbed` 排卡之前／之後（要 review 定）；
5. 驗收：真機重問 `亚巴顿` → 正文**唔准**再提被剷嘅框架合成（或必須明示「已隱藏」）；`emissionRefs` 數量 = 實際卡數；`零件` 資訊照舊走 strip。
6. **唔准**動 §9.2 已定嘅 B3'／B5／B7 範圍（唔好順手改樣本或 gate）。
