<!-- STATE:BEGIN -->
## STATE（五元素；每次改寫，唔 append）

- **目標**：packai（`super_minecraft_AI_player`）＝SK 第一優先 mod。三線：**(a) 單選物品（已完成待驗收）**、**(b) Settings 頁重做**（計畫 v2 已 review）、**(c) 機制事實層 M1／M1c／M1c-fix／M1e ＋ 知識庫**。另開**副線 `Documents\side-quest-money`**（搵第一筆收入；獨立 repo，唔混主線）。
- **現狀**：`forge/1.19.2` 已 commit 未 push：**單選 `fc484d6`／M1 `2894cb8`／KB-1 `12c2bb3`／K5 `29add13`／M1c＋M1c-fix `06a7a94`／M1e `6eea877`**（jar 部署版仍係單選 `7bec6043`，未 build 新 jar）。**閘況（真跑）**：forge `compileJava+compileTestJava` RC=0；**31／32 harness OK**（唯一 `ItemRefCheck`＝環境性 `Not bootstrapped`）；python **106 檔 = 3 baseline FAIL**（＋`check_ask_display_leak` 環境性 RC=2 冇真機 log）；雙樹 check `paused=True fail=0`。**副線**：研究＋成本＋渠道＋文案已入 repo（見 `side-quest-money` log），SK 已開 Gmail 專用＋閒魚（`skps00`）。
- **唔准郁**：trace 事件名／欄位語義、prompt／卡／scrub／渲染行為；`neoforge/1.21.1`（**暫停**，只落 forge）；`modularToolSingleItem` key；共用知識庫只收 **mod 知識**（KubeJS／pack id 唔准入庫）；`AGENTS.md`（要 SK 明確 go）。
- **未解**：① 新 jar（含 M1c-fix＋M1e）**未 build／未部署**，MC 開住唔准換（只准 `hermes/scripts/deploy_packai_jar.py`）② M1d（`/reload`／`F3+T` 後索引自動失效）**未做**（M1e 只 refresh bridge，未 invalidate mechanic scan）③ Settings A／B／C 三批未開工 ④ KB-2（GitHub pull）未做 ⑤ M1e bridge 真機未驗（`Pack AI kubejs bridge hits=… mode=api|scan`）⑥ 副線：閒魚服務類**唔支援網頁發佈**（要 App）；大陸 MC 服務需求未驗證（7 日免費答題測試中）
- **下一步（優先序）**：① SK 熄 MC → build 新 jar → `deploy_packai_jar.py` → 真機驗（`bridge hits`／`mechanic facts`／`knowledge`）② M1d 索引失效 ③ Settings 批 A ④ KB-2 ⑤ 副線：Fiverr 開 gig（英文客）＋7 日免費答題驗需求 ⑥ 出貨 bump `0.2.2`＋push
<!-- STATE:END -->

## 2026-09-14 12:40–15:30（Discord session）— M1c／M1c-fix／M1e 落地 ＋ 副線「搵第一筆錢」

- **M1 `2894cb8`**：mechanic facts（KubeJS use/drop handler＋FTB 任務文字、pack-local、tier A/C）；M1b 修「變數間接機率」抽唔到 → 12 harness 綠。
- **KB-1 `12c2bb3`**：本地知識庫（`config/packai/knowledge`＋`knowledge_lookup`＋unknown 去重）；13 harness 綠。
- **K5 `29add13`**：雙樹 lockstep check 暫停感知（負向控制：人造 drift `--no-paused` 仍 FAIL）。
- **凍結事故（09-14 第二次熱換 jar）**：M1 同步掃成個 kubejs 樹（649 scripts／374MB）→ 主線程卡死；回滾 jar `7bec6043`＋寫 `hermes/scripts/deploy_packai_jar.py`（MC 開住 REFUSED RC=2）＋skill `references/packai-deploy-and-scan-incidents.md`。
- **M1c＋M1c-fix `06a7a94`**：只掃 3 個 scripts 目錄＋文字白名單（374MB→~14.5MB）；背景索引；`not-ready` 回空（<50ms 斷言）；`warmup()` 令 class 初始化離主線程。**驗**：31／32 harness OK、python 106＝baseline 3、雙樹 paused PASS。
- **M1e `6eea877`**：`KubeJsApiBridge`（reflection 讀 KubeJS `EventGroup`→`extraEventContainers`→`extraId/source/line`＝A 級真值）＋有界掃描 fallback＋log `Pack AI kubejs bridge hits=<n> mode=api|scan`。**驗**：RC=0、31 harness OK、`check_kubejs_bridge OK`。**已知**：`/reload` 後 mechanic scan 未 invalidate（M1d）。
- **派工教訓**：第 2 次 M1e 派工殭屍化（55 min 零寫入、report 0 bytes）→ 殺掉、**收窄範圍＋`--force --model auto`** 重派即成功；cursor 交貨嘅 forward-reference compile error 已用微修派工修正（`DEFAULT_SCAN_MAX_*` 搬前）。
- **副線 `Documents\side-quest-money`**（獨立 git repo）：5 條並行研究（185 findings，A/B/C/D 分級）→ 報告 `plans/2026-09-14-easiest-money-route.md`；**成本＋最差情況**（Cursor US$20／Gemini HK$38／DeepSeek 餘額 **¥300**，30 日實測用量換算 **¥188–376／月** → runway **0.8–1.6 月**；家用 HK$5,000／月＋朋友支援 HK$500／月 → 淨流出 HK$4,922–5,223／月，儲備 HK$25,000 → **runway ≈5 個月**）；**渠道實測**：閒魚服務類唔支援網頁發佈、MC 掛牌以商品為主（帳號／光影）但英文市場有真買家（Fiverr 供給不足）、中國サーバー圈有成文價目表 ¥30–3,000 → **策略改：服主插件／包月技術支持為主，玩家排查只做引流**；已備 3 條掛牌文案＋封面圖＋7 日免費驗證計劃。
## 2026-09-14 09:00–12:40（Discord session）— 單選／Settings 重做／知識庫 repo／M1 機制掃描

