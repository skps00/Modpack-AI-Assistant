# Plan v3 — 取得途徑（成就）＋ 玩家睇得明（A＋B＋C1＋C5＋C6）

- 日期：2026-09-21；作者：Hermes（SK「go」）
- 狀態：**v3，待 R3 反方 review**（未改任何 code）
- Review 歷史：**R1 = 2:8**（唔可開工）→ v2 → **R2 = 6:4**（唔可開工）→ v3（本版）

## 0. R1＋R2 指控 → v3 對應（逐條，唔准漏）

### 0.1 R1 已解決（R2 覆核屬實）
① recipes 解鎖型排除（5968 條中 91.4% 屬此類，實測比例可重現）｜② `requirements` 單一 group 單一元素｜③ lang 自讀（唔靠 client `I18n`）｜④ fallback 跟 `ReplyLang.tr`（同語系 → zh 兄弟 → en_us）｜⑤ 掛入 `JarLightIndex` 既有掃描＋快取（`scanModJars` 默認 false 已寫明）｜⑨ 更正「已知紅」講法（124 檔全綠）｜⑪ 驗收改 trace 斷言｜⑬ 白名單補（見 §5）

### 0.2 R2 新指控 → v3 處理

| R2 | 指控（證據） | v3 處理 |
|---|---|---|
| O1｜應修 | §2.2 過濾後仍剩 **213 條／254 item**，大量係 **craft 成就**（`goety:craft_dark_altar`、`farmersdelight:harvest_straw`）＝「合成即完成成就」，同配方 fact 重複 → 新噪音 | §2.2 加**條件 8**：若該 criteria item == 焦點 id 且本包已有 `R|` 配方覆蓋 → **skip**（合成型成就唔再加一行） |
| O2｜應修 | §2.4「唔計入 `hasNonQuestAcquirePath`」**冇機制**：`AskEngine.java:1279-1297` 純字串判斷 → A 行會令 `return true` → 反轉 `demoteQuestNarrative`（`:437-438, 448, 527`） | §2.4 改為**指定實作**：該 flag 用「未加 A 行嘅 acquire 清單」計算；§5 白名單加入 `AskEngine.java` |
| O2b｜應修 | SLIM（3 行）下 jar 行排最前、A 行排最後 ⇒ **A 行永遠被切**；§6.6 係恆真斷言 | §6 改為**明示預期行為**＋可決定斷言：purpose／SLIM 情境 A 行被切（預期）；how-to-get／OUTPUT（12 行）情境 A 行**可見** |
| O3｜**阻塞** | `A|` 撞現行解析：`JarLightIndex.java:160-163`（只收 `L|R|U`）直接丟棄、`:277-299` `formatFact` default null、`AskEngine.java:1699-1723` `infoGapLines` 冇 `A` 分支 → 而該檔唔在白名單 ⇒ §5 自相矛盾 | §2.5 明列**三處必改**（`JarLightIndex:160-163`、`:277-299`、`AskEngine.infoGapLines`）；§5 加 `AskEngine.java`（限呢兩處） |
| O4｜應修 | §3.1 **打漏真槓桿**：玩家見到嘅 miss 措辭主源係 `ReplyLang.acquireIndexMiss`（`:1210-1211`）經 `HonestMiss:109-130`、`AskEngine:1244`；`LlmClient:446-458` 只係 sources 清單 | §3.1 改為「主改 `acquire_index_miss`（3 語）」；`AcquireAskTool.toolMissNote:43-48` 為第二槓桿（**必須英文**＋保留 `do not invent`）；**刪除**把 `LlmClient:446-458` 當措辭來源嘅講法 |
| O4b｜應修 | 閘衝突：`check_tool_miss_teaching.py:84-97`（英文＋400 字內含 `do not invent`）；`check_honest_miss.py:79-89`（內部 key 要 `未索引／not indexed`＋`禁止捏造／do not invent`、**無 `%`**）；玩家 key `ask_miss_acquire_player` **禁**含 `禁止／必须／do not invent／not indexed／未索引` 且**必須**含 `不確定／Unsure` | §3.3 新增「key 分工表」＋玩家 key 禁用詞表；§5 白名單加 `tests/check_tool_miss_teaching.py` |
| O5｜應修（原句不可行） | C1：`L|blocks/x` **冇 namespace**（`lootKeyFromPath:205-215` 剝走），實測 3819 key 有 **22 個跨 ns 撞名**（`blocks/rope`→create／farmersdelight／supplementaries…）⇒「`blocks/foo` = `ns:foo`」**唔成立** | §4 C1 改：(a) **反查 item index**：path == x 且擁有 `blocks/x` 掉落表者 → 唯一才用 `{{item:ns:x}}`；(b) 多於一個 → `loot_table_generic`；(c) 正規解（route key 帶 ns）另開 plan，唔塞入本輪 |
| O5b｜應修 | C5：`KubeJsMechanicScan.extractNote:893-918` 會把 `translatable("key")` 落入 `QUOTED` 分支並取最多 2 條以 `" | "` 串連（真 trace：`active_pill.1 | active_pill.2`）；lang 檔**只有 `zh_cn.json`** | §4 C5 改：**split(" | ") 逐條解析**；zh 語系可解、其他語系 fallback 保留原 key 並標記；§6.4 斷言用「真 `item_tooltips.js` 實際被掃到嘅 key」（需先核准邊行） |
| O5c｜應修 | `lootTableObtain` 有 4 個呼叫點，§5 漏 `Plainify.java:212` | §5 加入 |
| O6｜應修 | 恆真負控：(e) `scanModJars=false` 今日已綠；(f) §6.6 亦恆真；§6.5「唔可以再出現…句」不可決定（該句係 LLM 自由生成） | §6 刪 (e)(f)；新增「A 行 SLIM 被切／OUTPUT 可見」斷言；§6.5 只斷言 **tool result／gap 行** |
| O6b｜應修 | §6.3「三語 key 數一致」冇自動閘（只人手） | §6 新增 python 閘：forge 三語 key **集合**互比（缺一即紅） |
| O7｜**阻塞** | §5 白名單漏 `AskEngine.java`（A| 分支唯一落點）＋ `check_tool_miss_teaching.py` ＋ `Plainify.java` ＋ `research/gen_tmp_check.py` | §5 全部加入並寫明限定範圍 |
| O7b｜應修 | §2.2-7「存在於本包 item index」係 **client／registry 相依**（`client/knowledge/ItemIndex.java:24-33`）⇒ 免開遊戲驗收會撞；白名單亦無 headless id 來源 | §2.2-7 改為讀 **`<gameDir>/config/packai/item-index/*.json`**（真 instance 實測存在）做 headless id 白名單 |
| 數字更正 | 反方重算：IC 命中 **5950**（非 5968）、recipes 路徑 **5437**（91.38%）；「連言」按可重現定義＝**34**（多 group IC）／**16**（單 group 多元素）——R1 嘅「23」不可重現 | 全文改用可重現數字並註明定義 |

