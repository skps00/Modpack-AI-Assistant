#!/usr/bin/env python3
"""Static checks for ItemSearch scoring + index wiring (Ask sidebar search UI removed DEL R4)."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def score(q: str, item_id: str, label: str, schematic_tokens: list[str] | None = None) -> int:
    """Mirror ItemSearch.score (lower better; 99 = no match).

    Id prefix/contains use path after ':' unless query includes 'namespace:'.
    Schematic band 7–9 uses cached tokens only (no NBT walk here).
    """
    q = (q or "").strip().lower()
    if not q:
        return 99
    idl = (item_id or "").lower()
    nl = (label or "").strip().lower()
    if idl == q:
        return 0
    colon = idl.find(":")
    path = idl[colon + 1 :] if colon >= 0 else idl
    q_has_ns = ":" in q
    if q_has_ns:
        if idl.startswith(q):
            return 2
        if q in idl:
            return 3
    else:
        if path == q or idl.endswith(":" + q):
            return 1
        if path.startswith(q):
            return 2
        if q in path:
            return 3
    if nl == q:
        return 4
    if nl.startswith(q):
        return 5
    if q in nl:
        return 6
    toks = schematic_tokens or []
    for raw in toks:
        t = (raw or "").strip().lower()
        if t and t == q:
            return 7
    for raw in toks:
        t = (raw or "").strip().lower()
        if t and t.startswith(q):
            return 8
    for raw in toks:
        t = (raw or "").strip().lower()
        if t and q in t:
            return 9
    return 99


def main() -> None:
    forge_is = read("forge/1.19.2/src/main/java/com/skps9/packai/client/knowledge/ItemSearch.java")
    neo_is = read("neoforge/1.21.1/src/main/java/com/skps9/packai/client/knowledge/ItemSearch.java")
    for src in (forge_is, neo_is):
        assert "DEFAULT_LIMIT = 10" in src
        assert "getAllIngredients" in src
        assert "static int score" in src
        assert "admitOverWorst" in src
        assert "selectionKey" in src
        assert "ItemVariantKeys.schematicTokens" in src
        assert "score(q, id, label, schemToks)" in src
        assert "ItemIndex.INSTANCE.searchReady" in src
        assert "liveSearch" in src
        assert "best.size() >= SCAN_CANDIDATE_CAP) {\n                            break;" not in src
        assert "if (best.size() >= SCAN_CANDIDATE_CAP)" not in src or "admitOverWorst" in src
        # No early-break that freezes first N JEI matches (live path)
        live = src.split("static List<Hit> liveSearch")[1].split("private static void consider")[0]
        assert "break;" not in live.split("for (ItemStack stack : all)")[1].split("catch")[0]
        assert "qHasNs" in src or "q.indexOf(':')" in src
        assert "setFocused(this.input)" not in src  # focus fix lives in screen
        # D10: score must not call schematics()/schematicTokens on its own
        score_fn = src.split("static int score(String q, String id, String label, List<String> schematicTokens)")[1].split(
            "private static String norm"
        )[0]
        assert "ItemVariantKeys" not in score_fn
        assert "getTag" not in score_fn and "CUSTOM_DATA" not in score_fn
    assert "Registry.ITEM" in forge_is
    assert "BuiltInRegistries.ITEM" in neo_is

    forge_pk = read("forge/1.19.2/src/main/java/com/skps9/packai/client/knowledge/PackKnowledge.java")
    neo_pk = read("neoforge/1.21.1/src/main/java/com/skps9/packai/client/knowledge/PackKnowledge.java")
    assert "searchItems" in forge_pk and "searchItems" in neo_pk
    assert "ensureItemIndex" in forge_pk and "ensureItemIndex" in neo_pk
    forge_ui = read("forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java")
    neo_ui = read("neoforge/1.21.1/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java")
    for ui in (forge_ui, neo_ui):
        # DEL R4: Ask sidebar search EditBox + hit list removed; knowledge index stays.
        assert "onSearchChanged" not in ui
        assert "applySearchHit" not in ui
        assert "renderSearchHits" not in ui
        assert "searchBox" not in ui
        assert "PackKnowledge.searchItems" not in ui
        assert "PackKnowledge.ensureItemIndex" in ui
        assert "AskService.selectionKey" in ui

    forge_ask = read("forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java")
    neo_ask = read("neoforge/1.21.1/src/main/java/com/skps9/packai/client/service/AskService.java")
    for ask in (forge_ask, neo_ask):
        assert "public static String selectionKey" in ask
        # collectAskRecipeCards must dedupe via selectionKey, not bare registry id
        assert "selectionKey(fromStack(focus))" in ask
        start = ask.index("static List<RecipeCard> collectAskRecipeCards")
        end = ask.index("static ItemStack cardFocusStack", start)
        cards_fn = ask[start:end]
        assert "selectionKey(ref)" in cards_fn
        assert "done.add(id)" not in cards_fn

    for tree in (
        "forge/1.19.2/src/main/resources/assets/packai/lang",
        "neoforge/1.21.1/src/main/resources/assets/packai/lang",
    ):
        for lang in ("en_us.json", "zh_tw.json", "zh_cn.json"):
            text = read(f"{tree}/{lang}")
            for tip_key in (
                "packai.screen.tooltip.input",
                "packai.screen.tooltip.send",
                "packai.screen.tooltip.regenerate",
                "packai.screen.tooltip.clear_chat",
                "packai.screen.tooltip.pick_items",
                "packai.screen.tooltip.open_quest",
                "packai.screen.tooltip.quest_more",
                "packai.screen.tooltip.quest_next",
                "packai.screen.tooltip.next_step",
                "packai.screen.tooltip.jump_latest",
                "packai.screen.tooltip.settings",
            ):
                assert tip_key in text, f"missing {tip_key} in {tree}/{lang}"

    assert score("minecraft:dirt", "minecraft:dirt", "Dirt") == 0
    assert score("dirt", "minecraft:dirt", "Dirt") == 1
    assert score("iron", "minecraft:iron_ingot", "Iron Ingot") == 2
    assert score("ingot", "minecraft:iron_ingot", "Iron Ingot") == 3
    assert score("iron ingot", "minecraft:iron_ingot", "Iron Ingot") == 4
    assert score("iron i", "minecraft:iron_ingot", "Iron Ingot") == 5
    assert score("n in", "minecraft:stone", "Iron Ingot") == 6  # name contains only
    assert score("zzz", "minecraft:dirt", "Dirt") == 99
    # path-only: bare 'm' must NOT match all minecraft:*
    assert score("m", "minecraft:dirt", "Dirt") == 99
    assert score("m", "minecraft:mud", "Mud") == 2
    assert score("minecraft:", "minecraft:dirt", "Dirt") == 2
    assert score("minecraft:di", "minecraft:dirt", "Dirt") == 2

    # Schematic tokens (cached): Chinese label alone would miss English schematic query
    scroll = "tetra:scroll_rolled"
    mirror_toks = ["tetra:mirror", "mirror"]
    assert score("mirror", scroll, "鏡面反射", mirror_toks) == 7
    assert score("tetra:mirror", scroll, "鏡面反射", mirror_toks) == 7
    assert score("mir", scroll, "卷軸", mirror_toks) == 8
    gild_toks = ["hone/gild_2", "gild_2", "tetra:hone/gild_2"]
    assert score("gild", scroll, "卷軸", gild_toks) == 8  # gild_2 starts with gild
    assert score("ild_2", scroll, "卷軸", gild_toks) == 9  # contains only
    # Normal item unchanged: empty tokens → still 99 for junk query
    assert score("mirror", "minecraft:dirt", "Dirt", []) == 99
    # Label still beats schematic when both match (startsWith before schematic band)
    assert score("mirror", scroll, "Mirror Scroll", mirror_toks) == 5
    assert score("irror", scroll, "Mirror Scroll", mirror_toks) == 6

    print("check_item_search OK")


if __name__ == "__main__":
    main()
