# 顧問引擎 plan v4 — 第 4 輪 review（反方 → 正方 → 中立裁判）

- 日期：2026-10-10
- 對象：`docs/plans/2026-10-05-advisory-engine-recipe-graph.md`（v4）
- 題庫：`docs/plans/2026-10-05-player-question-set.md`（28 條）
- 流程：① 反方（獨立 subagent）→ ② 正方（獨立 subagent）→ ③ 中立裁判（Hermes 親自核實後判）
- **上限**：本輪＝第 4 輪（SK 規則上限 3–4 輪）。以下係最後一輪，判決後**唔再開新輪**。

> ⚠️ 方法聲明：兩份 reviewer 嘅數字／行號**我逐條親自查過**（下 §2 核實表）。凡查唔到、或者誇大嘅，已在下文明確降級或撤回——唔會照抄。

---

## 1. 反方（最強攻擊，已按我核實結果修正）

| # | 指控 | 我核實結果 | 最終嚴重度 |
|---|---|---|---|
| A1 | **A5（我有 X，差咩材料）冇資料路徑**：`GameContextCollector` 只讀手持／副手／hotbar（hotbar 仲要 `includeHotbar=true` 才有；`collect()` 默認係 `collect(false)`），全背包要靠玩家手動揀一件（`InvPickScreen` 註解：「one at a time」）。plan §1 承諾 A1–A5、§6 又要 A5 過關，但 §2／§3 完全冇提背包來源。 | **成立（但唔係結構性）**：現況確實冇「完整 36 格背包」嘅數據路徑。但 client-side 讀 `player.getInventory()`（36 格）本身係現成 API、零成本 → 係 plan **漏寫設計**，唔係做唔到。 | **中高**（plan 缺陷，非不可能） |
| A2 | **H2–H3「階段／tier」冇數據源**，唯一示範來源（Mekanism wiki）仲要係 plan §1 禁止嘅「pack 專屬硬編碼」。 | **大部分唔成立**：repo 已經有 `logic/RecipeUnlockGates.java`（**705 行**；讀 advancement literal id／`RecipeStages`（GameStages）／KubeJS `isAdvancementDone` 啟發式）＋ `logic/PlayerUnlockStatus.java`（**370 行**；runtime 玩家成就進度，含 `Progress.UNREADABLE`）。plan **冇引用呢啲既有基建**係事實，但「冇數據源」唔成立。 | **低–中**（變成「plan 未寫清用邊個既有來源」） |
| A3 | **冇 effort 估算、冇 go/no-go 數值門檻、冇失敗定義**（§5 只列量度項，冇「M/N 要幾多才 GO」）。 | **成立**（我逐段讀過：98 行 plan 內確無門檻／人日／失敗定義）。違反 AGENTS「開工前先寫驗收標準」精神。 | **中** |
| A4 | §3 契約對最常見（KubeJS 機率）鏈退化成「未有資料」，而 **P0 唔量測覆蓋率**。 | **一半錯**：§5 ② 明明有量「固定比例 vs 機率輸出比例 M/N」。**冇門檻**係真，但「唔量測」係錯。 | **低**（降級） |
| A5 | **最大賣點（檢視器抽象）零驗收**：§6 只喺 ATM8＋NWFC 兩包跑，但 §4 特別列出嘅 REI-only（universio）／EMI-only／無檢視器包都唔喺驗收範圍。 | **成立**：`packai_sandbox_universio` 實測 **JEI=0、REI=1**（我 ls 過 mods）＝plan 自己寫嘅降級案例，但 §6 驗收冇佢。 | **中** |
| A6 | 46,423 配方檔規模風險：§7 對策（背景建圖、分批 cache）**冇預算數字**。 | **數字真實**（見 §2），但 severity 被高估：census 同時顯示 **未壓縮只 15.6 MB**、可全量 parse；真正未知係 JEI runtime 枚舉速度，而 §5 ⑤ 已經要量。 | **低–中** |

## 2. 裁判親自核實（唔靠 reviewer 轉述）

