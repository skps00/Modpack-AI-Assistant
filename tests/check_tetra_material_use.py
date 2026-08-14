#!/usr/bin/env python3
"""Reverse Tetra datapack index: item id → [TETRA_USE] lines. Mirrors TetraMaterialItems."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / "tests" / "fixtures" / "tetra"
HEADER = "[TETRA_USE]"
MAX_USES = 12
MAX_SLOTS = 8


def looks_like_item_id(raw: str | None) -> bool:
    if not raw:
        return False
    s = str(raw).strip()
    if not s or len(s) > 128 or " " in s:
        return False
    c = s.find(":")
    if c <= 0 or c >= len(s) - 1:
        return False
    if s.find(":") != s.rfind(":"):
        return False
    return not s.startswith("/")


def item_ids_from(holder: dict | None) -> list[str]:
    if not isinstance(holder, dict):
        return []
    material = holder.get("material")
    if not isinstance(material, dict):
        return []
    items = material.get("items")
    if not isinstance(items, list):
        return []
    out: list[str] = []
    for e in items:
        if looks_like_item_id(e):
            t = str(e).strip()
            if t not in out:
                out.append(t)
    return out


def join_slots(root: dict | None) -> str:
    if not isinstance(root, dict):
        return ""
    slots = root.get("slots")
    if not isinstance(slots, list):
        return ""
    cleaned = [str(s).strip() for s in slots if s and str(s).strip() and len(str(s).strip()) < 64]
    if not cleaned:
        return ""
    if len(cleaned) <= MAX_SLOTS:
        return ",".join(cleaned)
    return ",".join(cleaned[:MAX_SLOTS]) + f",+{len(cleaned) - MAX_SLOTS}"


def use_line(kind: str, key: str, category: str, slots: str, module: str) -> str:
    parts = [kind]
    if key:
        parts.append(f"key={key}")
    if category:
        parts.append(f"category={category}")
    if slots:
        parts.append(f"slots={slots}")
    if module:
        if kind == "modifier":
            parts.append(f"improvement={module}")
        else:
            parts.append(f"module={module}")
    return " ".join(parts)


def add_use(reverse: dict[str, list[str]], item_id: str, line: str) -> None:
    if not item_id or not line:
        return
    key = item_id.strip().lower()
    lst = reverse.setdefault(key, [])
    if line in lst or len(lst) >= MAX_USES:
        return
    lst.append(line)


def index_json(obj: dict, reverse: dict[str, list[str]]) -> None:
    items = item_ids_from(obj)
    if not items:
        return
    key = str(obj.get("key") or "").strip()
    category = str(obj.get("category") or "").strip()
    kind = "socket" if category.lower() == "socket" else "material"
    line = use_line(kind, key, category, "", "")
    for item in items:
        add_use(reverse, item, line)


def index_schematic(obj: dict, reverse: dict[str, list[str]]) -> None:
    outcomes = obj.get("outcomes")
    if not isinstance(outcomes, list):
        return
    slots = join_slots(obj)
    for outcome in outcomes:
        if not isinstance(outcome, dict):
            continue
        items = item_ids_from(outcome)
        if not items:
            continue
        variant = str(outcome.get("moduleVariant") or "").strip()
        module_key = str(outcome.get("moduleKey") or "").strip()
        im = outcome.get("improvements")
        improvement = ""
        if isinstance(im, dict):
            for k in im:
                if k and str(k).strip():
                    improvement = str(k).strip()
                    break
        if improvement:
            kind, module = "modifier", improvement
        elif module_key:
            kind, module = "module", module_key
        else:
            kind, module = "schematic", ""
        line = use_line(kind, variant, "", slots, module)
        for item in items:
            add_use(reverse, item, line)


def index_tree(root: Path) -> dict[str, list[str]]:
    reverse: dict[str, list[str]] = {}
    mats = root / "materials"
    if mats.is_dir():
        for p in mats.rglob("*.json"):
            index_json(json.loads(p.read_text(encoding="utf-8")), reverse)
    schems = root / "schematics"
    if schems.is_dir():
        for p in schems.rglob("*.json"):
            index_schematic(json.loads(p.read_text(encoding="utf-8")), reverse)
    return reverse


def format_uses(lines: list[str] | None) -> str:
    if not lines:
        return ""
    body = [ln for ln in lines if ln][:MAX_USES]
    if not body:
        return ""
    return HEADER + "\n" + "\n".join(body)


def main() -> None:
    rev = index_tree(FIX)
    steel = format_uses(rev.get("golden_age:archotech_arcane_steel"))
    assert steel.startswith(HEADER), steel
    assert "material key=archotech_arcane_steel category=metal" in steel, steel
    assert "sword/" not in steel, steel  # no invented tool slots

    gem = format_uses(rev.get("golden_age:thunder_gem1"))
    assert "socket key=thunder_gem1_socket" in gem, gem

    wu = format_uses(rev.get("golden_age:wu"))
    assert "module key=wu slots=sword/blade module=sword/wu" in wu, wu

    assert not format_uses(rev.get("wu_hilt"))
    assert not format_uses(rev.get("minecraft:dirt"))

    copper = format_uses(rev.get("minecraft:copper_ingot"))
    assert "material key=battery_copper category=battery" in copper, copper

    nugget = format_uses(rev.get("minecraft:gold_nugget"))
    assert "modifier" in nugget and "improvement=hone_gild" in nugget, nugget
    assert "slots=" in nugget, nugget
    assert "sword/blade" in nugget, nugget
    assert ",+5" in nugget, nugget
    assert "bow/stave+5" not in nugget, nugget

    # Dual-tree markers
    for rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/TetraMaterialItems.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/TetraMaterialItems.java",
        "forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/service/AskService.java",
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/AskReplyScrub.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/AskReplyScrub.java",
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/ReplyLang.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/ReplyLang.java",
    ):
        src = (ROOT / rel).read_text(encoding="utf-8")
        if "TetraMaterialItems.java" in rel:
            assert "TETRA_USE" in src and "formatUses" in src and "indexJson" in src
            assert "purposeLines" in src
            continue
        if "AskService" in rel:
            assert "TetraMaterialItems.purposeLines" in src
            continue
        if "AskReplyScrub" in rel:
            assert "TETRA_USE" in src
            continue
        if "ReplyLang" in rel:
            assert "packai.reply.tetra_use" in src

    print("check_tetra_material_use OK")


if __name__ == "__main__":
    main()
