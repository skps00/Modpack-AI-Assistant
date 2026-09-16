# Plan α v3 — 修正「同 tag 其他成員」行洩漏腳本路徑／垃圾 peer（bug fix，獨立細修）

> 觸發（真 trace 實錘）：`<instance>/packai/trace/ask-20260915-230340-kubejs_god_bless_full_necklace.jsonl:28`
> 出 `同 tag 其他成員: kubejs/client_scripts/item_tooltips.js:2（無官方名）;`
> **v3 逐字重驗嘅真洩漏字串**（我跑咗）：
> - peer 行：`kubejs/client_scripts/golden_age/item_tooltips.js:1（無官方名）`（真 body 內文亦見
>   `...active_charm.1 (source:kubejs/client_scripts/golden_age/item_tooltips.js:1（無官方名） tier:A)`）
> - fact 本體碎片：`(js:1 A)`（**7 個 trace 檔**）、`(js:518 A)`（**3 檔**）、`(js:2 A)`
> - ns 假段：`kubejs/client_scripts/item_tooltips.js:2`、`kubejs/server_scripts/common/player_login.js:2`、
>   `.../mrqx_extra_pack/mrqx_common/mrqx_events.js:518`
> - 唯一合法 peer：`彩虹糖果（kubejs:colorful_candy）`×3
> **證據衛生（v3 更正）**：舊版列嘅 `整合包脚本（無官方名）`×10、`none（無官方名）`×6 **係我 parse 噪音**
> ——`collectPeers`（`:154-158`）只會存 `ns:path`（必含 `:`），冇冒號嘅 entry 佢**根本 emit 唔到**；
> 嗰兩組其實嚟自 trace 另一個欄位（`sources[]`）。已刪。
>
> **狀態**：R1 反方 review **正方 6 : 反方 4** → v2 套 5 條 required_changes → R2 反方 **正方 5 : 反方 5**
> （未達標：我 v2 嘅修法自帶咗新洞）→ **本版 v3 套用 R2 全部 required changes**（逐條見 §6），待 R3（有界輪）。

## 1. 根因（3 層，全部真 code／真 trace 核實）
目標檔：`forge/1.19.2/src/main/java/com/skps9/packai/logic/OfficialDisplay.java`（253 行，**現時 untracked**）
1. `ITEM_ID`（`:24` = `#?[a-z0-9_]+(?::[a-z0-9_./-]+)+`, CASE_INSENSITIVE）嘅字元類**包含 `/` 同 `.`**
   → `source:kubejs/client_scripts/item_tooltips.js:2` 會**整段** match 成一個 hit。
2. `resolveAnnotatable`（`:166-188`）按 `:` 切段後**只取最後兩段**當 `label:path`
   → 三段落嘅 ns 段變成 `kubejs/client_scripts/item_tooltips.js`（唔係 namespace）；
   `annotatable()`（`:202-225`）嘅 `.js/.snbt/.json` 檢查（`:216`）**只查 path 半邊**（`2`）→ 放行；
   `annotatableNamespace`（`:190-200`）5 條黑名單喺已經錯嘅 ns 上幫唔到手。
3. **冇任何「呢個 id 真係一件物品」嘅要求**：解唔到官方名都照加註 `（無官方名）`
   → peer 行（`labeled()` :136）同 fact 本體（`annotate()` :104）**兩邊都出垃圾**。
   ⚠️ 呢層係 v3 新寫嘅：v2 誤判成「`ITEM_ID` 冇 token 邊界 → 只 match 到碎片 `js:2`」，已被反方實測推翻（見 §2 撤回 P1）。

## 2. 修法（v3：重新排載重規則；**P1 撤回**）
- **⛔ 撤回 P1（token 邊界 lookbehind）**：反方實測 —— ① 真正喺生產環境洩漏嘅 token 係 standalone
  `(js:1 A)`／`(js:2 A)`（前面係 `(`），lookbehind **唔會 fire**；② 只用 P2/P3/P4 已經殺晒同一批
  `.js` 垃圾（`js:1`／`js:2`／`js:518`／`player_login.js:2`…），P1 **零邊際效益**；
  ③ P1 反而喺真 corpus 吞 **45 個真 id**（`packai:items`、`head:mace/mace`、`hilt:evil_universe`…）。
  → 唔做；記入 §5 撤回清單。（packs 內冇 token 邊界問題係因為 ITEM_ID 已要求至少一段 `:path`。）
