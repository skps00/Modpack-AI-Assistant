#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Offline mechanics-number extractor (A1 config / A2 data JSON / A3 javap / B lang).

Read-only on pack/instance. Stdlib only. Writes solely to --out / report --out.
See docs/plans/2026-10-11-mech-numbers-tool.md.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Any, Iterable, Optional

SCHEMA = 1

CONFIG_EXTS = {
    ".toml",
    ".json5",
    ".json",
    ".yaml",
    ".yml",
    ".snbt",
    ".cfg",
    ".txt",
}

DEFAULT_JAVAP = (
    "C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2/bin/javap.exe"
)

# unit_hint may ONLY be one of these (spec §4). Never invent others.
UNIT_HINTS = ("tick", "second", "block", "RF", "FE", "J", "mB", "speed", "duration")

# A2: only emit numeric fields whose key/path matches mechanics intent.
A2_KEY_RE = re.compile(
    r"(energy|burn|rate|tick|speed|duration|capacity|coolant|fuel|heat|power|"
    r"generation|transfer|cooldown|per_tick|pertick|joule|fluidrate|tank|"
    r"\brf\b|\bfe\b|\bmb\b)",
    re.I,
)
A2_SKIP_KEY = re.compile(
    r"^(count|x|y|z|u|v|r|g|b|a|color|priority|weight|order|index|slot|page|id)$",
    re.I,
)

# Line parsers
RE_TOML_TABLE = re.compile(r"^\s*\[([^\]]+)\]\s*(?:#.*)?$")
RE_ASSIGN = re.compile(
    r"^\s*(?:\"([^\"]+)\"|'([^']+)'|([A-Za-z_][\w\-\.]*))\s*[=:]\s*"
    r"(?:\"(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\"|"
    r"'(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)'|"
    r"(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?))\s*(?:[;#].*)?$"
)
RE_JSON_KV = re.compile(
    r"\"([^\"]+)\"\s*:\s*(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\s*,?"
)
RE_CFG_KV = re.compile(
    r"^\s*([A-Za-z_][\w\-\.]*)\s*[:=]\s*"
    r"(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\s*(?:[;#].*)?$"
)
RE_HASH_COMMENT = re.compile(r"^\s*#(.*)$")
RE_LINE_COMMENT = re.compile(r"^\s*//(.*)$")
RE_LANG_NUM = re.compile(
    r"(?:\"(?P<qkey>[^\"]+)\"|(?P<key>[A-Za-z0-9_./\-]+))\s*[=:]\s*"
    r"[\"']?(?P<body>.*?)[\"']?\s*,?\s*$"
)
RE_LANG_BODY_NUM = re.compile(
    r"(?P<num>-?\d+(?:\.\d+)?)\s*(?:RF|FE|J|mB|mb|tick|second|sec|/t\b)",
    re.I,
)
RE_JAVAP_METHOD = re.compile(r"^\s*(?:public|protected|private|static|final|\s)+.+\s+(\w+)\(.*\);?\s*$")
RE_JAVAP_NUM = re.compile(
    r"\b(?:bipush|sipush|ldc(?:_w)?|iconst_\d|ldc2_w)\b.*?\b(-?\d+(?:\.\d+)?)\b|"
    r"ConstantValue:\s*(-?\d+(?:\.\d+)?)"
)
RE_A3_CLASS = re.compile(
    r"(?i)(Builder|Config|Generator|Reactor|Machine|Burner|Turbine|Fission)"
)

FORBIDDEN_NET = {
    "urllib",
    "urllib.request",
    "urllib.parse",
    "requests",
    "http",
    "http.client",
    "httplib",
    "aiohttp",
    "socket",
}


def _norm_rel(path: str) -> str:
    return path.replace("\\", "/")


def _short_comment(text: str, limit: int = 120) -> str:
    t = " ".join(text.strip().split())
    if len(t) <= limit:
        return t
    return t[: limit - 1] + "…"


