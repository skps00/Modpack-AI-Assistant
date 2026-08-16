#!/usr/bin/env python3
"""Mirrors WorldgenFacts parse + format. Fixture JSON → edges; no invented Y."""

from __future__ import annotations

import json
import re
from typing import Any

HEADER = "[WORLDGEN]"

BIOME_JSON = '{"features":[[],["minecraft:ore_iron"],["minecraft:trees_plains"]]}'
STRUCTURE_JSON = (
    '{"type":"minecraft:jigsaw","biomes":"#minecraft:has_structure/village_plains"}'
)
STRUCTURE_NO_BIOMES = '{"type":"minecraft:jigsaw"}'
STRUCTURE_SET_JSON = (
    '{"structures":[{"structure":"minecraft:village_plains","weight":1}],'
    '"placement":{"type":"minecraft:random_spread","spacing":34,"separation":8}}'
)
ORE_CONFIGURED = '{"type":"minecraft:ore","config":{"size":9}}'
GEODE_NO_SIZE = '{"type":"minecraft:geode","config":{}}'
PLACED_HEIGHT = (
    '{"feature":"minecraft:ore_iron","placement":['
    '{"type":"minecraft:count","count":10},'
    '{"type":"minecraft:height_range","height":{'
    '"type":"minecraft:trapezoid",'
    '"min_inclusive":{"absolute":-24},'
    '"max_inclusive":{"absolute":56}}}]}'
)
PLACED_NO_HEIGHT = (
    '{"feature":"minecraft:ore_iron","placement":[{"type":"minecraft:count","count":4}]}'
)
MODIFIER_JSON = (
    '{"type":"forge:add_features","biomes":"minecraft:plains",'
    '"features":"mod:extra_ore","step":"underground_ores"}'
)
TAG_JSON = '{"values":["minecraft:plains","minecraft:meadow"]}'

Y_INVENTED = re.compile(r"Y\s*=")


def _data_rest(path: str) -> str | None:
    p = path.replace("\\", "/").lower()
    if p.startswith("data/"):
        start = len("data/")
    else:
        i = p.find("/data/")
        if i < 0:
            return None
        start = i + len("/data/")
    return p[start:] if start < len(p) else None


def kind_from_path(path: str) -> str | None:
    rest = _data_rest(path)
    if rest is None:
        return None
    slash = rest.find("/")
    if slash <= 0:
        return None
    after = rest[slash + 1 :]
    if after.startswith("tags/worldgen/") and after.endswith(".json"):
        return "tag"
    if after.startswith("worldgen/structure_set/") and after.endswith(".json"):
        return "structure_set"
    if after.startswith("worldgen/structure/") and after.endswith(".json"):
        return "structure"
    if after.startswith("worldgen/configured_feature/") and after.endswith(".json"):
        return "configured"
    if after.startswith("worldgen/placed_feature/") and after.endswith(".json"):
        return "placed"
    if after.startswith("worldgen/biome/") and after.endswith(".json"):
        return "biome"
    if after.endswith(".json") and (
        after.startswith("forge/biome_modifier/")
        or after.startswith("neoforge/biome_modifier/")
        or after.startswith("biome_modifier/")
        or after.startswith("biome_modifiers/")
    ):
        return "modifier"
    return None


def id_from_path(path: str) -> str:
    kind = kind_from_path(path)
    rest = _data_rest(path)
    if not kind or rest is None:
        return ""
    slash = rest.find("/")
    ns = rest[:slash]
    after = rest[slash + 1 :]
    folders = {
        "biome": "worldgen/biome/",
        "structure": "worldgen/structure/",
        "structure_set": "worldgen/structure_set/",
        "configured": "worldgen/configured_feature/",
        "placed": "worldgen/placed_feature/",
    }
    if kind == "tag":
        rest2 = after[len("tags/worldgen/") :]
        sl = rest2.find("/")
        if sl <= 0:
            return ""
        stem = rest2[sl + 1 :]
    elif kind == "modifier":
        for pre in (
            "forge/biome_modifier/",
            "neoforge/biome_modifier/",
            "biome_modifier/",
            "biome_modifiers/",
        ):
            if after.startswith(pre):
                stem = after[len(pre) :]
                break
        else:
            return ""
    else:
        stem = after[len(folders[kind]) :]
    if not stem.endswith(".json") or len(stem) <= 5:
        return ""
    stem = stem[: -len(".json")]
    ident = f"{ns}:{stem}"
    return f"#{ident}" if kind == "tag" else ident


def normalize_id(raw: str | None) -> str | None:
    if raw is None:
        return None
    s = str(raw).strip()
    if not s:
        return None
    tag = s.startswith("#")
    if tag:
        s = s[1:]
    s = s.lower()
    return f"#{s}" if tag else s