- **P2（載重）檔名檢查施加於 resolved id 嘅每一段**：任何一段含 `.js`／`.json`／`.snbt` → 丟。
  （`item_tooltips.js` 呢類 ns 段就係咁死；反方核實 12,521 個真 registry id **零誤殺**。）
- **P3（載重）ns 段唔准含 `/`**（合法多段 id 仍然保留：`ino_dlc_build:music.build_henshin`、
  `mrqx_extra_pack:item/mystery_item`、`ino_dlc_build:item/item/build_phone` —— **3 個**，v2 寫「4 個」係數錯，已改）。
  ⚠️ v3 更正：舊版寫「P3 唔准大寫」**係死碼**——`resolveAnnotatable:170` 已經 `toLowerCase(Locale.ROOT)`，
  P3 永遠只見到細寫；所以 `{Count:1b,id:…}` **唔係** P3 殺（v2 寫錯，見撤回清單）。
- **P4（載重）ns 段黑名單（全細寫，因已 lowercase）**：原有 `item`／`source`／`tier`／`note`／`file`
  ＋ **v3 新增 `count`**（`:1b` 係咁死）＋ `mechanic`（真 trace 主要垃圾源，反方實測解碼 corpus **81 次**；
  v2 寫 58 冇命令已刪）＋ `via`／`held`／`gets`／`entity`／`table`／`gateway`／`structure`／`dimension`／`from`／`src`／`rel`。
- **P5（載重）peer 必須有官方名**：`collectPeers` 只收 `officialName(id)` 非空嘅項。
- **P5b（載重，覆蓋 fact 本體）**：`annotate()` 亦要求 `officialName(id)` 非空，否則**原文 token 保持不變**。
  理據＝SK 2026-09-14 規則字面（`references/official-display-names-and-sibling-contamination.md:5`：
  「**官方冇名 → 只寫原 id**」）——所以 P5b 唔係新政策，係原本規則嘅一致落實。
  ⚠️ **誠實聲明**：P5b **會改 fact 本體輸出文字**（唔再出 `（無官方名）`）→ 「只改 predicate、唔動 fact 格式」
  嘅講法收窄成「**語法唔變、內容有變**」。
- **共用**：`annotate()` 同 `collectPeers()` 用**同一套** predicate（唔可以只改一邊）。

## 3. 落地（＋還原方案）
- 改 `forge/1.19.2/src/main/java/com/skps9/packai/logic/OfficialDisplay.java`（P2–P5b）。
- ⚠️ **該檔現時 UNTRACKED**（我跑：`git status --porcelain` → `?? …OfficialDisplay.java`；
  `git log --all -- "*OfficialDisplay*"` → **空**）→ **`git revert` 唔係有效還原**（revert 一個「新增檔」嘅
  commit 只會**刪檔**，唔會回到今日行為）。
  **還原方案**：改前 `cp` 去 `%TEMP%\OfficialDisplay.java.bak-<YYYYMMDD_HHMMSS>` ＋記 `md5sum`；
  還原＝copy 返＋`md5sum` 對得上（純函式、無 cache／schema，還原後以 §4.1 harness 由紅變綠確認正常）。
- 只改 **forge 樹**：`OfficialDisplay.java` 全 repo 只此一份（`grep -rl "class OfficialDisplay"`），
  `neoforge/1.21.1/README_PAUSED.md` ⇒ 雙樹 lockstep 已 pause（SK 2026-09-14），**唔需要同步另一棵樹**。
- 全部經 cursor-agent（AGENTS.md 硬規則）；Hermes 只 plan／派工／親驗。

## 4. 驗收（可證偽，**紅先於綠，兩步分明**）

