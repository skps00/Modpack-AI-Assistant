# Plan — Route cache 版本失效（C2）＋ 噪音過濾補窿（C3）

- 日期：2026-09-21；作者：Hermes（SK「go」）
- 狀態：**v1，待 review**
- 性質：**correctness（唔止文字）** —— 與「玩家睇得明」plan（`2026-09-21-obtain-advancement-and-honest-wording.md`）分開 review。

## 1. 問題（真 instance 實查）

1. **C2 cache 永不因 code 更新而失效**
   - `.../instances/AI_test_NFWC_DIM/minecraft/config/packai/jar-cache/` 全部檔 mtime **2026-08-08 09:10**（6 星期前），cache key 只認 **jar 檔 hash**（`manifest.json`）。
   - 後加嘅噪音過濾（`LootForwardIndex.isTrivialBlockSelfLoot`，被 `JarLightIndex.java:270` 使用）對舊 cache **無效** ⇒ 舊結論一直沿用。
   - 量化（本回合親手掃 230 個 cache 檔）：`L|` route 共 **12,198** 條，其中 **2,146 條（17.6%）** 係「挖方塊掉返自己」噪音（例：`ars_nouveau:ritual_brazier`、`goety:apparition_door`、`goety:arca`）。
   - 後果：玩家會見到無意義「取得途徑」；gap 面板亦會照樣顯示（真機 trace 已見）。
2. **C3 噪音過濾覆蓋不足**
   - cache 內出現以 **loot function／type／condition id** 做 item key 嘅記錄：`minecraft:survives_explosion`（另見 `minecraft:item`／`minecraft:block` 類同源字串會被 `ITEM_ID` regex（`JarLightIndex.java:52-53`）一概當 id 抓）。
   - 即 `PackIndex.isNoiseItemId` 對呢類「非物品 id」覆蓋不足。

## 2. 目標

- **C2**：cache 加**版本簽名**（唔止 jar hash）——至少包括：索引格式版本、`JarLightIndex` 掃描邏輯版本、噪音過濾版本；簽名不符 → **重建**（唔係沿用）。
- **C3**：`isNoiseItemId`／掃描端補窿：loot function／condition／type id（`minecraft:*` 非物品 ns:path 白名單外）唔准做 item key；加斷言紀錄。

## 3. 設計草案（待 review 決定細節）

- **C2-a**：`JarLightIndex` 掃描常數加 `INDEX_VERSION`（手動 bump 或由過濾器特徵字串導出）；`manifest.json` 記錄 `indexVersion`；讀 cache 時唔一致即重建。
- **C2-b**：重建要有**上限**（唔可以一次過掃爆）：沿用現有 fingerprint 分片（每 jar 一檔）；重建時間實測門檻（231 jar 全掃 ≈ 0.39s，成本可接受）。
- **C2-c**：一次性清理：偵測到舊版 cache → 重建並記錄（log 一行 N 檔重建）。
- **C3-a**：`isNoiseItemId` 加規則：`minecraft:survives_explosion`／`minecraft:item`／`minecraft:block`／loot table type 詞（可 grep 真 cache 取得完整清單，唔准靠估）。
- **C3-b**：掃描端加白名單概念：只有「在本包 item index 出現過」嘅 id 才可做 item key（同「玩家睇得明」plan §2.2-7 共用同一 helper，避免兩套）。

## 4. 驗收標準（開工前定）

1. **版本失效測試**：造一個舊版 cache（`indexVersion` 較舊）→ 啟動掃描 → 斷言 cache 被重建、且噪音 route 消失（數量下降以真 instance 數據為期望值）。
2. **噪音清零**：對真 instance cache 重跑掃描後，`L|blocks/<自身路徑>` 類 noise 應由 **2,146 → 0**（或列出仍剩者及理由）。
3. **負控**：`minecraft:survives_explosion` 類 id **唔可以**成為 item key（逐條紅→綠）。
4. **回歸**：`*Check` 全綠；`tests/check_*.py` 124 檔零新增紅；真機一輪 trace 斷言 gap 面板唔再出現自掉噪音。
5. **可還原**：cache 重建屬可重建資料（刪 cache 即回復原狀），但**唔准**未經 SK 清 SK 真 instance 嘅 cache —— 先用沙盒副本驗。

## 5. 風險／還原

- 風險：重建期間大量 I/O（實測全掃 <1s，可接受）；版本簽名寫錯會每次重建（成本可控）。
- 還原：cache dir 係 derived data，刪除後自動重建；改動全部在 git。
- **唔准**：未經 SK 批准去改／刪 SK 真 instance 嘅 cache 或 jar。

## 6. 已知限制

- 只修「自掉噪音」＋「非物品 id」兩類；其他 route 品質問題（例如 quest 誤配）不在本 plan。
- 依賴「玩家睇得明」plan 嘅 helper（若該 plan 改動 helper 簽名，本 plan 要同步）。
