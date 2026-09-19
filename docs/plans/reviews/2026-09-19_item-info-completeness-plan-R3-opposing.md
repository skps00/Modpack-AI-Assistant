# R3 反方 review — item-info-completeness plan（v3，§V3 覆寫）

- 角色：**反方（adversarial）**。審查對象：`docs/plans/2026-09-19-item-info-completeness-plan.md`（v2 body ＋ 頂部 **§V3 覆寫**，commit `d309ee7`）
- 環境（全部親測）：MC **1.19.2** / Forge **43.4.5** / pack **FTB Skies Expert**（`packai_sandbox_ftb`）/ packai `mod_version=0.2.3`
- 證據：
  - 19 條 capable trace `C:/Users/skps9/AppData/Local/Temp/autotest_results_20260919-231137/`（含 `latest.log`）
  - 另一組 19 條 **no-tools** trace `<sandbox>/minecraft/packai/trace/ask-20260919-13*.jsonl`
  - jar-cache `<sandbox>/minecraft/config/packai/jar-cache/`（360 shard）＋ mod jar `mods/ad_astra-forge-1.19.2-1.12.7.jar`
  - `forge/1.19.2/src/**`（含 `src/test/java/**`）、`tests/check_*.py`、`tools/check_item_info_coverage.py`
- 結論：**正方 4 : 反方 6（go = false）** —— 相對 R2（3:7）有實質進步，但仍有 **2 條 CRITICAL**（D0' 未綁定資料源；A5 無交付機制）＋ 1 條 HIGH（D2 失去驗收）。
- 本輪只讀；唯一寫入檔＝本報告。

---

## §A 我親核而 §V3 成立嘅部分（先講，唔想冤枉）

