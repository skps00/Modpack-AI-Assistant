# a+b 實作 Code Review（兩階段）— worldgen 三類（a）＋ 必答清單（b）

- 日期：2026-09-20｜審查者：Hermes（subagent，**唯讀**）
- 審查對象：**本輪 a+b 未 commit 改動**（`git status` 全部未 commit）
  `logic/WorldgenFacts.java`／`logic/WorldgenIndex.java`／`logic/AcquireAskTool.java`／`logic/ReplyLang.java`／
  `logic/AskEngine.java`（只限 a+b 嗰幾個 hunk）／新 `logic/InfoCompleteness.java`／
  新 harness `test/.../WorldgenRoutesCheck.java`、`InfoCompletenessCheck.java`／新 `tests/check_info_completeness_hook_order.py`／
  lang 三檔（`packai.reply.worldgen_ore`、`packai.reply.info_gap_header`）
- 依據：plan v3.1 `docs/plans/2026-09-20-worldgen-and-mandatory-facts.md`（§1／§2／§4／§9）
- 方法：讀檔（含 `git diff`）＋ 讀真機 trace（`packai_sandbox_ftb`，唯讀）＋ 唯讀 python 掃描；**冇改 repo 任何檔、冇 commit、冇跑 gradle、冇開遊戲**
- 範圍外（只作背景，未審）：同檔內 Plan F hunk（`frameKind`／`AskMissFallback`／`bindAskToolEnv`／`withSuggestedItemIds`）、其他未 commit 改動

## 0. 結論速覽

| 級 | 數 | 主題 |
|---|---|---|
| **P0** | **1** | worldgen gap 行（礦物分佈）被自家 token 判定永遠當「已覆蓋」⇒ b 對 a **完全失效**（有真機 trace 證據） |
| P1 | 6 | cap 先於過濾（維度靜默消失）；`putDimension` 無視 overwrite；維度超限計數冇 consumer；gap 側缺 `scanModJars` 閘；人化邏輯雙寫；共享 configured 時丟「礦脈大小」 |
| P2 | 8 | 參數未用／key 漏兜底／位置式模板／測試死碼與硬編碼／閘錨脆弱／靜態 lock 包住 I/O／只覆蓋焦點物品／每 session 首 ask 觸發 359 jar 掃描（成本未量） |

---

# Pass 1 — 即時質量（逐檔）

## [P0-1] worldgen gap 行永遠被 `mentioned()` 當「已覆蓋」⇒ 必答清單對礦物分佈完全冇效

**位置**
- `logic/InfoCompleteness.java:44`（`if (mentioned(lower, line)) continue;`）
- `logic/InfoCompleteness.java:99-127`（`mentioned`）；:104-109（`NS_PATH` 命中即 `return true`）；:110-125（`PATH_RUN` 切段 + `wholeWord` 亦係 `return true`）
- gap 行本身：`logic/AcquireAskTool.java:138-190` → `ReplyLang.java:461`（模板 `礦物分佈：%s｜生態域：%s｜…`，**第一個 `%s` 就係 ore feature id**，而 FTB/Ad Astra 嘅 feature id ＝ 物品 id）
- 產生點：`logic/AskEngine.java:1011` → `:1699-1721`（`:1721` 用 `humanWorldgenRoutes` 造 worldgen gap）

