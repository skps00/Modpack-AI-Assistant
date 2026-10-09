# Pack AI — 按鍵查詢（keybind）· v3

- **Status**：DRAFT v3 — 等 R3 review（R1 3:7 → R2 4:6；本版**削範圍**：由「加 tool」改為「deterministic 前饋 block」，兩個致命 blocker 連根拔起）
- Generated：2026-10-10｜SK 指示：按次序一個一個做
- Loaders：Forge 1.19.2（NeoForge 1.21.1 暫停）
- **本版最重要嘅改變**：**唔加 AskTool、唔改 `AskToolLoop`、唔改任何守衛測試**（見 §3、§9）

## 1. Goal / Non-goals

**Goal**：答「要撳咩掣先用得到 X」／「我個掣撞咗」／「呢個功能未綁掣」，資料全部來自遊戲內**即時**狀態。

**Non-goals**：改玩家按鍵｜猜未綁功能嘅預設鍵｜NeoForge 樹｜新 UI｜**新增 model-visible tool**（見 §3 理由）

## 2. 資料來源（事實已逐項核實）

| 來源 | 攞咩 | 核實（真 artifact） |
|---|---|---|
| `Minecraft.getInstance().options.keyMappings` | 全部功能＋目前綁定 | `javap`：`public net.minecraft.client.KeyMapping[] f_92059_;`（**public、非 final**）；tsrg：`f_92059_ keyMappings` |
| `KeyMapping#getName()` | 翻譯 key（`key.<mod>.<name>`） | tsrg：`m_90860_ ()Ljava/lang/String; getName` |
| `KeyMapping#getTranslatedKeyMessage()` | 目前按鍵顯示（例 `M`） | tsrg：`m_90863_ ... getTranslatedKeyMessage` |
| `KeyMapping#isUnbound()` | 未綁判斷 | tsrg：`m_90862_ ()Z isUnbound` |
| `KeyMapping.ALL` | **唔用** | field 存在（`f_90809_`，tsrg:14858）但 **private**；`f_90810_`＝Forge `KeyMappingLookup`（亦 private）→ 用 public 嘅 `options.keyMappings` |

**離線 oracle**：`tools/extract_keybinds.py`——**只作離線盤點，唔可以同 runtime 直接比數量**（語義唔同）：
- oracle「**登記**」＝jar lang 有 `key.*` 條目數；「**已綁**」＝該 id 命中 `options.txt` 嘅功能數；「**衝突**」＝同一完整按鍵字串綁 ≥2 功能
- runtime＝live `KeyMapping`（**冇** `options.txt` 紀錄時仍持有**建構子預設鍵**）
- 實測（我親手重跑）：ATM8 沙盒 **258 登記／0 綁定／0 衝突**（`options.txt` 係 2023 舊檔 → **唔可以用嚟驗衝突**）；StarTech **148／139 options 行／60 已綁／12 衝突**；NFWC **201／272／72／11 衝突**

## 3. 實作範圍（v3：削走 tool 路線）

### 為何唔加 tool（核實證據）
`logic/AskToolLoop.java:39-42` `CAPABLE_TOOLS`（14 個名）＋`:44 ALLOWLIST = Set.copyOf(CAPABLE_TOOLS)`；`register()` `:141-143` 唔在白名單就**靜默 return**。而 `:401 return capableLoop(state, llm, CAPABLE_TOOLS);`、`:550 llm.completeWithTools(CAPABLE_TOOLS)`、`LlmClient.java:511 body.add("tools", nativeToolsSchema(toolNames))`、`:628 nativeToolsSchema()` → **加入 CAPABLE_TOOLS ＝ 每次 capable round 都多一個 tool schema**（改 token 成本同 loop 行為），而且要改 `tests/check_tool_schema_stable.py:107-108` 嘅 `assert forge_only == ["knowledge_lookup"]`（守衛測試）。
→ 呢個功能**唔需要**模型揀 tool：問句意圖可由既有 deterministic 前饋路徑處理（同 `machineSection` / `purposeBlock` 一樣）。所以 **v3 完全唔郁 tool 層**。

### 改動清單

