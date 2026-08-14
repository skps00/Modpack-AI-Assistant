# Pack AI — Tool modifier / material read（Tetra · Tinkers · 同類）

Status: **TM0+TM1 done; TM1 thicken + TM3 smallest slice on `feature/tool-modifier-read`.** TM2 Tinkers deferred（`tic_*` 已記 Appendix A）。TM3：`packai.reply.tool_build` — `[TOOL_BUILD]` 時先講這把實例；空白 `tetra:modular_*` JEI ≠ 這把的取得。  
Generated: 2026-08-12.  
Related: [guidebook-index.md](guidebook-index.md)（書 = advisory）；既有 `ItemVariantKeys`（Tetra scroll schematic）、`AskPurposeContext`（attribute modifiers）、`TetraSchematicLookup`（卷軸材料）。  
**Loaders:** Forge 1.19.2 + NeoForge 1.21.1 鎖步。  
**Log:** repo-root `code_change_log.md`。  
**Version:** 實作波不 bump，除非使用者要出包。

**開工門檻：** 使用者說「開始 tool-mod」或「WP TM1」。

---

## 0. 使用者意圖（鎖定）

Guidebook 做完後，Ask 要能讀 **手上／選中工具** 的 **實際組成**：

- **用了什麼材料**（blade／handle／binding…）
- **掛了什麼 modifier／upgrade**（Tinkers modifier、Tetra improvement、honor 等）
- 答案以 **NBT／遊戲資料** 為 FACT，**不**靠手冊或 LLM 腦補

**已有（不重複發明）：**

| Piece | Today |
| ----- | ----- |
| `ItemVariantKeys` | Tetra **卷軸** schematic id（NBT walk） |
| `TetraSchematicLookup` | 卷軸 → datapack schematic／materials |
| `AskPurposeContext` | Food、potion、**vanilla** MAINHAND AttributeModifiers |
| `ItemIndex` dedupe | `tetra:scroll_rolled#tetra:mirror` 等 variant |

**Gap：**

- **模組化工具本體**（Tetra 錘／劍、Tinkers 多部件工具）→ 未系統化 pin
- **Modifier 名稱／效果** → 未從 NBT／mod API 抽成可讀 FACT
- **材料 tier／stats** → 未與 JEI「裸 id」區分

---

## 1. Goal / Non-goals

### Goal

Ask focus stack 若是 **可拆解工具**，在 PURPOSE／FACT 附：

```
[TOOL_BUILD] 或擴展 PURPOSE
  part: material id / display name
  modifier: id + 可讀效果（lang 或 API）
```

- accuracy > completeness；讀不到就誠實「此 NBT 未解析」
- **universal first**：NBT 路徑掃描 + lang fallback；mod API **soft-dep** 補 display
- 與 guidebook：**data（NBT／registry）> [GUIDE]**；衝突 data 勝

### Non-goals

- 不發明未裝上的 modifier
- 不把 JEI 裸 id 當「這把錘的材料」
- 不做完整 Tetra／Tinkers 百科 UI
- 不硬碼 NFWC 單包材料表
- Phase 1 不涵蓋 **所有** 模組工具（先 Tetra + Tinkers Construct 常見 NBT）

---

## 2. Approach（草案）

```
focus ItemStack
  → detect tool family (tetra modular / tinker tool / generic NBT)
  → extract parts + modifiers (NBT walk caps)
  → optional soft-dep enrich (display names, stats)
  → format [TOOL_BUILD] pin (capped)
  → AskEngine PURPOSE block（與 tooltip／guide 並列；guide 仍 advisory）
```

**優先序（與 guidebook 一致）：**

1. Stack NBT／mod API 讀到的 parts／modifiers  
2. JEI recipe／unlock／quest  
3. Guidebook `[GUIDE]` advisory  
4. Never: LLM invent

---

## 3. Work packages（TODO）

### WP TM0 — Spike（離線／NFWC 樣本）

| | |
| - | - |
| **做啥** | 截 3–5 把 Tetra 工具 + 2 把 Tinkers 工具的 NBT 結構（log／fixture）；對照遊戲內顯示名 |
| **產出** | 本檔 Appendix A 填表；決定 NBT 路徑 |
| **不** | 改 Ask 行為 |

### WP TM1 — 共用模型 + Tetra modular

| | |
| - | - |
| **做啥** | `ToolBuildFacts` record（parts、mods、rawSource）；擴 `ItemVariantKeys` 或新 `ModularToolScan`；Tetra `BlockEntityTag`／`data` 下 module／improvement |
| **測** | `tests/check_tetra_tool_build.py` + fixture NBT |
| **Accept** | 卷軸 **不回歸**；Tetra 錘能列出 head／handle 材料 id |

### WP TM2 — Tinkers Construct modifiers

| | |
| - | - |
| **做啥** | Tinkers tool NBT（`Modifiers`／`Materials` 等 — spike 定）；soft-dep `TinkersBridge` 可選 display |
| **測** | `tests/check_tinker_tool_build.py` |
| **Accept** | 常見錘／劍列出 modifier 名；miss 不瞎填 |

### WP TM3 — Ask wire + prompt

| | |
| - | - |
| **做啥** | `AskService.purposeTooltipFor` 或旁路注入 `[TOOL_BUILD]`；`ReplyLang.factCheck` 禁捏造 modifier；與 quest／guide dedupe |
| **測** | 擴 `check_guidebook_ask` 或 `check_tool_build_ask.py` |
| **Accept** | NFWC 手持 Tetra 工具 Ask「什麼材料／什麼 modifier」見 pin |

