"""Focus/extras rules for inventory multi-select ask."""

from __future__ import annotations


def resolve_focus(jei_id: str | None, selected: list[str]) -> str | None:
    if jei_id:
        return jei_id
    for s in selected:
        if s:
            return s
    return None


def extras_for(focus: str | None, selected: list[str]) -> list[str]:
    fid = (focus or "").lower()
    out: list[str] = []
    seen: set[str] = set()
    for s in selected:
        if not s:
            continue
        sid = s.lower()
        if sid == fid or sid in seen:
            continue
        seen.add(sid)
        out.append(s)
    return out


def main() -> None:
    assert resolve_focus(None, []) is None
    assert resolve_focus(None, ["mod:a", "mod:b"]) == "mod:a"
    assert resolve_focus("mod:jei", ["mod:a"]) == "mod:jei"
    assert extras_for("mod:a", ["mod:a", "mod:b", "mod:b"]) == ["mod:b"]
    assert extras_for("mod:jei", ["mod:a", "mod:b"]) == ["mod:a", "mod:b"]
    print("check_inv_pick_focus: OK")


if __name__ == "__main__":
    main()
