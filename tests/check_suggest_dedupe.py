# Mirror ItemResolver.addSuggestionRef / extractIds dedupe (no MC registry).
# Run: python tests/check_suggest_dedupe.py

from __future__ import annotations

import re
from collections import OrderedDict

MARKER = re.compile(r"<!--\s*packai:items=([^>]+)\s*-->", re.I)
ID = re.compile(r"\b([a-z0-9_]+:[a-z0-9_./-]+)\b", re.I)


def id_part(ref: str) -> str:
    bar = ref.find("|")
    return (ref if bar < 0 else ref[:bar]).strip().lower()


def name_part(ref: str | None) -> str | None:
    if ref is None:
        return None
    bar = ref.find("|")
    if bar < 0 or bar >= len(ref) - 1:
        return None
    name = ref[bar + 1 :].strip()
    return name or None


def add_suggestion_ref(refs: OrderedDict[str, str], ref: str | None) -> None:
    if not ref or not ref.strip():
        return
    iid = id_part(ref)
    if not iid:
        return
    name = name_part(ref)
    if name is not None:
        refs.pop(iid, None)
        key = f"{iid}|{name}"
        refs.setdefault(key, key)
        return
    for existing in refs.values():
        if iid == id_part(existing):
            return
    refs[iid] = iid


def extract_ids(answer: str | None, *, registry: set[str] | None = None) -> list[str]:
    """registry=None accepts any well-formed id (unit mirror)."""
    refs: OrderedDict[str, str] = OrderedDict()
    if answer is None:
        return []
    mm = MARKER.search(answer)
    if mm:
        for part in re.split(r"[,;]+", mm.group(1)):
            part = part.strip()
            if not part:
                continue
            bar = part.find("|")
            iid = (part if bar < 0 else part[:bar]).strip().lower()
            name = None if bar < 0 else part[bar + 1 :].strip()
            if ":" not in iid or " " in iid:
                continue
            if registry is not None and iid not in registry:
                continue
            ref = iid if not name else f"{iid}|{name}"
            add_suggestion_ref(refs, ref)
    prose = MARKER.sub(" ", answer)
    for m in ID.finditer(prose):
        cand = m.group(1).lower()
        if ":" not in cand or " " in cand or "|" in cand:
            continue
        if registry is not None and cand not in registry:
            continue
        add_suggestion_ref(refs, cand)
    return list(refs.values())


def main() -> None:
    # Bug repro: named marker + self-scan used to yield id|name AND bare id.
    sword = "ino_dlc_build:full_bottle_buster_sword"
    raw = f"說明<!--packai:items={sword}|Full Bottle Buster Sword-->"
    out = extract_ids(raw)
    assert out == [f"{sword}|Full Bottle Buster Sword"], out

    # Named + explicit bare in prose → still one.
    raw2 = f"{raw}\nalso {sword}"
    assert extract_ids(raw2) == [f"{sword}|Full Bottle Buster Sword"]

    # Distinct named variants kept.
    a = "mod:blade|Alpha"
    b = "mod:blade|Beta"
    assert extract_ids(f"<!--packai:items={a},{b}-->") == [a, b]

    # Bare alone ok.
    assert extract_ids("need minecraft:dirt") == ["minecraft:dirt"]

    print("check_suggest_dedupe OK")


if __name__ == "__main__":
    main()
