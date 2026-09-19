# Plan v2：件物品「全部資料」覆蓋（含結構战利品／挖方塊／維度／生態域／礦物分佈）

- 日期：2026-09-19（晚）／作者：Hermes／狀態：**v2，待 R2 反方 review**，未改任何 code
- 版本語境：MC **1.19.2** ＋ Forge 43.4.5；packai `mod_version=0.2.3`；實測 pack＝**FTB Skies Expert**（357 mods，`packai_sandbox_ftb`）；對照＝主包（NFWC 系）／E9E（232 mods）
- v1→v2 因由：R1 反方 = **2:8（go=false）**，捉到一個我嘅真 bug ＋ 一條**上游產品 bug**（見 §0）


## §V4 覆寫（v4；R3 = 4:6 後修訂；**覆蓋 §V3.8 之 A0/A5/A8/A1**）

### V4.1 交付路線：jar-cache → 工具通道（R3 指出嘅真斷點）
**R3 親核事實**：`PackIndex.java` **零 jar/ZipFile 掃描**（grep 0 hits）；`acquirePathsByItem` 只由 loose roots `Files.walk` 餵（`:214-238`）；jar-cache（`JarLightIndex.factsForAsk`，`AskEngine.java:272→276→686→692`）只入 facts 牆，而 `:826` 喺 capable 模式清空 ⇒ **jar loot refs 冇任何玩家可見管道**。

**D0''（具體交付物）**
1. `logic/JarLightIndex.java`：新增 `public List<String> routeLinesForItem(String itemId)`（讀現有 per-jar cache map；格式沿用 `L|…`／`U|…`／`R|…`）
2. `logic/AcquireAskTool.java`：回 `acquire` 時**合併**loose 結果＋上述 jar refs（去重、排序穩定；每 item 上限沿用 `MAX_ACQUIRE_LINES_FULL`）
3. **唔准**改 `AskEngine.java:826` 嘅 capable 清空語意；**唔准**新 config key；**唔准**改 prompt 文案

**A0（完全指定、可證偽）**
- fixture 來源：**真 jar-cache shard**（沙盒 `config/packai/jar-cache/9a59d6f03d4d.json`，jar=`ad_astra-forge-1.19.2-1.12.7.jar`）＋ 一個 loose-datapack 變體
- **斷言對象＝`acquire` 工具回傳文字**（唔係最終答案）
- expected（逐字）：`ad_astra:oxygen_tank` ⇒ 含 `chests/village/moon/blacksmith`；`tetra:dragon_sinew` ⇒ 含 `inject/chests/end_city_treasure`
- 執行：headless、**capable 模式**（toolsOffered=true；沿用 `AcquireFactsCheck` 註冊法，`tmp-check.gradle:9`）
- **負控**：`routeLinesForItem` 回空 ⇒ A0 **必紅**

### V4.2 A5 交付機制（閉環；worldgen 併入 acquire）
- 理由：`worldgen_lookup` **0/19 被叫**、`acquire` 19/19 被叫 ⇒ worldgen routes **併入 `acquire` 輸出**
- 格式（逐字）：`W|ore|dim=<dim>|biome=<biome>|y=<min>..<max>|size=<n>|count=<n>`；**缺欄位唔准填**（沿用 `WorldgenFacts` 「never invent」原則）
- **A5**：headless 對 5 個 fixture（含 `ad_astra:moon_desh_ore`、`minecraft:diamond_ore`、`ad_astra:mars_ostrum_ore` 等）斷言 `W|` 行含 dim／biome／y；真機：抽 5 件礦物，答案必提維度＋Y
- **A5b**：主包 1 輪抽 5 件（跨 pack 通用性；每次重新隨機抽）

### V4.3 D2 閉環（A2/A8 具體化）
- 實作：新 `logic/InfoCompleteness.java`（輸入 facts 類別清單 → 輸出「補充」段；lang key；插 footer **之前**）
- **A2**：新 headless `InfoCompletenessCheck.java`——fixture facts（3 類有料）⇒ 輸出必含 **3 行**（逐字 expected）
- **A8**：同 fixture、停用 `InfoCompleteness` ⇒ A2 **必紅**
- **A3**：`RecipeEmbed` 舊 fixture ＋ **新「卡＋補充段」fixture**（覆 `RecipeEmbed.java:749/764/766/769` 順序：interleave → placeEmissionCardsByRef → disperseUnplacedEmissionCards → addAll(sourceParts)）兩者都要綠

