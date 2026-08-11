#!/usr/bin/env python3
"""CraftPriority askEaseBand / loot / quest ordering (generic title keywords)."""

TITLE_TIERS = [
    ["crafting table", "crafting", "工作台", "合成"],
    ["stonecut", "切石"],
    ["smelt", "furnace", "blast", "熔爐", "高爐"],
    ["campfire", "smoker", "煙燻", "營火"],
    ["compost", "堆肥"],
    ["processing", "machine", "加工", "機器", "工作站"],
]

QUEST_KEYS = ["quest", "任務", "reward table", "獎勵表", "任務獎勵", "quest reward"]
LOOT_KEYS = ["loot", "chest", "treasure", "戰利", "战利", "寶箱", "宝箱", "掉落", "loot table"]


def norm(s: str) -> str:
    return (s or "").lower()


def is_quest(title: str) -> bool:
    t = norm(title)
    return any(k in t for k in QUEST_KEYS)


def is_loot(title: str) -> bool:
    t = norm(title)
    if not t or is_quest(t):
        return False
    return any(k in t for k in LOOT_KEYS)


def is_core_craft(title: str) -> bool:
    t = norm(title)
    if not t or is_quest(t) or is_loot(t):
        return False
    for i, keys in enumerate(TITLE_TIERS):
        if i > 3:
            break
        if any(k in t for k in keys):
            return True
    return False


def ask_ease_band(title: str, prefer: str = "craft") -> int:
    t = norm(title)
    if prefer == "quest":
        if is_quest(t):
            return 0
        if is_core_craft(t):
            return 1
        if is_loot(t):
            return 2
        return 3
    if prefer == "loot":
        if is_loot(t):
            return 0
        if is_core_craft(t):
            return 1
        if is_quest(t):
            return 3
        return 2
    if is_core_craft(t):
        return 0
    if is_loot(t):
        return 1
    if is_quest(t):
        return 3
    return 2


def tier_craft_prefer(title: str) -> int:
    t = norm(title)
    if is_quest(t):
        return 90
    if is_loot(t):
        return 8
    for i, keys in enumerate(TITLE_TIERS):
        if any(k in t for k in keys):
            return i
    return 30


def main() -> None:
    assert ask_ease_band("Crafting") < ask_ease_band("Quests · 任务书")
    assert ask_ease_band("Chest Loot") < ask_ease_band("Quests")
    assert ask_ease_band("Crafting") < ask_ease_band("Chest Loot")
    assert ask_ease_band("Loot Table", "loot") < ask_ease_band("Crafting", "loot")
    assert ask_ease_band("Quests", "quest") < ask_ease_band("Crafting", "quest")
    assert tier_craft_prefer("Chest Loot") < tier_craft_prefer("Quests")
    assert tier_craft_prefer("Crafting Table") < tier_craft_prefer("Chest Loot")
    assert "create" not in " ".join(k for row in TITLE_TIERS for k in row)

    # AskEngine: acquire before jei for craft/balanced/purpose (loot not buried by Quests JEI).
    from pathlib import Path

    root = Path(__file__).resolve().parents[1]
    for rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/AskEngine.java",
    ):
        text = (root / rel).read_text(encoding="utf-8")
        assert "blocks.add(acquireLines);\n                        blocks.add(jeiLines);" in text, rel
        purpose = text.split("if (purpose || machineAsk || hasMachine)", 1)[1].split("} else {", 1)[0]
        assert purpose.index("blocks.add(acquireLines)") < purpose.index("blocks.add(jeiLines)"), rel
        assert purpose.index("blocks.add(acquireLines)") < purpose.index("blocks.add(questFactLines)"), rel

    for rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/jei/JeiLookup.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/jei/JeiLookup.java",
    ):
        text = (root / rel).read_text(encoding="utf-8")
        assert "isQuestCategory(catTitle)" in text
        assert '!"quest".equals(PackAiConfig.preferObtain())' in text

    print("ok ask_ease_order")


if __name__ == "__main__":
    main()
