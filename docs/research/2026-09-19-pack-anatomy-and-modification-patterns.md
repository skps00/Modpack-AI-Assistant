# 整合包解剖 ＋ mod／pack 改法：版本標記研究（2026-09-19）

> 目的：搞清「一個整合包係點砌、人哋點改 mod／pack、玩家要嘅答案喺邊」，作為 packai（及其後任何離線讀取器）嘅設計基礎。
> **紀律**：所有結論一律標 **MC 版本 + loader + mod 版本 + 證據來源**；唔准跨版本套用。本文只覆蓋本機實測範圍（MC 1.8.3–1.21.1；packai 只支援 1.19.2 Forge ＋ 1.21.1 NeoForge）。
> 配套 skill：`minecraft-modpack-anatomy`（SOP，可直接照做）。

## 1. 方法（可重跑）

| 做法 | 範圍 | 產物 |
|---|---|---|
| 逐 instance read-only 掃描 | Prism `instances/` 全部 **51 個** instance（含 1.8.3 / 1.8.9 / 1.12.2×19 / 1.13.2 / 1.16.5×3 / 1.18.1 / 1.18.2×5 / 1.19 / 1.19.2×8 / 1.20.1×6 / 1.21.1×2） | `%TEMP%\mp_instance_matrix.md`（人睇表）、`%TEMP%\mp_instance_matrix.jsonl`（原始）、掃描器 `%TEMP%\mp_instance_matrix_scan.py` |
| 目錄風格硬證據 | 每個 instance 抽 **最大 12 個 jar**，regex 數 `data/<ns>/<dir>/` | 同上 `dir_style` 欄 |
| 文件研究（附來源） | datapack 層 / 腳本引擎 / pack 本體格式 / mod 改法 / 玩家文字來源 | `%TEMP%\mp_datapack_layer.md`、`mp_script_engines.md`、`mp_pack_anatomy.md`、`mp_mod_modification.md`、`mp_player_text_sources.md` |
| 本機實地追蹤 | 1.19.2 instance 5 件物品（`instantblocks:wand_netherite`、`sophisticatedbackpacks:netherite_backpack`、`create:wrench`、`tetra:modular_sword`、`golden_age:archotech_void_scythe`）逐個追 jar／kubejs／quests／config／EMI | `%TEMP%\mp_player_text_sources.md` |

## 2. 硬證據：版本分界（機械可驗，唔靠印象）

### 2.1 目錄命名（jar 內 `data/<ns>/…`）

| MC 帶 | instance 數 | 複數目錄命中 | 單數目錄命中 | `data_map(s)` |
|---|---|---|---|---|
| 1.16.5 | 3 | 38,327 | **0** | 0 |
| 1.18.x | 6 | 76,665 | **0** | 0 |
| 1.19.2 | 8 | 64,236 | **0** | 0 |
| 1.20.1 | 6 | 72,214 | **0** | 0 |
| **1.21.1** | 2 | **30**（殘留） | **21,391** | **76** |

⇒ **1.21 開始 `advancement/recipe/loot_table/structure/tag/function` 全部單數**；1.21.1 仍有少量 jar 留複數目錄（混用，讀取器要兩種都試）。`data_maps` 只喺 1.21.1 出現（= 1.20.5+ 特性）。

其他分界（附來源）：
- 1.13（pack_format 4）datapack 出世；1.12.2 冇 `data/` 概念（recipe/loot 喺 `assets/<modid>/`）。
- 1.17：loot table 數值 provider 必須寫 `type`，唔寫即 parse 失敗。
- 1.20（23w04a）：`minecraft:smithing` 拆成 `smithing_transform` + `smithing_trim`。
- **1.20.5（format 41）**：item NBT → item components（`{Damage:10}` → `[minecraft:damage=10]`；raw NBT 只剩 `custom_data`）；recipe result `item` → `id`+`components`；predicate 刪 `nbt`/`durability`。⇒ **1.19.2 嘅 NBT 邏輯唔可以搬去 1.21.1**。
- **1.21（format 48）**：目錄複數→單數（舊 pack 唔報錯，係「內容唔見」）。
- 覆蓋序：最後載入者贏；tag 無 `"replace": true` ＝ merge；`OpenLoader`/`global_packs` 最高；KubeJS 產生 pack 低於用戶世界 datapack。