def unit_hint_from(text: str) -> Optional[str]:
    """Map surrounding text to an allowed unit_hint, else None."""
    if not text:
        return None
    low = text.lower()
    # order matters: more specific first
    checks = [
        ("per tick", "tick"),
        ("/tick", "tick"),
        (" tick", "tick"),
        ("per second", "second"),
        ("/second", "second"),
        (" second", "second"),
        ("duration", "duration"),
        ("speed", "speed"),
        ("per block", "block"),
        ("/block", "block"),
        (" block", "block"),
        (" mb", "mB"),
        ("(mb)", "mB"),
        ("in mb", "mB"),
        (" rf", "RF"),
        ("(rf)", "RF"),
        (" fe", "FE"),
        ("(fe)", "FE"),
        (" joule", "J"),
        ("(j)", "J"),
        (" in j", "J"),
    ]
    for needle, hint in checks:
        if needle in low:
            return hint if hint in UNIT_HINTS else None
    # bare tokens
    for hint in UNIT_HINTS:
        if re.search(rf"\b{re.escape(hint.lower())}\b", low):
            return hint
    return None


def _parse_number(raw: str) -> Optional[float | int]:
    try:
        if re.fullmatch(r"-?\d+", raw):
            return int(raw)
        return float(raw)
    except ValueError:
        return None


def _mod_from_config_rel(rel: str) -> str:
    parts = _norm_rel(rel).split("/")
    # config/Mekanism/foo.toml → Mekanism; config/foo.toml → foo
    if len(parts) >= 3 and parts[0] == "config":
        return parts[1]
    if len(parts) >= 2 and parts[0] == "config":
        stem = Path(parts[1]).stem
        return stem or "config"
    return "config"


def _mod_from_jar(jar_name: str) -> str:
    base = re.sub(r"\.jar$", "", jar_name, flags=re.I)
    base = re.sub(r"-(?:forge|fabric|quilt)(?:-|$)", "-", base, flags=re.I)
    base = re.sub(r"-?\d+(?:\.\d+)+(?:[.-].*)?$", "", base)
    return base or jar_name


def _record(
    *,
    mod: str,
    source_kind: str,
    tier: str,
    file: str,
    key: str,
    value: Any,
    value_raw: str,
    unit_hint: Optional[str],
    line: Optional[int],
    comment: str,
    class_name: Optional[str] = None,
    method: Optional[str] = None,
) -> dict:
    return {
        "mod": mod,
        "source_kind": source_kind,
        "tier": tier,
        "file": _norm_rel(file),
        "key": key,
        "value": value,
        "value_raw": value_raw,
        "unit_hint": unit_hint,
        "line": line,
        "comment": _short_comment(comment) if comment else "",
        "class": class_name,
        "method": method,
    }


def _strip_line_comment_body(line: str) -> tuple[str, str]:
    """Return (code, trailing_comment_body)."""
    in_s = None
    for i, ch in enumerate(line):
        if in_s:
            if ch == in_s and (i == 0 or line[i - 1] != "\\"):
                in_s = None
            continue
        if ch in ("'", '"'):
            in_s = ch
            continue
        if ch == "#" or (ch == "/" and i + 1 < len(line) and line[i + 1] == "/"):
            return line[:i], line[i:].lstrip("#/").strip()
    return line, ""


