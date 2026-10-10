# Plan v3：成本地基 — cache 命中量度 ＋ system prompt byte-stable（2026-10-10）

- 觸發：SK「而家噉樣做消耗太多 token，而且我相信有更好嘅方法」。
- 事實底座（全部親手重跑）：**82.4% token ＝每輪重送同一份 system prompt ＋ 15 個 tool schema**（固定 51,758 tok／ask，總 62,818；腳本 `%TEMP%\token_breakdown_20261010.py` 重跑重現；出處＝`docs/research/2026-10-10-cheap-capability-lookup-research.md` **第 29／31 行**（該檔只有 §0–§5））。
- **本 plan 取代** `docs/plans/2026-10-10-cache-hit-measurement.md`（v1–v3、R1–R3、停在等 SK 揀甲／乙／丙／丁）。唯一衝突：舊 plan §P 寫死「唔改 `TokenUsage`／`AskResult` record 簽名」——本 v3 **明確推翻**（理由：舊約束為「3 行加 log、零足印」而設；本階段要 **per-ask 歸屬入 trace**，唔改 record 就做唔到結構化累加；全 repo 只有 **8 個建構點**要跟，見 §3）。
- 業界：OpenClaw（stable prefix／volatile suffix＋`cacheRead` 正規化）／Hermes（`system_and_3`，4 breakpoint）／DeepSeek Harness（token meter）／arXiv 2601.06007（省 41–80%）＝**一致做「讀 provider cache 計數 ＋ 保持前綴 byte 一致」，冇一家做 per-ask 歸屬**（`docs/research/2026-10-10-harness-prompt-cache-comparison.md`）。

## 0. Review 史與逐條裁決

| 輪 | 比分（正方:反方） | 主要打中 |
|---|---|---|
| R1 | 5.5 : 4.5 | V2 驗收同官方 cache 規則矛盾；tools 入唔入 prefix 未證；record 簽名影響面漏；dual-tree 機制寫錯；V4 冇指標；漏 trace 歸屬 |
| R2 | **4.5 : 5.5** | **V2 假陽性**（側線分類 hop 恆定命中）；設計 0 越權聲稱＋未文檔化；§0 節號對唔上正文；AskTrace 事件落點／test 未寫；V4 sha 抽取會被側線污染；側 hop usage 唔入主帳；設計 B 其實改咗結構唔止位置 |

### 逐條裁決（v3 位置全部經 grep 核對）

| # | 指控 | v3 修法（實際位置） |
|---|---|---|
| R2-1（high） | V2 可以靠「意圖分類」側線蒙混：`INTENT_CLASSIFY_SYSTEM` 係常數，跨 ask byte-identical，必然先中 cache ⇒ 設計 B 失效都可以 pass | **§2 設計 A 加 hop tag**（`cache[main]`／`cache[classify]`／`cache[body]`）；**§4 V2 只認 `cache[main]` 行**，並要求該行 `prompt` 係主線量級（≥10k，實測 14.5k 級） |
| R2-2（high） | 設計 0 聲稱「直接決定設計 B 值唔值得做」屬越權；又冇寫 script／key／model 來源 | **§2 設計 0 降格為「只答 provider 層行為」**＋補文檔（script／model／key 來源／成本／tools 係自製 schema）；「packai 前綴穩唔穩」改由 **§0.2 真機 trace 直證**（已完成，零成本） |
| R2-3（med） | §0 表「具體位置」欄指向唔存在嘅 §4.1、錯節號 | 本表改為**逐條寫真實節號＋行號**，已 grep 核 |
| R2-4（med） | AskTrace usage 事件冇落點、冇覆蓋真 test、指向唔存在嘅 `check_ask_trace*.py` | **§3 #4**：落點＝`AskService.finishAskTrace:505`（`AskTrace.close:544` 之前）；**明寫唔准喺 `close()` 內自動附加**；要覆蓋**兩個** `AskTrace.begin`（`AskService:2429` 亦有一個）；真 test＝`forge/1.19.2/src/test/.../AskTraceCheck.java`（assert `lines.size()==3`＋頭 3 條事件）⇒ **要一齊更新** |
| R2-5（med） | V4 抽 `send.system` 會被側線污染（一個 ask 有 1 條 278 分類＋3–4 條主線） | **§4 V4**：只取 `len>1000` 嘅主線 system（側線固定 278，實測） |
| R2-6（med） | 側 hop（意圖／body 修復）用獨立 `new LlmClient()`，usage 唔入主帳 ⇒ log 同 trace 必然對唔上 | **§4 度量定義**：量嘅係**主線前綴**；log 分 hop tag、trace 只落主線；兩邊定義寫死一致 |
| R2-7（med） | 設計 B 唔止「換位置」：`rules` 由 system 純文字變成 user JSON 內嘅 escaped nested value ⇒ 遵守度風險更大 | **§3 設計 B** 改措辭；**§4 V4 加語義抽查**（3 題核 rules 有冇生效／被忽略），唔止靠長度／card 數 |
| R2-8（low） | V2「第 1 條必 miss」漏咗第 2 條（官方 Example 2：前兩條唔中） | **§4 V2** 寫成三級：實測第 2 條已中；若第 2 條 0 → 睇第 3 條；**≥4 條仍 0 才當異常**（兼容官方保守講法） |
| R2-9（low） | `check_token_usage.py` 擋唔到「實作漏 cache 欄」 | **§3 #6**：保留 mirror 更新（3 欄→5 欄），另外**新增**一條真會變紅嘅 check（regex 核 `fromResponse` 讀 `prompt_cache_hit_tokens`／`prompt_cache_miss_tokens`） |
| R2-10（low） | A4 數字自相矛盾（「8 個＋5 處」） | 統一：**共 8 個 `new TokenUsage(`**（`TokenUsage.java` :13 / :25 / :64 ＋ `DailyTokenUsageCheck.java` :23/24/26/28/30）；`plus` 簽名喺 **:60**、其建構喺 **:64–66** |
| 數字核 | 其餘全部 TRUE：`send.system` 係真事件（`AskTrace.java:745`，由 `LlmClient:519` 發）；`LlmClient:270-277／:541-545` 400→notools 永久變體為真；`PackAiConfig:273-281` 預設 OpenAI 為真；`check_ask_capable_slim.py` 唔係 payload-key 閘（已由落地位刪）；Python baseline **127 檔、1 紅** 重跑一致 | 已全部寫入本 v3 |

