# Slice 1 v2 — R2 反方（唯讀）

- plan md5 `5e88b077d7717039380dd541ab25b47e`（同 pin 一致，未停手）
- 無改 plan／code；無 gradle；無開遊戲

## 1. 六條 flip condition

| 編號 | 判 | 證據 file:line | 我跑嘅命令 |
|---|---|---|---|
| 1 B 禁詞 | PARTIAL | plan §2 只得 **一句**文案，§2 無 `en_us`／`zh_cn`／`zh_tw`。真表 `AskReplyScrub.java:148-166` 17 個 marker；`AskJeiHints.java:39-78` 嘅 `contains` literal 45 個。呢句對 **predicate** 唔中（唔係對每個 literal）。負控只寫喺 plan §8.2「runtime scrubber 詞 → 紅」，冇叫 harness 呼叫真 method。 | `python` 抽 §2 文案＋兩份 Java token；再以 `looksLikeAbsenceClaim` 同等 boolean 跑該句 → `absence False` `unsafe []`。省略：unsafe 8 個（`注意：JEI`、`已完整扫描/掃描`、`推荐合成`、`推荐取得`、`<invoke`、`<tool_calls`、`[[tools]]{`）；absence literal 32 個唔喺 §2。對照句 `Unsure. JEI does not list… not listed…` → `absence True` 但閘 `check_reply_prompt_keys.py:54-67` 仍會綠（有 `Unsure`、無閘禁詞）。 |
| 2 C1 | PARTIAL | 開頭已寫反查搬 Slice 2、C1-lite 唔露 raw path。但 §3 仍寫「有 ns 且 item-index 唯一命中」。`ItemIndexCache.java:45` `Entry(id,label,nbt,schem,dedupe)` 冇 loot owner。而且 `AcquireAskTool.java:113-115`：`lootTableObtain` 結果 **唔含** table id 就會 `+ " " + table`。無 ns 嘅 `L\|`（JarLight）正正走泛用句、句內無 path → 呢段 **必定**把 `blocks/…` 補返出街。 | `rg lootTableObtain` → 4 caller 同 plan 一致。讀 `AcquireAskTool.java:109-116`。讀 `ItemIndexCache.java:45` 同 `:140-159` toJson 欄位。 |
| 3 C5 | PARTIAL | §4 已刪 `ReplyLang.tr` 查 pack lang（真係死路：`ReplyLang.java:122-163` 只查 packai bundle，缺 key `:132-134 return key`）。改遊戲語言表＋全缺唔出 raw key＋解析唔好烘入 cache（`KubeJsMechanicScan.java:1093` hash = `sha256(rel+"\0"+src)`，無 lang）都寫咗。**冇** zh_tw／en_us「唔准出 raw key」斷言；§8.4 只逼中文字串。 | 讀 `ReplyLang.java:122-163`、`KubeJsMechanicScan.java:792,1049,1093`。plan §8.1／§8.4 對照。 |
| 4 C7／C4 | RESOLVED | C4 claim 刪咗（plan L8、L57）。§5 寫死唔改 `AskEngine:1011` `InfoCompleteness.append`。§8 四 case **冇**卡／面板次序期望。白名單明文唔准 `AiAssistantScreen`／`RecipeEmbed`／hook-order 閘。 | `rg "C4\|卡之後\|RecipeEmbed" docs/plans/2026-09-21-player-text-humanize-slice1.md` → 只得刪 claim／唔准行，驗收段零中。 |
| 5 C8 | NOT-RESOLVED | plan L62 **同一句兩個相反位置**：①「必須早過」`:969-1010`；② harness「hook index **> STANDARD block end**」（即 1010 之後）。UNKNOWN 負控（L64）同 `update_reply_prompts.py` 入白名單（L65、L74）係有。位置未寫死。 | 讀 plan L62。讀 `AskEngine.java:950-1011`。 |
| 6 白名單 | PARTIAL | 仍要改、又有行號：`AskService.java:1994/2003/2007`、`AskResult.java:84-97`、`LlmClient.java:467`、`AskEngine.java:1462`、`update_reply_prompts.py`、兩個 frame check。C4 兩個檔正確排除。**需要改但 plan 冇符號落點**：`AcquireAskTool.humanJarRoute:113-115`（檔喺 `{…}` 檔名堆，冇呢個 if）；`KubeJsMechanicScan.appendParts:1049`（note 真正拼入 fact 嘅位；plan 只講「consume/display」）。`ItemIndex.java` 唔喺白名單，公開 API 係 `searchReady:111`（分數搜尋），唔係 id→loot 反查。 | `rg` plan 對 `humanJarRoute\|appendParts\|AskService\|update_reply_prompts\|AiAssistantScreen`。讀 `ItemIndex.java:111`。 |

