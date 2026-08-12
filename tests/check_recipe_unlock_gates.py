#!/usr/bin/env python3
"""Mirror RecipeUnlockGates #1B/#1C + WP4 PlayerUnlockStatus literal checklist."""

from __future__ import annotations

import re

MAX_GATES = 4
UNKNOWN_ADV_SENTINEL = "unknown_advancement_gate"

KUBE_HANDLER_KEY = re.compile(
    r"['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]\s*:\s*function\s*\(",
    re.I,
)
IS_ADVANCEMENT_DONE = re.compile(r"\.isAdvancementDone\s*\(", re.I)
EVENT_CANCEL = re.compile(r"\bevent\.cancel\s*\(\s*\)", re.I)
ADV_LITERAL = re.compile(
    r"\.isAdvancementDone\s*\(\s*['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]\s*\)",
    re.I,
)
LITERAL_ID = re.compile(r"^[a-z0-9_]+:[a-z0-9_./-]+$")

SUFFIX = {
    "DONE": " [done]",
    "NOT_DONE": " [not done]",
    "UNREADABLE": " [unable to read]",
}


def should_emit_advancement_gate(has_display: bool, title: str | None) -> bool:
    if not has_display:
        return False
    return bool(title and title.strip())


def is_literal_advancement_id(label: str | None) -> bool:
    if label is None or not label.strip():
        return False
    s = label.strip().lower()
    if s == UNKNOWN_ADV_SENTINEL:
        return False
    return bool(LITERAL_ID.match(s))


def with_progress(kind: str, raw_label: str | None, display_base: str, progress: str | None) -> str:
    """Mirror PlayerUnlockStatus.withProgress — checklist only for ADVANCEMENT + literal."""
    base = display_base or ""
    if kind != "ADVANCEMENT":
        return base
    if not is_literal_advancement_id(raw_label):
        return base
    p = progress or "UNREADABLE"
    return base + SUFFIX.get(p, SUFFIX["UNREADABLE"])


def format_gate_label(
    kind: str, label: str | None, progress: str | None = None
) -> str:
    if label is None:
        return ""
    s = label.strip()
    if not s or len(s) > 96:
        return ""
    if kind == "UNKNOWN" and s.lower() == UNKNOWN_ADV_SENTINEL:
        # Honest miss — never append checklist
        return "unknown advancement gate"
    if kind == "ADVANCEMENT":
        return with_progress(kind, s, s, progress)
    return s


def labels(gates: list[tuple[str, str]], progress_map: dict[str, str] | None = None) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    progress_map = progress_map or {}
    for kind, label in gates:
        prog = None
        if kind == "ADVANCEMENT" and is_literal_advancement_id(label):
            prog = progress_map.get(label.strip().lower(), "UNREADABLE")
        line = format_gate_label(kind, label, prog)
        if not line:
            continue
        key = line.lower()
        if key in seen:
            continue
        seen.add(key)
        out.append(line)
        if len(out) >= MAX_GATES:
            break
    return out


def extract_balanced_body(text: str, after_open_paren: int) -> str | None:
    brace = text.find("{", after_open_paren)
    if brace < 0 or brace - after_open_paren > 80:
        return None
    depth = 0
    end = min(len(text), brace + 12_000)
    for i in range(brace, end):
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return text[brace + 1 : i]
    return None


def gates_from_advancement_body(body: str) -> list[tuple[str, str]]:
    literals: list[str] = []
    seen: set[str] = set()
    for m in ADV_LITERAL.finditer(body):
        adv = m.group(1).lower().strip()
        if adv and adv not in seen:
            seen.add(adv)
            literals.append(adv)
        if len(literals) >= MAX_GATES:
            break
    if literals:
        return [("ADVANCEMENT", a) for a in literals]
    return [("UNKNOWN", UNKNOWN_ADV_SENTINEL)]


def parse_kubejs_advancement_gates(text: str) -> dict[str, list[tuple[str, str]]]:
    if not text or not text.strip():
        return {}
    out: dict[str, list[tuple[str, str]]] = {}
    for m in KUBE_HANDLER_KEY.finditer(text):
        recipe_id = m.group(1).lower()
        body = extract_balanced_body(text, m.end())
        if not body or len(body) < 12:
            continue
        if not IS_ADVANCEMENT_DONE.search(body):
            continue
        if not EVENT_CANCEL.search(body):
            continue
        out[recipe_id] = gates_from_advancement_body(body)
    return out


