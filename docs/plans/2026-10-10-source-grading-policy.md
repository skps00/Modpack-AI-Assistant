# Plan v2：落點 C — 回答來源分級政策（2026-10-10，SK 授權自主完成）

- 前身：`docs/plans/2026-10-10-capability-search-fit.md` §6（SK 決定 A→C→B；A 已完成）。
- **R1 反方 review：正方 3.5 : 反方 6.5 ⇒ 唔可以開工，本 v2 修設計。**

## 〇、R1 逐條裁決（全部採納）

| # | 反方指控 | 裁決 |
|---|---|---|
| A3（high）| 我打算用 U+2502 `│` 分組，但 `ReplySources.ensure → AskReplyScrub.renderSourcesFooter` 清洗時會 fallback 用 `sourceJoin`（`、`）重砌，**分組會被砸平**；而且 `│` 唔係 codebase 慣例（codebase 用 U+FF5C `｜`），我寫嘅理由（「同現有 join 一致」）係**事實錯誤** | **採納**：**唔引入任何新分隔符**。改成「每個 item 自帶 grade 前綴」（`A：JEI`），flat join `、` 照舊 ⇒ 清洗器重砌都保留分級 |
| A4（med-high）| 生產碼只有 **1 個** `build()` call site（`AskEngine:722`）＋ 2 個 `ensure()`（`:966`／`:1315`）；另有 **3 個硬砌【來源】行**（`:1082-84` `labelAcquireOffline`／`:1092-94`、`:1112-13` `labelNone`）唔經 build ⇒ 改完會有兩套格式並存 | **採納**：v2 明列全部 6 個落點（1 build + 2 ensure + **3 硬砌**）都要 grade-aware；唔再講「逐個 migrate」 |
| A2（med）| 分類唔完整：漏咗 `labelAiOnly`（空 build）、`labelAiModel`（空 format）、`labelNone`、`labelAcquireOffline`、`JEI_VARIANT_SOFT`；EMI 係 detect/stub 唔應無條件當 A | **採納**：v2 §1 表**逐個**定級（見下）。EMI 只會在真用到時才出 label；仍標 A，但明寫「stub 期間唔會出」 |
| A5（med-high）| **價值質疑**：分級由 code 嘅「今次執到咩 source」boolean 決定，**唔係 per-claim 證據** ⇒ 可能出現「標 A：JEI 但句子其實係模型記憶」。而「缺證據唔准斷言」係 `HonestMiss` 負責，本 plan 唔郁 | **採納並改定位**：明寫呢個 footer 係「**來源存量 legend**」，唔宣稱 per-claim。**新增一個 deterministic 規則**：若本次**一個 A／B 級 source 都冇執到**，footer 必須明示「本包索引查無，以下為通用知識」（code 強制，唔靠模型）⇒ 呢個才係真正嘅信任信號 |
| A6（low-med）| Prompt 加 A/B/C 定義係冗餘（`fact_check` 規則 5 已有「通用知識（非本包覆寫）」）＋只會加 token 同 cache churn | **採納**：**唔改 system prompt**。A/B/C 定義只放玩家可見 lang key。 |
| A1（low）| `jarMods` 我寫「bytecode 事實」係失實（實際係掃 jar 內 datapack JSON，明文 no decompile） | **採納**：改正 |
| 數字核實 | 「通用知識」句在 `packai.reply.fact_check`（**唔係** `llm_style`）；`│` 非現有慣例；baseline 126 pass／1 紅正確 | **採納**（已改正上文） |

## 1. 目標（一句）

**每個玩家可見答案嘅【來源】行，每一個來源都帶「證據級」前綴（A＝包內檔案／runtime 實測、B＝任務書／指南書、C＝通用知識／網搜），並且當本次一個 A／B 級來源都執唔到時，由 code 強制明示「本包索引查無，以下為通用知識」。**

## 2. 現況（Hermes 親核；數字已過獨立核實）

