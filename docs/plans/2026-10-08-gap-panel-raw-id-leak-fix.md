# Plan v3：玩家可見 raw id 外洩修復（只做已證實嘅 id 形態；model-facing 契約零改動）

- **日期**：2026-10-08（v1 → v2 → **v3**）
- **狀態**：**DRAFT v3 — 未實作，未過 review**（packai 規則：plan → 反方 review ≥8:2 → 才派 cursor）
- **review 進度**：R1 **7:3**（未過）→ R2 **6:4**（未過，v2 解咗 2 條、5 條仍開）→ 本 v3 逐條回應 R2 嘅 6 條 flip condition
- **證據（Hermes 2026-10-08 自己掃；已分 build）**：

| 掃描 | NFWC 沙盒（jar **10-07**≈現行 main） | ATM8 沙盒（jar **09-20**，只作對照） |
|---|---|---|
| body（玩家可見）id 形態（`(ns:)?(chests\|gameplay\|entities\|inject\|structures\|spawners\|blocks)/…`） | **2 / 2 / 2 / 0**（4 條之中 3 條中招） | 1 / 0 / 1 / 0 |
| body（玩家可見）U 形態（`used in <type> →`） | **0 / 4** | **0 / 4** |
| tool.result（model-facing）id 形態 / U 形態 | 9/6/8/(0)、U 0/2/2/1 | 7/(0)/(0)/2、U 1/8/0/6 |

⇒ **已證實嘅玩家層外洩＝id（路徑）形態**；`used in <type> → "…"` **只出現喺 model-facing tool.result（8 條 trace 嘅 body 全部 0）** ⇒ 本 plan **唔做 U 形態**（見 §5 Non-goals），只留一個順手 guard。

## 1. 根因（file:line 親核）

1. `Plainify.lootLine`（`Plainify.java:182-198`）：只喺 `blocks/` 開頭做人化；其餘 `return ReplyLang.lootTableObtain(lang, table)`（`ReplyLang.java:455`）⇒ raw path 入**玩家文字**。
2. **model-facing 保留 raw id 係刻意契約**，由兩個 check 守：`LootLineHumanizeCheck.java:35-36`、`AcquireJarRoutesCheck.java:104-106` ⇒ **唔准改源頭**（v1 錯，v2 已改）。
3. 玩家文字嘅**唯一 post-panel choke point**：`AskEngine.java:1012` `body = InfoCompleteness.append(body, infoGapLines(...))` → 之後經
   `AskResult.of/text/withAnswer` → `AskResult.finalizeAnswer`（`:181`／`:70`）→ **`AskReplyScrub.scrubPromptEcho`（`AskReplyScrub.java:906`）**（`withRecipeCards` 亦喺 `AskResult.java:90` 走同一函數）。
   ⚠️ `rewriteInternalJargon` 唔喺呢條鏈（唔喺 `AskEngine` 內）⇒ v2 寫「同 rewriteInternalJargon 同層」係**錯嘅落點**（R2 attack 1）。
4. 面板行喺 `AskEngine.java:1746` 先經 `AskReplyScrub.isPlayerSafeLine` 閘（`PLAYER_UNSAFE_MARKERS`）⇒ **如果喺 marker 加 id 形態，會將行「丟掉」而唔係「人化」**（R2 attack 2），同 A2 直接衝突 ⇒ marker 唔准加 id 形態。
5. `ReplyLang.tr` 缺 key 時**回 literal key**（`ReplyLang.java:132-134`）⇒ 任何新 key 都要 Java 側先驗存在（R2 attack 7）；本 v3 **唔加新 key**，直接重用 `packai.reply.loot_table_obtain`（`Loot table: %s`／`掉落表：%s`／`掉落表：%s`）＝零新語言風險。

## 2. 修法 v3

### F1 — 共用 `idToLabel(String id)`（單一定義；**唔准**另開第二個 helper）
- 重用現有 convention `ReplyLang.structureObtainLabel`（`:529-542`：剝 `ns:`、`_`／`/`→空格）並擴充：
  1. 剝 `ns:`；
  2. 剝**已知容器目錄**：`chests/`、`inject/`、`inject/chests/`、`gameplay/`、`entities/`、`structures/`、`spawners/`、`blocks/`；
  3. **剩餘取最後一段（leaf）** 做 label（`_`→空格、`/`→空格）；
  4. **碰撞處理（唯一規則、可測）**：若 corpus 內另一個 raw id 嘅 leaf 相同 ⇒ 該 label 後面加 `（<parent 段>）`；碰撞清單要列喺報告（A7）。
