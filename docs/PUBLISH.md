# Publishing Pack AI (CurseForge / Modrinth)

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

## Manual upload (simplest)

1. Build the target line (NeoForge root or `forge/1.19.2` + JDK 17).
2. Rename to the suggested upload name above.
3. On the website, set **game version** + **loader** to match that jar.
4. JEI is **optional** (soft dependency) — do not mark JEI required unless you want to.
5. Fill changelog from `code_change_log.md` / git log.

## Optional: Gradle plugins later

If you add publish plugins (e.g. Modrinth Minotaur, CurseGradle), wire:

- `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` as env secrets
- Project ids in the **version tree’s** `build.gradle` (not a deleted `mod/` path)
- Version from that tree’s `gradle.properties`

Do **not** put tokens in the repo.

## Compatibility note

Indexer corpora may come from older packs (e.g. 1.19.2 scripts). That does **not** mean every corpus pack is a supported runtime target — only rows marked **Supported** / **Preview** in VERSIONS.md.
