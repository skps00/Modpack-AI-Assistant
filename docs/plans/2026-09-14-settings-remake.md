# 2026-09-14 — Settings 頁重做計畫（**v2**，已吸收反方 review R1 7:3）

> SK 定案：**結構 c**（左側分類＋右側可捲動清單，參考 Create）／過時項**直接刪**／新設定由我提議。
> **v2 變更來源**：對抗式評審 R1（反方 7:3，未達 8:2 → **唔可以照 v1 開工**）。反方 10 個載重決定中 6 個判死，死因全部係 v1 自己嘅證據基礎。以下逐條已改。
> 參考實作（真源）：Create `mc1.19/0.5.1` `foundation/config/ui/{SubMenuConfigScreen,ConfigScreenList,entries/*}.java`（已用 GitHub API 核實存在）。**惟本 mod 係 client config（`ModConfig.Type.CLIENT`），唔需要 Create 嘅 server-authoritative 封包機制**，比 Create 簡單。

---

## 0. Baseline manifest（收貨基準；2026-09-14 實跑）

| 項 | 實測值 |
|---|---|
| `python tests/check_*.py` | **103 檔**，**3 FAIL**：`check_ask_tool_context.py`、`check_heavy_script_corpus.py`、`check_recipe_io_and_consume_use.py`（2026-09-14 11:5x 親自重跑核實；review 報「108 檔」有誤——`tests/*.py` 亦只 106 檔） |
| `check_ask_display_leak.py` | 環境性（取決於 `latest.log` 有冇內容）：review 當時 exit 2；**11:5x 重跑 RC=0 OK**（`lines=1 nonempty=1`） |
| Java harness（forge） | 6 個全 OK（AskTrace／AskReplyScrub／AskCardPlacement／AskToolLoop／AskModularPick／AskInvPick） |
| dual-tree gate | `check_dual_tree_sync.py` paused 模式（Neo 暫停）；新 forge-only 檔只 WARN，唔會紅 |

**收貨定義**：相對上表**冇新增紅**（唔係「全綠」）。

---

## 1. 問題陳述（已修正；反方實算證實逼爆係真）

- MC auto GUI scale 令**幾乎所有玩家**落喺 **240–270px 邏輯高**：854×480→427×240、720p→426×240、1366×768→455×256、1080p→480×270。
- 現時 4 tab 行槽：ASK **9**、Recipes 5、Connection 4、Quests **2**（嚴重失衡）。ASK tab 末行 `bottom=252 > 240`，同 `doneY=212` 重疊（`PackAiSettingsScreen.java:93`）。
- 要加 ~10 個新設定＋5 個 config-only ＝ 再加 ~15 行槽 → ASK 需要 384px，**連手動 scale 3（360px）都放唔落** → **tab 模型算術上加唔到**，平面清單（可搜尋＋可增長）係唯一出路。
- ⚠️ **KPI 改寫**：v1 講「解決 480p 逼爆」**唔準確**——新三欄喺 240–270px 可能一次只見到 **≈6 行**，而今日 ASK 完整可見 **7 行**。所以目標改為「**可搜尋＋可增長＋統一入口**」；480p 舒緩屬副產品，要 mock 量度後才寫死（見 §8 最大未知）。

---

## 2. 審計修正（v1 錯，必須先改）

| v1 講法 | 事實（本輪親查） | 處理 |
|---|---|---|
| 「7 個 config-only」 | **真係 5 個**：`recipeCardMirrorCategories`、`ingredientNbtSkipPatterns`、`ingredientNbtKeepPatterns`、`ollamaBaseUrl`、`ollamaModel`（`grep gui/` ＝ 0 refs）。**`recipeCategoryOrder`／`recipeCategoryHidden` 已有完整 UI**：`RecipeCategoryScreen.java`（搜尋 :51、逐行 toggle :104、拖曳 `moveRow`、reset :75）＋持久化 `PackAiConfig.setRecipeCategoryPrefs`（:495）＋`JeiCategoryCatalog.java:119`，入口 `PackAiSettingsScreen.java:367` | **SK 2026-09-14 定案 (ii)：新清單 UI 取代 `RecipeCategoryScreen`，舊 screen 刪走**；其功能（搜尋／逐行 toggle／拖曳排序／重設）併入新頁；連 **11 個 `packai.recipe_cats.*` lang ×3 檔**一齊處理（唔可以淨刪 code 留孤兒 lang） |

