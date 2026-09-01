#!/usr/bin/env python3
"""Mirror AskCardFallback.ensureCards — mod fully manages card marker placement."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "AskCardFallback.java",
    ROOT
    / "neoforge"
    / "1.21.1"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "AskCardFallback.java",
)
SERVICE_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "client" / "service" / "AskService.java",
    ROOT
    / "neoforge"
    / "1.21.1"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "client"
    / "service"
    / "AskService.java",
)

HOW_GET = (
    "怎么来",
    "怎样来",
    "怎么取得",
    "怎么获得",
    "怎麼來",
    "怎樣來",
    "怎麼取得",
    "怎麼獲得",
)

METHOD_LINE = re.compile(r"(?m)^\s*(\d+)\.\s+([^\n:]+):\s*$")
CARD_MARKER = re.compile(r"\[\[recipe_card:\d+\]\]")

SECTION_PREFIXES = ("怎么用", "用途", "怎么来", "怎样来", "作为材料")


def looks_like_how_to_get(reply: str | None) -> bool:
    if not reply:
        return False
    if any(h in reply for h in HOW_GET):
        return True
    lower = reply.lower()
    return "how to get" in lower or "how to obtain" in lower


def is_section_title(line: str | None) -> bool:
    if line is None:
        return False
    trimmed = line.strip()
    if ":" not in trimmed:
        return False
    return any(trimmed.startswith(prefix) for prefix in SECTION_PREFIXES)


def strip_markers(reply: str) -> str:
    return CARD_MARKER.sub("", reply)


def count_method_lines(reply: str) -> int:
    return len(METHOD_LINE.findall(reply))


def collect_output_quest_indices(cards: list[dict]) -> list[int]:
    indices: list[int] = []
    for i, c in enumerate(cards):
        if c is None or c.get("empty") or c.get("input"):
            continue
        indices.append(i)
    return indices


def collect_input_indices(cards: list[dict]) -> list[int]:
    indices: list[int] = []
    for i, c in enumerate(cards):
        if c is None or c.get("empty") or not c.get("input"):
            continue
        indices.append(i)
    return indices


def find_block_end(reply: str, block_start: int, next_method_start: int) -> int:
    line_start = block_start
    while line_start < next_method_start:
        line_end = reply.find("\n", line_start)
        actual_line_end = next_method_start if line_end == -1 or line_end >= next_method_start else line_end
        line = reply[line_start:actual_line_end]
        if is_section_title(line):
            return line_start
        if line_end == -1 or line_end >= next_method_start:
            break
        line_start = line_end + 1
    return next_method_start


def find_last_line_end(reply: str, block_start: int, block_end: int) -> int:
    last_end = block_start
    pos = block_start
    while pos < block_end:
        line_end = reply.find("\n", pos)
        actual_line_end = block_end if line_end == -1 or line_end >= block_end else line_end
        line = reply[pos:actual_line_end]
        if line.strip():
            last_end = actual_line_end
        if line_end == -1 or line_end >= block_end:
            break
        pos = line_end + 1
    return last_end


def try_insert_after_methods(reply: str, card_indices: list[int]) -> str | None:
    method_starts: list[int] = []
    method_ends: list[int] = []
    for m in METHOD_LINE.finditer(reply):
        method_starts.append(m.start())
        method_ends.append(m.end())
    if not method_starts:
        return None
    count = min(len(method_starts), len(card_indices))
    insertions: list[tuple[int, int]] = []
    for i in range(count):
        block_start = method_ends[i]
        next_method_start = method_starts[i + 1] if i + 1 < len(method_starts) else len(reply)
        block_end = find_block_end(reply, block_start, next_method_start)
        insert_pos = find_last_line_end(reply, block_start, block_end)
        insertions.append((insert_pos, card_indices[i]))
    insertions.sort(key=lambda x: x[0], reverse=True)
    sb = reply
    for pos, card_i in insertions:
        sb = sb[:pos] + f"\n[[recipe_card:{card_i}]]" + sb[pos:]
    return sb


def append_at_end(reply: str, card_indices: list[int]) -> str:
    extra = " ".join(f"[[recipe_card:{i}]]" for i in card_indices)
    base = reply if reply.endswith("\n") else reply + "\n"
    return base + extra


def ensure_cards(reply: str | None, cards: list[dict] | None) -> str:
    """cards: {empty, input} in catalog order; mod strips model markers and reinserts."""
    if reply is None or not str(reply).strip():
        return "" if reply is None else reply
    if not cards:
        return reply
    if not looks_like_how_to_get(reply):
        return reply
    stripped = strip_markers(reply)
    output_indices = collect_output_quest_indices(cards)
    input_indices = collect_input_indices(cards)
    if not output_indices and not input_indices:
        return stripped
    if output_indices:
        method_inserted = try_insert_after_methods(stripped, output_indices)
        if method_inserted is not None:
            method_count = count_method_lines(stripped)
            inserted_count = min(method_count, len(output_indices))
            to_append = list(output_indices[inserted_count:]) + input_indices
            return method_inserted if not to_append else append_at_end(method_inserted, to_append)
    to_append = list(output_indices) + input_indices
    return append_at_end(stripped, to_append)


def test_behavior() -> None:
    how = "怎样来:\n1. 工作台:\n3铁锭+2木棍。"
    mixed = [
        {"empty": False, "input": False},  # 0 output
        {"empty": False, "input": False},  # 1 quest-as-output
        {"empty": False, "input": True},  # 2 input — append end
        {"empty": True, "input": False},  # 3 empty — skip
    ]
    filled = ensure_cards(how, mixed)
    assert "[[recipe_card:0]]" in filled
    assert filled.index("[[recipe_card:0]]") > filled.index("3铁锭+2木棍")
    assert "[[recipe_card:1]]" in filled  # remaining output appended
    assert "[[recipe_card:2]]" in filled  # input appended
    assert "[[recipe_card:3]]" not in filled
    assert filled.rstrip().endswith("[[recipe_card:1]] [[recipe_card:2]]")

    two_methods = (
        "怎样来:\n"
        "1. 工作台:\n"
        "3 个铁锭 + 2 根木棍合成。\n"
        "2. 动力合成器:\n"
        "同样材料可自动合成。"
    )
    two_filled = ensure_cards(two_methods, mixed)
    assert "[[recipe_card:0]]" in two_filled and "[[recipe_card:1]]" in two_filled
    assert two_filled.index("[[recipe_card:0]]") > two_filled.index("3 个铁锭 + 2 根木棍合成")
    assert two_filled.index("[[recipe_card:1]]") > two_filled.index("同样材料可自动合成")
    assert two_filled.index("2. 动力合成器:") > two_filled.index("[[recipe_card:0]]")
    assert two_filled.rstrip().endswith("[[recipe_card:2]]")

    # Model markers piled at end — strip and reinsert output after method, input at end
    model_piled = (
        "怎样来:\n"
        "1. 工作台:\n"
        "下界合金碎片 + 龙息 + 恶魂之泪合成获得。\n"
        "怎么用:\n"
        "1. 手持使用（右键）后...\n"
        "2. 作为材料：用于召唤祭坛的仪式...\n"
        "[[recipe_card:0]] [[recipe_card:2]]"
    )
    piled_filled = ensure_cards(model_piled, mixed)
    assert "[[recipe_card:0]]" in piled_filled
    assert piled_filled.index("[[recipe_card:0]]") > piled_filled.index("下界合金碎片")
    assert piled_filled.rstrip().endswith("[[recipe_card:1]] [[recipe_card:2]]")
    assert piled_filled.count("[[recipe_card:0]]") == 1
    assert piled_filled.count("[[recipe_card:2]]") == 1

    # Markers already in correct position — strip and reinsert, same placement (harmless)
    already_correct = how + "\n[[recipe_card:0]]"
    reinserted = ensure_cards(already_correct, mixed)
    assert "[[recipe_card:0]]" in reinserted
    assert reinserted.index("[[recipe_card:0]]") > reinserted.index("3铁锭+2木棍")
    assert reinserted.rstrip().endswith("[[recipe_card:1]] [[recipe_card:2]]")

    purpose_only = "怎么用:\n1. 手持挖掘。"
    assert ensure_cards(purpose_only, mixed) == purpose_only

    no_method = "怎样来:\n在工作台用铁锭和木棍合成。"
    no_method_filled = ensure_cards(no_method, [{"empty": False, "input": False}])
    assert no_method_filled.endswith("[[recipe_card:0]]")

    input_only = "怎样来:\n在工作台用铁锭和木棍合成。"
    input_only_filled = ensure_cards(
        input_only,
        [{"empty": False, "input": True}],
    )
    assert input_only_filled.endswith("[[recipe_card:0]]")

    assert ensure_cards(how, []) == how
    assert ensure_cards(how, None) == how
    assert ensure_cards("", mixed) == ""
    assert ensure_cards(None, mixed) == ""

    en = "How to get:\n1. Crafting Table:\nsticks + iron."
    en_filled = ensure_cards(en, [{"empty": False, "input": False}])
    assert "[[recipe_card:0]]" in en_filled
    assert en_filled.index("[[recipe_card:0]]") > en_filled.index("sticks + iron")


def check_source(path: Path) -> None:
    src = path.read_text(encoding="utf-8")
    assert "ensureCards" in src, f"{path}: missing ensureCards"
    assert "[[recipe_card:" in src, f"{path}: missing recipe_card marker"
    assert "collectInputIndices" in src, f"{path}: must append input cards"
    assert "stripMarkers" in src, f"{path}: must strip model markers"
    assert "CARD_MARKER" in src, f"{path}: missing marker strip pattern"
    assert "looksLikeHowToGet" in src, f"{path}: missing how-to-get gate"
    assert "tryInsertAfterMethods" in src, f"{path}: missing method-line insertion"
    assert "fully manages" in src.lower() or "mod fully" in src.lower()


def check_wiring(path: Path) -> None:
    src = path.read_text(encoding="utf-8")
    assert src.count("AskCardFallback.ensureCards") >= 2, f"{path}: wire both ask paths"
    idx = src.find("AskCardFallback.ensureCards")
    gate = src.find("RecipeCardsMode.resolveGateMarker", idx)
    assert gate > idx, f"{path}: ensureCards must run before resolveGateMarker"


def main() -> None:
    test_behavior()
    for p in HELPER_PATHS:
        assert p.is_file(), f"missing {p}"
        check_source(p)
    for p in SERVICE_PATHS:
        assert p.is_file(), f"missing {p}"
        check_wiring(p)
    print("check_ask_card_fallback OK")


if __name__ == "__main__":
    main()
