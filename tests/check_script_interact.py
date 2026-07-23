#!/usr/bin/env python3
"""Mirror PackIndex.parseRightClickFacts — multiple KubeJS interact styles."""
import re

HEADER = re.compile(
    r"(?:(BlockEvents)\.(rightClicked|leftClicked|broken)\s*\(\s*(?:['\"]([a-z0-9_.:/-]+)['\"]\s*,)?"
    r"|(ItemEvents)\.(rightClicked|entityInteracted|foodEaten)\s*\(\s*(?:['\"]([a-z0-9_.:/-]+)['\"]\s*,)?"
    r"|onEvent\(\s*['\"](block\.right_click|block\.left_click|block\.break|"
    r"item\.right_click|item\.entity_interact|item\.food_eaten)['\"]\s*,)",
    re.I,
)
HELD = re.compile(
    r"event\.(?:item|handItem|mainHandItem)(?:\.id)?(?:\s*(?:[=!]=|\.equals\()|[\s\S]{0,60}?)"
    r"['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
    re.I,
)
BLOCK = re.compile(
    r"event\.block(?:\.id)?(?:\s*(?:[=!]=|\.equals\()|[\s\S]{0,60}?)"
    r"['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
    re.I,
)
ENTITY = re.compile(
    r"event\.(?:target|entity)(?:\.type)?(?:\s*(?:[=!]=|\.equals\()|[\s\S]{0,40}?)"
    r"['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
    re.I,
)
GIVE = re.compile(
    r"(?:\.give|giveInHand|addItem|giveExperienceless|spawnAtLocation|popItem|setItemInHand|set\()"
    r"\s*\(\s*(?:Item\.of\()?['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
    re.I,
)


def extract_body(text: str, start: int) -> str:
    i = start
    while i < len(text) and text[i].isspace():
        i += 1
    arrow = text.find("=>", i)
    brace = text.find("{", i)
    if arrow >= 0 and (brace < 0 or arrow < brace):
        brace = text.find("{", arrow)
    if brace < 0 or brace - i > 120:
        return text[i : i + 500]
    depth = 0
    limit = min(len(text), brace + 2500)
    for j in range(brace, limit):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return text[brace : j + 1]
    return text[brace:limit]


def classify(m: re.Match):
    if m.group(1):
        method = (m.group(2) or "").lower()
        via = {"leftclicked": "left_click", "broken": "break"}.get(method, "right_click")
        return via, (m.group(3) or "").lower() or None, "block"
    if m.group(4):
        method = (m.group(5) or "").lower()
        via = {"entityinteracted": "entity", "foodeaten": "food"}.get(method, "right_click")
        return via, (m.group(6) or "").lower() or None, "entity" if via == "entity" else "item"
    ev = (m.group(7) or "").lower()
    via = {
        "block.left_click": "left_click",
        "block.break": "break",
        "item.entity_interact": "entity",
        "item.food_eaten": "food",
    }.get(ev, "right_click")
    pref = "block" if ev.startswith("block.") else ("entity" if "entity" in ev else "item")
    return via, None, pref


def parse(text: str) -> list[str]:
    out = []
    for m in HEADER.finditer(text or ""):
        via, filter_id, pref = classify(m)
        body = extract_body(text, m.end())
        hm, bm, em = HELD.search(body), BLOCK.search(body), ENTITY.search(body)
        held = hm.group(1).lower() if hm else None
        block = bm.group(1).lower() if bm else None
        entity = em.group(1).lower() if em else None
        if filter_id:
            if pref == "block" and not block:
                block = filter_id
            elif pref == "entity" and not entity:
                entity = filter_id
            elif pref == "item" and not held:
                held = filter_id
        if entity and (pref == "entity" or not block):
            target_key, target = "entity", entity
        elif block:
            target_key, target = "block", block
        else:
            target_key, target = "block", None
        results = []
        for gm in GIVE.finditer(body):
            rid = gm.group(1).lower()
            if rid not in results:
                results.append(rid)
        if not results:
            continue
        for result in results:
            if result == held:
                continue
            if result == target and via != "break":
                continue
            if target and held:
                out.append(
                    f"item:{result} -[right_click]-> held:{held} + {target_key}:{target} + via:{via}"
                )
            elif target:
                out.append(
                    f"item:{result} -[right_click]-> held:_ + {target_key}:{target} + via:{via}"
                )
            elif held:
                out.append(f"item:{result} -[right_click]-> held:{held} + block:_ + via:{via}")
    return out


def main() -> None:
    dirt = """
BlockEvents.rightClicked('minecraft:dirt', event => {
  if (event.item.id == 'minecraft:stick') {
    event.player.give('minecraft:diamond')
  }
})
"""
    assert any("via:right_click" in f and "diamond" in f and "stick" in f for f in parse(dirt)), parse(dirt)

    milk = """
ItemEvents.entityInteracted('minecraft:bucket', event => {
  if (event.target.type == 'minecraft:cow') {
    event.player.giveInHand('minecraft:milk_bucket')
  }
})
"""
    facts = parse(milk)
    assert any("milk_bucket" in f and "entity:minecraft:cow" in f and "via:entity" in f for f in facts), facts

    legacy = """
onEvent('block.right_click', event => {
  if (event.block.id == 'minecraft:stone' && event.item.id == 'minecraft:flint') {
    event.player.give('minecraft:iron_nugget')
  }
})
"""
    assert any("iron_nugget" in f and "via:right_click" in f for f in parse(legacy)), parse(legacy)

    broken = """
BlockEvents.broken('minecraft:spawner', event => {
  event.block.popItem('minecraft:spawner')
})
"""
    assert any("via:break" in f and "spawner" in f for f in parse(broken)), parse(broken)

    print("ok script_interact_multi")


if __name__ == "__main__":
    main()
