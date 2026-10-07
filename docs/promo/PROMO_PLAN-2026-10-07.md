# Pack AI Assistant — 宣傳片計畫（2026-10-07，第一版）

> 目的：一支用嚟放 **CurseForge 專案頁 / YouTube** 嘅宣傳片，展示「真機真答案」——唔用假畫面、唔用渲染圖。
> 原則（跟 SK 規則）：**畫面全部係真 mod 真跑**；玩家文字只用官方顯示名；唔誇大（NeoForge 已暫停要老實講）。

---

## 1. 素材策略

| 來源 | 內容 | 狀態 |
|---|---|---|
| **真機錄影（新拍）** | 遊戲內 AI 面板：問一句 → 答案 ＋ 配方卡 ＋【Sources】；hover 物品長按 `Y` 問單件；任務引用 | ⏳ 要開沙盒遊戲（`packai_sandbox`）拍；**要 SK 唔用機／批准** |
| 現有截圖（repo 880 張 PNG） | `dist/cua_*` 等多為診斷用（有窗框／雜物）→ 只可用嚟做 b-roll 局部裁剪 | 要逐張篩 |
| 生成素材 | 標題卡／下三分一文字／箭嘴 highlight／背景底（純色或漸變） | 我用 ffmpeg／Python 生（零成本） |
| 音樂 | 需要一首可商用曲（CC0／CC-BY，要記 attribution） | ⏳ 未有（要揀） |
| 旁白 | 英文 TTS（本機 piper／edge-tts）或純字幕 | ⏳ 要 SK 揀 |

**技術路線**：用 ffmpeg（`gdigrab` 抓 Minecraft 窗）錄，**唔開 OBS**（OBS 的 obs-websocket 現時 `server_enabled=false`，開佢＝改設定，要問）。窗一律**擺去副螢幕**、零搶焦點（跟 skill `minecraft-mod-in-game-autotest` §13）。

---

## 2. Storyboard（60 秒 master；9:16 版抽 30–45 秒）

| # | 時間 | 畫面（真機） | 文字／旁白 |
|---|---|---|---|
| 1 | 0:00–0:06 | 遊戲內（重 pack）玩家行緊／望住一堆機器 | Hook：「Heavy modpack. You know what you want — not where the recipe is.」 |
| 2 | 0:06–0:18 | 按 `]` 開 AI 面板 → 打白話問題（例 `how do I make amethyst block?`）→ 答案出，**配方卡喺對應步驟下面**、最尾【Sources】 | 「Ask in plain language. Answers come from **your** pack — JEI recipes, quest book, local scripts.」 |
| 3 | 0:18–0:28 | Hover 一件物品長按 `Y` → 單件答案（例附魔／修理材料由遊戲 registry 讀） | 「Hover an item, hold Y — get the answer for that item.」 |
| 4 | 0:28–0:36 | 同一個答案：任務引用＋尾段 `On record, not in the answer:` | 「Nothing is silently dropped.」 |
| 5 | 0:36–0:46 | 世界生成類問題（維度／生態域／高度／礦脈）＋「本包冇寫就直講」 | 「Mined, looted, dropped — read from the pack's own data. If the pack doesn't say, Pack AI says so.」 |
| 6 | 0:46–0:54 | 三步設定畫面（jar 放入 mods → `]` → API key／Ollama／offline） | 「Client-only. No server install. Your LLM: OpenAI-compatible, Ollama, or offline mode.」 |
| 7 | 0:54–1:00 | 標題卡：Pack AI Assistant ＋ CurseForge／GitHub link ＋ 版本表 | 「Forge 1.19.2 — free, MIT. EN / 繁中 / 简中.」 |

**9:16 版**：抽 #2（問答＋卡）、#3（hover Y）、#7（CTA）。

---

## 3. 交付物

1. `promo_master_1920x1080.mp4`（~60 s，H.264，字幕可開關）
2. `promo_vertical_1080x1920.mp4`（~35 s，Shorts／抖音用）
3. `cover.png`（CurseForge 專案頁頭圖，1280×720）
4. `字幕.srt`（英）＋可選中文字幕版
5. `PROMO_NOTES.md`（每格畫面來源：trace 檔名／時間碼，證明係真跑）

---

## 4. 紅線（跟 AGENTS／skill）

- 錄影時：`sk_activity.json` 必須 `idle ≥120 s`，或者 SK 即時叫；MC 窗**一定放副螢幕**、零搶焦點；**唔碰真 instance**（只用 `packai_sandbox*`）。
- 唔用假畫面／唔用 AI 生成嘅「遊戲畫面」冒充真機；講 NeoForge 一定要帶「paused」。
- 唔用付費素材／唔用來源不明音樂（要 CC0／CC-BY 並記 attribution）。
- 發佈（上 CF／YouTube）＝對外，**要 SK 批**。

---

## 5. 等 SK 決定

1. **片長／比例**：a) 60 s 橫向（CF／YouTube）＋35 s 直向 b) 只要 60 s 橫向 c) 只要 30 s 直向
2. **旁白**：a) 英文 TTS ＋ 英文字幕 b) 純字幕 ＋ 音樂（唔講嘢） c) 中英雙字幕
3. **背景 pack**：a) DJ2（你最多觀眾熟） b) ATM8／Star Technology c) 由我揀
4. **音樂**：a) 我搵 CC0 曲 b) 你提供 c) 唔要音樂
5. **幾時錄**：a) 你唔用機時我自動跑（gated watcher） b) 你手動開 MC，我錄 c) 你話幾時
