# Plan D v9 — P1 政策（模組件合成台配方入答案）：併入 R7 六條裁決

> 狀態：**v9（2026-09-17 14:4x）** — R7 4:6。輪次：2:8 → 4:6 → 3:7 → 3:7 → 4:6 → 3:7 → 4:6 → R8（本版）。SK：審到第 10 輪。⛔ 未實作。

## §0 事實（R7 更正後）
1. 語言檔：`forge/1.19.2/src/main/resources/assets/packai/lang/{zh_cn,en_us,zh_tw}.json`；`llm_style`／`llm_style_notools`／`tool_build` 行號 zh_cn `388/389/484`、en_us `392/393/484`、zh_tw `392/393/484`（全部經 R7 逐行核對 ✓；全 510 key 只有呢 9 處帶 carve-out）。
 - **EN `llm_style` 嘅政策句喺中段**（Truth ladder 之後），zh 版兩 key 係**尾段** → 兩者要分開改，唔可以當同形。
 - **必守 token（否則既有閘紅）**：`tests/check_reply_prompt_keys.py:355-387` 對**兩棵樹**（含 neoforge）斷 `tool_build` 必須含 `empty-frame`／`empty modular`／`空白模組`（或繁/簡體）＋「not-this-instance obtain ban」token → 新措辭要保留呢批 token。
2. 12:24 log（Big5）：`:8433` 真行 `  - 机器“动力搅拌器、工作盆”： 石切器、木棍 → 石刻`；`claimHints obtainish=true src=221/375/0 **out=0**`（因現行要求「获得／取得」）。
3. trace 統計（R7 實測 51 條）：`send.user` payload 173 個，93 個有 `jei`，**84 個已含 `→`**，**`[TOOLTIP_HINT]` = 0 個** → 真正零出現嘅 token 係 `[TOOLTIP_HINT]`（唔係 `→`）。`round` 值域 = **{1,2,3}**。
4. F-B 鏈路（R7 端到端親證）：`client/service/AskService.java:276-288`（`jei = claimHints + "\n" + jeiRaw`）→ `AskEngine:309 recipeGetClean` → `:727 recipeGetCleanForLlm` → `:782 tooltipHintBlock`（本體 `:1448-1457`；`:1461-1465` 係 `recipeCardsCatalogSlim`）→ catalog 空時 `:783-786` 回 `pre` → tools path `AskEngine:815` 呼叫 `jeiForLlmSlim()` → `LlmClient:439-441` 非空才 `user.put("jei", …)`。模擬真行：放寬後 `claim=True`、`looksRow=True`、ship 出 `- 机器…石切器、木棍 → 石刻 （据 JEI）`＝28 字（<120 截斷）✓。
5. F-C 實況（R7 推翻舊寫法）：`shouldDropFrameCard(String,String,boolean,boolean)`（`logic/ModularFrameCards.java:18-34`，純字串）。**真呼叫點**：`AskService.java:2654` ✓、`AskToolEnv.java:90` ✓；`AskCardFallback.java:448` **唔係**呼叫佢，而係本檔私有 `isFocusFrameOutput`（`:451-459`）—— 該檔**只 import java.util/regex、冇 MC 型別、冇 recipe／level** ⇒ **呢條 path 根本無法得知有無 vanilla 配方**。`JeiFocusMatch.craftingResultMatches(Object,ItemStack)` `public` ✓（`:231`；alias `:275`）。

## §1 決定（SK P1）
模組件若有 vanilla 合成台配方 → 答案要講，並以包原文標明「材料版本／空白模組劍合成」。

## §2 修法
- **F-A 政策文字（9 處，3 檔）**：zh 尾段 2 key＋EN 中段 1 key 分開改；保留 §0.1 必守 token；zh/en/zh_tw 三檔一致（否則 tools 與 no-tools path 答案分歧）。
- **F-B（選 (b)，已親證）**：放寬 `client/service/AskService.java:906-972 appendClaimLines` 令含 `→` 嘅機器行算 claim → 落 `[TOOLTIP_HINT]`。**唔用 (a)**（`AskEngine:1461` 註解明文反設計）。
 - **必須抽純核**：`appendClaimLines` 係 `AskService` 內 `private static`＋帶 MC 型別 → 負控要可跑就**要把判斷抽成 headless 純函數**（新檔，例如 `logic/ClaimLineRules.java`），`AskService` 只做薄包裝。
- **F-C 抑制例外（重寫）**：
 - 例外條件：該模組件**存在 `RecipeType.CRAFTING` 配方**（item-id 匹配、忽略 NBT；用 `JeiFocusMatch.craftingResultMatches`）。
 - 覆蓋：`AskService.java:2654`、`AskToolEnv.java:90`（都要傳入預先算好嘅「有 vanilla 配方」布林值）；`shouldDropFrameCard` **改簽名** → `src/test/java/.../ModularFrameCardsCheck.java` **10 個 4-arg 呼叫**（`:17,18,19,27,29,31,37,45,47,74`）要全改（否則 `compileTestJava` 紅）。
 - **`AskCardFallback`（no-tools／keyword path）＝明示限制**：該檔無 MC 型別、拿唔到配方資訊 → **本版保留其現行 `isFocusFrameOutput` 行為**，並在 plan 寫明「P1 喺 no-tools path 未生效（只影響 HTTP 400／非 tool LLM 情況）」＋列後續工單。**刪去舊版「3 呼叫點全覆蓋否則分歧」嘅自相矛盾講法。**
 - **改動清單（R7 補齊）**：`RenderRecipeCardsAskTool.java:170`（`suppressedFrameOnly` log）＋`:172`（玩家可見字串「框架合成卡已隱藏（非本工具取得途徑）」→ 改「帶標籤卡」措辭）、`logic/AskLoopState.java`（`noteSuppressedFrameOffer`／`shouldSkipAutoEmit`／`modularFrameDropId`）、`tests/check_card_emission_suppression.py:92-94`（`:93-94` 硬約束：suppress body **唔准**出現 `equalsIgnoreCase`）／`:104-107`／`:110-111`／`:126-127`、`tests/check_ask_card_fallback.py:1369-1374`、`tests/check_maintenance_intent.py:72/77`、`tests/check_reply_prompt_keys.py:355-387`。
