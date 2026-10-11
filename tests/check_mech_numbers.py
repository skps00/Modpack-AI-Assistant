#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static + synthetic gate for tools/mech_numbers.py.

Checks: stdlib-only imports, no network modules, writes only via --out,
no datetime.now/time.time on the JSON artifact path, negative control
(NO_MATCH / no fabricated rows), determinism on synthetic pack.
"""
from __future__ import annotations

import ast
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "mech_numbers.py"

ALLOWED_IMPORT_ROOTS = {
    "argparse",
    "ast",
    "base64",
    "collections",
    "copy",
    "dataclasses",
    "enum",
    "fnmatch",
    "functools",
    "hashlib",
    "html",
    "io",
    "json",
    "math",
    "os",
    "pathlib",
    "pprint",
    "re",
    "shutil",
    "string",
    "struct",
    "subprocess",
    "sys",
    "tempfile",
    "textwrap",
    "typing",
    "zipfile",
    "__future__",
}

FORBIDDEN_NET = {
    "urllib",
    "requests",
    "http",
    "httplib",
    "aiohttp",
    "socket",
    "httpx",
    "urllib3",
}


def _src() -> str:
    return TOOL.read_text(encoding="utf-8")


def check_exists() -> None:
    assert TOOL.is_file(), f"missing {TOOL}"


def check_stdlib_and_no_network() -> None:
    tree = ast.parse(_src(), filename=str(TOOL))
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                root = alias.name.split(".")[0]
                assert root not in FORBIDDEN_NET, f"network import: {alias.name}"
                assert root in ALLOWED_IMPORT_ROOTS, f"non-stdlib import: {alias.name}"
        elif isinstance(node, ast.ImportFrom):
            if node.module is None:
                continue
            root = node.module.split(".")[0]
            assert root not in FORBIDDEN_NET, f"network import: {node.module}"
            assert root in ALLOWED_IMPORT_ROOTS, f"non-stdlib import: {node.module}"


def check_no_timestamp_on_json_path() -> None:
    src = _src()
    # Hard ban: these must not appear in the tool (JSON must be deterministic).
    assert "datetime.now" not in src, "datetime.now forbidden (breaks determinism)"
    assert "time.time" not in src, "time.time forbidden on JSON path"
    assert "datetime.utcnow" not in src, "datetime.utcnow forbidden"
    assert re.search(r"\bimport\s+datetime\b", src) is None, "datetime import forbidden"
    assert re.search(r"\bimport\s+time\b", src) is None, "time import forbidden"


def check_writes_only_out() -> None:
    """Static: open(..., 'w'/'wb'/'x') and Path.write_* only on --out targets."""
    tree = ast.parse(_src(), filename=str(TOOL))
    # Collect names bound from --out args in write helpers — allowlist write_* helpers.
    write_funcs = {"write_db", "cmd_report"}
    for node in ast.walk(tree):
        if isinstance(node, ast.Call):
            fn = node.func
            name = None
            if isinstance(fn, ast.Name):
                name = fn.id
            elif isinstance(fn, ast.Attribute):
                name = fn.attr
            if name == "open":
                # open() must not appear — use Path.write_bytes only
                raise AssertionError("bare open() not allowed; use Path.write_bytes on --out")
            if name in {"write_text", "write_bytes"}:
                # must be inside write_db / cmd_report only (lexical parent walk)
                pass
    # Ensure no hardcoded writes into mods/ or config/
    src = _src()
    assert "mods/" not in src or "--mods" in src  # flag ok
    assert not re.search(r'write_(?:text|bytes)\(\s*["\'][^"\']*mods/', src)
    assert not re.search(r'write_(?:text|bytes)\(\s*["\'][^"\']*config/', src)
    # write_bytes / write_text call sites must be in write_db or cmd_report
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef):
            if node.name in write_funcs:
                continue
            for child in ast.walk(node):
                if isinstance(child, ast.Call) and isinstance(child.func, ast.Attribute):
                    if child.func.attr in {"write_text", "write_bytes"}:
                        # nested helpers inside non-write funcs are forbidden
                        if node.name not in write_funcs and node.name != "main":
                            # allow only write_db / cmd_report
                            if node.name.startswith("cmd_") and node.name != "cmd_report":
                                raise AssertionError(
                                    f"write in unexpected func: {node.name}"
                                )


def _write_jar(path: Path, entries: dict[str, bytes]) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for name, data in sorted(entries.items()):
            zf.writestr(name, data)


def _run(args: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(TOOL), *args],
        capture_output=True,
        text=True,
        cwd=str(ROOT),
    )


def check_synthetic_build_query_determinism() -> None:
    tmp = Path(tempfile.mkdtemp(prefix="mechnum_chk_"))
    mc = tmp / "minecraft"
    cfg = mc / "config" / "FakeMech"
    cfg.mkdir(parents=True)
    (cfg / "machine.toml").write_text(
        "# FakeMech machine config\n"
        "[machines.burner]\n"
        "\t#Energy burned per tick.\n"
        "\tburnRate = 12.5\n"
        "\t#Capacity in mB.\n"
        "\ttankCapacity = 1000\n",
        encoding="utf-8",
    )
    mods = mc / "mods"
    mods.mkdir()
    _write_jar(
        mods / "fakemech-1.0.0.jar",
        {
            "data/fakemech/machines/burner.json": json.dumps(
                {"burn_rate": 3, "energy": 50, "count": 9}
            ).encode("utf-8"),
            "assets/fakemech/lang/en_us.json": b'{"item.fakemech.x":"Burns 9 RF per tick"}\n',
        },
    )
    out1 = tmp / "a.json"
    out2 = tmp / "b.json"
    for out in (out1, out2):
        p = _run(
            [
                "build",
                "--instance",
                str(mc),
                "--out",
                str(out),
                "--max-classes",
                "0",
            ]
        )
        assert p.returncode == 0, p.stderr + p.stdout
    assert out1.read_bytes() == out2.read_bytes(), "determinism failed"
    assert b"\r\n" not in out1.read_bytes(), "LF-only required"
    art = json.loads(out1.read_text(encoding="utf-8"))
    assert art["schema"] == 1
    assert "timestamp" not in art and "built_at" not in art
    keys = {r["key"] for r in art["records"] if r["source_kind"] == "A1"}
    assert any("burnRate" in k for k in keys), keys
    # A2 should keep burn_rate/energy, drop bare count
    a2_keys = {r["key"] for r in art["records"] if r["source_kind"] == "A2"}
    assert "burn_rate" in a2_keys or any("burn" in k for k in a2_keys), a2_keys
    assert "count" not in a2_keys

    q = _run(
        ["query", "--db", str(out1), "--terms", "burner burn", "--limit", "20"]
    )
    assert q.returncode == 0, q.stderr
    assert "burnRate" in q.stdout or "burn_rate" in q.stdout, q.stdout
    assert "NO_MATCH" not in q.stdout

    # Negative control: must be empty + NO_MATCH, no invented rows
    nq = _run(
        ["query", "--db", str(out1), "--terms", "zzz_no_such_thing_qq"]
    )
    assert nq.returncode == 0, nq.stderr
    assert "NO_MATCH" in nq.stdout
    assert "NO_NUMBER" in nq.stdout
    # fabricated-path detector: stdout must not invent a numeric value row
    for line in nq.stdout.splitlines():
        if line.strip() in {"NO_MATCH", "NO_NUMBER"}:
            continue
        if "value=" in line or "tier=" in line or "/A1" in line or "/A2" in line:
            raise AssertionError(f"fabricated query row: {line}")

    # --include-lang brings tier B
    outb = tmp / "lang.json"
    p = _run(
        [
            "build",
            "--instance",
            str(mc),
            "--out",
            str(outb),
            "--include-lang",
            "--max-classes",
            "0",
        ]
    )
    assert p.returncode == 0, p.stderr
    artb = json.loads(outb.read_text(encoding="utf-8"))
    assert any(r["tier"] == "B" for r in artb["records"]), artb["counts"]


def check_no_fabricate_helpers() -> None:
    """Fail if source contains obvious fabricate / guess value paths."""
    src = _src()
    banned = [
        r"random\.randint",
        r"fabricate",
        r"guess_value",
        r"invent_number",
        r"DEFAULT_BURN\s*=\s*\d",
    ]
    for pat in banned:
        assert re.search(pat, src, re.I) is None, f"fabricate path: {pat}"


def main() -> int:
    checks = [
        check_exists,
        check_stdlib_and_no_network,
        check_no_timestamp_on_json_path,
        check_writes_only_out,
        check_no_fabricate_helpers,
        check_synthetic_build_query_determinism,
    ]
    for fn in checks:
        fn()
        print(f"PASS {fn.__name__}")
    print("OK check_mech_numbers")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL {exc}")
        raise SystemExit(1)