def scan_config_file(abs_path: Path, rel: str) -> list[dict]:
    """A1: line-oriented scan preserving line numbers + nearby comments."""
    try:
        text = abs_path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return []
    ext = abs_path.suffix.lower()
    mod = _mod_from_config_rel(rel)
    records: list[dict] = []
    section = ""
    pending_comments: list[str] = []
    in_block = False

    lines = text.splitlines()
    for lineno, raw_line in enumerate(lines, 1):
        line = raw_line
        # block comments /* */
        if in_block:
            if "*/" in line:
                in_block = False
                line = line.split("*/", 1)[1]
            else:
                continue
        if "/*" in line and ext in {".json5", ".json", ".cfg", ".txt"}:
            before, _, after = line.partition("/*")
            if "*/" in after:
                after = after.split("*/", 1)[1]
                line = before + after
            else:
                in_block = True
                line = before

        hm = RE_HASH_COMMENT.match(line)
        lm = RE_LINE_COMMENT.match(line)
        if hm or lm:
            body = (hm.group(1) if hm else lm.group(1)).strip()
            if body.lower().startswith("range:"):
                # keep as comment context but do not clear pending
                pending_comments.append(body)
            else:
                pending_comments.append(body)
            continue

        code, trail = _strip_line_comment_body(line)
        code_s = code.strip()
        if not code_s:
            continue

        if ext in {".toml", ".yaml", ".yml"} or (
            ext in {".cfg", ".txt"} and code_s.startswith("[")
        ):
            tm = RE_TOML_TABLE.match(code_s)
            if tm:
                section = tm.group(1).strip()
                pending_comments.clear()
                continue

        key = raw_num = None
        m = RE_ASSIGN.match(code_s)
        if m:
            key = m.group(1) or m.group(2) or m.group(3)
            raw_num = m.group(4) or m.group(5) or m.group(6)
        elif ext in {".json", ".json5", ".snbt"}:
            jm = RE_JSON_KV.search(code_s)
            if jm:
                key, raw_num = jm.group(1), jm.group(2)
        else:
            cm = RE_CFG_KV.match(code_s)
            if cm:
                key, raw_num = cm.group(1), cm.group(2)

        if key is None or raw_num is None:
            pending_comments.clear()
            continue

        val = _parse_number(raw_num)
        if val is None:
            pending_comments.clear()
            continue

        full_key = f"{section}.{key}" if section else key
        comment = " ".join(pending_comments)
        if trail:
            comment = (comment + " " + trail).strip()
        hint = unit_hint_from(comment + " " + full_key)
        records.append(
            _record(
                mod=mod,
                source_kind="A1",
                tier="A",
                file=rel,
                key=full_key,
                value=val,
                value_raw=str(raw_num),
                unit_hint=hint,
                line=lineno,
                comment=comment,
            )
        )
        pending_comments.clear()
    return records


def scan_config_tree(config_dir: Path) -> tuple[list[dict], int]:
    records: list[dict] = []
    n_files = 0
    if not config_dir.is_dir():
        return records, 0
    paths = sorted(
        p
        for p in config_dir.rglob("*")
        if p.is_file() and p.suffix.lower() in CONFIG_EXTS
    )
    for p in paths:
        n_files += 1
        rel = _norm_rel(str(Path("config") / p.relative_to(config_dir)))
        records.extend(scan_config_file(p, rel))
    return records, n_files


def _walk_json_numbers(
    obj: Any, prefix: str, out: list[tuple[str, Any, str]]
) -> None:
    if isinstance(obj, dict):
        for k in sorted(obj.keys(), key=lambda x: str(x)):
            path = f"{prefix}.{k}" if prefix else str(k)
            v = obj[k]
            if isinstance(v, bool):
                continue
            if isinstance(v, (int, float)):
                if A2_KEY_RE.search(str(k)) or A2_KEY_RE.search(path):
                    if not A2_SKIP_KEY.match(str(k)):
                        raw = str(v)
                        out.append((path, v, raw))
            elif isinstance(v, (dict, list)):
                _walk_json_numbers(v, path, out)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            path = f"{prefix}[{i}]"
            if isinstance(v, bool):
                continue
            if isinstance(v, (int, float)):
                if A2_KEY_RE.search(prefix):
                    out.append((path, v, str(v)))
            elif isinstance(v, (dict, list)):
                _walk_json_numbers(v, path, out)