- `logic/ReplySources.java`：`HEADER` 認 `【來源】/【来源】/[Sources]`；`build(...)` 有 **4 個 overload**，最終版 **10 參數**（9 boolean + `replyLang`），輸出 `LinkedHashSet` label；join 用 `ReplyLang.sourceJoin`＝`、`（en_us `, `）。
- `labelXxx` 七個 method ＋ lang key 全部存在（`packai.reply.label.*`）。
- 「通用知識（非本包覆寫）」在 **`packai.reply.fact_check`**（3 語皆有）。
- 生產碼落點：`AskEngine:722`（唯一 `build`）＋ `:966`／`:1315`（`ensure`）＋ **`:1082-84`／`:1092-94`／`:1112-13`（硬砌 `labelAcquireOffline`／`labelNone`）**。
- 其他標籤：`labelAiOnly`（空 build）、`labelAiModel`（空 format）、`JEI_VARIANT_SOFT`（`JEI (NBT variants may mix)`）。
- 清洗鏈：`ReplySources.ensure → AskReplyScrub.renderSourcesFooter`（會用 `sourceJoin` 重砌）⇒ **格式必須對重砌免疫**。
- 今日 gate baseline：`tests/check_*.py` **127 檔／126 pass／1 已知紅**（`check_ask_display_leak`）；`check_dual_tree_sync.py` 無參數 RC=0、warn=106。
- 樹：只改 `forge/1.19.2`；`neoforge/1.21.1` PAUSED 唔改。

## 3. 設計

### 3.1 級別定義（寫死）

| Grade | 意思 | 標籤 |
|---|---|---|
| **A** | 包內檔案／runtime 實測 | JEI、EMI（真有資料時）、purpose、本地配方（KubeJS／datapack）、掉落／釣魚／交易表、jar 內 datapack JSON（**非** bytecode）、離線索引（`labelAcquireOffline`） |
| **B** | 包作者寫嘅書面說明 | 任務書（`labelQuestBook`）、指南書（`labelGuide`） |
| **C** | 唔屬本包 | 網搜（`labelWeb`）、模型通用知識（`labelAiModel`／`labelAiOnly`） |
| **—** | 查無 | `labelNone`（**唔加 grade 前綴**，另加強制提示，見 3.3） |
| **A（降級）** | NBT 變體可能混 | `JEI_VARIANT_SOFT` 保留原文，前置 `A：` |

### 3.2 玩家可見格式（對清洗免疫）

- 每個 item ＝ `grade 前綴 + 現有 label`，例：`A：JEI`、`A：本地配方`、`B：任務書`、`C：網搜`
- 整行仍然係 `【來源】` + `、` join（en_us `, `）—— **唔引入新分隔符**，所以 `renderSourcesFooter` 重砌後仍然保留分級。
- 排序：**A → B → C → —**（穩定；現行次序非 A→B→C，屬**已知行為改動**）。
- grade 前綴 i18n：新 lang key（3 語），**唔准 code 拼英文字面**。

### 3.3 新增 deterministic 規則（本 plan 真正價值）

- `ReplySources` 增加 API 回報「本 ask 有冇 A／B 級來源」（由同一批 boolean 算）。
- 若**冇** A／B（只有 C 或完全冇）：footer 必須包含一條由 lang key 出嘅提示句，例：
  - zh_tw：`本包索引查無相關資料，以下屬通用知識`
  - 由 **code 強制**插入（唔靠模型自願）。
- `labelNone` 路徑沿用現行 miss 文案 ＋ 同一條提示句。

### 3.4 落地位（全部 6 個）

1. `logic/ReplySources.java`：加 `enum Grade`（或等價 map）＋ `buildGraded(...)`；**舊 `build(...)` 4 個 overload 保留**（但 3 個無 caller 嘅 overload 標 deprecated 註解）。
2. `logic/AskEngine.java`：`:722` 改叫 grade-aware；`:966`／`:1315` `ensure` 保留；**`:1082-84`／`:1092-94`／`:1112-13` 三個硬砌改為經同一個 grade-aware formatter 出**（消除兩套格式）。
3. `ReplyLang`：加 grade 前綴／提示句 lang key。
4. lang JSON：`forge/1.19.2/src/main/resources/assets/packai/lang/{en_us,zh_cn,zh_tw}.json`（3 語同步；neoforge 唔改）。
5. **唔准郁**：卡落位（`RecipeEmbed`／`RecipeCard`）、`AskReplyScrub`（我哋改成對佢免疫，唔改佢）、`AskToolLoop`、`HonestMiss` 文案、system prompt。

## 4. 驗收標準（先寫死）

