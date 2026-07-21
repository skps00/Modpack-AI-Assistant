#!/usr/bin/env python3
"""Self-check for path-first index, focus filter, inverted lookup."""

from __future__ import annotations

import tempfile
from pathlib import Path

from mod_filter import active_scanners, derive_focus_mods, filter_paths_by_focus, path_matches_focus
from rag import PackIndex


def main() -> None:
    mods = ["minecraft", "neoforge", "kubejs", "create", "ftbquests", "packai"]
    scanners = active_scanners(mods)
    assert "kubejs" in scanners
    assert "crafttweaker" not in scanners

    focus = derive_focus_mods(mods, {"id": "create:andesite_alloy"}, "how to make create alloy")
    assert "create" in focus, focus

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "kubejs" / "server_scripts").mkdir(parents=True)
        (root / "kubejs" / "server_scripts" / "create_recipes.js").write_text(
            "ServerEvents.recipes(e => { e.shaped('create:andesite_alloy', ['A'], {A:'minecraft:iron_ingot'}) })",
            encoding="utf-8",
        )
        (root / "kubejs" / "server_scripts" / "botania_stuff.js").write_text(
            "// botania only\nServerEvents.recipes(e => { e.remove({mod:'botania'}) })",
            encoding="utf-8",
        )
        (root / "datapacks" / "pack" / "data" / "botania" / "recipes").mkdir(parents=True)
        huge = root / "datapacks" / "pack" / "data" / "botania" / "recipes" / "flower.json"
        huge.write_text('{"type":"minecraft:crafting_shapeless","ingredients":[],"result":{"item":"botania:flower"}}', encoding="utf-8")

        # Without kubejs in scanners, that root should not be indexed
        idx = PackIndex()
        no_kjs = PackIndex()
        key1 = "k1"
        key2 = "k2"
        pi = idx.ensure_index(root, mods, scanners, key1)
        assert pi is not None
        assert any("create_recipes" in p for p in pi.paths), pi.paths
        assert "create" in pi.inverted, list(pi.inverted.keys())[:20]

        pi2 = no_kjs.ensure_index(root, ["minecraft", "neoforge"], ["datapacks"], key2)
        assert pi2 is not None
        assert not any(p.startswith("kubejs/") for p in pi2.paths), pi2.paths

        # Focus create should prefer create path; botania datapack path filtered out when focus=create
        abs_paths = [root / p for p in pi.paths]
        focused = filter_paths_by_focus(abs_paths, ["create"])
        focused_rels = [p.relative_to(root).as_posix() for p in focused]
        assert any("create" in r for r in focused_rels), focused_rels
        assert not any("botania/recipes" in r for r in focused_rels), focused_rels

        snippets, sources, score, high = idx.retrieve(
            question="create andesite alloy recipe",
            pack_root=root,
            mod_ids=mods,
            scanners=scanners,
            cache_key=key1,
            held_item={"id": "create:andesite_alloy", "empty": False},
            focus_mods=["create"],
        )
        assert sources, (snippets, sources)
        assert any("create" in s for s in sources), sources
        # Should not need to have read unrelated botania as top hit
        assert not any("botania" in s for s in sources), sources

        assert path_matches_focus("kubejs/server_scripts/create_recipes.js", ["create"])
        print("ok", "scanners=", scanners, "sources=", sources, "score=", score, "high=", high)


if __name__ == "__main__":
    main()
