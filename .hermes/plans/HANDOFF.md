## 2026-09-21 遺留 bullets（原 section 已歸檔，未編入任何 section；⑫ 見 STATE 未解）

- **取得途徑缺口實例（09-21 07:2x，SK 真機問 `witherstormmod:withered_nether_star`）**：答案只列 2 條合成，並寫「掉落表／釣魚／交易／腳本索引都沒有取得路徑」→ **誤導**。真因：① mod **冇 wither_storm loot table**（jar 內 entities 只有 sickened_*＋withered_symbiont）⇒ 掉落係 **Java code 實作**，JSON 索引結構性睇唔到；② **但 JSON 有成就檔**：`advancements/main/wither_storm_defeated.json`（criteria=`inventory_changed` 取得此物品，zh 標題「此波平，彼浪起。」／描述「一劳永逸地摧毁凋灵风暴！」）⇒ 資料本身已可推出「打王掉落」。packai 掃 advancement 只做 `consume_item`（用途側），**取得側未接入**。建議修：A 取得側接入 `inventory_changed` 成就 ＋ B 措辭誠實化（JSON 冇 ≠ 遊戲冇，要講「可能由 mod 程式碼實作」）。（等 SK 決定開唔開 plan）
- **記帳調查（09-21 06:4x，Hermes 親做）**：`tokens delta=0` 根因＝**driver 讀 local 日期 vs 帳本用 UTC key**（非產品 bug）；證據：6 行 `Pack AI usage billed=` 合計 320,813／帳本 mtime 06:20:33；driver 已修。真 instance 端到端證實＝SK 玩一次後睇有冇新 `2026-09-20` key。
- **沙盒真機一輪（09-21 06:17，FTB，同源 flagged jar `autotest-dev-0.2.3.jar`）**：12 cases（10 隨機 craft＋`iron_ore` 控制＋`bedrock` 負控）→ **ok=6**、NO_SAMPLE 6（全部係非 JEI 輸出物品，符合預期）。
  證據：6 條真 trace（`model.reply*` 4–6 輪、`cardsOut` 2–8、body 1.1–2.4k 字真答案）；內部欄位外洩掃描 **CLEAN**；`latest.log` **packai 零例外**（P0 嵌套 icon 崩潰唔復現、零 `Query failed`）；焦點 before/after 都係 Discord＝**零搶**；⚠️ token 記帳仍 **delta=0**（舊未解 ①）。
  證據目錄：`%TEMP%\autotest_results_20260921-061710`（trace＋status＋latest.log）。
- jarvis-pc 線（09-21 06:0x）：MC repo `c1fe2fd` 已 push（remote 核實一致）；packai 最新 build `30d70e2f707d` 已部署真 instance（backup `06b5b129a114`）；沙盒真機一輪（FTB，同源 flagged jar `autotest-dev-0.2.3.jar`，12 cases 隨機抽＋bedrock 負控）因 SK 用機自動延後到 idle＋DS 離峰才跑。
- 乙線實測結論（09-21）：1.19.2 專用伺服器**唔同步** worldgen feature registry（regPlaced=-1 + IllegalStateException；單人 545）⇒ 乙只可單人／LAN，睇 docs/plans/2026-09-21-registry-multiplayer-result.md
<!-- STATE:BEGIN -->
## STATE（五元素；每次改寫，唔 append；≤2,500 tokens）
- **目標**：packai（`super_minecraft_AI_player`，MC 1.19.2 Forge 客端）＝**通用**整合包顧問：由「問單件物品」升級到包級（倍化／科技樹／能源／流體／材料／裝備／維度／自動化）＋**能力反查**（「包入面邊樣嘢可以做到 X」）。細節唔喺呢份檔。
- **現狀（2026-10-22 04:1x 改寫；Hermes 親核）**
  - **主線＝「取得途徑（流體／資源）」線**：plan `docs/plans/2026-10-21-obtain-fluid-and-recipe-sources-v3.md`（R3 兩邊 8:2 過）。已完 T0／T0.5／T1／T2a／**T2b（`77060f1`）**／T2c／T2e／**T2f（`525b5f6`）**／**T3（`a429677`）**／**T4（`c3003c9`）**／**T5（`ad3cce5`）**；未完 **T6（真機矩陣跑緊）**。接手先讀 `docs/plans/2026-10-21-obtain-fluids-RESUME.md`（命令／坑／白名單／驗收閘）。
  - **已完＋已 push 嘅舊線**：clarify v7（`1a154f4`）＋上限確定性標記（`3eb1cae`）／窄版焦點 `Y` 鍵（`166ed11`）＋面板 4 掣（`d37c7a2`）／離線能力索引 A（`3bb9b89` 等）／promo 封面＋分發包。細節＝`plans/archive/HANDOFF-2026-10.md`。
  - **測試基線**：只 Forge 1.19.2；compile ✅／`-I tmp-check.gradle` harness 全 PASS／`tests/check_*.py` **139 綠、4 紅全部係環境**（Windows ATM8 路徑、冇 `javap`、冇 build jar、`check_ask_display_leak` 需真機 log）。
  - **環境**：沙盒 `packai_sandbox`／`_atm8`（`_startech` jar 舊）＋真 instance `AI_test_NFWC_DIM`；部署唯一途徑 `mc_mod_deploy_jar.py`；`neoforge/1.21.1` PAUSED。
  - **Git**：repo public；`docs/plans/`＋`.hermes/` 只留本地（歷史已 `filter-repo` 清）；push 要 `GIT_SSH_COMMAND='ssh -i ~/.ssh/id_ed25519_packai -o IdentitiesOnly=yes'`（default key 會被 GitHub 拒）。
- **唔准郁**：卡落位（`RecipeEmbed`／`RecipeCard`）／`AskEngine` capable 清空語意／`neoforge` 樹／hot-copy jar／真 instance 自動部署／`AGENTS.md`（要 SK go）／自創乘數／keybind 已驗嘅評分規則。
- **未解（等 SK／待開工）**：① A 能力索引等 SK 收貨（精度／分級退化要另做校準）② P0 catalyst 覆蓋率要遊戲內 instrumentation（未批）③ 冷啟動掃描時間未量 ④ promo 要 SK 自己錄 gameplay ⑤ keybind 天花板：簡體／其他語言 label 對唔到繁中問句 ⑥ 題目庫冇 harness ⑦ L0／L1／L2／L3 選型未揀 ⑧ **S-A2 真機數字對照未驗** ⑨ advancement 取得側（`inventory_changed`）未接入（等開 plan）⑩ `ENTITY_FOCUS_RANGE` 預設 16 格待 SK 確認。
- **下一步（優先序）**：① 取得途徑線 T2f → T3 → T4 → T5 → T6（T6＝三 pack 真機矩陣，cold／warm 協定見 plan §T6）② S-A2 數字對照（T6 後）③ offer gate 殘留 31.5% → <30%（要先開 plan＋review）④ 其餘照「未解」。
- **歸檔索引**：`plans/archive/HANDOFF-2026-10.md`（09-19～10-10 舊 section，10-22 歸檔）／`plans/archive/HANDOFF-2026-09.md`（更舊）。

<!-- STATE:END -->

## 2026-10-22 04:0x–04:1x（流體鏈 T2b：FluidEdge＋接線，Hermes 親驗；commit `77060f1`）

- **範圍**：plan v3 §T2 剩餘部＋「接線規格」。cursor 改 10 檔（`ObtainGraph`／`ObtainPaths`／`ObtainPathText`／
  `ObtainPathService`／`RouteStoreCodec`／3 個 lang／新 `fluid_sources.json`／`ObtainFluidChainCheck`）。
- **實作**：`FluidEdge(recipeId,typeId,fluidId,amountMb,itemGroups,fluidInputs,actionLabel)` ＋ `fluidProducers` map（每個 fluid 最多 4 條，
  按 `edgePriority` 再 recipeId 排）；`pickFluidProducer(fluidId, consumer)` 排除「item 輸入含 consumer」嘅 producer（環路保護）；
  `ObtainPaths.Step` 帶 `fluidSources`（null 當空）；`renderStep` 加「← 用 {machine}：{inputs}」子行（lang key）；`StepDto` 存／讀
  `fluidSources`（舊檔缺欄＝空 list）；trace 加 `obtain.fluid_lines`／`obtain.fluid_unresolved`。
- **驗收（我親跑）**：`compileJava compileTestJava` **BUILD SUCCESSFUL**；`-I tmp-check.gradle` 四個 check
  （`runObtainFluidChainCheck`／`runObtainPathsCheck`／`runObtainInsertCheck`／`runObtainGraphCheck`）**全 PASS**；
  `tests/check_*.py` **139 PASS／4 紅**，4 紅全部係環境（`check_bytecode_numbers` 要 Windows ATM8 instance、
  `check_capability_index` 冇 `javap`、`check_jar_contains_fix` 冇 build jar、`check_ask_display_leak` 需真機 log）＝**零新增紅**。
- **測試設計修正（3 輪）**：① `ObtainFluidChainCheck` 缺 `import Item`；② `caseBadRecipe` 未初始化 local var；
  ③ 環路 case 原本 consumer 用 `COCOA_BEANS`（兩個 producer 都含 cocoa）＋排序本身已揀到安全嗰條 ⇒ **測唔到規則**。
  改成新增 stub `create:mixing/aaaa_melt`（輸入含 `COOKIE`、id 排最先）＋consumer=`COOKIE`，並加「用 `STICK` 做 consumer 時
  必須揀 `aaaa_melt`」嘅對照斷言（證明係排除規則、唔係排序）＋冇 producer 嘅 fluid 回 null。
- **已知限制**：`pickFluidProducer` 只睇 `itemGroups`（唔睇 fluid 輸入鏈），多層流體鏈未驗；T6 真機矩陣未跑。
### T2f — Requirement（能源／化學／氣體）＋普查 gate（`525b5f6`）
- **實作**：`ObtainGraph.Requirement(kind,id,amount,unit)`；`Edge` 帶 `requirements`；`RecipeIo` 加反射 accessor 表（energy／chemical／gas／slurry／pigment／temperature／other，逐個 fail-soft）；`ObtainPathText` 出「Machine needs N FE」／「Also needs …」子行（3 個語言檔 lang key）；`PackAiConfig.obtainCensus`（default off）＋ getter／setter 喺 config 未載入時做 no-op。
- **關鍵行為改動**：`RecipeIo.UNREPRESENTABLE_ACCESSORS` **移走 `getEnergy`** —— 能源係機器成本、唔係材料，改前會令所有帶 energy 嘅配方被判 `inputsPartial` ⇒ path 假 incomplete。
- **驗收（我親跑）**：compile **BUILD SUCCESSFUL**；4 個 Java check（FluidChain／Paths／Insert／Graph）**全 PASS**；`tests/check_*.py` 同 baseline 一樣（5 個環境紅／其餘綠，`check_advisory_trigger` 單跑會 PASS）。
- **修過嘅測試坑（5 個）**：stub 繼承 `final` class／config 未載入時 getter 拋 `IllegalStateException`／setter 拋 NPE／兩個 stub 共用 `packai:stub` id 被 dedupe／census「開咗會行」嘅斷言喺 harness 內根本驗唔到（config 未載入 ⇒ 保持 off，改為只驗 off 半邊）。
- **未做**：census「開」嘅行為只可以喺真機（config loaded）驗，留返 T6。
### T3 — 誠實 complete＋自然源（`a429677`）
- **自然源**：`ObtainGraph.FluidSources` 由 `config/packai/fluid_sources.json`（可覆寫）讀，fallback `assets/packai/fluid_sources.json`；只讀一次、永不 throw。
- **條件翻轉（LD4）**：`finish()` 計 `fluidEdges`／`fluidUnresolvedEdges`，比率 log 一次；**只有 ratio ≤ 0.10 才啟用誠實 incomplete**（`COMPLETE_FLIP_MAX_UNRESOLVED_RATIO`），>10% log `complete_flip=skipped` 保持舊行為。trace 加 `obtain.fluid_flip`。
- **文字**：自然源（water／lava／milk）**唔出**「來源未收錄」噪音行；非自然源且無 producer 才出。
- **驗收**：compile PASS／4 個 Java check PASS（新增 4 個 case：10% 啟用、100% 停用、全 resolved 保持 complete、自然源靜音）／新靜態閘 `tests/check_obtain_complete_rules.py` PASS／`tests/check_*.py` 同 baseline（5 個環境紅）。
- **修過**：lambda 捕獲 non-final 變數（`fluidFlip`）⇒ 用 `fluidFlipFinal`。
- **未做**：真機比率（T6 要睇 log 實際 ratio 決定行為）／T2f 漏咗嘅 trace 計數（`obtain.req_energy`／`req_extra`／`req_incomplete`，當時 whitelist 冇 ObtainPathService，補喺 T5）。
### T4 — 冷啟 cache（`c3003c9`）
- `ObtainGraphCache`：`config/packai/obtain_graph_cache.json`（schemaVersion／fingerprint／recipeCount／builtAt＋graph 快照）；
  fingerprint＝modsStamp＋MC／Forge 版本＋schema＋builder＋datapack 目錄 hash；recipeCount 唔同⇒stale。
- **命中即用**：`ready=true;building=false`、`obtain.graph.ready` 只出一次；之後 `onRecipesUpdated` 先查 cache，
  重建期間繼續服務舊圖。**8 MB 上限 ⇒ 唔寫（log skip），唔截斷**；讀壞檔／IO 錯 fail-soft 走重建。
- `PackAiConfig.obtainGraphCache` default **on**（config 未載入時 getter 回 default、setter no-op）。
- **驗收**：compile PASS／4 個 Java check PASS（round-trip、fingerprint 變、截斷檔、recipeCount stale、size cap）／
  新閘 `tests/check_obtain_graph_cache.py` PASS／`tests/check_*.py` 同 baseline。
- **未做**：真機 warm <2 s 同 cache 命中率要 T6 量。
### T5 — 閘收口＋requirement trace（`ad3cce5`）
- 補 T2f 漏嘅 trace：`Result` 加 `reqEnergy`／`reqExtra`／`reqIncomplete`（保留短建構子），trace 出 `obtain.req_energy`／`req_extra`／`req_incomplete`；`ObtainPaths` 加對應 helper。
- 新閘：`tests/check_obtain_fluid_text.py`（render 只用 lang key、零 CJK literal、三語 key set 相同）、`tests/check_obtain_fluid_chain.py`（FluidEdge／producer 規則／Step 接線／codec 舊檔相容）。
- **負控實測**：把 lang key 改成不存在嘅名 ⇒ gate **RC=1** 並印三語各自缺 key；改返乾淨。
- 驗收：compile PASS／4 個 Java check PASS／4 個 obtain 閘全 OK／`tests/check_*.py` 4 個環境紅（零新增）。

