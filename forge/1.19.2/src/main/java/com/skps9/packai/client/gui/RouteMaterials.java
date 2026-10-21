package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import com.skps9.packai.client.routes.RouteStoreCodec.FluidDto;
import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;
import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;
import com.skps9.packai.logic.FluidNames;
import com.skps9.packai.logic.MachineLabels;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/** Layer-merged material totals for a saved obtain route (pure computation). */
public final class RouteMaterials {
    private RouteMaterials() {}

    /** Null when machine should not be shown (blank or lang "unknown" placeholder). */
    @Nullable
    public static String displayMachineLabel(@Nullable String actionLabel) {
        return displayMachineLabel(actionLabel, null);
    }

    @Nullable
    public static String displayMachineLabel(@Nullable String actionLabel, @Nullable String typeId) {
        if (actionLabel != null && !actionLabel.isBlank() && isUnknownMachinePlaceholder(actionLabel)) {
            actionLabel = null;
        }
        ResourceLocation typeLoc =
                typeId != null && !typeId.isBlank() ? ResourceLocation.tryParse(typeId) : null;
        return localizePackaiMachineKey(MachineLabels.resolve(actionLabel, typeLoc, ReplyLang.current()));
    }

    /** Turn stored {@code packai.*} lang keys into localized UI text. */
    @Nullable
    private static String localizePackaiMachineKey(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if (raw.startsWith("packai.")) {
            try {
                return Component.translatable(raw).getString();
            } catch (Throwable ignored) {
                return raw;
            }
        }
        return raw;
    }

    private static boolean isUnknownMachinePlaceholder(String label) {
        return label.equals(Component.translatable("packai.route_tree.machine_unknown").getString())
                || label.equals(Component.translatable("packai.route_library.machine.unknown").getString());
    }

    public record Row(
            String id,
            int total,
            int have,
            int missing,
            int depth,
            @Nullable String machineLabelOrNull) {}

    public record FluidRow(String fluidId, int totalMb, int depth) {}

    public static List<FluidRow> computeFluids(SavedRoute route) {
        if (route == null || route.steps() == null || route.steps().isEmpty()) {
            return List.of();
        }
        List<StepDto> steps = route.steps();
        int n = steps.size();
        StepDto firstLine = steps.get(n - 1);
        String rootId = firstLine != null && firstLine.output() != null && !firstLine.output().isBlank()
                ? firstLine.output()
                : route.itemId();
        if (rootId == null || rootId.isBlank()) {
            return List.of();
        }
        Map<String, FluidAcc> acc = new HashMap<>();
        walkFluids(rootId, 1, 0, steps, new HashSet<>(), acc);
        List<FluidRow> out = new ArrayList<>();
        for (FluidAcc a : acc.values()) {
            out.add(new FluidRow(a.fluidId, a.totalMb, a.depth));
        }
        out.sort(Comparator.comparingInt(FluidRow::depth).thenComparing(FluidRow::fluidId));
        return List.copyOf(out);
    }

