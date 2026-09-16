# Plan v6 —「腳本產出物品」取得通道（code-first）

> 建立：2026-09-16｜狀態：DRAFT（未 review）｜上一版：`2026-09-16_kubejs-transform-v5.5.md`
> 本版取代 v5.5 嘅範圍定義：**唔再靠 JEI 信息頁（玩家視角）做來源，一律以 KubeJS code 為準。**

## 0. 一句話目標

玩家問「X 點嚟／點取得」時，只要 KubeJS 腳本有寫「產出 X」嘅邏輯（唔係配方、唔係 vanilla 掉落），答案就要講得出——並用**包自己嘅文字**（lang 原文）講，唔准自創講法。

## 1. 實測範圍（code 為準；命令可重跑）

量法：Python 掃 `<instance>/kubejs/{server,startup}_scripts/**/*.js`（排除 `/assets/`、`/data/`；`client_scripts` 只做顯示唔算來源）：
- 抽 handler 註冊位置 ×4 形：物件表 `'ns:id': function(...)`、賦值 `XxxStrategies['ns:id'] = function(...)`、`ItemEvents.rightClicked('ns:id', …)`、`BlockEvents.rightClicked('ns:id', …)`、`EntityEvents.death('ns:id', …)`
- 抽 handler body（quote／comment-aware brace match，上限 6000 字）內嘅字面輸出 sink ×3：`give`（`.give|addItem|insertItem|giveInHand|addToPopulation`）、`loot`（`addLoot|LootEntry|dropItem|spawnItem`）、`setSlot`（`.setStackInSlot(…, Item.of('id'))`）

結果（NFWC_DIM，2026-09-16）：
| 項目 | 數 |
|---|---|
| 會產出物品嘅 handler 站點 | **95**（物件表 52、`EntityEvents.death` 14、`ItemEvents.rightClicked` 16、`BlockEvents.rightClicked` 13） |
| 非 vanilla 產出物品 | **166** |
| 其中「只有腳本邏輯、連信息頁都冇」（A 組） | **15** |
| 「冇配方／戰利品／任務，但有 JEI 信息頁文字」（B 組） | **11** |
| **本單覆蓋目標 = A＋B** | **26 件 / 約 10 個 handler 站點** |
| 換物品（`setStackInSlot` 換另一個 id） | 2（神恩项链→满溢神恩项链；誓约之戒→银戒） |

A 組（code 出處）：`b_a_d_rclick.js:73`（蜂蜜酒／米酒／伏特加／鸡蛋格罗格）、`b_a_d_player_damage.js:2267`（切片肉排／眼球）、`golden_age/dlc_template_rclick.js:112`（projecte 5 件）、`b_a_d_item.js:493`、`mob_effect.js:731`、`events.js:1014`、`ino_dlc_build_block_broken.js:1`。
B 組：`momo_dlc/entity/momo_dlc_entity_death.js`（`dream_*` 8 件）＋ `b_a_d_item.js:876` 等。

Inventory 檔（唔入 repo）：`%TEMP%\nfwc_obtain_inventory.json`、`%TEMP%\nfwc_pure_script_gap.json`。

**今日點解答唔到**：取得答案嘅 assembling 有封閉 prefix 白名單（`PackIndex.acquireFactsDetailed` `:1265-1380`：fish／loot／trade／quest_submit／quest_obtain／recipe_needs／removed／right_click\*）；「腳本產出」唔在其中。46 條真機 trace 內 `-[on:` 類 fact 出現 **0 次**。

## 2. 設計（最小改動；位置全部實查過）

1. **掃描 pass（一定要 PackIndex，唔可以放 KubeJsMechanicScan）**
   `PackIndex.build` walk（`:206-246`，`:235-238` 逐檔）→ 加喺 `indexScriptItems(String rel)`（`:1568-1606`）末尾。該方法已收 `rel`、已 `readText`（零額外 IO）、無檔案數上限。
   唔可以放 `KubeJsMechanicScan`：實測其 cache 只 400 檔（`DEFAULT_SCAN_MAX_FILES=400`，`KubeJsMechanicScan.java:89`），本包 648 檔、其中 `startup_scripts/**` 0 檔入選。
   Gate：`rel.startsWith("kubejs/") && rel.endsWith(".js")`，且唔准含 `/assets/`、`/data/`。
