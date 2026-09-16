# Plan β — v5.5（API-first ＋ 包優先措辭；R6 修正）

> **文檔規則（v5.5 起強制）**：每條命題必須掛 `[V]`＝本檔作者已用指令實測，或 `[U]`＝未驗證（未驗證唔准當事實用）。
> 由來：v4.x 靜態解析 recipe 被推翻（382/895 方向反轉）；SK 指「用 KubeJS／RecipeManager API」→ recipe 全交 JEI；SK 再指「措辭 base on pack」→ §2 重寫。
> 輪次：R1 2:8／R2 5:5／R3 6:4／R4 6:4（舊設計）→ R5 反方 4:6／正方 8:2 → 裁判 7:3 → **R6 反方 5:5／fact audit 51/66** → 本檔 v5.5（第 4 輪＝硬上限）。

## 0. 設計原則
**Recipe 類 → 現成 JEI 通道（零新碼）；只有「唔係 recipe 嘅 JS 副作用」才解析腳本。**
- `[V]` 可解析 surface（我實測 raw site）：`.itemOutput(` **217** ＋ `registerCustomRecipe(new` **566** ＋ `setStackInSlot` **5** = **788**；kubejs script = **648** 個 regular `.js`（第「649」個係**目錄** `server_scripts/maodlc/goety_recipe.js`）
- `[V]` 其中真正要解析嘅 JS sink 只有 **2 個**：`curios/entity_death.js:26`（→ 滿溢神恩項鍊）、`curios/entity_hurt.js:36`（→ `irons_spellbooks:silver_ring`，負對照）
- ~~895／890／559~~ **全部刪**（不可重現；R6 fact audit F1/F2）

## 1. 通道

### 1.1 Recipe 類（零新碼；用 JEI，唔用 sweep）`[V]`
- 覆蓋來源＝**現成 JEI layout role 讀取**（`JeiLookup.java:809 roleMatchesFocus`／`JeiRecipeCards.java:356 createRecipeLookup`／`JeiInfoPages.java:99`）→ 自訂 type 一樣見到（真 trace：`召唤祭坛 → kubejs:god_bless_empty_necklace`、`魔法仪式 · 黑暗祭坛`）✓
- **sweep 取消**：`Recipe#getResultItem(RegistryAccess)` 1.19.2 唔存在（照寫 compile 唔到）；`summoningrituals` `AltarRecipe.getResultItem()` 返 `EMPTY`（javap -c）→ sweep 對 217 個 `.itemOutput(` site 零收穫
- `[U]` **未覆蓋**（明列，唔准當通用解）：JEI 無 category 嘅 recipe type（**數量未量度**）、`event.recipes.*`（`[V]` 1354）、`player.give(`（`[V]` 276）、`maodlc_key_pressed.js:288-291`（guiyan／guiying）
- 診斷 log（唔做斷言）：`PackAI jei-output focus=<id> categories=<N> roleOutput=<K> roleInput=<M>`

### 1.2 JS 副作用通道（窄；唯一解析規則 P-A）
- **P-A**：`setStackInSlot(<any>, Item.of('<out>'))` 型事件效果
  - `[V]` `setStackInSlot` 全包 5 次／4 檔；真 sink 2 個；其餘 3 個（`new ItemStack(key,max)`／變數）正確丟棄
  - **來源 key 取法（v5.5 收緊）**：由**外層物件定義**取（key 喺 `:22`、命中喺 `:26`）。取**最外層 map literal**（唔取最近一層，避免 nested map 取錯 key）；支援「map literal 內」**同**「具名 map ＋事後 `S['ns:id'] = function…` 賦值」兩形
  - **key 必須符 item id 形（`ns:path`）**，否則**唔出 edge**（例：`{ 'step_1': function… }` 要丟）——防 false positive
  - `[U]` 動態 key／spread／跨檔 `Object.assign` 合併（本包有 `maodlc_key_pressed.js:739` 呢種 idiom）：**本包已證 0 例命中旗艦、其他包未證**——唔准寫「換 pack 都成立」
  - 方向閘（v5.5）：只同「**同一 recipe／同一 type**」嘅 JEI 卡 role 比對；旗艦 3 張卡全部 `role=INPUT`（屬其他 recipe）→ **唔可以當反證**；對唔上**只 log、唔抑制 edge**
- 索引 `jsEffectSitesByOutput: outId → [(rel,line)]`（ingest 同一 pass 建；唔靠 `inverted`、唔爭 10-rel 名額）→ ask 期 O(1)
  - `[V]` 成本：全掃 648 檔 python 代理實測 **5.5–19.2 ms**（零額外 IO）

