# Plan β — KubeJS 腳本轉換途徑（v4.1：吸收 R1 反方 2:8／3:7 逐條修正）

> v4 已過 R1（兩個反方 **正方 2 : 反方 8**、**正方 3 : 反方 7**）→ 未達 8:2。本檔係吸收修正後嘅 v4.1（R1 為第 1 輪；本輪 review ＝ 第 2 輪）。
> **推翻 v4 §2 我嘅講法**：新 edge **需要** answer layer 路由（`AskEngine.java:472-504` 冇 branch 命中會直接丟）；`ReplyLang.kindLabel`（`:1181-1203`）唯一呼叫點係 `PackIndex.java:2249`（**ingest 期**），唔係顯示期 fallback。**唔可以再聲稱「零 answer layer 改動」**。

## 1. 抽取（取代 v4 §3 兩個 pattern）
**P1（檔案層 map 變身）**：新增**獨立**函式 `ingestTransformEdges(rel, text)`——**嚴禁**重用 `ingestAcquireEdges`（`PackIndex.java:2248-2260` 會用 `ITEM` regex 掃全檔、為**每個** id 出同一 label → 會令被消耗嘅 key 出反方向取得 edge：`entity_death.js` 會同時出 empty 與 full；`entity_hurt.js` 會令 `kubejs:friend_to_the_end` 變可取得）。
- 只對「key=來源 id、body 內 `setStackInSlot(<任何>, Item.of('<out>'))`」抽出嘅 **`<out>`** emit；key≠out。
- edge **保持機器可讀**（唔准烘 zh_tw prose）：`item:<out> -[transform]-> from:<srcId> src:<rel>:<line>`，ask 時按 `replyLang` 渲染（否則 zh_cn 場會見到 zh_tw 文字）。
- 兩個真 site 都係**有條件**機制（`entity_death.js` 需 boss 類型清單；`entity_hurt.js:31/36` 需 NBT friendName＋好友在線＋血量 ≤4 救援傳送）→ 渲染文字**必須**加「腳本事件轉換（有條件，見腳本）」字樣，唔准當普通取得途徑。

**P2（鏈式祭壇產出）**：`.itemOutput('ns:id')` —— **裸字串**（真旗艦 `server_scripts/ritual/summoning_rituals.js:439`；該檔 8/8 個 itemOutput 全裸字串，全檔只 1 次 `Item.of`）；v4 §3 寫 `Item.of('out')` 會令 S1 必 FAIL。
- chain 單位＝接收者運算式到 `;`（容許註解行）；**必須 chain 隔離**：同檔 29 個 `.altar(` vs 8 個 `.itemOutput(` → file-wide 實作會造 ~8×29 假配對。
- 同鏈 `.altar(X)`／`.input(...)` → **CONSUMES，永不出取得 edge**。
- `.input('16x id')` ／ `.input('8x #tag')` → 剝計數前綴；`#tag` 一律丟。
- `.sacrifice(` 7 次／`.mobOutput(` 21 次（實體 id，符合 ITEM regex）→ **零 item edge**。

**丟棄規則**：`Item.of('')`、`${…}` 動態 id（`chestcavity:${score}`）、`Item.of(mainitem.id)`／`Item.of(itemId)`（動態）、`new ItemStack(key,max)`（`startup_scripts/dlc/dlc_common.js:61/67`）、NBT 內 `ns:path`（`goety_ritual.js:142` 只准出 `gateways:gate_pearl`，唔准出 `kubejs:b_a_d/apocalypse`）、`Item.of('id','{NBT}')` 第 2 參數、註解內 id（`constdef.js:60`）。

## 2. 整合（必要，唔可以聲稱零改動）
1. `PackIndex.java:1265-1379` rank 迴圈：新增 `-[transform]->`／`-[ritual]->` 分支（否則永久 fall-through 丟棄）；同步更新 `:1197-1200` AcquireFacts javadoc。
2. `PackIndex.java:1388` 附近：指定 RankedAcquire **band**（建議 band 2，與 interact 同級、高於 generic loot band 1／script band 4）；注意 `AskToolContext:33 MAX_ACQUIRE_LINES_SLIM=3`（header 恆佔 1 行 → 實際只剩 2 行）。
3. `AskEngine.java:472-504`（graphLines 過濾）＋（如走 PURPOSE 通道）`AskPurposeContext:48-61 isPurposeGraphFact`：加同一對 label。
4. `PackIndex.java:1685-1696`（`ingestGraph` 內 `isScriptPath` 段）呼叫新函式；`indexScriptItems`（`:1568-1600` 或 `:233-237` 分派）令含 P1/P2 嘅檔入 `acquirePathsByItem[outId]`（比照 `:352`／`:1555`／`pinLootJsAcquirePath`），並處理 `:1232` 10-rel 上限與 retrieve `:510` 讀 40 檔上限對旗艦個案嘅截斷。
5. `MAX_GRAPH=200`（`:28`）＋`addFact` 滿額靜默丟（`:2541-2546`）→ 新 edge 用 `addFactForced`（`:2549`）或先於其他 ingest 執行。
6. **Kill-switch**：`PackAiConfig` 加 toggle（照 `KUBEJS_MECHANIC_SCAN`／`QUEST_MECHANIC_FACTS` 形式）＋3 檔 lang ＋ `tests/check_settings_registry.py` 綠。
7. **Cache 自動失效**：`KubeJsMechanicScan.java:1093` cache key 加 `PARSER_VERSION` salt（`sha256(rel+"\0"+src)` → 版本化）；手動刪 `config/packai/mechanic-cache/` 保留做保險。

