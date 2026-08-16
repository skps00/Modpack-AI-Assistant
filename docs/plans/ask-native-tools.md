# Pack AI — Ask native tools（真 function-calling + 舊路徑並存）

Status: **WP1 碼綠**（`feature/ask-native-tools` ← `origin/main` 099b3f5）。未 commit。  
Generated: 2026-08-16. Revised: 2026-08-16 14:38（inventory 齊 + nowadays harness + D5 superseded）。  
Related: [ask-tool-loop-harness.md](ask-tool-loop-harness.md)（0.1.10 Hybrid loop）；[ask-tool-context-b.md](ask-tool-context-b.md)；[summon-entity-recipes.md](summon-entity-recipes.md)（**D4=A 鎖定** — fallback 仍要字串 join）；[worldgen-lookup.md](worldgen-lookup.md)；[accuracy-first-next-wave.md](accuracy-first-next-wave.md)。  
**Loaders:** Forge 1.19.2 + NeoForge 1.21.1 鎖步。Forge／NFWC = 主驗收。  
**Log:** repo-root `code_change_log.md`（先寫日誌再改碼）。  
**Version:** 實作波 **不 bump** `mod_version`（本地 0.1.13 smoke）。

**分支：** 從 **`origin/main`（0.1.13）** 開 `feature/ask-native-tools`。**勿**混 `feature/summon-entity-recipes`、`feature/worldgen-lookup`、`feature/purpose-scrub-hold-y`。

**D5 superseded：** four-issue D5（「只加 pin／dump、不當 tool」）**作廢**。凡使用者看得到的能力＝**具名 tool + 保留今日 fallback**。D4=A（summon `otherOutputs` 字串 join）**仍要** — 真 tool 救不了弱模型。

---

## 0. 使用者意圖（鎖定）

「I want to change everything to 真 tool… don't remove current's code, since there still have some AI can't use tools」  
後續：「base on nowadays harness, to improve ours」— Pack AI = **Minecraft Ask harness**（不是 Hermes 模型）。抄 **pattern**，不抄各家 proprietary prompt。

**要：**

| 模型 | 行為 |
|------|------|
| **能** function-calling | 真 OpenAI-compat `tools`／`tool_calls`；每輪可帶 schema；`tool_calls`→本機執行→再 round |
| **不能** | **今日路徑原樣：** 預收集卡 + `[[recipe:]]`／`{{RECIPE}}`／`[[recipe_card:N]]` + FACT dump |

**不要：** 刪 marker／card-sandwich／`promptCardLine`／`RecipeEmbed`／`AskMarkerRepair`；兩個產品；world-write tools；bump／CF／CUA。

---

## 1. Goal / Non-goals

### Goal

一條 Ask 管線、兩個執行模式（自動切，玩家無感）：

1. **能力路徑：** 每個 LLM turn 可帶 `tools` schema（WP1＝既有五名）。`tool_calls` → `AskTool.run` → 再 round。空 `tool_calls` + 正文＝合法終答。
2. **Fallback 路徑：** 與今日相同（shot-0 FACT + 卡目錄 + marker sandwich + drain／escalate／`[[tools]]` JSON hop）。
3. Fallback **自動**（400 probe／已知劣 URL／config `off`）。
4. 新 lookup **能力＝tool**；fallback 仍要字串 FACT。

### Non-goals

| 不做 | 原因 |
|------|------|
| 刪／重寫 marker、`promptCardLine`、card-sandwich、`AskMarkerRepair` | 使用者禁；弱模型仍靠這條 |
| 硬編碼模型白名單當**唯一**閘 | 自架／Ollama／換 id 會誤判 |
| 兩個 UI／兩個「Ask 模式」 | Fallback 必須自動 |
| World-write／點 JEI／Mineflayer | Client-only；YAGNI |
| 把 summon WP1-A 或 worldgen 掃描 **併進** 本 PR | 兄弟計畫 |
| 當「沒呼叫 tool」＝不能用 tool | 能用的模型也可以直接答 |
| 本波實作 streaming／真 `role:tool` 訊息陣列 | 計畫後續；WP1 仍走 FACT 回灌 |
| bump／CF／commit／CUA | 使用者禁 |

---

## 2. Investigate findings（FACT vs INFERENCE）

