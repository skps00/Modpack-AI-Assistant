#!/usr/bin/env python3
"""JEI cardFocus registry id → LLM focusItemId fallback (Forge + Neo lockstep)."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SIDES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai",
)
ASK_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "client" / "service" / "AskService.java",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "client" / "service" / "AskService.java",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def resolve_focus_item_id(
    *,
    held_id: str | None,
    question: str,
    jei_focus_id: str | None,
) -> str | None:
    """Mirror LlmClient.completeRound focusItemId priority."""
    out: str | None = None
    if held_id:
        out = held_id
    q = question or ""
    for token in q.replace("（", "(").replace("）", ")").split():
        if ":" in token and token.count(":") == 1:
            mod, item = token.strip("()[]，,.？！。?!").split(":", 1)
            if mod and item:
                out = f"{mod}:{item}"
                break
    if out is None and jei_focus_id:
        out = jei_focus_id.strip()
    return out


def begin_ask_loop_item_id(focus_id: str | None, card_focus_id: str | None) -> str:
    """Mirror AskService.beginAskLoop itemId for AskLoopState."""
    if focus_id:
        return focus_id
    return card_focus_id or ""


def mirror_recipe_cards_catalog_slim(jei_text: str) -> str | None:
    """Mirror AskEngine.recipeCardsCatalogSlim ([RECIPE_CARDS] marker → last catalog entry)."""
    if not jei_text or not jei_text.strip():
        return None
    idx = jei_text.find("[RECIPE_CARDS]")
    if idx < 0:
        return None
    out: list[str] = []
    entry = re.compile(r"^\d+ \| .*role=")
    for line in jei_text[idx:].split("\n"):
        if not out:
            if "[RECIPE_CARDS]" not in line:
                continue
            out.append(line)
        elif entry.match(line.strip()):
            out.append(line)
        else:
            break
    return "\n".join(out) if out else None


def jei_for_llm_slim_catalog(recipe_catalog: str, jei_text: str) -> str | None:
    """Mirror AskEngine.jeiForLlmSlim catalog source: stable recipeCatalog first, jeiText fallback."""
    catalog = mirror_recipe_cards_catalog_slim(recipe_catalog)
    if catalog is None or not catalog.strip():
        catalog = mirror_recipe_cards_catalog_slim(jei_text)
    return catalog if catalog and catalog.strip() else None


def main() -> None:
    # held wins when present; question mod:id overwrites; JEI fallback when both absent.
    assert resolve_focus_item_id(
        held_id="minecraft:stick",
        question="硫磺花蜜怎么来？",
        jei_focus_id="bosses_of_mass_destruction:brimstone_nectar",
    ) == "minecraft:stick"
    assert resolve_focus_item_id(
        held_id=None,
        question="怎么用 bosses_of_mass_destruction:brimstone_nectar？",
        jei_focus_id="bosses_of_mass_destruction:sulfur_nectar",
    ) == "bosses_of_mass_destruction:brimstone_nectar"
    assert resolve_focus_item_id(
        held_id=None,
        question="硫磺花蜜怎么来？",
        jei_focus_id="bosses_of_mass_destruction:brimstone_nectar",
    ) == "bosses_of_mass_destruction:brimstone_nectar"
    assert resolve_focus_item_id(
        held_id=None,
        question="硫磺花蜜怎么来？",
        jei_focus_id=None,
    ) is None

    assert begin_ask_loop_item_id(None, "mod:item") == "mod:item"
    assert begin_ask_loop_item_id("mod:a", "mod:b") == "mod:a"

    # Stable recipeCatalog: seeded from shot-0 [RECIPE_CARDS], never overwritten by tool results.
    cards = "Season\n[RECIPE_CARDS] catalog\n0 | role=output | Crafting | iron → pick\nREQ"
    catalog = mirror_recipe_cards_catalog_slim(cards)
    assert catalog is not None and catalog.startswith("[RECIPE_CARDS]")
    assert "role=output" in catalog and "Season" not in catalog and "REQ" not in catalog
    # Later jei_lookup result without cards must not erase the stable catalog.
    assert jei_for_llm_slim_catalog(catalog, "variant result without cards") == catalog
    # Empty stable catalog falls back to jeiText.
    assert jei_for_llm_slim_catalog("", cards) == catalog
    assert jei_for_llm_slim_catalog("", "no cards at all") is None

    for main in SIDES:
        llm = read(main / "logic" / "LlmClient.java")
        engine = read(main / "logic" / "AskEngine.java")
        assert "String jeiFocusItemId" in llm
        assert "JEI card focus when question has no mod:id and held empty" in llm
        assert "!user.containsKey(\"focusItemId\")" in llm
        assert "String jeiFocusItemId" in engine
        assert "jeiFocusId" in engine
        assert "jeiFocusId, toolNames" in engine
        # jeiForLlmSlim: stable recipeCatalog first, jeiText fallback.
        assert "recipeCardsCatalogSlim(loopState.recipeCatalog())" in engine
        assert "recipeCardsCatalogSlim(loopState.jeiText())" in engine

    for path in ASK_PATHS:
        src = read(path)
        assert "cardFocusItemId(" in src
        assert "jeiFocusItemId" in src
        assert "cardFocusItemId(cardFocus)" in src
        assert "purposeGuide, jeiFocusItemId, askLoop" in src
        # beginAskLoop seeds the stable catalog from the shot-0 jei block.
        assert "setRecipeCatalog(" in src
        assert "AskEngine.recipeCardsCatalogSlim(text)" in src

    print("check_llm_focus_item_id OK")


if __name__ == "__main__":
    main()
