#!/usr/bin/env python3
"""Mirror AskMarkerRepair — FACT-grounded re-attach; no invent ids."""
from __future__ import annotations

import re

REGISTRY_REF = r"[a-z0-9_]+(?::[a-z0-9_./-]+)+"
FLAT_SNBT = r"\{[^}]*\}"
RECIPE_MARKER = re.compile(
    r"(?:\{\{\s*RECIPE(?:\s*:\s*(\d+|" + REGISTRY_REF + r"))?\s*\}\}"
    r"|\[\[\s*recipe(?:_card)?\s*:\s*(\d+|" + REGISTRY_REF + r")\s*\]\])",
    re.I,
)
ITEM_MARKER = re.compile(
    r"(?:\[\[\s*item\s*:\s*(" + REGISTRY_REF + r")(" + FLAT_SNBT + r")?(?:\s*[×xX*]\s*(\d+))?\s*\]\]"
    r"|\{\{\s*item\s*:\s*(" + REGISTRY_REF + r")(" + FLAT_SNBT + r")?(?:\s*[×xX*]\s*(\d+))?\s*\}\}"
    r"|\{\s*item\s*:\s*(" + REGISTRY_REF + r")(" + FLAT_SNBT + r")?(?:\s*[×xX*]\s*(\d+))?\s*\})",
    re.I,
)
ANY_MARKER = re.compile(RECIPE_MARKER.pattern + "|" + ITEM_MARKER.pattern, re.I)
EMPTY_ITEM = re.compile(
    r"(?:\{\{\s*item\s*:\s*\}\}|\[\[\s*item\s*:\s*\]\]|\{\s*item\s*:\s*\})",
    re.I,
)
SOURCES = re.compile(r"(?m)(【來源】|【来源】|\[Sources\])")
MAX_REINSERT = 16


def normalize_registry_ref(ref: str) -> str:
    raw = (ref or "").strip()
    snbt = ""
    brace = raw.find("{")
    if brace > 0:
        snbt = raw[brace:].lower()
        raw = raw[:brace].strip()
    r = raw.lower()
    if not r:
        return snbt
    if re.fullmatch(r"\d+", r):
        return r
    if r.startswith("mod:") and ":" in r[4:]:
        r = r[4:]
    return r + snbt


def bare_registry_id(ref: str) -> str:
    r = (ref or "").strip().lower()
    brace = r.find("{")
    if brace > 0:
        r = r[:brace]
    if r.startswith("mod:") and ":" in r[4:]:
        r = r[4:]
    return r


def _item_payload(m: re.Match) -> str:
    id_ = m.group(1) or m.group(4) or m.group(7) or ""
    snbt = m.group(2) or m.group(5) or m.group(8) or ""
    return id_ + snbt


def marker_key(exact: str) -> str:
    item = ITEM_MARKER.fullmatch(exact.strip()) if exact else None
    if item is None and exact:
        item = ITEM_MARKER.search(exact)
        if item and item.group(0) != exact:
            item = ITEM_MARKER.match(exact)
    if exact:
        im = ITEM_MARKER.match(exact)
        if im:
            return "item:" + normalize_registry_ref(_item_payload(im))
        rm = RECIPE_MARKER.match(exact)
        if rm:
            ref = rm.group(1) or rm.group(2) or ""
            if not ref:
                return "recipe:"
            return "recipe:" + normalize_registry_ref(ref)
    return ""


def collect_from_texts(texts: list[str]) -> list[str]:
    ordered: list[str] = []
    seen: set[str] = set()
    for text in texts or []:
        if not text:
            continue
        for m in ANY_MARKER.finditer(text):
            exact = m.group(0)
            if exact and exact not in seen:
                seen.add(exact)
                ordered.append(exact)
    return ordered


def collect_allowed(fact_lines: list[str], cards=None, suggested=None) -> list[str]:
    # cards / suggested never invent markers
    return collect_from_texts(fact_lines)


def _unique_allowed_for_bare(bare: str, allowed: list[str]) -> str | None:
    hit = None
    for exact in allowed:
        im = ITEM_MARKER.match(exact)
        if not im:
            continue
        id_ = im.group(1) or im.group(4) or im.group(7) or ""
        if bare_registry_id(id_) != bare:
            continue
        if hit is not None:
            return None
        hit = exact
    return hit


