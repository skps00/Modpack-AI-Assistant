# 研究：MC mod 宣傳片點做（2026-10-07）

> 目的：做 **Pack AI Assistant** 嘅宣傳片之前，先查（a）平台規則（b）業界實際做法（c）手法／工具。
> 全部結論都附來源；未核實嘅會明寫「未核實」。

---

## 0. 結論（一句）

**做一支 45–60 秒、真機畫面為主、有英文字幕（旁白可選）嘅 teaser，YouTube 上載、CurseForge／Modrinth 專案頁嵌入**；
**唔做 6–35 分鐘嘅「showcase」長片**（嗰個係另一種 genre，觀眾係先睇片再下載，唔係專案頁訪客）。

---

## 1. 真數據：業界片長（yt-dlp 實抽，2026-10-07）

`ytsearch "minecraft mod trailer official"` 抽 12 支（views 7 萬–3,100 萬）：

| 片長 | 例子 |
|---|---|
| **0:43** | TINY TAKEOVER – Official Trailer（176 萬 views） |
| **0:58** | WILDERNESS BOUND – Official Trailer（348 萬） |
| 1:19 | The Copper Age – Official Minecraft Trailer（301 萬） |
| 1:24 | Nether Update: Official Trailer（3,176 萬） |
| 2:11 | Mielon's The Sift – Mod Release Trailer（34 萬） |
| 2:43 | Asterion: The Labyrinth – Mod Trailer（7 萬） |
| 4:31 | Create Aeronautics Mod Release Trailer（162 萬） |

- **Mod trailer 主流＝45 秒–2 分半**（官方風格多數 < 1:30）。
- `ytsearch "minecraft mod showcase"` → **6–35 分鐘**（Aquamirae 23 分、Opposing Force 35 分）＝**另一種片**，唔係專案頁用。
- 抽兩支實測（`SCG-Extra` 1:07、`Hold My Items 5.0` 2:58）：**兩支都有英文字幕（auto-captions＝片中有旁白）** ⇒ 旁白係常態，唔係字幕黨。

---

## 2. 平台規則（硬紅線，原文引用）

### Modrinth Content Rules（2026-08-13 版）
- **§6.2 Prohibited**：「**No images** uploaded to a gallery, icon, description, or any other part of a project page **may be created or derived from generative AI output**. Any such images may be removed.」
  → **Modrinth 專案頁（icon／gallery／description）唔准用 AI 生成圖**。我哋嘅封面／截圖要用**真機畫面**。
- **§6.1 Disclosure**：要勾「Contains AI-generated content」當：① 大部分 code 係 AI 出 ② assets 係 AI 出 ③ **設計／功能依賴 generative AI** ④ 頁面文字／發佈依賴 AI。
  → 我哋個 mod **本身就係 AI 功能**（③）＋代碼大量 AI 協作（①）⇒ **Modrinth 一定要披露**。

### CurseForge Moderation Policies
- 「**AI Misleading Content Disclosure**：Any AI-modified (generated, edited, or enhanced) **showcase image** that may misrepresent the mod's actual content **must include a clear and visible disclaimer**（圖上或描述中）。」
- 「**Licensed Music**：Projects may include music only if the author **owns it or has redistribution rights**.」

### 音樂：YouTube Audio Library 嘅限制
- YAL 曲目係「免費供 YouTube 影片使用」，但**唔等於通用商用授權** ⇒ 片放喺 YouTube、CF 頁面只係**嵌入 YouTube 連結** ＝ OK；**直接上載檔案去 CF** 就唔穩。
- 保險做法：用 **CC0**（public domain）曲目，記低出處／授權。

---

## 3. 別人點做（手法）

**結構（Fumora 指南）**：① 最靚畫面 5 s（＝縮圖位）② 獨有功能 10 s ③ 玩法／社群 10 s ④ 建築 10 s ⑤ **CTA 5 s**。
- 「**Trailer 過 60 秒會流失一半觀眾；45 秒係甜點**」；「**文字唔好長，玩家係睇唔係讀**」；「**冇 CTA＝最大錯**」。
**工具鏈**：ReplayMod（錄資料、任意相機重播）＋ shaders（客戶端錄）→ DaVinci Resolve（免費、專業）或 CapCut → Epidemic Sound／Artlist（付費）或 CC0。
**我哋嘅適配**：本片主體係 **UI 示範**（唔需要電影感相機）⇒ **ReplayMod／shaders 唔需要**；用 `ffmpeg gdigrab` 直接抓 Minecraft 窗（零干擾、唔使開 OBS，亦避開改 OBS websocket 設定）。

---

## 4. 由研究得出嘅做法（更新 storyboard 決定）

