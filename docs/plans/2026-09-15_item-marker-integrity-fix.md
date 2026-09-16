# Plan — item marker 完整性修復（`<!--packai:items=…-->` 被 debris collapse 削壞）

日期：2026-09-15　狀態：**實作完成（cursor-agent）**　負責實作：cursor-agent　驗收：Hermes

## 1. 症狀（真機實錘）

`packai/trace/ask-*.jsonl` 嘅 `display.body.final` 內，建議物品機器標記出現**破損**：

```
before : <!--packai:items=golden_age:wu|扭曲悟-->
after  : <!-packai:items=golden_age:wu|扭曲悟->        ← 左右各少一個 '-'
```

- 掃 2026-09-14 全日 **30 條 trace**：有標記嘅 **12 條全部破損**，完整 **0 條**
  （`intact=0 damaged=12 none=18`）。
- 具體案例：`ask-20260914-234757-tetra_modular_sword.jsonl`，`check.scrub` 兩條事件
  都明確顯示 before/after（第 2 條 rule `stripDuplicateSectionHeaders` 冇再改）。

## 2. 根因（已定位到行）

| 位置 | 內容 |
|---|---|
| `logic/AskReplyScrub.java:235-236` | `DUP_SEPARATORS = ([、，,／|;；·:：\-])(?:[ \t\u3000]*\1)+` → **把所有連續 `-` 收成一個** |
| `AskReplyScrub.scrubPromptEcho():898-920` | → `scrubInternalFieldEcho()` → `stripReplyDebris()`（1050-1064）內呼叫 `DUP_SEPARATORS` |
| `logic/ItemResolver.java:28-29` | `MARKER = <!--\s*packai:items=([^>]+)\s*-->` ← **破損後唔再 match** |
| `logic/ItemResolver.java:36-41` | `stripMarker()` 靠同一個 pattern → 破損標記**剝唔走** |

引入時間：commit `9f9baaf`（09-14「keep internal labels out of replies」加咗 `stripReplyDebris`）。

## 3. 影響

1. **建議物品抽取失效**（靜默）：`ItemResolver.extractIds()` 抽唔到 refs → 建議 chips／highlight 冇咗。
2. **破損文字可能原樣顯示畀玩家**：`display.body.final` 含住 `<!-packai:items=…->`；
   現行 regex 剝唔走 → 需要真機目視確認（驗收項）。

## 4. 修法（最小改動、雙樹字面相同）

**L1（主修）— 先剝後擦：** 在**所有** `scrubPromptEcho()` 之前先處理機器標記，令 debris collapse 永遠見唔到標記。
- 涉及 call sites：`AskService:1900／1909`、`AskEngine:861`、`AskReplyScrub:1533／1538`、
  `AskMissFallback:130`、`AskResult:179-181`、`AskService:2075`（recipe-card marker 已有先剝）。
- **refs 必須喺剝之前由 raw 字串抽**（`extractIds(raw)`），否則剝完就冇得抽。

**L2（安全網）— 容忍已破損寫法：** `ItemResolver.MARKER` 放寬為同時接受
`<!--X-->` 同 `<!-X->`（`-{1,2}` 兩邊），並**只**認 `<!` 開頭 + `packai:items=` 嘅形狀
（避免變成任意 HTML comment 通配）。

**L3（防再犯）— `DUP_SEPARATORS` 加保護：** `-` 收合唔准喺 `<` 之後／`>` 之前發生
（negative lookbehind/lookahead），令 markup 內部嘅 `--` 永遠唔會被當 separator。

**唔准郁**：prompt 文案規則、卡／渲染行為、trace 事件名同欄位語義、`modularToolSingleItem`。

## 5. 測試（新 harness，雙樹 byte-identical）

`logic/AskMarkerIntegrityCheck.java`（或擴充 `AskReplyScrubCheck`，二選一但要有 negative control）：

1. 標記放喺 `【来源】` **之前** → `scrubPromptEcho` 後文字**唔准**含 `packai:items`；`extractIds(raw)` 要抽到 `golden_age:wu|扭曲悟`。
2. 標記放喺 `【来源】` **之後**（模型正確位置）→ 同上。
3. 輸入本身已破損 `<!-packai:items=…->` → 剝得走（L2）＋ refs 照抽到。
4. **Negative control**：`<!x-packai:items=A-->`／`<!-- notpackai:items=A -->` **唔准**被當標記剝走（防 over-broad）。
5. 回歸：正常重複分隔符照舊收合（`、、`→`、`、`，，`→`，`），並明文寫死 `--`（非 markup 情境）嘅期望行為。

## 6. 驗收（Hermes 親跑，唔信自報）

1. forge + neoforge `compileJava compileTestJava` **RC=0**（JDK17／JDK21 各自）。
2. 新 harness `-ea` 全綠（用 `packai_run_java_checks.gradle` 或 `java-verification-without-gradle` 手法）。
3. `python tests/check_dual_tree_diff_symmetry.py` ＋ `check_dual_tree_sync.py` 對稱。
4. 全量 `tests/check_*.py` 對比 baseline（已知 3 FAIL + 對稱閘舊誤報）。
5. 真機：部署後再問一次同類問題 → trace `display.body.final` 內**零** `packai:items`、refs 正常；目視確認畫面（需 SK 開 MC）。

## 7. 回滾

- code：`git revert <fix commit>`（未 commit 前 = `git checkout -- <files>`）。
- jar：部署腳本 `deploy_packai_jar.py` 自動備份（`%TEMP%\packai_deploy_backup_<ts>\`），copy 返就還原。
- 風險：低（純字串處理 + harness 覆蓋）；**唔准** hot-copy jar（只准 deploy 腳本）。
