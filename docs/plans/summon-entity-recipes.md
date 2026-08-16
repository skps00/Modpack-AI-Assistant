# Pack AI — Summon-entity JEI recipes（結果是召喚實體，不是物品）

Status: **PLAN ONLY — waiting for user 「開始」.** 本回合未改 production Java／測試／jar。  
Generated: 2026-08-16.  
Related: [accuracy-first-next-wave.md](accuracy-first-next-wave.md)（QA gate）；[four-issue-backlog.md](four-issue-backlog.md)（Gateways `entity_loot` ≠ 本題）；[tool-modifier-read.md](tool-modifier-read.md)（計畫格式）；[worldgen-lookup.md](worldgen-lookup.md)（結構／biome／礦脈＝worldgen，**≠** 本檔召喚配方）；[ask-native-tools.md](ask-native-tools.md)（**D4=A 鎖定** — 能力路徑可把同一 `otherOutputs` 包成 tool；fallback／弱模型**仍要**本檔 WP1 字串 join，否則說「无产物」。真 tool **不取代** A，**不做** option D 新卡家族）。  
**Loaders:** Forge 1.19.2 + NeoForge 1.21.1 鎖步（語意同；Forge／NFWC = 主驗收）。  
**Log:** repo-root `code_change_log.md`（先寫日誌再改碼）。  
**Version:** 實作波 **不 bump** `mod_version`（本地 0.1.13 smoke；公開上傳另見 `docs/RELEASE.md`）。

**開工門檻：** 使用者說「開始」或「開始 summon-entity」。  
**分支：** 從 **`origin/main`（0.1.13）** 開 `feature/summon-entity-recipes`。**勿**混／覆蓋未提交的 `feature/purpose-scrub-hold-y`、`feature/ask-native-tools`、`feature/worldgen-lookup`。

---

## 0. 使用者意圖（鎖定）

Playtest（NFWC Forge 1.19.2）：「some recipe is use for summoning entity, AI can't check it」。

JEI 有配方，**結果是召喚實體**（不是 ItemStack）。Ask 看不到／解釋不了 → LLM 說「无配方」或當沒產物。

**要：** Ask 能 check — JEI 若顯示 entity 輸出，留 FACT 行 + 卡（id + display，**誠實才寫**）。有召喚配方時 LLM **不准**說「无配方」。Data > guess。

**不要：** 新 widget 家族；invent entity id；改 Hold-Y cap／`firstItemInSlot`／Pass 2；bump／CF／CUA。

---

## 1. Goal / Non-goals

### Goal

JEI 配方若 **OUTPUT（或同等可見槽）是非物品實體**，Ask 仍：

1. **留卡**（已有 `RecipeExtra`／`otherOutputs` 路徑則重用）
2. **FACT 行寫出結果**（display；helper 有 registry／unique id 才寫 id）
3. **JEI dump 不寫「（无产物）」** 當 other／fluid 輸出存在
4. 有卡或 FACT 有召喚結果時，既有 `AskJeiHints` 缺席 scrub 仍生效；必要時加「有 otherOutputs ≠ 无配方」

### Non-goals

| 不做 | 原因 |
|------|------|
| 改 `firstItemInSlot` | 物品槽取樣；本洞在 **非物品** 輸出與 FACT 字串 |
| Hold-Y scan cap／Pass 2 | 無關 |
| 新 RecipeCard layout／entity widget | UI 已畫 `otherOutputs`（Mekanism gas 軟渲染） |
| 硬編碼 occultism／blood magic／graveyard／mota／Hexerei | 日誌**沒點名**本 bug 的 mod；WP0 才記真實 category |
| Tooltip-only／自繪 entity **假 parser** | 無真實 `setRecipe` 樣本則不 invent |
| Entity 當 Ask **焦點**（問 mob 本身、不是物品） | 超出最小片；WP0 若玩家問的是物品／祭壇則不做 |
| Gateways `entity_loot`／生物掉落索引 | 已有；本題是 **配方結果 = 召喚**，不是 loot |
| bump／CF／commit／CUA | 使用者禁 |

---

## 2. Investigate findings（FACT vs INFERENCE）

Bug-lookup：repo + Earth Online App `code_change_log.md` **無**「summon entity 當配方產物」✅。相近但**不同**：

- Gateways `entity_loot`（掉落表，不是召喚配方）
- NFWC「召喚祭壇」／Ritual Brazier（**物品**輸出／卡序／GUIDE）

本回合 **未改** production Java。讀碼（Forge 1.19.2；Neo `promptCardLine` 鏡像）。

### 2.1 JEI collect（已有路徑）

```
IRecipeCategory.setRecipe
  → JeiRecipeLayoutCollector
       item  → firstItemInSlot (ItemStack only)
       fluid → FluidStack
       else  → othersOnePerSlot → RecipeExtra (IIngredientHelper.getDisplayName + JeiSoftIngredients)
  → JeiRecipeCards.fromLayout
       otherOut = others(layout, OUTPUT, …)
```

