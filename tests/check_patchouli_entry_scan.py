#!/usr/bin/env python3
"""Mirrors PatchouliEntryScan — item match + text extract + GuidebookEntry parse."""

import json
import re


MAX_TEXT_CLIP = 2000
MAX_LINKED_ITEMS = 32


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


def is_text_like_page(page: dict) -> bool:
    if is_text_page(page):
        return True
    t = str(page.get("type") or "").lower()
    return (t in ("spotlight", "patchouli:spotlight") or t.endswith(":spotlight")) and "text" in page


def extract_text_clip(entry: dict, max_chars: int = MAX_TEXT_CLIP) -> str:
    parts: list[str] = []
    for page in entry.get("pages") or []:
        if not isinstance(page, dict) or not is_text_like_page(page):
            continue
        text = page.get("text")
        if isinstance(text, str) and text.strip():
            parts.append(strip_macros(text))
    joined = "\n".join(parts).strip()
    return joined if len(joined) <= max_chars else joined[:max_chars]


def collect_linked_items(entry: dict) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()

    def add(raw: str | None) -> None:
        kid = normalize_item_key(raw)
        if not kid or kid.startswith("#") or kid in seen:
            return
        seen.add(kid)
        out.append(kid)

    add(entry.get("icon") if isinstance(entry.get("icon"), str) else None)
    erm = entry.get("extra_recipe_mappings") or {}
    if isinstance(erm, dict):
        for k in erm:
            add(k)
    for page in entry.get("pages") or []:
        if not isinstance(page, dict):
            continue
        item = page.get("item")
        if isinstance(item, str):
            add(item)
        items = page.get("items")
        if isinstance(items, list):
            for it in items:
                if isinstance(it, str):
                    add(it)
    return out[:MAX_LINKED_ITEMS]


def parse_entry_path(source_path: str, resource_namespace: str = "") -> dict:
    ns_hint = (resource_namespace or "").strip().lower()
    if not source_path:
        return {"bookNs": ns_hint, "bookId": "", "lang": "", "entryId": ""}
    p = source_path.replace("\\", "/")
    book_ns = ns_hint
    for prefix in ("assets/", "data/"):
        idx = 0 if p.startswith(prefix) else p.find("/" + prefix)
        if idx < 0:
            continue
        cut = idx + (0 if p.startswith(prefix) else 1) + len(prefix)
        slash = p.find("/", cut)
        if slash > cut:
            book_ns = p[cut:slash].lower()
            p = p[slash + 1 :]
            break
    books = p.find("patchouli_books/")
    if books < 0:
        return {"bookNs": book_ns, "bookId": "", "lang": "", "entryId": ""}
    p = p[books + len("patchouli_books/") :]
    parts = p.split("/")
    if len(parts) < 4:
        return {"bookNs": book_ns, "bookId": parts[0] if parts else "", "lang": "", "entryId": ""}
    book_id, lang = parts[0], parts[1]
    entries_idx = next((i for i, s in enumerate(parts) if s == "entries"), -1)
    if entries_idx < 0 or entries_idx + 1 >= len(parts):
        return {"bookNs": book_ns, "bookId": book_id, "lang": lang, "entryId": ""}
    stem_parts = []
    for i, seg in enumerate(parts[entries_idx + 1 :], start=entries_idx + 1):
        if i == len(parts) - 1 and seg.endswith(".json"):
            seg = seg[:-5]
        if seg:
            stem_parts.append(seg)
    return {"bookNs": book_ns, "bookId": book_id, "lang": lang.lower(), "entryId": "/".join(stem_parts)}


def to_entry(entry: dict, source_path: str, resource_namespace: str = "") -> dict:
    path = parse_entry_path(source_path, resource_namespace)
    title = entry.get("name") if isinstance(entry.get("name"), str) else ""
    return {
        **path,
        "title": (title or "").strip(),
        "textClip": extract_text_clip(entry),
        "linkedItems": collect_linked_items(entry),
        "sourcePath": source_path.replace("\\", "/"),
    }


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

