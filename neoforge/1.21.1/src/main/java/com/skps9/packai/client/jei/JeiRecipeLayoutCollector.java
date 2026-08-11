package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.gui.widgets.ISlottedWidgetFactory;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Harvests JEI category slot data (Neo) by calling {@link IRecipeCategory#setRecipe}.
 */
final class JeiRecipeLayoutCollector {
    private JeiRecipeLayoutCollector() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static CollectedLayout collect(IRecipeCategory<?> category, Object recipe, mezz.jei.api.runtime.IIngredientManager ingredientManager) {
        CollectedLayout layout = new CollectedLayout();
        LayoutBuilder builder = new LayoutBuilder(layout, ingredientManager);
        ((IRecipeCategory) category).setRecipe(builder, recipe, EmptyFocusGroup.INSTANCE);
        return layout;
    }

    /** Empty focus group for {@code createRecipeLayoutDrawable} / setRecipe harvest. */
    static IFocusGroup emptyFocus() {
        return EmptyFocusGroup.INSTANCE;
    }

    static final class CollectedLayout {
        private final List<CollectedSlot> visibleSlots = new ArrayList<>();
        private final List<CollectedSlot> invisibleSlots = new ArrayList<>();

        List<CollectedIngredient> ingredients(RecipeIngredientRole role) {
            List<CollectedIngredient> out = new ArrayList<>();
            collectFrom(this.visibleSlots, role, out);
            collectFrom(this.invisibleSlots, role, out);
            return out;
        }

        /** All item alternatives across slots (focus match / spam scan). */
        List<ItemStack> itemStacks(RecipeIngredientRole role) {
            List<ItemStack> out = new ArrayList<>();
            for (CollectedIngredient ingredient : ingredients(role)) {
                if (ingredient.ingredient() instanceof ItemStack stack && !stack.isEmpty()) {
                    out.add(stack.copy());
                }
            }
            return out;
        }

        /**
         * One sample ItemStack per JEI slot (tag / multi-choice → single icon, not AND-all).
         * Prefers {@code prefer} when that stack appears in the slot.
         */
        List<ItemStack> itemStacksOnePerSlot(RecipeIngredientRole role, ItemStack prefer) {
            List<ItemStack> out = new ArrayList<>();
            for (CollectedSlot slot : slots(role)) {
                ItemStack sample = firstItemInSlot(slot, prefer);
                if (!sample.isEmpty()) {
                    out.add(sample);
                }
            }
            return out;
        }

        /** One sample per visible JEI slot with layout x/y (Create mechanical diamond etc.). */
        List<PlacedStack> placedItemStacksOnePerSlot(RecipeIngredientRole role, ItemStack prefer, int max) {
            List<PlacedStack> out = new ArrayList<>();
            for (CollectedSlot slot : slots(role)) {
                if (out.size() >= max) {
                    break;
                }
                if (!slot.visible()) {
                    continue;
                }
                ItemStack sample = firstItemInSlot(slot, prefer);
                if (sample.isEmpty()) {
                    continue;
                }
                out.add(new PlacedStack(sample, slot.x(), slot.y(), slot.role()));
            }
            return out;
        }

        /**
         * Visible item slots across INPUT / CATALYST / OUTPUT / RENDER_ONLY with JEI x/y.
         * Cooking / machine panels need catalyst + container + output positions, not INPUT-only.
         */
        List<PlacedStack> placedVisibleItemStacks(ItemStack prefer, int max) {
            List<PlacedStack> out = new ArrayList<>();
            for (CollectedSlot slot : this.visibleSlots) {
                if (out.size() >= max) {
                    break;
                }
                if (!slot.visible()) {
                    continue;
                }
                RecipeIngredientRole role = slot.role();
                if (role != RecipeIngredientRole.INPUT
                        && role != RecipeIngredientRole.CATALYST
                        && role != RecipeIngredientRole.OUTPUT
                        && role != RecipeIngredientRole.RENDER_ONLY) {
                    continue;
                }
                ItemStack sample = firstItemInSlot(slot, prefer);
                if (sample.isEmpty()) {
                    continue;
                }
                out.add(new PlacedStack(sample, slot.x(), slot.y(), role));
            }
            return out;
        }

        List<CollectedSlot> slots(RecipeIngredientRole role) {
            List<CollectedSlot> out = new ArrayList<>();
            collectSlots(this.visibleSlots, role, out);
            collectSlots(this.invisibleSlots, role, out);
            return out;
        }

        List<ItemStack> itemsInSlot(CollectedSlot slot) {
            List<ItemStack> out = new ArrayList<>();
            if (slot == null) {
                return out;
            }
            for (CollectedIngredient ingredient : slot.ingredients()) {
                if (ingredient.ingredient() instanceof ItemStack stack && !stack.isEmpty()) {
                    out.add(stack.copy());
                }
            }
            return out;
        }

        List<FluidStack> fluids(RecipeIngredientRole role) {
            List<FluidStack> out = new ArrayList<>();
            for (CollectedIngredient ingredient : ingredients(role)) {
                if (ingredient.ingredient() instanceof FluidStack fluid && !fluid.isEmpty()) {
                    out.add(fluid.copy());
                }
            }
            return out;
        }

        /** One fluid sample per JEI slot. */
        List<FluidStack> fluidsOnePerSlot(RecipeIngredientRole role) {
            List<FluidStack> out = new ArrayList<>();
            for (CollectedSlot slot : slots(role)) {
                for (CollectedIngredient ingredient : slot.ingredients()) {
                    if (ingredient.ingredient() instanceof FluidStack fluid && !fluid.isEmpty()) {
                        out.add(fluid.copy());
                        break;
                    }
                }
            }
            return out;
        }

        /**
         * Visible fluid tanks with JEI x/y and {@code setFluidRenderer} size.
         * Used so Pack AI can paint fluids inside the card (JEI tank blit may ignore pose).
         */
        List<PlacedFluidStack> placedVisibleFluids(int max) {
            List<PlacedFluidStack> out = new ArrayList<>();
            for (CollectedSlot slot : this.visibleSlots) {
                if (out.size() >= max) {
                    break;
                }
                if (!slot.visible()) {
                    continue;
                }
                FluidStack sample = firstFluidInSlot(slot);
                if (sample.isEmpty()) {
                    continue;
                }
                out.add(new PlacedFluidStack(
                        sample,
                        slot.x(),
                        slot.y(),
                        slot.fluidWidth(),
                        slot.fluidHeight(),
                        slot.role()));
            }
            return out;
        }

        List<CollectedIngredient> others(RecipeIngredientRole role) {
            List<CollectedIngredient> out = new ArrayList<>();
            for (CollectedIngredient ingredient : ingredients(role)) {
                Object value = ingredient.ingredient();
                if (!(value instanceof ItemStack) && !(value instanceof FluidStack) && value != null) {
                    out.add(ingredient);
                }
            }
            return out;
        }

        /** One non-item/non-fluid sample per JEI slot. */
        List<CollectedIngredient> othersOnePerSlot(RecipeIngredientRole role) {
            List<CollectedIngredient> out = new ArrayList<>();
            for (CollectedSlot slot : slots(role)) {
                for (CollectedIngredient ingredient : slot.ingredients()) {
                    Object value = ingredient.ingredient();
                    if (!(value instanceof ItemStack) && !(value instanceof FluidStack) && value != null) {
                        out.add(ingredient);
                        break;
                    }
                }
            }
            return out;
        }

        private static void collectSlots(
                List<CollectedSlot> slots, RecipeIngredientRole role, List<CollectedSlot> out
        ) {
            for (CollectedSlot slot : slots) {
                if (slot.role() == role) {
                    out.add(slot);
                }
            }
        }

        private static ItemStack firstItemInSlot(CollectedSlot slot, ItemStack prefer) {
            ItemStack fallback = ItemStack.EMPTY;
            for (CollectedIngredient ingredient : slot.ingredients()) {
                if (!(ingredient.ingredient() instanceof ItemStack stack) || stack.isEmpty()) {
                    continue;
                }
                if (prefer != null && !prefer.isEmpty() && ItemStack.isSameItemSameComponents(stack, prefer)) {
                    return stack.copy();
                }
                if (prefer != null && !prefer.isEmpty() && stack.is(prefer.getItem())) {
                    return stack.copy();
                }
                if (fallback.isEmpty()) {
                    fallback = stack.copy();
                }
            }
            return fallback;
        }

        private static FluidStack firstFluidInSlot(CollectedSlot slot) {
            if (slot == null) {
                return FluidStack.EMPTY;
            }
            for (CollectedIngredient ingredient : slot.ingredients()) {
                if (ingredient.ingredient() instanceof FluidStack fluid && !fluid.isEmpty()) {
                    return fluid.copy();
                }
            }
            return FluidStack.EMPTY;
        }

        boolean hasItemRole(RecipeIngredientRole role) {
            for (CollectedIngredient ingredient : ingredients(role)) {
                if (ingredient.ingredient() instanceof ItemStack stack && !stack.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private static void collectFrom(List<CollectedSlot> slots, RecipeIngredientRole role, List<CollectedIngredient> out) {
            for (CollectedSlot slot : slots) {
                if (slot.role() == role) {
                    out.addAll(slot.ingredients());
                }
            }
        }
    }

    static final class CollectedSlot {
        private final RecipeIngredientRole role;
        private final boolean visible;
        private int x;
        private int y;
        private int fluidWidth = 16;
        private int fluidHeight = 16;
        private final List<CollectedIngredient> ingredients = new ArrayList<>();

        CollectedSlot(RecipeIngredientRole role, boolean visible, int x, int y) {
            this.role = role;
            this.visible = visible;
            this.x = x;
            this.y = y;
        }

        RecipeIngredientRole role() {
            return this.role;
        }

        boolean visible() {
            return this.visible;
        }

        int x() {
            return this.x;
        }

        int y() {
            return this.y;
        }

        int fluidWidth() {
            return this.fluidWidth;
        }

        int fluidHeight() {
            return this.fluidHeight;
        }

        void setFluidRendererSize(int width, int height) {
            if (width > 0) {
                this.fluidWidth = width;
            }
            if (height > 0) {
                this.fluidHeight = height;
            }
        }

        void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }

        List<CollectedIngredient> ingredients() {
            return this.ingredients;
        }
    }

    record CollectedIngredient(IIngredientType<?> type, Object ingredient) {}

    /** Item sample + JEI layout coords from {@link IRecipeLayoutBuilder#addSlot}. */
    record PlacedStack(ItemStack stack, int x, int y, RecipeIngredientRole role) {}

    /** Fluid sample + JEI tank coords / renderer size. */
    record PlacedFluidStack(
            FluidStack fluid,
            int x,
            int y,
            int width,
            int height,
            RecipeIngredientRole role
    ) {}

    private static final class LayoutBuilder implements IRecipeLayoutBuilder {
        private final CollectedLayout layout;
        private final mezz.jei.api.runtime.IIngredientManager ingredientManager;

        LayoutBuilder(CollectedLayout layout, mezz.jei.api.runtime.IIngredientManager ingredientManager) {
            this.layout = layout;
            this.ingredientManager = ingredientManager;
        }

        @Override
        public IRecipeSlotBuilder addSlot(RecipeIngredientRole recipeIngredientRole) {
            CollectedSlot slot = new CollectedSlot(recipeIngredientRole, true, 0, 0);
            this.layout.visibleSlots.add(slot);
            return new SlotBuilder(slot, this.ingredientManager);
        }

        @Override
        public IRecipeSlotBuilder addSlotToWidget(RecipeIngredientRole recipeIngredientRole, ISlottedWidgetFactory<?> widgetFactory) {
            // harvest-only: ignore widget factory; same as a normal slot
            return addSlot(recipeIngredientRole);
        }

        @Override
        public IIngredientAcceptor<?> addInvisibleIngredients(RecipeIngredientRole recipeIngredientRole) {
            CollectedSlot slot = new CollectedSlot(recipeIngredientRole, false, 0, 0);
            this.layout.invisibleSlots.add(slot);
            return new InvisibleAcceptor(slot, this.ingredientManager);
        }

        @Override
        public void moveRecipeTransferButton(int posX, int posY) {
            // UI-only
        }

        @Override
        public void setShapeless() {
            // UI-only
        }

        @Override
        public void setShapeless(int posX, int posY) {
            // UI-only
        }

        @Override
        public void createFocusLink(IIngredientAcceptor<?>... slots) {
            // Lookup-only
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class SlotBuilder implements IRecipeSlotBuilder {
        private final CollectedSlot slot;
        private final mezz.jei.api.runtime.IIngredientManager ingredientManager;

        SlotBuilder(CollectedSlot slot, mezz.jei.api.runtime.IIngredientManager ingredientManager) {
            this.slot = slot;
            this.ingredientManager = ingredientManager;
        }

        @Override
        public SlotBuilder addIngredients(IIngredientType ingredientType, List ingredients) {
            if (ingredients != null) {
                for (Object ingredient : ingredients) {
                    addIngredient(ingredientType, ingredient);
                }
            }
            return this;
        }

        @Override
        public SlotBuilder addIngredient(IIngredientType ingredientType, Object ingredient) {
            collectIngredient(this.slot, this.ingredientManager, ingredientType, ingredient);
            return this;
        }

        @Override
        public SlotBuilder addIngredientsUnsafe(List ingredients) {
            if (ingredients != null) {
                for (Object ingredient : ingredients) {
                    collectIngredient(this.slot, this.ingredientManager, null, ingredient);
                }
            }
            return this;
        }

        @Override
        public SlotBuilder addFluidStack(Fluid fluid) {
            return addFluidStack(fluid, 1000L);
        }

        @Override
        public SlotBuilder addFluidStack(Fluid fluid, long amount) {
            return addFluidStack(fluid, amount, DataComponentPatch.EMPTY);
        }

        @Override
        public SlotBuilder addFluidStack(Fluid fluid, long amount, DataComponentPatch component) {
            if (fluid == null || amount <= 0L) {
                return this;
            }
            int safeAmount = (int) Math.min(Integer.MAX_VALUE, amount);
            DataComponentPatch patch = component != null ? component : DataComponentPatch.EMPTY;
            FluidStack stack = patch.isEmpty()
                    ? new FluidStack(fluid, safeAmount)
                    : new FluidStack(fluid.builtInRegistryHolder(), safeAmount, patch);
            collectIngredient(this.slot, this.ingredientManager, inferType(this.ingredientManager, stack), stack);
            return this;
        }

        @Override
        public SlotBuilder addTypedIngredients(List<ITypedIngredient<?>> ingredients) {
            if (ingredients != null) {
                for (ITypedIngredient<?> typed : ingredients) {
                    if (typed != null) {
                        collectIngredient(this.slot, this.ingredientManager, typed.getType(), typed.getIngredient());
                    }
                }
            }
            return this;
        }

        @Override
        public SlotBuilder addOptionalTypedIngredients(List<Optional<ITypedIngredient<?>>> ingredients) {
            if (ingredients != null) {
                for (Optional<ITypedIngredient<?>> typed : ingredients) {
                    if (typed != null && typed.isPresent()) {
                        ITypedIngredient<?> value = typed.get();
                        collectIngredient(this.slot, this.ingredientManager, value.getType(), value.getIngredient());
                    }
                }
            }
            return this;
        }

        @Override
        public SlotBuilder addTooltipCallback(IRecipeSlotTooltipCallback tooltipCallback) {
            return this;
        }

        @Override
        public SlotBuilder addRichTooltipCallback(IRecipeSlotRichTooltipCallback richTooltipCallback) {
            return this;
        }

        @Override
        public SlotBuilder setSlotName(String slotName) {
            return this;
        }

        @Override
        public SlotBuilder setStandardSlotBackground() {
            return this;
        }

        @Override
        public SlotBuilder setOutputSlotBackground() {
            return this;
        }

        @Override
        public SlotBuilder setBackground(IDrawable background, int xOffset, int yOffset) {
            return this;
        }

        @Override
        public SlotBuilder setOverlay(IDrawable overlay, int xOffset, int yOffset) {
            return this;
        }

        @Override
        public SlotBuilder setFluidRenderer(long capacity, boolean showCapacity, int width, int height) {
            this.slot.setFluidRendererSize(width, height);
            return this;
        }

        @Override
        public SlotBuilder setCustomRenderer(IIngredientType ingredientType, IIngredientRenderer ingredientRenderer) {
            return this;
        }

        @Override
        public SlotBuilder setPosition(int x, int y) {
            this.slot.setPosition(x, y);
            return this;
        }

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class InvisibleAcceptor implements IIngredientAcceptor<InvisibleAcceptor> {
        private final CollectedSlot slot;
        private final mezz.jei.api.runtime.IIngredientManager ingredientManager;

        InvisibleAcceptor(CollectedSlot slot, mezz.jei.api.runtime.IIngredientManager ingredientManager) {
            this.slot = slot;
            this.ingredientManager = ingredientManager;
        }

        @Override
        public InvisibleAcceptor addIngredients(IIngredientType ingredientType, List ingredients) {
            if (ingredients != null) {
                for (Object ingredient : ingredients) {
                    addIngredient(ingredientType, ingredient);
                }
            }
            return this;
        }

        @Override
        public InvisibleAcceptor addIngredient(IIngredientType ingredientType, Object ingredient) {
            collectIngredient(this.slot, this.ingredientManager, ingredientType, ingredient);
            return this;
        }

        @Override
        public InvisibleAcceptor addIngredientsUnsafe(List ingredients) {
            if (ingredients != null) {
                for (Object ingredient : ingredients) {
                    collectIngredient(this.slot, this.ingredientManager, null, ingredient);
                }
            }
            return this;
        }

        @Override
        public InvisibleAcceptor addFluidStack(Fluid fluid) {
            return addFluidStack(fluid, 1000L);
        }

        @Override
        public InvisibleAcceptor addFluidStack(Fluid fluid, long amount) {
            return addFluidStack(fluid, amount, DataComponentPatch.EMPTY);
        }

        @Override
        public InvisibleAcceptor addFluidStack(Fluid fluid, long amount, DataComponentPatch component) {
            if (fluid == null || amount <= 0L) {
                return this;
            }
            int safeAmount = (int) Math.min(Integer.MAX_VALUE, amount);
            DataComponentPatch patch = component != null ? component : DataComponentPatch.EMPTY;
            FluidStack stack = patch.isEmpty()
                    ? new FluidStack(fluid, safeAmount)
                    : new FluidStack(fluid.builtInRegistryHolder(), safeAmount, patch);
            collectIngredient(this.slot, this.ingredientManager, inferType(this.ingredientManager, stack), stack);
            return this;
        }

        @Override
        public InvisibleAcceptor addTypedIngredients(List<ITypedIngredient<?>> ingredients) {
            if (ingredients != null) {
                for (ITypedIngredient<?> typed : ingredients) {
                    if (typed != null) {
                        collectIngredient(this.slot, this.ingredientManager, typed.getType(), typed.getIngredient());
                    }
                }
            }
            return this;
        }

        @Override
        public InvisibleAcceptor addOptionalTypedIngredients(List<Optional<ITypedIngredient<?>>> ingredients) {
            if (ingredients != null) {
                for (Optional<ITypedIngredient<?>> typed : ingredients) {
                    if (typed != null && typed.isPresent()) {
                        ITypedIngredient<?> value = typed.get();
                        collectIngredient(this.slot, this.ingredientManager, value.getType(), value.getIngredient());
                    }
                }
            }
            return this;
        }
    }

    private static void collectIngredient(
            CollectedSlot slot,
            mezz.jei.api.runtime.IIngredientManager ingredientManager,
            IIngredientType<?> ingredientType,
            Object ingredient
    ) {
        if (slot == null || ingredient == null) {
            return;
        }
        IIngredientType<?> type = ingredientType != null ? ingredientType : inferType(ingredientManager, ingredient);
        Object copy = copyIngredient(ingredientManager, type, ingredient);
        if (copy == null) {
            return;
        }
        if (copy instanceof ItemStack item && item.isEmpty()) {
            return;
        }
        if (copy instanceof FluidStack fluid && fluid.isEmpty()) {
            return;
        }
        slot.ingredients().add(new CollectedIngredient(type, copy));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object copyIngredient(
            mezz.jei.api.runtime.IIngredientManager ingredientManager,
            IIngredientType<?> ingredientType,
            Object ingredient
    ) {
        if (ingredient instanceof ItemStack item) {
            return item.copy();
        }
        if (ingredient instanceof FluidStack fluid) {
            return fluid.copy();
        }
        if (ingredientManager != null && ingredientType != null) {
            try {
                IIngredientHelper helper = ingredientManager.getIngredientHelper((IIngredientType) ingredientType);
                if (helper != null && helper.isValidIngredient(ingredient)) {
                    Object copy = helper.copyIngredient(ingredient);
                    if (copy != null) {
                        return copy;
                    }
                }
            } catch (Throwable ignored) {
                // Keep raw ingredient below
            }
        }
        return ingredient;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IIngredientType<?> inferType(mezz.jei.api.runtime.IIngredientManager ingredientManager, Object ingredient) {
        if (ingredientManager == null || ingredient == null) {
            return null;
        }
        try {
            Optional<IIngredientType<?>> type = (Optional) ingredientManager.getIngredientTypeChecked(ingredient);
            return type.orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class EmptyFocusGroup implements IFocusGroup {
        private static final EmptyFocusGroup INSTANCE = new EmptyFocusGroup();

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public List<IFocus<?>> getAllFocuses() {
            return List.of();
        }

        @Override
        public Stream<IFocus<?>> getFocuses(RecipeIngredientRole role) {
            return Stream.empty();
        }

        @Override
        public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType) {
            return Stream.empty();
        }

        @Override
        public <T> Stream<IFocus<T>> getFocuses(IIngredientType<T> ingredientType, RecipeIngredientRole role) {
            return Stream.empty();
        }
    }
}
