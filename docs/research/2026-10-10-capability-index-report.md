# 落點 A — 離線能力索引：真 DJ2 首跑報告（2026-10-10，Hermes 過夜）

- 工具：`tools/mine_capability_index.py`；查詢表：`tools/capability_queries.json`
- 輸入：DJ2-Cleanroom-Client-v1.2 / `minecraft/mods`（**235 個 active jar**）＋ `--game-dir` 指向同 instance
- javap：`17.0.19`（本 run 呼叫 **1443** 次，cap 1500，caps_hit=False）
- 完整 artifact（**21 MB，唔入 git**，用 `_` 前綴放 scratch）：`docs/research/artifacts/_2026-10-10-capability-index-DJ2.json`
- 重跑性：同輸入兩次 run **byte-identical**（Hermes 自己跑，5211324 bytes ×2；單 query 版）

## 逐條 query

| need_id | 問句（SK 原句） | 候選 mod | level A | runtime_visible=true | 未確認 |
|---|---|---|---|---|---|
| `jsu_nonstackable` | 儲不可堆疊物品 | 20 | 20 | 1 | 0 |
| `dig_area_100x90` | 清 100×90 範圍 | 20 | 20 | 1 | 0 |
| `white_concrete_factory` | 白色混凝土工廠 | 20 | 20 | 1 | 0 |
| `wireless_power` | 無線傳電 | 20 | 20 | 1 | 0 |
| `auto_crafting` | 自動合成 | 20 | 20 | 1 | 0 |

## 硬證據樣本（SK 原句「儲不可堆疊物品」＝ DJ2 Junk Storage Unit）

- 候選：`enderutilities-1.12.2-0.7.15.jar`（level **A**、runtime_visible=**False**、lang 格式=lang）
  - `fi/dy/masa/enderutilities/tileentity/TileEntityJSU$ItemHandlerWrapperJSU.class`
    - `public boolean isItemValidForSlot(int, net.minecraft.item.ItemStack);`
    - `8: invokevirtual #47                 // Method net/minecraft/item/ItemStack.func_77976_d:()I`
  - `fi/dy/masa/enderutilities/tileentity/TileEntityJSU.class`
    - `0: sipush        270`
    - `10: sipush        256`

## 誠實限制（唔准當已確認）

1. **候選 ≠ 已確認**：關鍵詞＋常數命中係 heuristic。同一條 query 出 20 個候選、artifact 內含大量無關命中（例 `micdoodle8/mods/miccore/IntCache.class: sipush 256`）⇒ **精度低**，要人（或落點 B 嘅 tool）再篩。
2. **`getMaxStackSize` 字面唔會出現喺 1.12.2 jar**：Forge 1.12.2 用 SRG 名 `func_77976_d`（Hermes 已用 javap 核實）；所以 A2 嘅 logic 證據係 SRG 名，唔係 MCP 名。
3. **bytecode 層 runtime 讀唔到**：DJ2 係 1.12.2 `.lang` 格式 ⇒ `lang_format_detected=lang`、`runtime_visible=false`（packai runtime 只讀 1.19.2 JSON lang）⇒ 呢批能力答案**只能離線答**，唔會自動入遊戲內答案。
4. **artifact 體積**：5 條 query = **21 MB**（單 query 5.2 MB）⇒ 唔入 git；未來要落點 B 用就要諗壓縮／只留 top-N 證據。ATM8（380 jar）估算 >35 MB／run（**未實測**）。
5. **`--timeout-s` 原本係假安全閥**（實作只靠 `--max-javap-calls` 截流、零 wall-clock）——已由 code review 捉出並已修（改 `time.monotonic()` 真強制）；另 CRLF 寫檔問題（令 A4 只喺同平台成立）亦已修（一律 LF）。
6. **分級退化（review 指出，唔准當「有 20 個確認」）**：5 條 query 全部 **20/20 候選、全部 level A、unresolved = 0**。即係「level A」只代表「jar 內有 bytecode 原文」，**唔代表答案正確**；而且 20-cap 會靜默丟真候選、javap 1,500 上限用完後段 query 會攞到 0 class（已加 per-query `caps_hit` 標記）。⇒ 「runtime 覆蓋率」呢個 gate 數字**未校準**，落點 B 值唔值做要另做精度量測。
