# Slice 1 v4 — R4 獨立數字／行號核實

- 對象：`docs/plans/2026-09-21-player-text-humanize-slice1.md`
- md5：`1c35a4b7bbf785f982a0ebbc02d6d0bc`（同 pin；`hashlib.md5(read_bytes)`）
- 日期：2026-09-21
- 範圍：指令 8 項＋v4 內可重算嘅行號／計數。歷史 review 比分冇重判。
- 冇跑 gradle、冇開遊戲、冇 git write。Java 謂詞係照真碼逐行模擬，唔係 `javac` 執行。

| # | v4 claim（引句＋plan 行號） | 我重算嘅真值 | 判定 | 定義／corpus | 命令 |
|---|---|---|---|---|---|
| 1 | 五個新 key 今日唔存在、唔撞現有 key（plan L65–70） | forge `en_us`／`zh_cn`／`zh_tw` 各 515 keys，五個 key 全部 ABSENT。neo 三語同樣 ABSENT。相近但唔同名：`loot_table_obtain`、`info_gap_header` | OK | 分子＝精確 key 字串命中；分母＝該 json object keys。三語＝`assets/packai/lang/{en_us,zh_cn,zh_tw}.json` | `rg` 精確 key；`json.loads` `k in data` |
| 2 | `ReplyLang.jarLoot:827`；`jar_loot`＝「掉落：%s」（plan L19、L63） | 方法簽名喺 `ReplyLang.java:827`，`:828` `tr(..., "packai.reply.jar_loot", ...)`。zh_cn `:333`、zh_tw `:337` 值＝`掉落：%s`。en_us `:337`＝`loot: %s` | OK | 行號＝方法宣告行。lang 值＝三語 json 字面 | 讀 `ReplyLang.java` 827–828；`rg jar_loot` lang |
| 3 | `JarLightIndex.formatFact:296` `case 'L'`（plan L19、L63） | `formatFact` 宣告 `:277`。`:296` 係 `case 'L' -> ReplyLang.jarLoot(lang, rest);` | OK | `:296`＝`case 'L'` 行，唔係方法頭 | 讀 `JarLightIndex.java` 277–298 |
| 4 | `formatFact:288` → `jarCraft`（plan L23、L100） | `:288` 係 `yield ReplyLang.jarCraft(lang, type, ings);`（`case 'R'`） | OK | 同上，行為行 | 讀 283–288 |
| 5 | `parseLootJson:270` 重掃會因 trivial self-loot 丟掉 `L\|`（plan L149） | 方法宣告 `:258`。`:270` 係 `if (LootForwardIndex.isTrivialBlockSelfLoot(id, key))`，`:271` `continue`（唔 `addFact`） | OK | `:270`＝丟棄條件行。方法頭係 258 | 讀 258–274 |
| 6 | `isTrivialBlockSelfLoot` 對 `blocks/ritual_brazier`／`ars_nouveau:blocks/ritual_brazier` 回 true | 方法 `:106–120`。itemId=`ars_nouveau:ritual_brazier` 時兩表都 **True**。空 itemId **False**。`minecraft:stone`＋`blocks/ritual_brazier` **False**。`blocks/special/ice`＋`minecraft:ice` **False**（lootPath ≠ `blocks/ice`）。`blocks/rope`＋`farmersdelight:rope` **True** | OK | 真＝`lootPath == "blocks/" + itemPath`；`lootPath`＝第一個 `:` 之後，冇 `:` 就全串。item 必須有 `ns:path` | 讀 106–119；Python 逐行模擬 |
| 7 | `ReplyLang.jarCraft`／`jarUsedIn` lang（plan L100） | `jarCraft` `:818`，key `packai.reply.jar_craft`：zh_cn／zh_tw `合成 %s：%s`；en `craft %s: %s`。`jarUsedIn` `:823`，key `jar_used_in`：zh_cn `用于 %s → %s`；zh_tw `用於 %s → %s`；en `used in %s → %s` | OK | 方法行＋json 字面。plan 引嘅係簡體模板 | `rg` lang；讀 818–825 |
| 8 | `isPlayerSafeLine:1522`；五個新句／`info_gap_related` 會唔會 false（plan L98） | 方法 `:1522–1535`。`PLAYER_UNSAFE_MARKERS` `:148–166` 共 **17** 條。15 句模板（5 key × 3 語，含 `%s`）全部 **true**。`%s` 換成 `仪式火盆`／`下界合金`／`再利用梁杆`／`3` 仍然 **true**。`null`／`""` 先 false | OK | false＝空，或 `contains` 任一 marker（`role=` 用 lower）。模擬≠跑 JVM | 讀 148–166、1522–1535；Python 模擬 |
| 9 | `InfoCompleteness.append` `:46–99` 傳空 list 會唔會插空 header | 簽名 `:46`，閉合 `:100`。`:47–48`：`gapLines == null \|\| isEmpty()` → `return answer`（同一 reference），未到 `:68` header | OK | 空 list＝`List.isEmpty()`。唔插 header | 讀 46–99 |
| 10 | `A1` 真檔 1 次；閉合＝`:1010`（plan L109） | `AskEngine.java` 字串出現 **1** 次，行 **969**。由 969 數括號，depth 回 0 喺 **1010**。`:1010` 文本＝`}` | OK | A1＝`if (frameKind == ModularFrameStandard.Kind.STANDARD && frameMatch.recipeIndex() != null) {`。corpus＝成個 `AskEngine.java` | Python `in` 逐行；括號深度 |
| 11 | `A2` 真檔 1 次；`=` 喺 `:1011`（plan L110） | 字串出現 **1** 次。`:1011` 文本＝`body = InfoCompleteness.append(body, infoGapLines(heldItemId, lang, gameDir), lang);` | OK | A2＝`body = InfoCompleteness.append(` | 同上 |
| 12 | 裸 `Kind.STANDARD` 真檔 **2** 次（plan L21、L108） | 字串 `Kind.STANDARD` 出現 **4** 次：`:363` `==`、`:470` `!=`、`:969` `==`、`:1082` `!=`。若只計 `== ModularFrameStandard.Kind.STANDARD` 先係 2（363、969） | WRONG | 分子＝子字串 `Kind.STANDARD` 行數；分母＝`AskEngine.java` 全檔。plan 寫「真檔 2 次」冇再收窄 `==` | Python 逐行 `in` |
| 13 | 裸 `if (loop.intent() != …PURPOSE)` **2** 次：`:369`／`:950`（plan L21、L108） | 精確 `if (loop.intent() != AskLoopState.Intent.PURPOSE)` **2** 次，行 369、950。另有 `loopState.intent()` **1** 次喺 `:863`（唔計入） | OK | 分子＝`loop.intent()` 呢句，唔包括 `loopState` | 同上 |
| 14 | `:956` 係 `:950` PURPOSE `if` 閉合；hook 槽喺 956 後、1010 後、1011 前（plan L10、L106） | `:950` 係該 `if`。`:956` 文本＝`}`。`:1010` 係 STANDARD `if` 閉合。`:1011` 係 `append` | OK | 行文本＋括號 | 讀 950–956、969–1011 |
| 15 | 五個真機 id 喺 item-index 存在（plan L148–153） | 五個 id 喺 17 個 `*.json` 都有子字串。最新 zh_cn `v1_1.19.2_forge_zh_cn_a05e3f80d457.json`（mtime 2026-09-17 20:01:10）`"id":"<id>"` 次數：ritual_brazier 1、modular_double 2、withered_nether_star 1、empty_necklace 1、full_necklace 1 | OK | corpus＝`AI_test_NFWC_DIM/minecraft/config/packai/item-index/*.json`（17 檔）。存在＝compact `"id":"..."` ≥1 | Python 數子字串 |
| 16 | trace `heldItem.name`＝`仪式火盆`（plan L147）；檔 `ask-20260921-073004-ars_nouveau_ritual_brazier.jsonl` | 檔存在，156131 bytes。escaped blob `heldItem.id=ars_nouveau:ritual_brazier` 且 `name=仪式火盆` 出現 **6** 次。同段 `replyLanguage=zh_cn` | OK | 欄位喺 `send.user`／`send.facts` 內層 JSON，唔係 jsonl 頂層 key。6＝該 blob 次數 | Python `count` |
| 17 | jar-cache 有 `"ars_nouveau:ritual_brazier":["L\|blocks/ritual_brazier"]`（plan L149） | **exact** 命中 `config/packai/jar-cache/43e1636e1cfb.json`。片段：`,"ars_nouveau:ritual_brazier":["L\|blocks/ritual_brazier"],"ars_nouveau:rotating_spell_turret":` | OK | exact＝無空白緊湊 JSON。只此 1 檔 | Python `in` 掃 `*.json` |
| 18 | jar-cache 清單 mtime | `*.json` **231** 個（含 `manifest.json`）。unique mtime **1** 個：全部 **2026-08-08 09:10:39** | OK | `st_mtime` 取秒 | `Path.stat` |
| 19 | python 閘 TOTAL **124**／FAIL **0**（plan L144） | 檔數 **124**。本 session 真跑 **TOTAL 124 FAIL 0** | OK | 分子＝exit≠0 嘅 `tests/check_*.py`；分母＝glob 檔數。stdout 丟棄，只睇 exit | `for f in tests/check_*.py; do python "$f" >/dev/null 2>&1 \|\| echo FAIL; done` |
| 20 | harness 53 → 56（plan L143） | 今日 `*Check.java`（排除 `build/`）＝**53**，全部喺 `src/test`。`tmp-check.gradle` `'run…':` entry＝**53**。三個新 Check（`LootLineHumanizeCheck`／`KubeJsTooltipTextCheck`／`ToolBuildCanonicalCheck`）**ABSENT**。53+3＝56 係計劃數，唔係今日 gradle 跑數 | OK | 今日真值＝53 檔／53 entry。56＝未落地嘅目標 | `rglob *Check.java`；`rg "'run\\w+':"` |
| 21 | §7 白名單路徑 `git ls-files` tracked（plan L162） | 現存 24 條（12 java＋3 lang＋6 py＋`gen_tmp_check.py`＋`tmp-check.gradle`＋`code_change_log.md`）全部 exists=1 tracked=1。禁改：`InfoCompleteness.java`、`JarLightIndex.java`、`RecipeEmbed.java`、`RecipeCard.java`、`AiAssistantScreen.java`、`check_info_completeness_hook_order.py`、`neoforge/README_PAUSED.md` 全部 tracked。三個新 Check 今日無檔 | OK | tracked＝`git ls-files` 輸出含該路徑。新 harness 係未寫檔，唔喺「已 tracked」集合 | `git ls-files` |
| 22 | `git status --porcelain`（plan 寫未改 code，L3） | ` M docs/plans/2026-09-21-player-text-humanize-slice1.md`；`??` 兩份 v3 review、`forge/1.19.2/logs/`、`logs/`。冇 java／lang 改動 | OK | porcelain 全輸出。code 未改＝工作樹冇 `src/` diff | `git status --porcelain` |
| 23 | `check_honest_miss.py:80–89` internal miss（plan L43） | `:80` 禁 `%`；`:82–85` `not indexed`／`未索引`；`:86–89` `do not invent`／`禁止捏造` | OK | 行號對 `acquire_index_miss` 迴圈 | 讀 80–89 |
| 24 | `check_honest_miss.py:91–106` 禁詞（plan L42） | `:91–101` 係 player-miss 禁詞（禁止／必须／必須／不要用／请明说／請明說／do not invent／render_recipe_cards／role=）。`:102–106` 係必須含 `Unsure`／`不確定`／`不确定`，唔係禁詞 | OK | 行號塊存在。標「禁詞」會包埋正面 Unsure assert | 讀 91–106 |
| 25 | `check_reply_prompt_keys.py:23` `ask_miss_acquire_player` 已喺 `KEYS`（plan L48） | `:23` 字面＝`"packai.reply.ask_miss_acquire_player",` | OK | KEYS tuple 第 12 項，1-based 行 23 | 讀 11–28 |
| 26 | `check_reply_prompt_keys.py:45–49`（指令要求核；**v4 正文冇寫 `:45-49`**） | `:45` `n = val.count("%s")`；`:46–47` `llm_style`／`llm_style_notools` 必須 2；`:48–49` 其餘必須 0 | n/a | 檔案真值如上。plan 只寫「新 key 含 `%s` ⇒ 唔入 KEYS」（L64），冇引用呢個行號 | 讀 45–49 |
| 27 | `check_reply_prompt_keys.py:54–67`（plan L42） | `:54` 起係 `ask_miss_*_player` 禁詞，含 `:60` 禁 `not indexed`／`未索引`；`:63–67` 必須 Unsure | OK | 同 #24：範圍包含禁詞＋Unsure 正面條件 | 讀 54–67 |
| 28 | `check_dual_tree_diff_symmetry.py:36` `PAUSE_MARKER`（plan L48） | `:36` `PAUSE_MARKER = ROOT / "neoforge" / "README_PAUSED.md"`。檔存在且 tracked。預設 `resolve_paused`：marker 在且冇 `--no-paused` → paused | OK | 行 36＝常數宣告。exit 0 另見 #19（該 script 喺 124 之內） | 讀 33–64 |
| 29 | `lootTableObtain` 4 個呼叫點、無第 5（plan L52） | `AcquireAskTool.java:112`、`AskEngine.java:1712`、`PackIndex.java:1318`、`Plainify.java:212`。另加定義 `ReplyLang.java:454`。全 `*.java` 再無呼叫 | OK | 呼叫＝`ReplyLang.lootTableObtain(`，唔計定義 | `rg lootTableObtain\\(` |
| 30 | `lootKeyFromPath:205–214` 源頭冇 ns（plan L53） | 方法 `:205–215`（`:215` 係 `}`）。返回 `/loot_tables/` 之後、剝 `.json`，唔加 namespace | OK | 205–214＝邏輯行；閉合括號 215 | 讀 205–215 |
| 31 | `LOOT_TO_TABLE:38–39` group 2 強制有 `:`（plan L53） | `:38–39` regex `table:([a-z0-9_]+:[a-z0-9_./-]+)`。group1＝item，group2＝table，group2 類別含一個 `:` | OK | group 編號對 regex 括號 | 讀 38–39 |
| 32 | `humanizeGraphFact:210–213` 傳 `m.group(1)`（plan L62、L122） | 方法 `:183`。`:210–213` 今日係 `lootTableObtain(lang, m.group(2))`。group1＝item id，group2＝table。plan 係叫改去傳 group1 做 itemId | OK | 現行碼傳 group2＝table。設計句「傳 group1」同 regex 對得上 | 讀 183–213 |
| 33 | `ItemIndexCache.Entry:45` 冇 loot 欄；`searchReady:111` 係分數搜尋（plan L60） | `:45` record `(id, label, nbt, schem, dedupe)`。`searchReady` `:111`，內裡 `ItemSearch.score` | OK | 欄位表；方法行 | 讀 Entry 45；ItemIndex 108–131 |
| 34 | `humanJarRoute:109–119` 含 `:113–115` fallback；`mergeJarRoutes:77`（plan L61） | 方法 `:109–119`。`:113–115` 係譯文唔含 table 就 `+ " " + table`。`mergeJarRoutes` 宣告 `:68`；`:77` 係 `humanJarRoute(lang, code)` 呼叫（今日未傳 itemId） | OK | `:77`＝傳參呼叫行，唔係方法頭 | 讀 68–119 |
| 35 | `infoGapLines:1699–1722`（plan L92） | 方法 `:1699–1722`。`L` → `lootTableObtain` `:1712`；`U`／`R` → `formatFact` `:1715` | OK | 方法首尾行 | 讀 1699–1722 |
| 36 | `LlmClient:467` 簽名唔改（plan L62） | `:467` `readableFacts.add(Plainify.humanizeGraphFact(f));` | OK | 該行係單參呼叫 | 讀 460–468 |
| 37 | `PackIndex:1318` 同段已用 `isTrivialBlockSelfLoot`（plan L62） | `:1316` 呼叫 trivial check；`:1318` `lootTableObtain` | OK | 同 `table:` 分支 | 讀 1314–1318 |
| 38 | `AskService` 緊接 `:765` `factsForItem`（plan L83）；`:765–790`（plan L130） | `:765` `kjs = KubeJsMechanicScan.factsForItem(`。`:781` `OfficialDisplay.enrichFacts`。`:790` `if (!kbOn) return` | OK | 765＝呼叫行 | 讀 756–790 |
| 39 | `KubeJsMechanicScan:1093` hash＝`sha256(rel+"\0"+src)`（plan L84） | `:1093` `String hash = sha256(rel + "\0" + src);` | OK | 該行字面 | 讀 1093 |
| 40 | kubejs lang 只有 `zh_cn.json`、1958 keys（plan L82） | 目錄只得 `zh_cn.json`。`len(dict)`＝**1958** | OK | corpus＝instance `kubejs/assets/kubejs/lang/`。keys＝json object 長度 | `listdir`＋`json.loads` |
| 41 | `ensureCanonicalQuestLine:263–285`；插點 `AskJeiHints.java:274`；`ReplySources.HEADER` 喺 `:11`（plan L23、L105） | 方法 `:263–285`。`:274` `ReplySources.HEADER.matcher(body)`。`HEADER` 宣告 `ReplySources.java:11` | OK | 方法閉合＝285 | 讀兩檔 |
| 42 | `replaceWrongQuestishWithCanonical:291–317`；`Kind` `:24–28`（plan L105） | 方法 `:291–317`。enum `Kind` `:24–28`＝STANDARD／MODIFIED／UNKNOWN | OK | 首尾行 | 讀 |
| 43 | `AskTrace.event` `:133` public static；`:134–148` 全包 try（plan L101） | 兩參 `event` `:133`。`try` `:134–146`，`catch (Throwable)` `:147–148` | OK | 133＝兩參 overload。單參 delegate 喺 `:129` | 讀 129–148 |
| 44 | `AskMarkerRepair:69–78` 只補 marker；`AskResult:87` absence；`AskService:1974–2003` 只喺 body 空白時先做（plan L107） | `repair` `:69–78`（upgrade／empty shell／reinsert）。`:87` `scrubAbsenceClaimsWhenCards`。`ensureNonEmptyBody` `:1974–1976`：`bodyOnly` 非空白就 return；`:2003` 先係 fallback scrub，仍然喺空白 body 路徑 | OK | 1974＝早退行。方法頭係 1968，2003 之後仲有 fallback | 讀 |
| 45 | `looksLikeAbsenceClaim:39–78`（plan L42） | 方法 `:39–78`（`:78` 係 `}`） | OK | 首尾 | 讀 39–78 |
| 46 | `AskReplyScrub` token 表 `:27–39` 冇 `【工具】`（plan L117） | `INTERNAL_SECTION_TOKENS` `:27–36` 有 `TOOL_BUILD`，冇 `【工具】`。`:39` 係下一張 `EXTRA_BARE` 表頭 | OK | 字面 token list | 讀 27–39 |
| 47 | 唔准動 `AskEngine:826`（plan L125） | `:826` 文本＝`List<String> promptFacts = capable ? List.of() : factsLive;` | OK | 行存在。plan 冇描述內容，只禁改 | 讀 826 |
| 48 | `AskEngine:1462`（plan L125） | `:1462` `return Plainify.humanizeGraphFact(gf);` | OK | 行存在 | 讀 1462 |
| 49 | `Plainify:64–86` 只讀（plan L122） | `:64–86` 係 `displayName` | OK | 範圍＝該方法 | 讀 64–86 |
| 50 | full／empty 項鍊簡體 tooltip（plan L14、L153） | kubejs `zh_cn.json`：`kubejs.tooltips.god_bless_full_necklace.1`＝`用于在沙漠维度地牢中进行神意挑战`；`...empty_necklace.1`＝`击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能`。各 1 hit | OK | corpus＝instance kubejs lang，唔係 trace 斷言 | `json` 值包含 |
| 51 | C7 例「合成 crafting_shaped：minecraft:acacia_planks」（plan L100） | trace `ask-20260921-074759-tetra_modular_double.jsonl` 該句出現 24 次（gap 段） | OK | 字面子字串次數 | Python `count` |
| 52 | JDK 路徑存在（plan L141） | `C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2` exists | OK | 目錄存在。冇跑 gradle | `Path.exists` |