    @Nullable
    public static ItemStack bucketStackForFluidId(String fluidId) {
        Item bucket = bucketItemForFluidId(fluidId);
        if (bucket == null || bucket == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(bucket);
    }

    public static String displayFluidName(String fluidId) {
        return FluidNames.display(fluidId);
    }

    @Nullable
    private static Item bucketItemForFluidId(String fluidId) {
        ResourceLocation loc = ResourceLocation.tryParse(fluidId);
        if (loc == null) {
            return null;
        }
        Fluid fluid = Registry.FLUID.get(loc);
        if (fluid == null) {
            return null;
        }
        Item bucket = fluid.getBucket();
        return bucket == null || bucket == Items.AIR ? null : bucket;
    }

    public static List<Row> compute(SavedRoute route, ToIntFunction<String> haveById) {
        if (route == null || route.steps() == null || route.steps().isEmpty()) {
            return List.of();
        }
        List<StepDto> steps = route.steps();
        int n = steps.size();
        StepDto firstLine = steps.get(n - 1);
        String rootId = firstLine != null && firstLine.output() != null && !firstLine.output().isBlank()
                ? firstLine.output()
                : route.itemId();
        if (rootId == null || rootId.isBlank()) {
            return List.of();
        }
        Map<String, Acc> acc = new HashMap<>();
        walk(rootId, 1, 0, steps, new HashSet<>(), acc);
        List<Row> out = new ArrayList<>();
        for (Acc a : acc.values()) {
            int have = Math.max(0, haveById.applyAsInt(a.id));
            int missing = Math.max(0, a.total - have);
            out.add(new Row(a.id, a.total, have, missing, a.depth, a.machineLabel));
        }
        out.sort(Comparator.comparingInt(Row::depth).thenComparing(Row::id));
        return List.copyOf(out);
    }

    public static ToIntFunction<String> playerInventoryCounts() {
        return id -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || id == null || id.isBlank()) {
                return 0;
            }
            Item want = RouteBookmarkInject.itemFromId(id);
            if (want == null) {
                return 0;
            }
            ResourceLocation wantKey = Registry.ITEM.getKey(want);
            if (wantKey == null) {
                return 0;
            }
            int sum = 0;
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
                if (wantKey.equals(key)) {
                    sum += stack.getCount();
                }
            }
            return sum;
        };
    }

    private static final class Acc {
        final String id;
        int total;
        int depth;
        @Nullable
        String machineLabel;

        Acc(String id, int depth) {
            this.id = id;
            this.depth = depth;
        }
    }

    private static final class FluidAcc {
        final String fluidId;
        int totalMb;
        int depth;

        FluidAcc(String fluidId, int depth) {
            this.fluidId = fluidId;
            this.depth = depth;
        }
    }

    private static void walkFluids(
            String id,
            int need,
            int depth,
            List<StepDto> steps,
            Set<String> onPath,
            Map<String, FluidAcc> acc) {
        if (id == null || id.isBlank() || need <= 0) {
            return;
        }
        int stepIdx = findStepForOutput(steps, id);
        if (stepIdx < 0) {
            return;
        }
        if (onPath.contains(id)) {
            return;
        }
        StepDto step = steps.get(stepIdx);
        accumulateStepFluids(step, need, depth, acc);
        Map<String, Integer> counts = inputCounts(step);
        Set<String> nextPath = new HashSet<>(onPath);
        nextPath.add(id);
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            int perCraft = e.getValue();
            if (perCraft <= 0) {
                continue;
            }
            String inId = e.getKey();
            int childNeed = need * perCraft;
            int childStep = findStepForOutput(steps, inId);
            if (childStep >= 0) {
                walkFluids(inId, childNeed, depth + 1, steps, nextPath, acc);
            }
        }
    }

    private static void accumulateStepFluids(
            @Nullable StepDto step, int need, int depth, Map<String, FluidAcc> acc) {
        if (step == null || step.fluids() == null || need <= 0) {
            return;
        }
        for (FluidDto f : step.fluids()) {
            if (f == null || f.fluidId() == null || f.fluidId().isBlank() || f.amountMb() <= 0) {
                continue;
            }
            long add = (long) f.amountMb() * need;
            int mb = add > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) add;
            FluidAcc row = acc.computeIfAbsent(f.fluidId(), k -> new FluidAcc(k, depth));
            row.totalMb += mb;
            row.depth = Math.min(row.depth, depth);
        }
    }

    private static void walk(
            String id,
            int need,
            int depth,
            List<StepDto> steps,
            Set<String> onPath,
            Map<String, Acc> acc) {
        if (id == null || id.isBlank() || need <= 0) {
            return;
        }
        int stepIdx = findStepForOutput(steps, id);
        if (stepIdx < 0) {
            Acc row = acc.computeIfAbsent(id, k -> new Acc(k, depth));
            row.total += need;
            row.depth = Math.min(row.depth, depth);
            return;
        }
        if (onPath.contains(id)) {
            return;
        }
        StepDto step = steps.get(stepIdx);
        Map<String, Integer> counts = inputCounts(step);
        Set<String> nextPath = new HashSet<>(onPath);
        nextPath.add(id);
        String machine = step != null ? displayMachineLabel(step.actionLabel(), step.typeId()) : null;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            int perCraft = e.getValue();
            if (perCraft <= 0) {
                continue;
            }
            String inId = e.getKey();
            int childNeed = need * perCraft;
            int childStep = findStepForOutput(steps, inId);
            if (childStep < 0) {
                Acc row = acc.computeIfAbsent(inId, k -> new Acc(k, depth + 1));
                row.total += childNeed;
                row.depth = Math.min(row.depth, depth + 1);
                if (row.machineLabel == null && machine != null) {
                    row.machineLabel = machine;
                }
            } else {
                walk(inId, childNeed, depth + 1, steps, nextPath, acc);
            }
        }
    }

    private static Map<String, Integer> inputCounts(StepDto step) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (step != null && step.inputs() != null) {
            for (String in : step.inputs()) {
                if (in != null && !in.isBlank()) {
                    counts.merge(in, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static int findStepForOutput(List<StepDto> steps, String outputId) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            StepDto s = steps.get(i);
            if (s != null && outputId.equals(s.output())) {
                return i;
            }
        }
        return -1;
    }
}
