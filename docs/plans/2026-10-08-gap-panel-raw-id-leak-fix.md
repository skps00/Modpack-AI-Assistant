# Plan v2：gap 面板／答案 raw id 外洩修復（玩家層修，唔動 model-facing 契約）

- **日期**：2026-10-08（v1 → v2；v2 回應反方 review round 1 嘅 5 條 load-bearing 攻擊）
- **狀態**：**DRAFT v2 — 未實作，未過 review**（packai 規則：plan → 反方 review ≥8:2 → 才派 cursor）
- **反方 round 1 比分**：**opposition 7 : plan 3**（Hermes 已逐條核實：`LootLineHumanizeCheck.java:35-36`、`AcquireJarRoutesCheck.java:104-106`、`jar_used_in`＋三語 `quote` 全部真確 → v1 必須改）
- **真機證據（已分版本，重要）**：
  - **主證據**：`%TEMP%\promo_ask_results_20261008-190049\`（NFWC 沙盒，jar＝`autotest-dev-0.2.3` **10-07 build ≈ 現行 main**；每條 trace 都有 `check.info_gap` 事件 ⇒ 現行程式路徑）
  - **輔助（舊 build，只作對照）**：`%TEMP%\promo_ask_atm8_results_20261008-190629\`（jar＝**09-20 build**；4 條 trace **零** `check.info_gap` 事件 ⇒ 舊版走另一條路，**唔可以當現行碼證據**）

## 1. 症狀（逐字）

| 層 | 逐字 | 來源 |
|---|---|---|
| 玩家（zh_cn panel） | `资料有、答案未提：` 之下 `掉落表：chests/abandoned_temple/abandoned_temple_entrance` | NFWC trace `display.body.final` |
| 玩家（en panel，舊 build） | `On record, not in the answer:` / `Loot table: chests/forge` | ATM8 trace（**舊 jar**，只作對照） |
| 玩家（body，兩邊都有） | `used in crafting_shaped → "damage plate maim"`（en）／`用于 crafting_shaped → “…”`（zh_cn） | 兩邊 trace body |
| model-facing | acquire `tool.result` 內同樣有 `Loot table: chests/…`（**設計上保留 raw**） | 兩邊 trace |

中招率（body 掃 ASCII id 形態）：NFWC **3/4**（現行碼）、ATM8 2/4（舊碼）。

## 2. 根因（v2 更正；file:line 已親核）

1. **面板／答案嘅 `Loot table: <raw>`**：`Plainify.lootLine`（`Plainify.java:182-198`）只喺 `blocks/` 開頭做人化，其餘 `return ReplyLang.lootTableObtain(lang, table)`（`ReplyLang.java:455`）⇒ **raw table id 入玩家文字**。
2. **v1 錯誤更正（反方 falsified claim 1）**：`used in <type> → "<name>"` **唔係** `Plainify.java:232-241` 出嘅，係
   `JarLightIndex.formatFact`（`JarLightIndex.java:290-295`）嘅 **U-kind** → `ReplyLang.jarUsedIn`（`ReplyLang.java:843-845`）
   ＝`tr("packai.reply.jar_used_in", type, quote(resultName))`；`type` 就係 `crafting_shaped` 呢啲 recipe-type token，**完全冇人化**。
3. **兩個既有 check 定義咗「model-facing 保留 raw id」呢個契約**（v1 想改就會撞）：
   - `LootLineHumanizeCheck.java:35-36`：`Plainify.lootLine(lang,"minecraft:stone","chests/village/toolsmith")` **必須等於** `ReplyLang.lootTableObtain(lang, 同上 raw)`。
   - `AcquireJarRoutesCheck.java:104-106`：acquire 輸出**必須仍然包含** raw path（`chests/village/moon/blacksmith`／`inject/chests/end_city_treasure`／`entities/ender_dragon_extended`）。
   ⇒ **結論：raw id 喺 model-facing 係刻意設計**（模型要用精確 id）；缺陷只喺**玩家層冇做人化**。
4. 現有玩家層防護網 `AskReplyScrub.isPlayerSafeLine`（`:1522-1536`，靠 `PLAYER_UNSAFE_MARKERS`）捉唔到呢啲 id 形態（NFWC 4 條 trace `check.info_gap_drop` = **0**）。

## 3. 修法 v2（**玩家層加法，零 check 削弱**）
> 大原則：**model-facing 一律唔郁**（兩個 check 照綠）；修全部落喺**玩家可見文字嘅 rewrite 層**（`AskReplyScrub`，已有 `rewriteInternalJargon` 同類機制）。

### F1 — 共用一個「id → 可讀標籤」人化器（單一定義）
- 重用現有 convention：`ReplyLang.structureObtainLabel`（`:529-542`，已做 namespace 剝走＋`_`／`/`→空格）；擴充做 `idToLabel(String id)`：
  1. 剝 `ns:`；2. 去掉**已知容器目錄段**（`chests/`、`inject/`、`inject/chests/`、`gameplay/`、`entities/`、`structures/`、`blocks/`、`spawners/`）；
  3. 剩餘多段路徑**收成最後 ≤2 段**（避免 `abandoned_temple/abandoned_temple_entrance` 變一長串）；4. `_`／`/`→空格。
- **唔准**另開第二個 helper（v1 被反方指出重複）。
- 碰撞測試入驗收：同一批 corpus 內兩個**唔同** raw id **唔准**map 到同一個 label（或者明列碰撞清單）。

### F2 — 玩家層 rewrite 規則（`AskReplyScrub`）
- **L 類**：`<loot前綴>：<raw path>`（前綴由 lang key `packai.reply.loot_table_obtain` 嘅模板抽，**唔硬編碼語言字面**）→ 換成 prefix ＋ `idToLabel(...)`。同時處理 `packai.reply.jar_loot` 同 `loot_table_obtain` 兩個 key 嘅形態。
- **U 類**：`used in <type> → "<name>"`／`用于 <type> → “<name>”` → `<type>` 經 **新 lang key**（`packai.reply.recipe_kind.<type>`，三語）人化；未知 type → `packai.reply.recipe_kind.generic`。**唔郁** `jarUsedIn` 本身（model-facing 不變）。
- 落點：同 `rewriteInternalJargon` 同一層，喺 **最後一次 scrub 之後**（保證玩家睇到嘅係最終文字）。

### F3 — 防護網（fail-closed，**語言中立**）
- `PLAYER_UNSAFE_MARKERS` **唔准**加 `→ "`（反方實證：en 用 `"%s"`、zh_cn `“%s”`、zh_tw `「%s」` ⇒ 呢個 marker 喺 en 會誤殺 F2 啱啱修好嘅行、喺 zh 完全唔 match）。
- 改為加 **ASCII id 形態**（語言中立）：`chests/`、`gameplay/`、`entities/`、`inject/`、`structures/`、`spawners/`、`crafting_shaped`、`crafting_shapeless`。
- **要有 false-drop 測試**：三語各餵一段正常答案（含新 lang key 出嘅人化字），證明**冇**被誤 drop；`check.info_gap_drop` 數字改前／改後都要報（唔准暴增）。

