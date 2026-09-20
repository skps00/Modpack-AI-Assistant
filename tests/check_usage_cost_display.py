#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Plan B v3 usage／cost display static asserts (Forge-only)."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge/1.19.2"
LANGS = [
    FORGE / "src/main/resources/assets/packai/lang/en_us.json",
    FORGE / "src/main/resources/assets/packai/lang/zh_cn.json",
    FORGE / "src/main/resources/assets/packai/lang/zh_tw.json",
]
NEW_KEYS = [
    "packai.settings.usage.summary",
    "packai.settings.usage.peak",
    "packai.settings.usage.unreadable",
    "packai.reply.daily_token_limit_reset",
]
FORBIDDEN = re.compile(r"(src:)|(\.js\b)|(\.json\b)|(kubejs[/\\])")


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> int:
    cost = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/CostWindow.java")
    screen = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsScreenV2.java"
    )
    reply = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/ReplyLang.java")
    harness = read(
        "forge/1.19.2/src/test/java/com/skps9/packai/logic/CostWindowCheck.java"
    )
    daily = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/DailyTokenUsage.java")

    assert "class CostWindow" in cost
    assert "usageSummary" in cost
    assert "nextResetLocal" in cost
    assert "isDeepSeekPeak" in cost
    assert "formatCount" in cost
    assert "ZoneId.systemDefault()" not in cost
    assert "TimeZone.setDefault" not in cost
    assert 'contains("deepseek")' in cost

    assert "llm.dailyTokenLimit" in screen
    assert "CostWindow.usageSummary" in screen or "dailyTokenUsageSummary" in screen
    assert "USAGE_SUMMARY_CACHE_MS" in screen
    assert "packai.settings.usage.unreadable" in screen

    assert "daily_token_limit_reset" in reply
    assert "dailyTokenLimitReached" in reply
    # must not rewrite the 2-%s base key call away
    assert 'tr(code, "packai.reply.daily_token_limit"' in reply.replace(" ", "") or (
        'packai.reply.daily_token_limit' in reply
    )

    # Frozen: DailyTokenUsage not rewritten for this plan
    assert "dayClock" in daily

    assert "dayRolloverS1" in harness
    assert "deepSeekPeakS2" in harness
    assert "usageSummaryS3" in harness
    assert "widthS4" in harness
    assert "resetMessageS5" in harness
    assert "PLAYER_VISIBLE_FORBIDDEN" in harness
    assert "api.openai.com" in harness

    for lang_path in LANGS:
        data = json.loads(lang_path.read_text(encoding="utf-8"))
        for key in NEW_KEYS:
            if key not in data:
                print(f"FAIL: {lang_path.name} missing {key}")
                return 1
            val = data[key]
            if FORBIDDEN.search(val):
                print(f"FAIL: PLAYER_VISIBLE_FORBIDDEN {lang_path.name} {key}={val}")
                return 1
        # S6 pin: base reply still exactly 2 %s
        reply_tpl = data["packai.reply.daily_token_limit"]
        if reply_tpl.count("%s") != 2:
            print(f"FAIL: {lang_path.name} daily_token_limit needs 2 %s")
            return 1
        reset_tpl = data["packai.reply.daily_token_limit_reset"]
        if reset_tpl.count("%s") != 2:
            print(f"FAIL: {lang_path.name} daily_token_limit_reset needs 2 %s")
            return 1

    print("check_usage_cost_display OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
