"""Pack AI Bridge — quest guide, plain answers, pack graph, mod-routing."""

from __future__ import annotations

import hashlib
import os
import threading
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from fastapi import FastAPI
from pydantic import BaseModel, Field

from mod_filter import active_scanners, derive_focus_mods, search_query_mods
from pack_modified import detect_modification
from pack_graph import ensure_pack_graph, subgraph_facts
from plainify import friendly_offline, plainify_snippets
from quests import (
    detect_quest_conflict,
    ensure_quest_index,
    format_quest_guide,
    is_quest_override,
    match_quests,
)
from rag import PackIndex
from llm import ask_llm
from web_search import format_web_warning, web_search

load_dotenv()

app = FastAPI(title="Pack AI Bridge", version="0.3.0")
_index = PackIndex()
_mod_sessions: dict[str, list[str]] = {}
_warmup_lock = threading.Lock()
_ready: set[str] = set()


class AskRequest(BaseModel):
    question: str
    context: dict[str, Any] = Field(default_factory=dict)
    language: str = "zh-TW"


class AskResponse(BaseModel):
    answer: str
    sources: list[str] = Field(default_factory=list)
    scanners: list[str] = Field(default_factory=list)
    mod_count: int = 0
    used_llm: bool = True
    top_score: int = 0
    policy: str = "online_ok"
    packTouched: bool = False
    webSources: list[str] = Field(default_factory=list)
    softFallback: bool = False
    questGuided: bool = False
    questConflict: bool = False
    questHits: list[dict[str, Any]] = Field(default_factory=list)
    indexReady: bool = True


class WarmupRequest(BaseModel):
    context: dict[str, Any] = Field(default_factory=dict)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/warmup")
def warmup(req: WarmupRequest) -> dict[str, Any]:
    mod_ids = _resolve_mod_ids(req.context)
    scanners = active_scanners(mod_ids)
    game_dir = req.context.get("gameDirectory")
    pack_root = Path(game_dir) if game_dir else None
    cache_key = _cache_key(mod_ids, pack_root)

    def _run() -> None:
        with _warmup_lock:
            _warm_all(pack_root, mod_ids, scanners, cache_key)

    threading.Thread(target=_run, daemon=True).start()
    return {"status": "warming", "cacheKey": cache_key, "modCount": len(mod_ids), "scanners": scanners}


