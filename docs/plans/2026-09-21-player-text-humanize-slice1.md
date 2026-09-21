# Plan — Slice 1：玩家睇得明（B／C5／C7／C8）（v2）

- 日期：2026-09-21；作者：JARVIS（SK 批准「1+3」）
- 狀態：**v2，待 R2 反方 review**（未改任何 code）
- Review 歷史：**R1 = 正方 4 : 反方 6**（唔可開工）→ v2 逐條吸收 R1
- **v1 → v2 scope 變更**：
  - **C1（掉落表反查顯示名）→ 移去 Slice 2**。R1 證實：`config/packai/item-index/*.json` **完全冇**「邊個 ns 擁有 `blocks/x` 掉落表」資訊（只有 id/label/dedupe），而 `L|` route key 由 `JarLightIndex.lootKeyFromPath` **源頭就冇 ns**，且 `tests/check_jar_light_index.py:141` 鎖死「`L|` 無 ns」契約 → 反查做唔到。Slice 1 只做**降級版 C1-lite**：任何 `L|` 行**唔准出 raw path**，解析唔到顯示名就出泛用句（唔會講錯）。
  - **C4（卡／gap 次序）claim 刪除**：R1 證實 `client/gui/AiAssistantScreen.java:834-869` 係唯一決定點（白名單外），而且 `tests/check_info_completeness_hook_order.py:83-98` 硬鎖 `InfoCompleteness.append` 只 1 次＋固定位置 ⇒ 唔可以「插到卡之後」。C4 歸 Slice 2（如需）。

## 1. 問題（真機實證）

| 代號 | 症狀 | 證據 |
|---|---|---|
| B | 索引空時答案寫成「沒有取得路徑」 | `ask-20260921-072740-…withered_nether_star` |
| C1-lite | 玩家見到 `掉落表：blocks/ritual_brazier`（raw 檔路徑） | `ask-20260921-073004-ars_nouveau_ritual_brazier` |
| C5 | tooltip 文字只以 lang key 入 facts → 答案講「請看遊戲內 Shift 提示」 | `ask-20260921-073427-…god_bless_full_necklace` |
| C7 | gap 面板吐 raw route code（`合成 crafting_shaped: minecraft:acacia_planks` ×8） | `ask-20260921-074759-tetra_modular_double` |
| C8 | Tetra 零件有 fact 但答案冇用；改裝版被講成空框架合成 | 同上 trace |

## 2. B：誠實措辭（含 R1 修正）

**內部 key**（`ReplyLang.acquireIndexMiss:1210-1211` → `HonestMiss:109-130` → `AskEngine:1244`）：保留 `未索引/not indexed`＋`禁止捏造/do not invent`、**不得含 `%`**（`check_honest_miss.py:80-89`、`check_reply_prompt_keys.py:49-67`）。

**玩家 key 禁用詞（R1 補全＝閘 + runtime scrubber 兩層）**
- 閘層（`check_honest_miss.py:91-106`、`check_reply_prompt_keys.py:54-67`）：`禁止`、`必须`、`必須`、`不要用`、`请明说`、`請明說`、`do not invent`、`not indexed`、`未索引`、`render_recipe_cards`、`role=`
- **runtime scrubber 層（R1 新增）**：`AskReplyScrub.PLAYER_UNSAFE_MARKERS`（`:148-166`：`render_recipe_cards`、`[RECIPE_CARDS]`、`【JEI`、`role=`、`必须`、`禁止`、`不要用`、`请明说`、`DSML` …）＋`AskJeiHints.looksLikeAbsenceClaim`（`:39-78`：`沒有列出`、`没有列出`、`not listed`、`無配方`、`未持物品`…；觸發點 `AskResult.java:87 withRecipeCards` → **有卡就整句刪**）
- 玩家 key **必須含** `不確定`／`Unsure`。
- 文案（forge ×3 語）：「本包資料未見此物嘅取得途徑；**可能由 mod 程式碼實作**（例如擊敗特定 boss／事件）。不確定，請以遊戲內為準。」
  → 已逐字核**唔中**任何 runtime scrubber token ✅

**第二槓桿**：`AcquireAskTool.toolMissNote:43-48` 保持英文、保留 `do not invent`（`check_tool_miss_teaching.py:80-99`：首段 `;` 前不得有 CJK）。

**forge／neo 漂移決策（R1 B2 要求）**：**neo tree 唔改**（1.21.1 線暫停）→ 閘只用 paused 模式跑（`check_dual_tree_diff_symmetry.py` paused = WARN）；**新 key 唔加入 `check_reply_prompt_keys.KEYS` tuple**（否則 `:29-42` 會 iterate 兩樹逼改 neo）。

