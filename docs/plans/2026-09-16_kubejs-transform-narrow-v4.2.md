# Plan β — KubeJS 腳本轉換途徑（v4.2：吸收 R2 反方 15 條本地修正；核心設計不變）

> 輪次：R1（2:8／3:7）→ v4.1 → **R2 正方 5 : 反方 5（未達 8:2）** → 本檔 v4.2（只做本地修正，唔改核心設計）→ 待 R3。
> 本檔所有數字＝**我（Hermes）自己實測**；R2 反方嘅每個 load-bearing 指控我都親核（見 §7），其中 2 個數字錯、1 個漏 site 已更正。

## 0. R2 15 條 → v4.2 落點（對照表）

| # | R2 要求 | v4.2 落點 |
|---|---|---|
| 1 | 寫死「事實通道」二揀一 | §2.1：**走 PackIndex ask 期 pin 通道**（唔走 KubeJsMechanicScan），S3 亦照呢個通道斷言 |
| 2 | S3 斷言修正（raw marker 會被消滅） | §2.3：S3 只斷言 trace `send.facts` 內嘅 raw marker（`AskTrace.java:749` 原樣寫入）＋ rank 12 內；**唔**聲稱玩家可見 |
| 3 | §2.4 hook 改正 | §2.4：**唔改** `indexScriptItems`（`:1568-1600` 只寫 `inverted`，親核 ✓）；pin 落 `PackIndex.java:1231-1240`（同 `pinFocusLootJsFacts` 隔離） |
| 4 | `addFactForced` 洪泛 | §2.5：只用「focus-scoped pin」；唔喺 `ingestGraph` 內對所有讀到嘅 script 出 edge |
| 5 | 漏 `MAX_RETRIEVE_FACTS=24` | §2.6：明寫**唔依賴** overflow/graphLines 通道；加斷言 |
| 6 | band 講反 | §2.2：band = **1**（同 loot 同級；`:1388` `comparingInt(band)` 數字細優先，`:1397` 註解 fish0→loot1→interact2→trade3→script4→quest5/6） |
| 7 | P2 edge 語法未定義 | §1.2：寫死 `item:<out> -[ritual]-> altar:<altarArgId> src:<rel>:<line>` |
| 8 | S1 量化 | §3 S1：旗艦 8 條 + 第二檔 16 條（**新發現**）＋零 edge 清單（含 `.id(` 29 個 recipe id、`.blockBelow`、`Item.of('minecraft:potion',{Potion:…})`） |
| 9 | 旗艦第三 site 交代 | §1.1：`goety_ritual.js:142` ＝**範圍外已知 false negative**（NBT 內 `kubejs:b_a_d/apocalypse` 亦丟），只准出 `gateways:gate_pearl`＋`kubejs:god_bless_full_necklace`（site 係 `Item.of` 直接參數） |
| 10 | 行號／路徑歧義 | §1.3：所有檔一律寫全路徑（`constdef.js` 有兩個：`server_scripts/utils/`（325 行）、`startup_scripts/utils/`（46 行）） |
| 11 | S2 數字改準 | §3 S2：`'ns:id': function` 實測 **92＋47＋9 = 148**（R2 講 137 → 更正）；`summoning_rituals.js` 旗艦 `Item.of(` 只 1 次（`:8`，被消耗） |
| 12 | S3 負對照指名真 id | §3 S3：用 `kubejs:ritual_catalyst`／`kubejs:ritual_god_bless_summon`（**唔准**用 `irons_spellbooks:silver_ring`——親核有真 site） |
| 13 | §2 補 cache/schema 決定 | §2.8：**唔 bump** `INDEX_SCHEMA_VERSION`（走 ask 期，唔寫 index cache）；機制 cache 只需清 |
| 14 | §1/§3 處理 P2 增量 | §1.2：P2 只做「JEI 冇嘅 ritual」；真 trace 已有 `[card:2] 召唤祭坛 … -> kubejs:god_bless_empty_necklace role=uses`（親核 ✓）→ **P2 價值＝JEI 未收錄 ritual**，並要防重複行 |
| 15 | Python 鏡像決定 | §2.9：**跟** `tests/check_kubejs_universal_scan.py`（mirror `PackIndex #5`）→ 同步新增段落，否則 115 baseline drift |
| 補 | §4 對稱句＋配套 | §4：revert 要重跑 `AskMarkerIntegrityCheck`＋`tests/check_ask_marker_integrity.py` |

