# 2026-09-19 — packai「真機自動測試」計畫 **v5（Plan B：build-flag jar ＋ 沙盒 instance）**

> 狀態：**計畫（未實作）**。範圍 `forge/1.19.2`（`neoforge` PAUSED）。
> 版本聲明：只適用 **MC 1.19.2 + Forge 43.x**。
> 演進：v1 config-gated 沉睡 → v2 Prism `-w`（[反方 R1 `2:8`，已否證](reviews/2026-09-19_autotest-harness-plan-R1-opposing.md)）→ v4 縮 scope → **v5＝SK `go b` 後定案：build flag ＋ 沙盒 instance**。

## 0. 一句話

用 **build flag** 編譯一個「**帶 harness 嘅特別 jar**」，**只部署去沙盒（副本）instance**（231 mod 原封不動）→ harness 喺標題畫面自動開世界，逐條 case 用**玩家真入口**（`AiAssistantScreen.openAndAskAbout(stack)`＝JEI hold-Y 同一入口）發問 → 結果寫入現有 trace → Hermes 讀 trace 判 PASS／FAIL。
**正式發佈版**：冇 flag ⇒ harness code path 完全唔存在（零殘留、零暴露）。

## 1. 為什麼係 Plan B（實測證據）

- **Plan A（dev 環境）撞牆**：dev 副本要剔走 11 個 mod（`lazydfu`／`embeddium`／`oculus`／`rubidium-extra`／`ferritecore`／`modernfix`／`saturn`／`entityculling`／`UntranslatedItems`…）先有可能起機，**環境失真**（渲染／翻譯行為同玩家唔同）。
- **Plan B 零失真**：沙盒 instance 有全部 **231 個真 mod**（含 KubeJS／JEI／漢化）。
- **隔離**：沙盒＝`Documents\packai_dev_game` 副本；**SK 原 instance 一個 byte 都唔碰**（已驗：jar sha256 `06b5b129a114…`、mtime 2026-09-18 07:12、今日零寫入）。

## 2. 設計

### 2.1 Build flag（零殘留）
`forge/1.19.2/build.gradle`：
```groovy
if (project.hasProperty('packaiAutotest')) {
    processResources { from('src/autotest/resources') }   // 只含一個 packai-autotest.flag
}
```
- 冇 flag → jar 內冇 flag 檔 → `AutoTestHarness.active()` 永遠 `false`（連 trigger 檔都唔會讀）。
- 有 flag → 照跑。
- **唔加 config key、唔加 settings UI、唔加 lang key**（避開兩樹 parity 閘）。

### 2.2 Harness（新檔 `client/autotest/AutoTestHarness.java`，唯一新檔）
- **觸發**：`<gameDir>/packai/autotest/cases.json` 存在（第一行必須 `{"packaiAutotest":1,`  魔術前綴）。
- **開世界**（R1 F2 修正：唔用 `ClientPlayerNetworkEvent.LoggingIn`）：`onClientTick` 見到 `screen instanceof TitleScreen` → `Minecraft.getInstance().createWorldOpenFlows().loadLevel(titleScreen, <worldFolder>)`（1.19.2 真存在，R1 以 javap 核實）。
- **逐條 case**：由 `cases.json` 讀 `{id, item, nbt?, question?}` → 用**玩家入口** `AiAssistantScreen.openAndAskAbout(stack)`（`AiAssistantScreen.java:108`；`ClientSetup.java:174` 就係 JEI hold-Y 用佢）→ 真 pin JEI target、真渲染卡片。
- **結果**：唔自寫結果檔（R1 F9）——答案／卡片／事實落**現有 trace**（`<gameDir>/packai/trace/ask-*.jsonl`）；harness 另寫 `<gameDir>/packai/autotest/status-<ts>.json`（進度／完成旗標）供 driver 判完結。
- **護欄**：≤20 case／每條 180 秒／全程 20 分鐘上限；跑完寫 `DONE`；**永不重複跑**（見 DONE 即停）；只喺 flag build 存在。
- **關機**：cases 完成後（可選 flag）自動 `Minecraft.getInstance().stop()`。

