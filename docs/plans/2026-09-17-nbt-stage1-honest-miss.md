# Plan F v3.2（第 1 階段）— 標準框架照講合成台配方；特製版老實講「未收錄」

> 狀態：**v3.2（2026-09-17 18:5x）＝ ✅ 已過 review 閘** — 輪次：F-R1 **5:5** → F-R2 **3:7** → F-R3 **7:3** → **F-R4 正方 8 : 反方 2（達標）**。SK 指示係「做到 8:2 為止」⇒ **停手、唔開工**，等 SK 一句 go 才派 cursor 實作。⛔ **未實作**。
> ⚠️ §6 係歷史記錄；**本文 §1–§5 為準**；§7 逐條列 v2→v3 改咗咩、撤回咗咩。

## §0 症狀（兩類）＋真機證據（全部我自己跑出，命令見 §0b）

### 症狀 A — 標準框架（石刻）：明明有合成台配方，答案卻講「查不到」＋否定句
- 樣本：`<instance>/packai/trace/ask-20260917-122439-tetra_modular_sword.jsonl`（2026-09-17 12:24）。
- `tool.result`(rec 21, `tool_build`) 逐字：
  `part sword/hilt: sword/basic_hilt material basic_hilt/stick name 脆弱的` ／ `part sword/blade: sword/stonecutter material stonecutter/stonecutter name 石刀`
- jar `tetra-1.19.2-5.6.0.jar` → `data/tetra/recipes/stonecutter.json` `result.nbt` 逐字：`sword/blade=sword/stonecutter`、`sword/stonecutter_material=stonecutter/stonecutter`、`sword/hilt=sword/basic_hilt`、`sword/basic_hilt_material=basic_hilt/stick`（另加 per-instance `id` uuid）⇒ **兩者同組欄位一致＝標準框架**。
- 同一 trace `model.reply.final`（rec 53）逐字含：「**本包索引里查不到它的合成、掉落、交易、任务或脚本取得路径，取得方式无法确定**」＋「**空白的模组剑框架自身那个切石机合成只是空框架，不是这把定制工具的取得方式**」。
- 兩個工具都回空：`acquire`(rec 25) = `""`、`jei_lookup`(rec 27) = `""`；`check.cards`(rec 10) `reason=catalog_collect_empty`。
- 官方原文（jar `assets/tetra/lang/en_us.json`）：`item.tetra.stonecutter.description` = “**Combine with a stick in a crafting table** or use as a sword blade in a workbench to craft a powerful mining tool” ⇒ 合成台配方真存在。

### 症狀 B — 特製版（亞巴頓）：答案用空框架否定句頂住，冇老實講「未收錄」
- 樣本：`.../ask-20260917-122100-tetra_modular_single.jsonl`（2026-09-17 12:21）。
- `tool.result`(rec 21) 逐字：`part single/handle: single/archotech_void_scythe_handle ... name 灭天使之脊` ／ `part single/head: single/archotech_void_scythe ... item golden_age:archotech_void_scythe` ＋ 2 條 `improvement ... ultimate_stability` ⇒ 唔等於 jar 任何配方（`earthpiercer.json` = `single/head=single/earthpiercer`＋`single/handle=single/basic_handle`）⇒ **特製版**。
- `acquire`(rec 27) = `""`；`render_recipe_cards(role=output)`(rec 49) 回「框架合成卡已隱藏（非本工具取得途徑）」（**繁體原文**）；`check.cards`(rec 47) 其實**搵到** `category="Crafting", primaryOutputId="tetra:modular_single"` ⇒ 資料存在，但被**卡抑制政策**擋（`ModularFrameCards`）→ 屬刻意抑制，本階段**唔郁**（§2.7）。
- `model.reply.final`(rec 56) 逐字含：「**取得方式无法确定**」＋「**空白的单头模组框架自身只有切石机合成（空壳），那只是空框架，不是这把定制工具的取得途径**」。
- **政策文字係真兇（證據更正，v3.1）**：① 三語 `llm_style` 逐字含否定指令（zh_cn `:388`「空白 tetra:modular_* JEI（切石机＋木棍）只是空框架合成，不是这把定制工具的取得方式。」），而模型輸出嘅否定句同佢同源；② miss 引導雖然喺 prompt 內（`packai.reply.fact_check` 規則 19 含「未索引／明说未知」，224 條 `send.system` 中 173 條有），但**`acquire_index_miss` 本文（zh_cn `:472`）今日從未注入**——掃 51 個 `ask-*.jsonl` 全部 event：含「未索引：」**0**、含「请明说未知」**0**、含該 key 文案前綴 **0**（v3 原寫「已注入」係錯，已撤回；見 §7 撤回表）⇒ 模型係跟政策文字行，唔係跟 miss 引導。

## §0b 我自己量嘅基準（每條都寫死命令／時間；唔准靠估）

| 量嘅嘢 | 命令 | 結果（2026-09-17） |
|---|---|---|
| 判定器覆蓋（真實語料） | 見 §3 S4 harness 同款邏輯（Python 先驗版，跑 `trace/ask-*.jsonl` ＋ jar 26 個 recipe） | `trace/` 共 **51 個 `ask-*.jsonl`**（另 `index.jsonl`）／檔名含 `tetra_modular_` **15** 條／有 `tool_build` **14** 條 → 按 §1 判準：**標準 2**（`ask-20260916-132113`、`ask-20260917-122439`）／**特製 11**／**UNKNOWN 1**（`ask-20260914-131542`：`tool_build` = `[TOOL_BUILD]\nthis NBT not parsed` → 按 §1＝UNKNOWN，唔可當特製）／無 tool_build 1（`ask-20260914-132806`，唔可當標準） |
| jar 配方基數 | `python -c "import zipfile;z=zipfile.ZipFile(<tetra jar>);print([n for n in z.namelist() if n.startswith('data/tetra/recipes/') and n.endswith('.json')])"` | **26 個 json**（zip 內 28 個 entry ＝ 26 json＋2 目錄）；`result.item` 係 `tetra:modular_*` 嘅 = **13 個**（10×`hammer/*` ＋ `earthpiercer` ＋ `stonecutter` ＋ `toolbelt_modular`） |
| 閘 baseline | `for f in tests/check_*.py; do python "$f" \|\| echo FAIL $f; done` | **118 個／118 PASS／0 FAIL**（18:03 我親跑） |
| 答案 token 基準（45 條 `model.reply.final`） | 逐條 `in` 檢查；**繁簡兩種寫法都要列** | 繁體今日 **0**：「空白模組劍合成」「空白模組」「石切器」「無序合成」「目前沒有這件物品的取得資料」「未收錄」；**簡體今日有**：「空白模组」9、「空白模组剑合成」1、「空白框架」2、「未收录」4；另「只是空框架」6、「不是这把定制工具的取得方式」1、「取得方式无法确定」3、「切石机」9、「石刀」2、「木棍」9 ⇒ **「空白模組族」並非全新 token**，S1／S2 斷言要**繁簡一齊**比對（今日兩個指定樣本 `122439`／`122100` 係 0，所以 S1／S2 今日仍然係紅） |
| ask 耗時 | trace 首尾 `ts` 差（1 秒解析度） | 09-17 共 12 條：median **13s**／min 1s／max 22s（只作參考，1s 解析度） |

## §1 判準：標準框架 vs 特製版（寫死）

