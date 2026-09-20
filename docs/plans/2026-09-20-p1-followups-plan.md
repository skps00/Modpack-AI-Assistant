# Pack AI — P1 收尾（a+b 代碼審查遺留）plan v5

Status: **v6 — 剔除 F3（記 P2）＋ 新增丙（玩家字眼對齊）＋ 收齊 R3／R4-lite 九條（含 keepOreRoute 位置更正、丙 常數成本、NC 逐條獨立）；SK 2026-09-20 批准（甲／丙一齊落、乙另開 plan）。R4-lite 已核 → 落 cursor。**
Date: 2026-09-20 · Owner: packai (`super_minecraft_AI_player`) · Forge 1.19.2 · 未改 production code
Log: repo 根 `code_change_log.md` · Version: 不 bump

## 0. 範圍清賬（每次改版都列，唔准靜默縮／擴範圍）

上游報告 6 條 P1 去向：P1-1／P1-2＝**上一輪 `f325c4e` 已修**（R2 親核 code：`WorldgenIndex.java:32-33,74-89`、`WorldgenFacts.java:579,584-594,589-591,623-634`＋jar class）；P1-3＝**撤回＋記觀測性缺口**（test consumer 真存在：`WorldgenRoutesCheck.java:78`）；P1-4→**F2**；P1-5→~~F3~~ **v5 剔除、記 P2**（理由見下）；P1-6→**F1**。

**v4 新增：F1b（inline object feature ⇒ 10 個礦物永久冇「礦脈大小」）——同一症狀嘅主要原因、規模比 F1 大 5 倍。**
**v5 新增：丙（玩家字眼對齊業界慣用）——SK 2026-09-20 ok。**

**v5 剔除 F3（人化雙寫／純 DRY）理由（三點，唔係偷懶）**：① 佢**零行為改動**（三語 lang key 齊 ⇒ 舊 fallback 結構上不可達）；② 佢唯一守衛（源碼閘「恰一個呼叫點」）在白名單內**做唔到**——`lootTableObtain(` 喺 `logic` 有 **5 個呼叫點**（`AcquireAskTool:112`、`AskEngine:1712`、`PackIndex:1318`、`Plainify:212`＋定義 `ReplyLang:454`），後兩者唔在白名單，硬做會外溢風險；③ 按 SK「基建先答『真係需要？』」原則，一個冇行為價值、守衛又做唔到嘅重構唔值得佔本輪。**記入 P2 清單**（將與 P2-2 位置式模板一併處理）。

## 1. 量測（全部 Hermes 親跑；有 log／sha／檔名）

### 量測更正（v3 → v5，三處）
1. **v3 寫「真機 40 條 Ore spread 行全部有 size」＝錯。** 兩點錯：R1 reviewer 咁講，我未自己核就採納；而我自己早前掃描其實已列出咗兩條缺 size 行（當時我叫佢哋 `MISS`）。親跑重抽（`packai_sandbox_ftb/.../trace/ask-20260920-092542-occultism_silver_ore.jsonl`，acquire tool.result）：
   - `Ore spread: thermal:silver_ore｜Height: -60..40｜Count: 4` → **冇 Vein size、冇 Biome、冇 Dimension**
   - `Ore spread: immersiveengineering:silver｜Biome: #minecraft:is_overworld` → **冇 size、冇 Count**
   ⇒ **症狀今日已經玩家可見**。
2. **v3 「FTB fan-out 0 個」嘅掃描方法有 bug**：我用「jar 名」做 key，同一 jar 內兩個 placed 檔被摺疊 → 假 0。改**檔案級 key** 重掃（親跑）：
   - `packai_sandbox_ftb`：placed **93 檔**（**R3 更正：真 placed 91**；另 2 個係 `data/ars_nouveau/tags/worldgen/placed_feature/*.json`＝**tag 檔**，`kindFromPath` 先判 `tags/worldgen/` ⇒ 唔係 placed）、inline(object) feature **30**（`minecraft:ore` **10**）；configured 被 ≥2 placed 檔引用 **2**（`pneumaticcraft:oil_lake`、`rftoolsbase:dimshard_overworld` size=5）
   - `packai_sandbox_universio`：placed 6、inline 0；共享 1（`pneumaticcraft:oil_lake`）
   - **已知限制（明文記錄）**：`feature` 係 **array** 形態＝**0 個**（兩個包都冇）；解析失敗嘅檔＝0（R3 核）。
