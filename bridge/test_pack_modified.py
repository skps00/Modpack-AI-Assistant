#!/usr/bin/env python3
"""Self-check: pack modification detection -> local_only vs online_ok."""

from __future__ import annotations

from pack_modified import detect_modification


def main() -> None:
    # Pack has kubejs path touching create -> local_only
    v1 = detect_modification(
        scanners=["kubejs", "datapacks"],
        paths=[
            "kubejs/server_scripts/create_recipes.js",
            "datapacks/pack/data/minecraft/recipes/stick.json",
        ],
        focus_mods=["create"],
        held_item={"id": "create:andesite_alloy", "empty": False},
        snippets=[],
    )
    assert v1.pack_touched, v1
    assert v1.policy == "local_only", v1
    assert any("script_path" in r for r in v1.reasons), v1.reasons

    # Snippet recipe edit mentions focus -> local_only
    v2 = detect_modification(
        scanners=["kubejs"],
        paths=["kubejs/server_scripts/misc.js"],
        focus_mods=["create"],
        held_item={"id": "create:andesite_alloy"},
        snippets=["// file: kubejs/x.js\nevent.remove({ output: 'create:andesite_alloy' })"],
    )
    assert v2.pack_touched and v2.policy == "local_only", v2

    # Datapack override for create namespace
    v3 = detect_modification(
        scanners=["datapacks"],
        paths=["datapacks/pack/data/create/recipes/crushing/foo.json"],
        focus_mods=["create"],
        held_item={"id": "create:wheat_flour"},
        snippets=[],
    )
    assert v3.pack_touched and v3.policy == "local_only", v3

    # Common mod, no pack touch -> online_ok
    v4 = detect_modification(
        scanners=["datapacks"],
        paths=["datapacks/pack/data/minecraft/tags/blocks/logs.json"],
        focus_mods=["create"],
        held_item={"id": "create:andesite_alloy"},
        snippets=[],
    )
    assert not v4.pack_touched, v4
    assert v4.policy == "online_ok", v4

    # No scripting scanners and no override hit -> online_ok
    v5 = detect_modification(
        scanners=["config"],
        paths=["config/create-common.toml"],
        focus_mods=["create"],
        held_item={"id": "create:andesite_alloy"},
        snippets=["just a tooltip"],
    )
    assert v5.policy == "online_ok", v5

    print("ok", v1.policy, v4.policy)


if __name__ == "__main__":
    main()
