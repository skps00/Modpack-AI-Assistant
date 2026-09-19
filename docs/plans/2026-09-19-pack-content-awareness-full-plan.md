# 2026-09-19 — packai「Pack 內容感知 + 來源誠實」全計劃 v1

> 狀態：**計劃（未實作）**。範圍限 `forge/1.19.2`（`neoforge/1.21.1` PAUSED，本計劃唔郁）。
> 版本聲明：本計劃所有路徑／schema／行為只適用 **MC 1.19.2 + Forge 43.3.5 + KubeJS 6.x + JEI 11.8**（本機實測）。1.20.5+ components／1.21 單數目錄／NeoForge 相關做法**明確唔在範圍**。
> 依據：`docs/research/2026-09-19-pack-anatomy-and-modification-patterns.md`（版本分界＋讀取優先序）＋ 本計劃 §1 現況審計。

## 0. 一句話目標

令 packai 分得清「**pack 自加內容**」（`kubejs/data`、`kubejs/assets`、`startup_scripts` 註冊、DLC 命名空間、pack 自製 jar）同「**原廠 mod 內容**」，答得出 pack 自加物品嘅取得鏈，本地資料不足時按**版本標記**查上游 mod 文件，最後仍不足就**老實講**。

## 1. 現況審計（實測，2026-09-19）

| 項 | 現況（檔案為證） | 缺口 |
|---|---|---|
| KubeJS 內容 | 只掃 `kubejs/{startup,server,client}_scripts`（`PackAiConfig.kubejsMechanicScan`；`kubejsApiBridge`；上限 400 檔／8 MiB／8s） | `PackAiConfig` 明文「**Never scans kubejs/assets or kubejs/data**」→ 但本 pack 內容**全在此兩處**（32 個 kubejs-only 命名空間；`data/tetra` 2,420 檔） |
| jar 內容 | `logic/JarLightIndex.java` 已掃 jar zip 目錄（R/U/L facts → `config/packai/jar-cache/`），`logic/ModScanners.java` 偵測 kubejs／crafttweaker／groovyscript／ftbquests | 冇「命名空間 → 提供者」對應；`JarLightIndex` 只讀 recipe/loot facts |
| Tetra 內容 | `TetraMaterialItems`／`TetraSchematicLookup` 掃 materials／schematics | `modules`／`improvements`／`repairs`／`synergies` **完全冇讀** → 答唔到「圖紙＋材料」鏈（亞巴頓） |
| 來源標籤 | `AskResult.displaySrc`（`DISPLAY_SRC_UNKNOWN`）＋`packai.label.src.*`（例 scroll／tetra_use） | 冇「kubejs（pack 自加）」／「jar（原廠）」之分 |
| 配方類型 | Java 樹**完全冇** parse 配方 JSON（`grep "crafting_shaped"`＝0），配方一律經 JEI | `minecraft:smithing`／mod 自訂 type 只能靠 JEI 類別；卡路徑（JEI）同文字路徑（acquire）結論唔一致（實錘：SB 下界合金背包有卡、文字 miss） |
| Fallback | `HonestMiss`＋`config/packai/unknown_items.jsonl`（玩家零動作）；`KnowledgeRemote`（KB-2，預設 url 已設） | 冇「上游 mod 文件（標版本）」一層 |
| 版本護欄 | 1.19.2 樹獨立；neoforge 樹 PAUSED | 冇「本包／本版本」標記注入答案 |

實錘證據（全部本機檔案）：亞巴頓＝`kubejs/data/tetra/{modules/single/archotech_void_scythe.json, materials/metal/golden_age/archotech_steel.json, improvements/archotech_void/*, repairs/single/archotech_scythe.json, schematics/single/archotech_void_scythe.json, synergies/single/archotech_void_scythe.json}`＋顯示名 `kubejs/assets/golden_age_tetra/lang/zh_cn.json`＋tooltip `kubejs/client_scripts/golden_age/item_tooltips_1.js`＋註冊 `kubejs/startup_scripts/golden_age/dlc_template_item_register.js`。

## 2. 分階段交付（Phase 0–5）

### Phase 0 — fix A 收尾（已完成實作＋親驗，只欠真機）
- 內容：標準框架 keep-1 修法 A（gate 改讀佈局感知材料 id）＋ review 揪出嘅其餘項（D4 strip gate、D5 log、單一來源 helper）＝白名單 10 檔＋`RecipeCard.java`。
- 現況：cursor 已實作；Hermes 親驗 `compileJava compileTestJava` **BUILD SUCCESSFUL**、**49/49 harness 綠**、`tests/check_*.py` **120/121 綠**（1 條＝要真機 log）、新閘＋我親做負控（紅→綠，md5 逐字還原）。**未 deploy、未 commit**。
- 交付：與 Phase 1 一齊 build＋部署（見 §6），真機驗收 S5。
- 驗收 S5：木錘（STANDARD）→ 恰好 1 張「合成台」卡、落點相鄰「怎麼來」、尾段冇簡體 miss 句、來源行冇「已隐藏」；擬態（MODIFIED）→ 三項維持現狀。

