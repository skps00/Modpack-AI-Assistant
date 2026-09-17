# Plan A（2026-09-18）— Tetra 標準框架：答案用肯定句 + 合成資料要真確（v2）

> 範圍：**只做「標準框架嘅『怎麼來』段唔再講否定句」**（SK 2026-09-18 揀 `a`：拆細）。
> **唔碰卡抑制政策**（§10 另案）；**唔碰** `render_recipe_cards` 文案、MODIFIED／UNKNOWN 行為、`neoforge` 樹、trace 事件格式。
> 前置：`docs/plans/2026-09-17-nbt-stage1-honest-miss.md`（v3.2 過閘 8:2、已實作、已部署 `packai-0.2.3`）。
> v2 修正清單見 §7（對應 A-R1 反方 10 條洞）。

---

## §0 真機證據（2026-09-17 20:32–20:36，jar `packai-0.2.3` sha `bcceb19fc4fc`）

### 0.1 STANDARD 個案（石刻／切石器）＝ `ask-20260917-203249-tetra_modular_sword.jsonl`（80 條 record）
- `[21] tool.result tool_build`：`part sword/hilt: sword/basic_hilt material basic_hilt/stick` ／ `part sword/blade: sword/stonecutter material stonecutter/stonecutter`。
- `[44] render.cards`：`scannedCats=2 foundOutput=2 afterFilter=2 primaryOutputId=tetra:modular_sword`；`[45]`＝`Crafting`、`[46]`＝`自动搅拌 · 动力搅拌器`（同一輸出兩張卡）。
- `[47] tool.result render_recipe_cards` 回 `框架合成卡已隱藏（非本工具取得途徑）`。
- `[74] model.reply.final`：模型自己寫「**本包索引未列出这把实例的合成卡，也查不到掉落、交易或任务取得记录（框架合成卡对本工具被隐藏），所以没有可断言的确切取得步骤**」。
- `[77] display.body.final`（＝玩家真正睇到嘅字）：上面嗰段**原封不動**；程式插嘅配方句位於 **`补充` 之後、【来源】之前**（`HonestMiss.insertLineBeforeSources`：插喺 `ReplySources.HEADER` 前）——**唔係「句尾」**：
  `合成台（无序合成）：切石器 + 木棍 -> modular sword。这是空白模组框架合成版本。`
- 對應 log（**出處＝`logs/2026-09-17-1.log.gz` ＋ `logs/debug.log`，唔係今日嘅 `latest.log`**；今日 `latest.log` 零 Pack AI 行）：`[17Sep2026 20:33:29.165] … frame-standard: recipe line inserted (STANDARD frame)` ＋ `… final check present=true`。
- ⇒ 兩個結論：① fix3「最後插入」**成功**；② 玩家睇到**自相矛盾**（上面「冇可斷言嘅取得步驟」、下面又講合成台配方）＝本 plan 要修嘅嘢。

### 0.2 對照組（唔准郁）
- MODIFIED（拟态，`ask-20260917-203349-tetra_modular_sword.jsonl`，**60 條 record**）：`[54] model.reply.final` 老實講未收錄；`[57] display.body.final` 帶 `目前沒有這件物品的取得資料，暫時不確定怎麼拿到。`（`packai.reply.ask_miss_acquire_player`）→ **行為正確，唔改**。
- UNKNOWN（`create:schematic` 藍圖，`ask-20260917-203601`）：照舊「無法斷言」→ **唔改**。

### 0.3 現行配方句本身有真錯（同一句，一齊修）
`mods/tetra-1.19.2-5.6.0.jar` → `data/tetra/recipes/**`：**26 個 json、13 個產出 `tetra:modular_*`**：

| 產出 | jar recipe | type | 變體數 | 材料（representative） |
|---|---|---|---|---|
| `tetra:modular_sword` | `stonecutter.json` | **crafting_shapeless** | 1 | `tetra:stonecutter` + `minecraft:stick` |
| `tetra:modular_single` | `earthpiercer.json` | **crafting_shaped** | 1 | `tetra:earthpiercer` + `minecraft:stick` |
| `tetra:modular_double` | `hammer/{acacia,andesite,birch,dark_oak,diorite,granite,jungle,oak,spruce,stone}.json` | **crafting_shaped** | **10** | `#`＝各變體材料（6 木板＋4 石料）、`/`＝tag `forge:rods/wooden` |
| `tetra:modular_toolbelt` | `toolbelt_modular.json` | **crafting_shaped** | 1 | `minecraft:string`（pattern ` # `／`# #`／` # `） |