- 取手持 `[TOOL_BUILD]` 嘅 `part <slot>: <module>` 行 → 集合 `{slot: module}`；**排除** `id`（uuid，每件唔同）同 `*_material` 行。
- 同 jar `data/tetra/recipes/*.json` 之中 **`result.item` 係 `tetra:modular_*` 嘅 13 個**配方嘅 `result.nbt` 同組集合比對：**相等 → 標準框架**；唔相等 → **特製版**；缺件／例外 → `UNKNOWN`（§2.1 fail-open）。
- 標準框架 → 照講合成台配方（例：石刀〔`tetra:stonecutter`〕＋木棍 → 石刻），並**標明係「空白模組劍合成（該材料版本）」**。
- 特製版 → **老實講「這個版本嘅取得途徑未收錄」**（玩家句由 code 插入，§2.3）＋最多一句「似乎同任務內容有關」（**只有**現行 task 側資料指到呢件物時）。**唔准**用空白框架配方頂替，亦唔准講「只是空框架／不是取得方式」式否定。
- ⛔ 卡抑制政策（`ModularFrameCards` / `AskService.suppressModularFrameCards` / prompt pin）**本階段唔郁**；本階段只改答案文字＋判定器。

## §2 落點（逐項；實作檔白名單見 §4）

1. **判定器（新檔）**：`forge/1.19.2/src/main/java/com/skps9/packai/logic/ModularFrameStandard.java`（必留 `com.skps9.packai.logic`——`ToolBuildFacts.SKIP_KEYS` 係 `private`（`ToolBuildFacts.java:24`），`looksLikeUuid`／`isMaterialKey`／`isSlotKey`／`isImprovementKey` 係 package-private（`:220／:225／:236／:252`））。純函數、只做集合比對：**唔准讀檔、唔准掃 index**；回 `STANDARD／MODIFIED／UNKNOWN`。
2. **⛔ 唔准改 `ToolBuildFacts.format()` 嘅輸出格式**：`tests/check_tetra_tool_build.py` 有 Python mirror `format_scan`（`:143`）＋逐字 assert（例 `:365`）⇒ 只改 Java ＝ mirror 靜默失同步（假綠）。判定結果走**獨立** fact 行／獨立 code 位，唔入 `[TOOL_BUILD]` 本體。
3. **玩家句（決定性，唔靠模型自發）**：特製版答案嘅 miss 句＝現成 `ReplyLang.askMissAcquirePlayer(lang)`（lang key `packai.reply.ask_miss_acquire_player`；zh_cn `:475`／en_us `:479`）。用**現成 post-LLM 強制機制**（先例：`AskEngine.java:914 RecipeGetMarks.ensureVisibleInReply`、`:916 AskJeiHints.ensureQuestStatusVisible`（實作 `AskJeiHints.java:146`））確保句一定喺答案；缺就補。
   - ⚠️ **唔准**用 `packai.reply.acquire_index_miss`（zh_cn `:472`「未索引：…请明说未知 — 禁止捏造…」）做驗收字串——嗰句係**模型用內部指示**，唔應該出現喺玩家答案（三語都冇「未收錄」字樣）。
4. **三條 pin 出口要同步**（今日條件逐條核實）：`AskEngine.java:350`（`shouldPinAcquireMiss(acquire, hasObtainRecipes(...), question, heldItemId)` 另要 `!JeiInfoFacts.hasAny(jeiSummary) && jeiInfo.isEmpty()`）／`:457`（要 `loop.missPin()`）／`:1010`（`obtainRecipes` 離線路）；另 `:372-378` 係「skipLlm」確定性 miss body 既有先例。特製版要行到**同一條**出口。
5. **唔准壓制已有取得事實**：`js_obtain`／任務 tasks 側／loot 等現行 acquire 通道照講；有真途徑就唔行 miss。判定器只可以**加框架**，唔可以刪事實。
6. **語言檔＝只改 9 處**（3 檔 × `llm_style`／`llm_style_notools`／`tool_build`；行號實測 zh_cn `388／389／484`、en_us `392／393／484`、zh_tw `392／393／484`）：
   - 改嘅係**同一條政策句**（現行 zh 版本逐字：「空白 tetra:modular_* JEI（切石机＋木棍）只是空框架合成，不是这把定制工具的取得方式。」；EN 版本 `empty-frame craft only -- not how this customized tool was obtained`；`tool_build` 第 23 條版本另見下）。
   - 新語意（4 條）：① 合成台無序合成（例：石刀＋木棍 → 石刻）＝**空白模組劍合成**，要**照講**；② 標準框架（部件對得上標準配方）→ 照講呢個配方並標明「空白模組劍合成（該材料版本）」；③ 特製版（部件唔對應任何標準配方產出）→ 老實講「這個版本嘅取得途徑未收錄」＋（可選）一句任務相關提示，**唔准**用空框架配方頂替；④ 唔准寫否定句（唔准將「空白框架合成」寫成「唔係取得方式」一類句子）。
   - **必須同 commit 保住嘅閘 token**（`tests/check_reply_prompt_keys.py:376-392`）：`:379-384` `tool_build` 要有 `empty-frame`／`empty modular`／`空白模組`／`空白模组` 之一；`:385-390` 要有 `how this customized`／`empty-frame`／`禁止當成這把`／`禁止当成这把` 之一；`:391` `"[TOOL_BUILD]" in llm_style`。zh_cn／zh_tw 兩檔**只可以**靠「空白模組」「禁止当成这把」過閘 ⇒ 改寫時兩個 token 一定要留。
   - ⚠️ **同一句禁令要向「特製版」scoping**：`tool_build` 留住嘅「禁止当成这把…」只可以約束**特製版**（部件唔對應標準配方）；**標準框架**分支一定要寫成肯定句（「合成台無序合成：石刀＋木棍 → 石刻，係空白模組劍合成／該材料版本」），否則同一句禁令會令標準框架又跌返去做否定句（R3 落地提醒）。
   - **唔准**喺 `llm_style_notools` 提工具名（`tests/check_prompt_notools_no_toolwords.py`：`render_recipe_cards`／`jei_lookup`／`jei_info_use`／`jei_info_acquire`／`dump_level`）。
7. **唔郁**：`PackAiConfig` 預設、`showHiddenQuests=false`、`QuestGuide` spoiler 規則（`SPOILER_BOOL_KEYS`／`isSpoilerHiddenQuestObject`）、卡抑制政策（`ModularFrameCards`／`suppressModularFrameCards`）、trace 事件名／欄位格式（**只准加 `LOGGER` log 行，唔准加新 event／新欄位**）、`neoforge` 樹（停擺）、`AGENTS.md`。

## §3 驗收（每條寫死：樣本／命令／今日紅綠）

- **S1（特製版 → 老實講未收錄）**｜樣本＝真機手持亞巴頓（同 `ask-20260917-122100` 同款 NBT：`single/head=single/archotech_void_scythe`＋`single/handle=…scythe_handle`＋2 improvement）。
  - **今日紅**：final(rec 56) 含「只是空框架」「取得方式无法确定」，且**冇** `askMissAcquirePlayer` 句。
  - **綠**：新 trace 的 `model.reply.final` ① 含 `ReplyLang.askMissAcquirePlayer(lang)` **逐字**（由 lang 檔即時讀，唔准 hardcode；zh_cn 現值＝「目前没有这件物品的取得资料，暂时不确定怎么拿到。」）；② **唔含** 禁用句（逐字：`只是空框架`／`空框架合成，不是`／`不是这把定制工具的取得方式`／`不是這把定製工具的取得方式`／`不是这把定制工具的取得途径`／`取得方式无法确定`）；③ 有 trace 事件／log 記「miss 句已插入」以資鑑別；④ **反向斷言（唔准假肯定）**：答案若出現 `空白模組劍合成`／`空框架`／`空白框架` 任一字樣，就**必須同時**含 ① 嘅 miss 句（即標明嗰個只係空白版本，唔係呢件嘅途徑）。
