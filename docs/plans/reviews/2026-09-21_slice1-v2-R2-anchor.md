# Slice 1 v2 — R2 數字／行號獨立核實

- 身份：獨立核實（唔係反方、唔係正方）
- plan：`docs/plans/2026-09-21-player-text-humanize-slice1.md`
- md5：`5e88b077d7717039380dd541ab25b47e`（`len=10965`；同指令指定值一致）
- 日期：2026-09-21
- 禁 gradle、禁開遊戲。Headless 回傳只據源碼，冇跑 JVM。

## 總表

| # | plan claim（原文引句） | 我重算嘅真值 | 判定 | 定義／corpus | 命令 |
|---|---|---|---|---|---|
| 1 | 「harness（53 → 56 綠）」 | 今日 `*Check.java` = **53**；`tmp-check.gradle` `'run…'` entry = **53**；兩集合類名差集空。三個新名 `LootLineHumanizeCheck`／`KubeJsTooltipTextCheck`／`ToolBuildCanonicalCheck` repo 內零檔。56 = 53+3，未綠。 | OK（起點 53；56 係目標算術，唔係今日綠數） | 分子＝`src/test/java/**/*Check.java` 檔數；分母同集合＝gradle map key `run<Class>`。corpus＝`forge/1.19.2`。 | `python` 掃 `*Check.java` 同 regex `'run([A-Za-z0-9]+)'`；差集 `[]`／`[]` |
| 2 | 「`tests/check_*.py` **124/124 綠**」 | **TOTAL=124 FAIL=0**。FAIL 清單空。Glob 檔數亦 124。 | OK | 分子＝RC≠0 嘅腳本；分母＝`tests/check_*.py` 展開數。cwd＝repo root。 | `for f in tests/check_*.py; do python "$f" >/dev/null 2>&1 \|\| echo FAIL; done` → `TOTAL=124 FAIL=0` |
| 3 | 「`ReplyLang.lootTableObtain` … 4 個呼叫點：`AcquireAskTool:112`、`AskEngine:1712`、`PackIndex:1318`、`Plainify:212`」 | `forge/1.19.2/src` grep 5 行：定義 `ReplyLang.java:454` ＋ 上述 4 個呼叫，行號全中。無第 5 個呼叫。 | OK | 呼叫＝`ReplyLang.lootTableObtain(` 出現，扣除方法定義。corpus＝`forge/1.19.2/src`（main+test）。 | `rg -n lootTableObtain forge/1.19.2/src` |
| 4 | 「兩式：`blocks/<x>`（JarLight，無 ns）＋ `<ns>:blocks/<x>`（`LOOT_TO_TABLE:38-39`）」 | 兩式都係真，但 regex 唔係寫死 `blocks/`。見下「regex 原文」。`lootKeyFromPath` 丟 `data/<ns>/loot_tables/` 前綴，回 `blocks/x`。`parseLootJson` 寫 `L\|`+該 key（`:273`）。Plainify group 2 強制有 `:`。PackIndex `:1315` `table:` 後整段傳入。 | OK | 式 A＝`lootKeyFromPath` 回傳（無 `:`）。式 B＝`LOOT_TO_TABLE` group 2（必須 `ns:path`）。 | 讀 `Plainify.java:38-39`、`JarLightIndex.java:205-214,273`、`check_jar_light_index.py:141-143` |
| 5a | 「`PLAYER_UNSAFE_MARKERS`（`:148-166`：`render_recipe_cards`、`[RECIPE_CARDS]`、`【JEI`、`role=`、`必须`、`禁止`、`不要用`、`请明说`、`DSML` …）」 | 陣列真身 17 條（`:148-166`）。plan 點名嘅 9 個全部在表內，無多出。`…` 未列：`注意：JEI`、`已完整扫描`、`已完整掃描`、`推荐合成`、`推荐取得`、`<invoke`、`<tool_calls`、`[[tools]]{`。 | OK（子集；`…` 已標未列全） | 全表＝該 `String[]` 字面量。 | 讀 `AskReplyScrub.java:148-166` |
| 5b | 「`looksLikeAbsenceClaim`（`:39-78`：`沒有列出`、`没有列出`、`not listed`、`無配方`、`未持物品`…）」 | 方法真係 `:39-78`。五個 token 都在。`…` 仲有 `no crafting recipe`、`does not list`、`沒有可顯示的配方`、`無 JEI`、`無已知配方`、`no held item`、`有用配方 0 筆` 等。`:87` 叫 `scrubAbsenceClaimsWhenCards`；有卡時刪**命中行**，唔係成篇 answer。 | OK | token＝方法內 `contains` 字面量。 | 讀 `AskJeiHints.java:39-78`、`:110-138`；`AskResult.java:84-88` |
| 6 | 「文案（forge ×3 語）：『本包資料未見此物嘅取得途徑；…不確定，請以遊戲內為準。』→ 已逐字核唔中任何 runtime scrubber token」 | §2 **只得 1 句引文**，冇獨立 en_us／zh_cn／zh_tw。該句（剝 `**`）對 17 個 `PLAYER_UNSAFE_MARKERS` 同 absence 字面量（含 `沒有`／`没有`／`合成配方`／`not listed`／`無配方`／`未持物品`）**零命中**。 | WRONG（三語唔存在）；一句本身唔中 token＝OK | 測試字串＝plan §2 唯一引號句，去掉 `**`。對照＝5a 全表 ＋ 5b 字面量。 | `python` `in` 測試；輸出 `PLAYER_UNSAFE hits none`，absence 無 HIT |
| 6b | 閘層（`check_honest_miss.py:91-106`、`check_reply_prompt_keys.py:54-67`）禁：`禁止`、`必须`、`必須`、`不要用`、`请明说`、`請明說`、`do not invent`、`not indexed`、`未索引`、`render_recipe_cards`、`role=` | `honest_miss:95-101` 有前 7 項＋`render_recipe_cards`＋`role=`。**冇** `not indexed`／`未索引`。後兩者只在 `check_reply_prompt_keys.py:60`（且只掃 `KEYS` 內 `ask_miss_*_player`）。`:80` 禁任意 `%`；`reply_prompt_keys:45-49` 只數 `%s`。 | WRONG | 斷言字面量 ∈ 該行範圍。corpus＝兩支 py。 | 讀兩檔 `:80-106`、`:49-67` |
| 7 | 行號 anchor（指令清單） | 見下「行號」。符號都喺所寫範圍。貼邊（`officialName` 方法止於 102、範圍寫到 105）唔當錯。 | OK | 判法＝符號／語句喺範圍內。 | `python` 印 `start-end` 行 |
| 7b | 「≥ `:951`（ensureHowToGetBody 之後）、且必須早過 `:969-1010`；harness：**hook index > STANDARD block end**」 | `:930`＝`ensureQuestStatusVisible`。`:951-956`＝`ensureHowToGetBody` 呼叫。`:969-1010`＝STANDARD 區塊；`replaceHowToGetBody` 喺 `:980`。block end＝`:1010`。「早過 969」同「index > 1010」同時真唔可能。 | WRONG | 行號＝`AskEngine.java` 該段真行。 | 讀 `:923-1010` |
| 8 | 「`kubejs/assets/kubejs/lang/` 只有 `zh_cn.json`」；「`:1093` hash 唔含 lang code」 | 該目錄只得 `zh_cn.json`（144284 bytes，**1958** keys）。`mods/` 231 個 jar 內 `assets/kubejs/lang/*.json`＝**0**（含 `kubejs-forge-1902.6.2-build.73.jar`）。`:1093` `sha256(rel + "\\0" + src)`，無 lang。 | OK | 目錄＝instance `minecraft/kubejs/assets/kubejs/lang/` 一層 listing。jar＝`mods/*.jar` namelist。hash＝該行運算元。 | `python` `iterdir`＋`json` len＋`zipfile` namelist |
| 9 | 「`OfficialDisplay.officialName`（`:85-105`）」經 ItemResolver／`getHoverName` | `:85-102` `officialName` → `lookup` 預設 `hoverLookup`（`:60`）。`hoverLookup:74-78`：`ItemResolver.stackFromId` 然後 `stack.getHoverName().getString()`。`stackFromId:196` 用 `Registry.ITEM`。例外／空 stack → `""`（`:79-80`、`:99`）。Headless 未跑 JVM。 | OK（路徑）；回傳值見未能核實 | 源碼呼叫鏈，唔係實跑。 | 讀 `OfficialDisplay.java:59-102`、`ItemResolver.java:182-211` |
| 10 | §7 白名單每個檔 | 現有 25 路徑全部 EXISTS 且 `git ls-files` tracked。3 個新 Check 檔 MISSING（全 repo 只出現喺 plan）。見下清單。 | OK（新檔未存在＝plan「未改任何 code」） | tracked＝`git ls-files -z` 集合成員。 | `git ls-files` ＋ `Path.exists` |
| 11a | §8 驗收 `gradlew.bat jar`（JDK 17） | **§8 正文冇呢句、冇 JDK 路徑。** `forge/1.19.2/gradlew.bat` 存在。JDK 目錄 `C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2` 存在（AGENTS 路徑，唔係 plan 寫嘅）。未跑 jar。 | WRONG（命令唔喺 §8） | 搜 plan 全文 `gradlew`／`JDK`＝0。 | `rg` plan；`Path.exists` |
| 11b | 真機物品 id 存在 | 五個 id 都喺最新 index `entries[].id`：`ars_nouveau:ritual_brazier`、`tetra:modular_double`、`witherstormmod:withered_nether_star`、`kubejs:god_bless_empty_necklace`、`kubejs:god_bless_full_necklace`。 | OK | corpus＝`config/packai/item-index/v1_1.19.2_forge_zh_cn_a05e3f80d457.json` 精確 `"id"`。17 個 index json 都含字串。 | `python` json |
| 11c | full tooltip「能够激发一部分高级器官的／激活效果」；empty「擊敗虛空之花…即可充能」 | full 真 tooltip key `kubejs.tooltips.god_bless_full_necklace.1`＝`用于在沙漠维度地牢中进行神意挑战`。器官句係 `kubejs.tooltips.active_pill.1`／`.2`，唔係項鍊。empty 真值＝`击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能`（簡體）。檔內無「擊敗虛空之花」。 | WRONG | corpus＝instance `kubejs/assets/kubejs/lang/zh_cn.json`。 | `python` key 過濾 |
| 11d | 「子路徑（`blocks/special/ice` 等 8 條）」 | `mods/*.jar` 內 `loot_tables/blocks/<a>/<b>.json`＝**8**：`blocks/special/{ice,blue_ice,packed_ice}`＋`blocks/urn_loot/{common,epic,rare,uncommon,urn_loot}`。若定義收窄做只 `blocks/special/*` 則係 3。Vanilla client jar 唔喺 `mods/`，未掃。 | OK（三節 `blocks/*/*` 定義） | 分子＝zip namelist 去 `loot_tables/` 後、`blocks/` 下 ≥3 段。 | `python` `zipfile` |
| 12 | 「總行數上限 3」＋`AskTrace.event`（`:133` public、`:135-148` 唔 NPE）＋`PackAiConfig.askTraceJsonl()` | cap 3 **現碼冇**：`infoGapLines:1699-1722` 加完就 return。`AskTrace.java:133` `public static void event(String type, Consumer<JsonObject> extra)`；`:134-148` try；session 空就 return；`catch (Throwable)`。`PackAiConfig.java:1188` `public static boolean askTraceJsonl()` 存在。 | OK（API）；cap 3 係待做唔係現況 | 簽名＝該行 modifiers。cap＝方法內有冇常數 3 截斷（無）。 | 讀 `AskTrace.java:129-148`、`PackAiConfig.java:1187-1194`、`AskEngine.java:1699-1722` |