⇒ 現行 `packai.reply.frame_standard_recipe` 逐字寫死「**無序合成**」→ 對 **12/13 條 recipe 都係錯**。

### 0.4 結果物品名（已知，唔阻塞）
`tetra:modular_sword` 喺 tetra jar **冇** `item.tetra.modular_sword`（只有 `tetra.holo.craft.modular_sword`＝刀劍）；pack 內亦冇 override → `OfficialDisplay.officialName()` 空 → `Plainify.displayName()` path-token fallback（真機顯示 `modular sword`）。本 plan **維持現有顯示解析**，只要求「唔准出現裸 `ns:id`」。

---

## §1 目標（收窄到一段）

1. **只有「怎麼來」（取得方式）段**：STANDARD 框架嘅最終玩家可見文字，該段內文＝**程式決定性產生嘅配方句**，並且**唔准**再出現「未收錄／查不到／无法断言／不能當取得途徑」呢類**關於取得方式**嘅否定句。
2. **嗰句資料要真確**：無序／有序由 jar 真資料決定；同族多變體（錘系 10 種）要講明「多版本」。
3. **其他段唔喺本 plan 範圍**（例：`怎麼用` 段「本地资料未列出它作为合成材料」係另一件事實，**唔改**）。
4. MODIFIED／UNKNOWN 行為、卡政策、政策文字 9 處（已完成）**一概唔郁**。

---

## §2 改動（逐檔逐位寫死）

### 2.1 `forge/1.19.2/src/main/java/com/skps9/packai/logic/ModularFrameStandard.java`
- `FrameRecipe`（現 `:34-51`）加 `boolean shapeless`、`int variantCount`（3-arg 便捷 ctor 保留＝`shapeless=false`、`variantCount=1`）。`hasCraftLine()` 語義不變。
- **`:145`（`installExpectedRecipes`）必須 pass-through 兩個新欄位**（`new FrameRecipe(norm, r.ingredientItemIds(), r.resultItemId(), r.shapeless(), r.variantCount())`）——因為運行時走嘅係 `INSTALLED`（`classifyDetailedInstalled`／`recipeAt`／`installedExpectedRecipes`），**漏 pass-through ＝ 運行時永遠 `shapeless=false`（石刻會講錯「有序合成」）而靜態清單測試照綠**。
- `:163`（`installExpectedPartMaps`）／`:218`（`classify(text, maps)`）係 **part-map-only** 路徑（冇 craft metadata，`hasCraftLine()`＝false）→ 保持 3-arg，加註解寫明理由。
- `TETRA_BLANK_FRAMES`（現 `:80-105`）4 條改真值：sword `shapeless=true, variantCount=1`；single `false,1`；double `false,10`；toolbelt `false,1`。
- ⛔ **唔准改** `classify*` 判定行為；**唔准**新增 CJK 字串 literal（S11 gate）。

### 2.2 `logic/HonestMiss.java`
- 新增 `public static String frameStandardRecipeLine(String lang, ModularFrameStandard.FrameRecipe recipe)`：
  - `shapeless==true` → `packai.reply.frame_standard_recipe`；否則 → 新 key `packai.reply.frame_standard_recipe_shaped`。
  - `variantCount>1` → 尾接新 key `packai.reply.frame_standard_recipe_variants`（參數＝數量）。
  - 材料＝現有 `joinItemLabels(...)`、結果＝現有 `itemLabel(...)`（`:176-186`）；任一空 → 回 `""`（fail-open）。
- `ensureFrameStandardRecipeVisible`（`:151-174`）**保留**（append 語義；`:76` 有 python 源碼 assert 佢個名要留喺 `AskEngine`），內部改為呼叫新 helper 取字（單一真相來源）。

