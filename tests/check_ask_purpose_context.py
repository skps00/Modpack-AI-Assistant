#!/usr/bin/env python3
"""Mirrors AskPurposeContext — purpose vs JEI-U ingredient."""


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
    print("check_ask_purpose_context OK")


if __name__ == "__main__":
    main()
