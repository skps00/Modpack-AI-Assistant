#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static + synthetic gate for tools/source_numbers.py."""
from __future__ import annotations

import ast
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "source_numbers.py"
REPOS = ROOT / "tools" / "source_repos.json"

ALLOWED_IMPORT_ROOTS = {
    "argparse",
    "hashlib",
    "importlib",
    "io",
    "json",
    "os",
    "pathlib",
    "re",
    "shutil",
    "sys",
    "tarfile",
    "tempfile",
    "typing",
    "urllib",
    "zipfile",
    "__future__",
}

# Network modules allowed only because fetch needs them; build/query must not
# require live network (offline-cache-only + seeded cache proven below).
ALLOWED_NET = {"urllib"}


def _src() -> str:
    return TOOL.read_text(encoding="utf-8")


def check_exists() -> None:
    assert TOOL.is_file(), f"missing {TOOL}"
    assert REPOS.is_file(), f"missing {REPOS}"
    doc = json.loads(REPOS.read_text(encoding="utf-8"))
    assert "repos" in doc
    assert "mekanism" in doc["repos"]
    assert doc["repos"]["ic2"]["license"] == "unknown"


def check_stdlib_only() -> None:
    tree = ast.parse(_src(), filename=str(TOOL))
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                root = alias.name.split(".")[0]
                assert root in ALLOWED_IMPORT_ROOTS, f"non-stdlib import: {alias.name}"
        elif isinstance(node, ast.ImportFrom):
            if node.module is None:
                continue
            root = node.module.split(".")[0]
            assert root in ALLOWED_IMPORT_ROOTS, f"non-stdlib import: {node.module}"


def check_no_timestamp_on_json_path() -> None:
    src = _src()
    assert "datetime.now" not in src
    assert "time.time" not in src
    assert "datetime.utcnow" not in src
    assert re.search(r"\bimport\s+datetime\b", src) is None
    assert re.search(r"\bimport\s+time\b", src) is None


def check_unverified_label() -> None:
    src = _src()
    assert "NOT game-verified" in src
    assert "from mod source" in src


def check_no_source_text_in_schema() -> None:
    src = _src()
    assert "NOTE_S" in src
    # records must not include a 'source' or 'code' blob field in append schema
    assert '"source":' not in src
    assert '"code":' not in src


def check_ic2_skipped_in_map() -> None:
    doc = json.loads(REPOS.read_text(encoding="utf-8"))
    assert not doc["repos"]["ic2"].get("license") or doc["repos"]["ic2"]["license"] == "unknown"


def _run(args: list[str], *, env: dict | None = None) -> subprocess.CompletedProcess:
    import os

    base = os.environ.copy()
    if env:
        base.update(env)
    return subprocess.run(
        [sys.executable, str(TOOL), *args],
        capture_output=True,
        text=True,
        cwd=str(ROOT),
        env=base,
    )


