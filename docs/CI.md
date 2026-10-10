# Pack AI 本機 CI 閘

一個指令答「而家棵樹係唔係綠」。**唔上 GitHub Actions。**

## 點跑

在 **repo root**（或任意 cwd；gate 自己釘 `cwd=ROOT`）：

```bash
python tools/ci_gate.py                 # 靜態：全部 tests/check_*.py
python tools/ci_gate.py --compile       # 另加 forge/1.19.2 compileJava + compileTestJava
python tools/ci_gate.py --harness       # 另加 tmp-check.gradle 嘅 run*Check（先 gen）
python tools/ci_gate.py --list          # 只列清單，唔執行
```

可選：`--java-home PATH`（優先於環境變數 `JAVA_HOME`，再 fallback 本機預設 JDK 17 路徑）。

## RC 語意

| RC | 意思 |
|---|---|
| **0** | 全綠（已知紅符合 allowlist 允許值亦算過） |
| **1** | 任何失敗：check 紅、allowlist 腐化、JDK 路徑無效、harness 前置缺 |

## known-red（`tools/ci_known_red.txt`）

Tab 分隔：`<檔名>\t<允許RC>\t<原因>`。`#`／空行＝註解。

- **允許 RC 只准 `2`**（前置未滿足）。`RC=1`（真 fail／leak）**永不**放行。
- 條目指向唔存在嘅 `tests/<檔名>`、RC 欄唔係 2、原因空白 ⇒ gate **FAIL**（防清單腐化）。
- 在清單但變綠（RC=0）⇒ 只印 INFO，唔當失敗（提醒可刪條目）。

而家唯一條目：`check_ask_display_leak.py` → RC=2。該檔自訂語意：0=pass／1=leak／2=冇真機 `latest.log`（`NO LOG LINES`）。本機開發通常冇 Prism smoke log，故 RC=2 係預期，唔係洩漏。

## 為何 cwd 一定要當 repo root

部分 check 用相對路徑 `open(...)`（例如 `check_jei_focus_id_strict.py`、`check_recipe_card_role_budget.py`）。由 `tests/` 或其他目錄直接跑會誤紅。`ci_gate.py` 每個子進程顯式 `cwd=ROOT`（ROOT 由 `__file__` 推導），所以 `cd tests && python ../tools/ci_gate.py` 仍然全綠。

## 為何唔上 GitHub Actions

靜態 check 深度綁 **Windows 本機**：

- Prism 絕對路徑（`C:\...\PrismLauncher-...`）
- `%LOCALAPPDATA%`／Hermes 腳本
- 本機工具假設（`unzip`／`javap`／JDK 路徑）

Linux runner 會大面積假紅。今次範圍＝本機閘；GH Actions **明確不做**。
