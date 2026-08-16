#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""WP2 honest Gate/Loot miss — pin gate + lang + no-invent prompts."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TREES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
)
LANGS = ("en_us.json", "zh_tw.json", "zh_cn.json")
MISS_KEY = "packai.reply.acquire_index_miss"
UNKNOWN_GATE_KEY = "packai.reply.unknown_advancement_gate"


def should_pin_acquire_miss(
    acquire: list[str] | None,
    has_recipe_get: bool,
    question: str,
    held_item_id: str | None,
) -> bool:
    """Mirror HonestMiss.shouldPinAcquireMiss + PackIndex.isAcquireOrientedQuestion (subset)."""
    if not held_item_id or not held_item_id.strip():
        return False
    if acquire:
        return False
    if has_recipe_get:
        return False
    q = (question or "").lower()
    needles = (
        "如何取得",
        "怎麼取得",
        "怎么取得",
        "如何獲得",
        "如何获得",
        "怎麼獲得",
        "怎么获得",
        "怎麼來",
        "怎么来",
        "如何得到",
        "怎麼得到",
        "怎么得到",
        "how to get",
        "how do i get",
        "where to get",
        "where can i get",
        "obtain",
        "how to summon",
        "summon",
        "召唤",
        "召喚",
    )
    return any(n in q for n in needles)


def main() -> None:
    # Gate: pin only on obtain-oriented + empty acquire + no JEI
    assert should_pin_acquire_miss([], False, "how to get this", "mod:demo")
    assert should_pin_acquire_miss([], False, "如何取得挚友", "b_a_d:friend")
    assert not should_pin_acquire_miss(["Loot: chest"], False, "how to get", "mod:demo")
    assert not should_pin_acquire_miss([], True, "how to get", "mod:demo")
    assert not should_pin_acquire_miss([], False, "what does this do", "mod:demo")
    assert not should_pin_acquire_miss([], False, "how to get", "")
    assert not should_pin_acquire_miss(None, False, "how to get", None)
    assert should_pin_acquire_miss([], False, "最初的骑士怎样召唤", "somebosses:knight_garent_spawn_egg")
    assert should_pin_acquire_miss([], False, "how to summon foo", "mod:egg")

    # Lang keys present; miss wording honest; unknown gate unchanged contract
    for tree in TREES:
        for name in LANGS:
            path = tree / name
            data = json.loads(path.read_text(encoding="utf-8"))
            assert MISS_KEY in data, f"missing {MISS_KEY} in {path}"
            miss = data[MISS_KEY]
            assert isinstance(miss, str) and miss.strip(), path
            assert "%" not in miss, f"{path} acquire_index_miss must not use %s"
            low = miss.lower()
            assert (
                "not indexed" in low
                or "未索引" in miss
            ), f"{path} miss must say not-indexed: {miss}"
            assert (
                "do not invent" in low
                or "禁止捏造" in miss
            ), f"{path} miss must ban invent: {miss}"
            assert UNKNOWN_GATE_KEY in data, path
            gate = data[UNKNOWN_GATE_KEY]
            assert "unknown" in gate.lower() or "未知" in gate, gate

            fc = data["packai.reply.fact_check"]
            assert "19." in fc, path
            assert (
                "not indexed" in fc.lower()
                or "未索引" in fc
                or "unknown advancement gate" in fc.lower()
            ), f"{path} fact_check #19 must mention unknown/not-indexed"
            assert (
                "stage" in fc.lower()
                or "GameStages" in fc
                or "成就" in fc
            ), f"{path} fact_check #19 must ban inventing stage/advancement lists"

    print("check_honest_miss OK")


if __name__ == "__main__":
    main()
