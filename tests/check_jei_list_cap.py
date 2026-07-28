#!/usr/bin/env python3
"""Mirrors JeiLookup.capListedDetails — keep max shortest lines + optional more."""


def cap_listed_details(details: list[str], max_n: int, more_line: str | None) -> list[str]:
    if not details:
        return []
    cap = max(1, max_n)
    sorted_d = sorted(details, key=lambda s: (len(s), s))
    if len(sorted_d) <= cap:
        return list(sorted_d)
    out = sorted_d[:cap]
    if more_line:
        out.append(more_line)
    return out


def main() -> None:
    lines = [
        "a → short",
        "very long ritual ingredients → cursed thing",
        "mid → out",
        "x → y",
        "zzzzzzzz → altar spam",
    ]
    capped = cap_listed_details(lines, 3, "...and 2 more in Dark Altar — open JEI")
    assert len(capped) == 4, capped
    assert capped[-1].startswith("...and 2 more"), capped
    assert "a → short" in capped
    assert "x → y" in capped
    assert "mid → out" in capped
    assert "very long ritual" not in "\n".join(capped)

    assert cap_listed_details(["only"], 3, "more") == ["only"]
    assert cap_listed_details([], 3, "more") == []
    print("check_jei_list_cap OK")


if __name__ == "__main__":
    main()
