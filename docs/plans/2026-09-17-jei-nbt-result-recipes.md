# Plan D v6 — 只做 F2：令「已知配方行」真入到 prompt

> 狀態：**v6（2026-09-17 13:5x）** — 依 R4（3:7）建議收窄：**只做 F2**（反方已核實可行、目標文字真喺 ask-time dump 內）；**F1 擱置**（因果未證、且有 regression 面）。
> 輪次：R1 2:8 → R2 4:6 → R3 3:7 → R4 3:7 → 本版交 R5。SK：**做到第 10 輪**。⛔ 未批准實作。

## §0 事實（修正前幾版錯處）
1. **12:24 真 log**（Big5）：`:8425 recipe cards count=0`；`:8427 role=OUTPUT uids=[create:automatic_shapeless]`；**`:8433 JEI dump len=375 已有配方**「石切器、木棍 → 石刻」；`:8424 frameCardsSuppressed n=2`。
2. **trace `ask-20260917-122439`（59 事件）**：`send.user` **冇 `jei` key**、`send.system` **零**「石刻」；模型 round1 叫 `jei_lookup(INFO)` → 空；**round2 真叫過 `jei_lookup(OUTPUT)`**（rec 33）→ rec 35 收到 soft-miss note（`[TOOL_MISS] jei_lookup(INFO) empty…`）。
3. **更正 R3/R4 指出嘅錯**：① `send.facts` **非空**（graphFacts 永遠存在）→ 唔准用作判準；② 檔案路徑係 **`logic/JeiLookupAskTool.java:49-57`**（唔係 `client/jei/…`）；③ `client/jei/JeiLookup.java:666-669` 嘅 `createRecipeCategoryLookup().limitFocus(...)` 仍係類別發現點，但**唔改**（見 §2 剔出）。
4. 配方真相（mod jar `data/tetra/recipes/stonecutter.json`）：`minecraft:crafting_shapeless`（`tetra:stonecutter`＋`minecraft:stick` → `tetra:modular_sword`），result 帶 NBT。

## §1 根因（只保留已證嘅一條）
**R-B（已證）**：`logic/AskEngine.java:777-787 jeiForLlmSlim()` —— LLM 支援工具時只送 `[RECIPE_CARDS]` 目錄＋tooltip 提示，**唔送 JeiLookup dump**；12:24 時 catalog 係空（`:8425 count=0`）⇒ **375 字配方 fact 由頭到尾冇入 prompt**。模型只靠工具，而工具回空 → 答「查不到」。

## §2 修法（**只 F2**）
- **F2（唯一實作項）**：`AskEngine.jeiForLlmSlim()` —— 當 `recipeCatalogForLlm()` 係 null **且** shot-0 嘅 JeiLookup dump 非空 → 塞**最多 3 行** output 配方入 prompt。
 - 需要**新寫 extractor**（現成 `AskEngine.java:1462-1483 recipeCardsCatalogSlim` 係 marker-gated，catalog 空時拎唔到嘢）。
 - 「3 行」常數**新定義**（唔准借用 `JeiLookup.java:52/:54`）。
 - 只喺 catalog 空時生效 → catalog 非空時輸出**完全不變**（保護既有行為）。
- **⛔ 剔出（明確唔做）**：
 - **F1**（tool stack）—— R4 證：真檔係 `logic/JeiLookupAskTool.java:49-57`；「同 id 唔覆蓋」會吞掉模型用 SNBT 指名變體嘅路徑（`ItemResolver.java:190-207`；`latest.log:8429 focusFailTagOnly=1`），係未寫嘅 regression 面；而「bare stack 會唔會回空」**至今未有 trace 真跑到** ⇒ 證據不足，**擱置待真機量測**（S0）。
 - **類別 fallback**（`ensureCoreCraft` 回卡物件、`fromVanillaCrafting` 係 private `:766`；卡路徑已被 `ModularFrameCards`／`AskService.java:2643-2667` 政策壓制）→ 屬**另一個 suppression 政策計畫**。
 - `sameItemDifferentTags`（`JeiLookup.java:1022` private、diagnostic boolean）→ 對本目標零作用。
- **F3（可選，獨立）**：實際錯句**唔 match** 現有 predicate（真跑：`looksLikeAbsenceClaim("…查不到它的合成…取得方式无法确定。")`＝false）→ 若做，**必須新增詞樣並逐條列出**；唔准聲稱「複用即可」。

## §3 驗收（S0–S8，machine-checkable）
- **S0（前置，真機・SK 30 秒）**：SK 喺遊戲再問一次「石刻點嚟」→ 睇新 trace 有冇 round≥2 且真執行嘅 `jei_lookup(OUTPUT)` 同其 `tool.result`（一併記錄 `Pack AI JEI diag start`）。**目的**：為 F1 嘅未知留證據；F2 唔靠佢。
- **S1（F2 生效）**：新 trace 內 prompt 真含配方行 —— `send.system` 或 `send.user` **包含「木棍」**（今日 = 0，有鑑別力）。**唔准**用 `send.facts` 非空做判準（今日已成立＝假綠）。
- **S2**：**刪**（F2 唔會令 JEI `categories` 出現 `minecraft:crafting`；舊斷言假紅）。
- **S3（catalog 非空唔變）**：對一件 catalog 非空嘅物品（例今日 12:14 嗰件）→ `Pack AI LLM usage prompt=` **唔准**增加（回歸保護）。
- **S4（負控・真無配方）**：`minecraft:bedrock` → 答案**仍准**講「查不到」，且 S1 斷言唔准誤觸；**註明**包內有 bedrock rightClicked 腳本（`kubejs/server_scripts/b_a_d/item/b_a_d_item.js:307`、`golden_age/events.js:1007`）令判讀混濁，需人手確認一次。
- **S5（負控・政策唔准被繞過）**：修後 `frameCardsSuppressed` **仍然** n≥1；答案唔准把空框架配方寫成該定制工具嘅取得途徑。
- **S6（成本）**：基準用**石刻**問句（今日 `10850／11406／11838／12658`；唔准用 11852／12384＝另一件物品）＋ prompt 增幅 **≤400 字**＋單次 ask 耗時（貼實測）。
- **S7**：extractor harness：`src/test/java/com/skps9/packai/client/gui/JeiSlimRecipeLineCheck.java`；命令 `./gradlew.bat -I tmp-check.gradle runJeiSlimRecipeLineCheck`。
- **S8（更正基準）**：`tests/check_*.py` 共 **118** 個；`check_ask_display_leak.py` 無參數＝**RC=0**（唔係 2）；今日 trace `--since 20260917 --min-annotations 0` ＝**RC=0（零鑑別力，只算 smoke）**；**已知紅樣本**：`--since 20260901` → RC=1（`ask-20260915-170823-eccentrictome_tome.jsonl:25 purpose_lookup` peer 含「（無官方名）」）→ **如實記錄為已知基線**，唔准講「全綠」。

## §4 影響面／還原
- 檔案：`logic/AskEngine.java`（F2＋新 extractor）、新 harness、（可選）`logic/AskJeiHints.java`（F3）。
- 還原點：改動前 copy 去 `.hermes/backups/<日期>_jei_slimrecipe/` ＋ `md5sums.txt`。
- 唔郁：`PackAiConfig` 預設、語言檔、trace 格式、卡抑制政策、`PackIndex`、`JeiLookupAskTool`（F1 擱置）。

## §5 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| 1 | v1/v2 | 2 : 8 | 反方用 `:8433` 打死「冇 fact」前提 |
| 2 | v3 | 4 : 6 | 證「fact 冇入 prompt」；類別 fallback 唔夠 |
| 3 | v4 | 3 : 7 | F1 方向唔匹配；`send.facts` 非空；S 多條假綠 |
| 4 | v5 | 3 : 7 | F1 檔案路徑錯、SNBT 路徑 regression、bare-stack 未證 → **v6 只做 F2** |
| 5 | v6（本檔） | 待跑 | — |

## §6 R5（4:6）裁決後：**卡死點＝政策衝突，等 SK 拍板**
- F2 機制本身**存活**（R5 核實：目標文字真喺 shot-0 dump；錨點準）。但反方揭到一個**高嚴重度、未寫嘅後果**：
  被注入嗰行「機器：石切器、木棍 → 石刻」**正正係我哋自己政策明文禁止當成取得途徑嘅『空白框架合成』**（system prompt **規則 23** `kubejs/assets/kubejs/lang/zh_cn.json:484`；風格段 `:388`；抑制政策 `AskService.java:2643-2667` → `ModularFrameCards.shouldDropFrameCard`，今日 `:8424 frameCardsSuppressed n=2`）。
  ⇒ F2 等於由 dump 側門把**被壓制嘅內容塞返 prompt**，會令模型由今日「正確」嘅答案（trace rec 53：「空白的模组剑框架…只是空框架，不是取得方式」）變成違規。
- ⚠️ 但 **SK 09-17 12:26 明確講**：「這三張圖片所顯示的工具可以直接在合成台可以合成，這也是它的合成方法（跟正常的有點不同）」→ 即 SK 想答案**講得返**「合成台：石刀＋木棍 → 石刻」。
- **兩者直接衝突** ⇒ 呢個係**產品政策決定**，唔係 code path 問題；而且 `prompt／卡／scrub／渲染行為` 屬 AGENTS「唔准郁」清單，要 SK 明確 go 先改。
- 三個方向（等 SK 揀）：
  **P1**：改政策 —— Tetra 模組件若有 vanilla 合成台配方 → 答案要講（標明係「該材料版本／空框架合成」用包原文措辭）；配套改規則 23＋抑制 predicate（`ModularFrameCards.shouldDropFrameCard` 例外）。
  **P2**：維持現狀（唔注入、只講 Tetra 工作台組裝＋註明框架唔等於取得途徑）→ 唔改任何嘢，本 plan 收檔。
  **P3**：只改措辭（R5 建議 (b)：放寬 `AskService.java:906-972 appendClaimLines` 令 `→` 機器行當「低信心提示」入 `[TOOLTIP_HINT]`），仍然要 SK 批准（因為會改變玩家可見文字）。
- 另：F3 **剔**（`AskMissFallback.java:36/43-44` 已經 match「查不到／无法确定」，我上一版過度聲稱）；S0/S1/S3/S5/S8 嘅假綠修正已記錄（S0 anchor 改 `send.history`、S1 用『石切器』唔准用『木棍』、S3 用 09:13:02/09:13:32/10:36:12 或 `jei` 欄 byte-diff、S5 改成政策 gate、S8 必須帶 `--trace`）。
