# 2026-09-15 — Settings C 批（7 項新設定）＋ 顯示層 P0 修復

> SK 決定（2026-09-15）：**7 項全做**（原候選 4–10）。原「快捷鍵」項**唔做**——查過 MC 本身
> Options → Controls 已經可以改 keybind（`]` 開 AI、`Y` 查 JEI），再加一個設定係重複機制。
> 本檔要 SK 過目後才實作（plan-first 規則）；code 一律經 cursor-agent。
> 部署規則見 skill `minecraft-mod-jar-deploy`（唔准 hot-copy）。

---

## 0. Baseline（改動前，實測）

| 項 | 值 |
|---|---|
| Settings 控件 | 43 entry / 7 分類（CONNECTION 9、RECIPE 8、ANSWER 7、ADVANCED 6、DEBUG 6、INTERFACE 4、QUEST 3） |
| 控件型別 | `SettingsRegistry.ControlType` 只有 4 種：TOGGLE／NUMBER／TEXT／LIST |
| 存檔語意 | 每個 setter 尾 `SPEC.save()`（B 批已落）；harness `check_settings_setters.py` 守住 |
| 現有相關 key | `askTraceKeepFiles`（按**檔案數**保留）、`showTokenUsage`、`showHiddenQuests`、`ModConfig.Type.CLIENT` |
| 現成機制（重用，唔重造） | `TokenUsage`（每次 ask 有 prompt／completion 數，`LlmClient` 已累加 `cumulativeUsage`）；`AskTrace` 已有 trace 目錄解析；MC 原生 keybind |
| **收貨 baseline（2026-09-15 17:5x 實測）** | `compileJava compileTestJava` → **BUILD SUCCESSFUL**；`tests/check_*.py` → **110/110 綠**（冇 pre-existing 紅）→ 之後改動**任何紅都算 regress** |

---

## 1. P0 顯示層 bug：tooltip 同「要打字嘅欄位」被蓋喺後面（SK 2026-09-15 回報）

> **C-0 範圍（SK 2026-09-15 定 **1a**）＝ ① 繪製次序（睇得見）＋ ② mouse 路由（點得到）兩樣一齊做**，唔准只做一半。

**症狀（SK 原話）**：Settings 頁「tooltips 同要輸入嘅 field 都喺 background」。

**根因（z-order／繪製次序），code 證據**：
`SettingsScreenV2.renderScreen()`（:840）次序係
1. `renderBackground` → `GuiShell.nestedShell` → **`super.render()`（:846）畫所有 vanilla 控件，包括 `search`／`valueBox` 兩個 `EditBox`**
2. **`:847` `WidgetCompat.renderHoveredTips(...)` 畫 tooltip**
3. `:868–885` 之後才畫**自繪層**：title、分類欄 panel、entry list panel、逐行 row（`:932/:934` 用 `graphics.fill` 填滿整條 row 帶）、`renderDesc` 描述 panel

→ 自繪層畫喺控件同 tooltip **之上**：`valueBox`（要打字嘅欄位）被 row 高亮／row 文字／描述 panel 蓋住；
tooltip 喺 `:847` 畫完，再被之後嘅 panel 覆蓋（低高度時 `descAsOverlay` 嘅描述 panel 直接佔用 entry list 帶 —
`SettingsLayout.compute():127`）。即係「睇落喺背景」，但**接到 mouse／鍵盤**（控件仍然在最上層收 input），
所以「打得字但見唔到格」。

**修法（唯一正確方向：跟 vanilla 慣例，tooltip 最後畫）**：把 `renderScreen` 次序改成
`renderBackground → nestedShell → 自繪層（title／分類欄／entry list／desc） → super.render() → renderHoveredTips（最後）`；
`fallback`（畫面太細）分支要保留，並確保 tooltip 仍然最後。
**唔可以**用「加 z-level／改 alpha」作替代（唔解決遮蓋）。

**遮蓋程度（R1 反方修正，避免 overstate）**：
- row 高亮只有 **40% alpha**（`:932` `0x664488FF`／hover `0x33FFFFFF`）→ 只係「洗淡」輸入框而唔係完全遮住；
  row label 畫左半（`:941` `r.x+4`），valueBox 由 `r.x + r.w/2` 起（`:303`）→ **兩者唔重疊**。
- 真正近乎不透明嘅係描述 panel（`GuiShell.FILL_BODY = 0xD00C1018`，82%）——但佢只喺 **overlay 模式**
  （`height < 260`，`SettingsLayout:110/:127`）落 entry list 帶；≥260 係 docked（`:129`，喺清單下方）。