- **K3 到貨並親驗**（`f7d453c`）：`isModularRef`／`filterModularExtras`（只剔其他模組化工具、保留其他物品）＋`InvPickScreen` 拒揀第二件；真機 log **5 條 `Pack AI modularToolPickRefused`**（兩個方向都中）。**K4**（`61d6600`）：`check_dual_tree_sync.py` pause 感知；負向控制（人造 drift → `--no-paused` FAIL）已證。
- **S1＋S2 單選（SK 定案 a）**：`InvPickScreen.applySinglePick()`（點另一件＝取代、點自己＝取消）＋`trimPending()`（seed 只留 1、finish 前 trim）＋lang ×3「已選 1 件（再點即更換）」＋README／CF 描述 8→1＋新 harness `AskInvPickCheck`（4 case）＋python 加嚴（只加唔刪）＋log `invpick pending trimmed`。**驗**：forge compile 0 error＋7 harness OK（含新）+python 103 檔＝baseline 3 FAIL；**commit `fc484d6`**；**jar `7bec6043` 已部署**（備份喺 `%TEMP%\packai_deploy_backup_20260914_1151\`）。**待 SK 真機試**。
- **多物品亂版根因 ＋ 擱置**：`RecipeEmbed.splitTextIntoStepBlocks` 只認 `## 標題`／`1.`，唔認 `[[item:id]]` → 物品標題黏上一段；步驟編號唔逐件重數；卡無歸屬。**SK 拍板單選解決**，排版計畫 v2（P1–P6）擱置入 `docs/plans/2026-09-14-multi-item-reply-layout.md`。
- **Settings 頁重做計畫 v2**（`9bbf66f`／`f110a67`／`bcb289e`）：吸收反方 review **7:3**（方向贏、執行輸）——修正：① 真 config-only **5 個唔係 7 個**（`recipeCategoryOrder`／`Hidden` 已有 `RecipeCategoryScreen` UI，SK 定 1b＝新頁取代＋刪舊 screen＋清 11 條 lang）② `modularToolSingleItem` 唔准單邊刪（同單選 plan 衝突）③ 存檔語意寫死（即時生效；`onClose` 自動存 key；**佔位符唔准覆蓋真 key**）④ 防漏 check 由「假綠」改成 8 條真斷言（全路徑／三語 lang 齊／setter 有 save／round-trip／separator／fail-closed）⑤ 切 A／B／C 三批 ⑥ i18n 工作量修正為 ~240 條 ⑦ 版面 mock **實測**：新三欄一屏 6–7 行 vs 今日 7 行 → KPI 改「可搜尋＋可分類＋可增長」（唔再聲稱解決 480p）。
- **知識庫 `skps00/packai-knowledge`（public）已開**：README（範圍＝只收 mod 知識）／`schema/entry.schema.json`／`examples/example-entry.json`（用真 mod item `create:goggles`；**SK 更正：`momo_dlc` 係 KubeJS 命名空間唔係 mod，唔可做例子**）／`scripts/validate.py`（schema＋**去重 key**＋tier A/B/C＋`source` 必填）／`CONTRIBUTING.md`／issue template／`index.json`／`.github/workflows/validate.yml`（CI）。**實證**：validator 本地負向控制（重複 → FAIL）；**GitHub CI 負向控制**（臨時分支推重複資料 → run `34802793400+` **failure**，已刪分支 404 確認）；`gh auth refresh -s workflow` 已做（SK 授權一次）。**KB-1（mod 內）未開工**。
- **M1 機制事實層**（`KubeJSMechanicScan`／`QuestMechanicFacts`／`AskMechanicFactsCheck`／`tests/check_mechanic_facts.py`；config `kubejsMechanicScan`／`questMechanicFacts`／cache 上限）：scan 已改 `AskService`（facts 注入）＋`PackAiConfig`（4 key）；**compile OK**。**我實跑 harness 捉到真 bug**：`CHANCE_*` 只認同一句 `Math.random()*Math.random()*100 <= N`，但真 pack（`momo_dlc_entity_death.js`）係 `let random = …` ＋下一句 `if (random <= 1)` → **機率抽唔到**（正正係 SK 問嘅 1% 個案）→ **M1b 派工修復中**。
- **環境／流程**：VS Code 開唔到 → 真因＝VS Code 自動更新嘅 `CodeSetup-*.exe` 仍揸住 `vscode-updating` mutex，主程式卡喺 `checkInnoSetupMutex`（log 實錘）；kill 兩個殘留 setup 進程即復原。cursor 派工注意：`--model composer-1` **唔存在**（M1b 首次派工因此 0 改動）→ 用預設模型（唔傳 `--model`）。


## 2026-09-14 06:00–07:00（Discord session）— trace S0 落地 ＋ 親驗逼出 4 輪 compile／真 bug

