#!/usr/bin/env python3
"""Mirror RecipeEmbed.parts — sectionKey(sourceItemId) multi-select, no orphan fill."""
import re
from collections import OrderedDict

RECIPE_MARKER = re.compile(
    r"(?:\{\{\s*RECIPE(?:\s*:\s*(\d+|[a-z0-9_]+(?::[a-z0-9_./-]+)+))?\s*\}\}"
    r"|\[\[\s*recipe(?:_card)?\s*:\s*(\d+|[a-z0-9_]+(?::[a-z0-9_./-]+)+)\s*\]\])",
    re.I,
)
ITEM_MARKER = re.compile(
    r"(?:\[\[\s*item\s*:\s*([a-z0-9_]+(?::[a-z0-9_./-]+)+)(?:\s*[×xX*]\s*(\d+))?\s*\]\]"
    r"|\{\{\s*item\s*:\s*([a-z0-9_]+(?::[a-z0-9_./-]+)+)(?:\s*[×xX*]\s*(\d+))?\s*\}\}"
    r"|\{\s*item\s*:\s*([a-z0-9_]+(?::[a-z0-9_./-]+)+)(?:\s*[×xX*]\s*(\d+))?\s*\})",
    re.I,
)
ANY = re.compile(RECIPE_MARKER.pattern + "|" + ITEM_MARKER.pattern, re.I)
ORPHAN_RECIPE = re.compile(
    r"(?i)\[\[\s*recipe(?:_card)?\s*:[^\]]*\]\]|\{\{\s*RECIPE(?:\s*:[^}]*)?\s*\}\}"
)
# Must match ReplySources.HEADER / RecipeEmbed (zh_tw + zh_cn + en)
SOURCES = re.compile(r"(?m)(【來源】|【来源】|\[Sources\])")


def normalize_registry_ref(ref: str) -> str:
    r = (ref or "").strip().lower()
    if not r:
        return r
    if re.fullmatch(r"\d+", r):
        return r
    if r.startswith("mod:") and ":" in r[4:]:
        return r[4:]
    return r


def strip_markers(text: str) -> str:
    t = ANY.sub("", text or "")
    t = ORPHAN_RECIPE.sub("", t)
    t = re.sub(r"[ \t]+\n", "\n", t)
    t = re.sub(r"\n{3,}", "\n\n", t)
    return t.strip()


def strip_recipe_markers_only(text: str) -> str:
    t = RECIPE_MARKER.sub("", text or "")
    t = ORPHAN_RECIPE.sub("", t)
    t = re.sub(r"[ \t]+\n", "\n", t)
    t = re.sub(r"\n{3,}", "\n\n", t)
    return t.strip()


def is_item_token(token: str) -> bool:
    t = re.sub(r"\s+", "", (token or "").lower())
    return t.startswith("[[item:") or t.startswith("{{item:") or t.startswith("{item:")


def item_id_from_match(m: re.Match) -> str:
    return normalize_registry_ref(m.group(1) or m.group(3) or m.group(5) or "")


def item_count_from_match(m: re.Match) -> int:
    c = m.group(2) or m.group(4) or m.group(6)
    if not c:
        return 1
    try:
        return max(1, int(c))
    except ValueError:
        return 1


def index_of_sources(text: str) -> int:
    m = SOURCES.search(text or "")
    return m.start() if m else -1


def has_inline_interleave(kinds: list[str]) -> bool:
    """kinds: sequence of 'text'|'item'|'card' — mirrors RecipeEmbed.hasInlineInterleave."""
    saw_card = False
    for k in kinds:
        if k == "card":
            saw_card = True
            continue
        if not saw_card:
            continue
        if k == "item":
            return True
        if k == "text":
            return True
    return False


def section_key(source_id: str, primary_output_id: str) -> str:
    """Mirror RecipeCard.sectionKey — prefer Ask sourceItemId."""
    s = (source_id or "").lower()
    if s:
        return s
    return (primary_output_id or "").lower()


def _line_starts_with_needle(lower_line: str, needle: str) -> bool:
    if lower_line == needle:
        return True
    if not lower_line.startswith(needle):
        return False
    nxt = lower_line[len(needle)]
    return nxt in " \t:：-—–(（"


def _fill_by_item_markers(
    main_raw: str,
    groups: OrderedDict[str, list[int]],
    buckets: dict[str, list[str]],
    preamble: list[str],
) -> bool:
    hits = []
    for m in ITEM_MARKER.finditer(main_raw or ""):
        oid = item_id_from_match(m)
        if oid in groups:
            hits.append((m.start(), m.end(), oid))
    if not hits:
        return False
    last = 0
    current = None
    for start, end, oid in hits:
        before = strip_recipe_markers_only(main_raw[last:start]).strip()
        if before:
            if current and current in buckets:
                buckets[current].append(before)
            else:
                preamble.append(before)
        current = oid
        last = end
    tail = strip_markers(main_raw[last:]).strip()
    if tail:
        if current and current in buckets:
            buckets[current].append(tail)
        else:
            preamble.append(tail)
    return True


