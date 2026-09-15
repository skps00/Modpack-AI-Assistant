# 2026-09-15 — Settings C 批（7 項新設定）＋ 顯示層 P0 修復

> SK 決定（2026-09-15）：**7 項全做**（原候選 4–10）。原「快捷鍵」項**唔做**——查過 MC 本身
> Options → Controls 已經可以改 keybind（`]` 開 AI、`Y` 查 JEI），再加一個設定係重複機制。
> 本檔要 SK 過目後才實作（plan-first 規則）；code 一律經 cursor-agent。
> 部署規則見 skill `minecraft-mod-jar-deploy`（唔准 hot-copy）。

---

## 0. Baseline（改動前，實測）

| 項 | 值 |
|---|---|
| Settings 控件 | 43 entry / 7 分類（CONNECTION 9、RECIPE 8、ANSWER 7、ADVANCED 6、DEBUG 6、INTERFACE 4、QUEST 3） |
| 控件型別 | `SettingsRegistry.ControlType` 只有 4 種：TOGGLE／NUMBER／TEXT／LIST |
| 存檔語意 | 每個 setter 尾 `SPEC.save()`（B 批已落）；harness `check_settings_setters.py` 守住 |
| 現有相關 key | `askTraceKeepFiles`（按**檔案數**保留）、`showTokenUsage`、`showHiddenQuests`、`ModConfig.Type.CLIENT` |
| 現成機制（重用，唔重造） | `TokenUsage`（每次 ask 有 prompt／completion 數，`LlmClient` 已累加 `cumulativeUsage`）；`AskTrace` 已有 trace 目錄解析；MC 原生 keybind |

---

## 1. P0 顯示層 bug：tooltip 同「要打字嘅欄位」被蓋喺後面（SK 2026-09-15 回報）

**症狀（SK 原話）**：Settings 頁「tooltips 同要輸入嘅 field 都喺 background」。

**根因（z-order／繪製次序），code 證據**：
`SettingsScreenV2.renderScreen()`（:840）次序係
1. `renderBackground` → `GuiShell.nestedShell` → **`super.render()`（:846）畫所有 vanilla 控件，包括 `search`／`valueBox` 兩個 `EditBox`**
2. **`:847` `WidgetCompat.renderHoveredTips(...)` 畫 tooltip**
3. `:868–885` 之後才畫**自繪層**：title、分類欄 panel、entry list panel、逐行 row（`:932/:934` 用 `graphics.fill` 填滿整條 row 帶）、`renderDesc` 描述 panel

→ 自繪層畫喺控件同 tooltip **之上**：`valueBox`（要打字嘅欄位）被 row 高亮／row 文字／描述 panel 蓋住；
tooltip 喺 `:847` 畫完，再被之後嘅 panel 覆蓋（低高度時 `descAsOverlay` 嘅描述 panel 直接佔用 entry list 帶 —
`SettingsLayout.compute():127`）。即係「睇落喺背景」，但**接到 mouse／鍵盤**（控件仍然在最上層收 input），
所以「打得字但見唔到格」。

**修法（唯一正確方向：跟 vanilla 慣例，tooltip 最後畫）**：把 `renderScreen` 次序改成
`renderBackground → nestedShell → 自繪層（title／分類欄／entry list／desc） → super.render() → renderHoveredTips（最後）`；
`fallback`（畫面太細）分支要保留，並確保 tooltip 仍然最後。
**唔可以**用「加 z-level／改 alpha」作替代（唔解決遮蓋）。

**驗收（真機，唔可以只看 code）**：240／256／270／360px 邏輯高 × 三解析度：
① 每個 TEXT／NUMBER entry 點入去，輸入框要**完全可見**（有框、有文字、有游標）；
② 底排按鈕（Done／Reset／Reset all）同搜尋框 hover，tooltip 要**喺最上層**；
③ 描述 panel（docked／overlay 兩模式）唔可以遮住正在編輯嘅輸入框。

---

## 2. C 批七項（每項：key／型別／預設／分類／行為／護欄）

