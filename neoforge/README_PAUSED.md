# NeoForge (1.21.1) support — PAUSED

**Status: paused on 2026-09-14.** Owner decision (SK), scope "B" of
`docs/plans/2026-09-14-neoforge-support-pause.md`.

## What this means

- **Forge 1.19.2 stays the actively supported build** and keeps getting new features and fixes.
- **NeoForge 1.21.1 is no longer maintained**: no new features, no bug fixes, no rebuilds.
  The last NeoForge build is the one at the pause commit below and keeps working as-is.
- New changes land **only** under `forge/1.19.2/`. Do **not** mirror forge edits into
  `neoforge/1.21.1/` while the pause is in effect.

## Pause record

| | |
|---|---|
| Pause commit (last synced state) | `9ec0ebc` — feat(ask): per-ask JSONL audit trace + modular-tool single-item & frame-card handling |
| Pause date | 2026-09-14 |
| Reason | Cost of dual-tree lockstep (mirror every change, shim differences, and a test classpath that has been broken since mid-August) outweighed the value of the 1.21.1 build. |
| Outward notice | GitHub README + pinned issue; CurseForge project description (1.21.1 file kept, marked unsupported). |

## How to resume

1. Diff `forge/1.19.2` against this tree from `9ec0ebc` onwards and port the changes
   (`git log --oneline 9ec0ebc..HEAD -- forge/1.19.2`).
2. Check the loader shims that legitimately differ (`ForgeConfigSpec`/`ModConfigSpec`,
   `WidgetCompat.tipLines`/`tip`, JEI/registry APIs).
3. Fix the NeoForge test classpath (gson is missing from the `test` configurations, so
   `compileTestJava` has been failing since 2026-08).
4. Re-enable the dual-tree gates (`tests/check_dual_tree_sync.py`,
   `tests/check_dual_tree_diff_symmetry.py`) and update the contract in the
   `minecraft-modpack-ai-development` skill.