| 觀察 | 標籤 | 出處 |
|------|------|------|
| 整卡丢掉只在 **物品+流體+other 全空** | FACT | `JeiRecipeCards.fromLayout` ~L664–667 |
| `otherOutputs` 已接 OUTPUT 非物品槽 | FACT | 同檔 `others()` ~L1017–1054 |
| `firstItemInSlot` 只回 ItemStack；entity-only 槽 → 物品輸出空 | FACT | `JeiRecipeLayoutCollector` ~L238–254 |
| `addTooltipCallback` **no-op**；槽 tooltip 裡的實體名 **不收** | FACT | 同檔 `SlotBuilder.addTooltipCallback` ~L481–483 |
| `others()` **只問 INPUT／OUTPUT**，不問 `RENDER_ONLY` | FACT | `fromLayout` 只呼這兩 role |
| `RecipeExtra` 有 label／amount／tint／softId；**無** registry id 欄 | FACT | `RecipeExtra.java`；`others()` 未呼 `getUniqueId`／`getResourceLocation` |
| `JeiSoftIngredients.softId` = 渲染快取，**不是** entity id | FACT | `soft-` + identityHash |
| UI 已畫 `otherOutputs` 額外槽 | FACT | `AiAssistantScreen` |

### 2.2 FACT／prompt 洞（最小片主因）

| 觀察 | 標籤 | 出處 |
|------|------|------|
| `AskService.promptCardLine`：`outs = joinStackNames(c.outputs())` **只物品** | FACT | Forge+Neo ~L501–511 |
| 物品輸出空 → 行變成 `head \| ins`，**沒有 → 結果** | FACT | 同上 `outs.isEmpty()` 分支 |
| `JeiLookup.formatRecipe`／`shortIoLine`：物品輸出空 → `ReplyLang.jeiNoOut` | FACT | `JeiLookup` ~L320、~L758–766 |
| `jei_no_out` =「（无产物）」／「(no outputs)」 | FACT | lang zh_cn／zh_tw／en_us |
| `RecipeIoSummary` 無 extra／fluid join | FACT | `RecipeIoSummary.java` |
| 測試無 entity-output／`otherOutputs` 不被剝的 fixture | FACT | `tests/` grep 空 |

**INFERENCE（症狀）：** 祭壇／儀式常有物品輸入 → **卡還在**，但 FACT 不寫召喚結果、JEI 字串寫「无产物」→ LLM 說无配方／沒結果。  
**INFERENCE（更糟）：** 實體只在 tooltip／自繪、沒 `addIngredient` → `otherOutputs` 空；無樣本則 **不 invent parser**（WP0 決定走不走 C）。

### 2.3 還缺什麼（未量測）

無 NFWC 真實「召喚配方」JEI 樣本（category uid、`setRecipe` 是否 `addIngredient` 非物品、role=OUTPUT 或 RENDER_ONLY、或只 tooltip）。  
**沒有樣本就不要寫 entity 型別 parser。** 通用 `IIngredientHelper` 顯示名／id 可以；硬編碼 mod 類名不行。

---

## 3. Options

| | 做法 | 完整度 | 風險 |
|---|------|--------|------|
| **A（建議最小片）** | 重用 `otherOutputs`：`promptCardLine` + `JeiLookup.formatRecipe`／`shortIoLine` 把 extra（+ 已有 fluid 輸出）接上輸出側。僅當物品+流體+other **全空** 才 `jeiNoOut`。可選：`others(RENDER_ONLY)` 併進 otherOut（若 WP0 證實 role）。Fixture：空物品輸出 + `RecipeExtra` 不得被剝 | 7/10 快樂路徑（JEI 已註冊 ingredient） | 不修 tooltip-only |
| **B** | A + helper 有 `getUniqueId`／`getResourceLocation` 才寫誠實 id（`entity:` 或 raw）。沒有就只 display | 8/10 同 A，id 更穩 | 反射／JEI 版本文案；禁止猜 id |
| **C** | 收 `addTooltipCallback`／自繪／mod 專用 entity 型 | 視 WP0 | **無樣本 = 不做**。假 parser 比缺席更糟 |
| **D** | 新 entity 卡／Ask 焦點改 entity | 超出 | 違反 smallest／YAGNI |

**Recommendation: A，WP0 後視情況加 B。C 預設不做。**  
**D4=A（對 ask-native-tools）：** 本檔 option A **鎖定**為 fallback 必做片。能用 function-calling 的模型可另走 `jei_lookup`／日後 `summon_recipe`／`resolve_entity`（見 [ask-native-tools.md](ask-native-tools.md)）；弱模型沒有 A 仍會說「无产物」。A 對準已證實的 FACT 字串洞，不新家族、不動 `firstItemInSlot`。不是 `AskService` REQUIREMENTS 註解的 D4=B。

---

## 4. Approach（A，開工後）

