# Plan v6.1 — 腳本產出通道（**code-only 為唯一真相**）

> 建立 2026-09-16｜v6 → v6.1（收 SK「全部資訊都在 code」＋cursor 反方 R1 3:7 嘅 FC1–FC5）｜狀態：待 R2 review
> 前版：`2026-09-16_kubejs-transform-v5.5.md`（已取代）；反方報告：`reviews/2026-09-16_plan-v6-obtain-code-channel-R1-opposing.txt`
> 上一版：`2026-09-16_kubejs-obtain-code-channel-v6.md`

## 0. 目標（一句話）

玩家問「X 點嚟／點取得」時，只要 KubeJS **code** 有寫「產出 X」嘅邏輯，答案就要講得出嗰條路（觸發＋條件＋產出）——**fact 一律由 code 讀出**；玩家名用官方顯示名，唔准自創動詞。

**新規則（SK 2026-09-16）**：**code 係唯一來源**。JEI 信息頁／tooltip 文案＝玩家視角，只可以做「顯示用補充」，**唔准當 fact 來源或覆蓋判準**。

## 1. 範圍（code 掃描；命令可重跑）

掃 `<instance>/kubejs/{server,startup}_scripts/**/*.js`（**明文排除 `/client_scripts/`、`/assets/`、`/data/`**；`PackIndex.isScriptPath` `:2571` 係任何 `.js/.zs/.groovy` → gate 必須自己收窄）。

量法：handler 註冊位（物件表 `'ns:id': function`／賦值 `Xxx['ns:id'] = function`／`ItemEvents.*`／`BlockEvents.*`／`EntityEvents.*`）× handler body 內字面輸出 sink（`give`／`loot`／`setSlot`；regex 要 word-boundary，`\\.(give|addItem|insertItem)\\b` —— **唔可以裸 `addItem`**，實測 `player.addItemCooldown('…')` 喺 `b_a_d_alice_in_hell.js:497`、`b_a_d_constdef.js:572/580/587/593` 共 5 處會假陽）。

實測（NFWC_DIM 2026-09-16；inventory：`%TEMP%\nfwc_obtain_inventory.json`）：
| 項目 | 數 |
|---|---|
| 產出物品嘅 handler 站點 | **95**（物件表 52／`ItemEvents.rightClicked` 16／`EntityEvents.death` 14／`BlockEvents.rightClicked` 13） |
| 非 vanilla 產出物品 | **166** ← **本單覆蓋全集** |
| 冇 recipe／loot／quest 嘅（純腳本） | **15**（清單 `%TEMP%\nfwc_gap_A.json`） |
| 另有 JEI 信息文案、但一樣冇 recipe／loot／quest | **11**（清單 `%TEMP%\nfwc_gap_B.json`；`momo_dlc:dream_*` **10 件**，非 8） |
| 換物品（`setStackInSlot` 換另一個 id） | 2（神恩项链→满溢神恩项链、誓约之戒→银戒） |

**覆蓋對象 = 166 件全部**（唔再以「有冇 JEI 文案」做界；A/B 只做**驗收優先序**：A 15 件最硬，必須逐件出答案）。
A 組 15 件 id（可審計）：`b_a_d:egg_grog`／`b_a_d:mead`／`b_a_d:rice_wine`／`b_a_d:vodka`（`b_a_d_rclick.js:73`）、`b_a_d:sliced_meat`／`b_a_d:eyeball_sacrifice`（`b_a_d_player_damage.js:2267`）、`b_a_d:picture`（`effect/mob_effect.js:731`）、`alexsmobs:shrimp_fried_rice`（`server_scripts/b_a_d/item/b_a_d_item.js:493`）、`golden_age:believe`（`golden_age/events.js:1014`）、`ino_dlc_build:pandora_box_blue`（`ino_dlc_build_block_broken.js:1`）、`projecte:aeternalis_fuel`／`alchemical_coal`／`dark_matter`／`mobius_fuel`／`red_matter`（`golden_age/dlc_template_rclick.js:112`）。
B 組 11 件：`b_a_d:shattered_bubble`（`b_a_d_item.js:876`）＋ `momo_dlc:dream_*` 10 件（`momo_dlc/entity/momo_dlc_entity_death.js`）。

**今日點解答唔到**：取得答案 assembling 係封閉 prefix 白名單（`PackIndex.acquireFactsDetailed` `:1265-1380`），腳本產出唔在其中；46 條真機 trace 內 `-[on:` 類 fact 出現 0 次。

## 2. 設計（最小改動；位置全部實查）

1. **掃描 pass = PackIndex**（唔可以放 `KubeJsMechanicScan`：`DEFAULT_SCAN_MAX_FILES=400` `:89`，本包 648 檔、`startup_scripts` 0 檔入選）。
   位置：`PackIndex.build` walk `:206-246`（`:235-238`）→ `indexScriptItems(rel)` `:1568-1606` 末尾（已收 rel、已 readText，零額外 IO）。
   **Gate 必須明文**：`rel.startsWith("kubejs/")` ＋ `endsWith(".js")` ＋ 排除 `/client_scripts/`、`/assets/`、`/data/`。
