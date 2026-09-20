# Pack AI — 跨模組包測試範圍（registry）

Last updated: 2026-09-20 · Owner: packai (`super_minecraft_AI_player`) · Loaders: **Forge 1.19.2**（主線）／NeoForge 1.21.1（**暫停**）

## 0. 鐵則

1. **只可以郁沙盒副本**（`packai_sandbox_*`）；SK 玩緊嘅 instance（例：`AI_test_NFWC_DIM`、`Star Technology`、`All the Mods 8 - ATM8`、`UniversIO`、`Enigmatica 9- Expert - E9E`）**零觸碰**。
2. 部署 jar 只准用 `hermes/scripts/mc_mod_deploy_jar.py`（遊戲開住會 REFUSED）。
3. 每次跑都要**重新隨機抽樣**（唔准重用上一輪物品）＋要跨包（每個沙盒至少一個正向案例 + 一個誠實 miss 案例）。
4. 每個結論要附 **trace 檔名／log 行**；唔准靠肉眼或自報。
5. 沙盒 config：`scanModJars = true`、`askTraceJsonl = true`、`recipeCategoryOrder` 清空（等各包自己學）。

## 1. 沙盒清單

| 沙盒 instance | 來源包（SK 嘅 instance，唔郁） | MC／loader | 包 mods | placed／configured | inline ore | kubejs 檔 | 用途 | 狀態 |
|---|---|---|---|---|---|---|---|---|
| `packai_sandbox_ftb` | FTB Skies Expert | 1.19.2 / Forge 43.4.5 | 357 | 93（真 placed 91；2 個係 ars_nouveau tag）／71 | — | — | 主驗收（worldgen 三類＋必答清單＋卡落位） | ✅ 已多輪驗收（09-20） |
| `packai_sandbox_universio` | UniversIO | 1.19.2 / Forge 43.2.10 | 176 | 6／21 | 0 | 300 | 「包冇 worldgen 資料」老實答、跨包姿勢 | ✅ 7/7（09-20） |
| `packai_sandbox_e9e` | Enigmatica 9 Expert | 1.19.2 / Forge 43.4.23 | 233 | — | — | — | 專家包（任務鏈／改配方） | ✅ 沙盒已存在（09-19） |
| **`packai_sandbox_startech`** | **Star Technology** | **1.19.2 / Forge 43.3.9** | **154** | **28／15** | **9** | **107** | 新：科技包＋有 inline ore 個案 | 🆕 2026-09-20 建立（jar＋config 就位，待首次 smoke） |
| **`packai_sandbox_atm8`** | **All the Mods 8 (ATM8)** | **1.19.2 / Forge 43.2.14** | **379** | **448／420** | **10** | **284** | 新：**最大 stress test**（placed 數量最多、ore 路線最多、有 inline 個案、FTB quests） | 🆕 2026-09-20 建立（jar＋config 就位，待首次 smoke） |
| `packai_sandbox` | （dev 沙盒 junction） | 1.19.2 / Forge 43.3.5 | 231 | — | — | — | 舊 dev 環境 | ⚠️ dev 路線已放棄 |

沙盒 jar：`packai-autotest-dev.jar`（sha `b5ffe2761cea…`＝含 a+b ＋ P0／P1 修正嘅 autotest build）。

## 2. 已下載但**唔可以**入 packai 測試範圍（附理由）

| instance | MC／loader | 唔入範圍理由 |
|---|---|---|
| `All the Mods 10 - ATM10(1)` | 1.21.1 / NeoForge 21.1.241（489 mods） | packai 1.21.1 線**暫停**（2026-09-14 決定，見 GitHub issue #20）。要恢復先可以測。 |
| `All the Mods 10 - ATM10` | 1.21.1 / NeoForge 21.1.228 | 同上（內有舊 packai 1.21.1 jar，已停用） |
| `Enigmatica 2- Expert - Extended(1)` | 1.12.2 / Forge 14.23.5 | packai **冇 1.12.2** build |
| `Nomifactory CEu upgrade`（＋其他 Nomifactory 副本） | 1.12.2 | 同上 |
| `龙之冒险：新征程 v2.1／v2.2`、`Project Architect 2`、`神秘启旅客户端`、`All the Mods 9 - To the Sky` | 1.20.1 / Forge 47.x | packai 冇 1.20.1 build |
| `Not Too Complicated 2` | 1.16.5 | 冇 build |
| `Multiblock Madness 2`、`Create-*` | 1.18.2 | 冇 build |
| `茶樓客戶端(fabric)`、`盤靈古域`（fabric） | 1.19.2 / **Fabric** | packai 只支援 Forge（1.19.2）／NeoForge（暫停） |

