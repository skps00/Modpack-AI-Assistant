# Plan — Slice 1：玩家睇得明（B／C1-lite／C5／C7／C8）（v3）

- 日期：2026-09-21；作者：JARVIS（SK 批准「1+3」）
- 狀態：**v3，待 R3 反方 review**（未改任何 code）
- Review 歷史：
  - R1（v1）＝ **正方 4 : 反方 6**
  - R2（v2）＝ **正方 4 : 反方 6**（`docs/plans/reviews/2026-09-21_slice1-v2-R2-opposing.md`）＋ 同時段獨立數字核實（`docs/plans/reviews/2026-09-21_slice1-v2-R2-anchor.md`：15 組 OK、**6 條 WRONG**）
- **v2 → v3 改動（吸收 R2 全部必修項）**
  1. **C8 hook 位置自相矛盾已刪**：v2 同時寫「早過 `:969`」同「hook index > STANDARD block end」＝算術上不可能。v3 只留一個位：`AskEngine` `:956`（PURPOSE 守衛閉合）之後、`:1010`（STANDARD 區塊閉合）之後、`:1011` 之前。
  2. **C1-lite 改設計**：刪「item-index 唯一命中」（`ItemIndexCache.Entry:45` 冇 loot owner 欄位；`ItemIndex.searchReady:111` 係分數搜尋，唔係 id→loot 反查）→ 改用**焦點物品自身 id 比對 leaf**（4 個呼叫點都拿得到 focus item id）。
  3. **拆 `AcquireAskTool.humanJarRoute:113-115`**（R2 P0：譯文唔含 table 就 `+ " " + table` ⇒ 泛用句會被補返 raw `blocks/x`）。
  4. **B 寫出三語全文**（v2 只有一句）；負控改為**呼叫真 method**（`AskReplyScrub.isPlayerSafeLine:1522`、`AskJeiHints.looksLikeAbsenceClaim:39`）。
  5. **§8 驗收事實修正**（anchor R2 實錘）：full 項鍊真 tooltip ＝「用于在沙漠维度地牢中进行神意挑战」（器官句屬 `active_pill`，唔屬項鍊）；empty 項鍊真值 ＝ 簡體「击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能」；補 build 命令＋JDK 路徑；「或泛用句」收緊。
  6. **§7 加逐符號落點**；`InfoCompleteness.java` 移出可改集合。
  7. **C5 解析落點寫死喺 client 側**（唔可以由 logic 層引入 `I18n`）。

## 1. 問題（真機實證）

| 代號 | 症狀 | 證據 |
|---|---|---|
| B | 索引空時答案寫成「沒有取得路徑」 | `ask-20260921-072740-…withered_nether_star` |
| C1-lite | 玩家見到 `掉落表：blocks/ritual_brazier`（raw 檔路徑） | `ask-20260921-073004-ars_nouveau_ritual_brazier` |
| C5 | tooltip 文字只以 lang key 入 facts → 答案講「請看遊戲內 Shift 提示」 | `ask-20260921-073427-…god_bless_full_necklace` |
| C7 | gap 面板吐 raw route code（`合成 crafting_shaped: minecraft:acacia_planks` ×8） | `ask-20260921-074759-tetra_modular_double` |
| C8 | Tetra 零件有 fact 但答案冇用；改裝版被講成空框架合成 | 同上 trace |

## 2. B：誠實措辭（v3 三語全文）

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
- 樹政策：neo 唔改。**注意**：`ask_miss_acquire_player` 本身已喺 `KEYS`（`check_reply_prompt_keys.py:11-28`）而佢 iterate 兩棵樹 ⇒ 只改 forge 會令 `--no-paused` 模式 FAIL；驗收一律用 **paused 模式**（`check_dual_tree_diff_symmetry.py` 只 WARN）。

## 3. C1-lite：唔露 raw path（v3 重寫）