**機制（code 推理，逐條唔跳）**
1. gap 行 = `礦物分佈：ad_astra:moon_desh_ore｜生態域：…｜高度：-80..80｜…`。
2. `mentioned` 先做 `NS_PATH = [a-z0-9_.-]+:[a-z0-9_./-]+`；第一個命中就係 `ad_astra:moon_desh_ore`。
3. 答案 body 一定要有 heading `[[item:ad_astra:moon_desh_ore]]`（輸出契約硬性要求；`:962-964` 嘅 `AskMarkerRepair` 亦會把 FACT 標記貼返）⇒ `answerLower.contains("ad_astra:moon_desh_ore")` ＝ true ⇒ `return true` ⇒ **整行 gap 被棄**（連答案冇提過嘅「高度／礦脈大小／每區數量／維度」一齊棄）。
4. 就算無 marker，`PATH_RUN` 亦會把 `ad_astra:moon_desh_ore` 切成 `moon`／`desh` 等 ≥4 字段，答案寫 "Moon Desh Ore"／"Desh Ingot" 一樣命中 ⇒ 同一行第二重必殺。
5. 結論：**只要答案提到焦點物品（必然發生），礦物分佈 gap 行 100% 唔會出現** ⇒ plan §2 b-1「世界生成類 gap」在生產上係死碼；b 對 a 只係 no-op。

**證據（真機 trace，唯讀；`packai_sandbox_ftb`，2026-09-20 08:46 輪，lang=en_us）**
| trace | acquire `tool.result` 有 `Ore spread: <item id>｜…` | `display.body.final` 含 `[[item:<id>]]` | gap 段含 ore 行 / id |
|---|---|---|---|
| `ask-20260920-084621-ad_astra_moon_desh_ore.jsonl` | **有**（`Ore spread: ad_astra:moon_desh_ore｜Biome: ad_astra:lunar_wastelands｜Height: -80..80｜Vein size: 9｜Count: 9｜Dimension: ad_astra:moon`） | 有（第一行 `[[item:ad_astra:moon_desh_ore]] Moon Desh Ore`） | **冇** |
| `ask-20260920-084639-ad_astra_mars_ostrum_ore.jsonl` | **有** | 有 | **冇** |
| `ask-20260920-084648-ad_astra_venus_calorite_ore.jsonl` | **有** | 有 | **冇** |
- 三份嘅 gap 段只有 jar route 行（例：`used in blasting -> "desh ingot"`）＋ header，**冇任何** ore 行。
- 因為 acquire 同 gap 側都用同一個 in-memory index（`AcquireAskTool.java:81` vs `AskEngine.java:1721`），acquire 出得 line ⇒ `routesForItem` 非空 ⇒ 唯一能解釋「gap 側冇」嘅就係 `mentioned()` 過濾。**「路線係空」已被排除**。
- 時序補註（唔隱瞞）：08:46 輪之後先加「quoted／`->`」第三類 token（`InfoCompletenessCheck.java:87-99` NC5）；但今次 P0 依賴嘅 `NS_PATH`／`PATH_RUN` 兩類係最初就有嘅（plan §2 b-1 已寫死），故最終碼結論不變。09:04 輪（最終碼）gap 段整段冇出現 ⇒ 該輪「全部已覆蓋」，**既不證實亦不否證**；未在最終碼上重跑真機（唔准開遊戲）。

**修法（建議）**
- 唔好再用「任何 ns:path 命中 ⇒ 全行已覆蓋」處理含**多欄位**嘅行。二選一：
  (a) worldgen gap 行改**逐欄位**判定（height／vein size／count／biome／dim 各自搵 token）；
  (b) 保留全行判定，但把「焦點物品自己嘅 id／顯示名／已出 heading id」由 token 集合剔走，並要求命中門檻 >1（例如 biome id 或 dim id 才算）。
- 加 harness：`answer` 含 item id 但冇 `Height:` ⇒ 期望 gap 出現 `高度`（**現行必紅**，可做真負控）。

---

## 1-1 `WorldgenFacts.java`（a-1）

