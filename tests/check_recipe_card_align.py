#!/usr/bin/env python3
"""Reply↔card alignment: machine text must not attach Crafting-only uses."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for side in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        align = read(f"{side}/logic/RecipeCardAlign.java")
        assert "pickIndices" in align
        assert "replyLooksSpecific" in align
        assert "bestLineIndex" in align
        assert "strongMatch" in align
        assert "isGenericCraft" in align
        assert "制成" in align
        assert "祭坛" in align
        assert "SPECIFIC_PROSE" in align
        mode = read(f"{side}/logic/RecipeCardsMode.java")
        assert "RecipeCardAlign.pickIndices" in mode
        assert "replyLooksSpecific" in mode
        assert "case ALWAYS, KEYWORDS -> List.copyOf(collected)" in mode
        cards = read(f"{side}/client/jei/JeiRecipeCards.java")
        assert "pickWithCategoryDiversity" in cards
        assert "roleScanDone" in cards
        assert "distinctCategories" in cards
        assert "distinctNonGenericCategories" in cards
        assert "RecipeCardAlign.isGenericCraft" in cards
        ask = read(f"{side}/client/service/AskService.java")
        assert "result.answer()" in ask
        assert "buildDisplayCards" in ask
        assert "dropItem" in ask
        assert "show_recipe_card" in read(f"{side}/logic/ShowRecipeCardAskTool.java")
        assert "implements AskTool" in read(f"{side}/logic/ShowRecipeCardAskTool.java")
    print("check_recipe_card_align: OK")


if __name__ == "__main__":
    main()