| §V3 主張 | 我嘅獨立核實 |
|---|---|
| V3.1 `AskEngine.java:826` = `promptFacts = capable ? List.of() : factsLive`，capable ⇒ facts 牆清空 | ✅ 逐字（`:823-831` 全文：`capable = capableForTools()` `:825`、`promptFacts` `:826`、`completeRound(…promptFacts…)` `:827-830`） |
| 76/76 toolsOffered=true、19/19 trace 有 tool.call | ✅ `grep -c toolsOffered=true latest.log` = **76**、`=false` = **0**；19/19 trace 皆有 `tool.call`（工具直方圖 acquire 19／jei_lookup 30／purpose_lookup 18／render_recipe_cards 29／quest_fetch 6／guide_fetch 5／item_search 3／tool_build 2／repair 1／tetra 1／consume 1） |
| JAR 通道零輸出 | ✅ `grep -l "\[JAR\]" ask-*.jsonl` = **0/19**；另核 en_us 標頭真值係 `[JAR]`（`ReplyLang.jarHeader`），唔係字串不匹配 |
| JAR 資料進 facts 牆嘅完整鏈 | ✅ 親手走一次：`JarLightIndex.factsForAsk`（`AskEngine.java:272`）→ `jarLines`（`:276`）→ `blocks.add(jarFactLines)`（`:686`）→ `appendCapped(facts,…)`（`:692-694`）→ `:826` 丟棄。V3.1 條鏈**完全正確**，冇漏環 |
| facts 牆在 capable 模式真係空 | ✅ 76 個含 `graphFacts` 鍵嘅 `send.facts` payload，**非空 = 0/76**；`sources` 只係 4 條硬編碼 boilerplate（`['pack quest book or local recipes','JEI (if any)','pack loot / fishing / trades / scripts (if any)','web search …']`，來自 `LlmClient.java:449-457`） |
| V3.2 儀器降級；基線 75%／90%；per-category miss | ✅ 實跑 `python tools/check_item_info_coverage.py`：`ALL 161/121 = 75%`、`DETECTABLE 91/82 = 90%`、miss = script16/tags11/guide5/trade2/fishing2/tooltip2/usages1/loot1、answer 1262–2012 逐字重現；`EXCLUDED` 常數在 `:21` |
| V3.2 「recipe/usages/loot/quest/fishing/trade 由 prompt 樣板餵」 | ✅ 我逐類別分離 tool.result vs send.facts：`fishing` 19/19 只來自 facts（boilerplate）、`trade` 18 facts-only＋1 both、`usages` 19/19 含 facts、`recipe` 19/19 both、`tags` 13/13 tools-only、`guide` 5/5 tools-only |
| V3.3 forge-only `*Check.java` = **50 檔／50 註冊 task** | ✅ `find forge/1.19.2/src -name "*Check.java"` = **50**；`tmp-check.gradle` unique `run*Check` = **50**；`neoforge/1.21.1` = **41**（41+50+2 = 93，解釋到 R2 見到嘅 93） |
| V3.3 python 閘 = baseline 122 綠＋1 已知紅 | ✅ `ls tests/check_*.py` = **123**；`%TEMP%\gate_baseline_20260919.txt` 123 行，唯一非 0 = `check_ask_display_leak.py:2`（需真機 log） |
| V3.4 真 ref 空間數字 | ✅ 我對 360 shard 全量重算：**total refs 19,292／L refs 5,160**（＝plan）；`blocks 2989／chests 727／entities 430／inject 279／gameplay 162／actions 139／archaeology 90／extractor 69／spoils 47／forged 47／misc 42／custom 33` 逐個吻合 |
| V3.4 「`gameplay/*` 唔准寫釣魚」 | ✅ 162 條 gameplay ref 我逐條列：**全部係** `piglin_bartering`(26)、`hero_of_the_village`(4)、`transmutation_table_*`(45)、各種 `*_reward`(92)、`trader_elephant_chest`(16) —— **`gameplay/fishing` 一條都冇**。此條更正**正確** |
| V3.4 lang key 樣本 `en_us.json:472` | ✅ `:472` = `"packai.reply.loot_table_obtain": "Loot table: %s"` |
| V3.5 tags 缺嘅係**資料源** | ✅ 全 forge 樹 `TagKey<Item>` 只出現喺 `client/jei/IngredientReqHints.java:204,209,227-251,260,268`，唔在白名單；`logic/PurposeLookupAskTool.java:33-42` 只係把 `env.purposeTooltip` 過 `AskPurposeContext` |
| V3.6 路徑更正 | ✅ 真檔 `client/patchouli/PatchouliGuideLookup.java`，`// B2: title search only when item path empty` 在 **:55**（V3 寫 :54-63，合理） |
| V3.6 新加白名單檔存在 | ✅ `logic/ReplyLang.java`、`logic/LootForwardIndex.java`（`isTrivialBlockSelfLoot` **:106-120**、呼叫過濾 **:126**）、`research/gen_tmp_check.py` 全部存在 |
| V3.7 插入點 | ✅ `RecipeEmbed.java:749` `interleaveEmissionCards` → `:764 placeEmissionCardsByRef` → `:766 disperseUnplacedEmissionCards` → `:768 stripCardRefTokens` → **`:769 blocks.addAll(sourceParts)`** ⇒ V3.7 結論（RecipeEmbed 自己可以派剩卡入新段落）**成立**（範圍應寫 749-769，非 768，差一行） |
| 任務簡介講「tool.result 內容可能唔喺 trace」 | ❌ **唔成立**：23:11 組 trace 嘅 `tool.result` **有內容**（例：oxygen_tank trace 第 26-29 行 `acquire`→`""`、`purpose_lookup`→`'[PURPOSE]\nOxygen Tank\n§2Consumable…'`）⇒ D0' 可以由 trace 直接量度（見 §B1） |

---

## §B Blockers（只列真正卡死嘅；每條一個 flip）

### B1（CRITICAL）D0' 「acquire 由空變非空」**冇綁定資料源**：`acquire` 今日睇唔到 jar 內嘅 loot table，而 plan 想要嘅 loot ref **100% 在 jar 內**；用 plan 自己嘅 fixture 風格會**今日就綠**（正是 plan 想防嘅假綠）

證據鏈（全部親核）：

