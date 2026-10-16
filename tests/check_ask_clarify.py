#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static guards for ask_clarify (plan 2026-10-16 v7)."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai"
LANGS = [
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/en_us.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_cn.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_tw.json",
]

REPLY_KEYS = (
    "packai.reply.clarify.confirm",
    "packai.reply.clarify.which",
    "packai.reply.clarify.unclear",
    "packai.reply.clarify.uncertain",
)
SETTING_KEYS = (
    "packai.settings.clarify_enabled",
    "packai.settings.tooltip.clarify_enabled",
)


def main() -> int:
    loop = (FORGE / "logic/AskToolLoop.java").read_text(encoding="utf-8")
    assert '"ask_clarify"' in loop[loop.index("CAPABLE_TOOLS") : loop.index("ALLOWLIST")], (
        "ask_clarify missing from CAPABLE_TOOLS"
    )
    assert "RAW_ARGS_TOOLS" in loop and '"ask_clarify"' in loop[loop.index("RAW_ARGS_TOOLS") :], (
        "RAW_ARGS_TOOLS must include ask_clarify"
    )
    assert "stays 16" in loop, "CAPABLE_TOOLS comment should say stays 16"

    engine = (FORGE / "logic/AskEngine.java").read_text(encoding="utf-8")
    assert "new ClarifyAskTool()" in engine, "AskEngine must register ClarifyAskTool"
    # R2 F3 / R3-7: miss clarify exit must stay behind !offline + clarifyEnabled.
    miss_idx = engine.index("AskMissFallback.isMissAnswer")
    miss_block = engine[miss_idx : miss_idx + 2500]
    assert "!offline" in miss_block and "clarifyEnabled()" in miss_block, (
        "miss-fallback clarify must gate on !offline and clarifyEnabled"
    )
    assert "noMaterial" in miss_block, "R3-2 noMaterial guard missing"
    assert "shouldMarkUncertain" in miss_block, "miss tail must use shouldMarkUncertain"
    body_src = (FORGE / "logic/ClarifyBody.java").read_text(encoding="utf-8")
    assert "boolean shouldMarkUncertain(" in body_src, "shouldMarkUncertain missing"
    assert "appendUncertainBeforeFooter" in body_src, "appendUncertainBeforeFooter missing"
    assert "ReplySources.HEADER" in body_src, "footer insert must use ReplySources.HEADER"
    assert 'AskTrace.event("clarify.mark"' in engine, "clarify.mark trace missing"
    assert '"limit-post"' in engine and '"marked"' in engine, "clarify.mark fields missing"
    assert engine.count("appendUncertainBeforeFooter") >= 4, (
        "skipLlm + three tail returns must call appendUncertainBeforeFooter"
    )
    assert "DISPLAY_SRC_CLARIFY" in (FORGE / "logic/AskResult.java").read_text(encoding="utf-8")
    assert "clarifyEnabled" in (FORGE / "config/PackAiConfig.java").read_text(encoding="utf-8")
    reg = (FORGE / "client/gui/settings/SettingsRegistry.java").read_text(encoding="utf-8")
    assert "ui.clarifyEnabled" in reg and "setClarifyEnabled" in reg

    svc = (FORGE / "client/service/AskService.java").read_text(encoding="utf-8")
    assert svc.count("DISPLAY_SRC_CLARIFY") >= 2, "AskService needs async+sync clarify short-circuit"

    for path in LANGS:
        data = json.loads(path.read_text(encoding="utf-8"))
        for k in REPLY_KEYS + SETTING_KEYS:
            assert k in data and str(data[k]).strip(), f"{path.name} missing {k}"

    # Neo tree must stay untouched for ask_clarify (PAUSED).
    neo_loop = ROOT / "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/AskToolLoop.java"
    if neo_loop.is_file():
        neo = neo_loop.read_text(encoding="utf-8")
        assert '"ask_clarify"' not in neo, "neoforge must not gain ask_clarify while PAUSED"

    print("check_ask_clarify OK")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as e:
        print(f"FAIL: {e}", file=sys.stderr)
        raise SystemExit(1)
