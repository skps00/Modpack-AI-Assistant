#!/usr/bin/env python3
"""L1 offline capability index (SQLite) for packai — cheap capability lookup.

Design (see docs/research/2026-10-11-capability-index-l1-prototype.md):
one-off, offline scan of every mod jar -> a local SQLite index of
(mod, class, string) facts -> a keyword / BM25-ish CLI query that returns
ranked *candidates* with evidence.  Zero LLM tokens, stdlib only (no javap,
no third-party deps).

Extraction (per `.class` entry, by reading the constant pool directly):
  * `class`   — the class's own simple name (from the zip entry name)
  * `literal` — a real string constant (Utf8 referenced by a CONSTANT_String).
                These carry the useful signal: lang keys, config keys, ids,
                messages (`autocraft`, `wireless`, `fluid`, ...).
  * `ref`     — an internal class name referenced by CONSTANT_Class (e.g.
                `net/minecraft/world/item/ItemStack`).  Optional, noisy; can
                be dropped with --no-refs.

Method/field names (Utf8 reachable only via NameAndType) are deliberately
NOT indexed — they are the bulk of the noise that made a raw class list
useless in the 2026-10-10 cross-pack study.

Candidate != confirmed: this is a first-pass filter, not an answer.  The
output is a shortlist to hand to a human / LLM / javap (L2), never a fact to
paste straight into an answer.

Usage:
  python tools/capability_index_l1.py build --mods <dir> --db <file> [--pack-name X]
  python tools/capability_index_l1.py query --db <file> --terms "storage,barrel"
  python tools/capability_index_l1.py dump  --db <file> [--out <file>]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import struct
import sys
import time
import zipfile
from pathlib import Path

SCHEMA_VERSION = "packai.capability-index-l1/1"

# meta keys whose value is a per-run measurement, excluded from deterministic
# output (dump / query) so the same input yields byte-identical results.
VOLATILE_META = ("build_seconds",)

# Internal class-name prefixes that are library / JDK noise, not capability.
LIB_TYPE_PREFIXES = (
    "java/",
    "javax/",
    "jdk/",
    "sun/",
    "com/sun/",
    "kotlin/",
    "kotlinx/",
    "scala/",
    "org/apache/",
    "org/eclipse/",
    "org/slf4j/",
    "org/jetbrains/",
    "com/google/",
    "com/mojang/",
    "it/unimi/",
    "org/joml/",
    "org/lwjgl/",
    "org/spongepowered/",
    "org/objectweb/",
    "org/intellij/",
)

_INTERNAL_NAME = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$]*(/[A-Za-z0-9_$]+)+$")
_TOKEN_SPLIT = re.compile(r"[^0-9A-Za-z\u00c0-\uffff]+")
_CAMEL = re.compile(r"(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")


# --------------------------------------------------------------------------
# class-file constant pool parsing (no javap)
# --------------------------------------------------------------------------
def parse_constant_pool(data: bytes):
    """Return (utf8, string_literals, class_names) or None on non-class data.

    * utf8           dict[int, str]
    * string_literals set[str]  -- Utf8 reachable via CONSTANT_String (tag 8)
    * class_names     set[str]  -- Utf8 reachable via CONSTANT_Class  (tag 7)
    """
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        return None
    cp_count = struct.unpack_from(">H", data, 8)[0]
    pos = 10
    n = len(data)
    utf8: dict[int, str] = {}
    str_ref: list[int] = []
    class_ref: list[int] = []
    i = 1
    while i < cp_count:
        if pos >= n:
            break
        tag = data[pos]
        pos += 1
        if tag == 1:  # Utf8
            if pos + 2 > n:
                break
            ln = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            if pos + ln > n:
                break
            utf8[i] = data[pos : pos + ln].decode("utf-8", "replace")
            pos += ln
        elif tag == 7:  # Class
            class_ref.append(struct.unpack_from(">H", data, pos)[0])
            pos += 2
        elif tag == 8:  # String
            str_ref.append(struct.unpack_from(">H", data, pos)[0])
            pos += 2
        elif tag in (16, 19, 20):  # MethodType, Module, Package
            pos += 2
        elif tag == 15:  # MethodHandle
            pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):  # int/float/refs/NT/dyn
            pos += 4
        elif tag in (5, 6):  # Long, Double (two slots)
            pos += 8
            i += 1
        else:
            # unknown tag -> stop early, return what we parsed so far
            break
        i += 1
    return utf8, _resolve(utf8, str_ref), _resolve(utf8, class_ref)


def _resolve(utf8: dict[int, str], indices) -> set[str]:
    out: set[str] = set()
    for k in indices:
        v = utf8.get(k)
        if v:
            out.add(v)
    return out


def tokenize(s: str) -> list[str]:
    out: list[str] = []
    for part in _TOKEN_SPLIT.split(s):
        if not part:
            continue
        out.append(part.lower())
        for c in _CAMEL.split(part):
            if c:
                out.append(c.lower())
    return out


# --------------------------------------------------------------------------
# build
# --------------------------------------------------------------------------
def list_mod_jars(mods_dir: str) -> list[str]:
    out = []
    for name in sorted(os.listdir(mods_dir)):
        low = name.lower()
        if low.endswith(".jar") and not low.endswith(".jar.disabled"):
            out.append(name)
    return out


def _iter_jar_classes(jar_path: str):
    """Yield (entry_name, class_bytes) for sorted .class entries."""
    with zipfile.ZipFile(jar_path) as zf:
        names = sorted(n for n in zf.namelist() if n.endswith(".class") and not n.endswith("module-info.class"))
        for name in names:
            try:
                data = zf.read(name)
            except Exception:
                continue
            yield name, data


def _simple_name(class_entry: str) -> str:
    name = class_entry[:-6] if class_entry.endswith(".class") else class_entry
    simple = name.split("/")[-1]
    if "$" in simple:
        simple = simple.split("$", 1)[0]
    return simple


def build_index(
    mods_dir: str,
    db_path: str,
    pack_name: str = "",
    include_refs: bool = True,
    skip_lib_types: bool = True,
    min_len: int = 3,
    max_len: int = 200,
    progress=None,
):
    jar_names = list_mod_jars(mods_dir)
    if not jar_names:
        raise SystemExit(f"error: no jars in {mods_dir}")

    if os.path.exists(db_path):
        os.remove(db_path)
    con = sqlite3.connect(db_path)
    con.execute("PRAGMA journal_mode=OFF")
    con.execute("PRAGMA synchronous=OFF")
    con.execute("PRAGMA temp_store=MEMORY")
    cur = con.cursor()
    cur.executescript(
        """
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE mods(
            id INTEGER PRIMARY KEY,
            jar TEXT NOT NULL UNIQUE,
            n_entries INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE entries(
            id INTEGER PRIMARY KEY,
            mod_id INTEGER NOT NULL,
            cls TEXT NOT NULL,
            simple TEXT NOT NULL,
            kind TEXT NOT NULL,
            value TEXT NOT NULL
        );
        """
    )
    fts_ok = True
    try:
        cur.execute("CREATE VIRTUAL TABLE fts USING fts5(tokens, content='')")
    except sqlite3.OperationalError:
        fts_ok = False

    total_entries = 0
    next_id = 1
    t0 = time.monotonic()
    for mod_id, jar in enumerate(jar_names, start=1):
        cur.execute("INSERT INTO mods(id, jar) VALUES(?, ?)", (mod_id, jar))
        jar_path = os.path.join(mods_dir, jar)
        rows = []  # (cls, simple, kind, value)
        try:
            for entry, data in _iter_jar_classes(jar_path):
                cls = entry[:-6]
                simple = _simple_name(entry)
                seen_here: set[tuple[str, str]] = set()
                # the class's own simple name -> the class index row
                rows.append((simple, simple, "class", simple))
                seen_here.add(("class", simple))
                parsed = parse_constant_pool(data)
                if parsed:
                    _utf8, literals, class_names = parsed
                    for v in sorted(literals):
                        if not (min_len <= len(v) <= max_len):
                            continue
                        if ("literal", v) in seen_here:
                            continue
                        seen_here.add(("literal", v))
                        rows.append((cls, simple, "literal", v))
                    if include_refs:
                        for v in sorted(class_names):
                            if not _INTERNAL_NAME.match(v):
                                continue
                            if skip_lib_types and v.startswith(LIB_TYPE_PREFIXES):
                                continue
                            if not (min_len <= len(v) <= max_len):
                                continue
                            if ("ref", v) in seen_here:
                                continue
                            seen_here.add(("ref", v))
                            rows.append((cls, simple, "ref", v))
        except (zipfile.BadZipFile, OSError):
            if progress:
                progress(f"  ! bad jar skipped: {jar}")
            continue

        insert = []
        fts_insert = []
        for cls, simple, kind, value in rows:
            toks = tokenize(value)
            toks.extend(tokenize(simple))
            if kind == "class":
                toks.extend(tokenize(cls))
            toks = " ".join(dict.fromkeys(t for t in toks if t))
            insert.append((next_id, mod_id, cls, simple, kind, value))
            if fts_ok:
                fts_insert.append((next_id, toks))
            next_id += 1
        cur.executemany(
            "INSERT INTO entries(id, mod_id, cls, simple, kind, value) VALUES(?,?,?,?,?,?)",
            insert,
        )
        if fts_ok:
            cur.executemany("INSERT INTO fts(rowid, tokens) VALUES(?,?)", fts_insert)
        cur.execute("UPDATE mods SET n_entries=? WHERE id=?", (len(insert), mod_id))
        total_entries += len(insert)
        if progress and mod_id % 50 == 0:
            progress(f"  ... {mod_id}/{len(jar_names)} jars, {total_entries} entries")

    cur.executescript(
        """
        CREATE INDEX idx_entries_mod ON entries(mod_id);
        """
    )
    elapsed = time.monotonic() - t0
    meta = {
        "schema": SCHEMA_VERSION,
        "pack_name": pack_name,
        "mods_dir_basename": os.path.basename(os.path.normpath(mods_dir)),
        "include_refs": "1" if include_refs else "0",
        "skip_lib_types": "1" if skip_lib_types else "0",
        "fts5": "1" if fts_ok else "0",
        "jars": str(len(jar_names)),
        "entries": str(total_entries),
        "build_seconds": f"{elapsed:.3f}",
    }
    cur.executemany("INSERT INTO meta(key, value) VALUES(?,?)", sorted(meta.items()))
    con.commit()
    con.close()
    return {
        "jars": len(jar_names),
        "entries": total_entries,
        "build_seconds": elapsed,
        "fts5": fts_ok,
        "db_path": db_path,
        "db_bytes": os.path.getsize(db_path),
    }


# --------------------------------------------------------------------------
# query
# --------------------------------------------------------------------------
def _split_terms(raw: str) -> list[str]:
    out: list[str] = []
    for chunk in re.split(r"[,\s]+", raw or ""):
        if chunk:
            out.append(chunk)
    return out


def _escape_like(term: str) -> str:
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def query_index(db_path: str, terms: list[str], limit: int = 10, evidence: int = 3, mode: str = "auto"):
    con = sqlite3.connect(db_path)
    con.row_factory = sqlite3.Row
    meta = {r["key"]: r["value"] for r in con.execute("SELECT key, value FROM meta")}
    meta = {k: v for k, v in meta.items() if k not in VOLATILE_META}
    n_mods = con.execute("SELECT COUNT(*) FROM mods").fetchone()[0]
    fts_ok = meta.get("fts5") == "1" and _fts_present(con)

    # token pool: each query term contributes its tokenisation
    q_tokens: list[str] = []
    for t in terms:
        for tok in tokenize(t):
            if len(tok) >= 2:
                q_tokens.append(tok)
    q_tokens = list(dict.fromkeys(q_tokens))
    if not q_tokens:
        con.close()
        return {"mode": "none", "terms": terms, "candidates": [], "meta": meta}

    use_fts = (mode in ("auto", "bm25")) and fts_ok
    if use_fts:
        cands = _query_fts(con, q_tokens, n_mods, limit, evidence)
        mode_used = "bm25"
    else:
        cands = _query_like(con, q_tokens, n_mods, limit, evidence)
        mode_used = "substr"
    con.close()
    return {"mode": mode_used, "terms": terms, "tokens": q_tokens, "meta": meta, "candidates": cands}


def _fts_present(con) -> bool:
    row = con.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='fts'"
    ).fetchone()
    return bool(row)


def _query_fts(con, q_tokens, n_mods, limit, evidence):
    match = " OR ".join('"%s"' % t.replace('"', '""') for t in q_tokens)
    try:
        rows = con.execute(
            "SELECT e.mod_id AS mid, e.cls AS cls, e.simple AS simple, "
            "e.kind AS kind, e.value AS value, bm25(fts) AS s "
            "FROM fts JOIN entries e ON e.id = fts.rowid "
            "WHERE fts MATCH ? ORDER BY s LIMIT 4000",
            (match,),
        ).fetchall()
    except sqlite3.OperationalError:
        return []
    mods = {r["id"]: r["jar"] for r in con.execute("SELECT id, jar FROM mods")}
    per_mod: dict[int, list] = {}
    for r in rows:
        per_mod.setdefault(r["mid"], []).append(r)
    scored = []
    for mid, lst in per_mod.items():
        lst.sort(key=lambda r: r["s"])  # bm25: more negative = more relevant
        score = round(-sum(r["s"] for r in lst[:25]), 6)  # top-25 saturation
        scored.append((score, mid, lst))
    scored.sort(key=lambda t: (-t[0], t[1]))
    out = []
    for score, mid, lst in scored[:limit]:
        picked, used = [], set()
        for r in lst:
            toks = set(tokenize(r["value"])) | set(tokenize(r["simple"]))
            hit = [t for t in q_tokens if t in toks]
            if hit:
                used.update(hit)
                picked.append(
                    {"class": r["cls"], "simple": r["simple"], "kind": r["kind"], "value": r["value"], "matched": sorted(hit)}
                )
                if len(picked) >= evidence:
                    break
        out.append(
            {"mod": mods.get(mid, str(mid)), "score": score, "matched_terms": sorted(used), "evidence": picked}
        )
    return out


def _query_like(con, q_tokens, n_mods, limit, evidence):
    # fallback used only when FTS5 is unavailable: substring match + idf-ish weight
    mods = {r["id"]: r["jar"] for r in con.execute("SELECT id, jar FROM mods")}
    mod_scores: dict[int, float] = {}
    for t in q_tokens:
        like = "%" + _escape_like(t) + "%"
        rows = con.execute(
            "SELECT mod_id, COUNT(*) AS c FROM entries "
            "WHERE value LIKE ? ESCAPE '\\' GROUP BY mod_id",
            (like,),
        ).fetchall()
        df = len(rows)
        if df == 0:
            continue
        idf = max(0.0, (n_mods - df + 0.5) / (df + 0.5))
        for r in rows:
            mod_scores[r["mod_id"]] = mod_scores.get(r["mod_id"], 0.0) + idf * (r["c"] / (r["c"] + 1.0))
    scored = sorted(mod_scores.items(), key=lambda kv: (-kv[1], kv[0]))[:limit]
    out = []
    for mod_id, score in scored:
        picked, used = [], set()
        for e in con.execute(
            "SELECT cls, simple, kind, value FROM entries WHERE mod_id=?", (mod_id,)
        ):
            toks = set(tokenize(e["value"])) | set(tokenize(e["simple"]))
            hit = [t for t in q_tokens if t in toks]
            if hit:
                used.update(hit)
                picked.append(
                    {"class": e["cls"], "simple": e["simple"], "kind": e["kind"], "value": e["value"], "matched": sorted(hit)}
                )
                if len(picked) >= evidence:
                    break
        out.append(
            {"mod": mods.get(mod_id, str(mod_id)), "score": round(score, 6), "matched_terms": sorted(used), "evidence": picked}
        )
    return out


# --------------------------------------------------------------------------
# deterministic dump (for byte-identical self-check)
# --------------------------------------------------------------------------
def dump_index(db_path: str) -> str:
    con = sqlite3.connect(db_path)
    lines = []
    for k, v in con.execute("SELECT key, value FROM meta ORDER BY key"):
        if k in VOLATILE_META:
            continue
        lines.append(f"meta\t{k}\t{v}")
    for mid, jar, n in con.execute("SELECT id, jar, n_entries FROM mods ORDER BY jar, id"):
        lines.append(f"mod\t{mid}\t{jar}\t{n}")
    for eid, mid, cls, simple, kind, value in con.execute(
        "SELECT id, mod_id, cls, simple, kind, value FROM entries ORDER BY id"
    ):
        value = value.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
        lines.append(f"e\t{eid}\t{mid}\t{kind}\t{cls}\t{simple}\t{value}")
    con.close()
    return "\n".join(lines) + "\n"


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------
def _cmd_build(args) -> int:
    if not os.path.isdir(args.mods):
        sys.stderr.write(f"error: mods dir not found: {args.mods}\n")
        return 2
    res = build_index(
        args.mods,
        args.db,
        pack_name=args.pack_name,
        include_refs=args.refs,
        skip_lib_types=not args.keep_lib_types,
        min_len=args.min_len,
        max_len=args.max_len,
        progress=(lambda m: print(m, file=sys.stderr)) if args.verbose else None,
    )
    print(
        f"OK build pack={args.pack_name or '?'} jars={res['jars']} "
        f"entries={res['entries']} seconds={res['build_seconds']:.3f} "
        f"db_bytes={res['db_bytes']} fts5={res['fts5']} -> {args.db}"
    )
    return 0


def _cmd_query(args) -> int:
    terms = _split_terms(args.terms)
    result = query_index(
        args.db, terms, limit=args.limit, evidence=args.evidence, mode=args.mode
    )
    if args.json:
        sys.stdout.write(json.dumps(result, ensure_ascii=False, sort_keys=True, indent=1) + "\n")
        return 0
    print(f"mode={result['mode']} terms={','.join(terms)}")
    print(f"candidates={len(result['candidates'])}")
    for i, c in enumerate(result["candidates"], 1):
        ev = "; ".join(
            f"[{e['kind']}] {e['class']}#{e['value']}" for e in c["evidence"]
        )
        print(f"{i:>2}. {c['mod']}  score={c['score']}  matched={','.join(c['matched_terms'])}")
        if ev:
            print(f"      {ev}")
    return 0


def _cmd_dump(args) -> int:
    text = dump_index(args.db)
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8", newline="")
    else:
        sys.stdout.write(text)
    return 0


def _cmd_stats(args) -> int:
    con = sqlite3.connect(args.db)
    meta = {k: v for k, v in con.execute("SELECT key, value FROM meta")}
    jars = con.execute("SELECT COUNT(*) FROM mods").fetchone()[0]
    entries = con.execute("SELECT COUNT(*) FROM entries").fetchone()[0]
    kinds = dict(con.execute("SELECT kind, COUNT(*) FROM entries GROUP BY kind"))
    con.close()
    print(f"db={os.path.getsize(args.db)} bytes  jars={jars}  entries={entries}")
    for k in sorted(kinds):
        print(f"  kind={k}: {kinds[k]}")
    for k in sorted(meta):
        print(f"  meta.{k}={meta[k]}")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="L1 offline capability index (SQLite)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    b = sub.add_parser("build", help="scan jars and build the SQLite index")
    b.add_argument("--mods", required=True)
    b.add_argument("--db", required=True)
    b.add_argument("--pack-name", default="")
    b.add_argument("--refs", action="store_true", help="also index constant-pool class refs (noisier, ~4x bigger)")
    b.add_argument("--keep-lib-types", action="store_true", help="keep JDK/library class refs (only with --refs)")
    b.add_argument("--min-len", type=int, default=3)
    b.add_argument("--max-len", type=int, default=200)
    b.add_argument("--verbose", action="store_true")
    b.set_defaults(func=_cmd_build)

    q = sub.add_parser("query", help="keyword / BM25-ish capability lookup")
    q.add_argument("--db", required=True)
    q.add_argument("--terms", required=True, help="comma/space separated query terms")
    q.add_argument("--limit", type=int, default=10)
    q.add_argument("--evidence", type=int, default=3)
    q.add_argument("--mode", choices=["auto", "bm25", "substr"], default="auto")
    q.add_argument("--json", action="store_true")
    q.set_defaults(func=_cmd_query)

    d = sub.add_parser("dump", help="deterministic text dump (self-check)")
    d.add_argument("--db", required=True)
    d.add_argument("--out", default=None)
    d.set_defaults(func=_cmd_dump)

    s = sub.add_parser("stats", help="index statistics")
    s.add_argument("--db", required=True)
    s.set_defaults(func=_cmd_stats)

    args = ap.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