- **S2（標準框架 → 照講合成台配方，唔准否定）**｜樣本＝真機手持石刻（同 `ask-20260917-122439`）。
  - **今日紅（寫準）**：final(rec 53) 其實**已經**出現「木棍」「石刀」「切石机」（講零件），但 ① **冇**「空白模組劍合成」標籤、② 冇講「合成台（無序合成）」呢個動作、③ **含**禁用句（「只是空框架」「不是这把定制工具的取得方式」「取得方式无法确定」）⇒ 紅。今日 45 條答案之中「空白模組劍合成」= 0（簡體「空白模组剑合成」= 1）。
  - **綠**：final 同時含〔「木棍」〕＋〔`石刀`／`切石机`／`切石機`／`石切器` 之一〕＋〔「空白模組劍合成」或「空白模组剑合成」〕；並**唔含** S1 禁用句清單。若答案用「通用知識」帶出配方，要按現行規則標明（唔准當包內事實）。
- **S3（負控，弱）**：`minecraft:bedrock` → 仍准講「查不到／未收錄」，**唔准**亂引配方或 Tetra 知識。今日已綠 ⇒ 只作弱斷言，唔計入達標分。
- **S4（判定器 harness，機檢）**｜harness 名＋命令**寫死**：
  - 新 `forge/1.19.2/src/test/java/com/skps9/packai/logic/ModularFrameStandardCheck.java`（`java -ea` 入口）；先 `python research/gen_tmp_check.py` 重生 `tmp-check.gradle`（每個 `*Check.java` 自動一個 task），再
    `cd forge/1.19.2 && ./gradlew.bat -I tmp-check.gradle runModularFrameStandardCheck -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"`
  - fixture：真數據——由 jar 26 個 recipe 抽 **13 個 modular 配方**嘅 `result.nbt` ＋ 由 trace 抽 **14 條** `tool_build` 輸出；**成員表要分四類**（第 4 類係**另加**樣本，唔喺上面 14 條抽樣之內，所以 14/15 數目唔同係正常）：標準 **2**（`ask-20260916-132113`、`ask-20260917-122439`）→ 必回 `STANDARD`；特製 **11**（`ask-20260917-122100` 等）→ 必回 `MODIFIED`；**UNKNOWN 1**（`ask-20260914-131542`，`tool_build` = `[TOOL_BUILD]` + `this NBT not parsed`）→ 必回 `UNKNOWN`，**唔可以**當 `MODIFIED`／`STANDARD`；無 `tool_build` 1（`ask-20260914-132806`）→ 唔可以當 `STANDARD`。
  - 斷言：標準 → `STANDARD`；特製 → `MODIFIED`；UNKNOWN 樣本 → `UNKNOWN`；亂／缺欄位／例外 → `UNKNOWN`（fail-open）。
  - **紅→綠證明**：先釘一個「一律回 STANDARD」嘅 stub ⇒ harness 必須紅（否則零鑑別力）。
- **S5（唔可以退步，機檢）**：`tests/check_*.py` → baseline **118／118 PASS／0 FAIL（2026-09-17 18:03）**；收貨＝**同 baseline 一樣零紅**。
- **S6（玩家文字乾淨）**：`python tests/check_ask_display_leak.py --trace "<instance>/packai/trace" --since 20260917 --min-annotations 0` → RC=0；另答案**唔准**含 `[TOOL_BUILD]`／`未索引：`／`acquire_index_miss` 句。
- **S7（notools 路徑）**：3 檔嘅 `llm_style_notools` 同樣要含新語意（機檢：`grep -c` 新 token＝3；`grep` 舊否定句＝0）＋現成 no-tools 閘綠。真機 smoke **1 次**（設定頁熄工具模式，問同一件特製版）→ **SK 動作＝1 次設定切換＋1 條問題**；唔做＝該路徑只算語言層已驗（誠實列明）。
- **S8（成本／fail-open）**：判定器純記憶體（唔讀檔、唔掃 index）；harness 量 **1000 次呼叫總 ms ≤ 50ms**（保守上限，實測超標當紅）；任何例外／缺件 → `UNKNOWN` → **當標準框架＝現行行為**（fail-open）。
- **S9（唔准碰白名單以外）**：⚠️ 工作樹**已經有 107 個未 commit 改動**（包括白名單檔本身：3 個 lang 檔各 145 行、`logic/AskEngine.java` 76 行）⇒ **開工前先記 baseline**：`git status --porcelain > .hermes/backups/2026-09-17_stage1_honest_miss/pre_status.txt`，收貨斷言＝**新出現／新改**嘅路徑全部喺 §4 白名單之內（舊 dirty 唔算）；`git diff --stat -- <spoiler／config 檔>` 必須同 baseline 一樣空。
- **S10（唔准誤判「有取得事實」嘅物品）**：harness 級（唔靠真機）——同一支 `ModularFrameStandardCheck`（命令同 S4）內加：fixture 注入「`MODIFIED` 判定 ＋ acquire 非空（例：腳本／掉落途徑文字）」→ 斷言 ① 唔准出 miss 句、② 唔准覆蓋 acquire 事實。另記（誠實限制）：今日 trace 未見非 jar 途徑嘅正控樣本（`ask-20260917-103612-kubejs_god_bless_full_necklace.jsonl` 嘅 `acquire` 仍係 `""`）⇒ 真機層要第 2 階段先有正控。

## §4 風險／還原

- **最壞情況**：① 標準框架被誤判特製 → 明明有配方都講「未收錄」⇒ S4（2 條真標準樣本必須 STANDARD）＋S2 擋；② 模型照舊回帶禁用句（今日 6/45 有此句；中文 prompt 要保住「禁止当成这把」token 先過閘）⇒ S1/S2 紅。**收貨條件**：若真機 **3 問中 ≥1** 仍出禁用句 → 停手報 SK（改成語意式閘要動 `tests/check_reply_prompt_keys.py` 嘅 assert＝測試改動，唔自己揀）。
- **改動檔白名單**：`logic/ModularFrameStandard.java`（新）、`logic/AskEngine.java`（miss 出口／強制句）、`logic/HonestMiss.java`（如需）、`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`（9 處）、`src/test/java/.../ModularFrameStandardCheck.java`（新）、`tests/check_modular_frame_standard.py`（新）。**唔准**碰：`ToolBuildFacts.java` 輸出格式、`ModularFrameCards*`、`PackAiConfig`、`neoforge`。
- **還原點**：改動前把白名單檔 copy 去 `.hermes/backups/2026-09-17_stage1_honest_miss/` ＋ `md5sums.txt`（現時 `.hermes/backups/` 只有 `2026-09-17_settings_gui`，即係今次係新備份）＋ `git status --porcelain` 快照（S9）；jar 由 `python "$LOCALAPPDATA/hermes/scripts/mc_mod_deploy_jar.py" --target packai` 自動 backup（唔准 hot-copy）。⛔ **唔准**用 `git checkout`／`git stash`／`git restore` 做還原——工作樹有 **107 個未 commit 改動**（含白名單檔本身），一 checkout 就會掃走未 commit 嘅工作。
- **⚠️ 錨點嘅保鮮期**：全文所有行號（`zh_cn:388／472／475`、`en_us:392／479`、`zh_tw:392`、`AskEngine:350／457／914／916／1010`、`ToolBuildFacts:24／220…`）係 **2026-09-17 未 commit 工作樹**嘅狀態（`git show HEAD:…zh_cn.json` 嘅 `:388` 係另一個 key）⇒ 一旦有任何 commit／rebase／加減行，**開工第一步要重核全部行號**再落手。

## §5 第 2 階段（未批准，唔做）

1. 任務側 NBT 比對（`tasks:` 帶 NBT 要求）＋任務**獎勵**來源（`rewards:` walk）——SK 已定：秘密任務獎勵唔指名（可一句提示）、只作圖案（`icon:`）唔出聲。
2. 上面 §3 S7 若紅 → 語意式閘＋更新 `check_reply_prompt_keys.py` assert（要 SK 批）。
3. 卡抑制政策檢討（`框架合成卡已隐藏`）——本階段刻意唔郁。

## §6 Review 記錄