## 0.1 設計 0 實測（provider 層；`%TEMP%\cache_probe_20261010.py`；9 request；成本 <US$0.01）

環境：`api.deepseek.com`、`model=deepseek-flash`、**非串流**；key 由 instance `config/packai-client.toml` 讀（**永不印**）；system＝27,900 字元自製固定文字；tools＝**自製** 15 個 schema（**唔係** packai 真 schema⇒只可推 provider 行為，唔可代替真機）。

| # | 情況 | prompt | cache_hit | cache_miss |
|---|---|---|---|---|
| 1 | 冷啟動 | 14,622 | 0 | 14,622 |
| 2 | 同 sys＋同 tools | 14,622 | **14,464** | 158 |
| 3 | 同前 | 14,622 | 14,464 | 158 |
| 4 | 同 sys、**換 15 個 tool schema** | 14,622 | 13,568 | 1,054 |
| 5 | 換完之後第 2 條 | 14,622 | 14,464 | 158 |
| 6–8 | 唔帶 tools（對照） | 13,542 | 13,312 | 230 |

結論：① 三個 cache 欄位（`prompt_cache_hit_tokens`／`prompt_cache_miss_tokens`／`prompt_tokens_details.cached_tokens`）實存在，非串流都有 ⇒ 設計 A 讀得到；② 固定前綴**第 2 條請求就中**（同官方 Example 2「前兩條唔中」唔同——官方嗰個情境係「長文本身做前綴」，本 probe 係「固定 system＋易變尾段」；V2 因此寫成三級判斷）；③ **tools 確實喺 cache prefix 內**（換 tools 只令工具區 ~896 tok 單獨 miss）。

## 0.2 真機 trace 直證：現行 system prompt 真係逐題漂移（Hermes 親跑 `%TEMP%\sysprompt_stability_20261010.py`）

掃 `AI_test_NFWC_DIM\minecraft\packai\trace\ask-*.jsonl`（13 個 ask、共 **63 條 `send.system`**）：

| 類別 | 條數 | 觀察 |
|---|---|---|
| **側線**（len＝278，意圖分類） | 13 | **13 條 sha 全部相同**（`b146557d0dea`）⇒ 恆定，**必然跨 ask 命中**（＝R2-1 假陽性來源） |
| **主線**（len>1000） | 50 | **2 個 unique sha**：`903daed88d7f` len 14,590 ×30；`b75bbe0bdf39` len 14,593 ×20 |

逐行 diff 兩個主線 system，差異**只喺一行嘅尾段**（同 `LlmClient:478-481` 拼接位對得上）：
`…硬规则（R5）：…一定有标题行＋步骤文字。` ＋ 變體 A「整合包可能只魔改部分…」／變體 B「此物品／题目有本地覆…」

⇒ **cache bomb 實錘**：逐題變嘅 `rules`（`ReplyLang.llmRules` 5 分支）拼喺 `messages[0]`，令 14.5k token 前綴每轉一次分支就整段 miss。**設計 B 正正針對呢一行**。

