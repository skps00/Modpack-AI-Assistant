# Review — 宣傳片計畫 v3，**Round 2**（2026-10-08）

> 受審檔：`docs/promo/PROMO_PLAN-v3-2026-10-07.md`（11249 bytes，sha 基準＝本次讀入版本）
> 對照：`PROMO_PLAN-v2-2026-10-07.md`／`PROMO_PLAN-2026-10-07.md`／`RESEARCH-mod-promo-videos-2026-10-07.md`
> 方法：skill `adversarial-decision-review` 三階段（① 反方 → ② 正方 → ③ 中立裁判）。
> Round 1＝**正方 3 : 反方 7**（記錄於 v3 §12）。本輪＝Round 2。
> 所有數字由**本 agent 在 2026-10-08 對真 artifact 重跑**（命令列於 §1），唔照抄 plan 或 R1 記錄。

---

## 0. 一頁判決（for SK）

- **Round 2 比分：正方 4 : 反方 6**（未達 ≥8:2）→ **唔可以照 v3 開工**。
- v3 **真係修好咗 R1 嘅 11 條攻擊**（逐條核過，見 §1），但殺分嘅係 **plan 自己新露出／一直冇答嘅 4 個載重決定**：
  ① 問答驅動（M1 harness promo 模式）**冇 spec／冇驗收／冇成本**，而佢係最重要嗰格畫面嘅唯一來源；
  ② 錄影幾何**自相矛盾**（記錄副螢幕＝1080×1920 **直向**，但要出 1920×1080 master）；
  ③ 驗收 A2 同 §4 故事板**打架**（shot 1/6/7 冇 trace）＋ A3 像素閘只抽 10 幀（≈0.33% 覆蓋）；
  ④ 成個「宣傳」plan **冇任何分發／觀眾交付物**（去邊個 channel、標題／描述／縮圖、CTA URL）→「拍完有冇人睇」零對策。
- **最大未知**：harness promo 模式喺沙盒真跑時，到底叫唔叫得出「面板＋答案＋配方卡」——呢個係全 plan 唯一嘅實證缺口，**一次真跑（唔使寫新碼，用 `/ai` 或現有 harness）2 分鐘就知**。
- **輪次**：R1 3:7 → R2 4:6。距 3–4 輪硬上限仲有 1–2 輪 → 建議**一輪有界 R3**，只核 §5 嘅 FC1–FC5。

---

## 1. R1 十一條攻擊：v3 到底修咗未（逐條對真 artifact 核）

| # | R1 攻擊 | v3 回應（location） | 本輪核實 | 判定 |
|---|---|---|---|---|
| 1 | 片長 genre 錯 | §1 重新定位為「project page 片」（L17-18） | 重定位係**講法**，唔係新證據；genre 證據（Fumora）實為 **server trailer 指南**，套落 client mod 專案頁＝類比；目標頁觀眾行為**冇數據** | **PARTIAL** |
| 2 | gdigrab 黑片 | §4.5 M0 三工具各錄 10 s＋客觀亮度／方差判準；ddagrab 為主（L29, L58） | `ffmpeg -h filter=ddagrab` 有（ffmpeg 9.0-full_build）；`-devices` 有 gdigrab。修法具體、可測 | **RESOLVED** |
| 3 | DJ2 世代不相容 | §2 改用 ATM8（L28） | `packai_sandbox_atm8/mmc-pack.json` → MC **1.19.2**、Forge **43.2.14** ✅；DJ2 → MC **1.12.2**/14.23.5.2860 | **RESOLVED** |
| 4 | 沙盒空世界 | §4.3 改 shot 1 為 UI 過載感（L56） | 結論成立（saves 只有 `New World`／`New World (1)`），但**引用數字錯**：plan 寫 `New World (1)` **1.2 MB**，實測 **19170 KB ≈ 18.7 MB**（`du -sk`） | **RESOLVED（數字錯）** |
| 5 | computer_use 唔穩＋harness 唔收 free-text | §2 加 M1 harness promo 模式為首選＋computer_use fallback（L30, L59） | `AiAssistantScreen.java:108 openAndAskAbout(ItemStack)` 真係只收 ItemStack ✅；但 **M1 零 spec／零驗收／零成本**（§3 交付物冇佢、§6 A1-A8 冇佢、§9 冇佢） | **PARTIAL（實質未解）** |
| 6 | 腳本路徑錯 | §4.4 改為 `skills/software-development/.../move_window_to_monitor.py`（L57） | 該檔真身喺 `C:\Users\skps9\AppData\Local\hermes\skills\...`（唔係 repo 相對）；**同一條 §4.1 又寫死 `scripts/ds_peak_hours.py`** → repo 內 `scripts/` 目錄**唔存在**（真身 `hermes\scripts\ds_peak_hours.py`）＝同類錯換個位復發 | **PARTIAL（新引入）** |
| 7 | A3 假綠 | §6 A3 改雙層：① checker ② 成品像素 OCR（L92） | `tests/check_ask_display_leak.py` 存在（31321 bytes），`FORBIDDEN` tuple 喺 :50、讀 `--log`(:865)＝**log 層** ✅；但像素層只抽 **10 幀**（≈50 s×60 fps=3000 幀嘅 **0.33%**） | **PARTIAL（假綠風險仍在）** |
| 8 | A5 自相矛盾＋tautology | §6 A5 重寫＋明寫「已知限制：半自我核對」（L94） | 誠實、定義清楚 | **RESOLVED** |
| 9 | 「3 支片」無驗收 | §1 縮為 2 片＋1 封面，B站入 Phase 2（L11-18, L48） | 一致（§3 D1-D6、§6 逐件有驗收） | **RESOLVED** |
| 10 | 成本無根據 | §9 標明「未經量測估算」＋錨（L126-130） | 標籤正確，但**仍然冇量測基礎**，且 **M1 完全冇計入成本** | **PARTIAL** |
| 11 | 英文旁白同中文需求錯配 | §2 中文觀眾交 Phase 2（L31） | 係**延後**唔係解決；可接受（scope 已聲明），但旗艦交付物受眾仍係英文 | **PARTIAL** |

