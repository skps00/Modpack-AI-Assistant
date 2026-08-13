#!/usr/bin/env python3
"""ItemVariantKeysText + nested schematic allowlist walk + JeiFocusMatch OUTPUT policy."""

from __future__ import annotations

MAX_DEPTH = 4
MAX_SCHEMATICS = 16
MAX_LIST_SCAN = 8
NEST_COMPOUNDS = ("BlockEntityTag", "tag")
NEST_LISTS = ("data", "s", "schematics", "Schematics")


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


def _accept_key(v: str) -> bool:
    if not v or not str(v).strip():
        return False
    s = str(v).strip()
    if len(s) < 2 or len(s) > 64:
        return False
    if ":" in s or "/" in s:
        return True
    # Bare ids: underscore (energy_bottle) or short pack keys (terra); min len 3
    if len(s) < 3:
        return False
    for c in s:
        if not (c.isalnum() or c in "_.-"):
            return False
    return True


def scroll_lang_path(raw: str) -> str:
    if not raw or not str(raw).strip():
        return ""
    id_ = str(raw).strip()
    return id_.split(":", 1)[-1]


def scroll_mechanics_purpose_lines(translate) -> str:
    lines: list[str] = []
    seen: set[str] = set()
    pin = translate("packai.reply.tetra_scroll_mech")
    if pin and pin != "packai.reply.tetra_scroll_mech":
        lines.append(pin.strip())
        seen.add(pin.strip())
    for lang_key in (
        "item.tetra.scroll.schematics.description",
        "item.tetra.scroll.range.description",
        "item.tetra.scroll.effects.description",
        "item.tetra.scroll.intricate.description",
    ):
        t = translate(lang_key)
        if not t or t == lang_key:
            continue
        plain = t.strip()
        if plain and plain not in seen:
            seen.add(plain)
            lines.append(plain)
    if not lines:
        return ""
    return "[SCROLL_MECH]\n" + "\n".join(lines)


def tooltip_hints_tetra_scroll(tip: str | None) -> bool:
    if not tip or not tip.strip():
        return False
    t = tip.lower()
    if "5x5x5" in t or "5×5×5" in tip:
        return True
    if "nearby workbench" in t or "near a workbench" in t:
        return True
    if any(x in tip for x in ("附近工作台", "解锁附近", "解鎖附近", "图纸", "圖紙", "示意圖", "示意图")):
        return True
    if "schematic" in t and ("unlock" in t or "workbench" in t):
        return True
    return False


def scroll_effect_purpose_lines(variant_ids: list[str], translate) -> str:
    lines: list[str] = []
    seen: set[str] = set()
    for raw in variant_ids or []:
        path = scroll_lang_path(raw)
        if not path:
            continue
        for suf in (".description", ".description_extended"):
            lang_key = "item.tetra.scroll." + path + suf
            t = translate(lang_key)
            if not t or t == lang_key:
                continue
            plain = t.strip()
            low = plain.lower()
            if "shift" in low and ("read more" in low or "rmb" in low) and len(plain) < 48:
                continue
            if plain not in seen:
                seen.add(plain)
                lines.append(plain)
    if not lines:
        return ""
    return "[SCROLL_EFFECT]\n" + "\n".join(lines)


def _collect_at_compound(tag: dict, out: list[str]) -> None:
    for list_key in ("s", "schematics", "Schematics", "craftingEffects", "crafting_effects"):
        raw = tag.get(list_key)
        if not isinstance(raw, list):
            continue
        for el in raw[:MAX_LIST_SCAN]:
            if len(out) >= MAX_SCHEMATICS:
                return
            if isinstance(el, str) and el.strip():
                out.append(el.strip())
            elif isinstance(el, dict):
                for k in ("id", "schematic", "key", "name"):
                    v = el.get(k)
                    if isinstance(v, str) and v.strip():
                        if k == "key" and not _accept_key(v):
                            continue
                        out.append(v.strip())
                        break
    one = tag.get("schematic")
    if isinstance(one, str) and one.strip() and len(out) < MAX_SCHEMATICS:
        out.append(one.strip())
    one = tag.get("key")
    if isinstance(one, str) and _accept_key(one) and len(out) < MAX_SCHEMATICS:
        out.append(one.strip())


