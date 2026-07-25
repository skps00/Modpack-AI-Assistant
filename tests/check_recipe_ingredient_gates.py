#!/usr/bin/env python3
"""Mirror RecipeIngredientGates.formatRequest — original recipe request → labels."""


def format_request(
    *,
    kill: int = 0,
    proud_soul: int = 0,
    refine: int = 0,
    name: str | None = None,
    sword_types: list[str] | None = None,
) -> list[str]:
    out: list[str] = []
    if kill > 0:
        out.append(f"kill≥{kill}")
    if proud_soul > 0:
        out.append(f"proud_soul≥{proud_soul}")
    if refine > 0:
        out.append(f"refine≥{refine}")
    if name:
        lower = name.lower()
        if lower and not lower.endswith(":none") and lower != "none":
            path = name.split(":", 1)[-1]
            out.append(f"blade:{path}")
    for t in sword_types or []:
        s = t.split(".")[-1].lower()
        if s and s != "none":
            out.append(s)
    return out[:8]


def main() -> None:
    # Real amazing_shine.json blade ingredient request
    gates = format_request(refine=100, sword_types=["broken"])
    assert gates == ["refine≥100", "broken"], gates

    # Alternate path with named blade
    gates2 = format_request(
        refine=100,
        name="slashblade_addon:fluorescent_bar",
        sword_types=["BROKEN"],
    )
    assert "refine≥100" in gates2
    assert "blade:fluorescent_bar" in gates2
    assert "broken" in gates2

    # Zero thresholds omitted
    assert format_request(kill=0, proud_soul=0, refine=0) == []

    # Full gates
    full = format_request(kill=1000, proud_soul=5000, refine=50)
    assert full == ["kill≥1000", "proud_soul≥5000", "refine≥50"], full

    print("ok recipe_ingredient_gates_from_original_request")


if __name__ == "__main__":
    main()
