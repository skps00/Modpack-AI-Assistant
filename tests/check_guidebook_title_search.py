#!/usr/bin/env python3
"""B2 — title token search thresholds + guide intent. Pending user `tests OK`."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HIGH = 2
HIGH_NO_ITEM = 3


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def tokenize(title: str, entry_id: str = "") -> list[str]:
    parts = []
    for raw in (title, entry_id.replace("_", " ").replace("-", " ").split("/")[-1] if entry_id else ""):
        if not raw:
            continue
        norm = re.sub(r"[^\w]+", " ", raw.lower(), flags=re.UNICODE)
        parts.extend(t for t in norm.split() if len(t) >= 2)
    # dedupe preserve order
    out, seen = [], set()
    for t in parts:
        if t not in seen:
            seen.add(t)
            out.append(t)
    return out


def title_score(entry_tokens: list[str], title: str, query: str) -> int:
    q = tokenize(query)
    if not q:
        return 0
    et = set(entry_tokens)
    hit = sum(1 for t in q if t in et)
    ql = query.strip().lower()
    if len(ql) >= 4 and ql in (title or "").lower():
        hit += 2
    return hit


def has_guide_intent(q: str) -> bool:
    q = (q or "").lower()
    keys = ("guide", "handbook", "patchouli", "manual", "手冊", "手册", "指南", "圖鑑", "图鉴", "purpose")
    return any(k in q for k in keys)


def main() -> None:
    for side in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        pins = read(f"{side}/logic/GuidebookPins.java")
        assert "HIGH_TITLE_SCORE = 2" in pins
        assert "HIGH_NO_ITEM_SCORE = 3" in pins
        assert "hasGuideIntent" in pins
        assert "rankByTitle" in pins
        lookup = read(f"{side}/client/patchouli/PatchouliGuideLookup.java")
        assert "searchByTitle" in lookup
        assert "HIGH_NO_ITEM_SCORE" in lookup
        assert "lookup(stack, question)" in lookup or "lookup(ItemStack stack, String question)" in lookup

    tokens = tokenize("Dark Ritual Altar", "intro/dark_ritual")
    assert "dark" in tokens and "ritual" in tokens
    strong = title_score(tokens, "Dark Ritual Altar", "dark ritual handbook")
    assert strong >= HIGH
    weak = title_score(tokens, "Dark Ritual Altar", "fish")
    assert weak < HIGH
    assert has_guide_intent("how does the handbook describe rituals")
    assert not has_guide_intent("how do I cook fish")
    # no-item needs stricter + intent
    assert strong >= HIGH_NO_ITEM or title_score(tokens, "Dark Ritual Altar", "dark ritual altar guide") >= HIGH_NO_ITEM

    print("check_guidebook_title_search OK (pending user tests OK)")


if __name__ == "__main__":
    main()
