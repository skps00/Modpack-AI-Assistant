#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Python mirror of JeiRecipeCards.contentSignature + dedupeMirror (Fix 12 item-multiset)."""

from __future__ import annotations

from typing import Any

# Simulates PackAiConfig.isMirrorReplicatorCategory needles (lowercase substrings).
MIRROR_CATEGORIES: list[str] = []

QUEST_KEYS = ("quest", "任務", "任务", "reward table", "獎勵表", "任务奖励", "任務獎勵", "quest reward")
LOOT_KEYS = ("loot", "chest", "treasure", "戰利", "战利", "寶箱", "宝箱", "掉落", "loot table")
# Early TITLE_TIERS 0..3 only (crafting/stonecut/smelt/campfire); exclude machine-like.
CORE_CRAFT_KEYS = (
    "crafting table",
    "crafting",
    "工作台",
    "合成",
    "stonecut",
    "切石",
    "smelt",
    "furnace",
    "blast",
    "熔爐",
    "高爐",
    "campfire",
    "smoker",
    "煙燻",
    "營火",
)
MACHINE_LIKE = ("动力合成", "動力合成", "搅拌机", "攪拌機", "replicator", "auto-crafter", "autocrafter")


def _norm(t: str | None) -> str:
    return "" if t is None else t.strip().lower()


def is_quest_category(title: str | None) -> bool:
    t = _norm(title)
    return any(k in t for k in QUEST_KEYS)


def is_loot_category(title: str | None) -> bool:
    t = _norm(title)
    if not t or is_quest_category(t):
        return False
    return any(k in t for k in LOOT_KEYS)


def is_core_craft_category(title: str | None) -> bool:
    t = _norm(title)
    if not t or is_quest_category(t) or is_loot_category(t):
        return False
    if any(m in t for m in MACHINE_LIKE):
        return False
    return any(k in t for k in CORE_CRAFT_KEYS)


def is_mirror_replicator_category(title: str | None) -> bool:
    if not title or not MIRROR_CATEGORIES:
        return False
    t = title.lower()
    return any(n.lower() in t for n in MIRROR_CATEGORIES if n)


def _add_freq(freq, key, n=1):
    freq[key] = freq.get(key, 0) + n


def content_signature(c):
    """Multiset of inputs + outputs — ignores JEI layout/positions/catalysts/empty cells so
    machine replicators (auto-crafter/mixer) replaying a Crafting-Table recipe collapse."""
    # Callers/_card must populate only one of grid / placedInputs / inputs per card
    # (CRAFTING_3X3→grid, SHAPED→placedInputs, else→inputs) to avoid double-count.
    freq = {}
    for it in c.get("grid") or []:
        if it not in ("-", "", None):
            _add_freq(freq, it)
    for p in c.get("placedInputs") or []:
        s = p.get("stack") if isinstance(p, dict) else p
        if s not in ("-", "", None):
            _add_freq(freq, s)
    for it in c.get("inputs") or []:
        if it not in ("-", "", None):
            _add_freq(freq, it)
    for fl in c.get("fluidInputs") or []:
        _add_freq(freq, "f|" + str(fl))
    for o in c.get("otherInputs") or []:
        _add_freq(freq, "oi|" + str(o.get("label")) + "#" + str(o.get("amount")))
    for o in c.get("outputs") or []:
        _add_freq(freq, "o|" + str(o))
    for fl in c.get("fluidOutputs") or []:
        _add_freq(freq, "fo|" + str(fl))
    for o in c.get("otherOutputs") or []:
        _add_freq(freq, "oo|" + str(o.get("label")) + "#" + str(o.get("amount")))
    return ";".join(f"{k}#{freq[k]}" for k in sorted(freq))


def better_than(nu: dict[str, Any], old: dict[str, Any]) -> bool:
    nt = nu.get("category") or "?"
    ot = old.get("category") or "?"
    nu_mirror = is_mirror_replicator_category(nt)
    old_mirror = is_mirror_replicator_category(ot)
    if old_mirror and not nu_mirror:
        return True
    if not old_mirror and nu_mirror:
        return False
    nu_core = is_core_craft_category(nt)
    old_core = is_core_craft_category(ot)
    return (not old_core) and nu_core


