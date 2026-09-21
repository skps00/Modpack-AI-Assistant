# Plan — Slice 1：玩家睇得明（B／C1-lite／C5／C7／C8）（v4.2）

- 日期：2026-09-21；作者：JARVIS（SK 批准「1+3」）
- 狀態：**v4.2 —— R4 達標（9:1）→ 已實作 → R1-pass1／R2-pass2 code review 已跑；pass1 三條 P1 已修（見下 v4.2 節）＋ Hermes 親驗（compile／57 harness／python 閘）綠；真機驗收待 SK 離機**
- Review 歷史：
  - R1（v1）＝ **正方 4 : 反方 6**
  - R4（v4）＝ **正方 9 : 反方 1（達標）**（`docs/plans/reviews/2026-09-21_slice1-v4-R4-opposing.md`；清單 A／B 全 RESOLVED、清單 C 無矛盾）＋ 同輪獨立核實 52 項（`...-v4-R4-anchor.md`；1 條文件級 WRONG＝裸 `Kind.STANDARD` 次數寫 2、真值 4 —— 已喺本版改正）
  - R3（v3）＝ **正方 7 : 反方 3**（`docs/plans/reviews/2026-09-21_slice1-v3-R3-opposing.md`；6 條 R1 flip ＋ 3 條 R2 必修全部 RESOLVED）＋ 同輪獨立核實 3 條 WRONG（`...-v3-R3-anchor.md`）
  - R2（v2）＝ **正方 4 : 反方 6**（`docs/plans/reviews/2026-09-21_slice1-v2-R2-opposing.md`）＋ 同時段獨立數字核實（`docs/plans/reviews/2026-09-21_slice1-v2-R2-anchor.md`：15 組 OK、**6 條 WRONG**）
- **v2 → v3 改動（吸收 R2 全部必修項）**
  1. **C8 hook 位置自相矛盾已刪**：v2 同時寫「早過 `:969`」同「hook index > STANDARD block end」＝算術上不可能。v3 只留一個位：`AskEngine` `:956`（PURPOSE 守衛閉合）之後、`:1010`（STANDARD 區塊閉合）之後、`:1011` 之前。
  2. **C1-lite 改設計**：刪「item-index 唯一命中」（`ItemIndexCache.Entry:45` 冇 loot owner 欄位；`ItemIndex.searchReady:111` 係分數搜尋，唔係 id→loot 反查）→ 改用**焦點物品自身 id 比對 leaf**（4 個呼叫點都拿得到 focus item id）。
  3. **拆 `AcquireAskTool.humanJarRoute:113-115`**（R2 P0：譯文唔含 table 就 `+ " " + table` ⇒ 泛用句會被補返 raw `blocks/x`）。
  4. **B 寫出三語全文**（v2 只有一句）；負控改為**呼叫真 method**（`AskReplyScrub.isPlayerSafeLine:1522`、`AskJeiHints.looksLikeAbsenceClaim:39`）。
  5. **§8 驗收事實修正**（anchor R2 實錘）：full 項鍊真 tooltip ＝「用于在沙漠维度地牢中进行神意挑战」（器官句屬 `active_pill`，唔屬項鍊）；empty 項鍊真值 ＝ 簡體「击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能」；補 build 命令＋JDK 路徑；「或泛用句」收緊。
  6. **§7 加逐符號落點**；`InfoCompleteness.java` 移出可改集合。
  7. **C5 解析落點寫死喺 client 側**（唔可以由 logic 層引入 `I18n`）。
- **v3 → v4 改動（吸收 R3 兩份）**
  1. §3 leaf 規則寫死**段數**（`segs == 2` 才可出官方名句）；負控改寫成 **(table, itemId) 對**（`blocks/rope` + 自己 id 係正向、`blocks/special/ice` 係泛用）。
  2. §3 補**第二道 raw path 出口**：`JarLightIndex.formatFact:296` → `ReplyLang.jarLoot:827`（`掉落：%s`）⇒ 喺白名單內嘅 `ReplyLang.jarLoot` 收口。
