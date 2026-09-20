#!/usr/bin/env python3
"""Mirror AskMarkerIntegrity — packai:items marker vs debris collapse."""
from __future__ import annotations

import re
from pathlib import Path

# L2: intact <!--…--> and damaged <!-…->
MARKER = re.compile(r"<!-{1,2}\s*packai:items=([^>]+?)\s*-{1,2}>", re.I)
# L3: no ASCII -
DUP_SEPARATORS = re.compile(r"([、，,／|;；·:：])(?:[ \t\u3000]*\1)+")


def strip_marker(answer: str | None) -> str:
    if answer is None:
        return ""
    return MARKER.sub("", answer).strip()


def scrub_echo(answer: str | None) -> str:
    """Minimal mirror: strip marker then collapse dup seps (not full AskReplyScrub)."""
    if not answer:
        return ""
    t = strip_marker(answer)
    return DUP_SEPARATORS.sub(r"\1", t)


def extract_marker_body(answer: str | None) -> str | None:
    if not answer:
        return None
    m = MARKER.search(answer)
    return m.group(1).strip() if m else None


def main() -> None:
    intact = "<!--packai:items=minecraft:dirt|Dirt Block-->"
    damaged = "<!-packai:items=minecraft:dirt|Dirt Block->"
    ref_body = "minecraft:dirt|Dirt Block"

    before = f"推薦用泥土{intact}\n\n【来源】JEI"
    assert "packai:items" not in scrub_echo(before).lower()
    assert extract_marker_body(before) == ref_body

    after = f"推薦用泥土\n\n【来源】JEI\n{intact}"
    assert "packai:items" not in scrub_echo(after).lower()
    assert extract_marker_body(after) == ref_body

    assert strip_marker(f"見{damaged}完") == "見完"
    assert extract_marker_body(f"見{damaged}完") == ref_body

    neg1 = "keep <!x-packai:items=A--> here"
    neg2 = "keep <!-- notpackai:items=A --> here"
    assert "<!x-packai:items=A-->" in scrub_echo(neg1)
    assert "<!-- notpackai:items=A -->" in scrub_echo(neg2)

    assert scrub_echo("甲、、乙，，丙") == "甲、乙，丙"
    assert "a--b" in scrub_echo("range a--b end")

    # Source gate: Java MARKER + scrub strip + no dash in DUP
    root = Path(__file__).resolve().parents[1]
    for tree in ("forge/1.19.2", "neoforge/1.21.1"):
        ir = (root / tree / "src/main/java/com/skps9/packai/logic/ItemResolver.java").read_text(
            encoding="utf-8"
        )
        scrub = (root / tree / "src/main/java/com/skps9/packai/logic/AskReplyScrub.java").read_text(
            encoding="utf-8"
        )
        assert "<!-{1,2}" in ir, tree
        assert "PACKAI_ITEMS_MARKER" in scrub, tree
        assert "：\\\\-])" not in scrub, f"{tree}: DUP must not collapse -"
        assert "collapsing {@code --} broke" in scrub, tree

    print("check_ask_marker_integrity OK")


if __name__ == "__main__":
    main()
