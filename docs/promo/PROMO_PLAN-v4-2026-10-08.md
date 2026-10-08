# PLAN v4 — Pack AI Assistant 宣傳片（2026-10-08）

> 前置：`RESEARCH-mod-promo-videos-2026-10-07.md`（研究）／`PROMO_PLAN-v3-2026-10-07.md`（v3，本檔取代；v3 保留唔動）
> **Review 記錄：R1 正方 3 : 反方 7（v3 修正）→ R2 正方 4 : 反方 6（未達 ≥8:2）**（R2 全文 `docs/plans/reviews/2026-10-08_promo-plan-v3-round2.md`）
> 狀態：**PLAN ONLY**，未開機、未錄影、未剪片、未寫任何 code。
> 本檔語言：繁體中文；所有數字於 **2026-10-08** 由本 agent **親手重跑**核實（命令／檔案／行號見 §11）；推論一律標〔判讀〕。

---

## 0. 本版針對 R2 嘅 5 條 flip condition（FC1–FC5）對照

R2 §5.4 明列：達成以下即 8:2（逐條可機檢）。**本 v4 逐條照做**，另加 SK 2026-10-08 三個決定（分發三渠道／M1 兩樣都要／錄主螢幕）。

| FC | R2 原文要求 | 本 v4 改咗咩 | 落點 |
|---|---|---|---|
| **FC1**（LD-D） | M1 要有 **flag 名＋注入路徑＋一條專屬 acceptance＋一行成本**，並聲明 M1 **屬 dev-only、要真 jar 核實（唔准入正式版）** | 新增 **§4b「M1 問答驅動 spec」**：flag＝`packai-autotest.flag`；注入路徑＝**重用 `/ai <question>`（`AiClientCommands:22-34`）＋ `AskService.askAsync()`（:103）**，欠嘅只係「面板驅動層」（`draftInput` 預填＋`sendCurrent()` 自動送出）；專屬 acceptance＝**A9**（trace `display.body.final` 非空＋`render.cards.final` `cardsOut≥1`）；成本一行；dev-only 聲明。**工作量照實寫細**（唔當由零寫 harness）。 | §4b、§6 A9、§9 |
| **FC2**（LD-C） | §4.4/§4.5 改成錄**主螢幕**（2560×1440 橫向）或明文寫死裁切／補邊；M0 A/B 保留 | §4.4/§4.5 改為 **`move_window_to_monitor.py 0` 搬去主螢幕（實測 2560×1440＝16:9）**，`ddagrab -i output_idx=0`；master 1920×1080 由 2560×1440 **downscale**（非 upscale、非裁切）；D2 直向改**原生另錄**（副螢幕 1080×1920）避免向上放大變軟。M0 三工具 A/B **保留**。 | §4.4、§4.5、§3 D2 |
| **FC3**（LD-F） | A2 收窄（只有**答案類**格要 trace，其餘格另有證據類型）；A3 像素閘改成**覆蓋答案段首／中／尾並報覆蓋率** | §6 **A2** 改為：只有「答案類」格（shot 2/3/4/5）要 trace（path＋時間碼＋對應欄位），shot 1/6/7 用**另一種證據類型**（時間碼／截圖／純卡）；§6 **A3②** 改成抽**每段答案之首／中／尾三幀＋報覆蓋率**（唔再只抽 10 幀）。 | §6 A2、A3 |
| **FC4**（LD-H） | 加 D7「分發包」＝channel＋項目頁 URL＋標題／描述／縮圖文案＋CTA＋發佈 checklist | §3 新增 **D7**；§8 詳列 **三渠道**（YouTube／Bilibili／項目頁〔CurseForge `pack-ai-assistant-paia` id 1643097，`PUBLISH.md:26`〕）各自 標題／描述／縮圖文案／CTA／發佈 checklist。**Modrinth 項目頁 slug＝`pack-ai-assistant`（SK 2026-10-08 拍板）；2026-10-08 用 Modrinth API 核＝HTTP 404（未佔用）。** | §3 D7、§8 |
| **FC5**（文件級） | 修 `New World (1)` 大小、`scripts/ds_peak_hours.py` 路徑、mod 數 predicate、§1「每格都係真答案」措辭 | ① `New World (1)`＝**19,273,115 bytes＝18.38 MiB**（實測，見 §11）；② 路徑改 `%LOCALAPPDATA%\hermes\scripts\ds_peak_hours.py`；③ mod 數寫明 predicate（**381＝`grep -c '<li>' modlist.html`；380＝`ls mods/*.jar`**）；④ §1 措辭改「每格都係真機真畫面（答案類格為真機真答案）」。 | §11、§4.1、§2、§1 |

