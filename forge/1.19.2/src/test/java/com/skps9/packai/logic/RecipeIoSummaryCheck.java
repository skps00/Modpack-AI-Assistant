package com.skps9.packai.logic;

import java.util.List;

/** Catalog IO aggregation — identical 3×3 slots → Name×9 (not 8 truncated names). */
public final class RecipeIoSummaryCheck {
    private RecipeIoSummaryCheck() {}

    public static void main(String[] args) {
        // 9 identical names (one per slot) → ×9, never stop at 8.
        List<String> nine = List.of(
                "Forbidden Fragment",
                "Forbidden Fragment",
                "Forbidden Fragment",
                "Forbidden Fragment",
                "Forbidden Fragment",
                "Forbidden Fragment",
                "Forbidden Fragment",
                "Forbidden Fragment",
                "Forbidden Fragment");
        String joined = RecipeIoSummary.joinNamedCounts(nine, null);
        assert joined.equals("Forbidden Fragment×9") : joined;

        // Old bug: listing 8 then ellipsis — must not happen for same item.
        assert !joined.contains("…") : joined;
        assert !joined.contains("Forbidden Fragment, Forbidden Fragment") : joined;

        // Mixed: 2 oak + 1 stick
        String mixed = RecipeIoSummary.joinNamedCounts(
                List.of("Oak Planks", "Oak Planks", "Stick"), null);
        assert mixed.equals("Oak Planks×2, Stick") : mixed;

        // Stack counts multiply
        String stacked = RecipeIoSummary.joinNamedCounts(
                List.of("Iron Ingot", "Iron Ingot"), List.of(4, 2));
        assert stacked.equals("Iron Ingot×6") : stacked;

        // Empty item outputs + extra must keep the label (summon / gas).
        String extraOnly = RecipeIoSummary.joinOutputSide(
                List.of(),
                List.of(),
                List.of(new RecipeExtra("Summoned Foo", 0, 0, "")));
        assert extraOnly.contains("Foo") : extraOnly;
        assert extraOnly.contains("Summoned Foo") : extraOnly;
        String punctOut = RecipeIoSummary.joinOutputSide(
                List.of(),
                List.of(),
                List.of(new RecipeExtra("???", 1, 0, "", "mod:punct_mob")));
        assert punctOut.contains("???") : punctOut;
        assert punctOut.contains("mod:punct_mob") : punctOut;
        String catalog = extraOnly.isEmpty() ? "" : "→ " + extraOnly;
        assert catalog.contains("→") : catalog;
        assert catalog.contains("Foo") : catalog;

        String fluids = RecipeIoSummary.joinOutputSide(
                List.of(), List.of("Steam"), List.of());
        assert fluids.contains("Steam") : fluids;

        String withId = RecipeIoSummary.joinExtraLabels(
                List.of(new RecipeExtra("Foo", 0, 0, "", "mod:foo_entity")));
        assert withId.contains("mod:foo_entity") : withId;
        String displayAsId = RecipeIoSummary.joinExtraLabels(
                List.of(new RecipeExtra("Foo", 0, 0, "", "Foo Entity")));
        assert !displayAsId.contains("Foo Entity") || displayAsId.equals("Foo") : displayAsId;
        assert !RecipeIoSummary.looksLikeResourceId("Foo Entity");
        assert !RecipeIoSummary.looksLikeResourceId("minecraft:zombie extra");
        assert RecipeIoSummary.looksLikeResourceId("mod:foo_entity");

        System.out.println("RecipeIoSummaryCheck OK");
    }
}
