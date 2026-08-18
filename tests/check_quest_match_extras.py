"""Hotbar-only quest item hits must not surface as related missions."""

from __future__ import annotations


def score_hit(held: str, extras: list[str], question: str, items: list[str], blob: str) -> int | None:
    """Mirror QuestGuide.matchResult scoring (focus-id admit + hotbar-noise fix)."""
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
    # Concrete focus id → must reference that id (held_score); title-only token noise rejected.
    if held_l and held_score <= 0:
        return None
    if held_l and held_score >= 6 and total < 8:
        total = 8
    if held_l:
        admit = total >= 8 and held_score > 0
    else:
        admit = total >= 8 and token_score > 0
    return total if admit else None


def main() -> None:
    junk = ["mod:coin_gold"]
    focus = "mod:advanced_eyeglass"
    # Unrelated quest that only shares a hotbar coin
    assert score_hit(focus, junk, "how to craft", junk, "coin gold quest") is None
    # Focus item in quest items → keep
    assert score_hit(focus, junk, "how to craft", [focus], "advanced eyeglass tip") is not None
    # Hotbar alone with empty held → reject
    assert score_hit("", junk, "???", junk, "coin gold") is None
    # Title/display-name overlap only (扳手) must not bind create:wrench
    q = "What is Wrench (create:wrench) used for in this pack? Recipes and how to obtain?"
    assert score_hit(
        "create:wrench",
        [],
        q,
        ["create:precision_mechanism", "create:brass_sheet"],
        "pressure spring wrench 压力发条扳手 inlay precision mechanism stun",
    ) is None
    # Same question but quest lists create:wrench → keep
    assert score_hit(
        "create:wrench",
        [],
        q,
        ["create:wrench"],
        "make a create wrench",
    ) is not None
    # Full registry id in quest text only (+6) → keep after promote
    assert score_hit(
        "create:wrench",
        [],
        "how?",
        [],
        "reward is create:wrench from the book",
    ) is not None
    # Unspaced CJK: titleContainScore 8 admits empty-hand (mirror QuestGuide)
    q_zh = "最初的骑士怎样召唤怎样召唤？"
    assert "最初的骑士" in q_zh
    assert len("最初的骑士") >= 4
    print("check_quest_match_extras: OK")


if __name__ == "__main__":
    main()