## 1. 問題（已核實，含免責）

- 真 trace `ask-20260921-072740-…withered_nether_star`：答案把「索引冇」寫成「沒有取得路徑」。
- `witherstormmod-…jar`：`loot_tables/entities/` 無 `wither_storm`；**有** `advancements/main/wither_storm_defeated.json`（`inventory_changed` → `witherstormmod:withered_nether_star`，zh_cn 標題「此波平，彼浪起。」）。
- **免責**：jar 內冇 artifact 證明「摧毀→掉落」；該說法屬外部資料／語意推論，唔當已核事實。
- SK 真機新報：C1 `掉落表：blocks/ritual_brazier` raw path；C5 tooltip 只入 key（`note:kubejs.tooltips.active_pill.1 | .2`）⇒ 只能講「請看 Shift 提示」；C6「怎么来」含非取得途徑字句（「按 R／U 查看」）；C7 gap 面板吐 raw route code（`合成 crafting_shaped: minecraft:acacia_planks` ×8）。

## 2. 設計 A（v3 定案）

**2.1** 掛入 `JarLightIndex` 現有掃描／快取（`ensure()`、manifest／fingerprint），新 kind `A`；受 `scanModJars` 閘。

**2.2 認邊條（八條全中才 emit）**：① 路徑不含 `/advancements/recipes/`；② `parent` ≠ `minecraft:recipes/root`；③ 冇 `minecraft:recipe_unlocked` criteria／冇 `rewards.recipes`；④ 有 `display`；⑤ 有 `inventory_changed` criteria 命中焦點 id；⑥ `requirements` 恰好 1 group × 1 元素；⑦ 焦點 id 過 `PackIndex.isNoiseItemId` **且**存在於 `<gameDir>/config/packai/item-index/*.json`（headless 可用）；⑧ **（R2-O1）** 若命中 criteria item == 焦點 id 且本包已有該物嘅 `R|` 配方 → skip（避免與配方 fact 重複）。

