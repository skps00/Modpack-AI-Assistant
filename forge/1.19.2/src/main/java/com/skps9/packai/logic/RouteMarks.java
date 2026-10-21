package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.world.item.Item;

/**
 * Pure route mark rows for GUI / item-manager bridges (no JEI). Uses first path only.
 */
public final class RouteMarks {
    public static final int MAX_MARKS = 12;

    public enum Role {
        PRODUCT,
        MACHINE,
        MATERIAL
    }

    public record Mark(
            int stepNo, Item item, Role role, String machineLabel, boolean incomplete) {}

    public record Result(List<Mark> marks, int truncatedCount, boolean incomplete) {}

    private RouteMarks() {}

    public static Result build(List<ObtainPaths.Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return new Result(List.of(), 0, false);
        }
        ObtainPaths.Path path = paths.get(0);
        boolean incomplete = !path.complete();
        List<ObtainPaths.Step> steps = path.steps();
        if (steps == null || steps.isEmpty()) {
            return new Result(List.of(), 0, incomplete);
        }

        List<Mark> full = new ArrayList<>();
        int n = steps.size();
        for (int rev = 0; rev < n; rev++) {
            int stepNo = rev + 1;
            ObtainPaths.Step step = steps.get(n - 1 - rev);
            ObtainGraph.Edge edge = step.edge();
            if (edge == null) {
                continue;
            }
            String machineLabel = null;
            if (edge.actionLabel() != null && !edge.actionLabel().isBlank()) {
                machineLabel = edge.actionLabel();
            }
            full.add(new Mark(stepNo, edge.output(), Role.PRODUCT, machineLabel, incomplete));

            List<List<Item>> groups = step.groups();
            if (groups == null) {
                groups = edge.inputGroups();
            }
            if (groups != null) {
                for (List<Item> group : groups) {
                    if (group == null || group.isEmpty()) {
                        continue;
                    }
                    full.add(new Mark(stepNo, group.get(0), Role.MATERIAL, null, incomplete));
                }
            }
        }

        return truncate(full, incomplete);
    }

    private static Result truncate(List<Mark> full, boolean incomplete) {
        if (full.size() <= MAX_MARKS) {
            return new Result(List.copyOf(full), 0, incomplete);
        }

        int maxStepNo = 0;
        for (Mark m : full) {
            if (m.stepNo() > maxStepNo) {
                maxStepNo = m.stepNo();
            }
        }

        Set<Integer> mandatory = new HashSet<>();
        for (int i = 0; i < full.size(); i++) {
            if (full.get(i).stepNo() == 1) {
                mandatory.add(i);
            }
        }
        int finalProductIdx = -1;
        for (int i = full.size() - 1; i >= 0; i--) {
            Mark m = full.get(i);
            if (m.role() == Role.PRODUCT && m.stepNo() == maxStepNo) {
                mandatory.add(i);
                finalProductIdx = i;
                break;
            }
        }

        Set<Integer> keep;
        if (mandatory.size() > MAX_MARKS) {
            keep = new HashSet<>();
            if (finalProductIdx >= 0) {
                keep.add(finalProductIdx);
            }
            for (int i = 0; i < full.size() && keep.size() < MAX_MARKS; i++) {
                if (full.get(i).stepNo() == 1) {
                    keep.add(i);
                }
            }
        } else {
            keep = new HashSet<>(mandatory);
            for (int i = 0; i < full.size() && keep.size() < MAX_MARKS; i++) {
                if (!keep.contains(i)) {
                    keep.add(i);
                }
            }
        }
        List<Integer> order = new ArrayList<>(keep);
        order.sort(Integer::compareTo);
        List<Mark> out = new ArrayList<>(order.size());
        for (int idx : order) {
            out.add(full.get(idx));
        }
        int truncatedCount = full.size() - out.size();
        return new Result(List.copyOf(out), truncatedCount, incomplete);
    }
}
