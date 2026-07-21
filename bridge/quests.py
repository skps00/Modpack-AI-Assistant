"""FTB Quests / Heracles local quest index, matching, conflict, override detection."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

MAX_QUEST_HITS = 3

_OVERRIDE_RE = re.compile(
    r"(任務書?\s*(好像)?(不對|錯了|有誤|過時)|quest\s*wrong|quest\s*outdated|wrong\s*quest)",
    re.I,
)
_ITEM_ID_RE = re.compile(r"\b([a-z0-9_]+:[a-z0-9_./-]+)\b", re.I)
_TITLE_RE = re.compile(r'(?:title|Title)\s*:\s*"([^"]+)"')
_DESC_RE = re.compile(r'(?:description|Description)\s*:\s*"([^"]+)"')
# FTB snbt often uses translate keys; also catch plain id strings nearby
_CHAPTER_HINT = re.compile(r"chapter|Chapter|章", re.I)


@dataclass
class QuestHit:
    chapter: str
    title: str
    description: str
    source: str
    items: list[str] = field(default_factory=list)
    score: int = 0
    active: bool = False


@dataclass
class QuestIndex:
    hits: list[QuestHit] = field(default_factory=list)
    # item id -> quest indices that mention it
    by_item: dict[str, list[int]] = field(default_factory=dict)


_quest_cache: dict[str, QuestIndex] = {}


def is_quest_override(question: str, context: dict | None = None) -> bool:
    """True only for explicit 'quest is wrong' — stuck alone is NOT override."""
    ctx = context or {}
    if ctx.get("questOverride") is True:
        return True
    return bool(_OVERRIDE_RE.search(question or ""))


def ensure_quest_index(pack_root: Path | None, scanners: list[str], cache_key: str) -> QuestIndex:
    if cache_key in _quest_cache:
        return _quest_cache[cache_key]
    idx = QuestIndex()
    if not pack_root or not pack_root.exists():
        _quest_cache[cache_key] = idx
        return idx

    roots: list[Path] = []
    if "ftbquests" in scanners:
        roots.append(pack_root / "config" / "ftbquests")
    if "heracles" in scanners:
        roots.append(pack_root / "config" / "heracles")

    for root in roots:
        if not root.exists():
            continue
        for p in root.rglob("*"):
            if not p.is_file():
                continue
            if p.suffix.lower() not in {".snbt", ".nbt", ".json", ".txt", ".md"}:
                continue
            if p.stat().st_size > 500_000:
                continue
            try:
                text = p.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            for hit in _parse_quest_file(p, text, pack_root):
                i = len(idx.hits)
                idx.hits.append(hit)
                for item in hit.items:
                    idx.by_item.setdefault(item.lower(), []).append(i)

    _quest_cache[cache_key] = idx
    return idx


def match_quests(
    question: str,
    held_item: dict | None,
    quest_index: QuestIndex,
    active_quest_ids: list[str] | None = None,
) -> list[QuestHit]:
    held = held_item or {}
    item_id = str(held.get("id") or "").lower()
    q = (question or "").lower()
    tokens = [t for t in re.findall(r"[a-z0-9_\u4e00-\u9fff]{2,}", q) if t not in {"任務", "怎麼", "如何", "什麼"}]
    active = {a.lower() for a in (active_quest_ids or [])}

    scored: list[QuestHit] = []
    for hit in quest_index.hits:
        score = 0
        blob = f"{hit.chapter} {hit.title} {hit.description}".lower()
        if item_id and item_id in hit.items:
            score += 10
        if item_id and item_id in blob:
            score += 6
        for t in tokens:
            if t in blob:
                score += 2
        if hit.title.lower() in active or hit.source.lower() in active:
            score += 8
            hit = QuestHit(
                chapter=hit.chapter,
                title=hit.title,
                description=hit.description,
                source=hit.source,
                items=hit.items,
                score=score,
                active=True,
            )
        else:
            hit = QuestHit(
                chapter=hit.chapter,
                title=hit.title,
                description=hit.description,
                source=hit.source,
                items=list(hit.items),
                score=score,
                active=False,
            )
        if score > 0:
            scored.append(hit)

    scored.sort(key=lambda h: (-h.active, -h.score, h.title))
    return scored[:MAX_QUEST_HITS]


def detect_quest_conflict(hits: list[QuestHit], removed_items: set[str]) -> bool:
    """Conflict if a quest item was removed/replaced in pack scripts."""
    if not removed_items:
        return False
    removed = {r.lower() for r in removed_items}
    for h in hits:
        for item in h.items:
            if item.lower() in removed:
                return True
    return False


def format_quest_guide(
    hits: list[QuestHit],
    conflict: bool = False,
    local_plain: str | None = None,
    total_matched: int = 0,
) -> str:
    lines = [
        "【任務導引】這個整合包的任務書裡有相關內容。",
        "請打開任務書，查看：",
    ]
    for i, h in enumerate(hits, 1):
        chap = h.chapter or "（未命名章節）"
        title = h.title or "（未命名任務）"
        extra = "（進行中）" if h.active else ""
        lines.append(f"{i}. 章節：{chap}　任務：{title}{extra}")
        if h.description:
            desc = h.description[:120] + ("…" if len(h.description) > 120 else "")
            lines.append(f"   摘要：{desc}")
    if total_matched > len(hits):
        lines.append("還有其他相關任務，可在任務書搜尋關鍵字。")
    lines.append("若只是卡住／看不懂，請先照任務說明做；不必另外查 wiki。")
    srcs = "、".join(dict.fromkeys(h.source for h in hits))
    lines.append(f"【來源】{srcs}")
    if conflict:
        lines.append("【警告】任務書可能已過時（與本地魔改衝突），以下依整合包實際設定：")
        if local_plain:
            lines.append(local_plain)
    return "\n".join(lines)


def _parse_quest_file(path: Path, text: str, pack_root: Path) -> list[QuestHit]:
    try:
        rel = path.relative_to(pack_root).as_posix()
    except ValueError:
        rel = path.name

    titles = _TITLE_RE.findall(text)
    descs = _DESC_RE.findall(text)
    items = [m.group(1).lower() for m in _ITEM_ID_RE.finditer(text)]
    # dedupe items preserve order
    seen: set[str] = set()
    uniq_items: list[str] = []
    for it in items:
        if it not in seen and ":" in it:
            seen.add(it)
            uniq_items.append(it)

    chapter = path.parent.name
    if not titles:
        # filename as weak title if file mentions items
        if uniq_items or len(text) > 40:
            titles = [path.stem]
        else:
            return []

    hits: list[QuestHit] = []
    for i, title in enumerate(titles[:20]):
        desc = descs[i] if i < len(descs) else (descs[0] if descs else "")
        hits.append(
            QuestHit(
                chapter=chapter,
                title=title,
                description=desc,
                source=rel,
                items=uniq_items[:12],
            )
        )
    return hits
