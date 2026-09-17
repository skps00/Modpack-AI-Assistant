# Plan A（2026-09-18）— Tetra 標準框架：答案用肯定句 + 合成資料要真確

> 範圍：**只做「標準框架唔再講否定句」**（SK 2026-09-18 揀 `a`：拆細）。
> **唔碰卡抑制政策**（§10 卡政策另案）；**唔碰** `render_recipe_cards` 文案、MODIFIED／UNKNOWN 行為、`neoforge` 樹、trace 格式。
> 前置文件：`docs/plans/2026-09-17-nbt-stage1-honest-miss.md`（v3.2 過閘 8:2、已實作、已部署 `packai-0.2.3`）。

---

## §0 真機證據（2026-09-17 20:32–20:36，jar `packai-0.2.3` sha `bcceb19fc4fc`）

### 0.1 STANDARD 個案（石刻／切石器）＝ `ask-20260917-203249-tetra_modular_sword.jsonl`
- `[21] tool.result tool_build`：`part sword/hilt: sword/basic_hilt material basic_hilt/stick` ／ `part sword/blade: sword/stonecutter material stonecutter/stonecutter`。
- `[44] render.cards`：`scannedCats=2 foundOutput=2 afterFilter=2 primaryOutputId=tetra:modular_sword`；`[45]`＝`Crafting`、`[46]`＝`自动搅拌 · 动力搅拌器`（同一輸出兩張卡）。
- `[47] tool.result render_recipe_cards` 回 `框架合成卡已隱藏（非本工具取得途徑）`。
- `[74] model.reply.final`：模型自己寫「**本包索引未列出这把实例的合成卡，也查不到掉落、交易或任务取得记录（框架合成卡对本工具被隐藏），所以没有可断言的确切取得步骤**」。
- `[77] display.body.final`（＝玩家真正睇到嘅字）：上面嗰段**原封不動**，**句尾**多咗一行
  `合成台（无序合成）：切石器 + 木棍 -> modular sword。这是空白模组框架合成版本。`
- ⇒ **兩個結論**：① fix3「最後插入」**成功**（`latest.log` 有 `frame-standard: recipe line inserted (STANDARD frame)`）→ 之前報「插入冇觸發」已作廢；② 玩家睇到**自相矛盾**：上面講「冇可斷言嘅取得步驟」、下面又講合成台配方。**呢個就係本 plan 要修嘅嘢。**

### 0.2 對照組（唔准郁）
- MODIFIED（拟态，`ask-20260917-203349`）：`[74]` 老實講未收錄 + `[77]` 帶 `目前没有这件物品的取得资料，暂时不确定怎么拿到。`（`packai.reply.ask_miss_acquire_player`）→ **行為正確，本 plan 唔改**。
- UNKNOWN（`create:schematic` 藍圖，`ask-20260917-203601`）：照舊「無法斷言」→ **本 plan 唔改**。

### 0.3 現行配方句本身有**真錯**（今次 plan 一齊修，因為係同一句）
實測 `mods/tetra-1.19.2-5.6.0.jar` → `data/tetra/recipes/**`：**26 個 recipe json、其中 13 個產出 `tetra:modular_*`**，型別分佈：

| 產出 | jar recipe | type | 變體數 | 材料（representative） |
|---|---|---|---|---|
| `tetra:modular_sword` | `stonecutter.json` | **crafting_shapeless** | 1 | `tetra:stonecutter` + `minecraft:stick` |
| `tetra:modular_single` | `earthpiercer.json` | **crafting_shaped** | 1 | `tetra:earthpiercer` + `minecraft:stick` |
| `tetra:modular_double` | `hammer/*.json`（acacia／andesite／birch／dark_oak／diorite／granite／jungle／oak／spruce／stone） | **crafting_shaped** | **10** | `#`＝各變體材料、`/`＝tag `forge:rods/wooden` |
| `tetra:modular_toolbelt` | `toolbelt_modular.json` | **crafting_shaped** | 1 | `minecraft:string`（pattern ` # `／`# #`／` # `） |

⇒ 現行 `packai.reply.frame_standard_recipe` 逐字寫死「**無序合成**」→ 對 **3/4 種框架（12/13 個 recipe）都係錯**（只有石刻係 shapeless）。SK 今次只試過石刻所以未撞到；如果唔修就係明知會出錯資料。