## 1. 抽取

**1.1 P1（檔案層 map 變身）**：新增**獨立**函式 `ingestTransformEdges(rel, text)`／ask 期 pin `pinFocusTransformFacts(itemId, rel, text)`——**嚴禁**重用 `ingestAcquireEdges`（`PackIndex.java:2248-2260` ITEM regex 掃全檔 → 會令被消耗 key 出反方向取得 edge）。
- 只對「key＝來源 id、body 內 `setStackInSlot(<任何>, Item.of('<out>'))`」抽 `<out>`；key≠out。
- **真 site 只有 2 個**（我全 pack 掃 `Item.of('…god_bless_full_necklace')`）：`kubejs/server_scripts/curios/entity_death.js:26`、`kubejs/server_scripts/b_a_d/recipe/goety_ritual.js:142`。
- edge 機器可讀：`item:<out> -[transform]-> from:<srcId> src:<rel>:<line>`；ask 時按 `replyLang` 渲染（唔准烘 zh_tw prose）。
- 兩 site 都**有條件**（boss 類型清單／NBT friendName＋好友在線＋血量 ≤4）→ 渲染文字必須加「腳本事件轉換（有條件，見腳本）」。

**1.2 P2（鏈式祭壇產出）**：`.itemOutput('ns:id')` **裸字串**。
- 旗艦：`kubejs/server_scripts/ritual/summoning_rituals.js`（441 行；`.altar(` **29**、`.itemOutput(` **8**、`.mobOutput(` 21、`.sacrifice(` 7、`Item.of(` **1**、`.id(` 29、`.input(` 93）——v4.1 數字全部親核正確。
- **v4.2 新發現（v4.1 漏）**：第二個檔 `kubejs/server_scripts/b_a_d/ritual/summoning_rituals.js`（249 行；`.itemOutput(` **16**、`.altar(` 17、`.id(` 17、`Item.of(` 3）→ S1 兩個檔都要做 fixture（8＋16 條）。
- edge 語法寫死：`item:<out> -[ritual]-> altar:<altarArgId> src:<rel>:<line>`。
- chain 單位＝接收者運算式到 `;`（容許註解行）；**必須 chain 隔離**（`:100-107 .mobOutput( )` 跨行 → splitter 要括號感知，唔可以純行切）。
- 同鏈 `.altar(X)`／`.input(...)` → **CONSUMES，永不出取得 edge**；`.input('16x id')` 剝計數前綴；`#tag` 一律丟；`.sacrifice(`／`.mobOutput(` → 零 item edge。
- **增量限定**：只出「JEI 冇收錄嘅 ritual」（真 trace 已有 `[card:2] 召唤祭坛 … -> kubejs:god_bless_empty_necklace role=uses`）＋防重複行；若做唔到增量，就**只做 P1**（plan 名叫 narrow）。

**1.3 丟棄規則（全部寫全路徑）**：`Item.of('')`、`${…}` 動態、`Item.of(mainitem.id)`／`Item.of(itemId)`、`new ItemStack(key,max)`（`kubejs/startup_scripts/dlc/dlc_common.js:61/67`）、NBT 內 `ns:path`（`server_scripts/b_a_d/recipe/goety_ritual.js:142` 只准出 `gateways:gate_pearl`）、`Item.of('id','{NBT}')` 第 2 參數、註解內 id（`server_scripts/utils/constdef.js:60`，陣列項 `:253 machineChestLootTable`）。
- **已知未覆蓋（明寫）**：`kubejs/server_scripts/maodlc/goety_recipe.js` **讀唔到（PermissionError）** → parser 必須 graceful skip，測試要 pin 呢個行為。

