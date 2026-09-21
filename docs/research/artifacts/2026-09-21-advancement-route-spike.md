# A 案 Spike —— 成就（inventory_changed）取得途徑候選質量

- 日期：2026-09-21（DS 空閒時段）
- 目的：R3 停手後，SK 要「1+3」→ 先用真 data 驗 A 案**值唔值得做**（零 code 改動）
- 工具：`%LOCALAPPDATA%\Temp\spike_adv_routes.py`（純 Python 讀 zip；輸出 `spike_adv_routes\candidates.json`）
- 資料源：真 instance `AI_test_NFWC_DIM`（231 jar／7,717 advancement JSON；item-index 12,521 ids）

## 1. 條件漏斗（plan v3 §2.2 八條，條件⑧按 R3 修正用配方檔 `result|output|results`）

| 階段 | 剔除 | 剩 |
|---|---|---|
| advancement JSON 總數 | — | 7,717 |
| ① `/advancements/recipes/**` | −6,588 | — |
| ③ `recipes/root` parent／`recipe_unlocked`／`rewards.recipes` | −7 | — |
| ④ 無 `display` | −13 | — |
| ⑤ 有 `inventory_changed` criteria | — | 497 |
| ⑥ `requirements` 非「單 group 單元素」 | −260 | — |
| ⑦ 焦點 id 唔在包 item-index | −19 | — |
| ⑧ 已有配方結果（`result/output/results` 命中） | −172 | — |
| **最終候選** | | **46 條／46 件物品** |

（R3 反方獨立掃描報 47 條 → 同一量級，差異 1 條屬 nested 格式處理差異。）

## 2. 隨機抽樣 10 條（seed 20260921，全部人手判讀）

| # | 成就 id | 物品 | 成就標題（zh_cn） | 判定 |
|---|---|---|---|---|
| 1 | `materials/vobrite_crystal` | `art_of_forging:vobrite_crystal` | Crystallized Storm Clouds | 真取得（探索／事件） |
| 2 | `goety/get_stormlander` | `goety:stormlander` | 暴风之力 | 真取得（boss／儀式） |
| 3 | `graveyard/white_bone_staff` | `graveyard:white_bone_staff` | 扎特拉克·古尔之杖 | 真取得（boss 掉落） |
| 4 | `graveyard/corruption` | `graveyard:corruption` | 邪恶之源 | 真取得（boss／事件） |
| 5 | `main/infinite_potential` | `witherstormmod:command_block_book` | 潜能无限！ | 真取得（打 wither storm 後） |
| 6 | `graveyard/upper_bone_staff` | `graveyard:upper_bone_staff` | 残缺的部分 III | 真取得（boss 掉落） |
| 7 | `dungeons/find_bell_1` | `dimdungeons:item_secret_bell` | （key 未在 lang） | 真取得（地牢探索） |
| 8 | `burner` | `create:blaze_burner` | 活炉 | 有爭議（機器生產，可能唔算「取得途徑」） |
| 9 | `adventure/at_the_last_minutes` | `wares:completed_delivery_agreement` | At The Last Minutes | 真取得（事件完成） |
| 10 | `acquire_wither_waltz` | `bygonenether:wither_waltz_music_disc` | 跳一支华尔兹 | 真取得（boss 掉落） |

**判定：9/10 真取得途徑、1/10 有爭議；0/10 純噪音。**

## 3. 結論

- **值得做**：候選量（46）細、質量高，而且正好覆蓋 SK 見到嘅「打王掉」類缺口（witherstormmod、graveyard、bygonenether）。
- 但 R3 卡死點未解：(a) 條件⑧ 要用**配方檔 `results`**（本 spike 已實作並生效，剔除 172 條）；(b) `MAX_FACTS_PER_ITEM=8` 硬上限會令 A 行靜默丟棄（真 cache 471 個 item 頂 8）；(c) cache `manifest.v` 只寫不讀（舊 cache ⇒ A 行永不生效）→ 建議 A 案**連 C2 一齊做**。
- 未做：`create:blaze_burner` 類「機器生產」係否應排除（可加條件⑨：criteria item 有 `block` tag／屬機器方塊 → 排除）；留待 A 案 plan 決定。
