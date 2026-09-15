## 7. 停手報告（SK 規則：第 3 輪未達 8:2 → 停手問 SK，唔開第 4 輪）

### ① 逐輪比分（正方 : 反方）
| 輪 | 對象 | 比分 | 結果 |
|---|---|---|---|
| R1a | v1 §5 抽取 | 正方 3 : 反方 7 | ❌ |
| R1b | v1 §6-9 整合 | 正方 2 : 反方 8 | ❌ |
| R2 | v2 架構（資料驅動／API-agnostic） | 正方 5 : 反方 5 | ❌ |
| R3a | v3 §2.1-2.3 ＋ S1/S2 | 正方 4 : 反方 6 | ❌ |
| R3b | v3 分期／fail-safe／S3 驗收 | 正方 3 : 反方 7 | ❌ |

### ② 卡死嘅載重決定（3 條）
1. **旗艦個案嘅「來源側」唔係 call 參數**：`server_scripts/curios/entity_death.js:21-27` 係檔案層物件字面值，`'kubejs:god_bless_empty_necklace'` 係 **property key**（唔屬任何 call 嘅 arg，同被消耗嘅 `setStackInSlot` 相隔 4 行）→「按 arg 位置判方向」對佢**結構性無效**。要加「enclosing-key binding」規則，而同一形狀仲有 3 個**唔係變換**嘅 const map（`utils/constdef.js:253 machineChestLootTable`／`:255 warpFoodMap`／`:274 tagWorth`）要排除。另一實例 `curios/entity_hurt.js:30-36`。
2. **「方向過濾」擋唔到走漏**：`OfficialDisplay.collectPeers`（`:144-159`）係掃**整條 fact 文字**、只排除 focus 本身 → 材料 id 同 `evidence` 路徑會變成「同 tag 其他成員」peer 流出。**真 trace 已證**：`ask-20260915-230340-…jsonl` event[27] 有 `同 tag 其他成員: kubejs/client_scripts/item_tooltips.js:2（無官方名）`，玩家版 event[54] 出 `同 tag 其他成員: 整合包脚本（無官方名）`。而且 v3 §2.3 打錯靶——`annotatableNamespace`（`:190-200`）對 `ns:path` 形 id **永遠唔 fire**（reject 名單加 `from=`／`to=` 係死碼）；真 leak 係 `source:`／`evidence=` 嘅**檔案路徑**被當 id。
3. **S3「答出 4 王名」用現有 pipeline 根本做唔到**：4 個王係 **entity id**（`bosses_of_mass_destruction:void_blossom` 等），`OfficialDisplay.hoverLookup → ItemResolver.stackFromId → Registry.ITEM` **只解物品** → 只會印 `（無官方名）`；而且王名喺另一檔 `utils/constdef.js:66-71` 嘅 const 陣列，喺 `entity_death.js:23` 用**變數名**引用 → 需要「跨檔 const 引用解析」＋「entity 名解析（`EntityType` lang，zh_cn／zh_tw）」兩樣新能力，v3 完全冇。
   （另更正：BOMD jar `zh_cn.json` 係 **暗夜巫妖** lich，我之前寫「暗夜巫師」係錯。）

### ③ 最貴嘅未知（要咩數據／能力才解得開）
- **entity 名解析 + 跨檔 const 引用**：唔做 → 「講到 4 王名」永不可能（驗收要降級為「講到『由空項鍊充能』＋ evidence」）
- **Phase 1 唔會自動令答案變好**：新 fact 會經 `purpose_lookup` 入模型（真 trace event[27]／[54]）→ 模型**可能講、亦可能唔講**（機率性）；而 SK 見到嘅「無法確認」係模型按 `[TOOL_MISS] acquire empty …` ＋ `[RECIPE_CARDS] 只 role=input → 禁止寫怎麼來` 自寫 → 要真正解決必須 Phase 2 改 answer layer
- **索引係 partial**：最新 log `files=400`，但 repo 實際 **634 個 js** → 旗艦檔入 index 係排序運氣（`client_scripts` < `server_scripts`），唔係設計保證
- **402 候選 vs 可分類 ≤10 行**：角色表只可能覆蓋幾個，造 402 行骨架＝假期望

### ④ 建議（SK 揀）
- **A（我建議，收窄範圍）**：只做**已核實、方向明確**嘅少數機制（`.altar`／`.itemOutput`／`.input`／`registerCustomRecipe` 系列產出側／map-key TRANSFORM）＋角色表 ≤10 行＋「未分類即靜默」；**唔碰 answer layer**（fact 只入 debug／trace）；驗收降級為「講到『由空項鍊充能』＋ `file:line` 證據」，**唔要求 4 王名**。成本細、風險低。
- **B（拆細）**：先修 ② 揭示嘅**真 bug**（peer 行漏 `kubejs/…js:26` 路徑落玩家畫面）——獨立細修、有真 trace 證據；KubeJS source 之後再議。
- **C（換方案）**：唔做全 pack 索引＋分類表，改「**玩家問到某物品時即時 grep 該 pack 腳本**」（每次 ask 掃 1-2 檔），避開 402 候選／cache／role table 全部問題；代價＝每次 ask I/O。
- **D（放棄）**：維持現狀，記錄為已知限制（AI 只覆蓋 JEI／tooltip／任務／loot 四源）。

（同 SK 報告格式：① 逐輪比分 ② 卡死載重決定 ③ 最貴未知 ④ 建議——已按 AGENTS.md「Plan Review 上限 3-4 輪」要求提交。）
