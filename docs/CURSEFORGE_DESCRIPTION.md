# CurseForge description — short version (2026-10-27)

Editor notes (do not paste): written after checking how JEI / Jade / Create / EMI / AE2 present themselves —
one bold pitch, one WIP line, screenshot, four short "what you get" lines, install steps, FAQ, versions.
Everything long-form lives in `docs/FEATURES.md` and the README. Keep this page scannable.

---

**Pack AI Assistant — ask your modpack in plain language.**

Every answer is grounded in *your* pack: JEI recipes, quest books and the pack's own data files — not a wiki guess. Client-only, no server mod, no Python.

**Work in progress.** Pack AI is being tested pack by pack (All the Mods 8, FTB Skies, Minehattan Project). Expect rough edges — treat an answer as a strong first pass and verify in JEI. Forge 1.19.2 only for now.

*Screenshot: in-game question with recipe cards under the numbered steps.*

**What you get**

- **Recipes that match your pack** — reads JEI's real R / U / catalyst data and prints recipe cards in chat, each one right under the step it belongs to.
- **Machine cards** — ask about a machine and you get up to two sample recipes from the tabs where that machine is the workstation (machine + inputs → output). Covers Create's Mechanical Press, Millstone, Saw and Crafter.
- **Where it comes from** — loot tables, structure chests, mob drops, trades and mining facts (dimension, biome, height, vein size), read from the pack's files.
- **Quest aware** — cites the FTB Quests / Heracles entry that unlocks it, and can open it when the environment allows.
- **Honest gaps** — when the pack index holds facts the reply skipped, a short `On record, not in the answer:` line lists them, and "I'm not sure" beats a made-up number.
- **Your model, your choice** — any OpenAI-compatible `/v1` endpoint, local Ollama, or offline mode (quests + local facts, no API key). English, 繁體中文, 简体中文.

**How to use**

1. Download the jar for **Minecraft 1.19.2 + Forge** — one jar per loader, never mixed.
2. Fully quit Minecraft, drop the jar in `mods`, start the game.
3. Install [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) — strongly recommended; without it there are no recipe or machine cards.
4. **Mods → Pack AI → Connection**: API key + base URL ending at `/v1`, or pick Ollama / offline.
5. Press **`]`** to ask (`/ai <question>` works too). Hover an item and hold **Y** for ~1s to ask about that item alone.

Keys are remappable under *Options → Controls → Pack AI Assistant*.

**FAQ**

- **Do I need JEI?** Strongly recommended. Without it you still get quests, local facts and guidebooks, but no recipe or machine cards.
- **Is it a cheat client?** No. It reads your pack and answers questions; it never plays for you.
- **Do I need a server mod?** No — client-only.
- **Which model?** Yours: any OpenAI-compatible `/v1` endpoint, local Ollama, or offline mode. Free OpenRouter `:free` models are often rate-limited (HTTP 429) — retry or switch model.
- **Can I include it in my modpack?** Yes — client-only and MIT licensed.

**Versions**

| Minecraft | Loader | Status |
|---|---|---|
| 1.19.2 | Forge | **Supported** — all new work lands here |
| 1.21.1 | NeoForge | Paused — last build still runs, no new features or fixes |

**Known limits:** fan-based Create categories (e.g. Encased Fan) have no machine card yet, and the Packing card does not name the Basin.

Issues and requests: [GitHub](https://github.com/skps00/Modpack-AI-Assistant/issues) · Full feature detail: [docs/FEATURES.md](https://github.com/skps00/Modpack-AI-Assistant/blob/main/docs/FEATURES.md)

---

## 繁體中文（簡短版）

**Pack AI Assistant —— 喺遊戲內問你個整合包。** 答案嚟自你自己個包：JEI 配方、任務書、本機資料檔，唔係上網亂猜。只裝客戶端，唔需要伺服器 mod、唔需要 Python。

**開發中**：目前只支援 Forge 1.19.2，已喺 All the Mods 8、FTB Skies、Minehattan Project 實測；答案請喺 JEI 核實。

- 裝 jar 落 `mods`（先完全關閉遊戲），建議同時裝 JEI
- 遊戲內按 **`]`** 發問（或 `/ai <問題>`）；指住物品長按 **Y** 約 1 秒只問該物品
- Mods → Pack AI → Connection 填 API key 同 Base URL（結尾到 `/v1`），或揀 Ollama／離線模式
- 機器卡：問機器會出最多兩張樣本配方（機器＋輸入 → 輸出）；Create 壓力機／磨石／鋸／合成器已支援