def _fill_by_line_start_names(
    main: str,
    groups: OrderedDict[str, list[int]],
    names: dict[str, str],
    buckets: dict[str, list[str]],
    preamble: list[str],
) -> None:
    if not main:
        return
    ids = list(groups.keys())
    needles: list[tuple[int, str]] = []
    for i, oid in enumerate(ids):
        needles.append((i, oid.lower()))
        nm = (names.get(oid) or "").lower()
        if len(nm) >= 2:
            needles.append((i, nm))
    needles.sort(key=lambda x: -len(x[1]))

    headers: list[tuple[int, int, int]] = []  # line_start, content_start, id_idx
    offset = 0
    lines = main.split("\n")
    for li, line in enumerate(lines):
        lead = len(line) - len(line.lstrip())
        lower = line[lead:].lower()
        best_idx, best_len = -1, 0
        for idx, needle in needles:
            if len(needle) < best_len:
                break
            if _line_starts_with_needle(lower, needle) and len(needle) > best_len:
                best_len = len(needle)
                best_idx = idx
        if best_idx >= 0:
            content = offset + lead + best_len
            while content < offset + len(line) and main[content] in " \t:：-—–":
                content += 1
            if content >= offset + len(line):
                content = offset + len(line)
                if content < len(main) and main[content] == "\n":
                    content += 1
            headers.append((offset, content, best_idx))
        offset += len(line)
        if li < len(lines) - 1:
            offset += 1

    if headers:
        if headers[0][0] > 0:
            lead = main[: headers[0][0]].strip()
            if lead:
                preamble.append(lead)
        for i, (start, content, idx) in enumerate(headers):
            end = headers[i + 1][0] if i + 1 < len(headers) else len(main)
            chunk = main[content:end].strip()
            oid = ids[idx]
            if chunk:
                buckets[oid].append(chunk)
        return
    preamble.append(main)


def section_by_outputs(
    main: str,
    section_ids: list[str],
    names: dict[str, str] | None = None,
) -> list[tuple[str, str]]:
    """
    Mirror RecipeEmbed.sectionByOutputs kind sequence (no MC stacks).
    Prefer [[item:id]] split; else line-start display names; never orphan-fill.
    """
    groups: OrderedDict[str, list[int]] = OrderedDict()
    for i, oid in enumerate(section_ids):
        groups.setdefault(oid, []).append(i)

    src = index_of_sources(main)
    main_raw = main[:src] if src >= 0 else main
    sources = main[src:].strip() if src >= 0 else ""

    name_map = names or {}
    buckets: dict[str, list[str]] = {oid: [] for oid in groups}
    preamble: list[str] = []
    if not _fill_by_item_markers(main_raw, groups, buckets, preamble):
        body = strip_markers(main_raw).strip()
        _fill_by_line_start_names(body, groups, name_map, buckets, preamble)

    out: list[tuple[str, str]] = []
    if preamble:
        out.append(("text", "\n\n".join(preamble)))
    for oid, idxs in groups.items():
        out.append(("item", oid))
        if buckets[oid]:
            out.append(("text", "\n\n".join(buckets[oid])))
        for _ in idxs:
            out.append(("card", oid))
    if sources:
        out.append(("text", sources))
    return out


def text_under_item(parts: list[tuple[str, str]], item_id: str) -> str:
    """Concatenate TEXT parts immediately after the given ITEM until next ITEM/sources."""
    chunks: list[str] = []
    i = 0
    while i < len(parts):
        k, p = parts[i]
        if k == "item" and p == item_id:
            i += 1
            while i < len(parts):
                k2, p2 = parts[i]
                if k2 == "item":
                    break
                if k2 == "text" and index_of_sources(p2) == 0:
                    break
                if k2 == "text":
                    chunks.append(p2)
                i += 1
            break
        i += 1
    return "\n\n".join(chunks)


def no_trailing_card_pile(parts: list[tuple[str, str]]) -> bool:
    """True if last non-sources part is not a lonely card dump after all items done."""
    kinds = [k for k, _ in parts]
    end = len(kinds)
    if end and kinds[-1] == "text" and index_of_sources(parts[-1][1]) == 0:
        end -= 1
    if end == 0:
        return True
    last_item = max((i for i, k in enumerate(kinds[:end]) if k == "item"), default=-1)
    if last_item < 0:
        return False
    return True


