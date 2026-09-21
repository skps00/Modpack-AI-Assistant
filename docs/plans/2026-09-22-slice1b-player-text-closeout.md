# Plan — Slice 1b：玩家文字收口（**收窄版**：prose 層 jargon＋唯一性措辭）

- 日期：2026-09-22（承 Slice 1 `8cf28a4`）
- 狀態：**v4 — 依 R2 中立裁判建議收窄範圍**：只做 B1＋B4（只動 `AskReplyScrub`），B2／B3 拆去 **Slice 1c**
- Repo：`C:\Users\skps9\Documents\Code_Project\super_minecraft_AI_player`

## 0. 為咩收窄（R1 3:7 → R2 3:7；裁判：方向對，但 B2／B3 牽連太多未授權閘）

| 版本 | 結果 | 卡死點 |
|---|---|---|
| v1 | R1 反方 **7:3 唔過** | B1 做錯層（marker 表只 gate FACT fallback）／P1 歸因錯（真源頭係 `WorldgenFacts` 工具結果＋指令文字）／NC3 打錯閘／`AcquireJarRoutesCheck` 會紅／B4 靶唔中 |
| v2／v3 | R2 反方 **7:3 唔過**；中立裁判：`v2_adequate = false` | B3 邊界假設錯（`Plainify.lootLine` 同時餵模型 tool result 同玩家顯示 → 改咗必紅 `AcquireJarRoutesCheck:104-106`，而該檔 §3 又寫「唔准改」）／B2 會打爛 runtime 偵測器 `AskLoopState.isEmptyOrMiss:632-634`（靠「未索引」字樣判斷）＋要動 4 個未授權閘＋**neoforge 樹被 python 閘強制同步但 AGENTS.md 禁止改（PAUSED）** |
| **v4** | 收窄 | **B1（jargon）＋B4（唯一性）只喺 prose 層做**，唔碰 lang／generator／neoforge／`Plainify`／任何契約閘 |

**移出（Slice 1c，連 Slice 2 一齊做）**
- **B2 源頭措辭**（`WorldgenFacts.missLine` 英文 jargon、`fact_check` 規則 19、`acquire_index_miss`／`summon_index_miss`）
  → 要同時改：`tests/update_reply_prompts.py`（generator，唯一寫入者）、`check_reply_prompt_keys.py`、`check_honest_miss.py`（**會掃 forge＋neoforge × 三語**）、`check_worldgen_lookup.py`、`WorldgenFactsCheck.java`、`HonestMissCheck.java`、**`AskLoopState.isEmptyOrMiss`（runtime 偵測器，靠「未索引／not indexed」字樣）**，再加 neoforge PAUSED 決策（要 SK 批）。
- **B3 顯示／事實行人話化**（`Plainify.lootLine:197`、`ReplyLang.jarLoot:847-853`、機翻防護）
  → 正解係 Slice 2 嘅「結構／寶箱人話名」；要改契約測試 `AcquireJarRoutesCheck.java:104-106`＋`AskLineHumanizeCheck`，屬 Slice 2 範圍。

## 1. 背景證據（**19 條** ask trace，全部有 body；唔再寫「13 條」）

| 句式 | 出處 |
|---|---|
| 本包**未索引**到… | sandbox `ask-20260922-000814-minecraft_diamond`、`ask-20260922-001206-minecraft_amethyst_shard` |
| 本包**索引未收录**… | sandbox `ask-20260922-000959-minecraft_crying_obsidian` |
| 本包**索引没有**… | real `072649 chestopener`、`073004 brazier`、`073427 necklace`、`074619 active_charm` |
| …**索引都没有**… | real `072740 witherstar`、`072935 thunder_gem`、`074545 insosaber`、`081111 schematicannon` |
| **唯一**（已知）來源 | sandbox `223609 brazier`（`合成就是唯一已知来源`）、real `074545 insosaber`（**有 `[card:1]`**） |

