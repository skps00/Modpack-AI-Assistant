# Pack AI Assistant

**Client-only** helper for **heavy modpacks**: ask in plain language and get answers grounded in **JEI**, **quest books**, and **this pack’s scripts/datapacks** — not a generic wiki guess.

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia) · [Source](https://github.com/skps00/Modpack-AI-Assistant)

Drop the jar into `mods`. No Python bridge. Press **`]`** in-game (`/ai <question>` as backup).

## Features

- **JEI-accurate recipes** when JEI is installed (R / U / catalysts) with **recipe cards** in chat
- **Quest-aware** help for FTB Quests / Heracles (open related quests when possible)
- **Pack-local facts** from KubeJS / datapacks / loot / trades (local + JEI win over web search on conflicts)
- **Multi-turn chat**, pick up to **8** items, **【Sources】** on replies
- **Hold Y** on a hovered JEI / inventory item (~1s) to ask about that item alone
- **Cloud LLM**, **Ollama**, or **offline** (quests / local facts / JEI summaries without an API key)
- Optional web search (Modrinth + Minecraft Wiki; optional Tavily / Serper)
- Languages: English, Traditional Chinese, Simplified Chinese

## Supported

| Minecraft | Loader   | Status             |
| --------- | -------- | ------------------ |
| 1.21.1    | NeoForge | Supported          |
| 1.19.2    | Forge    | Supported (parity) |

Upload **one jar per line** — do not mix loaders.  
Suggested names: `packai-<ver>+mc1.21.1-neoforge.jar` / `packai-<ver>+mc1.19.2-forge.jar`

## Quick start

1. Install the jar for your Minecraft + loader into `mods` (**fully quit** before replacing jars).
2. Install **[JEI](https://www.curseforge.com/minecraft/mc-mods/jei)** if you can (strongly recommended).
3. Mods → **Pack AI** → Connection: API key + Base URL **through `/v1` only** (or Ollama / `offline`).
4. Press **`]`**. In JEI/inventory, hold **Y** on a hovered item.
5. Remap keys under Options → Controls → Pack AI Assistant.

### LLM notes

- OpenAI-compatible cloud: `POST {apiBaseUrl}/chat/completions`
- Free tiers (OpenRouter `:free`, etc.) often hit **shared rate limits (HTTP 429)** — retry, switch model, or use Ollama
- Large JEI context costs tokens — lower `maxJeiChars` / `historyTurns` in settings if needed
- Prefer in-game settings or env `PACKAI_API_KEY`. Never commit keys.

More setup (Groq / OpenRouter / gateways): see below under **LLM providers**.

## Requirements

- Matching Forge / NeoForge for the jar you downloaded
- **Optional (recommended):** JEI
- **Optional soft deps:** Curios (extra picker slots), EMI / Patchouli / GuideME as available — primary recipe UX is JEI
- **Not required:** Python bridge; server install (client-only)

## Pack authors

Optional `config/packai/AGENTS.md` injects pack-specific guidance into the LLM prompt.  
See [`docs/PACK_AUTHOR.md`](docs/PACK_AUTHOR.md) · example [`docs/examples/packai_AGENTS.md`](docs/examples/packai_AGENTS.md).

## Disclaimer

Assistive only — verify in-game. Not a cheat client; it does not play for you. Cloud usage is billed by **your** provider.

---

## 繁體中文（玩家）

**僅客戶端**整合包助手：用白話提問，答案對齊 **JEI**、**任務書**與**本包腳本／資料**，而不是泛用 wiki。

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia) · jar 丟進 `mods` 即可。按 **`]`** 開助手；JEI／背包懸停後按住 **Y** 單獨詢問該物。

支援 **NeoForge 1.21.1**、**Forge 1.19.2**。強烈建議裝 JEI。雲端需自備 API key；也可用 Ollama 或 `offline`。`:free` 模型常會 429，屬供應商共用限流。

商店說明原稿：[`docs/CURSEFORGE_DESCRIPTION.md`](docs/CURSEFORGE_DESCRIPTION.md)。

---

## LLM providers (OpenAI-compatible)

Base URL must end at `/v1` (do **not** append `/chat/completions`).

| Provider | Notes |
| -------- | ----- |
| [Ollama](https://ollama.com) | Local / free; `mode=ollama` |
| [OpenRouter](https://openrouter.ai/keys) | Many models; `:free` often rate-limited |
| [Groq](https://console.groq.com/keys) | Fast free tier (limits apply) |
| Others | Any OpenAI-compatible `/v1` endpoint |

Settings → **Connection** → paste key, base URL, model → **Refresh** model list.

---

## For developers

| Line | Path | Build |
| ---- | ---- | ----- |
| NeoForge 1.21.1 | `neoforge/1.21.1/` | Repo root: `.\gradlew.bat :neoforge-1.21.1:build` |
| Forge 1.19.2 | `forge/1.19.2/` | `.\build-jdk17.bat build` (JDK 17) |

- Version matrix: [`docs/VERSIONS.md`](docs/VERSIONS.md)
- Source map: [`docs/SOURCE_MAP.md`](docs/SOURCE_MAP.md)
- Publish / jars: [`docs/PUBLISH.md`](docs/PUBLISH.md)
- CurseForge copy: [`docs/CURSEFORGE_DESCRIPTION.md`](docs/CURSEFORGE_DESCRIPTION.md)
- Skip browsing: `bridge/` (legacy), `dist/`, `**/build`, `**/run`, `.codegraph`

Same package layout on both lines: `client/knowledge` (**PackKnowledge**), `client/service`, `client/jei`, `logic`.

Lang strings: `assets/packai/lang/{en_us,zh_tw,zh_cn}.json` (`packai.reply.*`).

### Config sketch (`config/packai-client.toml`)

Most options are in Mods → **Pack AI** (Connection / Ask / Recipes / Quests).

| Section | Highlights |
| ------- | ---------- |
| `[llm]` | `mode`, `apiKey`, `apiBaseUrl` (to `/v1`), `model`, Ollama fields |
| `[token]` | `maxJeiChars` (default 12000), `historyTurns`, `maxFacts` — main cost knobs |
| `[web]` | `allowWebSearch`, optional Tavily / Serper keys |
| `[ui]` | sidebar, `preferObtain`, recipe category prefs, quest toggles, `scanModJars` (default off) |

Typical Ask input is often ~4k–8k tokens; max JEI + multi-select can go much higher — lower caps to save cost.

`bridge/` is **legacy** reference only — players do not need it.