- 為何係 leaf 唔係 last-2：`chests/village/village_badlands_house` → leaf「village badlands house」（distinct）；last-2 會變「village village badlands house」（重複）。`chests/abandoned_temple/abandoned_temple_entrance` → leaf「abandoned temple entrance」＝ §6 例子一致。

### F2 — 落點＝**`AskReplyScrub.scrubPromptEcho`**（post-append、單一 choke point）
- 喺該函數內加一步（行為對所有 caller idempotent）：
  - **L 形態**：抽 `packai.reply.loot_table_obtain` 嘅模板（`ReplyLang.lookupLabel`，`:149` public）→ 用 `%s` 位置做前綴／後綴切法（**語言中立**，唔硬編碼「Loot table:」／「掉落表：」）→ 命中後將 `<raw path>` 換 `idToLabel(...)`。
  - 同一掃描器亦處理 `packai.reply.jar_loot` 嘅同類形態（jar 路徑前綴 `L|` 已喺上游剝走，唔使另做）。
- **U 形態唔做**（§5）——但同一函數順手保留「如果日後真有 U 形態入 body，就用 `idToLabel(name)`」嘅一行 guard（唔列為驗收項）。

### F3 — fail-closed 網（**post-humanisation**，語言中立，satisfies A2）
- **唔准**喺 `PLAYER_UNSAFE_MARKERS` 加 id 形態（上游閘會丟行）。
- 改喺 `scrubPromptEcho` **人化之後**做最後掃描：同一支**語言中立 shape scanner**（見下）命中 ⇒ **丟該行 ＋ `AskTrace`（新事件 `check.post_scrub_drop`，要報數）**。
- **Shape scanner（單一定義，F3 同驗收 A1 共用，唔准兩份）**：
  - id：`\b(?:[a-z0-9_.-]+:)?(?:chests|gameplay|entities|inject|structures|spawners|blocks)/[a-z0-9_/.-]+`
  - U（只做掃描用，唔改寫）：`(?:used in|用于|用於)\s+[a-z_]+\s*→`
- 咁樣 A1 同網**唔可能同時盲**（R2 attack 3 嘅解）。

## 3. 驗收（v3；每項要貼原文）

| # | 準則（機械） |
|---|---|
| **A1** | 新一輪真機（現行 build）4 條 case：body 過 shape scanner ⇒ **id 命中 = 0**（用同一支 scanner，唔准另寫 pattern） |
| **A2** | **人化 ≠ 消失**：每條原本中招嘅 case，body **仍要出現可讀 label**（例「掉寶：abandoned temple entrance」）；**唔准**出現「id 冇咗、事實都冇咗」。已知豁免：`InfoCompleteness.mentioned()` 因答案已提及而 dedupe＝**合法**（要對 trace `check.info_gap` 數字講明） |
| **A3** | model-facing 契約不變：`tool.result`（acquire）**仍然**含 raw path（`AcquireJarRoutesCheck` 照綠）；Java harness 總數同 baseline 一致；**零 check 被改弱** |
| **A4** | **冇新增 lang key**（重用 `loot_table_obtain`）；`python tests/check_*.py` 冇新紅；`grep "packai.reply.recipe_kind"` = 0（本 plan 唔引入） |
| **A5** | `compileJava compileTestJava` BUILD SUCCESSFUL；harness 全綠；`check.post_scrub_drop` 改前／改後數字並列（唔准暴增） |
| **A6** | **決定性 fixture 測試**（唔靠跑兩次真機對比）：把**已記錄嘅 body 原文**（8 條 trace 抽出）餵入新 `scrubPromptEcho`，斷言 ① id 命中 0 ② 非 raw 段落逐字不變（只准 raw→label 嘅替換）——可重跑、可審計 |
| **A7** | 碰撞清單：兩包 corpus 內 label 碰撞 = 0，或者明列並附 `<parent>` 消歧後結果 |

## 4. 風險
- label 係**機械人化**唔係官方名（明寫；官方名反查留另一 plan）。
- 若日後有 U 形態入 body（現時 0/8），今次只留 guard、唔列驗收 ⇒ 已知未覆蓋範圍，明寫。

## 5. Non-goals
- 唔改 `Plainify.lootLine`、唔改 `jarUsedIn`、唔改 `AskEngine.java:1746` 個 gate、唔改 acquire 輸出（全部係 model-facing 契約）。
- 唔碰 neoforge 樹、唔加 lang key、唔做官方名 lookup。
- 唔處理 `purpose_lookup` 嘅 `source:kubejs/...`（model-facing；8 條 body **0** 命中）——如日後入 body 另開 plan。

## 6. 等 SK
1. label 措辭用**現成** `掉落表：<label>` 夠唔夠？（唔加新 key ⇒ 零三語風險）
2. 收窄到 leaf 一段（+ 碰撞例外加 parent）＝可接受？
