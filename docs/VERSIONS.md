# Pack AI — version matrix & build

Client-only mod. Each Minecraft line may use a **different Gradle root**.

Product `mod_version` bump / soft lockstep: [RELEASE.md § Product versioning](RELEASE.md#product-versioning-mod_version) (this file is the MC/loader matrix only).

## Matrix

| Minecraft | Loader | Path | Status | Jar pattern | JEI | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.x | `neoforge/1.21.1/` | **Supported** | `packai-<ver>.jar` | Optional 19.x | Full Pack AI |
| 1.19.2 | Forge 43.4.x | `forge/1.19.2/` | **Supported** | `packai-<ver>.jar` | Optional **11.8.1.1035** | UI parity with NeoForge 1.21.1 |

Status meanings: **Supported** = playable feature set · **Preview** = MinPlay · **Scaffolding** = loads / logs only.

### 1.19.2 Parity gaps

No known parity gaps remain in the supported Forge 1.19.2 line. (Anti-spoiler `showHiddenQuests` default false is parity with NeoForge.)

**LLM note:** `runClient` uses `forge/1.19.2/run/` config (separate from NeoForge). Set `PACKAI_API_KEY` in the environment (Gradle client run forwards it) or Mods → Pack AI, else ask falls back to raw JEI text + tip.

## Local drop folder

After build, jars are also copied to repo-root **`dist/`** (gitignored):

| File | Meaning |
| --- | --- |
| `dist/packai-1.21.1-neoforge.jar` | Full Pack AI (Supported) |
| `dist/packai-1.19.2-forge.jar` | Forge Parity (Supported) |

Release / store uploads should use **versioned** names (see [RELEASE.md](RELEASE.md) / [PUBLISH.md](PUBLISH.md)):

| Pattern | Example |
| --- | --- |
| `packai-<mod_version>+mc1.21.1-neoforge.jar` | `packai-0.1.0+mc1.21.1-neoforge.jar` |
| `packai-<mod_version>+mc1.19.2-forge.jar` | `packai-0.1.0+mc1.19.2-forge.jar` |

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

## Dual-toolchain

| | NeoForge 1.21.1 | Forge 1.19.2 |
| --- | --- | --- |
| Plugin | `net.neoforged.moddev` | `net.minecraftforge.gradle` 5.1.+ |
| Gradle | 9.2.1 (repo root) | 7.6.4 (`forge/1.19.2`) |
| JVM to run Gradle | 21 | **17** |
| Included in root `settings.gradle`? | Yes (`:neoforge-1.21.1`) | **No** (own settings) |
| JEI pin | see `neoforge/1.21.1` / props | `props/1.19.2.properties` → `11.8.1.1035` |

**Decision:** do not merge both into one `settings.gradle`. Root aggregate only builds NeoForge; Forge is a second root.

## Add-version SOP

1. Copy nearest tree in the same **era** (modern NeoForge / modern Forge).
2. Add `props/<mc>.properties` pins (MC, loader, JEI).
3. Fix loader metadata + Java/Gradle toolchain.
4. Port compile errors; **do not share JEI APIs across eras**.
5. Add a row here (Supported / Preview / Scaffolding) + gaps if any.
6. Require that version’s `build` green before calling it Supported.
7. Document jar pattern in RELEASE/PUBLISH.

## 去哪找碼

詳見 [`SOURCE_MAP.md`](SOURCE_MAP.md)。摘要：

| 線 | 路徑 |
| --- | --- |
| Forge | `forge/1.19.2/src/main/java/com/skps9/packai/` |
| Neo | `neoforge/1.21.1/src/main/java/com/skps9/packai/` |

關鍵套件：`client/knowledge`（PackKnowledge）、`client/service`、`client/jei`、`logic`。

瀏覽略過：`bridge/`（legacy）、`common/shared/`（空殼 — **禁止**未核准抽 shared）、`mezz/`、`dist/`、`**/build`、`**/run`、`.codegraph`。

## Not shipped

- `bridge/` — **LEGACY** reference only, not a player dependency (see `bridge/README.md`).
- `mod/` — obsolete; sources live under `neoforge/1.21.1/`.
- `common/shared/` — empty placeholder; **no** unapproved shared / Architectury extract.

## Epic far (no schedule)

Ancient lines (e.g. 1.6.4 / 1.12) = separate era trees later, not Stonecutter across history.
