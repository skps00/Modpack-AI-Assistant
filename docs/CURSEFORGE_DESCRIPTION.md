# CurseForge project description (Pack AI)

Paste into CurseForge project **About / Description** for [pack-ai-assistant-paia](https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia) (project id `1643097`).

- Prefer the **English** block on the main Description field if the page is English-first; paste **繁體中文** in the same field below a language heading, or use CF localization if available.
- CurseForge rich text usually accepts HTML. Markdown below is the source of truth; convert headings/lists if the editor strips MD.
- Jar upload steps: [PUBLISH.md](PUBLISH.md). Version policy: [RELEASE.md](RELEASE.md).

Do **not** claim features not listed in the README. Keep this file in sync when the player-facing feature set changes.

---

## English

### Short summary (optional CF summary field)

Client-only AI helper for modpacks: ask in plain language, see JEI recipe cards and related quests, works with cloud LLMs, local Ollama, or offline pack data.

### Full description

**Pack AI Assistant** is a **client-only** Minecraft mod that helps you understand *this* modpack — recipes, items, and quests — in plain language.

Drop the jar into `mods`. No separate Python bridge. Press **`]`** in-game to open the assistant (`/ai <question>` is a backup).

#### Features

- **Ask UI** — multi-turn chat, sidebar actions, regeneratable answers, source list at the end of replies
- **Item picking** — explicitly select up to 8 items from hotbar / inventory / armor / offhand (Curios slots appear when Curios is installed); no silent “whole hotbar” dump
- **JEI-aware answers** — uses JEI R/U / catalysts when JEI is present; recipe **cards** in the UI (large layouts such as Create mechanical crafting use flow-style slots)
- **Hold Y to ask** — in inventory or JEI, hover an item and hold **Y** (~1s Create Ponder–style progress) to ask about that item alone
- **Quests** — related FTB Quests / Heracles entries with open-quest actions when available
- **Pack-focused context** — loaded mods + local facts; optional pack-author `config/packai/AGENTS.md` injected into the LLM prompt
- **Web search (optional)** — Modrinth + Minecraft Wiki by default; optional Tavily / Serper keys; local JEI / quest data wins on conflicts
- **Languages** — `en_us`, `zh_tw`, `zh_cn` (Simplified Chinese needs `zh_cn` in the jar; missing file falls back to English, not Traditional Chinese)

#### Supported versions / loaders

Upload **one jar per line** — do not mix loaders.

| Minecraft | Loader   | Status    |
| --------- | -------- | --------- |
| 1.21.1    | NeoForge | Supported |
| 1.19.2    | Forge    | Supported (parity) |

Suggested file names: `packai-<ver>+mc1.21.1-neoforge.jar` / `packai-<ver>+mc1.19.2-forge.jar`.

#### Requirements

