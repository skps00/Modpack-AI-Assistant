#!/usr/bin/env python3
"""preferObtain pathway ranking — craft | quest | loot | balanced."""


def quest_tier(prefer: str) -> int:
    return {"quest": -5, "loot": 40, "balanced": 35, "craft": 90}.get(prefer, 90)


def loot_tier(prefer: str) -> int:
    return {"loot": -3, "quest": 25, "balanced": 5, "craft": 8}.get(prefer, 8)


def main() -> None:
    assert quest_tier("craft") > quest_tier("quest")
    assert quest_tier("quest") < 0
    assert loot_tier("craft") < quest_tier("craft")
    assert loot_tier("loot") < quest_tier("loot")
    assert loot_tier("balanced") < quest_tier("balanced")
    print("ok prefer_obtain")


if __name__ == "__main__":
    main()