## WRONG 清單

1. **裸 `Kind.STANDARD` 次數＝2（plan L21、L108）→ 真值 4**（`:363`、`:470`、`:969`、`:1082`）。
   - 會唔會令驗收永遠紅：指定 harness 錨係 A1／A2／H，A1 全檔 1 次、閉合 `:1010`（#10）。**唔會**因為「2」呢個數而永遠紅。
   - 會唔會永遠綠：**唔會**。`count(A1)==1` 今日成立，但係因為 A1 真係唯一，唔係因為裸 token 數。
   - 若有人把 plan 嘅「2 次」寫成 `assert count("Kind.STANDARD")==2`，嗰條會**永遠紅**（真值 4）。
   - 「用裸 token 做錨會永遠紅」呢句結論仍然成立：4≠1，同 2≠1 一樣紅。錯嘅係個數。
   - 若把定義收窄成 `== ModularFrameStandard.Kind.STANDARD`（撇開 `!=`），先係 2 次（363、969）。plan 原文冇寫呢個收窄。

## 未能核實

- Review 比分（plan L5–7）：R1 4:6、R2 4:6、R3 7:3、R1 flip 6、R2 必修 3、R2 anchor 15 OK／6 WRONG、R3 anchor 3 WRONG。呢輪冇重判舊 review。
- `Language.getOrDefault`＝`Map.getOrDefault(key, key)`（plan L85）。冇跑 `javap`。
- gradle `compileJava`／`jar`／56 個 harness task。指令禁 gradle。56 只係 53+3 算術，三個新 `*Check.java` 今日不存在。
- `officialName` 回空時運行時行為。只核到設計句，冇跑 `OfficialDisplay`。
- 重掃會丟 `L|`：由**今日** `parseLootJson:270`＋`isTrivialBlockSelfLoot(ars_nouveau:ritual_brazier, blocks/ritual_brazier)==true` 推出。冇拆開 mod jar 讀 loot json 原文。現有 cache 係 2026-08-08 寫入，同今日程式唔係同一次掃描。
- `AskResult:87`「只刪 absence 行」：核到呼叫 `scrubAbsenceClaimsWhenCards`，冇逐行展開該方法係咪只刪、有冇改寫。
- v4 **冇**引用 `check_reply_prompt_keys.py:45-49`。檔案內容已讀（#26），唔當 plan WRONG。
