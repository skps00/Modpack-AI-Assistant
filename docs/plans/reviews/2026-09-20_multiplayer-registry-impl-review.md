# Review：乙線實作計畫（多人 registry 驗證）
日期 2026-09-20｜對象：`docs/plans/2026-09-20-multiplayer-registry-impl.md`｜協議：反方 → 正方 → 中立裁判

## R1 反方（最強攻擊）
- **A1 測試主體唔確定**：沙盒 358 mods ≠ FTB manifest 304 → 版本配對可能失敗；plan 冇定死 fallback 次序。
- **A2 驗收標準有天生缺陷（最強）**：基準 545／100／94 係「某世界／已載入 chunk」量到嘅數字，會受 seed／載入範圍影響；若用 ±0 硬比，專用伺服器（新世界）必然唔同 ⇒ **會產生假失敗**。
- **A3 javap 證據未夠**：只證明「server 送咗一份 `RegistryAccess$Frozen`」，未證客戶端**保留** worldgen layer（客戶端可能只留 biome／dimension type）。
- **A4 可能白做**：若 A2/A3 成立，花 2–3.5 小時都答唔到問題。
- **A5 改 packai code**：需 cursor-agent 改 `ClientSetup.java`，有污染工作樹風險。
- **A6 偏離 SK 指示**：manifest 路線＝官方 App 做法，但**唔係**「官方 server pack zip」（SK 明示用官方 server pack）。
- **A7 未考慮 Forge 自家 registry sync**（`NetworkRegistry`）會唔會補上 worldgen → 影響判讀。

## R1 正方
- A1 → 已寫「先配對，配唔到就換包」；補：**定死次序 FTB → Star Technology → ATM8**（ATM8 有官方 server pack，正合 SK 指示）。
- **A2 → 接受，屬實質缺陷**。修法：驗收分兩層——**主判準＝registry entries size**（同 seed／世界無關，必然可比）＋`registryOrThrow` 唔可以 throw；chunk 觀察值降級做**參考**，唔可以當 fail。已寫入 plan v2。
- A3 → 加一項決定性檢查：同一 run 內報 size ＋ 呼叫唔 throw ＋ 反查命中；若客戶端 registry 空／唔存在，會即時現形（唔會誤判）。
- A4 → 修正後答案係乾脆 yes/no，時間值得。
- A5 → 白名單一個檔＋跑完即還原＋記錄 jar sha（流程上次已驗證可行）。
- A6 → 承認並上報 SK：manifest＝官方清單（同 FTB App 裝 server 一樣），但若 SK 要「現成 server pack zip」就用 ATM8。
- A7 → 加註：如果客戶端拎到，要留意可能同時有 vanilla login blob 同 Forge sync 兩條路（判讀寫清楚）。

## R1 中立裁判
- **勝方：正方（守住方向），但反方 A2 為實質缺陷** → 本輪**有實質修改**（驗收標準改為 registry 層為主），非空轉。
- **比分：正方 8.2 : 反方 1.8**（≥8:2 門檻，過）——過門檻嘅關鍵係 A2 修正已入 plan。
- **最大未知（要實測解開）**：1.19.2 連專用伺服器時，客戶端 `PLACED_FEATURE_REGISTRY` 究竟有冇 entries（javap 只證「物件送咗」，未證「layer 內容」）。
- **反轉條件（flip conditions）**：
  1. `registryOrThrow(PLACED_FEATURE_REGISTRY)` throw 或 size==0 ⇒ 結論改為「乙唔可以喺多人用」。
  2. 版本配對失敗且三個包都唔成 ⇒ 停手問 SK，唔准硬試第四個包。
  3. 若 FTB manifest 路線要 SK 出手（例如要 CF 存取）⇒ 停手先問。
- **未達標風險**：如再有新證據推翻 A2 修正或出現新缺陷，才開 R2；否則可開工。
