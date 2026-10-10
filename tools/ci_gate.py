#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Pack AI local CI gate: static checks + optional compile/harness.

ROOT is derived from this file (parent of tools/), not caller cwd.
"""
from __future__ import annotations

import argparse
import glob
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import NamedTuple, Optional


DEFAULT_JAVA_HOME = (
    "C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
)


class KnownRedEntry(NamedTuple):
    """One allowlist row. allowed_rc is always 2 when valid."""

    name: str
    allowed_rc: int
    reason: str


def parse_known_red(text: str) -> list[KnownRedEntry]:
    """Parse allowlist text. Raises ValueError on corrupt data lines."""
    entries: list[KnownRedEntry] = []
    for lineno, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = raw.split("\t")
        if len(parts) < 3:
            raise ValueError(
                f"line {lineno}: need name\\tRC\\treason, got {raw!r}"
            )
        name = parts[0].strip()
        rc_s = parts[1].strip()
        reason = parts[2].strip()
        # trailing tabs may join; reason is field 2 only (col 3)
        if len(parts) > 3:
            reason = "\t".join(p.strip() for p in parts[2:]).strip()
        if not name:
            raise ValueError(f"line {lineno}: blank filename")
        if rc_s != "2":
            raise ValueError(
                f"line {lineno}: allowed RC must be 2, got {rc_s!r}"
            )
        if not reason:
            raise ValueError(f"line {lineno}: blank reason (invalid)")
        entries.append(KnownRedEntry(name=name, allowed_rc=2, reason=reason))
    return entries


def classify(rc: int, entry: Optional[KnownRedEntry]) -> str:
    """Classify a check result vs optional allowlist entry.

    Returns: ok | fail | allowlisted | info
    """
    if entry is None:
        return "ok" if rc == 0 else "fail"
    if rc == entry.allowed_rc:
        return "allowlisted"
    if rc == 0:
        return "info"
    return "fail"


def default_root() -> Path:
    return Path(__file__).resolve().parent.parent


def resolve_java_home(cli: Optional[str]) -> str:
    if cli:
        return cli
    env = os.environ.get("JAVA_HOME")
    if env:
        return env
    return DEFAULT_JAVA_HOME


def _decode(data: bytes) -> str:
    return data.decode("utf-8", "replace")


def _first_line(text: str) -> str:
    for line in text.splitlines():
        s = line.strip()
        if s:
            return s
    return ""


def list_checks(root: Path) -> list[Path]:
    pattern = str(root / "tests" / "check_*.py")
    return sorted(Path(p) for p in glob.glob(pattern))


def load_allowlist(root: Path) -> list[KnownRedEntry]:
    path = root / "tools" / "ci_known_red.txt"
    if not path.is_file():
        raise FileNotFoundError(f"missing allowlist: {path}")
    return parse_known_red(path.read_text(encoding="utf-8"))


def validate_allowlist(root: Path, entries: list[KnownRedEntry]) -> list[str]:
    """Return error messages for corrupt / dangling allowlist rows."""
    errs: list[str] = []
    for e in entries:
        target = root / "tests" / e.name
        if not target.is_file():
            errs.append(f"allowlist file missing: tests/{e.name}")
        if e.allowed_rc != 2:
            errs.append(f"allowlist RC not 2: {e.name} rc={e.allowed_rc}")
        if not e.reason.strip():
            errs.append(f"allowlist blank reason: {e.name}")
    return errs


def run_static(root: Path, entries: list[KnownRedEntry]) -> int:
    by_name = {e.name: e for e in entries}
    checks = list_checks(root)
    failed = 0
    known_hits = 0

    for path in checks:
        name = path.name
        proc = subprocess.run(
            [sys.executable, str(path)],
            cwd=str(root),
            capture_output=True,
        )
        out = _decode(proc.stdout) + _decode(proc.stderr)
        first = _first_line(out)
        entry = by_name.get(name)
        kind = classify(proc.returncode, entry)

        if kind == "allowlisted":
            known_hits += 1
            print(f"ALLOW  {name}  rc={proc.returncode}  {first}")
        elif kind == "info":
            print(f"INFO   {name}  rc=0 (allowlisted but green; consider remove)  {first}")
        elif kind == "fail":
            failed += 1
            print(f"FAIL   {name}  rc={proc.returncode}  {first}")
        else:
            print(f"PASS   {name}  rc=0")

    print(
        f"checks={len(checks)} known_red={len(entries)} failed={failed}"
    )
    return 1 if failed else 0


def run_compile(root: Path, java_home: str) -> int:
    java_exe = Path(java_home) / "bin" / "java.exe"
    if not java_exe.is_file():
        print(
            f"JDK path invalid: {java_home}"
            f"（用 --java-home 或設 JAVA_HOME）"
        )
        return 1
    forge = root / "forge" / "1.19.2"
    gradlew = forge / "gradlew.bat"
    cmd = [
        str(gradlew),
        "compileJava",
        "compileTestJava",
        "--rerun-tasks",
        "--console=plain",
        f"-Dorg.gradle.java.home={java_home}",
    ]
    proc = subprocess.run(cmd, cwd=str(forge), capture_output=True)
    out = _decode(proc.stdout) + _decode(proc.stderr)
    if proc.returncode != 0:
        print(f"FAIL compile rc={proc.returncode}")
        print(_first_line(out))
        return 1
    print("PASS compile")
    return 0


def run_harness(root: Path, java_home: str) -> int:
    forge = root / "forge" / "1.19.2"
    init = forge / "tmp-check.gradle"
    if not init.is_file():
        print("missing forge/1.19.2/tmp-check.gradle")
        print("run: python research/gen_tmp_check.py")
        return 1
    java_exe = Path(java_home) / "bin" / "java.exe"
    if not java_exe.is_file():
        print(
            f"JDK path invalid: {java_home}"
            f"（用 --java-home 或設 JAVA_HOME）"
        )
        return 1
    text = init.read_text(encoding="utf-8")
    tasks = re.findall(r"'(run[A-Za-z0-9_]*Check)'", text)
    if not tasks:
        print("FAIL harness: no run*Check tasks in tmp-check.gradle")
        return 1
    gradlew = forge / "gradlew.bat"
    cmd = [
        str(gradlew),
        "-I",
        "tmp-check.gradle",
        *tasks,
        "--console=plain",
        f"-Dorg.gradle.java.home={java_home}",
    ]
    proc = subprocess.run(cmd, cwd=str(forge), capture_output=True)
    out = _decode(proc.stdout) + _decode(proc.stderr)
    if proc.returncode != 0:
        print(f"FAIL harness rc={proc.returncode}")
        print(_first_line(out))
        return 1
    print(f"PASS harness tasks={len(tasks)}")
    return 0


def main(argv: Optional[list[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="Pack AI local CI gate")
    ap.add_argument("--compile", action="store_true")
    ap.add_argument("--harness", action="store_true")
    ap.add_argument("--java-home", default=None)
    ap.add_argument("--root", default=None, help="Override repo root (tests)")
    ap.add_argument("--list", action="store_true")
    args = ap.parse_args(argv)

    root = Path(args.root).resolve() if args.root else default_root()
    java_home = resolve_java_home(args.java_home)

    try:
        entries = load_allowlist(root)
    except (OSError, ValueError) as exc:
        print(f"FAIL allowlist: {exc}")
        return 1

    alist_errs = validate_allowlist(root, entries)
    if alist_errs:
        for msg in alist_errs:
            print(f"FAIL {msg}")
        print(
            f"checks={len(list_checks(root))} known_red={len(entries)} "
            f"failed={len(alist_errs)}"
        )
        return 1

    if args.list:
        print(f"ROOT={root}")
        checks = list_checks(root)
        print(f"checks ({len(checks)}):")
        for p in checks:
            mark = " [known_red]" if p.name in {e.name for e in entries} else ""
            print(f"  {p.name}{mark}")
        print(f"known_red ({len(entries)}):")
        for e in entries:
            print(f"  {e.name}\trc={e.allowed_rc}\t{e.reason}")
        return 0

    rc = run_static(root, entries)

    if args.compile:
        crc = run_compile(root, java_home)
        if crc != 0:
            rc = 1

    if args.harness:
        hrc = run_harness(root, java_home)
        if hrc != 0:
            rc = 1

    return rc


if __name__ == "__main__":
    sys.exit(main())
