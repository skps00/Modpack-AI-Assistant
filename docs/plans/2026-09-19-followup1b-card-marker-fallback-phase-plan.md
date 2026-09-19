# Follow-up #1b phase plan（**v6**）— 卡**堆埋一齊／去咗最尾**（fallback 落位）

> **2026-09-19 SK 定義症狀（決定性）**：「**卡堆埋一齊, 卡去咗最尾 both**」 ⇒ 對應 code：冇 `[card:N]` 嘅卡由 `RecipeEmbed` fallback（`disperseUnplacedEmissionCards` `:919-925` → `sectionLastAfter` `:1010-1042`）塞到段尾／答案尾；多張就堆埋。**⇒ 症狀變成可量度**（量「fallback 落位」次數，唔再靠「有冇 marker」呢個分辨不到嘅 proxy）。
> v5 R1 **4:6**（`docs/plans/reviews/2026-09-19_followup1b-plan-R1-opposing.md`）：v5 用「文本層抽／貼」有 O2 風險（貼錯段）＋驗收判準恆真。**v6 改用 SK 定義嘅可量度判準 ＋ 用現成 `emissionSectionOf` 揀正確段**。
> 版本：**只適用 MC 1.19.2 + Forge 43.x**

## 1. 量度儀器（第 1 步，**零行為改動**）
喺 `logic/RecipeEmbed.java` 落位邏輯加 **1–2 行 log**（現時該檔零 LOGGER）：
```
每一張卡： packai card-place: n=<序> item=<id> mode=<marker|fallback> section=<obtain|use|none> idx=<位置>
每次 ask： packai card-place: cards=<n> byMarker=<n> byFallback=<n>
```
**判準**：`byFallback > 0` ⟺ 出現咗 SK 講嘅「堆埋／去最尾」。修前 baseline 預期 >0；修後目標 **= 0**。

## 2. 修法（第 2 步；用現成 section 判定，避免 v5 嘅貼錯段）
- 每張發出的卡本身已知佢屬邊個 section（`RecipeEmbed.emissionSectionOf` `:1121-1136`，回 wantSec 0=obtain／1=use）
- **若某卡冇 marker**：喺**佢自己嗰個 section** 嘅最後一行尾補 ` [card:N]`（N＝該卡喺最終 shown 清單嘅 1-based 位置），**唔係**「最後一行 before 【来源】」（v5 嘅錯）
- 只補**未 placed** 嘅卡；已有 marker 一律唔碰；`cardStrip` 關 ⇒ 唔補
- 落點：`AskService` 喺 `withRecipeCards(...)` **之前**（`:403-404`／`:2483-2484`；`:406` 之後＝零 UI 效果）
- 若 review 判定「UI 層直接按 section 落位」更簡單（唔改文字），可改用該方案（二選一由 review 定）

## 3. 檔案白名單
- `logic/RecipeEmbed.java`（儀器 log）
- `client/service/AskService.java`（補 marker；兩條 ask 路徑共用 helper）
- **新增** `tests/check_card_placement_fallback.py`（fixture：冇 marker＋section 已知 ⇒ 補喺正確段；cards=0 ⇒ 唔補；cardStrip 關 ⇒ 唔補）
- **唔准改**：`logic/AskReplyScrub.java`、`logic/AskEngine.java`、`logic/AskTrace.java`、`neoforge/**`、lang 檔

## 4. 驗收標準（可量度，直接對應 SK 描述）
| # | 檢查 | 判準 |
|---|---|---|
| A1 | **修前 baseline**：同一物品 3 次＋多卡物品（石斧）3 次 | 記錄 `byFallback` 次數（預期 >0；證明症狀可量度） |
| A2 | **修後**：同樣 6 次 | `byFallback` **= 0**；`byMarker` = `cards` |
| A3 | 多卡唔准堆埋 | 每張卡 idx 互不相同且各自落喺對應 section |
| A4 | 負控 fixture | 冇卡 ⇒ 唔准補；已有 marker ⇒ 唔准改 |
| A5 | 122 閘＋新閘／49 Java 測試 | ≥121 綠、0 新紅、49 綠 |
| A6 | SK 目視 | 卡喺對應步驟下面（唔堆埋、唔去最尾） |

## 5. 流程
1. 本 v6 → 反方 R1（新機制第一輪）
2. **先只做 §1 儀器**（零行為改動）→ 你唔打機時跑 baseline（3＋3 次）
3. baseline 出數（`byFallback` > 0）→ 做 §2 修法 → 再跑同樣 case → `byFallback = 0`
4. code review 兩輪 → 入部署清單

---

# 附錄：v5 內容（歷史）



> 前情：`2026-09-19-followup1-frame-card-marker-phase-plan.md`（v1–v4）4 輪 review 3:7→4:6→5:5→6:4 未達 8:2 → 停手；**2026-09-19 SK 補充關鍵事實**：
> ① 「冇 marker 真係令卡走位，係我用你之前已經有嘅問題」② 「**same item can first time is bug, second time normal**」⇒ **隨機、取決於 LLM 每次寫法**。
> ⇒ 之前所有「針對 sword 個案」嘅修法（v4 span 抽取）都係**形狀相依**，唔可靠。本 v5 改為**形狀無關**。
> 版本：**只適用 MC 1.19.2 + Forge 43.x**；`neoforge` PAUSED 唔准郁。

