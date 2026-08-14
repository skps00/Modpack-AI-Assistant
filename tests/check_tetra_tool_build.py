#!/usr/bin/env python3
"""TM1: Tetra modular NBT → [TOOL_BUILD] parts/mods. Mirrors ToolBuildFacts + flatten."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures" / "tetra" / "tools"

HEADER = "[TOOL_BUILD]"
UNPARSED = "this NBT not parsed"
MAX_PARTS = 12
MAX_MODS = 16
SKIP_KEYS = {
    "damage",
    "hideflags",
    "honing_progress",
    "honing_available",
    "id",
    "repaircount",
    "enchantments",
    "attributemodifiers",
    "display",
    "enchantmentmapping",
    "custommodeldata",
}


def looks_like_tetra_modular_item(item_id: str | None) -> bool:
    if not item_id or not str(item_id).strip():
        return False
    raw = str(item_id).strip().lower()
    ns, _, path = raw.partition(":")
    if ns != "tetra" or "scroll" in path:
        return False
    return path.startswith("modular_")


def skip_key(key: str) -> bool:
    return key.strip().lower() in SKIP_KEYS


def is_material_key(key: str) -> bool:
    k = key.lower()
    return k.endswith("_material") and "/" in k


def is_slot_key(key: str) -> bool:
    if "/" not in key or ":" in key:
        return False
    k = key.lower()
    return not (k.endswith("_material") or "_tweak" in k or k.endswith("settle_progress"))


def is_improvement_key(key: str) -> bool:
    colon = key.find(":")
    if colon <= 0:
        return False
    k = key.lower()
    if "_tweak:" in k or k.endswith("settle_progress"):
        return False
    slash = key.find("/")
    return slash >= 0 and slash < colon


def looks_like_module_id(val: str) -> bool:
    return "/" in val and " " not in val and len(val) < 96


def looks_like_uuid(val: str) -> bool:
    v = val.strip()
    if len(v) != 36:
        return False
    dashes = v.count("-")
    hexpart = v.replace("-", "")
    return dashes == 4 and all(c in "0123456789abcdefABCDEF" for c in hexpart)


def flatten_tag(tag: dict) -> tuple[dict[str, str], dict[str, int]]:
    strings: dict[str, str] = {}
    ints: dict[str, int] = {}
    if not isinstance(tag, dict):
        return strings, ints
    for key, val in tag.items():
        if not key:
            continue
        if isinstance(val, dict):
            module = val.get("id")
            if isinstance(module, str) and module.strip():
                strings[key] = module.strip()
                mat = val.get("material")
                if isinstance(mat, str) and mat.strip():
                    strings[module.strip() + "_material"] = mat.strip()
                im = val.get("improvements")
                if isinstance(im, dict):
                    for ik, iv in im.items():
                        if ik and isinstance(iv, (int, float, bool)):
                            ints[f"{key}:{ik}"] = int(iv)
            continue
        if isinstance(val, bool):
            ints[key] = 1 if val else 0
        elif isinstance(val, int) and not isinstance(val, bool):
            ints[key] = val
        elif isinstance(val, str):
            strings[key] = val
    return strings, ints


def parse(strings: dict[str, str], ints: dict[str, int]) -> tuple[list[tuple[str, str, str]], list[tuple[str, int]]]:
    slots: dict[str, str] = {}
    materials: dict[str, str] = {}
    for key, val in (strings or {}).items():
        if not key or not val or skip_key(key) or looks_like_uuid(val):
            continue
        k, v = key.strip(), val.strip()
        if is_material_key(k):
            materials[k] = v
            continue
        if is_slot_key(k) and looks_like_module_id(v):
            slots[k] = v
    parts: list[tuple[str, str, str]] = []
    for slot, module in slots.items():
        if len(parts) >= MAX_PARTS:
            break
        mat = materials.get(module + "_material") or materials.get(slot + "_material") or ""
        parts.append((slot, module, mat))
    mods: list[tuple[str, int]] = []
    for key, level in (ints or {}).items():
        if len(mods) >= MAX_MODS:
            break
        if not key or skip_key(key) or not is_improvement_key(key):
            continue
        mods.append((key.strip(), int(level)))
    return parts, mods


def is_socket_part(slot: str, module: str, mat: str) -> bool:
    blob = f"{slot} {module} {mat}".lower()
    return "socket" in blob


def format_scan(parts: list, mods: list) -> str:
    if not parts and not mods:
        return ""
    lines = [HEADER]
    for row in parts:
        slot, module, mat = row[0], row[1], row[2]
        name = row[3] if len(row) > 3 else ""
        item = row[4] if len(row) > 4 else ""
        kind = "socket" if is_socket_part(slot, module, mat) else "part"
        line = f"{kind} {slot}: {module}"
        if mat:
            line += f" material {mat}"
        if name:
            line += f" name {name}"
        if item:
            line += f" item {item}"
        lines.append(line)
    for row in mods:
        key, level = row[0], row[1]
        name = row[2] if len(row) > 2 else ""
        item = row[3] if len(row) > 3 else ""
        line = f"improvement {key} {level}"
        if name:
            line += f" name {name}"
        if item:
            line += f" item {item}"
        lines.append(line)
    return "\n".join(lines)


def first_item_id(json_text: str) -> str:
    if not (json_text or "").strip():
        return ""
    root = json.loads(json_text)
    material = root.get("material")
    if not isinstance(material, dict):
        return ""
    for it in material.get("items") or []:
        if isinstance(it, str) and it.strip() and ":" in it.strip() and " " not in it.strip():
            return it.strip()
    return ""


def json_key(json_text: str) -> str:
    if not (json_text or "").strip():
        return ""
    root = json.loads(json_text)
    k = root.get("key")
    return k.strip() if isinstance(k, str) else ""


def last_segment(material_id: str) -> str:
    s = (material_id or "").strip()
    if "/" in s:
        return s.rsplit("/", 1)[-1]
    return s


def index_material_files(root: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not root.is_dir():
        return out
    for p in root.rglob("*.json"):
        text = p.read_text(encoding="utf-8")
        item = first_item_id(text)
        if not item:
            continue
        key = json_key(text)
        if key:
            out.setdefault(key.lower(), item)
            out.setdefault(last_segment(key).lower(), item)
    return out


def index_schematic_json(text: str, out: dict[str, str]) -> None:
    try:
        doc = json.loads(text)
    except Exception:
        return
    for o in doc.get("outcomes") or []:
        if not isinstance(o, dict):
            continue
        item = first_item_id(json.dumps({"material": o.get("material") or {}}))
        if not item:
            continue
        variant = o.get("moduleVariant") if isinstance(o.get("moduleVariant"), str) else ""
        module = o.get("moduleKey") if isinstance(o.get("moduleKey"), str) else ""
        if variant.strip():
            out.setdefault(variant.strip().lower(), item)
        if module.strip():
            out.setdefault(module.strip().lower(), item)
            out.setdefault(last_segment(module).lower(), item)


def index_item_files(root: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    mats = root / "materials"
    if mats.is_dir():
        out.update(index_material_files(mats))
    schems = root / "schematics"
    if schems.is_dir():
        for p in schems.rglob("*.json"):
            index_schematic_json(p.read_text(encoding="utf-8"), out)
    return out


def item_id_for(material_id: str, index: dict[str, str]) -> str:
    raw = (material_id or "").strip()
    if not raw:
        return ""
    hit = index.get(raw.lower())
    if hit:
        return hit
    last = last_segment(raw)
    if last.lower() != raw.lower():
        return index.get(last.lower()) or ""
    return ""


def apply_item_ids(parts: list) -> list:
    index = index_item_files(ROOT / "tests" / "fixtures" / "tetra")
    out = []
    for row in parts:
        slot, module, mat = row[0], row[1], row[2]
        item = item_id_for(mat, index)
        out.append((slot, module, mat, "", item))
    return out


def purpose_from_fixture(doc: dict) -> str:
    strings, ints = flatten_tag(doc.get("tag") or {})
    parts, mods = parse(strings, ints)
    parts = apply_item_ids(parts)
    item = doc.get("item") or ""
    if looks_like_tetra_modular_item(item) and "scroll" in str(item).lower():
        return ""
    # Scrolls: even if item id is modular, nested BlockEntityTag without slot keys → empty
    if str(item).endswith("scroll_rolled") or "scroll" in str(item).lower():
        return ""
    text = format_scan(parts, mods)
    if text:
        return text
    if looks_like_tetra_modular_item(item):
        return HEADER + "\n" + UNPARSED
    return ""


def load(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def main() -> None:
    hammer = purpose_from_fixture(load("copper_hammer.json"))
    assert hammer.startswith(HEADER), hammer
    assert "part double/head_left: double/basic_hammer_left material basic_hammer/copper" in hammer
    assert "part double/head_right: double/basic_hammer_right material basic_hammer/copper" in hammer
    assert "part double/handle: double/basic_handle material basic_handle/spruce" in hammer
    assert "improvement double/head_left:workable 1" in hammer
    assert "d5bf3a60" not in hammer
    assert "honing_progress" not in hammer

    axe = purpose_from_fixture(load("oak_axe.json"))
    assert "material basic_axe/oak" in axe
    assert "material butt/oak" in axe
    assert "material basic_handle/stick" in axe
    assert "improvement double/head_left:hone/efficiency 1" in axe

    sword = purpose_from_fixture(load("iron_sword.json"))
    assert "part sword/blade: sword/basic_blade material basic_blade/iron" in sword
    assert "part sword/hilt:" in sword
    assert "improvement sword/blade:hone/damage 1" in sword

    nested = purpose_from_fixture(load("nested_hammer.json"))
    assert "part double/head_left: double/basic_hammer_left material basic_hammer/iron" in nested
    assert "part double/handle: double/basic_handle material basic_handle/oak" in nested
    assert "improvement double/head_left:workable 1" in nested

    wu = purpose_from_fixture(load("wu_sword.json"))
    assert wu.startswith(HEADER), wu
    assert "part sword/blade: sword/wu material wu" in wu
    assert "item golden_age:wu" in wu
    assert "part sword/hilt: sword/wu_hilt material wu_hilt" in wu
    assert "part sword/fuller: sword/reinforced_fuller material reinforced_fuller/archotech_arcane_steel" in wu
    assert "item golden_age:archotech_arcane_steel" in wu
    assert "socket sword/guard: sword/sword_socket material sword_socket/thunder_gem1_socket" in wu
    assert "item golden_age:thunder_gem1" in wu
    assert "part sword/pommel: sword/forefinger_ring material forefinger_ring/archotech_arcane_steel" in wu
    blade_line = [ln for ln in wu.splitlines() if ln.startswith("part sword/blade:")][0]
    assert "item golden_age:wu" in blade_line, blade_line
    hilt_line = [ln for ln in wu.splitlines() if ln.startswith("part sword/hilt:")][0]
    assert " item " not in hilt_line, hilt_line
    assert "minecraft:dirt" not in wu

    gem = (ROOT / "tests/fixtures/tetra/materials/socket/thunder_gem1.json").read_text(encoding="utf-8")
    assert json_key(gem) == "thunder_gem1_socket"
    assert first_item_id(gem) == "golden_age:thunder_gem1"
    steel = (ROOT / "tests/fixtures/tetra/materials/metal/archotech_arcane_steel.json").read_text(
        encoding="utf-8"
    )
    assert first_item_id(steel) == "golden_age:archotech_arcane_steel"
    wu_mat = (ROOT / "tests/fixtures/tetra/materials/unique/wu.json").read_text(encoding="utf-8")
    assert first_item_id(wu_mat) == ""
    idx_m = index_material_files(ROOT / "tests/fixtures/tetra/materials")
    assert item_id_for("sword_socket/thunder_gem1_socket", idx_m) == "golden_age:thunder_gem1"
    assert item_id_for("wu", idx_m) == ""
    assert item_id_for("tag:forge:gems/diamond", idx_m) == ""
    idx = index_item_files(ROOT / "tests/fixtures/tetra")
    assert item_id_for("wu", idx) == "golden_age:wu"
    assert item_id_for("sword/wu", idx) == "golden_age:wu"
    assert item_id_for("wu_hilt", idx) == ""

    scroll = purpose_from_fixture(load("scroll_not_tool.json"))
    assert scroll == "", scroll

    from check_item_variant_keys import schematics_from_tag

    schem = schematics_from_tag(load("scroll_not_tool.json")["tag"])
    assert "hone/gild_2" in schem and "tetra:hone/gild_1" in schem

    assert looks_like_tetra_modular_item("tetra:modular_double")
    assert not looks_like_tetra_modular_item("tetra:scroll_rolled")
    empty_mod = purpose_from_fixture({"item": "tetra:modular_double", "tag": {}})
    assert empty_mod == HEADER + "\n" + UNPARSED
    assert purpose_from_fixture({"item": "minecraft:iron_ingot", "tag": {}}) == ""

    # Dual-tree markers
    for rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/ToolBuildFacts.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/ToolBuildFacts.java",
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/ModularToolScan.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/ModularToolScan.java",
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/TetraMaterialItems.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/TetraMaterialItems.java",
    ):
        src = (ROOT / rel).read_text(encoding="utf-8")
        if "TetraMaterialItems" in rel:
            assert "firstItemId" in src
            assert "itemIdFor" in src
            assert "indexSchematicJson" in src
            continue
        assert "TOOL_BUILD" in src
        assert "looksLikeTetraScroll" in src or "tetra_flat_nbt" in src
        if "ModularToolScan" in rel:
            assert "looksLikeTetraScroll" in src
            assert "purposeLines" in src
            assert "flattenNestedModule" in src
            assert "public static ToolBuildFacts.Scan scan" in src
            assert "TetraMaterialItems.itemIdFor" in src
        if "ToolBuildFacts" in rel:
            assert "MAX_PARTS" in src and "MAX_MODS" in src
            assert "_material" in src
            assert "isSocketPart" in src
            assert "itemId" in src

    for reply_rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/ReplyLang.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/ReplyLang.java",
    ):
        reply = (ROOT / reply_rel).read_text(encoding="utf-8")
        assert "packai.reply.tool_build" in reply

    for ask_rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/service/AskService.java",
    ):
        ask = (ROOT / ask_rel).read_text(encoding="utf-8")
        assert "ModularToolScan.purposeLines" in ask

    for scrub_rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/AskReplyScrub.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/AskReplyScrub.java",
    ):
        scrub = (ROOT / scrub_rel).read_text(encoding="utf-8")
        assert "TOOL_BUILD" in scrub

    # Scroll walk still allowlisted (regression)
    variant = (
        ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/ItemVariantKeys.java"
    ).read_text(encoding="utf-8")
    assert "walkAllowlisted" in variant
    assert "BlockEntityTag" in variant

    print("check_tetra_tool_build OK")


if __name__ == "__main__":
    main()
