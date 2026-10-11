#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""S-tier source-based mechanics-number extractor (stdlib only).

fetch: resolve git tags, download codeload tar.gz, parse *.java, cache records,
       delete extracted source trees.
build: emit deterministic JSON (S from cache + A3 bytecode fallback).
query: term search over a built DB.

Network (urllib) is used ONLY in fetch when not --offline-cache-only.
Values are from mod source — NOT game-verified.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import io
import json
import os
import re
import shutil
import sys
import tarfile
import tempfile
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from typing import Any, Optional

SCHEMA = 1
NOTE_S = "from mod source; NOT game-verified"
NOTE_A3 = "from bytecode; NOT game-verified"
REPOS_PATH = Path(__file__).resolve().parent / "source_repos.json"

MECH_RE = re.compile(
    r"(?i)(reactor|energy|rate|perTick|tick|fuel|burn|generat|capacit|heat|"
    r"speed|output|consume|usage|power|\beu\b|\brf\b|\bfe\b|\bmb\b)"
)
# Stronger signal: keep 0/1/-1 only when these appear (else treat as bool/ordinal).
MECH_STRONG_RE = re.compile(
    r"(?i)(burn|rate|perTick|capacit|heat|fuel|energy|generat|usage|power|reactor|speed)"
)
BOOLISH_RE = re.compile(
    r"(?i)^(is|has|can|should|allow|enable|disable|use|flag|show|hide)"
)
ENUMISH_RE = re.compile(r"(?i)(ordinal|\$VALUES|\bENUM\b)")
UNIT_RE = re.compile(
    r"(?i)\b(EU/t|FE/t|RF/t|mB/t|ticks?|percent|%)\b"
)

# Java numeric literal incl. underscore separators (1_000D) and hex/bin skipped.
_NUM = r"[-+]?(?:\d[\d_]*\.\d[\d_]*|\.\d[\d_]*|\d[\d_]*)(?:[eE][-+]?\d[\d_]*)?[fFdDlL]?"

# static final TYPE name = LITERAL;
FIELD_INIT_RE = re.compile(
    r"(?m)^\s*(?:(?:public|protected|private|static|final|volatile|transient)\s+)+"
    r"(?:(?:unsigned\s+)?(?:byte|short|int|long|float|double)|[\w.]+)\s+"
    r"(\w+)\s*=\s*"
    rf"({_NUM})\s*;"
    r"([^\n]*)"
)
# .defineInRange("name", LITERAL, ...) / .define("name", LITERAL)
DEFINE_RE = re.compile(
    r"""\.define(?:InRange)?\s*\(\s*["'](\w+)["']\s*,\s*"""
    rf"""({_NUM})"""
)
# static { NAME = LITERAL; } — only when NAME is identifier
STATIC_ASSIGN_RE = re.compile(
    r"(?m)^\s*([A-Z][A-Z0-9_]*)\s*=\s*"
    rf"({_NUM})\s*;"
    r"([^\n]*)"
)
PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([\w.]+)\s*;")
CLASS_RE = re.compile(
    r"(?m)^\s*(?:public\s+|protected\s+|private\s+)?(?:static\s+|final\s+|abstract\s+)*"
    r"(?:class|interface|enum)\s+(\w+)"
)
MC_LINE_RE = re.compile(r"(?:^|[^0-9])(1\.\d+(?:\.\d+)?)")


def _norm_rel(path: str) -> str:
    return path.replace("\\", "/")


def _load_repos() -> dict[str, Any]:
    return json.loads(REPOS_PATH.read_text(encoding="utf-8"))


def _repo_entry(modid: str, repos: dict[str, Any]) -> Optional[dict[str, Any]]:
    return (repos.get("repos") or {}).get(modid)


def _license_ok(entry: dict[str, Any]) -> bool:
    lic = (entry.get("license") or "").strip()
    if not lic or lic.lower() == "unknown":
        return False
    return True


def _parse_num(raw: str) -> tuple[Any, str]:
    s = raw.strip().replace("_", "")
    kind = "int"
    if s[-1:] in "fF":
        kind = "float"
        s = s[:-1]
    elif s[-1:] in "dD":
        kind = "double"
        s = s[:-1]
    elif s[-1:] in "lL":
        kind = "int"
        s = s[:-1]
    elif "." in s or "e" in s.lower():
        kind = "double"
    if kind in ("float", "double"):
        return float(s), kind
    return int(s, 10), "int"


