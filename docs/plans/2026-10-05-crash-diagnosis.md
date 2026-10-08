# 2026-10-05 診斷線：整合包「點解唔工作／點解 crash」助手（plan **v2** — R1 反方修正）

- Status：**SHELVED（SK 2026-10-05 決定：唔做診斷線）** — 保留作記錄，唔實作；R1 反方修正已寫入，若日後重開由此 v2 起
- 來源：SK 2026-10-05（Discord）+ R1 反方 review

## 0. v1 前提撤回（最重要嘅修正）

**v1 寫**：「mcmod 343 條評論中 136 條（40%）= 崩潰／唔工作求助，比任何資料問題都多」→ **呢個係我們自己嘅關鍵詞分類器假象，撤回。**

- 分類器 `_mcmod_survey.py` 嘅 `bug_help` regex 含 `怎么|怎麼|求助|问|問`，把大量 **how-to／取得途徑**問題（例：「怎么下载哇？」「滤网基座用到的蜘蛛网前期怎么获得」）吞入「崩潰」桶。
- Hermes 獨立重掃同一批 343 條：收窄到真正 crash 字（崩溃／闪退／报错／无响应／卡退／不工作）→ **17 條（5.0%）**；R1 反方用另一組字得 **28 條（8.2%）**。→ 誠實講法：**crash／壞機類 ≈5–8%，而且數字對關鍵詞表敏感**，遠低於 v1 講嘅 40%。
- 附註：分類桶合計只 204 條，139 條未分類；用 204 做分母會誤得 66.7%。

**後果**：診斷線**唔係需求最大**嘅功能 → 優先序下調，「診斷」唔應該先行。

## 1. 真 corpus 實測（R1 提供，Hermes 已獨立重核）

`instances/AI_test_NFWC_DIM/minecraft/crash-reports/` **52 份真 report**（Hermes 親數）：

| 項目 | 數 | 佔比 |
|---|---|---|
| `Mod loading error has occurred`（**啟動期**崩潰） | 14 | 26.9% |
| `Unexpected error` | 29 | 55.8% |
| `java.lang.StackOverflowError`（最大單一例外） | 25 | 48.1% |
| `NoClassDefFoundError` / `ClassNotFoundException` | 7 / 7 | — |
| `NullPointerException` | 4 | — |
| `OutOfMemoryError` | 1 | — |
| `MixinApplyError` / `InvalidMixinException` | **0** | 0% |
| GPU `hs_err`（`nvoglv64` 等） | 0 | 0% |

**結構性限制（v1 完全冇考慮）**：啟動期崩潰 = FML 直接中止遊戲（`dumpModLoadingCrashReport`），**packai 自己都載入失敗 → 玩家入唔到遊戲，根本問唔到助手**。而啟動崩潰佔我們真 corpus 嘅 26.9%。

**規則表對唔上現實**：v1 八條規則，mixin＝0 命中、缺前置／duplicate 字面＝0、最大單一例外 **StackOverflowError 完全冇規則**。v1 P0 要求湊「缺前置／mixin／OOM／GPU／mod bug 五類 ≥10 樣本」——本地 corpus 根本湊唔到。

## 2. 業界先例（v2 收窄差異化講法）

| 方案 | 已有能力 | 我哋剩低嘅空間 |
|---|---|---|
| **Crash Assistant**（1.19.2 支援、本機為先） | GUI 分析、**Package/Class Finder（class → mod 歸因）**、mixin／缺前置／OOM／整合顯卡分析、部分 auto-fix | **唔可以再當「歸因索引」係我們嘅空間**（v1 錯）。剩：遊戲內對話式（免開 GUI）、整合包感知（前置／版本）、繁中／三語、同 Ask 管線共用 |
| **MCDoctor.ai**（2.1K 下載；已核實唔支援 1.19.2） | 上傳日誌 AI 分析 | 本機為先（唔上傳）、覆蓋舊版 |
| 網頁 log analyzer | 貼文字分析 | 免複製、整合包 context |

## 3. 重訂目標／範圍

**目標（收窄）**：玩家**crash 後重開遊戲**問「點解我頭先 crash」，packai 讀本機日誌（上一 session 嘅 crash-report／latest.log）→ 講：① 最可能原因 ② 牽涉邊個 mod ③ 可行建議 ④ 唔肯定就講唔肯定。

