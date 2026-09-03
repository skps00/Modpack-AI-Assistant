#!/usr/bin/env python3
"""Native tools schema order stability — forge + neo lockstep."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TREES = (
    (
        "forge",
        ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
    ),
    (
        "neoforge",
        ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
    ),
)

LIST_OF_RE = re.compile(
    r"public\s+static\s+final\s+List<String>\s+(CAPABLE_TOOLS|FIRST_ROUND_TOOLS)\s*=\s*List\.of\((.*?)\);",
    re.DOTALL,
)
STR_LIT_RE = re.compile(r'"([a-z0-9_]+)"')


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_list(loop_src: str, const_name: str) -> list[str]:
    for m in LIST_OF_RE.finditer(loop_src):
        if m.group(1) == const_name:
            return STR_LIT_RE.findall(m.group(2))
    raise AssertionError(f"{const_name} List.of literal missing")


def extract_native_tools_schema(llm_src: str) -> str:
    # Match the METHOD definition, not the call site(s) (body.add("tools", nativeToolsSchema(...)))
    m = re.search(r"static\s+JsonArray\s+nativeToolsSchema\s*\([^)]*\)\s*\{", llm_src)
    assert m, "nativeToolsSchema method definition not found"
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
    raise AssertionError("unclosed nativeToolsSchema method")


def check_tree(label: str, logic: Path) -> tuple[list[str], list[str]]:
    loop = read(logic / "AskToolLoop.java")
    llm = read(logic / "LlmClient.java")

    # Must be List.of — not Set.of / Map / Collections.unmodifiableSet
    assert "CAPABLE_TOOLS = List.of(" in loop.replace("\n", "").replace(" ", "") or (
        "CAPABLE_TOOLS = List.of(" in loop
    ), f"{label}: CAPABLE_TOOLS must use List.of("
    assert "FIRST_ROUND_TOOLS = List.of(" in loop, f"{label}: FIRST_ROUND_TOOLS must use List.of("
    assert re.search(r"CAPABLE_TOOLS\s*=\s*Set\.of\(", loop) is None, f"{label}: CAPABLE_TOOLS must not be Set.of"
    assert re.search(r"FIRST_ROUND_TOOLS\s*=\s*Set\.of\(", loop) is None, (
        f"{label}: FIRST_ROUND_TOOLS must not be Set.of"
    )
    assert "CAPABLE_TOOLS = Collections.unmodifiableSet" not in loop
    assert "FIRST_ROUND_TOOLS = Collections.unmodifiableSet" not in loop

    capable = parse_list(loop, "CAPABLE_TOOLS")
    first = parse_list(loop, "FIRST_ROUND_TOOLS")
    assert capable, f"{label}: CAPABLE_TOOLS empty"
    assert first, f"{label}: FIRST_ROUND_TOOLS empty"
    missing = [t for t in first if t not in capable]
    assert not missing, f"{label}: FIRST_ROUND_TOOLS not subset of CAPABLE_TOOLS: {missing}"

    schema_body = extract_native_tools_schema(llm)
    assert "for (String name : names)" in schema_body, (
        f"{label}: nativeToolsSchema must iterate parameter names"
    )
    assert "QUERY_TOOLS" not in schema_body, f"{label}: nativeToolsSchema must not iterate QUERY_TOOLS"
    assert "ALLOWLIST" not in schema_body or "AskToolLoop.ALLOWLIST.contains" in schema_body, (
        f"{label}: unexpected ALLOWLIST use in nativeToolsSchema"
    )
    # Guard: must not be the iteration source
    assert not re.search(r"for\s*\([^)]*:\s*QUERY_TOOLS\s*\)", schema_body)
    assert not re.search(r"for\s*\([^)]*:\s*ALLOWLIST\s*\)", schema_body)
    assert not re.search(r"for\s*\([^)]*:\s*AskToolLoop\.ALLOWLIST\s*\)", schema_body)

    return capable, first


def main() -> None:
    parsed: dict[str, tuple[list[str], list[str]]] = {}
    for label, logic in TREES:
        parsed[label] = check_tree(label, logic)

    forge_c, forge_f = parsed["forge"]
    neo_c, neo_f = parsed["neoforge"]
    assert forge_c == neo_c, f"CAPABLE_TOOLS drift forge={forge_c} neo={neo_c}"
    assert forge_f == neo_f, f"FIRST_ROUND_TOOLS drift forge={forge_f} neo={neo_f}"
    print("check_tool_schema_stable: OK")


if __name__ == "__main__":
    main()
