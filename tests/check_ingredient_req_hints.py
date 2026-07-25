#!/usr/bin/env python3
"""Generic ingredient NBT policy — semantic gates, not mod-brand keep lists."""

DEFAULT_SKIP = (
    "energy;eu;fe;rf;mana;stored;capacity;eterna;durability;maxdamage;"
    "uuid;uid;color;texture;model;timestamp;hash;seed;damage"
)
# Semantic roles only — no slashblade / chestcavity / pack brand tokens.
DEFAULT_KEEP = (
    "kill;soul;refine;level;rank;tier;stage;progress;score;grade;quality;purity;"
    "upgrade;forge;blood;organ;times;combo;special;"
    "擊殺;等級;階段;進度;洗練;品質;器官"
)


def split_patterns(raw: str | None, default: str) -> list[str]:
    text = (raw or default).strip() or default
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
    for pat in patterns or split_patterns(None, DEFAULT_SKIP):
        if pat and pat in lower:
            return True
    return False


def looks_like_namespaced_attr_key(text: str) -> bool:
    key = text.strip()
    if len(key) < 3 or len(key) > 64 or " " in key:
        return False
    colon = key.find(":")
    if colon <= 0 or colon >= len(key) - 1 or key.find(":", colon + 1) >= 0:
        return False
    ns = key[:colon].lower()
    if ns in ("minecraft", "forge", "neoforge", "c"):
        return False
    for c in key:
        if c in ":_/.":
            continue
        if not (("a" <= c <= "z") or ("A" <= c <= "Z") or ("0" <= c <= "9")):
            return False
    return True


def matches_keep(text: str, patterns: list[str] | None = None) -> bool:
    if not text or not text.strip():
        return False
    lower = text.lower()
    for pat in patterns or split_patterns(None, DEFAULT_KEEP):
        if pat and pat in lower:
            return True
    return looks_like_namespaced_attr_key(text)


def allow_extra(text: str, mode: str, skip=None, keep=None) -> bool:
    if not text or not text.strip() or matches_skip(text, skip):
        return False
    if mode == "keep_only":
        return matches_keep(text, keep)
    return mode == "all"


def nbt_extras(pairs: list[tuple[str, float]], mode: str = "all", limit: int = 8) -> list[str]:
    out = []
    for key, value in pairs:
        if value == 0 or not allow_extra(key, mode):
            continue
        if float(value) == int(value):
            shown = str(int(value))
        else:
            shown = str(value)
        out.append(f"{key}≥{shown}")
        if len(out) >= limit:
            break
    return out


def label(name: str, extras: list[str], mode: str) -> str:
    if mode == "none" or not extras:
        return name
    return name + "（" + "、".join(extras) + "）"


def extras_mode(policy: str = "auto", accepts_bare: bool = False) -> str:
    if policy == "never":
        return "none"
    if policy == "always":
        return "all"
    return "keep_only" if accepts_bare else "all"


def main() -> None:
    skip = split_patterns(None, DEFAULT_SKIP)
    keep = split_patterns(None, DEFAULT_KEEP)

    assert matches_skip("Total Energy Stored: 6000 EU", skip)
    assert matches_skip("Eterna: +2.00", skip)
    assert not matches_skip("killCount", skip)
    assert not matches_skip("times", skip)

    # Semantic hits (work for many packs, not one brand)
    assert matches_keep("killCount", keep)
    assert matches_keep("ProudSoul", keep)  # soul
    assert matches_keep("擊殺數: 100", keep)
    assert matches_keep("organData", keep)  # organ
    assert matches_keep("每安裝該器官提供 5 點力量", keep)
    assert matches_keep("times", keep)

    # Namespaced attr heuristic — covers organ scores without listing mod ids
    assert looks_like_namespaced_attr_key("chestcavity:health")
    assert matches_keep("chestcavity:health", keep)
    assert matches_keep("kubejs:custom_score", keep)
    assert not looks_like_namespaced_attr_key("minecraft:damage")
    assert not matches_keep("energyStored", keep)
    assert not matches_keep("RepairCounter", keep)

    # Brand tokens must NOT be required in the default keep list
    brand_tokens = ("slashblade", "chestcavity", "saya", "拔刀", "耀魂", "脆骨")
    for tok in brand_tokens:
        assert tok not in DEFAULT_KEEP, tok

    pairs = [
        ("RepairCounter", 100),
        ("energy", 6000),
        ("killCount", 50),
        ("eterna", 2),
        ("ProudSoul", 1000),
        ("chestcavity:health", 5),
        ("chestcavity:fire_resistant", -5),
        ("times", 3),
    ]

    assert extras_mode("auto", True) == "keep_only"
    keep_extras = nbt_extras(pairs, "keep_only")
    assert "killCount≥50" in keep_extras
    assert "ProudSoul≥1000" in keep_extras
    assert "chestcavity:health≥5" in keep_extras
    assert "chestcavity:fire_resistant≥-5" in keep_extras
    assert "times≥3" in keep_extras
    assert "energy" not in " ".join(keep_extras)
    assert "RepairCounter" not in " ".join(keep_extras)

    assert extras_mode("auto", False) == "all"
    all_extras = nbt_extras(pairs, "all")
    assert "RepairCounter≥100" in all_extras

    assert allow_extra("energy", "keep_only") is False
    print("ok ingredient_nbt_semantic_gates")


if __name__ == "__main__":
    main()
