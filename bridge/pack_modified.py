"""Detect whether pack scripts/datapacks touched the focused mod/item for this question."""

from __future__ import annotations

from dataclasses import dataclass, field

SCRIPT_SCANNERS = frozenset({"kubejs", "crafttweaker", "groovyscript"})
OVERRIDE_PREFIXES = (
    "overrides/",
    "openloader/",
    "datapacks/",
    "global_packs/",
)
MOD_KEYWORDS = (
    "remove(",
    "replaceinput",
    "replaceoutput",
    "event.shaped",
    "event.shapeless",
    "event.recipes",
    "addjsonrecipe",
    "event.custom",
)


@dataclass
class ModificationVerdict:
    pack_touched: bool
    reasons: list[str] = field(default_factory=list)
    policy: str = "online_ok"  # local_only | online_ok


def detect_modification(
    scanners: list[str],
    paths: list[str] | None,
    focus_mods: list[str],
    held_item: dict | None,
    snippets: list[str] | None,
) -> ModificationVerdict:
    """
    If pack likely modified content related to this question -> local_only.
    Otherwise common/unmodified mod knowledge -> online_ok.
    """
    reasons: list[str] = []
    focus = [m.lower() for m in focus_mods]
    held = held_item or {}
    item_id = str(held.get("id") or "").lower()
    item_ns = item_id.split(":", 1)[0] if ":" in item_id else ""
    needles = list(dict.fromkeys([*focus, item_ns, item_id] if item_id else focus))
    needles = [n for n in needles if n and n not in {"empty", "true", "false"}]

    path_list = paths or []
    script_on = bool(SCRIPT_SCANNERS.intersection(scanners))

    # 1) Script scanner + path mentions focus mod / held item
    if script_on and needles:
        for rel in path_list:
            pl = rel.lower().replace("\\", "/")
            if not any(pl.startswith(p) for p in ("kubejs/", "scripts/", "groovy/")):
                continue
            if any(n in pl for n in needles):
                reasons.append(f"script_path_mentions_focus:{rel}")
                break

    # 2) Override / datapack trees contain focus namespace
    if needles:
        for rel in path_list:
            pl = rel.lower().replace("\\", "/")
            if not any(pl.startswith(pref) for pref in OVERRIDE_PREFIXES):
                continue
            if any(f"/{n}/" in f"/{pl}" or f"/{n}." in f"/{pl}" or n in pl for n in needles):
                reasons.append(f"override_or_datapack:{rel}")
                break

    # 3) Retrieved snippets look like pack recipe edits mentioning focus
    for snip in snippets or []:
        lower = snip.lower()
        if not any(k in lower for k in MOD_KEYWORDS):
            continue
        if needles and any(n in lower for n in needles):
            reasons.append("snippet_recipe_edit_mentions_focus")
            break

    pack_touched = bool(reasons)
    policy = "local_only" if pack_touched else "online_ok"
    return ModificationVerdict(pack_touched=pack_touched, reasons=reasons, policy=policy)