### 2.3 `logic/AskReplyScrub.java` — 新增 `replaceHowToGetBody`（**只用現成 anchored pattern，零新 CJK literal，零改動現有文面**）
- **起點**：`HOW_TO_GET_HEAD`（`:78-79`，已有 `(?im)^` anchor）。**唔准改** `EMPTY_HOW_TO_GET`（`:72-76`）文面——`check_reply_structure_scrub.py:144-149`、`check_ask_card_fallback.py:64` 有鏡射。
- **收尾**（全部係**現成 anchored pattern**，取最早者）：`HOW_TO_USE_HEAD`（`:84-85`）、`HOW_TO_UPGRADE_HEAD`（`:88-89`）、`AS_MATERIAL_HEAD`（`:81-82`）、`ReplySources.HEADER`；冇 → 用文末。**唔准**用 `EMPTY_HOW_TO_GET` 嗰個**冇 anchor** 嘅 lookahead（句內「怎麼用」會提早截斷＝A-R1 N2）。
- **被換走嘅範圍**＝由 heading 之後（heading match 已包 `[:：]|\s|\z`，所以**同行內文都算入範圍**）到收尾之前。
- `fill` 空／`answer` 空／**搵唔到 heading** → **原樣返回**（fail-open；由 `AskEngine` 回落 append，見 §2.4）＋由 caller 出 log。
- 尾隨用現成 `tidyNewlines(...)`。
- ⛔ 唔准改 `HOW_TO_GET_LABEL`（`check_howto_get_label_parity.py:56,95,97` 要求 forge／neoforge 逐字一致；neoforge 係 PAUSED 樹唔准改）。

### 2.4 `logic/AskEngine.java`（現 `:967-986` fix3 區塊）
`frameKind == STANDARD && frameMatch.recipeIndex() != null` 時：
1. `String line = HonestMiss.frameStandardRecipeLine(lang, stdRecipe);`
2. 有 line → `String replaced = AskReplyScrub.replaceHowToGetBody(body, line);`
   - `replaced` 同 `body` 唔同 → log **`frame-standard: how-to-get replaced (STANDARD frame)`**
   - 唔同（＝搵唔到 heading，例：語言未列入 heading 清單）→ **回落** `HonestMiss.ensureFrameStandardRecipeVisible(...)`（append，即今日行為，玩家照樣見到配方句）＋ log **`frame-standard: how-to-get heading not found -> appended`**
   - `line` 空 → 一樣回落 append（fail-open）
3. Log 保留：`frame-standard: branch entered recipe=…`（`:971-976`）、**`frame-standard: recipe line inserted (STANDARD frame)`**（`:980`，`tests/check_modular_frame_standard.py:78` pin 住）、**`frame-standard: final check present={}`（`:982-985` 已存在，唔係新增）**。
- ⛔ 唔准改 `:932-943`（MODIFIED honest-miss）、`:944-956`（其他 `ensureHowToGetBody` call site）、`:957-966`；唔准整走 `:978` 嗰句 `ensureFrameStandardRecipeVisible` 呼叫（`tests/check_modular_frame_standard.py:76` 有源碼 assert）。

### 2.5 語言檔（3 檔同步；英文必齊）
- `packai.reply.frame_standard_recipe`（三檔 `:512`）／`frame_standard_recipe_shaped` **新 key** / `frame_standard_recipe_variants` **新 key**：
  - zh_cn：`合成台（无序合成）：%1$s → %2$s。这是空白模组框架合成版本。` ／ 新 shaped：`合成台（有序合成）：%1$s → %2$s。摆放位置请以 JEI 为准。这是空白模组框架合成版本。` ／ 新 variants：`（同族材料版本共 %1$s 种，其他木板／石料版本一样可以合成）`
  - zh_tw：`合成台（無序合成）：%1$s → %2$s。這是空白模組框架合成版本。` ／ `合成台（有序合成）：%1$s → %2$s。擺放位置請以 JEI 為準。這是空白模組框架合成版本。` ／ `（同族材料版本共 %1$s 種，其他木板／石料版本一樣可以合成）`
  - en_us：`Crafting table (shapeless): …` ／ `Crafting table (shaped): %1$s → %2$s. Check JEI for the layout. This is the empty modular / empty-frame craft version.` ／ ` (this frame family has %1$s material variants; other plank/stone versions craft the same frame)`