- 即係「輸入框被蓋」最嚴重喺 **≤256 高度**；≥260 主要係 **tooltip 被後畫嘅 panel／title 覆蓋**。
- 但次序本身仍然係錯（vanilla 慣例：tooltip 一定最後畫；`WidgetCompat.java:20-22` 自己 doc 都咁寫）→ 修法照 §1 上面做。

**驗收（R2 反方修正：原方案兩層都係假綠，已重寫）**：
1. ~~headless 幾何斷言~~ **撤回**：修法唔改任何幾何，用幾何斷言證明唔到 z-order（R2 實算：`searchBox` 底 72 vs `entryList` 頂 76 → 交集恆空；`descPanel` 底 == 底排按鈕頂 → 亦恆空；overlay 下 `row ∩ descPanel` 必然非空 → 照字面寫反而即刻紅）。
   → 改為 **render-order 閘**（`tests/check_*.py` 新增，現時 tests/ 對呢件事 **0 命中**）：斷言 `renderScreen` 內 `indexOf("super.render") < indexOf("renderEntryList")/("renderDesc")`，且 `renderHoveredTips` 係最後一個 draw call；**要交紅→綠證明**（改回舊次序即紅）。
2. **fallback（畫面太細）分支要 pin 死**（R2 指出：照字面搬 `super.render` 會令 Done／Reset／搜尋框全部唔再被畫）：
   `renderBackground → nestedShell → 〔fallback: super.render → too_small 文字 → tips 最後 → return〕`；
   非 fallback：`… → title → 分類欄／entry list／desc → super.render → renderHoveredTips 最後`。
   理由：fallback 嘅 layout 係全屏 `Rect(0,0,W,H)`（`SettingsLayout:106-107`）→ search EditBox 會變全屏不透明底，訊息必須畫喺佢之後。
3. 真機：**可打字欄位由 registry 機械列舉**（實測：8 條 `TEXT` ＋ 3 條會開輸入框嘅 `LIST`；`NUMBER` 只 cycle 冇框 → 唔准手數／唔准寫「所有 entry」）；
   要**強制 desc panel 出現**（mouse 停留 entryList 範圍內，`renderDesc:1042-1046`）先測得到 82% 遮蓋；**加測 valueBox 自己嘅 TipEditBox tooltip**；
   取樣：240／256（overlay）＋270（docked）× 三 GUI scale。
4. ~~要 SK 決定：C-0 要唔要同一批補 mouse 路由？~~ → **SK 2026-09-15 覆「1a」：一齊修**，詳見下面第 4 點（已定案）。
4. **Mouse 路由（SK 揀 1a：同 C-0 一齊修）**——唔修就「見到框但點唔到」。
   - 現況（我讀碼確認）：`mouseClicked`（`SettingsScreenV2.java:1077-1104`）先處理分類列同 entryList 帶；落喺 entryList 帶內嘅 click **一律 `return true`（吞掉）**（`:1088-1102`），所以 **valueBox（`:305` 建立、`:338` addRenderableWidget，位置就喺 entry row 上面）永遠收唔到 mouse** → 點入去放 caret／拖選字都做唔到。**鍵盤冇事**（`:339` `setInitialFocus(valueBox)`），`commitValueBox`（`:352-360`）亦會先行 → **冇資料損失**（R2 已核）。
   - **修法定案**：喺 `mouseClicked` **custom 分支之前**加「命中互動控件就交返 vanilla」：
     遍歷 `this.children()`（或 `renderables`）→ 若 `child instanceof AbstractWidget w && w.visible && w.active && w.isMouseOver(mx,my)` → **`return super.mouseClicked(...)`**；
     唔命中任何控件 → 照舊行 custom 分類／row 邏輯（唔准改 row 點擊行為）。
   - `mouseDragged`／`mouseReleased` 已經係 `else { super.… }`（`:1140`／`:1154`）→ vanilla 拖選會經 `getFocused()` 正常運作；**要真機驗**拖選文字。
   - **唔准**改搜尋框（`:110-124`）行為、唔准改 JEI 拖曳排序（`:1107-1152`）。

5. 一致性（低嚴重、**唔擴 scope**）：其餘 3 個 screen 仍係舊次序（`ModelPickerScreen:126-130`／`WebSearchScreen:106-111`／`InvPickScreen:281-286`），佢哋 widget 唔在自繪區 → 唔算同一症狀；只加一行註釋講明次序契約，脆弱位記入 pass-2。

---

## 2. C 批七項（每項：key／型別／預設／分類／行為／護欄）