### T6 — 真機矩陣（跑緊 05:0x）
- jar `packai-0.2.3.jar`（04:49 build）已部署 3 個沙盒（sha `4487939cb4df9a90`），舊 jar 留 `deploy_backup_*.jar`。
- 6 輪（cold／warm × minimalcreate／minehattanproject／ftb），每輪 14 個 decoy + 真 case；cold 前刪 cache 檔。
- 驗收點：`obtain.fluid_lines` 0→≥1、route JSON 有 `fluidSources`、`thermal:apatite` 有結晶機路徑、`create:bar_of_chocolate` 有朱古力路徑、warm 輪 `obtain.cache_load=hit` 且 ready <2 s、bedrock 負控冇路徑。
- **第一輪結果（6 輪全部 runner `ok=true`）**：只有 **minimalcreate PASS**（朱古力 1 條流體來源／0 未解；奶茶 2 條流體＋2 個額外需求；clay OK；bedrock 負控冇輸出；暖輪 `obtain.cache_load=hit edges=3033`、`ready 0 ms`，cache 檔 1.27 MB）。**minehattanproject／ftb `obtain.paths=0`**：trace 有、但問嗰刻圖未 ready。
- **根因（實測＋算術）**：`ObtainGraphRuntime.TICK_BUDGET_NS=2 ms`，20 tps ⇒ 每秒只有 ~40 ms 建圖 CPU；minehattan 全速建圖 28.8 s ⇒ **遊戲內要 ~12 分鐘**才 ready（18 個 decoy ≈40 s 遠遠唔夠）。細包 7.2 s 全速 ⇒ 遊戲內 ~3 分鐘，但 4,310 條實際上喺 decoy 期内建完所以 PASS。
- **產品含意**：大包第一次開遊戲要有 cache 才有用（第二次起 instant）；冇 cache 嘅首幾分鐘 `obtain.paths=0`。呢個係 T6 捉到嘅真問題，唔係測試問題 → 要決定（a）加大 tick budget（b）首次建圖時背景優先（c）出廠附 cache。
- **跟進**：跑一輪 420 decoy（≈14 分鐘）嘅 minehattan 冷啟去產生 cache，再 warm 輪驗（`t6b_*`）。
- **測試基建坑（今日踩過）**：① `mods/` 唔可以放 jar 備份（preflight 見到 2 支同 class 會拒絕開機）→ 搬 `jar_backups/`；② **一定要用 `-PpackaiAutotest` build 嘅 `autotest-dev-*.jar`**（普通 jar 冇 `packai-autotest.flag`，harness 完全唔啟動，遊戲會企喺主選單等到 timeout）；③ lane request 要 `force_clean: true`，否則殘留 case 會令下一輪 preflight fail。


### T6b／T6c — 大包冷啟與「路徑睇唔到」（05:3x–06:0x）
- **動態建圖預算（SK 揀 A，已 commit `e5411e1`）**：`TICK_BUDGET_IDLE_NS=8ms`／`TICK_BUDGET_ACTIVE_NS=2ms`，`ASK_QUIET_NS=10s`，`noteAskActivity()` 由 `ObtainPathService` 呼叫；首次建圖用 idle 預算；config `obtainBuildBudgetIdleMs`(2–20, 預設 8)／`obtainBuildBudgetActiveMs`(2)；靜態閘 `check_obtain_build_budget.py`。
- **harness 上限**：`AutoTestHarness.MAX_CASES` 由 60 提到 **300**（大包暖機要 200 個 decoy；60 個只有 ~2 分鐘）。
- **minehattan 冷啟實測（8 ms）**：`obtain.graph.progress fed=16384 elapsedMs=303404` ⇒ 全量 ≈**5.1 分鐘**；`obtain.graph.ready edges=13007`（同 baseline 一致）；**`obtain.cache_write=ok bytes=6305021`**（6.3 MB，< 8 MB 上限）。2 ms 預算推算 ≈21 分鐘 ⇒ 大包第一次開遊戲一定要 cache。
- **真 case 結果（冷啟後）**：`thermal:apatite` 1 path／2 行／1 energy＋1 incomplete；`electrodynamics:crystaliron` 1 path／1 行／1 流體未解；`minecraft:clay_ball` 1 path／3 行／1 流體未解；`minecraft:bedrock` 負控 NO_SAMPLE（正確）。**三條都 `obtain.ready=True`**。
- **🔴 新 bug（T6c 修緊）**：三條 case 全部 `obtain.heading_found=false`、`insert_changed=false`、`path_skipped=''` ⇒ 路徑算到但**玩家完全睇唔到**。根因 `AskReplyScrub.insertAtHowToGetTop()` 冇 heading 就 `return body`（靜默）。修法：新 `insertAtHowToGetTopOrAppend(body, lines, missLine, heading)`，冇 heading 就 append（插在 sources footer 前、去重），trace `obtain.path_fallback=append`。
- **第一性原理 code review（SK 要求，已交報告）**：① 索引太肥——`itemsFromIngredient` 每條配方都展開 tag；建議只存 tag id／item，查詢時才展開；② 建圖應搬去**單一背景線程**（JEI Just Enough Threads／EMI 先例；JET 明講 modded recipe lazy cache 唔保證 thread-safe ⇒ 唔可以並行）；③ cache 方向正確，可加 gzip＋「索引中」提示；④ 唔建議改用 JEI `IRecipeLookup`（只回 wrapper、冇公開讀材料 API）。**次序：先索引瘦身，量到仍 >20 秒才搬背景線程**。

### T6c 驗收（06:1x，真機）
- **修好並 commit `49cfdc2`**：minehattan 三條 case 由 `heading_found=false/insert_changed=false` 變成 `path_fallback=append`、`insert_changed=true`、`path_lines=3`，body 真見到：
```
How to obtain
craft: Block of Apatite -> Apatite
(deeper steps unconfirmed)
or: smelt: Apatite Ore -> Apatite
[Sources] ...
```
（插在 `[Sources]` footer 之前、footer 保持最後、誠實標示 `deeper steps unconfirmed`／`fluid source not catalogued`）。
- **暖啟實錘（大包）**：`obtain.cache_load=hit edges=12678`、`obtain.graph.ready finish_ms=0` ⇒ **5.2 分鐘 → 0 毫秒**。
- **⚠ 待查：edge 數唔穩定**。兩次同一 pack 冷啟分別 `edges=13007 items=6067`（05:51，cache 6,305,021 bytes）同 `edges=12678 items=6037`（06:10，cache 6,197,032 bytes），recipes 都係 17,140。懷疑係配方迭代順序（HashMap）影響 `MAX_EDGES_PER_ITEM=32` 嘅取捨；已排兩個相同冷啟（`t6_determinism.py`）做 A/B 實測，未結論前唔好當 baseline 信邊個數。
- **測試工具改動**：`AutoTestHarness.MAX_CASES` 60 → 300（已入 `49cfdc2`）。
- **未 commit 嘅 code**：無（T6b `e5411e1`、T6c `49cfdc2` 都已 commit；T2b–T5 五個 commit 仍未 push）。

### T6d／T6e／T6f — in-game bench ＋ tags gate（06:4x–06:5x，commit `4cf1fa8`）
- **新工具：in-game `graph_bench`**（`{kind:"graph_bench", runs:N}`）——同一個 session 內**同步全速**建圖 N 次（唔受 2/8ms tick 預算），每次 log `obtain.bench run=.. edges=.. items=.. fluids=.. elapsedMs=.. tags=.. sentinel=.. sha=..`＋bail 計數，sha 唔同即 case FAIL。
- **實測（minehattan、jar 06:51）**：3 次全部 `edges=13008 items=6068 fluids=43`、**sha 完全相同（`52966c401b7ff2c1`）**、每次 **6.8 秒**全速；`tags=true sentinel=1`；`tags_gate applied=true reason=tags_event`（無 bypass）。
- **結論**：同一 session 內建圖係 deterministic ⇒ 之前 `12678` 異類唔係建圖本身唔穩定，而係**輸入狀態唔同**（最可能 tag 未到齊）。
- **真 bug 修正（tags gate）**：`RecipeIo` 用 `Ingredient.getItems()` 解 tag，而 client 收 recipes／tags 係兩個 packet；舊 code 冇處理 `TagsUpdatedEvent` ⇒ 可以建出殘缺圖並**寫入 cache**。現在：tag 到齊才建、途中／之後 tag 更新即重建、60 秒無 tag 就 bypass 並 log；**cache 只喺 tags 到齊時才寫**，fingerprint 加 tag 印記（sorted tag id＋成員數）。
- **效能洞見**：builder 全速只需 **6.8 秒**，但 in-game 要 5.1 分鐘 ⇒ 慢係**世界載入期間主線程爭用**，唔係 tick 預算；再加大預算幫助有限，正路係背景線程／延後建圖（見下面 code review 建議 ②）。
- **未解**：`13008/6068` vs 舊 baseline `13007/6067` 差 1（跨 jar 版本），未能重現；同一 session 內穩定。
- packai commits：**已 push**（`cdcc553`，2026-10-22 12:5x；`origin/main == local`）。之前嗰批 local commit 全部上咗。
- **未解**：`13008/6068` vs 舊 baseline `13007/6067` 差 1（跨 jar 版本），未能重現；同一 session 內穩定。
- **ftb 大包 bench（07:28，世界 `New World`——砂盒冇 `packaiworld`，第一次跑即失敗）**：3 次全部 `recipes=21713 edges=17286 items=9365 fluids=44`、**sha 相同 `d4d9003135975204`**、每次 **10.0–10.4 秒**；`tags=true sentinel=1`、`tags_gate applied=true reason=tags_event`。⇒ 最大 pack 都 deterministic，全速 10 秒。

### 背景 worker（SK 揀 B，11:2x–11:4x）
- **Plan**：`docs/plans/2026-10-22-obtain-graph-worker-thread.md`（6 輪 review：3:7 → 7:3×5；最後一輪剩「gate 覆蓋率」類爭議，SK 拍板 actionLabel 用英文 fallback，唔入指紋）。
- **實作（3 commits）**：`274d08f` BuildContext（client 狀態快照）、`61d6fc8` worker ＋ runtime 接線（含 submit-retry、timeout watchdog、0-edge publish guard、`path=worker|tick|cache`、`sha=`、`worker_ms=`）。
- **真機實測（minehattan，worker 路徑）**：`worker_ms=4174`／`4303`（原本 tick 路徑 **5.1 分鐘**）；`edges=13003`；cache 6.3 MB／191 ms；兩次冷啟 `ready=True`、真問題有路線（apatite 1 path/2 行、clay 1 path/3 行）。
- **失敗機制**：worker 忙時 submit 被拒 → 5 秒 retry 窗口 → 過期一次性 `workerFallbackForced` 降回 tick；timeout 180 s（可 `-Dpackai.workerTimeoutMs` 覆寫）；測試 props `packai.workerFailOnce`／`workerHangOnce`。
- **⚠ 未解（另開單）**：**跨 session 內容唔一致**——兩次冷啟 `edges` 相同（13003）但 canonical sha 唔同（`21e0b217e3002368` vs `a1e265060f38f959`），之前亦見過 13007／13008／12678 飄。同一 session 內 bench 3 次完全一致 ⇒ 疑 ingredient／tag 解析受載入次序影響。
- **未做**：hang／timeout 真機驗證（要 inject JVM `-D`；runner 經 systemd-run，暫未接）、ftb worker 冷啟驗證、`check_obtain_graph_worker.py` 只覆蓋 step 6 一部分。

### 量度驅動優化（12:2x–12:4x，commits `bbffe3d` `cdcc553`）
- **worker 預設關閉**（`d708de3`）：同 session 同 recipe 數（21,723），worker 圖 `edges=17277 sha=58b38785…` vs 主線程 bench `edges=17277 sha=8e9f05cf…` ⇒ 背景讀 modded recipe 材料結果唔一致。
- **T7（唔展開 tag）反方 4:6 唔過**：① `inputsPartial` 建圖期判斷唔到（空 tag 要展開才知）② `CompoundIngredient` 等冇 `values`，反射「成功」回空陣列 ③ `finish()`／cache／fluid 層仍然會叫 `inputGroups()`。
- **先量度（關鍵）**：minehattan 6.9 s 之中 `getItems()`（tag 展開）只 **0.76 ms**，**反射 6.67 s**。⇒ T7 前提錯。
- **真正瓶頸＝反射 miss 冇 cache**：每次探唔到 method 都拋新 `NoSuchMethodException`（`fillInStackTrace`）。已加負快取 + per-class missing set（`cdcc553`）。
- **實測**：全速 bench **6917 ms → 200 ms**；**tick 路徑冷啟 5.1 分鐘 → 29.5 秒**（`path=tick`，edges=13007 不變）；cache gzip **6.3 MB → 352 KB**（144 ms 寫完）。
- **ftb 冷啟（tick 路徑，worker 關）**：`recipes=21727 edges=17321 items=9369 fluids=46`、**`elapsedMs=61534`（61.5 秒）**、cache `989651 bytes`（990 KB）寫入 347 ms —— 之前因為 8 MB 上限**完全冇 cache**。
- **跨 session 配方數差異＝已查清（mod load race，非我哋 bug）**：ftb 取樣 21,715／21,717／21,721／21,723／21,727，163 個 namespace 只有 `farmersdelight`(-3) 同 `sliceanddice`(-3) 會變，其餘每次一樣。
- **修正（`0b12b30`）**：指紋唔再含 item-tag membership（tag 人口跨 launch 會變）；`BUILDER_VERSION=3`；配方數容差 ±32 ⇒ `HIT_TOLERANT`。ftb 連續 3 次開機全部命中（`delta=2`／`delta=6`），唔再重 build 61 秒。
- **量度陷阱**：每次循環都 rebuild jar ⇒ mods jar size 變 ⇒ `modsStamp` 變 ⇒ cache 必 miss。測 cache 一定要同一支 jar 跑幾次。
- **未做**：hang／timeout 真機驗證（worker 已預設關，優先度低）、crafting tree UI 升級（等 SK 揀 mockup A／B）。
### 2026-10-23 01:0x — 路線樹 Cards 檢視（SK 睇過截圖 approve）
- **obtainPaths 預設改 true**（`PackAiConfig`），另加**一次性遷移** `obtainDefaultMigrated`，令舊 config 都會開啟；遷移嘅 event bus 註冊由 static init 搬去 `registerConfigListener()`（`PackAiMod` 呼叫），否則 headless check 會 `ExceptionInInitializerError`。
- **新 CARDS 檢視**：`ViewMode {TREE,CARDS,TABLE,CHECKLIST}`、切換掣自己一行、卡格（物品圖示／名／數量／機器圖示＋名／需求行）、`drawFitted` 統一量寬＋省略號；`MachineIcons`（type→機器 item，含前綴 fallback）；卡片由**目標物品**排起。
- **config**：新 client 值 `routeTreeViewMode`（只在用家點擊時寫入）。
- **Code review（4:6）捉到並修好**：① 捲動死（16px vs snap ≥82px）→ 改成索引式 `cardIndex`＋純函式 `CardListLayout`（新測試 `ObtainCardsLayoutCheck`）② 渲染正序／度量反序唔一致 ③ 數量寫死 `×1` → 用 `edge.outputCount()` ④ 度量每 frame 重算但 cache 只是 init 一次 → 統一 `rebuildListMetrics` 快取＋graph ready 後刷新一次。
- **閘門**：Python 152（剩 3 個一直存在嘅紅）；Java 67 OK／4 個失敗全部係環境性（Windows 路徑＋jar fixture）—— **唔可以當新紅**。`check_obtain_graph_worker.py` 期望值已由 true 改 false（worker 故意關）。
- **commits**：`1c3bb9a`（Cards 檢視＋obtainPaths 預設）、`2a5b67f`（review 修正）、`bdddec8`（config listener 修正）。**未 push**。
- **未做**：自動重疊檢查器（dump＋C1–C6 規則；7 輪 plan review 未過 8:2，已另排）、push、Cards 捲動嘅真機滾輪驗證（單元測試已覆蓋邏輯）。

