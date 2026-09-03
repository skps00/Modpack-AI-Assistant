#!/usr/bin/env python3
"""Pack AI AskCardFallback — standalone card-placement test harness.

Drives the same ensureCards logic as the mod (mirrored in
tests/check_ask_card_fallback.py) WITHOUT launching Minecraft.

Use cases:
  - Past a raw model reply + define the recipe cards (or use presets),
    see exactly where each [[recipe_card:N]] lands after ensureCards.
  - Run the built-in regression cases to verify A+B section-aware fix.

Usage:
  python tools/card_placement_test.py --case sulfur
  python tools/card_placement_test.py --case iron
  python tools/card_placement_test.py --case mixed
  python tools/card_placement_test.py --interactive
  python tools/card_placement_test.py --reply "..." --cards json

Cards JSON shape: list of {"title": "...", "input": bool, "empty": bool}
  - output/quest card: {"title":"配方：Crafting","input":false}
  - input-use card:    {"title":"用作材料：召唤祭坛","input":true}
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests"))

try:
    import check_ask_card_fallback as m
except ImportError as e:  # pragma: no cover
    sys.exit(f"cannot import check_ask_card_fallback: {e}")

# --------------------------------------------------------------------------
# Presets (verified raw replies)
# --------------------------------------------------------------------------
PRESETS = {
    "sulfur": {
        "reply": (
            "[[item:bosses_of_mass_destruction:brimstone_nectar]] 硫磺花蜜\n\n"
            "怎样来:\n1. 工作台:\n下界合金碎片 + 龙息 + 恶魂之泪直接合成。\n\n"
            "怎么用:\n1. 使用后重置附近的 BOSS 生成结构，也就是清除该区域 BOSS 已被击败的进度，让 BOSS 可以再次生成／召唤。\n"
            "2. 作为材料（召唤祭坛）：\n   - 硫磺花蜜、充能末影珍珠 -> 奥秘·领主。\n\n"
            "【来源】JEI、整合包任务书或本地配方、整合包掉落表／钓鱼／交易／脚本（若有）\n"
        ),
        "cards": [
            {"title": "配方：Crafting", "input": False},
            {"title": "用作材料：召唤祭坛", "input": True},
            {"title": "用作材料：召唤祭坛", "input": True},
        ],
    },
    "iron": {
        "reply": (
            "[[item:minecraft:iron_pickaxe]] 铁镐\n\n"
            "怎样来:\n1. 工作台:\n3 个铁锭 + 2 根木棍直接合成。\n2. 动力合成器:\n同样材料可自动合成。\n\n"
            "怎么用:\n1. 手持挖掘：主手挖掘工具，可挖石头与各类矿石。\n"
            "2. 作为材料：与书、铁镐、铁斧、铁剑在动力搅拌器里做初学者法术书。\n\n"
            "【来源】JEI、整合包任务书或本地配方\n"
        ),
        "cards": [
            {"title": "配方：Crafting", "input": False},
            {"title": "配方：自动合成·动力合成器", "input": False},
            {"title": "用作材料：Crafting", "input": True},
        ],
    },
    "mixed": {
        "reply": (
            "怎样来:\n1. 工作台：\n3 个铁锭 + 2 根木棍直接合成。\n\n"
            "怎么用:\n1. 手持挖掘：主手挖掘工具。\n2. 作为材料：用于召唤祭坛。\n"
        ),
        "cards": [
            {"title": "配方：Crafting", "input": False},
            {"title": "用作材料：召唤祭坛", "input": True},
        ],
    },
}


def _cards_to_dicts(cards):
    return [
        {"title": c.get("title", ""), "input": bool(c.get("input")), "empty": bool(c.get("empty"))}
        for c in cards
    ]


def run_case(name, verbose=False):
    preset = PRESETS[name]
    reply = preset["reply"]
    cards = _cards_to_dicts(preset["cards"])
    out = m.ensure_cards(reply, cards)
    return reply, out, cards


def render(reply, out, cards):
    lines = []
    lines.append("=" * 64)
    lines.append("INPUT REPLY")
    lines.append("-" * 64)
    lines.append(reply.rstrip())
    lines.append("")
    lines.append("CARDS (as given):")
    for i, c in enumerate(cards):
        kind = "input-use" if c["input"] else "output/quest"
        lines.append(f"  [{i}] {c['title']}  ({kind})")
    lines.append("")
    lines.append("AFTER ensureCards (where each [[recipe_card:N]] lands):")
    lines.append("-" * 64)
    # annotate
    annotated = out
    lines.append(annotated.rstrip())
    lines.append("")
    lines.append("-- placement map --")
    for i in range(len(cards)):
        marker = f"[[recipe_card:{i}]]"
        idx = out.find(marker)
        lines.append(f"  [[recipe_card:{i}]] @ byte {idx}")
    # section positions
    for needle in ("怎样来", "怎么用", "【来源】"):
        lines.append(f"  {needle} @ byte {out.find(needle)}")
    lines.append("=" * 64)
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser(description="AskCardFallback card-placement test harness")
    ap.add_argument("--case", choices=list(PRESETS), help="run a built-in preset")
    ap.add_argument("--interactive", action="store_true", help="type a reply, then cards")
    ap.add_argument("--reply", help="raw reply text (with {CASE} to use a preset reply)")
    ap.add_argument("--cards", help="JSON list of cards")
    args = ap.parse_args()

    if args.case:
        reply, out, cards = run_case(args.case)
        print(render(reply, out, cards))
        return

    if args.interactive:
        print("Paste reply (end with a line containing only 'END'):")
        buf = []
        for line in sys.stdin:
            if line.strip() == "END":
                break
            buf.append(line)
        reply = "".join(buf)
        print("\nCards as JSON list? Suggestion: [{\"title\":\"配方：Crafting\",\"input\":false},{\"title\":\"用作材料：X\",\"input\":true}]")
        cards_raw = input("cards JSON > ")
        cards = json.loads(cards_raw)
        cards = _cards_to_dicts(cards)
        out = m.ensure_cards(reply, cards)
        print(render(reply, out, cards))
        return

    if args.reply:
        reply = PRESETS.get(args.reply, {}).get("reply", args.reply)
        cards = json.loads(args.cards) if args.cards else []
        cards = _cards_to_dicts(cards)
        out = m.ensure_cards(reply, cards)
        print(render(reply, out, cards))
        return

    ap.print_help()


if __name__ == "__main__":
    main()
