# 「能力反查」成本研究：有冇更平嘅方法？（2026-10-10）

> 起因：SK 指示「而家噉樣做消耗太多 token，而且我相信有更好嘅方法」。
> 本檔只做**研究同量度**，**未改任何 product code**。全部數字都係本機實測或官方文件，附來源。

---

## 0. 先講清楚：其實係兩個唔同嘅成本問題

| # | 問題 | 現況成本 | 性質 |
|---|---|---|---|
| **P1** | **離線能力索引**（`tools/mine_capability_index.py`，javap 掃 235–478 個 jar） | 每 pack 154–332 秒 CPU、artifact 8.1–48.5 MB | **CPU／磁碟**（唔係 token） |
| **P2** | **Runtime 每次 ask** | 平均 **62,818 tokens／ask**，其中 **82.4% ＝同一份系統提示＋工具 schema 每輪重送** | **Token**（真金白銀） |

P1 唔食 token（佢係離線 CLI）；真正燒 token 嘅係 P2。但 P1 產生嘅 artifact（8–48 MB）一旦餵落 LLM（落點 B），就會變成第三個成本。所以「省錢」要三條線一齊睇。

---

## 1. P2：Token 到底燒喺邊（親手量，14 條真機 trace）

方法：抽 `%TEMP%\keybind_run{1,2,3}_results_*\*.jsonl` 逐輪拆 `send.system`／`send.tools`／`send.history`／`send.user`／`send.facts` 嘅 UTF-8 bytes，bytes÷3.5 估 token。
腳本：`%TEMP%\token_breakdown_20261010.py`。

| 情境 | 固定 payload（system＋tools） | 整個 ask | 固定佔比 |
|---|---|---|---|
| 10 輪問答（run1／run2） | 89,657 tok | ~108,000–116,000 tok | **77–82%** |
| 6 輪 | 51,102 tok | 56,099–62,202 tok | 82–91% |
| 3 輪（好嘅 case） | 20,505 tok | 21,204–21,878 tok | **94–97%** |
| **14 條合計** | **724,622 tok** | **879,461 tok** | **82.4%** |

- 平均每 ask：固定 **51,758 tok**／總 **62,818 tok**。
- 10 輪 case 嘅固定成本係 3 輪嘅 **4.4 倍**；每輪多付 8,965 tok 就係「再問一輪」嘅代價。
- 15 個 tool schema 佔 **4.9 KB／輪**（≈1,400 tok），但大部分問題只用 1–2 個 tool。

### 1b. 官方有嘅省法，我哋未用：DeepSeek context caching

