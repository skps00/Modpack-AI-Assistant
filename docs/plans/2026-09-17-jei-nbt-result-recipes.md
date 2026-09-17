# Plan D v5 — 修「石刻／亞巴頓」答錯：收窄到兩處最小修

> 狀態：**v5（2026-09-17 13:3x）** — 依 R3（3:7）＋其 Q6 建議收窄。**刪去** F1（類別 fallback）＝反方證明方向唔匹配，且會被壓制政策打架。
> 輪次：R1 2:8 → R2 4:6 → R3 3:7 → 本版交 R4。SK：**a until it 8:2**。⛔ 未批准實作。

## §0 事實（逐條有行號；已修正前幾版錯誤）
1. **12:24 真 log**（Big5 解碼）：`:8425 recipe cards count=0`；`:8427 role=OUTPUT uids=[create:automatic_shapeless]`；**:8433 JEI dump len=375 已有配方**「石切器、木棍 → 石刻」（Create 自動攪拌形式）；`:8424 frameCardsSuppressed n=2`（卡路徑本來命中，被政策 drop）。
2. **模型側**（trace `ask-20260917-122439` 59 條事件）：`send.user` **冇 `jei` key** ✅、`send.system` **零**「石刻」✅、模型自己叫 `jei_lookup(dump_level=INFO)` → **result 空**（rec 27-28）。
3. **更正**：`send.facts` **非空**（rec 18/32/38/51 = 293 chars，因為 `graphFacts` 永遠存在；`LlmClient.java:514-519`）⇒ v4 §0.2 講「空」係錯，已刪。
4. **tool 回空嘅真因（兩條）**：
 - **T1**：`client/jei/JeiLookupAskTool.java:53-62` 用 `ItemResolver.stackFromId(args.itemId)` **覆蓋** `env.stack`，而 `ItemResolver.java:182-209` 冇 SNBT 就 `new ItemStack(item)`＝**冇 NBT**（codebase 自己喺 `:220-226` 註明此坑）→ NBT 變體物品 focus 匹配必失。
 - **T2**：模型第一次用 `dump_level=INFO`；`JeiLookup.java:151-160` INFO 只回 `JeiInfoPages.dump`，**天然冇配方**。
5. **prompt 側（R-B，v4 已證）**：`logic/AskEngine.java:777-787 jeiForLlmSlim()`：tool-capable 時只送 `[RECIPE_CARDS]` 目錄＋tooltip 提示，**唔送 JeiLookup dump**；當時 catalog 空 ⇒ 375 字 fact 冇入 prompt。
6. 配方真相（mod jar `data/tetra/recipes/stonecutter.json`）：`minecraft:crafting_shapeless`（`tetra:stonecutter`＋`minecraft:stick` → `tetra:modular_sword`），result 帶 NBT（含隨機 `id`）。

## §1 修法（只兩處，全部有現成先例）
- **F1（T1；1–3 行）**：`JeiLookupAskTool.run()` —— 當 `args.itemId` 同 `env.stack` **同一 registry id** 時，**唔准覆蓋** `env.stack`（或改用保留 NBT 嘅 `ItemResolver.stackFromRef`）→ 直接令已量到嘅 375 字 OUTPUT dump 生效。
- **F2（T2＋R-B）**：`AskEngine.jeiForLlmSlim()` —— 當 `recipeCatalogForLlm()` 係 null **且** shot-0 dump 非空（**需新寫 extractor**：現成 `recipeCardsCatalogSlim` `AskEngine.java:1462-1483` 係 marker-gated，取唔到）→ 塞**最多 3 行** output 配方。上限常數要**新定義**（唔准借用 `JeiLookup.java:52/:54`）。
- **F3（可選，獨立細項）**：實際錯句**唔 match** 現有 predicate（真跑：`looksLikeAbsenceClaim("…查不到它的合成…取得方式无法确定。")`＝false、`("…只是空框架，不是這把定制工具的取得方式。")`＝false）→ 若要做，**必須新增詞樣**（明確列出），否則 no-op。唔准聲稱「複用即可」。
- **⛔ 唔做（明確剔出）**：F1-v4 類別 fallback（`ensureCoreCraft` 回卡物件、`fromVanillaCrafting` 係 private `:766`；且卡路徑已被 `ModularFrameCards`／`AskService.java:2643-2667` **政策壓制**，同 system prompt 規則 1c 相反）→ 屬**另一個 suppression 政策計畫**。`sameItemDifferentTags`（`:1022` private，diagnostic boolean）對「令 dump 非空」零作用 → 唔用。

