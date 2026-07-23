#!/usr/bin/env python3
"""Generic ingredient NBT policy helpers — sample noise ≠ craft requirements."""

DEFAULT_SKIP = (
    "energy;eu;fe;rf;mana;stored;capacity;eterna;durability;maxdamage;"
    "uuid;uid;color;texture;model;time;hash;seed;damage"
)


def skip_patterns(raw: str | None = None) -> list[str]:
    text = (raw or DEFAULT_SKIP).strip() or DEFAULT_SKIP
    out, seen = [], set()
    for part in text.split(";"):
        s = part.strip().lower()
        if s and s not in seen:
            seen.add(s)
            out.append(s)
    return out


def matches_skip(text: str, patterns: list[str] | None = None) -> bool:
    if not text or not text.strip():
        return True
    lower = text.lower()
    for pat in patterns or skip_patterns():
        if pat and pat in lower:
            return True
    return False


def nbt_extras(pairs: list[tuple[str, int]], limit: int = 6) -> list[str]:
    out = []
    for key, value in pairs:
        if value <= 0 or matches_skip(key):
            continue
        out.append(f"{key}≥{value}")
        if len(out) >= limit:
            break
    return out


def label(name: str, extras: list[str], nbt_matters: bool) -> str:
    if not nbt_matters or not extras:
        return name
    return name + "（" + "、".join(extras) + "）"


def auto_nbt_matters(accepts_bare: bool, policy: str = "auto") -> bool:
    if policy == "never":
        return False
    if policy == "always":
        return True
    # auto
    return not accepts_bare


def main() -> None:
    pats = skip_patterns()
    assert matches_skip("Total Energy Stored: 6000 EU", pats)
    assert matches_skip("Eterna: +2.00", pats)
    assert matches_skip("energyStored", pats)
    assert not matches_skip("RepairCounter", pats)
    assert not matches_skip("killCount", pats)

    extras = nbt_extras([
        ("RepairCounter", 100),
        ("energy", 6000),
        ("killCount", 50),
        ("eterna", 2),
    ])
    assert extras == ["RepairCounter≥100", "killCount≥50"], extras

    # bare bookshelf accepted → name only
    assert not auto_nbt_matters(True, "auto")
    assert label("書櫃", ["energy≥6000"], False) == "書櫃"
    # real NBT gate
    assert auto_nbt_matters(False, "auto")
    assert "RepairCounter" in label("拔刀", extras, True)

    assert auto_nbt_matters(True, "never") is False
    assert auto_nbt_matters(True, "always") is True

    # tooltip-as-req default off: we simply don't call tooltip extras in that mode
    assert matches_skip("Mana Stored: 12", pats)

    print("ok ingredient_nbt_policy_generic")


if __name__ == "__main__":
    main()