- **S（trace S0）到貨**：cursor 交雙樹（未 commit）；**自報明言冇跑 build**（shell 被封）→ 我實跑 gradle：**兩樹 4 個 compile error**（AskService lambda 捕捉非 effectively-final：`scrubbed`×2、`recipeCards`×2）。
- **T**（cursor）修 AskService 4 錯＋我自寫嘅 `check_dual_tree_diff_symmetry.py` 誤報：加 documented `SHIM_NORMALIZE`（`ForgeConfigSpec`/`ModConfigSpec`→`ConfigSpec`；`WidgetCompat.tipLines(`/`tip(`→`TIP(`）；negative control 6 case 證仍嚴格（RC=0）。
- **F2**：第二輪 compile 剩 AskEngine `:326`（`loop`）＋`:850`（`llmAnswer`）→ hoist `final` snapshot。
- **F3**：`AskTraceCheck -ea` 實跑捉到**真 bug** —— `index.jsonl` 嘅 `file` 被自家 masker 食成 `"a***.jsonl"`（`sk-[A-Za-z0-9_-]{8,}` 撞檔名 `ask-2026…`）→ 修：regex 加 lookbehind（token 開頭）＋`writeIndex` 只 mask `question`；順手修兩個語義問題：`check.cards` placement/reason 調轉、`finishAskTrace` 出嘅假數值 `render.cards`→改 `render.cards.final`（誠實欄位）。
- **F4**：F3 引入新 6 錯（`first` 被 loop reassign）→ `final RecipeCard firstCard = first;`。**第四輪 compile 全綠**。
- **收貨證據（全部真跑）**：forge `compileJava+compileTestJava` SUCCESS；neo `compileJava` SUCCESS；`AskTraceCheck -ea` **兩樹 OK**（neo 用 javac 手動編＋跑，因 neo 測試 classpath 舊壞）；`AskReplyScrubCheck` OK（gradle JavaExec）；python **103 檔 3 FAIL = baseline**；`AskTrace`／`AskTraceCheck` 兩樹 **byte-identical**；無殘留探測檔（臨時 gradle init script 已刪）。
- **還原方案（已備份）**：`%TEMP%\packai_trace_backup_20260914_0647\`（`AskTrace*.java` ×4 ＋ `working_tree_tracked_changes.patch` ＋ stat）→ 還原＝`git checkout --` 追蹤檔＋刪兩樹新檔。
- **教訓（已入 skill `references/packai-ask-trace-s0-2026-09-14.md`）**：cursor 手寫 lambda 捕捉會被 reassign 嘅 local ＝反覆出現嘅系統性缺陷（今次 3 輪）；**每輪都要自己 compile**；masker 唔准掃整條序列化 record。
- **K2 派工**：**K2**（Tetra 模組化工具：`modularToolSingleItem` default true＋單件模式＋排除空框架卡＋卡貼對位；指示 `%TEMP%\cursor_packai_k2_instructions.md`）06:48 派、**已到貨並驗收（見下）**。⚠️ 原 **K**（02:50 派）死得無聲（report 0 bytes、cursor 無開 chat、repo 零痕跡）。
- **待 SK 決定**：① 真機試 K3 UI（要 SK 熄 MC 換 jar：`InvPickScreen` 揀第二件模組化工具應被拒）② K3／K4 commit 時機 ③ neo `build.gradle` test classpath 加 gson（已因暫停而作廢，除非恢復 neo）。
- **K2（Tetra 模組化工具單件模式）到貨並親驗**：新 config `modularToolSingleItem`（default true，Settings → Recipes 分頁）＋`AskService.isModularToolFocus`／`applyModularToolSingleItem`（log `modularToolSingleItem applied`）／`suppressModularFrameCards`（log `frameCardsSuppressed`）／`AskCardFallback` 加 4-arg `ensureCards(..., dropFocusOutputId)`＋mention 對位（`tryInsertAfterMethodsByMentions`／`findMentionInsertPos`）／`RecipeCard.mentionKeys()`／`RecipeEmbed` bestScore=0 → section end；新 harness `AskCardPlacementCheck`（雙樹 byte-identical）。
- **K3（模組化工具只可選一件，SK 定案）到貨並親驗（只改 forge；neo 已暫停）**：`AskService.isModularRef`／`filterModularExtras`／`applyModularToolSingleItem` 只剔其他模組化工具、保留其他物品（log 加 `kept=`）＋`InvPickScreen` 拒絕揀第二件（紅框／暗罩／log `modularToolPickRefused`／lang ×3）＋新 harness `AskModularPickCheck`＋python 加嚴（forge-only）。**驗**：forge `compileJava+compileTestJava` SUCCESS（0 error）＋5 harness 全 OK；python 103 檔 3 FAIL = baseline；`check_dual_tree_sync` PASS（AskService／InvPickScreen 本來就喺 allowlist＝WARN 級）＋diff_symmetry OK。**未做**：jar 未 build、未真機試 UI（SK 打 CS2 中）、未 commit。
- **NeoForge 暫停（B）**：`neoforge/README_PAUSED.md`（暫停點 `9ec0ebc`＋恢復步驟）＋`README.md` Supported 表／notice＋`docs/CURSEFORGE_DESCRIPTION.md` 同 `dist/_cf_desc/description.html`（各 7／6 處）→ commit `c97d61c`；**已 push**（`4886346..c97d61c`）；GitHub **issue #20 已開＋已 pin**；CurseForge 描述 **live 頁面親核**（`has_paused=True`、舊『kept in sync』已無）——⚠️ CF Core API 讀取有快取（會假報舊版），要用 CDP 瀏覽器讀 live 頁（已入 skill）；1.21.1 檔案保留可下載（SK 選 a）。
- **K4 完成並親驗**：`tests/check_dual_tree_sync.py` 加暫停感知（`neoforge/README_PAUSED.md` 在＝paused）＋`--paused`／`--no-paused` 覆寫。**驗**：預設 → `PAUSED` banner + `SUMMARY paused=True fail=0 warn=65` + RC=0；**negative control**（人造 drift：forge `AskEngine.java` 尾加一行注釋）→ `--no-paused` ＝ `FAIL (1): byte drift between trees: logic/AskEngine.java`（嚴格路徑保住）、同一 drift 喺預設 paused 下＝PASS 只 WARN；事後檔案 sha256 還原一致（`eb7bb4149e`）、`git status` 乾淨。
- **環境注意（2026-09-14 08:1x）**：`check_ask_display_leak.py` 預設讀 `<instance>/logs/latest.log`；MC 重開後 `latest.log` 會被 rotate 成 `2026-09-14-N.log.gz`，檔案唔在 → 該 check 會 exit 2 印 `NO LOG LINES`（**唔係 regression**）。用真 log 驗：`gunzip -c <gz> > <Windows路徑>` 再 `--log <Windows路徑>`（⚠️ 一定要 Windows 路徑，native python 讀唔到 `/tmp/...`）→ 2026-09-14 實測 `lines=4 nonempty=4` + `check_ask_display_leak OK`（即係 K2/jar `70133fc6` 嘅真機答案 body 乾淨）。
- **F5**：K2 令 2 個 python check 結構 assert 過時（`check_ask_card_fallback.py` 舊 2-arg call 文字；`check_maintenance_intent.py` 舊 `collectOutputQuestIndices` 簽名）→ 改成更嚴（4-arg regex＋async/blocking 兩路都要認＋signature 第 3 參要喺 body 用到），產品碼零改。
- **最終驗收（2026-09-14 07:25 真跑）**：forge `compileJava+compileTestJava` SUCCESS（0 error）＋4 harness 全 OK；neo `compileJava` SUCCESS；python 103 檔 3 FAIL = baseline；對稱 OK；`card_placement_test.py` sulfur／iron／mixed RC=0；雙樹新檔 byte-identical；`check_handoff_size.py` PASS。**未做**：jar 未 build、未 commit、未真機煙測。


## 2026-09-13 12:3x — DSML plan v3 開工：T6（P1+P2+P5）完成並驗收

**做咗**（commit `e85c4a5`；之前 T1–T4 = `db245f5`）：
- **P1**：`AskToolLoop:58` DSML_PIPE 同 `AskReplyScrub:68` DSML_PIPE_RUN 都改 **`{1,4}`**（有界，唔用 `+`）—— 修「真機雙全角豎線 `｜｜` 令偵測＋解析完全隱形」＋順手修 `abbc698` 引入嘅 catastrophic backtracking。
- **P2**：`callFromDsmlParams` 補讀 `item_id`／`role`／`machine`；新增 `canonicalArgsJson`，**recovered（`AskToolLoop`）同 native（`LlmClient:705-713`）兩邊都經 `canonicalizeCall` + `canonicalArgsJson`** → 指紋一致（解跨路徑重複執行）。
- **P5**：`tools/extract_dsml_fixture.py`（可重現抽 log）＋`tests/fixtures/dsml_real_doubled_2026-09-13.txt`（604 chars／64×U+FF5C）；`AskToolLoopCheck` 修 compile 錯＋加 K30–K34；`check_ask_display_leak.py` 加 junk assertion（負對照 OK）；新 `check_dsml_grammar_sync.py`。

**Hermes 親自驗收（真執行）**：偵測 `false→true`、解析 `0→2` call（唔再塌）、端到端 `result` 由「泄漏原文」變 `FINAL_ANSWER` ＋真執行 2 個 call（`OUTPUT`／`uses`＋machine）、跨路徑去重 2 次（冇 P2 會 3 次）、病態效能 4134ms→**25.5ms**（n=50k 36061ms→0.6ms）、全量 96 PASS／5 FAIL＝baseline、雙樹對稱、禁區無改。

**未驗**：① `AskToolLoopCheck` K30–K34**未跑**（headless javac 追唔完依賴圖，要 gradle classpath → 留 T5 一併跑）；② mod 全量 compile 未跑；③ 真機煙測未做。
**已知殘留**：hop-limit 出口 `:364-366` 過 P1 後**仍原封吐回 leak** → P3（等 T5 數據決定做唔做）。

**下次**（優先序）：
1. **T5 真機煙測**（要 SK 熄 MC）：build jar → 備份舊 jar → 部署 → SK 問 15 次 → 跑 `check_ask_display_leak.py`（真 log 行）＋ `check_jar_contains_fix.py` ＋ `AskToolLoopCheck`（gradle）→ 出報告。
2. 用 T5 數據決定 **P3**（hop-limit 出口 recovery）。
3. Chrome：`bg_launch.py --minimized` 已交（1c）；`focus_probe` 結果：只有「最小化開」零搶焦點。

- **2026-09-13 13:2x（T6 收尾）**：P1/P2/P5 完成親驗（真 bytes 偵測 false→true、解析 0→2、效能 4134→25ms、跨路徑去重 2 次）；commits `db245f5`＋`e85c4a5`；plan v3 已入 `docs/plans/`（`4886346`）；**只欠 T5 真機煙測**（要 SK 熄 MC）；K30–K34 harness 未跑（要 gradle classpath）
## 2026-09-13 10:4x — T3/T3b/T4 完成並驗收（DSML plan v4）

**新增（全部 Hermes 親驗）**
- **T3**：`obtainFill` 過 `playerSafeFacts`；offline dump 過濾＋honest miss；新 key `ask_miss_summon_player`／`ask_miss_acquire_player`（6 檔）。
- **T3b**：`fact_check` 抽走 tool 名 → 新 key `packai.reply.fact_check_tools_note`；`ReplyLang.factCheck(code, toolsOffered)`（toolsOffered=false 唔 append）。
- **T4**：UI 交接口 log 行 `Pack AI display body ver={} src={} {}`（`client/service/AskService.java:328/:363/:376`）＋`AskResult.displaySrc`（default `UNKNOWN`）；新 check：`check_ask_display_leak.py`（正數＝真機 log；`--fixture` 可離線）、`check_jar_contains_fix.py`、`check_dual_tree_diff_symmetry.py`、`check_prompt_notools_no_toolwords.py`＋`tests/fixtures/ask_display_leak_2026-09-13.txt`。
- 驗收：harness OK 兩樹；雙樹 added-lines 全對稱；**偵測器正負對照**（乾淨 fixture→PASS、舊 leak fixture→FAIL 捉到 `render_recipe_cards`）。

**收貨狀態**：全量 **100 檔 / 5 FAIL** ＝ baseline 3 ＋ 2 個「等 T5」嘅 check（`check_ask_display_leak.py`＝NO LOG LINES、`check_jar_contains_fix.py`＝冇 jar，未 build）。**T5 之後收貨＝只准 baseline 3 FAIL**。

**下一步 T5（要 SK 批准＋揀時間）**
1. `gradlew build`（食 CPU 幾分鐘）
2. **先備份** `mods\packai-*.jar` → `.bak-<ts>`，再放新 jar 落 Prism instance `AI_test_NFWC_DIM`
3. SK 真機問 15 次（10 次同一問題＋5 次混合）→ 跑 `python tests/check_ask_display_leak.py`（應該 exit 0）＋睇 `src=` 統計 `fallback_rate`（**≥1/10 就要收窄白名單**）
4. 還原方案：copy 返 `.bak-*` 舊 jar 即回到現狀（零資料損失）

## 2026-09-13 10:xx — DSML/FACT-leak plan v4 開工（T1/T2/T3 完成並驗收）

**Plan**：`.hermes/plans/2026-09-13_120000-dsml-fact-leak-player-safe.md`（v4；R1 7:3 → v2、R2 8:2 → v3、R3 7:3 → v4、R4 **8:2 達標** → 開工）

**已完成（每步都由 Hermes 親自驗，唔信 cursor 自報）**
- **T0 baseline**：96 檔 check、3 FAIL、`AskReplyScrubCheck:193` AssertionError 原文已記入 plan §0。
- **T1＋T1b（prompt 去矛盾）**：`LlmClient.toolsOffered(base, toolNames)` 收口 3 份副本；`sendTools=offered`（`:506`）；`llmStyle(code, toolsOffered)`；6 lang 檔新 key（`llm_style_notools`／`reply_pattern_notools`／`purpose_first_notools`）＋`fact_check` 去 tool 名。新 check `tests/check_prompt_notools_no_toolwords.py` exit 0。
- **T2（顯示層 fail-closed）**：`scrubPromptEcho` 加 `leftoverToolMarkup` → 回空；`proseOrFacts(llmAnswer, playerFacts, blankFallback)`；新 key `ask_body_unavailable`（6 檔）；`AskReplyScrubCheck:193` 過時斷言修好＋K20/K21 新 case → **harness OK RC=0 兩樹**。
- **T3（覆蓋清單）**：`obtainFill` 過 `playerSafeFacts`；offline dump 過濾＋honest miss；新 key `ask_miss_summon_player`／`ask_miss_acquire_player`；`ensureNonEmptyBody` repair 輸入 player-safe、輸出經 fail-closed。
- 每步驗收：全量 **97 檔 / 3 FAIL = baseline**、雙樹 added-lines 全對稱、`collectAllowed(factMarkerSources)`／`factsFull` 未動。
- **偏差（有記錄）**：`collectAllowed` 唔改（只抽 UI marker）；T3b 殘留（`fact_check` 仍含 `jei_lookup` 條件句）併入 T4。

**進行中**：T4（UI 交接口 log 行＋`AskResult.src`；`check_ask_display_leak.py`、`check_jar_contains_fix.py`、`check_dual_tree_diff_symmetry.py`）＋T3b。
**未做**：T5 真機煙測（要 build jar → 部署 → 10 次同一問題＋5 次混合）→ **要 SK 配合**；jar 未 build。

**坑（避免重複）**
- cursor sandbox：寫得到 packai repo，但 **shell 全封**（gradle／java check 一律 NOT RUN）→ 驗證一定要 Hermes 自己做。
- `check_dual_tree_sync.py` 對 `ReplyLang.java`／`AskService.java` **只 WARN**（allowlist）→ 唔可以當雙樹同步證明；要用 git diff added-lines 對稱法。
- `unzip -p … | javap` 會假綠（exit 0）；要解壓落目錄再 `javap -p -c`。

# HANDOFF — 2026-09-12 凌晨 session（兜底路徑漏內部 FACT 實錘 + config `off` 未還原）
> 固定檔：session 完結/開新前更新。上一版內容見 plans/archive/（冇存就覆蓋）。

## 2026-09-13 09:2x — packai DSML／prompt-leak：plan v4 通過 adversarial **8:2** → 開工 T1

- Plan（**v4 定稿**）：`.hermes\plans\2026-09-13_120000-dsml-fact-leak-player-safe.md`
  - Review 流程：R1 反方 7:3 → v2；R2 反方 8:2（6 個載重決定 5 個死）→ v3（單一改動，刪假「兩階段」）；R3 反方 7:3（有界 flip-check）→ v4；**R4 = 8:2（計劃）達標** → 開工。
- **T0 baseline 已實跑**（證據入 plan §0-T0）：
  - `tests/check_*.py` = **96 檔 / 3 FAIL**（`check_ask_tool_context`、`check_heavy_script_corpus`、`check_recipe_io_and_consume_use`）
  - `java -ea AskReplyScrubCheck` = **AssertionError at `AskReplyScrubCheck.java:193`**（`Hold [shift] + rmb read more`）→ `:276-283` 從未執行
- **T1 已派 cursor**（prompt 去矛盾）：tools 條件 3 副本收口成單一 helper（`LlmClient:479-482`／`AskEngine.capableForTools():728-731`／`urlLacksNativeTools()`；必須收 local `base`，唔可以讀 `lastBase`）＋ `ReplyLang.llmStyle(code, toolsOffered)`（false 時用新 key `llm_style_notools`／`reply_pattern_notools`，跳過 `recipe_cards_ai_marker`；true 時逐字不變）＋ 6 個 lang 檔（3 lang × 2 樹，各 454 keys）＋ 更新 `tests/check_reply_prompt_keys.py:70/:101/:449`（雙向 assert）。
- **未做**：T2 顯示層分家（`playerFacts`＋`scrubPromptEcho` 閘）、T3 覆蓋清單（`missBody`／`:834`／`:878-899` 離線 dump／`AskService` 7 call site＋4 定義）、T4 收貨工具（UI 交接口 `display body ver=<buildId> src=…` log＋fixture 程式抽取＋`javap -p -c` assert＋逐檔 md5）、T5 真機 15 問煙測。
- 關鍵設計（唔可以走回頭路）：**玩家可見文字只准用 `playerFacts`**（唔准貼模型 payload）＋ **fail-closed**（唔確定唔出街）＋ 唔再加寬 regex 黑名單（已 4 次失敗）。

---

## 2026-09-13（Discord session）—— DSML 洩漏第 3 次：實錘＋架構評估（未改 code）

**1. 實錘（唔使 SK 再描述）**：SK 問一次（07:50:52→07:51:03，問「猛者瓶怎麼用」）真機 log（`AI_test_NFWC_DIM/minecraft/logs/latest.log`，**cp950** 編碼）：
- 3 輪 `Pack AI LLM raw reply chars=0 toolCalls=4`（正常 native tool call）
- 第 4 輪 `chars=604 toolCalls=0 body=<｜DSML｜calls>…<｜DSML｜invoke name="render_recipe_cards">…` → **模型將 DSML 標記當普通文字放入 content**
- 之後 `ask reply before ensureCards: 怎么用`、`toolCards emission=3 cardsOut=3`
⇒ 玩家見到嘅「prompt 樣文字」= DSML 標記洩漏。**同 09-11／09-12 兩次 DSML 修復同類 → 第 3 次**（`900a7e4`／`96ad78a`／`1ba048f`／`abbc698`）。

**2. 未 commit（等 root-cause 一齊 commit）**：兩棵樹 `LlmClient.java` 各 +18 行 = raw-reply 診斷 log（`Pack AI LLM raw reply chars=… toolCalls=… body=…`，已 deploy 入 jar `packai-0.2.1+mc1.19.2-forge.jar` 09-12 07:07 → 正因為有佢，今次先捉到實錘）。

**3. 架構評估（SK 提「fork deepseek hermes 成 MC mod?」）**：已寫 `plans/2026-09-13-agent-architecture-fork-vs-light.md`。
- 結論：**唔好 fork**（Voyager 等 SOTA 都係「agent 喺遊戲外」；CurseForge 同類 mod 全部 thin client + 玩家自備 key；Hermes 係 Python／mod 係 Java／雙樹維護）。
- 公開線 = **輕量硬化**：刪走文字協議（DSML），只信 native `tool_calls`／strict JSON；答句渲染前加 deterministic 硬閘（含 markup 一律唔出／當 tool call）。
- SK 自用線 = **sidecar**（Hermes 本機 + mod thin client，零 fork），並行做、唔影響公開版本。

**4. 進行中**：cursor read-only **root-cause 討論**（model = `cursor-grok-4.6-xhigh`）——問清模型點解喺 content 出 DSML、`AskReplyScrub` 邊條 regex 漏、答句 pipeline 邊個位漏、4 個設計選項成本/風險、建議＋最脆弱位。報告出 → Hermes 自己核 → 才決定 fix 輪。

## 2026-09-12 07:0x（SK 批「do 1-3, 4 hold」）—— ①DSML fix 已 push ②raw-reply log 已加／已建／已 deploy ③jarvis-pc 63 commit 已 push（feature branch）

**1. ① DSML scrub fix 上 GitHub ✅**
- commit **`abbc698`**（`fix(ask): harden AskReplyScrub for doubled-pipe DSML variant`，雙樹 4 檔）→ push 到 **`origin/main`**；`git ls-remote` 實證 remote main = **`abbc698d9e2b`**（local ahead 0）。

**2. ② raw-reply log（診斷用，等 SK restart MC 收數）**
- 改動經 cursor-agent（instructions `%TEMP%\cursor_rawreply_instructions.md`、report `%TEMP%\cursor_rawreply_report.md`）：`LlmClient.completeRound()` 解析 response 後、`return new LlmRound(...)` 之前加
  `PackAiMod.LOGGER.info("Pack AI LLM raw reply chars={} toolCalls={} body={}", content.length(), calls.size(), rawReplyForLog(content));`
  ＋新常量 `RAW_REPLY_LOG_CAP = 4000`（class 頂）＋ 新 helper `rawReplyForLog(String)`（放 `completeRound` 之後）：換行 escape 成一行、上限 4000 字、超出接 `...[+N chars]`。
- **自己驗證（唔信自報）**：`git diff` 只有兩檔；**`cmp` 雙樹 byte-identical（exit 0）**；`\r\r\n` = 0；冇殘留檔（`_*`／staging 皆無）；`calls` 由 `parseNativeToolCalls()` 保證非 null（`List.of()` 兜底）→ `calls.size()` 安全。
- Build：Forge（JDK17）**BUILD SUCCESSFUL 27s**、NeoForge（JDK21）**BUILD SUCCESSFUL 14s**（兩邊 `--rerun-tasks`；`--max-workers=2` 因為 SK 打緊 CS2）；**class bytes 實證**含 `raw reply chars` + `rawReplyForLog`。
- jar：forge **`17ebc474`**（993,380B，07:07）、neo **`4817ec2c`**。
- **已 deploy**：`instances/AI_test_NFWC_DIM/minecraft/mods/packai-0.2.1+mc1.19.2-forge.jar`（sha = build 一致 `17ebc474`；舊 jar 備份喺 `%TEMP%\packai_deploy_backup_20260912_0708`，**mods/ 只留一個 packai jar**）。
- ⏸ **未 commit**（跟 DSML fix 先例：真機 smoke PASS 先 commit）。**要 SK restart MC → 再問同一題** → log 應出 `Pack AI LLM raw reply chars=… toolCalls=… body=…` → 之後可定案「①model 照抄 payload」定「②`proseOrFacts()` 貼 facts」。

**3. ③ jarvis-pc 63 個 commit 已 push ✅（但只上到 feature branch）**
- `git push origin HEAD` → **`origin/feature/hermes-alerts-mcp`**（`a276049..8e97a9b`）；`ls-remote` 實證 = `8e97a9be6754`。
- ⚠️ **`origin/main` 仍然係 `ca463a3`**（08-10 PR #11）→ 63 個 commit 未入 main；要 SK 決定開 PR 定直接 merge。

**3b. SK 決定（2026-09-13 00:5x）：兜底路徑漏 FACT → `test it before go`**
- 即係**先收真機 log 定案 ①model 照抄 payload vs ②`proseOrFacts()` 貼 facts**，然後才修；唔可以未證就改。
- 現況實查：instance jar = `packai-0.2.1+mc1.19.2-forge.jar` sha256 前綴 **`17ebc474`**（= 有 raw-reply log 嗰個 build，07:07 deploy）；`config/packai-client.toml` = `askNativeTools = "auto"`（22:15 寫入 ✅）；MC 22:14 已 restart（`logs/latest.log` 由 22:14:42 起）→ **`Pack AI LLM raw reply` 行數 = 0**（restart 後未問過）。
- 測試步驟（要重現兜底路徑）：① SK 關 MC → ② Hermes 改 `askNativeTools="off"`（**遊戲關咗才改**，改完讀檔驗）→ ③ SK 開 MC 問同一題（哭泣的黑曜石「怎麼來／怎麼用」）→ ④ 出 game → ⑤ Hermes 讀 `logs/latest.log` 嘅 `Pack AI LLM raw reply chars=… toolCalls=… body=…` 對比 payload → ⑥ 還原 `auto`（讀檔驗）。
- ⏸ 等 SK 關 game 先做（未動任何 config）。

**4. 「4」（語音 ASR 三選一）hold，未動。**

---

## 2026-09-12 06:20（SK reboot 後，`auto` 模式真機驗收）—— DSML/整段回吐 **PASS**；但捉到 FACT↔答案 矛盾（新，未拍板）

> 觸發：SK 重啟 PC → 06:19 已入 game（Prism `AI_test_NFWC_DIM`，新 jar 生效）→「just asked in auto」（問 哭泣的黑曜石「怎麼來／怎麼用」）。
> 本 session 只讀 log／檔，**冇改 code／config**。

**1. 環境實錘（開機後）**
- `LastBootUpTime` = 09/12 **06:15:38**（真 boot，非 sleep）；Prism 06:18:14 / javaw 06:18:31。
- instance jar = `packai-0.2.1+mc1.19.2-forge.jar`，sha256 前綴 **`012da9cc`**（= 09-11 DSML fix jar）→ 今次 run 跑嘅係有 fix 嘅 build。
- `config/packai-client.toml` 讀檔實證：`askNativeTools = "auto"` ✅（唔再係 `off`）。
- instance 路徑（實測，之前寫錯過）：`C:\Users\skps9\Documents\PrismLauncher-Windows-MSVC-Portable-8.0\PrismLauncher-Windows-MSVC-Portable-8.0\instances\AI_test_NFWC_DIM`（Prism 係 portable，唔喺 `%APPDATA%\PrismLauncher`）。

**2. 驗收結果（log：`logs/latest.log` 4928 行，06:20:53–06:21:09）**
| 項目 | 實錘 | 判定 |
|---|---|---|
| DSML 漏 markup | reply 內零 `｜`／`to=functions`／tag 殘留（逐字睇過） | ✅ PASS |
| Arch-3/3a 冇 regression | tool path 正常：2 輪 LLM（7→10 messages）、`renderCards ... role=uses scannedCats=24 foundOutput=0 afterFilter=24`、`toolCards emission=3 cardsOut=3` | ✅ PASS |
| 卡／文字配對 | 正文有 3 個 numbered step，各帶 `[card:1..3]` 錨（無孤兒卡，符合 SK UX 規則） | ✅ PASS |
| 「整段回吐」 | ❌ 冇出現（`auto` 行 tool path，唔係兜底）；答案係結構化短文，唔係 raw FACT payload | ✅ PASS |
| 【來源】尾行 | `ReplySources.ensure()` 加嘅正常尾行 | ✅ 正常 |

**3. ⚠️ 新捉到：FACT 注入 ↔ 最終答案 直接矛盾（未拍板）**
- FACT head（line 4866，`trace askJei`）：`【JEI】有配方卡（Crafting）。优先合成路径，勿宣称无法合成或仅掉落。`
- 最終答案（line 4913-4925）：「**本包 JEI 并未列出哭泣的黑曜石的合成配方，所以别指望用工作台合成。**」
- 客觀真相（同一 run 實錘）：`renderCards ... foundOutput=0` = **冇任何 recipe 以哭泣的黑曜石為 output** → **答案先係啱嘅，FACT head 講錯**（3 張 `cats=Crafting` 卡其實係 role=uses／以佢做材料嘅配方）。
- 影響：`有配方卡（Crafting）…勿宣称无法合成` 呢句係「以類別代替方向」，撞正 system prompt 第 8 條（禁止宣稱無配方）→ 即係**規則逼模型講錯**。今次 model 抗住冇跟（好彩），但呢個正正係 SK 講嘅「not every times」同類：**FACT 層冇區分 output vs uses**，靠 prompt 補唔到。
- 待辦方向（未拍板）：`askJei` head 應該按 `foundOutput`／direction 出（output=0 就唔可以講「有配方卡／勿宣稱無法合成」，應改講「本包無合成配方；以下係用途卡」）。

**4. 仍然未做**：① raw-reply log（分辨「model 照抄」vs `proseOrFacts()` 貼 facts）② commit + push 09-11 DSML scrub fix（4 檔，working tree 仍然 uncommitted、push 0/0）③ DSML 側 A/B/C。

## 今日（2026-09-12 凌晨 session，Discord）—— 兜底路徑「整份內部 FACT 漏出」（實錘）＋ 09-11 config `off` 未還原（我嘅漏）

> 起點：SK 截圖（哭泣的黑曜石「怎麼來」）→「hold it first, why this happen?」→ 查 log／jar／config。
> **本 session 冇改任何 code、冇改任何 config**（只讀檔 + 查 log + javap）。

**1. 症狀（截圖 = 玩家實際見到）**
- 答案正文 = **LLM-facing 內部文字**：「注意：JEI 可能混入同 id 的 NBT 變體配方…」「【JEI】有配方卡（Crafting）。优先合成路径，勿宣称无法合成或仅掉落。」「【JEI 资料】物品「哭泣的黑曜石」…（已完整扫描…）」「推荐合成／取得时，简易工作站优先于复杂机器…role=quest 是任务奖励／任务，不是锻造…」
- 加：內部 id（`bygonenether:chests/catacomb/treasure_rib`）、`[AS_INGREDIENT]` dump、`【来源】JEI (NBT variants may mix)、PURPOSE／提示、整合包本地配方、整合包掉落表／钓鱼／交易`、token 行「9.5k 入 · 1.2k 出」。

**2. 實錘（instance `AI_test_NFWC_DIM`，`minecraft/logs/latest.log`，01:59）**
```
01:59:09 LLM mode=cloud model=deepseek-v4-flash
01:59:09 LLM full prompt begin (17738 chars, 2 messages)   ← 單輪、冇 tools
01:59:16 LLM usage prompt=9450 completion=1237 total=10687 ← 就係截圖嗰行 token
01:59:16 ask reply before ensureCards: 怎么用
         <PURPOSE block> <[AS_INGREDIENT] 作為材料…dump> <掉落表 bygonenether:…>
         怎么来 / 注意：JEI 可能混入… / 【JEI】有配方卡…勿宣称… / 【JEI 资料】… / 推荐合成／取得时…
         【来源】JEI (NBT variants may mix)、PURPOSE／提示、…