def schematics_from_tag(tag: dict | None, depth: int = 0) -> list[str]:
    """Mirror ItemVariantKeys.schematicsFromTag allowlisted walk (D8)."""
    out: list[str] = []
    seen: set[str] = set()

    def add(v: str) -> None:
        if v and v not in seen and len(out) < MAX_SCHEMATICS:
            seen.add(v)
            out.append(v)

    def walk(node: dict, d: int) -> None:
        if not isinstance(node, dict) or d > MAX_DEPTH or len(out) >= MAX_SCHEMATICS:
            return
        buf: list[str] = []
        _collect_at_compound(node, buf)
        for v in buf:
            add(v)
        if len(out) >= MAX_SCHEMATICS:
            return
        for k in NEST_COMPOUNDS:
            child = node.get(k)
            if isinstance(child, dict):
                walk(child, d + 1)
                if len(out) >= MAX_SCHEMATICS:
                    return
        for k in NEST_LISTS:
            raw = node.get(k)
            if not isinstance(raw, list):
                continue
            for el in raw[:MAX_LIST_SCAN]:
                if len(out) >= MAX_SCHEMATICS:
                    return
                if isinstance(el, dict):
                    walk(el, d + 1)

    if tag:
        walk(tag, depth)
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

    # Flat root s: (legacy / JEI tooltip shape)
    flat = schematics_from_tag({"s": ["tetra:mirror"]})
    assert flat == ["tetra:mirror"]

    # Tetra sample: BlockEntityTag.data[].schematics + path-like key (no colon)
    nested = schematics_from_tag(
        {
            "BlockEntityTag": {
                "data": [
                    {
                        "key": "hone/gild_2",
                        "schematics": ["tetra:hone/gild_1", "tetra:hone/gild_2"],
                        "intricate": True,
                        "material": 2,
                    }
                ]
            }
        }
    )
    assert "hone/gild_2" in nested
    assert "tetra:hone/gild_1" in nested and "tetra:hone/gild_2" in nested
    nest_toks = expand_tokens(nested)
    assert "gild_2" in nest_toks and "hone/gild_2" in nest_toks

    # Treatise / pack bare keys (no colon/slash)
    fabric = schematics_from_tag(
        {"BlockEntityTag": {"data": [{"key": "fabric_expertise"}]}}
    )
    assert fabric == ["fabric_expertise"]
    energy = schematics_from_tag(
        {
            "BlockEntityTag": {
                "data": [
                    {
                        "key": "energy_bottle",
                        "schematics": ["tetra:energy_bottle"],
                    }
                ]
            }
        }
    )
    assert "energy_bottle" in energy and "tetra:energy_bottle" in energy
    effects = schematics_from_tag(
        {
            "BlockEntityTag": {
                "data": [
                    {
                        "key": "fabric_expertise",
                        "craftingEffects": ["tetra:treatise/expertise/fabric"],
                    }
                ]
            }
        }
    )
    assert "fabric_expertise" in effects
    assert "tetra:treatise/expertise/fabric" in effects
    assert not _accept_key("ab")
    assert not _accept_key("no underscore")
    assert _accept_key("fabric_expertise") and _accept_key("hone/gild_2")
    assert _accept_key("terra") and _accept_key("tetra:terra")

    langs = {
        "item.tetra.scroll.fabric_expertise.description": (
            "Major modules crafted from materials classified as fabric "
            "provide additional damage and efficiency"
        ),
        "item.tetra.scroll.hone/gild_2.description": "gild magic capacity",
        "item.tetra.scroll.warforge/hammer.description_extended": "§7[§8shift + rmb§7]§8 read more",
    }
    fx = scroll_effect_purpose_lines(
        ["fabric_expertise", "tetra:hone/gild_2", "warforge/hammer"],
        lambda k: langs.get(k, k),
    )
    assert fx.startswith("[SCROLL_EFFECT]")
    assert "fabric" in fx and "gild magic" in fx
    assert "read more" not in fx

    mech_langs = {
        "packai.reply.tetra_scroll_mech": "place near workbench — not RMB learn",
        "item.tetra.scroll.schematics.description": (
            "Unlocks additional crafting schematics for a nearby workbench"
        ),
        "item.tetra.scroll.range.description": (
            "The scroll has to be placed within a 5x5x5 area "
            "(centered two block above the workbench) to function"
        ),
        "item.tetra.scroll.effects.description": "nearby workbench effect",
        "item.tetra.scroll.intricate.description": "directly on top of a workbench",
    }
    mech = scroll_mechanics_purpose_lines(lambda k: mech_langs.get(k, k))
    assert mech.startswith("[SCROLL_MECH]")
    assert "not RMB learn" in mech and "5x5x5" in mech and "nearby workbench" in mech
    assert tooltip_hints_tetra_scroll("图纸\n解锁附近工作台的额外配方图纸\n5x5x5")
    assert tooltip_hints_tetra_scroll(
        "Schematic\nUnlocks additional crafting schematics for a nearby workbench"
    )
    assert not tooltip_hints_tetra_scroll("Iron Ingot\nminecraft:iron_ingot")

    # Non-allowlisted nest must not leak (Inventory.Items junk)
    junk = schematics_from_tag(
        {"Inventory": {"Items": [{"schematic": "should:not_appear"}]}}
    )
    assert junk == []

    # Cap list scan
    many = schematics_from_tag(
        {"s": [f"tetra:x{i}" for i in range(MAX_LIST_SCAN + 5)]}
    )
    assert len(many) == MAX_LIST_SCAN

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

    # Dual-tree source markers
    from pathlib import Path

    root = Path(__file__).resolve().parents[1]
    for rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/ItemVariantKeys.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/ItemVariantKeys.java",
    ):
        src = (root / rel).read_text(encoding="utf-8")
        assert "walkAllowlisted" in src
        assert "BlockEntityTag" in src
        assert "schematicTokens" in src
        assert "MAX_SCHEMATICS" in src
        assert "SCROLL_MECH" in src or "scrollMechanicsPurposeLines" in src
        assert '!"tetra".equals(id.getNamespace())' in src
        assert "ISB_Spells" in src
        text_rel = rel.replace("ItemVariantKeys.java", "ItemVariantKeysText.java")
        text_src = (root / text_rel).read_text(encoding="utf-8")
        assert "SCROLL_MECH_HEADER" in text_src
        assert "tooltipHintsTetraScroll" in text_src
        ask = (
            "forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java"
            if "forge" in rel
            else "neoforge/1.21.1/src/main/java/com/skps9/packai/client/service/AskService.java"
        )
        ask_src = (root / ask).read_text(encoding="utf-8")
        assert "scrollMechanicsPurposeLines" in ask_src

    print("check_item_variant_keys OK")


if __name__ == "__main__":
    main()
