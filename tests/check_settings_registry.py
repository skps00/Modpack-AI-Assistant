#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Batch B1 guard: SettingsRegistry paths ↔ PackAiConfig ↔ lang ×3 ↔ set*+SPEC.save()."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java"
REG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsRegistry.java"
SCREEN = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsScreenV2.java"
LANGS = [
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/en_us.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_cn.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_tw.json",
]

# Every ControlType must be referenced in SettingsScreenV2 (B2 widget migration).
REQUIRED_CONTROL_TYPES = ("TOGGLE", "NUMBER", "TEXT", "LIST")

# Fail-closed: empty unless a setter must not persist (file+line evidence required).
EXCLUDED: set[str] = set()

UI_CATEGORIES = (
    "CONNECTION",
    "ANSWER",
    "RECIPE",
    "QUEST",
    "INTERFACE",
    "ADVANCED",
    "DEBUG",
)

ENTRY_RE = re.compile(
    r'e(?:Hidden)?\(\s*"([^"]+)"\s*,\s*UiCategory\.(\w+)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*'
    r"ControlType\.\w+\s*,\s*\"(set\w+)\"",
    re.MULTILINE,
)
SETTER_RE = re.compile(r"public static void (set\w+)\s*\(")
PUSH_RE = re.compile(r'\b\.push\("([^"]+)"\)')
DEFINE_RE = re.compile(r'\.define(?:InRange)?\("([^"]+)"')
EXCLUDED_JAVA_RE = re.compile(
    r"EXCLUDED\s*=\s*java\.util\.Set\.of\(([^)]*)\)"
)


def method_body(src: str, name: str) -> str:
    needle = f"public static void {name}("
    if needle not in src:
        raise AssertionError(f"missing setter: {name}")
    idx = src.index(needle)
    brace = src.find("{", idx)
    if brace < 0:
        raise AssertionError(f"{name}: no opening brace")
    depth = 0
    for i in range(brace, len(src)):
        c = src[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[brace : i + 1]
    raise AssertionError(f"{name}: braces not balanced")


def parse_toml_paths(cfg: str) -> set[str]:
    paths: set[str] = set()
    section: str | None = None
    for line in cfg.splitlines():
        m = PUSH_RE.search(line)
        if m:
            section = m.group(1)
            continue
        if ".pop()" in line:
            section = None
            continue
        d = DEFINE_RE.search(line)
        if d and section:
            paths.add(f"{section}.{d.group(1)}")
    return paths


def load_lang(path: Path) -> dict[str, str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise AssertionError(f"{path.name}: not a JSON object")
    return data


def parse_excluded(reg: str) -> set[str]:
    m = EXCLUDED_JAVA_RE.search(reg)
    if not m:
        return set()
    inner = m.group(1).strip()
    if not inner:
        return set()
    return set(re.findall(r'"([^"]+)"', inner))


def main() -> int:
    cfg = CFG.read_text(encoding="utf-8")
    reg = REG.read_text(encoding="utf-8")
    langs = [load_lang(p) for p in LANGS]
    toml_paths = parse_toml_paths(cfg)
    setters = set(SETTER_RE.findall(cfg))
    java_excluded = parse_excluded(reg)
    if java_excluded != EXCLUDED:
        print(f"FAIL: EXCLUDED mismatch python={sorted(EXCLUDED)} java={sorted(java_excluded)}")
        return 1

    entries = ENTRY_RE.findall(reg)
    if not entries:
        print("FAIL: no registry entries parsed from SettingsRegistry")
        return 1

    paths: list[str] = []
    cats_seen: set[str] = set()
    label_keys: list[str] = []
    tip_keys: list[str] = []
    for path, cat, label, tip, setter in entries:
        paths.append(path)
        cats_seen.add(cat)
        label_keys.append(label)
        tip_keys.append(tip)

        if path not in toml_paths:
            print(f"FAIL: registry path not in PackAiConfig defines: {path}")
            return 1
        if setter in EXCLUDED:
            print(f"FAIL: registry lists EXCLUDED setter {setter} for {path}")
            return 1
        if setter not in setters:
            print(f"FAIL: registry setter missing in PackAiConfig: {setter} ({path})")
            return 1
        body = method_body(cfg, setter)
        if "SPEC.save()" not in body:
            print(f"FAIL: setter missing SPEC.save(): {setter}")
            return 1
        for i, lang in enumerate(langs):
            if label not in lang:
                print(f"FAIL: missing label {label} in {LANGS[i].name}")
                return 1
            if tip not in lang:
                print(f"FAIL: missing tooltip {tip} in {LANGS[i].name}")
                return 1

    if len(paths) != len(set(paths)):
        dup = sorted({p for p in paths if paths.count(p) > 1})
        print(f"FAIL: duplicate full paths: {dup}")
        return 1

    for i, lang in enumerate(langs):
        keys = list(lang.keys())
        if len(keys) != len(set(keys)):
            print(f"FAIL: duplicate lang keys in {LANGS[i].name}")
            return 1

    for cat in UI_CATEGORIES:
        if cat not in cats_seen:
            print(f"FAIL: UI category empty / unreachable: {cat}")
            return 1
    if cats_seen - set(UI_CATEGORIES):
        print(f"FAIL: unknown UI categories: {sorted(cats_seen - set(UI_CATEGORIES))}")
        return 1

    # Real branch only (not comment / import mention). Shape in SettingsScreenV2:
    #   if (e.type == ControlType.TOGGLE) { ... }
    CONTROL_BRANCH_RE = re.compile(r"==\s*ControlType\.(TOGGLE|NUMBER|TEXT|LIST)")
    screen = SCREEN.read_text(encoding="utf-8")
    found_types = set(CONTROL_BRANCH_RE.findall(screen))
    missing_types = [t for t in REQUIRED_CONTROL_TYPES if t not in found_types]
    if missing_types:
        print(f"FAIL: SettingsScreenV2 missing ControlType handler(s): {missing_types}")
        return 1

    print(
        f"OK: registry_entries={len(paths)} categories={len(cats_seen)} "
        f"toml_paths={len(toml_paths)} setters_checked={len({e[4] for e in entries})} "
        f"excluded={len(EXCLUDED)} control_types={len(REQUIRED_CONTROL_TYPES)}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
