# 2026-10-11 — 「兩個工具成日空手」根因查證（SK 決定 7a）

**問題句**：`consume_use` 被叫 11 次全部空手、`quest_fetch` 37 次有 16 次（43%）空手——係唔係 bug？

**方法（Hermes 親跑，唯讀）**：掃 7 個 instance 嘅 `<minecraft>/packai/trace/ask-*.jsonl`，一行＝一 record；配對 `tool.call`／`tool.result`，抽同名 `send.user` 問題；空手定義＝result 長度 0。

## 1. 實測結果

| 工具 | 呼叫 | 空手 | 空手率 | 空手案例 |
|---|---|---|---|---|
| `consume_use` | **11** | **11** | **100%** | **全部係同一題**：`ad_astra:oxygen_tank`（packai_sandbox_ftb），11 次係同一測試題重跑 |
| `quest_fetch` | **37** | **16** | **43%** | atm8：`immersiveengineering:wirecoil_copper`、`alltheores:tin_block`、`allthecompressed:constantan_block_5x`、`chemlib:gallium_nugget`、`allthecompressed:clay_block_4x`、`quark:duskbound_block`；sandbox：`minecraft:amethyst_shard`；AI_test：`create:schematicannon` |

有命中嘅例子（對照組）：`stray_expansion:chestopener_command` → 130 字（章節＋任務＋說明）、`golden_age:thunder_gem1` → 89 字、`ad_astra:oxygen_tank`（ftb，另一條）→ 331 字。

## 2. 代碼核實（空手係唔係預期行為）

- **`consume_use`**＝`ConsumeUseAskTool` → `ItemConsumeUseFacts.purposeLinesFor()`：資料只來自 datapack **`minecraft:consume_item` advancement**（右鍵消耗類，例：Goety 研究卷軸）＋**唔會 decompile jar**。`ad_astra:oxygen_tank` **唔屬於**呢類 → **空手係正確**；`toolMissNote` 已經寫明「has no right-click consume use … do not invent」（`ConsumeUseAskTool.java:25-28`）。
- **`quest_fetch`**＝任務書索引搜尋；上面 16 個空手 item 都係**包內冇任何任務提到**（如 `tin_block`、`amethyst_shard`）→ **空手係正確**。

## 3. 真正問題（唔係 bug，係浪費）

1. **模型叫錯工具**：`consume_use` 描述係「How to use via right-click consume」（`:17`），模型對**任何 item**（包括氧氣瓶）都會試一次 → 11 次空手 = 11 次無用 round（有 token 成本、亦可能令答案變慢）。
2. **`quest_fetch` 空手率 43% 主要係「item 冇任務」**——查詢本身冇錯，但每次空手都係一個 tool round；如果**離線索引**（`PackIndex` quest index）已知「冇命中」，其實可以**唔叫**。

## 4. 建議（要 SK 揀；全部低成本、可離線驗）

| # | 做法 | 成本 | 效果 |
|---|---|---|---|
| **a** | 收窄 `consume_use` 描述＋加一句「only for items that have a right-click consume use」 | 改一個字串（3 語同步） | 減少模型亂叫（唔保證，因為係 prompt 層） |
| **b** | **確定性**：喺 `QuestionRouter`／drain 階段用既有資料（advancement 索引／quest 索引）判斷有冇命中，冇就**唔 offer** 該 tool | 中等（要改 tool 提供邏輯） | 直接消除浪費；符合 SK「唔靠 prompt 靠結構」原則 |
| **c** | 唔改（現狀已誠實空手＋miss note 提醒模型唔准作） | 0 | 有 token 浪費，但無錯答風險 |

**Hermes 建議：b**（結構性、可量：量「空手呼叫次數」由 27 降到 ~0，並驗答案質素不變）；a 可同時做（一齊改，成本極低）。

## 5. 已知限制

- 樣本細（48 次呼叫、7 個 instance），全部係**測試題**（同一句式）→ 唔代表真玩家分佈。
- 未量每次空手嘅 token 成本（要 trace 內 usage 對齊；列入日後）。
