# Pack AI — PR #19 Ask 審計合成（計畫 only）

Date: **2026-08-18**  
Branch: `bugfix/ask-dsml-leak`  
PR: [#19](https://github.com/skps00/Modpack-AI-Assistant/pull/19)  
Audit HEAD: `e5fb171`（實作前再核；後續 commit 可能已存在）  
Status: **PLAN ONLY — 本檔不實作、不刪 production 碼。**  
Loaders: Forge 1.19.2 + NeoForge 1.21.1 鎖步（Ask 邏輯 Java **byte-identical**；漂移在 JEI adapter）。  
主驗收: NFWC Forge。

---

## 來源（去重後）

| 審計 | id | 用處 |
|------|-----|------|
| Bugbot | `2d4491d0` | PR diff 引入：HIGH 空怎么来、MED FLOW 卡／info 重複／名解析擋 entity 卡 |
| Ask 邏輯 | `f4d0e7f2` | 端到端 Ask：7 條 FACT + 推論 A–I + 看起來對的 |
| 死碼 | `b80d6e3b` | 可刪 fixture；CI 缺口；永不刪後備 |

**去重：**

- Bugbot MED「名解析擋 entity 卡」＋ Ask FACT-1「手持短路 `cardsForQuestion`」＝同一閘：`collectAskRecipeCards` 只在 `out.isEmpty()` 才跑 typed lookup。兩條 repro，一處修。
- Ask FACT-7 `dump_level=INPUT/FULL` ＋ 死碼「可選拿掉 INPUT 別名」＝同一洞：canonicalize 認假 enum、`valueOf` 落 OUTPUT。修邏輯或刪別名，勿做兩次。
- Bugbot MED「JEI info 重複」≠ 死碼「勿刪 `appendJeiInfoPages`」。修＝去重呼叫；方法留給 UI `jeiBlock`。

---

## Non-goals（本計畫明確不做）

| 不做 | 原因 |
|------|------|
| HTTP stream | 與 Ask 卡／名解析無關 |
| 全量 i18n | 只補 STRIP／coreUseful 必要片語，不開翻譯專案 |
| Tinkers | 無本波證據 |
| bump / CF / Modrinth / 商店重傳 | 0.1.14 本地煙測即可 |
| 實作本檔／刪 production | 等使用者說「開始」 |
| 混 `feature/purpose-scrub-hold-y` | 髒樹；本分支勿碰 |

---

## P0 — 邏輯（先修）

雙樹鏡像：下列 `logic/` + `AskService`/`JeiTypedLookup` 路徑 forge+neo 一起改。

### P0-1 召喚／名問被手持或錯物品卡短路

**標籤：** FACT（Ask-1 ＋ Bugbot-4）  
**檔：** `client/service/AskService.java` `collectAskRecipeCards`；`client/jei/JeiTypedLookup.java` `cardsForQuestion`

**錯：** `forItem(focus)` 後只有 `out.isEmpty()` 才 `cardsForQuestion`。

- Repro A：手拿鎬問「最初的骑士怎样召唤」→ catalog 只有鎬 R/U，儀式卡不進。
- Repro B：空手名解析先模糊打到 ItemIndex 物品 → 同閘擋 entity 卡。

**修（ponytail）：** 召喚／名問時 **合併** typed 卡，不要 `isEmpty()` 才跑。勿另開卡家族。

**測：** 手持有 JEI 的工具 + 召喚問句仍出 entity 卡；空手錯物品 substring 不得擋精確 entity。

### P0-2 `jei_lookup` 忽略 tool `item` id

**標籤：** FACT（Ask-2）  
**檔：** `logic/JeiLookupAskTool.java` `run`；`logic/AskToolLoop.java` `runCall`（`QUERY_TOOLS`）

**錯：** `jei_lookup` 不在 query 集合；`item ≠ state.itemId()` → `return ""`。tool 只用 `AskToolEnv.stack`，不理 `args.itemId`。  
**Repro：** 空手 `graveyard:corruption`；DSML `recipe_lookup item=graveyard:corruption`。測試 `dsmlRecipeLookupMappedAndHop` 先把 state item 設成同 id，測不到。

**修：** `jei_lookup` 用 `args.itemId` 組 stack（或納入 QUERY_TOOLS 並真 lookup）。修測試：state item 空。

### P0-3 `hasObtainRecipes` 吃過期 `jeiSummary` → 假空怎么来

**標籤：** FACT（Bugbot HIGH）  
**檔：** `logic/AskEngine.java` `hasObtainRecipes`（~L963）＋ `ensureHowToGetBody` 呼叫點

**錯：** catalog 在且只有 `role=input` → 直接 false，忽略之後更新的 `hasRecipeGet`／`recipeGetClean`。  
**Repro：** U 卡 catalog + tool loop 已有取得散文 → 仍灌空怎么来／`obtainUnknown`。

**修：** 有 catalog 時仍認 `hasRecipeGet`／當輪 JEI 正文；或改看 loop 後 `jeiText()`，勿死抱 AskService 原始 blob。

### P0-4 名核心：短中文 + 取得套話

**標籤：** FACT（Ask-3 ＋ Ask-4；兩洞疊加）  
**檔：** `logic/AskNameResolve.java` `coreUseful`／`STRIP`／`nameCore`；`JeiTypedLookup.findExactLabel`（精確相等）

**錯：**

- `coreUseful` 要 `length() >= 4` → 「凋灵」「骑士」「末影龙」不查 JEI。
- STRIP 只剝召喚套話 → 「钻石怎么来」核心仍帶「怎么来」；剝完若剩 2 字又撞 `coreUseful`。

**修：** CJK 2+ 算有用；STRIP 加「怎么来／怎么获得／how to get／how to obtain」（最小集，非全 i18n）。

**測：** 空手「怎样召唤凋灵」；「钻石怎么来」能對 JEI 名。既有「最初的骑士」「???」仍綠。

### P0-5 空手召喚 miss 提早 return，丟掉 retrieve／LLM

**標籤：** FACT（Ask-6）  
**檔：** `logic/AskEngine.java` `ask`（~L257–274）`shouldPinSummonMiss`

**錯：** 空卡 + 空手召喚 → 直接 miss `AskResult`。`idx.retrieve` 丟棄，不 drain／不 LLM。  
**Repro：** ItemIndex／quest 有本地名、JEI 顯示名對不上（英名 vs 中問）→ 只見「本包對不上」。

**修：** pin miss 擋 **web**；本地 retrieve／guide／FACT 仍可出。勿改 skip-web 本意（擋 Cataclysm wiki）。

---

## P1 — 邏輯／UI

### P1-1 Forge `collectRole` 缺 Neo `fromSupplier`

**標籤：** FACT（Ask-5）— Ask 邏輯 Java 相同；**JEI adapter 漂移**  
**檔：** `client/jei/JeiRecipeCards.java` `collectRole`（Forge ~L217 vs Neo ~L219）

**錯：** Neo：layout 空 → `tryCrafting` → `fromSupplier`。Forge：layout 空僅 OUTPUT `tryCrafting`，否則 `continue`。  
**Repro：** 同一 entity 儀式 `setRecipe` 失敗 → ATM10 可能有卡，NFWC 空 → miss。

**修：** Forge 對齊 Neo 後備（loader API 允許的最小片）。主驗收 NFWC。

### P1-2 `dump_level=INPUT`／`FULL` 不是真 enum

**標籤：** FACT（Ask-7）  
**檔：** `logic/AskToolLoop.java` `isDumpLevel`；`logic/AskToolContext.java` `parseJeiDumpLevel`

**錯：** canonicalize 認 `FULL`/`INPUT`；`JeiDumpLevel.valueOf` 失敗 → **OUTPUT**。LLM 要「只用 U」仍拿 R+U。`FULL` 實際＝OUTPUT（fingerprint 字串僥倖分開）。

**修（二選一，勿雙做）：** 實作真 INPUT／FULL，**或** 停認假名並文件化（死碼 Phase 1 可選刪 `INPUT` 別名）。`NONE` 仍 KEEP（防禦）。

### P1-3 JEI info 進 prompt 兩次

**標籤：** FACT（Bugbot MED）  
**檔：** `client/service/AskService.java` `appendJeiInfoPages`；`client/jei/JeiLookup.java` `summarize`／`JeiInfoPages.dump`

**錯：** `summarize` 已接 info dump，Ask 路徑又 `appendJeiInfoPages`。重複 `jei_info_acquire`／`jei_info_use` 灌 FACT，對撞 duplicate 怎么来 折疊。

**修：** 一邊即可。`appendJeiInfoPages` **勿刪**（死碼：UI `jeiBlock`／tools-off）。

### P1-4 `preferHarvestStrip` 擋 FLOW JEI 1:1 繪製

**標籤：** FACT（Bugbot MED）— GUI，非 Ask 迴圈  
**檔：** `logic/RecipeCard.java` `preferHarvestStrip`；`client/gui/AiAssistantScreen.java` `jeiDrawableFitsPanel`

**錯：** FLOW 常有 `jeiLayout`、空 `placedInputs` → 退成 icon strip。

**修：** 有可見槽／已 attach layout 時不要因 `preferHarvestStrip` 拒繪。機械 crafter 的 `hasVisibleItemSlots` 留下。

### P1-5 中文「祭坛」散文 + 英文 JEI 標題 → 整組卡省略

**標籤：** INFERENCE A  
**檔：** `logic/RecipeCardAlign.java` `replyLooksSpecific`／`strongMatch`／`isGenericCraft`

**推論：** 測試卡標題是「黑暗祭坛」。英文 `Living Altar` + 正文「祭坛制成」→ strong 空 + specific → `resolveAttach` 零卡。原 bug 亂貼 Crafting；現在可能矯枉過正。

**修：** omit 前用站名 token／譯名對 category；specific 勿在零命中時清空整包。未 NFWC 實測則先加英標 fixture。

---

## P2 — 推論／測試債（勿當 P0）

皆 **INFERENCE**（Ask A 已上 P1-5）。未 NFWC 實測。

| ID | 題 | 檔 | 風險 |
|----|----|-----|------|
| B | `isGenericCraft` 不含「制成」→ 工作台「制成」可能被當機器或被 omit | `RecipeCardAlign` | 泛用 Crafting 誤殺 |
| C | `isSummonQuestion`＝句中有 `summon`/`召唤` → 「summoning crystal 怎么用」skip web／召喚 miss | `SummonRecipeLookup` | 誤傷非儀式 |
| D | PURPOSE 蓋 OBTAIN：`drainBeforeFirstLlm`／`continueAfterAsk` 跳過 | `AskEngine`／intent 三元 | 無 DSML 的 obtain 後段不跑 |
| E | HonestMiss vs PackIndex 關鍵字不同步（「取得方式／how to obtain」） | `HonestMiss`／`PackIndex` | 不 pin acquire-miss |
| F | `mentionsFocus` path 子字串（`mod:bone` 撞 skeleton） | `JeiInfoFacts` | 雜 slot 頁當 PURPOSE |
| G | `unescapeLiteralNewlines` 把路徑 `\\n` 當換行 | `AskReplyScrub` | 少見 |
| H | `card_index` 0 vs 1；`bestLineIndex` 把數字當 catalog | `RecipeCardAlign`／show_recipe_card | 錯卡 |
| I | `findExactLabel` 第一非 Item 命中即 return | `JeiTypedLookup` | 同名 entity/item 無 score |

死碼 Phase 2（CI／parity）見下，同屬 P2。

---

## 看起來對的（FACT — 勿當 bug 重寫）

- **skip-web：** `skipWebForSummon = isSummonQuestion`；有 JEI／guide 的 purpose 也 skip。擋 wiki 當主敘。
- **`???`：** 先剝套話再留標點名；單個 `?` 當句號丟。`matchScore` 標點名只 equals。
- **DSML：** `parseLeakedToolXml` + `canonicalizeCall`：`recipe_lookup`+dump-like → `jei_lookup`；否則 `show_recipe_card`。`INFORMATION`→`INFO`。
- **role:tool：** `applyNativeCalls`；空結果 `[TOOL_MISS]`。
- **auto\|force\|off：** off 不送 schema；force 仍送；HTTP 400 → `askNoTools`。
- **INFO classify：** output slots 優先，再 `isCarryToGet` + `mentionsFocusAsCarried`。
- **R vs U：** catalog `role=output\|input\|quest`。有 catalog 時 INPUT 不當怎么来（P0-3 是過期 blob，不是這條規則錯）。
- **ensureHowToGetBody：** 填空 → 插標題 → 調到「作为材料」前 → orphan `2.` → `collapseDuplicateHowToGet`。`unescapeLiteralNewlines` 跳過 `[[…]]`。`proseOrFacts` 刮光 → FACT。
- **制成／祭坛（中文 JEI 標題）：** `SPECIFIC_PROSE` + `anyMachine` 丟 generic Crafting。英標題見 P1-5。
- **空手 miss 非空白：** summon miss 文案 + `ReplySources.ensure`。
- **雙樹 Ask 邏輯 Java 相同。** 漂移：`JeiRecipeCards`（P1-1）、`JeiLookup`、Neo `AskService` GuideME。

---

## 死碼階段（本檔仍不刪；開工後才動）

### Phase 1 — 現在可安全刪（fixture／一行）

| 項 | 路徑 | 信心 |
|----|------|------|
| 未引用 Tetra schematic | `tests/fixtures/tetra/schematics/sword/wu.json` | 高 |
| 同上 | `tests/fixtures/tetra/schematics/sword/wu_hilt.json` | 高 |
| 可選：`isDumpLevel()` 去掉 `"INPUT"` | `AskToolLoop.java` forge+neo | 中 — **先對齊 P1-2**，勿只刪別名卻讓 LLM 仍傳 INPUT |

### Phase 2 — 補測後再動（非刪功能）

1. `JeiInfoFactsCheck`／`AskReplyScrubCheck` 接進 python CI（`tests/check_*.py` 或 gradle `-ea`）。
2. Neo-only `*Check.java`：forge 鏡像或 python wrapper；再考慮整併 `RoadmapChecks`。
3. Forge `AskService.purposeGuideFor` vs Neo GuideME：**對齊行為**，不刪 Neo 側。
4. `JeiDumpLevel.NONE`：確認永不傳入再簡化（低優先；先 KEEP）。

### Phase 3 — 永不刪

見下一節。

---

## 永不刪（KEEP）

| 保留 | 原因 |
|------|------|
| 全部 `*AskTool` + `registerAskTools`／`CAPABLE_TOOLS`／`FIRST_ROUND_TOOLS` | 全活躍；註解「FACT pin when tools off」是後備 |
| `noteShot0` + pre-inject FACT（含 acquire） | tools-off／fingerprint／skip LLM／miss pin |
| DSML／`<tool_call>` parse + scrub | 本 PR 核心；非 OpenAI 端點仍吐 |
| `[[tools]]` JSON marker | URL 不支援 tools／400 fallback |
| `local_acquire_header`（【本地获取】）FACT + display scrub 雙層 | tools-off 後備；scrub 剝顯示，不刪 key |
| `JeiInfoFacts`／`JeiInfoPages`／INFO dump | PR 功能，非死碼 |
| `AskToolEnv`／`ToolChatTurn`／`LlmRound` | JSON／native tools 序列化 |
| lang keys（`check_reply_prompt_keys.py` 鎖定） | 執行期 `ReplyLang.tr()` |

---

## 建議實作序（開工後）

1. P0-1 卡合併閘  
2. P0-2 `jei_lookup` item  
3. P0-4 名核心（短中文 + 取得 STRIP）  
4. P0-3 過期 `hasObtainRecipes`  
5. P0-5 miss 勿丟 retrieve  
6. P1-1 Forge `fromSupplier`（NFWC）  
7. P1-2 dump_level  
8. P1-3 info 去重  
9. P1-4 FLOW 繪製  
10. P1-5 英標題（有 fixture 再動）  
11. Phase 1 fixture；Phase 2 CI  

P2 推論 B–I：有 repro 再升；無則不改。

---

## 驗收（實作波；本檔不跑）

- 相關 `tests/check_*.py` + forge `-ea` Check 連續兩次綠。  
- 新測至少覆蓋：手持鎬+召喚；`jei_lookup` 且 state item 空；「凋灵」；「钻石怎么来」；input-only catalog + `recipeGetClean`；可選 FLOW 卡非 strip。  
- Forge+Neo 語意鎖步。  
- 不 bump。jar→dist→NFWC 單一 packai jar。CUA 另叫。

---

## 未測／限制

- 三審計皆靜態碼；**未**跑 NFWC／CUA。  
- Ask 邏輯審計時 CodeGraph 曾指錯過主 worktree，後在本 worktree 建索引。  
- 死碼審計 CodeGraph 主樹；改 grep。高信心可刪僅 2 fixture。  
- P1-5、P2 皆推論，可能誤報。