## 2026-10-21 21:1x-21:5x（取得途徑 5 個真 bug：plan 3 輪過 review 8:2；T0 探針待批）

- **plan 已通過**：`docs/plans/2026-10-21-obtain-fluid-and-recipe-sources-v3.md`
  （v1 4:6 → v2 7:3 → **v3 兩邊都 8:2**；報告 `docs/plans/reviews/2026-10-21-obtain-fluid-R{1,2,3}-{opposing,supporting}.md`）。
- **核心設計（review 後大改）**：
  ① 唔再繞去 JEI 補洞 —— 真兇係**我們自己嘅產出抽取器**（Thermal 配方其實在 vanilla RecipeManager；
  `getResultItem()` 回 EMPTY，真產出在 `getOutputItems()`／`getOutputFluids()`；Create 形只覆蓋 `getRollableResults`）。
  修法＝新 `logic/RecipeIo` 反射適配層（輸出／輸入／流體全部元素級）。
  ② 流體產出＝**獨立 `FluidEdge`**（唔改 `Edge.recipeId` 契約、唔入 item 分組、唔經 `dedupeByRecipeId`），
  建邊分支放 `ObtainGraph.java:237-239` **之前**；環路規則＝「producer 嘅 item 輸入含 consumer item ⇒ 排除」。
  ③ 文字層：`ObtainPathText` 加流體、`JeiLookup.formatRecipe`／`jeiMachineLine`（唔係 `shortIoLine`）加流體；
  流體顯示名由 `client/gui/RouteMaterials.displayFluidName` **下沉**成 `logic/FluidNames`。
  ④ `complete` 翻轉改成 plan-time 決策（先量 runtime `Edge.fluids` 分母，>10% 就整段唔做）。
  ⑤ 冷啟：`logic/ObtainGraphCache` 持久化（fingerprint＝modsStamp＋datapack hash＋recipeCount 核對），cache hit 即 ready。
- **已量到嘅事實**（寫入 plan §1／§6）：`insert_changed` 全 home 177 條 trace＝false 110／true 7／冇欄位 60；
  7 條 true 全部 `minecraft:iron_pickaxe`（真 LLM）⇒ **插入鏈 work**，流體線冇出現係上游 `paths=0`＋文字唔 render。
- **T0 已完成（SK 批准後，21:55–21:59 真機 minehattanproject）**：探針 log 已入 `ObtainGraph`／`ObtainGraphRuntime`
  （永久 INFO：`obtain.graph.bail`／`bail.types`／`probe`／`summary`）；compile＋4 個 Java check 全綠。
  **量到**：`recipes=17140 → edges=8365／items=5682`、`elapsedMs=21813`、**`fluids=0`**；
  bail `emptyResult=3060／noInputs=5683／selfVariant=32`；大户 `fusion=3481, dissolver=1132, combiner=394, thermal:press=337, mekanism:*`。
  **三種死法（唔止一種）**：Thermal＝`getOutputItems` 唔認（accessor 抽到、built=false）；
  Electrodynamics＝輸出 OK 但輸入／流體抽唔到（bailNoInputs）；Mek／IE／Alchemistry＝`getIngredients()` 空，要 `getInputs()`／`getRecipeInputs()`。
  ⇒ **T2 要三層**（輸出候選鏈／流體元素級／輸入候選鏈），詳 plan §T0。
- **T0.5 已完成（22:0x）**：既有 `ObtainInsertCheck`（402→475 行）加 3 個 case（`caseFluidSublineInserted`／
  `ReinsertIdempotent`／`BodyRange`）；`runObtainInsertCheck OK`。**量到嘅結論**：母 step ＋ 流體子行（同一 `Line` 多行字串）
  **插得入、重覆 call 唔會插兩次、`howToGetBodyRange` 包得住子行**（cursor 原預測 case2 會紅，實測綠 ⇒ 去重係整串 contains，唔怕多行）。
- **SK 22:1x 問「MEK 呢？」→ 已量並改範圍**：Mekanism 4,094 條配方，2,476 條純 item 可表達
  （sawing 1717／crushing 304／enriching 250／pigment 205），1,081 要 chemical（painting 944…）。
  死因＝冇覆寫 `getIngredients()`，輸入在 `getInput()`／`getMainInput()`／`getExtraInput()`（元素解
  `InputIngredient.getRepresentations()`），輸出 `getOutputDefinition()` ⇒ **納入 T2 第三層**（唔再申報唔追）。
  **attribution 更正**：最大 bail 戶 `fusion=3481`／`dissolver=1132`／`combiner=394` 係 **Alchemistry**（chemical 產出／輸入，
  item 路線表達唔到，維持唔追）；`compactor 258` 同 painting 用 `inputsPartial` 標。
- **T1 已完成（22:2x）**：新 `logic/FluidNames`（`display`＋`amountText`，GUI `RouteMaterials` 改 delegate）；
  `ObtainPathText` 步驟行出流體（`items + fluid`）；`JeiLookup.formatRecipe` 用 `layout.fluidsOnePerSlot(INPUT)`
  加入機器摘要輸入（唔再出「(no inputs)」假話）；lang 三語新增 `packai.obtain.fluid`；
  `ObtainPathsCheck` 加 2 個 case（`caseRenderIncludesFluidRequirement`／`caseRenderFluidWithItems`，
  斷言含 `250 mB`）。**閘全綠**：`ObtainPathsCheck`／`ObtainGraphCheck`／`ObtainInsertCheck`／
  `RouteStoreCodecCheck`／`AskJeiHintCheck`／`AskDisplayNameCheck` OK；compile 綠。
  ⚠️ 一次編譯錯（`net.minecraftforge.fluids.Fluid` 唔存在）已由 cursor 修正為 `net.minecraft.world.level.material.Fluid`。
- **T2a 已完成（22:4x–22:5x，真機 minehattanproject）**：新 `logic/RecipeIo`（輸出／輸入／流體候選鏈＋
  per-Class `ClassPlan` memo＋public field fallback＋`getRepresentations`／`getMatchingStacks` 解包），
  `ObtainGraph` 改用佢；新 `ObtainFluidChainCheck`（6 case）綠。
  **量化成果（同一 pack、同一 cases）**：`edges 8365 → 12996（+55%）`、`items 5682 → 6063`、**`fluids 0 → 39`**、
  `bail.noInputs 5683 → 1042`、`bail.emptyResult 3060 → 2220`、建圖 `21.8 s → 24.3 s`（另加 `obtain.graph.progress` 進度 log）。
  `mekanism:enriching/clay_ball`、`electrodynamics:chemical_crystallizer/*`、Thermal `crystallizer` 全部 `built=true`。
- **T2a 暴露嘅真問題（下一手 T2b 要處理，唔准當完）**：
  ① ~~Electro 結晶機 `inputs=[]` 係 bug~~ **SK 23:0x 指正、已用真 jar 核實：結晶機真配方只有流體輸入**
  （`fluidinputs: {0:{amount:200,tag:forge:clay}}`，冇 item）⇒ `inputs=[]` 係正確。
  真問題係 **JEI 摘要把流體容器（Reinforced Canister）當材料**；已修：`JeiLookup.formatRecipe` 有流體輸入時
  用 `FluidUtil.getFluidContained` 過濾容器 item label（`AskJeiHintCheck` 等綠）。`actionLabel=null`（機械名）仍待 T2b
  ② `complete=true` 就算該流體（`electrodynamics:fluidclay`）根本冇來源 ⇒ 要 FluidEdge＋fluidSources＋誠實 `complete`
  ③ `thermal:apatite` 仍以 crafting（拆磚）為第 1 條；crystallizer 只做第 2 條（要等 fluidSources 接線才顯示）
  ④ IE `crusher` 仍未 `built`（`CrusherRecipe` 輸入係 public field `input`）——field fallback 已加，待下輪真機覆核
- **T2c 廣度核查（SK「many recipe is working like that, double check it」；23:0x）**：
  | pack | 流體配方（JSON 廣度） | 入圖「有流體」邊 | 其中純流體邊 |
  |---|---|---|---|
  | `minimalcreate`（Create＋TConstruct） | 1,690（casting_table 610／casting_basin 143／melting 640…） | **301** | 287 |
  | `minehattanproject`（Thermal／Electro／Mek／Alch） | 392（atomizer 38／liquifier 38／chiller 36／bottler 35…） | **104** | 32 |
  新增 `RecipeIo` 流體 accessor `getFluids()`（TConstruct `IDisplayableCastingRecipe.getFluids()`，javap 核實）⇒
  minimalcreate 由「流體邊≈0」變 **301**；`containerSuspects=0`（圖層冇把流體容器當材料）。
  仍入唔到圖嘅大戶（下一手 T2b／T2d）：`tconstruct:melting=296`（item→fluid，要 FluidEdge）、
  thermal chiller／bottler、alchemistry atomizer／liquifier、pneumatic thermo_plant（多數係 fluid 產出或 tag 流體）。
- **T2e 自訂資源普查（SK 23:5x 問）**：`obtain.graph.resources accessors=getEnergy=788,getChemicalInput=341,getGasInput=43,getTemperature=25`；
  `types=Integer=813,SinglePigmentStackIngredient=176,SingleGasStackIngredient=132,TaggedGasStackIngredient=37,TaggedInfusionStackIngredient=32,SingleSlurryStackIngredient=7`。
  ⇒ energy 係機器成本（唔係材料）、Mek chemical／gas 係真輸入但目前被靜靜丟（只得 `inputsPartial`）。T2f 會加 `Requirement` 表達。
  另：census 令建圖 24.3 → 28.8 s（17,140 條），要加 `obtainCensus` config gate（default off）避免生產白付。
- **下一步**：T2b（`FluidEdge`／`fluidSources`／誠實 `complete`）→ T2f（`Requirement`）→ T3 → T4 → T6。

## 2026-10-21 20:4x-21:1x（Discord「test it」＝流體／多流體／其他 mod 機器配方真機矩陣；Hermes 親核）

- 範圍：接 10-21 17:0x 流體修法（working tree 未 commit；autotest jar sha `1d7509a3…`），用 lane (`tools/packai_smoke_linux.py`) 跑三輪真機：`minimalcreate`（Create 14 mod，offline）／`ftb`（358 mod，真 key）／`minehattanproject`（78 mod，offline；config 暫時開 `obtainPaths`，已還原）。每輪 cases 帶 `routeMarks:true`，收 `<instance>/packai/routes/<world>.json`＋`display.body.final`＋截圖。
- **PASS（Create 線）**：`create:iron_sheet`（pressing）／`bar_of_chocolate`（compacting，`Chocolate 250 mB`）／`builders_tea`（filling，`tea 250 mB`）都入到 route JSON；GUI checklist 出「#1 [Compacting] … · Chocolate 250 mB」、Table 有 Fluids 區（截圖 `~/work/mc_lane/evidence_20261021/fluid-A-{checklist,table}.png`）。負控 `minecraft:bedrock` 三輪都 `NO_SAMPLE`。
- **真 bug ①（流體產出鏈斷）**：`ObtainGraph.captureResultStack` 只認 item result，results 只有 `fluid` 就回 EMPTY ⇒ `tryEdge` skip。實測 bar_of_chocolate／builders_tea 路徑各只有 1 步，**冇「chocolate／tea 點來」**；但 pack 內有 `create:mixing/chocolate`（sugar＋cocoa_beans＋250 mB `forge:milk` → 250 mB chocolate）同 `create:mixing/tea`（water＋milk 兩流體 → 500 mB tea，全包唯一多流體配方）。離線掃 jar 規模：FTB **131** 條 fluid-only 產出、createplus **57** 條。
- **真 bug ②（答案文字唔 render 流體）**：`ObtainPathText`（196 行）零 fluid 引用；`JeiLookup.shortIoLine` 只收 item slot。run A 真 trace body 實錄：`- Machine "Mechanical Press, Basin": (no inputs) -> Bar of Chocolate`、`- Machine "Spout": Glass Bottle -> Builder's Tea`（tea 消失）、`- Machine "Seared Casting Table, Scorched Casting Table": Small Plate Sand Cast → Iron Sheet`。同一份資料 GUI 正常 ⇒ 兩個 renderer 行為唔一致。
- **真 bug ③（complete 講大話）**：`ObtainPaths.Path.complete` 只計 item graph。route JSON 實測 `create:bar_of_chocolate` **complete=true**、`create:builders_tea` **complete=true**，而 chocolate／tea 冇任何取得步驟 ⇒ 「（更深步驟未確認）」永遠唔會出。
- **真 bug ④（非 vanilla RecipeManager 嘅機器配方完全睇唔到）**：graph 只 feed `rm.getRecipes()`（`ObtainGraphRuntime.onRecipesUpdated`）。`minehattanproject` graph ready=true 下實測：`thermal:apatite` paths=**1**（只有 block→9 合成，crystallizer「water＋apatite dust」缺失）、`electrodynamics:crystaliron` paths=**0**、`minecraft:clay_ball` paths=**0**，但同一輪 JEI 明明列到 `Chemical Crystallizer: Reinforced Canister -> Iron Sulfate Crystal`、`Compactor: Kaolinite -> Clay Ball`。pack 內 recipe JSON **23,713** vs graph snapshot **17,140**（差 6.5k）。同族：TConstruct casting（`create:iron_sheet` JEI 有 2 條 casting，但 graph paths=1）。
- **真 bug ⑤（冷啟動 race，大包）**：FTB 21,723 recipes，`obtain.graph.snapshot` 20:56:45 → `obtain.graph.ready` 20:57:30（**45 s**），兩個真 ask 都 `obtain.ready=false / paths=0` ⇒ 頭一分鐘問答靜默冇路徑；`RouteMarksAutotest` 200-tick 等待亦唔夠，`quitWhenDone` 早過 flush ⇒ 連 `route-marks.json` 都冇寫出。
- **環境（唔算產品）**：`packai_sandbox_e9e` 世界載入失敗——Placebo/emendatusenigmatica `JsonSyntaxException: Unknown item 'emendatusenigmatica:silver_dirty_dust'`／`silver_clump` 每次 tick 拋出，`openWorld` 卡死；改跑 ftb／minehattanproject。
- **收尾（已核）**：零殘留 java／prism；三個沙盒 route store 已由 `~/work/mc_lane/route_backup_*` 還原；minehattan `config/packai-client.toml` 已由 `.bak-20261021-205832` 還原；證據 `~/work/mc_lane/out/matrix_{fluidA,machinesC,machinesD,machinesE}/`。
- **未修**：五項全部等 SK 一句開 plan（建議分兩批：②③＝文字／判斷細修；①④⑤＝圖層／來源／預熱，要先寫 plan 過 review）。