### V4.4 A1 per-kind 樣本補齊（R3 指 3.7% 漏）
- 補：`inject/entities`(87)→`mob_drop`、`entity/*`(18)→`mob_drop`、`elementalcraft/*`(13)→`loot_other`、`advancements/*`(9)→`loot_other`、`chest/*`(3)→`chest_loot`、`items/*`(2)→`loot_other`、**無斜線裸 key**(61)→`loot_other`；`artifact`(35) 更正為 `artifacts`
- 每個 kind（含 default `loot_other`）都要貼**逐字人化樣本** ⇒ 5,160 條 L refs **100% 有歸類**

### V4.5 驗收表（覆蓋 §V3.8；新白名單項：`logic/JarLightIndex.java`／`logic/AcquireAskTool.java`／新 `logic/InfoCompleteness.java`＋`InfoCompletenessCheck.java`／`research/gen_tmp_check.py`）
| # | 斷言對象 | fixture | expected | 負控 |
|---|---|---|---|---|
| A0 | `acquire` 回傳文字（capable 模式） | 真 jar-cache shard ＋ loose 變體 | 逐字含 `chests/village/moon/blacksmith`／`inject/chests/end_city_treasure` | `routeLinesForItem` 回空 ⇒ 紅 |
| A0b | 真機答案（FTB） | 氧氣罐 | 講到掉落表人化名；**唔准**「no loot … indexed」 | — |
| A1 | `acquire` 文字 | 全 prefix→kind 樣本 | 逐 kind 逐字對（含 default） | — |
| A2 | `InfoCompleteness` 輸出 | 3 類有料 fixture | 3 行逐字 | 停用 ⇒ 紅（A8）|
| A3 | 卡落位 | 舊 fixture ＋ 新「卡＋補充段」fixture | 全綠；`RecipeEmbed`／`RecipeCard` sha256 零改動 | — |
| A5 | `acquire` `W\|` 行 ＋ 真機答案 | 5 礦物 fixture | 含 dim／biome／y；答案提維度＋Y | 拆 worldgen 併入 ⇒ 紅 |
| A6 | `guide_fetch` 回傳 | 無關查詢（3 個已知 case） | 回空；唔准 `how_to_enchant` | — |
| A7 | 全回歸 | — | forge **50/50**；python = baseline 122 綠＋1 已知 | — |
| A8 | 見各項負控 | — | 拆 D0''／D2／worldgen 併入 ⇒ 對應項必紅 | — |


## §V3 覆寫（v3；R2 = 3:7 後修訂，**以下內容覆蓋 §0.2／§0.3 兩點／§1／§2／§3／§4／§5／§6／§9**）

### V3.1 渠道模型更正（**最重要**；R2 B1 已由我親核）
`AskEngine.java:826`：`List<String> promptFacts = capable ? List.of() : factsLive;`
⇒ **工具可用時 facts 牆係故意清空**；實測 76/76 `toolsOffered=true`、19/19 trace 有 tool.call ⇒ 全部走 capable 路徑
⇒ v2 嘅 D0.2（「保證 L|/U|/R| 入 facts」）同 D5.1（worldgen 注入 facts）**零效果**，A0 headless 會**假綠**
⇒ **改為**：所有新資料一律經**工具通道**交付（模型實際會叫嘅工具），facts 牆**唔准**作為交付點
   - D0' 目標：`acquire` 由 16/19 空 → **fixture 必非空**；追 `AcquireAskTool.java:42-43` 空輸出分支 ＋ `AskEngine.java:823-830`（v2 打點漏咗）
   - A0 必須喺 **capable 模式**跑（toolsOffered=true）＋**負控**：capable=true 但工具回空 ⇒ 必紅

