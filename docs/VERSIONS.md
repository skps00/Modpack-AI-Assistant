# Pack AI — version matrix & build

Client-only mod. Each Minecraft line may use a **different Gradle root**.

## Matrix

| Minecraft | Loader | Path | Status | Jar | JEI | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.x | `neoforge/1.21.1/` | **Supported** | `packai-0.1.0.jar` (from that project) | Optional 19.x | Full Pack AI |
| 1.19.2 | Forge 43.4.x | `forge/1.19.2/` | **Scaffolding** | `packai-0.1.0-skeleton.jar` | — | Hello `@Mod` only (Skeleton) |

Status meanings: **Supported** = playable feature set · **Preview** = MinPlay · **Scaffolding** = loads / logs only.

## Local drop folder

After build, jars are also copied to repo-root **`dist/`** (gitignored):

| File | Meaning |
| --- | --- |
| `dist/packai-1.21.1-neoforge.jar` | Full Pack AI (Supported) |
| `dist/packai-1.19.2-forge.jar` | Skeleton hello only (Scaffolding) |

## Build commands

### NeoForge 1.21.1 (repo root, Gradle 9 + Java 21)

```powershell
cd <repo-root>
.\gradlew.bat :neoforge-1.21.1:build
# jar: neoforge/1.21.1/build/libs/packai-*.jar
```

### Forge 1.19.2 (separate wrapper, Gradle 7.6.4 + **JDK 17**)

ForgeGradle 5 cannot run on Gradle 8+ or as a subproject of the NeoForge ModDev root.

```powershell
cd forge\1.19.2
# Must use JDK 17 (not 21) for the Gradle daemon:
$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
# jar: forge/1.19.2/build/libs/packai-*.jar
```

Or: `.\build-jdk17.bat`

## Dual-toolchain spike (Skeleton)

| | NeoForge 1.21.1 | Forge 1.19.2 |
| --- | --- | --- |
| Plugin | `net.neoforged.moddev` | `net.minecraftforge.gradle` 5.1.+ |
| Gradle | 9.2.1 (repo root) | 7.6.4 (`forge/1.19.2`) |
| JVM to run Gradle | 21 | **17** |
| Included in root `settings.gradle`? | Yes (`:neoforge-1.21.1`) | **No** (own settings) |

**Decision:** do not merge both into one `settings.gradle`. Root aggregate only builds NeoForge; Forge is a second root.

## Add-version SOP

1. Copy nearest tree in the same era (modern NeoForge / modern Forge).
2. Add `props/<mc>.properties` pins.
3. Fix loader metadata + Java/Gradle toolchain.
4. Port compile errors; do not share JEI APIs across eras.
5. Add a row here (Supported / Preview / Scaffolding).
6. Require that version’s `build` green before calling it supported.

## Not shipped

- `bridge/` — legacy reference only, not a player dependency.
- `mod/` — if present, obsolete copy; sources live under `neoforge/1.21.1/`. Delete when unlocked.

## Epic far (no schedule)

Ancient lines (e.g. 1.6.4 / 1.12) = separate era trees later, not Stonecutter across history. See office-hours design doc if needed.