def _family(title: str | None) -> str:
    if is_quest_category(title):
        return "q"
    if is_loot_category(title):
        return "l"
    return "c"


def dedupe_mirror(chosen: list[dict[str, Any]] | None) -> list[dict[str, Any]]:
    if chosen is None or len(chosen) == 0:
        return []
    if len(chosen) <= 1:
        return list(chosen)
    best: dict[str, dict[str, Any]] = {}
    for c in chosen:
        if c is None:
            continue
        key = _family(c.get("category")) + "|" + content_signature(c)
        old = best.get(key)
        if old is None or better_than(c, old):
            best[key] = c
    out: list[dict[str, Any]] = []
    for c in chosen:
        if c is None:
            continue
        key = _family(c.get("category")) + "|" + content_signature(c)
        if best.get(key) is c:
            out.append(c)
    return out


def _card(
    category: str,
    *,
    layout: str = "CRAFTING_3X3",
    grid: list[str] | None = None,
    inputs: list[str] | None = None,
    outputs: list[str] | None = None,
    placedInputs: list[dict[str, Any]] | None = None,
    catalysts: list[str] | None = None,
    tag: str = "",
) -> dict[str, Any]:
    return {
        "category": category,
        "layout": layout,
        "grid": grid or [],
        "inputs": inputs or [],
        "outputs": outputs or [],
        "placedInputs": placedInputs or [],
        "catalysts": catalysts or [],
        "tag": tag,
    }