| 輪 | 比分（正方 : 反方） | 關鍵 |
|---|---|---|
| F-R1 | **5 : 5** | 揭自我矛盾：要求答案唔再出空框架否定，但同時「唔准改政策文字」；另錨點錯、樣本類型錯 |
| F-R2 | **3 : 7** | ① 矛盾原文仍在（§2 第 5 點 vs §7A）② **S1 樣本分類錯**（石刻其實係標準框架，同 S2 撞同一件）③ S1 字串 `acquire_index_miss` 根本冇「未收錄」字樣、且係模型用內部句 ④ §7E 27→**26** ⑤ S2 今日唔係綠、冇紅綠條件 ⑥ S4／S7／S8／S9 未收口 ⑦ §7C 刪提示行同 plan E §0 SK 決定唔一致 |
| F-R3 | **7 : 3** | 1／2／5／6／7 全 RESOLVED；3、4 未收口：① §0 症狀 B「miss 事實已注入」證據句**假**（實測 0/51，173 命中係 `fact_check` 規則 19）② §0b「特製 12」錯（`ask-20260914-131542` = `this NBT not parsed` ⇒ UNKNOWN）③ token 基準只量繁體（漏「空白模组」9 等）＋引文非逐字 ⇒ v3.1 已修 |
| F-R4 | **8 : 2 ✅ 達標** | 4 點：1／2／3 RESOLVED（R3 三條全部機核通過）；第 4 點只剩**文件一致性**（§7.2 一句數字未同步）＋3 個 nit ⇒ v3.2 即場修完（見 §7 尾）。**無 blocker、無新矛盾。** |

> **✅ 2026-09-17 18:5x：F-R4 = 正方 8 : 反方 2 → 過閘**（輪次：5:5 → 3:7 → 7:3 → **8:2**，共 4 輪，未到 20 輪上限）。依 SK 指示：**停手，唔開工實作**，等 SK 一句 go。

> 註：v2 尾段（commit `c7093e8`）嘅「§8 F-R2 裁決」係上一 session 記低嘅同一批 finding，內容已**全部**併入本版 §7（唔留兩份，免實作者睇到硬分叉）。

## §7 v3 修正（逐條對 F-R2）＋撤回清單

**改咗（有證據）：**
1. **§2 第 5 點矛盾刪除**：語言檔由「唔郁」改成「**只改 9 處**」（§2.6 逐檔逐 key 逐行號）＋列明必須保住嘅閘 token；`§5` 第 2 階段清單移除「政策文字 9 處」。
2. **§1／§3 樣本重新分類（我自己跑出嚟）**：石刻＝**標準框架**（NBT 對得住 `stonecutter.json`）→ 移去 S2；S1 改用**真機特製版**（亞巴頓 `ask-20260917-122100`）。全語料 15 條 modular 問答 → **標準 2／特製 11／UNKNOWN 1（`ask-20260914-131542`）／無 tool_build 1**（v3 當時誤記「特製 12」，v3.1 已更正；命令見 §0b），S1／S2 唔再撞同一件物。
3. **S1 出口字串更正**：由 `packai.reply.acquire_index_miss`（模型用內部指示，三語冇「未收錄」）改成**玩家句** `packai.reply.ask_miss_acquire_player`（zh_cn `:475`），並要求**由 code 決定性插入**（先例 `AskEngine.java:914／:916`），令斷言同改動有因果（今日 0/45）。
4. **數字更正**：jar `data/tetra/recipes/` **27 → 26 個 json**（13 個提及 modular 唔變）。
5. **S2 有紅綠**：今日紅證據＝`122439` final 逐字含否定句、「取得方式无法确定」、冇講配方；綠＝同時含 `木棍`＋`石刀`／`切石机`＋`空白模組劍合成` 且無禁用句。
6. **S4／S7／S8／S9／S10 收口**：harness 名（`ModularFrameStandardCheck`）＋命令（`-I tmp-check.gradle runModularFrameStandardCheck`）＋fixture（13 modular 配方＋14 條真 trace）＋紅→綠 stub；notools 一條真機 smoke（SK 動作數）＋語言層機檢；成本上限 1000 次 ≤50ms＋fail-open；白名單 diff；KubeJS 唔准誤判。
7. **§0 加真機證據**（逐字 trace 引文＋jar 配方原文＋Tetra 官方 lang 描述）；**§0b 加基準表**（118/118、26/13、token 基準、ask 耗時）。
8. **S3 標明弱斷言**（今日已綠、唔計分）；S5 baseline 寫我實測時間同數。
9. **§0b／§4 記低 mirror 風險**：唔改 `ToolBuildFacts.format()`（`tests/check_tetra_tool_build.py` Python mirror `format_scan:143`＋逐字 assert `:365`）。

**撤回清單（原聲稱｜否證證據｜因為撤回而避開嘅副作用）：**
| 原聲稱 | 否證證據 | 避開咗嘅副作用 |
|---|---|---|
| v1／v2 §3 S1「石刻係特製版」 | `ask-20260917-122439` `tool_build` 逐字 == jar `stonecutter.json` `result.nbt` 同組欄位 | 唔會令 S1「永遠紅」或逼實作造假（把標準框架講成未收錄） |
| v2 §7C「刪『一句任務相關提示』」 | plan E §0 SK 決定 1（1b：唔指名但**可以少量提示**） | 唔會單方面推翻 SK 已批嘅答案格式；改為「只有現行 task 側資料指到呢件物才出」 |
| v2 §3 S2「照舊講 Tetra 配方」 | `132113`／`122439` 兩條標準樣本今日答案都**否定**＋唔講配方 | 唔會用「現狀已 OK」掩蓋標準框架同樣係錯（SK 原始投訴） |
| v2 §3 S4（任務獎勵／icon 樣本） | 兩條樣本早被 `showHiddenQuests=false`／`TYPE_ITEM` 擋＝假綠，且屬第 2 階段範圍 | 唔會用假綠樣本當驗收；移去 §5 第 2 階段 |
| v2 §7E「27 個 json」 | zipfile 列名＝26 json（＋2 目錄） | 唔會令覆蓋面數字同真 artifact 唔一致 |
| v2 §2.4「唯一出口」講法 | 實際三條 pin 出口（`AskEngine:350／:457／:1010`）＋skipLlm 路 `:372-378` | 唔會漏改屬路徑（tools 綠、notools 紅） |
| **v3 §0 症狀 B「`send.system` 已含 `acquire_index_miss` 事實（224 中 173）」** | 我實測掃 51 個 `ask-*.jsonl` 全部 event：含「未索引：」**0**、含「请明说未知」**0**、含該 key 文案前綴 **0**；173 命中係 `packai.reply.fact_check` 規則 19 嘅「未索引／明说未知」字樣（唔係 acquire fact 注入） | 唔會用假因果鏈撐「真兇係政策文字」；改用真證據（`llm_style:388` 逐字否定指令＋miss 引導只喺 prompt 規則層） |
| **v3 §0b「特製 12」** | `ask-20260914-131542` 嘅 `tool_build` = `[TOOL_BUILD]` + `this NBT not parsed` ⇒ 按 §1 自己嘅規則＝UNKNOWN，唔可以計特製 | 唔會令 S4 fixture 嘅「特製全部 → MODIFIED」對該樣本必紅（自打嘴巴） |

**v3.1（依 F-R3 三條；全部係 evidence/claim 衛生，冇改設計）：**
1. §0 症狀 B 嘅因果句改正（撤回「miss 事實已注入」假證據，換 `llm_style:388` 逐字否定指令＋miss 引導只喺 prompt 規則層）——見上面撤回表。
2. §0b 覆蓋數：特製 12 → **11 ＋ UNKNOWN 1**；§3 S4 fixture 成員表改成四類（標準 2／特製 11／UNKNOWN 1／無 `tool_build` 1）＋斷言分開列。
3. §0b token 基準補繁簡兩種寫法（「空白模组」9／「空白模组剑合成」1／「空白框架」2）＋§0 症狀 B 引文改回真 trace 繁體「框架合成卡已隱藏（非本工具取得途徑）」＋§3 S2「今日紅」寫準（已出現木棍／石刀／切石机，紅係缺「空白模組劍合成」標籤＋含禁用句）。
4. §3 S10 改成 harness 級可機檢斷言（並誠實列明真機層今日冇正控樣本）＋§2.6 補「同一句禁令要 scoping 去特製版」嘅落地提醒（R3 觀察，非 blocker）。

