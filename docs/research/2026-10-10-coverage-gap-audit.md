# 覆蓋缺口審計（2026-10-10）

- 委託：SK「你看看仲有什麼我們沒有覆蓋到的」
- 方法：**逐項查真 artifact**（code／config／plan 檔／git／測試登記），唔靠印象
- 對照基準：題型研究 21 類（`2026-10-10-player-question-research.md`）＋`docs/TEST_SCOPE.md`＋`docs/plans/*`＋`git branch/tag`＋config 42 開關

---

## 1. 題型覆蓋（21 類 vs 實作）

### ✅ 已有工具／資料
| 類 | 靠咩 | 證據 |
|---|---|---|
| 材料／依賴（A） | `JeiLookup`＋配方卡（14 個 AskTool 之一） | `logic/*AskTool.java` |
| 取得／世界（E） | `AcquireAskTool`／`WorldgenLookupAskTool`／`QuestFetchAskTool`／`GuideFetchAskTool` | 同上 |
| 用途／效果（Q，部分） | `PurposeLookupAskTool`／`ConsumeUseAskTool`／`TetraUseAskTool`／`ToolBuildAskTool`／`RepairLookupAskTool` | 同上 |
| 附魔／維修 | `EnchantLookupAskTool`／`RepairLookupAskTool` | 同上 |
| **上網查（已確認真接線）** | `logic/WebSearch.java`（Tavily／Serper）→ **`AskEngine.java:705` 喺送 LLM 前呼叫** `WebSearch.search(question, focus, held)`，命中經 `formatForLlm` 變 `webBlock` 餵 LLM | 唔係 AskTool（14 個 tool 冇用 web），係**前置接地**；要玩家自己開 `allowWebSearch`＋填 API key |

### ❌ 完全冇（按需求排序）
| 類 | 需求證據 | 缺口性質 |
|---|---|---|
| **農場／自動化設計** | YT 20.68M／17.25M；wiki 教學 21% | **冇工具、冇資料、冇格式**（要步驟式輸出，唔係卡片） |
| **能力反查**（有咩可以做到 X） | 「near-infinite storage」真問題；Jade 70M | 14 個 tool **全部要 item id** → 冇「由需求反查」入口 |
| **按鍵／控制** | ViewBoard mod；YT HotKeys 1.86M；bili 338k | 冇工具（**但我已證明可離線抽**：`tools/extract_keybinds.py`） |
| **數值／效率**（RF/t、per hour） | iron farm 片標題寫「1300+/h」；ATM10 電源片 786k | 包內**冇資料源**（Patchouli 2.4%、config 87/848、tooltip 要 runtime）；**有條件**靠網頁接地（要開 `allowWebSearch`＋key） |
| **比較／推薦** | 「邊個發電機最好」 | 冇（要先有數值層；網頁接地可補部分） |
| **生成條件**（mob spawn） | YT「Maximize Mob Spawning」1.65M | worldgen 有；**mob spawn 條件係 Java code** → 冇（網頁／模型知識可補） |
| **運作／維持**（燃料／冷卻／安全） | **FTB tag 最高票問題** | 冇工具（Patchouli 只有部分） |
| **儲存／物流** | wiki 分類有 Storage & Logistics | 冇（含故障排查） |

---

## 2. 資料來源缺口（實測）

| # | 缺口 | 實測證據 |
|---|---|---|
| D1 | **包內冇機器數值** | Patchouli 3,741 檔只有 89 檔（2.4%）提數值，且係散文；config 848 檔只有 87 檔有數值行 |
| D2 | Patchouli 能力句**精準度低** | 7 條真查詢命中 5（71%），但多為偶然命中（例：lux 匹配到「無限儲存」） |
| D3 | **KubeJS 改配方未入任何量測** | ATM8 284 檔／E9E 1,013 檔未解析（P0 離線部分已聲明） |
| D4 | **離線索引天生唔完整** | demo 實測：vanilla 配方（喺 MC 本體）同 Forge **runtime 生成 tag** 都讀唔到 → anvil 只剩 Blue Skies 一條、osmium tag 只展開 1 個候選 |
| D5 | 冇 tooltip 抽取路線 | 玩家所謂「規格」多數喺 tooltip／GUI（要開遊戲量） |

---

## 3. 工程／風險缺口

