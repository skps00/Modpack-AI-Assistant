#!/usr/bin/env python3
"""ask_player tool v1 — sentinel format + source guards (Forge + Neo)."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def build_sentinel(question: str, options: list[str] | None = None) -> str:
    """Mirror AskPlayerAskTool.run sentinel format."""
    opts = "" if not options else ",".join(options)
    return f"[ASK_PLAYER] question={question}|options={opts}"


def parse_sentinel(text: str) -> tuple[str, list[str]]:
    """Parse question/options from sentinel. Empty options → empty list."""
    assert text.startswith("[ASK_PLAYER] "), f"bad prefix: {text!r}"
    body = text[len("[ASK_PLAYER] ") :]
    assert "|options=" in body, f"missing options sep: {text!r}"
    q_part, opt_part = body.split("|options=", 1)
    assert q_part.startswith("question="), f"missing question=: {text!r}"
    question = q_part[len("question=") :]
    if not opt_part:
        return question, []
    return question, opt_part.split(",")


def check_source(logic: Path) -> None:
    tool = logic / "AskPlayerAskTool.java"
    assert tool.is_file(), f"missing {tool}"
    body = read(tool)
    assert 'return "ask_player"' in body or "return \"ask_player\"" in body
    assert "[ASK_PLAYER]" in body
    assert "implements AskTool" in body
    assert "Phase 3" in body

    engine = read(logic / "AskEngine.java")
    assert "new AskPlayerAskTool()" in engine, f"{logic}: AskPlayerAskTool not registered"

    # v1 not ready: keep class + registration, but do NOT expose to capable loop
    loop = read(logic / "AskToolLoop.java")
    assert "CAPABLE_TOOLS" in loop, f"{logic}: missing CAPABLE_TOOLS"
    assert '"ask_player"' not in loop.split("ALLOWLIST")[0], (
        f"{logic}: ask_player must NOT be in CAPABLE_TOOLS (no sentinel/UI wiring yet)"
    )

    result = read(logic / "AskResult.java")
    assert "boolean needsPlayer" in result, f"{logic}: AskResult missing needsPlayer"
    assert "String pendingQuestion" in result, f"{logic}: AskResult missing pendingQuestion"
    assert "withNeedsPlayer" in result, f"{logic}: AskResult missing withNeedsPlayer"


def check_behavior() -> None:
    # question + options
    s = build_sentinel("Which boss?", ["alpha", "beta"])
    assert "Which boss?" in s
    assert s == "[ASK_PLAYER] question=Which boss?|options=alpha,beta"
    q, opts = parse_sentinel(s)
    assert q == "Which boss?"
    assert opts == ["alpha", "beta"]

    # empty options → empty list
    s2 = build_sentinel("Need hint?")
    assert s2 == "[ASK_PLAYER] question=Need hint?|options="
    q2, opts2 = parse_sentinel(s2)
    assert q2 == "Need hint?"
    assert opts2 == []

    # question verbatim (no pack-specific rewrite)
    raw = "Is this the right altar for the ritual?"
    s3 = build_sentinel(raw, ["yes", "no", "unsure"])
    assert raw in s3
    assert "yes,no,unsure" in s3
    q3, opts3 = parse_sentinel(s3)
    assert q3 == raw
    assert opts3 == ["yes", "no", "unsure"]


def main() -> None:
    for logic in LOGIC:
        check_source(logic)
    check_behavior()
    print("check_ask_player_tool OK")


if __name__ == "__main__":
    main()