## regex 原文

`Plainify.java:38-39`：

```
(?i)^item:([a-z0-9_]+:[a-z0-9_./-]+)\s+-\[loot\]->\s+table:([a-z0-9_]+:[a-z0-9_./-]+)$
```

group 2 必須含 `:`，所以係 `<ns>:<path>`，`blocks/` 只係 path 一種。

`JarLightIndex.lootKeyFromPath:205-214`：搵 `/loot_tables/`，截後面，剝 `.json`，**唔加 namespace**。測試鎖 `data/minecraft/loot_tables/chests/village_toolsmith.json` → `chests/village_toolsmith`（`check_jar_light_index.py:141-143`）。`data/<ns>/loot_tables/blocks/x.json` → `blocks/x`。

## PLAYER_UNSAFE_MARKERS 全表（`:149-165`）

`render_recipe_cards`、`[RECIPE_CARDS]`、`【JEI`、`注意：JEI`、`已完整扫描`、`已完整掃描`、`推荐合成`、`推荐取得`、`role=`、`必须`、`禁止`、`不要用`、`请明说`、`DSML`、`<invoke`、`<tool_calls`、`[[tools]]{`

## 行號（真行內容；符號在範圍＝OK）

| 範圍 | 真行 | 判定 |
|---|---|---|
| `ReplyLang.java:1210-1211` | `acquireIndexMiss` → `tr(..., "packai.reply.acquire_index_miss")` | OK |
| `:454-456` | `lootTableObtain` → `tr(..., "packai.reply.loot_table_obtain", tableId)` | OK |
| `:122-163` | `tr` `:122-143` 只 `lookup` → `BUNDLES`；`loadBundles:169` 只讀 `/assets/packai/lang/{zh_cn,zh_tw,en_us}.json`，唔讀 pack kubejs | OK |
| `HonestMiss.java:109-130` | `:116` 叫 `acquireIndexMiss`；`:121-130` 係另一方法 `acquireMissFactsPlayer`（叫 `askMissAcquirePlayer`） | OK |
| `AskEngine.java:951` | `body = AskReplyScrub.ensureHowToGetBody(`（呼叫延到 `:956`） | OK |
| `:969-1010` | STANDARD 區塊；`:980` `replaceHowToGetBody` | OK |
| `:1011` | `body = InfoCompleteness.append(...)` | OK |
| `:1244` | `String miss = ReplyLang.acquireIndexMiss(lang);` | OK |
| `:1699` | `infoGapLines` 方法頭（plan 正文未寫 1699；指令要求核。方法包住 `:1712`） | OK |
| `:1712` | `gaps.add(ReplyLang.lootTableObtain(lang, table));` table＝`L\|` 後無 ns key | OK |
| `:1462` | `return Plainify.humanizeGraphFact(gf);` | OK |
| `:826`（唔准郁） | `List<String> promptFacts = capable ? List.of() : factsLive;` | OK（行存在） |
| `:930` | `body = AskJeiHints.ensureQuestStatusVisible(body, acquire, lang);` | OK |
| `AskReplyScrub.java:948-965` | `replaceHowToGetBody` | OK |
| `:985-1016` | `ensureHowToGetBody` | OK |
| `:148-166` | `PLAYER_UNSAFE_MARKERS` | OK |
| `AskJeiHints.java:146-155` | `ensureQuestStatusVisible` | OK |
| `:263-285` | `ensureCanonicalQuestLine`；`:274` `ReplySources.HEADER` 前插入 | OK |
| `:291-317` | `replaceWrongQuestishWithCanonical` | OK |
| `AskResult.java:84-97` | `withRecipeCards`；`:87` `scrubAbsenceClaimsWhenCards(answer, hasCards)` | OK |
| `AcquireAskTool.java:43-48` | `toolMissNote` 英文，含 `do not invent`，首段 `;` 前無 CJK | OK |
| `:112` | `ReplyLang.lootTableObtain(lang, table)` | OK |
| `Plainify.java:64-86` | `displayName` 由 path token 砌字 | OK |
| `:183` | `humanizeGraphFact` | OK |
| `:212` | `return ReplyLang.lootTableObtain(lang, m.group(2));` | OK |
| `PackIndex.java:1318` | 同上，table 來自 `table:` 後綴（可有 ns） | OK |
| `OfficialDisplay.java:85-105` | `officialName` 本體 `:85-102` | OK |
| `KubeJsMechanicScan.java:792` | `h.note = extractNote(body);` | OK |
| `:1093` | `String hash = sha256(rel + "\0" + src);` | OK |
| `InfoCompleteness.java:46` | `append(...)` | OK |
| `:80-98` | 搵 `ReplySources.HEADER` 再插入 | OK |
| `check_info_completeness_hook_order.py:76-99` | `:83-85` append 只准 1 次；`:95-97` `i > block_end` | OK |
| `AiAssistantScreen.java:834-869` | 卡 interleave；`:844` `Component.translatable("packai.status.waiting")`（先例係呢個 key，唔係 kubejs tooltip） | OK |
| `AskService.java:1994` | `AskReplyScrub.scrubPromptEcho(...)` | OK |
| `:2003` | 同上，fallback | OK |
| `:2007` | `ReplyLang.askMissAcquirePlayer(...)` | OK |
| `LlmClient.java:467` | `Plainify.humanizeGraphFact(f)` | OK |
| `check_honest_miss.py:80-106` | `:80` `"%" not in`；`:82-88` 內部 key 必須 `not indexed`／`未索引`＋`do not invent`／`禁止捏造`；`:91-106` 玩家 key 禁詞（見 6b） | OK（範圍）；禁詞歸屬見 6b WRONG |
| `check_reply_prompt_keys.py:11-42` | `KEYS` `:11-28`；`TREES` `:29-32` 含 forge **同** neoforge；`:37-42` 雙樹 iterate | OK |
| `:49-67` | `:49` 非 llm_style 嘅 `%s` 數＝0；`:50-53` 內部 key 必須 not-indexed；`:54-67` 玩家 key 禁詞含 `:60` `not indexed`／`未索引` | OK |
| `ModularFrameStandard.java:24-28` | `enum Kind { STANDARD, MODIFIED, UNKNOWN }` | OK |
| `AskTrace.java:133` | `public static void event(String, Consumer<JsonObject>)` | OK |
| `PackAiConfig.java:1188` | `public static boolean askTraceJsonl()` | OK |

