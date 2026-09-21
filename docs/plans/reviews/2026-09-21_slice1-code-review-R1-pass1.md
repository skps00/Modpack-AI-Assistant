# Slice 1 v4 — code review pass 1（重構／質量）

唯讀。對照未 commit diff 同 `docs/plans/2026-09-21-player-text-humanize-slice1.md` §7。未跑 gradle、未開遊戲、未 commit。

| 嚴重度 | file:line | 問題 | 證據（命令＋輸出） | 建議修法 |
|---|---|---|---|---|
| P1 | `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java:1757-1778` | gap 面板可以多過 3 行。每桶先硬砍到 2（`:1757`／`:1761`／`:1765`），合併後再砍到 `CAP = 3`（`:1772-1776`），然後 `extra > 0` 再 `gaps.add(infoGapMore)`（`:1777-1778`）。more 行喺 cap 外面。例：L/U/other 各 2 條 → 合併 6 → 留 3 → 加 more = **4 行**。單一 class 有 3 條時，每桶 cap 2 會先丟掉一條本可放進總 cap 3 嘅行。三個新 harness 都冇叫 `infoGapLines`，呢個紅而家綠。 | `Read` `AskEngine.java:1757-1778`：`if (lootLines.size() > 2)` … `if (gaps.size() > CAP)` … `gaps.add(ReplyLang.infoGapMore(...))`。`rg "infoGapLines|infoGapMore" forge/1.19.2/src/test` → 零命中（測試樹冇呼叫）。 | more 計入 3：內容最多 2 行再加 more，或者 cap 時預留 more 嗰格。每桶數字 2 係碼自己加嘅，plan §5 只寫「各自 cap」冇寫 2；要嘛刪每桶 cap，要嘛寫死數字並加 harness（3+3+3 → 行數 ≤3；只有 3 條 L → 三條都出或者 more 計入 3）。 |
| P1 | `forge/1.19.2/src/test/java/com/skps9/packai/logic/ToolBuildCanonicalCheck.java:55-58` | hook 順序斷言鑑別力不足。`A1` 係 STANDARD `if` **開頭**（`AskEngine.java:969`），`A2` 係 `InfoCompleteness.append`（`:1012`）。`assert i1 < ih && ih < i2` 只要求 `H` 嘅字元落在兩點之間。成個 `if` body（`:970-1010`）都喺呢段。把 `ensureToolBuildPartsLine` 移入區塊、仍在閉合 `}`（`:1010`）之前，count 仍係 1、順序仍綠。plan §6 負控①要求「移入 A1 區塊 → 紅」。 | `Read` `AskEngine.java:969` = `if (frameKind == ModularFrameStandard.Kind.STANDARD && frameMatch.recipeIndex() != null) {`；`:1010` `}`；`:1011` `body = AskJeiHints.ensureToolBuildPartsLine(`；`:1012` `body = InfoCompleteness.append(`。`ToolBuildCanonicalCheck.java:11-14` 嘅 `A1`／`A2`／`H` 同 `:55-58`。 | 斷言 `H` 喺 A1 區塊配對 `}` **之後**、`A2` 之前。而家 `:1010` 下一行先係 hook，移入 body 必須紅。 |
| P1 | `forge/1.19.2/src/test/java/com/skps9/packai/client/service/KubeJsTooltipTextCheck.java:13-39` | 遊戲語言表分支刪咗都會綠。三個 case：① pack map 已有譯文；② `gameLang = key -> key`（同 key，`tooltipHit` 當缺）；③ `key -> ""`。冇「pack 缺、game 回一段唔等於 key 嘅譯文」。`AskService.resolveTooltipToken` 嘅 `gameLang.apply`（`:896-907`）整段刪走，現有 assert 仍過。`loadKubeJsPackLang`（`:860`）測試零呼叫，json 讀檔壞咗都唔紅。 | `Read` `KubeJsTooltipTextCheck.java:14-39`。`rg "loadKubeJsPackLang" forge/1.19.2/src` → 只得定義 `AskService.java:860` 同呼叫 `:822`。`Read` `AskService.java:888-908`：pack miss 先先 `gameLang`，再 generic。 | 加一條：`pack=Map.of()`、`gameLang` 回「击败虚空之花…」（唔等於 key），assert 含該句、唔含 `kubejs.tooltips.`、唔含 generic。pack 檔至少一個 temp json 打 `loadKubeJsPackLang`。 |
| P2 | `Plainify.java:182-197` 同 `ReplyLang.java:847-853` | 兩套 blocks 判斷，輸出唔同。**L\| 人化唔係兩條**：`AcquireAskTool.humanJarRoute:112` 同 `AskEngine.infoGapLines:1724` 都係 `Plainify.lootLine`。第三出口先分叉：`JarLightIndex.formatFact:296` `case 'L' -> jarLoot`（`factsForAsk:131` 入 prompt）。`lootLine`：`trim`、剝第一個 `:`、`split("/")`、`segs[0].equals("blocks")`，且 `segs.length==2` ＋ leaf＝item path ＋ `officialName` 非空 → `lootTableBlock`，其餘 blocks → `lootTableGeneric`。`jarLoot`：唔 trim、`startsWith("blocks/")` → **一律** generic，冇官方名。同一 `blocks/ritual_brazier`＋對得上 item：lootLine 出「破壞 %s 會掉落」，jarLoot 出泛用句。`formatFact` 冇 itemId，而 `JarLightIndex` 喺禁止改清單，呢個輸出差改唔到簽名。predicate 邊界先係真漂移：裸 `blocks`（無斜線）lootLine 入 blocks 分支出泛用句（唔露 path），jarLoot `startsWith("blocks/")` 為假 → `jar_loot`「掉落：%s」露出 path；前導空白 lootLine `trim` 後人化，jarLoot 當非 blocks 露出 path。`U\|` 亦兩條（plan 只要求 gap）：gap `:1726-1734` 用 `infoGapRelated(officialName\|\|displayName)`；`humanJarRoute:114` 落入 `formatFact` → `jarUsedIn`（含 recipe type）。 | `git diff` `Plainify.java`／`ReplyLang.java`／`AcquireAskTool.java`。`rg "formatFact\\(" forge/1.19.2/src/main/java` → `AskEngine.java:1737`（非 L/U）、`AcquireAskTool.java:114`、`JarLightIndex.java:131`。`Read` `JarLightIndex.java:283-296`。`rg '"packai.reply.jar_loot"'` lang → en `loot: %s`、zh_cn／zh_tw `掉落：%s`。 | 抽一個 `isBlockLootPath`（trim、剝 ns、第一段 `equals("blocks")`）兩邊共用。`jarLoot` 仍只出泛用句（冇 itemId）。裸 `blocks` 同前導空白都走泛用句，唔好落 `jar_loot`。 |
| P2 | `Plainify.java:197`、`ReplyLang.java:853` | 非 `blocks` 玩家句仍含 raw table／loot path。`lootLine` 其餘容器 `return ReplyLang.lootTableObtain(lang, table)`；`jarLoot` else `tr(..., "packai.reply.jar_loot", lootPath)`。模板係 `掉落表：%s`／`Loot table: %s` 同 `掉落：%s`／`loot: %s`。v4.1 寫明為咗 `AcquireJarRoutesCheck` 保留原句。硬約束④（玩家可見唔准 raw path）對非 blocks **未封**。新 harness 把呢個洩漏鎖成必過。 | `Read` `Plainify.java:197`。`Read` `ReplyLang.java:847-853`。`rg loot_table_obtain`／`jar_loot` 三語 json（見上）。`LootLineHumanizeCheck.java:35-36`：`chest.equals(ReplyLang.lootTableObtain(lang, "chests/village/toolsmith"))`。`git diff` plan：負控⑤改成保留原句。 | Slice 1 若接受 v4.1，就喺驗收寫明「非 blocks 仍可含 path」，唔好再當④已封。否則 generic 要連 chests 一齊封，並改 `AcquireJarRoutesCheck`（plan 禁止為綠改 assert，所以而家只可標已知缺口）。 |
| P2 | `AskJeiHints.java:267-274` | doc 同實作唔同。註解寫「canonical 【工具】 parts line」。三語 `packai.reply.tool_build_canonical` 都冇「【工具】」。實作呼叫 `ensureCanonicalQuestLine(answer, canonical, true)`，`true` 會走 `replaceWrongQuestishWithCanonical`（`:342-360`）：行內同時有「任務／任务／quest」同兌換類動詞就整行換走。註解講貼工具行，碼同時做 quest 行刪除。 | `Read` `AskJeiHints.java:267-274`。python 印三語 `tool_build_canonical`（下方 verify）冇「【工具】」。`Read` `AskJeiHints.java:342-360`。 | 註解改成「MODIFIED 先插入 `tool_build_canonical`；`scrubWrongQuestish=true` 會丟 quest-ish 兌換行」。若 Tetra 答案唔該刪任務行，第三個參數改 `false`。 |
| P2 | `AskJeiHints.java:295-309`；`code_change_log.md` 今次條目 | 零件行把 slot token 拼進玩家句：`slot＝name`，slot 來自 `part ` 到第一個 `:`（可以係 `double/head_left`）。`:277` 註解同呢段碼一致。`code_change_log.md` 寫「partsNames 唔剝 name 後面嘅 ` item `」，但 `:295-297` 有 `indexOf(" item ")` 截斷，`ToolBuildCanonicalCheck.java:46` 斷言 `!hammer.contains("item ")`。日誌同碼相反。標點 `＋`／`＝`（`:307`／`:309`）同 tooltip 連接符 `／`（`AskService.java:848`）係 Java 字元，唔經 lang；en_us 句仍會夾中文標點。 | `Read` `AskJeiHints.java:277-310`。`Read` `AskService.java:845-848`。`git diff code_change_log.md` 備註句「partsNames 唔剝 name 後面嘅 ` item `」。 | 改日誌。slot 若唔想玩家見 path token，只輸出 name。連接符放進 lang，或者接受並寫進 plan。 |
| P2 | `docs/plans/2026-09-21-player-text-humanize-slice1.md` | 檔唔喺 §7 白名單，但係今次 `M`。diff 係 v4.1：非 blocks 保留 `lootTableObtain`、負控⑤改期望原句。禁止源碼檔零 diff，所以**唔升 P0**。 | `git diff --name-only` 含該 plan。`git diff -- docs/plans/2026-09-21-player-text-humanize-slice1.md` 只動 v4.1 段落。禁止名掃描見 verify，全部 `none`。 | 規格收窄同實作分開 commit。review 以碼為準：非 blocks 露 path 已係實作行為，唔止 plan 自述。 |

