# Ask Hybrid tool-loop — acceptance harness (3 cases)

Status: **Forge v1 implemented** (loop code on `feature/ask-tool-context`). Eng-review **LOCKED** 2026-08-13; **logic errata same day**. Neo mirror = T8 / `TODOS.md`.  
Parent design: `~/.gstack/projects/super_minecraft_AI_player/skps9-feature-ask-tool-context-design-20260812-233217.md`  
Priority: **準 > 快**. Strategy: Plan B shot-0 → variant prefetch if needed → **intent-scoped empty-gate** drain other unrun tools → LLM only if intent-relevant hits. Native `tools` / JSON only in escalate mode (after drain + 1 grounding hop still ungrounded).  
UI theater (D): deferred — escalate **silent**; one visible final reply.

Instance: Prism **AI_test_NFWC_DIM** (Forge 1.19.2). Keybind `;`.

## Allowlisted tools (contract)

| Tool | Local source | Result budget (reuse Plan B) |
|------|--------------|------------------------------|
| `jei_lookup` | `JeiLookup.summarize` / recipe catalog | OUTPUT ≤4k, USES ≤400, catalyst ≤600 |
| `acquire` | PackIndex ranked obtain + loot edges | ≤12 lines (`MAX_ACQUIRE_LINES_FULL`) |
| `guide_fetch` | GuidebookIndex / Patchouli pins | existing guide pin budgets |
| `quest_fetch` | `QuestGuide` | existing quest fact clip |
| `consume_use` | `ItemConsumeUseFacts` | existing `[CONSUME_USE]` clip |

Hard cap: **MAX_LLM_ROUNDS = 3** (HTTP; **400-on-tools probe excluded**) AND **MAX_LOCAL_TOOLS = 8** AND wall **90s from Ask click** (includes client JEI). HTTP timeout = `min(90s, remaining wall)`. Duplicate `(tool, itemId, level, sorted variant keys)` aborts.

**Empty gate** (lock, **intent-scoped**, not whole-FACT):
- **Craft:** JEI OUTPUT for this focus empty (after variant prefetch) **or** recipe HonestMiss-only. Fat acquire does **not** skip drain.
- **Obtain:** ranked acquire empty **or** noise-only (trivial self-loot / no ranked obtain) **or** HonestMiss-only. Fat JEI does **not** skip drain.
- A few **intent-relevant** lines = not empty → happy-path LLM, not drain-as-if-miss.

## Shared flow (errata)

1. Intent gate → craft/obtain ⇒ `JeiDumpLevel.OUTPUT` + full acquire clip (shot-0). Purpose/idle ⇒ SLIM.
2. **Purpose/idle:** `LlmClient.ask()` **without** `tools` schema. No drain, no grounding. Stop.
3. **Craft/obtain — before first LLM:**
   - **Always:** `jei_lookup` with variant keys if focus has them and those args not yet run (bare-id shot-0 ≠ this). **D16 replace** JEI with this result (drop other-variant dump). Variant empty → JEI counts as empty for craft-empty gate.
   - **Empty-gate only** (intent-scoped): drain **other** unrun related tools. Skip same `(tool, canonical args)` as shot-0.
     - Craft: `guide_fetch` → `quest_fetch` (do **not** re-call same-args `jei_lookup`). Runs even if acquire is fat.
     - Obtain: `acquire` only if shot-0 did not already run full acquire same args; then guide → quest → `consume_use`. Runs even if JEI is fat.
   - Intent-relevant hits already present → **no** guide/quest drain. Go LLM.
4. Drain still no **intent-relevant** hits → HonestMiss, **0 LLM**. Hits → drop miss pin, D16-replace, then `ask()` **without** tools.
5. Grounding (craft/obtain replies only): claims must match **this focus + variant**. Max **1** extra lookup, **new args only**. Other-variant recipes ≠ support. Then stop (keep/scrub or miss).
6. Escalate `completeRound` WITH tools only if reply still ungrounded after (4)+(5) **and** unrun related tools remain. 400 → `ask()` with **updated** FACT, no tools. JSON hop only unrun tools.