| 項目 | 我點核 | 結果 |
|---|---|---|
| plan 引用嘅 JEI 接駁點 | 開檔睇 | `client/jei/JeiLookup.java` ~739 有 `recipes.createRecipeLookup(type).includeHidden().get()` ✅；`client/jei/JeiInfoPages.java` ~99 同 ✅ → **plan 引用真確** |
| 「現 14 個 AskTool」 | grep 註冊 | 數到 **14** ✅ |
| 5 個本地樣本包 | ls instances | 全部存在，jar 數同 plan 一致（380／155／231／233／177）✅；`universio` **JEI=0／REI=1** ✅ |
| A5 背包路徑 | 開 `GameContextCollector.java:50-78`、`InvPickScreen` 註解 | 只有手持／副手／（可選）hotbar 9 格；全背包要人手揀一件 → **A5 缺口成立** |
| H 數據源 | 開 `RecipeUnlockGates.java` 頭 60 行、`PlayerUnlockStatus.java` | 讀 advancement literal id／RecipeStages／KubeJS 啟發式；有 runtime 玩家進度＋`UNREADABLE` → **既有基建存在** |
| 配方圖係唔係由零起 | grep `PackIndex.java` | **`:25` 註解寫明 "light pack graph"**；`RECIPE_EDGE` = `item:X -[recipe_needs]-> item:Y`（:52-54）；建邊 :1745 起；`isCompactCycle` :1357-1360，`AskEngine.java:507-513` 讀邊 → **圖／循環偵測已經有雛形** |
| honest-miss 實作 | `wc -l` | `logic/HonestMiss.java` **271 行**存在 ✅（唔係只加免責句） |
| 規模數據 | 開 census json | `2026-10-06-pack-recipe-census.json`：ATM8＝**379 jar／216 有配方／46,423 配方檔／15.6 MB 未壓縮／219 namespace／584 recipe type**；top：crafting_shaped 12,072、crafting_shapeless 5,429、alchemistry:fusion 3,481、stonecutting 2,839 ✅ |
| 門檻／effort | 逐段讀 plan | **冇**（A3 成立） |

## 3. 正方（有效辯護）

1. **範圍大幅收窄**：9 類 → A＋H；defer 清單明寫（§1）。
2. **§2 檢視器抽象唔係空談**：踩住已存在嘅 JEI 接駁（上面已核實）；`JeiLookupAskTool` 已註冊。
3. **「配方依賴圖」唔係由零起**：`PackIndex` 已有 light pack graph、`recipe_needs` 邊、循環偵測、玩家文字化錯誤訊息 → **成本比外觀低**（本輪最重要發現）。
4. **honest-miss 已落地**（`HonestMiss.java` 271 行）。
5. **樣本包選得對**：5 個本地包已齊，仲特別包含 REI-only 降級案例同（下載清單）EMI-only／無檢視器極端案例 → 覆蓋面廣。
6. **契約（§3）符合 SK 鐵則**：值＋來源＋未計入因素、唔准平均、唔准當機率＝0。

## 4. 判決（中立裁判）

**比分：正方 7 : 反方 3**（R1 3:7 → R2 4:6 → R3 4:6 → **R4 7:3**，趨勢明顯向好，但**未達 8:2**）

- 反方**留下來嘅載重指控只有 3 條**，而且全部係**編輯級／設計補寫級**，唔係「方向錯」：
  1. **A5 背包來源未寫**（要寫清：讀 36 格？只讀 hotbar？NBT 要唔要？）
  2. **P0 冇數值門檻、冇 effort／優先次序、冇失敗定義**（例如：M/N < 幾多就唔做概率部分；建圖 > 幾秒就轉背景／分批）
  3. **驗收冇覆蓋新抽象嘅降級案例**（要加 universio（REI-only）＋至少一個 EMI 或無檢視器包）
- 反方其餘指控：A2 被既有基建推翻大半；A4 一半錯；A6 severity 高估（15.6 MB 可全量 parse）。
- 正方最脆弱嘅一環（正方自己都認）：**P0 數據根本未存在**——v4 係一個「結構良好嘅前置條件」，交付價值未經實測。

**最大未知（最貴嘅未知）**：JEI 喺 380 mod／584 recipe type 環境下，client 端**逐類枚舉＋建圖**嘅**實際時間／記憶體／帧率影響**。呢個係唯一可以推翻整個 Phase 1 嘅東西；plan §5 ⑤ 有排但未跑。

**反轉條件**：
- 若 P0 顯示「固定比例鏈佔壓倒多數（M/N 高）＋建圖 ≤ 可接受秒數」→ 反方 A4/A6 全滅，計劃變 8:2 以上。
- 若 P0 顯示建圖會卡（例如 JEI 枚舉 46k 配方 >數十秒 或 記憶體爆）→ 計劃要重新收窄到「只答單件物品、唔建全圖」。

**建議（我嘅立場）**：**有條件批准（conditional go）**——照做，但要先補 3 條修訂入 plan（上面 1–3），並且**P0 照跑**（P0 係量測，唔需要等批准就先可以做）。若 SK 想保守：只批准 P0，睇完數據再批實作。

**上限聲明**：本輪已用盡 3–4 輪上限，**唔會再開第 5 輪**。SK 覆核後直接決定：批准（連修訂）／只批 P0／停手換方向。