- **v4.1（實作期收窄，2026-09-21 19:4x）**：`blocks/` 以外嘅 `L|` 容器**保留今日原句**（唔走泛用句）——理由＝既有 `AcquireJarRoutesCheck:104-106` 硬 assert 原 table 字串會出現（實作後真跑見到 `runAcquireJarRoutesCheck FAILED`），而 SK 嘅投訴只針對 `blocks/`；`LootLineHumanizeCheck` 負控⑤ 同步改為期望原句。真機 cases 全部係 `blocks/*`，唔受影響。
  3. §3 列出 v4 五個新 lang key 嘅**三語全文**。
- **v4.1 → v4.2（code review R1-pass1 三條 P1 修補，2026-09-21 20:1x）**
  1. **§5 行數規則寫死**：刪「每 class 硬 cap 2」（該數字係碼自己加嘅）；改成**合併後總行數（連「另有 N 項」行）永遠 ≤3** —— 溢出時內容 2 行＋more 行（`cap - 1`）。抽出 package-private 純函數 `AskEngine.gapPanelLines(loot, use, other, lang)`（可測）＋新 harness `GapPanelCapCheck`（3/3/3、5/0/0、2/1/0、0/0/0、1/0/0 逐條 `size() ==`）。
  2. **§6 harness 加固**：`ToolBuildCanonicalCheck` 除原本 `idx(A1) < idx(H) < idx(A2)`（保留）外，加 `matchingBrace`（跳 string／char／`//`）斷言 **`idx(H)` 必須大過 STANDARD `if` 區塊配對 `}`** ⇒ 負控①「把 H 移入 A1 區塊」而家真係會紅（原本唔會）。
  3. **§4 harness 加固**：`KubeJsTooltipTextCheck` 加 case 4（pack 缺＋遊戲語言表回**真譯文**，原本刪走 `gameLang.apply` 都會綠）同 case 5（`loadKubeJsPackLang` 真讀 temp `zh_cn.json`＋缺檔回空 map）。
  4. **新增 python 閘** `tests/check_slice1_reply_keys.py`（R2-pass2 建議）：6 個新 key 三語齊、`%s` 次數（block／related／more／canonical＝1，generic／tooltip_hint＝0）、9 個禁字。
  5. **已知脆弱位（唔喺本 slice 修，延去 Slice 2，見 `...-R2-pass2.md`）**：① `JarLightIndex.parseLootJson` 重掃時靜默丟 `blocks/<itemPath>`（該檔喺禁止改清單 ⇒ 只可記錄；驗收**唔准清 jar-cache**）；② `AskJeiHints.partsNames` 綁死 `ToolBuildFacts.format` 字面，而 harness fixture 係手寫字串（`format` 一改就靜默失效）。
  4. §6 harness 錨改成**唯一字串**（裸 `Kind.STANDARD` 有 2 次、裸 `if (loop.intent() != …PURPOSE)` 有 2 次（`:369`／`:950`）⇒ 用咗會永遠紅）。
  5. §8 真機 case 1 改 zh_cn 斷言（`仪式火盆`）＋明寫「只准用現有 jar-cache、唔准清 cache；cache 重掃會因 `isTrivialBlockSelfLoot` 令呢條 `L|` 消失」。
  6. 行號更正：`formatFact` 嘅 `jarCraft` 喺 `:288`；`ReplySources.HEADER` 定義喺 `ReplySources.java:11`（插點 `AskJeiHints.java:274`）。

## 1. 問題（真機實證）

