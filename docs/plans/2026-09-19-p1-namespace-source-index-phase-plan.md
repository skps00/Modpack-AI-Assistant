# P1 phase plan — 命名空間來源索引（「原廠 mod」vs「本包自加」）

> 母 plan：`docs/plans/2026-09-19-pack-content-awareness-full-plan.md` §2 P1
> 版本聲明：**只適用 MC 1.19.2 + Forge 43.x**（`neoforge` 樹 PAUSED，唔准郁）
> 狀態：計畫（未實作）；要過反方 review 先開工

## 1. 目標（一句）
令 packai 答得出「**呢件嘢／呢個 ns 嘅內容，係原廠 mod 帶嘅，定係本包（kubejs／pack）加嘅，定兩邊都有**」——今日完全答唔到。

## 2. 證據（真實測量，非估算）
- 量法：`%TEMP%\overlap_ns.py`（36 行，掃 `kubejs/{data,assets}` 目錄名 vs 已載入 jar 內 `data/<ns>/`、`assets/<ns>/` entry）
- 結果：**jar ns 224 個**／**kubejs ns 55 個**／**重疊 27 個**（例：`tetra` ＝ jar＋kubejs 都有）／**kubejs 獨有 28 個**
- 現況：`PackAiConfig.java:482` 明文「**Never scans kubejs/assets or kubejs/data**」⇒ 本包內容根本冇入索引
- 現有來源標籤機制：`AskReplyScrub.java:810-812` → `packai.label.src.<token>`（有 key ⇒ 顯示；無 key ⇒ 唔顯示）＝P4 先處理文字，**P1 只做索引**
- 玩家可見後果：亞巴頓／DLC ns（`golden_age` 1,204 檔等）全部係 kubejs 供，但 AI 只會當「普通物品」

## 3. 設計（擴充現有，唔另起爐灶）
新增 `logic/NsSourceIndex.java`（單一類），重用：
- **jar 側**：`JarLightIndex.java:144-145` 嘅 `ZipFile.entries()` 枚舉法（jar 清單重用 `JarLightIndex` 已有 cache）
- **kubejs 側**：照 `KubeJsMechanicScan` 嘅掃描骨架（`ensureStart(gameDir, maxFiles, maxBytes, maxMs)`）＋ `KubeJsMechanicScan.java:89-90` 上限範式；但**只讀目錄／檔名，唔讀內容**（同 mechanic scan 分開，避免互相污染）
- **三態（檔案級）**：`mod`（只喺 jar）／`pack`（只喺 kubejs）／`both`（兩邊都有）——重疊 27 個 ns 一律 `both`（唔准二分法講死）
- **查詢 API**（內部）：`NsSource.of(ns)` → 三態＋fileCount；`NsSource.forItem("ns:path")` → 用 ns 分類＋（如 `assets/<ns>/lang/*` 或 `data/<ns>/<...>` 有對應路徑）作 refine
- **曝露面**：`packai/trace` 一條 `ns.source`（`ns`／`state`／`jarFiles`／`kubejsFiles`）＋ `AskResult` 一個欄位（**唔加 lang key**；文字標籤留 P4）
- **成本護欄**：≤**20,000** 個路徑、≤**2 秒**、≤**1 MiB**；key 去重（`Set<String>`，用 `ns + "\\u0000" + relPath`）；超限即停並記 `ns.source.truncated=true`
- **快取**：寫入 `packai/cache/ns-source.json`，**fingerprint＝jar 清單 sha＋kubejs 頂層目錄 mtime＋parser 版本**（升版即失效，沿用 P2/N1 教訓）；唔准無限增長

## 4. 檔案白名單（review 確認）
- **新增** `forge/1.19.2/src/main/java/com/skps9/packai/logic/NsSourceIndex.java`
- `logic/AskEngine.java`（曝露 trace／欄位）
- `config/PackAiConfig.java`（新 key `nsSourceIndex`，預設 `true`，附檔數／MB／時間上限）
- `logic/JarLightIndex.java`（如只為共用 jar 清單／枚舉 helper）
- **新增** `tests/check_ns_source_tri_state.py`（新閘）
- **唔准改**：`neoforge/**`、任何 `assets/*/lang/*`（P4 才掂）、`AskBrief*` 以外嘅 UI

## 5. 驗收標準（可執行）
| # | 檢查 | 判準 |
|---|---|---|
| A1 | 單元：合成 fixture（一個 jar-only ns、一個 kubejs-only、一個 both） | 三態一律正確；`both` **唔准**報單邊 |
| A2 | 真機（harness，沙盒）：`golden_age` DLC 物品（亞巴頓類，kubejs-only） | trace `ns.source.state=pack`；答案**唔准**當佢係原廠 mod 內容 |
| A3 | 真機：`tetra:modular_double`（**both**） | trace `state=both`；答案要保留「未確定／兩邊都有」語意（SK 09-19 Q2 決定） |
| A4 | 負控：`minecraft:`（jar 有、kubejs 冇） | `state=mod`；查不存在 ns → `unknown`，**唔准作** |
| A5 | 效能：掃描 ≤2 秒、路徑 ≤20,000、cache ≤1 MiB | 實測數字寫入報告 |
| A6 | 現有閘 | ≥121 綠；0 新紅（如撞 `check_internal_label_parity.py`／`check_ask_display_leak.py` 要即報） |
| A7 | 49 Java 測試 | 全綠 |

## 6. 風險／邊界
- **`both` 係主調**（27 個重疊 ns，含 `tetra`／`create`／`minecraft`）→ 答案措辭必須保留不確定性，唔准斷言
- **只做 ns／路徑級**：唔去解 kubejs script 語意（範疇外，屬 P6）
- **效能**：231 個 jar ＋ 9,144 個 kubejs 檔 → 只枚舉名字；實測 jar 掃描 0.34 秒（前測）
- **還原**：新檔＋既有檔改動（工作樹未 commit）→ 實作前 `%TEMP%\p1_backup_<ts>\` timestamped copy ＋ md5（**唔用 `git checkout`**，因為多數係 untracked/modified 混合）
- **跨版本**：其他樹（1.20.1／1.21）目錄佈局唔同（1.21 data pack 目錄由複數變單數）→ **P1 只做 1.19.2**，唔准套用

## 7. 流程
1. 本 plan → 反方 R1 review（≥8:2 才開工）
2. cursor 實作（白名單）
3. Hermes 親驗：compile／49 測試／122＋新閘／**負控**（改壞三態邏輯要紅）
4. 真機 A2／A3／A4（harness；~4 萬 tokens／條）
5. 通過 → 同 fix A ＋ harness 一齊出（未過唔部署）
