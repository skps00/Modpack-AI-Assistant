package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Harness for {@link RouteMarks#build}.
 */
public final class RouteMarksCheck {
    private RouteMarksCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try {
            caseEmpty();
            caseReverseOrder();
            caseMachineLabelNull();
            caseMachineLabelPresent();
            caseTruncation();
            caseIncomplete();
            System.out.println("RouteMarksCheck OK");
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void caseEmpty() {
        RouteMarks.Result r = RouteMarks.build(List.of());
        if (!r.marks().isEmpty() || r.truncatedCount() != 0 || r.incomplete()) {
            fail("empty input: " + r);
        }
        RouteMarks.Result r2 = RouteMarks.build(null);
        if (!r2.marks().isEmpty()) {
            fail("null paths");
        }
    }

    /** Three-step chain: marks run raw-side step 1 → final product step 3. */
    private static void caseReverseOrder() {
        Item a = Items.DIAMOND_BLOCK;
        Item b = Items.DIAMOND;
        Item c = Items.DIAMOND_ORE;
        List<ObtainPaths.Step> steps = List.of(
                step(edge("packai:r0", a, b), List.of(List.of(b))),
                step(edge("packai:r1", b, c), List.of(List.of(c))),
                step(edge("packai:r2", c, Items.COAL), List.of(List.of(Items.COAL))));
        ObtainPaths.Path path = new ObtainPaths.Path(a, steps, true);
        RouteMarks.Result r = RouteMarks.build(List.of(path));
        List<RouteMarks.Mark> m = r.marks();
        if (m.size() != 6) {
            fail("expected 6 marks got " + m.size());
        }
        if (m.get(0).stepNo() != 1 || m.get(0).item() != c || m.get(0).role() != RouteMarks.Role.PRODUCT) {
            fail("first mark must be step1 product ore: " + m.get(0));
        }
        if (m.get(4).stepNo() != 3 || m.get(4).item() != a || m.get(4).role() != RouteMarks.Role.PRODUCT) {
            fail("last product mark must be step3 block: " + m.get(4));
        }
        for (int i = 0; i < m.size() - 1; i++) {
            if (m.get(i).stepNo() > m.get(i + 1).stepNo()) {
                fail("stepNo must not decrease: " + m);
            }
        }
    }

    private static void caseMachineLabelNull() {
        ObtainGraph.Edge edge = edge("packai:craft", Items.STICK, Items.OAK_PLANKS);
        if (edge.actionLabel() != null) {
            fail("craft edge must have null actionLabel");
        }
        ObtainPaths.Path path =
                new ObtainPaths.Path(Items.STICK, List.of(step(edge, List.of(List.of(Items.OAK_PLANKS)))), true);
        RouteMarks.Result r = RouteMarks.build(List.of(path));
        RouteMarks.Mark product = r.marks().get(0);
        if (product.machineLabel() != null) {
            fail("expected null machineLabel");
        }
    }

    private static void caseMachineLabelPresent() {
        ObtainGraph.Edge edge = new ObtainGraph.Edge(
                new ResourceLocation("packai:mac"),
                new ResourceLocation("packai", "machine"),
                Items.IRON_INGOT,
                1,
                List.of(List.of(Items.RAW_IRON)),
                "Arc Furnace",
                List.of(),
                false);
        ObtainPaths.Path path =
                new ObtainPaths.Path(Items.IRON_INGOT, List.of(step(edge, edge.inputGroups())), true);
        RouteMarks.Result r = RouteMarks.build(List.of(path));
        if (!"Arc Furnace".equals(r.marks().get(0).machineLabel())) {
            fail("machine label on product: " + r.marks().get(0));
        }
        for (RouteMarks.Mark mk : r.marks()) {
            if (mk.role() == RouteMarks.Role.MACHINE) {
                fail("must not emit MACHINE role rows");
            }
        }
    }

    private static void caseTruncation() {
        List<ObtainPaths.Step> steps = new ArrayList<>();
        Item[] chain = {
            Items.NETHERITE_BLOCK,
            Items.NETHERITE_INGOT,
            Items.NETHERITE_SCRAP,
            Items.ANCIENT_DEBRIS,
            Items.DIAMOND_BLOCK,
            Items.DIAMOND,
            Items.DIAMOND_ORE,
            Items.COAL_BLOCK,
            Items.COAL,
            Items.CHARCOAL
        };
        for (int i = 0; i < chain.length - 1; i++) {
            Item out = chain[i];
            Item in = chain[i + 1];
            List<List<Item>> groups = new ArrayList<>();
            groups.add(List.of(in));
            groups.add(List.of(Items.DIRT, Items.GRASS_BLOCK, Items.STONE));
            groups.add(List.of(Items.SAND, Items.GRAVEL, Items.COBBLESTONE));
            steps.add(step(edge("packai:t" + i, out, in), groups));
        }
        ObtainPaths.Path path = new ObtainPaths.Path(chain[0], List.copyOf(steps), true);
        RouteMarks.Result r = RouteMarks.build(List.of(path));
        if (r.marks().size() != RouteMarks.MAX_MARKS) {
            fail("truncated size=" + r.marks().size());
        }
        if (r.truncatedCount() <= 0) {
            fail("expected truncatedCount > 0");
        }
        boolean hasStep1 = false;
        boolean hasFinalProduct = false;
        int maxStep = 0;
        for (RouteMarks.Mark mk : r.marks()) {
            if (mk.stepNo() == 1) {
                hasStep1 = true;
            }
            if (mk.stepNo() > maxStep) {
                maxStep = mk.stepNo();
            }
        }
        for (RouteMarks.Mark mk : r.marks()) {
            if (mk.role() == RouteMarks.Role.PRODUCT && mk.stepNo() == maxStep && mk.item() == chain[0]) {
                hasFinalProduct = true;
            }
        }
        if (!hasStep1 || !hasFinalProduct) {
            fail("must keep step1 rows and final product: " + r.marks());
        }
    }

    private static void caseIncomplete() {
        ObtainPaths.Path path = new ObtainPaths.Path(
                Items.IRON_INGOT,
                List.of(step(edge("packai:inc", Items.IRON_INGOT, Items.RAW_IRON), List.of(List.of(Items.RAW_IRON)))),
                false);
        RouteMarks.Result r = RouteMarks.build(List.of(path));
        if (!r.incomplete()) {
            fail("result.incomplete");
        }
        for (RouteMarks.Mark mk : r.marks()) {
            if (!mk.incomplete()) {
                fail("mark.incomplete: " + mk);
            }
        }
    }

    private static ObtainPaths.Step step(ObtainGraph.Edge edge, List<List<Item>> groups) {
        return new ObtainPaths.Step(edge, groups);
    }

    private static ObtainGraph.Edge edge(String id, Item output, Item input) {
        return new ObtainGraph.Edge(
                new ResourceLocation(id),
                new ResourceLocation("minecraft", "crafting"),
                output,
                1,
                List.of(List.of(input)),
                null,
                List.of(),
                false);
    }
}
