# Plan：JEI「同物品改造配方」被全數丟棄 → 答「查唔到配方」（2026-09-14，root cause 已實測鎖定）

狀態：**cursor 已實作（待真機驗收）** — 2026-09-14 21:25

來源：SK 實機診斷（`infinity_sword` droppedSelfIO=11 useful=0）＋ plan 已批。

## 一、根因（實機診斷，證據齊）

玩家問「寰宇支配之劍」(`golden_age:infinity_sword`)，AI 答「本包 JEI 沒有任何配方」。實機 log（21:18，jar `bd53a1088769`）：

```
Pack AI JEI diag cat item=golden_age:infinity_sword role=OUTPUT
    uid=irons_spellbooks:arcane_anvil   found=11 focusOk=11 focusFail=0
    droppedSelfIO=11  droppedSpamItem=0  droppedOther=0  useful=0
Pack AI JEI diag sample … line=巫師符文鐵砧：寰宇支配之劍、[咒語卷軸] → 寰宇支配之劍（符文衝擊可…）
```

**真相**：劍本體冇「一般合成配方」；JEI 顯示嘅係 **Iron's Spellbooks 巫師鐵砧（arcane_anvil）改造配方** — 「劍 + 咒語卷軸 → 同一把劍（加符文／法術）」，共 11 條（每個卷軸一條）。

**代碼位置**：`JeiLookup.appendSection()` `:754`
```java
if (JeiFocusMatch.focusAppearsAsInputAndOutput(layout, focusStack)
        && !includeSelfRecipe(intent, category)) { continue; }   // ← 11 條全部係呢度剔走
```
`includeSelfRecipe()` (`:1220`) 設計：只有當**問題意圖**係維護類（`REPAIR`／`UPGRADE`／`BOTH`）時，同物品改造配方才顯示；`NONE`（一般「點取得」）＝一律當噪音丟。
→ 今次問題意圖係 `NONE` → 11 條全丟 → JEI dump 空 → LLM 只能答「查唔到」。**唔係 JEI 冇資料，係我哋全部過濾走。**

（附帶事實：`CATALYST` section `cats=0` 係正常——鐵砧嘅 catalyst 係鐵砧方塊本身，唔係劍。）

## 二、修法（最小、data-driven，唔加硬編碼類別清單）

**只在「section 最終 useful == 0」時做 fallback**：

1. 掃描時遇到 `selfIo` 而被剔嘅 recipe：**唔即刻丟**，改成放入 `pendingSelfIo`（每 category 上限 3 條、整體上限 3 條，用現有 `formatRecipe` 結果）
2. Category 掃完後：若 `useful == 0 && !pendingSelfIo.isEmpty()` → 輸出呢批，並加一行標記：
   「（同物品改造／升級配方）」＋原有 `input → output` 行
3. 其他情況完全唔變（有一般配方時，self-IO 照舊丟）→ **唔會令正常答案變吵**
4. `JeiRecipeCards`（卡片路徑）**唔改**（今次只影響文字 dump）；卡片已有自己嘅 `upgradeOnly` 邏輯
5. 保持所有既有 cap／排序／文字格式

**抽一個可測嘅純函數**（免 MC bootstrap）：
`static List<String> selfIoFallback(List<String> pendingSelfIo, int usefulCount, int max)` — 只有 `usefulCount == 0` 時回前 `max` 條；否則回空 list。

## 三、驗收標準
1. `compileJava compileTestJava` RC=0
2. 新 harness `JeiSelfIoFallbackCheck`：
   - case A：`usefulCount=0` + 3 條候選 → 回 ≤3 條
   - case B：`usefulCount=0` + 8 條候選 → 只回 3 條（cap）
   - case C：`usefulCount=2` → 回空 list（**negative control：有正常配方時唔准出 fallback**）
   - case D：空候選 → 回空 list
3. 33 個 harness 全綠（`runItemRefCheck` 例外：pre-existing harness gap，另開單修）
4. 真機：問「寰宇支配之劍」→ 答案要**明確講**「無一般合成；但有巫師鐵砧改造（劍＋咒語卷軸）11 條」＋至少 1 條具體 `input → output`
5. 真機 regression：問一件有正常合成配方嘅物品（例如 `minecraft:iron_pickaxe`）→ 答案唔得出現「同物品改造」噪音

## 四、風險 / 還原
- 風險：低。改動只在「原本會輸出空白」嘅情況加內容；不影響有正常配方嘅題目
- 最壞情況：某物品出現少量 upgrade 噪音 → 收窄 cap 即可（3 → 1）
- 還原：`git revert <commit>`；jar 還原用 `%TEMP%\packai_deploy_backup_20260914_2106\`

## 五、後續（另開線，唔喺今次範圍）
1. `runItemRefCheck`：加 MC registry bootstrap（`SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()`）令 harness 可獨立跑
2. KubeJS bridge：`extraIds=16 entries=97 matched=0 via=public` → item id 對唔上 extra id，仍 `mode=scan`；要查 mapping
3. Token 成本：一次 ask 4 輪 × ~10k ≈ 40k input（log 實測）→ 截短工具結果／減 round／cache