## 白名單存在／tracked

全部相對 repo root。`git ls-files`＝tracked。

EXISTS+tracked：

- `forge/1.19.2/src/main/java/com/skps9/packai/logic/{ReplyLang,HonestMiss,AskEngine,AcquireAskTool,Plainify,PackIndex,KubeJsMechanicScan,AskJeiHints,InfoCompleteness,ModularFrameStandard,AskResult,LlmClient}.java`
- `forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java`
- `forge/1.19.2/src/main/resources/assets/packai/lang/{en_us,zh_cn,zh_tw}.json`
- `tests/{check_honest_miss,check_reply_prompt_keys,check_tool_miss_teaching,check_frame_standard_recipe_line,check_modular_frame_standard,update_reply_prompts}.py`
- `forge/1.19.2/tmp-check.gradle`（**tracked**；還原可用 git）
- `research/gen_tmp_check.py`
- `code_change_log.md`

MISSING（plan 先寫、而家無檔）：

- `LootLineHumanizeCheck.java`
- `KubeJsTooltipTextCheck.java`
- `ToolBuildCanonicalCheck.java`

當時 `git status --porcelain`（寫本報告前）：`?? forge/1.19.2/logs/`、`?? logs/`。白名單檔無未提交修改。

## WRONG 清單

1. **C8 hook 兩行號約束矛盾**（§6：早過 `:969`，同時 `hook index > STANDARD block end`）。block 係 `:969-1010`，end＝1010。兩條同時寫入 harness → **會永遠紅**（無整數 index 同時 `<969` 同 `>1010`）。只實作其中一條就唔會永遠紅，但 plan 兩條都寫死。
2. **`not indexed`／`未索引` 唔喺 `check_honest_miss.py:91-106`**。只喺 `check_reply_prompt_keys.py:60`，而且只掃 `KEYS`。plan 又話新 key **唔入** `KEYS`（`:29-42` 會逼改 neo）。若新玩家句含「未索引」而閘只抄 honest_miss 現範圍、又唔入 KEYS → **現有閘永遠綠**（假綠）。唔係永遠紅。
3. **§2「forge ×3 語」只得 1 句。** en_us／zh_cn／zh_tw 冇獨立文案可對。一句本身唔中 scrub token，所以 **唔會** 因為 scrub 而永遠紅。三語驗收而家無字串，閘亦未有該 key → 唔會因為呢句而紅或綠。
4. **full 項鍊期望 tooltip 張冠李戴。** 期望「能够激发一部分高级器官的／激活效果」屬於 `active_pill`，唔係 `god_bless_full_necklace`。真 tooltip 係沙漠地牢神意挑戰。若驗收要求該句出現喺 full 項鍊答案 → **永遠紅**（lang 檔唔會產出該句）。
5. **empty 期望用繁體「擊敗虛空之花」**，唯一 lang 檔係簡體「击败虚空之花…即可充能」。精確 grep 繁體 → **永遠紅**。若改搜簡體「击败虚空之花」同「即可充能」就中。
6. **§8 冇 `gradlew.bat jar`、冇 JDK 路徑。** 檔同 JDK 目錄本身存在。缺寫 **唔會** 令現有閘永遠紅或永遠綠（命令根本冇入驗收正文）。

