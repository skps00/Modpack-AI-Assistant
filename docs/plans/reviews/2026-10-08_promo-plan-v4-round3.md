# Review — 宣傳片計畫 v4，**Round 3（有界）**（2026-10-08）

> 受審檔：`docs/promo/PROMO_PLAN-v4-2026-10-08.md`（26475 bytes；sha256 `9006f7d12fa5e811196af6ad7b606e87d8c308f8e8b33df771d79720d19462fc`）
> 上輪：R2 正方 4 : 反方 6（`docs/plans/reviews/2026-10-08_promo-plan-v3-round2.md`，§5.4 有 FC1–FC5 全文）
> 對照：`PROMO_PLAN-v3-2026-10-07.md`
> 方法：skill `adversarial-decision-review`（① 反方 → ② 正方 → ③ 中立裁判）。
> **本輪界線（SK 訂）**：**只核 R2 §5.4 嘅 FC1–FC5 五條 flip condition，唔准加新要求**；全部 RESOLVED 且無新自相矛盾 → ≥8:2。
> 所有數字由**本 agent（獨立 reviewer）2026-10-08 親手對真 artifact 重跑**，**唔照抄 v4**；出處見 §4。

---

## 0. 一頁判決（for SK）

- **Round 3（有界）比分：正方 8 : 反方 2** → **`v4_adequate=true`**。
- **FC1–FC5 全部 RESOLVED**（逐條附檔案＋行號＋親測證據，見 §3）。
- 反方本輪**搵唔到「寫得好聽但其實冇閉合」嘅位**：五條 FC 每一條嘅要求都在 v4 有可機檢落點，而且落點嘅事實全部經我重跑核實（唔止 v4 自報）。
- 只餘 **2 條 cosmetic 文件級小瑕疵**（非載重、唔改任何決定、唔屬 FC1–FC5 範圍，故**唔**阻 8:2）：
  ① §10(4)「批咗才寫 §4b」係 v3 遺留措辭——§4b 其實已經寫好，字面應讀「批咗才**實作** §4b」；
  ② §4.5〔判讀〕「16:9 源裁 9:16 只得 810×1440」把切圖數字鬆散掛喺「master」一字：810×1440 係**由 2560×1440 原生錄影**裁 9:16 的結果（正確）；若真喺 1920×1080 master 裁 9:16 其實係 608×1080（×1.78 更軟）——結論（原生直向另錄）不受影響。
- **未有新自相矛盾**：以上兩條係措辭/舉例鬆散，非邏輯互相排斥；五條 FC 之間、FC 與 §1–§11 之間無打架。
- 依 skill：R1 3:7 → R2 4:6 → **R3 8:2**（逐輪有實質修改，唔係擲骰）。達標，**唔使停手交 SK**。

---

## 1. 本輪方法與界線

- **界線**：只判 FC1–FC5 有冇閉合 + v4 有冇**新**自相矛盾。**唔重開** LD-A／LD-B／LD-E 等 R2 已裁「存活（有保留）」嘅題；**唔加**任何 R2 §5.4 冇寫嘅新要求。
- **紀律**：`docs/plans/*.md` 內每個數字／行號，**我自己重跑一次**（唔靠 v4 §11 自報）。呢點係 skill 對「數字核實方」嘅硬要求。

---

## 2. 第 ① 階段 — 反方（只攻擊 FC1–FC5；禁止支持）

> 逐條：**證據（file:line／命令）＋太唔太似「講得好聽但未閉合」＋反轉條件**。

**O1（FC1，LD-D）——檢查 M1 spec 係真閉合抑或只係「改名扮有 spec」。**
- v4 §4b 聲稱：flag＝`packai-autotest.flag`；注入路徑＝重用 `/ai <question>`（`AiClientCommands.java:22-34`）＋`AskService.askAsync()`（:103）；欠嘅只係「面板驅動層」（`draftInput` 預填＋自動送出）；acceptance＝A9（`display.body.final` 非空＋`render.cards.final cardsOut≥1`）；成本一行；dev-only 聲明。照字面 **FC1 五個要件齊**。
- **反方戰**：`/ai` 只把答案丟去 **chat**，但 shot 2 要出「面板＋答案＋配方卡」→ v4 承認要**額外**改 harness（`CaseSpec` 加 `question`、`startCase` 加分支、`Judge` 改 key）。即係「零新 pipeline」係講法，實作仍要**動 dev-only harness 3 處＋1 支 static**。會唔會係「用／ai 存在」掩蓋「promo 面板驅動其實未證做得到」？
- **反轉條件**：flag／注入／acceptance／成本／dev-only 五件真係齊且可機檢。

