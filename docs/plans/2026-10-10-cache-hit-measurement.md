# Plan v3：答「我哋有冇白白重複付錢」——先量度、後（如需）落 log

- v1（`eafa62a`，3 檔加 log）→ R1 反方 **6:4**（3 條硬傷）。
- v2（零改碼 proxy 主線）→ R2 反方 **6.5:3.5**（proxy attribution 冇解、V1 定義上做唔到、成本／基準有錯）。
- v3＝修 R2 全部 must-fix，**加 M0（最平一步）**，attribution 改用 proxy 自身可算嘅特徵。

## 〇、R2 review 逐條裁決

| # | 反方指控 | 裁決 |
|---|---|---|
| ① | M1「http 唔會被擋」只係未被反證，plan 冇核證 | **採納**：`normalizeApiBaseUrl`（`LlmClient:894-916`）只 trim／剝 `/chat/completions`，**冇 https 強制、冇 scheme validator**；用 `java.net.http.HttpClient`（`:32`）；`URI.create(base+"/chat/completions")`（`:527`）⇒ `http://127.0.0.1:8080` 會被老實使用。**v3 已親核**（md5／grep 見 §六） |
| ② | proxy **認唔到 request 屬邊個 ask／邊輪**（三個 `LlmClient` 實例打同一 base，body 冇 ask_id）⇒ §三 兩個覆蓋率計唔到、V2 分母被污染 | **採納並修**：三個實例嘅 **system prompt 各不相同**（主 ask＝大 prompt；意圖分類＝`INTENT_CLASSIFY_SYSTEM`；body 修復＝`BODY_REPAIR_SYSTEM+digest`）⇒ **用 `sha1(system)` 分類**就分得開；ask 邊界用 **`messages` 數目 reset** 偵測。**唔需要** cross-correlate trace |
| ③ | V1 定義上做唔到：trace 有損（tools 掉 `type`／`description`、`maskSecrets` 將 `sk-*`／**所有 UUID** 換 `***`）⇒ body hash 永遠唔等 | **採納**：**刪 V1**（改為 §四 V1′） |
| ④ | §三 冇寫分母；「免費」係錯（hit 仍收費）；tools 係否入 cache prefix 未證 | **採納**：分母寫死 `Σhit/Σ(hit+miss)`；改講「**平 10–50×**（附來源日期）」；tools 入唔入 prefix ＝**本次量度要答嘅未知** |
| ⑤ | M1 一定要跑遊戲，而部機**而家有 java 佔住**；plan 冇 fallback | **採納**：實測 `javaw.exe`（~10.3 GB）＋ `prismlauncher.exe` 在跑 ⇒ M1 **要等實例收工**；v3 寫明 fallback（§二） |
| ⑥ | M2 replay 唔夠真（tools 冇 description）＋ key 來源未寫 | **採納**：tools 由 code 內 `nativeToolsSchema` 重建；key 由 **env 讀**、唔落 script／log；重組只算**近似**，數字要標誤差 |
| ⑦ | **有更簡單方法：`platform.deepseek.com/usage` 可匯出 CSV，本身已含 cached token** ⇒ plan 首選最重嘅 M1 係 over-engineering | **採納**：列為 **M0**（要 SK 登入匯出）；但 M0 只有**日彙總**，答唔到 per-round／跨 ask 分佈 ⇒ M1 仍有價值，只係次序改為 M0→M1 |
| ⑧ | config 有被 `SPEC.save()` 蓋嘅風險；冇寫「改前確認無 java、改完 re-read 驗值」 | **採納**：寫入 §五 |
| ⑨ | baseline 唔對數（AGENTS 舊記 110/110 vs 今日 127/126+1 紅） | **採納**：§四 V4 寫明「以今日實跑為準」＋舊數字出處 |
| ⑩ | §〇③ 引述錯：`AskService` **唔止**「冇 LlmClient 實例」——佢有兩處 inline `new LlmClient()`；而 grep `AskEngine\.` ＝ **4**（`:323/:653/:2551/:2963`），唔係 0 | **採納（我自己寫錯，收返）**：正確講法＝「`AskService` 冇 `LlmClient` **field**（主 ask 經 `AskEngine.INSTANCE`），但**有 2 個 inline hop**」。下游結論（usage 經 `AskResult` record 穿）不變 |

