# P0：`QuestGuide.stripQuestIcons` 嵌套 icon 崩潰（FTB 類 pack 全滅）修法計劃

- 日期：2026-09-19（晚）／作者：Hermes／狀態：**待反方 review（R1）**，未改任何 code
- 範圍：`forge/1.19.2`（Forge 1.19.2，`mod_version=0.2.3`）
- 觸發者：SK「test on diff pack」→ FTB Skies Expert 對照 run

---

## 0. TL;DR

`QuestGuide.stripQuestIcons` 用「單調 `last` ＋ `out.append(text, last, m.start())`」掃 `\bicon\s*:`。若一個 `icon:{…}` 區塊**內部再出現 `icon:`（嵌套，例如 FTB 的 `tag:{ Icon:… }`，大小寫不分）**，剝外層時 `last` 已越過內層 match 位置 ⇒ `start > end` ⇒ `IndexOutOfBoundsException`。`QuestGuide.index()` 冇 per-file 保護 ⇒ **一個任務檔令整個 mod 嘅答案全滅**（每問變 `Query failed: …`）。

- 影響面：**任何 pack 嘅任務檔含嵌套 icon 就中招**（FTB Skies Expert 實測 1/51 檔即觸發）。
- **已發佈版本同樣有**（`QuestGuide.java` 自 08-16 `c0365bb` 未改；已部署 jar `06b5b129a114` 反編譯確認含 `stripQuestIcons`）⇒ 真實玩家中招。
- 修法：**D1 單調守衛（3 行，核心）＋ D2 `index()` 逐檔 fail-soft（加固）＋ D3 同類站點審核（4 處）**。

---

## 1. 症狀與證據（全部 Hermes 親核，可重跑）

| 項 | 內容 |
|---|---|
| 症狀 | FTB Skies Expert：19/20 案例答案＝`Query failed: start 742, end 696, length 1214`；`model.reply.*` 事件 0（**冇叫過 LLM**）；tokens 增量 0 |
| Stack | `IndexOutOfBoundsException` ← `QuestGuide.stripQuestIcons:1538` ← `itemsInRange:1471` ← `parseQuestsArray:1013` ← `parseFile:947` ← `index:123` ← `AskEngine.ask:263` |
| 證據檔 | `%TEMP%\autotest_results_20260919-213821\latest.log`（19 次 `AskEngine failed`）；同目錄 20 條 trace |
| 內容差異 | FTB `config/ftbquests/quests/chapters/getting_started.snbt` 有 `icon: { Count: 1b id: "ftbquests:custom_icon" tag: { Icon: "ftbteams:textures/teams.png" } }` |
| 觸發分佈（Python 逐字移植同一算法掃真檔） | FTB **1/51**、E9E **0/44**、主包 **0/60** |
| 最小重現（已驗） | `icon: { Icon: "a" }` → 崩（last=19 > start=8）；`icon: "x"` → 正常（負控） |
| 版本紀律（SK 要求） | MC 1.19.2 ＋ Forge 43.4.5 ＋ **FTB Quests 1902.5.10**（FTB Skies Expert）；主包 FTB Quests **1902.5.9**（無嵌套 → 不觸發）⇒ **跨版本／跨 pack 內容差異**，結論唔可互套 |

## 2. 根因（逐行）

```java
static String stripQuestIcons(String text) {          // QuestGuide.java:1529
    Pattern iconKey = Pattern.compile("\\bicon\\s*:", Pattern.CASE_INSENSITIVE);
    Matcher m = iconKey.matcher(text);
    StringBuilder out = new StringBuilder(text.length());
    int last = 0;
    while (m.find()) {
        out.append(text, last, m.start());            // :1538 ← 爆炸點（last > m.start()）
        int i = m.end();
        ... 跳空白；若 '"' 掃到收引號；若 '{' 掃到配對 '}'（含字串／轉義處理）
        last = i;                                     // i 可以越過「下一個 match」嘅 start
    }
    out.append(text, last, text.length());
}
```

`Matcher.find()` 只向前掃；唔知「內層」概念。當 `icon:{...}` 區塊**自己包住另一個 `icon:`**，`last` 一跳就跳過咗內層 match 嘅位置；下一圈 `m.find()` 回傳內層位置（< last）⇒ `append(text, last, m.start())` 直接拋 `IndexOutOfBoundsException`。

## 3. 修法

### D1（核心，最小）— 單調守衛

```java
while (m.find()) {
    if (m.start() < last) {   // 嵌套：match 落喺已經剝走嘅區塊內 ⇒ 跳過
        continue;
    }
    out.append(text, last, m.start());
    ...
}
```

理由：外層 `icon:{…}` 區塊係整塊移除，內層 `icon:` 屬同一塊、唔應該獨立處理 ⇒ 跳過係**語意正確**（唔會漏剝應剝嘅裝飾 icon，亦唔會多剝正文）。

**已知邊界（要 reviewer 檢）**：
- 若 `icon:` 出現在**字串值內部**（例：`title: "use icon: like this"`）而該字串已被剝走 → 同樣守衛會跳過（＝唔會再剝）✓ 可接受（原本行為本身就會誤剝字串）。
- 守衛令 `last` 只單調前進 ⇒ 結構上唔可能再出現 `start < last`。

### D2（加固）— `QuestGuide.index()` 逐檔 fail-soft

需求：**單一壞檔唔可以令全部答案失敗**（今次就係 1/51 檔拖垮整個 mod）。
- 喺 `index()`（`:95`／`:123`）逐檔 `try/catch (Exception e)`：記錄 `WARN`（檔名＋例外類型，**唔准印內容**），continue 其餘檔案。
- 已有快取（`mechanic-cache`／`item-index`）：失敗檔案唔入快取，但唔影響其他檔。
- reviewer 需檢查：catch 範圍會唔會過闊（吞掉真 bug）→ 提議只 catch `RuntimeException` 並喺 catch 內 `AskTrace`／log 一次（每個檔最多一次），保留可診斷性。

