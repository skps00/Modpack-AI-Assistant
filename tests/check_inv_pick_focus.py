"""Focus/extras rules for inventory multi-select ask + chat labels / sticky target."""

from __future__ import annotations

# Mirror AskService multi-select extras caps (MAX_PENDING=8 → 7 non-focus).
MAX_PENDING_ITEMS = 8
MAX_EXTRAS_CONTEXT = MAX_PENDING_ITEMS - 1
MAX_EXTRAS_JEI_CHARS_EACH = 1800
MAX_EXTRAS_JEI_CHARS_TOTAL = MAX_EXTRAS_JEI_CHARS_EACH * MAX_EXTRAS_CONTEXT


def resolve_focus(jei_id: str | None, selected: list[str]) -> str | None:
    if jei_id:
        return jei_id
    for s in selected:
        if s:
            return s
    return None


def selection_key(item_id: str, schematic: str | None = None, label: str | None = None) -> str:
    """Mirror AskService.selectionKey (id + schematic / sample label)."""
    sid = (item_id or "").lower()
    if schematic:
        return f"{sid}#{schematic.lower()}"
    if label:
        return f"{sid}@{label.strip().lower()}"
    return sid


def extras_for(
    focus: str | None,
    selected: list[str] | list[tuple[str, str | None]],
    *,
    focus_schematic: str | None = None,
) -> list[str]:
    """selected: bare ids or (id, schematic) so scroll_rolled variants stay distinct."""
    fkey = selection_key(focus or "", focus_schematic)
    out: list[str] = []
    seen: set[str] = set()
    for entry in selected:
        if isinstance(entry, tuple):
            s, sch = entry[0], entry[1] if len(entry) > 1 else None
        else:
            s, sch = entry, None
        if not s:
            continue
        key = selection_key(s, sch)
        if key == fkey or key in seen:
            continue
        seen.add(key)
        out.append(s)
    return out


def normalize_selected(
    selected: list[tuple[str, str | None]],
) -> list[str]:
    """(id, schematic|None) → deduped ids via selection_key."""
    out: list[str] = []
    seen: set[str] = set()
    for item_id, schematic in selected:
        if not item_id:
            continue
        key = selection_key(item_id, schematic)
        if key in seen:
            continue
        seen.add(key)
        out.append(item_id)
        if len(out) >= MAX_PENDING_ITEMS:
            break
    return out


def context_focus(
    pin_or_draft: str | None,
    last_ask: str | None,
    pending: list[str],
) -> str | None:
    """Mirror AiAssistantScreen.contextStack without JEI hover."""
    if pin_or_draft:
        return pin_or_draft
    if pending:
        if last_ask:
            lid = last_ask.lower()
            for p in pending:
                if p and p.lower() == lid:
                    return last_ask
        return pending[0]
    return last_ask or None


def selected_item_labels(selected: list[tuple[str, str]], fallback: str) -> str:
    """(id, label) list → ``a][b`` so UI wraps once as ``[a][b]``."""
    parts: list[str] = []
    seen: set[str] = set()
    for item_id, label in selected:
        if not item_id:
            continue
        sid = item_id.lower()
        if sid in seen:
            continue
        seen.add(sid)
        name = (label or item_id).strip()
        if name:
            parts.append(name)
        if len(parts) >= 8:
            break
    return "][".join(parts) if parts else fallback


def strip_label_kind(pending_n: int) -> str:
    """Strip text mode: pending always wins over sticky Targeted."""
    return "picked" if pending_n > 0 else "targeted"


def selected_subjects(focus: str | None, extras: list[str]) -> list[str]:
    """LLM selectedItems order: focus then alsoSelected extras."""
    out: list[str] = []
    seen: set[str] = set()
    for s in ([focus] if focus else []) + list(extras):
        if not s:
            continue
        sid = s.lower()
        if sid in seen:
            continue
        seen.add(sid)
        out.append(s)
    return out


def extras_purpose_headers(extras: list[str], cap: int = MAX_EXTRAS_CONTEXT) -> list[str]:
    """Mirror AskService.extrasPurposeBlock headers (id only)."""
    out: list[str] = []
    for s in extras:
        if not s:
            continue
        out.append(f"--- alsoSelected: {s} ---")
        if len(out) >= cap:
            break
    return out


