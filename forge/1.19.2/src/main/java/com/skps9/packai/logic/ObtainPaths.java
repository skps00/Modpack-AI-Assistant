package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;

/**
 * DFS obtain paths over {@link ObtainGraph}. Cycle detection uses a path stack only
 * (no global visited). Caps: {@link #MAX_DEPTH}=6, {@link #MAX_BRANCHES_PER_ITEM}=3.
 *
 * Depth semantics: {@code steps.size()} never exceeds {@link #MAX_DEPTH}. When the
 * current node still has edges and {@code depth >= MAX_DEPTH}, emit an incomplete
 * path without adding another step. A node with no edges is always complete.
 */
public final class ObtainPaths {
    public static final int MAX_DEPTH = 6;
    public static final int MAX_BRANCHES_PER_ITEM = 3;
    public static final int DEFAULT_MAX_PATHS = 2;
    /** Groups larger than this after tag expansion stay leaves (any_of text only). */
    public static final int MAX_EXPAND_GROUP = 8;

    public record Step(ObtainGraph.Edge edge, List<List<Item>> groups) {}

    public record Path(Item target, List<Step> steps, boolean complete) {}

    private ObtainPaths() {}

    public static List<Path> find(ObtainGraph graph, Item target) {
        return find(graph, target, DEFAULT_MAX_PATHS);
    }

    public static List<Path> find(ObtainGraph graph, Item target, int maxPaths) {
        if (graph == null || target == null || maxPaths <= 0) {
            return List.of();
        }
        if (graph.edgesFor(target).isEmpty()) {
            return List.of();
        }
        List<Path> found = new ArrayList<>(maxPaths);
        List<Item> stack = new ArrayList<>();
        stack.add(target);
        search(graph, target, target, 0, stack, new ArrayList<>(), found, maxPaths);
        return List.copyOf(found);
    }

    private static void search(
            ObtainGraph graph,
            Item target,
            Item current,
            int depth,
            List<Item> stack,
            List<Step> steps,
            List<Path> found,
            int maxPaths) {
        if (found.size() >= maxPaths) {
            return;
        }

        List<ObtainGraph.Edge> edges = graph.edgesFor(current);
        if (edges.isEmpty()) {
            // Leaf: complete even when depth == MAX_DEPTH.
            found.add(new Path(target, List.copyOf(steps), true));
            return;
        }
        if (depth >= MAX_DEPTH) {
            // Still has edges but depth budget exhausted — incomplete, no extra step.
            found.add(new Path(target, List.copyOf(steps), false));
            return;
        }

        int branches = Math.min(MAX_BRANCHES_PER_ITEM, edges.size());
        for (int i = 0; i < branches && found.size() < maxPaths; i++) {
            ObtainGraph.Edge edge = edges.get(i);
            List<Step> nextSteps = new ArrayList<>(steps.size() + 1);
            nextSteps.addAll(steps);
            nextSteps.add(new Step(edge, edge.inputGroups()));

            Item child = firstExpandable(graph, edge, stack);
            if (child == null) {
                found.add(new Path(target, List.copyOf(nextSteps), true));
                continue;
            }
            stack.add(child);
            search(graph, target, child, depth + 1, stack, nextSteps, found, maxPaths);
            stack.remove(stack.size() - 1);
        }
    }

    /** First input (per group scan) that still has edges and is not on the path stack. */
    private static Item firstExpandable(
            ObtainGraph graph, ObtainGraph.Edge edge, List<Item> stack) {
        for (List<Item> group : edge.inputGroups()) {
            if (group == null || group.size() > MAX_EXPAND_GROUP) {
                // Huge tag groups: keep step as leaf; any_of text stands.
                continue;
            }
            for (Item ing : group) {
                if (stack.contains(ing)) {
                    continue;
                }
                if (!graph.edgesFor(ing).isEmpty()) {
                    return ing;
                }
            }
        }
        return null;
    }
}
