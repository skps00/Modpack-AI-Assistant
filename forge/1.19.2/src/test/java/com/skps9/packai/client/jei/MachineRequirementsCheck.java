package com.skps9.packai.client.jei;

import java.util.List;

/** Headless check for {@link MachineRequirements}. Run with -ea. */
public final class MachineRequirementsCheck {
    private MachineRequirementsCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        List<String> packing = MachineRequirements.requiredIdsFor("create:packing");
        if (!packing.equals(List.of("create:basin"))) {
            fail("create:packing expected [create:basin], got " + packing);
        }
        if (!MachineRequirements.requiredIdsFor("create:unknown_category").isEmpty()) {
            fail("unknown category should be empty");
        }
        if (!MachineRequirements.requiredIdsFor(null).isEmpty()) {
            fail("null category should be empty");
        }
        System.out.println("MachineRequirementsCheck OK");
    }
}