### D3（同類站點審核）— 逐個確認，**只修證實可嵌套者**

| 站點 | pattern／形狀 | 初步判斷 | 行動 |
|---|---|---|---|
| `AskReplyScrub:1420` | `LINE_START_NUM`（行首編號） | 前綴區塊，難自我嵌套 | 審核；若無證據 → **唔改** |
| `AskReplyScrub:1482` | `TOOLS_JSON_START`（工具 JSON） | JSON 可含同 marker | **要驗**；證實 → 同一守衛 |
| `AskReplyScrub:1227` | `sp[]` 位置對（非 find 迴圈） | 順序靠 resolver | **要驗** `sp[0] <= sp[1]` 單調性 |
| `OfficialDisplay:175` | `resolved.absStart/absEnd` | 多個 resolve 併接，可重疊 | **要驗** `absStart >= last` |

原則：**冇證據唔改**（避免無謂 diff）；每個「要驗」都要有可重現輸入或明文聲明「維持現狀＋記入 `REMAINING_WORK`」。

## 4. 測試

### 新增 `forge/1.19.2/src/test/java/com/skps9/packai/logic/QuestGuideStripIconsCheck.java`

| ID | 輸入 | 斷言 |
|---|---|---|
| T1 | `icon: { Icon: "a" }` | **唔拋例外**；輸出＝`""`（整塊剝走） |
| T2 | `icon: "minecraft:stone" tail` | **負控**：`icon: "…"` 剝走、`tail` 保留 |
| T3 | 變體矩陣：`ICON:`／多層嵌套（`icon:{ Icon:"a" }` 內再嵌）／`icon:` 喺字串內／`icon:` 喺字串最尾／`icon:` 喺位置 0／`icon:` 後接 `}`／`{ icon:{ Icon:"x" } }`（FTB 真形狀） | 全部唔拋；輸出無 `icon` 值殘留（除字串內者，另列期望） |
| T4 | FTB 真形狀合成片段（`icon:{ Count:1b id:"ftbquests:custom_icon" tag:{ Icon:"…" } }` ＋前後正文） | 唔拋；正文逐字保留；icon 塊全走 |
| T5 | 空字串／`null`／純文字 | 同現行為（`null`→`""`） |

- 註冊：加入 `forge/1.19.2/tmp-check.gradle`（本地 harness 清單，untracked）→ 檢查總數 49 → **50**。
- **負控（必做，Hermes 親手）**：暫時移除 D1 守衛 → T1/T3/T4 應**變紅**；還原後轉綠（證明測試真係偵測到 bug，唔係幽靈閘）。

## 5. 驗收標準（機械可驗）

- A1 `./gradlew.bat compileJava compileTestJava` rc=0。
- A2 Java harness **50/50 全綠**（原 49 ＋ 新 1）。
- A3 `python tests/check_*.py` **≥122 綠 ＋ 1 已知**（`check_ask_display_leak.py` 資料不足 rc=2），**0 新紅**。
- A4 **真機（FTB sandbox）**：重跑 `cases_ftb.json` → 有效案例 **≥90%** 有真 LLM 答案；body **零** `Query failed`；tokens 增量 > 0。
- A5 **回歸（主包＋E9E）**：重跑同一 `cases_*.json` → 每案例有真答案；主包 `adjacentCardPairs` 樣本率同今次基準（54/316）**同一量級**（±唔准惡化到 0 或倍增）。
- A6 負控：見 §4。
- A7 白名單外零改動（`git diff --stat` 核）；jar **唔准**部署到真 instance（沙盒 only）。

## 6. 白名單（准改）

1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/QuestGuide.java`（D1＋D2）
2. `forge/1.19.2/src/test/java/com/skps9/packai/logic/QuestGuideStripIconsCheck.java`（**新增**）
3. `forge/1.19.2/tmp-check.gradle`（本地 harness 註冊；untracked）
4. 條件式：`logic/AskReplyScrub.java`／`logic/OfficialDisplay.java`（**只喺 D3 證實有可重現輸入時**，並喺 plan 附上輸入）

## 7. 還原方案（具體、可驗）

- 改動前：`git stash list`／`git diff` 為零 → 只需 `git checkout -- <白名單檔>` ＋ `rm` 新測試檔即可回到現狀。
- 部署層：**全程唔部署真 instance**（jar 只入沙盒）；真 instance jar sha `06b5b129a114` 保持不變（驗 sha256 頭 16 位）。
- 沙盒可即時重建：`packai_sandbox_ftb`／`_e9e` 係複製品；最壞情況刪沙盒目錄重複製（0.6 GB／0.36 GB）。
- 驗證正常：A2／A3 全綠 ＋ A4 真機恢復。

## 8. 唔准郁

- `RecipeEmbed` 落位邏輯、trace 事件名／欄位、prompt／scrub 行為（除 D3 證實者）、`neoforge/1.21.1`、`AGENTS.md`、真 instance jar。
- 唔准 hot-copy jar；沙盒部署只准 `--mods <sandbox>/minecraft/mods`，**禁** `--target packai`。
- 唔准 commit（等 SK 批）。

## 9. 成本與時序

- 實作（cursor）：~15–25 分鐘；review R1：~4 分鐘；驗收（A1–A3）：~5 分鐘。
- 真機（A4／A5）：每個 pack ~10 分鐘遊戲時間（**要 SK 唔打機**）；FTB ＋ 主包 ＋ E9E ＝ ~30 分鐘。
- token：真機 3 輪 ≈ 150 萬（DS 空閒時段做）。
