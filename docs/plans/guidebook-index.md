# Pack AI — Guidebook index（Patchouli + similar）（整條路線計畫）

Status: **Phase B CODE COMPLETE (B1–B3).** Runtime verify **pending user `tests OK` / `jar`**. **Agent: zero Shell.**  
Generated: 2026-08-12；…；**WP1–4 code: 2026-08-12**；**Phase B code: 2026-08-12**.  
Related: [full-item-index.md](full-item-index.md)（指紋／disk cache 範本）; [accuracy-first-next-wave.md](accuracy-first-next-wave.md)（QA gate 風格）; quest 路徑仍見 `QuestGuide`（FTB / Heracles）。  
**Loaders:** Forge 1.19.2 + NeoForge 1.21.1（鎖步同一語意；Forge/NFWC = 主驗收）。  
**Log:** repo-root `code_change_log.md`（先寫日誌再改碼）。  
**Version:** 本波／後續 phase **不 bump** `mod_version`（本地 jar→dist→NFWC 煙測即可；公開上傳另談）。  
**Honesty（鎖定）：** guidebook／Patchouli = **secondary narrative pin**；mechanics 以 recipes／unlock／quest／registry **code/data** 為準（見 §1／§3／Appendix B #20）。

**開工門檻（鎖定）：** 使用者明確說「開始 Phase A」或「開始 WP1」之後才改 Java／產品測試；在此之前只更新本計畫與 changelog。

---

## 整條路線（Phases）— Obsidian + agent-memory 風格

對齊使用者討論的「可搜尋書本上下文」：先結構化筆記＋索引（A），再筆記間連結＋標題／token 搜尋（B），再 chunk 檢索＋session 記憶（C）；雲端 embedding／OCR 等永遠偏後（D）。

| Phase | 別名 | 交付物（一句） | 何時動手 |
| ----- | ---- | -------------- | -------- |
| **A** | v1 shipable | 結構化 Entry + disk index + item→entry Ask `[GUIDE]` + `guidebookScope` | **確認後 → WP1**（現 WP0–WP4） |
| **B** | Obsidian-like | entries as notes；inverted + **entry↔entry／category links**；**title+token search**（高門檻）；可選輕量 related graph；仍不 invent | Phase A DoD 後再開 B1 |
| **C** | Memory / RAG-lite | page/chunk store；item+query top-k clips；只 pin 檢索到的片段；可選 session「已示 entry」dedupe（**≠ player chat history**） | Phase B 核心穩定後 |
| **D** | Defer forever-ish | OCR 開書、Ponder、Akashic 內容、cloud embeddings | 不排進 A/B/C DoD |

```
Phase A ──► Phase B ──► Phase C        Phase D（旁路／永不強制）
  index        notes+links   chunk RAG     OCR / Ponder / embed cloud
  item→GUIDE   title search  session mem
```

**順序鎖定：** A → B → C；不跳過 A 做 B 搜尋；不做「先 RAG 再 index」。Start 仍是 **WP1 after confirm**。

---

## 0. Baseline（FACT — 今日已有／缺口）


| Piece                                     | Role today                                                                                                         |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `QuestGuide`                              | FTB Quests / Heracles 任務文字；**不是** mod guidebook                                                                    |
| `PatchouliBridge` / `PatchouliBridgeImpl` | Soft-dep：活體 `BookRegistry` + `getEntryForStack` → 抽 text pages                                                     |
| `PatchouliGuideLookup`                    | Ask focus stack：API 優先，否則 **每次** `listResources("patchouli_books/**/entries/*.json")` 全掃                           |
| `PatchouliEntryScan`                      | 無 Patchouli 類：icon / `extra_recipe_mappings` / page `item(s)` 比對；text 頁抽字 + macro strip；cap 2 entries / 3000 chars |
| `AskService` → `AskEngine`                | `purposeGuide` → `AskPurposeContext` 包成 `[GUIDE]`（PURPOSE 旁路）                                                      |
| `tests/check_patchouli_entry_scan.py`     | JSON match / extract fixtures                                                                                      |


**Gap（Phase A 要補）：**

1. **無 disk index** — 大包每次 Ask 可能重掃全部 entry JSON（慢、無指紋命中）。
2. **無結構化 entry 模型** — 只有 capped blob，無 `bookId` / `entryId` / linked items 可 dedupe／pin。
3. **無與 quest 明確分層** — `[GUIDE]` vs quest guide 文案可能重疊語意，需規則。
4. **無使用者可選的 guidebook scope** — 同 mod／跨 mod 需設定（§6）。

**已知不做於 Phase A（勿當 v1 缺口；見 Phase B／D）：**


| Item                                 | 歸屬              |
| ------------------------------------ | ----------------- |
| 問句-only／全書標題＋token 搜尋（無 focus item 也可命中） | **Phase B**（高門檻） |
| Open Patchouli GUI／OCR 看開書           | **Phase D**       |
| entry↔entry graph／category 瀏覽        | **Phase B**       |
| page chunk RAG／session memory          | **Phase C**       |


**INFERENCE:** 使用者說「今天只有 QuestGuide、沒 Patchouli pages」≈ 產品體感仍偏 quest；技術上已有 **薄 live lookup**，Phase A = **靜態索引升級**，不是從零發明 Patchouli。

**Target after Phase A（計畫）：** Ask 改 **index-first**（miss 才可選 soft-dep API）。**今日 FACT 仍是** API-then-scan（上表 `PatchouliGuideLookup`）— 勿把目標行為當現況。

---

## 1. Goal / Non-goals（雙視野）

### Goal — Horizon A（可出貨 v1）

讓 Ask 能 **準確引用** 已安裝 mod 的 Patchouli（及同形 datapack）guidebook 條目：item→entry 命中、標題／正文 clip 進 FACT/`[GUIDE]`；accuracy > completeness；**universal scanners**（無 pack id 硬碼）。

成功長相（Phase A）：

- 首 join 建一次 guidebook index → disk cache；同 fingerprint 跳過 rebuild。  
- Ask 有 focus item 時，`[GUIDE]` 來自 **index lookup**；**預設**只送該物品所屬 mod 的書，使用者可在設定改為允許跨 mod（見 §6）。  
- Miss → 空／誠實「無 guidebook 命中」，**不**用 JEI tooltip／任務文當 guide 真相。

### Truth hierarchy — guidebook ≠ ground truth（鎖定）

**User lock:** book ≠ true；pack author 常改書（resource pack／KubeJS／datapack）→ 書可過時、錯、或 pack-fiction。**讀 code／structured data 仍最正確。**

