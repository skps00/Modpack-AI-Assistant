# P0 剩餘量測（④ catalyst 覆蓋率 / ⑤ 首次載入時間·cache / ⑥ tool 選擇準確率）

- 日期：2026-10-10（Hermes 過夜 run）｜狀態：**⑤⑥ 已量（真機）**、**④ 未量（需遊戲內 instrumentation，未批）**
- 資料來源（全部唯讀、可重跑）：
  - 真機 run：`%TEMP%\keybind_run_results_20261010-041910\`（`latest.log` ＋ 6 條 `ask-*.jsonl` ＋ `status-20261010-042341.json`）
  - 沙盒：`packai_sandbox`（NFWC 副本，231 mods，MC 1.19.2 / Forge）
  - 沙盒 cache：`<gameDir>\config\packai\{item-index,guidebook-index,jar-cache,mechanic-cache}`
- 對應 plan：`docs/plans/2026-10-05-advisory-engine-recipe-graph.md` §5（P0 量測項）；① ② ③ 已喺 `docs/research/artifacts/2026-10-10-p0-recipe-metrics.json` 完成

## ⑤ 首次載入時間 / cache（真機，**有時間戳原文**）

| 事件 | 時間戳 | 出處 |
|---|---|---|
| JEI StartEventObserver → ENABLED | 04:21:49.389 | `latest.log`（`mezz.jei.forge.startup.StartEventObserver`）|
| JEI 收 `RecipesUpdatedEvent`（＝配方更新完） | 04:21:52.436 | 同上 |
| packai `ItemIndex loaded from disk entries=12708` | 04:21:57.331 | `com.skps9.packai.PackAiMod` |
| 世界開完（ModernFix 量） | 04:22:03.037 | `Total time to load game and open world was 110.01 seconds` |

- **JEI plugin callback 總耗時 ≈ 1,715 ms**（714 條 `PluginCaller … took <val>` 行，單位按原文 µs／ms 判讀；1,122 條 `Registering …` 行）
- 由 JEI ENABLED → 世界開完 ＝ **13.6 秒**
- ⚠️ **本輪 `ItemIndex` 係「from disk」＝讀 cache**，所以**未量到冷啟動掃描時間**（要清 `config/packai/item-index` 再跑一次才量到；未做，唔准當已量）

### cache 體積（沙盒實測，`du -sm`）

| 目錄 | 大小 |
|---|---|
| `config/packai/item-index` | **26 MB** |
| `config/packai/guidebook-index` | 8 MB |
| `config/packai/jar-cache` | 2 MB |
| `config/packai/mechanic-cache` | 2 MB |
| `config/packai`（合計） | **37 MB** |

→ 對照離線估算（ATM8：raw JSON 15.61 MB、est index 5.46 MB）：**真機 NFWC 231 mod 嘅 index cache 26 MB** 係同一數量級（唔同 pack／唔同欄位，唔可以逐 byte 比）。

## ⑥ `+1 tool` 選擇準確率（真機 6 條 question case）

| case 問題 | 第一個 tool | tool call 數 | 用過 tool 種類 | 輪數 | body 字數 | 出現「答句生成失败」 |
|---|---|---|---|---|---|---|
| Which key opens the map? | `keybind_lookup` ✅ | 8 | 2 | **9（頂到上限）** | 239 | ❌ |
| JEI 嘅快捷鍵係咩？ | `keybind_lookup` ✅ | 5 | 1 | **9（頂到）** | 267 | ❌ |
| 點樣綁定按鍵？而家綁咗邊啲掣？ | `keybind_lookup` ✅ | 5 | 1 | **9（頂到）** | 80 | ✅ |
| 有咩掣撞咗？ | `keybind_lookup` ✅ | 5 | 1 | **9（頂到）** | 80 | ✅ |
| Which key summons a dragon? | `quest_fetch` → `keybind_lookup` | 5 | 3 | 5 | 393 | ❌ |
| How do I get an iron ingot?（對照） | `quest_fetch` → `jei_lookup` … | 5 | **5** | 3 | 519 | ❌ |

- **路由（routing）正確**：4/4 按鍵問題第一個 tool 就係 `keybind_lookup`；對照題冇誤用按鍵 tool ⇒ 「加第 15 個 tool」冇令模型揀錯 tool。
- **但 4/6 條頂到 9 輪上限，2 條出 fallback 失敗文字** ⇒ 問題唔喺路由，而喺 **tool 回傳嘅資料令模型唔收貨**（同日 Hermes 已 root-cause：模型傳 `item`/`machine` 而非 `query` ＋ 泛用 token 令過濾失效 → dump；修法 v5.2 已派工）。
- 成本（同日 `latest.log` 6 行 `Pack AI usage billed=` 合計）＝ **545,655 tokens／6 asks ≈ 90,943 tokens／ask**；⚠️ 比 plan §9 用嘅 **~4–5 萬／ask 錨**高約 **2 倍** ⇒ 拍片／驗收預算要按 9 萬／ask 重估。

## ④ catalyst 覆蓋率：**未量**（要遊戲內 instrumentation）

- 定義（本項要答嘅問題）：583 個離線 recipe type 裡面，runtime JEI 有幾多個**搵得到 catalyst（機器／方塊）**，即「玩家問『用咩機器做』時答得到」。
- 為何離線量唔到：catalyst 係 **JEI runtime registry** 嘅概念（`IRecipeCategory#getCatalyst`／`getRecipeCatalysts`），jar 內只有 recipe JSON，冇 catalyst 對應。
- 建議測量設計（**未做，等 SK 批**）：dev-only harness check（`runJeiCatalystCoverageCheck`）喺沙盒跑：
  1. 由 `RecipeManager.getRecipeCategories()`（JEI）逐個 category 攞 `getRecipeCatalysts()` ＋ `getCatalyst()`；
  2. 同離線 census 嘅 583 個 `type` 做 join（key＝`recipeType.getUid()`）；
  3. 出表：`有 catalyst／只有 catalyst item stack／完全冇` 三類 ＋ 每類佔比；
  4. 同時記「catalyst 物品有冇 lang 名」（玩家睇唔睇得明）。
- 成本：一個新 check 檔（~150 行）＋1 次 compile＋1 次沙盒 run（約 5 分鐘，0 token，唔用 LLM）。

## 未覆蓋（老實聲明）

- 冷啟動（無 cache）嘅首次掃描時間／記憶體 **未量**（本輪 index 讀 cache）
- ④ catalyst 覆蓋率 **未量**（設計已寫，未實作）
- ⑤ 未有記憶體讀數（要用 `jcmd GC.heap_info` 喺遊戲內採樣；本輪冇做）
- 只量咗 **1 個 pack（NFWC）**；跨包要各跑一次
