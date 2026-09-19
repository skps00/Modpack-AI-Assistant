# P0：`QuestGuide.stripQuestIcons` 嵌套 icon 崩潰（FTB 類 pack 全滅）修法計劃 **v3**

- 日期：2026-09-19（晚）／作者：Hermes／狀態：**v3，待 R3 反方 review**，未改任何 code
- 範圍：`forge/1.19.2`（MC 1.19.2、Forge 43.4.5、`mod_version=0.2.3`）；`neoforge/1.21.1` PAUSED 不碰
- **v3 改動（回應 R2 7:3）**：① A4 分母更正（19/19，1 個 `NO_SAMPLE` 無 trace）；② A5 **取消絕對數值帶**（sampler 每次重抽 ⇒ 分子分母必變），改成「有效性＋零改動證明＋描述性記錄」，並把分析腳本 `tools/analyze_cardplace.py` 納入白名單令量法可重現；③ A8(b) 重寫（**我 fuzz 窮舉 ~4,680 個輸入證實 post-D1 零 crash** ⇒ 造唔到「確定性可觸發嘅壞檔」；改成 counter 可讀 ＋ 明文承認 catch 分支無法單測 ＋ 真機 `skippedFiles=0` 觀察）；④ A7 加 **sha256 內容 baseline**（status 碼睇唔到已 dirty 檔再被改）＋ baseline 寫 `%TEMP%` 唔寫 repo ＋ 放行 `docs/`；⑤ T2f 期望值更正；⑥ 刪走 §7.4 幽靈檔 `QuestIndexFailSoftCheck.java`；⑦ catch scope 三項（per-invocation counter／壞檔定義／harness 定義）已對齊。
- 歷史：R1 **6:4**（`…-R1-opposing.md`）→ v2 → R2 **7:3**（`…-R2-opposing.md`）→ 本 v3。

---

## 0. TL;DR

`QuestGuide.stripQuestIcons` 掃 `\bicon\s*:` 時用「單調 `last` ＋ `out.append(text, last, m.start())`」；若 `icon:{…}` 區塊**內部再出現 `icon:`（嵌套）**，剝外層時 `last` 越過內層 match ⇒ `start > end` ⇒ `IndexOutOfBoundsException`。`QuestGuide.index()` 嘅 per-file try **只 catch `IOException`**（`:150`）⇒ `RuntimeException` 逃出 `forEach` ⇒ **整個 index 建立失敗** ⇒ 每個問題都變 `Query failed`。

- 修法：**D1 單調守衛（核心）＋ D2 per-file fail-soft（加固）＋ D3 repo-wide 同形站點審核**。
- **已發佈版本同樣有**（`git show HEAD:…QuestGuide.java` 確認 HEAD 版無守衛；該檔自 `c0365bb` 未改、工作樹乾淨；已部署 jar sha `06b5b129a114a233`）。

## 1. 症狀與證據（v2 修正：全部簽名 ＋ 改單位）

