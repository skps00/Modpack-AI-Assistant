# 2026-09-19 — packai「Pack 內容感知 + 來源誠實」**v3（roadmap ＋ 分拆式實作閘）**

> 狀態：**計劃（未實作）**。範圍 `forge/1.19.2`；版本聲明：只適用 **MC 1.19.2 + Forge 43.3.5 + KubeJS 6.x + JEI 11.8**。
> v1＝`96e4a0906b53`（反方判 **正方 3 : 反方 7**，報告 `docs/plans/reviews/2026-09-19_pack-content-awareness-plan-R1-opposing.md` 共 **18 條**異議）。
> **v3 逐條回應（§9），並按 O7 改正架構：本檔只做 roadmap，每個 phase 實作前另出 phase plan＋獨立 review 閘。**

## 0. 目標（不變）
令 packai 分得清「pack 自加內容」同「原廠 mod 內容」，答得出 pack 自加物品取得鏈；資料不足時**老實講**。

## 1. 稽核（v3 修正數字，全部親手重測）

| 項 | 實測事實 | 缺口 |
|---|---|---|
| KubeJS | 只掃 `{startup,server,client}_scripts`；`PackAiConfig` 明文「Never scans kubejs/assets or kubejs/data」 | 本 pack `kubejs` 樹 374 MB／9,144 檔；內容主要在 data/assets |
| kubejs-only 命名空間 | **28 個**（jar 提供 224 個 ns；kubejs 目錄 55 個 ns）※v1 寫「32」係 data/assets 重複計 | 冇「ns → 提供者」對應 |
| jar 掃描成本 | **231 個 jar 目錄列舉 = 0.34 s**（實測） | v1 上限（4s／300 jar）過保守、headroom 只得 1.3× |
| jar 事實抽取 | `JarLightIndex`：`MAX_RECIPES_PER_JAR=200`／`MAX_LOOT_PER_JAR=150`（`:55-56`）；`scanModJars` 預設 **false**（`PackAiConfig.java:442`）、`ensure()` 早退（`:69-73`） | 每 jar 上限丟大量事實；預設關＝今日唔行 |
| 配方材料 key | `extractIngredients`（`:522-534`）只收 `ingredient/ingredients/key/input/inputs` | **冇收 `base/addition/template`** ⇒ smithing 類（jar 內 96 條）只有 result 冇材料＝SB 文字 miss 真因 |
| Tetra 內容 | materials／schematics 有掃 | modules／improvements／repairs／synergies 未讀 |
| 物品存在 | `ItemIndex` 已 runtime 掃 `Registry.ITEM`（`:266`） | 「存在」≠「取得鏈」 |
| 來源標籤 | `displaySrc` → `packai.label.src.<x>`（`AskReplyScrub:812`） | 冇 pack 值；新 label 撞兩樹 parity 閘 |
| 註冊來源 | `kubejs/startup_scripts/**` 內 `StartupEvents.registry('item')` 實測多檔（例 `golden_age/dlc_template_item_register.js`：`registry('item')` ＋ **222 個 `golden_age:` id 字面**）；`archotech_void_scythe` 字面出現在 **13 個 kubejs 檔** | regex 抽取唔可靠（動態 id）→ 用 **runtime registry** 為準 |
| 網絡／私隱 | `web.allowWebSearch` 預設 **true**；`knowledgeUrl` 指向 SK 公開 GitHub | 新功能必須明確預設關＋獨立同意 |

## 2. Roadmap（每 phase 實作前另出 phase plan、獨立 review、獨立部署）

