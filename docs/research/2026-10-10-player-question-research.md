# 玩家會問咩問題？— 研究（2026-10-10）

- 委託：SK（「do some research on what question will player ask」）
- 用途：決定「顧問引擎」要覆蓋邊啲題型、擴展次序
- 證據分級：**A＝官方一手／API 原文**、**B＝第三方平台原文**、**C＝媒體／二手**、**D＝我嘅推論**、**X＝做唔到**
- ⚠️ 全部來源都係**真實玩家問題**；HuggingFace 上嘅 MC QA 大數據集係 **LLM 合成**（見 §1 末），**唔可以**當玩家真實提問。

---

## 0. 一句話

玩家問嘅唔止「點做」；**最大需求其實係「農場／自動化點設計」「呢樣嘢係咩／做咩用」「有咩可以做 X」「要撳咩掣」**——呢幾類**大部分唔係配方圖問題**，而係「機器能力／數值 + 包內文件 + 模型知識」。（第二輪影片／wiki 證據見 §9）

---

## 1. 來源與方法

| 來源 | 攞到咩 | 級別 |
|---|---|---|
| **Stack Exchange（Arqade）API** | **300 條**真實問題（按票數取 top：`minecraft-java-edition`／`minecraft-mods`／`minecraft-feed-the-beast`） | A（API 原文） |
| **Modrinth API** | utility 類 mod 下載量 top 20（＝玩家**實際需求**嘅代理指標） | A |
| **ATM 官方 FAQ**（allthemods.github.io/alltheguides） | 官方認定嘅常見問題（技術／伺服器／遊戲） | A |
| **GTNH 中文維基 FAQ ＋ 新手建議** | 中文硬核包社群 FAQ（分階段問題） | A（社群官方 wiki） |
| **bilibili「整合包說明書」／mcmod／知乎** | 中文玩家實際文件（按鍵表、安裝／開服） | B |
| **HuggingFace MC QA 集**（390k／700k／300k／630k 條） | **LLM 合成 + 由 wiki 生成** → 只可當格式參考，**唔係**真實提問 | C |
| Reddit r/feedthebeast 週問帖 | **403 被擋**（datacenter IP） | **X** |

## 2. 題型分類（15 類，附真實證據）

### 2.1 遊戲內知識（我哋 mod 嘅範圍）

| 類 | 真實問題例子（來源） | 引擎覆蓋 |
|---|---|---|
| 1. 點做／材料鏈 | 「Optimal Crucible to Magmatic Dynamos ratio」（FTB tag, 5 票） | ✅ plan v4（A1／A2） |
| 2. 要邊部機 | 「What is the maximum EU per tick with a 6 Chambered Nuclear Reactor」（FTB, 6） | ✅ A3 |
| 3. 呢樣嘢係咩／做咩用 | Jade mod **70.5M 下載**（「shows information about what you are looking at」）；「What are these red lasers?」（FTB, 5） | ⚠️ 部分（`PurposeLookup`／`ConsumeUse` 要 item id） |
| 4. 喺邊度搵／取得 | 「How do I find Dungeons」（78）；「Faster way to farm ender-lilies」（FTB, 7） | ✅ 已有（Worldgen／Acquire／Loot 17,316 條） |
| 5. **能力反查**（有咩可以做到 X） | 「**What can give me near-infinite storage?**」（FTB, 6）；「Is there a way I can disable mods for specific players?」（mods, 6）；SK 問嘅「有咩可以儲 unstackable」 | ❌ **冇**（現時 14 個 tool **全部要 item id**） |
| 6. 比較／推薦 | 「**Advanced Pump vs BuildCraft Pump**」（FTB, 16）；「Is humus useless?」（FTB, 6）；「最有效率嘅挖礦策略」（358） | ❌ 冇 |
| 7. 數值／比率 | 「How much RF 出」（SK）；「Optimal pattern to place crops」（76）；「How far can mobs see」（84） | ❌ 冇（數值唔喺包檔案，見 §4） |
| 8. 運作／維持條件 | 「**How do I safely energize a hungry node without risk of degrading it**」（FTB, 18＝該 tag 最高票）；「How I make this generator keep working（MEK 反應堆）」（SK）；「Why does my AE Crafting System lose its jobs」（FTB, 10） | ❌ 冇 |
| 9. 效果／裝備能力 | 「this armor can give me what ability（Draconic／MekSuit）」（SK）；「What persists after respawn」（57） | ⚠️ 部分（registry／tooltip） |
| 10. 儲存／物流／自動化 | 「near-infinite storage」；「10,000 leaves stuck in itemduct loop」（FTB, 5）；「How do I transport items upwards from a monster trap」（56） | ❌ 冇 |
| 11. 佈局／設計 | 「Planning and analyzing lighting layout」（mods, 5）；「How far do I have to place torches」（54） | ❌ 冇（幾何／規則問題） |
| 12. 操作／按鍵 | bilibili 整份「**說明書**」＝按鍵表；「Changing the drop all keybind」（mods, 5） | ⚠️ 部分（mod 自己嘅 keybind 有；其他 mod 冇） |

