#!/usr/bin/env python3
"""Lang how-to-get headings must match AskReplyScrub.HOW_TO_GET_LABEL (both trees)."""

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
HOW_TO_GET_KEY = "packai.reply.section.how_to_get"


def extract_java_string(src: str, name: str) -> str:
    m = re.search(
        r"static final String " + re.escape(name) + r'\s*=\s*"([^"]+)"',
        src,
    )
    if not m:
        raise AssertionError(f"missing String {name}")
    return m.group(1)


def extract_insert_labels(src: str) -> tuple[str, str]:
    m = re.search(
        r'howToGetInsertHeading\s*\([^)]*\)\s*\{.*?han\s*\?\s*"([^"]+)"\s*:\s*"([^"]+)"',
        src,
        re.S,
    )
    if not m:
        raise AssertionError("missing howToGetInsertHeading hardcoded labels")
    return m.group(1), m.group(2)


def label_and_head(src: str) -> tuple[re.Pattern[str], re.Pattern[str]]:
    label = extract_java_string(src, "HOW_TO_GET_LABEL")
    # Mirror HOW_TO_GET_HEAD_PREFIX + HOW_TO_GET_HEAD in AskReplyScrub.
    head_prefix = (
        r"(?:#{1,3}[ \t]*)?(?:\d+[.)][ \t]*)?(?:[【\[])?" + label + r"(?:[】\]])?"
    )
    label_re = re.compile(label, re.IGNORECASE)
    head_re = re.compile(r"(?im)^[ \t]*" + head_prefix + r"(?:[:：]|\s|\z)")
    return label_re, head_re


def main() -> int:
    failed: list[str] = []
    labels: list[str] = []
    heads: list[re.Pattern[str]] = []
    insert_pairs: list[tuple[str, str]] = []
    for path in SCRUB_PATHS:
        if not path.is_file():
            failed.append(f"missing {path}")
            continue
        src = path.read_text(encoding="utf-8")
        try:
            label_re, head_re = label_and_head(src)
            han, en = extract_insert_labels(src)
        except AssertionError as e:
            failed.append(f"{path}: {e}")
            continue
        labels.append(extract_java_string(src, "HOW_TO_GET_LABEL"))
        heads.append(head_re)
        insert_pairs.append((han, en))
        if not label_re.search(han):
            failed.append(f"{path}: insert label {han!r} not matched by HOW_TO_GET_LABEL")
        if not label_re.search(en):
            failed.append(f"{path}: insert label {en!r} not matched by HOW_TO_GET_LABEL")
        if not head_re.search(han):
            failed.append(f"{path}: insert label {han!r} not matched by HOW_TO_GET_HEAD")
        if not head_re.search(en):
            failed.append(f"{path}: insert label {en!r} not matched by HOW_TO_GET_HEAD")

    if len(labels) == 2 and labels[0] != labels[1]:
        failed.append("HOW_TO_GET_LABEL differs between forge and neoforge")
    if len(insert_pairs) == 2 and insert_pairs[0] != insert_pairs[1]:
        failed.append("howToGetInsertHeading labels differ between forge and neoforge")

    if not heads:
        failed.append("no HOW_TO_GET_HEAD compiled from AskReplyScrub")
    else:
        for tree in LANG_TREES:
            for name in LANG_FILES:
                path = tree / name
                if not path.is_file():
                    failed.append(f"missing {path}")
                    continue
                data = json.loads(path.read_text(encoding="utf-8"))
                if HOW_TO_GET_KEY not in data:
                    failed.append(f"{path}: missing {HOW_TO_GET_KEY}")
                    continue
                val = data[HOW_TO_GET_KEY]
                if not isinstance(val, str) or not val.strip():
                    failed.append(f"{path}: empty {HOW_TO_GET_KEY}")
                    continue
                for i, hr in enumerate(heads):
                    if hr.search(val.strip()) is None:
                        failed.append(
                            f"{path}: {HOW_TO_GET_KEY}={val!r} not matched by HOW_TO_GET_LABEL/HEAD in {SCRUB_PATHS[i]}"
                        )

    if failed:
        print("check_howto_get_label_parity FAIL")
        for line in failed:
            print(f"  {line}")
        return 1
    print("check_howto_get_label_parity OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