- Guidebook／Patchouli text = **helpful secondary narrative pin**，**不是** mechanics 絕對真相。  
- Recipes（JEI cards）、unlock gates、loot／KubeJS（若已 index）、quest data、item registry 等 **structured FACT** 優先於書本措辭。  
- `[GUIDE]` **不得**覆寫 recipe／quest／unlock FACT；衝突時 **data 勝**；可同時 pin 並誠實注明「書寫 X、資料 Y」（不 invent）。  
- 全 phases 真相優先序見 **§3**；Ask prompt 見 **§6**；決策列 **Appendix B #20**。

### Goal — Horizon B／C（計畫中；A 完成後）

- **B：** 把 entry 當 Obsidian 式 notes：可沿 category／頁內連結找 related；在 **高門檻** 下允許 title＋token 搜尋補 item 命中之外的針；仍只 pin 真實 JSON 文字。  
- **C：** RAG-lite — 以 page／chunk 為單位 top-k 檢索，只 pin 檢索片段；可選跨 turn「已示 entry」去重（與玩家對話歷史分開存，若需要）。

### Non-goals（明確不做／歸後 phase）


| Item                                        | Reason                                           |
| ------------------------------------------- | ------------------------------------------------ |
| 發明 lore／補全書未寫內容                             | accuracy 禁令                                      |
| 把 JEI tooltip／Ponder tooltip 當 guidebook 真相 | 來源錯；Ponder = Create 場景 → Phase D               |
| Akashic Tome「書本內容」                          | 僅 holder／聚合道具 → Phase D                          |
| Live「看著打開的書螢幕」OCR / CUA 即時讀頁                | 脆、慢、非準 → Phase D                                |
| Pack-specific hardcodes（某包某書 id 表）          | universal only                                   |
| 強制永遠跨 mod／永遠禁止跨 mod（無設定）                    | **使用者可選**（§6）；預設同 mod                            |
| 完整翻譯 i18n 解析所有 `$(lang)`／嵌套 book extension  | A：優先實際 lang 資料夾 + en_us fallback（既有 `langRank`） |
| 強制開 Patchouli GUI／跳轉頁（除非後期 API 一行能做）    | YAGNI → Phase D／可選微 WP                           |
| 取代 `QuestGuide`                             | quest ≠ guidebook；並存、dedupe 規則即可                 |
| Version bump / CF upload / PR               | plan-only；實作另談                                   |
| 新獨立「百科 UI」screen                            | 跟 item-index：加速 Ask，不做 catalog B                 |
| Cloud／遠端 embedding 模型（除非日後另開）              | Phase C 優先 **本地 token／IDF**；cloud = Phase D       |
| Phase A 內做 query-only 模糊搜全書                 | 誤傷高 → **Phase B** 帶門檻才做                         |
| 宣稱 Patchouli／guidebook 是 pack-accurate mechanics 真相來源 | 書≠真；pack 可改書；mechanics 以 code／data 為準（§1／§3／#20） |


---

## 2. Research notes（輕量）

### Patchouli 存放（FACT）

- **Books:** 資源／datapack 慣例 `assets/<ns>/patchouli_books/<book_id>/`（或 runtime 註冊）；`book.json` 描述書本。  
- **Entries:** `<lang>/entries/**/*.json`（常見 `en_us`、`zh_tw`…）。  
- **Entry JSON（典型欄位）：** `name`, `icon`, `category`, `pages[]`（`type`: `text` / `spotlight` / `crafting` / …）, `extra_recipe_mappings`.  
- **Data 側:** 部分書用 `data/...` 或 KubeJS 生成；**client ResourceManager `listResources("patchouli_books", …)`** 已涵蓋多數 asset 書（現況 `PatchouliGuideLookup`）。  
- **Soft-dep:** `BookRegistry` + `BookContents.getEntryForStack` 解析 recipe mapping／icon 連結；extension book 現況跳過。  
- **File scan / index build（A 鎖定）：** 無 Patchouli jar 時仍可掃 JSON（`PatchouliEntryScan`）。**Async build thread：只用 JSON `linkedItems`**（icon / `extra_recipe_mappings` / page item(s)）— **不**在背景呼叫 Patchouli API 補 linked。  
- **Optional build-time enrich（若日後做）：** 必須 **client thread only**；寫入 index mappings；**bump `formatVersion`**；與 Ask 路徑分開。  
- **Ask-time soft-dep：** index lookup → 空才 **API once**（WP4）；**不**雙 pin；**不是**填 inverted map 的常態路徑。

### NFWC 其他「指南」形態（輕掃）


| 形態                                     | 處置                                                    |
| ---------------------------------------- | ----------------------------------------------------- |
| Patchouli（Botania Lexica、多數 mod book…） | **本路線主線（A→C）**                                      |
| Akashic Tome                           | **Phase D skip**（holder）                              |
| Create **Ponder**                      | **Phase D**；場景文字若可 API 抽再談；勿 scrape tooltip         |
| IE Manual / 舊自研 GUI book               | 無通用 JSON → **不進 A**；有公開 datapack 再加 scanner          |
| FTB Quests / Heracles                  | 已有 `QuestGuide` — **不併入** guidebook index             |


### 與 Obsidian／agent-memory 對照（INFERENCE）


| 概念              | Phase A              | Phase B                         | Phase C                          |
| ----------------- | -------------------- | ------------------------------- | -------------------------------- |
| Note              | `GuidebookEntry`     | + links／category／related        | + chunks                         |
| Vault index       | disk + inverted item | + title／token inverted          | + chunk inverted／IDF            |
| Backlinks         | —                    | entry↔entry（頁內／category）       | session「已示」≠ chat history      |
| Search            | itemId only          | title+token（高門檻）               | item+query top-k                 |
| Invent / hallucinate | **禁止**            | **禁止**                          | **禁止**（只 pin 檢索到的原文）        |


---

## 3. Approach（Phase A 總覽）

鏡像 `ItemIndex` / `ItemIndexCache`：

```
client ready / resource reload
  → async build GuidebookIndex from ResourceManager (patchouli_books)
  → disk config/packai/guidebook-index/ keyed by fingerprint
Ask (focus item)
  → itemNs = namespace of focus item id
  → lookup index by itemId
  → filter by **user setting** `guidebookScope`（預設 same_mod）
  → format [GUIDE] FACT pins (capped)
  → soft-dep API **僅當 index miss**（同 filter；單一路徑 pin；非 enrich／verify 雙寫）
miss → empty guide (honest)
```

**Scope 預設（使用者 2026-08-12）：** 預設只送 focus 物品同 mod 的書；**設定可改**為允許任何 book 只要 linkedItems／API 命中。**`guidebookScope` 的 ns 過濾僅在 `itemNs` 存在時套用**（無 focus item 見 §6 Phase B）。

