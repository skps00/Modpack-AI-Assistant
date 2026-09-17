# Plan E v1 — NBT 感知嘅「點嚟」查詢（分辨空框架 vs 魔改版）

> 狀態：**v1（2026-09-17 15:4x）** — SK `go`（開計畫）。方向由 SK 提出：同一個 id 要**跟埋 NBT** 查。⛔ 未實作；先過反方 review。

## §0 病因（SK 指出，已用真檔核實）
同一個 id 可以有兩種完全唔同嘅嘢，而我哋而家**只認 id**：
| 情況 | id | NBT | 真來源 |
|---|---|---|---|
| **空框架** | `tetra:modular_sword` | 冇（或只有默認） | Tetra 自己：切石機＋木棍（`data/tetra/recipes/stonecutter.json`，`minecraft:crafting_shapeless`） |
| **魔改版** | `tetra:modular_sword` | 有部件／材料欄位（數值被改過） | **任務獎勵** 或 **機器合成**（唔經 Tetra 系統） |

**證據 1（包自己點分辨）**：`kubejs/server_scripts/b_a_d/b_a_d_player_damage.js` 全靠「id ＋ NBT 材料欄位」判斷——`:366`（`sword/katana_blade_material`）、`:436`（`single/head:harpoon_rush`）、`:784`（`sword/saber_blade_material`）、`:794`（`sword/rapier_blade_material`）、`:978`（`single/mace_head_material`）、`:1019`（`sword/short_blade_material`）、`:1028`（`sword/euclidean_dagger_material`）、`:1415`（`sword/index_blade_material`＋`sword/lock_material`）、`:1499`（`double/basic_hammer_*_material`）、`:1652`（`bow/remembrance_stave_material`）；另有 `b_a_d_key_bind.js:604`。
**證據 2（包內 NBT 特徵種類，掃到）**：`single/soul_ruby_material`(7)、`single/head:single`(8)、`sword/soul_ruby_material`(6)、`sword/short_blade_material`(4)、`sword/key_guard_material`(4)、`sword/katana_blade_material`(4)、`sword/forefinger_ring_material`(4)、`sword/euclidean_dagger_material`(4)、`single/longinus_material`(3) …
**證據 3（任務路線真存在）**：`config/ftbquests/quests/chapters/*.snbt` 有 `tetra:modular_sword`（`017BA7BB23C1F872.snbt:2213`、`1A0496E63CC7F38B.snbt:129`）、`tetra:modular_double`（`1.snbt:384/413`、`1054A9816CE14981.snbt:107`）、`tetra:modular_toolbelt`（`205729B68F50DC1C.snbt:103`）。
**證據 4（今日嘅錯）**：12:21 亞巴頓／12:24 石刻 → 答案講「只是空框架，不是取得途徑」＝把**魔改版**當**空框架**答。

## §1 目標（做咩）
查「呢件嘢點嚟」時：
1. **分開兩種情況**：focus 有材料欄位 → 當「魔改版」；冇 → 當「空框架」。
2. **魔改版**：優先搵**任務獎勵**（`config/ftbquests/quests/**/*.snbt`）同**機器／腳本產出**（kubejs recipes，按 id ＋ NBT 材料欄位）；搵到就引任務／機器名。
3. **搵唔到**：老實講「未收錄呢個版本嘅取得途徑」，**唔准**用空框架配方頂替（今日就係錯呢點）。
4. **空框架**：照舊答 Tetra 配方（切石機＋木棍）。

## §2 技術要點（現況）
- 查詢用嘅 stack：`logic/JeiLookupAskTool.java:49-57` 用 `ItemResolver.stackFromId(args.itemId)` → **無 SNBT 就 `new ItemStack(item)`（掉咗 NBT）**；`ItemResolver.java:220-226` 自己已註明此坑；`:231-239 stackFromRef` 保留 NBT（`env.held` 係唯一 ref 來源，`AskToolEnv.java:17`）。
- NBT 比對：`JeiFocusMatch.java:59/240` 用 `ItemStack.isSameItemSameTags`（**完全一致**）→ Tetra 每件 NBT 有**隨機流水號**，完全一致永遠配唔上 ⇒ 要**只比部件／材料欄位**，忽略隨機欄位。
- 來源掃描：`logic/PackIndex.java:183-184` 只掃 kubejs／datapacks ⇒ **未掃 `config/ftbquests/`**（任務獎勵而家查唔到）。
- 取得事實入口：`logic/AcquireAskTool.java:40` → `PackIndex.acquireFactsDetailed`。

## §3 驗收（機驗）
- **S1**：真機問 亞巴頓（`tetra:modular_single`，有 NBT）→ 答案**唔准**再出「只是空框架，不是取得途徑」式否定；要分開講「魔改版 vs 空框架」。
- **S2**：真機問**空框架**（無 NBT）→ 答案照舊講切石機＋木棍（唔可以因為新邏輯而唔講）。
- **S3**：若任務檔有該件 → 答案要引**任務名／章節**（可查證字串）。
- **S4（負控）**：一件完全冇配方、冇任務嘅物品（例 `minecraft:bedrock`）→ 仍准講「查唔到」，唔准亂引。
- **S5**：隨機流水號**唔准**做比對條件（用兩件同款但唔同流水號嘅工具做 fixture，兩者都要命中）。
- **S6**：`tests/check_*.py` 全跑（118 個；已知紅樣本要帶 `--trace … --since 20260901`）。
- **S7**：新增／改動嘅 harness 名同命令要寫死喺計畫（實作前補）。
- **S8**：效能：新增掃描（ftbquests snbt）要有上限＋失敗即回退（唔准拖慢 ask）。

## §4 風險／還原
- 影響：`AskService`／`AskEngine`（取得事實組裝）、`PackIndex`（新增任務來源）、`JeiLookupAskTool`（保留 NBT）。
- 還原：改動前 copy 去 `.hermes/backups/2026-09-17_nbt_lookup/` ＋ `md5sums.txt`；jar 由 `mc_mod_deploy_jar.py` 自動備份。
- 最壞情況：答案由「講錯」變「唔講」→ 用 S2 擋；任務 snbt 解析失敗 → 回退唔注入（S8）。
- 唔郁：trace 事件／欄位、`PackAiConfig` 預設、卡抑制政策（Plan D v11 已剔出）。

## §5 Review 記錄
| 輪 | 比分 | 關鍵 |
|---|---|---|
| 1 | 待跑 | — |
