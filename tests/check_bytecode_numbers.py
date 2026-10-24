#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static + synthetic gate for tools/bytecode_numbers.py."""
from __future__ import annotations

import ast
import hashlib
import json
import os
import re
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "bytecode_numbers.py"
def _atm8_instance_path() -> Path:
    if "PACKAI_ATM8_INSTANCE" in os.environ:
        return Path(os.environ["PACKAI_ATM8_INSTANCE"]).resolve()
    primary = (
        ROOT / ".." / "pc-mirror" / "instances" / "packai_sandbox_atm8" / "minecraft"
    ).resolve()
    if primary.is_dir():
        return primary
    return Path(
        "~/opt/prism-data/pc-mirror/instances/packai_sandbox_atm8/minecraft"
    ).expanduser().resolve()


ATM8_INSTANCE = _atm8_instance_path()

ALLOWED_IMPORT_ROOTS = {
    "argparse",
    "collections",
    "hashlib",
    "json",
    "os",
    "pathlib",
    "re",
    "struct",
    "sys",
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
    assert "datetime.now" not in src
    assert "time.time" not in src
    assert "datetime.utcnow" not in src
    assert re.search(r"\bimport\s+datetime\b", src) is None
    assert re.search(r"\bimport\s+time\b", src) is None


def check_writes_only_out() -> None:
    tree = ast.parse(_src(), filename=str(TOOL))
    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            if node.func.id == "open":
                raise AssertionError("bare open() not allowed")
    for node in ast.walk(tree):
        if not isinstance(node, ast.FunctionDef):
            continue
        if node.name == "write_db":
            continue
        for child in ast.walk(node):
            if isinstance(child, ast.Call) and isinstance(child.func, ast.Attribute):
                if child.func.attr in {"write_text", "write_bytes"}:
                    raise AssertionError(f"write in unexpected func: {node.name}")


def check_no_fabricate_helpers() -> None:
    src = _src()
    for pat in (r"random\.randint", r"fabricate", r"guess_value", r"invent_number"):
        assert re.search(pat, src, re.I) is None, pat


def check_unverified_label() -> None:
    src = _src()
    assert "NOT game-verified" in src
    assert "from bytecode" in src


def _u1(v: int) -> bytes:
    return bytes([v & 0xFF])


def _u2(v: int) -> bytes:
    return struct.pack(">H", v)


def _u4(v: int) -> bytes:
    return struct.pack(">I", v)


def _build_fake_reactor_class() -> bytes:
    """Minimal class mech/FakeReactor with clinit sipush 350 + string EU/t."""
    # CP layout (1-based):
    # 1 Utf8 mech/FakeReactor
    # 2 Class 1
    # 3 Utf8 java/lang/Object
    # 4 Class 3
    # 5 Utf8 energyRate
    # 6 Utf8 I
    # 7 Utf8 <clinit>
    # 8 Utf8 ()V
    # 9 Utf8 Code
    # 10 Utf8 EU/t
    # 11 String 10
    # 12 Utf8 FakeReactor.java
    # 13 Utf8 SourceFile
    # 14 NameAndType 5,6
    # 15 Fieldref 2,14
    strings = [
        b"mech/FakeReactor",  # 1
        None,  # 2 Class
        b"java/lang/Object",  # 3
        None,  # 4 Class
        b"energyRate",  # 5
        b"I",  # 6
        b"<clinit>",  # 7
        b"()V",  # 8
        b"Code",  # 9
        b"EU/t",  # 10
        None,  # 11 String
        b"FakeReactor.java",  # 12
        b"SourceFile",  # 13
        None,  # 14 NameAndType
        None,  # 15 Fieldref
    ]
    cp = bytearray()
    cp += _u2(16)  # count = 16 (index 1..15)
    # 1 Utf8
    cp += _u1(1) + _u2(len(strings[0])) + strings[0]
    # 2 Class -> 1
    cp += _u1(7) + _u2(1)
    # 3 Utf8 Object
    cp += _u1(1) + _u2(len(strings[2])) + strings[2]
    # 4 Class -> 3
    cp += _u1(7) + _u2(3)
    # 5 Utf8 energyRate
    cp += _u1(1) + _u2(len(strings[4])) + strings[4]
    # 6 Utf8 I
    cp += _u1(1) + _u2(len(strings[5])) + strings[5]
    # 7 Utf8 <clinit>
    cp += _u1(1) + _u2(len(strings[6])) + strings[6]
    # 8 Utf8 ()V
    cp += _u1(1) + _u2(len(strings[7])) + strings[7]
    # 9 Utf8 Code
    cp += _u1(1) + _u2(len(strings[8])) + strings[8]
    # 10 Utf8 EU/t
    cp += _u1(1) + _u2(len(strings[9])) + strings[9]
    # 11 String -> 10
    cp += _u1(8) + _u2(10)
    # 12 Utf8 source
    cp += _u1(1) + _u2(len(strings[11])) + strings[11]
    # 13 Utf8 SourceFile
    cp += _u1(1) + _u2(len(strings[12])) + strings[12]
    # 14 NameAndType name=5 desc=6
    cp += _u1(12) + _u2(5) + _u2(6)
    # 15 Fieldref class=2 nat=14
    cp += _u1(9) + _u2(2) + _u2(14)

    # clinit code: sipush 350; putstatic #15; ldc #11; pop; return
    code = bytearray()
    code += bytes([0x11]) + _u2(350)  # sipush 350
    code += bytes([0xB3]) + _u2(15)  # putstatic
    code += bytes([0x12, 11])  # ldc string (keeps literal in use)
    code += bytes([0x57])  # pop
    code += bytes([0xB1])  # return

    code_attr = bytearray()
    code_attr += _u2(2)  # max_stack
    code_attr += _u2(0)  # max_locals
    code_attr += _u4(len(code))
    code_attr += code
    code_attr += _u2(0)  # exception_table_length
    code_attr += _u2(0)  # attributes_count

    out = bytearray()
    out += _u4(0xCAFEBABE)
    out += _u2(0)  # minor
    out += _u2(52)  # major
    out += cp
    out += _u2(0x0021)  # public super
    out += _u2(2)  # this = Class#2
    out += _u2(4)  # super = Class#4
    out += _u2(0)  # interfaces
    # fields: static int energyRate
    out += _u2(1)
    out += _u2(0x0008)  # static
    out += _u2(5)  # name
    out += _u2(6)  # desc
    out += _u2(0)  # no field attrs
    # methods: <clinit>
    out += _u2(1)
    out += _u2(0x0008)  # static
    out += _u2(7)
    out += _u2(8)
    out += _u2(1)  # one attr Code
    out += _u2(9)  # Code
    out += _u4(len(code_attr))
    out += code_attr
    # class attrs: SourceFile
    out += _u2(1)
    out += _u2(13)
    src_body = _u2(12)
    out += _u4(len(src_body)) + src_body
    return bytes(out)


def _run(args: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(TOOL), *args],
        capture_output=True,
        text=True,
        cwd=str(ROOT),
    )


