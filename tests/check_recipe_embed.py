#!/usr/bin/env python3
"""Mirror RecipeEmbed.parts — fails if recipe cards are not interleaved correctly."""
import re

MARKER = re.compile(r"\{\{\s*RECIPE(?:\s*:\s*(\d+))?\s*\}\}", re.I)
SOURCES = re.compile(r"(?m)(【來源】|\[Sources\])")


def strip_markers(text: str) -> str:
    t = MARKER.sub("", text or "")
    t = re.sub(r"[ \t]+\n", "\n", t)
    t = re.sub(r"\n{3,}", "\n\n", t)
    return t.strip()


def index_of_sources(text: str) -> int:
    m = SOURCES.search(text or "")
    return m.start() if m else -1


def first_paragraph_end(main: str) -> int:
    nn = main.find("\n\n")
    if nn >= 0:
        return nn
    n = main.find("\n")
    if n >= 0:
        return n
    return len(main)


def parts(answer: str, card_count: int) -> list[tuple[str, int]]:
    """Return list of ('text', -1) or ('card', index)."""
    raw = answer or ""
    if card_count <= 0:
        cleaned = strip_markers(raw)
        return [("text", -1, cleaned)] if cleaned else []

    if not MARKER.search(raw):
        sources_at = index_of_sources(raw)
        main = raw[:sources_at] if sources_at >= 0 else raw
        sources = raw[sources_at:] if sources_at >= 0 else ""
        split = first_paragraph_end(main)
        before = main[:split].strip()
        after = main[split:].strip()
        out: list[tuple[str, int, str]] = []
        if before:
            out.append(("text", -1, before))
        for i in range(card_count):
            out.append(("card", i, ""))
        rest = after
        if sources:
            rest = (after + "\n\n" + sources.strip()).strip() if after else sources.strip()
        if rest:
            out.append(("text", -1, rest))
        return out

    used = [False] * card_count
    next_auto = 0
    out = []
    last = 0
    for m in MARKER.finditer(raw):
        before = raw[last : m.start()].strip()
        if before:
            out.append(("text", -1, before))
        if m.group(1) is not None:
            idx = int(m.group(1))
        else:
            while next_auto < card_count and used[next_auto]:
                next_auto += 1
            idx = next_auto if next_auto < card_count else -1
            if idx >= 0:
                next_auto += 1
        if 0 <= idx < card_count and not used[idx]:
            used[idx] = True
            out.append(("card", idx, ""))
        last = m.end()
    tail = raw[last:].strip()
    if tail:
        out.append(("text", -1, tail))
    unused = [i for i, u in enumerate(used) if not u]
    if unused:
        if out and out[-1][0] == "text":
            last_t = out.pop()
            src = index_of_sources(last_t[2])
            if src >= 0:
                before = last_t[2][:src].strip()
                after = last_t[2][src:].strip()
                if before:
                    out.append(("text", -1, before))
                for i in unused:
                    out.append(("card", i, ""))
                if after:
                    out.append(("text", -1, after))
            else:
                out.append(last_t)
                for i in unused:
                    out.append(("card", i, ""))
        else:
            for i in unused:
                out.append(("card", i, ""))
    return out


def kinds(answer: str, n: int) -> list[str]:
    return [p[0] if p[0] == "text" else f"card{p[1]}" for p in parts(answer, n)]


def main() -> None:
    # fallback: after first paragraph, before sources
    k = kinds("第一段說明\n\n第二段步驟\n\n【來源】JEI", 2)
    assert k == ["text", "card0", "card1", "text"], k
    assert "【來源】" in parts("第一段說明\n\n第二段步驟\n\n【來源】JEI", 2)[-1][2]

    # explicit markers
    k = kinds("材料如下\n{{RECIPE}}\n然後去挖礦\n【來源】JEI", 1)
    assert k == ["text", "card0", "text"], k
    assert "{{RECIPE}}" not in parts("材料如下\n{{RECIPE}}\n然後去挖礦", 1)[0][2]

    k = kinds("A\n{{RECIPE:1}}\nB\n{{RECIPE:0}}\nC", 2)
    assert k == ["text", "card1", "text", "card0", "text"], k

    # no cards → strip markers
    assert parts("hi {{RECIPE}} there", 0)[0][0] == "text"
    assert "{{" not in parts("hi {{RECIPE}} there", 0)[0][2]
    assert "{{" not in strip_markers("x {{RECIPE:0}} y")

    print("ok recipe_embed")


if __name__ == "__main__":
    main()