| 級 | 位置 | 問題 | 依據 |
|---|---|---|---|
| **P1** | `:577-608` `putDimension(String dimId, JsonObject root)` | **冇 `overwrite` 參數、亦冇去重**：`biomeToDim` 只增不減。掃描次序係 jar（`overwrite=false`）先、loose（`overwrite=true`）後（`WorldgenIndex.java:131-133`），但 dimension 唔理 overwrite ⇒ datapack／overrides 覆寫同一個維度時，舊 jar 嘅 (biome,dim) 仍然留低 ⇒ ① 報錯維度（玩家照跟去錯維度）② 舊殘留可令另一個維度嘅同一 biome 變「歧義」而整個 drop | 對比 `:486-505` `putBiome`（`if (!overwrite && biomes.containsKey(id)) return;`）、`:526` `putPlaced`；`noteBiomeDim :610-626` 只有 add／remove-on-conflict |
| P2 | `:34`、`:483-484`、`:569-571`、`:581-585` | `MAX_DIM_FILES`＋`dimensionFilesOverCap()` 係「author-only counter」，但全 repo **冇任何 consumer**（grep 只有自身 javadoc 同測試）⇒ plan a-1「超出要記 counter（唔准當已覆蓋）」形式上做咗、實際上冇人睇得到；`dimFiles++` 亦**先加後驗**（malformed 都佔額），且 cap 係 jar＋loose 共用 ⇒ 先掃嘅 jar 食晒 200 額，被丟嘅反而係 datapack 覆寫 | `grep -rn "dimensionFilesOverCap"` → 只有 `WorldgenFacts.java:33,568,569`；`:585` `dimFiles++` 之後先 `:586` 檢查 root |
| 觀察（非缺陷） | `:104-134`、`:141-185`、`:187-215` | `Kind.DIMENSION` 四個使用點齊（`kindFromPath` :130-132、`idFromPath` `case DIMENSION -> "dimension/"` :168、`ingest` :213、Store 映射）——同 plan v3.1 §1 a-1 逐條對得上，**R2 死因已修** | 讀碼 + `WorldgenRoutesCheck.java:33-41` 斷言（今日綠，來自 plan §9） |

## 1-2 `WorldgenIndex.java`（a-2）

| 級 | 位置 | 問題 | 依據 |
|---|---|---|---|
| **P1** | `:72`（`formatMatches(itemId, MAX_ROUTES_PER_ITEM)`）＋ `:77-83`（之後才 `keepOreRoute` 過濾）＋ `WorldgenFacts.java:748-755`（cap 在**最後**才截、`:719-733` 嘅衍生 `placed_feature … in biome …` 係**最後**加入） | **Cap 早於 kind 過濾**：`formatMatches` 會混雜 biome／structure／tag／modifier 行，先截 8 行再由 `keepOreRoute` 剔 ⇒ 大包／多 tag 多 modifier 嘅礦物，`in biome` 行（**維度嘅唯一來源**）最先被截斷 ⇒ 生態域／維度靜默消失，而 `routesForItem` 明文「永遠唔回 miss」⇒ 完全無聲 | 讀碼；FTB 3 個礦物（A-a1a）今日過，係因為嗰批行數 ≤8 — 未證明其他礦物安全 |
| P2 | `:65-84` | `routesForItem` 對 `gameDir == null` 唔報錯，會用**上一次**載入嘅 store（換 instance／換包時可能回另一包資料）；`INSTANCE` 係 process-wide 靜態 | `:66-69`、`:122-125` |
| P2 | `:122-138` | `doEnsure` 全程**揸住 `lock` 做 I/O**（359 jar＋loose）；`AskService` 用 `CompletableFuture`（`client/service/AskService.java:315`）⇒ 理論上兩個 ask／warmup 併發會互等（今日單 ask 線程未爆） | 讀碼 |
| 觀察（非缺陷） | `:98-115` `keepOreRoute` | 只留 `placed_feature`／`type=minecraft:ore` 嘅 configured／`in biome`，且 `end == rest.length() || charAt(end)==' '` 邊界正確（`minecraft:ore_xxx` 唔會誤中） | 讀碼 |

## 1-3 `AcquireAskTool.java`（a-3／a-4）

