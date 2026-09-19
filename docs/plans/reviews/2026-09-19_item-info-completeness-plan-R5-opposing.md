# R5 反方 review — item-info-completeness plan（v2 body ＋ §V3 ＋ §V4 ＋ **§V5 覆寫**）

- 角色：**反方（adversarial）**。審查對象：`docs/plans/2026-09-19-item-info-completeness-plan.md`（v2 body ＋ §V3 ＋ §V4 ＋ **§V5 覆寫**，commit `5697fd6`）
- 環境（全部親測）：MC **1.19.2** / Forge **43.4.5** / pack **FTB Skies Expert**（`packai_sandbox_ftb`）/ packai `mod_version=0.2.3`
- 證據（本輪親跑，全部可重跑）：
  - jar-cache `<ftb sandbox>/minecraft/config/packai/jar-cache/`（**360 檔 = 359 shard ＋ manifest.json**；manifest `jars`=358；總 refs **19,292**＝U 7,741／R 6,391／**L 5,160**）
  - **記憶體合併模擬器**（逐字 mirror `JarLightIndex.mergeFacts`：去重、`MAX_FACTS_PER_ITEM=8`、manifest jar 次序）→ 6,842 items／14,634 codes
  - 全 `mods/*.jar`（359 個）掃 `data/*/dimension/*.json`、`worldgen/**`、loot `"type"` id；主包 `AI_test_NFWC_DIM` 對照掃描
  - `kubejs/data/minecraft/loot_tables/chests/end_city_treasure.json`（13,590 B，合法 JSON，內含 `"name": "tetra:dragon_sinew"`）
  - `forge/1.19.2/src/**`（`JarLightIndex`／`AcquireAskTool`／`AskToolContext`／`AskToolEnv`／`AskToolLoop`／`LootForwardIndex`／`WorldgenIndex`／`WorldgenFacts`／`ReplyLang`／`PackAiConfig`／`AskEngine`／`AskService`／`AiAssistantScreen`）、`src/test/**`、`tmp-check.gradle`、`research/gen_tmp_check.py`
- 結論：**正方 4 : 反方 6（go = false）** —— §V5 有**兩條真修正**（R4 B2 逐字真值 ✅、B1 嘅 `dim` 由「無源」變成「有確定性來源」✅），但同時**自己引入 3 條新矛盾**＋**用「重述要求」代替「交出 artifact」**（A2／A1／A0-loose／A0b／A6 五處 R4 flip 未交貨）。第 5 輪已超 repo 3–4 輪上限 → 見 §F 停手報告。
- 本輪只讀；唯一寫入檔＝本報告（原 127 項 dirty 未變，本報告為第 128 項 untracked；`forge/`／`neoforge/` 零改動）。

---

## §A 我親核而 §V5 成立嘅部分（先講，唔想冤枉）

| §V5 主張 | 我嘅獨立核實 |
|---|---|
| V5.2 `tetra:dragon_sinew` 只有 `L\|entities/ender_dragon_extended`；A0 expected 換成 `oxygen_tank`／`crystal_heart`／`dragon_sinew` | ✅ **R4 B2 全清**。我逐條由記憶體圖取出：`ad_astra:oxygen_tank → ['L\|chests/village/moon/blacksmith','U\|crafting_shaped\|ad_astra:oxygen_loader']`；`tetra:dragon_sinew → ['U\|crafting_shapeless\|art_of_forging:life_fiber','L\|entities/ender_dragon_extended']`；`artifacts:crystal_heart → ['L\|artifact','L\|inject/chests/bastion_treasure','L\|inject/chests/end_city_treasure','L\|inject/chests/pillager_outpost']`。三條 expected **逐字真** |
| V5.1 `dim` 之新資料源（D5c：索引 `data/*/dimension/*.json` ＋ biome→dimension 邊） | ✅ **方向可行**（詳見 §A-1）。R4 嘅「`dim` 零源」已被解，而且唔靠 namespace 猜 |
| V5.1 fixture 更正：`minecraft:diamond_ore` 出唔到 `W\|`，改用 5 個真 mod 礦 | ✅ 5 個 ad_astra 礦我逐個核：placed_feature 檔**全部存在**，而且每個都有 biome 歸屬（見 §A-1 表） |
| V5.3 A0 harness 前提：manifest／`mods/`／`scanModJars` | ✅ 三項**全部成立**（`loadAllShards` 只認 manifest：`JarLightIndex.java:395,407`；`ensure()` 要 `mods/` 目錄：`:74-80`；`PackAiConfig` 預設 false：`:447` define、`:919` accessor，沙盒 `config/packai-client.toml:140 scanModJars = true`） |
| V5.4 預算真值＝動態（12／3） | ✅ `acquireLineBudget`＝`wantsFullAcquire ? 12 : 3`（`AskToolContext.java:115-122`），`AcquireAskTool.java:42` 用 `clipAcquireLines(..., args.question)`；`clipLines` 語意＝**保留最前 N 行**（`:125-143`） |
| V5.6「真裸 key 係 `artifact`(35)」 | ✅ 逐字正確（`L\|artifact` 35 條，來自 artifacts 模組）——R4 B6 已修正 |
| V5.7「`WorldgenIndex` 冇 disk cache」 | ✅ `:18` 逐字 `Jar scan is capped (no disk cache)`；`MAX_JARS=400`（`:28`） |
| V5.7「`doLookup` miss 會出 miss line」 | ✅ `WorldgenFacts.missLine`（`:91-98`）＝`[WORLDGEN] this pack has no indexed worldgen for: <q>` |
| V5.8 A5b 復原 | ✅ A5b 可行：主包 `AI_test_NFWC_DIM` 231 個 mod jar 內有 `dimension` 15／`worldgen/biome` 28／`placed_feature` 318 |
| V5.9 刪錯句、A6 條件式移 P1 | ✅ 兩處舊錯句確實仍在（見 §D-7 對 A6 嘅異議） |