**真相優先序（鎖定 — 全 phases；guidebook = advisory）：**

1. **Structured game／data FACT（已在 Ask）** — recipes／JEI cards、unlock gates、loot／KubeJS indexes（若有）、quest data、item registry 等；**「讀 code／data」最正確**。Guide **不得**覆寫此層。  
2. **Indexed guidebook／Patchouli text**（resource／datapack JSON，macro-stripped）— **advisory** only；可能被 pack 改過、過時、或錯。  
3. **Soft-dep live Patchouli** — **僅當 index miss** 的備援；**同 advisory 層**（同一 Ask **不**雙 pin、不做交叉覆蓋）。  
4. **Never:** JEI tooltip-as-guide、LLM invent／腦補。

**衝突規則（鎖定）：** guide 與 (1) 矛盾 → **prefer data**；可同時 pin 兩邊若有用，但 prompt **必須**說 guide **不** authoritative；**勿讓 guide 勝**。Miss／矛盾 → 誠實（不 invent）。

**Ask lookup 禁令（Phase A）：** index miss → API-only（若有）→ else `""`。**禁止** Ask 路徑再 `listResources` 全掃／「短掃」（除 WP0 debug 開關）。

**Ponytail:** 擴現有 `PatchouliEntryScan` + 新薄 `GuidebookIndex`／`GuidebookIndexCache`；勿重寫 AskEngine 大段。`PatchouliGuideLookup.lookup` 改查 index；miss 用 API 或空。

---

## 4. Data model

### 4.1 Phase A — Entry（記憶體 + disk JSON 一列）


| Field         | Type     | Notes                                                              |
| ------------- | -------- | ------------------------------------------------------------------ |
| `bookId`      | string   | 穩定 id；**ownership ns 以資源路徑為準**（見 §6 `bookNs`）                      |
| `entryId`     | string   | 相對 entries 路徑 stem / RL path（穩定、可 log）                             |
| `lang`        | string   | 該 entry 來源資料夾語系；A index **只建 client 當前語系**（+ en_us 填洞），不做多語並列 pin |
| `title`       | string   | entry `name`（可含未解析 i18n key — 原樣；勿瞎翻）                              |
| `textClip`    | string   | text-like pages 合併後單條硬上限建議 ≤ 2000；**進 Ask 前仍受總 cap**（§4 Caps）      |
| `linkedItems` | string[] | icon + extra_recipe_mappings keys + page item(s)，normalize 後去重     |
| `sourcePath`  | string   | resource path（debug／fingerprint 輔；用於推 `bookNs`）                    |
| `scoreHints`  | optional | 建置時可不存；lookup 時用 matchScore 邏輯                                     |


### Index document（A）

```text
Meta: mc, loader, langPreferred, modFp, formatVersion, entryCount
entries: Entry[]
item→entryIds: inverted map（disk 可內嵌或建置後記憶體重建）
```

**Caps（Phase A 建議起點，實作 WP 可微調寫進 changelog）：**

- Max entries stored: ~20_000（低於 item-index；書遠少於物品）  
- Max linked items / entry: 32  
- Max textClip / entry（建置）：2000 chars  
- Ask attach **總長優先**：  
  1. 候選按 score 排序
  2. 依序加入，**累計 ≤ 3000** 即停（可能只有 1 條）
  3. 單條仍可再截 ≤ 2000，但 **總長 3000 門檻優先於「湊滿 2 條」**
- 「top 2」= 上限條數，不是保證 2×2000

### 4.2 Phase B — 模型擴充（計畫；A 後實作）


| Field / structure     | Type        | Notes |
| --------------------- | ----------- | ----- |
| `categoryId`          | string      | entry `category` RL／path；供同書分類鄰近 |
| `linksOut`            | string[]    | 頁內／Patchouli link 解析出的 `bookId/entryId`（能解析才存；不猜） |
| `linksIn`             | derived     | 建置時反轉 `linksOut` → backlinks（Obsidian 味） |
| `titleTokens`         | string[]    | 正規化 title token（小寫、去標點）；供搜尋 |
| `relatedEntryIds`     | optional    | 輕量：同 category 或雙向 link 的 top-N（**非**向量圖；可記憶體重建） |

**Index 擴充（B）：**

```text
(+ A document)
category→entryIds
entry↔entry adjacency（或只存 linksOut，查詢時反轉）
titleToken→entryIds（或 trigram／簡單 posting；先 token 即可）
```

**Non-goals（B）：** 不做完整知識圖譜 UI；不做雲端 embed；不把「相關」當真相發明。

### 4.3 Phase C — chunk／memory（計畫）


| Field / structure   | Type     | Notes |
| ------------------- | -------- | ----- |
| `Chunk`             | record   | `entryId`, `pageIndex`, `text`, optional `tokenWeights` |
| `chunkStore`        | disk/mem | 建置時切 page／固定窗；仍來源 = JSON 原文 |
| Session memory      | client   | `shownEntryIds`／`shownChunkIds`（本 Ask session 或短 TTL）；**與 player chat history 分開** |
| Retrieve            | fn       | candidates = item-linked **only** if non-empty，else optional B title；score(candidates, query) → top-k → pin **只這些 clips** |

**Non-goals（C）：** 預設不上雲端 embedding API；優先本地 token overlap／IDF；若日後加 embed = Phase D 決策。

---

## 5. Index / cache fingerprint（Phase A；B／C 延用＋format bump）

對齊 `ItemIndexCache`：


|                     |                                                                                                                                         |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| **Disk dir**        | `config/packai/guidebook-index/`（B／C 可同 dir 子檔或 `formatVersion` bump）                                                              |
| **Meta**            | `mc` + `loader` + `lang`（= 建置時 client 語系）+ `modFp` + `formatVersion`（+ 可選 `resourceFp`）                                                 |
| **modFp**           | 重用 `ItemIndexCache.fingerprintMods(modId@version)` — 同包物品索引                                                                             |
| **Rebuild when**    | meta miss；**client lang 變**；resource reload；format bump；手動清 cache                                                                       |
| **Build thread**    | Async after client ready；**JSON-only `linkedItems`**（無 Patchouli API on async）。Optional enrich 若做 → **client thread only** + `formatVersion` bump（§2）。ResourceManager 快照策略 WP1／WP2 寫清 |
| **Lang policy（A）** | **只索引當前語系**（缺頁用 `en_us` 填洞）；不把多語 entry 並列進同一 Ask pin                                                                                    |
| **Hit**             | meta match → load JSON → rebuild inverted item map in memory                                                                            |
| **Miss / corrupt**  | rebuild；失敗 → 空 index + log 一行；Ask guide = ""。**未知／新於支援的 `formatVersion` → 視為 corrupt → rebuild**。 |


