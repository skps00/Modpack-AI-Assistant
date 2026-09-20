#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Settings C-1 pin: traceKeepDays / askMaxToolRounds / dailyTokenLimit (forge-only)."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAUSE_MARKER = ROOT / "neoforge" / "README_PAUSED.md"
FORGE = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai"
CFG = FORGE / "config/PackAiConfig.java"
REG = FORGE / "client/gui/settings/SettingsRegistry.java"
SCREEN = FORGE / "client/gui/settings/SettingsScreenV2.java"
TRACE = FORGE / "logic/AskTrace.java"
STATE = FORGE / "logic/AskLoopState.java"
LOOP = FORGE / "logic/AskToolLoop.java"
DAILY = FORGE / "logic/DailyTokenUsage.java"
SVC = FORGE / "client/service/AskService.java"
LANGS = [
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/en_us.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_cn.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_tw.json",
]

PATHS = (
    "llm.traceKeepDays",
    "llm.askMaxToolRounds",
    "llm.dailyTokenLimit",
)
LANG_KEYS = (
    "packai.settings.trace_keep_days",
    "packai.settings.tooltip.trace_keep_days",
    "packai.settings.ask_max_tool_rounds",
    "packai.settings.tooltip.ask_max_tool_rounds",
    "packai.settings.daily_token_limit",
    "packai.settings.tooltip.daily_token_limit",
    "packai.reply.daily_token_limit",
)


def method_body_sig(src: str, sig: str) -> str:
    idx = src.find(sig)
    if idx < 0:
        raise AssertionError(f"missing {sig}")
    brace = src.find("{", idx)
    depth = 0
    for i in range(brace, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[brace : i + 1]
    raise AssertionError(f"{sig}: unbalanced")


def method_body(src: str, name: str) -> str:
    return method_body_sig(src, f"public static void {name}(")


def main() -> int:
    if not PAUSE_MARKER.is_file():
        print("WARN: Neo not paused — C-1 still forge-only per plan")

    cfg = CFG.read_text(encoding="utf-8")
    for needle in (
        'defineInRange("traceKeepDays"',
        'defineInRange("askMaxToolRounds"',
        'defineInRange("dailyTokenLimit"',
        "setAskTraceKeepDays",
        "setAskMaxToolRounds",
        "setDailyTokenLimit",
        "SPEC.save()",
    ):
        if needle not in cfg:
            print(f"FAIL: PackAiConfig missing {needle}")
            return 1
    for setter in ("setAskTraceKeepDays", "setAskMaxToolRounds", "setDailyTokenLimit"):
        body = method_body(cfg, setter)
        if "SPEC.save()" not in body:
            print(f"FAIL: {setter} missing SPEC.save()")
            return 1

    reg = REG.read_text(encoding="utf-8")
    for path in PATHS:
        if f'"{path}"' not in reg:
            print(f"FAIL: SettingsRegistry missing {path}")
            return 1

    screen = SCREEN.read_text(encoding="utf-8")
    for path in PATHS:
        if f'"{path}"' not in screen:
            print(f"FAIL: SettingsScreenV2 numberOptions/reset missing {path}")
            return 1

    trace = TRACE.read_text(encoding="utf-8")
    rot = method_body_sig(trace, "static void rotate(Path gameDir, int keepFiles, int keepDays)")
    # Day pass must appear before file-count trim (FC4).
    age_idx = rot.find("days > 0")
    count_idx = rot.find("asks.size() > keep")
    if age_idx < 0 or count_idx < 0 or age_idx > count_idx:
        print("FAIL: AskTrace.rotate must purge by age before file-count trim")
        return 1
    if "if (asks.size() <= keep)" in rot.split("days > 0")[0]:
        print("FAIL: early return on file count before age purge")
        return 1
    if "purgeRetention" not in trace:
        print("FAIL: AskTrace.purgeRetention missing")
        return 1
    if "DEFAULT_KEEP_DAYS = 3" not in trace:
        print("FAIL: DEFAULT_KEEP_DAYS must be 3")
        return 1

    state = STATE.read_text(encoding="utf-8")
    if "setMaxLlmRounds" not in state or "maxLlmRounds" not in state:
        print("FAIL: AskLoopState missing maxLlmRounds injection")
        return 1
    if "llmRounds < AskToolLoop.MAX_LLM_ROUNDS" in state:
        print("FAIL: canLlm must use injected maxLlmRounds, not hard constant")
        return 1
    if "llmRounds < maxLlmRounds" not in state:
        print("FAIL: canLlm must compare llmRounds < maxLlmRounds")
        return 1

    loop = LOOP.read_text(encoding="utf-8")
    if "MAX_LLM_ROUNDS = 3" not in loop:
        print("FAIL: MAX_LLM_ROUNDS default constant must remain 3")
        return 1
    if "hops >= MAX_LLM_ROUNDS" in loop or "hops < MAX_LLM_ROUNDS" in loop:
        print("FAIL: AskToolLoop hops must use state.maxLlmRounds()")
        return 1
    if "state.maxLlmRounds()" not in loop:
        print("FAIL: AskToolLoop must call state.maxLlmRounds()")
        return 1

    daily = DAILY.read_text(encoding="utf-8")
    for needle in (
        'FILENAME = "packai-usage.json"',
        "FILE_MAX_BYTES = 64 * 1024",
        "billable(",
        "estimateFromChars(",
        "overLimit(",
        "ATOMIC_MOVE",
    ):
        if needle not in daily:
            print(f"FAIL: DailyTokenUsage missing {needle}")
            return 1

    svc = SVC.read_text(encoding="utf-8")
    for needle in (
        "setMaxLlmRounds(PackAiConfig.askMaxToolRounds())",
        "dailyTokenBlockOrNull",
        "recordDailyTokenUsage",
        "AskTrace.purgeRetention",
    ):
        if needle not in svc:
            print(f"FAIL: AskService missing {needle}")
            return 1

    import json

    for lang_path in LANGS:
        data = json.loads(lang_path.read_text(encoding="utf-8"))
        for key in LANG_KEYS:
            if key not in data:
                print(f"FAIL: {lang_path.name} missing {key}")
                return 1
        reply = data["packai.reply.daily_token_limit"]
        if reply.count("%s") != 2:
            print(f"FAIL: {lang_path.name} daily_token_limit needs 2 %s")
            return 1

    print("OK: settings C-1 (traceKeepDays / askMaxToolRounds / dailyTokenLimit)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
