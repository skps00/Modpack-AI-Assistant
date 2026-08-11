#!/usr/bin/env python3
"""Mirror JeiFocusMatch: concrete registry id never matches other mods by display name."""

from __future__ import annotations


def output_matches_focus(
    output_item: str,
    focus_item: str,
    *,
    focus_name: str = "",
    output_name: str = "",
    focus_schematics: list[str] | None = None,
    output_schematics: list[str] | None = None,
    same_tags: bool = False,
) -> bool:
    """Subset of JeiFocusMatch OUTPUT policy after id-strict fix."""
    if same_tags and output_item == focus_item:
        return True
    if output_item != focus_item:
        # Hard reject: never accept by localized name alone across registry ids.
        return False
    name_useful = bool(focus_name) and focus_name.lower() not in {
        focus_item.lower(),
        focus_item.split(":", 1)[-1].lower().replace("_", " "),
        focus_item.split(":", 1)[-1].lower(),
        "item",
    }
    prefer = []
    for raw in focus_schematics or []:
        id_ = raw.strip().lower()
        prefer.extend([id_, id_.split(":", 1)[-1], id_.split("/")[-1]])
    out_blob = " ".join([output_name or ""] + list(output_schematics or [])).lower()
    mentions = any(t in out_blob for t in prefer if len(t) >= 2)
    has_variant = bool(focus_schematics)
    if has_variant:
        return focus_name == output_name or mentions
    if not name_useful or focus_name == output_name:
        return True
    return False


def ask_cards_per_item(configured: int, unique_keys: int) -> int:
    """Mirror AskService.collectAskRecipeCards — settings apply for any focus count."""
    _ = unique_keys
    return max(1, min(8, configured))


def main() -> None:
    # Bug: create:wrench vs other-mod wrench sharing zh name 扳手
    assert not output_matches_focus(
        "othermod:blue_wrench",
        "create:wrench",
        focus_name="扳手",
        output_name="扳手",
    )
    assert output_matches_focus(
        "create:wrench",
        "create:wrench",
        focus_name="扳手",
        output_name="扳手",
    )
    # Same item still OK
    assert output_matches_focus("minecraft:diamond", "minecraft:diamond")
    # Settings honor: single focus uses configured (not soft-capped to 1)
    assert ask_cards_per_item(3, 1) == 3
    assert ask_cards_per_item(3, 2) == 3
    assert ask_cards_per_item(1, 1) == 1

    # Source guards (forge + neo)
    for path in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/jei/JeiFocusMatch.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/jei/JeiFocusMatch.java",
    ):
        src = open(path, encoding="utf-8").read()
        assert "never matches other mods by localized name" in src
        assert "if (!stack.is(focus.getItem()))" in src
        # Old cross-item name accept path must be gone
        assert "if (nameUseful && focusName.equals(stackName))" not in src

    for path in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/service/AskService.java",
    ):
        src = open(path, encoding="utf-8").read()
        assert "Math.min(configured, 1)" not in src
        assert "PackAiConfig.recipeCardsPerItem()" in src
        assert "PackAiConfig.recipeCardsPerItemUse()" in src
        assert "JeiRecipeCards.forItem(focus, perOut, perUse)" in src

    for path in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/jei/JeiRecipeCards.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/jei/JeiRecipeCards.java",
    ):
        src = open(path, encoding="utf-8").read()
        assert "cardOutputMatchesFocus" in src
        assert "unit.setCount(1)" in src

    print("check_jei_focus_id_strict OK")


if __name__ == "__main__":
    main()