2. **Parser**：新 `logic/JsObtainSites.java`
   - `record Site(String outId, String triggerId, String mech, String rel, int line, String cond)`
   - `static List<Site> parse(String text, String rel)`（純函數；fixture 可測；key 一律要 `ns:path` 形）
   - 4 註冊形 × 3 sink（§1 量法；word-boundary）；body quote/comment-aware brace match，上限 6000 字（寫入 Javadoc 做已知盲點）
   - `cond`＝同 handler body 內可見條件字面（如 `%`、`概率`、`player.age % N`、tag 檢查）→ **只抽字面，唔解釋**（唔准自創「擊殺」「類 boss」等動詞）
3. **索引**：`PackIndex` 加 `Map<String,List<Site>> jsObtainByOutput`（key 小寫 outId）；`build()` 清理區 `:160-172` clear；**唔准**喺 `beginAskSession()` `:386-389` clear。
4. **ask 期注入＋可見性（修正 v6 最嚴重設計洞）**
   - v6 錯：`addFactForced`（`:2549`）係 append 去 graphFacts 尾，而 cap 檢查 `:1266` 在 sort `:1388` **之前** → busy focus 時新 edge **系統性最後被切**，S9 log-only = 靜默失敗。
   - v6.1 定案：**新 edge 直接入 `ranked`（強制 admission）**，唔靠 append：於 `acquireFactsDetailed` 內 `:1259` ranked 建構之後、`:1265` 白名單迴圈之前，插一個 pre-pass：for each site of focus → `ranked.add(new RankedAcquire(2, seq++, ReplyLang.jsProduce(...)))`；同時唔將同 focus 嘅 `-[js_produce]->` 交俾 graphFacts 迴圈（避免重複）。
   - band = **2**（同 interact 同級；`:1398` legend 補 `js_produce`）。cap 12（`:1266`）**唔准**把 js_produce 計入被切：pre-pass 先入 ranked，cap 只作用於 graphFacts 迴圈部分 → busy focus 都睇得到。
5. **取得 branch（若行 graphFacts 路線仍要）**：喺 `:1378` `right_click_as_block` 之後加 `} else if (f.startsWith(prefix + " -[js_produce]-> ")) { … }`（band 2）。呢條係保險，唔係主路。
6. **`AskEngine.graphLines` 唔加 branch**（死碼）：`retrieve()`（`AskEngine.java:270`）先拍 `RetrieveResult`（`PackIndex:563→:570`），`acquireFactsDetailed`（`AskEngine.java:287`）之後才跑 → 注入 fact 永遠唔喺 `retrieved.graphFacts()`。mirror 先例＝quest_obtain。
7. **文字（修正 FC2：唔再當 lang 係來源）**
   - fact 文字由 code 出：`ReplyLang.jsProduce(String code, String triggerName, String triggerId, String src, String cond)`（**簽名全文統一**；v6 兩處簽名唔一致係錯）→ 機械式模板：`【腳本產出】<outDisplayName>（<outId>）：<cond?> 觸發 <triggerDisplayName>（<triggerId>）→ 產出 ｜ src:<rel>:<line>`。
   - 玩家名用既有官方顯示名機制（`OfficialDisplay`）；**唔准**意譯 id。
   - 包文案（`kubejs.jei.*`／`*.tooltips.*`）**只可作顯示補充**（若存在且同 code 路徑一致，可 append「包說明：<原文>」），**唔可以當 fact 或覆蓋判準**；A 組 15 件實測多數只有 `item.<ns>.<path>` 顯示名，冇取得文案 → 唔影響本設計。
8. **開關（TOML-only，同 `kubejsMechanicScan` 一致）**：`PackAiConfig` 加 `JS_OBTAIN_CHANNEL`（`define("jsObtainChannel", true)`；getter 照 `:907-913`）＋ `JS_OBTAIN_DIAG_LOG`（`define("jsObtainDiagLog", false)`）。kill-switch 要包 **index 建立 ＋ pre-pass ＋ branch** 三處。
9. **`jsProduce` 顯示上限**：每 focus 最多 3 條（band 2 內按 seq），避免洗版；寫入 `AskToolContext` 常數（照 `MAX_ACQUIRE_LINES_*` 風格）。

## 3. 交付拆 3 張單（SK 決定；cursor FC1/FC4 後修訂）

- **單 1（本 plan）**：腳本產出通道（code-only）→ 覆蓋 166 件（驗收優先 A 15 件）。
- **單 2**：包文字通道 —— `KubeJsMechanicScan.factsFrom`（`:924-965`）note 只保留 focus 自己嘅 key（**唔准**改 `parseHandlers`／cache，cache key＝rel+content hash）＋ `AskService.appendMechanicBehavior`（`:726-780`）文字解析（先例 `ModularToolScan.i18nRaw :268-280`）。真機實錘：滿項鍊 fact 掛咗 `active_pill.1/.2`。
  **B 組（含 `dream_*` 10 件）唔歸單 1**：佢哋已有 JEI 取得文案（`momo_dlc_jei.js:36-38`「携带T-02-99击杀凋灵骷髅1%概率获得」）→ 正確層係 JEI 信息通道（單 2／`JeiInfoFacts`），避免兩條通道措辭分裂。
