# Pack AI — 按鍵查詢（keybind）· v4

- **Status**：**STOPPED（2026-10-10）— 等 SK 決定**。R4 反方 6 : 正方 5，上限（3–4 輪）已用盡 → 按 SK 規則停手，唔開第 5 輪（見 §10 停手報告）
- Generated：2026-10-10｜SK 指示：按次序一個一個做
- Loaders：Forge 1.19.2（NeoForge 暫停）
- **v4 相對 v3 嘅三項實質修改**：① 加 **post-LLM 強制可見**（R3 反方 HIGH）② **觸發規則用真數據量度**（R3 HIGH：A1 問句同自家規則唔匹配）③ 更正 3 個行號

## 1. Goal / Non-goals

**Goal**：答「要撳咩掣先用得到 X」／「我個掣撞咗」／「呢個功能未綁掣」，資料來自遊戲內即時狀態。

**Non-goals**：改玩家按鍵｜猜未綁功能預設鍵｜NeoForge 樹｜新 UI｜**新增 model-visible tool**

**價值定位（誠實）**：本 mod 自己個鍵**已經**喺 tooltip 顯示（`client/tooltip/PonderStyle.java:28-29` 用 `getTranslatedKeyMessage()`）；vanilla Controls 亦可查。**本功能獨有價值 ＝ ① 其他 mod 嘅鍵（380 mods 要逐個揾好痛苦）② 衝突偵測 ③ 未綁清單**，全部喺遊戲內一句問到。

## 2. 資料來源（逐項核實）

| 來源 | 攞咩 | 核實 |
|---|---|---|
| `Minecraft.getInstance().options.keyMappings` | 全部功能＋現況 | `javap`：`public net.minecraft.client.KeyMapping[] f_92059_;`（**public、非 final**） |
| `KeyMapping#getName()` | 翻譯 key | tsrg `m_90860_` |
| `KeyMapping#getTranslatedKeyMessage()` | 目前按鍵顯示 | tsrg `m_90863_`；本 repo 已有同款用法：`client/tooltip/PonderStyle.java:29` |
| `KeyMapping#isUnbound()` | 未綁 | tsrg `m_90862_` |
| `KeyMapping.ALL` | **唔用** | 存在（`f_90809_`，tsrg:14858）但 **private**；`f_90810_`＝Forge `KeyMappingLookup`（private） |

**離線 oracle** `tools/extract_keybinds.py`（只作離線盤點，唔可直接比數量）。實測（親手重跑）：ATM8 258 登記／0 綁定／0 衝突（`options.txt` 係 2023 舊檔）；StarTech 148／139／60／**12 衝突**；NFWC 201／272／72／**11 衝突**。

## 3. 觸發規則（v4 新增：真數據量度，唔再口頭保證）

規則集（7 族）**用 589 條真實玩家問題量度**（corpus：Stack Exchange API, 4 個 tag, 按票數；artifact `docs/research/artifacts/2026-10-10-question-corpus.json`）：

| 族 | 正則（節錄） |
|---|---|
| zh·按鍵／快捷鍵／熱鍵 | `按鍵\|按键\|快捷鍵\|快捷键\|熱鍵\|热键` |
| zh·撳掣／咩掣／按咩掣 | `撳\s?(咩\|乜\|邊個\|边个)?\s?掣\|按咩掣\|按咩鍵\|按什麼鍵\|按哪個鍵\|按哪个键\|咩掣\|乜掣\|邊個掣` |
| zh·改鍵／綁定 | `改鍵\|改键\|綁鍵\|绑键\|綁定按鍵\|按鍵設定\|冇綁\|沒綁\|未綁` |
| zh·撞掣／衝突 | `(掣\|鍵\|键)[^。！？]{0,8}(撞\|衝突\|重复\|重複)\|同一個(掣\|按鍵)` |
| EN·keybind／hotkey／rebind | `key\s?bind\|keybind\|hot\s?key\|hotkey\|rebind` |
| EN·which／what key∥button | `(which\|what)\s+(key\|button)` |
| EN·change the key | `change the .{0,12}key` |