**INFERENCE:** 僅 mod list 指紋可能漏「同 mod 版本、只改 resource pack 書」— 若 NFWC 常改 KubeJS 書，WP2 可加輕量 `booksFp`（entry path 列表 hash）。A 可先 modFp + resource reload hook。

**B／C：** 新欄位 → **必 bump `formatVersion`**；舊 cache miss → rebuild；不手寫遷移。

---

## 6. Ask integration（Phase A 核心；B／C 延伸）

### User setting：`guidebookScope`（讓使用者選）


| Value              | Behavior                                             |
| ------------------ | ---------------------------------------------------- |
| `same_mod`（**預設**） | 只 pin `bookNs == itemNs` 的 entries                   |
| `any_mod`          | 任何 book 只要 item hit（linkedItems／API）都可 pin（仍受總長 cap） |


- **Config：** `PackAiConfig` + 設定 UI（`PackAiSettingsScreen`）CycleButton；lang en／zh_tw／zh_cn × Forge+Neo。  
- **Tooltip：** 「同模組手冊 vs 允許其他模組書提到此物」。  
- **Prompt：** 仍「只能引用 pin」；scope 只影響 **哪些 pin 進 FACT**。  
- **FACT prompt line（鎖定 — Ask／LLM）：** `[GUIDE]` = pack book text；**可能**過時／錯／與 recipes／quests／unlock gates 不符（pack 常改書）。若衝突 → **trust recipes／unlock／quest data over guide wording**；guide **不** authoritative；可注「書寫 X、資料 Y」，**不 invent**。

**Ownership（單一真相）：**


|              |                                                                                                  |
| ------------ | ------------------------------------------------------------------------------------------------ |
| Focus item   | `ns:path` → `itemNs = ns`（小寫）                                                                    |
| `**bookNs**` | 資源路徑 `…/assets/<ns>/patchouli_books/…` 的 `<ns>`（**鎖定**）。`bookId` 字串僅顯示／log；**filter 不靠猜 bookId** |
| Soft-dep API | 結果必須映回同一 `bookNs`；映不到 → `same_mod` 下丟棄                                                           |
| `same_mod`   | `bookNs ≠ itemNs` → 丟棄                                                                           |
| `any_mod`    | 不過濾 ns；仍 accuracy／cap                                                                            |
| 多書同 ns       | 允許；仍受總長 cap                                                                                      |


**預期行為（免當 bug）：**


| Case                                    | `same_mod`      | `any_mod` |
| --------------------------------------- | --------------- | --------- |
| `botania:…` 物品 + Botania 書              | 可 pin           | 可 pin     |
| `minecraft:iron_ingot` + 他 mod 書 linked | **空 GUIDE**（預期） | 可 pin     |
| Addon `addon:foo` + 主 mod 書             | **空**（預期）       | 可 pin     |
| 無任何 hit                                 | 空               | 空         |


### When to attach `[GUIDE]`


| Trigger                              | Attach?                    | Phase |
| ------------------------------------ | -------------------------- | ----- |
| Focus item hit + 通過 `guidebookScope` | **Yes**                    | A     |
| PURPOSE／「guide／手冊」+ 同上               | Yes                        | A     |
| Hit 被 `same_mod` 濾掉                  | **No**（誠實 miss）            | A     |
| 無 focus item + guide intent（PURPOSE／手冊 keywords）+ title/token **HIGH_NO_ITEM** | **No in A**；**Yes in B**（**忽略 `same_mod` ns**；搜全部 indexed books；仍 cap） | B |
| 有 focus + item lookup 空 + B2 title search | No in A；Yes in B（**仍套 `guidebookScope`／`itemNs`** + 高門檻） | B |
| 弱 casual 問句、無 guide intent、無 item hit | **No**                     | B     |
| Related-entry 擴一跳（有 link）            | No in A；可選 in B（擴後 **再套同一 `guidebookScope`**） | B     |
| Chunk top-k by item+query            | No in A；Yes in C            | C     |
| 無 hit                                | **No**                     | all   |


### Format

- `AskPurposeContext.GUIDE_HEADER` = `[GUIDE]`。  
- Pin 前綴：`bookId/entryId | title` + textClip（套用 §4 總長優先 cap）。  
- FACT：LLM **只能引用** pin；禁止外推。  
- FACT 須含上列 **guide-secondary** 行：`[GUIDE]` 不覆寫 recipe／quest／unlock；衝突 → data 勝。

### Dedupe vs quest（單一可測規則 — A 鎖定；C chunk 同規）

```
normalize(guide title+textClip) 與 quest pin 重疊 ≥ 門檻
  → 從 [GUIDE] 刪該重複段（或整條 entry）
  → quest pin 不動
```

- **Phase C：** 同上規則套用 **chunk text**（overlap → **drop guide chunk，keep quest**）。  
- **不做**「留較短一側」／「進度 vs 機制」語意分類（易互斥；A 不做）。  
- Marker repair：guide **不**發明 `{{item:}}`；不從 guide 名猜 registry。

### Soft-dep vs index（鎖定）

```
hits = indexLookup(item, scope)
if hits.isEmpty:
  hits = apiLookup(item, scope)   # 可選；無 Patchouli → 仍空
pin = format(hits)                # 單一路徑文字
```

- **Build（A）：** resource JSON scan 必做；**async 只寫 JSON `linkedItems`**。Optional API enrich → **client thread only** + format bump（非 Ask 常態）。  
- **Ask：** index → 空 → API once；禁止 full／短 `listResources` fallback。  
- 兩者皆有命中同一 item 時：**只用 index clip**（不雙 pin）。

### Open book UI

- **Phase D / YAGNI:** 若 Patchouli 有穩定 client open-entry API 且一行可接，另開極小 WP；否則只給文字 pin +（可選）chat 顯示 `bookId/entryId` 供玩家手動開書。

### Phase B Ask 延伸（計畫）

