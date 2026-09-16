# Plan α v3.1（**已達標：R3 正方 8 : 反方 2**）— 修正「同 tag 其他成員」行／fact 本體洩漏腳本路徑同垃圾 id

> 觸發（真 trace 實錘）：`<instance>/packai/trace/ask-20260915-230340-kubejs_god_bless_full_necklace.jsonl:28`
> 出 `同 tag 其他成員: kubejs/client_scripts/item_tooltips.js:2（無官方名）;`
> **逐字重驗嘅真洩漏字串（我跑嘅命令同結果）**：
> - peer 行：`kubejs/client_scripts/golden_age/item_tooltips.js:1（無官方名）`；
>   body 內文亦見 `...active_charm.1 (source:kubejs/client_scripts/golden_age/item_tooltips.js:1（無官方名） tier:A)`
> - body／peer 碎片：`(js:1 A)`（**7 個 trace 檔**）、`(js:518 A)`（**3 檔**）；
>   `js:2` 只見 `js:2（無官方名）` 形（**26 次**）——`(js:2 A)` 係**由該形推回**、唔係逐字（v3.1 更正）。
> - ns 假段：`kubejs/server_scripts/common/player_login.js:2`、`.../mrqx_extra_pack/mrqx_common/mrqx_events.js:518`
> - 唯一合法 peer：`彩虹糖果（kubejs:colorful_candy）`×3
> **證據衛生**：舊版列嘅 `整合包脚本（無官方名）`×10、`none（無官方名）`×6 **係我 parse 噪音**（來自 trace 另一欄位
> `sources[]`）——`collectPeers`（`:147-158`）只會存 `ns:path`（必含 `:`）→ 冇冒號嘅 entry emit 唔到。已刪。
>
> **狀態**：R1 **正方 6 : 反方 4** → v2 套 5 條 required_changes → R2 **正方 5 : 反方 5**（未達標）→
> v3 套 R2 全部 → **R3 反方 正方 8 : 反方 2 ＝ 達標**（反方 FC1–FC6 全 RESOLVED；正方全部 standable）。
> v3.1＝套 R3 剩餘證據層小瑕疵（1–2 行級，唔涉設計改動）→ **可以開工**。

## 1. 根因（3 層，全部真 code／真 trace 核實）
目標檔：`forge/1.19.2/src/main/java/com/skps9/packai/logic/OfficialDisplay.java`（253 行，**untracked**）
1. `ITEM_ID`（`:24` = `#?[a-z0-9_]+(?::[a-z0-9_./-]+)+`, CASE_INSENSITIVE）字元類**包含 `/` 同 `.`**
   → `source:kubejs/client_scripts/item_tooltips.js:2` **整段** match 成一個 hit。
2. `resolveAnnotatable`（`:166-188`）按 `:` 切段後**只取最後兩段**當 `label:path`
   → ns 段 = `kubejs/client_scripts/item_tooltips.js`；`annotatable()`（`:202-225`）嘅 `.js/.snbt/.json` 檢查
   （`:216`）**只查 path 半邊**（`2`）→ 放行；`annotatableNamespace`（`:190-200`）黑名單喺錯嘅 ns 上幫唔到手。
3. **冇任何「呢個 id 真係一件物品」嘅要求** → 解唔到官方名都照加註 `（無官方名）`，peer 行（`labeled()` `:136`）
   同 fact 本體（`annotate()` `:104`）**兩邊都出垃圾**。

## 2. 修法（**載重規則 = P2／P4／P5／P5b；P1 已撤回**）
- **⛔ 撤回 P1（token 邊界 lookbehind）**：① 真洩漏 token `(js:1 A)` 前面係 `(` → lookbehind 唔 fire；
  ② 只用 P2/P3/P4 已殺晒同一批 `.js` 垃圾（反方實測：P1 零邊際效益）；③ P1 反而吞 45 個真 id。
- **P2（載重）**：`annotatable(id)` 對 **每一段**（ns 段＋path 段）檢查 `.js`／`.json`／`.snbt` → 丟。
- **P3（載重）**：ns 段唔准含 `/`。⚠️ 唔加「唔准大寫」——`resolveAnnotatable:170` 已 `toLowerCase` → 加咗係死碼。
- **P4（載重）**：`annotatableNamespace` 黑名單（**全細寫**）：原有 `item`／`source`／`tier`／`note`／`file`
  ＋ `mechanic`（**生產載重**：真 corpus 出現 32 次，`mechanic:none` 前面係空格所以 marker guard 唔會擋）
  ＋ `count`（**defence-in-depth／unit 級**，見 §4.1 ⑤ 說明）＋ `via`／`held`／`gets`／`entity`／`table`／
  `gateway`／`structure`／`dimension`／`from`／`src`／`rel`。
