# packai-knowledge repo — pending pieces

`validate.yml` is the CI workflow for the knowledge repo (`skps00/packai-knowledge`).
It could NOT be pushed from this machine: the `gh` OAuth token lacks the **`workflow`** scope
(GitHub refuses `.github/workflows/*` pushes without it).

To add it (pick one):
1. `gh auth refresh -s workflow` in a terminal (browser approval once), then copy this file to
   `.github/workflows/validate.yml` in the knowledge repo and push; or
2. paste its content into the knowledge repo's web UI (Add file → .github/workflows/validate.yml); or
3. skip CI — the same check runs locally via `python scripts/validate.py`.

Note: the knowledge repo's validator enforces the dedupe + provenance rules (tier A/B/C, `source`
required, duplicate fact keys rejected), which the mod's KB-1/KB-3 work relies on.
