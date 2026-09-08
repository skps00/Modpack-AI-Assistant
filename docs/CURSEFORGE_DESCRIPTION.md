# CurseForge project description (Pack AI) — REMADE 2026-09-08

Editor notes (do NOT paste): modeled on top CurseForge mods (JEI / Jade / JourneyMap / Create):
1-line bold tagline → prose intro → concise feature groups with small headings → FAQ → versions.
Keep it short. Screenshot insertion points marked with [SCREENSHOT: ...].

---

## English

**Pack AI Assistant — an in-game AI for heavy modpacks.** Ask in plain language; answers are grounded in *your* pack — JEI recipes, quest books, and local scripts — not a generic wiki guess.

*Screenshot coming soon — in-game question with recipe cards under the answer.*

Heavy modpacks are huge. You know what you want to build, but not where the recipe is, which quest unlocks it, or whether the item even exists in this pack. Pack AI answers in plain language — type or speak a question, get a short answer with recipe cards placed right under the relevant step, and a 【Sources】 line so you can verify.

No Python bridge, no server install. Put the jar in `mods`, press **`]`**, and ask.

## What it answers

**Recipes that match this pack.** With JEI installed, Pack AI reads the real R / U / catalyst data and the AI emits recipe cards itself — each card lands right after the numbered method it belongs to (workbench, auto-crafter, material recipes). Mirror machines (e.g. Kinetic Crafter / Mixer) merge into one card with an "also usable on" note.

**Focus: Forge 1.19.2** is the primary target and gets updates first. NeoForge 1.21.1 is kept in sync as a port.

**Quest-aware.** Related FTB Quests / Heracles entries are cited, and quests can be opened when the environment allows.

**Pack-local truth.** Facts come from KubeJS / datapacks / loot / trades — local data and JEI win over web search when they conflict.

**Enchant & repair answers from the game itself (0.2.0).** Which enchants apply is read from the game's registry (not a viewer), anvil repair materials from the actual repair predicate. Repair vs upgrade questions are separated.

**Tooltip-backed obtain hints (0.2.0).** If the item's own tooltip says how to get it, that shows as a low-confidence hint instead of a flat "unknown".

**Held Tetra tools.** Ask reads this instance's parts, sockets, and materials — the blank modular recipe is not how you obtained the tool in your hand.

## How to use

1. Download the jar for your loader (Forge 1.19.2 — primary target; NeoForge 1.21.1 port) — do not mix loaders.
2. Fully quit the game, put the jar in `mods`, start Minecraft.
3. Install JEI if you can — strongly recommended.
4. Press **`]`** in-game to ask (`/ai <question>` also works). Hover an item and hold **Y** (~1s) to ask about that item alone.
5. Mods → Pack AI → Connection: add your API key + Base URL through `/v1` only — or use Ollama / offline mode.

Remap keys under Options → Controls → Pack AI Assistant.

## FAQ

- **Does it need JEI?** Strongly recommended — recipes are JEI-accurate when JEI is installed. Without it, Pack AI still helps with quests, local facts, and guidebooks.
- **Do I need a server mod?** No — Pack AI is client-only.
- **Which LLM do I use?** Yours: any OpenAI-compatible `/v1` endpoint (OpenRouter, Groq, gateways…), local Ollama, or offline mode (quests / local facts / JEI summaries, no key).
- **Is it a cheat client?** No. It reads your pack and answers questions; it does not play for you. Assistive only — verify in-game.
- **Is it free?** The mod is free. Cloud LLM usage is billed by your provider; free OpenRouter models (`:free`) are often rate-limited (HTTP 429) — retry or switch.
- **Can I put it in my modpack?** Yes — client-only, no server side, MIT licensed.

## Supported versions

| Minecraft | Loader   | Status |
| --------- | -------- | ------ |
| 1.19.2    | Forge    | **Primary target** |
| 1.21.1    | NeoForge | Port (kept in sync) |

## Notes

- Languages: English, Traditional Chinese, Simplified Chinese.
- Extensibility (0.2.0): third-party mods can register their own Ask tools via the public `api/` package and registration events. Full schema/exec visibility for third-party tools is on the roadmap.
- Large JEI context uses more tokens — lower `maxJeiChars` / `historyTurns` in settings if needed.
- Source: https://github.com/skps00/Modpack-AI-Assistant