## 3. C1-lite：唔露 raw path（降級版）

- `ReplyLang.lootTableObtain`（`:454-456`）4 個呼叫點：`AcquireAskTool:112`、`AskEngine:1712`、`PackIndex:1318`、`Plainify:212`。
- 兩式都要處理：`blocks/<x>`（JarLight 側，無 ns）＋`<ns>:blocks/<x>`（Plainify `LOOT_TO_TABLE:38-39`／PackIndex 側，有 ns）。
- **有 ns 且 item-index 唯一命中** → `OfficialDisplay.officialName(id)`（`:85-105`；**唔用** `Plainify.displayName:64-86`，嗰個係由 path 砌字）＋新 key `packai.reply.loot_table_block`（「破壞 %s 會掉落」）。
- **冇 ns／多解／零解／子路徑（`blocks/special/ice` 等 8 條）** → `packai.reply.loot_table_generic`（「由某個掉落表提供（本包未對應到方塊名）」）——**唔露 path、唔猜**。
- 新 key 含 `%s` ⇒ **唔入 `KEYS` tuple**。

## 4. C5：kubejs tooltip key → 文字（含 R1 修正）

- 解析鏈改為：① pack 內 `kubejs/assets/kubejs/lang/<code>.json` → ② **遊戲語言表**（client 側 `Component.translatable(key)`／`I18n`，同玩家 tooltip 見到嘅一模一樣；`client/gui/AiAssistantScreen.java:844` 已有先例）→ ③ 全缺：**唔准出 raw key**，保留泛用句「請看遊戲內提示」（＝今日行為，唔可以更差）。
- `ReplyLang.tr` 唔會查 pack lang（只查 mod 自己 bundle，`:122-163`）→ v1 嘅第 2/3 步係死路，已刪。
- **解析時機**（R1 C5-2）：`KubeJsMechanicScan:792` 喺掃描期呼叫 `extractNote`，而 mechanic-cache hash（`:1093`）唔含 lang code ⇒ 解析必須搬到 **consume/display 期**（或 cache key 加 lang），否則切語言會出錯語言。
- 語系現實：`kubejs/assets/kubejs/lang/` 只有 `zh_cn.json`；zh_tw 會走遊戲語言表（＝玩家 tooltip 一樣）。

## 5. C7：gap 面板人話化 ＋ 入 log（唔改次序）

- **唔改插入位置**（`AskEngine:1011` → `InfoCompleteness.append` 保持；hook-order 閘唔動），只改**內容**：
  - class 優先序：`L|`（世界掉落）> `U|`（用途）> `R|`（配方）；每 class 各自 cap；header 帶「另有 N 項」（R1 C7-2）
  - 全部出路：顯示名（`OfficialDisplay.officialName`）／泛用句，**禁止 raw id、`crafting_shaped:`、`blocks/`**；`L|` 自掉噪音由 `LootForwardIndex`（`:1712` 側、只讀）繼續負責
  - 總行數上限 3（超出寫「另有 N 項」）
- **同時寫 trace**：`AskTrace.event("check.info_gap", …)`（`AskTrace.java:133` public、`:135-148` 安全唔會 NPE）。
- **刪 C4 claim**（見 §0）。

## 6. C8：Tetra 零件 canonical 行（含 R1 修正）

- 機制：`AskJeiHints.ensureCanonicalQuestLine:263-285`（純字串、插喺 `ReplySources.HEADER` 前、`:291-317` 換走講錯行）＋ wrapper 樣板 `ensureQuestStatusVisible:146-155`；分類器 `ModularFrameStandard.frameKind`（`Kind{STANDARD,MODIFIED,UNKNOWN}`，純記憶體、無 I/O）。
- **hook 位置寫死（R1 C8-1）**：`AskEngine` 內 **≥ `:951`（`AskReplyScrub.ensureHowToGetBody` 之後）**、且**必須早過** STANDARD 區塊替換（`:969-1010 replaceHowToGetBody`）生效位置 —— 因為 930 位置會被 951／980 覆蓋。並加 harness 斷言：**hook index > STANDARD block end**。
- 內容：改裝版（`MODIFIED`）→ canonical【工具】行「這把零件＝<部件顯示名>…；空白框架合成只提供空框架，實際在 Tetra 工作台組裝／更換部件。」缺行或改寫 → 貼回。
- **負控三態**：`STANDARD` → 唔准貼「未收錄」；`MODIFIED` → 必須貼；**`UNKNOWN` → 唔准貼零件行**（R1 C8-2；現行 fail-open 最易貼錯）。
- prompt 規則收緊：SoT 係 `tests/update_reply_prompts.py`（`packai.reply.llm_style`／`fact_check`）→ **必須入白名單**，否則下次 regen 靜默回退。
- 新 lang key：`packai.reply.*` 唔撞 `check_internal_label_parity.py`（只推導 `packai.label.src/role.*`）；`【工具】` 唔喺 scrub token 表 ✅；x3 語只改 forge、唔入 `KEYS`。

