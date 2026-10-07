# PLAN — Pack AI Assistant 宣傳片（v2，2026-10-07）

> 前置研究：`docs/promo/RESEARCH-mod-promo-videos-2026-10-07.md`（片長實測、平台規則、手法、B站）
> 狀態：**PLAN ONLY**。未開機、未錄影、未剪片。SK 2026-10-07 指令：「ok, make plan and review」。

---

## 1. 目標 / 非目標

**目標**：出 3 支片 + 1 張封面，令睇嘅人 30 秒內明白「呢個 mod 係咩、點解要裝、點裝」，並且**每一格畫面都係真機真答案**。

**非目標**（防 scope creep）：
- ✗ 唔做「頻道型」長期內容（唔做每週片）
- ✗ 唔做 3D 動畫／Mine-imator（成本高、同 UI demo 無關）
- ✗ 唔自動上載去任何平台（除 YouTube 可選，仍要 SK 批）
- ✗ 唔用 AI 生成圖做 Modrinth 任何位置（硬規，見 §7）

---

## 2. 已定 / 待定

### 已定（我按研究數據拍板；SK 可推翻）
| 項目 | 決定 | 依據 |
|---|---|---|
| Master 片長 | **50 秒（45–60 s 內）** | 業界抽樣 12 支、官方風格多數 < 1:30；指南「>60 s 流失一半觀眾、45 s 甜點」 |
| 比例／版本 | **16:9 master** ＋ **9:16 直向**（由 master 重剪） | YouTube／CF；Shorts／抖音 |
| 畫面 | **100% 真機**（`packai_sandbox`），文字 overlay 為主 | Modrinth §6.2 禁 AI 圖；CF 要求 disclaimer |
| 旁白 | **英文 TTS**（piper／edge-tts）＋英文字幕；另出中文字幕版 | 業界實測兩支 trailer 有旁白；語音線 HOLD（唔用 SK 真人聲） |
| 音樂 | **CC0**（要記授權頁）；**唔用** YouTube Audio Library 曲 | YAL 授權範圍限 YouTube，CF 要求「own/redistribution rights」 |
| 錄影工具 | `ffmpeg gdigrab` 抓 Minecraft 窗（**唔開 OBS**） | 唔使改 OBS websocket 設定；零額外窗 |
| B站 | **Phase 2**：中文版 5–10 分鐘（需另一支片，唔可以直搬 50 s） | 研究 §6：B站 mod 內容主流 3:56–19:06（n=6） |

### 待 SK（唔卡住開工，按默認先做）
1. 背景 pack：默認 **DJ2**（SK 最熟、觀眾基礎最大）；替代：ATM8／Star Technology
2. 旁白聲線：默認英文男聲（沉穩、非誇張）；要女聲／唔要旁白＝隨時改
3. 錄影時機：默認 **掛 watcher 等你離機 ≥2 分鐘自動錄**（`state==idle` ＋ `idle_seconds>=120` ＋ DS 離峰）

---

## 3. 交付物（驗收要逐件見到）

| # | 檔案 | 規格 |
|---|---|---|
| D1 | `promo_master_1920x1080.mp4` | ≥45 s ≤62 s、H.264、有英文旁白、字幕可開關（SRT 另附） |
| D2 | `promo_vertical_1080x1920.mp4` | 30–40 s、字幕**燒死**（手機睇） |
| D3 | `cover_1280x720.png` | 真機截圖＋大字標題、唔含 AI 生成圖 |
| D4 | `PROMO_NOTES.md` | 每格畫面嘅來源：trace 檔名／時間碼、用咗邊個 pack |
| D5 | `LICENSES.md` | 音樂檔名＋授權（CC0 出處 URL）＋TTS 引擎 |
| D6 | 合規 checklist | Modrinth §6.1 披露、CF AI disclaimer（如適用）、B站 AI 聲明（如發）逐項打勾＋依據 |

---

## 4. 錄影 pipeline（技術）

