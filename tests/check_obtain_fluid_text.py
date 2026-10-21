#!/usr/bin/env python3
"""Static gate for obtain fluid/requirement lang keys (T5)."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE_LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"
LANG_DIR = ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang"
LANGS = ("en_us", "zh_cn", "zh_tw")

OBTAIN_TEXT_KEYS = (
    "packai.obtain.fluid_source",
    "packai.obtain.fluid_source_missing",
    "packai.obtain.req.energy",
    "packai.obtain.req.extra",
)

CJK_IN_STRING = re.compile(r'"(?:[^"\\]|\\.)*[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff](?:[^"\\]|\\.)*"')


def read_logic(name: str) -> str:
    return (FORGE_LOGIC / name).read_text(encoding="utf-8")


def load_lang(lang: str) -> dict:
    return json.loads((LANG_DIR / f"{lang}.json").read_text(encoding="utf-8"))


def main() -> None:
    fails: list[str] = []
    text_src = read_logic("ObtainPathText.java")

    for key in OBTAIN_TEXT_KEYS:
        if key not in text_src:
            fails.append(f"ObtainPathText missing lang key {key}")

    for match in CJK_IN_STRING.finditer(text_src):
        fails.append(f"ObtainPathText CJK string literal: {match.group(0)[:80]}")

    lang_maps = {lang: load_lang(lang) for lang in LANGS}
    key_sets = [set(lang_maps[lang].keys()) for lang in LANGS]
    if key_sets[0] != key_sets[1] or key_sets[0] != key_sets[2]:
        fails.append(
            "lang key sets differ: "
            f"en_us={len(key_sets[0])} zh_cn={len(key_sets[1])} zh_tw={len(key_sets[2])}"
        )

    for lang in LANGS:
        for key in OBTAIN_TEXT_KEYS:
            if key not in lang_maps[lang]:
                fails.append(f"{lang}.json missing {key}")
            elif not str(lang_maps[lang][key]).strip():
                fails.append(f"{lang}.json empty value for {key}")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_fluid_text OK")


if __name__ == "__main__":
    main()