| 級 | 位置 | 問題 | 依據 |
|---|---|---|---|
| **P1** | `:216-231` | `configuredOwner` 係 `Map<configuredId, placedId>` 一對一：同一個 configured feature 被多個 placed 引用時，**只有最後一個** placed 拿得到 `size` ⇒ 其餘組別靜默冇「礦脈大小」（plan a-4 要求 `configured_feature` 提供 size） | 讀碼；`:219` `put` 覆蓋前值 |
| **P1** | `AskEngine.java:1705` vs `AcquireAskTool.java:71` | gap 側收集 jar route **冇** `PackAiConfig.scanModJars()` 閘（acquire 有），兩條通道語意唔一致：off 時答案 facts 冇 jar 料但 gap 段可能仲列（實務影響細：`byItem` 只由受閘掃描填充，`JarLightIndex.java:71,115`） | 讀碼 |
| **P1** | `AskEngine.java:1705-1719` vs `AcquireAskTool.java:109-119` | 人化邏輯**雙寫**：loot 線（`L|`）gap 側直接 `ReplyLang.lootTableObtain`，冇 `humanJarRoute` 嘅「line 必須含 table id，否則補上原 id」保險 ⇒ 下次改 route 格式／改 lang key 要改兩處，易漂移 | 讀碼 |
| P2 | `:90-102` | `mergeRoutes(itemId, lang, …)` 兩個參數**完全冇用**（plan 指定簽名，但未用＝可讀性負擔；`itemId` 亦令呼叫者以為有 per-item 邏輯） | 讀碼 |
| P2 | `:104-106` | `droppedJarRoute` 只為 `AskEngine.infoGapLines` 而設（跨類耦合 `AskEngine → AcquireAskTool` 常數語意） | 讀碼 |
| 觀察（非缺陷） | `:68-84`、`:121-135`、`:188-190`、`:289-303` | 薄 wrapper 行為等價（`LinkedHashSet` 去重、次序 jar→worldgen→loose 不變）；`displayHeight` 只剝字面 `absolute `（`above_bottom`／`below_top` 保留）；`humanWorldgenRoute` 對 `null`／空白回 `""`、`split("\\R")` 處理 CRLF — **同 plan a-3／a-4 一致** | 讀碼 + `WorldgenRoutesCheck.java:38-40,95-109` |

## 1-4 `InfoCompleteness.java`（新檔，b-1）

| 級 | 位置 | 問題 | 依據 |
|---|---|---|---|
| **P0** | 見上 [P0-1] `:44`、`:99-127` | 覆蓋判定過寬（any-token hit）⇒ 多欄位行被整行棄 | trace + 讀碼 |
| P2 | `:52-57` | `header` 只檢查 `null`／`isBlank`；`ReplyLang.tr`（`:122-142`）在三語都缺 key 時會**回傳 key 原文** ⇒ 會把 `packai.reply.info_gap_header` 印給玩家。同 repo 慣例（`ReplyLang.askMissRetry:892-897` 有 `s.equals(key)` 檢查）唔一致。因 `bundleLang`（`:114-119`）非中文一律 → `en_us`，只有三語同時缺 key 才觸發 ⇒ 降級 P2 | 讀碼 |
| P2 | `:64-83` | 用 `ReplySources.HEADER`（`ReplySources.java:11`，`【來源】/【来源】/[Sources]`）`find()` 第一命中做插入點：答案內文若先提及「[Sources]」字樣，gap 段會插錯位（低機率） | 讀碼 |
| 觀察（非缺陷） | `:30-33`、`:49-51` | gaps 空／null ⇒ 回**同一 reference**（byte-identical，plan A-b1 ✔）；含 `[[recipe_card:`／`[card:` 嘅 gap 行被剔 ✔；`:110-125` 路徑段比對、`:130-158` quoted／`->` 比對有對應負控（`InfoCompletenessCheck.java:74-99`） | 讀碼 |

## 1-5 `ReplyLang.java`／lang 三檔

