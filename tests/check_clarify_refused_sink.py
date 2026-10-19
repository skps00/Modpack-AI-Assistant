#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""clarifyRefused must be read by production code (ClarifyBody) and consumed by AskEngine"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BODY = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/ClarifyBody.java"
ENGINE = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java"


def main() -> int:
    body = BODY.read_text(encoding="utf-8")
    engine = ENGINE.read_text(encoding="utf-8")
    assert "loop.clarifyRefused()" in body, "ClarifyBody never reads loop.clarifyRefused()"
    assert "ClarifyBody.clarifyRefusedBlank(" in engine, "clarifyRefusedBlank call missing"
    print("check_clarify_refused_sink OK")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as e:
        print(f"FAIL: {e}", file=sys.stderr)
        raise SystemExit(1)
