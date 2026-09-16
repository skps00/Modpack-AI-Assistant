# Plan β — KubeJS 腳本取得途徑（v4.3：通用多 modpack 版；SK 09-16 定方向）

> SK 指示（09-16）：**「b，I want it can support many modpacks as we can」**
> → 保留全範圍（唔收窄），但**所有規則必須源自「JS 構造」而唔係 mod／包名硬編碼**；
> 兩個載重決定由我（Hermes）按「可支援多 pack」原則拍（見 §0.2），然後做**最後一輪（第 4 輪）**bounded review。
> 輪次：R1 2:8／3:7 → R2 5:5 → R3 **6:4／6:4**（未達 8:2）→ v4.3（本檔）→ R4（最後一輪）。
> 本檔所有數字＝我本機實測（命令見 §8）。

## 0. 兩條載重決定（R3 blocker 1 直接解）

**0.1 通道決定：驗收改為「渲染後文字」，放棄 raw marker 可驗。**
- 理由（R3 實測）：PackIndex 通道每條 fact 都會過 `LlmClient.java:462-470` → `Plainify.humanizeGraphFact` → `Plainify.java:218`（`replace("-["," → ")`），所以 raw `-[transform]->` **永遠入唔到 trace `send.facts`**（真 trace 5 個 `send.facts` 事件、raw marker **0**；raw 只喺 `send.history`／`tool.result`）。
- 決定：S1（harness）＋S3（真機）都斷言**同一條 render 函式嘅輸出**（`ReplyLang` 新 label）——pack 無關、語言無關、唔靠內部 marker 格式。
- 保留 raw marker **做 debug**（寫入 trace 嘅 `tool.result` 或 mechanic 通道），但**唔做驗收條件**。

**0.2 覆蓋決定：`goety_ritual.js:142` 由「Sink C 通用規則」覆蓋（唔列 false negative）。**
- 理由：呢個 site 屬 `registerCustomRecipe(new <X>Recipe(...))` 構造（實測全 pack **566 次／38 檔**，橫跨 GoetyRitualRecipe／MixingCauldronRecipe／DragonForgeRecipe ⋯）→ 係**大類**，支援多 pack 必須做（單一 pack 先可以當例外）。
- `from:`／`by:` 語意寫死：`by:<recipeIdArg> src:<rel>:<line>`（`recipeIdArg` 取構造呼叫第一個字串參數，例 `'lich'`；冇字串參數就 `by:?`）。

## 1. 通用抽取規則（全部按 JS 構造，唔按 mod 名）

**Sink A — 狀態改寫（JS 物件／key 型）**
`setStackInSlot(<任何>, Item.of('<out>'[, '<nbt>']))` → `item:<out> -[transform]-> from:<enclosingKeyId> src:<rel>:<line>`
- `from:` ＝ 最近外層 map key 嘅 id 字面（實測本 pack 只有 `curios/entity_death.js:26`／`entity_hurt.js:36` 兩處係 `Item.of` 型；另外 3 處 `setStackInSlot(…newItem)`／`new ItemStack(key,max)`（`startup_scripts/dlc/dlc_common.js:61/67`、`server_scripts/maodlc/maodlc_key_pressed.js:291`）按「只認 `Item.of` 字面」規則**自然丟棄** ✓ 唔使特判）。

**Sink B — builder 鏈輸出**
`.itemOutput('<id>')` → `item:<id> -[ritual]-> by:<ownerChainId> src:<rel>:<line>`；`.itemOutput(Item.of('<id>'[, nbt]))` 同等（實測本 pack 旗艦 0 個，但第二檔 3 個 → 要支援）。`.itemOutput('#tag')` 丟。
- `ownerChainId` ＝ 同一 statement（接收者運算式到 `;`，括號感知）內 `.id('…')` 嘅值；冇 `.id(` 則 `by:?`。
- `.mobOutput(`／`.sacrifice(` ＝ 實體／祭品 → **零 item edge**（實測 21／7 次）。
- `.input(` ＝ CONSUMES（剝 `'16x '` 前綴；`'#tag'` 丟）→ 永不出取得 edge。
- scale 證據：`.itemOutput(` 全 pack **217 次／12 檔**（唔止旗艦）→ 規則必須全市掃。

**Sink C — recipe 註冊構造（最大類）**
`registerCustomRecipe(new <X>Recipe(<args…>))`：`args` 內 **`Item.of(...)` 頂層參數 ＝ 輸出**；**`Ingredient.of(...)` 內嘅 ＝ 輸入（CONSUMES）**；第一個字串參數 ＝ recipeId。
- 每個輸出出 `item:<out> -[recipe]-> by:<recipeId> src:<rel>:<line>`。
- 括號感知 splitter 分頂層參數（唔可以用 regex 直接掃，實測跨行）。
- scale 證據：566 次／38 檔；`Item.of(` 全 pack 3361／222 檔、`Ingredient.of(` 1282／40 檔。

