# Slice 1 v3 — R3 數字／行號獨立核實

- 日期：2026-09-21
- 核實方：獨立重算（冇抄 plan 內數字、冇讀 R2 報告）
- plan：`docs/plans/2026-09-21-player-text-humanize-slice1.md`
- md5：`0f674c68296caebb7b358085d0400350`（`python hashlib.md5`，15594 bytes）＝ pin。繼續。
- 標籤：FACT＝今次命令／檔案讀到。INFERENCE＝由 FACT 推出、冇直接跑到。

## 總表

| # | v3 claim（原文引句＋plan 行號） | 我重算嘅真值 | 判定 | 定義／corpus | 命令 |
|---|---|---|---|---|---|
| 1a | 「目標＝現有 key `packai.reply.ask_miss_acquire_player`」「已喺 `KEYS`（`check_reply_prompt_keys.py:11-28`）」（plan L29、L40） | tuple 喺 L11–28。key 喺 **L23**。全文 16 條 key，呢條係其中一條。 | OK | 分子＝該字串係唔係 `KEYS` 元素。分母＝`tests/check_reply_prompt_keys.py` L11–28。 | Read `tests/check_reply_prompt_keys.py` L10–28 |
| 1b | 「佢 iterate 兩棵樹 ⇒ 只改 forge 會令 `--no-paused` 模式 FAIL；驗收一律用 paused 模式」（plan L40） | **因果唔成立。** `check_reply_prompt_keys.py` 嘅 `TREES`（L29–32）係 forge＋neo 各做 predicate，**唔比較兩樹字串相等**，檔內 **零** `paused`／`--no-paused`。lang JSON **唔喺** `check_dual_tree_sync.py`（`TREES` 只係 `src/main/java`）。`check_dual_tree_diff_symmetry.py` 只喺 **同一 relative path 兩邊 diff 都有** 先 FAIL；只改 forge 唔入 `common`。另：今日 `check_dual_tree_sync.py --no-paused` **已經 RC=1、`FAIL (37)`**（既有 Java byte drift／缺 twin，含 `AskEngine.java`、`AcquireAskTool.java`、`LlmClient.java`、`HonestMiss.java`；`KubeJsMechanicScan.java`、`ModularFrameStandard.java` 係 missing twin）。預設（auto paused）RC=0、`SUMMARY paused=True fail=0 warn=102`。呢個紅 **唔係** KEYS 造成。 | WRONG | 「改文」＝只改 forge lang 值。閘＝`check_reply_prompt_keys.py` 同 `check_dual_tree_sync.py`。 | Read 兩支 py；`python tests/check_dual_tree_sync.py` RC=0；`python tests/check_dual_tree_sync.py --no-paused` RC=1 |
| 2 | 三語全文唔中 `PLAYER_UNSAFE_MARKERS`（`:148-166`，17 條）、唔中 `looksLikeAbsenceClaim`（`:39-78`）、唔中兩閘禁詞（`check_honest_miss.py:91-106`、`check_reply_prompt_keys.py:54-67`）；含 Unsure／不確定／不确定；唔含 `%`；唔含 未索引／not indexed（plan L31–34） | marker 陣列 **17** 條（L148–166）。三句：`isPlayerSafeLine` 鏡像＝true；absence 鏡像＝false；兩閘禁詞命中＝空；三句分別含 `Unsure`／`不确定`／`不確定`；`%`＝0；`未索引`／`not indexed`＝無。負控：`沒有列出`、`not listed recipe` → absence true；`請明說` → python 閘命中，但 **`isPlayerSafeLine` 對繁體 `請明說` 係 true**（marker 只得簡體 `请明说`）。 | OK（三句） | 分子＝plan L31–33 三句逐字。predicate＝由 `AskReplyScrub.java` 抽出 17 marker ＋ `isPlayerSafeLine` 規則（`role=` 用 lower）；absence＝`AskJeiHints.java` L45–77 布林照抄；閘＝兩 py 嘅 `assert` 字面聯集。 | `python -c` 載入 17 marker 後逐句印 `safe/abs/gate/unsure/pct/idx`。輸出：`en/cn/tw safe True abs False gate []` |
| 3a | `AcquireAskTool.java:109-119` 含 `:113-115`「譯文唔含 table 就 `+ " " + table`」（plan L52） | L109 `humanJarRoute(String lang, String code)`。L112 `ReplyLang.lootTableObtain(lang, table)`。L113–115 真碼：`if (!table.isEmpty() && (line == null \|\| !line.contains(table))) { return (line == null \|\| line.isBlank() ? "Loot table:" : line) + " " + table; }` | OK | 行號＝檔內 1-based。 | Read `AcquireAskTool.java` L108–119 |
| 3b | `mergeJarRoutes:77` 傳入（plan L52） | L68 方法簽名 `mergeJarRoutes(String itemId, String lang, List<String> loose)`。**L77**＝`addLine(jarRoutes, seenJar, humanJarRoute(lang, code));`。而家 **冇** 傳 `itemId`。 | OK（行號） | 呼叫點行。 | Read L68–77 |
| 3c | `AskEngine.java:1712`（plan L44、L53） | L1712＝`gaps.add(ReplyLang.lootTableObtain(lang, table));`，喺 `infoGapLines`（L1699 起）。 | OK | 同上 | Read L1699–1722 |
| 3d | `PackIndex.java:1314-1318`（plan L44、L53、L92） | L1314 `else if (rest.startsWith("table:"))`；L1315 `table = rest.substring("table:".length())`；L1316 `isTrivialBlockSelfLoot(id, table)`；L1318 `ReplyLang.lootTableObtain(lang, table)`。 | OK | 同上 | Read L1314–1318 |
| 3e | `Plainify.java:210-213`（plan L44、L53、L88） | L210 `LOOT_TO_TABLE.matcher`；L212 **`return ReplyLang.lootTableObtain(lang, m.group(2));`**（而家傳 group 2，唔係 group 1）。 | OK（行號；現行傳參係 group 2） | 同上 | Read L210–213 |
| 3f | `LlmClient.java:467` 簽名唔改（plan L53、L94） | L467＝`readableFacts.add(Plainify.humanizeGraphFact(f));`。簽名係 `humanizeGraphFact(String fact)`（L183）。 | OK | 同上 | Read `LlmClient.java` L462–468；`Plainify.java` L183 |
| 4 | 「4 個呼叫點都拿得到 focus item id」（plan L46） | 見下節逐點。四點都有一條拿 item id 嘅路，但 **唔係四個都叫 focus／held**。 | OK（見分點；名稱要跟真變數） | 「拿得到」＝該 `lootTableObtain` 語句所屬方法、或其 plan 點名嘅直接 caller，有 item id 變數或 regex group。corpus＝forge main 四個呼叫（grep `lootTableObtain(` 除 `ReplyLang` 定義外剛好 4）。 | grep `lootTableObtain(` |
| 4.1 | AcquireAskTool 呼叫點 | `humanJarRoute` 區域變數只有 `lang, code, table, line`。**冇** item id。直接 caller `mergeJarRoutes` 參數名 **`itemId`**（L68），L77 未傳落去。 | OK（上一層 `itemId`）；方法內而家冇 | 作用域＝`mergeJarRoutes` | Read L68–119 |
| 4.2 | AskEngine:1712 | 方法 `infoGapLines(String itemId, String lang, Path gameDir)`（L1699）。L1011 呼叫係 `infoGapLines(heldItemId, ...)`。 | OK | 變數＝`itemId`；入口＝`heldItemId` | Read L1011、L1699–1712 |
| 4.3 | PackIndex:1318 | `acquireFactsDetailed(String itemId, ...)`（L1217）。L1222 `String id = itemId.toLowerCase(...).trim()`。L1316–1318 用 **`id`**。 | OK | 變數＝`id`（唔叫 focus） | Read L1217–1222、L1314–1318 |
| 4.4 | Plainify:212／LlmClient:467 | `humanizeGraphFact` **冇** item 參數。`LOOT_TO_TABLE` 匹配成功時 **`m.group(1)` 係 item id**。另一個 caller：`AskEngine.java:1462` `return Plainify.humanizeGraphFact(gf)`。 | OK | 變數＝`m.group(1)`（邊嘅左項，唔係 `heldItemId`） | Read regex L38–39；L210–212；AskEngine L1462 |
| 5 | `LOOT_TO_TABLE:38-39` group 2 強制有 `:`；`humanizeGraphFact` 傳 `m.group(1)` 係 item id（plan L45、L53） | 原文：`(?i)^item:([a-z0-9_]+:[a-z0-9_./-]+)\\s+-\\[loot\\]->\\s+table:([a-z0-9_]+:[a-z0-9_./-]+)$`。group 1＝`item:` 後、**必須含一個 `:`** 嘅 id。group 2＝`table:` 後、**必須含一個 `:`**。`table:blocks/ritual_brazier`（冇 ns）**唔 match**。現行碼傳 **group 2 做 table**。plan「傳 `m.group(1)`」若指 **itemId 參數**，同 regex 一致。 | OK | corpus＝`Plainify.java` L38–39。測試字串兩條 match、一條無 ns 唔 match。 | `python re.compile` 對三條樣本 |
| 6 | 「4 個 `lootTableObtain` 呼叫點…（無第 5 個）」（plan L44） | forge main **呼叫** 剛好 4：`AcquireAskTool.java:112`、`AskEngine.java:1712`、`PackIndex.java:1318`、`Plainify.java:212`。定義 `ReplyLang.java:454` 唔計。**但** `JarLightIndex.factsForAsk`（L114）喺 L131 叫 `formatFact`，**唔經** `routeLinesForItem`。`formatFact` 嘅 `L` 分支（L296）係 `ReplyLang.jarLoot`，**唔係** `lootTableObtain`。caller：`AskEngine.java:272` `factsForAsk(heldItemId, lang)`。zh_cn `packai.reply.jar_loot`＝`掉落：%s`（會帶 raw path）。呢條 ritual trace：`掉落：`＝0，`掉落表：`＝7。 | lootTableObtain 計數 OK；「無第 5 出口」WRONG | 第 5 出口定義＝`routeLinesForItem` 以外、會把 L 碼變成人話且 template 含 path 嘅路。 | grep `lootTableObtain(`、`formatFact(`、`factsForAsk(`、`jarLoot(` |
| 7a | `:956` 係 `:950` `if (loop.intent() != PURPOSE)` 區塊閉合；`:1010` STANDARD 閉合；`:1011` `InfoCompleteness.append`；三錨唯一（plan L9、L77–79） | 括號配對：L950 if → **閉合 L956**。L969 `Kind.STANDARD && frameMatch.recipeIndex() != null` → **閉合 L1010**。L1011＝`body = InfoCompleteness.append(...)`。`InfoCompleteness.append` 出現 **1** 次。長 STANDARD if 出現 **1** 次。 | 行號配對 OK | 配對＝由該行第一個 `{` 計到 depth 0。 | python 掃 `AskEngine.java` |
| 7b | 同上「必須唯一（唔唯一即紅）」 | `if (loop.intent() != AskLoopState.Intent.PURPOSE)` 出現 **2** 次：L369 閉合 **L419**、L950 閉合 L956。裸 `frameKind == ModularFrameStandard.Kind.STANDARD` 出現 **2** 次：L363 閉合 L365、L969 閉合 L1010。`}` 本身唔係唯一字串。 | WRONG（若錨用 PURPOSE if 原文或裸 STANDARD） | 出現次數＝`str.count` 全檔。 | 同上 |
| 8 | §8 `ars_nouveau:ritual_brazier`：leaf 命中 focus 就唔准泛用。trace 內 `L\|` table 原文（plan L113；指令第 8 點） | 檔存在，156131 bytes，64 行。字面 `L\|`＝**0**。`table:`＝0。`blocks/ritual_brazier`＝7，七次都係已譯文 **`掉落表：blocks/ritual_brazier`**。zh_cn `packai.reply.loot_table_obtain`＝`掉落表：%s` ⇒ INFERENCE table 參數＝`blocks/ritual_brazier`（**冇 ns**）。`container=blocks`，`leaf=ritual_brazier`，`pathOf(ars_nouveau:ritual_brazier)=ritual_brazier`，**相等**。 | OK（leaf 相等）；raw `L\|` 條目唔存在 | corpus＝指定 jsonl 全文＋ forge `zh_cn.json` 該 key。 | python `count`／切片 |
| 9 | `JarLightIndex.formatFact:287` → `ReplyLang.jarCraft`，lang `packai.reply.jar_craft`＝`合成 %s：%s`（plan L71） | `formatFact` 由 L277 起。`case 'R'`：L287 係 `String ings = ...`；**`yield ReplyLang.jarCraft(lang, type, ings)` 喺 L288**。`ReplyLang.jarCraft` L818 → key `packai.reply.jar_craft`。zh_cn L331、zh_tw L335＝`合成 %s：%s`。en_us L335＝`craft %s: %s`。 | 行號 WRONG（287→288）；中文 lang OK | 行號＝`JarLightIndex.java`。lang＝forge 三份 json。 | Read L277–288；grep key |
| 10a | `Language.getOrDefault`＝`Map.getOrDefault(key, key)`（plan L62） | Mojmap：`net.minecraft.locale.Language` → `pe`；實作喺 `Language$1` → `pe$1`，方法 `getOrDefault(String)` → `a(String)`，mappings 行號 49:49。`javap -c -p`：`aload_1; aload_1; invokeinterface Map.getOrDefault`。即 default＝同一個 key。 | OK | corpus＝`client.jar`（forge gradle `minecraft_repo/versions/1.19.2/client.jar`）＋ `client_mappings.txt` L33219–33239。 | `javap -c -p -classpath .../client.jar pe$1` |
| 10b | `KubeJsMechanicScan` 零 `net.minecraft` import（plan L60） | import 區 L3–26 只有 `java.*` 同 `com.google.gson.*`。grep `net.minecraft`＝0。 | OK | 全檔字面 | grep |
| 11 | `tests/check_*.py` TOTAL 124／FAIL 0；`*Check.java`＝53；`tmp-check.gradle` entry＝53（plan L109–110） | `tests/check_*.py`＝**124** 個檔。今日真跑 **TOTAL 124 FAIL 0**。`forge/1.19.2` 下 `*Check.java`＝**53**。`tmp-check.gradle` map L6–L58＝**53** 條 `run*Check`，類名同 53 個 java 檔一一對應（去掉 `.java`）。三個 plan 新 harness（`LootLineHumanizeCheck`／`KubeJsTooltipTextCheck`／`ToolBuildCanonicalCheck`）**檔案數＝0**。 | OK（53／124／0） | FAIL＝`python "$f"` RC≠0。gradle entry＝map 內 `run…Check` 鍵。 | `for f in tests/check_*.py`；`rglob *Check.java`；讀 gradle L6–58 |
| 12a | §7 白名單路徑 `git ls-files` 存在；`InfoCompleteness.java` 移出可改集合；全文冇殘留「叫人改 InfoCompleteness」（plan L14、L68、L87–103、L125） | 已存在嘅白名單＋禁改路徑 28 條 `git ls-files` 全部 tracked（含 `InfoCompleteness.java`、`JarLightIndex.java`、`RecipeEmbed.java`、`RecipeCard.java`、`AiAssistantScreen.java`、`check_info_completeness_hook_order.py`）。全文 `InfoCompleteness` 只係：移出可改集合、唔改插入位置、`append` 保持、禁改、hook 要喺 `append` 之前。**冇**「去改 `InfoCompleteness.java` 邏輯」。三個新 `*Check.java` 未存在 ⇒ **未** tracked。 | 已存在路徑 OK；「全部 tracked」對三個未寫 harness WRONG | corpus＝plan 全文 grep `InfoCompleteness`＋`git ls-files` | `git ls-files --` 該 28 路徑，`wc -l`＝28 |
| 12b | `AskService.java:765-790`、`:1974-2003`；`KubeJsMechanicScan:1093`；kubejs lang 只有 `zh_cn.json`、1,958 keys（plan L59–61、L78、L96） | L765 起係 `KubeJsMechanicScan.factsForItem(...)`；L781 `OfficialDisplay.enrichFacts`；L790 `KnowledgeLookup.factsForItem`（範圍包到知識庫，唔只係 tooltip 解析）。L1093＝`String hash = sha256(rel + "\0" + src);`。`ensureNonEmptyBody`：L1974 `if (!bodyOnly(reply).isBlank()) return`；L1994、L2003 先 `scrubPromptEcho`；方法去到 **L2012** 先完。`kubejs/assets/kubejs/lang/` 只有 `zh_cn.json`。`len(json)`＝**1958**。 | 行號／1958／只有 zh_cn OK | key 數＝JSON object 頂層鍵。 | Read 三段；`json.loads` `len`；`ls` lang 目錄 |
| 13 | 真機 5 個物品 id；tooltip 逐字兩句（plan L113–116） | §8 五個 id：`ars_nouveau:ritual_brazier`、`tetra:modular_double`、`witherstormmod:withered_nether_star`、`kubejs:god_bless_empty_necklace`、`kubejs:god_bless_full_necklace`。兩句喺 `kubejs/assets/kubejs/lang/zh_cn.json` **各 1 次、逐字相同**：`kubejs.tooltips.god_bless_full_necklace.1`＝`用于在沙漠维度地牢中进行神意挑战`；`kubejs.tooltips.god_bless_empty_necklace.1`＝`击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能`。沙漠句 **唔喺** `active_pill`（`active_pill` 係「能夠激发一部分高級器官的」等，另一組 key）。 | 兩句 OK；五 id 只核到 plan 文字＋kubejs 兩個 item key，註冊表未核 | 逐字＝substring 命中次數＝1 且 value 全等。 | `json.loads` 掃描 |