def test_dedup() -> None:
    global MIRROR_CATEGORIES
    MIRROR_CATEGORIES = []

    # 1) speed_upgrade shape: OUTPUT + INPUT pools → keep Crafting, drop 動力合成器 mirrors
    iron = ["minecraft:iron_ingot"] * 3 + ["-"] * 6
    craft_out = _card("Crafting", grid=iron, outputs=["create:speed_upgrade"], tag="craft_out")
    repl_out = _card("動力合成器", grid=iron, outputs=["create:speed_upgrade"], tag="repl_out")
    stack_grid = ["create:stacked"] + ["-"] * 8
    craft_stack = _card("Crafting", grid=stack_grid, outputs=["create:stacked_out"], tag="craft_stack")
    repl_stack = _card("動力合成器", grid=stack_grid, outputs=["create:stacked_out"], tag="repl_stack")
    tv_grid = ["create:tv"] + ["-"] * 8
    craft_tv = _card("Crafting", grid=tv_grid, outputs=["create:tv_bottle"], tag="craft_tv")
    pool = [craft_out, repl_out, craft_stack, repl_stack, craft_tv]
    got = dedupe_mirror(pool)
    assert [c["tag"] for c in got] == ["craft_out", "craft_stack", "craft_tv"], [c["tag"] for c in got]
    assert all("動力" not in c["category"] for c in got)

    # 2) real variants kept: different inputs (FLOW)
    a = _card(
        "使用·機械手",
        layout="FLOW",
        inputs=["stripped_acacia_wood"],
        outputs=["create:brass_casing"],
        tag="wood",
    )
    b = _card(
        "使用·機械手",
        layout="FLOW",
        inputs=["stripped_acacia_log"],
        outputs=["create:brass_casing"],
        tag="log",
    )
    got2 = dedupe_mirror([a, b])
    assert [c["tag"] for c in got2] == ["wood", "log"]

    # 3) machine-only kept when no craft twin
    only = _card("動力合成器", grid=iron, outputs=["mod:unique_item"], tag="machine_only")
    got3 = dedupe_mirror([only])
    assert [c["tag"] for c in got3] == ["machine_only"]

    # 4) quest family not absorbed by craft; duplicate quest collapsed
    craft_q = _card("Crafting", grid=iron, outputs=["mod:thing"], tag="craft")
    quest1 = _card("Quest Rewards", layout="FLOW", inputs=["q"], outputs=["mod:thing"], tag="q1")
    quest2 = _card("Quest Rewards", layout="FLOW", inputs=["q"], outputs=["mod:thing"], tag="q2")
    got4 = dedupe_mirror([craft_q, quest1, quest2])
    assert [c["tag"] for c in got4] == ["craft", "q1"]

    # 5) config mirror: 搅拌机 dropped when Crafting twin exists; kept alone
    MIRROR_CATEGORIES = ["搅拌机"]
    mixer_twin = _card("搅拌机", grid=iron, outputs=["create:speed_upgrade"], tag="mixer")
    got5 = dedupe_mirror([craft_out, mixer_twin])
    assert [c["tag"] for c in got5] == ["craft_out"]
    alone = _card("搅拌机", grid=["x"] + ["-"] * 8, outputs=["mod:mixer_only"], tag="mixer_alone")
    got5b = dedupe_mirror([alone])
    assert [c["tag"] for c in got5b] == ["mixer_alone"]
    MIRROR_CATEGORIES = []

    # 6) betterThan tie: keep earliest non-core / non-mirror
    m1 = _card("机械加工", layout="FLOW", inputs=["a"], outputs=["o"], tag="first")
    m2 = _card("高级加工", layout="FLOW", inputs=["a"], outputs=["o"], tag="second")
    got6 = dedupe_mirror([m1, m2])
    assert [c["tag"] for c in got6] == ["first"]

    # 7) Fix12: layout/catalyst mirror — CRAFTING_3X3 grid vs SHAPED placedInputs + catalyst
    # 5 materials + empties in grid; SHAPED has same 5 as placedInputs + mechanical_crafter catalyst
    mats = [
        "minecraft:iron_ingot",
        "minecraft:iron_ingot",
        "minecraft:iron_ingot",
        "minecraft:redstone",
        "minecraft:redstone",
    ]
    craft_grid = mats + ["-"] * 4
    craft_layout = _card(
        "Crafting",
        layout="CRAFTING_3X3",
        grid=craft_grid,
        outputs=["create:speed_upgrade"],
        tag="craft_layout",
    )
    shaped_mirror = _card(
        "動力合成器",
        layout="SHAPED",
        placedInputs=[{"stack": m, "x": i % 3, "y": i // 3} for i, m in enumerate(mats)],
        catalysts=["create:mechanical_crafter"],
        outputs=["create:speed_upgrade"],
        tag="shaped_repl",
    )
    # catalysts must NOT affect signature — same IO → collapse to Crafting
    assert content_signature(craft_layout) == content_signature(shaped_mirror), (
        content_signature(craft_layout),
        content_signature(shaped_mirror),
    )
    got7 = dedupe_mirror([craft_layout, shaped_mirror])
    assert [c["tag"] for c in got7] == ["craft_layout"], [c["tag"] for c in got7]

    # 8) Fix12: real SHAPED variants kept (wood vs log)
    wood_s = _card(
        "使用·機械手",
        layout="SHAPED",
        placedInputs=[{"stack": "stripped_acacia_wood", "x": 0, "y": 0}],
        outputs=["create:brass_casing"],
        tag="shaped_wood",
    )
    log_s = _card(
        "使用·機械手",
        layout="SHAPED",
        placedInputs=[{"stack": "stripped_acacia_log", "x": 0, "y": 0}],
        outputs=["create:brass_casing"],
        tag="shaped_log",
    )
    got8 = dedupe_mirror([wood_s, log_s])
    assert [c["tag"] for c in got8] == ["shaped_wood", "shaped_log"]

    # 9) Fix12: machine-only SHAPED (no Crafting twin) kept
    machine_only_shaped = _card(
        "動力合成器",
        layout="SHAPED",
        placedInputs=[{"stack": "mod:special_in", "x": 0, "y": 0}],
        catalysts=["create:mechanical_crafter"],
        outputs=["mod:machine_unique"],
        tag="shaped_machine_only",
    )
    got9 = dedupe_mirror([machine_only_shaped])
    assert [c["tag"] for c in got9] == ["shaped_machine_only"]


if __name__ == "__main__":
    test_dedup()
    print("check_recipe_card_dedup OK")
