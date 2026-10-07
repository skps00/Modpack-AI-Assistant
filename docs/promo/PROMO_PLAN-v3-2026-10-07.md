# PLAN v3 — Pack AI Assistant 宣傳片（2026-10-07）

> 前置：`RESEARCH-mod-promo-videos-2026-10-07.md`（研究）／`PROMO_PLAN-v2-2026-10-07.md`（v2，已被本檔取代）
> **Round 1 review 結果：正方 3 : 反方 7**（反方 11 條攻擊；本檔逐條修正。評分記錄見 §12）
> 狀態：**PLAN ONLY**，未開機、未錄影、未剪片。

---

## 1. 目標 / 非目標

**目標**：出 **2 支片 + 1 張封面**，令睇嘅人 30 秒內明白「呢個 mod 係咩、點解要裝、點裝」，**每格畫面都係真機真答案**。

**非目標**：
- ✗ B站／抖音中文長版 → **Phase 2，另立 plan**（本 plan 唔包，避免「3 支片」懸空承諾）
- ✗ 3D 動畫／Mine-imator；✗ 自動上載任何平台；✗ AI 生成圖放 Modrinth（硬規）

**片種定位（回應反方攻擊 1）**：本片係 **project page 片**（CF／Modrinth 專案頁訪客、30 秒內決定要唔要裝）——
所以 45–60 s 係對嘅；**唔係** B站／YouTube「showcase」片（嗰類 4–35 分鐘，屬 Phase 2）。

---

## 2. 已定參數（附依據；可被 SK 推翻）

| 項目 | 決定 | 依據 |
|---|---|---|
| Master 片長 | **50 s（硬界 45–60 s）** | 官方風格 trailer 抽樣：43 s／58 s／79 s…（**表列 7 支，樣本 n=12**，研究 §1）；Fumora 指南「>60 s 失一半觀眾」 |
| 版本 | **16:9 master** ＋ **9:16 直向 30–40 s**（同一 take 裁切） | CF／Modrinth 嵌入；Shorts／抖音 |
| **背景 pack** | **`packai_sandbox_atm8`（ATM8，MC 1.19.2／Forge 43.2.14，381 mods）**；備選 `packai_sandbox`（NFWC，1.19.2／Forge 43.3.5，232 mods） | **DJ2 唔可能用：DJ2 = MC 1.12.2／Forge 14.23.5.2860，同 mod（1.19.2）唔同世代**（實測 `mmc-pack.json`）。1.19.2 sandbox 只有 6 個 |
| **錄影工具** | **`ddagrab`（DXGI Desktop Duplication，抓螢幕）為主**；`gdigrab` fallback；OBS 最後手段（要 SK 批改 websocket） | 本機 ffmpeg 兩個都有（實測 `-devices`／`-h filter=ddagrab`）；`gdigrab` 對硬件加速窗有黑片風險（反方引 ffmpeg ticket 7718）⇒ **M0 必做 10 秒 A/B 試錄，用「抽 3 幀平均亮度＋方差」客觀判非全黑** |
| **問答驅動** | **首選：harness 加「promo 模式」**（flag-gated，程式化填問題字串入面板再按 Ask → 零輸入注入）；**fallback：`computer_use` 背景輸入**（先做校準動作） | harness 既有路徑係 `openAndAskAbout(ItemStack)`，**唔收 free-text**（反方攻擊 5 已核實）⇒ 要一段新 harness 碼（M1，需 code review）；`computer_use` 對 MC 實測有時完全唔入 |
| 旁白 | **英文 TTS**（piper jarvis-high／edge-tts）＋英文字幕；另出中文字幕 SRT | 業界兩支實測 trailer 有旁白；語音線 HOLD（唔用 SK 真人聲）。**中文觀眾（B站強訊號）由 Phase 2 中文版覆蓋** |
| 音樂 | **CC0**（記出處 URL 入 `LICENSES.md`） | CF 要求 own／redistribution rights；YouTube Audio Library 只限 YouTube 用 |
| 收尾 | 關遊戲用 `PostMessageW(WM_CLOSE)`，等它自己退（>60 s），唔硬殺 | skill（硬殺＝下次載入 data pack 錯誤） |

---

## 3. 交付物（驗收逐件見）

| # | 檔案 | 規格 |
|---|---|---|
| D1 | `promo_master_1920x1080.mp4` | 45–60 s、H.264、英文旁白、另附 SRT |
| D2 | `promo_vertical_1080x1920.mp4` | 30–40 s、字幕燒死 |
| D3 | `cover_1280x720.png` | 真機截圖＋大字（**無 AI 生成圖**） |
| D4 | `PROMO_NOTES.md` | 每格：trace 檔名＋時間碼＋用邊個 pack |
| D5 | `LICENSES.md` | 音樂檔＋CC0 出處 URL＋TTS 引擎 |
| D6 | 合規 checklist | Modrinth §6.1／§6.2、CF、B站（如發）逐項＋依據 |

