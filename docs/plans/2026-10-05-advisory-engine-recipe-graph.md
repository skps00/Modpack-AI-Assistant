# 2026-10-05 顧問引擎（plan **v4** — 範圍收窄 + 檢視器抽象 + 區間輸出）

- Status：DRAFT v4 — 未批准、未實作（R1 3:7 → R2 4:6 → R3 4:6；v4 大幅收窄範圍後待 R4）
- SK 已決定（2026-10-05）：① 產品要**通用**（all the type）② **先做共同底座＋1 類**，量測完再擴 ③ 檢視器要一層抽象（唔可以假設所有包都係 JEI）④ 倍化輸出要**最低／最高區間** ⑤ 答案冇資料＝**「我唔確定」**
- 題庫（驗收用）：`docs/plans/2026-10-05-player-question-set.md`（28 條）
- 已擱置：診斷線 `2026-10-05-crash-diagnosis.md`

## 0. 一句話

先建**共同底座**（檢視器抽象 ＋ 配方依賴圖），只交付**能力 A（合成依賴）＋ H（進度閉包）**；其餘 7 類 defer 到量測有數據為止。所有數值附來源；算唔到就講「我唔確定」或「機率部分未有資料」。

## 1. 本階段範圍（v4 收窄）

**做**：
- `RecipeSource` 抽象層（見 §2）
- 配方依賴圖（item/fluid 節點；recipe 邊；含 catalyst）
- 能力 A（題庫 A1–A5）：「呢件嘢點做／要幾多材料／要邊幾部機器／互換品／我有 X 差咩」
- 能力 H（題庫 H2–H3）：「去到咩階段先做得到 X／有冇替代路線」
- 輸出契約（§3）＋ honest-miss

**defer（唔做，直到 P0 有數據）**：B 倍化、C 能源、D 世界、E 工具裝備、F 自動化、G 流體、H1。
**唔做**：獨立百科 GUI、server-side、pack 專屬硬編碼、`neoforge`、任何猜數字。

## 2. 檢視器抽象（v4 新增）

| 檢視器 | 現況 | 對策 |
|---|---|---|
| JEI | packai 已接（`IRecipeManager.createRecipeLookup(type).includeHidden().get()`；`client/jei/JeiLookup.java:739`、`JeiInfoPages.java:99`） | Phase 1 唯一實作 |
| REI | 部分包只用佢（實測 UniversIO 177 jar／0 JEI） | Phase 1 **唔支援**：答「我唔確定（此包冇我識讀嘅配方介面）」 |
| EMI | 未見於本地樣本 | 之後用同一介面接 |

介面定義：`listCategories()` / `listRecipes(type)` / `catalysts(type)` / `slots(recipe)` → 圖 builder 只依賴介面，唔直接呼叫 JEI。**冇任何檢視器 ＝ 降級，唔係 bug。**

## 3. 輸出契約（v4 新增，回應「最低／最高」）

每個數值一律附三樣：**值 ＋ 來源標籤 ＋ 未計入因素**。

| 情況 | 講法 |
|---|---|
| 固定比例鏈（例：Mekanism 5× 全程 1:1 步進） | 「保證 **5×**（最低＝最高）」＋ 機器清單 ＋ 來源 |
| 有機率加成且我們有數據 | 「保證 **1×**，最高 **1.33×**（機率來源：…）」 |
| 有機率但**冇**數據（JEI 冇結構化欄位） | 「保證 **1×**；**另有機率加成，數值未有資料**」 |
| 鏈含循環／外部加成（Fortune、附魔、外部 mod） | 明列「未計入：…」 |
| 完全算唔到 | 「**我唔確定**」 |

**鐵則**：最低值只可以由固定比例推出（唔准當機率 = 0）；最高值冇來源就唔准寫數字；唔准把兩端平均。

**Mekanism 實例（官方 wiki 數字）**：Tier1 2×（Enrichment→熔）／Tier2 3×（+氧，Purification→Crusher→Enrichment→熔）／Tier3 4×（+氯化氫，Chemical Injection）／Tier4 5×（+硫酸，Dissolution→Washer→Crystallizer→5 晶體→Tier3 流程）；**原礦只 3.33×**。呢條鏈全部固定比例 → 最低＝最高＝5×。

