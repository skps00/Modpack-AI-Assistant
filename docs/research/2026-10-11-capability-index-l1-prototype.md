# L1 平價能力檢索「離線原型」＋真數據量測（2026-10-11）

> 目的：為四層「平價能力檢索」（L0 runtime 零 token → **L1 本地 SQLite 索引＋關鍵字／BM25 查詢（零 LLM token）** → L2 javap 只掃短名單 → L3 Modrinth／CurseForge hash 補覆蓋）補上**一直缺失嘅 L1 原型**，並用**真 pack** 量效能同質量。
> 狀態：**離線原型 + 實測**，未接入 product code、未改任何現有檔、未 commit。
> 工具：`tools/capability_index_l1.py`（純 Python stdlib ＋ sqlite3，**零第三方依賴、唔用 javap**）。
> 自檢閘：`tests/check_capability_index_l1.py`（離線，唔開 game）。

背景同 L0–L3 設計見 `docs/research/2026-10-10-cheap-capability-lookup-research.md`；已知限制見 `docs/research/2026-10-10-capability-index-crosspack.md`。

---

## 1. 方法

### 1.1 抽咩（唔用 javap，直接讀 class 檔）
逐個 jar 掃 `.class`，直接解析 **JVM class 檔 constant pool**，只抽三類事實：

| kind | 來源 | 例子 | 為何要 |
|---|---|---|---|
| `class` | zip entry 名（`.class` 路徑） | `FunctionalStorageDrawer` | 就係「class 名索引」本體，最平（連檔內容都唔使解壓） |
| `literal` | **真字串常數**（Utf8 被 `CONSTANT_String`（tag 8）指向） | `autocraft`、`tile.create.fluid_tank`、`wireless` | **核心信號**：lang key／config key／id／訊息，能力詞就係喺呢度 |
| `ref` | `CONSTANT_Class`（tag 7）指向嘅內部類名 | `net/minecraft/world/item/ItemStack` | 可選（**預設關**）：雜訊大、佔 ~4–5 倍體積 |

**關鍵設計決定（同 2026-10-10 研究 §2 S3 一致）**：**唔收** method name／field name（只經 `NameAndType`（tag 12）到達嘅 Utf8）。上一輪研究已證明「原始 class 名清單雜訊大到冇用」（`Minecraft` 撞 Craft／Mine）。今次淨係收**真字串常數**，令索引細、信號純；`ref` 因為係外部類引用、絕大部分係 vanilla／library 名，所以預設唔收。

### 1.2 索引結構（SQLite）
```
meta(key, value)                       -- schema 版本、pack、jar 數、entry 數、config、build_seconds
mods(id, jar, n_entries)
entries(id, mod_id, cls, simple, kind, value)   -- index(mod_id)
fts5(tokens, content='')               -- contentless FTS5，rowid = entries.id
```
`tokens` = 由 `value`、class simple name、class 路徑做 **camelCase 切詞 + 小寫** 得出。查詢用 FTS5 `MATCH` ＋內建 `bm25()` 排序（真 BM25，非近似）；FTS5 唔可用時自動 fallback 去 `LIKE '%term%'` ＋ idf 加權。

### 1.3 查詢排序（BM25-ish）
- 查詢詞 → 同一套 tokenizer 切詞 → `fts MATCH 't1 OR t2 OR ...'`。
- 每個 mod 分數 = 佢命中 entry 之中 **最相關 25 條** 嘅 `−bm25` 之和（top-25 飽和，防止大 mod 靠「條數多」霸榜）。
- 每條候選附 **證據**（mod／class／kind／字串／命中詞），即係「mod／class／字串」三件事齊。

---

## 2. 真數據來源

| pack | MC | mods 目錄 | jar 數 |
|---|---|---|---|
| ATM8 | 1.19.2 | `...\instances\packai_sandbox_atm8\minecraft\mods` | **380** |
| StarTech | 1.19.2 | `...\instances\packai_sandbox_startech\minecraft\mods` | **155** |