**Phase 2（另 plan）**：B站／抖音中文長版 5–10 分鐘（中文旁白＋AI 聲明）。

---

## 4. 錄影 pipeline（已修正路徑）

1. **Gate**：`sk_activity.json` `state==idle` 且 `idle_seconds>=120`；`scripts/ds_peak_hours.py --quiet` RC=0。
2. **沙盒**：`packai_sandbox_atm8`（唔碰真 instance）；jar 只准用 `mc_mod_deploy_jar.py` 部署；**開錄前用 zipfile 掃「邊幾支 jar 含本 mod class」＝必須只有一支**（skill §16 雙 jar 陷阱）。
3. **世界**：沙盒世界全部係**新生成空世界**（實測：ATM8 saves `New World` 0 MB／`New World (1)` 1.2 MB）⇒ **唔存在「行過機器陣」嘅現成場景**；因此分鏡 #1 改用**真機 UI 過載感**（JEI 物品清單／任務書翻頁），唔靠景物。
4. **開遊戲**：Prism `-l packai_sandbox_atm8`；入世界用真 API `loadLevel("<存檔夾名>")`；開完**即移窗去副螢幕**——用 **skill 腳本** `skills/software-development/minecraft-mod-in-game-autotest/scripts/move_window_to_monitor.py`（實測存在；v2 寫錯路徑），之後**獨立再量一次** `GetWindowRect`。
5. **錄影**：`ffmpeg -f ddagrab -i output_idx=<副螢幕> -framerate 60 -c:v libx264 -crf 18`（或 `gdigrab -i desktop`），**M0 三種各錄 10 秒**再揀；判準＝抽 3 幀計平均亮度／方差，非全黑且有畫面。
6. **問答**：harness promo 模式（首選）／`computer_use`（fallback，先校準）；每次輸入後 `capture_after` 驗狀態真變。
7. **收尾**：WM_CLOSE → 等自然退出 → 還原沙盒（toml／cases 檔）→ 核 javaw 清零。

**分鏡（總 50 s，逐格相加＝5+10+10+7+8+6+4）**

| # | 秒 | 真機動作 | Overlay | 證據 |
|---|---|---|---|---|
| 1 | 0–5 | ATM8 遊戲內：JEI 物品清單快速捲動／任務書（＝「重包」感） | 「381 mods. You know WHAT you want — not WHERE it is.」 | 錄影時間碼 |
| 2 | 5–15 | `]` 開面板 → **打白話問題** → 答案出，配方卡貼對應步驟、【Sources】 | 「Ask in plain language.」 | trace：`display.body.final`＋`render.cards.final.cardsOut>0` |
| 3 | 15–25 | Hover 物品 → 長按 `Y` → 單件答案 | 「Hover an item. Hold Y.」 | 同 trace |
| 4 | 25–32 | 同一答案尾段 `On record, not in the answer:` | 「Nothing silently dropped.」 | trace body 原文 |
| 5 | 32–40 | 世界生成類問題（維度／生態域／高度／礦脈） | 「Mined, looted, traded — read from the pack itself.」 | trace body |
| 6 | 40–46 | 設定畫面三步（jar → `]` → API key／Ollama） | 「Client-only. No server install.」 | 截圖 |
| 7 | 46–50 | 標題卡＋連結 | 「Pack AI Assistant — free, MIT. Forge 1.19.2.」 | — |

---

## 5. 剪輯 pipeline（已修正標準值）

- 剪：ffmpeg ＋ Python/PIL 逐格 overlay；兩比例由同一 take 裁切。
- 字幕：手打 SRT → **邊界核對**（whisper 段落 vs SRT 句界偏差 ≤0.5 s）＋抽 5 句人核。
- 旁白：**音量 −16 LUFS**（AES／串流常用）。音樂 bed：**−23 LUFS（EBU R128）**；唔用來源不明值（v2 寫嘅 −22 冇標準依據，已刪）。
- 封面：真機截圖裁切＋大字。
- 音樂：CC0 入 bed（相對旁白 −10 dB 亦可）。

---

## 6. 驗收標準（開工前定死；全部機械可量）

