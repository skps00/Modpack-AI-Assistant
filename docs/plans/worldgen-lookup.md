# Pack AI — Worldgen lookup（結構 · 生態域 · 資源生成）

Status: **PLAN ONLY — waiting for user 「開始」.** 本回合未改 production Java／測試／jar。  
Generated: 2026-08-16.  
Related: [accuracy-first-next-wave.md](accuracy-first-next-wave.md)（QA gate）；[tool-modifier-read.md](tool-modifier-read.md)（計畫格式）；[summon-entity-recipes.md](summon-entity-recipes.md)（**兄弟、勿合併** — JEI 配方結果＝召喚實體 ≠ worldgen）；[ask-native-tools.md](ask-native-tools.md)（能力路徑＝`worldgen_lookup` tool；fallback 仍要本檔 WP5 `[WORLDGEN]` pin，弱模型才不編 Y／wiki）；[full-item-index.md](full-item-index.md)（物品索引 ≠ biome／structure／feature）；[guidebook-index.md](guidebook-index.md)（書＝advisory）。  
**Loaders:** Forge 1.19.2 + NeoForge 1.21.1 鎖步（語意同；Forge／NFWC = 主驗收）。ATM10 只當 Neo／另一包對照，**不**當 NFWC 事實。  
**Log:** repo-root `code_change_log.md`（先寫日誌再改碼）。  
**Version:** 實作波 **不 bump** `mod_version`（本地 0.1.13 smoke；公開上傳另見 `docs/RELEASE.md`）。

**開工門檻：** 使用者說「開始」或「開始 worldgen」。  
**分支：** 從 **`origin/main`（0.1.13）** 開 `feature/worldgen-lookup`。**勿**混 `feature/summon-entity-recipes`、`feature/ask-native-tools`、`feature/purpose-scrub-hold-y`。

**檔名：** 原擬 `structure-biome-lookup.md` **未落檔**。使用者加「also resource gen」後改此檔，涵蓋 biome + structure + ore／vein／geode／placed feature。

---

## 0. 使用者意圖（鎖定）

Playtest add-on：

1. 「structure and biome」— Ask **不能 check** 結構／生態域 **是什麼、在哪找**。  
2. 「also resource gen」— 礦脈／vein／configured+placed feature／geode／包覆寫的礦產，同樣不能 check。

**要：** Ask 能用 **本機包資料** 答：

- 這是什麼（id + display；registry／datapack 有才寫）
- 在哪找＝**註冊關係**，不是座標：`structure X 的 biomes＝…`／`placed_feature X 掛在 biome Y`／`structure_set Z 含 X`
- 資源生成：pack **有寫** 才寫 count／height／size；沒寫＝誠實 miss，**不准**腦補 Y 層或礦脈大小

**不要：** 新百科 UI；invent 座標／Y／vein size；`/locate` 掃世界；wiki；併進 summon-entity WP；bump／CF／CUA。

---

## 1. Goal / Non-goals

### Goal

問「村／林地府邸／平原／鐵礦／紫水晶／XX 礦在哪」時，Ask 仍：

1. **留 FACT**（建議 pin `[WORLDGEN]`，與 `[GUIDE]`／`[TOOL_BUILD]` 並列）
2. **只寫讀到的邊：** biome↔structure／structure_set、biome↔placed_feature、configured_feature 設定、explorer-map destination、FTB 任務若 **明確** 寫 biome／structure／dimension
3. 讀不到 → **誠實 miss**（「此包未索引到 X 的 worldgen」），LLM **不准**編原版 wiki 座標或「大概 Y=…」
4. NFWC ≠ ATM10：索引 **實例本地**（datapack／jar／kubejs），無 pack id 硬碼

### Non-goals

| 不做 | 原因 |
|------|------|
| 發明出生點／最近結構 XYZ | 客戶端無誠實 locate；即使用指令也是活體世界，不是 pack FACT |
| 發明 Y 層、vein size、count | 除非 `placed_feature`／`configured_feature` JSON **有該欄** |
| 活體 chunk 掃描／旁路 `/locate` | 慢、劇透、雙樹差、非 datapack 真相 |
| 外網 wiki／LLM 記憶當 FACT | 包會覆寫原版礦與結構 |
| 併入 summon-entity | 那是 JEI **配方輸出＝實體**；本題是 worldgen |
| Gateways `entity_loot`／寶箱 loot→物品 | 已有；chest 路徑名 ≠「結構在哪個 biome」 |
| ItemIndex 當 biome 百科 | 只索引物品 |
| 新 widget／地圖 UI | YAGNI |
| 玩家「我在哪」即時 biome（可後做） | 本題是 **包註冊**，不是座標 HUD |
| bump／CF／commit／CUA | 使用者禁 |

---

## 2. Investigate findings（FACT vs INFERENCE）

