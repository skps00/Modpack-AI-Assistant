# Pack AI — 整合包作者自訂（AGENTS.md）

玩家端 Pack AI 會讀取你放在遊戲目錄的 **Markdown 指引**，注入 LLM 的 system prompt，用來說明本包玩法、優先途徑、常見坑等。

概念類似 Cursor / 倉庫的 `AGENTS.md`，但是給**遊戲內助手**，不是給寫程式的 Agent。

## 放哪裡（先找到的為準）

1. `config/packai/AGENTS.md`（建議）
2. `config/packai/agents.md`
3. `kubejs/packai/AGENTS.md`
4. `kubejs/packai/agents.md`
5. `packai/AGENTS.md`（遊戲根目錄）

把範例複製過去即可：見 [`examples/packai_AGENTS.md`](examples/packai_AGENTS.md)。

## 行為

- 進遊戲 warmup／每次提問前會重新讀檔（改完存檔即可，通常不必重開遊戲）
- 最長約 **4000** 字元，超出會截斷
- 與 **JEI／任務書／本地腳本事實** 衝突時，**以遊戲本地資料為準**
- 玩家仍可用 Mods → Packai 調整模型、token、NBT 政策等

## 建議寫什麼

- 本包主線／推薦進程（一句到一小段）
- 「問合成時優先看什麼」（任務書 vs JEI vs 某機器）
- 已知魔改：某些 JEI 樣品 NBT 不要當硬門檻、或一定要講的條件
- 禁止助手亂編的內容（例如虛構維基頁）
- 物品離線追查 SOP：見 [`ITEM_SOURCE_LOOKUP.md`](ITEM_SOURCE_LOOKUP.md)；可節錄 §9 進 `AGENTS.md`（範例已含）

## 不建議

- 貼整本攻略或超長任務列表（吃 token、易被截斷）
- 要求模型忽略 JEI／本地事實
- 寫入 API key 或機密
