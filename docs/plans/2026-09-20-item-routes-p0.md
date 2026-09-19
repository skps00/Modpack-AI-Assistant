# Plan v6（P0，收窄版）：把 pack 索引嘅「取得途徑」接到玩家答案

- 日期：2026-09-20（凌晨）／作者：Hermes／狀態：**已由 SK 批（1a／2a＋go）**，待 cursor 實作
- 版本語境：MC **1.19.2**／Forge 43.4.5／packai `0.2.3`／實測 pack＝**FTB Skies Expert**（357 mods）
- 上位 plan（已停手、P1 內容留喺度）：`docs/plans/2026-09-19-item-info-completeness-plan.md`（R1 2:8／R2 3:7／R3 4:6／R4 5:5／R5 4:6；**R5 = option A 拆細**）
- **SK 決定（寫死）**：**1a** 維度只在**可唯一推導**時講、否則唔講；**2a** 挖方塊掉落**要講**

## 一句話問題
pack 索引**有**取得途徑資料（例：`ad_astra:oxygen_tank` → `L|chests/village/moon/blacksmith`），但佢去唔到 AI 眼前 ⇒ 答案會講**錯**：「No loot, chest, trade or fishing path ... indexed」（實測 19/19 之一）。

## 根因（全部已親核，逐條附證據）
1. `AskEngine.java:826`：`promptFacts = capable ? List.of() : factsLive` ⇒ **工具可用時 facts 牆清空**；實測 `latest.log` **76/76** `toolsOffered=true`、19/19 trace 有 tool.call ⇒ 全部走 capable 路徑
2. `acquire`（唯一「取得途徑」工具）**16/19 空**（`AcquireAskTool.java:42-43`）；佢只讀 loose datapack（`PackIndex` grep jar/ZipFile = **0**）
3. jar-cache 本身好齊：`JarLightIndex.byItem`（`:62` `ConcurrentHashMap<String,List<String>>`，`loadAllShards:394-433`／`scanMods:287-311`）；`formatFact:249-271` 已有 `L|` → `ReplyLang.jarLoot` 分支 ⇒ **零 jar 重掃**可取
4. 上限（寫死，唔准假設全量）：scan time `MAX_FACTS_PER_ITEM=8`（`:279,:439`）⇒ raw 5,160 條 L refs 只有 **2,418（46.9%）**入到記憶體；`MAX_ASK_LINES=4`（`:59,:128`）；`acquireLineBudget` **動態**（`AskToolContext.java:115-122`：craft／acquire 類＝12，其餘＝3）；`clipLines:125-143` **保留最前 N**

## 設計
### D1（接線）
- `logic/JarLightIndex.java` 新增 `public List<String> routeLinesForItem(String itemId)`：讀 `byItem`，回 `L|`／`R|`／`U|` 原碼（**零 jar 重掃**）
- `logic/AcquireAskTool.java`：喺 `:42` `clipAcquireLines(...)` **之前** merge（**jar refs 優先**、loose 其後；去重；排序穩定）
- 前提（寫死）：`manifest.json` 要存在（`:395,:407`）；`mods/` 目錄要存在（`:77-80`）；`PackAiConfig.scanModJars()==true`（**預設 false**，`PackAiConfig.java:442-447`；沙盒 toml `config/packai-client.toml:140` 已 true）⇒ **真機前必先驗 = true**
- **唔准**改 `AskEngine.java:826` 語意；**唔准**新 config key；**唔准**改 prompt 文案

### D2（過濾：只丟「冇內容」嘅，唔准丟整類）
- 丟：`L|empty`、`L|loot`、`L|artifact`(裸 key)、`L|chest/example_random_source_loot_table`、`L|items/drinking_hat`、`L|entity/treasure_goblin`、`L|advancements/shader_epic`（逐條寫死；全部係「冇 table path 或無意義」嘅 key）
- **保留**：`chests/*`、`inject/chests/*`、`entities/*`、`blocks/*`（非自身掉落；scan time 已由 `LootForwardIndex.isTrivialBlockSelfLoot:106-120`／`JarLightIndex:242` 過濾）、`gameplay/*`、`actions/*`、`archaeology/*`、`extractor/*`、`spoils/*`、`forged/*`、`misc/*`、`custom/*`、`entity/*`、`elementalcraft/*`、`advancements/*`、`chest/*`、`items/*`、`artifact`(35，屬 artifacts 系列)
- **明文**：唔准為「減少噪音」而丟棄有實質 table path 嘅資料（R5 指舊版 deny-list 會令 6 件物品，含 `minecraft:stone`，變返「冇途徑」＝原本缺陷翻生）

