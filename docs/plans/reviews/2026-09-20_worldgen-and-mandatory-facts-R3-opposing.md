# R3（有界反方）— `2026-09-20-worldgen-and-mandatory-facts.md` **v3**

- 日期：2026-09-20｜裁判：Hermes（反方／有界輪）｜範圍：**只核 R2 餘下 2 條**（a-1 `idFromPath` case DIMENSION、b-2 全出口表＋hook 順序閘）；唔加新要求（除 v3 自相矛盾／新引入錯）。
- 版本鎖：`md5sum docs/plans/2026-09-20-worldgen-and-mandatory-facts.md` → **`82c63f7e18f80be0d3ade5ad39216528`**（＝任務指定，**PASS**，非 VERSION MISMATCH）。
- 前置：R1 正方 3 : 反方 7；R2 正方 7 : 反方 3（餘 2 條）。**READ-ONLY**：本輪只寫本檔，無改 repo 其他檔、無 commit、無 build、無開遊戲。

---

## ① a-1：`idFromPath()` 的 `case DIMENSION -> "dimension/";` — **RESOLVED**

| 事實（本輪親跑） | 證據（file:line） |
|---|---|
| `kindFromPath()` 7 個 branch 全無 `dimension/` ⇒ 維度檔今日 `kindFromPath` 回 `null`、被 `isWorldgenPath` 擋 | `logic/WorldgenFacts.java:100-127`（:126 `return null;`）、`:129-131` |
| `idFromPath()` 內部 `switch (kind)` 只列 5 個 case ＋ `default -> ""`；DIMENSION 落 default ⇒ `folder.isEmpty()` ⇒ **回 `""`** | `logic/WorldgenFacts.java:155-162`（:161 `default -> "";`）、`:163-165` |
| `ingest()` 因 `id.isEmpty()` **靜默 return** ⇒ 維度檔全丟（即使 kind 認得）→ v3 撤回「無需改」**陳述正確** | `logic/WorldgenFacts.java:183-187`（:185） |
| `ingest()` 的 `switch (kind)`（arrow switch statement，Java 17 對 enum **唔強制窮盡**）無 DIMENSION ⇒ 新增 enum 常數會變 **no-op**，v3 第 2 點確實必要 | `logic/WorldgenFacts.java:192-205`（BIOME/STRUCTURE/STRUCTURE_SET/CONFIGURED/PLACED/TAG/MODIFIER 七 case） |
| `Store` 無 `biomeToDim`／`dimensionOf`（今日 0 hits）⇒ v3 第 4 點必要 | `logic/WorldgenFacts.java:464-471`（7 個 `LinkedHashMap`，無 dim map） |
| **全 repo `WorldgenFacts.Kind` 使用點清點**：只有 `:145`（TAG）、`:152`（MODIFIER）、`:176`（TAG 三元）＋ 7 個 `kindFromPath` return ＋ `switch (kind)` 於 `:155`／`:192`；`JarLightIndex.java:280` 係**另一個** enum（無關）⇒ v3「4 處」**無漏** | `grep -rn "Kind\." --include=*.java .`；`grep -n "switch (kind)" -r .` → 只有 `WorldgenFacts:155`／`:192` |
| **A-a1c 自洽**：`data/ad_astra/dimension/moon.json` → `afterNamespace`＝`dimension/moon.json` → 新 branch 認 DIMENSION → `case DIMENSION -> "dimension/"` 過 `after.startsWith(folder)` → `stem="moon.json"` → 剝 `.json` → **`ad_astra:moon`** ✅ 同 `:168-176` 流程一致（`:176` 只對 TAG 加 `#`） | `logic/WorldgenFacts.java:133-177`（`afterNamespace` 定義）；`sed -n` 親讀 |

- 邊界核（無新矛盾）：`dimension_type/` **唔會**誤中，因 branch 係 `dimension/`（`dimension_type/x.json`.startsWith("dimension/")＝false）。**僅要求實作真係寫 `dimension/` 帶斜線**（v3 §1 a-1 第 1 點寫 `dimension/<name>.json`，OK）。
- 判定：**RESOLVED**（v3 明文的一行修正＋撤回 v2 說法，與 A-a1c 斷言、真 code 逐點對得上）。

---

## ② b-2：全出口表 ＋ hook 順序閘 — **RESOLVED（核心已補）**，附 2 項反方殘餘

### 出口清點（真跑）

```
grep -n "return " logic/AskEngine.java | awk -F: '$1>=201 && $1<=1110'
```
→ 宿主方法 `public AskResult ask(` 起 `logic/AskEngine.java:201`，body 終於 `:1109-1110`；該方法內 **AskResult 出口共 14 條**：
`:282`、`:396`、`:424`、`:875`、`:1012`、`:1016`、`:1019`、`:1029`、`:1038`、`:1066`、`:1075`、`:1083`、`:1096`、`:1107`。

| 出口 | 是否 LLM 答案 | v3 表有無列 |
|---|---|---|
| `:282` offline quest-guide 短路（`:275` 條件 `offline && !questHits.isEmpty()`） | ❌ 無 LLM | **漏列** |
| `:396` honest-miss（`:381-395` 組 missBody） | ❌ 無 LLM | **漏列** |
| `:424` `plain` 高信心純檢索（`:422` 條件） | ❌ 無 LLM | **漏列** |
| `:875` `ReplyLang.isLlmSetupError(llmAnswer)` 直回 | ❌ 設定錯誤字串 | **漏列** |
| `:1012`／`:1016`／`:1019` | ✅ LLM body | 有列＝**3 條覆蓋** |
| `:1029`／`:1038`／`:1066`／`:1075`／`:1083`／`:1096`／`:1107` | ❌ offline／純檢索／honest-miss／friendly-offline | 有列 |