### §A-1 `dim` 到底可唔可確定性推導？——**可以，但只限 4 個 namespace、11 個 biome，而且有 1 個歧義**

我親掃 359 個 `mods/*.jar`：

```
data/*/dimension/*.json        = 14 檔（ad_astra 11、compactmachines 1、createteleporters 1、l2library 1）
data/*/dimension_type/*.json   = 16 檔
data/*/worldgen/biome/*.json   = 11 檔（ad_astra 10、compactmachines 1）
loose roots（datapacks／kubejs-data／openloader／overrides）= 0 個 dimension／worldgen JSON
mods／loose 內 **冇** data/minecraft/dimension/overworld.json（原版維度唔在 mods）
```

由 14 個 dimension JSON 抽 `generator.biome_source` 得到 **biome→dimension 邊（11 條）**：

| biome | dimension | 歧義 |
|---|---|---|
| `ad_astra:lunar_wastelands` | `ad_astra:moon` | — |
| `ad_astra:martian_wastelands`／`martian_canyon_creek`／`martian_polar_caps` | `ad_astra:mars` | — |
| `ad_astra:glacio_snowy_barrens`／`glacio_ice_peaks` | `ad_astra:glacio` | — |
| `ad_astra:venus_wastelands`／`infernal_venus_barrens` | `ad_astra:venus` | — |
| `ad_astra:mercury_deltas` | `ad_astra:mercury` | — |
| **`ad_astra:orbit`** | **6 個 orbit 維度**（earth／glacio／mars／mercury／moon／venus_orbit） | ⚠️ **AMBIGUOUS** |
| `createteleporters:pd_biome` | `createteleporters:pocket_dimension` | — |

而 `ad_astra:moon_desh_ore` 嘅鏈係**逐環真**：

1. `data/ad_astra/dimension/moon.json` 逐字：`{"type":"ad_astra:moon","generator":{"type":"minecraft:noise","settings":"ad_astra:moon","biome_source":{"type":"minecraft:fixed","biome":"ad_astra:lunar_wastelands"}}}` → **fixed ⇒ 單一 biome，無歧義**
2. `data/ad_astra/worldgen/biome/lunar_wastelands.json` 嘅 `features` 真含 `ad_astra:moon_desh_ore`（我 parse 確認）
3. `WorldgenFacts.formatMatches` 有反向鏈出 `[WORLDGEN] placed_feature ad_astra:moon_desh_ore in biome ad_astra:lunar_wastelands`（`:637-646`）
4. `data/ad_astra/worldgen/placed_feature/moon_desh_ore.json` **存在**（所以 `lookup("ad_astra:moon_desh_ore")` 打得到 placed 命中）

⇒ **`ad_astra:moon_desh_ore → ad_astra:moon` 係確定性可推（唔係發明）**。5 個 A5 fixture 全部同款：

| fixture | biome（我由真 biome JSON 抽） | dimension | placed_feature 檔 |
|---|---|---|---|
| `ad_astra:moon_desh_ore` | lunar_wastelands | moon | ✓ |
| `ad_astra:mars_ostrum_ore` | martian_wastelands／canyon_creek／polar_caps | mars | ✓ |
| `ad_astra:glacio_copper_ore` | glacio_ice_peaks／glacio_snowy_barrens | glacio | ✓ |
| `ad_astra:venus_diamond_ore` | venus_wastelands／infernal_venus_barrens | venus | ✓ |
| `ad_astra:mercury_iron_ore` | mercury_deltas | mercury | ✓ |

**但三個範圍事實仍未寫入 plan**（詳見 B1）：① 全包只有 11 個 biome 有 dimension 邊（overworld 類**永遠冇**）；② `ad_astra:orbit` 有 6 個 dimension＝真歧義，plan 冇 tie-break 規則；③ plan 冇寫「推唔到 dim 時」嘅行為（略去？`dim=unknown`？當冇命中去 `missLine`？）。

---

## §B Blockers（每條要一個 flip 才翻）

### B1（HIGH）V5.1 嘅 D5c 冇指名**宿主檔／記錄結構／歧義與未知嘅規則**，而 A5 係「逐字 expected」→ 呢條 scope 決定今日仍然落唔到地

- §V5.1 逐字：「**新增 D5c**：索引 `data/*/dimension/*.json`（＋biome→dimension 邊）⇒ `dim=` 變成**可推導**」。但——
  - **冇指名檔**：`WorldgenIndex` 現時嘅 `WorldgenFacts.Store`（`WorldgenFacts.java:32-42` 嘅 7 個 record）**冇維度欄位**；要加 `dimension` 資料＝要動 `WorldgenFacts` 嘅 record／parse／`formatMatches`（白名單 §6 第 5 項只寫「（D5 觸發詞／注入）」）。plan 冇寫邊個檔、邊個 record、邊條 query 回 `dim`。
  - **歧義未定**：`ad_astra:orbit` 同時屬 6 個 orbit 維度（我實掃）。若 plan 照「biome→dimension 邊」直落，查 `ad_astra:orbit` 類物品會**任選或全印 6 個**；plan 冇寫 tie-break（正解應係「多於一個 ⇒ 唔准填 dim」＝沿用自己「缺欄位唔准填」原則）。
  - **未知行為未定**：全包 11/11 有邊嘅 biome 之外，`minecraft:overworld` 系（無 dimension JSON 在 mods／loose，我掃過 = 0）以及所有未映射 biome ⇒ dim 未知。plan 只寫「**若 SK 唔批 D5c 範圍** ⇒ A5 只斷言 y／size／count／biome」，**冇寫「批咗 D5c 但該 biome 無 dimension 資料」** 呢個（佔絕大多數）狀態。
  - **A5 行係條件式**：§V5.8 A5 寫「`y/size/count/biome`（＋`dim` 若 D5c 批）」＝**同一行有兩個互斥 expected**，同 §9「每項 pre-registered，唔准事後改」直接衝突（未批＝A5 係第二個版本，等於事後改）。
