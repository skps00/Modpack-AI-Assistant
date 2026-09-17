# Plan D v10 — P1 政策：三項實質修改（F-B 收窄 / F-C id-only＋注入鏈 / 補齊清單）

> 狀態：**v10（2026-09-17 15:0x）** — R8 = **5:5**（最接近）。輪次：2:8 → 4:6 → 3:7 → 3:7 → 4:6 → 3:7 → 4:6 → 5:5 → R9（本版）。SK：審到第 10 輪。⛔ 未實作。

## §1 F-B（收窄 — R8 指出按 plan 字面做會污染 13% 問答）
**現況實測**：放寬「含 `→` 即算 claim」→ **22/173（13%）payload 新增 hint 行（54 行）**，加「必須係機器行」守衛只降到 21/173（無效）。受影響個案大多數**同模組件無關**（砂纸／厨锅／消化器／任务书／召唤祭坛／动力合成器…），而且**新行好多係 `role=input`（focus 喺 `→` 左邊）**：例 10:36:12 新加 3 行＝`机器“混合釜”： 银树之心、满溢神恩项链… → 银树树心容器`。呢個同 catalog 政策「role=input 用作材料，不是怎么来」**方向相反**，而 hint 頻道自稱「低信心提示（取得）」＋「（据 JEI）」＝會向模型講錯。

**修改 1（predicate 鎖死）**：放寬只收 **`→` 右側 item 名／id == focus** 嘅行（即「產出 = 焦點物品」），其餘（focus 喺左側、無 `→`）一律照舊唔收。實作前用真 trace 重跑同一複刻腳本，**驗證命中率由 22/173 降到「只含焦點為產出」嘅子集**（目標 ≤3 個 payload；若仍 >5 → 停手報告，唔准開工）。
- **抽純核（理由更正）**：`appendClaimLines(StringBuilder,String,String,int[])`（`client/service/AskService.java:906-973`）**本身已係純函數**；`claimHintsText(...)`（`:870-903`，含 obtain 問句 gate 同 `[TOOLTIP_HINT]` 前綴）**亦係純字串** → **兩個一齊抽出**新檔（例 `logic/ClaimLineRules.java`），`AskService` 只做薄包裝。理由係「harness 載唔到 `AskService`」，唔係「有 MC 型別」。**唔抽 `claimHintsText` ⇒ S10 嘅「非 obtain 問句」分支冇嘢可測。**
- **另兩個必寫嘅交互**：① 3 行 cap **三源共用**、tooltip 源先掃（`:888`）→ purpose 行多時會擠走 JEI 機台行；② prepend 令 `RecipeGetMarks.isEmiPreview`／`isNoRecipeUi`（`startsWith`）失效 → EMI／no-recipe-UI 個案會被當「有 JEI」。兩者要喺 plan 寫明處理方式（①保留現狀但記錄；②把新 mark 放喺 `RecipeGetMarks.strip` **之後**）。

## §2 F-C（predicate 改 id-only ＋ 寫齊注入鏈）
- 例外條件改 **純 id 比對**：`primaryOutputId == focus item id` **且** 該 id 存在 `RecipeType.CRAFTING` 配方（用 `JeiFocusMatch.craftingResultMatches` 只作**判斷來料**，唔喺 `shouldDropFrameCard` 內直接叫 —— 消除「純核心收唔到 `Object recipe`」嘅矛盾）。布林「有 vanilla 配方」由**上層**算好再傳落去。
- `shouldDropFrameCard` 改簽名 → `src/test/java/.../ModularFrameCardsCheck.java` **10 個 4-arg 呼叫**（`:17,18,19,27,29,31,37,45,47,74`）。
- **注入鏈（R8 指出 plan 漏寫，要一次過注入）**：`AskService.java:2636 modularFrameDropId(ItemStack)`（＋`isModularToolFocus`）→ `setModularFrameDropId` → `logic/AskLoopState.java:524-546` → `AskEngine:1616-1617 env.modularFrameDropId`；另 `AskService.java:408`／`:2487` 用 `modularFrameDropId(cardFocus)` 傳入 `AskCardFallback.ensureCards`。
- 真呼叫點：`AskService.java:2654`、`AskToolEnv.java:90-91`（`rejectFrameCard`）。改簽名**唔會**撞 python 硬約束（`check_card_emission_suppression.py:73/76-80` 只 pin 字串）✓。
- `AskCardFallback`（no-tools／keyword path）＝**明示限制**（該檔冇 MC 型別）＋後續工單。