### V3.2 量度降級（**coverage 唔做閘，只做描述**；R2 B2 成立）
「available」分母被 **prompt 樣板／問題文字**餵：`loot` 19/19 來自 "If local acquire lists loot/chest/fish"；`recipe` 19/19 來自 `[RECIPE_CARDS]` 導言；`usages` 19/19 來自問題 "used for"；`quest` 19/19 來自 `role=quest`；`tags` 13/19 來自 tooltip 一行——全 corpus 真 item tag id 只有 **1 個**
⇒ 處置：① coverage 儀器**降級為描述性附錄**（唔做 A4 閘）② **撤回** A4「tags 11→0」（既係幻影，又同 EXCLUDED 自相矛盾）③ 真正閘改為**具體 case 斷言**（V3.4/A0b）

### V3.3 我 §0.3 兩個數字更正（R2 B6；我錯）
- 「93 個 `*Check.java`」＝我連 neoforge(41)＋forge(50)＋備份一齊數；**真值 forge-only = 50 檔／50 註冊 task** ⇒ **A7 維持 50/50，唔使改**（v2 講「要改寫」係錯）
- 「224/6842 物品超 8 refs」對住 FTB jar-cache 重算係 **476/9,840**；產生 v2 數字嘅腳本／快照未保存 ⇒ **撤回該數字**（要保留就要公開生成腳本）

### V3.4 D1 route schema 補全（R2 B5）
真 ref 空間（FTB jar-cache：19,292 refs／5,160 L refs）prefix 分佈：`blocks` 2989／`chests` 727／`entities` 430／`inject` 279／`gameplay` 162／`actions` 139／`archaeology` 90／`extractor` 69／`spoils` 47／`forged` 47／`misc` 42／`artifact` 35／`custom` 33 ＋**冇斜線裸 key**
⇒ `kind` 映射要**補齊＋有 default**：`blocks/*`→`block_drop`；`chests/*`→`chest_loot`；`entities/*`→`mob_drop`；`inject/chests/*`→`chest_loot`（人化樣本：`Loot table: inject/chests/end_city_treasure`，lang key `en_us.json:472`）；`gameplay/*`→`loot_misc`（**唔准寫「釣魚」**，多數係 `piglin_bartering`）；其餘 → `loot_other`
⇒ A1 expected 要逐 kind 貼**人化字串樣本**

### V3.5 tags deliverable 撤回（R2 B3/B4）
缺 tag id 唔係「加工具」而要**新資料源**：唯一讀 `TagKey<Item>` 者係 `client/jei/IngredientReqHints.java:204-251`（白名單外），`purpose_lookup` 只回 NBT tag **數量**（`logic/PurposeLookupAskTool.java:33-46`）
⇒ **本 plan 唔做 tags**（移 P1）；如要保留就要把該資料源寫入白名單並加測試

### V3.6 白名單更正（R2 B3）
- 路徑錯：`PatchouliGuideLookup.java` 真檔係 **`client/patchouli/PatchouliGuideLookup.java:54-63`**
- 補入：`logic/PurposeLookupAskTool.java`／`logic/ReplyLang.java`／`logic/LootForwardIndex.java`（`blocks/*` 過濾喺 `:106-120,126`）／`research/gen_tmp_check.py`（新 harness 註冊）
- **刪**：tags 相關檔（V3.5 撤回）；`config/PackAiConfig.java` 仍**唔准**郁

### V3.7 D2 插入點（R2 B7）
`RecipeEmbed.java:749-768` 會 `placeEmissionCardsByRef` ＋ `disperseUnplacedEmissionCards` **之後**才 `addAll(sourceParts)` ⇒ RecipeEmbed 自己可以派剩卡入新「補充」段
⇒ A3 要加**「卡＋補充段」fixture**（唔可靠舊 fixture）＋斷言落位正確