| 級 | 位置 | 問題 | 依據 |
|---|---|---|---|
| P2 | `:461-490` `worldgenOre` | **位置式配對**：`parts[i]` 對 `vals[i]`，`Math.min(parts.length, vals.length)` 令段數唔對時**靜默截斷**（多段被丟／少段令維度消失）；亦冇檢查每段含 `%s`。三語今日都係 6 段 6 個 `%s`（已實測），但**冇測試守住**（`WorldgenRoutesCheck.java:104-106` 只驗非空） | `python -c` 讀三檔 json：`parts=6 pct=6`（en_us／zh_cn／zh_tw） |
| 觀察（非缺陷） | `:495-497`、lang 三檔 | `packai.reply.info_gap_header` 三語齊（`資料有、答案未提：`／`资料有、答案未提：`／`On record, not in the answer:`）；`worldgen_ore` 空值規則（null／空白 ⇒ 連標籤刪）實作正確，`ad_astra:orbit` 無映射案例輸出冇「維度：」 | `WorldgenRoutesCheck.java:104-109`；讀碼 |

## 1-6 `AskEngine.java`（b-2 hook）

| 級 | 位置 | 問題 | 依據 |
|---|---|---|---|
| P2 | `:1699-1722` `infoGapLines` | 只覆蓋**焦點物品**（`heldItemId`）；多選（alsoSelected）冇 gap。若係刻意，應寫入「已知限制」 | 讀碼 |
| P2 | `:1011 → :1721 → WorldgenIndex.java:69` | hook 對**每個**標準答案都行 ⇒ 每 session 第一次回答（就算唔係 worldgen 問題）都會觸發 359 jar 全掃描。**離 render thread**（`AskService.java:315` `CompletableFuture.supplyAsync`）所以唔會卡畫面，但成本未量（硬規則唔准跑 gradle）。唯讀 python *zip 列目錄* proxy：357 jar／0.47s（只作數量級參考，唔等於 Java 讀＋Gson parse） | `AskService.java:315`；python proxy |
| 觀察（非缺陷） | `:969-1010`、`:1011`、`:1012` | hook 位置＝STANDARD 區塊之後、`if (override) {` 之前，覆蓋 `:1012`／`:1016`／`:1019` 三條 LLM 出口；閘 `tests/check_info_completeness_hook_order.py` 用**唯一錨**＋括號配對（NC3a 假綠已修）驗證 | 讀碼 + `check_info_completeness_hook_order.py:11-13,30-99` |

## 1-7 測試／閘

| 級 | 位置 | 問題 | 依據 |
|---|---|---|---|
| P2 | `WorldgenRoutesCheck.java:90-94` | 負控 `for (String line : empty) { assert … }` 係**死碼**（`empty` 必空，前面已 assert）；真正有效嘅只有 `assert empty.isEmpty()` | 讀碼 |
| P2 | `WorldgenRoutesCheck.java:139-160` | 硬編碼 `C:/Users/skps9/…/packai_sandbox_ftb` ＋ 真值 `biomeDimensionCount()==10`／`ad_astra:moon_desh_ore` ⇒ 換機即 SKIP、換 pack／升 pack 即紅（環境紅，唔係 code 紅） | 讀碼 |
| P2 | `tests/check_info_completeness_hook_order.py:12` | `A2 = "if (override) {"` 要求全檔 **count==1**：日後任何新增同形 `if (override) {` 會令閘**假紅**（錨脆弱）；`:36` 自認冇 text-block 掃描 | 讀碼 |
| 觀察 | `InfoCompletenessCheck.java:19-117` | 覆蓋 byte-identical／三語／token 命中／路徑段／quoted-arrow／scrub 存活，皆有真負控（對應 plan §9 NC2／NC4／NC5） | 讀碼 |

---

# Pass 2 — 前瞻脆弱位（三個月後接新 pack／新版本）

> 假設：① 換／升整合包（jar 數上升、命名風格唔同、有 datapack 覆寫）② MC／NeoForge 版本推進（目錄或 JSON 形狀變）③ 加多一種 gap 類（tooltip／quest）。

