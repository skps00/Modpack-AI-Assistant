#!/usr/bin/env python3
"""Mirror AskReplyScrub.stripDuplicateSectionHeaders — drop duplicate section title lines."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRUB_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "AskReplyScrub.java",
    ROOT
    / "neoforge"
    / "1.21.1"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "AskReplyScrub.java",
)
SERVICE_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "client" / "service" / "AskService.java",
    ROOT
    / "neoforge"
    / "1.21.1"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "client"
    / "service"
    / "AskService.java",
)

PURE_SECTION_HEADER = re.compile(
    r"^[ \t]*(?:\d+[.)][ \t]*)?"
    r"(怎么来|怎样来|怎么來|怎樣來|怎麼来|怎麼來|怎么用|怎麼用|用途|作为材料|作為材料|How to get|How to use|Usage)"
    r"[ \t]*[:：]?[ \t]*$",
    re.IGNORECASE | re.MULTILINE,
)


def canonical_section_key(label: str) -> str:
    t = label.strip()
    lower = t.lower()
    if t in ("怎么来", "怎样来", "怎么來", "怎樣來", "怎麼来", "怎麼來") or lower == "how to get":
        return "how_to_get"
    if t in ("怎么用", "怎麼用") or lower in ("how to use", "usage"):
        return "how_to_use"
    if t == "用途":
        return "purpose"
    if t in ("作为材料", "作為材料"):
        return "as_material"
    return lower


def strip_duplicate_section_headers(reply: str | None) -> str:
    if reply is None or not reply:
        return "" if reply is None else reply
    seen: set[str] = set()
    kept: list[str] = []
    for line in re.split(r"\r?\n", reply):
        m = PURE_SECTION_HEADER.match(line)
        if m:
            key = canonical_section_key(m.group(1))
            if key in seen:
                continue
            seen.add(key)
        kept.append(line)
    return "\n".join(kept)


def test_behavior() -> None:
    dup = (
        "怎样来:\n"
        "1. 工作台: 合成。\n"
        "[[recipe_card:0]]\n"
        "2. 直接使用: 右键。\n"
        "1. 怎么来 :\n"
        "3. 作为材料（召唤祭坛）: 献祭。"
    )
    out = strip_duplicate_section_headers(dup)
    assert out.count("怎样来:") == 1
    assert "1. 怎么来 :" not in out
    assert "3. 作为材料（召唤祭坛）" in out
    assert "[[recipe_card:0]]" in out
    assert "2. 直接使用" in out

    twice = "怎么来:\n步骤一。\n怎么来:\n步骤二。"
    twice_out = strip_duplicate_section_headers(twice)
    assert twice_out.count("怎么来:") == 1
    assert "步骤一。" in twice_out and "步骤二。" in twice_out

    mixed = (
        "怎样来:\n"
        "1. 工作台:\n"
        "铁锭。\n"
        "1. 怎么来 :\n"
        "3. 作为材料:\n"
        "献祭。"
    )
    mixed_out = strip_duplicate_section_headers(mixed)
    assert "1. 怎么来 :" not in mixed_out
    assert "3. 作为材料:" in mixed_out
    assert "铁锭。" in mixed_out

    prose = "如果不知道怎么来，可以查 JEI。\n怎么来:\n箱子掉落。"
    prose_out = strip_duplicate_section_headers(prose)
    assert prose_out == prose

    distinct = "怎么用:\n手持。\n作为材料:\n合成。\n用途:\n装饰。"
    distinct_out = strip_duplicate_section_headers(distinct)
    assert distinct_out == distinct

    en = "How to get:\nloot\nHow to get:\nmore"
    en_out = strip_duplicate_section_headers(en)
    assert en_out.count("How to get:") == 1

    # English prose after a label must survive (label + space is not a pure header).
    en_prose = "Usage in combat is limited to tools.\nUsage:\nswing it"
    en_prose_out = strip_duplicate_section_headers(en_prose)
    assert "Usage in combat is limited to tools." in en_prose_out
    assert en_prose_out.count("Usage:") == 1

    en_prose_only = "Usage in combat is limited to tools."
    assert strip_duplicate_section_headers(en_prose_only) == en_prose_only

    assert strip_duplicate_section_headers("plain text\nno headers") == "plain text\nno headers"
    assert strip_duplicate_section_headers("") == ""
    assert strip_duplicate_section_headers(None) == ""


def check_source(path: Path) -> None:
    src = path.read_text(encoding="utf-8")
    assert "stripDuplicateSectionHeaders" in src, f"{path}: missing stripDuplicateSectionHeaders"
    assert "PURE_SECTION_HEADER" in src, f"{path}: missing header pattern"
    assert "canonicalSectionKey" in src, f"{path}: missing canonicalSectionKey"


def check_wiring(path: Path) -> None:
    src = path.read_text(encoding="utf-8")
    assert src.count("AskReplyScrub.stripDuplicateSectionHeaders") >= 2, f"{path}: wire both ask paths"
    idx = src.find("AskReplyScrub.stripDuplicateSectionHeaders")
    cards = src.find("AskCardFallback.ensureCards", idx)
    assert cards > idx, f"{path}: stripDuplicateSectionHeaders must run before ensureCards"


def main() -> None:
    test_behavior()
    for p in SCRUB_PATHS:
        assert p.is_file(), f"missing {p}"
        check_source(p)
    for p in SERVICE_PATHS:
        assert p.is_file(), f"missing {p}"
        check_wiring(p)
    print("check_reply_structure_scrub OK")


if __name__ == "__main__":
    main()
