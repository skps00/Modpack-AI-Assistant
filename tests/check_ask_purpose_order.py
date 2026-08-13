#!/usr/bin/env python3
"""askPurposeOrder config + purpose-branch FACT order (purpose_first | ingredient_first)."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def purpose_ask_fact_order(
    purpose_block: str,
    as_ingredient: str,
    get_body: str,
    order: str = "purpose_first",
) -> str:
    """Mirror AskEngine purpose-branch block order for both askPurposeOrder values."""
    parts: list[str] = []
    use = ("## How to use\n" + purpose_block.strip()) if purpose_block and purpose_block.strip() else ""
    ing = as_ingredient.strip() if as_ingredient and as_ingredient.strip() else ""
    get = ("## How to get\n" + get_body.strip()) if get_body and get_body.strip() else ""
    if order == "ingredient_first":
        for p in (ing, get, use):
            if p:
                parts.append(p)
    else:
        for p in (use, ing, get):
            if p:
                parts.append(p)
    return "\n".join(parts)


def main() -> None:
    for side in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        cfg = (ROOT / side / "config/PackAiConfig.java").read_text(encoding="utf-8")
        assert "ASK_PURPOSE_ORDER" in cfg
        assert 'define("askPurposeOrder", "purpose_first")' in cfg
        assert "askPurposeOrder()" in cfg
        assert "setAskPurposeOrder" in cfg
        assert "ingredient_first" in cfg and "purpose_first" in cfg
        assert "obtain_first" in cfg  # alias

        engine = (ROOT / side / "logic/AskEngine.java").read_text(encoding="utf-8")
        assert "PackAiConfig.askPurposeOrder()" in engine
        assert 'ingredient_first".equals' in engine or '"ingredient_first".equals' in engine
        assert "hasObtainRecipeBody" in engine

        purpose_ctx = (ROOT / side / "logic/AskPurposeContext.java").read_text(encoding="utf-8")
        assert "hasObtainRecipeBody" in purpose_ctx

        scrub = (ROOT / side / "logic/AskReplyScrub.java").read_text(encoding="utf-8")
        assert "EMPTY_HOW_TO_GET" in scrub
        assert "怎么来" in scrub

        settings = (ROOT / side / "client/gui/PackAiSettingsScreen.java").read_text(encoding="utf-8")
        assert "ask_purpose_order" in settings
        assert "ASK_PURPOSE_ORDERS" in settings
        assert "setAskPurposeOrder" in settings

        reply = (ROOT / side / "logic/ReplyLang.java").read_text(encoding="utf-8")
        assert "askPurposeOrderHint" in reply

    for tree, locs in (
        ("forge/1.19.2", ("en_us", "zh_tw", "zh_cn")),
        ("neoforge/1.21.1", ("en_us", "zh_tw", "zh_cn")),
    ):
        for loc in locs:
            import json

            lang = json.loads(
                (ROOT / tree / "src/main/resources/assets/packai/lang" / f"{loc}.json").read_text(
                    encoding="utf-8"
                )
            )
            assert "packai.settings.ask_purpose_order" in lang
            assert "packai.settings.ask_purpose_order.purpose_first" in lang
            assert "packai.settings.ask_purpose_order.ingredient_first" in lang
            assert "packai.settings.tooltip.ask_purpose_order" in lang
            assert "packai.reply.ask_purpose_order.purpose_first" in lang
            assert "packai.reply.ask_purpose_order.ingredient_first" in lang

    purpose = "[PURPOSE]\nForbidden Scroll\n[CONSUME_USE] unlock"
    as_ing = "[AS_INGREDIENT]\nCrafting → sword"
    get = "Crafting → stick"
    pf = purpose_ask_fact_order(purpose, as_ing, get, "purpose_first")
    assert pf.index("[PURPOSE]") < pf.index("[AS_INGREDIENT]")
    assert pf.index("[AS_INGREDIENT]") < pf.index("## How to get")
    assert pf.index("## How to use") < pf.index("## How to get")

    ig = purpose_ask_fact_order(purpose, as_ing, get, "ingredient_first")
    assert ig.index("[AS_INGREDIENT]") < ig.index("[PURPOSE]")
    assert ig.index("## How to get") < ig.index("## How to use")

    print("check_ask_purpose_order OK")


if __name__ == "__main__":
    main()