def scan_mods_a2(mods_dir: Path) -> tuple[list[dict], int, int]:
    """A2: data/**/*.json numeric fields (mechanics-keyed only)."""
    records: list[dict] = []
    jars_scanned = 0
    json_files = 0
    if not mods_dir.is_dir():
        return records, 0, 0
    for jar_name in sorted(os.listdir(mods_dir)):
        low = jar_name.lower()
        if not low.endswith(".jar") or low.endswith(".jar.disabled"):
            continue
        jar_path = mods_dir / jar_name
        jars_scanned += 1
        mod = _mod_from_jar(jar_name)
        try:
            with zipfile.ZipFile(jar_path) as zf:
                names = sorted(
                    n
                    for n in zf.namelist()
                    if n.startswith("data/")
                    and n.endswith(".json")
                    and not n.endswith("/")
                )
                for name in names:
                    json_files += 1
                    try:
                        raw = zf.read(name)
                    except Exception:
                        continue
                    if len(raw) > 400_000:
                        continue
                    try:
                        data = json.loads(raw.decode("utf-8", "replace"))
                    except Exception:
                        continue
                    found: list[tuple[str, Any, str]] = []
                    _walk_json_numbers(data, "", found)
                    for key, val, raw_s in found:
                        hint = unit_hint_from(key)
                        records.append(
                            _record(
                                mod=mod,
                                source_kind="A2",
                                tier="A",
                                file=f"{jar_name}!{name}",
                                key=key,
                                value=val,
                                value_raw=raw_s,
                                unit_hint=hint,
                                line=None,
                                comment="",
                            )
                        )
        except (zipfile.BadZipFile, OSError):
            continue
    return records, jars_scanned, json_files


def scan_mods_lang_b(mods_dir: Path) -> tuple[list[dict], int]:
    """B: numbers in lang / description text (explicit tier B)."""
    records: list[dict] = []
    n_files = 0
    if not mods_dir.is_dir():
        return records, 0
    for jar_name in sorted(os.listdir(mods_dir)):
        low = jar_name.lower()
        if not low.endswith(".jar") or low.endswith(".jar.disabled"):
            continue
        jar_path = mods_dir / jar_name
        mod = _mod_from_jar(jar_name)
        try:
            with zipfile.ZipFile(jar_path) as zf:
                for name in sorted(zf.namelist()):
                    nl = name.lower()
                    want = (
                        "/lang/" in nl
                        or nl.endswith("mcmod.info")
                        or nl.endswith("mods.toml")
                        or nl.endswith("fabric.mod.json")
                    )
                    if not want:
                        continue
                    if not (
                        nl.endswith(".json")
                        or nl.endswith(".lang")
                        or nl.endswith(".toml")
                        or nl.endswith(".info")
                    ):
                        continue
                    n_files += 1
                    try:
                        text = zf.read(name)[:65536].decode("utf-8", "replace")
                    except Exception:
                        continue
                    for lineno, line in enumerate(text.splitlines(), 1):
                        ctx = line.strip()
                        if not re.search(
                            r"(tick|second|rf|fe|mb|joule|energy|burn|rate|speed|duration|block)",
                            ctx,
                            re.I,
                        ):
                            continue
                        km = RE_LANG_NUM.search(ctx)
                        key = (km.group("qkey") or km.group("key")) if km else f"line{lineno}"
                        body = km.group("body") if km else ctx
                        for bm in RE_LANG_BODY_NUM.finditer(body):
                            raw = bm.group("num")
                            val = _parse_number(raw)
                            if val is None:
                                continue
                            records.append(
                                _record(
                                    mod=mod,
                                    source_kind="B",
                                    tier="B",
                                    file=f"{jar_name}!{name}",
                                    key=key,
                                    value=val,
                                    value_raw=raw,
                                    unit_hint=unit_hint_from(ctx),
                                    line=lineno,
                                    comment=_short_comment(ctx),
                                )
                            )
        except (zipfile.BadZipFile, OSError):
            continue
    return records, n_files


def resolve_javap(explicit: Optional[str]) -> Optional[str]:
    if explicit:
        p = Path(explicit)
        return str(p) if p.is_file() else None
    default = Path(DEFAULT_JAVAP)
    if default.is_file():
        return str(default)
    return shutil.which("javap")