## 1. 症狀（隨機）
同一物品、同一問法，**有時**出現「有卡但內文冇 `[card:N]`」⇒ 卡唔喺對應步驟下面（UI fallback 會把未引用嘅卡塞到段尾，或堆埋）。
已知會令 marker 消失／抽唔到嘅形狀（唔限於此）：
- 標準框架分支 `how-to-get **replaced**`（`AskReplyScrub.replaceHowToGetBody`）→ 連 marker 一齊換走
- LLM 將「怎么来」同配方寫**同一行**（`怎么来:合成台…版本。 [card:1]`）→ 舊 v4 嘅 span 抽唔到
- LLM 索性**唔寫** marker（prompt 明講「唔寫 `[card:N]` 都可以」）
⇒ 任何「跟文本形狀」嘅修法都會有盲區。

## 2. 設計（v5）：喺「已知有幾張卡」嗰層做**最後防線**
**位置**：`client/service/AskService.java`，**卡已算好之後**、寫 trace 之前；兩條 ask 路徑（`runAsk` AI 分支 `:399-409`；`askBlocking` AI 分支 `:2478-2487`）**共用一個新 helper**（唔准只改一條）。

**規則（形狀無關，只有一個條件）**：
```
if (cardStrip 開啟)                                  // [card:N] 只喺 AI strip 模式有意義
   && cardsOut.size() >= 1                            // 真係有卡要引用
   && body 內完全冇任何 [card:N] token               // ← 唯一觸發條件：一個都冇
{
    X = cardsOut 內「焦點物 output 卡」嘅 1-based 位置（identity 比對 item id；比唔中就 1）
    喺 body 嘅 how-to-get 行尾（冇該標題 ⇒ 最後一行 before 【来源】）貼 " [card:X]"
}
否則：一個字都唔改（**唔搬、唔補、唔重編號**）
```

**為何形狀無關**：觸發條件係「**一個 marker 都冇**」，唔理 LLM 點寫、唔理有冇 replaced、唔理同行定分行 ⇒ 覆蓋全部形狀；而「已有 marker」嘅情況一律唔碰 ⇒ 零搬位風險（解 R3 O3／R4 O4）。

## 3. 可觀察性（量化前後）
加 **1 行 LOGGER.info**：`packai card-marker: cards=<n> hasMarker=<bool> injected=<bool>`
⇒ 可用 harness 跑 N 次同一物品，**統計**「cards≥1 但 hasMarker=false」嘅比率（修前 vs 修後）。

## 4. 檔案白名單
- `client/service/AskService.java`（新 helper ＋ 兩條路徑各一行呼叫）
- **新增** `tests/check_card_marker_fallback.py`（fixture：冇 marker＋有卡 ⇒ 補；已有 marker ⇒ 唔碰；cards=0 ⇒ 唔補；cardStrip 關 ⇒ 唔補）
- **唔准改**：`logic/AskReplyScrub.java`、`logic/AskEngine.java`（唔使動）、`logic/AskTrace.java`、`neoforge/**`、lang 檔

## 5. 驗收標準（統計式，回應「隨機」）
| # | 檢查 | 判準 |
|---|---|---|
| A1 | **修前 baseline**（現役 build）：同一物品（`tetra:modular_double`）跑 **3 次** | 記錄 `cards≥1 && hasMarker=false` 次數（預期 >0，證明隨機） |
| A2 | **修後**：同一物品跑 **3 次** | 該次數＝**0** |
| A3 | 對照 `minecraft:stone_axe`（5 卡，非框架） | 標記數目唔少過 baseline；**唔准**多加 |
| A4 | 負控 fixture：無卡 | 唔准補 marker |
| A5 | 122 閘＋新閘／49 Java 測試 | ≥121 綠、0 新紅、49 綠 |
| A6 | 日誌統計 | 每次 ask 都有 `card-marker:` 行；injected 次數＝(A1 baseline 失敗次數之期望) 相符 |

## 6. 風險／還原
- 貼錯行（貼咗去「怎么用」段）⇒ A3＋人眼核 trace；規則寫死「how-to-get 行尾，冇標題就最後一行 before 來源」
- `cardStrip` 判斷錯 ⇒ 新閘覆蓋兩態
- 還原：1 個 Java 檔（未 commit）→ 實作前 `%TEMP%\fu1b_backup_<ts>\` timestamped copy ＋ md5

## 7. 流程
1. 本 v5 → 反方 R1（**新機制＝新一輪**，唔繼承舊 4 輪）
2. cursor 實作 → Hermes 親驗（compile／49／122＋新閘／fixture 負控紅→綠）
3. **真機統計驗證**（3＋3＋對照；約 20–25 萬 tokens）＋可視需要影 UI 截圖
4. code review 兩輪 → 入部署清單
