# Plan：件物品「全部資料」覆蓋（含結構战利品／挖方塊取得）

- 日期：2026-09-19（晚）／作者：Hermes／狀態：**待反方 review（R1）**，未改任何 code
- 版本語境：MC **1.19.2** ＋ Forge 43.4.5；packai `mod_version=0.2.3`；實測 pack ＝ **FTB Skies Expert**（357 mods）＋ 主包（NFWC 系）＋ **E2E**（E9E）
- 觸發：SK 要求「player can get all the info about that item」，並補充「**some item is only can gain from structure (mine block)**」

## 0. 目標（一句）
玩家問任何一件物品，答案要覆蓋 **pack 內實際可得嘅全部資料類別**——包括冇配方、只可以由**結構宝箱／挖方塊／生物掉落／釣魚／交易／任務／腳本**取得嘅途徑，並且要講出**來源名**（結構名／方塊名／生物名／檔案）。

## 1. 現況量測（今日親測，可重跑）

### 1.1 資料側（packai 手上）——已經好齊，而且帶出處
樣本（`minecraft:end_portal_frame`，FTB Skies Expert，trace `ask-20260919-231449`）：
```
[PURPOSE] Shift-Right click with an empty hand to pickup
          Can only be placed on Glacio
          Hold Ctrl for Tags
-[use]-> BlockEvents.rightClicked (source:kubejs/server_scripts/playerhandler.js:40 tier:A)
-[use]-> BlockEvents.placed 條件:dimension:ad_astra:glacio (playerhandler.js:335)
-[quest_text]-> 任務描述
```
### 1.2 答案側覆蓋率（12 類資料 × 19 案例）
| 量法 | 結果 |
|---|---|
| 逐 category（取得／用途／tooltip／loot／trade／quest／guide／tags／mobdrop／fishing／recipe／worldgen） | **134/165 = 81%** |
| 剔除「只喺資料側存在」嘅假類別（`kubejs` 呢類唔會喺答案字面出現） | **≈90%** |
| 同一物品跑兩次（`tetra:modular_double`） | 文字相似 **65%**、**事實一致**（同 5 個工具呼叫）⇒ 差異屬**措辭**，唔係漏料 |

### 1.3 真缺口（3 個，已排除措辭因素）
| # | 缺口 | 實測 | 性質 |
|---|---|---|---|
| G1 | **`tags` 拎唔到** | 工具只回 `Hold Ctrl for Tags`；12/19 案例有此 gap | **工具集結構性缺口** |
| G2 | **`guide_fetch` 檢索唔準** | End Portal Frame 竟然回 Ars Nouveau 嘅「How to Enchant」頁（無關） | **檢索 bug**（5 案例受影響） |
| G3 | **答案長度上限**（1.3k–2.0k 字）壓縮可載資料量 | 19 條全部落喺呢個範圍 | 設計張力（要決定「塞入」定「分流去 UI」） |

### 1.4 取得途徑索引覆蓋（code 事實）
`AcquireAskTool`（`description()`：`loot/trade/quest/script`）＋ `JarLightIndex:173-182` ＋ `LootForwardIndex:88-119`
⇒ 已覆蓋 `loot_tables/**`（`chests/…` 結構箱、`blocks/…` 挖方塊（含「blocks/<item> 掉自己」預設）、`entities/…`、`gameplay/…`）＋ trade ＋ quest ＋ KJS script。
⚠️ **未確認**：`fishing`（`gameplay/fishing*`）實際有冇入 index、以及答案有冇強制提到（19 案例中 2 個案例有此類資料但答案冇提）。

## 2. 設計（deterministic，唔靠「prompt 叫佢講多啲」）

> 原則（SK 規則）：**唔接受 LLM 選擇性行為**——所以完整度要靠**結構**（facts 餵齊 ＋ 事後斷言 ＋ 缺就補），唔係靠語氣。

### D1 — facts 全量注入（source of truth）
`AskEngine` 組 facts 時，除現有 `jei` 卡之外，加入「**已索引取得途徑摘要**」結構化欄位：
```
acquireRoutes: [
  {kind:"craft", station:"Crafting Table", in:[...], out:1},
  {kind:"machine", station:"Crusher", ...},
  {kind:"loot", table:"chests/end_city_treasure", structure:"End City", ...},
  {kind:"blockdrop", block:"minecraft:ancient_debris", ...},
  {kind:"mobdrop", entity:"minecraft:ender_dragon", ...},
  {kind:"fishing"|"trade"|"quest"|"script", ...}
]
```
規則：**只放 pack 真有嘅**；每條帶 provenance（表名／方塊名／檔名:行號），令答案可以講出「邊度嚟」。

### D2 — 必答清單 ＋ deterministic repair
- 由 D1 生成 `requiredSections`（有資料嘅類別清單）。
- Post-check（`AskGrounding` 之後）：逐類驗答案有冇覆蓋（**類別級**，唔係 token 級）；缺 → 追加一個「**補充**」小節（只加缺嘅類別，原文一字不改）。
- ⚠️ 唔准用「叫模型記得寫齊」嘅 prompt-only 手法（會被無視／唔穩定）。

### D3 — 補工具集缺口
1. 新 `tags_lookup(item)`：由 pack 嘅 tag 索引（`data/*/tags/**`）回 tag 清單（含 `#c:` 共通 tag），並納入 D1/D2 類別。
2. `guide_fetch` 檢索修正：現時回無關頁 ⇒ 加相關度過濾（entry 標題／內文含 item id 或名；唔中就老實回空，唔好塞無關頁）。

