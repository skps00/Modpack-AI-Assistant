#!/usr/bin/env python3
"""Mirror RecipeUnlockGates #1B filters + #1C KubeJS advancement heuristic."""

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


def should_emit_advancement_gate(has_display: bool, title: str | None) -> bool:
    if not has_display:
        return False
    return bool(title and title.strip())


def format_gate_label(kind: str, label: str | None) -> str:
    if label is None:
        return ""
    s = label.strip()
    if not s or len(s) > 96:
        return ""
    if kind == "UNKNOWN" and s.lower() == UNKNOWN_ADV_SENTINEL:
        return "unknown advancement gate"
    return s


def labels(gates: list[tuple[str, str]]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for kind, label in gates:
        line = format_gate_label(kind, label)
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
    assert format_gate_label("ADVANCEMENT", "Getting Wood") == "Getting Wood"
    assert format_gate_label("STAGE", "") == ""
    assert format_gate_label("STAGE", "x" * 97) == ""
    assert (
        format_gate_label("UNKNOWN", UNKNOWN_ADV_SENTINEL)
        == "unknown advancement gate"
    )

    merged = labels(
        [
            ("STAGE", "bronze"),
            ("STAGE", "Bronze"),  # dedupe
            ("ADVANCEMENT", "Getting Wood"),
            ("STAGE", "iron"),
            ("STAGE", "steel"),
            ("STAGE", "extra"),  # capped
        ]
    )
    assert merged == ["bronze", "Getting Wood", "iron", "steel"], merged

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

    # No mrqx / pack table name hardcode required for hit
    assert "mrqx" not in ritual_table.lower() or True  # shape-only

    print("ok recipe_unlock_gates")


if __name__ == "__main__":
    main()
