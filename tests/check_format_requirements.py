#!/usr/bin/env python3
"""Mirror FormatRequirements.lines / askBlock / ingredient-gate filter."""

import re

INGREDIENT_GATE = re.compile(r"(?i)(refine|kill|proud[_\s-]?soul)\s*[≥>=]")
MAX_LINES = 12


def is_ingredient_gate_noise(raw: str | None) -> bool:
    if raw is None or not raw.strip():
        return True
    return INGREDIENT_GATE.search(raw.strip()) is not None


def clean(raw: str | None) -> str:
    if raw is None:
        return ""
    s = raw.strip()
    if not s or len(s) > 96:
        return ""
    return s


def lines(
    ingredient_hints: list[str] | None,
    req_notes: list[str] | None,
    unlock_gates: list[str] | None,
    *,
    unlock_prefix: str = "Unlock: ",
) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []

    def add_all(src: list[str] | None, unlock: bool) -> None:
        if not src:
            return
        for raw in src:
            if len(out) >= MAX_LINES:
                return
            cleaned = clean(raw)
            if not cleaned or is_ingredient_gate_noise(cleaned):
                continue
            line = (unlock_prefix + cleaned) if unlock else cleaned
            key = line.lower()
            if key in seen:
                continue
            seen.add(key)
            out.append(line)

    add_all(ingredient_hints, False)
    add_all(req_notes, False)
    add_all(unlock_gates, True)
    return out


def ask_block(
    ingredient_hints: list[str] | None,
    req_notes: list[str] | None,
    unlock_gates: list[str] | None,
    *,
    header: str = "REQUIREMENTS:\n",
    unlock_prefix: str = "Unlock: ",
) -> str:
    merged = lines(ingredient_hints, req_notes, unlock_gates, unlock_prefix=unlock_prefix)
    if not merged:
        return ""
    body = "".join(f"- {line}\n" for line in merged)
    return header + body


def main() -> None:
    assert is_ingredient_gate_noise("refine≥100")
    assert is_ingredient_gate_noise("kill>=50")
    assert not is_ingredient_gate_noise("0.7 XP")
    assert not is_ingredient_gate_noise("10 seconds")

    # Plain 3x3 → empty
    assert lines([], [], []) == []
    assert ask_block([], [], []) == ""

    # XP + time notes
    merged = lines([], ["0.7 XP", "10 seconds"], [])
    assert merged == ["0.7 XP", "10 seconds"], merged

    # Dedupe case-insensitive
    merged = lines([], ["0.7 XP", "0.7 xp"], [])
    assert merged == ["0.7 XP"], merged

    # refine noise dropped from notes; unlock kept with prefix
    merged = lines(
        ["Iron Ingot（refine≥100）"],
        ["refine≥100", "Stress Impact: 4.0"],
        ["stage_bronze"],
        unlock_prefix="解鎖：",
    )
    assert "refine≥100" not in merged
    assert "Stress Impact: 4.0" in merged
    assert "解鎖：stage_bronze" in merged
    # ingredient hint with refine inside still dropped by gate filter on whole string
    assert not any("Iron" in x for x in merged)

    # Multiple unlock gates keep order + prefix
    unlocked = lines([], [], ["bronze", "Getting Wood"], unlock_prefix="Unlock: ")
    assert unlocked == ["Unlock: bronze", "Unlock: Getting Wood"], unlocked

    block = ask_block([], ["0.7 XP"], [], header="REQUIREMENTS:\n")
    assert block.startswith("REQUIREMENTS:\n")
    assert "- 0.7 XP\n" in block

    block2 = ask_block([], [], ["bronze"], header="REQUIREMENTS:\n", unlock_prefix="Unlock: ")
    assert "- Unlock: bronze\n" in block2

    print("ok format_requirements")


if __name__ == "__main__":
    main()
