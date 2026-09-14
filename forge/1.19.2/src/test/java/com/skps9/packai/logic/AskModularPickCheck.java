package com.skps9.packai.logic;

import java.util.List;
import java.util.function.Predicate;

import com.skps9.packai.client.service.AskService;

/**
 * Headless: modular-tool extra filter (no PackAiConfig / ModularToolScan / ItemStack).
 * Run with -ea.
 */
public final class AskModularPickCheck {
    private AskModularPickCheck() {}

    public static void main(String[] args) {
        Predicate<String> tetra = ToolBuildFacts::looksLikeTetraModularItem;
        List<String> extras = List.of(
                "tetra:modular_sword",
                "minecraft:stick",
                "tetra:modular_double");

        List<String> kept = AskService.filterModularExtras(true, true, extras, tetra);
        assert kept.equals(List.of("minecraft:stick")) : kept;
        assert extras.size() - kept.size() == 2 : "dropped=" + (extras.size() - kept.size());
        assert kept.size() == 1 : "kept=" + kept.size();

        List<String> nonFocus = AskService.filterModularExtras(true, false, extras, tetra);
        assert nonFocus == extras : "non-modular focus must keep extras as-is";

        List<String> disabled = AskService.filterModularExtras(false, true, extras, tetra);
        assert disabled == extras : "enable=false must keep extras as-is";

        List<String> empty = List.of();
        List<String> emptyOut = AskService.filterModularExtras(true, true, empty, tetra);
        assert emptyOut == empty : "empty extras must return same list";
        List<String> nullOut = AskService.filterModularExtras(true, true, null, tetra);
        assert nullOut != null && nullOut.isEmpty() : "null extras must not throw";

        System.out.println("AskModularPickCheck OK");
    }
}
