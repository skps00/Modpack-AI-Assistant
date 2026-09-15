# Plan α — 修正「同 tag 其他成員」行洩漏腳本路徑／垃圾 peer（bug fix，獨立細修）

> 觸發（真 trace 實錘）：`<instance>/packai/trace/ask-20260915-230340-kubejs_god_bless_full_necklace.jsonl:28` 出
> `同 tag 其他成員: kubejs/client_scripts/item_tooltips.js:2（無官方名）;`
> 實測 39 條 trace、共 **67 個 peer entry，64 個係垃圾**：
> `…item_tooltips.js:1（無官方名）`×24、`mechanic:none（無官方名）`×18、`整合包脚本（無官方名）`×10、`none（無官方名）`×6、`player_login.js:2（無官方名）`×3；
> **唯一合法者 = `彩虹糖果（kubejs:colorful_candy）`×3**。
> 玩家可見版本會變成「同 tag 其他成員: 整合包脚本（無官方名）」。

## 1. 根因（三層，全部真 code 核實）
1. `OfficialDisplay.ITEM_ID`（`:24-25`）冇 token 邊界 → 喺 `…item_tooltips.js:2` 內**只匹配到碎片 `js:2`**。
2. `resolveAnnotatable`（`:166-188`）按 `:` 切段後**只取最後兩段**當 `label:path` → 三段落嘅 `source:kubejs/client_scripts/item_tooltips.js:2` 令 **ns 段 = `kubejs/client_scripts/item_tooltips.js`**（唔係 namespace）。
3. `annotatable()`（`:202-225`）嘅 `.js/.snbt/.json` 檢查**只查 path 半邊**（例：`2`）→ 通過；`annotatableNamespace`（`:190-200`）5 條黑名單（item/source/tier/note/file）喺已錯嘅 ns 上幫唔到手。
4. `annotate()`（`:87-109`）同 `collectPeers`（`:144-159`）**共用同一 predicate** → 只改 collectPeers 會令 `（無官方名）` 繼續留喺 fact 本體（trace event[54]/[56] 已見 `(js:2（無官方名） A)`）。

## 2. 修法（predicate，逐條照做）
- **P1 token 邊界**：`(?<![A-Za-z0-9_./\\-])#?[a-z0-9_]+(?::[a-z0-9_./-]+)+(?![A-Za-z0-9_./-])` → 令 `js:26`／`js:2` 唔再 match（真反例 `server_scripts/common/player_login.js:2`）。
- **P2 檔名檢查施加於整個 resolved id（含 ns 段）**：id 任何段含 `.js`／`.json`／`.snbt` → 丟。
- **P3 ns 必須 `^[a-z0-9_.-]+$`**（唔准 `/`、唔准大寫）→ 令 `kubejs/client_scripts/…` 唔可能當 ns；**但保留**合法多段 id：`ino_dlc_build:music.build_henshin`、`mrqx_extra_pack:item/mystery_item`、`ino_dlc_build:item/item/build_phone`（實測 odd namespace = 0／4,321 檔）。
- **P4 黑名單擴充**（實測會漏嘅 fact key／NBT key）：`mechanic`（trace 58 次）、`via`、`held`、`gets`、`entity`、`table`、`gateway`、`structure`、`dimension`、`from`、`src`、`rel`，以及唔分大小寫 `Count`／`Damage`／`tag`／`id`（trace 真見 `{Count:1b,id:` 18 次 → 否則出 peer `count:1（無官方名）`）。
- **P5 peer 必須有官方名**：`collectPeers` 只收 `officialName(id)` 非空嘅項（實測 64/67 垃圾全部係「（無官方名）」）。
- **共用**：`annotate()` 同 `collectPeers()` 用同一套 predicate（唔可以只改一邊）。

## 3. 落地
- 改 `client/…/OfficialDisplay.java`（P1–P5）。
- **唔改**：`PackIndex`、`AskEngine` 決策、fact 內容格式（只改「邊啲 token 當得成 item／peer」）。

## 4. 驗收（可證偽，紅先於綠）
- **紅證據（修前）**：`AskDisplayNameCheck` 新增 fixture（抄真句）：
  ① `(source:kubejs/client_scripts/item_tooltips.js:2 tier:A)` ② `:2` 行號變體 ③ `{Count:1b,id:…}` ④ `mechanic:none`
  → 修前 harness **必須 FAIL**（現況 fixture 只有 2 段 `source:ftbquests/quests/demo.snbt`，ns 碰巧 == `source` → 意外通過，係漏洞走漏原因）。
- **正向對照（唔准殺錯）**：`彩虹糖果（kubejs:colorful_candy）` 必須**仍然**出 peer；`ino_dlc_build:music.build_henshin`、`mrqx_extra_pack:item/mystery_item` 必須仍然可以 annotate。
- **閘**：`tests/check_ask_display_leak.py` 加「peer 行唔准含 `.js`／`js:<n>`／`（無官方名）」＋掃 `packai/trace/ask-*.jsonl`（**按 build／日期 scope**，免舊 trace 永久紅；注意現時該閘 RC=0、latest.log peer=0，唔可以當證據）。
- **回歸**：現有 115 check 全綠；`check_ask_marker_integrity.py` 綠（`AskReplyScrub` 有 separator 坑）。

## 5. 最壞情況／還原
- 最壞：predicate 收得太緊 → **合法 peer／官方名被吞**（例如上面三個多段 id）→ 由正向對照守住；或收得太鬆 → 洩漏照舊（紅證據守住）。
- 還原：單一 commit revert；無 cache／schema 改動（`OfficialDisplay` 純函式，唔涉 cache）。
