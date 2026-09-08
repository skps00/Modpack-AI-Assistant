# Friend Wishlist 現況 + Tools 架構決策（2026-09-08）

Status: **決策記錄（PLAN INPUT，未改 production code）**
Generated: 2026-09-08.
Related: [skill-system-dropin.md](skill-system-dropin.md)（drop-in skill 方法包，v1 已 commit `fd7d314`）；[ask-native-tools.md](ask-native-tools.md)（tool 層真 function-calling）；Scope Y（`AskTool` plugin API 已上 0.2.0，stored-only）。

Source: Discord session 2026-09-08 06:49–07:2x（「查看武刃武器详细属性」thread：friend 提出 8 項 wishlist → 逐項對照現況 + skill/tool 分層分析 → tools 架構拆法 A/B/C/D 決策）。

---

## 0. 背景一句

SK friend 提咗 8 項 packai 想有嘅功能。分析發現：**大部份卡喺 data/player-state 層，唔係 skill 或 prompt 教得到**；由此引伸「我哋本身嘅 tools 要唔要拆開」嘅架構問題。

## 1. Friend wishlist 三層分類（逐項實錘對照）

### 已實現 ✅（唔使做——friend 可能未用過/唔知）

| # | 功能 | 證據 |
|---|---|---|
| 3 | 冇 item 用 JEI 查 | `ItemSearchAskTool` + `resolveStable`（問「XX 係咩」唔使 hover 都有） |
| 7 | 季節作物提示 | `SeasonContext.java` 已存在（偵測季節 mod + crop 問題先出提示） |
| 8 部分 | 回答內 item hover | Chat 有 item marker／`[[item:]]`；`AiAssistantScreen.renderTooltip` 已有基礎（正文提到嘅 item 係咪 hover 到要再確認） |

### 要 player-state tool 🟡（data 層缺失——skill 點教都冇用）

| # | 功能 | 缺咩 |
|---|---|---|
| 2 | 期望 DPS（連玩家數值） | Raw weapon attribute 已有（`AskPurposeContext` MAINHAND AttributeModifiers），但**玩家本身狀態冇管道**：飾品/裝備加成、藥水 buff、特殊包機制（chestcavity 脆骨症器官）、Apotheosis 爆擊率——全部未入 model 眼 |
| 5 | 高速機台傾向 | mirror coalesce 已將機台合併「亦可用」——**model 睇唔到邊部快**；機台速度 data 未入 tooltip |

### 要 mod 整合 / UI code ❌（skill 唔 cover）

| # | 功能 | 點解唔係 skill |
|---|---|---|
| 1 | Psi 術式生成 | 術式係結構化程式——要讀 Psi CAD/data，純文字教會亂作 |
| 4 | 合成路徑鏈（深鏈） | 淺鏈 skill 可教（逐層 chain jei_lookup），但撞 `MAX_LLM_ROUNDS=3` hop budget；深鏈要 progression tool |
| 6 | Regenerate 按鈕 | UI code |

### Skill 唯一純 candidate（分析後降級）

- #2 原本似 skill（計法），但 SK 糾正：**DPS 要連玩家飾品/器官先係 friend 想要嘅「期望 DPS」** → 降級做 player-state data 問題。

## 2. Tools 架構拆法決策（A/B/C/D 分析 + Verdict）

現況：`AskEngine.java` L30-46 **硬註冊 14 個內建 tools**（jei_lookup/acquire/worldgen_lookup/enchant_lookup/repair_lookup/item_search/purpose_lookup/render_recipe_cards…），全部寫死同一個 jar、玩家冇得揀。`AskToolLoop` 有 ALLOWLIST gate + focus-item gate + `MAX_LLM_ROUNDS=3`。

| 拆法 | 判斷 | 原因 |
|---|---|---|
| **A. 分 jars（可選 mod 模組）** | ✗ | dual-tree（Forge+NeoForge）× 每 jar = 版本矩陣爆炸；玩家安裝複雜。MC 社群慣例 = 一個 mod 內做 extensible API（JEI/EMI plugin），唔係拆 mod |
| **B. config 開關** | 唔算拆法 | 只係「閂現有」，friend 想要嘅係「加新」——B 俾唔到；可做 registry 嘅 side option |
| **C. data/logic 分離** | ✗ | Friend wishlist 大部份係 **runtime state**（玩家飾品/器官/機台速度）同 **logic**（Psi）——唔係 data 餵得到；為一個 friend 做 data-driven framework = over-engineering |
| **D. 開放 tools API** | ⚠️ 方向啱、時機錯 | D 受眾 = 第三方 mod dev；friend 係玩家唔寫 tool。packai 連自己 player_state 都未有，開放咗冇人用 |

### Verdict（8:2）

**維持現狀：唔拆 jar、唔開放。** 要做嘅只係 **D 嘅第一步——內部 registry 化**：14 隻 hard-code tools 移入同一個 registry（2026-09-05 Public AskTool plan 已做 `registerExternal` 雛形），令 packai **自己**加 player_state 等新 built-in tool 時唔使再改 `AskEngine` 個 hard-code list。Registry 化係 friend wishlist 嘅 prerequisite，唔係獨立 project。

**最大未知**：friend 係咪會長期用 packai、值唔值得為佢開 roadmap。
**反轉條件**：幾日內出現**第二個**真係想自己寫 tool 嘅人 → 先將 registry 升做 public API（即 full D）。

## 3. Next（建議優先序）

1. **`player_state` tool**（packai 自己寫 built-in）：讀玩家背包/裝備 slots/Curios/器官（chestcavity）/藥水效果 → 先 unlock #2 期望 DPS（連 player buffs）同 #5 機台傾向（連 machine speed data）嘅 data 層
2. **Registry 化**（D 第一步）：14 隻 hard-code tools → registry（配合 Scope Y `registerExternal` stored-only 現狀）
3. #4 淺鏈 skill（逐層 chain jei_lookup）可先行驗證——唔使等 player_state

## 4. 教訓（寫入 skill system 設計）

- **「教 AI 追」之前，要確認啲 data 真係入到 model 面前**——skill 係「最後一里」：data 齊，skill 教點用；data 唔齊，skill 點教都冇用（同 adversarial-decision-review 2026-09-08 實測 P1 一致：武刃 root cause = data/capture 層，唔係 model 冇人教）
- Friend wishlist 浮現 packai 由「百科」→「助手」嘅關鍵：**AI 要識玩家而家嘅狀態先答到** → player-state 讀取管道係 roadmap 主線