Bug-lookup：repo + Earth Online App `code_change_log.md` **無** biome／structure／ore-gen Ask lookup ✅。相近但**不同**：

- summon-entity JEI（配方結果＝召喚）
- `docs/ITEM_SOURCE_LOOKUP.md` §4「礦脈 → `worldgen/placed_feature`、`biome_modifier`」＝**人離線 SOP**，未接 Ask
- PackIndex loot／`JarLightIndex` L 事實＝物品掉落，不是 feature 放置

本回合 **未改** production Java。讀碼（Forge 1.19.2 為主；Neo 鏡像同類）。Codegraph MCP 本環境只露出 `mcp_auth`，改 grep／讀檔。

### 2.1 已有路徑（皆物品／配方／書／任務）

| 片 | 今日做什麼 | biome／structure／feature id？ |
|----|------------|--------------------------------|
| `ItemIndex` | JEI 成分 + `Registry.ITEM`；disk cache | **無**（物品 only） |
| `GuidebookIndex`／`[GUIDE]` | Patchouli entry；item→書 | **無結構化**。書正文可能提到「平原」＝advisory，**不是** registry 邊 |
| `PackIndex.graphFacts` | `recipe_needs`、loot／trade／quest_submit／obtain、script_use、removed | **無** worldgen 邊。`datapacks`／openloader／kubejs **會 walk `.json`**，但 `ingestGraph` 只認配方／loot／任務／腳本 |
| `JarLightIndex` | `scanModJars`（預設關）：`data/**/recipes` + `loot_tables` | **無** `worldgen/**`。Neo 1.21 另有 `loot_table` 路徑差，仍不是 worldgen |
| `LootForwardIndex` | loot／gateway → 物品 | chest 檔名偶爾像結構，**不當** structure FACT |
| `QuestGuide` | FTB／Heracles 標題／描述／**物品** id | **無** biome／structure／location／dimension task 解析。註解寫明 **item tasks only**（`PackIndex.ingestQuestAcquireEdges`） |
| JEI／`JeiLookup` | 配方卡 | **無** worldgen category 當結構／礦脈索引 |
| `AskPurposeContext` | tooltip／食物／屬性／Tetra | **無** 玩家維度／biome |
| `tests/` | 無 worldgen fixture | grep 空（僅 guidebook「structured entry」用詞） |

**FACT：** Ask 今日沒有 first-class biome／structure／placed_feature 索引。  
**INFERENCE（症狀）：** 問「林地府邸在哪／鐵礦哪挖」→ 無 FACT → LLM 抄原版記憶或說不知道；包若改礦或加模組結構，答案錯。

### 2.2 包資料在哪（人 SOP 有、Ask 沒吃）

| 資料 | 典型路徑 | 能當誠實 FACT 的內容 |
|------|----------|----------------------|
| Biome | `data/*/worldgen/biome/*.json` | id、`features[]`（placed_feature）、spawners；**通常不列結構**（1.19+ 結構在 structure／set／modifier） |
| Structure | `data/*/worldgen/structure/*.json` | `biomes`（id 或 `#tag`）、type、spawn_overrides |
| Structure set | `data/*/worldgen/structure_set/*.json` | 含哪些 structure、placement **spacing／separation**（不是 XYZ） |
| Tags | `data/*/tags/worldgen/biome\|structure\|structure_set\|configured_feature\|placed_feature` | tag→id 展開 |
| Configured feature | `data/*/worldgen/configured_feature/*.json` | type（ore／geode／disk…）、`config.size` 等 **僅當 JSON 有** |
| Placed feature | `data/*/worldgen/placed_feature/*.json` | 指向 configured + placement（count、`height_range`、biome filter）**僅當 JSON 有** |
| Biome 掛 feature | biome `features[]` 或 Forge／Neo `biome_modifier` `add_features` | 「此包把 X 放進 biome Y」 |
| Explorer map | loot `minecraft:exploration_map` `destination`（多為 structure **tag**） | 「地圖指向 tag／結構」，不是座標 |
| Pack 覆寫 | `kubejs/data`、`datapacks`、`openloader`、`global_packs`、`overrides` | **蓋 jar**；Ask 必須先 loose／kubejs 再 jar |
| FTB | `config/ftbquests/**/*.snbt` | **若** task 寫 biome／dimension／structure／location — WP0 才記真實 type 鍵；無樣本不 invent parser |

**FACT：** 原版＋多數模組 worldgen 在 **`mods/*.jar`**，不在 loose datapack。只掃 `datapacks/` → 原版鐵礦／村會 **誠實 miss**（可接受當 Phase 0；出貨需 jar／`ResourceManager`）。  
**INFERENCE：** NFWC 與 ATM10 的 jar／覆寫不同；同一套掃描器、兩份實例索引。

### 2.3 還缺什麼（未量測）

