- **取得途徑缺口實例（09-21 07:2x，SK 真機問 `witherstormmod:withered_nether_star`）**：答案只列 2 條合成，並寫「掉落表／釣魚／交易／腳本索引都沒有取得路徑」→ **誤導**。真因：① mod **冇 wither_storm loot table**（jar 內 entities 只有 sickened_*＋withered_symbiont）⇒ 掉落係 **Java code 實作**，JSON 索引結構性睇唔到；② **但 JSON 有成就檔**：`advancements/main/wither_storm_defeated.json`（criteria=`inventory_changed` 取得此物品，zh 標題「此波平，彼浪起。」／描述「一劳永逸地摧毁凋灵风暴！」）⇒ 資料本身已可推出「打王掉落」。packai 掃 advancement 只做 `consume_item`（用途側），**取得側未接入**。建議修：A 取得側接入 `inventory_changed` 成就 ＋ B 措辭誠實化（JSON 冇 ≠ 遊戲冇，要講「可能由 mod 程式碼實作」）。（等 SK 決定開唔開 plan）
- **記帳調查（09-21 06:4x，Hermes 親做）**：`tokens delta=0` 根因＝**driver 讀 local 日期 vs 帳本用 UTC key**（非產品 bug）；證據：6 行 `Pack AI usage billed=` 合計 320,813／帳本 mtime 06:20:33；driver 已修。真 instance 端到端證實＝SK 玩一次後睇有冇新 `2026-09-20` key。
- **沙盒真機一輪（09-21 06:17，FTB，同源 flagged jar `autotest-dev-0.2.3.jar`）**：12 cases（10 隨機 craft＋`iron_ore` 控制＋`bedrock` 負控）→ **ok=6**、NO_SAMPLE 6（全部係非 JEI 輸出物品，符合預期）。
  證據：6 條真 trace（`model.reply*` 4–6 輪、`cardsOut` 2–8、body 1.1–2.4k 字真答案）；內部欄位外洩掃描 **CLEAN**；`latest.log` **packai 零例外**（P0 嵌套 icon 崩潰唔復現、零 `Query failed`）；焦點 before/after 都係 Discord＝**零搶**；⚠️ token 記帳仍 **delta=0**（舊未解 ①）。
  證據目錄：`%TEMP%\autotest_results_20260921-061710`（trace＋status＋latest.log）。
- jarvis-pc 線（09-21 06:0x）：MC repo `c1fe2fd` 已 push（remote 核實一致）；packai 最新 build `30d70e2f707d` 已部署真 instance（backup `06b5b129a114`）；沙盒真機一輪（FTB，同源 flagged jar `autotest-dev-0.2.3.jar`，12 cases 隨機抽＋bedrock 負控）因 SK 用機自動延後到 idle＋DS 離峰才跑。
- 乙線實測結論（09-21）：1.19.2 專用伺服器**唔同步** worldgen feature registry（regPlaced=-1 + IllegalStateException；單人 545）⇒ 乙只可單人／LAN，睇 docs/plans/2026-09-21-registry-multiplayer-result.md
<!-- STATE:BEGIN -->
## STATE（五元素；每次改寫，唔 append；≤2,500 tokens）
- **目標**：packai（`super_minecraft_AI_player`，MC 1.19.2 Forge client-side）＝**通用**整合包 AI 助手（唔為單一包硬編碼）。方向（SK 10-05）：由「問單件物品」升級到**包級顧問**（倍化／科技樹／能源／流體／材料／裝備／維度／自動化）。SK 10-10 追加：融入 MC 物品調查 SOP ⇒ **能力反查**（「包入面邊樣嘢可以做到 X」）做齊 A/C/B 三個落點。
- **現狀（2026-10-10 04:5x 改寫；全部 Hermes 親核）**：
  - **① keybind（覆蓋缺口第 1 項）＝真機驗收 PASS ＋ 已 commit ＋ 已 push**（`0c778c6` 程式／`c72f1a2` 文件；`aa1b98f..c72f1a2`）。三輪沙盒 run 共 13 條 question case：**run3 3/3 OK**（`动力鞘翅推进器 → 空格 ⚠撞鍵`／`向左移动 → A`／`跳跃 → 空格`）、run2 4/5（唯一失敗條就係 v5.3 修嘅路由錯）、body 零 raw 外洩、零搶焦點、`cases.json` 無殘留。修嘅兩個真缺陷：模型只傳 `item/machine`（v5.2 加參數兼容）＋泛用 token「key」命中 235/273 行（v5.2 加 40% 覆蓋率剔除）；v5.3 加 DESC 路由指引。
  - **② P0 剩餘量測（B）**：`docs/research/2026-10-10-p0-remaining-measurements.md` — ⑤真機載入（JEI plugin callback ≈1.7 s、JEI→世界開完 13.6 s、cache 37 MB；**冷啟動掃描未量**）＋⑥tool 選擇（路由 4/4 正；**實測 9.09 萬 tokens／ask**，比原錨高約 2 倍）＋④catalyst **未量**（設計已寫，待批 instrumentation）。
  - **③ A（離線能力索引）**：plan `docs/plans/2026-10-10-capability-index-offline.md` v2；review **R1 4:6 → R2 6:4**（未達 8:2；R2 兩項數字錯已修）；R3 delta 抽核跑緊。**實作未派**（依 SK「≥8:2 才開工」規則）。
  - **④ C（promo 封面＋分發包）**：`cover_1280x720.png`（真機截圖＋大字）＋`DISTRIBUTION.md`＋`COMPLIANCE-CHECKLIST.md`（已 push）。
  - **⑤ D（promo shot 1／6 錄製）**：**未做**（要 SK 收機）。
  - **測試範圍**：只 Forge 1.19.2（`neoforge/1.21.1` PAUSED）。Baseline：compile ✅、harness **60/60**、python **125 檔 1 已知紅**（`check_ask_display_leak` 需真機 log）。
  - **環境**：沙盒 `packai_sandbox`／`_atm8`（`_startech` jar 舊）＋真 instance `AI_test_NFWC_DIM`；部署唯一途徑 `mc_mod_deploy_jar.py`。
- **唔准郁**：卡落位（`RecipeEmbed`／`RecipeCard`）／`AskEngine` capable 清空語意／`neoforge` 樹／hot-copy jar／真 instance 自動部署／`AGENTS.md`（要 SK go）／猜數字（禁自創乘數）／keybind 已驗嘅評分規則。
- **未解**：① **A 實作未派**（review 6:4 <8:2；R3 抽核結果出即要 SK 一句「開工／唔開」）② P0 ④ catalyst 覆蓋率要遊戲內 instrumentation（未批）③ ⑤ 冷啟動掃描時間未量 ④ D promo 錄片要 SK 收機 ⑤ keybind 天花板：簡體／其他語言 label 對唔到繁體問句 ⑥ 題庫無 harness。
- **下一步**：① 等 R3 → 若 ≥8:2 就派 cursor 實作 A，否則附停手報告交 SK ② B 剩項（catalyst instrumentation）等批 ③ D 等 SK 收機 ④ 語音／mic 線仍 HOLD。
- **歸檔索引**：`plans/archive/HANDOFF-2026-09.md`（2026-10-10 再搬 09-15～09-19 共 8 個 section 入去；早前 ≤09-13 已搬）；主檔現 200 行（>400 先再歸檔）。
<!-- STATE:END -->

