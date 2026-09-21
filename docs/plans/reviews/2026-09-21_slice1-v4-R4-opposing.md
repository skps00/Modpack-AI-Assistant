# R4 反方 — Slice 1 plan v4（最後一輪）

- 日期：2026-09-21
- 角色：獨立反方
- plan pin：`md5sum docs/plans/2026-09-21-player-text-humanize-slice1.md` → `1c35a4b7bbf785f982a0ebbc02d6d0bc`（163 行）。同指示 pin。未停手。
- 範圍：只核清單 A／B／C。C 只報矛盾／永遠紅／永遠綠。

## 1. 清單 A（R3 必修 3 條）

| # | 判定 | 證據 | 反轉條件 |
|---|---|---|---|
| A1 | RESOLVED | plan §8 第 6 點（plan:146-153）寫死 **client 語言＝zh_cn**、`heldItem.name`＝`仪式火盆`、「所有字串斷言一律用簡體原文」。項鍊同段：`击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能`、`用于在沙漠维度地牢中进行神意挑战`（plan:153）。同一 zh_cn。 | 真機斷言改回繁體，或火盆案同項鍊案語言拆開。 |
| A2 | RESOLVED | plan:57-59 寫死 `segs == 2` ＋ `container.equals("blocks")` ＋ `leaf.equals(pathOf(itemId))` 先官方名句；其餘泛用。plan:71-77 七對。本輪 python 重跑七對，七對全 OK（見底「命令」）。`blocks/special/ice`＋`minecraft:ice` → segs 3 → generic。`blocks/rope`＋`farmersdelight:rope` → segs 2、leaf＝`rope` → official。`isTrivialBlockSelfLoot` 鏡像：brazier／rope 為 true、`blocks/special/ice` 為 false（`LootForwardIndex.java:106-119`）。負控④同 trivial 過濾唔衝突：④係 `lootLine` 段數，唔係 cache 過濾。 | 段數定義改成「`/` 出現次數」或 ns 剝法同 plan:57 唔同，令④變 official 或③變 generic。 |
| A3 | RESOLVED | plan:148-150 明文：`L\|blocks/ritual_brazier` 只存在於現有 jar-cache；`JarLightIndex.parseLootJson:270` 重掃會因 `LootForwardIndex.isTrivialBlockSelfLoot` 丟；驗收唔准清／重建 cache。真碼：`JarLightIndex.java:270-273` `continue` 之後先 `addFact(..., "L\|"+key)`。trivial 條件 `lootPath.equals("blocks/"+itemPath)`（`LootForwardIndex.java:119`）。現有 cache：`config/packai/jar-cache/43e1636e1cfb.json` 有 `"ars_nouveau:ritual_brazier":["L\|blocks/ritual_brazier"]`（231 個 json 裡 1 個檔命中）。 | cache 檔冇呢條 `L\|`，或 `parseLootJson:270` 唔再 call `isTrivialBlockSelfLoot`。 |

## 2. 清單 B（R3 獨立核實 3 條 WRONG）