## §2.10（SK 2026-09-17 19:0x 追加，硬性約束）語言無關
- **唔准變成中文限定；目標係所有語言可用。** 具體要求：
  1. 判定器／答案閘**零自然語言 literal**（唔准 CJK，亦唔准英文字串做語意判斷）；判定只靠 NBT 部件集合。
  2. 所有面向玩家文字嚟自 lang key（`i18n/ReplyLang`，逐當前語言取、缺 fallback `en_us`）。
  3. 現時只有 zh_cn／en_us／zh_tw 三個語檔係**現狀**，唔係限制 → 其他語言靠 `en_us` fallback 亦必須正常。
  4. 新增／改字串**三語同步**（3 檔 × 同 key），`en_us` 必須齊（fallback 源）。
  5. **新閘**：`tests/check_modular_frame_standard.py` 加一條「白名單內新／改 Java 檔唔准有 CJK 字串 literal」靜態檢查。
- 驗收追加：**S11** — 上述靜態檢查綠；另人手核 `ReplyLang` 取值路徑對「唔存在嘅語言」會 fallback（寫明係靠 MC 標準行為，唔另寫 fallback）。


## §8 真機驗收 1（19:24）失敗 → 真因更正（19:3x）
- 真機 19:24 石刻（`sword/hilt: sword/basic_hilt`＋`sword/blade: sword/stonecutter`）＝ **標準框架**；判定器**正確**（log 冇插入）。
- 但**模型自己**答成「本包索引未收录…它属于自行组装出来的定制版本（刀刃与柄不对应标准空白剑框架产出）…不能当作取得方式」⇒ **係新政策文字被誤用**：模型對標準框架都套用「特製版→未收錄」。
- 對照 19:25 真特製版（`golden_age` 零件）→ 判定器 MODIFIED ＋ 強制插入 miss 句 → 答案正確 ✓（原本要修嘅 case 已成功）。
- **修正 1（已實作，19:3x）**：① `ModularFrameStandard` 加 `Match(kind, recipeIndex)`＋`FrameRecipe`（parts／材料 id／產出 id），4 組標準框架（stonecutter／earthpiercer／oak-hammer／toolbelt）；② `HonestMiss.ensureFrameStandardRecipeVisible`（post-LLM 強制插入，新 lang key `packai.reply.frame_standard_recipe`，材料／產出名字走 MC 語言 API）；③ `AskEngine` STANDARD＋recipeIndex → 插入＋log `frame-standard: recipe line inserted`；④ 3 語檔（9 處）分清 STANDARD（**必須**講合成台配方、肯定句）vs 特製版（未收錄），明文禁止對標準框架講未收錄／定制版本；⑤ harness 加 `fix1StandardRecipeInsert`。
- **我親驗**：compile BUILD SUCCESSFUL；harness 8 斷言全 OK（cost 9ms）；`tests/check_*.py` **119 綠／0 紅**；改動全在白名單。build `packai-0.2.3.jar`（19:39）→ **部署等 SK 完全關遊戲**（19:39 被拒：GAME pid 37484 跑住）。

## §9 修正 4（19:5x-20:4x 真機三連敗後，依業界做法）— 程式接管「取得」段
### 真因（硬證）
- `display.body.final` 已含插入句「合成台（无序合成）：切石器 + 木棍 -> modular sword。这是空白模组框架合成版本。」（log `frame-standard: final check present=true`）→ **插入成功**。
- 但同一 body 仍有模型自寫嘅「本包索引未列出这把实例的合成卡，也查不到掉落、交易或任务取得记录…所以没有可断言的确切取得步骤」→ **自相矛盾**（SK 判「唔 work」）。
- 產出名字顯示 "modular sword"（未本地化）；插入句排喺最尾（唔喺「怎麼來」段）。
### 業界依據（已搜、有 source）
1. 「模型講查唔到＝只代表佢手上冇」；標準修法＝**由程式提供確定性缺席證明**（Towards Data Science, 10 Common RAG Mistakes）。
2. NeMo Guardrails **output rail**：答案出街前用已取證據核，唔過就**改寫／取代**，唔係只加一句（docs.nvidia.com/nemo/guardrails fact-checking）。
3. TruLens RAG triad：需要 **groundedness** 檢查（我哋現缺）。
4. MC 側：配方隱藏係包／mod 既有機制（CraftTweaker、JEI #1209）→ **唔可以**由「卡隱藏」推論「冇取得方式」。
### 要做（只改 `logic/AskEngine.java` ＋ 需要時 `logic/HonestMiss.java`／`logic/ModularFrameStandard.java`；唔准碰其他）
1. **標準框架 → 程式接管「取得」段**：判定 STANDARD ＋ 配對到配方時，**用配方句取代** body 內「怎麼來」段嘅**模型內容**（唔可以留「查不到／未列出／沒有可斷言／未收錄」一類句子）。做法：先移除該段內屬否定句嘅行（用現成 `AskJeiHints`／`AskMissFallback` 已有嘅缺料句型偵測，唔好新寫第二套），再喺段內插入配方句。
2. **本地化**：材料／產出名字一律由 item stack display name 取（當前語言）；**唔准**顯示 id 名（今日 "modular sword" 係 bug）。
3. **Groundedness 閘（新）**：`tests/check_*` 加一條靜態／harness 檢查 —— **同一 body 唔准同時**有「配方句」與「否定句清單任一句」。
4. **失敗要老實**：配方查唔到（STANDARD 但無 recipeIndex）→ 保持現行行為，唔准亂講。
### 驗收（機檢）
- harness 新案例：餵「含否定句嘅 怎麼來 段」＋STANDARD 判定 → 斷言輸出 ① 含配方句 ② **唔含**否定句清單任一句 ③ 名稱係 display name（用遊戲語言 API 結果）。
- 反向（MODIFIED）：餵真特製版 trace 樣本 → 仍然係 miss 句、**唔准**插入配方句。
- 現有 `tests/check_*.py` 119 綠／0 紅；compile OK；白名單內。
- 真機：手持石刻問一次 → 「怎麼來」段只講合成台配方（無「查不到」）；再問真特製版（亞巴頓）→ 仍然「未收錄」。
### 風險／還原
- 風險：刪行邏輯可能刪多（誤刪真用途句）→ harness 要有「非否定句必須保留」案例；改動集中一個檔，還原＝`git checkout` 該檔或用 `.hermes/backups/2026-09-17_stage1_honest_miss/` 備份（⚠️ 唔准用 git checkout 全樹，工作樹有 112 未 commit 改動）。