### 2.2 技術支援（**唔建議做**；係另一個產品）

| 類 | 例子 | 註 |
|---|---|---|
| 13. 安裝／啟動器 | 「How exactly do I install Minecraft Mods and what is Forge?」（45）；「Why delete META-INF」（48） | ATM 官方 FAQ 已覆蓋；我哋 mod 係 client-side、唔應該做 |
| 14. 崩潰／錯誤 | GTNH FAQ：「我的游戏崩溃了，怎么办？」；「Ticking Entity Or Block」（ATM FAQ） | 我哋「診斷線」**已 SHELVED**（SK 2026-10-05） |
| 15. 伺服器／多人 | 「Why isn't my LAN server working?」（85）；「Can my friend and I play with different mods?」（21） | 唔關單機顧問事 |

### 2.3 元問題（學術／哲學）
「What's the goal of Minecraft?」（82）、「Is Minecraft Turing-Complete?」（156）→ 唔係產品範圍。

## 3. 需求排序（用「玩家實際裝咗咩」做代理指標）

Modrinth utility 類下載量（2026-10-10 實查）：

| Mod | 下載量 | 反映玩家想要 |
|---|---|---|
| **Mod Menu** | **151.9M** | 「**我個包有咩**」 |
| **AppleSkin** | 93.6M | 食物／數值資訊 |
| **VeinMiner** | 91.6M | 「**一鍵清一大片**」＝SK 問嘅「清 100×90」同源需求 |
| **JEI** | 81.3M | 「點做／有咩用」 |
| **Jade** | 70.5M | 「**我望住嘅係咩**」 |
| Xaero 地圖 ×2 | 115M + 100M | 定位／導航 |

→ 需求最強嘅係：**① 包內有咩 ② 呢樣嘢係咩 ③ 點做 ④ 有咩工具做到 X ⑤ 自動化／物流**。其中 **①③ 以外都唔係配方問題**。

## 4. Arqade 300 條真實問題（關鍵詞粗分；D 級推論，可入多桶）

| 類 | 條數（%） | 樣本 |
|---|---|---|
| 運作／條件 | 25（8.3%） | 「keep monsters out of my nether regions」(184) |
| 能力反查 | 22（7.3%） | 「Is there a way to keep Zombie Pigmen off minecart tracks」(62) |
| 係咩／做咩用 | 20（6.7%） | 「What is the terminal velocity of a sheep」(154) |
| 伺服器 | 20（6.7%） | 「Why isn't my LAN server working」(85) |
| 比較／推薦 | 19（6.3%） | 「most efficient mining strategy」(358) |
| 點做／材料 | 18（6.0%） | 「How do I make an effective SMP trap」(47) |
| 安裝 | 16（5.3%） | 「Why delete META-INF」(48) |
| 儲存／物流 | 14（4.7%） | 「transport items upwards from monster trap」(56) |
| 崩塌 | 12（4.0%） | 「list of error codes」(71) |
| 數值／比率 | 10（3.3%） | 「How far can mobs see」(84) |
| 喺邊度搵 | 10（3.3%） | 「How do I find Dungeons」(78) |
| 佈局／設計 | 8（2.7%） | 「minimum safe spacing between trees」(126) |

