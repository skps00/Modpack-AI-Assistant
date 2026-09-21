# Slice 1b — 玩家文字 jargon 回歸 corpus（2026-09-22 實測）

來源：沙盒 `packai_sandbox` ＋真 instance `AI_test_NFWC_DIM` 嘅 `packai/trace/ask-*.jsonl`（`display.body.final` 事件，玩家可見 body 喺**頂層 `body`** 欄位，`data.body` 係空）。
用途：Slice 1b `InternalJargonCheck` 嘅輸入 fixture（每條都要變成 0 jargon 而句子仍完整），同 `tests/check_ask_display_leak.py` 新增 prose 掃描嘅負控樣本。

## A. 內部術語（要改寫成人話）

| # | 原文（verbatim） | trace |
|---|---|---|
| 1 | `本包未索引到钻石的世界生成资料，挖矿仍是原版常规途径。` | sandbox `ask-20260922-000814-minecraft_diamond.jsonl` |
| 2 | `本包未索引到它的矿脉／世界生成资料，能否自然遇到以实际地形为准` | sandbox `ask-20260922-001206-minecraft_amethyst_shard.jsonl` |
| 3 | `本包索引未收录它的世界生成、宝箱／掉落或任务取得路径，本地取得资料是空的` | sandbox `ask-20260922-000959-minecraft_crying_obsidian.jsonl` |
| 4 | `除此之外，本包索引没有这只开胸器的掉落、交易或任务取得路径，所以主要就是上面两条合成。` | real `ask-20260921-072649-stray_expansion_chestopener_command.jsonl` |
| 5 | `本包的掉落表、钓鱼、交易与脚本索引都没有这只星的取得路径` | real `ask-20260921-072740-witherstormmod_withered_nether_star.jsonl` |
| 6 | `本包的掉落表、宝箱、钓鱼、交易与脚本索引都没有它的取得路径` | real `ask-20260921-072935-golden_age_thunder_gem1.jsonl` |
| 7 | `本包索引没有它的掉落、交易或任务取得路径，所以主要就是上面两条合成。` | real `ask-20260921-073004-ars_nouveau_ritual_brazier.jsonl` |
| 8 | `本包索引没有它的掉落、宝箱、钓鱼、交易、任务或合成路径` | real `ask-20260921-073427-kubejs_god_bless_full_necklace.jsonl` |
| 9 | `本包的掉落表、宝箱、钓鱼、交易、任务与脚本索引都没有它的取得路径` | real `ask-20260921-074545-ino_dlc_saber_ankokuken_kurayami.jsonl` |
| 10 | `本包索引没有它的掉落、宝箱、钓鱼、交易或任务取得路径` | real `ask-20260921-074619-golden_age_active_charm.jsonl` |
| 11 | `本包的掉落表、宝箱、钓鱼、交易与任务索引都没有它的条目` | real `ask-20260921-081111-create_schematicannon.jsonl` |

## B. 機翻／半 raw 掉落表名（**唔喺本 slice 範圍 → Slice 1c／2**）

| # | 原文 | trace |
|---|---|---|
| 12 | `本地宝箱与器官脚本：暮色森林水井战利品表、下界墓穴宝箱（宝藏肋骨）、citadel 掉落表都有钻石` | sandbox `ask-20260922-000814-minecraft_diamond.jsonl` |
| 13 | `开箱子：本包掉落表把它放进「地下墓穴宝物箱」（treasure rib）和 citadel 结构的箱子，开箱可得。` | real `ask-20260921-230304-minecraft_crying_obsidian.jsonl` |

**源頭原文（工具結果，唔係玩家文字但係種子）**：
- `[WORLDGEN] 此包未索引到 minecraft:diamond 的 worldgen`（＝`WorldgenFacts.java:100`）
- `掉落：掉落表「treasure rib」|掉落表：bygonenether:chests/catacomb/treasure_rib`（＝`Plainify.lootLine:197` → `ReplyLang.lootTableObtain`）

## C. 唯一性斷言（要禁）

| # | 原文 | trace | 備註 |
|---|---|---|---|
| 14 | `本包没有掉落／交易／任务取得路径，合成就是唯一已知来源。` | sandbox `ask-20260921-223609-ars_nouveau_ritual_brazier.jsonl` | |
| 15 | `本包的掉落表、宝箱、钓鱼、交易、任务与脚本索引都没有它的取得路径，所以黑暗祭坛就是目前已知的唯一来源。` | real `ask-20260921-074545-ino_dlc_saber_ankokuken_kurayami.jsonl` | **有 `[card:1]` 都咁寫** |

