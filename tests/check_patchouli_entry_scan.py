#!/usr/bin/env python3
"""Mirrors PatchouliEntryScan — item match + text extract without Patchouli runtime."""

import json
import re


def normalize_item_key(raw: str | None) -> str:
    if not raw:
        return ""
    s = raw.strip()
    brace = s.find("{")
    if brace >= 0:
        s = s[:brace]
    if not s.startswith("#"):
        hash_i = s.find("#")
        if hash_i >= 0:
            s = s[:hash_i]
    return s.strip().lower()


def id_mentions(raw: str | None, item_id: str | None) -> bool:
    want = normalize_item_key(item_id)
    got = normalize_item_key(raw)
    return bool(want and got and got == want)


def references_item(entry: dict, item_id: str) -> bool:
    if id_mentions(entry.get("icon"), item_id):
        return True
    erm = entry.get("extra_recipe_mappings") or {}
    if isinstance(erm, dict):
        for k in erm:
            if id_mentions(k, item_id):
                return True
    for page in entry.get("pages") or []:
        if not isinstance(page, dict):
            continue
        if id_mentions(page.get("item"), item_id):
            return True
        items = page.get("items")
        if isinstance(items, list):
            for it in items:
                if isinstance(it, str) and id_mentions(it, item_id):
                    return True
    return False


def strip_macros(raw: str) -> str:
    s = raw.replace("$(br)", "\n").replace("$()", "")
    s = re.sub(r"\$\([^)]*\)", "", s)
    s = re.sub(r"[ \t]+\n", "\n", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


def is_text_page(page: dict) -> bool:
    t = str(page.get("type") or "").lower()
    if not t:
        return "text" in page
    return t in ("text", "patchouli:text") or t.endswith(":text")


def extract_plain_text(entry: dict) -> str:
    parts: list[str] = []
    name = entry.get("name")
    if isinstance(name, str) and name.strip():
        parts.append(name.strip())
    for page in entry.get("pages") or []:
        if not isinstance(page, dict) or not is_text_page(page):
            continue
        text = page.get("text")
        if isinstance(text, str) and text.strip():
            parts.append(strip_macros(text))
    return "\n".join(parts).strip()


def join_capped(bodies: list[str], max_entries: int = 2, max_chars: int = 3000) -> str:
    out: list[str] = []
    total = 0
    for body in bodies:
        if not body or not body.strip():
            continue
        chunk = body.strip()
        sep = 2 if out else 0
        if total + sep + len(chunk) > max_chars:
            room = max_chars - total - sep
            if room > 0:
                out.append(chunk[:room])
            break
        out.append(chunk)
        total += sep + len(chunk)
        if len(out) >= max_entries:
            break
    return "\n\n".join(out)


def build_purpose_with_guide(tooltip: str, purpose_lines: list[str], guide: str) -> str:
    body = []
    if tooltip and tooltip.strip():
        body.append(tooltip.strip())
    for line in purpose_lines or []:
        if line and line.strip():
            body.append(line.strip())
    parts = []
    if body:
        parts.append("[PURPOSE]\n" + "\n".join(body))
    if guide and guide.strip():
        g = guide.strip()
        if not g.startswith("[GUIDE]"):
            g = "[GUIDE]\n" + g
        parts.append(g)
    return "\n".join(parts)


SAMPLE = {
    "name": "Cursed Ingot",
    "icon": "evilcraft:dark_gem",
    "extra_recipe_mappings": {"evilcraft:dark_gem_crushed": 0},
    "pages": [
        {"type": "patchouli:text", "text": "Used in dark rituals.$(br)Right-click the altar."},
        {"type": "patchouli:spotlight", "item": "evilcraft:dark_gem", "text": "Spotlight fluff"},
        {"type": "patchouli:crafting", "item": "minecraft:stick"},
    ],
}


def main() -> None:
    assert normalize_item_key("evilcraft:dark_gem{foo:1}") == "evilcraft:dark_gem"
    assert normalize_item_key("minecraft:dirt#0") == "minecraft:dirt"
    assert references_item(SAMPLE, "evilcraft:dark_gem")
    assert references_item(SAMPLE, "evilcraft:dark_gem_crushed")
    assert not references_item(SAMPLE, "minecraft:dirt")
    # crafting page item alone
    craft_only = {"name": "Stick tip", "icon": "minecraft:book", "pages": [{"type": "crafting", "item": "minecraft:stick"}]}
    assert references_item(craft_only, "minecraft:stick")

    text = extract_plain_text(SAMPLE)
    assert "Cursed Ingot" in text
    assert "dark rituals" in text
    assert "Right-click the altar" in text
    assert "$(br)" not in text
    assert "Spotlight fluff" not in text  # spotlight type is not a text page

    capped = join_capped(["aaaa", "bbbb", "cccc"], 2, 3000)
    assert capped == "aaaa\n\nbbbb"
    assert len(join_capped(["x" * 100], 1, 10)) == 10

    block = build_purpose_with_guide("Tip line", ["Right-click dirt"], text)
    assert block.startswith("[PURPOSE]\n")
    assert "[GUIDE]" in block
    assert "Tip line" in block
    assert "dark rituals" in block
    assert build_purpose_with_guide("", [], "") == ""
    assert build_purpose_with_guide("", [], "only guide").startswith("[GUIDE]\n")

    # round-trip JSON string
    obj = json.loads(json.dumps(SAMPLE))
    assert extract_plain_text(obj)
    print("check_patchouli_entry_scan OK")


if __name__ == "__main__":
    main()
