#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assert Pack AI display-body log lines contain no model/tool leak strings.

Usage (repo root):
  python tests/check_ask_display_leak.py
  python tests/check_ask_display_leak.py --ver 0.2.1
  python tests/check_ask_display_leak.py --log <path>
  python tests/check_ask_display_leak.py --trace <dir>
  python tests/check_ask_display_leak.py --trace <dir> --since YYYYMMDD
  python tests/check_ask_display_leak.py --self-test

Always runs junk negative control against
tests/fixtures/ask_display_leak_2026-09-13.txt (must FAIL junk rules).
`--fixture` is for a *clean* body file (one body per line).
`--trace` scans ask-YYYYMMDD-*.jsonl for packai-injected facts / peer lines only
(never model prose). Trace events carry no build id, so `--since` scopes by
filename date (default = today) and ignores pre-fix historical traces.

Exit 0 = pass; 1 = leak/empty-all; 2 = no log lines / no traces in scope /
no injected facts / not certified (need real-machine smoke).
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import tempfile
from datetime import date, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRISM_LOG = (
    Path(os.environ.get(
        "PACKAI_PRISM",
        r"C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0",
    ))
    / "instances"
    / "AI_test_NFWC_DIM"
    / "minecraft"
    / "logs"
    / "latest.log"
)
MARKER = "Pack AI display body ver="
LINE_RE = re.compile(r"Pack AI display body ver=(\S+) src=(\S+) (.*)$")
ENSURE_PREFIX = "Pack AI ask reply before ensureCards:"
FORBIDDEN = (
    "render_recipe_cards",
    "[RECIPE_CARDS]",
    "【JEI",
    "注意：JEI",
    "role=",
    "已完整扫描",
    "已完整掃描",
    "DSML",
    "<invoke",
    "<tool_calls",
)
UNFIXED_FIXTURE = ROOT / "tests" / "fixtures" / "ask_display_leak_2026-09-13.txt"
CARD_PARAM_VALUES = ("ino_dlc_build:cross_z_build_full_bottle",)
BARE_ID_RE = re.compile(r"^[a-z0-9_]+:[a-z0-9_/]+$")
ROLE_EQ_RE = re.compile(r"role\s*=\s*(?:output|uses)\b", re.I)

# Python golden copies — compared against OfficialDisplay.java; used as fallback
# only when derivation is impossible (that path still exits 2; never silent pass).
_EXPECTED_NS_DENY = frozenset(
    {
        "item",
        "source",
        "tier",
        "note",
        "file",
        "mechanic",
        "count",
        "via",
        "held",
        "gets",
        "entity",
        "table",
        "gateway",
        "structure",
        "dimension",
        "from",
        "src",
        "rel",
    }
)
_EXPECTED_TK_PREFIXES = (
    "item",
    "block",
    "entity",
    "fluid",
    "itemgroup",
    "gui",
    "tooltip",
    "jei",
    "packai",
    "effect",
    "enchantment",
    "biome",
    "dimension",
    "attribute",
    "advancement",
    "death",
    "container",
    "subtitles",
    "key",
    "options",
    "stat",
    "commands",
    "gamerule",
    "tutorial",
    "resourcepack",
    "pack",
    "screen",
    "menu",
    "narration",
    "chat",
    "mount",
    "painting",
    "particle",
    "sound",
    "recipe",
    "instrument",
    "banner_pattern",
    "cat_variant",
    "frog_variant",
    "wolf_variant",
)


def _find_official_display_java() -> Path | None:
    """Locate OfficialDisplay.java under forge/* or neoforge/* (not hard-coded)."""
    for tree in ("forge", "neoforge"):
        base = ROOT / tree
        if not base.is_dir():
            continue
        hits = sorted(base.glob("**/logic/OfficialDisplay.java"))
        if hits:
            return hits[0]
    return None


def _java_str(s: str) -> str:
    return (
        s.replace(r"\\", "\\")
        .replace(r"\"", '"')
        .replace(r"\n", "\n")
        .replace(r"\t", "\t")
    )