## §3 F-A（逐 key 處理 ＋ 兩個閘）
- 9 處 = 3 檔 × `llm_style`／`llm_style_notools`／`tool_build`（zh_cn `388/389/484`、en_us `392/393/484`、zh_tw `392/393/484`）。**唔可以「zh 尾段 vs EN 中段」二分**：`tool_build` 係獨立「第 23 條」段落（zh 位置 40/317、EN 356/687）→ **逐 key 逐檔**改；zh_cn 同 zh_tw 內容有差（412 key）。
- 政策句位置：zh `llm_style` 97%（尾）、EN 44%（中，Truth ladder 後、`[VARIANT]` 前）。
- **必守 token（閘 1）**：`tests/check_reply_prompt_keys.py:376-392`（`empty-frame`／`empty modular`／`空白模組`繁簡＋ not-this-instance ban；`:391` `"[TOOL_BUILD]" in style` 要留）。
- **必守禁令（閘 2，R8 新發現）**：`tests/check_prompt_notools_no_toolwords.py` —— `llm_style_notools` 內**唔准**出現工具名（`render_recipe_cards`／`jei_lookup`／`jei_info_use`／`jei_info_acquire`／`dump_level`），兩棵樹 × 3 檔。新措辭唔准提名工具。
- 舊行號更正：plan 曾寫 `check_reply_prompt_keys.py:355-387`，真 pin 係 **:376-392**。

## §4 驗收（S0–S11；更正處標 ★）
- **S0**：新 trace `send.user.content` parse → `.jei` 含 **`[TOOLTIP_HINT]`**（今日 173 payload = 0）。唔准用 `→`（84 個已有）；唔准寫 `send.user.jei`；`round` 值域 {1,2,3}。
- **S1**：`.jei` 含「**石切器**」（全 trace 1/51；唯一命中＝09-15 卡 digest）。唔准用「木棍」（45/51）。
- **S2**：刪。
- **S3 ★（單位更正＋門檻）**：三個真非空個案 09:13:02（**417 chars**/4 行）、09:13:32（473 chars/4）、10:36:12（590 chars/5 ＋ 1182 chars/16）—— 單位係 **chars 唔係 B**。門檻：只准新增 `[TOOLTIP_HINT]` 行（≤3 行、每行 ≤120 字），**原有 catalog 行 byte 不變**。
- **S4**：`minecraft:bedrock` 負控（人手確認一次）。
- **S5**：新答案須含「合成台／無序合成」＋「石切器」＋「木棍」＋「空白模組劍合成」；唔准再出「只是空框架，不是取得方式」式否定。
- **S6**：石刻基準 `10850／11406／11838／12658`（`latest.log:8450/8460/8470/8507`）；prompt 增幅 ≤400 字；耗時貼實測。
- **S7**：新 harness `src/test/java/com/skps9/packai/client/gui/JeiSlimRecipeLineCheck.java` ＋**重生 `tmp-check.gradle`**。
- **S8**：118 個 python 檢查；無參數 RC=0；已知紅樣本**必須帶** `--trace "<instance>/packai/trace" --since 20260901` → RC=1。
- **S9**：零 `file:line`／`.js`／內部 id 落玩家文字。
- **S10**：抽核後嘅純函數 headless harness：非模組件／非 obtain 問句 → 唔准產生 `[TOOLTIP_HINT]`（要涵蓋 `claimHintsText` 嘅 obtain gate）。
- **S11 ★**：重跑 12:21 亞巴頓 (`tetra:modular_single`) → 讀 `renderCards suppressedFrameOnly n=`／`cards emitted=`，**新基線 `n=0` ＋ 帶標籤卡 1 張**；並**要驗 `shouldSkipAutoEmit` 喺 n=0 之後嘅行為**（唔再 skip → 會唔會多出一張卡）。舊基線作廢（`HANDOFF.md:18`、`docs/plans/2026-09-15_card-attribution-and-suppression.md:31-32`）。本 repo 冇 `REMAINING_WORK.md`。

## §5 改動清單（成品）
- 語言：`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`（9 處）。
- 程式：`client/service/AskService.java`（`:870-903`＋`:906-973` 抽出、`:2636`、`:408`、`:2487`、`:2654`）、新 `logic/ClaimLineRules.java`、`logic/AskEngine.java`（`:1616-1617`、`:782`、`:783-786`、`:815`）、`logic/ModularFrameCards.java`、`logic/AskToolEnv.java:90-91`、`logic/RenderRecipeCardsAskTool.java:170/172`、`logic/AskLoopState.java:524-546`。
- 測試：`src/test/.../ModularFrameCardsCheck.java`（10 處）、`tests/check_card_emission_suppression.py:92-94/104-107/110-111/126-127`（`:93-94` 禁 `equalsIgnoreCase`）、`tests/check_ask_card_fallback.py:1369-1374`、`tests/check_maintenance_intent.py:72/77`、`tests/check_reply_prompt_keys.py:376-392`、`tests/check_prompt_notools_no_toolwords.py`。
- 還原：改動前 copy 去 `.hermes/backups/2026-09-17_p1_policy/` ＋ `md5sums.txt`；jar 由 `mc_mod_deploy_jar.py` 自動備份。

## §6 Review 記錄
| 輪 | 比分 | 關鍵 |
|---|---|---|
| 1–4 | 2:8／4:6／3:7／3:7 | 根因三度收窄 |
| 5 | 4:6 | F2 撞規則 23 → P1 |
| 6 | 3:7 | lang 9 處；F-C 簽名 |
| 7 | 4:6 | F-B 親證；S0 token 錯 |
| 8 | 5:5 | F-B 污染 13%；F-C 注入鏈漏；漏閘 2 個 |
| 9 | 本檔 | — |
