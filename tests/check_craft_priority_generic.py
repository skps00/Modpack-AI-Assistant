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

QUEST_KEYS = ["quest", "任務", "任务", "reward table", "獎勵表", "任务奖励", "任務獎勵", "quest reward"]
LOOT_KEYS = ["loot", "chest", "treasure", "戰利", "战利", "寶箱", "宝箱", "掉落", "loot table"]
MACHINE_LIKE_KEYS = ["自動", "自动", "動力", "合成器", "機器", "机器", "機", "机", "machine", "auto", "工作站"]
CRAFTING_TABLE_KEYS = ["crafting table", "工作台"]


def is_machine_like(title: str) -> bool:
    t = title.lower()
    if any(k in t for k in CRAFTING_TABLE_KEYS):
        return False
    return any(k in t for k in MACHINE_LIKE_KEYS)


def is_core_craft(title: str) -> bool:
    t = title.lower()
    if not t:
        return False
    if any(k in t for k in QUEST_KEYS):
        return False
    if any(k in t for k in LOOT_KEYS):
        return False
    for i, keys in enumerate(TITLE_TIERS):
        if i > 3:
            break
        if any(k in t for k in keys):
            return not is_machine_like(t)
    return False


def tier(title: str) -> int:
    t = title.lower()
    if any(k in t for k in QUEST_KEYS):
        return 90  # craft prefer
    if any(k in t for k in LOOT_KEYS):
        return 8
    for i, keys in enumerate(TITLE_TIERS):
        if any(k in t for k in keys):
            return i
    return 30


def main() -> None:
    assert is_core_craft("Crafting")
    assert is_core_craft("工作台")
    assert is_core_craft("合成")
    assert not is_core_craft("自動合成 · 動力合成器")
    assert tier("Crafting Table") < tier("Automatic Stirrer")
    assert tier("Crafting Table") < tier("Some Machine Processing")
    assert tier("Create Mixing") == tier("Mekanism Crusher") == 30
    assert tier("Chest Loot") < tier("Quests")
    assert tier("Crafting Table") < tier("Chest Loot")
    assert "create" not in " ".join(k for row in TITLE_TIERS for k in row)
    assert "mekanism" not in " ".join(k for row in TITLE_TIERS for k in row)
    from pathlib import Path
    root = Path(__file__).resolve().parents[1]
    for rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/CraftPriority.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/CraftPriority.java",
    ):
        text = (root / rel).read_text(encoding="utf-8")
        assert '"任务"' in text, rel
        assert '"任务奖励"' in text, rel
    print("ok craft_priority_generic")


if __name__ == "__main__":
    main()