| # | key | 型別 | 預設 | 分類 | 行為 | 護欄（SK 規則：開關＋硬上限＋key 去重） |
|---|---|---|---|---|---|---|
| 4 | `llm.traceKeepDays` | NUMBER | **3**（SK 2026-09-15 定：3 日；玩家可自行改） | DEBUG | 只刪 trace 目錄內 `ask-*.jsonl` 中 mtime 老過 N 日嘅檔；刪幾多寫一行 log | `0`＝唔清；clamp 0–365；**只限** `<instance>/packai/trace/`，只 match `ask-*.jsonl`；與現有 `askTraceKeepFiles` 並存（先按日數，再按檔案數）；tooltip 要**老實講明「會自動刪除舊 trace」** |
| 5 | `llm.askMaxToolRounds` | NUMBER | **3**（實測 `AskToolLoop.java:31 MAX_LLM_ROUNDS = 3`，兩樹同值） | ANSWER | AskToolLoop 嘅追問／工具輪數上限改由 config 讀（`:428`／`:446` 判斷點）；到頂走現有 fallback 文案 | clamp 1–8（唔准 0＝無上限，防無限 loop 燒 token）；**預設 3＝零行為改變**；>3 時 UI tooltip 老實講「會多用 token」 |
| 6 | `llm.dailyTokenLimit` | NUMBER | 0＝無限制 | CONNECTION | 每次 ask 完累加 `TokenUsage`（已存在），寫 `<config dir>/packai-usage.json`；當日累計超過上限 → 唔再 call LLM，出提示＋log | 檔案硬上限 64KB；**每日一條 key**（`YYYY-MM-DD` 去重，覆寫同日）；跨日自動重置；只寫自己嘅檔（唔碰 config.toml） |
| 7 | `llm.answerDetail` | LIST（concise／standard／detailed） | standard | ANSWER | 只換 prompt 嘅 **style 段**（沿用 `ReplyLang` 現有 llm_style 機制，3 檔 lang 各加對應字串） | **唔准**改 FACT 規則、官方名規則、禁意譯規則；三個值都要有 lang（3 檔齊） |
| 8 | `ui.askBlacklist` | LIST（separator `;`） | 空 | ANSWER | **三層語意**：① 完全 item id（`mod:item`）② namespace 前綴（`mod:` — 必須帶冒號）③ 關鍵字（必須寫成 `*字*` 才當模糊比對）→ 命中就唔查 JEI／唔送 facts，答 canned 提示 | 空清單＝零影響；純函數 headless 測；**fixture 必須斷言 `minecraft` 唔命中等於 `minecraft:stone_sword`**（防誤殺整個 namespace）；答案唔准回顯黑名單內容；上限 64 條（超出截斷＋log） |
| 9a | `llm.answerLang` | LIST（auto／zh_tw／zh_cn／en） | auto | ANSWER | **只改答案語言**（`ReplyLang.current()` 第一個來源改為 config，`ReplyLang.java:25-31`）＝原 S-1 | auto＝跟遊戲語言（現行為）；3 檔 lang 已存在 |
| 9b | `ui.uiLang` | LIST（auto／zh_tw／zh_cn／en） | auto | INTERFACE | **Settings 頁＋JEI 類別頁 UI 文案**語言，唔跟遊戲語言 | 機制要新建（**實測：現時冇 per-screen override**）——`ReplyLang` bundle 白名單只收 `packai.reply.`／`packai.label.`（`ReplyLang.java:180-181`），settings key 唔在內 → 要擴白名單包 `packai.settings.`／`packai.recipe_cats.`，再加 helper 取代 `SettingsScreenV2` 內 **20 個** `Component.translatable` site（實測：`client/gui/**` 全部 81 個，但本批只做 settings 頁）＋搜尋過濾要跟住（`matchesSearch` 用 translatable 字串 :256-263）。範圍外：其他 GUI 唔做 |
| 10 | 診斷包匯出（DEBUG 分類一個**按鈕**，非 config） | 新 `ControlType.ACTION` | — | DEBUG | 打包最近 trace（預設 10 個）＋`logs/latest.log` 尾 2000 行＋redacted `packai-client.toml` → `<instance>/packai/diag/diag-<ts>.zip`，完成後 log 路徑 | zip 硬上限 **32MB**（超出只取最新檔）；**一律 redact** `apiKey`／`token`／`serper`／`tavily` 值；只寫 `<instance>/packai/diag/`；寫入前同名 key 去重（同日重跑覆蓋同名）；**新增閘**（見 §3）：`REQUIRED_CONTROL_TYPES`（`tests/check_settings_registry.py:23`）現時硬編 4 種，ACTION 完全在 assertion 之外 → 要加 ACTION 專屬斷言（數量、必須被 handler 引用）並做紅→綠證明 |

