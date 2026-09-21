# Slice 1 v3 — R3 反方（有界輪，唯讀）

- plan md5 `0f674c68296caebb7b358085d0400350`（同 pin 一致，未停手）
- `wc -l` = 126
- 無改 plan／code；無 gradle；無開遊戲
- 本輪只核清單 A／B，加 v3 新文字自相矛盾或永遠紅／永遠綠

## 1. 清單 A — R1 六條 flip

| 編號 | 判定 | 證據（plan Lxx ／ file:line） | 我跑嘅命令 |
|---|---|---|---|
| 1 B 禁詞兩層＋三語全文＋真 method 負控 | RESOLVED | plan L30-39 寫死 en_us／zh_cn／zh_tw 三句，負控要呼叫 `isPlayerSafeLine` 同 `looksLikeAbsenceClaim`。真方法 `AskReplyScrub.java:1522`、`AskJeiHints.java:39`。`PLAYER_UNSAFE_MARKERS` 17 條喺 `:148-166`。三句對真 predicate：safe=True、absence=False、無 `%`、有 Unsure／不确定／不確定、無 `未索引`。閘禁詞喺 `check_honest_miss.py:91-106` 同 `check_reply_prompt_keys.py:54-67`（含 `:60` `not indexed`／`未索引`）。例 `請明說` 閘會紅（py `:98`／`:58`）；`isPlayerSafeLine` 本身唔含繁體「請明說」（marker 係「请明说」）——plan 寫嘅係閘紅，唔係 scrubber 紅。`沒有列出`／`not listed recipe` → absence True。 | `python` 由 Java 抽出 17 marker＋`looksLikeAbsenceClaim` 同等式，跑三句＋兩條負例句 |
| 2 C1-lite 唔露 raw path；反查搬 Slice 2 | RESOLVED | plan L10、L51 刪 item-index 唯一命中，反查搬 Slice 2。L52 明文刪 `humanJarRoute:113-115` 補 path。L44 四個 `lootTableObtain` 呼叫同 `rg` 一致（無第 5 個，定義喺 `ReplyLang.java:454`）。 | `rg -n lootTableObtain forge/1.19.2/src` |
| 3 C5 遊戲語言表；堵 raw key；cache 唔烘 lang | RESOLVED | plan L58-64：pack lang → 遊戲語言表 → 缺 key 泛用句；`equals(key)` 先當缺；斷言答案／facts 唔准含 `kubejs.tooltips.`（zh_tw／en_us 都包）。落點 client `AskService.java:765` `factsForItem` 之後、`:781` `enrichFacts` 同區。cache hash `KubeJsMechanicScan.java:1093` = `sha256(rel+"\0"+src)`，無 lang。`factsForItem:193` 回新 `List<String>`；`enrichFacts:187-214` 再抄一份；`writeCache` 只喺 `loadHandlers:1105` 寫 `Handler`。解析落返去嘅 list 唔寫 cache。logic 層仍零 `net.minecraft` import（檔頭 import 區）。 | 讀 `AskService.java:733-790`、`KubeJsMechanicScan.java:156-194,1049,1081-1106`、`OfficialDisplay.java:187-214` |
| 4 C7／C4：C4 claim 已刪；C7 唔改插入位 | RESOLVED | plan 全文 `C4` 零次。L68 唔改 `AskEngine:1011` `InfoCompleteness.append`。L103 唔准 `AiAssistantScreen`／`RecipeEmbed`／`RecipeCard`／`check_info_completeness_hook_order.py`。§8 無卡序期望。 | `rg "C4\|早過\|:969\|item-index\|唯一命中"` plan |
| 5 C8 hook 寫死；UNKNOWN 負控；`update_reply_prompts.py` 入白名單 | RESOLVED | plan L9、L77 刪「早過 `:969`」。唯一槽：`:1010` 嘅 `}` 之後、`:1011` `InfoCompleteness.append` 之前（同時晚過 `:956`）。`frameKind` 喺 `AskEngine.java:352`，`:1010` 同 method 睇到。L81 UNKNOWN 唔准貼。L82、L101 `update_reply_prompts.py` 入白名單。harness「index > STANDARD 閉合 且 < append」有整數解（1010 同 1011 之間），唔會永遠紅。 | 讀 `AskEngine.java:950-1011` |
| 6 白名單補齊（R1 九檔＋逐符號） | RESOLVED | R1 九行對 v3：① `AiAssistantScreen`、② hook-order py → L103 唔准（C4 已刪，唔使改）。③ `update_reply_prompts.py` → L101。④ `AskService` → L96 `:765-790`（B 唔使改 `:1994/:2003/:2007`，嗰度已經 `ReplyLang.askMissAcquirePlayer`）。⑤ `AskResult.java` → L95 檔名；L78 引用 `:87` 只刪 absence 行，設計唔改呢個 method。⑥ `LlmClient.java:467`、`AskEngine.java:1462` → L93、L91。⑦ 兩個 frame check → L101。⑧ `check_ask_display_leak`／fixtures 唔入可改集；§8 L117 改做手 grep `Pack AI display body ver=`，唔使改個 py。⑨ `neoforge/**` → L103 唔准；L40 寫 paused。R2 缺嘅符號：`humanJarRoute:109-119` 刪 `:113-115`（L52、L90）；`appendParts:1049` 改為 `KubeJsMechanicScan` 只准註解（L94）。 | `rg` plan 對 C4／白名單檔名；讀 §7 |