**O2（FC2，LD-C）——檢查錄影幾何係真修好抑或換個講法。**
- v4 聲稱：錄**主螢幕 2560×1440（16:9）**，`move_window_to_monitor.py 0`＋`ddagrab -i output_idx=0`；master 由 2560×1440 **downscale**（零裁切零 upscale）；D2 改**副螢幕原生 1080×1920 另錄**；M0 A/B 保留。
- **反方戰**：v4 自報「實測主＝2560×1440」——若螢幕真身唔係 2560×1440，整條 16:9 論證即崩（＝照抄舊結論）。另 `ddagrab` 係 output-only（抓整個 monitor），v4 有冇老實講「唔可以指定單一窗」？
- **反轉條件**：主螢幕真係 2560×1440 橫向；D2 真係原生 1080×1920 副螢幕，唔係 upscale。

**O3（FC3，LD-F）——檢查 A2 收窄有冇「偷放水」，A3 覆蓋率係真抽樣抑或仍係假綠。**
- v4 聲稱：A2 只有答案類格（shot 2/3/4/5）要 trace，shot 1/6/7 用另一種證據類型；A3② 抽每段答案**首／中／尾三幀**＋**報覆蓋率**。
- **反方戰**：A2 收窄後會唔會「所有格都話自己係非答案格」而閘形同虛設？A3「三幀/段」對 4 段答案＝12 幀，覆蓋率仍低——係真 fix 抑或由 10 幀改 12 幀嘅數字遊戲？
- **反轉條件**：答案格 vs 非答案格有清晰、可機檢嘅二分；A3 明寫「報已掃答案段數／幀數＝覆蓋率」而非只改幀數。

**O4（FC4，LD-H）——檢查 D7「分發包」係真三渠道抑或填表。**
- v4 聲稱：加 D7＝三渠道（YouTube／Bilibili／項目頁），各齊 標題／描述／縮圖文案／CTA／發佈 checklist；項目頁 URL＝CurseForge `pack-ai-assistant-paia` id 1643097。
- **反方戰**：項目頁 URL 有冇真出處？Modrinth slug 係咪「假裝齊全」？三個渠道係咪真有 **CTA URL**（可 HTTP 200）抑或空殼？
- **反轉條件**：URL 有 repo 內出處；Modrinth 若未知則老實標﹝待確認﹞；三渠道各段真齊五元素＋A10 可機檢。

**O5（FC5，文件級）——重算 v4 改嘅四個數字／路徑。**
- 反轉條件：`New World (1)` 大小、`ds_peak_hours.py` 路徑、mod 數 predicate、§1 措辭 ——全部改到且同真 artifact 一致（**我自己量**）。

---

## 3. 第 ②＋③ 階段 — 中立裁判：逐條判 RESOLVED／UNRESOLVED（附**我親測**證據）

> 記法：`v4 落點` → `我嘅核實` → `判定`。

### FC1（LD-D）M1 spec 五件齊 → **RESOLVED**
| 要件 | v4 落點 | 我嘅親測（獨立） | 判 |
|---|---|---|---|
| flag 名 | `packai-autotest.flag`（§4b(1)） | `AutoTestHarness.java:96` `getResourceAsStream("/packai-autotest.flag")`，:97-99 null→false ✅ | ✅ |
| flag 只喺 dev build | `build.gradle:113-115` | :113 `if (project.hasProperty('packaiAutotest'))`、:114 `sourceSets.main.resources.srcDir 'src/autotest/resources'`、:115 `archiveBaseName='autotest-dev'` ✅ | ✅ |
| 注入路徑 | 重用 `/ai`＋`askAsync`（§4b(2)） | `AiClientCommands.java:22` greedyString argument、:27 `askService().askAsync(q,…)`（v4 引 22-34 正確）；`AskService.java:103` `askAsync(String,Consumer)` ✅ | ✅ |
| 面板驅動層 | `draftInput`\(:78\)＋預填\(:161-162\)＋`sendCurrent()`→askAsync\(:428\) | `AiAssistantScreen.java:78` `private String draftInput`、:161-162 `if(!draftInput.isEmpty()) input.setValue(...)`、:428 `askService().askAsync(...)` ✅ | ✅ |
| 專屬 acceptance | A9（§6）：`display.body.final` 非空＋`render.cards.final cardsOut≥1` | `AutoTestHarness.java:658` `"display.body.final"`、:660-662 `"render.cards.final"`＋`cardsOut` ✅ 事件名真存在，機檢可行 | ✅ |
| 成本一行 | §4b(4)：1 薄改動＋1 compile＋1 autotest；1–2 h；錨 | 有寫、有錨（skill 一輪 autotest≈10–15 min、單 ask≈25 s）；`grep -rni promo *.java`＝0（誠實標未建） | ✅ |
| dev-only 聲明 | §4b(5)：出廠 jar 無 flag ⇒ `active()` false ⇒ inert；唔准入正式版 | `active()` 讀 flag，jar 冇 flag 即 false ✅ | ✅ |
- **裁判**：O1 攻擊**不成立**（作反轉條件）。v4 **有**老實承認要動 harness 3 處＋1 static（§4b(2)①-③ 逐點列）；「零新 pipeline」係準確描述（ask pipeline 真已存在）。§4b(3) 嘅 A9 事件名經我核到真碼（:658/:660），**唔係杜撰驗收**。→ **RESOLVED**。

