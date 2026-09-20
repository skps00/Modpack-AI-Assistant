# Plan — 取得途徑：接入成就（inventory_changed）＋ 誠實措辭（A＋B）

- 日期：2026-09-21
- 作者：Hermes（SK 指示「a+b」）
- 狀態：**v1，待 R1 反方 review**（未改任何 code）
- 相關：`docs/plans/2026-09-19-item-info-completeness-plan.md`（P1 完整度）、`docs/plans/2026-09-20-p1-followups-plan.md`

## 1. 問題（真機實證，唔係推論）

SK 真 instance（`AI_test_NFWC_DIM`）問 `witherstormmod:withered_nether_star`（風暴之星），
trace `ask-20260921-072740-witherstormmod_withered_nether_star.jsonl`，答案只列 2 條合成並寫：

> 「本包的掉落表、釣魚、交易與腳本索引都沒有這隻星的取得路徑，所以上面兩條合成就是目前已知的來源。」

**呢句係誤導**：實際上凋靈風暴被摧毀後就會掉落（mcmod 同 mod 成就都講到明）。兩個獨立成因：

### 成因 1：掉落係 mod 程式碼實作，唔喺資料檔

拆 `witherstormmod-1.19.2-3.1.1.1.jar`：
- `data/witherstormmod/loot_tables/entities/` 只有 `sickened_creeper`／`sickened_skeleton`／`sickened_spider`／`sickened_zombie`／`withered_symbiont` —— **冇 `wither_storm`**。
- 即係掉落唔經 loot table JSON ⇒ 我哋掃 JSON 嘅取得索引**結構性睇唔到**。

### 成因 2（可修）：資料檔其實有答案，我哋冇接入

同一 jar 有 `data/witherstormmod/advancements/main/wither_storm_defeated.json`：

```json
"criteria": {"obtain_withered_nether_star": {"trigger": "minecraft:inventory_changed",
              "conditions": {"items": [{"items": ["witherstormmod:withered_nether_star"]}]}}}
```

lang（zh_cn）：標題 **「此波平，彼浪起。」**／描述 **「一劳永逸地摧毁凋灵风暴！」**；成就 id 本身叫
`wither_storm_defeated`；`icon.item` 亦係同一件物品。

⇒「拿到它 = 打敗凋靈風暴」呢件事**已存在於 JSON 同 lang**，玩家可讀、可審計。現時
`ItemConsumeUseFacts` 只認 `minecraft:consume_item`（用途側），**取得側（`inventory_changed`）冇接入**。

### 成因 3（措辭）：把「索引冇」講成「遊戲冇」

`HonestMiss.shouldPinAcquireMiss` 喺 acquire 空時 pin 一句 miss FACT，配 lang
`packai.reply.acquire_index_miss`。現時文字冇「可能由 mod 程式碼實作」嘅意思 ⇒ LLM 直接寫成
「沒有取得路徑」。

## 2. 目標

- **A**：把 `minecraft:inventory_changed` 成就當作**取得途徑 fact**（例：取得此物會完成成就「此波平，彼浪起。」／`wither_storm_defeated`），並令 acquire 非空 ⇒ 唔再誤觸 miss。
- **B**：索引真係空時，措辭改為誠實版：「本包資料檔（配方／掉落表／釣魚／交易／腳本）未見；**可能由 mod 程式碼實作**（例如擊敗特定 boss／事件）」，並加 prompt 規則禁止「遊戲冇」式結論。

## 3. 已核事實（plan 依據，全部本回合親手跑）

| # | 事實 | 證據 |
|---|---|---|
| 1 | mod 冇 `wither_storm` loot table（掉落係 code） | zip 內 `loot_tables/entities/` 清單 |
| 2 | 有 `inventory_changed` 成就指向該物品 | `advancements/main/wither_storm_defeated.json` criteria |
| 3 | 成就文字可讀（zh_cn／en_us 齊） | jar 內 `assets/witherstormmod/lang/{zh_cn,en_us}.json` |
| 4 | 現有成就掃描器只做 consume 側 | `ItemConsumeUseFacts.java:176,309`（`"minecraft:consume_item".equals(trigger)`）；`IndexAdvancement` + `indexFromJson` + `resolveText` 可複用 |
| 5 | acquire fact 入口 | `AcquireAskTool.java:57`（`env.index.acquireFactsDetailed`）、`PackIndex.java:1204/1217` |
| 6 | honest miss 入口 | `HonestMiss.shouldPinAcquireMiss`（`logic/HonestMiss.java`）＋lang `packai.reply.acquire_index_miss`（3 語齊，`check_honest_miss.py` 有閘） |
| 7 | 語系硬約束 | AGENTS.md：邏輯零 natural-language literal；玩家文字一律 lang key、缺 → en_us；新字串三語同步 |
| 8 | jar 掃描有開關 | `PackAiConfig.scanModJars()`（`JarLightIndex.routeLinesForItem` 已用同一閘） |

## 4. 設計 A：`ItemObtainAdvancementFacts`（新類，模仿 `ItemConsumeUseFacts`）

- **掃描**：`<gameDir>/mods/*.jar` 內 `data/<ns>/advancements/**.json`（zip only，**唔反編譯**）。
- **認邊啲**：criteria 內 `trigger == "minecraft:inventory_changed"`，且 `conditions.items[]`（或 item 字串）
  命中焦點 item id；同一份 JSON 有多 criteria 時逐個判。
- **emit**：一行 acquire fact（內部英文標記，唔進玩家正文）：
  `[OBTAIN_ADV] <成就標題> — <成就描述> (adv:<ns:path>)`；標題／描述經 mod lang 解析，缺就只用 en_us，
  **全缺就唔 emit**（唔准發明文字，同 `ItemConsumeUseFacts` 一致）。