## 2. LD 存活表

| LD | 判 | 證據 | 反轉條件 |
|---|---|---|---|
| LD1 兩層禁詞 | 死 | 兩層有點名，但全表用省略號；×3 語未寫出。唯一句對真 predicate 唔中（見 §1.1）。英文最小閘句可以過 `check_reply_prompt_keys.py:54-67` 同時被 `looksLikeAbsenceClaim` 整行刪（有卡，`AskResult.java:87`）。 | 寫死 en_us／zh_cn／zh_tw 三句；負控改為呼叫 `isPlayerSafeLine` **同** `looksLikeAbsenceClaim`（唔好抄省略詞表）。 |
| LD2 C1-lite 兩式＋4 caller | 死 | 4 個 `lootTableObtain` caller 齊（`AcquireAskTool:112`、`AskEngine:1712`、`PackIndex:1318`、`Plainify:212`；`LOOT_TO_TABLE` `Plainify.java:38-39` 確係 `ns:path`）。但 `:113-115` 喺譯文唔含 table 時把 raw path 接返去。無 ns 泛用句一定中招。`item-index 唯一命中` 無 match key；`Entry` 無 loot 欄。 | 寫明刪或改 `:113-115`，令「句內無 path」唔再補 table。唯一命中改成可執行規則（例如 `ns:blocks/<leaf>` → id `ns:<leaf>` 再 `officialName`，空名就泛用句），或者刪走 item-index 句、全部走泛用句。 |
| LD3 C5 解析搬 consume、hash 無 lang | 存活 | `extractNote:893` 喺 parse；cache `:1093` 無 lang；玩家見到嘅 note 係 `appendParts:1049` `note:`+`h.note`。搬去 1049（或 `factsForItem` 出口）先係 consume。`factsForItem` 呼叫方係 `AskService.java:765`（client）。 | 若實作改喺 `extractNote` 入面翻譯，即死（烘入 cache）。 |
| LD4 C7 只改內容、cap 3、class 優先 | 存活 | 優先序 L>U>R、每 class cap、總行數 3、另有 N 項都寫咗（plan §5）。唔改 `:1011`。`R\|` 而家經 `JarLightIndex.formatFact:287` → `ReplyLang.jarCraft`，lang 係 `合成 %s：%s`（`zh_tw.json` `packai.reply.jar_craft`）→ 會出 `crafting_shaped`。改內容可以喺白名單內嘅 `AskEngine.infoGapLines:1699-1722` 做，唔使郁 `JarLightIndex`。§8 無卡序。 | 驗收再要求卡喺 gap 前／後 → 死（白名單唔准 `AiAssistantScreen`）。 |
| LD5 C8 hook＋三態 | 死 | 位置自相矛盾（§1.5）。`ensureHowToGetBody` 喺 `if (loop.intent() != PURPOSE)` **裡面**（`AskEngine.java:950-956`）。未分類問題 intent 係 PURPOSE（`:335-338`，`AskLoopState.java:27` 預設都係）。「早過 969」可以插喺 if 入面 → PURPOSE 唔貼零件行。`replaceHowToGetBody:948-964` 由 how-to-get 頭替到來源 header；`ensureCanonicalQuestLine:274-282` 就插喺來源 header 前 → 若 hook 早過 980 **而且** `frameKind==STANDARD && recipeIndex!=null`（`:969`），零件行喺 how-to-get span 內會被整段換走。MODIFIED 唔入 `:969`，所以 MODIFIED 本身唔會被 980 覆蓋。UNKNOWN 負控有寫。 | 只留一個位：`956` 嘅 `}` **之後**，而且 **1010 之後**（STANDARD 替換已完）、`return` 之前。刪「早過 :969」。寫明 PURPOSE 都跑。 |
| LD6 白名單 | 死 | 檔名覆蓋大部分，但令 LD2 失敗嘅符號 `:113-115` 同 C5 出口 `:1049` 冇寫成必改落點。`§7` 又把 `InfoCompleteness.java` 放進可改集合，同時禁止改呼叫位置——檔可改、位不可改，實作者可以改 `append` 本體繞過「只改內容」而閘仍綠（閘鎖嘅係 `AskEngine` 呼叫次數／位置，唔係 `append` 內部）。 | 必改符號列到方法:行：`humanJarRoute` 嘅 contains-table 分支、`appendParts` 嘅 note 拼接。`InfoCompleteness.java` 移出可改集合（內容改喺 `infoGapLines`）。 |
| LD7 驗收可 grep、唔做會唔會綠 | 存活 | §8.4 四 case：禁 `blocks/`、禁 `crafting_shaped:`、要 B 新句、要指定中文 tooltip。而家 lang `掉落表：%s`（`zh_tw.json` `loot_table_obtain`）同 `jar_craft` 會令「唔做」紅。`tests/check_*.py` 實數 124（`ls tests/check_*.py \| wc -l`）。 | 「或泛用句」令 ritual_brazier **唔做唯一命中都綠**（見 finding）。若有人拿掉禁詞、只留「或泛用句」，C1 獨特路徑無驗收。 |