---

## 1. 目標 / 非目標

**目標**：出 **2 支片 + 1 張封面 + 1 份分發包**，令睇嘅人 30 秒內明白「呢個 mod 係咩、點解要裝、點裝」，**每格畫面都係真機真畫面**（其中「答案類」格＝真機真答案，見 §6 A2）。〔判讀：措辭由 v3「每格都係真答案」收窄，因 shot 1/6/7 並非答案格——見 R2 A4／A10〕

**非目標**：
- ✗ B站／抖音中文長版（5–10 分鐘）→ 屬 **Phase 2，另立 plan**（本 plan 只交付 B站**短版嵌入版**嘅文案，見 §8）；避免「3 支片」懸空承諾。
- ✗ 3D 動畫／Mine-imator；✗ 自動上載任何平台（YouTube API 自動上載要 SK 批）；✗ AI 生成圖放 Modrinth／CF 專案頁（硬規）。

**片種定位**（回應 R1 攻擊 1，R2 §5.1 判「存活（有保留）」）：本片係 **project page 片**（CF／Modrinth 專案頁訪客，30 秒內決定要唔要裝），所以 45–60 s 係對嘅；**唔係** B站／YouTube「showcase」片（4–35 分鐘）。
〔判讀：R2 A8 指片長指南（Fumora）實為 **server trailer 指南**、目標頁觀眾行為**零數據**→ 本版**明文標此為「類比推論」**，唔當硬證據。〕

---

## 2. 已定參數（附依據；可被 SK 推翻）

| 項目 | 決定 | 依據（實測） |
|---|---|---|
| Master 片長 | **50 s（硬界 45–60 s）** | 官方風格 trailer 抽樣 43 s／58 s／79 s…（研究 §1，樣本 n=12；**類比推論**，見 §1） |
| 版本 | **16:9 master 1920×1080** ＋ **9:16 直向 1080×1920**（直向**原生另錄**，見 §4.5） | CF／Modrinth 嵌入；Shorts／抖音 |
| **背景 pack** | **`packai_sandbox_atm8`（ATM8，MC 1.19.2／Forge 43.2.14）**；備選 `packai_sandbox`（NFWC，1.19.2／Forge 43.3.5） | 實測 `packai_sandbox_atm8/mmc-pack.json` → MC 1.19.2、Forge 43.2.14。**DJ2 唔用得**：DJ2＝MC 1.12.2（唔同世代）。1.19.2 sandbox 實測共 **6 個**（`packai_sandbox`／`_atm8`／`_e9e`／`_ftb`／`_startech`／`_universio`）。 |
| **mod 數（predicate 寫明）** | **380 jar**（`ls .../packai_sandbox_atm8/minecraft/mods/*.jar \| wc -l`）；**381 modlist 條目**（`grep -c '<li>' .../packai_sandbox_atm8/modlist.html`，檔喺 **instance 根目錄**、非 `minecraft/`） | 兩者 predicate 唔同，**唔可以混講**。NFWC：`mods/*.jar`＝**231**。 |
| **錄影工具** | **`ddagrab`（DXGI Desktop Duplication，抓整個 monitor）為主**；`gdigrab` fallback；OBS 最後手段（要 SK 批改 websocket） | 本機 ffmpeg 兩個都有；`gdigrab` 對硬件加速窗有黑片風險（反方引 ffmpeg ticket 7718）⇒ **M0 必做 10 秒 A/B 試錄**，用「抽 3 幀平均亮度＋方差」客觀判非全黑。 |
| **錄影幾何（FC2）** | **錄主螢幕＝2560×1440（實測 16:9）**；`move_window_to_monitor.py 0` 搬窗至主螢幕；`ddagrab -i output_idx=0` | 實測 `EnumDisplayMonitors`：主＝(0,0,2560,1440)＝**2560×1440 橫向**；副＝(-1080,-241,0,1679)＝**1080×1920 直向**。2560×1440 本身係 16:9 ⇒ downscale 1920×1080 零裁切、零 upscale。`ddagrab` 係 output（整個 monitor）抓取，**唔可以指定單一窗**。 |
| **問答驅動** | **重用現成 `/ai <question>` ＋ `AskService.askAsync()`**；面板視覺由既有 `AiAssistantScreen`（`draftInput` 預填＋`sendCurrent()`）出 ⇒ 只欠一層薄驅動（M1，見 **§4b**） | 實測：`AiClientCommands.java:22-34`（`/ai`＝`greedyString()`→`askAsync`）；`AskService.java:103` `askAsync(String,Consumer)`；`AiAssistantScreen.java:78` `draftInput`、`:161-162` 預填、`:428` `askAsync`。 |
| 旁白 | **英文 TTS**（piper jarvis-high／edge-tts）＋英文字幕；另出中文字幕 SRT | 業界兩支實測 trailer 有旁白（研究 §1）；語音線 HOLD（唔用 SK 真人聲）。〔判讀：中文觀眾由 B站短文案＋Phase 2 中文版覆蓋；旗艦交付物受眾仍係英文，此點 R2 #11 判「PARTIAL（可接受）」〕 |
| 音樂 | **CC0**（記出處 URL 入 `LICENSES.md`） | CF 要求 own／redistribution rights；YouTube Audio Library 只限 YouTube 用 |
| 收尾 | 關遊戲用 `PostMessageW(WM_CLOSE)`，等它自己退（>60 s），唔硬殺 | skill（硬殺＝下次載入 data pack 錯誤） |