- **FLIP**：① 明文寫死宿主檔＋新增欄位（例：`WorldgenIndex` 掃 `data/*/dimension/*.json` → `WorldgenFacts.Store.dimensionOf(biomeId)`；`dim` 多於一個候選 ⇒ **唔准填**；全無 → **唔填且唔出 miss line**）；② A5 行**改成兩條獨立 row**（A5-d（真值可導）／A5-nodim（SK 未批版）），唔准用「若…則…」；③ 附 1 個 fixture 嘅逐字 expected（e.g. `moon_desh_ore → dim=ad_astra:moon|biome=ad_astra:lunar_wastelands|y=-80..80|size=9|count=9`）。

### B2（CRITICAL）§V5.4 兩個 pin 都被真數據打死：「jar route refs **永遠唔會被 clip**」＋「只承諾『**有 loot ref 就必出**』」

我唔係推理，係**重建 `byItem` 記憶體圖**（逐字 mirror `mergeFacts`：dedupe、`MAX_FACTS_PER_ITEM=8`、manifest 次序）再量度：

| 量度 | 值 |
|---|---|
| shard 原始 L refs | **5,160**（plan 用嘅基數） |
| **記憶體 L occurrences（＝routeLinesForItem 真正讀得到）** | **2,418（46.9%）** |
| 記憶體 L／item 分佈 | 1→754、2→154、3→62、**4→61、5→28、6→26、7→18、8→63** ⇒ **>3 條 L 嘅 item＝196 個** |
| 8 codes 飽和 item | 422 個（其中 **225 個飽和但零 L**） |
| **「raw 有 L、記憶體零 L」嘅 item** | **6 個**：`minecraft:ender_eye`／`end_stone`／`crafting_table`／`piston`／`hopper`／`redstone_torch` |

- 「**永遠唔會被 clip**」：`clipLines` 係**保留最前 N 行**（`AskToolContext.java:125-143`），預算對非 craft/acquire 問題係 **3**（`:115-122`）。jar refs 排最前只保證**頭 3 條**；**196 個 item 有 >3 條 L**（63 個 saturate 到 8）⇒ 照字面「jar 先、loose 後」之下，jar refs 一樣會被截（3／8 或 3／4）。要真正做到「永不 clip」就**必須另開 seam**（例如 `AskToolContext` 新方法：只 clip loose 部分、jar 行 bypass，輸出**超出** `MAX_ACQUIRE_LINES_SLIM/FULL`）——而 plan **冇指名呢個 seam**，亦冇寫「輸出長度可以超出預算」；`AskToolContext` 類頭逐字係 `hard per-section char/line budgets`（`:14`）＝同新行為相衝。
- 「**有 loot ref 就必出**」：**6 個真 item 反例**（上面表）——佢哋 raw shard 有 L，但 `mergeFacts` 嘅 8-code cap 已被 R／U 食滿，記憶體根本冇 L。而 `minecraft:crafting_table`／`ender_eye`／`hopper` 係**最常問**嗰批。要讓呢句成立，就要**改寫入側 cap 政策**（`JarLightIndex.java:279`／`:439`）＝超出 plan 自己宣稱嘅範圍，而且 plan 冇提。
- 附帶精確度錯：§V5.4 寫「記錄 scan-time 截斷：`MAX_FACTS_PER_ITEM=8`（`:279,:439`）、`MAX_ASK_LINES=4`（`:59,:128`）」——`:59` 係**常數宣告**，`:128` 係 `factsForAsk` 嘅**讀取期** cap（證據：`:127-129` 係 `for` 迴圈內嘅 `lines.size() - 1 >= MAX_ASK_LINES`），**唔係 scan-time**；而 `routeLinesForItem` 係新函數，會唔會沿用 `MAX_ASK_LINES` 完全冇寫。
- **FLIP**：① 改寫承諾為**可量度嘅形式**（例：「記憶體每 item ≤8 codes；**頭 3 條 L 必出**」），並列出 6 個反例 item 做**已知限制**（或明文寫要改寫入 cap）；② 明寫 merge seam（檔名＋方法＋語意）＋輸出行數上界（可否超預算）；③ 加兩條**有鑑別力**嘅負控：`(a)` fixture 用一個 >3 條 L 嘅真 item（e.g. `artifacts:crystal_heart` 有 4 條 L）＋**非** craft 問句 ⇒ 斷言第 4 條 L 唔准消失；`(b)` 用 `minecraft:hopper` ⇒ 斷言「有 L ref 就必出」係被推翻定被修正。

### B3（HIGH）§V5.3 嘅「指名步驟」仍然唔可照跑：欠 `PackAiConfig.setScanModJars(true)`、欠 MC bootstrap、而 `AskToolEnv` 係第一個要 MC 嘅 logic harness

- 我 grep 全 `src/test`：`new AskToolEnv(`／`AskToolLoop.bindEnv(`／`AskToolEnv.current()` ＝ **0 命中**（R4 已核，本輪重核不變）。所以新 harness 要自己由零搭：
  1. `AskToolEnv` 建構子＝**5 參數**：`AskToolEnv(ItemStack stack, PackIndex index, Path gameDir, List<String> scanners, ItemRef held)`（`AskToolEnv.java`，ctor 內第一句就 `stack == null ? ItemStack.EMPTY : stack`）⇒ 要 **MC class**；`AskToolEnv.current()` 讀 `AskToolLoop.env()`（`AskToolLoop.java:99`），而 ENV 係 **ThreadLocal**（`:78`）⇒ 要 `AskToolLoop.bindEnv(Object)`（`:103`）**在同一 thread**。
  2. **`scanModJars` 唔止係真機前設**：`JarLightIndex.ensure` 第一句就 `if (!PackAiConfig.scanModJars()) return;`（`:70-72`），`factsForAsk` 亦一樣（`:111-113`）⇒ **headless A0 都必須令佢回 true**。setter **存在**（`PackAiConfig.setScanModJars`，`:923`），但 plan 由頭到尾冇寫呢個 call，而且 setter 內係 `SCAN_MOD_JARS.set(enabled); SPEC.save();`（`:924-925`）⇒ **會寫 config 檔**（headless 下 spec 未 load 時 `ConfigValue.set` 可能拋／寫落不明位置）——plan 冇寫、亦冇 QC。
  3. `ItemStack` ⇒ 要 `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();`（先例 `ItemRefCheck.java:14-15`，亦係**唯一**用 MC 嘅 logic harness）。而 `AskToolLoopCheck.java:19` 檔頭逐字寫 `Hybrid loop branches. Run with -ea. No Minecraft.` ⇒ 新 harness 會**破壞 logic harness 家族慣例**，plan 冇寫呢個成本／步驟。