### 0.4 結果物品名（已知，唔阻塞）
`tetra:modular_sword` 喺 tetra jar 語言檔**冇** `item.tetra.modular_sword`（只有 `tetra.holo.craft.modular_sword`＝刀劍／Blades）；pack 內亦搵唔到 override → `OfficialDisplay.officialName()` 回空 → 落到 `Plainify.displayName()` 嘅 path-token fallback（真機顯示 `modular sword`）。本 plan **維持現有顯示解析**（唔自創名），只要求「唔准出現裸 `ns:id`」。

---

## §1 目標

1. **STANDARD 框架嘅「怎麼來」段，最終玩家可見文字＝肯定句**：講清楚合成台配方（站別／型別／材料 → 結果），並且**唔可以再出現**「未收錄／查不到／无法断言／不能當取得途徑」等否定句（同一段之內）。
2. **嗰句資料要真確**：無序 vs 有序 由 jar 真資料決定；同族多變體（錘系 10 種）要講明係多版本。
3. MODIFIED／UNKNOWN 行為、卡政策、政策文字（9 處已完成）**一概唔郁**。

---

## §2 改動（逐檔逐位寫死）

### 2.1 `forge/1.19.2/src/main/java/com/skps9/packai/logic/ModularFrameStandard.java`
- `FrameRecipe`（現 `:34-51`）加兩個欄位：`boolean shapeless`、`int variantCount`（default `shapeless=false`、`variantCount=1`；保留現有 `Map<String,String> parts`／`List<String> ingredientItemIds`／`String resultItemId` 次序同語義）。
  - ⛔ **唔准改** `classify*`／`installExpected*`／`recipeAt` 嘅判定行為（STANDARD／MODIFIED／UNKNOWN 判準不變）。
- `TETRA_BLANK_FRAMES`（現 `:80-105`）4 條改成 §0.3 真值：sword `shapeless=true, variantCount=1`；single `false,1`；double `false,10`；toolbelt `false,1`。
- 類別內**唔准新增 CJK 字串 literal**（S11 gate）。
- 加註解寫明資料來源：`data/tetra/recipes/**`（26 json／13 modular／1 shapeless）＋ representative 材料慣例（沿用現有註解 `ponytail` 風格）。

### 2.2 `logic/HonestMiss.java`
- 新增 `public static String frameStandardRecipeLine(String lang, ModularFrameStandard.FrameRecipe recipe)`：
  - `shapeless==true` → key `packai.reply.frame_standard_recipe`；否則 → 新 key `packai.reply.frame_standard_recipe_shaped`。
  - `variantCount>1` → 尾接新 key `packai.reply.frame_standard_recipe_variants`（參數＝數量）。
  - 材料＝現有 `joinItemLabels(recipe.ingredientItemIds())`；結果＝現有 `itemLabel(recipe.resultItemId())`；任一空 → 回 `""`（fail-open，唔插）。
  - 只經 `ReplyLang.tr(...)` 取字（零硬編文字）。
- 現有 `ensureFrameStandardRecipeVisible`（`:151-174`）**改為呼叫新 helper**（單一真相來源），保留 append 語義以免影響未走新路徑嘅 call site。

### 2.3 `logic/AskReplyScrub.java`
- 由現有 `EMPTY_HOW_TO_GET`（`:72-76`）嘅 lookahead **抽出常量** `NEXT_SECTION_HEAD`（下一個 section 標題：怎麼用／作為材料／【來源】／`[Sources]`；**零新字面**），`EMPTY_HOW_TO_GET` 引用返同一個常量（行為不變）。
- 新增 `public static String replaceHowToGetBody(String answer, String fill)`：
  - 用現有 `HOW_TO_GET_HEAD`（`:78-79`）搵 heading → 由 heading 行尾之後、到 `NEXT_SECTION_HEAD`（或文末）之間嘅內文**換成 `fill`**；其他 section 原封不動。
  - `fill` 空 / 冇 heading / `answer` 空 → 原樣返回（fail-open）。
  - **唔准**新增 CJK 字串 literal（一律用上面抽好嘅常量）。