| 代號 | 症狀 | 證據 |
|---|---|---|
| B | 索引空時答案寫成「沒有取得路徑」 | `ask-20260921-072740-…withered_nether_star` |
| C1-lite | 玩家見到 `掉落表：blocks/ritual_brazier`（raw 檔路徑） | `ask-20260921-073004-ars_nouveau_ritual_brazier` |
| C5 | tooltip 文字只以 lang key 入 facts → 答案講「請看遊戲內 Shift 提示」 | `ask-20260921-073427-…god_bless_full_necklace` |
| C7 | gap 面板吐 raw route code（`合成 crafting_shaped: minecraft:acacia_planks` ×8） | `ask-20260921-074759-tetra_modular_double` |
| C8 | Tetra 零件有 fact 但答案冇用；改裝版被講成空框架合成 | 同上 trace |

## 2. B：誠實措辭（v4 三語全文）

- 目標＝**現有 key** `packai.reply.ask_miss_acquire_player`（三語檔各改一條；唔加新 key，所以 `check_reply_prompt_keys.KEYS` 唔使改）。
- 三語全文（**逐字，唔准改寫**）：
  - `en_us`：`No obtain data for this item in pack facts; it may be implemented in mod code (for example a boss drop or an event). Unsure — please confirm in game.`
  - `zh_cn`：`本包资料未见此物的获取途径；可能由 mod 代码实现（例如击败特定 boss／事件）。不确定，请以游戏内为准。`
  - `zh_tw`：`本包資料未見此物的取得途徑；可能由 mod 程式碼實作（例如擊敗特定 boss／事件）。不確定，請以遊戲內為準。`
- 已核（我親跑＋R2 反方獨立跑）：三句都**唔中** `AskReplyScrub.PLAYER_UNSAFE_MARKERS`（`:148-166`，17 條）、**唔中** `AskJeiHints.looksLikeAbsenceClaim`（`:39-78`）任何 literal、**唔中** 兩閘禁詞（`check_honest_miss.py:91-106`、`check_reply_prompt_keys.py:54-67`）、**含** `Unsure／不確定／不确定`、**唔含** `%`、**唔含** `未索引／not indexed`。
- 內部 key 不變：`acquire_index_miss` 保留 `未索引／not indexed` ＋ `禁止捏造／do not invent`，且唔准含 `%`（`check_honest_miss.py:80-89`）。
- **負控（harness 要呼叫真 method，唔准抄詞表）**：
  - ① 三語新句 → `isPlayerSafeLine(line)==true` 且 `looksLikeAbsenceClaim(line)==false`；
  - ② 含閘禁詞（例 `請明說`）句 → 閘紅；
  - ③ 含 `沒有列出`／`not listed recipe` 句 → `looksLikeAbsenceClaim==true` ⇒ 紅。
- 樹政策：neo 唔改。`ask_miss_acquire_player` 已喺 `KEYS`（真值 `check_reply_prompt_keys.py:23`）；該閘 iterate 兩棵樹但**只對同一 key 名做內容規則斷言**（唔比對兩樹字串）⇒ 只改 forge 唔會令它紅。另一支閘 `check_dual_tree_diff_symmetry.py` 因 `neoforge/README_PAUSED.md` 存在而**預設 paused**（forge-only lang diff 只 WARN、exit 0）；`--no-paused` 先 FAIL ⇒ 驗收一律用預設（paused）。

## 3. C1-lite：唔露 raw path（v4 重寫）