## 2026-10-10 過夜（Discord；SK 睡前派工 `1a 2a 3 A→C→D 4c` → 全部 Hermes 親跑）
- **keybind 真機驗收＝PASS**（今晚第 1 項）：沙盒 `packai_sandbox`（NFWC 231 mod）跑 **3 輪共 13 條** question case。**run1（10-09 code）揭 2 個真缺陷**：① 模型只傳 `item`／`machine`（唔傳 `query`）⇒ 查詢接唔到，dump 全部行；② 泛用 token「key」經 rawKey 命中 **235/273 行（≈86%）**。→ cursor v5.2（參數兼容 `query|machine|item|問句`＋40% 覆蓋率剔除＋空 query 改回 miss＋指引）。**run2 4/5**：`向左移动→a ⚠撞鍵`／`打开装饰盔甲栏→（未綁）`／`跳跃→空格`＋負控老實答冇資料；**唯一失敗**＝模型當「动力鞘翅推进器」係物品，狂叫 `item_search` 9 輪（body「答句生成失败」）。→ cursor v5.3（只改 `DESC` 加路由指引）。**run3 3/3 OK**（含重測嗰條：`动力鞘翅推进器 → 空格 ⚠撞鍵`）。
- **驗收機械面**：3 輪全部 `status=OK`、body 零 raw 外洩（CLEAN）、**零搶焦點**（foreground before/after 都係 `steamwebhelper`；窗用 PID 認法搬去副螢幕 `on_target_monitor=true`）、跑完 `cases.json` 無殘留、沙盒 trace／logs 已還原。成本：run1 **90.9k tokens／ask** → run2 43.4k → run3 33.4k（rounds 由 9 降到 2–5）。
- **Hermes 自跑閘**：`compileJava` RC=0；harness **60/60 GREEN**（`run_harness_checks.py`，53 s）；python 閘 **125 PASS／1 已知紅**（`check_ask_display_leak` 需真機 log）；jar 內容親核（`Call this FIRST`／`filterGenericTokens`／`GENERIC_TOKEN_COVERAGE_PCT` 都在 `KeybindAskTool.class`；sha `9a885c413edb`）。
- **Commit／push（SK 已批 1a／2a）**：`0c778c6` feat(keybind)（7 檔 code／test＋`code_change_log.md`）＋`c72f1a2` docs（A plan、P0 量測、promo 封面／分發）→ **已 push `aa1b98f..c72f1a2`**（push 前 secrets 掃描 12 檔 CLEAN；冇 `git add -A`；`logs/` 冇入）。
- **B（P0 剩餘量測）報告**：`docs/research/2026-10-10-p0-remaining-measurements.md` — ⑤ 真機：JEI plugin callbacks 合計 **≈1,715 ms**、JEI ENABLED→世界開完 **13.6 s**、cache **37 MB**（item-index 26 MB）；**冷啟動掃描未量**（本輪 index 讀 disk cache，唔准當已量）。⑥ tool 選擇：**4/4 按鍵問題第一個 tool 就叫 `keybind_lookup`**（加第 15 個 tool 冇令路由變差）。④ catalyst 覆蓋率 **未量**（要遊戲內 instrumentation；測量設計已寫落報告等批）。
- **C（promo 封面＋分發包）**：`docs/promo/cover_1280x720.png`（真機 frame＋大字，三輪視覺檢查、已裁走 windowed 標題列）＋`DISTRIBUTION.md`（三渠道文案＋**CTA 核實表**：GitHub 200 ✅／CurseForge 403（Cloudflare 擋自動化，存在性依據 `PUBLISH.md`）／Modrinth **頁面未開**（API 404））＋`COMPLIANCE-CHECKLIST.md`（各平台 AI 披露逐項）。
- **A（離線能力索引）＝plan 過咗 2 輪 review 但未達 8:2**：`docs/plans/2026-10-10-capability-index-offline.md`。R1 **4:6**（兩個獨立 reviewer：反方＋數字核實方；4 條指控我逐條親核成立 → v2：A6 改 baseline-diff、`runtime_visible` 按真 runtime surface 重定義（1.12.2 `.lang` runtime 唔讀）、A2 只留我 javap 親核嘅 5 項（270／256／`getMaxStackSize()==1`／lang 兩句／enum 建構 `true,true`）、加 javap 全 run 1500 次上限）。R2 **6:4**（4 項已解決；另捉 2 個數字錯已即修：javap 算術、DJ2 jar 數 248→235 active）。R3 delta 抽核跑緊。**依 SK「≥8:2 才開工」規則：實作未派工**（cursor 指令已備好）。
- **D（promo shot 1／6 錄製）＝未做**（要 SK 收機；gameplay 仍由 SK 自己錄）。
- **等 SK 一句**：A 實作開唔開工（附停手報告：R1 4:6→R2 6:4、卡住嘅係「查詢表人手策展令 javap 證據可能變裝飾」＋「recall 同通用性嘅張力」、最貴未知＝5 個真 pack 上嘅 precision、建議＝照做但先只跑 DJ2 一條 query 做驗證）。

## 2026-10-10（keybind slice：4 輪 review → 停手 → SK 批工具路線 → cursor 實作 v5＋Fix 9；真機已驗 PASS）
- **覆蓋缺口次序第 1 項「按鍵查詢」**：plan `docs/plans/2026-10-10-keybind-lookup.md`（v1→v4＋停手報告；commits `df3212a`／`156f688`／`53a471c`／`fb80af4`／`5a506d5`）。4 輪獨立 review：**R1 3:7 → R2 4:6 → R3 5:5 → R4 6:5**（上限用盡 → 停手交 SK）。
- **我逐條核實反方指控（全部成立）**：① `AskToolLoop.java:39-42 CAPABLE_TOOLS`＋`:44 ALLOWLIST`＋`:141-143` 靜默丟棄（唔加白名單＝新 tool 永遠唔會出現）② `tests/check_tool_schema_stable.py:108 assert forge_only == ["knowledge_lookup"]`（加第二個 Forge-only tool 即硬 FAIL）③ 加 tool 會令**每次 capable round**多一個 tool schema（`AskToolLoop:401`／`:550` → `LlmClient:511 nativeToolsSchema`）④ `AskEngine.java:396`／`:422-425` early return 早過 FACT 區 `:571`（按鍵問題最易中）⑤ `options.txt` 離線 oracle 同 runtime 語義唔同。
- **SK 決定**：保留**工具路線**。我原先寫「現有 tool 都要 item id」係**錯**（已收返：`item_search`／`guide_fetch` 只要 query 字串，Ask 面板可空手問）。guard-test 例外集合我原本要 SK 批，改設計後**唔再需要**（v5 直接擴充例外＋註解）。
- **cursor-agent v5（hidden 派工、零彈窗）**：新 `client/context/KeybindReader.java`（live `options.keyMappings`；ALL 係 private 唔用）＋`logic/KeybindAskTool.java`（第 15 個 tool：查功能→按鍵／撞鍵／未綁）＋`AskToolLoop` 白名單＋`QUERY_TOOLS`＋`AskEngine:50` 註冊＋`PackIndex.java:1155-1176 isKeybindQuestion`（7 族規則）＋兩條 early-return guard 加 `&& !isKeybindQuestion`＋`check_tool_schema_stable` 例外＋`tests/check_keybind_lookup.py`。
- **Fix 9（我 review 後捉到嘅真問題）**：用 201 條真 keybind 資料實測，原過濾**真實問句 0 命中／打「R」回 104 條垃圾** ⇒ 改評分＋排序（key 全等 +100／rawKey +80／label 含 token +10×len／namespace +20）＋0 命中回**事實統計行**＋描述引導模型傳啱參數。
- **Hermes 自驗（唔信 agent 自報）**：compile RC=0；python **125 PASS／1 FAIL（已知 baseline）**；jar 內容核（`KeybindAskTool`／`KeybindReader`／`keybind_lookup`／`AskEngine` 註冊／`isKeybindQuestion` 全在，316 class）；mirror test 讀真 artifact；pass2 用 javap 證 Forge `getTranslatedKeyMessage()` 會叫 `KeyModifier.getCombinedName()` ⇒ 撞鍵分組唔會假警報；讀 live 按鍵有前例（`GameContextCollector`）。
- **未 commit 嘅 code（等真機驗收）**：`logic/KeybindAskTool.java`（新）／`client/context/KeybindReader.java`（新）／`tests/check_keybind_lookup.py`（新）／`AskEngine.java`／`AskToolLoop.java`／`PackIndex.java`／`tests/check_tool_schema_stable.py`。實作規格入咗 repo：`docs/plans/2026-10-10-keybind-impl-instructions.md`（＝派工原文；就算 code 唔見都可以原樣重派）。
- **SOP 融入（SK 追問「合埋落去邊個位」）**：`docs/plans/2026-10-10-capability-search-fit.md`（`00fb127`）＝3 落點（A 離線能力索引／B `capability_search` tool／C 來源分級政策）＋老實話：**bytecode 取證 runtime 做唔到**。SK 覆「三個都做，次序你定」→ **A → C → B**。
- **SOP 實例核實（DJ2 JSU）**：數字全對（`sipush 270`／`sipush 256`／lang 原文），但**SOP 指令路徑錯**（真 package `fi/dy/masa/enderutilities/...`、wrapper 係內層 class `TileEntityJSU$ItemHandlerWrapperJSU`）→ 已寫入 skill `minecraft-mod-bytecode-forensics` §11。
## 2026-10-10 凌晨（R4 review 判 7:3＋promo 收尾＋兩 repo push）
- **顧問引擎 v4 R4（反方→正方→中立裁判；Hermes 親核所有引用）**：判 **正方 7:3**（R1 3:7→R2 4:6→R3 4:6→R4 7:3），**未達 8:2、已用盡上限**，交 SK 決定。報告 `docs/plans/reviews/2026-10-10_advisory-v4-R4-judge.md`。
  - 我親自核實：plan 引用真確（`JeiLookup.java:~739`、`JeiInfoPages.java:~99`、14 個 tool、5 樣本包存在＋jar 數一致、`universio` JEI=0/REI=1）；**既有基建比 plan 寫嘅多**（`PackIndex.java:25` "light pack graph"＋`recipe_needs` 邊 :52-54/:1745＋`isCompactCycle` :1357；`RecipeUnlockGates.java` 705 行；`PlayerUnlockStatus.java` 370 行；`HonestMiss.java` 271 行）。
  - 反方只剩 3 條載重指控（全部編輯／設計補寫級）：**A5 背包來源未寫**（`GameContextCollector.java:50-78` 只有手持／副手／可選 hotbar 9 格；全背包要手動揀一件）、**P0 無數值門檻／effort／失敗定義**、**驗收未覆蓋 REI-only 降級案例**。
  - 規模實數（census `2026-10-06-pack-recipe-census.json`）：ATM8＝379 jar／216 有配方／**46,423 配方檔**／15.6MB 未壓縮／584 recipe type。
