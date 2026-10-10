#!/usr/bin/env python3
"""Static gate: TokenUsage cache hit/miss fields + LlmClient cache log line."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOKEN = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/TokenUsage.java"
LLM = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/LlmClient.java"


def main() -> int:
    try:
        src = TOKEN.read_text(encoding="utf-8")
        llm = LLM.read_text(encoding="utf-8")

        assert "prompt_cache_hit_tokens" in src
        assert "prompt_cache_miss_tokens" in src
        assert "prompt_tokens_details" in src
        assert "cached_tokens" in src

        plus_m = re.search(
            r"public\s+TokenUsage\s+plus\s*\(\s*TokenUsage\s+\w+\s*\)\s*\{",
            src,
        )
        assert plus_m, "plus( method not found"
        # Brace-walk to method body end (inner if {} must not truncate).
        i = plus_m.end() - 1
        depth = 0
        end = None
        for j in range(i, len(src)):
            if src[j] == "{":
                depth += 1
            elif src[j] == "}":
                depth -= 1
                if depth == 0:
                    end = j
                    break
        assert end is not None, "plus( body unclosed"
        plus_body = src[plus_m.end() : end]
        assert "promptCacheHitTokens" in plus_body, "plus() missing promptCacheHitTokens"
        assert "promptCacheMissTokens" in plus_body, "plus() missing promptCacheMissTokens"

        assert re.search(
            r"NONE\s*=\s*new\s+TokenUsage\s*\(\s*-1\s*,\s*-1\s*,\s*-1\s*,\s*-1\s*,\s*-1\s*\)",
            src,
        ), "NONE must be 5 × -1"

        assert "Pack AI cache hit=" in llm

        print("PASS check_token_cache_fields")
        return 0
    except AssertionError as e:
        print(f"FAIL check_token_cache_fields: {e}")
        return 1
    except Exception as e:
        print(f"FAIL check_token_cache_fields: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
