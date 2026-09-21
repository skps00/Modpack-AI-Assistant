# Slice 1 v4 — Code review pass 2（三個月後脆弱位）

- 日期：2026-09-21
- 範圍：未 commit Slice 1（`git status --porcelain` 嘅 Java／lang／plan／`tmp-check.gradle`）＋ `docs/plans/2026-09-21-player-text-humanize-slice1.md` v4.1
- 性質：前瞻。唔係今日 bug 清單。
- 命令（本輪親跑）：`git status --porcelain`、`git diff -U1`（AskEngine hook hunk）、`python` 錨點／KEYS 掃描。未跑 gradle、未開遊戲、未 git write。

## 量度（FACT）

`AskEngine.java` 源碼掃描：

```
A1 1 first 50908
A2 1 first 53747
H  1 first 53659
H_INSIDE_STANDARD_BLOCK_WOULD_STILL_PASS i1<ih<i2 True
close_brace_before_A2 53617  H_after_close True
BETWEEN_H_AND_A2 = ensureToolBuildPartsLine(...);\n + 空白，然後先到 A2
```

`rg`：`ensureToolBuildPartsLine` 喺 `AskEngine.java` **1 次**，行 `1011`。`git diff -U1` hunk：`@@ -1010,2 +1010,3 @@`，插喺 `}` 之後、`InfoCompleteness.append` 之前。

`tests/check_reply_prompt_keys.py` 嘅 `KEYS` **唔含** `loot_table_block`／`loot_table_generic`／`info_gap_related`／`info_gap_more`／`tool_build_canonical`／`kubejs_tooltip_hint`（六個都 `False`）。Plan 寫 5 個；實作多咗 `kubejs_tooltip_hint`。

`research/gen_tmp_check.py` 用 `*Check.java` glob 重生 task list，**唔係**手寫類名。三個新 harness 檔名符合 `*Check.java`，下次 regen **唔會**因為生成器而掉 task。`gen_tmp_check.py` 本身冇改（`git diff --stat -- tests/ research/gen_tmp_check.py` 空）。

## 表