def main() -> None:
    assert "{{RECIPE}}" not in strip_markers("A {{RECIPE}} B")
    assert "[[recipe:" not in strip_markers("x [[recipe:create:brass]] y")
    assert "[[recipe_card:" not in strip_markers("x [[recipe_card:0]] y")
    assert "[[item:" not in strip_markers("x [[item:minecraft:diamond]] y")
    # recipe_card index markers interleave (guide style)
    assert RECIPE_MARKER.search("blurb\n[[recipe_card:0]]\nmore\n[[recipe_card:1]]")
    m0 = RECIPE_MARKER.search("[[recipe_card:0]]")
    assert m0 and (m0.group(1) == "0" or m0.group(2) == "0"), m0.groups() if m0 else None
    # must not treat [[recipe_cards:on]] as a recipe_card slot
    assert not RECIPE_MARKER.search("[[recipe_cards:on]]")
    # LLM copies prompt placeholder "mod:id" → mod:ns:path (screenshot leak)
    leak = "正文\n[[recipe:mod:tetra:scroll_rolled]]\n尾"
    assert RECIPE_MARKER.search(leak), leak
    assert "[[recipe:" not in strip_markers(leak), strip_markers(leak)
    assert normalize_registry_ref("mod:tetra:scroll_rolled") == "tetra:scroll_rolled"
    assert normalize_registry_ref("tetra:scroll_rolled") == "tetra:scroll_rolled"
    assert normalize_registry_ref("mod:custom_thing") == "mod:custom_thing"  # real ns=mod
    assert "[[recipe:" not in strip_markers("x [[recipe:???broken]] y")  # orphan scrub
    assert item_id_from_match(ITEM_MARKER.search("[[item:mod:tetra:scroll_rolled]]")) == (
        "tetra:scroll_rolled"
    )

    sample = (
        "[[item:minecraft:iron_ingot]] Iron\n"
        "Smelt ore.\n"
        "[[recipe:minecraft:iron_ingot]]\n"
        "[[item:minecraft:coal]] Coal\n"
        "Fuel.\n"
        "[[recipe:minecraft:coal]]\n"
        "[Sources] JEI"
    )
    markers = list(ANY.finditer(sample))
    assert len(markers) == 4, markers
    kinds = []
    for m in markers:
        kinds.append("item" if is_item_token(m.group()) else "recipe")
    assert kinds == ["item", "recipe", "item", "recipe"], kinds

    assert not is_item_token("[[recipe:minecraft:item_frame]]")
    assert is_item_token("[[item:minecraft:item_frame]]")
    assert is_item_token("[[ item:minecraft:dirt ]]")

    assert index_of_sources("正文\n\n【来源】JEI") >= 0
    assert index_of_sources("正文\n\n【來源】JEI") >= 0
    assert index_of_sources("body\n\n[Sources] JEI") >= 0
    body = "第一段\n\n第二段\n\n【来源】JEI"
    src = index_of_sources(body)
    assert src > 0
    assert "【来源】" in body[src:]
    assert "第二段" in body[:src]

    assert not has_inline_interleave(["text", "card", "card", "card"])
    assert has_inline_interleave(["item", "text", "card", "item", "text", "card"])
    assert has_inline_interleave(["text", "card", "text", "card"])

    names_ic = {
        "minecraft:iron_ingot": "iron ingot",
        "minecraft:coal": "coal",
    }
    wall = (
        "Iron ingot comes from smelting ore.\n\n"
        "Coal is fuel for furnaces.\n\n"
        "[Sources] JEI"
    )
    parts = section_by_outputs(
        wall, ["minecraft:iron_ingot", "minecraft:coal", "minecraft:coal"], names_ic
    )
    kinds2 = [k for k, _ in parts]
    assert kinds2.count("item") == 2, kinds2
    assert kinds2.count("card") == 3, kinds2
    assert has_inline_interleave(kinds2), kinds2
    assert "smelting" in text_under_item(parts, "minecraft:iron_ingot").lower()
    assert "fuel" in text_under_item(parts, "minecraft:coal").lower()
    assert "fuel" not in text_under_item(parts, "minecraft:iron_ingot").lower()

    item_only = (
        "[[item:minecraft:iron_ingot]] Smelt it.\n\n"
        "[[item:minecraft:coal]] Burn it.\n\n"
        "[Sources] JEI"
    )
    parts3 = section_by_outputs(item_only, ["minecraft:iron_ingot", "minecraft:coal"])
    assert has_inline_interleave([k for k, _ in parts3]), parts3
    assert "Smelt" in text_under_item(parts3, "minecraft:iron_ingot")
    assert "Burn" in text_under_item(parts3, "minecraft:coal")

    # No headers → whole body preamble; sections still get cards (no orphan fill)
    one_wall = "Both are useful in early game.\n\n[Sources] JEI"
    parts4 = section_by_outputs(one_wall, ["minecraft:iron_ingot", "minecraft:coal"])
    kinds4 = [k for k, _ in parts4]
    assert kinds4.count("item") == 2
    assert kinds4.count("card") == 2
    assert text_under_item(parts4, "minecraft:iron_ingot") == ""
    assert text_under_item(parts4, "minecraft:coal") == ""
    assert any(k == "text" and "Both are useful" in p for k, p in parts4)

    keys = [
        section_key("minecraft:golden_axe", "minecraft:golden_axe"),
        section_key("minecraft:golden_axe", "ftbquests:quest_reward_book"),
        section_key("minecraft:coal", "minecraft:coal"),
    ]
    assert keys == [
        "minecraft:golden_axe",
        "minecraft:golden_axe",
        "minecraft:coal",
    ]
    partial_markers = (
        "[[item:minecraft:coal]] Coal is fuel.\n"
        "[[recipe:minecraft:coal]]\n\n"
        "Golden axe later…\n\n"
        "[Sources] JEI"
    )
    parts5 = section_by_outputs(partial_markers, keys)
    kinds5 = [k for k, _ in parts5]
    assert kinds5.count("item") == 2, kinds5
    assert kinds5.count("card") == 3, kinds5
    axe_idxs = [i for i, (k, p) in enumerate(parts5) if k == "card" and p == "minecraft:golden_axe"]
    coal_idxs = [i for i, (k, p) in enumerate(parts5) if k == "card" and p == "minecraft:coal"]
    assert len(axe_idxs) == 2, parts5
    assert len(coal_idxs) == 1, parts5
    src_i = next(i for i, (k, p) in enumerate(parts5) if k == "text" and index_of_sources(p) == 0)
    assert all(i < src_i for i, (k, _) in enumerate(parts5) if k == "card"), parts5
    assert no_trailing_card_pile(parts5)

    multi = section_by_outputs(
        "[[item:minecraft:golden_axe]] Axe tip.\n\n[[item:minecraft:coal]] Coal tip.\n\n[Sources] JEI",
        ["minecraft:golden_axe", "minecraft:golden_axe", "minecraft:golden_axe", "minecraft:coal"],
    )
    assert [p for k, p in multi if k == "card"].count("minecraft:golden_axe") == 3
    assert has_inline_interleave([k for k, _ in multi])

    # --- axe offset regression: stone/diamond/gold must not swap ---
    axe_names = {
        "minecraft:stone_axe": "stone axe",
        "minecraft:diamond_axe": "diamond axe",
        "minecraft:golden_axe": "golden axe",
    }
    axe_ids = [
        "minecraft:stone_axe",
        "minecraft:diamond_axe",
        "minecraft:golden_axe",
    ]
    # Classic offset bait: single newlines, title then body (no blank lines)
    axe_offset = (
        "Stone Axe\n"
        "Basic woodcutting tool.\n"
        "Diamond Axe\n"
        "High damage and durability.\n"
        "Golden Axe\n"
        "Soft but easy to enchant.\n\n"
        "[Sources] JEI"
    )
    axe_parts = section_by_outputs(axe_offset, axe_ids, axe_names)
    stone_t = text_under_item(axe_parts, "minecraft:stone_axe").lower()
    diam_t = text_under_item(axe_parts, "minecraft:diamond_axe").lower()
    gold_t = text_under_item(axe_parts, "minecraft:golden_axe").lower()
    assert "basic" in stone_t and "high damage" not in stone_t, stone_t
    assert "high damage" in diam_t and "soft" not in diam_t, diam_t
    assert "soft" in gold_t and "basic" not in gold_t, gold_t

    # Orphan must NOT fill empty diamond (old bug: round-robin into emptyIds)
    orphan = (
        "Intro leftover without any item header.\n\n"
        "Stone Axe\n"
        "Basic only.\n\n"
        "Golden Axe\n"
        "Enchantable.\n\n"
        "[Sources] JEI"
    )
    orphan_parts = section_by_outputs(orphan, axe_ids, axe_names)
    assert text_under_item(orphan_parts, "minecraft:diamond_axe") == ""
    assert "leftover" in "\n".join(p for k, p in orphan_parts if k == "text").lower()
    assert "leftover" not in text_under_item(orphan_parts, "minecraft:diamond_axe").lower()
    assert "basic" in text_under_item(orphan_parts, "minecraft:stone_axe").lower()
    assert "enchantable" in text_under_item(orphan_parts, "minecraft:golden_axe").lower()
    assert orphan_parts[0][0] == "text" and "leftover" in orphan_parts[0][1].lower()

    print("check_recipe_embed OK")


if __name__ == "__main__":
    main()