## 2. 整合（必要）

1. `PackIndex.java:1265-1379` rank 迴圈：新增 `-[transform]->`／`-[ritual]->` 分支；同步更新 `:1197-1200` AcquireFacts javadoc。
2. **band = 1**（與 loot 同級，優先於 interact2／trade3／script4）：`:1388` `Comparator.comparingInt(RankedAcquire::band).thenComparingInt(seq)` 數字細優先；`:1397` 註解即 fish0→loot1→interact2→trade3→script4→quest5/6。obtain 問題 binding cap ＝ `AskToolContext.java:31 MAX_ACQUIRE_LINES_FULL=12`（`:119` 依 `wantsFullAcquire`），而 12 已喺 `PackIndex.java:1266`（`ranked.size()+cycles.size() >= 12`）封頂 → S3 加斷言「新 edge 必須喺 ranked 12 條內」。
3. `AskEngine.java:472-504`（graphLines 過濾）＋（如走 PURPOSE 通道）`AskPurposeContext.java:48-61`：加同一對 label。
4. **hook（R2 #3 改正）**：喺 `PackIndex.java:1231-1240`（`for (String rel : rels) { … ingestGraph(rel, text); pinFocusLootJsFacts(id, text); n++; }`，10-rel 上限 `:1231`）**隔離**加 `pinFocusTransformFacts(id, rel, text)`；候選 rels 由 `inverted`（`indexScriptItems` 寫入，`:1581-1586`，id → path index）搵「含 focus id 嘅 script」，cap 同 :1231（10）。
5. `MAX_GRAPH=200`（`:28`；滿額靜默丟 `:2542-2546`）→ 只用 focus-scoped `addFactForced`（`:2549`）pin 單條 focus edge，**唔**對所有 script 大量 emit。
6. `MAX_RETRIEVE_FACTS=24`（`:35`／`:563` `selectRelatedGraphFacts`）：明寫**唔依賴** overflow／graphLines 通道（只靠 ranked 12 ＋ pin）；S2/S4 加斷言。
7. **Kill-switch**：`PackAiConfig` 加 toggle（照 `KUBEJS_MECHANIC_SCAN`／`QUEST_MECHANIC_FACTS` 形式）＋3 檔 lang ＋ `tests/check_settings_registry.py` 綠。
8. **Cache/schema 決定**：走 ask 期 → **唔 bump** `INDEX_SCHEMA_VERSION`（`KubeJsMechanicScan.java:96`）；`kjs-*.json` cache（`:1093` key、`:1113` 檔名）不受影響；保險做法＝手動清 `config/packai/mechanic-cache/`。
9. **Python 鏡像**：`tests/check_kubejs_universal_scan.py`（docstring 明寫 mirror `PackIndex #5`）**跟住改**（新增 transform/ritual 段落）＋正負對照（`:129-137/160/174` 前例），否則 115 check baseline drift。

## 3. 驗收（可證偽）

