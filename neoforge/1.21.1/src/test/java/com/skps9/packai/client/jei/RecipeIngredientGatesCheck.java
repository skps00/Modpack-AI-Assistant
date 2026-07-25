package com.skps9.packai.client.jei;

import java.util.List;

/**
 * Self-check: formatRequest mirrors SlashBlade RequestDefinition-style accessors.
 * Run: gradle test --tests RecipeIngredientGatesCheck (or main).
 */
public final class RecipeIngredientGatesCheck {
    private RecipeIngredientGatesCheck() {}

    /** Stand-in for mods.flammpfeil.slashblade.recipe.RequestDefinition. */
    public record FakeRequest(
            String name,
            int proudSoulCount,
            int killCount,
            int refineCount,
            List<String> defaultType
    ) {}

    public static void main(String[] args) {
        // amazing_shine.json: request { refine: 100, sword_type: [broken] }
        FakeRequest shine = new FakeRequest("slashblade:none", 0, 0, 100, List.of("broken"));
        List<String> gates = RecipeIngredientGates.formatRequest(shine);
        expect(gates.contains("refine≥100"), "refine gate: " + gates);
        expect(gates.contains("broken"), "broken type: " + gates);
        expect(!gates.stream().anyMatch(s -> s.startsWith("kill")), "no fake kill: " + gates);

        FakeRequest named = new FakeRequest(
                "slashblade_addon:fluorescent_bar", 0, 0, 100, List.of("BROKEN"));
        List<String> g2 = RecipeIngredientGates.formatRequest(named);
        expect(g2.contains("blade:fluorescent_bar"), "blade name: " + g2);
        expect(g2.contains("refine≥100"), "refine: " + g2);

        FakeRequest full = new FakeRequest("slashblade:none", 5000, 1000, 50, List.of());
        List<String> g3 = RecipeIngredientGates.formatRequest(full);
        expect(g3.equals(List.of("kill≥1000", "proud_soul≥5000", "refine≥50")), "full: " + g3);

        System.out.println("ok RecipeIngredientGatesCheck");
    }

    private static void expect(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError(msg);
        }
    }
}