### PLAYER_UNSAFE_MARKERS 真 tuple（17）

`render_recipe_cards`、`[RECIPE_CARDS]`、`【JEI`、`注意：JEI`、`已完整扫描`、`已完整掃描`、`推荐合成`、`推荐取得`、`role=`、`必须`、`禁止`、`不要用`、`请明说`、`DSML`、`<invoke`、`<tool_calls`、`[[tools]]{`

### 其他行號（抽查，多數 OK）

| claim | 真值 | 判定 |
|---|---|---|
| `isPlayerSafeLine:1522` | 方法由 L1522 起 | OK |
| `looksLikeAbsenceClaim:39` | 方法 L39–78 | OK |
| `ItemIndexCache.Entry:45` | `record Entry(String id, String label, String nbt, List<String> schem, String dedupe)` L45。冇 loot 欄 | OK |
| `ItemIndex.searchReady:111` | 方法 L111，註解「Score over in-memory index」 | OK |
| `lootKeyFromPath:205-214` | 剝 `/loot_tables/` 同 `.json`，**唔帶** `data/<ns>/`。例 `.../loot_tables/blocks/x.json` → `blocks/x` | OK |
| `AskTrace.event` `:133`、try `:134-148` | 兩參數 `event` 由 L133；try L134，catch 收到 L148 | OK |
| `ensureCanonicalQuestLine:263-285`、`replaceWrongQuestishWithCanonical:291-317`、`Kind` L24–28 | 行號同方法／enum 範圍一致 | OK |
| `ReplySources.HEADER:274` | **HEADER 定義喺 `ReplySources.java:11`**。`AskJeiHints.java:274` 先係 `HEADER.matcher(body)` | WRONG |
| `AskEngine:826` 禁改 | L826＝`List<String> promptFacts = capable ? List.of() : factsLive;` | 行存在 OK（唔係獨立語義錨） |
| `AskEngine:1462` | `return Plainify.humanizeGraphFact(gf);` | OK |
| `infoGapLines:1699-1722` | 方法 L1699，`return gaps` 喺 L1722，`}` 喺 L1723 | OK |
| `AskMarkerRepair:69-78` | `repair(...)` L69–78 | OK（行號） |
| `AskResult:87` | L87＝`scrubAbsenceClaimsWhenCards`；L88 另有 `RecipeCardsMode.scrubMarker` | OK（87 係 absence scrub） |

