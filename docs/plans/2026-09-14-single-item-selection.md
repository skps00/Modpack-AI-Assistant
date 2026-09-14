# 2026-09-14 — 單選物品（一次只准選 1 件）計畫

> SK 2026-09-14 10:2x 定案（選項 a）：**Picker 一次只可以選一件物品**；多選排版問題直接消失。
> 狀態：**獲批 → 派工實作**（原「多物品排版修正」計畫 v2 擱置入 backlog，唔刪）。
> 範圍：**只改 `forge/1.19.2`**（NeoForge 1.21.1 已暫停）。

## 做法（最小、可逆）

1. **Picker 行為**：`client/gui/InvPickScreen.java`
   - 點一件未選 → **清空舊選、只留新選**（取代）；點自己 → 取消（維持）。
   - 抽純函數（headless 測得到）：`static Set<String> applySinglePick(Set<String> sel, String key)`（回新 set；含 replace／deselect 語義）。
   - log：真係取代舊選時一行 `Pack AI invpick replaced old=<oldKey> new=<newKey>`（驗收用）。
2. **UI 文字**：`packai.invpick.count_one`（=「已選 1 件（再點即更換）」／英：`1 item selected (click another to switch)`）＋提示行；移除 `packai.invpick.cap` 用法（鍵可留空引用）。
3. **K3 模組化規則由單選涵蓋**：刪 InvPick 嘅「第二件模組化工具」拒絕分支同 `packai.invpick.modular_one_only` 用法（後台 `applyModularToolSingleItem`／`isModularRef`／`filterModularExtras` **保留**＋保留 harness，作 dormant 安全網）。
4. **文件**：README ＋ `docs/CURSEFORGE_DESCRIPTION.md` ＋ `dist/_cf_desc/description.html`（gitignored 但係 CF 上傳源）：「可選最多 8 件物品」→「一次只可選 1 件（點另一件即更換）」。
5. **python check**：`tests/check_ask_card_fallback.py` 內 K3 加嘅 `check_k3_modular_pick_forge` 要改成斷言單選語義（replace/deselect helper＋新 lang key），**唔准淨係刪**。

## 非目標

唔改後台多選管道（`extrasFor`／`alsoSelected`／prompt 規則全部保留，只係 UI 產生唔到）、唔改 trace／卡／scrub／AskCardFallback。

## 失敗模式（自查）

- 單選後「Hold Y 單件詢問」不受影響（另一條路徑，唔經 picker）。
- 舊 pending 多選（session 內殘留）→ `normalizeSelected` 照跑，行為不變（安全）。
- lang 檔缺 key 會顯示 raw key → 三個 lang 檔都要加。

## 驗收

1. Java harness（新或擴充）：`applySinglePick` replace／deselect／空 set 三種 case。
2. python 全套 = baseline 3 FAIL（＋環境性 `check_ask_display_leak` 需真 log）。
3. SK 真機：picker 點第二件 → 舊嘅取消、只剩一件；問答只出一件物品嘅內容。
4. 之後：README／CF 描述於下次出貨一併更新（bump 0.2.2）。
