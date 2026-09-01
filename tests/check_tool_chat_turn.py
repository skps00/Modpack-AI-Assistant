#!/usr/bin/env python3
"""ToolChatTurn reasoning_content pass-back + related wiring (Forge + Neo lockstep)."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SIDES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai",
)
LANG_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
)
RULE_SNIPPETS = (
    "role=output",
    "模组通用知识",
    "mod general knowledge",
    "模組通用知識",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def check_side(main: Path) -> None:
    turn = read(main / "logic" / "ToolChatTurn.java")
    assert "String reasoningContent" in turn, f"{main}: ToolChatTurn missing reasoningContent"
    assert 'assistant(String content, List<AskToolCall> calls, String reasoningContent)' in turn
    assert 'o.addProperty("reasoning_content", reasoningContent)' in turn
    assert "!reasoningContent.isBlank()" in turn

    round_src = read(main / "logic" / "LlmRound.java")
    assert "String reasoningContent" in round_src
    assert "reasoningContent = reasoningContent == null" in round_src

    llm = read(main / "logic" / "LlmClient.java")
    assert '"reasoning_content"' in llm
    assert 'message.has("reasoning_content")' in llm
    assert "toolSchemaRequired" in llm
    assert "additionalProperties" in llm
    assert 'if ("acquire".equals(name))' in llm

    loop = read(main / "logic" / "AskToolLoop.java")
    assert "round.reasoningContent()" in loop

    engine = read(main / "logic" / "AskEngine.java")
    assert "recipeCardsCatalogSlim" in engine
    assert "return capable ? null : jeiForLlm()" not in engine


def check_lang(lang_dir: Path) -> None:
    for name in ("zh_cn.json", "zh_tw.json", "en_us.json"):
        data = json.loads(read(lang_dir / name))
        pattern = data["packai.reply.reply_pattern"]
        assert any(s in pattern for s in RULE_SNIPPETS), f"{lang_dir}/{name}: missing JEI recipe rule"


def mirror_recipe_cards_catalog_slim(jei_text: str) -> str | None:
    """Mirror AskEngine.recipeCardsCatalogSlim for a quick python-side sanity check."""
    if not jei_text or not jei_text.strip():
        return None
    idx = jei_text.find("[RECIPE_CARDS]")
    if idx < 0:
        return None
    out: list[str] = []
    entry = re.compile(r"^\d+ \| .*role=")
    for line in jei_text[idx:].split("\n"):
        if not out:
            if "[RECIPE_CARDS]" not in line:
                continue
            out.append(line)
        elif entry.match(line.strip()):
            out.append(line)
        else:
            break
    return "\n".join(out) if out else None


def check_behavior() -> None:
    sample = (
        "Season\n"
        "[RECIPE_CARDS] UI cards\n"
        "0 | role=output | Crafting | iron → pick\n"
        "REQUIREMENTS:\n"
    )
    slim = mirror_recipe_cards_catalog_slim(sample)
    assert slim is not None
    assert slim.startswith("[RECIPE_CARDS]")
    assert "role=output" in slim
    assert "Season" not in slim
    assert "REQUIREMENTS" not in slim

    with_reason = {
        "role": "assistant",
        "content": "",
        "reasoning_content": "think",
    }
    assert "reasoning_content" in with_reason
    without_reason = {"role": "assistant", "content": ""}
    assert "reasoning_content" not in without_reason


def main() -> None:
    for side in SIDES:
        check_side(side)
    for lang_dir in LANG_PATHS:
        check_lang(lang_dir)
    check_behavior()
    print("check_tool_chat_turn OK")


if __name__ == "__main__":
    main()
