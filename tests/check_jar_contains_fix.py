#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Assert the installed Pack AI jar contains T2 fail-closed symbols + lang keys.

Usage:
  python tests/check_jar_contains_fix.py
  python tests/check_jar_contains_fix.py <jar path>

Extracts to %LOCALAPPDATA%/Temp/packai_jar_check then javap -p -c.
Do not pipe unzip -p into javap (MSYS can exit 0 with no classes).
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

CLASS = "com.skps9.packai.logic.AskReplyScrub"
DEST = Path(os.environ.get("LOCALAPPDATA", str(Path.home() / "AppData" / "Local"))) / "Temp" / "packai_jar_check"
PRISM_MODS = (
    Path.home()
    / "Documents"
    / "PrismLauncher-Windows-MSVC-Portable-8.0"
    / "PrismLauncher-Windows-MSVC-Portable-8.0"
    / "instances"
    / "AI_test_NFWC_DIM"
    / "minecraft"
    / "mods"
)
NEED_JAVAP = ("playerSafeFacts", "isPlayerSafeLine", "leftoverToolMarkup")
NEED_LANG = ("ask_body_unavailable", "llm_style_notools")
LANG_ENTRY = "assets/packai/lang/zh_cn.json"


def find_default_jar() -> Path | None:
    if not PRISM_MODS.is_dir():
        return None
    jars = sorted(PRISM_MODS.glob("packai*.jar"), key=lambda p: p.stat().st_mtime, reverse=True)
    return jars[0] if jars else None


def find_javap() -> str | None:
    w = shutil.which("javap")
    if w:
        return w
    home = os.environ.get("JAVA_HOME")
    if home:
        exe = Path(home) / "bin" / ("javap.exe" if os.name == "nt" else "javap")
        if exe.is_file():
            return str(exe)
    return None


def extract_jar(jar: Path, dest: Path) -> None:
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True, exist_ok=True)
    unzip = shutil.which("unzip")
    if unzip:
        r = subprocess.run(
            [unzip, "-o", str(jar), "-d", str(dest)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        if r.returncode == 0:
            return
        print("WARN unzip failed, falling back to zipfile:", r.stderr[:300])
    with zipfile.ZipFile(jar) as zf:
        zf.extractall(dest)


def main() -> None:
    jar = Path(sys.argv[1]) if len(sys.argv) > 1 else find_default_jar()
    if jar is None or not jar.is_file():
        print("FAIL no packai jar (pass path or install into Prism mods/)")
        sys.exit(1)
    print("jar:", jar)

    extract_jar(jar, DEST)

    javap = find_javap()
    if not javap:
        print("FAIL javap not found (JAVA_HOME / PATH)")
        sys.exit(1)
    r = subprocess.run(
        [javap, "-p", "-c", "-cp", str(DEST), CLASS],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    dump = (r.stdout or "") + "\n" + (r.stderr or "")
    if r.returncode != 0:
        print("FAIL javap exit", r.returncode)
        print(dump[:1500])
        sys.exit(1)
    missing = [n for n in NEED_JAVAP if n not in dump]
    if missing:
        print("FAIL javap missing", missing)
        sys.exit(1)
    print("OK javap", ", ".join(NEED_JAVAP))

    lang_path = DEST / LANG_ENTRY.replace("/", os.sep)
    if not lang_path.is_file():
        print("FAIL jar missing", LANG_ENTRY)
        sys.exit(1)
    lang = lang_path.read_text(encoding="utf-8", errors="replace")
    miss_lang = [k for k in NEED_LANG if k not in lang]
    if miss_lang:
        print("FAIL zh_cn.json missing", miss_lang)
        sys.exit(1)
    print("OK lang", ", ".join(NEED_LANG))
    print("check_jar_contains_fix OK")


if __name__ == "__main__":
    main()