---

## 3. 刪除清單（逐條附 reader 證據）

| 項目 | 證據 | 動作 |
|---|---|---|
| `packai.settings.hint`（含 NeoForge 字眼） | lang-only，`grep` 冇 java/py 引用 | **刪**（3 檔） |
| `packai.settings.save_all` | lang-only，冇控件引用 | **刪**（3 檔） |
| `PackAiConfig.java:192–196` SPEC comment 過時句（同 `settings.hint` 同一句 stale NeoForge 備註）**會寫入玩家 toml** | 讀檔 | **改寫**（唔係刪 key，保留 key 免破壞舊 toml） |
| `modularToolSingleItem` | **係活嘅 code**：`AskService.java:2408 applyModularToolSingleItem` 讀佢；同已獲批嘅單選 plan（明文「後台安全網保留＋保留 harness」）**直接矛盾** | **v2 唔刪**。要刪就同單選 plan **一個 commit 同步**（key＋method＋harness＋3 lang），唔准一邊刪一邊留 |
| `packai.invpick.cap`／`modular_one_only` 等單選文案 | 屬單選 plan 工作範圍 | **本 plan 唔重複做**，只跟單選 plan 同步（避免同一批 lang 檔兩個 plan 同時改） |

---

## 4. 存檔語意（v1 未定義 → 反方判死，v2 寫死）

**現況事實**：所有 setter 直接 `.set()`（in-memory 即時）；reader（`LlmClient`、`AskService`、`JeiCategoryCatalog`）每次讀 live；`onClose()` **每次關窗都自動寫 apiKey／baseUrl**（`PackAiSettingsScreen.java:513–518`）；`ModelCatalog` 有 `cloudCache/ollamaCache/cloudFetchedAt/ollamaFetchedAt`＋`invalidate()`。

**v2 定案（即時套用模型，唔做 pending／儲存／捨棄 — Forge client config 之下「捨棄」無法還原已被引擎讀走嘅效果）**：
1. 所有 entry **即時生效**（每個 setter 尾必須 `SPEC.save()`；§6 FC4 斷言強制）。
2. **保留** `onClose()` 自動存 apiKey／baseUrl 行為（唔改既有 UX）。
3. **不變式（新）**：欄位顯示佔位符（`•••`／「已設定」）時**唔准寫回 config**（否則 `onClose` 會用佔位符覆蓋真 key）→ 加 `isPlaceholderValue()` 判斷＋harness 測。
4. **重設本頁／全部** → 走 `reset*Prefs()` 類 API；**唔准清 apiKey／tavily／serper**（要清就要確認對話框）；重設後呼叫 `ModelCatalog.invalidate()` 清 cache。
5. 版本：`PackAiConfig` 加註「所有 setter 必寫盤」comment，防日後再犯。

---

## 5. 版面與落地（反方指：唔好引入新 framework）

- **唔用** `ObjectSelectionList`／`AbstractSelectionList`（全 forge 樹 **0 個**用法）。
- **用 repo 已有自繪先例**：`ModelPickerScreen`（`ROW_H`、`scrollOffset`、`mouseScrolled` :190–196）＋`RecipeCategoryScreen`（搜尋＋toggle＋拖曳）。搜尋過濾放 responder（**唔准**在 render 逐行做）。
- 三欄：左＝分類（7 個：連線／回答／配方／任務／介面／進階／除錯）、右＝可捲動 entry 清單、底＝描述面板。
- **最小支援高度條款**：240／256／270px 三檔明寫行為；高度緊絀時描述面板改為 **hover overlay**（慳 1 行）。
- **版面對映**：TOML 4 section（`llm`／`token`／`ui`／`web`）≠ UI 7 分類 → registry 要寫明映射，唔可以靠猜。

