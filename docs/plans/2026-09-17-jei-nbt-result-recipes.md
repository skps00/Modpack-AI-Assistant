# Plan D v11 — P1 政策（最終收窄）：只改「政策文字＋提示行」，**剔出卡抑制改動**

> 狀態：**v11（2026-09-17 15:2x）** — R9 = 4:6。輪次：2:8／4:6／3:7／3:7／4:6／3:7／4:6／5:5／4:6 → **R10（本版，第 10 輪＝SK 上限）**。⛔ 未實作。

## §0 本版最大改動：**F-C 整個剔出**
R9 揭到：12:21 亞巴頓 trace `check.cards` 有 `category='Crafting', primaryOutputId='tetra:modular_single'` → **該 id 真係有 crafting 配方** ⇒ 照原 F-C clause 佢仍然會被 drop ⇒ 原 S11「`n=0`＋帶標籤卡 1 張」**永遠達唔到**（逼人造綠），而 F-C 又要改簽名＋傳播鏈＋測試 pin。
**決定**：P1 要求嘅係**答案文字**（要講「合成台：切石機＋木棍 → X」），**唔係卡**。⇒ **維持現有卡抑制不變**，F-C／簽名／`AskCardFallback` 限制全部唔做。風險面大幅收窄。

## §1 F-A 政策文字（9 處；逐 key 逐檔）
- 檔：`forge/1.19.2/src/main/resources/assets/packai/lang/{zh_cn,en_us,zh_tw}.json`；key×行號：zh_cn `388/389/484`、en_us `392/393/484`、zh_tw `392/393/484`（R6–R9 逐檔核對 ✓；只准 forge 樹——`neoforge` 停擺，唔改亦唔會紅：`check_dual_tree_sync.py` 只掃 `src/main/java` 且 pause 模式 WARN；`check_howto_get_label_parity.py` 只比兩個 key）。
- 政策句位置：zh `llm_style` 97%（尾）；EN 44%（中，Truth ladder 後、`[VARIANT]` 前）；`tool_build` 係獨立「第 23 條」段落（zh 40/317、EN 356/687）→ **三個 key 逐個處理，唔可以二分**。
- 新措辭：若某工具喺合成台有無序合成配方（例：切石機＋木棍 → 石刻）→ **要照講**，並標明係「空白模組劍合成／該材料版本」；**保留** `tests/check_reply_prompt_keys.py:376-392` 要求嘅 token（`empty-frame`／`empty modular`／`空白模組`繁簡＋ not-this-instance ban；`:391` `"[TOOL_BUILD]" in style`）。
- **唔准**喺 `llm_style_notools` 提任何工具名（`tests/check_prompt_notools_no_toolwords.py`：`render_recipe_cards`／`jei_lookup`／`jei_info_use`／`jei_info_acquire`／`dump_level`，2 棵樹 × 3 檔）。
- ⚠️ **唔准剷走現有「切石机＋木棍」句**（S5 靠佢；要同 S5 一齊鎖）。

## §2 F-B 提示行（predicate 已收窄；門檻用污染指標）
- 改動：`client/service/AskService.java:870-903 claimHintsText` ＋ `:906-973 appendClaimLines` **兩個一齊**抽出新檔（例 `logic/ClaimLineRules.java`，headless），`AskService` 只做薄包裝（`PackAiMod.LOGGER.info` 留喺薄包裝）。
- **predicate 鎖死**：只收 **`→` 右側 == focus 名／id** 嘅行（focus 要入新簽名——現行簽名冇 focus，唔加就實作唔到）；focus 喺左側／無 `→` 一律唔收。
- **門檻（更正 R9：舊門檻自相矛盾）**：改用**污染指標** —— **「focus 只喺 `→` 左側嘅新行」必須 = 0**。R9 實測：relaxed 22/173（54 行）→ narrow 14/173（25 行）／嚴格解讀 10/173（18 行），差集 16 payloads／29 行**全部係左側污染**（砂纸／厨锅／消化器／任务书／召唤祭坛→创世纪／动力合成器→使徒残片／上帝方块…）→ 收窄後要全清；narrow 剩返嘅 14／10 個 payload 全部係「focus == 產出」嘅正確行（例 `机器“奥术铁砧”： … → 寰宇支配之剑`、`[16 slots] 动力合成器： 无尽锭×16 → 寰宇之刃`）。
- **payload 分母定義（寫死）**：173 = `send.user.content` 可 JSON.parse 者（224 條 `send.user` − 51 條純問題字串）。
- 另兩個交互（照寫）：① 3 行 cap 三源共用、tooltip 源先掃（`:888`）→ purpose 行多會擠走機台行（保留現狀並記錄）；② prepend 令 `RecipeGetMarks.isEmiPreview`／`isNoRecipeUi`（`startsWith`）失效 → 新 mark 要放喺 `RecipeGetMarks.strip` **之後**。