def _unit_from(name: str, comment: str) -> Optional[str]:
    blob = f"{name} {comment}"
    m = UNIT_RE.search(blob)
    if not m:
        # soft hints from name
        low = name.lower()
        if "pertick" in low or low.endswith("rate") or "/t" in comment.lower():
            if "mb" in low or "fuel" in low or "fluid" in low:
                return "mB/t"
            if "eu" in low:
                return "EU/t"
            if "fe" in low or "rf" in low or "energy" in low or "power" in low:
                return "FE/t"
        if "tick" in low:
            return "ticks"
        if "percent" in low or "pct" in low:
            return "%"
        return None
    tok = m.group(1)
    if tok == "%":
        return "%"
    if tok.lower() in ("tick", "ticks"):
        return "ticks"
    if tok.lower() == "percent":
        return "%"
    return tok


def _keep_record(name: str, comment: str, value: Any, value_kind: str) -> bool:
    """Documented skip rules for non-mechanics noise.

    Keep only when field name OR trailing comment matches MECH_RE.
    Skip:
      - enum/ordinal-ish names (ENUMISH_RE)
      - int/long in {0, 1, -1} when boolish name OR lacking MECH_STRONG_RE
        (0/1 boolean-ish flags / ordinals)
    """
    blob = f"{name} {comment}"
    if not MECH_RE.search(blob):
        return False
    if ENUMISH_RE.search(name):
        return False
    if value_kind == "int" and value in (0, 1, -1):
        if BOOLISH_RE.search(name):
            return False
        if not MECH_STRONG_RE.search(blob):
            return False
    return True


def parse_java_text(
    text: str, *, file_rel: str
) -> list[dict[str, Any]]:
    """Extract candidate numeric constants from one .java file (no source stored)."""
    pkg_m = PACKAGE_RE.search(text)
    pkg = pkg_m.group(1) if pkg_m else ""
    cls_m = CLASS_RE.search(text)
    cls = cls_m.group(1) if cls_m else Path(file_rel).stem
    class_fq = f"{pkg}.{cls}" if pkg else cls
    out: list[dict[str, Any]] = []
    seen: set[tuple] = set()

    def add(field: str, raw: str, comment: str, line: int) -> None:
        try:
            value, kind = _parse_num(raw)
        except ValueError:
            return
        if not _keep_record(field, comment, value, kind):
            return
        key = (class_fq, field, value, kind)
        if key in seen:
            return
        seen.add(key)
        out.append(
            {
                "class": class_fq,
                "field": field,
                "value": value,
                "value_kind": kind,
                "unit": _unit_from(field, comment),
                "file": file_rel,
                "line": line,
            }
        )

    for m in FIELD_INIT_RE.finditer(text):
        line = text.count("\n", 0, m.start()) + 1
        add(m.group(1), m.group(2), m.group(3) or "", line)
    for m in DEFINE_RE.finditer(text):
        line = text.count("\n", 0, m.start()) + 1
        add(m.group(1), m.group(2), "", line)
    for m in STATIC_ASSIGN_RE.finditer(text):
        line = text.count("\n", 0, m.start()) + 1
        add(m.group(1), m.group(2), m.group(3) or "", line)
    return out


def parse_java_tree(root: Path) -> tuple[list[dict[str, Any]], dict[str, int]]:
    records: list[dict[str, Any]] = []
    classes = 0
    files = 0
    for dirpath, _dirnames, filenames in os.walk(root):
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            files += 1
            path = Path(dirpath) / fn
            try:
                text = path.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            rel = _norm_rel(str(path.relative_to(root)))
            # strip single top-level folder from github tarball (repo-commit/)
            parts = rel.split("/", 1)
            if len(parts) == 2:
                rel = parts[1]
            got = parse_java_text(text, file_rel=rel)
            if got:
                classes += 1
            records.extend(got)
    return records, {"java_files": files, "classes_with_hits": classes}


# ---------------------------------------------------------------------------
# mods.toml / jar inventory
# ---------------------------------------------------------------------------


def _read_mods_toml(zf: zipfile.ZipFile) -> str:
    for name in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
        try:
            return zf.read(name).decode("utf-8", errors="replace")
        except KeyError:
            continue
    return ""


