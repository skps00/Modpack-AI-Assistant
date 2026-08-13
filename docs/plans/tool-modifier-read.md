# Pack AI — Tool modifier / material read（Tetra · Tinkers · 同類）

Status: **ROADMAP / TODO — guidebook A+B 後下一波。**  
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

- [ ] TM0 spike 表完成  
- [ ] TM1 Tetra modular pin 綠 ×2  
- [ ] TM2 Tinkers modifier pin 綠 ×2（或明確 defer + 原因）  
- [ ] TM3 Ask + honesty prompt  
- [ ] Forge+Neo 鎖步；無 pack hardcode  
- [ ] changelog；**jar smoke NFWC**（使用者 `jar`）

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
| | | | | |