## 3. 新 finding

| 嚴重度 | file:line | 證據（命令＋輸出） | 反轉條件 |
|---|---|---|---|
| P0 | plan L62 vs `AskEngine.java:950-1011` | 同一句要求 hook「早過 :969-1010」**同**「hook index > STANDARD block end」。讀 `AskEngine.java:950-956`：`ensureHowToGetBody` 只喺 `intent != PURPOSE` 先跑；`:969` 先係 `STANDARD && recipeIndex!=null` 先 `replaceHowToGetBody`（`:980`）。兩個約束不能同時真。PURPOSE（`:335-338` else 分支）若 hook 放喺 if 內，零件行永不出現。 | 見 LD5 反轉。 |
| P0 | `AcquireAskTool.java:113-115` | 讀檔：`if (!table.isEmpty() && (line == null \|\| !line.contains(table))) return line + " " + table;`。C1-lite 泛用句不含 `blocks/x` → 條件真 → raw path 出街。`AskEngine:1712`／`PackIndex:1318`／`Plainify:212` **冇**呢段補 path。plan 只點 `:112`。 | 白名單已含 `AcquireAskTool.java`，但 plan 必須寫明拆掉呢個 fallback，否則實作「只改 lootTableObtain」仍然漏 path。 |
| P1 | plan §3 vs `ItemIndexCache.java:45`、`ItemIndex.java:111` | Entry 欄位無 loot table。公開查詢係 `searchReady(queryNorm, limit)` 分數搜尋，`null`=未 ready。`officialName`（`OfficialDisplay.java:85-101`）失敗回 `""`：`hoverLookup:68-81` `catch (Throwable) return ""`；`ItemResolver.stackFromId:182-211` 用 `Registry.ITEM`，空／AIR → `ItemStack.EMPTY`。headless **唔炸**，但無官方名。4 個 caller 都喺 `logic/`，in-game client registry 起好先叫到名；plan 冇寫空名 → 泛用句。 | 刪「item-index 唯一命中」，或寫死派生 id＋`officialName` 空則泛用句，並喺 harness 用現成 `OfficialDisplay.lookup` test hook（`:60`），唔好指望 headless `Registry.ITEM`。 |
| P1 | plan §8.4 ritual 行 | 「破壞〈官方名〉會掉落 **或** 泛用句」+ 禁 `blocks/`。只出泛用句、永遠唔做唯一命中，真機 case 仍綠。 | 有 ns 唯一先要求官方名句；泛用句只允許多解／零解／無 ns。 |
| P1 | `KubeJsMechanicScan.java:3-26` vs plan §4 `I18n` | 呢個 class **零** `net.minecraft` import。`I18n` 喺 client mappings `client_mappings.txt:24693` `net.minecraft.client.resources.language.I18n`。`Component.translatable(String)` 喺 `:35078`，回傳 `MutableComponent`，**唔係**已翻譯字串；`AiAssistantScreen.java:844` 係 `.getString()`。`javap -c -p pe$1`（`client.jar`，mappings 上 `Language$1`）：`getOrDefault` = `Map.getOrDefault(key, key)` → **缺 key 就回 raw key**。plan 寫咗全缺唔出 key，呢步必須顯式 `equals(key)` 先丟棄；直接信 `getString()` 會漏 key。`PackAiMod.java:28-30` dedicated server 早退，而家 `factsForItem` 只由 client `AskService:765` 叫，所以 server 唔會行到 I18n——**若**把 `I18n` 放進 `KubeJsMechanicScan` 而 headless `*Check` 叫到，classpath 無 client class 會 `NoClassDefFoundError`。 | 翻譯放 client 側、logic 只留 raw key；或者 `catch (Throwable)` 後走泛用句。缺 key 當 `result.equals(key)` 就棄用。補 zh_tw／en_us「輸出不得含 `kubejs.tooltips.`」斷言。 |
| P2 | plan L89 | 寫「焦點前後量度指令寫明」，檔內冇任何量度指令。 | 補真實指令，或者刪呢句。 |
| P2 | plan L62「930 會被 951／980 覆蓋，所以要早過 969」 | 對 **MODIFIED**（C8 要貼嘅態）`:969` 唔跑，早過定遲過都唔會被 980 覆蓋。對 **STANDARD**，早過 980 先至會被 `replaceHowToGetBody` 換走（span 去到來源 header，`AskReplyScrub.java:961`）。「早過先安全」呢句理由係反嘅。第三個 writer：`AskMarkerRepair.repair:69-78` 只補 marker，唔刪【工具】行。`INTERNAL_SECTION_TOKENS`（`AskReplyScrub.java:27-36`）無「工具」，`TAG_CJK:202` 唔剝【工具】。`AskResult.java:87-90` 只刪 `looksLikeAbsenceClaim` 行。`AskService.java:1974-2003` 只喺 `bodyOnly` 空白先 `scrubPromptEcho`。所以 MODIFIED 零件行若唔含 absence token，**1010 之後**插入唔會被現有 writer 刪。plan 唔准郁 `RecipeEmbed`；卡插入唔改呢句文字。 | 同 LD5：hook 放 1010 之後。 |