| Phase | 範圍 | 前置閘 | 真機驗收（**分批，唔一次過**） |
|---|---|---|---|
| **P0** | fix A 收尾＋工作樹歸屬核實 | 逐檔 md5 清單交 SK | 2 問（木錘／擬態） |
| **P1** | 命名空間來源索引（**擴充現有 `PackIndex`／mechanic-cache，唔另起爐灶**） | phase plan＋review | 1 問（亞巴頓↔木棍來源）＋trace 斷言 |
| **P2** | 配方材料 key 根因修（`base/addition/template`）＋SB 回歸 | phase plan＋review（細） | 1 問（SB 背包） |
| **P3** | Tetra 六層＋包作者文字（lang／tooltip） | phase plan＋review | 1 問（亞巴頓取得鏈） |
| **P4** | 來源標籤（`AskResult.provenance` 欄位；**零新 lang key**） | phase plan＋review＋SK 決定文字標籤否 | 1 問＋trace |
| **P5** | Fallback（**級 2 重新定義**：只用本機 jar 內文件；上網層＝另案，預設關） | phase plan＋review | 負控（假 id 唔准編） |
| **P6** | （可選，SK 批准才做）`kubejs/data` 內容解析（loot／advancement／worldgen） | phase plan＋review | 1 問 |

**P2 排前**：幾行代碼、直接修一個已驗收失敗嘅實錘（SB 文字 miss），成本最低、可即時回報。

## 3. 共通硬約束（不變）
零玩家動作；設定 gate＋硬上限＋key 去重；零硬編碼包名；NL 一律 lang key（缺 key → `en_us`）；log 全 ASCII；trace 只加新欄位；唔碰 `neoforge`／prompt 政策段／`shouldDropFrameCard` 簽名；掃描 async＋可中斷＋有預算（**上限一律由實測數字定，唔靠估**）。

## 4. config（v3：單一命名 `contentIndex.*`，全部按實測定上限）
| key | 預設 | 值（依據） |
|---|---|---|
| `contentIndex.enabled` | true | 與 `scanModJars` **完全解耦** |
| `contentIndex.maxJars` | 600 | 231 → 2.6× headroom（實測 0.34s） |
| `contentIndex.maxMb` | 4 | 只存 ns→provider 字串 |
| `contentIndex.timeBudgetMs` | 2000 | 實測 340ms，×5 安全邊際 |
| `contentIndex.packContentLang` | true | Phase 3，附檔數／MB 上限（kubejs/lang 檔數上限 4000／8 MB） |
| `docsLookup.network` | **false** | 上網＝另案，需 SK 明確開＋沿用 `KnowledgeRemote` 契約 |

## 5. 驗收標準
每 phase：compile 成功；harness 全綠（新增 ≥1，**且 phase 內新增嘅 python 閘要有本機鏡像檢查**）；`tests/check_*.py` 零新增紅（baseline 121＝120 綠＋1）；`check_internal_label_parity.py` 綠；兩棵樹 lang key 數一致；新增 CJK literal＝0。真機：**每 phase 最多 2 問**（避免一次過驗多個改動、fail 唔可歸因）。

## 6. 還原點與部署
每 phase：`.hermes/backups/2026-09-19_<phase>/`（逐檔 copy＋md5）。部署只用 `mc_mod_deploy_jar.py --target packai --jar <新 jar>`（關 game／自動 backup／sha256）。**明文：部署＝整棵工作樹狀態**（P0 前置清單要 SK 確認）。

## 7. Review 要求
Roadmap：反方→正方→中立裁判，8:2 為閘，上限 3–4 輪。每個 phase plan 亦要獨立 review。實作一律 cursor-agent；Hermes 親驗；實作後 code review 兩輪。

## 8. 待 SK 決定
1. **P0 工作樹歸屬清單**（部署＝整樹，要你確認邊批改動一齊出）。
2. **P4 文字標籤**：要唔要動 neoforge 樹**只加 lang 字串**（3 key × 3 檔，零功能改動）；唔要就用「`AskResult` 欄位＋UI」方案。
3. **P6** 做唔做（`kubejs/data` 內容解析）。
4. 上網 fallback（`docsLookup.network`）永遠預設關？

## 9. R1 十八條 → v3 逐條回應

