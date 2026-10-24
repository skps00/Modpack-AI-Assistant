package com.skps9.packai.logic;

import java.util.List;

public final class RecipeCardEqualityCheck {
    private RecipeCardEqualityCheck() {}

    private static RecipeCard base(Object jeiLayout, List<SlotOptions> slotOpts) {
        return new RecipeCard(
                "Machine",
                RecipeCard.Layout.FLOW,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                jeiLayout,
                List.of(),
                RecipeCard.FocusRole.OUTPUT,
                List.of(),
                "",
                slotOpts);
    }

    public static void main(String[] args) {
        Object layoutA = new Object();
        Object layoutB = new Object();
        List<SlotOptions> opts = List.of(new SlotOptions("minecraft:logs", List.of(), 3));
        RecipeCard a = base(layoutA, null);
        RecipeCard b = base(layoutA, opts);
        assert a.equals(b) : "slotOptions must not affect equals";
        assert a.hashCode() == b.hashCode() : "slotOptions must not affect hashCode";

        RecipeCard c = base(layoutB, null);
        assert !a.equals(c) : "jeiLayout change must break equals";

        System.out.println("check RecipeCardEqualityCheck OK");
    }
}
