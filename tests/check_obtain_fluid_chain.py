#!/usr/bin/env python3
"""Static gate for obtain fluid producer chain wiring (T5)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE_LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"
FORGE_CLIENT = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/routes"


def read_logic(name: str) -> str:
    return (FORGE_LOGIC / name).read_text(encoding="utf-8")


def read_client(name: str) -> str:
    return (FORGE_CLIENT / name).read_text(encoding="utf-8")


def main() -> None:
    fails: list[str] = []
    graph = read_logic("ObtainGraph.java")
    paths = read_logic("ObtainPaths.java")
    codec = read_client("RouteStoreCodec.java")

    if "record FluidEdge" not in graph and "FluidEdge(" not in graph:
        fails.append("ObtainGraph missing FluidEdge record")
    if "fluidProducers()" not in graph:
        fails.append("ObtainGraph missing fluidProducers()")
    if "pickFluidProducer" not in graph:
        fails.append("ObtainGraph missing pickFluidProducer")
    if "MAX_FLUID_EDGES_PER_FLUID" not in graph:
        fails.append("ObtainGraph missing MAX_FLUID_EDGES_PER_FLUID")

    if "producerInputsContain" not in graph:
        fails.append("pickFluidProducer missing producerInputsContain cycle guard")
    if "edgePriority" not in graph:
        fails.append("pickFluidProducer missing edgePriority sort")

    if "fluidSources" not in paths or "record Step" not in paths:
        fails.append("ObtainPaths.Step missing fluidSources")
    if "resolveFluidSources" not in paths:
        fails.append("ObtainPaths missing resolveFluidSources")
    if "pickFluidProducer" not in paths:
        fails.append("ObtainPaths search/find must call pickFluidProducer (via resolveFluidSources)")

    if not re.search(r'o\.add\("fluidSources"', codec):
        fails.append("RouteStoreCodec missing fluidSources encode")
    if "readFluidSourcesField" not in codec:
        fails.append("RouteStoreCodec missing fluidSources decode helper")
    if not re.search(r'!o\.has\("fluidSources"\)', codec):
        fails.append("RouteStoreCodec missing legacy fluidSources → empty list")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_fluid_chain OK")


if __name__ == "__main__":
    main()
