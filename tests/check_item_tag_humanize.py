#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static gate: player-facing item tags must carry a tag word; bare #ns:path banned.

Negative control: strip the fix from source text ⇒ assertions must fail (RC≠0 path).
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge" / "1.19.2"
HINTS = (
    FORGE
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "client"
    / "jei"
    / "IngredientReqHints.java"
)
SCRUB = (
    FORGE
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "AskReplyScrub.java"
)
REPLY_LANG = (
    FORGE
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "ReplyLang.java"
)
LANG_DIR = FORGE / "src" / "main" / "resources" / "assets" / "packai" / "lang"
LANGS = ("en_us.json", "zh_cn.json", "zh_tw.json")
KEY = "packai.reply.item_tag_paren"
EXPECTED = {
    "en_us.json": "tag %s",
    "zh_cn.json": "标签 %s",
    "zh_tw.json": "標籤 %s",
}
BARE_TAG_RE = re.compile(r"#[a-z0-9_.-]+:[a-z0-9_./-]+")
TAG_WORD_RE = re.compile(r"(?:tag|标签|標籤)\s+#[a-z0-9_.-]+:[a-z0-9_./-]+")


def fail(msg: str) -> None:
    print(f"FAIL: {msg}")
    raise SystemExit(1)


def check_lang() -> None:
    for name in LANGS:
        path = LANG_DIR / name
        data = json.loads(path.read_text(encoding="utf-8"))
        if KEY not in data:
            fail(f"missing {KEY} in {name}")
        if data[KEY] != EXPECTED[name]:
            fail(f"{name} {KEY}={data[KEY]!r} want {EXPECTED[name]!r}")


def check_reply_lang(src: str) -> None:
    if "itemTagParen" not in src:
        fail("ReplyLang missing itemTagParen")
    if KEY not in src:
        fail(f"ReplyLang must use {KEY}")


def check_hints(src: str) -> None:
    if "ReplyLang.itemTagParen" not in src:
        fail("IngredientReqHints.labelForAlternatives must call ReplyLang.itemTagParen")
    # Old bare form: name + "（" + tag + "）" without itemTagParen on that branch
    m = re.search(
        r"String tag = commonTagId\(clean\);\s*"
        r"if \(tag != null && !tag\.isEmpty\(\)\) \{\s*"
        r"return name \+ \"（\" \+ tag \+ \"）\";",
        src,
        re.S,
    )
    if m:
        fail("IngredientReqHints still returns bare tag without tag word")


def check_scrub(src: str) -> None:
    if "BARE_ITEM_TAG" not in src:
        fail("AskReplyScrub missing BARE_ITEM_TAG")
    if "humanizeBareItemTags" not in src:
        fail("AskReplyScrub missing humanizeBareItemTags")
    if "body = humanizeBareItemTags(body);" not in src:
        fail("scrubPromptEcho must call humanizeBareItemTags in final pass")
    # Must NOT widen RAW_ID_SHAPE itself for tags
    raw = re.search(
        r"private static final Pattern RAW_ID_SHAPE = Pattern\.compile\(\s*\"([^\"]+)\"\s*\);",
        src,
    )
    if not raw:
        fail("RAW_ID_SHAPE pattern not found")
    if "#" in raw.group(1) or "ingots" in raw.group(1):
        fail("RAW_ID_SHAPE must not be widened for item tags")


def player_ok(text: str) -> bool:
    """True when every bare #ns:path is preceded by a tag word."""
    for m in BARE_TAG_RE.finditer(text):
        start = m.start()
        before = text[max(0, start - 8) : start]
        if not (before.endswith("tag ") or before.endswith("标签 ") or before.endswith("標籤 ")):
            return False
    return True


def _expect_fail(fn, *args) -> bool:
    """Run a check that should SystemExit(1); swallow its FAIL print."""
    import io
    from contextlib import redirect_stdout

    buf = io.StringIO()
    try:
        with redirect_stdout(buf):
            fn(*args)
        return False
    except SystemExit as e:
        return e.code not in (0, None)


def negative_control_hints() -> None:
    """Inject old bare return ⇒ check_hints must fail."""
    src = HINTS.read_text(encoding="utf-8")
    poisoned = src.replace(
        "String paren = ReplyLang.itemTagParen(lang, tag);\n"
        "            if (paren == null || paren.isEmpty()) {\n"
        "                return name;\n"
        "            }\n"
        "            return name + \"（\" + paren + \"）\";",
        'return name + "（" + tag + "）";',
        1,
    )
    if poisoned == src:
        fail("negative control: could not poison IngredientReqHints")
    if not _expect_fail(check_hints, poisoned):
        fail("negative control: bare-tag IngredientReqHints did not fail")


def negative_control_scrub() -> None:
    """Remove humanizeBareItemTags call ⇒ check_scrub must fail."""
    src = SCRUB.read_text(encoding="utf-8")
    poisoned = src.replace("body = humanizeBareItemTags(body);\n", "", 1)
    if poisoned == src:
        fail("negative control: could not poison AskReplyScrub")
    if not _expect_fail(check_scrub, poisoned):
        fail("negative control: scrub without humanizeBareItemTags did not fail")


def negative_control_player_text() -> None:
    bare = "Steel Ingot（#forge:ingots/steel）"
    if player_ok(bare):
        fail("negative control: bare #forge:ingots/steel marked ok")
    labeled = "Steel Ingot（標籤 #forge:ingots/steel）"
    if not player_ok(labeled):
        fail("labeled tag form must pass player_ok")
    if not TAG_WORD_RE.search(labeled):
        fail("labeled form must match TAG_WORD_RE")
    # Counter-examples without '#'
    if BARE_TAG_RE.search("minecraft:diamond"):
        fail("minecraft:diamond must not match BARE_TAG_RE")
    if BARE_TAG_RE.search("ad_astra:steel_ingot"):
        fail("ad_astra:steel_ingot must not match BARE_TAG_RE")


def main() -> None:
    check_lang()
    check_reply_lang(REPLY_LANG.read_text(encoding="utf-8"))
    check_hints(HINTS.read_text(encoding="utf-8"))
    check_scrub(SCRUB.read_text(encoding="utf-8"))
    negative_control_player_text()
    negative_control_hints()
    negative_control_scrub()
    print("check_item_tag_humanize OK")


if __name__ == "__main__":
    main()