## D. 唔准誤傷（要保留嘅正常句）

| 原文 | 出處 | 原因 |
|---|---|---|
| `通用知识（非本包覆写）：破坏废墟传送门框架、与猪灵以物易物也能获得。` | real `230304 crying_obsidian` | 正確標示通用知識 |
| `本包资料未列用途机制，通用知识（Ars Nouveau 模组，非本包覆写）` | sandbox `223609 brazier` | 正確措辭 |
| `世界生成：…`（`packai.reply.worldgen_ore`） | 遊戲 UI 標籤 | 正常玩家標籤，唔准當 jargon 刪 |
| `破坏 仪式火盆 会掉落`（Slice 1 新句） | real `235024 brazier` | Slice 1 成果，唔准退步 |

## E. Golden 輸出（zh_cn，供 `InternalJargonCheck` 逐條比對）

規則（zh_cn）：`未索引`→`未见`／`索引未收录`→`资料未收录`／`索引都没有`→`资料里都没有`／`索引没有`→`资料里没有`／殘餘 `索引`→`资料`／`唯一已知来源|目前已知的唯一来源|唯一取得`→`目前资料见到的来源`

| # | golden |
|---|---|
| A1 | `本包未见到钻石的世界生成资料，挖矿仍是原版常规途径。` |
| A2 | `本包未见到它的矿脉／世界生成资料，能否自然遇到以实际地形为准` |
| A3 | `本包资料未收录它的世界生成、宝箱／掉落或任务取得路径，本地取得资料是空的` |
| A4 | `除此之外，本包资料里没有这只开胸器的掉落、交易或任务取得路径，所以主要就是上面两条合成。` |
| A5 | `本包的掉落表、钓鱼、交易与脚本资料里都没有这只星的取得路径` |
| A6 | `本包的掉落表、宝箱、钓鱼、交易与脚本资料里都没有它的取得路径` |
| A7 | `本包资料里没有它的掉落、交易或任务取得路径，所以主要就是上面两条合成。` |
| A8 | `本包资料里没有它的掉落、宝箱、钓鱼、交易、任务或合成路径` |
| A9 | `本包的掉落表、宝箱、钓鱼、交易、任务与脚本资料里都没有它的取得路径` |
| A10 | `本包资料里没有它的掉落、宝箱、钓鱼、交易或任务取得路径` |
| A11 | `本包的掉落表、宝箱、钓鱼、交易与任务资料里都没有它的条目` |
| C1 | `本包没有掉落／交易／任务取得路径，合成就是目前资料见到的来源。` |
| C2 | `本包的掉落表、宝箱、钓鱼、交易、任务与脚本资料里都没有它的取得路径，所以黑暗祭坛就是目前资料见到的来源。` |

## F. zh_tw fixture（繁體輸入；**唔可以拎簡體句套繁體表**）

| # | 輸入 | golden |
|---|---|---|
| T1 | `本包未索引到鑽石的世界生成資料，挖礦仍是原版常規途徑。` | `本包未見到鑽石的世界生成資料，挖礦仍是原版常規途徑。` |
| T2 | `本包索引沒有它的掉落、交易或任務取得路徑。` | `本包資料裡沒有它的掉落、交易或任務取得路徑。` |
| T3 | `本包的掉落表、寶箱、釣魚、交易與腳本索引都沒有它的取得路徑` | `本包的掉落表、寶箱、釣魚、交易與腳本資料裡都沒有它的取得路徑` |
| T4 | `本包索引未收錄它的世界生成資料` | `本包資料未收錄它的世界生成資料` |
| T5 | `合成就是唯一已知來源。` | `合成就是目前資料見到的來源。` |

**混排斷言**：zh_cn 輸出唔准含 `資料`／`裡`；zh_tw 輸出唔准含 `资料`／`里`。

## G. 驗證方法（實作後要跑）

1. 逐條 A/B/C 輸入 → 新 pass 輸出：A 類 0「索引／未索引」、B 類（1c）0 raw 表名／0 機翻、C 類 0「唯一」；A／C 要**逐字等於 §E golden**。
2. 逐條 D 輸入 → 輸出**byte-identical**。
3. 真機 A/B：同一批問題（diamond／amethyst／crying_obsidian／brazier）＋Tetra 改裝版，掃 `display.body.final`。