## 2. 清單 B — R2 必修三條

| 編號 | 判定 | 證據（plan Lxx ／ file:line） | 我跑嘅命令 |
|---|---|---|---|
| 1 拆 `humanJarRoute:113-115` | RESOLVED | plan L52 明文刪「譯文唔含 table 就 `+ " " + table`」。真碼仲喺 `AcquireAskTool.java:113-115`（未改 code，符合 plan 狀態）。簽名而家 `humanJarRoute(String lang, String code)` `:109`。唯一呼叫 `mergeJarRoutes:77`；`mergeJarRoutes(String itemId, String lang, List<String> loose)` `:68` 已有 `itemId`。加參數、`:77` 傳入，無第二個 caller。 | `rg -n "humanJarRoute\\(" forge/1.19.2/src` → 只得 `:77` 呼叫＋`:109` 定義 |
| 2 C8 相反位號清乾淨；`:956` 後、`:1010` 後、`:1011` 前可插且唔被覆寫 | RESOLVED | plan 冇再要求「早過 `:969`」。三個約束收成一個槽：`AskEngine.java:1010` `}`（STANDARD `if` 由 `:969` 關）同 `:1011` `InfoCompleteness.append` 之間。`:950-956` PURPOSE 守衛已經行完。`replaceHowToGetBody` 生產呼叫只得 `AskEngine.java:980`（block 內）。`ensureHowToGetBody` 生產呼叫只得 `:951`。`AskMarkerRepair.repair:69` 呼叫喺 `:965`，早過 hook。`InfoCompleteness.append:46-99` 只喺來源 header 前插入，唔刪 header 前已有正文。之後：`stripDuplicateSectionHeaders:857` 只丟重複純 header；`ensureNonEmptyBody:1974` 正文非空白就原樣 return；`AskResult.withRecipeCards:87` 只刪 absence 行；`dedupeQuestChatWhenCardShows:661` 只刪被卡覆蓋嘅任務旁白。plan L80 零件句冇 absence token。MODIFIED 唔入 `:969`，STANDARD 替換喺 hook 之前結束。 | codegraph callers：`replaceHowToGetBody`、`ensureHowToGetBody`；讀上述行 |
| 3 B 三語全文＋負控改叫真 method | RESOLVED | plan L31-33 三句逐字。L37-39 負控叫 `isPlayerSafeLine`／`looksLikeAbsenceClaim`，行號同真碼一致（`:1522`、`:39`）。三句過兩條 predicate，亦過兩支 py 閘嘅禁詞＋必須有 Unsure／不確定／不确定。`ask_miss_acquire_player` 已喺 `KEYS`（`check_reply_prompt_keys.py:23`）。`check_dual_tree_diff_symmetry.py:36` `PAUSE_MARKER` = `neoforge/README_PAUSED.md`，檔存在 → 預設 paused，forge-only lang diff 只 WARN、exit 0。`--no-paused` 先會 FAIL（plan L40 已寫驗收用 paused）。 | 同清單 A.1 嘅 `python`；`Path("neoforge/README_PAUSED.md").is_file()` → True |

## 3. 清單 C — v3 新文字自掃