- **P5（載重，peer 行）**：`collectPeers()` 只收 `officialName(id)` **非空**嘅項。
- **P5b（載重，fact 本體）**：`annotate()` 亦要求 `officialName(id)` **非空**，否則**原文 token 保持不變**。
  理據＝SK 2026-09-14 規則字面（skill `minecraft-modpack-ai-development`／
  `references/official-display-names-and-sibling-contamination.md:5`：「**官方冇名 → 只寫原 id**」）。
  ⚠️ 誠實聲明：P5b **會改 fact 本體輸出文字**（唔再出 `（無官方名）`）→ 講法係「**語法唔變、內容有變**」。
- **⛔ `labeled()` 簽名同行為唔准改**（`AskDisplayNameCheck.unresolvedKeepsId():131-137` 明文斷言
  `labeled("mod:totally_unknown_item_xyz")` 含 `NO_OFFICIAL`）→ **P5／P5b 只落喺呼叫點**
  （`annotate()` `:87-109` 同 `collectPeers()` `:144-159`）。副作用：`labeled()` 嘅 `NO_OFFICIAL` 分支
  之後只由測試觸發（生產死碼）——要寫 code comment 講明。
- **共用**：兩條路徑用**同一套** predicate（唔准只改一邊）。

## 3. 落地（＋還原方案）
- 改 3 個檔：`logic/OfficialDisplay.java`（P2–P5b）、`src/test/.../AskDisplayNameCheck.java`（fixture＋斷言）、
  `tests/check_ask_display_leak.py`（trace 模式）。
