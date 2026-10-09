# Plan v4：落點 B — 能力反查（按需注入候選；**注入面已換成 capable 模式都送嘅新 user key**）

- 比分史：v1 **3:7**（S2 前提假）→ v2 **3:7**（tooltip thread／tool 不可達／ROI）→ v3 **4:6**（**注入面 capability 模式下被清空**、ItemIndex 冇枚舉 API、驗收不可行）。
- 三輪同一族病灶：**機制可達性**（設計好咗但喺 live 配置下係 no-op）。v4 逐條對住 flip condition 修。

## 〇、R3 逐條裁決（全部採納）

| # | 指控（證據） | v4 修法 |
|---|---|---|
| **A2（high，最致命）** | FACT 區＝`user.graphFacts`；但 `capable` 時 `promptFacts = List.of()`（`AskEngine.java:829`，`capableForTools()`＝`LlmClient.toolsOffered(lastBase)`，`lastBase=""`→true）⇒ 候選寫入 facts **送唔到**；`tests/check_ask_capable_slim.py:41` 明證 | **改注入面**：喺 `LlmClient.completeRound` 嘅 `user` JSON 加一個**新 key `capability`**（同 `jei` 並排，`LlmClient.java:440` 一帶）。user payload **兩種模式都送**（已親核：`jeiForLlmSlim` 喺 capable 都傳，`AskEngine.java:832`）。gate 唔中 → key 唔加 ⇒ 零固定成本 |
| A1（med） | `ItemIndex` 只有 `isReady/invalidate/ensureAsync/ensureAsync(Path)/searchReady`（:57/62/69/111）；`entries` private，**冇枚舉 accessor**；`searchReady(queryNorm,limit)` 回 `ItemSearch.Hit(stack,id,label)`，cap `DEFAULT_LIMIT=10`。「12,708」係 NFWC＋該語言嘅實測值唔係常數 | **改用 `searchReady`**（唔自己排名，做唔到就唔做）；「12,708」改成「per-pack／per-lang 實測（NFWC＝12,708）」 |
| A3（med） | FACT 區組裝喺 `AskEngine.java:436` 之後；之前有 `:276-284`（offline＋questHits）、`:377-399`（skipLlm）、`:424-428`（highConfidence）return；`AskService.java:311` token 封頂 | 候選**同 `jei` 同一位置**組裝（兩條 bridge：`askNoTools:816-822`／`completeWithTools:826-833`）⇒ 同 `jei` 一樣係「有就送」；**唔靠**任何 guard；誠實承認：`:276/:424`/token 封頂 三條路徑**唔會有候選**（列出嚟，唔當成功） |
| A4（med） | 若「候選係未確認」說明句寫入 `llm_style`／`fact_check` ⇒ 每次 ask 固定加字 | **說明句只喺注入塊內**；**唔改** style／fact_check；新 key 唔經 `Plainify`／`factCap` |
| A5（med） | 冇標好嘅「能力題」語料；589 條係 SE keybind 語料、positives 作者自撰、filter 排除 mods | V4 降級為：589 語料量**誤觸率**＋自撰正例量 recall，**明文聲明限制**（非獨立樣本） |
| A6（med） | 無注入對照喺現 harness 做唔到（`AutoTestHarness` 冇 per-run 開關） | 加 client config key **`capabilityCandidates`**（默認 on）⇒ **同一支 jar** 跑 on／off 兩次做 negative control |
| A7（med） | 可能有第 8–11 個落地位（section tag／label parity／`check_dual_tree_diff_symmetry`／`check_reply_prompt_keys`）；V2 係 runtime 性質，Python source-lock 驗唔到 | **唔引入任何新 section tag、唔改任何 system prompt 文案、唔改 lang** ⇒ (a)(b)(c) 全部唔適用；V2 改成「純函數 Python mirror」＋真機 trace 驗可達（§3） |

## 1. 目標與誠實預期

**目標**：玩家問「包入面邊樣嘢可以做到 X」（唔給 item id）時，mod 由**包內真實資料**（物品顯示名候選＋mod 描述）抽候選，**注入 prompt**，誠實標「候選（未確認）」。

**成本原則（回應 SK 投訴）**：唔加 tool、唔加固定 payload；gate 唔中 → key 唔加 → **普通題目零額外 token**。