- **Promo**：50 秒片重複鏡頭修好（結尾換另一段世界鏡頭，逐格比對 mean|diff|≈50 證唔同）；刪走「NeoForge」（end card／字幕）；加 **MIT LICENSE**（`packai` `5dafa9c`）。
- **Push**：`packai` `edffe69..5dafa9c`；`jarvis-pc` `17377c4..46405ee`（推送前 secrets 掃描 CLEAN）。
- **cron**：`cs2-perf-gate0-watch`（`3f322ccff9a3`）已**刪除**（SK 指示；CS2 線 10-08 已收，詳情 `Documents\PC_Troubleshoot\cs2-perf\`）。
- **其他**：朋友求職個案（正合）報告已出（`Documents\job-check\2026-10-09-個案總覽.md`）→ **SK 叫停，唔再跟**。
- **等 SK**：① v4 決定 → **已答：只批 P0** ② 收機後去 CurseForge 拎 mod icon 入片 ③ 佢自己錄新 gameplay（有動作嗰種）→ 我再剪
- **玩家問題研究（3 輪，SK 要求；全部用真數據）**：`docs/research/2026-10-10-player-question-research.md` — Arqade API 300 條真實問題、YouTube 225 條（觀看數排序）＋30 條片 description 內容分析、minecraft.wiki 452 條教學（**farming 97＝21% 最大宗**）、Bilibili API、ATM／GTNH 官方 FAQ、Modrinth 下載量（Mod Menu 151.9M／Jade 70.5M）。**結論：需求最大係「農場／自動化設計」，唔係配方問題**。失敗照記：Reddit 403、bili 部分 412、yt-dlp bilisearch null。
- **新工具（離線、唔使開遊戲）**：`tools/extract_keybinds.py`（按鍵表＋衝突偵測；NFWC 201 條／72 已綁／**11 組衝突**、StarTech 148／60／12）、`tools/patchouli_capability_index.py`（能力反查 spike：3741 檔→25 mod／779 句；7 條真查詢命中 5，但精準度低→要 LLM 讀檢索）、`tools/mine_video_demand.py`、`tools/p0_recipe_metrics.py`、`tools/p0_engine_demo.py`。
- **題庫已擴充 I–T 類**（`docs/plans/2026-10-05-player-question-set.md`）：農場／自動化、能力反查、使用／按鍵、運作維持、數值、比較、生成條件、儲存物流、效果裝備；技術支援類明確列**唔做**。
- **P0 離線量測完成**（`tools/p0_recipe_metrics.py` → `docs/research/artifacts/2026-10-10-p0-recipe-metrics.json`）：ATM8 46,423 檔／583 類／固定比例 90.31%／NBT 2.65%／~89k 圖邊／掃 8.67s；StarTech 96.80%；NWFC 95.27%；E9E 93.17%；UniversIO（REI-only）76.33%。**未開遊戲**，④⑤b⑥ 未答。

## 2026-10-08 上午（Discord；文件執手尾＋修兩個已知缺陷）
- **Push**：jarvis-pc 4 個 docs commit（`882ee25..9c9e7b3`）；packai `c3d814c`＋新 `5aae718`（3 份 10-05 計畫書）／`d8917cb`（10-05／06 研究 artifacts）→ `815c5cb..d8917cb`。推送前 secrets／PII 掃描 CLEAN。
- **入版控**：`docs/plans/2026-10-05-*` 3 份＋`docs/research/artifacts/2026-10-05-*`／`2026-10-06-*` 6 個；`_*` 開頭 13 個 scratch 檔（raw dump／scraper script）仍未被追蹤（等 SK 決定入 git 定 .gitignore）。
- **修缺陷**：① STATE 由 09-22 改寫到 10-08 上午；② 第 172 行 `...[truncated]` 殘骸按 `docs/plans/2026-09-19-in-game-autotest-harness.md` §Plan A 重建＋標註（原文尾段無法還原）。
- **排程**：promo plan v3 Round-2 review（cron `e2ee9e156a95`，12:00）＋ NVIDIA Profile Inspector 第三方 review（cron `0c4c71d2df84`，12:20）——兩者皆 DeepSeek 半價時段。

## 2026-10-08 凌晨（promo 宣傳片計畫 v3）
- **promo 片（Pack AI Assistant 宣傳片）**：研究＋計畫 —— `docs/promo/RESEARCH-mod-promo-videos-2026-10-07.md`（含中文平台長度／合規）、`docs/promo/PROMO_PLAN-v3-2026-10-07.md`（v1／v2 已被取代）。
- **Plan review Round 1＝正方 3 : 反方 7（唔過）**：反方指控＝① 50 秒 teaser 同研究到嘅長片生態唔匹配 ② `ffmpeg gdigrab` 抓唔到硬件加速窗（會錄黑片）③ 背景 pack 用 DJ2＝世代錯（DJ2 係 1.12.2、mod 係 Forge 1.19.2）④ 英文旁白對最需要嘅中文社群係錯語言 ⑤ 驗收「字幕 ≥98%」無機械定義。數字重算另捉到：研究表列 7 行非 12、「~6 片／日」過時、−22 LUFS 非公認標準。
- **v3 已逐條修正**：背景 pack 改 1.19.2 沙盒、錄影改 `ddagrab` 為先＋10 秒試錄做 gate、中文版獨立 Phase 2、A5 定義改機械可測、LUFS 改 −16（旁白）／EBU R128、YouTube 配額更正。
- **下一步**：Round 2 review（反方＋數字核實）；達 ≥8:2 才開工拍。**至今未拍過任何畫面**。
- **✅ 2026-10-08 上午已修兩個已知缺陷**：① STATE 區塊已由 09-22 改寫到 10-08 上午；② 第 172 行 `...[truncated]` 殘骸已重建＋標註來源（原文尾段無法還原）。


## 2026-09-22 session（Slice 1b 收窄版：plan 3 輪 review → cursor 實作 → Hermes 自驗）
- **Slice 1 push 完**：`8cf28a4`（21 檔）＋`f8a3c34`（HANDOFF）＋`46805fb`（Slice 1b plan v4＋R1 review＋corpus）→ `origin/main` 核實一致；push 前 secrets 掃描 CLEAN。
- **Slice 1b plan 三輪 review（唔過就修，到 R3 裁判認可）**：R1 **3:7 唔過**（B1 做錯層／P1 歸因錯／NC3 打錯閘／B4 靶唔中／白名單漏 4 檔）→ R2 **3:7 唔過**（B3 邊界假設錯：`Plainify.lootLine` 同時餵模型同玩家，改咗必令 `AcquireJarRoutesCheck` 紅；B2 會打爛 `AskLoopState.isEmptyOrMiss:632-634` 偵測器）→ **中立裁判建議收窄** → v4 只做 B1＋B4（只動 `AskReplyScrub`）→ R3 反方機械面全過、剩 3 條屬已修範圍，**裁判判 `v4_adequate=true` 可開工**（R3 亦確認 `AskLoopState` 只讀 tool-result 文字、唔讀 display prose → prose 層做唔會打爛偵測器）。三輪即上限，唔再開新輪。
- **Hermes 自己量到嘅關鍵數字（唔靠 subagent）**：19 條 ask trace 有 body、**13 條**含 jargon／「唯一」；`check.scrub` 37 條（18 `scrubPromptEcho`＋19 `stripDuplicateSectionHeaders`）、`before!=after` 2 條（與 jargon 無關）；python 閘 baseline＝125 檔、1 條已知紅（`check_ask_display_leak` RC=2，需真機 log）。
- **B1／B4 規則離線 dry-run**：zh_cn 字表跑 corpus A 11＋C 2 → 0 殘餘、D 類 4 條 byte-identical；golden 逐條寫入 corpus §E；另加 zh_tw fixture §F 5 條＋混排斷言（zh_cn 唔准出「資料」，反之亦然）。
- **cursor 實作（hidden、零彈窗）**：`logic/AskReplyScrub.java` 加 `rewriteInternalJargon(text, lang)`（三語字表）＋新 `InternalJargonCheck.java`（121 行，A/C golden＋B/D identical＋zh_tw／en＋混排）＋`AskReplyScrubCheck.java` 加案例；`tmp-check.gradle` 重生（58 task）；偏離白名單：另改 `code_change_log.md`（專案慣例）。
- **Hermes 自驗（獨立，唔信 cursor 自報）**：compile RC=RC=0／harness **58/58**／python **125 檔、FAIL 1（baseline：check_ask_display_leak RC=2）**／負控 NC3（停用 :1643）紅 → 還原 sha 一致 → 綠 ✅；真機 A/B **未跑**（SK 要關機，留明早）。
- **未做／明早**：Slice 1b 真機 A/B → 全過才 commit；Slice 1c plan（源頭措辭＋機翻＋關聯閘）；Slice 2 正式 plan；下一輪 corpus（模型新寫法）。

## 2026-09-22 session（Slice 1 驗收完成＋commit；Slice 2 世界生成研究）
- **Slice 1 真機驗收 PASS（09-21 23:02–23:50，真 instance 新 jar）**：火盆「破坏 仪式火盆 会掉落」＋零 raw path（`blocks/`／`chests/`／`.json`／`crafting_shaped:` 全 0）；**Tetra 改裝版零件行** PASS（「左右锤头：下界合金材质锤头」「手柄：再利用梁杆」）；哭泣黑曜石有結構名（citadel）＋通用知識標示「非本包覆写」。
- **commit `8cf28a4`（本地，未 push）**：13 code＋`GapPanelCapCheck`／`LootLineHumanizeCheck`／`ToolBuildCanonicalCheck`／`KubeJsTooltipTextCheck`＋`tests/check_slice1_reply_keys.py`＋plan v4.2＋R1-pass1／R2-pass2 review＋Slice 2 底稿；驗收數字：harness 57/57、python 125/125、3 負控紅→綠。
- **computer_use 自測 5 條（09-22 00:08–00:12，沙盒，我自己開世界＋打字）**：`minecraft:diamond`／`amethyst_shard` 內容豐富但⚠️**吐「未索引」內部術語**；`iceandfire:sapphire`（此包冇）誠實答「暫時不確定」；`crying_obsidian` 誠實＋通用知識；`ritual_brazier` 沙盒零外洩（冇 cache 資料）。
- **Slice 2 研究（2 個 subagent，10 分鐘）**：業界冇一站式 where-to-obtain（JER 硬編碼礦物分佈＋手寫檔；唯一自動由 worldgen 推導嘅 JEIWorldGen 冇 1.19.2）；官方 client jar **冇 worldgen 資料夾**（vanilla processor_list 唔喺度 → 哭泣黑曜石要內建表）；mod jar `structures/*.nbt` **6787**＋`processor_list` **548**＋`configured_feature` 347 未掃；**結構模板內寶箱自帶掉落表名** ⇒ 可砌「XX 結構寶箱有機會出」（例 ancient_city 13／bastion 29）。
- **新捉到嘅外洩類**：`chests/catacomb/treasure_rib` → 玩家文字見「（treasure rib）」／「宝藏肋骨」（機翻）⇒ 歸 Slice 2 人話化。
- **部署／環境**：真 instance jar 換成 `af448fa4d939`（backup `%TEMP%\deploy_backup_20260921_2258`，231 mod）；沙盒 jar 由 watcher 自動補返（`0a79ee90c0bd`）；沙盒遊戲已關（WM_CLOSE，javaw 只剩 SK 自己開嘅 `AI_test_NFWC_DIM` pid 5220）。
- **未做**：push（3 個 commit 未推：`8cf28a4`／`49d77d1`／`fae7724`）／Slice 1b plan／Slice 2 正式 plan。

- **真 instance 一輪 session 結果（09-21 07:26–08:11，SK 玩，10 條問題）**：`packai` **零例外**（`com.skps9`/`Query failed`/`NoClassDefFoundError` 錯誤行全 0）；**記帳正常 ✅**（帳本 UTC key：09-20 +477,773、09-21 +50,387；`Pack AI usage billed=` 10 行）⇒ 之前 `delta=0` 確定係 driver 量測 bug。質素：**7/10 clean**；**3 條 gap 面板吐 raw token**（`073004 blocks/`、`074710 crafting_shapeless`、`074759 crafting_shaped`）＝C1/C7；**5 條**寫「本包索引沒有…取得路徑」＝B；**Tetra 2/2 中 C8**（`modular_sword` 只講「NBT 有 6 個標籤」、`modular_double` 講成空框架合成）⇒ C8 要覆蓋全部 `tetra:modular_*`；C5：滿溢神恩項鏈只講「請看遊戲內 Shift 提示」。watcher `LEAK` pattern 已修強（舊版漏報 1 條）。
- **mcmod 工具升級（09-21，SK 指示：核日期＋睇教程／評論）**：`mcmod.py` 加 ① `read` 出**頁面日期**（`创建日期／最后编辑／編輯次數`，抽 `data-original-title` 到秒）② 新指令 `comments <id> [--channel 1|2|3]`（`POST /frame/comment/CommentRow/` JSON、含 `time.source`；**唔准**打 `/action/doComment/`）。實證價值：風暴之星頁最后编辑 **2025-03-01**，但 **2025-07-15 短評**講「新版已更名為疫染下界之星」＝正文冇（本 pack 1.19.2 用舊名正確）；儀式火盆 4 條評論只有用法技巧。已同步 live＋skill 包＋`Code_Project\mcmod-reader`。C9 QA 流程更新為：read 日期 → comments → post 教程 → jar/官方交叉（jar＞官方＞mcmod＞真機）。
- **Slice 1 plan v2（09-21 08:5x）**：R1 = **正方 4 : 反方 6**（未過）→ v2 吸收 6 阻塞：① B 禁詞補 **runtime scrubber 層**（`AskReplyScrub.PLAYER_UNSAFE_MARKERS:148-166`＋`AskJeiHints.looksLikeAbsenceClaim:39-78`，有卡會整句刪）② **C1 降級**（item-index 冇掉落表資訊＋`L|` 源頭無 ns ⇒ 反查做唔到 → 移 Slice 2；Slice 1 只做「唔露 raw path」）③ C5 fallback 改**遊戲語言表**（`ReplyLang.tr` 唔查 pack lang，v1 第 2/3 步係死路；解析搬去 display 期）④ C7 **刪 C4 claim**（`AiAssistantScreen:834-869`＋`check_info_completeness_hook_order.py:83-98` 鎖死位置）⑤ C8 **hook 寫死 ≥`AskEngine:951`**（930 會被 951/980 覆蓋）＋`UNKNOWN` 負控 ⑥ 白名單補 9 檔＋逐 case 期望值。
- **A 案 spike 修正（09-21）**：修 3 個腳本 bug（`rewards.recipes` 死碼、NBT regex 截斷、分支次序）→ **kept=114／111 物品**（我 per-item）vs **44**（verifier per-advancement）；抽樣 10/10 真取得 ⇒ 值得做，但 **corpus／predicate／配方收集規則必須寫死**先有可重現性。artifact：`docs/research/artifacts/2026-09-21-advancement-route-spike.md`
- **mcmod 雙重核實（09-21，SK 提醒 mcmod 可能錯）**：反例＝儀式火盆頁 `/give id = ars_nouveau:ritual`，真 1.19.2 jar 係 `ars_nouveau:ritual_brazier`（版本漂移）；核實優先序＝jar(1) > 官方 repo/wiki(2) > mcmod(3) > 真機(最終)。風暴之星**三邊一致**（jar advancement＋mcmod 591072＋官方 wiki「Phase 4.0–7.5 附近玩家直接收到」＋GitHub issue #2070）⇒ 可採用；官方 wiki 亦解釋咗「本体唔掉物品、係程式碼發放」＝冇 loot table 之因。
- **mcmod 對照核實（09-21，SK 指示）**：mcmod 591072 明文「风暴之星是凋灵风暴被摧毁后掉落的战利品」＋「获得此物品时会完成挑战【此波平，彼浪起。】」⇒ **A 案方向證實**；新發現 **A 條件⑨**（已有非自掉 loot／worldgen 途徑唔出 A 行——例 `command_block_book` = 凋靈共生體 100% 掉落，我們已有 `loot_tables/entities/withered_symbiont.json`）；儀式火盆 mcmod 只有合成（證實 self-loot 行係噪音，C1／C7 方向對）；**Tetra 全系列頁只有合成表** ⇒ C8 唔可以用 mcmod 核（靠 jar facts）；mcmod 只作**開發期 oracle，唔入 code**（版權）。artifact：`docs/research/artifacts/2026-09-21-mcmod-crosscheck.md`

## 今日完成（2026-09-21 session）
- **route cache 過期（09-21 實查，真 instance）**：`config/packai/jar-cache/` 全部檔 mtime **08-08 09:10**（6 週前舊 code 建），key 只認 jar hash ⇒ **code 更新唔會令 cache 失效**。量化：230 檔、`L|` route **12,198** 條，其中 **2,146 條（17.6%）**係 `isTrivialBlockSelfLoot` 應該過濾嘅「挖方塊掉返自己」噪音（例 `ars_nouveau:ritual_brazier`／`goety:apparition_door`）。另見 cache 把 loot function id（`minecraft:survives_explosion`）當 item key。→ 兩個候選缺陷：C2 cache 版本失效、C3 噪音過濾覆蓋。
- **掉落表 raw path 入玩家視野（09-21 SK 截圖）**：trace `ask-20260921-073004-ars_nouveau_ritual_brazier` 嘅 `acquire` 工具結果同 gap 面板都係 `掉落表：blocks/ritual_brazier`（lang `packai.reply.loot_table_obtain`）；`blocks/*` 冇 namespace ⇒ 玩家睇唔明。候選 C1＝解析方塊顯示名＋用 `{{item:}}` 標記。

- **F2 真機 leg 完成（同 session 可逆）**：MC pid 49592，同一物品 `ad_astra:oxygen_tank` 同一問題 3 問：ON 14:41:11 `Loot table`×3（正文引 village/moon blacksmith chest）→ 設定關 14:42:46（toml 即時 false）OFF 14:43:06 `Loot table`×0（正文「No chest/loot/trade source is indexed」）→ 開返 14:44:45（toml true）ON 14:45:00 ×7。方法論修正（已入 skill）：computer_use `coordinate=` 係 **window-relative**；送輸入前必須真有 focus；改 toml 檔對 runtime 無效（Forge 唔 reload）。MC 已關／toml 還原 true／焦點交還。

### 2026-09-20（session 下午：P1 修復交付）

- **P1 修復落地（plan v6／`docs/plans/2026-09-20-p1-followups-plan.md` §4b 有全套證據）**：
  - **F1b**（主因）：`WorldgenFacts.parsePlaced` 認 inline object `feature`（讀 `type`＋`config.size`）→ emit 行尾 `inline_type=`／`inline_size=`；`AcquireAskTool` size fallback＋`field()` 終止符加兩 key。**真機前後對照**：`thermal:silver_ore` 修前冇 size → 修後 `Vein size: 8`（同一物品、FTB 沙盒 trace 09:25 vs 13:38）。
  - **F1**（次因）：`AcquireAskTool.configuredOwner` 改一對多（`Map<String,List<String>>`）；`WorldgenIndex.keepOreRoute` **無需改**（原本已 `return true`，加咗斷言守住）。
  - **F2**：`JarLightIndex.routeLinesForItem` 首句加 `PackAiConfig.scanModJars()` 閘（負控紅→綠；真機同 session toggle leg 未做）。
  - **丙**：三語 `worldgen_ore` 字眼對齊業界（`World gen:`／`Y level:`／`Veins per chunk:`；zh「世界生成／高度（Y）／每區塊礦脈數」）——真機已 live。
  - 驗收：compile ✅／**53/53 檢查綠**／python 閘 124 檔 1 已知紅（零新增）／**4 條負控逐條獨立紅→綠**／真機 FTB 5 條 `status=DONE`＋零外洩＋原版控制組老實答。
- **過程事故（已修）**：第一版負控腳本 `shutil.copyfile(path,path)` 拋錯 → 四個還原冇執行、工作樹半壞；即時停手用反向替換補回，四重核實（numstat 逐格／其他改動仍在／5 檢查全綠／sha 對快照）。
- **部署安全（重要改動）**：`state/mc_mod_jar_guard.json` —— 真 instance target 由 `packai` 改名 **`packai_real_play`**（標明唔准自動部署），新增 5 個沙盒 target（`packai_ftb`／`packai_atm8`／`packai_startech`／`packai_e9e`／`packai_universio`）；負控證實 `--target packai` 已 FAIL。備份 `%TEMP%\guard_backup_20260920_1335.json`。**發現 `packai_sandbox_e9e` 有兩個 packai jar（未清，等 SK）**。
- **測試範圍擴充**：`docs/TEST_SCOPE.md`＋`tools/make_cases_pack.py`（每次重新隨機；**修好抽樣盲點**：舊抽樣器只讀 configured_feature 檔 ⇒ 抽唔到 inline 礦）。
- **研究**：`docs/research/2026-09-20-ore-gen-viewers-landscape.md`（JER／JEIWorldGen／EMI Ores／RER 比較；結論：業界讀 runtime registry，我哋讀 JSON ⇒ F1b 根因；乙 plan `docs/plans/2026-09-20-worldgen-registry-source.md` 第一步＝probe 實測）。
- **未做**：commit（等 SK）／F2 真機同 session toggle（需 GUI，等 SK）／乙 probe／e9e 重複 jar 清理。
- **R1 反方 review（A＋B plan，09-21）= 正方 2 : 反方 8，唔可開工**。卡死點（要 v2 改設計）：① `inventory_changed` 命中 5968 條、**91.4% 係 `advancements/recipes/**` 配方解鎖型**（無 display、criteria 係材料持有）→ 必須明文排除；② 必須要求 `requirements` 單元素 group（否則「取得此物會完成成就」係假陳述；連言 group 實測 23 條）；③ §7.1 fixture 必紅：`ItemConsumeUseFacts.resolveText` 走 client `I18n`（headless 回空）→ 要另寫由 jar 讀 lang；⑦ B 打錯靶：live 路徑 miss 字句係 `AcquireAskTool.java:44-46`／`LlmClient.java:450` 硬編，唔係 lang key；⑬ 白名單缺 `PackIndex`／`JarLightIndex`（鎖死正確落點）；⑭ 缺 `check_reply_prompt_keys.py`（新字面撞語意 marker 閘）。另：`scanModJars` **默認 false**（`PackAiConfig.java:447`）；`check_ask_display_leak` 並非已知紅（124/124 全綠）。
- **卡落位（09-21 SK 截圖：附魔裝置卡夾喺「資料有、答案未提」下面）根因**：gap 面板由 `AskEngine.java:1011` → `InfoCompleteness.append`（`:80-90`）插喺 **【来源】之前**；卡嘅字符串級 fallback 插入邊界同樣係「【来源】之前」（`RecipeEmbed.java:706-712 indexBeforeSources`、`AskCardFallback.tryInsertAfterMaterialUseMethod`），gap 先插 ⇒ 卡被推落 gap 之下。⚠️ 卡落位（`RecipeEmbed`／`RecipeCard`）喺**唔准郁清單** ⇒ 修法必須喺 gap 面板側（gap 排最後或改邊界）。
- **kubejs tooltip lang key 未解析（09-21 實查）**：`purpose_lookup` 對 `kubejs:god_bless_full_necklace` 只回 `note:kubejs.tooltips.active_pill.1 | .2`（**key 而非文字**；`KubeJsMechanicScan.java:1050` 只 append note key）⇒ 答案變成「請看遊戲內 Shift 提示」。實際文字喺 pack 內：`kubejs/assets/kubejs/lang/zh_cn.json` → `kubejs.tooltips.god_bless_empty_necklace.1 = 击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能`。code 內**冇任何** `kubejs.tooltips` 解析器 ⇒ 候選 C5＝掃 pack lang 解析 note key（唔靠 client I18n）。另 C6＝分段規則：「按 R/U 查看」「升級／充能」等非取得途徑字句唔應出現喺『怎么来』段。
- **C7 gap 面板吐 raw route code（09-21 SK 截圖，真 trace `ask-20260921-074759-tetra_modular_double`）**：玩家問「下界合金錘」→ 「资料有、答案未提」面板列出 8 條 `合成 crafting_shaped: minecraft:acacia_planks／andesite／diorite…`（實為該錘可作工具參與嘅 shaped 配方，但顯示為 raw registry id）。SK 判：呢類應入 log 而唔係玩家畫面。候選 C7＝gap 面板改寫入 trace／log（或保留就要人話化＋限行數＋過濾 as-ingredient）。註：面板由 `AskEngine.java:1011` → `InfoCompleteness.append` 插入正文（同 C4 同一插入點）。
- **C8 tool_build（Tetra 零件）事實有但答案冇用（09-21 SK 真機 trace `ask-20260921-074759-tetra_modular_double`）**：`tool_build` 工具結果真係有 `part double/head_left: basic_hammer_left ← basic_hammer/netherite（下界合金）`、`head_right` 同樣、`double/handle: basic_handle ← basic_handle/forged_beam（再利用梁杆）`；但 final body 只講「空框架合成：橡木木板＋木棍 → modular double」＋主手數值，零件只喺任務句側面出現 ⇒ 違反既有 lang 規則（改裝版唔准用空框架配方當取得途徑、要領住列零件）。候選 C8＝tool_build 零件要入答案主線，且用 **canonical FACT 行強制貼回**（照任務 canonical 行機制；SK 唔接受 LLM 選擇性行為）。
- **Plan review 第 3 輪停手（09-21，SK 規則：3–4 輪未達 8:2 即停）**：R1 **2:8** → R2 **6:4** → R3 **6:4**。卡死載重決定：① 條件⑧（合成型成就排除）唔可靠 —— `JarLightIndex.parseRecipeJson:235-238` 只讀 `result/output`（`sequenced_assembly` 用 `results` → 漏反例），靠 cache `R|` 又撞 **`MAX_FACTS_PER_ITEM=8` 硬上限＋靜默丟棄**（`JarLightIndex:57,301-311`；真 cache 471 個 item 已頂 8，histogram 8→164/9→36/10→28/11→23/12→13）⇒ 結果隨掃描次序不確定；② `hasNonQuestAcquirePath`（`AskEngine:1279`）入參係人性化字串 list，`:407-409` 已被 acquire tool 輸出覆寫 ⇒「未加 A 行清單」不存在，要改 `PackIndex.AcquireFacts` record 形狀（加 `preAdvancementLines()`）＋連帶 `missPin`（`HonestMiss:31-33`）／`isHeldLocallyTouched`（`:293`）／`:359-362`,`:469-473` 都要開白名單；③ cache `manifest.v` 只寫不讀（`JarLightIndex:335`），舊 cache ⇒ A| 永不生效（依賴 C2）；④ C1 反查 item-index：unique 2123/2302（92.2%，1 個錯）、multi 32、zero 147；反查未用 ns ⇒ 白白降級（`blocks/analyzer` 實唯一）。⑤ §3.3 禁用詞漏 4 個（`必須`／`請明說`／`render_recipe_cards`／`role=`）；C1 新 key（含 `%s`）唔准入 `check_reply_prompt_keys.KEYS`。→ 已交 SK 決定：拆細（Slice 1 顯示層先做／A 延後做 spike）／只做誠實措辭／硬食／放棄 A。**未改任何 code。**
- **SK 決定（09-21）C7＝面板照顯示俾玩家，但人話化（唔露 raw id／剔 as-ingredient 噪音／限行數）＋同時寫入 log／trace**（更正我早前「玩家唔見」嘅讀法）；C8 併入 plan。

## 2026-09-20 packai a+b（世界生成三類 ＋ 必答清單；SK 09-20 06:5x「a+b」）

- **plan**：`docs/plans/2026-09-20-worldgen-and-mandatory-facts.md` v3.1（R1 3:7 → R2 7:3 → R3 **8:2 達標**）；4 份 review 報告喺 `docs/plans/reviews/2026-09-20_worldgen-and-mandatory-facts-*`。
- **實作**：cursor-agent 落 6 個 Java 檔（`WorldgenFacts`／`WorldgenIndex`／`AcquireAskTool`／`ReplyLang`／`AskEngine`／新 `InfoCompleteness`）＋ 新 harness ×2 ＋ 新 python 閘（`tests/check_info_completeness_hook_order.py`）＋ lang 三檔；白名單外零觸碰（sha256 對 07:38 基線）。
- **親驗（Hermes 親跑）**：compile **RC=0**；53 個 `run*Check` **53 綠**（原先 1 紅＝plan 期望值 11→10 錯，已修）；python **124 檔 / 1 個已知紅**（`check_ask_display_leak.py`）；**負控 5 條**全紅後還原（其中 hook 位置負控揭發舊閘假綠 ⇒ 已加括號配對）。
- **FTB 沙盒真機 4 輪**：**(a) ✅** 維度／生態群系／高度／礦脈大小／每群數量（例：`ad_astra:moon_desh_ore` → Moon／Lunar Wastelands／-80..80／size 9／count 9）；**(b) ✅** gap 段只在真缺時出，3 類 token 修正後**零重複**；6 條 trace 掃 `configured=`／`count=`／`[WORLDGEN]`／`L|`／`U|`／`R|`／卡 marker ⇒ **零外洩**。
- **跨包 UniversIO**（新沙盒 `packai_sandbox_universio`，複製 213MB，原 instance 零改動；已生成世界 `New World`）：**7/7 OK**、零外洩、原版礦老實答「包冇索引到 worldgen」（該包零 ore feature ⇒ a 正向路徑唔適用，已明文記錄）。
- **未 commit（等 SK 指示）**：6 個 Java 檔 ＋ 2 harness ＋ 1 python 閘 ＋ lang×3 ＋ `code_change_log.md`（＋ plan／4 review 報告已 commit）。
- **課外（測試基建坑，已寫入 plan §9）**：harness `cases.json` 第一行必須逐字 `{"packaiAutotest":1,`（Python 預設 `": "` 有空格 ⇒ 靜默唔跑、零 log），且一個 JVM session 只讀一次；ore feature 名 ≠ 物品 id（要 lang 交集抽樣，否則 NO_SAMPLE）。

- **09:0x–09:27 code review（兩階段，subagent）**：`docs/plans/reviews/2026-09-20_ab-implementation-code-review.md` → **P0×1／P1×6／P2×8／Pass2 脆弱位×6**。P0-1 = gap 覆蓋判定被答案自己嘅 `[[item:id]]` marker 自我命中 ⇒ **b 對 a 係 no-op**（Hermes 加個案對未修碼跑 = **RED RC=1** 親手重現）。
- **P0-1 修**：`InfoCompleteness.stripMarkers`（比對前剝 `[[…]]`／`[…]`）。**P1-2 修**：`WorldgenIndex.routesForItem` 先掃（`MAX_ROUTES_SCAN=256`）→過濾→後截（8）。**P1-3 修**：`WorldgenFacts.putDimension(..., overwrite)`＋`dimBiomes`，覆寫先 `dropDimBiomes`。
- **修後親驗**：compile RC=0；兩新檢查 OK；**NC2** 抵銷 marker 剝除 ⇒ 同 AssertionError **RED（RC=1）** ⇒ 還原 sha `4c1df3e70f37795d` 一致 ⇒ 重跑 **GREEN**；python 124／1 已知紅；hook-order RC=0。**修後真機**（FTB 09:26，14 案例 6 OK）：零外洩、世界生成照出、**零假 gap**。
- **未修 P1（待辦，唔阻交付）**：`dimOverCap` 冇 consumer；gap 側缺 `scanModJars` 閘；`L|` 人化雙寫；`configuredOwner` 1:1 令共享 configured 冇 size。
- **commit 狀態**：docs 已入 git（`3c0a1c9`／`ae70d74`）；**code 未 commit**（工作樹 135 項，含多個 session 累積）→ 已問 SK：(1) 只 commit 今次 a+b 批次 定 (2) 照舊累積到版本發佈。
- **09:4x SK 揀 A → 累積批次 commit `f325c4e`**（135 檔、+17,361／−1,314；**唔 push**，main 領先 origin 10）。內容＝a+b（含 P0/P1 修正）＋ 之前 session 累積（Plan F／settings／CostWindow／DailyTokenUsage／JsObtainSites／OfficialDisplay／ModularFrame*／autotest harness…）。commit 前親掃：零 secret（key-like／apiKey 賦值／`.env`／client.toml 全空）、零 jar/class/log 入 commit（`logs/` 兩個 runtime log 目錄**故意排除**，未入 .gitignore — 建議下次加）。
- 覆核：UniversIO 用**最終版 jar**（sha `b5ffe2761cea`，已驗 class 含 stripMarkers／MAX_ROUTES_SCAN／dropDimBiomes）跑 **7/7 OK**、零外洩、原版礦老實答「包冇 worldgen 覆寫」。
- **09:5x–10:0x GitHub README ＋ CF 描述更新（SK 指示）**：README 加世界生成／必答清單／取得途徑三點，commit `3ad7ccf` ＋ push，並**由 GitHub raw 線上核實**三點真係上咗；CF 描述（`docs/CURSEFORGE_DESCRIPTION.md`，同步線上版 paused 段）＋貼上版本 `dist/_cf_desc/packai_cf_description_paste.txt`。
- **CF 現況**：CF 上最新 file 仍係 **0.2.1（09-09）**；0.2.3 正式 jar 已 build＋驗（`dist/packai-0.2.3+mc1.19.2-forge.jar`，sha `be55abcb…`，無 harness marker、三個修正入 class）。
- **自動上傳唔可行**：CF 全部 API host 被 Cloudflare 擋（403「Just a moment」×4 host ×2 UA）→ 改為**手動上傳**（步驟＋metadata 已寫入 `docs/PUBLISH.md`）；helper `tools/cf_upload.py` 入 repo（`a3073dc`，push）。等 SK 上傳後由公開頁面核實。
- **10:0x–10:1x CF 上傳＋描述：兩樣都成功**（更正我早前「做唔到」嘅結論）。上傳：`dist/_cf_upload/upload_028.py` 用 `CURSEFORGE_AUTHOR_TOKEN` POST → **HTTP 200、file id 8926920**（0.2.3 Forge 1.19.2，等 CF 審核先出公開列表）。描述：`dist/_cf_desc/update_description.py`（off-screen Chrome 暖 cookie → PUT `description.html`）→ **真瀏覽器核實線上頁面已有 EN＋繁中新段**。
- **教訓（已寫入 `docs/PUBLISH.md`）**：只有 `GET /api/game/versions` 被 Cloudflare 403 擋，**上傳 POST 冇事**——唔准因嗰個 403 就推論上傳做唔到（我今次就係咁錯咗一次，SK 即時糾正）；version id 一律 pin（1.19.2 Forge `9366,7498,9638`）。
- **11:1x 測試範圍擴充（SK 交新包）**：新沙盒 `packai_sandbox_startech`（Star Technology 154 mods／Forge 43.3.9／placed 28・inline ore 9）、`packai_sandbox_atm8`（ATM8 379 mods／Forge 43.2.14／placed 448・configured 420・inline ore 10 = 最大 stress test）；jar `b5ffe2761cea`、config `scanModJars=true`、`recipeCategoryOrder` 清空。登記文件 **`docs/TEST_SCOPE.md`**（含鐵則、沙盒表、唔入範圍嘅包＋理由：1.12.2／1.20.1／1.16.5／1.18.2 無 build、ATM10 NeoForge 線暫停、Fabric 唔支援）。**未做**：遊戲內 smoke（活動 Gate = `playing`，唔准開窗）。
## 2026-09-20 06:5x（Discord；SK read hand off → drift 複核）
- 親核：tree 未 commit 73 檔（+4,484／−1,305）＋54 untracked＋2 deleted；真 instance jar `06b5b129a114`（09-18 07:12，落後 fix A／`31185e8`／`344e805`）；沙盒 FTB harness jar 09-20 01:00。
- 已寫入 STATE 現況／未解（不再只靠逐日 section）。
- SK 09-20 06:5x 指示：**update hand off first**。
- SK 昨夜（09-19 20:5x–09-20 01:05） 原話要求：玩家問一件物品 → 要拎到**全部資料**，包括「只可以由結構／挖礦拎到」，生態群系，礦物分佈，維度。
- SK 已授權 push（原話 `btw u can push to git if u want`）；本次 docs commit 仍未 push。
- 下一步序：a 維度／生態群系／礦物分佈｜ b 必答清單｜ c 細試點 → **等 SK 覆**。

## 2026-09-20 凌晨 session（v6：索引→答案管道 P0）

- v6 plan 寫成（`docs/plans/2026-09-20-item-routes-p0.md`，自成一檔唔疊覆寫）＋ cursor 實作 2 個 code 檔＋1 個 harness。
- 本地驗收：51/51 checks 真跑（明確任務名）、負控翻紅、python 閘 122 綠＋1 已知紅；cursor 兩次都老實報 NOT RUN（shell 被拒），無假綠。
- 真機 FTB 4/4：氧氣罐答「village / moon blacksmith chest」；crystal heart 講 end city／bastion；dragon sinew 講 ender dragon drop；對照（oak corner trim）零假陽性。`focus_stolen=False`。
- 我兩次自製假警報並修正：① coverage 儀器分子 bug（用 intersection 後真值 70%／detectable 90%）② python 閘 cwd 跑錯（由 `tests/` 跑 → 假紅 2 個）。
- 完整度 plan 5 輪反方 review：2:8 → 3:7 → 4:6 → 5:5 → 4:6（平台化，已停）；R5 揭方法論錯：驗收分母要用**交付路徑可見**範圍（raw 5,160 L refs 只有 2,418 可達）。
- commit `344e805`（code＋artifact＋plan）＋docs；push 到 `skps00/Modpack-AI-Assistant` main。

## 2026-09-19 晚 session（Discord；卡走位真值儀器＋多 pack 對照 → 揭 FTB P0）
- 儀器（cursor 實作＋Hermes 親驗）：`AiAssistantScreen` :869 後 gated log；新閘 `tests/check_cardplace_instrument.py`；`RecipeEmbed` 零改動；compile rc=0；49 Java 檢查全綠；python 122 綠＋1 已知；負控紅→綠。
- **主包真值 run**（23 案例／18 成功；tokens +768k）：316 樣本 `adjacentCardPairs=1` **54（17%）**、15/18 案例受影響；`afterSrcStart` 恆 0、`lastIsCard` 恆 false ⇒「堆埋」量到、「去最尾」量唔到。
- 交叉排除：15 個案例嘅 `display.body.final` **冇**同行多個 `[card:N]` ⇒ 相鄰唔係模型寫法造成（反方警告嘅假陽性已排除）。
- **E9E run（真 LLM，tokens +518k）**：11 案例／73 樣本 `adjacentCardPairs` **全 0**（config 與主包一致）⇒ 症狀同 pack 內容相關，非全域硬 bug。
- **FTB run＝作廢**：19/20 答案＝`Query failed: start 742, end 696, length 1214`；stack trace：`QuestGuide.stripQuestIcons:1538` ← `itemsInRange:1471` ← `parseQuestsArray:1013` ← `parseFile:947` ← `index:123` ← `AskEngine.ask:263`。
- **P0 根因**：`out.append(text, last, m.start())` ＋ FTB 任務檔嵌套 `icon:{… tag:{ Icon:"…" }}`（大小寫不分 `\bicon\s*:`）⇒ 剝外層後 `last` 衝過內層 match ⇒ start>end 爆。Python 逐字移植掃真檔：FTB **1/51**（`chapters/getting_started.snbt`）、E9E 0/44、主包 0/60。
- **已發佈影響**：`QuestGuide.java` 自 08-16 `c0365bb` 未改；已部署 jar `06b5b129a114` 反編譯確認含 `stripQuestIcons` ⇒ 真實玩家中招。
- Prism 換路徑善後：deploy guard／驅動腳本（改 env `PACKAI_PRISM`／`PACKAI_SANDBOX`／`PACKAI_WORLD`／`PACKAI_CASES`）／repo 硬編碼（`tests/check_ask_display_leak.py`、`AskJsObtainSitesCheck.java`、`research/_proto_js_obtain.py`、`AGENTS.md`）全修好；49 檢查由紅→全綠。
- 樣本工具：`tools/cardplace_sampler.py`（每次新 seed＋記錄 seed＋去重；`--pinned` 重複問同一物品）；補抽 `BlockEvents`／`PlayerEvents`／`EntityEvents`（FTB `event_item` 3→19）。
- 教訓：**唔可以只信 `status=OK`**——E9E 第一輪 config 冇 API key ⇒ 離線模板答案（每問 0.6 秒、tokens 0）但全報 OK；對照實驗必核 latency ＋ token 增量。
- 我方更正紀錄：曾報「E9E KubeJS 抽取覆蓋唔到」（假；E9E 真係只有 6 個 KubeJS 物品）、曾報「E9E 冇 FTB Quests」（假；grep 用連寫 `ftbquest` 漏 hyphen；實際 45 檔）。

- **P0 修法實作（cursor，未 commit）**：`QuestGuide.java`（D1 嵌套守衛 `:1570`＋D2 每檔 fail-soft＋5 參數 `index(...,int[] skippedOut)`）／新 `QuestGuideStripIconsCheck.java`（T1a–T5）／`QuestGuideIdCheck.java`（A8(a)(b)(d)）／`tests/check_quest_strip_icons.py` 同步。
- **P0 驗收（Hermes 親跑）**：`compileJava compileTestJava` rc=0；**50/50 檢查真跑全綠**（BUILD SUCCESSFUL 44s，50 個 `Check OK` 行）；123 python 閘只有 1 個已知紅（`check_ask_display_leak.py` rc=2）；**負控**（拆 D1）→ BUILD FAILED ＋ `AssertionError: A8(a) missing NESTEDQUEST00001`，還原後 sha 一致；**A8(d) 真 FTB 檔 PASS**（兩個真任務 id 命中）；`RecipeEmbed.java`／`RecipeCard.java` sha256 不變。
- **坑（新）**：`tmp-check.gradle` 只 register 任務、無 `dependsOn check/build` ⇒ 必須逐個任務名跑，否則 0 檢查（cursor 首報「50 pass」係用無任務名指令，唔可能成立）；gradle 輸出含 cp950 byte，Python 讀取要 `decode(utf-8, replace)`。
- **未做**：真機 FTB 一輪（A4：19/19 真答案、零 `Query failed`、`skippedOut[2]==0`）——等 SK 唔打機；未 commit（依規則真機驗收後才 commit）。
- **P0 QuestGuide 嵌套 icon 崩潰：完成**（code commit `31185e8`，已 push）——D1 守衛＋D2 per-file fail-soft＋5 參數 `skippedOut`；真機 FTB 19/19 真答案、0 `Query failed`、tokens +1,020,381、`focus_stolen=False`；headless 50/50 綠＋負控翻紅＋A8(d) 真檔 PASS
- **P0 教訓（新，入 skill）**：cursor 自報「用 `-I tmp-check.gradle` 跑齊 50 個檢查」技術上唔可能（init script 只 register，無 hook check/build）⇒ 一律用**明確任務名**跑
- **新主線：件物品『全部資料』覆蓋 plan**（`docs/plans/2026-09-19-item-info-completeness-plan.md`）——v1 `7b12a17` → R1 **2:8** → v2 `9141e45`（已 push），R2 跑緊
- **R1 捉到一個上游真 bug（P0 級）**：jar-cache 有 loot／用途 refs（例 `ad_astra:oxygen_tank` → `L|chests/village/moon/blacksmith`）但 **0/19 trace 含 `[JAR]`、`send.facts` 冇 `chests/`** ⇒ 氧氣罐答案寫「no loot … indexed」＝**同索引相反**（D0 要修管道）
- **coverage 儀器**（`tools/check_item_info_coverage.py`）修好分子 bug（要用交集）：真值 **ALL 121/161=75%／detectable 82/91=90%**；答案長度 1262–2012（**冇 code cap**）
- **worldgen 三類實測**（維度／生態域／礦物分佈）：機制齊、pack 有料（biome 67／structure 36／configured 20／placed 4）但 **`worldgen_lookup` 0/19 被叫**、生態域 **0/19** ⇒ 要 D5 mandatory 注入
- **答案內容核對（對官方 doc 抽 4 條）**：modularrouters／elementalcraft／tetra／mekanism SPS 官方來源對得上；⚠️ `flux_shovel` RF 值屬**版本差**（官方 1.12 vs pack 1.19.2）
- **本機已 push 到 GitHub**（`skps00/Modpack-AI-Assistant`，main，222 commits，掃過 secrets 乾淨）


## 2026-09-19 session（Discord；跨 09-19 16:0x–17:0x）

- **P1（命名空間來源索引）撤回**：反方 R1 **3:7**（粒度斷裂＝kubejs 註冊物品喺 `startup_scripts/*.js`，唔喺 `data|assets/<ns>/`；`PackIndex:183-184` 已掃 `kubejs` 全根 ⇒ 新索引＝重複造輪）＋**真機 artifact 推翻前提**：harness 問純本包物品 `golden_age:bloody_scissor`（kubejs 註冊＋kubejs 配方＋kubejs 任務）→ 現況**已答對**（顯示名「染血的剪片」／生物鍛造台配方／Tetra 模組／任務／來源「整合包本地配方」）⇒ 症狀重現唔到，唔做。plan＋R1 report commit `8b09b49`，artifact `docs/research/artifacts/2026-09-19-autotest-run-v2/ask-20260919-1551*.jsonl`（commit `f34035c`）。
- **Follow-up #1（框架卡冇 `[card:N]`）＝6 輪 review 未過，依規則停手；未改任何 code**：v1 3:7 → v2 4:6 → v3 5:5 → v4 6:4 → v5 4:6 → v6 3:7。關鍵發現（全部有 artifact／行號）：① 真兇＝`AskReplyScrub.replaceHowToGetBody`（`:943-965`，由 `AskEngine:980` 呼叫）喺 「how-to-get **replaced**」路徑食走 marker；sandbox log 實錘分兩條路（`replaced` vs `heading not found → appended`，後者保住 marker）② `AskTrace.markers()` 掃 `"[[recipe_card:"` ⇒ **唔可以量 `[card:N]`**（我 v1/v2 判準錯）③ UI 本身已有 `disperseUnplacedEmissionCards`（`:919-925`）＋needle-disperse（`:926-967`，註釋明言防「sticky-cluster at section end」）④ `byFallback>0` ≠ 症狀（真反例 `ask-20260919-140842` 落位正確）。plan `docs/plans/2026-09-19-followup1b-card-marker-fallback-phase-plan.md`（v6）；reviews `docs/plans/reviews/2026-09-19_followup1*-plan-R*.md`。
- **SK 定義症狀（決定性）**：「**卡堆埋一齊, 卡去咗最尾 both**」＝ `RecipeEmbed` fallback 落位（冇 marker 嘅卡塞段尾，多張就堆埋）。SK 亦確認：**隨機**（同一物品「第一次錯、第二次正常」）、玩家可見。
- **③ Hermes skill 庫 renew ＝ 已完成（SK `go`）**：① 死路徑修正（真錯 1 條；audit 其餘 6 條誤報）② git 分支雙胞胎合併（keeper `long-lived-branch-merge`；舊者原生歸檔）③ `minecraft-modpack-ai-development` 100,306→56,212 字（4 段搬 references／sha1 零損失）④ **語音 3合1**（umbrella `jarvis-voice-assistant`；`windows-voice-pipeline`＋`hermes-voice-windows` 原生歸檔；7 項實測驗收全中；5 輪 review 4:6→6:4→7:3→7:3→**8:2**；詳 `jarvis-pc\.hermes\plans\2026-09-19-skill-voice-merge-plan.md`）。原 audit 記錄：audit 報告 `%LOCALAPPDATA%\hermes\reports\2026-09-19_skill-library-audit.md`；**第 1 項（3 個 `references/merged-*/SKILL.md` 仍被收錄）已親驗為假**（`skills_list(software-development)` 32 個、零 merged-*）⇒ 跳過。新發現：`minecraft-mod-in-game-autotest` 由**背景 skill curator** 於 15:55 自動建立（內容已核，準確）。

