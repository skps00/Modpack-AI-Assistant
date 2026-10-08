# Plan：gap 面板／答案 raw id 外洩修復（玩家睇到 `chests/…`、`crafting_shaped → …`）

- **日期**：2026-10-08（Hermes 起草；SK 2026-10-08 決定「1a＝修」）
- **狀態**：**DRAFT — 未實作，未過 review**（packai 規則：`docs(plan)` commit → 反方 review 到 ≥8:2 → 才派 cursor）
- **來源證據（真機，兩輪）**：
  - `%TEMP%\promo_ask_results_20261008-190049\ask-20261008-1902*-*.jsonl`（NFWC，現行 10-07 jar）
  - `%TEMP%\promo_ask_atm8_results_20261008-190629\ask-20261008-1908*-*.jsonl`（ATM8）
- **觸發**：promo 拍片格畫面會出現呢啲字；玩家（唔止拍片）都會見到。

## 1. 症狀（逐字，來自 trace）

玩家可見（`display.body.final.body` 內）：

| 語言 | 逐字 |
|---|---|
| zh_cn | `资料有、答案未提：` / `掉落表：chests/abandoned_temple/abandoned_temple_entrance` |
| en_us | `On record, not in the answer:` / `Loot table: chests/forge` |
| en_us | `used in crafting_shaped → "damage plate maim"` |

同時（model-facing，`tool.result`，name=acquire）：
```
used in crafting_shaped → "damage plate maim"
Loot table: chests/ancient_temple_apex
Loot table: chests/forge
Loot table: chests/abandoned_temple/abandoned_temple_entrance
…
```

中招率（4 case／輪，逐條 trace 掃）：NFWC **3/4**、ATM8 **2/4**。

## 2. 根因（file:line，已讀碼核實）

1. **`Plainify.lootLine(lang, itemId, table)`（`forge/1.19.2/src/main/java/com/skps9/packai/logic/Plainify.java:182-198`）**
   只喺 table path 以 `blocks/` 開頭時做人化（`loot_table_block`／`loot_table_generic`）；**其餘全部**落到
   `return ReplyLang.lootTableObtain(lang, table);` ⇒ `ReplyLang.java:455`
   `tr(code, "packai.reply.loot_table_obtain", tableId)` ⇒ 玩家見到 `Loot table: chests/lich_tower`（raw id 直出）。
2. **`crafting_shaped → "…"`**：同一路徑嘅 `Plainify` 人化（`Plainify.java:232-241`）只做 `-[`／`]->` 換箭頭，
   **唔會**人化 recipe-type token（`crafting_shaped`／`crafting_shapeless`…）。
3. 現有安全網 **`AskReplyScrub.isPlayerSafeLine`（`AskReplyScrub.java:1522-1536`，靠 `PLAYER_UNSAFE_MARKERS`）**
   **捉唔到**呢兩種形態 ⇒ `check.info_gap_drop reason=unsafe` 冇觸發，行照出。

## 3. 修法（三個細改；全部要 lang key、三語同步）

### F1 — 通用 loot table 名字人化（核心）
喺 `Plainify` 加一個 `lootTableLabel(String tableId)`：namespace 剝走 → 去掉已知目錄段（`chests/`／`gameplay/`／
`entities/`／`blocks/`／`spawners/`／`archaeology/`…）→ `_`→空格 → trim。
- 結果非空 ⇒ `ReplyLang.lootTableObtain(lang, label)`（例：`chests/lich_tower` → `Loot table: lich tower`；
  或者用新 key `packai.reply.loot_table_obtain_named`＝`Chest loot: %s`／`宝箱战利品：%s`／`寶箱戰利品：%s`，由 prefix 決定措辭）。
- 結果空／無意義（只剩 `chests` 之類）⇒ **`lootTableGeneric`**（現成 key），**唔准**回落 raw id。
- `Plainify.lootLine` 同 `AcquireAskTool`（model-facing 文字）**共用**同一個 label 函數（單一定義，唔准雙寫）。

### F2 — recipe-type token 人化
`crafting_shaped`／`crafting_shapeless`／`smelting`／`blasting`／`smithing` … 一律經 lang key
（例 `packai.reply.recipe_kind.crafting_shaped`＝`工作台配方`／`Crafting Table recipe`）顯示；
未知 type ⇒ 用 `packai.reply.recipe_kind.generic`（唔好直出 token）。

### F3 — 收緊安全網（防同類再漏）
`AskReplyScrub.PLAYER_UNSAFE_MARKERS` 加：`chests/`、`gameplay/`、`entities/`、`spawners/`、`crafting_shaped`、
`crafting_shapeless`、`→ "`（箭頭＋引號）等形態；**同時**保持 `check.info_gap_drop` trace（唔准靜默丟）。
> 注意：F3 係**防護**（fail-closed），唔係主修；F1／F2 做唔到時 F3 會令行消失（寧願唔出 raw id）。

## 4. 驗收（全部機械、可重跑；貼原文）

- **A1**：`grep -rn "chests/\|gameplay/" <真機 trace body>` 對**新一輪**真機 ask = **0 hit**（同一批 case：iron_sword／furnace／book／diamond_pickaxe）。
- **A2**：`tool.result`（acquire）掃 raw 形態（`chests/`、`crafting_shaped`、`→ "`）= 0 hit 或**已人化**（要貼原文）。
- **A3**：全 repo lang 三語（`en_us`／`zh_cn`／`zh_tw`）新 key 齊；`grep -c` 三檔數量一致；`python tests/check_*.py` 冇新紅。
- **A4**：`compileJava compileTestJava` BUILD SUCCESSFUL ＋ Java harness 全綠（數量同 baseline 一致）。
- **A5**：改前／改後**同一批 case** body 逐字 diff：raw 行消失、其餘段落唔准無故變短（保護已驗收功能）。
- **A6**：`check.info_gap_drop` 冇因為 F3 而**暴增**（要報數字：改前幾多行被 drop、改後幾多）。

## 5. 風險／Non-goals

- 風險：F1 人化後可能同「mod 提供嘅真名」唔一致（今次唔做 lookup，只做機械人化）；F3 太狠會令有用行消失 ⇒ 所以 F3 只加**明確 raw 形態**，並要 A6 數字證明冇濫殺。
- Non-goals：唔改卡片落位／唔改 prompt 邏輯／唔碰 neoforge 樹／唔做「靚名 lookup」（jar-cache 反查留另一 plan）。
- 唔准：為咗綠而刪／放寬現有 check assert。

## 6. 開工前要 SK 拍板

1. F1 措辭：`Loot table: lich tower`（機械人化）還是 `Chest loot: lich tower`（新 key 分目錄類型）？
2. 呢個 plan 要唔要照規矩做**反方 review 到 8:2** 才實作（建議：要，但可以只做一輪窄範圍抽核——改動細、全屬字串層）。
