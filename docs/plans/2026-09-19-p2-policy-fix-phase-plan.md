# P2 phase plan — 「有配方卻答無法確定」政策修（SB 下界合金背包實錘）

> 母 plan：`docs/plans/2026-09-19-pack-content-awareness-full-plan.md`（§2 P2、第 37 行「P2 排前」）
> 版本聲明：**只適用 MC 1.19.2 + Forge 43.x**（`neoforge` 樹 PAUSED，唔准郁）
> 狀態：**計畫（未實作）**；本檔要過反方 review 先開工

## 1. 問題（有真 trace 證據）
玩家問「下界合金背包點嚟」時：
- **JEI 卡面**：有（`sophisticatedbackpacks:smithing_backpack_upgrade`）
- **文字**：`send.facts.jei` 逐字已含「钻石背包, 下界合金锭 → 下界合金背包」⇒ LLM **收到正確配方**
- **結果**：文字答「**无法确定**」（實錘：`ask-20260917-091302-sophisticatedbackpacks_netherite_backpack.jsonl`）

## 2. 根因（已用 artifact 推翻舊假設）
- `logic/AcquireAskTool.java:29`：`acquire` 只覆蓋 **loot／trade／quest／script** 路徑，回 `[TOOL_MISS] acquire empty ...`
- `logic/AskEngine.java:944`：`obtainFill = acquire.isEmpty() ? …`
- `logic/AskEngine.java:957`：`hasLocalFact = (acquire != null && !acquire.isEmpty()) …`
- ⇒ **合成／鍛造類配方唔被當成「本地事實」** → acquire 空空 → 政策判「無足夠事實」→ 答「无法确定」，**無視已經喺 facts 內嘅 JEI 配方**
- 舊假設（`JarLightIndex.extractIngredients` 漏收 `base/addition/template`）**已被 trace 推翻**（見母 plan §10「v3→v4」）；jar 材料 key 修**降級為次要**，要有 artifact 證明真有遺漏才做

## 3. 修法（最小 diff）
1. **事實判準擴充**：`hasLocalFact` 除 acquire 外，**加「JEI 已提供可取樣配方行」**（用現成 `AskMissFallback.extractJeiPlayerLines(jeiDump,max)`：已會濾 meta 行、只留帶 `→` 嘅配方行）
2. **「點嚟」填槽**：`obtainFill` 喺 `acquire` 空但 JEI 有配方行時，用該行（例：「锻造台：钻石背包 + 下界合金锭 → 下界合金背包」），**並保留卡片**
3. **政策唔准講「無法確定」**：只要 facts 內有可顯示配方行，禁止 denial 字串（`AskMissFallback.looksLikeDenial` 已存在，改用於此閘）
4. **來源標註**：該行要標「JEI／本包配方」而唔係「取得鏈」

## 4. 檔案白名單（實作前要 review 確認）
- `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java`（事實判準＋填槽）
- `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskMissFallback.java`（如需暴露/重用）
- `forge/1.19.2/src/main/java/com/skps9/packai/logic/AskJeiHints.java`（如需）
- **新增** `tests/check_p2_jei_fact_policy.py`（新閘）
- **唔准改**：`neoforge/**`、`JarLightIndex`（次要項另開）、lang 檔（如需新字串 → 另開 P4）

## 5. 驗收標準（可執行）
| # | 檢查 | 判準 |
|---|---|---|
| A1 | SB 背包真機（`sophisticatedbackpacks:netherite_backpack`） | 文字**含**「钻石背包 + 下界合金锭」**且冇**「无法确定」；卡照舊 |
| A2 | **誠實 miss 唔可以消失**（負控） | 冇任何配方／取得路徑嘅物品，仍然要答「未收錄／無法確定」而唔係作嘢 |
| A3 | 現有 122 閘 | ≥121 綠（+新閘）；0 新紅 |
| A4 | 49 Java 測試 | 全綠 |
| A5 | guard：`hasLocalFact` 只認「帶 `→` 嘅 JEI 配方行」 | 用兩個 fixture 證明（有配方行／純 meta 行）|

## 6. 風險／邊界
- **過度自信**：JEI 行可能係**同 id 其他變體**（Tetra 已見 20 張卡）→ 行文要保留「樣本／以 JEI 為準」字眼，**唔准**當唯一路徑
- **既有閘**：`check_ask_display_leak.py` 等以文字為輸入 → 新填槽行必須經 `AskReplyScrub.isPlayerSafeLine`
- **成本**：純政策改，唔加 LLM round（`MAX_LLM_ROUNDS` 不变）
- **還原**：本 phase 只改 3 個 Java 檔＋1 新閘 → 復原＝`git checkout` 該批檔（相關改動未 commit，**先做 `.hermes/backups/2026-09-19_p2/` 快照**）

## 7. 流程
1. 本 plan → 反方 review（≥8:2 才開工）
2. cursor 實作（白名單）
3. Hermes 親驗：compile／49 測試／122＋新閘／**負控**（改壞判準要紅）
4. **真機驗收**：用 autotest harness（沙盒）跑 A1＋A2 兩條 case（成本約 4 萬 tokens／條）
5. 通過 → 入部署清單（同 fix A 一齊放）；未過 → 唔部署
