"""Hotbar-only quest item hits must not surface as related missions."""

from __future__ import annotations


def score_hit(held: str, extras: list[str], question: str, items: list[str], blob: str) -> int | None:
    """Mirror QuestGuide.matchResult scoring (post hotbar-noise fix)."""
    held_l = (held or "").lower()
    blob_l = blob.lower()
    held_score = 0
    extra_score = 0
    token_score = 0
    if held_l and any(i.lower() == held_l for i in items):
        held_score += 10
    if held_l and held_l in blob_l:
        held_score += 6
    for extra in extras:
        if not extra:
            continue
        el = extra.lower()
        if any(i.lower() == el for i in items):
            extra_score += 4
        elif el in blob_l or el.replace(":", "_") in blob_l:
            extra_score += 2
    stop = {
        "the", "and", "for", "with", "from", "this", "that", "item", "block",
        "minecraft", "mod", "pack", "how", "what", "use", "used", "recipe",
        "recipes", "obtain", "craft", "golden", "enchanted",
    }
    import re
    for tok in re.split(r"[^a-z0-9_\u4e00-\u9fff]+", (question or "").lower()):
        if len(tok) < 3 or tok in stop:
            continue
        if tok in blob_l:
            token_score += 2
    total = held_score + extra_score + token_score
    if total >= 8 and (held_score > 0 or token_score > 0):
        return total
    return None


def main() -> None:
    junk = ["mod:coin_gold"]
    focus = "mod:advanced_eyeglass"
    # Unrelated quest that only shares a hotbar coin
    assert score_hit(focus, junk, "how to craft", junk, "coin gold quest") is None
    # Focus item in quest items → keep
    assert score_hit(focus, junk, "how to craft", [focus], "advanced eyeglass tip") is not None
    # Hotbar alone with empty held → reject
    assert score_hit("", junk, "???", junk, "coin gold") is None
    print("check_quest_match_extras: OK")


if __name__ == "__main__":
    main()
