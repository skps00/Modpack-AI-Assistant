#!/usr/bin/env python3
"""Self-check: upgrade-style JEI predicate (focus id in both INPUT and OUTPUT)."""


def focus_appears_as_input_and_output(
    focus_id: str, input_ids: list[str], output_ids: list[str]
) -> bool:
    if not focus_id or not focus_id.strip():
        return False
    fid = focus_id.strip().lower()
    as_in = any(i and i.strip().lower() == fid for i in input_ids)
    as_out = any(o and o.strip().lower() == fid for o in output_ids)
    return as_in and as_out


def main() -> None:
    # Arcane anvil upgrade: same blade id in + out
    assert focus_appears_as_input_and_output(
        "slashblade:slashblade",
        ["slashblade:slashblade", "minecraft:diamond"],
        ["slashblade:slashblade"],
    )
    # Normal craft: focus only as output
    assert not focus_appears_as_input_and_output(
        "minecraft:stick",
        ["minecraft:oak_planks"],
        ["minecraft:stick"],
    )
    # Focus only as input (uses)
    assert not focus_appears_as_input_and_output(
        "minecraft:iron_ingot",
        ["minecraft:iron_ingot"],
        ["minecraft:iron_block"],
    )
    # Case-insensitive registry id
    assert focus_appears_as_input_and_output(
        "Mod:Item",
        ["mod:item"],
        ["MOD:ITEM"],
    )
    assert not focus_appears_as_input_and_output("", ["a:b"], ["a:b"])
    print("ok jei_upgrade_filter")


if __name__ == "__main__":
    main()