**過濾現況（親量；方法已修正）**：`check.scrub` 事件係**頂層**欄位（`before`／`after`／`rules`）；
- 19 條 trace、**13 條** body 含 jargon／「唯一」；
- `check.scrub` 事件共 **37** 條 ＝ `scrubPromptEcho` **18** ＋ `stripDuplicateSectionHeaders` **19**；
- `before != after` ＝ **2 條**（都係 `scrubPromptEcho`，但改嘅唔係 jargon 字——jargon 句子全部原封不動）→ 結論不變：**現行 prose scrub 對呢類字零作用**（[fixture corpus](file:///C:/Users/skps9/Documents/Code_Project/super_minecraft_AI_player/docs/research/artifacts/2026-09-22-slice1b-jargon-corpus.md) 有 15 條原文，其中 A 類 11＋C 類 2 屬本 slice，B 類 2 條屬 Slice 1c）。

**掛載點（R3 已核）**：玩家 body 唯一源頭＝`AskReplyScrub.proseOrFacts`（`:1578-1590`）嘅 prose 支（`:1579 scrubPromptEcho`）；之後 `AskEngine.java:921` → `:1014 AskResult.text(body)` → `AskService.java:512 display.body.final`，全條路無再重算 prose。**B1／B4 只喺 prose 支做**（唔改 FACT 支）——FACT fallback 行嘅措辭屬 lang／1c 範圍，喺呢個 slice 動佢只會撞更多既有閘，冇必要。

## 2. 目標（可測）

| ID | 改動 | 成功條件 |
|---|---|---|
| **B1** | `AskReplyScrub` 新增 **prose 層定向改寫**（只喺玩家可見 prose 支＋FACT 支 return 前）。規則＝**逐字替換、按當前語言選字表**（禁模糊 NLP）：<br>**zh_cn**：`未索引`→`未见`／`索引未收录`→`资料未收录`／`索引都没有`→`资料里都没有`／`索引没有`→`资料里没有`／殘餘 `索引`→`资料`<br>**zh_tw**：`未索引`→`未見`／`索引未收錄`→`資料未收錄`／`索引都沒有`→`資料裡都沒有`／`索引沒有`→`資料裡沒有`／殘餘 `索引`→`資料`<br>**en**：`not indexed`→`not seen in pack data`／`no indexed worldgen`→`no world generation data`／`worldgen`→`world generation`<br>**字形唔准混排**（zh_cn 輸出唔准含繁體「資料」，反之亦然）＋**保留語意**（唔准整句刪） | corpus A 11 條＋C 2 條逐條比對 **golden 輸出**（已算出，見 corpus §E）→ 0 jargon；`AskReplyScrubCheck` 新增案例綠；真機 19 body 掃描 0 hit；混排 0 |
| **B4** | 同一 pass 加**唯一性措辭**改寫：`唯一已知来源`／`目前已知的唯一来源`／`唯一來源`／`唯一取得` → `目前資料見到的來源`（zh_cn：`目前资料见到的来源`） | corpus C 兩條 → 0「唯一」；有 `[card:1]` 條都唔准 |

**B1／B4 規則已離線 dry-run 驗證（2026-09-22 實跑，第二版規則）**：用 zh_cn 字表跑 corpus **11 條 A ＋ 2 條 C** → **0 殘餘 jargon／0「唯一」、0 繁體混排**；D 類 4 條 **byte-identical**。zh_cn golden 例：
- `本包未索引到钻石的世界生成资料…` → `本包未见到钻石的世界生成资料…`
- `除此之外，本包索引没有这只开胸器的掉落、交易或任务取得路径…` → `除此之外，本包资料里没有这只开胸器的掉落、交易或任务取得路径…`
- `本包的掉落表、宝箱、钓鱼、交易与脚本索引都没有它的取得路径` → `本包的掉落表、宝箱、钓鱼、交易与脚本资料里都没有它的取得路径`
- `合成就是唯一已知来源。` → `合成就是目前资料见到的来源。`

⚠️ zh_tw 字表要**用繁體輸入**先測（corpus 原文係簡體，唔可以拎簡體句套繁體表當驗證）→ 實作要另寫 5 條 zh_tw fixture。

**非目標**：源頭 lang 措辭（1c）｜顯示層 raw／機翻（1c／Slice 2）｜索引／過濾行為｜`AskEngine:826`｜卡落位｜neoforge 樹

## 3. 改動白名單（4 項）

1. `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskReplyScrub.java`（新 pass：B1＋B4；掛喺 `proseOrFacts` 嘅 prose 支，唔改 FACT 支）
2. 新 `forge/1.19.2/src/test/java/com/skps9/packai/logic/InternalJargonCheck.java`（輸入＝corpus 15 條；斷言 0 jargon、0「唯一」、D 類「唔准誤傷」4 條 byte-identical）
3. `forge/1.19.2/src/test/java/com/skps9/packai/logic/AskReplyScrubCheck.java`（加 regression 案例；**唔准刪現有 assert**）
4. `docs/plans/2026-09-22-slice1b-player-text-closeout.md`（本檔）＋`docs/research/artifacts/2026-09-22-slice1b-jargon-corpus.md`

> 唔喺白名單＝唔准改。**唔准改** lang／`update_reply_prompts.py`／任何 `tests/check_*.py`／`Plainify`／`AcquireJarRoutesCheck`／neoforge 樹。

## 4. 驗收（先寫標準，後逐項真跑，全部留工具輸出）

1. `gradlew.bat compileJava` RC=0
2. harness 全跑：**58/58**（57＋新 `InternalJargonCheck`）
3. python 閘：**125 個、無新增紅**；baseline 已知紅＝`tests/check_ask_display_leak.py` RC=2（`NO LOG LINES`）→ **驗收時要記錄實測 baseline 值**
4. **負控 3 條（紅→還原→綠，sha 一致）**
   - NC1 停用新 pass → `InternalJargonCheck` 必紅（15 條全文）
   - NC2 只移除「索引都没有」一條規則 → `InternalJargonCheck` 必紅（證明 corpus 真覆蓋）
   - NC3 把 D 類（唔准誤傷）句子塞入一條會被改寫嘅 pattern → 必紅
5. **真機 A/B（沙盒）**：`minecraft:diamond`／`amethyst_shard`／`crying_obsidian` → 玩家 body 0 jargon、0「唯一」；**唔准退步**：Tetra 改裝版零件行、火盆「破坏 仪式火盆 会掉落」、「通用知识（非本包覆写）」標示
6. 可追溯：**jar build sha＋部署 sha＋backup 路徑＋trace 檔名＋python baseline**寫入 §6
7. 全過 → 本地 commit → 問 SK push

## 5. 風險／還原

- 風險：改寫太寬會誤改正常句 → 只做**逐字替換**；D 類 4 條做 byte-identical 斷言。
- 風險：模型日後用新寫法（例如「未收錄於包內」）→ 本 slice 只承諾杜絕**已知 15 條＋pattern 覆蓋**；新寫法屬下一輪 corpus。
- 還原：改動集中 4 項；`git checkout --` 完全還原；baseline＝`8cf28a4`（已 push）；jar 可還原（§6 記 backup 路徑）。

## 6. 驗收結果（2026-09-22 02:23）

| 項 | 結果 |
|---|---|
| `compileJava compileTestJava` | RC=0 |
| harness | **58/58**（含新 `InternalJargonCheck`） |
| python `tests/check_*.py` | TOTAL **125**；FAIL **1**＝`check_ask_display_leak.py` RC=2（`NO LOG LINES`）＝**baseline 已知紅**；無新增紅 |
| NC1 停用 `rewriteInternalJargon` | `runInternalJargonCheck` RC=1 → 還原 sha 一致 → 綠 |
| NC2 刪「索引都没有」規則 | RC=1（A5 golden 紅）→ 還原 sha 一致 → 綠 |
| NC3 D 類塞入會改寫 pattern（`通用知识`→`通用资讯`） | RC=1（identical 紅）→ 還原 sha 一致 → 綠 |
| `AskReplyScrub.java` sha256 | `e60c1a4c388b3938ac2a9399991908f87fdd7b9dae931b1608d10740b4f0e11f` |
| jar／部署／真機 A/B | **未做**（等 SK 批 deploy；本輪唔 jar） |
| baseline commit | `8cf28a4` |


## 7. Review 記錄

- R1（v1）：反方 7:3 唔過 → `docs/plans/reviews/2026-09-22_slice1b-R1-opposing.md`／`-supporting.md`
- R2（v2）：反方 7:3 唔過；中立裁判裁 R1 反方強、`v2_adequate=false`，建議收窄或出 v2.1
- **R3（v4；最後一輪）**：
  - 反方：**機械面全部驗證通過**（掛鈎點真對：`proseOrFacts:1578-1590` → `AskEngine:921` → `AskResult:1014` → `AskService:512`；白名單零契約閘（dual-tree 喺 `neoforge/README_PAUSED.md` 存在時只 WARN；`gen_tmp_check.py:6` 自動註冊新 check；57→58 準確）；A/C 規則真有效、D 類 byte-identical、無現有 harness 會紅、python baseline 相符）。**仍卡 3 條** → 已全部吸收：① corpus 15 條含 B 類（唔屬本 slice）→ corpus 已標明 B 歸 1c；② FACT 支掛載點未寫死 → 已寫（§1 掛載點）；③ 改寫可讀性未驗／字形混排 → 已加 zh_cn 第二版字表＋golden（§E）＋zh_tw fixture（§F）＋混排斷言。
  - 中立裁判：**`v4_adequate = true`（可開工）**，另附 3 項小修（全部已入本版）：§1 數字修正（19 條／13 條含 jargon／scrub 37＝18+19／before≠after 2）、字形混排防護、FACT 支掛載。並確認 `AskLoopState.isEmptyOrMiss` 只讀 tool-result 文本（唔讀 display prose）→ B1 喺 prose 層做**唔會**打爛 runtime miss 偵測器（R2 對 B2 嘅反對點 v4 已繞開）。
  - **結論**：R3 反方剩餘卡點全部屬「已修得」範圍，裁判明確認可 → 按上限（3 輪）**開工實作**，唔再開新一輪。