---

## H1 — Craft + empty JEI → drain **other** sources, not a second identical `jei_lookup`

**Why:** Shot-0 already ran `JeiLookup.summarize`. Same `(jei_lookup, item, OUTPUT)` is a no-op and would hit dup-abort. Recovery is guide/quest (or variant-args JEI). **Craft-empty even if acquire is fat.** If those empty too → miss, **no LLM**.

**NFWC setup**

| Field | Value |
|-------|--------|
| Focus | Item whose shot-0 JEI OUTPUT is empty (no recipes, or fixture). |
| Ask | `這個怎麼合成？` / `配方怎麼做` |
| Intent | craft → shot-0 `OUTPUT` |

**Escalate:** craft + empty JEI after shot-0 → drain unrun related tools **before any LLM**, even if acquire has lines. Do **not** re-call `jei_lookup` with the same args.

**Expected sequence**

```
shot0   Plan B OUTPUT (empty) + acquire (may be fat — ignore for craft-empty)
        record: jei_lookup(item, OUTPUT) already done
drain   guide_fetch(item)           # skip jei_lookup same args
        quest_fetch(item) if still no craft hits
        jei_lookup only if NEW args (variant keys) — not this H1
if craft hits → drop miss pin → LLM ask() no tools → visible reply
if no craft hits → HonestMiss, STOP (0 LLM)
```

**PASS**

- [ ] Does **not** emit a second `jei_lookup` with identical args as shot-0.
- [ ] `guide_fetch` and/or `quest_fetch` run before any LLM when JEI empty.
- [ ] Hits → reply from updated FACT; no invented ingredients.
- [ ] Drain empty of **craft** evidence → **honest miss**, **zero** LLM HTTP (loot lines do not count as craft hits).
- [ ] Tool payloads respect Plan B budgets. Silent (no D UI).

**FAIL**

- Invents recipe after empty drain.
- Duplicate same-args `jei_lookup`.
- LLM called when drain still has no craft evidence (including “acquire was fat so skip drain”).
- Full U encyclopedia dump.

---

## H2 — Obtain + thin acquire → drain unrun obtain tools, LLM only if hits

**Why:** Shot-0 acquire may be empty/noise or HonestMiss-only. **Obtain-empty even if JEI is fat.** Re-run `acquire` only if shot-0 did **not** already do full acquire with same args (fixture: shot-0 stripped). Then guide → quest → consume_use. Still no obtain hits → miss, 0 LLM.

**NFWC setup**

| Field | Value |
|-------|--------|
| Focus | Item with obtain in guide/quest/loot that shot-0 acquire missed (or fixture strip acquire to 0). Gateways / LootJS family OK. |
| Ask | `如何取得？` / `how to get` |
| Intent | obtain → shot-0 `OUTPUT` + acquire ≤12 |

**Escalate:** obtain + empty/noise/HonestMiss-only acquire → drain unrun related tools before LLM, even if JEI has recipes.

**Expected sequence**

```
shot0   Plan B OUTPUT (may be fat — ignore for obtain-empty) + acquire (empty / noise / miss pin / fixture 0)
drain   acquire(item) if shot0 did not already run full acquire same args
        guide_fetch(item)
        quest_fetch(item)
        consume_use(item)
if obtain hits → drop miss pin → D16 replace acquire section → LLM ask() no tools
if no obtain hits → HonestMiss, STOP (0 LLM)
```

If shot-0 already ran full acquire (empty result), **skip** same-args `acquire`; still drain guide/quest/consume_use.

**PASS**

- [ ] Unrun obtain tools run **before** any invent-loot LLM.
- [ ] Hits → reply lead with FACT `{{item:ns:id}}`; no fake stage lists.
- [ ] Drain empty of **obtain** evidence → honest miss, **0 LLM** (JEI recipes do not count as obtain hits).
- [ ] Purpose control (`用途是什麼`) → **0** extra tools, `ask()` no tools schema.
- [ ] Local tool count ≤ 8; LLM rounds ≤ 3.