| # | 檔 | 改咩 |
|---|---|---|
| 1 | `logic/KeybindFacts.java`（新，**純邏輯**） | `public static String format(List<Row> rows, String query, String langCode)`：由 (功能名, 按鍵顯示, 是否未綁) 砌**有上限**嘅行（cap 8 行；超出寫「…另有 N 項」）；同一按鍵 ≥2 功能 → 標衝突；query 空 → 列最相關／全部頭 N 行。**唔准**自己譯／創作按鍵名 |
| 2 | `client/context/KeybindReader.java`（新，**薄 client 讀取層**） | `snapshot()`：由 live `Minecraft.getInstance().options.keyMappings` 讀 `getName()`／`getTranslatedKeyMessage()`／`isUnbound()` → `List<Row>`；**遊戲未 init／任何例外 → 回空 list**（唔拋錯） |
| 3 | `logic/PackIndex.java` | 加 `isKeybindQuestion(String)`（同 `:1131 isPurposeQuestion`／`:1156 isMachineQuestion` 同風格）；**必須喺 purpose 判斷之前**用（否則含「用」字問句被 `isPurposeQuestion` 捉走） |
| 4 | `logic/AskEngine.java`（FACT 組裝區 **:571-612**，`machineAsk` 喺 `:575`） | 加 `boolean keybindAsk = PackIndex.isKeybindQuestion(question);`；`keybindAsk && !block.isBlank()` → append `List.of(ReplyLang.sectionKeybind(lang) + "\n" + block)`；block 空 → 附一句 miss 文案（lang key） |
| 5 | `logic/ReplyLang.java` | 加 `sectionKeybind(String code)`（跟 `:988 sectionHowToGet`／`:992 sectionHowToUse`／`:996 sectionMachine` 同款，讀 lang key） |
| 6 | `assets/packai/lang/{en_us,zh_cn,zh_tw}.json` | `packai.reply.section.keybind`（例 `【按鍵】`）＋ miss 文案（例「未收錄相關按鍵功能」）——**三語同步** |
| 7 | `tests/check_keybind_facts.py`（新） | 純 Python mirror：驗 ① 衝突分組 ② 未綁標記 ③ 行數上限 ④ query 篩選 ⑤ miss 文案；用 fixture（跟 `tests/` 既有型式） |

**唔碰**：`AskTool` 介面、`AskToolLoop`（CAPABLE_TOOLS／QUERY_TOOLS／ALLOWLIST）、`tests/check_tool_schema_stable.py`、現有 14 個 tool、NeoForge 樹、`options.txt`。

## 4. 驗收標準（逐項可機械核實）

| # | 條件 | 證據 |
|---|---|---|
| A1 | 問「點開 mod 清單」→ 答到**真掣** | ask trace 內出現 `[按鍵]`／`【按鍵】` block，且答案含 `getTranslatedKeyMessage()` 實際字串 |
| A2a | 衝突清單**內部自洽**（每個列出嘅 key 真係綁 ≥2 功能） | `tests/check_keybind_facts.py` 對真 trace 斷言 |
| A2b | 抽樣 **5 組**衝突人工核對（Controls 畫面截圖 vs 答案） | 5 張截圖＋trace 並排（我做，記錄喺 plan review log） |
| A3 | 未綁功能 → 明講「未綁／要去設定綁」 | 答案／trace 出現 `packai.reply.section.keybind` 對應 block ＋ 未綁標記（由 mirror 測試斷言格式） |
| A4 | 問唔存在功能 → **唔准作** | 出現 miss lang key 文案（三語齊）；**唔准**自創新字串（codebase 冇「我唔確定」） |
| A5 | 跨包：**StarTech＋NFWC**（真玩家設定，12／11 組衝突）；ATM8 只作「掣名」案例 | 3 個 trace 檔名 |
| A6 | 冇新洩漏 | 跑既有 `tests/check_ask_display_leak.py`（`--trace`）→ 對 baseline **零新增紅** |
| A7 | compile＋測試 | `compileJava` BUILD SUCCESSFUL；`tests/check_*.py` 同 baseline 一致（已知 1 紅＝`check_ask_display_leak` 走查模式） |

## 5. 風險 / 限制