def _load_official_display_literals() -> tuple[str, str, int, frozenset[str], tuple[str, ...]]:
    """Derive PEER_* / NS_DENY / TRANSLATION_KEY prefixes from OfficialDisplay.java."""
    path = _find_official_display_java()
    if path is None or not path.is_file():
        print(
            "FAIL cannot locate OfficialDisplay.java "
            "(glob **/logic/OfficialDisplay.java under forge/* and neoforge/*)"
        )
        sys.exit(2)
    text = path.read_text(encoding="utf-8")
    m_peer = re.search(
        r'static\s+final\s+String\s+PEER_HEADER\s*=\s*"((?:\\.|[^"\\])*)"\s*;', text
    )
    m_no = re.search(
        r'static\s+final\s+String\s+NO_OFFICIAL\s*=\s*"((?:\\.|[^"\\])*)"\s*;', text
    )
    m_cap = re.search(r"static\s+final\s+int\s+PEER_CAP\s*=\s*(\d+)\s*;", text)
    if not m_peer or not m_no or not m_cap:
        print(f"FAIL cannot parse PEER_HEADER/NO_OFFICIAL/PEER_CAP from {path}")
        sys.exit(2)
    peer = _java_str(m_peer.group(1))
    no_off = _java_str(m_no.group(1))
    cap = int(m_cap.group(1))
    if not peer or not no_off or cap <= 0:
        print(
            f"FAIL OfficialDisplay literals empty/zero: "
            f"PEER_HEADER={peer!r} NO_OFFICIAL={no_off!r} PEER_CAP={cap}"
        )
        sys.exit(2)

    m_deny = re.search(
        r"NS_DENY\s*=\s*Set\.of\(\s*((?:.|\n)*?)\)\s*;", text
    )
    m_tk = re.search(
        r"TRANSLATION_KEY\s*=\s*Pattern\.compile\(\s*((?:.|\n)*?)\)\s*;", text
    )
    if not m_deny or not m_tk:
        print(
            f"FAIL literals drift from OfficialDisplay.java: "
            f"cannot parse NS_DENY/TRANSLATION_KEY from {path}"
        )
        sys.exit(2)

    deny_body = m_deny.group(1)
    derived_deny = frozenset(
        _java_str(s) for s in re.findall(r'"((?:\\.|[^"\\])*)"', deny_body)
    )
    if not derived_deny:
        print(
            "FAIL literals drift from OfficialDisplay.java: "
            "NS_DENY parsed empty"
        )
        sys.exit(2)

    tk_body = m_tk.group(1)
    # Join Java string-literal fragments inside Pattern.compile(...).
    tk_joined = "".join(_java_str(s) for s in re.findall(r'"((?:\\.|[^"\\])*)"', tk_body))
    m_pref = re.search(r"\^\(([^)]+)\)\\?\.", tk_joined)
    if not m_pref:
        print(
            "FAIL literals drift from OfficialDisplay.java: "
            "cannot parse TRANSLATION_KEY prefixes"
        )
        sys.exit(2)
    derived_tk = tuple(p for p in m_pref.group(1).split("|") if p)
    # Strip optional (?i) already outside; prefixes only.
    if not derived_tk:
        print(
            "FAIL literals drift from OfficialDisplay.java: "
            "TRANSLATION_KEY prefixes empty"
        )
        sys.exit(2)

    details: list[str] = []
    if derived_deny != _EXPECTED_NS_DENY:
        details.append(
            f"NS_DENY java={sorted(derived_deny)} py={sorted(_EXPECTED_NS_DENY)}"
        )
    if derived_tk != _EXPECTED_TK_PREFIXES:
        details.append(f"TRANSLATION_KEY prefixes java={derived_tk} py={_EXPECTED_TK_PREFIXES}")
    if details:
        print("FAIL literals drift from OfficialDisplay.java: " + "; ".join(details))
        sys.exit(2)

    return peer, no_off, cap, derived_deny, derived_tk


PEER_HEADER, NO_OFFICIAL, PEER_CAP, NS_DENY, TK_PREFIXES = _load_official_display_literals()

# Plan α semantic junk (never raw substring "js:" — that matches kubejs:).
JS_BARE_RE = re.compile(r"(?<![A-Za-z0-9_])js:\d+")
FULLWIDTH_PAREN_RE = re.compile(r"（([^）]+)）")
COUNT_RE = re.compile(r"count:\d+")
ASK_TRACE_RE = re.compile(r"^ask-(\d{8})-.*\.jsonl$", re.I)
# Legitimate annotation: fullwidth-paren plain registry id.
ANNOT_ID_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
# Every namespace in OfficialDisplay NS_DENY — junk inside （…）.
DENY_NS_IN_PAREN_RE = re.compile(
    r"(?i)(?:" + "|".join(re.escape(n) for n in sorted(NS_DENY)) + r"):"
)
# Translation-key shaped fullwidth-paren ids (same prefixes as OfficialDisplay).
TRANSLATION_KEY_RE = re.compile(
    r"(?i)^(" + "|".join(re.escape(p) for p in TK_PREFIXES) + r")\.[a-z0-9_.]+$"
)
CJK_RE = re.compile(
    r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff"
    r"\u3040-\u309f\u30a0-\u30ff\uac00-\ud7af]"
)