**量度結果**（artifact `docs/research/artifacts/2026-10-10-keybind-intent-probe.json`）：
- **正向回收：18/18 ＝ 100%**（16 條原例 ＋ 2 條補例；v3 規則只有 13/16＝81%，漏「撳咩掣」「咩掣」「個掣撞咗」三類 → 已補）
- **誤觸：589 條真問題中 1 條（0.17%）**，而該條（`Changing the "drop all"/"drop stack" keybind in Minecraft`）**人手核對後確認係真按鍵問題** → 此 corpus 上**誤觸 0**
- 規則**唔用單字「用」**（避免同 `PackIndex.java:1131 isPurposeQuestion` 撞）

## 4. 實作範圍

| # | 檔 | 改咩 |
|---|---|---|
| 1 | `logic/KeybindFacts.java`（新，純邏輯） | `format(rows, query, lang) → String`：砌**有上限**行（cap 8，超出寫「另有 N 項」）；同鍵 ≥2 功能標衝突；未綁標記。唔准自譯／創作 |
| 2 | `client/context/KeybindReader.java`（新） | `snapshot()`：由 live `options.keyMappings` 讀三樣；**例外／未 init → 空 list** |
| 3 | `logic/PackIndex.java` | `isKeybindQuestion(String)`（§3 規則集；**喺 purpose 判斷之前**） |
| 4 | `logic/AskEngine.java`（FACT 組裝區 :571-612；`machineAsk` :575；`machineLines` :609） | `keybindAsk` 為真且 block 非空 → 加入 blocks（同 `machineLines` 同級） |
| 5 | **`logic/AskEngine.java` §post-LLM 區（:921-931）** | **v4 新增（R3 HIGH）**：喺既有強制可見鏈（`:921 proseOrFacts` → **`:928 RecipeGetMarks.ensureVisibleInReply(body, machineSection, lang)`** → `:930 AskJeiHints.ensureQuestStatusVisible`）加入 **`ensureKeybindVisible(body, keybindSection, lang)`**；**同時**把 keybind 段併入 `playerFacts`（`:734 AskReplyScrub.playerSafeFacts(purposeFactLines, acquire)`）→ 保證 LLM 走 prose／fail 路徑時**唔會消失** |
| 6 | `logic/ReplyLang.java` | `sectionKeybind(String code)`（跟 `:988/:992/:996` 同款） |
| 7 | lang ×3 | `packai.reply.section.keybind`（例 `【按鍵】`）＋ miss 文案 |
| 8 | `tests/check_keybind_facts.py`（新） | mirror：衝突分組／未綁／行數上限／query 篩選／miss 文案；**另加意圖規則 fixture**（§3 嘅 18 正例 ＋ 589 反例抽樣） |

**唔碰**：`AskTool`／`AskToolLoop`（CAPABLE_TOOLS 39-42、ALLOWLIST :44、register 靜默 return :141-143）／`tests/check_tool_schema_stable.py`／現有 14 個 tool／NeoForge 樹。

## 5. 驗收標準

| # | 條件 | 證據（**以玩家可見 body 為準**，非 trace facts） |
|---|---|---|
| A1 | 問**含關鍵詞**嘅問句（例「呢個 mod 嘅快捷鍵係咩？」）→ 答到真掣 | 玩家可見回覆含 `【按鍵】`／`[Keybind]` 段＋`getTranslatedKeyMessage()` 實際字串 |
| A1b | **規則表**：18 正例全中、corpus 誤觸 ≤1 | `tests/check_keybind_facts.py` 內 fixture 直接跑（可重現） |
| A2a | 衝突清單內部自洽（列出嘅 key 真係 ≥2 功能） | mirror 測試對真 trace 斷言 |
| A2b | 抽樣 5 組衝突人工核對（Controls 截圖 vs 答案） | 5 截圖＋trace 並排 |
| A3 | 未綁 → 講「未綁／去設定綁」 | body 出現未綁標記 |
| A4 | 唔存在功能 → **唔准作** | body 出現 miss lang key（三語齊） |
| A5 | 跨包：StarTech＋NFWC（12／11 組衝突）；ATM8 只驗掣名 | 3 個 trace |
| A6 | 冇新洩漏 | 既有 `tests/check_ask_display_leak.py`（`--trace`）對 baseline 零新增紅 |
| A7 | compile＋測試 | `compileJava` OK；`tests/check_*.py` 同 baseline 一致 |

