# R3 review 紀錄 — Slice 1b plan v4（收窄版；最後一輪）

- 日期：2026-09-22（subagent ×2 並行；反方 28 calls／裁判断 16 calls）
- 裁決：**反方機械面全過、剩 3 條屬「已修得」範圍；中立裁判 `v4_adequate = true`（可以開工）**
- 全文：`C:\Users\skps9\AppData\Local\hermes\cache\delegation\subagent-summary-0-20260922_021303_442301.txt`（反方）／`...-1-20260922_021303_442814.txt`（裁判断）

## 反方親驗通過（機械面）
- **掛鈎點真對**：`AskReplyScrub.proseOrFacts`（`:1578-1590`）prose 支 → `AskEngine:921` → `AskResult:1014` → `AskService:512`（`display.body.final`），全條路無再重算 prose；實測 4 條 trace body `src=prose` 且逐字含 corpus 原文。
- **白名單自足**：`AskReplyScrub.java` 唔喺 dual-tree ALLOWLIST，但 `neoforge/README_PAUSED.md` 存在 → byte drift 只 WARN（`check_dual_tree_sync.py:236-244`）；新 `*Check.java` 喺 `src/test` 唔入 dual-tree 視野；`research/gen_tmp_check.py:6` rglob 自動註冊（`:32 -ea`）→ **57→58 準確**。
- A／C 規則 Python 逐字套用 → 0 殘留；D 類 byte-identical；無現有 harness 會紅（`AskReplyScrubCheck:356`／`JeiInfoFactsCheck:99` 嘅「索引」走另一支）。
- python baseline：125 個閘、只有 `check_ask_display_leak` RC=2（docstring 明言唔掃 model prose）。

## 反方剩餘 3 條（**全部已修**）
1. corpus 15 條含 B 類（機翻／raw 表名，唔屬本 slice）→ corpus 已標明 B 歸 Slice 1c，`InternalJargonCheck` 唔斷言 B。
2. FACT 支掛載點未寫死 → **決定只掛 prose 支**（FACT fallback 措辭屬 1c），plan 已寫明。
3. 改寫可讀性未驗／字形混排 → 已加 zh_cn 第二版字表（`索引没有`→`资料里没有` 等）＋golden §E＋zh_tw fixture §F＋混排斷言。

## 中立裁判親量（已入 plan）
- §1 數字修正：19 條 trace／**13 條**含 jargon／`check.scrub` 37＝18＋19／`before!=after` 2 條（與 jargon 無關）。
- `AskLoopState.isEmptyOrMiss` **只讀 tool-result 文字**（jeiText／acquireText／guideText／questText）、唔讀 display prose → B1 喺 prose 層做**唔會**打爛 runtime miss 偵測器（R2 對 B2 嘅反對點 v4 已繞開）。
- 驗收要記 baseline 同 jar sha；退步檢查三句（Tetra 零件行／火盆掉落句／通用知識標示）已確認唔含 jargon token。
