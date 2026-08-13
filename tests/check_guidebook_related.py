#!/usr/bin/env python3
"""B3 — related hop + scope re-apply; default off. Pending user `tests OK`."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def expand_related(seeds, by_key, category_map, scope, item_ns, max_extra=2):
    merged = {e["key"]: e for e in seeds}
    added = 0
    for seed in seeds:
        if added >= max_extra:
            break
        for to in seed.get("linksOut") or []:
            if added >= max_extra:
                break
            t = by_key.get(to)
            if not t or t["key"] in merged:
                continue
            if scope == "same_mod" and item_ns and t["bookNs"] != item_ns:
                continue
            merged[t["key"]] = t
            added += 1
        cat = (seed.get("categoryId") or "").lower()
        for k in category_map.get(cat) or []:
            if added >= max_extra:
                break
            t = by_key.get(k)
            if not t or t["key"] in merged:
                continue
            if scope == "same_mod" and item_ns and t["bookNs"] != item_ns:
                continue
            merged[t["key"]] = t
            added += 1
    return list(merged.values())


def main() -> None:
    for side in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        pins = read(f"{side}/logic/GuidebookPins.java")
        assert "expandRelated" in pins
        assert "MAX_RELATED_EXTRA" in pins
        cfg = read(f"{side}/config/PackAiConfig.java")
        assert "GUIDEBOOK_RELATED_HOP" in cfg
        assert 'define("guidebookRelatedHop", false)' in cfg
        lookup = read(f"{side}/client/patchouli/PatchouliGuideLookup.java")
        assert "guidebookRelatedHop" in lookup
        settings = read(f"{side}/client/gui/PackAiSettingsScreen.java")
        assert "guidebook_related" in settings

    a = {"key": "goety/black_book/a", "bookNs": "goety", "categoryId": "intro", "linksOut": ["goety/black_book/b"]}
    b = {"key": "goety/black_book/b", "bookNs": "goety", "categoryId": "intro", "linksOut": []}
    cross = {"key": "other/book/x", "bookNs": "other", "categoryId": "intro", "linksOut": []}
    by = {e["key"]: e for e in (a, b, cross)}
    cat = {"intro": ["goety/black_book/a", "goety/black_book/b", "other/book/x"]}
    expanded = expand_related([a], by, cat, "same_mod", "goety", 2)
    keys = {e["key"] for e in expanded}
    assert "goety/black_book/b" in keys
    assert "other/book/x" not in keys  # same_mod drops cross ns

    print("check_guidebook_related OK (pending user tests OK)")


if __name__ == "__main__":
    main()