```
base = itemLookup(item, scope)          # 同 A；有 focus 時套 guidebookScope(itemNs)

# B2 titleTokenSearch
if base non-empty:
  pin = format(base)                    # item 路徑優先；不混 title
elif has focus item:                    # item miss
  titleHits = titleTokenSearch(query, minScore=HIGH)
  filter guidebookScope using itemNs    # same_mod / any_mod 仍適用
  pin = format(titleHits)
elif hasGuideIntent(query):             # 無 focus：PURPOSE／手冊 keywords
  titleHits = titleTokenSearch(query, minScore=HIGH_NO_ITEM)  # 嚴於 item-miss
  # NO itemNs → ignore same_mod ns filter；搜全部 indexed books；仍 cap
  pin = format(titleHits)
else:
  pin = empty                           # 弱 casual、無 guide intent → 空

# B3 optional related hop
optional: expand one hop via linksOut／category
  → re-apply same guidebookScope        # itemNs 存在時 bookNs 過濾；same_mod 丟跨 mod links
  → 仍 cap；需設定或預設關
```

**鎖定（B2 scope）：** `guidebookScope` **只在 `itemNs` 存在時**做 ns 過濾。無 focus item = query-only 路徑；**不**用 `same_mod` 當 ns filter。

**門檻原則（B）：** 寧可 miss 也不要弱匹配灌 FACT；`HIGH_NO_ITEM` > item-miss `HIGH`；具體分數 WP B2 用 fixture 釘死。

### Phase C Ask 延伸（計畫）

```
if item-linked hits non-empty:
  candidates = item-linked only         # title hits do NOT dilute
else:
  candidates = optional B title hits (if enabled)
chunks = retrieveTopK(candidates, query, k)
pin = format(chunks)                    # 只 pin 這些 clips
session.markShown(chunk/entry ids)      # 下輪可降權／跳過；≠ chat log
```

**鎖定（C）：** item-linked 非空時，optional B title ∪ **OFF**（不併入 candidates）。
---

## 7. Ordered work packages

### Phase A — WP0–WP4（現 v1；確認後自 WP1 開工）

#### WP0 — Spike / inventory（短；少改行為）


|                |                                                                                   |
| -------------- | --------------------------------------------------------------------------------- |
| **Problem**    | 不知 NFWC Patchouli 書量、現況 Ask `[GUIDE]` 命中率、每次 `listResources` 成本                   |
| **Approach**   | 記：書本數、entry 數、3 個已知有書物品 Ask 有無 `[GUIDE]`；粗 timing 一次 full scan；**不修產品**（可加暫時 log） |
| **Files**      | 筆記進本檔附錄或 changelog；可選 debug log                                                   |
| **Acceptance** | 書面數字；WP1–2 cap／fingerprint 有依據                                                    |
| **Test**       | 手動 ×2 同物品                                                                         |
| **QA gate**    | 2× 症狀穩定 → logic N/A → 對齊後續 WP                                                     |


---

#### WP1 — 結構化 Entry + 離線／fixture 掃描（可測核心）


|                  |                                                                                                                                                                                      |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Problem**      | 只有 blob；難 dedupe、難測 book/entry id、難建 inverted map                                                                                                                                    |
| **Approach**     | 擴 `PatchouliEntryScan`（或薄 `GuidebookEntry` record）：從一則 entry JSON + path → Entry 模型；`linkedItems` 收集；`textClip`；path→`bookId`/`entryId`/`lang` 解析。**無 MC 執行時**可單測                    |
| **Files likely** | Forge+Neo：`PatchouliEntryScan.java`（或新 `GuidebookEntry.java`）；`tests/check_patchouli_entry_scan.py` 擴充 / `tests/check_guidebook_entry.py`；可選 Java `-ea` check                        |
| **Parity**       | logic/ 雙樹鏡像                                                                                                                                                                          |
| **Acceptance**   | ① fixture entry → 正確 bookNs／id／title／linkedItems／clip ② macro strip 回歸 ③ 無 item 連結的純 text entry **仍可解析進模型**（供 index 完整；**A Ask 無 item hit 不會 pin**——註明非 Ask 路徑） ④ 不引入 pack hardcode |
| **Test**         | 上列 check **×2**                                                                                                                                                                      |
| **QA gate**      | Mandatory §9；特別查：miss 空字串非假文                                                                                                                                                         |


**← 使用者確認「開始 Phase A / WP1」後由此開始實作。**

---

#### WP2 — Disk index + fingerprint（鏡像 item-index）


|                  |                                                                                                                                                             |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Problem**      | 每次 Ask full resource scan 不可擴                                                                                                                               |
| **Approach**     | `GuidebookIndex` + `GuidebookIndexCache`：async build；disk meta；hit/miss；inverted `itemId → entryIds`；resource reload／join hook。Reuse mod fingerprint helper |
| **Files likely** | Forge+Neo：`GuidebookIndex*.java`、`ItemIndexCache` 旁或共享 `ModFingerprint` 若已抽；client init／reload；`tests/check_guidebook_index.py`                             |
| **Acceptance**   | 首建／次載 skip；modFp 變 rebuild；corrupt→rebuild；NFWC 不凍；log 一行 reason                                                                                            |
| **Test**         | fingerprint fixtures **×2**；NFWC 冷啟動 log                                                                                                                    |
| **QA gate**      | 主線不凍；格式 version 欄位存在                                                                                                                                        |


**Depends on:** WP1。

---

#### WP3 — Wire Ask lookup + dedupe vs quest


|                  |                                                                                                                                                                                              |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Problem**      | `PatchouliGuideLookup` 仍活掃；與 quest 可能重複                                                                                                                                                      |
| **Approach**     | `lookup` → index + **`guidebookScope` 過濾**；soft-dep 同；設定 UI；§6 dedupe；prompt「GUIDE 僅引 pin」                                                                                                   |
| **Files likely** | `PatchouliGuideLookup`、`PackAiConfig`、`PackAiSettingsScreen`、lang×3×2、`AskService` 最小、tests                                                                                                  |
| **Acceptance**   | ① `same_mod`：同 bookNs 有書 → pin；他 mod linked → 不 pin ② `any_mod`：他 mod linked → 可 pin ③ `minecraft:` + 他書 + `same_mod` → 空（預期） ④ 切設定影響下次 Ask ⑤ 無 Ask 全掃 fallback ⑥ quest 重疊砍 guide ⑦ checks 綠 |
| **Test**         | unit **×2**；NFWC Ask ×2（hit/miss）；可選 `dist/cua_guidebook_w3.png`                                                                                                                             |
| **QA gate**      | guide ⊄ tooltip；無 invent                                                                                                                                                                     |


**Depends on:** WP2。

---

#### WP4 — Soft-dep Ask fallback + lang／extension 邊角（可選收斂）