### Phase 1 — 命名空間來源索引（對應 SK 建議 ③，並為 ① 打底）
- 目的：任何 `ns:path` 可以答「邊個提供」＝mod jar／kubejs(data|assets)／pack 自製 jar／runtime 註冊／未知。
- 做法（重用現有基建，唔另起爐灶）：
  1. 新 `logic/NamespaceProviderIndex.java`：掃 `mods/*.jar` **zip 目錄**（`JarLightIndex` 模式，唔解壓內容）→ 建 `ns → jar` 對應；同時掃 `kubejs/data/*`、`kubejs/assets/*` 目錄名 → `ns → kubejs`；jar 內 `META-INF/mods.toml` 嘅 `modId` 亦記入（處理「modId ≠ 命名空間」情況，例 `golden_age_mod` vs `golden_age`）。
  2. 快取到 `config/packai/provider-cache/`（沿用 item-index 嘅 fingerprint＋shard 模式）。
  3. 硬上限（新 config）：`providerIndex.maxJars`＝300、`providerIndex.maxEntries`＝20000、`providerIndex.maxMb`＝4、`providerIndex.timeBudgetMs`＝4000；全部可關。
  4. 註冊來源（`StartupEvents.registry('…')`）用輕量 regex 由 `kubejs/startup_scripts/**` 抽 `ns:id` 字面（抽唔到就標 `runtime-registered`，唔准猜）。
- 驗收：S1 對 `golden_age:archotech_void_scythe` 回「kubejs（本包自加）」；對 `minecraft:stick` 回「minecraft（原廠）」；對 `tetra:modular_sword` 回「tetra（mod jar）＋本包另有 kubejs 覆蓋」。harness：`NamespaceProviderIndexCheck`（純檔案系統替身，唔靠真 instance）。
- 風險：231 個 jar × namelist 成本 → 必須 async＋快取＋上限；大 pack（NFWC）要實測耗時（列為 S1b）。

### Phase 2 — 讀齊 pack 內容六層＋包作者文字（對應建議 ②）
- 目的：Tetra 類 pack 擴充答得完整（模組／圖紙／材料／改裝／修理／協同），並且用**包作者親手寫嘅文字**。
- 做法：
  1. 擴 `logic/TetraMaterialItems`／`TetraSchematicLookup` 家族：加讀 `modules`／`improvements`／`repairs`／`synergies`（路徑 `kubejs/data/tetra/**` 同 `data/tetra/**` 皆掃，1.19.2 複數目錄）；統一由新 `TetraContentIndex` 管，輸出「模組 → 可用材料／圖紙需求／改裝」關係。
  2. 加讀 `kubejs/assets/*/lang/*.json`（pack 自加顯示名；注意 BOM→`utf-8-sig`）＋ `kubejs/client_scripts/**` 內 tooltip 字串（已有 kubejs script 掃描基建）。
  3. 注入答案時標明來源（Phase 3 嘅標籤）。
- 驗收：S2 問「亞巴頓點嚟」→ 答案含：模組 `single/archotech_void_scythe`、圖紙（`schematics/single/archotech_void_scythe`）、材料（`golden_age:archotech_void_scythe`＝使徒殘片）、改裝（`archotech_void/*`），並標「本包自加」；S2b 木錘維持原廠 mod 路線。
- 風險：pack 內容命名混亂（同一料多變體）→ 一律以 id 對 id，唔做語意猜測。

### Phase 3 — 來源標籤落地（對應建議 ①）
- 做法：擴 `AskResult.displaySrc` 同 `packai.label.src.*` → 新增 label 值（`pack`, `mod`, `packJar`, `runtime`, `docs`）；三語 lang 檔同步（`en_us`／`zh_cn`／`zh_tw`）；scrub 既有標籤機制唔改語義。
- 驗收：S3 同一條答案內，pack 自加內容標「本包」、原廠內容標 mod 名；來源行唔會洩漏檔名／內部 id（沿用既有規則）。

### Phase 4 — Fallback 三級（對應建議 ④，SK 2026-09-19 指示）
- 級 1 本地檔案（Phase 1/2 ＋ 現有 JEI／quest／lang）。
- 級 2 **上游 mod 文件**：只喺級 1 全空時用；**必須標版本**（例「Tetra 1.19.2-5.6.0 官方 wiki」），並且唔准用最新版資料答舊版（鐵律）。實作：`docs` 來源層，預設**唔上網**（`docsLookup.network=false`），先用本機 jar 內自帶文件（patchouli／lang／advancement desc）；如日後開網，沿用 `KnowledgeRemote` 隱私契約（只送 item id、ETag 快取、失敗靜默）。
- 級 3 仍冇 → `HonestMiss`＋`unknown_items.jsonl`（維持零玩家動作）。
- 驗收：S4 一件本地真係冇嘅物品 → 答案標「本地未收錄」＋（若開）來源標版本；再冇 → 老實 miss 並記低。負控：故意指向不存在 id，確認唔會編嘢。

