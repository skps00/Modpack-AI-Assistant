#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Focus row uses FocusLineMode harness + gradle task."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODE = (
    "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/FocusLineMode.java"
)
CHECK = (
    "forge/1.19.2/src/test/java/com/skps9/packai/client/gui/FocusLineModeCheck.java"
)
TMP = ROOT / "forge/1.19.2/tmp-check.gradle"


def main() -> None:
    mode = (ROOT / MODE).read_text(encoding="utf-8")
    assert "packai.focus.no_item" in mode
    assert "packai.focus.no_block" in mode
    assert "packai.focus.no_entity" in mode
    assert "modeAware" in mode

    check = (ROOT / CHECK).read_text(encoding="utf-8")
    assert "checkNegativeControlIgnoresMode" in check
    assert "checkItemModeIconLayout" in check
    assert "checkBlockModeIconLayout" in check
    assert "checkNegativeControlUnselectedIcon" in check
    assert "modeIconStack" in mode
    assert "MODE_ICON_TEXT_SHIFT" in mode
    assert "omitIconWhenUnselected" in mode

    gradle = TMP.read_text(encoding="utf-8")
    assert "runFocusLineModeCheck" in gradle
    assert "FocusLineModeCheck" in gradle

    print("check_focus_line_mode OK")


if __name__ == "__main__":
    main()