SAMPLE_PATH = "assets/evilcraft/patchouli_books/origins_of_darkness/en_us/entries/items/cursed_ingot.json"

PURE_TEXT = {
    "name": "Intro Lore",
    "pages": [{"type": "text", "text": "Once upon a pack..."}],
}


def main() -> None:
    assert normalize_item_key("evilcraft:dark_gem{foo:1}") == "evilcraft:dark_gem"
    assert normalize_item_key("minecraft:dirt#0") == "minecraft:dirt"
    assert references_item(SAMPLE, "evilcraft:dark_gem")
    assert references_item(SAMPLE, "evilcraft:dark_gem_crushed")
    assert not references_item(SAMPLE, "minecraft:dirt")
    craft_only = {"name": "Stick tip", "icon": "minecraft:book", "pages": [{"type": "crafting", "item": "minecraft:stick"}]}
    assert references_item(craft_only, "minecraft:stick")

    text = extract_plain_text(SAMPLE)
    assert "Cursed Ingot" in text
    assert "dark rituals" in text
    assert "Right-click the altar" in text
    assert "$(br)" not in text
    assert "Spotlight fluff" not in text

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

    obj = json.loads(json.dumps(SAMPLE))
    assert extract_plain_text(obj)

    # WP1 structured entry
    ge = to_entry(SAMPLE, SAMPLE_PATH)
    assert ge["bookNs"] == "evilcraft"
    assert ge["bookId"] == "origins_of_darkness"
    assert ge["entryId"] == "items/cursed_ingot"
    assert ge["lang"] == "en_us"
    assert ge["title"] == "Cursed Ingot"
    assert "evilcraft:dark_gem" in ge["linkedItems"]
    assert "evilcraft:dark_gem_crushed" in ge["linkedItems"]
    assert "minecraft:stick" in ge["linkedItems"]
    assert "dark rituals" in ge["textClip"]
    assert "Cursed Ingot" not in ge["textClip"]
    assert "Spotlight fluff" in ge["textClip"]  # WP4 spotlight text in clip
    assert "$(br)" not in ge["textClip"]

    data_path = "data/ars_nouveau/patchouli_books/worn_notebook/zh_tw/entries/getting_started/world_generation.json"
    path = parse_entry_path(data_path)
    assert path["bookNs"] == "ars_nouveau"
    assert path["bookId"] == "worn_notebook"
    assert path["lang"] == "zh_tw"
    assert path["entryId"] == "getting_started/world_generation"
    rl = parse_entry_path(
        "patchouli_books/worn_notebook/en_us/entries/foo/bar.json",
        "ars_nouveau",
    )
    assert rl["bookNs"] == "ars_nouveau"
    assert rl["entryId"] == "foo/bar"

    pure = to_entry(PURE_TEXT, "assets/pack/patchouli_books/lore/en_us/entries/intro.json")
    assert pure["title"] == "Intro Lore"
    assert pure["linkedItems"] == []
    assert "Once upon" in pure["textClip"]
    assert pure["bookNs"] == "pack"

    long_entry = {
        "name": "Long",
        "pages": [{"type": "text", "text": "Z" * 5000}],
    }
    assert len(extract_text_clip(long_entry)) == MAX_TEXT_CLIP

    # WP4 spotlight text included in clip
    spot = {
        "name": "Gem",
        "icon": "evilcraft:dark_gem",
        "pages": [
            {"type": "patchouli:spotlight", "item": "evilcraft:dark_gem", "text": "Spotlight fluff now indexed"},
        ],
    }
    assert "Spotlight fluff" in extract_text_clip(spot)
    # extract_plain_text still skips spotlight (legacy text-only pages)
    assert "Spotlight fluff" not in extract_plain_text(spot)

    print("check_patchouli_entry_scan OK")


if __name__ == "__main__":
    main()
