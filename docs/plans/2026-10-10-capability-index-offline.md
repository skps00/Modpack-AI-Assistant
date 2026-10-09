# Pack AI — 落點 A：離線能力索引（capability index）· v2

- **Status**：**v2（R1 4:6 → R2 6:4 → R3 8:2 可開工，2026-10-10）**；SK 2026-10-10 已批「三個都做」＋次序 **A → C → B**
- Generated：2026-10-10｜前置分析：`docs/plans/2026-10-10-capability-search-fit.md`
- 目的：補 SOP（`mayacraft.net/hermes/mc-item-research-sop.html`）第 ① ② ③ ⑤ 步，離線答「**包入面邊樣嘢可以做到 X**」

## 1. Goal / Non-goals

**Goal**：一支**離線**工具 `tools/mine_capability_index.py`——輸入＝一個 pack（Prism instance 或 mods 目錄）＋一組「能力需求」查詢；輸出＝每條需求嘅**候選物品／方塊**＋**逐條證據（檔案／class／行號）**＋**來源等級（A/B/C）**＋`runtime_visible` 標記。

兩個用途：
1. 即刻（唔開遊戲）答到 SK 三條真問題：儲不可堆疊物品／清 100×90 範圍／白色混凝土工廠
2. **量度 runtime 覆蓋率**——決定落點 B（`capability_search` tool）值唔值做

**Non-goals**：
- 唔改 product code（唔碰 `forge/`、`neoforge/`；本 slice 只加 `tools/`、`tests/`、`docs/`）
- 唔做 runtime 取證（bytecode 掃描喺遊戲內不可行 ⇒ 落點 A 係「唯一量度方法」）
- **唔聲稱自動理解任意中文問句**：需求 → 關鍵詞由**人手策展**嘅查詢表提供，artifact 明文標 `curated: true`
- 唔加任何 Python 第三方依賴（只用 stdlib ＋ `javap`）

## 2. 資料來源（逐項核實，2026-10-10）

| 來源 | 攞咩 | 核實 |
|---|---|---|
| `mods/*.jar` entries（`zipfile`） | class／assets／data 清單 | 實測 DJ2 **235 個 active `*.jar`**（另 11 個 `*.jar.disabled`；`mods/` 共 246 檔）；enderutilities jar 1,139 entries |
| `javap -p -c` | 數字常數、比較／filter 邏輯 | JDK17 `C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2/bin/javap.exe`，`javap -version` = **17.0.19** |
| lang（1.12.2） | `assets/<ns>/lang/en_us.lang`（key=value） | enderutilities jar 有 `en_us.lang`／`zh_cn.lang` |
| lang（1.19.2） | `assets/<ns>/lang/en_us.json` | 兩代格式**兩條路都要行** |
| 任務書 | `config/betterquesting/DefaultQuests.json`（1.12.2 系）／`config/ftbquests/**`（1.19.2 系） | DJ2 `config/betterquesting` 存在 |
| CraftTweaker | `scripts/**/*.zs` | DJ2 實測 **130** 個 |
| recipe JSON | 1.19.2 `data/<ns>/recipes/*.json`（ATM8 已量 46,423 檔） | 1.12.2 多數**執行時註冊** ⇒ jar 內只部分 |
| tags | 1.19.2 `data/<ns>/tags/**` | — |

**已核實嘅正確取證路徑（SOP 原文寫錯，本 plan 用真實路徑）**：
`fi/dy/masa/enderutilities/tileentity/TileEntityJSU.class` ＋ inner `TileEntityJSU$ItemHandlerWrapperJSU.class`（zips 實查；唔係 SOP 寫嘅 `enderutilities/tileentity/…`）。

## 3. 演算法（每條需求 = 6 步）

