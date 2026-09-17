# Plan D v8 — P1 政策：模組件「合成台配方」要入答案（修正 R6 六條 flip conditions）

> 狀態：**v8（2026-09-17 14:2x）** — 依 R6（3:7）六條修。輪次：2:8 → 4:6 → 3:7 → 3:7 → 4:6 → 3:7 → R7（本版）。SK：審到第 10 輪。⛔ 未實作。

## §0 事實（v6 錯誤已由 R6 更正）
1. **語言檔唔喺 kubejs**：真路徑 = `forge/1.19.2/src/main/resources/assets/packai/lang/{zh_cn,en_us,zh_tw}.json`（repo 內冇 `kubejs/assets/kubejs/lang/`）。行號：zh_cn `388/389/484`；en_us `392/393/484`（en_us `:388` 係 `season_fd`）；zh_tw `392/393/484`。
2. 12:24 log（Big5 `:8424-8438`）：`recipe cards count=0`；`role=OUTPUT uids=[create:automatic_shapeless]`；`:8433` dump 375 字＝「· [自动搅拌] 共 1 項： - 机器“动力搅拌器、工作盆”： 石切器、木棍 → 石刻」；`claimHints obtainish=true src=221/375/0 **out=0**`。
3. trace `ask-20260917-122439`：`send.user` **事件頂層冇 `jei`**（`AskTrace.java:747` 硬寫 `content`；payload 係 `send.user.content` 內 JSON）→ 斷言要 parse `content` 再取 `.jei`。
4. 政策相反方向：`tool_build` key（`:484`）＋ `llm_style`／`llm_style_notools`（`:388/389` 或 `:392/393`）**共 9 處**（3 檔 × 3 key）—— 現行措辭：空框架合成唔等於該定制工具嘅取得方式（有 carve-out：要提就標成「空白模組劍合成」）。
5. `AskEngine.java:777-787 jeiForLlmSlim()`：tool-capable 時唔送 dump；catalog 空 → `:784` 回 `pre`（＝`recipeGetCleanForLlm`）。`AskEngine.java:1461` 註解明寫 slim path 目的係 **keep indexed [RECIPE_CARDS] catalog only（no JEI summary / machine noise）**。

## §1 決定（SK P1）
模組件若有 vanilla 合成台配方 → 答案**要**講，並以包原文標明「該材料版本／空框架合成」。→ 改 9 處政策文字 ＋ 為抑制機制加例外；已得 SK 明確 go。

## §2 修法（三件；R6 六條修已併入）
- **F-A 政策文字：全部 9 處（3 檔 × llm_style／llm_style_notools／tool_build）**，改完要**三檔一致**（否則 tools path 一套、no-tools path（HTTP 400／非 tool LLM）另一套 → 同一問句兩個答案）。新措辭同 `llm_style` 尾段「不是這把定制工具的取得方式」**同時改**，唔可以只改一半變自相矛盾。措辭必須用包原文（「切石机＋木棍」）。
- **F-B（已驗接通 ✅，選 (b)）**：放寬 `AskService.java:906-972 appendClaimLines`，令含 `→` 嘅機器行當 claim → 落 `[TOOLTIP_HINT]`（`AskEngine.java:782` → `recipeGetCleanForLlm` → `:1461-1465`；catalog 空時經 `:784` 出 `user.jei`）。今日 `out=0` 只因現行要求「获得／取得」字。
 - **唔用 (a)**：`AskEngine:1461` 明文反設計（會倒機台雜訊入 prompt）；`AskEngine:783-786` 仍要改，唔係「零改」。
 - **新增守則（R6 ⑥）**：`→` 放寬係**全局 predicate**（tooltip／JEI／卡描述三源共用），而 `[TOOLTIP_HINT]` 喺 51 條 trace **出現 0 次** → 改後會由「從未觸發」變成「大部分 obtain 問句都觸發」⇒ 必須加**負控**：非模組件、非 obtain 問句**唔准**新增 TOOLTIP_HINT。
- **F-C 抑制例外（R6 判死，要照下面重寫）**：
 - `ModularFrameCards.shouldDropFrameCard(String,String,boolean,boolean)` ＝**純字串、冇 recipe／ItemStack** → 要「知道存在 vanilla 合成台配方」**必須改簽名**（⇒ `src/test/java/.../ModularFrameCardsCheck.java` 4-arg 呼叫全改，否則 `compileTestJava` 紅）**或**把規則搬出純核心。
 - **3 個呼叫點全要覆蓋**：`AskService.java:2654`、`AskToolEnv.java:90`（emission gate → 模型收嘅 digest）、`AskCardFallback.java:448`（keyword／no-tools path）。**漏任一 = 兩條路徑行為分歧**。
 - **例外邊界（寫死）**：例外只適用於「該模組件嘅 vanilla crafting 配方（`RecipeType.CRAFTING`，item-id 匹配、忽略 NBT；用 `JeiFocusMatch.craftingResultMatches(Object recipe, ItemStack focus)` — `public` ✅）**存在**」；命中 → 唔 drop，改成**帶標籤嘅卡**（標籤用包原文「空白模組劍合成」）。其餘一律維持 drop。
 - **要改嘅測試 pin**：`tests/check_card_emission_suppression.py:92-94`（`shouldDropFrameCard(` 仍須存在）、`:110-111`（`suppressedFrameOnly`／「框架合成卡已隱藏」措辭 → 改為「帶標籤卡 1 張」）、`:126-127`（`AskCardFallback` 分歧保留）。唔改就會「新政策 vs 舊 pin」對撞。