- NFWC 真實問句樣本（哪個結構／biome／礦、Ask 現況原文）
- 該包 loose datapack 有沒有覆寫 `worldgen/**`（KubeJS `addJson` vs 只靠 jar）
- FTB 是否真有 biome／structure task（**不**先寫死 `ftbquests:biome`）
- Neo 1.21 `loot_table` vs 1.19 `loot_tables` 已分；worldgen 目錄名大致同，WP0 對一筆 Neo jar 即可
- Explorer map 在 NFWC 是 loot function 還是村民交易

**沒有樣本就不要寫 mod 專用 worldgen parser**（例如某礦維度模組私有 JSON）。通用 datapack schema + tag 展開可以。

---

## 3. Options

| | 做法 | 完整度 | 風險 |
|---|------|--------|------|
| **A** | 只掃 loose：`datapacks`／openloader／kubejs／overrides 的 `worldgen/**` + tags + biome_modifier | 3/10 出貨（原版礦／村在 jar） | 誠實 miss 多；適合 WP1 可測核心 |
| **B（建議）** | A + `ResourceManager`／`mods/*.jar` 的 `worldgen/**`（cap＋指紋 cache，學 `GuidebookIndex`／`JarLightIndex`）。覆寫優先於 jar | 8/10「這包註冊了什麼」 | jar 掃描成本；須 cap／cache；預設可跟 `scanModJars` 或獨立開關 |
| **C** | 活體 `/locate`、chunk 掃、發明 Y | 假完整 | **禁** |
| **D** | 書／wiki／LLM 當 FACT | 假完整 | 與 guidebook 誠實規則衝突 |

**Recommendation: WP1＝A 可測模型；出貨片＝B。C／D 不做。**  
先 A 才能用 fixture 鎖 parser；B 才答得了原版＋模組。Resource gen 與 biome／structure **同一索引、不同 WP**。

---

## 4. Approach（開工後）

```
Ask「X 是什麼／在哪」
  → 辨認 seed：structure / biome / configured|placed feature / 物品當礦產物（僅當 feature config 寫到該 block）
  → WorldgenIndex（disk cache + 實例指紋）
       loose datapack／kubejs  蓋  jar／ResourceManager
  → 展開 tags
  → FACT 邊（有才寫）：
       structure S  biomes B|#tag
       structure_set Z  contains S
       biome B  placed_feature F
       F  → configured C  （type, size/height 僅 JSON 有）
       exploration_map  destination tag/S
       quest  僅明確 task 欄
  → [WORLDGEN] pin + 誠實 miss
  → LLM 禁編座標／Y／vein size
```

**優先序：** datapack／jar 讀到的邊 > 任務明確欄 > `[GUIDE]` 書句（advisory）> **永不** LLM 腦補 worldgen。

**雙樹：** 解析＋模型放 `logic/` 鏡像；1.19 vs 1.21 只允許路徑／registry API 差（`loot_tables`／`loot_table`、biome_modifier 命名空間）。語意鎖步。

**覆寫：** 同 id 以 loose／kubejs／openloader **勝** jar（pack 改礦＝真相）。

---

## 5. Work packages

### WP0 — Spike（可與「開始」同週；**先於 B／FTB parser**）

| | |
|--|--|
| **做啥** | NFWC Ask 各 1 句：結構、biome、一種礦／geode。記現況回覆。列 3–5 個真實檔：loose 覆寫 vs jar `worldgen/**`。FTB 若有 location／biome task，抄 **真實鍵名**。**不修碼** 也可 |
| **產出** | 本檔 Appendix A |
| **Accept** | 知道 A 夠不夠、B 是否必修；不 invent FTB／mod schema |

### WP1 — Biome + structure 模型＋loose datapack

| | |
|--|--|
| **做啥** | `WorldgenFacts`（或同等 record）：biome／structure／structure_set／tag 展開。掃 loose `worldgen/biome`、`structure`、`structure_set`、`tags/worldgen/**`。Ask 尚未接也可先測 parser |
| **測** | `tests/check_worldgen_lookup.py`：fixture JSON → 邊正確；缺欄 → 不編 biomes／座標 |
| **不** | jar、`/locate`、summon-entity、Y 發明 |
| **Accept** | fixture 綠；無 id 不編 |

### WP2 — Resource gen（configured + placed + biome 掛載 + 覆寫）

| | |
|--|--|
| **做啥** | 掃 `worldgen/configured_feature`、`placed_feature`、biome `features[]`、`biome_modifier`（add_features）。KubeJS／datapack **同 id 覆寫** 勝 jar（jar 在 WP3）。FACT：「此包把 F 放進 biome Y」；`size`／`height_range`／`count` **僅 JSON 有才寫** |
| **測** | 同測檔加：ore／geode fixture；無 height → 行裡 **不准** 出現發明的 Y；覆寫 fixture 蓋 base |
| **不** | 發明礦脈形狀、維度「通常在下界」除非 JSON／modifier 寫了維度／biome tag |
| **Accept** | 見 §6 resource 條 |