**明確唔做（v2）**：
- **啟動期崩潰**（載入階段 FML 中止）→ 遊戲內攞唔到；若要做，要另一條「遊戲外讀檔」路徑（launcher／獨立小工具），**另立 plan**，本 plan 唔包。
- 唔自動改 mods／config；唔上傳日誌（預設全本機）；唔做 server-side。

## 4. 做法（按真 corpus 重排）

### 4.1 歸因索引（共用基建，非差異化）
掃 `mods/*.jar` → `package → modid → 顯示名`。
**v2 加黑名單**：shaded／relocated／共用 package 會誤歸因（真 corpus 25 條 SOE 來自 oculus **重定位** 嘅 `de.odysseus.ithaka.digraph`）→ 必須建重定位黑名單 + confidence 評分，唔准單靠 package 名指名。

### 4.2 規則引擎（按實測頻率排序，唔係憑想像）
1. **StackOverflowError／深度遞歸**（48.1% —— v1 冇）→ 認 stack 循環 pattern + 歸因 + 建議（例：光影／渲染 mod 組合）
2. `NoClassDefFoundError`／`ClassNotFoundException`（各 7）→ 缺前置／版本錯／jar 損壞
3. `NullPointerException`（4）→ 指涉 mod + 觸發情境（Description 已成欄，例 `mouseClicked event handler`）
4. `OutOfMemoryError`（1）→ 記憶體建議
5. `Mod loading error`（26.9%，**只做「讀取報告並解釋」**，用於玩家重開後問「頭先點解入唔到 game」）
6. duplicate mods／缺前置（字面 0 命中，但屬通用啟動失敗 → 保留但標「本機 corpus 未見」）
7. 已知衝突簽名庫（curated，每條要來源）

### 4.3 LLM 只措辭
引擎出「症狀 → 證據行 → 歸因 → 建議」；LLM 唔准加新 fact；玩家文字規則不變（三語、零檔名／行號）；顯示時遮蔽路徑／用戶名。

### 4.4 入口
`DiagnoseAskTool`（問答觸發）。**唔做自動彈窗**（打機中零打擾）。「貼朋友日誌」文字分析 = 可選（P3）。

## 5. 分階段（v2 重訂）

- **P0 測試集**：用**已標註嘅真 report**（本地 52 份為底；要**人手標 ground truth**，唔准用我們自己嘅分類器當答案）。要求覆蓋到 corpus 真正有嘅類別（SOE／NPE／NoClassDef／啟動失敗），**唔准硬湊 mixin／GPU**。
- **P1 歸因索引 + 規則引擎（離線 harness）**：驗收＝對標註集，正確歸因率 ≥8/10 **並且**要報「唔確定」時唔亂指名；負控＝乾淨日誌要答「冇發現問題」。
- **P2 接入 Ask（真機）**：真機 crash 後重開問 → 答得出；trace 為證。
- **P3（可選）**：貼日誌文字／簽名庫擴充。

## 6. 風險

| 風險 | 對策 |
|---|---|
| 歸因錯（指名錯 mod）比唔答更差 | confidence 閘；重定位黑名單；證據行齊 |
| 啟動崩潰做唔到（26.9%） | 明寫唔包；另 plan 處理 |
| 樣本偏單一（本地 52 份集中幾個包） | 標明；要 SK 提供多包 corpus 先算覆蓋 |
| 私隱 | 全本機、遮蔽、永不上傳 |
| 巨型 debug.log | 只讀尾 N MB + 目標段 |

## 7. 未決（要 SK）

1. 診斷線**仲做唔做**？（v2 評：需求證據 ≈5–8%，唔係最大；但要 SK 判斷）
2. 要唔要「遊戲外讀 crash-report」路徑（唯一可以覆蓋 26.9% 啟動崩潰嘅方法）？
3. 要唔要多包 crash corpus 先開 P0？

## 8. R1 review 記錄（2026-10-05）

- **比分 正方 3 : 反方 7（唔過）**。
- 已修：① **撤回 40% 前提**（改 ≈5–8%，並說明分類器缺陷）② 加真 corpus 實測表 ③ 承認啟動崩潰結構性做唔到，範圍收窄為「crash 後重開」 ④ 規則表按真實頻率重排（SOE 第一）⑤ 撤回「歸因索引」差異化講法（Crash Assistant 已有）⑥ 加重定位誤歸因對策 ⑦ P0 唔准用自家分類器當 ground truth。
- 下一步：等 SK 決定做唔做 → 若做，R2 反方。
