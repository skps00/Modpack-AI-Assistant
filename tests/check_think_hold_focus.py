"""Y / ThinkHold replaces InvPick pending with the hovered item only."""

from __future__ import annotations


def think_hold_pending(hovered_id: str | None, prior_pending: list[str]) -> list[str]:
    """Mirror askAboutStack: drop multi-select; pin focus to Y target."""
    _ = prior_pending  # intentionally ignored
    if not hovered_id:
        return []
    return [hovered_id]


def main() -> None:
    prior = [
        "minecraft:dirt",
        "minecraft:cobblestone",
        "minecraft:stone",
        "minecraft:oak_log",
        "minecraft:iron_ingot",
        "minecraft:coal",
        "minecraft:stick",
        "minecraft:torch",
    ]
    disc = "mrqx_disc_pack:dantalion_disc"
    assert think_hold_pending(disc, prior) == [disc]
    assert think_hold_pending(None, prior) == []
    assert think_hold_pending("", prior) == []
    # InvPick Ask path unchanged: empty hover does not invent picks.
    assert think_hold_pending(disc, []) == [disc]
    print("check_think_hold_focus: OK")


if __name__ == "__main__":
    main()
