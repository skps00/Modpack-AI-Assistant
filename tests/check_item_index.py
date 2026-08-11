#!/usr/bin/env python3
"""WP3 — ItemIndexCache fingerprint / cache hit-miss + ItemSearch index wiring."""
from __future__ import annotations

import hashlib
import json
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def fingerprint_mods(lines: list[str]) -> str:
    sorted_lines = sorted(x.strip().lower() for x in lines if x and x.strip())
    return hashlib.sha1("\n".join(sorted_lines).encode("utf-8")).hexdigest()


def main() -> None:
    forge_cache = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/ItemIndexCache.java"
    )
    neo_cache = read(
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/ItemIndexCache.java"
    )
    for src in (forge_cache, neo_cache):
        assert "FORMAT_VERSION = 1" in src
        assert "MAX_ENTRIES = 80_000" in src
        assert "config/packai/item-index" in src
        assert "fingerprintMods" in src
        assert "shouldRebuild" in src
        assert "shouldUpgradeForJei" in src
        assert "metaMatches" in src
        assert "cacheKey" in src
        assert "root.addProperty(\"jei\"" in src or 'addProperty("jei"' in src

    forge_idx = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/knowledge/ItemIndex.java"
    )
    neo_idx = read(
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/knowledge/ItemIndex.java"
    )
    for src, loader in ((forge_idx, '"forge"'), (neo_idx, '"neoforge"')):
        assert "ensureAsync" in src
        assert "searchReady" in src
        assert "invalidate" in src
        assert "waitForJeiQuietly" in src
        assert "shouldUpgradeForJei" in src
        assert "JeiUniversalSpam.isSpamItemId" in src
        assert "ItemIndexCache.MAX_ENTRIES" in src
        assert loader in src
        assert "packai-item-index" in src  # daemon thread name

    forge_is = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/knowledge/ItemSearch.java"
    )
    neo_is = read(
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/knowledge/ItemSearch.java"
    )
    for src in (forge_is, neo_is):
        assert "ItemIndex.INSTANCE.ensureAsync" in src
        assert "ItemIndex.INSTANCE.searchReady" in src
        assert "liveSearch" in src
        assert "getAllIngredients" in src  # fallback still present

    forge_cs = read("forge/1.19.2/src/main/java/com/skps9/packai/client/ClientSetup.java")
    neo_cs = read("neoforge/1.21.1/src/main/java/com/skps9/packai/client/ClientSetup.java")
    for src in (forge_cs, neo_cs):
        assert "ItemIndex.INSTANCE.ensureAsync" in src
        assert "ItemIndex.INSTANCE.invalidate" in src

    forge_pk = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/knowledge/PackKnowledge.java"
    )
    neo_pk = read(
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/knowledge/PackKnowledge.java"
    )
    assert "ensureItemIndex" in forge_pk and "ensureItemIndex" in neo_pk

    forge_ui = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java"
    )
    neo_ui = read(
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java"
    )
    assert "PackKnowledge.ensureItemIndex" in forge_ui
    assert "PackKnowledge.ensureItemIndex" in neo_ui

    # Fingerprint hit / miss (mirror Java)
    fp_a = fingerprint_mods(["jei@10", "minecraft@1.19.2", "packai@0.1.5"])
    fp_b = fingerprint_mods(["jei@10", "minecraft@1.19.2", "packai@0.1.6"])
    assert fp_a != fp_b
    assert fp_a == fingerprint_mods(["packai@0.1.5", "jei@10", "minecraft@1.19.2"])

    # Synthetic score-over-N timing (NFWC spike deferred — silent mode)
    n = 20_000
    labels = [f"Item {i}" for i in range(n)]
    ids = [f"mod:item_{i}" for i in range(n)]
    q = "item_12345"
    t0 = time.perf_counter()
    hits = 0
    for i in range(n):
        idl = ids[i]
        if q in idl or q in labels[i].lower():
            hits += 1
    elapsed_ms = (time.perf_counter() - t0) * 1000
    # Sanity: linear over 20k entries should stay well under typical JEI full walk
    assert elapsed_ms < 500, f"synthetic index scan too slow: {elapsed_ms:.1f}ms"
    print(
        f"check_item_index synthetic_spike n={n} hits={hits} elapsed_ms={elapsed_ms:.2f} "
        "(NFWC live/JEI timing deferred — silent mode)"
    )

    # Round-trip JSON shape expected by ItemIndexCache
    doc = {
        "v": 1,
        "mc": "1.19.2",
        "loader": "forge",
        "lang": "zh_tw",
        "modFp": fp_a,
        "entries": [
            {"id": "minecraft:dirt", "label": "Dirt", "dedupe": "minecraft:dirt"},
            {
                "id": "tetra:scroll_rolled",
                "label": "卷軸",
                "nbt": '{key:"mirror"}',
                "schem": ["tetra:mirror", "mirror"],
                "dedupe": "tetra:scroll_rolled#tetra:mirror",
            },
        ],
    }
    assert json.loads(json.dumps(doc))["entries"][1]["schem"][1] == "mirror"

    for tree in (
        "forge/1.19.2/src/test/java/com/skps9/packai/logic/ItemIndexCacheCheck.java",
        "neoforge/1.21.1/src/test/java/com/skps9/packai/logic/ItemIndexCacheCheck.java",
    ):
        assert "ItemIndexCacheCheck" in read(tree)
        assert "shouldRebuild" in read(tree)

    print("check_item_index OK")


if __name__ == "__main__":
    main()