- **⛔ 唔做**：F1 tool-stack（`logic/JeiLookupAskTool.java:49-57`，未證＋SNBT regression 面）／類別 fallback（`ensureCoreCraft` 回卡物件；`fromVanillaCrafting` `:766` private）／`sameItemDifferentTags`（private＋diagnostic）／F3 措辭 predicate（`AskMissFallback.java:36/43-44` 已 match）。

## §3 驗收（S0–S9，機器可驗）
- **S0**：新 trace 斷言 = **parse `send.user.content` → `.jei` 含 `→` 行**（今日缺席 ⇒ 有鑑別力）。**唔准**寫 `send.user.jei`；**唔准**用 `round` 欄（只有 {1,3}）。
- **S1**：斷 `send.user.content→jei` 出現「**石切器**」提示行。**唔准**用「木棍」（system prompt 已有，45/51）；**唔准**用全 trace 出現次數（`石切器` 1/51 命中＝09-15 卡 digest，F-C 一開即假綠）。
- **S2**：刪（categories 唔會出 `minecraft:crafting`）。
- **S3**：用真非空 catalog 個案 09:13:02／09:13:32／10:36:12（R6 量到行數 4／4／5(16)）→ **實作前用同一次 ask 嘅 `jei` 欄 byte-diff 再量一次**，並 pin 實測值（唔准靠跨 ask token 數）。
- **S4**：`minecraft:bedrock` 負控（包內有 bedrock 腳本 `b_a_d_item.js:307`／`golden_age/events.js:1007` → 人手確認一次）。
- **S5（政策 gate）**：答案**唔准**再出「只是空框架，不是取得方式」式否定（今日 trace rec 53 有此句）；改後須同時含「合成台／無序合成」＋「石切器」＋「木棍」＋「空白模組劍合成」標籤。
- **S6**：石刻問句基準 `10850／11406／11838／12658`（出處 `latest.log:8450/8460/8470/8507`）；prompt 增幅 ≤400 字；ask 耗時貼實測。
- **S7**：**新建** harness `src/test/java/com/skps9/packai/client/gui/JeiSlimRecipeLineCheck.java` ＋ **重生 `tmp-check.gradle`**（新 harness 要註冊）＋ `./gradlew.bat -I tmp-check.gradle runJeiSlimRecipeLineCheck`。
- **S8**：`tests/check_*.py` 共 118；無參數 RC=0；**已知紅樣本必須帶 `--trace "<instance>/packai/trace" --since 20260901` → RC=1**（`ask-20260915-170823-eccentrictome_tome.jsonl:25 purpose_lookup peer has （無官方名）`）；如實記錄，唔准講「全綠」。
- **S9**：新 trace 零 `file:line`／`.js`／內部 id 落玩家文字。

## §4 基線取代（R6 ⑤）
P1 **取代** HANDOFF:16-18 同 09-15 嘅卡抑制驗收：舊基線（`suppressedFrameOnly n=2`、`emitted=0`）**作廢**；**新基線 = 例外下 `n=0` ＋ 帶標籤卡 1 張**。實作時同步更新 HANDOFF＋`REMAINING_WORK`。

## §5 風險／還原
- 檔案（真路徑）：`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`、`logic/AskService.java`、`logic/AskEngine.java`、`logic/ModularFrameCards.java`、`logic/AskToolEnv.java`、`logic/AskCardFallback.java`、`src/test/.../ModularFrameCardsCheck.java`、`tests/check_card_emission_suppression.py`。
- 改動前全部 copy 去 `.hermes/backups/2026-09-17_p1_policy/` ＋ `md5sums.txt`（全部 tracked → `git revert` 亦可）；jar 由 `mc_mod_deploy_jar.py` 自動備份。
- 最壞情況：答案把「空框架」講成唯一途徑 → 用 S5 三件共現 + 「材料版本」標籤擋；影響面＝公開發佈玩家（跨時區）。
- 唔郁：trace 事件名／欄位語義、`PackAiConfig` 預設、`PackIndex`、卡渲染本身。

## §6 Review 記錄
| 輪 | 比分 | 關鍵 |
|---|---|---|
| 1 | 2 : 8 | `:8433` 打死「冇 fact」 |
| 2 | 4 : 6 | fact 冇入 prompt |
| 3 | 3 : 7 | F1 方向唔匹配；S 假綠 |
| 4 | 3 : 7 | F1 路徑錯；SNBT regression |
| 5 | 4 : 6 | F2 可行但撞規則 23 → P1 |
| 6 | 3 : 7 | lang 檔路徑錯／9 處漏；F-C 要改簽名＋3 呼叫點；S0/S1 假綠 |
| 7 | 本檔 | — |
