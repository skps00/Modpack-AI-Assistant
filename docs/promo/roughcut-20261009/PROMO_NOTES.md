# PROMO rough cut — 2026-10-09 04:5x（Hermes 自動產）

## 檔案
- `promo_roughcut_27s.mp4` — 1920×1080、27.22s、H.264 CRF21、`+faststart`、60fps（1,633 frames）、字幕已燒
- `roughcut.srt` — 字幕來源（5 段：標題／3 段答案／結尾）
- `still_*.png` — 5 張預覽（1.5s／6s／12s／20s／25.5s）
- 原片：`%TEMP%\promo_footage_20261009-034259\promo_raw_20261009-034259.mp4`（2560×1440、105.2s、6,223 frames）

## 內容（分鏡 → 原片時間碼）
| 段 | 內容 | 原片時間 | 輸出時間 |
|---|---|---|---|
| 標題 | 面板開、AI Thinking（未答） | 64.0–67.0 | 0–3 |
| A | 問題「how do I get diamonds?」答案＋Crafting／Blasting 卡 | 68.6–75.0 | 3–9.4 |
| B | 單件物品（diamond）答案：掉落／用途／合成（5 張卡） | 82.0–89.0 | 9.4–16.4 |
| C | 世界生成問題答案（4 張卡；畫面見到「Loot table: temple／large dungeon chest」＝人化後嘅 loot 名） | 92.0–99.8 | 16.4–24.2 |
| 結尾 | 面板＋字幕 | 100.0–103.0 | 24.2–27.2 |

## M0 方法（已定案，2026-10-09 03:33）
- **`ddagrab_dl`＝`-f lavfi -i ddagrab=output_idx=0:framerate=60 -vf hwdownload,format=bgra`**（裸 `ddagrab` 會 `Impossible to convert` d3d11→yuv420p）
- `gdigrab` fallback 必須 `-offset_x 0 -offset_y 0 -video_size 2560x1440`（`-i desktop` 會錄 3640×1920 全虛擬桌面）
- **坑**：硬 terminate ffmpeg ⇒ mp4 `moov atom not found`（壞檔）→ 要 stdin 送 `q` 禮貌收工
- 兩次錄影都零搶焦點（`GetForegroundWindow()` 開錄前後一致）

## 已知限制（粗剪，唔係 master）
1. 錄影係**視窗模式**：原片有 MC 標題列＋Windows 工作列 → 已 crop（上 40px、下 ~40px）＋補黑邊 30px；正式 master 應該用**全螢幕**錄。
2. shot 3 嗰條問題係 harness 自動產生（含 `minecraft:diamond` 字串）；答案段仲有 `silentgear:diamond`（Silent Gear 材料名）——兩者**唔應該**出現喺正式片（換成純人話問題／揀乾淨答案）。
3. 未有：旁白、音樂 bed、封面、D2 直式（1080×1920）、D7 分發包、A3 像素掃描覆蓋率報告。
4. 字幕由 Hermes 手寫（未做 A5 嘅 whisper 邊界核對）。
