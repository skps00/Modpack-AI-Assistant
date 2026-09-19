# Follow-up #1 phase plan **v4（極簡版）** — 框架卡 `[card:N]` 被 replaced 路徑食走

> R1 **3:7** → R2 **4:6** → R3 **5:5**（3 輪未達 8:2，依 SK 規則曾停手報告）。
> **2026-09-19 SK 親口確認**：「冇 marker 真係令卡走位，that is a problem that before I use u」⇒ **U1（最貴未知）解答：症狀玩家可見、真問題，必做**。
> 版本：**只適用 MC 1.19.2 + Forge 43.x**

## 0. v4 設計（回應 R3 兩條 HIGH：O1／O2 用「唔理 cardsOut」直接繞開）
**改動只喺 `logic/AskReplyScrub.java` 嘅 `replaceHowToGetBody` 內部**（佢自己知道被取代嘅 span）：

```
1. 喺 bodyStart..bodyEnd（＝即將被取代嘅 how-to-get 段）內，抽走所有 [card:N] token（regex \[card:(\d+)\]，保留出現次序、去重）
2. 執行原有取代（canonical 句照寫，維持 2026-09-18 已批決定）
3. 若抽到 token：append 到新句行尾（` [card:N]`；多個按原序）
4. 冇抽到 ⇒ 行為同今日完全一樣（零回退風險）
```
- **唔需要** `cardsOut`（解 R3 O2：唔使喺 AskEngine 重覆 keep-only 政策）
- **只喺 span 內抽**（解 R3 O3：唔會搬走「怎么用」段嘅合法 marker）
- token 若越界 → `RecipeEmbed.java:804-805` 照樣 strip ⇒ **最壞＝回到現狀**（唔會更差）
- 兩條 ask 路徑（`AskService :399-409` / `:2478-2487`）都經 `AskEngine.ask`（`:320`/`:2428`）⇒ 一處覆蓋

## 1. 檔案白名單
- `logic/AskReplyScrub.java`（唯一改動）
- **新增** `tests/check_frame_card_marker_preserve.py`（新閘：span 內抽／貼；span 外 marker 原封不動；冇 marker ⇒ 行為不變）
- **唔准改**：`AskEngine.java`、`AskService.java`、`AskTrace.java`、`neoforge/**`、lang 檔

## 2. 驗收標準（全部程式可保證）
| # | 檢查 | 判準 |
|---|---|---|
| A1 | 真機 case **觸發 replaced**（log `how-to-get replaced`） | 取代後 body 含 `[card:N]`；**N 對得上該卡**（人眼核 trace 原文該行） |
| A2 | span 外 marker 唔准被搬（用 `debug-4.log.gz` 14:09:48 真 body 做 fixture） | 「怎么用」段嘅 `[card:2]` 位置不變 |
| A3 | fixture：span 內冇 marker | 結果**冇** `[card:N]`（唔准無中生有） |
| A4 | fixture：span 內有 2 個 marker | 兩個都貼返、次序不變 |
| A5 | **SK 目視驗收**（U1 已由 SK 確認症狀；最終以 SK 望一眼為準） | 卡真係喺該步驟下面 |
| A6 | 122 閘＋新閘／49 Java 測試 | ≥121 綠、0 新紅、49 綠 |
| A7 | 覆蓋聲明 | harness 只行 `askBlocking`；`runAsk` 由新閘靜態覆蓋（同一 function） |