### V3.8 驗收表（覆蓋 §9）
| # | 項目 | 標準 |
|---|---|---|
| A0 | headless **capable 模式** | fixture item 經工具通道拎到 route；**負控**（工具回空⇒必紅）|
| A0b | 真機 FTB | 氧氣罐答案講到掉落表人化名；**唔准**再出「no loot … indexed」|
| A1 | headless routes | 逐 kind 人化字串**逐字對**（V3.4 樣本）|
| A3 | 卡落位 | 舊 fixture ＋**新「卡＋補充段」fixture** 都要綠；`RecipeEmbed`／`RecipeCard` sha256 零改動 |
| A5 | 真機 worldgen | 隨機抽 5 礦物＋5 限定物品；**經工具通道**出維度／生態域／Y（唔靠 facts 牆）|
| A6 | headless guide | 無關查詢回空 |
| A7 | 全回歸 | forge **50/50**（唔變）＋python 閘 = baseline 122 綠＋1 已知 |
| A8 | 負控 | 拆 D0' ⇒ A0 紅；拆 D2 ⇒ 具體 case 斷言跌 |
| — | coverage 儀器 | **描述性**（唔做閘）；基線 75%／90% 只作附錄 |

## §0 基線更正（v1 數字係錯，以下係修好之後嘅真值）

### 0.1 儀器 bug（我錯，已修）
`tools/check_item_info_coverage.py` v1 嘅分子用咗「**答案有嘅類別**」而唔係「**|available ∩ covered|**」⇒ 高估（v1 報 81%）。已改成交集運算，並凍結分母（新增 `EXCLUDED` 常數集）。

### 0.2 真值（19 條 FTB trace，MC 1.19.2＋Forge 43.4.5）
| 指標 | 值 |
|---|---|
| ALL categories | available **161** / covered **121** = **75%** |
| DETECTABLE subset（排除 fishing／script／tags／trade／worldgen） | **91 / 82 = 90%** |
| per-category miss | script 16／tags 11／guide 5／trade 2／fishing 2／tooltip 2／usages 1／loot 1 |
| 答案長度 | 1262–2012 字（**code 層冇 cap**；v1 講「1.3–2.0k 上限」係錯） |

### 0.3 R1 指控（我逐條親核，全部成立）
1. **jar-cache 有料但冇入 prompt**：`ad_astra-forge-1.19.2-1.12.7` 嘅 cache 有
   `ad_astra:oxygen_tank: ["L|chests/village/moon/blacksmith", "U|crafting_shaped|ad_astra:oxygen_loader"]`
   但 **0/19** trace 含 `[JAR]`；`send.facts`（76 次）**冇一次**含 `chests/`。
   ⇒ 氧氣罐答案寫「No loot, chest, trade or fishing path … indexed」＝**同索引事實相反**（真 bug）
2. `JarLightIndex` grep `structure|worldgen` ＝ **0 hits**；loot ref 只有裸 table id（`chests/village/moon/blacksmith`）⇒ **冇 tableId→結構名 mapping** ⇒ v1 嘅 `structure:'End City'` 唔可行
3. `acquire` 16/19 空（`AcquireAskTool.java:42-43`）；`graphFacts` 19/19 空
4. caps：`MAX_ASK_LINES=4`／`MAX_FACTS_PER_ITEM=8`／`MAX_LOOT_PER_JAR=150`（`JarLightIndex.java:56-60`）／`MAX_ACQUIRE_LINES_FULL=12`（`AskToolContext.java:31`）／`maxFacts=24`／`maxJeiChars=12000`；**224/6842** 物品已超 8 refs
5. `guide_fetch` 真 bug：5 次中 3 次回同一個無關頁（`worn_notebook/enchantments/how_to_enchant`）＝`PatchouliGuideLookup.java:55-63` title fallback；回傳 title/textClip 係 raw lang key
6. `git status --porcelain` ＝ **126** 項（71 M／53 ??／2 D）——v1 寫 127 係錯
7. `tests/check_tool_schema_stable.py:108` 斷言 `forge_only == ["knowledge_lookup"]` ⇒ **加新工具會令閘變紅**（新紅，唔屬「已知 1 紅」）
8. 93 個 `*Check.java` 但只註冊 **50** 個 run*Check 任務 ⇒ A4 講「50/50」要寫明係「已註冊任務 50/50」

## §1 D0（最高優先）：通返「pack 索引 → prompt」管道
**問題**：索引有 loot／用途／配方 refs，但冇入到模型 ⇒ 玩家拎唔到（而且會答錯「冇索引到」）。