|                |                                                                                                                                           |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **Problem**    | 僅 JSON 可能漏 runtime mapping；extension book、非 text 頁（spotlight 旁白）                                                                          |
| **Approach**   | **Ask-time：** index miss → API once（WP3 已接 scope）；**不**雙 pin。**Build：** 維持 JSON-only linked；optional client-thread enrich = 另決策＋format bump（非本 WP 預設）。可選 spotlight `text` 納入 extract；extension 政策寫死（**建議 skip** 同今日） |
| **Acceptance** | API-only mapping 物品在 Patchouli 在場且 index miss 時仍能 hit；無 Patchouli 時 JSON-only 仍 work；**不**暗示 async build 呼叫 API 補 inverted map |
| **Test**       | fixture + 可選 NFWC 一物品                                                                                                                     |
| **QA gate**    | soft-dep 類不在 compile 主路徑泄漏                                                                                                                |


**Depends on:** WP3（可與 WP3 尾並行評估）。

---

### Phase B — Obsidian-like（A DoD 後）

#### WP B1 — Links + category 進 index


|                |                                                                 |
| -------------- | --------------------------------------------------------------- |
| **Problem**    | Entry 孤立；無法沿書內結構找 related                                        |
| **Approach**   | 解析 `category` + 可穩定抽出的頁內 entry links → `linksOut`；建置反轉 `linksIn`；`formatVersion` bump |
| **Acceptance** | fixture：雙 entry 互鏈 → 雙向可見；無 link 不瞎造；scope 仍適用於 pin           |
| **Test**       | `tests/check_guidebook_links.py` ×2                             |
| **QA gate**    | 無 invent；Forge+Neo 對稱                                           |


**Depends on:** Phase A DoD only（**不**依賴 B2）。

#### WP B2 — Title + token search（高門檻）


|                |                                                                 |
| -------------- | --------------------------------------------------------------- |
| **Problem**    | 無 focus item／item miss 時玩家問「手冊怎麼寫 X」無路                             |
| **Approach**   | title（+ 可選 entryId stem）token inverted。**觸發：** (1) 無 focus → 需 guide intent（PURPOSE／手冊 keywords），`minScore=HIGH_NO_ITEM`，**忽略 `same_mod` ns**；(2) 有 focus + item miss → 可 B2，**仍套 `guidebookScope(itemNs)`** + HIGH。有 item hit 仍走 item 路徑。 |
| **Acceptance** | ① 弱匹配／無 guide intent 不 pin ② 強匹配 pin 原文 ③ 無 item + 低分 → 空 ④ item miss 下 same_mod 仍濾 bookNs ⑤ 不啟用雲端 embed |
| **Test**       | fixture 正／負例 ×2；NFWC 可選一句問句                                    |
| **QA gate**    | 誤傷率書面門檻；logic review                                             |


**Depends on:** Phase A DoD only（**不**要求 B1）。

#### WP B3 — Lightweight related（可選）


|                |                                                                 |
| -------------- | --------------------------------------------------------------- |
| **Problem**    | 單條 entry 不夠；玩家要「同章下一則」                                           |
| **Approach**   | 同 category 或一跳 `linksOut` 擴候選 → **再套同一 `guidebookScope`**（有 `itemNs` 時 bookNs 濾；`same_mod` 丟跨 mod）；**預設關或極嚴 cap**；仍總長 3000 優先 |
| **Acceptance** | 擴充不超過 cap；跨 mod link 在 same_mod 下不進 pin；關閉設定＝純 B2／A 行為          |
| **Test**       | unit ×2                                                         |
| **QA gate**    | related ≠ lore 發明                                               |


**Depends on:** B1（links／category）；可選與 B2 並行但 related 需 B1。

---

### Phase C — Memory / RAG-lite（B 核心後）

#### WP C1 — Page／chunk store


|                |                                                                 |
| -------------- | --------------------------------------------------------------- |
| **Problem**    | 整條 `textClip` 過粗；問句只相關一頁卻 pin 全文頭                              |
| **Approach**   | 建置時按 page（或固定窗）切 `Chunk`；disk／mem；`formatVersion` bump         |
| **Acceptance** | chunk 原文 ⊆ entry JSON；無跨頁胡拼假句                                   |
| **Test**       | `tests/check_guidebook_chunks.py` ×2                            |


#### WP C2 — Retrieve top-k by item + query


|                |                                                                 |
| -------------- | --------------------------------------------------------------- |
| **Problem**    | 需依問句選片段，而非整 entry                                               |
| **Approach**   | **Candidates：** item-linked 非空 → **只用 item-linked**（B title ∪ OFF）；else optional B title。本地 token／IDF → top-k；**只 pin 檢索 clips** |
| **Acceptance** | item hits 存在時 title 不稀釋候選；k 與總長 cap 共存；miss 誠實；**無 cloud embed**（除非日後 Phase D）；chunk↔quest 重疊砍 chunk（§6） |
| **Test**       | 正／負 query fixtures ×2                                           |


#### WP C3 — Session memory（已示 dedupe）


|                |                                                                 |
| -------------- | --------------------------------------------------------------- |
| **Problem**    | 多輪 Ask 重複貼同一 guide                                              |
| **Approach**   | client `shownEntryIds`／chunk ids（session 或短 TTL）；下輪降權／跳過；**存儲與 player chat history 分離**。**Clear `shown*` on：** clear chat、close Pack AI screen、logout／leave world |
| **Acceptance** | 上列 lifecycle 觸發後可再示；不污染對話 transcript 模型                           |
| **Test**       | 單元模擬兩輪 ×2；clear／close／leave 各至少一案                                 |


---

### Phase D — Defer forever-ish（明確清單；不進 A/B/C DoD）

見 §8。不排進任何 Phase A–C Done when。

---

## 8. Defer / Phase D（明確）


| Defer                                | Why                 | Upgrade path                                 |
| ------------------------------------ | ------------------- | -------------------------------------------- |
| Live watch open book screen（OCR／CUA） | 非準、非通用、焦點／DPI 地獄    | 靜態 index 足夠；若玩家要「當頁」再評估 Patchouli screen API |
| Create Ponder 場景全文                   | 非 Patchouli；API 面不同 | 獨立 scanner plan                              |
| Akashic Tome 內容                      | holder              | —                                            |
| 一鍵 Open Patchouli GUI                | API／版本差             | A WP4 後若一行 API 穩再做                           |
| Cloud／遠端 embeddings                  | 成本、隱私、離線包           | C 本地 IDF 不夠用再開 RFC                           |
| booksFp 細指紋                          | 可能 YAGNI            | 若 KubeJS 常改書而不改 mod 版再加                      |


**已從「純 defer」移出：**

| 原 defer                         | 新歸屬                                      |
| -------------------------------- | ------------------------------------------- |
| 問句-only／標題模糊搜全書                 | **Phase B（WP B2）** — 高門檻；非 Phase A         |


---

