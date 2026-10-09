# Pack AI — 按鍵查詢（keybind）· v4

- **Status**：DRAFT v4 — 等 R4 review（比分趨勢：R1 3:7 → R2 4:6 → R3 5:5；上限 3–4 輪，R4 為最後一輪）
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
