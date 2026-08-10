"""ItemVariantKeys / TetraSchematicText — scroll schematic PURPOSE facts."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tests" / "fixtures" / "tetra" / "schematics"
MAT_FIXTURES = ROOT / "tests" / "fixtures" / "tetra" / "materials" / "battery"

MAX_RESOURCES = 4
MAX_MATS = 4
MAX_EFFECT_TOKENS = 6
MAX_INSTALL_ITEMS = 8
INSTALL_ITEMS_PREFIX = "install_items (pick one / 任選其一): "
NONE_MATERIALS_LINE = "none (no material required)"


def key_path(raw: str) -> str:
    s = (raw or "").strip()
    if ":" in s:
        return s.split(":", 1)[1].strip()
    return s


def resource_matches_key(resource_path: str, raw_key: str) -> bool:
    path = resource_path.replace("\\", "/").lower()
    if path.endswith(".json"):
        path = path[:-5]
    if "/schematics/" in path:
        path = "schematics/" + path.split("/schematics/", 1)[1]
    kp = key_path(raw_key).lower()
    if not kp:
        return False
    under = kp.replace("/", "_")
    file = path.rsplit("/", 1)[-1]
    if path == f"schematics/{kp}" or path.endswith("/" + kp):
        return True
    if path == f"schematics/{under}" or path.endswith("/" + under):
        return True
    if "/" not in kp and file == kp:
        return True
    if "/" in kp and file == kp.split("/", 1)[0]:
        return True
    return False


def format_translation_effects(translation: dict | None) -> str:
    if not isinstance(translation, dict):
        return ""
    parts: list[str] = []
    for field in (
        "primaryAttributes",
        "secondaryAttributes",
        "primaryEffects",
        "secondaryEffects",
        "tools",
    ):
        obj = translation.get(field)
        if isinstance(obj, dict):
            for k, v in obj.items():
                if len(parts) >= MAX_EFFECT_TOKENS:
                    break
                parts.append(f"{k}={v}")
        if len(parts) >= MAX_EFFECT_TOKENS:
            break
    for scalar in ("durability", "integrity", "magicCapacity"):
        if len(parts) >= MAX_EFFECT_TOKENS:
            break
        if scalar in translation:
            parts.append(f"{scalar}={translation[scalar]}")
    return "; ".join(parts)


def format_materials(outcome: dict) -> str:
    mats: list[str] = []
    seen: set[str] = set()

    def add(x: str) -> None:
        if x and x not in seen and len(mats) < MAX_MATS:
            seen.add(x)
            mats.append(x)

    for m in outcome.get("materials") or []:
        if isinstance(m, str):
            add(m.strip())
    material = outcome.get("material")
    if isinstance(material, dict):
        tag = material.get("tag")
        if isinstance(tag, str) and tag.strip():
            add("#" + tag.strip())
        for it in material.get("items") or []:
            if isinstance(it, str):
                add(it.strip())
        count = material.get("count")
        if count is not None and mats:
            first = mats[0]
            mats[0] = f"{first} x{count}"
    return ", ".join(mats)


def extract_locked(req) -> str:
    if not isinstance(req, dict):
        return ""
    t = str(req.get("type") or "").lower()
    if t.endswith("locked") or t == "tetra:locked":
        return str(req.get("key") or "").strip()
    nested = extract_locked(req.get("requirement"))
    if nested:
        return nested
    for r in req.get("requirements") or []:
        nested = extract_locked(r)
        if nested:
            return nested
    return ""


def locked_key_matches(locked: str, scroll: str) -> bool:
    a = (locked or "").strip().lower()
    b = (scroll or "").strip().lower()
    if not a or not b:
        return False
    if a == b:
        return True
    return key_path(a).lower() == key_path(b).lower()


def locked_key_from_json(json_text: str) -> str:
    root = json.loads(json_text)
    if not isinstance(root, dict):
        return ""
    return extract_locked(root.get("requirement"))


def purpose_from_loaded(raw_key: str, resource_ids: list[str], bodies: list[str]) -> str:
    key = (raw_key or "").strip()
    if not key:
        return ""
    if not bodies:
        return f"[SCROLL_UNLOCK]\nschematic:{key} (json unknown)"
    unlock: list[str] = []
    materials: list[str] = []
    seen_u: set[str] = set()
    seen_m: set[str] = set()
    saw_outcome = False
    any_mat = False
    slot_zero = False

    def add_u(s: str) -> None:
        if s and s not in seen_u:
            seen_u.add(s)
            unlock.append(s)

    def add_m(s: str) -> None:
        if s and s not in seen_m:
            seen_m.add(s)
            materials.append(s)

    add_u(f"schematic:{key}")
    n = min(len(bodies), len(resource_ids) if resource_ids else len(bodies), MAX_RESOURCES)
    for i in range(n):
        rid = resource_ids[i] if i < len(resource_ids) else ""
        root = json.loads(bodies[i])
        if rid:
            add_u(f"resource:{rid}")
        slots = root.get("slots") or []
        if slots:
            add_u("slots:" + ",".join(slots[:6]))
        locked = extract_locked(root.get("requirement"))
        if locked:
            add_u(f"locked:{locked}")
        effect = format_translation_effects(root.get("translation"))
        if effect:
            add_u("effect:" + effect)
        if root.get("materialSlotCount") == 0:
            slot_zero = True
        applicable = root.get("applicableMaterials") or []
        if applicable:
            add_m("applicable:" + ",".join(str(x) for x in applicable[:4]))
            any_mat = True
        for o in root.get("outcomes") or []:
            if not isinstance(o, dict):
                continue
            saw_outcome = True
            module = str(o.get("moduleKey") or "").strip()
            variant = str(o.get("moduleVariant") or "").strip()
            if module:
                line = f"module:{module}"
                if variant:
                    line += f" variant:{variant}"
                add_u(line)
            improvs = o.get("improvements")
            if isinstance(improvs, dict) and improvs:
                add_u(
                    "improvement:"
                    + ",".join(f"{k}:{v}" for k, v in list(improvs.items())[:4])
                )
            mats = format_materials(o)
            if mats:
                any_mat = True
                prefix = f"{module} -> " if module else ""
                add_m(prefix + mats)
    if not any_mat and (saw_outcome or slot_zero) and len(unlock) > 1:
        add_m(NONE_MATERIALS_LINE)
    if len(unlock) <= 1 and not materials:
        return f"[SCROLL_UNLOCK]\nschematic:{key} (json unknown)"
    out = "[SCROLL_UNLOCK]\n" + "\n".join(unlock[:10])
    if materials:
        out += "\n[SCROLL_MATERIALS]\n" + "\n".join(materials[:8])
    return out


def is_material_folder_ref(token: str) -> bool:
    t = (token or "").strip()
    colon = t.find(":")
    return colon > 0 and t.endswith("/") and colon < len(t) - 1


def material_folder_category(folder_ref: str) -> str:
    if not is_material_folder_ref(folder_ref):
        return ""
    path = key_path(folder_ref.strip()).rstrip("/")
    return path


def material_folder_refs(purpose: str) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    in_mats = False
    for line in (purpose or "").split("\n"):
        trimmed = line.strip()
        if trimmed == "[SCROLL_MATERIALS]":
            in_mats = True
            continue
        if trimmed.startswith("[") and trimmed.endswith("]"):
            in_mats = False
            continue
        if not in_mats or not trimmed:
            continue
        body = trimmed
        if " -> " in body:
            body = body.split(" -> ", 1)[1]
        if body.startswith(("applicable:", "tools:", "install_items")):
            continue
        for part in body.split(","):
            p = part.strip()
            if is_material_folder_ref(p) and p not in seen:
                seen.add(p)
                out.append(p)
    return out


def bare_item_id(item: str) -> str:
    s = (item or "").strip()
    x = s.rfind(" x")
    if x > 0:
        maybe = s[x + 2 :].strip()
        if maybe.isdigit():
            return s[:x].strip()
    return s


def items_from_material_json(raw: str) -> list[str]:
    if not (raw or "").strip():
        return []
    root = json.loads(raw)
    material = root.get("material")
    if not isinstance(material, dict):
        return []
    out: list[str] = []
    for it in material.get("items") or []:
        if isinstance(it, str) and it.strip():
            out.append(it.strip())
    if not out:
        return []
    count = material.get("count")
    if count is not None:
        out[0] = f"{out[0]} x{count}"
    return out


def merge_install_items(from_json: list[str], dest: list[str]) -> None:
    seen = {bare_item_id(x) for x in dest}
    for item in from_json:
        bare = bare_item_id(item)
        if not bare or bare in seen:
            continue
        seen.add(bare)
        dest.append(item.strip())


def format_install_items_line(items: list[str], folder_refs: list[str] | None = None) -> str:
    list_ = [s.strip() for s in items if s and s.strip()]
    if not list_:
        return ""
    shown = list_[:MAX_INSTALL_ITEMS]
    line = INSTALL_ITEMS_PREFIX + ", ".join(shown)
    more = len(list_) - len(shown)
    if more > 0:
        line += f", … +{more} more"
        folder = ""
        for r in folder_refs or []:
            if r and r.strip():
                folder = r.strip()
                break
        if folder:
            line += f" in {folder}"
    return line


def with_install_items_line(purpose: str, install_line: str) -> str:
    if not purpose:
        return purpose or ""
    if not install_line:
        return purpose
    if "install_items" in purpose:
        return purpose
    if "[SCROLL_MATERIALS]" not in purpose:
        return purpose + "\n[SCROLL_MATERIALS]\n" + install_line
    return purpose + "\n" + install_line


def expand_from_material_bodies(purpose: str, bodies: list[str]) -> str:
    refs = material_folder_refs(purpose)
    if not refs:
        return purpose
    items: list[str] = []
    for _ref in refs:
        for body in bodies:
            merge_install_items(items_from_material_json(body), items)
    return with_install_items_line(purpose, format_install_items_line(items, refs))


def parse_one_item_spec(token: str) -> tuple[str, int] | None:
    t = (token or "").strip()
    if not t:
        return None
    low = t.lower()
    x = low.rfind(" x")
    item_id = t
    count = 1
    if x > 0 and x + 2 < len(t):
        num = t[x + 2 :].strip()
        if num.isdigit() and len(num) <= 4:
            item_id = t[:x].strip()
            count = max(1, int(num))
    if ":" not in item_id or " " in item_id or item_id.startswith("#"):
        return None
    return item_id.lower(), count


def parse_item_spec_list(csv: str) -> list[tuple[str, int]]:
    out: list[tuple[str, int]] = []
    seen: set[str] = set()
    for raw in (csv or "").split(","):
        tok = raw.strip()
        if not tok or tok.startswith("…") or tok.startswith("..."):
            continue
        if is_material_folder_ref(tok):
            continue
        spec = parse_one_item_spec(tok)
        if not spec or spec[0] in seen:
            continue
        seen.add(spec[0])
        out.append(spec)
        if len(out) >= MAX_INSTALL_ITEMS:
            break
    return out


def scroll_material_body_lines(purpose: str) -> list[str]:
    out: list[str] = []
    in_mats = False
    for line in (purpose or "").split("\n"):
        trimmed = line.strip()
        if trimmed == "[SCROLL_MATERIALS]":
            in_mats = True
            continue
        if trimmed.startswith("[") and trimmed.endswith("]"):
            in_mats = False
            continue
        if in_mats and trimmed:
            out.append(trimmed)
    return out


def says_no_materials(purpose: str) -> bool:
    for line in scroll_material_body_lines(purpose):
        t = line.strip().lower()
        if t == NONE_MATERIALS_LINE or t == "none" or t.startswith("none ("):
            return True
    return False


def install_item_specs_from_purpose(purpose: str) -> list[tuple[str, int]]:
    body = scroll_material_body_lines(purpose)
    for line in body:
        if line.lower().startswith("install_items"):
            colon = line.find(":")
            rhs = line[colon + 1 :].strip() if colon >= 0 else ""
            specs = parse_item_spec_list(rhs)
            if specs:
                return specs
    out: list[tuple[str, int]] = []
    seen: set[str] = set()
    for line in body:
        trimmed = line.strip()
        low = trimmed.lower()
        if (
            low.startswith("tools:")
            or low.startswith("applicable:")
            or low.startswith("install_items")
            or low == NONE_MATERIALS_LINE
            or low == "none"
            or low.startswith("none (")
            or is_material_folder_ref(trimmed)
        ):
            continue
        rhs = trimmed
        if "->" in trimmed:
            rhs = trimmed.split("->", 1)[1].strip()
        for spec in parse_item_spec_list(rhs):
            if spec[0] in seen:
                continue
            seen.add(spec[0])
            out.append(spec)
            if len(out) >= MAX_INSTALL_ITEMS:
                return out
    return out


def main() -> None:
    assert resource_matches_key("schematics/sword/energy_bottle", "tetra:energy_bottle")
    assert resource_matches_key("schematics/shared/hone_gild_1", "tetra:hone/gild_1")
    assert not resource_matches_key("schematics/sword/other", "tetra:energy_bottle")

    sword = (FIXTURES / "energy_bottle_sword.json").read_text(encoding="utf-8")
    block = purpose_from_loaded(
        "tetra:energy_bottle",
        ["tetra:schematics/sword/energy_bottle"],
        [sword],
    )
    assert block.startswith("[SCROLL_UNLOCK]")
    assert "schematic:tetra:energy_bottle" in block
    assert "locked:tetra:energy_bottle" in block
    assert "module:sword/energy_bottle" in block
    assert "effect:" in block and "generic.attack_damage" in block
    assert "[SCROLL_MATERIALS]" in block
    assert "tetra:battery/" in block
    assert "(json unknown)" not in block
    assert material_folder_refs(block) == ["tetra:battery/"]
    assert material_folder_category("tetra:battery/") == "battery"

    copper_json = (MAT_FIXTURES / "copper.json").read_text(encoding="utf-8")
    assert items_from_material_json(copper_json) == ["minecraft:copper_ingot x64"]
    mat_bodies = [p.read_text(encoding="utf-8") for p in sorted(MAT_FIXTURES.glob("*.json"))]
    assert len(mat_bodies) >= 3
    expanded = expand_from_material_bodies(block, mat_bodies)
    assert "tetra:battery/" in expanded
    assert "install_items (pick one / 任選其一):" in expanded
    assert "minecraft:copper_ingot x64" in expanded
    assert "minecraft:iron_ingot x32" in expanded
    assert "minecraft:gold_ingot x32" in expanded
    # overflow formatting (cap 8; folder ref in tail)
    many = [f"mod:item_{i}" for i in range(23)]
    overflow = format_install_items_line(many, ["tetra:battery/"])
    assert overflow.startswith("install_items (pick one / 任選其一):")
    assert "… +15 more in tetra:battery/" in overflow
    assert overflow.count("mod:item_") == 8

    hone = (FIXTURES / "hone_gild_1.json").read_text(encoding="utf-8")
    hone_block = purpose_from_loaded("tetra:hone/gild_1", ["tetra:schematics/shared/hone_gild_1"], [hone])
    assert "improvement:hone_gild:1" in hone_block
    assert "minecraft:gold_nugget" in hone_block
    assert purpose_from_loaded("tetra:missing", [], []).endswith("(json unknown)")

    # Locked-by reverse: terra scroll has no terra.json; cthulhu schematic locks tetra:terra
    assert locked_key_matches("tetra:terra", "terra")
    assert locked_key_matches("tetra:terra", "tetra:terra")
    assert not locked_key_matches("tetra:energy_bottle", "tetra:terra")
    cthulhu = (FIXTURES / "shield" / "plate" / "cthulhu.json").read_text(encoding="utf-8")
    assert locked_key_from_json(cthulhu) == "tetra:terra"
    terra_block = purpose_from_loaded(
        "tetra:terra",
        ["tetra:schematics/shield/plate/cthulhu"],
        [cthulhu],
    )
    assert "module:shield/cthulhu" in terra_block
    assert "iceandfire:cyclops_eye" in terra_block
    assert "(json unknown)" not in terra_block

    none_json = (FIXTURES / "no_mat_upgrade.json").read_text(encoding="utf-8")
    none_block = purpose_from_loaded(
        "tetra:no_mat_demo",
        ["tetra:schematics/no_mat_upgrade"],
        [none_json],
    )
    assert "[SCROLL_MATERIALS]" in none_block
    assert NONE_MATERIALS_LINE in none_block
    assert "module:sword/demo_free" in none_block

    specs = install_item_specs_from_purpose(
        "[SCROLL_MATERIALS]\n"
        + INSTALL_ITEMS_PREFIX
        + "minecraft:copper_ingot x64, minecraft:iron_ingot x32, … +1 more in tetra:battery/"
    )
    assert specs[0] == ("minecraft:copper_ingot", 64)
    assert specs[1] == ("minecraft:iron_ingot", 32)
    assert len(specs) == 2
    hone_specs = install_item_specs_from_purpose(hone_block)
    assert any(s[0] == "minecraft:gold_nugget" and s[1] == 3 for s in hone_specs)
    assert says_no_materials(none_block)
    assert not says_no_materials(hone_block)

    # Java sources present both trees
    for tree in ("neoforge/1.21.1", "forge/1.19.2"):
        text = (ROOT / tree / "src/main/java/com/skps9/packai/logic/TetraSchematicText.java").read_text(
            encoding="utf-8"
        )
        assert "SCROLL_MATERIALS_HEADER" in text
        assert "formatInstallItemsLine" in text
        assert "INSTALL_ITEMS_PREFIX" in text
        assert "NONE_MATERIALS_LINE" in text
        assert "installItemSpecsFromPurpose" in text
        assert "MAX_INSTALL_ITEMS = 8" in text
        assert "itemsFromMaterialJson" in text
        assert "lockedKeyMatches" in text
        assert "MAX_INSTALL_ITEMS" in text
        assert "injectInlineMaterials" in text
        assert "formatItemMarker" in text
        assert "materialStrip" in (
            ROOT / tree / "src/main/java/com/skps9/packai/logic/RecipeCard.java"
        ).read_text(encoding="utf-8")
        lookup = (ROOT / tree / "src/main/java/com/skps9/packai/logic/TetraSchematicLookup.java").read_text(
            encoding="utf-8"
        )
        assert "kubejs" in lookup
        ask = (ROOT / tree / "src/main/java/com/skps9/packai/client/service/AskService.java").read_text(
            encoding="utf-8"
        )
        assert "withScrollMaterialInline" in ask
        assert "withScrollMaterialCards" in ask
        assert "tryLoadFromDiskTree" in lookup
        assert "tryLoadLockedByKey" in lookup
        assert "expandMaterialFolders" in lookup
        assert "loadMaterialFolderItems" in lookup
        ask = (ROOT / tree / "src/main/java/com/skps9/packai/client/service/AskService.java").read_text(
            encoding="utf-8"
        )
        assert "scrollSchematicPurposeLines" in ask

    print("check_tetra_schematic_facts: OK")


if __name__ == "__main__":
    main()