| 風險 | file:line 或機制 | 觸發條件 | 三個月後症狀 | 建議加固（最便宜） |
|---|---|---|---|---|
| 中 | `ToolBuildCanonicalCheck.java:11-14,49-58` 對 `AskEngine.java:969`（A1）、`:1011`（H）、`:1012`（A2） | **有**唯一性斷言：`count(A1/A2/H)==1`。第二個相同呼叫、或 if 換行／多空格令 A1 子字串消失 → harness **紅**。 | 加第二個呼叫唔會靜默過呢條閘。格式一改就紅，要人手改錨。 | 保持 `count==1`。唔好再加第二套字串錨。 |
| 中 | 同上 `:58` 只斷言 `i1 < ih < i2` | Plan §6 負控①「H 移入 A1 區塊 → 紅」**做唔到**。STANDARD 區塊（`:970-1010`）成段都喺 A1 行與 A2 行之間。把呼叫搬去 `:970` 仍然 `count==1` 且順序綠。今日搬入去運行時仍係 no-op：區塊條件係 `Kind.STANDARD`，而 `ensureToolBuildPartsLine`（`AskJeiHints.java:270`）對非 `MODIFIED` 直接 return。 | 日後有人放寬 if、或喺區塊內 `replaceHowToGetBody`（而家喺 `:980`，index 51747，介乎 A1 與閉合括號）之後先呼叫 H → 零件行被整段換走，閘仍綠。 | 閘加一行：`indexOf(H) > 該 if 嘅閉合 `}`（今次量度 close=53617，H 已喺其後）。唔改現有 `count==1`。 |
| 中 | `AskEngine.java:1011-1012`；`InfoCompleteness.java:46-66,115-142` | C8 然後先 C7。兩者都插喺 `ReplySources.HEADER`（`ReplySources.java:12`）之前，所以而家次序係：零件行 → gap → 來源。日後喺 `:1011` 與 return（`:1013-1024`）之間加 writer，或 `append` 嘅 `mentioned()` 當 gap 行已出現過。錨點只要求 H 喺 A2 **之前**，中間加碼仍然綠。 | 零件行或 gap 面板靜默冇咗。`check.info_gap`（`:1782-1788`）記嘅 `l\|`／`u\|`／`total` 係 **append 之前** 嘅桶計數；`mentioned()` 丟掉嘅行冇事件。`ensureToolBuildPartsLine` 喺 kind≠MODIFIED 或 `partsNames` 空白時（`:270-272`）零 log。有 trace 嘅只有 `check.info_gap_skip`（R\|，`:1714`）同 `check.info_gap_drop`（unsafe，`:1744`）。 | H 與 A2 之間禁止其他 `body =`（閘掃呢 1 行窗口）。`parts` 空白或 kind 跳過時加一條 `AskTrace.event`（reason=blank_parts／not_modified）。 |
| 低 | `KubeJsMechanicScan.java:1093-1105` hash = `sha256(rel+"\0"+src)`，不含 lang；`Handler.note` 經 `extractNote`（`:893`）入 cache；解析喺 `AskService.resolveKubeJsTooltipFacts`（`:818`）用 `ReplyLang.current()` | 換語言、只改 `kubejs/.../lang/*.json`、script 不變：cache 仍係 raw key，下次 ask 重解析。換 pack 改 script：hash 變、cache miss、重 parse，仍然 raw key。 | 語言**唔會**被烘死。呢條設計係穩嘅。 | 唔使改 hash。註解留低「唔好喺 `writeCache` 前翻譯」。 |
| 中 | `AskService.java:860-876` 只讀 `kubejs/assets/kubejs/lang/<current>.json`；缺檔或 parse 失敗（`:876` swallow）先至 `I18n` | 玩家語≠檔名（本包 plan 寫只有 `zh_cn.json`）。`zh_tw`／`en_us` 唔會回退去 `zh_cn.json`。 | 另一語嘅 tooltip 句在 pack 檔入面，玩家見到 `kubejs_tooltip_hint` 泛用句（`zh_cn.json:480`「请看游戏内提示（按住 Shift 查看）。」）。Raw key 唔出街。冇 trace 話係 pack-miss 定 I18n-miss。 | generic 之前 `AskTrace` 記 `lang` + `reason=pack_miss`。唔好把譯文寫入 mechanic-cache。 |
| 高 | `JarLightIndex.java:348-354` 指紋相同就唔重掃；`:270-272` `isTrivialBlockSelfLoot`（`LootForwardIndex.java:106-119`）喺重掃時丟掉 `blocks/<itemPath>` | 整合包更新、jar 指紋變、或 `config/packai/jar-cache` 被清。現有 shard **保留**舊 `L\|`，所以今日真機 case 仍在。 | `ars_nouveau:ritual_brazier` 嘅「破坏 仪式火盆 会掉落」靜默消失（fact 根本冇入 index，唔係改成泛用句）。`parseLootJson` 嗰個 `continue` 冇 log。三個新 harness 都唔讀 jar-cache，繼續綠。 | 重掃丟棄時 `AskTrace`／log 一條 `trivial_block_self_loot`（id）。驗收正向句唔好再綁呢條會被過濾嘅 `L\|`。 |
| 中 | 六個新 key 唔喺 `check_reply_prompt_keys.py:11-28` 嘅 `KEYS`。`ReplyLang.tr`（`:122-133`）缺 key 會跨語 fallback，三語都缺先回傳 **key 字串本身** | 有人只改一語、刪一語、或把 `未索引`／`請明說`／`do not invent`／`blocks/`／`kubejs.tooltips.` 抄入新句。`check_honest_miss.py:91-106` 只掃 `ask_miss_*_player`。`update_reply_prompts.py:542-562` 係 surgical merge，**唔會**刪呢六個 key（plan 擔心嘅「regen 靜默回退」對呢六個 key 唔成立）。 | 玩家見到 key 名、或禁詞、或 `blocks/` 又漏出。現有 python 閘全綠。 | **新檔** `tests/check_slice1_reply_keys.py`（唔改舊 assert）：forge 三語齊；`%s` 次數 block/related/more/canonical=1、generic/tooltip=0；禁上列子字串。Neo 唔入。 |
| 低 | `Plainify.lootLine` `OfficialDisplay.officialName` 空（`Plainify.java:191-196`；`OfficialDisplay.java:85-100` 譯 key 形狀亦當空） | 第三方物品冇 hover 名、headless、`hoverLookup`（`:79`）拋錯。leaf 仍然命中 `blocks/<path>`。 | 玩家見到泛用句「由某个掉落表提供（本包未对应到名字）」，唔見 path，亦唔見「（無官方名）」。唔會講錯物品名。 | 唔加資料源。若要診斷：trace `reason=no_official_name`。 |
| 中 | `U\|`：`AskEngine.java:1730-1734` 名空就 `Plainify.displayName`（`:64-85`） | 產物 id 冇官方名。 | 玩家見到「另外相关：acacia planks」呢類 path token（底線變空格），唔係 `crafting_shaped`。唔係 registry 全 id。 | 可接受。想收緊先 trace，唔好喺 gap 行拼 recipe type。 |
| 高 | `AskJeiHints.partsNames` `:278-311` 綁死 `ToolBuildFacts.format` `:167-185` 嘅字面：行首 `part `、最後一個 ` name `、可選 ` item ` | `format` 加欄位而且唔係 ` item `、改用 `label`、或 `isSocketPart` 令行首變 `socket `（`:174`）。測試樣本（`ToolBuildCanonicalCheck.java:19-23`）係手寫字串，**唔經** `format()`。`PART_NS_PATH`（`AskJeiHints.java:13`）只食小寫。 | 零件行整段消失（空白就 return，零 trace）。Socket 零件永遠唔入句。大寫 `Mod:Id` 會漏入玩家句。Harness 仍綠。 | 測試改為 `partsNames(ToolBuildFacts.format(scan))`，並加一行 `socket ` 預期被丟或被收。生產側空白時要有 trace（同上）。 |
| 低 | 非 `blocks/` 容器：`Plainify.lootLine` `:197` 走回 `lootTableObtain` | 新 pack 嘅 `chests/`／`entities/`。v4.1 故意保留原句。 | 玩家仍然見到 raw table 字串（例如 `chests/village/...`）。`blocks/` 先被封。`jarLoot`（`ReplyLang.java:847-851`）只封 `blocks/` 開頭。 | 唔好喺 Slice 1 改。第三條 raw 出口要靠真機「唔准含 `blocks/`」；非 blocks 唔喺呢條斷言入面。 |
| 中 | 非 zh_cn：`loadKubeJsPackLang` 用 `ReplyLang.current()` 檔名，唔用 `bundleLang`。`packai.reply.*` 先經 `bundleLang`（`:114-118`）落到 zh_cn／zh_tw／en_us | 客戶端語言 `zh_tw`／`en_us`／`ja_jp`。KubeJS 包只帶 `zh_cn.json`。 | packai 句跟 bundle（日文→英文字串）。Tooltip 唔讀 zh_cn 檔，I18n 都缺就泛用句。物品官方名跟遊戲語言，可能係英文 hover。 | 同 cache 列：generic 前打 trace。跨語 fallback 讀 `zh_cn.json` 會偏離「同玩家 tooltip 一致」，唔好順手加。 |
| 低 | server／headless：`ReplyLang.current`（`:26-31,88-89`）抓 Throwable → `zh_tw`。`AskService.minecraftTooltip`（`:914-920`）抓 Throwable → `""` → 泛用句。`hoverLookup` 同樣變空名 | 冇 client class、或 dedicated 上誤跑解析。本 mod 係 client；正常遊戲唔行呢條。 | Tooltip 泛用句（headless 時係 zh_tw 模板，因為 `kubejsTooltipGeneric` 用 `current()`）。`blocks/` 自掉落變泛用句，唔崩潰。 | 唔使為 server 加 I18n。Headless 測試保持注入 `gameLang`，唔好改去真 `I18n`。 |
| 中 | `LootLineHumanizeCheck.java:12-46` | 官方名係 stub，唔讀 jar-cache。斷言係 `equals(ReplyLang.lootTableBlock/Generic/Obtain(...))`，模板改字兩邊一齊變。⑤ `chests/...` 只證明 delegate，**冇**鎖定字串入面有 raw path（raw path 由別條 `AcquireJarRoutesCheck` 鎖）。 | 清 cache、改「破坏 %s 会掉落」措辭、甚至 generic 句加入禁詞：呢條仍然綠。 | 另加凍結字面：zh_cn block 句必須等於 plan 全文，且 generic **唔含** `blocks/`、`未索引`。stub lookup 保留。 |
| 中 | `KubeJsTooltipTextCheck.java:14-40` | pack lang 同 `gameLang` 都係注入。`generic` 用 `ReplyLang.tr(current())`，期望值又用同一個 `generic`，措辭改動兩邊一齊綠。唔打開真 `kubejs/.../zh_cn.json`。 | 真包 key 改寫、note 格式唔再係 `note:kubejs.tooltips.*`、I18n 真路徑壞：閘仍綠。 | 凍結一條期望：`zh_cn` 泛用句等於 lang 檔而家呢句；再加一條「pack map 命中優先於 gameLang」。 |
| 中 | `ToolBuildCanonicalCheck.java:19-46` 行為樣本 vs `:48-58` 源碼錨 | 樣本係固定「part … name 下界合金」。`format()` 或 socket 前綴變，樣本唔變。源碼錨會捉到 H 被刪或重複，**唔會**捉到 `partsNames` 解析失敗（方法仍在、呼叫仍在）。 | 玩家零件行沒了，`ToolBuildCanonicalCheck OK` 仍可印。 | 見 partsNames 列：樣本改走 `format()`。 |
| 低 | 六個 python 閘（plan §7 名單；工作樹 **冇** diff） | `check_reply_prompt_keys`／`check_honest_miss` 嘅樣本係舊 `KEYS`／`ask_miss_*`，環境點變都唔睇六個新 key。`check_modular_frame_standard.py`、`check_frame_standard_recipe_line.py` 係源碼子字串，唔含 `ensureToolBuildPartsLine`。`check_tool_miss_teaching.py` 只鎖 tool list。`update_reply_prompts.py` surgical 唔刪額外 key。 | 新 key 缺語、C8 呼叫被刪：呢六條可以仍然綠。佢哋唔會因為 NFWC 樣本消失而假紅，亦唔會因為樣本消失而開始保護 Slice 1。 | 唔改呢六條 assert。新行為只靠上面嗰條新 python＋三個 harness 加固。 |

## 三個月後會爛嘅次序（INFERENCE）

1. 整合包一更新，jar 指紋變，`blocks/` 自掉落從 index 消失，火盆正向句冇咗，測試全綠。
2. `ToolBuildFacts.format` 加欄或 socket 行變多，零件行空白返回，源碼錨仍然綠。
3. 有人喺 `:1011` 之後加 writer，或把 H 搬入 STANDARD 區塊再改 if，錨點順序仍然綠。
4. 六個 lang key 缺一語或混入禁詞，`KEYS` 閘睇唔到。

KubeJS mechanic-cache **唔會**因為換語言而爛；爛嘅係「只搵當前語言檔、失敗冇 trace」。

pass2 結論：最貴嘅一個脆弱位係 `JarLightIndex.parseLootJson:270` 嘅 trivial `blocks/` 過濾綁住過期 jar-cache——包一更新正向句靜默消失，而三個新 harness 全部用 stub，繼續綠。