```
- **逐字比對**：玩家見到嗰堆字同送去 model 嘅 payload **一樣**——連 `[AS_INGREDIENT]` tag 剝走後留低嘅**前導空格**都保留；`jei` payload 原句順序 =（recipe cards 0-2）→「【JEI】有配方卡…」→「【JEI 资料】…」→「[AS_INGREDIENT] …」。
- 尾行【來源】係 **code 生成**：`PURPOSE／提示`（`zh_cn.json:389`）、`整合包本地配方`（213）、`整合包掉落表／钓鱼／交易`（214）只有 lang 檔有 → `ReplySources.ensure()` 加嘅。
- **舊 bug 冇翻發**：deployed jar sha256 前綴 `012da9cc`（= 09-11 新 jar），`javap` 見到 `dropResidualDsmlLines()` + `DSML_PIPE_RUN` → 09-11 嗰個 DSML 顯示層 fix **確實在跑緊**，今日 reply 冇 DSML 痕。

**3. Root cause（分清已證／未證）**
- ✅ **已證**：玩家睇到嘅係 LLM-facing FACT／提示文字；post-LLM 只清 bracket tag（`[PURPOSE]` 等）／DSML／tool XML，對「整段回吐」**零防禦**。同 09-11 嘅結論一致：黑名單式追漏（上次 markup、今次 facts）。
- ⚠️ **未證**（欠 raw reply log）：係 ① model 照抄 payload，定 ② reply 被 scrub 到空 → `AskReplyScrub.proseOrFacts()` 貼 `facts` 兜底。兩者顯示一樣；mod 而家**只 log full prompt + usage，冇 log reply** → **先補 log 再落 fix**。

**4. Config 事故（SK 提出：呢個係 09-11 驗收測試要求嘅設定）**
- 09-11 為 Arch-3/3a 驗收暫設 `instances/AI_test_NFWC_DIM/minecraft/config/packai-client.toml` → `askNativeTools="off"`（檔內註釋：`#off = never send tools (today's marker/FACT path)`）。舊 HANDOFF 寫「測完 config 已還原 auto」→ **實際冇**，今日 01:59 全程行兜底。
- 02:09 SK 自己喺遊戲設定改返 `auto`（檔案 02:09:03 寫入 ✅）。`PackAiConfig.askNativeToolsMode()` 每次問答即時讀 `ASK_NATIVE_TOOLS.get()` → **唔使重啟遊戲**即時生效。
- 教訓（已寫入 memory）：mod config 要**遊戲關咗先改**（開住改 → 遊戲之後覆寫返記憶值）；「已還原」一律**讀檔驗**。