| # | 缺口 | 證據 | 風險 |
|---|---|---|---|
| E1 | **冇 CI** | `.github/workflows` **唔存在**；`docs/RELEASE.md` 仲寫「CI (if any)」 | push 冇自動 compile／test gate |
| E2 | **遊戲內回歸慢** | `TEST_SCOPE.md §4`：harness「每個 JVM session 只讀一次 `cases.json`」→ **每次測試要重開遊戲** | 改動驗證成本高 |
| E3 | **Prompt injection 未設防** | KubeJS／Patchouli／quest／lang 文字，＋**網頁結果**（`AskEngine:705` → `webBlock`）都係不可信輸入，直接餵 LLM；grep 只見 API key sanitize，**未見輸入文字防護** | 假資料／指令注入（尤其玩家可自己開網頁接地） |
| E4 | **啟動成本未量** | `scanModJars`／`kubejsMechanicScan`（402 乾淨機制）對啟動／FPS 影響**冇數字** | 玩家體感（ATM8 380 mods） |
| E5 | **冇遙測／回饋迴路** | grep 冇 thumbs／report／rating 機制 | 答錯唔知 → 冇得改善 |
| E6 | **多人／伺服器情境未驗** | `mods.toml` 依賴 side=CLIENT；測試登記只有單機沙盒 | 自訂 pack／伺服器限制未測 |
| E7 | 語言只有 3 種 | `lang/`：en_us、zh_cn、zh_tw | 其他語言玩家（低優先） |

---

## 4. 規劃中但未落地（已有 plan 檔）

| Plan 檔 | 狀態 | 影響 |
|---|---|---|
| `docs/plans/summon-entity-recipes.md` | **PLAN ONLY — 等你講「開始」** | 召喚實體（非物品）配方可能答「無產物」 |
| `docs/plans/tool-modifier-read.md` | TM0+TM1 完成；**TM2 Tinkers deferred**；TM3 最細 slice | 工具改裝資料未齊 |
| `docs/plans/full-item-index.md` | in progress（WP3） | 物品搜尋速度／覆蓋 |
| `docs/plans/guidebook-index.md` | Phase B code complete；**runtime verify 等「tests OK」** | 說明書索引未認證 |
| `docs/plans/accuracy-first-next-wave.md` | PLAN LOCKED W1→W5；**WP5 GUI remake 未做** | 準確度波未跑完 |
| `docs/plans/four-issue-backlog.md` | 4 題（部分完成） | — |

（註：`git branch --no-merged main` **空** → 冇散落未合併嘅工作；20 條 branch 全部已合併。）

---

## 5. 產品／發佈缺口

| # | 缺口 | 證據 |
|---|---|---|
| P1 | **從未上架** | CurseForge id 1643097 **未發佈**；git tag 只到 `v0.1.13`，**現時 0.2.3 冇 tag／冇 release** |
| P2 | 冇 mod icon（連 promo CTA 都缺） | 未取 |
| P3 | 冇上架後支援渠道 | 未有 issue tracker／Discord 政策 |
| P4 | 需求研究全部係**代理指標** | 因為冇真實用戶（YT／wiki／Arceus 都係他人玩家） |

---

## 6. 刻意唔做（已知，唔算缺口）

技術支援類（安裝／崩潰／伺服器）｜語音／mic 線 HOLD（等新 mic）｜NeoForge 1.21.1 暫停（issue #20）｜1.12.2／1.20.1／Fabric 版本線｜技術支援診斷線 SHELVED（SK 2026-10-05）

---

## 7. 最高價值三個缺口（我嘅判斷）

1. **農場／自動化設計**：需求最大（研究兩輪證據一致）、**完全冇**、而且輸出格式要改（步驟式）。
2. **上架＋回饋迴路**：而家全部需求證據都係代理指標，冇一個真實玩家問題係嚟自我們自己嘅用戶。
3. **CI ＋ 快回歸**：冇 CI、每次遊戲內測試要重開遊戲 → 改動風險同成本都高，會拖慢之後每一項。

## 8. 建議次序（如果要我揀）

1. **快贏**：按鍵（K1）——離線、今日可完成、需求有實證
2. **結構**：能力反查（J）——補上「由需求反查」入口，係 T 類問題嘅共同前置
3. **治理**：CI（E1）＋ harness 加速（E2）——令之後每項改動可驗證
4. **產品**：上架（P1）＋回饋迴路（E5）——攞真實需求，取代代理指標
