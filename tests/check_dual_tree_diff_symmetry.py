#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Forge/NeoForge working-tree +line sets must match for dual-changed files.

check_dual_tree_sync.py only WARNs on allowlisted files (ReplyLang / AskService),
so T1–T4 edits there would not fail that gate. This script diffs +lines.

Usage (repo root): python tests/check_dual_tree_diff_symmetry.py
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE_PREFIX = "forge/1.19.2/"
NEO_PREFIX = "neoforge/1.21.1/"
MAX_SHOW = 5


def git_out(args: list[str]) -> str:
    r = subprocess.run(
        ["git", "-C", str(ROOT), *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if r.returncode != 0:
        print("FAIL git", args, r.stderr[:400])
        sys.exit(1)
    return r.stdout


def plus_lines(path: str) -> set[str]:
    text = git_out(["diff", "-U0", "--", path])
    out: set[str] = set()
    for line in text.splitlines():
        if line.startswith("+++"):
            continue
        if line.startswith("+"):
            out.add(line[1:])
    return out


def rel_of(path: str) -> tuple[str, str] | None:
    p = path.replace("\\", "/")
    if p.startswith(FORGE_PREFIX):
        return "forge", p[len(FORGE_PREFIX) :]
    if p.startswith(NEO_PREFIX):
        return "neo", p[len(NEO_PREFIX) :]
    return None


def main() -> None:
    names = [
        ln.replace("\\", "/").strip()
        for ln in git_out(["diff", "--name-only", "HEAD"]).splitlines()
        if ln.strip()
    ]
    forge_rels: set[str] = set()
    neo_rels: set[str] = set()
    for n in names:
        parsed = rel_of(n)
        if not parsed:
            continue
        tree, rel = parsed
        if tree == "forge":
            forge_rels.add(rel)
        else:
            neo_rels.add(rel)
    common = sorted(forge_rels & neo_rels)
    if not common:
        print("OK no dual-tree intersection in git diff")
        print("check_dual_tree_diff_symmetry OK")
        return
    failed = False
    for rel in common:
        f_set = plus_lines(FORGE_PREFIX + rel)
        n_set = plus_lines(NEO_PREFIX + rel)
        if f_set == n_set:
            print(f"OK {rel} +lines={len(f_set)}")
            continue
        failed = True
        only_f = sorted(f_set - n_set)
        only_n = sorted(n_set - f_set)
        print(f"FAIL {rel}")
        if only_f:
            print("  only-forge:")
            for line in only_f[:MAX_SHOW]:
                print("   +", line[:200])
        if only_n:
            print("  only-neo:")
            for line in only_n[:MAX_SHOW]:
                print("   +", line[:200])
    if failed:
        sys.exit(1)
    print("check_dual_tree_diff_symmetry OK")


if __name__ == "__main__":
    main()
