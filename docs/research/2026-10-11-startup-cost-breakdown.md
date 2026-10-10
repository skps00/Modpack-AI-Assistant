# 啟動固定開銷拆解（Slice 3，只量唔改）· 2026-10-11

- 目的：`docs/plans/2026-10-10-real-machine-loop-first-principles.md` §3 Slice 3 —— 由真機 `latest.log` 時間戳拆開「開一次沙盒要幾耐」，決定值唔值得壓。
- 資料來源（唯讀、可重跑）：`%TEMP%\packai_smoke_results_packai_sandbox_20261010-192227\latest.log`（2026-10-10 19:22 沙盒 `packai_sandbox`（NC 231 mods、Forge 1.19.2）真機 run，3 條 case，jar `autotest-dev-0.2.3.jar`）。
- 方法：抽 log 內關鍵里程碑嘅真時間戳（`[10Oct2026 19:22:35.636]` 格式），相對 JVM 啟動計差。

## 量到嘅拆解（相對 ModLauncher 起跑 = 0.00s）

| 階段 | 相對時間 | 該段耗時 |
|---|---|---|
| JVM／ModLauncher 起飛 | 0.00s | — |
| Forge mod loading 開始 | 19.10s | **19.1s** |
| Pack AI（mod）載入完 | 19.22s | 0.1s |
| 資源包／ModernFix 階段 | 47.81s | **28.6s** |
| 音效引擎起動 | 62.67s | 14.9s |
| 世界載入完成段 | 79.03s | 16.4s |
| JEI 開始掃描 | 96.47s | 17.4s |
| **第一條 case 開始**（trace 檔 `ask-20261010-192417`） | ≈101.4s | 4.9s |

⇒ **固定開銷（JVM 起到第一條 case）≈ 101 秒**；最大三塊＝mod loading（19s）＋資源/ModernFix（29s）＋世界＋JEI（34s）。

## 同舊成本模型唔一致（要留意，唔准當已解決）

- `real-machine-loop-first-principles.md` §1.2 用「3 次 run、14 條 case」算出 `wall ≈ 36 s ＋ N × 12 s`；今次同一沙盒嘅 log 顯示 **淨 boot 已 ~101s**（未計 deploy／開窗／收尾）。
- 今次 run 嘅 driver 數字：`wall_s = 169.3`、harness `elapsedMs = 53.5s`、逐 case 合計 `17.0s`。三者用嘅起點唔同（driver = 由命令開始；harness = 由世界 ready；log = 由 JVM），**唔可以直接互相減**。
- ⇒ 結論：**下一步真機 run（22 條 case）要同時記 driver wall／harness elapsedMs／log 里程碑**，全部對齊之後才定「壓 boot 值唔值」。而家只可以講：boot 係 **~100 秒**級，唔係 36 秒 —— 呢個令 L2（一次 launch 跑多條 case）**更值錢**。

## 淨結論（唔改任何 code）

1. 一次 launch 嘅固定成本 ≈ 100 秒級（實測 101.4s 到第一條 case）。
2. 每條 case ≈ 8–12 秒（ask 時間）——即 22 條 ≈ 101 + 22×10 ≈ **4.7 分鐘**；60 條 ≈ **11–12 分鐘**（同 plan §1.2 對 60 條嘅 11.8 分鐘結論一致）。
3. 要壓 boot 只可以壓三大塊（mod loading／資源包／世界＋JEI），全部屬 modpack 本體，唔屬 packai 可控制範圍；**建議唔做**（只記錄）。