### WP TM4 — ItemIndex variant + cards（可選）

| | |
| - | - |
| **做啥** | 同 id 不同材料組成 → dedupe key（類 `scroll_rolled#schematic`） |
| **Accept** | 搜尋／任務不混兄弟工具 |

### WP TM5 — 其他 mod（YAGNI 後）

| | |
| - | - |
| **候選** | Silent Gear、Immersive Engineering hammer、Botania 等 — **僅 spike 後排** |

---

## 4. Done when（Phase 1）

- [x] TM0 spike 表完成  
- [x] TM1 Tetra modular pin 綠 ×2  
- [ ] TM2 Tinkers modifier pin 綠 ×2（defer：`tic_materials`／`tic_modifiers` 已 spike；本波只 Tetra）  
- [x] TM3 Ask + honesty prompt（最小片：`packai.reply.tool_build`；Ask 已注入 `[TOOL_BUILD]`＋socket／name／itemId）  
- [x] Forge+Neo 鎖步；無 pack hardcode  
- [x] changelog；**jar smoke NFWC**（本機 0.1.11，不 bump／不 CF）

---

## 5. Next after guidebook

順序建議：

1. **驗 guidebook fix**（relay_deposit `[GUIDE]` + i18n）— 使用者進行中  
2. **TM0 spike**（可與驗收並行、只筆記）  
3. **TM1 → TM3**（可出貨最小）  
4. Phase C guidebook chunks — **與本波可並行排程，不阻塞 tool read**

---

## Appendix A — NBT spike（TM0 填）

| Sample | mod | item id | NBT path notes | in-game parts/modifiers |
| ------ | --- | ------- | -------------- | ----------------------- |
| Copper hammer (FTB #736, unused) | Tetra | `tetra:modular_double` | **Flat root strings/ints** — not scroll `BlockEntityTag`. Slot→module: `"double/head_left":"double/basic_hammer_left"`. Material: `"double/basic_hammer_left_material":"basic_hammer/copper"` (`moduleKey+"_material"`, Tetra `ItemModule.variantTagKey`). Improvement: `"double/head_left:workable":1` (`slot+":"+key`, `ItemModuleMajor`). Skip `Damage`/`HideFlags`/`honing_progress`/`id`(UUID). | left+right basic_hammer copper; spruce basic_handle; workable 1 both heads |
| Oak axe replacement (Tetra wiki 1.20) | Tetra | `tetra:modular_double` | Same flat keys. Heads: `double/basic_axe_left` + `basic_axe/oak`; `double/butt_right` + `butt/oak`; handle `double/basic_handle` + `basic_handle/stick`. Improvement `"double/head_left:hone/efficiency":1`. | oak axe + oak butt + stick handle; hone efficiency 1 |
| 悟 modular sword (NFWC playtest) | Tetra | `tetra:modular_sword` | Flat slots `sword/blade`=`sword/wu` + `sword/wu_material`=`wu` (unique variant, **no** `material.items`; schematic outcomes `golden_age:wu`); `sword/hilt`=`sword/wu_hilt` (schematic **no** items → omit icon); `sword/fuller`=`sword/reinforced_fuller` + `reinforced_fuller/archotech_arcane_steel`; `sword/guard`=`sword/sword_socket` + `sword_socket/thunder_gem1_socket` (**socket = module+material**, tooltip 插槽【觉醒雷暴】); `sword/pommel`=`sword/forefinger_ring` + steel. Item ids: material JSON `items[0]` then schematic `outcomes[].material.items[0]`. | 悟 blade=`golden_age:wu`; hilt omit; steel fuller+ring; socket `golden_age:thunder_gem1` |
| Treatise/scroll (negative) | Tetra | `tetra:scroll_rolled` | `BlockEntityTag.data[].key` / `schematics` — **ItemVariantKeys only**. No `double/` slot keys. TM1 must return empty `[TOOL_BUILD]`. | schematic ids, not tool parts |
| Tinkers pick (TC3 1.19.2 + 1.20.1) | Tinkers | `tconstruct:pickaxe` | **ItemStack NBT**（`ToolStack` wrapper；非 capability / ItemStackHandler）。Keys 1.19.2=`1.20.1` 同名：`tic_materials` string list（index＝零件序，**無** Tetra 式 `sword/blade` slot key）；`tic_upgrades`＝配方加的 `{name,level}`；`tic_modifiers`＝upgrades＋材料/工具 traits（rebuild）；另 `tic_stats` / `tic_multipliers` / `tic_persistent`（code；wiki 2021 寫 `tic_persistent_data`＝stale）/ `tic_volatile_data` / `tic_broken`。零件卡 item：datapack `tinkering/tool_definitions/<id>.json` `part_stats.parts[]` 對齊 index → `tconstruct:pick_head`+`Material` NBT；**勿**把 `tconstruct:iron` 發明成 `minecraft:iron_ingot`。空 NBT：`ToolModel` fallback `originalModel`（可見，≠ Tetra 隱形）；JEI 常用 `tic_display`+假 materials。**官方無 1.21.1 TC**（FAQ：先 Mantle/Metalborn 再 port）。NFWC/ATM10 本機 mods **無** `tconstruct`。**TM2** — 勿走 Tetra `ModularToolScan` walk。 | parts：`pick_head`/`tool_handle`/`tool_binding`（序≠ Tetra blade/guard） |
| Tinkers sword (TC3) | Tinkers | `tconstruct:sword` | Same `tic_*`。零件 **非** blade/guard/handle：`small_blade` + `tool_handle`×2（無 guard）。 | `tconstruct:small_blade` + 兩支 `tool_handle` + modifiers |