## §10 方案 1（SK 2026-09-17 20:5x 拍板）：標準框架唔再抑制配方卡（改資料源，唔改答案文字）
### 理據
- 真機 log 已證：`renderCards … foundOutput=2`（卡**搵到**）→ 係我哋政策（`frameCardsSuppressed n=2`）**主動丟棄**；模型見到「卡被隱藏」就自行推論「冇取得方式」＝今日錯答案根源。
- SK 今早原話：呢類工具嘅合成台配方**就係**佢嘅合成方法 → 標準框架出卡政策一致。
- 本節 **supersede**：§2.7「卡抑制政策本階段唔郁」、`docs/plans/2026-09-15_card-attribution-and-suppression.md` 舊基線（`suppressedFrameOnly n=2／cards emitted=0` 作廢，新基線＝標準框架 `cardsOut>=1`）。
### 行為
- STANDARD（部件對得上 jar 標準配方，判定器已有）→ **唔抑制**，正常出配方卡。
- MODIFIED（特製版）→ **維持抑制**（現行行為不變）＋走 miss 句出口。
- UNKNOWN（缺件／解析失敗）→ **維持抑制（fail-safe，唔准因唔確定而出錯卡）**。
### 落點（依 R7/R8/R9 已核 anchor）
1. 抑制決策：`logic/ModularFrameCards.shouldDropFrameCard(String,String,boolean,boolean)`（真呼叫點 `client/service/AskService.java:~2654`、`logic/AskToolEnv.java:~90-91`）；`AskService.suppressModularFrameCards`（`:~2643`）＋`AskService:~2636 modularFrameDropId(ItemStack)`／`isModularToolFocus` → `AskLoopState.setModularFrameDropId`（`:524-546`）→ `AskEngine:~1616-1617 env.modularFrameDropId`；`AskService:~408`／`~2487` 入 `AskCardFallback.ensureCards`。
   - 做法：**由 caller 先問判定結果**（`ModularFrameStandard`，用 focus 嘅 `[TOOL_BUILD]`／`ItemStack` NBT）→ STANDARD 就**唔**呼叫 suppress；MODIFIED／UNKNOWN 照舊。唔准改 `shouldDropFrameCard` 簽名以外嘅語意。
2. 文字：`logic/RenderRecipeCardsAskTool.java:~170-172` 現有 log／字串「框架合成卡已隱藏（非本工具取得途徑）」→ 標準框架分支唔應該再講「隱藏」；新增／調整 lang key（**三語同步**，缺 fallback `en_us`）。
3. 提示詞：`assets/packai/lang/{zh_cn,en_us,zh_tw}.json` `tool_build`／`llm_style` 內「唔准當取得途徑／禁止当成这把」一類句**只可以約束特製版**；標準框架要明文要求照講配方（並保留現成閘 token：`空白模組`／`空白模组`／`empty-frame`／`禁止当成这把`／`[TOOL_BUILD]`）。
4. 受影響既有測試（要一併更新，唔准放寬）：`src/test/java/.../ModularFrameCardsCheck.java`（10 個 4-arg 呼叫點）、`tests/check_card_emission_suppression.py`（`:92-94/105-107/110-111/126-127`）、`tests/check_ask_card_fallback.py`（`:1367-1375`）、`tests/check_maintenance_intent.py`（`:72/77`）、`tests/check_reply_prompt_keys.py`（`:376-392`）。
### 驗收（機檢優先）
- S-a：`ModularFrameCardsCheck` 新 case：STANDARD → 唔 drop（false）；MODIFIED → drop（true）；UNKNOWN → drop（true）。
- S-b：真機手持石刻（STANDARD）→ `render.cards.final cardsOut>=1` 且答案**唔含**「卡已隱藏／不能當作取得途徑」；真特製版（亞巴頓）→ 仍 `cardsOut=0` ＋ miss 句。
- S-c：`tests/check_*.py` **119 綠／0 紅**（含更新後嘅 4 條相關檢查）；compile OK；改動全在白名單。
- S-d：唔准為變綠而刪／放寬既有 test 語意（有 supersede 嘅要明寫）。
### 白名單
`logic/ModularFrameCards.java`、`client/service/AskService.java`、`logic/AskToolEnv.java`、`logic/ModularFrameStandard.java`、`logic/RenderRecipeCardsAskTool.java`、`logic/AskEngine.java`（如需）、`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`、`src/test/java/.../ModularFrameCardsCheck.java`、`tests/check_card_emission_suppression.py`、`tests/check_ask_card_fallback.py`、`tests/check_maintenance_intent.py`、`tests/check_reply_prompt_keys.py`。
### 風險／還原
- 最壞：出咗**錯卡**（唔係呢件工具嘅途徑）→ 由 S-a/S-b 擋；UNKNOWN 一律保守抑制。
- 還原：`.hermes/backups/2026-09-17_stage1_honest_miss/`（已存 md5）＋只改白名單檔；⛔ 唔准 `git checkout` 全樹（工作樹 112 個未 commit 改動）。

## §11（SK 2026-09-17 21:0x）：方案 4 ＝ 做成**玩家可選設定**（答案核對 rail）
### 定位
- 方案 1（§10）＝改資料源；本節＝**額外保險**，成本（多一次 AI 呼叫）由**玩家自己決定開唔開**。
- **預設關**（省 token／延遲）；設定頁可開，開啟後每次問答多一次輕量核對呼叫。
### 設定項（新）
- `config/PackAiConfig.java`（CLIENT）：新增 bool，例 `answerVerifyEnabled`（預設 `false`）。
  - ⚠️ 專案 gotcha：setter 尾必須 `SPEC.save()`；settings registry 加 key **要 3 檔 lang**（en_us／zh_cn／zh_tw，缺 fallback `en_us`）＋`tests/check_settings_registry.py` 綠。
  - 設定頁文案 3 語同步（label＋tooltip 各一條）；玩家可見文字**唔准**含 file:line／內部代號。
- 相關落點：`client/gui/settings/*`（新 key 註冊）、`config/PackAiConfig.java`、lang 3 檔。
### 行為（開啟時）
1. 喺 post-LLM 之前／之中加一次**核對呼叫**：輸入＝（已收集事實文字 ＋ 候選答案），要求回「有冇與事實矛盾嘅句子；如有回改寫後全文」。
2. 有矛盾 → 用改寫版（log `answer-verify: replaced (conflict)`）；冇 → 原答案（log `answer-verify: ok`）。
3. 呼叫失敗／超時／解析失敗 → **fail-open**（用原答案）＋log，**唔准**因此令答案消失。
4. 只准加 `LOGGER` 行，**唔准**加新 trace event／欄位。
5. 為咗唔浪費：只在「問句屬取得／配方類」且「有相關事實」時跑（沿用現成 intent 判定）。
### 驗收（機檢）
- V-a（關）：`answerVerifyEnabled=false` → 斷言**零額外 LLM 呼叫**（用 mock／計數），行為同今日一致。
- V-b（開）：mock 回「有矛盾」→ 答案被改寫版取代；mock 回「無矛盾」→ 答案不變；mock 拋錯 → fail-open（原答案保留）。
- V-c（成本透明）：log 記 `answer-verify tokens=…`；設定頁 tooltip 寫明「會多一次 AI 呼叫（約多 X token）」。
- V-d：`tests/check_settings_registry.py` 綠；`tests/check_*.py` **119 綠／0 紅**（含新檢查）；三語齊；compile OK；改動全在白名單。
### 白名單（本節追加）
`config/PackAiConfig.java`、`client/gui/settings/*`（新 key）、`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`、新增核對元件（放 `client/service/`，例 `AnswerVerify.java`）、`tests/check_settings_registry.py`（如需）、新增 `tests/` 檢查。
### 風險／還原
- 風險：多一次呼叫＝成本／延遲；玩家預設關 → 影響面零；開啟時 fail-open 保證唔會冇答案。
- 還原：同 §10（備份目錄＋白名單；⛔ 唔准 `git checkout` 全樹）。
- 註：§11 係新加，**未經反方 review**（§10 嗰輪已開跑）→ 開工前要補一輪。

