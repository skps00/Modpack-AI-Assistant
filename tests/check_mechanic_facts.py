#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""M1 mechanic facts: KubeJS scan + FTB quest_text. Forge-only (Neo paused). Add-only."""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def java_method_body(src: str, sig: str) -> str:
    i = src.find(sig)
    assert i >= 0, sig
    brace = src.find("{", i)
    assert brace >= 0, sig
    depth = 0
    for j in range(brace, len(src)):
        c = src[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[brace : j + 1]
    raise AssertionError("unclosed " + sig)


def main() -> None:
    kjs = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KubeJsMechanicScan.java")
    quest = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/QuestMechanicFacts.java")
    cfg = read("forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java")
    ask = read("forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java")
    harness = read(
        "forge/1.19.2/src/test/java/com/skps9/packai/logic/AskMechanicFactsCheck.java"
    )

    assert "class KubeJsMechanicScan" in kjs
    assert "class QuestMechanicFacts" in quest
    assert "ForgeCaps['curios:inventory']" in kjs
    assert "Math.random" in kjs
    assert "onEvent(" in kjs
    assert "ftbquests" in quest
    assert "kubejsMechanicScan" in cfg
    assert "questMechanicFacts" in cfg
    assert "mechanicCacheMaxFiles" in cfg
    assert "mechanicCacheMaxMb" in cfg
    assert "mechanic facts" in ask
    assert "appendMechanicBehavior" in ask
    assert "mechanic:none" in kjs
    assert "AskMechanicFactsCheck" in harness
    assert "class AskMechanicFactsCheck" in harness

    chance_pats = re.findall(r"Pattern (CHANCE_\w+)", kjs)
    assert len(chance_pats) >= 6, chance_pats
    assert any("VAR" in n for n in chance_pats), chance_pats
    assert "let|var|const" in kjs

    folders = re.search(r"SCRIPT_FOLDERS\s*=\s*\{([^}]+)\}", kjs)
    assert folders, "SCRIPT_FOLDERS missing"
    folder_body = folders.group(1)
    assert "startup_scripts" in folder_body
    assert "server_scripts" in folder_body
    assert "client_scripts" in folder_body
    assert "assets" not in folder_body
    assert '"data"' not in folder_body
    assert "'data'" not in folder_body

    for chunk in re.split(r"public static ", kjs):
        if chunk.startswith("List<String> factsForItem(Path"):
            assert "Files.walk" not in chunk, chunk[:400]
    for chunk in re.split(r"public static ", quest):
        if chunk.startswith("List<String> factsForItem(Path"):
            assert "Files.walk" not in chunk, chunk[:400]
    assert "Files.walk" not in ask

    assert "mechanicScanMaxFiles" in cfg
    assert "mechanicScanMaxBytes" in cfg
    assert "mechanicScanMaxMs" in cfg
    assert "questScanMaxFiles" in cfg
    assert "schemaVersion" in kjs
    assert 'INDEX_JSON = "index.json"' in kjs or '"index.json"' in kjs
    assert "ensureStart" in ask
    assert "ensureStart" in kjs

    warmup_body = java_method_body(kjs, "public static void warmup(")
    assert "Files.walk" not in warmup_body, warmup_body
    assert "readString" not in warmup_body, warmup_body
    blocking = java_method_body(ask, "private void warmupBlocking(")
    assert "KubeJsMechanicScan.warmup()" in blocking, blocking[:500]

    print("check_mechanic_facts OK")


if __name__ == "__main__":
    main()