def check_synthetic_build_query_determinism() -> None:
    tmp = Path(tempfile.mkdtemp(prefix="bcnum_chk_"))
    mc = tmp / "minecraft"
    mods = mc / "mods"
    mods.mkdir(parents=True)
    class_bytes = _build_fake_reactor_class()
    with zipfile.ZipFile(mods / "fakereactor-1.0.0.jar", "w") as zf:
        zf.writestr("mech/FakeReactor.class", class_bytes)

    out1 = tmp / "a.json"
    out2 = tmp / "b.json"
    for out in (out1, out2):
        p = _run(
            ["build", "--instance", str(mc), "--out", str(out), "--max-classes", "10"]
        )
        assert p.returncode == 0, p.stderr + p.stdout
    b1, b2 = out1.read_bytes(), out2.read_bytes()
    assert b1 == b2, "determinism failed"
    assert b"\r\n" not in b1
    h1 = hashlib.sha256(b1).hexdigest()
    h2 = hashlib.sha256(b2).hexdigest()
    assert h1 == h2
    art = json.loads(b1.decode("utf-8"))
    assert art["schema"] == 1
    assert "timestamp" not in art
    assert any(r["value"] == 350 for r in art["records"]), art["records"]
    assert any(
        r.get("unit_hint") == "eu/t" for r in art["records"]
    ), [r.get("unit_hint") for r in art["records"]]
    assert all(r.get("note") == "from bytecode; NOT game-verified" for r in art["records"])

    q = _run(["query", "--db", str(out1), "--terms", "fakereactor energy", "--limit", "20"])
    assert q.returncode == 0, q.stderr
    assert "350" in q.stdout
    assert "NOT game-verified" in q.stdout or "unverified" in q.stdout.lower()

    nq = _run(["query", "--db", str(out1), "--terms", "zzz_no_such_thing_qq"])
    assert nq.returncode == 0
    assert "NO_MATCH" in nq.stdout
    assert "NO_NUMBER" in nq.stdout

    # empty jar → 0 records, not error
    mods2 = tmp / "empty_mc" / "mods"
    mods2.mkdir(parents=True)
    with zipfile.ZipFile(mods2 / "empty-1.0.jar", "w") as zf:
        zf.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
    oute = tmp / "empty.json"
    pe = _run(
        [
            "build",
            "--instance",
            str(tmp / "empty_mc"),
            "--out",
            str(oute),
        ]
    )
    assert pe.returncode == 0, pe.stderr + pe.stdout
    arte = json.loads(oute.read_text(encoding="utf-8"))
    assert arte["counts"]["total"] == 0


