# keybind_lookup 實作規格（cursor-agent 派工原文，2026-10-10）

> 用途：v5 實作（impl）＋ Fix 9（評分／排序）嘅**原文規格**。若 working tree 嘅未 commit code 唔見咗，
> 用同一份規格重派 cursor-agent 即可原樣重建（配合 `docs/plans/2026-10-10-keybind-lookup.md` v4）。
> ⚠️ 內容全部係當時派工指令，**不含任何 secret**。

---

## A. impl（8 fix）

# Cursor Fix: Pack AI — 加 keybind_lookup 工具（v5，工具路線）

## 0. 硬規則（違反就即刻停手並在報告講明）

- Workspace / repo root：`C:\Users\skps9\Documents\Code_Project\super_minecraft_AI_player`（**呢個係唯一可以改嘅 repo**）。
- **只准改 Forge 樹**：`forge/1.19.2/...`。**絕對唔准**改 `neoforge/`（NeoForge 線暫停）。
- **唔准** build jar、**唔准** copy 任何檔案入 Prism instance、**唔准** `git commit`／`git push`。
- **唯一准寫嘅新檔**＝下面 Fix 1／Fix 2／Fix 8 列出嘅檔；**唔准**喺 repo 內寫任何其他新檔（包括你自己的 instructions／review 副本）。報告只寫去 `C:\Users\skps9\AppData\Local\Temp\cursor_keybind_impl_report.md`。
- **唔准**再寫 review 檔、**唔准**改 plan 檔。設計已經 review 完（4 輪），直接實作。
- 有 shell 被拒係常態：跑唔到驗證就喺報告寫「NOT RUN ＋建議命令」，由 Hermes 跑。

## 1. 目標

加一個新 AskTool：`keybind_lookup`，答「要撳咩掣先用得到某功能／我個掣係唔係撞咗／呢個功能未綁掣」。
資料來自**遊戲內即時狀態**（client 側），唔准作、唔准猜。

參考現有實作風格：`forge/1.19.2/src/main/java/com/skps9/packai/logic/ItemSearchAskTool.java`（最接近嘅範本）、介面 `api/AskTool.java`、參數類型 `api/AskToolArgs.java`。

## 2. 逐項改動（numbered，逐項要照做）

### Fix 1 — 新檔 `forge/1.19.2/src/main/java/com/skps9/packai/client/context/KeybindReader.java`

Client 側**薄讀取層**，讀 live 按鍵狀態。要求：

- 由 `net.minecraft.client.Minecraft.getInstance().options.keyMappings`（型別 `net.minecraft.client.KeyMapping[]`，**public 非 final**，SRG `f_92059_`）逐個讀：
  - 功能名：`Component.translatable(km.getName()).getString()`（`getName()` 回翻譯 key，例如 `key.packai.open`）
  - 目前按鍵顯示：`km.getTranslatedKeyMessage().getString()`（例如 `M`）
  - 未綁：`km.isUnbound()`
- **唔准**用 `KeyMapping.ALL`（1.19.2 存在但係 **private**）。
- 回傳一個 immutable list，每項含：`label`、`rawKey`（`km.getName()`）、`keyDisplay`、`unbound`、`namespace`（由 `rawKey` 第二段抽出，例如 `key.jade.toggle` → `jade`）。
- **衝突**：同一個 `keyDisplay` 出現喺 ≥2 個**未綁=false** 嘅項目 → 該 keyDisplay 標為衝突。
- **例外／`Minecraft.getInstance()` 為 null／`options` 為 null → 回空 list，唔准拋錯**（呢個 method 會被 tool 叫）。
- 加 class 註解解釋「為何用 options.keyMappings 而唔用 KeyMapping.ALL」。

### Fix 2 — 新檔 `forge/1.19.2/src/main/java/com/skps9/packai/logic/KeybindAskTool.java`

`implements com.skps9.packai.api.AskTool`，形狀照 `ItemSearchAskTool`：