**5. 未 commit 狀態（延續 09-11）**
- 4 檔 modified：雙樹 `logic/AskReplyScrub.java` + 雙樹 `src/test/.../AskReplyScrubCheck.java`（= DSML fix，已驗、cursor review SHIP）。建議 message：`fix(ask): harden AskReplyScrub for doubled-pipe DSML variant`。
- ⚠️ `compileTestJava` pre-existing 壞（`AskReplyScrubCheck` L193 喺 HEAD 一樣 fail）→ 跑呢個 check 要單獨 `javac` + `java -ea`。
- MC repo push 狀態 = **0/0**（`1ba048f` 已喺 remote ✅）。

**6. Next（優先序）**
1. **加 raw-reply log**（一行：LlmClient 收到 reply 後 `PackAiMod.LOGGER.info("Pack AI LLM raw reply: {}", …)`）→ 雙樹 build → deploy → 重問同一題 → 定案 ①/②。**等 SK 拍板**。
2. 依定案修：①→ 唔好將指令文當 facts（或偵測「答案 ≈ fact wall」）；②→ `proseOrFacts()` 唔好貼 LLM-facing facts（要貼就貼 player-facing 版）。
3. commit + push 09-11 scrub fix（等 SK 一句）。
4. DSML 側 pending A/B/C（detection layer／recovery／close-tag cap）仍未拍板。

