#!/usr/bin/env python3
"""Mirror of Plainify.stripMcFormat — fails if § color codes are not stripped."""
import re

MC_FORMAT = re.compile(r"(?i)§#[0-9a-f]{6}|§[0-9a-fk-or]|[&][0-9a-fk-or]")


def strip_mc_format(text: str) -> str:
    if not text:
        return ""
    return MC_FORMAT.sub("", text)


def main() -> None:
    assert strip_mc_format("你：§6Fluorescent") == "你：Fluorescent"
    assert strip_mc_format("AI: &eHello &rworld") == "AI: Hello world"
    assert strip_mc_format("plain") == "plain"
    assert "§" not in strip_mc_format("§#FFAA00Gold blade")
    print("ok strip_mc_format")


if __name__ == "__main__":
    main()