- ⇒ A0 仍然可以「因為 harness 未 setup」而紅（同 R4 B3 同一缺陷），即「pre-fix 必紅」嘅**歸因**依然唔成立；plan 只係把要求寫得明確咗，冇交步驟細節。
- **FLIP**：① A0 明文寫死 bind 序列：`Bootstrap.bootStrap()` → `PackAiConfig.setScanModJars(true)`（＋其 `SPEC.save()` 副作用處理／或用反射／或明文「接受寫檔」）→ `new AskToolEnv(stack, idx, fixtureGameDir, List.of(), null)` → `AskToolLoop.bindEnv(env)` → `new AcquireAskTool().run(args)`；② 或改斷言**唔要 env 嘅純函數 seam**（則該 seam 宿主必須入白名單）；③ 寫明 fixture 目錄內容（`manifest.json` ＋ 空 `mods/` ＋ 3 個 shard）。

### B4（HIGH）§V5.5 用「要求」代替「artifact」：D2 宿主仍未**選定**、A2 三行 expected **仍然留白**

- §V5.5 逐字要求「宿主檔……**寫死選一個**」、介面要收 `(answerText, availableCategories)`、「A2 三行 expected 要**逐字寫出**（類別＋字串＋lang key），否則違反 pre-registered」。**但呢三件事 §V5 冇做**：宿主係「`AskJeiHints.java:274` **或** `AskEngine`」（依然二選一未定）、A2 三行字串／lang key 名**一個字都冇貼**。
- ⇒ R4 B5a／B5b／B5c 三條**照樣開着**；§V5.8 A2 嘅 expected 欄仍寫「**3 行逐字（V5.5）**」＝指向一段冇 artifact 嘅要求。呢個係「重述 flip condition」而唔係「翻轉 flip condition」。
- 附帶：如果選「純函數 seam」路線（B3 flip ②），`InfoCompleteness.java`／`InfoCompletenessCheck.java` 只見於 **§V4.5 第 41 行** 嘅一句白名單補充，而 §V5 開頭一句「**覆蓋 §V4.1–§V4.5**」令佢生死不明；§6 正文（`:165-181`）嘅第 10 項只列 `AskFactsRoutesCheck.java`（＋`ItemInfoCoverageCheck.java` 如需）。實作者睇 §6 會**唔敢開 `InfoCompleteness*.java`**（或反過來越界）＝白名單與交付物唔一致。
- **FLIP**：① 貼出 A2 三行逐字 expected（類別｜人化字串｜lang key 名）＋宿主檔名唯一化；② §6 白名單**補列** `logic/InfoCompleteness.java`、`logic/InfoCompletenessCheck.java`（或明文講明 §V4.5 第 41 行仍然生效）。

### B5（HIGH）§V5.6 三個問題：`blocks/*` 嘅「決定」係 no-op、deny-list 數字錯、而 deny-list 自己**製造一個新嘅「明明有索引卻答冇」** 班

**(a) `blocks/*` 決定＝重述既有行為，冇答 rule #22 嘅真問題**
- §V5.6 逐字：「`blocks/*`（2,989＝58%）**只保留「非自身掉落」**——即跳過 `LootForwardIndex.isTrivialBlockSelfLoot`（`:106-120`，**scan time 已用** `JarLightIndex.java:242`）嘅項」。我核實：`isTrivialBlockSelfLoot` 只剔「**loot table path == `blocks/<自己>`**」一種（`:106-120` 逐字 `lootPath.equals("blocks/" + itemPath)`），而佢**已經**在 `JarLightIndex.parseLootJson` 掃描期套咗（`:242-244`），`PackIndex.java:1316` 亦套。⇒ plan 講嘅「決定」**等於描述現狀**，0 改動；真正待答嘅係 rule #22。
- rule #22 逐字（`packai.reply.loot_noise_skip`，`en_us.json`，`ReplyLang.java:1233-1236` 拼入 `factCheck`，再經 `LlmClient.java:380,481` 入 system prompt）：
  `22. Do not cite default block loot (mining a placed block drops itself) as usage or obtain trivia. Only mention block pickup when FACT says it is special (cannot mine back, different drop, silk touch required, etc.).`
  ⇒ 規則嘅**觸發條件係「FACT 話佢 special」**，但 jar 通道出嘅人化語句係 `packai.reply.jar_loot`＝`loot: %s`（`JarLightIndex.formatFact` 嘅 `'L'` 分支，`:264`），**冇任何 specialness marker**；另一種人化係 `packai.reply.loot_table_obtain`＝`Loot table: %s`（`en_us.json:472`，鬆散路徑用）。plan（a）禁改 prompt、（b）冇改格式 ⇒ 模型見到 `loot: blocks/foo` 時**無法判斷係唔係 special**，而 rule #22 要求佢「只有 FACT 話 special 才准提」。§V5.6 聲稱「同時滿足 SK『挖方塊拎到』要求＋既有 prompt 規則 #22」——**呢個共存機制今日喺格式層唔存在**。
- 數字亦係 raw 層：`blocks/*` **raw 2,989 條（1,281 個 distinct key）**，但**記憶體只有 781 occurrences**（＝可交付 L 嘅 32%），其中 50 條（6.4%）掛喺**非 item 嘅 key**（loot function／condition／entry type id，見 (c)）。plan 用 58% 去論證「最大桶」，但可交付層嘅比例係 **781/2,418＝32%**。

