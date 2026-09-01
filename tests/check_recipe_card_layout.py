#!/usr/bin/env python3
"""Mirrors JeiRecipeCards.fitsCrafting3x3 / titleWithSlotCount / formatCountedLabels."""


def is_vanilla_sized_crafting_title(title: str | None) -> bool:
    if title is None or not title.strip():
        return False
    t = title.lower()
    for bad in (
        "mechanical",
        "动力",
        "動力",
        "automated",
        "sequenced",
        "assembly",
        "装配",
        "裝配",
    ):
        if bad in t or bad in title:
            return False
    return "crafting" in t or "工作台" in title or "合成" in title


def fits_crafting_3x3(title: str | None, input_slots: int) -> bool:
    if input_slots <= 0 or input_slots > 9:
        return False
    return is_vanilla_sized_crafting_title(title)


def title_with_slot_count(title: str | None, slots: int) -> str:
    base = "?" if title is None or not title.strip() else title.strip()
    if slots <= 0:
        return base
    return f"{base} · {slots} slots"


def title_large_grid(title: str | None, total: int, shown: int) -> str:
    base = title_with_slot_count(title, total)
    if shown < total:
        return base + " · grid truncated — open JEI"
    return base


def format_counted_labels(counts: dict[str, int], max_n: int) -> list[str]:
    out: list[str] = []
    for key, n in counts.items():
        if len(out) >= max_n:
            break
        nn = 1 if n is None else n
        out.append(f"{key}×{nn}" if nn > 1 else key)
    return out


def card_budget(item_count: int, per_item: int) -> int:
    """Ask total cap: items × 2 roles × per-role N."""
    return max(1, item_count) * max(1, min(8, per_item)) * 2


def has_useful_positions(placed: list[tuple[int, int]]) -> bool:
    if placed is None or len(placed) < 2:
        return False
    jei_slot_stride = 18
    xs = [p[0] for p in placed]
    ys = [p[1] for p in placed]
    return (max(xs) - min(xs)) >= jei_slot_stride or (max(ys) - min(ys)) >= jei_slot_stride


def prefer_multi_role_panel(title: str | None, panel_count: int) -> bool:
    """Mirror JeiRecipeCards.preferMultiRolePanel (≥2 visible slots; crafting included)."""
    return panel_count >= 2


def prefer_layout(
    title: str | None,
    input_slots: int,
    placed: list[tuple[int, int]],
    panel_count: int | None = None,
    layout_catalysts: int = 0,
) -> str:
    """Mirror JeiRecipeCards.fromLayout — SHAPED/xy before CRAFTING_3X3 smash."""
    n_panel = panel_count if panel_count is not None else len(placed)
    if prefer_multi_role_panel(title, n_panel) or has_useful_positions(placed):
        return "SHAPED"
    if fits_crafting_3x3(title, input_slots) and layout_catalysts == 0:
        return "CRAFTING_3X3"
    return "FLOW"


def title_with_machine(title: str | None, machine_name: str | None) -> str:
    """Mirror JeiRecipeCards.titleWithMachine (first catalyst hover name)."""
    base = "" if title is None else title.strip()
    name = "" if machine_name is None else machine_name.strip()
    if not name:
        return base
    if name.lower() in base.lower():
        return base
    if not base:
        return name
    return f"{base} · {name}"


def is_core_craft_category(title: str | None) -> bool:
    """Mirror CraftPriority.isCoreCraftCategory (crafting/stonecut/smelt/campfire; not quest)."""
    t = (title or "").lower()
    if not t:
        return False
    for q in ("quest", "任務", "reward table", "獎勵表", "任務獎勵", "quest reward"):
        if q in t or q in (title or ""):
            return False
    machine_keys = ("自動", "自动", "動力", "合成器", "機器", "机器", "機", "机", "machine", "auto", "工作站")
    table_keys = ("crafting table", "工作台")

    def machine_like() -> bool:
        if any(k in t or k in (title or "") for k in table_keys):
            return False
        return any(k in t or k in (title or "") for k in machine_keys)

    tiers = [
        ("crafting table", "crafting", "工作台", "合成"),
        ("stonecut", "切石"),
        ("smelt", "furnace", "blast", "熔爐", "高爐"),
        ("campfire", "smoker", "煙燻", "營火"),
    ]
    for keys in tiers:
        for k in keys:
            if k in t or k in (title or ""):
                return not machine_like()
    return False