### 1.3 整合接線（**v5.5 新增，必須做；唔做玩家睇唔到**）
- **問題**：acquire 組裝處 `PackIndex.acquireFactsDetailed`（`:1214-1396`）逐條 match graphFacts 用**封閉 prefix 白名單**（`:1265-1380`：fish／loot／trade／quest_*／recipe_needs／removed／right_click*）；`AskEngine` 第二條 rendering（`graphLines`，`:472-504`）同樣係封閉集合 → **新 prefix（`-[transform]->`）唔會有任何一行輸出**，即資料入咗索引同 trace，但答案同今日一樣（「冇取得途徑」）
- **必做**：在 `:1265-1380` 加一條 `-[transform]->` branch（＋必要時 `AskEngine` 同步），並指定**低 band**
- **band 限制（要寫明）**：cap `ranked.size()+cycles.size() >= 12`（`:1266`）**喺 band 排序（`:1388`）之前** → band 只排「已入選 12 條」嘅次序，**救唔到一條從未入 ranked 嘅 edge**；SLIM 3 行（`AskToolContext.java:31/33`；`labeled[0]`＝header → SLIM 實質只有 header＋2 條）
- `[V]` 旗艦今日 acquire 結果＝**空**（真 trace `ask-20260916-133612`）→ 新 edge 一定入到 12 行／SLIM 3 行 → **今日顯示得到** ✓
- `[U]` 「本身有 ≥12 條更早邊嘅物品」會唔會被擠走 → **S9 量度**（見 §3）

### 1.4 KubeJS tooltip 文字（**v5.5 重寫**）
- `[V]` **今日實況（我逐條 trace 實測）**：滿項鍊嘅 tooltip **文字有入 fact**（2 個 trace 含「用于在沙漠维度地牢中进行神意挑战」）；但 **lang key 本身 0 命中**（`kubejs.tooltips.god_bless_full_necklace.1` = 0 檔）→ v5.4 講「只有 key」同 R6 reviewer 講「trace 已含 key」**兩者都唔準**
- `[V]` **旗艦真正缺口＝來源側措辭**：空項鍊嘅文字「击败…即可充能」喺全部 trace = **0 命中**（`god_bless_empty_necklace.1`／「即可充能」皆 0）→ 而要跟 SK「base on pack」，答案要用嘅正正係呢句（§2 規則 1）
- `[V]` **真問題＝note 錯配**：`KubeJsMechanicScan` 嘅 `note`（`:1049-1050`；由 `extractNote` `:893-917` 喺整個 handler body 抓**頭兩個** `Text.translatable`）係**檔案級** → 會掛錯物品（滿項鍊 fact 帶住 `active_pill.1／.2`；`active_pill` 係另一件物品）
- **修法**：note **只准**用**焦點物品自己嘅** key（`kubejs.tooltips.<focusId>.N`）＋該 key 存在於 `kubejs/assets/**/lang/<lang>.json`；解析唔到 → **唔出 note**（寧缺勿錯）
- 語言：`[V]` 本包 lang **只有 `zh_cn.json`**（無 en_us／zh_tw）→ 其他語言用 zh_cn 原文並標 `lang_fallback`（唔准靜默出 key）
- 解析位置：**render 期**（`I18n.get`；先例 `AskService.java:2250`、`ModularToolScan.java:274`）→ **唔使 bump** `INDEX_SCHEMA_VERSION`；若改 scan 期（`PackIndex.translations`，`:151/:295/:307`）→ 因 note 會烘入 `kjs-*.json`（`:96/505/531`）**必須 bump**

## 2. 邊緣文字規則 R-PHRASE（**v5.5 重寫**；SK：base on pack）
1. **原文優先 —— 兩邊都要搵**：焦點物品**同**來源物品嘅 tooltip／JEI info／任務文字
   - `[V]` 旗艦：**來源側（空項鍊）** `zh_cn.json:1093` 已有包自己嘅動詞同四隻 boss 名 → **用原文**：`击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能`（v5.4 誤判「包只講用途 → 走機械式」→ 撤回）
2. **包真係冇字眼** → 真機械式（**零解釋**）：`<來源官方名>（id）→（<事件識別字>）→ <產出官方名>（id）`
   - 禁止：自行加動詞（「击杀」）、展開 mod 全名、補「類 boss」等解釋；未解析識別字要標明係「**腳本標記**」（唔可以當玩家用語）
3. **動詞規則（v5.5 改正）**：答案內出現嘅動詞**必須可喺包文字搵到出處**（可回溯）——**唔係黑名單**（v5.4 把包自己嘅「充能」列做禁詞＝錯）
4. 語言 fallback：如上（`lang_fallback`）