- `name()` → `"keybind_lookup"`
- `description()`／`llmDescription()` → 英文一句講清楚：用嚟查「邊個鍵綁咗邊個功能／邊個鍵衝突／邊個功能未綁」，query＝玩家原話。
- `argsSchemaJson()` → `{"type":"object","properties":{"query":{"type":"string"},"key":{"type":"string"},"namespace":{"type":"string"}},"required":["query"],"additionalProperties":false}`
- `run(AskToolArgs args)` 行為：
  1. 由 `KeybindReader.snapshot()` 攞 rows；**空** → 回 `toolMissNote(...)`（即 `"[TOOL_MISS] keybind_lookup empty — do not invent"`；用介面 default 或同一格式）。
  2. 篩選：`query` 對 `label` 做唔分大小寫 substring 比對（亦接受 `namespace`／`key` 參數收窄）。
  3. 輸出**最多 10 行**（超出加一行「…另有 N 項」），每行格式：
     `- <label> → <keyDisplay>` ；若未綁寫 `<label> → （未綁）` ；若衝突喺行尾加 `  ⚠撞鍵`。
  4. 冇命中 → 回 `toolMissNote(...)`（**唔准**自己創作按鍵名／功能名）。
  5. 任何例外 → 回 miss 文字，唔准拋。
- **唔准**做 UI、唔准改任何現有 tool。

### Fix 3 — `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskToolLoop.java`

- `CAPABLE_TOOLS`（約 **39-42 行**，`List.of(...)` 14 個名）→ **加入 `"keybind_lookup"`**（變成 15 個）。
- `QUERY_TOOLS`（約 **46-48 行**）→ **加入 `"keybind_lookup"`**。
- `ALLOWLIST`（約 **44 行**）＝ `Set.copyOf(CAPABLE_TOOLS)`，唔需要改。
- ⚠️ 冇加 CAPABLE_TOOLS 嘅話，`register()`（`:141-143`）會**靜默丟棄**呢個 tool。

### Fix 4 — `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java`（tool 註冊區，約 36-49 行）

喺 `AskToolLoop.INSTANCE.register(new KnowledgeLookupAskTool());` 之後加一行：
`AskToolLoop.INSTANCE.register(new KeybindAskTool());`

### Fix 5 — `forge/1.19.2/src/main/java/com/skps9/packai/logic/PackIndex.java`

加 `public static boolean isKeybindQuestion(String question)`（風格照 `:1131 isPurposeQuestion`，null／blank → false）。
用以下 **7 族**關鍵詞（case-insensitive；中文用 literal，唔好改字）：

1. `按鍵|按键|快捷鍵|快捷键|熱鍵|热键`
2. `撳\s?(咩|乜|邊個|边个)?\s?掣|按咩掣|按咩鍵|按什麼鍵|按哪個鍵|按哪个键|咩掣|乜掣|邊個掣`
3. `改鍵|改键|綁鍵|绑键|綁定按鍵|按鍵設定|冇綁|沒綁|未綁`
4. `(掣|鍵|键)[^。！？]{0,8}(撞|衝突|重复|重複)|同一個(掣|按鍵)`
5. `key\s?bind|keybind|hot\s?key|hotkey|rebind`
6. `(which|what)\s+(key|button)`
7. `change the .{0,12}key`

⚠️ **唔准**只用單字「用」做判斷（會同 `isPurposeQuestion` 撞）。

### Fix 6 — `AskEngine.java`：early-return guard 唔准吞掉 keybind 問題（**呢項最重要**）

實測：`AskEngine.java:376-379` 有呢個 guard：

```java
if (loop.skipLlm()
        && !hasJei
        && !hasMachine
        && jeiInfo.isEmpty()
        && !(retrieved.highConfidence() && retrieved.snippets() != null && !retrieved.snippets().isEmpty())) {
    ... return AskResult.text(missBody);   // :396
}
```

同 `:422` 呢條：
```java
if (plain != null && retrieved.highConfidence() && questHits.isEmpty() && !hasRecipeGet && !hasMachine) {
    return withSideQuests(...);
}
```

按鍵問題多數「冇 JEI／冇機器／低信心」→ 會**喺未叫 LLM、未行 tool loop 之前就 return 一句 miss**，新 tool 永遠冇機會執行。

做法：**加以下條件令 keybind 問題唔會行 early return**（即係當佢係「有內容」）：
- 喺 `:376` 個 `if` 條件尾加 `&& !PackIndex.isKeybindQuestion(question)`
- 喺 `:422` 個 `if` 條件尾加 `&& !PackIndex.isKeybindQuestion(question)`

⚠️ 只改呢兩個 guard 條件，**唔准**改其他邏輯／唔准重排 blocks。改完喺報告逐字貼出改動前後兩行。

### Fix 7 — `tests/check_tool_schema_stable.py`

