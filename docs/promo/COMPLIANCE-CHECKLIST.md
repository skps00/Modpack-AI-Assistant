# D6 — 合規 checklist（Modrinth / CurseForge / Bilibili / YouTube）

- 產出：2026-10-10（Hermes 過夜 run）｜依據：`docs/promo/PROMO_PLAN-v4-2026-10-08.md` §7 ＋ `docs/promo/RESEARCH-mod-promo-videos-2026-10-07.md`
- 原則：**每項要指到來源**；未發佈項目標「未做」，**唔可以當已通過**

| # | 平台／要求 | 依據（來源） | 我哋狀態（2026-10-10） |
|---|---|---|---|
| 1 | **Modrinth §6.2**：gallery／icon／description **禁 AI 生成或衍生圖像** | modrinth.com/legal/rules §6.2 Prohibited（研究 §37-39 有原文引用） | ✅ 合規設計：封面 `cover_1280x720.png`＝**真機截圖（ATM8 沙盒）＋文字**；gallery 計劃全真機圖；冇任何生成／修圖 |
| 2 | **Modrinth §6.1**：發佈時**必須勾**「Contains AI-generated content」 | 同上 §6.1 Disclosure（研究 §40-41） | ⏳ **未發佈**（項目頁未開）→ 開頁時必勾；本 repo 已記錄（見 `DISTRIBUTION.md` §3 checklist） |
| 3 | **CurseForge**：showcase image 若有 AI 修改要**明顯 disclaimer**；音樂須 own／可轉授權 | CF 政策（研究 §69） | ✅ 封面無 AI 修改（純真機截圖＋文字）；音樂＝CC0（`docs/promo/narration-20261009/LICENSES.md` 記出處 URL） |
| 4 | **Bilibili**：AI 生成內容要自行**「創作者聲明」**，平台會自動檢測未聲明內容 | 《人工智能生成合成内容标识办法》2025-09-01 施行（研究 §102-107） | ⏳ 未出片。**判定規則（已寫落 plan）**：純真機遊戲畫面＋自製字幕 ⇒ 一般唔屬 AI 生成內容；**若加 TTS 旁白 ⇒ 必勾聲明** |
| 5 | **YouTube**：如用 AI 配音 ⇒ 勾 AI 披露 | YouTube AI 政策（研究 §6.3／§163） | ⏳ 未出片；現版本**無旁白**（SK 2026-10-09 決定取消旁白）⇒ 暫無披露義務；日後加 TTS 就要勾 |
| 6 | **音樂授權**：CF／YouTube 都要 own／轉授權；YouTube Audio Library 只限 YouTube | 研究 §49 | ✅ 用 **CC0**（莫扎特《費加洛》序曲；`narration-20261009/LICENSES.md` 記 Wikimedia 出處 URL） |
| 7 | **畫面真實性**：對外素材必須係真機畫面（唔可以合成假畫面／假答案） | plan §2 A2／§6（每格有證據類型） | ✅ 50 s 版＝真機錄影（`%TEMP%\promo_footage_20261009-034259\promo_raw_20261009-034259.mp4`，105.2 s，2560×1440）；封面來自同一原片（94 s 格） |
| 8 | **AI 生成內容披露（Modrinth 描述）**：本 mod 功能即 AI ⇒ 專案描述要講清楚 | 研究 §40（③ 功能依賴 generative AI） | ✅ `docs/CURSEFORGE_DESCRIPTION.md` 已描述 AI 功能；Modrinth 開頁時沿用 |
| 9 | **YouTube 自動上載配額**（如做）：`videos.insert`＝1,600 units，日配額另有 100 次上限 | 研究 §149 | ⏳ 未做；SK 未批自動上載 |
| 10 | **CTA URL 可達性** | plan §6 A10 | ⚠️ **部分**：GitHub 200 ✅｜CurseForge 403（Cloudflare 擋機器；頁面存在性有 `PUBLISH.md` 記錄）｜Modrinth **未開（Project not found）** → 見 `DISTRIBUTION.md` §0 |

## 未達成／待辦（老實列）

- Modrinth 項目頁未開 ⇒ §6.1 披露**未實際勾過**（未發佈，無得勾）
- CurseForge 0.2.3 審核狀態未確認（`PUBLISH.md` 只記「API 200＝已接受，唔等於已發佈」）
- B站／YouTube 片未出 ⇒ 兩邊嘅平台披露項全部**未做**