## 6. 風險 / 限制

| 項 | 內容 |
|---|---|
| LLM 掩掉 block | **已由 §4#5 處理**（post-LLM 強制 + playerFacts）—— R3 HIGH |
| 觸發漏／誤 | **已量度**（§3）；仍會列入 mirror fixture，改規則要重跑數字 |
| 模組名 | runtime 攞唔到註冊者 → 由翻譯 key namespace 推（`key.jade.toggle`→`jade`）＋`ModList.get().getModContainerById`（repo 已用 `client/context/GameContextCollector.java:16,36`）；查唔到只顯示 raw key id，標明「依 namespace 推斷」 |
| 語言缺字串 | 顯示 raw key id，唔准自己譯 |
| Context | block 上限 8 行；**唔加 tool schema** → prompt 影響有界 |
| 未 init／例外 | `snapshot()` 回空 → 唔加 block → 走原路徑 |
| 部署 | 只准 `hermes/scripts/mc_mod_deploy_jar.py --target packai`（遊戲開住 REFUSED）；換前備份 `%TEMP%`；換後驗 sha256；只郁沙盒 |
| Rollback | `git revert <slice commit 範圍>`＋jar 還原自備份驗 sha |
| 機會成本 | 研究 §9.6 需求排第 7；本項排第一＝**SK 指示**；I／J 需求更高但需新資料層（已記入 `coverage-gap-audit` §8 次序） |

## 7. 工作量

2 新檔＋`PackIndex` 1 方法＋`AskEngine` 兩處（FACT 區＋post-LLM 鏈）＋`ReplyLang` 1 方法＋3 語 lang＋1 測試檔。實作經 **cursor-agent**。

## 8. 更正記錄（全部自己核實）

| 版本 | 錯 | 真值 | 查法 |
|---|---|---|---|
| v1 | 「`KeyMapping.ALL` 冇」 | 存在但 private | tsrg:14858＋javap |
| v1 | 「只加 AskEngine 一行」 | `AskToolLoop:141-143` 靜默丟棄 | 讀 code |
| v1 | 「A7 全綠」 | `check_tool_schema_stable:108` FAIL | 讀測試 |
| v1 | 「答我唔確定」 | codebase 冇；慣例 `[TOOL_MISS]`／`未收錄` | grep |
| v2 | 「`options.keyMappings` public final」 | **非 final** | javap |
| v2 | CAPABLE_TOOLS 39-44／register 139-145 | **39-42**／**141-143** | grep -n |
| v3 | 「machineLines :605」 | **:609**（用於 626/631/641/655） | grep -n |
| v3 | A1 問句「點開 mod 清單」 | **唔命中自家規則** → 已改問句＋量度 | §3 量度 |
| v3 | block 只入 prompt | 需要 post-LLM 強制（前例 `AskEngine:928`／`RecipeGetMarks` 註解） | 讀 code |

## 9. Review 記錄

| 輪 | 比分 | 主要發現 → 處理 |
|---|---|---|
| R1 | 3 : 7 | 2 致命（ALLOWLIST／guard test）＋6 中 → v2 |
| R2 | 4 : 6 | 加 tool ＝ global schema／token 成本＋自相矛盾 → **v3 削範圍（唔加 tool）** |
| R3 | 5 : 5 | ① A1 問句唔命中自家觸發詞（＋無量度）② 缺 post-LLM 持久化 ③ 驗收靠 input trace 可假綠 → **v4 全修**（§3 量度、§4#5、§5 改「玩家可見 body」） |
| R4 | 待 | 反方＋數字核實方（**最後一輪**；若 <8:2 → 停手交 SK，附四件停手報告） |

