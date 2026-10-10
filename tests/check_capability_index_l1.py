#!/usr/bin/env python3
"""Offline self-check for tools/capability_index_l1.py (stdlib only, synthetic jars).

No game, no javap, no network.  Builds a tiny synthetic pack of real-enough
.class files, then asserts build/query/dump behaviour and determinism.

Run: python tests/check_capability_index_l1.py
"""
from __future__ import annotations

import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "capability_index_l1.py"


def _class_bytes(strings: list[str], class_names: list[str]) -> bytes:
    """Craft a minimal class file with a constant pool holding string constants
    (tag 8 -> Utf8) and class refs (tag 7 -> Utf8)."""
    pool = b""
    idx = 1
    str_idx, cls_idx = [], []
    for u in strings:
        b = u.encode("utf-8")
        pool += b"\x01" + struct.pack(">H", len(b)) + b
        str_idx.append(idx)
        idx += 1
    for u in class_names:
        b = u.encode("utf-8")
        pool += b"\x01" + struct.pack(">H", len(b)) + b
        cls_idx.append(idx)
        idx += 1
    for si in str_idx:
        pool += b"\x08" + struct.pack(">H", si)
    for ci in cls_idx:
        pool += b"\x07" + struct.pack(">H", ci)
    n_entries = (idx - 1) + len(str_idx) + len(cls_idx)
    header = struct.pack(">IHHH", 0xCAFEBABE, 0, 52, n_entries + 1)
    return header + pool + b"\x00" * 6


def _write_jar(path: Path, classes: dict[str, bytes]) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for name, data in sorted(classes.items()):
            zf.writestr(name, data)


def _make_pack(tmp: Path, name: str = "mods") -> Path:
    mods = tmp / name
    if mods.exists():
        shutil.rmtree(mods)
    mods.mkdir()
    _write_jar(
        mods / "cool-storage.jar",
        {
            "com/cool/StorageDrawer.class": _class_bytes(
                ["autocraft", "tile.cool.drawer", "Items Drawer", "wireless"],
                ["com/cool/StorageDrawer", "net/minecraft/world/item/ItemStack"],
            ),
            "com/cool/AutocraftMachine.class": _class_bytes(
                ["assembler", "pattern"], ["com/cool/AutocraftMachine"]
            ),
        },
    )
    _write_jar(
        mods / "unrelated-foo.jar",
        {"com/foo/Foo.class": _class_bytes(["hello world"], ["com/foo/Foo"])},
    )
    return mods


def _run(args, env=None, db=None):
    e = dict(os.environ)
    if env:
        e.update(env)
    p = subprocess.run([sys.executable, str(TOOL), *args], capture_output=True, text=True, env=e)
    if p.returncode != 0:
        raise AssertionError(f"rc={p.returncode}\nstdout={p.stdout}\nstderr={p.stderr}")
    return p


def _build(mods: Path, db: Path, extra=None, env=None):
    args = ["build", "--mods", str(mods), "--db", str(db), "--pack-name", "synth"]
    if extra:
        args += extra
    return _run(args, env=env)


def _query(db: Path, terms: str, extra=None, env=None):
    args = ["query", "--db", str(db), "--terms", terms, "--json"]
    if extra:
        args += extra
    return _run(args, env=env).stdout


def test_build_and_hits(tmp: Path):
    mods = _make_pack(tmp, "mods_hits")
    db = tmp / "hits.sqlite"
    _build(mods, db)
    out = json.loads(_query(db, "autocraft,drawer,assembler"))
    cands = out["candidates"]
    assert cands, "expected at least one candidate"
    assert cands[0]["mod"] == "cool-storage.jar", cands[0]
    assert all(c["mod"] != "unrelated-foo.jar" for c in cands), "unrelated mod must not match"
    assert out["mode"] in ("bm25", "substr"), out["mode"]
    assert any(e["kind"] == "literal" for e in cands[0]["evidence"]), cands[0]["evidence"]
    # a query with no signal returns no candidates (honest miss, no padding)
    miss = json.loads(_query(db, "zzznotfoundzzz"))
    assert miss["candidates"] == [], miss


def test_determinism(tmp: Path):
    mods = _make_pack(tmp, "mods_det")
    a, b = tmp / "a.sqlite", tmp / "b.sqlite"
    # different hash seeds must not change the index content or ranking order
    _build(mods, a, env={"PYTHONHASHSEED": "0"})
    _build(mods, b, env={"PYTHONHASHSEED": "12345"})
    da = tmp / "a.txt"
    db_ = tmp / "b.txt"
    _run(["dump", "--db", str(a), "--out", str(da)])
    _run(["dump", "--db", str(b), "--out", str(db_)])
    assert da.read_bytes() == db_.read_bytes(), "dump not byte-identical across hash seeds"
    qa = _query(a, "storage,fluid,wireless", env={"PYTHONHASHSEED": "0"})
    qb = _query(b, "storage,fluid,wireless", env={"PYTHONHASHSEED": "777"})
    assert qa == qb, "query output not identical across hash seeds"


def test_no_leak(tmp: Path):
    mods = _make_pack(tmp, "mods_leak")
    db = tmp / "leak.sqlite"
    _build(mods, db)
    q = _query(db, "storage")
    d = _run(["dump", "--db", str(db)]).stdout
    for blob in (q, d):
        assert os.path.abspath(str(mods)) not in blob, "absolute mods path leaked"
        assert re.search(r"\d{4}-\d{2}-\d{2}", blob) is None, "timestamp-like date leaked"


def test_refs_opt_in(tmp: Path):
    mods = _make_pack(tmp, "mods_refs")
    db_plain = tmp / "plain.sqlite"
    db_refs = tmp / "refs.sqlite"
    _build(mods, db_plain)
    _build(mods, db_refs, extra=["--refs"])
    plain = _run(["stats", "--db", str(db_plain)]).stdout
    refs = _run(["stats", "--db", str(db_refs)]).stdout
    assert "kind=literal:" in plain and "kind=class:" in plain, plain
    assert "kind=ref: 0" not in refs, "with --refs there must be ref rows"
    assert "kind=ref:" in refs, refs


def main() -> int:
    assert TOOL.is_file(), TOOL
    tmp = Path(tempfile.mkdtemp(prefix="l1_check_"))
    try:
        test_build_and_hits(tmp)
        test_determinism(tmp)
        test_no_leak(tmp)
        test_refs_opt_in(tmp)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print("check_capability_index_l1 OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