**誠實預期**：文字來源天花板約 2/5；幾何／具體機器**預期撈唔到** → 答「未確認」。**<2/5 唔算完成。**

## 2. 設計

### 2.1 意圖閘 `PackIndex.isCapabilityQuestion(question)`

- 新 predicate，照 `isAcquireOrientedQuestion`（:843-881）等現有寫法。
- v4 係**加料**（唔短路）⇒ 誤觸只係多幾行字；**誤殺**才係失效 ⇒ V4 以 recall 為主，兼量誤觸率。

### 2.2 候選檢索 `logic/CapabilityCandidates.java`（forge-only）

| 來源 | 做法 | 成本 |
|---|---|---|
| **A** | **`ItemIndex.INSTANCE.searchReady(queryNorm, limit)`**（唯一公開檢索面）→ 命中物品／label → namespace→`ModList.getModContainerById` 反查 mod（查唔到顯示 raw namespace ＋標「依 namespace 推斷」） | 記憶體內 |
| **B** | `ModList.get().getMods()` → `IModInfo.getDescription()/getDisplayName()`（KB 級、唔加權） | 一次遍歷 ~231 mod |
| ~~C~~ | ~~tooltip~~ | **唔做**（worker thread 唔安全） |

- 合併排序 deterministic：片語 > 多詞 > 單詞；同分按 label／mod id 字母序（**唔准**靠 HashMap 次序）。
- **N ≤ 8**；每個候選必須有**非空**證據片段（≤120 字）。
- **零 raw id**：`namespace:path`、mod id、`tile.*.*` 一律唔准出。
- 零命中 → 唔注入。

### 2.3 注入面（親核過嘅可達面）

- 新 user key **`capability`**，加喺 `LlmClient.completeRound` 嘅 `user`（`LlmClient.java:440` 一帶，`jei` 隔籬）；`user` 會 `GSON.toJson` 入 `messages[user]`（`:494-497`）。
- 由兩條 bridge 傳入（`askNoTools`／`completeWithTools`），同 `jeiForLlmSlim()` 同層（`:820`／`:832`）。
- 內容（注入時）＝標題句（「以下係包內候選（未確認），唔可以當結論」）＋候選行；**用 zh／en 兩版（跟 `langCode`，同 `sources` key 一樣喺 code 內 literal，唔入 lang 檔）**。
- gate 唔中 → 唔加 key。

### 2.4 落地位（7 項，全部要親核）

1. `LlmClient` user payload 加 `capability` key（+ 方法簽名多一個參數）。
2. 新檔 `logic/CapabilityCandidates.java`（forge-only；`neoforge` 唔准改 ⇒ dual-tree 只 WARN）。
3. `PackIndex.isCapabilityQuestion(...)`。
4. `AskEngine` 兩條 bridge 計候選（`askNoTools:816-822`、`completeWithTools:826-833`）——**唔郁** guard（`:276/:377/:424`）。
5. `PackAiConfig` 加 `capabilityCandidates`（默認 true）＋ client toml mirror＝negative control 開關。
6. `tests/check_ask_capable_slim.py` mirror 更新（payload key 集）。
7. `tests/check_dual_tree_sync.py` `TREE_SPECIFIC` 加新檔。

**唔准郁**：卡落位、`AskReplyScrub`、`INTERNAL_SECTION_TOKENS`、`llm_style`／`fact_check`、lang 檔、tool 三張名單、`neoforge` 樹。

### 2.5 已知唔會有候選嘅路徑（誠實列出，唔當成功）

`:276-284` offline＋questHits；`:424-428` highConfidence 本地回；`AskService:311-316` token 封頂。

## 3. 驗收標準

