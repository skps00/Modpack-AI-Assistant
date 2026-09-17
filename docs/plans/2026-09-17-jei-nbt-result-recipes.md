# Plan D v7 — 政策 P1：模組件嘅「合成台配方」要入到答案（標明材料版本）

> 狀態：**v7（2026-09-17 14:0x）** — SK 拍板 **P1**：Tetra 類模組件若有 vanilla 合成台配方 → 答案要講，並以包原文標明「該材料版本／空框架合成」。
> 輪次：R1 2:8 → R2 4:6 → R3 3:7 → R4 3:7 → R5 4:6 → R6（本版）。SK：審到第 10 輪。⛔ 未開始實作。

## §0 事實（已核，前版本錯處已改）
1. Tetra 配方真身（`tetra-1.19.2-5.6.0.jar` `data/tetra/recipes/stonecutter.json`）：`minecraft:crafting_shapeless`，`tetra:stonecutter`＋`minecraft:stick` → `tetra:modular_sword`，result 帶 NBT（含隨機 `id`）。
2. 12:24 真 log（Big5）：`:8425 recipe cards count=0`／`:8427 role=OUTPUT uids=[create:automatic_shapeless]`／**`:8433 JEI dump len=375 含「石切器、木棍 → 石刻」**／`:8424 frameCardsSuppressed n=2`。
3. trace `ask-20260917-122439`（59 事件）：`send.system` 零「石刻」；`send.user` 無 `jei` key（`LlmClient.java:439-440` 只喺非空時加）；模型 round1 叫 `jei_lookup(INFO)` 回空；round2 真叫過 `jei_lookup(OUTPUT)`（rec 33）→ 收到 `[TOOL_MISS]` 措辭（`LlmClient.java:618` 只按工具名揀 note、**唔睇 level** ← 附帶 bug，記錄唔修）。
4. **現行政策係相反方向**：`kubejs/assets/kubejs/lang/zh_cn.json:484` 規則 23＋`:388` 風格段明文禁止把「切石机＋木棍」當成該定制工具嘅取得方式；卡路徑由 `AskService.java:2643-2667` → `ModularFrameCards.shouldDropFrameCard`（primaryOutput==focus id 就 drop）執行；今日 `:8424 n=2`。
5. `AskEngine.java:777-787 jeiForLlmSlim()`：LLM 支援工具時**唔送** JeiLookup dump，只送 `[RECIPE_CARDS]` 目錄（今日 catalog 空 → 375 字 dump 完全冇入 prompt）。`recipeCardsCatalogSlim` 真位置 `:1462-1486`（marker-gated）。

## §1 決定（SK 2026-09-17 約 13:4x，P1）
**政策**：Tetra 類「模組件」（有 vanilla 合成台配方者）→ 答案**要**講該配方，並以**包原文**標明佢係「該材料版本／空框架合成」，唔可以照舊當佢唔存在。
→ 即係**改寫規則 23 ＋ 為抑制機制加入例外**；`prompt／卡／渲染行為` 屬 AGENTS「唔准郁」清單，**已取得 SK 明確 go（P1）**。

## §2 修法（三件，缺一唔得）
- **F-A 政策文字（zh + en 同步）**：`zh_cn.json:484`（規則 23）、`:388`（風格段）＋ `en_us.json` 對應 key。新措辭 = **引用包自己嘅字**（「切石机＋木棍」），句子結構：「若某工具喺合成台有無序合成配方（例：切石机＋木棍 → 石刻），**要照講**，並註明係**該材料版本**；唔准當佢冇配方，但亦唔准把它講成唯一途徑」。
- **F-B 資料入得 prompt（二選一，要寫明點解唔用另一個）**：
 - **(b) 最小**：放寬 `AskService.java:906-972 appendClaimLines`（現時要求行含「获得／取得」），令 `→` 機器行算「取得」→ 落**既有** `[TOOLTIP_HINT]` 頻道（`AskEngine.java:782`、`:1448-1459`），cap 3 複用，`AskEngine` 唔使改（2 行）。
 - **(a) 零新 extractor**：`AskEngine.java:781-786` catalog null 時 fallback 現成 `jeiForLlm()`（= 同一份 375 字 dump，走 shipped `jeiForLlmFull()`；`:1511-1520`）＋ clip。代價：帶輸入清單雜訊。
 - **建議 (b)**（改動更細、落點係低信心提示頻道、唔帶雜訊）；(a) 留作 fallback 方案。
- **F-C 抑制機制例外**：`ModularFrameCards.shouldDropFrameCard` —— 當該模組件**存在** vanilla 合成台配方（item-id 匹配、**忽略 NBT**；`JeiFocusMatch.craftingResultMatches` 係 `public` ✅ 可直接重用）→ **唔 drop**，改成輸出「標明材料版本」嘅行／卡。唔做呢件會自相矛盾（F-B 塞返入去、卡路徑照舊丟）。
- **⛔ 明確唔做**：F1 tool-stack（`logic/JeiLookupAskTool.java:49-57`；未證、有 SNBT regression 面）／類別 fallback（`ensureCoreCraft` 回卡物件、`fromVanillaCrafting` `:766` 係 private）／`sameItemDifferentTags`（private＋diagnostic）／F3 措辭 predicate（`AskMissFallback.java:36/43-44` 已經 match，上一版過度聲稱）。

## §3 驗收（S0–S9，全部機器可驗；R5 指出嘅假綠已修）
- **S0 真機前置（SK 30 秒）**：再問一次「石刻點嚟」→ 新 trace：`send.user.jei` 有 `→` 行 **或** `send.history` 內含該 tool message（**唔准**用 `round` 欄，trace 只有 {1,3}）。同時記錄 `Pack AI JEI diag start`。
- **S1（入到 prompt）**：新 trace 內含「**石切器**」（今日全 trace = 0，有鑑別力）。**唔准**用「木棍」（system prompt 規則文字已有，45/51 條 trace 命中＝假綠）；**唔准**用 `send.facts` 非空（今日已成立）。
- **S2**：刪（F-B 唔會令 `categories` 出 `minecraft:crafting`，舊斷言假紅）。
- **S3（catalog 非空唔變）**：用**真非空**個案 09:13:02（6 行）／09:13:32（4）／10:36:12（12）；用同一次 ask 嘅 `jei` 欄 **byte-diff**，唔用跨 ask prompt token 數。
- **S4（負控・真無配方）**：`minecraft:bedrock` → S1 斷言唔准誤觸；註明包內有 bedrock 腳本（`b_a_d_item.js:307`、`golden_age/events.js:1007`）需人手確認一次。
- **S5（政策 gate・新）**：答案**唔准**再出「只是空框架，不是取得方式」式否定（今日 trace rec 53 有）；改後必須包含「合成台／無序合成」＋「石切器」＋「木棍」三者（並保留「材料版本」字樣）。
- **S6（成本）**：石刻問句基準 `10850／11406／11838／12658`；prompt 增幅 ≤400 字（dump 375 字自洽）＋ asking 耗時貼實測。
- **S7**：harness `src/test/java/com/skps9/packai/client/gui/JeiSlimRecipeLineCheck.java`；命令 `./gradlew.bat -I tmp-check.gradle runJeiSlimRecipeLineCheck`。
- **S8**：`tests/check_*.py` 共 **118**；`check_ask_display_leak.py` 無參數 → RC=0；**已知紅樣本必須帶 `--trace "<instance>/packai/trace" --since 20260901` → RC=1**（`ask-20260915-170823-eccentrictome_tome.jsonl:25 purpose_lookup peer has （無官方名）`）→ 如實記錄，唔准講「全綠」。
- **S9（洩漏）**：新 trace 零 `file:line`／`.js`／內部 id 落玩家文字。

## §4 風險／還原（第一規則）
- **風險**：改 prompt 規則 + 卡抑制＝**玩家可見行為改變**（公開發佈中，跨時區玩家）；最壞情況＝答案把「空框架」講成唯一途徑（今日反而係相反病）。
- **還原**：改動前逐檔備份到 `.hermes/backups/2026-09-17_p1_policy/`（`zh_cn.json`／`en_us.json`／`AskService.java`／`ModularFrameCards.java`／`AskEngine.java`）＋ `md5sums.txt`；`git revert <commit>` 亦可（呢批檔全部 tracked ✅）。舊 jar 由 `mc_mod_deploy_jar.py` 自動備份。
- **唔郁**：trace 事件名／欄位、`PackAiConfig` 預設、`PackIndex`、卡渲染邏輯本身。

## §5 Review 記錄
| 輪 | 對象 | 比分 | 關鍵 |
|---|---|---|---|
| 1 | v1/v2 | 2 : 8 | 反方用 `:8433` 打死「冇 fact」 |
| 2 | v3 | 4 : 6 | 證「fact 冇入 prompt」 |
| 3 | v4 | 3 : 7 | F1 方向唔匹配；S 假綠 |
| 4 | v5 | 3 : 7 | F1 路徑錯、SNBT regression |
| 5 | v6/v6.1 | 4 : 6 | F2 可行但撞規則 23 → 交 SK（P1） |
| 6 | v7（本檔） | 待跑 | P1 三件修法 + 全機器可驗驗收 |
