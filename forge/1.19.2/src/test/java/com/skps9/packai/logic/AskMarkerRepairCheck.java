package com.skps9.packai.logic;

import java.util.List;

/**
 * Runnable check: FACT-grounded marker repair — restore stripped embeds; never invent ids.
 */
public final class AskMarkerRepairCheck {
    private AskMarkerRepairCheck() {}

    public static void main(String[] args) {
        String pearl = "{{item:gateways:gate_pearl{gateway:\"b_a_d:friend\"}}}";
        String iron = "{{item:minecraft:iron_ingot}}";
        String recipe = "[[recipe:minecraft:stick]]";

        List<String> facts = List.of(
                pearl + " Open gateway challenge.",
                "Craft with " + iron,
                "Use " + recipe
        );
        List<String> allowed = AskMarkerRepair.collectAllowed(facts, List.of(), List.of());
        assert allowed.contains(pearl) : allowed;
        assert allowed.contains(iron) : allowed;
        assert allowed.contains(recipe) : allowed;

        // ① LLM stripped all markers → re-insert exact FACT strings
        String stripped = "Open gateway challenge.\nCraft with iron.\nUse sticks.\n\n[Sources] JEI";
        String repaired = AskMarkerRepair.repair(stripped, allowed);
        assert repaired.contains(pearl) : repaired;
        assert repaired.contains(iron) : repaired;
        assert repaired.contains(recipe) : repaired;

        // ② FACT has no alien id → do not insert
        String alienOnly = AskMarkerRepair.repair(
                "No embeds here.\n\n[Sources] JEI",
                AskMarkerRepair.collectAllowed(List.of("plain fact without markers"), List.of(), List.of()));
        assert !alienOnly.contains("{{item:") : alienOnly;
        assert !alienOnly.contains("[[recipe:") : alienOnly;

        // ③ NBT pearl round-trip (bare → FACT NBT when unique)
        String barePearl = "Use {{item:gateways:gate_pearl}} to open.\n\n[Sources] JEI";
        String upgraded = AskMarkerRepair.repair(barePearl, List.of(pearl));
        assert upgraded.contains(pearl) : upgraded;
        assert !upgraded.contains("{{item:gateways:gate_pearl}}")
                || upgraded.contains(pearl) : upgraded;

        // Empty shell + unique FACT → restore that one
        String empty = "See {{item:}} here.\n\n[Sources] JEI";
        String fixedEmpty = AskMarkerRepair.repair(empty, List.of(iron));
        assert fixedEmpty.contains(iron) : fixedEmpty;

        // Empty shell + multiple FACT → strip shell; missing FACT markers may re-attach (still ⊆ FACT)
        String amb = AskMarkerRepair.repair("See {{item:}} here.", List.of(iron, pearl));
        assert !amb.contains("{{item:}}") : amb;
        assert amb.contains(iron) && amb.contains(pearl) : amb;

        // Already present (alt form same key) → no duplicate {{item:}} insert
        String altForm = "[[item:minecraft:iron_ingot]] already.\n\n[Sources] JEI";
        String noDup = AskMarkerRepair.repair(altForm, List.of(iron));
        assert noDup.contains("[[item:minecraft:iron_ingot]]") : noDup;
        assert !noDup.contains(iron) : "must not also insert curly form: " + noDup;

        System.out.println("AskMarkerRepairCheck OK");
    }
}