3. **R3 更正嘅細項**：`sharedConfigured()` 係**新增方法**（唔係改現有）；`PackAiConfig.java` scanModJars 定義喺 **:442-447**。

### F1b｜inline feature（主因，真機可見）
- 形態：`thermal_foundation-1.19.2-10.3.1.57.jar` → `data/thermal/worldgen/placed_feature/silver_ore.json`：`"feature": {"type": "minecraft:ore", "config": {"size": 8, …targets…}}`（`apatite_ore` size=9）。**jar 內冇對應 `configured_feature` 檔。**
- 根因：`WorldgenFacts.parsePlaced:271-276` 只接受 `feature` 係**字串** ⇒ `configured = null` ⇒ size／type 永遠冇 ⇒ 人化行永遠冇「礦脈大小」。
- 規模：FTB 30 個 inline 中 **10 個係 `minecraft:ore`**（thermal apatite/cinnabar/lead/nickel/niter/oil_sand/silver/sulfur/tin＋elementalcraft inert_crystal）⇒ 全部永久冇 size。
- 覆蓋率（新抽樣器量測，`tools/make_cases_pack.py`）：ATM8 ore 交集 **52**、FTB 41、Star Technology 18；三包 inline ore 分別 **10／10／9**。

### F1｜fan-out（次因，fixture 級重現）
- `AcquireAskTool.java:198` `Map<String,String> configuredOwner` 一對一、後者覆蓋（`:219`）→ `:216-231` 只掛落唯一 owner。
- fixture 重現（`%TEMP%\f1_probe.log`）：1 個 configured（size=9）被 2 個 placed（count=9／4）引用 ⇒ `mod:ore_a` 行 **冇 Vein size**、`mod:ore_b` 行有。

### F2｜gap 側缺閘（已重現）
- `%TEMP%\f2_probe.log:92`：旗標 ON 載入 → `setScanModJars(false)`（唔 reset）→ `routeLinesForItem` 仍回 **n=2**。
- 更正 caller 事實：`reset()` **production 零 caller、test 一個**（`AcquireJarRoutesCheck.java:50`）。

### B3 嘅反駁（保留記錄）
R2 指 `AskEngine:1721`／`:696` 缺 `scanModJars` 閘。**親核 config 文件：`PackAiConfig.java:442-447` 明寫 scanModJars 只管 `data/**/recipes|loot_tables` → jar-cache → `[JAR]` 提示**（worldgen 唔在其範圍；worldgen 由獨立 `WorldgenIndex` 提供，預設 OFF 時 a+b 仍運作）。⇒ **唔加閘**。殘留成本（首個 ask 全掃、P2-8）另記 P2。

## 2. 本輪改動（3 項 ＋ 丙）

