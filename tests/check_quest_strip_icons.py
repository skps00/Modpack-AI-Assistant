"""Quest icon fields must not count as task/reward item ids for Ask matching."""

from __future__ import annotations

import re

_ITEM = re.compile(r"\b([a-z0-9_]+:[a-z0-9_./-]+)\b", re.I)
_ICON = re.compile(r"\bicon\s*:", re.I)


def strip_quest_icons(text: str) -> str:
    """Mirror QuestGuide.stripQuestIcons (string + compound object forms)."""
    if not text:
        return text or ""
    out: list[str] = []
    last = 0
    for m in _ICON.finditer(text):
        out.append(text[last : m.start()])
        i = m.end()
        while i < len(text) and text[i].isspace():
            i += 1
        if i < len(text) and text[i] == '"':
            i += 1
            esc = False
            while i < len(text):
                c = text[i]
                i += 1
                if esc:
                    esc = False
                elif c == "\\":
                    esc = True
                elif c == '"':
                    break
        elif i < len(text) and text[i] == "{":
            depth = 0
            in_str = False
            esc = False
            while i < len(text):
                c = text[i]
                if in_str:
                    if esc:
                        esc = False
                    elif c == "\\":
                        esc = True
                    elif c == '"':
                        in_str = False
                    i += 1
                    continue
                if c == '"':
                    in_str = True
                elif c == "{":
                    depth += 1
                elif c == "}":
                    depth -= 1
                    i += 1
                    if depth == 0:
                        break
                    continue
                i += 1
        last = i
    out.append(text[last:])
    return "".join(out)


def items_in_range(text: str) -> set[str]:
    cleaned = strip_quest_icons(text)
    return {m.group(1).lower() for m in _ITEM.finditer(cleaned)}


def main() -> None:
    # NFWC tetra_2: decorative Create wrench icon, precision_mechanism task
    snbt = """
			icon: "create:wrench"
			rewards: [{
				item: "lightmanscurrency:coin_gold"
				Count: 1
			}]
			tasks: [{
				id: "xxx"
				item: "create:precision_mechanism"
				type: "item"
			}]
			title: "压力发条扳手"
"""
    items = items_in_range(snbt)
    assert "create:wrench" not in items, items
    assert "create:precision_mechanism" in items, items
    assert "lightmanscurrency:coin_gold" in items, items

    compound = 'icon: { Count: 1b, id: "create:wrench" }\nitem: "minecraft:stick"'
    items2 = items_in_range(compound)
    assert "create:wrench" not in items2, items2
    assert "minecraft:stick" in items2, items2

    print("check_quest_strip_icons: OK")


if __name__ == "__main__":
    main()
