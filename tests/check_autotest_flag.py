#!/usr/bin/env python3
"""Static gate: autotest flag is build-opt-in and the harness has one tick hook."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge" / "1.19.2"
JAVA_ROOTS = [
    FORGE / "src" / "main" / "java",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java",
]
HARNESS = (
    FORGE
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "client"
    / "autotest"
    / "AutoTestHarness.java"
)
FLAG = FORGE / "src" / "autotest" / "resources" / "packai-autotest.flag"
SKIP_DIR = {"build", ".gradle", ".git", "out", "bin", ".gradle-user-home", "caches", "node_modules"}
WRITE_RE = re.compile(
    r"Files\.(?:writeString|write|delete|deleteIfExists|createDirectories|newBufferedWriter|newOutputStream|copy|move)\s*\([^;]*;"
    r"|new\s+PrintWriter\s*\([^;]*;"
    r"|new\s+FileOutputStream\s*\([^;]*;",
    re.S,
)
END_GUARD = re.compile(
    r"if\s*\(\s*event\.phase\s*!=\s*TickEvent\.Phase\.END\s*\)\s*\{[^{}]*\breturn\b[^{}]*\}\s*AutoTestHarness\.tick\s*\(",
    re.S,
)
END_BLOCK = re.compile(
    r"if\s*\(\s*(?:event\.)?phase\s*==\s*TickEvent\.Phase\.END\s*\)\s*\{[^{}]*AutoTestHarness\.tick\s*\(",
    re.S,
)


def fail(msg: str) -> None:
    print(msg)
    raise SystemExit(1)


def strip_comments(src: str) -> str:
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"//.*?$", "", src, flags=re.M)


def iter_java():
    for path in ROOT.rglob("*.java"):
        rel = path.relative_to(ROOT)
        if any(part in SKIP_DIR or part.startswith(".") for part in rel.parts):
            continue
        yield path


def check_gradle() -> None:
    text = (FORGE / "build.gradle").read_text(encoding="utf-8")
    key = "src/autotest/resources"
    marker = "if (project.hasProperty('packaiAutotest'))"
    start = text.find(marker)
    if start < 0:
        fail("build.gradle: missing hasProperty('packaiAutotest')")
    brace = text.find("{", start)
    if brace < 0:
        fail("build.gradle: autotest if has no brace")
    depth = 0
    end = -1
    for i in range(brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                end = i
                break
    if end < 0:
        fail("build.gradle: autotest if block not closed")
    block = text[start : end + 1]
    if key not in block:
        fail("build.gradle: autotest resources dir not inside hasProperty block")
    check_flag_archive_base(block)
    rest = text[:start] + text[end + 1 :]
    if key in rest:
        fail("build.gradle: autotest resources dir also outside hasProperty block")


def check_flag_archive_base(block: str) -> None:
    names = re.findall(r"archiveBaseName\.set\(\s*'([^']*)'\s*\)", block)
    names += re.findall(r'archiveBaseName\.set\(\s*"([^"]*)"\s*\)', block)
    if not names:
        fail(
            "build.gradle: flag build must set archiveBaseName "
            "to a literal that does not start with packai-"
        )
    for name in names:
        if not name or name.startswith("packai-"):
            fail("flag build archive base name must not start with packai-: " + name)


def check_properties() -> None:
    text = (FORGE / "gradle.properties").read_text(encoding="utf-8")
    if "packaiAutotest" in text:
        fail("gradle.properties must not contain packaiAutotest")


def check_flag() -> None:
    if not FLAG.is_file():
        fail(f"missing flag file: {FLAG}")
    main = FORGE / "src" / "main"
    leaked = sorted(main.rglob("*autotest*.flag"))
    if leaked:
        fail("src/main must not contain *autotest*.flag: " + ", ".join(str(p) for p in leaked))
    allowed = FLAG.resolve()
    extras = [p for p in (FORGE / "src").rglob("*autotest*.flag") if p.resolve() != allowed]
    if extras:
        fail(
            "flag file only allowed at src/autotest/resources/packai-autotest.flag: "
            + ", ".join(str(p) for p in extras)
        )


def check_flag_name(src: str) -> None:
    found = re.findall(r'getResourceAsStream\(\s*"([^"]+)"\s*\)', src)
    if len(found) != 1:
        fail(f"active() must read exactly one flag resource, got {found}")
    name = found[0].lstrip("/")
    if name != FLAG.name:
        fail(f"active() reads {found[0]!r} but flag file is {FLAG.name}")


def check_tick_guard(src: str) -> None:
    m = re.search(r"public static void tick\(\)\s*\{", src)
    if not m:
        fail("AutoTestHarness.tick() missing")
    head = "".join(src[m.end() :].splitlines(keepends=True)[:5])
    if "active()" not in head or "return" not in head:
        fail(f"tick() must early-return on active() within first 5 lines: {head!r}")


def check_call_sites() -> None:
    calls: list[Path] = []
    refs: list[Path] = []
    for path in iter_java():
        text = path.read_text(encoding="utf-8")
        if "AutoTestHarness.tick(" in text:
            calls.append(path)
    for java_root in JAVA_ROOTS:
        if not java_root.is_dir():
            continue
        for path in java_root.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            if "AutoTestHarness" in text:
                refs.append(path)
    if len(calls) != 1:
        fail(f"expected exactly one AutoTestHarness.tick() in the repo, got {calls}")
    call_text = calls[0].read_text(encoding="utf-8")
    if not END_GUARD.search(call_text) and not END_BLOCK.search(call_text):
        fail("AutoTestHarness.tick() is not inside TickEvent.Phase.END branch")
    allowed = {HARNESS.resolve(), calls[0].resolve()}
    extra = [p for p in refs if p.resolve() not in allowed]
    if extra:
        fail("AutoTestHarness referenced outside harness + tick caller: " + ", ".join(str(p) for p in extra))


def check_write_paths(src: str) -> None:
    code = strip_comments(src)
    for lit in re.findall(r'"(?:\\.|[^"\\])*"', code):
        if ".." in lit:
            fail("harness composes a path containing ..: " + lit)
    stmts = [m.group(0) for m in WRITE_RE.finditer(code)]
    if not stmts:
        fail("harness has no file write")
    for stmt in stmts:
        flat = " ".join(stmt.split())
        if ".." in stmt:
            fail("write path contains ..: " + flat)
        if "trace" in stmt or "config" in stmt or "logs" in stmt:
            fail("write path leaves packai/autotest: " + flat)
        under = 'resolve("autotest")' in stmt or "packai/autotest" in stmt
        sibling_move = stmt.lstrip().startswith("Files.move")
        if not under and not sibling_move:
            fail("write not under <gameDir>/packai/autotest/: " + flat)


def main() -> None:
    check_gradle()
    check_properties()
    check_flag()
    src = HARNESS.read_text(encoding="utf-8")
    check_flag_name(src)
    check_tick_guard(src)
    check_call_sites()
    check_write_paths(src)
    print("check_autotest_flag: OK")


if __name__ == "__main__":
    main()
