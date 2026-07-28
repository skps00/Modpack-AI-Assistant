#!/usr/bin/env python3
"""Mirrors AskPurposeContext — purpose vs JEI-U ingredient + fuel/tool lines."""


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

    merged = with_item_behavior("Coal", [fuel, format_tool_actions_line(["axe_dig"])])
    assert "Coal" in merged
    assert "Furnace fuel: 1600" in merged
    assert "axe_dig" in merged
    purpose = build_purpose_block(merged, [])
    assert purpose.startswith("[PURPOSE]\n")
    assert "Furnace fuel" in purpose

    print("check_ask_purpose_context OK")


if __name__ == "__main__":
    main()