1. **觀察成立**：19 條 trace 每次 `acquire` 呼叫 1 次、**16/19 result 為空**（非空 3 條：`tetra_dragon_sinew` = `[Local acquire] "dragon sinew"\nLoot table: minecraft:chests/bastion_treasure\nLoot: 掉落表「end city treasure」`；`thermalendergy` = 只有 variant note；`mekanism_sps_casing` = `Obtain: Antimatter-Dimensions`）。即 **只有 1/19 條 acquire 結果含 loot 資訊**。
2. **資料真係有**：`mods/ad_astra-forge-1.19.2-1.12.7.jar` 內 `data/ad_astra/loot_tables/chests/village/moon/blacksmith.json` **存在**，我抽該檔文字，`'oxygen_tank' in text == True`；jar-cache shard `9a59d6f03d4d.json` 亦記 `ad_astra:oxygen_tank → ["L|chests/village/moon/blacksmith", …]`。
3. **點解仍然空**：`PackIndex.acquireFactsDetailed`（`logic/PackIndex.java:1217-1244`）只由 `acquirePathsByItem`（`:147`，填充點 `:1612-1625 indexAcquireFile`）餵；而 `acquirePathsByItem` 由 `build()` 內 `Files.walk(roots)`（`:214-238`）填充 —— **`PackIndex.java` 全檔冇 `ZipFile`／`.jar`／jar 條目掃描（grep 0 hits）**。⇒ 模組 jar 內嘅 loot table **唔會**入 PackIndex ⇒ 對 ad_astra 氧氣罐（唯一 loot 來源在 jar 內），`acquire` 結構性必然空。
4. **plan 冇講呢件事，而且指向錯嘅地方**：V3.1 只寫「追 `AcquireAskTool.java:42-43` 空輸出分支 ＋ `AskEngine.java:823-830`」。`AcquireAskTool.java:42-43` 係 `clipAcquireLines(...)` ＋ `return lines.isEmpty() ? "" : …` —— 佢係**傳訊者**，上游空就必然回空；在該兩行加打點只會見到「lines 空」。真正要決定嘅係「**acquire 由邊個源頭拎 jar loot ref**」（白名單內唯一可行者＝`JarLightIndex`（§6-1 已白名單，且今日只有 `AskEngine.java:272` 一個呼叫者，**冇任何 tool 暴露它**））。
5. **假綠風險（最貴）**：現存 harness `forge/1.19.2/src/test/java/com/skps9/packai/logic/AcquireFactsCheck.java`（**已註冊**：`tmp-check.gradle:9 runAcquireFactsCheck`）用**鬆散檔** fixture（`:15-48` 寫 `datapacks/pack/data/minecraft/loot_table/chests/bonus_box.json` 等）就令 `acquireFactsFor("minecraft:diamond")` 含「掉落」、`nautilus_shell` 含「釣魚」——**證明今日 code 對鬆散 loot table 已經 work**。若 A0 沿用同款 fixture（plan 只寫「fixture item 經工具通道拎到 route」，冇要求 jar-sourced），**A0 今日已可綠，而 P0 缺陷（jar loot 到唔到玩家）零改變**。
6. 白名單亦未閉合：要嘛把 `JarLightIndex` 指定為 acquire 新源頭（可行，兩檔都在 §6／§V3.6），要嘛把 **`logic/PackIndex.java`** 加入白名單（今日唔在）；V3 兩者都冇寫。

- **FLIP**（任一成立即撤回）：① §V3.1 明寫 D0' 嘅資料源＝`JarLightIndex`（jar-cache），並寫明 `MAX_ASK_LINES=4`／`MAX_FACTS_PER_ITEM=8`／`clipAcquireLines` 預算下 fixture 至少出到 **1 條指定 table id**；**或** 把 `logic/PackIndex.java` 加入白名單並講明 jar 掃描範圍。② A0 嘅 fixture **必須係只存在於 jar 內嘅 loot ref**，而且 **pre-fix 必須紅**（＝今日跑 A0 就紅）；負控改為「把該 ref 從 jar-cache 移走 ⇒ 必紅」。③ 刪走 D0' 或把它降級為「診斷 only」。

