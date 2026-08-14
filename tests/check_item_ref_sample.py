# Check ItemRef sample NBT path (mirrors Java ItemResolver.stackFromRef / preferFocusNbt).
# Run: python tests/check_item_ref_sample.py


def stack_from_ref(has_sample: bool, sample_nbt: dict | None, bare_nbt: dict | None) -> dict | None:
    """Prefer sample NBT when present; else bare registry stack (usually empty NBT)."""
    if has_sample:
        return dict(sample_nbt or {})
    return dict(bare_nbt or {})


def prefer_focus_nbt(built_has_nbt: bool, focus_has_nbt: bool, same_item: bool) -> str:
    """Which stack GUI should render. Fails closed on 'built' when items differ."""
    if not same_item:
        return "built"
    if built_has_nbt:
        return "built"
    if focus_has_nbt:
        return "focus"
    return "built"


def main() -> None:
    wm_real = {"Ability": "endure", "CoreWeaponlevels": 12, "PartList": ["a", "b"]}
    assert stack_from_ref(True, wm_real, {}) == wm_real
    assert stack_from_ref(False, wm_real, {}) == {}
    bare = stack_from_ref(False, None, {})
    assert bare.get("Ability") in (None, "")
    # Empty ability key → lang ability.weaponmaster..name
    ability = bare.get("Ability", "")
    key = f"ability.weaponmaster.{ability}.name"
    assert key == "ability.weaponmaster..name"

    assert prefer_focus_nbt(False, True, True) == "focus"
    assert prefer_focus_nbt(True, True, True) == "built"
    assert prefer_focus_nbt(False, True, False) == "built"
    assert prefer_focus_nbt(False, False, True) == "built"
    print("check_item_ref_sample OK")


if __name__ == "__main__":
    main()
