# Plan v4：成本地基 — **縮到只做「量度」（Slice A）**；改 prompt（Slice B）暫緩待數據

- 觸發：SK「而家噉樣做消耗太多 token，而且我相信有更好嘅方法」。
- **v4 係縮範圍版**：v3（含 Design B 改 prompt）嘅**核心前提被我自己嘅實測推翻**（§0.3），所以 **Design B 唔落**；v4 只做「量度真機 cache 命中率」。
- Review 史：R1 5.5:4.5 → R2 4.5:5.5 → **R3 4:6（反方反勝，已用盡 3–4 輪上限）** ⇒ 依 SK 規則停手，改行縮範圍路線（本 v4）。

## 0.3 ⚠️ 撤回：「82% 白付／整段 14.5k miss」係錯（三項親手實測）

| 實驗 | 結果 | 意思 |
|---|---|---|
| 兩個主線 system 逐字元比對（`%TEMP%\verify_diff_index_20261010.py`） | **首個差異 index＝14,539／14,590 ⇒ 99.63% 前綴相同**；差異只在最後 ~51 字元（正正係 `rules` 尾句） | 兩個變體唔係「前後都唔同」，而係**只有尾巴**分別 |
| 冷啟尾句變化 probe（`%TEMP%\tail_variation_cost_20261010.py`，nonce 保證冷啟） | cold 0/12,624 → warm hit **12,416**／miss 209 → **換尾句 hit 11,520／miss 1,109** → 轉返舊尾句 hit 12,416／miss 207 | **DeepSeek 係「最長共同前綴」命中**：尾句一變，只 miss 由該處起（尾句＋tools＋user ≈ **900 tok**），前面 12.4k 照 hit |
| tools 變化 probe（§0.1） | 換 15 個 tool schema，hit 只跌 896 | 同上，證實部分前綴命中 |

⇒ **撤回兩句講過嘅話**：① 「每轉一次 rules 分支就整段 14.5k miss」——錯，實際 ≈**900 tok／次（≈7% of 12.6k）**；② 「82% 係白付」——錯，**82.4% 係「重送比例」，唔等於 miss 比例**；由於主要係 cache-hit 價（hit/miss 差 50×），嗰 82% 大部分**已經**係便宜嗰邊。當時把「重送」直接當「白付」係推論跳步。

**仍然成立**：① 三個 cache 欄位存在、非串流都讀得到（設計 A 做得到）；② tools 喺 cache prefix 內；③ 現行 13 條 trace **零 usage 記錄** ⇒ 真機命中率**目前無人知**。

**收窄後嘅真未知（唯一要解）**：真機（沙盒、真 packai body）每 request 嘅 **system＋tools 命中率**係幾多？→ 若高（實測 probe 類比 ≈ 96%），成本問題唔喺 prompt 結構，唔值得為佢改 prompt；若低（例如 `offered` 由 400 翻 notools，令 `factCheck` 喺 system 頭 ~80 字元就變 ⇒ 真「整段 miss」），才處理——**而正確做法係修 `offered` 嘅早期不穩，唔係搬 `rules` 尾句**。

## 0.4 R3 逐條裁決（全部採納；行號已 grep 核）

| # | 指控 | v4 處置 |
|---|---|---|
| A1（high） | 「整段 14.5k miss」量化錯（實際 99.63% 前綴相同、只尾段 miss） | **撤回並改正**（§0.3）；Design B 因此**唔落** |
| A2（high） | 設計 B 打錯靶：真正會「整段 miss」嘅係 `offered`→`factCheck`（system 第 ~80 字元位就變，`LlmClient:478-481`＋`:541-545`） | 寫入 §0.3 真未知；**Design B 取消**；如日後要修，目標係早期段（`factCheck`／`style`），唔係尾段 `rules` |
| A3（high） | hop tag 喺 `LlmClient` 內做唔到（`chatOnce` public、只有 2 個 inline caller、冇 caller context），且同「唔准郁 method 簽名」矛盾 | **改設計**：唔加 tag、唔改簽名 —— 用 **prompt token 數 + system 首段 sha8** 喺 log 行自己分類（零耦合） |
| A4（high） | V2 按 plan 寫根本跑唔到（要求 `cache[main]` tag）；其實長度＋sha 已夠硬 | 承 A3：V2 改為「**只認 prompt≥10k 且 system-sha8＝主線值**嘅行」 |
| A5（med） | Slice A 唔係「零行為改動」；`AskTraceCheck` 其實**唔會**紅（佢直接驅動 `AskTrace`，唔經 `finishAskTrace`） | §6 改措辭為「**唔改 prompt／唔改 request／唔改回應行為**」；§3#6 由「必要」改成「**主動加固（可選）**」 |
| A6（med） | 「側線固定 278」錯：body-repair = `BODY_REPAIR_SYSTEM`(~93 字)＋**隨卡數變嘅 digest**，可能 >1000 | 分類改用 token 數＋sha（唔靠長度閾值）；實測 13 條側線 278 只代表 classify hop（body-repair 從未 fire）——已寫入 |
| A7（med） | V4 語義抽查唔客觀；repo 已有機械閘未用 | 改用**現成機械閘**（`tests/check_reply_structure_scrub.py`／`check_reply_sources_header.py`／`check_reply_lang.py`／`check_ask_marker_integrity.py`）對固定題庫跑；人手抽查降為輔助 |
| A8（med） | §0.1 唔可 clean 重現（cache 殘留） | 新 script 用 **nonce 冷啟**（`tail_variation_cost_20261010.py` 已示範）；§0.1／§0.3 全部標明「需冷啟」＋附腳本路徑 |
| A9（med） | 13 條 trace 同日同 session，2 個 sha 樣本窄 | 列為**已知限制**；Slice A 落機後即累積新數據 |
| A10（med） | 用 probe ≈99% 做 V2 期望值係跨情境套用 | V2 只量 **system＋tools 段**命中率，唔計 user／history，並設合理閾值 |
| A11（low） | `finishAskTrace` 係 :506 唔係 :505 | 已改 |
| A12（low） | 新 check #8 覆蓋窄 | 擴到核 `plus()`／`NONE`／fallback；Java test 為準 |

