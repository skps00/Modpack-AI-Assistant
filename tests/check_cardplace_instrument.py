#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""源碼形狀閘，唔驗行為。

Pins:
1. PackAiConfig.cardPlacementDiagLog define defaults to false.
2. AiAssistantScreen has exactly two ``Pack AI cardplace:`` logs inside
   ``if (PackAiConfig.cardPlacementDiagLog())``, before ``if (parts.isEmpty())``.
3. RecipeEmbed.java contains no cardplace log (placement logic untouched).
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java"
SCREEN = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java"
EMBED = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/RecipeEmbed.java"

GATE = "if (PackAiConfig.cardPlacementDiagLog())"
DEFINE = '.define("cardPlacementDiagLog", false)'
LOG = "Pack AI cardplace:"


def fail(msg: str) -> int:
    print(f"FAIL: {msg}")
    return 1


def main() -> int:
    if not CFG.is_file():
        return fail(f"missing {CFG}")
    if not SCREEN.is_file():
        return fail(f"missing {SCREEN}")
    if not EMBED.is_file():
        return fail(f"missing {EMBED}")

    cfg = CFG.read_text(encoding="utf-8")
    if DEFINE not in cfg:
        return fail(f"missing default-false define: {DEFINE}")
    if "boolean cardPlacementDiagLog()" not in cfg:
        return fail("missing cardPlacementDiagLog() accessor")

    screen = SCREEN.read_text(encoding="utf-8")
    if screen.count(LOG) != 2:
        return fail(f"expected exactly 2 {LOG!r} lines, got {screen.count(LOG)}")
    i_gate = screen.find(GATE)
    if i_gate < 0:
        return fail(f"missing gate {GATE}")
    i_empty = screen.find("if (parts.isEmpty())", i_gate)
    if i_empty < 0:
        return fail("gate is not before if (parts.isEmpty())")
    between = screen[i_gate:i_empty]
    if between.count(LOG) != 2:
        return fail("cardplace logs are not inside cardPlacementDiagLog() gate")
    if "Pack AI cardplace: n=" not in between or "Pack AI cardplace: cards=" not in between:
        return fail("missing n= or cards= cardplace log line inside gate")

    embed = EMBED.read_text(encoding="utf-8")
    if "cardplace" in embed.lower():
        return fail("RecipeEmbed.java must not contain a cardplace log")

    print("check_cardplace_instrument OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