- 4 個 `ReplyLang.lootTableObtain` 呼叫點（真值，雙方核實）：`AcquireAskTool.java:112`、`AskEngine.java:1712`、`PackIndex.java:1318`、`Plainify.java:212`（無第 5 個）。
- 兩式：`blocks/<leaf>`（`JarLightIndex.lootKeyFromPath:205-214`，源頭冇 ns）＋ `<ns>:<path>`（`Plainify.LOOT_TO_TABLE:38-39` group 2，強制有 `:`）。
- **新判定（純函數、唔加資料源；4 個呼叫點都拿得到 focus item id）**
  - helper 落 `Plainify.java`（白名單內）：`static String lootLine(String lang, String itemId, String table)`。
  - `container` = `table` 第一段（先剝 `ns:`）；`leaf` = 最後一段；`pathOf(itemId)` = `itemId` `:` 之後。
  - **段數規則（v4 寫死）**：`table` 剝 `ns:` 之後，`container` = 第一段、`leaf` = 最後一段、**`segs` = `/` 分段數**。
  - 官方名句條件（**三條同時成立**）：`segs == 2`、`container.equals("blocks")`、`leaf.equals(pathOf(itemId))` → 新 key `packai.reply.loot_table_block`（「破壞 %s 會掉落」，`%s` = `OfficialDisplay.officialName(itemId)`）；`officialName` 回空 → **改用泛用句**（唔准出 `（無官方名）`、唔准出 path）。
  - `container.equals("blocks")` 但其餘唔命中（`segs != 2` 例 `blocks/special/ice`／leaf 唔對／`itemId` 空） → 新 key `packai.reply.loot_table_generic`（「由某個掉落表提供（本包未對應到名字）」，唔露 path）。
  - **非 `blocks` 容器（例 `chests/village/moon/blacksmith`、`entities/…`、裸 `artifact`）→ 保留今日原句 `ReplyLang.lootTableObtain(lang, table)`（v4.1 收窄）**。
    - 原因（實作期實錘，唔係推論）：既有 harness `AcquireJarRoutesCheck:104-106` 硬 assert acquire tool 輸出**含**原 table 字串（`chests/village/moon/blacksmith`／`artifact`／`entities/ender_dragon_extended`）⇒ 全部改泛用句會**整紅既有閘**（AGENTS：任何紅當 regress，唔准改 assert 求綠）。SK 09-21 投訴亦**只針對 `blocks/`**。」
  - **唔做 item-index 反查**（`ItemIndexCache.Entry:45` 冇 loot 欄；`ItemIndex.searchReady:111` 係分數搜尋）⇒ 反查搬 Slice 2。
- **`AcquireAskTool.java` 必改**：`humanJarRoute:109-119` 刪走 `:113-115` 嘅「譯文唔含 table 就 `+ " " + table`」fallback；`humanJarRoute` 加 `itemId` 參數，`mergeJarRoutes:77` 傳入。
- 其餘呼叫點傳參：`AskEngine.infoGapLines:1712` 傳 `itemId`；`PackIndex:1318` 傳 `id`（同段已用 `LootForwardIndex.isTrivialBlockSelfLoot(id, table)`）；`Plainify.humanizeGraphFact:210-213` 傳 `m.group(1)`（`LlmClient:467` 嘅呼叫簽名唔改）。
- **第二道保險（v4 補；R3 揭發另一條 raw path 出口）**：`JarLightIndex.formatFact:296` 嘅 `case 'L' -> ReplyLang.jarLoot(lang, rest)` → 而家出 `packai.reply.jar_loot` =「掉落：%s」＋ raw path（`JarLightIndex.factsForAsk` 等路徑都會出街；呢啲路徑冇 item 上下文，白名單又唔准改 `JarLightIndex`）。修法：喺**白名單內**嘅 `ReplyLang.jarLoot`（`logic/ReplyLang.java:827`）加同款判斷——`lootPath` 剝 `ns:` 後以 `blocks/` 開頭 → 回泛用句（新 key）；其餘保留原文。⇒ 全站 `blocks/*` raw path 一封。
- 新 key 含 `%s` ⇒ **唔入 `KEYS`**；x3 語只改 forge。
- 新 lang key（v4 寫死；x3 語；唔入 `KEYS`）：
  - `packai.reply.loot_table_block`：en `Breaking %s drops it`／zh_cn `破坏 %s 会掉落`／zh_tw `破壞 %s 會掉落`
  - `packai.reply.loot_table_generic`：en `Provided by a loot table (name not resolved in this pack)`／zh_cn `由某个掉落表提供（本包未对应到名字）`／zh_tw `由某個掉落表提供（本包未對應到名字）`
  - `packai.reply.info_gap_related`：en `Also related: %s`／zh_cn `另外相关：%s`／zh_tw `另外相關：%s`
  - `packai.reply.info_gap_more`：en `(+%s more)`／zh_cn `（另有 %s 项）`／zh_tw `（另有 %s 項）`
  - `packai.reply.tool_build_canonical`：en `Parts = %s; the blank-frame recipe only gives an empty frame — assemble/swap parts at the Tetra workbench.`／zh_cn `这把零件＝%s；空白框架合成只提供空框架，实际要在 Tetra 工作台组装／更换部件。`／zh_tw `這把零件＝%s；空白框架合成只提供空框架，實際要在 Tetra 工作台組裝／更換部件。`