無 P0。

死碼：新方法都有呼叫（`rg`，見 verify）。新增 import（`AskService` 的 `Files`／`StandardCharsets`／`Function`；`AskJeiHints` 的 `Matcher`／`Pattern`）都有用。冇見到未用區域變數。

`OfficialDisplay.lookup`：`LootLineHumanizeCheck.java:12` 存 `prev`，`:47-48` `finally` 寫回。中途 `:41` 再賦值 `id -> ""` 仍喺 try 內，finally 會還原。

永遠成立嘅 assert：冇一條係常數 `true`。弱但唔係永遠真：`ToolBuildCanonicalCheck.java:27` 喺 `:26` `equals("plain answer")` 之後再查唔含「下界合金」（fixture 已保證）。`LootLineHumanizeCheck` 對 `lootTableBlock`／`lootTableGeneric` 嘅 `equals` 有鑑別力（改走 blocks 分支會紅）。真正「唔做都綠」係上表兩條 P1 harness，唔係呢啲重覆 assert。

CJK：production Java **新增行**零個 `"...中文..."`（下方 python）。中文只出現喺三個 harness 嘅預期顯示名／斷言（fixture，唔係 production 分支）。lang json 嘅中文係玩家文案，三語齊。

## 本輪 pass1 結論

無 P0；三個 P1 未補（gap 可成 4 行、hook 閘捉唔到「移入 STANDARD 區塊」、kubejs 遊戲語言表分支冇負控），pass 1 唔當過。

