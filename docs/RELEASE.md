# GitHub Release checklist

## Product versioning (`mod_version`)

Soft lockstep Forge 1.19.2 + NeoForge 1.21.1:

1. **Same feature set together** → same product `mod_version` on both trees. Single-line hotfix may bump only that tree (note in changelog); next joint release realigns.
2. **Bump on every public upload** (CurseForge / Modrinth / GitHub Release) — at least PATCH. Local Prism / `runClient` / `dist` overwrite smoke tests — no bump. Do **not** bump every commit.
3. **Sync** `mod_version` in `neoforge/1.21.1/gradle.properties`, `forge/1.19.2/gradle.properties`, and root `gradle.properties` (same number when lockstep).
4. **Never re-upload** the same version/filename to CurseForge or Modrinth. Wrong file → new patch + changelog.
5. Semver pre-1.0: `0.1.x` = fix/small; `0.2.0` = bigger feature/behavior change; `1.0.0` later.
6. Store jar names: `packai-<ver>+mc1.19.2-forge.jar` / `packai-<ver>+mc1.21.1-neoforge.jar` (see below / [PUBLISH.md](PUBLISH.md)).

### Checklist steps

1. Ensure `main` is pushed and CI (if any) is green.
2. Tag: `git tag -a v0.1.0 -m "packai 0.1.0"` then `git push origin v0.1.0`
3. Build jars (see [VERSIONS.md](VERSIONS.md)):

```powershell
# NeoForge 1.21.1 (repo root, JDK 21)
.\gradlew.bat :neoforge-1.21.1:jar

# Forge 1.19.2 (JDK 17)
cd forge\1.19.2
.\build-jdk17.bat jar
```

4. Rename / copy for the release asset names:

| Built | Release asset name |
| --- | --- |
| `neoforge/1.21.1/build/libs/packai-0.1.0.jar` | `packai-0.1.0+mc1.21.1-neoforge.jar` |
| `forge/1.19.2/build/libs/packai-0.1.0.jar` | `packai-0.1.0+mc1.19.2-forge.jar` |

Local smoke copies may also live under `dist/packai-1.21.1-neoforge.jar` / `dist/packai-1.19.2-forge.jar` (gitignored).

5. Create release:

```bash
gh release create v0.1.0 \
  packai-0.1.0+mc1.21.1-neoforge.jar \
  packai-0.1.0+mc1.19.2-forge.jar \
  --title "packai 0.1.0" \
  --notes-file CHANGELOG.md
```

Or paste notes from recent `code_change_log.md` / `git log --oneline`.

6. Optionally mirror jars to CurseForge/Modrinth (see [PUBLISH.md](PUBLISH.md)) — upload **per Minecraft version** with the matching loader.
