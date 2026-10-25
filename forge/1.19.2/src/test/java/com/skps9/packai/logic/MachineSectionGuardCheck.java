package com.skps9.packai.logic;

import java.util.List;

/**
 * Headless check for {@link MachineSectionGuard} — unrelated machine sections dropped (work order AK).
 */
public final class MachineSectionGuardCheck {
    private static final String PIPE_FOCUS = "pipez:basic_pipe";
    private static final String ENCAPSULATOR = "industrialforegoing:fluid_encapsulator";

    private static final String UNRELATED_SECTION =
            "[Machine]\n"
                    + "JEI: Fluid Encapsulator\n"
                    + "e.g. Glass Bottle -> Honey Bottle; Depthrock Grade: not graded -> Mossy Cobblestone";

    private static final String ENCAPSULATOR_SECTION =
            "[Machine]\n"
                    + "JEI: Fluid Encapsulator\n"
                    + "e.g. Glass Bottle -> Honey Bottle";

    private MachineSectionGuardCheck() {}

    public static void main(String[] args) {
        checkUnrelatedPipeDropped();
        checkFocusMachineKept();
        checkNegativeControlGuardDisabled();
        System.out.println("MachineSectionGuardCheck OK");
    }

    private static void checkUnrelatedPipeDropped() {
        String out = MachineSectionGuard.filterMachineSection(
                UNRELATED_SECTION,
                PIPE_FOCUS,
                "how do I craft basic mechanical pipe?",
                "JEI: recipes for Basic Mechanical Pipe",
                List.of());
        assert out.isBlank() : "unrelated machine section must be empty, got: " + out;
    }

    private static void checkFocusMachineKept() {
        String out = MachineSectionGuard.filterMachineSection(
                ENCAPSULATOR_SECTION,
                ENCAPSULATOR,
                "what is this?",
                "",
                List.of());
        assert !out.isBlank() : "machine focus must keep [Machine] section";
        assert out.contains("Fluid Encapsulator") : out;
    }

    private static void checkNegativeControlGuardDisabled() {
        boolean prev = MachineSectionGuard.GUARD_ENABLED;
        try {
            MachineSectionGuard.GUARD_ENABLED = false;
            String out = MachineSectionGuard.filterMachineSection(
                    UNRELATED_SECTION, PIPE_FOCUS, "craft pipe", "", List.of());
            assert !out.isBlank()
                    : "with guard disabled unrelated section must pass (negative control)";
        } finally {
            MachineSectionGuard.GUARD_ENABLED = prev;
        }
    }
}