### B2（CRITICAL）V3.1 令 worldgen **只剩工具通道**，但 §V3 冇任何 D5' 機制，而 A5 仍被 pre-register —— **A5 無法按 pre-registered 方式跑**

- V3.1 明令：新資料「一律經**模型實際會叫嘅工具**」交付，facts 牆唔准做交付點。
- 但 A5（`§V3.8`）要求：「隨機抽 5 礦物＋5 限定物品；**經工具通道**出維度／生態域／Y（唔靠 facts 牆）」—— plan 全文**冇**任何 D-item 講點令 worldgen 資料經工具出去（舊 §4-D5.1「無論問題點問都注入 facts」已被 V3.1 廢掉；§4 其餘（D5.2 觸發詞、D5.3 併入 D2）都係 facts 側或清單側）。
- 唯一候選工具 `worldgen_lookup` **確實在** `AskToolLoop.CAPABLE_TOOLS`（`AskToolLoop.java:39-42`），但實測 **0/19 被呼叫**（工具直方圖無此名；plan §4 自己都寫「19/19 冇叫過 worldgen_lookup」）。⇒ 按 V3.1 自己嘅約束（「模型**實際會叫**嘅工具」），`worldgen_lookup` **唔合格**。
- 而 §6 尾明文「**唔准**改 prompt 文案當修法」＋「唔准郁 `config/PackAiConfig.java`」，即「叫模型去 call worldgen_lookup」呢條路在 plan 內被封。結果：A5 變成「冇機制、冇白名單路徑」嘅 pre-registered 項。
- 附帶：舊 §9 有嘅 **A5b（主包對照）** 在 §V3.8 無聲消失（見 §D）。

- **FLIP**：① 學 V3.5 處理 tags 咁，把 worldgen（D5／A5／A5b）**明文移 P1**；**或** ② 寫出工具通道機制（邊個工具、client 側如何保證被叫——例如確定性 pre-round 注入 tool 結果、或允許改 orientation 文案並列明例外），且 A5 斷言落喺**確定性層**（tool.result 文字）而非答案文字。

### B3（HIGH）D2 仍然係 plan 嘅「completeness」主機制，但 §V3 覆寫之後 **D2 冇驗收**（A2 無聲消失），而 A8 又 pre-register 一條 D2 負控 —— 兩者互相矛盾

- §V3 覆寫聲明覆蓋 §9，但 V3.8 表只有 A0／A0b／A1／A3／A5／A6／A7／A8：**A2（headless 必答清單，舊 §9）被靜靜刪走**，V3 全文冇一句撤回 A2（A4 有明文撤回：V3.2 ②）。同樣消失嘅仲有舊 §9 A4（已明文撤回，OK）同 A1b（§2-D1 嘅真機驗收，**無聲消失**）。
- 同時 A8 仍寫「**拆 D2 ⇒ 具體 case 斷言跌**」；但 V3.2 ③ 只說「真正閘改為具體 case 斷言（**V3.4/A0b**）」——呢兩者係 **D1/D0' 嘅 loot route** 斷言，唔係 D2（必答清單＋補充段）嘅斷言。D2 唯一剩低嘅相關 test 係 A3＝`check_ask_card_fallback` 迴歸（**啱、但拆走 D2 一樣會綠**，因為它只斷言卡位唔被破壞）⇒ A8 嘅 D2 半條仍然係 **R1 指過嘅自指涉／無斷言**。
- 附註（對 plan 有利、但 plan 冇寫清）：V3.1 殺嘅係 facts **入 prompt** 嘅路；D2 嘅資料源其實係 client 側組裝嘅 facts list（`:481/:686-694`）＋ `loopState.extraFactLines()`，**唔一定要入 prompt**。既然要靠呢個活口，就要明寫，否則實作者會以為 D2 隨 V3.1 一齊死。
- **FLIP**：① 還原 A2（逐類別 expected，headless）並把 A8 嘅 D2 負控改為「拆 D2 ⇒ **A2 逐類別斷言**跌」；② 明寫 D2 資料源＝client 側 facts 組裝（並列明 V3.1 只禁 prompt 交付、不禁 client 組裝）。

