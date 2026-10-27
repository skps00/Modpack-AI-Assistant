# 2026-10-13 真機掃描：9 個整合包 × packai autotest（server 側）

> 執行者：Hermes（server `skps00-Z590-UD`，X11 `:0` ＋ PrismLauncher 11.1.1）。
> 目的：證明 packai（client-side 助手 mod）在**真整合包環境**開得遊戲、載得世界、答得到 pack 專屬問題。
> 全部數字都係即場親核；證據檔一齊放喺本資料夾。

## 1. 結果（一覽）

| 整合包（instance） | mod 數 | Forge | 題數 | 結果 | 完成時間（HKT） |
|---|---|---|---|---|---|
| All the Mods 8 (`atm8`) | 382 | 43.2.14 | 2 | ✅ 2/2 | 06:26 |
| Create+ (`createplus`) | 287 | 43.5.1 | 2 | ✅ 2/2 | 07:42 |
| Enigmatica 9 (`e9e`) | 233 | 43.4.23 | 2 | ✅ 2/2 | 07:25 |
| FTB Skies Expert (`ftb`) | 358 | 43.4.5 | 2 | ✅ 2/2 | 05:23 |
| Minehattan Project | 78 | 43.3.0 | 2 | ✅ 2/2 | 07:28 |
| Minimal Create | 14 | 43.4.0 | 2 | ✅ 2/2 | 06:29 |
| Slimes Adventure | 95 | 43.4.0 | 2 | ✅ 2/2 | 06:39 |
| Star Technology (`startech`) | 155 | 43.3.9 | 3 | ✅ 3/3 | 04:16 |
| UniversIO | 177 | 43.2.10 | 2 | ✅ 2/2 | 04:36 |

- 「題」＝真機 LLM 問答（每 pack 1 條知識題＋1 條 pack 專屬物品／配方題）。
- 每題嘅 trace 檔名寫在 status JSON 內（`traceFile`），答案全文在 instance 的
  `minecraft/packai/trace/ask-*.jsonl`。
- 全部 9 個 pack 都係**同一個 jar**（`packai-autotest-dev.jar`，sha256 `05f4a6dd06437e3a`，
  由 `forge/1.19.2` build 出，部署時做 backup＋sha256 驗證）。

## 2. 方法

1. **世界生成**（`tools/packai_make_world_linux.py`）：用 pack 自己嘅 Forge **專用伺服器**
   生成一個原生世界（唔可以照搬別的 pack 嘅世界 —— 實測會 `WORLD_TIMEOUT`），
   再 copy 入 instance 的 `saves/packaiworld`。
2. **真機測試**（`tools/packai_smoke_linux.py`）：deploy jar → 開 Prism → 自動撳過 Prism
   精靈／Demo／玩家名 → 遊戲內 autotest（讀 `cases.json` → 載世界 → 逐題問 LLM →
   寫 `status-*.json`）→ 收 trace／截圖／log。
3. 一次只開一個 instance（同一部機、同一個 X display）。

## 3. 時間線（2026-10-13）

| 時間 | 事件 |
|---|---|
| 04:16 | Star Technology 3/3（首個 pack） |
| 04:36 | UniversIO 2/2 |
| 05:23 | FTB Skies Expert 2/2 |
| 05:52–06:13 | ATM8 第一次跑：卡到「落後 246 ticks」零進度 → 查出 **instance 冇設 heap**（Prism 預設 ~2GB） |
| 06:19 | ATM8 改用 8GB heap 重跑 → 06:26 **2/2** |
| 06:21–06:24 | 世界生成工具喺 Minimal Create 出 `Invalid or corrupt jarfile run.sh` → 修（用 `bash run.sh`） |
| 06:24 | 再試：專用伺服器一開即死（Oculus 光影，client-only）→ 加**自動停用唔相容 mod**迴圈 → Minimal Create 世界生成成功 → 06:29 **2/2** |
| 06:39 | Slimes Adventure 2/2 |
| 06:46–07:11 | Enigmatica 9 世界生成：揭發 **stdout 死鎖**（見 §4-3）＋ 兩個並行 worldgen 撞 port 25565 |
| 07:28 | Minehattan Project 重跑 2/2（第一次 `NO_SCREEN`，見 §4-6） |
| 07:42 | Create+ 2/2（關掉 MemorySettings 開機警告畫面後，見 §4-6） |

## 4. 發現（全部有 log／截圖證據）

