# 計劃（只計劃，未開工）— Worldgen 資料改由 runtime registry 做主來源

Status: **PLAN ONLY — 等 SK go；第一步係「可行性 probe 實測」，量唔到就唔做。**
Date: 2026-09-20 · Owner: packai · Forge 1.19.2 · 來源：SK 2026-09-20 問「b seem better?」＋`docs/research/2026-09-20-ore-gen-viewers-landscape.md`

## 1. 目標（一句）

礦物世界生成資料由「掃 jar 內 worldgen JSON」升級為「讀 runtime registry 物件（`PLACED_FEATURE`／`CONFIGURED_FEATURE`／`BIOME`）**做主來源**，檔案掃描做 fallback」。

## 2. 為何（證據）

| 問題 | 檔案掃描（今日） | Registry（建議） |
|---|---|---|
| inline object feature（Thermal 式） | **睇唔到 ⇒ 冇 size**（10 個礦物／FTB） | 睇到（物件已解析） |
| tag 指向 feature | 睇唔到 | 睇到（tag 已解析） |
| 未來新寫法／array | 靜默漏（脆弱位） | 唔受影響 |
| biome↔feature 關聯 | 靠 dimension 檔＋啟發式（有歧義要剔） | `Biome#getGenerationSettings()` 精確 |
| 多人／伺服器 datapack | 只讀**玩家本機** jar（可能唔同） | 反映**實際**伺服器資料 |

業界同路線實證（我親讀源碼）：`Abbie5/emi-ores`（`FeaturesSender.java`：`registryOrThrow(Registries.PLACED_FEATURE)`＋`OreConfiguration`／`GeodeConfiguration`＋`Biome#getGenerationSettings`）、`Larsens-Mods/JEIWorldGen`（`OreGenData`：`size`／`CountPlacement`／`HeightRangePlacement`）。**兩家都做 server→client 同步**——呢點係最大未知（見 §3）。

## 3. 第一步：可行性 probe（唔准憑估）

要量到以下數字／事實先可以寫 implementation plan：

1. **客戶端讀唔讀到 registry？**
   - 單人：`Minecraft.getInstance().getSingleplayerServer().registryAccess()`／`level.registryAccess()` 有冇 `PLACED_FEATURE`（print 條數）；
   - 多人：connected server 嘅 registry 有冇同樣內容（同一 probe，連一個真 server 試）；
   - **若單人得、多人唔得** ⇒ 要唔要加網絡通道（同業界一樣）＝**另一個 plan ＋ SK 批准**，唔偷步。
2. **成本量測**：iterate 全部 biome × `getGenerationSettings().features()` ＋ 由 `OreConfiguration.targets` 解出物品 id，耗時（ms）＋記憶體；同今日 JSON 掃描對比。
3. **覆蓋率對比**：同一批物品（用 `tools/make_cases_pack.py` 抽）喺 ① 今日、② 甲（inline 修復）、③ registry 三種來源下各答到幾多（用真包 FTB／ATM8／Star Technology；要出數字表）。
4. **與現有管線相容性**：registry → 同一套 `[WORLDGEN]` raw 行嘅適配層係唔係可行（headless fixture 驗證一定要保住，唔准拆）。

## 4. 驗收（若 go）

- 適配層輸出同一格式 raw 行 ⇒ 現有 headless 檢查（`WorldgenRoutesCheck` 等）**逐字不變**；
- 來源優先規則寫死：「入咗世界／有 registry → registry 贏；標題畫面／registry 未 ready → 檔案掃描」；兩者不一致要可觀測（trace 記低用咗邊個來源）；
- 真機兩個包（FTB＋ATM8）前後對照；覆蓋率表（§3.3）要有改善數字。

## 5. 鐵則

- 未量到 §3.1 之前：**唔准改 production code**、唔准加網絡通道、唔准動現有 index。
- 唔准為咗 registry 路線而拆走檔案掃描（標題畫面仍然需要）。
- 全部改動照 `AGENTS.md`：cursor-agent 實作、Hermes 親驗、真 instance 唔郁。