- **接入**：加入 `AcquireAskTool` 取得清單（jar 途徑後、quest 前——**次序待 review 決定**）；`PackAiConfig.scanModJars()` 閘。
- **與 miss 關係**：有 advancement fact ⇒ acquire 非空 ⇒ `shouldPinAcquireMiss` 自然唔觸發（唔改 HonestMiss 邏輯）。
- **卡片**：**唔出卡**（唔係配方；避免「有卡冇文字」同孤兒卡問題）。
- **玩家可見文字**：新 lang key ×3（例 `packai.reply.label.obtain_advancement` ＝「完成成就：%s」／
  `packai.reply.obtain_advancement_hint`），由 prompt 指示 LLM 用玩家語言轉述；邏輯層零中文 literal。

## 5. 設計 B：誠實措辭

1. 改 `packai.reply.acquire_index_miss`（en_us／zh_cn／zh_tw）為：
   「本包資料檔（配方／掉落表／釣魚／交易／腳本）未見此物取得途徑；**可能由 mod 程式碼實作**
   （例如擊敗特定 boss 或觸發事件）。」
2. `packai.reply.fact_check`（#19 條）＋`llm_style` 加硬規則：索引為空時**禁止**寫成「遊戲內冇得取得／
   沒有取得路徑」；只准講「本包索引未見」＋可能由程式碼實作。
3. `check_honest_miss.py` 若斷言舊字面 → **有意識更新**（diff 要俾 SK 睇）。

## 6. 白名單（cursor 只准改呢啲；其餘一律不准）

| 檔案 | 動作 |
|---|---|
| `forge/1.19.2/src/main/java/com/skps9/packai/logic/ItemObtainAdvancementFacts.java` | 新 |
| `forge/1.19.2/src/main/java/com/skps9/packai/logic/AcquireAskTool.java` | 接入 |
| `forge/1.19.2/src/main/java/com/skps9/packai/logic/ReplyLang.java` | 如需要 key helper |
| `forge/1.19.2/src/main/resources/assets/packai/lang/{en_us,zh_cn,zh_tw}.json` | 3 語同步 |
| `forge/1.19.2/src/test/java/com/skps9/packai/logic/ItemObtainAdvancementCheck.java` | 新 harness |
| `forge/1.19.2/tmp-check.gradle` | 由 `research/gen_tmp_check.py` 重生 |
| `tests/check_honest_miss.py`＋新增 `tests/check_obtain_advancement.py` | 閘（如需要） |
| `code_change_log.md` | 記錄 |

**唔准**：`HonestMiss` 判定邏輯（只改 lang）、`RecipeEmbed`／`RecipeCard`（卡落位）、`AskEngine:826`、
consume 側 `ItemConsumeUseFacts` 行為、`neoforge/1.21.1` 樹、任何部署／commit。

## 7. 驗收標準（開工前定；完成後逐項親跑）

1. **新 harness 綠**：fixture＝真 `wither_storm_defeated.json` 內容（`indexFromJson` 風格注入）→ 斷言 emit
   `[OBTAIN_ADV]` 且含 zh 標題；**負控**（a）改 item id → 唔 emit（b）改 trigger 做 `consume_item` → 唔 emit
   （c）無 `criteria` → 唔 emit；三條負控逐條獨立紅→綠。
2. **免開遊戲端到端**：由真 mod jar 抽出 `advancements/main/{root,wither_storm_defeated}.json`＋`lang/zh_cn.json`，
   組一個 temp `gameDir/mods/test.jar`，跑 scan → 斷言有 fact（**真 artifact，非手寫 fixture**）。
3. **全套回歸**：`run*Check` 由 53 → 54 全綠；`tests/check_*.py` 124 檔**零新增紅**（`check_ask_display_leak` 為已知紅）；
   lang 三語 key 數一致；新邏輯檔 CJK literal＝0。
4. **真機**：部署去有 `witherstormmod` 嘅 instance（先沙盒；SK 真 instance 要 SK go）→ 問同一物品 →
   答案要出現「打敗／摧毀凋靈風暴」或成就標題，且**唔再**出現「沒有取得路徑」式句子；trace 檔名連答案落 artifact。
5. **誠實措辭**：對一件**真係任何索引都冇**嘅物品（例：純 code 掉落且無成就者）→ 答案要講「本包索引未見；可能由
   mod 程式碼實作」，**唔准**講「遊戲內冇得拎」。

## 8. 風險／最壞情況／還原

- 風險：誤把「凡取得此物就完成嘅成就」當成**取得途徑**（例：成就 criteria 係「背包持有」但物品其實係合成得）⇒
  答案可能多講一句但不至於錯（成就描述本身係遊戲事實）。**緩解**：措辭寫「取得此物會完成成就 X」而非「由此成就取得」；
  review 要釘實呢點。
- 最壞：新 fact 令 acquire 非空 ⇒ 唔再 pin miss，但 fact 內容空泛 ⇒ 答案變差。緩解：全缺文字時唔 emit。
- 還原：全部 code 改動喺 git 內（`git checkout --`）；部署有 `mc_mod_deploy_jar.py` backup；
  真 instance 未經 SK 唔會動。
- 成本：沙盒真機一輪 ≈ 10 條 ask ≈ 40 萬 tokens（排 DS 離峰）；harness／靜態閘零成本。

## 9. 已知限制（誠實聲明）

- 只覆蓋**有 advancement JSON** 嘅 code 掉落／事件取得；**冇成就嘅純 code 途徑**依然索引唔到（靠 B 誠實措辭交代）。
- 唔會反編譯、唔會執行遊戲碼、唔會用 mcmod 原文（版權）。
- 只測 Forge 1.19.2 主線；`neoforge/1.21.1` 唔動。
