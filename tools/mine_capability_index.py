#!/usr/bin/env python3
"""Offline pack capability index: curated needs → candidate mods + bytecode/lang evidence.

Stdlib + javap only. See docs/plans/2026-10-10-capability-index-offline.md.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from pathlib import Path

JAR_TEXT_EXACT = {
    "META-INF/mods.toml",
    "mcmod.info",
    "fabric.mod.json",
}
TEXT_HEAD = 65536
PUSH_NUM = re.compile(r"\b(?:sipush|bipush|ldc)\b.*\b(\d+)\b")


def resolve_javap(explicit: str | None) -> str:
    if explicit:
        p = Path(explicit)
        if not p.is_file():
            sys.stderr.write(f"error: --javap not a file: {explicit}\n")
            sys.exit(2)
        return str(p)
    found = shutil.which("javap")
    if found:
        return found
    sys.stderr.write(
        "error: javap not found (pass --javap <path> or add javap to PATH)\n"
    )
    sys.exit(2)


def javap_version_line(javap: str) -> str:
    try:
        p = subprocess.run(
            [javap, "-version"],
            capture_output=True,
            timeout=20,
        )
        raw = (p.stderr or p.stdout or b"").decode("utf-8", "replace").strip()
        return raw.splitlines()[0] if raw else "?"
    except Exception as e:
        return f"? ({e})"


def list_mod_jars(mods_dir: str) -> list[str]:
    out = []
    for name in sorted(os.listdir(mods_dir)):
        if name.lower().endswith(".jar") and not name.lower().endswith(".jar.disabled"):
            out.append(name)
    return out


def is_jar_text_path(path: str) -> bool:
    if path in JAR_TEXT_EXACT:
        return True
    if path.startswith("assets/") and "/lang/" in path:
        base = path.rsplit("/", 1)[-1].lower()
        return base in ("en_us.json", "en_us.lang", "zh_cn.lang")
    return False


def keyword_hits_in_text(text, keywords):
    """Return (keyword, 1-based line, line_text) for substring hits (case-insensitive)."""
    hits = []
    lines = text.splitlines()
    lower_lines = [ln.lower() for ln in lines]
    for kw in keywords:
        k = kw.lower()
        for i, ll in enumerate(lower_lines):
            if k in ll:
                hits.append((kw, i + 1, lines[i][:500]))
    return hits


def scan_jar_candidate(mods_dir, jar_name, keywords):
    """Return (distinct_keyword_hit_count, evidence list)."""
    evidence = []
    hit_kws = set()
    jar_lower = jar_name.lower()
    filename_matched = False
    for kw in keywords:
        if kw.lower() in jar_lower:
            hit_kws.add(kw)
            filename_matched = True
    if filename_matched:
        evidence.append({"file": jar_name, "line": 0, "text": "filename match"})

    jar_path = os.path.join(mods_dir, jar_name)
    try:
        with zipfile.ZipFile(jar_path) as zf:
            for entry in zf.namelist():
                if not is_jar_text_path(entry):
                    continue
                try:
                    raw = zf.read(entry)[:TEXT_HEAD]
                except Exception:
                    continue
                text = raw.decode("utf-8", "replace")
                for kw, line, line_text in keyword_hits_in_text(text, keywords):
                    hit_kws.add(kw)
                    evidence.append(
                        {"file": entry, "line": line, "text": line_text}
                    )
    except (zipfile.BadZipFile, OSError):
        return 0, []

    evidence = _dedupe_evidence(evidence)
    evidence.sort(key=lambda e: (e["file"], e["line"], e["text"]))
    return len(hit_kws), evidence


def _dedupe_evidence(rows):
    seen = set()
    out = []
    for r in rows:
        key = (r["file"], r["line"], r["text"])
        if key in seen:
            continue
        seen.add(key)
        out.append(r)
    return out


def simple_class_match_name(class_path: str) -> str:
    """Strip .class, package, and $inner suffix."""
    name = class_path
    if name.endswith(".class"):
        name = name[:-6]
    simple = name.split("/")[-1]
    if "$" in simple:
        simple = simple.split("$", 1)[0]
    return simple


def match_classes_in_jar(
    mods_dir: str, jar_name: str, class_tokens: list[str], limit: int
) -> list[str]:
    tokens_l = [t.lower() for t in class_tokens]
    matched: list[str] = []
    jar_path = os.path.join(mods_dir, jar_name)
    try:
        with zipfile.ZipFile(jar_path) as zf:
            for entry in sorted(zf.namelist()):
                if not entry.endswith(".class"):
                    continue
                simple = simple_class_match_name(entry)
                sl = simple.lower()
                if any(t in sl for t in tokens_l):
                    matched.append(entry)
    except (zipfile.BadZipFile, OSError):
        return []
    return matched[:limit]


def expand_with_inners(all_entries: set[str], selected: list[str]) -> list[str]:
    """Ensure X$*.class inners accompany each selected outer/inner path."""
    out: list[str] = []
    seen: set[str] = set()
    for path in selected:
        base = path[:-6] if path.endswith(".class") else path
        # outer base (before first $)
        outer = base.split("$", 1)[0]
        group = [path]
        for e in all_entries:
            if e == path:
                continue
            if e.startswith(outer + "$") and e.endswith(".class"):
                group.append(e)
        for g in sorted(group):
            if g not in seen:
                seen.add(g)
                out.append(g)
    return out


def parse_javap_output(text, numeric_patterns, logic_patterns):
    numbers = []
    logic = []
    num_l = [p.lower() for p in numeric_patterns]
    log_l = [p.lower() for p in logic_patterns]
    current_method = "?"
    for i, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        # method declaration heuristic
        if (
            stripped.endswith(";")
            and "(" in stripped
            and ")" in stripped
            and not stripped.startswith("//")
            and " = " not in stripped
        ):
            # e.g. "public int getInventoryStackLimit();"
            if not stripped.startswith(".") and "Code:" not in stripped:
                current_method = stripped.rstrip(";")
        if stripped.startswith("// Method"):
            current_method = stripped

        ll = line.lower()
        num_hit = any(p in ll for p in num_l) or bool(PUSH_NUM.search(line))
        if num_hit and re.search(r"\d", line):
            numbers.append(
                {"method": current_method, "line": i, "text": line.rstrip()[:500]}
            )
        if any(p in ll for p in log_l):
            logic.append(
                {"method": current_method, "line": i, "text": line.rstrip()[:500]}
            )
    numbers.sort(key=lambda x: (x["line"], x["text"]))
    logic.sort(key=lambda x: (x["line"], x["text"]))
    return numbers, logic


class JavapBudget:
    def __init__(
        self, max_calls: int, timeout: float, wall_timeout_s: float, t0: float
    ):
        self.max_calls = max_calls
        self.timeout = timeout
        self.wall_timeout_s = wall_timeout_s
        self.t0 = t0
        self.used = 0
        self.caps_hit = False
        self.timeout_hit = False

    def wall_exceeded(self) -> bool:
        # Cap uses --max-javap-calls + wall-clock --timeout-s.
        if self.wall_timeout_s <= 0 or (
            time.monotonic() - self.t0 > self.wall_timeout_s
        ):
            self.timeout_hit = True
            return True
        return False

    def run(
        self, javap: str, class_bin_name: str, cwd: str
    ) -> tuple[str | None, str | None]:
        """Returns (stdout_text, error_or_None)."""
        if self.wall_exceeded():
            return None, "timeout_hit"
        if self.used >= self.max_calls:
            self.caps_hit = True
            return None, "caps_hit"
        self.used += 1
        if self.used >= self.max_calls:
            self.caps_hit = True
        try:
            p = subprocess.run(
                [javap, "-p", "-c", class_bin_name],
                cwd=cwd,
                capture_output=True,
                timeout=self.timeout,
            )
            out = (p.stdout or b"").decode("utf-8", "replace")
            err = (p.stderr or b"").decode("utf-8", "replace")
            if p.returncode != 0 and not out.strip():
                return None, err.strip() or f"javap rc={p.returncode}"
            return out, None
        except subprocess.TimeoutExpired:
            return None, "timeout"
        except OSError as e:
            return None, str(e)


def javap_classes(
    mods_dir: str,
    jar_name: str,
    class_paths: list[str],
    numeric_patterns: list[str],
    logic_patterns: list[str],
    budget: JavapBudget,
    javap: str,
):
    classes_out = []
    unresolved = []
    jar_path = os.path.join(mods_dir, jar_name)
    tmp = tempfile.mkdtemp(prefix="packai_cap_")
    try:
        with zipfile.ZipFile(jar_path) as zf:
            all_entries = set(zf.namelist())
            expanded = expand_with_inners(all_entries, class_paths)
            for cp in expanded:
                if cp not in all_entries:
                    unresolved.append(f"{jar_name}:{cp}:missing")
                    continue
                try:
                    zf.extract(cp, tmp)
                except Exception as e:
                    unresolved.append(f"{jar_name}:{cp}:extract:{e}")
                    continue
                bin_name = cp[:-6].replace("/", ".")
                text, err = budget.run(javap, bin_name, tmp)
                if err or text is None:
                    unresolved.append(f"{jar_name}:{cp}:{err or 'empty'}")
                    continue
                numbers, logic = parse_javap_output(
                    text, numeric_patterns, logic_patterns
                )
                classes_out.append(
                    {"name": cp, "numbers": numbers, "logic": logic}
                )
    except (zipfile.BadZipFile, OSError) as e:
        unresolved.append(f"{jar_name}:jar:{e}")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    classes_out.sort(key=lambda c: c["name"])
    return classes_out, unresolved


def read_lang_hits(mods_dir, jar_name, keywords, limit=40):
    """Return (lang rows, lang_format_detected)."""
    rows = []
    fmt = ""
    jar_path = os.path.join(mods_dir, jar_name)
    try:
        with zipfile.ZipFile(jar_path) as zf:
            for entry in sorted(zf.namelist()):
                if not (entry.startswith("assets/") and "/lang/" in entry):
                    continue
                base = entry.rsplit("/", 1)[-1].lower()
                if base not in ("en_us.json", "en_us.lang"):
                    continue
                try:
                    raw = zf.read(entry)[:TEXT_HEAD]
                except Exception:
                    continue
                text = raw.decode("utf-8", "replace")
                if base.endswith(".json"):
                    if not fmt:
                        fmt = "json"
                    try:
                        obj = json.loads(text)
                    except Exception:
                        continue
                    if isinstance(obj, dict):
                        for k in sorted(obj.keys()):
                            v = obj[k]
                            if not isinstance(v, str):
                                continue
                            blob = f"{k}={v}"
                            if any(kw.lower() in blob.lower() for kw in keywords):
                                rows.append(
                                    {"file": entry, "key": k, "text": v[:500]}
                                )
                                if len(rows) >= limit:
                                    return rows, fmt or "json"
                else:
                    if not fmt:
                        fmt = "lang"
                    for line in text.splitlines():
                        if "=" not in line or line.strip().startswith("#"):
                            continue
                        k, _, v = line.partition("=")
                        blob = line
                        if any(kw.lower() in blob.lower() for kw in keywords):
                            rows.append(
                                {
                                    "file": entry,
                                    "key": k.strip(),
                                    "text": v.strip()[:500],
                                }
                            )
                            if len(rows) >= limit:
                                return rows, fmt or "lang"
    except (zipfile.BadZipFile, OSError):
        return [], fmt
    rows.sort(key=lambda r: (r["file"], r["key"], r["text"]))
    return rows, fmt


def _grep_file_keywords(path, rel, keywords, limit):
    out = []
    try:
        with open(path, "rb") as f:
            raw = f.read(TEXT_HEAD * 4)
        text = raw.decode("utf-8", "replace")
    except OSError:
        return out
    for kw, line, line_text in keyword_hits_in_text(text, keywords):
        out.append({"file": rel.replace("\\", "/"), "line": line, "text": line_text})
        if len(out) >= limit:
            break
    return out


def collect_cross(mods_dir, jar_name, keywords, game_dir):
    cross = {
        "scripts": [],
        "quests": [],
        "recipes": [],
    }
    if not game_dir:
        # still scan jar recipes/tags
        pass
    else:
        # scripts
        for root_name, bucket in (
            ("scripts", "scripts"),
            ("kubejs", "scripts"),
        ):
            root = os.path.join(game_dir, root_name)
            if not os.path.isdir(root):
                continue
            for dirpath, _dirs, files in os.walk(root):
                for fn in sorted(files):
                    if root_name == "scripts" and not fn.endswith(".zs"):
                        continue
                    if root_name == "kubejs" and not fn.endswith(".js"):
                        continue
                    full = os.path.join(dirpath, fn)
                    rel = os.path.relpath(full, game_dir)
                    cross["scripts"].extend(
                        _grep_file_keywords(full, rel, keywords, 20)
                    )

        bq = os.path.join(game_dir, "config", "betterquesting", "DefaultQuests.json")
        if os.path.isfile(bq):
            cross["quests"].extend(
                _grep_file_keywords(
                    bq, "config/betterquesting/DefaultQuests.json", keywords, 40
                )
            )
        ftb = os.path.join(game_dir, "config", "ftbquests")
        if os.path.isdir(ftb):
            for dirpath, _dirs, files in os.walk(ftb):
                for fn in sorted(files):
                    if not fn.endswith((".snbt", ".nbt", ".json", ".txt")):
                        continue
                    full = os.path.join(dirpath, fn)
                    rel = os.path.relpath(full, game_dir)
                    cross["quests"].extend(
                        _grep_file_keywords(full, rel, keywords, 20)
                    )

    # jar recipes / tags
    jar_path = os.path.join(mods_dir, jar_name)
    try:
        with zipfile.ZipFile(jar_path) as zf:
            for entry in sorted(zf.namelist()):
                el = entry.lower()
                if not (
                    el.startswith("data/")
                    and (
                        "/recipes/" in el
                        or "/tags/" in el
                    )
                    and el.endswith(".json")
                ):
                    continue
                try:
                    raw = zf.read(entry)[:TEXT_HEAD]
                except Exception:
                    continue
                text = raw.decode("utf-8", "replace")
                for _kw, line, line_text in keyword_hits_in_text(text, keywords):
                    cross["recipes"].append(
                        {"file": entry, "line": line, "text": line_text}
                    )
                    if len(cross["recipes"]) >= 40:
                        break
                if len(cross["recipes"]) >= 40:
                    break
    except (zipfile.BadZipFile, OSError):
        pass

    for k in cross:
        cross[k] = _dedupe_evidence(cross[k])
        cross[k].sort(key=lambda e: (e["file"], e["line"], e["text"]))
    return cross


def evidence_runtime_visible(evidence, lang, cross, lang_format):
    """true only for 1.19.2-style runtime surfaces (JSON lang, data, kubejs, ftbquests…)."""
    # .lang format is NOT runtime-visible for PackIndex
    paths: list[str] = []
    for e in evidence:
        paths.append(str(e.get("file", "")))
    for e in lang:
        paths.append(str(e.get("file", "")))
    for bucket in cross.values():
        for e in bucket:
            paths.append(str(e.get("file", "")))

    for p in paths:
        pl = p.replace("\\", "/").lower()
        if pl.endswith(".lang") and "/lang/" in pl:
            continue  # 1.12.2 lang — offline only
        if "/lang/" in pl and pl.endswith(".json"):
            return True
        if pl.startswith("data/") and ("/tags/" in pl or "/recipes/" in pl):
            return True
        if "kubejs/" in pl or pl.startswith("kubejs/"):
            return True
        if "config/ftbquests" in pl or "heracles" in pl or "datapacks/" in pl:
            return True
    return False


def assign_level(evidence, classes, lang, cross):
    has_a = False
    # jar file evidence (not mere filename?) — instruction: A = jar-internal file / bytecode
    for e in evidence:
        f = e.get("file", "")
        if f and e.get("line", 0) > 0:
            has_a = True
            break
        # filename match alone is weak; still jar-side but prefer bytecode
    for c in classes:
        if c.get("numbers") or c.get("logic"):
            has_a = True
            break
    # filename match counts as jar-internal evidence for A? Instruction:
    # level = "A"（有 jar 內檔案／bytecode 原文）
    # filename is jar name not inside — so not A by itself.
    # But mcmod.info etc. hits are jar-internal → A
    if has_a:
        return "A"
    has_b = bool(lang) or any(cross.get(k) for k in ("scripts", "quests", "recipes"))
    # also filename-only candidate with no deeper evidence → 未確認
    if has_b:
        return "B"
    if evidence:  # filename match only
        return "未確認"
    return "未確認"


def run_query(
    query,
    mods_dir,
    jar_names,
    game_dir,
    javap,
    budget,
    max_mod_candidates,
    max_classes_per_mod,
):
    keywords = list(query.get("keywords") or [])
    class_tokens = list(query.get("class_tokens") or [])
    numeric_patterns = list(query.get("numeric_patterns") or [])
    logic_patterns = list(query.get("logic_patterns") or [])

    scored = []
    for jn in jar_names:
        n_hits, ev = scan_jar_candidate(mods_dir, jn, keywords)
        if n_hits >= 1:
            scored.append((n_hits, jn, ev))
    scored.sort(key=lambda t: (-t[0], t[1]))
    scored = scored[:max_mod_candidates]

    candidates = []
    unresolved = []
    query_caps_hit = False

    if not scored:
        unresolved.append("no_mod_candidates")

    for _hits, jn, ev in scored:
        if budget.wall_exceeded():
            break
        class_paths = match_classes_in_jar(
            mods_dir, jn, class_tokens, max_classes_per_mod
        )
        if class_paths and budget.used >= budget.max_calls:
            query_caps_hit = True
        classes, unres = javap_classes(
            mods_dir,
            jn,
            class_paths,
            numeric_patterns,
            logic_patterns,
            budget,
            javap,
        )
        if any(str(u).endswith(":caps_hit") for u in unres):
            query_caps_hit = True
        unresolved.extend(unres)
        lang, lang_fmt = read_lang_hits(mods_dir, jn, keywords)
        cross = collect_cross(mods_dir, jn, keywords, game_dir)
        level = assign_level(ev, classes, lang, cross)
        runtime_vis = evidence_runtime_visible(ev, lang, cross, lang_fmt)
        candidates.append(
            {
                "mod": jn,
                "jar": jn,
                "evidence": ev,
                "classes": classes,
                "lang": lang,
                "cross": cross,
                "level": level,
                "runtime_visible": runtime_vis,
                "lang_format_detected": lang_fmt,
            }
        )

    candidates.sort(key=lambda c: c["mod"])
    return {
        "need_id": query.get("need_id", ""),
        "question": query.get("question", ""),
        "keywords": keywords,
        "candidates": candidates,
        "unresolved": sorted(set(unresolved)),
        "caps_hit": query_caps_hit,
    }


def write_report(artifact, report_path):
    lines = []
    lines.append(f"# Capability index — {artifact.get('pack', '')}")
    lines.append("")
    lines.append(
        f"mods_scanned={artifact.get('mods_scanned')} · "
        f"javap={artifact.get('javap_version')} · "
        f"javap_calls_used={artifact.get('caps', {}).get('javap_calls_used')} · "
        f"caps_hit={artifact.get('caps', {}).get('caps_hit')}"
    )
    lines.append("")
    for q in artifact.get("queries", []):
        lines.append(f"## {q.get('need_id')}: {q.get('question')}")
        lines.append("")
        cands = q.get("candidates") or []
        if not cands:
            lines.append("（無候選）")
            lines.append("")
            continue
        lines.append("| mod | level | runtime_visible | 證據摘錄 |")
        lines.append("|---|---|---|---|")
        for c in cands:
            snippets = []
            for e in (c.get("evidence") or [])[:3]:
                snippets.append(f"`{e.get('file')}:{e.get('line')}` {e.get('text', '')[:80]}")
            for cl in (c.get("classes") or [])[:2]:
                for n in (cl.get("numbers") or [])[:2]:
                    snippets.append(f"num `{cl.get('name')}` {n.get('text', '')[:80]}")
                for n in (cl.get("logic") or [])[:2]:
                    snippets.append(f"logic `{cl.get('name')}` {n.get('text', '')[:80]}")
            cell = "<br>".join(snippets) if snippets else "—"
            lines.append(
                f"| `{c.get('mod')}` | {c.get('level')} | {c.get('runtime_visible')} | {cell} |"
            )
        lines.append("")
        lines.append(
            f"runtime 可答到：{'有部分候選 runtime_visible=true' if any(c.get('runtime_visible') for c in cands) else '否（本條候選皆非 runtime surface）'}"
        )
        lines.append("")

    lines.append("## bytecode 層：runtime 做唔到嘅部分")
    lines.append("")
    lines.append(
        "bytecode／javap 數字同 filter 邏輯只喺離線索引出現；"
        "runtime PackIndex 讀唔到 class 常數。候選 ≠ 已確認。"
    )
    lines.append("")
    Path(report_path).parent.mkdir(parents=True, exist_ok=True)
    with open(report_path, "w", encoding="utf-8", newline="") as f:
        f.write("\n".join(lines) + "\n")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Offline pack capability index")
    ap.add_argument("--pack-name", required=True)
    ap.add_argument("--mods", required=True, help="mods directory")
    ap.add_argument("--game-dir", default=None)
    ap.add_argument("--queries", default="tools/capability_queries.json")
    ap.add_argument("--out", required=True)
    ap.add_argument("--report", default=None)
    ap.add_argument("--javap", default=None)
    ap.add_argument("--max-mod-candidates", type=int, default=20)
    ap.add_argument("--max-classes-per-mod", type=int, default=40)
    ap.add_argument("--max-javap-calls", type=int, default=1500)
    ap.add_argument("--javap-timeout", type=float, default=20)
    ap.add_argument("--timeout-s", type=float, default=900)
    args = ap.parse_args(argv)

    javap = resolve_javap(args.javap)
    ver = javap_version_line(javap)

    with open(args.queries, encoding="utf-8") as f:
        queries = json.load(f)
    if not isinstance(queries, list):
        sys.stderr.write("error: queries must be a JSON array\n")
        return 2

    mods_dir = args.mods
    if not os.path.isdir(mods_dir):
        sys.stderr.write(f"error: mods dir not found: {mods_dir}\n")
        return 2

    jar_names = list_mod_jars(mods_dir)
    t0 = time.monotonic()
    # Cap: --max-javap-calls + wall-clock --timeout-s (both enforced).
    budget = JavapBudget(
        args.max_javap_calls, args.javap_timeout, args.timeout_s, t0
    )

    q_out = []
    for q in queries:
        q_out.append(
            run_query(
                q,
                mods_dir,
                jar_names,
                args.game_dir,
                javap,
                budget,
                args.max_mod_candidates,
                args.max_classes_per_mod,
            )
        )

    artifact = {
        "schema": "packai.capability-index/1",
        "pack": args.pack_name,
        "javap_version": ver,
        "mods_scanned": len(jar_names),
        "queries": q_out,
        "caps": {
            "max_mod_candidates": args.max_mod_candidates,
            "max_classes_per_mod": args.max_classes_per_mod,
            "max_javap_calls": args.max_javap_calls,
            "javap_calls_used": budget.used,
            "caps_hit": budget.caps_hit,
            "timeout_hit": budget.timeout_hit,
        },
    }

    out_text = json.dumps(artifact, ensure_ascii=False, indent=1, sort_keys=False) + "\n"
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="") as f:
        f.write(out_text)

    if args.report:
        write_report(artifact, args.report)

    print(
        f"OK pack={args.pack_name} mods={len(jar_names)} "
        f"javap_calls_used={budget.used} caps_hit={budget.caps_hit} "
        f"timeout_hit={budget.timeout_hit} -> {args.out}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
