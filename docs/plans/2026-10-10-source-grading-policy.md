# Plan：落點 C — 回答來源分級政策（2026-10-10，SK 授權自主完成）

- 前身：`docs/plans/2026-10-10-capability-search-fit.md` §6「SK 決定：三個都做，次序 ① A → ② C → ③ B」。A 已完成（`3bb9b89`／`0deb4f2`／`a873bcf`）。**本 plan ＝ 落點 C**。
- 目標（一句）：**每個玩家可見答案嘅【來源】行改成「分級」** —— A＝包內檔案／runtime 實測、B＝任務書／指南書、C＝通用知識／網搜；並且**缺證據時明示「未確認」，唔准斷言「呢個包冇」**（SK 文字鐵則）。

## 1. 現況（Hermes 親核）

| 事實 | 證據 |
|---|---|
| 已經有【來源】行機制 | `logic/ReplySources.java`：`HEADER` 認 `【來源】/【来源】/[Sources]`；`build(...)` 由 9 個 boolean 砌一條 source 清單（JEI／EMI／purpose／guide／questBook／localScripts／acquireTables／jarMods／webSearch），全部係 label，**冇分級** |
| 已有 canonical 重寫 | `ReplySources.ensure(...)`（同一檔）會對髒【來源】改寫 |
| label 已經 i18n | `ReplyLang.labelPurpose/labelGuide/labelQuestBook/labelLocalRecipes/labelAcquire/labelJarIndex/labelWeb` |
| prompt 已經有「通用知識」概念 | `packai.reply.llm_style` 內已有一句「JEI／本地無本包覆寫 → 可用原版／該模組通用知識，並標明『通用知識（非本包覆寫）』」⇒ **C 只係把呢個概念變成結構化分級，唔係新概念** |
| 樹狀態 | `neoforge/1.21.1` **PAUSED**（`neoforge/README_PAUSED.md`）⇒ **只改 forge**；雙樹非 allowlist drift 喺 paused 下只 WARN（`check_dual_tree_sync.py` 實測 RC=0、warn=106） |

## 2. 設計（最小改動）

### 2.1 Grade 定義（寫死，唔准自創）

| Grade | 意思 | 對應現有 source |
|---|---|---|
| **A** | 包內檔案／runtime 實測 | JEI、EMI、本地配方（KubeJS／datapack）、掉落／釣魚／交易表、jar 索引（bytecode 事實） |
| **B** | 包作者寫嘅書面說明 | 任務書（FTB Quests）、指南書（Patchouli／guide） |
| **C** | 唔屬於本包嘅資料 | 網搜結果、模型通用知識 |

（purpose 標籤屬 A —— 佢係由 lang／tooltip／本地事實抽出嚟。）

### 2.2 輸出格式（玩家可見）

- 有分級來源：`【來源】A：JEI、本地配方　│　C：網搜`
- 只有一級：`【來源】A：JEI`
- **完全冇來源**（誠實 miss 路徑）：保留現行 miss 文案 ＋ **仍出一行** `【來源】A：（本包索引查無）` 之類 —— 具體字句用 lang key，**唔准由 code 拼英文**。
- 格式用既有 `│` 分隔慣例（同 `ReplySources` 現有 join 一致；實作前要核實）。

### 2.3 落地位

1. `logic/ReplySources.java`：加 `enum Grade {A,B,C}`（或等價）＋ `buildGraded(...)`；**保留舊 `build(...)` 簽名不變**（避免波及 call site），新舊並存，call site 逐個 migrate。
2. `ReplyLang`：加 legend／grade 前綴 lang key。
3. lang JSON：**只改 `forge/1.19.2/src/main/resources/assets/packai/lang/{en_us,zh_cn,zh_tw}.json`**（三語同步；neoforge 唔改）。
4. prompt：`llm_style`（或 fact_check）加一句講 A/B/C 定義 ＋「正文只靠 C 級嘅事實要標明通用知識」。
5. **唔准郁**：卡落位（`RecipeEmbed`／`RecipeCard`）、`AskEngine` capable 清空語意、`AskToolLoop`、miss 文案本身。

## 3. 驗收標準（先寫死）

- **V1** python check（新增 `tests/check_source_grading.py`）：每個 source label → 正確 grade；空 source → 唔出空 grade；順序固定 A→B→C。
- **V2** 全部 `tests/check_*.py` **冇新增紅**（今日 baseline＝127 檔／126 pass／1 已知紅 `check_ask_display_leak`）。
- **V3** `compileJava compileTestJava` RC=0；現有 harness 全綠（`runAskMarkerIntegrityCheck` 等）。
- **V4** lang JSON 3 檔逐個 `json.load` OK（cursor 改過大 value 會靜默整爛）。
- **V5** 真機（沙盒）跑 1–2 條真 ask → **貼 `latest.log` 同答案原文**，肉眼核【來源】行真係分級、冇 raw id／冇 prompt 殘留。
- **V6** `git status` 只准出現預期檔；`neoforge/` 零改動。

## 4. 風險／還原

- 風險：低——純顯示格式 ＋ prompt 文字。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\` 還原。
- 唔准：hot-copy jar、真 instance 自動部署、`git add -A`。

## 5. 執行

- 實作＝**cursor-agent**（packai 鐵則）；Hermes 只做 plan／派工／親驗。