### B4（MED-HIGH）V3.4 kind 映射**未補齊**（193/5,160 L refs 未列），且 `blocks/*`→`block_drop` 無條件映射與現行 `loot_noise_skip`／`isTrivialBlockSelfLoot` 相衝

我對同一份 jar-cache 重算 L prefix 全量（sum = 5,160 ✓）：

| 類 | 我嘅真值 | V3.4 有無列 | 後果 |
|---|---|---|---|
| `inject/chests` | 169 | ✅→chest_loot | — |
| **`inject/entities`** | **87** | ❌ 未列 | 落 default `loot_other`（1.7% L refs） |
| **`entity`**（單數） | **18** | ❌ 未列 | 同上 |
| **`elementalcraft`** | **13** | ❌ 未列 | 同上 |
| **`advancements`** | **9** | ❌ 未列 | 同上 |
| **`chest`**（單數） | **3** | ❌ 未列 | 同上 |
| **`items`** | **2** | ❌ 未列 | 同上 |
| 無斜線裸 key | **61**（`artifact`35／`loot`11／`bastion_scrolls`10／`empty`2／`holosphere_reward`1／`book`1／`give_manual`1） | V3.4 寫「＋冇斜線裸 key」但**冇列**，且把 `artifact 35` **誤列成 prefix** | `empty`／`artifact` 呢類係掃描噪音，會直接變玩家可見 route（今日 jar-cache 已有 `L|empty`、`L|artifact`、`L|items/drinking_hat`、`L|advancements/shader_epic`） |

⇒ 共 **193/5,160（3.7%）** 未 pre-register，全部靠 default 兜到 `loot_other`；而 A1 要求「逐 kind **人化字串樣本**」逐字對 —— 未列嘅 kind 就冇 expected 可對。另外 `blocks/*` 佔 2,989/5,160（58%）：`LootForwardIndex.isTrivialBlockSelfLoot`（`:106-120`，call site `:126`）係刻意**剔走**「方塊掉自己」，而 en_us 已有 `packai.reply.loot_noise_skip`（"Do not cite default block loot (mining a placed block drops itself)…"）。V3.4 只寫 `blocks/*`→`block_drop`，冇寫要唔要套同一過濾 ⇒ 新工具輸出可能同現行 prompt 規則相反（玩家視角＝噪音）。

- **FLIP**：補齊全 prefix 表（含上述 6 個＋把 `artifact` 移到裸 key 並列出 61 條），加 default 規則，並明寫 `blocks/*` 套用 `isTrivialBlockSelfLoot`（或講清唔套而 A1 sample 用非平凡例）。

### B5（MED）V3.2 新加嘅「全 corpus 真 item tag id 只有 **1** 個」**係錯**（我搵到 4 個）—— 呢條係 V3.5 撤回 tags 嘅主要理據之一

- 我對 19 條 capable trace 兩個欄（`tool.result` ＋ `send.facts`）全量抽 `#ns:path`，去重後 **4 個真 item tag id**：
  - `#minecraft:piglin_loved`、`#balm:gems`（`advancedperipherals_inventory_manager` 嘅 JEI 行：`- Advanced Computer（#minecraft:piglin_loved）, Emerald（any of 4）, Emerald（#balm:gems）`）
  - `#ad_astra_platform:iron_rods`、`#forge:storage_blocks/steel`（`ad_astra_desh_engine` 嘅 JEI 行：`Desh Block, Iron Rod（#ad_astra_platform:iron_rods）, Block of Steel（#forge:storage_blocks/steel）`）
