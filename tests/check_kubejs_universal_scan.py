#!/usr/bin/env python3
"""Mirror PackIndex #5 universal KubeJS scan: LootJS, food.eaten, interactConditions."""

from __future__ import annotations

import re

LOOTJS_MARK = re.compile(r"LootJS\.modifiers\s*\(", re.I)
LOOTJS_ENTITY_MOD = re.compile(
    r"\.addEntityLootModifier\s*\(\s*['\"]([a-z0-9_.:/-]+)['\"]", re.I
)
LOOTJS_TABLE_MOD = re.compile(
    r"\.addLootTableModifier\s*\(\s*['\"]([a-z0-9_.:/-]+)['\"]", re.I
)
LOOTJS_ENTRY = re.compile(
    r"(?:LootEntry\.of|\.addLoot|addLoot)\s*\(\s*['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
    re.I,
)
ITEM_CREATE = re.compile(r"\.create\(\s*['\"]([a-z0-9_.:/-]+)['\"]\s*\)", re.I)
CREATE_USE_HOOK = re.compile(r"\.(finishUsing|useDuration|use)\s*\(", re.I)
CREATE_FOOD_EATEN = re.compile(r"\.eaten\s*\(", re.I)
GIVE = re.compile(
    r"(?:\.give|giveInHand|addItem|popItem)\s*\(\s*(?:Item\.of\()?['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
    re.I,
)
IF_STAGE = re.compile(r"stages\.has\(\s*['\"]([^'\"]+)['\"]", re.I)
IF_DIM = re.compile(
    r"(?:\.dimension(?:Key)?|getDimension(?:Key)?)\s*"
    r"(?:[=!]=|\.equals\(|\.toString\s*\(\s*\)\s*[=!]=|[\s\S]{0,40}?)"
    r"['\"]([a-z0-9_.:/-]+)['\"]",
    re.I,
)
IF_NIGHT = re.compile(r"(?:\.isNight\s*\(|!\s*[\w.]*\.isDay\s*\()", re.I)
IF_NBT = re.compile(
    r"(?:persistentData\.(?:get|contains|put)|getOrCreateTag\s*\(|\.nbt\.|hasNBT\s*\()",
    re.I,
)


def resolve_create_id(raw: str) -> str:
    s = raw.lower().strip()
    return s if ":" in s else f"kubejs:{s}"


def parse_lootjs(text: str) -> list[str]:
    if not text or not LOOTJS_MARK.search(text):
        return []
    hits: list[tuple[int, str, str]] = []
    for m in LOOTJS_ENTITY_MOD.finditer(text):
        hits.append((m.start(), "entity", m.group(1).lower()))
    for m in LOOTJS_TABLE_MOD.finditer(text):
        hits.append((m.start(), "table", m.group(1).lower()))
    for m in LOOTJS_ENTRY.finditer(text):
        hits.append((m.start(), "item", m.group(1).lower()))
    hits.sort(key=lambda h: h[0])
    out: list[str] = []
    seen: set[str] = set()
    source = ""
    for _, kind, val in hits:
        if kind in ("entity", "table"):
            source = f"{kind}:{val}"
            continue
        line = f"item:{val} -[loot]-> via:lootjs"
        if source:
            line += f" + {source}"
        if line not in seen:
            seen.add(line)
            out.append(line)
    return out


def parse_food_eaten(text: str) -> list[str]:
    out: list[str] = []
    for m in ITEM_CREATE.finditer(text or ""):
        raw = m.group(1).lower()
        if "/" in raw:
            continue
        item_id = resolve_create_id(raw)
        from_ = m.end()
        to = min(len(text), from_ + 2500)
        nxt = ITEM_CREATE.search(text, from_)
        if nxt and nxt.start() < to:
            to = nxt.start()
        chain = text[from_:to]
        food_eaten = ".food" in chain.lower() and CREATE_FOOD_EATEN.search(chain)
        use_hook = CREATE_USE_HOOK.search(chain)
        if not food_eaten and not use_hook:
            continue
        if not food_eaten:
            continue
        via = "food_eaten"
        results = [gm.group(1).lower() for gm in GIVE.finditer(chain)]
        if not results:
            out.append(f"item:{item_id} -[script_use]-> via:{via}")
        else:
            for r in results:
                if r != item_id:
                    out.append(f"item:{item_id} -[script_use]-> via:{via} + gets:{r}")
    return out


def interact_conditions(body: str) -> str:
    parts: list[str] = []
    for m in IF_STAGE.finditer(body or ""):
        parts.append(f"if:stage:{m.group(1).lower()}")
        if len(parts) >= 2:
            break
    for m in IF_DIM.finditer(body or ""):
        parts.append(f"if:dim:{m.group(1).lower()}")
        break
    if IF_NIGHT.search(body or ""):
        parts.append("if:night")
    if IF_NBT.search(body or ""):
        parts.append("if:nbt")
    return " + ".join(parts)


def main() -> None:
    loot = """
LootJS.modifiers(event => {
  event.addEntityLootModifier('minecraft:slime')
    .addLoot(LootEntry.of('kubejs:mini_slime'));
  event.addLootTableModifier('dnl:entity/bee_swarm/reward')
    .addLoot(LootEntry.of('kubejs:candy_pancreas'));
  event.addEntityLootModifier('goety:apostle')
    .addLoot('kubejs:pandora_inactive');
})
"""
    facts = parse_lootjs(loot)
    assert any(
        "kubejs:mini_slime" in f and "-[loot]->" in f and "entity:minecraft:slime" in f
        for f in facts
    ), facts
    assert any("kubejs:candy_pancreas" in f and "table:dnl:entity/bee_swarm/reward" in f for f in facts), facts
    assert any("kubejs:pandora_inactive" in f and "via:lootjs" in f for f in facts), facts
    # Negative: no LootJS → empty
    assert parse_lootjs("ItemEvents.rightClicked('x', e => {})") == []

    eaten = """
event.create('lucky_cookie').food(food => {
  food.hunger(1).eaten(event => {
    event.player.give(Item.of('kubejs:lucky_cookie_organ'))
  })
})
event.create('active_pill').food(food => {
  food.eaten(ctx => { ctx.player.persistentData.putInt('organActive', 1) })
})
"""
    ef = parse_food_eaten(eaten)
    assert any(
        "item:kubejs:lucky_cookie" in f and "script_use" in f and "via:food_eaten" in f
        and "gets:kubejs:lucky_cookie_organ" in f
        for f in ef
    ), ef
    assert any(
        "item:kubejs:active_pill" in f and "via:food_eaten" in f and "script_use" in f
        for f in ef
    ), ef
    # Negative: create without eaten
    assert parse_food_eaten("event.create('scrap').texture('x')") == []

    cond = interact_conditions(
        """
        if (event.player.stages.has('bronze') && event.level.isNight()
            && event.level.dimension == 'minecraft:the_nether'
            && event.player.persistentData.contains('flag')) {}
        """
    )
    assert "if:stage:bronze" in cond, cond
    assert "if:night" in cond, cond
    assert "if:dim:minecraft:the_nether" in cond, cond
    assert "if:nbt" in cond, cond
    # Negative: empty body
    assert interact_conditions("event.player.give('minecraft:dirt')") == ""

    print("ok kubejs_universal_scan")


if __name__ == "__main__":
    main()
