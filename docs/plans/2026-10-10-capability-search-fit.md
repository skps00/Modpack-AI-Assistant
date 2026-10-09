# 「能力反查」SOP 融入位分析（2026-10-10）

- 對象：`mayacraft.net/hermes/mc-item-research-sop.html`（物品／能力調查 SOP）＋ skill `minecraft-mod-bytecode-forensics` §11
- 目的：答「**包入面邊樣嘢可以做到 X**」（無 item id 入手）——我哋引擎目前 **J 類缺口**（覆蓋缺口審計 §3）
- 狀態：分析（未改 product code）

## 1. SOP 七步 ↔ 我哋現有 layer

| SOP 步驟 | 我哋做到未？ | 落點（實際 code／檔案） |
|---|---|---|
| ① 問題拆成可驗證行為 | ❌ 冇 | `AskEngine` 前饋 facts／prompt 契約（顧問路徑） |
| ② 候選掃描（mod 清單） | ❌ 冇（`PackIndex` 冇 mod→能力） | **`PackIndex.build(gameDir, scanners)` 加一個 scanner** |
| ③ 行為取證（javap bytecode 常數／filter） | ❌ **runtime 做唔到**（太重） | **只可以離線**：`tools/` 新成員出 artifact |
| ④ lang 原文（tooltip／描述） | ✅ 已 index | `PackIndex.translations`（:151）＋`descByItem`（:153） |
| ⑤ 三方印證（數字＋邏輯＋文案） | ❌ 冇規則 | 回答政策層（`HonestMiss`＋來源分級） |
| ⑥ 配方追溯（逐層拆料到原材料） | ✅ 有 | Recipe graph（P0 已量：固定比例鏈 76–97%） |
| ⑦ 交叉驗證（任務書／第二來源） | ✅ 部分 | `quest_fetch`／FTB quests index（81 檔） |

**結論：SOP 唔係「新功能」，係把 ① ② ③ ⑤ 補上，令 ④ ⑥ ⑦ 由「答單件物品」升級成「答需求」。**

## 2. 三個落點（由細到大）

### 落點 A — 離線能力索引（**建議先做**）
- 新 `tools/mine_capability_index.py`：對 pack 內 jar 做 SOP ②③（候選掃描 → javap 抽常數／filter 判斷）＋④⑦ 交叉
- 出 artifact：`docs/research/artifacts/<date>-capability-index-<pack>.json`（每條＝能力→候選物品→證據等級）
- 為何先做：**零 product 風險**、可即刻量度「runtime 覆蓋率有幾多」（我哋唔知 lang／tags／quest 夠唔夠答，因為 bytecode 那層 runtime 冇）

### 落點 B — mod 內新 tool `capability_search`
- `logic/CapabilitySearchAskTool.java`（`api.AskTool`）：args＝`need`（需求字串，**唔需要 item id**）＋`limit`
- 查 runtime 可及來源：`translations`／`descByItem`／item tags／JEI recipe type／quest 文本
- **同一套落地位**（同 keybind v5 一模一樣，可一次過做）：
  `AskEngine` 註冊 → `AskToolLoop.CAPABLE_TOOLS`（:39-42）＋`QUERY_TOOLS`（:46-48）→ `tests/check_tool_schema_stable.py` 例外集合
- ⚠️ 教訓（keybind R1/R2 實錘）：呢三處漏一處＝**靜默失效**，所以必須同一 commit

### 落點 C — 回答政策（來源分級 + 未確認）
- 每條結論標來源：**A** 包內檔案／bytecode、**B** 任務書、**C** wiki／模型知識
- 缺證據 → 標「未確認」，**唔准斷言「呢個包冇」**（SK 文字鐵則）
- 位置：`HonestMiss`＋前饋 facts 契約（同 machine／keybind 段同層）

## 3. 誠實限制（要寫落 plan）

- **bytecode 取證唔可能在 runtime 做**（要在遊戲內掃 249 個 jar 嘅 class 常數）⇒ mod 內覆蓋率**只靠** lang／tags／JEI／quest 文字；SOP 最強嘅一步只能離線用嚟驗證同補資料
- 因此落點 A 嘅 artifact 亦係「runtime 夠唔夠用」嘅**唯一量度方法**（未做之前唔准講覆蓋率）
- 我哋 mod 係**通用（all packs）**⇒ 唔可以硬編碼任何包嘅能力清單；capability index 只能「由玩家自己 instance 現場生成」或「離線研究用」

## 4. 建議次序

1. **落點 A（離線索引＋覆蓋率量度）** ← 零風險、可即刻做、決定 B 值唔值做
2. 落點 B（`capability_search` tool）——若 A 顯示 runtime 來源覆蓋 ≥ 某門檻（做前定，例：≥60% 條目可答）
3. 落點 C（來源分級政策）——可與 B 同一 slice

**協同**：B 同 keybind v5 要改嘅係同樣三個位（白名單／註冊／guard test）⇒ 可合併一個 slice 一次過驗收（省一次部署＋一次實機 smoke）。

## 5. 開工前驗收標準（落點 A）

- A1 至少 5 條真問題（含 SK 原句：「儲不可堆疊物品」「清 100×90 範圍」「白色混凝土工廠」）→ 每條有候選清單＋證據行號
- A2 對 DJ2 JSU 案**重現 SOP 結論**（270／256／只收非堆疊），且用**已核實嘅正確路徑**
- A3 每條結論標來源等級（A/B/C）；缺證據標「未確認」
- A4 artifact 可重跑（固定輸入 → 同輸出）
- A5 誠實列「runtime 做唔到嘅部分」＝bytecode 層