- 負控（逐條寫成 **(table, itemId) 對**；lookup 用 test hook `OfficialDisplay.lookup`；**v4.1：⑤ 改為「保留原句」**）：
  - ① `blocks/ritual_brazier` + `ars_nouveau:ritual_brazier` → **官方名句**、唔含 `blocks/`
  - ② 同一 table + `minecraft:stone` → **泛用句**
  - ③ `blocks/rope` + `farmersdelight:rope` → **官方名句**（leaf 真命中，唔准當噪音）
  - ④ `blocks/special/ice` + `minecraft:ice` → **泛用句**（`segs == 3`）
  - ⑤ `chests/village/toolsmith` + `minecraft:stone` → **保留原句 `ReplyLang.lootTableObtain`**（`AcquireJarRoutesCheck` 鎖住；唔准改佢）
  - ⑥ `ns:blocks/ritual_brazier` + `ars_nouveau:ritual_brazier` → **官方名句**（有 ns 一式同一判定）
  - ⑦ `officialName` 回空（唔裝 lookup） → **泛用句**

## 4. C5：kubejs tooltip key → 文字（v4 落點寫死）

- 解析鏈：① pack 內 `kubejs/assets/kubejs/lang/<code>.json`（實測只有 `zh_cn.json`、1,958 keys）→ ② 遊戲語言表 → ③ 全缺：泛用句（唔准出 raw key）。
- **落點（v3）**：解析喺 **client 側** `client/service/AskService.java` 緊接 `:765` `KubeJsMechanicScan.factsForItem(...)`（同 `OfficialDisplay.enrichFacts` 同區）做。**logic 層唔准引入 `I18n`／`Component.translatable`**：`KubeJsMechanicScan` 現時零 `net.minecraft` import，而 headless `*Check` classpath 冇 client class（會 `NoClassDefFoundError`）。
- cache 因此保持只存 raw key（`KubeJsMechanicScan:1093` hash = `sha256(rel+"\0"+src)`，唔含 lang）⇒ 唔會被烘死；切語言即刻正確。
- 缺 key 判定：真碼 `Language.getOrDefault` = `Map.getOrDefault(key, key)`（`javap -c` 實錘）⇒ 解析結果 `equals(key)` 就當缺 → 走 ③（唔准直接信 `.getString()`）。
- 斷言：答案／facts 輸出**唔准含** `kubejs.tooltips.`（zh_tw／en_us 一樣）。
- zh_tw／en_us 現實：pack 只有 zh_cn ⇒ 呢兩個語言走遊戲語言表（同玩家 tooltip 一致）；全缺出泛用句「請看遊戲內提示」（＝唔會比今日差）。

## 5. C7：gap 面板人話化（只改內容；v4 逐 class 寫死）

