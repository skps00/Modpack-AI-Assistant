#!/usr/bin/env python3
"""Mirrors JarLightIndex recipe/loot path + compact fact parsing (no zip)."""

from __future__ import annotations


MAX_INGS = 6
MAX_FACTS_PER_ITEM = 8


def is_recipe_entry(path_lower: str) -> bool:
    return (
        path_lower.startswith("data/")
        and "/recipes/" in path_lower
        and path_lower.endswith(".json")
        and "/advancements/" not in path_lower
    )


def is_loot_entry(path_lower: str) -> bool:
    return (
        path_lower.startswith("data/")
        and "/loot_tables/" in path_lower
        and path_lower.endswith(".json")
    )


def loot_key_from_path(path_lower: str) -> str:
    i = path_lower.find("/loot_tables/")
    if i < 0:
        return path_lower
    rest = path_lower[i + len("/loot_tables/") :]
    if rest.endswith(".json"):
        rest = rest[: -len(".json")]
    return rest


def short_type(typ: str) -> str:
    if not typ:
        return "recipe"
    colon = typ.find(":")
    s = typ[colon + 1 :] if colon >= 0 else typ
    return s[:24]


def extract_item_id(el) -> str | None:
    if el is None:
        return None
    if isinstance(el, str):
        return el.lower() if ":" in el else None
    if isinstance(el, dict):
        if "item" in el:
            return extract_item_id(el["item"])
        if "id" in el:
            return extract_item_id(el["id"])
    return None


def collect_items(el, out: set[str]) -> None:
    if el is None or len(out) >= 40:
        return
    if isinstance(el, str):
        if ":" in el and not el.startswith("#"):
            out.add(el.lower())
        return
    if isinstance(el, list):
        for x in el:
            collect_items(x, out)
        return
    if isinstance(el, dict):
        if "item" in el:
            collect_items(el["item"], out)
        elif "id" in el:
            collect_items(el["id"], out)
        elif "tag" in el:
            return
        else:
            for k, v in el.items():
                if k in ("count", "nbt", "components"):
                    continue
                collect_items(v, out)


def add_fact(out: dict[str, list[str]], item_id: str, fact: str) -> None:
    if not item_id or not fact:
        return
    iid = item_id.lower().strip()
    lst = out.setdefault(iid, [])
    if len(lst) >= MAX_FACTS_PER_ITEM or fact in lst:
        return
    lst.append(fact)


def parse_recipe_json(obj: dict, out: dict[str, list[str]]) -> None:
    typ = short_type(str(obj.get("type") or "recipe"))
    result = extract_item_id(obj.get("result")) or extract_item_id(obj.get("output"))
    if not result:
        return
    ings: set[str] = set()
    collect_items(obj.get("ingredient"), ings)
    collect_items(obj.get("ingredients"), ings)
    collect_items(obj.get("key"), ings)
    collect_items(obj.get("input"), ings)
    collect_items(obj.get("inputs"), ings)
    ing_list = list(ings)[:MAX_INGS]
    add_fact(out, result, f"R|{typ}|{','.join(ing_list)}")
    for u, ing in enumerate(ing_list):
        if u >= MAX_INGS or ing == result:
            continue
        add_fact(out, ing, f"U|{typ}|{result}")


def parse_loot_json(path_lower: str, text: str, out: dict[str, list[str]]) -> None:
    import re

    key = loot_key_from_path(path_lower)
    seen: set[str] = set()
    for m in re.finditer(r'"([a-z0-9_]+:[a-z0-9_./-]+)"', text, re.I):
        iid = m.group(1).lower()
        if iid in seen or len(seen) >= 40:
            continue
        seen.add(iid)
        add_fact(out, iid, f"L|{key}")


def main() -> None:
    assert is_recipe_entry("data/minecraft/recipes/stick.json")
    assert not is_recipe_entry("data/minecraft/advancements/recipes/foo.json")
    assert is_loot_entry("data/minecraft/loot_tables/chests/village_toolsmith.json")
    assert loot_key_from_path("data/minecraft/loot_tables/chests/village_toolsmith.json") == (
        "chests/village_toolsmith"
    )

    out: dict[str, list[str]] = {}
    parse_recipe_json(
        {
            "type": "minecraft:crafting_shaped",
            "result": "minecraft:stick",
            "key": {"X": {"item": "minecraft:bamboo"}},
        },
        out,
    )
    assert any(f.startswith("R|crafting_shaped|") for f in out["minecraft:stick"])
    assert any(f.startswith("U|crafting_shaped|minecraft:stick") for f in out["minecraft:bamboo"])

    loot_out: dict[str, list[str]] = {}
    parse_loot_json(
        "data/minecraft/loot_tables/chests/simple_dungeon.json",
        '{"pools":[{"entries":[{"type":"item","name":"minecraft:iron_ingot"}]}]}',
        loot_out,
    )
    assert "L|chests/simple_dungeon" in loot_out["minecraft:iron_ingot"]

    print("check_jar_light_index OK")


if __name__ == "__main__":
    main()