**偏差聲明**：Arqade 以 **vanilla** 玩家為主，modded 玩家嘅問題多數喺 Discord（冇公開 API）→ 呢個分佈係 proxy，唔可以當 modded 分佈。

## 5. 對 plan v4 嘅含意

1. v4 嘅 **A＋H**（材料鏈＋階段）只覆蓋 §2.1 嘅第 1、2 類（＋部分第 4 類）。
2. **需求最強嘅類唔喺 v4 範圍**：第 3（係咩）、5（能力反查）、8（運作條件）、10（儲存／物流）、6／7（比較／數值）。
3. **第 5 類（能力反查）係結構性缺口**：現有 14 個 tool 全部「先要 item id」，冇「由需求反查」入口。
4. **第 6／7 類（比較／數值）**：實測過（`2026-10-10-p0-recipe-metrics-report.md` §2）——數值**唔喺** Patchouli（3,741 檔只有 2.4% 提數值字眼，仲係散文）、**部分喺** config TOML（848 檔中 87 檔有數值行）、其餘要 runtime tooltip＋策展 → 一定要標「未收錄」，唔准估。
5. **技術支援類（13–15）唔建議做**：ATM／GTNH 官方 FAQ 已經做，而且唔係我們嘅差異化。

## 6. 建議

1. **把呢 15 類寫入題庫**（`docs/plans/2026-10-05-player-question-set.md` 擴充），每類附真實問題例子做測試句。
2. **擴展次序按需求排序**（§3）：先 ① 包內有咩（已部分有）→ ② 係咩／做咩用 → ⑤ 能力反查 → ⑧ 運作條件 → ⑩ 儲存／物流。
3. **能力反查（第 5 類）值得獨立做一個 spike**：來源＝Patchouli（ATM8 有 41 個 mod／3,741 檔）＋tooltip＋JEI category，量覆蓋率（同 P0 同法）。
4. 唔好做：安裝／崩潰／伺服器（13–15）。

## 7. 未做到／限制（誠實）

- **Reddit 週問帖**：API 403（datacenter IP 被擋）→ 改用 Arqade ＋ Modrinth ＋ 官方 FAQ 代替；**冇做成功**。
- **Discord 支援頻道**：冇公開 API，攞唔到體量最大嘅真實問題源（呢個係最大盲點）。
- Arqade 樣本偏 vanilla；關鍵詞分類係粗分（D 級）。
- 中文搜尋結果偏「安裝／開服／製作整合包」（搜尋偏差），唔代表中文玩家唔問遊戲內問題（bilibili 說明書／GTNH wiki 證明佢哋有問）。

## 8. 來源清單

- Stack Exchange API（Arqade）：https://api.stackexchange.com/2.3/questions?site=gaming&tagged=minecraft-feed-the-beast&sort=votes
- Modrinth API：https://api.modrinth.com/v2/search?facets=[["categories:utility"]]
- ATM 官方 FAQ：https://allthemods.github.io/alltheguides/help/faq/
- GTNH 中文維基（FAQ／新手建議）：https://gtnh.huijiwiki.com/wiki/常见问题解答
- FTB Wiki（r/feedthebeast 週帖機制）：https://ftb.fandom.com/wiki/R/feedthebeast
- Modrinth／CurseForge mod 頁（Jade／JEI／VeinMiner／Mod Menu／AppleSkin）
- HuggingFace MC QA 集（**合成，只作格式參考**）：Minecraft_QA-pairs_Instruction_Dataset／minecraft-question-answer-700k／Minecraft-QA-300k／Hydrus-Minecraft-QA

