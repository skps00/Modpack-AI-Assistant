#!/usr/bin/env python3
"""Mirror JeiFocusMatch OUTPUT: same item OK when focus name generic; distinctive/VARIANT need match."""


def output_matches_focus(
    output_item: str,
    output_nbt: str | None,
    focus_item: str,
    focus_nbt: str | None,
    *,
    focus_name: str = "",
    output_name: str = "",
    focus_schematics: list[str] | None = None,
    output_schematics: list[str] | None = None,
) -> bool:
    if output_item == focus_item and (output_nbt or "") == (focus_nbt or ""):
        return True
    name_useful = bool(focus_name) and focus_name.lower() not in {
        focus_item.lower(),
        focus_item.split(":", 1)[-1].lower().replace("_", " "),
        focus_item.split(":", 1)[-1].lower(),
        "item",
    }
    prefer = []
    for raw in focus_schematics or []:
        id_ = raw.strip().lower()
        prefer.extend([id_, id_.split(":", 1)[-1], id_.split("/")[-1]])
    out_blob = " ".join([output_name or ""] + list(output_schematics or [])).lower()
    mentions = any(t in out_blob for t in prefer if len(t) >= 2)
    if not prefer and focus_name:
        mentions = focus_name.lower() in out_blob
    out_useful = bool(output_name) and output_name.lower() not in {
        focus_item.lower(),
        focus_item.split(":", 1)[-1].lower(),
        "item",
    }
    if (
        output_item == focus_item
        and name_useful
        and out_useful
        and focus_name != output_name
        and not mentions
    ):
        return False
    has_variant = bool(focus_schematics)
    if output_item == focus_item:
        if has_variant:
            return focus_name == output_name or mentions
        if not name_useful or focus_name == output_name:
            return True
    if name_useful and focus_name == output_name:
        return True
    return False


def main() -> None:
    # Generic / same name: NBT may differ (Surgery Box samples)
    assert output_matches_focus(
        "mod:surgery_box", "{a:1}", "mod:surgery_box", "{a:2}",
        focus_name="Surgery Box", output_name="Surgery Box",
    )
    assert output_matches_focus("minecraft:diamond", None, "minecraft:diamond", None)
    assert not output_matches_focus("minecraft:coal", None, "minecraft:diamond", None)
    # Distinctive Tetra names must not collide
    assert not output_matches_focus(
        "tetra:scroll_rolled", "{s:[energy]}", "tetra:scroll_rolled", "{s:[mirror]}",
        focus_name="Mirror Scroll", output_name="Energy Bottle Scroll",
        focus_schematics=["tetra:mirror"],
        output_schematics=["tetra:energy_bottle"],
    )
    print("check_jei_focus_nbt_output OK")


if __name__ == "__main__":
    main()
