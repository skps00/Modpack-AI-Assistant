# 乙線實作細節（多人 registry 驗證）— implementation plan v1

狀態：**待 review → 待 SK 批**｜日期 2026-09-20｜前置研究見本檔下方（javap 證據）

## 一句話
喺**專用伺服器**（官方 server 安裝）連線後，量客戶端 `level.registryAccess()` 嘅 worldgen registry，
同**同一個包、同一個版本嘅單人基準**（FTB 沙盒實測：placed=545／orePlaced=100／biomesWithOre=94／反查 thermal:silver_ore 命中 1）逐項對比。

## 測試主體（先用 FTB Skies）
- 沙盒：`packai_sandbox_ftb`（1.19.2／Forge **43.4.5**／358 個 mod jar）
- **先做一步核實**：用官方 manifest（`api.modpacks.ch`）比對沙盒 mod 清單，定出**確實嘅 pack id 同版本**
  （已知：pack 103 = FTB Skies，最新版本 manifest 304 個 mod ≠ 沙盒 358 ⇒ 要配對正確版本，或就係「FTB Skies Expert」另一個 id）
- 若配對唔到 ⇒ 改用 **Star Technology（155 mods，最細）** 或 **ATM8**（ATM 團隊官方發 server pack）

## Server 安裝（跟 SK 指示：用官方 server 檔，唔自己砌）
- **首選**：官方 FTB manifest（`https://api.modpacks.ch/public/modpack/<id>/<ver>`）——公開免登入、同 FTB App 裝 server 用同一份清單；下載 `type=mod/config/script` ＋ Forge 43.4.5 server installer
- **次選**：ATM8 CurseForge 官方 server pack（要 CF 存取，可能要 SK 出手 ⚠️）
- 安裝位置：**沙盒目錄**（例：`PrismLauncher.../instances/packai_srv_ftb/`），**零觸碰真 instance**
- 設定：`eula=true`、`online-mode=true`（用 SK 正版帳號登入客戶端）、RAM 8–10 GB、`server.properties` 用預設、世界用新 world
- 起動驗證：log 見到 `Done (xx.xxxs)! For help, type "help"` 才當 ready

## 客戶端
- 用現有沙盒 instance（同 pack、同 mod 清單）＋ packai jar（`autotest-dev-0.2.3.jar`）
- 連 `127.0.0.1:25565` → 入 world → 等 chunk 載入（≥400 ticks）→ probe 出數

## Probe（要經 cursor-agent 改；只准改白名單檔）
- 檔案：`forge/1.19.2/src/main/java/com/skps9/packai/client/ClientSetup.java`（唯一白名單）
- 只做**客戶端**（`FMLClientSetupEvent`／世界載入後 tick）；**唔准** server 側執行
- 輸出（寫 log ＋ chat）：`REGPROBE v2 {"side":"client","dim":"minecraft:overworld","placed":N,"orePlaced":N,"configured":N,"biomes":N,"biomesWithOre":N,"hitPlaced":N,"ms":N}`
- 反查用 `thermal:silver_ore`（同單人基準一致，可直接比）
- 跑完**即刻還原**（同上次一樣，零殘留），jar 重新 deploy 前後 sha 要記錄

## 驗收標準（v2；review R1 修正——原本「±0」寫法有缺陷）
⚠️ 關鍵修正：原基準數字（placed=545／orePlaced=100／biomesWithOre=94）係喺**某個世界／已載入 chunk**量到嘅，
會受 seed／載入範圍影響 ⇒ **唔可以**直接要求專用伺服器跑出同一數字（會產生假失敗）。
改為分兩層，**主判準同世界無關**：

**主判準（registry 層，必須完全相同）**
1. `registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY).size()` → **同包同版本下，客戶端 == 單人**（都係 registry 內 entries 數，同世界／seed 無關）
2. 同樣報 `CONFIGURED_FEATURE_REGISTRY` (size)、`BIOME_REGISTRY` (size)、`DIMENSION_TYPE_REGISTRY` (size)
3. `registryOrThrow(...)` **唔可以 throw**（throw = registry 唔存在 → 直接結論「多人唔可用」）
4. 反查 `thermal:silver_ore` → 要**搵得到**對應 placed feature（hitPlaced ≥ 1）

**輔判準（chunk 觀察層，只作參考，唔可以當 fail 條件）**
5. 已載入 chunk 內觀察到嘅 placed features／生態域有礦數（會因世界而異）
6. `ms < 50`、無 crash／error stack

**結論寫法（唔准含糊）**
- 主判準全過 → 寫「乙可以喺多人用（1.19.2 專用伺服器已驗）」
- 任何一項唔過 → 寫「乙唔可以喺多人用」，並列出實際數字

## 情境（最少 3 個）
1. 專用伺服器（主要）
2. LAN 開服（整合伺服器，同單人一樣 = 對照組）
3. 跨維度（主世界 → 另一個維度，例如 Nether／mod 維度）

## 成本／風險
- 時間：約 2–3.5 小時（安裝 40–60 分鐘、probe 改造＋compile 30 分鐘、3 個情境 60–90 分鐘、報告 30 分鐘）
- 風險：① 版本配對唔到 → 多花 30 分鐘試 ② FTB manifest 檔多（1,057）→ 下載慢 ③ server 首啟撞 mod → 換細包（Star Technology）
- **還原**：全部喺沙盒新目錄；刪目錄即可還原；真 instance（`AI_test_NFWC_DIM`）零觸碰
- SK 動作：**0**（最後如要試 SK 自己連線環境先問）

## 未開工前要 SK 答
- 用邊個包（FTB Skies／Star Technology／ATM8）？我建議先 FTB（已有單人基準）


## 結果（2026-09-21）：見 `2026-09-21-registry-multiplayer-result.md`
結論：專用伺服器唔同步 worldgen feature registry ⇒ 乙只適用單人／LAN。
