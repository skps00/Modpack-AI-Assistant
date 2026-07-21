#!/usr/bin/env python3
"""Self-check for pack graph facts and removals."""

from pathlib import Path
import tempfile

from pack_graph import ensure_pack_graph, subgraph_facts


def main() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        js = root / "kubejs" / "server_scripts"
        js.mkdir(parents=True)
        (js / "r.js").write_text(
            "event.remove({ output: 'create:andesite_alloy' })\n"
            "event.shapeless('create:andesite_alloy', ['minecraft:iron_nugget', 'minecraft:andesite'])\n",
            encoding="utf-8",
        )
        paths = ["kubejs/server_scripts/r.js"]
        g = ensure_pack_graph(root, paths, "g1")
        assert "create:andesite_alloy" in g.removed_items
        facts, srcs = subgraph_facts(
            g,
            {"id": "create:andesite_alloy"},
            "alloy",
            ["create"],
        )
        assert any("recipe_needs" in f or "removed" in f for f in facts), facts
        assert srcs
    print("ok pack_graph", facts[:3])


if __name__ == "__main__":
    main()