---

# 第二輪（SK 加料）：由「最多人睇嘅影片／wiki」反推玩家問題

> 方法：**唔靠估**，直接用「玩家實際睇咩」做代理指標。

## 9.1 方法與來源

| 來源 | 攞到 | 級別 |
|---|---|---|
| **YouTube**（yt-dlp `ytsearch`，9 組關鍵詞 × 25） | **225 條**影片＋**真實觀看數** | A（API 原文） |
| **Bilibili 官方 search API**（`search_type=video`） | 6 組關鍵詞、每組 20 條＋播放量（**部分關鍵詞 412 被擋**） | A（成功部分）／X（被擋部分） |
| **minecraft.wiki**（MediaWiki API `Category:Tutorials`） | **452 條**教學頁＋主題分佈 | A |
| **moddedmc.net wiki 分類** | 社群 wiki 自己點分類：Storage & Logistics／Tech & Automation／Magic／Adventure／Utility & QoL／**Troubleshooting & Errors** | B |
| ViewBoard mod（顯示邊個鍵未用） | 證明「按鍵衝突」係真痛點 | B |
| Reddit | **403 被擋** | X |

## 9.2 YouTube：最多人睇嘅片（按觀看數，225 條內 top 15）

| 觀看數 | 片名 | 反推出嘅問題 |
|---|---|---|
| **6,088,385** | 5 Automatic Farms to Start in Minecraft | 「**點開始自動化／點做農場**」 |
| 4,605,597 | Easiest Automatic Sugarcane Farm | 同上（單一作物） |
| 3,924,683 | 23 Super Simple Redstone Builds | 「點砌紅石」 |
| 3,560,556 | How to make an Auto Wheat Farm | 農場 |
| 3,507,333 | EASY Automatic Chicken Farm | 農場 |
| 3,014,528 | Easy IRON Farm Tutorial - **1300+ Per Hour** | 農場＋**產量數值** |
| 2,945,710 | The FASTEST Iron Farm - **1450+ Iron Per Hour** | **「邊個最快／最好」＋數值** |
| 2,944,990 | **BEST** MINECRAFT IRON FARM \| New Design | 比較 |
| 2,803,429 | Storage Room With Automatic Sorter | 儲存／物流 |
| 2,786,263 | Villager Auto Crop Farm Tutorial | 農場＋村民 |
| 2,352,202 | Ultimate Guide to Trains \| Create .5 | **「點用某功能」（火車）** |
| 2,281,372 | Create Mod Beginners Guide | **入門／點用** |
| 1,863,571 | Top 10 Clever Minecraft **HotKeys** | **按鍵／控制** |
| 1,646,317 | Using Science to MAXIMIZE **Mob Spawning** | **生成條件** |
| 1,178,391 | **BEST Mystical Agriculture Farm Design \| All The Mods 8** | **包專屬農場設計** |
| 786,681 | **Best Power Sources and Setups in All The Mods 10** | **包內發電比較（數值）** |

→ 標題分類統計（可多桶，D 級推論）：**農場／自動化 57**、使用功能／操作 76、比較／最好 33、點做 23、**生成條件 22**、安裝 22、**數值／效率 19**、**按鍵 15**。

## 9.3 minecraft.wiki 官方教學頁主題分佈（452 條）

| 主題 | 條數 | 佔比 |
|---|---|---|
| **農場 farming** | **97** | **21%（最大宗）** |
| 效能／技術 | 36 | 8% |
| 生存／開始 | 32 | 7% |
| 紅石／機械 | 23 | 5% |
| 建築 | 16 | 4% |
| 取得／位置 | 7 | 2% |
| 村民／交易 | 7 | 2% |
| 附魔／裝備 | 5 | 1% |

（例：Allay farming／Amethyst farming／Animal farming／Armor farming／Axolotl farming／Bamboo farming／Bartering farm／Basalt farming……）