- **V1** `compileJava compileTestJava` RC=0；現有 harness 全綠。
- **V2** 新 `tests/check_capability_candidates.py`（純函數 mirror）：① 同輸入兩次 byte-identical；② 零命中 → 空；③ 候選必有非空證據；④ **零 raw id**（`namespace:path`／mod id／`tile.*.*`）；⑤ N ≤ 8；⑥ **普通題（gate 唔中）→ 注入字串為空**。
- **V3** 全部 `tests/check_*.py` 冇新增紅（baseline 以**當日實跑**為準）。
- **V4** 意圖閘量測：589 條語料量**誤觸率**；自撰能力正例量 recall，**明文聲明「正例作者自撰、非獨立樣本」**。出實數。
- **V5** **真機（`packai_sandbox`，NFWC 231 mod，`refinedstorage-1.11.7.jar` 真存在）**：① 能力題候選要包含 refined storage 相關物品；② 加 2 條預期撈唔到嘅題目要答「未確認」；③ **negative control＝同一支 jar，`capabilityCandidates` on／off 各跑一次**，貼 trace 內 `capability` key 有／無，同答案差異。全部要有 log／trace 原文。
- **V6** `git status` 只准預期檔；`neoforge/` 零改動。

## 4. 風險／還原

- 風險：**低-中**（唔加 tool、唔改 system prompt、唔改 lang、唔改 scrub；注入 key 唔中＝唔存在）。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\` 還原。
- 唔准：hot-copy jar、真 instance 自動部署、`git add -A`。

## 5. 執行

- 實作＝**cursor-agent**；Hermes 只做 plan／派工／親驗。

---

## 6. 【停手報告】四輪 review 未達 8:2 + 親手量度 ⇒ **決定唔做**（2026-10-10 07:0x）

### ① 逐輪比分

| 輪 | 比分 | 打中嘅位 |
|---|---|---|
| R1 | 3 : 7 | S2 前提假（runtime 冇 mod lang） |
| R2 | 3 : 7 | tooltip thread 不安全；新 tool 不可達；ROI |
| R3 | 4 : 6 | 注入面喺 capable 模式被清空；ItemIndex 冇枚舉 API |
| R4 | **5 : 5** | 檢索面：`searchReady` 係「整條 query 子串」，能力題（動詞＋目標）永遠唔會係物品名（名詞）嘅子串 ⇒ 又係 no-op |

四輪都係**同一族病灶：機制可達性**（設計好咗，但 live 配置／資料形狀令佢變 no-op）。依 SK「Plan review 上限 3–4 輪」規則，**停手**。

### ② 卡死嘅載重決定

- **資料形狀對唔上**：能力題係「動詞＋目標」問句；runtime 平價來源係**名詞標籤**（物品顯示名）＋推銷式 mod 描述。中間冇任何 runtime API 可以「由需求搵物品」。

### ③ 最貴嘅未知（已親手解開，唔再係未知）

`tools/probe_capability_text_hits.py`（離線、零改碼、唔開遊戲）量 **packai_sandbox / NFWC（231 jar）**：
- **9,385 條 zh_cn item/block 顯示名**；**只有 39／231 個 mod 有描述**。
- 14 條 realistic 能力需求（token 子串命中；**呢個已經係對設計最有利嘅 case**）：

| 題 | 結果 |
|---|---|
| 自動合成 | ✅ **create 动力合成器(11)、refinedstorage 合成终端(10)** |
| 無線傳電 | ⚠️ 弱中（createaddition「输出 FE 能量」，1 條） |
| 儲存不可堆疊 | ❌ 全雜訊（sophisticatedbackpacks「能够让背包捡起物品」） |
| 清 100×90 / 白色混凝土 | ❌ 全雜訊（白色混凝土 → **白色骏鹰**；刷怪塔 → **刷怪蛋**） |
| 自動農場／自動挖礦／抽水／分類／傳送／釣魚／飛行裝備 | ❌ **零命中** |
| 儲存大量物品 | ⚠️ 1 條弱命中 |

⇒ **約 2/14 有用，其餘零命中或純雜訊**。而且注入雜訊會**主動拉低答案質素**（例：問白色混凝土，候選卻係白色骏鹰）。

### ④ 建議（本工程師決定，唔開第 5 輪）

**唔做落點 B（runtime 文字能力反查）**。理由：實測命中約 2/14，且 false positive 主導 ⇒ 唔止無效，係有害。
- 「包內邊樣嘢做得到 X」呢個能力，**證據支持嘅路線係落點 A（離線索引，已完成）**或行為測試，唔係 runtime 文字搜。
- 保留 artifact：`tools/probe_capability_text_hits.py`（日後任何「文字檢索」設計前，先跑佢驗命中率）。
- 次序改為：**A（完成）→ 成本地基 → CI → harness → 農場／自動化 → 上架**。
