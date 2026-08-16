#!/usr/bin/env python3
"""Ask Hybrid tool-loop v1 — Forge + Neo source lockstep."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SIDES = (
    (
        ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai",
        ROOT / "forge" / "1.19.2" / "src" / "test" / "java" / "com" / "skps9" / "packai" / "logic",
    ),
    (
        ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai",
        ROOT / "neoforge" / "1.21.1" / "src" / "test" / "java" / "com" / "skps9" / "packai" / "logic",
    ),
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def check_side(main: Path, test: Path) -> None:
    loop = read(main / "logic" / "AskToolLoop.java")
    assert "MAX_LLM_ROUNDS = 3" in loop
    assert "MAX_LOCAL_TOOLS = 8" in loop
    assert "WALL_MS = 90_000L" in loop
    assert 'JSON_MARKER = "[[tools]]"' in loop
    assert "drainBeforeFirstLlm" in loop
    assert "continueAfterAsk" in loop
    assert "firstAsk(" in loop
    assert "shouldOfferFirstRoundTools(" in loop
    assert "FIRST_ROUND_TOOLS" in loop
    assert "protocolProbe" in loop
    assert "fingerprint(" in loop
    assert "[TOOL_MISS]" in loop

    state = read(main / "logic" / "AskLoopState.java")
    assert "enum Intent" in state
    assert "craftEmpty(" in state
    assert "obtainEmpty(" in state
    assert "httpTimeout(" in state
    assert "countSuccessfulLlm(" in state

    ground = read(main / "logic" / "AskGrounding.java")
    assert "needsLookup(" in ground
    assert "containsAny(" in ground
    assert "jeiStationTemplate" in ground
    assert "[STATION_TEMPLATE]" not in ground

    assert "jeiStationTemplate" in state

    llm = read(main / "logic" / "LlmClient.java")
    assert "LlmRound completeRound(" in llm
    assert "urlLacksNativeTools(" in llm
    assert "nativeToolsSchema(" in llm
    assert "toolSchemaDescription" in llm
    assert "show_recipe_card" in llm
    assert "protocolProbe" in llm

    engine = read(main / "logic" / "AskEngine.java")
    assert "AskLoopState loop" in engine
    assert "drainBeforeFirstLlm" in engine
    assert "continueAfterAsk" in engine
    assert "firstAsk(" in engine
    assert "countSuccessfulLlm" in engine

    cfg = read(main / "config" / "PackAiConfig.java")
    assert "ASK_NATIVE_TOOLS" in cfg
    assert "askNativeTools" in cfg
    assert "askNativeToolsMode(" in cfg

    embed = read(main / "logic" / "RecipeEmbed.java")
    assert "[[recipe_card:" in embed or "recipe_card" in embed

    svc = read(main / "client" / "service" / "AskService.java")
    assert "beginAskLoop" in svc
    assert "AskToolLoop.WALL_MS" in svc
    assert "purposeGuide, askLoop" in svc

    ctx = read(main / "logic" / "AskToolContext.java")
    assert "AskToolLoop" in ctx
    assert "deferred" not in ctx.split("Recipe cards stay local")[0]

    for name in (
        "JeiLookupAskTool.java",
        "AcquireAskTool.java",
        "GuideFetchAskTool.java",
        "QuestFetchAskTool.java",
        "ConsumeUseAskTool.java",
        "ShowRecipeCardAskTool.java",
        "PurposeLookupAskTool.java",
        "ToolBuildAskTool.java",
        "TetraUseAskTool.java",
        "WorldgenLookupAskTool.java",
    ):
        body = read(main / "logic" / name)
        assert "implements AskTool" in body

    jei = read(main / "client" / "jei" / "AskJeiClient.java")
    assert "isSameThread()" in jei
    assert "future.get(" in jei
    assert "mc.execute" in jei

    check = read(test / "AskToolLoopCheck.java")
    assert "purposeZeroExtra" in check
    assert "h1CraftEmptyDrainsGuideQuestNotJei" in check
    assert "h2ObtainEmptyDrainsEvenIfJeiFat" in check
    assert "h3VariantArgsNotDup" in check
    assert "probe400NotARound" in check
    assert "jsonMarkerOnly" in check
    assert "firstAskCapableSendsFiveTools" in check
    assert "firstAsk400FallsBackNoTools" in check
    assert "firstAskOffNeverSends" in check
    assert "firstAskPurposeSendsTools" in check
    assert "followupRoundStillSendsTools" in check
    assert "roleToolMessageShape" in check
    assert "cardAlignMismatchOmits" in check
    assert "CAPABLE_TOOLS" in loop
    assert "show_recipe_card" in loop
    assert "worldgen_lookup" in loop

    gcheck = read(test / "AskGroundingCheck.java")
    assert "otherVariantNotSupport" in gcheck
    assert "maxOneLookup" in gcheck
    assert "stationTemplateGrounded" in gcheck


def main() -> None:
    for main_src, test_src in SIDES:
        check_side(main_src, test_src)
    print("check_ask_tool_loop: OK")


if __name__ == "__main__":
    main()