**結構證明「只有 3 條 LLM 出口」成立**：hook 落點（16 空格層、`:1011 if (override) {` 之前）位於 `:923 if (!visibleAnswer.isBlank()) {` 之內，該 block 於 `:1023` 關閉，而 block 內 `:1012/:1016/:1019` **必有一條 return**；`:1029` 之後的出口只在 `visibleAnswer` 為空（＝冇 LLM 答案）時可達。⇒ 落點正確、覆蓋面與設計宣稱一致（`:1310` 早退亦共用同一 `body`）。★

**閘錨唯一性（任務指定要核）**：`grep -n -F` 全檔
| 錨 | 命中 | 唯一？ |
|---|---|---|
| `InfoCompleteness.append(` | 今日 **0** hits（新檔未生）→ 斷言 `==1` 可達 | ✔（可斷言） |
| `if (override) {` | **1**（`:1011`） | ✔ |
| `AskResult.text(body)` | **2**（`:1012`、`:1316`，後者在 `withSideQuests` 內） | ✘ **唔唯一** |
| `ModularFrameStandard.Kind.STANDARD` | **4**（`:363`、`:470`、`:969`、`:1081`） | ✘ **唔唯一** |
| `frameMatch.recipeIndex() != null` | **1**（`:969`） | ✔ 唯一可用錨 |

（`:1081` 位於 hook 之後，用 `lastIndexOf` 會反向誤判；用 `indexOf` 首命中 ＝ `:363`，會令「後於 STANDARD」形同虛設 ⇒ 假綠風險。）

### 反方殘餘（v3 新引入，**唔改設計、唔係 blocker**，但須修文字）

1. **覆蓋表文字 vs 表列自相矛盾**：§2 b-2 `:89` 寫「其餘 **6** 條」、§8 `:145` 寫「全部 **9** 條出口（3 覆蓋／6 不適用）」，但表格實際列 **10** 個行號（`:1012/:1016/:1019/:1029/:1038/:1066/:1075/:1083/:1096/:1107`）＝3＋7；而真 code 該方法實有 **14** 條 AskResult 出口。⇒ 「全部出口表」係 overclaim。**修法（一行）**：改寫成「該方法 AskResult 出口 14 條；3 條 LLM 出口由本 hook 覆蓋，11 條屬 offline／純檢索／設定錯誤路徑，明文列不適用」。
2. **閘錨未寫死 ＋ 位置負控不保證變紅**：§2 b-2 `:90` 只寫「後於 `ModularFrameStandard.Kind.STANDARD` 區塊」，該 token 有 4 hits；而負控「把呼叫移入 STANDARD 區塊內 ⇒ 必紅」只有在錨係**該區塊的收尾 `}`（`:1010`）**時才成立（錨在區塊**開頭** `:969` 時，移入區塊仍在其後 ⇒ 閘維持綠 ⇒ 負控假過）。**修法（一行）**：明文寫死錨＝以 `if (frameKind == ModularFrameStandard.Kind.STANDARD && frameMatch.recipeIndex() != null) {`（`:969` 唯一）起始、brace-balance 至收尾 `}`，再斷言 `hookIndex > 收尾` 且 `< if (override) {`；此即 repo 前例手法（`tests/check_settings_render_order.py:41-44` `src.find(sig_prefix)` ＋ `:44 brace = src.find("{", idx)`）。第 2 個負控（`sed` 改兩次呼叫 ⇒ 靠斷言① count==1 變紅）**成立**。

- 判定：**RESOLVED（核心 R2 投訴已解決）**：R2 指「只列 1 條、實測 ≥5 條繞過」→ v3 已列 10 條並逐條附理由，且 hook 落點的 block 結構確證「只有 3 條 LLM 出口」。殘餘 2 項屬文字／錨規格精確度，不影響 a/b 設計與驗收表可行性。

---

## 比分

**正方 8 : 反方 2**（門檻＝正方 ≥8 且反方 ≤2 → **go = true / pass**）

- 正方得分依據：① 完全 RESOLVED（真 code 逐點對上，A-a1c 自洽）；② R2 死因已解（出口清單由 1 → 10 條、附 3/7 分類＋理由；hook 位置的 block 結構可證）。
- 反方 2 分：v3 新引入的（i）出口覆蓋表計數自相矛盾／「全部」overclaim、（ii）閘錨 2/3 唔唯一致位置負控不保證變紅 —— 兩者皆**須在 plan 文字補寫（各一行）**，但**不構成 blocker**（設計與驗收斷言不變；且 (ii) 在實作時若照 plan 硬性要求跑負控，會被逼出正確錨）。
- **無新 blocker、無前提錯未被修**；不觸發「3–4 輪未達標停手問 SK」規則（本輪已達 8:2）。

### 本輪親跑命令（供覆核）
```
md5sum docs/plans/2026-09-20-worldgen-and-mandatory-facts.md
grep -n "kindFromPath\|idFromPath\|isWorldgenPath\|Kind\.\|dimensionOf\|biomeToDim" logic/WorldgenFacts.java
grep -rn "Kind\." --include=*.java . ; grep -rn "switch (kind)" --include=*.java .
grep -n "return " logic/AskEngine.java | awk -F: '$1>=201 && $1<=1110'
grep -n -F -e 'if (override) {' -e 'AskResult.text(body)' -e 'ModularFrameStandard.Kind.STANDARD' -e 'frameMatch.recipeIndex() != null' -e 'InfoCompleteness' logic/AskEngine.java
sed -n '18,230p;460,520p' logic/WorldgenFacts.java ; sed -n '255,305p;378,432p;820,1130p' logic/AskEngine.java
```
（工作目錄：`forge/1.19.2/src/main/java/com/skps9/packai/`；`tests/`、plan 路徑為 repo root）