### Phase 5 — 卡／文字一致性（對應建議 ⑤）
- 目的：修正「卡片路徑（JEI）有、文字路徑 miss」嘅實錘不一致。
- 做法：以 Phase 1 provider index ＋ 現有 JEI 結果做單一來源，`AcquireAskTool`／`AskGrounding` 文字路徑同 `RecipeCard` 卡路徑對齊（同一結論、同一 id 集合）；加 trace 欄位對照（例 `acquire.src`／`card.src`），並用 SB 下界合金背包（`sophisticatedbackpacks:smithing_backpack_upgrade`）做回歸樣本。
- 驗收：S6 SB 背包 → 卡同文字都講「鍛造台：鑽石背包＋下界合金錠」；`create:schematic` → 卡同文字都講「冇呢個物品，你係想問 empty_schematic／schematic_and_quill」。

## 3. 共通硬約束（唔准違反）

1. **零玩家動作**：所有掃描自動、靜默；錯誤靜默（只寫 log）。
2. **寫入一律設定 gate＋硬上限＋key 去重**；唔准背景全包無上限掃描。
3. **零硬編碼**：唔准寫死 pack 名／mod id／命名空間（用 `ModScanners`＋provider index 推導）；唔准中文限定（文字一律 lang key，缺 key → `en_us` fallback）。
4. **唔准 NL literal**：新增玩家文字一律入 3 個 lang 檔（`en_us`／`zh_cn`／`zh_tw` 同步，key 數唔准減少）。
5. **log 全 ASCII**；trace 事件名／欄位語義唔改（只准加新欄位）。
6. **唔碰**：`neoforge/1.21.1`、prompt 政策段、`shouldDropFrameCard` 簽名、jar 部署腳本以外的部署方式。
7. 掃描全部 async＋可中斷＋有時間／記憶體預算；jar 只讀目錄，唔解壓內容。

## 4. 新增 config／settings（草案）

| key | 預設 | 上限 | 說明 |
|---|---|---|---|
| `contentIndex.enabled` | true | — | Phase 1 provider index 總開關 |
| `contentIndex.maxJars` / `maxEntries` / `maxMb` / `timeBudgetMs` | 300／20000／4／4000 | 同左 | 硬上限 |
| `packContent.lang` | true | — | Phase 2 讀 pack lang／tooltip |
| `provenance.enabled` | true | — | Phase 3 來源標籤 |
| `docsLookup.network` | **false** | — | Phase 4 級 2 是否上網（預設關，SK 決定） |

Settings：ADVANCED 頁加對應 key（3 檔 lang 同步）＋ `tests/check_settings_registry.py` 綠。

## 5. 驗收標準（開工前定稿；全部要真跑）

- 每 phase：`compileJava compileTestJava` BUILD SUCCESSFUL、harness 全綠（新增 ≥1）、`tests/check_*.py` 相對 baseline **零新增紅**（baseline 2026-09-19：121 檔＝120 綠＋1 資料不足）、lang 3 檔 key 數一致、改動檔新增 CJK 字串 literal＝0。
- 真機（SK 開 game 時一次過驗）：S5（fix A）＋ S2（亞巴頓）＋ S6（SB 背包）＋ S1（木錘來源標籤）。
- 每 phase 完成 → HANDOFF 一行＋（SK 准許時）commit。

## 6. 還原點與部署

- 每 phase 開工前：`.hermes/backups/2026-09-19_<phase>/`（逐檔 timestamped copy ＋ md5；untracked 檔特別標明）。
- 部署：**只准** `python "$LOCALAPPDATA\hermes\scripts\mc_mod_deploy_jar.py" --target packai --jar <新 jar>`（關 game、自動 backup、sha256 覆核）；禁止 hot-copy。
- 回滾：jar 由 `%TEMP%\deploy_backup_*` copy 返；code 由 backup dir copy 返。
- 部署時機：**Phase 0＋1 一併部署**（SK 2026-09-19 傾向合併），其後每 phase 獨立部署（由 SK 定）。

## 7. Review 要求（SK 規則）

- **計劃 review**：反方 → 正方 → 中立裁判，門檻 8:2；上限 3–4 輪；每輪必須有實質修改；卡死 → 停手報告（逐輪比分／卡死點／最貴未知／建議）。
- **實作**：一律 cursor-agent（寫死範圍）；Hermes 只做 plan／派工／親驗（自己跑 compile／harness／python 閘／diff／負控）。
- **實作後 code review 兩輪**：pass1 重構／pass2「三個月後最脆弱位」，脆弱位即刻修。

## 8. 待 SK 決定

1. 部署時機（Phase 0＋1 一齊？）與真機問答清單（S1／S2／S5／S6）。
2. `docsLookup.network` 預設開唔開（我建議**關**，維持離線優先＋私隱）。
3. 是否要我先出手機版「SOP 摘要」doc（非技術人話版本）——可選。