def _a1_a2_key_set(records: list[dict]) -> set[str]:
    return {r["key"].lower() for r in records if r["source_kind"] in ("A1", "A2")}


def _list_a3_candidates(mods_dir: Path, limit: int) -> list[tuple[str, str]]:
    """Deterministic (jar, binary_class_name) candidates for gap-fill A3."""
    cands: list[tuple[str, str]] = []
    if not mods_dir.is_dir() or limit <= 0:
        return cands
    for jar_name in sorted(os.listdir(mods_dir)):
        low = jar_name.lower()
        if not low.endswith(".jar") or low.endswith(".jar.disabled"):
            continue
        # Prefer jars likely to hold machine constants missing from config.
        if not re.search(
            r"(rftools|mekanism|thermal|create|immersive|pneumatic|generator)",
            low,
        ):
            continue
        jar_path = mods_dir / jar_name
        try:
            with zipfile.ZipFile(jar_path) as zf:
                for name in sorted(zf.namelist()):
                    if not name.endswith(".class") or "$" in name:
                        continue
                    simple = name.rsplit("/", 1)[-1][:-6]
                    if not RE_A3_CLASS.search(simple):
                        continue
                    bin_name = name[:-6].replace("/", ".")
                    cands.append((jar_name, bin_name))
        except (zipfile.BadZipFile, OSError):
            continue
    return cands[:limit]


def scan_a3(
    mods_dir: Path,
    javap: Optional[str],
    max_classes: int,
    existing_keys: set[str],
    warns: list[str],
) -> tuple[list[dict], int]:
    """A3: javap numeric constants for a bounded class set (gap-fill)."""
    records: list[dict] = []
    used = 0
    if max_classes <= 0:
        return records, 0
    if not javap:
        warns.append("A3 skipped: javap not found")
        return records, 0
    cands = _list_a3_candidates(mods_dir, max_classes * 4)
    if not cands:
        warns.append("A3: no candidate classes")
        return records, 0

    with tempfile.TemporaryDirectory(prefix="mechnum_a3_") as tmp:
        tmp_path = Path(tmp)
        for jar_name, bin_name in cands:
            if used >= max_classes:
                break
            # gap-fill: skip if a same simple key already present from A1/A2
            simple = bin_name.rsplit(".", 1)[-1].lower()
            if any(simple in k for k in existing_keys):
                # still allow if no numeric keys mention this class token — soft skip
                pass
            jar_path = mods_dir / jar_name
            # extract single class tree into tmp for javap
            try:
                with zipfile.ZipFile(jar_path) as zf:
                    class_entry = bin_name.replace(".", "/") + ".class"
                    if class_entry not in zf.namelist():
                        continue
                    target = tmp_path / class_entry
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(zf.read(class_entry))
            except (zipfile.BadZipFile, OSError, KeyError):
                continue
            used += 1
            try:
                proc = subprocess.run(
                    [javap, "-p", "-c", "-constants", bin_name],
                    cwd=str(tmp_path),
                    capture_output=True,
                    timeout=30,
                )
            except (OSError, subprocess.TimeoutExpired) as exc:
                warns.append(f"A3 javap fail {bin_name}: {exc}")
                continue
            text = (proc.stdout or b"").decode("utf-8", "replace")
            if proc.returncode != 0 and not text:
                continue
            method = "<clinit>"
            mod = _mod_from_jar(jar_name)
            for line in text.splitlines():
                mm = RE_JAVAP_METHOD.match(line)
                if mm and "(" in line:
                    method = mm.group(1)
                for nm in RE_JAVAP_NUM.finditer(line):
                    raw = nm.group(1) or nm.group(2)
                    if raw is None:
                        continue
                    val = _parse_number(raw)
                    if val is None:
                        continue
                    # skip tiny opcode noise (iconst_0..5 already filtered by pattern partly)
                    key = f"{bin_name}.{method}"
                    records.append(
                        _record(
                            mod=mod,
                            source_kind="A3",
                            tier="A",
                            file=f"{jar_name}!{bin_name.replace('.', '/')}.class",
                            key=key,
                            value=val,
                            value_raw=str(raw),
                            unit_hint=unit_hint_from(key + " " + line),
                            line=None,
                            comment=_short_comment(line.strip()),
                            class_name=bin_name,
                            method=method,
                        )
                    )
    return records, used