Bug-lookup：repo + Earth Online App **無**「第一輪就送 native tools／雙路徑」✅。相近：0.1.9 Forge loop、0.1.10 Neo 鏡像。

### 2.1 已是 tool（0.1.10，Forge+Neo）

`AskEngine.registerAskTools` 兩邊各 register 一次：

| Tool id | Adapter | 本機來源 |
|---------|---------|----------|
| `jei_lookup` | `JeiLookupAskTool` | `AskJeiClient.summarize`／`JeiLookup` |
| `acquire` | `AcquireAskTool` | `PackIndex.acquireFactsDetailed` |
| `guide_fetch` | `GuideFetchAskTool` | `PatchouliGuideLookup`／Guidebook pins |
| `quest_fetch` | `QuestFetchAskTool` | `QuestGuide` |
| `consume_use` | `ConsumeUseAskTool` | `ItemConsumeUseFacts` |

`AskToolLoop.ALLOWLIST` = 上表五名。`AskTool` = `name()` + `run(AskToolArgs)` → 字串；空＝miss。

`LlmClient.nativeToolsSchema`：OpenAI `type=function`；五工具共用參數 `item`／`variant_keys`／`dump_level`。`parseNativeToolCalls` 讀 `message.tool_calls` **整列**（已能平行）。

### 2.2 Native tools 今日何時才送（窄）— WP1 要改的洞

| 觀察 | 標籤 | 出處 |
|------|------|------|
| 快樂路徑 `LlmClient.ask()` → `completeRound(..., toolNames=null)` **不**加 `tools` | FACT | `AskToolContext` L10–12；`AskEngine` ~L656–670；`LlmClient.ask` L121–125 |
| `drainBeforeFirstLlm` 是**本機**呼叫，不是 LLM function-call | FACT | `AskToolLoop` L137–166 |
| Native `completeWithTools` **只**在 `continueAfterAsk`：drain + 最多 1 次 grounding 後仍未接地 | FACT | `AskToolLoop` L168–237 |
| HTTP 400 + 曾送 tools → 記 URL、不計 `MAX_LLM_ROUNDS` | FACT | `LlmClient` L28、L322–353 |
| 該 URL 之後 `sendTools=false`；escalate 改 `[[tools]]` JSON hop | FACT | `AskToolLoop` L194–196、L240–258 |
| Request **無** `tool_choice` | FACT | `LlmClient.completeRound` |
| 無 `askNativeTools` config | FACT | `PackAiConfig` |
| PURPOSE／idle：0 extra drain、`ask()` 無 schema | FACT | harness Control |
| `tool_calls` 結果回灌＝`extraFactLines()` 塞進下一發 **user JSON**，**不是** `role:tool` | FACT | `AskEngine` bridge `pushExtras`；`AskLoopState.extraFactLines` |
| 空 `run()` → 靜默 `""`（drain 空閘靠這個） | FACT | `AskToolLoop.run`；`JeiLookupAskTool` catch → `""` |
| `MAX_LLM_ROUNDS=3`、`MAX_LOCAL_TOOLS=8`、`WALL_MS=90s` | FACT | `AskToolLoop` |

### 2.3 Inventory：能力 → 具名 tool → fallback（**不刪**今日碼）

凡「使用者看得到」的 Ask 能力不得只是 dump。WP1 **不**實作新 id（除非括號寫 WP1）。