| 項 | 內容 |
|---|---|
| 症狀 | FTB Skies Expert sandbox：20 案例中 **19 個答案＝`Query failed: <IOBE msg>`**；`model.reply` 事件 **0**（冇叫過 LLM）；tokens 增量 **0** |
| 三種簽名（R1 逐行統計，我親核 `latest.log`） | `(742,696,1214)`×1、**`(758,710,1259)`×18**、`(466,415,780)`×（R1 於真檔切片重現，log 未見） |
| Stack | `IndexOutOfBoundsException` ← `QuestGuide.stripQuestIcons:1538` ← `itemsInRange:1471` ← `parseQuestsArray:1013` ← `parseFile:947` ← `QuestGuide.lambda$index$2:142` ← `index:123` ← `AskEngine.ask:263` |
| 證據檔 | `%TEMP%\autotest_results_20260919-213821\latest.log`（19×`AskEngine failed`、38 行 `Query failed`）；同目錄 20 條 trace |
| **正確指標（v2）** | **2 / 755 個任務 slice 崩**（R1 逐 top-level quest object 切片：obj#26 `len=1214` → `start 742, end 696`，`id=4EFD411CA5975754` "Starting Your Island"；obj#81 `len=780` → `466,415,780`，`id=4697678CA1F15CD6` "Viewing Dependencies"）；**檔層 = 1/51**；對照 E9E **0/1328**、主包 **0/2517** |
| ⚠️ 未證實項 | `(758,710,1259)`（18/19 次失敗嘅主簽名）**R1 NOT REPRODUCED**；推測章節檔喺 run 中途被 FTB Quests 重寫（`getting_started.snbt` mtime `21:40:54` 落喺第 1、2 次失敗之間）——**未證實，唔可以當事實** |
| 最小重現（Hermes 親驗，port 一致） | `icon: { Icon: "a" }` → current **CRASH(19,8,19)**；`icon: "minecraft:stone" tail` → 正常 |
| 版本紀律（**v2 更正推論方向**） | **FTB Skies Expert 與 E9E 用同一支 `ftb-quests-forge-1902.5.10-build.497.jar`**，但 FTB 崩、E9E 唔崩 ⇒ **判別因素係 pack 內容（有冇嵌套 `tag:{ Icon: … }`），唔係版本**。主包係另一版本（`1902.5.9-build.399`）但同樣內容唔觸發。保留版本號做記錄，**唔可以**讀成「1902.5.9 安全、1902.5.10 中招」 |
| port 保真度（v2 註明） | 我同 R1 嘅 Python port 係**近似**：`Python isspace()` ≠ Java `Character.isWhitespace`（NBSP `\u00a0`：port 報 CRASH、**Java 唔會**）；Java offset 係 UTF-16 code unit（本檔 0 個 non-BMP，今次數字巧合對得上）。⇒ 結論唔靠 port 單獨支撐，Java 側有真 stack trace 實錘 |
| 附錄輸入完整性（Hermes 新增） | R1 附表部分輸入字串**被省略號截短**（例：`icon: { a: { Icon: "x" } Icon: "y" }` 佢報 D1 `' item: …'`、我實測 `''`）⇒ v2 §4 所有 fixture **逐字寫全**，唔准用省略號 |
| **post-D1 窮舉（v3 新增，供 A8(b) 用）** | 用同一 port 窮舉 4-token 內（`icon:`／`icon: `／`{`／`}`／`"`／`a`／空格／`Icon:` 共 ~4,680 組合）：**加咗 D1 守衛後 0 個輸入仍會 crash** ⇒ 現實中冇「確定性可觸發」嘅 post-D1 例外輸入（所以 A8(b) 唔可以用壞檔測 catch 分支） |

## 2. 根因（逐行；R1 已獨立確認）

```java
static String stripQuestIcons(String text) {          // :1529
    Matcher m = Pattern.compile("\\bicon\\s*:", CASE_INSENSITIVE).matcher(text);
    int last = 0; StringBuilder out = new StringBuilder(text.length());
    while (m.find()) {
        out.append(text, last, m.start());            // :1538 ← 爆炸（last > m.start()）
        int i = m.end(); … 跳空白；'"' 掃到收引號；'{' 掃到配對 '}'（含字串／轉義）
        last = i;                                     // i 可越過「下一個 match」嘅 start
    }
    out.append(text, last, text.length());
}
```

`find()` 只向前掃、唔識嵌套；`icon:{… { Icon: … } …}` 令 `last` 一跳跳過內層 match ⇒ 下一圈 `append(text, last, m.start())` 拋 IOBE。而 `index()` 嘅 catch 只接 `IOException`（`:150`）⇒ 異常逃出 `forEach`（`:123`）⇒ 全檔索引失敗。

## 3. 修法

### D1（核心，最小）— 單調守衛

```java
while (m.find()) {
    if (m.start() < last) {          // 嵌套：match 落喺已剝走區間 ⇒ 跳過
        continue;
    }
    out.append(text, last, m.start());
    …
}
```