## §2 驗收（S1–S8，machine-checkable，已按 R3 更正）
- **S1**：新 trace 內 `jei_lookup` 嘅 `tool.result` **非空**，且包含「**木棍**」（避免只用焦點顯示名嘅 tautology）；同時要記錄 model 實收嘅係 result 抑或 TOOL_MISS 文字。
- **S2**：**刪**（F1 唔會令 JEI `categories` 出現 `minecraft:crafting`；舊 S2 係假紅）。
- **S3**：新 trace 內 prompt **真含**配方行：`send.system` 或 `send.user` **包含「石切器」或「木棍」**（今日 = 0，有鑑別力）。唔准用 `send.facts` 非空（今日已成立＝假綠）。
- **S4**：負控（真無配方）：`minecraft:bedrock` → 答案仍准講「查不到」；**註明**包內有 bedrock rightClicked 腳本（`kubejs/server_scripts/b_a_d/item/b_a_d_item.js:307`、`golden_age/events.js:1007`）會令判讀混濁 → 需人手確認一次。
- **S5**：負控（**主要風險**＝空框架配方被歸因到定制工具）：斷言修後 `frameCardsSuppressed` **仍然** n≥1（政策唔准被繞過）＋ 答案唔准把空框架配方寫成該定制工具嘅取得途徑。
- **S6**：成本基準改用**石刻**問句（今日 `10850／11406／11838／12658`，唔係 v4 寫嘅 11852／12384＝另一件物品）＋ prompt 增幅 **≤400 字**；另加**時間上限**（fallback／extractor 不得令單次 ask 明顯變慢，貼實測）。
- **S7**：新 extractor 嘅 harness：`src/test/java/com/skps9/packai/client/gui/JeiSlimRecipeLineCheck.java`；命令 `./gradlew.bat -I tmp-check.gradle runJeiSlimRecipeLineCheck`。
- **S8（更正基準）**：`tests/check_*.py` 共 **118** 個；`check_ask_display_leak.py` 無參數＝**RC=0**（唔係 2）、今日 trace `--since 20260917 --min-annotations 0` ＝**RC=0**（零鑑別力，只能當 smoke）；**既有紅樣本**：`--since 20260901` → RC=1（`ask-20260915-170823-eccentrictome_tome.jsonl:25 purpose_lookup` peer 含「（無官方名）」）→ 要**明確記錄為已知基線**，唔准講「全綠」。

## §3 影響面／還原
- 檔案：`client/jei/JeiLookupAskTool.java`（F1）、`logic/AskEngine.java`（F2＋新 extractor）、（可選）`logic/AskJeiHints.java`（F3）、新 harness。
- 還原點：改動前 copy 去 `.hermes/backups/<日期>_jei_toolstack/` ＋ `md5sums.txt`。
- 唔郁：`PackAiConfig` 預設、語言檔、trace 格式、卡抑制政策、`PackIndex`。

## §4 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| 1 | v1/v2 | 2 : 8 | 反方用 `:8433` 打死「冇 fact」前提 |
| 2 | v3 | 4 : 6 | 證「fact 冇入 prompt」；D1 類別 fallback 唔夠（focusGroup 過濾） |
| 3 | v4 | 3 : 7 | F1 方向唔匹配（卡路徑已命中，只係被政策壓制）＋真因＝tool stack NBT 被蒸發＋INFO 層級；`send.facts` 非空 → v5 收窄 |
| 4 | v5（本檔） | 待跑 | — |
