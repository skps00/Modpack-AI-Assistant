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

## 3. Entry schema（草案）

```json
{
  "item": "momo_dlc:t-02-99",
  "display": {"zh_cn": "空虚之梦", "en_us": "Dream of Emptiness"},
  "mod": "momo_dlc",
  "applies_to": {"mc": "1.19.2", "loader": "forge", "mod_versions": ["*"]},
  "obtain": [
    {"type": "drop", "mob": "minecraft:skeleton", "requires": [{"worn": "momo_dlc:t-02-99", "slot": "curios:feet"}],
     "chance": "1%", "source": "kubejs:momo_dlc/entity/momo_dlc_entity_death.js", "tier": "A"},
    {"type": "loot", "container": "nether_chest", "source": "jei_info", "tier": "B"}
  ],
  "use": [{"trigger": "hold_right_click", "effect": "顯示未來提示（N/4）",
           "source": "kubejs:mrqx_extra_pack/mrqx_common/mrqx_events.js", "tier": "A"}],
  "worn": [{"slot": "curios:feet", "effects": ["..."], "source": "tooltip", "tier": "B"}],
  "notes": "…", "contributors": ["SK"], "updated": "2026-09-14"
}
```

- **tier** 沿用機制事實計畫嘅 A（機器可驗：腳本／loot table／advancement）／B（人寫針對該物品：JEI info／tooltip／guidebook）／C（人寫可能唔完整：FTB 任務描述）。
- 同一 item 可由多來源拼合；**A／B 永遠照列，C 只作補充並標明**。

## 4. 分期

- **KB-1（本地打底）**：`config/packai/knowledge/` 讀取＋`knowledge_lookup` ask-tool（照 `enchant_lookup` 架構）＋ask 內 deterministic 注入＋`unknown_items.jsonl` 記錄＋Settings 一頁（KB 開關／資料夾／重建快取）。
- **KB-2（GitHub pull）**：`knowledge.url`（預設 `https://raw.githubusercontent.com/<repo>/main`）＋單件 fetch＋ETag 快取＋失敗靜默（離線照用本地）＋「測試連線」按鈕。
- **KB-3（自動起草）**：由機制事實掃描（M1／M2／M3／M6）自動生成 draft entry（A 級優先，附 `source`）→ 寫 `config/packai/knowledge-drafts/` → SK review → commit 上 GitHub repo（日後可開放社群 PR）。
- **KB-4（社群／多 pack）**：index.json 版本化、按 mod 版本 gate、貢獻者欄位；多人維護先考慮 hosted API（要另審，同「本機/免費優先」原則相衝）。

## 5. 風險

- **網絡**：預設行為要 SK 定（每次查＝要上網；只 send 物品 id，無私隱問題；但離線／公司網要 fallback 本地）。單件 raw fetch 失敗要靜默、唔可以拖慢答案。
- **正確性**：知識庫由人／AI 起草，會有錯 → 每條要帶 `source`＋`tier`，UI／答案要標「知識庫（tier B，來源：JEI info）」，唔可以當成機器實證。
- **維護**：庫會腐爛（mod 更新後機制變）→ `applies_to.mod_versions`＋`updated` 欄位；mod 更新時提示重建。
- **覆蓋率**：第一步唔會即刻齊 → 靠 `unknown_items.jsonl` 逐件補（同 repo 既有「煙測撞到邊個補邊個」一致）。
- **重複來源**：知識庫同 M1～M3 掃描結果可能重疊 → 規則：**掃描結果（A 級）優先**，知識庫只補空缺（避免 stale 覆蓋現況）。

## 6. 驗收

1. harness：entry 解析／precedence（本地 > cache > remote）／tier 排序／缺 source 要 warn／壞 JSON 要跳過唔 crash。
2. 真機：問一件**冇** A／B fact 但知識庫有 entry 嘅物品 → 要答到＋標來源；問一件三個來源都冇 → HonestMiss＋`unknown_items.jsonl` 有記錄。
3. 離線測試：斷網 → 用 cache／本地仍可答，唔可以卡住。
4. 兩次 code review（pass1 重構／pass2 三個月後脆弱位）。
