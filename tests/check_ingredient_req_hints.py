#!/usr/bin/env python3
"""Generic NBT/component extras on JEI ingredient labels (mod-agnostic)."""

import re

SKIP = re.compile(r".*(uuid|uid|color|texture|model|time|damage|maxdamage|hash|seed).*", re.I)


def nbt_extras(pairs: list[tuple[str, int]], limit: int = 4) -> list[str]:
    out = []
    for key, value in pairs:
        if value <= 0 or SKIP.match(key):
            continue
        out.append(f"{key}≥{value}")
        if len(out) >= limit:
            break
    return out


def rich_label(name: str, extras: list[str]) -> str:
    if not extras:
        return name
    return name + "（" + "、".join(extras) + "）"


def looks_like_requirement(s: str) -> bool:
    t = s.strip()
    if len(t) < 2 or len(t) > 48:
        return False
    if any(ch.isdigit() for ch in t):
        return True
    return bool(re.search(r"\s(I|II|III|IV|V|VI|VII|VIII|IX|X)$", t))


def main() -> None:
    extras = nbt_extras([("RepairCounter", 3), ("killCount", 100), ("Damage", 5), ("uuid", 1)])
    assert extras == ["RepairCounter≥3", "killCount≥100"]  # Damage/uuid skipped
    assert rich_label("Blade", extras + ["Sharpness III"]) == "Blade（RepairCounter≥3、killCount≥100、Sharpness III）"
    assert looks_like_requirement("Something 12")
    assert looks_like_requirement("Sharpness III")
    assert not looks_like_requirement("hello")
    # prompt must stay generic — no SlashBlade-only words required in this helper
    assert "鍛造" not in rich_label("Blade", extras)
    print("ok ingredient_req_hints_generic")


if __name__ == "__main__":
    main()