### (a)–(e) 短答

- **(a)** 「≥951」**唔**自動覆蓋所有 intent。`:950-956` 先至係 PURPOSE 守衛。守衛內：PURPOSE（含未分類，`:338`）唔跑 `ensureHowToGetBody`，亦唔跑插喺度嘅 hook → 無零件 canonical 行，LLM 原文留低（C8 症狀可以仲喺）。守衛外（`:957` 起，包括 `:1011`）所有 intent 都跑。
- **(b)** 「貼咗就唔會被覆蓋」只對 **MODIFIED** 真（唔入 `:969`）。STANDARD 會用 `replaceHowToGetBody` 換走 how-to-get 到來源 header 之間嘅字。1011 之後嘅 `InfoCompleteness.append`、`AskResult.withRecipeCards`、`AskService.ensureNonEmptyBody` 唔會刪一條正常【工具】句（見 P2）。無第三個刪行 writer，除非句中 absence token。
- **(c)** 4 個 caller 都叫得到 `officialName`（同 package 層）。headless：`Throwable` 吞咗 → `""`，唔炸。in-game 要 registry 已有呢個 item。空名 plan 冇 fallback。
- **(d)** `Component.translatable` 本身係 common（`net.minecraft.network.chat`），但 **唔**等於玩家 tooltip 字，要 `.getString()` 而且語言表已注入。`I18n` 係 client-only。`KubeJsMechanicScan` 而家無 MC import；server 入口早退，現呼叫鏈唔會喺 dedicated server 跑到。直接 `I18n` 進呢個 class 係 headless／server 風險，唔係而家 client ask 必炸。
- **(e)** 抽查過嘅否定句：`ReplyLang.tr` 唔查 pack lang = 真（`:160-163`）。cache hash 無 lang = 真（`:1093`）。【工具】唔喺 scrub token = 真（`:27-36`）。「item-index 完全冇 loot owner」= 真（`Entry:45`）。「4 caller 改完就唔露 path」= **假**（`:113-115`）。「hook 早過 969 就唔會被覆蓋」= **假**（STANDARD span）。「×3 語已逐字核」= **假**（只有一句）。