| 能力 | 今日（FACT） | 具名 tool | Fallback（保留） |
|------|-------------|-----------|------------------|
| JEI 配方／用途文字 | shot-0 `noteShot0(jei_lookup)` + escalate 才可被叫 | `jei_lookup`（**WP1 第一輪可叫**） | shot-0 JEI dump + `[RECIPE_CARDS]` |
| 取得／loot／魚／交易 | shot-0 acquire | `acquire`（WP1） | shot-0 acquire + `HonestMiss` |
| Patchouli／書 | `[GUIDE]` pin + `guide_fetch` | `guide_fetch`（WP1） | `[GUIDE]` in `AskPurposeContext` |
| 任務事實／側欄 | quest fact lines + `withSideQuests` + `quest_fetch` | `quest_fetch`（WP1） | quest facts + `withSideQuests` + `AskJeiHints.ensureQuestStatusVisible` |
| 右鍵／消耗用途 | `[CONSUME_USE]` + `consume_use` | `consume_use`（WP1） | `[CONSUME_USE]` 併進 PURPOSE |
| PURPOSE tooltip／燃料／ToolAction／食物 | `[PURPOSE]` dump | `purpose_lookup`（後） | `[PURPOSE]` pin — strangler 先留一小段 |
| Tetra 成品零件 | `[TOOL_BUILD]` `ToolBuildFacts`／`ModularToolScan` | `tool_build`（queued） | `[TOOL_BUILD]` pin |
| Tetra 材料／插槽／图纸 | `[TETRA_USE]` `TetraMaterialItems` | `tetra_use`（後） | `[TETRA_USE]` pin |
| 作為材料 JEI-U | `[AS_INGREDIENT]` peel | `jei_lookup` `dump_level=SLIM/USES` | `[AS_INGREDIENT]` peel |
| JEI 卡預收集 | `JeiRecipeCards` → `AskService.appendRecipeCardsCatalog` | `show_recipe_card`（WP2） | `[RECIPE_CARDS]` + `promptCardLine` |
| `promptCardLine` | `role \| cat \| 物品ins → 物品outs`（**只 ItemStack**） | `show_recipe_card`／加厚 `jei_lookup` | **保留**；D4=A 接 `otherOutputs` |
| `[[recipe_card:N]]`／`[[recipe:]]`／`{{RECIPE}}` | `RecipeEmbed` sandwich | `show_recipe_card` **側寫**既有標記 | **保留** marker + `AskMarkerRepair` |
| `[[recipe_cards:on\|off]]` | `RecipeCardsMode` | （模式，不是 lookup） | **保留** |
| `{{item:}}`／`[[item:]]` | FACT 接地 | （標記，不是 dump） | **保留** `AskMarkerRepair` |
| `[[tools]]` JSON hop | 文字偽 tool | （協議 fallback） | **保留** |
| Machine brief | `RecipeGetMarks.extractMachine` | 後 `machine_brief` 或 `jei_lookup` | **保留** machine section + post-LLM |
| 容器 NBT | `[CONTAINED]` `ContainedItems` | `contained_items`（後） | **保留** `[CONTAINED]` |
| 卷軸效果 | `[SCROLL_EFFECT]` `ItemVariantKeysText` | 後／併 `purpose_lookup` | **保留** pin |
| Jar 配方／loot 提示 | `[JAR]` lines | `datapack_lookup`（queued） | **保留** jar lines |
| KubeJS／腳本邊 | `graphFacts` | `kubejs_lookup`（queued） | **保留** graphLines |
| 召喚非物品輸出 | UI 有 `otherOutputs`；FACT 字串常「无产物」 | `jei_lookup`／`summon_recipe` | **D4=A 必做** `promptCardLine`＋`JeiLookup` join extras |
| 實體名→registry id | **無** | `resolve_entity`（WP3） | 無 id 不編 |
| Worldgen 結構／biome／礦 | **無** | `worldgen_lookup`（WP4） | `[WORLDGEN]` pin（worldgen WP5） |
| Hold-Y／Pack AI tooltip 套件 | scrub（`AskReplyScrub`；purpose-scrub WIP） | — 不是能力 | scrub，不是 dump |
| Web 搜 | 可選 web | （非本機 tool） | **保留** |
| 解鎖／REQUIREMENTS | per-card footnote | `show_recipe_card`／`jei_lookup` | **保留** footnotes |

**沒有**這些 registry 名（WP1 不新增）：`show_recipe_card`、`resolve_entity`、`worldgen_lookup`、`summon_recipe`、`purpose_lookup`、`tool_build`、`tetra_use`。

---

## 3. Options

| | 做法 | 完整度 | 風險 |
|---|------|--------|------|
| **A（建議）** | 雙路徑同一產品。能力：每輪可送 native tools。不能／400／`off`：今日碼不刪。Routing＝AUTO。新 lookup 能力＝tool、fallback＝FACT | 8/10 | 第一輪 tools 增延遲 |
| **B** | 只加新 tool id，快樂路徑永不送 `tools` | 3/10 | 能用的模型仍只吃 FACT |
| **C** | 刪 marker，全員改 tool | 假完整 | **違約** |
| **D** | 設定頁兩個 Ask 產品 | 分裂 | 禁 |
| **D5** | 只加 pin、不當 tool | — | **superseded** |

**Recommendation: A。**

---

## 4. Approach（A）+ nowadays harness

