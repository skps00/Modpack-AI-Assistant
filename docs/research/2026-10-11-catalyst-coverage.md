# catalyst 覆蓋率（P0 ④）— 真機量測報告 · 2026-10-11

- 題目（原本定義）：玩家問「用咩機器做 X」時，我哋答唔答得到機器名？＝ 583 個離線 recipe type 之中，runtime JEI 有幾多個搵得到 catalyst（機器／方塊）。
- 為何只可以真機量：catalyst 係 **JEI runtime registry** 嘅概念（`IRecipeCategory#getRecipeCatalysts`），jar 內只有 recipe JSON，冇 catalyst 對應。
- 授權：SK 2026-10-11 03:2x 批（原本設計見 `docs/research/2026-10-10-p0-remaining-measurements.md` §④）。
- 實作：autotest-flag build 內新增 case action `catalyst_census`（`client/autotest/JeiCatalystCensus.java` ＋ `AutoTestHarness` 分支），世界載入後走 JEI recipe manager 逐個 category 取 catalyst item stack，寫 `packai/autotest/jei-catalyst-census.json`。**冇 flag ⇒ 完全 inert**（正式 jar 冇 `packai-autotest.flag`）。
- 真機 run：沙盒 `packai_sandbox_atm8`（ATM8，380 jar），2026-10-11 03:57，jar sha `1cc6739a0fa6…`；證據 `%TEMP%\packai_smoke_results_packai_sandbox_atm8_20261011-035502\`（summary＋trace＋latest.log）＋ census 副本 `%TEMP%\jei-catalyst-census_atm8_20261011.json`；零搶焦點、跑完零殘留。

## 結果（runtime，ATM8）

| 指標 | 數值 |
|---|---|
| JEI recipe categories（runtime 實際註冊） | **387** |
| 有 ≥1 個 catalyst item（答得到「用咩機器」） | **358（92.5%）** |
| 完全冇 catalyst | **29（7.5%）** |
| distinct catalyst items | 599 |
| catalyst 有 lang 名（玩家睇得明，**下界**） | 533（89.0%） |

- 冇 catalyst 嘅 29 個（例子）：`jei:information`（資訊頁）、`create:sequenced_assembly`（配方序列）、`create:item_application`（就地塗抹）、`ae2:item_transformation`、`botanypots:crop`、`industrialforegoing:machine_produce`、`hexerei:*_dye` —— **大部分本身唔屬「用機器做」類問題**，7.5% 屬合理。
- 最常做 catalyst 嘅（跨 category 數）：`ars_nouveau:enchanting_apparatus`（5）、`create:basin`（5）、`minecraft:crafting_table`（4）、`mekanism:robit`（4）、`create:encased_fan`（4）。
- lang 名覆蓋率係**下界**：66 個冇命中入面 **20 個係 `minecraft:*` 原版物品**（名喺原版 client jar，唔喺本次掃描範圍）＋12 個屬變體／隱藏條目（`occultism:ritual_dummy/*`、`sophisticatedbackpacks:chipped/*`、`rechiseled:chisel`）。⇒ 實際可讀率應高於 89%。

## 同離線 583 個 type 嘅關係（唔可以直接相減）

- 離線 census（`docs/research/artifacts/2026-10-10-p0-recipe-metrics.json`）數嘅係 **recipe JSON 內出現嘅 `type`**（ATM8 = 583）；runtime 數嘅係 **JEI 真正註冊嘅 category**（387）。兩者係唔同集合（離線包埋冇 JEI category／被隱藏／只由 datapack 生成的 type），**唔可以互相減**。
- 對玩家有意義嘅分母係 runtime 嗰個：**92.5% 嘅 JEI category 答得到「用咩機器做」**。

## 限制（誠實）

1. 只量一個包（ATM8，1.19.2 科技主力）；未量 StarTech／NWFC。
2. `includeHidden()` 已開 ⇒ 連隱藏 catalyst 都算入「有」。
3. census 只存在於 autotest flag build；正式版路徑零新 I/O。
4. lang 名覆蓋率係**離線近似**（掃 jar 內 `assets/*/lang/en_us.json`），唔係遊戲內真實顯示名。