### 2.2 改造機制分佈（實測 51 instance、合共 10,413 個 mod 安裝）

| MC 帶 | KubeJS | CraftTweaker `.zs` | GroovyScript | FTB Quests `.snbt` |
|---|---|---|---|---|
| 1.12.2（19 個） | **0** | **17 個 instance**（E2E 507 檔、MeatballCraft 447、NovaEngineering 128） | 4（Nomifactory） | 0（1.12.2 未用 FTB Quests） |
| 1.16.5–1.20.1 | **16 個 instance** | 4 | 0 | 13 |
| 1.21.1 | 2（KubeJS **7.2**） | 0 | 0 | 2（**任務文字已搬去 lang**，因 `ftbquestslangsplitter`） |

KubeJS 版本對應（實測檔名）：`1605.3.19`＝3.x（1.16.5）／`1801.4.3`＝4.x／`1802.5.5`＝5.x（1.18.2）／`1902.6.2`＝6.x（1.19.2）／`2001.6.5`＝6.5（1.20.1）／`2101.7.2`＝7.x（1.21.1，NeoForge only）。
目錄語意：`startup_scripts`＝註冊；`server_scripts`＝配方/tag/loot（`/reload`）；`client_scripts`＝資源包/JEI/tooltip；`kubejs/data/**`＝等同 datapack（永遠載入）；`kubejs/assets/**`＝等同 resource pack。

### 2.3 「玩家文字」實際位置（1.19.2 實測）

- 漢化中文**唔一定喺 jar**：`instantblocks` jar 只有 en_us，玩家睇到嘅「下界合金即时权杖」來自 `resourcepacks/*-Converted-1.19.2.zip`（I18nUpdateMod 類工具，會聯網覆寫）。
- pack 可以用 script 直接賣／送物品：`sophisticatedbackpacks:netherite_backpack` 由黑市 `kubejs/server_scripts/utils/wares_model.js:137` 以 1 鑽石幣賣（帶 NBT `{inventorySlots:180,…}`）；任務檔亦有送背包。
- 遊戲內教學可讀：Create（1.19.2-0.5.1）lang 有 **151 場景 header ＋ 684 條步驟文字**、162 個場景 `.nbt`；但**步序／時序只喺 bytecode**（42 個 class）。
- config 可以「殺死」jar 資料：本包 `chestLootEnabled=false` ⇒ jar 內地城戰利品表失效，唔准答「地城搵到」。

## 3. 對 packai 嘅含意（只列，未開工）

1. **盲點清單**（全部有實例）：非原版配方類型（`minecraft:smithing`、mod 自訂 type）、lang 教學文字、advancement 描述、pack 自加內容、物品根本唔存在（`create:schematic`）。
2. **兩層知識分工**：mod 級玩法（Tetra 錘→工作台、tier、廢墟）＝跨包一樣，可寫死／共用；pack 級內容（`golden_age_tetra`、黑市 wares、quest 贈品）＝**只可本地讀**，上庫無意義。
3. **讀取層要分版本**：1.19.2 用複數目錄＋NBT；1.21.1 用單數目錄＋components；同一套 regex 通吃必錯。
4. 線上共用庫唔係瓶頸：上述答案幾乎全部已經喺玩家本機。

## 4. 來源（節錄）

- Minecraft Wiki：Data pack / Pack.mcmeta / Resource pack（目錄、pack_format、1.20.5 components、1.21 單數化）
- KubeJS：`kubejs.com/wiki/folder-structure/data`、`/assets`、`wiki.latvian.dev/…/list-of-events`、`kubejs.com/wiki/other/major-updates/7.0`、`api.modrinth.com/v2/project/kubejs/version`（版本對應）
- 發行格式：Modrinth `.mrpack` 文件、`gdlauncher.com/docs/modpack-manifest-format`（CurseForge）、`packwiz.infra.link`、MultiMC wiki（`instance.cfg`／`mmc-pack.json`）
- Tetra：`tetra.mickelus.se/wiki/1.20/tech`（「Most content in tetra can be changed using datapacks」）、`tetra.mickelus.se/compat`（92 個兼容 mod）
- Create：Create wiki Custom Recipes（recipe 走 datapack）；Ponder 場景 code 註冊
- Sophisticated Backpacks：`github.com/P3pp3rF1y/SophisticatedBackpacks`（`smithing_backpack_upgrade`）
- FTB Quests：`docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests`