抄 **pattern**（Claude Code／Cursor 類／OpenAI+Anthropic tool loop），**不**抄產品名或 system prompt 原文。

```
Ask click
  │
  ├─ 本機照舊：JEI dump、卡收集、promptCardLine、PURPOSE／GUIDE pins
  │            drainBeforeFirstLlm — 不刪
  │
  ├─ route = AUTO
  │     config off                       → FALLBACK
  │     URL in URLS_WITHOUT_NATIVE_TOOLS → FALLBACK（auto）
  │     config force                     → CAPABLE（忽略已記 URL）
  │     else                             → CAPABLE
  │
  ├─ CAPABLE（nowadays）
  │     每個 LLM turn 帶 tools schema（WP1＝五舊名）
  │     200 + tool_calls[] → 平行 run → 結果回灌 → 再 turn（MAX_LLM_ROUNDS / 90s）
  │     200 + 無 tool_calls + 正文 → 終答（≠「不會用 tool」）
  │     HTTP 400 + tools → 記 URL、不計 round → 同一次 FALLBACK
  │     工具空／例外 → 能力路徑回 [TOOL_MISS]／[TOOL_ERROR]；drain 仍 ""
  │
  └─ FALLBACK（今日，不刪）
        ask() 無 tools
        [RECIPE_CARDS] + promptCardLine
        LLM 寫 [[recipe_card:N]] / [[recipe:]] / {{RECIPE}}
        RecipeEmbed sandwich
        continueAfterAsk：grounding → 可選 [[tools]] hop
```

**優先序：** 本機 tool／JEI／索引 > 卡存在 > **永不** LLM 腦補 id／座標／「无产物」。

**雙樹：** ALLOWLIST、routing、firstAsk 語意鎖步。

### 4.1 Nowadays patterns（對照我們）

| # | 現今 harness | 我們今日 | 本計畫 |
|---|--------------|----------|--------|
| 1 | **每輪**帶 tools schema | 只 escalate 才送 | WP1：第一輪五工具；後續 capable turn 仍可送（`continueAfterAsk` 已會）。真「每輪含 follow-up」＝WP1.5 |
| 2 | **On-demand > dump** | PURPOSE／JEI／GUIDE 先牆再問 | Strangler：dump 隨 tool 落地縮小；WP1 仍 pin 小 PURPOSE 給弱模型 |
| 3 | `tool_calls`→執行→**`role:tool`**→再 call | 執行後塞 `extraFactLines` 進下一發 user JSON | WP1 沿用 FACT 回灌；**WP-T** 改真 tool 訊息。現有 loop＝`AskToolLoop`（`MAX_LLM_ROUNDS=3`） |
| 4 | 協議失敗 ≠ 模型選擇不叫 tool | 400 已記 URL | **鎖定**：只 400／unsupported→fallback。空 tool_calls+正文＝終答 |
| 5 | 平行 `tool_calls[]` | `parseNativeToolCalls` 已 for-each | WP1 **沿用**（不改 parser） |
| 6 | 型別化 tool error 回模型 | `run()` 空／catch 靜默 | WP1 能力路徑：`[TOOL_MISS] name`；**drain／fallback 仍 ""** |
| 7 | Streaming 邊生成邊顯示 | 單發 HTTP，可等到牆（使用者 6min 經驗＝非 stream） | **WP-S 只計畫**，本波不實作 |
| 8 | 卡＝tool 結果，不是新 widget | 卡靠 marker | `show_recipe_card` **側寫** `[[recipe_card:N]]`（WP2）；WP1 不新 UI |

401／429 **不**改協議（已鎖）。`tool_choice` v1 不設 `required`。

### 4.2 Routing

| 層 | 行為 | 已有？ |
|----|------|--------|
| URL 400 probe | 送 tools 得 400 → 記 `base` | **有** |
| config | `askNativeTools` = `auto`／`force`／`off` | **WP1 加**（toml；本波不加設定頁） |
| per-provider | key＝normalize 後 base | **有** |
| model hint | 後；不得當唯一閘 | **無** |

### 4.3 Strangler（dump 縮小，不是刪）

```
今日：大 FACT 牆 + 快樂路徑無 tools
WP1：牆仍在 + 能用的模型第一輪能叫五工具
WP2+：新 tool 落地 → 對應 pin 可縮（不刪 API）
弱模型：永遠看得到今日牆
```