- **S1（Java harness，真 snippet fixture）**：P1 兩個 site 出正確 edge（方向／`src:rel:line`）；P2 **兩個檔**（旗艦 8 條＋`b_a_d/ritual` 16 條）出 `-[ritual]->`；`.altar(X)` 零取得 edge；`entity_hurt.js` 唔准令 `kubejs:friend_to_the_end` 變可取得（親核：`:31` key、`:36` `Item.of('irons_spellbooks:silver_ring')` → 只准出 silver_ring 方向）。
- **S1 零 edge 清單（要寫死）**：29 個 `.id('kubejs:ritual_*')`、`.blockBelow('minecraft:command_block')`（`:437`）、`Item.of('minecraft:potion',{Potion:"minecraft:strong_turtle_master"})`（`:8`，被消耗）、`kubejs:god_bless_full_necklace`（旗艦 `:435 .altar(`）、`minecraft:nether_star`（`:438 .input(`）。
- **S2（真值閘＋naive 對照）**：`'ns:id': function` 實測 **`server_scripts/b_a_d/b_a_d_player_damage.js` 92 ＋ `server_scripts/b_a_d/b_a_d_key_bind.js` 47 ＋ `server_scripts/b_a_d/b_a_d_food_eaten.js` 9 ＝ 148**（R2 講 137 → 更正）；harness 先放 naive parser（`Item\.of\(\s*'([^']*)'`＋整 chain 掃 quoted id）令 fixture **見紅**（`naive >= 50`），再對真 parser 斷言 **0**。
- **S2 五條真檔**：chain 隔離（29×8）／`.sacrifice`＋`.mobOutput` 實體 id 零 item edge／NBT 內 `ns:path`／`new ItemStack`／動態＋計數形；另加**兩個 `summoning_rituals.js` 都要掃**。
- **S2 收窄**：`event.addItem`／tooltip／`scene.world.setBlock` → 斷言「**零 transform／零 ritual edge**」（唔可以寫「零 fact」：`INTERACT_GIVE`（`PackIndex:81-84`→`:2124`）現行已出取得 fact）。
- **S2 mutation**：刪 `PackIndex` 內 `.altar(`（或 `Item.of('literal')`）分支 → 具名 test 必須轉紅。
- **S3（真機／trace 層）**：斷言 trace `send.facts` 含 `-[transform]->` ＋ `kubejs:god_bless_empty_necklace` ＋ `curios/entity_death.js:26`；**刪**「（空）」措辭（真名「神恩项链」）；玩家可見文字會俾 `Plainify.humanizeText`（`:265-266`）humanize → **只喺 trace／LLM-facts 層可驗**。
- **S3 負對照**：用 `kubejs:ritual_catalyst`（全 pack 99 次，`:435` 類 `.altar(` 參數）／`kubejs:ritual_god_bless_summon`（`.id()` recipe id）→ 斷言 `-[ritual]->` 數 ＝ 0；可觀察方式：照 `AskService.java:770` 前例加 `Pack AI transform edges item=… n=…`。
- **S4/S5**：新閘要版本 scope（舊 trace 免假紅）＋正負對照；115 check 全綠；`check_mechanic_facts.py` 只係 string assert，**唔可以**當抽取正確性證明。
- **S6（新）**：P1 site 唯一性斷言——全 pack `Item.of('kubejs:god_bless_full_necklace')` **＝ 2 處**（實測）→ 將來新增／搬 site 而測試照綠要即刻見紅。

## 4. 還原