- 4 個 `ReplyLang.lootTableObtain` 呼叫點（真值，雙方核實）：`AcquireAskTool.java:112`、`AskEngine.java:1712`、`PackIndex.java:1318`、`Plainify.java:212`（無第 5 個）。
- 兩式：`blocks/<leaf>`（`JarLightIndex.lootKeyFromPath:205-214`，源頭冇 ns）＋ `<ns>:<path>`（`Plainify.LOOT_TO_TABLE:38-39` group 2，強制有 `:`）。
- **新判定（純函數、唔加資料源；4 個呼叫點都拿得到 focus item id）**
  - helper 落 `Plainify.java`（白名單內）：`static String lootLine(String lang, String itemId, String table)`。
  - `container` = `table` 第一段（先剝 `ns:`）；`leaf` = 最後一段；`pathOf(itemId)` = `itemId` `:` 之後。
  - **`container.equals("blocks")` 且 `leaf.equals(pathOf(itemId))`** → 新 key `packai.reply.loot_table_block`（「破壞 %s 會掉落」，`%s` = `OfficialDisplay.officialName(itemId)`）；`officialName` 回空 → **改用泛用句**（唔准出 `（無官方名）`、唔准出 path）。
  - 其餘（leaf 唔對／子路徑如 `blocks/special/ice`／非 `blocks` 容器如 `chests/…`） → 新 key `packai.reply.loot_table_generic`（「由某個掉落表提供（本包未對應到名字）」，唔露 path）。
  - **唔做 item-index 反查**（`ItemIndexCache.Entry:45` 冇 loot 欄；`ItemIndex.searchReady:111` 係分數搜尋）⇒ 反查搬 Slice 2。
- **`AcquireAskTool.java` 必改**：`humanJarRoute:109-119` 刪走 `:113-115` 嘅「譯文唔含 table 就 `+ " " + table`」fallback；`humanJarRoute` 加 `itemId` 參數，`mergeJarRoutes:77` 傳入。
- 其餘呼叫點傳參：`AskEngine.infoGapLines:1712` 傳 `itemId`；`PackIndex:1318` 傳 `id`（同段已用 `LootForwardIndex.isTrivialBlockSelfLoot(id, table)`）；`Plainify.humanizeGraphFact:210-213` 傳 `m.group(1)`（`LlmClient:467` 嘅呼叫簽名唔改）。
- 新 key 含 `%s` ⇒ **唔入 `KEYS`**；x3 語只改 forge。
- 負控（真 lookup 用 test hook `OfficialDisplay.lookup`）：① `blocks/ritual_brazier` + `ars_nouveau:ritual_brazier` → 官方名句、唔含 `blocks/`；② 同一 table + `minecraft:stone` → 泛用句；③ `blocks/rope` → 泛用句；④ `chests/village/toolsmith` → 泛用句；⑤ `officialName` 回空 → 泛用句。

## 4. C5：kubejs tooltip key → 文字（v3 落點寫死）

- 解析鏈：① pack 內 `kubejs/assets/kubejs/lang/<code>.json`（實測只有 `zh_cn.json`、1,958 keys）→ ② 遊戲語言表 → ③ 全缺：泛用句（唔准出 raw key）。
- **落點（v3）**：解析喺 **client 側** `client/service/AskService.java` 緊接 `:765` `KubeJsMechanicScan.factsForItem(...)`（同 `OfficialDisplay.enrichFacts` 同區）做。**logic 層唔准引入 `I18n`／`Component.translatable`**：`KubeJsMechanicScan` 現時零 `net.minecraft` import，而 headless `*Check` classpath 冇 client class（會 `NoClassDefFoundError`）。
- cache 因此保持只存 raw key（`KubeJsMechanicScan:1093` hash = `sha256(rel+"\0"+src)`，唔含 lang）⇒ 唔會被烘死；切語言即刻正確。
- 缺 key 判定：真碼 `Language.getOrDefault` = `Map.getOrDefault(key, key)`（`javap -c` 實錘）⇒ 解析結果 `equals(key)` 就當缺 → 走 ③（唔准直接信 `.getString()`）。
- 斷言：答案／facts 輸出**唔准含** `kubejs.tooltips.`（zh_tw／en_us 一樣）。
- zh_tw／en_us 現實：pack 只有 zh_cn ⇒ 呢兩個語言走遊戲語言表（同玩家 tooltip 一致）；全缺出泛用句「請看遊戲內提示」（＝唔會比今日差）。

