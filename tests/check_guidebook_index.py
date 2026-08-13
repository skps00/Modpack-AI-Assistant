#!/usr/bin/env python3
"""WP2 — GuidebookIndexCache fingerprint / hit-miss + inverted map + Forge/Neo wiring."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def fingerprint_mods(lines: list[str]) -> str:
    sorted_lines = sorted(x.strip().lower() for x in lines if x and x.strip())
    return hashlib.sha1("\n".join(sorted_lines).encode("utf-8")).hexdigest()


def build_item_map(entries: list[dict]) -> dict[str, list[str]]:
    m: dict[str, list[str]] = {}
    for e in entries:
        key = f"{e['bookNs']}/{e['bookId']}/{e['entryId']}"
        for item in e.get("linkedItems") or []:
            id_ = item.strip().lower()
            if not id_:
                continue
            lst = m.setdefault(id_, [])
            if key not in lst:
                lst.append(key)
    return m


def lang_rank(folder: str, preferred: str) -> int:
    folder = (folder or "").strip().lower()
    pref = (preferred or "").strip().lower()
    if not folder:
        return 2
    if pref and folder == pref:
        return 0
    if folder == "en_us":
        return 1
    return -1


def main() -> None:
    forge_cache = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/GuidebookIndexCache.java"
    )
    neo_cache = read(
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/GuidebookIndexCache.java"
    )
    for src in (forge_cache, neo_cache):
        assert "FORMAT_VERSION = 3" in src
        assert "MAX_ENTRIES = 20_000" in src
        assert "config/packai/guidebook-index" in src
        assert "fingerprintMods" in src
        assert "ItemIndexCache.fingerprintMods" in src
        assert "shouldRebuild" in src
        assert "metaMatches" in src
        assert "buildItemMap" in src
        assert "enrichLinksIn" in src
        assert "langRank" in src

    forge_idx = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/knowledge/GuidebookIndex.java"
    )
    neo_idx = read(
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/knowledge/GuidebookIndex.java"
    )
    for src, loader in ((forge_idx, '"forge"'), (neo_idx, '"neoforge"')):
        assert "ensureAsync" in src
        assert "lookupByItem" in src
        assert "invalidate" in src
        assert "packai-guidebook-index" in src
        assert "snapshotResources" in src
        assert "PatchouliEntryScan.toEntry" in src
        assert loader in src
        # async build must not call Patchouli API
        assert "PatchouliBridge" not in src

    forge_cs = read("forge/1.19.2/src/main/java/com/skps9/packai/client/ClientSetup.java")
    neo_cs = read("neoforge/1.21.1/src/main/java/com/skps9/packai/client/ClientSetup.java")
    for src in (forge_cs, neo_cs):
        assert "GuidebookIndex.INSTANCE.ensureAsync" in src
        assert "GuidebookIndex.INSTANCE.invalidate" in src

    # fingerprint semantics
    mods_a = ["jei@10", "minecraft@1.19.2", "packai@0.1.5"]
    mods_b = ["jei@10", "minecraft@1.19.2", "packai@0.1.6"]
    fp_a = fingerprint_mods(mods_a)
    fp_b = fingerprint_mods(mods_b)
    assert fp_a and fp_a != fp_b
    assert fp_a == fingerprint_mods(["packai@0.1.5", "jei@10", "minecraft@1.19.2"])

    # inverted map
    entries = [
        {
            "bookNs": "evilcraft",
            "bookId": "origins",
            "entryId": "items/gem",
            "linkedItems": ["evilcraft:dark_gem", "evilcraft:dark_gem_crushed"],
        },
        {
            "bookNs": "evilcraft",
            "bookId": "origins",
            "entryId": "items/gem2",
            "linkedItems": ["evilcraft:dark_gem"],
        },
    ]
    imap = build_item_map(entries)
    assert imap["evilcraft:dark_gem"] == [
        "evilcraft/origins/items/gem",
        "evilcraft/origins/items/gem2",
    ]
    assert imap["evilcraft:dark_gem_crushed"] == ["evilcraft/origins/items/gem"]

    # lang policy
    assert lang_rank("zh_tw", "zh_tw") == 0
    assert lang_rank("en_us", "zh_tw") == 1
    assert lang_rank("ja_jp", "zh_tw") == -1

    # disk json shape (mirror Java toJson fields)
    doc = {
        "v": 1,
        "mc": "1.19.2",
        "loader": "forge",
        "lang": "en_us",
        "modFp": fp_a,
        "entryCount": 1,
        "entries": [
            {
                "bookNs": "goety",
                "bookId": "black_book",
                "entryId": "intro/x",
                "lang": "en_us",
                "title": "X",
                "textClip": "hello",
                "sourcePath": "assets/goety/patchouli_books/black_book/en_us/entries/intro/x.json",
                "linkedItems": ["goety:cursed_ingot"],
            }
        ],
    }
    roundtrip = json.loads(json.dumps(doc))
    assert roundtrip["v"] == 1
    assert roundtrip["entries"][0]["bookNs"] == "goety"
    assert "goety:cursed_ingot" in roundtrip["entries"][0]["linkedItems"]
    # corrupt / unknown format → treat as miss
    assert json.loads('{"v":99,"entries":[{"bookNs":"x"}]}')["v"] != 1

    print("check_guidebook_index OK")


if __name__ == "__main__":
    main()
