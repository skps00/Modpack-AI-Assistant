# GitHub Release checklist

1. Ensure `main` is pushed and CI (if any) is green.
2. Tag: `git tag -a v0.1.0 -m "packai 0.1.0"` then `git push origin v0.1.0`
3. Build jar(s): see [VERSIONS.md](VERSIONS.md). NeoForge: `./gradlew :neoforge-1.21.1:jar` from repo root.
4. Create release:

```bash
gh release create v0.1.0 neoforge/1.21.1/build/libs/packai-0.1.0.jar --title "packai 0.1.0" --notes-file CHANGELOG.md
```


Or paste notes from recent `code_change_log.md` / `git log --oneline`.

5. Optionally mirror the same jar to CurseForge/Modrinth (see [PUBLISH.md](PUBLISH.md)).