## 2026-10-20 02:0x-02:4x（Discord；SK 睡前授權「見到 bug 自己修，直到所有 packai 待辦做完」）
- **修 #1＝mixin config 從未註冊（真產品 bug，已 commit `2a67de7`，未 push）**
  - 證據鏈：`packai-0.2.3.jar` manifest 只有 `Manifest-Version`（無 `MixinConfigs`）；jar 內無 refmap、`ScreenMixin` class 仍 dev 名；**9/9 沙盒 `debug.log` 零行 packai mixin**（同一個 `Screen` 有 5 個其他 mod 正常 apply）；atm8 151 個 mixin jar 中 142 用 manifest、只有 packai 用 `mods.toml [[mixins]]`（Forge 1.19.2 唔讀）；git `-S MixinConfigs` 全空 ⇒ 從來未生效過。
  - 影響（有第三方碼實錘）：IC2Classic `ToolTipHelper` 直接 call `Screen.m_96637_/638/639`（= hasControlDown/hasShiftDown/hasAltDown）⇒ 唔強制按鍵就永遠攞唔到「按 Ctrl 展開」嗰段；`TooltipCapture.forceExpanded()` 一直白做。
  - 修法：`build.gradle` 加 MixinGradle plugin ＋ `mixin { add sourceSets.main, 'packai.refmap.json' }` ＋ `jar { manifest { attributes 'MixinConfigs': 'packai.mixins.json' } }`；`packai.mixins.json` 加 `refmap`；新 `tests/check_mixin_registration.py`。
  - **我親驗**：`./gradlew jar` BUILD SUCCESSFUL；jar manifest 有 `MixinConfigs`、jar 內有 refmap（`hasShiftDown→m_96638_`、`hasControlDown→m_96637_`——順序同我早前口頭講嘅相反，refmap 為準）；沙盒 startech 真機 `debug.log`：`Registering mixin config: packai.mixins.json` ＋ `Mixing ScreenMixin ... into net.minecraft.client.gui.screens.Screen` ＋ `Mixing FontDrawCaptureMixin ... into ...Font`，**0 injection error**；autotest run `ok=1 cardsOut=8`（無回歸）。證據留在 `~/work/backups/mixin_fix_evidence_20261020/`。
- **修 #2＝clarify 額度用盡＋模型空回覆 → 假故障訊息（已 commit `fa5235a`，未 push）**
  - 真 trace 實錘 `ask-20261019-050848-none.jsonl`：round 1 `ask_clarify` → `[CLARIFY_LIMIT]` → `model.reply.final content=""` → `display.body.final src=langfallback`（玩家見到「Model or tools may have failed…」）。根因：`clarifyRefused` 係**死旗**（ClarifyArgs 寫、ClarifyAskCheck assert、生產碼零讀者）。
  - 修法：`ClarifyBody.clarifyRefusedBlank(loop, answer)`（refused＋空白）＋`AskEngine` 喺 miss 分支後加 sibling `else if` → 回 `AskResult.clarify(buildFallback)`；`ClarifyAskCheck` +3 case；新 `tests/check_clarify_refused_sink.py`。
  - **我親驗**：`-I tmp-check.gradle runClarifyAskCheck` → `ClarifyAskCheck OK`、BUILD SUCCESSFUL；兩個新 python gate OK；其餘 python checks 6 紅＝全部環境性（Windows 路徑／javap 不在 PATH／kernel py3.11 無 `\z`），**零新增紅**。
  - **未做（明寫）**：同一 chat 第二次 clarify 用現有 harness 重現不了（每 case 開新 chat）⇒ 冇真機覆蓋，只有真 trace 可達性證明；要補「多問一 chat」harness 或 scripted multi-turn stub。
- **順帶記錄**：上游 `yan-mc/minecraft-mod-dev` probe 喺 SK 部 PC（1.20.1 dev）真跑成功——tick freeze 8 秒 `gameTime` delta=0、`step 20` → +20；client `/ui/snapshot` 真控件樹、靠 snapshot 座標撳掣入世界；`/player/mine` 拆方塊後**server 側**變 air（probe 自己 client-side 狀態唔可信）。三個坑：`online-mode=true` 拒 dev client、未 `forceload` 嘅 chunk `setblock` 靜靜回 0、玩家唔 `tp` 到目標旁邊手勢會 MISS。全部已關機清乾淨，冇郁 SK 自己 instance。
- **T3＝冷啟動索引掃描時間已量（commit `de1e24a`）**：`JarLightIndex` 本來零 log，加一行 permanent `Pack AI index scan: cacheLoad/modsJarsScanned/items/took`（`scanMods` 只加 int 回傳，行為不變）。同一個 build、`packai_sandbox_startech`（155 jar、3766 items）：
  - **warm（shard 齊）：142 ms**
  - **cold（移走 `config/packai/jar-cache`）：417 ms**
  - 兩次 run 都 `ok=true`。注意：cold 係「刪 cache」級，唔係「重開機磁碟冷」級。
- **T4＝advancement 取得側 plan v3 跑咗 R3 對抗 review ⇒ 唔可以開工（反方 8:2）**。報告：`docs/plans/reviews/2026-10-20-obtain-advancement-v3-R3.md`。三個硬阻塞：①`manifest.v` 冇版本閘 ⇒ 舊 shard 照用，A 行真機永遠唔出（temp gameDir 驗收會假綠）②`MAX_FACTS_PER_ITEM=8` 會靜默丟 A 行，且 directory 次序不定⇒邊 8 條存活機器相依 ③`A|<ns:path>` 帶唔到 title，`formatFact` render 唔出，而 `A|id|title` 又撞 `|` 拆法＋鎖死語言。另加：白名單行號整體漂移 120–160 行、plan 數字（5950/5437）無法重現（artifact 係 7692/497/114）。**結論：今晚唔實作，要寫 v4（cache 版本閘＋A payload 重新設計＋8-fact 排序＋單一 channel＋行號重校）。**
- **仲等 SK**：① push 三個 commit（`2a67de7`／`fa5235a`／`de1e24a`，要 deploy key）② probe 線 plan（v5）四輪未達 8:2，仍等 ①②③ 選擇 ③ advancement 要唔要我去到 v4（會再用一輪 review）。

## 2026-10-19 05:0x-05:1x（Discord；clarify 真機 smoke 用 stub LLM ＝部分 PASS ＋揭一個真 bug）

- **目的**：反方要求「非確定 LLM 唔可以當閘」⇒ 用 scripted stub 令 ask 路徑確定性，做真機證明。
- **工具（新，唔入 repo）**：`~/work/mc_lane/stub_llm.py`（127.0.0.1:8787，每次都回 `ask_clarify(guess,candidates,why)`，請求寫 `stub_requests.jsonl`）；cases `~/work/mc_lane/cases_clarify_stub{,2}.json`。
- **做法**：HEAD（`3eb1cae`）重編 autotest jar（`-PpackaiAutotest`，sha `b2db278bf0e893d5`）→ 部署沙盒 `packai_sandbox_startech`（世界係 **`New World (1)`**，`New World` 係空殼，preflight 會擋）→ 沙盒 `config/packai-client.toml` 暫時指去 stub（備份 `~/work/mc_lane/packai-client.toml.bak-20261019-050155`，跑完已還原）。
- **PASS（實錘 trace `ask-20261019-050459-none.jsonl`）**：`tool.call ask_clarify` → `tool.result [CLARIFY] queued` → `clarify source=tool level=confirm guess=Iron Chest candidates=3 enabled=true` → `display.body.final src=CLARIFY body="Did you mean Iron Chest? (yes / no)"`；stub 收 11 次請求。
- **未達**：`clarify.mark source=limit-post` 真機觸發不到 —— harness 每個 case 開新對話，「同一 chat 問第二次」重現不了；目前只有 `runClarifyAskCheck`（Java，已綠）覆蓋。
- **🆕 揭到真 bug（未修，等 SK 批 plan）**：模型如果成功叫完 `ask_clarify` 之後**重複再叫**，offer-gate dedupe 之後 round 2–9 全部空回覆 → `model.reply.final content=""` → 玩家見到 `display.body.final src=langfallback`「Model or tools may have failed…」而**唔係**澄清問題。建議：`ask_clarify` 成功即結束 tool loop（唔好再叫下一輪）。
- **環境注意**：lane smoke 期間唔可以同時跑 build／世界生成（單 GPU）。

## 2026-10-10 過夜（Discord；SK 睡前派工 `1a 2a 3 A→C→D 4c` → 全部 Hermes 親跑）
- **keybind 真機驗收＝PASS**（今晚第 1 項）：沙盒 `packai_sandbox`（NFWC 231 mod）跑 **3 輪共 13 條** question case。**run1（10-09 code）揭 2 個真缺陷**：① 模型只傳 `item`／`machine`（唔傳 `query`）⇒ 查詢接唔到，dump 全部行；② 泛用 token「key」經 rawKey 命中 **235/273 行（≈86%）**。→ cursor v5.2（參數兼容 `query|machine|item|問句`＋40% 覆蓋率剔除＋空 query 改回 miss＋指引）。**run2 4/5**：`向左移动→a ⚠撞鍵`／`打开装饰盔甲栏→（未綁）`／`跳跃→空格`＋負控老實答冇資料；**唯一失敗**＝模型當「动力鞘翅推进器」係物品，狂叫 `item_search` 9 輪（body「答句生成失败」）。→ cursor v5.3（只改 `DESC` 加路由指引）。**run3 3/3 OK**（含重測嗰條：`动力鞘翅推进器 → 空格 ⚠撞鍵`）。
- **驗收機械面**：3 輪全部 `status=OK`、body 零 raw 外洩（CLEAN）、**零搶焦點**（foreground before/after 都係 `steamwebhelper`；窗用 PID 認法搬去副螢幕 `on_target_monitor=true`）、跑完 `cases.json` 無殘留、沙盒 trace／logs 已還原。成本：run1 **90.9k tokens／ask** → run2 43.4k → run3 33.4k（rounds 由 9 降到 2–5）。
- **Hermes 自跑閘**：`compileJava` RC=0；harness **60/60 GREEN**（`run_harness_checks.py`，53 s）；python 閘 **125 PASS／1 已知紅**（`check_ask_display_leak` 需真機 log）；jar 內容親核（`Call this FIRST`／`filterGenericTokens`／`GENERIC_TOKEN_COVERAGE_PCT` 都在 `KeybindAskTool.class`；sha `9a885c413edb`）。
- **Commit／push（SK 已批 1a／2a）**：`0c778c6` feat(keybind)（7 檔 code／test＋`code_change_log.md`）＋`c72f1a2` docs（A plan、P0 量測、promo 封面／分發）→ **已 push `aa1b98f..c72f1a2`**（push 前 secrets 掃描 12 檔 CLEAN；冇 `git add -A`；`logs/` 冇入）。
- **B（P0 剩餘量測）報告**：`docs/research/2026-10-10-p0-remaining-measurements.md` — ⑤ 真機：JEI plugin callbacks 合計 **≈1,715 ms**、JEI ENABLED→世界開完 **13.6 s**、cache **37 MB**（item-index 26 MB）；**冷啟動掃描未量**（本輪 index 讀 disk cache，唔准當已量）。⑥ tool 選擇：**4/4 按鍵問題第一個 tool 就叫 `keybind_lookup`**（加第 15 個 tool 冇令路由變差）。④ catalyst 覆蓋率 **未量**（要遊戲內 instrumentation；測量設計已寫落報告等批）。
- **C（promo 封面＋分發包）**：`docs/promo/cover_1280x720.png`（真機 frame＋大字，三輪視覺檢查、已裁走 windowed 標題列）＋`DISTRIBUTION.md`（三渠道文案＋**CTA 核實表**：GitHub 200 ✅／CurseForge 403（Cloudflare 擋自動化，存在性依據 `PUBLISH.md`）／Modrinth **頁面未開**（API 404））＋`COMPLIANCE-CHECKLIST.md`（各平台 AI 披露逐項）。
- **A（離線能力索引）＝完成＋已 push**：plan v2（R1 4:6 → R2 6:4 → **R3 8:2 可開工**）→ cursor 實作 `tools/mine_capability_index.py`＋`tools/capability_queries.json`＋`tests/check_capability_index.py` → 真 DJ2 首跑（235 mods／javap 1,443 次）抽到 JSU `sipush 270`／`sipush 256`／`func_77976_d` 原文。Hermes 自驗：自檢 RC=0、兩次 run byte-identical、**LF-only**、`--timeout-s` 真強制；**code review 2 阻塞已修**（CRLF 寫檔／假 timeout）＋per-query `caps_hit`。commits `3bb9b89`（實作）／`0deb4f2`（review 修正）；報告 `docs/research/2026-10-10-capability-index-report.md`。**已知限制**：候選 ≠ 確認（精度低）、DJ2 `.lang` ⇒ `runtime_visible=false`、21 MB artifact 唔入 git。
- **D（promo shot 1／6 錄製）＝未做**（要 SK 收機；gameplay 仍由 SK 自己錄）。
- **等 SK 一句**：① A 收唔收貨（唔收即回滾 `3bb9b89`／`0deb4f2`）② P0 ④ catalyst instrumentation 批唔批 ③ C 落點（來源分級政策）開唔開工 ④ D 收機時錄唔錄 ⑤ 語音／mic 線仍 HOLD。

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

