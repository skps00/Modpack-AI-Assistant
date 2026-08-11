#!/usr/bin/env python3
"""Mirror LootForwardIndex #5b — Gateways + loot JSON forward index (no invent)."""

from __future__ import annotations

import re

LOOT_ITEM_NAME = re.compile(r'"name"\s*:\s*"([a-z0-9_]+:[a-z0-9_./-]+)"', re.I)
LOOT_TABLE_FIELD = re.compile(r'"loot_table"\s*:\s*"([a-z0-9_]+:[a-z0-9_./-]+)"', re.I)
GATEWAY_LOOT_TYPE = re.compile(r'"type"\s*:\s*"(?:gateways:)?loot_table"', re.I)
GATEWAY_ENTITY_LOOT_TYPE = re.compile(r'"type"\s*:\s*"(?:gateways:)?entity_loot"', re.I)
GATEWAY_STACK_TYPE = re.compile(r'"type"\s*:\s*"(?:gateways:)?stack"', re.I)
GATEWAY_STACK_LIST_TYPE = re.compile(r'"type"\s*:\s*"(?:gateways:)?stack_list"', re.I)
STACK_ITEM_FIELD = re.compile(r'"item"\s*:\s*"([a-z0-9_]+:[a-z0-9_./-]+)"', re.I)
ENTITY_FIELD = re.compile(r'"entity"\s*:\s*"([a-z0-9_]+:[a-z0-9_./-]+)"', re.I)
ADD_JSON_LOOT = re.compile(
    r"addJson\(\s*[`'\"]([a-z0-9_]+):loot_tables/([a-z0-9_./-]+)\.json[`'\"]",
    re.I,
)


def entity_loot_table_id(entity_id: str) -> str:
    if not entity_id or ":" not in entity_id:
        return ""
    ns, path = entity_id.lower().split(":", 1)
    return f"{ns}:entities/{path}"


def loot_table_id_from_path(rel: str) -> str:
    m = re.search(r"(?:^|/)data/([a-z0-9_]+)/loot_tables?/(.+?)\.json$", rel.replace("\\", "/").lower())
    return f"{m.group(1)}:{m.group(2)}" if m else ""


def gateway_id_from_path(rel: str) -> str:
    m = re.search(r"(?:^|/)data/([a-z0-9_]+)/gateways/(.+?)\.json$", rel.replace("\\", "/").lower())
    return f"{m.group(1)}:{m.group(2)}" if m else ""


def items_from_loot_text(text: str) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for m in LOOT_ITEM_NAME.finditer(text or ""):
        i = m.group(1).lower()
        if i not in seen:
            seen.add(i)
            out.append(i)
    return out


def parse_facts(rel: str, text: str) -> list[str]:
    facts: list[str] = []
    seen: set[str] = set()

    def add(f: str) -> None:
        if f not in seen:
            seen.add(f)
            facts.append(f)

    table_path = loot_table_id_from_path(rel)
    if table_path:
        for item in items_from_loot_text(text):
            add(f"loot_table:{table_path} -[contains]-> item:{item}")
            add(f"item:{item} -[loot]-> table:{table_path}")
    else:
        for m in ADD_JSON_LOOT.finditer(text or ""):
            table_id = f"{m.group(1).lower()}:{m.group(2).lower()}"
            from_ = m.end()
            to = min(len(text), from_ + 4000)
            nxt = ADD_JSON_LOOT.search(text, from_)
            if nxt and nxt.start() < to:
                to = nxt.start()
            for item in items_from_loot_text(text[from_:to]):
                add(f"loot_table:{table_id} -[contains]-> item:{item}")
                add(f"item:{item} -[loot]-> table:{table_id}")

    gw = gateway_id_from_path(rel)
    if GATEWAY_LOOT_TYPE.search(text or ""):
        for m in LOOT_TABLE_FIELD.finditer(text):
            table = m.group(1).lower()
            if gw:
                add(f"gateway:{gw} -[reward_loot]-> loot_table:{table}")
            add(f"loot_table:{table} -[gateway_reward]-> true")

    if GATEWAY_ENTITY_LOOT_TYPE.search(text or ""):
        for tm in GATEWAY_ENTITY_LOOT_TYPE.finditer(text):
            window = text[max(0, tm.start() - 200) : min(len(text), tm.end() + 200)]
            for em in ENTITY_FIELD.finditer(window):
                ent = em.group(1).lower()
                table = entity_loot_table_id(ent)
                if not table:
                    continue
                if gw:
                    add(
                        f"gateway:{gw} -[reward_loot]-> loot_table:{table} + entity_loot:{ent}"
                    )
                add(f"loot_table:{table} -[entity_loot_of]-> entity:{ent}")

    # stack / stack_list → item:X -[loot]-> gateway:…
    if gw:
        stack_items: list[str] = []
        seen_items: set[str] = set()

        def take_items(window: str, limit: int) -> None:
            for im in STACK_ITEM_FIELD.finditer(window):
                if len(stack_items) >= limit:
                    return
                i = im.group(1).lower()
                if i not in seen_items:
                    seen_items.add(i)
                    stack_items.append(i)

        for tm in GATEWAY_STACK_TYPE.finditer(text or ""):
            # pattern requires closing quote after stack — does not match stack_list
            take_items(text[tm.start() : min(len(text), tm.end() + 350)], 2)
        for tm in GATEWAY_STACK_LIST_TYPE.finditer(text or ""):
            take_items(text[tm.start() : min(len(text), tm.end() + 2500)], 40)
        for item in stack_items:
            add(f"item:{item} -[loot]-> gateway:{gw}")
            add(f"gateway:{gw} -[reward_stack]-> item:{item}")
            add(f"item:gateways:gate_pearl -[opens]-> gateway:{gw}")
            add(f"gateway:{gw} -[gate_pearl]-> item:gateways:gate_pearl")

    # Item.of('gateways:gate_pearl', '{gateway:"…"}') / nearby gateway NBT
    for m in re.finditer(
        r"(?i)gateways:gate_pearl.{0,120}?gateway\s*[:=]\s*\\?[\"']([a-z0-9_]+:[a-z0-9_./-]+)[\"']",
        text or "",
    ):
        pgw = m.group(1).lower()
        add(f"item:gateways:gate_pearl -[opens]-> gateway:{pgw}")
        add(f"gateway:{pgw} -[gate_pearl]-> item:gateways:gate_pearl")

    return facts