## 4. 樣本包（v4 定案；全部 1.19.2，packai 只支援 1.19.2）

| 用途 | 包 | jar／mod 數 | 狀態 |
|---|---|---|---|
| 科技主力 | `packai_sandbox_atm8`（+ 真 `All the Mods 8 - ATM8`） | 380 / 379 | 本機已有 |
| 科技（另一個風格） | `packai_sandbox_startech` | 155 | 本機已有 |
| 魔法／冒險 | `AI_test_NFWC_DIM` | 231 | 本機已有 |
| 專家包 | `packai_sandbox_e9e` | 233 | 本機已有 |
| **REI-only 降級案例** | `packai_sandbox_universio`（REI） | 177 | 本機已有 |
| **REI-only ＋重科技（下載）** | Modrinth `technical-electrical` v2.12.10（Mekanism／Thermal／IE／Create／Botania） | 78 mod | 已篩，待裝 |
| **EMI-only 案例（下載）** | Modrinth `tensura-suraimu` v2.0.6（魔法向） | 61 mod | 已篩，待裝 |
| 迷你基準（下載） | Modrinth `minimalcreate` v1.1（Create only，最快） | 13 mod | 已篩，待裝 |
| 小型科技（下載） | Modrinth `minehattan-project` v5.0.6（Mekanism／IE／RFTools／PneumaticCraft） | 43 mod | 已篩，待裝 |
| 魔法＋任務（下載） | Modrinth `witch-hunt` v0.1.1（Occultism／Ars Nouveau） | 79 mod | 已篩，待裝 |
| 無檢視器極端案例（下載） | Modrinth `create-evil-awake`（129 mod，**冇偵測到 JEI／REI／EMI**） | 129 mod | 已篩，待裝 |

篩選數據：`docs/research/artifacts/2026-10-05-modrinth-sample-screening.json`（12 包，只讀 .mrpack 清單檔，冇裝）。

（其他本地包 ATM10＝1.21.1、E2E／Nomifactory／DJ2＝1.12.2，唔屬 packai 範圍。）

## 5. P0 量測（開工閘；兩個包各一次）

量：① 類別數／配方總數 ② 固定比例 vs 機率輸出的比例 **M/N**（決定 §3 邊種講法能覆蓋幾成）③ NBT 覆蓋率 ④ catalyst 覆蓋率 ⑤ 首次掃描時間／記憶體／cache 大小 ⑥ `+1 tool` 對既有 tool 選擇準確率。
方法：優先用既有 dump／report；否則獨立 branch 嘅 temporary instrumented build（明標非出貨）。**唔量完唔准寫產品碼。**

## 6. 驗收

1. 題庫 A1–A5、H2–H3 逐條喺 **ATM8（科技）＋NWFC（魔法）** 各跑一次；trace 為證。
2. 答案內每個 item／數量／機器出現喺本輪工具結果；算唔到＝「我唔確定」；有機率＝跟 §3 講法。
3. `compileJava compileTestJava` SUCCESSFUL；`tests/check_*.py` 對 baseline 零新增紅。
4. 求解器 Java harness：固定比例／循環／機率／缺資料四類。
5. 玩家文字零檔名／行號／raw path；三語齊。

## 7. 風險

| 風險 | 對策 |
|---|---|
| 配方量巨大卡遊戲 | P0 量；背景建圖；分批 cache（fingerprint 含 code 版本） |
| 機率無結構化數據 | §3「另有機率加成，數值未有資料」；唔准估 |
| 循環鏈 | 迭代上限 + 標明 |
| 檢視器 API 差異 | `RecipeSource` 介面；Phase 1 只 JEI |
| tool 爆炸（現 14 個） | 先量選擇準確率，跌就合併 |

## 8. Review 記錄

- R1 **3:7**（12 blocker）→ v2 修 7；R2 **4:6**（N1–N7）→ v3 修；R3 **4:6**（未達 8:2，停手交 SK）。
- SK 覆核後指示：通用／先做 1 類／檢視器抽象／區間輸出 → v4。
- v4 相對 v3 主要變動：**範圍由 9 類收窄到 A＋H**；新增檢視器抽象層；新增 §3 區間輸出契約；樣本包由 2 個擴到 5 個（含 REI 降級案例）；刪除 jar→modid 索引。
- 已用 3 輪；R4 為上限內最後一輪。