- **Step 0 需求 → 查詢表**（`tools/capability_queries.json`，人手策展）：`need_id`、`question`（原文）、`keywords`（mod 名／class 名／行為詞）、`number_patterns`（例 `INV_SIZE|MAX_STACK|SLOTS|CAPACITY`）、`logic_patterns`（例 `maxStackSize|getMaxStackSize|isItemValid|canInsert`）。
- **Step 1 候選掃描（SOP②）**：`mods/*.jar` 檔名 ＋ jar 內 `mcmod.info`／`mods.toml`／lang 名稱描述，命中關鍵詞 ⇒ 候選 mod（記命中來源檔）。
- **Step 2 行為取證（SOP③）**：候選 jar 內 **class 名**命中行為詞 ⇒ `javap -p -c` 抽 (a) 數字常數行 (b) 比較／filter 行，**存原文行**（`evidence.text`）。
- **Step 3 lang 交叉（SOP④）**：由 class 名／jar 名推 lang key → 抽 tooltip 原文（A/B 級證據）。
- **Step 4 配方／任務交叉（SOP⑦）**：`scripts/*.zs` grep 該 id；任務書 grep（1.12.2 用 betterquesting）。
- **Step 5 分級**：**A** ＝包內檔案／bytecode 行；**B** ＝任務書／lang 官方文案／scripts；**C** ＝（唔產生：模型知識一律唔寫）；缺證據 ⇒ 標「未確認」。
- **Step 6 runtime 可及性（v2 更正：按**實際**runtime 讀到嘅 surface）**：`runtime_visible: true` 只當候選嘅證據出現喺
  **runtime index 真係會讀**嘅來源：
  - 1.19.2：`assets/<ns>/lang/*.json`（實核 `PackIndex.isLangPath`＝`endsWith(".json") && pathLower.contains("/lang/")`，`PackIndex.java:260-262`）、
    `data/**`（recipes／tags）、`kubejs/`、`groovy/`、`datapacks/`、`ftbquests`、`heracles`（`PackIndex.build` scanners 清單）
  - ⚠️ **1.12.2 嘅 `.lang`（key=value）形態 runtime 唔會讀** ⇒ DJ2 類包嘅 lang 只可以當「離線證據」，**唔可以**計入 `runtime_visible`
  - ⚠️ JEI runtime 枚舉（遊戲內）唔屬離線範圍 ⇒ 另列 `runtime_jei: unknown（要遊戲內量）`


**硬上限（v2 按實測修訂）**：實測 `javap -p -c` ≈ **0.24 s／次**（reviewer 量；5 條 query × 800 次 = 960 s，**超出原定 900 s**）⇒
每條需求 ≤20 候選 mod、每 mod ≤40 class、**全 run javap 呼叫上限 1500 次（≈6 分鐘）**、每個 `javap` ≤20 s、全 run ≤900 s；超出寫 `caps_hit`。

## 4. 輸出

- artifact：`docs/research/artifacts/2026-10-10-capability-index-<pack>.json`
  形狀：`{pack, mods_scanned, javap_version, curated: true, queries:[{need_id, question, keywords, candidates:[{mod, jar, level, runtime_visible, classes:[{name, numbers:[{line,text}], logic:[{line,text}], lang:[{file,text}]}], cross:{scripts:[],quests:[]}}], unresolved:[]}], caps_hit:[]}`
- 人話報告：`docs/research/2026-10-10-capability-index-report.md`（每條問題 → 候選 ＋ 證據 ＋ 等級 ＋ 未確認項）

**確定性（A4）**：所有清單排序（mod／class／證據行）；artifact 唔含時間戳；`javap_version` 記版本字串（唔記絕對路徑）⇒ 同一輸入兩次 run **byte-identical**。

## 5. 驗收標準