def llm_multiselect_flags(extras: list[str]) -> dict:
    """Keys LlmClient adds when extras (hotbar) non-empty."""
    if not extras:
        return {}
    return {
        "alsoSelected": list(extras),
        "answerAllSelected": True,
    }


def main() -> None:
    assert resolve_focus(None, []) is None
    assert resolve_focus(None, ["mod:a", "mod:b"]) == "mod:a"
    assert resolve_focus("mod:jei", ["mod:a"]) == "mod:jei"
    assert extras_for("mod:a", ["mod:a", "mod:b", "mod:b"]) == ["mod:b"]
    assert extras_for("mod:jei", ["mod:a", "mod:b"]) == ["mod:a", "mod:b"]
    # Two tetra:scroll_rolled variants must not collapse
    scroll = "tetra:scroll_rolled"
    assert normalize_selected(
        [(scroll, "tetra:mirror"), (scroll, "tetra:energy_bottle")]
    ) == [scroll, scroll]
    assert extras_for(
        scroll,
        [(scroll, "tetra:mirror"), (scroll, "tetra:energy_bottle")],
        focus_schematic="tetra:mirror",
    ) == [scroll]

    # Sticky lastAsk coal must not win when pending is only axe.
    assert context_focus(None, "minecraft:coal", ["minecraft:iron_axe"]) == "minecraft:iron_axe"
    assert context_focus(None, "minecraft:coal", ["minecraft:coal", "minecraft:iron_axe"]) == "minecraft:coal"
    assert context_focus(None, "minecraft:coal", []) == "minecraft:coal"
    assert context_focus("minecraft:dirt", "minecraft:coal", ["minecraft:iron_axe"]) == "minecraft:dirt"

    labels = selected_item_labels(
        [("minecraft:iron_axe", "铁斧"), ("minecraft:coal", "煤炭")],
        "fallback",
    )
    assert labels == "铁斧][煤炭"
    assert f"[{labels}]" == "[铁斧][煤炭]"
    assert selected_item_labels([], "空") == "空"

    assert strip_label_kind(2) == "picked"
    assert strip_label_kind(0) == "targeted"

    # Multi-select ask: focus first pending, rest extras (payload path).
    pending = ["minecraft:iron_axe", "minecraft:coal"]
    focus = context_focus(None, "minecraft:coal", pending)
    assert focus == "minecraft:coal"  # still in pending → keep sticky
    assert extras_for(focus, pending) == ["minecraft:iron_axe"]
    focus2 = context_focus(None, None, pending)
    assert focus2 == "minecraft:iron_axe"
    assert extras_for(focus2, pending) == ["minecraft:coal"]

    # Multi-select LLM: all subjects + alsoSelected flags + PURPOSE headers for extras.
    assert selected_subjects(focus2, extras_for(focus2, pending)) == [
        "minecraft:iron_axe",
        "minecraft:coal",
    ]
    assert MAX_EXTRAS_CONTEXT == MAX_PENDING_ITEMS - 1
    assert MAX_EXTRAS_JEI_CHARS_EACH == 1800
    assert MAX_EXTRAS_JEI_CHARS_TOTAL == MAX_EXTRAS_JEI_CHARS_EACH * MAX_EXTRAS_CONTEXT
    # Total covers all non-focus extras at full each-cap.
    assert MAX_EXTRAS_JEI_CHARS_TOTAL >= MAX_EXTRAS_JEI_CHARS_EACH * 3
    assert MAX_EXTRAS_CONTEXT == 7
    five = ["minecraft:coal", "minecraft:dirt", "a", "b", "c"]
    assert extras_purpose_headers(five) == [f"--- alsoSelected: {s} ---" for s in five]
    eight = [f"mod:e{i}" for i in range(8)]
    assert len(extras_purpose_headers(eight)) == MAX_EXTRAS_CONTEXT
    assert extras_purpose_headers(eight)[-1] == "--- alsoSelected: mod:e6 ---"
    flags = llm_multiselect_flags(["minecraft:coal"])
    assert flags["answerAllSelected"] is True
    assert flags["alsoSelected"] == ["minecraft:coal"]
    assert llm_multiselect_flags([]) == {}

    print("check_inv_pick_focus: OK")


if __name__ == "__main__":
    main()