## 3. 驗收（可證偽；S2 要靠「naive parser 對照」令紅可觀察）
- **S1 harness（Java，真 snippet fixture）**：P1 兩個真 site 出正確 edge（方向／`src:rel:line`）；P2 出 `-[ritual]->` 而 `.altar(X)` 零取得 edge；`entity_hurt.js` 唔准令 `kubejs:friend_to_the_end` 變可取得。
- **S2(真值閘)**：直接餵 parser，斷言 **0 edge** —— `constdef.js` 三個「同 shape 非變換」項（**注意 `:253 machineChestLootTable` 係陣列，唔係 object literal**）＋約 150 個 `'ns:id': function(event, organ){…}` map（`b_a_d_player_damage.js:10`、`b_a_d_key_bind.js:10/78/91`、`b_a_d_food_eaten.js:10/33`）。
- **S2(反例紅→綠)**：harness 內**先放一個故意寬鬆嘅 naive parser**（例：`Item\.of\(\s*'([^']*)'` ＋整條 chain 掃全部 quoted id）令 fixture 真係出 edge（見紅），再對真 parser 斷言 0 —— 否則「今日已係綠」係 green-by-absence，唔算證明。
- **S2 新增 5 條**（全部真檔）：(a) chain 隔離（`summoning_rituals.js` 29×8 假配對）(b) `.sacrifice(`／`.mobOutput(` 實體 id → 零 item edge (c) NBT 內 `ns:path` (d) `new ItemStack(key,max)` (e) 動態／計數形（`Item.of(mainitem.id)`、`.input('16x createaddition:gold_spool')`）。
- **S2 收窄**：原本「`event.addItem`／`tooltip`／`scene.world.setBlock` → 零 fact」要改成「**零 transform／零 ritual edge**」——因為 `INTERACT_GIVE`（`PackIndex:81-84`→`:2124`）**現行已經**為 `event.addItem('id')` 出取得 fact，寫「零 fact」會永久紅。
- **S2(⑨ mutation)**：刪 `PackIndex` 內 `.altar(`（或 `Item.of('literal')`）分支 → **具名 test 必須轉紅**（唔可以只靠 table-driven 自證）。
- **S3 真機（改寫）**：喺 trace `send.facts` 斷言含 `-[transform]->`＋`kubejs:god_bless_empty_necklace`＋`curios/entity_death.js:26`；**刪**「由神恩項鍊（空）…」措辭——真名係「神恩项链」（zh_cn.json:445），全 pack 冇「（空）」，而玩家可見文字會被 `Plainify.humanizeText`（`:265-266`）humanize 成「整合包脚本:26」→ **證據只喺 trace／LLM-facts 層可驗**，唔可以當玩家可見。
- **S3 負對照修正**：「問一件普通物品 → edge 數 0」唔成立（`irons_spellbooks:silver_ring` 喺 `entity_hurt.js:36` 真有 site）→ 改指定真冇 site 嘅 id，並要**定義可觀察方式**（加 `Pack AI transform edges item=… n=…` log 或掃 trace）。
- **S4/S5**：新閘要版本 scope（舊 trace 免假紅），並要**正向＋負向對照對**（照 `tests/check_kubejs_universal_scan.py:129-137/160/174`）；現有 115 check 全綠；`check_mechanic_facts.py` 只係 string assert，**唔可以**當抽取正確性證明。

## 4. 還原（要寫齊）
- `git revert` 單一 commit（含 harness＋gate）；**jar 還原**：由 `%TEMP%\deploy_backup_*\` 或用 `mc_mod_deploy_jar.py` 重新部署＋核 sha256（要關遊戲）。
- **明寫有冇 bump `INDEX_SCHEMA_VERSION`（`KubeJsMechanicScan:96`）**：forward 有 bump → revert 亦要再 bump，否則舊 `index.json` 被重用；另外必刪／版本化 `kjs-*.json` parse cache（`:1093/:1109-1135`）。
- revert 後要**重跑 S4 閘（舊 code 上必須紅）**＋115 check 全綠。

## 5. 最壞情況（更新）
- 最壞 A：**pattern 真命中但語意誤導**（兩個 site 都係有條件事件）→ edge 文字加「有條件」註記＋S3 只斷言 trace 層。
- 最壞 B：方向反轉（被消耗 id 被當可取得）→ 由「獨立函式、只 emit `<out>`」＋新負對照守住。

## 6. 交付
- Code 經 **cursor-agent**（唔准 commit／唔准動其他未 commit 檔／唔准 hot-copy jar）；Hermes 親驗 compile／harness／115 check／負對照／diff。
