#!/usr/bin/env python3
"""Reply language: MC English (US) → en_us for LLM + ReplyLang bundles."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for side in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        rl = read(f"{side}/logic/ReplyLang.java")
        assert "resolveMcLanguageCode" in rl
        assert "options.languageCode" in rl
        assert "getMethod(\"getCode\")" in rl
        svc = read(f"{side}/client/service/AskService.java")
        assert "ReplyLang.resolveMcLanguageCode(mc)" in svc

    # normalize / bundle mapping (logic mirrored in ReplyLang.java)
    def normalize(code: str) -> str:
        return code.strip().lower().replace("-", "_")

    def bundle_lang(code: str) -> str:
        c = normalize(code)
        if not c.startswith("zh"):
            return "en_us"
        if c.startswith("zh_cn") or c.startswith("zh_sg") or c in ("zh_hans", "zh"):
            return "zh_cn"
        return "zh_tw"

    assert normalize("en-US") == "en_us"
    assert bundle_lang("en_us") == "en_us"
    assert bundle_lang("en_gb") == "en_us"
    assert bundle_lang("zh_tw") == "zh_tw"

    print("check_reply_lang OK")


if __name__ == "__main__":
    main()
