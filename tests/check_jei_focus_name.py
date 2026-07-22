#!/usr/bin/env python3
"""Parse display name beside mod:id in ask templates (SlashBlade NBT fix)."""

import re

ZH = re.compile(r"「(.+?)」（\s*([a-z0-9_]+:[a-z0-9_./-]+)\s*）", re.I)
EN = re.compile(r"(?:What is\s+)?(.+?)\s*\(\s*([a-z0-9_]+:[a-z0-9_./-]+)\s*\)", re.I)


def label_beside(question: str, item_id: str) -> str:
    for rx in (ZH, EN):
        for m in rx.finditer(question):
            if m.group(2).lower() == item_id.lower():
                return m.group(1).strip()
    return ""


def main() -> None:
    q = "「Fluorescent 「Wonder」」（slashblade:slashblade）在這個整合包有什麼用途、配方和取得方式？"
    assert label_beside(q, "slashblade:slashblade") == "Fluorescent 「Wonder」"
    q2 = "What is Fluorescent Wonder (slashblade:slashblade) used for in this pack?"
    assert label_beside(q2, "slashblade:slashblade") == "Fluorescent Wonder"
    assert label_beside(q, "minecraft:dirt") == ""
    print("ok jei_focus_name")


if __name__ == "__main__":
    main()