**R3 反方亦確認為真嘅（我照收）**：§0.1③ tools 入 prefix、§0.2 兩個 sha、13 條 trace 零 usage、Python baseline 127 檔 1 紅、8 個 `new TokenUsage(`、`neoforge` 有孖生檔、grep 全 repo **冇**任何測試硬依賴「rules 必喺 system」。

## 1. 現況（親核，不變）

- `logic/TokenUsage.java:20-29` `fromResponse` 只讀 3 欄（`:26-28`）⇒ 13 條 trace 零 usage。
- `logic/LlmClient.java:478-481` system 拼接、`:379-380` 逐題 `style`／`rules`。
- Provider：`PackAiConfig:273-281` 預設 OpenAI；**8 個 instance** 實測全指 `https://api.deepseek.com`。fallback 只補 hit ⇒ miss＝`prompt_tokens − cache_hit`。

## 2. 只做一件事：Design A（量度）

- `logic/TokenUsage.java`（record，:12）：加 `promptCacheHitTokens`／`promptCacheMissTokens`（`-1`＝未知）；`fromResponse` 讀兩欄＋OpenAI fallback（`prompt_tokens_details.cached_tokens`）；`plus()`（簽名 :60，建構 :64-66）同步加總；`NONE`(:13) 一齊改。
- `logic/LlmClient.java` 兩解析點（`:229-233` chatOnce／`:552-557` completeRound）各 log 一行，**自帶分類資訊**：`Pack AI cache hit=<n> miss=<n> prompt=<n> sysLen=<chars> sysSha8=<sha8>` ⇒ 憑 `prompt`＋`sysSha8` 就分得出主線／classify／body-repair（唔需要 caller context）。**唔改任何 method 簽名。**
- `client/service/AskService.java`：usage log **:607-612** ＋ usage_missing 分支 **:598-604** 加 cache 欄。
- **新**：`AskTrace` usage 事件（hit／miss／prompt／round），落點＝**`AskService.finishAskTrace:506`**（`close()` :544 之前），**只落主線**；**唔准喺 `AskTrace.close()` 內自動附加**。
- 測試：#5 `DailyTokenUsageCheck.java` 5 處 `new TokenUsage(...)`（:23/24/26/28/30）補參數；#6 `AskTraceCheck.java` 為**可選加固**（佢唔經 `finishAskTrace`，唔會自動紅）；#7 `tests/check_token_usage.py` mirror 3→5 欄；#8 新 `tests/check_token_cache_fields.py`（核 `fromResponse`／`plus`／`NONE`／fallback）＋**負控**（抽走一行必須變紅）。

**dual-tree**：`TokenUsage.java`／`LlmClient.java` 兩樹都有 ⇒ forge-only 改動＝byte-drift ⇒ paused 下 **WARN、RC 0**（預期）；**唔郁 gate**。

## 3. 驗收標準（Slice A）

- **V1** `compileJava compileTestJava` RC=0；harness 全綠。
- **V2（可執行版）** 沙盒真機連問 **3 條** → 由 log 取出 **prompt≥10k 且 sysSha8 一致**嘅行：報告 **system＋tools 段命中率 = hit/(hit+miss−(user 部分))**，並貼原始行。**唔用 probe 嘅 99% 做門檻**；門檻＝「量到數、定義一致、可重現」。
- **V3** `tests/check_*.py` 冇新增紅（baseline 實跑 127 檔、1 已知紅）；新 check #8 負控要做。
- **V4** trace 新事件格式正確（貼一條真 trace 行）；`AskTraceCheck`／`DailyTokenUsageCheck` 綠。
- **V5** `git status` 只准預期檔（改動前記 HEAD＋status）；`neoforge/` 零改動。

## 4. Slice B（改 prompt）＝**暫緩，附開啟條件**

滿足**全部**才開：① Slice A 顯示 system＋tools 命中率**明顯偏低**（唔係 90% 以上）；② 低命中嘅成因已用 trace 定位到早期段（`factCheck`／`style`／`offered` 變體），而唔係尾段 `rules`；③ 有具體修法只郁嗰段、唔動尾段無事嘅部分。否則成本問題唔喺 prompt 結構，應轉去減回合／減 payload。

## 5. 風險／還原

- 風險：低（只加欄位／log／trace 事件；唔改 prompt、唔改 request、唔改回應）。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\`（部署只准 `mc_mod_deploy_jar.py --target packai`，要關 java）。
- ⚠️ jar guard cron 自 2026-09-15 20:25 paused ⇒ 部署／還原人手核 sha256。

## 6. 執行

- 實作＝**cursor-agent**（唔准 commit／唔准 hot-copy jar／只改 §2 範圍）；Hermes 親驗（compile／harness／checks／diff）。
- 完成後兩輪 code review（pass1 重構／pass2 三個月後脆弱位）。
