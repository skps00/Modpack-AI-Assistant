# Plan v2 — 取得途徑：接入成就 ＋ 玩家睇得明（A＋B＋C1＋C5＋C6）

- 日期：2026-09-21；作者：Hermes（SK「go」）
- 狀態：**v2，待 R2 反方 review**（未改任何 code）
- v1 → v2 變更理由：R1 反方 review 得 **正方 2 : 反方 8**（唔可開工）。本版逐條吸收 R1 指控，並加入 SK 新要求 C1／C5／C6。

## 0. R1 指控 → v2 處理對照（逐條，唔准漏）

| R1 | 指控 | v2 處理 |
|---|---|---|
| ① | `inventory_changed` 命中 5968 條，**91.4% 係 `advancements/recipes/**` 配方解鎖型**（無 display、criteria 係材料持有） | §2.2 明文排除：路徑含 `/advancements/recipes/`、`parent=minecraft:recipes/root`、criteria trigger `minecraft:recipe_unlocked`、有 `rewards.recipes` → **一律唔 emit**；**必須有 `display`** |
| ② | 唔讀 `requirements` ⇒「取得此物會完成成就」係假陳述（連言 group 實測 23 條） | §2.2 只認 `requirements` **恰好一個 group、且該 group 恰好一個元素**（即單一 criterion）；否則唔 emit |
| ③ | §7.1 headless 必紅：`resolveText` 靠 client `I18n` | §2.3 另寫「由 jar／pack 讀 lang」解析器（唔用 client `I18n`）；§6 驗收 fixture 帶 lang 檔 |
| ④ | lang fallback 同 repo 慣例相反 | §2.3 跟 `ReplyLang.tr` 前例：同語系 → zh_tw↔zh_cn → en_us |
| ⑤ | 掃描成本／快取冇設計；`JarLightIndex` 已有 manifest＋fingerprint＋`ensure()` | §2.1 **唔另起全 jar 走訪**，掛入 `JarLightIndex` 現有掃描與快取；一併註明 `scanModJars` **默認 false**（`PackAiConfig.java:447`） |
| ⑥ | 靜默改變已驗收行為（`hasNonQuestAcquirePath`→`demoteQuestNarrative`；`clipAcquireLines` SLIM 3 行上限） | §2.4 新 fact **排最尾**、**唔計入非任務取得路徑**；§6 加「既有 jar／worldgen 行不得被擠走」斷言 |
| ⑦ | B 打錯靶：live miss 字句係 `AcquireAskTool.java:44-46`／`LlmClient.java:446-458` 硬編，唔係 lang key | §3.1 改 live 措辭源；§5 白名單加入兩檔 |
| ⑧ | 新 marker `[OBTAIN_ADV]` 唔在 scrub 清單；加就要動 neoforge lang | §2.5 **唔引入新 bracket marker**：內部 route code 用 `A|<ns:path>`（同 `L|`／`U|` 同款），玩家文字走 `ReplyLang` lang key ⇒ 唔觸 `AskReplyScrub`／唔動 neoforge |
| ⑨ | §7.3「`check_ask_display_leak` 已知紅」係錯（實測 124/124 綠） | §6.3 更正為「124 檔全綠，零新增紅」 |
| ⑩ | 三條負控「唔做都會綠」 | §6.2 換成 7 條真風險負控（見下） |
| ⑪ | §7.5 靠 LLM 輸出斷言（非決定性） | §6.5 改為 **trace／tool result 斷言** |
| ⑬ | 白名單缺 `PackIndex`／`JarLightIndex`（鎖死正確落點） | §5 加入 |
| ⑭ | 缺 `check_reply_prompt_keys.py`；新字面撞語意 marker 閘 | §3.2 新字面**保留** `未索引`／`not indexed` ＋ `禁止捏造`／`do not invent` 兩個 marker；§5 加白名單 |
| ⑮ | 缺 `LlmClient.java`／`AskReplyScrub.java`／neo lang | §5 已列（neo lang **不動**，因為 §2.5 冇新 token） |
| E | 「摧毀→掉落」係未核推論 | §1 改寫：只寫「loot table 冇 + 成就文字提 defeated」；mcmod 僅外部對照，唔當證據 |
| — | `root.json` 用 `minecraft:summoned_entity` | §6.2 加負控 |

## 1. 問題（只列已核實）