- 意思：**item tag id 今日真係經工具通道（JEI recipe 行）到咗模型**，唔係「幻影／零資料」。V3.5 撤回「唔加新工具」嘅**決定**仍然守得住（本 plan 要嘅係「該 item 所屬 tag 清單」，唯一讀者 `IngredientReqHints` 確在白名單外），但**理由**寫成「全 corpus 只有 1 個」係新一輪「我親核」卻核錯（與 R2-B6 同款風險）。
- **FLIP**：把該句改成「item 自身 tag 清單嘅唯一資料源在白名單外；JEI recipe 行偶然夾帶 `#ns:tag`（實測 4 個 id／2 條 trace），但唔係可依賴嘅 tag 交付通道」，並把 4 個 id 寫入報告。

---

## §C §V3.8 驗收表逐項可證偽性

| 項 | 可證偽？ | 反方判斷 |
|---|---|---|
| A0 | ⚠️ **否（現狀）** | 「fixture item 經工具通道拎到 route」冇指定：①邊個 tool 結果／定答案文字？②fixture 由邊個源（jar vs 鬆散檔）？③**pre-fix 應否紅**？→ 用鬆散檔 fixture 今日已綠（B1-5） |
| A0b | ✅ | 真機、字串明確（唔准再出「no loot … indexed」）；但「掉落表人化名」有 **2 種 formatter** 在跑（`en_us:472 "Loot table: %s"` vs trace 實見混語 `Loot: 掉落表「end city treasure」`）⇒ 要 pin 死用邊一個，否則可以對住一個 zh 洩漏字串報綠 |
| A1 | ⚠️ | 逐字 expected 可判，但 V3.4 映射未補齊（B4）＋同一 tool 通道有兩種人化體 |
| A3 | ✅ | 舊 fixture ＋**新「卡＋補充段」fixture**（V3.7 證實 `:769 addAll` 在 disperse 之後）—— 呢個係本輪最好嘅修正之一；sha 零改動只證「冇郁檔案」，新 fixture 才係真控制 |
| A5 | ❌ | 冇機制（B2）；按 V3.1 由 pre-registered 變成**跑唔到** |
| A6 | ✅（需補 seam） | 「無關查詢回空」可判；但目標類 `client/patchouli/PatchouliGuideLookup.java:40` 用 `ForgeRegistries.ITEMS`（client-only），現存 headless 先例係經純 logic helper（`PatchouliApiFallbackCheck` 測 `GuidebookPins`）⇒ 要明寫 headless seam，否則 A6 跑唔到 |
| A7 | ✅ | 我親核：forge **50/50**（50 檔／50 task）、python **123 檔＝122 綠＋1 已知紅**（`check_ask_display_leak.py`）；V3.3 已把「113?」寫死 |
| A8 | ⚠️ | D0' 半條 OK（但見 B1：要改成 pre-fix 紅）；**D2 半條無定義斷言**（B3） |
| coverage 儀器（描述性附錄） | ✅ | 正確降級（唔做閘）；基線 75%／90% 我已逐字重現 |

**仍有一條唔可證偽（A5）＋兩條「形式可證偽但標的錯」（A0／A8-D2）** ⇒ R1 起講嘅可證偽性問題**未全清**。

## §D §V3 覆寫自身引入嘅新矛盾／回歸（v2 文字仍上下可見）

1. **無聲刪項**：舊 §9 嘅 **A2**（D2 必答清單 headless）同 **A1b**（§2-D1 真機 loot-only 人化表名）在 V3.8 消失，冇撤回聲明；而 **D2／D1 仍然在生**（A8 引 D2、V3.4/A1 引 D1）⇒ 「一項交付物冇驗收」。
2. **覆寫範圍語意不明**：V3 標題寫「覆蓋 §0.2／§0.3 兩點／§1／§2／§3／§4／§5／§6／§9」，但 V3 只提供**局部修訂**（V3.1-V3.8），冇 restate 被覆蓋章節嘅其餘內容 ⇒ 讀者要自行判斷「舊 §5-D3.2 仲算唔算數」等（結論上 §5/§6 大部分仍生效，但呢種寫法對實作者係風險）。
3. **§V3.6「刪：tags 相關檔」係空刪**：舊 §6 14 項內本來冇 tags 檔；真正缺嘅係 **`logic/PackIndex.java`**（B1）同 guide 側 seam（§C-A6）——白名單仍然未閉合。
4. **`config/PackAiConfig.java` 不變 × A5 需要新工具** 潛在衝突：若 A5 最終要靠新 tool，`tests/check_tool_schema_stable.py:108`（`assert forge_only == ["knowledge_lookup"]`）會變**新紅**（plan 自己 §0.3-7 知，但 §V3 冇講 worldgen 點繞）。
5. 小錯（唔卡死但要改）：`logic/PurposeLookupAskTool.java:33-46` —— 該檔**只有 43 行**，`run()` 係 **:33-42**（:46 唔存在）；`RecipeEmbed.java:749-768` 應為 **749-769**。