## 我 verify 咗邊幾項

1. 範圍。`git status --porcelain` ＋ `git diff --name-only`。M 檔 13 個；新 harness 3 個（`LootLineHumanizeCheck`、`ToolBuildCanonicalCheck` 喺 `logic`；`KubeJsTooltipTextCheck` 喺 `client/service`，package 同路徑一致）。§7 允許嘅 Java／lang／`tmp-check.gradle`／`code_change_log.md` 對得上。`LlmClient.java`、`HonestMiss.java`、`KubeJsMechanicScan.java`、`update_reply_prompts.py` 無 diff。禁止名：

```
BAD neoforge/ none
BAD JarLightIndex.java none
BAD InfoCompleteness.java none
BAD RecipeEmbed none
BAD AiAssistantScreen.java none
```

未追蹤 `logs/`、`forge/1.19.2/logs/` 喺 porcelain（檔名日期由 2026-09-05 起），唔喺今次 Java diff，冇當成本 slice 改動。

2. CJK／raw id。對 `git diff -U0` 新增行跑 python（`"[^"]*[\u4e00-\u9fff][^"]*"`）。Java 新增行無 CJK 字串；唯一 RAW 提示係 `ReplyLang.java` 新增 `noNs.startsWith("blocks/")`（判斷，唔係玩家句）。zh_cn／zh_tw json 新增 key 有中文（預期）。harness CJK 行：`LootLineHumanizeCheck.java:15-16,23-24,30,39`；`KubeJsTooltipTextCheck.java:15,21`；`ToolBuildCanonicalCheck.java:28,32,39,44`。

