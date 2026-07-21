#!/usr/bin/env python3
"""Self-check for quest matching, override, multi-hit list."""

from pathlib import Path
import tempfile

from quests import (
    detect_quest_conflict,
    ensure_quest_index,
    format_quest_guide,
    is_quest_override,
    match_quests,
)


def main() -> None:
    assert is_quest_override("任務書好像不對", {"questOverride": False})
    assert is_quest_override("x", {"questOverride": True})
    assert not is_quest_override("我卡住了看不懂下一步", {})
    assert not is_quest_override("任務做不到要怎麼做", {})
    assert is_quest_override("任務做不到而且任務錯了", {})

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        chap = root / "config" / "ftbquests" / "chapters" / "intro"
        chap.mkdir(parents=True)
        (chap / "quest1.snbt").write_text(
            'title: "Make Alloy"\ndescription: "Craft create:andesite_alloy"\nid: "create:andesite_alloy"\n',
            encoding="utf-8",
        )
        (chap / "quest2.snbt").write_text(
            'title: "Iron Age"\ndescription: "Get iron"\nminecraft:iron_ingot\n',
            encoding="utf-8",
        )
        (chap / "quest3.snbt").write_text(
            'title: "Alloy Again"\ndescription: "More create:andesite_alloy"\n',
            encoding="utf-8",
        )
        (chap / "quest4.snbt").write_text(
            'title: "Alloy Extra"\ndescription: "create:andesite_alloy bonus"\n',
            encoding="utf-8",
        )

        idx = ensure_quest_index(root, ["ftbquests"], "t1")
        hits = match_quests(
            "how to make alloy",
            {"id": "create:andesite_alloy"},
            idx,
        )
        assert 1 <= len(hits) <= 3, hits
        assert all("alloy" in h.title.lower() or "alloy" in h.description.lower() or "andesite" in str(h.items) for h in hits)

        guide = format_quest_guide(hits, conflict=False, total_matched=4)
        assert "任務導引" in guide and "章節" in guide
        assert "還有其他相關任務" in guide

        assert detect_quest_conflict(hits, {"create:andesite_alloy"})
        assert not detect_quest_conflict(hits, {"minecraft:dirt"})

    print("ok quests", [h.title for h in hits])


if __name__ == "__main__":
    main()