- **D0.1 診斷**：喺 `JarLightIndex.factsForAsk`（讀：`AskEngine.java:272/292`；組裝：`:481`；注入：`:692-693`）逐段加**可數** debug log（每段 entry 數／字元數／有冇被 cap 截）——唔准入 secrets、唔准入玩家可見文字
- **D0.2 修**：保證每件 item 至少 top-N 條 `L|`（loot：`chests/*`＝結構箱、`blocks/*`＝**挖方塊掉落**、`entities/*`＝生物掉落、`gameplay/fishing`＝釣魚）／`U|`（用途）／`R|`（配方）入 facts
- **D0.3 預算表（明寫，唔准靜靜截）**：每輪 facts 總字元上限＋各段（`[JAR]`／JEI／tooltip／quest）配額；超標必須**明示 truncated**（唔准靜默掉）
- **D0.4 防假綠閘（headless）**：`AskFactsRoutesCheck.java`——用 fixture item（有 loot refs）跑 facts 組裝 ⇒ **prompt 文字必須含該 table id**；負控：cap 設 1 ⇒ 必須跌
- **驗收**：A0 headless PASS＋負控翻紅；A0b **真機**：氧氣罐答案要講「Moon village blacksmith chest」、唔准再出現「no loot … indexed」

## §2 D1：取得途徑清單（**只到 table-id 級**，唔自創新 mapping）
- **D1.1 範圍收窄**：route entry 只寫索引真係有嘅值：`{kind:"loot", table:"chests/village/moon/blacksmith"}`／`{kind:"block_drop", table:"blocks/moon_desh_ore"}`／`{kind:"mob_drop", table:"entities/martian_raptor"}`／`{kind:"fishing", table:"gameplay/fishing"}`／`{kind:"craft", station:"Crafting Table"}`／`{kind:"usage", station:"ad_astra:oxygen_loader"}`
- **D1.2 人化**：用**現有 lang key**（`packai.reply.loot_table_obtain`，`en_us.json:472`）把 table id 變玩家可讀文字；**冇 tableId→結構名 mapping 就唔准写結構名**（要寫新 mapping 就另立 D1b，唔喺本 plan 範圍）
- **D1.3 明令**：**唔准**把 `kubejs/server_scripts/x.js:40` 呢類 provenance 出到玩家文字（`PackIndex.java:1284` 明文禁 file/line）
- **驗收**：A1 headless：fixture item 嘅 route entry 齊全且格式正確（逐字寫死 expected）；A1b 真機：抽 loot-only 物品，答案要講得出**掉落表人化名**（唔准講結構名）

## §3 D2：必答清單＋機械式補完（唔靠 prompt 喊）
- **D2.1 清單**：由 facts 生成「有料類別」清單（recipe／usages／tooltip／loot／trade／quest／guide／tags／worldgen），逐類檢查答案有冇覆蓋；**類別級**判斷（唔係 token 級）
- **D2.2 補完**：缺失類別 → 生成一段「補充」，**插入位置＝footer 之前**（同 `AskJeiHints.ensureQuestStatusVisible` 用同一 anchoring：`ReplySources.HEADER`，`AskJeiHints.java:274`），**原文一字不改**
- **D2.3 唔准打爛卡落位**：`RecipeEmbed.java:747` 明文「Sources footer always last」；`interleaveEmissionCards`（`:749-752`）以 footer 硬切；`tests/check_ask_card_fallback.py:309/:842` 斷言「尾段文字唔可以吞卡／card marker 唔可以落喺尾段文字內」⇒ D2 補完段落必須喺 footer 前、且**唔准含 card marker**；A3 會逐條跑呢兩個 test
- **D2.4 語言**：所有新字串走 lang key（`en_us`／`zh_cn`／`zh_tw` 三語同步）；provenance 一律經 `ReplyLang` 人化
- **驗收**：A2 headless：清單對 fixture 生成正確；A3 卡落位兩個 test 保持綠；A4 真機：coverage 由基線 75%／90% 升到**100% of available（detectable 子集）**，逐類別預先寫死目標

