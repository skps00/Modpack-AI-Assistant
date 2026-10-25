package com.skps9.packai.client.jei;

import java.util.List;

/** INPUT tag variants: one framed plank must not drop a real crafting recipe. */
public final class SpamFilterVariantCheck {
    private SpamFilterVariantCheck() {}

    public static void main(String[] args) {
        inputMixedFramedAndNormalNotSpam();
        inputAllFramedIsSpam();
        outputFramedIsSpam();
        negativeControlAnyInputVariant();
        System.out.println("SpamFilterVariantCheck OK");
    }

    private static void inputMixedFramedAndNormalNotSpam() {
        List<String> inputs = List.of(
                "framedblocks:framed_oak_planks",
                "minecraft:oak_planks");
        assert !JeiUniversalSpam.recipeInvolvesSpam(inputs, List.of("productivebees:bee_cage"), List.of())
                : "mixed INPUT must not be spam";
        System.out.println("inputMixedFramedAndNormalNotSpam OK");
    }

    private static void inputAllFramedIsSpam() {
        List<String> inputs = List.of(
                "framedblocks:framed_oak_planks",
                "framedblocks:framed_birch_planks");
        assert JeiUniversalSpam.recipeInvolvesSpam(inputs, List.of("minecraft:stick"), List.of())
                : "all framed INPUT must be spam";
        assert "IN:all".equals(JeiUniversalSpam.spamSkipRolesTag(inputs, List.of("minecraft:stick"), List.of()));
        System.out.println("inputAllFramedIsSpam OK");
    }

    private static void outputFramedIsSpam() {
        List<String> outputs = List.of("integrateddynamics:facade");
        assert JeiUniversalSpam.recipeInvolvesSpam(List.of("minecraft:dirt"), outputs, List.of())
                : "OUTPUT framed must be spam";
        assert "OUT".equals(JeiUniversalSpam.spamSkipRolesTag(List.of(), outputs, List.of()));
        System.out.println("outputFramedIsSpam OK");
    }

    private static void negativeControlAnyInputVariant() {
        List<String> inputs = List.of(
                "framedblocks:framed_oak_planks",
                "minecraft:oak_planks");
        assert JeiUniversalSpam.recipeInvolvesSpamAnyInputVariant(
                inputs, List.of("productivebees:bee_cage"), List.of())
                : "old any-variant INPUT rule would have dropped bee cage recipe";
        assert !JeiUniversalSpam.recipeInvolvesSpam(inputs, List.of("productivebees:bee_cage"), List.of());
        System.out.println("negativeControlAnyInputVariant OK");
    }
}