**語意宣稱（v2 收窄，回應 F4）**：
- **well-formed SNBT**：正確（R1 V8 實證：真 slice 輸出 = 輸入減去**恰好一個** icon block（116/121 chars）、`icon:` 殘留 0、item 抽得到）。
- ⚠️ **畸形 SNBT（unterminated `{`）**：會令尾部內容被靜默截走（**既有行為**，但 D1 令佢由「偶然爆」變「永遠靜默」）。例：`head icon: { Count: 1b tag: { Icon: "a" item: "minecraft:stone" tail icon: "b" end` → D1 輸出 `'head '`（`item:`／`tail`／`end` 全失）。
- **對策（v2 新增）**：跳過時加 **一行診斷**（`PackAiMod.LOGGER.debug`，帶 `last`／`m.start()`／`text.length()`，只喺 `last > m.start()` 時出，唔會洗版）；並把「unterminated `{` 會截尾」寫入 `REMAINING_WORK.md`（唔喺 P0 範圍）。
- D1 令 `last` 只單調前進 ⇒ 結構上唔可能再出現 `start < last`。

### D2（加固）— per-file fail-soft（**v2 寫死 scope，回應 F7**）

| 要求 | 寫死內容 |
|---|---|
| catch 位置 | **`QuestGuide.java:137-152` 個 lambda body 內部**（唔准喺 `:95`／`:123` 公開 overload 層——嗰層 catch 等於整個 walk 一次 fail-soft，之後檔案唔再讀，同需求相反） |
| catch 類型 | 只 `catch (RuntimeException e)`；**唔准** `Throwable`（OOM／SOE 要照爆） |
| logging | **每檔最多一次 `WARN`**：`p.getFileName()` ＋例外 class ＋ message（**唔准**印檔內容／答案）；stack 落 `DEBUG` |
| 可數性 | 加 **`skippedFiles` counter**（例：`AskTrace` event `packai.index.skippedQuestFiles=N`；`QuestGuide.index()` 回傳值或 out-param 皆可）⇒ 「靜默缺檔」永遠可被發現（SK「唔准 fake success」） |
| partial state 交代 | `parseQuestsArray` 喺拋之前會往 `spoilerIds` add（`:1004`）但 `Hit` 全丟（`:1017` `out` 未 return）⇒ 該檔**全有或全無**（命中全失）；`spoilerIds` 可能殘留半截（`filterHidden` 靠 id 唯一 ⇒ 實務無害，但要寫一行註釋）。**v2 明文寫入 plan** |
| 測試 | **新增 fail-soft harness**：fixture gameDir 內放 1 個壞 `.snbt`（嵌套 icon）＋1 個好檔 ⇒ 斷言：好檔照樣索引到、`skippedFiles=1`（見 A8） |

### D3（同形站點審核；**v2 改成 repo-wide grep ＋ 逐條 verdict，回應 F12**）

命令：`grep -rn "append([^,]*, *last" src/main/java` ＋ `grep -rn "last = " src/main/java` 交叉 —— 逐條寫 verdict（**安全**／**已守衛**／**要改**／**無證據唔改**）：

| 站點 | 現況 verdict（R1 讀碼＋我覆核） | 行動 |
|---|---|---|
| `AskReplyScrub:1433`（HEAD） | **已守衛**（`if (m.start() < last)`，同 D1 同一 pattern）→ 證明呢個 fix 早已被 codebase 接受 | 唔改（列為先例證據） |
| `AskReplyScrub:1420` `shiftLeadingList` | `last = m.end()` ＋ `find()` 遞增 ⇒ **結構性安全** | 唔改 |
| `AskReplyScrub:1227` | spans 由 `howToGetSpans` 產生、`from = Math.max(start+1,end)`（`:1246-1248`）⇒ 遞增不重疊 ⇒ **安全** | 唔改 |
| `OfficialDisplay:175` | `absStart = matchStart + localStart`（`:270-271`，`localStart >= 0`）⇒ **安全** | 唔改 |
| `RecipeEmbed:405/418/560/600/1655/1665` | 全部 `last = m.end()` ⇒ **安全** | 唔改 |
| 其他新發現站點 | **逐條補 verdict**（D3 完成定義＝每條都有 verdict） | 只改證實可嵌套者 |