## 2026-10-15 21:0x（Discord；新功能：ask clarify「唔明就反問」plan v2；S-A2 數字對照仍未驗）
- **SK 要求（原話）**：①「player may just said reactor, not saying which one, so the mod need to re-ask the player which mod」②「不應衹是焦點，如果不理解玩家在說什麼問題的話，也需要反問」③「應該噉講，衹要有唔明都要反問」④ `a+b` ＝兩級（估得到→反問＋順帶估；估唔到→淨係問，唔准自創）⑤ 共同規矩：同一對話最多問一次；唔回應／第二次仍唔明 ⇒ 最可能答案＋標明唔確定。
- **Plan**：`docs/plans/2026-10-15-ask-clarify.md`（commit `5c15363` v1 → `9aea768` v2）。選項 A ＝ pre-LLM 短路：新增 `logic/ClarifyDecision.java`（headless 決策）＋`client/service/ClarifyScanner.java`（worker 掃描）；`AskService.java:320-322` worker 段短路；`ChatSession` 加 `pendingClarify`／`askedClarifyCount`；新 trace event `clarify`。
- **Review**：R1 反方 **8:2（唔過）**＋數字方（更正 `AskService.java:173` `resolveAskIntent` 係純規則、非 LLM）。必改已入 v2：U1 加名詞抽取（`PackIndex.ADVISORY_MACHINE_CJK` :1233-1237）＋可測性拆兩層＋U2 補齊 8 個 intent predicate（:806/:843/:884/:1013/:1131/:1165/:1178/:1256）＋短路點改 worker thread＋新增 clarify trace event。R2 反方跑緊。
- **業界來源**：微軟 HAX 10A、Google Conversation Design、Alexa Design Guide、Copilot Studio、Rao & Daumé 2018（EVPI）、Leuski 2004。
- **S-A2 數字對照：仍未驗**。20:20 run（item 有值＋自由問題）→ 鏈 `triggered=true`／`exit=pin`／`matchedTerm=反應爐`，但 `tiers{A1:0,S:0,A3:0,B:0}`；根因（實碼）＝`AutoTestHarness.java:423-429` 有 question 就跳過 `sample()`／pin；item-only 又只問固定模板（`packai.ask.item_about_id`，唔觸發鏈）。反方指出：問題文字內嵌 `mod:id` 時 `ItemResolver.idInQuestion` 會令該 id 成為焦點（20:46 試跑，環境層面失敗：遊戲未啟動、trace 無新檔）。
- **結論**：clarify 做完之後「問反應爐 → 反問 → 答 Mekanism」＝焦點明確 ⇒ S-A2 數字對照自然可驗。
- **環境坑（已入 skill `minecraft-mod-in-game-autotest`）**：lane service 內直接 spawn Prism 會死（見 `run_mc_request.py` m_prism 註解）；**sandbox 內可直接 `DISPLAY=:0 ~/opt/py-x/bin/python tools/x11_gui.py click <x> <y>` 控制遊戲（唔經 lane）**——runner GUI assist 失效時用呢招。