### F1b（主修）｜`parsePlaced` 支援 inline（object）feature
- `WorldgenFacts.parsePlaced`：`feature` 係 object 時，讀 `feature.type` 同 `feature.config.size` → 存為 placed 記錄嘅 `inlineType`／`inlineSize`。
- **Emit 線（R3 硬條件，寫死）**：只喺 inline 個案加 `inline_type=<type>` `inline_size=<n>`，**一律放行尾**（最後兩個欄位）；並且 `AcquireAskTool.field()` 嘅終止符清單要**明列** `inline_type=`／`inline_size=`，防止「讀到行尾污染 height／count」。驗收要有一條 case 斷言 `Height:` 段唔含 `inline_`（否則即係污染）。
- `AcquireAskTool`：size 取 `configured size` → 否則 `inline_size`。
- **R4-lite 更正**：`keepOreRoute` 真身喺 **`WorldgenIndex.java:106`**（`:83` 呼叫），**唔係 `AcquireAskTool`** ⇒ 白名單必須加 `logic/WorldgenIndex.java`（只准 `keepOreRoute`）。改法：**額外**接受 `inline_type=minecraft:ore`，現有規則一律保留、不得改動。
- 驗收：headless fixture **直接抄真 jar 內容**（thermal `silver_ore.json` inline size=8）⇒ 人化行必須含 `Vein size: 8`；另 apatite（size=9）同一 case。**污染斷言（R4-lite 加）**：inline 行嘅 `Height:` 段要**逐字**等於 `-60..40`（即 `inline_` 冇被吞入）；另加一條「污染線自身」NC（故意移除 `field()` 終止符 ⇒ 該斷言必紅）。NC：還原 object 處理 ⇒ 必紅。**回歸**：現有全部 `WorldgenRoutesCheck` fixtures 逐字不變（證明只影響 inline）。
- **真機前後對照**：`ask-20260920-092542` 已有修前樣本（`thermal:silver_ore` 冇 size）；修後 FTB 沙盒問同一物品 ⇒ 必須出 `Vein size: 8`。

### F1（次修）｜`configuredOwner` 一對多
- `Map<String,List<String>>`；placed 行有 ` configured=` → `computeIfAbsent(cfg, …).add(id)`；join 時 owner 清單 null/空 → 保留 fallback（掛落 configured id 自己），否則逐個 owner 掛（保留插入序）。
- 驗收：**新增** `WorldgenRoutesCheck.sharedConfigured()` —— 合成 fixture **恰好 2 行＋逐字等於期望字串**；真實個案 fixture（`rftoolsbase:dimshard_overworld` size=5 × 2 placed）兩行都要有 size。NC：還原一對一 ⇒ 必紅。
- **刪除** v3 嘅「mergeRoutes 次序不變」作為 F1 證據（R2：`WorldgenRoutesCheck:115-124` 本來已綠、零鑑別力）——改為只喺回歸對照（§3.4）觀察。

### F2（必修）｜`JarLightIndex.routeLinesForItem` 加 config self-gate
- 只做①（首句 `if (!PackAiConfig.scanModJars()) return List.of();`）；②（toggle 時 reset）明文延後（白名單外＋觸發 ~358 jar 重掃）。
- 驗收：`AcquireJarRoutesCheck` 常設 case（ON→ensure→有；OFF→**必須空**；ON→還原），**唔靠其他 case 嘅狀態**。NC：移走閘 ⇒ 必紅。

### 丙（玩家字眼對齊，SK 已批）
- 改 1 個 key（`packai.reply.worldgen_ore`）三語同步：
  - `en_us`: `Ore spread:` → `World gen:`；`Height:` → `Y level:`；`Count:` → `Veins per chunk:`（其餘 `Biome:`／`Vein size:`／`Dimension:` 保留）
  - `zh_tw`: 「礦物分佈：%s｜生態域：%s｜高度（Y）：%s｜礦脈大小：%s｜每區塊礦脈數：%s｜維度：%s」
  - `zh_cn`: 「世界生成：%s｜生态域：%s｜高度（Y）：%s｜矿脉大小：%s｜每区块矿脉数：%s｜维度：%s」
