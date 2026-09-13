#!/usr/bin/env python3
"""Every INTERNAL_SECTION_TOKENS / EXTRA / SCROLL / role value has packai.label.* in 6 lang files."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRUB_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "AskReplyScrub.java",
    ROOT
    / "neoforge"
    / "1.21.1"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "AskReplyScrub.java",
)
LANG_TREES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
)
LANG_FILES = ("en_us.json", "zh_cn.json", "zh_tw.json")


def extract_list_of_strings(src: str, name: str) -> list[str]:
    m = re.search(
        rf"(?:public |private )?static final List<String> {name}\s*=\s*List\.of\((.*?)\);",
        src,
        re.S,
    )
    if not m:
        raise AssertionError(f"missing List.of {name}")
    return re.findall(r'"([^"]+)"', m.group(1))


def extract_java_string(src: str, name: str) -> str:
    m = re.search(rf"static final String {name}\s*=\s*\"([^\"]+)\"", src)
    if not m:
        raise AssertionError(f"missing String {name}")
    return m.group(1)


def expected_keys(tokens: list[str], extras: list[str], roles: list[str], has_scroll: bool) -> list[str]:
    keys: list[str] = []
    for t in tokens:
        keys.append("packai.label.src." + t.lower())
    for t in extras:
        keys.append("packai.label.src." + t.lower())
    if has_scroll:
        keys.append("packai.label.src.scroll")
    for r in roles:
        keys.append("packai.label.role." + r)
    # stable unique, keep order
    seen: set[str] = set()
    out: list[str] = []
    for k in keys:
        if k not in seen:
            seen.add(k)
            out.append(k)
    return out


def main() -> int:
    failed: list[str] = []
    token_sets: list[list[str]] = []
    extra_sets: list[list[str]] = []
    role_sets: list[list[str]] = []
    scroll_flags: list[bool] = []
    for path in SCRUB_PATHS:
        if not path.is_file():
            failed.append(f"missing {path}")
            continue
        src = path.read_text(encoding="utf-8")
        try:
            tokens = extract_list_of_strings(src, "INTERNAL_SECTION_TOKENS")
            extras = extract_list_of_strings(src, "EXTRA_BARE_SECTION_TOKENS")
            roles = extract_list_of_strings(src, "INTERNAL_ROLE_VALUES")
            scroll = extract_java_string(src, "SCROLL_SECTION_REGEX")
        except AssertionError as e:
            failed.append(f"{path}: {e}")
            continue
        token_sets.append(tokens)
        extra_sets.append(extras)
        role_sets.append(roles)
        scroll_flags.append(scroll.startswith("SCROLL_"))
        if not tokens:
            failed.append(f"{path}: INTERNAL_SECTION_TOKENS empty")
        if not roles:
            failed.append(f"{path}: INTERNAL_ROLE_VALUES empty")

    if len(token_sets) == 2 and token_sets[0] != token_sets[1]:
        failed.append(f"INTERNAL_SECTION_TOKENS differ: {token_sets}")
    if len(extra_sets) == 2 and extra_sets[0] != extra_sets[1]:
        failed.append(f"EXTRA_BARE_SECTION_TOKENS differ: {extra_sets}")
    if len(role_sets) == 2 and role_sets[0] != role_sets[1]:
        failed.append(f"INTERNAL_ROLE_VALUES differ: {role_sets}")
    if len(scroll_flags) == 2 and scroll_flags[0] != scroll_flags[1]:
        failed.append("SCROLL_SECTION_REGEX presence differs between trees")

    if not token_sets or not role_sets:
        print("check_internal_label_parity FAIL")
        for line in failed:
            print(f"  {line}")
        return 1

    keys = expected_keys(token_sets[0], extra_sets[0], role_sets[0], scroll_flags[0] if scroll_flags else True)
    for tree in LANG_TREES:
        for name in LANG_FILES:
            path = tree / name
            if not path.is_file():
                failed.append(f"missing {path}")
                continue
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
            except json.JSONDecodeError as e:
                failed.append(f"{path}: JSON {e}")
                continue
            if not isinstance(data, dict):
                failed.append(f"{path}: not a JSON object")
                continue
            for key in keys:
                if key not in data:
                    failed.append(f"{path}: missing {key}")
                    continue
                val = data[key]
                if not isinstance(val, str) or not val.strip():
                    failed.append(f"{path}: empty {key}")
            roles = role_sets[0]
            for r in roles:
                rkey = "packai.label.role." + r
                if rkey not in data:
                    failed.append(f"{path}: missing role value label {rkey}")
                    continue
                rval = data[rkey]
                if not isinstance(rval, str) or not rval.strip():
                    failed.append(f"{path}: empty role value label {rkey}")
            for key in data:
                if not isinstance(key, str) or not key.startswith("packai.label.role."):
                    continue
                role_name = key[len("packai.label.role.") :]
                if role_name not in roles:
                    failed.append(
                        f"{path}: orphan role key {key} not in INTERNAL_ROLE_VALUES {roles}"
                    )

    if failed:
        print("check_internal_label_parity FAIL")
        for line in failed:
            print(f"  {line}")
        return 1
    print("check_internal_label_parity OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