**R2 比分：正方 6.5 : 反方 3.5**（仍 <8:2 ⇒ 唔可以開工，要再修再 review）。

**另外 R2 數字核實捉到嘅錯（全部由我採納）**：價錢引用要附**官方頁＋查證日**（我原文 $0.0028／$0.14／$0.28 出自 `deepseek.com/en/platform`；`api-docs.deepseek.com/quick_start/pricing` 另一組，比率同為 **50×**）；M1 唔可以寫「US$0」；M2 上界要擴到 ~US$0.045；dual-tree 註記要加 `LlmClient.java`。

## 一、v2 錯喺邊（一句話）

**v2 揀咗正確方向（零改碼量度），但冇解「proxy 點知邊個 request 屬邊個 ask／邊輪」，又寫咗一條（trace hash 比對）根本做唔到嘅驗收，令交付物攞唔到手。**

## 二、v3 設計

### M0（最平、零工程；要 SK 一次登入）

- SK 喺 `platform.deepseek.com/usage` 匯出 CSV（欄含 input／output／**cached**）。⇒ 直接有**整體** cache 命中率。
- 限制（要講明）：只有日彙總，**分唔到**「同 ask 多輪 vs 跨 ask」，亦混雜 Hermes 自己嘅用量 ⇒ **只可以做第一個 sanity check，唔可以當結論**。

### M1（主線；零 product code、零 build、零換 jar）

- 沙盒 instance（`packai_sandbox`）嘅 `config/packai-client.toml` 將 `apiBaseUrl` 指去 `http://127.0.0.1:<port>`（`http` 已核可，見 §〇①）。
- 細 Python 反向代理：**原樣轉發** request、**原樣回** response，只落一行 JSON：
  `{ts, sha1(body.system), messages_len, sha1(body.messages[1..]) 截斷, prompt_tokens, cache_hit, cache_miss}`。
- **唔需要 trace**：分類靠 `sha1(system)`（三個實例天然分開）；ask 邊界靠 `messages_len` reset。
- 跑 **連續 3–5 條 ask**（同一 session，正常用語）⇒ 讀 log。
- ⚠️ 前置：**遊戲要跑得到**且唔可以同時有其他實例佔住（實測現時有 `javaw.exe` ~10.3 GB）⇒ 要等 SK 收機／沙盒可用。
- 成本：**唔係 US$0** —— 3–5 條 ask ≈ **US$0.02–0.05**（用實測均值 62,818 tok/ask）。

### M2（後備，唔需要遊戲）

- 由真 trace 重組 request（`send.system`／`send.history`／`send.user`），**tools 由 code 內 `nativeToolsSchema` 重建**（trace 冇 description）；key 由 **env** 讀。
- 連發 2–3 次同 prefix ⇒ 直接答「同一個固定 prefix 第二次有冇中」。
- 誠實限制：body 只係**近似**（UUID／`sk-` 被 mask 過嘅位唔會還原）⇒ 數字要標 ±誤差。
- 成本：≈**US$0.01–0.045**（要 SK 批，因為係付費動作）。

### P（只有 SK 話要「長期每次 ask 都記錄」才做）

- 只加**每回合一行** log 落 `LlmClient:229/:553`；若做累計，必須喺 `resetUsageAccumulator()`（`:47-49`）一齊清零；**唔改 `TokenUsage`／`AskResult` record 簽名**；加永久 harness check（`src/test` 風格同 `DailyTokenUsageCheck` 並列）。
- dual-tree：`TokenUsage.java` **同** `LlmClient.java` 都**唔喺** ALLOWLIST，兩樹現時 `TokenUsage.java` byte 相同（md5 `f4054f09…`）⇒ 只改 forge 會出非 allowlist drift；paused 下＝**WARN＋exit 0**（唔會 FAIL），`--no-paused` 先 FAIL。

## 三、量度定義（回應 R2 ④）

