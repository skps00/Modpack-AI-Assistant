# Plan v3：落點 B — 能力反查（**唔加 tool**，改為「按需注入候選事實」）（2026-10-10）

- v1 R1 **3:7**（S2 前提假）；v2 R2 **3:7**（S2b thread 不安全／tool 可達性／V5 真值不可行／ROI）。
- **兩輪都被同一族證據打穿 ⇒ 我改變設計：唔做第 16 個 model-visible tool。**

## 〇、R2 逐條裁決（全部採納）

| # | 反方指控（有證據） | 裁決 |
|---|---|---|
| **S2b（high）** | `TooltipCapture` 要 `LocalPlayer`，但 tool 只喺 worker thread 行（`AskService:318 supplyAsync`；`AskToolEnv` 冇 player）⇒ off-thread 跑任意 mod 嘅 `getTooltipLines` ＝ data race／crash | **採納**：**完全唔用 tooltip** |
| **可達性（high）** | `capability_search` 唔入 drain（`AskToolLoop:338/341-359`）又唔入 `FIRST_ROUND` ⇒ 唯一觸發係 LLM 自己叫；而 v2 想靠嘅兩個 guard 對目標題**近乎 no-op**（`:377` 要 `skipLlm`，`:424` 要 `highConfidence`）；另漏 `:276` offline quest return、`AskService:311-316` token 封頂 | **採納**：唔加 tool ⇒ 呢整片雷區消失 |
| S2a 成本（med） | 唔應該每次 ask 重掃 registry；repo 已有 **`ItemIndex.INSTANCE`（12,708 條 item label＋stack、一次建、async＋disk cache）** | **採納**：重用 `ItemIndex`，零新掃描 |
| namespace≠modid（low） | 要 `getModContainerById` 反查＋明文 fallback，唔准把 namespace 當 modid 斷言 | **採納** |
| K 無數值（med） | 冇 K、冇 per-capture 量測 | **採納**：唔用 tooltip ⇒ 無 K 問題；候選硬上限 N≤8 |
| intent 閘未定義（med） | 現有 predicate 全係 substring 關鍵詞表；「自動合成」「xx 工廠」跨幾個 predicate | **採納**：v3 附**真實問題語料量測**（見 V4） |
| 落地位漏第 8 項（med） | tool 路由靠 `KeybindAskTool:31-32` 嘅 call-FIRST 描述；`check_ask_tool_loop.py:117-136/150-153` 鎖檔案清單 | **採納**：唔加 tool ⇒ 大部分消失；仍要睇 `check_ask_tool_loop` 有冇掃 `logic/*AskTool.java` |
| **V5 真值不可行（high）** | DJ2 `enderutilities` 係 **1.12.2**，喺 1.19.2 沙盒根本唔存在；沙盒（NFWC 231 mod）**有** `refinedstorage-1.11.7.jar` | **採納**：V5 真值全部換成**沙盒內真存在**嘅嘢 |
| **ROI／gate（med）** | 加第 16 個 tool 令**每次 ask** 多一份 schema，而 fixed payload 已佔 82.4%（51,758 tok/ask）——**正係 SK 原本投訴**；且同 `item_search`／`purpose_lookup`／`acquire`／`quest_fetch` 語義重疊 | **採納（關鍵）**：**唔加 tool ⇒ 零固定成本增加** |

## 1. 目標與誠實預期（唔搬龍門）

**目標**：玩家問「包入面邊樣嘢可以做到 X」（唔給 item id）時，mod **按需**把**包內真實證據**（物品顯示名候選＋mod 描述）注入 prompt，令答覆由「靠模型通用知識」變成「有本包證據」，並誠實標「候選／未確認」。

**設計原則（直接回應 SK 嘅成本投訴）**：呢個功能**只喺能力題觸發**，普通題目**零額外 token**（唔加 tool、唔加固定 payload）。

**誠實預期**：文字來源天花板約 2/5；幾何／具體機器類**預期撈唔到** → 必須答「未確認」。**<2/5 就唔算完成。**

## 2. 設計

### 2.1 意圖閘 `PackIndex.isCapabilityQuestion(question)`