**(b) deny-list 數字自相矛盾**
- §V5.6 列：`empty`(2)／`artifact`(35)／`loot`(11)／`items/drinking_hat`(2)／`chest/example_random_source_loot_table`(3)／`entity/treasure_goblin`(18)／`advancements/shader_epic`(9)，然後寫「約 **127**/5,160＝2.5%」。**呢 7 個加起來＝80，唔係 127**（80/5,160＝1.55%）。而 `advancements/shader_epic` 嘅**單 key** 真值係 **3**（`advancements/*` 家族 9＝3 個 key：`shader_epic`／`shader_masterwork`／`shader_rare`）⇒ §V5.6 把 V4.4 嘅**家族計數**當**單 key 計數**，同節仲同時寫「更正」字眼。
- 可交付層對照（我逐 key 數記憶體 occurrences）：`empty` 1／`artifact` **35**／`loot` 11／`items/drinking_hat` 2／`chest/example_random_source_loot_table` 2／`entity/treasure_goblin` **12**／`advancements/shader_epic` **1** ⇒ 合計 **64/2,418（2.6%）**。plan 嘅「2.5%」剛好撞啱，但**分子分母都係另一批數字**＝巧合，唔可以當已核。

**(c) deny-list 造出 6 個「有索引但被迫答冇」嘅 item（同 R1 原始 bug 同一 defect class）**
我逐 key 反查持有者：

| deny key | 記憶體持有人（節錄） |
|---|---|
| `artifact` | **35 個 artifacts item**（含 A0 fixture `artifacts:crystal_heart`！） |
| `entity/treasure_goblin` | `minecraft:potion`／`gold_ingot`／`enchanted_golden_apple`／`saddle`／`name_tag`／`diamond_horse_armor` ＋ `apotheosis:module` 等非 item |
| `chest/example_random_source_loot_table` | **`minecraft:stone`**、`ftbpc:random_loot_item_function` |
| `loot` | `minecraft:chest`／`quartz`／`redstone`／`emerald`／`diamond`／`ad_astra:moon_sand` … |
| `empty`／`items/drinking_hat`／`advancements/shader_epic` | `occultism:lighted_air`／`artifacts:*_drinking_hat`／`immersiveengineering:shader_bag_epic` |

**「記憶體內 L refs 全部係 deny key」嘅 item ＝ 6 個**：`minecraft:stone`、`artifacts:plastic_drinking_hat`、`artifacts:novelty_drinking_hat`、`ftbpc:random_loot_item_function`、`immersiveengineering:shader_bag_epic`、`occultism:lighted_air`。其中 `minecraft:stone` 由查方塊掉落變成**零 loot 行**，而 `AcquireAskTool` 一旦 lines 空就回 `""` ⇒ 工具回 `[TOOL_MISS] acquire empty — pack index has no loot/trade/quest/script path for 'minecraft:stone'`。**呢句同索引事實相反**——即 R1 投訴嘅同一個病（「明明有索引，卻答冇」）被 §V5.6 自己重新製造，只係今次係 plan 主動 filter 掉。plan 冇寫「deny 到清空」時嘅輸出語意。

**(d) 白名單／實作點未定**：deny-list 究竟喺 `JarLightIndex.routeLinesForItem`（讀 raw code，白名單 ✔）定 `AcquireAskTool`（要 parse raw code）過濾？plan 冇寫 ⇒ 實作者自決（drift）。

- **FLIP**：① 刪走「只保留非自身掉落」呢句（改成「現狀已如此，本 plan 不改寫入側」）；② 明寫 rule #22 嘅**共存機制**（建議：出 `blocks/*` 時必須帶「different drop」訊號，例如人化字串改用能表達特殊掉落嘅既有 lang key；否則明文寫「**唔出 `blocks/*`**，54% 桶移 P1」）；③ deny 數表改用**單 key × 記憶體 occurrences**，並修 127→64（或 80 raw）；④ 明寫 deny 導致清空時嘅行為（唔准出 `[TOOL_MISS]`／要出「有索引但屬噪音表」嘅中立句）＋ 為 6 個 item 逐個寫 expected；⑤ 指名 deny filter 落點檔。

### B6（MED）§V5.2 A0-loose 要求「指名一個 loose 檔」但**冇指名**，而**真候選檔今日就存在**（成本 30 秒）

- §V5.2 逐字：「另立 A0-loose：**指名一個 loose datapack 檔**（內容逐字含 `tetra:dragon_sinew`）測 loose 路徑（唔准混入 jar 期望）」。我實掃 sandbox 全 tree，**真檔就係**：
  - `kubejs/data/minecraft/loot_tables/chests/end_city_treasure.json`（13,590 B，合法 JSON，逐字含 `{ "type": "minecraft:item", "weight": 2, "name": "tetra:dragon_sinew" }`）
  - 另有 `kubejs/data/minecraft/loot_tables/chests/bastion_treasure.json` 同樣提到 `dragon_sinew`
  - ⚠️ **注意表路徑係 `chests/end_city_treasure`（唔係 `inject/chests/end_city_treasure`）**——正好解釋 R3 trace 見到「end city treasure」但 jar-cache 冇嗰條 `inject/*`。
- ⇒ flip 差最後一步（貼路徑＋逐字 expected）未做，A0-loose 今日**唔可照跑**。
- **FLIP**：寫死 fixture 路徑＝`<instance>/kubejs/data/minecraft/loot_tables/chests/end_city_treasure.json` ＋ expected ＝ 工具輸出含該表人化名（並寫明用邊個人化 key），並聲明 A0-loose fixture 目錄**唔准放** jar-cache。

### B7（MED）§V5.7「worldgen disk cache」係一個**冇檔案、冇白名單、冇驗收、而且前提推錯**嘅新 scope 項

