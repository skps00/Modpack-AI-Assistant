#!/usr/bin/env python3
"""ItemVariantKeysText + JeiFocusMatch distinctive-name OUTPUT policy."""

from __future__ import annotations


def purpose_line(schematics: list[str]) -> str:
    clean = [s.strip() for s in schematics if s and s.strip()]
    if not clean:
        return ""
    return "[VARIANT] schematic: " + ", ".join(clean)


def expand_tokens(schematics: list[str]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for raw in schematics:
        if not raw or not raw.strip():
            continue
        id_ = raw.strip().lower()
        for t in (id_, id_.split(":", 1)[-1], id_.split(":")[-1].split("/")[-1]):
            if t and t not in seen:
                seen.add(t)
                out.append(t)
    return out


def mentions_any(blob: str, items: list[str], tokens: list[str]) -> bool:
    if not tokens:
        return True
    b = (blob or "").lower()
    its = [i.lower() for i in items if i]
    for tok in tokens:
        t = tok.strip().lower()
        if len(t) < 2:
            continue
        if t in b:
            return True
        if any(t in it for it in its):
            return True
    return False


def prefer(hits: list[str], tokens: list[str], blobs: dict[str, str]) -> list[str]:
    if not tokens or not hits:
        return hits
    ok = [h for h in hits if mentions_any(blobs[h], [], tokens)]
    return ok if ok else hits


def output_matches_focus(
    output_item: str,
    output_nbt: str | None,
    output_name: str,
    focus_item: str,
    focus_nbt: str | None,
    focus_name: str,
    *,
    focus_schematics: list[str] | None = None,
    output_schematics: list[str] | None = None,
) -> bool:
    """Mirror JeiFocusMatch: same tags OR name/variant signal; VARIANT rejects sibling names."""
    if output_item == focus_item and (output_nbt or "") == (focus_nbt or ""):
        return True
    name_useful = bool(focus_name) and focus_name.lower() not in {
        focus_item.lower(),
        focus_item.split(":", 1)[-1].lower(),
        "item",
    }
    prefer = expand_tokens(focus_schematics or [])
    out_blob = " ".join(
        [output_name or ""] + list(output_schematics or [])
    ).lower()
    mentions = any(t in out_blob for t in prefer if len(t) >= 2)
    if not prefer and focus_name:
        mentions = focus_name.lower() in out_blob
    out_useful = bool(output_name) and output_name.lower() not in {
        focus_item.lower(),
        focus_item.split(":", 1)[-1].lower(),
        "item",
    }
    if (
        output_item == focus_item
        and name_useful
        and out_useful
        and focus_name != output_name
        and not mentions
    ):
        return False
    has_variant = bool(focus_schematics)
    if output_item != focus_item:
        # Hard: never match other mods by display name alone.
        return False
    if has_variant:
        return focus_name == output_name or mentions
    if not name_useful or focus_name == output_name:
        return True
    return False


def main() -> None:
    assert purpose_line(["tetra:mirror"]) == "[VARIANT] schematic: tetra:mirror"
    toks = expand_tokens(["tetra:mirror"])
    assert "tetra:mirror" in toks and "mirror" in toks

    hits = ["energy", "mirror", "sheath"]
    blobs = {
        "energy": "unlock energy bottle schematic",
        "mirror": "unlock mirror pose recipes",
        "sheath": "sword sheath blueprint",
    }
    assert prefer(hits, toks, blobs) == ["mirror"]

    # Soft: none mention → keep all
    assert prefer(hits, ["zzz_nope"], blobs) == hits

    # Surgery Box: generic name → same item OK despite NBT
    assert output_matches_focus(
        "mod:surgery_box", "{a:1}", "Surgery Box",
        "mod:surgery_box", "{a:2}", "Surgery Box",
    )
    # Tetra scrolls: distinctive names must not cross-match
    assert not output_matches_focus(
        "tetra:scroll_rolled", "{s:[tetra:energy_bottle]}", "Energy Bottle Scroll",
        "tetra:scroll_rolled", "{s:[tetra:mirror]}", "Mirror Scroll",
        focus_schematics=["tetra:mirror"],
        output_schematics=["tetra:energy_bottle"],
    )
    assert output_matches_focus(
        "tetra:scroll_rolled", "{s:[tetra:mirror]}", "Mirror Scroll",
        "tetra:scroll_rolled", "{s:[tetra:mirror]}", "Mirror Scroll",
        focus_schematics=["tetra:mirror"],
        output_schematics=["tetra:mirror"],
    )
    assert output_matches_focus(
        "tetra:scroll_rolled", None, "Mirror Scroll",
        "tetra:scroll_rolled", "{s:[tetra:mirror]}", "Mirror Scroll",
        focus_schematics=["tetra:mirror"],
    )
    # VARIANT + generic focus name: bare same-id rejected without token
    assert not output_matches_focus(
        "tetra:scroll_rolled", "{s:[energy]}", "Scroll",
        "tetra:scroll_rolled", "{s:[mirror]}", "scroll_rolled",
        focus_schematics=["tetra:mirror"],
        output_schematics=["tetra:energy_bottle"],
    )
    # Cross-mod same localized name must not match
    assert not output_matches_focus(
        "othermod:blue_wrench", None, "扳手",
        "create:wrench", None, "扳手",
    )
    print("check_item_variant_keys OK")


if __name__ == "__main__":
    main()