| 項 | 內容 |
|---|---|
| 模組名 fallback | runtime 攞唔到「邊個 mod 註冊」→ 只可由翻譯 key 嘅 namespace 推（`key.jade.toggle` → `jade`），再用 `ModList.get().getModContainerById(ns)`（repo 已用：`client/context/GameContextCollector.java:16,36`）；查唔到 → **只顯示 raw key id**，標明「依 namespace 推斷」。唔准當事實 |
| 玩家語言缺字串 | 顯示 raw `key.<mod>.<name>`，唔准自己譯 |
| Context／token | block **硬上限 8 行**（超出寫「另有 N 項」）＋無新增 tool schema → 對 prompt 影響有界 |
| 遊戲未 init／例外 | `KeybindReader.snapshot()` 回空 → 唔 append block → 走返原本路徑（唔會壞） |
| 誤觸 | `isKeybindQuestion` 只認「按鍵／快捷鍵／hotkey／keybind／撳掣／要按」等**明確詞**，唔用單字「用」（避免同 `isPurposeQuestion` 撞） |
| 部署紀律 | 只准 `hermes/scripts/mc_mod_deploy_jar.py --target packai`（遊戲開住會 REFUSED）；換前備份 `%TEMP%`；換後**驗 sha256**；只郁沙盒 |
| Rollback | `git revert <slice commit 範圍>`；沙盒 jar 還原自 `%TEMP%` 備份＋驗 sha |
| 已知限制 | repo 冇 tool-selection 準確率 harness——**本 slice 唔聲稱量度**；因本設計**唔經模型揀 tool**，此風險不適用（v3 明確剔除，非口頭保證） |

## 6. 需求證據

| 證據 | 實情 |
|---|---|
| 按鍵衝突 | **實測** StarTech 12 組／NFWC 11 組（真玩家設定） |
| 未綁需求 | ViewBoard 類工具＝「顯示邊個鍵**未用**」（支持未綁需求；**唔係**衝突證據） |
| 影片／平台 | YT「Top 10 Clever HotKeys」1,863,571（research §9.2）；bili 按鍵設定教學 338,445（§9.4） |
| 需求排名 | research §9.6 排第 7；本項排第一＝**SK 指示**，且係唯一完全離線可驗嘅一項 |

## 7. 工作量

1 個工作段：2 個新檔（`logic/KeybindFacts`、`client/context/KeybindReader`）＋`PackIndex` 1 方法＋`AskEngine` ～5 行＋`ReplyLang` 1 方法＋3 語 lang＋1 測試檔。**比 v2 更細**（唔郁 tool 層）。實作經 **cursor-agent**。

## 8. 更正記錄（自己核實，唔靠 reviewer）

| 版本 | 錯咩 | 真值 | 查法 |
|---|---|---|---|
| v1 §8 | 「`KeyMapping.ALL` 1.19.2 冇」 | 存在但 **private** | tsrg:14858＋javap |
| v1 §3 | 「只加 AskEngine 一行」 | 會被 `AskToolLoop:141-143` 靜默丟棄 | 讀 code |
| v1 §4 | 「A7 全綠」 | `check_tool_schema_stable:108` 會 FAIL | 讀測試 |
| v1 §5 | 「覆核舊題（P0 §5⑥）」 | 本 repo 冇該節／冇 harness | grep |
| v1 §4 | 要求答「我唔確定」 | codebase 冇；慣例係 `[TOOL_MISS] … do not invent`／`未收錄` | grep |
| v2 §2 | 「`options.keyMappings` public **final**」 | **非 final**（`javap`：`public KeyMapping[] f_92059_`） | javap |
| v2 §3 | CAPABLE_TOOLS「39-44」、register「139-145」 | CAPABLE_TOOLS＝**39-42**；ALLOWLIST＝:44；靜默 return＝**141-143** | grep -n |
| v2 §3 | 「加 tool」被當成中性操作 | 會令**每次 capable round 多一個 tool schema**（`AskToolLoop:401/550`、`LlmClient:511/628`） | 讀 code → 促成 v3 削範圍 |

## 9. Review 記錄

| 輪 | 比分 | 主要發現 → 處理 |
|---|---|---|
| R1 | 正方 3 : 反方 7 | 2 致命（ALLOWLIST 靜默丟棄／guard test 硬 FAIL）＋6 中（oracle 定義／miss 字串／假引用／歸因／ALL／注入點）→ v2 全修 |
| R2 | 正方 4 : 反方 6 | 新發現：加 tool ＝ global schema 增長＋token 成本＋同「唔經模型揀 tool」自相矛盾；guard test 改動踩 AGENTS 禁區；行號／修飾符細節錯 → **v3 削範圍（唔加 tool）**，兩個致命 blocker 同矛盾一齊消失 |
| R3 | 待 | 反方＋數字核實方（獨立） |