### D4 — 儀器（可審計、可做 gate）
- 新 `tools/check_item_info_coverage.py`（入庫）：讀一個 run 嘅 traces，出 §1.2 嘅覆蓋矩陣（**類別級**）＋ per-case 缺漏清單。今晚已寫成雛型（`coverage_matrix.py`），要正式化＋加「資料側類別白名單」避免假類別。
- `tools/cardplace_sampler.py` 加 `--mode loot-only`：**只可以由結構箱／挖方塊／生物掉落取得**（無任何 recipe 產出、但有 loot 表命中）嘅物品池，每次**重新隨機抽**（SK 要求）。

## 3. 驗收標準（開工前定死；跑唔到就報 NOT RUN）

| ID | 標準 | 量法 |
|---|---|---|
| A1 | coverage instrument 對今晚 FTB trace 集：**類別覆蓋 = 100% of available** | headless 重跑 `check_item_info_coverage.py` |
| A2 | **真機**:隨機抽 **5 件 loot-only／block-drop-only** 物品 → 每條答案必須出現該途徑 **＋來源名**（結構／方塊／生物名） | FTB 沙盒 run（`--mode loot-only`，新 seed 記錄落 artefact） |
| A3 | **真機**:隨機抽 **5 件一般物品** → coverage 100%，且答案長度／格式冇變差（同 baseline 比） | 同上 |
| A4 | 0 regression：**50/50** Java checks 全綠；python 閘只保留已知 1 紅；卡落位檔 sha256 **零改動** | gradle 逐任務名跑＋sha256 |
| A5 | **負控**：拆走 D2 repair ⇒ A1/A2 必須跌（證明 gate 有效，唔係擺設） | 短暫移除再跑，還原後 sha256 一致 |
| A6 | 跨 pack：同一儀器喺**主包**跑一次（判 generic vs pack-specific） | 主包沙盒 run（可慳錢時段做） |
| A7 | 版本紀律：所有結論標 (MC/loader/mod 版本) | 報告審查 |

## 4. 白名單（准改檔案）
1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java`（facts 注入）
2. `forge/1.19.2/src/main/java/com/skps9/packai/logic/AcquireAskTool.java`（途徑輸出格式）
3. 新 `…/logic/TagsLookupAskTool.java` ＋ 註冊位（`api/AskTool*`／`client/AskToolRegisterEvent.java`）
4. `…/logic/GuideFetchAskTool.java`（檢索相關度）
5. 新 `…/logic/ItemInfoCoverage.java`（類別判定／補充段生成）
6. 新 `…/src/test/java/.../ItemInfoCoverageCheck.java`（A1/A5 用）
7. 新 `tools/check_item_info_coverage.py`；`tools/cardplace_sampler.py`（只加 `--mode loot-only`）
8. `tests/check_item_info_coverage.py`（python 閘）＋ `code_change_log.md`（repo 規矩）

**唔准郁**：`RecipeEmbed.java`／`RecipeCard.java`（卡落位）；`voice`／`Hermes` 任何嘢；`neoforge/` 樹；prompt 檔以外嘅行為；任何 jar deploy 去真 instance。

## 5. 還原方案
- 動手前：`git status --porcelain` → `%TEMP%\baseline_<ts>.txt`；白名單檔案逐個 `sha256` → `%TEMP%\baseline_sha_<ts>.txt`（**唔准入 repo**）
- 還原：逐檔 `cp` 返備份（工作樹有 127 項未提交改動 ⇒ **嚴禁** `git checkout -- .`／`git stash`）
- 新檔＝`rm`；jar 只入沙盒，真 instance 全程零改動（sha 頭 16 位 `06b5b129a114a233` 不變）

## 6. 風險／未知
| 風險 | 緩解 |
|---|---|
| D2「補充段」令答案變長、破壞現有格式／卡落位 | 補充段放最尾、唔動原文；A3 驗長度同格式；卡落位檔零改動 |
| `tags` 資料量巨大（一個 item 可能 10+ tag） | 只列 **具意義** tag（`#c:`／`#forge:`／`#minecraft:` 共通 ＋ mod 自家 tag），硬上限（例如 8 條）＋可 config 關 |
| `--mode loot-only` 抽到「無 loot 表命中但實際可挖」物品 ⇒ 假案例 | 交叉過濾：無 recipe 產出 **且** 有 `blocks/…` 或 `chests/…` 命中；抽完人手抽查 1–2 件 |
| 真機成本（~10 分鐘／~1M tokens 一次） | 半價時段跑；A2/A3 合併一個 run（10 案例）；A6 可延後 |
| 檢索修正（D3.2）可能令 guide 覆蓋下降 | 老實回空 > 塞無關頁；A1 覆蓋率對照會顯示 |

## 7. 成本／時序（估）
- D1+D2+D4 實作（cursor）≈ 1.5–2 小時；D3（tags 工具＋檢索修正）≈ 1 小時
- 我嘅驗證（headless）≈ 30 分鐘；真機 A2+A3 合併 ≈ 15 分鐘；A6 ≈ 15 分鐘
- **唔准**用估算當數據：上面數字係工作量估計，非量測結果

## 8. 未答問題（SK 決定）
1. 「全部資料」係唔係就係 §1.2 嗰 12 類？（有冇要加／要踢）
2. 長答案 vs UI 分流（G3）：要「答案塞齊」定「答案精簡＋UI 出口」？
3. tags 要唔要列全部，定只列具意義嘅（有上限）？
