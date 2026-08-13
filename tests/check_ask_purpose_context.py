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
            "-[script_use]->",
            "-[consume_item]->",
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


def index_of_header_line(text: str, header: str) -> int:
    if not text or not header:
        return -1
    if text.startswith(header):
        return 0
    i = text.find("\n" + header)
    return -1 if i < 0 else i + 1


def split_get_and_as_ingredient(jei: str | None, catalyst_title: str = "") -> tuple[str, str]:
    """Mirror AskPurposeContext.splitGetAndAsIngredient."""
    if not jei or not jei.strip():
        return "", ""
    u = index_of_header_line(jei, "[AS_INGREDIENT]")
    if u < 0:
        return jei.strip(), ""
    before = jei[:u].rstrip()
    from_u = jei[u:]
    cut = -1
    if catalyst_title and catalyst_title.strip():
        cut = index_of_header_line(from_u, catalyst_title.strip())
    if cut > 0:
        uses = from_u[:cut].rstrip()
        after = from_u[cut:].strip()
    else:
        uses = from_u.strip()
        after = ""
    get = before
    if after:
        get = after if not get else get + "\n" + after
    return get.strip(), uses


def has_obtain_recipe_body(get_body: str | None) -> bool:
    """Mirror AskPurposeContext.hasObtainRecipeBody — chrome / INPUT-only ≠ obtain."""
    if not get_body or not get_body.strip():
        return False
    for line in get_body.split("\n"):
        t = line.strip()
        if not t:
            continue
        if t.startswith("- "):
            return True
        if "role=output" in t or "role=quest" in t:
            return True
        if " → " in t or " -> " in t or "→" in t:
            return True
    return False


def purpose_ask_fact_order(
    purpose_block: str,
    as_ingredient: str,
    get_body: str,
    order: str = "purpose_first",
) -> str:
    """Mirror AskEngine purpose-branch block order (askPurposeOrder)."""
    parts: list[str] = []
    use = ("## How to use\n" + purpose_block.strip()) if purpose_block and purpose_block.strip() else ""
    ing = as_ingredient.strip() if as_ingredient and as_ingredient.strip() else ""
    get = ("## How to get\n" + get_body.strip()) if has_obtain_recipe_body(get_body) else ""
    if order == "ingredient_first":
        for p in (ing, get, use):
            if p:
                parts.append(p)
    else:
        for p in (use, ing, get):
            if p:
                parts.append(p)
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
    assert is_purpose_graph_fact("item:x -[consume_item]-> unlock:research")
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

    # JEI dump split: recipes stay in get; AS_INGREDIENT peeled for purpose order
    jei = (
        "JEI for Test\n"
        "配方（如何製作，等同 JEI 按 R）\n"
        "Crafting → stick\n"
        "[AS_INGREDIENT] 作為材料（JEI 按 U）—不是功能用途／怎麼用\n"
        "Crafting → sword\n"
        "作為機器／工作站的配方（JEI 催化劑；特殊合成多在此）\n"
        "Smelting → charcoal\n"
        "useful=3"
    )
    get, as_ing = split_get_and_as_ingredient(
        jei, "作為機器／工作站的配方（JEI 催化劑；特殊合成多在此）"
    )
    assert "[AS_INGREDIENT]" not in get
    assert "Crafting → stick" in get
    assert "Smelting → charcoal" in get
    assert as_ing.startswith("[AS_INGREDIENT]")
    assert "Crafting → sword" in as_ing
    assert "Smelting" not in as_ing

    purpose_with_consume = (
        "[GUIDE]\nright-click unlock\n"
        "[PURPOSE]\nForbidden Scroll\n[CONSUME_USE] unlock research"
    )
    ordered = purpose_ask_fact_order(purpose_with_consume, as_ing, get)
    assert ordered.index("[PURPOSE]") < ordered.index("[AS_INGREDIENT]")
    assert ordered.index("[CONSUME_USE]") < ordered.index("[AS_INGREDIENT]")
    assert ordered.index("[GUIDE]") < ordered.index("[AS_INGREDIENT]")
    assert ordered.index("## How to use") < ordered.index("## How to get")
    assert ordered.index("[AS_INGREDIENT]") < ordered.index("## How to get")

    ordered_ing = purpose_ask_fact_order(purpose_with_consume, as_ing, get, "ingredient_first")
    assert ordered_ing.index("[AS_INGREDIENT]") < ordered_ing.index("[PURPOSE]")
    assert ordered_ing.index("## How to get") < ordered_ing.index("## How to use")

    empty_get, empty_u = split_get_and_as_ingredient("only recipes\nCrafting → a", "")
    assert empty_u == ""
    assert "only recipes" in empty_get

    chrome = (
        "【JEI 资料】物品x [golden_age:landscape_realm_scroll]\n"
        "【JEI】有配方卡（序列组装）。优先合成路径，勿宣称无法合成或仅掉落。\n"
        "• [Quests] 略过 2 笔（通用）"
    )
    assert not has_obtain_recipe_body(chrome)
    assert has_obtain_recipe_body("Crafting\n  - stick → planks")
    assert has_obtain_recipe_body("0 | role=output | Crafting | oak → planks")
    assert not has_obtain_recipe_body("0 | role=input | Sequenced Assembly | board")
    no_get = purpose_ask_fact_order("[PURPOSE]\nscroll", "[AS_INGREDIENT]\nseq", chrome)
    assert "## How to get" not in no_get
    assert "[AS_INGREDIENT]" in no_get

    print("check_ask_purpose_context OK")


if __name__ == "__main__":
    main()
