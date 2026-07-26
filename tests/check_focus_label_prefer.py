#!/usr/bin/env python3
"""Mirror IngredientReqHints.pickSample: prefer focus when ingredient accepts it."""


def pick_sample(items: list[str], prefer: str | None, accepts: set[str]) -> str | None:
    if prefer and prefer in accepts:
        return prefer
    for item in items:
        if item:
            return item
    return None


def main() -> None:
    planks = ["oak_planks", "spruce_planks", "birch_planks"]
    accepts = set(planks)
    assert pick_sample(planks, "spruce_planks", accepts) == "spruce_planks"
    assert pick_sample(planks, None, accepts) == "oak_planks"
    assert pick_sample(planks, "diamond", accepts) == "oak_planks"
    # prefer wins even when sample list empty (ingredient.test still accepts)
    assert pick_sample([], "spruce_planks", accepts) == "spruce_planks"
    assert pick_sample([], "diamond", accepts) is None
    print("ok focus_label_prefer")


if __name__ == "__main__":
    main()