1. **前置**：`sk_activity.json` `state==idle` 且 `idle_seconds>=120`；`python scripts/ds_peak_hours.py --quiet` RC=0（重活排離峰）。
2. **環境**：`packai_sandbox`（**唔碰真 instance**）；jar 用當日版本（部署只准 `mc_mod_deploy_jar.py`）；**確認 mods 目錄只有一支含 mod class 嘅 jar**（skill §16 雙 jar 陷阱）。
3. **開遊戲**：Prism 以 `-l packai_sandbox` 啟動；入世界用真 API（`loadLevel("<世界>")`）；開完**即時把窗移去副螢幕**（`scripts/move_window_to_monitor.py`，`SWP_NOACTIVATE`），再獨立量度一次 `GetWindowRect` 確認。
4. **錄影**：`ffmpeg -f gdigrab -framerate 60 -i title=... -c:v libx264 -crf 18` 出檔（**唔搶焦點**；窗已喺副螢幕）。
5. **驅動問答**：`computer_use` 背景輸入（`]` 開面板 → 打英文問題 → Ask）——每次輸入後 `capture_after=True` 驗狀態真變；**輸入通道先做一次校準動作**（skill §GUI）。
6. **收尾**：`PostMessageW(WM_CLOSE)` 正常關遊戲（>60 s 才由 tasklist 消失，唔硬殺）；還原沙盒狀態；核 javaw 清零。

**每格要錄嘅真機動作（分鏡，總計 ~50 s）**

| # | 秒 | 真機動作 | 文字 overlay | 證據 |
|---|---|---|---|---|
| 1 | 0–5 | 遊戲內行過一排機器（重包 feel） | 「Heavy modpack. You know WHAT you want — not WHERE it is.」 | 錄影時間碼 |
| 2 | 5–15 | 按 `]` → 打白話問題 → 答案出，**配方卡貼喺對應步驟**、尾【Sources】 | 「Ask in plain language. Answers from YOUR pack.」 | trace `ask-*.jsonl`：`display.body.final`＋`render.cards.final.cardsOut>0` |
| 3 | 15–25 | Hover 一件物品 → 長按 `Y` → 單件答案（附魔／修理材料） | 「Hover an item. Hold Y.」 | 同 trace |
| 4 | 25–32 | 同一答案尾段 `On record, not in the answer:` | 「Nothing silently dropped.」 | trace body 原文 |
| 5 | 32–40 | 世界生成類問題（維度／生態域／高度／礦脈） | 「Mined, looted, traded — read from the pack itself.」 | trace body ＋ 資料源 |
| 6 | 40–46 | 設定畫面：三步（jar → `]` → API key／Ollama） | 「Client-only. No server install.」 | 設定畫面截圖 |
| 7 | 46–50 | 標題卡＋連結 | 「Pack AI Assistant — free, MIT. Forge 1.19.2.」 | — |

---

## 5. 剪輯 pipeline

- **剪**：ffmpeg（cut／speed／Ken Burns 於靜態截圖／transition）＋ Python 逐格生成 overlay PNG（PIL）→ 合成。
- **字幕**：腳本 → SRT（手打時間）→ **反向核對**：whisper 跑成品音軌，同 SRT 逐句比對（唔准自己話對就對）。
- **旁白**：piper（jarvis-high 英文模型）或 edge-tts；音量 −16 LUFS 標準化（ffmpeg loudnorm）。
- **音樂**：CC0 曲入 bed（−22 LUFS），記 URL 入 `LICENSES.md`。
- **封面**：真機截圖裁切＋大字（PIL），輸出 1280×720。

---

## 6. 驗收標準（**開工前定死**，完成後逐條實測）