## 9. Mandatory QA gate（每 WP + 每 Phase）

同 accuracy-first 精神，**不得跳過**：

1. **2× test run** — 相關 `tests/check_*.py`（及 Java `-ea`）連續兩次皆綠
2. **Logic review** — FACT 進（JSON／index／chunk）→ `[GUIDE]` 出；miss 誠實；**無 invent**
3. **2× code review** — 無 pack hardcode；Forge↔Neo 對稱；無無關 refactor；guide ≠ tooltip
4. Playable Forge 變更 → jar → `dist`（versioned + `packai-1.19.2-forge.jar`）→ NFWC 單一 packai jar → smoke（`;`）
5. 寫入 `code_change_log.md`

### Suggested tests


| Phase | Test                                                                 | Covers                                |
| ----- | -------------------------------------------------------------------- | ------------------------------------- |
| A     | `tests/check_patchouli_entry_scan.py`（擴）或 `check_guidebook_entry.py` | Entry 模型／linkedItems／clip             |
| A     | `tests/check_guidebook_index.py`                                     | fingerprint hit/miss、cap、inverted map |
| A     | 既有 Ask／purpose checks（若有 GUIDE 鍵）                                    | 不回歸 prompt                            |
| B     | `tests/check_guidebook_links.py`                                     | category／links 雙向                     |
| B     | `tests/check_guidebook_title_search.py`                              | 高／低門檻正負例                              |
| C     | `tests/check_guidebook_chunks.py`                                    | chunk ⊆ 原文、top-k、session dedupe       |


---

## 10. Files likely（整條地圖）


| Area        | Forge 1.19.2 / Neo 1.21.1                                                               | Phase |
| ----------- | --------------------------------------------------------------------------------------- | ----- |
| Scan／model  | `logic/PatchouliEntryScan.java`, 新 `logic/GuidebookEntry.java`（可選）                      | A     |
| Cache／index | 新 `logic/GuidebookIndexCache.java`, `client/.../GuidebookIndex.java`（命名以實作為準）           | A     |
| Lookup      | `client/patchouli/PatchouliGuideLookup.java`                                            | A     |
| Soft-dep    | `compat/PatchouliBridge.java`, `PatchouliBridgeImpl.java`                               | A     |
| Ask／設定     | `AskService`, `PackAiConfig`, `PackAiSettingsScreen`, lang×3×2                          | A     |
| Links／search | index 擴充 + lookup 分支                                                                   | B     |
| Chunk／session | `GuidebookChunk*`、session store（client）                                              | C     |
| Tests       | `tests/check_guidebook_*.py`, 擴 `check_patchouli_entry_scan.py`                         | A–C   |
| Docs        | 本檔 status 更新；勿先改 CF 文案                                                                  | all   |


---

## 11. Risks


| Risk                                 | Phase | Mitigation                             |
| ------------------------------------ | ----- | -------------------------------------- |
| 大包上萬 entry → 記憶體／建置久                 | A     | caps；async；NFWC spike 數字               |
| i18n key 當 title 原樣難讀                | A     | 誠實原樣 > 瞎翻；API 在場可換 display string（WP4） |
| Resource 執行緒安全                       | A     | 對齊 ItemIndex 模式；spike 定策略              |
| Guide 與 quest 雙重敘事                   | A     | §6 **單一** dedupe：重疊砍 guide             |
| Soft-dep Patchouli 版本差（1.19 vs 1.21） | A     | BridgeImpl 隔離；JSON scan 為底             |
| 把 spotlight／crafting 當完整教學           | A     | 只抽 text-like；crafting 頁不當 lore         |
| Fingerprint 漏 resource-pack 改書       | A     | reload hook；必要時 booksFp                |
| Cap 2×2000 > 3000                    | A     | §4 **總長優先** 選入順序                       |
| `same_mod` + vanilla 空 GUIDE         | A     | §6 預期表；非 bug                           |
| bookNs 猜錯                            | A     | **路徑 ns 鎖定**                           |
| 背景執行緒碰 Patchouli／JEI                 | A     | 禁止；同 ItemIndex                         |
| Title search 誤傷灌 FACT                | B     | **高門檻**；無 item 需 guide intent + `HIGH_NO_ITEM`；負例測試；預設寧可空 |
| No-item B2 與 `same_mod` 語意衝突         | B     | **鎖定：** 無 `itemNs` → 忽略 ns filter；有 focus miss → 仍套 scope |
| Related 一跳變「百科幻覺」                    | B     | 預設關／嚴 cap；只原文；擴後 **再套 guidebookScope**（same_mod 丟跨 mod） |
| Chunk 切壞句子／跨頁假連續                     | C     | 以 page 邊界優先；不跨 entry 拼接                |
| Title ∪ 稀釋 item 路徑                    | C     | item-linked 非空 → candidates **僅** item；title ∪ OFF |
| Session mem 與 chat 搞混                | C     | **分開存**；clear on chat clear／關屏／logout／leave；文件＋測試釘死 |
| 過早上 cloud embed                      | C/D   | 計畫禁令；本地 IDF 先                         |
| Stale／wrong pack-edited books 誤導 LLM   | all   | Guide = **secondary**；§3 衝突 → data 勝；prompt 明說非 authoritative；**永不**覆寫 recipe／quest／unlock |


---

## 12. Done when

### Phase A DoD（v1 shipable）

- [x] WP1：Entry 模型 + fixtures 綠 ×2（2026-08-12）  
- [x] WP2：disk index + fingerprint hit/miss；async JSON-only（**代碼**；NFWC 建置 log 待 `jar`）  
- [x] WP3：Ask `[GUIDE]` 走 index；**設定 `guidebookScope`（預設 same_mod／可 any_mod）**；與 quest 不無腦雙貼（重疊砍 guide）；honesty prompt（**代碼**；CUA 略／no-popup）  
- [x] WP4：index miss 才 API；無雙 pin；extension skip；spotlight text（**代碼**）  
- [x] 無 pack hardcode；Forge+Neo 鎖步（源碼）  
- [ ] 整波 QA gate + changelog — changelog ✅；**gradle compile／jar 待使用者**  
- [x] **不宣稱** books = pack-accurate mechanics 真相；guide secondary；衝突 data 勝（§1／§3／#20）— `guide_advisory`  
- [x] **A 未做：** query-only 搜尋、OCR 開書、Ponder、Akashic、cloud embed、強制開書 UI（停在 A；不開 B）  
- [ ] 無 pack hardcode；Forge+Neo 鎖步  
- [ ] 整波 QA gate + changelog  
- [ ] **不宣稱** books = pack-accurate mechanics 真相；guide secondary；衝突 data 勝（§1／§3／#20）  
- [ ] **A 未做：** query-only 搜尋、OCR 開書、Ponder、Akashic、cloud embed、強制開書 UI  