- 真 instance trace `ask-20260921-072740-witherstormmod_withered_nether_star`：答案把「索引冇」寫成「沒有取得路徑」。
- `witherstormmod-1.19.2-3.1.1.1.jar`：`loot_tables/entities/` 無 `wither_storm`（實核 6 個 entry）；**有** `advancements/main/wither_storm_defeated.json`（`minecraft:inventory_changed` → `witherstormmod:withered_nether_star`，zh_cn 標題「此波平，彼浪起。」／描述「一劳永逸地摧毁凋灵风暴！」）。
- 免責：jar 內**冇**任何 artifact 證明「摧毀→掉落」；該結論屬外部資料（mcmod）＋成就文字語意，**唔當已核事實**。
- SK 新報（09-21 真機截圖＋trace）：
  - **C1** `acquire` 工具結果同 gap 面板出現 `掉落表：blocks/ritual_brazier`（raw 檔路徑）。
  - **C5** `kubejs:god_bless_*` 嘅 tooltip 文字只以 **lang key** 形式入 fact（`note:kubejs.tooltips.active_pill.1 | .2`；`KubeJsMechanicScan.java:1050`），全 code **零** `kubejs.tooltips` 解析器 ⇒ 答案只能講「請看遊戲內 Shift 提示」。實際文字在 `kubejs/assets/kubejs/lang/zh_cn.json`＝「击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能」。
  - **C6** 「怎么来」段出現非取得途徑字句（「按 R／U 查看」；tooltip 講嘅充能鏈則完全冇出）。

## 2. 設計 A：取得途徑接入 `inventory_changed` 成就

**2.1 掃描落點**：掛入 `JarLightIndex`（現有 jar data 掃描＋manifest／fingerprint 快取＋`ensure()` 生命週期），新 route kind `A`；受 `PackAiConfig.scanModJars()` 閘（默認 false，驗收要用實例已開＝true 嘅前提寫明）。

**2.2 認邊條（全部條件要中，缺一即 skip）**
1. 路徑**不**含 `/advancements/recipes/`；
2. `parent` ≠ `minecraft:recipes/root`；
3. 冇 criteria trigger `minecraft:recipe_unlocked`、冇 `rewards.recipes`；
4. **有 `display`**（title 或 description 至少一個）；
5. 有 ≥1 criteria `trigger == minecraft:inventory_changed` 且 item 命中焦點 id；
6. `requirements` **恰好一個 group 且該 group 恰好一個元素**，且該元素就係命中 criteria（連言 → 唔 emit）；
7. 焦點 id 過 `PackIndex.isNoiseItemId` 且**存在於本包 item index**（擋 loot function／condition id：例如 `minecraft:survives_explosion`）。

**2.3 文字解析（新 helper，唔靠 client）**
- 次序：`<gameDir>/kubejs/assets/**/lang/<code>.json`（C5 共用）→ `<jar>/assets/<ns>/lang/<code>.json` → 同 `ReplyLang.tr` 慣例（同語系 → zh_tw↔zh_cn → en_us）。
- 全部搵唔到文字 → **唔 emit**（唔准發明）。

**2.4 接入與排序**
- `acquireFactsDetailed`（`PackIndex.java:1217`）內**排最尾**；**唔計入** `hasNonQuestAcquirePath`（`AskEngine.java:1279-1296`）以避免 `demoteQuestNarrative` 行為改變；SLIM `clipAcquireLines` 3 行上限下**不得擠走**既有 jar／worldgen 行（§6 驗收斷言）。

**2.5 標記與玩家文字**
- 內部 route code：`A|<ns:path>`（同 `L|` 同款，**唔加**新 bracket token ⇒ 不動 `AskReplyScrub`／neo lang）。
- 玩家文字：新 lang key（forge 三語同步）`packai.reply.obtain_advancement`（例：「完成成就：%s」＋「（取得此物）」）；顯示用 `{{item:}}` 標記（若 fact 已帶）。

## 3. 設計 B：誠實措辭（改 live 來源，唔係改 lang key）

- **3.1**：改 `AcquireAskTool.toolMissNote`（`AcquireAskTool.java:44-46`）＋ `LlmClient.java:446-458` sources hint —— 索引為空時**禁止**寫成「遊戲內冇取得／沒有取得路徑」；只准「本包資料檔（配方／掉落表／釣魚／交易／腳本）**未見**；**可能由 mod 程式碼實作**（例如擊敗特定 boss／事件）」。
- **3.2**：新字面必須**保留**兩個語意 marker：`未索引`／`not indexed`、`禁止捏造`／`do not invent`（`check_honest_miss.py:82-89`、`check_reply_prompt_keys.py:18,50-53`）。同 `packai.reply.acquire_index_miss`（3 語）同步更新。

## 4. C1／C5／C6

- **C1（raw path → 人話）**：`ReplyLang.lootTableObtain` 改為先解析 `blocks/<x>` → `{{item:<ns>:<x>}}` ＋ 新 lang key `packai.reply.loot_table_block`（「破壞 %s 會掉落」）；解析唔到 → `packai.reply.loot_table_generic`（「某個掉落表（未對應到方塊）」）——**任何情況唔露 raw path**。
- **C5（kubejs tooltip key 解析）**：`KubeJsMechanicScan` 嘅 `note:` 改為「key → 解析後文字（解析唔到就保留原 key 並標記）」；解析器同 §2.3 共用。
- **C6（分段純度）**：prompt 規則＋fact 分類：
  - tooltip 明寫嘅**取得／充能／升級鏈** → 入「怎么来」，開頭標「據 tooltip」（低信心，已有 lang 規則 19）；
  - 「按 R／U 查看」等**查法**字句 → **禁止**入「怎么来」。

