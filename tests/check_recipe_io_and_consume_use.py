# -*- coding: utf-8 -*-
"""Recipe catalog Name×N aggregation + consume_use PURPOSE wiring."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for side in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        summary = read(f"{side}/logic/RecipeIoSummary.java")
        assert "joinNamedCounts" in summary
        assert "MAX_UNIQUE" in summary
        assert "append('×')" in summary or 'append("×")' in summary or "a.count" in summary
        ask = read(f"{side}/client/service/AskService.java")
        assert "RecipeIoSummary.joinStackNames" in ask
        assert "n >= 8" not in ask
        jei = read(f"{side}/client/jei/JeiRecipeCards.java")
        assert "RecipeIoSummary.countFilledInputs" in jei
        assert "tryCrafting" in jei
        facts = read(f"{side}/logic/ItemConsumeUseFacts.java")
        assert "CONSUME_USE" in facts
        assert "minecraft:consume_item" in facts

    print("check_recipe_io_and_consume_use OK")


if __name__ == "__main__":
    main()