**7. 等 SK 拍板（本 session 收工狀態：冇郁任何 code／config）**
> 本 session 外部動作只有兩樣：更新兩份 HANDOFF＋`jarvis-pc` docs commit `49d1e5e`（未 push）。MC repo code 4 檔照樣 uncommitted。

| # | 事項 | 狀態 |
|---|---|---|
| 1 | 加 raw-reply log（一行：LlmClient 收 reply 後 log 原字）→ 雙樹 build → deploy → 重問同一題 → 定案「①model 照抄」定「②proseOrFacts 貼 facts」 | ⏸ 等 SK go |
| 2 | commit + push 09-11 DSML fix（雙樹 4 檔，已驗 / review SHIP） | ⏸ 等 SK go |
| 3 | DSML 側架構：A 放寬 detection layer／B recovery（解析後真執行）／C close-tag 配對 + buffer cap | ⏸ 未拍板 |
| 4 | 「整段回吐」修法（prompt 側唔好當 facts vs `proseOrFacts()` 側唔好貼 LLM-facing facts） | ⏸ 要等 1 定案 |

---

## 2026-09-11 早（續）—— 為咩 OpenClaw / Hermes **唔會有**呢個 DSML 問題（架構比較，SK 問）

> 結論：**佢哋都撞過**，只係架構上「唔確定就唔出街」；PackAI 係「照出，事後 regex 洗」→ 黑名單永遠追唔完（= SK 講嘅「not every times」根因）。

