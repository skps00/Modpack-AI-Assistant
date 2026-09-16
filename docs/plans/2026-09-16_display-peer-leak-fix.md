# Plan α — 修正「同 tag 其他成員」行洩漏腳本路徑／垃圾 peer（bug fix，獨立細修）

> 觸發（真 trace 實錘）：`<instance>/packai/trace/ask-20260915-230340-kubejs_god_bless_full_necklace.jsonl:28` 出
> `同 tag 其他成員: kubejs/client_scripts/item_tooltips.js:2（無官方名）;`
> 實測 39 條 trace、共 **67 個 peer entry，64 個係垃圾**：
> `…item_tooltips.js:1（無官方名）`×24、`mechanic:none（無官方名）`×18、`整合包脚本（無官方名）`×10、`none（無官方名）`×6、`player_login.js:2（無官方名）`×3；
> **唯一合法者 = `彩虹糖果（kubejs:colorful_candy）`×3**。
> 玩家可見版本會變成「同 tag 其他成員: 整合包脚本（無官方名）」。
>
> **狀態**：R1 反方 review 已回 —— **正方 6 : 反方 4**（方向正確、P1–P5 對已觀測垃圾完備、0 誤殺；3 處硬傷必修）
> → 本版已套用全部 5 條 `required_changes`（逐條對應見 §6）；待 R2 review。

## 1. 根因（三層，全部真 code 核實）
目標檔：`forge/1.19.2/src/main/java/com/skps9/packai/logic/OfficialDisplay.java`（253 行）
1. `ITEM_ID`（`:24`）冇 token 邊界 → 喺 `…item_tooltips.js:2` 內**只匹配到碎片 `js:2`**。
2. `resolveAnnotatable`（`:166-188`）按 `:` 切段後**只取最後兩段**當 `label:path` → 三段落嘅
   `source:kubejs/client_scripts/item_tooltips.js:2` 令 **ns 段 = `kubejs/client_scripts/item_tooltips.js`**（唔係 namespace）。
3. `annotatable()`（`:202-225`）嘅 `.js/.snbt/.json` 檢查**只查 path 半邊**（例：`2`）→ 通過；
   `annotatableNamespace`（`:190-200`）5 條黑名單（item/source/tier/note/file）喺已錯嘅 ns 上幫唔到手。
4. `annotate()`（`:87-109`）同 `collectPeers`（`:144-159`）**共用同一 predicate** → 只改 collectPeers 會令
   `（無官方名）` 繼續留喺 fact 本體（trace event[54]/[56] 已見 `(js:2（無官方名） A)`）。

## 2. 修法（predicate，逐條照做）
- **P1 token 邊界**：`(?<![A-Za-z0-9_./\\-])#?[a-z0-9_]+(?::[a-z0-9_./-]+)+(?![A-Za-z0-9_./-])`
  → 令 `js:26`／`js:2` 唔再 match（真反例 `server_scripts/common/player_login.js:2`）。
  **已知 false negative**：緊接 `-` 嘅合法 id 唔再 annotate（39 條 trace 未見實例）→ 見 §5。
- **P2 檔名檢查施加於整個 resolved id 嘅「每一段」**（唔止 path 半邊）：任何一段含 `.js`／`.json`／`.snbt`
  → 丟（`kubejs/client_scripts/item_tooltips.js` 呢類 ns 段就係咁死）。
- **P3 ns（切段後嘅 label 段）必須 `^[a-z0-9_.-]+$`**：唔准 `/`、唔准大寫。
  **但保留**合法多段 id：`ino_dlc_build:music.build_henshin`、`mrqx_extra_pack:item/mystery_item`、
  `ino_dlc_build:item/item/build_phone`（實測 4 個多段 id 全部合法，見 §4 正向對照）。