### 4.1 紅證據（Step 1：**只加 fixture，未改 predicate**）
**fixture 逐字真句（紅，全部由真 trace 抽出）**：
| # | 輸入（真句片段） | 修前必須出 | 修後必須唔出 |
|---|---|---|---|
| ① | `item:怪奇宝典（eccentrictome:tome） -[use]-> … (source:kubejs/client_scripts/item_tooltips.js:2 tier:A)` | peer 行含 `kubejs/client_scripts/item_tooltips.js:2（無官方名）` | 該 entry 消失 |
| ② | `… active_charm.1 (js:1 A)` | body 含 `(js:1（無官方名） A)` | body 唔含 `（無官方名）` |
| ③ | `… mrqx_events.js:518 (js:518 A)` | 同上（`js:518`） | 同上 |
| ④ | `mechanic:none` | 被 annotate | 唔 annotate（P4） |
| ⑤ | `{Count:1b,id:"minecraft:iron_sword"}` | `count:1b（無官方名）` | 唔 annotate（P4 `count`） |
**正向對照（唔准殺錯）**：`彩虹糖果（kubejs:colorful_candy）` 仍然出 peer；`ino_dlc_build:music.build_henshin`
／`mrqx_extra_pack:item/mystery_item`／`ino_dlc_build:item/item/build_phone` 仍然 annotate。
**stub lookup 必須有名**（否則 P5／P5b 會連正向對照一齊吞）。

```bash
# Step 1（紅）
python research/gen_tmp_check.py            # 自動為 *Check.java 生成 run<Class> task
cd forge/1.19.2 && ./gradlew.bat -I tmp-check.gradle runAskDisplayNameCheck \
  -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"   # 預期 AssertionError
# Step 2（綠）：改 predicate（P2–P5b）→ 重跑同一命令 → PASS
```
兩步**分開 commit／分開留證**，令 verifier 可以獨立重現紅。

### 4.2 斷言分層（**v3 修 R2 CRITICAL：唔准用 raw substring `js:`**）
- ❌ v2 寫嘅 peer 行斷言 `!contains("js:")` **物理上唔可能綠**：正向對照 `kubejs:colorful_candy` 內嘅
  `kubejs:` **本身就含 `js:`**（我實跑 `python -c "print('js:' in '彩虹糖果（kubejs:colorful_candy）')"` → `True`）。
- ✅ v3 斷言改成**語意式**：
  1. **peer 行**：按 `;` 切成 entry set → 每個 entry 嘅 id 部分（全角括號內）唔准 match
     `(?<![A-Za-z0-9_])js:\d+`、唔准含 `.js`／`.json`／`.snbt`、唔准含 `NO_OFFICIAL`；
     同時 entry 數 ≤ `PEER_CAP`（8）。
  2. **fact 本體**：唔准含 `NO_OFFICIAL`；**所有全角括號內嘅 id** 唔准 match 同一組 junk pattern
     （`js:\d+`、`\.js`、`mechanic:`、`count:\d+`）——因為 P5b 之後，凡有括號標註嘅都應該係有名嘅真 id。
  3. **正向對照**（§4.1）必須綠。
  4. 記低 R2 提過嘅殘餘弱點：P5b 之後 body 唔再出 `（無官方名）` → 「錯但可解析」嘅標註**冇 body 訊號**可測，
     所以 body 側要靠上面第 2 條嘅 junk pattern 清單（唔可以只靠 `NO_OFFICIAL`）。

### 4.3 閘（`tests/check_ask_display_leak.py`）
- **實測現況**：RC=**2**，輸出 `OK negative control (unfixed sample fails junk assertions)` ＋
  `NO LOG LINES (need real-machine smoke)`（`latest.log` 冇 `Pack AI display body ver=` 行）
  → 舊版寫「現時 RC=0」係錯；RC=2 唔可以當證據，要記成「要真機 smoke 才有意義」。
- 要加：**trace 模式**（`--trace <instance>/packai/trace`）＋ **日期 scope**（檔名 `ask-YYYYMMDD-*`；
  反方核實 39 個 trace **冇 buildId／ver 欄位** → 唔可以用 build scope，否則舊 trace 令閘永久紅）。
- ⚠️ 閘內嘅斷言形式**要同 §4.2 一致**（唔好用 `js:` raw substring，否則同一個 CRITICAL 會在閘度復發）。

### 4.4 回歸 baseline（**實測，v3 更正**）
- `tests/check_*.py` = **115 個**（v2 寫 110，錯）；逐個跑 = **113 PASS / 2 FAIL**：
  - `tests/check_ask_display_leak.py` → RC=2（＝本 plan 目標閘，見 §4.3）
  - `tests/check_howto_get_label_parity.py` → RC=1，`re.error: bad escape \z at position 150`（**pre-existing**，唔關本改動）