---

## 6. 防漏機制（v1 判死「假綠」，v2 重寫）

**目標**：唔准「加咗 config 但唔記得加 UI」；亦唔准假綠。

新斷言集（`tests/check_settings_registry.py` ＋ Java harness）：
1. **全路徑**比對（`llm.mode`，非葉名 `mode`；`b.push` 有 4 個 section，葉名會撞）。
2. 每個 entry：**3 個 lang key 齊**（en_us／zh_cn／zh_tw，label＋tooltip）。
3. 每個 entry：setter 尾有 `SPEC.save()`（或走統一 save 路徑）。
4. **get → set → get round-trip** 一致（toggle／數字／文字／清單四型）。
5. **ListEntry separator 正確**：`recipeCategory*`／`ingredientNbt*` 用 **`;`**、`recipeCardMirrorCategories` 用 **`,`**（`PackAiConfig.java:619`）。
6. **反向斷言**：registry 唔准登記唔存在嘅 key。
7. `EXCLUDED` **fail-closed**：只准「冇 `set*` accessor」嘅 key 入（由 `grep` 機械判定），**唔准**自由文字理由。
8. 機械檢查「settings 相關 lang key 三檔齊」（現時 3 檔各 490 key 對齊、121 個 `packai.settings.*`；`check_reply_lang.py` 唔覆蓋 settings）。

Harness 跑法（要可獨立跑，因 `compileTestJava` 係 pre-existing 壞）：`javac -nowarn -d … -sourcepath "src/main/java;src/test/java" …` ＋ `java -ea`，classpath 用 `forge/1.19.2/build/classpath/runClient_minecraftClasspath.txt`（存在，17.5KB）。

---

## 7. 切批（反方：高風險同高價值唔可以綁死）

| 批 | 內容 | 為何獨立 |
|---|---|---|
| **A（低風險、先出貨）** | 16 個 setter 補 `SPEC.save()`＋harness；刪 2 條死 lang（×3）；改 SPEC 過時 comment；**5 個 config-only 落「現有」tab**（mirror／NBT skip／NBT keep／ollama 網址／模型） | 全部可獨立回滾，即時減痛 |
| **B（骨架）** | 新三欄骨架＋registry＋搜尋＋**遷移 28 控件**（此時存檔語意已有測試護住） | 大改動，獨立審 |
| **C（新設定）** | S-1 回答語言（**行為改變，單獨一批**）／S-2 Ollama UI（A 已做則只剩細節）／S-3 JEI 類別（已存在，只加 link）／S-4～S-10 | 行為改變要 harness＋真機驗 |

**i18n 工作量修正**：唔係「7 個 lang ×3」而是 **每 entry label＋tooltip × ~40 entry × 3 檔 ≈ 240 條**；另加 §6.8 機械檢查。

---

## 8. 最大未知 → **已用實測 mock 解開**（2026-09-14 12:0x）

用 repo 真實常數（`PackAiSettingsScreen`：首行 top=56、control 行距 22、高 20、`doneY=h-28`；`ModelPickerScreen`：`ROW_H=18`、`listBottom=h-52`）＋ MC auto GUI scale 實算：

| 螢幕 | 邏輯解析度 | 今日 ASK tab 可見行 | 新三欄（22px 行距） | 新三欄（18px 行距） |
|---|---|---|---|---|
| 854×480（auto s=2） | 427×240 | **7** | **6** | 7 |
| 1280×720（auto s=3） | 426×240 | 7 | 6 | 7 |
| 1366×768（auto s=3） | 455×256 | 7 | 6 | 8 |
| 1920×1080（auto s=4） | 480×270 | 8 | 7 | 9 |
| 1920×1080（手動 s=3） | 640×360 | 12 | 11 | 14 |

