# 乙線：多人／專用伺服器 registry 驗證（計畫；**未開工**）

狀態：**pending（SK 未批准開工，2026-09-20 記錄）**
負責：JARVIS（Hermes）｜沙盒進行，唔碰真 instance

## 背景（一句話）
甲＝掃 mod 檔案推斷世界生成（會漏）；乙＝遊戲運行時問 Minecraft registry（準、快）。
乙已喺**單人世界**實測成功（FTB 沙盒：placed=545／orePlaced=100／biomesWithOre=94／11 ms／反查 thermal:silver_ore 命中 1）。
**未證**：玩家連**專用伺服器**時，客戶端拎唔拎到完整 worldgen registry。未證 → 唔可以升級做主要來源。

## 方法（SK 2026-09-20 指示）
1. **用官方／第三方提供嘅 server pack**（網上下載）——**唔好自己由 pack mods 砌 server**（SK 明示）
   - CurseForge 整合包多有 server pack 檔；作者官方 Wiki／GitHub／mcmod 頁嘅下載連結亦可能提供
   - 若目標包冇 server pack → 換一個有嘅包做驗證（唔一定要用 359 mod 嗰個大包，減風險）
2. 沙盒開 server（背景）→ 客戶端（沙盒 instance）連線入世界
3. 跑同一個 registry probe（單人版代碼改 server 世界用）
4. 情境：① 專用伺服器 ② LAN 開服 ③ 跨維度（至少 2 個）
5. 對比單人基準（placed／orePlaced／biomesWithOre／反查命中）

## 驗收標準（先定死）
- 客戶端 probe 出嘅清單要同 server 側一致，**或有明確 fallback**（要講清楚係咩 fallback）
- 延遲 < 50 ms
- 唔 crash／唔卡
- 有差異要逐項講清楚（**唔准講「大致一樣」**）

## 成本
- 下載 server pack＋開 server：20–40 分鐘
- probe 改造＋compile：20–40 分鐘
- 跑 3 個情境＋等載入：60–90 分鐘
- 對比＋報告：20–30 分鐘
- 合計：**約 2–3.5 小時**（若 server pack 即插即用）；風險：缺 mod／版本不合 → 可能變半天
- SK 動作：**0**（沙盒自動化；只有最後要試真實連線環境先叫 SK）

## 研究結果（2026-09-20，對真 artifact 核實；SK 指示「先做研究」）
方法：`javap` 打真 Forge 1.19.2 mapped jar
`~\.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.19.2-43.4.0_mapped_official_1.19.2\forge-1.19.2-43.4.0_mapped_official_1.19.2.jar`

**證據（客觀、可覆核）**
1. **1.19.2 完全冇 `ClientboundRegistryDataPacket`** —— 掃全 jar 8,000 個 class，**0 個引用**（逐 registry 同步係 **1.19.3+** 先加，NeoForge 1.19.2→1.19.3 primer 亦印證 registry 系統大改）。
2. 1.19.2 改為喺**登入包**送成份 registry：`ClientboundLoginPacket` 有 field `RegistryAccess$Frozen registryHolder`；建構時呼叫 `RegistryAccess.freeze()`。
3. 發送方係 server 側 `PlayerList`：佢持有 `registryHolder: RegistryAccess$Frozen`（constructor 傳入），**直接傳入登入包**；同一份亦用嚟 `TagNetworkSerialization.serializeTagsToNetwork(...)`。
4. 客戶端 `ClientPacketListener` 收包後用 `registryHolder.registries()` / `registryOrThrow(...)`（並建 `CommandBuildContext`）→ 即客戶端係**照住 server 側嘅 registry** 建立自己嗰份。

**推論（高信心，但要實測確認）**
- 玩家連**專用伺服器**時，客戶端 registry 應該**同 server 一致**（即 worldgen registry 都拎得到）⇒ **乙有機會喺多人環境一樣可行**。
- 亦解釋到點解 1.19.2 撞 mod 唔一致會直接 **connection 失敗**（"failed to synchronize registry data from server"）——因為成份 registry 一次過送＋比對。
- **殘留未知（要實測）**：server 側送到客戶端嘅 registry 是否包含**全部 layer**（worldgen layer：placed_feature／configured_feature／biome 等）——javap 睇唔到 layer 組裝細節 ⇒ 專用伺服器實測仍然係唯一乾淨嘅證明。

**對計畫嘅影響**
- 測試性質改變：由「可能唔 work」變成「**確認**（confirm）」
- 測試要點：連 server 後直接打 `level.registryAccess()` 睇 `PLACED_FEATURE_REGISTRY` 有冇 entries ＋ 對比單人基準（placed=545／orePlaced=100）
- 若確認通過 ⇒ 乙可升級做主要來源（甲保留做 fallback）

## 下一步
等 SK 講「開工」→ 先寫 implementation 細節（邊個包、server pack 來源、probe 改動點）→ 過 review → 才動手。
