# Plan — Slice 1：玩家睇得明（B／C1／C5／C7／C8）

- 日期：2026-09-21；作者：JARVIS（SK 批准「1+3」，Slice 1 先做）
- 狀態：**v1，待反方 review**（未改任何 code）
- 範圍：**只做顯示／文字層**，唔碰 route 索引核心（`JarLightIndex` 掃描、cache 版本、8-fact 上限）—— 嗰啲係 Slice 2（A＋C2/C3），另有 plan。
- 相關：`2026-09-21-obtain-advancement-and-honest-wording.md`（v3，R1 2:8→R2 6:4→R3 6:4 停手）、`2026-09-21-route-cache-and-noise-filter.md`、`docs/research/artifacts/2026-09-21-advancement-route-spike.md`

## 1. 問題（真機實證，逐條有 trace）

| 代號 | 症狀（SK 真機） | 證據 |
|---|---|---|
| B | 索引空時答案寫成「沒有取得路徑」 | `ask-20260921-072740-…witherstormmod_withered_nether_star` |
| C1 | 玩家見到 `掉落表：blocks/ritual_brazier`（raw 檔路徑） | `ask-20260921-073004-ars_nouveau_ritual_brazier` |
| C5 | tooltip 有文字但只入 lang key → 答案講「請看遊戲內 Shift 提示」 | `ask-20260921-073427-…god_bless_full_necklace`；真文字在 `kubejs/assets/kubejs/lang/zh_cn.json` |
| C7 | gap 面板吐 raw route code（`合成 crafting_shaped: minecraft:acacia_planks` ×8）；卡仲被擠到面板下面 | `ask-20260921-074759-tetra_modular_double` |
| C8 | Tetra 零件（`[TOOL_BUILD]`）有 fact 但答案冇用，改裝版被講成「空框架合成」 | 同上 trace；`tool_build` tool result 有 head_left/right=netherite、handle=forged_beam |

## 2. SK 已定決策

- **C7 = 玩家照見，但人話化；同時寫入 log**（唔係收埋）
- **C8 併入**（零件要入答案主線；改裝版唔准用空框架合成當取得途徑）
- 測試：JARVIS 自己喺沙盒副本跑真機驗收（SK 唔需要做動作）

## 3. 設計

### 3.1 B：誠實措辭（主槓桿＝玩家見到嗰句）
- `ReplyLang.acquireIndexMiss`（`ReplyLang.java:1210-1211`，3 語）＋ `HonestMiss.java:109-130`／`AskEngine.java:1244` 呼叫鏈：內部 key 保留 `未索引／not indexed` ＋ `禁止捏造／do not invent`（`check_honest_miss.py:79-89` 要），**不得含 `%`**。
- **玩家 key**（新增／改 `ask_miss_acquire_player` 類）：**禁用** `禁止`、`必须`、`必須`、`不要用`、`请明说`、`請明說`、`do not invent`、`not indexed`、`未索引`、`render_recipe_cards`、`role=`；**必須含** `不確定`／`Unsure`。措辭：「本包資料未見此物嘅取得途徑；**可能由 mod 程式碼實作**（例如擊敗特定 boss／事件）。不確定，請以遊戲內為準。」
- 第二槓桿：`AcquireAskTool.toolMissNote`（`AcquireAskTool.java:43-48`）**必須英文**＋保留 `do not invent`（`check_tool_miss_teaching.py:84-97`）。

### 3.2 C1：掉落表 raw path → 人話
- `ReplyLang.lootTableObtain`（`:454-456`）：先解析 `blocks/<x>` → **反查包 item-index**（path == `x` 且擁有 `blocks/x` 掉落表者）：
  - 唯一（實測 2,123／2,302 = 92.2%）→ 新 lang key `packai.reply.loot_table_block`（「破壞 %s 會掉落」）＋顯示名＋`{{item:ns:x}}`
  - 多解（32 例，如 `blocks/rope`）／零解（147 例）→ `packai.reply.loot_table_generic`（「某個掉落表（未對應到方塊）」）
- **任何情況唔露 raw path**。4 個呼叫點同步：`AcquireAskTool:112`、`AskEngine:1712`、`PackIndex:1318`、`Plainify:212`。
- ⚠️ 新 key 含 `%s` ⇒ **唔可以**加入 `check_reply_prompt_keys.KEYS`（該 tuple 要求 `%s` 數 == 0）。

### 3.3 C5：kubejs tooltip lang key 解析
- `KubeJsMechanicScan.extractNote`（`:893-918`，最多 2 條 key 以 `" | "` 串連）→ **split(" | ") 逐條查 lang**：`<gameDir>/kubejs/assets/**/lang/<code>.json` → 缺 → `<jar>/assets/<ns>/lang/<code>.json` → 缺 → `ReplyLang.tr` 慣例（同語系 → zh 兄弟 → en_us）→ 全缺：保留原 key 並標記（唔准發明文字）。
- 語系現實：`kubejs/assets/kubejs/lang/` 只有 `zh_cn.json` → zh_cn 玩家可解；其他語系 fallback 保留 key（已知限制）。

### 3.4 C7：gap 面板人話化 ＋ 入 log（照 SK：仍然顯示）
- 內容人話化：`L|` → 「破壞 <顯示名> 掉落」（同 C1）；`R|`／`U|` → 「可作為材料參與合成：<配方產物顯示名>」；**剔走** self-loot 噪音（`LootForwardIndex.isTrivialBlockSelfLoot`）與非物品 id；**上限**：同類合併＋最多 3 行（超出寫「另有 N 項」）。
- **同時**寫入 trace：新 event `check.info_gap`（`AskTrace.event("check.info_gap", o -> o.addProperty("lines", …))`，API 已存在 `AskTrace.java:129-133`）。
- 插入點仍係 `AskEngine.java:1011` → `InfoCompleteness.append`（**唔郁卡落位**：`RecipeEmbed`／`RecipeCard` 保持不變；面板改為喺卡片之後插入 ⇒ 順帶解決 C4「卡被擠到面板下面」）。