## §10.1 §10 修正（依 F-R-opt1 4:6；逐條對反方 flip conditions）
1. **單一判定載體（唔准逐位問判定）**：只在 ask 起點問**一次**判定（`client/service/AskService.java` 決定 `modularFrameDropId` 嘅位，`:2636-2641 modularFrameDropId(ItemStack)`／`isModularToolFocus:2568`）→ STANDARD ⇒ `AskLoopState.setModularFrameDropId("")`（清空，令抑制自然失效）；MODIFIED／UNKNOWN ⇒ 維持現值。
   - 下游**只准讀**載體，唔准自己再問判定（避免兩閘分叉）：`suppressModularFrameCards` **6 個真呼叫點 `179／397／418／2324／2479／2495`** ＋ `logic/AskToolEnv.java:90-91`（`rejectFrameCard`）＋ `AskLoopState.shouldSkipAutoEmit`（`:541-546`）。
   - **anchor 更正**：`AskEngine` 真 bind 點係 `:370`（drain）／`:854`（LLM）／`:1664-1665`（`bindAskToolEnv`）；原寫 `:1616-1617` **錯**（該處係 `withToolBuildHowToGet` 迴圈，無關）。全文 anchor 已重核，刪「已核」字樣。
2. **卡內容斷言**：S-b 除 `cardsOut>=1`，要斷言卡嘅 **category／station 對得上合成台**（唔可以出機器卡頂替）。
3. **第三條抑制源明寫「本輪唔修」**：`logic/AskCardFallback.java:448`（`collectOutputQuestIndices` → `isFocusFrameOutput:455`）＝KEYWORDS／ensureCards 路徑，§10/§10.1 **不改**；驗收明確**唔覆蓋**該路徑（誠實列明）。
4. **測試清單補全**：受影響／白名單加入 `tests/check_modular_frame_standard.py`；`tests/check_card_emission_suppression.py:111` 需寫明 supersede 措辭（舊基線作廢）。
5. **UNKNOWN 明文 supersede**：UNKNOWN ⇒ 維持抑制 ＋ **唔行 miss 句**；並明文 supersede §7 撤回表相關列（唔准兩處並存）。
6. 其餘（STANDARD 出卡、MODIFIED 抑制、fail-safe）不變。

## §10.2 §10 第 3 版（依 F-R-opt1b 嘅 3 條「收貨最低條件」；推翻舊方案）
### 錯咗嘅（撤回，唔准再寫）
- ❌ 「喺 ask 起點清空 `modularFrameDropId` 就能同時關掉 6 個抑制出口」＝**假**：6 個 `suppressModularFrameCards` 呼叫點（`179／397／418／2324／2479／2495`）**唔讀載體**，各自喺 `AskService.java:2647` 用 `modularFrameDropId(focus)` **由 ItemStack 重新推導**。
- ❌ 時序亦唔成立：最早抑制 `179／2324` 發生喺 `:276`（`mergeExtrasToolBuild`）／`:2399` **之前**；真機 log 硬證：`frameCardsSuppressed …n=2` 喺 **20:32:53.403**，而 `frame-classify: kind=STANDARD` 喺 **20:32:55.389**（遲 2 秒）。⇒ 舊方案照字面做係 **no-op**（`AskToolEnv` 放卡後 `:397` 會再殺一次，`cardsOut` 仍 0）。
### 新方案（三條）
1. **唯一閘＝`AskService.modularFrameDropId(ItemStack):2636-2641`**：STANDARD ⇒ 回 `null`（等於「冇卡要丟」）；MODIFIED／UNKNOWN ⇒ 照現行回非空。**所有**下游（6 個呼叫點＋`AskToolEnv:90-91`＋`AskCardFallback` 路徑）**一律唔改判定邏輯**，佢哋自然跟隨呢個唯一來源。刪除「6 點讀載體」句。
2. **判定必須提早**：要早於 `:179`／`:2324`（亦即 `:276`／`:2399` 之前或同位）。做法：喺該位**由 ItemStack NBT** 走現成 `ModularFrameStandard.partsFromFlatStrings(Map)` ＋同一組 4 個標準 part-map（純函數，零 I/O）。加**一致性閘**：真實石刻樣本兩條路徑（`[TOOL_BUILD]` 文字 vs ItemStack NBT）必須**同判 STANDARD**（防兩套判定分叉）。
3. **`AskCardFallback` 路徑必須一併處理**：同一 dropId 載體 ⇒ STANDARD 時 `logic/AskCardFallback.java:448`（`isFocusFrameOutput:455`）**必然改變行為** → 唔可以寫「本輪唔修」；要**一併驗**（或明文寫明接受咩行為改變，並列入驗收）。
### 其餘收口
4. **UNKNOWN supersede 改指**：§3 **S8** ＋ §1 第 37 行（並改 S8 措辭）；UNKNOWN ⇒ 維持抑制 ＋ **唔行 miss 句**。
5. **新靜態閘**：STANDARD ⇒ `AskService` **唔准**傳非空 dropId（否則 S-a 冇鑑別力）。
6. 保留：STANDARD 出卡（卡內容要對得上**合成台** category／station）；MODIFIED ⇒ 抑制 ＋ miss 句；fail-safe（例外／缺件 ⇒ UNKNOWN ⇒ 保守抑制）。
### 白名單（更新）
`logic/ModularFrameCards.java`、`client/service/AskService.java`、`logic/AskToolEnv.java`、`logic/AskCardFallback.java`、`logic/ModularFrameStandard.java`、`logic/RenderRecipeCardsAskTool.java`、`logic/AskLoopState.java`（如需）、`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`、`src/test/java/.../ModularFrameCardsCheck.java`、`tests/check_card_emission_suppression.py`、`tests/check_ask_card_fallback.py`、`tests/check_maintenance_intent.py`、`tests/check_reply_prompt_keys.py`、`tests/check_modular_frame_standard.py`。

## §10.3 §10 第 4 版（依 F-R-opt1c 4:6；撤回「回 null」，改逐卡判）
### 被證偽嘅（撤回）
- ❌ **「STANDARD ⇒ `modularFrameDropId` 回 null」唔夠**（硬證）：真機 focus `tetra:modular_sword` 有 **2 張** output 卡（trace line 46-48／44-47）—— category `Crafting` 同 `自动搅拌 · 动力搅拌器`，**兩張 `primaryOutputId` 都係同一件、`outputsSize=1`**；`RenderRecipeCardsAskTool.java:320-351 filterRole` 只按 promptRole 過濾、**唔按 station** ⇒ 回 null ＝ **兩張一齊放行**（連機器台卡）＝違反本計畫自己嘅驗收（§10.2 第 6 點／S-b「唔可以出機器卡頂替」）。
### 新機制（逐卡判，唔係一刀切）
1. **STANDARD ⇒ 只丟「唔係合成台」嘅 output 卡**（例如 `自动搅拌 · 动力搅拌器` 等機器台類別），**合成台（Crafting）卡放行**；MODIFIED／UNKNOWN ⇒ 照現行**全丟**。
   - 落點：`client/service/AskService.java:2643-2665 suppressModularFrameCards`（`:2647` 已由 focus 推導）＋同源的 `AskToolEnv.java:90-91`、`AskCardFallback.java:448/455`（一律跟同一結果，唔准分叉）。
   - 站別判斷**要用結構化資料**（category／station 字串比對白名單），**唔准**用自然語言 hardcode；若必須入 `logic/RenderRecipeCardsAskTool.java:320-351`，要喺白名單明列並解釋。
