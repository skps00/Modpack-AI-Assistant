# 乙線測試結果（2026-09-21）— 多人 registry 可行性

狀態：**完成**｜方法：實機（沙盒）｜證據：client log + server log

## 設定
- 包：FTB Skies Expert 1.12.0（Forge 1.19.2-43.4.5、336 mods server ／ 379 mods client）
- Server：沙盒專用伺服器 `C:\Users\skps9\Documents\MC_Sandbox_Servers\ftb_skies_expert_1.12.0`
  - 由官方 1.12.0 安裝複製；剔除 4 個 client-only mod（packai 自己、entity_model_features、entity_texture_features、colorfulhearts）
  - 首啟 crash 原因 = `-- MOD packai --`（client-only class 被 dist cleaner 拒）；修好後 `Done (2.765s)`
- Probe：`REGPROBE v2`（client-side，讀 `level.registryAccess()`），jar sha `d68fc6c832ca79bb`（= `packai-0.2.3.jar`）
  - 代碼 patch 存檔：`docs/plans/2026-09-21-regprobe-v2.patch`（跑完已還原，main 樹零殘留）
- 連線：client `Direct Connection` → `127.0.0.1`（online-mode 預設 ✅ 成功入 server）

## 原始數據（兩行都係同一份 probe、同一個包）
| 欄位 | 單人（整合伺服器） | 連專用伺服器 |
|---|---|---|
| regPlaced（`worldgen/placed_feature`.size()） | **545** | **-1**（throw） |
| regConfigured（`worldgen/configured_feature`） | 488 | **-1**（throw） |
| regBiome | 95 | 94 |
| regDimType | 22 | 22 |
| hitPlaced（反查 `thermal:silver_ore`） | 1 | **-1**（throw） |
| err | `""` | `IllegalStateException` |

單人：`{"placed":177,"orePlaced":46,"biomes":6,"biomesWithOre":6,"regPlaced":545,"regConfigured":488,
"regBiome":95,"regDimType":22,"hitPlaced":1,"hitConfigured":0,"ms":2,"err":""}`
多人：`{"placed":0,"orePlaced":0,"biomes":9,"biomesWithOre":0,"regPlaced":-1,"regConfigured":-1,
"regBiome":94,"regDimType":22,"hitPlaced":-1,"hitConfigured":-1,"ms":0,"err":"IllegalStateException"}`

## 結論（決定性）
1. **1.19.2（vanilla＋Forge）連專用伺服器時，客戶端只同步 `biome`／`dimension_type`，冇 worldgen feature registry**
   ⇒ `registryOrThrow(PLACED_FEATURE_REGISTRY)` 直接 throw `IllegalStateException`。
2. 所以 **乙（遊戲內 registry 做主來源）喺多人環境行唔通** ❌；**單人／LAN（整合伺服器）可行** ✅。
3. ⚠️ 之前用 `javap` 推論「登入包免費送成份 RegistryAccess ⇒ 客戶端應該有」**係錯**（送嘅只係部分 layer）。
   → 印證 SK 規則：**網上／靜態推論 ≠ 實際，必須實測**。

## 對 packai 嘅影響（設計約束）
- 甲（掃 mod jar／資料）**必須保留**做多人主要來源；乙只可以做單人／LAN 加速或交叉驗證。
- 若日後要喺多人用乙 → 需要 **server 側 companion**（server 安裝 packai 或插件回報 registry）＝新契約，要另開 plan ＋ SK 批准。
- 反轉條件（review 已列）：registry throw → 「乙唔可以喺多人用」＝**已觸發**，本結論成立。

## 未做（明確記錄）
- 未測：Forge 自己嘅 `NetworkRegistry` 有無機會補上（實測已顯示冇）；1.19.3+／NeoForge（NeoForge 1.19.3+ 有完整 registry sync，可能可行，未測）。
- 未測：跨維度、玩家中途連接（唔影響結論）。