**Registry／lang 工作量（R1 反方修正）**：本批 config key ＝ **6 條**（4／5／6／7／8／9a）→ `6 × 3 語言 × (label＋tooltip) ＝ 36 條`，另 #10 按鈕 label＋tooltip ×3 ＝ **3 條**，共 **約 39 條**（初稿寫「18 條」係錯，已改）。
`tests/check_settings_registry.py` 對 **config key** 有斷言（registry 每條要 3 檔 lang＋setter 有 `SPEC.save()`）；
**但 9b 同 #10 嘅 `ACTION` 型別唔在斷言範圍**（`REQUIRED_CONTROL_TYPES` 硬編 4 種）→ 見 §3 要補閘。

---

## 3. 切批同派工（全部 cursor-agent，Hermes 親驗）

| 批 | 內容 | 為何獨立 |
|---|---|---|
| **C-0** | §1 P0 顯示層次序修復（唔加任何新設定） | 修好之前，新頁**根本用唔到**（睇唔到輸入框）；要先單獨驗 |
| **C-1** | 4＋5＋6（省硬碟／省 token／防爆費） | 純數值＋護欄，harness 可完全 headless 驗；三條預設值全部＝現行為 |
| **C-2** | 7＋8＋9a（答案風格／黑名單／答案語言） | 影響答案，要真機睇；9a 只改一個來源，可獨立回滾 |
| **C-3** | 10＋**新閘**（`ControlType.ACTION` 專屬 assertion：數量、必須被 handler 引用；先做紅→綠證明） | 唯一會寫檔＋新控件型別；無閘唔准開工 |
| **C-4** | 9b（Settings／JEI 頁 UI 語言） | 要擴 `ReplyLang` 白名單＋改 20 個 call site＋搜尋過濾；做完 C-3 有閘機制之後才做較安全 |

**雙樹範圍（R1 反方修正）**：NeoForge 樹 **已 PAUSED**（`neoforge/README_PAUSED.md` 明文唔 mirror forge 改動），且 neoforge **冇 settings package** →
C-0／C-2／C-3／C-4 一律 **forge-only**；C-1 嘅 `AskToolLoop`／`PackAiConfig` 兩樹都有檔，但依 pause 政策**只改 forge**，
`check_dual_tree_sync.py` 係 pause-aware（forge-only 改動 = WARN 唔會紅）。每批都要寫明「只改 forge」。

每批：派工 → **forge compile 0 error**（NeoForge paused，見上）→ 相關 harness 綠 → `tests/check_*.py` **相對 baseline 冇新增紅** →
兩次 code review（pass1 重構／pass2 三個月後脆弱位）→ 真機驗 → 才落下一批。

---

## 3b. Review trail（SK 規則：plan 要過目＋反方 review）

| 輪 | 日期 | 形式 | 比分 | 結果 |
|---|---|---|---|---|
| R1 | 2026-09-15 | 反方（read-only subagent，skill `adversarial-decision-review`） | **反方 7:3** | 6 條載重 objection，5 條已用**實測**吸收（見下），1 條（#9 機制）已量度範圍 |

**R1 已吸收（附實測證據）**：
1. #5 預設值錯 → 實測 `MAX_LLM_ROUNDS = 3`（`AskToolLoop.java:31`，兩樹同值）→ 預設改 **3**（零行為改變）。
2. #9 機制唔存在 → 實測 `ReplyLang` 白名單只收 `reply.`／`label.`；`Component.translatable` site 實測 **20 個**（settings 頁）／81 個（全 gui）→ 拆成 **9a（答案語言，可即刻做）／9b（UI 語言，C-4）**。
3. #10 冇閘 → 實測 `REQUIRED_CONTROL_TYPES` 硬編 4 種，ACTION 完全在 assertion 外 → C-3 要先加閘＋紅→綠證明。
4. #8 語意太闊（會誤殺）→ 改三層語意＋fixture 斷言 `minecraft` 唔命中 `minecraft:stone_sword`。
5. 工作量 18 → 實算 **約 39 條**；雙樹範圍寫明 **forge-only**。
6. §1 遮蓋程度 overstate → 修正為「≤256 最嚴重；≥260 主要係 tooltip」，並改成「先 headless 幾何斷言、真機兩個高度」。

**未解（R1 遺留）**：`llm.traceKeepDays` 預設 → **已解**：SK 2026-09-15 定 **3 日**，且玩家可自行改（0＝唔清）。

