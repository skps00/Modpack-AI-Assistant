#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Panel focus line is one target and has no hover-tooltip duplicate."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCREEN = (
    "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java"
)
PROBE = "forge/1.19.2/src/main/java/com/skps9/packai/client/focus/FocusProbe.java"
LINE_KEYS = (
    "packai.focus.current",
    "packai.focus.none",
    "packai.focus.too_far",
    "packai.focus.entity_gone",
)


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def method_body(src: str, signature: str) -> str:
    at = src.find(signature)
    assert at >= 0, f"missing {signature}"
    brace = src.find("{", at)
    assert brace >= 0
    depth = 0
    for i in range(brace, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[brace : i + 1]
    raise AssertionError(f"unclosed {signature}")


def main() -> None:
    screen = read(SCREEN)
    probe = read(PROBE)

    assert "public static void openAndAskAbout(ItemStack stack)" in screen
    assert "openAndAskAboutItem" not in screen
    assert "boolean isPauseScreen()" in screen
    pause = method_body(screen, "boolean isPauseScreen()")
    assert "return false;" in pause
    assert "passEvents" not in screen

    tick = method_body(screen, "public void tick()")
    assert "panelProbeDue" in tick
    assert "FocusProbe.probe(" not in tick
    refresh = method_body(screen, "void refreshFocusCache()")
    assert refresh.count("FocusProbe.probe(") == 1
    assert "resolveTracked" in refresh
    assert "emitStateChange" in refresh

    line = method_body(screen, "void renderFocusLine(")
    assert line.count("drawString") == 1
    assert "addItemHover" not in line
    assert "renderTooltip" not in line
    assert "tooltip" not in line.lower()
    assert "setTooltip" not in line
    assert screen.count("renderFocusLine(") == 2  # def + one call

    keys = method_body(probe, "String panelLineKey(")
    for k in LINE_KEYS:
        assert keys.count(f'"{k}"') == 1, k
    assert "boolean panelProbeDue(" in probe

    buttons = method_body(screen, "int addFocusButtons(")
    assert "tooltip" not in buttons.lower()
    assert "packai.focus.ask" in buttons
    assert "packai.focus.pick_item" in buttons
    assert "packai.focus.pick_block" in buttons
    assert "packai.focus.pick_entity" in buttons
    assert "probeBlockOnly" in buttons
    assert "probeEntityOnly" in buttons
    assert "openAndAskAbout(" in buttons
    assert buttons.count("WidgetCompat.button(") == 4

    print("check_focus_panel OK")


if __name__ == "__main__":
    main()