def is_translation_key_shaped(label: str) -> bool:
    if not label or any(ch.isspace() for ch in label) or CJK_RE.search(label):
        return False
    return bool(TRANSLATION_KEY_RE.match(label))


def read_log_text(path: Path) -> str:
    raw = path.read_bytes()
    for enc in ("utf-8", "cp950", "cp1252"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def parse_display_lines(text: str) -> list[tuple[str, str, str]]:
    out: list[tuple[str, str, str]] = []
    for line in text.splitlines():
        idx = line.find(MARKER)
        if idx < 0:
            continue
        m = LINE_RE.search(line[idx:])
        if not m:
            # Fallback: take everything after marker as body, ver/src unknown.
            rest = line[idx + len(MARKER) :]
            out.append(("", "", rest))
            continue
        out.append((m.group(1), m.group(2), m.group(3)))
    return out


def unescape_body(body: str) -> str:
    return body.replace("\\n", "\n").replace("\\r", "")


def junk_reasons(body: str) -> list[str]:
    """Display junk the T4 'zero markup' gate missed (R9)."""
    text = unescape_body(body)
    reasons: list[str] = []
    for val in CARD_PARAM_VALUES:
        if val in text:
            reasons.append(f"card param {val!r} in prose")
    if ROLE_EQ_RE.search(text) or "role=" in text:
        reasons.append("role=")
    for line in text.splitlines():
        if BARE_ID_RE.match(line.strip()):
            reasons.append(f"bare registry id {line.strip()!r}")
    return reasons


def assert_junk_negative_control() -> None:
    """Unfixed leak sample MUST trip junk assertions."""
    bare = "ok\nminecraft:iron_ingot\n"
    if not any(r.startswith("bare registry") for r in junk_reasons(bare)):
        print("FAIL junk selfcheck: bare registry id line did not trip")
        sys.exit(1)
    param = "see ino_dlc_build:cross_z_build_full_bottle in prose"
    if not any("card param" in r for r in junk_reasons(param)):
        print("FAIL junk selfcheck: recipe card param did not trip")
        sys.exit(1)
    if not junk_reasons("role=uses leftover"):
        print("FAIL junk selfcheck: role=uses did not trip")
        sys.exit(1)
    if junk_reasons("怎么用：在工作台合成。"):
        print("FAIL junk selfcheck: clean prose tripped")
        sys.exit(1)
    if not UNFIXED_FIXTURE.is_file():
        print(f"FAIL missing unfixed fixture {UNFIXED_FIXTURE}")
        sys.exit(1)
    hit = False
    for body in load_fixture_bodies(UNFIXED_FIXTURE):
        if junk_reasons(body):
            hit = True
            break
    if not hit:
        print("FAIL negative control: unfixed sample did not trigger junk assertions")
        sys.exit(1)
    print("OK negative control (unfixed sample fails junk assertions)")


def load_fixture_bodies(path: Path) -> list[str]:
    bodies: list[str] = []
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        bodies.append(line)
    return bodies


def assert_bodies(bodies: list[str], label: str) -> None:
    if not bodies:
        print("NO LOG LINES (need real-machine smoke)")
        sys.exit(2)
    nonempty = 0
    for i, body in enumerate(bodies):
        if body.strip():
            nonempty += 1
        for word in FORBIDDEN:
            if word in body:
                print(f"FAIL {label}[{i}] contains {word!r}")
                print(body[:500])
                sys.exit(1)
        for reason in junk_reasons(body):
            print(f"FAIL {label}[{i}] junk {reason}")
            print(unescape_body(body)[:500])
            sys.exit(1)
    if nonempty == 0:
        print(f"FAIL {label}: all bodies empty")
        sys.exit(1)
    print(f"OK {label} lines={len(bodies)} nonempty={nonempty}")


def dump_ensurecards(text: str) -> list[str]:
    """Join multi-line 'before ensureCards' blocks into one body each."""
    bodies: list[str] = []
    cur: list[str] | None = None
    ts = re.compile(r"^\[\d{1,2}\w{3}\d{4} ")
    for line in text.splitlines():
        idx = line.find(ENSURE_PREFIX)
        if idx >= 0:
            if cur:
                bodies.append("\\n".join(cur))
            start = idx + len(ENSURE_PREFIX)
            first = line[start:].lstrip()
            cur = [first] if first else []
            continue
        if cur is None:
            continue
        if ts.match(line):
            bodies.append("\\n".join(cur))
            cur = None
            continue
        cur.append(line)
    if cur:
        bodies.append("\\n".join(cur))
    return bodies


def _peer_lines_in(text: str) -> list[str]:
    out: list[str] = []
    for line in text.splitlines():
        idx = line.find(PEER_HEADER)
        if idx >= 0:
            out.append(line[idx:].strip())
    return out


def _assert_paren_id_clean(pid: str, label: str, where: str) -> None:
    if JS_BARE_RE.search(pid):
        print(f"FAIL {label} {where} bare js:<n>: {pid!r}")
        sys.exit(1)
    if ".js" in pid or ".json" in pid or ".snbt" in pid:
        print(f"FAIL {label} {where} script ext: {pid!r}")
        sys.exit(1)
    if "mechanic:" in pid:
        print(f"FAIL {label} {where} mechanic:: {pid!r}")
        sys.exit(1)
    if COUNT_RE.search(pid):
        print(f"FAIL {label} {where} count:<n>: {pid!r}")
        sys.exit(1)
    if DENY_NS_IN_PAREN_RE.search(pid):
        print(f"FAIL {label} {where} deny-ns in （…）: {pid!r}")
        sys.exit(1)
    if is_translation_key_shaped(pid):
        print(f"FAIL {label} {where} translation-key id: {pid!r}")
        sys.exit(1)


def _assert_peer_line_clean(peer_line: str, label: str) -> None:
    rest = peer_line[len(PEER_HEADER) :] if peer_line.startswith(PEER_HEADER) else peer_line
    entries = 0
    for part in rest.split(";"):
        entry = part.strip()
        if not entry:
            continue
        entries += 1
        if NO_OFFICIAL in entry:
            print(f"FAIL {label} peer has {NO_OFFICIAL}: {entry[:200]}")
            sys.exit(1)
        if ".js" in entry or ".json" in entry or ".snbt" in entry:
            print(f"FAIL {label} peer has script ext: {entry[:200]}")
            sys.exit(1)
        for m in FULLWIDTH_PAREN_RE.finditer(entry):
            _assert_paren_id_clean(m.group(1), label, "peer")
    if entries > PEER_CAP:
        print(f"FAIL {label} peer entries={entries} > PEER_CAP")
        sys.exit(1)


def _assert_fact_body_clean(text: str, label: str) -> None:
    # Peer header may sit mid-line — still body-check the pre-header slice (B4).
    body_lines: list[str] = []
    for line in text.splitlines():
        idx = line.find(PEER_HEADER)
        if idx >= 0:
            pre = line[:idx]
            if pre.strip():
                body_lines.append(pre)
            continue
        body_lines.append(line)
    body = "\n".join(body_lines)
    if NO_OFFICIAL in body:
        print(f"FAIL {label} body has {NO_OFFICIAL}")
        print(body[:500])
        sys.exit(1)
    for m in FULLWIDTH_PAREN_RE.finditer(body):
        _assert_paren_id_clean(m.group(1), label, "body")


def _assert_injected_text(text: str, label: str) -> None:
    if not text or not text.strip():
        return
    for peer in _peer_lines_in(text):
        _assert_peer_line_clean(peer, label)
    _assert_fact_body_clean(text, label)


def _is_annotation_id(pid: str) -> bool:
    """One annotation = fullwidth-paren plain registry id (pipeline under test)."""
    if not ANNOT_ID_RE.match(pid):
        return False
    ns, _, _rest = pid.partition(":")
    if "/" in ns:
        return False
    for seg in pid.split(":"):
        if ".js" in seg or ".json" in seg or ".snbt" in seg:
            return False
    return True


def _count_annotations(text: str) -> int:
    n = 0
    for m in FULLWIDTH_PAREN_RE.finditer(text):
        if _is_annotation_id(m.group(1)):
            n += 1
    return n


def extract_injected_facts_from_trace(
    path: Path,
) -> tuple[
    list[tuple[str, str]],
    list[tuple[str, str]],
    list[tuple[str, str]],
    list[int],
    list[str],
]:
    """Packai-injected fact text only (send.facts graphFacts + tool.result).

    Returns (fact_texts, tool_full_texts, partial_piece_texts, partial_row_chars,
    sha256_only_labels).

    Truncated tool.result rows: scan head and tail as **two separate texts** —
    a fabricated token/group must never span the seam (joined head+tail can
    invent a closing （…） that never existed in either half).
    """
    facts: list[tuple[str, str]] = []
    tools: list[tuple[str, str]] = []
    partials: list[tuple[str, str]] = []
    partial_chars: list[int] = []
    sha256_only: list[str] = []
    for lineno, raw in enumerate(
        path.read_text(encoding="utf-8", errors="replace").splitlines(), start=1
    ):
        line = raw.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            continue
        event = obj.get("event") or ""
        label = f"{path.name}:{lineno}:{event}"
        if event == "send.facts":
            content = obj.get("content")
            if isinstance(content, str) and content.strip().startswith("{"):
                try:
                    inner = json.loads(content)
                except json.JSONDecodeError:
                    continue
                gf = inner.get("graphFacts")
                if isinstance(gf, list):
                    for i, fact in enumerate(gf):
                        if isinstance(fact, str) and fact.strip():
                            facts.append((f"{label}:graphFacts[{i}]", fact))
            elif isinstance(content, dict):
                gf = content.get("graphFacts")
                if isinstance(gf, list):
                    for i, fact in enumerate(gf):
                        if isinstance(fact, str) and fact.strip():
                            facts.append((f"{label}:graphFacts[{i}]", fact))
        elif event == "tool.result":
            name = obj.get("name") or "tool"
            result = obj.get("result")
            if isinstance(result, str) and result.strip():
                tools.append((f"{label}:{name}", result))
                continue
            head = obj.get("head")
            tail = obj.get("tail")
            has_head = isinstance(head, str) and bool(head)
            has_tail = isinstance(tail, str) and bool(tail)
            if has_head or has_tail:
                # Separate texts only — never join (seam must not invent tokens).
                nchars = (len(head) if has_head else 0) + (len(tail) if has_tail else 0)
                partial_chars.append(nchars)
                if has_head:
                    partials.append((f"{label}:{name}:head", head))
                if has_tail:
                    partials.append((f"{label}:{name}:tail", tail))
                continue
            if obj.get("sha256"):
                sha256_only.append(f"{label}:{name}")
    return facts, tools, partials, partial_chars, sha256_only


def assert_trace_dir(trace_dir: Path, since: str, min_annotations: int = 1) -> None:
    if not trace_dir.is_dir():
        print(f"FAIL --trace not a directory: {trace_dir}")
        sys.exit(2)
    candidates: list[Path] = []
    files: list[Path] = []
    for p in sorted(trace_dir.iterdir()):
        if not p.is_file():
            continue
        m = ASK_TRACE_RE.match(p.name)
        if not m:
            continue
        candidates.append(p)
        if m.group(1) >= since:
            files.append(p)
    filtered = len(candidates) - len(files)
    print(f"NOTE: --since {since} filtered out {filtered} trace file(s)")
    if not files:
        print(f"NO TRACES in scope (since {since})")
        sys.exit(2)

    fact_n = 0
    tool_n = 0
    partial_n = 0
    annotations = 0
    texts_scanned = 0

    for path in files:
        facts, tools, partials, partial_chars, sha256_only = extract_injected_facts_from_trace(
            path
        )
        for note in sha256_only:
            print(f"NOTE tool.result sha256-only (unscanned): {note}")
            # sha256-only: noted, not counted toward texts_scanned / coverage
        for chars in partial_chars:
            print(
                f"NOTE: partial tool.result scanned head+tail only (chars={chars})"
            )
            partial_n += 1

        for label, text in facts:
            _assert_injected_text(text, label)
            fact_n += 1
            texts_scanned += 1
            annotations += _count_annotations(text)
        for label, text in tools:
            _assert_injected_text(text, label)
            tool_n += 1
            texts_scanned += 1
        for label, text in partials:
            # Scanned for leaks; must NOT count toward annotation coverage.
            _assert_injected_text(text, label)
            tool_n += 1
            texts_scanned += 1

    if fact_n == 0:
        print("NO INJECTED FACTS in scope (need real-machine smoke)")
        sys.exit(2)
    if annotations < min_annotations:
        print(
            f"NOT CERTIFIED: {annotations} annotations in {fact_n} fact texts "
            f"(use --min-annotations 0 for a legitimately annotation-free scope)"
        )
        sys.exit(2)
    print(
        f"OK trace dir={trace_dir} since={since} files={len(files)} "
        f"fact_texts={fact_n} annotations={annotations} tool_results={tool_n} "
        f"partial={partial_n} texts_scanned={texts_scanned}"
    )


def _run_main_capture(argv: list[str]) -> tuple[int, str]:
    """Invoke main() with argv; return (exit_code, stdout)."""
    buf = io.StringIO()
    old_out, old_argv = sys.stdout, sys.argv
    try:
        sys.stdout = buf
        sys.argv = argv
        try:
            main()
            return 0, buf.getvalue()
        except SystemExit as e:
            code = e.code
            if code is None:
                return 0, buf.getvalue()
            if isinstance(code, int):
                return code, buf.getvalue()
            return 1, buf.getvalue()
    finally:
        sys.stdout = old_out
        sys.argv = old_argv


def _write_jsonl(path: Path, rows: list[dict]) -> None:
    path.write_text(
        "\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n",
        encoding="utf-8",
    )


def run_self_test() -> None:
    """In-gate synthetic traces — no fixture files committed."""
    day = "20260916"
    failures: list[str] = []
    n_ok = 0

    def expect(
        name: str,
        code: int,
        argv_extra: list[str],
        rows: list[dict],
        want_note_substr: str | None = None,
        want_out_substr: str | None = None,
    ) -> None:
        nonlocal n_ok
        with tempfile.TemporaryDirectory(prefix="packai_gate_st_") as tmp:
            tdir = Path(tmp)
            _write_jsonl(tdir / f"ask-{day}-case.jsonl", rows)
            min_ann = 1
            if "--min-annotations" in argv_extra:
                i = argv_extra.index("--min-annotations")
                min_ann = int(argv_extra[i + 1])
            buf = io.StringIO()
            old = sys.stdout
            rc = 0
            try:
                sys.stdout = buf
                try:
                    assert_trace_dir(tdir, day, min_annotations=min_ann)
                except SystemExit as e:
                    rc = (
                        0
                        if e.code is None
                        else int(e.code)
                        if isinstance(e.code, int)
                        else 1
                    )
            finally:
                sys.stdout = old
            out = buf.getvalue()
            if rc != code:
                failures.append(f"{name}: want rc={code} got {rc}; out={out!r}")
                return
            if want_note_substr is not None and want_note_substr not in out:
                failures.append(
                    f"{name}: missing note {want_note_substr!r}; out={out!r}"
                )
                return
            if want_out_substr is not None and want_out_substr not in out:
                failures.append(
                    f"{name}: missing out {want_out_substr!r}; out={out!r}"
                )
                return
            n_ok += 1

    clean_fact = {
        "event": "send.facts",
        "content": json.dumps(
            {"graphFacts": ["鐵錠（minecraft:iron_ingot）"]}, ensure_ascii=False
        ),
    }
    junk_fact = {
        "event": "send.facts",
        "content": json.dumps(
            {
                "graphFacts": [
                    "kubejs/client_scripts/item_tooltips.js:2（無官方名）"
                ]
            },
            ensure_ascii=False,
        ),
    }
    bare_fact = {
        "event": "send.facts",
        "content": json.dumps(
            {"graphFacts": ["just a fact with no paren id"]}, ensure_ascii=False
        ),
    }

    # 1. clean annotated fact -> 0
    expect("1_clean_annotated", 0, [], [clean_fact])

    # 2. junk in fact -> 1
    expect("2_junk_fact", 1, [], [junk_fact])

    # 3. truncated row with leak in head -> 1
    expect(
        "3_partial_leak_head",
        1,
        [],
        [
            clean_fact,
            {
                "event": "tool.result",
                "name": "lookup",
                "head": "x（file:leak）y",
                "tail": "clean tail",
            },
        ],
    )

    # 4. head ends with （ + tail has ） but neither half alone violates -> 0
    expect(
        "4_seam_false_positive",
        0,
        [],
        [
            clean_fact,
            {
                "event": "tool.result",
                "name": "lookup",
                "head": "prefix（",
                "tail": "file:seam）suffix",
            },
        ],
    )

    # 5. sha256-only -> note printed, not counted
    expect(
        "5_sha256_only",
        0,
        [],
        [
            clean_fact,
            {
                "event": "tool.result",
                "name": "big",
                "sha256": "abc123",
            },
        ],
        want_note_substr="NOTE tool.result sha256-only",
        want_out_substr="tool_results=0",
    )

    # 6. no send.facts at all -> 2
    expect(
        "6_no_facts",
        2,
        [],
        [
            {
                "event": "tool.result",
                "name": "lookup",
                "result": "鐵錠（minecraft:iron_ingot）",
            }
        ],
    )

    # 7. facts with no annotation, default -> 2
    expect("7_no_annot_default", 2, [], [bare_fact])

    # 8. same facts with --min-annotations 0 -> 0
    expect("8_min_annot_0", 0, ["--min-annotations", "0"], [bare_fact])

    # 9. bad --since (20261332) -> 2 (via main argparse validation)
    rc9, out9 = _run_main_capture(
        [
            str(Path(__file__).resolve()),
            "--since",
            "20261332",
            "--trace",
            str(ROOT / "tests"),
        ]
    )
    if rc9 != 2:
        failures.append(f"9_bad_since: want rc=2 got {rc9}; out={out9!r}")
    else:
        n_ok += 1

    if failures:
        for f in failures:
            print(f"SELFTEST FAIL {f}")
        print(f"SELFTEST FAILED passed={n_ok} failed={len(failures)}")
        sys.exit(1)
    print(f"SELFTEST OK n={n_ok}")
    sys.exit(0)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ver", default="", help="filter display-body lines by buildId")
    ap.add_argument("--fixture", default="", help="one body per line (comments #)")
    ap.add_argument("--log", default="", help="latest.log path")
    ap.add_argument(
        "--trace",
        default="",
        help="dir of ask-YYYYMMDD-*.jsonl; check packai-injected facts only",
    )
    ap.add_argument(
        "--since",
        default=date.today().strftime("%Y%m%d"),
        help="with --trace: only ask-YYYYMMDD-*.jsonl with file date >= this (default=today)",
    )
    ap.add_argument(
        "--min-annotations",
        type=int,
        default=1,
        help="with --trace: minimum annotation count in send.facts (default=1)",
    )
    ap.add_argument(
        "--self-test",
        action="store_true",
        help="run in-gate synthetic self-test and exit",
    )
    ap.add_argument(
        "--dump-ensurecards",
        default="",
        help="write extracted before-ensureCards bodies to this file and exit",
    )
    args = ap.parse_args()

    if args.self_test:
        run_self_test()
        return

    try:
        datetime.strptime(args.since, "%Y%m%d")
    except ValueError:
        print(f"FAIL --since must be YYYYMMDD (invalid date: {args.since!r})")
        sys.exit(2)

    assert_junk_negative_control()

    if args.dump_ensurecards:
        log_path = Path(args.log) if args.log else PRISM_LOG
        text = read_log_text(log_path)
        bodies = dump_ensurecards(text)
        dest = Path(args.dump_ensurecards)
        dest.parent.mkdir(parents=True, exist_ok=True)
        header = (
            f"# Source: {log_path}\n"
            f"# Extracted Pack AI ask reply before ensureCards bodies "
            f"(pre-T4 leak sample). Newlines escaped as \\n.\n"
        )
        dest.write_text(header + "\n".join(bodies) + "\n", encoding="utf-8")
        print(f"wrote {dest} bodies={len(bodies)}")
        return

    if args.trace:
        assert_trace_dir(Path(args.trace), args.since, min_annotations=args.min_annotations)
        print("check_ask_display_leak OK")
        return

    if args.fixture:
        bodies = load_fixture_bodies(Path(args.fixture))
        assert_bodies(bodies, args.fixture)
        print("check_ask_display_leak OK")
        return

    log_path = Path(args.log) if args.log else PRISM_LOG
    if not log_path.is_file():
        print("NO LOG LINES (need real-machine smoke)")
        sys.exit(2)
    parsed = parse_display_lines(read_log_text(log_path))
    if args.ver:
        parsed = [p for p in parsed if p[0] == args.ver]
    parsed = parsed[-5:]
    bodies = [p[2] for p in parsed]
    assert_bodies(bodies, str(log_path))
    print("check_ask_display_leak OK")


if __name__ == "__main__":
    main()