## 1. 兩個真問題（親核）

1. **睇唔到**：`logic/TokenUsage.java:20-29` `fromResponse` 只讀 3 欄（`:26-28`），丟棄 cache 兩欄；13 條 instance trace **零 usage 事件**（event 種類共 20 種，冇 usage）⇒ 冇數字睇。
2. **前綴唔穩**：見 §0.2；成因＝`logic/LlmClient.java:379-380` 逐題計 `style`／`rules`，`:478-481` 拼入 `messages[0]`（`:482` add 喺 history／user 之前）。

**Provider 前提**：`PackAiConfig:273-281` 預設 `api.openai.com`＋`gpt-4o-mini`（純 String、無 validator）；實測 **8 個 instance** config 全部指 `https://api.deepseek.com`（唔止 3 個）。fallback `prompt_tokens_details.cached_tokens` 只補 hit ⇒ **miss 記算式＝`prompt_tokens − cache_hit`**。

## 2. 三件工作（0 → A → B）

### 設計 0（**已完成**，見 §0.1）

### 設計 A：cache 命中量度（**唔改 prompt、唔加 request**）

- `logic/TokenUsage.java`（record，:12）：加 `promptCacheHitTokens`／`promptCacheMissTokens`（`-1`＝未知）；`fromResponse` 讀兩欄＋OpenAI fallback；`plus()` 同步加總；`NONE` 一齊改。
- `logic/LlmClient.java` 兩解析點（`:229-233` chatOnce／`:552-557` completeRound）各 log 一行，**帶 hop tag**：`Pack AI cache[main] hit=… miss=… prompt=…`／`cache[classify]`／`cache[body]`（`chatOnce` 呼叫者分辨：`AskService:1561` 意圖、`:2111` body 修復、其餘＝main）。
- `client/service/AskService.java`：usage log **:607-612** 加 cache 欄；**另 :598-604（usage_missing 分支）亦要加**。
- **新**：`AskTrace` usage 事件（`hit`／`miss`／`prompt`／`round`），落點＝**`AskService.finishAskTrace:506`**（`close()` 喺 :544 呼叫，之前）；**只落主線**（側 hop 唔入 trace，定義寫死，見 §4）。

### 設計 B：令 `messages[0]` byte-stable（**內容逐字保留，但承認結構有變**）

- 把逐題變嘅 `rules`（`LlmClient:380`）由 system message 搬去 **user payload 新 key `rules`**（同 `jei`（`:439-441`）並排）。
- ⚠️ **如實聲明**：唔止換位置 —— user content 係 `GSON.toJson(user)` 字串（`:496`），`rules` 變成 nested JSON 內嘅 escaped 值，模型要喺 JSON 內 parse 指令 ⇒ **遵守度風險大於「純位置改動」**，故 V4 加語義抽查。
- **唔改任何 method 簽名**（`rules` 已喺 method 內計）；`user` map 無條件送出（`:494-497`）⇒ tools／no-tools 兩種 **completeRound** 模式都送（`chatOnce` 另一條 body，唔受影響）。

## 3. 落地位（漏一項即靜默或紅）

| # | 檔案 | 改動 |
|---|---|---|
| 1 | `logic/TokenUsage.java` | record 加 2 component（:12）＋`NONE`(:13)／`fromResponse`(:20-29)／**`plus` 內建構（:64-66；方法簽名 :60）** |
| 2 | `logic/LlmClient.java` | 兩解析點 log（帶 hop tag）；user payload 加 `rules` key |
| 3 | `client/service/AskService.java` | usage log **:607-612** ＋ **usage_missing 分支 :598-604** |
| 4 | `logic/AskTrace.java` ＋ `AskService.finishAskTrace:505` | 新 usage 事件（**唔准喺 `AskTrace.close()` 自動附加**）；覆蓋兩個 `AskTrace.begin`（含 `AskService:2429`） |
| 5 | `forge/1.19.2/src/test/.../DailyTokenUsageCheck.java` | **5 個 `new TokenUsage(...)`（:23/24/26/28/30）補新參數** |
| 6 | `forge/1.19.2/src/test/.../AskTraceCheck.java` | assert `lines.size()==3`＋頭 3 條事件類型 ⇒ **要同步更新**（否則 compileTestJava 紅） |
| 7 | `tests/check_token_usage.py` | mirror 3 欄→5 欄（:26-37／:47-50） |
| 8 | **新** `tests/check_token_cache_fields.py` | 真會變紅嘅閘：regex 核 `fromResponse` 有讀 `prompt_cache_hit_tokens`／`prompt_cache_miss_tokens`（R2-9：舊 check 擋唔到漏欄） |

