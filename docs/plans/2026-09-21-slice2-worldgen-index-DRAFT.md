# Plan 底稿 — Slice 2：世界生成索引（block → 結構／群系／礦脈）

- 日期：2026-09-21
- 作者：JARVIS（SK 指示：「yes, and do some research, I think we still missing some of that」）
- 狀態：**DRAFT（等外部 research 回填 §4 來源／§6 對照）**；未 review、未實作
- 相關：Slice 1（`2026-09-21-player-text-humanize-slice1.md`）、Slice 1b（mine-only／老實句收緊，另開）

## 1. 玩家問題（SK 原話）

「not a loot list or what —— it spawn when world gen」（例：哭泣的黑曜石 = 廢墟傳送門結構生成）。
即：**玩家問「呢件嘢喺邊度搵到」時，要答得出「世界生成位置」**，唔限於合成／掉落表／任務。

## 2. 現況（本機實測，2026-09-21 22:5x）

**已經有嘅**
- `WorldgenIndex` / `WorldgenFacts` / `WorldgenLookupAskTool`（`logic/`）**已存在**，覆蓋路徑：
  `tags/worldgen/**`、`worldgen/structure_set/*.json`、`worldgen/structure/*.json`、
  `worldgen/configured_feature/*.json`、`worldgen/placed_feature/*.json`、`worldgen/biome/*.json`、
  `dimension/*.json`、`forge|neoforge/biome_modifier/*.json`
- → **礦脈（ore）路線已經有**：`configured_feature type=minecraft:ore` ＋ `placed_feature` ＋ biome（`WorldgenIndex.keepOreRoute:106-125`）
- 掉落表（`LootForwardIndex`／`JarLightIndex`）、JEI 配方、任務、KubeJS 機制、Patchouli 書（各自 scanner）

**缺口（SK 直覺正確，code 實測冇覆蓋）**

| # | 資料源 | 路徑 pattern（1.19.2） | 答得到咩 | 現況 |
|---|---|---|---|---|
| G1 | 結構模板 palette | `data/<ns>/structures/**/*.nbt`（gzip NBT，palette 有方塊清單） | 「呢個方塊喺 XX 結構自然生成」 | ❌ 冇掃 |
| G2 | Processor 轉換 | `data/<ns>/worldgen/processor_list/*.json`（例 `block_age`：obsidian→crying_obsidian；`block_rot`、`gravity`） | 補 G1 嘅動態方塊（哭泣的黑曜石案例） | ❌ 冇掃 |
| G3 | Template pool（jigsaw） | `data/<ns>/worldgen/template_pool/*.json` | 邊啲模板組成邊個結構／邊個生物群系 | ❌ 冇掃 |
| G4 | 工具需求（mineable tags） | `data/<ns>/tags/blocks/mineable/*.json`、`needs_*_tool.json` | 「要用咩鎬／咩等級挖」 | ❌ 冇掃 |
| G5 | 村民交易／豬布林以物易物（如 JEI 冇覆蓋） | 多數由 JEI 顯示；資料源待 research 確認 | 「由村民／豬布林換到」 | ⚠️ 靠 JEI（待確認） |
| G6 | 釣魚／考古／其他 gameplay loot | `data/<ns>/loot_tables/gameplay/**` | 「釣魚／考古拎到」 | ⚠️ 部分（LootForwardIndex 讀 loot_tables） |
| G7 | **礦物方塊 ↔ 掉落物 join**（SK 2026-09-21 追問） | `configured_feature` 嘅 `config.targets[].target`（＝礦石方塊 id）＋ 掉落表 `blocks/<ore>` → 寶石 ＋ `placed_feature.height` | 「挖 ruby 礦石就會拎到 ruby（生成於 X，Y 範圍、需咩工具）」 | ❌ **根因：`WorldgenFacts.Configured` 只存 `(id, type, size)`，冇抽 `targets`**（`WorldgenFacts.java:42`）→ 只可靠 feature id 名撞彩；`Placed` 有 `heightRange` 但 key 係 feature id |

**G7 詳解（mod 礦物 case）**：玩家問一件「只可以挖礦拎到」嘅 mod 寶石，正確答案鏈＝
`寶石 item` ←（掉落表 `blocks/<ore>`，非 self-loot，已索引 ✅）→ `礦石方塊` ←（`type=minecraft:ore` feature 嘅 target，**未抽 ❌**）→ 生成高度／群系（已索引 ✅，但 key 係 feature id）→ 工具需求（`tags/blocks/mineable/*`＋`needs_*_tool`，**未掃 ❌**）。
→ 補齊之後出句應該係：「挖 **ruby 礦石**（生成於 <群系>，Y <a>–<b>，需 <工具>）就會拎到 ruby」。

