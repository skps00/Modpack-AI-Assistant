#!/usr/bin/env python3
"""Static gate for T6d in-game obtain-graph bench (graph_bench autotest case)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"
HARNESS = (
    ROOT
    / "forge/1.19.2/src/main/java/com/skps9/packai/client/autotest/AutoTestHarness.java"
)
GRAPH = LOGIC / "ObtainGraph.java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> None:
    fails: list[str] = []
    runtime = read(LOGIC / "ObtainGraphRuntime.java")
    harness = read(HARNESS)
    graph = read(GRAPH)

    if "BenchResult benchBuild(RecipeManager rm, int runs)" not in runtime:
        fails.append("missing benchBuild(RecipeManager, int)")

    bench_start = runtime.find("public static BenchResult benchBuild")
    if bench_start < 0:
        fails.append("cannot locate benchBuild body")
    else:
        bench_end = runtime.find("\n    public ObtainGraph graph()", bench_start)
        if bench_end < 0:
            bench_end = len(runtime)
        bench_body = runtime[bench_start:bench_end]
        if "new ObtainGraph.Builder" not in bench_body:
            fails.append("benchBuild must use new ObtainGraph.Builder")
        if "tryWrite" in bench_body or "ObtainGraphCache.write" in bench_body:
            fails.append("benchBuild must not write obtain graph cache")

    if '"graph_bench".equals(spec.kind())' not in harness:
        fails.append("AutoTestHarness missing graph_bench branch")

    if "obtain.bench run=" not in runtime:
        fails.append("missing obtain.bench log key")

    if "MessageDigest.getInstance(\"SHA-1\")" not in graph:
        fails.append("ObtainGraph fingerprint must use SHA-1")
    if "canonicalContentFingerprint" not in graph:
        fails.append("missing canonicalContentFingerprint on ObtainGraph")
    if not re.search(r"ids\.sort\(String::compareTo\)", graph):
        fails.append("fingerprint input groups must sort item ids")
    if "lines.sort(String::compareTo)" not in graph:
        fails.append("fingerprint must sort edge lines")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_graph_bench OK")


if __name__ == "__main__":
    main()
