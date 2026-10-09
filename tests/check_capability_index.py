#!/usr/bin/env python3
"""Self-check for tools/mine_capability_index.py (stdlib only, synthetic jars)."""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "mine_capability_index.py"
QUERIES = ROOT / "tools" / "capability_queries.json"


def _write_jar(path: Path, entries: dict[str, bytes]) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for name, data in sorted(entries.items()):
            zf.writestr(name, data)


def _run_tool(mods_dir: Path, out: Path, extra: list[str] | None = None) -> dict:
    cmd = [
        sys.executable,
        str(TOOL),
        "--pack-name",
        "synth",
        "--mods",
        str(mods_dir),
        "--queries",
        str(QUERIES),
        "--out",
        str(out),
        "--max-mod-candidates",
        "10",
        "--max-classes-per-mod",
        "5",
    ]
    if extra:
        cmd.extend(extra)
    # Prefer PATH javap; optional --javap from env
    env_javap = os.environ.get("PACKAI_JAVAP")
    if env_javap:
        cmd.extend(["--javap", env_javap])
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        raise AssertionError(
            f"tool rc={p.returncode}\nstdout={p.stdout}\nstderr={p.stderr}"
        )
    return json.loads(out.read_text(encoding="utf-8"))


def _queries_one(need_id: str, **overrides) -> Path:
    """Write a tiny queries json with one need (for focused tests)."""
    base = {
        "need_id": need_id,
        "question": "q",
        "keywords": ["storage", "jsu"],
        "class_tokens": ["JSU", "Storage"],
        "numeric_patterns": ["INV_SIZE", "MAX_STACK", "SIZE"],
        "logic_patterns": ["getMaxStackSize", "isItemValid", "func_77976_d"],
    }
    base.update(overrides)
    path = Path(tempfile.mkdtemp(prefix="capq_")) / "q.json"
    path.write_text(json.dumps([base], ensure_ascii=False), encoding="utf-8")
    return path


def test_determinism_and_hits():
    tmp = Path(tempfile.mkdtemp(prefix="cap_chk_"))
    mods = tmp / "mods"
    mods.mkdir()
    # hit jar: filename + mcmod.info
    _write_jar(
        mods / "cool-storage-mod.jar",
        {
            "mcmod.info": b'[{"name":"Cool Storage","description":"jsu-like storage drawer"}]\n',
            "assets/cool/lang/en_us.lang": b"tile.cool.chest.name=Cool Chest\n",
        },
    )
    # miss jar
    _write_jar(
        mods / "unrelated-foo.jar",
        {"mcmod.info": b'[{"name":"Foo","description":"nothing relevant"}]\n'},
    )
    qpath = _queries_one(
        "hit_test",
        keywords=["storage", "jsu", "drawer"],
    )
    out1 = tmp / "a1.json"
    out2 = tmp / "a2.json"
    # monkey via subprocess args — pass custom queries
    for out in (out1, out2):
        cmd = [
            sys.executable,
            str(TOOL),
            "--pack-name",
            "synth",
            "--mods",
            str(mods),
            "--queries",
            str(qpath),
            "--out",
            str(out),
            "--max-javap-calls",
            "0",
        ]
        p = subprocess.run(cmd, capture_output=True, text=True)
        assert p.returncode == 0, p.stderr

    b1 = out1.read_bytes()
    b2 = out2.read_bytes()
    assert b1 == b2, "determinism failed: out1 != out2"

    art = json.loads(b1.decode("utf-8"))
    q = art["queries"][0]
    mods_hit = {c["mod"] for c in q["candidates"]}
    assert "cool-storage-mod.jar" in mods_hit, mods_hit
    assert "unrelated-foo.jar" not in mods_hit

    # no-hit query
    qmiss = _queries_one("miss_test", keywords=["zzznotfoundzzz"], class_tokens=["Nope"])
    outm = tmp / "miss.json"
    cmd = [
        sys.executable,
        str(TOOL),
        "--pack-name",
        "synth",
        "--mods",
        str(mods),
        "--queries",
        str(qmiss),
        "--out",
        str(outm),
        "--max-javap-calls",
        "0",
    ]
    p = subprocess.run(cmd, capture_output=True, text=True)
    assert p.returncode == 0, p.stderr
    artm = json.loads(outm.read_text(encoding="utf-8"))
    assert artm["queries"][0]["candidates"] == []
    assert artm["queries"][0]["unresolved"], "expected unresolved on miss"

    shutil.rmtree(tmp, ignore_errors=True)
    shutil.rmtree(qpath.parent, ignore_errors=True)
    shutil.rmtree(qmiss.parent, ignore_errors=True)