**V1 掃描全靠「路徑形狀」寫死 ⇒ 版本一變，整個維度類靜默歸零（最高危）**
- `logic/WorldgenFacts.java:130-132`（`dimension/` 硬編碼）、`logic/WorldgenIndex.java:189`（`walkTree`）、`:245`（`scanJarFile`）——兩個入口都先過 `isWorldgenPath`。
- 風險：`worldgen/*` 子目錄改名、`dimension_type/` 之類新資料夾、`.json5`／zip 內 datapack，全部會靜默 0 coverage；而 `routesForItem` 明文「永遠唔回 miss」（`WorldgenIndex.java:62-64`）⇒ **零 coverage 冇任何訊號**。
- 建議：加「掃到幾個 worldgen 檔／維度檔」嘅 health 計數（trace event 或 `dimensionFilesOverCap()` 一齊報），並把資料夾白名單抽成一張可由新版覆寫嘅表；至少令「0 個維度檔」變可見。

**V2 維度形狀白名單太窄（只 fixed／`biomes[]`）⇒ 新包可能大面積冇映射**
- `logic/WorldgenFacts.java:594-607`。
- 風險：`multi_noise`＋preset、`the_end`、`checkerboard`、datapack 用 `biome_source` 引 preset 名等都唔記錄（plan 明文只認①②），新 pack／升版後 mapping 數可以係 0，而玩家只見到「冇維度」。
- 建議：把「唔認得嘅形狀」記 counter（現時連 counter 都冇），並在 health 輸出對比「dimension 檔數 vs 認得嘅檔數」。

**V3 token 命中判定係人手調參、英文 ASCII 取向 ⇒ 換語言／換命名即漂移**
- `logic/InfoCompleteness.java:16-22`（`STOP` 8 個字、長度門檻 3／4）、`:110-125`（只認 ASCII whole-word）、`:182-204`。
- 風險：同一 P0-1 機制嘅鏡像——新 pack 命名（CJK 顯示名、`+`、`.`、大寫）會令判定向「假覆蓋」（靜默漏）或「假 gap」（噪音）漂移，而**冇 metric**可以發現漂移。
- 建議：加 gap 統計（每類 gap 命中／被剔數量）入 trace；把判定由「字串啟發式」收窄到「明確 id 欄位比對」（亦順帶修 P0-1）。

**V4 cap／budget 互相搶，且冇「被截」訊號**
- `WorldgenIndex.java:31`（`MAX_ROUTES_PER_ITEM=8`）＋ `:72`（cap 先於過濾）＋ `WorldgenFacts.java:748-755`；acquire 側預算 `AskToolContext.clipAcquireLines`（12／3）＋次序 jar→worldgen→loose（`AcquireAskTool.java:96-101`）。
- 風險：新 pack 多一條 ore route／多一個 tag，就會擠走**包自訂**嘅 loose 行或 `in biome` 行；冇 log ⇒ 玩家只覺得「有時答得少」。
- 建議：cap／budget 觸頂時記 trace（現時 `formatMatches` 截斷無聲）；`in biome`／`configured` 行應該優先於 tag／modifier 行。

**V5 測試綁死單一沙盒真值 ⇒ 升 pack 即紅，紅咗之後冇人信**
- `WorldgenRoutesCheck.java:139-160`（`==10`、固定 biome／item、開發機絕對路徑）。
- 建議：真值改為由 fixture datapack 自建（`writeFixture` 已有），sandbox 段只做「≥1 映射／≥3 行」嘅弱斷言＋印數字；路徑改由 property／env 提供，缺席即 SKIP（現時 SKIP 已做，但真值 assert 仍硬）。

**V6 兩條人手化／兩條 gap 收集路徑並存 ⇒ 下次改格式必漂移**
- `AcquireAskTool.java:109-119` vs `AskEngine.java:1705-1719`；`AcquireAskTool.java:138-190` vs `:193-241`。
- 建議：抽成單一 `WorldgenRouteHumanizer`／`humanJarRoute`（package-visible），gap 側只呼叫，唔准自己再寫一份。

