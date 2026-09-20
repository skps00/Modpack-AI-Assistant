#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""C-0 / D-batch gate: SettingsScreenV2.renderScreen draw order.

Plan D 5B.6: after super.render, ONLY renderHoveredTips is allowed
(fallback also allows GuiShell.mutedCentered). Closed DRAW_CALL_RE name set is
insufficient — renamed paintDescPanel after super would false-green. This gate
scans *any* identifier call after super and allowlists post-super names.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCREEN = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsScreenV2.java"

# Any method/field call that looks like a statement call site.
ANY_CALL_RE = re.compile(r"\b([A-Za-z_][\w.]*)\s*\(")

# After super.render in a branch, only these may remain.
POST_SUPER_ALLOWED = {"WidgetCompat.renderHoveredTips"}
POST_SUPER_FALLBACK_EXTRA = {"GuiShell.mutedCentered"}

CUSTOM_BEFORE = {
    "GuiShell.title",
    "GuiShell.panel",
    "GuiShell.statusOk",
    "renderCategoryCol",
    "renderEntryList",
    "renderJeiList",
    "renderDesc",
    "renderBackground",
    "GuiShell.nestedShell",
}


def method_body(src: str, sig_prefix: str) -> str:
    idx = src.find(sig_prefix)
    if idx < 0:
        raise AssertionError(f"missing method: {sig_prefix}")
    brace = src.find("{", idx)
    if brace < 0:
        raise AssertionError(f"{sig_prefix}: no opening brace")
    depth = 0
    for i in range(brace, len(src)):
        c = src[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[brace : i + 1]
    raise AssertionError(f"{sig_prefix}: braces not balanced")


def split_branches(body: str) -> tuple[str, str]:
    m = re.search(r"if\s*\(\s*this\.layout\.fallback\s*\)\s*\{", body)
    if not m:
        raise AssertionError("renderScreen missing layout.fallback branch")
    start = m.end() - 1
    depth = 0
    end = None
    for i in range(start, len(body)):
        if body[i] == "{":
            depth += 1
        elif body[i] == "}":
            depth -= 1
            if depth == 0:
                end = i
                break
    if end is None:
        raise AssertionError("fallback branch braces unbalanced")
    return body[start : end + 1], body[end + 1 :]


def strip_strings_comments(block: str) -> str:
    """Rough strip so string literals do not invent fake call names."""
    no_line = re.sub(r"//[^\n]*", "", block)
    no_block = re.sub(r"/\*.*?\*/", "", no_line, flags=re.S)
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', no_block)


def call_names(block: str) -> list[str]:
    cleaned = strip_strings_comments(block)
    skip = {
        "if",
        "for",
        "while",
        "switch",
        "catch",
        "return",
        "new",
        "super",
        "this",
        "Math",
        "Math.max",
        "Math.min",
        "Component.translatable",
        "Boolean.toString",
        "Integer.toString",
        "List.of",
        "Set.of",
    }
    out: list[str] = []
    for m in ANY_CALL_RE.finditer(cleaned):
        name = m.group(1)
        if name in skip or name.startswith("Math."):
            continue
        # Normalize super.render
        if name == "super.render" or cleaned[m.start() : m.start() + 12] == "super.render":
            out.append("super.render")
            continue
        if name.endswith(".render") and "super" in cleaned[max(0, m.start() - 6) : m.start()]:
            out.append("super.render")
            continue
        out.append(name)
    # Fix: ANY_CALL_RE on "super.render(" captures "super.render" if we use that pattern —
    # actually group is "super.render" only if we allow dots. Our pattern has [\w.]* so
    # "super.render" works when written as super.render(
    return out


def normalize_calls(block: str) -> list[str]:
    cleaned = strip_strings_comments(block)
    names: list[str] = []
    # Prefer explicit super.render detection
    for m in re.finditer(
        r"\b(?:super\.render|WidgetCompat\.renderHoveredTips|GuiShell\.\w+|render\w+)\s*\(",
        cleaned,
    ):
        names.append(m.group(0).rstrip(" \t(").strip())
    return names


def check_branch(label: str, block: str, *, expect_custom_before_super: bool) -> list[str]:
    errs: list[str] = []
    names = normalize_calls(block)
    if "super.render" not in names:
        errs.append(f"{label}: missing super.render")
        return errs
    if names.count("WidgetCompat.renderHoveredTips") != 1:
        errs.append(
            f"{label}: renderHoveredTips count={names.count('WidgetCompat.renderHoveredTips')} want 1"
        )
    if names[-1] != "WidgetCompat.renderHoveredTips":
        errs.append(f"{label}: last draw call is {names[-1]!r}, want renderHoveredTips")

    super_i = names.index("super.render")
    after = names[super_i + 1 :]
    allowed = set(POST_SUPER_ALLOWED)
    if label == "fallback":
        allowed |= POST_SUPER_FALLBACK_EXTRA
    for n in after:
        if n not in allowed:
            errs.append(f"{label}: draw after super.render: {n}")

    # Stronger allowlist: any *paint* / *render* / GuiShell after super via raw scan
    after_src = block.split("super.render", 1)[1] if "super.render" in block else ""
    after_src = strip_strings_comments(after_src)
    # Drop the tips call itself from scrutiny of "extra" names
    probe = after_src
    for banned in re.finditer(
        r"\b(paint\w+|render(?!HoveredTips)\w+|GuiShell\.\w+)\s*\(", probe
    ):
        name = banned.group(1)
        if name == "GuiShell.mutedCentered" and label == "fallback":
            continue
        if name.startswith("render") and "renderHoveredTips" in name:
            continue
        # WidgetCompat.renderHoveredTips already allowed via normalize
        if name in ("GuiShell.mutedCentered",) and label == "fallback":
            continue
        # Flag unknown paint*/render* (except tips) and GuiShell.* after super
        if name.startswith("paint") or (
            name.startswith("render") and name != "renderHoveredTips"
        ):
            errs.append(f"{label}: post-super banned call {name}")
        if name.startswith("GuiShell.") and name not in allowed:
            errs.append(f"{label}: post-super GuiShell {name}")

    before = names[:super_i]
    if expect_custom_before_super:
        if "renderEntryList" not in before and "renderJeiList" not in before:
            errs.append(f"{label}: missing renderEntryList/renderJeiList before super")
        if "GuiShell.title" not in before:
            errs.append(f"{label}: missing GuiShell.title before super")
    else:
        if "renderEntryList" in names or "renderJeiList" in names or "renderDesc" in names:
            errs.append(f"{label}: must not paint entry/desc layers")
        if "GuiShell.mutedCentered" not in after:
            errs.append(f"{label}: too_small mutedCentered must be after super.render")
        mid = after[:-1] if after and after[-1] == "WidgetCompat.renderHoveredTips" else after
        if mid != ["GuiShell.mutedCentered"]:
            errs.append(f"{label}: expected [mutedCentered, tips] after super, got {after}")
    return errs


def check_mouse_super_first(src: str) -> list[str]:
    errs: list[str] = []
    body = method_body(src, "public boolean mouseClicked(")
    if "super.mouseClicked" not in body:
        return ["mouseClicked: missing super.mouseClicked"]
    super_pos = body.find("super.mouseClicked")
    custom_pos = body.find("this.layout.entryList")
    if custom_pos < 0:
        errs.append("mouseClicked: missing entryList custom branch")
    elif super_pos > custom_pos:
        errs.append("mouseClicked: super.mouseClicked must run before entryList custom logic")
    if "rebuildUiPreservingDraft" not in src:
        errs.append("missing rebuildUiPreservingDraft (scroll/search draft pin)")
    if "private String editDraft" not in src:
        errs.append("missing durable editDraft field (off-screen scroll)")
    scroll_body = method_body(src, "public boolean mouseScrolled(")
    if "rebuildUiPreservingDraft" not in scroll_body:
        errs.append("mouseScrolled: must call rebuildUiPreservingDraft while editing")
    return errs


def negative_control_paint_after_super() -> list[str]:
    """Inject paintDescPanel after super → must FAIL (D 5B.6)."""
    src = SCREEN.read_text(encoding="utf-8")
    body = method_body(src, "private void renderScreen(")
    _, rest = split_branches(body)
    poisoned = rest.replace(
        "super.render(graphics.pose(), mouseX, mouseY, partialTick);",
        "super.render(graphics.pose(), mouseX, mouseY, partialTick);\n"
        "        paintDescPanel(graphics, mouseX, mouseY);",
        1,
    )
    errs = check_branch("non-fallback", poisoned, expect_custom_before_super=True)
    if not any("paintDescPanel" in e or "post-super" in e for e in errs):
        return [
            "negative control: injecting paintDescPanel after super did not fail "
            f"(errs={errs})"
        ]
    return []


def main() -> int:
    src = SCREEN.read_text(encoding="utf-8")
    body = method_body(src, "private void renderScreen(")
    fallback, rest = split_branches(body)
    errs: list[str] = []
    errs.extend(check_branch("fallback", fallback, expect_custom_before_super=False))
    errs.extend(check_branch("non-fallback", rest, expect_custom_before_super=True))

    tips_total = normalize_calls(body).count("WidgetCompat.renderHoveredTips")
    if tips_total != 2:
        errs.append(f"renderHoveredTips total in renderScreen={tips_total} want 2")

    errs.extend(check_mouse_super_first(src))
    errs.extend(negative_control_paint_after_super())

    if errs:
        for e in errs:
            print(f"FAIL: {e}")
        return 1
    print("check_settings_render_order OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