- **唔改插入位置**：`AskEngine:1011` → `InfoCompleteness.append` 保持；hook-order 閘唔動；`InfoCompleteness.java` 移出可改集合。
- 改 `AskEngine.infoGapLines:1699-1722`：
  - class 優先序 `L|` > `U|` > `R|`；**唔設每 class 上限**；**合併後總行數（連「另有 N 項」行）永遠 ≤3**——溢出時內容只留 2 行，第 3 行係「另有 N 項」（`N` ＝被截走項數，含原本每 class 想砍嘅行）。落點＝`AskEngine.gapPanelLines`（v4.2 抽成 package-private 純函數，`GapPanelCapCheck` 直接測）。
  - 行內容（v4 寫死逐 class）：
  - `L|` → §3 嘅 `Plainify.lootLine(lang, itemId, table)`
  - `U|` → 新 key `packai.reply.info_gap_related`（`%s` = `OfficialDisplay.officialName(resultId)`，空則 `Plainify.displayName(resultId)`）；**唔准**出 recipe type（`crafting_shaped` 等）
  - `R|` → **唔出面板**（as-ingredient 噪音，SK 09-21 判要剔）；只寫 `AskTrace.event("check.info_gap_skip", …)`（記 `code`＋`reason=as_ingredient`）
  - 全部行先過 `AskReplyScrub.isPlayerSafeLine:1522`（假就丟，並寫 trace）
  - 空／假行剔除後總行數 **≤3**；截走嘅計數 → 新 key `packai.reply.info_gap_more`（`%s` = 項數）一行
  - 參考：`R|` 今日會出「合成 crafting_shaped：minecraft:acacia_planks」（`JarLightIndex.formatFact:288` → `ReplyLang.jarCraft`，lang `合成 %s：%s`）；`U|` 出「用于 %s → %s」。舊行為可以完全換掉，唔受任何 python 閘鎖住（`check_info_completeness_hook_order.py` 只鎖呼叫位置／次數）。
- **同時寫 trace**：`AskTrace.event("check.info_gap", …)`（`AskTrace.java:133` public static；`:134-148` 全包 try，唔 NPE）。

## 6. C8：Tetra 零件 canonical 行（v4 位置唯一＋唯一錨）

- 機制：`AskJeiHints.ensureCanonicalQuestLine:263-285`（純字串、插喺 `ReplySources.HEADER:274` 前）＋ `replaceWrongQuestishWithCanonical:291-317`；分類器 `ModularFrameStandard`（`Kind` `:24-28`）。
- **hook 位置（唯一；v2 嘅「早過 :969」已刪）**：`AskEngine` 內 **`:956`（`:950` `if (loop.intent() != PURPOSE)` 閉合）之後**、**`:1010`（STANDARD 區塊閉合）之後**、**`:1011` `InfoCompleteness.append` 之前**。
  - 理由（R2 實核）：`:950-956` 嘅 `ensureHowToGetBody` 喺 PURPOSE 守衛內，放守衛內會令 PURPOSE 唔貼；`:969-1010` 嘅 STANDARD 分支（`:980` `replaceHowToGetBody`）會由「怎麼來」段頭換到來源 header **整段** ⇒ 早過 1010 插入必被換走；MODIFIED（C8 要貼嘅態）唔入 `:969`，而 1010 之後插入唔會被任何 writer 刪（`AskMarkerRepair:69-78` 只補 marker；`AskResult:87` 只刪 absence 行；`AskService:1974-2003` 只喺 body 空白時 scrub）。
- harness 斷言（新 `ToolBuildCanonicalCheck`；**錨一律用唯一字串**，唔准用裸 `Kind.STANDARD`（真檔 4 次：`:363` `:470` `:969` `:1082`；其中 `== …Kind.STANDARD` 形式 2 次）或裸 `if (loop.intent() != …PURPOSE)`（真檔 2 次：`:369`、`:950`））：
  - `A1` = `if (frameKind == ModularFrameStandard.Kind.STANDARD && frameMatch.recipeIndex() != null) {`（真檔 1 次；閉合行 = `:1010`）
  - `A2` = `body = InfoCompleteness.append(`（真檔 1 次；`=` `:1011`）
  - `H` = 新 hook 呼叫字串（自己嘅方法名，必須全檔 1 次）
  - 斷言：`count(A1)==1 and count(A2)==1 and count(H)==1`（唔唯一即紅）＋ `idx(A1) < idx(H) < idx(A2)`＋**（v4.2 加固）`idx(H) > matchingBrace(src, src.indexOf('{', idx(A1)))`**（即 H 必須喺 STANDARD `if` 區塊配對 `}` **之後**；淨係 `A1<H<A2` 捉唔到「H 搬入區塊」）
  - 負控：① 把 `H` 移入 `A1` 區塊內 → 紅；② 加第二次 `H` → 紅；③ 還原 → 綠
