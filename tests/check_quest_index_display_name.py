#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static asserts: quest index gate matches display names, not only registry ids."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GATE = (
    ROOT
    / "forge"
    / "1.19.2"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "AskToolOfferGate.java"
)


def main() -> None:
    text = GATE.read_text(encoding="utf-8")

    # 1. empty quest index → unknown (null), not proven zero
    assert "questAll.isEmpty()" in text
    hit_block = text.split("Boolean hasQuestHit", 1)[1].split("private static boolean questIndexMentions", 1)[0]
    assert "return null" in hit_block
    assert "questAll == null || questAll.isEmpty()" in text or (
        "questAll == null" in hit_block and "questAll.isEmpty()" in hit_block
    )

    # 2. display-name match via hover / registry
    mentions = text.split("private static boolean questIndexMentions", 1)[1].split(
        "private static boolean containsFullId", 1
    )[0]
    assert "getHoverName" in text or "displayName" in mentions or "getHoverName" in mentions

    # 3. short display names must not false-positive
    assert "length() >= 4" in text

    # 4. no hardcoded item display strings in this gate
    assert '"Oxygen Tank"' not in text
    forbidden = ('"oxygen tank"', '"Oxygen Tank"')
    for bad in forbidden:
        assert bad not in text, bad

    # 5. quest cache key includes lang
    assert 'id + "\\0" + lang' in text or "id + \"\\0\" + lang" in text
    assert "questCache.get(cacheKey)" in text
    assert "questCache.put(cacheKey" in text

    print("check_quest_index_display_name OK")


if __name__ == "__main__":
    main()