**D3 完成定義（v2 新增）**：合格 = (a) 已跑上述兩條 grep，命中清單**完整列於 plan 附錄**；(b) 每條有 verdict ＋ 理由（引行號）；(c) 任何「要改」項都有可重現輸入。**冇 (a)–(c) = D3 未完成**。

## 4. 測試（**v2：逐字 expected string**；全部 Hermes 親跑 port 覆核過）

新檔：`forge/1.19.2/src/test/java/com/skps9/packai/logic/QuestGuideStripIconsCheck.java`
Task 名：**`runQuestGuideStripIconsCheck`**；harness 契約（house style）：`public static void main` ＋ `assert`（靠 `jvmArgs '-ea'`）＋失敗拋 `AssertionError` ＋尾行 `System.out.println("… OK")`（無 junit 依賴）。

| ID | 輸入（逐字） | 期望（逐字） |
|---|---|---|
| T1a | `icon: { Icon: "a" }` | `""`（current：CRASH(19,8,19)） |
| T1b | `icon:{Icon:"a"}` | `""`（current：CRASH(15,6,15)） |
| T1c | `ICON: { Icon: "a" }` | `""`（大小寫不分） |
| T1d | `{ icon:{ Icon:"x" } }` | `"{  }"` |
| T1e | `pre icon: { Count: 1b id: "ftbquests:custom_icon" tag: { Icon: "ftbteams:x" } } post` | `"pre  post"`（**雙空格**，要寫死） |
| T2（負控 · 平 icon） | `icon: "minecraft:stone" tail` | `" tail"`（前置空格） |
| T2b | `icon: "a" item: "minecraft:stone" icon: "b"` | `' item: "minecraft:stone" '` |
| T2c | `icon:` | `""` |
| T2d | `icon: }` | `"}"` |
| T2e | `icon: 5 item: "minecraft:stone"` | `'5 item: "minecraft:stone"'` |
| T2f | `icon: "a\"b" item: "minecraft:stone"` | `' item: "minecraft:stone"'`（**v3 更正**：icon 值（含轉義引號）被剝走、前面文字一齊走；v2 寫「不變」係錯） |
| T3a（unterminated `{`，**v2 新增**） | `head icon: { Count: 1b tag: { Icon: "a" item: "minecraft:stone" tail icon: "b" end` | `"head "`（**已知語意損失**，要寫死＋註釋） |
| T3b（unterminated，無後續 match） | `head icon: { Count: 1b item: "minecraft:stone" tail` | `"head "`（既有行為，D1 唔改） |
| T3c | `icon: { Icon: "a" }   \n\t ` | `"   \n\t "` |
| T3d（**既有缺陷，唔喺 P0 範圍**） | `desc: "text icon: more" item: "minecraft:stone"` | `'desc: "text more" item: "minecraft:stone"'`（字串內 `icon:` 被剝 ⇒ 字串改爛；current == D1） |
| T3e（同上） | `desc: "see icon:"` | `'desc: "see '`（收尾引號連後文一齊失） |
| T4（FTB 真形狀 · **v2：用合成 fixture，唔入第三方原文**） | `pre icon: { Count: 1b id: "ftbquests:custom_icon" tag: { Icon: "packai:test_icon" } } post` | `"pre  post"`；＋斷言輸出零 `Icon:`／零 `custom_icon` 殘留 |
| T5 | `null` → `""`；`""` → `""`；`"plain"` → `"plain"` | 同現行為 |

**fixture 政策（回應 F10）**：**唔會** commit FTB Skies Expert 嘅任務原文（第三方內容）；改用**同等結構嘅合成 fixture**（T1e／T4，已實證 reproduces 同一 signature）＋「真檔驗證」擺喺真機層 A8（用 sandbox 現成檔案，run 時記錄 3 個 quest 檔 sha256）。

