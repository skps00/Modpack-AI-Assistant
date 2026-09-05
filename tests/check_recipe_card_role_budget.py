#!/usr/bin/env python3
"""Mirror JeiRecipeCards collect budget: separate OUTPUT / INPUT caps."""


def allocate_roles(out_count: int, in_count: int, max_out: int, max_in: int) -> list[str]:
    """Each role gets its own cap; not a shared remainder pool."""
    out_n = min(max(0, int(out_count)), max(0, int(max_out)))
    in_n = min(max(0, int(in_count)), max(0, int(max_in)))
    return (["output"] * out_n) + (["input"] * in_n)


def ask_total_budget(item_count: int, per_out: int, per_use: int) -> int:
    """Mirror AskService.collectAskRecipeCards total cap."""
    clamp = lambda n: max(1, min(8, int(n)))
    return max(1, int(item_count)) * (clamp(per_out) + clamp(per_use))


def prompt_card_line(role: str, cat: str, ins: str, outs: str) -> str:
    head = f"role={role} | {cat}"
    if not ins and not outs:
        return head
    if not ins:
        return f"{head} | → {outs}"
    if not outs:
        return f"{head} | {ins}"
    return f"{head} | {ins} → {outs}"


def caption_key(is_input: bool, is_strip: bool, is_quest: bool = False) -> str:
    if is_strip:
        return "literal"
    if is_input:
        return "packai.screen.recipe_use"
    if is_quest:
        return "packai.screen.quest_reward"
    return "packai.screen.recipe"


def main() -> None:
    # blood_bottle: 0 obtain recipes, several uses → up to max_in INPUT
    assert allocate_roles(0, 5, 3, 3) == ["input", "input", "input"]
    # different caps: out=1, use=5
    assert allocate_roles(2, 5, 1, 5) == ["output", "input", "input", "input", "input", "input"]
    # craftable + uses: both roles get their caps
    assert allocate_roles(2, 5, 3, 3) == ["output", "output", "input", "input", "input"]
    # full both sides
    assert allocate_roles(5, 5, 3, 3) == ["output"] * 3 + ["input"] * 3
    assert allocate_roles(0, 0, 3, 3) == []
    assert ask_total_budget(1, 3, 3) == 6
    assert ask_total_budget(1, 2, 5) == 7
    assert ask_total_budget(3, 3, 3) == 18

    assert prompt_card_line("input", "Mixing Cauldron", "Blood Bottle", "Elixir").startswith(
        "role=input |"
    )
    assert prompt_card_line("output", "Crafting", "A, B", "C").startswith("role=output |")
    assert caption_key(True, False) == "packai.screen.recipe_use"
    assert caption_key(False, False) == "packai.screen.recipe"
    assert caption_key(False, False, True) == "packai.screen.quest_reward"
    assert caption_key(True, True) == "literal"

    for path in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/RecipeCard.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/RecipeCard.java",
    ):
        src = open(path, encoding="utf-8").read()
        assert "public String promptRole()" in src
        assert 'return "quest"' in src
        assert "packai.screen.quest_reward" in src

    for path in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java",
    ):
        src = open(path, encoding="utf-8").read()
        assert "card.captionLangKey()" in src

    for path in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java",
    ):
        src = open(path, encoding="utf-8").read()
        assert "hadLeadIn" not in src
        assert "appendRecipeCardCaption(lines, card);" in src
        assert "scrollbarThumbH" in src

    for path in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/jei/JeiRecipeCards.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/jei/JeiRecipeCards.java",
    ):
        src = open(path, encoding="utf-8").read()
        assert "fill remaining with INPUT" not in src
        assert "collectRole(stack, RecipeIngredientRole.OUTPUT, maxOutput, seen)" in src
        assert "collectRole(stack, RecipeIngredientRole.INPUT, maxInput, seen)" in src
        assert "int remain = maxCards - out.size()" not in src
        assert "fromVanillaCraftingUses" in src
        assert "mergeVanillaUses" in src
        assert ".includeHidden()" in src

    for path in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/service/AskService.java",
    ):
        src = open(path, encoding="utf-8").read()
        assert "recipeCardsPerItemUse()" in src
        assert "perOut + perUse" in src
        # Wave-2 (2026-09-05): AskService uses the split forItemParts — normal budget
        # trimmed first, MAINTENANCE cards appended trailing (never displace normal)
        assert "forItemParts(focus, perOut, perUse)" in src
        assert "parts.normal()" in src and "parts.maintenance()" in src

    for path in (
        "forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/config/PackAiConfig.java",
    ):
        src = open(path, encoding="utf-8").read()
        assert 'defineInRange("recipeCardsPerItem"' in src
        assert 'defineInRange("recipeCardsPerItemUse"' in src

    print("check_recipe_card_role_budget: OK")


if __name__ == "__main__":
    main()
