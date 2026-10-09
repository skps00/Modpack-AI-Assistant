# Pack AI — 按鍵查詢（keybind_lookup）· v2

- **Status**：DRAFT v2 — 等 R2 review（R1＝正方 3 : 反方 7，唔過；本版逐條修）
- Generated：2026-10-10（覆蓋缺口審計 §8 次序第 1 項；SK 2026-10-10 指示「按次序一個一個做」）
- Loaders：**Forge 1.19.2**（NeoForge 1.21.1 暫停，issue #20；`neoforge/README_PAUSED.md`）
- 本版相對 v1 嘅改動全部有**編號**（見 §9），每條對應一個 R1 指控或我自己核實嘅事實

## 1. Goal / Non-goals

### Goal
答「**要撳咩掣先用得到 X**」、「**我個掣係唔係撞咗**」、「**呢個功能未綁掣**」，全部用**遊戲內即時狀態**。

### Non-goals
改玩家按鍵／自動綁鍵｜猜未綁功能嘅預設鍵（遊戲內冇此資訊）｜NeoForge 樹（暫停）｜新增 UI 畫面

## 2. 資料來源（v2 修正）

| 來源 | 攞咩 | 核實 |
|---|---|---|
| `Minecraft.getInstance().options.keyMappings` | **全部功能現況**（`public final KeyMapping[]`） | `javap`：`public final net.minecraft.client.KeyMapping[] f_92059_`；tsrg：`f_92059_ keyMappings` ✅ |
| `KeyMapping#getName()` | 翻譯 key（`key.<mod>.<name>`） | tsrg：`m_90860_ ()Ljava/lang/String; getName` ✅ |
| `KeyMapping#getTranslatedKeyMessage()` | 目前按鍵顯示（例 `M`） | tsrg：`m_90863_ ... getTranslatedKeyMessage` ✅ |
| `KeyMapping#isUnbound()` | 未綁判斷 | tsrg：`m_90862_ ()Z isUnbound` ✅ |
| ~~`KeyMapping.ALL`~~ | **唔用** | **v2 更正**：field **存在**（`f_90809_`／tsrg:14858），但係 **private** `Map<String,KeyMapping>`；`f_90810_` 亦係 private（Forge `KeyMappingLookup`）→ 要用就要反射／AT，**所以改用 public 嘅 `options.keyMappings`**（v1 §8 寫「冇呢個 field」係**錯**，已更正） |

**離線 oracle**：`tools/extract_keybinds.py`（只做離線登記盤點）。⚠️ **定義差異（v2 講明）**：oracle 用 `options.txt`（玩家**存過檔**嘅綁定）判斷「已綁」；runtime 用 live `KeyMapping`（**冇** options.txt 紀錄時仍持有**建構子預設鍵**）→ 兩者**語義唔同，數量唔應該直接比**。實測：ATM8 沙盒 options.txt 係 2023 舊檔 → **0 綁定（0/0，vacuous）**；StarTech 139／148 登記／60 已綁／12 衝突；NFWC 272／201／72／11 衝突。

## 3. 實作範圍（v2 修正：補返兩個致命缺漏）

