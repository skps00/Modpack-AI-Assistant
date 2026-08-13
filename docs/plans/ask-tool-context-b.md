# Ask tool context — Plan B (token)

Status: **implemented on `feature/ask-tool-context`** (intent-gated progressive fetch).  
Goal: cut Ask LLM input tokens without dumping full JEI-U / loot encyclopedias every turn.

## Approach

- **Not** ungated always-on tools from turn 0. Happy path = `LlmClient.ask()` without `tools` schema; `completeRound` only in escalate.
- **MVP B:** client-side intent gate + hard section budgets (`AskToolContext`).
- Recipe cards stay local UI; honesty / PURPOSE / GUIDE / CONSUME_USE stay.
- Upgrade path: Hybrid multi-turn tool loop (B+C). **Eng-review locked 2026-08-13**; **logic errata same day** (drain unrun tools before LLM; happy path `ask()` no tools schema). Implement Forge first per design `skps9-feature-ask-tool-context-design-20260812-233217.md` § Eng review locks. Acceptance: [`ask-tool-loop-harness.md`](ask-tool-loop-harness.md). Backlog: repo `TODOS.md`.

## Behavior

| Question intent | JEI text dump | Acquire / loot |
|-----------------|---------------|----------------|
| 配方 / craft / how to make | `OUTPUT` (≤4k OUTPUT, ≤400 USES, ≤600 catalyst) | up to 12 ranked lines |
| 取得 / obtain / how to get | same `OUTPUT` | up to 12 ranked lines |
| Purpose / default / idle | `SLIM` (≤900 OUTPUT, ≤400 USES, no catalyst dump) | top **3** ranked lines; no loot graph overflow |

`maxJeiChars` remains a global ceiling; section budgets bite first.

## Key files

- `logic/AskToolContext.java` (Forge + Neo)
- `client/jei/JeiLookup.java` — `summarize(stack, level)`
- `client/service/AskService.java` — intent → level
- `logic/AskEngine.java` — clip acquire / U / OUTPUT; skip loot overflow when slim
- `tests/check_ask_tool_context.py`

## Retest

```text
python tests/check_ask_tool_context.py
python tests/check_ask_purpose_order.py
python tests/check_ask_purpose_context.py
python tests/check_recipe_cards_mode.py
```

NFWC: rebuild Forge jar → dist → mods; Ask 用途 (slim FACT) vs 配方/取得 (OUTPUT); cards still attach; `;` open.
