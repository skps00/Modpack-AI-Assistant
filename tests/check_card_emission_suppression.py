#!/usr/bin/env python3
"""Pin B11: modular frame suppress at offerEmission (forge-only; no neo iterate)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai"
LOGIC = FORGE / "logic"
ASK_SERVICE = FORGE / "client" / "service" / "AskService.java"
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
    / "ModularFrameCardsCheck.java"
)


def read(p: Path) -> str:
    assert p.is_file(), f"missing {p}"
    return p.read_text(encoding="utf-8")


def strip_strings_comments(block: str) -> str:
    no_line = re.sub(r"//[^\n]*", "", block)
    no_block = re.sub(r"/\*.*?\*/", "", no_line, flags=re.S)
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', no_block)


def method_body(src: str, sig: str) -> str:
    i = src.find(sig)
    assert i >= 0, f"missing {sig!r}"
    brace = src.find("{", i)
    assert brace >= 0, f"no brace after {sig!r}"
    depth = 0
    for j in range(brace, len(src)):
        c = src[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[brace : j + 1]
    raise AssertionError(f"unbalanced {sig!r}")


def line_of(src: str, needle: str) -> int:
    i = src.find(needle)
    assert i >= 0, f"missing {needle!r}"
    return src.count("\n", 0, i) + 1


def main() -> None:
    core = read(LOGIC / "ModularFrameCards.java")
    assert "shouldDropFrameCard" in core
    assert "equalsIgnoreCase" in core

    env_src = read(LOGIC / "AskToolEnv.java")
    offer = method_body(env_src, "public int offerEmission(CardEmission emission)")
    offer_clean = strip_strings_comments(offer)
    # S1: offerEmission calls shared core (via rejectFrameCard → shouldDropFrameCard)
    assert "rejectFrameCard(" in offer_clean or "shouldDropFrameCard(" in offer_clean
    reject = method_body(env_src, "boolean rejectFrameCard(")
    assert "ModularFrameCards.shouldDropFrameCard(" in strip_strings_comments(reject)

    # S2: predicate before refId assign before add
    pred_line = line_of(offer, "rejectFrameCard(")
    if "rejectFrameCard(" not in offer:
        pred_line = line_of(offer, "shouldDropFrameCard(")
    ref_line = line_of(offer, "pendingEmissions.size() + 1")
    add_line = line_of(offer, "pendingEmissions.add(")
    assert pred_line < ref_line < add_line, (
        f"order pred={pred_line} ref={ref_line} add={add_line}"
    )

    # signature unchanged
    assert "public int offerEmission(CardEmission emission)" in env_src
    assert "modularFrameDropId" in env_src
    assert "suppressedFrameOffers" in env_src

    svc = read(ASK_SERVICE)
    suppress = method_body(svc, "static List<RecipeCard> suppressModularFrameCards(")
    assert "ModularFrameCards.shouldDropFrameCard(" in strip_strings_comments(suppress)
    # no hand-rolled equals for frame drop inside suppress body
    assert "equalsIgnoreCase" not in strip_strings_comments(suppress)
    assert "setModularFrameDropId(" in svc
    assert "shouldSkipAutoEmit()" in svc
    # both auto-emit sites gated
    assert svc.count("shouldSkipAutoEmit()") >= 2

    engine = read(LOGIC / "AskEngine.java")
    assert "bindAskToolEnv(" in engine
    assert engine.count("bindAskToolEnv(") >= 3  # def + 2 call sites

    loop = read(LOGIC / "AskLoopState.java")
    assert "shouldSkipAutoEmit()" in loop
    assert "noteSuppressedFrameOffer()" in loop
    assert "modularFrameDropId" in loop

    render = read(LOGIC / "RenderRecipeCardsAskTool.java")
    assert "suppressedFrameOnly" in render
    assert "框架合成卡已隱藏" in render
    # missEmpty text must remain for other paths
    assert "missEmpty(" in render

    assert HARNESS.is_file(), f"missing {HARNESS}"
    harness = read(HARNESS)
    assert "ModularFrameCardsCheck" in harness
    assert "dropCoreCases" in harness
    assert "crossLayerSharedCore" in harness
    assert "skipAutoEmit" in harness
    # harness must stay headless (no AskToolEnv — ItemStack clinit)
    assert "new AskToolEnv" not in harness
    assert "rejectFrameCard" not in harness

    # Keyword path allowed to diverge — pin it still exists, not unified
    fallback = read(LOGIC / "AskCardFallback.java")
    assert "isFocusFrameOutput" in fallback

    print("check_card_emission_suppression OK")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print(f"FAIL: {e}", file=sys.stderr)
        sys.exit(1)
