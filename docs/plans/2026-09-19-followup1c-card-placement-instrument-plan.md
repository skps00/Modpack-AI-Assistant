# Follow-up #1c phase plan **v2** — 卡走位量度儀器（先重現、後修）

> 2026-09-19。**只適用 MC 1.19.2 + Forge 43.x**（`neoforge` PAUSED）
> **R1 ＝ 正方 3 : 反方 7**（報告：`docs/plans/reviews/2026-09-19_followup1c-plan-R1-opposing.md`）；v2 逐條回應 O1–O3。
> SK 症狀（逐字）：「**卡堆埋一齊, 卡去咗最尾 both**」＋「**same item can first time is bug, second time normal**」（隨機）。

## 0. v2 修正（對應 R1；**我自己核實過行號**）
| R1 | 實況（我核實） | v2 做法 |
|---|---|---|
| O1 CRITICAL：`maxSameBlock` 量唔到堆埋 | ✅ 成立：`:821 blocks.add(insertAt, Part.card(idx))` 每卡**獨立 Part** ⇒ 兩卡結構上**唔可能同 index**；真「堆埋」＝**兩卡之間冇 TEXT**（`skipCardsAfter:1112`） | **唔量 index**；改量**最終序列**：連續 CARD run 長度 → `maxCardRun ≥ 2` ＝ 堆埋 |
| O2 CRITICAL：`atLastBlock` ≠ 去最尾，且假陽性 | ✅ 成立：`:769 blocks.addAll(sourceParts)` 喺落位**之後**才 append【来源】⇒ log 時「最後 block」唔係答案尾；`wantSec<0`（maint）設計上就落絕對最尾 | 量**相對【来源】位置**：`cardsAfterSources`（>0 ＝ 去咗最尾）＋ `lastIsCard` |
| O3 HIGH：mode 枚舉唔完備 | ✅ 成立：`disperseUnplacedEmissionCards` 實有 **5 條** insertion 分支（`:907-909` absEnd／`:919-922` secTail／`:954-957` needle／`:960-965` secTail／`:969-971` clamp）；`UNPLACED` 恆 0 | **刪 mode 枚舉**；改為 log ① `byRef` 索引集合（`:819-823` 落位者）② 最終序列壓縮字串 ③ 派生指標 |
| O7 行號錯 | `emissionSectionOf` 實為 **`:1121`** | 已更正 |

## 1. 目標
每次 ask 輸出**最終 block 序列** ＋ 3 個直接對應 SK 講法嘅指標，令症狀**可量度、可重現**。

## 2. 儀器設計（**只 2 個 log 呼叫點**，唔碰分支邏輯）
### 2.1 config（默認關）
`config/PackAiConfig.java`：`.define("cardPlacementDiagLog", false)`（放 `jsObtainDiagLog` **同一個 toml 表**；`jsObtainDiagLog` 喺 `:466`、accessor 喺 `:962`）＋ accessor `public static boolean cardPlacementDiagLog()`

### 2.2 log（`logic/RecipeEmbed.java`；該檔現無 LOGGER → 加 1 個）
插入點：**`:769 blocks.addAll(sourceParts);` 之後、`:770 return blocks;` 之前**（即睇到**含【来源】嘅最終序列**）。
```
packai cardplace: cards=<n> byRef=[<i,...>] srcAt=<idx>
packai cardplace: seq=<壓縮序列，T=文字 block／C=卡／S=来源>
packai cardplace: maxCardRun=<m> cardRuns=<k> cardsAfterSources=<a> lastIsCard=<bool>
packai cardplace: pos=<每卡 1-based 位置，逗號分隔>
```
**判準（直接對應 SK 用語）**
| 指標 | 意思 |
|---|---|
| `maxCardRun ≥ 2` | **「卡堆埋一齊」** |
| `cardsAfterSources > 0` 或 `lastIsCard` | **「卡去咗最尾」** |
| `byRef` 空 + `maxCardRun=1` + `cardsAfterSources=0` | 正常（卡散落各步驟） |

### 2.3 為何唔再用舊 proxy
`AskTrace.markers()` 掃雙括號 `"[[recipe_card:"` 量唔到 `[card:N]`；`cardsOut` 只知數目。R4/R5 已證 `byFallback>0 ≠ 症狀`。

## 3. 白名單（3 檔）
- `config/PackAiConfig.java`（+1 key、+1 accessor）
- `logic/RecipeEmbed.java`（LOGGER ＋ 2 個 gated log 呼叫；**唔准改** `placed[]`／`skipCardsAfter`／`sectionLastAfter`／任何落位行為）
- **新增** `tests/check_cardplace_instrument.py`
- **唔准改**：`neoforge/**`、lang 檔、jar、`AskService.java`

## 4. 驗收
| # | 判準 |
|---|---|
| A1 | 親驗：`compileJava compileTestJava` rc=0；49 Java 測試全綠；122 python 閘 ≥121 綠、**0 新紅**；新閘負控**三條**（① 改壞 `:769` 之前/之後 anchor ② 刪 log ③ gate 條件改 `true`）各自應紅 |
| A2 | 沙盒 config `cardPlacementDiagLog=true` → 跑 **2 件物品 × 3 次 ＝ n=6**（木錘 1 卡／石斧 5 卡）⇒ 每次有齊 4 行；**樣本量限制明文**：n=6 ⇒ 只可聲稱殘餘率 ≤39%，唔可以講「一定冇」 |
| A3 | **重現判定**：6 次入面 ≥1 次 `maxCardRun ≥ 2` 或 `cardsAfterSources > 0` ⇒ 症狀可量度；**另加變異判準**：同一物品多次之間 `pos` 分佈有差異 ⇒ 已重現「隨機」 |
| A4 | **修法唔喺本 plan 範圍**（數據出咗另開 plan） |
| A5 | 默認關閉下**零輸出**（跑 1 次驗證冇 `cardplace:` 行） |

## 5. 流程
1. v2 → 反方 R2（若 <8:2 依 SK 3–4 輪上限停手）
2. cursor 實作（白名單；**唔准** commit／deploy 去真 instance／開 game）
3. Hermes 親驗（A1）
4. **等 SK 唔打機** → 部署沙盒 → 跑 n=6（A2/A3）→ 出數據
5. 數據出咗 → 另開修法 plan

## 6. 還原
log-only（2 檔）；key 默認 `false`；實作前備份 2 檔（md5）；沙盒 config 改完還原
**成本**：cursor ~20 分鐘；n=6 ≈ 25–30 萬 tokens（週六 DS 半價）；跑嗰陣要 SK 唔打機