@app.post("/v1/ask", response_model=AskResponse)
def ask(req: AskRequest) -> AskResponse:
    mod_ids = _resolve_mod_ids(req.context)
    scanners = active_scanners(mod_ids)
    game_dir = req.context.get("gameDirectory")
    pack_root = Path(game_dir) if game_dir else None
    held = req.context.get("heldItem") or {}
    cache_key = _cache_key(mod_ids, pack_root)

    if cache_key not in _ready and pack_root:
        # Brief sync warm so first ask is not empty
        with _warmup_lock:
            _warm_all(pack_root, mod_ids, scanners, cache_key)

    focus_mods = derive_focus_mods(mod_ids, held, req.question) or search_query_mods(mod_ids, held)
    path_idx = _index.ensure_index(pack_root, mod_ids, scanners, cache_key)
    paths = list(path_idx.paths) if path_idx else []
    graph = ensure_pack_graph(pack_root, paths, cache_key)
    quest_idx = ensure_quest_index(pack_root, scanners, cache_key)

    override = is_quest_override(req.question, req.context)
    active_ids = [str(x) for x in (req.context.get("activeQuests") or [])]
    quest_hits = [] if override else match_quests(req.question, held, quest_idx, active_ids)
    conflict = detect_quest_conflict(quest_hits, graph.removed_items) if quest_hits else False

    snippets, sources, top_score, high_conf = _index.retrieve(
        question=req.question,
        pack_root=pack_root,
        mod_ids=mod_ids,
        scanners=scanners,
        cache_key=cache_key,
        held_item=held,
        focus_mods=focus_mods,
    )
    facts, fact_sources = subgraph_facts(graph, held, req.question, focus_mods)
    all_sources = list(dict.fromkeys([*sources, *fact_sources]))

    # --- Quest-first path (skip web) ---
    if quest_hits and not override:
        local_plain = None
        if conflict:
            local_plain = plainify_snippets(snippets, sources) or None
            if not local_plain and facts:
                local_plain = "【本地圖事實】\n" + "\n".join(facts[:8])
        answer = format_quest_guide(
            quest_hits,
            conflict=conflict,
            local_plain=local_plain,
            total_matched=len(quest_hits),
        )
        return AskResponse(
            answer=answer,
            sources=[h.source for h in quest_hits],
            scanners=scanners,
            mod_count=len(mod_ids),
            used_llm=False,
            top_score=top_score,
            policy="local_only",
            packTouched=True,
            webSources=[],
            softFallback=False,
            questGuided=True,
            questConflict=conflict,
            questHits=[_quest_dict(h) for h in quest_hits],
            indexReady=cache_key in _ready,
        )

    verdict = detect_modification(
        scanners=scanners,
        paths=paths,
        focus_mods=focus_mods,
        held_item=held,
        snippets=snippets,
    )

    env_web = os.getenv("ALLOW_WEB_SEARCH", "false").lower() == "true"
    soft_fallback = False
    web_hits: list[dict[str, str]] = []
    if env_web:
        if verdict.policy == "online_ok" or override:
            web_hits = web_search(req.question, focus_mods, held)
        elif verdict.policy == "local_only" and not snippets and not facts:
            soft_fallback = True
            web_hits = web_search(req.question, focus_mods, held)

    used_llm = True
    plain = plainify_snippets(snippets, sources)
    if plain and high_conf and not web_hits:
        answer = plain
        used_llm = False
    elif plain and not os.getenv("OPENAI_API_KEY") and not web_hits:
        answer = plain
        used_llm = False
    else:
        excerpt = ""
        if not facts and snippets:
            body = snippets[0]
            if body.startswith("// file:") and "\n" in body:
                body = body.split("\n", 1)[1]
            excerpt = body[:120]
        answer = ask_llm(
            question=req.question,
            language=req.language,
            held_item=held,
            focus_mods=focus_mods,
            policy=verdict.policy,
            graph_facts=facts,
            sources=all_sources,
            web_hits=web_hits,
            soft_fallback=soft_fallback,
            quest_override=override,
            quest_conflict=conflict,
            short_excerpt=excerpt,
        )
        if not os.getenv("OPENAI_API_KEY") and not plain and not facts:
            answer = friendly_offline(all_sources, req.question)
            used_llm = False
        if soft_fallback and web_hits:
            answer = format_web_warning(answer)
        if override and web_hits:
            answer = "【注意：已略過任務書導引（你表示任務可能有誤）】\n" + answer

    return AskResponse(
        answer=answer,
        sources=all_sources,
        scanners=scanners,
        mod_count=len(mod_ids),
        used_llm=used_llm,
        top_score=top_score,
        policy=verdict.policy,
        packTouched=verdict.pack_touched,
        webSources=[h.get("url", "") for h in web_hits if h.get("url")],
        softFallback=soft_fallback,
        questGuided=False,
        questConflict=conflict,
        questHits=[],
        indexReady=cache_key in _ready,
    )


def _warm_all(pack_root: Path | None, mod_ids: list[str], scanners: list[str], cache_key: str) -> None:
    path_idx = _index.ensure_index(pack_root, mod_ids, scanners, cache_key)
    paths = list(path_idx.paths) if path_idx else []
    ensure_pack_graph(pack_root, paths, cache_key)
    ensure_quest_index(pack_root, scanners, cache_key)
    _ready.add(cache_key)


def _quest_dict(h: Any) -> dict[str, Any]:
    return {
        "chapter": h.chapter,
        "title": h.title,
        "source": h.source,
        "score": h.score,
        "active": h.active,
    }


def _resolve_mod_ids(context: dict[str, Any]) -> list[str]:
    raw = context.get("modIds")
    fingerprint = str(context.get("modIdsFingerprint") or "")
    if isinstance(raw, list) and raw:
        mod_ids = [str(m) for m in raw]
        _mod_sessions[fingerprint or _fingerprint(mod_ids)] = mod_ids
        return mod_ids
    if fingerprint and fingerprint in _mod_sessions:
        return list(_mod_sessions[fingerprint])
    return []


def _fingerprint(mod_ids: list[str]) -> str:
    blob = ",".join(sorted(m.lower() for m in mod_ids))
    return hashlib.sha1(blob.encode()).hexdigest()[:16]


def _cache_key(mod_ids: list[str], pack_root: Path | None) -> str:
    parts = [",".join(sorted(mod_ids))]
    if pack_root and pack_root.exists():
        try:
            parts.append(str(pack_root.stat().st_mtime_ns))
        except OSError:
            pass
    return hashlib.sha1("|".join(parts).encode()).hexdigest()[:16]


if __name__ == "__main__":
    import uvicorn

    host = os.getenv("BRIDGE_HOST", "127.0.0.1")
    port = int(os.getenv("BRIDGE_PORT", "8765"))
    uvicorn.run("main:app", host=host, port=port, reload=False)
