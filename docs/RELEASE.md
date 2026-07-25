# GitHub Release checklist

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
