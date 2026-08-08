"""preferFocusIdHits: list-id quests beat title-only / blob-only siblings."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass
class Hit:
    title: str
    items: list[str]
    score: int = 0


def mentions_focus(h: Hit, held: str) -> bool:
    want = held.lower().split("{", 1)[0]
    for it in h.items:
        got = it.lower().split("{", 1)[0]
        if got == want:
            return True
    return False


def prefer_focus_id_hits(scored: list[Hit], held: str) -> list[Hit]:
    if not scored or not held:
        return scored
    listed = [h for h in scored if mentions_focus(h, held)]
    return listed if listed else scored


def main() -> None:
    held = "create:wrench"
    title_only = Hit("压力发条扳手", ["create:precision_mechanism"], 12)
    listed = Hit("Brass Wrench", ["create:wrench"], 10)
    # Soft-prefer: when any lists focus id, drop title-only sibling
    out = prefer_focus_id_hits([title_only, listed], held)
    assert out == [listed], out
    # No listed id → keep blob/text survivors as-is
    out2 = prefer_focus_id_hits([title_only], held)
    assert out2 == [title_only]
    print("check_quest_focus_id_prefer: OK")


if __name__ == "__main__":
    main()
