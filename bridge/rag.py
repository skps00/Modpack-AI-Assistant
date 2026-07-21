"""Path-first pack indexer with inverted index — read file bodies only for candidates."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from mod_filter import (
    filter_paths_by_focus,
    path_tokens,
    tokenize_query,
)

TEXT_EXTS = {".js", ".zs", ".groovy", ".json", ".snbt", ".txt", ".md", ".toml", ".cfg", ".properties"}
MAX_CANDIDATES = 40
MAX_SNIPPETS = 3
SNIPPET_CHARS = 600
HIGH_CONFIDENCE_SCORE = 12
RECIPE_HINTS = ("event.shaped", "event.shapeless", "event.recipes", '"type":', "remove(", "addJsonRecipe")


@dataclass
class PathIndex:
    """In-memory path catalog + inverted token -> path indices."""

    pack_root: Path
    paths: list[str] = field(default_factory=list)  # relative posix paths
    inverted: dict[str, list[int]] = field(default_factory=dict)

    def add_path(self, rel: str) -> None:
        idx = len(self.paths)
        self.paths.append(rel)
        for tok in path_tokens(rel):
            self.inverted.setdefault(tok, []).append(idx)


@dataclass
class RetrieveHit:
    path: str
    text: str
    score: int


class PackIndex:
    def __init__(self) -> None:
        self._cache: dict[str, PathIndex] = {}
        self._text_cache: dict[tuple[str, str], str] = {}  # (cache_key, rel) -> text

    def ensure_index(
        self,
        pack_root: Path | None,
        mod_ids: list[str],
        scanners: list[str],
        cache_key: str,
    ) -> PathIndex | None:
        if not pack_root or not pack_root.exists():
            return None
        hit = self._cache.get(cache_key)
        if hit is not None:
            return hit
        idx = self._build_path_index(pack_root, scanners)
        self._cache[cache_key] = idx
        return idx

    def retrieve(
        self,
        question: str,
        pack_root: Path | None,
        mod_ids: list[str],
        scanners: list[str],
        cache_key: str,
        held_item: dict,
        focus_mods: list[str],
    ) -> tuple[list[str], list[str], int, bool]:
        """Returns (snippets, sources, top_score, high_confidence)."""
        idx = self.ensure_index(pack_root, mod_ids, scanners, cache_key)
        if idx is None:
            return [], [], 0, False

        # Narrow path set by focus before scoring
        abs_all = [idx.pack_root / rel for rel in idx.paths]
        focused = filter_paths_by_focus(abs_all, focus_mods)
        focused_rels = {p.relative_to(idx.pack_root).as_posix() for p in focused}
        # Map rel -> original index
        rel_to_i = {rel: i for i, rel in enumerate(idx.paths)}

        tokens = tokenize_query(question, held_item)
        candidate_ids = self._candidate_ids(idx, tokens, focus_mods)
        if focused_rels:
            candidate_ids = [i for i in candidate_ids if idx.paths[i] in focused_rels]
            if not candidate_ids:
                candidate_ids = [rel_to_i[r] for r in focused_rels if r in rel_to_i]

        scored_paths: list[tuple[int, int]] = []
        for i in candidate_ids:
            rel = idx.paths[i]
            pl = rel.lower()
            score = 0
            for t in tokens:
                if t in pl:
                    score += 2
            for mid in focus_mods:
                if f"/{mid}/" in pl or mid in Path(pl).name:
                    score += 3
            if score > 0 or not tokens:
                scored_paths.append((score, i))

        scored_paths.sort(key=lambda x: -x[0])
        top_ids = [i for _, i in scored_paths[:MAX_CANDIDATES]]
        if not top_ids and focused_rels:
            top_ids = [rel_to_i[r] for r in list(focused_rels)[:20] if r in rel_to_i]

        hits: list[RetrieveHit] = []
        for i in top_ids:
            rel = idx.paths[i]
            text = self._read(cache_key, idx.pack_root, rel)
            if text is None:
                continue
            lower = text.lower()
            score = 0
            for t in tokens:
                if t in lower:
                    score += 3
                if t in rel.lower():
                    score += 2
            for mid in focus_mods:
                if mid in lower or mid in rel.lower():
                    score += 2
            if score > 0 or not tokens:
                hits.append(RetrieveHit(path=rel, text=text, score=score))

        hits.sort(key=lambda h: -h.score)
        top = hits[:MAX_SNIPPETS]
        snippets = [f"// file: {h.path}\n{h.text[:SNIPPET_CHARS]}" for h in top]
        sources = [h.path for h in top]
        top_score = top[0].score if top else 0
        high = self._is_high_confidence(top)
        return snippets, sources, top_score, high

    def _is_high_confidence(self, top: list[RetrieveHit]) -> bool:
        if not top:
            return False
        best = top[0]
        if best.score < HIGH_CONFIDENCE_SCORE:
            return False
        lower = best.text.lower()
        return any(h in lower for h in RECIPE_HINTS) or best.score >= HIGH_CONFIDENCE_SCORE + 6

    def _candidate_ids(self, idx: PathIndex, tokens: list[str], focus_mods: list[str]) -> list[int]:
        ids: set[int] = set()
        for t in tokens:
            for i in idx.inverted.get(t, []):
                ids.add(i)
        for mid in focus_mods:
            for i in idx.inverted.get(mid, []):
                ids.add(i)
        if ids:
            return list(ids)
        return list(range(len(idx.paths)))

    def _build_path_index(self, pack_root: Path, scanners: list[str]) -> PathIndex:
        roots: list[Path] = []
        if "kubejs" in scanners:
            roots.append(pack_root / "kubejs")
        if "crafttweaker" in scanners:
            roots.append(pack_root / "scripts")
        if "groovyscript" in scanners:
            roots.append(pack_root / "groovy")
        if "openloader" in scanners:
            roots.append(pack_root / "openloader")
        if "datapacks" in scanners:
            roots.append(pack_root / "datapacks")
            roots.append(pack_root / "global_packs")
        if "ftbquests" in scanners:
            roots.append(pack_root / "config" / "ftbquests")
        if "heracles" in scanners:
            roots.append(pack_root / "config" / "heracles")
        if "patchouli" in scanners:
            roots.append(pack_root / "patchouli_books")
        roots.append(pack_root / "overrides")

        raw: list[Path] = []
        for root in roots:
            if not root.exists():
                continue
            for p in root.rglob("*"):
                if p.is_file() and p.suffix.lower() in TEXT_EXTS and p.stat().st_size < 400_000:
                    raw.append(p)

        idx = PathIndex(pack_root=pack_root)
        for p in raw:
            try:
                rel = p.relative_to(pack_root).as_posix()
            except ValueError:
                continue
            idx.add_path(rel)
        return idx

    def paths_for_focus(self, cache_key: str, focus_mods: list[str]) -> list[str]:
        """Test helper: relative paths matching focus after filter."""
        idx = self._cache.get(cache_key)
        if idx is None:
            return []
        abs_paths = [idx.pack_root / rel for rel in idx.paths]
        filtered = filter_paths_by_focus(abs_paths, focus_mods)
        return [p.relative_to(idx.pack_root).as_posix() for p in filtered]

    def _read(self, cache_key: str, pack_root: Path, rel: str) -> str | None:
        key = (cache_key, rel)
        if key in self._text_cache:
            return self._text_cache[key]
        path = pack_root / rel
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            return None
        self._text_cache[key] = text
        return text


def format_local_answer(question: str, snippets: list[str], sources: list[str]) -> str:
    """Prefer plain Chinese; never dump raw code as the main answer."""
    from plainify import friendly_offline, plainify_snippets

    plain = plainify_snippets(snippets, sources)
    if plain:
        return plain
    return friendly_offline(sources, question)
