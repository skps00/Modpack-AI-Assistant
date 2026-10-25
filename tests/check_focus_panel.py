#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Panel focus line is one target and has no hover-tooltip duplicate."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCREEN = (
    "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java"
)
SEGMENTED = (
    "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/SegmentedFocusControl.java"
)
PROBE = "forge/1.19.2/src/main/java/com/skps9/packai/client/focus/FocusProbe.java"
LINE_KEYS = (
    "packai.focus.current",
    "packai.focus.none",
    "packai.focus.too_far",
    "packai.focus.entity_gone",
)
FOCUS_MODE_KEYS = (
    "packai.focus.pick_item",
    "packai.focus.pick_block",
    "packai.focus.pick_entity",
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
    segmented_src = read(SEGMENTED)
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
    assert "FocusLineMode.resolve" in line
    assert "panelLineKey" not in line
    assert line.count("drawString") == 1
    assert "modeIconStack" in line
    assert "renderItem" in line
    assert "addItemHover" in line
    assert "renderTooltip" not in line
    assert "tooltip" not in line.lower()
    assert "setTooltip" not in line
    # def + single render() call site (unchanged after SegmentedFocusControl layout)
    assert screen.count("renderFocusLine(") == 2

    keys = method_body(probe, "String panelLineKey(")
    for k in LINE_KEYS:
        assert keys.count(f'"{k}"') == 1, k
    assert "boolean panelProbeDue(" in probe

    assert "int addFocusButtons(" not in screen
    assert screen.count("new SegmentedFocusControl(") >= 1
    assert "packai.focus.ask" not in screen
    assert "packai.focus.ask" not in segmented_src
    focus_ui = screen + "\n" + segmented_src
    for k in FOCUS_MODE_KEYS:
        assert k in focus_ui, k

    modes = method_body(screen, "private void applyFocusMode(")
    assert "tooltip" not in modes.lower()
    assert "probeBlockOnly" in modes
    assert "probeEntityOnly" in modes

    print("check_focus_panel OK")


if __name__ == "__main__":
    main()