約 **107-108 行**而家有：
```python
# KB-1: knowledge_lookup is forge-only while NeoForge support is paused.
assert forge_only == ["knowledge_lookup"], f"CAPABLE_TOOLS forge extras={forge_only}"
```
改為（**保留 assert 嚴格性**，只擴充已知 paused 例外集合）：
```python
# KB-1 / KB-2: Forge-only tools while NeoForge support is paused (issue #20).
assert forge_only == ["knowledge_lookup", "keybind_lookup"], f"CAPABLE_TOOLS forge extras={forge_only}"
```
⚠️ **唔准**刪 assert、唔准改成 `in`／`<=`／subset 檢查（要保留「任何非預期新名都會紅」）。

### Fix 8 — 新檔 `tests/check_keybind_lookup.py`

純 Python mirror 測試（風格照 `tests/check_worldgen_lookup.py`），無外部依賴，直接 `python tests/check_keybind_lookup.py` 跑得。要斷言：

1. **衝突分組**：同一 keyDisplay 綁 ≥2 個未綁=false 項目 → 標衝突；只有 1 個 → 唔標。
2. **未綁**：`isUnbound()==true` → 顯示「（未綁）」。
3. **行數上限**：>10 條命中 → 只出 10 行 ＋「另有 N 項」。
4. **命中篩選**：query 大小寫不影響；`namespace` 收窄有效。
5. **miss**：零命中 → 回 `[TOOL_MISS] keybind_lookup empty — do not invent`。
6. **意圖規則**：實作 Fix 5 嘅 7 族正則（同一份 literal），並讀
   `docs/research/artifacts/2026-10-10-question-corpus.json`（589 條真實問題；key `titles`）
   ＋ `docs/research/artifacts/2026-10-10-keybind-intent-probe.json`（key `positives` 有 18 條正例、key `rules`）。
   斷言：**18 條正例全部命中**、**589 條 corpus 內被標記 ≤1 條**。檔案唔存在 → 測試印 SKIP 但**唔准**紅（唔准為綠而刪 assert）。

## 3. 驗證（跑得到就跑；跑唔到寫 NOT RUN ＋命令）

```bash
cd forge/1.19.2
./gradlew.bat compileJava --console=plain --max-workers=2    # 期望 BUILD SUCCESSFUL
cd ../..
python tests/check_tool_schema_stable.py
python tests/check_keybind_lookup.py
python tests/check_ask_tool_loop.py
```

## 4. 報告（寫去 `C:\Users\skps9\AppData\Local\Temp\cursor_keybind_impl_report.md`，UTF-8）

逐項 Fix：狀態（DONE／NOT DONE）＋ `file:line`；Fix 6 要逐字貼改動前後；驗證命令＋原文結果（BUILD SUCCESSFUL／FAILED 原文）；Blocked 項明確列出；有冇改到清單以外嘅檔（要明講）。
**直接實作，唔好停低問問題、唔好 plan、唔好寫 review。**


---

## B. Fix 9（評分／排序／0 命中提示）

# Cursor Fix 9: keybind_lookup 過濾／排序要真係用得（v5.1）

Workspace：`C:\Users\skps9\Documents\Code_Project\super_minecraft_AI_player`

## 背景（實測證據，唔係推測）

用真資料（`docs/research/artifacts/2026-10-10-keybinds-AI_test_NFWC_DIM.json`，201 行；標籤係英文，例如 `[Elemental] Open trinkets pouch`）
跑現行過濾邏輯（`label.contains(query)`）：

| query | 命中 |
|---|---|
| `撳咩掣開 mod 清單` | **0** |
| `我點開個地圖？` | **0** |
| `快捷鍵` | **0** |
| `jei recipe key` | **0** |
| `清單` | **0** |
| `map` | 3 |
| `R` | **104**（垃圾 dump） |

⇒ 現行邏輯對真實玩家問句**永遠 miss**，對單字 key 就回一堆無排序結果。要改成「**評分＋排序**」，
並在 0 命中時回**有事實根據**嘅收窄提示（唔准作）。

## 只准改呢三個檔（其他一律唔准動）