## §4 D5：worldgen 三類（維度／生態域／礦物分佈）
**現況**（親測）：機制齊（`WorldgenIndex`／`WorldgenFacts` parse 到 `Configured(size)`＝礦脈大小、`Placed(count,countRange,heightRange)`＝Y 分佈、biome／structure／structure_set／modifier）；pack 有料（頭 120 個 jar：biome 67／structure 36／configured_feature 20／structure_set 15／template_pool 7／placed_feature 4）；但 **19/19 冇叫過 `worldgen_lookup`**、facts 注入係關鍵詞觸發（`AskEngine.java:696 WorldgenIndex.lookup(question,…)`）。
- **D5.1**：凡 item 有 worldgen entry（礦物／生態域／結構生成）⇒ **無論問題點問都注入**（維度／生態域／Y 範圍／礦脈大小／頻率／結構 id）
- **D5.2**：核 jar 掃描範圍**真係**包 worldgen（config 只寫 `recipes|loot_tables`）＋核 `WorldgenIndex.lookup` 觸發詞表（中英問法／where to find／維度）
- **D5.3**：維度／生態域／礦物分佈併入 D2 必答清單
- **驗收**：A5 真機（**每次重新隨機抽**，唔准重用上輪）：5 件礦物 → 必出 **Y 範圍＋礦脈大小＋維度＋生態域**；5 件生態域／結構／維度限定物品 → 必出對應來源；A5b 對照主包（跨 pack 通用性）

## §5 D3：工具缺口（**唔加新工具**，避開 schema gate 變紅）
- **D3.1 tags**：v1 打算加 `tags_lookup`，但 `check_tool_schema_stable.py:108` 會變紅 ⇒ 改為**擴充現有 `purpose_lookup` 輸出**（tags 清單），唔改工具 schema
- **D3.2 guide 檢索 bug（真 bug）**：`PatchouliGuideLookup.java:55-63` title fallback 令 generic 詞中招 ⇒ 修：item path 空時**唔准**用 question 標題搜；無相關 entry ⇒ 回空（並記 counter）
- **D3.3** 順手修：guide entry 回傳 raw lang key（`ars_nouveau.page.how_to_enchant`）⇒ 經 lang 解析
- **驗收**：A6 headless：無關查詢回空（唔准回無關頁）；A6b 真機：3 個已知 case 唔再出 `how_to_enchant`

