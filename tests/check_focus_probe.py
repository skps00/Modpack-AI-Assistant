#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static asserts for FocusProbe / FocusTarget / PackAiConfig focus keys."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FOCUS_KEYS = (
    "packai.focus.current",
    "packai.focus.none",
    "packai.focus.too_far",
    "packai.focus.entity_gone",
    "packai.focus.block_no_item",
    "packai.status.focus_entity_range",
    "key.packai.focus",
)


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    probe = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/focus/FocusProbe.java"
    )
    target = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/focus/FocusTarget.java"
    )
    cfg = read("forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java")
    setup = read("forge/1.19.2/src/main/java/com/skps9/packai/client/ClientSetup.java")

    # ① MISS via getType(), never instanceof for hit discrimination
    assert "bh.getType() != HitResult.Type.MISS" in probe
    # strip line comments then forbid hit-result instanceof gates
    code_only = re.sub(r"//.*?$", "", probe, flags=re.M)
    assert "instanceof BlockHitResult" not in code_only
    assert "instanceof EntityHitResult" not in code_only
    assert "instanceof HitResult" not in code_only

    # ② config bounds 4 and 64
    assert "FOCUS_PROBE_ENABLED" in cfg
    assert "ENTITY_FOCUS_RANGE" in cfg
    assert 'define("focusProbeEnabled", true)' in cfg
    assert 'defineInRange("entityFocusRange", 16, 4, 64)' in cfg
    assert "entityFocusRange()" in cfg
    assert "focusProbeEnabled()" in cfg
    assert "SPEC.save()" in cfg
    assert "RAY_MAX = 64.0" in probe or "RAY_MAX = 64.0" in probe
    assert "Math.min(Math.max(entityRangeBlocks, 4.0), RAY_MAX)" in probe

    # ③ three langs × 7 keys
    for lang in ("en_us", "zh_cn", "zh_tw"):
        txt = read(f"forge/1.19.2/src/main/resources/assets/packai/lang/{lang}.json")
        for k in FOCUS_KEYS:
            assert f'"{k}"' in txt, f"missing {k} in {lang}"

    # ④ FocusProbe never writes pick fields (assignment forms)
    assert not re.search(r"\bmc\.hitResult\s*=", code_only)
    assert not re.search(r"\.hitResult\s*=", code_only)
    assert not re.search(r"crosshairPickEntity\s*=", code_only)

    assert "record FocusTarget" in target
    assert "enum Kind" in target
    assert "enum State" in target
    assert "probeBlockOnly" in probe
    assert "probeEntityOnly" in probe
    assert "FOCUS_ASK" in setup
    assert "key.packai.focus" in setup
    assert "focusConsumedThisTick" in setup

    # only= parameter: auto (world probe) / block / entity (reselect)
    ask = read("forge/1.19.2/src/main/java/com/skps9/packai/client/focus/FocusAsk.java")
    screen = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java"
    )
    trace = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/focus/FocusTrace.java"
    )
    assert "askResolved(" in ask
    assert 'askResolved(mc, t, "auto", "world", key)' in ask
    sig = ask.split("void askResolved(", 1)[1].split(")", 1)[0]
    assert "only" in sig
    assert '"block"' in screen
    assert '"entity"' in screen
    assert "probeBlockOnly" in screen
    assert "probeEntityOnly" in screen
    assert 'o.addProperty("only"' in trace

    print("check_focus_probe OK")


if __name__ == "__main__":
    main()