**G7 spike 已驗證（2026-09-21，唯讀）**：`configured_feature.config.targets[].state.Name` **真係礦石方塊 id**，實例：`iceandfire:sapphire_ore`、`goety:jade_ore`、`tetra:geode_ore`、`twilightforest:legacy_redstone_ore`、`unusualprehistory:amber_fossil_ore`。→ 抽取成本極低（只係多讀一個欄位），但**現行 `WorldgenFacts` 冇讀**。

| G8 | **純 Java code 生成嘅 mod**（SK 舉 AE2 為例，2026-09-21） | **冇任何資料檔**（實測：`appliedenergistics2-forge-12.9.x.jar` → `.nbt` = **0**、`worldgen/*.json` = **0**；生成邏輯喺 `appeng/init/worldgen/InitStructures.class`／`InitBiomes.class`） | 「AE2 隕石／礦喺邊度生成」 | ❌ 掃唔到（要另一條路：mod 自帶 JEI plugin／guide 文字、內建人工表、或明確標示「通用知識，非包內」） |

**G8 影響**：所有「資料檔掃描」方案對呢類 mod 完全無效（AE2 係大頭）。plan 要分兩條路：**A 路＝資料檔（模板／worldgen／tags）**；**B 路＝非資料檔來源**（JEI plugin 文字、Patchouli、內建表、通用知識並要標明）。

**G8 覆蓋率實測（2026-09-21，SK 提「冒險類 mod 也有」→ 我掃咗三個包，唯讀）**

| mod（1.19.2 冒險／內容類） | `.nbt` 模板 | worldgen JSON | 判定 |
|---|---|---|---|
| DungeonsArise | **650** | 244 | A |
| blue_skies | **569** | 19 | A |
| dimdungeons | 363 | 1 | A |
| Structory | 238 | 94 | A |
| YungsBetterDungeons | 227 | 61 | A |
| DungeonCrawl | 115 | 1 | A |
| L_Enders_Cataclysm | 86 | 46 | A |
| twilightforest | 65 | **268** | A |
| iceandfire | 26 | 71 | A |
| bygonenether | 30 | 56 | A |
| The_Undergarden | 35 | 26 | A |
| ars_nouveau | 5 | 19 | A |
| **AE2（12.9.x）** | **0** | **0** | **B（純 code）** |
| SandBox／Mekanism／Cyclic／CustomMachinery | 0 | 0 | B（疑，class 有 structure/worldgen） |

- 包層統計（`AI_test_NFWC_DIM`，230 mod jar）：**有 `.nbt` ＝ 40 個、有 worldgen JSON ＝ 41 個**
- 結論：**冒險類 mod 絕大多數有資料檔 → A 路 ROI 高；B 路係補底（AE2、部分技術 mod）**。SK 嘅擔心部分成立（AE2 類）但唔係主流。

## 3. 本機 spike 數據（唯讀，2026-09-21）

- 官方 1.19.2 client jar：**925 個結構模板**（village 482／bastion 167／woodland_mansion 73／ancient_city 58／underwater_ruin 48／end_city 20／shipwreck 20／fossil 16／nether_fossils 14／ruined_portal 13／pillager_outpost 11／igloo 3）
- mod jar 亦有，例：`witherstormmod-1.19.2-3.1.1.1.jar` → 10 個 `.nbt` ＋ 3 個 `configured_feature`
- **實測成功解到 palette**：`data/witherstormmod/structures/bowels/bowels_drop.nbt` → `tainted_cobblestone`、`tainted_flesh_block`、`infected_flesh_block`、`bowels_drop` 等
- **反例（要 G2 才答得準）**：13 個 `ruined_portal` 模板 palette **冇** `crying_obsidian`（只有 netherrack／obsidian／gold_block／chest／stone_bricks／lava）→ 哭泣的黑曜石係 `block_age` processor 按完整度動態生成
- 成本參考：模板合共 925＋（每個包不同）→ 必須硬上限（學 `KubeJsMechanicScan` 嘅 `mechanicScanMaxFiles／MaxBytes／MaxMs` 做法）

## 4. 外部 research（2026-09-21 完成，2 個 subagent；來源見文末）

