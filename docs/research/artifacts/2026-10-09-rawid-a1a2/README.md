# 2026-10-09 raw-id 修復 真機驗收（A1/A2）— packai_sandbox（NFWC 線，1.19.2）

- jar：`autotest-dev-0.2.3.jar`（sha256 開頭 `140b9c0cab35`）＝含 `Plainify.playerLootLine`（玩家側 source-first）＋ M1；部署記錄：`%TEMP%\deploy_backup_20261009_0325`。
- 跑法：`%TEMP%\packai_a1a2_run.py`（gate idle → 寫 cases.json → 開沙盒 → 搬副螢幕 → 等 status → 收 trace → 還原 → 清 cases）。
- 結果：**4/4 OK**（diamond ×2、amethyst_shard ×2，cardsOut 各 7）＋ 負控 bedrock `NO_SAMPLE`；elapsed 84s；成本 +175,403 tokens（UTC 10-08 累計 159,995 → 335,398）。
- 焦點：開錄前／後前台 hwnd 都係 cua-driver（`foreground_is_mc=false`）＝零搶焦點。
- 分析（`hermes_analysis.txt`，Hermes 自己跑，predicate ＝ **獨立較闊**嘅 `x/y` path-like token）：
  - 玩家 body 過 path-token 掃描 **0 命中**（4/4 clean）。
  - 原本外洩樣本已變人話：`gameplay/transmutation_table_rare` → 「transmutation table rare」、`archaeology/desert_pyramid` → 「desert pyramid」、`chests/bathhouse/bathhouse_normal` → 「bathhouse normal」。
  - `check.post_scrub_drop`（fail-closed 網）＝ 0 → 冇一行要撳網（source-first 已經處理）。
- 未覆蓋（同一輪另見 ATM8）：**item tag id**（例 `forge:ingots/steel`）唔屬今次 plan 範圍 → 見 `2026-10-09-m1-a9/README.md`。