### WP3 — Jar／ResourceManager（出貨完整度）

| | |
|--|--|
| **做啥** | 學 `GuidebookIndex`／`JarLightIndex`：掃 jar `data/**/worldgen/**` + tags + biome_modifier；cap＋指紋 cache。預設策略 WP0 後定（跟 `scanModJars` 或獨立、預設開但 cap） |
| **測** | 假 jar／zip fixture 或 ResourceManager 快照字串；loose 覆寫仍勝 |
| **Accept** | 無 loose 覆寫時，原版／模組 id 能從 jar 出邊；關掃描 → 誠實 miss 不是假表 |

### WP4 — Explorer map + FTB（有樣本才做）

| | |
|--|--|
| **做啥** | loot／交易裡 `exploration_map.destination`。FTB：**WP0 證實的鍵** 才解析 biome／structure／dimension task；location 的 XYZ 可當「任務寫了這點」**標明來源＝任務**，不當世界 locate |
| **不** | 無樣本的假 task type |
| **Accept** | 有 destination 才寫；任務 XYZ 不偽裝成 `/locate` |

### WP5 — Ask 接線 + 誠實 prompt

| | |
|--|--|
| **做啥** | `AskEngine`／`AskService` 注入 `[WORLDGEN]`；`ReplyLang` 禁編座標／Y／vein size；與 quest／guide dedupe（data > `[GUIDE]`）。**Fallback 必做 pin**（弱模型）。能力路徑另見 [ask-native-tools.md](ask-native-tools.md) `worldgen_lookup` — 本 WP 不刪字串 pin |
| **測** | `tests/check_worldgen_ask.py`（或擴既有 ask fixture）：有邊 → pin；無邊 → miss 句；stub LLM 不得當「有座標」 |
| **Accept** | 見 §6 |

### WP6 — NFWC 煙測

Forge jar（JDK17、`GRADLE_USER_HOME` = repo `.gradle-user-home`）、仍 **0.1.13**、`dist` + NFWC **一個** packai。Neo 若碰過 → ATM10(1)。Skip CUA。不殺 javaw。Ask 結構＋biome＋一種礦：FACT 有邊或誠實 miss；不說假 Y。

---

## 6. Done when（出貨片＝WP1+WP2+WP5；B＝WP3）

- [ ] 結構問句：有 datapack／jar 邊 → FACT 含 structure id + biomes 或 structure_set；**無 XYZ**
- [ ] Biome 問句：有則寫 id／display + 已索引的 structure／placed_feature 邊；沒有 → miss
- [ ] Resource gen：有則寫「F 在 biome Y」；Y／size／count **僅 JSON 有**
- [ ] 覆寫勝 jar（同 id）
- [ ] 無邊 → 誠實 miss，不編 wiki
- [ ] 與 summon-entity、ask-native-tools **分檔、分分支、分 WP**
- [ ] Forge+Neo 鎖步；無 NFWC／ATM10 hardcode
- [ ] 測試綠；changelog
- [ ] NFWC 煙測（WP6）；**不 bump／不 CF／不 CUA**

---

## 7. Test / NFWC verify

**自動：** fixture JSON（biome + structure + ore placed_feature + 無 height 的負例 + 覆寫）。不改 `firstItemInSlot`／Hold-Y／summon-entity 測試。

**手動（你；agent 不 CUA）：** NFWC `;` Ask：(1) 一個模組或原版結構 (2) 一個 biome (3) 一種礦或 geode。過：FACT 有註冊關係或明確 miss。不過：記 Appendix A，不硬修 C。

---

## 8. 本回合摸過的檔（revert 用）

| 檔 | 動作 |
|----|------|
| `docs/plans/worldgen-lookup.md` | **新增**（本計畫；含 resource gen） |
| `docs/plans/summon-entity-recipes.md` | Related 一行（worldgen ≠ summon） |
| repo `code_change_log.md` | 頂部加計畫條；02:05 舊檔名標成未落檔／已取代 |
| Earth Online App `code_change_log.md` | 對應條 |

**未改** `forge/` `neoforge/` `tests/*.py` jar。未建立 `structure-biome-lookup.md`。

---

## Appendix A — NFWC worldgen spike（WP0 填）

| 問句 | 種類 | 現況 Ask | 資料在 loose 還是 jar | 覆寫？ | 備註 |
|------|------|----------|----------------------|--------|------|
| （未填） | structure / biome / ore / geode | 不能 check / 亂編 / … | | | |
| | FTB task 鍵名（若有） | | | | 無樣本不寫 parser |

填表前 **不要** 寫 C（locate）或未見過的 FTB／mod schema。