def ask_category_sort_key(title: str, prefs_sort: int) -> tuple[int, int]:
    """Core craft first, then RecipeCategoryPrefs.sortKey."""
    return (0 if is_core_craft_category(title) else 1, prefs_sort)


def ensure_core_craft(jei_titles: list[str], vanilla_titles: list[str], max_cards: int) -> list[str]:
    """Mirror JeiRecipeCards.ensureCoreCraft title order (simplified)."""
    has_core = any(is_core_craft_category(t) for t in jei_titles)
    if has_core:
        return jei_titles[:max_cards]
    out: list[str] = []
    for t in vanilla_titles + jei_titles:
        if len(out) >= max_cards:
            break
        out.append(t)
    return out


def merge_vanilla_uses(
    cards: list[tuple[str, str]], vanilla_use_titles: list[str], max_in: int
) -> list[tuple[str, str]]:
    """Mirror JeiRecipeCards.mergeVanillaUses — (role, title); core INPUT first."""
    has_core_in = any(
        role == "input" and is_core_craft_category(title) for role, title in cards
    )
    if has_core_in:
        return list(cards)
    out = [(r, t) for r, t in cards if r != "input"]
    ins = [(r, t) for r, t in cards if r == "input"]
    uses = [("input", t) for t in vanilla_use_titles]
    return out + (uses + ins)[: max(0, int(max_in))]


