#!/usr/bin/env python3
"""Card tool-emission v2 sentinels — forge + neo lockstep."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TREES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def check_tree(packai: Path) -> None:
    loop = read(packai / "logic" / "AskToolLoop.java")
    capable = loop[loop.index("CAPABLE_TOOLS") : loop.index("ALLOWLIST")]
    assert '"item_search"' in capable, f"{packai}: CAPABLE_TOOLS missing item_search"
    assert '"render_recipe_cards"' in capable, f"{packai}: CAPABLE_TOOLS missing render_recipe_cards"
    assert '"show_recipe_card"' not in capable, f"{packai}: show_recipe_card must not be in CAPABLE_TOOLS"
    assert "flushEmissionsTo" in loop

    result = read(packai / "logic" / "AskResult.java")
    assert "boolean cardStrip" in result
    assert "withRecipeCards(List<RecipeCard> cards, boolean strip)" in result
    assert "withRecipeCards(List<RecipeCard> cards)" in result

    ask = read(packai / "client" / "service" / "AskService.java")
    assert "Pack AI toolCards emission=" in ask
    assert "emittedCards()" in ask
    assert "withRecipeCards(cardsOut, true)" in ask
    assert "buildDisplayCards" not in ask
    assert "DisplayCardsBuilt" not in ask
    # KEYWORDS / ALWAYS still marker path
    assert "AskCardFallback.ensureCards" in ask
    assert "resolveAttach" in ask

    screen = read(packai / "client" / "gui" / "AiAssistantScreen.java")
    assert "result.cardStrip()" in screen or "msg.cardStrip()" in screen
    assert "boolean cardStrip" in screen
    assert "if (cardStrip)" in screen
    assert "appendAssistantBody" in screen
    assert "interleaveEmissionCards" in screen
    assert "indexBeforeSources" in screen

    embed = read(packai / "logic" / "RecipeEmbed.java")
    assert "interleaveEmissionCards" in embed
    assert "HOW_TO_UPGRADE_HEAD" in embed
    assert "splitTrailingSources" in embed
    # R5: peel sources before insert (sentinel in interleaveEmissionCards body)
    ile = embed[embed.index("interleaveEmissionCards") :]
    assert "splitTrailingSources" in ile[:800]

    emission = read(packai / "logic" / "CardEmission.java")
    assert "public record CardEmission" in emission
    assert "dedupeKey()" in emission

    state = read(packai / "logic" / "AskLoopState.java")
    assert "offerCardEmission(CardEmission emission)" in state
    assert "emittedCards()" in state
    assert "cardEmissions()" in state

    env = read(packai / "logic" / "AskToolEnv.java")
    assert "flushEmissionsTo(AskLoopState state)" in env
    assert "offerEmission(CardEmission emission)" in env
    assert "pendingEmissions" in env

    engine = read(packai / "logic" / "AskEngine.java")
    assert "new ItemSearchAskTool()" in engine
    assert "new RenderRecipeCardsAskTool()" in engine
    assert "ShowRecipeCardAskTool" not in engine

    llm = read(packai / "logic" / "LlmClient.java")
    assert 'if ("item_search".equals(name))' in llm
    assert 'if ("render_recipe_cards".equals(name))' in llm
    assert "Show JEI recipe cards under the answer" in llm

    stub = read(packai / "logic" / "ShowRecipeCardAskTool.java")
    assert "RETIRED" in stub
    assert 'return "show_recipe_card"' in stub

    render = read(packai / "logic" / "RenderRecipeCardsAskTool.java")
    assert "Pack AI renderCards item=" in render
    assert "afterFilter=" in render
    assert "SCAN_CAP" in render or "scannedCats=" in render
    assert "勿用相同 args 重試" in render or "Do not retry" in render

    loop = read(packai / "logic" / "AskToolLoop.java")
    assert 'emissionTool = "render_recipe_cards".equals(name)' in loop or (
        '"render_recipe_cards".equals(name) || "item_search".equals(name)' in loop
    )

    ask = read(packai / "client" / "service" / "AskService.java")
    assert "stripAiRecipeCardMarkers" in ask
    assert "Pack AI markerStrip count=" in ask

    jei = read(packai / "client" / "jei" / "JeiRecipeCards.java")
    assert "cardOutputMatchesFocus(card, stack) && layout == null" in jei


def assert_lockstep(a: Path, b: Path, rel: str) -> None:
    pa, pb = a / rel, b / rel
    assert pa.is_file() and pb.is_file(), f"missing {rel}"
    assert pa.read_text(encoding="utf-8") == pb.read_text(encoding="utf-8"), (
        f"dual-tree drift: {rel}"
    )


def main() -> None:
    forge, neo = TREES
    for packai in TREES:
        check_tree(packai)
    for rel in (
        "logic/AskToolLoop.java",
        "logic/AskResult.java",
        "logic/CardEmission.java",
        "logic/AskLoopState.java",
        "logic/AskToolEnv.java",
        "logic/ItemSearchAskTool.java",
        "logic/RenderRecipeCardsAskTool.java",
        "logic/ShowRecipeCardAskTool.java",
        # AskService may differ by loader line noise — sentinel-checked above, not byte-lockstep
    ):
        assert_lockstep(forge, neo, rel)
    print("check_card_tool_emission: OK")


if __name__ == "__main__":
    main()
