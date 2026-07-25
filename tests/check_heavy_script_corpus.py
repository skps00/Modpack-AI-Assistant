#!/usr/bin/env python3
"""Mirror heavy-script interact + strategy dispatch patterns (corpus regression)."""
import re
import sys
from pathlib import Path

# Reuse Java-tested fixtures as documentation; full assert lives in HeavyScriptChecks.java
ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    java = ROOT / "mod/src/test/java/com/skps9/packai/logic/HeavyScriptChecks.java"
    assert java.is_file(), java
    text = java.read_text(encoding="utf-8")
    for needle in (
        "organRightClickedOnlyStrategies",
        "PlayerEvents.tick",
        "getLuckyBlockRandomLoot",
        "hasTag('kubejs:lung')",
        "isThundering",
        "stages.has",
    ):
        assert needle in text, needle
    print("ok heavy_script_corpus_doc")


if __name__ == "__main__":
    main()