def main() -> None:
    # Hidden recipe-book unlockers (no display) must not spam
    assert not should_emit_advancement_gate(False, "Stick")
    assert not should_emit_advancement_gate(True, "")
    assert not should_emit_advancement_gate(True, "   ")
    assert should_emit_advancement_gate(True, "Getting Wood")

    # Stage label passes through; FormatRequirements adds Unlock: prefix
    assert format_gate_label("STAGE", "bronze") == "bronze"
    # Title-only ADVANCEMENT: no fake checkbox
    assert format_gate_label("ADVANCEMENT", "Getting Wood") == "Getting Wood"
    assert format_gate_label("STAGE", "") == ""
    assert format_gate_label("STAGE", "x" * 97) == ""
    assert (
        format_gate_label("UNKNOWN", UNKNOWN_ADV_SENTINEL)
        == "unknown advancement gate"
    )
    # UNKNOWN never gets progress suffix
    unk = format_gate_label("UNKNOWN", UNKNOWN_ADV_SENTINEL, "DONE")
    assert unk == "unknown advancement gate"
    assert "[done]" not in unk.lower()

    # WP4: literal ADVANCEMENT three-state
    assert is_literal_advancement_id("mod:story/done")
    assert not is_literal_advancement_id("Getting Wood")
    assert not is_literal_advancement_id(UNKNOWN_ADV_SENTINEL)
    assert (
        format_gate_label("ADVANCEMENT", "mod:story/done", "DONE")
        == "mod:story/done [done]"
    )
    assert (
        format_gate_label("ADVANCEMENT", "mod:story/done", "NOT_DONE")
        == "mod:story/done [not done]"
    )
    assert (
        format_gate_label("ADVANCEMENT", "mod:story/done", "UNREADABLE")
        == "mod:story/done [unable to read]"
    )
    assert with_progress("UNKNOWN", UNKNOWN_ADV_SENTINEL, "unknown advancement gate", "DONE") == (
        "unknown advancement gate"
    )

    merged = labels(
        [
            ("STAGE", "bronze"),
            ("STAGE", "Bronze"),  # dedupe
            ("ADVANCEMENT", "Getting Wood"),  # title-only, no suffix
            ("STAGE", "iron"),
            ("STAGE", "steel"),
            ("STAGE", "extra"),  # capped
        ]
    )
    assert merged == ["bronze", "Getting Wood", "iron", "steel"], merged

    # Literal + mock progress map
    lit_labels = labels(
        [("ADVANCEMENT", "mod:story/done")],
        {"mod:story/done": "DONE"},
    )
    assert lit_labels == ["mod:story/done [done]"], lit_labels

    # Empty / missing soft-dep → empty
    assert labels([]) == []
    assert labels([("STAGE", "  ")]) == []

    # --- #1C happy: table/var isAdvancementDone + cancel → UNKNOWN (no invent list)
    ritual_table = """
const strategies = {
  'pack:ritual_mystery_flesh': function (event) {
    let player = event.player
    let b = false
    for (let adv in checkTable['mystery_flesh']) {
      if (!player.isAdvancementDone(checkTable['mystery_flesh'][adv])) {
        b = true
      }
    }
    if (b) {
      event.cancel()
    }
  },
}
"""
    hit = parse_kubejs_advancement_gates(ritual_table)
    assert "pack:ritual_mystery_flesh" in hit, hit
    assert hit["pack:ritual_mystery_flesh"] == [
        ("UNKNOWN", UNKNOWN_ADV_SENTINEL)
    ], hit
    # UNKNOWN gate labels: no fake checkbox even if progress would be DONE
    unk_line = labels(
        hit["pack:ritual_mystery_flesh"],
        {"anything": "DONE"},
    )
    assert unk_line == ["unknown advancement gate"], unk_line

    # --- #1C happy: literal advancement id → ADVANCEMENT
    ritual_literal = """
{
  'mod:ritual_locked': function (event) {
    if (!event.player.isAdvancementDone('mod:story/done')) {
      event.cancel()
    }
  }
}
"""
    lit = parse_kubejs_advancement_gates(ritual_literal)
    assert lit["mod:ritual_locked"] == [("ADVANCEMENT", "mod:story/done")], lit

    # --- #1C negative: isAdvancementDone without cancel → no gate
    no_cancel = """
{
  'mod:ritual_soft': function (event) {
    if (!event.player.isAdvancementDone('mod:story/x')) {
      event.player.tell('missing')
    }
  }
}
"""
    assert parse_kubejs_advancement_gates(no_cancel) == {}

    # --- #1C negative: cancel without isAdvancementDone → no gate
    no_adv = """
{
  'mod:ritual_busy': function (event) {
    if (event.player.isCrouching()) {
      event.cancel()
    }
  }
}
"""
    assert parse_kubejs_advancement_gates(no_adv) == {}

    # --- isolation: empty map for recipe A must not inherit B's UNKNOWN
    # (AskService global REQUIREMENTS no longer merges unlocks; per-id lookup only)
    mixed = parse_kubejs_advancement_gates(
        ritual_table
        + """
{
  'iceandfire:dragonsteel_lightning_ingot': function (event) {
    // no isAdvancementDone / cancel — not a gate handler
    event.player.tell('ok')
  }
}
"""
    )
    assert "pack:ritual_mystery_flesh" in mixed
    assert "iceandfire:dragonsteel_lightning_ingot" not in mixed
    assert labels(mixed.get("iceandfire:dragonsteel_lightning_ingot", [])) == []

    print("ok recipe_unlock_gates")


if __name__ == "__main__":
    main()
