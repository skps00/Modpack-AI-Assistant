# Publishing Pack AI (CurseForge / Modrinth)

Pack AI is a **NeoForge 1.21.1 client** mod. Jar: `mod/build/libs/packai-0.1.0.jar` (`gradlew jar`).

## Prerequisites

1. Create a project page on [CurseForge](https://www.curseforge.com/) and/or [Modrinth](https://modrinth.com/).
2. Create API tokens (never commit tokens; use env vars or CI secrets).
3. Build: `cd mod && ./gradlew jar`

## Manual upload (simplest)

1. Run `gradlew jar`.
2. Upload `mod/build/libs/packai-*.jar` on the website.
3. Fill changelog from `code_change_log.md` / git log.

## Optional: Gradle plugins later

If you add publish plugins (e.g. Modrinth Minotaur, CurseGradle), wire:

- `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` as env secrets
- `modrinth.projectId` / CurseForge project id in `mod/build.gradle`
- Version from `gradle.properties`

Do **not** put tokens in the repo.

## Version note

Heavy packs like No Flesh Within Chest (1.19.2 / 1.20.1) are used as **script corpora** for the indexer. The mod jar itself targets **1.21.1 NeoForge** only.
