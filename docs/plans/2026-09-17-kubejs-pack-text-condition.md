# Plan C — 包原文文字層（KubeJS tooltip／lang 做「條件」文字來源 ＋ 去重）

> 狀態：**v1 草稿（2026-09-17 11:3x）** — 未 review（等反方 R1）。開工條件：**正方 ≥8 : 反方 ≤2**。
> 由來：β（`docs/plans/2026-09-16_kubejs-transform-v5.0-api-first.md` v5.4 §1.3）＋ SK 09-17 指正：「**tooltip 嘅部分可能會與 kubejs 程式碼邏輯重合**——以神恩頸鏈為例，代碼層睇到邏輯，tooltip 睇到充能方式」。
> 結論：**唔係另加一句描述，而係用包原文補「條件」欄 ＋ 去重**（同一機制只出一段）。

## 0. 目標（一句）
玩家答案嘅「條件」欄用**包自己寫嘅字**（包有寫就用原文），code 出結構（來源→觸發→產出）；兩者講同一件事時**只出一段**（唔重複）。

## 1. 事實（逐字核實，唔靠估）
- **code 側**：`<instance>/kubejs/server_scripts/curios/entity_death.js:22-26`
  ```js
  const curiosDeathStrategies = {
      'kubejs:god_bless_empty_necklace': function (event, curios, slot, item) {
          if (!bossesOfMassDestructionBossTypeList.some(ctx => ctx == event.entity.type)) return
          curios.setStackInSlot(slot, Item.of('kubejs:god_bless_full_necklace'));
      },
  }
  ```
  → 條件係「揹住空項鍊 ＋ 殺死 `bossesOfMassDestructionBossTypeList` 內嘅實體」：**個 list 係識別字，玩家睇唔明係邊隻 boss**（呢個就係之前卡住嘅「4 個 boss 名要解 entity 名」）。
- **包原文側**：`<instance>/kubejs/assets/kubejs/lang/zh_cn.json:1093`
  `"kubejs.tooltips.god_bless_empty_necklace.1": "击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能"`
  → 直接列出 **4 隻 boss 名**，而且「**充能**」係**包自己嘅用字**（按 SK 規則：包有寫＝可以用，唔算自創動詞）。
- **命名不一致（pack 作者問題，我哋唔改）**：同一隻 boss，tooltip 寫「暗夜**巫師**」，JEI 文字（`kubejs/assets/kubejs/lang/zh_cn.json:871`）寫「暗夜**巫妖**」→ 照引原文，唔自己揀。
- **key 形狀**：`kubejs.tooltips.<item_path>.<n>`（**用 item path、冇 namespace**）→ 跨 namespace 同名 path 有撞名風險。
- **既有基礎設施**：`logic/PackIndex.java:295 loadLangFile`／`:307 translations::get`（已會載 pack lang）；`logic/ReplyLang` 出玩家文字；`logic/JsObtainSites`（v6.14 新加）出 JS 站點（`cond` 目前係字面／識別字）。

## 2. 設計

### C1 條件欄文字來源（優先序）
1. **包原文**（`kubejs.tooltips.<path>.<n>`，即玩家喺遊戲內見到嘅字）——**當 code 條件係識別字／變數、或包原文包含 code 睇唔到嘅細節（boss 名、階段、前置）**。
2. **code 字面**（v6.14 已有：`cond` literal，例如 `Math.random() < 0.2`）——當包原文冇講、或包原文係純用途描述。
3. **機械式**（`<來源>（id）→（<觸發>）→ <產出>（id）`）——兩者都冇。
- 讀取用 **render 期 `I18n.get(key)`**（β §1.3 已選 (a)：先例 `client/service/AskService.java:2250`、`logic/ModularToolScan.java:274`）→ **唔使 bump `INDEX_SCHEMA_VERSION`**、唔使重掃。