def test_cap_javap():
    tmp = Path(tempfile.mkdtemp(prefix="cap_cap_"))
    mods = tmp / "mods"
    mods.mkdir()
    # Minimal class files: empty invalid .class will fail javap but still consume budget
    # Use tiny fake class bytes — javap will fail; calls still count.
    fake = bytes.fromhex(
        "cafe babe 0000 0034 0001 0001 0000 0000 0000 0000 0000"
    )
    entries = {
        "mcmod.info": b'[{"name":"Storage"}]\n',
        "com/example/StorageA.class": fake + b"\x00" * 40,
        "com/example/StorageB.class": fake + b"\x01" * 40,
        "com/example/StorageC.class": fake + b"\x02" * 40,
        "com/example/JSUThing.class": fake + b"\x03" * 40,
    }
    _write_jar(mods / "storage-cap.jar", entries)
    qpath = _queries_one(
        "cap",
        keywords=["storage"],
        class_tokens=["Storage", "JSU"],
    )
    out = tmp / "out.json"
    cmd = [
        sys.executable,
        str(TOOL),
        "--pack-name",
        "synth",
        "--mods",
        str(mods),
        "--queries",
        str(qpath),
        "--out",
        str(out),
        "--max-javap-calls",
        "2",
        "--max-classes-per-mod",
        "40",
    ]
    env_javap = os.environ.get("PACKAI_JAVAP") or shutil.which("javap")
    if env_javap:
        cmd.extend(["--javap", env_javap])
    p = subprocess.run(cmd, capture_output=True, text=True)
    assert p.returncode == 0, p.stderr + p.stdout
    art = json.loads(out.read_text(encoding="utf-8"))
    caps = art["caps"]
    assert caps["javap_calls_used"] <= 2, caps
    assert caps["caps_hit"] is True, caps
    shutil.rmtree(tmp, ignore_errors=True)
    shutil.rmtree(qpath.parent, ignore_errors=True)


def test_lang_runtime_visible():
    tmp = Path(tempfile.mkdtemp(prefix="cap_lang_"))
    mods = tmp / "mods"
    mods.mkdir()
    _write_jar(
        mods / "storage-lang.jar",
        {
            "mcmod.info": b'[{"name":"Storage"}]\n',
            "assets/x/lang/en_us.lang": b"tile.x.chest.name=Storage Chest\n",
        },
    )
    qpath = _queries_one("lang", keywords=["storage", "chest"])
    out = tmp / "lang.json"
    cmd = [
        sys.executable,
        str(TOOL),
        "--pack-name",
        "synth",
        "--mods",
        str(mods),
        "--queries",
        str(qpath),
        "--out",
        str(out),
        "--max-javap-calls",
        "0",
    ]
    p = subprocess.run(cmd, capture_output=True, text=True)
    assert p.returncode == 0, p.stderr
    art = json.loads(out.read_text(encoding="utf-8"))
    c = art["queries"][0]["candidates"][0]
    assert c["lang_format_detected"] == "lang", c
    assert c["runtime_visible"] is False, c

    # json lang → runtime_visible true
    mods2 = tmp / "mods2"
    mods2.mkdir()
    _write_jar(
        mods2 / "storage-json.jar",
        {
            "mcmod.info": b'[{"name":"Storage"}]\n',
            "assets/x/lang/en_us.json": b'{"tile.x.chest":"Storage Chest"}\n',
        },
    )
    out2 = tmp / "json.json"
    cmd[-5] = str(mods2)  # replace --mods value — fragile; rebuild cmd
    cmd = [
        sys.executable,
        str(TOOL),
        "--pack-name",
        "synth",
        "--mods",
        str(mods2),
        "--queries",
        str(qpath),
        "--out",
        str(out2),
        "--max-javap-calls",
        "0",
    ]
    p = subprocess.run(cmd, capture_output=True, text=True)
    assert p.returncode == 0, p.stderr
    art2 = json.loads(out2.read_text(encoding="utf-8"))
    c2 = art2["queries"][0]["candidates"][0]
    assert c2["lang_format_detected"] == "json", c2
    assert c2["runtime_visible"] is True, c2

    shutil.rmtree(tmp, ignore_errors=True)
    shutil.rmtree(qpath.parent, ignore_errors=True)


def test_no_timestamp():
    tmp = Path(tempfile.mkdtemp(prefix="cap_ts_"))
    mods = tmp / "mods"
    mods.mkdir()
    _write_jar(
        mods / "storage-ts.jar",
        {"mcmod.info": b'[{"name":"Storage"}]\n'},
    )
    qpath = _queries_one("ts", keywords=["storage"])
    out = tmp / "ts.json"
    cmd = [
        sys.executable,
        str(TOOL),
        "--pack-name",
        "synth",
        "--mods",
        str(mods),
        "--queries",
        str(qpath),
        "--out",
        str(out),
        "--max-javap-calls",
        "0",
    ]
    p = subprocess.run(cmd, capture_output=True, text=True)
    assert p.returncode == 0, p.stderr
    s = out.read_text(encoding="utf-8")
    assert re.search(r"\d{4}-\d{2}-\d{2}", s) is None, "timestamp-like date found"
    abs_prefix = os.path.abspath(str(mods))
    assert abs_prefix not in s, "absolute mods path leaked into artifact"
    shutil.rmtree(tmp, ignore_errors=True)
    shutil.rmtree(qpath.parent, ignore_errors=True)


def main() -> int:
    assert TOOL.is_file(), TOOL
    assert QUERIES.is_file(), QUERIES
    test_determinism_and_hits()
    test_cap_javap()
    test_lang_runtime_visible()
    test_no_timestamp()
    print("check_capability_index OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