### FC2（LD-C）錄影幾何改主螢幕 2560×1440 → **RESOLVED**
- **我親測 `EnumDisplayMonitors`（ctypes）**：`monitor[0] rect=(0,0,2560,1440)→2560×1440`（＝主）；`monitor[1] rect=(-1080,-241,0,1679)→1080×1920`（＝副，直向）；`GetSystemMetrics(0/1)=2560×1440`。**同 v4 §2/§11 逐字一致**。
- v4 §4.4/§4.5：`move_window_to_monitor.py 0`（主）＋`ddagrab -i output_idx=0`；D2＝`move_window_to_monitor.py 1`（副）＋`output_idx=1` 原生直向另錄。M0 三工具 10 s A/B **保留**（§2/§4.5）。
- 我實測 `move_window_to_monitor.py` 真身＝`%LOCALAPPDATA%\hermes\skills\software-development\minecraft-mod-in-game-autotest\scripts\`（repo **0 命中**）——v4 路徑寫法正確。
- **裁判**：O2 攻擊**不成立**。主螢幕係真 16:9 2560×1440；v4 亦老實寫「`ddagrab` 係整個 monitor 抓取、唔可以指定單一窗」（§2）。幾何衝突（R2 LD-C 死因）**閉合**。→ **RESOLVED**。

### FC3（LD-F）A2 收窄＋A3 覆蓋率 → **RESOLVED**
- A2（§6，v4）：只有**答案類格**（shot 2/3/4/5）要 trace；shot 1/6/7 用**另一種證據類型**（時間碼／截圖／純卡）；並明寫「**唔會必然紅**」。§4 分鏡表嘅「證據類型」欄逐格對應（shot1 時間碼／shot2-5 答案格／shot6 截圖／shot7 純卡）——**self-consistent**。
- A3②（§6，v4）：抽**每段答案之首／中／尾三幀**（非 10 幀），**報告「已掃答案段數／已掃幀數＝覆蓋率」**。
- **裁判**：O3 攻擊**不成立**。二分（答案格 vs 非答案格）清單化、可機檢；A3 由「固定 10 幀」改成「每答案段首中尾＋報覆蓋率」＝**可審計嘅覆蓋率聲明**，唔係只加 2 幀嘅數字遊戲（佢要求明寫段／幀比）。R2 指「A2↔§4 打架必然紅」之死因**閉合**。→ **RESOLVED**。

### FC4（LD-H）加 D7 三渠道分發包 → **RESOLVED**
- v4 §3 加 **D7**；§8 三渠道：8.1 YouTube／8.2 Bilibili／8.3 項目頁——每段真齊 標題／描述／縮圖文案／CTA／發佈 checklist（5/5/5）。
- **項目頁 URL 出處（我親讀）**：`docs/PUBLISH.md:26` → `pack-ai-assistant-paia`（id `1643097`）`https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia` ✅ 逐字相符。
- **Modrinth slug**：我 `grep -rniE "modrinth\.com/(mod|project|modpack)/..."` 全 repo docs → 只有一條無關 hit（2026-09-15 plan 提 `modrinth.com/mod/emi`）⇒ **repo 內確無本 mod 嘅 Modrinth 項目連結**。v4 老實標〔待 SK 確認〕、**唔自創** ✅。
- A10（§6）：D7 有 3 channel 段、每段齊五元素、CTA URL 可 HTTP 200（可機檢）✅。
- **裁判**：O4 攻擊**不成立**。FC4 只要求「channel＋項目頁 URL（`pack-ai-assistant-paia` id 1643097）＋標題／描述／縮圖文案＋CTA＋發佈 checklist」——v4 全部提供；Modrinth slug 屬**額外**未確認項（FC4 原文冇要求 Modrinth slug），誠實標示＝加分而非扣分。→ **RESOLVED**。

### FC5（文件級）修四項 → **RESOLVED**（全部我親測）
| 項 | v4 寫 | 我嘅親測 | 判 |
|---|---|---|---|
| `New World (1)` 大小 | 19,273,115 bytes＝18.38 MiB（211 檔） | Python `os.walk`+`getsize` 求和＝**19,273,115 bytes＝18.38 MiB；files=211** → **逐 byte 一致** | ✅ |
| `New World` | 3 bytes（空） | 3 bytes／1 檔 ✅ | ✅ |
| `du -sk` 佐證 | 19170 KB（≈18.7 MiB，R2 講法由此來） | `du -sk`＝**19170 KB** ✅；`du -sb`＝19273115 ✅ | ✅ |
| `ds_peak_hours.py` 路徑 | `%LOCALAPPDATA%\hermes\scripts\ds_peak_hours.py` | 檔案真存在該路徑；repo **無** `scripts/` 目錄 ✅ | ✅ |
| mod 數 predicate | 380 jar（`ls *.jar\|wc -l`）／381 modlist（`grep -c '<li>'`） | `ls .../mods/*.jar\|wc -l`＝**380**；`grep -c '<li>' .../modlist.html`＝**381**（modlist.html 喺 **instance 根目錄**）✅ | ✅ |
| §1 措辭 | 「每格都係真機真畫面（答案類格為真機真答案）」 | 同 A2 二分一致 ✅ | ✅ |
- **裁判**：O5 攻擊**不成立**。四項全部改到且同真 artifact 一致；`New World (1)` 唔止「差 15×」修返，連 R2 §2 表嘅「≈18.7 MB」都追出係 `du -sk` 19170÷1024 之來源——**閉合**。→ **RESOLVED**。

### 新自相矛盾掃描（FC 之間 / FC 與全文）
- 五條 FC 落點互相一致（FC1→§4b/§6 A9；FC2→§4.4/4.5；FC3→§6 A2/A3；FC4→§3 D7/§8/A10；FC5→§1/§2/§11）。**無互相排斥**。
- **兩條 cosmetic 瑕疵**（見 §0）：§10(4)「批咗才寫 §4b」措辭（§4b 已寫）；§4.5 810×1440 掛「master」字眼鬆散。**兩者都唔改任何決定、唔屬 FC1–FC5、亦非邏輯互斥** → 判**唔構成**「新自相矛盾」。
- 其他：分鏡秒數 `5+10+10+7+8+6+4`＝**50**（我 `python` 核）✅；沙盒 mods 只有 `packai-autotest-dev.jar` 一支 packai jar ✅；1.19.2 sandbox＝6 個（`packai_sandbox/_atm8/_e9e/_ftb/_startech/_universio`）✅——全部同 v4 §2/§11 一致。

---

## 4. 數字／行號重算表（**我親跑**，非 v4 自報）

| v4 聲稱 | 我嘅命令 | 我實測 | 一致？ |
|---|---|---|---|
| `New World (1)`＝19,273,115 bytes／18.38 MiB／211 檔 | Python `os.walk` `getsize` 求和 | 19,273,115／18.38 MiB／211 | ✅ |
| `du -sk`＝19170 KB | `du -sk`／`du -sb` | 19170／19273115 | ✅ |
| jar＝380；modlist `<li>`＝381 | `ls *.jar\|wc -l`；`grep -c '<li>'` | 380；381（modlist.html 喺 root） | ✅ |
| 主＝2560×1440；副＝1080×1920 | ctypes `EnumDisplayMonitors`＋`GetSystemMetrics` | (0,0,2560,1440)；( -1080,-241,0,1679) | ✅ |
| `ds_peak_hours.py` 喺 hermes/scripts，repo 冇 scripts/ | `ls` | 存在／repo 冇 | ✅ |
| `move_window_to_monitor.py` 喺 hermes/skills… | `find` | 存在／repo 0 命中 | ✅ |
| `AiClientCommands` `/ai` greedyString→askAsync | read :22/:27 | ✅（21-34 block） | ✅ |
| `AskService.askAsync(String,Consumer)` :103 | read :103 | ✅ | ✅ |
| `AiAssistantScreen` draftInput :78、預填 :161-162、askAsync :428 | read | ✅ | ✅ |
| `build.gradle` flag :113-115 | read | ✅ | ✅ |
| `AutoTestHarness` flag :96、cases :140、parse :197-209、openAndAskAbout :419、Judge :658-662、loadLevel :265 | read | 全部命中 | ✅ |
| PUBLISH.md:26 CurseForge `pack-ai-assistant-paia` id 1643097 | read :26 | ✅ | ✅ |
| 三渠道 CTA：YouTube/Bilibili/項目頁 | §8 逐段 | 各段真齊五元素 | ✅ |

---

## 5. 第 ③ 階段 — 比分

**正方 8 : 反方 2**（`v4_adequate=true`）。

- 記分理由：R2 嘅 **5 條載重決定（LD-C／D／F／G／H）全部閉合**，且落點事實**經獨立重跑核實**（唔止 v4 自報）。反方本輪**冇**搵到「講得好聽但未閉合」嘅位（O1–O5 五條攻擊逐條被反轉條件反殺）。
- 扣 2 分（唔放水到 9:1）：① §10(4)「批咗才寫 §4b」v3 遺留措辭；② §4.5 810×1440 掛「master」字眼鬆散。兩者係文件級 cosmetic，**唔改決定、唔阻達標**，但如實記低。
- **診斷（比分形狀）**：R1 死喺 spec 細節（3:7）→ R2 死喺載重結構（4:6）→ **R3 載重結構全數補齊且可機檢（8:2）**。呢個係 skill 期望嘅「逐輪換層、最終收口」軌跡，**唔係**換 prompt 擲骰。
- 對照 SK 規則：**達 ≥8:2**，**唔使**停手交 SK。

### 建議（**唔屬 flip condition、唔扣分**，只係一行 handoff note）
1. §10(4)「批咗才**寫** §4b」→ 改「批咗才**實作** §4b」（1 字，清掉唯一字面矛盾）。
2. §4.5 810×1440 補一句「（由 2560×1440 原生錄影裁；1920×1080 master 裁則為 608×1080）」。
3. 開工前照 §10(5) 跑嗰個 **2 分鐘 `/ai <問題>` 真跑**，順手落 A9 頭一條真 trace（把 M1「立足點」由推論變實測）。

---

## 6. 本輪用過嘅來源（可重跑）

- 受審：`docs/promo/PROMO_PLAN-v4-2026-10-08.md`（sha256 `9006f7d1…`／26475 bytes）；對照 `PROMO_PLAN-v3-2026-10-07.md`；上輪 `docs/plans/reviews/2026-10-08_promo-plan-v3-round2.md`。
- 程式碼（forge/1.19.2）：`AiClientCommands.java:22-34`、`AskService.java:103`、`AiAssistantScreen.java:78/161-162/428`、`AutoTestHarness.java:96/140/197-209/265/419/658-662`、`build.gradle:113-115`。
- 環境：`PrismLauncher…/instances/packai_sandbox_atm8/{saves,mods,modlist.html}`（`os.walk` 求和＋`du`）；`%LOCALAPPDATA%\hermes\scripts\ds_peak_hours.py`；`%LOCALAPPDATA%\hermes\skills\software-development\minecraft-mod-in-game-autotest\scripts\move_window_to_monitor.py`；ctypes `EnumDisplayMonitors`。
- 文件：`docs/PUBLISH.md:26`。

## 7. 輪次記錄

| 輪 | 日期 | 正方:反方 | 卡死嘅載重決定 |
|---|---|---|---|
| R1 | 2026-10-07 | **3 : 7** | 片長 genre／gdigrab／DJ2 世代／沙盒空世界／驅動機制／路徑／A3 假綠／A5 tautology／3 支片／成本／旁白受眾 |
| R2 | 2026-10-08 | **4 : 6** | LD-C 錄影幾何／LD-D M1 未 spec／LD-F A2↔§4 矛盾／LD-G 成本／LD-H 零分發 |
| **R3（有界）** | **2026-10-08** | **8 : 2** | **FC1–FC5 全部 RESOLVED；餘 2 cosmetic 文件級瑕疵，非載重** |

> **裁定：`v4_adequate=true`（≥8:2）。** 可進實作（實作前先做 §10(5) 2 分鐘真跑＋清 2 條 cosmetic 措辭）。
