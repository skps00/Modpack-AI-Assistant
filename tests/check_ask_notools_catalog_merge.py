#!/usr/bin/env python3
"""Arch-3/3a guard: no-tools path (askNoTools) must carry the SAME [RECIPE_CARDS] catalog as slim.

Mirrors AskEngine.jeiForLlmFull() / recipeCatalogForLlm() / mergeJeiCatalogFull() /
stripRecipeCardsBlock() and asserts the source structure on both trees (forge + neoforge).

Why: beginAskLoop records the raw JEI summary under the jei_lookup fingerprint, so the later
noteShot0(jei_lookup, AskService jei) is rejected -> loop.jeiText() has no [RECIPE_CARDS]. Before
Arch-3/3a, askNoTools() (HTTP-400 fallback / tools-off / post-hop full path) therefore answered
without any card catalog while the UI showed cards - the catalog-leak class of contradiction.
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASK_ENGINE_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "AskEngine.java",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "AskEngine.java",
)

LEAD = "[RECIPE_CARDS] lead"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def is_recipe_catalog_entry_line(line: str) -> bool:
    """Mirror AskEngine.isRecipeCatalogEntryLine."""
    t = (line or "").strip()
    if not t:
        return False
    bar = t.find(" | ")
    if bar <= 0:
        return False
    if not all(ch.isdigit() for ch in t[:bar]):
        return False
    return "role=" in t[bar + 3:]


def strip_recipe_cards_block(jei_text: str) -> str:
    """Mirror AskEngine.stripRecipeCardsBlock - line filter (any block, any position, CRLF-safe)."""
    if jei_text is None or not jei_text.strip():
        return ""
    kept = []
    for raw in jei_text.split("\n"):
        line = raw[:-1] if raw.endswith("\r") else raw
        if "[RECIPE_CARDS]" in line or is_recipe_catalog_entry_line(line):
            continue
        kept.append(line)
    return "\n".join(kept).strip()


def _blank(s: str) -> bool:
    """Mirror Java String.isBlank() - NOT Python truthiness ("   " is blank in Java)."""
    return s is None or not s.strip()


def merge_jei_catalog_full(full_dump: str, catalog: str) -> str:
    """Mirror AskEngine.mergeJeiCatalogFull (merge, never replace)."""
    if _blank(catalog):
        return full_dump
    if _blank(full_dump):
        return catalog
    rest = strip_recipe_cards_block(full_dump)
    return catalog if not rest else catalog + "\n" + rest


def check_source(path: Path) -> None:
    src = read(path)

    for sym in ("recipeCatalogForLlm", "jeiForLlmFull", "mergeJeiCatalogFull",
                "stripRecipeCardsBlock", "isRecipeCatalogEntryLine"):
        assert sym in src, f"{path}: missing {sym}"

    # slim must reuse the shared catalog helper (assembly lives in exactly one place)
    slim_start = src.index("private String jeiForLlmSlim()")
    slim_end = src.index("private String jeiForLlmFull()", slim_start)
    slim = src[slim_start:slim_end]
    assert "recipeCatalogForLlm()" in slim, f"{path}: jeiForLlmSlim must reuse recipeCatalogForLlm()"
    assert "new StringBuilder(ReplyLang.recipeCardsCatalogLead" not in slim, (
        f"{path}: jeiForLlmSlim must not re-inline catalog assembly (belongs in recipeCatalogForLlm)"
    )
    assert "capableForTools()" in slim, f"{path}: jeiForLlmSlim must reuse capableForTools()"

    # full path delegates to the testable static merge
    full_start = src.index("private String jeiForLlmFull()")
    full_end = src.index("private String purposeForLlmSlim()", full_start)
    full = src[full_start:full_end]
    assert "mergeJeiCatalogFull(" in full, f"{path}: jeiForLlmFull must delegate to mergeJeiCatalogFull()"

    # catalog helper stays recipeCardLines-first (the real UI cards), fallbacks second
    helper_start = src.index("private String recipeCatalogForLlm()")
    helper_end = src.index("private String jeiForLlmSlim()", helper_start)
    helper = src[helper_start:helper_end]
    assert "loopState.recipeCardLines()" in helper, f"{path}: catalog helper must prefer recipeCardLines()"
    assert "recipeCardsCatalogSlim(" in helper, f"{path}: catalog helper must keep the fallback chain"

    # no-tools path takes the merged jei, never the slim-only one
    ant_start = src.index("public String askNoTools()")
    ant_end = src.index("public LlmRound completeWithTools", ant_start)
    ant = src[ant_start:ant_end]
    assert "factsFull" in ant, f"{path}: askNoTools must use factsFull"
    assert "jeiForLlmFull()" in ant, f"{path}: askNoTools must use jeiForLlmFull()"
    assert "jeiForLlmSlim()" not in ant, f"{path}: askNoTools must not use slim-only jei"
    assert "purposeForLlm" in ant, f"{path}: askNoTools must use purposeForLlm"

    # merge/strip helpers are package-private so AskToolLoopCheck can pin them
    assert "static String mergeJeiCatalogFull(" in src, f"{path}: mergeJeiCatalogFull must stay package-private static"
    assert "static String stripRecipeCardsBlock(" in src, f"{path}: stripRecipeCardsBlock must stay package-private static"


def check_behavior() -> None:
    catalog = LEAD + "\n0 | 铁镐 output 挖掘 role=output"

    # 1. raw dump without catalog (the askNoTools regression) -> catalog first, dump kept
    dump = "JEI summary line\nmachine brief"
    assert merge_jei_catalog_full(dump, catalog) == catalog + "\n" + dump

    # 2. dump already carries the block AS SLIM BUILDS IT (lead ends with \n -> blank line after
    #    the header) -> entries must NOT survive (review finding 1, blank line broke the strip)
    dirty = "JEI summary line\n" + LEAD + "\n\n0 | 铁镐 output 挖掘 role=output\nmachine brief"
    merged = merge_jei_catalog_full(dirty, catalog)
    assert merged.count("[RECIPE_CARDS]") == 1, merged
    assert merged == catalog + "\nJEI summary line\n\nmachine brief", merged

    # 3. two catalog blocks in one dump -> both stripped (review finding 2)
    twice = "A\n" + LEAD + "\n0 | a role=output\nB\n" + LEAD + "\n1 | b role=input\nC"
    assert strip_recipe_cards_block(twice) == "A\nB\nC", strip_recipe_cards_block(twice)

    # 4. dump with an inline (non-block) catalog -> single copy, surrounding text kept
    full2 = "JEI summary line\n" + LEAD + "\n0 | 铁镐 output 挖掘 role=output\nmachine brief"
    merged2 = merge_jei_catalog_full(full2, catalog)
    assert merged2.count("[RECIPE_CARDS]") == 1, merged2
    assert merged2 == catalog + "\nJEI summary line\nmachine brief", merged2

    # 5. no cards in this ask (cardLines empty, no fallback match) -> old behaviour: raw dump
    assert merge_jei_catalog_full(dump, None) == dump
    assert merge_jei_catalog_full(dump, "   ") == dump

    # 6. empty dump -> catalog still shipped
    assert merge_jei_catalog_full(None, catalog) == catalog
    assert merge_jei_catalog_full("", catalog) == catalog

    # 7. dump is only the catalog block -> single copy
    only = LEAD + "\n0 | a role=output"
    assert merge_jei_catalog_full(only, only) == only

    # 8. strip keeps text before AND after the block
    assert strip_recipe_cards_block("前言\n" + LEAD + "\n0 | a role=output\n後語") == "前言\n後語"

    # 9. CRLF-safe
    assert strip_recipe_cards_block("A\r\n" + LEAD + "\r\n0 | a role=output\r\nB") == "A\nB"

    # 10. no marker -> trim only
    assert strip_recipe_cards_block("  plain dump \n") == "plain dump"
    assert strip_recipe_cards_block(None) == ""
    assert strip_recipe_cards_block("   ") == ""

    # 11. non-catalog numbered lines survive
    assert strip_recipe_cards_block(LEAD + "\n0 | a role=output\n7 | 不是目錄行") == "7 | 不是目錄行"

    # 12. entry-line predicate
    assert is_recipe_catalog_entry_line("12 | x role=input")
    assert not is_recipe_catalog_entry_line("x | 12 role=input")
    assert not is_recipe_catalog_entry_line("0 | 没有 role 的行")
    assert not is_recipe_catalog_entry_line("")


def main() -> None:
    for path in ASK_ENGINE_PATHS:
        check_source(path)
    check_behavior()
    print("check_ask_notools_catalog_merge OK")


if __name__ == "__main__":
    main()