| # | 標準 | 量法 |
|---|---|---|
| A1 | 片長 45–62 s（master）／30–40 s（直向） | `ffprobe -show_entries format=duration` |
| A2 | 每格畫面可追溯：`PROMO_NOTES.md` 每格都有 trace 檔名＋時間碼，且**該 trace 真存在** | 逐個 `ls` ＋ 讀該 trace 對應欄位 |
| A3 | 示範答案**零 raw token 外洩** | 對該 trace body 跑 skill 嘅外洩 regex（`configured=`／`L|`／`blocks/`／`role=`／`count=\d` …）→ 必須 CLEAN |
| A4 | 錄影期間**零搶焦點** | 開錄前／收錄後 `GetForegroundWindow` 一致（實測 skill 提醒：用進程名做 proxy 不可靠，要報原始值） |
| A5 | 字幕 100% 對得上音軌 | whisper 轉錄成品 → 逐句同 SRT diff，命中率 ≥98% |
| A6 | 音樂授權可查 | `LICENSES.md` 有檔名＋CC0 出處 URL，開得到 |
| A7 | D1／D2／D3 三個檔真存在、`ffprobe` 讀到正確解析度 | `ffprobe` |
| A8 | 合規：Modrinth 無 AI 生成圖（人工核 D3）＋披露項已列；CF/B站要求已列 | checklist 逐項＋依據連結 |

**A2／A3 係核心**：證明「真機真答案」而唔係砌出嚟——呢個 mod 嘅賣點就係唔靠 wiki。

---

## 7. 合規（硬紅線）

- **Modrinth §6.2**：gallery／icon／description **禁 AI 生成圖** ⇒ 封面同 gallery 全部用真機截圖。
- **Modrinth §6.1**：本 mod 功能即 AI ⇒ 專案頁要勾「Contains AI-generated content」。
- **CurseForge**：AI 修改過嘅 showcase image 要明顯 disclaimer；音樂要 own／有轉授權。
- **B站（如發）**：AI 配音／AI 生成畫面 ⇒ 要勾「創作者聲明」；平台會自動偵測。
- **YouTube（如自動上載）**：Data API v3，1,600 quota／片（~6 片／日）；一次性 OAuth；**要 SK 批**。

---

## 8. 風險 / 還原

| 風險 | 影響 | 對策 |
|---|---|---|
| 錄影時 SK 突然用機 | 畫面被搶／打斷 | watcher 逐 60 s 雙重檢查 `state` ＋ `idle_seconds`；一有輸入即 abort |
| 沙盒 mods 有兩支 jar（新功能冇生效） | 錄到錯畫面、白錄 | 開錄前 zipfile 掃 class 歸屬，必須只有一支 |
| 問答答案有 raw token 外洩 | 片上網＝公開錯料 | A3 掃描；有外洩即換問題重錄（唔靠後期遮） |
| 音樂授權睇錯 | 片被下架／索償 | 只用 CC0＋記 URL |
| 沙盒世界／設定被改壞 | 下次測試唔準 | 錄完還原（`packai-client.toml`／cases 檔）；沙盒係副本，最壞重複製 |
| 遊戲錄到一半 crash | 損失一輪 | 分鏡分開錄（每格獨立檔案），唔靠一 take 過 |

**不可逆動作**：零（全部喺沙盒、全部可再生）。**唯一要 SK 批**：對外發佈（YouTube 上載／CF／B站／Modrinth 貼出）。

---

## 9. 成本 / 排程（估算，D 級推論）

- 錄影：1–2 輪 × 每輪 ~20–30 分鐘（**要 SK 離機**；DS 離峰才跑）
- 剪輯＋字幕＋TTS：我 ~3–5 小時（可用離峰分批）
- 現金成本：**HK$0**（本機 ffmpeg／whisper／piper／CC0 音樂）
- 風險成本：若 DJ2 世界唔夠「戲」，可能要換 pack 重錄（+1 輪）

---

## 10. 未解 / 等 SK

1. B站版做唔做（Phase 2；要中文長版）
2. 要唔要順手做 YouTube 自動上載（要你 OAuth）
3. 錄影時機確認（默認掛 watcher）
