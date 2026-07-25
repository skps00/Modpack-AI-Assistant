# 整合包物品取得途徑 — 檔案追查流程

通用方法：在**本機整合包實例**裡，用物品 ID 反查「怎麼拿到」，而不是靠記憶或外網猜測。  
適用：KubeJS／資料包魔改包、Prism／MultiMC／官方啟動器實例。

可給：人類查攻略、Pack AI `AGENTS.md`、Cursor／寫腳本的 Agent。

---

## 0. 輸入要齊

最少要有一項：

| 優先 | 來源 | 例子 |
|------|------|------|
| 1 | 物品 ID（F3+H 進階提示／EMI／JEI） | `gud_toolkit:erratic_stone` |
| 2 | 模組名 + 顯示名 | Gud Toolkit / 异象石 |
| 3 | 只有中文名 | 先在 `kubejs/assets/**/lang/*.json` 反查 ID |

**原則：有 ID 再往下查；沒 ID 先找 lang，禁止瞎猜。**

實例根目錄（依啟動器而變）：

```text
<instance>/minecraft/          # 或 .minecraft/
  mods/
  kubejs/
  config/
  datapacks/                   # 若有
```

下文以 `<mc>` 代表這個目錄。

---

## 1. 全實例搜尋物品 ID

在 `<mc>`（至少含 `kubejs/`、`config/`、必要時 `mods/*.jar`）搜：

```text
<namespace>:<path>
```

例：`erratic_stone` 或完整 `gud_toolkit:erratic_stone`。

依命中檔類型判斷「是不是來源」：

| 命中類型 | 通常意義 | 是否「取得方式」 |
|----------|----------|------------------|
| `startup_scripts/**` 的 `event.create(...)` | 註冊物品 | 否 |
| `assets/**/lang/*.json` | 顯示名 | 否 |
| `data/**/recipes/**`、`**/shaped.json` 等 | 工作台／熔爐等配方 | **是**（產物） |
| `data/**/capsid_recipes/**`、`**/analyzer/**` 等機器配方 | 特殊機器轉換 | **是** |
| `server_scripts/**` 的 `BlockEvents`／`ItemEvents`／`LootJS` | 腳本掉落／互動 | **是**（常被 JEI 漏） |
| `data/**/loot_tables/**` | 方塊／寶箱／實體掉落 | **是** |
| 別的配方的 **ingredient** | 用途（拿它去做別的） | 否（用途，不是來源） |
| `emi.json`／JEI 快取 | 僅索引 | 參考即可 |

**先讀「result / output / probability / popItem / give」那邊的檔，再讀「拿它當材料」的檔。**

---

## 2. 配方檔：讀輸入 → 再追材料

打開產出該物品的 JSON／腳本後：

1. 記下 **機器或配方類型**（工作台、Capsid、造石機、分析儀…）
2. 列出 **所有輸入 ID**
3. 對每個輸入 **重複本流程第 1 步**（直到原版／常見礦物／任務書已知物）

### 特殊配方目錄（常見）

這些目錄名常被「任意命名空間」載入，不要因為不在 `minecraft/recipes` 就略過：

| 目錄片段 | 常見負責模組 | 玩家操作 |
|----------|--------------|----------|
| `**/capsid_recipes/**` | Alex's Mobs Capsid | 把物品放進膠囊體等待 |
| `**/analyzing/**`、`**/analyzer/**` | Unusual Prehistory 等 | 分析儀 |
| `**/metallurgic_infusing/**` 等 | Mekanism 等 | 對應機器 |
| `config/armorsets/*.json` | Armor Sets | 套裝屬性（多半不是「取得」，是效果） |

若不確定誰讀該目錄：在 `mods/*.jar` 內搜目錄名或 class 名（如 `CapsidRecipeManager`），確認模組後再寫給玩家的步驟。

---

## 3. 腳本掉落／事件（JEI 常缺）

在 `kubejs/server_scripts/**` 搜物品 ID，重點看：

```text
BlockEvents.broken(...)
BlockEvents.rightClicked(...)
ItemEvents.rightClicked(...)
LootJS.modifiers(...)
entityDeath / player.inventory 等 give / popItem
```