| # | 標準 | 量法（精確） |
|---|---|---|
| A1 | D1 片長 45–60 s；D2 30–40 s | `ffprobe -v error -show_entries format=duration -of csv=p=0` |
| A2 | 每格畫面追得到真 trace | `PROMO_NOTES.md` 每格有 trace 絕對路徑＋時間碼；逐個 `ls` 存在 ＋ 讀該 trace 對應欄位 |
| A3 | **雙層**防外洩：① repo 自家 checker 全綠 ② 成品**像素**掃描 CLEAN | ① `python tests/check_ask_display_leak.py`（以該檔 FORBIDDEN 清單為準，唔自創）＋該輪 trace 過 `judge_run_traces.py` ② 由成品抽 10 幀 **OCR**（tesseract 或 vision）掃 raw-token 形態（`configured=`／`inline_type=`／`L\|`／`blocks/`／`role=`／`count=\d`／`crafting_shaped:`／底線→空格形態）→ 命中 0 |
| A4 | 錄影零搶焦點 | 開錄前／收錄後 `GetForegroundWindow()` **原始 hwnd 值**一致（唔用進程名做 proxy；skill 實測會誤報） |
| A5 | 字幕對得上音軌 | 定義：① SRT 每句起訖 vs whisper 段落邊界偏差 ≤0.5 s（全部句子）；② 抽 5 句人眼核字。**已知限制**：旁白同字幕都係我出，屬半自我核對，唔算獨立驗證 |
| A6 | 音樂／TTS 授權可查 | `LICENSES.md` 有檔名＋CC0 URL（HTTP 200） |
| A7 | D1／D2／D3 存在且解析度正確 | `ffprobe` 讀 width／height |
| A8 | 合規逐項 | Modrinth：封面／gallery 全真機截圖（人工核）＋披露項已列；CF／B站要求已列；每項附來源連結 |

---

## 7. 合規（硬紅線）

- **Modrinth §6.2**：gallery／icon／description 禁 AI 生成圖。
- **Modrinth §6.1**：本 mod 功能即 AI ⇒ 勾「Contains AI-generated content」。
- **CF**：AI 修改嘅 showcase image 要明顯 disclaimer；音樂要 own／轉授權。
- **YouTube（如做自動上載，要 SK 批）**：`videos.insert` = **1,600 units**（官方 quota 表）；預設日配額 10,000 units，另有獨立 `videos.insert` 桶（上限 **100 次／日**）——**實際上載前再查一次當時 Console 顯示值**。
- **B站（Phase 2）**：AI 配音要勾「創作者聲明」（《人工智能生成合成内容标识办法》2025-09-01 施行）。

---

## 8. 風險 / 還原

| 風險 | 對策 |
|---|---|
| 錄影工具錄到黑片 | M0 三工具 10 s 試錄＋客觀判準；全部唔得→問 SK 開 OBS websocket |
| 沙盒空世界冇「重包感」 | 分鏡 #1 改用 UI 過載感（實測 saves 全空）；必要時用創造模式砌一小區（需輸入自動化，另評估成本） |
| 問答驅動唔穩 | harness promo 模式（首選）／computer_use（fallback，先校準） |
| 雙 jar 令新功能冇生效 | 開錄前 zipfile 掃 class 歸屬 |
| 答案有 raw token | A3 雙層；有命中即換問題重錄（唔後期遮） |
| 沙盒被改壞 | 錄完還原；沙盒係副本，最壞重複製 |

**不可逆動作：零**（全沙盒、可再生）。**要 SK 批**：對外發佈（YouTube／CF／Modrinth／B站）。

---

## 9. 成本（**未經量測估算**，第 1 輪錄影後回填真實數字）

- 錄影：1–2 輪；**錨**：skill 記「一輪 autotest ≈10–15 分鐘」、單一 ask ≈25 s；7 段分鏡**唔需要** 7 次開關遊戲（同一 session 內可連拍，harness 只需一次 cases）
- 剪輯＋字幕＋TTS＋封面：3–5 小時（**估算，無歷史數據**）
- 現金：HK$0

---

## 10. 未解 / 等 SK

1. B站中文版（Phase 2）做唔做
2. YouTube 自動上載要唔要（要你 OAuth）
3. 錄影時機（默認掛 watcher：`state==idle && idle_seconds>=120` ＋ DS 離峰）
4. M1 要動 harness 碼（flag-gated、需 code review）——批唔批

---

## 12. Review 記錄

| 輪 | 日期 | 反方主要攻擊 | 比分（正方:反方） | 處理 |
|---|---|---|---|---|
| R1 | 2026-10-07 | 11 條：片長 genre／gdigrab 黑片／**DJ2 世代不相容**／沙盒空世界／computer_use 唔穩＋harness 唔收 free-text／腳本路徑錯／A3 假綠（checker 清單唔同＋只掃 log 唔掃像素）／A5 自相矛盾＋tautology／「3 支片」無驗收／成本無根據／英文旁白同中文需求錯配 | **3 : 7** | 本 v3 逐條修正（見上） |
| R2 | 2026-10-07 | （待跑） | — | — |