## 3. 驗收（S1–S9；誠實標示）
| # | 斷言（artefact） | 狀態 |
|---|---|---|
| S1 | **P-A 經正式 scanner**（唔准測試自己 reimplement）：真 snippet 餵入 ingest 路徑 → 出 `item:kubejs:god_bless_full_necklace -[transform]-> from:kubejs:god_bless_empty_necklace src:curios/entity_death.js:26`（**唔要求條件／事件**——P-A 未定義條件抽取；條件留白） | **紅** ✓ |
| S2 | **role 政策**：`OUTPUT claim 只算 recipe role=OUTPUT`（寫死，免得新 edge 令 S2 自己紅）；正向：`:142`＝`activation_item` ＋ `result=gateways:gate_pearl` ＋ 滿項鍊 JEI-output 查詢 = **0 條 recipe 產出** | **紅** ✓（今日零 claim＝真空 → 靠正向斷言變紅） |
| S3 | 真機 trace：**acquire tool.result／`send.facts` 區塊**出現新 render label（同 `ReplyLang` 輸出逐字比對）——**唔斷言最終答案**（`[V]` `ReplyLang.localAcquireHeader` 只入 fact 區塊；46 個 trace 最終答案 0 次） | **紅** ✓ |
| S4 | 冇 kubejs 目錄／空目錄／讀唔到檔：唔准 crash、唔准出錯 edge | 守門 |
| S5 | caps：站點／檔案大小上限（config）＋超限只 log 唔爆 | 守門 |
| S6 | **站點真相表**：`Item.of('kubejs:god_bless_full_necklace')` = 2 處（1 產出 `entity_death.js:26`、1 激活 `goety_ritual.js:142`）＋ 4 處輸入側提及（`peifang.js:219`、`lunasexrecipes.js:997`、`summoning_rituals.js:435`、`goety_ritual.js:142`）→ parser 只准由 `:26` 出 edge | **紅** ✓ |
| S7 | **索引斷言**：`jsEffectSitesByOutput` 對滿項鍊**恰好 1 個 site**＝`entity_death.js:26`；負對照 `entity_hurt.js:36` 只可出 `silver_ring` ← `friend_to_the_end`；**key 非 item id 形要丟**（poisoned fixture：`{'step_1': …}` 唔准出 edge） | **紅** ✓ |
| S8 | **綁 focus item 嘅 lang**：focus＝空項鍊 → fact 必須出現 `zh_cn.json:1093` 原文；**唔准**用「全 trace 有冇『激活效果』」呢種鬆判準（嗰個係另一件物品 `active_pill` 嘅字）；regression guard：唔准出錯物品文字 | **紅** ✓ |
| S9 | **擠走測試（新）**：造一個「≥12 條更早 acquire 邊」嘅 fixture 物品 → 斷言新 edge **仍然出現**，或被擠走時**必須有 log**（量度 cap 風險） | **紅** ✓ |

## 4. 開關／兼容（多 pack）
- kill-switch：**`packai-client.toml`**（`[V]` packai 只有 CLIENT spec：`PackAiMod.java:32`）→ `jsEffectSites`＋`diagLog`
- parser 版本：索引寫 `version`；版本變 → 重建（cache salt）
- 範圍：只掃 `kubejs/**/*.js`（`Files::isRegularFile`）；大檔（>400 KB）／讀唔到 → skip + log

## 5. 我親核過嘅事實（只列 `[V]`）
- `registerCustomRecipe` = 包 glue（`goety_ritual.js:32`）→ `event.custom(...)` = KubeJS 官方 API ✓
- `goety_ritual.js:142`：項鍊＝`activation_item`（第 3 參數、被消耗）；`result = gateways:gate_pearl` ✓
- Goety 有 JEI plugin（`Goety-2.34.2.jar`：`GoetyJeiPlugin`／`JeiRecipeTypes`）✓
- `entity_death.js:22` key＝`kubejs:god_bless_empty_necklace`；`:26`＝`setStackInSlot(..., Item.of('kubejs:god_bless_full_necklace'))` ✓
- `entity_hurt.js:31` key＝`kubejs:friend_to_the_end`；`:36`＝`Item.of('irons_spellbooks:silver_ring')` ✓
- 648 個 `.js`／`Item.of(` 3361／`Ingredient.of(` 1282（R6 audit 交叉核）
- `kubejs.tooltips` 全 trace 32 命中；真截斷 `(?<!kube)js\.tooltips`＝**0**（v5.4 假 bug 已撤回）
- 115 個 `tests/check_*.py`：113 PASS／2 FAIL（baseline）

## 6. 還原
- 純新增（索引、label、log、1 條 prefix branch）＋ toml 開關；revert = `git revert` ＋ 清 KubeJS mechanic cache
- 驗證還原：`AskMarkerIntegrityCheck`（新 label 含 `-[…]->`）＋ 115 check 回到 baseline

## 7. 歷史比分
R1 2:8 → R2 5:5 → R3 6:4 → R4 6:4（舊設計）→ **改設計（API-first）** → R5 4:6／8:2 → 裁判 7:3 → R6 5:5 → **v5.5（第 4 輪＝上限）**