| # | 檔 | 改咩 | v1 缺漏 |
|---|---|---|---|
| 1 | `logic/KeybindAskTool.java`（新） | 實作 `api/AskTool`：`name()="keybind_lookup"`、`argsSchemaJson()`（`query?`／`key?`／`namespace?`）、`run()` 回傳命中清單＋現況掣＋衝突標記；**冇命中／未綁**→ 回既有慣例字串（見 §4 A3） | — |
| 2 | **`logic/AskToolLoop.java:39-44`** | **`CAPABLE_TOOLS` 加 `"keybind_lookup"`**（＋`QUERY_TOOLS` 同步加：呢個 tool 唯讀、無副作用、適合一輪內被叫）；`ALLOWLIST` 係 `Set.copyOf(CAPABLE_TOOLS)` 自動跟 | ⚠️ **v1 只寫 `AskEngine` 加一行 → 會被 `register()` 靜默丟棄**（`AskToolLoop.java:139-145`：`if (!ALLOWLIST.contains(tool.name())) return;`） |
| 3 | **`tests/check_tool_schema_stable.py:107-108`** | `assert forge_only == ["knowledge_lookup"]` 會因為多一個 Forge-only tool 而 **硬 FAIL**。改為一個**明示、附註解嘅 paused 例外集合**：`assert forge_only == ["knowledge_lookup", "keybind_lookup"]`（＋註解引用既有一模一樣嘅前例 `# KB-1: knowledge_lookup is forge-only while NeoForge support is paused.`）。測試仍然會喺**任何非預期**新名出現時紅 → 意圖（捉非預期漂移）保留。 | ⚠️ **v1 完全冇提** → A7「全綠」注定達唔到 |
| 4 | `logic/PackIndex.java` | 加 `isKeybindQuestion(String)`（同 `:1131 isPurposeQuestion`／`:1156 isMachineQuestion` 同風格）；**必須喺 purpose 判斷之前檢查**（否則含「用」字嘅問句被 `isPurposeQuestion` 捉走 → 走錯路徑） | v1 冇講次序風險 |
| 5 | `logic/AskEngine.java`（FACT 組裝區，**:571-612**） | 喺 `boolean machineAsk = PackIndex.isMachineQuestion(question);`（:575）同一區加 `boolean keybindAsk = PackIndex.isKeybindQuestion(question);`；`keybindAsk` 為真→ append 一個 **KEYBIND block**（由 live registry 即時砌，唔經模型揀 tool）；miss → block 內容＝該 tool 嘅 `[TOOL_MISS]` 文案 | v1 只寫「加 deterministic FACT 注入」，**冇注入點** |
| 6 | `assets/packai/lang/{en_us,zh_cn,zh_tw}.json` | 新 `packai.keybind.*`（三語同步；缺字串 fallback 見 §5） | — |
| 7 | `tests/check_keybind_lookup.py`（新） | 純 Python mirror：驗 ① 衝突分組邏輯（同一 key ≥2 功能）② 未綁判斷 ③ miss 文案，用 fixture（跟 `tests/` 既有型式，130 個檔） | — |

**唔碰**：現有 14 個 tool 內部行為、`options.txt`、玩家設定檔、NeoForge 樹。

## 4. 驗收標準（v2 重寫，逐項可機械核實）

| # | 條件 | 證據要求（機械核實） |
|---|---|---|
| A1 | 問「點開 mod 清單」→ 答到**真掣** | ask trace 內出現 KEYBIND block，且答案含 `getTranslatedKeyMessage()` 嘅實際字串 |
| A2a | 衝突清單**內部自洽**：每個列出嘅 key，真係綁咗 ≥2 個功能 | `tests/check_keybind_lookup.py` 對真 trace 斷言（唔靠 oracle 數量比對） |
| A2b | 抽樣 5 組衝突**人工核對**（截圖 Controls 畫面） | 5 張截圖＋trace 並排 |
| A3 | 未綁功能 → 用**既有慣例**講清楚 | 答案／trace 出現 `[TOOL_MISS] keybind_lookup empty — do not invent`（`api/AskTool.java:29-31`）或 `未收錄`（`AskReplyScrub:1669`） |
| A4 | 問唔存在功能 → **唔准作** | 同上；**唔准**自己作「我唔確定」呢類新字串（codebase 冇） |
| A5 | 跨包：**StarTech ＋ NFWC**（兩包都有真玩家設定：12／11 組衝突）；ATM8 沙盒只作「掣名」案例（其 options.txt 係舊檔、0 衝突 → **唔用嚟驗衝突**） | 3 個 trace 檔名 |
| A6 | 冇新洩漏 | 跑**既有** `tests/check_ask_display_leak.py`（`--trace` 模式）對 baseline **零新增紅**（唔用自製關鍵字掃描） |
| A7 | compile＋測試 | `compileJava` BUILD SUCCESSFUL；`tests/check_*.py` 與 baseline 一致（已知 1 紅 `check_ask_display_leak` 走查模式除外，且 A6 另計） |

## 5. 風險 / 限制（v2 誠實版）