**業界現況（重點）**
- **冇任何一個工具做「一站式 where-to-obtain」**：JER／EMI／JEI 全部係拼出嚟。
- **JER（1.19.2 有 branch，127★，最後 push 2026-08-03）**：礦物分佈**硬編碼**（`MinecraftCompat.registerOres()` 逐條寫死，例：鑽石＝`DistributionTriangular(6,6,-64,64)`＋OVERWORLD）；mod 礦物只有三條路：①mod 自己用 `IJERPlugin` 註冊 ②玩家手寫 `<config>/world-gen.json`（`WorldGenAdapter.java`）③`/jer_profile` 逐 chunk 實測掃描（**限單人＋permission 4**）。生物掉落則由 loot table 推（`LootTableHelper`）。
- **JEIWorldGen** 係唯一自動由 worldgen 資料推導嘅現成實作，**但冇 1.19.2 版** → 唔可以直接用。
- 結論：我哋要嘅 (B) 世界生成索引，**業界冇現成可抄**，要自己實作；JER 嘅做法（硬編碼＋掃描）對我哋唔啱（我哋要 offline、要跟包）。

## 4b. 本機實測基準（subagent 量，可審計）

- **vanilla 1.19.2 client jar**：`data/minecraft/` 只有 5 個資料夾——`advancements`(1179)、`loot_tables`(1026)、`recipes`(1089)、`structures`(925)、`tags`(342)；**完全冇 `worldgen/`**（只有 `tags/worldgen/**`）→ 官方 processor_list／configured_feature **拎唔到**，同我自己 spike 一致
- 925 個 vanilla 模板**全部 gzip**；**0 個**含 `crying_obsidian`（證實係後處理產生）
- **重要新發現**：結構模板內嘅 chest block entity 帶 `LootTable` 欄位 → **結構 ↔ 寶箱掉落表 嘅真連線**（例：`chests/bastion_other` 29、`chests/ancient_city` 13、`chests/ruined_portal` 13、`chests/village/village_savanna_house` 10）→ 可以砌「XX 結構嘅寶箱有機會出呢件物品」（比用 loot table 名猜好得多）
- **整合包（AI_test_NFWC_DIM，231 jar）實測**：`data/<ns>/structures/**.nbt` = **6787**；`worldgen/template_pool` 2384、`worldgen/structure` 548、`worldgen/processor_list` **548**、`configured_feature` 347、`placed_feature` 318、`biome` 28；`forge/biome_modifier` 219；`loot_modifiers` 175；**`worldgen` JSON 內含 `block_age` = 0**（即 1.19.2 冇 mod 直接用 block_age → 哭泣的黑曜石要內建小表）
- 低信心項（已標示）：`mossiness → BlockAgeProcessor → crying_obsidian` 呢條因果鏈係拼接（各步有來源，但冇任何 1.19.2 JSON 直接用 `block_age`）

**來源**：Minecraft Wiki wikitext API、Forge docs、`misode/mcmeta` 1.19.2-data 鏡像、GitHub `way2muchnoise/JustEnoughResources`（raw 原始碼）、`gh api` repo 統計；本機實測（vanilla client jar＋231 mod jar）。詳見 delegation summary：`%LOCALAPPDATA%\hermes\cache\delegation\subagent-summary-{0,1}-20260921_225845_*.txt`

## 5. 未定嘅設計問題

1. **掃描範圍**：只掃 mod jars？定要連 vanilla client jar（925 模板喺嗰度）？—— vanilla 路徑要喺 launcher libraries 揾，唔喺 mods 目錄
2. **上限策略**：每包模板數可觀；要設 maxFiles／maxBytes／maxMs＋快取（fingerprint＝mod 清單＋jar sha）
3. **出句方式**：新增 lang key（3 語）？例如「自然生成於：<結構名>（<生物群系>）」；結構名用官方顯示名（唔自創）
4. **優先序**：G1＋G2 先（覆蓋 SK 舉嘅例子）？G4（工具）另開？
5. **同 Slice 1 嘅關係**：Slice 1 嘅正面句「破壞 X 會掉落」屬掉落表；世界生成句係另一條路線，兩者要在 `PackIndex` acquire 排序講清楚先後
6. **Slice 1b**（mine-only 可挖句＋老實句收緊）要唔要合併入 Slice 2 一次過改？

## 6. 驗收構想（未定案）

- 真機沙盒：問 `minecraft:crying_obsidian` → 必須講「廢墟傳送門自然生成」＋唔准出現 raw path／`.nbt`／`worldgen/` 等內部字串
- 負控：一件純 mod 掉落物（冇世界生成）→ 唔准亂講結構
- harness：世界生成句 generator 純函數測試（3 態：有結構／有礦脈／兩者皆無）
- Python 閘：新 lang key 三語齊＋`%s` 次數

## 7. 下一步

1. 外部 research 回填 §4（已派出 2 個 subagent）
2. 補 spike：processor_list 拎唔拎到 `block_age` → 證明 G2 可行
3. plan 收斂 → 反方 review（≥8:2，上限 3–4 輪）→ 才交 cursor 實作