def _upgrade_bare(body: str, allowed: list[str]) -> str:
    def repl(m: re.Match) -> str:
        exact = m.group(0)
        id_ = m.group(1) or m.group(4) or m.group(7) or ""
        snbt = m.group(2) or m.group(5) or m.group(8) or ""
        if not id_ or snbt:
            return exact
        unique = _unique_allowed_for_bare(bare_registry_id(id_), allowed)
        if unique is None or "{" not in unique.lower():
            return exact
        if marker_key(unique) == marker_key(exact):
            return exact
        return unique

    return ITEM_MARKER.sub(repl, body)


def _repair_empty(body: str, allowed: list[str]) -> str:
    if not EMPTY_ITEM.search(body):
        return body
    if len(allowed) != 1:
        return EMPTY_ITEM.sub("", body)
    return EMPTY_ITEM.sub(allowed[0], body)


def _reinsert_missing(body: str, allowed: list[str]) -> str:
    present: set[str] = set()
    for m in ANY_MARKER.finditer(body or ""):
        key = marker_key(m.group(0))
        if key:
            present.add(key)
    missing: list[str] = []
    for exact in allowed:
        key = marker_key(exact)
        if not key or key in present:
            continue
        missing.append(exact)
        present.add(key)
        if len(missing) >= MAX_REINSERT:
            break
    if not missing:
        return body
    block = " ".join(missing)
    m = SOURCES.search(body or "")
    if m:
        before = (body[: m.start()]).rstrip()
        after = body[m.start() :]
        if not before:
            return block + "\n\n" + after
        return before + "\n\n" + block + "\n\n" + after
    if not (body or "").strip():
        return block
    return block + "\n\n" + (body or "").lstrip()


def repair(answer: str, allowed: list[str]) -> str:
    if not allowed:
        return answer or ""
    body = answer or ""
    body = _upgrade_bare(body, allowed)
    body = _repair_empty(body, allowed)
    body = _reinsert_missing(body, allowed)
    return body


def main() -> None:
    pearl = '{{item:gateways:gate_pearl{gateway:"b_a_d:friend"}}}'
    iron = "{{item:minecraft:iron_ingot}}"
    recipe = "[[recipe:minecraft:stick]]"
    facts = [
        pearl + " Open gateway challenge.",
        "Craft with " + iron,
        "Use " + recipe,
    ]
    allowed = collect_allowed(facts)
    assert pearl in allowed, allowed
    assert iron in allowed, allowed
    assert recipe in allowed, allowed

    stripped = "Open gateway challenge.\nCraft with iron.\nUse sticks.\n\n[Sources] JEI"
    repaired = repair(stripped, allowed)
    assert pearl in repaired, repaired
    assert iron in repaired, repaired
    assert recipe in repaired, repaired

    alien = repair(
        "No embeds here.\n\n[Sources] JEI",
        collect_allowed(["plain fact without markers"]),
    )
    assert "{{item:" not in alien, alien
    assert "[[recipe:" not in alien, alien

    upgraded = repair("Use {{item:gateways:gate_pearl}} to open.\n\n[Sources] JEI", [pearl])
    assert pearl in upgraded, upgraded

    fixed_empty = repair("See {{item:}} here.\n\n[Sources] JEI", [iron])
    assert iron in fixed_empty, fixed_empty

    # Empty + multi FACT: strip shell; re-attach all missing (still ⊆ FACT, not invent)
    amb = repair("See {{item:}} here.", [iron, pearl])
    assert "{{item:}}" not in amb, amb
    assert iron in amb and pearl in amb, amb

    # invent ban: suggested bare id must not create marker when FACT empty
    no_invent = repair("hello", collect_allowed([], cards=None, suggested=["minecraft:diamond"]))
    assert "{{item:" not in no_invent, no_invent

    # Sentinel: AI tool-emission strip (no buildDisplayCards)
    from pathlib import Path

    root = Path(__file__).resolve().parents[1]
    for tree in ("forge/1.19.2", "neoforge/1.21.1"):
        ask = (root / tree / "src/main/java/com/skps9/packai/client/service/AskService.java").read_text(
            encoding="utf-8"
        )
        assert "buildDisplayCards" not in ask
        assert "Pack AI toolCards emission=" in ask
        assert "withRecipeCards(cardsOut, true)" in ask

    print("check_ask_marker_repair OK")


if __name__ == "__main__":
    main()
