"""Lightweight pack knowledge graph to shrink LLM prompts (codegraph-like, local)."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

from plainify import _SHAPED_RE, _SHAPELESS_RE, _REMOVE_RE, _clean_item, _ITEM_RE

_ITEM_ID_RE = re.compile(r"\b([a-z0-9_]+:[a-z0-9_./-]+)\b", re.I)


@dataclass
class PackGraph:
    """Adjacency: node -> list of (edge, neighbor)."""

    edges: dict[str, list[tuple[str, str]]] = field(default_factory=dict)
    removed_items: set[str] = field(default_factory=set)
    files_for: dict[str, set[str]] = field(default_factory=dict)  # node -> source files

    def add(self, a: str, edge: str, b: str, source: str = "") -> None:
        self.edges.setdefault(a, []).append((edge, b))
        if source:
            self.files_for.setdefault(a, set()).add(source)
            self.files_for.setdefault(b, set()).add(source)


_graph_cache: dict[str, PackGraph] = {}


def ensure_pack_graph(
    pack_root: Path | None,
    paths: list[str],
    cache_key: str,
    max_files: int = 400,
) -> PackGraph:
    if cache_key in _graph_cache:
        return _graph_cache[cache_key]
    g = PackGraph()
    if not pack_root or not pack_root.exists():
        _graph_cache[cache_key] = g
        return g

    count = 0
    for rel in paths:
        if count >= max_files:
            break
        lower = rel.lower().replace("\\", "/")
        if not any(
            x in lower
            for x in ("kubejs/", "scripts/", "groovy/", "datapacks/", "overrides/", "ftbquests", "heracles")
        ):
            continue
        path = pack_root / rel
        if not path.is_file() or path.stat().st_size > 200_000:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        count += 1
        _ingest_text(g, text, rel)

    _graph_cache[cache_key] = g
    return g


def subgraph_facts(
    graph: PackGraph,
    held_item: dict | None,
    question: str,
    focus_mods: list[str],
    max_nodes: int = 20,
) -> tuple[list[str], list[str]]:
    """Return (fact lines, source files) for LLM — compact."""
    seeds: list[str] = []
    held = held_item or {}
    item_id = str(held.get("id") or "").lower()
    if item_id and ":" in item_id:
        seeds.append(f"item:{item_id}")
    for mid in focus_mods[:4]:
        seeds.append(f"mod:{mid}")
    for m in _ITEM_ID_RE.findall(question or ""):
        seeds.append(f"item:{m.lower()}")

    visited: set[str] = set()
    facts: list[str] = []
    sources: set[str] = set()
    queue = list(seeds)

    while queue and len(visited) < max_nodes:
        node = queue.pop(0)
        if node in visited:
            continue
        visited.add(node)
        for src in graph.files_for.get(node, ()):
            sources.add(src)
        for edge, neigh in graph.edges.get(node, []):
            facts.append(f"{node} -[{edge}]-> {neigh}")
            if neigh not in visited and len(visited) + len(queue) < max_nodes * 2:
                queue.append(neigh)

    # also surface removals related to seeds
    for seed in seeds:
        if seed.startswith("item:"):
            iid = seed[5:]
            if iid in graph.removed_items:
                facts.append(f"item:{iid} -[removed]-> true")

    return facts[:40], sorted(sources)[:8]


def _ingest_text(g: PackGraph, text: str, rel: str) -> None:
    for m in _REMOVE_RE.finditer(text):
        inner = m.group(1)
        for item in _ITEM_RE.findall(inner):
            clean = item.lower()
            g.removed_items.add(clean)
            g.add(f"item:{clean}", "removed", "true", rel)

    for m in _SHAPED_RE.finditer(text):
        out = _clean_item(m.group("out")).lower()
        for item in _ITEM_RE.findall(m.group("keys") + m.group("pattern")):
            need = item.lower()
            g.add(f"item:{out}", "recipe_needs", f"item:{need}", rel)
            g.add(f"item:{need}", "recipe_makes", f"item:{out}", rel)

    for m in _SHAPELESS_RE.finditer(text):
        out = _clean_item(m.group("out")).lower()
        for item in _ITEM_RE.findall(m.group("ings")):
            need = item.lower()
            g.add(f"item:{out}", "recipe_needs", f"item:{need}", rel)
            g.add(f"item:{need}", "recipe_makes", f"item:{out}", rel)

    # weak item mentions in quest-like paths
    if "ftbquests" in rel.lower() or "heracles" in rel.lower():
        for item in _ITEM_ID_RE.findall(text):
            g.add(f"quest_file:{rel}", "quest_mentions_item", f"item:{item.lower()}", rel)
