"""Mirror PackIndex.clipNearMatch / shouldSkipSnippets — nearby KubeJS clip + PURPOSE keep."""
from __future__ import annotations

CLIP_LINES_RADIUS_DEFAULT = 30
CLIP_MAX_CHARS = 1100
SNIPPET_SKIP_WHEN_FACTS = 2


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


def is_craft_oriented(question: str) -> bool:
    q = (question or "").lower()
    keys = (
        "如何做",
        "怎麼做",
        "怎么做",
        "怎麼合成",
        "怎么合成",
        "如何合成",
        "如何製作",
        "如何制作",
        "配方",
        "合成",
        "製作",
        "制作",
        "how to make",
        "how to craft",
        "how do i craft",
        "how do i make",
        "craft ",
        " crafting",
        "recipe",
    )
    return any(k in q for k in keys)


def has_craft_shaped_fact(related: list[str]) -> bool:
    return any(f and "-[recipe_needs]->" in f for f in related)


def is_code_or_behavior(question: str) -> bool:
    q = (question or "").lower()
    if any(
        k in q
        for k in (
            "kubejs",
            "源码",
            "源碼",
            "脚本",
            "腳本",
            "程式",
            "程序",
            "script",
            "原理",
            "behavior",
            "行為",
            "行为",
            "how it works",
            "how this works",
            "怎么工作",
            "怎麼工作",
            "代碼",
            "代码",
        )
    ):
        return True
    if "how does" in q and "work" in q:
        return True
    if "code" in q and any(k in q for k in ("check", "read", "看", "查", "讀", "读")):
        return True
    return False


def should_attach_cards(question: str) -> bool:
    if not (question or "").strip():
        return True
    if is_craft_oriented(question):
        return True
    # acquire: keep cards (mirror PackIndex.isAcquireOrientedQuestion — craft already covered)
    q = question.lower()
    if any(
        k in q
        for k in (
            "how to get",
            "how do i get",
            "where to get",
            "obtain",
            "summon",
            "召唤",
            "召喚",
            "如何取得",
            "怎么获得",
            "怎麼獲得",
            "怎么来",
            "怎麼來",
        )
    ):
        return True
    return not is_code_or_behavior(question)


def should_skip(question: str, related: list[str], seeds: set[str], purpose_ask: bool) -> bool:
    if not related:
        return False
    if is_code_or_behavior(question):
        return False
    if purpose_facts_cover(related, seeds):
        return True
    if purpose_ask:
        return False
    if len(related) >= SNIPPET_SKIP_WHEN_FACTS:
        return True
    return is_craft_oriented(question) and has_craft_shaped_fact(related)


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
    recipe = "item:minecraft:diamond -[recipe_needs]-> item:minecraft:coal"
    # Craft ask + one recipe fact → still skip
    assert should_skip("如何做鑽石", [recipe], {"minecraft:diamond"}, False)
    # General ask + single weak/recipe fact → keep clips
    assert not should_skip("告訴我鑽石", [recipe], {"minecraft:diamond"}, False)
    # Two facts → skip even for general ask
    assert should_skip(
        "告訴我鑽石",
        [recipe, "item:minecraft:diamond -[loot]-> chest"],
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
    # Code ask keeps clips even when purpose facts already cover
    assert not should_skip(
        "check it's code",
        ["item:kubejs:miracle_milk -[desc]-> restores soul"],
        seeds,
        False,
    )
    assert is_code_or_behavior("check it's code")
    assert is_code_or_behavior("看一下原理")
    assert not should_attach_cards("check it's code")
    assert should_attach_cards("如何做鑽石")
    assert should_attach_cards("魔力转化器")
    print("check_packindex_nearby_clip OK")


if __name__ == "__main__":
    main()
