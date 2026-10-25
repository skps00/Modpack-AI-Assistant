package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.client.gui.RouteTreeScreen;
import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;
import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Headless check for route-tree display-limit truncation metadata. */
public final class RouteTreeTruncationCheck {
    private RouteTreeTruncationCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        // T1: wide tree — hits MAX_NODES, not stack depth
        List<StepDto> wideSteps = new ArrayList<>(5001);
        List<String> rootInputs = new ArrayList<>(5000);
        for (int i = 0; i < 5000; i++) {
            rootInputs.add("packai:i" + i);
        }
        wideSteps.add(new StepDto(
                null,
                null,
                "packai:root",
                null,
                rootInputs,
                List.of(),
                List.of(),
                List.of()));
        for (int i = 0; i < 5000; i++) {
            wideSteps.add(new StepDto(
                    null,
                    null,
                    "packai:i" + i,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()));
        }
        RouteTreeData.Tree wide = RouteTreeBuilder.build("packai:root", wideSteps);
        if (wide.nodes().size() != RouteTreeData.MAX_NODES) {
            fail("T1 nodes.size want=" + RouteTreeData.MAX_NODES + " got=" + wide.nodes().size());
        }
        if (!wide.truncated()) {
            fail("T1 truncated should be true");
        }
        if (wide.omitted() <= 0) {
            fail("T1 omitted want>0 got=" + wide.omitted());
        }

        // T2: deep chain — depth cap, must not StackOverflowError
        List<StepDto> deepSteps = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            String output = "packai:t" + i;
            String next = "packai:t" + (i + 1);
            deepSteps.add(new StepDto(
                    null,
                    null,
                    output,
                    null,
                    List.of(next),
                    List.of(),
                    List.of(),
                    List.of()));
        }
        RouteTreeData.Tree deep;
        try {
            deep = RouteTreeBuilder.build("packai:t0", deepSteps);
        } catch (StackOverflowError e) {
            fail("T2 StackOverflowError on 200-step chain");
            return;
        }
        if (!deep.truncated()) {
            fail("T2 truncated should be true");
        }
        if (deep.omitted() <= 0) {
            fail("T2 omitted want>0 got=" + deep.omitted());
        }
        if (deep.nodes().size() >= 200) {
            fail("T2 nodes.size want<200 got=" + deep.nodes().size());
        }

        // T3: single step — no truncation
        StepDto one = new StepDto(
                null,
                null,
                "packai:only",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of());
        RouteTreeData.Tree small = RouteTreeBuilder.build("packai:only", List.of(one));
        if (small.truncated()) {
            fail("T3 single-step tree should not be truncated");
        }
        if (small.omitted() != 0) {
            fail("T3 single-step omitted want=0 got=" + small.omitted());
        }

        // T4: autotest force-truncated flag must not leak past screen close (negative control: drop goBack reset → T4a fails)
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RouteTreeScreen.autotestSetForceTruncated(true);
        if (!RouteTreeScreen.autotestIsForceTruncated()) {
            fail("T4a setup force flag should be true");
        }
        SavedRoute pressRoute = mechanicalPressFixture();
        new RouteTreeScreen(pressRoute, null).onClose();
        if (RouteTreeScreen.autotestIsForceTruncated()) {
            fail("T4a force flag should reset after onClose");
        }

        RouteTreeData.Tree press = RouteTreeBuilder.build(pressRoute.itemId(), pressRoute.steps());
        if (press.nodes().size() != 6) {
            fail("T4b nodes.size want=6 got=" + press.nodes().size());
        }
        if (press.omitted() != 0) {
            fail("T4b omitted want=0 got=" + press.omitted());
        }
        if (press.truncated()) {
            fail("T4b truncated should be false for mechanical_press fixture");
        }

        System.out.println("check RouteTreeTruncationCheck OK");
        System.out.println("T1 nodes=" + wide.nodes().size() + " omitted=" + wide.omitted());
        System.out.println("T2 nodes=" + deep.nodes().size() + " omitted=" + deep.omitted());
        System.out.println("T4b nodes=" + press.nodes().size() + " omitted=" + press.omitted());
    }

    /** Same shape as create:mechanical_press (3 steps) → nodes=6 omitted=0 in RouteTreeBuilder. */
    private static SavedRoute mechanicalPressFixture() {
        List<StepDto> steps = List.of(
                new StepDto(
                        null,
                        "minecraft:crafting",
                        "create:mechanical_press",
                        null,
                        List.of(
                                "create:shaft",
                                "create:andesite_casing",
                                "minecraft:smooth_stone_slab"),
                        List.of(),
                        List.of(),
                        List.of()),
                new StepDto(
                        null,
                        "minecraft:crafting",
                        "create:shaft",
                        null,
                        List.of("create:andesite_alloy"),
                        List.of(),
                        List.of(),
                        List.of()),
                new StepDto(
                        null,
                        "create:mixing",
                        "create:andesite_alloy",
                        null,
                        List.of("create:andesite_alloy_block"),
                        List.of(),
                        List.of(),
                        List.of()));
        return new SavedRoute("create:mechanical_press", "", true, "", steps);
    }
}
