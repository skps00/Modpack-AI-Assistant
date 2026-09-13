#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assert AskToolLoop.DSML_PIPE and AskReplyScrub.DSML_PIPE_RUN match + stay bounded.

Usage (repo root): python tests/check_dsml_grammar_sync.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TREES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
)
CLASS_QUANT = re.compile(r"^(\[[^\]]+\])(.*)$")


def literals(path: Path, name: str) -> str:
    text = path.read_text(encoding="utf-8")
    # AskToolLoop: DSML_PIPE only (first match). AskReplyScrub: DSML_PIPE_RUN.
    if name == "DSML_PIPE":
        m = re.search(
            r'private static final String DSML_PIPE\s*=\s*"([^"]+)"', text
        )
    else:
        m = re.search(
            r'private static final String DSML_PIPE_RUN\s*=\s*"([^"]+)"', text
        )
    if not m:
        print(f"FAIL missing {name} in {path}")
        sys.exit(1)
    return m.group(1)


def split_class(lit: str) -> tuple[str, str]:
    m = CLASS_QUANT.match(lit)
    if not m:
        print(f"FAIL not a char-class literal: {lit!r}")
        sys.exit(1)
    return m.group(1), m.group(2)


def main() -> None:
    classes: list[str] = []
    quants: list[str] = []
    for tree in TREES:
        loop = literals(tree / "AskToolLoop.java", "DSML_PIPE")
        run = literals(tree / "AskReplyScrub.java", "DSML_PIPE_RUN")
        for label, lit in (("DSML_PIPE", loop), ("DSML_PIPE_RUN", run)):
            if "+" in lit or "*" in lit:
                print(f"FAIL {tree.parent.parent.parent.name} {label} unbounded: {lit}")
                sys.exit(1)
            cls, quant = split_class(lit)
            if quant != "{1,4}":
                print(f"FAIL {label} quant {quant!r} want {{1,4}} lit={lit}")
                sys.exit(1)
            classes.append(cls)
            quants.append(quant)
        if loop != run:
            # same class + same bound is enough even if written identically
            c1, q1 = split_class(loop)
            c2, q2 = split_class(run)
            if c1 != c2 or q1 != q2:
                print(f"FAIL semantic mismatch loop={loop} run={run}")
                sys.exit(1)
    if len(set(classes)) != 1:
        print(f"FAIL char class drift {classes}")
        sys.exit(1)
    print("check_dsml_grammar_sync OK")


if __name__ == "__main__":
    main()