## 4. 驗收（v2；全部機械、要貼原文）

| # | 準則 |
|---|---|
| **A1** | 新一輪真機（**現行 build**）同一批 case：body 掃 id 形態（`chests/`／`gameplay/`／`entities/`／`inject/`／`structures/`／`crafting_shaped`）= **0**（v1 只掃兩款 → 反方指出漏 `crafting_shaped`／`inject/`） |
| **A2** | **人化 ≠ 消失**：每條原本外洩嘅 case，body **仍然要有嗰個事實**（例：出現「宝箱战利品：…」或同等可讀標籤）；唔准出現「raw 冇咗但資料都冇咗」 |
| **A3** | model-facing 契約**不變**：acquire `tool.result` **仍然**含 raw path（＝`AcquireJarRoutesCheck` 照綠）；Java harness 總數同 baseline 一致、**冇任何 check 被改弱** |
| **A4** | 三語 lang key 齊（`en_us`／`zh_cn`／`zh_tw` 數量一致）；新 key 有用到（`grep` 證明）；`python tests/check_*.py` 冇新紅 |
| **A5** | `compileJava compileTestJava` BUILD SUCCESSFUL；`eval`／harness 全綠 |
| **A6** | 改前／改後同一批 case **body 逐字 diff**：只有 raw 形態被換成人化、其餘段落唔准無故變短；`check.info_gap_drop` 改前／改後數字並列 |
| **A7** | 碰撞測試：corpus（兩包 trace ＋ `LootForwardIndex` 抽樣）內 label 碰撞 = 0，或明列清單 |

## 5. 風險／Non-goals
- 風險：機械人化唔等於官方名（今次唔做 lookup，明寫）；label 可能變長（用 ≤2 段收窄）；F3 誤殺（A6 digit 證明）。
- Non-goals：唔改 `Plainify.lootLine` 契約、唔改 `jarUsedIn`、唔改 acquire 輸出、唔碰 neoforge、唔做 jar-cache 名反查（另 plan）。
- **唔准**：為綠而刪／放寬現有 assert（v2 設計正正係為咗唔需要改佢）。

## 6. 等 SK 拍板
1. `recipe_kind` 措辭：`Crafting Table recipe` / `工作台配方`？（我建議：`crafting_shaped`→工作台配方、`crafting_shapeless`→無序合成、`smelting`→熔煉…）
2. 收窄到「最後 ≤2 段」夠唔夠？（例 `chests/abandoned_temple/abandoned_temple_entrance` → 「abandoned temple entrance」）