## 10. 停手報告（SK 規則：第 3–4 輪仍未達 8:2 → 立即停手交 SK）

### ① 逐輪比分（全部有獨立 reviewer ＋ 我逐條核實）

| 輪 | 比分（正方：反方） | 主要發現 |
|---|---|---|
| R1 | 3 : 7 | 2 致命：`AskToolLoop.java:141-143` 白名單靜默丟棄；`tests/check_tool_schema_stable.py:108` guard 硬 FAIL |
| R2 | 4 : 6 | 加 tool ＝ 每次 capable round 多一個 native tool schema（`AskToolLoop:401/550` → `LlmClient:511`）＋同「唔經模型揀 tool」自相矛盾 |
| R3 | 5 : 5 | A1 驗收問句唔命中自家觸發規則（無量度）；block 只入 prompt，LLM 會掩掉 |
| R4 | 5 : 6 | ① **early return 可達性**（`:396`／`:422`，早於 FACT 區 `:571`）② 量度不可獨立審計（正例無存檔） |

**淨值停滯**：修好 3 點但新增 1 個 HIGH → 趨勢升到 5:5 後回落，未見達 8:2 嘅路徑。

### ② 卡死嘅載重決定（兩個）

**A. 可達性（R4 HIGH，我已核實成立）**
`AskEngine.java:396 return AskResult.text(missBody);` 同 `:422-425 return withSideQuests(...)` **兩條 early return 早過 FACT 區（`:571`）**。典型按鍵問題（無 focus item、無 JEI、無 machine、低 confidence）**正正最易命中** → 個 block 同 post-LLM 強制**永遠唔會行到**。v4 §4#4/#5 冇處理。
→ 修法（未做）：把 keybind 視為「有內容」，即喺兩條 guard 加 `&& !keybindAsk`，令處理提前到 :396／:422 **之前**。

**B. 量度可審計性**
`18/18 回收` 嘅**正例係我自己寫**（非獨立樣本），而 artifact 原本冇存正例清單 → 唔可獨立重算。
→ 已補（本輪）：artifact 現存 **18 條正例（逐條命中族）＋v3 基線 15/18＋per-family 命中**。但**更根本**嘅問題係：corpus 係 **Stack Exchange 英文／偏 vanilla**，**中文（尤其簡體）誤觸率完全未量度**（實測：中文四族喺該 corpus 命中 0 → 無數據）。

### ③ 最貴嘅未知

**冇一份獨立、真實嘅 modpack 玩家按鍵問句語料（含中文）**。今日所有 recall／誤觸數字都建基於（i）我自寫正例（ii）英文 SE 標題。呢個未知一日未解，規則嘅真實誤觸率就係估。

### ④ 我嘅建議（4 個選項，附我推薦）

| 選項 | 內容 | 我嘅評估 |
|---|---|---|
| **1（推薦）** | **收窄＋修可達性**：① keybind 處理提前到 `:396`／`:422` 之前（`&& !keybindAsk`）② 規則只認**英＋繁中明確觸發詞**，明文寫「簡體／其他語言未覆蓋」③ 驗收 A1 改用已證實存在嘅 `display.body.final`（`AskService.java:512`）④ 保存正例＋per-family 數字（已做） | 2 個已知缺口都係**具體可修**，唔需要再 review；風險已明示、範圍細、離線可驗 |
| 2 | **先建語料**：等你／朋友／遊戲內實測收集 ≥50 條真實按鍵問句（含中文）才開工 | 最穩，但會拖住；而我哋**未有真實用戶**（未上架） |
| 3 | **跳去做第 2 項**（能力反查 J 類，需求更高） | 合理；但按鍵係唯一「完全離線可驗」嘅一項 |
| 4 | 放棄本項 | 需求排第 7，放棄成本低 |

**停手狀態**：plan 檔已凍結於 v4（＋本報告）；未改任何 production 碼；probe artifact 已補可審計內容。等你一句指示。