def main() -> None:
    assert entity_loot_table_id("minecraft:slime") == "minecraft:entities/slime"
    assert loot_table_id_from_path("kubejs/data/foo/loot_tables/bar/baz.json") == "foo:bar/baz"
    assert gateway_id_from_path("data/gateways/gateways/slime_gate.json") == "gateways:slime_gate"

    loot_js = """
ServerEvents.highPriorityData(event => {
  event.addJson(`mrqx_extra_pack:loot_tables/seaborn_organs.json`, {
    "pools": [{ "entries": [
      { "name": "mrqx_extra_pack:heart_tidal_elegy", "type": "item" },
      { "name": "mrqx_extra_pack:lung_the_tide", "type": "item" }
    ]}]
  })
})
"""
    lf = parse_facts("kubejs/server_scripts/loot.js", loot_js)
    assert any(
        "loot_table:mrqx_extra_pack:seaborn_organs -[contains]-> item:mrqx_extra_pack:heart_tidal_elegy" in f
        for f in lf
    ), lf
    assert any("item:mrqx_extra_pack:lung_the_tide -[loot]-> table:mrqx_extra_pack:seaborn_organs" in f for f in lf), lf

    gw = """
{
  "rewards": [{
    "desc": "deep",
    "loot_table": "mrqx_extra_pack:seaborn_organs",
    "rolls": 4,
    "type": "gateways:loot_table"
  }]
}
"""
    gf = parse_facts("kubejs/data/mrqx_extra_pack/gateways/mrqx_ocean.json", gw)
    assert any(
        "gateway:mrqx_extra_pack:mrqx_ocean -[reward_loot]-> loot_table:mrqx_extra_pack:seaborn_organs" in f
        for f in gf
    ), gf

    slime = """
{
  "rewards": [{
    "type": "entity_loot",
    "entity": "minecraft:slime",
    "rolls": 5
  }]
}
"""
    sf = parse_facts("data/gateways/gateways/slime_gate.json", slime)
    assert any(
        "entity_loot:minecraft:slime" in f and "loot_table:minecraft:entities/slime" in f
        for f in sf
    ), sf
    # Negative: no invent — entity_loot alone does not fabricate slime_ball
    assert not any("slime_ball" in f for f in sf), sf
    assert parse_facts("x.js", "ItemEvents.rightClicked('a', e => {})") == []

    # NFWC drowning: gateways:stack → b_a_d:friend (挚友)
    drowning = """
{
  "waves": [{ "entities": [{ "entity": "biomancy:hungry_flesh_blob" }], "rewards": [] }],
  "rewards": [{
    "type": "gateways:stack",
    "stack": { "item": "b_a_d:friend", "count": 1, "nbt": {} }
  }]
}
"""
    df = parse_facts("kubejs/data/kubejs/gateways/b_a_d/drowning.json", drowning)
    assert any(
        "item:b_a_d:friend -[loot]-> gateway:kubejs:b_a_d/drowning" in f for f in df
    ), df
    assert any(
        "gateway:kubejs:b_a_d/drowning -[reward_stack]-> item:b_a_d:friend" in f for f in df
    ), df
    assert any(
        "item:gateways:gate_pearl -[opens]-> gateway:kubejs:b_a_d/drowning" in f for f in df
    ), df
    # wave entity must not become a stack reward
    assert not any("hungry_flesh_blob" in f and "reward_stack" in f for f in df), df

    pearl_js = """
Item.of('gateways:gate_pearl', '{gateway:\"kubejs:b_a_d/drowning\"}')
"""
    pf = parse_facts("kubejs/server_scripts/ritual.js", pearl_js)
    assert any(
        "item:gateways:gate_pearl -[opens]-> gateway:kubejs:b_a_d/drowning" in f for f in pf
    ), pf

    stack_list = """
{
  "rewards": [{
    "type": "stack_list",
    "stacks": [
      { "item": "minecraft:diamond", "count": 1 },
      { "item": "minecraft:emerald", "count": 2 }
    ]
  }]
}
"""
    sl = parse_facts("data/kubejs/gateways/demo.json", stack_list)
    assert any("item:minecraft:diamond -[loot]-> gateway:kubejs:demo" in f for f in sl), sl
    assert any("item:minecraft:emerald -[loot]-> gateway:kubejs:demo" in f for f in sl), sl

    print("ok loot_forward_index")


if __name__ == "__main__":
    main()
