#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""M1 mechanic facts: KubeJS scan + FTB quest_text. Forge-only (Neo paused). Add-only."""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


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

    print("check_mechanic_facts OK")


if __name__ == "__main__":
    main()
