# -*- coding: utf-8 -*-
"""Summon / gas / extra outputs stay on FACT + catalog IO. Dual-tree string asserts."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SIDES = (
    "forge/1.19.2/src/main/java/com/skps9/packai",
    "neoforge/1.21.1/src/main/java/com/skps9/packai",
)
TEST_SIDES = (
    "forge/1.19.2/src/test/java/com/skps9/packai/logic",
    "neoforge/1.21.1/src/test/java/com/skps9/packai/logic",
)


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def slice_method(src: str, name: str) -> str:
    key = f" {name}("
    i = src.find(key)
    if i < 0:
        i = src.find(f"\n    static String {name}(")
    if i < 0:
        i = src.find(f"\n    private static String {name}(")
    if i < 0:
        raise AssertionError(f"missing {name}")
    start = src.rfind("\n", 0, i)
    brace = src.find("{", i)
    depth = 0
    for j in range(brace, len(src)):
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0:
                return src[start:j + 1]
    raise AssertionError(f"unclosed {name}")


def main() -> None:
    for side in SIDES:
        summary = read(f"{side}/logic/RecipeIoSummary.java")
        assert "joinOutputSide" in summary
        assert "joinExtraLabels" in summary
        assert "looksLikeResourceId" in summary
        assert "joinNamedCounts" in summary
        extra = read(f"{side}/logic/RecipeExtra.java")
        assert "uniqueId" in extra
        lookup = read(f"{side}/logic/SummonRecipeLookup.java")
        assert 'PREFIX = "summon: "' in lookup
        assert "isSummonQuestion" in lookup
        engine = read(f"{side}/logic/AskEngine.java")
        assert "skipWebForSummon" in engine
        assert "AskNameResolve.relatedHintIds" in engine
        assert "shouldPinSummonMiss" in engine
        name = read(f"{side}/logic/AskNameResolve.java")
        assert "resolveId" in name
        assert "knight_garent" not in name
        assert "occultism" not in lookup.lower()
        assert "bloodmagic" not in lookup.lower()
        assert "hexerei" not in lookup.lower()
        assert "minecraft:zombie" not in lookup

        ask = read(f"{side}/client/service/AskService.java")
        prompt = slice_method(ask, "static String promptCardLine")
        assert "joinOutputSide" in prompt
        assert "otherOutputs" in prompt
        assert "fluidDisplayNames" in prompt
        assert "→ " in prompt
        assert "joinStackNames(c.outputs())" not in prompt
        assert "appendSummonFact" in ask
        assert "SummonRecipeLookup.factLine" in ask

        jei = read(f"{side}/client/jei/JeiLookup.java")
        fmt = slice_method(jei, "static String formatRecipe")
        short = slice_method(jei, "static String shortIoLine")
        for body in (fmt, short):
            assert "extraOutputLabels" in body
            assert "jeiNoOut" in body
            assert "outputs.isEmpty()" in body
        cards = read(f"{side}/client/jei/JeiRecipeCards.java")
        assert "honestResourceId" in cards
        assert "getResourceLocation" in cards
        collector = read(f"{side}/client/jei/JeiRecipeLayoutCollector.java")
        assert "firstItemInSlot" in collector

    for test in TEST_SIDES:
        io = read(f"{test}/RecipeIoSummaryCheck.java")
        assert 'RecipeExtra("Summoned Foo"' in io
        assert "joinOutputSide" in io
        assert "→ " in io
        sm = read(f"{test}/SummonRecipeLookupCheck.java")
        assert "Summoned Foo" in sm
        ar = read(f"{test}/AskNameResolveCheck.java")
        assert "最初的骑士" in ar
        assert "somebosses:knight_garent" in ar
        assert "minecraft:" in sm  # must assert we do NOT invent it
        assert "noInvent.isEmpty()" in sm

    print("check_summon_entity_recipes OK")


if __name__ == "__main__":
    main()