- **V1** 新增 `tests/check_source_grading.py`：① 每個 source label → 正確 grade；② 每個 grade 前綴有 3 語 lang key；③ 空集／`labelNone` 唔出空 grade；④ 排序 A→B→C→—。
- **V2** **清洗往返斷言**（回應 A3/A7）：`【來源】…` 經 `AskReplyScrub` 對應嘅 python mirror（`check_reply_structure_scrub.py` 同一套）處理**之後**，grade 前綴仍然齊全、數目唔變。
- **V3** **落點覆蓋**（回應 A4）：grep 證明 `AskEngine` 三個硬砌 emitter 已經唔再自己拼 `【來源】`；全 repo 只由一處出 footer。
- **V4** 全部 `tests/check_*.py` 冇新增紅（baseline 127／126／1）。
- **V5** `compileJava compileTestJava` RC=0；現有 harness 全綠。
- **V6** lang JSON 3 檔逐個 `json.load` OK。
- **V7** **真機（沙盒）覆蓋矩陣**（回應 A7）：至少跑到 ① 有 A 級、② 只有 B 級、③ **只有 C 級（無 A/B）**、④ 完全查無 四種情境，逐個貼 `latest.log`＋答案原文。
- **V8** `git status` 只准預期檔；`neoforge/` 零改動。

## 5. 風險／還原

- 風險：低—中（純顯示格式 ＋ 排序改動 ＋ 3 個 miss 路徑改 emitter）。**冇**改 prompt、冇改卡、冇改清洗器。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\` 還原。
- 唔准：hot-copy jar、真 instance 自動部署、`git add -A`。

## 6. 執行

- 實作＝**cursor-agent**；Hermes 只做 plan／派工／親驗。

---

## 7. 【停手／re-scope】R1 3.5:6.5、R2 4:6（2026-10-10 06:3x）—— 本工程師決定：**暫緩「footer A/B/C 分級」**

### 逐輪比分

| 輪 | 比分 | 打中嘅位 |
|---|---|---|
| R1 | 正方 **3.5** : 反方 **6.5** | 格式被清洗器砸平（`renderSourcesFooter` 用 `、` 重砌）；`│` 理由事實錯；落點漏 3 個硬砌；分類漏 5 個 fallback label；價值質疑；prompt 冗餘 |
| R2 | 正方 **4** : 反方 **6** | **grade 喺主路徑 inert**（model 自己寫 footer，`ensure` 見乾淨 footer 就原封不動 return）；grade 前綴對 `splitTopLevel` 唔免疫（`labelAcquire` 內含 `／`）；實際 footer emitter **≥9 個、跨 3 檔**（`AskEngine` ×4 含 `:1072` 硬編英文 `"JEI"`、`Plainify:403`、`QuestGuide:868`），`AskEngine:1324` 仲會 append 第二個 footer；9 個 boolean 喺 `if (!offline)` 內、硬砌 emitter 喺外面；`purposeUsed` 混入 model 文字卻定 A；`softenJeiForVariant` 靠 `"JEI".equals(s)` 會靜默失效 |

### 決定（第一性原理）

1. **value 問題係真嘅**：現有【來源】label 本身已經自解釋（任務書／本地配方／JEI vs 網搜）——加 A/B/C 前綴嘅**邊際價值低**，但**脆弱度高**（要同時改 ≥9 個 emitter、繞過清洗器、補 5 個 fallback label、避開 soften 靜默失效）。
2. 「缺證據唔准斷言」呢個**真正有價值**嘅一半，一直由 `HonestMiss` 承擔，唔屬本 plan。
3. ⇒ **暫緩** footer A/B/C 分級。**唔刪**：`落點 C` 嘅證據誠實 kernel（「全靠通用知識時由 code 加一句」）**搬去落點 B 之後**再評，前提係要有一條**單一最後 pass**（跑喺所有清洗之後）＋真機觸發率數據支持價值。
4. 工作次序調整為 **A（完成）→ B → C′**（C′＝證據誠實 kernel，唔做 footer 前綴）。理由：B 係**新能力**（玩家真係答唔到嘅問題），C 係**呈現改動**；資源有限時先做能力。

**已沉沒成本**：2 輪 review（4 個 subagent）。**未改任何 product code。**