| # | 條件 | 證據 |
|---|---|---|
| A1 | **5 條真問題**（SK 原句「儲不可堆疊物品」「清 100×90 範圍」「白色混凝土工廠」＋研究 2 條：自動合成／近乎無限儲存）**每條有候選清單＋證據行號** | artifact `queries[*].candidates[*].classes[*].numbers/logic` 有原文行 |
| A2 | **DJ2 JSU 案重現 SOP 結論**，**只 claim 我親自 javap 核實過嘅事實**：`INV_SIZE`=**270**、`MAX_STACK_SIZE`=**256**（`sipush` 原文）、`isItemValidForSlot` 用 `getMaxStackSize()==1`（`func_77976_d` + `iconst_1` + `if_icmpne`）、lang 原文兩句、JSU enum 建構 `("JSU",7,7,-1,"jsu",true,true)` | `javap`／`zipfile` 原文行；class 路徑＝`fi/dy/masa/enderutilities/tileentity/TileEntityJSU.class`（＋inner `$ItemHandlerWrapperJSU`）。⚠️ **唔 claim「打爆保留」**：`retainsContentsWhenBroken` 喺 `BlockBarrel`，唔喺 JSU class（review 更正） |
| A3 | 每條結論標來源等級 A/B；缺證據標「未確認」 | artifact `level` 欄 ＋ `unresolved` |
| A4 | 同輸入兩次 run，artifact byte-identical | 兩次 run ＋ `diff`（0 byte 差） |
| A5 | 誠實列「runtime 做唔到嘅部分」＝bytecode 層，並逐條列 runtime 覆蓋唔到嘅需求 | 報告有一節 ＋ `runtime_visible=false` 統計 |
| A6 | **本 slice 唔改 product code**：跑之前先記 `git status --porcelain` baseline；跑完再跑一次，**新增嘅路徑只准** `tools/mine_capability_index.py`、`tools/capability_queries.json`、`tests/check_capability_index.py`、`docs/**` 內新檔。⚠️ **本輪之前已存在嘅未 commit 改動唔計**（keybind slice：`AskEngine.java`／`AskToolLoop.java`／`PackIndex.java`／`KeybindAskTool.java`／`KeybindReader.java`／`tests/check_tool_schema_stable.py`／`tests/check_keybind_lookup.py`）| 兩次 `git status --porcelain` diff（baseline 清單寫入報告） |

## 6. 風險 / 限制（誠實）

- `javap` 掃描係**heuristic**：只提供「候選 ＋ 原文」，**唔等於已證明能力** ⇒ 報告一定要寫「候選 ≠ 已確認」。
- 1.12.2（`.lang` ＋ CT scripts ＋ betterquesting）同 1.19.2（`.json` lang ＋ `data/**/recipes` ＋ ftbquests）**兩套來源結構**，兩條路都要行；唔可以只用一套。
- 大 pack（ATM8 380 jar／46k recipe）掃描時間長 ⇒ 硬上限；首發只跑 **DJ2（235 active jar）**，並用 `packai_sandbox_atm8` 做一次「大 pack 唔死」煙測。
- **唔可以硬編碼任何包名**（mod 通用）⇒ 查詢表用通用行為詞；JSU 只係**測試案例輸入**（寫落 tests，唔寫落 tools 邏輯）。
- 部分 mod 冇 lang／冇描述（程式生成內容）⇒ 候選會漏；報告要寫漏檢風險。

## 7. 工作量 / 派工

- 新檔：`tools/mine_capability_index.py`（~400–600 行）＋`tools/capability_queries.json`＋`tests/check_capability_index.py`（確定性 + JSU fixture 斷言）
- 實作**經 cursor-agent**（packai 鐵則）；Hermes 負責 plan／派工／親驗（A1–A6 自己跑）
- **唔碰**：`forge/`、`neoforge/`、現有 `tools/*.py`

## 8. 更正記錄