- **還原（第一規則；實測 git 狀態）**：
  - `OfficialDisplay.java`、`AskDisplayNameCheck.java` → **兩者都係 untracked（`??`）** →
    `git revert` **無效**（revert 新增檔＝刪檔）。→ 改前各 `cp` 去
    `%TEMP%\packai_alpha_backup_<ts>\` ＋記 `md5sum`；還原＝copy 返＋md5 對得上。
  - `tests/check_ask_display_leak.py` → **tracked** → git 提供還原（`git checkout -- …`）。
- 只改 **forge 樹**：`OfficialDisplay.java` 全 repo 只此一份（`grep -rl "class OfficialDisplay"`）；
  pause 檔真位置 = **`neoforge/README_PAUSED.md`**（v3 寫 `neoforge/1.21.1/README_PAUSED.md` 係錯，已改）
  → 雙樹 lockstep 已 pause（SK 2026-09-14）→ **唔需要同步另一棵樹**。
- 全部經 cursor-agent（AGENTS.md 硬規則）；Hermes 只 plan／派工／親驗。

## 4. 驗收（**紅先於綠，兩步分明**）

### 4.1 fixtures（**逐字真句；分 body 路徑／peer 路徑**——兩條路徑 guard 唔同）
| # | 輸入 fact（逐字） | 路徑 | 修前（紅） | 修後（綠） |
|---|---|---|---|---|
| ① | `item:怪奇宝典（eccentrictome:tome） -[use]-> 觸發:ItemEvents.tooltip (source:kubejs/client_scripts/item_tooltips.js:2 tier:A)` | peer（經 `enrichFacts`） | peer 行含 `kubejs/client_scripts/item_tooltips.js:2（無官方名）;` | peer 行只剩 `怪奇宝典（eccentrictome:tome）;` |
| ② | `消耗法力：14 active_charm.1 (js:1 A)` | body＋peer | body `(js:1（無官方名） A)`；peer `js:1（無官方名）;` | 兩邊都冇（peer 行整個消失） |
| ③ | `ftbquests 任務 (mrqx_events.js:518 (js:518 A))` | body＋peer | 同上（`js:518`） | 同上 |
| ④ | `item:彩虹糖果（kubejs:colorful_candy） note:mechanic:none` | body＋peer | body＋peer 都出 `mechanic:none（無官方名）` | 兩邊都冇（P4） |
| ⑤ | `x count:1b x` → 直接餵 `annotate()` | body（**unit 級**） | ⚠️ **唔係生產洩漏**：`{Count:1b` 前面係 `{` → `insideItemMarker`（`annotate():96`／`collectPeers():147`）**已 skip**；`annotate()` 輸出 === 輸入（無紅） | 同樣無改動（P4 `count` 係 defence-in-depth，唔係 red→green） |
| 正向 | `效果:give:怪奇宝典（eccentrictome:tome）,give:彩虹糖果（kubejs:colorful_candy）` | body＋peer | 正常 | **一樣正常**（唔准殺） |
**stub lookup 必須有名**（照抄 `mechanicFactItemPrefixNoMangle()` 嘅 pattern）：
`kubejs:colorful_candy`→彩虹糖果、`eccentrictome:tome`→怪奇宝典、`golden_age:infinity_sword`→寰宇支配之剑、
`ino_dlc_build:music.build_henshin`／`mrqx_extra_pack:item/mystery_item`／`ino_dlc_build:item/item/build_phone`→非空。

```bash
# Step 1（紅）：只加 fixture、未改 predicate
python research/gen_tmp_check.py
cd forge/1.19.2 && ./gradlew.bat -I tmp-check.gradle runAskDisplayNameCheck \
  -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"   # 預期 AssertionError
# Step 2（綠）：改 predicate（P2–P5b）→ 重跑同一命令 → "AskDisplayNameCheck OK"
```
兩步**分開留證**（唔可以一次改晒就宣稱紅過）。

### 4.2 斷言（分層；**⛔ 唔准用 raw substring `js:`**）
- ❌ v2 嘅 `!contains("js:")` **物理上唔可能綠**：正向對照 `kubejs:colorful_candy` 內嘅 `kubejs:` 本身含 `js:`
  （我實跑 `python -c "print('js:' in '彩虹糖果（kubejs:colorful_candy）')"` → `True`）。
- ✅ v3.1 語意式（R3 兩邊都獨立驗過 regex：正向對照 → False；`(js:1 A)`／`.js:2（無官方名）` → True；
  對 12,521 個真 id 掃描 → match = 0）：
  1. **peer 行**（`PEER_HEADER` 開頭嗰個元素）：按 `;` 切 entry → 每個 entry 嘅 id（全角括號內）
     唔准 match `(?<![A-Za-z0-9_])js:\d+`、唔准含 `.js`／`.json`／`.snbt`、唔准含 `NO_OFFICIAL`；entry 數 ≤ `PEER_CAP`(8)。
  2. **fact 本體**：唔准含 `NO_OFFICIAL`；全角括號內嘅 id 唔准 match `js:\d+`／`\.js`／`mechanic:`／`count:\d+`。
  3. **⚠️ scope 寫死（R3 正方捉到嘅假紅風險）**：①②**只適用於 packai 注入嘅 fact 文字／peer 行**，
     **唔可以**對模型自己寫嘅正文（prose）套用——真 corpus 有 **14 處**模型自寫嘅 `本包脚本（player_login.js）`
     （field = content/before/after/body）→ 若閘對整條回覆套用，**閘會永久紅**。
  4. 正向對照（§4.1 正向行）必須綠。
  5. 現有 7 個 harness 方法（`positiveLabeled`／`peerSectionAndNoInvented`／`mechanicFactItemPrefixNoMangle`／
     `itemMarkerUntouched`／`proseItemColonUntouched`／`unresolvedKeepsId`／`promptRulePresent`）**全部要照綠**。

### 4.3 閘（`tests/check_ask_display_leak.py`）
- **實測現況**：RC=**2**，輸出 `OK negative control (unfixed sample fails junk assertions)` ＋
  `NO LOG LINES (need real-machine smoke)`（`latest.log` 冇 `Pack AI display body ver=` 行）→ 唔可以當證據。
- 加 **trace 模式**（`--trace <dir>`）：只掃 **注入 facts**（同 §4.2 scope 一致，唔掃 prose），斷言**唔准出現**
  `（無官方名）` 嘅 peer entry／`.js`／`js:<n>`／`mechanic:`／`count:<n>`。
- **日期 scope 用檔名** `ask-YYYYMMDD-*`（反方核實 39 個 trace **冇 buildId／ver 欄位** → 用 build scope 會永久紅）。
- 斷言形式同 §4.2 一致（唔准 `"js:" in line`）；冇 log 行時仍要出 `NO LOG LINES` ＋ RC=2（唔准改成 0）。

### 4.4 回歸 baseline（**實測**）
- `tests/check_*.py` = **115 個**，逐個跑 = **113 PASS / 2 FAIL**：
  `check_ask_display_leak.py`（RC=2，＝本 plan 目標閘）、`check_howto_get_label_parity.py`
  （RC=1，`re.error: bad escape \z at position 150`，**pre-existing**）。
- 收貨＝**冇新增紅**（唔准要求全綠；唔准為綠改 assert）。
- `compileJava compileTestJava` `BUILD SUCCESSFUL`；`AskMarkerIntegrityCheck` 綠。

## 5. 量化效果（**我自己嘅 Python mirror 跑真 corpus**，39 檔 13,877 個 string leaf）
| 指標 | OLD | NEW（P2+P3+P4+P5+P5b） |
|---|---|---|
| 被加註 distinct id | **1,740** | **85**（全部真物品：`golden_age:infinity_sword`、`create:wrench`、`eccentrictome:tome`…） |
| 新增（over-match） | — | **0** |
| 殺咗 | — | **1,655**：`not_in_index` **1,653**（js 路徑、`ns:path`、`mod:id`、`on:/right_click/desc`、時間戳碎片 `01:24.9548338`、`packai:items`…）＋ **2 個真物品但 index 無 label**（`tetra:modular_single`、`tetra:modular_sword`） |
| **有名嘅被誤殺** | — | **0** |
（mirror 位置：`%TEMP%` execute_code kernel；lookup = instance `config/packai/item-index/*.json` 最後一個，
12,587 entries／12,512 有 label。）

## 6. 最壞情況／還原／撤回清單／已接受 false negative
- **最壞**：predicate 太緊 → 吞合法 peer／官方名（守：§4.1 正向對照）／太鬆 → 洩漏照舊（守：§4.1 ①–④ 逐字真句）。
- **還原**：見 §3（2 個 untracked 檔 → timestamped backup ＋ md5；閘檔 tracked → git）。
- **撤回清單（留痕，唔准靜靜 drop）**：
  1. `P1 token 邊界 lookbehind`（零邊際效益＋吞 45 真 id＋真洩漏 token 前係 `(`）。
  2. `（無官方名）` 作 fact 本體主要訊號（P5b 之後 body 唔再出 → body 側要靠 §4.2 junk pattern 清單）。
  3. `「現時該閘 RC=0」`（實測 RC=2）。
  4. `「4,321 檔／odd namespace=0」`（無可重跑命令；實測 9,144 檔／**649** `.js`）。
  5. `整合包脚本（無官方名）`×10、`none（無官方名）`×6（parse 噪音，`collectPeers` 唔可能 emit）。
  6. `(js:2 A)` 逐字聲稱（真 corpus 0 次；係由 `js:2（無官方名）` 推回）。
  7. fixture ⑤ 作「生產 red→green」（`{` 觸發 marker guard，body/peer 兩邊本來都唔 annotate）。
- **已接受 false negative（精確量化 §5）**：
  1. 唔存在於 index 嘅 id（1,653 distinct：真垃圾 ✓ 想要）。
  2. **2 個真物品**（`tetra:modular_single`／`tetra:modular_sword`，index entry 存在但 label 空）
     → 變成只寫原 id（＝ SK 規則字面，有損但可接受）。
  3. 非物品 registry id（`minecraft:generic.attack_speed` 81 次、`forge:reach_distance` 27 次…）→ 同上。
  4. lookup 失效時（client 執行緒缺件／index 未載）同樣唔加註 → **輸出取決於 index 狀態**（已知，唔會出假名）。

## 7. Review record（逐輪）
| 輪 | 正方 | 反方 | 卡住嘅 LD | 本輪改咗咩 |
|---|---|---|---|---|
| R1 | 6 | 4 | — | 套 5 條 required_changes |
| R2 | 5 | 5 | **LD1**（P1 無效＋誤殺）、**LD3**（P3 大寫死碼）、**LD6**（斷言同正向對照互斥） | v3：撤回 P1；P4 加 `count`；斷言改語意式；補 5 條真句 fixture；修 baseline／數字／還原方案；刪 parse 噪音 |
| R3 | **8** | 2 | 無（FC1–FC6 全 RESOLVED） | **v3.1**：修 R3 剩餘證據層小瑕疵（`README_PAUSED.md` 路徑、`(js:2 A)` 標記、⑤ 降為 unit 級、§4.2/§4.3 寫死 scope）＋併入我自己 mirror 量度（§5）同 harness 落點（§2 `labeled()` 唔准改） |
**達標 → 可以開工**（R3 之後唔再開新 review 輪；剩餘只可係文件一致性級）。

### R3 → v3.1 逐條對應
| R3 剩餘項 | 落點 |
|---|---|
| pause 檔路徑寫錯 | §3（改 `neoforge/README_PAUSED.md`） |
| `(js:2 A)` 要標明係推回 | 頂部 trigger 區（加註 ＋ 真值 26 次 `js:2（無官方名）`） |
| fixture ⑤ 唔係生產 red→green | §4.1 ⑤（標「unit 級／defence-in-depth」）＋§6 撤回清單第 7 條 |
| §4.2 body 規則 scope 太闊（14 處 prose 假紅） | §4.2 第 3 點（scope 寫死：只掃注入 facts／peer 行）＋§4.3 |
| 649 定 648？（正方主張 648） | **維持 649**（我自己兩次實跑 `find … -name "*.js" | wc -l` = 649；正方嗰個數唔可重現） |