def check_parser_and_synthetic_build_query() -> None:
    # Import parser directly
    sys.path.insert(0, str(ROOT / "tools"))
    import source_numbers as sn  # type: ignore

    sample = """
package mekanism.generators.common.config;
public class GeneratorsConfig {
    // The default burn rate of the fission reactor.
    static {
        // placeholder
    }
    public void init() {
        defaultBurnRate = CachedDoubleValue.wrap(this, builder.comment("burn")
              .defineInRange("defaultBurnRate", 0.1D, 0.001D, 1D));
        burnPerAssembly = CachedLongValue.wrap(this, builder
              .defineInRange("burnPerAssembly", 1L, 1, 1000000));
        fissionCasingHeatCapacity = CachedDoubleValue.wrap(this, builder
              .defineInRange("casingHeatCapacity", 1_000D, 1, 1_000_000));
    }
    public static final int FUEL_BURN_RATE = 42; // mB/t fuel burn
    public static final int IS_ENABLED = 1; // flag
    public static final int ORDINAL_DUMMY = 0;
}
"""
    recs = sn.parse_java_text(sample, file_rel="src/GeneratorsConfig.java")
    fields = {r["field"]: r for r in recs}
    assert "defaultBurnRate" in fields, fields
    assert fields["defaultBurnRate"]["value"] == 0.1
    assert "burnPerAssembly" in fields
    assert "casingHeatCapacity" in fields
    assert fields["casingHeatCapacity"]["value"] == 1000.0
    assert "FUEL_BURN_RATE" in fields
    assert fields["FUEL_BURN_RATE"]["unit"] == "mB/t"
    assert "IS_ENABLED" not in fields  # 0/1 boolish skipped
    assert "ORDINAL_DUMMY" not in fields

    tmp = Path(tempfile.mkdtemp(prefix="srcnum_chk_"))
    mc = tmp / "minecraft"
    mods = mc / "mods"
    mods.mkdir(parents=True)
    # Minimal mods.toml jar
    toml = (
        'modLoader="javafml"\n'
        "[[mods]]\n"
        'modId="fakereactor"\n'
        'version="1.0.0"\n'
    )
    with zipfile.ZipFile(mods / "fakereactor-1.0.0.jar", "w") as zf:
        zf.writestr("META-INF/mods.toml", toml)

    # Seed repo map override via writing cache as if fetched for a mapped mod.
    # Use create from real map? Better: inject into a temp copy — instead seed
    # cache for a mod we add by patching REPOS is forbidden. Use biggerreactors
    # jar name? Simpler path: call parse + manually write cache + fetch_index
    # for modId present in source_repos — plant a fake "powah" jar.
    toml_p = (
        'modLoader="javafml"\n'
        "[[mods]]\n"
        'modId="powah"\n'
        'version="4.0.11"\n'
    )
    with zipfile.ZipFile(mods / "Powah-4.0.11.jar", "w") as zf:
        zf.writestr("META-INF/mods.toml", toml_p)
    # Unmapped mod for negative control
    toml_u = (
        'modLoader="javafml"\n'
        "[[mods]]\n"
        'modId="notarealmodxyz"\n'
        'version="0.0.1"\n'
    )
    with zipfile.ZipFile(mods / "notarealmodxyz-0.0.1.jar", "w") as zf:
        zf.writestr("META-INF/mods.toml", toml_u)
        # tiny class so bytecode fallback has something? optional empty ok

    cache = tmp / "cache"
    cache.mkdir()
    commit = "a" * 40
    repo = "owmii/Powah"
    cache_art = {
        "schema": 1,
        "repo": repo,
        "commit": commit,
        "stats": {"java_files": 1, "classes_with_hits": 1},
        "records": [
            {
                "class": "owmii.powah.api.energy.Energy",
                "field": "transferRate",
                "value": 100,
                "value_kind": "int",
                "unit": "FE/t",
                "file": "src/Energy.java",
                "line": 10,
            }
        ],
        "note": "from mod source; NOT game-verified",
    }
    cp = cache / f"owmii_Powah@{commit}.json"
    cp.write_bytes(
        (json.dumps(cache_art, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode(
            "utf-8"
        )
    )
    fetch_index = {
        "schema": 1,
        "mods": {
            "powah": {
                "status": "cached",
                "repo": repo,
                "ref": "4.0.11",
                "commit": commit,
                "match_quality": "exact_tag",
                "jar_version": "4.0.11",
                "license": "LGPL-3.0",
            }
        },
    }
    (cache / "fetch_index.json").write_text(
        json.dumps(fetch_index, indent=2, sort_keys=True) + "\n", encoding="utf-8"
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
                "--cache-dir",
                str(cache),
            ]
        )
        assert p.returncode == 0, p.stderr + p.stdout
        assert "sha256=" in p.stdout
        assert "source_skipped:notarealmodxyz:not_in_repo_map" in p.stdout
    b1, b2 = out1.read_bytes(), out2.read_bytes()
    assert b1 == b2
    h1 = hashlib.sha256(b1).hexdigest()
    h2 = hashlib.sha256(b2).hexdigest()
    assert h1 == h2
    art = json.loads(b1.decode("utf-8"))
    assert art["schema"] == 1
    assert "timestamp" not in art
    s_hits = [r for r in art["records"] if r.get("tier") == "S"]
    assert any(r["field"] == "transferRate" and r["value"] == 100 for r in s_hits)
    assert all(r.get("note") == "from mod source; NOT game-verified" for r in s_hits)
    skipped_mods = {x["mod"] for x in art["source_skipped"]}
    assert "notarealmodxyz" in skipped_mods

    q = _run(["query", "--db", str(out1), "--terms", "powah transfer energy", "--limit", "20"])
    assert q.returncode == 0, q.stderr
    assert "transferRate" in q.stdout
    assert "100" in q.stdout

    nq = _run(["query", "--db", str(out1), "--terms", "zzz_no_such_thing_qqq"])
    assert nq.returncode == 0
    assert "NO_MATCH" in nq.stdout

    # offline-cache-only must succeed without network (cache already present)
    p_off = _run(
        [
            "fetch",
            "--instance",
            str(mc),
            "--out-dir",
            str(cache),
            "--offline-cache-only",
            "--mods-filter",
            r"Powah",
        ]
    )
    assert p_off.returncode == 0, p_off.stderr + p_off.stdout
    assert "offline=True" in p_off.stdout or "offline=True" in p_off.stdout.replace(" ", "")


def main() -> int:
    checks = [
        check_exists,
        check_stdlib_only,
        check_no_timestamp_on_json_path,
        check_unverified_label,
        check_no_source_text_in_schema,
        check_ic2_skipped_in_map,
        check_parser_and_synthetic_build_query,
    ]
    for fn in checks:
        fn()
        print(f"PASS {fn.__name__}")
    print("OK check_source_numbers")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL {exc}")
        raise SystemExit(1)