## 5. 白名單（cursor 只准改呢啲）

| 檔案 | 動作 |
|---|---|
| `logic/JarLightIndex.java` | 新 route kind `A` ＋掃描 |
| `logic/ItemObtainAdvancementFacts.java` | 新（解析／判定） |
| `logic/PackIndex.java` | `acquireFactsDetailed` 接入＋排序 |
| `logic/AcquireAskTool.java` | miss 措辭＋acquire 清單 |
| `logic/LlmClient.java` | sources hint 措辭 |
| `logic/KubeJsMechanicScan.java` | C5 note 解析 |
| `logic/ReplyLang.java` | 新 key 取用（C1／A 玩家文字） |
| `resources/assets/packai/lang/{en_us,zh_cn,zh_tw}.json` | forge 三語同步 |
| `test/java/com/skps9/packai/logic/{ItemObtainAdvancementCheck, LootLineHumanizeCheck, KubeJsTooltipTextCheck}.java` | 新 harness |
| `forge/1.19.2/tmp-check.gradle` | 由 `research/gen_tmp_check.py` 重生 |
| `tests/check_honest_miss.py`、`tests/check_reply_prompt_keys.py` | **有意識**更新（diff 要交 SK 睇） |
| `code_change_log.md` | 記錄 |

**唔准**：`RecipeEmbed`／`RecipeCard`（卡落位）、`HonestMiss` 判定邏輯、`AskEngine:826`、neoforge 樹、部署／commit。

## 6. 驗收標準（開工前定；完成後逐項親跑）

1. **新 harness 綠**：真 `wither_storm_defeated.json` 內容注入 → 斷言 emit 且含 zh 標題（fixture 同時帶 lang 檔）。
2. **負控 7 條（逐條獨立紅→綠）**：(a) recipes 解鎖型（`advancements/recipes/**`）(b) 連言 `requirements`（FarmersDelight `craft_knife`）(c) 無 `display` (d) `root.json`（`minecraft:summoned_entity`）(e) `scanModJars=false` (f) lang 全缺 → 唔 emit（g) 命中 id 唔在 item index（`minecraft:survives_explosion` 類）。
3. **全套回歸**：`*Check` 由 53 → 56 全綠；`tests/check_*.py` **124 檔全綠、零新增紅**（R1 已澄清無「已知紅」）；forge 三語 key 數一致（neo 不動）。
4. **真 artifact 端到端（免開遊戲）**：temp `gameDir` + 由真 jar 抽出嘅 `advancements/main/{root,wither_storm_defeated}.json` ＋ mod lang → 斷言 emit；kubejs：temp `kubejs/assets/kubejs/lang/zh_cn.json` ＋真 `item_tooltips.js` 嘅 key → 斷言解析出「擊敗虛空之花…即可充能」。
5. **trace 斷言（唔靠 LLM 措辭）**：真機一輪後，`acquire` tool result／gap 行**必須**含誠實措辭或解析後文字，且**唔可以**再出現 `blocks/<x>` raw path 或「沒有取得路徑」式句子。
6. **不得擠走既有行**：harness 斷言既有 `L|`／worldgen 行在新 fact 加入後仍存在（SLIM 3 行上限情境）。

## 7. 風險／還原

- 風險：91% 假 fact（已由 §2.2 排除）；誤把「取得即完成成就」當取得途徑 → 措辭寫「取得此物**會完成**成就 X」而非「由此成就取得」。
- 最壞：新 fact 令 acquire 非空 ⇒ 唔再 pin miss 但內容空泛 → 靠「解析唔到就唔 emit」擋。
- 還原：全部改動在 git；部署有 `mc_mod_deploy_jar.py` backup；真 instance 未經 SK 唔動。
- 成本：沙盒一輪 ≈ 10 ask ≈ 40 萬 tokens（排 DS 離峰）；靜態閘零成本。

## 8. 已知限制（誠實）

- 只覆蓋**有合格 advancement JSON** 嘅取得；純 code 且無成就者仍靠 B 誠實措辭。
- 唔反編譯、唔執行遊戲碼、唔用 mcmod 原文。
- 只做 Forge 1.19.2；`neoforge/1.21.1` 不動。
- **依賴**：C2（cache 版本失效）未修前，你部機 cache 仍可能帶舊噪音 route ⇒ 本 plan 驗收要用「清 cache 後」嘅 run，並在 HANDOFF 記錄。
