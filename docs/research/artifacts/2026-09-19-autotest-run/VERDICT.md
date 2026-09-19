# 2026-09-19 — 真機自動測試（autotest harness）首次成功跑：fix A 驗收

**來源 artifact**：本目錄 3 條 `ask-*.jsonl`（由沙盒 instance 真機產生，`packai_sandbox`）＋ `status-*.json`。
**沙盒**：`instances/packai_sandbox`（`minecraft` ＝ junction → `Documents\packai_dev_game`，**231 個 jar**＝230 個 pack mod ＋ `packai-autotest-dev.jar`）。
**你玩嘅 instance**：**零改動**（jar sha256 `06b5b129a114…`、mtime 09-18 07:12；`saves` mtime 07-23）。

## 驗收結果（逐條，證據為 trace 內 `render.cards.final.cardsOut` 同 `display.body.final`）

| case | 物品 | `cardsOut` | 內文 | 判定 |
|---|---|---|---|---|
| A3（fix A） | `tetra:modular_double`（花崗岩錘） | **1** | 「怎么来:合成台（**有序合成**）：橡木木板 + 木棍 -> modular double…」；**冇**「已隐藏」；**冇**簡體 miss 句 | ✅ **PASS** |
| A4 | `sophisticatedbackpacks:netherite_backpack` | 1 | 「锻造台：钻石背包 + 下界合金锭 → 下界合金背包 `[card:1]`」＋「这是它的唯一取得方式」 | ✅ 卡／文字一致（「有卡冇文字」已修） |
| A5 | `tetra:modular_single`（鑿地器） | 2 | 有 `[card:1]` 標記 | ✅ 正常（keep-1 只針對標準框架） |

- A3 候選類別：`Crafting`＋`自动合成 · 动力合成器`（多個候選）→ 最終 **1 張** ⇒ **keep-1 卡閘真機生效**。
- **新發現（細，未修）**：A3 有出卡但 **內文冇 `[card:1]` 內聯標記**（`render.markers.recipe_card_markers=[]`、`emissionRefs=[1]`）；A4／A5 則有標記。→ 列入 follow-up（SK 在意「卡要有相鄰文字」）。
- **未量度**：token delta（driver 未做跑前後快照，係我漏）；`focus_stolen`（driver 未量）。
- 沙盒 `config/packai-usage.json`：2026-09-19 = 288,641 tokens（含早前 dev 嘗試，未隔離）。

## Harness 行為（實測）
- 自動入世界（由標題畫面 `loadLevel`）、逐條用**真 GUI 入口** `AiAssistantScreen.openAndAskAbout(stack)`（＝JEI hold-Y 同一入口）、答案落**現有 trace**、寫 `status-*.json`、`quitWhenDone` 自動關 game。
- 樣本由 JEI output 取（**優先帶 NBT**）→ 木錘用真 oak 樣本，非空殼。
- 上限：`MAX_CASES=20`、每條 180 秒、全程 20 分鐘、`WORLD_TIMEOUT_TICKS=6000`。

## 已知 driver 坑（今次踩過，已修）
1. **先收集結果再還原 trace**（第一次跑：還原步驟先刪走 trace → 證據冇咗）。
2. **跑前移走舊 `status-*.json`**，且只認**新過開機時間**嘅 status（第二次跑：driver 揀咗上一輪 status → 即刻 kill game，0 條 trace）。
