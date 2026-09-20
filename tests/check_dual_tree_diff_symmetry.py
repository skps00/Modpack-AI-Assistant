#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Forge/NeoForge working-tree +line sets must match for dual-changed files.

check_dual_tree_sync.py only WARNs on allowlisted files (ReplyLang / AskService),
so T1–T4 edits there would not fail that gate. This script diffs +lines.

Before set comparison, each +line is token-normalized via SHIM_NORMALIZE
(documented loader/MC-version API aliases → a shared token). This is not a
file skip and not a count-only check: leftover tokens after normalize still
FAIL (exit 1). Adding a map entry requires a one-line comment naming both
APIs and why they are the same setting/call — do not collapse unrelated logic
(BooleanValue vs IntValue, extra identifiers, whole-file ignores).

Pause mode (same convention as check_dual_tree_sync.py, not a skipped check):
if neoforge/README_PAUSED.md exists, +line asymmetries involving the neoforge
tree are WARN (not FAIL); exit 0 when those are the only issues. --no-paused
forces the strict lockstep gate — genuine asymmetry (same relative file changed
in both trees with non-corresponding +lines) still FAILs. Default = auto-detect
from the pause marker file.

Usage (repo root):
  python tests/check_dual_tree_diff_symmetry.py
  python tests/check_dual_tree_diff_symmetry.py --no-paused
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE_PREFIX = "forge/1.19.2/"
NEO_PREFIX = "neoforge/1.21.1/"
PAUSE_MARKER = ROOT / "neoforge" / "README_PAUSED.md"
MAX_SHOW = 5

# Keys are regex. Applied to each git +line before set compare.
# New entry: one-line comment = the two APIs + why they are a legal shim.
SHIM_NORMALIZE: dict[str, str] = {
    # Forge 1.19.2 NightConfig spec type; Neo twin is ModConfigSpec (same keys).
    r"ForgeConfigSpec": "ConfigSpec",
    # NeoForge 1.21.1 spec type; Forge twin is ForgeConfigSpec (same keys).
    r"ModConfigSpec": "ConfigSpec",
    # Forge 1.19.2 WidgetCompat tooltip helper wrapping translatable lines.
    r"WidgetCompat\.tipLines\(": "TIP(",
    # Neo 1.21.1 local tip() helper (same lang key). Word-boundary so tooltip( is not eaten.
    r"\btip\(": "TIP(",
}


def apply_shim_normalize(line: str) -> str:
    for pat, repl in SHIM_NORMALIZE.items():
        line = re.sub(pat, repl, line)
    return line


def resolve_paused(argv: list[str]) -> bool:
    """--no-paused forces strict; else auto-detect PAUSE_MARKER (same as check_dual_tree_sync)."""
    if "--no-paused" in argv:
        return False
    return PAUSE_MARKER.is_file()


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


def main(argv: list[str] | None = None) -> None:
    if argv is None:
        argv = sys.argv[1:]
    paused = resolve_paused(argv)
    if paused:
        print(
            "PAUSED: NeoForge 1.21.1 support is paused (neoforge/README_PAUSED.md) — "
            "neoforge-tree +line asymmetries are WARN, not FAIL."
        )

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
        if paused:
            print("SUMMARY paused=True fail=0 warn=0")
        print("check_dual_tree_diff_symmetry OK")
        return
    failed = 0
    warns = 0
    for rel in common:
        f_raw = plus_lines(FORGE_PREFIX + rel)
        n_raw = plus_lines(NEO_PREFIX + rel)
        f_set = {apply_shim_normalize(s) for s in f_raw}
        n_set = {apply_shim_normalize(s) for s in n_raw}
        if f_set == n_set:
            print(f"OK {rel} +lines={len(f_set)}")
            continue
        only_f = sorted(s for s in f_raw if apply_shim_normalize(s) not in n_set)
        only_n = sorted(s for s in n_raw if apply_shim_normalize(s) not in f_set)
        # Same paused-tree convention as check_dual_tree_sync.py: neo asymmetries
        # are WARN while paused — not a skipped check. Under --no-paused, genuine
        # dual-tree +line mismatch still FAILs.
        if paused:
            warns += 1
            print(f"WARN {rel}")
        else:
            failed += 1
            print(f"FAIL {rel}")
        if only_f:
            print("  only-forge:")
            for line in only_f[:MAX_SHOW]:
                print("   +", line[:200])
        if only_n:
            print("  only-neo:")
            for line in only_n[:MAX_SHOW]:
                print("   +", line[:200])
    if paused:
        print(f"SUMMARY paused=True fail={failed} warn={warns}")
    if failed:
        sys.exit(1)
    print("check_dual_tree_diff_symmetry OK")


if __name__ == "__main__":
    main()