- 前提錯：§V5.7 寫「首次 acquire 會觸發**同步 357-jar 掃描**……⇒ 新 scope 項：**worldgen disk cache**（同 jar-cache 同級，**背景掃描時建**），唔准 per-call 掃」。事實：
  - `WorldgenIndex.doEnsure` 已經有 **in-memory 快取**（`:53-76`：`if (ready && abs.equals(loadedDir)) return;`）⇒ **根本唔會 per-call 掃**，一個 JVM session 每個 gameDir 只掃一次（`reset()` 後才再掃）。所以「唔准 per-call 掃」係**已經成立**，唔構成新工作要求。
  - 真正存在嘅成本係「每個 session 第一次 worldgen 命中／第一次 acquire 同步掃一次」，而**冇任何背景掃描 seam**：`JarLightIndex.INSTANCE.ensure` 嘅唯一 call site 係 `AskEngine.java:251-252`（gate＝`PackAiConfig.scanModJars() && !isReady()`），位置喺 `AskEngine.ask(...)`（宣告 `:201`）內；`AskService.runAsk`（`:320`／`:2428`）**冇任何 executor／thread offload**（grep `submit|Executor|executor` ＝ 0），而 `AiAssistantScreen.java:428` 由 GUI 直接叫 `askAsync` ⇒ 「背景掃描」係 plan 由 `PackAiConfig.java:442-445` 嘅**註解文字**（`background-scan mods/*.jar`）抄出嚟嘅講法，同 code 事實唔符。
  - 如果真要嚴肅處理，正確寫法係「**首次 ask 會同步掃 mods（在呼叫 thread）**」，並指明要唔要 offload（offload 要動 `AskEngine`／`AskService`＝新 scope）。
- 而且呢個新 scope 項在 §V5.8 **冇任何 A 行**（冇斷言 cache 存在／命中／失效），在 §6 **冇檔案**（新 class 唔在白名單；塞入 `WorldgenIndex` 就超出 §6 第 5 項嘅 parenthetical「D5 觸發詞／注入」），亦冇設計（cache 路徑／fingerprint／失效）。§V4.1 曾要求「唔准新 config key」——一個新 cache 目錄唔算 key，但呢個係 scope 擴張，唔應以一行句子偷偷加入。
- **FLIP**：① 刪走 disk cache 項（因為「per-call 掃」係假前提），改成「明寫首次同步掃 cost ＋ 觸發時機（`looksLikeQuery` vs 無條件 acquire）」；或 ② 若堅持要 cache ⇒ 當**獨立交付物**：指名新檔＋入白名單＋加 A 行（cache 建／讀／失效 三條斷言）＋明寫唔動 config key。

### B8（MED）A0b／A1／A6 三個「留白 expected」本輪**原封不動**

- **A0b**：§V5.8 仍只寫「講掉落表人化名；唔准『no loot … indexed』；先驗 `scanModJars=true`」。三個洞：(i) 「人化名」有**兩個**候選形式（`packai.reply.jar_loot`＝`loot: %s`：`JarLightIndex.java:264`；`packai.reply.loot_table_obtain`＝`Loot table: %s`：`en_us.json:472`）——plan 冇 pin 邊個；(ii) 禁用句冇逐字（R1 原文係 `No loot, chest, trade or fishing path … indexed`）；(iii) 冇 pin 問句 ⇒ 預算 3／12 唔定（B2）。
- **A1**：仍寫「逐 kind 逐字對；deny-list 唔准出現」，**零逐字樣本**；而 deny 斷言對今日 harness 係**空轉**——每個 deny key 只掛喺少數具體 item（`artifact` 只掛 artifacts 系、`chest/example_random_source_loot_table` 只掛 `minecraft:stone`）⇒ 唔指名 fixture item 就永遠綠（零鑑別力）。
- **A6**：§V5.9 寫「**若**寫唔到 headless seam ⇒ **移 P1**」＝在驗收表內開一個**自我豁免出口**；而「3 個已知 case」仍然冇逐字 list。呢個寫法令 A6 可以永遠消失，同 §9「唔准事後改」相衝。
- **FLIP**：A0b 貼逐字禁用句＋pin 人化 key＋pin 問句；A1 貼每個 kind 一行逐字 expected ＋ **逐個 deny key 指定一個 fixture item**；A6 二選一：貼出 seam（要係白名單內）＋3 個 case，**或者**明文由驗收表刪走（唔准留 conditional row）。

---

## §C §V5.8 驗收表逐項可證偽性（R5 現況）

