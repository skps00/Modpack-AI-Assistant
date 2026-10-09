# Plan：落點 B — mod 內 `capability_search`（2026-10-10，SK 授權自主完成）

- 前身：`docs/plans/2026-10-10-capability-search-fit.md`（SOP 融入分析；A 已完成）。次序調整：**A（完成）→ B → C′**（C 嘅 footer 分級已 re-scope，理由見 `2026-10-10-source-grading-policy.md` §7）。
- 目標（一句）：玩家可以**唔提供 item id**，直接問「**包入面邊樣嘢可以做到 X**」，mod 由**runtime 可及來源**搵候選並誠實標明「候選／未確認」。

## 1. 為什麼係呢個設計（已被研究否證嘅做法唔重複）

- 研究（`docs/research/2026-10-10-cheap-capability-lookup-research.md`、`2026-10-10-harness-prompt-cache-comparison.md`）實測：
  - **最平最準＝S1（mod 描述）＋S2（lang 顯示名／tooltip）**：兩條真值能力（DJ2 `enderutilities` 儲不可堆疊／ATM8 `refinedstorage` 自動合成）**淨靠 S2 就撈到**。
  - **S3（class 名單）雜訊大**、**S4（任務／指南文字）貴 3–5 倍** ⇒ 唔做主要來源。
  - **javap／bytecode 喺 runtime 做唔到**（每包 1500 次呼叫、artifact 8–48 MB）⇒ 唔喺 mod 內做。
  - 幾何（清 100×90）／具體機器（白色混凝土工廠）**任何文字來源都撈唔到** ⇒ 必須老實講「未確認」。
- 上一代離線索引（落點 A）嘅弱點：候選 **只按 jar 名排序、無相關度** ⇒ 候選清單 ≠ 答案。**本 plan 必須修正呢點**（要有相關度排序＋證據片段）。

## 2. 設計

### 2.1 新 tool：`logic/CapabilitySearchAskTool.java`（`api.AskTool`）

- `name()` ＝ `"capability_search"`。
- args（`argsSchemaJson()`）：`need`（string，**需求自由文字**，**唔需要 item id**）＋ `limit`（integer，**默認 8、上限 20**）。
- 來源（全部 runtime、零檔案掃描、零網絡）：
  1. **S1 mod 描述**：`ModList.get().getMods()` → `IModInfo.getDescription()/getDisplayName()/getModId()`（已親核 Forge 1.19.2 `forgespi` 有呢三個 method；packai 現時**冇**用）。**只建一次**（pack 載入後 cache），唔可以每 ask 重掃。
  2. **S2 顯示名／tooltip**：`PackIndex.translations`（key→文字，zh 優先）。現時係 **private** ⇒ 要加一個 read-only accessor 或 search API。
  3. **S2b item 描述事實**：`PackIndex.descByItem`。
  4. **S4 任務／指南文字**：**唔做**（貴、命中率唔見得高）；若日後要，另開 slice。
- 排序（要 deterministic、可測）：
  - 問題 → 關鍵詞切分（去停用詞、支援中英）；
  - 命中：完整片語 > 多詞同現 > 單詞；**加權**：S1 mod 描述命中 ×2（因為佢直接答「邊個 mod」）；
  - 輸出 = top-N，每個候選要**帶證據片段**（≤120 字，去 raw id）；
  - 同分 → 按 mod id 字母序（**穩定，唔准靠 HashMap 次序**）。
- 輸出文字（餵 LLM）：明寫「**候選（未確認）**」＋每條證據來源（mod 描述／顯示名）；**唔准**寫成結論。
- 零命中 → 回空字串（交返 loop 嘅誠實 miss 路徑）＋ `toolMissNote()`。

### 2.2 落地位（同 keybind v5 一模一樣；漏一處＝靜默失效）

1. `AskEngine` 註冊（照 `keybind_lookup` 位置）。
2. `AskToolLoop.CAPABLE_TOOLS`（:39-42）＋ `QUERY_TOOLS`（:46-48）。
3. `tests/check_tool_schema_stable.py` 例外集合。

**唔准郁**：卡落位、`AskReplyScrub`、`HonestMiss` 文案、system prompt 主體、`neoforge` 樹。

### 2.3 誠實限制（要寫落玩家可見／plan）

- runtime 冇 bytecode 層 ⇒ 幾何／具體機器功能**預計撈唔到**；撈唔到時要講「未確認」，**唔准**講「呢個包冇」。

## 3. 驗收標準（先寫死）

- **V1** `compileJava compileTestJava` RC=0；`tests/check_tool_schema_stable.py` 綠。
- **V2** 新增 `tests/check_capability_search.py`：① 同一輸入兩次 → **輸出 byte-identical**（determinism）；② 冇 `need` → 空；③ `limit` 上限夾得住；④ 每個候選一定有證據片段；⑤ **輸出零 raw id**（`namespace:path` 唔准出）。
- **V3** 全部 `tests/check_*.py` 冇新增紅（baseline 127／126／1 已知紅）。
- **V4** 現有 harness 全綠（14→15 個 tool 唔可以令路由變差：重跑 keybind 嗰 4/4 路由檢查）。
- **V5** **真機（沙盒）**：跑 5 條 canonical 需求（儲不可堆疊物品／清 100×90／白色混凝土工廠／無線傳電／自動合成），逐條貼 `latest.log` ＋ 答案原文；**誠實報命中率**（已知真值：DJ2 `enderutilities`、ATM8 `refinedstorage` 應該入到候選）。
- **V6** `git status` 只准預期檔；`neoforge/` 零改動。

## 4. 風險／還原

- 風險：中（新增 tool ＝ 改白名單／註冊，keybind 教訓：三處漏一處會靜默失效）。
- 還原：`git revert <commit>`；jar 由 `%TEMP%\deploy_backup_*\` 還原。
- 唔准：hot-copy jar、真 instance 自動部署、`git add -A`。

## 5. 執行

- 實作＝**cursor-agent**；Hermes 只做 plan／派工／親驗。