2. **判定入口（唔准第三套推導）**：喺 `logic/ModularFrameStandard.java`（**已喺 `com.skps9.packai.logic`，同 package**）新增 public `classifyStack(ItemStack)` → 內部直接叫現成 **package-private** `ModularToolScan.flatten/fromTag`（`:118/:148`）→ `partsFromFlatStrings` → 同一組 4 個標準 part-map。**唔准**喺 `AskService` 重寫 NBT flatten，亦**唔准**用要 `enrich()` 嘅 `scan()`（會多 I/O）。
3. **空 NBT／解析失敗要寫死**：`tetra:modular_sword` 冇 NBT ⇒ `[TOOL_BUILD]` 係 `this NBT not parsed`（`ModularToolScan.java:44-49`）→ **UNKNOWN**（保守全丟）；NBT 路徑空 Map ⇒ **UNKNOWN**。真機已見 `kind=UNKNOWN parts={}`（20:36:09）⇒ fixture 要加**空 NBT case**。
4. **S-a 作廢並改寫**：`ModularFrameCardsCheck` 係 headless 純 id 表（連 `ItemStack` 都冇）＋`shouldDropFrameCard` **冇 Kind 概念** ⇒ 舊 S-a 係 tautology（MODIFIED 都會 pass）。改成：① 純函數測試覆蓋空 NBT／空 Map ⇒ UNKNOWN；② Python 靜態閘：`AskService` 喺 STANDARD 分支**唔准**傳非空 dropId 兼**唔准**全放行（要見站別過濾）；③ 真機斷言：STANDARD ⇒ 正常出卡且**卡類別＝合成台**；MODIFIED／UNKNOWN ⇒ `cardsOut=0`。
5. **`AskCardFallback` 路徑要可機檢**（刪「或」）：`tests/check_ask_card_fallback.py` 加 case —— STANDARD ⇒ 正文卡**唔准**出現 output==focus 嘅**機器台**卡。
6. **supersede 收口**：§1 第 40 行（「卡抑制政策本階段唔郁」）明文 supersede；§2.7 已 supersede；**§9 保留**並寫清分工 —— §10 管「卡／資料源」，§9 管「模型否定句」（兩者都要，缺一唔得）；§3 S8 措辭改寫（UNKNOWN ⇒ 維持抑制＋唔行 miss 句）。

## §10.4 §10 第 5 版（依 F-R-opt1d 4:6）— 站別判準寫死為「結構化 recipe category UID」
### 被指出嘅未定案（必須寫死）
- `RecipeCard`（`client/jei/…:20-62`）**冇** `categoryUid`／`station` accessor；唯一相關係 `categoryTitle`（String），而佢係 `JeiRecipeCards.titleWithMachine:1159-1181` 拼出嚟（base ＋ ` · ` ＋ 機械名）→ **跟語言變**（真機 zh_cn 例：`Crafting`／`自动搅拌 · 动力搅拌器`）⇒ **唔准**用標題字串做判準。
- 亦唔准用 `CraftPriority.isCoreCraftCategory(String)`（現成但係字串法，違反本計畫「結構化、語言無關」原則）。
### 寫死嘅判準（結構化）
- **主判準**：由卡片攞 **recipe category UID**：`card.jeiLayout()` → `IRecipeLayoutDrawable.getRecipeCategory().getRecipeType().getUid()`（先例：`JeiLayoutDraw:239/376/414`、`JeiCategoryCatalog.categoryUid:122`、`VANILLA_ANVIL_UID:32`）。
- **合成台白名單**：`minecraft:crafting`（＋如適用 `minecraft:crafting_shapeless` 等同 UID 家族；實作時以真 UID 為準，唔准靠標題文字）。
- **null／取唔到 UID 政策（寫死）**：任何 null／例外 ⇒ **當「非合成台」⇒ 照丟（保守）**，並 log `frame-card: category uid unavailable → dropped`（只加 log，唔加 trace event）。
- 逐卡判：STANDARD ＋ UID ∈ 合成台白名單 ⇒ **保留**；其餘（含 null、機器台）⇒ 丟。MODIFIED／UNKNOWN ⇒ 照現行**全丟**。
### 白名單（更新）
新增（只讀／如需）：`client/jei/JeiLayoutDraw.java`（如需 accessor）、`logic/JeiCategoryCatalog.java`（如需 UID 常數）；其餘同 §10.3。**唔准**改 JEI 上游。
### 驗收補充
- 真機斷言改為：STANDARD ⇒ `cardsOut>=1` 且**保留卡嘅 recipe category UID == `minecraft:crafting`**；MODIFIED／UNKNOWN ⇒ `cardsOut=0`。
- 加負控：模擬 `jeiLayout()==null` ⇒ 該卡被丟（唔准例外當保留）。

## §10.5（SK 2026-09-17 21:3x 拍板，**取代** §10–§10.4 嘅「丟卡／篩站別」機制）
### SK 原話
「**資料照發送給AI，然後特別提醒兩者的 nbt 是否相同**」
### 新機制（簡單、唔再篩卡）
1. **唔再隱藏**：框架相關配方卡**照發**（catalog-collect 內容不變，撤回全部「丟卡／篩站別／UID 判準」設計 —— §10／§10.1／§10.2／§10.3／§10.4 一律**作廢**，唔准照抄）。
2. **逐卡加一句結構化標註**（focus 物品相關嘅 output 卡）：
   - `/same/`：卡嘅產出部件**同你手上呢件相同** ⇒ 標準框架（**呢張就係**佢嘅合成方法）。
   - `/different/`：**唔相同** ⇒ 特製版（呢張係空白框架版本，**唔係**呢件嘅取得途徑）。
   - `/unknown/`：判定唔到 ⇒ 明文叫 AI 老實講「唔確定」，**唔准**自行當途徑亦唔准否定。
3. **判定來源（唯一）**：現成 `logic/ModularFrameStandard`（STANDARD／MODIFIED／UNKNOWN＝純部件集合比對）。**唔准**新增語言字串比對、唔准第三套推導。空 NBT／解析失敗 ⇒ `/unknown/`（唔係 MODIFIED）。
4. **文字**：標註行同玩家答案要求一律由 **lang key** 提供（三語同步、缺 fallback `en_us`）；Java 決策邏輯零自然語言 literal。
5. **提示詞**：明確指示 AI —— 見到 `/same/` ⇒ 照講合成台配方（肯定句）；`/different/` ⇒ 老實講「未收錄呢個版本嘅取得途徑」＋最多一句「似同任務內容有關」；`/unknown/` ⇒ 講唔確定。**唔准**自行推論「定制版＝冇取得方式」。
### 驗收（機檢優先）
- V1：真機手持**石刻**（STANDARD）⇒ 答案含「合成台／無序合成」＋材料名，且**唔含**「查不到／不能當作取得途徑」；卡照出（`cardsOut>=1`，**唔再**要求篩走機器卡）。
- V2：真機手持**真特製版**（亞巴頓）⇒ 答案講「未收錄／空白版本唔係途徑」。
- V3：harness 覆蓋三態（`/same/`／`/different/`／`/unknown/`），含**空 NBT ⇒ unknown** 新 case。
- V4：`tests/check_*.py` 119 綠／0 紅；compile OK；白名單內；三語齊。
### 白名單（縮細）
`logic/ModularFrameStandard.java`、`logic/AskJeiHints.java`（如需標註注入）、`client/service/AskService.java`、`logic/ModularFrameCards.java`（如要取消抑制）、`logic/AskToolEnv.java`（如需）、`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`、`src/test/java/.../ModularFrameStandardCheck.java`、`tests/check_modular_frame_standard.py`。

### §10.5 補充（SK 2026-09-17 21:3x 再拍板）— 標註**只入 AI prompt，唔准出畀玩家**
- `/same/`／`/different/`／`/unknown/` 標註屬**內部事實行**（同 `[TOOL_BUILD]` 同類）→ **只可以**出現在送畀模型嘅 prompt／facts 內。
- **嚴禁**出現在：玩家答案正文、UI 面板、tooltip、卡片文字、log 以外任何玩家可見位置。
- **驗收加一條（機檢）**：`tests/check_ask_display_leak.py` 嘅禁止 token 清單要加入新標註 token（例如 `FRAME_MATCH`／`/same/`／`/different/`／`/unknown/` 嘅實際字樣），並跑 `--trace <instance>/packai/trace --since 20260917` RC=0；真機 smoke：問石刻／特製版各一次，答案文字**唔准**含任何標註 token。
- 玩家最終只會見到自然語言答案（same ⇒ 合成台配方；different ⇒ 未收錄；unknown ⇒ 唔確定）。
