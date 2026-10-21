#!/usr/bin/env python3
"""Static gate for T6b dynamic obtain-graph per-tick build budget."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"


def read(name: str) -> str:
    return (LOGIC / name).read_text(encoding="utf-8")


def main() -> None:
    fails: list[str] = []
    runtime = read("ObtainGraphRuntime.java")
    path_svc = read("ObtainPathService.java")

    if "TICK_BUDGET_IDLE_NS = 8_000_000L" not in runtime:
        fails.append("missing TICK_BUDGET_IDLE_NS 8ms constant")
    if "TICK_BUDGET_ACTIVE_NS = 2_000_000L" not in runtime:
        fails.append("missing TICK_BUDGET_ACTIVE_NS 2ms constant")

    if "void noteAskActivity()" not in runtime:
        fails.append("missing noteAskActivity()")
    if "ObtainGraphRuntime.noteAskActivity()" not in path_svc:
        fails.append("ObtainPathService must call noteAskActivity()")

    if "ASK_QUIET_NS" not in runtime or "10_000_000_000L" not in runtime:
        fails.append("missing 10s ASK_QUIET_NS threshold")
    if not re.search(r"isIdleBuildBudget|tickBudgetNanos", runtime):
        fails.append("missing dynamic budget selection method")

    tick_body = runtime[runtime.find("public void tick()") :]
    if tick_body.count("2_000_000L") > 0:
        fails.append("tick() must not hardcode 2_000_000L; use tick budget variable")
    if "tickBudgetNs" not in tick_body:
        fails.append("tick() must use tickBudgetNs variable")

    if "obtain.graph.budget idle_ms=8 active_ms=2 quiet_s=10" not in runtime:
        fails.append("missing one-shot obtain.graph.budget log")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_build_budget OK")


if __name__ == "__main__":
    main()