- DeepSeek **context caching 係預設開**（官方 `https://api-docs.deepseek.com/guides/kv_cache`）。
- 價錢（官方 `https://www.deepseek.com/en/platform/`）：**cache hit 輸入 US$0.0028／M** vs **cache miss US$0.14／M＝相差 50 倍**；輸出 US$0.28／M。
- 官方限制（同一頁）：cache hit **要完整命中一個已持久化嘅 cache prefix unit**（滑動窗口），「頭兩次請求通常唔中，第三次先中」；best-effort，唔保證。
- **我哋完全睇唔到有冇中 cache**：
  - `forge/1.19.2/src/main/java/com/skps9/packai/logic/TokenUsage.java:25-28` 只讀 `prompt_tokens`／`completion_tokens`／`total_tokens` ⇒ **`prompt_cache_hit_tokens` 被丟棄**。
  - 檢查 `AI_test_NFWC_DIM\minecraft\packai\trace\` 13 個 `ask-*.jsonl`：**0 個**檔含 `usage`／`cache_hit`／`prompt_tokens` 事件 ⇒ trace 亦冇記錄。
- ⇒ 即係「82.4% 重複 payload」有機會**已經**被 cache 抵銷咗大部分，但我哋今日**冇數據**證明或否定。加兩行讀取＋一行 log 就量得到（零風險）。

---

## 2. P1：平價資料源實測（4 個真 pack，唔開遊戲、唔 javap）

方法：Python `zipfile` 唯讀（開 jar 一律 `with`，唔解壓落磁碟）。

| pack | MC | mods | S1 mod 描述 | S2 lang | S3 .class 路徑 | S4 任務／指南文字 |
|---|---|---|---|---|---|---|
| DJ2 | 1.12.2 | 235 | 15,994 B／253 | **5,745,240 B／40,378 key** | 5,512,582 B／90,010 | 7,902,065 B／1,899 檔 |
| ATM8 | 1.19.2 | 379 | 31,144 B／385 | **11,947,925 B／81,045 key** | 5,350,962 B／82,785 | 5,116,824 B／6,218 檔 |
| StarTech | 1.19.2 | 155 | 12,586 B／158 | 4,109,442 B／32,791 key | 2,389,500 B／37,400 | 7,060,310 B／2,281 檔 |
| ATM10 | 1.21.1 | 478 | **3,733 B（全部空字串）** | 21,175,210 B／152,084 key | 9,388,535 B／132,990 | 23,977,699 B／9,200 檔 |

**命中測試（5 條能力詞，只用上面文字源）**

| 問題 | 真值 | 結果 |
|---|---|---|
| 儲不可堆疊物品 | DJ2 `enderutilities` | **DJ2 中**（S2 `tile.enderutilities.jsu`＝"Junk Storage Unit"＋tooltip「Can only store normally non-stackable items」）；ATM8 **唔中** |
| 自動合成 | ATM8 `refinedstorage` | **中**（S2 lang `autocraft`×18；排第 2） |
| 無線傳電 | ATM8 `refinedstorage`(物品)／真電力＝FluxNetworks | 部分中 |
| 清 100×90 範圍 | — | **0/4 全唔中**（只出 `flatten` 雜訊） |
| 白色混凝土工廠 | — | **0/4 全唔中**（只撈到 Chisel／chipped 等方塊裝飾 mod） |

**結論**：
1. **最抵＝S1＋S2**（KB–MB 級，兩條真值能力淨靠 S2 就撈到）。
2. **S3 唔值用**：class 名單雜訊太大（`Minecraft` 撞中 Craft／Mine ⇒ `MinecraftLogger`、`MinecraftJavaLocator` 假命中排前）。
3. **S4 貴 3–5 倍**（每包 5–24 MB），命中率同 S2 差唔多 ⇒ 只宜針對性抽。
4. 兩類能力（**幾何範圍／尺寸**、**具體機器功能**）任何文字源都撈唔到 ⇒ 呢類**只可以**靠 bytecode／行為驗證（同 §2 限制一致）。
5. ⚠️ **NeoForge 1.21（ATM10）mods.toml description 全部空 ⇒ 新版唔可以淨靠 S1**。

### 2b. 關鍵發現：S1＋S2 我哋**遊戲內**其實已經有

- **S1（mod 描述）**：實 artifact 核過——`javap` 打真 Forge 1.19.2 jar（`fmlcore-1.19.2-43.4.0.jar`＋`forgespi-6.0.0.jar`），`net.minecraftforge.forgespi.language.IModInfo` 有 `getModId()`／`getDisplayName()`／`getDescription()`，而 `ModList.get().getMods()` 回 `List<? extends IModInfo>`。
  ⇒ **每個已載入 mod 嘅描述，喺遊戲內一行 API 就拎到，零檔案掃描、零 javap**（packai 今日冇用呢個）。
- **S2（lang）**：⚠️ **2026-10-10 06:4x 更正（原版寫錯，已撤回）**：原先寫「`logic/PackIndex.java:298` 已經 `translations.put(...)` ⇒ 顯示名／tooltip 已經 index 緊」——**錯**。`PackIndex.translations` 只由 `build()` 掃 **gameDir 嘅 config／腳本目錄**（kubejs／scripts／datapacks／config/ftbquests…）下嘅 `*lang*.json`，**完全冇讀 mod jar**；全 repo 5 個 jar reader（`GuidebookIndex`／`ItemConsumeUseFacts`／`JarLightIndex`／`RecipeJsonOutputs`／`WorldgenIndex`）**冇一個**讀 `assets/lang`。所以本報告 §2 量到嘅「S2＝40,378／81,045 key」係**離線掃 jar** 得出，**唔等於 runtime 有**。
  - **runtime 真係有嘅等價物**：(a) `ForgeRegistries.ITEMS` 遍歷 ＋ `ItemStack.getHoverName()`（本地化顯示名；`AnvilRepairHint:55`／`PatchouliBridgeImpl:45` 已有先例）；(b) `client/context/TooltipCapture.capture(stack, player)`（**已存在**，會展開 Shift 隱藏行）。呢兩個才係 B 落點應該用嘅 S2。
⇒ 即係「答『呢個包做唔做到 X』」所需嘅兩大支柱，**runtime 版本**係「`IModInfo.getDescription()`（S1）＋ registry 顯示名／`TooltipCapture` tooltip（S2）」——**唔需要 javap，亦唔需要讀 lang 檔**。（原版寫「已經喺手」係過度樂觀：S1 要新加、S2 要用 registry 而唔係現有 `translations`。）

---

## 3. 外部 API 路線實測（Modrinth／CurseForge）

- **Modrinth `/v2/search`**：免 key、免登入；實測 rate limit header `X-Ratelimit-Limit: 300`（每 IP 每分鐘 300 次），連打 60 request 全 200、零 429。
- **多詞 query 會回 0**：`void miner`／`wireless energy` → `total_hits=0`（AND 配對）；**只收單詞**（storage／builder／autocrafting）先有結果。
- **slug ≠ modid**：`/v2/project/sophisticatedstorage` → 404（要 `sophisticated-storage`）；`appliedenergistics2` → 404（要 `ae2`）。用 modid 直接查大量 404。
- **用 modid 去 search 亦唔可靠**：30 個真 ATM8 modid 樣本，真正命中約 **14/30**，其餘撞到附屬 mod（`sophisticatedstorage` → `bluemap-x-sophisticatedstorage`）。
- **唯一可靠 modid→project 路線＝sha1 hash 比對**：對 379 個 jar 算 sha1（**0.7 秒、免費、唔開遊戲、唔 javap**）→ 一個 `POST /v2/version_files`（algorithm=sha1）回 **223/379 ＝ 59%**（response 353,725 B／1.18 s）。
- **批量 metadata**：`GET /v2/projects?ids=[223]` 一次回齊 title／categories／description（1,467,900 B／0.56 s）⇒ 餵 LLM 嘅索引 ≈ 22,465 字 ≈ **~5,616 prompt tokens**。
- **覆蓋硬傷**：379 個 jar 有 **156 個（41%）喺 Modrinth 搵唔到**（hash 無對應），包括真答案之一 `sophisticated-storage`（CurseForge 首發）＋`flux-networks`、`rftools-builder`、`ender-utilities`。
- **CurseForge**：無 key 實測 `GET /v1/mods/search` → **HTTP 403**（`API Key missing or invalid`）。文檔（`docs.curseforge.com/rest-api`）有對等 endpoint：`/v1/mods/search`、`/v1/mods/{id}/description`、**`/v1/fingerprints`（hash→mod，同 Modrinth hash 路線對等）**，auth＝`x-api-key`；pageSize ≤ 50。補 41% 盲點就係靠佢。
- **命中測試**：盲目搜（路線 A）5 條只有 ~2/5 中；用真 pack hash 索引（路線 B）Q2／Q4 中、Q1／Q3／Q5 唔中（真答案唔喺 59% 覆蓋內，或 description 係推銷文案「An elegant solution to your hoarding problem」唔含 crafting 字眼）。
⇒ **verdict**：**唔可以當獨立能力引擎**（命中率低＋覆蓋 59%＋description 係推銷文案），但**可以做「零成本第一步 enrichment／候選產生層」**，配 CurseForge key 補覆蓋，唔確定就 fallback 落 javap／讀 config。

---

## 4. 業界既有做法（網上研究，全部有 URL；查證日 2026-10-10）

### 4a. 平台 API 路線（metadata 層）

| 做法 | 適用性 | 成本 | 精度風險 |
|---|---|---|---|
| **Modrinth `/v2/search`**（Typesense） | 可用但唔係答案 | 免費、免 key、300 req/min | **只搜 name／indexed_name／slug／author／indexed_author／summary（權重 15,15,10,3,3,1），長描述 body 明確唔在搜尋欄位內**——源碼硬證據：`github.com/modrinth/code` `apps/labrinth/src/env.rs`（`SEARCH_TYPESENSE_DEFAULT_QUERY_BY`）⇒ 先天 recall 低 |
| **CurseForge for Studios API** | 同 Modrinth（只搜 name／短描述），但**有 `/v1/fingerprints`（hash→mod）** | 免費 key（console.curseforge.com）；pageSize ≤50、總 ≤10 000；**每日配額官方無明文**（逾限回 403/429，實例 `github.com/itzg/docker-minecraft-server/issues/3251`） | 無 capability 概念 |
| **Modpack Index API**（modpackindex.com/api） | **包→mod 清單**、mod 跨平台連結 | 免費、免 key、rate-limited；**明文禁止 bulk-scrape** | 只係目錄 metadata，唔含功能 |
| **modpacks.ch / FTB 目錄 API** | 攞 FTB 系包嘅 mod 清單／版本 | 免費、已 cache | 同上 |
| **mcmod.cn** | 中文最大資料庫，但**無官方 API**、要 HTML scrape、改版即爛、ToS 風險 | — | **唔掂**（本任務亦禁瀏覽器登入） |
| **FTB Wiki / fandom module list** | 人工維護嘅 per-version mod 清單 | 免費 | 人工、滯後、唔係 per-capability |

### 4b. 現成 OSS（最貼近我哋嘅）

| 專案 | 對 packai 嘅意義 | 規格（查證日） |
|---|---|---|
| **modlens-mcp**（CreeperHost） | **最貼近**：一次性 ingest 全部 jar → 持久 DB（SQLite／Postgres）存 manifest、**class 索引**、mixin targets、AT/AW、反編譯源；查詢用 **BM25 FTS（免 Ollama）** 或 semantic search；有 `reindex class names`／`find-implementors`／`scan-registrations` | 4★、TypeScript、last push 2026-09-25、active |
| **jarspect**（Microck） | 證明「由 bytecode 抽 capability profile」係業界手法，而且**只抽特徵、唔傾倒全部 bytecode**：解析 constant pool＋invoke 指令 → 11 個 capability detector | 6★、Rust、2026-09-06、active |
| **Modpack-Inspector**（Rearth） | 證明「**manifest 讀取＋hash enrichment**」係極廉價 metadata 路線：讀 mods.toml／fabric.mod.json；Modrinth 用 JAR **SHA-1**、CurseForge 用 **MurmurHash2 fingerprint** 反查 | 36★、Go、2026-03-27 |
| **AMI**（Automated Materials Index） | 證明「由 class／inheritance／tags 抽 metadata 標籤再建可搜尋 index」係現成手法（但只喺 runtime、item/registry 層） | modrinth.com/mod/automatedmaterialsindex |
| **NotchNet** | 公開 **LLM+RAG** 例子：本地 Ollama(llama3:8b)+FAISS、**動態抓 RLCraft／FTB wiki**、auto mod detection、答「你個包入面其他 mod」——**但語料係 wiki 文字，唔係 bytecode** | 3★、MIT、`github.com/aaravchour/NotchNet` |
| **minecraft-mod-search**（MasterHesse） | 示範多平台 NL mod 搜尋＋把「整合包概念」拆組件 —— 做「推薦邊個 mod」而唔係「已裝 jar 能力反查」 | 3★、Python、2026-05-02 |
| **SearchableFTBQuests** | 證明「**搜 quest 文字**」呢條路有人行（mod 本身就係做呢件事） | modrinth.com/project/vjqVl3RU |
| **AstralBot**（Erdragh） | 社群**人手**維護「包功能 FAQ」（純 Markdown，非 LLM）—— 反證：正因為冇自動 capability index，先要人手寫 | 4★、Kotlin |
| mcmodding-mcp（66★） | 官方文檔 RAG，答「點寫 mod」，**唔關** capability 反查 | 2026-08-28 |

### 4c. Benchmark 搜證

- 唯一搵到嘅學術研究（`arxiv.org/abs/2103.14439`，CurseForge 2 228 個 mod 嘅人氣特徵分析）**唔係** capability lookup。
- **查唔到任何官方或社群做過「用 mod description 搜功能 vs 讀 bytecode」嘅 recall／precision 對比 benchmark** ⇒ 呢個係真空（我哋 §2 嘅實測就係自己補呢個窿）。

---

## 5. 綜合結論：更平嘅方法係「分層」，唔係換一個資料源

業界冇一個 API 可以直接答「呢個包邊個 mod 提供 X」（§4a 全部只索引 name／summary；§4c 亦無現成 benchmark）。但三個方向一致指向同一件事：**唔好每次 ask 都全量 javap＋傾倒證據；改為一次性建索引、查詢時只做檢索、只對短名單深挖。**

### 建議分層（由平到貴，每層可獨立驗收）

| 層 | 做咩 | 成本 | 風險／精度 |
|---|---|---|---|
| **L0（runtime，免費）** | 用 `ModList.get().getMods()` 拎 mod 描述（§2b 已實證 API 存在）＋已有 `PackIndex.translations`（§2b）＋item tags／JEI／quest 文字，建一個**排序嘅 capability 檢索**（即落點 B） | **0 token、0 新增檔案掃描** | 覆蓋 ~2/5 能力；幾何／具體機器功能撈唔到（§2） |
| **L1（離線，一次性索引）** | 照 **modlens-mcp 模式**：jar entry 名（class 清單）＋manifest＋lang ＋mixin/AT/AW 入 **SQLite**；用 **BM25（免 embedding）** 查 | 一次性本地算力；查詢 0 LLM token | class 名雜訊大（§2 S3）⇒ 只可做**第一層過濾**，要配人工／LLM 篩 |
| **L2（離線，只對短名單）** | **javap 只掃 L1 出嘅短名單（例 ≤20 個）**，唔掃整個 pack | 由 1 500 次 javap → 十幾次 | 現狀嘅精度，成本降 1–2 個數量級 |
| **L3（外部，可選）** | Modrinth **sha1 hash** 路線（免費、0.7 s）＋ **CurseForge `/v1/fingerprints`**（要免費 key）補 41% 盲點 | 2 個 HTTP／pack、索引 ≈5 616 prompt tokens | 覆蓋 59%→~100%；description 係推銷文案，只可做 enrichment |

### 另外一件獨立、更平嘅事（同 capability 無關但直接答「太貴」）

P2（§1）顯示 **82.4% token 係每輪重送同一份 system prompt＋tool schema**，而 DeepSeek caching **預設已經開**（hit 價差 50 倍）。但 packai 今日**丟棄 `prompt_cache_hit_tokens`**（`TokenUsage.java:25-28`）＋trace **0 個檔**記錄 usage ⇒ 我哋連「有冇中 cache」都唔知。
**加讀兩個欄位＋log 一行 = 零風險，即刻有真數據**，之後再決定值唔值得縮 prompt／減輪數。

### 建議次序（未做，等 SK 揀）

1. **先做 cache-hit 量度**（最平、零風險、即刻有數據）
2. **再做 L0**（0 token 成本；用 5 條真問題驗收命中率）
3. L1 → L2 按 L0 命中率決定值唔值
4. L3 視乎要唔要覆蓋冷門 mod

**明確唔建議**：維持現狀（每 ask 全量 javap＋餵大 artifact）——§4b 已有多個現成做法證明「只抽特徵／建索引」係行得通嘅。