## 5. C7：gap 面板人話化（只改內容）

- **唔改插入位置**：`AskEngine:1011` → `InfoCompleteness.append` 保持；hook-order 閘唔動；`InfoCompleteness.java` 移出可改集合。
- 改 `AskEngine.infoGapLines:1699-1722`：
  - class 優先序 `L|` > `U|` > `R|`；每 class 各自 cap；總行數上限 **3**；超出加「另有 N 項」（新 lang key）。
  - 行內容：`L|` → §3 helper；`U|`／`R|` → `JarLightIndex.formatFact` 之後再過人話過濾，**禁止**出現 raw `crafting_shaped:`／`blocks/`；`R|` 今日會出「合成 crafting_shaped：minecraft:acacia_planks」（`JarLightIndex.formatFact:287` → `ReplyLang.jarCraft`，lang `合成 %s：%s`）⇒ Slice 1 只准出「同〈物品顯示名〉有關嘅配方」級人話（唔出 recipe type／raw id）。
- **同時寫 trace**：`AskTrace.event("check.info_gap", …)`（`AskTrace.java:133` public static；`:134-148` 全包 try，唔 NPE）。

## 6. C8：Tetra 零件 canonical 行（v3 位置唯一）

- 機制：`AskJeiHints.ensureCanonicalQuestLine:263-285`（純字串、插喺 `ReplySources.HEADER:274` 前）＋ `replaceWrongQuestishWithCanonical:291-317`；分類器 `ModularFrameStandard`（`Kind` `:24-28`）。
- **hook 位置（唯一；v2 嘅「早過 :969」已刪）**：`AskEngine` 內 **`:956`（`:950` `if (loop.intent() != PURPOSE)` 閉合）之後**、**`:1010`（STANDARD 區塊閉合）之後**、**`:1011` `InfoCompleteness.append` 之前**。
  - 理由（R2 實核）：`:950-956` 嘅 `ensureHowToGetBody` 喺 PURPOSE 守衛內，放守衛內會令 PURPOSE 唔貼；`:969-1010` 嘅 STANDARD 分支（`:980` `replaceHowToGetBody`）會由「怎麼來」段頭換到來源 header **整段** ⇒ 早過 1010 插入必被換走；MODIFIED（C8 要貼嘅態）唔入 `:969`，而 1010 之後插入唔會被任何 writer 刪（`AskMarkerRepair:69-78` 只補 marker；`AskResult:87` 只刪 absence 行；`AskService:1974-2003` 只喺 body 空白時 scrub）。
- harness 斷言（新 `ToolBuildCanonicalCheck`）：`count(hook) == 1`；`index(hook) > index(STANDARD 區塊閉合行)`；`index(hook) < index(InfoCompleteness.append)`；三個錨**必須唯一**（唔唯一即紅）。
- 內容（新 lang key）：MODIFIED → canonical【工具】行「這把零件＝〈部件顯示名〉…；空白框架合成只提供空框架，實際要在 Tetra 工作台組裝／更換部件。」缺行或改寫 → 貼回。
- 三態負控：STANDARD → 唔准貼；MODIFIED → 必須貼；**UNKNOWN → 唔准貼零件行**。
- prompt SoT：`tests/update_reply_prompts.py` **入白名單**（否則下次 regen 靜默回退）。
- 新 key 唔入 `KEYS`；x3 語只改 forge；`【工具】` 唔喺 `AskReplyScrub` token 表（`:27-39`）⇒ 唔會被剝。

## 7. 白名單（cursor 只准改；v3 加逐符號落點）

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