- **冷啟動唔算白付**：同一 prefix unit 首次出現必然 miss（官方機制）。
- **覆蓋率定義（寫死）**：`Σ prompt_cache_hit_tokens / Σ (prompt_cache_hit_tokens + prompt_cache_miss_tokens)`，**只計主類 request**（即 `sha1(system)` ＝ 主 ask 大 prompt 嗰組），並且**每一個 ask 嘅第 1 個 request 剔除**（冷啟動）。
- **兩個視角**：(a) 所有主類 request；(b) 逐 ask 內第 2..N 個（＝「每輪重送」係否已被 cache 食掉）。
- **決策規則**：
  - 覆蓋率 **≥80%** ⇒「重複 payload 已經**大致**由 cache 食掉（仍收 hit 價，唔係免費）」⇒ **唔值得**改 prompt／輪數。
  - **<80%** ⇒ 下一步做 prompt 瘦身（縮 system prompt／按需掛 tool schema／減輪數），再量。
  - **恆等 0** ⇒ 先查 prefix 穩定性（時間戳／隨機排序／動態 facts）：即係 prefix 唔穩，唔係平台唔 cache。

## 四、驗收標準（v3）

- **V1′**（取代 v1／v2 嘅 body-hash 比對）proxy 記錄嘅 request 數 ≥ 沙盒執行嘅 ask 數；**主類 `sha1(system)` 只有一個值**（若多過一個 ⇒ 假設錯，即刻停）。
- **V2** log 每個 LLM round 一行，含 `prompt_cache_hit_tokens`／`prompt_cache_miss_tokens`／`prompt_tokens`；分母由 §三 定義決定，**唔用 log 行數**。
- **V3** 連續 3–5 條 ask 都有數據；報告分開「ask 內第 2..N」同「跨 ask」。
- **V4** 對照閘：`python tests/check_*.py` **今日實跑** baseline（2026-10-10 實測：**127 檔／126 pass／1 紅 ＝ `check_ask_display_leak`，需真機 latest.log**）。⚠️ 舊文件（packai `AGENTS.md` Gotchas）記「2026-09-15：110/110 綠」——**已過時**（該日之後新增 17 個 check 檔）；判 regress 一律以今日實跑逐檔清單為準。
- **V5** 階段 M 全程**唔准**改 `forge`／`neoforge` 任何 source ⇒ `git status` 唔應該有 source 改動。

## 五、風險／還原

- 改 `packai-client.toml` 前：**確認冇 java 進程**（`javaw.exe`）；改完 **re-read toml 用第二個獨立讀法驗真係 `http://127.0.0.1`**（因為 `PackAiConfig` 每個 setter 尾 `SPEC.save()`，遊戲在跑時任何 settings 操作會用記憶體值重寫 toml）。
- 備份 `packai-client.toml`（檔名含日期時間）→ 做完**即刻還原** → 刪 proxy 腳本。
- secret：API key 只喺 proxy **記憶體**轉發；**唔准**寫入 log／腳本（M2 由 env 讀）。
- 只喺沙盒做，**唔准**掂真 instance `AI_test_NFWC_DIM`。
- product code 完全未動 ⇒ 零回滾需求。

## 六、成本

| 階段 | 成本 | 要 SK 批？ |
|---|---|---|
| M0（平台 CSV） | US$0 | 要 SK 登入一次 |
| M1（proxy） | ≈**US$0.02–0.05**（ask token 費） | 唔使（等沙盒可用） |
| M2（replay） | ≈**US$0.01–0.045** | **要** |
| P（永久 log） | US$0（唔加 request） | 要（product code） |

---

## 七、停手報告（SK 規則 2026-09-13：第 3–4 輪仍未達 8:2 → 停手問 SK）

### ① 逐輪比分

| 輪 | 比分 | 主要打中嘅位 |
|---|---|---|
| R1 | 正方 **6** : 反方 **4** | v1 用改 product code 答一次性問題；「兩個解析點覆蓋全部」錯；要累計就要改 `AskResult` record |
| R2 | 正方 **6.5** : 反方 **3.5** | proxy 認唔到 request 屬邊個 ask／輪；V1（trace body hash 比對）定義上做唔到；成本寫 US$0 誤導 |
| R3 | 正方 **5.5** : 反方 **4.5** | v3 兩個**新**載重假設都冇 code 保證（見下） |

