#!/usr/bin/env python3
"""Mirrors JarLightIndex recipe/loot path + compact fact parsing (no zip)."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/JarLightIndex.java"


def _read_java_int(name: str) -> int:
    text = JAVA.read_text(encoding="utf-8")
    m = re.search(rf"static final int {re.escape(name)} = (\d+);", text)
    assert m, f"missing {name} in {JAVA}"
    return int(m.group(1))


MAX_INGS = 6
MAX_FACTS_PER_ITEM = _read_java_int("MAX_FACTS_PER_ITEM")
MAX_LOOT_ROUTES_PER_ITEM = _read_java_int("MAX_LOOT_ROUTES_PER_ITEM")
CACHE_SCHEMA = _read_java_int("CACHE_SCHEMA")


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
    # L| must never go through add_fact (loot lives in lootRoutes)
    assert not fact.startswith("L|"), f"L| must not use add_fact: {fact}"
    iid = item_id.lower().strip()
    lst = out.setdefault(iid, [])
    if len(lst) >= MAX_FACTS_PER_ITEM or fact in lst:
        return
    lst.append(fact)


def add_loot_route(out: dict[str, list[str]], item_id: str, table_path: str) -> None:
    if not item_id or not table_path:
        return
    iid = item_id.lower().strip()
    lst = out.setdefault(iid, [])
    if len(lst) >= MAX_LOOT_ROUTES_PER_ITEM or table_path in lst:
        return
    lst.append(table_path)


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


def is_trivial_block_self_loot(item_id: str, loot_table_id_or_key: str) -> bool:
    if not item_id or not loot_table_id_or_key or ":" not in item_id:
        return False
    item_path = item_id.lower().split(":", 1)[1]
    loot = loot_table_id_or_key.lower().strip()
    loot_path = loot.split(":", 1)[1] if ":" in loot else loot
    return loot_path == f"blocks/{item_path}"


def _accept_loot_item_entry(
    o: dict, table_key: str, seen: set[str], loot_out: dict[str, list[str]]
) -> None:
    if len(seen) >= 40 or "name" not in o:
        return
    typ = o.get("type")
    if typ is not None:
        tl = str(typ).lower()
        if tl in ("minecraft:loot_table", "loot_table"):
            return
        if tl not in ("minecraft:item", "item"):
            return
    iid = extract_item_id(o.get("name"))
    if not iid or iid in seen or len(seen) >= 40:
        return
    seen.add(iid)
    if is_trivial_block_self_loot(iid, table_key):
        return
    add_loot_route(loot_out, iid, table_key)


def _walk_loot_node(
    o: dict, table_key: str, seen: set[str], loot_out: dict[str, list[str]]
) -> None:
    if len(seen) >= 40:
        return
    for key in ("pools", "entries", "children"):
        if key in o:
            _walk_loot_element(o[key], table_key, seen, loot_out)
    _accept_loot_item_entry(o, table_key, seen, loot_out)


def _walk_loot_element(
    el, table_key: str, seen: set[str], loot_out: dict[str, list[str]]
) -> None:
    if el is None or len(seen) >= 40:
        return
    if isinstance(el, list):
        for x in el:
            _walk_loot_element(x, table_key, seen, loot_out)
        return
    if isinstance(el, dict):
        _walk_loot_node(el, table_key, seen, loot_out)


def parse_loot_json(path_lower: str, obj: dict, loot_out: dict[str, list[str]]) -> None:
    """Structural loot parse → loot_out (paths, no L|). Never calls add_fact."""
    key = loot_key_from_path(path_lower)
    seen: set[str] = set()
    _walk_loot_node(obj, key, seen, loot_out)


def route_lines_for_item(
    by_item: dict[str, list[str]], loot_routes: dict[str, list[str]], item_id: str
) -> list[str]:
    """Mirror: L from lootRoutes first, then R|, then U| from byItem."""
    iid = item_id.lower().strip()
    out: list[str] = []
    for path in loot_routes.get(iid, []):
        out.append(f"L|{path}")
    raw = by_item.get(iid, [])
    for code in raw:
        if code and len(code) >= 3 and code[1] == "|" and code[0] == "R":
            out.append(code)
    for code in raw:
        if code and len(code) >= 3 and code[1] == "|" and code[0] == "U":
            out.append(code)
    return out


def main() -> None:
    assert CACHE_SCHEMA == 2, CACHE_SCHEMA
    assert MAX_FACTS_PER_ITEM == 8, MAX_FACTS_PER_ITEM
    assert MAX_LOOT_ROUTES_PER_ITEM == 6, MAX_LOOT_ROUTES_PER_ITEM

    java = JAVA.read_text(encoding="utf-8")
    assert 'jar.cache.schema {} -> {}, rescan' in java
    assert 'jar.loot.capped item={} dropped={}' in java
    assert "lootRoutes" in java
    assert "routeLinesForItem" in java
    assert "ConcurrentHashMap<String, List<String>> lootRoutes" in java
    # L| must not be written via addFact (loot uses addLootRoute / lootRoutes)
    assert 'addFact(out, id, "L|' not in java
    assert "addLootRoute" in java
    assert "parseLootJson" in java and "lootOut" in java
    assert "mergeLoot" in java

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
        {
            "pools": [
                {
                    "entries": [
                        {"type": "minecraft:item", "name": "minecraft:iron_ingot"},
                        {
                            "type": "minecraft:loot_table",
                            "name": "minecraft:chests/abandoned_mineshaft",
                        },
                    ]
                }
            ]
        },
        loot_out,
    )
    assert loot_out["minecraft:iron_ingot"] == ["chests/simple_dungeon"]
    assert "minecraft:chests/abandoned_mineshaft" not in loot_out
    assert "chests/abandoned_mineshaft" not in {
        p for paths in loot_out.values() for p in paths
    }

    # no explicit type on item entry still accepted
    bare: dict[str, list[str]] = {}
    parse_loot_json(
        "data/minecraft/loot_tables/chests/simple_dungeon.json",
        {"pools": [{"entries": [{"name": "minecraft:gold_ingot"}]}]},
        bare,
    )
    assert bare["minecraft:gold_ingot"] == ["chests/simple_dungeon"]

    trivial_out: dict[str, list[str]] = {}
    parse_loot_json(
        "data/ars_nouveau/loot_tables/blocks/relay_deposit.json",
        {
            "pools": [
                {
                    "entries": [
                        {"type": "minecraft:item", "name": "ars_nouveau:relay_deposit"}
                    ]
                }
            ]
        },
        trivial_out,
    )
    assert "ars_nouveau:relay_deposit" not in trivial_out

    # route merge: loot-only item still non-empty; order L then R then U
    by_item = {
        "mod:a": ["R|shaped|x", "U|shaped|mod:a"],
    }
    loot_routes = {"mod:a": ["chests/x"], "mod:loot_only": ["entities/foo"]}
    routes_a = route_lines_for_item(by_item, loot_routes, "mod:a")
    assert routes_a == [
        "L|chests/x",
        "R|shaped|x",
        "U|shaped|mod:a",
    ], routes_a
    routes_lo = route_lines_for_item(by_item, loot_routes, "mod:loot_only")
    assert routes_lo == ["L|entities/foo"], routes_lo

    print("check_jar_light_index OK")


if __name__ == "__main__":
    main()