- 新 predicate；以「需求／能力」語意為主（中英關鍵詞＋唔含具體物品 token 嘅「邊個 mod／有冇得／可以做到」句式）。
- 因為 v3 係**加料**（唔係短路），誤觸只係多幾行事實；**誤殺**才係失效 ⇒ V4 量 **recall 為主**，兼量誤觸率（要有數，唔准拍腦）。

### 2.2 候選檢索 `CapabilityCandidates.find(need, N)`

| 來源 | 內容 | 成本 |
|---|---|---|
| **A** | **`ItemIndex.INSTANCE`** 已有嘅 12,708 條 item label（**重用，唔重掃**）→ 反查 namespace → `ModList.getModContainerById` 反查 mod（查唔到顯示 raw namespace 並標「依 namespace 推斷」） | 記憶體內字串比對 |
| **B** | `ModList.get().getMods()` → `IModInfo.getDescription()/getDisplayName()`（KB 級；**唔加權**） | 一次遍歷 ~231 mod |
| ~~C~~ | ~~tooltip~~ | **唔做**（thread 不安全） |

- 排序 deterministic：完整片語 > 多詞同現 > 單詞；同分按 mod id／label 字母序（**唔准**靠 HashMap 次序）。
- 輸出：**N ≤ 8** 個候選，每個帶**非空**證據片段（≤120 字）＋來源標記；明寫「**候選（未確認）**」；**零 raw id**（`namespace:path`／mod id／translation key `tile.*.*` 一律唔准出玩家可見文字）。
- 零命中 → **唔注入任何嘢**（保持今日行為，唔會退化）。

### 2.3 注入點（**唯二落地位**）

1. `AskEngine` prompt 組裝：**只喺 gate 命中**時，把候選事實插入 **FACT 區之前**（教訓：`AskEngine` early return 早過 FACT 區，新資料要放前面）。
2. 文案：3 語 lang key（`zh_cn`／`zh_tw`／`en_us`）＋一條 `packai.reply.capability_candidates` 說明句（「以下係包內候選（未確認），唔可以當結論」）。

**唔准郁**：卡落位、`AskReplyScrub`、`HonestMiss` 文案、`AskToolLoop` 三張名單、tool schema、`neoforge` 樹。

### 2.4 誠實限制（寫入 plan 同玩家可見文案）

runtime 冇 bytecode 層 ⇒ 幾何／具體機器功能撈唔到；撈唔到時答「未確認」，**唔准**講「呢個包冇」。

## 3. 驗收標準（先寫死）

- **V1** `compileJava compileTestJava` RC=0；現有 harness 全綠。
- **V2** 新增 `tests/check_capability_candidates.py`：① 同輸入兩次 byte-identical；② 零命中 → 空；③ 候選一定有非空證據；④ **零 raw id**（`namespace:path`／mod id／`tile.*.*`）；⑤ N 上限夾得住；⑥ **普通題目（gate 唔中）→ 注入字串為空**（零固定成本嘅回歸鎖）。
- **V3** 全部 `tests/check_*.py` 冇新增紅（baseline 以**當日實跑**為準）。
- **V4** **意圖閘量測**：用真問題語料量 recall（能力題要中）＋誤觸率；報告出實數，唔准拍腦。
- **V5** **真機（`packai_sandbox`，NFWC 231 mod）**：真值**只用沙盒內真存在嘅嘢**（例：`refinedstorage-1.11.7.jar` 存在 ⇒「自動合成」類題目候選應包含 refined storage 物品）；另加 2 條預期撈唔到嘅題目，要求**誠實答未確認**。同一批題目**另跑一次無注入對照**，證明注入真係改變答案（negative control）。**有 log 原文。**
- **V6** `git status` 只准預期檔；`neoforge/` 零改動。

## 4. 風險／還原

- 風險：**低**（唔加 tool、唔改 prompt 主體、唔改 tool 名單；只加一個 gate＋一個注入塊＋文案）。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\` 還原。
- 唔准：hot-copy jar、真 instance 自動部署、`git add -A`。

## 5. 執行

- 實作＝**cursor-agent**；Hermes 只做 plan／派工／親驗。