### D3（人化）
- 用**已存在** lang key `packai.reply.loot_table_obtain`（`en_us.json:472`，值 `Loot table: %s`）＋ 三語同步；**唔准**自創新玩家文字
- 玩家可見文字**唔准**含檔名／行號（`PackIndex.java:1284` 明文）

### D4（回覆格式＝五格；**唔新增 prompt 文案**，只做驗收期望）
1. 一句（官方顯示名＋係咩）2. 點嚟（逐條一行，帶來源標記：合成／挖方塊／箱／生物／任務／交易）3. 點用 4. 注意（tooltip 限制）5. 來源行（**永遠最後**）
- **冇提及 ≠ 否定**：冇資料就唔提嗰格；**只有真係查過**才可以講「本 pack 未收錄」
- 有料但被截（`MAX_FACTS_PER_ITEM=8`／clipLines）⇒ 要老實標「另有 N 條未顯示」

## 白名單（只准改呢啲）
1. `logic/JarLightIndex.java`
2. `logic/AcquireAskTool.java`
3. 新 harness `src/test/java/com/skps9/packai/logic/AcquireJarRoutesCheck.java`
4. `research/gen_tmp_check.py`（重生 `tmp-check.gradle`；該檔 untracked，**唔准 add**）
5. `assets/packai/lang/{en_us,zh_cn,zh_tw}.json`（**只有**需要新 key 時；優先用現有 key）
6. `code_change_log.md`
- **唔准郁**：`AskEngine.java`（尤其 `:826`）／`RecipeEmbed`／`RecipeCard`（卡落位，A3 要 sha256 零改動）／`PackAiConfig.java`／`neoforge/`

## 驗收
| # | 項目 | 標準 | 負控 |
|---|---|---|---|
| A0 | headless（**capable 模式**，bind `AskToolEnv`；ctor 5 參數含 `ItemStack`，`AskToolLoop.bindEnv` 係 ThreadLocal `:78/:103`；要 `PackAiConfig.setScanModJars(true)`（setter `:923` 會 `SPEC.save()`）＋ MC bootstrap（`SharedConstants`／`Bootstrap`，先例 `ItemRefCheck.java:14-15`）；fixture 需 `manifest.json`＋`mods/`） | `ad_astra:oxygen_tank` 嘅 `acquire` 回傳文字**逐字含** `chests/village/moon/blacksmith`；`artifacts:crystal_heart` 含 `inject/chests/end_city_treasure`；`tetra:dragon_sinew` 含 `entities/ender_dragon_extended` | `routeLinesForItem` 回空 ⇒ 必紅 |
| A1 | 真機 FTB | 氧氣罐答案講到**掉落表**；**唔准**出現「no loot … indexed」 | — |
| A2 | 真機 FTB（隨機抽，每次重抽） | 5 件礦物：必出「**挖方塊** → 掉落物」；**肯定時**才出維度（1a） | — |
| A3 | 全回歸 | forge **50/50**（新 harness 由 `gen_tmp_check.py` 自動註冊）；python 閘＝baseline **122 綠＋1 已知**；`RecipeEmbed`／`RecipeCard` sha256 零改動 | — |
| A4 | 交付路徑一致性 | A0 用**記憶體內可見**範圍（非 raw 全量）做斷言；報告要寫「可達 vs raw」兩個數 | — |

## 還原
- 改前逐檔備份（`%TEMP%\v6_backup_<ts>\`）＋ sha256 baseline；還原＝`git checkout -- <白名單檔>`（**禁**裸 `git checkout -- .`：工作樹 127 項 dirty 唔係本 plan 造成）＋重生 `tmp-check.gradle`
- 部署／commit：**真機驗收通過先**（SK 規則）；實作者唔准 commit／deploy／開遊戲