| # | 判定 | 證據 | 反轉條件 |
|---|---|---|---|
| B1 | RESOLVED | `AskEngine.java` 全檔：A1 字串 `if (frameKind == ModularFrameStandard.Kind.STANDARD && frameMatch.recipeIndex() != null) {` count＝**1**（`:969`；repo grep 亦只此一行）。閉合 `}`＝`:1010`。A2 `body = InfoCompleteness.append(` count＝**1**（`:1011`）。裸 `if (loop.intent() != AskLoopState.Intent.PURPOSE)` count＝**2**，`:369` 同 `:950` 各 1。plan:108-109 明文禁止用裸 `Kind.STANDARD` 同裸 PURPOSE `if`。`frameKind == ModularFrameStandard.Kind.STANDARD` count＝**2**（`:363`、`:969`）＝ plan 講嘅「2 次」。token `Kind.STANDARD` 全檔 **4**（`:363` `:470` `:969` `:1082`）；plan 嘅「2」對齊 `==` 形式，唔係 raw token。錨斷言用 A1／A2，唔用裸 token，所以 4≠2 唔令 harness 永遠紅。 | A1 或 A2 再出現第二次；或驗收改去搜 raw token `Kind.STANDARD` 又要求 count＝1。 |
| B2 | RESOLVED | `JarLightIndex.formatFact:296` `case 'L' -> ReplyLang.jarLoot(lang, rest)`。`ReplyLang.jarLoot:827-828` 而家 `tr(..., "packai.reply.jar_loot", lootPath)`。lang：`zh_cn.json:333` `掉落：%s`（`zh_tw.json:337`、`en_us.json:337` `loot: %s`）。修法 plan:63：`jarLoot` 內剝 `ns:` 後 `blocks/` 開頭 → 泛用句，其餘保留原文。**做得到**：呢個修法只用 `ReplyLang.tr`，唔使 call `Plainify`／`OfficialDisplay`。`ReplyLang.java` import 只有 java＋gson（`:3-14`）。現有已有 `ReplyLang.java:783` → `Plainify.displayName`；`Plainify` 又 call `ReplyLang`（例如 `Plainify.java:66`）。循環已存在而且編譯得。`jarLoot` 唔使再加 `OfficialDisplay`。caller grep（forge main）：`jarLoot` 只 `:296`；`lootTableObtain` 只 4 點（`AcquireAskTool.java:112`、`AskEngine.java:1712`、`PackIndex.java:1318`、`Plainify.java:212`）；`formatFact` 只 `JarLightIndex.java:131`、`AcquireAskTool.java:118`（非 L）、`AskEngine.java:1715`（U／R）。`routeLinesForItem:144-165` 回 raw code，但只餵上面兩個人話化點。plan 把 4 個 `lootTableObtain` 改 `lootLine`（唔出 path）＋ `jarLoot` 封 `blocks/`。**blocks/\* 冇第 3 個玩家出口。** 非 `blocks/` 嘅 `L\|` 經 `jarLoot` 仍出 raw path——plan:63 寫明「其餘保留原文」，唔係漏網。 | 再發現一個唔經 `lootTableObtain`／`jarLoot` 就把 `L\|blocks/...` 寫入玩家 body 嘅 caller。 |
| B3 | RESOLVED | `formatFact` 嘅 `jarCraft` yield 喺 `JarLightIndex.java:288`。`ReplySources.HEADER` 定義 `ReplySources.java:11`（`public static final Pattern HEADER`）。插點 `AskJeiHints.java:274` `ReplySources.HEADER.matcher(body)`。`tests/check_reply_prompt_keys.py:23` 係 `"packai.reply.ask_miss_acquire_player"`。`neoforge/README_PAUSED.md` 存在（1727 bytes）。`check_dual_tree_diff_symmetry.py:36` `PAUSE_MARKER`；`:59-63` 無 `--no-paused` 就 `PAUSE_MARKER.is_file()` → paused；paused 時 asymmetry 係 WARN 唔係 FAIL（`:148-166`），`failed==0` 就 exit 0。plan:48「預設 paused、驗收用預設」成立。措辭「forge-only lang diff 只 WARN」偏嚴：只改 forge、neo 冇同相對路徑 diff 時，`common` 空（`:122-131`），直接 `OK no dual-tree intersection`，**唔入 WARN**，仍然 exit 0。唔令 §8 python 閘永遠紅。 | `README_PAUSED.md` 刪走而驗收又唔加 `--no-paused` 以外嘅豁免；或 `jarCraft`／`HEADER` 行號再漂。 |

## 3. 清單 C（v4 新文字：只報矛盾／永遠紅／永遠綠）