3. 兩條 L\|。`rg "lootLine\\("` → `Plainify.java:182` 定義、`:234` graph fact、`PackIndex.java:1318`、`AcquireAskTool.java:112`、`AskEngine.java:1724`、harness。`humanJarRoute` 同 `infoGapLines` 的 L\| 同一個函數。blocks 第二套係 `jarLoot:847`。

4. caller。`rg` 新符號：`lootTableBlock`／`lootTableGeneric`／`infoGapRelated`／`infoGapMore`／`toolBuildCanonical`／`ensureToolBuildPartsLine`／`partsNames`／`resolveKubeJsTooltipFact(s)`／`loadKubeJsPackLang`／`kubejsTooltipGeneric` 都有呼叫。`kubejsTooltipGeneric` 只被 `resolveTooltipToken:908` 用。

5. harness。讀齊三個 `main`。lookup finally 見上。`tmp-check`：python 掃 `*Check.java` 得 56 個 `run*`，同 `tmp-check.gradle` 的 56 個 key **順序相同**（`equal True`）。`research/gen_tmp_check.py` 係 rglob 生成器，本身唔使改先至 regen 得到呢 56；gradle 已對齊，無手改漂移。

6. lang。python `json.loads` 三檔：`key set equal True`；7 個新／改 key 三語都在、無 missing。全文同 plan §2／§3 逐字一致（含 en em dash `Unsure —`）。`kubejs_tooltip_hint` plan §4 只寫「請看遊戲內提示」，碼係更長嘅 Shift 句，三語都有，唔係缺 key。

7. 註解。`lootLine` javadoc（`Plainify.java:178-181`）同分支一致。`humanJarRoute` javadoc（`AcquireAskTool.java:108`）同碼一致。唔一致嘅係上表 `AskJeiHints:267` 同 `code_change_log` 的 `item ` 句。

8. hook 落點。`git diff` hunk 只係 `AskEngine` `@@ -1008,6 +1008,7 @@`（加一行）同 `@@ -1701,24 +1702,90 @@`（`infoGapLines`）。`:826`、`:951-956` 唔喺 hunk。STANDARD `if` 仍由 `:969` 到 `:1010`，hook 喺閉合之後、`append` 之前（`:1011-1012`）。