**結論（老實講）**：
1. **「解決 480p 逼爆」唔成立** —— 新三欄一屏可見行數同今日**相若（6–7 vs 7）**。KPI 正式改為「**可搜尋＋可分類＋可增長＋統一入口**」。
2. 真正嘅必要性係**算術**：今日 4 tab 最多 9 行槽；加 ~15 項後 ASK 要 384px（連 360px 都放唔落）→ **tab 模型加唔到**，唔係為咗靚。
3. 分類後**最大分類 = 8 行**（Connection 8／Answers 7／Recipes 7／Debug 6／Advanced 5／Interface 4／Quests 3）→ 240px 一屏 6 行，**最大分類要捲 1–2 下**（但有搜尋＋hover 描述補償）。
4. 三個補償手段可再搶位（B 批實作時量真機）：描述面板改 **hover overlay**（+20px ≈ +1 行）、行距收窄至 20、`h<260` 時「精簡模式」（藏 tooltip 行）。

## 9. 真機驗收（rebuild 後逐項做，唔准只信 harness）

1. **存檔真偽**：改一個 toggle → **工作管理員強殺遊戲** → 重開睇 `config/packai-client.toml`（同時判定 16 個 setter 唔 save 嘅真實影響）。
2. **三檔 GUI scale × 三解析度**：480p／720p／1080p（auto）＋手動 2／3 → 搜尋框／清單／描述面板／按鈕**有冇重疊或出界**（每個分類都睇，尤其最大分類）。
3. **值往返**：每個 entry 改一次 → 重開遊戲 → 值仍在（含 5 個新 UI、四種輸入型別）。
4. **清單 separator round-trip**：mirror（逗號）／recipeCategory（分號）／NBT（分號）存檔後直接讀 toml 字串；再確認「刪走一個 keep pattern 有冇作用」（預期：冇 → UI 要老實講「只可加」，或改 accessor 令清空生效）。
5. **密鑰**：貼新 key → 重開生效；再**只按返回**確認舊 key 冇被 `•••` 覆蓋；`grep logs/latest.log` 確認冇 key 洩漏。
6. **重設**：改 3 個值 → 重設 → 值返舊且即時行為返舊；「重置全部」**唔可以**清 key。
7. **舊 toml 兼容**：用刪 key 前嘅 `packai-client.toml` 開遊戲 → 唔 error、唔靜默改行為。
8. **搜尋**：打 `key`／`nbt`／中文 label 都命中；空結果有提示。
9. **Headless 收貨**：Java harness `-ea` PASS；`python tests/check_*.py` = baseline 冇新增紅；**純 layout 函數 headless 斷言**（`static List<Rect> rowRects(w,h,n)` → 斷言不重疊／不出界）。
10. **兩次 code review**（pass1 重構／pass2 三個月後脆弱位）。
11. **文件**：`README.md:57/105`（settings 流程）＋CF 描述一併更新（v1 漏）。

---

## 10. 風險（v2 補三條真洞）

1. **值損毀**：清單 separator（`;` vs `,`）＋`ingredientNbtKeepPatterns` default 永遠被 union（`PackAiConfig.java:539–546`）、空清單 fallback 回 default（:551–553、:562–566）→ UI 要誠實（「只可加」）或改 accessor。
2. **佔位符覆蓋真 key**（§4.3 不變式）。
3. **重設後 cache 未清**（`ModelCatalog.invalidate()`）。
4. 舊 toml 兼容性（刪 `modularToolSingleItem` 對 `=false` 用戶嘅影響）。

---

## 11. SK 已決定（2026-09-14）

1. **`RecipeCategoryScreen` → (ii) 用新清單取代並刪舊 screen**（連 11 個 `packai.recipe_cats.*` lang ×3 同步刪）。→ 併入 B 批工作範圍，新增一項「舊頁功能移植＋孤兒 lang 清零」。
2. 切批 A／B／C：**A 先行**（低風險即時減痛）。
3. §8 mock **已完成**（見上表）；KPI 已改寫成「可搜尋＋可分類＋可增長＋統一入口」。

**因此 B 批範圍更新**：新三欄骨架＋registry＋搜尋＋**遷移 28 控件**＋**接手 RecipeCategoryScreen（搜尋／toggle／拖曳／重設）＋刪舊 screen＋清 11 條 lang**。
