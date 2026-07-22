#!/usr/bin/env python3
"""Recipe category hide + custom order sort keys."""


def sort_key(uid: str, title_tier: int, order: list[str]) -> int:
    if order:
        try:
            return order.index(uid)
        except ValueError:
            return 10_000 + title_tier
    return title_tier


def visible(uids: list[str], hidden: set[str]) -> list[str]:
    return [u for u in uids if u not in hidden]


def main() -> None:
    order = ["minecraft:crafting", "minecraft:smelting", "quest:reward"]
    assert sort_key("minecraft:crafting", 0, order) == 0
    assert sort_key("quest:reward", 90, order) == 2
    assert sort_key("create:mixing", 10, order) == 10_010
    assert sort_key("minecraft:crafting", 0, []) == 0

    cats = ["a", "b", "c"]
    assert visible(cats, {"b"}) == ["a", "c"]
    assert visible(cats, set()) == cats
    print("ok recipe_category_prefs")


if __name__ == "__main__":
    main()
