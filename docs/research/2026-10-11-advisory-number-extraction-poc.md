# S-A 預檢（PoC）— 「每 tick 數字」到底抽唔抽得到？（2026-10-11，離線）

- 目的：回應 advisory-chain plan R1 反方嘅 **A2／A3**（「每 tick 實數唔存在／抽唔到」）。全部**本機實跑、唯讀**。
- 樣本：`packai_sandbox_atm8`（ATM8，380 jar）、`packai_sandbox_startech`（StarTech，155 jar）。

## 1. Config 掃描（機械規則寫死）

規則：行內同時有（a）數字、（b）速率／時間關鍵詞（`per tick|per second|/t|tick|rate|consumption|generat|burn|duration|speed|capacity|RF|FE|Joule|mB`），檔尾 `.toml/.json5/.json/.yaml/.yml/.snbt/.cfg/.txt`，跳過 `packai/`、`ftbquests/`、`jei/`、`emi/`。

| 包 | config 檔掃過 | 命中行（關鍵詞＋數字） | Top mod |
|---|---|---|---|
| ATM8 | **520** | **1,078** | industrialforegoing 172、Mekanism 147、ironjetpacks 136、byg 62、ae2 11、ars_nouveau 8 |
| StarTech | 169 | 148 | NuclearCraft 13、ae2 11 |

真實命中樣本（原文）：
- Industrial Foregoing：`#Amount of Power Consumed per Tick - Default: [40FE]`、`#Cooldown Time in Ticks [20 Ticks per Second] - Default: [100 (5s)]`
- Wireless Chargers：`# How much energy per tick per block should the Basic Wireless Block Charger transfer? 1 second = 20 ticks.`
- PneumaticCraft：`#Immersive Engineering: External Heater FE/t.`
- Mekanism（`config/Mekanism/generators.toml`）：`bioGeneration = "350"`（註釋：**Amount of energy in Joules the Bio Generator produces per tick**）、`defaultBurnRate = 0.1`、`burnPerAssembly = 1`（註釋：**Max Burn Rate = fuelAssemblies × burnPerAssembly**）、`maxFuelPerAssembly = 8000`、`heatedCoolantPerTank = 1000000`

⇒ **結論 1：per-tick／每次數字真係喺 config，而且部分連「公式」都寫喺註釋**（burn rate = 組件數 × 每組件）。

## 2. 反方指「抽唔到」嘅兩點，逐條核

| 反方指控 | 我嘅核實結果 |
|---|---|
| A2：Mekanism「只有 defaultBurnRate=0.1，實際 = GUI 設定 × 幾何；單位只在 lang」 | **部分成立**：有效 burn rate 依賴玩家 GUI 設定＋燃料組件數；但 config **同時提供** 起始值（0.1）、每組件增量（1）、上限（`maxFuelPerAssembly=8000`）＋範圍（0.001～1.0）⇒ 可以答「**默認／典型值＋假設講明**」，只係唔可以當「唯一實數」。單位多數寫喺同檔註釋（唔一定要靠 lang）。 |
| A3：L1 索引只收 string literal，唔收 int／float ⇒ 數字要 javap | **成立**：L1 index 設計上只收字串。但本機 **javap 可用**（`C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2/bin/javap.exe`），實測可讀 config 類別欄位＋常量（例：`biggerreactors Config$Reactor` → `FuelUsageMultiplier`、`FuelMBPerIngot`、`PerFuelRodCapacity`）。⇒ **bytecode 路線可行，但要另建抽取器（javap 慢：每個 class 一次 process，要池化／批次）**。 |

## 3. 仍然真嘅限制（唔可以扮有）

1. **部分數字只在 bytecode**（rftools RF/block、occultism miningTime、Create 轉速）——要 javap 路線。
2. **部分 mod 完全冇「每 tick」實數**（BiggerReactors 只有乘數、MysticalAgriculture 只有 drop chance、蜜蜂只有機率）⇒ 答案要**老實講「未收錄／只有機率」**。
3. **玩家可變項**（Mekanism burn rate、GT 電壓等級）⇒ 答案必須寫**假設**（同 SK 講嘅「be careful，玩家可能加條件」對應）。
4. **config 格式唔止 TOML**（ftbic＝`.snbt`、powah＝`.json5`、gtceu＝`.yaml`）＋有啲 mod 用**子目錄**（`config/Mekanism/` 14 檔）⇒ 掃描器要寫齊。

## 4. 判斷

- SK 嘅前提（「數字可以由 code／data 抽」）**成立**，但**唔係全部都抽得到**——所以要設計成「**抽到就答（附來源＋假設），抽唔到就誠實講未收錄**」。
- 反方 A2／A3 唔足以否決整條線，但**足以否決「精確 per-tick 唯一值」嘅承諾**；plan 要改成「默認值＋公式＋假設」。
