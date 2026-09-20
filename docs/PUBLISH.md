# Publishing Pack AI (CurseForge / Modrinth)

Bump / lockstep `mod_version` before any public upload — see [RELEASE.md § Product versioning](RELEASE.md#product-versioning-mod_version). Never re-upload the same version/filename.

Pack AI ships **per Minecraft line** (see [VERSIONS.md](VERSIONS.md)). Do not upload one jar for both loaders.

## Jar naming

| Line | Loader | Build output | Suggested upload name |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge | `neoforge/1.21.1/build/libs/packai-<ver>.jar` | `packai-<ver>+mc1.21.1-neoforge.jar` |
| 1.19.2 | Forge | `forge/1.19.2/build/libs/packai-<ver>.jar` | `packai-<ver>+mc1.19.2-forge.jar` |

Optional local drop folder: `dist/packai-1.21.1-neoforge.jar`, `dist/packai-1.19.2-forge.jar`.

## Prerequisites

1. Create a project page on [CurseForge](https://www.curseforge.com/) and/or [Modrinth](https://modrinth.com/).
2. Create API tokens (never commit tokens; use env vars or CI secrets).
3. Build the jar for the line you are publishing (VERSIONS.md).

## Project description (About)

Store-ready **English** + **繁體中文（台灣）** copy lives in [CURSEFORGE_DESCRIPTION.md](CURSEFORGE_DESCRIPTION.md).

- Paste into CurseForge project **About / Description** for [pack-ai-assistant-paia](https://www.curseforge.com/minecraft/mc-mods/pack-ai-assistant-paia) (id `1643097`).
- Keep that file accurate when player-facing features change; do not invent store claims.
- No automated description upload in this repo yet — manual paste (or a future token-backed API step).

## Automated upload: blocked by Cloudflare (2026-09-20)

Tried the **official Upload API** (`POST /api/projects/1643097/upload-file`, `X-Api-Token` from env)
from this machine: every CF API host answers **HTTP 403 "Just a moment…"** (Cloudflare challenge) —
`minecraft.curseforge.com`, `www.curseforge.com`, `authors.curseforge.com` (both browser and custom
User-Agent), and `api.curseforge.com` has no upload endpoint (404 / Eterna key scheme).

⇒ **Do not retry blindly**; no bypass tricks (third-party captcha solvers / fingerprint spoofing) are used here.
Manual web upload remains the path; the helper stays in `tools/cf_upload.py` (`--dry` prints the exact
metadata it would send) for a network/CI where CF is reachable.

### Metadata to paste on upload (0.2.3, Forge 1.19.2)

- Display name: `packai-0.2.3+mc1.19.2-forge`
- Game version: `1.19.2` · Loader: `Forge` · Release type: `Release`
- Changelog: `dist/_cf_desc/packai-0.2.3_changelog.txt`
- Project description (About): `dist/_cf_desc/packai_cf_description_paste.txt`

## Manual upload (simplest)

1. Build the target line (NeoForge root or `forge/1.19.2` + JDK 17).
2. Rename to the suggested upload name above.
3. On the website, set **game version** + **loader** to match that jar.
4. JEI is **optional** (soft dependency) — do not mark JEI required unless you want to.
5. Fill changelog from `code_change_log.md` / git log.
6. If the project About text is stale, refresh from [CURSEFORGE_DESCRIPTION.md](CURSEFORGE_DESCRIPTION.md).

## Optional: Gradle plugins later

If you add publish plugins (e.g. Modrinth Minotaur, CurseGradle), wire:

- `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` as env secrets
- Project ids in the **version tree’s** `build.gradle` (not a deleted `mod/` path)
- Version from that tree’s `gradle.properties`

Do **not** put tokens in the repo.

## Compatibility note

Indexer corpora may come from older packs (e.g. 1.19.2 scripts). That does **not** mean every corpus pack is a supported runtime target — only rows marked **Supported** / **Preview** in VERSIONS.md.
