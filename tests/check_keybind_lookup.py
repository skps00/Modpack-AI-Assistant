#!/usr/bin/env python3
"""Mirrors KeybindReader conflict/unbound + KeybindAskTool format/filter + PackIndex intent."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
MISS = "[TOOL_MISS] keybind_lookup empty — do not invent"
LIMIT = 10
GENERIC_TOKEN_COVERAGE_PCT = 40
EMPTY_QUERY_HINT = (
    "未指定查詢（query 為空）：請用 query=<該模組標籤裡最獨特嘅關鍵詞> "
    "或 key=按鍵名（例 M）或 namespace=modid（例 jei）再查一次。"
)
CJK_RUN = re.compile(r"[\u4e00-\u9fff]+")

# Fix 5 / probe rules — same literals
KEYBIND_RULES = [
    r"按鍵|按键|快捷鍵|快捷键|熱鍵|热键",
    r"撳\s?(咩|乜|邊個|边个)?\s?掣|按咩掣|按咩鍵|按什麼鍵|按哪個鍵|按哪个键|咩掣|乜掣|邊個掣",
    r"改鍵|改键|綁鍵|绑键|綁定按鍵|按鍵設定|冇綁|沒綁|未綁",
    r"(掣|鍵|键)[^。！？]{0,8}(撞|衝突|重复|重複)|同一個(掣|按鍵)",
    r"key\s?bind|keybind|hot\s?key|hotkey|rebind",
    r"(which|what)\s+(key|button)",
    r"change the .{0,12}key",
]
KEYBIND_PATTERNS = [re.compile(p, re.IGNORECASE) for p in KEYBIND_RULES]


def namespace_of(raw_key: str) -> str:
    if not raw_key:
        return ""
    parts = raw_key.split(".", 2)
    return parts[1] if len(parts) >= 2 else ""


def mark_conflicts(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    counts: dict[str, int] = {}
    for r in rows:
        if not r.get("unbound") and r.get("keyDisplay"):
            kd = r["keyDisplay"]
            counts[kd] = counts.get(kd, 0) + 1
    conflict_keys = {k for k, n in counts.items() if n >= 2}
    out = []
    for r in rows:
        conflict = (not r.get("unbound")) and r.get("keyDisplay") in conflict_keys
        out.append({**r, "conflict": conflict})
    return out


def format_line(row: dict[str, Any]) -> str:
    label = row.get("label") or ""
    if row.get("unbound"):
        body = f"- {label} → （未綁）"
    else:
        body = f"- {label} → {row.get('keyDisplay') or ''}"
    if row.get("conflict"):
        body += "  ⚠撞鍵"
    return body


def tokens(query: str) -> list[str]:
    """Whitespace tokens + CJK 2/3-grams (same rules as KeybindAskTool.tokens)."""
    if not query or not str(query).strip():
        return []
    out: list[str] = []
    seen: set[str] = set()

    def add(t: str) -> None:
        if t and t not in seen:
            seen.add(t)
            out.append(t)

    for part in str(query).strip().split():
        add(part)
    for m in CJK_RUN.finditer(str(query)):
        run = m.group()
        for n in (2, 3):
            for i in range(0, len(run) - n + 1):
                add(run[i : i + n])
    return out


def score_tokens(query: str) -> list[str]:
    """If query has CJK, only CJK-bearing tokens (mirrors KeybindAskTool.scoreTokens)."""
    all_tok = tokens(query)
    if not query or not CJK_RUN.search(str(query)):
        return all_tok
    return [t for t in all_tok if t and CJK_RUN.search(t)]


def filter_generic_tokens(
    token_list: list[str], rows: list[dict[str, Any]]
) -> list[str]:
    """Drop len≥2 tokens hitting ≥GENERIC_TOKEN_COVERAGE_PCT% of rows (mirrors Java)."""
    if not token_list:
        return []
    if not rows:
        return list(token_list)
    n = len(rows)
    kept: list[str] = []
    for token in token_list:
        if not token:
            continue
        if len(token) < 2:
            kept.append(token)
            continue
        t_lower = token.lower()
        hit_count = 0
        for r in rows:
            label_lower = (r.get("label") or "").lower()
            rk_lower = (r.get("rawKey") or "").lower()
            if t_lower in label_lower or t_lower in rk_lower:
                hit_count += 1
        if hit_count * 100 >= n * GENERIC_TOKEN_COVERAGE_PCT:
            continue
        kept.append(token)
    return kept


def resolve_query(args: dict[str, Any] | None, question: str | None = None) -> str:
    """query → machine → item → question (mirrors KeybindAskTool.run)."""
    if not args:
        args = {}
    for key in ("query", "machine", "item"):
        v = args.get(key)
        if isinstance(v, str) and v.strip():
            return v.strip()
    if question is not None and str(question).strip():
        return str(question).strip()
    return ""


def empty_query_guidance(rows: list[dict[str, Any]]) -> str:
    return MISS + "\n" + EMPTY_QUERY_HINT + "\n" + stats_line(rows)


def run_lookup(
    rows: list[dict[str, Any]],
    args: dict[str, Any] | None = None,
    question: str | None = None,
) -> str:
    """Full mirror of KeybindAskTool.run (coverage filter + empty guidance)."""
    if not rows:
        return MISS
    args = args or {}
    query = resolve_query(args, question=question)
    key_f = (args.get("key") or "").strip() if isinstance(args.get("key"), str) else ""
    ns_f = (
        (args.get("namespace") or "").strip()
        if isinstance(args.get("namespace"), str)
        else ""
    )
    if not query and not key_f and not ns_f:
        return empty_query_guidance(rows)
    tok: list[str] = []
    if query:
        tok = filter_generic_tokens(score_tokens(query), rows)
        if not tok:
            return empty_query_guidance(rows)
    scored: list[tuple[int, dict[str, Any]]] = []
    for r in rows:
        if ns_f and (r.get("namespace") or "").lower() != ns_f.lower():
            continue
        ks = 0
        if key_f:
            ks = key_match_score(r, key_f)
            if ks <= 0:
                continue
        qs = 0
        if query:
            qs = score_row(r, tok)
            if qs <= 0:
                continue
        sort_score = qs if query else ks
        scored.append((sort_score, r))
    if not scored:
        return MISS + "\n" + stats_line(rows)
    scored.sort(key=lambda t: (-t[0], (t[1].get("label") or "")))
    hits = [r for _, r in scored]
    return format_hits(hits)


def score_row(row: dict[str, Any], token_list: list[str]) -> int:
    """Same scoring literals as KeybindAskTool.scoreRow."""
    if not token_list:
        return 0
    label = (row.get("label") or "").lower()
    kd = row.get("keyDisplay") or ""
    rk = row.get("rawKey") or ""
    rk_lower = rk.lower()
    ns = row.get("namespace") or ""
    score = 0
    for token in token_list:
        if not token:
            continue
        t_lower = token.lower()
        if kd.lower() == t_lower:
            score += 100
        if rk.lower() == t_lower:
            score += 80
        if len(token) >= 2 and t_lower in label:
            score += 10 * len(token)
        if len(token) >= 2 and t_lower in rk_lower:
            score += 5
        if ns.lower() == t_lower:
            score += 20
    return score


def key_match_score(row: dict[str, Any], key_filter: str) -> int:
    if not key_filter:
        return 0
    kd = row.get("keyDisplay") or ""
    rk = row.get("rawKey") or ""
    kl = key_filter.lower()
    if kd.lower() == kl or rk.lower() == kl:
        return 100
    if kl in kd.lower() or kl in rk.lower():
        return 10
    return 0


def score_rows(
    rows: list[dict[str, Any]],
    query: str = "",
    key: str = "",
    namespace: str = "",
) -> list[tuple[int, dict[str, Any]]]:
    """Score + filter; returns (score, row) sorted high→low, then label."""
    q = (query or "").strip()
    key_f = (key or "").strip()
    ns_f = (namespace or "").strip()
    tok = score_tokens(q) if q else []
    scored: list[tuple[int, dict[str, Any]]] = []
    for r in rows:
        if ns_f and (r.get("namespace") or "").lower() != ns_f.lower():
            continue
        ks = 0
        if key_f:
            ks = key_match_score(r, key_f)
            if ks <= 0:
                continue
        qs = 0
        if q:
            qs = score_row(r, tok)
            if qs <= 0:
                continue
        sort_score = qs if q else ks
        scored.append((sort_score, r))
    scored.sort(key=lambda t: (-t[0], (t[1].get("label") or "")))
    return scored


def filter_rows(
    rows: list[dict[str, Any]],
    query: str = "",
    key: str = "",
    namespace: str = "",
) -> list[dict[str, Any]]:
    return [r for _, r in score_rows(rows, query=query, key=key, namespace=namespace)]


def stats_line(rows: list[dict[str, Any]]) -> str:
    n = len(rows)
    unbound = sum(1 for r in rows if r.get("unbound"))
    conflict_groups = {
        r.get("keyDisplay")
        for r in rows
        if r.get("conflict") and r.get("keyDisplay")
    }
    c = len(conflict_groups)
    return (
        f"已載入 {n} 個按鍵功能：{c} 組撞鍵、{unbound} 個未綁。"
        f"可用 key=按鍵名（例 M）或 namespace=modid（例 jei）收窄。"
    )


def format_hits(rows_or_hits: list[dict[str, Any]] | None = None, hits: list[dict[str, Any]] | None = None) -> str:
    """format_hits(hits) or format_hits(rows, hits=[]) for miss+stats."""
    if hits is not None:
        rows = rows_or_hits or []
        actual = hits
    else:
        rows = None
        actual = rows_or_hits or []
    if not actual:
        if rows:
            return MISS + "\n" + stats_line(rows)
        return MISS
    lines = [format_line(h) for h in actual[:LIMIT]]
    extra = len(actual) - LIMIT
    if extra > 0:
        lines.append(f"…另有 {extra} 項")
    return "\n".join(lines)


def is_keybind_question(question: str | None) -> bool:
    if question is None or not str(question).strip():
        return False
    for p in KEYBIND_PATTERNS:
        if p.search(question):
            return True
    return False


def load_artifact_rows(path: Path) -> list[dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    raw_rows = data.get("rows") or []
    out: list[dict[str, Any]] = []
    for r in raw_rows:
        raw_key = r.get("id") or ""
        key_path = r.get("key") or ""
        bound = bool(r.get("bound"))
        unbound = not bound
        display = ""
        if bound and key_path:
            seg = key_path.rsplit(".", 1)[-1]
            if seg and seg.lower() != "unknown":
                display = seg.upper() if len(seg) == 1 else seg
            else:
                unbound = True
        out.append(
            {
                "label": r.get("label") or "",
                "rawKey": raw_key,
                "keyDisplay": display,
                "unbound": unbound,
                "namespace": namespace_of(raw_key),
            }
        )
    return mark_conflicts(out)


def main() -> None:
    # 1) conflict grouping
    base = [
        {"label": "Open Map", "rawKey": "key.xaero.minimap", "keyDisplay": "M", "unbound": False, "namespace": "xaero"},
        {"label": "Open Pack AI", "rawKey": "key.packai.open", "keyDisplay": "M", "unbound": False, "namespace": "packai"},
        {"label": "Jump", "rawKey": "key.jump", "keyDisplay": "SPACE", "unbound": False, "namespace": "jump"},
        {"label": "Unbound Thing", "rawKey": "key.mod.x", "keyDisplay": "M", "unbound": True, "namespace": "mod"},
    ]
    marked = mark_conflicts(base)
    by_label = {r["label"]: r for r in marked}
    assert by_label["Open Map"]["conflict"] is True
    assert by_label["Open Pack AI"]["conflict"] is True
    assert by_label["Jump"]["conflict"] is False
    assert by_label["Unbound Thing"]["conflict"] is False  # unbound not counted

    solo = mark_conflicts([
        {"label": "Only M", "rawKey": "key.a", "keyDisplay": "M", "unbound": False, "namespace": "a"},
    ])
    assert solo[0]["conflict"] is False

    # 2) unbound display
    assert "（未綁）" in format_line(by_label["Unbound Thing"])
    assert "→ M" in format_line(by_label["Open Map"])
    assert "⚠撞鍵" in format_line(by_label["Open Map"])

    # 3) line limit
    many = [
        {
            "label": f"Action {i}",
            "rawKey": f"key.mod.a{i}",
            "keyDisplay": str(i),
            "unbound": False,
            "namespace": "mod",
            "conflict": False,
        }
        for i in range(15)
    ]
    text = format_hits(many)
    body_lines = [ln for ln in text.splitlines() if ln.startswith("- ")]
    assert len(body_lines) == 10, len(body_lines)
    assert "…另有 5 項" in text

    # 4) filter: case-insensitive query + namespace narrow
    rows = mark_conflicts([
        {"label": "Open JEI", "rawKey": "key.jei.showRecipe", "keyDisplay": "R", "unbound": False, "namespace": "jei"},
        {"label": "Open Map", "rawKey": "key.xaero.map", "keyDisplay": "X", "unbound": False, "namespace": "xaero"},
        {"label": "jei Config", "rawKey": "key.jei.config", "keyDisplay": "C", "unbound": False, "namespace": "jei"},
    ])
    q_hits = filter_rows(rows, query="open")
    assert len(q_hits) == 2
    q_hits_ci = filter_rows(rows, query="OPEN")
    assert len(q_hits_ci) == 2
    ns_hits = filter_rows(rows, query="open", namespace="jei")
    assert len(ns_hits) == 1 and ns_hits[0]["label"] == "Open JEI"

    # 5) miss
    assert format_hits([]) == MISS
    assert filter_rows(rows, query="zzz_no_such") == []
    assert format_hits(filter_rows(rows, query="zzz_no_such")) == MISS

    # 6) intent rules + corpus
    probe_path = ROOT / "docs/research/artifacts/2026-10-10-keybind-intent-probe.json"
    corpus_path = ROOT / "docs/research/artifacts/2026-10-10-question-corpus.json"
    if not probe_path.is_file() or not corpus_path.is_file():
        print("SKIP intent corpus/probe missing")
    else:
        probe = json.loads(probe_path.read_text(encoding="utf-8"))
        positives = probe.get("positives") or []
        assert len(positives) == 18, len(positives)
        for p in positives:
            q = p["q"] if isinstance(p, dict) else str(p)
            assert is_keybind_question(q), f"positive miss: {q!r}"

        corpus = json.loads(corpus_path.read_text(encoding="utf-8"))
        titles = corpus.get("titles") or []
        assert len(titles) == 589, len(titles)
        flagged = [t for t in titles if is_keybind_question(t)]
        assert len(flagged) <= 1, (len(flagged), flagged[:5])

    # 7) Fix 9: score ranking — open map before Open Trinkets Pouch
    rank_rows = mark_conflicts([
        {"label": "Open Trinkets Pouch", "rawKey": "key.mod.pouch", "keyDisplay": "P", "unbound": False, "namespace": "mod"},
        {"label": "Open Map", "rawKey": "key.xaero.map", "keyDisplay": "X", "unbound": False, "namespace": "xaero"},
    ])
    ranked = filter_rows(rank_rows, query="open map")
    assert [r["label"] for r in ranked] == ["Open Map", "Open Trinkets Pouch"], [r["label"] for r in ranked]

    # 8) Chinese query on English labels → 0 hits + stats line
    en_rows = mark_conflicts([
        {"label": "Open Map", "rawKey": "key.xaero.map", "keyDisplay": "M", "unbound": False, "namespace": "xaero"},
        {"label": "Open Pack AI", "rawKey": "key.packai.open", "keyDisplay": "M", "unbound": False, "namespace": "packai"},
        {"label": "Jump", "rawKey": "key.jump", "keyDisplay": "SPACE", "unbound": False, "namespace": "jump"},
    ])
    zh_q = "撳咩掣開 mod 清單"
    zh_hits = filter_rows(en_rows, query=zh_q)
    assert zh_hits == [], zh_hits
    miss_text = format_hits(en_rows, hits=[])
    assert MISS in miss_text
    assert "組撞鍵" in miss_text
    assert "個未綁" in miss_text

    # 9) key=R — exact/contains on key only; ≤10 body lines; …另有 N 項 format
    many_r = mark_conflicts(
        [
            {
                "label": f"Bound R {i}",
                "rawKey": f"key.mod.r{i}",
                "keyDisplay": "R",
                "unbound": False,
                "namespace": "mod",
            }
            for i in range(12)
        ]
        + [
            {
                "label": "Has r in label only",
                "rawKey": "key.foo.baz",
                "keyDisplay": "X",
                "unbound": False,
                "namespace": "foo",
            }
        ]
    )
    r_hits = filter_rows(many_r, key="R")
    assert all(
        (h.get("keyDisplay") or "").lower() == "r"
        or "r" in (h.get("rawKey") or "").lower()
        for h in r_hits
    )
    assert all("Has r in label only" != h["label"] for h in r_hits)
    r_text = format_hits(r_hits)
    r_body = [ln for ln in r_text.splitlines() if ln.startswith("- ")]
    assert len(r_body) <= 10, len(r_body)
    if len(r_hits) > LIMIT:
        assert re.search(r"…另有 \d+ 項", r_text), r_text

    # 10) query=r single char — no label-substring dump
    eng_many = mark_conflicts(
        [
            {
                "label": f"Craft recipe {i}",
                "rawKey": f"key.mod.c{i}",
                "keyDisplay": "C",
                "unbound": False,
                "namespace": "mod",
            }
            for i in range(20)
        ]
        + [
            {
                "label": "Show Recipe",
                "rawKey": "key.jei.showRecipe",
                "keyDisplay": "R",
                "unbound": False,
                "namespace": "jei",
            }
        ]
    )
    single_r = filter_rows(eng_many, query="r")
    exact_key = [
        r
        for r in eng_many
        if (r.get("keyDisplay") or "").lower() == "r" or (r.get("rawKey") or "").lower() == "r"
    ]
    assert len(single_r) < len(eng_many)
    assert len(single_r) == len(exact_key), (len(single_r), len(exact_key))

    # 11) real artifact — Chinese query → 0 hits
    art_path = ROOT / "docs/research/artifacts/2026-10-10-keybinds-AI_test_NFWC_DIM.json"
    assert art_path.is_file(), art_path
    art_rows = load_artifact_rows(art_path)
    assert len(art_rows) == 201, len(art_rows)
    art_hits = filter_rows(art_rows, query="撳咩掣開 mod 清單")
    assert art_hits == [], len(art_hits)

    # 12) Fix 5.1 — coverage filter on real artifact
    eng_q = "Which key opens the map?"
    eng_tok = score_tokens(eng_q)
    assert "key" in [t.lower() for t in eng_tok]
    filtered_eng = filter_generic_tokens(eng_tok, art_rows)
    assert "key" not in [t.lower() for t in filtered_eng], filtered_eng
    eng_out = run_lookup(art_rows, {"query": eng_q})
    if MISS in eng_out:
        eng_hit_n = 0
    else:
        eng_hit_n = len([ln for ln in eng_out.splitlines() if ln.startswith("- ")])
        if "…另有" in eng_out:
            # capped display; still must not equal dump of all rows
            m = re.search(r"…另有 (\d+) 項", eng_out)
            eng_hit_n = (eng_hit_n + int(m.group(1))) if m else eng_hit_n
    assert eng_hit_n != len(art_rows), (eng_hit_n, len(art_rows))
    map_kept = filter_generic_tokens(score_tokens("map"), art_rows)
    assert "map" in [t.lower() for t in map_kept], map_kept
    map_out = run_lookup(art_rows, {"query": "map"})
    assert "未指定查詢" not in map_out, map_out
    assert "→" in map_out, map_out

    # 13) Fix 5.2 — empty query/machine/item → guidance, no dump lines
    empty_out = run_lookup(art_rows, {})
    assert MISS in empty_out
    assert "未指定查詢" in empty_out
    assert "組撞鍵" in empty_out
    assert "→" not in empty_out, empty_out

    # 14) Fix 5.3 — arg compat: item / machine / query → same result
    fake = mark_conflicts(
        [
            {
                "label": "Open Map",
                "rawKey": "key.xaero.map",
                "keyDisplay": "X",
                "unbound": False,
                "namespace": "xaero",
            },
            {
                "label": "Jump",
                "rawKey": "key.jump",
                "keyDisplay": "SPACE",
                "unbound": False,
                "namespace": "minecraft",
            },
            {
                "label": "Open Pack AI",
                "rawKey": "key.packai.open",
                "keyDisplay": "SEMICOLON",
                "unbound": False,
                "namespace": "packai",
            },
        ]
    )
    a_item = run_lookup(fake, {"item": "map"})
    a_machine = run_lookup(fake, {"machine": "map"})
    a_query = run_lookup(fake, {"query": "map"})
    assert a_item == a_machine == a_query, (a_item, a_machine, a_query)
    assert "Open Map" in a_query

    # 15) Fix 5.4 — generic-only query "key" → empty-query path, not full dump
    key_only = run_lookup(art_rows, {"query": "key"})
    assert MISS in key_only
    assert "未指定查詢" in key_only
    assert "→" not in key_only, key_only

    print("check_keybind_lookup OK")


if __name__ == "__main__":
    main()
