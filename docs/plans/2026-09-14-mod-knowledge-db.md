# 2026-09-14 — Mod 知識庫（Knowledge DB）計畫 v1

> SK 原意（2026-09-14 11:1x，並確認係之前傾過嘅 idea）：**建立一個 mod 資料庫擺喺 GitHub 或某處，AI 每次遇到唔識嘅物品就去查。**
> 前身（repo 內既有 todo，`.hermes/plans/2026-09-06_anvil-repair-vs-upgrade-split.md` L1／L2）：
> - **L1**：pack 級結構化知識檔 `config/packai/knowledge/*.json` 記錄各 mod 機制（upgrade／取得／使用）→ 問 item 時按 namespace deterministic 注入 FACT；可做 agentic `upgrade_lookup`（照 `enchant_lookup`／`repair_lookup` 架構）。
> - **L2**（SK 已 mark todo）：同一批 JSON 放 **GitHub repo** 做跨 pack 共用 single source of truth；mod 安裝／更新時 pull（仍然係檔，唔使 server；真要多人才考慮 hosted API）。
>
> 狀態：**計畫（未實作）**，等 SK 揀項。範圍：只改 `forge/1.19.2`。

## 0. 範圍界定（SK 2026-09-14 定案，重要）

- **共用 GitHub 庫只收「mod 知識」**：tooltip（含 Shift 展開行）／JEI info 頁／jar 內 loot table／recipe／attribute modifier／mod guidebook 段落 → 呢類**跨 pack 通用**，先值得共用。
- **唔收 KubeJS／pack 腳本知識**（`kubejs/**` 每個包自己寫，唔通用）→ 呢類只留 **pack 本地**：由機制事實計畫 **M1** 掃描＋注入，唔會上共用庫。
- 本地 `config/packai/knowledge/*.json` 仍然可以寫 pack 專屬補充（precedence 最高），但**同共用庫分開存放**，唔會互相污染。
- **KB-3 自動起草只由 mod 側 A／B 級來源生成**（腳本類 entry 唔會出現喺共用庫）。
- 網絡（2a）：預設開、**只 send 物品 id**、帶 ETag 快取、斷網自動 fallback 本地。

## 0b. 決定記錄（SK 2026-09-14 11:3x）

| # | 題目 | 決定 |
|---|---|---|
| 1 | M1（KubeJS 掃描） | **做**，但只留 **pack 本地**（唔上共用庫） |
| 2 | 版本 gate | **要**（`applies_to.mod_versions`；mod 更新後提示 entry 可能過期） |
| 3 | 邊個起草 entry | **AI 自動起草 ＋ 其他玩家**（都可以） |
| 4 | 貢獻權 | **開放社群 PR** |
| 5 | Seed entries | **為重點 mod 起草**（AI 由 mod 側來源生成 draft → SK review → commit） |
| 6 | `unknown_items.jsonl` | **本機檔案、唔需要 server**（見下） |

### 6 詳解（v2，SK 2026-09-14 11:4x 修正：**假設玩家懶**，唔可以要玩家做任何嘢）
- **玩家零動作**：❌ 唔要「複製清單」按鈕、❌ 唔要貼 issue、❌ 唔要玩家開選項。
- `config/packai/unknown_items.jsonl` = **純本機靜默記錄**（一行一件：時間／item id／問題類型；無身份、無對話）。玩家唔會見到、唔使理。
- **入口靠自動化，唔靠人**：
  1. **自動起草**（KB-3）：由 mod 側來源（loot table／recipe／attribute／tooltip／JEI info）＋pack 腳本（M1，本地）自動生成 draft entry。
  2. **Trace 挖掘**：每條問答已經寫 `packai/trace/*.jsonl`（問題／facts／卡片／答案／缺漏）→ 可以直接 mine 出「邊啲物品答得虛」→ 自動補 entry。**玩家一樣零動作。**
  3. 本機 `unknown_items.jsonl` 只係**包作者（SK）用**：JARVIS 讀檔 → 出報告 → AI 起草 → SK 批准 → publish。其他 pack 作者一樣可以咁做（各自機上檔案）。
- **共用庫寫入路徑**：預設＝**維護者／AI 起草後 commit**（唔靠玩家）；社群貢獻＝**mod 作者或熱心玩家**自願開 PR（KB-4），唔會要求一般玩家做嘢。
- （可選，要 SK 定）如果將來想收集**其他玩家**嘅未識物品：只有兩個做法——(i) 免費 serverless worker ＋ GitHub token 自動開 issue／commit（一次性設定、免費額度）；(ii) 唔收，一律靠自動起草＋自願 PR。**唔會**設計成「玩家手動上報」。