---

## 3. 交付物（驗收逐件見）

| # | 檔案 | 規格 |
|---|---|---|
| D1 | `promo_master_1920x1080.mp4` | 45–60 s、H.264、英文旁白、另附 SRT |
| D2 | `promo_vertical_1080x1920.mp4` | 30–40 s、字幕燒死、**原生直向另錄**（零 upscale，見 §4.5） |
| D3 | `cover_1280x720.png` | 真機截圖＋大字（**無 AI 生成圖**） |
| D4 | `PROMO_NOTES.md` | 每格：**證據類型**＋trace 檔名（限答案類格）＋時間碼＋用邊個 pack（見 §6 A2） |
| D5 | `LICENSES.md` | 音樂檔＋CC0 出處 URL＋TTS 引擎 |
| D6 | 合規 checklist | Modrinth §6.1／§6.2、CF、B站逐項＋依據 |
| **D7** | **`DISTRIBUTION.md`（分發包）** | **三渠道**（YouTube／Bilibili／項目頁）各自：標題／描述／縮圖文案／CTA／發佈 checklist（全文見 §8）。**FC4 新增。** |

**Phase 2（另 plan）**：B站／抖音中文長版 5–10 分鐘（中文旁白＋AI 聲明）。

---

## 4. 錄影 pipeline（已修正路徑＋幾何）