```
JEI setRecipe
  → 既有 others() → RecipeExtra otherOutputs   （不動 firstItemInSlot）
  → RecipeCard 照舊（空物品輸出 + 非空 otherOut = 合法卡）
  → promptCardLine：outs = items + fluids + extra labels
  → JeiLookup：輸出標籤同左；全空才「无产物」
  → 可選 B：helper id → FACT 一行（無 id 不編）
```

**優先序：** JEI ingredient 讀到的 display／id > 卡存在 > 絕不 LLM 腦補實體。

**雙樹：** `logic/RecipeIoSummary`（或 `promptCardLine` 薄 helper）鏡像；`JeiLookup` 各 loader 各改一處。Neo 1.21.1 JEI API 差允許，語意鎖步。

---

## 5. Work packages

### WP0 — Spike（可與「開始」同週；**先於 C**）

| | |
|--|--|
| **做啥** | NFWC 開一張「召喚實體」JEI。記 category uid／標題、OUTPUT 槽是物品蛋／非物品 ingredient／RENDER_ONLY／只 tooltip。日誌或截圖。**不修碼** 也可（若只筆記） |
| **產出** | 本檔 Appendix A 一列。仍不點名沒看到的 mod |
| **Accept** | 知道 A 夠不夠，或必須停（tooltip-only → 誠實 miss，不做 C） |

### WP1 — FACT／dump 接上 `otherOutputs`（建議實作片）

| | |
|--|--|
| **做啥** | `RecipeIoSummary` join extras（+ fluid 顯示名若一行能接）。`AskService.promptCardLine` 用它。`JeiLookup.formatRecipe`／`shortIoLine` 同。空物品輸出 + 非空 extra → 必須有 `→ <label>`，禁止 `jeiNoOut` |
| **測** | `tests/check_summon_entity_recipes.py`（或擴 `check_recipe_io_and_consume_use.py`）：fixture／字串斷言 extra 不被剝。可選 Java `-ea` 對 `promptCardLine`（headless extras） |
| **不** | `firstItemInSlot`、Hold-Y、Pass 2、新 widget |
| **Accept** | 見 §6 |

### WP1b — 誠實 id（可選，A 之後）

Helper 有 unique／resource id 才寫。沒有 = 只 display。不從 display 反推 registry。

### WP2 — NFWC 煙測

Forge jar（JDK17、`GRADLE_USER_HOME` = repo `.gradle-user-home`）、仍 **0.1.13**、`dist` + NFWC **一個** packai。Neo 若碰過 → ATM10(1)。Skip CUA。不殺 javaw。Ask 那張召喚配方：FACT 有結果、不說无配方。

---

## 6. Done when（WP1）

- [ ] `promptCardLine`：物品輸出空、`otherOutputs` 有 label → 行含該 label（`→`）
- [ ] `JeiLookup` 同等：不對「有 extra／fluid 輸出」寫「（无产物）」
- [ ] 有卡或 FACT 有召喚結果 → 不得當无配方（既有 scrub + 新 FACT）
- [ ] 無 helper id → **不**編 `minecraft:zombie` 等
- [ ] Forge+Neo 鎖步；無 pack hardcode
- [ ] 測試綠（§5 WP1）；changelog
- [ ] NFWC 煙測（WP2）；**不 bump／不 CF／不 CUA**
- [ ] 未改 Hold-Y cap、`firstItemInSlot`、Pass 2

---

## 7. Test / NFWC verify

**自動：** extra-output 不被剝（空 `outputs` + `RecipeExtra("Summoned Foo")` → catalog／prompt 含 Foo；對照「只物品」舊行為）。不改既有 `firstItemInSlot` 測試。

**手動（你；agent 不 CUA）：** NFWC `;` Ask 一張 JEI 召喚配方。過：卡在、FACT 有實體名（有 id 才寫 id）、答覆不說无配方。不過：WP0 若是 tooltip-only → 記誠實 miss，不硬修。

---

## 8. 本回合摸過的檔（revert 用）

| 檔 | 動作 |
|----|------|
| `docs/plans/summon-entity-recipes.md` | **新增**（本計畫） |
| repo `code_change_log.md` | 頂部加計畫條 |
| Earth Online App `code_change_log.md` | 頂部加對應條 |

**未改** `forge/` `neoforge/` `tests/*.py` jar。工作樹若仍在 `feature/purpose-scrub-hold-y` 髒檔，那是 **另一波**，不要當成本計畫的 diff。

---

## Appendix A — NFWC summon JEI spike（WP0 填）

| Sample | JEI category uid / title | OUTPUT 是什麼 | `addIngredient` 非物品？ | role | Ask 現況 |
|--------|--------------------------|---------------|--------------------------|------|----------|
| （未填 — 無日誌樣本） | | item egg / JEI entity type / tooltip only / 自繪 | 未知 | OUTPUT / RENDER_ONLY / ? | 不能 check |

填表前 **不要** 寫 C（tooltip parser）。
