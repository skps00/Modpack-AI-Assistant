#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""JEI craft present → demote quest narrative (optional reward note, not primary get)."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for tree in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic",
    ):
        ask = read(f"{tree}/AskEngine.java")
        assert "demoteQuestNarrative(" in ask
        assert "questOptionalRewardNote" in ask
        # When demoted, skip purposeQuests embedding of full quest body
        assert "if (!demoteQuestNarrative)" in ask
        rl = read(f"{tree}/ReplyLang.java")
        assert "questOptionalRewardNote" in rl
        assert "packai.reply.quest_optional_reward" in rl

    for tree in (
        "forge/1.19.2/src/main/resources/assets/packai/lang",
        "neoforge/1.21.1/src/main/resources/assets/packai/lang",
    ):
        for lang in ("en_us.json", "zh_tw.json", "zh_cn.json"):
            text = read(f"{tree}/{lang}")
            assert "packai.reply.quest_optional_reward" in text

    print("check_quest_demote_when_jei: OK")


if __name__ == "__main__":
    main()