- 收貨＝**冇新增紅**（唔准要求全綠；亦唔准為咗綠而改 assert）。
- `AskMarkerIntegrityCheck` ＋ `tests/check_ask_marker_integrity.py` 綠；`compileJava compileTestJava` `BUILD SUCCESSFUL`。

## 5. 最壞情況／還原／撤回清單／已接受 false negative
- **最壞**：predicate 收得太緊 → 合法 peer／官方名被吞（守：§4.1 正向對照）／收得太鬆 → 洩漏照舊（守：§4.1 紅 fixture 逐字真句）。
- **還原**：見 §3（untracked 檔 → timestamped backup ＋ md5；唔係 `git revert`）。
- **撤回清單（要留痕，唔准靜靜 drop）**：
  1. `P1 token 邊界 lookbehind` —— 無邊際效益（P2/P3/P4 已殺同一批）＋ 吞 45 個真 id ＋ 真洩漏 token 前係 `(`。
  2. `（無官方名）` 係 fact 本體嘅**主要/唯一** body 訊號 —— P5b 之後 body 唔再出，body 側要靠 junk pattern 清單。
  3. `「現時該閘 RC=0」`（實測 RC=2）。
  4. `「4,321 檔／odd namespace=0」`（無可重跑命令；實測 9,144 檔／649 `.js`）。
  5. `整合包脚本（無官方名）`×10、`none（無官方名）`×6 —— parse 噪音，`collectPeers` 唔可能 emit（無冒號）。
- **已接受 false negative（v3）**：
  1. P5／P5b → 解唔到官方名嘅**合法非物品 registry id**（`minecraft:generic.attack_damage`、`forge:reach_distance`、
     `irons_spellbooks:summon_damage`…）**唔再被加註**（＝ SK 規則字面「官方冇名 → 只寫原 id」，但係有損資訊）。
  2. lookup 失效時（client 執行緒缺件／index 未載）同樣唔加註 → **輸出取決於 index 狀態**（已知，唔會出假名）。
- **數字（我自己重跑 2026-09-16 10:4x）**：kubejs `-type f` = **9,144**、`*.js` = **649**（v2 寫 648 錯）；
  含 peer header 行 **51**；帶（無官方名）entry 嘅**精確**數由 §4.3 trace mode 產出（我 crude parse 67 含噪音，唔作決策依據）。

## 6. Review record（逐輪）
| 輪 | 正方 | 反方 | 卡住嘅 LD | 本輪改咗咩 |
|---|---|---|---|---|
| R1 | 6 | 4 | — | 套 5 條 required_changes（斷言分層、紅證據命令、閘 RC=2＋trace/日期 scope、P2/P3/P4 收窄、P5b body 規則） |
| R2 | 5 | 5 | **LD1**（P1 無效＋誤殺 45 id）、**LD3**（P3 大寫係死碼）、**LD6**（斷言同正向對照互斥，永遠綠唔到） | **v3**：撤回 P1；P4 加 `count`；斷言改語意式（禁 raw `js:`）；補 5 條逐字真句 fixture；修 baseline（115/113/2）＋數字（649）＋還原方案（untracked→file backup）；刪 parse 噪音證據 |
| R3 | 待跑 | 待跑 | — | 有界輪：只核 R2 清單，唔准加新要求 |

### R2 → v3 逐條對應
| R2 項 | 落點 |
|---|---|
| ① 斷言要可滿足（唔准 raw `js:`） | §4.2（改成語意式 entry set／junk pattern）＋§4.3 尾句 |
| ② 唔可以當 P1 係修法；載重係 P5/P5b（＋P2/P4）；要加真句 fixture | §2（撤回 P1）＋§4.1（①②③ 真句） |
| ③ 修數字／baseline | §4.4（115/113/2＋兩個 FAIL 名同錯誤）＋§5 數字段＋§1 證據衛生 |
| ④ `{Count:1b}` 唔係 P3 殺（理由錯） | §2 P3 更正＋P4 新增 `count` |
| ⑤ fixture ② 唔具體、冇覆蓋真洩漏字串 | §4.1 表格（①–⑤ 逐字真句） |
| ⑥ LD7 兩點：P5b 實質改 fact 文字／untracked 唔可以 git revert | §2 P5b 誠實聲明＋§3 還原方案 |