**一律丟棄**：`Item.of('')`、`${…}`／identifiers（`Item.of(mainitem.id)`）、`new ItemStack(<key>,<n>)`、NBT 內 `ns:path`、`Item.of('id','{NBT}')` 第 2 參數、`#tag`、註解內 id（`server_scripts/utils/constdef.js:60`；`:253 machineChestLootTable` 係陣列）。
**檔案列表**：一律寫全路徑；`constdef.js` 有兩個（`server_scripts/utils/` 326 行、`startup_scripts/utils/` 47 行）。

## 2. 完整站點索引（直接解 R3 blocker 2／3）

**新索引（同一次 script 掃描內建立，唔加 extra IO）**：
- `sinkSitesByOutput: outId → List<(rel,line,kind,byId)>`（Sink B／C：**以輸出 id 為 key**）
- `transformSitesByKey: keyId → List<(rel,line)>`（Sink A：以來源 key 為 key）
- **唔靠** `inverted`（`indexScriptItems:1576` 有 `seen.size() < 120` 上限 → 實測 `goety_ritual.js` 掃到 120 個 id 就停、尾段嘅 necklace **永遠搵唔到**）、**唔爭** `jeiInfoScriptRels` 嗰 10 個名額（`PackIndex.java:1224-1241`）。
- Ask 期：`sinkSitesByOutput.get(focusId)` ＋ `transformSitesByKey.get(focusId)` → 直接拎 `(rel,line)` → 只 parse 命中行嘅所屬 statement（唔 parse 全檔）→ **O(1)、pack size 無關** ✓
- 預算（多 pack 安全）：每檔站點上限 2,000、全局 50,000（超出只 log）；`Files::isRegularFile` 已過濾目錄（`PackIndex.java:211`）→ Python 鏡像要自己 skip dir。

## 3. 整合

1. `PackIndex.java:1265-1379` rank 迴圈：新增 `-[transform]->`／`-[ritual]->`／`-[recipe]->` 三分支（parse 規則寫死：`startsWith("item:<focusId> ")` 之後逐 kind `substring`）；同步 `:1197-1200` javadoc。
2. **band = 1**（同 loot；`:1388` `comparingInt(band)` 數字細優先；註解喺 **`:1398`**（唔係 :1397）fish0→loot1→interact2→trade3→script4→quest5/6）。obtain binding cap ＝ `AskToolContext.java:31 MAX_ACQUIRE_LINES_FULL=12`（`:119`）＋ `PackIndex.java:1266` 12 封頂 → 加「新 edge 必須喺 ranked 12 內」斷言。
3. `AskEngine.java:472-504` ＋ `AskPurposeContext.java:48-61`：加同一對 label。
4. hook：喺 `PackIndex.java:1231-1240`（`ingestGraph` ＋ `pinFocusLootJsFacts` 隔離）加 `pinFocusScriptEdges(id, rel, line)`，**只 pin `item:<focusId>`**；候選由 §2 新索引直接拎（唔經 rels 預算）。滿 `MAX_GRAPH=200`（`:28`／`:2542` guard）時只用 `addFactForced`（`:2549`）pin 單條 focus edge（唔洪泛）。
5. **唔依賴** `MAX_RETRIEVE_FACTS=24`（`:35`／`:563`）overflow 通道（明寫）；加斷言。
6. **Kill-switch**：`PackAiConfig` 加 toggle（**照 toml-only 先例**：`:439-443` `KUBEJS_MECHANIC_SCAN` 形式，唔郁 SettingsRegistry／lang）→ 多 pack 一定要有開關。
7. **Parser 版本＋cache**：新索引寫入現有 index（唔寫 `kjs-*.json`）；`INDEX_SCHEMA_VERSION`（`KubeJsMechanicScan.java:96`）**bump 1→2**（索引新增欄位）＋cache key（`:1093`）加 parser salt；revert 要再 bump ＋ 刪 `kjs-*.json`（`:1113`）。
8. **Python 鏡像同步**：`tests/check_kubejs_universal_scan.py`（mirror PackIndex #5）要加三分支＋skip dir，否則 115 baseline drift。

## 4. 驗收（可證偽）