def _jar_version_fallback(jar_name: str) -> str:
    """Prefer MC-line+modver from filename (e.g. 1.19.2-0.5.1.f)."""
    base = re.sub(r"\.jar$", "", jar_name, flags=re.I)
    # strip common loader suffixes for matching
    base = re.sub(
        r"-(?:forge|fabric|quilt|universal)(?:-|$)",
        "-",
        base,
        flags=re.I,
    ).rstrip("-")
    m = re.search(
        r"(1\.\d+(?:\.\d+)?(?:-[\w.+]+)+)$",
        base,
        re.I,
    )
    if m:
        return m.group(1)
    m = re.search(
        r"(\d+\.\d+(?:\.\d+)*(?:-[0-9A-Za-z.]+)?(?:\.[a-z])?)$",
        base,
        re.I,
    )
    return m.group(1) if m else base


def _mc_hint_from_jar(jar_name: str, version: str) -> str:
    for blob in (jar_name, version):
        m = re.search(r"(1\.19\.\d+|1\.20\.\d+|1\.21\.\d+|1\.18\.\d+|1\.16\.\d+)", blob)
        if m:
            return m.group(1)
    return _mc_line_from_version(version) or "1.19.2"


def list_instance_mods(
    instance: Path, mods_filter: Optional[re.Pattern[str]] = None
) -> list[dict[str, str]]:
    """Return [{modId, version, jar}] from mods/*.jar META-INF/mods.toml."""
    mods_dir = instance / "mods"
    out: list[dict[str, str]] = []
    if not mods_dir.is_dir():
        return out
    for jar_name in sorted(os.listdir(mods_dir)):
        low = jar_name.lower()
        if not low.endswith(".jar") or low.endswith(".jar.disabled"):
            continue
        if mods_filter and not mods_filter.search(jar_name):
            continue
        jar_path = mods_dir / jar_name
        try:
            zf = zipfile.ZipFile(jar_path)
        except (zipfile.BadZipFile, OSError):
            continue
        try:
            raw = _read_mods_toml(zf)
        finally:
            zf.close()
        if not raw:
            continue
        # First [[mods]] block (allow trailing comments on the header line)
        blocks = re.split(r"(?m)^\s*\[\[mods\]\].*$", raw)
        mod_id = ""
        version = ""
        if len(blocks) >= 2:
            body = blocks[1]
            # stop at next table header
            body = re.split(r"(?m)^\s*\[", body, maxsplit=1)[0]
            mi = re.search(r'(?m)^\s*modId\s*=\s*"([^"]+)"', body)
            ver = re.search(r'(?m)^\s*version\s*=\s*"([^"]+)"', body)
            if mi:
                mod_id = mi.group(1)
            if ver:
                version = ver.group(1)
        if not mod_id:
            continue
        if (not version) or "${" in version:
            # MANIFEST then filename
            try:
                zf2 = zipfile.ZipFile(jar_path)
                try:
                    info = zf2.getinfo("META-INF/MANIFEST.MF")
                    mf = zf2.read(info).decode("utf-8", errors="replace")
                finally:
                    zf2.close()
                for key in (
                    "Implementation-Version",
                    "Specification-Version",
                ):
                    mm = re.search(
                        rf"(?mi)^{key}:\s*(\S+)", mf
                    )
                    if mm:
                        version = mm.group(1)
                        break
            except Exception:
                pass
            if (not version) or "${" in version:
                version = _jar_version_fallback(jar_name)
        out.append({"modId": mod_id, "version": version, "jar": jar_name})
    return out


# ---------------------------------------------------------------------------
# GitHub tag resolution + download (fetch only)
# ---------------------------------------------------------------------------


def _http_get(url: str, token: Optional[str] = None) -> bytes:
    headers = {
        "User-Agent": "packai-source-numbers/1.0",
        "Accept": "application/vnd.github+json",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=120) as resp:
        return resp.read()


def _github_token() -> Optional[str]:
    return os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")


def list_tags(repo: str) -> list[str]:
    """Return tag names (newest-first pages flattened)."""
    token = _github_token()
    names: list[str] = []
    page = 1
    while page <= 20:
        url = (
            f"https://api.github.com/repos/{repo}/tags"
            f"?per_page=100&page={page}"
        )
        try:
            data = json.loads(_http_get(url, token).decode("utf-8"))
        except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError):
            break
        if not isinstance(data, list) or not data:
            break
        for item in data:
            if isinstance(item, dict) and item.get("name"):
                names.append(str(item["name"]))
        if len(data) < 100:
            break
        page += 1
    if names:
        return names
    # fallback: matching-refs
    url = f"https://api.github.com/repos/{repo}/git/matching-refs/tags"
    try:
        data = json.loads(_http_get(url, token).decode("utf-8"))
    except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError):
        return []
    if not isinstance(data, list):
        return []
    for item in data:
        ref = item.get("ref") if isinstance(item, dict) else None
        if isinstance(ref, str) and ref.startswith("refs/tags/"):
            names.append(ref[len("refs/tags/") :])
    return names