**R2（2026-09-15，v3 結構性修改）**：R1 只得 7:3，未達 SK 要求嘅 8:2／9:1 → **未可開工**。依 SK 指示做兩件事：
1. **把交付物拆開評**（R1 最大成因係「一個 plan 綁死 P0 修復同 7 項新功能」）：
   - **交付物 A（C-0）**：只係 `SettingsScreenV2.renderScreen()` 次序修正，唔加設定、唔改行為、唔寫檔 → 目標 **9:1** 才動工。
   - **交付物 B（C-1～C-4）**：七項設定 → 目標 **8:2** 才動工。
2. **補上 R1 指出嘅三個未 pin 項**：兩個係「閘／機制」問題（#10 ACTION 閘、9b 白名單），一個係「reversal 安全」（已由 R1 自己用 javap 證）。
   本輪再審會逐條要證據，唔接受「計劃會做」。

| 輪 | 交付物 | 比分 | 結果 |
|---|---|---|---|
| R1 | A+B（合併評） | 反方 7 : 正方 3 | 6 條 objection，已全部實測吸收（見下） |
| R2 | A（顯示層修復） | 反方 3 : 正方 7 | 方向存活；3 個未 pin 位（fallback 次序／驗收假綠／mouse 路由）→ v4 已補（見 §1），補完近 9:1 |
| R2 | B（七項設定） | 反方 7 : 正方 3 | 5 條「開工即撞牆」pin（見 §2b），全部實測核實 → v4 已吸收，**待 R3 重評** |

## 2b. R2 反方 pin（交付物 B；每條都係「照字面做會撞牆／靜默失效」級，全部已用實測核過）

| # | Pin | 證據（我親自覆核） |
|---|---|---|
| 5 | 唔止兩點：**三個地方**都要讀 config —— `AskToolLoop.java:428`、`:446`、**`AskLoopState.java:191`（`canLlm()` 直接讀 `AskToolLoop.MAX_LLM_ROUNDS`）**。漏最後一個 → 設 8 都只行 3 輪，驗收必敗。常數保留做 default；**同一個 commit 要更新** `tests/check_ask_tool_loop.py:34`（硬斷言字串 `MAX_LLM_ROUNDS = 3`）→ 改成 config 驅動期望，**唔准為綠而刪 assert** | `grep MAX_LLM_ROUNDS` 三處 + check 檔第 34 行 |
| 4 | 觸發點要 pin：**client startup ＋ 每次 ask 完成**兩處。現時唯一清理入口 `rotate()` 只有一個 caller ＝ `AskTrace.finish():435`；`askTraceJsonl=false` 時 `open()` 回 null → `finish()` 早退 → 日清永遠唔跑。**`index.jsonl` 明文 never deleted**（`PackAiConfig.java:47/:283`）→「省硬碟」只部分成立，要老實寫；兩機制係 `min(檔數, 日數)` 唔會互鬥 | `AskTrace.java:435/464`、`PackAiConfig.java:47/283` |
| 6 | 記帳欄位 pin 死：**`prompt + completion` 相加**（**唔准**用 `total`——缺欄位時係 `-1` 哨兵，`plus()` 當 identity → 靜默 no-op）。缺 `usage`／HTTP ≥400／timeout **都要記**（否則伺服器已燒嘅 token 唔入賬＝上限永遠超得過）→ 用 prompt 字元估算（`chars/3` 向上取整）＋ log `usage_missing estimated=`。**最貴未知已用 log 解開**：今晚 15 個 round **全部**有 usage（`total` 全 >0，冇 0／負數）→ deepseek 路徑可行；**ollama 本地路徑未驗** | `LlmClient.java:229-233/553-557`；log grep 15 vs 15 |
| 7 | `llm_style` 有**兩個變體**（`packai.reply.llm_style`／`llm_style_notools`，按 `toolsOffered` 分流 `LlmClient:378-379`）→ 3 值 × 2 變體 × 3 語 ＝ **18 條**，唔係 3 條 | `en_us.json` 兩個 key 實測存在 |
| 9a | **Hook 位唔係 `ReplyLang.current()`**：答案語言係 `AskService.clientLanguageCode(mc)`（`:2606` 定義、`:143`／`:2197` 使用）再顯式傳入 `AskEngine.ask(..., replyLang, …)`；改 `current()` 只會改 facts／labels（~30 站）→ 出「事實一種語言、答案另一種」。改 `clientLanguageCode` 之後 `tests/check_reply_lang.py:20/:24` 仍然綠 | 實測 grep 三個檔 |
| 9b | 站點用「全部呼叫」計：`SettingsScreenV2` **35 個**、`client/gui/**` **109 個**（先前寫 20／81 係只數字面 `packai.*` key／部分檔）。擴白名單後同頁會有兩條解析路徑（`Component.translatable` vs helper）→ 要明寫中文 fallback 一致，C-4 工作量上調 | 我親手 count 35／109 |
| 10 | `ACTION` **唔可能係正常 Entry**：`SettingsRegistry.java:519-521` fail-closed（setter 名必須 `startsWith("set")`，否則 `IllegalStateException("fail-closed: missing set* for …")` ＋ `:523` duplicate path 檢查）→ 要定義為**非 Entry 嘅 action descriptor**；python 閘要**放寬 `check_settings_registry.py:173 CONTROL_BRANCH_RE`**（硬編 `TOGGLE|NUMBER|TEXT|LIST`），**保留** `:519-521` 不變式；UI 跟現成 async pattern（`SettingsScreenV2:750-770`／`786-800`：`CompletableFuture` ＋ busy flag ＋ `minecraft.execute`） | 實測 grep |



