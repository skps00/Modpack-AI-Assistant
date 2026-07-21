#!/usr/bin/env python3
"""Minimal self-check: mod-list pruning enables the right scanners."""

from mod_filter import active_scanners, derive_focus_mods, filter_paths_by_focus, search_query_mods
from pathlib import Path


def main() -> None:
    mods = ["minecraft", "neoforge", "kubejs", "create", "ftbquests", "packai"]
    scanners = active_scanners(mods)
    assert "kubejs" in scanners, scanners
    assert "ftbquests" in scanners, scanners
    assert "crafttweaker" not in scanners, scanners

    focus = search_query_mods(mods, {"id": "create:andesite_alloy", "empty": False})
    assert focus[0] == "create", focus
    assert "minecraft" not in focus and "neoforge" not in focus, focus

    derived = derive_focus_mods(mods, {"id": "create:andesite_alloy"}, "need create press")
    assert "create" in derived, derived

    paths = [
        Path("kubejs/server_scripts/create_fix.js"),
        Path("datapacks/x/data/botania/recipes/a.json"),
    ]
    kept = filter_paths_by_focus(paths, ["create"])
    assert any("create" in p.as_posix() for p in kept), kept
    assert not any("botania" in p.as_posix() for p in kept), kept

    print("ok", scanners, focus, derived)


if __name__ == "__main__":
    main()