def resolve_commit_for_ref(repo: str, ref: str) -> Optional[str]:
    token = _github_token()
    # commit URL works for tags and branches
    url = f"https://api.github.com/repos/{repo}/commits/{urllib.request.quote(ref)}"
    try:
        data = json.loads(_http_get(url, token).decode("utf-8"))
    except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError):
        return None
    if isinstance(data, dict) and data.get("sha"):
        return str(data["sha"])
    return None


def default_branch(repo: str) -> str:
    token = _github_token()
    url = f"https://api.github.com/repos/{repo}"
    try:
        data = json.loads(_http_get(url, token).decode("utf-8"))
        if isinstance(data, dict) and data.get("default_branch"):
            return str(data["default_branch"])
    except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError):
        pass
    return "master"


def list_branches(repo: str) -> list[str]:
    token = _github_token()
    names: list[str] = []
    page = 1
    while page <= 5:
        url = (
            f"https://api.github.com/repos/{repo}/branches"
            f"?per_page=100&page={page}"
        )
        try:
            data = json.loads(_http_get(url, token).decode("utf-8"))
        except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError):
            break
        if not isinstance(data, list) or not data:
            break
        for item in data:
            if isinstance(item, dict) and item.get("name"):
                names.append(str(item["name"]))
        if len(data) < 100:
            break
        page += 1
    return names


def _mc_line_from_version(version: str) -> Optional[str]:
    m = MC_LINE_RE.search(version)
    return m.group(1) if m else None


def _version_candidates(jar_version: str) -> list[str]:
    ver = jar_version.strip()
    out = [ver]
    m = re.match(r"1\.\d+(?:\.\d+)?-(.+)$", ver)
    if m:
        out.append(m.group(1))
    # strip trailing letter build like 0.5.1.f → 0.5.1
    for v in list(out):
        m2 = re.match(r"^(\d+\.\d+\.\d+)\.[a-z]$", v, re.I)
        if m2:
            out.append(m2.group(1))
    # unique preserve order
    seen: set[str] = set()
    uniq: list[str] = []
    for c in out:
        if c and c not in seen:
            seen.add(c)
            uniq.append(c)
    return uniq


def pick_ref(
    tags: list[str],
    jar_version: str,
    mc_hint: Optional[str] = None,
    branches: Optional[list[str]] = None,
) -> tuple[str, str]:
    """Return (ref, match_quality) where quality ∈ exact_tag|nearest_tag|branch.

    exact_tag: tag string contains jar version (or trailing modver segment).
    nearest_tag: tag on same MC line (e.g. 1.19.2) — first match in API order.
    branch: best versioned branch on MC line, else empty (caller → default_branch).
    """
    if not jar_version:
        return "", "branch"
    candidates = _version_candidates(jar_version)
    for cand in candidates:
        for tag in tags:
            if cand and cand in tag:
                return tag, "exact_tag"
    mc = mc_hint or _mc_line_from_version(jar_version) or "1.19.2"
    for tag in tags:
        if mc in tag:
            return tag, "nearest_tag"
    mc_alt = f"mc{mc}"
    for tag in tags:
        if mc_alt in tag:
            return tag, "nearest_tag"
    # MC minor line without patch: 1.19.2 → 1.19
    mc_minor = ".".join(mc.split(".")[:2]) if mc.count(".") >= 1 else mc
    for tag in tags:
        if f"mc{mc_minor}" in tag or mc_minor in tag:
            return tag, "nearest_tag"

    # Branches (Create deleted old tags; mc1.19/0.5.1 is the honest ref)
    brs = branches or []
    for cand in candidates:
        for br in brs:
            if cand in br and (mc in br or mc_minor in br or f"mc{mc_minor}" in br):
                return br, "branch"
    for br in brs:
        if mc in br or f"mc{mc}" in br or f"mc{mc_minor}" in br:
            # prefer non-default-looking versioned branches first
            if any(c in br for c in candidates):
                return br, "branch"
    for br in brs:
        if f"mc{mc_minor}" in br or mc in br:
            return br, "branch"
    return "", "branch"


