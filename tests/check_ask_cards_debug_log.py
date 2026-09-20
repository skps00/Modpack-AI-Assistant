#!/usr/bin/env python3
"""Pin B1 permanent card debug log + B9 emissionRefs filter + B10 role alignment (forge-only)."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAUSE_MARKER = ROOT / "neoforge" / "README_PAUSED.md"
ASK_SERVICE = (
    ROOT
    / "forge"
    / "1.19.2"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "client"
    / "service"
    / "AskService.java"
)
ASK_CARDS_DEBUG = (
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
    / "AskCardsDebug.java"
)
SCREEN = (
    ROOT
    / "forge"
    / "1.19.2"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "client"
    / "gui"
    / "AiAssistantScreen.java"
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
    / "AskCardsDebugCheck.java"
)


def _window(src: str, anchor: str, before: int = 0, after: int = 40) -> str:
    i = src.find(anchor)
    assert i >= 0, f"missing anchor {anchor!r}"
    start = max(0, i - before)
    return src[start : i + after]


def main() -> None:
    assert ASK_CARDS_DEBUG.is_file(), f"missing {ASK_CARDS_DEBUG}"
    assert HARNESS.is_file(), f"missing {HARNESS}"
    dbg = ASK_CARDS_DEBUG.read_text(encoding="utf-8")
    assert "sectionLabel" in dbg, "AskCardsDebug.sectionLabel"
    assert "formatEmittedLine" in dbg, "AskCardsDebug.formatEmittedLine"
    assert "bodyHasPartsHeading" in dbg, "AskCardsDebug.bodyHasPartsHeading"
    assert "toolPartsPath" in dbg, "AskCardsDebug.toolPartsPath"
    assert 'return "用作材料"' in dbg, "uses section label"
    assert 'return "取得"' in dbg, "obtain section label"
    assert 'return "零件"' in dbg, "parts section label"
    # B9
    assert "visibleEmissionRefIds" in dbg, "AskCardsDebug.visibleEmissionRefIds"
    assert "filterRefsByIdentity" in dbg, "AskCardsDebug.filterRefsByIdentity"
    assert "Drop-only" in dbg or "never renumber" in dbg.lower() or "Never renumber" in dbg, (
        "B9 drop-only javadoc"
    )
    # Per-card log role = promptRole (B10 peer)
    assert "c.promptRole()" in dbg, "formatEmittedLine uses promptRole"

    svc = ASK_SERVICE.read_text(encoding="utf-8")
    assert "Pack AI cards emitted=" in svc, "missing cards emitted log"
    assert "logCardsEmitted(" in svc, "missing logCardsEmitted calls"
    assert svc.count("logCardsEmitted(cardsOut") >= 4, (
        f"expect ≥4 call sites (async AI/KW + blocking AI/KW); got {svc.count('logCardsEmitted(cardsOut')}"
    )
    assert "Pack AI cards suppressed" in svc, "missing suppressed log"
    assert "Pack AI card {}" in svc or 'Pack AI card {}' in svc, "missing per-card line"
    assert "AskCardsDebug.formatEmittedLine" in svc, "wire formatEmittedLine"
    # B9 wire: finishAskTrace filters via visibleEmissionRefIds (not raw loop dump)
    assert "AskCardsDebug.visibleEmissionRefIds" in svc, "finishAskTrace must filter emissionRefs"
    fin = _window(svc, "static void finishAskTrace", after=3500)
    assert "visibleEmissionRefIds" in fin, "visibleEmissionRefIds inside finishAskTrace"
    assert "AskTrace.markers" in fin, "markers still written"
    # Negative: no raw unfiltered dump of every emission refId in finishAskTrace
    assert "for (CardEmission em : loop.cardEmissions())" not in fin, (
        "finishAskTrace must not dump all cardEmissions refIds"
    )
    # B10: render.cards.final.role = promptRole (not focusRole enum name)
    role_win = _window(fin, 'addProperty("role"', after=160)
    assert "promptRole()" in role_win, "render.cards.final.role must use promptRole"
    assert "focusRole()" not in role_win, "render.cards.final.role must not use focusRole"

    screen = SCREEN.read_text(encoding="utf-8")
    assert "Pack AI toolParts path=" in screen, "missing toolParts path log"
    assert "AskCardsDebug.toolPartsPath" in screen, "wire toolPartsPath"
    assert "AskCardsDebug.bodyHasPartsHeading" in screen, "wire bodyHasPartsHeading"

    harness = HARNESS.read_text(encoding="utf-8")
    assert "bodyPartsHeadingDetect" in harness, "harness bodyPartsHeadingDetect"
    assert "mid-sentence" in harness or "没有零件" in harness, "negative control mid-sentence"
    assert "visibleEmissionRefIdsDropOnly" in harness, "harness B9 drop-only"
    assert "visibleEmissionRefIdsKeepIdentity" in harness, "harness B9 keep identity"
    assert "must not invent ref" in harness, "B9 negative invent control"
    assert "must not renumber" in harness, "B9 negative renumber control"

    # Negative control proof lives in harness; pin here that mid-sentence is rejected in source.
    assert "没有零件在句子中间" in harness, "negative control string"

    if PAUSE_MARKER.is_file():
        print("PAUSED: forge-only pin (NeoForge support paused)")
    print("check_ask_cards_debug_log OK")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print(f"FAIL: {e}", file=sys.stderr)
        sys.exit(1)
