"""RecipeCardsMode: marker parse/scrub + dual-tree mirror."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

MARKER = re.compile(r"\[\[\s*recipe_cards\s*:\s*(on|off)\s*\]\]", re.I)
CARD_INDEX = re.compile(r"\[\[\s*recipe_card\s*:\s*\d+\s*\]\]", re.I)


def parse_marker(answer: str | None) -> bool | None:
    if not answer:
        return None
    last = None
    for m in MARKER.finditer(answer):
        last = m.group(1).lower() == "on"
    return last


def has_card_index(answer: str | None) -> bool:
    return bool(answer and CARD_INDEX.search(answer))


def resolve_gate_marker(answer: str | None) -> bool | None:
    gate = parse_marker(answer)
    if gate is not None:
        return gate
    return True if has_card_index(answer) else None


def scrub_marker(answer: str | None) -> str:
    if not answer:
        return ""
    t = MARKER.sub("", answer)
    t = re.sub(r"[ \t]+\n", "\n", t)
    t = re.sub(r"\n{3,}", "\n\n", t)
    return t


def main() -> None:
    assert parse_marker("hello [[recipe_cards:on]] world") is True
    assert parse_marker("[[recipe_cards:off]]") is False
    assert parse_marker("[[recipe_cards:on]] then [[recipe_cards:off]]") is False
    assert parse_marker("no marker") is None
    scrubbed = scrub_marker("A [[recipe_cards:on]] B")
    assert "recipe_cards" not in scrubbed
    assert "A" in scrubbed and "B" in scrubbed

    # Implicit on from [[recipe_card:N]]; explicit off wins
    assert resolve_gate_marker("prose\n[[recipe_card:0]]") is True
    assert resolve_gate_marker("[[recipe_cards:off]]\n[[recipe_card:0]]") is False
    assert resolve_gate_marker("[[recipe_cards:on]]") is True
    assert resolve_gate_marker("plain text only") is None

    for tree in ("forge/1.19.2", "neoforge/1.21.1"):
        mode_java = (ROOT / tree / "src/main/java/com/skps9/packai/logic/RecipeCardsMode.java").read_text(
            encoding="utf-8"
        )
        assert "enum RecipeCardsMode" in mode_java
        assert "KEYWORDS" in mode_java and "AI" in mode_java
        assert "ALWAYS" in mode_java and "NEVER" in mode_java
        assert "recipe_cards" in mode_java
        assert "shouldCollect" in mode_java
        assert "resolveAttach" in mode_java
        assert "resolveGateMarker" in mode_java
        assert "hasCardIndexMarker" in mode_java
        assert "isCraftOrientedQuestion" in mode_java
        assert "isAcquireOrientedQuestion" in mode_java
        assert "hideUpgradeRecipes" in (
            ROOT / tree / "src/main/java/com/skps9/packai/client/jei/JeiRecipeCards.java"
        ).read_text(encoding="utf-8")
        cards = (ROOT / tree / "src/main/java/com/skps9/packai/client/jei/JeiRecipeCards.java").read_text(
            encoding="utf-8"
        )
        assert "ItemVariantKeys.hasVariantKeys" in cards
        variant = (ROOT / tree / "src/main/java/com/skps9/packai/logic/ItemVariantKeys.java").read_text(
            encoding="utf-8"
        )
        assert "ISB_Spells" in variant
        assert "collectSpellShapedId" in variant
        pack = (ROOT / tree / "src/main/java/com/skps9/packai/logic/PackIndex.java").read_text(
            encoding="utf-8"
        )
        assert "取得方式" in pack

        cfg = (ROOT / tree / "src/main/java/com/skps9/packai/config/PackAiConfig.java").read_text(
            encoding="utf-8"
        )
        assert 'define("recipeCardsMode", "ai")' in cfg
        assert "recipeCardsMode()" in cfg
        assert 'return "ai"' in cfg
        assert 'case "keywords"' in mode_java
        assert "default -> AI" in mode_java
        assert "return AI" in mode_java

        ask = (ROOT / tree / "src/main/java/com/skps9/packai/client/service/AskService.java").read_text(
            encoding="utf-8"
        )
        assert "RecipeCardsMode.current()" in ask
        assert "resolveGateMarker" in ask
        assert "resolveAttach" in ask

        settings = (
            ROOT / tree / "src/main/java/com/skps9/packai/client/gui/PackAiSettingsScreen.java"
        ).read_text(encoding="utf-8")
        assert "RECIPE_CARDS_MODES" in settings
        assert "recipe_cards_mode" in settings

        for lang in ("en_us", "zh_tw", "zh_cn"):
            lang_path = ROOT / tree / f"src/main/resources/assets/packai/lang/{lang}.json"
            data = json.loads(lang_path.read_text(encoding="utf-8"))
            assert "packai.settings.recipe_cards_mode" in data
            assert "packai.settings.tooltip.recipe_cards_mode" in data
            assert "packai.reply.recipe_cards_ai_marker" in data
            marker = data["packai.reply.recipe_cards_ai_marker"]
            tip = data["packai.settings.tooltip.recipe_cards_mode"]
            assert "[[recipe_cards:on]]" in marker
            assert "[[recipe_card:N]]" in marker
            assert "MUST" in marker or "必須" in marker or "必须" in marker
            assert "describe" in tip.lower() or "說明" in tip or "说明" in tip
            assert "(default)" in tip or "（預設）" in tip or "（默认）" in tip
            assert tip.find("AI") < tip.find("Keywords") or tip.find("AI") < tip.find("關鍵字") or tip.find("AI") < tip.find("关键字")

    print("check_recipe_cards_mode: OK")


if __name__ == "__main__":
    main()