| 項 | 內容 |
|---|---|
| 模組名 fallback | runtime **攞唔到**「邊個 mod 註冊」→ 只可由翻譯 key 嘅 **namespace** 推（`key.jade.toggle` → `jade`），再用 `ModList.get().getModContainerById(ns)`（repo 已用 ModList：`client/context/GameContextCollector.java:16,36`）查顯示名；查唔到→**只顯示 raw key id**，並標明「依 namespace 推斷」。**唔准**當確定事實 |
| 玩家語言缺字串 | 直接顯示 raw `key.<mod>.<name>`，**唔准**自己譯 |
| tool 選擇準確率（14→15） | **本 repo 冇任何 tool-selection 準確率 harness**（`tests/` 只斷言字串）→ 本 slice **明確唔聲稱**量度到；本功能走 deterministic FACT 注入路徑，理論上唔經模型揀 tool。列為已知限制，寫入 `docs/plans/four-issue-backlog.md` 待辦 |
| 誤報衝突 | 只列事實（同 key ≥2 功能），**唔判斷**嚴重性／唔建議改鍵 |
| Guard test 改動（#3） | 動到一個守衛測試嘅預期集合 → **需要 SK 知悉／批准**（前例：同檔已有 KB-1 同型例外）；唔批准就**唔做本 slice**（唔會為咗綠而刪測試） |
| 部署紀律 | 只准 `hermes/scripts/mc_mod_deploy_jar.py --target packai`（遊戲開住會 REFUSED）；換 jar 前備份到 `%TEMP%`；換後**驗 sha256**；只郁沙盒，實機唔部署 |
| Rollback | `git revert <本 slice 嘅 commit 範圍>`（多檔改動，非單一 commit）；沙盒 jar 還原自 `%TEMP%` 備份＋驗 sha |

## 6. 需求證據（v2 更正）

| 證據 | 實情 |
|---|---|
| 「按鍵衝突係真痛點」 | **實測**：StarTech **12 組**衝突、NFWC **11 組**（真玩家設定；`tools/extract_keybinds.py` 跑出）。**唔再**歸因 ViewBoard |
| ViewBoard mod | 研究文件描述＝「顯示**邊個鍵未用**」→ 支持「**未綁**」需求，**唔係**衝突（v1 講錯，已更正） |
| 影片／平台 | YT「Top 10 Clever HotKeys」**1,863,571**（research §9.2）；bili 按鍵設定教學 **338,445**（§9.4） |
| 需求排名 | 按鍵喺研究 §9.6 排 **第 7**；本 slice 排第一係 **SK 2026-10-10 指示（按次序）**，並因佢係唯一**完全離線可驗**嘅一項；I（農場）／J（能力反查）需求更高但需要新資料層 |

## 7. 工作量

1 個工作段：1 新 tool（～150 行）＋`AskToolLoop` 2 行＋`AskEngine` ～6 行＋`PackIndex` 1 方法＋3 語 lang＋2 個測試檔改動。實作一律經 **cursor-agent**。

## 8. v1 錯處更正（自己核實）

| v1 寫法 | 真值 | 查法 |
|---|---|---|
| §8「`KeyMapping.ALL` 1.19.2 冇呢個 field」 | **錯**：`f_90809_ ALL` 存在（**private**，`javap` 顯示 `private static final Map<String,KeyMapping>`）；`f_90810_`＝Forge `KeyMappingLookup`（亦 private） | `srg_to_official_1.19.2.tsrg:14858` ＋ `javap -p` |
| §3「只改 AskEngine 加 register」 | **錯**：會被 `AskToolLoop.register()` 靜默丟棄（ALLOWLIST，`:139-145`） | 讀 `AskToolLoop.java:39-44,139-145` |
| §4 A7「tests 全部綠」 | **錯**：`check_tool_schema_stable.py:108` 會 FAIL | 讀測試 |
| §5「覆核舊題（P0 §5⑥ 做法）」 | **錯**：本 repo 冇該節／冇 harness | `grep` 全 repo |
| §4 A3/A4 要求答「我唔確定」 | **錯**：codebase 冇此字串；慣例係 `[TOOL_MISS] … do not invent`／`未收錄` | `grep` |

## 9. Review 記錄

| 輪 | 反方分 | 處理 |
|---|---|---|
| R1 | **正方 3 : 反方 7**（唔過） | 反方 13 條指控；我逐條核實：**2 條致命成立**（ALLOWLIST／guard test）、6 條中（oracle 定義／miss 字串／準確率假引用／需求歸因／ALL 錯述／fallback mod 名／注入點）、3 條低（重疊／部署含糊／A6 充數）。全部喺 v2 修（§3 #2#3#4#5、§4 A2a/A2b/A3/A6、§5、§6、§8） |
| R2 | 待 | 反方 ＋ 數字核實方（獨立） |
