#!/usr/bin/env python3
"""Capable-mode bridge split — identity-only completeWithTools, full wall on askNoTools."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASK_ENGINE_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "AskEngine.java",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "AskEngine.java",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def is_capable(*, tools_off: bool, tools_force: bool, url_lacks_native: bool) -> bool:
    """Mirror AskEngine completeWithTools capable boolean."""
    if tools_off:
        return False
    if tools_force:
        return True
    return not url_lacks_native


def prompt_facts_for_complete_with_tools(
    *,
    tools_off: bool,
    tools_force: bool,
    url_lacks_native: bool,
    facts_live: list[str],
) -> list[str]:
    """Mirror completeWithTools promptFacts decision."""
    capable = is_capable(
        tools_off=tools_off,
        tools_force=tools_force,
        url_lacks_native=url_lacks_native,
    )
    return [] if capable else list(facts_live)


def prompt_facts_for_ask_no_tools(facts_full: list[str]) -> list[str]:
    """Mirror askNoTools — always full wall."""
    return list(facts_full)


def check_source(path: Path) -> None:
    src = read(path)
    assert "factsFull" in src, f"{path}: missing factsFull"
    assert "jeiForLlmSlim" in src, f"{path}: missing jeiForLlmSlim"
    assert "purposeForLlmSlim" in src, f"{path}: missing purposeForLlmSlim"

    cwt_start = src.index("public LlmRound completeWithTools")
    cwt_end = src.index("public void rememberNoNativeTools", cwt_start)
    cwt = src[cwt_start:cwt_end]
    assert "boolean capable" in cwt, f"{path}: completeWithTools missing capable boolean"
    assert "List.of()" in cwt, f"{path}: completeWithTools missing List.of() slim path"
    assert "promptFacts" in cwt, f"{path}: completeWithTools missing promptFacts"
    assert "jeiForLlmSlim()" in cwt, f"{path}: completeWithTools should call jeiForLlmSlim"
    assert "purposeForLlmSlim()" in cwt, f"{path}: completeWithTools should call purposeForLlmSlim"

    ant_start = src.index("public String askNoTools()")
    ant_end = src.index("public LlmRound completeWithTools", ant_start)
    ant = src[ant_start:ant_end]
    assert "factsFull" in ant, f"{path}: askNoTools must use factsFull"
    assert "jeiForLlm()" in ant, f"{path}: askNoTools must use jeiForLlm()"
    assert "purposeForLlm" in ant, f"{path}: askNoTools must use purposeForLlm"


def check_behavior() -> None:
    full = ["fact:a", "fact:b", "extra:c"]

    # capable(force) → empty prompt facts
    assert prompt_facts_for_complete_with_tools(
        tools_off=False, tools_force=True, url_lacks_native=True, facts_live=full
    ) == []

    # capable(auto, urlOk) → empty prompt facts
    assert prompt_facts_for_complete_with_tools(
        tools_off=False, tools_force=False, url_lacks_native=False, facts_live=full
    ) == []

    # fallback(off) → full factsLive
    assert prompt_facts_for_complete_with_tools(
        tools_off=True, tools_force=False, url_lacks_native=False, facts_live=full
    ) == full

    # fallback after 400 (url lacks native) → full factsLive on completeWithTools
    assert prompt_facts_for_complete_with_tools(
        tools_off=False, tools_force=False, url_lacks_native=True, facts_live=full
    ) == full

    # askNoTools always full wall (400 fallback regression)
    assert prompt_facts_for_ask_no_tools(full) == full
    assert prompt_facts_for_ask_no_tools(["only:wall"]) == ["only:wall"]


def main() -> None:
    for path in ASK_ENGINE_PATHS:
        check_source(path)
    check_behavior()
    print("check_ask_capable_slim OK")


if __name__ == "__main__":
    main()
