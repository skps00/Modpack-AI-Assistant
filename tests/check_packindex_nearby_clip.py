"""Mirror PackIndex.clipNearMatch / shouldSkipSnippets — nearby KubeJS clip + PURPOSE keep."""
from __future__ import annotations

CLIP_LINES_RADIUS_DEFAULT = 30
CLIP_MAX_CHARS = 1100


def clip_near_match(text: str, needles: list[str], line_radius: int = CLIP_LINES_RADIUS_DEFAULT) -> str:
    if not text:
        return ""
    radius = max(5, min(100, line_radius))
    lower = text.lower()
    best = -1
    for n in needles:
        if not n or len(n) < 2:
            continue
        i = lower.find(n.lower())
        if i >= 0 and (best < 0 or i < best):
            best = i
    if best < 0:
        return text[:CLIP_MAX_CHARS]
    line_start = 0
    before = 0
    for i in range(best - 1, -1, -1):
        if text[i] == "\n":
            before += 1
            if before > radius:
                line_start = i + 1
                break
    line_end = len(text)
    after = 0
    for i in range(best, len(text)):
        if text[i] == "\n":
            after += 1
            if after > radius:
                line_end = i
                break
    clip = text[line_start:line_end]
    if len(clip) <= CLIP_MAX_CHARS:
        return clip
    local = max(0, best - line_start)
    half = CLIP_MAX_CHARS // 2
    frm = max(0, local - half)
    to = min(len(clip), frm + CLIP_MAX_CHARS)
    frm = max(0, to - CLIP_MAX_CHARS)
    return clip[frm:to]


def purpose_facts_cover(related: list[str], seeds: set[str]) -> bool:
    for f in related:
        strong = "-[desc]->" in f or "-[right_click" in f or "-[on:" in f
        if not strong:
            continue
        if any(s and s in f for s in seeds):
            return True
    return False


def should_skip(question: str, related: list[str], seeds: set[str], purpose_ask: bool) -> bool:
    if not related:
        return False
    if purpose_facts_cover(related, seeds):
        return True
    if purpose_ask:
        return False
    return len(related) >= 1


def main() -> None:
    pad = "".join(f"// PAD_LINE_{i}\n" for i in range(60))
    deep = pad + "ItemEvents.foodEaten('kubejs:miracle_milk', e => {\n  e.player.tell('soul_mana')\n})\n"
    near = clip_near_match(deep, ["kubejs:miracle_milk"], 30)
    assert "miracle_milk" in near and "soul_mana" in near, near
    assert "PAD_LINE_0" not in near, near

    tight = clip_near_match(deep, ["kubejs:miracle_milk"], 5)
    assert "miracle_milk" in tight, tight
    assert "PAD_LINE_0" not in tight, tight
    assert len(tight) <= len(near), (len(tight), len(near))

    seeds = {"kubejs:miracle_milk"}
    assert should_skip(
        "如何做鑽石",
        ["item:minecraft:diamond -[recipe_needs]-> item:minecraft:coal"],
        {"minecraft:diamond"},
        False,
    )
    assert not should_skip(
        "這個有什麼用",
        ["item:kubejs:miracle_milk -[recipe_needs]-> item:minecraft:milk_bucket"],
        seeds,
        True,
    )
    assert should_skip(
        "這個有什麼用",
        ["item:kubejs:miracle_milk -[desc]-> restores soul"],
        seeds,
        True,
    )
    print("check_packindex_nearby_clip OK")


if __name__ == "__main__":
    main()