---

# 分級清單（交付）

## P0（真 bug，要修）— 1 條
1. **[P0-1]** worldgen gap 行（`礦物分佈`）被 `mentioned()` 的 any-token 判定永遠當已覆蓋 ⇒ b（必答清單）對 a（礦物分佈）完全失效。
   - `InfoCompleteness.java:44`、`:99-127`；gap 行首 token ＝ 物品 id（`AcquireAskTool.java:190`＋`ReplyLang.java:461`）；產生點 `AskEngine.java:1011`→`:1721`
   - 證據：`packai_sandbox_ftb` trace `084621／084639／084648`（acquire 有 ore 行、body 含 `[[item:…]]`、gap 段零 ore 行）
   - 建議：改逐欄位判定或剔走「焦點物品自身 id／顯示名」token；加「answer 含 id 但冇 Height」負控。

## P1（應該修）— 6 條
1. `WorldgenIndex.java:72`＋`WorldgenFacts.java:719-733,748-755`：cap 早於 kind 過濾、`in biome` 行最後加入 ⇒ 大包靜默丟生態域／維度。
2. `WorldgenFacts.java:577-608`：`putDimension` 無視 `overwrite`、只增不減 ⇒ datapack 覆寫時殘留舊映射／假歧義（錯維度）。
3. `WorldgenFacts.java:34,569-571`：維度超限 counter 冇任何 consumer（plan 要求「記住」但冇出口）。
4. `AskEngine.java:1705` 缺 `scanModJars` 閘（與 `AcquireAskTool.java:71` 不一致）。
5. gap 側與 acquire 側人手化邏輯雙寫（`AskEngine.java:1705-1719` vs `AcquireAskTool.java:109-119`），loot 線少一層保險。
6. `AcquireAskTool.java:216-231`：共用 configured feature 時除最後一個 placed 外全部冇「礦脈大小」。

## P2（可選）— 8 條
1. `AcquireAskTool.java:90-95`：`mergeRoutes` 兩個未用參數。
2. `InfoCompleteness.java:52-57`：header key 三語全缺時會印出 key 原文（`ReplyLang.askMissRetry:892-897` 已有慣例可抄）。
3. `ReplyLang.java:461-490`：位置式模板＋`Math.min` 靜默截斷；加「6 段／段內 1 個 `%s`」測試。
4. `InfoCompleteness.java:64-83`：`ReplySources.HEADER` 第一命中做插入點。
5. `WorldgenRoutesCheck.java:90-94`：死碼負控。
6. `WorldgenRoutesCheck.java:139-160`：硬編碼沙盒路徑＋真值。
7. `tests/check_info_completeness_hook_order.py:12`：`if (override) {` 錨要求 count==1，易假紅。
8. `AskEngine.java:1699`（只覆蓋焦點物品）＋ `WorldgenIndex.java:122-138`（lock 內做 I/O，並行 ask 會互等）＋ 每 session 首個 ask 觸發全掃描（成本未量）。

## 明文「未核」（唔當結論）
- 未跑 `compileJava`／53 個 harness／python 閘（硬規則禁止）⇒ 本報告一切「綠」嘅引用都係 plan §9 記錄，非我親跑。
- P1-1（cap 搶位）只有機制推理＋A-a1a 3 個礦物通過，未在真機踩到大包案例。
- python zip 0.47s 只係目錄列舉 proxy，唔等於 Java 掃描耗時。
- 最終碼（09:04 之後）未再有「gap 段非空」嘅真機樣本 ⇒ P0-1 靠 08:46 樣本＋最終碼同構推理。
- Plan F 相關改動（`frameKind`／`AskMissFallback`／`bindAskToolEnv`／`withSuggestedItemIds`／`askMissRetry` 等）**唔屬**本輪 a+b，未審。