- **S1 harness（Java，真 snippet fixture；每個 Sink 一個真 snippet）**：A＝`entity_death.js:26` → `item:kubejs:god_bless_full_necklace -[transform]-> from:kubejs:god_bless_empty_necklace src:…/entity_death.js:26`；A 負例＝`entity_hurt.js:36` 只准出 `irons_spellbooks:silver_ring`（**唔准**令 `kubejs:friend_to_the_end` 變可取得）；B＝旗艦 8 條裸字串 + 第二檔 13 條（16 減 3 個 `Item.of(gateway…)`）＋3 個 gateway 案例只准出 1 條（去重）；C＝`goety_ritual.js:142` → `item:kubejs:god_bless_full_necklace -[recipe]-> by:lich src:…:142`（NBT 內 `kubejs:b_a_d/apocalypse` 丟）。
- **S1 零 edge 清單（寫死）**：7 個 `.id('kubejs:ritual_*')`（`_shard` 7 個）＋其餘 `.id(` 值、`.blockBelow('minecraft:command_block')`（`:437`）、`Item.of('minecraft:potion',{Potion:"minecraft:strong_turtle_master"})`（`:8`，被消耗）、`minecraft:nether_star`（`:438 .input(`）、`kubejs:god_bless_full_necklace`（`:435 .altar(`）。
- **S2（真值閘＋naive 對照）**：`'ns:id'\s*:\s*function` 實測＝**92＋47＋9＝148**（另一種寫法 `: function`（無空白容許）＝85＋44＋8＝137——**兩者都係定義差別，唔係錯**）→ naive parser 門檻寫 `>= 50`（一定紅）、真 parser ＝ **0**。
- **S2 五條真檔**：chain 隔離（旗艦 29 `.altar(` × 8 `.itemOutput(`）／`.sacrifice`＋`.mobOutput` 零 item edge／NBT 內 id／`new ItemStack`／動態＋計數形；**全市掃**（`.itemOutput(` 12 檔）唔可以只掃旗艦。
- **S2 mutation**：刪 Sink B 分支 → 具名 test 轉紅；刪 Sink C 分支 → C fixture 轉紅。
- **S3（真機／trace 層；渲染文字）**：斷言 trace 內（`send.facts` 或 `tool.result`）出現**render 函式對該 edge 嘅輸出**（含 `src:<rel>:<line>` 或 humanize 後等價字串）；**唔再**斷言 raw `-[…]->`；**唔**聲稱玩家可見（`Plainify.humanizeText` 會 humanize）。
- **S3 負對照（指名真 id，唔可以用 `silver_ring`）**：`kubejs:god_bless_summon`（實測 2 次／2 檔、**唔係 site**）→ 斷言「**subject ＝ focus id** 嘅 edge 數 ＝ 0」（唔係數 marker 出現次數——`altar:kubejs:ritual_catalyst` 會出現喺別人 payload 內）。
- **S4/S5**：新閘要版本 scope（舊 trace 免假紅）＋正負對照；115 check 全綠；`check_mechanic_facts.py` 只係 string assert，唔可以當抽取正確性證明。
- **S6**：站點計數斷言——Sink A = 2（`Item.of` 型）、Sink B 裸字串 = 8（旗艦）＋13（第二檔）、Sink C = 566 次／38 檔（只斷言 ≥ 500／≥ 30 檔做**回歸下限**，唔寫死精確值以免包更新就紅）。
- **S7（多 pack 安全，新）**：① 無 `kubejs/` 目錄嘅 pack → 零 edge、唔 crash；② 讀唔到／係目錄嘅路徑 → skip（Java：`Files::isRegularFile`；Python：`os.path.isfile`）；③ 極大 script（本 pack 最大 207 KB）→ 時間 < 50 ms、無超時；④ 非 UTF-8／BOM → 唔 crash。

## 5. 多模組包適配（本節＝SK 目標嘅落地）

- 三條 Sink 規則全部係 **JS 構造**（`Item.of` 出現位置／`registerCustomRecipe(new XRecipe(` 大類／`.itemOutput(` builder）→ 換 pack、換 mod 都成立（本 pack 已經有 40 種 recipe class 名同時命中 Sink C）。
- 索引 **by-output**（唔 by-file）→ 新 pack 大細唔影響 ask 期成本。
- 全部上限可 config（站點數／檔案 bytes／時間）；kill-switch 一鍵關。
- **唔准**任何 `goety`／`b_a_d`／`kubejs` 名硬編碼（fixture 可以用真名，規則唔可以）。

## 6. 還原