**2.3 文字解析**：`kubejs/assets/**/lang/<code>.json` → `<jar>/assets/<ns>/lang/<code>.json` → `ReplyLang.tr` 慣例；全缺 → 唔 emit。

**2.4 接入與排序**：`PackIndex.acquireFactsDetailed` 尾（＝ `AcquireAskTool.mergeRoutes` 嘅 loose 段）；`hasNonQuestAcquirePath`（`AskEngine:1279-1297`）**用未加 A 行嘅清單**計算；SLIM 3 行下 A 行會被切（**明示預期**）。

**2.5 三處必改**：`JarLightIndex.java:160-163`（收 `A`）、`JarLightIndex.java:277-299` `formatFact`（`A` 分支）、`AskEngine.infoGapLines:1699-1723`（`A` 分支）；內部 code `A|<ns:path>`，**唔加**新 bracket token。

## 3. 設計 B（v3 定案）

**3.1 主槓桿**：`packai.reply.acquire_index_miss`（3 語，經 `ReplyLang:1210-1211`）；**第二槓桿**：`AcquireAskTool.toolMissNote:43-48`（**必須英文**＋保留 `do not invent`，見 `check_tool_miss_teaching.py:84-97`）。措辭：「本包資料檔（配方／掉落表／釣魚／交易／腳本）未見；可能由 mod 程式碼實作（例如擊敗特定 boss／事件）」。

**3.2 內部 key marker**：`acquire_index_miss` 保留 `未索引／not indexed` ＋ `禁止捏造／do not invent`，**不得含 `%`**（`check_honest_miss.py:79-89`）。

**3.3 玩家 key 分工（R2-O4b）**｜玩家要睇嘅字句一律走 `ask_miss_acquire_player` 類 key：
- **禁用詞**（玩家 key）：`禁止`、`必须`、`不要用`、`请明说`、`do not invent`、`not indexed`、`未索引`
- **必須含**：`不確定`／`Unsure`
- 內容示例：「本包資料未見此物嘅取得途徑；可能由 mod 程式碼實作（例如擊敗特定 boss）。**不確定**，請以遊戲內為準。」

## 4. C1／C5／C6（v3 定案）

- **C1**：`lootTableObtain`（4 個呼叫點：`AcquireAskTool:112`、`AskEngine:1712`、`PackIndex:1318`、`Plainify:212`）→ 先**反查 item index**（path == `x` 且擁有 `blocks/x`）：唯一 → 新 key `packai.reply.loot_table_block`（「破壞 %s 會掉落」＋`{{item:ns:x}}`）；多解 → `packai.reply.loot_table_generic`（「某個掉落表（未對應到方塊）」）。**任何情況唔露 raw path**。
- **C5**：`extractNote` 之 `" | "` 串 → **逐條 split 解析**（lang 只 `zh_cn.json`；非 zh 語系 → 保留 key 並標記）；共用 §2.3 解析器。
- **C6**：prompt 規則＋事實分類：tooltip 寫嘅**取得／充能／升級鏈** → 入「怎么来」並標「據 tooltip」；**查法類**（R／U／Shift）**禁止**入「怎么来」。
- **C7（待 SK 決定）**：gap 面板 (a) **唔俾玩家**（改寫入 trace，`AskEngine:1011` 唔再 append；**若揀 (a)，C4 自動消失**）／(b) 保留但人話化＋上限 3 行。

## 5. 白名單（v3；cursor 只准改呢啲）