## §3 驗收（S0–S11；★＝本版更正）
- **S0**：新 trace `send.user.content` parse → `.jei` 含 **`[TOOLTIP_HINT]`**（今日 173 payload = 0）。唔准用 `→`（84 個已有）；唔准寫 `send.user.jei`；`round` 值域 {1,2,3}。
- **S1**：`.jei` 含「**石切器**」（全 trace 1/51）。唔准用「木棍」（45/51）。
- **S2**：刪。
- **S3 ★**：三個真非空個案 09:13:02（417 **chars**/4 行）、09:13:32（473/4）、10:36:12（590/5＋1182/16）；門檻＝只准新增 `[TOOLTIP_HINT]` 行（≤3 行，**內容 ≤120 字**——emit 行本身必然 >120 字，係 `"- " + 內容 + "  （据 <source>）"`），原有 catalog 行 byte 不變。
- **S4**：`minecraft:bedrock` 負控（人手確認一次）。
- **S5**：新答案須含「合成台／無序合成」＋「石切器」＋「木棍」＋「空白模組劍合成」；唔准再出「只是空框架，不是取得方式」式否定。
- **S6**：石刻基準 `10850／11406／11838／12658`（`latest.log:8450/8460/8470/8507`）；prompt 增幅 ≤400 字；耗時貼實測。
- **S7**：新 harness `src/test/java/com/skps9/packai/client/gui/JeiSlimRecipeLineCheck.java` ＋重生 `tmp-check.gradle`。
- **S8**：118 個 python 檢查；無參數 RC=0；已知紅樣本**必須帶** `--trace "<instance>/packai/trace" --since 20260901` → RC=1。
- **S9**：零 `file:line`／`.js`／內部 id 落玩家文字。
- **S10 ★（加正控）**：純核 harness —— **正控**：真 trace 行 `机器“奥术铁砧”： … → 寰宇支配之剑`（focus = 產出）→ 必須收；**負控**：`机器“混合釜”： 银树之心… → 银树树心容器`（focus 喺左側）→ 必須唔收；另「非 obtain 問句」→ 唔出 hint。
- **S11 ★（改為回歸閘）**：12:21 亞巴頓 (`tetra:modular_single`) 重跑 → 卡抑制**維持** `suppressedFrameOnly n=2`／`cards emitted=0`（P1 唔郁卡路徑；舊基線因此**唔作廢**，只作回歸比對）；`shouldSkipAutoEmit` 行為**不得改變**。

## §4 改動清單（成品）
- 語言：`assets/packai/lang/{zh_cn,en_us,zh_tw}.json`（9 處；只 forge 樹）。
- 程式：新 `logic/ClaimLineRules.java`（由 `AskService:870-903`＋`:906-973` 抽出）＋ `client/service/AskService.java` 薄包裝（log 留原位）。
- 測試：新 harness `JeiSlimRecipeLineCheck.java`（S10 正/負控）＋重生 `tmp-check.gradle`。
- **唔郁**：`ModularFrameCards`／`AskLoopState`／`AskToolEnv`／`AskCardFallback`／`RenderRecipeCardsAskTool`／所有卡抑制 pin（`check_card_emission_suppression.py`、`check_ask_card_fallback.py`、`check_maintenance_intent.py`）／`RecipeGetMarks`。
- 還原：改動前 copy 去 `.hermes/backups/2026-09-17_p1_policy/` ＋ `md5sums.txt`；jar 由 `mc_mod_deploy_jar.py` 自動備份。

## §5 Review 記錄
| 輪 | 比分 | 關鍵 |
|---|---|---|
| 1 | 2:8 | `:8433` 打死「冇 fact」 |
| 2 | 4:6 | fact 冇入 prompt |
| 3 | 3:7 | F1 方向唔匹配 |
| 4 | 3:7 | F1 路徑錯 |
| 5 | 4:6 | F2 撞規則 23 → P1 |
| 6 | 3:7 | lang 9 處；F-C 簽名 |
| 7 | 4:6 | F-B 親證；S0 token 錯 |
| 8 | 5:5 | F-B 污染 13%；注入鏈漏 |
| 9 | 4:6 | 門檻自相矛盾；F-C 傳播鏈欠；S11 達唔到 → **v11 剔 F-C** |
| 10 | 本檔 | — |
