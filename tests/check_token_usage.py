#!/usr/bin/env python3
"""Mirror TokenUsage.parse / formatCount (no $ price)."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/TokenUsage.java"


def format_count(n: int) -> str:
    if n < 0:
        return "—"
    if n < 1000:
        return str(n)
    k = n / 1000.0
    if n < 10_000:
        s = f"{k:.1f}k"
        return s[:-3] + "k" if s.endswith(".0k") else s
    return f"{k:.0f}k"


def from_response(root: dict | None):
    if not root or not isinstance(root.get("usage"), dict):
        return None
    u = root["usage"]

    def nn(key: str) -> int:
        v = u.get(key)
        if not isinstance(v, (int, float)) or v < 0:
            return -1
        return int(v)

    return nn("prompt_tokens"), nn("completion_tokens"), nn("total_tokens")


def main() -> int:
    src = JAVA.read_text(encoding="utf-8")
    assert "prompt_tokens" in src and "completion_tokens" in src
    assert "formatCount" in src

    assert from_response(None) is None
    assert from_response({}) is None
    assert from_response({"usage": {}}) == (-1, -1, -1)
    assert from_response(
        {"usage": {"prompt_tokens": 1200, "completion_tokens": 400, "total_tokens": 1600}}
    ) == (1200, 400, 1600)

    assert format_count(-1) == "—"
    assert format_count(400) == "400"
    assert format_count(1200) == "1.2k"
    assert format_count(1000) == "1k"
    assert format_count(15_000) == "15k"

    # Compact UI line shape lives in lang keys (in · out).
    for lang in (
        ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/en_us.json",
        ROOT / "neoforge/1.21.1/src/main/resources/assets/packai/lang/en_us.json",
    ):
        data = json.loads(lang.read_text(encoding="utf-8"))
        assert "packai.screen.token_usage" in data
        assert "%s" in data["packai.screen.token_usage"]
        assert "packai.settings.show_token_usage" in data

    # Config key present both trees.
    for cfg in (
        ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java",
        ROOT / "neoforge/1.21.1/src/main/java/com/skps9/packai/config/PackAiConfig.java",
    ):
        text = cfg.read_text(encoding="utf-8")
        assert re.search(r'define\("showTokenUsage",\s*true\)', text), cfg

    print("check_token_usage: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