- ⚠️ 現有三個 shapeless 文案**逐字不改**（A-R1 指出我 v1 寫「改」其實係 no-op）；**新 key 要由新 python check pin 文案**（今日 `check_modular_frame_standard.py:100-107` 只 pin `%1$s`／`%2$s` 存在，frame 文案本身**冇閘**）。
- 三檔 key 數 511 → 513。

### 2.6 明確唔郁
卡抑制政策（8 檔 14 位）／`render_recipe_cards` 文案／`ModularFrameCards`／`PackAiConfig`／MODIFIED・UNKNOWN 分支／trace 事件名同欄位／政策文字 9 處／`AGENTS.md`／`neoforge` 樹／`HOW_TO_GET_LABEL`／`EMPTY_HOW_TO_GET` 文面。

---

## §3 驗收標準（做完逐項跑；predicate 寫死）

### S1 Java harness（新 `forge/1.19.2/src/test/java/com/skps9/packai/logic/FrameStandardRecipeLineCheck.java`；跑法：`python research/gen_tmp_check.py` 重生 `tmp-check.gradle` → `./gradlew.bat -I tmp-check.gradle runFrameStandardRecipeLineCheck`）
- `installedShapelessFlags OK`：**讀 `installedExpectedRecipes()`／`recipeAt(i)`**（＝運行時同一條路徑）斷言 4 條框架 `shapeless`＝`true,false,false,false`、`variantCount`＝`1,1,10,1`。**唔准**只讀 `tetraBlankFrameRecipes()`（靜態清單）。
- `installPassThrough OK`（負控，專捉 N1）：`installExpectedRecipes(List.of(new FrameRecipe(parts, List.of("x"), "y", true, 3)))` → `recipeAt(0).shapeless()==true && recipeAt(0).variantCount()==3`；**還原** `installExpectedRecipes(ModularFrameStandard.tetraBlankFrameRecipes())` 收尾。
- `lineByType OK`：**寫死 key 名**——`tetra:modular_sword` 條 line 必須等於 `ReplyLang.tr("zh_cn","packai.reply.frame_standard_recipe",…,…)`；`tetra:modular_single` 必須等於 `…frame_standard_recipe_shaped…`（**同一輸入用錯 key 時必須唔相等**＝負控），`zh_cn`／`en_us` 兩個語系各跑一次。
- `variantsClause OK`：double 條 line 含 `variantCount`（10）；sword 條 line **唔含**。
- `sectionReplaced OK`：答案＝`ReplyLang.sectionHowToGet(lang)` ＋ 一段「否定」內文 ＋ `ReplyLang.sectionHowToUse(lang)` ＋ `[Sources]`-style footer（**heading 全部由 `ReplyLang` 取，harness 零 CJK literal**）→ 替換後：(i) 該段內文 == line；(ii) `How to use` 段同 footer **逐字不變**。
- `inlineUseHeadNotSplit OK`（A-R1 N2 case 1）：內文含一句「句中有 heading 字樣喺句中位置」（用 `ReplyLang.sectionHowToUse(lang)` 嘅文字砌入句中）→ 替換後**唔可以**殘留後半段文字（斷言整段內文 == line）。
- `sameLineContent OK`（N2 case 2）：heading 同行接住內文（`sectionHowToGet + "：" + 內文`）→ 內文一樣要清走（斷言 body 唔再含該內文）。
- `noHeadingFallback OK`：答案冇 how-to-get heading → `replaceHowToGetBody` 原樣返回（**等於** input），而且（等同 `AskEngine` 路徑）`ensureFrameStandardRecipeVisible` 仍然插到 line。
- `noPartialMarker OK`：被換走嘅段含 `[[item:tetra:modular_sword]]` → 替換後 body 內 `[[` 同 `]]` 數目相等（唔准半截 marker）。
- `stubDiscriminates OK`（鑑別力負控）：用一個「唔做替換」嘅 stub 走同一組斷言 → `sectionReplaced` **必須紅**。

