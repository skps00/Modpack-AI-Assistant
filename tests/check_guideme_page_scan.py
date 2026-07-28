#!/usr/bin/env python3
"""Mirrors GuideMePageScan — frontmatter item_ids + markdown strip without GuideME runtime."""

import re

FRONTMATTER = re.compile(r"^---\r?\n(.*?)\r?\n---\r?\n?", re.DOTALL)
ITEM_IDS_BLOCK = re.compile(r"(?m)^item_ids:\s*\r?\n((?:^[ \t]*-[ \t]*.+\r?\n?)+)")
ITEM_ID_LINE = re.compile(r"^[ \t]*-[ \t]*(.+)$", re.MULTILINE)
NAV_TITLE = re.compile(
    r"(?m)^navigation:\s*\r?\n(?:^[ \t]+.+\r?\n)*?^[ \t]+title:\s*[\"']?([^\"'\r\n]+)[\"']?"
)
MDX_TAG = re.compile(r"<[^>]+>")
MD_LINK = re.compile(r"\[([^\]]+)\]\([^)]*\)")
MD_IMAGE = re.compile(r"!\[[^\]]*\]\([^)]*\)")
MD_HEADING = re.compile(r"(?m)^#{1,6}\s+")
MD_EMPHASIS = re.compile(r"[*_]{1,3}([^*_]+)[*_]{1,3}")
MD_CODE = re.compile(r"`([^`]+)`")


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


def frontmatter_body(md: str) -> str:
    m = FRONTMATTER.match(md or "")
    return m.group(1) if m else ""


def item_ids_from_frontmatter(md: str) -> list[str]:
    fm = frontmatter_body(md)
    if not fm:
        return []
    block = ITEM_IDS_BLOCK.search(fm)
    if not block:
        return []
    out: list[str] = []
    for m in ITEM_ID_LINE.finditer(block.group(1)):
        raw = m.group(1).strip()
        if len(raw) >= 2 and ((raw[0] == '"' and raw[-1] == '"') or (raw[0] == "'" and raw[-1] == "'")):
            raw = raw[1:-1]
        if raw.strip():
            out.append(raw.strip())
    return out


def references_item(md: str, item_id: str) -> bool:
    return any(id_mentions(i, item_id) for i in item_ids_from_frontmatter(md))


def navigation_title(md: str) -> str:
    fm = frontmatter_body(md)
    if not fm:
        return ""
    m = NAV_TITLE.search(fm)
    return m.group(1).strip() if m else ""


def strip_frontmatter(md: str) -> str:
    m = FRONTMATTER.match(md or "")
    return md[m.end() :] if m else (md or "")


def strip_markdown(raw: str) -> str:
    s = raw or ""
    s = MD_IMAGE.sub("", s)
    s = MD_LINK.sub(r"\1", s)
    s = MDX_TAG.sub("", s)
    s = MD_HEADING.sub("", s)
    s = MD_EMPHASIS.sub(r"\1", s)
    s = MD_CODE.sub(r"\1", s)
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    s = re.sub(r"[ \t]+\n", "\n", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip()


def extract_plain_text(md: str) -> str:
    parts: list[str] = []
    title = navigation_title(md)
    if title:
        parts.append(title)
    plain = strip_markdown(strip_frontmatter(md))
    if plain:
        parts.append(plain)
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


SAMPLE = """---
item_ids:
  - ae2:controller
  - minecraft:stick
navigation:
  title: Channels
  parent: index.md
---
# Channels
Used for ME networks. <ItemLink id="ae2:controller" />
See [docs](https://example.com) and **power**.
"""


def main() -> None:
    assert references_item(SAMPLE, "ae2:controller")
    assert references_item(SAMPLE, "minecraft:stick")
    assert not references_item(SAMPLE, "minecraft:dirt")
    assert navigation_title(SAMPLE) == "Channels"
    text = extract_plain_text(SAMPLE)
    assert "Channels" in text
    assert "ME networks" in text
    assert "docs" in text
    assert "power" in text
    assert "<ItemLink" not in text
    assert "**" not in text
    assert "https://example.com" not in text
    assert join_capped(["aaaa", "bbbb", "cccc"], 2, 3000) == "aaaa\n\nbbbb"
    assert len(join_capped(["x" * 100], 1, 10)) == 10
    print("check_guideme_page_scan OK")


if __name__ == "__main__":
    main()
