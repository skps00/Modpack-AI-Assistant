#!/usr/bin/env python3
"""Static gate for ObtainGraphWorker (step 3 — worker body, fingerprint, config)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"
CONFIG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> None:
    fails: list[str] = []
    worker = read(LOGIC / "ObtainGraphWorker.java")
    graph = read(LOGIC / "ObtainGraph.java")
    config = read(CONFIG)

    forbidden = re.compile(
        r"Minecraft|RecipeManager|ReplyLang|FMLPaths|PackAiConfig|FluidSources"
    )
    if forbidden.search(worker):
        fails.append("ObtainGraphWorker must not reference client-only types")

    if not re.search(r"if\s*\(\s*busy\s*\)[\s\S]{0,120}return\s+false", worker):
        fails.append("submit must return false when a task is already running")

    if "latestGen" not in worker:
        fails.append("missing latestGen tracking")
    if "obtain.graph.worker abort" not in worker:
        fails.append("missing abort debug log")

    for prop in (
        "packai.workerFailOnce",
        "packai.workerHangOnce",
        "packai.workerTimeoutMs",
    ):
        if prop not in worker:
            fails.append(f"missing test property {prop}")

    edge_fn = re.search(
        r"static String canonicalEdgeLine\(Edge edge\)\s*\{([\s\S]*?)^\s*\}",
        graph,
        re.MULTILINE,
    )
    if not edge_fn:
        fails.append("missing package-private canonicalEdgeLine(Edge)")
    else:
        body = edge_fn.group(1)
        if "inputsPartial" not in body:
            fails.append("canonicalEdgeLine must include inputsPartial")
        if "requirements" not in body:
            fails.append("canonicalEdgeLine must include requirements")
        if "actionLabel" in body:
            fails.append("canonicalEdgeLine must not include actionLabel")

    # The worker is off by default on purpose: in-game testing showed it produces a
    # different graph than the main-thread build in the same session (equal edge counts,
    # different sha), because it reads Minecraft's lazy ingredient caches off-thread.
    # The tick path is fast enough after the reflection-miss cache (minehattan cold 29 s).
    if not re.search(r'define\("obtainGraphWorker",\s*false\)', config):
        fails.append('obtainGraphWorker config default must be false (worker is unsafe off-thread)')

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_graph_worker OK")


if __name__ == "__main__":
    main()