## 9.4 Bilibili（中文平台，實查播放量）

| 播放量 | 片名 | 反推 |
|---|---|---|
| 4,226,187 | 我的世界1.19.4 教学式生存【一档到底】EP1 | 入門生存 |
| **2,889,635** | 我的世界:从零开始的**机械动力**入门教程【超齐全】 | 大 mod 入門／點用 |
| 858,229 | 适合小白看的机械动力6.0生存教学-01 | 同上 |
| 499,788 | 把这个调到最低，就可以打出外挂般操作？ | **按鍵／設定** |
| **338,445** | 萌新必看！我的世界入门级**按键设置**教程！ | **按鍵** |

→ 中文玩家同樣係「入門／教學／保姆級」＋**按鍵設定**；搜「刷怪塔／通用机械／自动化农场」時觸發 **412 被擋**（記錄為做唔到）。

## 9.5 新增題型（16–21）＋證據

| # | 題型 | 證據 | 引擎現況 |
|---|---|---|---|
| 16 | **農場／自動化設計**（「要怎樣做 xxx 農場」） | wiki 教學 21% 係 farming；YT 冠軍 6.09M 係自動農場 | ❌ 完全冇 |
| 17 | **按鍵／控制**（「要撳咩掣先用得到」） | ViewBoard mod；YT HotKeys 1.86M；bili 按鍵教學 338k／499k | ❌ 冇（但**可以自動抽**：mod 註冊 keybind 有 metadata） |
| 18 | **生成條件**（「某某嘅生成條件係咩」） | minecraft.wiki Mob spawning／Spawn-proofing；YT「Maximize Mob Spawning」1.65M | ⚠️ 部分（worldgen 有；mob spawn 條件係 Java code） |
| 19 | **點用某功能**（「我要點樣用某功能」） | YT Create Trains 2.35M；bili 机械动力入門 2.89M；Reddit「how do you auto drive trains」 | ⚠️ 部分（`Purpose`／`ConsumeUse` 要 item id） |
| 20 | **包內發電／能源比較** | YT「Best Power Sources in ATM10」786k | ❌ 冇（數值問題） |
| 21 | **每小時產量數值**（per hour） | top iron farm 片 3/5 標題寫「1300+／1450+ Per Hour」 | ❌ 冇（數值問題） |

## 9.6 更新後嘅需求排序（兩輪證據合併）

1. **點做／材料**（JEI 81M、wiki 教學 21% 係 farming 嘅物料部分）✅ v4
2. **農場／自動化設計**（YT 冠軍 6.09M；wiki 最大宗）← **v4 完全冇，需求最大**
3. **呢樣嘢係咩／做咩用**（Jade 70M）
4. **有咩工具／機器做到 X**（near-infinite storage 問題）
5. **點用某功能**（Create 系列影片動輒 2M+）
6. **數值／比較**（per hour、發電、電壓）
7. **按鍵／控制**（可自動抽，成本低）
8. **生成條件**（worldgen 有、mob spawn 要靠 code／wiki）

## 9.7 對引擎嘅含意（第二輪新增）

- **「農場／自動化設計」係最大需求，但佢唔係配方問題**——係「設計模式＋機器能力＋物流」嘅合成問題。要用：（a）包內機器資料（b）機器能力／數值（c）步驟排序規則；而知識來源多數係**影片／wiki**，唔係包檔案 → **必然要模型知識＋標明來源**。
- **按鍵係最便宜嘅一個**：mod 註冊 keybind 有 metadata（本 mod 自己都做過：`ClientSetup.java` 有 `key.packai.open`／`key.packai.think`），可以離線抽晒全包 keybind 清單 → 直接答「要撳咩掣」。
- **生成條件**：worldgen 部分喺 jar（biome modifier／placed features），mob spawn 條件係 code（要 wiki／模型知識）。
- **包專屬示範片收視高**（ATM8 Mystical Agriculture farm 1.18M）→ 支持我們「grounded in YOUR pack」嘅定位。
