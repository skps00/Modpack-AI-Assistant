# 真機驗收 checklist（2026-09-15，目標 14:00 run）

> 只准用 `scripts/deploy_packai_jar.py` 部署；禁止 hot-copy jar 入 Prism instance。
> 驗收未過 → 唔 commit（SK 規則：commit 時機＝真機驗收通過後）。

## 0. 事前（Hermes，唔需要 SK）

1. `forge/1.19.2` build：`./gradlew.bat build -x test`（JAVA_HOME = eclipse_adoptium-17）→ 記 jar sha256 前 8 位。
2. 兩樹 compile 0 error；`-I tmp-check.gradle` 全部 harness 綠；`tests/check_*.py` 只剩
   `check_ask_display_leak`（要真機 log，驗收後才會轉綠）。
3. 部署：`python <skill>/scripts/deploy_packai_jar.py`（產生 `%TEMP%\packai_deploy_backup_<ts>`，
   入面要有舊 jar 才落新）。

## 1. 要 SK 落 MC 問嘅句子（一次過問，零額外工夫）

| # | 問句 | 要核嘅嘢 |
|---|---|---|
| A | 任何一條關於 `golden_age` 物品嘅問題（例：「扭曲悟點攞？」） | `display.body.final` 出**官方名**（扭曲悟）＋raw id；**零**意譯名（阿撒托斯／暗鋼閃電類） |
| B | 任何會有建議物品嘅問題 | trace `render.markers` 非空；`display.body.final` **冇** `<!-packai:items=` / `<!--packai:items=` 殘留 |
| C | 「`tetra:modular_sword` 點造？」（單件 Tetra） | 卡片行為符合 `modularToolSingleItem`；**新**：Tetra focus NBT 唔再被丟（圖示唔退化） |
| D | 任何 JEI／mechanic 問題 | log 出 `Pack AI kubejs bridge ... matched=?`；收集 diag 行（byItem key 形狀／extraId 樣本） |
| E | Settings → Knowledge → 開 `knowledgeRemote` → 測試連線；再關掉 | 開：顯示 OK；關：完全唔上網（log 零 fetch） |

## 2. Hermes 逐項核（唔靠肉眼印象）

- 讀 `instances/AI_test_NFWC_DIM/minecraft/packai/trace/ask-*.jsonl` 最新檔：`display.body.final`、
  `render.markers`、`render.cards.final.cardsOut`、`check.scrub.rules`。
- 讀 `minecraft/logs/latest.log`：`Pack AI` 開頭嘅行（fill=…、source=…、skills、bridge、knowledge）。
- 對照 `tests/check_ask_display_leak.py` 由紅轉綠。

## 3. 過之後

1. bump `forge/1.19.2/gradle.properties` `mod_version=0.2.2`（neoforge 暫停，維持 0.2.1）。
2. commit（一個 release commit，含 docs／code／tests；**排除** `logs/`、`tmp-check.gradle`、
   `export_tcp.gradle`）；push 等 SK 一句。
3. HANDOFF STATE + `code_change_log.md` 更新；逐項驗收證據入檔。

## 4. 未過嘅處理

- 記低邊一項唔過（含 trace／log 原文）→ 開新 fix 派工（cursor）→ 再驗；**唔准**為綠而改 assert。
