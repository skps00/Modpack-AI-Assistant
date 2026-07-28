"""Forge GuiGraphics tooltips must use remappable Screen calls, not string reflection."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/GuiGraphics.java"


def main() -> None:
    text = SRC.read_text(encoding="utf-8")
    assert "invokeScreen" not in text, "tooltip must not use invokeScreen string reflection"
    assert 'getDeclaredMethod' not in text, "tooltip must not reflect method names"
    assert "renderComponentTooltip" in text, "item/text tips need Screen.renderComponentTooltip"
    assert "getTooltipFromItem" in text, "item tips need Screen.getTooltipFromItem"
    print("check_forge_tooltip_remap: OK")


if __name__ == "__main__":
    main()