def cache_key(repo: str, commit: str) -> str:
    flat = repo.replace("/", "_")
    return f"{flat}@{commit}.json"


def cache_path(out_dir: Path, repo: str, commit: str) -> Path:
    return out_dir / cache_key(repo, commit)


def download_and_parse(
    repo: str, commit: str, out_dir: Path
) -> dict[str, Any]:
    """Download codeload tar.gz, parse java, delete tree; write cache JSON."""
    cp = cache_path(out_dir, repo, commit)
    if cp.is_file():
        return json.loads(cp.read_text(encoding="utf-8"))

    url = f"https://codeload.github.com/{repo}/tar.gz/{commit}"
    raw = _http_get(url, _github_token())
    tmp = Path(tempfile.mkdtemp(prefix="srcnum_"))
    try:
        with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as tf:
            # safe extract: no absolute / parent paths
            for m in tf.getmembers():
                name = m.name
                if name.startswith("/") or ".." in Path(name).parts:
                    continue
                tf.extract(m, path=tmp)
        records, stats = parse_java_tree(tmp)
        artifact = {
            "schema": SCHEMA,
            "repo": repo,
            "commit": commit,
            "stats": stats,
            "records": records,
            "note": NOTE_S,
        }
        _write_json(cp, artifact)
        return artifact
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def _write_json(path: Path, obj: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(obj, ensure_ascii=False, indent=2, sort_keys=True)
    path.write_bytes((payload + "\n").encode("utf-8"))


def write_db(path: Path, artifact: dict) -> str:
    """Write DB; return sha256 hex of bytes written."""
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(artifact, ensure_ascii=False, indent=2, sort_keys=True)
    data = (payload + "\n").encode("utf-8")
    path.write_bytes(data)
    return hashlib.sha256(data).hexdigest()


def load_db(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def sort_s_records(records: list[dict]) -> list[dict]:
    def sk(r: dict) -> tuple:
        v = r.get("value")
        if isinstance(v, float):
            vkey = ("f", f"{v:.17g}")
        else:
            vkey = ("i", str(v))
        return (
            r.get("mod") or "",
            r.get("class") or "",
            r.get("field") or "",
            r.get("value_kind") or "",
            vkey,
            r.get("file") or "",
            int(r.get("line") or 0),
            r.get("tier") or "",
        )

    return sorted(records, key=sk)


# ---------------------------------------------------------------------------
# bytecode fallback
# ---------------------------------------------------------------------------


def _load_bytecode_mod():
    path = Path(__file__).resolve().parent / "bytecode_numbers.py"
    spec = importlib.util.spec_from_file_location("bytecode_numbers", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot load bytecode_numbers.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def bytecode_fallback(
    instance: Path, jar_names: list[str]
) -> list[dict[str, Any]]:
    if not jar_names:
        return []
    bc = _load_bytecode_mod()
    # Filter jars by exact names via regex alternation
    escaped = "|".join(re.escape(j) for j in sorted(set(jar_names)))
    filt = re.compile(f"^(?:{escaped})$")
    result = bc.scan_instance(instance, instance / "mods", filt, max_classes=0)
    records = []
    for r in result.get("records") or []:
        row = dict(r)
        row["tier"] = "A3"
        row["note"] = NOTE_A3
        row["fallback"] = "bytecode"
        records.append(row)
    return records


# ---------------------------------------------------------------------------
# fetch / build / query
# ---------------------------------------------------------------------------


def cmd_fetch(args: argparse.Namespace) -> int:
    instance = Path(args.instance)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    offline = bool(args.offline_cache_only)
    filt = re.compile(args.mods_filter) if args.mods_filter else None
    repos_doc = _load_repos()
    mods = list_instance_mods(instance, filt)

    index: dict[str, Any] = {
        "schema": SCHEMA,
        "instance": instance.name,
        "offline_cache_only": offline,
        "mods": {},
    }
    fetched = 0
    skipped = 0
    unresolved = 0

    # Deduplicate by repo@resolved so monorepo shared once
    for mod in mods:
        modid = mod["modId"]
        entry = _repo_entry(modid, repos_doc)
        if entry is None:
            index["mods"][modid] = {
                "status": "source_skipped",
                "reason": "not_in_repo_map",
                "jar": mod["jar"],
                "jar_version": mod["version"],
            }
            skipped += 1
            continue
        if not _license_ok(entry):
            index["mods"][modid] = {
                "status": "source_skipped",
                "reason": "license_unknown_or_absent",
                "license": entry.get("license"),
                "repo": entry.get("repo"),
                "jar": mod["jar"],
                "jar_version": mod["version"],
            }
            skipped += 1
            continue

        repo = entry["repo"]
        jar_ver = mod["version"]
        mc_hint = _mc_hint_from_jar(mod["jar"], jar_ver)
        if offline:
            # Find any cache file for this repo
            prefix = repo.replace("/", "_") + "@"
            matches = sorted(out_dir.glob(prefix + "*.json"))
            # Prefer index prior resolution if present
            prior = None
            idx_path = out_dir / "fetch_index.json"
            if idx_path.is_file():
                try:
                    prior_doc = json.loads(idx_path.read_text(encoding="utf-8"))
                    prior = (prior_doc.get("mods") or {}).get(modid)
                except (json.JSONDecodeError, OSError):
                    prior = None
            if prior and prior.get("commit"):
                cp = cache_path(out_dir, repo, prior["commit"])
                if cp.is_file():
                    index["mods"][modid] = {
                        "status": "cached",
                        "repo": repo,
                        "ref": prior.get("ref"),
                        "commit": prior["commit"],
                        "match_quality": prior.get("match_quality"),
                        "jar_version": jar_ver,
                        "license": entry.get("license"),
                        "cache": cache_key(repo, prior["commit"]),
                    }
                    fetched += 1
                    continue
            if matches:
                # use first alphabetically stable cache
                commit = matches[0].stem.split("@", 1)[-1]
                meta = json.loads(matches[0].read_text(encoding="utf-8"))
                index["mods"][modid] = {
                    "status": "cached",
                    "repo": repo,
                    "ref": commit[:12],
                    "commit": meta.get("commit") or commit,
                    "match_quality": "branch",
                    "jar_version": jar_ver,
                    "license": entry.get("license"),
                    "cache": matches[0].name,
                    "note": "offline: used existing cache; match_quality not re-resolved",
                }
                fetched += 1
                continue
            index["mods"][modid] = {
                "status": "source_unresolved",
                "reason": "offline_cache_miss",
                "repo": repo,
                "jar_version": jar_ver,
            }
            unresolved += 1
            continue

        # online resolve
        try:
            tags = list_tags(repo)
            branches = list_branches(repo)
            # Prefer filename-derived version when MANIFEST is short (Create 0.5.1.f)
            jar_ver_for_match = jar_ver
            fb = _jar_version_fallback(mod["jar"])
            if fb and fb != jar_ver and (
                jar_ver.count(".") < fb.count(".") or len(fb) > len(jar_ver)
            ):
                jar_ver_for_match = fb
            ref, quality = pick_ref(
                tags, jar_ver_for_match, mc_hint=mc_hint, branches=branches
            )
            if not ref:
                ref = default_branch(repo)
                quality = "branch"
            commit = resolve_commit_for_ref(repo, ref)
            if not commit:
                index["mods"][modid] = {
                    "status": "source_unresolved",
                    "reason": f"commit_unresolved:{ref}",
                    "repo": repo,
                    "ref": ref,
                    "match_quality": quality,
                    "jar_version": jar_ver,
                }
                unresolved += 1
                continue
            art = download_and_parse(repo, commit, out_dir)
            index["mods"][modid] = {
                "status": "fetched",
                "repo": repo,
                "ref": ref,
                "commit": commit,
                "match_quality": quality,
                "jar_version": jar_ver,
                "license": entry.get("license"),
                "cache": cache_key(repo, commit),
                "stats": art.get("stats"),
            }
            fetched += 1
        except Exception as exc:
            index["mods"][modid] = {
                "status": "source_unresolved",
                "reason": f"fetch_error:{type(exc).__name__}:{exc}",
                "repo": repo,
                "jar_version": jar_ver,
            }
            unresolved += 1

    _write_json(out_dir / "fetch_index.json", index)
    print(
        f"OK fetch instance={instance.name} out_dir={_norm_rel(str(out_dir))} "
        f"fetched={fetched} skipped={skipped} unresolved={unresolved} "
        f"offline={offline}"
    )
    for mid, info in sorted(index["mods"].items()):
        st = info.get("status")
        if st == "source_skipped":
            print(f"source_skipped:{mid}:{info.get('reason')}")
        elif st == "source_unresolved":
            print(f"source_unresolved:{mid}:{info.get('reason')}")
    return 0


def cmd_build(args: argparse.Namespace) -> int:
    instance = Path(args.instance)
    if not instance.is_dir():
        sys.stderr.write(f"error: --instance not a directory: {instance}\n")
        return 2
    out = Path(args.out)
    # Cache dir: sibling of out or --cache-dir
    cache_dir = Path(args.cache_dir) if args.cache_dir else out.parent / "source_cache"
    filt = re.compile(args.mods_filter) if args.mods_filter else None
    repos_doc = _load_repos()
    mods = list_instance_mods(instance, filt)

    index_path = cache_dir / "fetch_index.json"
    fetch_index: dict[str, Any] = {}
    if index_path.is_file():
        fetch_index = json.loads(index_path.read_text(encoding="utf-8"))

    s_records: list[dict[str, Any]] = []
    source_skipped: list[dict[str, str]] = []
    source_unresolved: list[dict[str, str]] = []
    fallback_jars: list[str] = []
    per_mod_s: dict[str, int] = {}
    parsed_classes = 0
    parsed_fields = 0

    for mod in mods:
        modid = mod["modId"]
        entry = _repo_entry(modid, repos_doc)
        if entry is None:
            reason = "not_in_repo_map"
            source_skipped.append({"mod": modid, "reason": reason})
            print(f"source_skipped:{modid}:{reason}")
            fallback_jars.append(mod["jar"])
            continue
        if not _license_ok(entry):
            reason = "license_unknown_or_absent"
            source_skipped.append(
                {"mod": modid, "reason": reason, "license": str(entry.get("license"))}
            )
            print(f"source_skipped:{modid}:{reason}")
            fallback_jars.append(mod["jar"])
            continue

        info = (fetch_index.get("mods") or {}).get(modid) or {}
        repo = entry["repo"]
        commit = info.get("commit")
        ref = info.get("ref")
        quality = info.get("match_quality")
        license_s = entry.get("license")
        jar_ver = mod["version"]

        if not commit:
            # try any cache for repo
            prefix = repo.replace("/", "_") + "@"
            matches = sorted(cache_dir.glob(prefix + "*.json"))
            if matches:
                meta = json.loads(matches[0].read_text(encoding="utf-8"))
                commit = meta.get("commit") or matches[0].stem.split("@", 1)[-1]
                ref = ref or commit[:12]
                quality = quality or "branch"
            else:
                reason = "no_cache_or_fetch"
                source_unresolved.append({"mod": modid, "reason": reason})
                print(f"source_unresolved:{modid}:{reason}")
                fallback_jars.append(mod["jar"])
                continue

        cp = cache_path(cache_dir, repo, commit)
        if not cp.is_file():
            reason = f"cache_missing:{cache_key(repo, commit)}"
            source_unresolved.append({"mod": modid, "reason": reason})
            print(f"source_unresolved:{modid}:{reason}")
            fallback_jars.append(mod["jar"])
            continue

        art = json.loads(cp.read_text(encoding="utf-8"))
        stats = art.get("stats") or {}
        parsed_classes += int(stats.get("classes_with_hits") or 0)
        raw_recs = art.get("records") or []
        parsed_fields += len(raw_recs)
        n = 0
        for r in raw_recs:
            s_records.append(
                {
                    "tier": "S",
                    "mod": modid,
                    "class": r["class"],
                    "field": r["field"],
                    "value": r["value"],
                    "value_kind": r["value_kind"],
                    "unit": r.get("unit"),
                    "repo": repo,
                    "ref": ref,
                    "commit": commit,
                    "file": r["file"],
                    "line": r["line"],
                    "jar_version": jar_ver,
                    "match_quality": quality,
                    "license": license_s,
                    "note": NOTE_S,
                }
            )
            n += 1
        per_mod_s[modid] = per_mod_s.get(modid, 0) + n
        print(f"S-tier {modid}: {n} records (repo={repo}@{commit[:12]} quality={quality})")

    a3_records = bytecode_fallback(instance, fallback_jars)
    all_records = sort_s_records(s_records) + _sort_a3(a3_records)

    by_mod: dict[str, int] = {}
    by_tier: dict[str, int] = {"S": 0, "A3": 0}
    for r in all_records:
        by_mod[r["mod"]] = by_mod.get(r["mod"], 0) + 1
        t = r.get("tier") or "?"
        by_tier[t] = by_tier.get(t, 0) + 1

    artifact = {
        "schema": SCHEMA,
        "instance": instance.name,
        "note": "S=source A3=bytecode fallback; NOT game-verified",
        "source_skipped": sorted(source_skipped, key=lambda x: x["mod"]),
        "source_unresolved": sorted(source_unresolved, key=lambda x: x["mod"]),
        "stats": {
            "cache_classes_with_hits_sum": parsed_classes,
            "cache_fields_sum": parsed_fields,
            "s_records": len(s_records),
            "a3_fallback_records": len(a3_records),
            "fallback_jars": sorted(set(fallback_jars)),
        },
        "counts": {
            "total": len(all_records),
            "by_mod": dict(sorted(by_mod.items())),
            "by_tier": dict(sorted(by_tier.items())),
            "s_by_mod": dict(sorted(per_mod_s.items())),
        },
        "records": all_records,
    }
    digest = write_db(out, artifact)
    print(
        f"OK build instance={instance.name} out={_norm_rel(str(out))} "
        f"S={len(s_records)} A3={len(a3_records)} total={len(all_records)} "
        f"sha256={digest}"
    )
    print(
        "source_skipped_list "
        + json.dumps(artifact["source_skipped"], ensure_ascii=False)
    )
    print(
        "source_unresolved_list "
        + json.dumps(artifact["source_unresolved"], ensure_ascii=False)
    )
    return 0


def _sort_a3(records: list[dict]) -> list[dict]:
    bc = _load_bytecode_mod()
    return bc.sort_records(records)


def _haystack(r: dict) -> str:
    parts = [
        r.get("mod") or "",
        r.get("class") or "",
        r.get("field") or "",
        r.get("owner") or "",
        r.get("unit") or "",
        r.get("unit_hint") or "",
        r.get("note") or "",
        r.get("repo") or "",
        r.get("file") or "",
        str(r.get("value") if r.get("value") is not None else ""),
        r.get("value_kind") or "",
        r.get("tier") or "",
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


def format_s_line(r: dict) -> str:
    unit = r.get("unit") or ""
    u = f" {unit}" if unit else ""
    return (
        f"S {r['mod']} {r['class']}#{r['field']} = {r['value']}{u} "
        f"({r['value_kind']}) "
        f"{r.get('repo')}@{r.get('commit')}:{r.get('file')}:{r.get('line')} "
        f"match_quality={r.get('match_quality')} "
        f"jar_version={r.get('jar_version')} "
        f"note={r.get('note')}"
    )


def cmd_query(args: argparse.Namespace) -> int:
    db = load_db(Path(args.db))
    hits = query_records(db, args.terms, args.limit)
    if args.json:
        print(
            json.dumps(
                {"terms": args.terms, "hits": hits},
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            )
        )
    else:
        if not hits:
            print("NO_MATCH")
            print("NO_NUMBER")
        else:
            for r in hits:
                if r.get("tier") == "S":
                    print(format_s_line(r))
                else:
                    print(
                        f"A3/bytecode mod={r.get('mod')} "
                        f"{r.get('class')}#{r.get('owner') or r.get('field')} "
                        f"value={r.get('value')} ({r.get('value_kind')}) "
                        f"note={r.get('note')}"
                    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(
        description="S-tier source mechanics-number extractor (unverified)"
    )
    sub = ap.add_subparsers(dest="cmd", required=True)

    f = sub.add_parser("fetch", help="Resolve tags, download source, cache parsed records")
    f.add_argument("--instance", required=True)
    f.add_argument("--out-dir", required=True)
    f.add_argument("--mods-filter", default=None)
    f.add_argument(
        "--offline-cache-only",
        action="store_true",
        help="Use only existing cache; no network",
    )
    f.set_defaults(func=cmd_fetch)

    b = sub.add_parser("build", help="Build JSON db from cache + bytecode fallback")
    b.add_argument("--instance", required=True)
    b.add_argument("--out", required=True)
    b.add_argument(
        "--cache-dir",
        default=None,
        help="Fetch cache dir (default: <out-parent>/source_cache)",
    )
    b.add_argument("--mods-filter", default=None)
    b.set_defaults(func=cmd_build)

    q = sub.add_parser("query", help="Query db by terms")
    q.add_argument("--db", required=True)
    q.add_argument("--terms", required=True)
    q.add_argument("--limit", type=int, default=20)
    q.add_argument("--json", action="store_true")
    q.set_defaults(func=cmd_query)
    return ap


def main(argv: Optional[list[str]] = None) -> int:
    ap = build_parser()
    args = ap.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    sys.exit(main())