**負控（v2：逐條點名，回應 F6）**：
- 移除 D1 後**必須翻紅**嘅 assertion：T1a／T1b／T1c／T1d／T1e／T3a、T4、A8 嘅 Java 層。
- **明文記錄**：T2*、T3b–T3e、T5 **本來就唔會翻紅**（佢們唔經 `start < last` 分支）⇒ 負控只證明「嵌套子集」被測到，唔可以宣稱全部案例都測到 bug。

## 5. 驗收標準（**v2：全部 pre-registered 數值**）

| ID | 判準 | 量法 |
|---|---|---|
| A1 | `./gradlew.bat compileJava compileTestJava` rc=0 | 親跑，貼輸出尾 5 行 |
| A2 | Java harness **50/50 全綠**（原 49 ＋ `runQuestGuideStripIconsCheck`）；跑前**先 `python research/gen_tmp_check.py` 重生 `tmp-check.gradle`**（該檔係 AUTO-GENERATED，手改會被覆蓋）；hit 數 49→50 要貼 | 親跑 |
| A3 | python 閘：**123 檔＝122 rc=0 ＋ 1 rc=2（`tests/check_ask_display_leak.py`，已知資料不足）**，**0 新紅**；baseline 檔 `%TEMP%\gate_baseline_20260919.txt`（今晚實測）**逐行 diff** | 親跑＋`comm` |
| A4 | 真機 FTB：**19/19 有 trace 嘅 case 各自 ≥1 個 `model.reply`** ＋ tokens 增量 > 0 ＋ body **零** `Query failed`（**v3 更正分母**：harness 派 20 個 case，其中 1 個 `NO_SAMPLE` 無 trace ⇒ 分母寫死 19，唔准用「有效案例」伸縮講法）；**明文：`status.ok`／`cardsOut` 唔算證據** | 真機 + trace 解析 |
| A5 | 回歸（**v3 重寫，回應 R2**：因為 sampler 每次重新隨機抽樣，樣本分子分母必然變動 ⇒ 絕對數值帶**唔健全**，取消）：(i) **有效性**：主包／E9E 兩輪各自「有 trace 嘅 case 全部 ≥1 `model.reply`、零 `Query failed`」；(ii) **零改動證明**：`git diff` 顯示落位相關檔（`RecipeEmbed.java`／`AskService.java` 卡路徑／`RecipeCard.java`）**零改動** ⇒ P0 修法結構上唔影響卡落位；(iii) **描述性記錄**（唔做閘）：用已入庫嘅 `tools/analyze_cardplace.py`（**v3 新增白名單項**）記錄今輪 `adjacentCardPairs` 樣本率，同今晚 baseline（主包 54/316、E9E 0/73）並列供人比較；(iv) 決定性保護由 50 個 Java check ＋ `tests/check_quest_strip_icons.py` 承擔 | 真機 + 同一已入庫腳本 |
| A6 | 負控（§4）逐條翻紅 → 還原後全綠 | 親手 |
| A7 | **baseline diff（v3 強化，回應 R2）**：(a) 動手前 `git status --porcelain > %TEMP%\baseline_<ts>.txt`（**唔准寫入 repo**）；(b) **同時**記錄白名單每個檔嘅 **sha256** 到 `%TEMP%\baseline_sha_<ts>.txt`——因為 `status` 碼睇唔到「已經 dirty 嘅檔再被改內容」（例：`AskReplyScrub.java` 本身已 `M`，再改仍然只顯示 ` M`）；(c) 收工後：`status` 逐行 diff **只准**白名單檔 ＋ `docs/`（HANDOFF／plan／review）＋ baseline 自身；任何其他 M/??/D = FAIL；白名單檔 sha256 若變但唔喺預期清單 = FAIL | `diff` ＋ `sha256sum -c` |
| **A8（v3 重寫，回應 F1＋R2）** | **headless index 內容斷言**（實作落點：**擴充已存在嘅 `QuestGuideIdCheck.java`**——佢已經用 temp gameDir 叫 `QuestGuide.index(root, List.of("ftbquests"), null, false)`（`:59`），係現成可行 pattern）：<br>**(a) 內容真係入 index（判別力已由 R2 獨立驗證）**：fixture gameDir 放**合成** quest 檔（含嵌套 `icon:{…Icon:…}`）→ assert 返回到該 quest id ＋ items 含指定 id。**只加 D2 時必須紅**（因為壞檔被 skip ⇒ 內容唔入 index）。<br>**(b) fail-soft 機制（v3 改：唔再靠「造一個會拋嘅檔」）**：我窮舉 4-token 內（`icon:`／`icon: `／`{`／`}`／`"`／`a`／空格／`Icon:`）共 ~4,680 組合，**post-D1 零 crash** ⇒ 現實中**冇**確定性可觸發嘅 post-D1 例外輸入 ⇒ 唔可以用「壞檔」測 catch 分支。改成：(i) fixture 全好檔 → assert `skippedFiles == 0`（counter 存在且可讀）；(ii) **明文承認** catch 分支無法用單元測試確定性觸發，理由（fuzz 證據）寫入 plan ＋ `REMAINING_WORK.md`；(iii) 真機層以 `skippedFiles == 0` 做觀察點（無聲缺檔會即刻現形）。<br>**(c) 真機層（v3 具體化）**：FTB run 嘅 `latest.log` **零** `AskEngine failed`；19/19 有 trace 嘅 case 有 `model.reply`；**≥1 個 case 嘅 trace quest hits 非空**（用該次 run 自己嘅 quest 檔內容對，避免硬編版本 id） | 親跑 |
| A9 | jar **唔准**部署真 instance（真 instance jar sha 頭 16 位保持 `06b5b129a114a233`）；沙盒部署只准 `--mods <sandbox>/minecraft/mods` | 親核 sha |

