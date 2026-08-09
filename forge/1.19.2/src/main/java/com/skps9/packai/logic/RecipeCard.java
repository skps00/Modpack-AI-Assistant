package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Compact recipe card for the assistant UI (from JEI).
 * {@link Layout#CRAFTING_3X3} uses {@link #grid()} (9 slots, row-major);
 * {@link Layout#SHAPED} keeps JEI slot x/y ({@link #placedInputs()}) scaled in UI;
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
        List<RecipeExtra> otherOutputs,
        List<PlacedItem> placedInputs,
        /** Ask/JEI focus item that produced this card (section key). Empty if unknown. */
        String sourceItemId,
        /**
         * Opaque JEI {@code IRecipeLayoutDrawable} (client). Null when unavailable.
         * Logic never inspects; UI draws category background / flame / clock extras.
         */
        Object jeiLayout
) {
    public RecipeCard {
        sourceItemId = sourceItemId == null ? "" : sourceItemId.toLowerCase(Locale.ROOT);
    }

    public enum Layout {
        CRAFTING_3X3,
        FLOW,
        SHAPED
    }

    /**
     * One JEI slot sample with layout coords (pixels as JEI reported).
     * {@link SlotKind} distinguishes catalyst / output / render-only in SHAPED panels.
     */
    public enum SlotKind {
        INPUT,
        CATALYST,
        OUTPUT,
        RENDER
    }

    public record PlacedItem(ItemStack stack, int x, int y, SlotKind kind) {
        public PlacedItem {
            stack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            kind = kind == null ? SlotKind.INPUT : kind;
        }

        public PlacedItem(ItemStack stack, int x, int y) {
            this(stack, x, y, SlotKind.INPUT);
        }
    }

    public RecipeCard withSourceItemId(String id) {
        return new RecipeCard(
                categoryTitle, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                id == null ? "" : id, jeiLayout);
    }

    public RecipeCard withJeiLayout(Object layoutDrawable) {
        return new RecipeCard(
                categoryTitle, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                sourceItemId, layoutDrawable);
    }

    /**
     * UI section key: prefer Ask focus id that collected this card, else primary output.
     * Quests / FLOW with odd outputs still group under the selected item.
     */
    public String sectionKey() {
        if (sourceItemId != null && !sourceItemId.isEmpty()) {
            return sourceItemId;
        }
        return primaryOutputId();
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
                List.of(),
                List.of(),
                "",
                null);
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
                copyExtras(otherOutputs),
                List.of(),
                "",
                null);
    }

    public static RecipeCard shaped(
            String categoryTitle,
            List<PlacedItem> placed,
            List<ItemStack> catalysts,
            List<ItemStack> outputs,
            List<FluidStack> fluidInputs,
            List<FluidStack> fluidOutputs,
            List<RecipeExtra> otherInputs,
            List<RecipeExtra> otherOutputs
    ) {
        List<PlacedItem> copy = copyPlaced(placed);
        List<ItemStack> flat = new ArrayList<>();
        for (PlacedItem p : copy) {
            if (p.stack() != null && !p.stack().isEmpty()) {
                flat.add(p.stack().copy());
            }
        }
        return new RecipeCard(
                categoryTitle == null ? "" : categoryTitle,
                Layout.SHAPED,
                List.of(),
                List.copyOf(flat),
                copyItems(catalysts),
                copyItems(outputs),
                copyFluids(fluidInputs),
                copyFluids(fluidOutputs),
                copyExtras(otherInputs),
                copyExtras(otherOutputs),
                copy,
                "",
                null);
    }

    /** Primary output registry id (for {@code [[recipe:mod:id]]} matching), or empty. */
    public String primaryOutputId() {
        if (outputs != null) {
            for (ItemStack stack : outputs) {
                String id = itemId(stack);
                if (!id.isEmpty()) {
                    return id;
                }
            }
        }
        return "";
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
        if (layout == Layout.SHAPED) {
            return placedInputs.isEmpty() && outputs.isEmpty()
                    && catalysts.isEmpty()
                    && fluidInputs.isEmpty() && fluidOutputs.isEmpty()
                    && otherInputs.isEmpty() && otherOutputs.isEmpty();
        }
        return inputs.isEmpty() && catalysts.isEmpty() && outputs.isEmpty()
                && fluidInputs.isEmpty() && fluidOutputs.isEmpty()
                && otherInputs.isEmpty() && otherOutputs.isEmpty();
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
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

    private static List<PlacedItem> copyPlaced(List<PlacedItem> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<PlacedItem> out = new ArrayList<>();
        for (PlacedItem p : in) {
            if (p != null && p.stack() != null && !p.stack().isEmpty()) {
                out.add(new PlacedItem(p.stack(), p.x(), p.y(), p.kind()));
            }
        }
        return List.copyOf(out);
    }
}