## 2026-10-16（ask clarify v7 落地 + 真機驗收；望向焦點 plan v1；lane 工具修好）
- **ask clarify v7＝已實作＋已 commit `1a154f4`**（SK 揀 `A1`：修 R3 必改項、唔再開第 4 輪）。三輪反方：v4 6:4 → v5 7:3 → v6 **3:7**（差一分），R3 report `docs/plans/reviews/2026-10-16-ask-clarify-v6-R3-opposing.md`（commit `6c1f10a`）。
- **Hermes 自驗（唔信 subagent 自報）**：`compileJava compileTestJava --rerun-tasks`（JDK17）**BUILD SUCCESSFUL**；`tests/check_*.py` **138 個 → 6 紅**，同 **baseline worktree（HEAD）跑嘅 6 紅逐個一樣**（零新增紅），新 `check_ask_clarify.py` 綠；`runClarifyAskCheck` headless OK；開 jar 親核（新 zh_tw 字串＋`ClarifyAskTool` class 在內）。我另修 cursor-agent 寫嘅 zh_tw（粵語口語 → 書面語，6 行，同 pack 其他字串一致）。
- **真機（startech 155 mod，沙盒 lane）5 條 case 實測**（trace `~/work/mc_lane/out/clarify_v7_startech/`，5 條）：
  - `cl_pos_vague`（「呢部機點用？」）⇒ `ask_clarify` **真係被 LLM 叫**（trace `clarify` source=`tool`、level=`confirm`、4 個候選）、body `src=CLARIFY`「Did you mean …? (yes / no)」✓ 旗艦行為成立。
  - `cl_followup` ⇒ 上限生效：**冇第二次反問**，miss body＋「(I'm not sure about this answer.)」標記 ✓。
  - `cl_neg_clear`（鑽石鎬）＋`cl_neg_clear2`（item 名）⇒ **零 clarify**、正常 prose 答案 ✓（冇誤觸）。
  - ⚠️ `cl_neg_clear2` harness 報 TIMEOUT（180s），但 trace 本身完整正常 ⇒ 係 autotest harness 完成訊號問題，唔係產品 bug（已知，未修）。
- **待跟進（要 SK 批先改）**：上限用完之後、若 LLM 仍然出 prose 答案，嗰個「唔確定」標記目前**只靠 prompt 指示**（非確定性）——同 SK「唔接受 LLM 選擇性行為」原則有落差，建議加確定性 append。
- **真機工具修復（兩個真 bug）**：① `tools/packai_smoke_linux.py` 嘅 `request_launch()`（用 Prism IPC `-l <instance>` 啟動、每 25s 重試）**由頭到尾冇被呼叫**（死代碼）⇒ 一直靠「固定像素偏移撳 Launch」，Prism 窗口被放到 `828x666-14-49`（半出界）就永遠撳唔中；已接返上（commit `1a154f4`）。② `~/work/mc_lane/run_mc_request.py` 加 `out` 透傳（只准指向 lane 之下）令 trace 落返我可讀嘅目錄。③ Prism 幾何重置（備份 `~/opt/prism-data/prismlauncher.cfg.bak-20261016-0540`）。**SK 定嘅閘：5 分鐘冇 game pid ＝ 有問題，即刻查**（今次修好後 2 分鐘入到 game）。
- **望向焦點（look-at）plan v1＝已寫＋已 commit `58d03fc`**：`docs/plans/2026-10-16-look-at-focus.md`。SK 拍板預設：望向 > JEI 游標 > 手持；只做方塊；望太遠／空氣＝冇焦點；冇 item 嘅方塊純文字答；回覆標明用咗望向焦點。第一性原理：方塊註冊名唯一 ⇒「反應爐」跨 mod 歧義**結構性消失**。反方＋數字核實方兩個 reviewer 跑緊。
- **REA＝SK 決定擱置（`3c`）**：唔裝、唔出付費工具替代清單；記入 `~/.hermes/media_import/PENDING_PROPOSALS.md`，保留 2027-01-16 靜音自動重檢（cron `7550ff683616`）。
- **在職證據清單＝已交付**：`~/.hermes/out/2026-10-16_在職期間證據清單_Primetech.md`（Gmail 全箱實查：**零糧單**；替代＝HR first/last-day confirm、MPF（AIA／eMPF）、銀行月結單、timesheet／duty report）。SK 已自己打電話問 Primetech ⇒ on hold，唔使幫佢寫 email。

- **望向焦點 plan v2＝已 commit `6f5ded5`**（R1 反方 8:2 ⇒ 修 10 條：`Registry.BLOCK`／`HitResult.Type.BLOCK` 硬檢／方塊要問世界／刪「GUI 開住 hitResult 唔更新」假機制／harness 能力缺口…）。R1 兩份報告 `docs/plans/reviews/2026-10-16-look-at-focus-R1-{opposing,numbers}.md`。
- **R2 review（反方＋數字核實方，兩個 subagent；05:51:40 派、05:57 收）＝反方 7:3 唔過**；報告原本只存在於 subagent session（session 快滿／已被 auto-reset）⇒ **已由 `~/.hermes/state.db` 回收落檔** `docs/plans/reviews/2026-10-16-look-at-focus-R2-opposing.md`／`-R2-numbers.md`（每份連原派工 instructions；未經 Hermes 逐條覆核，引用前要親核）。R2 findings **6 條**：**① blocker** capture 點矛盾（`mc.screen==null` 恆假 ⇒ LOOK 喺 GUI 內問嘢永遠唔會產生、`la_pos`／`la_text_only` 兩條驗收 case 結構性必紅，同時 SK 拍板嘅「望向＞JEI＞手持」會被靜默反轉成 JEI 優先）；② §2.2 `probe()` 回傳型別錯（lambda 唔可以回 record）⇒ 編唔到；③ `gen_tmp_check.py` hardcode Windows 路徑 ⇒ Linux 生唔到 harness task，而 `tmp-check.gradle` 係 tracked（要 regen，唔係「唔改」）；④（medium-high）§2.2 用**方塊**註冊名做 `ItemRef` id——實測 4/8 vanilla 方塊 block-id ≠ item-id（`redstone_wire`／`wall_torch`／`oak_wall_sign`／`carrots`）⇒ 入現有 item 管線會靜默解唔到、卡／fact 冇出；⑤（medium）§3 import 白名單寫 `net.minecraft.world.phys.*` 包 `BlockPos`／`Direction` 係錯 package（真位置 `net.minecraft.core`）；⑥（瑣碎）§2.3「寫死一段」係中文 pseudo code、照抄編譯唔到。其中 1 條 high＝上面條 SK 問題（`mc.screen` 恆非空 ⇒ JEI 幾乎全開、SK 拍板嘅「望向＞JEI＞手持」被靜默反轉）。報告正本另有 subagent 批次檔 `~/.hermes/cache/delegation/subagent-summary-0-20261016_060137_457766.txt`／`-1-…457919.txt`（長度同 repo 兩份一致：11,842／6,818 字元）。
- **等 SK 一句**：GUI 開住時邊個焦點贏 —— **(1) JEI 揀嗰個贏、望向只在冇開 GUI 時用（我建議）**／**(2) 任何情況望向都贏**。
- **本 session 收尾（SK 06:01「停手準備 hand off；而家個記錄太大」）**：盤點 = packai 未 push（fresh fetch 已核：remote `97acab19`、ahead **13→17**（本輪加 4 個 docs commit）；**絕對數字會自我過期，push 前一律重新 `git rev-list --count origin/main..HEAD`**）⚠️ 呢個 repo remote 係 SSH，而 non-interactive shell **冇 ssh-agent、repo 冇 `core.sshCommand`** ⇒ 直接 `git ls-remote/fetch` 會 `Permission denied (publickey)`；要 `GIT_SSH_COMMAND="ssh -i ~/.ssh/id_ed25519_packai -o IdentitiesOnly=yes"`（10-16 實測通）、工作樹 3 項（`forge/1.19.2/tmp-check.gradle` modified、`docs/research/artifacts/2026-10-13-pack-sweep/`＋`tools/packai_make_world_linux.py` untracked）；jarvis-pc 本輪零改動；lane 無殘留進程（Prism／java／cursor 皆無）。

- **SK 拍板焦點次序（2026-10-16 06:1x，原話）**：「以玩家當刻聚焦嘅視角嚟講 應該係優先選擇鼠標停留嘅物品位置(呢一樣嘢我哋已經有, press y)，所以玩家視角嘅呢一個功能應該只會係出現喺玩家特別要求嘅時候，又或者玩家按〔原文如此〕開啟 packai's gui嘅時候先至需要」⇒ **滑鼠停留（JEI hover／press-y）＞ 望向**；望向只在「玩家特別要求」或「由 packai 面板問嘢而冇 hover」時用。**連帶**：plan §2.3 嘅 `mc.screen` 閘作廢（改用 hover 做閘）⇒ R2 ①blocker＋②high 同一刀解決；已寫入 plan §2.3.1／§2.3 註記／§7 v3 行（本 commit）。
## 2026-10-19 15:1x（clarify 預算 plan v2；SK 批預設 2；等 18:05 review）
- **舊 plan 死因（真 trace 實錘）**：`ask-20261019-050848-none.jsonl` 第一次叫 `ask_clarify` 即回 `[CLARIFY_LIMIT]`（額度被同對話上一條 ask `050459` 用掉）→ 9 輪 → `model.reply.final content=""` → `display.body.final src=langfallback`（`packai.reply.ask_body_unavailable`＋`askMissRetry`），零 `clarify.*` 事件。舊 plan（`2026-10-19-clarify-loop-exit.md`）R1 反方 **2:8 唔過**（診斷倒轉／對照證據錯／驗收必紅）。
- **SK 決策（10-19 15:0x）**：問「player 可以設定詢問多少次？」→ 研究後批 **預設 2**。研究檔：`~/work/research/2026-10-19-clarify-budget-research.md`（學術／Microsoft HAX／Google／Apple／NN-G；結論：冇廠商把次數交用家，真正病因係預算綁「整個對話」）。
- **新 plan v2＝已寫**：`docs/plans/2026-10-19-clarify-budget-v2.md`。三改動：①每題最多 1 次＋每對話上限可設（0–3，預設 2）②用完／被拒 → `ClarifyBody.buildUncertain` 確定性「唔確定」卡（`src=CLARIFY_UNCERTAIN`，唔准技術字串）③`AskToolLoop:437` 後早退（pending 或 refused 即交返控制權）。
- **排程**：cron `28165a073773`（18:05 唯讀對抗 review，8:2 門檻）→ cron `a8a1c01e3c4d`（18:50 只有 review 過才經 cursor-agent 實作＋親驗；唔准 commit）。
- **未做**：實作、真機驗、commit；「⑤ 因果鏈 provenance」今日未開工。

## 2026-10-19 19:2x（clarify plan v2 review 2:8 唔過 → v3 已寫；等 20:15 R2）
- **R1 v2 結果：正方 2 : 反方 8（唔過）**。報告 `docs/plans/reviews/2026-10-19-clarify-budget-v2-R1.md`。三個致命：①插入點 `:1048` 在 miss 分支之後 ⇒ refused 路徑永遠到唔到 ②驗收 math 不可能（cap=2 兩條問題仍會 clarify）③`cap=0` 唔等於關（`clarifyAsked()` 恆 true ⇒ 全局蓋「唔確定」章）。另有 O4/O5/O6/O7/O9。
- **18:50 implement job 正確自行 abort**（唔過就靜音）。
- **v3 已寫**：`docs/plans/2026-10-19-clarify-budget-v3.md`。核心修正：①插入點改 `AskEngine:1006` miss 分支第一句 ②三個 predicate（`clarifyActive`／`clarifyExhausted`／`clarifyAllowedNow`）③cap=0 真正等於關 ④src 重用 `CLARIFY`（食 `AskService:352-356` 短路）⑤每檔行為表 ⑥驗收 V1 cap=1／V2 三條問題／V3 cap=0／V4 cap=3。
- **排程**：cron `01b8206acdb6`（20:15 R2 review，8:2）→ `6c824eb04100`（21:00 過才實作＋親驗，唔准 commit）。
- 案件引用更正：吃額度的是 `050845`（05:08:46），`050848`（05:08:48.6）被拒；`050459` 的 +1 已被 reset，機制未證實。

## 2026-10-21 06:4x（新 idea：路線產生後自動標記機器／材料 — 只做研究，未開工）
- **SK 原話**：「當packai幫你產生完自動化路線之後可以根據需要的機器和材料 自動在JEI/hei/任何物品管理器中標記出來(按順序)」
- **研究（真 jar：JEI 1.19.2-forge-11.8.1.1034，`javap`＋vineflower）**：
  - 公開 API `IJeiRuntime.getBookmarkOverlay()` 回 `IBookmarkOverlay`，**只有 `getIngredientUnderMouse()`**（唯讀）⇒ 公開介面**冇得加**書籤。
  - 內部 `mezz.jei.gui.bookmarks.BookmarkList`（public class，`add`／`remove`／`addToListWithoutNotifying`／`notifyListenersOfChange`／`getElements`），實例只藏在 `BookmarkOverlay.bookmarkList`（private，冇 getter）⇒ 要反射。
  - `BookmarkList.add()`（`:30-38`）**每次都 `bookmarkConfig.saveBookmarks(...)`＝即刻寫盤**；`addToListWithoutNotifying(value,false)`（`:88-102`）唔寫盤但亦唔通知 UI。
  - 插入位置受 `clientConfig.isAddingBookmarksToFrontEnabled()`（`:31`）影響 ⇒ 用戶設定會令自動加入變**倒序**。
  - **版本差異實測**：11.6.0.1019 jar **冇** `mezz/jei/gui/bookmarks/BookmarkList.class`；11.8.1.1034 有 ⇒ 反射路徑跨版本唔穩。
  - 公開 API `IIngredientFilter.setFilterText(String)`（搜尋框）＝安全可還原，一次只亮一件。
  - `IRecipesGui.show(List<IFocus<?>>)`／`showRecipes(...)` 可程式開配方頁（現時 packai 未用過：`grep showRecipes|setFilterText` 零命中）。
- **資料面**：`ObtainPaths.Path(Item target, List<Step> steps, boolean complete)`；`Step(edge, groups)`，edge 有 `recipeId/typeId/output/outputCount/inputGroups` ⇒ 機器（typeId）＋材料＋次序**已經有**。⚠️ steps 係由**成品往原料**append（`ObtainPaths.java:96-100`），要「先做咩」要反轉。
- **待 SK 揀**：A 自家「材料／機器清單」面板（建議；manager-agnostic、有編號、唔郁 JEI 設定）／B A＋JEI 搜尋框逐件亮（公開 API）／C 真 JEI 書籤（反射＋寫盤風險）。

## 2026-10-21 07:3x（路線標記：plan 演進 v1→v4；代 R4 review；Hermes 親核）
- **SK 決定**：書籤鏡像要做（2+3），並主動提出「獨立『路線庫』GUI ＋ 一顆掣加入 JEI／任何物品管理器」⇒ 已寫入 v4 D10（取代 v6 之前嘅「面板最近路線清單」）。
- **Plan 檔**：`docs/plans/2026-10-21-route-item-markers.md`(v1)／`-v2.md`／`-v3.md`（主體）／`-v4.md`（增量：D6 跨日續做、D7 釘住、D8 F3+T 重注入、D9 R3 修正、D10 路線庫 GUI＋管理器能力表）。
- **Review 進度**：R1 兩邊各 7（相加 14 ⇒ 作廢）；R2 字數 8:2／反方 4:6；R3 字數 8:2／反方 6:4；**R4 有界（只核 D6–D9）跑緊**。若 R4 反方仍 ≤2 達標 ⇒ 併入 D10 交 SK 一句 go。
- **親核事實（jar 實查，唔靠 reviewer）**：
  - 1.19.2 JEI 三代：`11.6.x`（`addToList(ITypedIngredient,false)`、`getIngredientList()`、冇 move）／`11.8.x`（`addToListWithoutNotifying(IBookmark,false)`、`getElements()`、`moveBookmark(prev,bookmark,offset)`）／`≥11.64`（`BookmarkList implements IBookmarkManager`、`moveBookmark(IBookmark,int)`、`addToListWithoutNotifying`）。
  - `IBookmarkManager` 首現＝`11.64.0.1080`（2026-09-06）；**`11.68.0.1088` 有 mandatory `mezz_config [0.6.3,1.0.0)`（jar 冇 shade，沙盒全部冇）⇒ P1 lane 要用 11.64**。
  - `saveBookmarks` 全 jar 只有 `add`／`moveBookmark`／`remove` 三個 caller ⇒ **append-only 注入＝真零寫盤**（書籤檔 hash 不變可作驗收證據）。
  - `BookmarkConfig`：11.8 同步寫 `bookmarks.ini`；11.64+ `SAVE_DELAY 5s` 寫 `bookmarks.json`。
  - 現碼缺口：`ObtainGraph.Edge` **冇材料數量**、機器只有 `actionLabel` 文字（無 Item）⇒ 面板唔准出「×N」或機器 icon；`ObtainPathService.Result` 冇 paths（要加）。
  - 管理器：EMI `EmiFavorites.addFavoriteAt(EmiIngredient,int)`（runtime 套件、可指定位置）；REI 只搵到 `FavoriteEntry` 抽象＋provider，**未證實公開寫入 ⇒ 按鈕 disabled**。
- **下一步**：R4 結果 → 併 D10 → 交 SK go → cursor-agent 實作（唔准自己 commit）。

## 2026-10-21 08:3x（路線標記：實作 S1–S4.5 完成＋真機驗（未 commit）；Hermes 親核）
- **SK 定案**：新 GUI 存路線＋一粒掣加入物品管理器（v5 plan）。
- **實作（全部未 commit；cursor-agent 寫、Hermes 親 compile／跑 check／真機核）**
  - 新檔：`logic/RouteMarks.java`、`client/routes/{RouteStore,RouteStoreCodec}.java`、`client/jei/JeiBookmarkBridge.java`、`client/emi/EmiFavoriteBridge.java`、`client/routes/ItemManagerBridge.java`、`client/gui/RouteLibraryScreen.java`、`client/autotest/RouteMarksAutotest.java`、2 個 check。
  - 改檔：`ObtainPathService`(Result+paths)、`PackAiConfig`(routeMarks/Jei/Emi 預設 off/true/true)、`SettingsRegistry`、`ClientSetup`(key 未綁)、`AiAssistantScreen`(入口掣)、三語 lang、`AutoTestHarness`(routeMarks/openRouteLibrary flag)、`tools/packai_smoke_linux.py`(pass-through)、`tmp-check.gradle`(gen)。
  - **Pin／釘住功能已移除**（SK 設計冇此概念；亦拆掉 R4 兩個 blocker）。
- **真機結果（沙盒 lane、`cases_route_marks.json`）**
  - JEI **11.6.0.1019**（minimalcreate）：`profile=P3`、注入 7／4、`orderMatches=true`、清走 7／4、書籤檔 hash 三次全等 ⇒ **零寫盤**。
  - JEI **11.8.1.1034**（ftb）：`profile=P2`、注入 6／5、次序正確、清走 6／5、hash 全等。
  - **EMI**（e9e 1.1.24）：`APPEND_ONLY`、注入 6／4、移除 6／4。
  - e9e 嘅 JEI 側＝NONE：**EMI 嘅 JEMI 接管咗 JEI 書籤面板**（overlay class＝`dev.emi.emi.jemi.runtime.JemiBookmarkOverlay`）⇒ 該 pack 應走 EMI（正確行為）。
  - 跨日：關 game 後 `packai/routes/packaiworld.json` 仍有 2 條（iron_pickaxe、mechanical_press）。
- **期間修真 bug 5 個**（全部由實機數字／trace 捉）：①11.6 冇 `IBookmark` 令 bridge 靜靜變 NONE ②`getTypedIngredient` 應該喺 IElement 唔係 IBookmark ③11.6 `addToList` normalize ⇒ 移除要靠 uid 比對 ④`remove()` 內部會寫盤 ⇒ 改靜默移除（反射欄位）⑤11.6 私欄名係 `list`（唔係 `ingredientList`）。另修 runner 丟 root flag、graph 未 ready race。
- **未驗／缺口**：①**P1（JEI 11.64.0.1080）未驗**：換 jar 後 11.64 與 pack 內 addon 撞（`IRecipeLayoutBuilder.addOutputSlot` 例外）令問答 NO_SAMPLE（已還原 11.6 jar）；要另開乾淨 instance ②GUI 外觀未截到圖（autotest 截圖路徑未觸發；`openRouteLibrary` flag 已可開畫面但要人手／x11 睇）③REI 仍 disabled（未證實公開寫入 API）。
- **Gates**：`compileJava/compileTestJava` OK；`runRouteMarksCheck`／`runRouteStoreCodecCheck`／`runSettingsLayoutCheck` OK；`tests/check_autotest_flag.py` OK（修咗 harness 寫檔路徑格式）。`tests/check_*.py` 全 143 個仍有 6 個環境性 FAIL（JAVA_HOME／javap PATH／Windows 路徑／py3.11 `\z`），與本改動無關。
- **未做**：commit／PR（等 SK 一句）；GUI 人手目視；P1 lane。

## 2026-10-21 13:5x（GUI 大執 + code review 結果；未 commit）
- **做法改變**：唔再靠 SK cap 圖。已加 autotest `openScreens`（assistant/settings/routeLibrary/modelPicker/invPick）逐個 `Screenshot.grab`，我用 vision 自己核。
- **已修（S27–S32）**：全部畫面改平面自繪掣（冇原版石頭貼圖）、移除滿版黑底（0x99000000）、面板貼內容高度、搜尋框 placeholder、分段選擇器縮到 44×20、輸入框文字垂直置中、路線庫行文字截斷 + 狀態移入面板、model picker 唔再高亮資訊行。
- **SK 決定**：側欄唔要 item 圖示（16px 睇唔清），改「組標題」（S32 A）。
- **Code review（2 個 subagent，靜態 + JEI/EMI bytecode 核對）**：
  - BLOCKER：`RouteStore.save()` 正式流程零 caller（只有 autotest 寫）⇒ 玩家路線庫永遠空，功能唔成立。S33 接線（AskResult 加 `paths`、AskEngine 傳、AiAssistantScreen 存）。
  - MAJOR：`RouteLibraryScreen.onClose()` 唔 clear（已修 S32 D）；`clear()` fallback call JEI `remove()` 會寫盤（S33 刪）；EMI `addFavoriteAt` 會 save 而 `removeFavorite` 唔會（S33 補 save）；`PackAiConfig` 冇主開關（已修 S32 C）；`jeiConfigHash` 掃錯路徑（S33 改 Files.walk）；`0xFFAAAA` alpha 0（已修 S32 B）；設定頁按鈕 y 計早過 filter（已修 S32 B）。
  - MINOR：`RouteMarks` mandatory 可超 12、`decode()` 冇 cap、`capByBytes` 單條可超 256KB、`RouteStore` worldKey 碰撞（未修，待議）。
- **未做**：真機 smoke 驗新接線（S33 後要跑）；11.64 P1 lane 未驗；`RouteStore` worldKey 用 hash 取代 sanitize（待 SK 決定）。

### 2026-10-23 03:0x — 問答 freeze 已修（SK 報「每次問 AI 停一秒」）
- **量度**：加常駐日誌 `ask.phase=capture_begin/capture_a_ms/classify_ms/capture_b_ms/apply_begin/apply_end`＋`ask.capture.step name=… ms=`＋`render.stall_ms`（只在 near_ask 記）。sandbox 冇 GPU 只有 ~1 fps，所以比較**階段耗時**，唔比 fps。
- **根因**：`AskService.resolveAskIntent()` 喺 **Render thread** 打 HTTP（`LlmClient.chatOnce`）做意圖分類 → `resolve_ask_intent ms=736`、`capture_end ms=1110/1378`。
- **第一次嘗試（撤回）**：改用本地關鍵詞 `PackIndex.maintenanceIntent()`（0 ms、少一次 API）→ SK 明確否決「關鍵詞版唔夠準」。
- **最終修法（`dec694f`，已 push）**：保留 remote LLM 分類，但把 `runAsk` 拆兩段 render-thread hop：
  `capture A`（~39 ms）→ 背景 `classify`（~1045 ms，ForkJoinPool）→ `capture B`（~68 ms，JEI 摘要／卡片過濾）→ 背景 engine。
  實測畫面線程由 1110–1378 ms 降到 **~107 ms**；log 仍見 `intentClassify source=remote`。
- `askIntentRemote` **default true**（local 只作 opt-out）；`tests/check_recipe_intent_split.py` 的 R6-A 斷言已更新（原斷言要求「每次問答都行 remote」，現改為檢查兩段式結構）。
- **未修（同一類風險）**：`ensureNonEmptyBody()` 喺 apply 路徑仍會打 LLM（只在「模型答完但正文空白」時觸發），要修就同一招（搬背景）。
- Gate：python 152，4 個一直存在嘅環境性紅；Java 67 OK／4 個環境性失敗（Windows 路徑＋jar fixture）。

### 2026-10-23 07:5x — Tree 升級：12 輪 plan review 後定案（先記低）
- **SK 決定**：先完成 Tree／材料數量（A），之後用真玩家問題（機制／策略類）做 AI 答題評測基準。
- **真玩家問題樣本（SK 貼 Discord 截圖）**：「有沒有辦法阻止大史萊姆的重生？這裡只有2格高，我用龍科技傳送門送怪物，但大的史萊姆會都被傳走，降低刷怪效率」→ 屬**機制／做法**，唔係「物品點取得」；obtain graph 答唔到，要靠 pack 知識＋LLM。此類問題要入評測集。
- **根因（第一性原理）**：路線檔只存平鋪 steps（`RouteStoreCodec:37-44`）；層級／狀態／材料數量都係即時推導，推導時丟失（`RouteTreeScreen:333-360` `counts.merge(in,1,sum)`）。狀態三分一（原材料／循環／查唔到）冇旗標分開。
- **定案設計（plan v12）**：`docs/plans/2026-10-23-route-tree-counts-v10.md`（檔名舊，內容 v12）
  1. **只存「材料數量」**：`StepDto` 加 `List<InputDto> inputItems`；**唔存樹**（樹可重算；存落去會撐爆 `capByBytes` 256KB 上限，單筆超標會清空整個路線檔）。
  2. 數量鏈：`Ingredient.getItems().getCount()` → `RecipeIo.pickCount` → `inputGroupsWithCounts`（單趟 accessor）→ `Edge.inputCounts`（normalize 等長）→ `ObtainGraphCache` encode/decode（decode 要守衛）→ `ObtainPaths.Step.inputCounts`（對齊 `groups()`）→ `fromPath` → `StepDto.inputItems`。
  3. 樹即時算：`RouteTreeBuilder.build(rootId=route.itemId(), steps)`，`Status {OK, CYCLE, NOT_IN_ROUTE}`（**唔准叫「冇配方」**，因為路線只存一條 path）。
  4. UI：單欄縮排卡片（`TreeCardLayout`＋`Fit`），每卡 名稱／×數量／機器圖示＋名（16×16）／需求／流體來源／狀態行。
  5. `BUILDER_VERSION` 3→4（舊 cache 一次性重建）。
- **Plan review 紀錄**：12 版，反方分數 8→7→4→5→6→5→7→5→5→7→5→5；最後一輪 4 個扣分點已寫入 v12（`NO_RECIPE` 改名、`ObtainGraph:802/833` 接駁、`StepDto` 7/8 參數保留、`canonicalEdgeLine` 加 counts）。
- **實作進度**：Phase 1（資料層：RecipeIo／ObtainGraph／ObtainGraphCache／ObtainPaths／RouteStoreCodec／RouteTreeData＋Builder）已派 cursor；Phase 2（UI：TreeCardLayout／Fit／RouteTreeScreen 改寫）未開始。

### BACKLOG（新 idea，未排期）
- **機制／策略問答**（SK 2026-10-23 貼真實玩家問題）：「有沒有辦法阻止大史萊姆重生？2 格高＋龍科技傳送門會把大史萊姆傳走，降低刷怪效率」。屬**遊戲機制／做法**，唔係「物品取得路徑」，obtain graph 幫唔到；要用 pack 知識＋LLM。**要做**：(1) 收集真實機制問題做評測集（來源：Discord 截圖、SK 提供）(2) 量度現時答題質素 (3) 再決定要唔要知識庫／工具。
- **參考 mod（2026-10-23 已查）**：兩個都係 Fabric 1.21.1，API 同 packai（Forge 1.19.2）唔同 → **只可借設計，唔可以 copy-paste**。
  - `MrPauk335/Verity-Minecraft-Mod` — **CC0 1.0**（可自由取用）。AI 同伴：`ai/VerityDialogueController`、`ai/VerityLLMClient`；賣點係 **rich game context**（座標／生命／生物群系／附近礦物真座標／村莊／手上物品／時間）＋NBT 記憶 40 條＋「唔識就講唔知」。另有 Whisper STT／Fish Audio TTS（我哋唔需要）。
  - `mari0w/blockwright` — **MIT**。AI 控制層：`WorldScanner`（讀附近方塊）、`PlayerSnapshot`、`ActionExecutor`（受控工具：給物品／時間天氣／指令）、`PlacementPolicy`／`PlacementStats`（擺方塊前記錄藍圖、擺完讀回驗證）、`JobPoller`。呢個先係「讀世界＋做嘢＋驗證」最貼近我哋想要嘅。
  - **BACKLOG（SK 2026-10-23 指示：「記住先，遲啲再加」——唔准而家做）**，可借方向：
  1. world-context 收集器（座標／生物群系／附近方塊／礦物真座標／村莊／手上物品）→ 塞入 ask prompt，答機制題要有「你身邊有咩」。
  2. 每個 action 之後 read-back 驗證（借 blockwright `PlacementStats`／`verify` 思路）。
  3. 對話記憶分「事實」同「對話」兩層（借 Verity NBT memory 思路）。
  授權：Verity = CC0；blockwright = MIT（可參考，但兩者都 Fabric 1.21，唔可 copy-paste）。

## Tree 升級（2026-10-23）
- 資料層：`RouteTreeBuilder` 由 `SavedRoute.steps` 重建真實樹（唔寫入存檔）；`StepDto.inputItems` 只存「算唔返」的材料數量；`Edge.inputCounts` → cache `"inputCounts"` → `Step.inputCounts` → `inputItems`。
- UI：Tree 由 18×18 節點改成縮排卡片（物品圖示＋名＋×N、機器 8×8＋名、需求行、狀態行）；`TreeCardLayout` 純幾何；`Fit.fit()` 截字。
- 兩輪 code review 已修：① graph 未 ready 時寫死 `lastGraphGen` 令之後永不刷新（改 edge-triggered）② `RecipeIo` 同一個 `getItems()` 走兩次（改單趟）③ 刪死欄位／9 條死 lang key ④ C4（cache roundtrip）／C5（recipe→inputItems 端到端）契約測試。
- 新增 `RouteTreeFixtureCheck`（讀真路線檔，env `PACKAI_ROUTES_FIXTURE`）：對真實存檔驗 10 nodes、`ae2:quartz_glass ×2`、`minecraft:redstone ×3`、`NOT_IN_ROUTE` 語意、parent/depth 一致性。
- 真機驗（ftb 沙盒）：`routeId` 與 `scrollY` 已接入 autotest，可以指定路線並滾到下半部截圖。

### Tree review 未修項（2026-10-23，已修 F1/F10/F15/dead-code，以下待辦）
- **Checklist 數量語意不一致**：`RouteTreeScreen.formatStepMaterials` 仍用 slot 數（`merge(in,1,sum)`），Tree 用 stack 數 → 同一材料兩邊數字唔同（F12）。
- **`RouteTreeBuilder.findStepForOutput`** 由尾向前掃、只比 `output()`：同一 item 在路線出現兩次會掛錯 step（#2）。
- **`ObtainGraphCache.decodeEdge`** `catch(Throwable){return null}` ＋ 只 `has("inputCounts")` 就 `getAsJsonArray` → 型別錯會靜默丟 edge，cache 仍當 HIT（#5）；`decodeGraph` 應比對實際 edge 數 vs 檔頭 edgeCount。
- **`inputs` 與 `inputItems` 兩份事實**：舊 build 讀新檔再存會靜默剝奪 count（#9）；建議 `inputs` 由 `inputItems` 投影 ＋ 檔頭 schema 版本。
- **`orderedSteps()`** 名字暗示排序／過濾，但 builder 用 `route.steps()` 索引 → 日後改動會靜默錯位（#10）。
- **測試弱點**：C4 legacy 測試其實經 encoder（守衛不可達，T2）；R5 恆真（T3）；R4 近乎恆真（T4）；`RecipeCountsCheck` 未驗單趟（應比 `statGetItemsCalls` delta==1，T5）。
- **小修**：scissor 負尺寸（F7）、`Fit` O(n²)＋surrogate（F8）、空樹無提示（F9）、`obtain.counts` 覆蓋率分母含 bail（F5）、`count<=0?1:count` 五處重複（#4）、zh_tw「冇」用語（#15）、plan 文件 task 名 drift（#16）。

### 未完成驗證（2026-10-23）
- **truncation 提示未喺真機影到**：單元測試（寬樹 4096／深鏈 MAX_DEPTH=64）已綠、compile 綠、已 push `ac3acd8`；但 autotest 的 `scrollY` 仍然影到頂部（`pendingAutotestScrollY` 已加、仍無效），所以「已達顯示上限」嗰張卡**未有截圖證據**。教訓：`routeMarks:true` 會用真實路線覆寫測試用的合成路線（曾令 routeId 揀唔中）。

## 大包 bench 真機（2026-10-23 13:00）
| pack | recipes | edges | 建圖 ms (3 runs) | edges 一致 | sha |
|---|---|---|---|---|---|
| minehattanproject | 17,140 | 13,007 | 460 / 277 / 217 | ✅ | run1 `6ed4ae…` ≠ run2/3 `79e039…` |
| ftb | 21,729 | 17,287 | 927 / 577 / 538 | ✅ | run1 `ded04e…` ≠ run2/3 `cead92…` |
- **無 hang、無 timeout**（T6 之前 5.1 分鐘的問題已不復見）。
- **未解**：同 session 第一次建圖 sha 與之後不同（minehattan calls 36039 vs 35798）。cache 存的是 run1 → 冷啟 cache 可能同重建結果唔一致。建議修法：建圖前先做一次「prime pass」走一次所有 recipe 的 `getItems()`（暖 MC lazy cache），再正式建；成本一次 ~0.2–0.9s。
- T7（lazy tag 展開）已正式結案（`docs/plans/2026-10-22-obtain-index-slimming.md` 頂部加結案 banner）。

## 2026-10-23 主線收官（全部有真機證據）
| # | 項目 | commit | 證據 |
|---|---|---|---|
| 1 | Tree 卡片（圖示／名稱／×N／機器／需求／狀態） | `21dac6b` | 真機截圖 Iron Plate ×3、Force Stick ×2 |
| 2 | 正文空白修復搬離 Render thread | `02c73cc` | apply 12ms/6ms；新 log `ask.bodyrepair=bg` |
| 3 | 上限用盡確定性標示 | `ac3acd8` + `c4fabc3` | 真機橙字 `Reached the display limit…`（測試專用開關 forceTreeTruncated） |
| 4 | 自動重疊檢查器 | `76512c9` | `TreeOverlapCheck` + negative control（11*rows→10*rows 即紅） |
| 5 | 材料／機器總表（tab/drawer/bar 可選） | `56f68de` + `33ced81` | 3 張真機截圖；設定頁 `ui.routeSummaryMode` |
| 6 | 路線檔名防撞遷移 | `b570a4a` | 真機 log `migrated New_World.json -> New_World-411b5590.json` |
| 7 | 大包不卡 + sha 穩定 | `949e3ed` | minehattan 17,140 配方、ftb 21,725；3 次 sha 一致 `cdf2d46535ce55cb` |
| 8 | T7 結案（lazy tag 唔做） | 文件 | `docs/plans/2026-10-22-obtain-index-slimming.md` 頂部 banner |
| 9 | 同一材料重複掛錯 step | `209d1f5` | `RouteTreeDuplicateStepCheck` + negative control |

### 為什麼 sha 曾經唔同（已定位）
不是 lazy cache（暖機會固化負快取）。log 證據：`obtain.partial.first count=3185` → `obtain.partial.rebuilt count=3184`，即**第一次建圖恰好多一條** `inputsPartial=true` 的邊（duroplast bottling），重建後正常 → 兩次內容差一條邊，sha 就唔同。bench diff 顯示**只有 `inputsPartial` 一格不同**（duroplast bottling、rubberwood sawmill）。現時做法：第一次建圖如 `partialEdgeCount()>0` 就再建一次，用第二次結果寫 cache。ftb 冷啟成本 1.7s → 2.6s（每個 pack 版本一次）。

### 未決（等 SK）
1. 4 個要真 pack 資料的 check（`AdvisoryChainCheck` 要 ATM8、`AskJsObtainSitesCheck` 要 NFWC KubeJS）：資料不在時 **SKIP** 還是維持紅？我建議 SKIP + gate 摘要列明，若設定檔聲明「應有資料」而缺失則照紅。
2. 第 9 項 Promo／發佈：等 SK 自己錄 gameplay，我負責版面／剪接／文字卡。

### 仍待辦（backlog，未排期）
- `ObtainGraphCache.decodeEdge` 型別守衛（`has("inputCounts")` 要加 `isJsonArray()`），decode 失敗要 warn + 當 CORRUPT。
- `inputs` vs `inputItems` 兩份事實（舊 build 讀新檔再存會靜默剝奪 count）；建議 `inputs` 由 `inputItems` 投影 + 檔頭 schema 版本。
- `orderedSteps()` 改名／統一 builder 同 screen 用同一個 local。
- `Fit` O(n²) + surrogate pair；scissor 負尺寸；`obtain.counts` 分母含 bail。
- `count<=0?1:count` 五處重複 → 抽單一入口。

## 2026-10-23 22:40 — 兩個「環境性失敗」已由 PC mirror 解決
- 新 Prism（`...\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0`）有 `packai_sandbox_All the Mods 8 - ATM8`（382 jars, 816MB）同 `AI_test_NFWC_DIM`（kubejs 9,144 檔）。
- 已 tar 串流到 `~/opt/prism-data/pc-mirror/instances/{packai_sandbox_atm8,AI_test_NFWC_DIM}`。
- 跑法：
  `PACKAI_PRISM_ROOT=$HOME/opt/prism-data/pc-mirror JAVA_TOOL_OPTIONS=-Dpackai.prism=$HOME/opt/prism-data/pc-mirror ./gradlew -I tmp-check.gradle runAdvisoryChainCheck runAskJsObtainSitesCheck`
- 結果：`AdvisoryChainCheck OK numeric=19/22 routes=19/22`、`AskJsObtainSitesCheck OK` → **Java check 由 79 OK/2 FAILED 變成 81 OK/0 FAILED**（同機有 mirror 時）。
- 唔需要 SKIP 分層；冇 mirror 的機器仍然會紅（可考慮日後自動偵測＋SKIP）。

## 2026-10-23 23:05 — 機制問答實測（8 條）＋兩個真 bug
跑法：`mc_lane/cases_mech_eval.json`（8 條機制題），答案喺 `latest.log` 的 `Pack AI display body`（trace 只有 tool call，冇 final body）。

結果：
- 8/8 有答案、引用 12 個 id **全部存在**於 pack item-index（0 虛構）。
- **Bug A（已修 699dca5）**：inline marker 外洩 — `{{item:x}}`×3、`{item icon}`×1 直出玩家畫面。`RecipeEmbed.normalizeInlineMarkers` 線性掃描＋7 條 `RecipeEmbedInlineCheck`；negative control 要 mutate 函式本體才紅（改 caller literal 唔紅，因為測試直接傳 maxKinds）。
- **Bug B（已修 1fe0f8a）**：卡片材料清單 — `RenderRecipeCardsAskTool.cardBriefAscii` ①把輸出物當材料佔一格 ②`n>=4` 靜默截斷。
  真機前後：`Mega Torch+3 Torch+2 Diamond+2 Dark Oak Log` → `Torch×3+Diamond×2+Dark Oak Log×2+Block of Gold×2`（金塊返嚟、數量用 ×）。
  新純函式 `briefFromFreq(..., maxKinds=6)`，超出加 `+…`；`CardBriefAsciiCheck` 7 條；negative control：`Math.min(maxKinds,totalKinds)→totalKinds` 會紅。
  永久診斷：`ask.card.slots out=… layout=… grid=… resolved=… empty=… ids=…`（每卡一行）。

未修（需另開 plan）：
- Tag 顯示成單一代表物品：`#minecraft:logs` → "Dark Oak Log"、`#darkutils:plate_base` → "Deepslate"。玩家會以為只可用該物品。需要保留 ingredient 的 tag 身份（現在 grid 只有 ItemStack，tag 資訊已失）。

## 2026-10-24 09:10 — 配方卡「多選一」保真（forge 完成，neoforge 同步中）
- 依據：JEI 實證（反編譯 `RecipeSlot` + `CycleTicker`）：slot 一次只畫一個候選、每 20 tick 輪換、tag 名只喺 tooltip、單一物品 tag 預設隱藏 → **唔可以自己反推 tag 名**。
- 真機教訓：反推曾把 `#bookshelf:slime_balls` 顯示成 `#gag:labeling_tool_charges`（玩家會搵錯材料）→ 文字只寫 `any of N`，唔寫 tag。
- forge commit：`0dfb900` feat(cards) + `6030c7c` test(cards)，已 push。
- 新檔：`SlotOptions`（tagId 保留但顯示層唔讀）、`SlotCycler`（20 tick，同 JEI）、`CardSlotLabels`、`CardLabelMerge`（key = `(id,label)`）。
- `RecipeCard` 加 `slotOptions` component + compact ctor 按 layout 對齊 + 手寫 equals/hashCode（排除新欄位；`jeiLayout` 保留）。
- 真機驗證（ftb）：`Torch×3+Diamond×2+Dark Oak Log×2 (any of 118)+Block of Gold×2`、`Deepslate×6 (any of 17)+Sugar×2+Pink Slime (any of 2)`。
- 測試：`SlotCyclerCheck`、`CardSlotLabelsCheck`、`CardLabelMergeCheck`、`RecipeCardEqualityCheck`、`TagPickCheck`、`CardBriefAsciiCheck` 全綠；3 個 negative control 實測會紅。
- 環境：JDK 21 已裝 `~/opt/jdk-21.0.13+9`（neoforge 用）。

## 2026-10-24 12:05 — 五項 backlog 全清（forge + neoforge）
- repo 衛生 `feba91f`：untrack 236 個 `forge/1.19.2/logs/*.log.gz`，`.gitignore` 加 `*.log.gz`、`**/logs/`。
- 材料總數 `3c5ef3c`：`RouteMaterials.inputCounts` 改用 `StepDto.inputItems()` 每格數量（之前一格當 1，6 個深板岩會算成 1）；補 fallback 與 count<=0→1；新 `RouteMaterialCountsCheck`（5 條）。
- NeoForge harness `3311d8e`：`build.gradle` 把 test source set 接上 main classpath（之前 43 個 check 全部 compile 唔到）；全套由 0 可跑變 **42 OK / 3 SKIP / 0 FAILED**。
- neoforge 修正 `e3b2cf3`：`QuestGuide.langCodeFromPath` 認得開頭就係 `lang/` 的路徑（原本只認 `/lang/`，令 en/es 同分）；`ItemRefCheck`／`WebSearchCheck`（要 FML bootstrap）、`QuestDisplayNameCheck`（要 item registry）改為誠實印 SKIP 唔扮失敗。
- sha 決定性：ftb 真機 3 次 `edges=17292 items=9365 sha=661cf851f1e934b6` 完全一致（1791/1327/1286 ms），舊問題重現唔到；`obtain.bench.diff` 診斷留住做探測器，冇改邏輯。
- 截斷橫幅真機截圖：`forceTreeTruncated` + `openScreens:["routeTree:tree"]` 成功，Tree 分頁顯示橙字 `Reached the display limit — some materials are not shown`（存 `~/work/routeTree-truncation-banner.png`）。Cards 分頁唔會顯示該 banner。

## 2026-10-24 16:40 — Clarify 句改由模型寫（8dcff04）

- 根因：`ask_clarify` 的 `why` 一直被 parse 但從未顯示（`.why()` 全 repo 0 次），玩家只見到 canned `clarify.unclear`。
- 改動：`ClarifyBody`（sanitize／語言檢查／截 160）、`ClarifyAskTool` 兩個描述、`AskEngine.traceClarify` 加 body＋why 欄位、新 `ClarifyBodyWhyCheck`（9 case）、`ClarifyAskCheck` fixture。
- Review：R1 6:4 未過 → v2 → R2 兩邊 8:2 過。
- 驗收：compile 0、4 check OK、2 Python 閘 OK、CJK 0、負向對照會紅。
- 真機（ftb 沙盒）：`Pack AI clarify why len=65 shown=true`；body 首行 = 模型句「Several mods name an item "Energy Cable" — which one do you mean?」，第二行係我們排的候選。

## 2026-10-25 05:10 — 測試環境 1 fps 追查（結案：環境所致）＋ 對話歷史污染修復

- **1 fps 追查**（JFR 真 attach `jcmd JFR.start`；Prism 的 `JvmArgs` 會被逗號拆散，用 jcmd 才成）：卡頓係**精準 1 tick/秒**（每分鐘 60 次、每次 ~980ms），與問答無關。已排除：① harness `scanFile` 每 tick 全檔 parse（真存在，JFR 12.7% samples，已改增量讀取 `8c9b21b`，但**改完無改善**）② vsync（`enableVsync:false` 生效後仍 274 次）③ GC（0.4%）。伺服器 `xrandr` **所有輸出 disconnected** ⇒ headless X 下 GLFW/MC 被節流，屬環境問題。所有 `options.txt` 已還原。
- **Bug：失敗訊息被塞返入對話歷史**（`ask-20261025-045631-none.jsonl` 實證）：每輪 `send.history` 都帶 `assistant: "Query incomplete (no model reply)… [Sources] pack local recipes"` ⇒ 一題失敗污染之後所有題。修法：`AskResult`/history entry 加 `synthetic` 標記，組歷史時剔除；新 `AskHistoryFilterCheck`（負向對照會紅）。
- **Bug：Java 硬編碼中文** `找不到，試改名/英文 id` → `packai.reply.tool.item_search_miss`（三語）＋ `tests/check_tool_result_lang.py` gate（allowlist 舊命中，新增即紅）。
- commit：`8c9b21b`（harness 增量掃描）、`7d25ee0`（歷史污染＋CJK），已 push。
- **評測**：6 包 145 題人手核完 → `~/work/packai_qa_report.pdf`（startech 17/17 最好；createplus 9 題退化為「資料冇」）。

## 2026-10-25 15:45 — NBT 變體配方缺口（蜂蛋 DNA）正式收口

**問題**：問「鉍蜂生怪蛋」時答案冇 Honey Treat、甚至寫「JEI 沒有把它當產出的合成卡」。

**根因鏈（全部真機 trace 證實）**
1. `render_recipe_cards` 用 `ItemResolver.stackFromId()` → **裸 stack（無 NBT）**，JEI focus 對唔上 NBT 變體輸出 → `foundOutput=0`
2. 只有當手上 stack 帶 NBT 時才找得到 → 同一題中/英、不同輪結果不同
3. 補救層逐層加：catalog fallback → ObtainGraph（按 id，但唔覆蓋自訂 category）→ **JEI item→category 索引**
4. 索引按 id 命中 22 個 category、24 張卡（全部係「換蜂種」變體），真配方（Incubation）被擠出 cap
5. 修法：索引每個 (item, category) 記樣本 output stack（帶 NBT）→ refocus；候選 **同 namespace 優先** → `productivebees:incubation` 命中

**實測數字（ATM8）**：JEI `cats=412 recipes>=190,163 items=30,105`；force_build 4.8s；tick budget 6ms（`indexBuild` 只供 autotest）

**真機驗收**
- EN：`BaBee Incubator: Egg + Honey Treat (Bismuth) -> Bismuth Bee Spawn Egg [card:1]`，cards=2
- ZH：`BaBee 孵化器（Incubation）：放雞蛋＋蜂蜜零食…JEI 卡上示範的是 Dark gem 那款`，cards=2（中文無變體譯名，樣本只能做示範）
- 5 個變體物品（Honey Treat／Apotheosis Gem／Enchanted Book／Potion／蜂蛋）卡數全部由 0 變有

**commits**：`7705d5f` `72e0713` `da52f0a` `d43f2fa` `7cfc438` `5591d95` `08efa06`（全部已 push）

**已知未做**
- `id_index refocus` 的 `matched=` log 在中文問題會誤報 true（只影響 log，不影響選樣本）
- 中文變體名（Bismuth）無譯名 → 只能講「對應蜂種」＋示範卡，未能點名 Bismuth
- assistant 面板截圖永遠滾到最底（harness 只有 route tree 有 scrollY），要頂部截圖需加 hook

## 2026-10-25 16:55 — GUI：卡片要滾才出現＋icon 壓住免責聲明

- **A. 卡片全有或全無**：`AiAssistantScreen:2645` 只畫「整張入框」的卡 → `MAX_SHAPED_CARD_H=168` 幾乎一定超框 → 空白；改成卡片**相交即畫**（scissor 已裁剪）。`AssistantLineVisibilityCheck` 綠。
- **B. focus icon 穿出免責帶**：`renderInputHeldStrip:909` `focusRowY-18` 落喺免責帶；`renderItem` 開 depth test → 之後 `fill` 蓋唔到 → 壓住「AI」兩字。已 clamp 位置 + icon pass 後 `disableDepthTest`。
- 真機截圖：免責行已無 icon（確認）；「卡片唔需滾」在沙盒未重現（該輪答案冇卡），需 SK 部機覆核。
- commit `11d801e`，PC 已部署 sha `36085CBA5AC8987A`。

## 2026-10-25 19:00 — 目標行整合（A2）＋模式化標籤＋圖示

- 已選物品 strip 併入目標行：最多 3 個 icon，第 4 件起 `+N`（5 件＝3 icon + `+2`）；獨立 `已選：N` 行已刪（`2a59807`）
- 目標行文字跟模式：物品／方塊／實體；未選顯示 `未選擇物品／方塊／實體`（新三語 key）；物品／方塊唔再夾 id（`221254a`）
- 目標行文字前加 16×16 圖示（物品＝物品圖、方塊＝方塊 item、實體＝spawn egg）；未選唔畫（`221254a`）
- 實體「真 3D model」未做：16px 睇唔清，等 SK 決定要唔要 32px + 加高行
- checks：`FocusLineModeCheck`／`FocusRowIconsCheck`／`AssistantLineVisibilityCheck` 綠；`check_focus_line_mode.py`／`check_focus_panel.py` OK
- PC 已部署 sha `8C00DCB81F19A080`

## 2026-10-25 20:25 — 目標行收尾＋世界生成 tag＋部署防呆

- 目標行重複圖示：已選 stack 同目標 stack（item＋NBT）相同就唔再畫第二個（`f31f17c`）
- 世界生成補漏行：raw tag（`#ns:path`／`標籤 #…`）一律剔；整行只剩 tag 就整行唔顯示（`800abee`，`WorldgenTagFilterCheck` 綠）
- **部署防呆**（`f5eedc1`）：`tools/pc_deploy.ps1` 用 Win32_Process 檢查該 instance 的 javaw，開住就 `REFUSED`＋stage `.pending`
- 事故記錄：20:07 在遊戲運行中換 jar → 之後 lazy load `WebSearch` 出 `ClassNotFoundException`（磁碟 jar 有 class，但 JVM 仍持舊 zip handle）。**jar 換完一定要重開遊戲**。
- 目前 PC 部署：`545545B00F6FD516`（含 `800abee`）

## 2026-10-25 20:36 — 非焦點卡收斂（SK 選 A）

- 真機 trace：問蜂蛋（focus=spawn_egg_ashy_mining_bee），模型自己叫 `render_recipe_cards(item_id=productivebees:bee_cage, role=uses)` → 倒 3 張 Bee Cage 用途卡（bee_bomb／guide_book／sturdy_bee_cage）
- 規則：非焦點物品 `uses` ≤1、`output` ≤2、output 排前、總數 ≤2；焦點物品不變（`b4834bd`，`NonFocusCardCapCheck` 綠）
- PC 已部署 sha `F46FBD0B08CD9506`

## 2026-10-25 21:35 — 通用配方過濾器誤殺真配方（Bee Cage）

- 真機：`jei_lookup(bee_cage, OUTPUT)` 的 `[合成]` 顯示「略過 1 筆通用配方」，但 JEI 實有 2 木 + 蜂蜜瓶 → Bee Cage
- 根因：`involvesSpamItem`／`involvesSpam` 掃 ingredient 的**全部 tag 變體**，木板 tag 內含 FramedBlocks 的 `framed_*` 變體 → 整條真配方被丟
- 修法：INPUT 改成「全部變體都中招才算 spam」；OUTPUT／CATALYST 不變（`8304bb2`，`SpamFilterVariantCheck` 綠）
- PC 部署 sha `02FA1EAA3EFD763A`

## 2026-10-25 21:50 — 通用配方過濾器改 namespace 制＋ATM8 全配方審計

- 實測（382 jar）：`framedblocks:*` 141（真噪音）；`ae2:*_covered_cable` 36、`quark:*_framed_glass` 34、`chipped:framed_*_planks` 12、`botania:framed_*` 4 等係正常物品
- `#minecraft:planks` 459 個成員；`#forge:chests` 19 個（framedblocks 只佔 1）→ 用名字判斷必誤殺
- 改法：`isSpamItemId` 只認 `framedblocks` namespace + `integrateddynamics:facade`（`4122df0`，`SpamFilterVariantCheck` 綠含 negative control）
- 新增 `spamAudit` autotest 探針（`packai_smoke_linux.py` 已轉發）
- **審計結果**：412 categories / 190,168 recipes / 281ms / cap_hit=false；完整表 `~/work/atm8_recipe_audit.csv`
  - 噪音大戶：`minecraft:anvil` 33,610、`create:automatic_shaped` 17,632、`botanypots:crop` 17,197、`minecolonies:*` ~21,000、`minecraft:mason_table` 3,045、燃料類 ~12,000、`jei:information` 2,958
  - 約 53% 配方屬重複 category／非取得途徑
- PC 部署 sha `A21BF1FEAFE46E8B`
- **待 SK 決定**：是否按上表實作 category 黑名單（跳過重複 category 的卡與文字）