- `git revert` 單一 commit（含 harness＋gate＋python mirror）；**jar 還原**：`%TEMP%\deploy_backup_*\` 或用 `mc_mod_deploy_jar.py` 重新部署＋核 sha256（要關遊戲）。
- **唔 bump `INDEX_SCHEMA_VERSION`**（走 ask 期）→ revert 只需清 `config/packai/mechanic-cache/`（唔關 index.json）。
- revert 後要重跑：S4 閘（舊 code 上必須紅）＋115 check 全綠＋`AskMarkerIntegrityCheck`（Java harness）＋`tests/check_ask_marker_integrity.py`（新 line 含 `-[…]->`／`->` 序列，AGENTS.md separator 坑）。

## 5. 最壞情況

- 最壞 A：pattern 真命中但**語意誤導**（兩 site 有條件）→ 文字加「有條件」＋S3 只斷言 trace 層。
- 最壞 B：**方向反轉**（被消耗 id 當可取得）→ 獨立函式只 emit `<out>`＋S1/S2 負對照守住。
- 最壞 C（新）：**第二個 `summoning_rituals.js` 影響面**（16 條 edge）令 ranked 12 爆位 → band 1＋「必須喺 ranked 12 內」斷言＋kill-switch 可即時關。

## 6. 交付

- Code 經 **cursor-agent**（唔准 commit／唔准動其他未 commit 檔／唔准 hot-copy jar）；Hermes 親驗 compile／harness／115 check／負對照／diff。
- 完成後做 code review 兩輪（Pass 1／Pass 2）＋獨立 reviewer（fail-closed JSON）。

## 7. 我嘅親核（R2 指控逐條；全部本機實跑）

| R2 指控 | 結果 | 證據 |
|---|---|---|
| `indexScriptItems` 只寫 `inverted`，唔寫 `acquirePathsByItem` | **✓ 成立** | `PackIndex.java:1568-1600`（只 `inverted.computeIfAbsent`）；`:352/:375/:1555` 才寫 acquirePaths |
| `:233-238` 分派令 `kubejs/**` 只入 `scriptRels`；`isAcquirePath` 只認 fishing/loot/trade/quest | **✓ 成立** | `:233-238`；`:2575-2580` |
| 正確 hook 係 ask 期 pin（比照 `pinFocusLootJsFacts`） | **✓ 成立** | `:1231-1240`（`ingestGraph`＋`pinFocusLootJsFacts`，10-rel 上限）；`:2275` pin 定義 |
| band 講反（band 2 唔係「高於」loot band 1） | **✓ 成立** | `:1388` `comparingInt(band)`；`:1397` 註解 fish0→loot1→interact2→trade3→script4→quest5/6 |
| obtain binding cap ＝ `MAX_ACQUIRE_LINES_FULL=12`（唔係 SLIM=3） | **✓ 成立** | `AskToolContext.java:31/:33/:119`；ranked 12 封頂 `PackIndex.java:1266` |
| `addFactForced` 繞 `MAX_GRAPH=200` → 洪泛風險 | **✓ 成立** | `:2542` guard／`:2549` forced／`:28` MAX_GRAPH |
| 漏 `MAX_RETRIEVE_FACTS=24` | **✓ 成立** | `:35`／`:563` |
| raw marker 會被 `Plainify` 消滅（S3 走錯通道） | **✓ 成立** | `Plainify.java:218` `replace("-["," → ").replace("]->"," ")`；`AskEngine.java:1389` fall-through；`AskTrace.java:749` raw 入 `send.facts` |
| mechanic scan 線（`:96/:505/:531/:1093/:1113`） | **✓ 成立** | 逐行核對 |
| `b_a_d_*` 計數 ＝ 137 | **✗ 更正 → 148**（92／47／9） | 我 regex 實測（`'ns:id': function`） |
| 旗艦 `summoning_rituals.js` 29／8／21／7／1 | **✓ 成立**，但**只屬 `server_scripts/ritual/`** | 另有 `server_scripts/b_a_d/ritual/summoning_rituals.js`（249 行，itemOutput **16**）v4.1 冇提 → §1.2 已補 |
| `silver_ring` 有真 site（唔可以做負對照） | **✓ 成立** | `entity_hurt.js:31/:36` |
| `constdef.js` 兩個 | **✓ 成立** | `server_scripts/utils/`（325 行）／`startup_scripts/utils/`（46 行） |
| 真 trace 已有 `[card:2] 召唤祭坛 … role=uses` | **✓ 成立** | `ask-20260915-230340-kubejs_god_bless_full_necklace.jsonl`（8 個 `[card:N]`） |
| （新）1 個 script 讀唔到 | **新發現** | `kubejs/server_scripts/maodlc/goety_recipe.js` PermissionError → parser 要 graceful skip ＋ 測試 pin |

## 8. R3 review 結果（2026-09-16；**未達 8:2 → 按 AGENTS.md 上限停手等 SK**）

| 輪 | 比分 | 註 |
|---|---|---|
| R1 | **正方 2 : 反方 8**／**3 : 7**（兩個反方） | 方向反轉未修好 |
| R2 | **正方 5 : 反方 5** | 15 條本地修正 |
| R3 | **正方 6 : 反方 4**（反方）／**6 : 4**（正方） | 報告：`reviews/2026-09-16_plan-beta-review-R3-{opposing,supporting}.txt` |

**R3 兩條載重 blocker（兩個 reviewer 獨立指向同一位置）**

1. **S3 走錯通道（UNPASSABLE）**：已選嘅 PackIndex 通道會將**每條** graphFact 過
   `LlmClient.java:462-470` → `Plainify.humanizeGraphFact` → `Plainify.java:218`（`replace("-["," → ")`）→
   所以 raw `-[transform]->` **永遠入唔到 `send.facts`**（`AskTrace.java:749` 內容 ＝ `GSON.toJson(user)`）。
   實測真 trace：`send.facts` 5 個事件 **0** raw marker；raw marker 只喺 `send.history`(4)／`tool.result`(1)。
   → 現寫法係「第三條唔存在嘅路」（正向永遠紅、負向 trivial PASS）。
2. **hook 可達性**：`PackIndex.java:1224-1241` 嘅 `rels = acquirePathsByItem[id] ＋ 全部 jeiInfoScriptRels`，
   只有 **10 個名額**（實測含 `.addItem(` 嘅腳本 15 個 → jeiInfoScriptRels 已食爆）；
   而 `inverted` **本身有損**（`indexScriptItems:1576` `seen.size() < 120` 上限）→
   `goety_ritual.js`（旗艦 LICH 源頭）掃到 120 個 id 就收工，**永遠搵唔到**該檔。

**我 v4.2 嘅錯（R3 反方指出，我自己覆核成立 → 已認）**
- `kubejs/server_scripts/maodlc/goety_recipe.js` **唔係檔案，係目錄**（我 Python `open()` 開目錄所以 PermissionError）
  → Java `PackIndex.java:211 Files::isRegularFile` 根本唔會收 → 「parser graceful skip」係**假診斷**，要刪。
- 「R2 講 137 → 我更正 148」→ **兩個數都可重現**（`: function` 無空白容許 = 137；空白容許 = 148）→
  係 regex 定義差別，唔係 R2 錯（我 §7 嘅措辭要改）。
- `constdef.js` 行數 325／46 → 實測 **326／47**；band 註解 `:1397` → **`:1398`**；負對照 id
  `kubejs:ritual_god_bless_summon` **唔存在** → 應 `kubejs:god_bless_summon`；「29 個 `.id('kubejs:ritual_*')`」實際 **7** 個。
- 第二檔 `b_a_d/ritual/summoning_rituals.js`：16 次 `.itemOutput(` 之中 **:89/:118/:125 係 `Item.of('gateways:gate_pearl','{gateway:…}')`**
  → 按我哋自己嘅「裸字串」規則最多 **13** 條，而且三條塌成同一 id（重複 edge）。

## 9. v4.3 提案（三選一，等 SK 拍板；本輪之後唔再開新 review 輪）

- **方案 A（建議）＝收窄：只做 P1 transform edge**
  範圍：`entity_death.js:26`（唯一可達真 site，focus-scoped pin）＋ `entity_hurt.js:36`（silver_ring，做負對照）；
  S3 改斷言 `send.history`／`tool.result`（實測有 raw 嘅只有呢兩條）**或**明文放棄「raw marker 可驗」改斷言渲染文字；
  hook 由 `scriptRels` 逐檔掃（唔靠 `inverted`、唔同 `jeiInfoScriptRels` 爭 10 個位）；
  **砍**：P2（ritual／JEI 增量無定義）＋ kill-switch（ask 期唯讀，無不可逆風險）＋ python 鏡像同步（另開 plan 或 SK 要求先做）。
- **方案 B＝保留全範圍但要 SK 拍兩條載重決定**：(i) 走 mechanic 通道（raw 入 `purpose`，真 trace 已證有 raw）定放棄 raw 可驗；
  (ii) `goety_ritual.js:142`（`registerCustomRecipe` 第 N 個 `Item.of`）覆蓋定明文列 known false negative ——
  然後**最後一輪（第 4 輪）**review，唔達標即放棄／改方向。
- **方案 C＝擱置 β**，先做 C-2（Settings 下一批）／M1e，β 留待「KubeJS 腳本源」有更清楚需求先重提。