> A8 係 P0 修法嘅**唯一防假綠閘**：只加 D2（唔加 D1）時，A1–A7 會全綠但兩個任務內容**永遠唔入 index** ⇒ A8 必須紅。
> ⚠️ **可數性設計約束**：counter **必須 per-invocation**（例如 `index(...)` 加一個 out-param／回傳小 result 物件），**唔准**用可變 static 欄位——`AskEngine.ask` 可能喺 worker thread 跑，static 計數會有 race（R2 請裁決用邊種）。

## 6. 白名單（**v2 補全**）

1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/QuestGuide.java`（D1＋D2＋`lastSkippedFiles()` accessor；工作樹**乾淨** ✓ 可用 `git checkout --` 還原）
2. `forge/1.19.2/src/test/java/com/skps9/packai/logic/QuestGuideStripIconsCheck.java`（**新增**）
3. `forge/1.19.2/src/test/java/com/skps9/packai/logic/QuestGuideIdCheck.java`（**擴充**：A8 內容斷言＋fail-soft 案例；已存在、已註冊 harness）
4. `forge/1.19.2/tmp-check.gradle`（**AUTO-GENERATED、untracked** ⇒ 唔手改，跑 `python research/gen_tmp_check.py` 重生）
5. **`tests/check_quest_strip_icons.py`（v2 新增，回應 F8）**：呢個係 `stripQuestIcons` 嘅**第二份實作（Python mirror）**，現時只測非嵌套 2 條。決定：**同步**加守衛 ＋ 加嵌套 assert（否則 Java 有守衛、mirror 冇 ⇒ 永久漂移，而 A3 永遠測唔到）。若 R2 反對同步 → 替代：明文記入 `REMAINING_WORK.md`（唔准「默認放任」）
6. 條件式：`logic/AskReplyScrub.java`／`logic/OfficialDisplay.java`（**只喺 D3 證實要改時**）——⚠️ 兩者**都唔係乾淨**（見 §7），要特別還原程序
7. `tools/analyze_cardplace.py`（**v3 新增**：把今晚用嘅 cardplace 分析腳本入庫，令 A5(iii) 嘅量法可重現）
8. `docs/plans/*`／`.hermes/plans/HANDOFF.md`／`REMAINING_WORK.md`（記錄 D3 掃描結果、unterminated 截尾已知限制、D2 catch 分支無法確定性測試）
9. `forge/1.19.2/tmp-check.gradle` 由 `research/gen_tmp_check.py` 自動重生（**唔算手改**）

## 7. 還原方案（**v2 重寫，回應 F2**）

**實況（今晚親核）**：工作樹**唔乾淨**——`git status --porcelain` **128 項**，其中
- `logic/AskReplyScrub.java` = **M（已有未提交改動）**；`neoforge/…/AskReplyScrub.java` = M
- `logic/OfficialDisplay.java` = **?? untracked（從未入 git）**
- 2 個**已刪除**檔：`client/gui/PackAiSettingsScreen.java`、`client/gui/RecipeCategoryScreen.java`
⇒ 「回到現狀 ≠ `git checkout --`」，plan v1 嘅前提**係錯嘅**。

**正確程序**：
1. 動手前：`git status --porcelain > %TEMP%\baseline-<ts>.txt`（另存）＋記 `git rev-parse HEAD`。
2. 每個白名單檔**先 copy 一份**到 `%TEMP%\p0_backup_<ts>\`（含 untracked 嘅 `OfficialDisplay.java`）。
3. **唔准**對「已 M」或「untracked」嘅白名單檔用裸 `git checkout --`（會清走未提交工作／直接報 pathspec 錯）。
4. 還原 = 還原 backup（`cp` 返）＋ `rm` 新增檔（**`QuestGuideStripIconsCheck.java`**；fail-soft 案例放喺 `QuestGuideIdCheck.java`，**唔准開第二個 harness／第二個檔**——`gen_tmp_check.py` 係 `rglob("*Check.java")` 全自動收集，多開一個檔會令 A2 變 51/51 且 A7 見到非白名單 `??` ⇒ FAIL）（**v3 修：刪走 v2 §7.4 提到嘅幽靈檔 `QuestIndexFailSoftCheck.java`**）＋ 若同步過 mirror 就還原 mirror；`QuestGuide.java` 因乾淨可用 `git checkout --`。
5. 驗證還原成功：`git status --porcelain` 同 baseline **逐行一致**；A2／A3 回到「49 綠／122+1」。
6. 部署層：全程唔碰真 instance（jar sha 不變）；沙盒係複製品，最壞刪目錄重複製（`packai_sandbox_ftb` 0.58 GB／`_e9e` 0.37 GB）。

## 8. 唔准郁

- `RecipeEmbed` 落位邏輯、trace 事件名／欄位語義、prompt／scrub 行為（除 D3 證實者）、`neoforge/1.21.1`、根 `AGENTS.md`、真 instance jar。
- 唔准 hot-copy jar；沙盒部署只准 `--mods <sandbox>/minecraft/mods`，**禁** `--target packai`。
- 唔准 commit（等 SK 批）。唔准用 `status.ok`／`cardsOut` 當證據。

## 9. 成本與時序

- plan v2 ＋ R2 review：~15 分鐘；cursor 實作：~20–30 分鐘；A1–A3＋A6＋A8 headless：~10 分鐘。
- 真機（A4／A5）：每 pack ~10 分鐘遊戲時間（**要 SK 唔打機**）＝ FTB＋主包＋E9E ≈ 30 分鐘、~150 萬 tokens（DS 空閒時段）。
- 閘嘅已知局限：Java harness 係 **local-only**（`tmp-check.gradle` 不入庫、冇 CI）⇒ 呢個 fix 嘅唯一自動保護係本機 harness ＋ `tests/check_quest_strip_icons.py`（入庫）；**已記入 REMAINING_WORK**。
