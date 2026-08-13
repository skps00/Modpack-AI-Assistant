#!/usr/bin/env python3
"""Ask Hybrid tool-loop v1 — Forge source lockstep (Neo waits T8)."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai"
TEST = ROOT / "forge" / "1.19.2" / "src" / "test" / "java" / "com" / "skps9" / "packai" / "logic"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> None:
    loop = read(FORGE / "logic" / "AskToolLoop.java")
    assert "MAX_LLM_ROUNDS = 3" in loop
    assert "MAX_LOCAL_TOOLS = 8" in loop
    assert "WALL_MS = 90_000L" in loop
    assert 'JSON_MARKER = "[[tools]]"' in loop
    assert "drainBeforeFirstLlm" in loop
    assert "continueAfterAsk" in loop
    assert "protocolProbe" in loop
    assert "fingerprint(" in loop

    state = read(FORGE / "logic" / "AskLoopState.java")
    assert "enum Intent" in state
    assert "craftEmpty(" in state
    assert "obtainEmpty(" in state
    assert "httpTimeout(" in state
    assert "countSuccessfulLlm(" in state

    ground = read(FORGE / "logic" / "AskGrounding.java")
    assert "needsLookup(" in ground
    assert "containsAny(" in ground
    assert "jeiStationTemplate" in ground
    assert "[STATION_TEMPLATE]" not in ground

    assert "jeiStationTemplate" in state

    llm = read(FORGE / "logic" / "LlmClient.java")
    assert "LlmRound completeRound(" in llm
    assert "urlLacksNativeTools(" in llm
    assert "nativeToolsSchema(" in llm
    assert "protocolProbe" in llm

    engine = read(FORGE / "logic" / "AskEngine.java")
    assert "AskLoopState loop" in engine
    assert "drainBeforeFirstLlm" in engine
    assert "continueAfterAsk" in engine
    assert "countSuccessfulLlm" in engine

    svc = read(FORGE / "client" / "service" / "AskService.java")
    assert "beginAskLoop" in svc
    assert "AskToolLoop.WALL_MS" in svc
    assert "purposeGuide, askLoop" in svc

    ctx = read(FORGE / "logic" / "AskToolContext.java")
    assert "AskToolLoop" in ctx
    assert "deferred" not in ctx.split("Recipe cards stay local")[0]

    for name in (
        "JeiLookupAskTool.java",
        "AcquireAskTool.java",
        "GuideFetchAskTool.java",
        "QuestFetchAskTool.java",
        "ConsumeUseAskTool.java",
    ):
        body = read(FORGE / "logic" / name)
        assert "implements AskTool" in body

    jei = read(FORGE / "client" / "jei" / "AskJeiClient.java")
    assert "isSameThread()" in jei
    assert "future.get(" in jei
    assert "mc.execute" in jei

    check = read(TEST / "AskToolLoopCheck.java")
    assert "purposeZeroExtra" in check
    assert "h1CraftEmptyDrainsGuideQuestNotJei" in check
    assert "h2ObtainEmptyDrainsEvenIfJeiFat" in check
    assert "h3VariantArgsNotDup" in check
    assert "probe400NotARound" in check
    assert "jsonMarkerOnly" in check

    gcheck = read(TEST / "AskGroundingCheck.java")
    assert "otherVariantNotSupport" in gcheck
    assert "maxOneLookup" in gcheck
    assert "stationTemplateGrounded" in gcheck

    print("check_ask_tool_loop: OK")


if __name__ == "__main__":
    main()
