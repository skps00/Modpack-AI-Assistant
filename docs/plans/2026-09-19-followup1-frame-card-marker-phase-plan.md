# Follow-up #1 phase plan **v2** — 框架卡冇內聯 `[card:N]`（孤兒卡）

> v1 被反方判 **3:7**（`docs/plans/reviews/2026-09-19_followup1-plan-R1-opposing.md`）；v2 已親手核實所有更正。
> 版本：**只適用 MC 1.19.2 + Forge 43.x**

## 1. 症狀（三重證據：trace ＋ log ＋ code）
沙盒真機（`docs/research/artifacts/2026-09-19-autotest-run-v2/`）：

| case | cardsOut | 文字 `[card:N]` | `recipe_card_markers` | log 路徑 |
|---|---|---|---|---|
| `tetra:modular_double` | 1 | **1** | 0 | `how-to-get heading not found → **appended**` |
| `tetra:modular_sword` | 1 | **0** | 0 | `how-to-get **replaced** (STANDARD frame)` |
| `minecraft:stone_axe`（對照） | 5 | **2**（1,2） | 0 | 非框架路徑 |

⇒ **觸發條件＝LLM 已寫「怎么来」標題**時，標準框架分支會**整段取代**，`[card:1]` 連帶消失；標題唔存在時係**附加**，標記保住。

## 2. 根因（已核實到行，v1 嘅歸屬係錯）
- 真兇：`AskReplyScrub.replaceHowToGetBody(answer, fill)`（`AskReplyScrub.java:944-948`，javadoc 逐字「**Replace how-to-get section body with fill (heading kept)**」）
- 呼叫點：`AskEngine.java:980`（STANDARD frame 分支；`line = HonestMiss.frameStandardRecipeLine(lang, stdRecipe)`）
- **唔係** `HonestMiss.insertLineBeforeSources`（v1 講錯；該 function 係「附加」路徑，反而係保住標記嗰條）
- `AskMarkerRepair.repair()` 只修補**已存在**標記 ⇒ 冇 fallback
- **量度儀器更正**：`AskTrace.markers()`（`:759`）掃嘅係 `"[[recipe_card:"`（雙括號 guide interleave）⇒ `recipe_card_markers` **唔可以**用嚟量 `[card:N]`（v1 驗收標準作廢）

## 3. 修法（最小；兩者一齊做）
**F1（主要）**：喺**卡已發出之後**嘅位置（`client/service/AskService.java`，emit／refs 已定，約 `:2479` 之後區段），若最終 body **冇任何** `[card:N]` 而**呢件 focus 物有已發出嘅卡** → 喺該配方行尾補 ` [card:N]`（N＝該卡 ref，唔准重新編號）。理由：refs 只有喺 emit 之後才確定（log 實證 `refs=1`）。
**F2（防資訊流失）**：`replaceHowToGetBody` 取代前，若原 body 已含標準配方嘅**全部材料＋產出名**（`HonestMiss.bodyContainsAllLabels` 已有同類 helper）→ **跳過取代**（LLM 已寫對，連標記同上下文都保住）。
- **唔准**：改 lang 字串、改 `[card:N]` 編號語意、動 `AskTrace.markers()`（避免撞現有閘）

## 4. 檔案白名單
- `client/service/AskService.java`（F1；`:2444` stripAiRecipeCardMarkers 一帶要睇清楚）
- `logic/AskReplyScrub.java`（F2 嘅判斷）
- `logic/AskEngine.java`（只在需要傳遞旗標時；`AskEngine.java:980` 呼叫點）
- **新增** `tests/check_frame_card_marker_text.py`（新閘：靜態＋fixture；量 `[card:N]`）
- **唔准改**：`logic/AskTrace.java`、`neoforge/**`、lang 檔

## 5. 驗收標準（**儀器已更正**：量 `display.body.final` 內嘅 `[card:N]`）
| # | 檢查 | 判準 |
|---|---|---|
| A1 | 真機 case **必須觸發 replaced 路徑**（log 見 `how-to-get replaced`） | 文字 `[card:N]` **≥1**，且 N ≤ `cardsOut`；卡片位置喺該步驟下面 |
| A2 | 對照 `stone_axe`（5 卡） | 文字標記**唔少過** baseline（2；唔准退化） |
| A3 | 負控：冇卡嘅物品（`minecraft:bedrock`） | `cardsOut=0` ⇒ 文字**唔准**有 `[card:N]` |
| A4 | 資訊唔准流失 | 取代後「怎么来」仍要含**材料＋產出**（唔准只剩一句模板） |
| A5 | **UI 證據**（解 R1 最貴未知 U1） | 沙盒截圖：卡真係喺該步驟下面（唔係堆到最尾） |
| A6 | 現有 122 閘＋新閘 | ≥121 綠（＋新閘）、0 新紅 |
| A7 | 49 Java 測試 | 全綠 |

## 6. 風險／還原
- `[card:N]` 號錯 → 出錯卡（A1 要逐個 N 對卡面核）
- 特製版（非標準）路徑、`frame_standard_recipe_variants`（lang `:514`）唔准受影響
- 還原：2-3 個 Java 檔（工作樹未 commit）→ 實作前 `%TEMP%\fu1v2_backup_<ts>\` timestamped copy ＋ md5

## 7. 流程
1. 本 v2 → 反方 R2 review（≥8:2）
2. cursor 實作 → Hermes 親驗（compile／49／122＋新閘／負控紅→綠）
3. 真機 harness（double＋sword＋stone_axe＋bedrock；約 15–20 萬 tokens）＋**截圖**
4. code review 兩輪（SK 規則）→ 通過才入部署清單