- **⛔ 唔做**：F1 tool-stack（`logic/JeiLookupAskTool.java:49-57`）、類別 fallback（`fromVanillaCrafting` `:766` private）、`sameItemDifferentTags`、F3 措辭 predicate（`AskMissFallback.java:36/43-44` 已 match）。

## §3 驗收（S0–S11）
- **S0（改判準）**：新 trace `send.user.content` parse 後 `.jei` 含 **`[TOOLTIP_HINT]`**（今日 173 payload 中 **0** ⇒ 有鑑別力）。**唔准**用 `→`（今日 84 個已有）；**唔准**寫 `send.user.jei`（事件頂層冇；`AskTrace.java:747`）；`round` 值域係 {1,2,3}。
- **S1**：`.jei` 提示行含「**石切器**」（全 trace 1/51；唯一命中係 09-15 卡 digest）。唔准用「木棍」（45/51）。
- **S2**：刪。
- **S3（加門檻）**：三個真非空個案 09:13:02（417B/4 行）／09:13:32（473B/4）／10:36:12（590B/5＋1182B/16）；**預期 delta**：只准「新增 `[TOOLTIP_HINT]` 行（≤3 行、每行 ≤120 字）」，**原有 catalog 行 byte 不變**；超出即紅。
- **S4**：`minecraft:bedrock` 負控（人手確認一次，包內有 bedrock 腳本）。
- **S5**：新答案須同時含「合成台／無序合成」＋「石切器」＋「木棍」＋「空白模組劍合成」標籤；**唔准**再出現「只是空框架，不是取得方式」式否定。
- **S6**：石刻基準 `10850／11406／11838／12658`（`latest.log:8450/8460/8470/8507`）；prompt 增幅 ≤400 字；耗時貼實測。
- **S7**：新建 harness `src/test/java/com/skps9/packai/client/gui/JeiSlimRecipeLineCheck.java` ＋**重生 `tmp-check.gradle`**（新 harness 要註冊）。
- **S8**：118 個 python 檢查；無參數 RC=0；已知紅樣本**必須帶** `--trace "<instance>/packai/trace" --since 20260901` → RC=1（`ask-20260915-170823-eccentrictome_tome.jsonl:25 purpose_lookup peer has （無官方名）`）。
- **S9**：零 `file:line`／`.js`／內部 id 落玩家文字。
- **S10（新・負控，R7 ⑤）**：對抽核後嘅純函數落 headless harness：非模組件／非 obtain 問句 → **唔准**產生 `[TOOLTIP_HINT]` 行（今日必然 0；放寬後仍要 0）。
- **S11（基線，R7 ⑥）**：重跑 12:21 亞巴頓 (`tetra:modular_single`) 同一問句 → 讀 `renderCards suppressedFrameOnly n=` 同 `cards emitted=`（新基線 `n=0` ＋ 帶標籤卡 1 張）；舊基線 `n=2`／`emitted=0` 作廢（`HANDOFF.md:18`、`docs/plans/2026-09-15_card-attribution-and-suppression.md:31-32`）。本 repo **冇** `REMAINING_WORK.md` → 同步目標只有 `HANDOFF.md`。

## §4 風險／還原
- 檔案：lang ×3、`client/service/AskService.java`、`logic/AskEngine.java`、`logic/ModularFrameCards.java`、`logic/AskToolEnv.java`、`logic/RenderRecipeCardsAskTool.java`、`logic/AskLoopState.java`、新純核檔、測試 ×5（`ModularFrameCardsCheck.java`＋4 個 python）。
- 改動前 copy 去 `.hermes/backups/2026-09-17_p1_policy/` ＋ `md5sums.txt`（全部 tracked → `git revert` 亦可）；jar 由 `mc_mod_deploy_jar.py` 自動備份。
- 已知未覆蓋：no-tools／keyword path（`AskCardFallback`）維持舊行為；影響面＝公開發佈玩家。

## §5 Review 記錄
| 輪 | 比分 | 關鍵 |
|---|---|---|
| 1 | 2 : 8 | `:8433` 打死「冇 fact」 |
| 2 | 4 : 6 | fact 冇入 prompt |
| 3 | 3 : 7 | F1 方向唔匹配 |
| 4 | 3 : 7 | F1 路徑錯 |
| 5 | 4 : 6 | F2 撞規則 23 → P1 |
| 6 | 3 : 7 | lang 9 處；F-C 簽名 |
| 7 | 4 : 6 | F-B 親證 ✓；F-C 第 3 呼叫點認錯；S0 揀錯 token；負控未落地 |
| 8 | 本檔 | — |