---

## §E Flip conditions 總表（每條一 flip，收窄即可翻）

1. **B1**：明寫 D0' 資料源＝`JarLightIndex`（jar-cache；含預算）或把 `logic/PackIndex.java` 入白名單；A0 fixture 改為 **jar-only loot ref**，**pre-fix 必須紅**，負控＝移走該 ref ⇒ 紅。
2. **B2**：worldgen（D5／A5／A5b）明文移 P1；或寫出「工具通道＋確定性被呼叫」機制，斷言落 tool.result。
3. **B3**：還原 A2（逐類別 headless 斷言），A8 嘅 D2 負控改指 A2；明寫 D2 資料源＝client 側 facts 組裝（V3.1 只禁入 prompt）。
4. **B4**：補齊 prefix→kind 全表（＋default；`artifact` 移裸 key；列 61 裸 key），明寫 `blocks/*` 是否套 `isTrivialBlockSelfLoot`（同 `loot_noise_skip` 一致）。
5. **B5**：改寫「只有 1 個 tag id」為「4 個／2 trace，且屬 JEI 行夾帶，非可依賴 tag 通道」。
6. **§D-1/2/4/5**：補撤回聲明、寫清覆寫語意、修 3 個行號、講清 worldgen 若需新 tool 點避 schema gate。

---

## §F 評分與建議

- **正方 4 : 反方 6 → go = false**
- **逐輪比分**：R1 正方2:反方8 → R2 3:7 → **R3 4:6**
- 卡死嘅**載重決定**（兩條）：
  1. **D0' 交付機制**：plan 已正確鎖定「capable ⇒ facts 牆死」，但未鎖定「jar loot ref 由邊個源入 `acquire`」。實測證明現行 `acquire` 結構性睇唔到 jar 內 loot table（`PackIndex` 只 walk 鬆散檔），而 plan 自己風格嘅 fixture 今日就綠。
  2. **A5 worldgen**：V3.1 之後 worldgen 冇任何交付設計，唯一工具 0/19 未被呼叫，而 plan 又禁「改文案當修法」。
- 最貴嘅未知（要拎咩數據才解開）：① 一次 **jar-sourced fixture** 在現行 code 下 A0 係紅嘅實證（我今日只能推論＋指出 `AcquireFactsCheck` 用鬆散檔已綠，未跑 gradle，因為本輪 read-only）；② 「提升 `worldgen_lookup` 被呼叫率」在白名單內可否做到（需要 1 次 capable run 對照）。
- **建議（按 repo 規則：3–4 輪上限，R3 已係第 3 輪且未達 8:2）→ 停手交 SK 定**。可選路線（我推薦 A）：
  - **A（推薦）拆件**：(a) 「D0' 渠道修復（jar-cache → `acquire`）＋A0/A0b 收緊」單獨出 v4，範圍細、P0 級、四條 flip 全部可即時滿足；(b) D1 route 清單／D2 必答清單；(c) worldgen（D5）同 tags 一齊留 P1。
  - **B**：只補 B1/B2/B3 三條（其餘小修），再評一輪（第 4 輪，最後一輪）。
  - **C**：照做但接受 A5／D2 無閘（風險：兩個真機 run 白做、覆蓋率回升無依據）—— 我唔建議。