## 未能核實清單

1. **`officialName` headless 實跑回傳。** 源碼：`Registry.ITEM`／`getHoverName` 拋錯 → `hoverLookup` 回 `""` → `officialName` 回 `""`。要解：開一次 **唔使遊戲窗** 嘅 headless harness（指令禁 gradle，今次冇跑）。`AskDisplayNameCheck.java:187` 斷言 `officialName("chestcavity:cud").isEmpty()`，未執行。
2. **「56 綠」。** 三個新 Check 未存在，未跑。要解：落地後 `gradlew` 跑齊 56 個 `run*Check`。
3. **cap 3、新 lang key、C8 canonical 行。** 現碼未有。要解：改完再數 `infoGapLines` 輸出行數同 lang json。
4. **遊戲語言表第 2 步**（`I18n`／`Component.translatable` 對 kubejs key）喺 client 實際顯示咩。要解：開遊戲，語言切 zh_tw（pack 無 `zh_tw.json`）睇 tooltip。`:844` 只證明 API 用過 `packai.status.waiting`。
5. **Vanilla `blocks/` 子路徑會唔會令「8 條」變多。** `libraries/` 無 `client/1.19.2/*.jar`（今次 glob 0）。要解：搵到 1.19.2 client jar 再數 `loot_tables/blocks/*/*`。
6. **真機四 case 答案文案**（破壞官方名、gap ≤3、B 新句、tooltip 中文）。要解：沙盒開遊戲跑 ask，grep trace ＋ `latest.log` 嘅 `Pack AI display body ver=`。物品 id 本身已在 index，唔使開遊戲先證明 id 存在。
7. **R1 比分「正方 4 : 反方 6」。** 唔喺 code artifact。要解：對返 R1 review 檔原文。