**實錘 ①：OpenClaw 撞過一模一樣嘅**
`openclaw/openclaw` PR **#128882**（merged 2026-08-29，closes #128858），標題幾乎一字一樣：
> fix(deepseek): **doubled-bar DSML tool calls are delivered as text and never executed**

—— 連「never executed」都中（今次 log：`toolCards emission=0`，靠 `autoEmission` 補卡 = model 想叫工具但用戶乜都冇發生）。

**實錘 ②：三邊架構對比**

| | **OpenClaw** | **Hermes** | **PackAI** |
|---|---|---|---|
| 偵測位置 | transport **串流層**（文字未到 UI） | adapter normalize 之後 | UI 前最後一步，**事後 regex** |
| 變體處理 | `["\|","｜","｜｜"]` **一次明列三種**（含雙豎線），recovery + filter 共用同一份 grammar | `_TOOL_CALL_LEAK_PATTERN`（`to=functions.x`） | `DSML_PIPE` = **淨一條** |
| 撞到之後 | **Recovery：解析返做真 call 並執行** + 文字過濾 | **當回合 `incomplete`** → 清空 `final_text` → 重試要模型用正式 `tool_calls` | **淨刪唔執行** → 行動蒸發 |
| 唔確定 | **fail-closed**（pair 唔上嘅 tag 唔准授權工具 + 256KB cap） | **fail-incomplete** | **fail-open**（照出，靠 regex 追） |

- **OpenClaw 檔案**：`packages/ai/src/transports/deepseek-dsml-grammar.ts`（`DEEPSEEK_DSML_MARKERS`）、`deepseek-text-filter.ts`（串流 filter，buffer split tag 前綴 `MAX_OPEN_TOKEN_LEN`）、`openai-completions-dsml.ts`（`RecoveredDeepSeekDsmlToolCall` = **執行返**）。
  PR body 關鍵句：「**Each invocation, parameter, and suppressed block must close with its opening marker**」← 正解 review 提嘅 over-match LOW；測試門檻 821 tests / 52 files 全過（char-by-char chunks、真 HTTP/SSE、malformed 拒絕）。
- **Hermes 檔案**：`hermes-agent/agent/codex_responses_adapter.py` → `_TOOL_CALL_LEAK_PATTERN` + `leaked_tool_call_text`；註釋寫明原因「**the parent sees a confident-looking summary with no audit trail and no tools actually ran**」。主 loop 每回合都帶 tools（`tools=None` 只見於內部 summary call）→ 結構上少撞。

**實錘 ③：PackAI 已經有救嘅機器，係兜底路線冇叫佢出嚟**
- 已有：`hasLeakedToolXml()` / `parseEmbeddedToolCalls()` / `parseLeakedToolXml()`。
- 但 `AskToolLoop.firstAsk` **L332**：`if (!offer) return nz(llm.askNoTools());` ← **直接 return，冇經 embedded-call recovery**；而 `capableLoop`（L373）同 `continueAfterAsk`（L487）**有**行。→ 「有能力救，但繞過咗」= skill 早記錄嘅 adjacent failure（「scrub 咗但冇執行 —— action silently vanishes」）。
- ⚠️ **同一個盲點有第二個 site**：`AskToolLoop.DSML_TOKEN`（L72-73）用嘅係**同一個單豎線 class** → **連偵測都認唔到**雙豎線變體。今日只修咗顯示層 `AskReplyScrub`，**偵測層未修**。

**建議（未拍板，等 SK）**
- **A（細、即刻）**：放寬 `AskToolLoop.DSML_TOKEN` → 補偵測層盲點。
- **B（中）**：抄 OpenClaw **recovery** —— 兜底路徑收到 DSML 文字 → `parseLeakedToolXml` → **真正執行**（解「想叫工具但冇嘢發生」）。
- **C（結構）**：抄「**close tag 要配對**」+ buffer cap，取代鈍刀 `dropResidualDsmlLines()` → 順手清 review 嗰個 over-match LOW。

**已寫入 skill**：`llm-tool-calling-reliability` → `references/tool-call-markup-leaks.md`（新 section「How the big runtimes avoid it (checked 2026-09-11)」，連 PR 編號／檔案路徑／audit-every-detector 提醒）。

---

## 2026-09-11 早（Discord session）—— Arch-3/3a 驗收 ＋ 新 DSML scrub bug

**1. Arch-3/3a（`1ba048f`）真機驗收 PASS**
- ⚠️ 修正：之前嘅「等 SK restart game 煙測」係錯——**根本冇 build 過 jar**（最後 build 09-10 01:53，commit 09-10 23:42）。
- 補做：backup → 雙樹 build（`--rerun-tasks`）→ symbol 驗證（`jeiForLlmFull`／`mergeJeiCatalogFull`／`capableForTools` 喺新 jar、唔喺舊 jar）→ deploy。
- 驗收：config 暫設 `askNativeTools="off"` 強制走兜底路徑 → 問「铁镐…用途/配方/取得方式」（focus = iron_pickaxe）→ log 實錘 **淨 1 輪 LLM、冇 tool_calls**，而 prompt 嘅 `jei` payload **開頭就係真 `[RECIPE_CARDS]` catalog**（5 條 index 0–4，同 `cards count=5` 對得上）→ **PASS**。config 已還原 `auto`。
- `1ba048f` **已 push**（`main`，ahead/behind 0/0）。

**2. 🐛 DSML 漏入 UI（SK 截圖）—— 已修，未 commit**
- Root cause（真 `AskReplyScrub` class + reflection 實測）：model 吐**雙豎線 U+FF5C** ＋ 容器字 **`calls`**（唔係 `tool_calls`）→ 四個 pattern 全 `find=false` → 清唔走。
- Fix（cursor-agent，雙樹）：`DSML_PIPE_RUN`（一條或多條豎線）／`(?:tool_)?calls?`／catch-all `</?[^<>]*DSML[^<>]*>`／新 `dropResidualDsmlLines()`。
- 驗收：雙樹 `cmp` byte-identical｜雙樹 compileJava BUILD SUCCESSFUL｜`AskReplyScrubCheck -ea` OK（K1–K4）｜真 reply 端到端 **556 → 51 字**｜python checks FAIL=3 = baseline。cursor review：**SHIP**。
- ⚠️ Pre-existing：`AskReplyScrubCheck` L193 喺 HEAD 一樣 fail（唔關今次）；`compileTestJava` 早已壞 → 呢個 check 一直冇跑（繞過法：單獨 `javac` + `java -ea`）。
- **未 commit**（4 檔 modified）。建議 message：`fix(ask): harden AskReplyScrub for doubled-pipe DSML variant`。

**3. 下一件**：SK restart MC → 問同一題（`auto`）→ 確認冇 regression + DSML 唔再漏 → 過就 commit scrub fix。


> ⚠️ **2026-09-11 狀態修正（jarvis-pc HANDOFF 那次 session 補）**：下面「Arch-3 / 3a 落地，code 未 commit」已過時 —— **3a 已經 commit `1ba048f`**（`fix(ask): keep [RECIPE_CARDS] catalog on no-tools fallback path`，09-10 23:42），**未 push（ahead 1）**，等 SK restart game 真機煙測 PASSED 先 push。commit 前嘅驗收（雙樹 compileJava BUILD SUCCESSFUL、scratch harness 8 case PASS、python checks 93 PASS/3 pre-existing FAIL、cursor review 兩輪）全部已完成，唔使重做。

