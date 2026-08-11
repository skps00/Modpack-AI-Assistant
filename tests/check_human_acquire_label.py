#!/usr/bin/env python3
"""Mirror PackIndex resolveConsume + ReplyLang humanAcquireLabel fallbacks."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def resolve_consume(task: bool | None, chapter: bool | None, file_default: bool | None) -> bool | None:
    """D7: task → chapter → file; all missing → None (do not label)."""
    if task is not None:
        return task
    if chapter is not None:
        return chapter
    return file_default


def is_fishing_path(pl: str) -> bool:
    return "fishing" in pl or "fisherman" in pl or "/fish/" in pl or "fish_loot" in pl


def is_loot_path(pl: str) -> bool:
    return "loot_table" in pl or "loot_tables" in pl


def is_trade_path(pl: str) -> bool:
    return "villager" in pl or "/trade" in pl or "trades" in pl or "wandering_trader" in pl


def is_quest_path(pl: str) -> bool:
    pl = pl.replace("\\", "/").lower()
    if "ftbquests" not in pl:
        return False
    name = pl.rsplit("/", 1)[-1]
    if name in ("data.snbt", "chapter_groups.snbt", "chapter_group.snbt", "chapter.snbt", "reward_table.snbt"):
        return False
    if "reward_table" in pl or "/lang/" in pl:
        return False
    return "/chapters/" in pl or "/quests/" in pl


def kind_from_path(pl: str) -> str:
    """Path-only label kind — else must NOT be trade."""
    lower = pl.replace("\\", "/").lower()
    if is_fishing_path(lower):
        return "fishing"
    if is_loot_path(lower):
        return "loot"
    if is_trade_path(lower):
        return "trade"
    return "pack_data"


def depth1_explicit_bool(slice_text: str, key: str) -> bool | None:
    pat = re.compile(rf"\b{re.escape(key)}\s*:\s*(true|false)\b", re.I)
    depth = 0
    in_string = False
    escape = False
    for i, c in enumerate(slice_text):
        if in_string:
            if escape:
                escape = False
            elif c == "\\":
                escape = True
            elif c == '"':
                in_string = False
            continue
        if c == '"':
            in_string = True
            continue
        if c == "{":
            depth += 1
            continue
        if c == "}":
            depth -= 1
            continue
        if depth != 1:
            continue
        m = pat.match(slice_text, i)
        if m:
            return m.group(1).lower() == "true"
    return None


def main() -> None:
    # inherit true/false/default/ambiguous
    assert resolve_consume(True, False, False) is True
    assert resolve_consume(False, True, True) is False
    assert resolve_consume(None, True, False) is True
    assert resolve_consume(None, False, True) is False
    assert resolve_consume(None, None, False) is False
    assert resolve_consume(None, None, True) is True
    assert resolve_consume(None, None, None) is None

    # path labels
    assert kind_from_path("datapacks/x/villager_trades/y.json") == "trade"
    assert kind_from_path("datapacks/x/loot_table/chests/a.json") == "loot"
    assert kind_from_path("datapacks/x/loot_table/gameplay/fishing/treasure.json") == "fishing"
    assert kind_from_path("kubejs/server_scripts/recipes.js") == "pack_data"
    assert kind_from_path("config/ftbquests/quests/chapters/demo.snbt") == "pack_data"

    assert is_quest_path("config/ftbquests/quests/chapters/demo.snbt")
    assert not is_quest_path("config/ftbquests/quests/data.snbt")
    assert not is_quest_path("kubejs/server_scripts/x.js")

    # SNBT depth-1 consume on task vs nested noise
    task_true = '{\n\tid: "T1"\n\tconsume_items: true\n\titem: { id: "minecraft:stick" }\n\ttype: "item"\n}'
    task_absent = '{\n\tid: "T2"\n\titem: { id: "minecraft:dirt" }\n\ttype: "item"\n}'
    assert depth1_explicit_bool(task_true, "consume_items") is True
    assert depth1_explicit_bool(task_absent, "consume_items") is None

    chapter = '{\n\tid: "CH1"\n\tconsume_items: false\n\tquests: [{ tasks: [{ consume_items: true }] }]\n}'
    assert depth1_explicit_bool(chapter, "consume_items") is False

    # Java sources must keep else≠tradeKind
    for tree in (
        ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "ReplyLang.java",
        ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "ReplyLang.java",
    ):
        src = tree.read_text(encoding="utf-8")
        assert "questSubmitKind" in src
        assert "prefer packData over wrong" in src or "never default to trade" in src
        # old bug: else { kind = tradeKind
        assert re.search(r"else\s*\{\s*kind\s*=\s*tradeKind", src) is None, f"else tradeKind still in {tree}"

    for tree in (
        ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "PackIndex.java",
        ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "PackIndex.java",
    ):
        src = tree.read_text(encoding="utf-8")
        assert "resolveConsume" in src
        assert "quest_submit" in src and "quest_obtain" in src
        assert "isQuestPath" in src
        assert "ensureFocusQuestAcquireEdges" in src
        assert "addFactForced" in src

    for tree in (
        ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
        ROOT / "neoforge" / "1.21.1" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
    ):
        for name in ("en_us.json", "zh_tw.json", "zh_cn.json"):
            text = (tree / name).read_text(encoding="utf-8")
            assert "packai.reply.quest_submit_kind" in text
            assert "packai.reply.quest_obtain_kind" in text

    print("check_human_acquire_label OK")


if __name__ == "__main__":
    main()