### 2.4 `logic/AskEngine.java`（現 `:967-986` 嘅 fix3 區塊）
- `frameKind == STANDARD && frameMatch.recipeIndex() != null` 時：
  1. `String line = HonestMiss.frameStandardRecipeLine(lang, stdRecipe);`
  2. `body = AskReplyScrub.replaceHowToGetBody(body, line);`（取代現時 `ensureFrameStandardRecipeVisible` 嘅 append；`line` 空 → 回落現有 append 路徑）
  3. 保留 `frame-standard: branch entered recipe=…` 同 **`frame-standard: recipe line inserted (STANDARD frame)`** 兩條 log 字串（`tests/check_modular_frame_standard.py:78` 有 pin），另加 `frame-standard: how-to-get replaced (STANDARD frame)` 同 `frame-standard: final check present={}`（唔改 trace 事件格式，只加 `LOGGER` 行）。
- ⛔ **唔准**改 `:932-943`（MODIFIED honest-miss）、`:944-956`（`ensureHowToGetBody` 其他 call site）、`:957-966`。

### 2.5 語言檔（3 檔同步；英文必齊）
- 改現有 key `packai.reply.frame_standard_recipe`（三檔現時都喺 `:512`）→ 保持 `%1$s`／`%2$s` 同「空白模組框架合成版本」token（`check_modular_frame_standard.py:100-127` 有 pin）：
  - zh_cn：`合成台（无序合成）：%1$s → %2$s。这是空白模组框架合成版本。`
  - zh_tw：`合成台（無序合成）：%1$s → %2$s。這是空白模組框架合成版本。`
  - en_us：`Crafting table (shapeless): %1$s → %2$s. This is the empty modular / empty-frame craft version.`
- 新 key `packai.reply.frame_standard_recipe_shaped`（有序）：zh_cn `合成台（有序合成）：%1$s → %2$s。摆放位置请以 JEI 为准。这是空白模组框架合成版本。`／zh_tw 對應／en_us `Crafting table (shaped): %1$s → %2$s. Check JEI for the layout. This is the empty modular / empty-frame craft version.`
- 新 key `packai.reply.frame_standard_recipe_variants`：zh_cn `（同族材料版本共 %1$s 种，其他木板／石料版本一样可以合成）`／zh_tw `（同族材料版本共 %1$s 種，其他木板／石料版本一樣可以合成）`／en_us ` (this frame family has %1$s material variants; other plank/stone versions craft the same frame)`
- 三檔 key 數同步（現 511 → 513）；`tests/check_settings_registry.py` 不受影響（唔係 settings key）。

### 2.6 明確唔郁（本 plan 之外）
卡抑制政策（`suppressModularFrameCards` 等 8 檔 14 位）／`render_recipe_cards` 回覆文案／`ModularFrameCards`／`PackAiConfig`／MODIFIED・UNKNOWN 分支／trace 事件名同欄位／政策文字 9 處（09-17 已完成）／`AGENTS.md`／`neoforge` 樹。

---

## §3 驗收標準（先寫好，做完逐項跑）

### S1 Java harness（新 `forge/1.19.2/src/test/java/com/skps9/packai/logic/FrameStandardRecipeLineCheck.java`，經 `tmp-check.gradle` 新 task）
- `shapelessFlags OK`：4 條框架 `shapeless` 真值（true／false／false／false）。
- `variantCount OK`：`modular_double`＝10，其餘＝1。
- `lineByType OK`：sword 用 shapeless key、single／double／toolbelt 用 shaped key（**唔准**靠字面比對，要對 `ReplyLang` 實回值＋語系切換 zh_cn／en_us 都成立）。
- `variantsClause OK`：double 句含版本數。
- `sectionReplaced OK`：餵「完整答案」（有怎麼來＋否定散文＋怎麼用＋【來源】）＋STANDARD → 出嚟嘅怎麼來＝配方句；**怎麼用／【來源】逐字不變**。
- `stubDiscriminates OK`（負控）：把替換關掉（直接回原答案）→ `sectionReplaced` 斷言**必須紅**（證明測試有鑑別力）。
- `noHeadingFailOpen OK`：答案冇「怎麼來」heading → 原樣返回（唔准亂插）。

### S2 Python 靜態閘
- 改 `tests/check_modular_frame_standard.py`：新欄位存在、4 條真值（shapeless／variantCount）、新 helper／`replaceHowToGetBody` 存在、3 檔語言有新 key 同 placeholder、`ModularFrameStandard.java` 仍然 CJK=0。
- 新 `tests/check_frame_standard_recipe_line.py`：pin 3 條語言文案（含「有序」「無序」分家）、`AskEngine` STANDARD 分支用新 helper、log 字串仍在。
- 全量 `for f in tests/check_*.py`：**唔准新增紅**（baseline 119 PASS／0 FAIL，09-17 實測）。