- 理由：業界慣用（JER "World Gen"／EMI Ores "Y-level distribution"、"Vein size"）；**同我哋 README 對齊**（README 第 16 行寫 "vein size and veins per chunk"，但答覆字串寫 `Count:` ⇒ 今日內部不一致）；`Veins per chunk` 亦明確區分 JER 嘅 "Avg. blocks per chunk"（唔同指標）。
- 成本（量測，**R4-lite 更正**）：production 邏輯**零耦合**（`worldgenOre` 只讀 template 並按 `｜` 切段、空欄位自動唔出；`InfoCompleteness` 唔用字眼比對）；要改嘅：3 個 lang 檔＋**2 個測試檔**——`WorldgenRoutesCheck` **`ZH_TW` 逐字常數（`:18-22`）＋ `:96` `ZH_TW.equals(human)`** 及 `:102` `en.contains("Ore spread: …")`；`InfoCompletenessCheck:122,131`。
- **呢個係預期改動，唔算「新增紅」**（字眼改咗、斷言必須跟住改）；但**唔准**改動斷言語意（欄位數／值／順序一律唔變）。
- 驗收：三語 key 齊、`%s` 數一致（6/6/6）、受影響檢查重跑綠、README／CF 描述**已經**用對字眼（唔使改）。

## 3. 驗收（Hermes 親跑）
1. `compileJava`／`compileTestJava` RC=0；逐任務名真跑全部 `run*Check`（53 個）全綠。
2. NC（**逐條獨立**，R4-lite 要求）：NC-F1b、NC-F1、NC-F2、NC-污染線——每條都要 ① 由備份還原**單一**修正 ② 跑指定檢查見到**紅**（記紅嘅 assert 字串）③ 還原成修後狀態逐 byte（記 sha）④ 重跑見綠。
3. python 閘：124 檔（已知紅仍 1，**唔准新增紅**）；`tests/check_worldgen_lookup.py`（自述 mirror parse／format）要**同步新欄位**，否則明文記 drift。
4. **回歸對照（可執行定義，R3 要求列檔名）**：diff 對象＝**確定性行集合**——① 原始 `[WORLDGEN] …` 行、② 人化行、③ gap 段行（`On record, not in the answer:` 之後）。Baseline 檔＝`packai_sandbox_ftb/packai/trace/ask-20260920-08{3,4,5,6,7,8}*` 同 `…-0925*`（09-20 08:30–09:26 共 39 個檔，逐個抽上述三類行）。**唔用** LLM 最終 body 逐字（R2 親核：同題三 trace 長度 1665／1253／1785）。
5. 部署：**`--target packai` 唔准用**——親核 `state/mc_mod_jar_guard.json`：target `packai` 指向 **`instances/AI_test_NFWC_DIM/minecraft/mods`＝SK 真實遊玩 instance**（照用會踩紅線）。沙盒一律用 **ad-hoc 形式**：`python "$LOCALAPPDATA/hermes/scripts/mc_mod_deploy_jar.py" --jar forge/1.19.2/build/libs/autotest-dev-0.2.3.jar --mods <沙盒>/minecraft/mods`（照樣有 backup＋驗證；MC 開住會 REFUSED）。**「修前」樣本必須喺部署前拍**；真 instance `AI_test_NFWC_DIM` 一個 byte 都唔郁。
6. 真機（FTB 沙盒，GUI 要過 SK 活動 Gate，記低操作者／時間＋trace 檔名）：
   a. **F1b 前後對照（同一題，R4-lite 更正）**：修前樣本係 `ask-20260920-092542-occultism_silver_ore`（問題＝`occultism:silver_ore`，同一答案內含 `thermal:silver_ore` 嗰組）⇒ **修後問同一題**，斷言答案內 `thermal:silver_ore` 嗰組**由「冇 Vein size」變「Vein size: 8」**；另**直接問** `thermal:silver_ore` 做第二次對照。
   ※ **開窗規則**：SK 打機／用機時唔准開；要開一律用 `run_hidden.vbs` 開 launcher（唔閃 console），開完即刻用 `bg_launch.py --check` 核有冇搶焦點並記入報告；搶到要用 `AttachThreadInput` 還原原本前景窗。
   b. **F2 同一 session toggle**（前置：先確認 `config/packai-client.toml` `scanModJars = true`）：設定畫面「掃描模組 jar」ON → 問 `oxygen_tank`（要見 jar 行）→ 同 session toggle **OFF** → 同題再問 ⇒ 正文同 gap 都要零 jar 行 → toggle 回 ON ⇒ jar 行返嚟。**明文禁止**「改 toml＋重開」當驗；
   c. **丙**：三語各問一次，睇新字眼（`世界生成：`／`World gen:`）真機出到。
