# 落點 A — 跨 pack 通用性測試（2026-10-10，SK 指示：DJ2 係 1.12.2，試其他 pack）

同一支工具、同一張查詢表（5 條 SK 原句），跑 4 個唔同 MC 版本嘅真 pack（**唔開遊戲**）。

| pack | MC | mods | javap 呼叫（上限 1500） | 耗時 | artifact | cap 觸頂 | lang 格式 | runtime 讀得到？ |
|---|---|---|---|---|---|---|---|---|
| DJ2 | 1.12.2 | 235 | 1443 | ~300 s | 21.3 MB | 否 | `.lang`(1.12.2) | **唔可以**（只 1/20） |
| ATM8 | 1.19.2 | 379 | 1405 | 292 s | 13.2 MB | 否 | JSON | 可以 20/20 |
| StarTech | 1.19.2 | 155 | 777 | 154 s | 8.1 MB | 否 | JSON | 可以（20/20） |
| ATM10 | 1.21.1 | 478 | 1500 | 332 s | 48.5 MB | **係** | JSON | 可以 20/20 |

## 逐條 query（候選數／runtime 可見／有無被 cap 截斷）

### DJ2（MC 1.12.2，235 個 jar）

| need_id | 你原句 | 候選 | runtime 可見 | cap 截斷 | 無證據 mod 數 |
|---|---|---|---|---|---|
| `jsu_nonstackable` | 儲不可堆疊物品 | 20 | 1 | 否 | 0 |
| `dig_area_100x90` | 清 100×90 範圍 | 20 | 1 | 否 | 0 |
| `white_concrete_factory` | 白色混凝土工廠 | 20 | 1 | 否 | 0 |
| `wireless_power` | 無線傳電 | 20 | 1 | 否 | 0 |
| `auto_crafting` | 自動合成 | 20 | 1 | 否 | 0 |

### ATM8（MC 1.19.2，379 個 jar）

| need_id | 你原句 | 候選 | runtime 可見 | cap 截斷 | 無證據 mod 數 |
|---|---|---|---|---|---|
| `jsu_nonstackable` | 儲不可堆疊物品 | 20 | 20 | 否 | 0 |
| `dig_area_100x90` | 清 100×90 範圍 | 20 | 20 | 否 | 0 |
| `white_concrete_factory` | 白色混凝土工廠 | 20 | 20 | 否 | 0 |
| `wireless_power` | 無線傳電 | 20 | 20 | 否 | 0 |
| `auto_crafting` | 自動合成 | 20 | 20 | 否 | 0 |

### StarTech（MC 1.19.2，155 個 jar）

| need_id | 你原句 | 候選 | runtime 可見 | cap 截斷 | 無證據 mod 數 |
|---|---|---|---|---|---|
| `jsu_nonstackable` | 儲不可堆疊物品 | 20 | 20 | 否 | 0 |
| `dig_area_100x90` | 清 100×90 範圍 | 8 | 8 | 否 | 0 |
| `white_concrete_factory` | 白色混凝土工廠 | 20 | 20 | 否 | 0 |
| `wireless_power` | 無線傳電 | 20 | 20 | 否 | 0 |
| `auto_crafting` | 自動合成 | 14 | 14 | 否 | 0 |

### ATM10（MC 1.21.1，478 個 jar）

| need_id | 你原句 | 候選 | runtime 可見 | cap 截斷 | 無證據 mod 數 |
|---|---|---|---|---|---|
| `jsu_nonstackable` | 儲不可堆疊物品 | 20 | 20 | 否 | 0 |
| `dig_area_100x90` | 清 100×90 範圍 | 20 | 20 | 否 | 0 |
| `white_concrete_factory` | 白色混凝土工廠 | 20 | 20 | 否 | 0 |
| `wireless_power` | 無線傳電 | 20 | 20 | 否 | 0 |
| `auto_crafting` | 自動合成 | 20 | 20 | 係 | 167 |

## 跨 pack 得出嘅事實（有 artifact 為證）

1. **跑得通 3 個 MC 版本**：1.12.2（DJ2）／1.19.2（ATM8、StarTech）／1.21.1（ATM10）——同一 CLI、同一查詢表，零 crash；javap 17 全跑得。
2. **版本差異正確反映喺 `runtime_visible`**：1.12.2 用 `.lang` ⇒ **DJ2 只有 1/20 個候選係 runtime 讀得到**；1.19.2／1.21 用 JSON lang ⇒ **20/20 都讀得到**。即係「呢個功能可否喺遊戲內答到」＝由 version 決定，唔係由 pack 決定。
3. **大包會撞 1,500 javap 上限**：ATM10（478 jar）`caps_hit=True`，最後一條 query **167 個候選攞唔到 bytecode 證據**（工具照樣誠實標 `unresolved`，唔係靜靜當 0）。ATM8 379 jar 用 1,405 次，啱啱好未撞。
4. **artifact 體積同 pack 大小成正比**：8.1 MB（155 jar）→ 13.2 MB（379）→ 21 MB（235，DJ2 bytecode 較肥）→ **48.5 MB（478）** ⇒ 唔可以入 git，亦要諗壓縮先可以餵落落點 B。
5. **精度問題每個 pack 都一樣**：候選係「關鍵詞命中之後按 jar 名排序、截 20 個」，**冇相關度排序**。例：ATM8 jsu 條 query 有真答案（`sophisticatedstorage`、`refinedstorage`），但同 `CraftPresence`、`hexerei`、`modernfix` 混埋一齊；StarTech 出 `ColossalChests`（合理）＋`DustrialDecor`（唔關事）。⇒ **候選清單唔可以當答案**，落點 B 之前一定要加排序／驗證層。
6. **成本**：每 pack 154–332 秒 CPU 重活（javap 為主），可以離線（唔開遊戲）跑。

## 檔案

- 摘要數據（呢個就係上面表）：`docs/research/artifacts/2026-10-10-capability-index-crosspack.json`
- 完整 artifact（每個 8–48 MB，**唔入 git**）：`%%TEMP%%\capindex_ATM8.json`／`capindex_StarTech.json`／`capindex_ATM10.json`／`capability_a_dj2_run2.json`
- 工具：`tools/mine_capability_index.py`；查詢表：`tools/capability_queries.json`