- **P4 黑名單擴充，只作用於 ns 段、只列小寫**：`mechanic`、`via`、`held`、`gets`、`entity`、`table`、
  `gateway`、`structure`、`dimension`、`from`、`src`、`rel`（`mechanic` 喺真 trace 出現 58 次，係主要 load-bearing 項）。
  ⚠️ **R1 更正**：舊版列出嘅大寫 `Count`／`Damage`／`tag`／`id` 係**死碼**——`resolveAnnotatable` 已經
  `toLowerCase()`，加上 P3 唔准大寫 ns，所以 `{Count:1b,id:…}` 係由 **P3** 殺，唔係 P4。名單唔再列大寫項。
- **P5 peer 必須有官方名**：`collectPeers` 只收 `officialName(id)` 非空嘅項（實測 64/67 垃圾全部係「（無官方名）」）。
  ⚠️ **R1 更正**：P5 **只守 peer 行**，`annotate()`（fact 本體）冇對應規則 → 任何過得 P1–P4 但解唔到 hover 名嘅 id
  （實測存在：`additional_attributes:spell_general`、`minecraft:generic.attack_damage`、`forge:reach_distance`、
  `irons_spellbooks:summon_damage`）一旦落入 fact 本體就會照出「（無官方名）」，而單查 peer 行嘅閘會**靜默過關**。
  → **P5b（新增，覆蓋 fact 本體）**：`annotate()` 亦要求 `officialName(id)` 非空，否則**唔加註**（原文 token 保持不變）。
- **共用**：`annotate()` 同 `collectPeers()` 用**同一套** predicate（唔可以只改一邊）。

## 3. 落地
- 改 `forge/1.19.2/src/main/java/com/skps9/packai/logic/OfficialDisplay.java`（P1–P5b）。
- **唔改**：`PackIndex`、`AskEngine` 決策、fact 內容格式（只改「邊啲 token 當得成 item／peer／annotation」）。
- 全部經 cursor-agent（AGENTS.md 硬規則），Hermes 只 plan／派工／親驗。

## 4. 驗收（可證偽，**紅先於綠，兩步分明**）

### 4.1 紅證據（Step 1：只加 fixture，未改 predicate）
```bash
# (0) 確認 harness 任務存在（gen 會讀 *Check.java 自動生成 task）
python research/gen_tmp_check.py
# (1) 跑 harness —— 預期 AssertionError（紅）
cd forge/1.19.2 && ./gradlew.bat -I tmp-check.gradle runAskDisplayNameCheck \
  -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
```
**Step 2**：改 predicate（P1–P5b）→ **重跑同一命令** → PASS。兩步要分開 commit／分開截圖，
令 verifier 可以獨立重現紅（唔可以一次改晒再宣稱紅過）。

**fixture（`AskDisplayNameCheck.java:55-75`，抄真句）**：
① `item:…（focus） -[use]-> … (source:kubejs/client_scripts/item_tooltips.js:2 tier:A)`（真實 3 段變體）
② `:2` 行號變體 ③ `{Count:1b,id:…}` ④ `mechanic:none`
→ 修前**必須 FAIL**（現況只有 2 段 `source:ftbquests/quests/demo.snbt`，ns 碰巧 == `source` → 意外通過，
呢個就係漏洞走漏原因）。

### 4.2 斷言分層（R1 required #1；現況寫法自相矛盾）
- **peer 行**（`enrichFacts` 回傳中以 `OfficialDisplay.PEER_HEADER` 開頭嗰個元素）：
  斷言 `!contains(".js")` ＋ `!contains("js:")` ＋ `!contains(OfficialDisplay.NO_OFFICIAL)`。
- **fact 本體**：**只**斷言 `!contains(NO_OFFICIAL)`（P5b 生效後成立）。
  ⚠️ **唔可以**對 body 禁 `.js`／`js:` —— 修好後 raw 腳本路徑按 §3 仍然原文留低，嗰個斷言**永遠綠唔到**。
- **正向對照（唔准殺錯）**：`彩虹糖果（kubejs:colorful_candy）` 必須**仍然**出 peer；
  `ino_dlc_build:music.build_henshin`、`mrqx_extra_pack:item/mystery_item`、`ino_dlc_build:item/item/build_phone`
  必須仍然可以 annotate。**stub lookup 必須有名**，否則 P5 會連正向對照一齊吞。