## §6 白名單（R1 點名嘅缺口已補齊；只准改以下檔案）
1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/JarLightIndex.java`（D0）
2. `logic/AskEngine.java`（D0 注入／D2 清單／D5 注入）
3. `logic/AskToolContext.java`（D0/D2 預算常數）
4. `logic/AskToolLoop.java`（只在必要時；**唔准**改 MAX_LLM_ROUNDS=3／CAPABLE_TOOLS 語意）
5. `logic/WorldgenIndex.java`／`logic/WorldgenFacts.java`（D5 觸發詞／注入）
6. `logic/AcquireAskTool.java`（acquire 空輸出 bug）
7. `logic/PatchouliGuideLookup.java`（D3.2 檢索修正）
8. `logic/ReplySources.java`／`logic/AskJeiHints.java`（只在 D2 插入點必要時）
9. `assets/packai/lang/en_us.json`＋`zh_cn.json`＋`zh_tw.json`（新字串三語同步）
10. 新 harness：`src/test/java/com/skps9/packai/logic/AskFactsRoutesCheck.java`（＋`ItemInfoCoverageCheck.java` 如需）
11. `tools/check_item_info_coverage.py`（儀器；已修分子§0.1）
12. `tools/cardplace_sampler.py`（新增 `--mode loot-only`，見 §8）
13. `tests/check_*.py`（如新閘需要）
14. `code_change_log.md`（repo `AGENTS.md:49` 要求）
- **唔准郁**：`logic/RecipeEmbed.java`／`logic/RecipeCard.java`（卡落位；A3 用 sha256 證明零改動）、`neoforge/` 樹、`config/PackAiConfig.java`（**唔加新 config key**——避開 3 lang 檔＋settings registry 測試連鎖）
- **唔准** commit／deploy／開遊戲（實作者唔准）；**唔准**改 prompt 文案當修法（所有修法要結構性）

## §7 還原方案（已核實）
- baseline：`%TEMP%\p0_baseline_<ts>.txt`（status 126 項）＋`baseline_sha_<ts>.txt`（120 檔 sha256）
- 改動前逐檔備份（`%TEMP%\p0_backup_<ts>\`）；**嚴禁裸 `git checkout -- .`**（126 項 dirty 唔係本 plan 造成）
- 還原步驟：`git checkout -- <白名單檔案>`（逐檔）＋`python research/gen_tmp_check.py` 重生 `tmp-check.gradle`
- 驗證還原成功：`git status` 回到 126 項、`RecipeEmbed.java` sha256 不變

## §8 Sampler 工作（A5 前置，v1 講錯）
`tools/cardplace_sampler.py` **冇** `--mode`（只有 `--per-cat/--random-n/--seed/--pinned`；docstring 寫 pools/draw 兩個 subcommand），而且 `pools` 由 jar assets 抽 item model／lang，**唔含 loot table 訊號**；GAME 預設係 `C:/Users/skps9/Documents/packai_dev_game`（唔係 FTB 沙盒）。
- **要做**：新增 `--mode loot-only`（＝「喺 pack loot tables 出現 且 冇 craft 產出」）＋支援 `PACKAI_GAME_DIR` 指去沙盒
- **紀律**：每次 run **重新隨機抽**（新 seed 並記錄），唔准重用上輪物品

## §9 驗收（每項 pre-registered，唔准事後改）
| # | 項目 | 通過標準 |
|---|---|---|
| A0 | headless facts 管道 | fixture item prompt **含** `chests/village/moon/blacksmith`；負控（cap=1）**必跌** |
| A0b | 真機（FTB） | 氧氣罐答案講到「Moon village blacksmith chest」；**唔准**再出「no loot … indexed」 |
| A1 | headless routes | route entries 格式逐字對 expected（station／table id） |
| A2 | headless 清單 | 必答清單對 fixture 生成正確（逐類別 expected） |
| A3 | 卡落位回歸 | `tests/check_ask_card_fallback.py` 保持綠；`RecipeEmbed.java`／`RecipeCard.java` **sha256 零改動** |
| A4 | 真機 coverage | detectable 子集 **82/91 → 100%**；逐類別目標：tags 11→0、guide 5→0、usages 1→0、loot 1→0（ALL 類別 75% 記錄但唔做閘） |
| A5 | 真機 worldgen | 隨機抽 5 件礦物 ⇒ Y／礦脈／維度／生態域齊；5 件生態域／結構／維度限定 ⇒ 來源齊 |
| A6 | headless guide | 無關查詢回空（唔准回 `how_to_enchant`） |
| A7 | 全回歸 | 已註冊 Java harness **50/50**、python 閘 = baseline **122 綠＋1 已知紅**（113? 見註） |
| A8 | 負控 | 拆 D2 ⇒ coverage 必跌；拆 D0 ⇒ A0 必紅 |
- 註：python 閘 baseline 以 `%TEMP%\gate_baseline_20260919.txt` 為準（123 檔，1 個已知紅）；`tmp-check.gradle` 尚未 add ⇒ 唔准寫入 repo

## §10 成本／風險／版本紀律
- 成本：實作（AI 執行）＋真機 run ≈ 2 × 12 分鐘遊戲／約 100–150 萬 tokens；半價時段做
- 風險：① caps 令新 block 被截（D0.3 預算表＋A0 防）② D2 補完打亂卡落位（A3 防）③ worldgen 注入令 facts 爆（D0.3）④ 跨 pack 差異（A5b 主包對照）
- 版本紀律：任何結論一律標 **MC 1.19.2／Forge 43.4.5／pack＋mod 版本＋證據檔案**；1.12／1.20.1 來源唔准套用
- JAR 通道今日（19 條 trace）**零輸出**係 P0 級事實；因 mod 未對外發佈，按 SK 2026-09-19 決定**行 plan 流程**（唔做緊急 hotfix）
