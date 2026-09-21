# mcmod 對照核實 —— packai「取得途徑」事實正確性與完整性

- 日期：2026-09-21（DS 空閒時段）；SK 指示：「search mcmod's context to make sure that info in correct and not missing」
- 工具：`mcmod-cn-reader` skill（`python %LOCALAPPDATA%\hermes\scripts\mcmod.py`）
- **用途界定**：mcmod 只作**開發期 QA 對照（oracle）**，**唔入 code**（版權＋維護負擔，SK 已定案）；核實結論入 plan 驗收。

## 1. 逐 case 對照

| packai 焦點 | mcmod 頁 | mcmod 原文（節錄） | 我哋 fact | 判定 |
|---|---|---|---|---|
| `witherstormmod:withered_nether_star` | [591072 风暴之星](https://www.mcmod.cn/item/591072.html) | 「風暴之星是**凋靈風暴徹底被摧毀後掉落的戰利品**，掉落時會在玩家頭頂緩緩降落」「其唯一的用途就是制作风暴信标」「**當玩家獲得此物品時會完成挑戰【此波平，彼浪起。】**」 | 我哋只得成就（`inventory_changed`）＋用途；**冇掉落來源**（Java code 實作） | ✅ **A 案方向正確**：mcmod 明文證實「獲得＝完成成就」，成就即取得途徑。**漏嘅係「凋靈風暴掉落」呢句來源描述**（A 行應同時講掉落＋成就） |
| `witherstormmod:command_block_book` | [591070 命令方块附魔书](https://www.mcmod.cn/item/591070.html) | 取得＝**凋靈共生體（Withered Symbiont）100% 掉落**（另有 風暴樂事 mod 合成） | 我哋有 `loot_tables/entities/withered_symbiont.json`（實體掉落表） | ⚠️ **A 候選 #5 會重複**：已有非自掉 loot fact ⇒ **新增條件⑨**（已有 loot 途徑 → 唔出 A 行） |
| `ars_nouveau:ritual_brazier` | [414814 仪式火盆](https://www.mcmod.cn/item/414814.html) | 取得＝**工作台合成**：奧術基座×1＋魔力寶石塊×1＋金錠×3 → 儀式火盆×1 | 我哋 card:1 完全一致 ✅ | ✅ 一致；mcmod **冇**任何「破壞掉落」→ 證實我們 cache 嘅 `L\|blocks/ritual_brazier` 自我掉落線係**噪音**（C1／C7 過濾方向正確） |
| `tetra:modular_double`（下界合金錘） | [438528 木锤](https://www.mcmod.cn/item/438528.html) | 取得＝工作台：木板標籤×2＋木棍×2 → 木錘 | 我哋 card:1 一致 ✅ | ✅ 空框架合成一致。**但 mcmod 嘅 Tetra 頁只有合成表**（[211499 加工台](https://www.mcmod.cn/item/211499.html)、[552471 锤 HammerHead](https://www.mcmod.cn/item/552471.html) 都**冇機制文字**）⇒ **C8（零件升級路徑）mcmod 核唔到**，要靠 jar facts（已有）+ SK 領域知識 |
| `graveyard:corruption` | [624722 堕落精华](https://www.mcmod.cn/item/624722.html) | 頁存在但**簡介為空** | A 候選 | ⚠️ mcmod 收錄不全 |
| `goety:stormlander` | [792836 雷霆之锤](https://www.mcmod.cn/item/792836.html) | 頁存在但**簡介為空** | A 候選 | ⚠️ 同上 |
| `graveyard:white_bone_staff`、`dimdungeons:item_secret_bell`、`bygonenether:wither_waltz_music_disc` | 冇 | `find <id>` 零命中 | A 候選 | ⚠️ mcmod 未收錄（唔能作核實來源） |

## 2. mcmod 作為 oracle 嘅能力邊界（要記落 plan）

1. **中文物品頁覆蓋率唔均**：熱門 mod（Ars Nouveau、CWSM、Goety、Graveyard）有頁；細 mod／KubeJS 自訂物品（`kubejs:god_bless_empty_necklace`）**冇**。
2. **內容深度唔均**：同一 mod 有啲頁有完整「取得／用途／掉落」段落，有啲頁**只有合成表**（Tetra 全系列）。
3. **`/give` id 有版本陷阱**（skill 已知坑再現）：仪式火盆頁寫 `ars_nouveau:ritual`（新版本 id），真 1.19.2 jar 係 `ars_nouveau:ritual_brazier` ⇒ **跨版本結論唔准照搬**。
4. `search.mcmod.cn` 自家搜尋會間歇彈 CAPTCHA ⇒ 用 `search`（item search）＋ `find <id>`，唔用 search.mcmod.cn。

## 3. 對 plan 嘅影響（新增項目）

- **A 案**：
  - A 行措辭要**同時**cover「掉落來源（若有）」＋「成就」：即 mcmod 講「凋靈風暴被摧毀後掉落」＋「獲得時完成成就【此波平，彼浪起。】」＝我們 canonical 行應該照此結構（來源→成就），但**內容只可以來自 jar facts／成就 lang**（唔准照抄 mcmod 文字）。
  - **新條件⑨**：已有「非自掉 loot fact」或「非自掉 worldgen fact」嘅物品 → 唔出 A 行（避免重複；例：`command_block_book`）。
- **C1／C7**：過濾方向獲第三方證實（儀式火盆冇「破壞掉落」）。
- **C8**：唔可以用 mcmod 核；驗收只靠 jar facts（零件＋材料顯示名）＋真機目視。
- **新增 QA 步驟（C9，開發期用、不入 code）**：每次新增 fact 類型，抽 5–10 件物品用 mcmod 對照「有冇漏玩家重視嘅資訊類型」（掉落來源／結構生成／事件條件／任務獎勵）；發現覆蓋缺口 → 寫入 plan 備忘，唔直接貼 mcmod 文字。

## 4. ⚠️ mcmod 係 wiki（用戶貢獻）——**必須雙重核實**（SK 2026-09-21 明確提醒）

**已知反例（實測）**：儀式火盆頁寫 `/give id = ars_nouveau:ritual`，而真 1.19.2 jar 係 `ars_nouveau:ritual_brazier` ⇒ **mcmod 內容有版本漂移／滯後**，唔可以單獨當真相。

**核實優先序（由強到弱）**
1. **本 pack 真 jar 資料**（`data/**` JSON、lang、advancement）＝ version-exact、可重現 ⇒ **唯一可寫入 code 嘅來源**。
2. 官方 mod 倉庫／文件（GitHub README／issues、CurseForge 描述、官方 wiki）。
3. mcmod／其他 wiki（中文社群）＝ **只作「有冇漏玩家重視嘅資訊類型」嘅線索**，任何具體數值／id／機制都要 2＋3 交叉。
4. 遊戲內實測（最終仲裁）。

**落地**：本 artifact 所有 mcmod 主張一律**降級為「待交叉」**（除非同時有 jar 或官方來源支持）。C9 QA 步驟要寫明：mcmod 命中 → 必須用上面 (1)/(2) 或真機核對過先可以寫入 plan／code。

**已交叉核對嘅項目**
- `witherstormmod:withered_nether_star`：jar 內 `advancements/main/wither_storm_defeated.json`（`minecraft:inventory_changed` ← 本 pack 真資料，1 級來源）＋ mcmod 591072（3 級）＋ web 第二來源（見 §5）三邊一致 → 可採用。
- `ars_nouveau:ritual_brazier`：**jar 側**（`data/ars_nouveau/loot_tables/blocks/ritual_brazier.json`＋recipe）＋ mcmod 合成表一致（但 id 欄位唔一致，已記為反例）。

## 5. 第二來源交叉（風暴之星掉落）—— 三邊一致 ✅

| 來源 | 級別 | 原文／要點 |
|---|---|---|
| 本 pack jar：`advancements/main/wither_storm_defeated.json` | **1（version-exact）** | `minecraft:inventory_changed` ← `witherstormmod:withered_nether_star`；標題「此波平，彼浪起。」／描述「一劳永逸地摧毁凋灵风暴！」 |
| mcmod [591072](https://www.mcmod.cn/item/591072.html) | 3（wiki） | 「風暴之星是凋靈風暴徹底被摧毀後掉落的戰利品」 |
| 官方 wiki（wiki.gg，`crackerswitherstormmod.wiki.gg/wiki/Wither_Storm`） | 2 | 「A Wither Storm itself does not drop any items, however **if the Wither Storm is Phase 4.0 to 7.5, a nearby player will receive a Withered Nether Star**」 |
| GitHub 官方 repo issue #2070（`nonamecrackers2/crackers-wither-storm-mod`） | 2 | 討論「multipl...」玩家中邊個獲得 Withered Nether Star（＝確認係擊敗後發放） |

**結論**：三邊一致 ⇒ 可採用。**額外洞察（對 A 行措辭有用）**：官方 wiki 明確講「凋靈風暴本体唔掉任何物品，係**附近玩家直接收到**（Phase 4.0–7.5）」→ **正好解釋為何冇 loot table**（掉落係程式碼發放），亦支持 A 行講「擊敗 boss 後直接獲得」而唔係「掉落表」。

## 6. 未做（下次可補）

- `bygonenether`／`dimdungeons` 用英文名或 DuckDuckGo `site:mcmod.cn` 再試（今次用 id 零命中）。
- 其餘 36 條 A 候選未逐條 mcmod 核（今次只抽咗 10 條中可在 mcmod 找到嘅 3 條）。
