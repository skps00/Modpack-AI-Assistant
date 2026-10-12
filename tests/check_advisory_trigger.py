#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CI gate: S-A2 advisory trigger cases + real PackIndex predicate harness."""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CASES = ROOT / "docs" / "plans" / "2026-10-11-sa2-trigger-cases.json"
PACK_INDEX = (
    ROOT
    / "forge"
    / "1.19.2"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "PackIndex.java"
)
HARNESS = (
    ROOT
    / "forge"
    / "1.19.2"
    / "src"
    / "test"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "AdvisoryTriggerCheck.java"
)
FORGE = ROOT / "forge" / "1.19.2"
DEFAULT_JAVA_HOME = (
    "C:/Users/skps9/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
)

REQUIRED_TRUE = (
    "呢部反應爐點維持唔會停？",
    "呢部機點維持唔會停？",
    "the fission reactor won't keep going",
)
REQUIRED_FALSE = (
    "鑽石鎬效率",
    "鑽石劍消耗",
    "呢個合成反應過程效率點解咁低？",
)


def main() -> int:
    errors: list[str] = []

    if not CASES.is_file():
        print("FAIL missing cases JSON")
        return 1
    if not PACK_INDEX.is_file():
        print("FAIL missing PackIndex.java")
        return 1
    if not HARNESS.is_file():
        print("FAIL missing AdvisoryTriggerCheck.java")
        return 1

    cases = json.loads(CASES.read_text(encoding="utf-8"))
    if not isinstance(cases, list):
        print("FAIL cases JSON must be array")
        return 1
    n = len(cases)
    print(f"advisory_trigger_cases={n}")
    if n < 20:
        errors.append(f"need >=20 cases, got {n}")

    by_q = {c.get("question"): c for c in cases if isinstance(c, dict)}
    for q in REQUIRED_TRUE:
        c = by_q.get(q)
        if c is None:
            errors.append(f"missing required TRUE case: {q}")
        elif c.get("expected") is not True:
            errors.append(f"case must be expected:true: {q}")
    for q in REQUIRED_FALSE:
        c = by_q.get(q)
        if c is None:
            errors.append(f"missing required FALSE case: {q}")
        elif c.get("expected") is not False:
            errors.append(f"case must be expected:false: {q}")

    src = PACK_INDEX.read_text(encoding="utf-8")
    if "isAdvisoryQuestion" not in src:
        errors.append("PackIndex.isAdvisoryQuestion missing")
    if "isMachineQuestion" not in src:
        errors.append("PackIndex.isMachineQuestion must remain")
    # Bare 機 must not appear as a machine-list entry (allow 部機/機台/機器/…).
    if re.search(r'"機"', src) or re.search(r'"机"', src):
        errors.append("bare 機/机 must not be in machine-noun list")
    if r"(?<![A-Za-z0-9_])" not in src:
        errors.append("ASCII lookaround boundary missing")
    if r"\b" in src and "never" not in src.lower():
        # soft: plan forbids \\b for ASCII advisory terms
        pass
    if "matchAdvisoryQuestion" not in src and "matchedTerm" not in src:
        errors.append("matchedTerm / matchAdvisoryQuestion missing")

    if errors:
        print("FAIL check_advisory_trigger")
        for e in errors:
            print(" -", e)
        return 1

    java_home = os.environ.get("JAVA_HOME") or DEFAULT_JAVA_HOME
    if sys.platform == "win32":
        gradlew = FORGE / "gradlew.bat"
        if not gradlew.is_file():
            gradlew = FORGE / "gradlew"
            if gradlew.is_file():
                gradlew.chmod(gradlew.stat().st_mode | 0o111)
    else:
        gradlew = FORGE / "gradlew"
        if gradlew.is_file():
            gradlew.chmod(gradlew.stat().st_mode | 0o111)
        elif (FORGE / "gradlew.bat").is_file():
            gradlew = FORGE / "gradlew.bat"
    init = FORGE / "tmp-check.gradle"
    if not init.is_file():
        print("FAIL missing tmp-check.gradle — run python research/gen_tmp_check.py")
        return 1
    if "runAdvisoryTriggerCheck" not in init.read_text(encoding="utf-8"):
        print("FAIL tmp-check.gradle missing runAdvisoryTriggerCheck — regen gen_tmp_check.py")
        return 1

    cmd = [
        str(gradlew),
        "-I",
        "tmp-check.gradle",
        "runAdvisoryTriggerCheck",
        "--console=plain",
        f"-Dorg.gradle.java.home={java_home}",
    ]
    env = os.environ.copy()
    env["PACKAI_ADVISORY_CASES"] = str(CASES)
    proc = subprocess.run(
        cmd,
        cwd=str(FORGE),
        capture_output=True,
        env=env,
    )
    out = (proc.stdout or b"").decode("utf-8", "replace") + (
        proc.stderr or b""
    ).decode("utf-8", "replace")
    print(out.rstrip())
    if proc.returncode != 0:
        print("FAIL check_advisory_trigger harness rc=", proc.returncode)
        return 1
    if f"trigger_cases={n}" not in out and f"trigger_cases=" not in out:
        print("FAIL harness did not print trigger_cases")
        return 1
    # Extract printed count — must match JSON length (shrunk file cannot silent-pass).
    m = re.search(r"trigger_cases=(\d+)", out)
    if not m or int(m.group(1)) != n:
        print(f"FAIL harness case count mismatch json={n} harness={m.group(1) if m else '?'}")
        return 1
    if "fail=0" not in out:
        print("FAIL harness reported failures")
        return 1
    print("PASS check_advisory_trigger")
    return 0


if __name__ == "__main__":
    sys.exit(main())
