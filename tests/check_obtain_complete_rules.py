#!/usr/bin/env python3
"""Static gate for LD4 obtain complete / natural-fluid honesty (T3)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE_LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"


def read(name: str) -> str:
    return (FORGE_LOGIC / name).read_text(encoding="utf-8")


def main() -> None:
    fails: list[str] = []
    graph = read("ObtainGraph.java")
    paths = read("ObtainPaths.java")
    text = read("ObtainPathText.java")

    if "completeFlipEnabled" not in graph:
        fails.append("ObtainGraph missing completeFlipEnabled")
    if not re.search(r"0\.10|COMPLETE_FLIP_MAX_UNRESOLVED_RATIO", graph):
        fails.append("ObtainGraph missing 0.10 flip threshold")

    if "fluid_sources.json" not in graph:
        fails.append("ObtainGraph must load fluid_sources.json")
    for src, label in ((graph, "ObtainGraph"), (paths, "ObtainPaths")):
        if re.search(r'"minecraft:water"', src):
            fails.append(f"{label} hardcodes minecraft:water for fluid judgment")

    if not re.search(
        r"isNaturalFluid\s*\([^)]*\)\s*\{[^}]*continue",
        text,
        re.DOTALL,
    ) and "isNaturalFluid" not in text:
        fails.append("ObtainPathText missing isNaturalFluid before fluid_source_missing")
    elif "fluid_source_missing" in text and "isNaturalFluid" not in text:
        fails.append("ObtainPathText missing natural-fluid guard before fluid_source_missing")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_complete_rules OK")


if __name__ == "__main__":
    main()
