# packai 真機驗收：一條命令（`tools/packai_smoke.py`）

> 2026-10-10 起：真機驗收唔再手寫 driver。之前每輪都要新寫一份 300+ 行 script（當晚寫咗 3 份），
> 步驟靠人記，易漏「備份／還原／清 `cases.json`」。

## 點跑

```bash
cd C:/Users/skps9/Documents/Code_Project/super_minecraft_AI_player
python tools/packai_smoke.py --cases docs/smoke/smoke_cases_example.json
```

常用參數

| 參數 | 用途 |
|---|---|
| `--cases FILE` | **必填**。harness 格式：`{"world": "...", "quitWhenDone": true, "cases": [{"id","item"或"question"}]}` |
| `--instance NAME` | 預設 `packai_sandbox`（可以 `packai_sandbox_atm8` 等副本） |
| `--jar PATH` | 預設 `forge/1.19.2/build/libs/autotest-dev-0.2.3.jar`（要先 `./gradlew jar -PpackaiAutotest`） |
| `--wait-idle-min N` | 等 SK idle 最多 N 分鐘才開遊戲（自己唔喺機前面時用） |
| `--expect-class X` | 部署後核 jar 內有冇某 class（可重複；防「跑咗舊 build」） |
| `--dry-run` | 只做前置檢查，唔寫檔唔開遊戲 |
| `--no-launch` | 只做閘／備份／部署／落 case（等批准才開） |
| `--force` | **只可以喺 SK 明確批准時用**：無視 activity gate |

Exit code：`0` 完成、`2` 被閘擋（SK 用緊機）、`3` 等唔到 status、`4` 開遊戲／移窗失敗、
`5` 部署失敗、`6` 前置檢查失敗（沙盒唔乾淨／jar 缺／世界冇 `level.dat`）、`9` 內部錯誤。

## 佢幫你做咩（順序）

1. **前置檢查**：沙盒路徑、jar 存在、`level.dat`、**恰一支** mod-class jar、冇殘留 `cases.json`、沙盒冇開住。
2. **Activity gate**：`playing`／`using` ⇒ 拒跑（RC=2）。
3. **備份**：`packai/trace`、`logs`、沙盒 jar → `%TEMP%\packai_smoke_backup_<instance>_<stamp>\`。
4. **部署**：只經 `hermes/scripts/mc_mod_deploy_jar.py`（自動 backup ＋ sha 驗證）；**禁止 hot-copy**。
5. **落 case**：原子寫（`tmp` ＋ `os.replace`）。
6. **開沙盒**：`prismlauncher -l <instance>`、還原原本前景窗、窗移去副螢幕。
7. **等 status**：poll `packai/autotest/status-*.json`（唔用死等）。
8. **收證據** → `%TEMP%\packai_smoke_results_<instance>_<stamp>\`：`summary.json`（status ＋ 每條 case ＋
   jar sha ＋ 焦點前後 ＋ tokens 增減 ＋ wall time）＋ trace ＋ `latest.log`。
9. **收尾**：等 harness 自己關機（`quitWhenDone`）→ 等唔到才硬殺 → 還原 trace／log／jar → 刪 `cases.json`。

## 鐵則由 CI 閘守住

`tests/check_packai_smoke.py`（`python tools/ci_gate.py` 會跑）機械檢查：
部署只准經官方工具、`restore()` 以外唔准 copy 入 `mods`、activity gate 存在、cases 原子寫、
收尾有還原＋刪 `cases.json`、開窗要移副螢幕、有 `summary.json`、唔准用 PowerShell `-like` 淨前綴認 instance。

## 配套（skill `minecraft-mod-in-game-autotest`）

- `scripts/sandbox_preflight.py`：唯讀快手核（jar／世界／config／殘留）。
- `scripts/wait_idle_and_run.py`：等到「SK idle ＋ DS 離峰」才自動跑（`DRIVER` 指去本工具）。
- `scripts/judge_run_traces.py`：判每條 case 嘅 trace。
- `scripts/cases_from_pack_index.py`：由包索引生成 case（跨包抽樣）。
