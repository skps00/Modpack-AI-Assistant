# Plan C v2 — 包原文文字層（站點「來源物品」原文做條件文案 ＋ 決定表去重）

> 狀態：**v2（2026-09-17 12:2x）** — 依 R1 反方 findings 重寫（subagent 2:8 ＋ cursor 3:7，兩邊一致；本 plan v1 係三張之中最差）。
> 開工條件：再跑 review 達 **正方 ≥8 : 反方 ≤2**；**另需 SK 拍板 §7 Q1**（因為佢決定本 plan 有冇旗艦價值）。
> 由來：β（`docs/plans/2026-09-16_kubejs-transform-v5.0-api-first.md` v5.4）第 3 點——但 v1 方向錯，見 §0。

## §0 撤回項（v1 → v2，反方證據）
1. **撤回「查 focus item 嘅 tooltip 就夠」**：旗艦例子嘅條件文案喺**源物品**，唔喺產出物品。
   - 站點（`kubejs/server_scripts/curios/entity_death.js:22-26`）＝ 揹住 **空項鍊**`kubejs:god_bless_empty_necklace` ＋ 殺 `bossesOfMassDestructionBossTypeList` 之一 → 變 **滿溢項鍊**`kubejs:god_bless_full_necklace`。
   - `kubejs.tooltips.god_bless_empty_necklace.1`（zh_cn.json:1093）＝「击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能」← **4 隻 boss 名喺呢度**。
   - 而 `god_bless_full_necklace` 嘅 tooltip＝「用于在沙漠维度地牢中进行神意挑战」（**唔係** boss 名）→ 玩家問「滿溢項鍊點充能」而 focus=full 時，v1 設計會攞錯句。
   - → **lookup 主體改為站點嘅 input／held／transform 源 id**，唔係 focus／output。
2. **撤回「v1 已覆蓋旗艦例子」**：`PackIndex.java:1271-1276` 可見性白名單寫死 `site.kind() != Kind.PRODUCE → continue` → **TRANSFORM 站點（項鍊）今日永遠唔會出玩家文字** → v1 旗艦收益＝**零**（要靠 §7 Q1 拍板）。
3. **撤回固定 0.6 去重門檻**：真語料 59 對 pair 實測 char-jaccard 最大 **0.071**、bigram 0.010、dice 0.133 → 0.6 永遠唔觸發＝**假精度**；而「只出一段」今日其實已成立 → 無鑑別力。→ 改**決定表**（§3 D3）。
4. **修正 key 形狀假設**：唔止 `kubejs.tooltips.<path>.<n>`——zh_cn 有 **61** 條 4 段以下（例 `kubejs.tooltips.heart`＝「心脏」，器官類）→ 要支援 bare key。
5. **修正前綴命中率**：`kubejs.tooltips.<path>` 實測 **HIT 46 / MISS 214**；真語料 13 個 tooltip 前綴共 3684 key，`kubejs` 只 649 → 要正式 resolution order＋fail-closed。
6. **修正錨點**：v1 引 `zh_cn.json:871`「暗夜巫妖」當神恩對照＝錯（該行係 `kubejs.jei.bad_ink.1`，墨水 JEI）。
7. **修正多語假設**：instance **冇** `kubejs/assets/kubejs/lang/en_us.json`、`zh_tw.json`（只見 `zh_cn.json`）→ 唔可以假設三語齊全。

## §1 目標（v2）
玩家問一件物品「點充能／點嚟／點用」時，答案嘅**條件欄**用**包自己寫嘅字**（唔係我哋砌），而且同一件事**唔會講兩段**。

## §2 範圍（v2）
- **C-1 條件文案來源＝站點來源物品嘅包原文**：站點 kind 為 `TRANSFORM`／`SWAP` 時，lookup 站點嘅 input／held id；`PRODUCE` 時用 output id。
- **C-2 key 解析**：`<ns>.tooltips.<path>` 系列 ＋ bare key；resolution order＝① 該 item 所屬 namespace 嘅 tooltip 前綴 ② `kubejs` 前綴 ③ 其餘 12 個前綴；**歧義 → fail-closed（唔配）＋ diag log**（真例：`flame_heart`／`ice_heart`／`creeper_appendix` 同時有 `kubejs.tooltips.<path>.*`）。
- **C-3 去重（決定表，唔用閾值）**：
  - code cond 係**識別字／變數**（例 `bossesOfMassDestructionBossTypeList`）→ 只用包原文；
  - code cond 有**數值／`Math.random`／明確門檻**（例 `random<0.3`、`count>=5`）→ 包原文 ＋ code 數值**併一行**；
  - 包原文缺 → code 字面 → 皆缺 → 機械式（來源→觸發→產出）。
  - 「一段」操作定義＝回覆行內以 `｜` 分隔嘅**條件片段**（單一），並以 fixture 做 poisoned 斷言（包原文在 ⇒ raw JS cond 唔准出現）。
