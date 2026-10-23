#!/usr/bin/env python3
"""Static gate: partial-edge rebuild after first obtain-graph finish."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/ObtainGraph.java"
RUNTIME = (
    ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/ObtainGraphRuntime.java"
)


def main() -> None:
    fails: list[str] = []
    graph = GRAPH.read_text(encoding="utf-8")
    runtime = RUNTIME.read_text(encoding="utf-8")

    # 1: partialEdgeCount on ObtainGraph
    if "partialEdgeCount()" not in graph:
        fails.append("ObtainGraph.java missing partialEdgeCount()")

    # 2: partial rebuild log keys
    if "obtain.partial.first" not in runtime:
        fails.append("ObtainGraphRuntime missing obtain.partial.first log")
    if "obtain.partial.rebuilt" not in runtime:
        fails.append("ObtainGraphRuntime missing obtain.partial.rebuilt log")

    # 3: tick path rebuild between finish() and applyResult(
    tick_finish = runtime.rfind("ObtainGraph finished = builder.finish();")
    if tick_finish < 0:
        fails.append("missing builder.finish() in tick path")
    else:
        tick_tail = runtime[tick_finish:]
        apply_idx = tick_tail.find("applyResult(")
        finish_idx = tick_tail.find(".finish()")
        if apply_idx < 0:
            fails.append("missing applyResult after tick finish")
        elif "obtain.partial.first" not in tick_tail[:apply_idx]:
            fails.append("partial rebuild must appear after finish() and before applyResult() on tick path")

    # 4: benchBuild also uses partialEdgeCount
    bench_start = runtime.find("public static BenchResult benchBuild")
    if bench_start < 0:
        fails.append("missing benchBuild")
    else:
        bench_end = runtime.find("\n    public ObtainGraph graph()", bench_start)
        if bench_end < 0:
            bench_end = len(runtime)
        bench_body = runtime[bench_start:bench_end]
        if bench_body.count("partialEdgeCount") < 2:
            fails.append("benchBuild must reference partialEdgeCount at least twice")

    # 5: no new background threading in runtime changes
    if re.search(r"\bnew\s+Thread\b", runtime):
        fails.append("ObtainGraphRuntime must not use new Thread")
    if re.search(r"\bExecutor\b", runtime):
        fails.append("ObtainGraphRuntime must not use Executor")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_partial_rebuild OK")


if __name__ == "__main__":
    main()
