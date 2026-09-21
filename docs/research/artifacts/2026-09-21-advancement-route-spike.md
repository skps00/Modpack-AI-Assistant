# A 案 Spike —— 成就（inventory_changed）取得途徑候選質量（v2 修正版）

- 日期：2026-09-21（DS 空閒時段）；**v2 修正**：修好 3 個腳本 bug 後重算
- 目的：R3 停手後，SK 要「1+3」→ 先用真 data 驗 A 案**值唔值得做**（零 code 改動）
- 工具：`%LOCALAPPDATA%\Temp\spike_adv_routes_v2.py`（純 Python 讀 zip、**結構化 JSON parse**）
- 資料源：真 instance `AI_test_NFWC_DIM`（231 jar；item-index 17 檔／12,521 unique id）

## 0. 腳本 bug 記錄（誠實）

| # | Bug | 影響 | 發現者 |
|---|---|---|---|
| 1 | 條件③ `rewards.recipes` 分支寫成 `"rewards" in json.dumps(rewards)` → **恆 false（死碼）** | 漏 10 條 `rewards.recipes` 型成就 | R1 verifier |
| 2 | recipe regex `\{.{0,400}?\}` 遇 NBT 逃逸大括號**提前截斷** → 有配方嘅物品被當無配方 | 假陽性 | R1 verifier |
| 3 | 分支次序：`data/<ns>/advancements/recipes/**` 同時含 `"/recipes/"` → 被配方分支先吞（v1 無、v2 初版有） | adv_json 由 7,692 跌到 1,129 | 我自己（修完先發現） |

⇒ 教訓：**凡掃描類結論要有「獨立實作重算」先算證據**（今次兩個 bug 都係靠第二實作揾出）。

## 1. 漏斗（兩套獨立實作，數字有差）

| 階段 | 我 v2（per-item 判定） | R1 verifier（per-advancement 判定） |
|---|---|---|
| advancement JSON（`data/**/advancements/**`） | 7,692 | 7,717 |
| ① `/advancements/recipes/**` | −6,563 | −6,588 |
| ③ `rewards.recipes`／`recipe_unlocked` | −10 | −10（verifier 講原 `−7` 實為 10） |
| ④ 無 `display` | −13 | −13 |
| ⑤ `inventory_changed` 命中 | **497** | **494** |
| ⑥ `requirements` 非單元素 | −578（per-item） | −258（per-advancement） |
| ⑦ 唔在 item-index | −28 | −19 |
| ⑧ 已有配方結果（結構化收集 `result/output/results`） | −378 | −173 |
| **最終候選** | **114 條／111 物品** | **44 條／44 物品** |

**差異原因**：①⑥⑧ 用 per-advancement vs per-item 兩種語意（一條成就多件物品時行為唔同）；②配方產出收集覆蓋率唔同（我收 6,723 ids、verifier 較闊）。
⇒ **結論：A 案 plan 必須寫死 corpus（jar 清單 hash＋item-index 檔名）＋predicate（per-item 定 per-advancement）＋配方產出收集規則（走 `result/output/results` 結構化）**，否則驗收冇可重現性。

## 2. 樣本質量（我 v2 抽 10／114；seed 20260921）

| # | 成就 | 物品 | 標題（jar lang） | 判定 |
|---|---|---|---|---|
| 1 | `main/petrified` | `unusualprehistory:petrified_wood_log` | — | 真取得（結構／事件） |
| 2 | `alexsmobs/shattered_dimensional_carver` | `alexsmobs:shattered_dimensional_carver` | 通往世界尽头的门票 | 真取得（boss 掉） |
| 3 | `graveyard/black_bone_staff` | `graveyard:black_bone_staff` | 诺托克·古尔之杖 | 真取得（boss 掉） |
| 4 | `alexsmobs/lost_tentacle` | `alexsmobs:lost_tentacle` | 需要付出一次小代价…… | 真取得（生物掉） |
| 5 | `graveyard/purple_bone_staff` | `graveyard:purple_bone_staff` | 夏普诺克·古尔之杖 | 真取得（boss 掉） |
| 6 | `magic_map` | `twilightforest:filled_magic_map` | — | 真取得（任務／事件） |
| 7 | `graveyard/corruption` | `graveyard:corruption` | 邪恶之源 | 真取得（boss／事件） |
| 8 | `irons_spellbooks/staff_root` | `irons_spellbooks:blood_staff` | No Longer Short-Staffed | 真取得（boss 掉） |
| 9 | `alexsmobs/fish_bones` | `alexsmobs:fish_bones` | 鸡蛋里挑骨头 | 真取得（釣魚／事件） |
| 10 | `dungeons/all_trophies` | `dimdungeons:item_trophy_5` | — | 真取得（地牢） |

**10/10 都有真取得含意、0 條噪音**（比 v1 樣本更乾淨；v1 嘅 `create:blaze_burner` 喺 ⑧ 修正後已被剔）。

## 3. 結論

- **A 案值得做**：候選 44–114 條（定義而定）、質量高（抽樣 10/10 真取得）、正正補「打王掉／事件取得」缺口。
- **前置依賴**：① 條件⑧要**結構化 parse**（唔可以 regex）；② **新條件⑨**（mcmod 核實：已有非自掉 loot 途徑 → 唔出 A 行，例 `command_block_book`＝凋靈共生體 100% 掉落）；③ **`MAX_FACTS_PER_ITEM=8`** 硬上限會令 A 行靜默丟棄（真 cache 471 件物品頂 8）→ 必須同 **C2（cache 版本）** 一齊做。
- **必須寫死**：corpus（jar 清單 hash＋item-index 檔名）／predicate（per-item vs per-advancement）／配方產出收集規則，否則驗收不可重現。

## 4. mcmod 對照（同日）見 `2026-09-21-mcmod-crosscheck.md`