7. 白名單核：先記 `git status`（應同 `HEAD` 一致）→ 逐檔 sha256 對比 → 白名單外零觸碰。

## 4. 白名單（cursor-agent）
`logic/WorldgenFacts.java`（只准 `parsePlaced`＋emit 線）、`logic/WorldgenIndex.java`（**只准 `keepOreRoute`**，R4-lite 加）、`logic/AcquireAskTool.java`、`logic/JarLightIndex.java`、`logic/AskEngine.java`（只准 `infoGapLines` 相關）、`logic/ReplyLang.java`（只准 `worldgenOre` 註釋；字眼一律改 lang 檔）、`lang/{en_us,zh_tw,zh_cn}.json`、`test/.../WorldgenRoutesCheck.java`、`test/.../AcquireJarRoutesCheck.java`、`test/.../InfoCompletenessCheck.java`、`tests/check_worldgen_lookup.py`、`code_change_log.md`。
禁令：唔准 commit／deploy／開遊戲／改卡落位檔／改 `PackAiConfig` 預設／自報測試結果（驗證一律 Hermes 做）；跑唔到報 `NOT RUN`＋原始錯誤。

## 4b. 執行記錄（2026-09-20，Hermes 親跑）

**改動落地**：cursor-agent 兩輪（第一輪 4 項；第二輪清死碼＋補斷言）。改動檔案＝
`WorldgenFacts.java`（+17/−2：`Placed` 加 `inlineType`／`inlineSize`；`parsePlaced` 認 object；emit 行尾加兩欄）、
`AcquireAskTool.java`（+23/−7：size fallback、`field()` 終止符加兩 key、`configuredOwner` 一對多）、
`JarLightIndex.java`（+3：config self-gate）、3 個 lang 檔（各 1 key）、
`WorldgenRoutesCheck`（+119：6 個新個案）、`AcquireJarRoutesCheck`（+17：`scanModJarsGate`）、
`InfoCompletenessCheck`（2 行字面）、`tests/check_worldgen_lookup.py`（mirror＋斷言）、`code_change_log.md`（1 行）。

**發現（我指示有誤，非 cursor 錯）**：`keepOreRoute`（`WorldgenIndex.java:106`）對 `placed_feature` **本來就 `return true`** ⇒ 我要求嘅「額外接受 inline_type」係**死碼**。第二輪已刪（`WorldgenIndex.java` 回到 HEAD 零改動），改為加斷言 `keepOreRouteAcceptsInline`（inline 行／一般 placed 行要入、`minecraft:tree` 要唔入）——即「唔使改代碼」係有測試守住嘅結論。

**驗收結果（全部親跑，log 路徑見下）**
| 項 | 結果 | 證據 |
|---|---|---|
| compile main＋test | ✅ | `%TEMP%\verify_p1fix.log`（BUILD SUCCESSFUL） |
| 5 個相關檢查 | ✅ 全綠 | 同上（WorldgenFacts／WorldgenRoutes／AcquireJarRoutes／AcquireFacts／InfoCompleteness 各 OK） |
| 全部 53 個檢查 | ✅ 53/53、RC=0 | `%TEMP%\allset_sweep.log`（`TASK_COUNT=53`、`CHECKS_RC=0`） |
| python 閘 | 124 檔／1 紅（＝基線已知紅 `check_ask_display_leak.py`），**零新增紅** | 同上 |
| 負控 4 條（逐條獨立） | ✅ 全部 紅→（快照逐 byte 還原）→綠 | `%TEMP%\nc_p1fix2.log`：f1b pre=1 post=0／f1 1→0／pollution 1→0／f2 1→0 |
| 污染線紅訊息（示範危害） | `World gen: thermal:silver_ore｜Y level: -60..40 inline_type=minecraft:ore inline_size=8｜…` | NC-pollution 輸出 |