| 檔案 | 動作 |
|---|---|
| `logic/JarLightIndex.java` | `:160-163` 收 `A`；`:277-299` `formatFact` `A` 分支；新掃描 |
| `logic/ItemObtainAdvancementFacts.java` | 新（八條判定＋解析） |
| `logic/PackIndex.java` | `acquireFactsDetailed` 接入／排序 |
| `logic/AskEngine.java` | **限** `infoGapLines:1699-1723`（`A` 分支）、`hasNonQuestAcquirePath:1279-1297`（排除 A 行）、`InfoCompleteness` append 呼叫點 `:1011`（只在 C7 揀 (a) 時）；**`:826` 仍然唔准郁** |
| `logic/AcquireAskTool.java` | miss 措辭、acquire 清單次序 |
| `logic/ReplyLang.java` | 新／改 key 取用 |
| `logic/KubeJsMechanicScan.java` | C5 note 解析 |
| `logic/Plainify.java` | `:212` `lootTableObtain` 呼叫點（C1） |
| `resources/assets/packai/lang/{en_us,zh_cn,zh_tw}.json` | forge 三語同步 |
| `test/java/com/skps9/packai/logic/{ItemObtainAdvancementCheck,LootLineHumanizeCheck,KubeJsTooltipTextCheck}.java` | 新 harness |
| `forge/1.19.2/tmp-check.gradle`、`research/gen_tmp_check.py` | 重生（53→56） |
| `tests/check_honest_miss.py`、`tests/check_reply_prompt_keys.py`、`tests/check_tool_miss_teaching.py` | **有意識**更新（diff 交 SK） |
| `tests/check_lang_key_parity.py`（新） | 三語 key 集合互比閘 |
| `code_change_log.md` | 記錄 |

**唔准**：`RecipeEmbed`／`RecipeCard`（卡落位）、`HonestMiss` 判定邏輯、neoforge 樹、`AskEngine:826`、部署／commit。

## 6. 驗收標準（v3）

1. **新 harness 綠**：真 `wither_storm_defeated.json`＋lang 注入 → emit 含 zh 標題。
2. **負控（6 條，逐條獨立紅→綠）**：(a) `advancements/recipes/**` 型 (b) 連言 `requirements`（`FarmersDelight craft_knife`）(c) 無 `display` (d) `root.json`（`summoned_entity`）(e) lang 全缺 → 唔 emit (f) **合成型成就**（`goety:craft_dark_altar`，已有 `R|` 配方）→ skip。**（已刪 R2 指為恆真嘅 `scanModJars=false` 同「既有行不得被擠走」）**
3. **全套回歸**：`*Check` 53 → 56 全綠；`tests/check_*.py` 124 檔零新增紅；**新增**三語 key 集合互比閘綠。
4. **真 artifact 端到端（免開遊戲）**：temp `gameDir`＋真 jar 抽出嘅 advancement／lang → 斷言；kubejs：temp `kubejs/assets/kubejs/lang/zh_cn.json`＋**真 `item_tooltips.js` 內會實際被掃到嘅 key（先核實邊行）** → 斷言解析出中文。
5. **trace 斷言（只斷 tool／gap 行）**：`acquire` tool result 與 gap 行**必須**含誠實措辭或解析後文字，且**唔可以**再出現 `blocks/<x>` raw path。**唔再斷言 LLM 正文句子**（該句係模型自由生成）。
6. **SLIM 行為斷言（取代恆真條）**：purpose／SLIM 情境 A 行**被切**；how-to-get／OUTPUT 情境 A 行**可見**；`hasNonQuestAcquirePath` 前後一致（A 行不改變 `demoteQuestNarrative` 結果）。

## 7. 風險／還原

- 風險：假 fact（§2.2 八條＋§6.2 六負控擋）；誤當「取得即完成成就」為取得途徑 → 措辭「取得此物**會完成**成就 X」。
- 最壞：A 行令 acquire 非空 ⇒ 唔再 pin miss 但內容空泛 → 「解析唔到就唔 emit」＋(f) 合成型排除。
- 還原：全部在 git；部署有 backup；真 instance 未經 SK 唔動。
- 成本：沙盒一輪 ≈ 10 ask ≈ 40 萬 tokens（DS 離峰）。

## 8. 已知限制

- 只覆蓋「有合格 advancement」嘅取得；純 code 無成就者靠 B 誠實措辭。
- 只做 Forge 1.19.2；neo 不動；唔反編譯；唔用 mcmod 原文。
- **依賴 C2／C3**（cache 版本失效／噪音過濾，另一份 plan）：本 plan 驗收要用「重建 cache 後」嘅 run，並在 HANDOFF 記錄。