## 1. 目標行為（SK 一句）

> 問一件物品 → 我哋 pipeline 有 A／B 級 fact 就照答；**冇（唔識）→ 去知識庫查**（先本地檔，再 GitHub）→ 有就答（標來源）；都冇 → 老實講「未列出」＋**記低呢件物品**（餵返個庫）。

## 2. 架構

```
GitHub repo: packai-knowledge（公開）
  index.json                       ← 版本／mod 清單／每檔 sha
  mods/<namespace>.json            ← 逐 mod 一批 entries（細檔、易 diff、易 PR）
  items/<namespace>/<path>.json    ← 亦可逐 item 一檔（on-demand 單件 fetch 用）

本地（玩家機）
  config/packai/knowledge/*.json        ← pack 作者覆寫／補充（precedence 最高）
  config/packai/knowledge-cache/*.json  ← 由 GitHub pull 落嚟嘅副本（連 ETag／sha）
  config/packai/unknown_items.jsonl     ← 查唔到嘅物品記錄（餵庫用；唔含玩家私隱）
```

**查詢流程（ask 內）**：本地檔 → cache → （如開咗網絡）GitHub raw 單件 fetch（`items/<ns>/<path>.json`，只 send 物品 id，帶 ETag 條件請求）→ 命中即注入 fact（**標來源＋tier**）→ 都冇就 HonestMiss ＋ append `unknown_items.jsonl`。

## 3. Entry schema（草案；真身＝repo `schema/entry.schema.json`）

```json
{
  "item": "create:goggles",
  "display": {"en_us": "Engineer's Goggles", "zh_cn": "工程师护目镜"},
  "mod": "create",
  "applies_to": {"mc": "1.19.2", "loader": "forge", "mod_versions": ["0.5.x"]},
  "obtain": [{"type": "craft", "source": "mod:recipe_json", "tier": "A"}],
  "use": [{"trigger": "wear_in_head_slot", "effect": "顯示護目鏡資訊覆蓋層（應力／容量／方塊資訊）",
           "source": "mod:tooltip", "tier": "B"}],
  "worn": [{"slot": "head", "effects": ["佩戴時顯示護目鏡資訊覆蓋層"], "source": "mod:tooltip", "tier": "B"}],
  "notes": "…", "contributors": ["SK"], "updated": "2026-09-14"
}
```

> ⚠️ **例子用 `create:goggles`（真 mod item）。** `momo_dlc`／`mrqx_extra_pack` 係 **pack 用 KubeJS 建嘅命名空間（唔係 mod）** → 屬 pack 本地知識，**唔准入共用庫**（SK 2026-09-14 更正）。所以 `source` **唔會**出現 `kubejs:*`；pack 腳本知識由機制事實層（M1）在 pack 內處理。

- **tier** 沿用機制事實計畫嘅 A（機器可驗：腳本／loot table／advancement）／B（人寫針對該物品：JEI info／tooltip／guidebook）／C（人寫可能唔完整：FTB 任務描述）。
- 同一 item 可由多來源拼合；**A／B 永遠照列，C 只作補充並標明**。

## 4. 分期

- **KB-1（本地打底）**：`config/packai/knowledge/` 讀取＋`knowledge_lookup` ask-tool（照 `enchant_lookup` 架構）＋ask 內 deterministic 注入＋`unknown_items.jsonl` 記錄＋Settings 一頁（KB 開關／資料夾／重建快取）。
- **KB-2（GitHub pull）**：`knowledge.url`（預設 `https://raw.githubusercontent.com/<repo>/main`）＋單件 fetch＋ETag 快取＋失敗靜默（離線照用本地）＋「測試連線」按鈕。
- **KB-3（自動起草）**：由機制事實掃描（M1／M2／M3／M6）自動生成 draft entry（A 級優先，附 `source`）→ 寫 `config/packai/knowledge-drafts/` → SK review → commit 上 GitHub repo（日後可開放社群 PR）。
- **KB-4（社群／多 pack）**：index.json 版本化、按 mod 版本 gate、貢獻者欄位；多人維護先考慮 hosted API（要另審，同「本機/免費優先」原則相衝）。
- **KB-5（TODO，SK 2026-09-14 決定「b 長遠較好，暫存 todo，現時用 a」）**：自願／匿名收集**其他玩家**嘅未識物品（免費 serverless worker ＋ GitHub token 自動開 issue／寫 entry）。**前置條件**：mod 真係多用戶先做；要另備（i）私隱聲明（只傳物品 id）、（ii）預設關＋可選開、（iii）worker 成本評估（免費額度）。**現階段＝不實作。**