**FAIL**

- Invents loot/stage without tool evidence.
- Skips `guide_fetch`/`quest_fetch` while they would hit.
- Calls LLM on still-empty obtain drain (including “JEI was fat so skip drain”).
- Purpose ask escalates.

---

## H3 — Craft variant miss → `jei_lookup` with **different args** (variant keys)

**Why:** Shot-0 may dump bare-id / wrong variant. That is **not** a dup of `jei_lookup(item, variant_keys=…)`. Drain that lookup **before** first LLM. Grounding: at most **one** extra lookup if drain missed it.

**NFWC setup**

| Field | Value |
|-------|--------|
| Focus | `irons_spellbooks:scroll` **with** spell NBT (`ISB_Spells`). |
| Ask | `這個卷軸配方怎麼做？` / `how to make this scroll` |
| Intent | craft → shot-0 `OUTPUT` (may be non-empty but wrong variant) |

**Live vs fixture:** After Plan B `ItemVariantKeys` / `ISB_Spells`, shot-0 may **already** dump the matching spell recipe. Then **no extra** `jei_lookup` — PASS = correct spell, not “must call tool”. Fixture / stub: shot-0 **without** variant keys (bare id) to force the hop.

**Escalate:** shot-0 missing this spell’s recipe. Not hedge words.

**Expected sequence (fixture: shot-0 bare-id)**

```
shot0   Plan B OUTPUT (bare id / wrong variant)
        record: jei_lookup(scroll, OUTPUT, no variant) maybe done
ALWAYS  jei_lookup(scroll, variant_keys=<ISB_Spells ids>, OUTPUT)   # args differ
        D16 replace JEI (drop other-variant dump)
        if variant HIT → not craft-empty → LLM ask() no tools
        if variant EMPTY → craft-empty gate → drain guide_fetch → quest_fetch
          craft hits → drop miss pin → LLM ask() no tools
          no craft hits → HonestMiss, STOP (0 LLM)
ground  skip if variant lookup already ran; else max 1 new-args lookup then stop
        other-variant recipes in FACT ≠ grounded
```

**PASS**

- [ ] If shot-0 already has this spell’s recipe → **0** extra `jei_lookup`; reply matches focused spell.
- [ ] Else extra `jei_lookup` includes **variant keys**, not bare id only; args ≠ shot-0.
- [ ] Final recipe matches focused spell.
- [ ] Recipe cards still attach for craft ask.
- [ ] Extra lookup ≤ 1. LLM rounds ≤ 3. Budgets held.
- [ ] Variant JEI empty after drain → **honest miss** (or scrub); **not** invented ink/paper; **not** keep other-variant recipes as FACT.

**FAIL**

- Generic scroll / wrong spell.
- hideUpgrade wipes the only relevant recipe.
- Grounding loops 2+ identical variant lookups.
- Re-dumps full JEI-U encyclopedia.
- Forces a second same-args `jei_lookup` when shot-0 already had the variant.

---

## Control (not a 4th harness — regression guard)

| Ask | Focus | Expected |
|-----|--------|----------|
| `用途是什麼` | any common item with PURPOSE | Plan B **SLIM**, **0** extra tools, `ask()` **no** tools schema, near Plan B latency |

---

## How to run later

1. **Unit / fixture:** `python tests/check_ask_tool_loop.py`；Forge `AskToolLoopCheck`／`AskGroundingCheck` `-ea`（headless）。
2. **NFWC CUA:** jar → dist → mods；**重開** instance；Ask H1–H3；screenshot `dist/cua_ask_tool_h{1,2,3}.png`。
3. **Lockstep:** same cases on Neo/ATM10 when Neo tree has the loop.

## Out of scope (this harness)

- Live tool cards / JEI highlight (D).
- Ungated always-on tools from turn 0.
- Web / wiki override.
- Phase C guidebook RAG chunk quality (separate plan).