2. **Parser（重用，唔另寫一套）**：新 `logic/JsObtainSites.java`
   - `record Site(String outId, String triggerId, String mech, String rel, int line)`
   - `static List<Site> parse(String text, String rel)`（純函數，fixture 可測）
   - 4 個註冊 pattern ＋ 3 個 sink pattern（見 §1 量法）；key 一律要求 `ns:path` 形（唔合格丟棄，天然擋 `{'step_1': …}` 假陽明）
   - 唔要「map literal 最外層」推導：改為「同一 handler body 內嘅字面 sink」——比 v5.5 簡單，且已覆蓋 `dlc_template_rclick.js:112` 一個 handler 多產出嘅情況
   - 已知盲點（寫入 Javadoc）：變數 id（`Item.of(newVar)`）、動態註冊、body >6000 字
3. **索引**：`PackIndex` 加 `private final Map<String,List<Site>> jsObtainByOutput`（key = 小寫 outId）；`build()` 清理區（`:160-172`）clear；**唔准**喺 `beginAskSession()`（`:386-389`）clear。
4. **ask 期注入（繞 MAX_GRAPH）**：新 `private void injectFocusJsObtainEdges(String id)`，叫喺 `acquireFactsDetailed` 內 `:1257 ensureFocusQuestAcquireEdges(...)` 之後、`:1259` ranked 建構之前：
   `addFactForced("item:" + id + " -[js_produce]-> via:" + mech + " from:" + triggerId + " src:" + rel + ":" + line)`
   （house 格式對齊 `KubeJsMechanicScan.sourceTag`；`addFactForced` 見 `:2549`）
5. **取得 branch**：喺 `:1265-1380` 白名單加一條，插喺 `right_click_as_block`（`:1378`）之後：
   `} else if (f.startsWith(prefix + " -[js_produce]-> ")) { ranked.add(new RankedAcquire(2, seq++, ReplyLang.jsProduce(lang, name, trigger, src))); }`
   band = **2**（同 interact 同級；`:1398` band legend 補一行）。注意 cap 在 `:1266`、sort 在 `:1388` → band 只影響已入選 12 條。
6. **擠走可觀察（S9）**：`:1266` 觸發 break 時，若 graphFacts 仍有 focus 嘅 `-[js_produce]->` 未入 ranked → 一次性 `PackAiMod.LOGGER.info("Pack AI acquire cap hit focus={} dropped_edge={}")`。
7. **`AskEngine.graphLines` 唔加 branch**（會係死碼）：`retrieve()`（`AskEngine.java:270`）先拍 `RetrieveResult` 快照（`PackIndex:563`→`:570`），`acquireFactsDetailed`（`AskEngine.java:287`）之後才跑 → 注入嘅 fact 永遠唔喺 `retrieved.graphFacts()`。要 mirror 嘅先例係 **quest_obtain**（focus-pin → 只喺 PackIndex render），唔係 loot。
8. **文字 label**：`ReplyLang` 加 `public static String jsProduce(String code, String triggerName, String triggerId, String src)`，key `packai.reply.js_produce`；3 檔 forge lang（`en_us`／`zh_cn`／`zh_tw`）都要（`check_internal_label_parity` 範圍外，但 AGENTS 要求 3 檔齊）。neoforge **唔 mirror**（PAUSED）。
9. **措辭一律用包原文（R-PHRASE 規則 1）**：新增 `PackIndex.packLangText(String key)`，讀 `:151 translations`（`:275-300 loadLangFile` 疊入 `kubejs/assets/**/lang/*.json`；`:307` 已有先例）→ 俾 `jsProduce` 用文字（例：`kubejs.jei.ore_lung.1`＝手持肺脏或者动物肺脏右键沙子获得）。**唔准**喺 logic 層用 `I18n`（headless harness 會爆）。解唔到 → 走機械式並標 `lang_fallback`，**唔准自創動詞**。
10. **開關（TOML-only，唔入設定 UI，同 `kubejsMechanicScan` 一致）**：`PackAiConfig` 加 `JS_OBTAIN_CHANNEL`（`define("jsObtainChannel", true)`，getter 照 `:907-913`）＋ `JS_OBTAIN_DIAG_LOG`（`define("jsObtainDiagLog", false)`）。kill-switch 要包住 **index 建立 ＋ branch ＋ 注入** 三處（唔止 parser）。

## 3. 交付拆 3 張單（SK 09-16 決定）

- **單 1（本 plan）**：腳本產出通道（§2 全部）→ 覆蓋 26 件。
- **單 2**：包文字通道收尾 —— (a) `KubeJsMechanicScan.factsFrom`（`:924-965`）note 只保留 `kubejs.tooltips.<focusPath>.` 開頭 key（**唔准**改 `parseHandlers`／cache，cache key = rel+content hash）；(b) `AskService.appendMechanicBehavior`（`:726-780`）用 `I18n.exists/I18n.get` 解析 note 文字（先例 `ModularToolScan.i18nRaw :268-280`），解析唔到就丟。真機實錘：滿項鍊 fact 掛咗 `active_pill.1/.2`。
- **單 3**：plan 文本修正（R7/cursor review 嘅 FC1–FC5）：S 表假紅、`src:` 格式、`115 check → 114/1`、895/649 殘留、雙 §3 編號、§5「純新增」vs §4「skip+log」矛盾。