| 項 | 可證偽？ | 反方判斷（附 file:line／量測） |
|---|---|---|
| A0-jar | ⚠️ | expected 三條**逐字真**（我三重核實 ✅）；但 (i) **冇 pin 問句** ⇒ 預算 3／12 未定（`AskToolContext.java:115-122`）；(ii) 排序負控「若 loose 排前 ⇒ 必紅」**造唔到**：三個 fixture 都冇 loose 行，reorder 對結果零影響 ⇒ 負控係 no-op；(iii) fixture `artifacts:crystal_heart` 自身帶 deny 條 `L\|artifact`（35 個 artifacts item 之一）⇒ 同 V5.6 嘅 deny-list 交叉但 A0 冇斷言 |
| A0-loose | ❌ | 「指名一個 loose 檔」——**冇指名**（真檔存在：`kubejs/data/minecraft/loot_tables/chests/end_city_treasure.json`）⇒ 照字面跑唔到（B6） |
| A0b | ❌ | 人化形式二選一未定、禁用句未逐字、問句未 pin（B8）；且依賴 `scanModJars=true`（已驗法 ✅） |
| A1 | ❌ | 零逐字樣本；deny 數字錯（80≠127、`advancements/shader_epic` 3≠9）；deny 斷言唔指名 holder item ⇒ 空轉；`blocks/*` 決定係 no-op 且同 rule #22 未共存（B5） |
| A2 | ❌ | §V5.5 只寫「要逐字寫出」⇒ **同一輪內自認未 pre-register**；宿主檔仍二選一（B4） |
| A3 | ✅ | 維持 R4 判斷（舊＋新「卡＋補充段」fixture；`RecipeEmbed`／`RecipeCard` sha256 零改動）。本輪無新異議 |
| A5 | ⚠️ | fixture 換成 5 個真 ad_astra 礦 ✅ 且 dim 可確定性推導 ✅（我逐個核）；但 (i) `y/size/count/biome` **逐字值冇寫**；(ii) `dim` 係「若 SK 批」⇒ **一行兩版本**（B1）|
| A5b | ⚠️ | 復原 ✅ 且主包有料（231 jar：dimension 15／biome 28／placed_feature 318）；但「同上」＝繼承 A5 嘅逐字值缺口 |
| A6 | ❌ | 保留自我豁免句＋3 case 未列（B8） |
| A7 | ⚠️ | 「forge **50/50**」已過時：`tmp-check.gradle` 由 `research/gen_tmp_check.py` **rglob `*Check.java` 自動生成**（`gen_tmp_check.py:5-14`），今日 50 個 task；本 plan 要加 `AskFactsRoutesCheck`（§6 第 10 項）(+可能 `InfoCompletenessCheck`) ⇒ 收貨時應係 **51/51 或 52/52**。python 側若加 `tests/check_*.py`（§6 第 13 項）亦會由 122 綠＋1 紅變動 |
| A8 | ⚠️ | 全部係 **ablation 級**（拆走 code ⇒ 紅）——R4 已指出 D0'' 半條**恆真**；§V5.4 補嘅「排序負控」如 A0 所述**造唔到**。冇任何 boundary 負控（>budget／deny 清空／8-code cap） |

**⇒ 今日可証偽：A3 一條；條件式／留白：A0／A5／A5b／A7／A8；照字面跑唔到：A0-loose／A0b／A1／A2／A6 五條。**

---

## §D §V5 **自身引入**嘅新矛盾／殘留（R4 已清嘅唔重提）

1. **「永遠唔會被 clip」× `clipLines` keep-first × 196 個 >3 L 嘅 item**（B2）——同一節另一句「只承諾有 loot ref 就必出」又被 6 個 item 反證（`minecraft:hopper`／`crafting_table`／`ender_eye`／`end_stone`／`piston`／`redstone_torch`）。
2. **deny-list × 原始症狀**：deny 令 `minecraft:stone` 等 **6 個 item** 嘅 loot 被清空 ⇒ `AcquireAskTool.java:43` 回 `""` ⇒ `toolMissNote` 出「no loot/trade/quest/script path」＝**R1 投訴嘅同一句子復活**（B5c）。
3. **deny 數字 × 自己嘅清單**：列出 7 項＝80，寫「約 127」；`advancements/shader_epic`(9) 係家族值（單 key 3）。
4. **raw 基數 × 可交付層**：V5.6 全節（2.5%、58%、127）都建基於 raw 5,160／19,292；但 `routeLinesForItem` 讀嘅係記憶體圖，L 只有 **2,418（46.9%）**，`blocks/*` 只有 **781（32%）**。
5. **rule #22 × §V4.1「唔准改 prompt」× 新格式**：三者未協調，而 plan 聲稱「同時滿足」（B5a）；`loot: %s` 冇 specialness 訊號，rule #22 嘅條件永遠唔成立。
6. **worldgen「背景掃描」× code**：冇 background scan seam（唯一 ensure site `AskEngine.java:251-252`，喺 ask 內；`AskService` 冇 offload）（B7）。
7. **scope 擴張 × 驗收/白名單**：worldgen disk cache 冇 A 行、冇檔、冇設計（B7）；`logic/InfoCompleteness.java`／`InfoCompletenessCheck.java` 只活在 §V4.5 一句，而 §V5 宣告覆蓋 §V4.1–V4.5 ⇒ 白名單狀態不明（B4）。
8. **`W\|` 格式 vs 現成 formatter**：§V5.8 A5 要求 `acquire` 出 `W\|…` 行，但今日 `WorldgenIndex.lookup` 回嘅係 `WorldgenFacts` 嘅**人化句**（`[WORLDGEN] placed_feature X configured=… count=… height_range=…`，`:429-443`；`… in biome Y`，`:637-646`；`configured_feature … type=… size=…`，`:418-427`）。plan 由 V4 到 V5 **都冇寫點由人化句轉成 `W\|` 結構欄**（另開 formatter？加 `Store` 訪問器？）。呢個係 A5 嘅實作前提，今日缺。
9. **V5.9 宣佈「刪 §V3.2 錯句」但冇做**：§V3.2 內「全 corpus 真 item tag id 只有 **1** 個」原文仍在（`docs/plans/…-plan.md` §V3.2），只係在 §V5.9 寫「刪」⇒ 計劃書本身自相矛盾（下一位讀者讀 §V3.2 會中招）。

---

## §E Flip conditions 總表（每個 blocker 一條）

