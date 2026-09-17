# Plan E v2 — NBT 感知查詢（併入 E-R1 裁決；逐 key predicate）

> 狀態：**v2（2026-09-17 15:5x）** — E-R1 3:7。SK `go`（開計畫）。⛔ 未實作；目標 正方 ≥8 : 反方 ≤2。

## §0 證據（R1 更正：圖示 ≠ 取得途徑 —— 我原本引嘅 5 條全係 `icon:`）
**真 reward（我自己覆核原文）**
- `config/ftbquests/quests/chapters/tetra.snbt:3465-3475`：`rewards: [{ id: "6C426604A2A3C3A4", item: { Count: 1b, id: "tetra:modular_sword", tag: { Damage: 0, Unbreakable: 1.0d, "sword/blade": "sword/twilight_eye", "sword/guard": "sword/twilight_beak" … } } }]` ← **任務獎勵直接畀一件帶 NBT 部件嘅劍**
- `config/ftbquests/quests/chapters/goldenagetetra.snbt:~203-210`：item `id: "tetra:modular_sword"`、`tag: { Damage: 0, HideFlags: 1, honing_progress: 305, id: "c2ffc027-86aa-4b6b-813c-27ce53472a7f", "sword/blade": "sword/clover" … }`（R1 指另一處 `secret: true`）
- **統計（R1 掃 50 個 chapter）**：95 個 `tetra:modular_*` 提及 = **77 icon ＋ 13 tasks（其中 8 個淨係 icon）＋ 2 真 reward**。⇒ **`icon:` 唔准當取得途徑**（我 v1 就係犯呢個錯）。
**包自己點分辨版本**：`kubejs/server_scripts/b_a_d/b_a_d_player_damage.js:366`（`sword/katana_blade_material`）、`:436`（`single/head:harpoon_rush`，**該行 id 係 `modular_single`**）、`:1415`（`sword/index_blade_material`＋`sword/lock_material`）、`:1499`（`double/basic_hammer_*_material`）、`:1652`（`bow/remembrance_stave_material`）；`b_a_d_key_bind.js:604`✓。
**空框架唔係「冇 NBT」**（R1 解 jar 更正）：`data/tetra/recipes/stonecutter.json` 輸出**本身就帶 nbt**（`sword/blade:sword/stonecutter`、`sword/hilt:sword/basic_hilt`＋材料欄位）⇒「有材料欄位＝魔改版」係**錯判準**。

## §1 新判準（改）
1. **關鍵**：Tetra 工具嘅 **`id` 欄（uuid）每件唔同**（reward 樣本都係 uuid）→ **唔准做比對條件**。
2. **分類**：把 focus 嘅**部件／材料欄位集合**同 Tetra 自己配方（jar 內 `data/tetra/recipes/*.json` 各結果嘅同組欄位）比對：
 - **相符** → 當「標準框架／Tetra 自產」→ 答案照講該 Tetra 配方。
 - **唔相符**（例 `sword/blade: sword/clover`、`sword/twilight_eye`）→ 當「特定版本」→ 去查**任務獎勵**（`rewards:`）／其他來源；搵唔到就老實講「未收錄呢個版本」。
3. **唔准**用「圖示（icon）」「任務要求（task）」當取得途徑 —— 只有 `rewards:`／`reward_tables/`（同機器產出，**若**有真證據）先算。
4. **政策文字**（沿 Plan D v11 §1 嘅 F-A）：答案要講得返「合成台：切石機＋木棍」並標明係空白框架／材料版本。

## §2 技術要點（R1 更正）
- **任務資料其實已經有 ingest**（R1：`PackIndex`/相關檔 `:198-199`、`:2476`）⇒ **唔係「未掃 ftbquests」**；本計畫只**補 `rewards:` 同 `reward_tables/`**，沿用既有 `QuestGuide` 切片＋防劇透政策（實作者要自己覆核呢兩處行號再落手）。
- 查詢 stack：`logic/JeiLookupAskTool.java:49-57` 用 `ItemResolver.stackFromId` → 無 SNBT 時 `new ItemStack(item)` **掉 NBT**；`ItemResolver.java:190-207`（支援 `id{…}`）、`:220-226`（自註此坑）、`:231-239 stackFromRef`（保留 NBT，ref 來源 `AskToolEnv.java:17`）。
- 比對：`JeiFocusMatch.java:59/240` 用 `isSameItemSameTags`（完全一致，含 uuid → 永遠配唔上）。
- **predicate 要逐 key 寫死**（白名單＝部件／材料；黑名單＝`id`／`Damage`／`HideFlags`／`honing_progress`／`Unbreakable`）。唔准用「有冇 NBT」做判準。
- **刪「機器產出」**（R1：冇真證據；我 grep 只見到 cooldown 呼叫，無輸出配方）。

## §3 驗收
- **S1（合併 Plan D v11 嘅 S5，唔重複）**：真機問 亞巴頓／石刻（帶 NBT）→ 答案須分開講「特定版本 vs 空框架」，並含「合成台／切石機＋木棍」＋「空白模組」字樣；**唔准**再出「只是空框架，不是取得方式」式否定。
- **S2**：問**標準框架**（Tetra 配方產出嘅組合）→ 照講切石機＋木棍。
- **S3**：任務有真 reward 嘅個案 → 引任務名／章節；**負控**：只作 `icon:` 嘅個案 → **唔准**引。
- **S4**：完全冇配方／冇任務（例 `minecraft:bedrock`）→ 仍准講「查唔到」。
- **S5**：**uuid 唔准做比對條件**（fixture：兩件同部件、唔同 uuid → 都要命中）。
- **S6**：`tests/check_*.py` 全跑（118 個）；**具名紅樣本**：`--trace "<instance>/packai/trace" --since 20260901` → RC=1（`ask-20260915-170823-eccentrictome_tome.jsonl:25`）。
- **S7**：新／改 harness 名＋命令寫死（實作前補）。
- **S8**：`config/ftbquests/quests/chapters/*.snbt` 有 50 檔 → 掃描要有**上限＋失敗回退**，並寫明量測（今日 ask 耗時 vs 改後）。

## §4 與 Plan D 嘅關係（明文 supersede）
- **Plan D v11 只保留 §1（政策文字 9 處）**，併入本計畫當第 4 件；D v11 §2（提示行）／F-C 一律**唔做**。D 嘅 S5 併入本檔 S1。
- 檔案／還原：改動前 copy 去 `.hermes/backups/2026-09-17_nbt_lookup/` ＋ `md5sums.txt`；jar 由 `mc_mod_deploy_jar.py` 自動備份。
- 唔郁：trace 事件／欄位、`PackAiConfig` 預設、卡抑制政策。

## §5 Review 記錄
| 輪 | 比分 | 關鍵 |
|---|---|---|
| 1 | 3 : 7 | 我引嘅 5 條任務證據全係 `icon:`；「空框架＝冇 NBT」被 jar 原文推翻；任務 ingest 其實已存在 |
| 2 | 本檔 | — |
