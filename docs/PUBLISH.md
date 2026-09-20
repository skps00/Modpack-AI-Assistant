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

## Automated upload: WORKS via author token (verified 2026-09-20)

`dist/_cf_upload/upload_028.py` (same shape as `upload_027.py`, which shipped 0.2.1) POSTs the jar to
`https://minecraft.curseforge.com/api/projects/1643097/upload-file` with `X-Api-Token: $CURSEFORGE_AUTHOR_TOKEN`
and a multipart `metadata` part → **HTTP 200, file id 8926920** for `packai-0.2.3+mc1.19.2-forge.jar`
(release, `gameVersions` = 1.19.2 + Forge). The new file then goes through CurseForge review before it
appears in the public file list — the API returning 200 means *accepted*, not *published*.

**Pitfall that cost one round (2026-09-20):** `GET /api/game/versions` **is** behind Cloudflare (403
"Just a moment…", browser UA included). That 403 says nothing about uploads — the upload POST works fine.
Do **not** probe version ids at runtime; pin them:

| Line | gameVersions |
| ---- | ------------ |
| 1.19.2 Forge | `9366, 7498, 9638` |
| 1.21.1 NeoForge | `11779, 10150, 9638` |

### Project description (About) — also automated

`dist/_cf_desc/update_description.py` warms CF cookies in an **off-screen** Chrome
(`--user-data-dir=dist/_cf_upload/chrome_profile --window-position=-32000,-32000`, `CREATE_NO_WINDOW`),
reads cookies over CDP and `PUT`s `description.html` — built by `build_html.py` from
`docs/CURSEFORGE_DESCRIPTION.md` (never hand-edit the HTML).

Verify a live edit with a **real browser** (`document.body.innerText`): the markdown/Exa extractor silently
dropped one `<p>` that was in fact live, which nearly caused a false "the paragraph didn't save" report.

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