**dual-tree**：`TokenUsage.java`／`LlmClient.java` 兩樹都有 ⇒ forge-only 改動 = byte-drift，**paused 模式下 WARN、RC 仍 0**（屬預期）；**唔郁 gate**（`TREE_SPECIFIC` 只管單邊存在嘅檔；ALLOWLIST 只收「純邏輯要 lockstep」嘅例外）。

**唔准郁**：卡落位（`RecipeEmbed`／`RecipeCard`）／`AskReplyScrub`／`AskToolLoop` 三張名單／lang 檔／system prompt 內其他文字／`neoforge` 樹／任何 method 簽名。

## 4. 驗收標準

- **V1** `compileJava compileTestJava` RC=0；現有 harness 全綠（含更新後嘅 `AskTraceCheck`／`DailyTokenUsageCheck`）。
- **V2（主線專屬，防假陽性）** 沙盒真機連問 **2 條** → log 必須見到 **`cache[main]`** 行而 `prompt` 係主線量級（≥10k；現況 14.5k 級）且 `hit` 接近 `prompt`（§0.1 實測比例 ≈99%）。三級判斷：第 2 條中＝PASS；第 2 條 0 → 睇第 3 條；**≥4 條仍 0 才當異常**，並即對照 §0.2（主線 sha 是否已收斂成 1 個）＋確認 provider。**側線 `cache[classify]`／`cache[body]` 嘅命中一律唔算**（R2-1）。
- **V3** 全部 `tests/check_*.py` 冇新增紅；baseline＝**當日實跑**：127 檔、1 已知紅（`check_ask_display_leak.py` 需真機 `latest.log`）。新 check (#8) 要**做負控**：故意抽走一行 cache 讀取 → 必須變紅。
- **V4（機械＋語義）**：同一批題（**≥6 條固定清單**，沙盒）設計 B 前／後並列：
  1. body 長度、item／card 數、`【來源】`行齊全度 —— **用「同題 N=3 次」分佈比較**（唔靠單次 ±30%：`temperature` 非 0，單次波動不可靠）；
  2. **零新 fail-closed 外洩 token**（親掃真機 body）；
  3. **語義抽查（新，針對 R2-7）**：抽 3 題人手／LLM 核 `rules`（R5 硬規則：標題行／numbered steps／卡片貼文字／【來源】最後）有冇真生效、有冇被忽略；
  4. **byte-identity 證明**：由 trace 抽 `send.system` **只取主線（len>1000）**，同條件下 **sha256 必須收斂成 1 個**（現況 2 個，見 §0.2）；同時快照 `offered`／`preferObtain`／`RecipeCardsMode`／`lang`／pack `AGENTS.md`，條件一變唔跨邊界比對。
- **V5** `git status` 只准預期檔（改動前已記 HEAD＋status baseline）；`neoforge/` 零改動；唔准 `git add -A`。

**度量定義（寫死，防 R2-6）**：本 plan 量嘅係**主線（main ask）前綴**；側 hop（意圖分類／body 修復）用獨立 `new LlmClient()`，其 usage **唔入**主帳 ⇒ log 有 tag、trace 只落主線，兩邊定義一致。

## 5. 風險／還原

- 風險：中低。設計 A 唔改 prompt；設計 B 改指令位置＋結構（nested JSON）⇒ V4 第 3 項把關，變差即**只保留設計 A**。
- 相容性風險：`AskTraceCheck`／`DailyTokenUsageCheck` 係 Java test，改 record ／加 trace 事件會令佢哋紅 ⇒ 必須同步改（#5／#6），唔准為綠而改 assert 內容以外嘅嘢。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\` 還原（部署**只准** `mc_mod_deploy_jar.py --target packai`，要關 java）。
- ⚠️ **jar guard cron 自 2026-09-15 20:25 起 paused**（冇自動守門）⇒ 部署／還原要人手核 sha256。
- 改動前：記 `git rev-parse HEAD` ＋ `git status --short` 全文。

## 6. 執行（**拆兩個 slice 落地**）

- 實作＝**cursor-agent**（唔准 commit、唔准 hot-copy jar、只改 §3 範圍）；Hermes 做 plan／派工／**親驗**（compile／harness／check／diff）。
- **Slice A（設計 A）＝可以獨立落地、獨立驗收**：零 prompt 改動、只有 log／trace／record 欄位；驗 V1／V3／V2 前半。過了即刻 commit。
- **Slice B（設計 B）＝另開一輪驗收**（V4 全項：機械＋語義＋byte-identity）；A 唔過就唔開 B。
- 每個 slice 完成後跑兩輪 code review（pass1 重構／pass2 三個月後脆弱位）。
