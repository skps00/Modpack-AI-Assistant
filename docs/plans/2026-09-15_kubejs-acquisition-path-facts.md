# KubeJS 取得途徑 facts（acquisition paths from pack scripts）— plan v1

> 觸發：SK 2026-09-15 23:1x 問「can AI know how to craft this item?」（`kubejs:god_bless_full_necklace`／满溢神恩项链），AI 答「未找到產出它的配方…取得方式無法確認」→ **答案係錯嘅**：包內有三條路，全部只存在於 KubeJS 腳本。
> 倉庫：`C:\Users\skps9\Documents\Code_Project\super_minecraft_AI_player`（只改 **forge/1.19.2** 樹；NeoForge 樹 PAUSED）。
> 父規則：主契約 §第一規則（風險評估→最壞情況→還原方案→才動手）、全項目 plan → review 過閘（**正方 ≥ 8 : 反方 ≤ 2**）才實作、Research-first、玩家零動作、寫檔功能要 toggle＋硬上限。

## 1. 實錘（真機 + 真檔，全部 file:line）

個案：`kubejs:god_bless_full_necklace`（满溢神恩项链）。AI 答：**「未找到产出它的配方，JEI 里它只以材料（输入）身份出现…本地索引也没有它的掉落、钓鱼、交易或任务路径」**。

包內實際取得途徑（Prism instance `AI_test_NFWC_DIM/minecraft/kubejs/**`）：

| # | 途徑 | 證據 |
|---|---|---|
| 1 | **充能**：戴住「神恩项链（空）」打死 **虛空之花／暗夜巫師／黑曜巨石柱／下界鐵掌** 之一 → 直接變成「满溢」 | `server_scripts/curios/entity_death.js:22-26`（`'kubejs:god_bless_empty_necklace': function(...)` → `curios.setStackInSlot(slot, Item.of('kubejs:god_bless_full_necklace'))`）；tooltip `client_scripts/item_tooltips.js:72-73` ＋ lang `kubejs.tooltips.god_bless_empty_necklace.1`（`:1093`）「击败虚空之花、暗夜巫师、黑曜巨石柱、下界铁掌之一即可充能」 |
| 2 | **Goety 儀式合成** | `server_scripts/b_a_d/recipe/goety_ritual.js:142`：`registerCustomRecipe(new GoetyRitualRecipe('lich', [12 件 Ingredient.of('…')], Item.of('kubejs:god_bless_full_necklace'), Item.of('gateways:gate_pearl','{gateway:…}')).setSoulCost(666).setDuration(30))` |
| 3 | **祭壇催化（消耗）** | `server_scripts/ritual/summoning_rituals.js:435-439`：`.altar('kubejs:god_bless_full_necklace')… .itemOutput('kubejs:god_bless_empty_necklace')` |
| 4 | （空項鍊本身）地牢寶箱 | `data/dimdungeons/loot_tables/chests/chestloot_2.json:40`；JEI info `kubejs.jei.god_bless_empty_necklace.1`（lang `:857`） |

**根因**：我哋四個來源 ＝ JEI 配方／tooltip／FTB 任務／loot JSON。上面 1／2／3 都係 **KubeJS 伺服器腳本**語意：
- `registerCustomRecipe(...)` ＝ mod 自訂 recipe type（**唔經 vanilla recipe manager → JEI 見唔到**）
- `setStackInSlot(slot, Item.of('<out>'))` ＝ **事件式物品轉換**（唔係配方，任何配方掃描都見唔到）
- `.altar('<id>')` ＝ 儀式 builder 嘅催化位
→ 即係「連 JEI 都冇嘅取得方式」，玩家只能靠 tooltip／腳本。

## 2. 目標（一句）

令答案層喺 focus 物品有**腳本級取得途徑**時，能引用「途徑 ＋ 官方顯示名材料 ＋ 來源 file:line」；**搵唔到就唔講**（唔准靠估）。

## 3. 唔做（明確排除）

- **唔寫 JS parser**（只做定向 pattern；腳本花樣多 → miss 可接受，假 fact 不可接受）。
- **唔重複 JEI 已見嘅配方**（KubeJS `ServerEvents.recipes` 產生嘅 recipe 已係真 recipe → 唔再出 fact）。
- **唔改** card／mirror／emission 鏈（B11 剛定稿）；唔碰提示詞 lang 規則（SK 已定「唔改」）。
- **唔加新資料源 class**——先擴充現有 `logic/KubeJsMechanicScan.java`（見 §4），避免重複機制。

## 4. 現狀（已經有嘅嘢，唔准重造）

- `logic/KubeJsMechanicScan.java`（1838 行）已掃 `startup_scripts／server_scripts／client_scripts`，families 白名單 `ItemEvents／BlockEvents／EntityEvents／PlayerEvents／ServerEvents`；`SKIP_PREFIX = {ForgeEvents, ForgeModEvents, LootJS}`（註解：LootJS 已在 `PackIndex`）；核心 pattern 係「chance / use / conditional drop」類。
- 上限已存在：`MAX_FACTS_PER_ITEM=8`、`MAX_CLIP=200`、`DEFAULT_CACHE_FILES=500`、`DEFAULT_CACHE_MB=5`。
- 出口：`AskService:754-776`（`ensureStart` → `factsForItem` → `honestMerge`）；reload 失效 `KubeJsApiBridge:458/:478` → `invalidateOnReload`。
- **缺口精確**：全 repo **零命中** `registerCustomRecipe`／`setStackInSlot`／`altar(`／`itemOutput`／`soulCost`（2026-09-15 grep）→ 上面 1／2／3 三類**完全冇覆蓋**。

## 5. 設計（擴充 KubeJsMechanicScan，新增 3 類 fact）