> 註：2026-10-10 研究寫 ATM8 = 379 jar；**今次實測 380**（環境可能加過一個 jar）。全部數字係本輪實跑。
> DB 一律寫 `%TEMP%\packai_l1\`（**唔入 git**）。

---

## 3. 量測數字（本機實跑，Windows／Python 3.14.2／sqlite 3.50.4）

### 3.1 建索引

| pack | jar | config | 建索引時間 | entry 數 | class | literal | **DB 大小** |
|---|---|---|---|---|---|---|---|
| ATM8 | 380 | 預設（class+literal） | **7.10 s** | 348,530 | 83,093 | 265,437 | **51,036,160 B ≈ 48.7 MiB** |
| ATM8 | 380 | `--refs`（加 class ref） | 15.89 s | 1,113,348 | 83,093 | 265,437 | 258,326,528 B ≈ 246 MiB |
| StarTech | 155 | 預設 | **3.18 s** | 162,927 | 37,399 | 125,528 | **23,846,912 B ≈ 22.7 MiB** |

- **預設 config 係推薦值**：`--refs` 令體積膨脹 **~5 倍**（765k 條幾乎全係外部類引用），對能力檢索幫助細，所以預設唔收。
- 對比上一輪 javap 路線（同一 ATM8：**292 s**、artifact 13.2 MB）：**建索引快 ~40 倍**（7 s vs 292 s），且**唔需要 JDK／javap**。

### 3.2 查詢時間

| pack | 端到端 CLI（含 Python 啟動 ~60 ms） | 純查詢（in-process）cold | 純查詢 warm |
|---|---|---|---|
| ATM8 | cold avg **87.0 ms**（n=5，min 80.3／max 94.9）；warm avg 92.7 ms | **24.1 ms** | **~19 ms**（中位，n=5） |
| StarTech | cold avg **82.6 ms**（n=5，min 79.2／max 86.0）；warm avg 82.9 ms | **10.4 ms** | **~10.2 ms** |

- 端到端大部分係 **Python 解譯器啟動**（~60 ms）；真正 query 只 **10–24 ms**。
- 對比 runtime ask 平均 **62,818 token／次**：L1 查詢係 **0 LLM token**、<0.1 s。
- cold≈warm，因為每次係新 process、OS file cache 建索引後已暖；真正「磁碟冷啟」未量（見 §6 限制）。

---

## 4. 質量評估（5 條真能力問題，人工判首 5 候選）

查詢詞（中文問題 → 英文能力詞；L1 淨識字面，所以呢步係人工／上層負責）。判定：**Y = 明顯做到**、**P = 相關但唔係正解**、**N = 唔關事**。

### ATM8（380 jar）

| # | 問題 | 查詢詞 | 首 5 候選（Y/P/N） | Y 數 |
|---|---|---|---|---|
| Q1 | 儲存大量物品 | storage,barrel,drawer,chest,cache | functionalstorage(Y)、sophisticatedstorage(Y)、refinedstorage(Y)、journeymap(N)、minecolonies(P) | 3/5 |
| Q2 | 自動挖礦 | quarry,miner,mining,drill,excavate,vein | Mekanism(Y)、minecolonies(P)、ImmersiveEngineering(Y)、pneumaticcraft(Y)、AdditionalEnchantedMiner(Y) | 4/5 |
| Q3 | 液體包裝 | fluid,tank,bucket,canister,drain,fill | create(Y)、Mekanism(Y)、pneumaticcraft(Y)、titanium(N)、littlelogistics(Y) | 4/5 |
| Q4 | 無線傳電 | wireless,flux,induction,tesla,energy | FluxNetworks(Y)、Mekanism(P)、ImmersiveEngineering(N)、createaddition(P)、ironfurnaces(Y) | 2/5 |
| Q5 | 自動合成 | autocraft,autocrafting,assembler,molecular,pattern,encoding | appliedenergistics2(Y)、refinedstorage(Y)、ImmersiveEngineering(P)、megacells(Y)、create(N) | 3/5 |

> **ATM8 合計：Y 16／25 = 64%**；Y+P（相關或更好）21／25 = 84%。

### StarTech（155 jar）

| # | 問題 | 首 5 候選（Y/P/N） | Y 數 |
|---|---|---|---|
| Q1 | 儲存大量物品 | functionalstorage(Y)、ExNihiloSequentia(P)、NuclearCraft(P)、appliedenergistics2(Y)、createdieselgenerators(P) | 2/5 |
| Q2 | 自動挖礦 | gtceu(Y)、create(Y)、NuclearCraft(Y)、MysticalAgriculture(P)、cofh_core(P) | 3/5 |
| Q3 | 液體包裝 | create(Y)、titanium(N)、gtceu(Y)、createdieselgenerators(Y)、botarium(P) | 3/5 |
| Q4 | 無線傳電 | FluxNetworks(Y)、appliedenergistics2(P)、ExtendedAE(Y)、exmachinis(N)、ExtendedCrafting(N) | 2/5 |
| Q5 | 自動合成 | appliedenergistics2(Y)、NuclearCraft(P)、solarenergy(N)、kotlinforforge(N)、create(N) | 1/5 |

> **StarTech 合計：Y 11／25 = 44%**；Y+P 19／25 = 76%。

### 命中率總結

| pack | 嚴（只算 Y） | 鬆（Y+P） | **首名（top-1）** |
|---|---|---|---|
| ATM8 | **64%**（16/25） | 84%（21/25） | **5/5 都係明顯正解** |
| StarTech | **44%**（11/25） | 76%（19/25） | **5/5 都係明顯正解** |
| **合計** | **54%（27/50）** | **80%（40/50）** | **10/10** |

**Recall（已知真答案有冇入 top-15）**：幾乎全中。ATM8 例：
- storage top-15 含 functionalstorage／sophisticatedstorage／refinedstorage／AE2／Mekanism／DimStorage；
- automine top-15 含 Mekanism／AdditionalEnchantedMiner／ImmersiveEngineering(Excavator)／industrial-foregoing／rftoolsbuilder／ftb-industrial-contraptions；
- autocraft top-15 含 AE2／refinedstorage／IntegratedDynamics／megacells。
⇒ **L1 係「短名單產生器」，recall 高，precision（首 5 名純度）中等**。

### 失敗例子（誠實列）

1. **library mod 污染**：`titanium`（Q3 fluid，ATM8／StarTech 皆見）係共用 library，只因有個 `FluidTankComponent` class 而中；玩家根本唔會「用」佢。同理 `kotlinforforge` 靠 `BitEncoding` 中 autocraft。
2. **巧合命名**：`journeymap`（Q1 storage #4）靠 class `CacheStorage`（地圖渲染 cache）中，同物品儲存無關；`create`（Q5 autocraft #5）靠 `CartAssemblerBlock`（砌礦車）中，唔係自動合成。
3. **同名唔同義 / 裝飾方塊**：`ImmersiveEngineering`（Q4 wireless #3）靠一個 client model 檔含 `tesla`／`induction` 字面中，但 IE 個 Tesla coil 係裝飾，唔係無線傳電。
4. **「包內冇」≠「唔相關」分唔到**：StarTech Q5（autocraft）只有 AE2 真係正解，其餘 4 個係 N——但呢個 pack 真係冇第二個自動合成 mod；L1 分唔到「能力唔存在」同「弱命中」，永遠照出 top-N。
5. **mod 名唔可靠**：`ExNihiloSequentia`／`NuclearCraft` 排前只因為 config／lang key 含 `barrel`，唔代表佢做物品儲存。

---

## 5. 自檢（determinism）

- 同一 pack、**兩個唔同 `PYTHONHASHSEED`** 各建一次索引：
  - `dump`（索引內容 canonical 文字）→ **byte-identical**；
  - `query --json` → **byte-identical**。
- 修正咗兩個真 bug：① 輸出夾雜 `build_seconds`（每次唔同）→ 移入 `VOLATILE_META`，dump／query 排除；② 遍歷 Python `set`（literals／class_names）受 hash 隨機化影響 → 全部**先 `sorted`** 再入索引。
- **SQLite 檔本身**唔係 byte-identical（頁面配置／freelist 會變）——所以閘係斷言 **dump＋query 一致**，唔係斷言 `.sqlite` 一致（符合任務容許）。
- 靜態閘 `tests/check_capability_index_l1.py`：合成細 pack → 斷言 top-1 命中、無關 mod 唔中、空查詢誠實回空、determinism、無絕對路徑／時間戳外洩、`--refs` 可選。**已跑綠**。
- 全 repo Python 閘：**130 pass / 1 fail**；唯一 fail 係 `check_ask_display_leak`（要真機 `latest.log`，環境無 → 印「NO LOG LINES」，**非本次 regress**，AGENTS.md 已記錄）。

---

## 6. 誠實限制（唔准當答案用）

1. **候選 ≠ 確認**。輸出係「呢個 mod 有含有能力詞嘅 class／字串」——**唔等於**佢真係做得到。見 §4 五個失敗例子。
2. **命名猜測**。詞命中係靠 class／字串名；`tesla`、`assembler`、`cache` 呢類一詞多義會誤中。
3. **中文問題唔可以直接餵**。L1 只認英文字面；中文問題要先靠人工／上層翻成英文能力詞（呢步係隱藏成本，未自動化）。
4. **分唔到「包內冇」**。永遠出 top-N，唔會答「冇」。要「誠實 miss」需另加門檻（例如最低分門檻，未做）。
5. **library / 核心 mod 污染**。titanium／kotlinforforge／bdlib 呢類共用 library 有大量能力詞 class，會排前。可用「library 名單」過濾（未做）。
6. **真「冷啟」未量**。本次 cold＝新 process 但 OS cache 已暖；真正磁碟冷讀延遲未測。
7. **未做 L2 接駁**。今日只驗 L1 產生短名單；「短名單 → javap 深挖」未接。
8. **索引含 class 名（意即玩家／mod 名單），但冇 mod 描述／lang**。所以 L1 係**獨立於** S1（mod 描述）／S2（lang）嘅第三條信號，唔取代佢哋。

---

## 7. 同 L0／L2／L3 嘅取捨建議（人話）

- **L1 值得做**：建一次索引 **7 s／49 MB（380 jar）**、查詢 **<0.1 s＋0 LLM token**，用真包測到 **首名 10/10 啱、首 5 名約 5 成純**、已知答案 recall 高。呢個係「唔燒 token 就收窄到十幾個候選」嘅抵用工具。
- **但要擺啱位置**：**L1 出「短名單」，唔出「答案」**。直接餵 LLM 會引入 library 污染同命名誤中（§4）。
- **同 L0 分工**：L0（runtime `IModInfo.getDescription()` ＋ registry 顯示名／tooltip）用**官方文字**、覆蓋約 2/5 能力但**權威**；L1 用**bytecode 字面**、覆蓋更闊但要篩。建議 **L0 先答／L1 補漏**，兩者並存。
- **同 L2 分工（最明顯嘅收益）**：L1 短名單 → 只對**十幾個** class javap（原本 1 400+ 次）。即 **L1 係 L2 嘅前置過濾**，令 L2 成本再降一個數量級。
- **同 L3 分工**：L3（Modrinth sha1 ＋ CurseForge fingerprint）補「mod 身份／描述」，覆蓋 59%→~100%；L1 補「mod 內 class 能力詞」。**互補，唔重疊**。
- **建議下一步（未做，等 SK 揀）**：
  1. 加**最低分門檻**（解決「分唔到包內冇」）；
  2. 加 **library 名單過濾**（titanium／kotlinforforge 等）；
  3. 試將 L1 同 L0 排序**合併**（官方文字加權高於 bytecode 字面）；
  4. 真機驗收：攞 5 條問題，L0+L1 → 短名單 → L2 javap，量最終 precision。

---

## 8. 檔案

- 工具：`tools/capability_index_l1.py`（新增）
- 自檢閘：`tests/check_capability_index_l1.py`（新增，已跑綠）
- 本報告：`docs/research/2026-10-11-capability-index-l1-prototype.md`（新增）
- 大 artifact（**唔入 git**）：`%TEMP%\packai_l1\atm8.sqlite`（49 MB）、`startech.sqlite`（23 MB）
- 未改任何現有 code；未 commit；未開 game。