## 4. 總評

v2 真係收咗 C4（驗收唔再靠卡位）、C5 嘅死路 fallback、cache 唔含 lang、UNKNOWN 負控、同多數白名單檔。未夠開工：C8 hook 一句兩個相反行號，而且 PURPOSE 守衛未提；C1-lite 嘅主路徑（無 ns 泛用句）會被 `AcquireAskTool:113-115` 把 raw path 補返去。B 嘅三語文案同全表負控仍係省略。

必修（最多 3）：
1. 拆 `AcquireAskTool.java:113-115` 補 path；item-index「唯一命中」要可執行，否則刪。
2. C8 hook 只准一處：`AskEngine` `956` 閉合之後 **兼** `1010` 之後；刪「早過 :969」。
3. B 寫出三語全文；負控呼叫真 `isPlayerSafeLine`＋`looksLikeAbsenceClaim`。

正方 4 : 反方 6

## 5. 我 verify 咗邊幾項

- `md5sum docs/plans/2026-09-21-player-text-humanize-slice1.md` → `5e88b077d7717039380dd541ab25b47e`
- `python`：§2 文案只 1 句；對 `PLAYER_UNSAFE_MARKERS` 17 個全 miss；`looksLikeAbsenceClaim` 同等式 → `absence False`。對照英文句 → `absence True`。
- 讀 `AskReplyScrub.java:148-166,948-964,27-36`；`AskJeiHints.java:39-78,110-138,263-285`；`AskEngine.java:335-338,950-1011,1462,1699-1722`；`AcquireAskTool.java:109-118`；`Plainify.java:38-39,210-212`；`PackIndex.java:1314-1318`；`ReplyLang.java:122-163,454-456,818-820,1210-1234`；`OfficialDisplay.java:59-101`；`ItemResolver.java:182-211`；`ItemIndexCache.java:45,140-159`；`ItemIndex.java:111`；`KubeJsMechanicScan.java:3-26,792,893-918,1049,1093`；`AskResult.java:84-97`；`AskService.java:1968-2007,765`；`PackAiMod.java:27-30`；`JarLightIndex.java:277-297`；`AskMarkerRepair.java:69-78`；`ModularFrameStandard.java:14-28`；`InfoCompleteness.java:80-99`
- `rg lootTableObtain` forge `*.java` → 4 caller
- `rg factsForItem` → `AskService.java:765` 係 kubejs note 出口呼叫方
- lang：`packai.reply.loot_table_obtain` = `掉落表：%s`；`jar_craft` = `合成 %s：%s`；現有 `ask_miss_acquire_player` 三語已存在（plan 未寫新三語）
- `client_mappings.txt:24693` I18n；`:35078` `Component.translatable`；`:33230` `Language.getOrDefault`
- `javap -classpath …/1.19.2/client.jar -c -p pe$1` → `Map.getOrDefault(key, key)`
- `ls tests/check_*.py | wc -l` → `124`
- `rg` plan 內 C4／卡序 → 只得刪除與禁止，驗收無卡序
