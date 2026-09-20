# 礦物世界生成資料 —— recipe viewer 生態研究（2026-09-20）

問題（SK）：「check JEI / REI / EMI WorldGen」。目的：搞清楚業界點做「玩家睇得到嘅礦物分佈資料」，我哋（packai）嘅做法同佢哋差幾遠、有咩可以借。

## 1. 生態總覽

| | **JER**（Just Enough Resources） | **JEIWorldGen**（CurseForge 名：JEI / REI / EMI WorldGen） | **EMI Ores** | **RER**（Roughly Enough Resources） |
|---|---|---|---|---|
| 掛喺邊個 viewer | JEI（EMI 經 JEI 兼容層） | JEI／REI／EMI | EMI（+ EMI Loot 做掉落） | REI |
| **資料來源** | **硬編碼／DIY**（1.19.2 config `diyData = true`；modded 礦要靠整合 mod） | **Runtime registry 物件**（`OreData(targets, size, CountPlacement, HeightRangePlacement)`）＋ server→client 同步 | **Runtime registry 物件**（`Registries.PLACED_FEATURE`＋`OreConfiguration`／`GeodeConfiguration`＋`Biome#getGenerationSettings`）＋ server→client 同步 | **掃實際 chunk**（經驗抽樣） |
| 顯示欄位 | Spawn Biomes／Valid Dimensions／Drops／Avg. blocks per chunk＋分佈圖 | Y 分佈、vein size、count、biome | Y-level 分佈、vein size、vein count/chunk、biome 限制、石／深板岩合併 | 由實測推分佈 |
| Modded 礦自動支援 | ❌（要 JER-Integration：Thermal／Create／IE／TConstruct／Mekanism…） | ✅ | ✅ | ✅ |
| 需要兩邊（client+server） | — | 係（網絡同步） | 係（否則靜默停用） | — |

來源：EMI Ores CurseForge 頁嘅官方比較表（作者自述）；我另外親讀開源碼：
- `Abbie5/emi-ores` → `xplat/.../networking/FeaturesSender.java`：`access.registryOrThrow(Registries.PLACED_FEATURE)`、`pf.feature()` → `cf.feature() instanceof OreConfiguration`、`Registries.BIOME` → `biome.getGenerationSettings().features()` 反查 biome。
- `Larsens-Mods/JEIWorldGen` → `common/.../client/OreGenData.java`：`OreData{targets,size,CountPlacement,HeightRangePlacement}`＋`BiomeData`，透過 `FriendlyByteBuf` 做 S2C 同步。
- 本機實物：ATM8 裝 **JER 1.19.2-1.2.3.243**（`diyData = true`）、FTB 裝 **JER-Integration 4.5.0**（內含 `compat/thermal/ThermalWorldGen`）、NFWC 裝 **EMI 1.1.18 + emi_loot 0.6.6**。

## 2. 同 packai 嘅對照（重點）

| | 業界 | packai 今日 |
|---|---|---|
| 資料來源 | **已解析嘅 runtime 物件**（registry） | 掃 jar 內 **worldgen JSON** |
| Inline 定義（Thermal 式 `"feature": {…}`） | 自動得（物件已解析） | **睇唔到 ⇒ 冇「礦脈大小」**（plan P1-F1b） |
| Biome 反查 | `Biome#getGenerationSettings()`（精確） | 靠 dimension 檔＋啟發式（有歧義要剔） |
| Datapack 動態加入 | 得（registry 係來源） | 得（掃檔）但要處理路徑形態 |
| 需要世界／server | 係（要 registry；EMI Ores 要兩邊） | **唔需要**（標題畫面都答得） |
| 欄位 | dimension／biome／height／vein size／count（＋分佈形狀、石／深板岩合併） | dimension／biome／height／vein size／count ✅ 同欄位 |

**結論**
1. 我哋走嘅係「**資料驅動**」路線，同 EMI Ores／JEIWorldGen 同一家族（唔係 JER 嘅硬編碼路線）——方向正確，亦解釋咗點解 modded 包我哋答得到。
2. 但佢哋讀 **registry 物件**、我哋讀 **JSON 檔** ⇒ 一切「解析唔到嘅寫法」（inline feature、未來新形態）我哋會靜默漏，佢哋唔會。**呢個就係 P1-F1b 嘅根因，亦係之前 code review 講嘅「路徑形狀寫死」脆弱位。**
3. 佢哋有、我哋未有嘅：分佈形狀（三角形／均勻／梯形）、石／深板岩合併、geode（紫水晶洞）支援、`Avg. blocks per chunk` 計法。
4. 佢哋要 client+server 兩邊；我哋純檔案掃描（標題畫面都答得）＝一個真實優勢，值得保留做 fallback。

## 3. 建議（交 SK 決定）

- **(甲) 短期（已喺 plan 內）**：照 P1-F1b 修 JSON 解析（認 object 型 `feature`，讀 `type` + `config.size`）。成本細、可即刻做。
- **(乙) 中期（新建議）**：加一條 **registry 來源**（入咗世界之後讀 `Minecraft.getInstance().getConnection().registryAccess()` 嘅 `PLACED_FEATURE`／`BIOME`）做**主來源**，檔案掃描做 fallback（標題畫面／未入世界）。咁就一次過解決 inline、tag、新寫法、biome 精確度（同 EMI Ores 同等），亦剷走「路徑形狀寫死」脆弱位。成本：中等（要處理 registry 未 ready 嘅時機＋與現有 index 合併）。
- **(丙) 對齊用詞**：玩家可見字眼可以跟業界慣用（JER 叫「World Gen」、欄位「Spawn Biomes／Valid Dimensions」；EMI Ores 用「Y-level distribution／Vein size／Vein count per chunk／Biome restrictions」），減少玩家理解成本。**注意唔可以照搬**（唔同語言／唔同版權字串），只作參考。

## 4. 來源清單

- EMI Ores（CurseForge）：https://www.curseforge.com/minecraft/mc-mods/emi-ores （官方比較表＋「gathers its data from data pack configured/placed features」）
- EMI Ores 源碼：https://github.com/Abbie5/emi-ores （`xplat/src/main/java/cc/abbie/emi_ores/networking/FeaturesSender.java`、`client/FeaturesReciever.java`）
- JEI / REI / EMI WorldGen（CurseForge）：https://www.curseforge.com/minecraft/mc-mods/jei-worldgen ；源碼：https://github.com/Larsens-Mods/JEIWorldGen （`common/.../client/OreGenData.java`）
- JER：https://www.curseforge.com/minecraft/mc-mods/just-enough-resources-jer ；本機 `JustEnoughResources-1.19.2-1.2.3.243.jar`（`jeresources/jei/worldgen/*`、`config/jeresources.toml` → `diyData = true`）；整合：`JER-Integration-4.5.0.jar`
- RER：https://www.curseforge.com/minecraft/mc-mods/roughly-enough-resources