讀腳本時注意：

- **條件**：蹲下？手持物？維度？晝夜？
- **機率**：`probability`／`Math.random`／權重表
- **取消路徑**：`event.cancel()`、提早 `return`（例如蹲下改掉整塊方塊）

把條件寫進答案，否則玩家會覺得「機率不對」。

---

## 4. 掉落表與世界生成

| 目標 | 查哪裡 |
|------|--------|
| 方塊掉落 | `data/<mod>/loot_tables/blocks/*.json` 或 jar 內同路徑 |
| 寶箱 | `loot_tables/chests/**` |
| 生物 | `loot_tables/entities/**` |
| 礦脈 | `worldgen/placed_feature/**`、`biome_modifier/**` |

絲綢之觸／時運會改掉落時，要在步驟裡提一句。

---

## 5. 模組 jar 內查（KubeJS 沒寫時）

當搜尋只打到「用途」或 lang：

1. 在 `<mc>/mods` 找 `namespace` 對應的 jar  
2. `jar tf mod.jar` 或解壓後搜物品 path  
3. 讀 `data/.../recipes`、`loot_tables`、必要時用 `javap` 看事件邏輯（效果／機率公式）

效果類問題（擋傷、幸運、反射）優先看：

- `EventHandler`／`LivingHurtEvent`／`LivingAttackEvent`
- 整合包 **覆寫**（`kubejs/server_scripts/**` 裡對同物品的削弱／加成）— 以腳本為準覆蓋模組原版說明

---

## 6. 輸出給玩家的標準格式

查完用固定結構，避免只丟 ID：

```markdown
### 物品
- 顯示名：…
- ID：`namespace:path`

### 推薦取得（由穩到不穩）
1. **方法 A（主要）**：步驟 1 → 2 → 3
2. **方法 B（可選）**：條件 + 約略機率

### 注意
- JEI 可能看不到的腳本條件
- 整合包對原版模組效果的額外削弱／加成
```

遊戲內 Pack AI：優先引用 JEI／本地腳本事實；本文件是**離線追查 SOP**，與 JEI 衝突時以實例內檔案為準。

---

## 7. 檢查清單（查完勾）

- [ ] 已確認物品 ID（不是只靠譯名）
- [ ] 已區分「來源配方」vs「拿它當材料」
- [ ] 特殊機器配方已對上負責模組（玩家知道要開哪台機器）
- [ ] 腳本掉落已寫清條件與機率
- [ ] 材料鏈有至少追到「挖礦／任務／商店」之一
- [ ] 若有整合包覆寫效果，已與模組原版 tooltip 分開說明

---

## 8. 範例（精簡）

**問：怎么获得 `gud_toolkit:erratic_stone`（异象石）？**

1. 搜 `erratic_stone` → 命中 `data/tetra/capsid_recipes/erratic_stone.json`  
2. 輸入 = `unusualprehistory:opal_chunk`；目錄 = `capsid_recipes` → Alex's Mobs **Capsid**  
3. 追 `opal_chunk` → Unusual Prehistory 蛋白石礦掉落（万彩石块）  
4. 另命中 `loot_test.js` → 砸 `golden_age:foundation_crate_biotechnology`、不蹲、約 33%  

→ 主要：挖万彩石块 → Capsid 轉換；次要：砸指定箱子。

---

## 9. 給自動化／Pack AI 的提示（可節錄進 AGENTS.md）

```text
查物品來源時：
1) 先要 namespace:path
2) 在實例 kubejs/ 與 data/ 搜 ID
3) 優先 result/capsid/loot/BlockEvents，忽略單純 create() 與「當材料」
4) 特殊目錄要對上模組（capsid → Alex's Mobs Capsid）
5) 步驟寫成人話；機率與蹲下等條件必寫
6) 禁止虛構維基或未在檔案出現的合成表
```

---

## 相關文件

- 整合包作者自訂 AI 指引：[`PACK_AUTHOR.md`](PACK_AUTHOR.md)
- 範例 `AGENTS.md`：[`examples/packai_AGENTS.md`](examples/packai_AGENTS.md)