| # | Blocker | Flip（做到即翻） |
|---|---|---|
| 1 | B1 D5c 未落地 | 指名宿主檔＋`dim` 欄位／查詢；多候選＝唔填；無資料＝唔填唔出 miss；A5 拆兩行（批／未批），附 1 個逐字 expected |
| 2 | B2 clip／承諾 | 承諾改成可量度（每 item ≤8、頭 3 條 L 必出）＋列 6 個反例；明寫 merge seam 語意與行數上界；加 (a) `artifacts:crystal_heart`（4 條 L、非 craft 問句）(b) `minecraft:hopper` 兩條真負控 |
| 3 | B3 A0 步驟 | 寫死 bind 序列（`Bootstrap.bootStrap()`→`setScanModJars(true)`（含 `SPEC.save()` 處理）→`new AskToolEnv(stack, idx, dir, List.of(), null)`→`bindEnv`→`run`）＋fixture 目錄內容；或改純函數 seam（並入白名單） |
| 4 | B4 D2 宿主／A2 | 宿主唯一化；貼 A2 三行逐字（類別｜字串｜lang key）；§6 補列 `InfoCompleteness*.java` |
| 5 | B5 blocks／deny | 刪「只保留非自身掉落」no-op 句；寫清 rule #22 共存（或明文唔出 `blocks/*`）；deny 數字改單 key×記憶體（64/2,418）；寫清 deny 清空時嘅輸出；指名 filter 落點檔 |
| 6 | B6 A0-loose | 指名 `kubejs/data/minecraft/loot_tables/chests/end_city_treasure.json`（13,590 B、含 `dragon_sinew`）＋逐字 expected＋「fixture 唔准含 jar-cache」 |
| 7 | B7 worldgen cache | 刪走 cache 項（前提假）＋明寫首次同步掃嘅 cost／觸發；或當獨立交付物（檔＋白名單＋3 條 A 行） |
| 8 | B8 A0b／A1／A6 | A0b：pin 人化 key＋逐字禁用句＋問句；A1：逐 kind 逐字＋逐 deny key 指名 holder item；A6：貼 seam＋3 case 或**永久刪 row** |
| 9 | A7／形式 | A7 改成「50＋新增 harness 數／50＋N」，並寫明新 harness 由 `research/gen_tmp_check.py` 重生；`W\|` formatter 落點指名（§D-8） |

---

## §F 評分、停手報告（第 5 輪，已超 repo 3–4 輪上限）

- **正方 4 : 反方 6 → go = false**
- **逐輪比分**：R1 2:8 → R2 3:7 → R3 4:6 → R4 5:5 → **R5 4:6**（**比分回落**）。回落唔係「反方變嚴」：本輪兩條真修正（B2 expected、B1 dim 有源）我全部核實成立；扣分係**§V5 用「重述要求」代替「交 artifact」**（A2／A1／A0-loose／A0b／A6 五處 R4 flip 只換寫法）＋**新引入 3 條矛盾**（deny-list 造出同類「答冇」；「never clip／有 loot 必出」兩個 pin 被量測打死；worldgen cache 前提假）。
- **卡死嘅載重決定（兩條，連續第 2–3 輪同一族）**：
  1. **驗收 artifact 交付**：A0-loose／A0b／A1／A2／A6 五個 expected 欄每輪都「被要求」但**從未貼出**（今次 §V5.5 自己都寫「否則違反 pre-registered」，等於自認未達）。呢個係**流程問題**：只要 plan 未貼逐字 expected，A 行就係幽靈閘。
  2. **可交付層真相**：R1–R4 一直用 **shard raw 數字（5,160 L／58% blocks／127 噪音）**論證，但交付路徑 (`routeLinesForItem` → `byItem` → `clipLines`) 見到嘅係 **2,418／32%／64**，再加 8-code cap 令 6 個常見 item 完全冇 L。plan 從未量過記憶體層 ⇒ 每輪嘅「量化」都對唔上落點。
- **最貴嘅未知（要咩數據才解得開）**：
  1. **一次真跑**：headless A0（bind env + `setScanModJars(true)` + 3 shard fixture）到底紅定綠、紅因係咩——只有真跑先分得清「harness 未 setup」同「缺陷」。（`SPEC.save()` 喺 headless 會唔會拋，亦只有真跑知。）
  2. **SK 對兩件事嘅決定**：(a) `dim` 值唔值得為 11 個 biome／4 個 namespace 開 D5c；(b) `blocks/*` 54–58% 桶要唔要出（要出＝必須改 prompt 文案或人化格式；plan 兩者都禁）。
- **按 repo 規則（`AGENTS.md`「Plan／Idea Review 上限 3–4 輪」；R4 已係第 4 輪，R5 已破限）→ 立即停手，交 SK 定。** R4 建議嘅拆分 §V5 **冇採用**，結果係「一邊加新 scope（D5c／worldgen cache／deny-list）一邊唔補舊 artifact」。我嘅建議（同 R4 一致，並收窄）：
  - **A（推薦）拆兩件，只放行細嗰件**：
    - **(a) 「jar-cache → acquire」v6（P0）**：範圍＝`JarLightIndex.routeLinesForItem` ＋ `AcquireAskTool` 合併（＋merge seam 明文）。驗收＝A0-jar（3 條真值已核）／A0-loose（指名 `chests/end_city_treasure.json`）／A0b（pin 問句＋人化 key）／A1（逐 kind 逐字＋deny 表改單 key 數字）／A7。**要同時做 §E-2/3/4/5/6/8**，全部係寫死級成本，唔使新 scope。
    - **(b) worldgen（D5/A5＋D5c＋cache）**：連 `dim` 資料源、`W\|` formatter、disk cache 一齊**明文移 P1**，做之前先問 SK 兩條（dim 值唔值、blocks 桶要唔要）。
  - **B**：唔拆，硬修 §E-1…9 再評一輪 —— 呢個係**第 6 輪**，違反 repo 上限，我唔建議。
  - **C**：照做 —— 接受 dim 缺席／deny 清空 6 個 item／`blocks/*`（54%）出得但同 rule #22 未協調／A2 事後填 expected。咁會令 A5／A2 兩個真機 run 白做，並且**親手製造一個同 R1 同類嘅「答冇」regression**（`minecraft:stone`）。我明確反建議。
- **反方對 R4 嘅自我更正**：R4 §B2 講「`tetra:dragon_sinew` 呢個 case 來自鬆散 datapack」——本輪我**搵到真檔並證明**（`kubejs/data/minecraft/loot_tables/chests/end_city_treasure.json`，`"name": "tetra:dragon_sinew"` weight 2），R4 講法成立，但**表路徑係 `chests/end_city_treasure` 唔係 `inject/chests/…`**——§V5.2 若照 R4 字面寫 expected 會再錯一次。

（本輪只讀：無 commit、無 deploy、無開遊戲、未改 `forge/`／`neoforge/`；`git status --porcelain` 127 項＋本報告 1 項。）