1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/KeybindAskTool.java`
2. `tests/check_keybind_lookup.py`
3. `forge/1.19.2/src/main/java/com/skps9/packai/client/context/KeybindReader.java`（**只有**編譯需要時才改；`snapshot()` 簽名同行為要保持）
4. 報告：`%TEMP%\cursor_keybind_fix9_report.md`

## Fix 9.1 — 評分式匹配（取代 `contains` 硬性過濾）

- 由 `query` 抽 tokens：以空白切詞；另外所有 **CJK 連續字串**要加 **2 字同 3 字 n-gram**（中文無空白）。
- 每行評分（分數只作排序，唔可以外洩邏輯）：
  - `keyDisplay` 完全等於 token（不分大小寫）→ **+100**
  - `rawKey` 完全等於 token → **+80**
  - token 長度 ≥2 而 `label` 包含該 token → **+10 × token 長度**
  - token 長度 ≥2 而 `rawKey` 包含該 token → **+5**
  - `namespace` 等於 token → **+20**
- 只保留 score > 0 嘅行；按 score 由高到低排，同分按 `label` 字典序（穩定）。
- `key` 參數：完全等（keyDisplay 或 rawKey）優先、其次 contains；同樣要排序，唔可以原序 dump。
- `namespace` 參數：維持現行（不分大小寫等於比較）。
- `query` 留空且 `key`／`namespace` 都留空 → 維持現行（回全部頭 10 行）。
- ⚠️ 單字 token（長度 1，例如 `R`）**唔可以**做 label 子串匹配，只可以做「完全等於 keyDisplay／rawKey」。

## Fix 9.2 — 0 命中時回「有事實根據」嘅收窄提示

當 rows 非空但過濾後 0 命中：
- 第一行照回 `toolMissNote("")`（＝`[TOOL_MISS] keybind_lookup empty — do not invent`）。
- 之後加**一行**統計（數字必須由 snapshot 即時算出，唔可以硬編碼）：
  `已載入 {n} 個按鍵功能：{c} 組撞鍵、{u} 個未綁。可用 key=按鍵名（例 M）或 namespace=modid（例 jei）收窄。`
- 若 rows 本身係空（遊戲未 init）→ 只回 `toolMissNote("")`，**唔可以**加統計行（因為無資料）。

（中文短句字面量可以照寫——`logic/ItemSearchAskTool.java:46` 已經有「找不到，試改名/英文 id」嘅先例。）

## Fix 9.3 — 更新描述（引導模型傳啱參數）

`description()` 同 `llmDescription()` 保持同一句，改成（英文，單行）：
`Live keybind lookup. query=action/feature keyword (prefer a distinctive word from the mod's own label; for Chinese questions pass the feature keyword). key=key name like M or SPACE. namespace=modid like jei. Returns ranked matches, conflicts and unbound.`

## Fix 9.4 — mirror 測試要覆蓋真案例（`tests/check_keybind_lookup.py`）

新增／保持：
- 保留現有全部斷言（唔准刪）。
- 新增 `score_rows()` / `matcher` 嘅 Python 版（同 Java 用同一組規則同字面量），測：
  1. `query="open map"` → `"Open Map"` 排喺 `"Open Trinkets Pouch"` **之前**。
  2. `query="撳咩掣開 mod 清單"`（英文 label 資料）→ **0 命中**，而 `format_hits(rows, hits=[])` 回**含統計行**嘅文字（斷言含 `組撞鍵` 同 `個未綁`）。
  3. `key="R"` → 只保留 `keyDisplay`／`rawKey` 命中者，且輸出 ≤ 10 行（斷言；同時斷言包含 `…另有 N 項` 時格式正確）。
  4. `query="r"`（單字）＋英文 label → 唔准因為 label 含 'r' 而爆量（斷言命中數 < 全部行數，或等於完全等 key 嘅數）。
  5. 讀真 artifact `docs/research/artifacts/2026-10-10-keybinds-AI_test_NFWC_DIM.json`：`query="撳咩掣開 mod 清單"` 嘅命中 = 0（用真資料，唔准只用手造 fixture）。
- 結尾照印 `check_keybind_lookup OK`。

## Fix 9.5 — 驗證（必跑，把實際輸出貼落報告）

```
cd forge/1.19.2 && ./gradlew.bat compileJava --console=plain --max-workers=2 -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
python tests/check_keybind_lookup.py
python tests/check_tool_schema_stable.py
```

## 紅線

- 唔准改 `PackIndex.java` / `AskEngine.java` / `AskToolLoop.java` / `tests/check_tool_schema_stable.py`。
- 唔准改 neoforge 樹、唔准 build jar、唔准部署、唔准 git commit／push、唔准寫其他新檔。
- 唔准「為咗過測試」而放寬斷言；測試要測真行為。
- 報告列明每個 fix 嘅狀態＋實際命令輸出（唔准只寫 OK）。
