# Follow-up #1c phase plan — 卡走位**量度儀器**（先重現、後修）

> 2026-09-19。**只適用 MC 1.19.2 + Forge 43.x**（`neoforge` PAUSED）
> 前情：follow-up #1（框架卡 `[card:N]`）6 輪 review 未過（3:7→4:6→5:5→6:4→4:6→3:7）已停手，未改任何 code。
> **SK 定義症狀（逐字）**：「**卡堆埋一齊, 卡去咗最尾 both**」＋「**same item can first time is bug, second time normal**」（隨機）。
> 已知教訓（R4/R5/v6）：**`byFallback>0` ≠ 症狀**（真反例 `ask-20260919-140842` 落位正確）；`RecipeEmbed` 本身已有 needle-disperse 防堆埋邏輯。
> **⇒ 本 plan 只做「量度儀器」，令症狀可量度；修法另開 plan（由數據決定）。**

## 1. 目標（一句）
每次 ask 之後，log 出**每張卡實際落喺邊個 block／用咩路徑落位**，同**有冇 ≥2 張卡落同一個位** ⇒ 令「堆埋／去最尾」變成可量度、可重現。

## 2. 儀器設計
### 2.1 config（默認關；零行為改動）
`config/PackAiConfig.java`（跟現有慣例 `jsObtainDiagLog`）：
```java
.define("cardPlacementDiagLog", false);          // ~:466 附近
public static boolean cardPlacementDiagLog();    // ~:962 附近
```

### 2.2 落位記錄（`logic/RecipeEmbed.java`；該檔現時無 LOGGER → 加 1 個）
三個落位點各記一次（**只喺 `cardPlacementDiagLog()` 為 true 時輸出**）：
| 落位點 | 行（現況） | 記錄 mode |
|---|---|---|
| `placeEmissionCardsByRef` 插入 | `:819`（`insertAt = skipCardsAfter(blocks, i+1)`） | `MARKER`（有 `[card:N]` 引用） |
| `disperseUnplacedEmissionCards` 分散到 numbered step | `:889-925`（needle score > 0） | `NEEDLE` |
| 同上之 section 尾 fallback | `:1010 sectionLastAfter(blocks, wantSec)` | `SECEND` |
| 最後仍未 placed | 管線尾 | `UNPLACED` |

**輸出格式（每次 ask ≤ ~20 行）**
```
packai cardplace: n=<i> item=<itemId> mode=<MARKER|NEEDLE|SECEND|UNPLACED> sec=<0|1|2|-1> at=<blockIdx>
packai cardplace: summary cards=<n> marker=<n> needle=<n> secend=<n> unplaced=<n> maxSameBlock=<m> atLastBlock=<k>
```
`maxSameBlock` ＝ 同一 blockIdx 上最多幾張卡（**≥2 ＝「堆埋」**）；`atLastBlock` ＝ 落喺最後一個 block 嘅卡數（**>0 ＝「去咗最尾」**）。

### 2.3 為何唔用現有 trace
`AskTrace.markers()` 掃 `"[[recipe_card:"`（雙括號），量唔到 `[card:N]`；`render.cards.final.cardsOut` 只知幾張、唔知落喺邊。→ 需要新 log。

## 3. 白名單（3 檔）
- `forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java`（+1 key、+1 accessor）
- `forge/1.19.2/src/main/java/com/skps9/packai/logic/RecipeEmbed.java`（LOGGER ＋ 4 個記錄點，全部 gated）
- **新增** `tests/check_cardplace_instrument.py`（源碼級閘：① key 存在且默認 `false` ② 三個落位點都有 gated log ③ 無 gated 以外的新 log）
- **唔准改**：落位邏輯本身（`placed[]`／`skipCardsAfter`／`sectionLastAfter` 行為）、`neoforge/**`、lang 檔、jar

## 4. 驗收標準
| # | 判準 |
|---|---|
| A1 | 親驗：`compileJava compileTestJava` rc=0；49 Java 測試全綠；122 python 閘 ≥121 綠、**0 新紅**；新閘負控（改壞 → 紅） |
| A2 | 沙盒 `config/packai-client.toml` 開 `cardPlacementDiagLog=true` → 跑同一物品（木錘）**3 次** ⇒ 每次 `latest.log` 有齊 per-card ＋ summary 行 |
| A3 | **重現判定**：3 次入面至少 1 次出現 `maxSameBlock ≥ 2`（堆埋）或 `atLastBlock ≥ 1`（去最尾）或 `unplaced ≥ 1` ⇒ 症狀**已可量度**；若 3 次全部 `marker=cards`，擴大樣本至多卡物品（石斧 5 卡）＋另一件 Tetra 工具 |
| A4 | **唔喺本 plan 範圍**：任何修法（改落位邏輯）留待數據出咗另開 plan（避免重蹈 6 輪 review 覆轍） |
| A5 | 默認關閉下**零輸出**（跑 1 次驗證冇 `cardplace:` 行） |

## 5. 流程
1. 本 plan → 反方 R1（新機制第一輪）
2. cursor 實作（白名單；唔准 commit／deploy 去真 instance／開 game）
3. Hermes 親驗（A1）
4. **等 SK 唔打機**（活動 gate）→ 部署入**沙盒**（唯一批准路徑 `mc_mod_deploy_jar.py --target packai` 唔涉及真 instance）→ 跑 3 次（A2/A3）→ 出數據
5. 數據出咗 → 另開修法 plan

## 6. 還原
- log-only 改動（2 個 Java 檔）；`cardPlacementDiagLog` 默認 `false` ⇒ production 行為零變
- 實作前備份 2 檔（`%TEMP%\cardplace_backup_<ts>\` ＋ md5）；沙盒 config 改完還原

## 7. 成本
cursor 實作 ~20 分鐘；baseline 3 次 ≈ 15 萬 tokens（今日週六＝DS 半價）；**零 GUI 干擾**（沙盒係獨立 instance，開嗰陣要 SK 唔打機）
