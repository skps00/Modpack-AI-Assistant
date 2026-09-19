# Follow-up #1 plan v2 — 反方 review（R2）

> ⚠️ 本檔由 Hermes 由 subagent 輸出（`deleg_ce0c3f9e`）**逐字重建**：該 subagent 讀完所有來源但 tool 迭代額度用盡，報告未寫入磁碟。原文見 `%LOCALAPPDATA%\hermes\cache\delegation\subagent-summary-0-20260919_162359_990594.txt`。

## ① 驗過嘅事實（真／假／未核實）
- **真**：`AskReplyScrub.java:943-948` javadoc 逐字「Replace how-to-get section body with fill (heading kept).」；方法體 `:948-965`；早退 `:953-955` `if (!head.find()) { return answer; }`；邊界 `:956-964`。v2 §2 定位正確。
- **真**：`AskEngine.java:980` `String replaced = AskReplyScrub.replaceHowToGetBody(body, line);`；`:983-984` log「how-to-get replaced (STANDARD frame)」；`:986-988` appended 分支；`:991` `ensureFrameStandardRecipeVisible` 第二回落。
- **真**：v2 §1 三個 case 數字可原樣重現（sword 文字 `[card:N]`=0、cardsOut=1；double=1、cardsOut=1；stone_axe=2、cardsOut=5；三 case `recipe_card_markers` 皆 []）。
- **真**：log 兩行存在於 `packai_dev_game/logs/debug-1.log.gz`，時間戳同 trace 逐秒對上 ⇒ R1 未核實項已解。
- **真**：`AskTrace.java:759` `String needle = "[[recipe_card:";` ⇒ `recipe_card_markers` 對 `[card:N]` 結構性盲（v2 儀器更正成立）。
- **真【N 嘅編號空間】**：`RecipeEmbed.java:77-78` `CARD_REF_TOKEN` + `:789-812` `int idx = n - 1; if (idx >= 0 && idx < cards.size() && !placed[idx])` ⇒ **N 係 shown strip 嘅 1-based 位置索引，唔係 refId**；refId 出自 `AskToolEnv.java:83`／`CardEmission.java:8`。
- **真**：`AskService` 有**兩條**近乎相同 ask 路徑——`runAsk`（`:129-461`，AI 分支 `:399-409`）同 `askBlocking`（`:2275-2513`，AI 分支 `:2478-2487`）。
- **真**：`AskResult.java:84-91` `withRecipeCards(cards, strip)` 內部再行 `AskJeiHints.scrubAbsenceClaimsWhenCards`＋`RecipeCardsMode.scrubMarker`＋`AskReplyScrub.scrubPromptEcho`。
- **真**：`AskService.java:2444` 只剝 `[[recipe_cards?:...]]`；`HonestMiss.java:232` `bodyContainsAllLabels`（package-private）；122 閘實跑＝**121 綠、1 紅**（`check_ask_display_leak.py` rc=2「need real-machine smoke」，非 card marker）。
- **真（反證 A3）**：`NEG_bedrock` 恆為 `NO_SAMPLE`（`AutoTestHarness.java:475-502`）⇒ A3 用 bedrock 係空頭閘。
- **真（反證 A5）**：harness 無 screenshot code（grep 0 hit）、`RecipeEmbed` 無 interleave log（grep `LOGGER`=0）、sandbox `screenshots/` 最新 09-11；`sk_activity.json`（16:17:25）＝ `playing / counter-strike 2 / fullscreen` ⇒ 依活動 Gate 此刻禁開窗截圖。
- **未核實**：F2 skip 會否令 sword 由 replaced 變 skip；`RecipeCardsMode.scrubMarker` 會否剝 `[card:N]`。

## ② 問題（severity＋證據）
- **O1 HIGH**：F1 嘅 N 語意寫錯（應該係 shown 1-based index，唔係 refId；drop 實測存在 dropped=1／9）。照 v2 字面做 → 越界被 strip（body 仍有 token＝假綠）或撞位顯示錯卡。
- **O2 HIGH**：F2 實質推翻已批嘅 canonical 句決定（`docs/plans/2026-09-18-frame-standard-affirmative-answer.md:45`），且把 MC runtime 依賴（`OfficialDisplay`）引入純函式 `AskReplyScrub`。
- **O3 MEDIUM-HIGH**：雙 ask 路徑漏改風險（plan 只寫一個落點）。
- **O4 MEDIUM-HIGH**：落點未精確（須喺 `withRecipeCards(cardsOut, true)` `:2483-2484` 之後、`finishAskTrace` 之前）。
- **O5 MEDIUM**：A3 負控不可能驗（bedrock 永不進 ask）。
- **O6 MEDIUM**：A5 UI 證據現時不可行（無截圖 code／無 log／活動 gate 禁開窗）⇒ 要麼入白名單，要麼明文降級。
- **O7 MEDIUM**：未比較「保留原文 marker」方案（反方估：1 檔約 8 行）。
- **O8 LOW-MEDIUM**：A2 baseline 依賴 LLM 行為（下次 LLM 唔寫就 false red）。

## ③ 比分
**正方 4 : 反方 6**（未達 8:2 ⇒ 唔准開工；R1 兩個 CRITICAL 已真解決，剩 3 條 HIGH／MEDIUM-HIGH 屬「可修」而非「方向錯」）。

## ④ Flip conditions
Fc1 N＝shown index+1 並引 `RecipeEmbed.java:804-805`；Fc2 落點寫死喺 `:2483-2484` 之後並附「scrub 唔剝 `[card:N]`」證據；Fc3 兩條路徑都改或抽 helper；Fc4 F2 取 SK 批准或改窄；Fc5 A3 換設計（移除 bedrock 依賴）；Fc6 A5 給可行取證路徑或明文降級；Fc7 補三方案最小 diff 比較；Fc8 A2 改為程式可保證判準；Fc9 全部滿足 ⇒ ≥8:2 方可開工。