1. **片長**：master **45–60 s**（原 plan 60 s ✓ 保留但唔加長）；直向版 30–40 s。
2. **畫面**：100% 真機（沙盒 instance）＋文字 overlay；**唔用 AI 生成圖**（Modrinth 硬規＋CF 要 disclaimer）。
3. **旁白**：業界常態有旁白；我哋用**英文 TTS**（語音線 HOLD，唔用 SK 真人聲）＋英文字幕；中文版另出字幕。
4. **音樂**：搵 **CC0** 曲；唔用來源不明音樂。
5. **每平台版本**：YouTube（master 16:9）＋Short／抖音（9:16）＋Modrinth/CF **只嵌 YouTube 連結**（唔直接上載檔案）。
6. **披露**：Modrinth 勾「Contains AI-generated content」；CF 若有任何 AI 修圖要加 disclaimer。

---

## 5. 未核實（誠實聲明）

- **CurseForge 專案頁實際嘅影片嵌入方式**：直接 fetch CF 頁面回 **403**（Cloudflare），要用離屏 Chrome 腳本讀（`hermes\scripts\cf_offscreen_read.py`）——**未做**（會開 GUI，等 SK 唔用機時做）。
- **Reddit `r/feedthebeast`「mod showcase pet peeve」帖**：直連同 jina 都 **403** ⇒ 社群期望未抽到原文（只靠上面指南代替）。
- 片長抽樣係 YouTube 搜尋前 12 名，**唔係隨機抽樣**（偏差：搜尋排名偏向高 views 片）。

---

## 6. Bilibili（B站）——SK 2026-10-07 追問後補查

### 6.1 實際片長（yt-dlp `bilisearch`，2026-10-07 抽 6 條）

| 片長 | 標題（節錄） | views |
|---|---|---|
| **11:00** | 將我的世界植入求生之路2？！遊戲模組展示與安裝！ | 13,833 |
| **19:06** | 我的世界：挖到寶了！25個MC神仙模組… | 61,230 |
| **3:56** | 我的世界1.20.1新模組【超位魔法】展示 | 119 |
| **5:18** | 【模組推薦】我的世界20個必備的實用模組 | 974,049 |
| **7:33** | 最全的MC danny AOT mod更新介紹 | 11,606 |
| **11:09** | 【自然之需模組生存】… | 133,131 |

⇒ **B站 MC mod 內容主流＝4–19 分鐘長片**（盤點式：「20 個必備模組」；試玩式；更新介紹），**同 YouTube 嘅 `mod showcase` 同一個 genre**，
**唔係** 45–60 秒 teaser。標題風格偏「數字＋形容詞＋清單」（吸睛式）。

### 6.2 對我們嘅意思

- 45–60 秒 teaser **唔可以照搬去 B站**（會顯得單薄）⇒ 要另一支：**中文旁白／字幕講解、5–10 分鐘**（或起碼 2–3 分鐘），封面用大字標題。
- 面向 B站＝面向中國大陸觀眾 ⇒ 文案用簡體、術語用大陸習慣（「模組」「整合包」）。

### 6.3 法規／平台規則（硬）

- 《**人工智能生成合成内容标识办法**》（國家網信辦等四部門，**2025-09-01 施行**）：AI 生成／合成內容要加**顯式標識**（用戶一眼見到）＋**隱式標識**（檔案數據）。
- **B站已上線「創作者聲明」**功能（發 AI 內容要自己聲明）；平台亦**會自動檢測**未聲明內容並加「**疑似內容為 AI 生成**」標籤
  （實測報導：六大平台約一半 AI 片有作者聲明、約三成被平台標「疑似」）。
- ⇒ 若我哋 B站版用 **AI 配音／AI 生成畫面** ⇒ **一定要勾 AI 聲明**；若純真機遊戲畫面＋自製字幕（無人聲合成）⇒ 一般唔屬 AI 生成內容，但**TTS 旁白就屬**。

### 6.4 未核實

- B站**自動上載**可行性：官方冇公開嘅個人投稿 API；第三方工具用 web 接口＝非官方、有風險。按既有規則**唔用自動化瀏覽器登入 SK 平台** ⇒ B站發佈只做「出片＋文案、人手按發佈」。
- 樣本只有 6 條、來自搜尋排名（偏差：偏熱門）；B站短片（<1 分鐘 mod 片）實際比例未量化。

## 7. 來源

1. Modrinth Content Rules — https://modrinth.com/legal/rules（§6.1 §6.2 原文）
2. CurseForge Moderation Policies — https://support.curseforge.com/support/solutions/articles/9000197279-moderation-policies
3. Fumora Lab, *How to Make a Minecraft Server Trailer That Actually Gets Players* — https://www.fumora.net/lab/minecraft-server-trailer-guide
4. YouTube Audio Library 授權說明 — https://vidiq.com/blog/post/royalty-free-music-youtube-audio-library 、 https://nanashino-chan.github.io/site/pages/youtube-audio-library-vs-commercial-music.html
5. yt-dlp 實抽片長（2026-10-07，本機 `yt-dlp 2026.08.19`）
6. Reddit r/feedthebeast 帖（403，未能讀取）— https://www.reddit.com/r/feedthebeast/comments/1jetbcz/
