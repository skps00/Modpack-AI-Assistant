package com.skps9.packai.logic;

import java.util.List;
import java.util.Map;

import com.skps9.packai.client.gui.RouteMaterials;
import com.skps9.packai.client.routes.RouteStoreCodec;

/** Harness for {@link RouteMaterials#inputCounts}. */
public final class RouteMaterialCountsCheck {
    private RouteMaterialCountsCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static void eq(int want, int got, String what) {
        if (want != got) {
            fail(what + ": want=" + want + " got=" + got);
        }
    }

    private static RouteStoreCodec.InputDto dto(String id, int count) {
        return new RouteStoreCodec.InputDto(id, count);
    }

    private static RouteStoreCodec.StepDto step(
            List<String> inputs, List<RouteStoreCodec.InputDto> inputItems) {
        return new RouteStoreCodec.StepDto(
                "packai:test", "minecraft:crafting", "packai:out", "Crafting",
                inputs, List.of(), List.of(), inputItems);
    }

    private static void caseInputItemsCountNotSlotTally() {
        Map<String, Integer> counts = RouteMaterials.inputCounts(
                step(List.of("a", "a"), List.of(dto("a", 6))));
        eq(6, counts.get("a"), "inputItems count wins over inputs slot tally");
    }

    private static void caseInputsOnlyFallback() {
        Map<String, Integer> counts = RouteMaterials.inputCounts(step(List.of("b"), List.of()));
        eq(1, counts.get("b"), "inputs-only fallback");
    }

    private static void caseZeroCountFloorsToOne() {
        Map<String, Integer> counts = RouteMaterials.inputCounts(
                step(List.of("c"), List.of(dto("c", 0))));
        eq(1, counts.get("c"), "zero count floors to 1");
    }

    private static void caseUnionInputItemsAndInputs() {
        Map<String, Integer> counts = RouteMaterials.inputCounts(
                step(List.of("d"), List.of(dto("d", 2), dto("e", 3))));
        eq(2, counts.get("d"), "d from inputItems");
        eq(3, counts.get("e"), "e from inputItems only");
    }

    private static void caseNullStep() {
        Map<String, Integer> counts = RouteMaterials.inputCounts(null);
        if (!counts.isEmpty()) {
            fail("null step: want empty map got=" + counts);
        }
    }

    public static void main(String[] args) {
        caseInputItemsCountNotSlotTally();
        caseInputsOnlyFallback();
        caseZeroCountFloorsToOne();
        caseUnionInputItemsAndInputs();
        caseNullStep();
        System.out.println("check RouteMaterialCountsCheck OK");
    }
}