## 4. 風險／回滾

- 全部係 **client config**，新 key 預設值＝現行為（例外：`llm.traceKeepDays` **SK 定 3 日**，會刪舊 trace → tooltip 要明講）。
- #6 會寫 `packai-usage.json`、#10 會寫 zip → 兩者都受硬上限＋只寫自己目錄；出事刪檔即可，唔影響存檔／世界。
- #5／#7／#8／9a 只影響 prompt／回答路徑 → 改返 config 即復原。
- 無 DB、無遷移、無刪除玩家資料；jar 回滾 = `%TEMP%\deploy_backup_*` 舊 jar copy 返（見 skill `minecraft-mod-jar-deploy`）。

## 5. 真機驗收（Hermes 逐項核 trace／log，唔靠肉眼印象）

1. C-0：§1 三項（三解析度 × 三高度）。
2. 4：改 7 → 造幾個舊 mtime 假檔 → 開遊戲 → 只有過期檔被刪、log 有一行。
3. 5：設 1 → 問一條要查 JEI 嘅問題 → 只 1 輪就 fallback；設 8 → 正常多輪。
4. 6：設一個細數（例 1000）→ 問到爆 → 第 2 條問題被拒＋提示；`packai-usage.json` 只有當日一條 key。
5. 7：同一問題在 three 值下答法明顯唔同，但**官方名＋禁意譯**規則三值都遵守。
6. 8：加一個你問開嘅物品 → 即刻變成 canned 提示；移走 → 回復正常。
7. 9：切 zh_tw／zh_cn／en → UI＋答案語言跟住轉，重開遊戲仍在。
8. 10：按一次 → 有 zip；解壓檢查：冇任何 key 明文、大小 ≤32MB、trace 齊。
9. 最後：`python tests/check_*.py` 相對 baseline 冇新增紅；兩次 code review 記錄入 `code_change_log.md`。

---

## 9. R3 反方吸收（v5；本輪比分 A 反方3:正方7、B 反方6:正方4 → 未達標，以下逐條改成可執行規格）

### 9A. 交付物 A（C-0）四條

- **A-1 繪製次序閘唔准用 `indexOf`**（實測：`indexOf("renderEntryList")`＝**922**＝方法定義行，今日已經「過」＝假綠；`renderHoveredTips` 修完第一次命中會係 fallback 分支）。
  改法：**先切出 `renderScreen` 方法體**（由 `renderScreen(` 到下一個 `private/public` 方法），喺方法體內用 **regex 抽 call site 次序**，
  用 **lastIndexOf** 斷言「`super.render` 之後唔應該再有自繪層」＋`renderHoveredTips` **出現次數 == 2**（fallback／非 fallback 各一，且各自係該分支最後一個 draw call）；
  明列「計數嘅 draw call 集合」（含 `statusOk` `:888` 同空清單訊息，唔准漏）；**必須逐分支做紅→綠**（fallback／非 fallback 各 revert 一次證明閘會紅）。
- **A-2 驗收第 3 步改寫（1.19.2 EditBox 冇 drag-select）**：改測「點兩處 → caret 有移動」＋「Ctrl+A 之後打字會取代全選」。
  **唔准**測 drag-select（唔存在，會出 false red 或引實作者擴 scope）。