### S3 真機（SK 出手，我核 trace）
1. 手持**石刻／切石器**問同一個問題 → `display.body.final` 必須：(i) 有配方句（合成台（無序合成）：切石器 ＋ 木棍 → …）；(ii) **「怎麼來」段唔准**再出現「未收錄／查不到／无法断言／不能當取得途徑」；(iii) 同一段前後唔准自相矛盾。
2. 手持**擬態**（真特製版）→ 仍然老實講未收錄（唔准變成講配方）。
3. 手持**木錘**（`tetra:modular_double` 類，shaped 系）→ 講「**有序合成**」＋版本提示（呢條證明 §0.3 修好）。
4. `python tests/check_ask_display_leak.py --trace <instance>/packai/trace --since 20260918 --min-annotations 0` → RC=0。
5. 回歸對照：抽 1 條非框架問題（例 `create:schematic`）→ 答案同今日一致。

### S4 交付前
`compileJava compileTestJava` 0 error；harness 全綠；python 閘無新增紅；`code_change_log.md`／HANDOFF 更新；**未過真機驗收唔 commit 實作、唔 bump 版本、唔部署**。

---

## §4 白名單 + 還原點

**改（7）**：`logic/ModularFrameStandard.java`、`logic/HonestMiss.java`、`logic/AskReplyScrub.java`、`logic/AskEngine.java`、`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`、`tests/check_modular_frame_standard.py`、`research/gen_tmp_check.py`（加 task）
**新（2）**：`src/test/java/com/skps9/packai/logic/FrameStandardRecipeLineCheck.java`、`tests/check_frame_standard_recipe_line.py`
（`forge/1.19.2/tmp-check.gradle` 係生成檔、gitignored，唔准 `git add`。）

**還原點（動工前做，可驗證）**：
- `.hermes/backups/2026-09-18_frame_standard_answer/`：上述 4 個 Java 檔 ＋ 3 個 lang 檔備份 ＋ `md5sum` ＋ `git status --porcelain` 快照（現時 **112 項 dirty** 為 baseline）。
- 現行上機 jar：`mods/packai-0.2.3+mc1.19.2-forge.jar`，sha256 **`bcceb19fc4fc`**（本 plan 唔部署；要部署時改用 `mc_mod_deploy_jar.py --jar <新jar> --name <新名>`，舊 jar 自動 backup 去 `%TEMP%\deploy_backup_*`）。
- 回滾：`.hermes/backups/…` copy 返檔（或 `git restore` 只限本 plan 白名單檔）＋關遊戲後由 `%TEMP%` backup copy 返 jar。

---

## §5 風險 / 反轉條件（出現即停手問 SK）

| 風險 | 偵測 | 應對 |
|---|---|---|
| 段替換邊界寫錯（吞咗「怎麼用」或【來源】） | S1 `sectionReplaced`／`noHeadingFailOpen` | 唔准出閘；改 pattern 再跑 |
| 新文案令 `check_reply_prompt_keys.py`／`check_modular_frame_standard.py` 紅 | S2 | 改文案，**唔准**改 assert 遷就 |
| 段替換令某啲答案「怎麼來」變空／重複 | S1＋真機 S3-1 | 停手，回落 append 路徑 |
| 真機 3 問中**仍然**出現否定句（≥1 次） | S3-1 | **停手問 SK**（唔自己擴範圍去碰卡政策） |
| 結果名仍然係 path-token（`modular sword`） | S3 | 已知限制（§0.4），唔阻塞；若 SK 要求換名 → 另開 |
| 改動超出白名單 | `git status --porcelain` 對 baseline | 即刻 revert 該檔 |

**反轉條件（推翻本 plan 方向）**：① 若真機證明模型嘅否定散文唔喺「怎麼來」段（而係散落各段）→ 段替換不足，要改為「餵 AI 事實」路線；② 若段替換令答案結構明顯變差（SK 睇到唔鍾意）→ 回落 append 路線＋接受矛盾（或改產品決定）。

---

## §6 反方 review 紀錄（Gate：正方 ≥8 : 反方 ≤2）

| 輪 | 日期 | 比分 | 主要修正 |
|---|---|---|---|
| A-R1 | 2026-09-18 | 待填 | — |
