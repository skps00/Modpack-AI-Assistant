package com.skps9.packai.logic;

import java.util.List;
import java.util.Set;

/** Runnable check: partial-pack web policy keys off held-item local touches. */
public final class PartialPackPolicyCheck {
    private PartialPackPolicyCheck() {}

    public static void main(String[] args) {
        PackIndex.RetrieveResult empty = new PackIndex.RetrieveResult(
                List.of(), List.of(), 0, false, Set.of(), List.of());
        assert !AskEngine.isHeldLocallyTouched("minecraft:stick", empty, List.of());

        PackIndex.RetrieveResult removed = new PackIndex.RetrieveResult(
                List.of(), List.of(), 0, false, Set.of("minecraft:stick"), List.of());
        assert AskEngine.isHeldLocallyTouched("minecraft:stick", removed, List.of());

        PackIndex.RetrieveResult recipe = new PackIndex.RetrieveResult(
                List.of(), List.of(), 0, false, Set.of(),
                List.of("item:minecraft:stick -[recipe_needs]-> item:minecraft:oak_planks"));
        assert AskEngine.isHeldLocallyTouched("minecraft:stick", recipe, List.of());

        assert AskEngine.isHeldLocallyTouched(
                "minecraft:stick", empty, List.of("【本地獲取】", "腳本配方需要：oak planks"));

        // Unrelated snippet should not mark stick as touched by itself — only if snippet contains id
        PackIndex.RetrieveResult otherSnip = new PackIndex.RetrieveResult(
                List.of("// file: kubejs/x.js\nevent.remove({id:'create:foo'})"),
                List.of("kubejs/x.js"), 5, false, Set.of(), List.of());
        assert !AskEngine.isHeldLocallyTouched("minecraft:stick", otherSnip, List.of());

        System.out.println("PartialPackPolicyCheck OK");
    }
}