## 2026-09-10 晚（Arch-3 / 3a 落地，code 未 commit）

- **做咗**：Arch-3 **3a**——`askNoTools()`（no-tools / HTTP-400 fallback / hop 後 full path）過去用 `jeiForLlm()` = `loop.jeiText()`（raw JEI summary，**冇 `[RECIPE_CARDS]`**，因為 `beginAskLoop` 嘅 shot0 fingerprint 蓋咗 AskService 完整 jei）→ UI 有卡但 model 見唔到 = catalog-leak 同類矛盾。改為 `jeiForLlmFull()` = `recipeCatalogForLlm()`（`recipeCardLines` 優先，同 slim 同源）⊕ `mergeJeiCatalogFull()`（= strip 走 dump 內重複 catalog block 再合併，**merge 唔係 replace**）。順手抽 `capableForTools()`（bridge 三處共用）、`stripRecipeCardsBlock` 改行過濾（空行／多 block／CRLF 都處理）。
- **改咗嘅檔（working tree，未 commit、未 push；5 改 + 1 新）**：雙樹 `logic/AskEngine.java`；雙樹 `src/test/java/com/skps9/packai/logic/AskToolLoopCheck.java`（新 `notoolsCatalogMerge()`）；`tests/check_ask_capable_slim.py`（assert 更新）；**新** `tests/check_ask_notools_catalog_merge.py`。
- **驗收實錘**：forge + neoforge `compileJava` = BUILD SUCCESSFUL；Hermes scratch harness 對**真 AskEngine bytecode** `-ea` 8 組 case PASS（`%TEMP%\hermes_arch3src\com\skps9\packai\logic\Arch3MergeCheck.java`，`javac -cp build/classpath/runClient_minecraftClasspath.txt`）；Python checks 93 PASS / 3 FAIL（3 個 fail 已 `git stash` baseline 證實同今次無關：`check_ask_tool_context` / `check_heavy_script_corpus` / `check_recipe_io_and_consume_use`）；cursor **review 兩輪**（首輪 finding 1/2 MED 已修，re-review「可以收貨」）。
- **未做／已知問題**：① **真機煙測**未做（等你 restart game）② ⚠️ **`forge compileTestJava` 喺 HEAD 已經壞**（2 個 pre-existing error：`LlmClient.toolSchemaDescription(String)` 已被 Arch-1 移除）→ repo 嘅 Java harness（`AskToolLoopCheck -ea`）而家**compilable 唔到**，所以今次新 cases 用 scratch harness 跑；想恢復要另開一輪修 test ③ 未 commit、未 push（跟 SK 「唔擅自 commit」規則）。
- **下次優先序**：① SK 拍板 commit（建議 `fix(ask): keep [RECIPE_CARDS] catalog on no-tools fallback path`）+ 真機煙測 ② **3b**（shot0 毒化：`beginAskLoop` raw summarize 蓋掉 AskService 完整 jei → 丟 `[TOOLTIP_HINT]`／facts cards）③ **3c**（purpose 預注 or `purpose_lookup` 對齊 `purposeBlock`；`pushExtras` capable 無效）④ 修 `compileTestJava` pre-existing（恢復 Java harness）⑤ backlog 不變（skill-system 暫緩、ComfyUI 等拍板）。
- **Audit 原文**（設計來源）：`%TEMP%\cursor_arch3_audit.md_report.md`（282 行，2026-09-07）+ review 報告 `%TEMP%\cursor_arch3_3a_review_report.md` / `_rereview_report.md`。

## 今日做咗咩（MC repo）
主線 = **武刃 tooltip root cause 調查 + 0.2.1 bug-fix release**（順帶清咗 backlog #1-#4）。

Commit chain（全部已 push `main`，`dec1471..96ad78a`）：
- `900a7e4` **fix(ask): keep claim lines past trimPurposeTooltip head cap**（forge 1.19.2）——武刃 root cause
- `96ad78a` **fix(ask): neoforge sync + bump 0.2.1**（root + forge + neoforge gradle.properties）

### 武刃 root cause（實錘鏈，推翻 09-08 cursor report）
- 09-08 結論「NBT-gated lore 唔入 capture」= **INFERENCE 錯**（report 自己標未 A/B）
- 實錘：KubeJS `addAdvanced('maodlc:wuren', ...)` handler **唔睇 NBT**（javap wrapper + script 都冇 gate）；`TooltipCapture` 一直 capture 到 12 行完整 tooltip（綠色行喺第 11 位）——**係 `AskService.trimPurposeTooltip` L495 `if (kept>=8 && !claim) break;` 剪走**：武刃頭 8 行係 stats（武刃/Wuren/耐久/可注入法術/主手/攻傷/攻速/無法破壞），第 8 行完 break，未 scan 到第 11 位綠色行
- Code comment 話「keep obtain/claim lines even past 8-line cap」但 implementation 係 break = **意圖同實作矛盾**
- Fix：`break` → `continue`（cap 後 skip 非 claim 行但繼續 scan 到尾）
- Smoke 實錘：`claimHints src=232/out=0` → `src=278/out=1` ✅ AI 答到「炮景/禮包」取得鏈
- 診斷用 debug log（PackAiTooltipHandler「PAI ItemTooltipEvent fired」+ TooltipCapture「PAI TooltipCapture dump」）已降 DEBUG level（保留日後 tooltip-chain 診斷用，唔 spam）

### Release 0.2.1（SK 明示 push + CF upload）
- CF files：**8845552**（forge）/ **8845554**（neoforge）——v1 API verify fileLength match（992395 / 1001183），releaseType=1
- `dist/_cf_upload/upload_027.py`（changelog = tooltip obtain hints fix）
- Instance `AI_test_NFWC_DIM` mods 已 deploy `packai-0.2.1+mc1.19.2-forge.jar`（0.2.0 + .bak 清走）——下次 restart game 生效

### Backlog 狀態
- **#1-3 ✅**（push / jarvis alerts fix / sidecar monitor——jarvis 線另見 jarvis-pc HANDOFF）
- **#4 skill-system plan review ✅**（三階段完成：P1 武刃 case 唔成立已獨立 fix、P3 SK 拍板 v1 只 serve capable path、P2/P4/P5 已寫）；**SK 揀 B：skill-system impl 暫緩**
- **#5 Arch-3 round（3a askNoTools catalog merge + 3b shot0）**：設計已收斂（見 `%TEMP%\cursor_arch3_audit.md_report.md`，282 行，09-07 cursor audit）；SK 揀 B：**聽日先開工**
- **#6 ComfyUI 新線**：pending（等 Obsidian 拍板）

## Next（下次 session 優先序）
1. **#5 Arch-3 round 開工**（3a：`askNoTools` 組裝 = `recipeCardLines`(slim catalog) ⊕ merge full JEI dump，唔 replace；3b：shot0 毒化 fix 方案 A——seed 空 content 保留 fingerprint）。audit report 喺 `%TEMP%\cursor_arch3_audit.md_report.md`（或 session 20260907_144825 recovery）；落地順序見 report Q5「建議落地順序」4 步。雙樹 sync。改 model 輸入要獨立回歸 smoke。
2. SK 開新 session 前：唔使等——handoff 已寫
3. **#6 ComfyUI**（SK 拍板 Obsidian 後）
4. backlog：skill-system（暫緩）、Grok Bot 選項、Douyin cron（每月 1/15）

## 記憶更新
- （已有）debug log 實錘偏好再次應驗——今次全靠 TooltipCapture dump log 先知 capture 有行、斷點喺 trim
- 教訓：cursor report 自標 INFERENCE 嘅 root cause 唔好當 FACT；武刃類「tooltip 有 claim 但 AI 答唔到」→ 先查 trim 層，唔好衝去 NBT/事件鏈