### Phase B DoD

- [x] B1：category／entry links 進 index；format bump（v2；**代碼**；tests pending）  
- [x] B2：title+token search **高門檻**；弱匹配不 pin；A item 路徑不回歸（**代碼**）  
- [x] B3：related 可關＋受 cap（預設關；**代碼**）  
- [ ] QA gate × Forge+Neo；無 invent — **pending user `tests OK` / `jar`**  

### Phase C DoD

- [ ] C1：chunk store ⊆ 原文  
- [ ] C2：item+query top-k；只 pin 檢索 clips；本地 token／IDF（無強制 cloud embed）  
- [ ] C3（若做）：session 已示 dedupe **≠ chat history**  
- [ ] QA gate × Forge+Neo  

### Phase D

- [ ] （不勾 DoD）清單見 §8 — 永不自動納入 A/B/C  

---

## 13. Next step

1. **使用者確認本 roadmap**（尤其 A→B→C 順序與 B 搜尋門檻哲學）。  
2. 使用者明確說 **開始 Phase A / WP1** → 才實作結構化 Entry + 可測 scan（不先動 disk／Ask 大線）。  
3. WP0 spike 可與確認並行（僅筆記／log）。  
4. **在確認前：無產品 Java 代碼。**

---

## Appendix A — Repro / spike 欄（WP0 填）

**NFWC inventory（2026-08-12；離線 jar zip；未開遊戲）：**

| Metric | Value |
| ------ | ----- |
| mods jars | 231 |
| jars with `patchouli_books/**/entries/*.json` | 9 |
| unique books (`ns:bookId`) | **12**（goety×2, ars_nouveau×2, ars_*, extradelight×2, iss, modulargolems, dimdungeons） |
| entry JSON total | **1491**（en_us 755 / zh_cn 289 / ja_jp 222 / ru_ru 216 / zh_tw 9） |
| offline full parse all entry JSON | **~751 ms**（PowerShell StreamReader；非 ResourceManager） |
| Patchouli jar | `Patchouli-1.19.2-77.jar` present |
| Hexerei | jar present；**no** `patchouli_books` entries in jar |
| Botania / EvilCraft | **not** in this NFWC instance |

| Item id (NFWC) | 期望有書？ | 今日 `[GUIDE]`？ | Full-scan ms | Notes |
| -------------- | ----- | ------------- | ------------ | ----- |
| `ars_nouveau:source_gem` | Yes | **未測 live** | — | worn_notebook `world_generation.json` icon/text hit |
| `goety:cursed_ingot` | Yes | **未測 live** | — | black_book `magical_components.json` |
| `irons_spellbooks:common_ink` | Yes | **未測 live** | — | iss_guide_book `ink.json` |
| `extradelight:cheese` | Yes | **未測 live** | — | cookbook cheesecake/cheeseburger |
| `minecraft:iron_ingot` | Cross-mod only | **未測 live** | — | Goety hammer rumor linked → `same_mod` 預期空 GUIDE |

**WP0 acceptance：** 書面數字有；cap／fingerprint 可依 ~1.5k entries／12 books 起算。Live Ask `[GUIDE]`／RM scan ms 留 WP3 NFWC smoke。


## Appendix B — Locked decisions（確認後勾）


| #   | Topic         | Choice                                                                  |
| --- | ------------- | ----------------------------------------------------------------------- |
| 1   | GUIDE 文字來源    | Indexed JSON (+ optional API fallback)；禁止 tooltip-as-guide。**Mechanics 真相階層見 #20／§3** |
| 2   | Cache         | `config/packai/guidebook-index/` + modFp 鏡像 item-index                  |
| 3   | Ask（A）        | `guidebookScope`：`same_mod`（預設）／`any_mod`；**禁 Ask 全掃**；API **僅 index miss** |
| 4   | Query search  | **Phase B**（高門檻 title+token）；**非** Phase A                             |
| 5   | Open UI / OCR | **Phase D**                                                             |
| 6   | Soft-dep      | **Build：** JSON-only linked on async；optional enrich = client thread + format bump。**Ask：** index miss 才 API；不雙 pin；不交叉覆蓋 |
| 7   | bookNs        | `assets/<ns>/patchouli_books/` 路徑 ns                                    |
| 8   | Lang（A）       | 只建 client 語系（+ en_us 填洞）                                                |
| 9   | Cap           | 總長 ≤3000 優先於湊滿 2 條                                                      |
| 10  | Dedupe        | 重疊砍 guide（含 C chunk text），留 quest                                       |
| 11  | Roadmap order | **A → B → C**；D 旁路；不跳級                                                  |
| 12  | Embeddings    | C 優先本地 token／IDF；cloud = D                                                |
| 13  | Session mem   | C；與 player chat history **分開**；clear `shown*` on clear chat／close Pack AI／logout／leave world |
| 14  | Start         | **WP1 after user confirms Phase A / WP1** — 此前無產品代碼                    |
| 15  | B2 scope／無 item | 無 focus：需 guide intent；**忽略 `same_mod` ns**；`minScore=HIGH_NO_ITEM`。有 focus + miss：B2 **仍套** `guidebookScope(itemNs)`。`guidebookScope` ns 過濾 **僅當 `itemNs` 存在** |
| 16  | C candidates  | item-linked 非空 → **只用 item-linked**；title ∪ **OFF**（不稀釋）              |
| 17  | A build linked | async index build = **JSON `linkedItems` only**（無 Patchouli API on build thread） |
| 18  | formatVersion | 未知／新於支援 → corrupt → rebuild                                             |
| 19  | B WP deps    | B1／B2 各自只依 A；B2 **不**需 B1；B3 依 B1                                      |
| 20  | Guidebook ≠ truth | Book ≠ true；pack 可改書（RP／KubeJS／datapack）→ 可錯／過時／fiction。**Code／structured data > book.** Conflict → **data wins**；`[GUIDE]` advisory only，不覆寫 recipe／quest／unlock；可雙 pin + 誠實注明，不 invent |


## Appendix C — Phase 一覽（給人類掃）

| Phase | WPs        | 一句 |
| ----- | ---------- | ---- |
| A     | WP0–WP4    | Entry + disk index + item→`[GUIDE]` + scope |
| B     | B1–B3      | notes／links／高門檻 title search／可選 related |
| C     | C1–C3      | chunks + top-k + session 已示 dedupe |
| D     | —          | OCR／Ponder／Akashic／cloud embed／開書 UI |