| 項目 | 判定 | 證據 | 反轉條件 |
|---|---|---|---|
| 4 個呼叫點拿唔拿得到 focus item id；`m.group(1)` 係唔係 item id；leaf 規則 | 矛盾 | 拿得到 id：① `mergeJarRoutes:68` 有 `itemId`（`:77` 傳入）。② `infoGapLines(String itemId,…):1699`，`:1011` 傳 `heldItemId`（`AskEngine.java:221`）。③ `PackIndex.acquireFactsDetailed:1222` `id`，`:1318` 同段用。④ `Plainify.humanizeGraphFact` 無獨立 focus 變數；`formatInteractOrAcquireFact(String gf, String lang):1351` 都無。`LlmClient.java:396-411` 有 `focusItemId`，但 plan L53 唔改 `:467` 簽名。id 來自 regex。`Plainify.java:39`：`item:([a-z0-9_]+:[a-z0-9_./-]+)` = group 1，`table:([a-z0-9_]+:[a-z0-9_./-]+)` = group 2（無 `:` 嘅 `blocks/x` **唔** match；我跑 `table:blocks/ritual_brazier` → False）。樣本 `item:ars_nouveau:ritual_brazier -[loot]-> table:ars_nouveau:blocks/ritual_brazier` → g1=`ars_nouveau:ritual_brazier`。**矛盾：** L48-49 只係 `container==blocks` 且 `leaf==pathOf(itemId)`（leaf=最後一段）。L50 寫子路徑 `blocks/special/ice` 走泛用。我跑 `blocks/special/ice` + `minecraft:ice` → leaf=`ice`==path → **命中，唔係泛用**。L55 ③ `blocks/rope` → 泛用句，冇 itemId。`blocks/rope` + `farmersdelight:rope` → **命中**。負控若用 rope 自己嘅 id，同判定式不能同時真 → harness 永遠紅。 | 改其中一邊：判定加「path 恰好兩段 `blocks/<leaf>`，多段必泛用」；或者刪「子路徑必泛用」，並把 ③ 寫成 `blocks/rope` + 非 rope id（例如 `minecraft:stone`）先泛用。rope 自身 id 要期望官方名句。 |
| `humanJarRoute` 加 `itemId`、`mergeJarRoutes:77` 傳入 | OK | 見清單 B.1。無其他 caller。`:77` 而家係 `humanJarRoute(lang, code)`，`itemId` 已在 scope。 | 若再發現第二個 caller 又唔傳 `itemId` → 死。 |
| §4 解析落 `AskService:765-790`：有無 lang；facts 會唔會入 cache | OK | `:733` `appendMechanicBehavior(List, ItemStack)` **無** local `lang`。同檔 `:1872`／`:2005` 已叫 `ReplyLang.current()`；`ReplyLang.java:26-47` 讀 `options.languageCode`。`:746` 已有 `Minecraft.getInstance()`。`:2248` `I18n.get` 另有 `equals(key)` 先例（`:2249`）。cache 見清單 A.3：note 喺 scan 寫入 `Handler`，ask 期改返去嘅 string list 唔寫 `mechanic-cache`。 | 若實作改去 `extractNote`／`loadHandlers` 入面翻譯 → 烘入 cache，本條死。 |
| §5 gap cap 3＋優先序可喺 `infoGapLines` 做，唔使郁 `JarLightIndex`；`R\|` 可唔可以喺呼叫點過濾 | OK | `infoGapLines:1704` 手上有 raw `code`。`:1714-1718` `U\|`／`R\|` 先 `formatFact` 再 `gaps.add`。`formatFact:287` `R` → `ReplyLang.jarCraft(lang, type, ings)`。呼叫點可以唔 add 呢句、改出人話，唔使改 `JarLightIndex`。cap 3 可以喺同一個 method return 前做（而家 `:1722` 無 cap）。L103 唔准改 `JarLightIndex` 同呢條唔衝突。 | 若驗收要求改 `formatFact` 本體 → 白名單唔准，死。 |
| §8 `ars_nouveau:ritual_brazier` 走 `infoGapLines` 嘅 `L\|`：leaf vs `pathOf`；期望句 | 永遠紅 | **相等（字串）：** 真 trace `ask-20260921-073004-ars_nouveau_ritual_brazier.jsonl` L28 acquire result 原文 `掉落表：blocks/ritual_brazier`。L61 `display.body.final` 尾段係 gap header「资料有、答案未提：」＋同一句（`zh_cn.json` `packai.reply.info_gap_header`）。jar-cache shard `config/packai/jar-cache/43e1636e1cfb.json` 有 `"ars_nouveau:ritual_brazier":["L|blocks/ritual_brazier"]`。`loadAllShards:422-456` 載入時**唔**再跑 trivial 濾。leaf=`ritual_brazier`，`pathOf("ars_nouveau:ritual_brazier")`=`ritual_brazier`，相等。官方名唔係繁體：trace L19 `heldItem.name`＝`仪式火盆`，`replyLanguage`＝`zh_cn`。plan L113 grep「破壞 儀式火盆 會掉落」（繁體「儀式／破壞／會」）。簡體名對唔上。同段 L116 項鍊又鎖簡體原文 → 同一個 zh_cn client 不能兩句精確 grep 都綠。**第二條紅：** `LootForwardIndex.isTrivialBlockSelfLoot:119` 對 `blocks/ritual_brazier` 同 `ars_nouveau:blocks/ritual_brazier` 都 true。`JarLightIndex.parseLootJson:270-272` 因此唔 `addFact`。`PackIndex.java:1316` 亦 skip，而 plan 改 `:1318` 係喺呢個 if **裡面**。L103 唔准改 `JarLightIndex`。清 jar-cache 再掃，`L\|` 無生產者，L113「必須出官方名句、泛用句＝紅」冇句可出。而家睇到只係舊 shard。 | 期望改成 zh_cn 真名「仪式火盆」，並寫出 zh_cn 鍵全文（同項鍊同一語言）。另外寫明：驗收依賴現有 jar-cache、不准清 cache；或者準改 `parseLootJson:270`／`PackIndex:1316` 令呢對 self-loot 仍然出句（而家白名單做唔到）。 |

