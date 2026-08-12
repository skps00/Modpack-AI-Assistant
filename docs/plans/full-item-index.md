# Plan: Full item / mod index (離線全物品索引)

Status: **in progress (accuracy wave WP3)** — see [accuracy-first-next-wave.md](accuracy-first-next-wave.md).  
Related: [CURSEFORGE_DESCRIPTION.md](../CURSEFORGE_DESCRIPTION.md); [four-issue-backlog.md](four-issue-backlog.md).

## Locked decisions (2026-08-10)

| # | Topic | Choice |
|---|--------|--------|
| 1 | UI | **A already ships** (`ItemSearch` + Ask search hits). v1 work = **fast index under A**, not new search box. **B** (separate catalog screen) = optional later / marketing — only if A+index still feels weak. |
| 2 | When to build | **Not** “settings opt-in vs always-on UI”. Build/cache on **first pack join** (after client ready); **rebuild only when mod-list fingerprint changes** (and lang / resource reload as needed). Silent reuse of disk cache otherwise. |
| 3 | Priority | **Four-issue backlog first.** This plan idle until that track clears (or you explicitly pull it forward). |
| 4 | Spike pack | **Primary: NFWC (Forge)** — your heavy KubeJS + real JEI load. **ATM10 optional ceiling** for “many mods / huge registry” if NFWC numbers look OK but Neo path still unknown. No Neo-only spike as default. |

## Goal (revised)

Make pack item search **fast and cacheable** — not invent a second discovery UI.

**Non-goal (v1):** competitor animated crafting; tooltip-as-gates; replace `PackIndex`; new B screen unless A+index still inadequate.

## What we already have (FACT)

| Piece | Role |
| ----- | ---- |
| `ItemSearch` | Live scan: JEI `getAllIngredients` + `Registry.ITEM`; score by name/id; cap 80 candidates → top 10 |
| `AiAssistantScreen` | Search hits UI → `applySearchHit` → focus / ask |
| Comment in `ItemSearch` | `Upgrade: debounce + prefix index` — this plan *is* that upgrade |
| `PackIndex` / `JarLightIndex` | Scripts / quests / jar hints — **not** the item encyclopedia |

**Gap:** every query can full-walk JEI+registry (cost scales with pack size). No persistent prefix/token index keyed by mod-list.

## Clarification on Q2 (what was confusing)

Earlier “default on / opt-in” meant: **should indexing run without the player flipping a setting?**

Your answer maps to the right model:

```
first launch of this modpack (this client profile)
  → build index once → write cache (mod-list hash + lang + MC)
later launches, same mod list
  → load cache, skip rebuild
mods added/removed/updated (hash change)
  → rebuild once
```

No need for a separate “enable item index” toggle for v1 unless rebuild is too heavy and you want a kill switch later.

## Proposed design (after backlog)

### Index contents
1. Registry id + display name (+ modid bucket)  
2. Prefer merging JEI stacks when JEI present (NBT variants) — same as today’s `ItemSearch` source  
3. No tooltip scrape as craft truth

### Build / cache
- Async after world/client ready (no main-thread freeze)  
- Disk: e.g. `config/packai/item-index/` keyed by `mc + loader + lang + modListFingerprint`  
- Rebuild triggers: fingerprint change, lang change, resource reload, manual “rebuild” if we add one  
- Cap + spam filters (reuse JEI spam ideas)

### UX
- Keep **A** (Ask search). Wire search to **prefix/token index** instead of full scan.  
- **B** only if product still wants a “catalog mode” story after A is fast.

### Phases (when unblocked)
1. **Spike on NFWC** — time/memory of today’s live `ItemSearch` vs building a prefix index once  
2. Cache + fingerprint rebuild  
3. Wire Ask search to index  
4. Optional: ATM10 Neo ceiling check  
5. Optional **B** screen / CF one-liner after it feels real

## Risks

- JEI ingredient list huge → build must stay async  
- SlashBlade-style NBT siblings need JEI merge (registry alone insufficient) — already partially handled in live search  
- Don’t block four-issue PRs

## Done when (v1, later)

- [x] First join builds once; second join with same mods skips rebuild *(code path: disk meta match → load; NFWC smoke deferred silent)*
- [x] Mod add/remove forces rebuild *(fingerprint includes modId@version; fixture)*
- [x] Ask search uses index; no full JEI walk per keystroke (or only fallback)
- [ ] NFWC smoke: search usable, no freeze *(deferred — silent mode / no CUA)*
- [x] Tests: fingerprint / cache hit-miss
- [ ] CF description line only after ship

## Next step

Implement under **accuracy-first WP3** only (after WP1–WP2). Start with NFWC timing spike of current `ItemSearch`, then cache + wire Ask search.
