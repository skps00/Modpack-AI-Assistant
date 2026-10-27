<h1 align="center">Pack AI Assistant</h1>

<p align="center"><b>Ask your modpack in plain language.</b><br>
Answers come from this pack's JEI data, quest books and local files — not a wiki guess.</p>

<p align="center">
<a href="https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia"><img src="https://cf.way2muchnoise.eu/full_1643097_downloads.svg" alt="CurseForge"></a>
<img src="https://img.shields.io/badge/Minecraft-1.19.2%20%C2%B7%20Forge-blue" alt="Minecraft 1.19.2 Forge">
<img src="https://img.shields.io/badge/status-work%20in%20progress-orange" alt="Work in progress">
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="MIT"></a>
</p>

> **Work in progress.** Tested pack by pack (All the Mods 8, FTB Skies, Minehattan Project). Expect rough edges — treat an answer as a strong first pass and verify in JEI. Forge 1.19.2 only.

<!-- [SCREENSHOT: in-game question, recipe cards under the numbered steps] -->

## What it does

- **Recipes that match your pack** — reads JEI's real R / U / catalyst data and prints recipe cards in chat, each one right under the step it belongs to.
- **Machine cards** — ask about a machine and you get up to two sample recipes from the tabs where that machine is the workstation (machine + inputs → output). Covers Create's Press, Millstone, Saw and Crafter.
- **Where it comes from** — loot tables, structure chests, mob drops, trades and mining facts (dimension, biome, height, vein size) read from the pack's own files.
- **Quest aware** — cites the FTB Quests / Heracles entry that unlocks it.
- **Says what it doesn't know** — a short `On record, not in the answer:` line lists pack facts the reply skipped, and "I'm not sure" beats a made-up number.
- **Any model you like** — any OpenAI-compatible `/v1` endpoint, local Ollama, or offline mode (quests + local facts, no API key). English, 繁體中文, 简体中文.

## Install

1. Download the jar for **Minecraft 1.19.2 + Forge** and drop it in `mods` (fully quit the game first).
2. Install [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — strongly recommended; without it you lose recipe and machine cards.
3. In game: **Mods → Pack AI → Connection** — API key + base URL (ending at `/v1`), or pick Ollama / offline.
4. Press **`]`** to ask (`/ai <question>` also works). Hover an item and hold **Y** for ~1s to ask about that item alone.

Keys can be remapped under *Options → Controls → Pack AI Assistant*.

## Requirements

| | |
|---|---|
| Minecraft | 1.19.2 |
| Loader | Forge (43.x) |
| Required | none — client-only, no server mod, no Python |
| Recommended | JEI |
| Optional | Curios slots, Patchouli / GuideME / EMI present in the pack are read when available |

## Status

| Minecraft | Loader | Status |
|---|---|---|
| 1.19.2 | Forge | **Supported** — gets all new work |
| 1.21.1 | NeoForge | Paused — last build still runs, no new features or fixes |

Known limits: fan-based Create categories (e.g. Encased Fan) have no machine card yet; the Packing card does not name the Basin.

## Docs

- [Feature detail](docs/FEATURES.md) — everything the mod reads and why
- [Publishing notes](docs/PUBLISH.md) · [Version policy](docs/VERSIONS.md) · [Release process](docs/RELEASE.md)
- Issues and requests: [GitHub Issues](https://github.com/skps00/Modpack-AI-Assistant/issues)

## 繁體中文（玩家）

**Pack AI Assistant —— 喺遊戲內問你個整合包。** 答案嚟自**你自己個包**：JEI 配方、任務書、本機檔案，唔係上網亂猜。

- 裝好 jar 落 `mods`（要先完全關閉遊戲），建議同時裝 JEI
- 遊戲內按 **`]`** 發問（或 `/ai <問題>`）；指住物品長按 **Y** 約 1 秒，就會只問嗰件物品
- Mods → Pack AI → Connection 填 API key＋Base URL（結尾到 `/v1`），或者用 Ollama／離線模式
- **開發中**：目前只支援 Forge 1.19.2，答案請喺 JEI 核實

## License

MIT — client-only, safe to ship inside a modpack.
