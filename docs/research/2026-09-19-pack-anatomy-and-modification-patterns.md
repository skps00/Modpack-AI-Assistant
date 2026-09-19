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

## 4. 公開 pack 語料（2026-09-19 追加；逐 commit 標記）

方法：`gh api repos/<r>/git/trees/<branch>?recursive=1`（一個 call 攞全檔清單＋commit SHA）＋ `gh search code`。原始數據 `%TEMP%\mp_public_repos.json`。

| repo | branch | 最後 push | KubeJS（srv/dir） | 目錄風格 | 備註 |
|---|---|---|---|---|---|
| AllTheMods/ATM-10 | main | 2026-08-29 | 177/336/1746 | **單數** `recipe/tags/loot_table/advancement`（1.21.1） | 1.21 分界再證 |
| AllTheMods/ATM-9 | main | 2025-10-12 | 98/373/76 | 複數 | 1.20.1 |
| AllTheMods/ATM-8 | main | 2024-06-02 | 69/183/21 | 複數 | 1.19.2 |
| AllTheMods/ATM-7 | Staging | 2024-03-21 | 37/224/105 | 複數 | 1.18.2 |
| EnigmaticaModpacks/Enigmatica2Expert | master | 2026-09-09 | **0** | — | 1.12.2，`.zs` 108 檔、零 KubeJS |
| EnigmaticaModpacks/Enigmatica6 | master | 2026-09-02 | **817**/290/557 | 複數 | 1.16.5，KubeJS 大量 |
| EnigmaticaModpacks/Enigmatica9 | master | 2026-07-25 | 865/57/582 | 複數 | 1.19.2 |
| Nomifactory/Nomifactory | dev | 2026-09-08 | 0 | — | 1.12.2，`.zs` 45＋`manifest.json`/`overrides/` |
| TeamMoegMC/TheWinterRescue | 1.20 | 2026-08-26 | 56/868/238 | 複數 | 中文包，含 `improvements`／`schematics`（Tetra 系） |
| CTNH-Team/Create-New-Horizon | dev | 2026-09-16 | **0** | — | packwiz（`index.toml`＋218 `.pw.toml`），**零 script**，純 datapack／resource pack |
| Jasons-impart/Create-Delight-Remake | main | 2026-09-18 | 343/2221/3899 | 複數；`schematics 525, materials 267, improvements 162, modules 86` | 302★，同樣大幅擴充 Tetra |
| Eternal-Snowstorm/Create-Mechanism-and-Innovation | main | 2026-09-18 | 244/467/1605 | 複數；含 `replacements 29` | packwiz 225 |

**NFWC 家族（SK 玩嘅 pack，實測）**

| repo | 星星 | 最後 push | `kubejs/data/tetra/` 檔數 | 備註 |
|---|---|---|---|---|
| Yorunina/No-Flesh-Within-Chest | 475 | 2025-01-14 | **141** | GPL-3.0，公開原版（1.19.2） |
| **本機 instance**（No_Flesh_Within_Chest-1.0.2-DIM） | — | — | **543** | 衍生版（有 golden_age／archotech 等私有內容） |
| Yorunina/No-Flesh-Within-Chest-2 | 22 | 2026-09-18（活躍） | 207 | kubejs data 920／assets 1666／server 523 |
| wdsjzly/…-DLC | 1 | 2026-07-09 | 141 | MIT；有 `NFWC_DLC_Template.jar`（DLC jar 通道） |
| 777441/…-2-DIM-Migrate1.20.1 | 2 | 2026-06-19 | 284 | 1.20.1 遷移線 |

**結論**：① 公開 repo 可以做對照／驗來源，但**唔等於玩家手上版本**（141 vs 543）；② 私有內容（`archotech_void_scythe`／`golden_age_tetra`）GitHub code search **零結果** ⇒ 只可本地讀；③ pack 內容可以經 **DLC jar** 出（`NFWC_DLC_Template.jar`；本機 `ino_dlc_*`／`hpdlc`／`maodlc`／`mrqx_disc_pack`）——去 jar 內 `data/<dlc_ns>/` 搵，唔喺 `kubejs/`；④ 同一款 pack 可以有 4–5 個衍生版本同時活躍，任何結論都要寫清「對住邊個 repo／branch」。

## 6. Pack 自加內容地圖（本機 1.19.2 instance 實測，2026-09-19）

**DLC 命名空間全部由 `kubejs/` 提供，唔係 jar**（231 個 jar 逐個掃 `data/`／`assets/` 命名空間 ＋ 對比 kubejs）：