def collect_ids(el: Any, out: list[str], cap: int = 64) -> None:
    if el is None or len(out) >= cap:
        return
    if isinstance(el, str):
        s = normalize_id(el)
        if s and s not in out:
            out.append(s)
        return
    if isinstance(el, list):
        for x in el:
            collect_ids(x, out, cap)
        return
    if isinstance(el, dict):
        if "feature" in el:
            collect_ids(el["feature"], out, cap)
        elif "id" in el and "type" not in el:
            collect_ids(el["id"], out, cap)


def parse_biomes_field(obj: dict) -> str | None:
    if "biomes" not in obj:
        return None
    el = obj["biomes"]
    if isinstance(el, str):
        return normalize_id(el)
    if isinstance(el, list):
        ids: list[str] = []
        collect_ids(el, ids, 32)
        return ",".join(ids) if ids else None
    return None


def _short_type(typ: str | None) -> str:
    if not typ:
        return ""
    t = str(typ).lower()
    return t.split(":", 1)[-1]


def _int_or_none(obj: dict, key: str) -> int | None:
    v = obj.get(key)
    return int(v) if isinstance(v, (int, float)) and not isinstance(v, bool) else None


def _vertical_anchor(el: Any) -> str | None:
    if not isinstance(el, dict):
        return None
    for k in ("absolute", "above_bottom", "below_top"):
        if k in el and isinstance(el[k], (int, float)) and not isinstance(el[k], bool):
            n = el[k]
            return f"{k} {int(n) if float(n) == int(n) else n}"
    return None


def parse_placed(obj: dict) -> dict:
    configured = None
    feat = obj.get("feature")
    if isinstance(feat, str):
        configured = normalize_id(feat)
    count = None
    count_range = None
    height_range = None
    for mod in obj.get("placement") or []:
        if not isinstance(mod, dict):
            continue
        st = _short_type(mod.get("type"))
        if st == "count" and "count" in mod:
            c = mod["count"]
            if isinstance(c, (int, float)) and not isinstance(c, bool):
                count = int(c)
            elif isinstance(c, dict):
                src = c["value"] if isinstance(c.get("value"), dict) else c
                mn = _int_or_none(src, "min_inclusive")
                mx = _int_or_none(src, "max_inclusive")
                if mn is not None and mx is not None:
                    count_range = f"{mn}..{mx}"
                elif mn is not None:
                    count = mn
                elif mx is not None:
                    count = mx
        elif st == "height_range" and height_range is None:
            h = mod.get("height")
            if isinstance(h, dict):
                mn = _vertical_anchor(h.get("min_inclusive"))
                mx = _vertical_anchor(h.get("max_inclusive"))
                if mn and mx:
                    height_range = f"{mn}..{mx}"
                else:
                    height_range = mn or mx
    return {
        "configured": configured,
        "count": count,
        "count_range": count_range,
        "height_range": height_range,
    }


def parse_configured(obj: dict) -> dict:
    size = None
    cfg = obj.get("config")
    if isinstance(cfg, dict) and isinstance(cfg.get("size"), (int, float)):
        size = int(cfg["size"])
    return {"type": obj.get("type"), "size": size}


def parse_structure_set(obj: dict) -> dict:
    structs = []
    for el in obj.get("structures") or []:
        if isinstance(el, dict) and el.get("structure"):
            structs.append(normalize_id(el["structure"]))
    place = obj.get("placement") if isinstance(obj.get("placement"), dict) else {}
    return {
        "structures": structs,
        "spacing": _int_or_none(place, "spacing"),
        "separation": _int_or_none(place, "separation"),
    }


def parse_modifier(obj: dict) -> dict | None:
    typ = str(obj.get("type") or "")
    if "add_features" not in typ.lower():
        return None
    features: list[str] = []
    collect_ids(obj.get("features"), features, 32)
    return {"biomes": parse_biomes_field(obj), "features": features}


def parse_tag(obj: dict) -> list[str]:
    out: list[str] = []
    for el in obj.get("values") or []:
        if isinstance(el, str):
            s = normalize_id(el)
            if s:
                out.append(s)
        elif isinstance(el, dict) and el.get("id"):
            out.append(normalize_id(el["id"]))
    return out


def format_biome(ident: str, features: list[str]) -> str:
    line = f"{HEADER} biome {ident}"
    if features:
        line += " placed_feature=" + ",".join(features[:12])
    return line


def format_structure(ident: str, biomes: str | None) -> str:
    line = f"{HEADER} structure {ident}"
    if biomes:
        line += f" biomes={biomes}"
    return line


def format_structure_set(ident: str, rec: dict) -> str:
    line = f"{HEADER} structure_set {ident}"
    if rec["structures"]:
        line += " contains=" + ",".join(rec["structures"][:12])
    if rec["spacing"] is not None:
        line += f" spacing={rec['spacing']}"
    if rec["separation"] is not None:
        line += f" separation={rec['separation']}"
    return line


def format_configured(ident: str, rec: dict) -> str:
    line = f"{HEADER} configured_feature {ident}"
    if rec.get("type"):
        line += f" type={rec['type']}"
    if rec.get("size") is not None:
        line += f" size={rec['size']}"
    return line