| # | 判定 | 證據 | 反轉條件 |
|---|---|---|---|
| C1 五 key × 三語 | 無矛盾／唔永遠紅／唔永遠綠 | plan:66-70 五 key。python：17 個 `PLAYER_UNSAFE_MARKERS`（`AskReplyScrub.java:148-166`，數＝17）對 15 句零命中。`looksLikeAbsenceClaim`（`AskJeiHints.java:39-78`）全部 literal 移植後 15 句 `absence=False`。兩閘禁詞（`check_reply_prompt_keys.py:54-67`、`check_honest_miss.py:91-106`）只掃 `ask_miss_*`／`acquire_index_miss`，唔掃新 key。新 key 唔入 `KEYS`（plan:64）。`%` 命中只係模板 `%s`，唔入上述閘。 | 五 key 被加進 `KEYS`，或某句寫入 `必须／禁止／没有列出／not listed`＋recipe。 |
| C2 `%s` vs 閘 | 無撞 | `check_reply_prompt_keys.py:41-49`：`%s` 數＝0 喺 `for key in KEYS` 內，而且 `llm_style*` 例外係 n＝2。新 key 唔喺 `KEYS`。`check_reply_lang.py` 全文只 assert `ReplyLang.java` 有 `resolveMcLanguageCode` 等字串＋本地 `bundle_lang`（`:14-43`），**冇**三語 key 齊全檢查。forge-only 加 key 唔會因呢支閘紅。 | `KEYS` 加咗 `loot_table_block`（含 `%s`）或 `check_reply_lang.py` 改成全 key 三語齊套。 |
| C3 `isPlayerSafeLine` 誤殺 | 唔會誤殺計畫內合法模板 | `AskReplyScrub.java:1522-1535`：空字串 false；其餘 substring 中 17 marker 先 false。五 key 靜態字串零 marker（含 `Parts = %s` 唔含 `role=`）。驗收句「破坏 仪式火盆 会掉落」「由某个掉落表提供…」同樣唔中。代入 `%s` 後，官方名若含 `必须／禁止／不要用／请明说／推荐合成／推荐取得` 先會被丟——驗收用名唔中，唔構成永遠紅。 | 驗收改用一個官方名本身含上述 marker 嘅物品，又要求該行必須出現。 |
| C4 剔完 `R\|` 空 gap → 空 header | 唔會 | `InfoCompleteness.append`（`InfoCompleteness.java:46-49`）`gapLines==null` 或 `isEmpty()` → return 同一個 `answer`，唔加 header。`:65-66` kept 空亦 return `answer`。header 只喺 `:68-72` kept 非空先 append。plan:91 `InfoCompleteness.java` 唔改；`:1011` 呼叫位唔改。全 `R\|` 被剔後傳入空 list → 無 header。 | `infoGapLines` 改為自己先寫 `infoGapHeader` 再交空 body，或 `append` 空 list 仍寫 header。 |
| C5 `U\|` `OfficialDisplay.officialName` | 同 package，可直接叫 | `AskEngine` 同 `OfficialDisplay` 都係 `package com.skps9.packai.logic`（`OfficialDisplay.java:1`、`AskEngine` 同 package）。`officialName` 係 `public static`（`OfficialDisplay.java:85`）。同 package 唔使 import。而家 `AskEngine` 零 `OfficialDisplay` 引用（grep 無命中），加 call 合法。空名走 `Plainify.displayName`（plan:96）同 `officialName` 回 `""`（`:85-100`）對得上。 | `officialName` 改 private，或搬去 client package。 |
| C6 §8 唔做都綠 | 無「功能斷言永遠綠／永遠紅」 | 功能斷言唔做會紅：case 1 舊文 `掉落表：blocks/...` 含 `blocks/`（plan:148 唔准）；wither 舊文係「没有取得路径」（plan:152 唔准）；項鍊要 tooltip 原文（plan:153）；tetra 唔准 `crafting_shaped:`（plan:151）；harness 而家 `*Check.java`＝**53**，plan:143 要 56。compile／jar／python 124／0 係回歸閘，唔做功能都綠，但唔扮功能證明。C9（plan:156）寫明唔入 code，唔係 pass/fail。cache 前提同「必須含火盆＋掉落」唔衝突：官方句「破坏 %s 会掉落」含兩者且唔含 `blocks/`；泛用句唔含「掉落」，但 cache 有 `L\|` 時走 `lootLine` 官方句（A2①）。`mentioned()`（`InfoCompleteness.java:115-142`）唔會因中文官方名抑制該行（行內無 `ns:path`／path run／引號／`->`）。 | 真機 body 只出泛用句又唔 append 官方 gap 行，則「必須含掉落」變紅——那是實作偏離 plan，唔係 plan 自相矛盾。 |

## 4. 比分

正方 9 : 反方 1

清單 A／B 全部 RESOLVED。清單 C 冇矛盾、冇永遠紅、冇永遠綠。扣 1 分只係措辭精度（B1 token `Kind.STANDARD`＝4 而 plan 寫 2，實際對齊嘅係 `==` 形式＝2；B3 forge-only diff 係 intersection 空就 OK，唔係 WARN），兩者都唔翻盤、唔使必修。

## 5. 我 verify 咗邊幾項

1. `md5sum docs/plans/2026-09-21-player-text-humanize-slice1.md` → `1c35a4b7bbf785f982a0ebbc02d6d0bc`；`wc -l` → 163。
2. python 數 `AskEngine.java`：A1＝1、A2＝1、`frameKind == ModularFrameStandard.Kind.STANDARD`＝2、`Kind.STANDARD`＝4、`if (loop.intent() != AskLoopState.Intent.PURPOSE)`＝2。行號 363／369／470／950／969／1011／1082。
3. python 七對 `(table, itemId)` leaf 分類，七對 OK；trivial 鏡像 brazier true、rope true、ice false。
4. python：17 markers × 15 句零命中；absence 移植 15 句 false；`%s` 計數。
5. python：`neoforge/README_PAUSED.md` exists，1727 bytes；`*Check.java` 53；jar-cache `43e1636e1cfb.json` context `"ars_nouveau:ritual_brazier":["L|blocks/ritual_brazier"]`。
6. 讀：`JarLightIndex.java:205-299`、`LootForwardIndex.java:106-119`、`ReplyLang.java:1-14,454-456,783,827-828`、`AcquireAskTool.java:108-118`、`AskEngine.java:363-369,950-956,969-1011,1699-1722`、`PackIndex.java:1314-1318`、`Plainify.java:38-39,64-86,210-212`、`AskReplyScrub.java:148-166,1521-1535`、`AskJeiHints.java:39-78,263-285`、`InfoCompleteness.java:46-99,115-142`、`OfficialDisplay.java:1,60,85-101`、`ReplySources.java:11`、`check_reply_prompt_keys.py:11-67`、`check_reply_lang.py` 全文、`check_honest_miss.py:80-106`、`check_dual_tree_diff_symmetry.py:15-20,36,59-63,122-166`。
