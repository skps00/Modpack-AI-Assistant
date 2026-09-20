# AGENTS.md — packai（super_minecraft_AI_player）

> 專案級 agent 契約。主契約（`C:\Users\skps9\AGENTS.md`）仍然適用；本檔只補 **packai 專屬**規則同 context。
> 老闆：SK。回覆用繁體中文（專有名詞可原文）。人格見 `SOUL.md`。

## 專案簡介

Minecraft **client-side** mod：整合包感知嘅 AI 助手（JEI／FTB 任務／物品知識／LLM 問答，卡片版面）。

- **雙樹**：`forge/1.19.2`（**primary，活躍**）＋ `neoforge/1.21.1`（**PAUSED** — `neoforge/README_PAUSED.md`：
  唔准 mirror forge 改動、唔准為 symmetry 而改 Neo 樹）。除非 SK 明講開返，**一律只改 forge**。
- 實驗版：`-ask-native-tools`／`-bugfix-ask-fp`／`-bugfix-summon-miss`（worktree，唔係主線）。

## 關鍵路徑

| 項目 | 路徑 |
|---|---|
| Repo | `C:\Users\skps9\Documents\Code_Project\super_minecraft_AI_player` |
| Forge 樹 | `forge/1.19.2`（`gradle.properties` → `mod_version`） |
| Java 17 | `C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2` |
| Prism instance | `C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0\instances\AI_test_NFWC_DIM\minecraft` |
| Trace | `<instance>\packai\trace\ask-*.jsonl` ＋ `index.jsonl` |
| Client config | `<instance>\config\packai-client.toml`（`ModConfig.Type.CLIENT`） |
| 計畫書 | `docs/plans/*.md`（**要入 git**）＋ `.hermes/plans/HANDOFF.md`（現況） |

## 部署鐵則（硬規則，違反過兩次）

1. **唯一部署方式**：`python "$LOCALAPPDATA/hermes/scripts/mc_mod_deploy_jar.py" --target packai`
   （會檢查無 java 進程、自動 backup 舊 jar、複製後驗 sha256＋zip 完整性）。
2. **禁止**任何形式 hot-copy（手動 `cp`／檔案總管拖拉／cursor 自己抄）jar 入 instance。
   原因：live JVM 對 zip 有 stale view → `NoClassDefFoundError`＋功能靜默失效；而且冇 backup 就冇得還原。
3. **禁止**在遊戲／java 進程跑住時換 jar。
4. 回滾 = 由 `%TEMP%\deploy_backup_*\` 內舊 jar copy 返 `mods/`（同樣要關遊戲）。
5. **守門 cron**：`mc-mod-jar-guard`（每 5 分鐘、靜默 watchdog）監住 instance jar 有冇被繞過腳本改動 → 會直接報 SK。
   規則同工具見 skill `minecraft-mod-jar-deploy`（通用，任何 MC mod 專案共用）。

## 開發流程（SK 規則，唔准跳步）

1. **Plan first（連 bug fix）**：寫 `docs/plans/YYYY-MM-DD-<topic>.md` ＋ commit（`docs(plan): ...`）→ 之後才實作。
2. **反方 review 到 8:2 或 9:1 才開工**：plan／idea 用 skill `adversarial-decision-review`（①反方 ②正方 ③中立裁判）。
   - 每輪之間**必須有實質修改**（新證據／縮範圍／改設計），唔准換 prompt 重新擲骰。
   - 上限 **3–4 輪**：到第 3–4 輪仍未達 8:2 → 停手問 SK（附逐輪比分／卡死點／最貴未知／建議）。
   - 複雜交付物**拆開評**（小修復同新功能唔好綁同一個 plan）。
3. **Code 一律經 cursor-agent**（連細 fix）：Hermes 只做 plan／派工／**親驗**。派工要寫死禁令：
   唔准 commit、唔准動其他未 commit 檔、唔准 hot-copy jar、只改指定範圍。
4. **唔准 fake success**：cursor 自報唔算，Hermes 要自己跑 compile／harness／check／diff 對 baseline。
5. 完成後 **兩次 code review**（pass1 重構／pass2 三個月後脆弱位）。
6. 真機驗收逐項核 trace／`latest.log`，唔靠肉眼印象；**驗收未過 → 唔 commit、唔部署新版本**。
7. HANDOFF／`code_change_log.md` 更新。

## Commands（本 repo 實測有效）

```bash
# compile（唯一可靠路徑：JAVA_HOME 指 eclipse_adoptium-17）
cd forge/1.19.2 && ./gradlew.bat compileJava compileTestJava --rerun-tasks --console=plain \
  -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"

# Java harness（⚠️ 唔可以用 -b tmp-check.gradle；一定要 init-script 形式 -I）
cd forge/1.19.2 && ./gradlew.bat -I tmp-check.gradle runAskMarkerIntegrityCheck \
  -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"

# Python 靜態閘（repo root；先記 baseline，改完只准「冇新增紅」）
for f in tests/check_*.py; do python "$f" >/dev/null 2>&1 || echo "FAIL $f"; done

# 打包
cd forge/1.19.2 && ./gradlew.bat jar -Dorg.gradle.java.home="C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
```

## 唔准 add 嘅檔（`git add -A` 禁止）

`logs/`、`forge/1.19.2/logs/`、`forge/1.19.2/tmp-check.gradle`、`forge/1.19.2/export_tcp.gradle`
（`tmp-check.gradle` 由 `research/gen_tmp_check.py` 生成，屬本地工具）。`neoforge` 樹未 paused 前呢條要覆核。

## Gotchas

- **Baseline（2026-09-15 17:5x 實測，工作樹）**：`compileJava compileTestJava` → `BUILD SUCCESSFUL`；
  `tests/check_*.py` → **110/110 綠**（含 `check_ask_display_leak`，佢要有真機 `latest.log` 內容才過）。
  → 之後**任何紅都當 regress 查**；判「有冇 regress」要建 baseline worktree（`git worktree add`）對跑，
  唔准當係自己造成，亦**唔准為咗綠而改 assert**。（舊文件寫嘅「pre-existing FAIL／`compileTestJava` 壞」已經過時，實測已修。）
- `AskReplyScrub` 曾經把 `<!--packai:items=…-->` 內嘅 `--` 收成 `-`（12/12 真機 trace 中招）→ 改動任何
  「separator／去重」regex 前，先跑 `AskMarkerIntegrityCheck` ＋ `tests/check_ask_marker_integrity.py`。
- `PackAiConfig` 每個 setter 尾必須 `SPEC.save()`；settings registry 加 key 要 3 檔 lang（en_us／zh_cn／zh_tw）
  ＋ `tests/check_settings_registry.py` 綠。
- MSYS／Windows：native 工具收 `C:/...` forward-slash；`python "$LOCALAPPDATA/..."` 用 bash 變數。
- Secrets 只存在 `<instance>/config/packai-client.toml`；**唔准**寫入 repo／HANDOFF／計畫書（視為可公開）。
