# Plan：一律用「官方顯示名」＋嚴禁意譯 item id（SK 規則 2026-09-14）

狀態：**已實作（forge harness 綠；真機待驗）**（SK：「always use 官方顯示名」）；只改 `forge/1.19.2`

## 事件（實錘）
玩家問「命令开胸器」`stray_expansion:chestopener_command`，AI 答句出現「**暗鋼閃電**」：
- 包內真名 = **「龙霆钢开胸器」**（`stray_expansion:chestopener_dsteellightning`；冰火龍鋼系 dsteelfire=龙炎钢／dsteelice=龙霜钢／dsteellightning=龙霆钢）
- AI 喺注入資料（`kubejs/server_scripts/golden_age/chest_opener_tag.js` tag 清單、FTB 任務文字列出同系列全部 id）睇到**原始 id 字串**，自行拆字意譯：`d-steel`→「暗鋼」、`lightning`→「閃電」
- 同時把**另一件物品**嘅提示行講成所問物品 → 跨物品污染

## 規則（SK 定）
1. 一切面向玩家嘅文字**一律用官方顯示名**（lang／JEI 名）；id 只可以做括號補充（`{{item:ns:id}}`）。
2. **嚴禁自行意譯／翻譯／拆字解釋 item id**。官方冇名 → 只寫原 id。
3. 事實注入要**同時**提供 id 同官方顯示名（由 `ItemResolver`／`Plainify.displayName` 解析，唔靠模型）。
4. 牽涉**同 tag／同腳本／同任務**嘅其他物品，要獨立分段並標明（例如「同 tag 其他成員」），唔可以混入目標物品嘅資訊。

## 改動
1. **Prompt 硬規則**（`AskEngine` 規則區＋`ReplyLang` ×3 語）：加「一律官方顯示名；唔准意譯 id；無名只寫 id」＋一條 few-shot 反例（`chestopener_dsteellightning` → 龙霆钢开胸器，**唔可以**寫「暗鋼閃電」）。
2. **Facts 注入**（`KubeJsMechanicScan`／`QuestMechanicFacts`／`AskService`）：輸出每個 id 時附官方顯示名；tag／同檔其他 id 獨立分段標「同 tag 其他成員」；無法解析名 → 標 `(無官方名)`。
3. **Harness**：新 `AskDisplayNameCheck`（正例：id→官方名；反例：不得出現意譯字串；無法解析 → 只寫 id）＋ `tests/check_official_display_name.py`（靜態：prompt 規則存在＋facts 有附名邏輯＋負向控制）。

## 驗收（真機）
1. 問「命令开胸器」→ **唔准**出現「暗鋼閃電」；提同系列必須「龙霆钢开胸器（`stray_expansion:chestopener_dsteellightning`）」
2. 問「铁镐」等 → 名 = 官方名（鐵鎬），無意譯
3. 無官方名嘅 id → 原樣顯示，唔准自創
4. harness 全綠（含新 check）；`compileJava/compileTestJava` 0 error

## 還原
只改 forge 源碼，jar 由 `deploy_packai_jar.py` 部署（自動 backup）；回滾 = 用 backup jar 覆蓋 `mods/`。
