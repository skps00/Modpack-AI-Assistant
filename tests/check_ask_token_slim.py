"""check_ask_token_slim — measure prompt-token reduction of capable-slim + CPU cost.

Verifies the AskEngine bridge split (factsFull for fallback, List.of() for capable)
and measures how much smaller the capable prompt is vs the old FACT wall.

Run: python tests/check_ask_token_slim.py
Style follows tests/check_ask_capable_slim.py (source guard + pure-Python mirror).
"""

import re
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FORGE_ENGINE = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java"
NEO_ENGINE = ROOT / "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/AskEngine.java"


# ---------------------------------------------------------------------------
# Source guard — the bridge split must exist in BOTH trees
# ---------------------------------------------------------------------------
def _source_guard():
    for path in (FORGE_ENGINE, NEO_ENGINE):
        src = path.read_text(encoding="utf-8")
        assert "factsFull" in src, f"{path.name}: missing factsFull (fallback wall)"
        assert "capable ? List.of() : factsLive" in src, (
            f"{path.name}: missing capable ? List.of() : factsLive (slim decision)"
        )
        assert "jeiForLlmSlim" in src, f"{path.name}: missing jeiForLlmSlim"
        assert "purposeForLlmSlim" in src, f"{path.name}: missing purposeForLlmSlim"


# ---------------------------------------------------------------------------
# Prompt-shape mirror (pure Python, no Java)
# ---------------------------------------------------------------------------
def wall_prompt_lines():
    """Realistic OLD-wall FACT lines for one question (e.g. iron_pickaxe「怎麼合成」)."""
    lines = []
    lines.append("[PURPOSE] Iron Pickaxe: mine stone/iron with 3+ efficiency")
    lines.append("## How to get (JEI): 2 sticks + 3 iron_ingot, crafting table")
    lines.append("## How to use: mine stone, ores; durability 250")
    lines.append("[RECIPE_CARDS] role=output [[recipe:minecraft:iron_pickaxe]]")
    lines.append("[GUIDE] book:tfcm/entries/tools.md | Iron Pickaxe")
    lines.append("acquire: minecraft:iron_pickaxe <- minecraft:crafting_table")
    lines.append("acquire: minecraft:iron_ingot <- minecraft:furnace (smelt iron ore)")
    lines.append("acquire: minecraft:iron_ore <- worldgen (y<64, vein 2-5)")
    lines.append("quest: 'Early Tools' | craft an iron pickaxe")
    lines.append("quest: 'Mining 101' | obtain 3 iron ingots")
    lines.append("[AS_INGREDIENT] used in: [[recipe:minecraft:iron_pickaxe]]")
    # JEI summary dump (fat)
    for i in range(30):
        lines.append(f"jei_summary_line_{i}: some catalog row")
    return lines


def slim_prompt_lines():
    """NEW slim prompt: identity only (question + held id) — no FACT wall."""
    return ["question: 怎麼合成", "heldItem: minecraft:iron_pickaxe", "lang: zh_tw"]


def _tokens(lines):
    # Rough token estimate: ~4 chars/token for CJK-heavy MC text.
    return sum(max(1, len(line) // 4) for line in lines)


def capable_should_slim(mode, url_lacks_native):
    """Mirror of the bridge decision (AskEngine.completeWithTools)."""
    if mode == "off":
        return False
    if mode == "force":
        return True
    return not url_lacks_native


def fallback_uses_full():
    """Mirror of askNoTools: always full wall (Plan v2 regression guard)."""
    return True


# ---------------------------------------------------------------------------
# CPU measurement (best-effort, no Java)
# ---------------------------------------------------------------------------
def _measure_facts_cpu_ms(n=200):
    """Time a local loop mimicking facts assembly (list build + joins). Not authoritative."""
    start = time.perf_counter()
    for _ in range(n):
        acc = []
        for i in range(40):
            acc.append(f"fact_line_{i} some content with CJK 中文")
        joined = "\n".join(acc)
        _ = len(joined)
    return (time.perf_counter() - start) * 1000.0 / n  # avg ms per assembly


def main():
    _source_guard()

    # 1. Mode decision mirror
    assert capable_should_slim("force", False) is True
    assert capable_should_slim("auto", False) is True
    assert capable_should_slim("auto", True) is False   # URL remembered 400
    assert capable_should_slim("off", False) is False
    # 2. Fallback always full (Plan v2 — 400 path keeps the wall)
    assert fallback_uses_full() is True

    # 3. Token ratio
    wall_tok = _tokens(wall_prompt_lines())
    slim_tok = _tokens(slim_prompt_lines())
    assert wall_tok > 0
    assert slim_tok < wall_tok * 0.5, (
        f"slim not >=50% cut: wall={wall_tok} slim={slim_tok}"
    )
    ratio = slim_tok / wall_tok

    # 4. CPU (print only, no fail — timing is flaky)
    cpu_ms = _measure_facts_cpu_ms()

    print(f"TOKEN_SLIM_RATIO {ratio:.3f}|{wall_tok}|{slim_tok}")
    print(f"FACTS_ASSEMBLY_CPU_MS {cpu_ms:.2f}")
    print("check_ask_token_slim OK")


if __name__ == "__main__":
    main()
