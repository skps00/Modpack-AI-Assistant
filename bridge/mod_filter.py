"""Prune work by loaded mod list — skip scanners / paths for mods that are not installed."""

from __future__ import annotations

import re
from pathlib import Path

# Loader / API noise — never use these to focus web search
NOISE_MOD_IDS = frozenset({
    "minecraft",
    "java",
    "neoforge",
    "forge",
    "fml",
    "mcp",
    "fabricloader",
    "fabric-api",
    "fabric",
    "quilt_loader",
    "mixinextras",
    "packai",
})

# Scanner id -> any of these mod ids must be present to enable the scanner
SCANNER_REQUIREMENTS: dict[str, frozenset[str]] = {
    "kubejs": frozenset({"kubejs"}),
    "crafttweaker": frozenset({"crafttweaker"}),
    "groovyscript": frozenset({"groovyscript"}),
    "ftbquests": frozenset({"ftbquests", "ftb_quests"}),
    "heracles": frozenset({"heracles"}),
    "patchouli": frozenset({"patchouli"}),
    "openloader": frozenset({"openloader"}),
    "datapacks": frozenset(),  # always allowed (vanilla mechanism)
    "config": frozenset(),  # always allowed but filtered by mod id in path/content
}

_TOKEN_RE = re.compile(r"[a-z0-9_.:/-]+", re.I)


def active_scanners(mod_ids: list[str]) -> list[str]:
    """Return scanner names that should run for this mod list."""
    present = {m.lower() for m in mod_ids}
    out: list[str] = []
    for name, need in SCANNER_REQUIREMENTS.items():
        if not need or present.intersection(need):
            out.append(name)
    return out


def derive_focus_mods(
    mod_ids: list[str],
    held_item: dict | None,
    question: str = "",
) -> list[str]:
    """Mods that should drive path filtering for this question."""
    present = {m.lower() for m in mod_ids} - NOISE_MOD_IDS
    focus: list[str] = []

    held = held_item or {}
    item_id = str(held.get("id") or "")
    if ":" in item_id:
        ns = item_id.split(":", 1)[0].lower()
        if ns in present and ns not in focus:
            focus.append(ns)

    q_lower = question.lower()
    for mid in sorted(present, key=len, reverse=True):
        if mid in q_lower and mid not in focus:
            focus.append(mid)
        if len(focus) >= 6:
            break

    if not focus:
        focus = search_query_mods(mod_ids, held)[:4]
    return focus


def path_matches_focus(path_lower: str, focus_mods: list[str]) -> bool:
    """True if path is related to a focus mod or is a small pack meta file."""
    if _looks_pack_meta(path_lower):
        return True
    for mid in focus_mods:
        if f"/{mid}/" in path_lower or f"/{mid}." in path_lower:
            return True
        if mid in Path(path_lower).name:
            return True
        # kubejs/scripts often name files after mods
        if mid in path_lower:
            return True
    return False


def filter_paths_by_focus(paths: list[Path], focus_mods: list[str]) -> list[Path]:
    """Keep paths matching focus mods (or pack meta). No keep-all-by-size."""
    if not focus_mods:
        return [p for p in paths if _looks_pack_tree(p.as_posix().lower())]
    kept: list[Path] = []
    for p in paths:
        if path_matches_focus(p.as_posix().lower(), focus_mods):
            kept.append(p)
    if not kept:
        # Fallback: pack trees so we still have something to search
        kept = [p for p in paths if _looks_pack_tree(p.as_posix().lower())]
    return kept


def filter_paths_by_mod_ids(
    paths: list[Path],
    mod_ids: list[str],
    text_by_path: dict[Path, str] | None = None,
) -> list[Path]:
    """
    Legacy helper: keep pack trees or path/content mentioning installed content mods.
    Prefer filter_paths_by_focus for retrieval.
    """
    present = {m.lower() for m in mod_ids}
    content_mods = present - NOISE_MOD_IDS
    if not content_mods:
        return [p for p in paths if _looks_pack_tree(p.as_posix().lower())]

    kept: list[Path] = []
    for p in paths:
        lower_name = p.as_posix().lower()
        if path_matches_focus(lower_name, list(content_mods)[:12]) or _looks_pack_tree(lower_name):
            kept.append(p)
            continue
        text = (text_by_path or {}).get(p, "")
        if text and any(m in text.lower() for m in content_mods):
            kept.append(p)
    return kept


def _looks_pack_meta(path_lower: str) -> bool:
    return any(s in path_lower for s in ("readme", "changelog", "/overrides/"))


def _looks_pack_tree(path_lower: str) -> bool:
    return any(
        s in path_lower
        for s in (
            "/kubejs/",
            "/scripts/",
            "/groovy/",
            "/overrides/",
            "/openloader/",
            "/datapacks/",
            "/global_packs/",
            "readme",
            "changelog",
            "ftbquests",
            "heracles",
            "patchouli",
        )
    )


def search_query_mods(mod_ids: list[str], held_item: dict | None) -> list[str]:
    """Small set of mod ids to append to web search / LLM focus."""
    focus: list[str] = []
    held = held_item or {}
    item_id = str(held.get("id") or "")
    if ":" in item_id:
        ns = item_id.split(":", 1)[0].lower()
        if ns not in NOISE_MOD_IDS:
            focus.append(ns)
    for mid in mod_ids:
        m = mid.lower()
        if m in NOISE_MOD_IDS or m in focus:
            continue
        focus.append(m)
        if len(focus) >= 8:
            break
    return focus


def tokenize_query(question: str, held_item: dict | None = None) -> list[str]:
    """Tokens used for inverted-index lookup and scoring."""
    tokens: list[str] = []
    for m in _TOKEN_RE.findall(question.lower()):
        if len(m) > 2:
            tokens.append(m)
        if ":" in m:
            ns, rest = m.split(":", 1)
            if ns:
                tokens.append(ns)
            if rest:
                tokens.append(rest)
    held = held_item or {}
    item_id = str(held.get("id") or "").lower()
    if item_id and item_id != "empty":
        tokens.append(item_id)
        if ":" in item_id:
            tokens.append(item_id.split(":", 1)[0])
            tokens.append(item_id.split(":", 1)[1])
    # dedupe preserve order
    seen: set[str] = set()
    out: list[str] = []
    for t in tokens:
        if t not in seen:
            seen.add(t)
            out.append(t)
    return out


def path_tokens(rel_path: str) -> list[str]:
    """Tokens extracted from a relative path for the inverted index."""
    lower = rel_path.lower().replace("\\", "/")
    parts = re.split(r"[/._\-]+", lower)
    toks = [p for p in parts if len(p) > 1]
    # also whole segments between slashes
    for seg in lower.split("/"):
        if len(seg) > 1:
            toks.append(seg)
        if ":" in seg:
            toks.extend(seg.split(":"))
    seen: set[str] = set()
    out: list[str] = []
    for t in toks:
        if t not in seen and t not in {".", ".."}:
            seen.add(t)
            out.append(t)
    return out