| 命名空間 | jar | kubejs 檔數 |
|---|---|---|
| `ino_dlc_build` | **NONE** | 431 |
| `ino_dlc_wizard` | **NONE** | 78 |
| `hpdlc` | **NONE** | 109 |
| `maodlc` | **NONE** | 195 |
| `mrqx_disc_pack` | **NONE** | 24 |
| `golden_age` | **NONE** | 1,204 |

32 個命名空間「喺 kubejs 但唔喺任何 jar」；`kubejs` 目錄供應量：`data/tetra` 2,420 檔、`assets/golden_age` 1,120、`assets/tetra` 970、`assets/ino_dlc_build` 431…

**Pack 自加 Tetra 內容散落 6 層**（以「亞巴頓」為例，全部本機檔案實錘）：
- `kubejs/data/tetra/modules/single/archotech_void_scythe.json`（模組）
- `kubejs/data/tetra/materials/metal/golden_age/archotech_steel.json` 等（材料）
- `kubejs/data/tetra/improvements/archotech_void/{epitaph,sonic,void_judgement}.json`（改裝）
- `kubejs/data/tetra/repairs/single/archotech_scythe.json`（修理）
- `kubejs/data/tetra/schematics/single/archotech_void_scythe.json`、`synergies/single/…`（圖紙／協同）
- 顯示名：`kubejs/assets/golden_age_tetra/lang/zh_cn.json`；tooltip：`kubejs/client_scripts/golden_age/item_tooltips_1.js`；伺服器邏輯：`kubejs/server_scripts/golden_age/{recipes,tetra_1,tetra_effect,judgement,gate}.js`

**物品註冊**：`kubejs/startup_scripts/golden_age/dlc_template_item_register.js`（`StartupEvents.registry('item', …)`）——即係話 pack 自加物品係**由 KubeJS startup script 生出嚟，冇 jar、冇 datapack 定義**。
**pack 自製 jar**：`goldenage-mod.jar`（modId `golden_age_mod`，MCreator 生成，提供 `assets/golden_age_mod` 230、`data/golden_age_mod` 62、`data/golden_age_structure` 38、`data/test` 208）。

### NFWC-2（公開活躍版）vs 本機 instance —— **唔同 MC 版本，唔可以互相套用**

| | NFWC-2 公開版 | 本機 instance |
|---|---|---|
| MC／loader | **1.20.1 Forge**（`mods/*-1.20.1.jar`） | **1.19.2 Forge 43.3.5** |
| 版本標記 | `main` @ commit `5b83ed6978a0`，pushed 2026-09-18 | pack `No_Flesh_Within_Chest-1.0.2-DIM` |
| kubejs | server 523／startup 103／client 83／data 920／assets 1,666 | 447／153／244／3,087／5,208 |
| config 檔 | 640 | 1,646 |
| data 命名空間 | 只有 7 個同本機重疊 | 本機獨有 12 個（`golden_age`／`hpdlc`／`maodlc`／`luna_flesh_reforged`／`mvs`…）；遠端獨有 17 個（`irons_spellbooks`／`agricraft`／`mbtool`…） |

⇒ 「新一代」≠「同一個 pack 嘅更新版」：係**另一條 MC 版本線**，命名空間集合大幅分歧。任何對比都要寫清版本。

## 7. 來源（節錄）

- Minecraft Wiki：Data pack / Pack.mcmeta / Resource pack（目錄、pack_format、1.20.5 components、1.21 單數化）
- KubeJS：`kubejs.com/wiki/folder-structure/data`、`/assets`、`wiki.latvian.dev/…/list-of-events`、`kubejs.com/wiki/other/major-updates/7.0`、`api.modrinth.com/v2/project/kubejs/version`（版本對應）
- 發行格式：Modrinth `.mrpack` 文件、`gdlauncher.com/docs/modpack-manifest-format`（CurseForge）、`packwiz.infra.link`、MultiMC wiki（`instance.cfg`／`mmc-pack.json`）
- Tetra：`tetra.mickelus.se/wiki/1.20/tech`（「Most content in tetra can be changed using datapacks」）、`tetra.mickelus.se/compat`（92 個兼容 mod）
- Create：Create wiki Custom Recipes（recipe 走 datapack）；Ponder 場景 code 註冊
- Sophisticated Backpacks：`github.com/P3pp3rF1y/SophisticatedBackpacks`（`smithing_backpack_upgrade`）
- FTB Quests：`docs.feed-the-beast.com/mod-docs/mods/suite/Quests/Developer/Quests`