### 2.3 沙盒 instance（唔碰原 instance）
- 建 `instances/packai_sandbox/`：`instance.cfg`＋`mmc-pack.json`（由 `AI_test_NFWC_DIM` 複製）＋ `minecraft` **junction** 指向 `Documents\packai_dev_game`（唔再食 3.5GB）。
- 用 `prismlauncher.exe -l <instance>` 開（唔用 `-w`，R1 F1 已證 1.19.2 無效）。
- 部署特別 jar 去**沙盒** `mods\`（直 copy，唔用 `mc_mod_deploy_jar.py`＝嗰支係保護 live instance 用；沙盒前先記 sha／備份）。

### 2.4 Driver（Hermes 側，`%TEMP%\packai_autotest_run.py`）
1. 讀 activity gate：`playing`／`using` → **拒絕開**（除非 SK 即時批准）。
2. 備份沙盒 `packai/trace/`＋`logs/latest.log`（R1 F5：唔想跑一次就清走舊 trace）。
3. 寫 `cases.json` → 開沙盒 instance（`prismlauncher.exe -l`，開完驗前景窗有冇被搶、搶到即還原）。
4. 等 `status-*.json` DONE（timeout 20 分鐘）。
5. 讀 trace 逐條判 PASS／FAIL → 出報告；還原 trace／log 備份。
6. **finally（包 crash／timeout）**：刪 `cases.json`（R1 F7）＋必要時 taskkill 沙盒 java。
7. Driver **唔會讀／印** `config/packai-client.toml`（含 API key）。

## 3. 驗收標準（寫死，開工前）

| # | 驗收項 | 判定 |
|---|---|---|
| A1 | 冇 flag build 出嘅 jar：`AutoTestHarness.active()==false`（連觸發檔都唔讀） | 靜態檢查 flag 資源唔存在＋harness 相關 class 唔入 jar |
| A2 | 沙盒起機：231 個 mod 全部載入、零 crash | `latest.log` 行數／`Loaded N mods` |
| A3 | **fix A 驗收**：木錘 → 恰 1 張合成台卡、尾段無「已隐藏」、無簡體 miss 句 | trace `check.cards`／`display.body.final` |
| A4 | 擬態（私有 pack 內容）→ 老實答「未收錄」（唔准亂編） | trace |
| A5 | SB 下界合金背包 → 卡同文字同一結論（有配方就唔准答無法確定） | trace |
| A6 | 跑完 **a. 沙盒 trace／log 還原**、b. 原 instance sha256 不變、c. 冇殘留 java | 檔案核對 |
| A7 | 成本：≤20 條 ask、跑前讀 DS 時段、印預估 | driver log |

## 4. 還原點
- `.hermes/backups/2026-09-19_dev_env/build.gradle.bak`（已建，md5 核對一致）
- 沙盒 jar 部署前：記錄 `mods\` 清單 sha256
- 原 instance：**唔郁**（本計劃零寫入）

## 5. 待 SK
- ~~方案選擇~~ → **`go b`** ✅
- 測試世界：沙盒內 `新的世界 (1)`（可換）
- 跑得密唔密：每次改動後自動跑（建議）／只喺 SK 叫時跑

## 7. v5 → v6（R2 反方判定後嘅規格補洞）

R2 比分 **4:6**：F1／F3／F5／F6／F7 判**真解決**；以下係仍然要釘死嘅位（全部具體、可實作）：

### 7.1 開世界嘅互動框（R2 新 CRITICAL）——**必須程序化處理**
`loadLevel()` 對已存在世界可能彈確認框（`askForBackup`／bundle 載入失敗／世界版本提示）。做法（dev-only、只喺沙盒）：
- 呼叫 `loadLevel` 之後每個 tick 檢查 `Minecraft.getInstance().screen`：
  - `ConfirmScreen` / `BackupPromptScreen` 之類 → **程序化按下「確認／繼續」按鈕**（`screen.children()` 內第一個 `Button`），最多 3 次；
  - 仍唔入世界 → 寫 `status` 失敗原因（唔准靜默 hang）。
- 世界優先揀**同版本建立**嘅（沙盒 `saves` 內 1.19.2 存檔）避免版本提示。

### 7.2 問題文字同 NBT 樣本（R2 F3 規格洞）
- 入口 `openAndAskAbout(ItemStack)` **唔收 question**（問題由 lang 模板 `packai.ask.item_about_id` 生成）→ **case 就係「用預設問法問某件物品」**，呢個正正係要測嘅玩家行為（JEI hold-Y）。
- **木錘 NBT 樣本來源**（決定 A3 有冇意義）：唔自編 SNBT。優先用 **JEI 真實 output stack**（`JeiRecipeCards` 由 JEI recipe 取出嘅 result，天然帶 NBT）；driver 只提供「目標輸出 id」，由 harness 用同一條 JEI 查詢取 stack。若 JEI 取唔到 → 讀該 mod 嘅配方 JSON（`data/tetra/recipes/**` result NBT）合成 stack，並喺報告標明係「合成樣本」而唔係玩家真樣本。

### 7.3 成本 delta 閘（R2 F4）
`DailyTokenUsage` 實際落 `<gameDir>/config/packai-usage.json`（沙盒真實存在）→ driver 跑前後**各讀一次**，計 delta，寫入報告；delta 超預算 → 標記失敗。

### 7.4 hook 落點同白名單（R2 F8）
- harness 唔用 `@EventBusSubscriber`（repo 零先例）；改為喺**現有** `ClientSetup.onClientTick`（`ClientSetup.java:126`）加一行 `AutoTestHarness.tick()`（一行、單一入口、易 review）。
- 白名單最終版：`build.gradle`、`ClientSetup.java`（+1 行）、`client/autotest/AutoTestHarness.java`（新）、`tests/check_autotest_flag.py`（新）、`docs/**`、（driver 留 `%TEMP%`）。**無其他檔案**。

### 7.5 焦點量度工具（R2 F6 細節）
開沙盒後用 `powershell GetForegroundWindow`／`GetWindowRect` 前後各一次，判定有冇搶焦點；搶到即用 `AttachThreadInput` 還原（同 AGENTS.md 既有做法一致），並喺報告寫 `focus_stolen=true/false`。

## 6. R1 十一條 → v5 回應（逐條）

| R1 | 異議 | v5 處理 |
|---|---|---|
| F1 | Prism `-w` 對 1.19.2 no-op | 已刪；改由 harness 由標題畫面開世界 |
| F2 | `LoggingIn` 之後唔能載入世界（雞蛋問題） | 改用 title screen ＋ `createWorldOpenFlows().loadLevel()` |
| F3 | `beginAsk` 唔存在；GUI／指令語義唔同 | 改用真 GUI 入口 `openAndAskAbout(stack)`（＝JEI hold-Y）；`/ai` 只作純文字 fallback |
| F4 | 成本護欄空（`dailyTokenLimit` 預設 0＝無上限） | harness 自帶硬上限 ≤20 條＋timeout；driver 印預估 |
| F5 | 跑一次會輪換清走 trace／log → 令現有綠閘轉紅 | driver 跑前備份、跑後還原 |
| F6 | `run_hidden.vbs` 唔存在；焦點量度有盲區 | 唔用 vbs；用 `prismlauncher.exe -l`＋開完獨立量度前景窗 |
| F7 | 清理步驟唔完整（crash 都必須清） | driver `finally` 刪 trigger＋kill 沙盒 java |
| F8 | 白名單唔齊 | 白名單：`build.gradle`、`AutoTestHarness.java`、`tests/check_autotest_flag.py`、`docs/plans/**`、driver（`%TEMP%`） |
| F9 | 唔應自寫結果檔（重複建設） | 直接讀現有 trace；harness 只寫 `status-*.json` |
| F10 | 路徑／保留／碰撞 | 一律 `<gameDir>/packai/autotest/`，檔名含時間戳 |
| F11 | 依賴咗未 commit 嘅 `dailyTokenLimit` 工作 | v5 **零依賴**未 commit 工作 |

## 8. v6 → v7（R3 最後一輪 5:5 之後嘅三個具體修正；**唔再開新 review 輪**，依 SK 3–4 輪上限 → 停手問 SK）

### 8.1 ⚠️ A3 斷言面錯咗（**直接影響 fix A 驗收**，必改）
- `logic/RenderRecipeCardsAskTool.java:126` 嘅 `check.cards` **喺上限過濾之前**發（`PER_CALL_CAP=6` 喺 `:23`，套用喺 `:161-167`，`offerEmission` 喺 `:184`）⇒ 用佢斷言「恰 1 張卡」**結構上永遠紅**。
- **改用** `AskService.java:521` 嘅 **`render.cards.final`**（已存在、係最終 render 面）做 A3 斷言來源；`check.cards` 只作診斷附註。

### 8.2 互動框類別名同按鈕路徑（R3 修正）
- 1.19.2 **冇** `BackupPromptScreen`；真名係 **`BackupConfirmScreen`**，而佢 **`extends Screen`（唔係 `ConfirmScreen`）** ⇒ 只判 `instanceof ConfirmScreen` 會**漏佢而 hang**。
- 做法：同時處理 `ConfirmScreen`（`addButtons` 第一粒 Button 嘅 callback ＝ `accept(true)`）＋ `BackupConfirmScreen`（按「繼續／Backup」）；**入到世界即停止再按**（guard）；觸發源係 `forgeLifecycle=experimental`（唔係版本），所以「揀同版本世界」唔係解法——**要處理框，唔係避開框**。

### 8.3 NBT 樣本嘅**公開**取法（R3 修正：原本寫嘅 class 係 package-private，白名單內做唔到）
- 可行兩步：`JeiRecipeCards.forItem(<bare id>)` → `card.outputs()`（都要親核簽名；`JeiRecipeLayoutCollector`／`CollectedLayout`／`itemStacks()` 係 package-private，**唔准用**）。
- 若 `forItem` 取唔到 Tetra 木錘（`tetra:modular_double`＋oak NBT）→ 退回讀 jar 內 `data/tetra/recipes/hammer/oak.json` 嘅 `result` NBT 自建 stack，並喺報告明標「合成樣本」。

### 8.4 剩餘全部係「**必須真跑才解**」（R3 明列）
1. 沙盒（230 jar ＋ harness jar）能否起機、`Loaded N mods`
2. `prismlauncher.exe -l packai_sandbox` 實跑行為（搶焦點／帳號互動）
3. 沙盒 `saves` 世界版本＋確認框實際種類
4. `JeiRecipeCards.forItem` 對 Tetra 木錘嘅真實行為

⇒ **建議：唔再輪 review，直接用最小實作去解呢四項**（真跑一次 = 唯一裁判）。
