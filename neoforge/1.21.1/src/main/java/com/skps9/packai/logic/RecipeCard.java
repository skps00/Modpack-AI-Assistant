package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Compact recipe card for the assistant UI (from JEI).
 * {@link Layout#CRAFTING_3X3} uses {@link #grid()} (9 slots, row-major);
 * {@link Layout#FLOW} uses item / fluid / {@link RecipeExtra} lists (gas, slurry, …).
 */
public record RecipeCard(
        String categoryTitle,
        Layout layout,
        List<ItemStack> grid,
        List<ItemStack> inputs,
        List<ItemStack> catalysts,
        List<ItemStack> outputs,
        List<FluidStack> fluidInputs,
        List<FluidStack> fluidOutputs,
        List<RecipeExtra> otherInputs,
        List<RecipeExtra> otherOutputs
) {
    public enum Layout {
        CRAFTING_3X3,
        FLOW
    }

    public static RecipeCard crafting3x3(String categoryTitle, List<ItemStack> nineSlots, ItemStack output) {
        List<ItemStack> grid = normalizeNine(nineSlots);
        List<ItemStack> outs = output == null || output.isEmpty()
                ? List.of()
                : List.of(output.copy());
        return new RecipeCard(
                categoryTitle == null ? "" : categoryTitle,
                Layout.CRAFTING_3X3,
                grid,
                List.of(),
                List.of(),
                outs,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public static RecipeCard flow(
            String categoryTitle,
            List<ItemStack> inputs,
            List<ItemStack> catalysts,
            List<ItemStack> outputs,
            List<FluidStack> fluidInputs,
            List<FluidStack> fluidOutputs,
            List<RecipeExtra> otherInputs,
            List<RecipeExtra> otherOutputs
    ) {
        return new RecipeCard(
                categoryTitle == null ? "" : categoryTitle,
                Layout.FLOW,
                List.of(),
                copyItems(inputs),
                copyItems(catalysts),
                copyItems(outputs),
                copyFluids(fluidInputs),
                copyFluids(fluidOutputs),
                copyExtras(otherInputs),
                copyExtras(otherOutputs));
    }

    public boolean isEmpty() {
        if (layout == Layout.CRAFTING_3X3) {
            boolean anyIn = false;
            for (ItemStack s : grid) {
                if (s != null && !s.isEmpty()) {
                    anyIn = true;
                    break;
                }
            }
            return !anyIn && outputs.isEmpty();
        }
        return inputs.isEmpty() && catalysts.isEmpty() && outputs.isEmpty()
                && fluidInputs.isEmpty() && fluidOutputs.isEmpty()
                && otherInputs.isEmpty() && otherOutputs.isEmpty();
    }

    private static List<ItemStack> normalizeNine(List<ItemStack> nine) {
        List<ItemStack> out = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            ItemStack s = nine != null && i < nine.size() ? nine.get(i) : ItemStack.EMPTY;
            out.add(s == null || s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }
        return List.copyOf(out);
    }

    private static List<ItemStack> copyItems(List<ItemStack> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : in) {
            if (s != null && !s.isEmpty()) {
                out.add(s.copy());
            }
        }
        return List.copyOf(out);
    }

    private static List<FluidStack> copyFluids(List<FluidStack> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<FluidStack> out = new ArrayList<>();
        for (FluidStack f : in) {
            if (f != null && !f.isEmpty()) {
                out.add(f.copy());
            }
        }
        return List.copyOf(out);
    }

    private static List<RecipeExtra> copyExtras(List<RecipeExtra> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<RecipeExtra> out = new ArrayList<>();
        for (RecipeExtra e : in) {
            if (e != null && !e.isEmpty()) {
                out.add(e);
            }
        }
        return List.copyOf(out);
    }
}