PURPOSE v1：仍 0 第一輪 schema（與 Control 一致）。能力路徑 PURPOSE 帶只讀 tool＝後 WP。

### 4.4 與 D4=A／worldgen

- Fallback：`otherOutputs`（+ fluid）接上 `promptCardLine`／`JeiLookup`。**不做** option D 新卡家族。
- Worldgen：能力＝`worldgen_lookup`；fallback＝`[WORLDGEN]` pin。本計畫不實作掃描器。

---

## 5. Work packages

### WP0 — 協議探測（可不改碼）

對實際 cloud + 一個 Ollama 打帶／不帶 `tools`。填 Appendix A。

### WP1 — 第一輪送既有五工具 + 同一次 fallback

| | |
|--|--|
| **做啥** | craft／obtain：`firstAsk` → `completeWithTools(五名)`。400→記 URL→同一次 `askNoTools`。`off`＝永不送。`force`＝忽略已記 URL。PURPOSE 無第一輪 schema。不刪 drain／marker／`promptCardLine`。平行：已有 parser。能力路徑空 tool → `[TOOL_MISS]` |
| **測** | `AskToolLoopCheck`：第一輪 schema 五名；400→第二次無 schema；`off`／PURPOSE 不送；fallback 字串可含 `[[recipe_card:N]]` |
| **不** | 新 tool id、刪 sandwich、真 `role:tool`、streaming、設定頁、bump |
| **Accept** | §6 WP1 |

### WP1.5 — 每個 capable turn 都帶 schema

follow-up（跑完 `tool_calls` 之後）仍 `completeWithTools`，直到終答／round 牆。仍不是 `role:tool`。

### WP-T — 真 tool 訊息

`assistant.tool_calls` + `role:tool` + `tool_call_id`。MAX 到了 force 一發無 tools 要正文。

### WP2 — `show_recipe_card`（側寫標記）

成功 → 插入 `[[recipe_card:N]]`。不新 widget。

### WP3 — `resolve_entity` + summon（依 D4=A 資料）

### WP4 — `worldgen_lookup` hook

### WP-S — Streaming（只計畫）

今日：`HttpClient.send` 阻塞到完整 JSON。6min 感＝非 stream + 長 dump。後：SSE／`stream:true` 先畫字再補卡。**本波不實作。**

### WP5 — NFWC 煙測

仍 0.1.13。`dist` + NFWC 一個 packai。Neo→ATM10(1)。Skip CUA。

---

## 6. Done when

### WP1

- [x] craft／obtain 第一輪 HTTP body 可含 `tools`（五舊名）
- [x] 400＋tools → 同一次改今日路徑；auto 下該 URL 之後不送
- [x] `off` → 永不送；`force` → 仍送
- [x] PURPOSE v1：無第一輪 schema
- [x] 空 `tool_calls`+正文＝終答（不因此記 URL）
- [x] **未刪** `promptCardLine`、`RecipeEmbed`、`AskMarkerRepair`、`[[tools]]`
- [x] Forge+Neo 鎖步
- [x] 測試綠；changelog
- [x] **不 bump／不 CF／不 CUA**

---

## 7. Test / NFWC verify

**自動：** capable／400／`off`／PURPOSE。舊 H1–H3 仍綠。

**手動（你；agent 不 CUA）：** NFWC `;` Ask 有卡配方。`auto` 能用的模型 log 可見 `tools`。`off` 仍 sandwich。

---

## 8. 本回合摸過的檔

| 檔 | 動作 |
|----|------|
| `docs/plans/ask-native-tools.md` | 擴寫 inventory + harness |
| forge+neo `AskToolLoop`／`AskEngine`／`LlmClient`／`PackAiConfig`／`AskToolContext`／`AskLoopState` | WP1 |
| `AskToolLoopCheck`；`tests/check_ask_tool_loop.py` | 測 |
| repo + Earth Online `code_change_log.md` | 先寫 |

---

## Appendix A — 協議探測（WP0 填）

| Endpoint / model | 帶 `tools` | 結果 | 記 URL？ |
|------------------|------------|------|----------|
| （未填）cloud | 五 function | 200+calls / 200 忽略 / 400 | |
| （未填）Ollama | 同上 | | |

填表前 **不要** 寫死模型白名單當唯一閘。