**過程事故（照實報）**：第一版負控腳本寫錯一行（`shutil.copyfile(path, path)` 拋 `SameFileError`）⇒ 四個還原全部**冇執行**，工作樹一度留低半壞狀態。已即時停手，用反向替換補回，四重核實後才繼續：① numstat 逐格對比（AcquireAskTool 23+7=30／WorldgenFacts 17+2=19／JarLightIndex 3+0=3，同事故前一致）② 其他修後改動仍在 ③ 重跑 5 檢查全綠 ④ 三檔 sha 同快照逐 byte 一致（`%TEMP%\p1fix_postfix\sha256.txt`）。第二版腳本改用快照還原（唔再用 `shutil`），重跑 4 條負控全過。

**真機階段（2026-09-20 13:38–13:39 已跑：SK 授權「go」）**
- 部署：`mc_mod_deploy_jar.py --target packai_ftb --jar …/autotest-dev-0.2.3.jar` → backup＋sha 一致（`23843464a35a`）；
- 開窗：`run_hidden.vbs` 開 launcher；**開窗後前景覆核＝無搶焦點**（13:39 前景仍係 Counter-Strike 2／Discord）；
- harness：`packai autotest status=DONE cases=5`；trace 5 個（`ask-20260920-1338*`／`1339*`）。
- **F1b 前後對照（同一物品 `thermal:silver_ore`）**：修前（09:25 trace）`placed_feature thermal:silver_ore count=4 height_range=…`＝**冇 size**；修後（13:38）`World gen: thermal:silver_ore｜Y level: -60..40｜**Vein size: 8**｜Veins per chunk: 4`，答案正文寫「vein size 8, about 4 veins per chunk」（jar 內 `config.size=8` 對得上）。
- **同一題對照**（`occultism:silver_ore`）：修後答案同時列出 Thermal 變體嘅 size（修前完全冇）。
- `thermal:apatite_ore`（size 9）／`thermal:deepslate_tin_ore`（size 9）都出到正確 size；原版控制組 `minecraft:iron_ore` 老實答「no entry — vanilla knowledge」。
- **零內部欄位外洩**（`[WORLDGEN]`／`configured=`／`inline_*`／`L|` 全部冇出現）；丙（`World gen:`／`Y level:`／`Veins per chunk:`）已 live。
- **未做（F2 真機 leg）**：同一 session 設定畫面 toggle「掃描模組 jar」（ON→問→OFF→問→ON→問）——需要 GUI 自動化（設定畫面＋`/ai` command），等 SK 講先做；代碼層已有 NC 紅→綠（`scanModJarsGate`）。

**（原）真機階段（未做，等 SK 安排）**：SK 2026-09-20 澄清——`mc_mod_deploy_jar.py --target packai` 指去 `AI_test_NFWC_DIM` 係**早期留低嘅測試目標**，而家唔再適用 ⇒ **唔准用**，沙盒只准 ad-hoc `--jar/--mods`。代碼層驗收已全綠（見上表）；真機個案表已備（`packai/autotest/cases.json` 5 條：thermal silver／occultism silver／thermal apatite／thermal deepslate tin／原版控制），**未跑**；SK 叫先開。

**（原）真機階段（待做）**：測試 jar 已砌（`autotest-dev-0.2.3.jar` 12:15）；FTB 沙盒 `cases.json` 已釘 5 條（`thermal:silver_ore`／`occultism:silver_ore`／`thermal:apatite_ore`／`thermal:deepslate_tin_ore`／`minecraft:iron_ore`）；發現 `/ai <問題>` client command ⇒ F2 同 session 切換可自動化（唔准改 toml 重開當驗）。


