#!/usr/bin/env python3
"""Numen-aligned TOOL_MISS teaching notes — forge + neo lockstep."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TREES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
)

LIST_OF_RE = re.compile(
    r"public\s+static\s+final\s+List<String>\s+(CAPABLE_TOOLS|FIRST_ROUND_TOOLS)\s*=\s*List\.of\((.*?)\);",
    re.DOTALL,
)
STR_LIT_RE = re.compile(r'"([a-z0-9_]+)"')


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_tool_lists(loop_src: str) -> set[str]:
    found: dict[str, list[str]] = {}
    for m in LIST_OF_RE.finditer(loop_src):
        name = m.group(1)
        body = m.group(2)
        found[name] = STR_LIT_RE.findall(body)
    assert "CAPABLE_TOOLS" in found, "CAPABLE_TOOLS List.of missing"
    assert "FIRST_ROUND_TOOLS" in found, "FIRST_ROUND_TOOLS List.of missing"
    return set(found["CAPABLE_TOOLS"]) | set(found["FIRST_ROUND_TOOLS"])


def extract_tool_miss_note(llm_src: str) -> str:
    # Match the METHOD definition (public static String toolMissNote(...) {), not a call site
    m = re.search(r"public\s+static\s+String\s+toolMissNote\s*\([^)]*\)\s*\{", llm_src)
    assert m, "toolMissNote method definition not found"
    brace = m.end() - 1  # position of the method's opening brace
    depth = 0
    i = brace
    while i < len(llm_src):
        ch = llm_src[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return llm_src[brace : i + 1]
        i += 1
    raise AssertionError("unclosed toolMissNote method")


def check_tree(logic: Path) -> None:
    loop = read(logic / "AskToolLoop.java")
    llm = read(logic / "LlmClient.java")
    assert "toolMissNote" in llm, f"{logic}: LlmClient missing toolMissNote"
    assert "public static String toolMissNote" in llm, f"{logic}: toolMissNote not public static"

    tools = parse_tool_lists(loop)
    note_body = extract_tool_miss_note(llm)

    # Default / unknown-name path keeps old behavior
    assert ('"[TOOL_MISS] " + name + " empty — do not invent"' in note_body) or (
        '"[TOOL_MISS] " + n + " empty — do not invent"' in note_body
    ) or ('"[TOOL_MISS] " + name + " empty — do not invent"' in llm), (
        f"{logic}: default TOOL_MISS fallback missing"
    )

    for tool in sorted(tools):
        # tool-specific case: string literal starting with that tool name inside a TOOL_MISS note
        needle = f'"[TOOL_MISS] {tool}'
        if needle not in note_body:
            # item_search (etc.) may use default TOOL_MISS fallback — no dedicated branch
            assert f'"{tool}".equals(name)' not in note_body, (
                f"{logic}: {tool} has equals-branch but missing TOOL_MISS literal"
            )
            continue
        # Extract the concatenated note for this tool (rough: from needle through next semicolon return)
        idx = note_body.index(needle)
        chunk = note_body[idx : idx + 400]
        assert "TOOL_MISS" in chunk
        assert tool in chunk
        # English teaching + do-not-invent instruction
        assert "do not invent" in chunk.lower() or "Do not invent" in chunk, (
            f"{logic}: {tool} miss note missing 'do not invent'"
        )
        # No CJK in tool-specific teaching strings (model-facing English)
        assert not re.search(r"[\u4e00-\u9fff]", chunk.split(";")[0]), (
            f"{logic}: {tool} miss note must be English"
        )

    # render_recipe_cards has dedicated miss note (retired show_recipe_card maps here too)
    assert '"[TOOL_MISS] render_recipe_cards' in note_body
    assert "Do not retry the same" in note_body or "do not invent" in note_body.lower()

    miss_refs = loop.count("toolMissNote")
    assert miss_refs >= 2, f"{logic}: AskToolLoop must call toolMissNote >= 2 times, got {miss_refs}"

    # R4: emission tools bypass MAX_LOCAL_TOOLS blank gate
    assert 'emissionTool = "render_recipe_cards".equals(name)' in loop or (
        '"render_recipe_cards".equals(name) || "item_search".equals(name)' in loop
    ), f"{logic}: render/item_search must bypass local-tools cap"


def main() -> None:
    for logic in TREES:
        check_tree(logic)
    print("check_tool_miss_teaching: OK")


if __name__ == "__main__":
    main()