- 內容（新 lang key）：MODIFIED → canonical【工具】行「這把零件＝〈部件顯示名〉…；空白框架合成只提供空框架，實際要在 Tetra 工作台組裝／更換部件。」缺行或改寫 → 貼回。
- 三態負控：STANDARD → 唔准貼；MODIFIED → 必須貼；**UNKNOWN → 唔准貼零件行**。
- prompt SoT：`tests/update_reply_prompts.py` **入白名單**（否則下次 regen 靜默回退）。
- 新 key 唔入 `KEYS`；x3 語只改 forge；`【工具】` 唔喺 `AskReplyScrub` token 表（`:27-39`）⇒ 唔會被剝。

## 7. 白名單（cursor 只准改；v4 加逐符號落點）

**Java（main）**
- `logic/Plainify.java`：新 `lootLine(String lang, String itemId, String table)`；`:210-213`（`humanizeGraphFact` 傳 `m.group(1)`）；`:64-86`（只讀）
- `logic/ReplyLang.java`：新 key 取用方法（`lootTableBlock`／`lootTableGeneric`／gap 相關）
- `logic/AcquireAskTool.java`：`humanJarRoute:109-119`（刪 `:113-115` fallback、加 `itemId` 參數）、`mergeJarRoutes:77`、`:112`
- `logic/AskEngine.java`：新 hook（`:1010` 之後、`:1011` 之前）、`infoGapLines:1699-1722`、`:1462`；**唔准動** `:826`、`:951-956`、`:969-1010`、`:1011` 位置
- `logic/PackIndex.java`：`:1314-1318`（傳 `id`）
- `logic/LlmClient.java`：`:467`（只跟簽名）
- `logic/KubeJsMechanicScan.java`：**只准加註解／唔准改邏輯**（note 保持 raw key）；若真需要改 → 停手報告
- `logic/AskJeiHints.java`、`logic/HonestMiss.java`、`logic/ModularFrameStandard.java`、`logic/AskResult.java`
- `client/service/AskService.java`：`:765-790`（note lang 解析；client 側唯一新落點）
- `resources/assets/packai/lang/{en_us,zh_cn,zh_tw}.json`（forge only）

**Java（test／新 harness）**：`test/.../{LootLineHumanizeCheck,KubeJsTooltipTextCheck,ToolBuildCanonicalCheck,GapPanelCapCheck}.java`

**Python**：`tests/{check_honest_miss,check_reply_prompt_keys,check_tool_miss_teaching,check_frame_standard_recipe_line,check_modular_frame_standard,check_slice1_reply_keys}.py`、`research/gen_tmp_check.py`、`forge/1.19.2/tmp-check.gradle`、`code_change_log.md`

**唔准（硬）**：`logic/InfoCompleteness.java`、`logic/JarLightIndex.java`、`RecipeEmbed`／`RecipeCard`／`client/gui/AiAssistantScreen.java`、`tests/check_info_completeness_hook_order.py`、`HonestMiss` 判定邏輯、`neoforge/**`、`AskEngine:826`、部署／hot-copy jar。

## 8. 驗收（v4，逐條可跑）

