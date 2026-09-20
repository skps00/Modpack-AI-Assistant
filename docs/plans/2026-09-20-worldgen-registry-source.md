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

## Probe 實測結果（2026-09-20，第一步：可行性）— 已做，數據如下

**方法**：臨時 probe 注入 `ClientSetup.onClientTick`（一次性，mc.level 出現後量一次），build `-PpackaiAutotest` jar → 部署 FTB 沙盒（`packai_ftb`）→ GUI 載入世界 → 讀 `logs/latest.log`。**量完即刻還原檔案**（sha 回到基線 `24a91d1200a2`，jar 內零 probe 痕跡，乾淨 jar 已重部署）。

**真機輸出**（FTB Skies Expert，359 mods，Overworld）：
```
[probe-registry] dim=minecraft:overworld placed=545 orePlaced=100 biomes=95                  biomesWithOre=94 featureSets=1034 ms=11                  probeBlock=thermal:silver_ore hitPlaced=1 hitSample=thermal:silver_ore
```

**讀數意義**：
- `placed=545`：**client 側真係讀得到 dynamic registry**（`Registry.PLACED_FEATURE_REGISTRY`），數目 545 —— 對比檔案掃描只抽到 91 placed（同一包）⇒ registry 覆蓋率高一個量級。
- `orePlaced=100`／`biomesWithOre=94 / 95`：礦物 feature 同 biome 覆蓋率極高（94/95 biome 有 ore feature）。
- `ms=11`：整次掃描（placed + biome 全走一次）**11 毫秒** ⇒ 成本可忽略，可以每次入世界做一次。
- `probeBlock=thermal:silver_ore hitPlaced=1`：**「礦物 block → placed feature」反查可行且精準**（`OreConfiguration.targetStates[].state.getBlock()` 比對 block id，命中 1 個 placed feature，key 就叫 `thermal:silver_ore`）。

**未驗（probe 涵蓋唔到，唔可以當已證）**：
1. **多人／專用伺服器**：客戶端係唔係一樣收到完整 PLACED_FEATURE registry（1.19.2 dynamic registry 同步行為未測，需要真 server 環境）。
2. **in-world 實際分佈**（seed／chunk 級）——registry 只提供 feature 定義，唔提供實際生成位置（要 seed 級數據仍要靠其他來源）。
3. **跨版本**：本 probe 只喺 1.19.2 Forge 跑過；1.19.3+（`Registries.*` 類名）／NeoForge 未測。

**結論（暫時）**：單人世界內「以 runtime registry 做主來源」**技术上可行、成本 11 ms、覆蓋率遠勝檔案掃描**；但升級做主來源之前，一定要先解上面第 1 點（多人），因為 packai 現時係 client-side、玩家實際好多時連 server。
