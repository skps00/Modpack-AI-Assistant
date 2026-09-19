# Follow-up #1c phase plan **v3** — 卡走位：**離線語料儀器**（零遊戲、零 LLM、可重複）

> 2026-09-19。**只適用 MC 1.19.2 + Forge 43.x**（`neoforge` PAUSED）
> 路線：SK 揀 `1`（用真 trace 做離線語料）——因 8 輪 plan review（6 修法＋2 儀器）全部未過 8:2，根因＝**log 側 proxy 唔等於畫面**。
> plan 前身：R1 **3:7**（index 度量唔到）→ v2 **4:6**（`cardsAfterSources` 係幽靈、`maxAdjacent` 有 base-rate 假陽性）。

## 0. 硬約束（我親手核實）
| 事實 | 證據 | 對設計嘅影響 |
|---|---|---|
| `RecipeCard` 係 **record**，靜態工廠（`crafting3x3(...)`／`flow(...)`）要 `ItemStack` ⇒ **headless 建唔到** | `logic/RecipeCard.java:20/275/301`；`src/test/.../AskCardPlacementCheck.java` 註釋「Does not construct RecipeCard (needs Minecraft registry)」 | **Java 離線語料測試做唔到**（要改生產碼加 test factory）→ 離線層用 **Python mirror**，Java 側只加**行為 log** |
| UI 真正消費點 ＝ `RecipeEmbed.interleaveEmissionCards(answer, cards)` | `client/gui/AiAssistantScreen.java:839`（另 `:867-868` 係 `cardStrip` 路徑） | 語料測試要對齊**呢個函數**嘅輸出序列 |
| 現有 mirror | `tools/card_placement_test.py`（repo 已有） | 擴充成語料掃描器，唔另起一套 |
| 真 trace 語料 | `docs/research/artifacts/*/*.jsonl`（**7** 檔；含 `display.body.final`＋`render.cards.final`） | 語料來源（離線、已 commit） |

## 1. 目標（一句）
離線跑真 trace 內文 → 出「**版面指標 + base rate + 成因標籤**」報告，令「卡堆埋／去最尾」**可量化、可重複、零成本**；**真值驗證**留待一次沙盒 run（UI 層 log）。

## 2. 兩層設計
### 2.1 離線層（Python mirror；**假設生成器**，唔係真值）
新 `tools/card_placement_corpus.py`：
- 讀語料 `docs/research/artifacts/2026-09-19-cardplace-corpus/corpus.json`（由新 `tools/build_cardplace_corpus.py` 從 7 個 trace 抽：`display.body.final` 原文、`render.cards.final` 卡數／role、`[card:N]` 出現集合）
- 忠實重寫 `interleaveEmissionCards` 序列（`splitTextIntoStepBlocks` → `placeEmissionCardsByRef` → `disperseUnplacedEmissionCards` → `stripCardRefTokens` → `addAll(sourceParts)`；分支行號逐一對應現有碼）
- 每 case 輸出：`cards`、`byRef=[...]`、最終 `seq`（T/C/S 壓縮）、`maxAdjacentCards`、`cardsInTail`（以 `srcStart = len(blocks) - len(sourceParts)` 為界；**唔用 index 相等**）、`cause`（`byRef-multi`／`secTail-dump`／`maint-absEnd`／`needle-miss`）
- 出 **base rate 表**（真語料內幾多 % 已亮燈）＋ **合成負控**（手造明顯堆埋 case 必須亮燈）⇒ 直接回應 R2 O-R2-2（base rate）＋O-R2-1（幽靈）
- 決定性：同一輸入 → 同一輸出（無 LLM、無時間）

### 2.2 真值層（唯一真值；1 行 gated log；**下一次沙盒 run 才用**）
`client/gui/AiAssistantScreen.java`（`:839` 之後）：`cardPlacementDiagLog` 為 true 時，log 出 `parts` 最終序列（T/C/S 壓縮）＋每卡位置 ⇒ **畫面真正睇到嘅次序**（唔再由 log proxy 推）。
- config key `cardPlacementDiagLog`（默認 `false`；跟 `jsObtainDiagLog` 慣例 `config/PackAiConfig.java:466/962`）
- 唔改任何落位行為

## 3. 白名單
- **新增** `tools/build_cardplace_corpus.py`、`tools/card_placement_corpus.py`、`docs/research/artifacts/2026-09-19-cardplace-corpus/corpus.json`、`tests/check_cardplace_corpus.py`
- `forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java`（+1 gated log 行）
- `forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java`（+1 key、+1 accessor）
- **唔准改**：落位邏輯（`RecipeEmbed.java` 完全唔碰）、`neoforge/**`、lang、jar

## 4. 驗收
| # | 判準 |
|---|---|
| A1 | 語料建成：`corpus.json` 有 **7** 個 case、每個有原文＋卡數；`git` 已 commit（可審計） |
| A2 | 掃描器可重複：連跑 2 次輸出**逐字節相同**（決定性）；報告含 base rate 表 ＋ 每 case 指標 |
| A3 | **鑑別力**：① 合成負控（明顯堆埋）必須亮燈 ② 合成正常 case 必須唔亮 ③ 真語料 base rate 有數字（若 base rate 高 ⇒ 證明「亮燈」唔等於症狀，報告要分開講） |
| A4 | 真值 log：gated 默認關 ⇒ 跑一次驗證零輸出；開咗則出 1 行（**沙盒 run 留待 SK 唔打機**） |
| A5 | 閘：`tests/check_cardplace_corpus.py`（源碼形狀＋負控：改壞配置／刪 log 應紅）；122 閘 ≥121 綠、0 新紅；49 Java check 全綠；`compileJava compileTestJava` rc=0 |
| A6 | **本 plan 唔做修法**（報告出咗另開 plan） |

## 5. 流程
1. v3 → 反方 R1（cap 3–4 輪內）
2. cursor 實作（白名單；**唔准** commit／deploy 真 instance／開 game）
3. Hermes 親驗 A1–A3、A5
4. 報告 SK：base rate ＋ 成因分佈 ＋ **初步 hypothesis**（邊種寫法出事）
5. 如需要真值 → 等 SK 唔打機跑一次沙盒（A4）→ 之後才寫修法 plan

## 6. 成本／還原
- 掃描：**0 token、0 GUI**（純 python，跑幾秒）
- cursor：~20–30 分鐘；備份 2 個 Java 檔（md5）
- 還原：log 行 gated、key 默認 false；mirror 係 `tools/` 腳本（唔影響 runtime）