### 工具側（`tools/packai_make_world_linux.py`；已修）
1. Forge 1.19.2 installer 出嘅係 classpath 啟動腳本，舊碼當佢係 jar（`java -jar run.sh`）→ `Invalid or corrupt jarfile`。
2. 專用伺服器載唔到 **client-only mod**（Oculus／Rubidium／CIT Reforged／feytweaks／findme／lightspeed／fusion…）。
   修法：解析伺服器 mod 表 `|ERROR` 行 ＋ `crash-reports/*-fml.txt` 的 `Mod File:` ＋
   「@Mixin target net.minecraft.client.* was not found <config>.json」→ 該 jar 移入 `mods-disabled/` → 重試。
3. **stdout 死鎖（最隱蔽）**：舊碼一見到 `Done (` 就停止讀 stdout → Forge 寫滿 pipe 卡死 →
   `stop` 收唔到、save 做唔完 → 世界 region 檔 **0 bytes** ＋ 留低孤兒 JVM。
   修法：reader thread 全程抽乾 stdout、逐行 flush、`start_new_session` ＋ `os.killpg` 殺整個 process group、
   `stop` 後畀 900 秒收工、240 秒冇輸出就當掛機。
4. 世界生成唔可以並行（兩個伺服器一齊搶 port 25565 → `FAILED TO BIND TO PORT`）→ 每個 instance 用唔同 port。
5. 殘留 `packaiworld` 會被「載入」而不是重生 → 每次先清。
6. 我哋自己支 mod（`packai-autotest-dev.jar`）係 client mod，放入伺服器即死 → 世界生成前一定先移走。

### Mod 側（`forge/1.19.2`；**未改**，等 SK 決定）
6. **開機彈窗擋住世界載入**：Create+ 的 `memorysettings` mod 開機彈「Proceed／Open guide／Stop showing」畫面，
   autotest 只在 `mc.screen instanceof TitleScreen` 才載世界 → 5 分鐘燒光 → `WORLD_TIMEOUT`。
   證據：`createplus/screen.png`＋`createplus/status-20261013-071709_BEFORE-fix.json`。
   臨時繞道（只改 sandbox 設定）：該 pack 自己嘅 `config/memorysettings.json` 設 `disableWarnings=true`
   （備份 `*.bak-20261013-0739`）→ 07:42 **2/2**。
7. **`NO_SCREEN` 偶發**：Minehattan 第一次跑第 2 題時 685ms 內 screen 未開就判失敗；重跑 2/2。
   即係 harness 檢查太快、冇 retry。
8. **世界載入預算由「讀 cases.json」開始計**（`WORLD_TIMEOUT_TICKS = 6000`），大 pack 光係載入 mod
   就已經燒掉預算（E9E 07:24 仍然在 Forge 100% loading screen）→ 有機會假 `WORLD_TIMEOUT`。

> 6/7/8 都係同一個根因家族：**harness 假設「client 一開就係 title screen、screen 一定即刻出現」**。
> 建議（未做，等批准）：① OPEN_WORLD 階段遇到非 Title／非助手 screen 就按鈕（Proceed／Continue／OK…）試關掉；
> ② `openTicks` 改為「第一次見到 title screen 才開始數」；③ 助手 screen 檢查畀幾個 tick 再判 `NO_SCREEN`。

## 5. 未完成／待決定

- 上述 mod 側 3 個修復（Java）：要唔要我直接改＋build＋重跑 3 個 pack 回歸？（專案規則係「code 經 cursor-agent」，
  但 cursor-agent 在 server 未登入。）
- 世界生成工具（新檔，未 commit）＋本資料夾要唔要 commit。
- 未跑：`packai_sandbox_slimesadventure` 已跑；其餘沙盒（e9e 以外的 4 個）已全數覆蓋 —— 即 9/9 沙盒都有結果。

## 6. 檔案對照

- `ledger.json`：機器可讀嘅結果表（含每 pack 的 mod 數、Forge 版本、證據檔名）。
- `<instance>/status-*.json`：autotest 原始結果（題目 id／狀態／耗時／trace 檔名）。
- `<instance>/smoke.log`、`summary.json`：外層驅動記錄。
- `<instance>/screen.png`：跑完時嘅螢幕截圖（多數係遊戲已關，屬對照用）。
- 逐題答案全文：`<instance>/ask-*.jsonl`（在 instance 的 `minecraft/packai/trace/`，未 copy 入 repo）。