| # | key | 型別 | 預設 | 分類 | 行為 | 護欄（SK 規則：開關＋硬上限＋key 去重） |
|---|---|---|---|---|---|---|
| 4 | `llm.traceKeepDays` | NUMBER | 7 | DEBUG | 只刪 trace 目錄內 `ask-*.jsonl` 中 mtime 老過 N 日嘅檔；刪幾多寫一行 log | `0`＝唔清（預設行為不變）；clamp 0–365；**只限** `<instance>/packai/trace/`，只 match `ask-*.jsonl`；與現有 `askTraceKeepFiles` 並存（先按日數，再按檔案數） |
| 5 | `llm.askMaxToolRounds` | NUMBER | 4（＝現時 hardcoded 值） | ANSWER | AskToolLoop 嘅追問／工具輪數上限改由 config 讀；到頂走現有 fallback 文案 | clamp 1–8（唔准 0＝無上限，防無限 loop 燒 token）；>4 時 UI tooltip 老實講「會多用 token」 |
| 6 | `llm.dailyTokenLimit` | NUMBER | 0＝無限制 | CONNECTION | 每次 ask 完累加 `TokenUsage`（已存在），寫 `<config dir>/packai-usage.json`；當日累計超過上限 → 唔再 call LLM，出提示＋log | 檔案硬上限 64KB；**每日一條 key**（`YYYY-MM-DD` 去重，覆寫同日）；跨日自動重置；只寫自己嘅檔（唔碰 config.toml） |
| 7 | `llm.answerDetail` | LIST（concise／standard／detailed） | standard | ANSWER | 只換 prompt 嘅 **style 段**（沿用 `ReplyLang` 現有 llm_style 機制，3 檔 lang 各加對應字串） | **唔准**改 FACT 規則、官方名規則、禁意譯規則；三個值都要有 lang（3 檔齊） |
| 8 | `ui.askBlacklist` | LIST（separator `;`） | 空 | ANSWER | 命中（item id 全名／namespace／關鍵字，case-insensitive）→ 唔查 JEI／唔送 facts，答 canned 提示 | 空清單＝零影響；命中判斷用純函數（headless 測）；**答案唔准回顯黑名單內容**；上限 64 條（超出截斷＋log） |
| 9 | `ui.uiLang` | LIST（auto／zh_tw／zh_cn／en） | auto | INTERFACE | UI 文案語言（唔跟遊戲語言）；答案語言同此值（＝原 S-1） | auto＝現行為；3 個語言檔已存在（各 484 key），只切換 key 前綴；**唔准**就地改 lang 檔內容 |
| 10 | 診斷包匯出（DEBUG 分類一個**按鈕**，非 config） | 新 `ControlType.ACTION` | — | DEBUG | 打包最近 trace（預設 10 個）＋`logs/latest.log` 尾 2000 行＋redacted `packai-client.toml` → `<instance>/packai/diag/diag-<ts>.zip`，完成後 log 路徑 | zip 硬上限 **32MB**（超出只取最新檔）；**一律 redact** `apiKey`／`token`／`serper`／`tavily` 值；只寫 `<instance>/packai/diag/`；寫入前同名 key 去重（同日重跑覆蓋同名） |

**Registry／lang 工作量**：`3` 個 config key × 3 語言 × (label＋tooltip)＝18 條；`9` 額外要處理語言切換機制。
`tests/check_settings_registry.py` 已斷言「registry 每條都有 3 檔 lang＋setter 有 save」→ 新 key 自動被閘住。

---

## 3. 切批同派工（全部 cursor-agent，Hermes 親驗）

| 批 | 內容 | 為何獨立 |
|---|---|---|
| **C-0** | §1 P0 顯示層次序修復（唔加任何新設定） | 修好之前，新頁**根本用唔到**（睇唔到輸入框）；要先單獨驗 |
| **C-1** | 4＋5＋6（省硬碟／省 token／防爆費） | 純數值＋護欄，harness 可完全 headless 驗 |
| **C-2** | 7＋8（行為影響答案） | 要真機睇答案風格／黑名單效果 |
| **C-3** | 9＋10（語言切換＋新 `ControlType.ACTION`） | 10 會新增控件型別＋寫檔，要最嚴驗收 |

每批：派工 → 雙樹 compile 0 error → 相關 harness 綠 → `tests/check_*.py` **相對 baseline 冇新增紅** →
兩次 code review（pass1 重構／pass2 三個月後脆弱位）→ 真機驗 → 才落下一批。

## 4. 風險／回滾

- 全部係 **client config**，所有新 key 預設值 = 現行為（4 預設 7 日但原本冇清理 → 例如第一晚會刪舊 trace，**要喺 tooltip 講明**；若要零行為改變，可改成預設 0＝唔清，由 SK 開）。
  ⚠️ **待 SK 一句**：`llm.traceKeepDays` 預設要 `7`（自動清）定 `0`（唔清、要自己開）？
- 6 會寫 `packai-usage.json`、10 會寫 zip → 兩者都受硬上限＋只寫自己目錄；出事刪檔即可，唔影響存檔／世界。
- 5／7／8 只影響 prompt／回答路徑 → 隨時改返 config 就復原。
- 無 DB、無遷移、無刪除玩家資料；jar 回滾 = 用 `%TEMP%\deploy_backup_*` 舊 jar copy 返（見 skill）。

## 5. 真機驗收（Hermes 逐項核 trace／log，唔靠肉眼印象）

1. C-0：§1 三項（三解析度 × 三高度）。
2. 4：改 7 → 造幾個舊 mtime 假檔 → 開遊戲 → 只有過期檔被刪、log 有一行。
3. 5：設 1 → 問一條要查 JEI 嘅問題 → 只 1 輪就 fallback；設 8 → 正常多輪。
4. 6：設一個細數（例 1000）→ 問到爆 → 第 2 條問題被拒＋提示；`packai-usage.json` 只有當日一條 key。
5. 7：同一問題在 three 值下答法明顯唔同，但**官方名＋禁意譯**規則三值都遵守。
6. 8：加一個你問開嘅物品 → 即刻變成 canned 提示；移走 → 回復正常。
7. 9：切 zh_tw／zh_cn／en → UI＋答案語言跟住轉，重開遊戲仍在。
8. 10：按一次 → 有 zip；解壓檢查：冇任何 key 明文、大小 ≤32MB、trace 齊。
9. 最後：`python tests/check_*.py` 相對 baseline 冇新增紅；兩次 code review 記錄入 `code_change_log.md`。