---

## 繁體中文（台灣）

**Pack AI Assistant —— 給重度整合包的遊戲內 AI。用白話提問，答案建立在「你的包」上：JEI 配方、任務書、本包腳本，而不是泛用 wiki 的猜測。**

*（遊戲內問答與配方卡截圖即將補上）*

重度整合包內容龐大：你知道想做出什麼，但不知道配方在哪、哪個任務解鎖、這個包裡到底有沒有這東西。Pack AI 用白話回答——輸入問題，拿到精簡答案，配方卡逐張跟在對應的編號方法後面，並附【來源】方便你查證。

不需要 Python bridge、不需要裝伺服器端。jar 放進 `mods`，按 **`]`** 直接問。

## 能回答什麼

**對齊這個包的配方。** 裝了 JEI 後，Pack AI 讀取真實的 R／U／催化資料，AI 自己排放配方卡——每張卡跟在對應的編號方法（工作台、自動合成、作為材料）後面；鏡像機台（如動力合成器／攪拌機）合併成一張卡並註明「亦可用」。

**開發重點：Forge 1.19.2** 是主要目標、優先更新；NeoForge 1.21.1 作為移植版同步維護。

**任務感知。** 引用相關的 FTB Quests／Heracles 條目，環境允許時可直接開啟任務。

**本包事實優先。** 答案來自 KubeJS／資料包／戰利品／交易——與網路搜尋衝突時以本包資料和 JEI 為準。

**附魔與修繕直接讀遊戲資料（0.2.0）。** 可用的附魔清單從遊戲 registry 讀取（不靠查看器），鐵砧修繕材料由實際 predicate 判定；維修與升級問題分開回答。

**Tooltip 取得線索（0.2.0）。** item tooltip 明寫取得方法時，以低信心提示呈現，不再硬答「未知」。

**手持 Tetra 工具。** 讀的是這把實例的零件／插槽／材料——空白模組配方不等於你手上這把的取得方式。

## 使用方法

1. 下載對應 loader 的 jar（NeoForge 1.21.1 或 Forge 1.19.2），不要混用。
2. 完全退出遊戲，jar 放進 `mods`，重新啟動。
3. 建議安裝 JEI（強烈推薦）。
4. 遊戲內按 **`]`** 提問（`/ai <問題>` 也可以）。懸停物品後按住 **Y**（約 1 秒）單獨詢問該物。
5. Mods → Pack AI → Connection：填入 API key 與 Base URL（只到 `/v1`），或使用 Ollama／離線模式。

按鍵可在 Options → Controls → Pack AI Assistant 重新綁定。

## 常見問題

- **需要 JEI 嗎？** 強烈建議——有 JEI 時配方對齊遊戲內資料；沒有 JEI 也能用任務、本包事實與指南書。
- **需要裝伺服器端嗎？** 不用——Pack AI 是純客戶端。
- **用哪個 LLM？** 你自己決定：任何 OpenAI 相容的 `/v1` 端點（OpenRouter、Groq、閘道…）、本機 Ollama，或離線模式（任務／本包事實／JEI 摘要，免 key）。
- **是作弊客戶端嗎？** 不是。它讀你的包、回答問題，不會幫你玩。僅輔助——請在遊戲內驗證。
- **免費嗎？** Mod 本身免費。雲端 LLM 用量由你的供應商收費；OpenRouter 的 `:free` 模型常會限流（HTTP 429）——重試或換模型。
- **可以放進我的整合包嗎？** 可以——純客戶端、無伺服器端、MIT 授權。

## 支援版本

| Minecraft | Loader   | 狀態 |
| --------- | -------- | ------ |
| 1.21.1    | NeoForge | 支援 |
| 1.19.2    | Forge    | 支援（同步） |

## 備註

- 語言：英文、繁體中文、簡體中文。
- 擴充性（0.2.0）：第三方 mod 可經公開 `api/` 套件與註冊事件登記自己的 Ask 工具；第三方工具的完整 schema／執行可見性仍在規劃中。
- 大型 JEI context 較耗 token——需要時可在設定調低 `maxJeiChars`／`historyTurns`。
- 原始碼：https://github.com/skps00/Modpack-AI-Assistant
