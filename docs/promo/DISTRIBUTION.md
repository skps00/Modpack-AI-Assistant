# D7 — 分發包（Distribution pack）

- 產出：2026-10-10（Hermes 過夜 run）｜依據：`docs/promo/PROMO_PLAN-v4-2026-10-08.md` §8（FC4）
- 文案為**初稿**（可改）；**未發佈**，所有 URL 一發佈就要回填
- 封面：`docs/promo/cover_1280x720.png`（1280×720、**真機截圖**＝ATM8 沙盒 AI 面板答案＋配方卡，唔係 AI 生成圖）

## 0. CTA URL 核實表（2026-10-10 04:2x Hermes 實測；方法＝`python urllib`，UA=Mozilla）

| 目標 | URL | 實測 | 備註 |
|---|---|---|---|
| GitHub 源碼 | `https://github.com/skps00/Modpack-AI-Assistant` | **HTTP 200** ✅ | 可直接用 |
| GitHub Releases | `https://github.com/skps00/Modpack-AI-Assistant/releases` | **HTTP 200** ✅ | 下載頁 |
| CurseForge 項目頁 | `https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia` | **HTTP 403**（Cloudflare 擋自動化，**唔代表頁面唔存在**） | 存在性依據＝`docs/PUBLISH.md:26`＋`:30-36`（0.2.3 上載回 HTTP 200、file id `8926920`，**仍待 CF 審核**）→ 正式發佈前**人手開一次**確認可達才落 CTA |
| Modrinth 項目頁 | `https://modrinth.com/mod/pack-ai-assistant` | 頁面 `<title>`＝**「Project not found」**；`api.modrinth.com/v2/project/pack-ai-assistant`＝**HTTP 404** | **項目頁未開**（SK 2026-10-08 已拍板 slug，但未建立）⇒ 本輪 CTA 先用 CF／GitHub；開頁後回填 |
| 影片（YouTube） | （待上載） | — | 上載後回填 |

> A10（「CTA URL 皆可 HTTP 200」）**本輪只部分達成**：GitHub 兩個 ✅、CurseForge 機器檢唔到（403）、Modrinth 未開。**唔可以當全綠**。

---

## 1. YouTube（master 16:9 宿主）

- **標題**：`Pack AI Assistant — ask your modpack in plain language (MC 1.19.2 Forge)`
- **描述**：
  ```
  Your modpack has 380+ mods. Pack AI Assistant reads JEI, FTB Quests and item data
  and answers in plain language, in-game — client-only, no server install. Free, MIT.
  Source: https://github.com/skps00/Modpack-AI-Assistant
  Download: https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia
  0:00 What it is / 0:05 Ask in plain language / 0:15 Hover + hold Y / 0:32 From the pack itself / 0:46 Client-only
  ```
- **縮圖文案**：`ASK ANYTHING · CLIENT-ONLY`（成品＝`cover_1280x720.png`）
- **CTA**：`Free download on CurseForge`（＋片尾 GitHub 連結）
- **發佈 checklist**：
  1. [ ] 片長 45–60 s、1920×1080（A1／A7 機檢）
  2. [ ] 上傳英文字幕 SRT（＋中文字幕）
  3. [ ] 如最終版有 TTS 旁白 → 勾 YouTube「AI 使用披露」
  4. [ ] 可見性＝Public（或先 Unlisted 過 SK 眼）
  5. [ ] 記低影片 URL → 回填本檔 §0 同 §3（項目頁嵌入）

## 2. Bilibili（中文短版嵌入）

- **標題**：`【MC模组】整合包AI助手 — 直接用大白话问整合包（1.19.2 Forge）`
- **描述**（簡體）：
  ```
  380+ 模组的整合包，不用翻 JEI。Pack AI Assistant 读取 JEI／FTB 任务／物品数据，
  用大白话回答，全客户端、免服务器安装。免费、MIT。
  源码：https://github.com/skps00/Modpack-AI-Assistant
  下载：https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia
  ```
- **縮圖文案**：`一問就答 · 純客戶端`
- **CTA**：`項目頁免費下載 →`（CurseForge 連結）
- **發佈 checklist**：
  1. [ ] 若用 AI 配音／AI 生成畫面 → **必勾「創作者聲明」**（《人工智能生成合成内容标识办法》2025-09-01）
  2. [ ] 標題／文案用大陸術語（模組／整合包）
  3. [ ] 自訂封面（大字版）
  4. [ ] 選分區＋標籤（Minecraft／單機遊戲）
  5. [ ] 記低 URL 回填本檔

## 3. 項目頁（CurseForge ＋ Modrinth）

- **CurseForge**：`https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia`（id `1643097`）
  - 描述沿用 `docs/CURSEFORGE_DESCRIPTION.md`（英文＋繁中）；gallery 全真機圖
  - 嵌入方式：Modrinth 專案頁 iframe **只准嵌 YouTube／Discord** ⇒ 嵌 **YouTube 片 URL**，唔直接上載 mp4
- **Modrinth**：slug `pack-ai-assistant`（**頁面未開**，見 §0）
  - 開頁 checklist：① 開專案（slug 如上）② 上載正名 `packai-<ver>+mc1.19.2-forge.jar`（**唔要** `-thin`／`-sources`）③ 文案（**禁 AI 生成圖**、**必披露 AI**）④ changelog ⑤ 標籤／分類／授權（MIT）⑥ 互相連結（Modrinth ↔ CurseForge ↔ 影片描述）
- **縮圖文案**：`Ask in plain language — client-only AI for your modpack`
- **CTA**：片尾「Download on CurseForge / Modrinth」
- **發佈 checklist**：
  1. [ ] Modrinth 勾「Contains AI-generated content」（§6.1）
  2. [ ] gallery／icon 全真機圖（§6.2）
  3. [ ] CF 若有任何 AI 修圖 → 加 disclaimer（本輪封面冇修圖，只有真機截圖＋文字）
  4. [ ] 嵌入 URL 可達（Section 0 表）
  5. [ ] 更新 `docs/PUBLISH.md` 記片 URL

---

## 4. 待 SK 決定

1. Modrinth 項目頁**幾時開**（唔開 → §0 兩個 CTA 用唔到）
2. CurseForge 0.2.3 審核狀態（未過 → 公開下載頁可能未 ready）
3. 影片最終版本（60 s master 需唔需要 SK 收機補拍 shot 1／6＋KubeJS demo）
