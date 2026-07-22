#!/usr/bin/env python3
"""CraftPriority uses generic title keywords only (no mod brands)."""


TITLE_TIERS = [
    ["crafting table", "crafting", "工作台", "合成"],
    ["stonecut", "切石"],
    ["smelt", "furnace", "blast", "熔爐", "高爐"],
    ["campfire", "smoker", "煙燻", "營火"],
    ["compost", "堆肥"],
    ["processing", "machine", "加工", "機器", "工作站"],
]

QUEST_KEYS = ["quest", "任務", "reward table", "獎勵表", "任務獎勵", "quest reward"]


def tier(title: str) -> int:
    t = title.lower()
    if any(k in t for k in QUEST_KEYS):
        return 90  # craft prefer
    for i, keys in enumerate(TITLE_TIERS):
        if any(k in t for k in keys):
            return i
    return 30


def main() -> None:
    assert tier("Crafting Table") < tier("Automatic Stirrer")
    assert tier("Crafting Table") < tier("Some Machine Processing")
    assert tier("Create Mixing") == tier("Mekanism Crusher") == 30
    assert "create" not in " ".join(k for row in TITLE_TIERS for k in row)
    assert "mekanism" not in " ".join(k for row in TITLE_TIERS for k in row)
    print("ok craft_priority_generic")


if __name__ == "__main__":
    main()