### C2 去重（SK 要求）
- 同一 JS 站點嘅答案**只出一段條件文字**：
  - normalize（去空白／全半角／大小寫）後 token 重疊 ≥ 0.6 → **只出包原文**（玩家語言優先），code 段唔再重複。
  - 包原文只講用途、code 條件有實質資訊（機率／數值）→ 出 code 字面，**唔加**包原文。
  - 兩者資訊互補（例如包原文有 boss 名、code 有機率）→ **合併成一句**（`包原文 ＋ ｜機率：0.2`），仍然係**一段**。
- 斷言方式：harness 計「同一站點輸出段數 == 1」（唔准出現兩段講同一件事）。

### C3 缺 lang／撞名處理（fail-closed，唔准亂配）
- item 冇 `kubejs.tooltips.*` → 走 code 字面／機械式（**唔准**空白、唔准 crash）。
- **同名 path 跨 namespace 撞**（例：兩個 namespace 都有 `god_bless_empty_necklace`）→ **skip** 包原文，並且診斷 log 一行（`packText ambiguous path=<path>`）；唔准靠猜。
- lang key 存在但值空白 → 當冇。

### C4 開關／範圍
- 新 config：`jsObtainPackText`（default **true**）獨立 kill-switch（同 `jsObtainChannel` 分開）。
- 範圍：**只查 focus item（同答案內出現嘅站點）**嘅 tooltip key；**唔做**全包 tooltip 索引／唔掃 client_scripts。
- 唔准改：`JsObtainSites` 站點語義（v6.14 已 review 過）、JEI fact 通道。

## 3. 驗收（S1–S8）

| # | 斷言 | 今日 |
|---|---|---|
| S1 | harness：focus=`kubejs:god_bless_empty_necklace` → 條件欄 == 包原文 `击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能`（zh_cn） | **紅** |
| S2 | **去重**：poisoned fixture（包原文同 code cond 逐字相同）→ 只出 **1** 段（段數斷言） | **紅** |
| S3 | 互補合併：包原文（boss 名）＋ code（`Math.random()<0.2`）→ 同一段出現兩者（唔係兩段） | **紅** |
| S4 | 多語：en_us／zh_cn／zh_tw 各自讀自己 lang；該語缺 key → 走 code／機械式（唔准 fallback 去 zh_cn 扮有） | **紅** |
| S5 | 撞名 path → skip 包原文 ＋ 診斷 log 一行（唔准亂配） | **紅** |
| S6 | `PLAYER_VISIBLE_FORBIDDEN_RE` 零命中（新輸出） | **紅**（守門） |
| S7 | v6.14 既有 harness／S1–S17 全綠（唔可以退）＋`AskMarkerIntegrityCheck` OK | 綠（regression） |
| S8 | 真機（SK）：問「滿溢神恩項鍊」→ 條件欄出現 4 隻 boss 名（包原文），一句過、冇重複 | 人手 |

## 4. 影響面／還原
- 改：`logic/ReplyLang.java`（條件欄組裝）、新增小工具（`PackTextFacts.java`：path→tooltip key 解析＋去重）、`config/PackAiConfig.java`（1 個新 key）、3 個 lang（如需新文案格式）、harness（新 `PackTextFactsCheck`）。
- **還原** = `git revert` 單 commit；config kill-switch 即時關；jar 由 Hermes 部署。
- ⚠️ 同 Plan B 共用 `ReplyLang.java`／3 lang → **唔可以並行派工**；建議次序 **Plan A → Plan B → Plan C**。

## 5. Review 狀態
- R1：**未跑**（反方）。上限 3–4 輪；未達 8:2 停手交 SK。

## 6. 已知脆弱位
- `kubejs.tooltips.<path>.<n>` 用 path（冇 namespace）→ 撞名風險（C3 已 fail-closed）。
- 包原文可能含**多語夾雜／錯字**（實例：tooltip「暗夜巫師」vs JEI「暗夜巫妖」）→ 我哋照引，唔修正（唔准自創）。
- render 期 `I18n.get` 依賴 client resource 已載入（`ModularToolScan` 已有先例）；離線 harness 要用注入式 resolver 測試（唔可以靠 client 資源）。
- 唔准擴 scope 去「全包 tooltip 抽字」（成本大、無目標）。