## WRONG 清單

1. **「已喺 KEYS ⇒ 改文要 paused」**（plan L40）。會唔會永遠紅：改 forge lang **呢句**唔會因為 KEYS 閘永遠紅（兩樹各自過 predicate 就綠；三句已過鏡像 predicate）。**若驗收改跑 `check_dual_tree_sync.py --no-paused`：今日已經 RC=1、FAIL 37，slice 未開工都紅，會永遠紅**，原因係既有 Java drift／缺 twin，唔係呢條 key。預設 paused 閘今日係 fail=0。
2. **PURPOSE if 唔唯一**（plan L77–79）。`if (loop.intent() != AskLoopState.Intent.PURPOSE)` 出現 2 次（369→419、950→956）。若新 `ToolBuildCanonicalCheck` 用呢句做 `count==1`：**而家已經紅，加 hook 之後仍然係 2，驗收永遠紅**。L956 作為「950 那個 if 嘅閉合」本身係真。
3. **裸 `Kind.STANDARD` 唔唯一**。出現 2 次（363→365、969→1010）。帶 `&& frameMatch.recipeIndex() != null` 先唯一，閉合先係 1010。若錨用裸 STANDARD：`count==1` **永遠紅**。用長條件：唔會。
4. **`ReplySources.HEADER:274`**（plan L76）。定義行係 `ReplySources.java:11`。唔會令驗收永遠紅（插入用 pattern，唔用 274 行號）；會令人改錯檔。
5. **`formatFact:287`**（plan L71）。`jarCraft` 喺 **288**。唔會永遠紅（驗收冇鎖行 287）。en_us 唔係「合成 %s：%s」。
6. **「無第 5 個」當作出口**（plan L44）。`lootTableObtain` 呼叫數 4 係真。`factsForAsk`→`formatFact`→`jarLoot`（`掉落：%s`）係第 5 條 raw path 人話，而且 `JarLightIndex.java` 喺禁改名單。**呢條 ritual trace 唔會因此永遠紅**（`掉落：`＝0）。其他物品若 prompt 有 L 碼 jar fact，答案仍可能出 `blocks/`，§8「唔准出現 `blocks/`」**可能仍然紅**。INFERENCE：視乎該物品 `factsForAsk` 有冇 L 碼；呢次冇逐個物品掃 jar index。
7. **「§7 白名單全部 git tracked 已核」**（plan L125）對三個未建立 harness 唔真（檔案數 0）。唔會永遠紅。已存在路徑 28/28 tracked。

## 未能核實清單

- R1／R2「正方 4 : 反方 6」（plan L5–6）。分數係 review 意見。指令禁止照抄 R2 報告，今次冇重評。
- `compileJava`／`jar`／harness「53 → 56/56」（plan L107–109）。指令禁止 gradle。只核到而家 53 個 `*Check.java` 同 53 個 gradle entry；56 係未寫嘅目標。
- `check_dual_tree_diff_symmetry.py --no-paused` 今日 exit code。冇跑。只讀過佢：forge-only diff 唔入 intersection，唔會單靠「只改 forge lang」FAIL。
- 五個物品 id 係咪註冊喺遊戲。只核到 plan 文字、兩個 kubejs item lang key、ritual trace 檔存在。要開遊戲或讀 registry dump。
- 真機再跑五條 ask、焦點前後 `sk_activity.json`。今次只讀既有 trace，冇開遊戲。
- `officialName` 回空走泛用句。要 `OfficialDisplay.lookup` test hook 或遊戲。冇跑。
- C9「5–10 件 mcmod 對照」。冇做。
- `factsForAsk` 對 `tetra:modular_double`／`withered_nether_star` 會唔會吐 `掉落：`＋raw path。要掃該 instance 嘅 jar index 或再跑 ask。