- `git revert` 單一 commit（含 harness／gate／python mirror）；jar 由 `%TEMP%\deploy_backup_*\` 或 `mc_mod_deploy_jar.py` 重新部署（要關遊戲）。
- `INDEX_SCHEMA_VERSION` bump 1→2 → revert 要**再 bump 返**（否則舊 index 被重用）＋刪 `config/packai/mechanic-cache/`。
- revert 後：S4 閘（舊 code 必須紅）＋115 check 全綠＋`check_ask_marker_integrity.py`。

## 7. 逐條回應 R3（15 條 + 9 blockers）

| R3 | 狀態 | v4.3 落點 |
|---|---|---|
| #1 通道二揀一 PARTIAL | ✓ 解 | §0.1（揀「渲染文字」） |
| #2 S3 不可證偽 NOT RESOLVED | ✓ 解 | §0.1＋§4 S3 |
| #3 hook 可達性 PARTIAL | ✓ 解 | §2（by-output 索引，棄 `inverted`） |
| #4 addFactForced RESOLVED | 保留 | §3.4 |
| #5 MAX_RETRIEVE_FACTS RESOLVED | 保留 | §3.5 |
| #6 band RESOLVED（行號更正） | ✓ | §3.2（`:1398`） |
| #7 edge 語法／parse key PARTIAL | ✓ 解 | §1＋§3.1（寫死 `startsWith`） |
| #8 S1 量化 PARTIAL（第二檔 13 唔係 16） | ✓ 更正 | §4 S1／S6 |
| #9 goety:142 自相矛盾 NOT RESOLVED | ✓ 解 | §0.2（Sink C 覆蓋） |
| #10 路徑／行號 RESOLVED | ✓ 更正 | §1（326／47） |
| #11 137 vs 148 PARTIAL | ✓ 更正 | §4 S2（兩者都係定義差別） |
| #12 負對照 PARTIAL | ✓ 解 | §4 S3（`god_bless_summon`＋subject 語意） |
| #13 cache/schema RESOLVED | 保留（改 bump） | §3.7 |
| #14 P2 增量 PARTIAL | ✓ 解 | Sink B 全市掃＋`by:` 語意；重複 gateway 去重 |
| #15 python 鏡像 RESOLVED | 保留 | §3.8 |
| R3 補 §4 配套 PARTIAL | ✓ 更正 | §6（真風險位係 `Plainify:218`／`AskReplyScrub` 嘅 `role=`／` -> `，唔係 marker separator） |
| blocker 4（規則 vs site） | ✓ 解 | §0.2／§1 Sink C |
| blocker 5（假診斷：goety_recipe.js 係目錄） | ✓ 刪 | §8 |
| blocker 6（第二檔 16→13） | ✓ 更正 | §4 |
| blocker 7（payload 打爆負對照） | ✓ 解 | §4 S3 subject 語意 |
| blocker 8（P2 方向／降級語意） | ✓ 解 | Sink B 出 `by:`；`altar:` 唔再入 edge payload；「降級兼消耗」用 `-[ritual]->`＋文字標明「消耗」 |
| blocker 9（scope creep） | ✓ 降 | kill-switch 改 toml-only（唔郁 SettingsRegistry／lang） |

## 8. 我親核更正（R3 指出，全部成立，已認）

| 我 v4.2 寫 | 實情 | 來源 |
|---|---|---|
| `maodlc/goety_recipe.js`「讀唔到（PermissionError）要 graceful skip」 | 佢**係目錄**（內含 `maodlc_recipe.js`）；我 Python `open()` 開目錄先出錯；Java `Files::isRegularFile`（`PackIndex.java:211`）根本唔收 → 假診斷，**刪** | 目錄 stat ＋ `:211` |
| 「R2 講 137 → 更正 148」 | 兩個數都可重現：`: function`（無空白）＝137；`\s*:\s*function`＝148 → 定義差別，唔係 R2 錯 | 兩 regex 各跑一次 |
| `constdef.js` 325／46 行 | **326／47** | `splitlines()` |
| band 註解 `:1397` | **`:1398`** | `sed -n` |
| 負對照 id `kubejs:ritual_god_bless_summon` | **唔存在**（0 次）→ 用 `kubejs:god_bless_summon`（2 次／2 檔） | 全 pack grep |
| 「29 個 `.id('kubejs:ritual_*')`」 | `.id(` 值共 29 個，其中 `ritual_*` **7** 個 | regex 列舉 |
| 第二檔 ritual edge 16 條 | **13** 條裸字串（16 之中 3 個係 `Item.of('gateways:gate_pearl','{gateway:…}')`） | `itemOutput(` 分類 |
| `kubejs:ritual_catalyst` 99 次 | **98 次／14 檔** | 全 pack 計數 |