## 4. 總評

清單 A 六條、清單 B 三條都解得開。未夠 8:2：v3 新寫嘅 leaf 規則同負控③／子路徑例相反；§8 火盆期望句用繁體，真 trace／zh_cn 名係「仪式火盆」，同項鍊簡體斷言搶同一個 client 語言；呢條 `L\|` 喺乾淨重掃會被 trivial self-loot 丟掉，白名單又唔准修來源。

必修（最多 3）：
1. §8 火盆期望改「仪式火盆」，並寫 zh_cn 鍵全文，同項鍊案同一語言。
2. §3：`blocks/special/ice`＋`minecraft:ice`、`blocks/rope`＋`farmersdelight:rope` 要嘛改判定（多段 path 必泛用），要嘛改 L50／L55 ③ 期望。
3. 寫明 `L|blocks/ritual_brazier` 只存在於舊 jar-cache；`parseLootJson:270` 重掃會丟。不准清 cache，或者准改個濾（而家 L103 唔准）。

正方 7 : 反方 3

## 5. 我 verify 咗邊幾項

- `md5sum docs/plans/2026-09-21-player-text-humanize-slice1.md` → `0f674c68296caebb7b358085d0400350`；`wc -l` → 126
- `python`：三語新句 vs `PLAYER_UNSAFE_MARKERS`（17）同 `looksLikeAbsenceClaim` 同等式 → safe True／absence False；`請明說` 唔中 marker；`沒有列出`、`not listed recipe` → absence True
- 同個 `python`：leaf 規則六組（ritual 兩式命中、stone 唔中、rope 自身命中、`blocks/special/ice`+`minecraft:ice` 命中、chest 唔中）；trivial self-loot 對 ritual 兩式 True、對 ice 子路徑 False；`LOOT_TO_TABLE` 樣本 g1=item id；無 ns 嘅 table 唔 match
- `rg -n lootTableObtain forge/1.19.2/src` → 4 呼叫＋1 定義
- `rg -n humanJarRoute forge/1.19.2/src` → `:77` 同 `:109`
- `Path("neoforge/README_PAUSED.md").is_file()` → True
- 讀 plan 全文；R2 反方、R2 anchor、R1 summary
- 讀 `AcquireAskTool.java:1-119`、`AskEngine.java:221,350-352,920-1024,1351-1462,1699-1722`、`Plainify.java:38-39,183-218`、`PackIndex.java:1217-1318`、`LlmClient.java:380-467`、`JarLightIndex.java:52-57,144-166,205-214,258-298,422-469`、`LootForwardIndex.java:106-131`、`AskReplyScrub.java:148-166,857-899,1522-1535`、`AskJeiHints.java:39-78`、`AskResult.java:84-97`、`AskMarkerRepair.java:65-78`、`InfoCompleteness.java:46-99`、`AskService.java:300-406,733-790,1872,1968-2011,2228-2256`、`ReplyLang.java:26-47`、`OfficialDisplay.java:187-214`、`KubeJsMechanicScan.java:156-194,1039-1106`
- 讀 `check_honest_miss.py:73-106`、`check_reply_prompt_keys.py:11-67`、`check_dual_tree_diff_symmetry.py:15-36,59-63`
- `zh_cn.json`：`loot_table_obtain`＝`掉落表：%s`；`info_gap_header`＝`资料有、答案未提：`
- trace `…/packai/trace/ask-20260921-073004-ars_nouveau_ritual_brazier.jsonl` L28 result 原文、L19 名 `仪式火盆`、L61 gap 尾段含 `掉落表：blocks/ritual_brazier`
- jar-cache `43e1636e1cfb.json` 含 `"ars_nouveau:ritual_brazier":["L|blocks/ritual_brazier"]`