### S2 Python 靜態閘
- 改 `tests/check_modular_frame_standard.py`：新欄位存在；`:145` **有 pass-through**（源碼級 regex 斷言 5-arg）；`frameStandardRecipeLine`／`replaceHowToGetBody` 存在；`ModularFrameStandard.java` CJK=0（維持）。
- 新 `tests/check_frame_standard_recipe_line.py`：pin 3 條新／舊文案（有序／無序分家、variants 句）＋`AskEngine` STANDARD 分支同時有 replace 同 append 回落路徑＋兩條 log 字串＋`HOW_TO_GET_LABEL`／`EMPTY_HOW_TO_GET` 文面**未改**（逐字 pin）＋**唔准出現**用 `EMPTY_HOW_TO_GET` lookahead 做收尾嘅寫法。
- 全量 `for f in tests/check_*.py`：**相對今日 baseline 冇新增紅**。**今日 baseline（09-18 實測，119 檔）＝118 PASS／1 已知紅：`check_ask_display_leak.py` RC=2（NO LOG LINES，需真機 log，非回歸）**。

### S3 真機（SK 出手，我核 trace）
1. 手持**石刻／切石器**問同一問題 → `display.body.final`：(i) 「怎麼來」段內文 **== 插入行**（機械抽段比對，唔用字串黑名單）；(ii) 段內唔准再出現取得類否定句；(iii) `怎麼用`／`【來源】` 逐字仍在。
2. 手持**擬態**（真特製版）→ 仍然老實講未收錄（唔准入配方句）。
3. 手持**木錘**（`tetra:modular_double`，shaped 系）→ 講「**有序合成**」＋版本提示。
4. `python tests/check_ask_display_leak.py --trace <instance>/packai/trace --since 20260918 --min-annotations 0` → RC=0。
5. 回歸（結構式判準，唔靠 LLM 隨機）：同一條**非框架**問題（例 `create:schematic`）嘅 trace **冇** `frame-standard:` 任何 log 行。

### S4 交付前
`compileJava compileTestJava` 0 error；harness 全綠；python 閘無新增紅；更新 `code_change_log.md`＋`.hermes/plans/HANDOFF.md`；**未過真機驗收唔 commit 實作、唔 bump 版本、唔部署**。

---

## §4 白名單 + 還原點

**改（10 條現有）＋新（2 條）＝12 條路徑**：`logic/ModularFrameStandard.java`、`logic/HonestMiss.java`、`logic/AskReplyScrub.java`、`logic/AskEngine.java`、`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`、`tests/check_modular_frame_standard.py`、`tests/check_frame_standard_recipe_line.py`（新）、`src/test/java/com/skps9/packai/logic/FrameStandardRecipeLineCheck.java`（新）、`code_change_log.md`、`.hermes/plans/HANDOFF.md`。
**唔需要改**：`research/gen_tmp_check.py`（佢 `rglob("*Check.java")` 自動生 task；只需**重跑**佢重生 `forge/1.19.2/tmp-check.gradle`，而該檔 gitignored、**唔准 `git add`**）；`ModularFrameStandardCheck.java`（fix1/fix3 斷言用靜態清單自比，仍綠）。

**還原點（動工前已完成）**：`.hermes/backups/2026-09-18_frame_standard_answer/`（4 Java＋3 lang 備份＋`md5.txt`＋`git_status_snapshot.txt`；baseline＝112 項 dirty）；上機 jar `mods/packai-0.2.3+mc1.19.2-forge.jar` sha256 `bcceb19fc4fc`（本 plan 唔部署；要部署時用 `mc_mod_deploy_jar.py --jar <新jar> --name <新名>`，舊 jar 自動 backup 去 `%TEMP%\deploy_backup_*`）。回滾＝備份 copy 返（或 `git restore` 只限白名單檔）＋關遊戲後由 `%TEMP%` copy 返 jar。

---

## §5 風險 / 反轉條件

| 風險 | 偵測 | 應對 |
|---|---|---|
| 新欄位漏 pass-through（運行時永遠 shaped） | S1 `installPassThrough`／`installedShapelessFlags` | 唔准出閘 |
| 段界線提早截斷／同行內文殘留 | S1 `inlineUseHeadNotSplit`／`sameLineContent` | 唔准出閘 |
| 語言未列入 heading 清單 → 該語言照舊矛盾 | S1 `noHeadingFallback`＋`AskEngine` 回落＋log | 玩家仍見到配方句（append）；log 可分辨；**唔准**為此改 `HOW_TO_GET_LABEL`（兩樹一致要求） |
| 替換令段內 `[[item:]]` 被刪、`suggestedItemIds` 仍帶舊 id（`:878` 由 `llmAnswer` 抽） | S1 `noPartialMarker` | 接受（屬既有行為），plan 明寫，唔准偷偷擴大範圍 |
| 真機 3 問仍出現取得類否定句（≥1 次） | S3-1 | **停手問 SK**（唔自己擴去碰卡政策） |
| 新文案撞現有 check | S2 | 改文案，**唔准**改 assert 遷就 |
| 改動超白名單（`git status --porcelain` 對 baseline 112） | S4 | 即刻 revert 該檔 |