def sort_records(records: list[dict]) -> list[dict]:
    def sk(r: dict) -> tuple:
        return (
            r.get("source_kind") or "",
            r.get("mod") or "",
            r.get("file") or "",
            r.get("key") or "",
            "" if r.get("line") is None else f"{r['line']:08d}",
            r.get("value_raw") or "",
            r.get("class") or "",
            r.get("method") or "",
        )

    return sorted(records, key=sk)


def write_db(path: Path, artifact: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(artifact, ensure_ascii=False, indent=2, sort_keys=True)
    # LF-only for byte-identical cross-run
    data = (payload + "\n").encode("utf-8")
    path.write_bytes(data)


def load_db(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def cmd_build(args: argparse.Namespace) -> int:
    instance = Path(args.instance)
    if not instance.is_dir():
        sys.stderr.write(f"error: --instance not a directory: {instance}\n")
        return 2
    mods = Path(args.mods) if args.mods else instance / "mods"
    out = Path(args.out)
    warns: list[str] = []

    a1, n_cfg = scan_config_tree(instance / "config")
    a2, n_jars, n_json = scan_mods_a2(mods)
    records = list(a1) + list(a2)

    javap = resolve_javap(args.javap)
    a3, a3_used = scan_a3(
        mods, javap, args.max_classes, _a1_a2_key_set(records), warns
    )
    records.extend(a3)

    n_lang = 0
    if args.include_lang:
        b_recs, n_lang = scan_mods_lang_b(mods)
        records.extend(b_recs)

    records = sort_records(records)
    # dedupe exact identity
    seen: set[tuple] = set()
    deduped: list[dict] = []
    for r in records:
        sig = (
            r["source_kind"],
            r["file"],
            r["key"],
            r["value_raw"],
            r["line"],
            r["class"],
            r["method"],
        )
        if sig in seen:
            continue
        seen.add(sig)
        deduped.append(r)

    scanned = {
        "config_files": n_cfg,
        "mods_jars": n_jars,
        "data_json_files": n_json,
        "a3_classes": a3_used,
        "lang_files": n_lang if args.include_lang else 0,
        "include_lang": bool(args.include_lang),
        "warns": sorted(warns),
    }
    counts = {
        "A1": sum(1 for r in deduped if r["source_kind"] == "A1"),
        "A2": sum(1 for r in deduped if r["source_kind"] == "A2"),
        "A3": sum(1 for r in deduped if r["source_kind"] == "A3"),
        "B": sum(1 for r in deduped if r["source_kind"] == "B"),
    }
    artifact = {
        "schema": SCHEMA,
        "instance": instance.name,
        "scanned": scanned,
        "counts": counts,
        "records": deduped,
    }
    write_db(out, artifact)
    print(
        f"OK build instance={instance.name} out={_norm_rel(str(out))} "
        f"config_files={n_cfg} A1={counts['A1']} A2={counts['A2']} "
        f"A3={counts['A3']} B={counts['B']} total={len(deduped)}"
    )
    for w in warns:
        print(f"WARN {w}")
    return 0


def _haystack(r: dict) -> str:
    parts = [
        r.get("mod") or "",
        r.get("file") or "",
        r.get("key") or "",
        r.get("comment") or "",
        r.get("class") or "",
        r.get("method") or "",
        r.get("unit_hint") or "",
        str(r.get("value_raw") or ""),
    ]
    return " ".join(parts).lower()


def query_records(db: dict, terms: str, limit: int) -> list[dict]:
    toks = [t.lower() for t in terms.split() if t.strip()]
    if not toks:
        return []
    hits = []
    for r in db.get("records") or []:
        h = _haystack(r)
        if all(t in h for t in toks):
            hits.append(r)
    return hits[:limit]


def cmd_query(args: argparse.Namespace) -> int:
    db = load_db(Path(args.db))
    hits = query_records(db, args.terms, args.limit)
    if args.json:
        print(json.dumps({"terms": args.terms, "hits": hits}, ensure_ascii=False, indent=2))
    else:
        if not hits:
            print("NO_MATCH")
            print("NO_NUMBER")
        else:
            for r in hits:
                print(
                    f"{r['tier']}/{r['source_kind']} mod={r['mod']} key={r['key']} "
                    f"value={r['value_raw']} file={r['file']} line={r['line']} "
                    f"unit={r['unit_hint']} class={r['class']} method={r['method']}"
                )
    return 0


def cmd_report(args: argparse.Namespace) -> int:
    db = load_db(Path(args.db))
    hits = query_records(db, args.terms, 50) if args.terms else (db.get("records") or [])[:50]
    lines = [
        f"# mech_numbers report — {db.get('instance')}",
        "",
        f"schema={db.get('schema')} total_records={len(db.get('records') or [])}",
        f"scanned={json.dumps(db.get('scanned') or {}, ensure_ascii=False, sort_keys=True)}",
        f"counts={json.dumps(db.get('counts') or {}, ensure_ascii=False, sort_keys=True)}",
        "",
        f"## Hits (terms={args.terms!r})" if args.terms else "## Sample",
        "",
    ]
    if not hits:
        lines.append("NO_MATCH")
        lines.append("NO_NUMBER")
    else:
        for r in hits:
            lines.append(
                f"- [{r['tier']}/{r['source_kind']}] `{r['key']}` = `{r['value_raw']}` "
                f"({r['mod']}) @ `{r['file']}`:{r['line']} unit={r['unit_hint']}"
            )
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(("\n".join(lines) + "\n").encode("utf-8"))
    print(f"OK report out={_norm_rel(str(out))} rows={len(hits)}")
    return 0


def cmd_stats(args: argparse.Namespace) -> int:
    db = load_db(Path(args.db))
    counts = db.get("counts") or {}
    scanned = db.get("scanned") or {}
    print(
        f"instance={db.get('instance')} schema={db.get('schema')} "
        f"total={len(db.get('records') or [])} "
        f"A1={counts.get('A1', 0)} A2={counts.get('A2', 0)} "
        f"A3={counts.get('A3', 0)} B={counts.get('B', 0)} "
        f"config_files={scanned.get('config_files', 0)}"
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(description="Offline mechanics number extractor")
    sub = ap.add_subparsers(dest="cmd", required=True)

    b = sub.add_parser("build", help="Scan instance → JSON db")
    b.add_argument("--instance", required=True, help="Path to <minecraft> dir")
    b.add_argument("--mods", default=None, help="Mods dir (default: <instance>/mods)")
    b.add_argument("--out", required=True, help="Output JSON path (only write target)")
    b.add_argument("--include-lang", action="store_true", help="Also scan lang (tier B)")
    b.add_argument("--javap", default=None, help="javap executable path")
    b.add_argument("--max-classes", type=int, default=8, help="A3 class budget")
    b.set_defaults(func=cmd_build)

    q = sub.add_parser("query", help="Query db by terms")
    q.add_argument("--db", required=True)
    q.add_argument("--terms", required=True)
    q.add_argument("--limit", type=int, default=20)
    q.add_argument("--json", action="store_true")
    q.set_defaults(func=cmd_query)

    r = sub.add_parser("report", help="Write markdown report")
    r.add_argument("--db", required=True)
    r.add_argument("--out", required=True)
    r.add_argument("--terms", default=None)
    r.set_defaults(func=cmd_report)

    s = sub.add_parser("stats", help="Print counts")
    s.add_argument("--db", required=True)
    s.set_defaults(func=cmd_stats)
    return ap


def main(argv: Optional[list[str]] = None) -> int:
    ap = build_parser()
    args = ap.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    sys.exit(main())
