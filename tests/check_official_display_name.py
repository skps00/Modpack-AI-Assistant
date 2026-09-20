#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static asserts: official display-name rule + facts annotate/peers (Forge-only)."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE_JAVA = ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai"
LANG = ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    od = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/OfficialDisplay.java")
    ask = read("forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java")
    reply = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/ReplyLang.java")
    harness = read(
        "forge/1.19.2/src/test/java/com/skps9/packai/logic/AskDisplayNameCheck.java"
    )

    assert "class OfficialDisplay" in od
    assert "enrichFacts" in od
    assert "PEER_HEADER" in od
    assert "NO_OFFICIAL" in od
    assert "hoverLookup" in od
    # must NOT fall back to path-token invent (Plainify.displayName)
    assert "Plainify.displayName" not in od

    assert "OfficialDisplay.enrichFacts" in ask
    assert "officialNameRule" in reply
    assert "packai.reply.official_name_rule" in reply

    assert "class AskDisplayNameCheck" in harness
    assert "暗鋼閃電" in harness or "暗钢闪电" in harness
    assert "龙霆钢开胸器" in harness
    assert "chestopener_dsteellightning" in harness

    for lang in ("zh_cn.json", "zh_tw.json", "en_us.json"):
        text = (LANG / lang).read_text(encoding="utf-8")
        assert "packai.reply.official_name_rule" in text, lang
        assert "chestopener_dsteellightning" in text, lang
        assert "暗鋼閃電" in text or "暗钢闪电" in text or "Dark Steel Lightning" in text, lang

    # negative control: junk file must NOT satisfy the product asserts
    junk = "class Fake {}\n"
    assert "OfficialDisplay.enrichFacts" not in junk

    print("check_official_display_name OK")


if __name__ == "__main__":
    main()
