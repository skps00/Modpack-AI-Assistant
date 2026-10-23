package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.client.routes.RouteStoreCodec;

/** Headless checks for {@link RouteSummary}. */
public final class RouteSummaryCheck {
    private RouteSummaryCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static void eq(Object want, Object got, String what) {
        if (want == null ? got != null : !want.equals(got)) {
            fail(what + ": want=" + want + " got=" + got);
        }
    }

    private static RouteStoreCodec.InputDto dto(String id, int count) {
        return new RouteStoreCodec.InputDto(id, count);
    }

    private static RouteStoreCodec.StepDto step(
            String typeId,
            String actionLabel,
            List<RouteStoreCodec.InputDto> inputItems,
            List<String> inputs) {
        return new RouteStoreCodec.StepDto(
                "packai:r",
                typeId,
                "packai:out",
                actionLabel,
                inputs,
                List.of(),
                List.of(),
                inputItems);
    }

    private static void caseMergeInputItems() {
        List<RouteStoreCodec.StepDto> steps = List.of(
                step("t1", "M1", List.of(dto("a", 2), dto("b", 1)), List.of()),
                step("t2", "M2", List.of(dto("a", 3)), List.of()));
        RouteSummary.Data data = RouteSummary.of(steps);
        if (data.empty()) {
            fail("merge: should not be empty");
        }
        int a = 0;
        int b = 0;
        for (RouteSummary.Material m : data.materials()) {
            if ("a".equals(m.itemId())) {
                a = m.count();
            } else if ("b".equals(m.itemId())) {
                b = m.count();
            }
        }
        eq(5, a, "material a");
        eq(1, b, "material b");
        eq(2, data.totals().distinctMaterials(), "distinctMaterials");
        eq(6, data.totals().totalItems(), "totalItems");
    }

    private static void caseLegacyInputsFallback() {
        RouteStoreCodec.StepDto legacy = step("t", null, List.of(), List.of("x", "x", "y"));
        RouteSummary.Data data = RouteSummary.of(List.of(legacy));
        int x = 0;
        int y = 0;
        for (RouteSummary.Material m : data.materials()) {
            if ("x".equals(m.itemId())) {
                x = m.count();
            } else if ("y".equals(m.itemId())) {
                y = m.count();
            }
        }
        eq(2, x, "legacy x");
        eq(1, y, "legacy y");
    }

    private static void caseMachineAgg() {
        List<RouteStoreCodec.StepDto> steps = List.of(
                step("same", "Craft", List.of(), List.of()),
                step("same", "Craft", List.of(), List.of()),
                step("same", "Craft", List.of(), List.of()));
        RouteSummary.Data data = RouteSummary.of(steps);
        eq(1, data.machines().size(), "machines list size");
        eq(3, data.machines().get(0).steps(), "machine steps");
        eq(1, data.totals().machines(), "totals.machines");
    }

    private static void caseEmpty() {
        RouteSummary.Data data = RouteSummary.of(List.of());
        if (!data.empty()) {
            fail("empty flag");
        }
        eq(0, data.totals().distinctMaterials(), "distinct");
        eq(0, data.totals().totalItems(), "totalItems");
        eq(0, data.totals().machines(), "machines");
    }

    public static void main(String[] args) {
        caseMergeInputItems();
        caseLegacyInputsFallback();
        caseMachineAgg();
        caseEmpty();
        System.out.println("check RouteSummaryCheck OK");
    }
}