1. **Gate**：`sk_activity.json` `state==idle` 且 `idle_seconds>=120`；`python "$LOCALAPPDATA/hermes/scripts/ds_peak_hours.py" --quiet` RC=0（**路徑修正**：真身喺 `%LOCALAPPDATA%\hermes\scripts\`，repo 內**冇** `scripts/` 目錄；見 §11）。
2. **沙盒**：`packai_sandbox_atm8`（唔碰真 instance）；jar 只准用 `mc_mod_deploy_jar.py`（`%LOCALAPPDATA%\hermes\scripts\`）部署；**開錄前用 zipfile 掃「邊幾支 jar 含本 mod class」＝必須只有一支**（skill §16 雙 jar 陷阱；實測 ATM8 `mods/` 只有 `packai-autotest-dev.jar` 一支 packai jar）。
3. **世界**：沙盒 saves 實測有 `New World`（**3 bytes＝空**）同 `New World (1)`（**19,273,115 bytes＝18.38 MiB**）⇒ **唔存在「行過機器陣」嘅現成場景**。**FC5 修正**：v3 寫 `New World (1)` 1.2 MB 係錯（差約 15 倍）；本版改用親測數字。因此分鏡 #1 改用**真機 UI 過載感**（JEI 物品清單／任務書翻頁），唔靠景物。
4. **開遊戲**：Prism `-l packai_sandbox_atm8`；入世界用真 API `loadLevel("<存檔夾名>")`（`AutoTestHarness.java:265`）；開完**即搬窗去主螢幕**（**FC2 修正**）——用 skill 腳本 `%LOCALAPPDATA%\hermes\skills\software-development\minecraft-mod-in-game-autotest\scripts\move_window_to_monitor.py 0`（`0`＝主螢幕 2560×1440；實測該腳本存在，v2 寫錯 repo 相對路徑），之後**獨立再量一次** `GetWindowRect` 核落喺 2560×1440。
5. **錄影**：`ffmpeg -f ddagrab -i output_idx=0 -framerate 60 -c:v libx264 -crf 18`（`output_idx=0`＝主螢幕，**FC2 修正**；或 `gdigrab -i desktop`），**M0 三種各錄 10 秒**再揀；判準＝抽 3 幀計平均亮度／方差，非全黑且有畫面。**D1**：2560×1440 → **downscale** 1920×1080（16:9→16:9，零裁切零 upscale）。**D2**：**另錄一次原生直向**——`move_window_to_monitor.py 1`（副螢幕實測 1080×1920 原生）＋ `ddagrab -i output_idx=1`，零 upscale。〔判讀：唔用 master 中央裁 9:16，因 16:9 源裁 9:16 只得 810×1440，拉上 1080×1920 係 ×1.33 會軟。〕
6. **問答**：M1 driver（見 §4b）——重用 `/ai` 與 `askAsync`；每次輸入後驗面板真出答案＋卡（`capture_after`）。
7. **收尾**：WM_CLOSE → 等自然退出 → 還原沙盒（toml／cases 檔）→ 核 javaw 清零。

### 4b. M1 問答驅動 spec（**FC1 專屬**）

> SK 決定（2026-10-08）：M1＝① 寫正式 spec（本節）② **實作路線改用現成 `/ai` ＋ `AskService.askAsync()`，唔當由零寫 harness，工作量照實寫細**。

**(1) Flag 名**：**沿用現有 `packai-autotest.flag`**（classpath 資源，**只喺 `-PpackaiAutotest` build 出現**：`build.gradle:113-115` 把 `src/autotest/resources` 加入 `sourceSets.main.resources.srcDir`，jar 更名 `autotest-dev-<ver>.jar`；`AutoTestHarness.active()` 讀 `/packai-autotest.flag`，:96-107）。**唔新開 flag**；promo 模式由 `cases.json` 新增欄位選中（見 (2)）。

**(2) 注入路徑**（重用既有元件，唔寫新 pipeline）：
- **既有 free-text 入口（零新 code）**：`/ai <question>` → `AiClientCommands.java:22-34`（`StringArgumentType.greedyString()`）→ `AskService.askAsync(q, cb)`（`AskService.java:103`）。**呢條今天就跑得**，係 R2 §5.3「最便宜收口實驗」講嘅路徑。
- **面板視覺路径（只欠薄驅動層）**：shot 2 要出「面板＋答案＋配方卡」，而 `/ai` 只將答案送去 chat ⇒ 需開 `AiAssistantScreen` 並令佢自動送出。現成元件：`draftInput`（`:78`）＋ `init()` 預填（`:161-162`）＋ `sendCurrent()`→`askAsync(...)`（`:428`）。**欠嘅只係**：
  - ① `AutoTestHarness` 嘅 `CaseSpec` 由 `{id,item}` 擴為 `{id,item?,question?}`（`parse()` :197-209 已讀 `id`／`item`，加讀 `question`）；
  - ② `startCase()`（:407-421）加一分支：`question` 非空 → 開 `AiAssistantScreen` 並設 `draftInput=question` 再觸發 `sendCurrent()`（等價 `/ai` 走同一 `askAsync`）；`item` 非空 → 沿用 `openAndAskAbout(stack)`（:419）；
  - ③ `Judge`（:658-662）對免費文字 case 改以「trace 檔最新 `display.body.final`／`render.cards.final`」為準（唔以 `item` 作 key）。
- **一句總結**（R2 A2 反轉條件）：**free-text ask pipeline 早已存在（`/ai`＋`askAsync`）；真正欠嘅只係「把問題餵入面板並自動提交」嘅面板驅動層。**

**(3) 專屬 acceptance（A9）**：喺 `packai_sandbox_atm8` 跑一次 promo case 後，**其 trace 檔（`<instance>/packai/trace/ask-*.jsonl`）同時滿足**：
- 存在 `display.body.final` 事件且 `body` 非空；且
- 存在 `render.cards.final` 事件且 `cardsOut ≥ 1`。
機檢法：讀該 JSONL、`grep '"event":"display.body.final"'`／`'"event":"render.cards.final"'` 逐行解析（事件名同 `AutoTestHarness.java:658-662` 嘅 Judge 一致）。**此驗收同時補 A9**（證 packai 喺 ATM8 真跑出到合格答案，關 R2 A9「未證 ATM8 答案質素」）。

**(4) 成本（一行）**：M1＝**約 1 個薄改動**（`CaseSpec` 加一欄＋`openAndAskQuestion` 一支＋`startCase` 一分支＋Judge 一調整）＋ **1 條 acceptance（A9）**；**冇新 ask pipeline**（重用 `/ai`＋`askAsync`）。估 **1–2 h 開發＋1 次 compile＋1 次 autotest 跑**；錨＝skill「一輪 autotest ≈10–15 分鐘」、單一 ask ≈25 s。**需經 code review**（契約：code 一律經確認流程）。

**(5) dev-only 聲明**：M1 全部落喺 **`AutoTestHarness`（dev-only harness）**內，由 `packai-autotest.flag` 閘住；**出廠 jar 冇 flag ⇒ `active()` false ⇒ 完全 inert**。**唔准入正式版**；部署前用 `zipfile` 核真 jar 內容（flag 只准喺 `-PpackaiAutotest` build 出現，正式 `packai-<ver>.jar` 必**無** `*.flag`）。

**分鏡（總 50 s，逐格相加＝5+10+10+7+8+6+4＝50）**

| # | 秒 | 真機動作 | Overlay | 證據類型（依 §6 A2） |
|---|---|---|---|---|
| 1 | 0–5 | ATM8 遊戲內：JEI 物品清單快速捲動／任務書（＝「重包」感） | 「380 mods. You know WHAT you want — not WHERE it is.」 | 非答案格：錄影時間碼 |
| 2 | 5–15 | `]` 開面板 → **打白話問題** → 答案出，配方卡貼對應步驟、【Sources】 | 「Ask in plain language.」 | **答案格**：trace `display.body.final`＋`render.cards.final.cardsOut>0` |
| 3 | 15–25 | Hover 物品 → 長按 `Y` → 單件答案 | 「Hover an item. Hold Y.」 | **答案格**：同 trace |
| 4 | 25–32 | 同一答案尾段 `On record, not in the answer:` | 「Nothing silently dropped.」 | **答案格**：trace body 原文 |
| 5 | 32–40 | 世界生成類問題（維度／生態域／高度／礦脈） | 「Mined, looted, traded — read from the pack itself.」 | **答案格**：trace body |
| 6 | 40–46 | 設定畫面三步（jar → `]` → API key／Ollama） | 「Client-only. No server install.」 | 非答案格：截圖 |
| 7 | 46–50 | 標題卡＋連結 | 「Pack AI Assistant — free, MIT. Forge 1.19.2.」 | 非答案格：純卡（無 trace） |

---

## 5. 剪輯 pipeline

- 剪：ffmpeg ＋ Python/PIL 逐格 overlay；D1＝主螢幕 2560×1440 downscale；D2＝副螢幕原生 1080×1920。
- 字幕：手打 SRT → **邊界核對**（whisper 段落 vs SRT 句界偏差 ≤0.5 s）＋抽 5 句人核。
- 旁白：**音量 −16 LUFS**（AES／串流常用）。音樂 bed：**−23 LUFS（EBU R128）**。
- 封面：真機截圖裁切＋大字（1280×720）。
- 音樂：CC0 入 bed（相對旁白 −10 dB 亦可）。

---

## 6. 驗收標準（開工前定死；全部機械可量）

| # | 標準 | 量法（精確） |
|---|---|---|
| A1 | D1 片長 45–60 s；D2 30–40 s | `ffprobe -v error -show_entries format=duration -of csv=p=0` |
| **A2** | **（FC3 收窄）**只有**答案類格**（shot 2/3/4/5）要有 trace；其餘格（shot 1/6/7）用**另一種證據類型** | 答案格：`PROMO_NOTES.md` 有 trace 絕對路徑＋時間碼，逐個 `ls` 存在＋讀對應欄位（§4b A9 兩個事件）；非答案格：`PROMO_NOTES.md` 記明證據類型（時間碼／截圖 path／純卡），抽 1 張截圖存在。**唔會必然紅** |
| **A3** | **雙層**防外洩（**FC3 改覆蓋率**）：① repo 自家 checker 全綠 ② 成品**像素**掃描 CLEAN 且**報覆蓋率** | ① `python tests/check_ask_display_leak.py`（以該檔 `FORBIDDEN` 清單為準，唔自創）＋該輪 trace 過 `judge_run_traces.py` ② **抽每一段答案之首／中／尾三幀**（唔再只抽 10 幀），OCR 掃 raw-token 形態（`configured=`／`inline_type=`／`L\|`／`blocks/`／`role=`／`count=\d`／`crafting_shaped:`／底線→空格）→ 命中 0；**報告「已掃答案段數／已掃幀數＝覆蓋率」** |
| A4 | 錄影零搶焦點 | 開錄前／收錄後 `GetForegroundWindow()` **原始 hwnd 值**一致（唔用進程名做 proxy） |
| A5 | 字幕對得上音軌 | ① SRT 每句起訖 vs whisper 段落邊界偏差 ≤0.5 s（全部句子）；② 抽 5 句人核字。**已知限制**：旁白同字幕都係我出，屬半自我核對，唔算獨立驗證 |
| A6 | 音樂／TTS 授權可查 | `LICENSES.md` 有檔名＋CC0 URL（HTTP 200） |
| A7 | D1／D2／D3 存在且解析度正確 | `ffprobe` 讀 width／height（D1 1920×1080；D2 1080×1920；D3 1280×720） |
| A8 | 合規逐項 | Modrinth §6.1／§6.2、CF、B站要求逐項＋每項附來源連結 |
| **A9** | **（FC1 專屬）**M1 promo case 落 trace 出到答案＋卡 | 見 §4b(3)：trace JSONL 有 `display.body.final`（非空）＋`render.cards.final`（`cardsOut≥1`）。**兼答 R2 A9（ATM8 答案質素）** |
| **A10** | **（FC4 專屬）**D7 分發包齊三渠道 | `DISTRIBUTION.md` 有 3 個 channel 段，每段齊 標題／描述／縮圖文案／CTA／發佈 checklist；CTA URL 皆可 HTTP 200（項目頁 URL 見 §8） |

---

## 7. 合規（硬紅線）

- **Modrinth §6.2**：gallery／icon／description 禁 AI 生成圖（研究 §2 原文引用）。
- **Modrinth §6.1**：本 mod 功能即 AI ⇒ 勾「Contains AI-generated content」。
- **CF**：AI 修改嘅 showcase image 要明顯 disclaimer；音樂要 own／轉授權。
- **YouTube（如做自動上載，要 SK 批）**：`videos.insert`＝**1,600 units**；預設日配額 10,000 units，另有獨立 `videos.insert` 桶（上限 **100 次／日**）——**實際上載前再查一次當時 Console 顯示值**。
- **B站**：AI 配音要勾「創作者聲明」（《人工智能生成合成内容标识办法》2025-09-01 施行）。

---

## 8. D7 分發包（**FC4**；三渠道全部寫齊）

> SK 2026-10-08 決定：**分發渠道＝全部三個**。以下文案為**初稿**（可改）；通道 URL 見各行。**Modrinth 項目頁 slug＝`pack-ai-assistant`（SK 2026-10-08 拍板；API 核未佔用）。**

### 8.1 YouTube（master 16:9 宿主）
- **標題**：`Pack AI Assistant — ask your modpack in plain language (MC 1.19.2 Forge)`
- **描述**：`Your modpack has 380+ mods. Pack AI Assistant reads JEI, FTB Quests & item data and answers in plain language, in-game — client-side only, no server install. Free, MIT.`＋來源 GitHub 連結＋章節時間碼。
- **縮圖文案**：`ASK ANYTHING · CLIENT-ONLY`（真機截圖底）。
- **CTA**：`Free download: [CurseForge project page]`（見 §8.3）＋訂閱提示。
- **發佈 checklist**：① 片長/解析度過 A1/A7；② 字幕 SRT 上傳；③ 勾 AI 披露（如用 TTS，見研究 §6.3／YouTube AI 政策）；④ 設定可見性＝Public；⑤ 記錄影片 URL 回寫 D7 同 §8.3 嵌入用。

### 8.2 Bilibili（中文短版嵌入）
- **標題**：`【MC模组】整合包AI助手 — 直接用大白话问整合包（1.19.2 Forge）`
- **描述**：簡體文案：`380+ 模组的整合包，不用翻 JEI。Pack AI Assistant 读取 JEI／FTB 任务／物品数据，用大白话回答，全客户端、免服务器安装。免费、MIT。`＋GitHub 連結。
- **縮圖文案**：大字`一問就答 · 純客戶端`。
- **CTA**：`項目頁免費下載 →`（CurseForge 連結）。
- **發佈 checklist**：① 若用 AI 配音／AI 生成畫面 ⇒ **必勾「創作者聲明」**（研究 §6.3）；② 標題用大陸術語（模组／整合包）；③ 封面大字；④ 分區／標籤；⑤ 記 URL 回寫。

### 8.3 項目頁（Modrinth／CurseForge 專案頁嵌入）
- **項目頁 URL**：CurseForge **`pack-ai-assistant-paia`**，id **`1643097`** → `https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia`（`docs/PUBLISH.md:26`）。**Modrinth 項目頁 slug＝`pack-ai-assistant`** → `https://modrinth.com/mod/pack-ai-assistant`（SK 2026-10-08 拍板；同日 `api.modrinth.com/v2/project/pack-ai-assistant` → **HTTP 404 ＝ 未佔用**；⚠️ 專案頁本身**仍未開**，開頁後回填真 URL）。
- **嵌入方式**：Modrinth 專案頁 iframe **只准嵌 YouTube／Discord**（研究 §7 來源）⇒ **嵌 YouTube 片 URL**，唔直接上載 mp4。
- **描述**：沿用 `docs/CURSEFORGE_DESCRIPTION.md`（英文＋繁中）；gallery 全真機截圖（唔加 AI 圖）。
- **縮圖文案**：`Ask in plain language — client-only AI for your modpack`。
- **CTA**：片尾「Download on CurseForge／Modrinth」。
- **發佈 checklist**：① 勾 Modrinth §6.1 AI 披露；② gallery／icon 全真機圖（§6.2 合規）；③ CF 若有 AI 修圖加 disclaimer；④ 確認嵌入 URL 可 HTTP 200；⑤ 更新 `docs/PUBLISH.md` 記片 URL。