- **A-3 新增 scroll pin（真資料損失路徑）**：`mouseScrolled :1158-1171` 只要指針喺 entryList±4（valueBox 正正在嗰度）就吞 scroll；
  而 `editingPath != null` 時會 `rebuildUi()`（clearWidgets＋init，valueBox 用 **config 值**重建）→ **未 commit 嘅字會蒸發**。
  ⚠️ **唔准手數受影響欄位**（R4 捉到我寫「4 條」係 undercount）：實測會開 EditBox 嘅有 **9 條** ＝ 6 條 `TEXT` 走 `startEdit`
  （`llm.apiKey:122`／`llm.apiBaseUrl:131`／`llm.ollamaBaseUrl:152`／`web.tavilyApiKey`／`web.serperApiKey`／`ui.knowledgeUrl:432`）
  ＋ 3 條 `FREE_TEXT_LIST`（`SettingsScreenV2:45-48`）；**一律由 registry 機械列舉**。
  → C-0 一併：`rebuildUi()` 前**保 draft**（重建後 `setValue(draft)` ＋還原 caret；若技術上唔可行才改「先 `commitValueBox()`」），
  驗收加「打字 → scroll → 字仍在」。
- **A-4 行為聲明＋實作形狀**：修完之後「正在編輯嗰行右半」click 由（今日：`activateRow→startEdit→commitValueBox()+rebuildUi()`）
  變成（交返 box：放 caret、唔 commit）——**冇資料損失**（onClose／下次 startEdit 照 commit），但要明文寫低。
  實作**用 `if (super.mouseClicked(mx,my,button)) return true;`**（行 vanilla `ContainerEventHandler` 預設路徑：first-consuming-child → `setFocused` → button0 `setDragging(true)`），
  **唔准**自己砌 `hovered ∧ visible ∧ active ∧ isMouseOver`（語意唔等於真 dispatch）。

### 9B. 交付物 B（七項設定）五條 flip condition（下一輪只核呢 5 條）

- **FC1（#7 answerDetail，最重）**：`packai.reply.llm_style` 係**單一 value**（實測 en 8,336 字、zh_tw 4,235、zh_cn 4,235——**FACT marker／禁 bare id／禁 Markdown／禁回顯 section tag 規則全部喺同一個 value 內**），
  冇一個可以獨立換嘅「段」。→ 改成「**不變核心 rule block ＋ detail block**」設計，並**補機械 check**（斷言 3 個 variant 共用嘅核心 **byte-identical**）；
  寫明 `tests/check_reply_prompt_keys.py` 點改（`KEYS` 硬編 tuple、suffix 測試 `key.endswith("llm_style")` → 新 key 會跌入 else 分支要求 0 個 `%s`）：
  **唔准放寬成 0 個 `%s`**；每個 variant 必須**照帶 2 個 `%s`**（`ReplyLang.tr():137-141` 吞 `String.format` exception 會原樣回 template → 漏 placeholder 會靜默送 literal `%s` 落 prompt）。
  **core／detail 邊界要指明**（R4 implementation note）：**core ＝ value 前綴（不變）**，**detail ＝ 尾部 append 嘅區塊**；
  要一個機械可驗嘅分界（例：固定 sentinel 行 `<PACKAI_DETAIL>` 之後全部屬 detail），否則 byte-identical-core check 寫唔出。
- **FC2（#5）**：`canLlm()`（`AskLoopState:190-192`）係**共用閘**（實測 13 個 `canLlm()`；`AskToolLoop` 內 12 個 call site + `AskToolLoopCheck:326`）
  → 改咗等於一齊改 localTools／grounding／no-tools 預算，tooltip 要老實講「呢個係全部工具輪數上限」。
  **實作形狀**：**`AskLoopState` 欄位注入**（由 `AskService` 傳入），**唔准**喺 `logic/` 直接讀 `PackAiConfig`
  （harness 由 `research/gen_tmp_check.py` 生成純 `JavaExec -ea`、唔 load ModConfig → Forge `ConfigValue.get()` before-load 會 throw → 現綠 harness 直接轉紅；
  repo 先例＝`AskTrace.configEnabled()/configKeepFiles()` 用 `Class.forName`＋`catch(Throwable)` 回 default）。要宣示「預設 3 ＝零行為改變」。
- **FC3（#6 dailyTokenLimit）**：**單一 writer 機制**（lock／atomic append）——`ChatSession.busy` 只喺 `AiAssistantScreen:415/:447`，
  但 `/ai`（`AiClientCommands:27`）＋`askBlocking:2173/:2186` 都行得，`AskEngine.INSTANCE` 共用一個 `LlmClient`、per-ask accumulator 喺 `AskEngine:215` reset → 重疊 ask 會互相污染；
  記帳每欄 **clamp 0** 後用 **`max(total, p+c)`**（`TokenUsage.readNonNeg` 每欄獨立 -1 哨兵 → 只回 `total_tokens` 時 `p+c = -2` 會倒扣放行），並 log 原始 triple。