### ② 卡死嘅載重決定（唔解決就量唔準）

1. **「`sha1(system prompt)` 認得主類」唔成立**：主 ask 嘅 system ＝ `llmSystemLead + factCheck + PackAuthorAgents.systemAddon + style + rules`；其中 `rules = llmRules(lang, questOverride, questConflict, policy)` **有 5 個分支**，`style` 亦含 `RecipeCardsMode.current()`，而 `questOverride`／`qConflict`／`policy` **全部 per-question**（`LlmClient:478-481`；`ReplyLang:1310-1323`／`:1216-1236`；`AskEngine:259`／`:272`／`:296-303`）⇒ 主類會出現**多過一個** hash。
2. **「`messages` 數目 reset ＝ ask 邊界」唔可靠**：GUI 傳 `ChatSession.recentForLlm()`（最近 8 條，history **累積唔清零**），所以單 round ask 之間長度**唔會跌**（`ChatSession:353-363`；`PackAiConfig historyTurns=8`；`LlmClient:483-504`）。
3. **「冷啟動剔走每個 ask 第 1 個 request」會系統性低估**：官方 cache 係 **prefix-unit** 制，跨 ask 嘅第 1 個 request 其實**部分命中**（`api-docs.deepseek.com/guides/kv_cache` Example 2）。

### ③ 最貴嘅未知

- 主 prompt 喺**一次真實 session 內**實際會唔會變（rules 分支會唔會真係跳）——只有實跑數據答得到。
- **tools 陣列到底入唔入 cache prefix**——官方文件冇講。
- 兩者都令「量度」同「假設」互相依賴，形成循環。

### ④ 建議（等 SK 揀）

- **甲**：**唔追求 per-ask／per-round 歸屬**，只答一個更窄更硬嘅問題——「**同一份完全一樣嘅 prefix，第二次送出會唔會中 cache？命中幾多？**」（由真 trace 重組 body、tools 由 code 內 `nativeToolsSchema` 重建、連發 2 次）。成本 ≈**US$0.02**，唔需要 proxy、唔需要遊戲、唔需要 attribution。
- **乙**：照跑 M1，但**放寬定義**（主類＝非 intent／非 body；邊界改用 `sha1(user message)`；冷啟動**唔剔除**而係兩個數都報）。成本 ≈US$0.03，用真數據反過來驗假設。
- **丙**：做 P（永久 per-round log）—— log 行自帶回合上下文，**天生有歸屬**，但屬 product code 改動（要重走 plan／review 流程）。
- **丁**：停量度，直接按已知事實（82.4% 重複 payload）做 prompt 瘦身。

### 附：R3 數字核實捉到嘅錯（已記錄，未改入上文）

- `nativeToolsSchema` 定義喺 `LlmClient.java:628`（唔係 `:648`）。
- `BODY_REPAIR_SYSTEM` 只有 113 字元（唔可以講「兩個常數都長過 200 字」）。
- 價錢：`deepseek.com/en/platform` 記 flash hit $0.0028／miss $0.14；`api-docs.deepseek.com/quick_start/pricing`（查證日 2026-10-10，off-peak）記 hit $0.003／miss $0.15 ⇒ **比率同為 50×**，但絕對價兩頁唔一致；Pro 兩頁比率分別 120×／30×，**唔可以籠統講「兩頁一致」**。
- M0（平台 CSV）**真存在**（Usage → Export → 月度 ZIP，amount CSV 有 `input_cache_hit_tokens`／`input_cache_miss_tokens`），粒度＝**每日×模型×API key**；但 DeepSeek **冇**公共 usage API（只有 `GET /user/balance`），而且**會混入 Hermes 自己嘅 DS 用量** ⇒ 對「packai 3–5 條 ask」解像度近乎零。
- M2 成本下界應為 ≈US$0.02（唔係 US$0.01）。
- 雙樹實測：`check_dual_tree_sync.py` 無參數 → RC=0、paused=True、fail=0、warn=106；`--no-paused` → RC=1（FAIL）。
- 部機 java 進程：本輪 review 時 **0 個**（06:15 前後 SK 已收機）⇒ M1 前置 blocker 暫時唔存在。