1. **compile**：`cd forge/1.19.2 && ./gradlew.bat compileJava compileTestJava --rerun-tasks --console=plain -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"` → RC=0
2. **build**：`./gradlew.bat jar -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"` → RC=0（v3 補寫）
3. **harness**：53 → **57/57 綠**（逐 task 名 ＋ `--rerun-tasks`；判準＝任務數＝`…Check OK` 行數＝57、FAILED=0）
4. **python 閘**：`for f in tests/check_*.py; do python "$f" >/dev/null 2>&1 || echo "FAIL $f"; done` → **TOTAL 125／FAIL 0**（09-21 20:2x 親跑：`PY_TOTAL=125 PY_FAIL=0`）
5. **負控**：§2 三態、§3 七對（(table, itemId)）、§5 兩態（`R|` 唔入面板／行數 cap）、§6 三態，逐條「紅 → 還原 → 綠」
6. **真機（沙盒副本，JARVIS 自己跑；SK 用機時唔准動 GUI，跑前 `sk_activity.json` state 必須 idle ≥120s；焦點前後要係原本窗口）**
   - **client 語言＝zh_cn**（trace `replyLanguage=zh_cn`；`heldItem.name`=`仪式火盆`）⇒ 所有字串斷言一律用**簡體原文**
   - `ars_nouveau:ritual_brazier` → **必須**含「火盆」＋「掉落」；**唔准**出現 `blocks/`（唔准出現 `掉落表：blocks/` 或 `掉落：blocks/`）
     - ⚠️ **cache 前提（R3 實錘）**：呢條 `L|blocks/ritual_brazier` 只存在於**現有** `config/packai/jar-cache/*.json`；`JarLightIndex.parseLootJson:270` 重掃時會因 `LootForwardIndex.isTrivialBlockSelfLoot` 丟咗佢 ⇒ **驗收唔准清／重建 jar-cache**；若 cache 被重建，本 case 降級成「唔准含 `blocks/`」＋正向斷言搬去 harness（§3 負控①），並即場由真 cache 另揀一件仍在、leaf 對得上嘅物品做真機正向 case（記錄揀到嘅 id）
     - `ars_nouveau:ritual_brazier` 條 `L|` 若因 cache 重掃消失 → 唔算品 bug（已知 C2／噪音過濾行為，Slice 2）
   - `tetra:modular_double` → 必須有零件行（含「下界合金」×2 同「再利用梁杆」）；gap ≤3 行且**唔含** `crafting_shaped:`（`R|` 噪音已剔）
   - `witherstormmod:withered_nether_star` → 必須含 §2 新句（zh_cn：「本包资料未见此物的获取途径」）；唔准含「没有取得路径」「not indexed」「未索引」
   - `kubejs:god_bless_empty_necklace` → 必須出「击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能」（簡體原文）；`kubejs:god_bless_full_necklace` → 必須出「用于在沙漠维度地牢中进行神意挑战」；兩者**唔准**出現 `kubejs.tooltips.`
   - 每 case 一條 grep 斷言：trace（`<instance>\packai\trace\ask-*.jsonl`）＋ `latest.log` 嘅 `Pack AI display body ver=` 行
   - **焦點量度指令（v3 補）**：跑前後各跑一次 `python "$LOCALAPPDATA/hermes/scripts/activity_monitor.py" --once`／讀 `state/sk_activity.json`，記 `foreground.title`／`hwnd`；前後唔同 ＝ 搶焦點 ⇒ 紅
7. **C9 QA**：抽 5–10 件真物品用 mcmod 對照「有冇漏玩家重視嘅資訊類型」（開發期 oracle，唔入 code）

## 9. 風險／還原

- 風險：hook 位置放錯（已寫死唯一槽＋harness 三個唯一錨）；C5 遊戲語言表只喺 client（server-only 走泛用句）；`officialName` 回空走泛用句（唔會講錯）；`blocks/` raw path 兩道收口（4 個呼叫點 ＋ `jarLoot`）——若有第 3 條出口，驗收「唔准含 `blocks/`」會捉到。
- 最壞：canonical 行貼錯 → 多半句（可 revert）；gap 行數變少（可 revert）。
- 還原：全 git（§7 白名單全部 `git ls-files` tracked 已核）＋ 沙盒 jar backup（`mc_mod_deploy_jar.py` 自動 backup）。
- 成本：沙盒一輪 4–6 條 ask；harness 零成本；真機要 SK 唔用機。