（如需 1.12.2／1.20.1 支援 = 新版本線，屬大工程，另開 plan。）

## 3. 新沙盒建立步驟（可重現）

1. `robocopy <來源 instance> <packai_sandbox_xxx> /E /XD logs crash-reports screenshots saves journeymap`
2. 部署 jar：`python "%LOCALAPPDATA%\hermes\scripts\mc_mod_deploy_jar.py" --jar <來源 jar> --mods <沙盒>\minecraft\mods --name packai-autotest-dev.jar`
3. Config：由 `packai_sandbox_ftb\minecraft\config\packai-client.toml` 複製 → 改 `scanModJars = true`、`askTraceJsonl = true`、`recipeCategoryOrder = ""`
4. 首次 smoke：開遊戲（背景／最小化，過 SK 活動 Gate）→ 確認 `minecraft/logs/latest.log` 有 packai 載入、`minecraft/packai/trace/` 開始寫 → 關遊戲。
5. 記入本檔表格（狀態改 ✅）＋ HANDOFF。

## 4. 抽樣方法（每次重新隨機）

**工具：`tools/make_cases_pack.py <game_dir> [N] [--seed S]`**（每次跑都 `random.seed()` 重新抽；寫 `<game>/packai/autotest/cases.json`，並即場斷言首行逐字 `{"packaiAutotest":1,` —— 呢個 magic 唔啱 harness 會靜默唔跑）。

- 正向（worldgen）：抽「該包 **ore feature** ∩ **lang 有宣告嘅物品**」交集。
  **2026-09-20 修正盲點**：舊 sampler 只讀 `configured_feature` 檔 ⇒ **完全抽唔到 inline 定義嘅礦**（Thermal 系：nickel／apatite／sulfur／lead／tin／oil_sand…），即「缺礦脈大小」嗰批物品本身抽唔到。新工具連 inline（object-form）feature 一齊掃，所以 pools 大咗：startech 18／atm8 52／ftb 41。
- 誠實 miss：`minecraft:iron_ore`（原版控制組）＋（可選）一個非礦物物品。
- 每包實測 pool：`packai_sandbox_startech` ore 30／items 15702／交集 18／inline 檔 9；`packai_sandbox_atm8` 115／28985／**52**／10；`packai_sandbox_ftb` 53／18364／41／10。
- 每次跑都要附 trace 檔名；唔准重用上一輪抽到嘅物品。
- ⚠️ harness **每個 JVM session 只讀一次** `cases.json` ⇒ 要重跑必須重開遊戲。

## 2026-09-20 新沙盒遊戲內 smoke（Star Technology／ATM8）— 已做

沙盒世界狀態：兩個包都係**用 GUI 喺包內新建世界**（harness 只 `loadLevel`、唔會自建；有效世界名 = **`New World (1)`**，
因為早前失敗載入留低一個空殼 `New World`（只有 `session.lock`），新建時 MC dedup 加 `(1)`）。

| 包 | cases | OK | NO_SAMPLE | 有 `WORLDGEN` 行嘅 trace | 備註 |
|---|---|---|---|---|---|
| Star Technology（186 mods, Forge 43.3.9） | 13 | **10** | 3 | 原版控制組 `minecraft:iron_ore` | cards 4–8；NO_SAMPLE = 唔係 JEI 輸出嘅物品 |
| ATM8（391 mods, Forge 43.2.14） | 13 | **8** | 5 | `alltheores:tin_block`、`minecraft:iron_ore` | cards 1–7 |

**結論**：兩包都通過 smoke（mod 正常載入、`ItemIndex` 建成 —— StarTech 27505／ATM8 34049 條、
JEI 卡片正常、答案 trace 正常、原版控制組照樣老實答）。

**方法教訓（已入 skill `minecraft-mod-in-game-autotest`）**：
1. `cases.json` 第一行要 `{"packaiAutotest":1,` **緊湊格式**，空格 = 靜默拒收。
2. `sample()` 只認 **JEI 配方輸出** ⇒ 礦石／原料會 `NO_SAMPLE`；抽樣要由**該包自己嘅
   `config/packai/item-index/*.json`** 揀可合成類 id（jar 交叉掃描會抽出唔存在嘅 id）。
3. `quitWhenDone=true` 之後要**等遊戲自己退出**，唔可以即刻 kill（殺喺存檔途中會整壞世界 datapack config）。