- **Required:** Minecraft + matching Forge / NeoForge for that jar
- **Optional (recommended):** [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — best recipe / usage parity with in-game R/U
- **Optional soft deps:** Curios (extra equipment slots in item picker), EMI / Patchouli / GuideME listed where relevant — primary recipe UX is JEI
- **Not required:** any Python bridge or server-side install (client-only; servers need not require this mod)

#### How to use

1. Install the jar for your MC + loader into `mods` (fully quit the game before replacing jars).
2. Strongly recommended: install JEI.
3. Mods → **Pack AI** settings (Connection / Ask / Recipes / Quests tabs).
4. Connect an LLM **or** use offline helpers:
   - **Cloud:** any OpenAI-compatible `POST …/v1/chat/completions` (Base URL through `/v1` only) + API key + model
   - **Local:** [Ollama](https://ollama.com) (`mode=ollama` or `auto` without a cloud key)
   - **Offline:** `mode=offline` — quest guidance, local plain-language recipe help, and JEI summaries still work without calling an LLM
5. In-game: press **`]`**. In JEI/inventory: hold **Y** on a hovered item to ask about it.
6. Keys are remappable under Options → Controls → Pack AI Assistant.

#### Disclaimer (API keys & costs)

- Cloud LLM use requires **your own** API key. Pack AI does not ship free cloud quota.
- You pay whatever your provider charges (or use free-tier / local Ollama at your own risk and limits).
- Large JEI context increases token usage; lower `maxJeiChars` / `historyTurns` in settings if you hit rate limits or cost.
- Never commit API keys; prefer the in-game settings page or env `PACKAI_API_KEY`.
- Answers are assistive only — verify recipes and progression in-game. Not a cheat client; it does not play the game for you.

#### Links

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia
- Source: https://github.com/skps00/Modpack-AI-Assistant
- Player docs: repository `README.md` · version matrix: `docs/VERSIONS.md` · pack authors: `docs/PACK_AUTHOR.md`

---

## 繁體中文（台灣）

### 短摘要（選用 CF summary）

客戶端整合包 AI 助手：用白話提問，顯示 JEI 配方卡與相關任務；支援雲端 LLM、本機 Ollama，或離線整合包資料。

### 完整說明

**Pack AI Assistant（整合包 AI 助手）** 是一款**僅客戶端**模組，幫你用白話搞懂「這個整合包」的配方、物品與任務。

把 jar 丟進 `mods` 即可，**不必**另外跑 Python Bridge。遊戲中按 **`]`** 開啟助手（`/ai <問題>` 為備援）。

#### 功能

- **Ask 問答介面** — 多輪對話、側欄操作、可重新生成、回答結尾標示【來源】
- **選物品** — 明確勾選熱鍵欄／背包／盔甲／副手最多 8 項（有安裝 **Curios** 時也會列出飾品格）；不會默默附帶整排熱鍵欄
- **JEI 感知** — 有 JEI 時對齊 R／U／催化劑；介面顯示**配方卡**（大型配方如 Create 動力合成用流程卡列槽位）
- **按住 Y 提問** — 在背包或 JEI 懸停物品後按住 **Y**（約 1 秒、類似 Create Ponder 進度條），單獨詢問該物品
- **任務書** — 支援 FTB Quests／Heracles 相關任務，可開啟任務（若環境允許）
- **整合包脈絡** — 依已載入模組與本地事實回答；作者可放 `config/packai/AGENTS.md` 注入 LLM 提示
- **網搜（可選）** — 預設查 Modrinth＋Minecraft Wiki；可選填 Tavily／Serper；與本地 JEI／任務衝突時以本地為準
- **語系** — `en_us`／`zh_tw`／`zh_cn`（簡中需 jar 含 `zh_cn`；缺檔會回落英文，**不會**自動改用繁中）

#### 支援版本／載入器

請依版本上傳**對應** jar，勿混用載入器。

| Minecraft | 載入器   | 狀態           |
| --------- | -------- | -------------- |
| 1.21.1    | NeoForge | 支援           |
| 1.19.2    | Forge    | 支援（功能對齊） |

建議檔名：`packai-<ver>+mc1.21.1-neoforge.jar`／`packai-<ver>+mc1.19.2-forge.jar`。

#### 需求

- **必要：** 對應版本的 Minecraft＋Forge／NeoForge
- **可選（強烈建議）：** [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — 配方／用途與遊戲內 R／U 最一致
- **可選軟依賴：** Curios（物品挑選可含飾品格）、EMI／Patchouli／GuideME 等視版本列出 — 主要配方體驗以 JEI 為主
- **不需要：** Python Bridge；亦非伺服器必要模組（僅客戶端）

#### 怎麼用

1. 依你的 MC＋載入器安裝對應 jar 到 `mods`（**換 jar 前請完全關閉遊戲**）。
2. 強烈建議安裝 JEI。
3. 模組清單 → **Pack AI** 設定（連線／Ask／配方／任務四頁籤）。
4. 接 LLM，或先用離線功能：
   - **雲端：** 任何 OpenAI 相容端點（Base URL **只要到 `/v1`**）＋API key＋模型
   - **本機：** [Ollama](https://ollama.com)（`mode=ollama`，或 `auto` 且未設雲端 key）
   - **離線：** `mode=offline` — 不呼叫 LLM，任務書導引、本地配方白話與 JEI 摘要仍可用
5. 遊戲中按 **`]`**。在 JEI／背包：懸停物品後按住 **Y** 提問。
6. 按鍵可在 **設定 → 控制 → 整合包 AI 助手** 更改。

#### 注意（API 金鑰與費用）

- 雲端 LLM 需使用**你自己的** API key；本模組不附贈雲端額度。
- 費用依各供應商計費（或自行使用免費層／本機 Ollama，額度與風險自負）。
- JEI 上下文越大越耗 token；若撞到速率限制或成本，請在設定調低 `maxJeiChars`／`historyTurns`。
- 請勿把金鑰提交到 git；優先用設定頁或環境變數 `PACKAI_API_KEY`。
- 回答僅供輔助，請以遊戲內配方與進度為準。這不是外掛代打，不會替你操作遊戲。

#### 連結

- CurseForge：https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia
- 原始碼：https://github.com/skps00/Modpack-AI-Assistant
- 玩家說明：倉庫 `README.md` · 版本矩陣：`docs/VERSIONS.md` · 整合包作者：`docs/PACK_AUTHOR.md`