## 7. 白名單（cursor 只准改；R1 補 9 檔）

`logic/{ReplyLang,HonestMiss,AskEngine,AcquireAskTool,Plainify,PackIndex,KubeJsMechanicScan,AskJeiHints,InfoCompleteness,ModularFrameStandard}.java`、
`client/service/AskService.java`（`:1994/2003/2007` miss 出口＋prompt-echo scrub）、`logic/AskResult.java`（`:84-97` 最後流失位）、`logic/LlmClient.java`（`:467` Plainify 呼叫點）、`logic/AskEngine.java:1462`、
`resources/assets/packai/lang/{en_us,zh_cn,zh_tw}.json`（forge only）、
新 harness `test/.../{LootLineHumanizeCheck,KubeJsTooltipTextCheck,ToolBuildCanonicalCheck}.java`、
`tests/{check_honest_miss,check_reply_prompt_keys,check_tool_miss_teaching,check_frame_standard_recipe_line,check_modular_frame_standard,update_reply_prompts}.py`、
`forge/1.19.2/tmp-check.gradle`、`research/gen_tmp_check.py`、`code_change_log.md`。

**唔准**：`JarLightIndex`（Slice 2）、`RecipeEmbed`／`RecipeCard`／`client/gui/AiAssistantScreen.java`（卡落位）、`HonestMiss` 判定、`InfoCompleteness` 呼叫位置／`check_info_completeness_hook_order.py`、`neoforge/**`、`AskEngine:826`、部署。

## 8. 驗收（逐 case 寫死；R1 A1/A3 修正）

1. **harness**（53 → 56 綠）：C1-lite 三態（有 ns 唯一／多解／零解）、C5 三態（pack lang／遊戲語言表／全缺唔出 key）、C8 三態（STANDARD／MODIFIED／UNKNOWN）。
2. **負控**：玩家 key 含 ① 閘禁詞 ② runtime scrubber 詞 → 紅；③ 缺「不確定」→ 紅；④ C1-lite 多解 → 唔露 path；⑤ C5 全缺 → 唔出 raw key；⑥ C8 UNKNOWN → 唔貼。
3. **回歸**：`*Check` 56/56 綠；`tests/check_*.py` **124/124 綠**（唔係「零新增紅」）；forge 三語 key 集合一致；dual-tree 閘用 paused 模式（neo 唔改）。
4. **真機（沙盒副本，JARVIS 自己跑；顯示層係病徵，必做）**：逐 case 期望值——
   - `ars_nouveau:ritual_brazier` → 必須出「破壞 〈官方名〉 會掉落」或泛用句；**唔准** 出現 `blocks/`
   - `tetra:modular_double` → 必須有零件行（下界合金×2＋再利用梁杆）；gap ≤3 行且**唔含** `crafting_shaped:`
   - `witherstormmod:withered_nether_star` → 必須含 B 新句；**唔准** 出現「沒有取得路徑／not indexed」
   - `kubejs:god_bless_empty_necklace`（**同 full 一齊測**）→ 必須出中文 tooltip（full：`能够激发一部分高级器官的／激活效果`；empty：`擊敗虛空之花…即可充能`）
   - 每 case 一條可 grep 斷言（trace ＋ `latest.log` 嘅 `Pack AI display body ver=`）＋焦點前後量度指令寫明
5. **唔做會唔會綠？** 逐條自問已寫入 §8.1–8.4；`check_ask_display_leak.py` 需要真機（已列為必要，唔靠「免開遊戲」）。
6. **C9 QA**：抽 5–10 件真物品用 mcmod 對照「有冇漏玩家重視嘅資訊類型」（開發期 oracle，唔入 code）。

## 9. 風險／還原

- 風險：hook 位置放錯（已寫死行號＋harness 斷言）；C5 遊戲語言表只在 client 側（server-only 環境走泛用句）。
- 最壞：canonical 行貼錯 → 多一句（可 revert）。
- 還原：全 git；沙盒 jar backup（`mc_mod_deploy_jar.py`）。
- 成本：沙盒一輪 4–6 條 ask；harness 零成本。