- **單 3**：plan 文本修正（FC5 尾＋v5.5 遺留）：`b_a_d_item.js` 真路徑係 `server_scripts/b_a_d/item/b_a_d_item.js`；`115 check` → 實測 114 PASS／1 FAIL；895／649 殘留；雙 §3 編號；§5「純新增」措辭（本單會改 `acquireFactsDetailed` 組裝核心 → 唔算純新增，靠開關＋revert）。

## 4. 驗收（**只認 code 掃描＋真機答案**；唔准用截圖／JEI 文案做驗收）

| # | artefact 比對 | 今日 | 實作後點證 |
|---|---|---|---|
| S1 | `jsObtainByOutput` site（harness 讀**真 code**，唔准 fixture reimplement） | 紅（無索引） | `runAcquireFactsCheck` 擴充：fixture 用真 snippet（`b_a_d_rclick.js:73` 一 handler 多產出／`dlc_template_rclick.js:112`／`entity_death.js:22-26`）→ assert site 數＋`src:` 行號 |
| S2 | **每個 id 必須出 `-[js_produce]->`／新 label**（唔准只 assert「acquire 非空」＝假綠） | 紅 | A 組 15 件逐件 assert 新 prefix；負控：`minecraft:stone`／只喺註解出現嘅 id → 0 site |
| S3 | 真機 trace `tool.result name=acquire` 逐字含新 label | 紅（旗艦 acquire = `""`） | 真機問 3 件（`b_a_d:vodka`、`kubejs:god_bless_full_necklace`、`alchemical_coal` 類）→ grep `packai/trace/ask-*.jsonl` |
| S4 | 4 註冊形各出 1 site（含 `Xxx['ns:id'] = function` 賦值形） | 紅 | harness fixture 4 形 |
| S5 | 真相表可重跑 | — | §1 命令；inventory + `nfwc_gap_A.json`／`nfwc_gap_B.json` 三個檔數一致（95／166／15／11） |
| S6 | **busy focus 都要見到**（v6 致命洞） | — | harness：cap fixture（≥12 條更早 edge）＋同 id 有 js site → assert 仍出現（pre-pass 生效）；負控：開關關 → 0 條 |
| S7 | 文字：機械式＋官方名；出現包說明時必須係原文 | — | fixture 比對字串；assert 無自創動詞（白名單） |
| S8 | `addItemCooldown` 唔可以產生 site | 紅（若用裸 `addItem` regex） | fixture 貼 `b_a_d_constdef.js:572` 真句 → 0 site |
| S9 | 迴歸：`jsObtainChannel=false` → 0 條新行、其他取得行 byte-identical | — | harness 跑兩次比對 |

入口：`cd forge/1.19.2 && ./gradlew.bat -I tmp-check.gradle runAcquireFactsCheck -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"`（今日 11s OK）；另加 `runAskMechanicFactsCheck`。

## 5. 回滾

純新增 class ＋ `PackAiConfig` 兩個定義 ＋ `acquireFactsDetailed` 一個 pre-pass ＋ `ReplyLang` 一個方法 ＋ 3 檔 lang。索引 in-memory（無 cache 檔、無 schema bump）。`git revert` 單 commit 可回今日；開關可即時失效。**注意**：唔可以宣稱「純新增」（會改組裝核心）。

## 6. 未知／盲點（誠實列）

1. 166 件係 code 掃描結果（A 級可重跑），但盲點：變數 id、動態註冊、body >6000 字 → 實際可能多過 166。
2. `%`／條件字面抽取只做到字面，唔會解釋 tag／NBT 條件 → 答案會講「條件：<原文字面>」，唔會翻譯成玩家語言。
3. busy focus 真實分佈未量（要真機 trace 睇 cap 命中率）。
4. JEI 信息通道（`JeiInfoFacts`／`via:jei_info`）今日 trace 0 次係「未證通」，唔等於「已覆蓋」→ 單 2 要真機驗。
5. `I18n.get("kubejs.tooltips.*")` 解唔解到（單 2 依賴）→ 真機。
6. A 組 15 件玩家實際會唔會問 → 未量。

## 7. Review 狀態

- v5.5：R1 2:8 → R2 5:5 → R3 6:4 → R4 6:4 →（換 API 路線）R5 4:6／正方 8:2 → 裁判 7:3 → R6 5:5 → R7 supporting 6:4、cursor 反方 4:6 → 硬上限 → SK 定案改 code-first ＋拆 3 單。
- **v6 R1（cursor 反方）：正方 3 : 反方 7，唔開工**；5 條 FC 已全部收入本 v6.1：FC1（範圍改 166／A-B 清單可重跑）、FC2（文字演算法寫死＋唔再吹「包原文覆蓋」）、FC3（強制入 ranked，唔靠 append＋cap）、FC4（S2 改 assert 新 prefix）、FC5（word-boundary／gate 排除 client_scripts／修路徑／統一簽名）。
- 本輪（v6.1）要再過一次反方；目標 正方 ≥8:2 才開工。未過 → 停手問 SK（唔准無限循環）。