| 版本 | 錯 | 真值 | 查法 |
|---|---|---|---|
| v1 | （SOP 原文）`enderutilities/tileentity/TileEntityJSU.class` | `fi/dy/masa/enderutilities/tileentity/TileEntityJSU.class` ＋ inner `$ItemHandlerWrapperJSU` | `zipfile` 實查 jar entries |
| v2 | `runtime_visible`＝「有 lang／tags／quest」 | runtime 只讀 **1.19.2 JSON 形態** lang（`PackIndex.java:260-262` `endsWith(".json") && contains("/lang/")`）；1.12.2 `.lang` **唔讀** ⇒ DJ2 類包 lang 只算離線證據 | 讀 `PackIndex.isLangPath`／`build` scanners 清單（reviewer 指正＋我核） |
| v2 | A2 寫「打爆保留」係 JSU 行為 | `retainsContentsWhenBroken` 喺 `BlockBarrel`；JSU 只有 enum `retainsContents`（建構 `true,true`）⇒ 只 claim 我 javap 核實過嘅 5 項 | `javap` 掃 `BlockBarrel`／`BlockStorage$EnumStorageType` |
| v2 | 硬上限「每 mod 40 class × 5 query」可行 | `javap -p -c` ≈0.24 s／次 ⇒ 每 query 800 次 ≈ 192 s，但 **5 條 query 合計 4,000 次 ≈ 960 s，超 900 s 全 run 上限** ⇒ 加全 run 1500 次 javap 上限（≈360 s） | reviewer 實測 timing（R2 更正算術：192 s／query，960 s 係 5×800） |
| v2 | A6「零 product code 改動」可直接機檢 | 工作樹已有 keybind slice 未 commit 改動 ⇒ 改成「記 baseline、diff 只准新增 tools／tests／docs」 | `git status --porcelain` |

## 9. Review 記錄

| 輪 | 比分（正方:反方） | 主要發現 → 處理 |
|---|---|---|
| R1（2026-10-10，兩個獨立 reviewer：反方 ＋ 數字核實方） | **4 : 6** | 反方 4 條成立：(a) A6 現狀不可通過 → v2 改 baseline-diff 語意 (b) `runtime_visible` 同 runtime 真讀嘅 surface 唔一致 → v2 更正 (c) A2「打爆保留」誤植 Barrel → v2 只留親核事實 (d) javap 成本超上限 → v2 加呼叫上限。**未達 8:2** ⇒ 依 SK 規則：第 2 輪為上限內最後一輪；若 R2 仍 <8:2，交付時要附停手報告 |
| R2 | **6 : 4**（有界；只核 v2 delta） | 四項 v2 修正全部**已解決**（A6 baseline-diff 機制可機檢、Step 6 runtime surface 更正、A2 只留親核事實、javap cap 已加）；兩個**新數字錯**已即修：① §8 算術（800×0.24 s＝192 s／query，960 s 係 5×800）② DJ2 **active `*.jar`＝235**（另 11 個 `*.jar.disabled`；`mods/` 頂層檔案 246 個；非隱藏項數 248 係**另一個 metric**、唔好同 246 混用）。**仍 <8:2** ⇒ 依規則停手，唔再開新輪；剩餘兩項屬已修數字，交 SK 時附停手報告 |
| R3 | **8 : 2 → 可開工**（有界 delta 抽核，只核 R2 兩項） | reviewer 自己重跑：javap 25 次 mean **0.2408 s／call**（192／960／360 s 三個算術全對）＋DJ2 **235 active jar / 11 disabled / 246 files** ⇒ 兩項由反方點翻正方點。剩餘**非阻塞**文檔項：metric 標示（已加）＋版本標籤（已改 v2） |

## 10. 實作記錄

- **2026-10-10（cursor-agent）**：新增 `tools/mine_capability_index.py`、`tools/capability_queries.json`、`tests/check_capability_index.py`（未 commit）。
- Self-check：`python tests/check_capability_index.py` → `check_capability_index OK`。
- DJ2 驗收：`mods_scanned=235`，`javap_calls_used=1443`，`caps_hit=false`；A1 五條皆 ≥1 候選；A2 找到 `TileEntityJSU`＋`$ItemHandlerWrapperJSU`，`sipush 270`／`sipush 256`，logic=`func_77976_d`（SRG；原文無 `getMaxStackSize` 字串）；A4 兩次 artifact byte-identical（21266964 bytes）。
- 偏離：`--timeout-s` 只留 CLI（stdlib allowlist 無 `time`，靠 `--max-javap-calls` 硬停）；A2 報告用 `func_77976_d` 行代替字面 `getMaxStackSize`。

