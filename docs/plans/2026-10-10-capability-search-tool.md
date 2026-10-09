# Plan v2：落點 B — mod 內 `capability_search`（2026-10-10）

- **R1 反方 review：正方 3 : 反方 7 ⇒ 唔可以開工。本 v2 換咗整個資料源設計。**

## 〇、R1 逐條裁決（全部採納）

| # | 反方指控（有證據） | 裁決 |
|---|---|---|
| **A1（critical）** | **S2 前提係假**：`PackIndex.translations` 只由 `build()` 掃 gameDir 嘅 config／腳本目錄（kubejs／scripts／datapacks／config/ftbquests…）下嘅 `*lang*.json`，**完全冇讀 mod jar**。研究量到嘅「40,378／81,045 key」係離線 zipfile 掃 jar 內 `assets/<mod>/lang/*.json`，同 runtime 唔係同一批資料；5 個 jar reader 全部只讀 `data/` 下 recipe／loot／patchouli／worldgen。⇒ 真機 S2 可能≈0 | **採納（最重要）**：**S2 改用 runtime registry**，唔再靠 lang 檔（見 §2.2） |
| A2（med）| S1（mod 描述）價值低被過度加權（平均 ~63 字／mod 推銷文案；兩條真值都唔係 S1 撈到；NeoForge 1.21 全空） | **採納**：S1 **唔加權**（×1），只做輔助 |
| **A3（high）** | 漏咗**兩個 early-return guard**：keybind 上一代要喺 `AskEngine:377-399`／`:424-428` 加 `&& !PackIndex.isKeybindQuestion(question)`，否則問題含 craft／obtain 字眼就會喺**未叫 LLM 前** return miss ⇒ 新 tool 靜默永不執行 | **採納**：v2 補 `isCapabilityQuestion` 意圖閘＋兩個 guard（§2.4） |
| A4（med）| 落地點唔止 3 個：`check_tool_schema_stable.py:108` 係**精確 list 相等**；`capability_search` **唔可以**入 `FIRST_ROUND_TOOLS`（`:109 forge_f == neo_f` 會紅）；`check_dual_tree_sync.py` 要加 `TREE_SPECIFIC`；而且 tool 數係 **15→16**（plan v1 寫 14→15 已過期） | **採納** |
| A5（med-high）| V4 只證明舊路由冇爛，證唔到新 tool 可用 | **採納**：加 capability routing probe ＋ 對照題（§3 V4） |
| A6（med）| 驗收可假綠：determinism 只測 mirror 唔測 runtime；raw-id regex 捉唔到 mod id／translation key；V5 冇 pass 門檻；baseline 引 AGENTS 舊記「110/110」已過時 | **採納** |
| A7（med）| ROI 天花板 ≈2/5，而且 plan 違反自己嘅 gate（fit §6 寫 B 要「A 覆蓋率 ≥60%」） | **半採納**：見 §1 誠實聲明 —— 我**明示豁免**該 gate，理由：現況係 **0%**（完全答唔到），2/5 係實質改善；且唔係硬編碼任何包 |

## 1. 目標與誠實預期

**目標**：玩家唔提供 item id，直接問「包入面邊樣嘢可以做到 X」，mod 由 runtime 來源搵候選並標明「候選／未確認」。

**誠實預期（寫死，唔准事後搬龍門）**：研究嘅 5 條 canonical 需求，文字來源天花板約 **2/5**；幾何（清 100×90）／具體機器（白色混凝土工廠）／部分能力**預期撈唔到**，必須答「未確認」。

**明示豁免 gate**：`2026-10-10-capability-search-fit.md` §6 嘅「A 覆蓋率 ≥60% 才提前做 B」—— 現有證據**未達**；我以工程師身分決定照做，因為 (a) 現況 0%，(b) mod 保持通用（唔硬編碼任何包），(c) 呢個係 SK 明確批准嘅三個落點之一。若 V5 <2/5，**唔准**當成功，要出停手報告。

## 2. 設計

### 2.1 新 tool

- `logic/CapabilitySearchAskTool.java`（`api.AskTool`）：實作 **`name()`／`run(AskToolArgs)`／`description()`／`argsSchemaJson()`**（`run` 同 `description` 係 abstract，必實作）；`need`／`limit` 由 `args.argumentsJson` 解析（`AskToolArgs` 冇現成欄位 —— 照 `KeybindAskTool` 做法）。
- `limit` 默認 8、上限 20。

### 2.2 資料源（全部 runtime、零 jar 掃描、零網絡）