- **C-4 開關**：新 config `jsObtainPackText`（預設 on）；要 **3 lang ＋ settings registry ＋ setter gate**（易漏，入 §4 驗收）。

## §3 設計要點
- render 期 `I18n.get`（**唔 bump** `INDEX_SCHEMA_VERSION`；先例 `AskService.java:2250`、`ModularToolScan.java:277`）；離線 harness 用**注入式 resolver**（唔依賴 instance 檔）。
- 只喺 KubeJS 取得通道命中嘅站點做（唔做全包 tooltip 索引）。

## §4 驗收（S1–S9）
- **S1** 旗艦 pair（強制 fixture）：站點 output=`god_bless_full_necklace`、來源=`god_bless_empty_necklace` → 條件欄 == zh_cn 原文（4 boss 隻名）→ 今日紅。
- **S2** 決定表：poisoned fixture（包原文與 code cond 講同一件事）→ 條件片段**只有一段**且係包原文；另一 fixture（code 有 `random<0.3`）→ 兩者**併一行** → 今日紅。
- **S3** key 形狀：`.n` 序列（多段 concatenate）＋ bare key（`kubejs.tooltips.heart`）兩者都解析到 → 今日紅。
- **S4** 多語／注入：**唔假設** instance 有 en_us／zh_tw；用注入 fixture 測「有 zh_cn → 用 zh_cn；缺 → 機械式」→ 今日紅。
- **S5** 撞名 fail-closed：fixture 用真例（`flame_heart` 跨 b_a_d／kubejs）→ 唔配 ＋ diag 有紀錄 → 今日紅。
- **S6** 顯示層：條件片段零 `PLAYER_VISIBLE_FORBIDDEN`（`JsObtainSites` 已有同一常數）→ 今日綠（regression）。
- **S7** v6.14 既有 harness 全綠（`runAskJsObtainSitesCheck`／`runAcquireFactsCheck`／`runAskMechanicFactsCheck`）＋ python 閘（今日 116 PASS/0 FAIL）→ 綠（regression）。
- **S8** config 對齊：`jsObtainPackText` 3 lang key ＋ registry ＋ setter gate → 今日紅。
- **S9** 真機：SK 問「滿溢神恩項鍊點充能」→ 答案條件欄出現包原文 4 隻 boss 名（前提＝§7 Q1 過）。

## §5 唔准做
- 唔准自創動詞（包有字用包原文；包冇 → 機械式）。
- 唔准用單一固定前綴硬配（會 82% MISS）。
- 唔准 bump schema／改 `JsObtainSites` 站點資料結構（只加解析層）。

## §7 開工前要 SK 拍板
- **Q1**：要唔要連 **TRANSFORM** 站點都出畀玩家（例：空項鍊 → 殺 4 王之一 → 滿項鍊）？
  - **y** ＝放寬 `PackIndex.java:1271-1276` 白名單（focus 命中時 PRODUCE＋TRANSFORM 都出；仍然 cap 3 行／fail-closed）→ C 線先有旗艦價值；
  - **n** ＝只做 PRODUCE → 項鍊例子放棄，C 線價值細（建議唔做，或者同 v6.14 嘅後續一齊考慮）。

## §8 Review 記錄
| 輪 | 對象 | 正方 : 反方 | 結果 |
|---|---|---|---|
| R1 | v1 | 2 : 8（subagent）／3 : 7（cursor 第三評審） | ❌ 未過；旗艦 lookup 方向、PRODUCE 白名單、key 前綴、去重閾值全部要改 |
| R2 | v2（本檔） | 待跑（**先要 SK 答 Q1**） | — |