---

## 9. 成本

- **M1（FC1）**：**一行**——薄驅動層（§4b(4)）約 1–2 h 開發＋1 compile＋1 autotest 跑；**冇新 ask pipeline**（重用 `/ai`＋`askAsync`）。
- 錄影：1–2 輪；錨：skill「一輪 autotest ≈10–15 分鐘」、單一 ask ≈25 s；7 段分鏡**唔需要** 7 次開關遊戲（同一 session 內可連拍）。（**仍未量測**；第 1 輪錄影後回填真實數字。）
- 剪輯＋字幕＋TTS＋封面＋分發文案：3–5 小時（**估算，無歷史數據**）。
- 現金：HK$0。

---

## 10. 未解 / 等 SK

1. **Modrinth 項目頁 slug 已定＝`pack-ai-assistant`**（§8.3）；**頁面未開** → D7／A10 要等頁面真開才機檢（現時 `https://modrinth.com/mod/pack-ai-assistant` ＝404）。
2. B站中文長版（Phase 2）做唔做。
3. YouTube 自動上載要唔要（要你 OAuth；配額見 §7）。
4. **M1 批唔批**（要動 harness＋`AiAssistantScreen` 一支 static；dev-only、需 code review）——批咗才寫 §4b。
5. 首次真跑（R2 §5.3）：喺 ATM8 用 `/ai <問題>` 跑一次，讀 trace 驗 A9——**建議開工前先做呢個 2 分鐘實驗**（順手證 M1 立足點＋A9 ATM8 答案質素）。