## 5. 空間控制（SK 2026-09-14 11:5x 定：**起草功能跟設定**，唔可以無上限食空間）

所有寫入一律受設定控制＋有硬上限（超標即按 LRU／最舊淘汰），並在 Settings 有一頁「知識庫」可以改／清：

| config key | 預設 | 作用 |
|---|---|---|
| `knowledge.enabled` | `true` | 總開關（關＝完全唔讀唔寫知識庫） |
| `knowledge.remote` | `true` | 允許由 GitHub 拉（關＝只用本地檔） |
| `knowledge.cacheMaxMb` | `32` | 遠端副本 cache 上限（MB，LRU 淘汰） |
| `drafts.enabled` | `true` | 自動起草（關＝唔會產生任何 draft） |
| `drafts.scope` | `asked_only` | **只為「實際被問過」嘅物品起草**（唔會全包掃描爆檔） |
| `drafts.maxEntries` | `200` | draft 檔數上限 |
| `drafts.maxMb` | `2` | draft 總大小上限（MB） |
| `unknown.maxLines` | `2000` | `unknown_items.jsonl` 行數上限（同一 item 去重，只留最近一次） |
| `unknown.maxMb` | `1` | 同上大小上限 |

- **Settings（知識庫頁）按鈕**：開啟知識庫資料夾／清空起草／清空快取／匯出未識清單（我自己用；玩家唔使理）。
- **寫入前必檢上限**：任何 append（draft／unknown／cache）都先查上限，爆就淘汰最舊／最少用嗰批（deterministic，唔靠人）。
- **唔會**：背景全包掃描產生海量 draft（除非 SK 明確改 `drafts.scope = scan_all`）。



## 5b. 去重規則（SK 2026-09-14 定：「還要記得刪除重複」）

四層都要去重（**deterministic，靠 key 唔靠人眼**）：

| 層 | Key | 行為 |
|---|---|---|
| `unknown_items.jsonl` | `item id` | 同一 item 只留**最後一次**（更新 timestamp／`seen` 次數），唔會疊行 |
| `knowledge-drafts/` | `item id + 機制種類 + 正規化內容`（hash） | 同一條機制**只一份**：重複出現就 merge（更新 `updated`、`seen+1`、補 `source` 清單），唔會出多檔 |
| `knowledge-cache/` | 檔案 **sha256**（內容尋址） | 同內容自動只存一份；ETag 命中就唔重寫 |
| 共用庫 entry | `item id + mechanism type + normalized effect text` | 同一 key 只准一條；**PR 上會有 CI 檢查**（JSON schema＋重複 key＝紅燈），唔會靠人為自覺 |

- **衝突唔算重複**：同一物品同一機制但**內容唔同**（例：兩個來源寫唔同機率）→ **兩條都保留**，各帶 `source`／`tier`，並標 `conflict:true` 俾答案明示（唔准靜靜揀一個）。
- 去重係**寫入前**做（append 之前查 key），唔係事後清理。

## 6. 風險

- **網絡**：預設行為要 SK 定（每次查＝要上網；只 send 物品 id，無私隱問題；但離線／公司網要 fallback 本地）。單件 raw fetch 失敗要靜默、唔可以拖慢答案。
- **正確性**：知識庫由人／AI 起草，會有錯 → 每條要帶 `source`＋`tier`，UI／答案要標「知識庫（tier B，來源：JEI info）」，唔可以當成機器實證。
- **維護**：庫會腐爛（mod 更新後機制變）→ `applies_to.mod_versions`＋`updated` 欄位；mod 更新時提示重建。
- **覆蓋率**：第一步唔會即刻齊 → 靠 `unknown_items.jsonl` 逐件補（同 repo 既有「煙測撞到邊個補邊個」一致）。
- **重複來源**：知識庫同 M1～M3 掃描結果可能重疊 → 規則：**掃描結果（A 級）優先**，知識庫只補空缺（避免 stale 覆蓋現況）。

## 7. 驗收

1. harness：entry 解析／precedence（本地 > cache > remote）／tier 排序／缺 source 要 warn／壞 JSON 要跳過唔 crash。
2. 真機：問一件**冇** A／B fact 但知識庫有 entry 嘅物品 → 要答到＋標來源；問一件三個來源都冇 → HonestMiss＋`unknown_items.jsonl` 有記錄。
3. 離線測試：斷網 → 用 cache／本地仍可答，唔可以卡住。
4. 兩次 code review（pass1 重構／pass2 三個月後脆弱位）。
