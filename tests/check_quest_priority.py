#!/usr/bin/env python3
"""preferObtain pathway ranking — craft | quest | loot | balanced."""


def quest_tier(prefer: str) -> int:
    return {"quest": -5, "loot": 30, "balanced": 30, "craft": 90}.get(prefer, 90)


def main() -> None:
    assert quest_tier("craft") > quest_tier("quest")
    assert quest_tier("quest") < 0
    assert quest_tier("loot") == 30
    print("ok prefer_obtain")


if __name__ == "__main__":
    main()