- **FC4（#4 traceKeepDays）**：日清 pass **明寫插喺 `asks.size() <= keep → return` 早退之前**（實測 `AskTrace.java:480`，block 480–482），
  驗收要用 **< `askTraceKeepFiles` 嘅檔數**（否則 vacuous——預設 50，要 ≥51 個假檔才會行到 delete loop）。
- **FC5（9b UI 語言）**：`SettingsScreenV2` 35 個 `Component.translatable` 中有 **vanilla key**（`gui.done` `:138`）→ 盲換 helper 會打爛 Done 按鈕；
  helper 要**排除非 `packai.` key**，並補「miss 時 fallback 行為」斷言（`tr()` miss 會回 raw key → 會顯示 `packai.settings.*` 字面）。
  文件級更正：forge 樹 `RecipeCategoryScreen` 已刪、inlined 入 `SettingsScreenV2:75`（「JEI 類別頁」唔再係獨立 screen）。

**R4（最後一輪，只核 §9A 四條 ＋ §9B FC1–FC5）結果**：**A ＝ 反方 1 : 正方 9 → 達 9:1 ✅ 可開工**；**B ＝ 反方 2 : 正方 8 → 達 8:2 ✅ 可依 §3 切批開工**。
殘留全部 non-blocking（已即時改正）：A-3 欄位枚舉 undercount（4 → 9，已改成 registry 機械列舉）；FC4 行號 480（非 481）；FC1 core／detail 邊界已指明。

---

## 10. C-0 落地紀錄（2026-09-15 19:4x，Hermes 親驗）

**實作**（cursor 派工）：
- `renderScreen()` 兩個分支重排：**正常分支**＝自繪（title／分類欄／清單／row／desc／`statusOk`）→ `super.render()` → `renderHoveredTips`（最後）；
  **fallback 分支**＝`super.render()` → **`too_small` 文字** → tips。
  ⚠️ **修正 plan §9A 字面**：fallback 必須「widgets 先、錯誤文字後」——因為 fallback layout 係**全屏 `Rect(0,0,W,H)`**，
  全屏 EditBox 會蓋住文字；次序反轉先睇得到提示（R3 反方已論證、實作跟咗）。
- `mouseClicked`：**最前面** `if (super.mouseClicked(...)) return true;`（行 vanilla dispatch：first-consuming-child → focus → drag）；
  編輯行右半嘅 click 語意改為交返 box（唔再 `activateRow→commit+rebuild`）＝A-4 已聲明；`onClose`／下次 `startEdit` 照 commit，**冇資料損失**。
- `mouseScrolled`：`rebuildUi()` 改為 `rebuildUiPreservingDraft()` → `captureEditDraftFromBox()`（存 `editDraft`＋`editCaret`），
  rebuild 後 valueBox 用 `editDraft` 重建＋還原 caret（`:339/:341`）→ **打字中 scroll 唔會再吞字**。
- 其餘 3 個 screen（`ModelPickerScreen`／`WebSearchSettingsScreen`／`InvPickScreen`）**只加註釋**講明次序契約（diff 確認無行為改動）。

**我親自跑嘅驗證（唔信實作 agent 自報）**：
| 項 | 結果 |
|---|---|
| `compileJava compileTestJava --rerun-tasks` | **BUILD SUCCESSFUL** |
| 新閘 `tests/check_settings_render_order.py` | **OK** |
| **負對照 A**：正常分支插入 `renderEntryList` 於 `super.render` 之後 | **紅**：`FAIL: non-fallback: draw after super.render: renderEntryList` |
| **負對照 B**：fallback 分支插入 `GuiShell.title` 於 `super.render` 之後 | **紅**：`FAIL: fallback: expected [mutedCentered, tips] after super, got [...]` |
| 還原後 | md5 完全一致（`25b2b41f6e75cabc8f5dd0b562953dd9`）、閘回綠 |
| 全量 `tests/check_*.py` | **112 檔全綠**（111 baseline + 新閘） |
| harness `runSettingsLayoutCheck` | **OK**（5 子斷言） |

**jar**：`1b207174c40789ed`（19:48）已用 `mc_mod_deploy_jar.py` 部署（舊版 backup `%TEMP%\deploy_backup_20260915_1948`）。
待 SK 真機驗收（§1 第 3 點 ＋「打字→scroll→字仍在」）。
