package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;

/**
 * Headless: duplicate output ids must be allocated to steps in route order (quota),
 * not "always the last matching step".
 *
 * Fixture (route order matters):
 *   step0 planks <- stick
 *   step1 stick  <- planks
 *   step2 planks <- planks_dust
 * The root planks must use step0 (child stick) and the nested planks must use step2
 * (child planks_dust). The old last-match scan gave both nodes step2.
 */
public final class RouteTreeDuplicateStepCheck {
    private RouteTreeDuplicateStepCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static StepDto step(String output, List<String> inputs) {
        return new StepDto(
                "packai:test/" + output,
                "minecraft:crafting",
                output,
                "Crafting Table",
                inputs,
                List.of(),
                List.of(),
                List.of());
    }

    private static RouteTreeData.Node childOf(
            List<RouteTreeData.Node> nodes, int parentIndex, String itemId) {
        for (RouteTreeData.Node n : nodes) {
            if (n.parentIndex() == parentIndex && itemId.equals(n.itemId())) {
                return n;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        // Root is a frame so the repeated id never becomes its own ancestor (that would be CYCLE).
        List<StepDto> steps = List.of(
                step("packai:frame", List.of("packai:planks", "packai:stick")),
                step("packai:planks", List.of("packai:log")),
                step("packai:stick", List.of("packai:planks")),
                step("packai:planks", List.of("packai:planks_dust")));

        RouteTreeData.Tree tree = RouteTreeBuilder.build("packai:frame", steps);
        List<RouteTreeData.Node> nodes = tree.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            RouteTreeData.Node n = nodes.get(i);
            System.out.println("  #" + i + " id=" + n.itemId() + " parent=" + n.parentIndex()
                    + " depth=" + n.depth() + " status=" + n.status() + " step=" + n.stepIndex());
        }

        List<RouteTreeData.Node> planks = new ArrayList<>();
        for (RouteTreeData.Node n : nodes) {
            if ("packai:planks".equals(n.itemId()) && n.status() == RouteTreeData.Status.OK) {
                planks.add(n);
            }
        }
        if (planks.size() != 2) {
            fail("expected 2 OK packai:planks nodes, got " + planks.size());
        }

        RouteTreeData.Node root = nodes.get(0);
        if (!"packai:frame".equals(root.itemId()) || root.stepIndex() != 0) {
            fail("root must be frame with stepIndex 0, got " + root);
        }
        RouteTreeData.Node firstPlanks = childOf(nodes, 0, "packai:planks");
        if (firstPlanks == null) {
            fail("frame must expand a planks child");
        }
        if (firstPlanks.stepIndex() != 1) {
            fail("first planks (frame child) must use step 1, got " + firstPlanks.stepIndex());
        }
        if (childOf(nodes, nodes.indexOf(firstPlanks), "packai:log") == null) {
            fail("first planks must expand step1 inputs (log)");
        }

        int stickIndex = nodes.indexOf(childOf(nodes, 0, "packai:stick"));
        RouteTreeData.Node nested = childOf(nodes, stickIndex, "packai:planks");
        if (nested == null) {
            fail("stick must expand to a nested planks node");
        }
        if (nested.stepIndex() != 3) {
            fail("nested planks must use step 3, got " + nested.stepIndex());
        }
        int nestedIndex = nodes.indexOf(nested);
        if (childOf(nodes, nestedIndex, "packai:planks_dust") == null) {
            fail("nested planks must expand step2 inputs (planks_dust)");
        }
        if (childOf(nodes, nestedIndex, "packai:log") != null) {
            fail("nested planks must not reuse step1 inputs (crossed steps)");
        }

        System.out.println("check RouteTreeDuplicateStepCheck OK");
    }
}
