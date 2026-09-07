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
APIS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "api",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "api",
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
    m = re.search(r"public\s+static\s+String\s+toolMissNote\s*\([^)]*\)\s*\{", llm_src)
    assert m, "toolMissNote method definition not found"
    brace = m.end() - 1
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


def collect_asktool_miss_corpus(logic: Path) -> str:
    chunks: list[str] = []
    for path in sorted(logic.glob("*AskTool.java")):
        chunks.append(read(path))
    return "\n".join(chunks)


def check_tree(logic: Path, api: Path) -> None:
    loop = read(logic / "AskToolLoop.java")
    llm = read(logic / "LlmClient.java")
    ask = read(api / "AskTool.java")
    assert "toolMissNote" in llm, f"{logic}: LlmClient missing toolMissNote"
    assert "public static String toolMissNote" in llm, f"{logic}: toolMissNote not public static"
    assert "AskToolLoop.byName" in llm, f"{logic}: toolMissNote must delegate via byName"
    assert "default String toolMissNote" in ask, f"{api}: default toolMissNote missing"
    assert "AskToolLlm" not in llm, f"{logic}: AskToolLlm bridge must be gone"

    tools = parse_tool_lists(loop)
    note_body = extract_tool_miss_note(llm)
    corpus = collect_asktool_miss_corpus(logic) + "\n" + ask

    assert ('"[TOOL_MISS] " + n + " empty — do not invent"' in note_body) or (
        '"[TOOL_MISS] " + name() + " empty — do not invent"' in ask
    ), f"{logic}: default TOOL_MISS fallback missing"

    for tool in sorted(tools):
        needle = f'"[TOOL_MISS] {tool}'
        if needle not in corpus:
            continue
        idx = corpus.index(needle)
        chunk = corpus[idx : idx + 400]
        assert "TOOL_MISS" in chunk
        assert tool in chunk
        assert "do not invent" in chunk.lower() or "Do not invent" in chunk, (
            f"{logic}: {tool} miss note missing 'do not invent'"
        )
        assert not re.search(r"[\u4e00-\u9fff]", chunk.split(";")[0]), (
            f"{logic}: {tool} miss note must be English"
        )

    assert '"[TOOL_MISS] render_recipe_cards' in corpus
    assert "Do not retry the same" in corpus or "do not invent" in corpus.lower()

    miss_refs = loop.count("toolMissNote")
    assert miss_refs >= 2, f"{logic}: AskToolLoop must call toolMissNote >= 2 times, got {miss_refs}"

    assert 'emissionTool = "render_recipe_cards".equals(name)' in loop or (
        '"render_recipe_cards".equals(name) || "item_search".equals(name)' in loop
    ), f"{logic}: render/item_search must bypass local-tools cap"


def main() -> None:
    for logic, api in zip(TREES, APIS):
        check_tree(logic, api)
    print("check_tool_miss_teaching: OK")


if __name__ == "__main__":
    main()