def check_atm8_sandbox_build() -> None:
    """Gate: full ATM8 sandbox (incl. IC2Classic) must complete RC=0."""
    assert ATM8_INSTANCE.is_dir(), f"missing ATM8 instance: {ATM8_INSTANCE}"
    tmp = Path(tempfile.mkdtemp(prefix="bcnum_atm8_"))
    out1 = tmp / "a.json"
    out2 = tmp / "b.json"
    stdout_blob = ""
    for out in (out1, out2):
        p = _run(
            [
                "build",
                "--instance",
                str(ATM8_INSTANCE),
                "--out",
                str(out),
            ]
        )
        assert p.returncode == 0, p.stderr + p.stdout
        assert "OK build" in p.stdout, p.stdout
        stdout_blob += p.stdout
    art = json.loads(out1.read_text(encoding="utf-8"))
    assert art["counts"]["total"] > 0, art["counts"]
    sc = art["scanned"]
    assert "walk_truncated" in sc
    # When non-zero, samples must be reported (tool stdout + JSON samples).
    if sc["walk_truncated"]:
        assert sc.get("walk_truncated_samples"), sc
        assert "WALK_TRUNCATED_SAMPLES" in stdout_blob, stdout_blob
        assert "walk_truncated=" in stdout_blob
    b1, b2 = out1.read_bytes(), out2.read_bytes()
    assert hashlib.sha256(b1).hexdigest() == hashlib.sha256(b2).hexdigest()
    assert b1 == b2


def main() -> int:
    checks = [
        check_exists,
        check_stdlib_and_no_network,
        check_no_timestamp_on_json_path,
        check_writes_only_out,
        check_no_fabricate_helpers,
        check_unverified_label,
        check_synthetic_build_query_determinism,
        check_atm8_sandbox_build,
    ]
    for fn in checks:
        fn()
        print(f"PASS {fn.__name__}")
    print("OK check_bytecode_numbers")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL {exc}")
        raise SystemExit(1)
