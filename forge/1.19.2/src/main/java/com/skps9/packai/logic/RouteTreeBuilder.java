package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.skps9.packai.client.routes.RouteStoreCodec.InputDto;
import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;

/** Rebuild route tree structure from persisted steps + input item counts. */
public final class RouteTreeBuilder {
    private static final int MAX_DEPTH = 64;

    private RouteTreeBuilder() {}

    public static RouteTreeData.Tree build(String rootId, List<StepDto> steps) {
        if (rootId == null || rootId.isBlank() || steps == null || steps.isEmpty()) {
            return new RouteTreeData.Tree(List.of());
        }
        List<RouteTreeData.Node> nodes = new ArrayList<>();
        int[] omitted = {0};
        visit(rootId, -1, 0, 1, steps, new HashSet<>(), nodes, omitted);
        return new RouteTreeData.Tree(List.copyOf(nodes), omitted[0] > 0, omitted[0]);
    }

    private static void visit(
            String id,
            int parentIndex,
            int depth,
            int count,
            List<StepDto> steps,
            Set<String> onPath,
            List<RouteTreeData.Node> nodes,
            int[] omitted) {
        if (nodes.size() >= RouteTreeData.MAX_NODES) {
            omitted[0]++;
            return;
        }
        if (depth > MAX_DEPTH) {
            omitted[0]++;
            return;
        }
        if (onPath.contains(id)) {
            nodes.add(new RouteTreeData.Node(
                    id, parentIndex, depth, count, RouteTreeData.Status.CYCLE, -1));
            return;
        }
        int stepIndex = findStepForOutput(steps, id);
        if (stepIndex < 0) {
            nodes.add(new RouteTreeData.Node(
                    id, parentIndex, depth, count, RouteTreeData.Status.NOT_IN_ROUTE, -1));
            return;
        }
        int myIndex = nodes.size();
        nodes.add(new RouteTreeData.Node(
                id, parentIndex, depth, count, RouteTreeData.Status.OK, stepIndex));
        StepDto step = steps.get(stepIndex);
        Map<String, Integer> childCounts = inputCountsById(step);
        Set<String> nextPath = new HashSet<>(onPath);
        nextPath.add(id);
        for (Map.Entry<String, Integer> e : childCounts.entrySet()) {
            if (nodes.size() >= RouteTreeData.MAX_NODES) {
                omitted[0]++;
                break;
            }
            visit(e.getKey(), myIndex, depth + 1, e.getValue(), steps, nextPath, nodes, omitted);
        }
    }

    private static Map<String, Integer> inputCountsById(StepDto step) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (step == null) {
            return counts;
        }
        if (step.inputItems() != null && !step.inputItems().isEmpty()) {
            for (InputDto in : step.inputItems()) {
                if (in == null || in.id() == null || in.id().isBlank()) {
                    continue;
                }
                int c = in.count() <= 0 ? 1 : in.count();
                counts.merge(in.id(), c, Integer::sum);
            }
            return counts;
        }
        if (step.inputs() != null) {
            for (String in : step.inputs()) {
                if (in != null && !in.isBlank()) {
                    counts.merge(in, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    static int findStepForOutput(List<StepDto> steps, String outputId) {
        if (steps == null || outputId == null) {
            return -1;
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            StepDto s = steps.get(i);
            if (s != null && outputId.equals(s.output())) {
                return i;
            }
        }
        return -1;
    }
}
