#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static gate: Forge 1.19.2 registers packai.mixins.json and names the refmap."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "forge" / "1.19.2" / "build.gradle"
MIXINS = ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "packai.mixins.json"


def fail(msg: str) -> None:
    print(msg)
    sys.exit(1)


def main() -> None:
    text = GRADLE.read_text(encoding="utf-8")
    for needle in (
        "org.spongepowered.mixin",
        "mixin {",
        'add sourceSets.main, "packai.refmap.json"',
        "MixinConfigs",
    ):
        if needle not in text:
            fail(f"FAIL build.gradle missing {needle!r}")
    with MIXINS.open(encoding="utf-8") as fh:
        data = json.load(fh)
    if data.get("refmap") != "packai.refmap.json":
        fail(f"FAIL packai.mixins.json refmap={data.get('refmap')!r}")
    print("OK check_mixin_registration")


if __name__ == "__main__":
    main()