### 4.3 閘（R1 required #3：實測 RC 更正 ＋ scope）
- **實測現況**：`python tests/check_ask_display_leak.py` → **RC=2**，輸出
  `NO LOG LINES (need real-machine smoke)`（latest.log 冇 `Pack AI display body ver=` 行）。
  → 舊版寫「現時該閘 RC=0」**係錯**；RC=2 唔可以當證據，要記錄成「需要真機 smoke 才有意義」。
- 閘要加：**trace 模式**（`--trace <instance>/packai/trace`，斷言 `同 tag 其他成員:` 行唔准含
  `.js`／`js:<n>`／`（無官方名）`）＋ **日期 scope**（用檔名 `ask-YYYYMMDD-*` 過濾；**唔可以用 build scope**
  ——trace event 冇 buildId，舊 trace 會令閘永久紅）。
- 閘亦要加 **fact 本體禁「（無官方名）」**（覆蓋 P5 守唔到嘅殘留 ＝ §4.2 body 斷言同源）。
- 判「有冇 regress」要對 baseline worktree 對跑（AGENTS.md），**唔准為咗綠而改 assert**。

### 4.4 回歸
- 現有 `tests/check_*.py` 全數（baseline 110/110）**冇新增紅**；判 baseline 要用 worktree 對跑。
- `AskMarkerIntegrityCheck` ＋ `tests/check_ask_marker_integrity.py` 綠（`AskReplyScrub` separator 坑）。
- `compileJava compileTestJava` `BUILD SUCCESSFUL`。

## 5. 最壞情況／還原／已接受 false negative
- 最壞：predicate 收得太緊 → **合法 peer／官方名被吞**（守：§4.2 三個正向對照）／收得太鬆 → 洩漏照舊（守：§4.1 紅 fixture）。
- **還原**：單一 commit revert；`OfficialDisplay` 係純函式、無 cache／schema 改動。
- **已接受 false negative（要明寫，唔係「唔知」）**：
  1. P1 lookbehind 含 `-` → 緊接減號嘅合法 id 唔會 annotate（39 條 trace 未見實例）。
  2. P5／P5b → 解唔到官方名嘅**合法非物品 registry id**（例如 `minecraft:generic.attack_damage`）會變成**唔加註**；
     呢個係想要嘅行為（唔好出（無官方名）），但要記住係有損資訊。
- **數字更正（R1 實測）**：舊版寫「4,321 檔／odd namespace=0」**對唔上**。實測本機 instance kubejs：
  `find <instance>/minecraft/kubejs -type f | wc -l` = **9,144**；`-name "*.js" | wc -l` = **648**。
  「odd namespace = 0」冇可重跑命令 → **本版刪咗該宣稱**，只保留已逐個核實嘅 4 個多段 id（§2 P3）。

## 6. R1 review → 已套用嘅修改（逐條對應）
| R1 required_change | 落點 |
|---|---|
| ① 斷言分層＋紅 fixture | §4.1、§4.2（peer 行／body 分開；fixture ①②③④；正向對照要 stub 有名） |
| ② §4 紅證據寫實命令與次序、兩步分明 | §4.1（`gen_tmp_check.py` → `-I tmp-check.gradle runAskDisplayNameCheck`；Step1 紅／Step2 綠分開） |
| ③ §4 閘 RC 更正（RC=2）＋trace 模式＋日期 scope＋body 規則 | §4.3 |
| ④ P4 只作用 ns 且只列小寫；P2 全段；P3 小寫無斜線；harness 反例清單 | §2 P2–P4（＋刪大寫死碼項）、§4.1–4.2 |
| ⑤ P5 只守 peer 行 → 補 body 規則；列出會吞嘅合法 peer；刪／補「4,321 檔」數字 | §2 P5b、§5（兩條已接受 false negative、9,144／648 實測數字） |
