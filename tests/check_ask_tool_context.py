#!/usr/bin/env python3
"""Plan B: AskToolContext intent-gated JEI/loot budgets (Forge+Neo lockstep)."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SIDES = (
    "forge/1.19.2/src/main/java/com/skps9/packai",
    "neoforge/1.21.1/src/main/java/com/skps9/packai",
)


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for side in SIDES:
        ctx = read(f"{side}/logic/AskToolContext.java")
        assert "Plan B" in ctx
        assert "MAX_JEI_USES_CHARS = 400" in ctx
        assert "MAX_JEI_OUTPUT_CHARS = 4000" in ctx
        assert "MAX_JEI_OUTPUT_SLIM_CHARS = 900" in ctx
        assert "MAX_ACQUIRE_LINES_SLIM = 3" in ctx
        assert "MAX_ACQUIRE_LINES_FULL = 12" in ctx
        assert "enum JeiDumpLevel" in ctx
        assert "jeiDumpLevel(" in ctx
        assert "wantsFullAcquire(" in ctx
        assert "clipAcquireLines(" in ctx
        assert "clipChars(" in ctx
        assert "truncateBuilderFrom(" in ctx
        if side.startswith("forge/"):
            assert "AskToolLoop" in ctx
        else:
            assert "deferred" in ctx or "AskToolLoop" in ctx

        # Mirror intent gate (same rules as Java).
        assert "isCraftOrientedQuestion" in ctx
        assert "isAcquireOrientedQuestion" in ctx

        engine = read(f"{side}/logic/AskEngine.java")
        assert "AskToolContext.clipAcquireLines" in engine
        assert "AskToolContext.wantsFullAcquire" in engine
        assert "AskToolContext.clipChars" in engine
        assert "AskToolContext.MAX_JEI_USES_CHARS" in engine
        assert "jeiLevel.outputBudget()" in engine
        assert "skip loot encyclopedia overflow" in engine

        jei = read(f"{side}/client/jei/JeiLookup.java")
        assert "AskToolContext.JeiDumpLevel" in jei
        assert "summarize(ItemStack stack, AskToolContext.JeiDumpLevel level)" in jei
        assert "truncateBuilderFrom" in jei
        assert "level.usesBudget()" in jei
        assert "level.outputBudget()" in jei

        svc = read(f"{side}/client/service/AskService.java")
        assert "AskToolContext.jeiDumpLevel" in svc
        assert "JeiLookup.summarize(cardFocus, jeiLevel)" in svc
        assert "JeiDumpLevel.SLIM" in svc

    # Intent fixtures (Python mirror of PackIndex keywords used by AskToolContext).
    def craft(q: str) -> bool:
        q = q.lower()
        return any(k in q for k in ("配方", "合成", "如何做", "怎麼做", "怎么做", "craft", "recipe", "how to make"))

    def acquire(q: str) -> bool:
        q = q.lower()
        return any(k in q for k in ("如何取得", "怎么取得", "怎麼取得", "how to get", "obtain", "如何獲得"))

    def level(q: str) -> str:
        return "OUTPUT" if craft(q) or acquire(q) else "SLIM"

    assert level("這個有什麼用") == "SLIM"
    assert level("用途是什麼") == "SLIM"
    assert level("配方怎麼做") == "OUTPUT"
    assert level("如何取得鐵錠") == "OUTPUT"
    assert level("how to get diamond") == "OUTPUT"
    assert level("how to make a pickaxe") == "OUTPUT"

    def clip_chars(text: str, max_chars: int) -> str:
        t = text.strip()
        if len(t) <= max_chars:
            return t
        return t[: max(1, max_chars - 1)] + "…"

    fat_u = "U" * 2000
    clipped = clip_chars(fat_u, 400)
    assert len(clipped) == 400
    assert clipped.endswith("…")

    def clip_lines(lines: list[str], max_n: int) -> list[str]:
        out = []
        for line in lines:
            if not line.strip():
                continue
            out.append(line)
            if len(out) >= max_n:
                break
        return out

    assert len(clip_lines([f"L{i}" for i in range(12)], 3)) == 3
    assert len(clip_lines([f"L{i}" for i in range(12)], 12)) == 12

    print("check_ask_tool_context: OK")


if __name__ == "__main__":
    main()