**F2 真機 leg（2026-09-20 14:36-14:46 完成）✅**：同一 session（MC pid 49592，同一世界）、同一物品（`ad_astra:oxygen_tank`）、同一問題，用遊戲內設定畫面 toggle 三次：
- ON（14:41:11）→ trace 有 `Loot table: chests/village/moon/blacksmith`（3 次）＋答案正文寫「Chest loot: village/moon blacksmith chest loot table」
- 設定關（14:42:46；設定畫面 Debug→搜尋「scan」→撳「Scan mod jars」；toml 即時寫 false）→ OFF（14:43:06）→ **`Loot table` 0 次**，答案正文寫「No chest/loot/trade source is indexed」
- 開返（14:44:45；toml 即時寫 true）→ ON（14:45:00）→ `Loot table` **7 次**（資料返嚟，可逆）
⇒ **修正閘在同一 session 遊戲內真機被證實有鑑別力**（唔係靠冷啟動）。
沿途兩個方法論修正（已寫入 skill）：① `computer_use` 嘅 `coordinate=` 係 **window-relative**（唔係工具 summary 講嘅 native desktop）；② 遊戲窗**必須先有真 focus** 先送輸入（`focus_app` 失敗就用 `AttachThreadInput`）；③ **改 toml 檔對 runtime 無效**（Forge 唔 reload 客戶端 config），驗 runtime 切換一定經遊戲內設定畫面。收尾：MC 已關、toml 還原 `true`、焦點交還 Chrome。

## 5. 延後（明文，含理由）
- **F3**（人化雙寫／純 DRY）→ 記 P2（理由見 §0）。
- F2-②（toggle 時 reset）：白名單外＋~358 jar 重掃成本。
- 上游 P1-3 觀測性（`dimensionFilesOverCap()` 冇出口）：test 有斷言但作者／玩家見唔到 → 併入 P2 觀測性組。
- `AskEngine:1721`／`:696` 嘅 gate：**唔做**（理由入 §1 B3）。
- IE（`immersiveengineering:silver`）冇 size／count 嘅來源：未核到 ⇒ 不當證據、不修（但要記：佢係真機第二個缺 size 樣本）。
- **F1 真機不可觀察（明文承認）**：兩個真實共享個案都出唔到玩家層症狀——`rftoolsbase:dimshard_overworld` 嘅 item id 過唔到 `WorldgenFacts` 嘅全 token 匹配規則、`pneumaticcraft:oil_lake` 被礦物過濾濾走 ⇒ **F1 只有 fixture 級證據（合成＋真實 id fixture），唔可以聲稱「修好一個玩家可見 bug」**。
- `feature` 係 array 形態：兩個包都 0 個 ⇒ 唔處理，明文記為已知限制。
- P2 八條（未用參數、位置式模板、硬編碼沙盒路徑、lock 內 I/O、首個 ask 全掃成本等）不動。
- **乙（registry 主來源）**：另開 `docs/plans/2026-09-20-worldgen-registry-source.md`，第一步＝**可行性 probe 實測**（單人／多人讀唔讀到 `PLACED_FEATURE`／`BIOME`）＋覆蓋率量測，量完先決定做唔做。

## 6. Rollback／停工
- 備份：`%TEMP%\p1fix_backup_20260920\`（5 檔＋`sha256_baseline.txt`）；**開工前先補齊**：`WorldgenFacts.java`、`WorldgenIndex.java`、`AcquireJarRoutesCheck.java`、`WorldgenRoutesCheck.java`、`InfoCompletenessCheck.java`、3 個 lang 檔、`tests/check_worldgen_lookup.py`（逐檔 sha256 入 baseline）。
- 同一條連續 3 次失敗 ⇒ 停手報 SK；真機新外洩／崩潰 ⇒ 即刻停、還原、上報。

## 7. 開工門檻（updated）
R1 6:4 → R2 7:3 → R3 6:4（三輪用完，依法停手）。**SK 2026-09-20 決定：剔除 F3、其餘照 R3 修正、甲（F1b＋F1＋F2）／丙 一齊落、乙另開 plan。** R4-lite＝只核 v4→v5 嘅 delta（唔係新完整輪）；通過 → `code_change_log.md` → cursor-agent → Hermes 親驗（§3）→ HANDOFF。
