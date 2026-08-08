#!/usr/bin/env python3
"""Mirrors AskPurposeContext — purpose vs JEI-U ingredient + fuel/tool/food lines."""


def is_purpose_graph_fact(gf: str) -> bool:
    if not gf:
        return False
    return any(
        t in gf
        for t in (
            "-[desc]->",
            "-[score]->",
            "-[triggers]->",
            "-[on:",
            "-[right_click]->",
            "-[right_click_use]->",
            "-[right_click_as_block]->",
        )
    )


def build_purpose_block(tooltip: str | None, purpose_lines: list[str] | None) -> str:
    body: list[str] = []
    if tooltip and tooltip.strip():
        body.append(tooltip.strip())
    if purpose_lines:
        for line in purpose_lines:
            if line and line.strip():
                body.append(line.strip())
    if not body:
        return ""
    return "[PURPOSE]\n" + "\n".join(body)


def with_item_behavior(tooltip: str | None, behavior_lines: list[str] | None) -> str:
    parts: list[str] = []
    if tooltip and tooltip.strip():
        parts.append(tooltip.strip())
    if behavior_lines:
        for line in behavior_lines:
            if line and line.strip():
                parts.append(line.strip())
    return "\n".join(parts)


def format_fuel_line(burn_ticks: int) -> str:
    if burn_ticks <= 0:
        return ""
    seconds = burn_ticks // 20
    return f"Furnace fuel: {burn_ticks} ticks (~{seconds}s)"


def format_saturation(saturation: float) -> str:
    s = f"{saturation:.3f}"
    s = s.rstrip("0").rstrip(".")
    return s if s else "0"


def format_food_use_line(
    use_kind: str | None,
    nutrition: int,
    saturation: float,
    always_edible: bool,
    fast_eat: bool,
    effect_specs: list[str] | None,
    max_effects: int = 8,
) -> str:
    specs = [s.strip() for s in (effect_specs or []) if s and s.strip()]
    has_stats = nutrition > 0 or saturation > 0 or always_edible or fast_eat or bool(specs)
    kind = (use_kind or "").strip().lower()
    if not has_stats and not kind:
        return ""
    if not has_stats:
        if kind == "drink":
            return "Drinkable (hold right-click to drink)"
        if kind == "eat":
            return "Edible (hold right-click to eat)"
        return "Consumable (hold right-click)"
    verb = "Drinkable" if kind == "drink" else "Edible"
    parts = [f"{verb} food: nutrition {nutrition}, saturation {format_saturation(saturation)}"]
    if always_edible:
        parts.append("always edible")
    if fast_eat:
        parts.append("fast eat")
    line = parts[0]
    if len(parts) > 1:
        line = parts[0] + "; " + "; ".join(parts[1:])
    if specs:
        truncated = len(specs) > max_effects
        if truncated:
            specs = specs[:max_effects]
        line += "; effects: " + ", ".join(specs)
        if truncated:
            line += ", …"
    return line


def format_food_effects_gap_line() -> str:
    return (
        "Effects not in FoodProperties; check item tooltip / quest book / mod docs"
        " (custom finishUsing not readable at Ask-time)"
    )


def format_tool_actions_line(action_names: list[str] | None, max_actions: int = 16) -> str:
    if not action_names:
        return ""
    sorted_names = sorted(n.strip() for n in action_names if n and n.strip())
    if not sorted_names:
        return ""
    truncated = len(sorted_names) > max_actions
    if truncated:
        sorted_names = sorted_names[:max_actions]
    joined = ", ".join(sorted_names)
    if truncated:
        joined = joined + ", …"
    return "Tool actions: " + joined


def main() -> None:
    assert is_purpose_graph_fact("item:x -[right_click_use]-> held:y block:z")
    assert is_purpose_graph_fact("item:x -[desc]-> teleport stone")
    assert not is_purpose_graph_fact("item:x -[recipe_needs]-> item:y")
    assert not is_purpose_graph_fact("item:x -[loot]-> chest")

    block = build_purpose_block(
        "Cursed Ingot\nRitual material",
        ["Right-click dirt with stick → diamond"],
    )
    assert block.startswith("[PURPOSE]\n")
    assert "Ritual material" in block
    assert "Right-click" in block
    assert build_purpose_block("", []) == ""
    assert build_purpose_block(None, None) == ""

    assert format_fuel_line(0) == ""
    assert format_fuel_line(-1) == ""
    fuel = format_fuel_line(1600)
    assert fuel == "Furnace fuel: 1600 ticks (~80s)"
    assert format_tool_actions_line([]) == ""
    assert format_tool_actions_line(["shovel_dig", "axe_dig"]) == "Tool actions: axe_dig, shovel_dig"
    many = [f"a{i:02d}" for i in range(20)]
    tools = format_tool_actions_line(many)
    assert tools.startswith("Tool actions: ")
    assert tools.endswith(", …")
    assert tools.count(",") == 16  # 15 between 16 names + trailing ellipsis comma

    assert format_food_use_line(None, 0, 0.0, False, False, None) == ""
    assert format_food_use_line("drink", 0, 0.0, False, False, []) == (
        "Drinkable (hold right-click to drink)"
    )
    gap = format_food_effects_gap_line()
    assert "Effects not in FoodProperties" in gap
    assert "tooltip" in gap
    assert "quest book" in gap
    food = format_food_use_line(
        "drink",
        0,
        0.1,
        True,
        False,
        ["mod:soul@0 200t (100%)", "mod:magic@1 100t (50%)"],
    )
    assert food.startswith("Drinkable food: nutrition 0, saturation 0.1")
    assert "always edible" in food
    assert "mod:soul@0 200t (100%)" in food
    assert "mod:magic@1 100t (50%)" in food
    many_fx = [f"e{i}" for i in range(10)]
    fx = format_food_use_line("eat", 4, 0.3, False, True, many_fx)
    assert fx.startswith("Edible food:")
    assert "fast eat" in fx
    assert fx.endswith(", …")

    merged = with_item_behavior("Coal", [fuel, format_tool_actions_line(["axe_dig"])])
    assert "Coal" in merged
    assert "Furnace fuel: 1600" in merged
    assert "axe_dig" in merged
    purpose = build_purpose_block(merged, [])
    assert purpose.startswith("[PURPOSE]\n")
    assert "Furnace fuel" in purpose
    milk = with_item_behavior("Miracle Milk", [food])
    assert "Drinkable food" in build_purpose_block(milk, [])

    print("check_ask_purpose_context OK")


if __name__ == "__main__":
    main()