| 代號 | 來源 | 已核實嘅 API 存在 | 成本 |
|---|---|---|---|
| **S2a 顯示名** | 全部已註冊物品嘅本地化顯示名 | `ForgeRegistries.ITEMS`（`PatchouliBridgeImpl:45` 已用）＋ `ItemStack.getHoverName()`（`AnvilRepairHint:55` 已用） | 一次遍歷 ~10k 條字串 |
| **S2b tooltip** | 玩家會見到嘅 tooltip（含 Shift 隱藏行） | **`client/context/TooltipCapture.capture(ItemStack, LocalPlayer)` 已存在**（`TooltipCapture.java:23`，內部 `getTooltipLines(...ADVANCED)`＋`FORCE` 展開） | **貴** ⇒ 只對**短名單**做，硬上限 |
| **S1 mod 描述** | `IModInfo.getDescription()`／`getDisplayName()`／`getModId()` | forgespi 6.0.0 已 javap 核實存在；packai 現時只用 `getModId()`／version（`GameContextCollector:36` 等） | 一次遍歷 ~380 mod，KB 級 |

- **唔做** S3（class 名單，已否證）／S4（任務文字，貴 3–5 倍）。

### 2.3 檢索與排序（deterministic）

1. 拆 `need` 為關鍵詞（中英、去停用詞）。
2. Stage 1：對 S1＋S2a（記憶體內字串）算分：完整片語 > 多詞同現 > 單詞；同分 → 按 **registry/mod id 字母序**（**唔准**靠 HashMap 次序）。
3. Stage 2：對 Stage 1 頭 **K 個候選 mod**（硬上限）做 S2b tooltip 掃描，取真證據片段（≤120 字）。
4. 輸出：**「候選（未確認）」**＋每個候選嘅證據片段＋來源標記（mod 描述／顯示名／tooltip）；**唔准**寫成結論。
5. 零命中 → 回空字串（走 loop 嘅誠實 miss 路徑）＋ `toolMissNote()`。

### 2.4 落地位（**全部 7 項**，漏一項即靜默失效）

1. `AskEngine` 註冊（照 `keybind_lookup`）。
2. `AskToolLoop.CAPABLE_TOOLS`（:39-42）。
3. `AskToolLoop.QUERY_TOOLS`（:46-48）。（`ALLOWLIST` 自動跟）
4. **唔入** `FIRST_ROUND_TOOLS`（會令 `check_tool_schema_stable.py:109` 紅）。
5. `tests/check_tool_schema_stable.py:108` 精確 list 加 `"capability_search"`。
6. `tests/check_dual_tree_sync.py` `TREE_SPECIFIC` 加新檔（paused 下 WARN；`--no-paused` 會 FAIL，屬預期）。
7. **兩個 early-return guard**：`AskEngine:377-399` 同 `:424-428` 尾加 `&& !PackIndex.isCapabilityQuestion(question)`；新增 `PackIndex.isCapabilityQuestion(...)` 意圖閘（照 `isKeybindQuestion` 寫法）。**呢項係 keybind 上一代實錘、標「最重要」嘅一項。**

**唔准郁**：卡落位、`AskReplyScrub`、`HonestMiss` 文案、system prompt 主體、`neoforge` 樹。

## 3. 驗收標準（先寫死）

- **V1** `compileJava compileTestJava` RC=0；`tests/check_tool_schema_stable.py` 綠；現有 harness 全綠。
- **V2** 新增 `tests/check_capability_search.py`：① 同輸入兩次 → 輸出 byte-identical；② 冇 `need` → 空；③ `limit` 上限夾得住；④ 每個候選必有**非空**證據（否則唔出該候選）；⑤ **raw-id 定義擴大**：`namespace:path`、**mod id**、**translation key**（`tile.*.*`）一律唔准出現喺玩家可見輸出。
- **V3** 全部 `tests/check_*.py` 冇新增紅（baseline **以當日實跑為準**；AGENTS 舊記「110/110」已過時，唔可以當 gate）。
- **V4** **routing probe**：5 條 canonical 需求嘅第一個 tool ＝ `capability_search`；另加 2 條普通物品題做**負控**（唔准誤揀 `capability_search`）。
- **V5** **真機（沙盒）**：跑 5 條 canonical，**硬門檻 ≥2/5 命中**（真值：DJ2 `enderutilities`／ATM8 `refinedstorage`），逐條貼 `latest.log` ＋答案原文；撈唔到嘅要誠實列明。**<2/5 ＝ 唔算完成，出停手報告。**
- **V6** `git status` 只准預期檔；`neoforge/` 零改動。

## 4. 風險／還原

- 風險：中——新增 tool（7 個落地位）；S2b tooltip 掃描有 CPU 成本 ⇒ 硬上限 K。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\` 還原。
- 唔准：hot-copy jar、真 instance 自動部署、`git add -A`。

## 5. 執行

- 實作＝**cursor-agent**；Hermes 只做 plan／派工／親驗。
