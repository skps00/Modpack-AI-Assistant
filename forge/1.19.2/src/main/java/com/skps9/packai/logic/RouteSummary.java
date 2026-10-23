package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.skps9.packai.client.routes.RouteStoreCodec.InputDto;
import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;

/** Aggregated materials and machines for a saved obtain route (pure data, no Minecraft). */
public final class RouteSummary {
    private RouteSummary() {}

    public record Material(String itemId, int count) {}

    public record Machine(String typeId, String label, int steps) {}

    public record Totals(int distinctMaterials, int totalItems, int machines) {}

    public record Data(List<Material> materials, List<Machine> machines, Totals totals, boolean empty) {}

    public static Data of(List<StepDto> steps) {
        if (steps == null || steps.isEmpty()) {
            return new Data(List.of(), List.of(), new Totals(0, 0, 0), true);
        }
        Map<String, Integer> materialCounts = new LinkedHashMap<>();
        Map<String, MachineAgg> machineAggs = new LinkedHashMap<>();
        for (StepDto step : steps) {
            if (step == null) {
                continue;
            }
            mergeMaterials(step, materialCounts);
            mergeMachine(step, machineAggs);
        }
        List<Material> materials = new ArrayList<>();
        int totalItems = 0;
        for (Map.Entry<String, Integer> e : materialCounts.entrySet()) {
            int c = e.getValue();
            materials.add(new Material(e.getKey(), c));
            totalItems += c;
        }
        List<Machine> machines = new ArrayList<>();
        for (MachineAgg agg : machineAggs.values()) {
            machines.add(new Machine(agg.typeId, agg.label, agg.steps));
        }
        Totals totals = new Totals(materials.size(), totalItems, machines.size());
        return new Data(materials, machines, totals, false);
    }

    private static void mergeMaterials(StepDto step, Map<String, Integer> out) {
        List<InputDto> items = step.inputItems();
        if (items != null && !items.isEmpty()) {
            for (InputDto in : items) {
                if (in == null || in.id() == null || in.id().isBlank()) {
                    continue;
                }
                int c = in.count() <= 0 ? 1 : in.count();
                out.merge(in.id(), c, Integer::sum);
            }
            return;
        }
        List<String> inputs = step.inputs();
        if (inputs == null) {
            return;
        }
        for (String in : inputs) {
            if (in != null && !in.isBlank()) {
                out.merge(in, 1, Integer::sum);
            }
        }
    }

    private static void mergeMachine(StepDto step, Map<String, MachineAgg> out) {
        String typeId = step.typeId() != null ? step.typeId() : "";
        String actionLabel = step.actionLabel();
        String keyLabel = actionLabel != null ? actionLabel : typeId;
        String key = typeId + "\0" + keyLabel;
        MachineAgg agg = out.get(key);
        if (agg == null) {
            String display = actionLabel != null && !actionLabel.isBlank() ? actionLabel : typeId;
            agg = new MachineAgg(typeId, display);
            out.put(key, agg);
        }
        agg.steps++;
    }

    private static final class MachineAgg {
        final String typeId;
        final String label;
        int steps;

        MachineAgg(String typeId, String label) {
            this.typeId = typeId;
            this.label = label;
        }
    }
}
