package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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
        /** JEI fluid tank slots with layout x/y/w/h (pixels). Empty → footer fallback. */
        List<PlacedFluid> placedFluids,
        /** Ask/JEI focus item that produced this card (section key). Empty if unknown. */
        String sourceItemId,
        /**
         * Opaque JEI {@code IRecipeLayoutDrawable} (client). Null when unavailable.
         * Logic never inspects; UI draws category background / flame / clock extras.
         */
        Object jeiLayout,
        /**
         * JEI-visible non-slot notes (XP / cook time / stress / energy text / …).
         * Empty for plain 3×3 crafting. Merged via {@link FormatRequirements}.
         */
        List<String> reqNotes,
        /**
         * Why this card was collected for the Ask focus:
         * {@link FocusRole#OUTPUT} = how to obtain/craft; {@link FocusRole#INPUT} = uses as material.
         */
        FocusRole focusRole,
        /**
         * Unlock gate labels from {@link RecipeUnlockGates} (#1B/#1C). Empty when none.
         * Prefixed by {@link FormatRequirements} / {@link ReplyLang#unlockPrefix}.
         */
        List<String> unlockGates,
        /**
         * FTB/Heracles quest id for open_book when this card is a quest JEI recipe.
         * Empty for normal craft cards. Category JEI title is often just "Quests" —
         * real quest name is written into {@link #categoryTitle} at collect time.
         */
        String questOpenId
) {
    public RecipeCard {
        sourceItemId = sourceItemId == null ? "" : sourceItemId.toLowerCase(Locale.ROOT);
        placedFluids = placedFluids == null ? List.of() : placedFluids;
        reqNotes = reqNotes == null || reqNotes.isEmpty() ? List.of() : List.copyOf(reqNotes);
        focusRole = focusRole == null ? FocusRole.OUTPUT : focusRole;
        unlockGates = unlockGates == null || unlockGates.isEmpty() ? List.of() : List.copyOf(unlockGates);
        questOpenId = questOpenId == null ? "" : questOpenId.trim();
    }

    /** Focus item role that produced this card (JEI R / U). */
    public enum FocusRole {
        /** Focus is a recipe result — obtain / craft. */
        OUTPUT,
        /** Focus is a recipe ingredient — uses as material. */
        INPUT
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

    /** One JEI fluid tank sample with layout coords + renderer size. */
    public record PlacedFluid(FluidStack fluid, int x, int y, int width, int height, SlotKind kind) {
        public PlacedFluid {
            fluid = fluid == null || fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
            width = Math.max(1, width);
            height = Math.max(1, height);
            kind = kind == null ? SlotKind.INPUT : kind;
        }

        public PlacedFluid(FluidStack fluid, int x, int y) {
            this(fluid, x, y, 16, 16, SlotKind.INPUT);
        }
    }

    public RecipeCard withSourceItemId(String id) {
        return new RecipeCard(
                categoryTitle, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                placedFluids, id == null ? "" : id, jeiLayout, reqNotes, focusRole, unlockGates, questOpenId);
    }

    public RecipeCard withJeiLayout(Object layoutDrawable) {
        return new RecipeCard(
                categoryTitle, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                placedFluids, sourceItemId, layoutDrawable, reqNotes, focusRole, unlockGates, questOpenId);
    }

    public RecipeCard withPlacedFluids(List<PlacedFluid> fluids) {
        return new RecipeCard(
                categoryTitle, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                copyPlacedFluids(fluids), sourceItemId, jeiLayout, reqNotes, focusRole, unlockGates, questOpenId);
    }

    public RecipeCard withReqNotes(List<String> notes) {
        return new RecipeCard(
                categoryTitle, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                placedFluids, sourceItemId, jeiLayout, notes == null ? List.of() : notes, focusRole, unlockGates,
                questOpenId);
    }

    public RecipeCard withUnlockGates(List<String> gates) {
        return new RecipeCard(
                categoryTitle, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                placedFluids, sourceItemId, jeiLayout, reqNotes, focusRole,
                gates == null ? List.of() : gates, questOpenId);
    }

    public RecipeCard withFocusRole(FocusRole role) {
        return new RecipeCard(
                categoryTitle, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                placedFluids, sourceItemId, jeiLayout, reqNotes, role, unlockGates, questOpenId);
    }


    public RecipeCard withCategoryTitle(String title) {
        return new RecipeCard(
                title == null ? "" : title, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                placedFluids, sourceItemId, jeiLayout, reqNotes, focusRole, unlockGates, questOpenId);
    }

    public RecipeCard withQuestOpenId(String id) {
        return new RecipeCard(
                categoryTitle, layout, grid, inputs, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, placedInputs,
                placedFluids, sourceItemId, jeiLayout, reqNotes, focusRole, unlockGates,
                id == null ? "" : id);
    }

    /** True when this card can open an FTB/Heracles quest book entry. */
    public boolean hasQuestOpen() {
        return questOpenId != null && !questOpenId.isEmpty();
    }

    public boolean isInputUse() {
        return focusRole == FocusRole.INPUT;
    }

    /** True when JEI gave tank x/y — draw in-layout, not footer strip. */
    public boolean hasPlacedFluids() {
        return placedFluids != null && !placedFluids.isEmpty();
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
                List.of(),
                "",
                null,
                List.of(),
                FocusRole.OUTPUT,
                List.of(),
                "");
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
                List.of(),
                "",
                null,
                List.of(),
                FocusRole.OUTPUT,
                List.of(),
                "");
    }

    /**
     * Compact workbench install strip (icons + counts). Empty {@code items} + none label →
     * title-only strip via {@link RecipeExtra} softId {@code packai:label}.
     */
    public static RecipeCard materialStrip(
            String categoryTitle,
            List<ItemStack> items,
            String noneLabel,
            String sourceItemId
    ) {
        List<ItemStack> inputs = copyItems(items);
        List<RecipeExtra> extras = List.of();
        if (inputs.isEmpty() && noneLabel != null && !noneLabel.isBlank()) {
            extras = List.of(new RecipeExtra(
                    noneLabel.trim(), 0, 0x00000000, TetraSchematicText.MATERIAL_NONE_SOFT_ID));
        }
        return flow(
                categoryTitle == null ? "" : categoryTitle,
                inputs,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                extras,
                List.of()
        ).withSourceItemId(sourceItemId == null ? "" : sourceItemId);
    }

    /** True when this FLOW card is a scroll material strip (incl. none-required label). */
    public boolean isScrollMaterialStrip() {
        if (layout != Layout.FLOW) {
            return false;
        }
        if (otherInputs != null) {
            for (RecipeExtra e : otherInputs) {
                if (e != null && TetraSchematicText.MATERIAL_NONE_SOFT_ID.equals(e.softId())) {
                    return true;
                }
            }
        }
        // Heuristic: inputs-only FLOW with no outputs/fluids/catalysts (AskService material attach).
        return !inputs.isEmpty()
                && (outputs == null || outputs.isEmpty())
                && (catalysts == null || catalysts.isEmpty())
                && (fluidInputs == null || fluidInputs.isEmpty())
                && (fluidOutputs == null || fluidOutputs.isEmpty())
                && (otherOutputs == null || otherOutputs.isEmpty())
                && (otherInputs == null || otherInputs.isEmpty());
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
        return shaped(
                categoryTitle, placed, catalysts, outputs,
                fluidInputs, fluidOutputs, otherInputs, otherOutputs, List.of());
    }

    public static RecipeCard shaped(
            String categoryTitle,
            List<PlacedItem> placed,
            List<ItemStack> catalysts,
            List<ItemStack> outputs,
            List<FluidStack> fluidInputs,
            List<FluidStack> fluidOutputs,
            List<RecipeExtra> otherInputs,
            List<RecipeExtra> otherOutputs,
            List<PlacedFluid> placedFluids
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
                copyPlacedFluids(placedFluids),
                "",
                null,
                List.of(),
                FocusRole.OUTPUT,
                List.of(),
                "");
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
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

    private static List<PlacedFluid> copyPlacedFluids(List<PlacedFluid> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<PlacedFluid> out = new ArrayList<>();
        for (PlacedFluid p : in) {
            if (p != null && p.fluid() != null && !p.fluid().isEmpty()) {
                out.add(new PlacedFluid(p.fluid(), p.x(), p.y(), p.width(), p.height(), p.kind()));
            }
        }
        return List.copyOf(out);
    }
}
