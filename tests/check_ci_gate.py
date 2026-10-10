#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Unit checks for tools/ci_gate.py pure helpers (no full-gate spawn)."""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GATE_PATH = ROOT / "tools" / "ci_gate.py"


def _load_gate():
    spec = importlib.util.spec_from_file_location("ci_gate", GATE_PATH)
    assert spec is not None and spec.loader is not None
    mod = importlib.util.module_from_spec(spec)
    # Python 3.14: dataclass/typing need sys.modules entry before exec_module
    sys.modules[spec.name] = mod
    spec.loader.exec_module(mod)
    return mod


def main() -> int:
    g = _load_gate()
    n = 0
    errors: list[str] = []

    def check(cond: bool, msg: str) -> None:
        nonlocal n
        n += 1
        if not cond:
            errors.append(msg)

    # parse_known_red happy path
    entries = g.parse_known_red("check_a.py\t2\treason")
    check(len(entries) == 1, "parse: expect 1 entry")
    check(entries[0].allowed_rc == 2, "parse: allowed_rc == 2")
    check(entries[0].name == "check_a.py", "parse: name")
    check(entries[0].reason == "reason", "parse: reason")

    # RC column not 2 => invalid
    raised = False
    try:
        g.parse_known_red("check_a.py\t1\treason")
    except ValueError:
        raised = True
    check(raised, "parse: RC=1 must raise")

    # blank reason => invalid
    raised = False
    try:
        g.parse_known_red("check_a.py\t2\t")
    except ValueError:
        raised = True
    check(raised, "parse: blank reason must raise")

    # also blank reason with only whitespace after tab
    raised = False
    try:
        g.parse_known_red("check_a.py\t2\t   ")
    except ValueError:
        raised = True
    check(raised, "parse: whitespace reason must raise")

    # classify
    check(g.classify(0, None) == "ok", "classify(0, None)=ok")
    check(g.classify(1, None) == "fail", "classify(1, None)=fail")
    entry2 = g.KnownRedEntry("x.py", 2, "r")
    check(g.classify(2, entry2) == "allowlisted", "classify(2, entry)=allowlisted")
    check(g.classify(1, entry2) == "fail", "classify(1, entry)=fail")
    check(g.classify(0, entry2) == "info", "classify(0, entry)=info")

    # comments / blanks ignored
    entries2 = g.parse_known_red("# c\n\ncheck_b.py\t2\tok reason\n")
    check(len(entries2) == 1 and entries2[0].name == "check_b.py", "parse: skip comments")

    if errors:
        for e in errors:
            print(f"FAIL {e}")
        print(f"FAIL check_ci_gate: n={n} errors={len(errors)}")
        return 1
    print(f"PASS check_ci_gate: n={n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