## 4. 驗收（一律 code 掃描 + 真機答案；**唔用截圖／JEI 文字做驗收**）

| # | 比對 artefact | 今日 | 實作後點證 |
|---|---|---|---|
| S1 | `jsObtainByOutput` site 清單（harness 讀真 code，唔准 fixture reimplement） | 紅（無索引） | `runAcquireFactsCheck` 擴充：fixture 貼真 snippet（`b_a_d_rclick.js:73` 一 handler 多產出＋`dlc_template_rclick.js:112`＋`entity_death.js:22-26`）→ assert site 數／src 行號 |
| S2 | 26 件 A/B 組每件 `acquireFactsFor(id)` 非空 | 紅（全部空） | `AcquireFactsCheck` 加 26 件 id 清單（由 `nfwc_obtain_inventory.json` 生成 fixture）逐件 assert 有取得行 |
| S3 | 真機 trace `tool.result name=acquire` 逐字含新 label | 紅（旗艦 acquire = `""`） | 真機問 3 件（`b_a_d:vodka`、`momo_dlc:dream_heart`、`kubejs:god_bless_full_necklace`）→ grep `packai/trace/ask-*.jsonl` |
| S4 | 負對照：冇腳本產出嘅物品唔可以無中生有 | — | `AcquireFactsCheck` fixture：`minecraft:stone` ／只出現喺註解／字串嘅 id → assert 0 site |
| S5 | 註冊形覆蓋：4 形都出 site | 紅 | fixture 4 形各 1 例（含 `XxxStrategies['x'] = function`） |
| S6 | 真相表（code grep 可重跑） | — | 命令寫入 plan §1；assert inventory 數（95 handlers／166 items／26 gap） |
| S7 | 擠走：≥12 條更早 edge 嘅物品 | — | `runAcquireFactsCheck` 抄既有 cap fixture（`AcquireFactsCheck` 220 條 filler）→ assert 有行 **或** 有 cap log |
| S8 | 文字：取得行必須用包原文（非自創動詞） | — | fixture `kubejs.jei.*` key → assert 出中文原文；未知 key → 出 `lang_fallback` |
| S9 | 迴歸：`jsObtainChannel=false` 時 0 條新行、其他取得行不變 | — | 同 harness 跑兩次（開／關）比對 |

入口：`cd forge/1.19.2 && ./gradlew.bat -I tmp-check.gradle runAcquireFactsCheck -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"`（今日 11s OK）。

## 5. 回滾

純新增 + 一個 TOML 開關；`git revert` 單 commit；索引係 in-memory（無新 cache 檔、無 schema bump）→ 回到今日行為。實作前 `git status` 要乾淨（現有 α 批未 commit 改動唔關本單）。

## 6. 未知／盲點（誠實列）

1. 26 件以外嘅 140 件產出物品，靠 recipe／loot／quest 覆蓋——**冇逐件真機驗過**，只係 code 掃描分級（B 級）。
2. A 組/B 組分類用「檔案路徑含 recipe」做 recipe proxy（B/C 級）＋ ftbquests 全文提及做 quest proxy → 可能高估覆蓋。
3. body >6000 字元嘅 handler、變數 id、動態註冊未掃 → 實際站點可能多過 95。
4. JEI 信息頁文字（`event.addItem` 223 條／`kubejs.jei.*` 70 條 lang）今日 trace 內 **raw key 0 次、中文 0 次** → 呢條路實際喺 runtime 通唔通，只有真機測得到（唔可以當已覆蓋）。
5. `I18n.get("kubejs.tooltips.*")` 解唔解到 → 真機（單 2 依賴）。
6. 26 件入面有幾件玩家真會問 → 未量（要真機 log）。

## 7. Review 狀態（必讀）

- v5.5：R1 2:8 → R2 5:5 → R3 6:4 → R4 6:4 →（換 API 路線）R5 反方 4:6／正方 8:2 → 裁判 7:3 → R6 5:5 → v5.5。
- v5.5 R7 supporting（Hermes）6:4；cursor 反方對 supporting 4:6；硬上限已到 → SK 09-16 決定：範圍改 26 件 code-first（本版），拆 3 張單。
- 本版要過 **≥8:2** 才開工；未過就停手問 SK（唔准無限 review）。