小結：**RESOLVED 5**（#2/#3/#8/#9 ＋ #4 帶數字錯）、**PARTIAL 6**（#1/#4/#5/#6/#7/#10/#11 中 6 條）。有界 Round 2「只核 R1 flip conditions」都**過唔到 8:2**（最佳約 6:4）。

---

## 2. 數字／路徑重算表（唔准靠印象；全部今日親跑）

| plan 聲稱（行） | 重算命令 | 實測 | 判定 |
|---|---|---|---|
| ATM8 = 381 mods（L28） | `ls .../packai_sandbox_atm8/minecraft/mods/*.jar \| wc -l` ／ `grep -c "<li>" modlist.html` | jar=**380**；modlist `<li>`=**381** | predicate 差（mod 清單 vs 實檔）→ 要寫明用邊個 |
| NFWC = 232 mods（L28） | `ls .../packai_sandbox/minecraft/mods/*.jar \| wc -l` | **231** | predicate 差 |
| ATM8 = 1.19.2 / Forge 43.2.14（L28） | `cat mmc-pack.json` | 1.19.2 / 43.2.14 ✅ | 正確 |
| DJ2 = 1.12.2（L28） | `cat DJ2-.../mmc-pack.json` | 1.12.2 / 14.23.5.2860 ✅ | 正確 |
| ATM8 saves `New World (1)` = 1.2 MB（L56） | `du -sk .../saves/*/` | 19170 KB ≈ **18.7 MB** | **錯（≈15×）** |
| 「1.19.2 sandbox 只有 6 個」（L28） | `ls instances/ \| grep packai_sandbox` | 6 個 ✅ | 正確 |
| 劇本秒數 5+10+10+7+8+6+4（L62） | 心算＋`python -c` | **50** ✅ | 正確 |
| YouTube `videos.insert`=1600、預設 10000/日、另有 100 次/日桶（L106） | Google developers 文件（§6 來源） | 1600 units、10,000/日、`videos.insert` 獨立桶 **100/日** ✅ | 正確（引文精準） |
| Modrinth §6.1／§6.2 引文（L103-104） | https://modrinth.com/legal/rules | §6.2「No **images** … may be created or derived from generative AI output」、§6.1 四款披露 ✅ | 正確（節號＋原文都對） |
| `scripts/ds_peak_hours.py`（L54） | `ls scripts/`（repo）／`ls hermes/scripts/` | repo **冇** `scripts/`；真身 `hermes\scripts\ds_peak_hours.py` | **路徑錯** |
| move_window 腳本「實測存在」（L57） | `find . -name move_window*` ／ hermes 路徑 | repo 內 **0 命中**；真身喺 `hermes\skills\...\scripts\` | 路徑寫法易誤解 |
| 沙盒 mods 內 packai jar（L55） | `ls .../mods/ \| grep -i packai` | 各 1 支 `packai-autotest-dev.jar` ✅（單 jar） | 正確 |

---

## 3. 第 ① 階段 — 反方（只攻擊，禁止支持）

> 逐條：**證據（file:line／命令）＋嚴重度＋反轉條件**。

**A1（CRITICAL，載重決定 LD-D）核心機制「M1 harness promo 模式」係一個未建、未 spec、未驗收、未計成本嘅黑箱。**
- 證據：全 repo `grep -rni "promo" --include=*.java forge/1.19.2/src/main/java` = **0 命中**（唯一 "promote" 係無關嘅 QuestGuide.java:697 comment）；`AutoTestHarness.java` 只有 `active()/tick()/openWorld()`，冇 free-text ask 入口；plan §10(4) 自己列「M1 要動 harness 碼…批唔批」＝**未定**。
- 而 plan §3 交付物（D1–D6）**冇 M1**、§6 驗收（A1–A8）**冇 M1**、§9 成本**冇 M1**。⇒ 最重要嘅 shot 2（打白話問題→答案＋卡）**唯一來源**係一個唔存在、唔知做唔做得到、唔知幾貴嘅功能。
- 反轉條件：M1 有 flag 名＋注入路徑＋一條專屬 acceptance＋一行成本，即翻。

**A2（HIGH，LD-D）plan 漏報一個現成 free-text 路徑，令 M1「要一段新 harness 碼」嘅成本聲稱不可信。**
- 證據：`AiClientCommands.java:27` 已註冊 `/ai <question>`（`StringArgumentType.greedyString()`）→ `ClientSetup.askService().askAsync(q, …)`；`AskService.java:103` 有 `askAsync(String,…)`。另 `AiAssistantScreen.java:78/161-162` 已有 `draftInput` 預填機制。
- ⇒ free-text **ask pipeline 早就存在**；真正欠嘅只係「把問題餵入面板並自動提交」。plan 把自己講成「要一段新 harness 碼（M1，需 code review）」係**overstate 工作量**（skill 明列嘅「missed facts：已有元件已 cover」）。
- 反轉條件：一句講清缺嘅只係 panel 驅動層，其餘重用 `/ai` 與 `draftInput`。

**A3（HIGH，LD-C）錄影幾何自相矛盾：記錄副螢幕（1080×1920 直向）但要出 1920×1080 master。**
- 證據：`move_window_to_monitor.py` docstring 實測記錄 `secondary_rect=[-1080,-241,1080,1920]`＝**直向**（預設 `monitors()[1]`＝副螢幕）；plan §4.4「即移窗去副螢幕」＋§4.5「`ddagrab -i output_idx=<副螢幕>`」＋§3 D1 `1920x1080`。
- `ddagrab` 係 **output（整個 monitor）** 抓取，**唔可以指定單一窗**（`ffmpeg -h filter=ddagrab`：Desktop Duplication）。⇒ 照字面錄到嘅係直向畫面：要麼大裁切／黑邊，要麼把 ≤1080 寬嘅畫面 upscale 上 1920＝軟。
- 反轉條件：改錄**主螢幕**（2560×1440 橫向；腳本 `move_window_to_monitor.py 0`）＝trivial 修，或明文寫死裁切／補邊方案。

**A4（HIGH，LD-F）驗收同故事板打架：A2 要求「每格都有 trace 絕對路徑」，但 shot 1/6/7 根本冇 trace。**
- 證據：§6 A2（L91）vs §4 表：shot 1＝JEI 捲動（證據「錄影時間碼」）、shot 6＝設定畫面（證據「截圖」）、shot 7＝標題卡（證據「—」）。⇒ A2 照字面跑**必然紅**，唯一「過關」方法係改斷言（＝造綠）。
- 反轉條件：A2 收窄為「答案類格（shot 2/3/5/6 之中有答案者）要有 trace」，其餘格另有證據類型。

**A5（MEDIUM，LD-F）A3 像素層只抽 10 幀＝0.33% 覆蓋，係假綠溫床。**
- 證據：§6 A3②「由成品抽 **10 幀** 掃 raw-token」（L92）；片長 50 s×60 fps≈3000 幀。raw token 若只出現喺冇抽到嘅幀，閘照綠。
- 反轉條件：掃**每格答案段嘅首／中／尾**並報覆蓋率，或對答案全文（非抽幀）落 OCR。

**A6（HIGH，LD-G）成本模型冇量測基礎，且 M1 未計。**
- 證據：§9（L126-130）自認「未經量測估算，無歷史數據」；剪輯「3–5 小時」無錨；M1（新碼＋code review）0 成本行。
- 反轉條件：跑 M0（三工具各 10 s）＋一次真 ask，回填實測秒數。

**A7（CRITICAL，LD-H）「宣傳片」plan 完全冇分發／觀眾面交付物——「拍完有冇人睇」零對策。**
- 證據：§3 交付物＝2 片＋1 封面＋notes／licenses／checklist，**冇**「上邊個 channel／項目頁 URL／標題／描述／縮圖文案／發佈 checklist／CTA 連結」。§7 只講 quota 唔講 channel。§10(2) 仲要問 SK「YouTube 自動上載要唔要」＝連自唔自建 channel 都未定。
- 而 plan 對外 CTA 對象（CurseForge 頁）**真係存在**：`docs/PUBLISH.md:26` `pack-ai-assistant-paia` id **1643097** —— plan 由頭到尾冇引用過呢個 URL。
- ⇒ 以「宣傳」為名，卻只優化 artifact、唔優化 funnel＝**機會成本**：同樣工時放喺專案頁 copy／截圖／文字教學，可能更直接帶來下載。
- 反轉條件：加一件 D7「分發包」＝channel／URL／文案／縮圖／checklist。

**A8（MEDIUM，LD-A）片長 / genre 證據係套用錯受眾嘅指南。**
- 證據：`RESEARCH…:118` 引嘅係 **Fumora《Minecraft Server Trailer》**（招 server 玩家），唔係 client mod 專案頁；研究 §5 自認「CF 專案頁未讀（403）」「r/feedthebeast 403」→ 目標頁觀眾行為**零數據**。
- 反轉條件：補專案頁／社群對「專案頁嵌入片」嘅實際行為數據，或明寫「此為類比推論」。

**A9（MEDIUM，LD-B）揀 ATM8 但未證 packai 喺 ATM8 上答得到、答得好。**
- 證據：ATM8 有 380 jar（重包）；plan 冇任何「packai 喺 ATM8 真跑過並出到合格答案」嘅證據（jar `packai-autotest-dev.jar` 存在，但未見 ATM8 上嘅 trace）。demo 賣點係「真機真答案」，若 ATM8 上答案質素差（或索引過慢），成條片立論崩。
- 反轉條件：喺 ATM8 跑一次真 ask＋讀 trace，貼 `display.body.final` 出嚟。

**A10（LOW／文件級）**
- `New World (1)` 大小 1.2 MB vs 實測 18.7 MB（§2 表）。
- `scripts/ds_peak_hours.py` repo 相對路徑唔存在（真身 hermes）。
- mod 數 381/380、232/231 未寫 predicate。
- §1 目標寫「每格畫面都係真機真答案」但 shot 1/6/7 並非「答案」（措辭 overclaim，同 A4 同源）。

---

## 4. 第 ② 階段 — 正方（最強支持）

**S1（強）v3 真係逐條修好 R1 嘅 11 條**：DJ2→ATM8 已用 `mmc-pack.json` 證實；A5 由「100% 對得上」改成誠實嘅「≤0.5 s 偏差＋半自我核對」；「3 支片」縮成 2＋1 消除懸空承諾；gdigrab 黑片有 M0 客觀判準。呢啲係**實質範圍／設計修改**，唔係換 prompt 擲骰。（本輪核實：RESOLVED 5、PARTIAL 6。）

**S2（強）平台規則引用精準、可審計**：Modrinth §6.1/§6.2 節號＋原文、YouTube 1600 units＋100/日獨立桶，全部同官方文件逐字對得上（§2 表）。呢啲係硬紅線，引對＝避開下架／removal。

**S3（強）風險面極低、零不可逆**：全程沙盒、HK$0 現金、WM_CLOSE 自然退出、jar 只准 `mc_mod_deploy_jar.py`、沙盒壞咗可重複製。`packai_sandbox_atm8`／`packai_sandbox` 兩個 instance 真實存在、版本正確、各只有一支 `packai-autotest-dev.jar`（單 jar 陷阱已避）。呢個 plan 嘅 downside 幾乎係零。

**S4（中強）誠實度高**：plan 主動標「未經量測估算」「M1 要 SK 批」「A5 半自我核對」——對反方最愛打嘅「假精度／假綠」有免疫基礎。

**S5（中）關鍵機制大部分**早已存在：`AutoTestHarness.openWorld` 用 `mc.createWorldOpenFlows().loadLevel(title, world)`（AutoTestHarness.java:265）＝plan §4.4「入世界用真 API `loadLevel(...)`」係真嘅；`/ai <q>`＋`askAsync(String)`＋`draftInput` 已在（§3 A2）。⇒ M1 唔係由零起，係「薄驅動層」。

**S6（中）交付物範圍收窄後自洽**：D1–D6 逐件有對應驗收（A1/A7/A2/A3/A6/A8），除咗 A2/A3 兩處（見反方 A4/A5）。

**S7（中）避開咗錯誤路線**：明拒 AI 生成圖放 Modrinth、明拒 YAL 音樂直接上 CF、明拒 B站自動化登入——同 SK 品牌紅線一致。

---

## 5. 第 ③ 階段 — 中立裁判（唔平均；用載重決定存活表計分）

### 5.1 載重決定存活表

| LD | 載重決定 | 反方判定 | 正方判定 | 裁判裁決（證據） |
|---|---|---|---|---|
| LD-A | 片種＝project-page 45–60 s | 死（證據係 server-trailer 指南，目標頁零數據） | 站得住（industry trailer 43 s–2:43 有樣本） | **存活（有保留）**：方向可，但要把「類比推論」明寫 |
| LD-B | 背景 pack＝ATM8 | 死（未證 packai 喺 ATM8 答案質素） | 站得住（版本／單 jar 已核） | **存活（有保留）**：缺一次真跑證明 |
| LD-C | 錄影工具 ddagrab | **死**（output-only ＋ 直向副螢幕 vs 16:9 master） | 有保留（有 fallback，M0 可測） | **死**（幾何衝突未解；翻面成本低） |
| LD-D | 問答驅動＝M1 harness promo | **死**（未建、未 spec、未驗收、未計成本；漏 `/ai` 現成路徑） | 有保留（機制大部分已有） | **死**（最重要 shot 嘅唯一來源＝黑箱） |
| LD-E | 交付物＝2 片＋1 封面 | 站得住 | 站得住 | **存活** |
| LD-F | 驗收 A1–A8 | **死**（A2↔§4 打架、A3 抽樣假綠） | 有保留（A1/A5/A6/A7/A8 都硬） | **死** |
| LD-G | 成本模型 | **死**（未量測＋M1 未計） | 有保留（已誠實標估算） | **死** |
| LD-H | 推廣／分發（有冇人睇） | **死**（零分發交付物；CTA URL 存在都冇用） | —（正方未提，等於默認弱） | **死** |

→ 存活 3（A/B/E，其中 A/B 有保留）、死 5（C/D/F/G/H）。

### 5.2 比分

**正方 4 : 反方 6**。

- 唔用機械平均（若 A/B 半分則約 2:6），亦唔放水到 5:5：**最重分量嘅證據**（A1/A7＝核心機制黑箱＋零分發）兩條都係 CRITICAL 級，反方明顯較強。
- 記分理由：v3 對 R1 嘅實質修正值得 +1（R1 3:7 → R2 4:6），但新露出嘅 3 條載重缺陷（LD-C/D/H）＋1 條自相矛盾（LD-F）把分數壓住。
- **診斷（比分形狀）**：R1 死喺 **spec 細節**（tooling／政策／路徑）；R2 死喺 **載重結構**（機制未 spec、幾何矛盾、冇分發）。**兩次 3–4 分唔係原地踏步，係死因換咗層**——呢個正正係 skill 講嘅「上一輪修法帶出新洞」訊號。

### 5.3 最大未知（＋最便宜嘅收口實驗）

- **最大未知**：harness promo 模式（M1）喺沙盒真跑時，到底可唔可以令面板出到「答案＋配方卡」並落 trace。
- **收口實驗（今日可做，唔使寫新碼）**：喺 `packai_sandbox_atm8` 起一次，用現成 `/ai <問題>`（AiClientCommands.java:27）或現有 harness 跑一次 ask → 讀 `packai/trace/ask-*.jsonl` 嘅 `display.body.final` 同 `render.cards.final.cardsOut`。**2 分鐘**就知 M1 有冇立足點，亦順手答 A9（ATM8 答案質素）。
- skill 明示：凡「只有真跑才知」嘅項 → **停 review、跑最便宜嘅實驗**，唔好再開 review 輪。

### 5.4 反轉條件（＝下一輪嘅 acceptance list）

達成以下即 8:2（逐條可機檢）：

- **FC1（LD-D）**：M1 有 flag 名＋注入路徑（重用 `draftInput` 預填＋自動提交，或 `/ai`）＋**一條專屬 acceptance**（落 §6）＋**一行成本**；並聲明 M1 屬 dev-only、要真 jar 核實（唔准入正式版）。
- **FC2（LD-C）**：§4.4/§4.5 改成錄**主螢幕**（2560×1440 橫向）或明文寫死裁切／補邊方案；M0 A/B 保留。
- **FC3（LD-F）**：A2 收窄（只有答案類格要 trace，其餘格另有證據類型）；A3 像素閘改成覆蓋答案段首／中／尾並報覆蓋率。
- **FC4（LD-H）**：加 D7「分發包」＝channel＋項目頁 URL（`pack-ai-assistant-paia` id 1643097）＋標題／描述／縮圖文案＋CTA＋發佈 checklist。
- **FC5（文件級）**：修 `New World (1)` 大小（≈18.7 MB）、`scripts/ds_peak_hours.py` 路徑（hermes）、mod 數 predicate（381 modlist／380 jar）、§1「每格都係真答案」措辭。

---

## 6. 本輪用過嘅來源（可重跑）

- 本地 artifact：`docs/promo/PROMO_PLAN-v3-2026-10-07.md`、`PROMO_PLAN-v2-…`、`PROMO_PLAN-2026-10-07.md`、`RESEARCH-mod-promo-videos-2026-10-07.md`、`docs/PUBLISH.md:26`、`.hermes/plans/HANDOFF.md`。
- 程式碼：`AiClientCommands.java:27`、`AskService.java:103/107/121`、`AiAssistantScreen.java:78/108/161-162/419`、`AutoTestHarness.java:265`、`tests/check_ask_display_leak.py:50/865`。
- 環境：`PrismLauncher…/instances/packai_sandbox_atm8/{mmc-pack.json,mods,modlist.html,saves}`、`packai_sandbox/…`、`DJ2-Cleanroom-Client-v1.2/mmc-pack.json`；`ffmpeg 9.0-full_build`（ddagrab／gdigrab）；`hermes/scripts/ds_peak_hours.py`；`hermes/skills/.../move_window_to_monitor.py`。
- 官方／社群：Modrinth Content Rules（2026-08-13）https://modrinth.com/legal/rules ；Modrinth Advanced Markdown（iframe 只准 YouTube／Discord）https://support.modrinth.com/en/articles/8801962 ；YouTube Data API 配額 https://developers.google.com/youtube/v3/determine_quota_cost ；YouTube AI 披露政策（generic TTS 免披露）https://blog.youtube/news-and-events/disclosing-ai-generated-content/ 。

## 7. 輪次記錄

| 輪 | 日期 | 正方:反方 | 卡死嘅載重決定 |
|---|---|---|---|
| R1 | 2026-10-07 | **3 : 7** | 片長 genre／gdigrab／DJ2 世代／沙盒空世界／驅動機制／路徑／A3 假綠／A5 tautology／3 支片／成本／旁白受眾 |
| R2 | 2026-10-08 | **4 : 6** | **LD-C 錄影幾何／LD-D M1 未 spec／LD-F A2↔§4 矛盾／LD-G 成本／LD-H 零分發** |

> 距 3–4 輪硬上限仲有 1–2 輪。建議：**一輪有界 R3**，dispatch 指令明寫「只核 FC1–FC5，唔准加新要求；全部 RESOLVED 又冇新自相矛盾 → 直接 ≥8:2」。
> （依 skill：若 R3 仍 <8:2 → 停手，交「逐輪比分＋卡死點＋最貴未知＋3 選項」。）