def main() -> None:
    assert fits_crafting_3x3("Crafting", 9)
    assert fits_crafting_3x3("工作台", 4)
    assert not fits_crafting_3x3("Mechanical Crafting", 9)
    assert not fits_crafting_3x3("动力合成", 41)
    assert not fits_crafting_3x3("Crafting", 41)
    assert not fits_crafting_3x3("Sequenced Assembly", 3)

    assert title_with_slot_count("动力合成", 41) == "动力合成 · 41 slots"
    assert title_large_grid("动力合成", 81, 81) == "动力合成 · 81 slots"
    assert title_large_grid("动力合成", 81, 48) == "动力合成 · 81 slots · grid truncated — open JEI"
    assert format_counted_labels({"Iron": 3, "Gold": 1}, 40) == ["Iron×3", "Gold"]
    assert format_counted_labels({f"I{i}": 1 for i in range(81)}, 81) == [f"I{i}" for i in range(81)]
    assert len(format_counted_labels({f"I{i}": 1 for i in range(90)}, 81)) == 81
    # Cap mirrors JeiRecipeCards.MAX_FLOW_INPUT_SLOTS (Create 9×9)
    MAX_FLOW_INPUT_SLOTS = 81
    assert MAX_FLOW_INPUT_SLOTS >= 81
    assert card_budget(3, 3) == 18
    assert card_budget(1, 3) == 6
    assert card_budget(0, 3) == 6

    # Create diamond: irregular JEI coords → SHAPED not FLOW
    diamond = [(54, 0), (36, 18), (72, 18), (18, 36), (54, 36), (90, 36), (36, 54), (72, 54), (54, 72)]
    assert has_useful_positions(diamond)
    assert prefer_layout("动力合成", 41, diamond + [(i * 18, 90) for i in range(32)]) == "SHAPED"
    # Vanilla crafting with JEI xy / multi-role panel → SHAPED (drawable path), not CRAFTING_3X3 smash
    assert prefer_layout("Crafting", 9, [(0, 0), (18, 0), (36, 0)]) == "SHAPED"
    assert prefer_multi_role_panel("Crafting", 4)
    assert prefer_layout("Crafting", 9, [(0, 0), (18, 0)], panel_count=4) == "SHAPED"
    # INPUT-only tight coords stay FLOW unless multi-role panel qualifies
    assert prefer_layout("Smelting", 2, [(0, 0), (1, 0)], panel_count=1) == "FLOW"
    # Cooking / machine: ≥2 multi-role slots → SHAPED even if INPUT span < 18
    assert prefer_multi_role_panel("烹饪", 4)
    assert prefer_layout("烹饪", 2, [(0, 0), (1, 0)], panel_count=4) == "SHAPED"
    assert prefer_layout("Smelting", 1, [(0, 0)], panel_count=3) == "SHAPED"
    # No useful xy + no multi-role panel → CRAFTING_3X3 smash fallback
    assert prefer_layout("Crafting", 9, [(0, 0)], panel_count=1, layout_catalysts=0) == "CRAFTING_3X3"
    assert prefer_layout("烹饪", 2, [(0, 0), (1, 0)], panel_count=4, layout_catalysts=0) == "SHAPED"
    assert title_with_machine("烹饪", "烹饪锅") == "烹饪 · 烹饪锅"
    assert title_with_machine("烹饪 · 烹饪锅", "烹饪锅") == "烹饪 · 烹饪锅"
    assert title_with_machine("Cooking", "Cooking Pot") == "Cooking · Cooking Pot"
    assert is_core_craft_category("Crafting")
    assert is_core_craft_category("Smelting")
    assert is_core_craft_category("合成")
    assert is_core_craft_category("工作台")
    assert not is_core_craft_category("自動合成 · 動力合成器")
    assert not is_core_craft_category("Analyzer")
    assert not is_core_craft_category("Quests")
    assert not is_core_craft_category("Quest Reward Table")
    # Quests/Analyzer fill slots → vanilla Crafting still prepended
    assert ensure_core_craft(["Quests", "Analyzer", "Analyzer"], ["Crafting"], 3) == [
        "Crafting",
        "Quests",
        "Analyzer",
    ]
    assert ensure_core_craft(["Crafting", "Quests"], ["Crafting"], 3) == ["Crafting", "Quests"]
    # Machine-only JEI (matches「合成」keyword) → vanilla Crafting prepended first
    assert ensure_core_craft(["自動合成 · 動力合成器"], ["Crafting"], 2) == [
        "Crafting",
        "自動合成 · 動力合成器",
    ]
    # Altar OUTPUT + altar/quest INPUT (no table U) → vanilla shapeless use inserted, OUTPUT kept
    assert merge_vanilla_uses(
        [("output", "Summoning Altar"), ("input", "Summoning Altar · 17 slots"), ("input", "Quests")],
        ["Crafting"],
        3,
    ) == [
        ("output", "Summoning Altar"),
        ("input", "Crafting"),
        ("input", "Summoning Altar · 17 slots"),
        ("input", "Quests"),
    ]
    assert merge_vanilla_uses(
        [("output", "Crafting"), ("input", "Crafting")],
        ["Crafting"],
        3,
    ) == [("output", "Crafting"), ("input", "Crafting")]
    # Ask sort: craft before Analyzer even if prefs put Analyzer first
    cats = [("Analyzer", 0), ("Crafting", 1), ("Quests", 2)]
    cats.sort(key=lambda c: ask_category_sort_key(c[0], c[1]))
    assert [c[0] for c in cats] == ["Crafting", "Analyzer", "Quests"]

    # B) All recipe card layouts attach JEI layout drawable when available — both trees
    from pathlib import Path

    root = Path(__file__).resolve().parents[1]
    for tree in ("neoforge/1.21.1", "forge/1.19.2"):
        draw = (root / tree / "src/main/java/com/skps9/packai/client/jei/JeiLayoutDraw.java").read_text(
            encoding="utf-8"
        )
        cards = (root / tree / "src/main/java/com/skps9/packai/client/jei/JeiRecipeCards.java").read_text(
            encoding="utf-8"
        )
        recipe = (root / tree / "src/main/java/com/skps9/packai/logic/RecipeCard.java").read_text(
            encoding="utf-8"
        )
        screen = (root / tree / "src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java").read_text(
            encoding="utf-8"
        )
        assert "createRecipeLayoutDrawable" in draw
        assert "layout() != RecipeCard.Layout.SHAPED" not in draw
        assert "JeiLayoutDraw.attach" in cards
        assert "attachJeiCraftingLayout" in cards
        assert "upgradeCraftingLayouts" in cards
        assert "fromVanillaCraftingUses" in cards
        assert "mergeVanillaUses" in cards
        assert "includeHidden" in cards
        assert "Object jeiLayout" in recipe
        assert "withJeiLayout" in recipe
        assert "tryRenderJeiRecipeLayout" in screen
        assert "itemUnderMouse" in draw
        assert "mapScreenMouseToJei" in draw
        assert "registerJeiLayoutItemHovers" in screen
        assert "OUTSIDE_DRAW_PAD" in draw
        assert "layoutFitWidth" in draw
        assert "layoutFitHeight" in draw
        # JEI drawable always 1:1 — no pose.scale / FBO (Create Sawing OUTPUT drift)
        assert "Always 1:1" in draw or "always 1:1" in draw.lower()
        assert "drawScaledPoseFallback" not in draw
        assert "drawScaledViaFbo" not in draw
        assert "TextureTarget" not in draw
        assert "MAX_FBO_EDGE" not in draw
        assert "setupForFlatItems" in draw
        # Do NOT wipe ModelView to identity — blanks JEI layout (SHA 75EE42A2 regression)
        assert "getModelViewStack" not in draw
        assert "setIdentity" not in draw
        assert "modelView.identity()" not in draw
        assert "applyModelViewMatrix" not in draw
        assert "return 1.0f" in screen
        assert "pose.scale desyncs" in screen or "Always 1:1 for JEI" in screen
        # Scale helpers still present for harvest SHAPED (no drawable)
        assert "MAX_SHAPED_CARD_H" in screen
        assert "jeiDrawableFitsPanel" in screen
        assert "preferHarvestStrip" in recipe
        assert "hasVisibleItemSlots" in draw
        assert "preferHarvestStrip()" in screen
        assert "JeiLayoutDraw.width(card)" in draw or "JeiLayoutDraw.height(card)" in draw or "hasLayout" in screen
        assert "layoutFitWidth(card)" not in screen
        # Slot hover: JEI drawHoverOverlays (not drawRecipe); avoid full drawOverlays tooltips
        assert "drawSlotHoverHighlight" in draw
        assert "drawHoverOverlays" in draw
        assert "getSlotUnderMouse" in draw
        # setPosition origin matches draw + hover (no separate scale map)
        assert "drawable.setPosition(left, top)" in draw
        assert "mapScreenMouseToJei" in draw
        assert "return new int[]{mouseX, mouseY}" in draw
        # Fluids: JEI FluidTankRenderer via drawRecipe (no Pack AI renderPlacedFluids overlay).
        # Hover: layoutHoverUnderMouse = getSlotUnderMouse + slot getRect (not full-card hitbox).
        assert "PlacedFluid" in recipe
        assert "hasPlacedFluids" in recipe
        assert "placedVisibleFluids" in (
            root / tree / "src/main/java/com/skps9/packai/client/jei/JeiRecipeLayoutCollector.java"
        ).read_text(encoding="utf-8")
        assert "layoutHoverUnderMouse" in draw
        assert "FLUID_STACK" in draw
        assert "renderPlacedFluids" not in screen
        assert "setFluidRendererSize" in (
            root / tree / "src/main/java/com/skps9/packai/client/jei/JeiRecipeLayoutCollector.java"
        ).read_text(encoding="utf-8")
        # Hexerei: Woodcutter/Mortar = slots-before-extras; MixingCauldron/FluidMixing = isolated extras then slots
        assert "needsHexereiSlotsBeforeExtras" in draw
        assert "drawHexereiSlotsBeforeExtras" in draw
        assert "needsHexereiIsolatedExtrasThenSlots" in draw
        assert "drawHexereiIsolatedExtrasThenSlots" in draw
        assert "WoodcutterRecipeCategory" in draw
        assert "PestleAndMortarRecipeCategory" in draw
        assert "MixingCauldronRecipeCategory" in draw
        assert "FluidMixingRecipeCategory" in draw
        assert "isHexereiCategory" not in draw  # all-hexerei gate still removed
        # DEL R4: Ask sidebar search overlay removed.
        assert "searchBoxY" not in screen
        assert "renderSearchHits" not in screen

    print("check_recipe_card_layout OK")


if __name__ == "__main__":
    main()