| # | 反方異議（severity） | 核實 | v3 點改 |
|---|---|---|---|
| O1 | 新 label 必破兩樹 parity 閘、同「唔碰 neoforge」死鎖（CRITICAL） | ✅ 真（閘比兩樹 token＋6 個 lang 檔） | P4 改成 `AskResult.provenance` 欄位（零新 lang key）；文字標籤＝要 SK 批 neoforge lang-only |
| O2 | `JarLightIndex` per-jar 上限 → 「單一來源」只 ~49% 覆蓋（HIGH） | ✅ 真（200／150） | 刪「大對齊」；P2 只做根因修＋SB 回歸 |
| O3 | smithing 有 4 行解法，plan 揀最貴機器（HIGH） | ✅ 真 | P2 提前、做法＝`extractIngredients` 加收 `base/addition/template` |
| O4 | 靠 `JarLightIndex`，但預設關（HIGH） | ✅ 真（`:442`／`:69-73`） | P1 明寫解耦＋觸發＝首次開 Ask＋失敗靜默 |
| O5 | P0 規模低估（67 未 commit 檔；「10 檔 backup」覆蓋唔到真 diff）（HIGH） | ✅ 真 | P0 加前置：全樹歸屬清單＋md5＋SK 確認；明文部署＝整樹 |
| O6 | S1–S6 綁一次真機 session，fail 唔可歸因（HIGH） | ✅ 真 | 真機驗收拆成每 phase ≤2 問 |
| O7 | 5 phase 綁一個 plan 違反 repo 契約（HIGH） | ✅ 真 | 本檔降格做 roadmap；每 phase 另出 plan＋獨立閘 |
| O8 | P2 冇硬上限（kubejs 374 MB／9,144 檔）（MED-HIGH） | ✅ 真 | 新 key 附檔數／MB／時間上限；超即停 |
| O9 | 「modId ≠ ns」未解決，例子選錯（MED-HIGH） | ✅ 真 | 唔再假設相等：jar ns（權威）同 modId 分開記錄，對唔上標 `unknown` |
| O10 | runtime 註冊 regex 唔可靠；引嘅註冊檔名錯（MED-HIGH） | ⚠️ 半對：`dlc_template_item_register.js` 確實有 `registry('item')`＋222 個 id；但 `archotech_void_scythe` 字面只喺 13 個檔（Tetra 資料檔為主） | 改為 **runtime registry 為準**（`ItemIndex` 已做）；檔案只作旁證；regex 只做 fallback |
| O11 | 「32 個 kubejs-only ns」複核唔到（得 28）（MED） | ✅ 反方對（我重測＝28） | §1 已改正 |
| O12 | 成本／上限冇量測依據（實測 0.31 s）（MED） | ✅ 反方對（我重測 0.34 s） | §4 上限全部按實測重定 |
| O13 | Fallback 級 2 空洞（patchouli 屬級 1；「標版本」冇取得機制）（MED） | ✅ 真 | 級 2 重定義＝只用本機 jar 內文件；真上網層拎出嚟做另案（預設關＋需 SK 開） |
| O14 | 私隱論述不完整（`allowWebSearch` 預設 true；`knowledgeUrl` 指向公開 repo）（MED） | ✅ 真 | §1 加網絡現況；新功能預設關＋獨立同意；答案唔准洩路徑／內部 id |
| O15 | 另起爐灶（已有 `PackIndex` #5 KubeJS 抽取）；冇 python 鏡像（MED） | ✅ 真 | P1 改「擴充現有基建」；每新閘要有 python 鏡像 |
| O16 | key 命名前後不一（`providerIndex.*` vs `contentIndex.*`）（MED） | ✅ 真 | 統一 `contentIndex.*` |
| O17 | 誤解 `check_settings_registry.py` 約束（LOW-MED） | ⚠️ 部分（未致命） | 開工前先讀該閘真實規則再定 settings 做法 |
| O18 | `maxJars=300` headroom 只 1.3×（LOW） | ✅ 真 | 改 600（2.6×） |