---

## 11. 數字核實表（本 agent 2026-10-08 親手重跑；唔准靠印象）

| plan v4 聲稱 | 核實命令／來源 | 實測 |
|---|---|---|
| ATM8 `New World (1)` 大小（FC5） | Python `os.path.getsize` 求和（211 檔）；`du -sk`；`du -sb` | **19,273,115 bytes＝18.38 MiB**；`du -sk`＝19170 KB（≈18.7 MiB，**R2 講嘅「≈18.7 MB」＝19170÷1024 由此來**）；`New World`＝**3 bytes（空）** |
| ATM8 mod 數（FC5） | `ls .../packai_sandbox_atm8/minecraft/mods/*.jar \| wc -l`；`grep -c '<li>' .../packai_sandbox_atm8/modlist.html` | jar＝**380**；modlist `<li>`＝**381**（檔喺 **instance 根目錄**） |
| NFWC mod 數（FC5） | `ls .../packai_sandbox/minecraft/mods/*.jar \| wc -l` | **231** |
| ATM8 版本 | `mmc-pack.json` | MC **1.19.2** / Forge **43.2.14** |
| 1.19.2 sandbox 數量 | `ls instances/ \| grep packai_sandbox` | **6 個** |
| Modrinth slug 未佔用（2026-10-08 新增） | Python urllib `GET https://api.modrinth.com/v2/project/pack-ai-assistant` | **HTTP 404**（未佔用）；`pack-ai-assistant-paia` 亦 404 |
| 錄影幾何（FC2） | `EnumDisplayMonitors`（ctypes） | 主＝(0,0,2560,1440)＝**2560×1440**；副＝(-1080,-241,0,1679)＝**1080×1920** |
| `ds_peak_hours.py` 路徑（FC5） | `ls "$LOCALAPPDATA/hermes/scripts/ds_peak_hours.py"`；`ls repo/scripts/` | 真身＝`%LOCALAPPDATA%\hermes\scripts\`；repo **冇** `scripts/`。跑 `--json` → `OFF_PEAK`（2026-10-08 13:18 週四） |
| `move_window_to_monitor.py` 路徑 | `find "$LOCALAPPDATA/hermes/skills" -name move_window_to_monitor.py`；`find repo -name move_window*` | 真身喺 `hermes/media…/software-development/minecraft-mod-in-game-autotest/scripts/`；repo **0 命中** |
| 分鏡秒數 5+10+10+7+8+6+4 | `python` 心算核 | **50** |
| CTA 項目頁（FC4） | `docs/PUBLISH.md:26` | CurseForge `pack-ai-assistant-paia` id **1643097**；**Modrinth URL 未見於 repo** |
| M1 flag／注入（FC1） | `build.gradle:113-115`；`AutoTestHarness.java:96-107`／`:140`／`:197-209`／`:419`／`:658-662`；`AiClientCommands.java:22-34`；`AskService.java:103`；`AiAssistantScreen.java:78/161-162/428` | 全部實存；`grep -rni promo` Java 源碼＝**0 命中**（M1 未建） |
| `/ai` 及 `askAsync` 存在（R2 A2） | 同上 | ✅ `/ai <q>`（greedyString）→ `askAsync(q, cb)`；`askAsync(String,Consumer)` 存在 |

---

## 12. Review 記錄

| 輪 | 日期 | 反方主要攻擊 | 比分（正方:反方） | 處理 |
|---|---|---|---|---|
| R1 | 2026-10-07 | 11 條：片長 genre／gdigrab 黑片／DJ2 世代／沙盒空世界／computer_use＋harness／腳本路徑／A3 假綠／A5 tautology／3 支片／成本／旁白受眾 | **3 : 7** | v3 逐條修正 |
| R2 | 2026-10-08 | 4 條載重：LD-C 錄影幾何／LD-D M1 未 spec／LD-F A2↔§4 矛盾＋A3 假綠／LD-G 成本／LD-H 零分發（＋文件級） | **4 : 6** | 本 **v4** 逐條對 FC1–FC5（見 §0） |
| （R3，有界） | 待跑 | **只核 FC1–FC5，唔准加新要求**；全部 RESOLVED 又無新自相矛盾 → ≥8:2 | — | 距 3–4 輪硬上限仲有 1–2 輪 |

> 依 skill：若 R3 仍 <8:2 → 停手，交「逐輪比分＋卡死點＋最貴未知＋3 選項」畀 SK。


---

## 附錄 A（2026-10-08 SK 決定）：分發第三渠道＝Modrinth

SK 2026-10-08 原話：「mod 頁兩個都要」→ 除 **CurseForge 項目頁**（`pack-ai-assistant-paia`，id `1643097`，見 `docs/PUBLISH.md`）外，**同時新開 Modrinth 專案頁**。

- D7／§8 分發包要加一段 **Modrinth 上架 checklist**（專案頁現時**未存在**，屬新增工作）：
  1. 開 Modrinth 專案（slug＝**`pack-ai-assistant`**，SK 2026-10-08 拍板；同日核未佔用）
  2. 上傳同版本 jar（release 只用正名 `.jar`；`-thin`／`-sources` 唔要）
  3. 專案頁文案（**禁 AI 生成圖**；**必須披露 AI 使用**）
  4. 版本 changelog
  5. 標籤／分類／授權
  6. 互相連結（Modrinth ↔ CurseForge ↔ 影片描述）
- A10 機檢項要同時覆蓋 **CurseForge ＋ Modrinth 兩頁**。
- ⚠️ 本檔前文寫「Modrinth slug〔待 SK 確認〕」→ **2026-10-08 SK 拍板＝`pack-ai-assistant`**（Hermes 建議；SK 答「use ur suggest」）。同日用 `api.modrinth.com/v2/project/pack-ai-assistant` 核＝**HTTP 404（未佔用）**。**頁面未開**；開頁後把真 URL 回填 §8.3、並更新 §11 核實表。
