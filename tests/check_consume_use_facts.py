# -*- coding: utf-8 -*-
"""WP: consume_item advancement → PURPOSE [CONSUME_USE]; guidebook lock policy text."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for side in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        facts = read(f"{side}/logic/ItemConsumeUseFacts.java")
        assert "CONSUME_USE" in facts
        assert "minecraft:consume_item" in facts
        assert "Right-click / consume" in facts
        purpose = read(f"{side}/logic/AskPurposeContext.java")
        assert "-[consume_item]->" in purpose
        ask = read(f"{side}/client/service/AskService.java")
        assert "ItemConsumeUseFacts.purposeLinesFor" in ask
        scan = read(f"{side}/logic/PatchouliEntryScan.java")
        # Guidebook index does NOT gate on player unlock / advancement flag.
        assert "hasAdvancement" not in scan
        assert "isLocked" not in scan
        # Indexer reads JSON pages only — no player unlock filter API.
        assert "PlayerUnlock" not in scan
        assert "SEHelper" not in scan
        scrub = read(f"{side}/logic/AskReplyScrub.java")
        assert "CONSUME_USE" in scrub

    for side in (
        "forge/1.19.2/src/main/resources/assets/packai/lang",
        "neoforge/1.21.1/src/main/resources/assets/packai/lang",
    ):
        en = read(f"{side}/en_us.json")
        assert "[CONSUME_USE]" in en
        assert "Patchouli entry JSON" in en
        zh = read(f"{side}/zh_cn.json")
        assert "[CONSUME_USE]" in zh
        assert "手册索引" in zh or "手冊索引" in zh

    # Real Goety advancement shape (fixture mirrors jar)
    goety_shape = '''
    "trigger": "minecraft:consume_item"
    "goety:forbidden_scroll"
    '''
    assert "consume_item" in goety_shape

    print("check_consume_use_facts OK")


if __name__ == "__main__":
    main()
