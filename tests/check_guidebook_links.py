#!/usr/bin/env python3
"""B1 — category + linksOut/linksIn (source asserts + fixture logic). Pending user `tests OK`."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LINK_MACRO = re.compile(r"\$\(l:([^)]+)\)", re.I)


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def collect_links_out(entry: dict, book_ns: str, book_id: str) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()

    def add(raw: str | None) -> None:
        if not raw:
            return
        s = raw.strip().replace("\\", "/")
        if "#" in s:
            s = s.split("#", 1)[0]
        if not s or s.startswith("http"):
            return
        if ":" not in s:
            key = f"{book_ns}/{book_id}/{s}".lower()
        else:
            ns, rest = s.split(":", 1)
            if ns.lower() != book_ns.lower() or not rest:
                return
            key = f"{ns.lower()}/{book_id}/{rest}".lower()
        if key not in seen:
            seen.add(key)
            out.append(key)

    for page in entry.get("pages") or []:
        if not isinstance(page, dict):
            continue
        add(page.get("entry") if isinstance(page.get("entry"), str) else None)
        add(page.get("link") if isinstance(page.get("link"), str) else None)
        text = page.get("text")
        if isinstance(text, str):
            for m in LINK_MACRO.finditer(text):
                add(m.group(1))
    return out[:16]


def enrich_links_in(entries: list[dict]) -> list[dict]:
    known = {e["key"] for e in entries}
    inbound: dict[str, list[str]] = {}
    for e in entries:
        for to in e.get("linksOut") or []:
            if to in known:
                inbound.setdefault(to, []).append(e["key"])
    out = []
    for e in entries:
        row = dict(e)
        row["linksIn"] = list(inbound.get(e["key"], []))
        out.append(row)
    return out


def main() -> None:
    for side in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        cache = read(f"{side}/logic/GuidebookIndexCache.java")
        assert "FORMAT_VERSION = 4" in cache
        assert "enrichLinksIn" in cache
        assert "buildCategoryMap" in cache
        assert "buildTitleTokenMap" in cache
        scan = read(f"{side}/logic/PatchouliEntryScan.java")
        assert "collectLinksOut" in scan
        assert "tokenizeTitle" in scan
        entry = read(f"{side}/logic/GuidebookEntry.java")
        assert "categoryId" in entry and "linksOut" in entry and "linksIn" in entry

    a = {
        "name": "A",
        "category": "goety:intro",
        "pages": [{"type": "text", "text": "See $(l:intro/b) next."}],
    }
    b = {
        "name": "B",
        "category": "goety:intro",
        "pages": [{"type": "patchouli:link", "entry": "intro/a", "text": "back"}],
    }
    la = collect_links_out(a, "goety", "black_book")
    lb = collect_links_out(b, "goety", "black_book")
    assert "goety/black_book/intro/b" in la
    assert "goety/black_book/intro/a" in lb
    rows = enrich_links_in(
        [
            {"key": "goety/black_book/intro/a", "linksOut": la},
            {"key": "goety/black_book/intro/b", "linksOut": lb},
        ]
    )
    by = {r["key"]: r for r in rows}
    assert "goety/black_book/intro/a" in by["goety/black_book/intro/b"]["linksIn"]
    assert "goety/black_book/intro/b" in by["goety/black_book/intro/a"]["linksIn"]
    # no invent
    assert collect_links_out({"pages": [{"text": "no links"}]}, "goety", "black_book") == []

    print("check_guidebook_links OK (pending user tests OK)")


if __name__ == "__main__":
    main()