新增 fact 種類（同一 facts 出口，`OfficialDisplay` 出官方顯示名）：

| kind | pattern（定向） | fact 內容 |
|---|---|---|
| `CUSTOM_RECIPE` | `registerCustomRecipe(` 同一 statement 內含 `Item.of('<out>')` | output=<out>（官方名）＋材料＝同 statement 內全部 `Ingredient.of('…')`（去重、保留順序）＋同 statement 內 `.setSoulCost(n)`／`.setDuration(n)`（有就帶） |
| `TRANSFORM` | `setStackInSlot(` 同一 statement 內 `Item.of('<out>')`，而該 handler 以 `'<in>':` 為 key（或同一 script 內 `<in>` 出現） | from=<in>（官方名）→ to=<out>（官方名）；trigger＝handler 所屬 family／檔案名 |
| `ALTAR` | `.altar('<id>')`（催化）／`.itemOutput('<id>')`（產出）／`.itemInput('<id>')`（材料） | 方向必須明確：`altar` = 催化（**唔當取得途徑**，除非同 statement 另有 `.itemOutput`）；`itemOutput` = 產出；`itemInput` = 材料 |

保守規則（硬性）：
1. 先剝**註解**（`//`、`/* */`）同**字串字面**以外嘅內容才做 pattern 匹配；**字串內**嘅 `Item.of('x')` 唔算（除咗 pattern 本身要求嘅參數字串）。
2. `event.remove(...)`／`.remove(` 出現喺同一 statement → **唔算**取得途徑（係刪配方）。
3. 一個 fact 若 output 方向唔明確（例如只有 `.altar('<id>')` 而該 id 係被消耗）→ 標 `consumes`，**唔准**講成「取得」。
4. 缺 `Item.of`／`Ingredient.of` → 丟掉（唔猜）。
5. 每個 fact 必須帶 evidence：相對路徑＋行號＋截句 ≤200 字（人類可核）。

資料形狀（沿用現有 facts 結構，唔加 network／檔案格式）：
```
kind=TRANSFORM from=神恩项链(空) to=满溢神恩项链 evidence=server_scripts/curios/entity_death.js:22-26
kind=CUSTOM_RECIPE out=满溢神恩项链 inputs=[…官方名…] soulCost=666 duration=30s evidence=server_scripts/b_a_d/recipe/goety_ritual.js:142
```

## 6. 落點（實作指引）

- `logic/KubeJsMechanicScan.java`：新增 pattern 常數 ＋ 三個抽取分支（純字串 parse，**唔准**依賴 Minecraft 類 → 保持 headless `-ea` 可測）。
- `AskService:754-776` 合并位：**唔改簽名**，只讓新 fact 種類流過（`honestMerge` 已有去重語意）。
- 顯示名：一律經 `logic/OfficialDisplay`（SK 規則：唔准自譯 item id）。
- 上限／設定：**沿用**現有 caps；如要新增 toggle，另開一輪（本 plan 唔加設定，避免動 `SettingsRegistry`／`check_settings_*` 閘）。

## 7. 驗收（可證偽，逐項要 RC）

- **S1 harness**（fixture 字串，唔靠 MC）：三個真 snippet（由 §1 file:line 直接抄）→ 期待 fact（kind／from／to／inputs／evidence 行號）齊。
- **S2 負對照**（每個都要「改前綠、改後紅」）：① 註解內 pattern → 無 fact ② `event.remove({output:'<id>'})` → 唔當取得途徑 ③ 只有 `.altar('<id>')` → 標 consumes 唔標取得 ④ `registerCustomRecipe` 冇 `Item.of` → 丟 ⑤ 同一 `Item.of('<id>')` 喺**別人家族**（`ForgeEvents`）→ 跟現有 skip 規則一致。
- **S3 真機**：問「满溢神恩项链」→ 答出 ① 充能（打死 4 王之一）② Goety 儀式（材料＋靈魂 666＋30 秒）；**負對照**問一件 JEI 已有配方嘅物品（如「寰宇支配之剑」）→ **唔應**多出 kubejs 假 fact。
- **S4 閘**：新 `tests/check_kubejs_acquisition_paths.py`（**forge-only**，NeoForge 樹 PAUSED 唔可以紅）＋ 現有 **115 check 全綠**、compile／harness 全綠。

## 8. 風險／最壞情況／還原

- **最壞情況**：pattern 誤配 → 答出**唔存在嘅取得途徑**（比「唔知」更差）。緩解＝§5 保守規則＋每 fact 帶 evidence＋S2 五個負對照＋S3 真機負對照。
- 材料清單過長（`MAX_CLIP=200` 截斷）→ 只截句，唔截 fact 本體；材料多過 N（例如 12）→ 只列前 8 ＋「等 N 項」。
- 效能：掃描係一次過＋cache（已有 caps 500 檔／5 MB）；大 pack 掃描時間要實測（>5s 就要加 progressive）。
- **還原**：純新增 fact 種類，無資料遷移；`git revert` 一個 commit ＋ 刪 cache 檔即可回到今日狀態（cache 檔路徑會喺落地紀錄寫明）。

## 9. 未解（要 review／真機答）

- `setStackInSlot` 嘅 key 判定（`'<in>': function(...)` vs 陣列 map）喺真 pack 有幾種寫法？→ 需要真檔抽樣（本 plan 只鎖最保守一種）。
- 儀式類 fact 要唔要連 catalyst 一齊講（例如 §1 #3 滿溢項鍊係消耗品）→ 影響「取得 vs 用途」表述。
- 同 `PackIndex`／`LootForwardIndex` 嘅重疊（LootJS 已 skip）要唔要一併處理 `data/**/loot_tables` 嘅 transform 類（可能有 `set_count`／`functions` 變體）。
