# CurseForge project description (Pack AI)

Paste into CurseForge project **About / Description** for [pack-ai-assistant-paia](https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia) (project id `1643097`).

**How to paste:** CurseForge editor → **Markdown** mode → paste the **English** block first. Optionally add the **繁體中文** block under a language heading in the same field.

Aligned with [CurseForge project page tips](https://blog.curseforge.com/how-to-create-the-best-project-page/): short summary, clear what it does, requirements, how-to, English first, no walls of developer jargon, no competitor-brand comparisons.

Keep in sync with the player-facing parts of `README.md`. Jar upload: [PUBLISH.md](PUBLISH.md).

---

## English

### Short summary (CF summary field, ≤256 chars)

Client-only modpack AI: JEI-accurate recipes, quest context, and local pack facts — answers for *this* pack, not a generic wiki. Cloud, Ollama, or offline.

### Full description (paste below)

**Pack AI Assistant** is a **client-only** helper for **heavy modpacks**. Ask in plain language; answers use **JEI**, **quest books**, and **local pack data** (KubeJS / datapacks) so they match *this* pack — not a generic wiki guess.

Install the jar in `mods`. No Python bridge. Press **`]`** in-game (`/ai <question>` as backup).

### Features

- **JEI-level recipes** — with JEI installed, answers align with in-game **R / U / catalysts**, plus **recipe cards** in chat. Cards stay **after** the how-to-use section instead of splitting it
- **Pack guidebooks** — Patchouli (and similar) guide pages are cited in Ask. Crafting-page recipe results are indexed, so items that only appear as a recipe output still find their guide entry. If the local index misses, Ask uses the same lookup as Ctrl-hover (no book in inventory required)
- **Quest-aware** — related **FTB Quests / Heracles**; open a quest when the environment allows
- **Pack-local truth** — loot, trades, and script facts from the pack; local + JEI win if web search disagrees
- **Held Tetra tools** — Ask reads **this instance's** parts, sockets, and materials. Part cards sit in how-to-get (not extra selected items; not while thinking). A blank `tetra:modular_sword` recipe is not how you obtained the tool in your hand. Assistive only — answers may be inaccurate
- **Multi-turn chat** — pick up to **8** items; **【Sources】** on replies
- **Hold Y** — hover a JEI/inventory item and hold **Y** (~1s) to ask about that item alone
- **Cloud, Ollama, or offline** — offline still helps with quests, local facts, and JEI summaries
- Optional web search (Modrinth + Minecraft Wiki; optional Tavily / Serper)
- Languages: English, Traditional Chinese, Simplified Chinese

### Supported versions

| Minecraft | Loader   | Status             |
| --------- | -------- | ------------------ |
| 1.21.1    | NeoForge | Supported          |
| 1.19.2    | Forge    | Supported (parity) |

Download the **matching** jar. Do not mix loaders.

### Requirements

- Minecraft + matching Forge / NeoForge
- **Strongly recommended:** [JEI](https://www.curseforge.com/minecraft/mc-mods/jei)
- Optional: Curios (extra item-picker slots)
- **Not required:** Python bridge or a server-side install

### How to use

1. Put the correct jar in `mods` (fully quit the game before replacing jars).
2. Install JEI if you can.
3. Mods → **Pack AI** → set API key + Base URL through `/v1` only, or use Ollama / offline mode.
4. Press **`]`**. In JEI or inventory, hold **Y** on a hovered item.
5. Remap keys under Options → Controls → Pack AI Assistant.

### Notes

- You provide your own LLM API key (or use Ollama). Pack AI does not include free cloud quota.
- Free OpenRouter models (`:free`) often return **HTTP 429** from shared upstream pools — retry or switch model.
- Large JEI context uses more tokens — lower `maxJeiChars` / `historyTurns` in settings if needed.
- Assistive only. Verify in-game. Not a cheat client.

### Links

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia
- GitHub: https://github.com/skps00/Modpack-AI-Assistant

---

## 繁體中文（台灣）

### 短摘要

客戶端整合包 AI：對齊 JEI 配方、任務書與本包資料，回答貼近「這個包」而非泛用 wiki。支援雲端、Ollama 或離線。

### 完整說明

**Pack AI Assistant** 是給重度整合包用的**僅客戶端**助手：用白話提問，答案建立在 **JEI**、**任務書**與**本包腳本／資料**上。

jar 放進 `mods` 即可。按 **`]`** 開助手；JEI／背包懸停後按住 **Y** 單獨詢問該物。

配方卡排在「怎麼用」之後，不拆開說明。Ask 會引用 Patchouli 等指南頁（含合成結果物品）；本地索引錯過時走與 Ctrl 懸停相同的查詢，不必背包裡有書。手持 Tetra 模組工具時讀這把實例的零件／插槽／材料；零件卡在「怎麼來」，不佔已選、思考中不畫。空白模組劍配方不當這把的取得。答覆不一定準確。

**支援：** NeoForge 1.21.1、Forge 1.19.2（請下載對應 jar）。**強烈建議**安裝 JEI。雲端需自備 API key；也可用 Ollama 或離線模式。`:free` 模型常會限流（429），屬正常現象。

原始碼：https://github.com/skps00/Modpack-AI-Assistant