**Java（test／新 harness）**：`test/.../{LootLineHumanizeCheck,KubeJsTooltipTextCheck,ToolBuildCanonicalCheck}.java`

**Python**：`tests/{check_honest_miss,check_reply_prompt_keys,check_tool_miss_teaching,check_frame_standard_recipe_line,check_modular_frame_standard,update_reply_prompts}.py`、`research/gen_tmp_check.py`、`forge/1.19.2/tmp-check.gradle`、`code_change_log.md`

**唔准（硬）**：`logic/InfoCompleteness.java`、`logic/JarLightIndex.java`、`RecipeEmbed`／`RecipeCard`／`client/gui/AiAssistantScreen.java`、`tests/check_info_completeness_hook_order.py`、`HonestMiss` 判定邏輯、`neoforge/**`、`AskEngine:826`、部署／hot-copy jar。

## 8. 驗收（v3，逐條可跑）

1. **compile**：`cd forge/1.19.2 && ./gradlew.bat compileJava compileTestJava --rerun-tasks --console=plain -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"` → RC=0
2. **build**：`./gradlew.bat jar -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"` → RC=0（v3 補寫）
3. **harness**：53 → **56/56 綠**（逐 task 名 ＋ `--rerun-tasks`；判準＝任務數＝`…Check OK` 行數＝56、FAILED=0）
4. **python 閘**：`for f in tests/check_*.py; do python "$f" >/dev/null 2>&1 || echo "FAIL $f"; done` → **TOTAL 124／FAIL 0**（今日 baseline 親跑已確認 124/0）
5. **負控**：§2 三態、§3 五態、§6 三態，逐條「紅 → 還原 → 綠」
6. **真機（沙盒副本，JARVIS 自己跑；SK 用機時唔准動 GUI，跑前 `sk_activity.json` state 必須 idle ≥120s；焦點前後要係原本窗口）**
   - `ars_nouveau:ritual_brazier` → **必須**出「破壞 儀式火盆 會掉落」；**唔准**出現 `blocks/`；**若出泛用句＝紅**（leaf 命中 focus 就唔准走泛用）
   - `tetra:modular_double` → 必須有零件行（下界合金×2＋再利用梁杆）；gap ≤3 行且唔含 `crafting_shaped:`
   - `witherstormmod:withered_nether_star` → 必須含 §2 新句；唔准含「沒有取得路徑／not indexed／未索引」
   - `kubejs:god_bless_empty_necklace` → 必須出「击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能」（簡體原文）；`kubejs:god_bless_full_necklace` → 必須出「用于在沙漠维度地牢中进行神意挑战」；兩者**唔准**出現 `kubejs.tooltips.`
   - 每 case 一條 grep 斷言：trace（`<instance>\packai\trace\ask-*.jsonl`）＋ `latest.log` 嘅 `Pack AI display body ver=` 行
   - **焦點量度指令（v3 補）**：跑前後各跑一次 `python "$LOCALAPPDATA/hermes/scripts/activity_monitor.py" --once`／讀 `state/sk_activity.json`，記 `foreground.title`／`hwnd`；前後唔同 ＝ 搶焦點 ⇒ 紅
7. **C9 QA**：抽 5–10 件真物品用 mcmod 對照「有冇漏玩家重視嘅資訊類型」（開發期 oracle，唔入 code）

## 9. 風險／還原

- 風險：hook 位置放錯（已寫死唯一行＋harness 三錨唯一性斷言）；C5 遊戲語言表只喺 client（server-only 走泛用句）；`officialName` 回空走泛用句（唔會講錯）。
- 最壞：canonical 行貼錯 → 多半句（可 revert）；gap 行數變少（可 revert）。
- 還原：全 git（§7 白名單全部 `git ls-files` tracked 已核）＋ 沙盒 jar backup（`mc_mod_deploy_jar.py` 自動 backup）。
- 成本：沙盒一輪 4–6 條 ask；harness 零成本；真機要 SK 唔用機。