### 3.5 C8：Tetra 零件 canonical 行（抄任務 canonical 機制）
- 事實源：`ToolBuildAskTool`／`AskService.mergeExtrasToolBuild:1039`／`AskEngine:358,608,1616-1631`；分類器已有：`ModularFrameStandard`（標準框架 vs 改裝版）。
- 新增 post-LLM 強制行（照 `AskJeiHints.ensureCanonicalQuestLine:263` 同款 pattern）：偵測「改裝版 build」時，玩家可見回覆**必須**含 canonical【工具】行：
  - 「【工具】這把零件＝<部件1 顯示名>、<部件2 顯示名>…；空白框架合成只提供空框架，實際在 Tetra 工作台組裝／更換部件。」
  - 缺行或改寫 → 強制貼回（deterministic；唔靠 LLM 自覺）
- 並收緊 prompt 規則：改裝版**禁止**用空白框架合成當「怎么来」（現行規則保留但無強制；本項加強制）。
- 唔郁 `AskReplyScrub` 既有 token 表（`[TOOL_BUILD]` 已在內）。

## 4. 白名單（cursor 只准改）

| 檔案 | 動作 |
|---|---|
| `logic/ReplyLang.java` | 新／改 key 取用（B、C1） |
| `logic/HonestMiss.java` | 只改字串來源／玩家 key（**判定邏輯不動**） |
| `logic/AskEngine.java` | `:1011` gap 插入位置＋`:1244` miss 文字；**`:826` 唔准郁** |
| `logic/AcquireAskTool.java` | `toolMissNote:43-48`、`:112` |
| `logic/Plainify.java` | `:212` |
| `logic/PackIndex.java` | `:1318` |
| `logic/KubeJsMechanicScan.java` | `extractNote:893-918` note 解析 |
| `logic/AskJeiHints.java` | 新增 tool-build canonical 函數（照 quest canonical 同款） |
| `logic/InfoCompleteness.java` | 人話化＋上限＋合併 |
| `logic/LootForwardIndex.java` | 只讀既有 `isTrivialBlockSelfLoot`（唔改邏輯） |
| `resources/assets/packai/lang/{en_us,zh_cn,zh_tw}.json` | forge 三語同步 |
| `test/.../{LootLineHumanizeCheck,KubeJsTooltipTextCheck,ToolBuildCanonicalCheck}.java`（新） | harness |
| `tests/check_honest_miss.py`、`tests/check_reply_prompt_keys.py`、`tests/check_tool_miss_teaching.py` | 有意識更新（diff 交 SK） |
| `forge/1.19.2/tmp-check.gradle`、`research/gen_tmp_check.py` | 重生（53→56） |
| `code_change_log.md` | 記錄 |

**唔准**：`JarLightIndex`（Slice 2）、`RecipeEmbed`／`RecipeCard`（卡落位）、`HonestMiss` 判定、`neoforge/1.21.1`、`AskEngine:826`、部署。

## 5. 驗收標準（開工前定）

1. **harness 綠**：C1 反查（唯一／多解／零解三態）＋ C5 note 解析（`active_pill.1 | active_pill.2` 逐條）＋ C8 canonical 行（缺行→強制貼回；改寫→還原）。
2. **負控**：① 玩家 key 含禁用詞 → 閘紅 ② 玩家 key 缺「不確定」→ 閘紅 ③ C1 多解（`blocks/rope`）→ 出泛用句**唔露** raw path ④ C5 lang 全缺 → 保留 key 唔發明 ⑤ C8 標準框架（非改裝）→ **唔**貼零件 canonical 行（唔可以對標準框架講「未收錄」）。
3. **回歸**：`*Check` 53 → 56 全綠；`tests/check_*.py` 124 檔零新增紅；forge 三語 key 集合一致。
4. **真 artifact 端到端（免開遊戲）**：temp gameDir（真 `item_tooltips.js` key ＋ 真 `kubejs/assets/kubejs/lang/zh_cn.json`）→ 斷言解析出中文。
5. **trace 斷言**：`acquire` tool result／gap 行唔再出現 `blocks/<x>` / `crafting_shaped:` raw；新 event `check.info_gap` 存在。
6. **真機（JARVIS 自己跑沙盒副本）**：4 條 case——`ars_nouveau:ritual_brazier`、`tetra:modular_double`（改裝）、`kubejs:god_bless_empty_necklace`、`witherstormmod:withered_nether_star`——答案要①唔講「沒有取得路徑」②顯示方塊顯示名③列出零件④tooltip 文字出中文；焦點前後零搶。

## 6. 風險／還原

- 風險：玩家 key 措辭踩閘（已列禁用詞表）；canonical 行誤對標準框架生效（負控⑤）。
- 最壞：canonical 行貼錯 → 玩家見到多餘一句（可 git revert）。
- 還原：全部在 git；沙盒 jar 由 `mc_mod_deploy_jar.py` backup。
- 成本：沙盒一輪 ≈ 4 條 ask；harness 零成本。

## 7. 已知限制

- C5 只有 zh_cn 語系可解（其他語系保留 key）。
- C1 反查 92.2% 唯一；multi／zero 走泛用句。
- 唔覆蓋 route 索引層問題（8-fact 上限、舊 cache 噪音）→ Slice 2。