def format_placed(ident: str, rec: dict) -> str:
    line = f"{HEADER} placed_feature {ident}"
    if rec.get("configured"):
        line += f" configured={rec['configured']}"
    if rec.get("count") is not None:
        line += f" count={rec['count']}"
    elif rec.get("count_range"):
        line += f" count={rec['count_range']}"
    if rec.get("height_range"):
        line += f" height_range={rec['height_range']}"
    return line


def format_modifier(rec: dict) -> str:
    line = f"{HEADER} biome_modifier"
    if rec.get("biomes"):
        line += f" biomes={rec['biomes']}"
    if rec.get("features"):
        line += " features=" + ",".join(rec["features"][:12])
    return line


def miss_line(query: str, lang: str) -> str:
    q = (query or "").strip()
    code = (lang or "").strip().lower().replace("-", "_")
    if not code or code.startswith("zh"):
        return f"{HEADER} 此包未索引到 {q} 的 worldgen"
    return f"{HEADER} this pack has no indexed worldgen for: {q}"


def _no_y(text: str) -> None:
    assert not Y_INVENTED.search(text), text


def main() -> None:
    assert id_from_path("data/minecraft/worldgen/biome/plains.json") == "minecraft:plains"
    assert (
        id_from_path("kubejs/data/minecraft/worldgen/placed_feature/ore_iron.json")
        == "minecraft:ore_iron"
    )
    assert (
        id_from_path("data/minecraft/tags/worldgen/biome/has_structure/village_plains.json")
        == "#minecraft:has_structure/village_plains"
    )
    assert kind_from_path("data/minecraft/recipes/stick.json") is None

    biome_feats: list[str] = []
    collect_ids(json.loads(BIOME_JSON)["features"], biome_feats)
    assert "minecraft:ore_iron" in biome_feats
    assert "minecraft:trees_plains" in biome_feats
    biome_line = format_biome("minecraft:plains", biome_feats)
    assert biome_line.startswith(HEADER)
    assert "placed_feature=minecraft:ore_iron" in biome_line
    _no_y(biome_line)

    st = json.loads(STRUCTURE_JSON)
    st_line = format_structure("minecraft:village_plains", parse_biomes_field(st))
    assert "biomes=#minecraft:has_structure/village_plains" in st_line
    _no_y(st_line)

    no_bio = format_structure("minecraft:foo", parse_biomes_field(json.loads(STRUCTURE_NO_BIOMES)))
    assert no_bio == f"{HEADER} structure minecraft:foo"
    assert "biomes=" not in no_bio
    _no_y(no_bio)

    sset = parse_structure_set(json.loads(STRUCTURE_SET_JSON))
    ss_line = format_structure_set("minecraft:villages", sset)
    assert "contains=minecraft:village_plains" in ss_line
    assert "spacing=34" in ss_line
    assert "separation=8" in ss_line
    _no_y(ss_line)

    cfg = parse_configured(json.loads(ORE_CONFIGURED))
    cfg_line = format_configured("minecraft:ore_iron", cfg)
    assert "type=minecraft:ore" in cfg_line
    assert "size=9" in cfg_line

    geode = parse_configured(json.loads(GEODE_NO_SIZE))
    geode_line = format_configured("minecraft:amethyst_geode", geode)
    assert "type=minecraft:geode" in geode_line
    assert "size=" not in geode_line

    placed_h = parse_placed(json.loads(PLACED_HEIGHT))
    ph_line = format_placed("minecraft:ore_iron", placed_h)
    assert placed_h["count"] == 10
    assert placed_h["height_range"] == "absolute -24..absolute 56"
    assert "count=10" in ph_line
    assert "height_range=absolute -24..absolute 56" in ph_line
    _no_y(ph_line)

    placed_n = parse_placed(json.loads(PLACED_NO_HEIGHT))
    pn_line = format_placed("minecraft:ore_iron", placed_n)
    assert placed_n["count"] == 4
    assert placed_n["height_range"] is None
    assert "count=4" in pn_line
    assert "height_range" not in pn_line
    _no_y(pn_line)
    assert "Y=" not in pn_line
    assert "Y =" not in pn_line

    mod = parse_modifier(json.loads(MODIFIER_JSON))
    assert mod is not None
    mod_line = format_modifier(mod)
    assert "biomes=minecraft:plains" in mod_line
    assert "features=mod:extra_ore" in mod_line

    assert parse_tag(json.loads(TAG_JSON)) == ["minecraft:plains", "minecraft:meadow"]

    miss = miss_line("no_such_thing", "en_us")
    assert miss.startswith(HEADER)
    assert "no indexed worldgen" in miss
    _no_y(miss)
    assert miss_line("x", "zh_tw").startswith(HEADER + " 此包未索引到")

    print("check_worldgen_lookup OK")


if __name__ == "__main__":
    main()
