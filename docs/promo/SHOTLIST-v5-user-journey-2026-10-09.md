# Promo 補拍分鏡 v5 —「真操作流程」示範（2026-10-09 SK 要求）

## 為什麼要補拍
現有素材（`%TEMP%\promo_footage_20261009-034259\promo_raw_20261009-034259.mp4`，105 秒）係**玩家企定、AI 面板開住**錄嘅。
量測（逐秒像素變化，5fps 取樣）：**大段時間 mean abs diff ≈ 0.00**（像素完全冇變），只有載入／切畫面幾秒有郁。
→ 睇落似「靜態截圖」。要 SK 要求嘅「完整使用流程」＝**一定要重錄**。

## 實機按鍵（已由 code 核實，唔係估）
| 按鍵 | 功能 | 出處 |
|---|---|---|
| `Y` | 問 hovered 物品（`THINK_JEI`；**GUI 情境**；要**長按**至進度滿才觸發） | `forge/1.19.2/.../client/ClientSetup.java:50-56`、`client/tooltip/ThinkHoldTracker.java` |
| `]`（右方括號） | 開／閂 AI 面板（`OPEN_AI`，IN_GAME） | `ClientSetup.java:42-48` |
| `E` | 開背包；JEI 用搜尋欄／`R`/`U` 查配方用途 | 原版 MC |

觸發鏈：hover item → 長按 Y → `ThinkHoldTracker` 進度滿 → `AiAssistantScreen.openAndAskAbout(stack)` → 面板彈出＋自動問。

## 錄影設定（沿用已定案 M0 方法）
```
ffmpeg -f lavfi -i ddagrab=output_idx=0:framerate=60:draw_mouse=1 \
       -vf hwdownload,format=bgra -c:v libx264 -crf 18 -pix_fmt yuv420p out.mp4
```
- **`draw_mouse=1`**：ddagrab 預設 true（已核 `ffmpeg -h filter=ddagrab`）→ **錄到滑鼠游標**（SK 要求要見到游標 hover）。
- 主螢幕 2560×1440（16:9，之後 crop/scale 到 1920×1080）。
- **全螢幕**錄（唔要 MC 標題列／Windows 工作列）。
- 收工要**送 `q` 畀 ffmpeg**（硬 kill → mp4 `moov atom not found` 壞檔）。
- 錄影／開窗期間 SK 唔可以打機（會錄到遊戲＋搶焦點）。

## 分鏡（8 段，總長目標 30–35 秒可用素材）
| # | 內容 | 要見到咩 | 長度 |
|---|---|---|---|
| S1 | 世界起步：行緊／轉視角 | 背景有活動（唔係靜止） | 4s |
| S2 | 開背包／JEI、游標移到目標物品 | **游標移動**、tooltip 彈出 | 5s |
| S3 | **長按 Y 觸發** | tooltip 上 hold 進度 → 面板彈出＋自動填入問題 | 4s |
| S4 | 答案 streaming | 答案逐字出、配方卡出現、sources 行 | 8s |
| S5 | **KubeJS demo** | 答案講「呢個包改咗」（唔係照 wiki） | 10s |
| S6 | 世界生成／掉落查詢 | 答案含 loot 來源（人化咗嘅名） | 8s |
| S7 | 設定畫面 | model／key／offline（client-only 賣點） | 4s |
| S8 | 結尾 B-roll | 世界有動作 | 5s |

## S5 KubeJS demo — 已揀好示範物品
ATM8 沙盒（`packai_sandbox_atm8`）真係跑 KubeJS（`kubejs-forge-1902.6.2`）。揀咗**有明確改動**嘅腳本做 demo：

- **首選：Occultism 銀礦** — `kubejs/server_scripts/ore_removal.js` 用 `forge:remove_features` **刪走** occultism `silver_ore` / `silver_ore_deepslate` 嘅主世界生成。
  → 問「where do I find silver?」→ 答案應該反映「呢個包改咗／刪咗」，而唔係照原版 wiki 講。
- **次選：ATM 礦物處理** — `ore_processing.js`（Mitchell52）加咗 Create 粉碎／IE 電弧爐／Thermal 粉碎機嘅礦物處理配方。
  → 問一件礦石／粉 → 見到 KubeJS 加嘅處理路線。

⚠️ 未有 trace 證據顯示 runtime 一定抽到 kubejs facts（現存 10 條 trace 只有 prompt 文字提及 kubejs）→ **S5 錄之前先跑一次確認答案真係引到 `// file: kubejs`**；引唔到就改用次選物品／或者唔拍 S5（唔可以假造）。

## 驗收標準
1. 每段都要**睇到滑鼠游標**，S2→S3 要**連續睇到**「hover → 長按 Y → 面板彈出」。
2. 全部真機真操作，零剪接造假。
3. 錄完即刻抽 3 格核對（唔可以黑屏／靜止）。
4. 記低 ffmpeg 開錄／停錄時間 ＋ 每個 shot 時間碼，寫入 `PROMO_NOTES`。

## 環境需求（開錄前 checklist）
- [ ] SK 唔喺打機／用機（讀 `state/sk_activity.json`）
- [ ] Prism instance：`packai_sandbox_atm8`（已裝 packai jar + KubeJS）
- [ ] 遊戲設**全螢幕**、GUI scale 適中（令面板文字大）
- [ ] packai client 憑證 OK（`config/packai-client.toml`，唔准入 repo）
- [ ] 磁碟夠位（2560×1440@60 約 1GB/分鐘）