**反轉條件**：① 若真機證明取得類否定句唔喺「怎麼來」段（散落其他段）→ 段替換不足，改路線（餵 AI 事實／卡政策另案）；② 若段替換令答案結構明顯變差（SK 睇到唔接受）→ 回落 append；③ 若 `ReplySources.HEADER` 喺某啲答案缺失 → 收尾改為「文末」，並補 S1 case。

---

## §6 反方 review 紀錄（Gate：正方 ≥8 : 反方 ≤2）

| 輪 | 日期 | 比分（正方 : 反方） | 主要修正 |
|---|---|---|---|
| A-R1 | 2026-09-18 | **4 : 6** | §0 三處引述錯；N1 新欄位過唔到 `installExpectedRecipes`（運行時永遠 `shapeless=false`，S1 假綠）；N2 段界線用冇 anchor 嘅 lookahead → 句內／同行殘留；N3 baseline 唔可重現（今日 118／1）；N4 非中英 heading 靜默 fail-open；N5 段內 marker；N7 白名單錯漏；N8 `:76` assert 未列入保留；N9 log／pin 引述錯；N10 S3-5 無判準＋`lineByType` 循環風險＋§1 範圍大過驗收 → 全部寫入 v2（見 §7）|
| A-R2 | 2026-09-18 | 待填 | — |

---

## §7 v2 修正清單（逐條對 A-R1 嘅洞）

| # | A-R1 洞 | v2 落點 |
|---|---|---|
| N1 | 新欄位過唔到 `installExpectedRecipes`；S1 讀靜態清單＝假綠 | §2.1（`:145` 必須 pass-through、`:163/:218` 保持 3-arg 並寫理由）；S1 `installedShapelessFlags`＋`installPassThrough` 負控 |
| N2 | 段界線冇 anchor → 提早截斷／同行殘留 | §2.3（只用現成 anchored pattern 做收尾、heading match 已包同行內文）；S1 `inlineUseHeadNotSplit`＋`sameLineContent` |
| N3 | baseline「119 PASS/0 FAIL」今日唔可重現 | §3 S2 改成「相對今日 baseline 冇新增紅」＋列明已知紅 `check_ask_display_leak.py` RC=2 |
| N4 | 非中英 heading → 靜默 fail-open | §2.4 明確回落 append ＋ 兩條 log（成功／回落）；S1 `noHeadingFallback`；S3-1 改結構式斷言 |
| N5 | 段內 marker 被刪、`suggestedItemIds` 唔同步 | §5 明寫屬既有行為；S1 `noPartialMarker` |
| N6 | §0 三處引述錯（句尾／`[74][77]`／`latest.log`） | §0.1／§0.2 全部更正（插入位置、【來源】之前；log 出處 `logs/2026-09-17-1.log.gz`＋`debug.log`；擬態 trace `[54]`／`[57]`） |
| N7 | 白名單：`gen_tmp_check.py` 唔使改、缺 `code_change_log.md`／HANDOFF、計數錯 | §4 重寫（9 改＋2 新名單） |
| N8 | `:76` 源碼 assert 未列入保留 | §2.4 末明寫保留 `ensureFrameStandardRecipeVisible` 呼叫 |
| N9 | 「另加 final check present」其實已存在；frame 文案被誤指有閘 | §2.4 更正（`:982-985` 已存在）；§2.5 明寫今日**冇**閘、由新 python check 補 |
| N10 | S3-5 無判準；`lineByType` 循環；§1 範圍大過驗收 | §3 S3-5 改結構式（冇 `frame-standard:` log）；S1 `lineByType` 寫死 key＋錯 key 負控；§1 收窄到「怎麼來」段 |