## 3. 風險／還原
- 一行放兩個 token 或次序錯 ⇒ A4 fixture 擋
- 還原：1 個 Java 檔（未 commit）→ 實作前 `%TEMP%\fu1v4_backup_<ts>\` timestamped copy ＋ md5

## 4. 流程
1. 本 v4 → 反方 **R4（第 4 輪＝上限）**；若仍未達 8:2 ⇒ **唔再輪**，直接交 SK（但 U1 已解，建議 SK 可批准照做）
2. cursor 實作 → Hermes 親驗（compile／49／122＋新閘／fixture 負控紅→綠）
3. 真機 harness（double／sword／stone_axe）
4. code review 兩輪 → 入部署清單


---

# 附錄：v3 內容（歷史；已被 v4 取代，保留供 review 對照）

> R1 **3:7** → R2 **4:6**（`docs/plans/reviews/2026-09-19_followup1-plan-R{1,2}-opposing.md`）；v3 逐條回應 R2 嘅 O1–O8／Fc1–Fc8。
> 版本：**只適用 MC 1.19.2 + Forge 43.x**

## 1. 症狀（三重證據：trace ＋ 沙盒 log ＋ code）
| case | cardsOut | 文字 `[card:N]` | log 路徑 |
|---|---|---|---|
| `tetra:modular_double` | 1 | 1 | `heading not found → **appended**`（15:41:57.741）|
| `tetra:modular_sword` | 1 | **0** | `how-to-get **replaced** (STANDARD frame)`（15:42:23.735）|
| `minecraft:stone_axe`（對照） | 5 | 2 | 非框架路徑 |
（`recipe_card_markers` 一律 0＝正常，因 `AskTrace.java:759` 掃嘅係 `"[[recipe_card:"`，同 `[card:N]` 無關）

## 2. 根因（已核實到行）
- `AskEngine.java:980` → `AskReplyScrub.replaceHowToGetBody(body, line)`（`AskReplyScrub.java:944-948`，javadoc「Replace how-to-get section body with fill (heading kept)」）⇒ 標準框架分支**整段取代**，LLM 寫嘅 `[card:1]` 消失
- 標題唔存在時走 `:986-988` **append** 分支 ⇒ 標記保住（同 log 一致）

## 3. 三個方案比較（回應 R2 O7／Fc7）——**採用 S**

| 方案 | 改動 | 最小 diff | 為何唔揀 |
|---|---|---|---|
| **S（採用）**：取代前抽出 body 內原 `[card:N]`，取代後貼回該配方行尾 | `logic/AskEngine.java` 一處（約 8–12 行） | **1 檔** | — |
| F1：喺 `AskService` emit 後按 identity 算 index 補 marker | `AskService.java` **兩條路徑**（`:399-409`／`:2478-2487`）＋落點要喺 `withRecipeCards(:2483-2484)` 之後 | 2 段＋N 換算 | 要處理 refId→shown index 換算（drop 令兩者唔等，實測 dropped=1／9） |
| F2：原文已含全部材料名就**跳過取代** | `AskEngine`＋`AskReplyScrub` | 1–2 檔 | **推翻 2026-09-18 已批嘅 canonical 句決定**；且會把 MC runtime 依賴（`OfficialDisplay`）引入純函式 `AskReplyScrub` |
| P：`replaceHowToGetBody` 加 `preserveMarkers` 參數 | `AskReplyScrub`＋呼叫點 | 2 檔 | 同 S 等效但改多一個檔 |

**S 為何安全**：① 兩條 ask 路徑都經 `AskEngine.INSTANCE.ask`（`AskService.java:320`／`:2428`）⇒ 一處覆蓋兩者（解 R2 O3）；② 標準框架經 keep-only 後**只剩 1 張卡**（log：`cards kept=1 dropped=0 refs=1`）⇒ 正確 N 恆為 **1**，唔需要 index 換算（解 R2 O1 嘅 refId≠index 問題）；③ `[card:N]` **唔會被任何 scrub 剝走**（已核實：`RecipeCardsMode.MARKER` 只 match `[[recipe_cards:on|off]]`、`CARD_INDEX` 只 match `[[recipe_card:N]]`、`AskReplyScrub.scrubPromptEcho` 無 `[card:` 規則）⇒ 貼回後可存活到 UI（解 R2 O4／Fc2）。
**S 嘅確定性規則**：貼回時若原 N **>** 最終 `cardsOut` → 夾到 `1`（因 keep-only 後只有 1 張）；`cardsOut=0` ⇒ **唔准**貼（解 R2 O5／Fc5 嘅一半）。

## 4. 檔案白名單
- `logic/AskEngine.java`（唯一改動：標準框架分支抽／貼 marker）
- **新增** `tests/check_frame_card_marker_preserve.py`（新閘：靜態斷言 S 嘅三個規則 ＋ fixture：有卡⇒marker／冇卡⇒唔補／N>cardsOut⇒夾 1）
- **唔准改**：`client/service/AskService.java`、`logic/AskReplyScrub.java`、`logic/AskTrace.java`、`neoforge/**`、lang 檔

## 5. 驗收標準（全部**程式可保證**，唔靠 LLM 行為；回應 R2 O8／Fc8）
| # | 檢查 | 判準 |
|---|---|---|
| A1 | 真機 case **必須觸發 replaced 路徑**（log `how-to-get replaced`） | 文字 `[card:N]` **≥1** 且 N=1 ≤ cardsOut |
| A2 | 對照 `stone_axe` | 文字 `[card:N]` 數目**唔少過 baseline 2**；cardsOut=5 |
| A3 | **fixture 負控（唔靠 bedrock）**：`cardsOut=0` 嘅 body | marker **唔准**被補（新閘內跑，免開 game） |
| A4 | 資訊唔准流失（取代後仍含材料＋產出） | 新閘 fixture 斷言 |
| A5 | **UI 證據＝本輪明文降級**：現時做唔到（harness 無截圖 code、`RecipeEmbed` 無 interleave log、SK 正玩 CS2 fullscreen ⇒ 活動 gate 禁開窗）→ 記入 `REMAINING_WORK`，等 SK 空閒或另開一輪 | 降級聲明寫入 plan（唔留空當做得到） |
| A6 | 122 閘＋新閘 | ≥121 綠、0 新紅（baseline 紅＝`check_ask_display_leak.py` rc=2） |
| A7 | 49 Java 測試 | 全綠 |
| A8 | 真機 bedrock case | 仍然 `NO_SAMPLE`（driver 層 sanity，**唔**當 marker 判準） |

## 6. 風險／還原
- 貼回位置錯 ⇒ marker 落喺錯行：A1 要**人眼核**該行係配方行（trace 原文）
- 非 STANDARD 路徑、特製版路徑唔准受影響（加 fixture）
- 還原：1 個 Java 檔（未 commit）→ 實作前 `%TEMP%\fu1v3_backup_<ts>\` timestamped copy ＋ md5

## 7. 流程
1. 本 v3 → 反方 R3 review（≥8:2）；**若 R3 仍未達 8:2 ⇒ 依 SK 3–4 輪上限停手，交 SK 決定**
2. cursor 實作 → Hermes 親驗（compile／49／122＋新閘／負控紅→綠）
3. 真機 harness（double／sword／stone_axe／bedrock）
4. code review 兩輪 → 通過才入部署清單
