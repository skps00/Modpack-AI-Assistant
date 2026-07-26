package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.CraftPriority;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.RecipeCategoryPrefs;
import com.skps9.packai.logic.RecipeExtra;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.FluidStack;

/**
 * Structured JEI recipe cards for the assistant UI (3x3 crafting or machine flow).
 */
public final class JeiRecipeCards {
    private static final int MAX_SCAN_PER_CAT = 80;
    private static final int DEFAULT_MAX_CARDS = 3;

    private JeiRecipeCards() {}

    public static List<RecipeCard> forItem(ItemStack stack) {
        return forItem(stack, DEFAULT_MAX_CARDS);
    }

    public static List<RecipeCard> forItem(ItemStack stack, int maxCards) {
        if (stack == null || stack.isEmpty() || maxCards <= 0) {
            return List.of();
        }
        List<RecipeCard> fromJei = List.of();
        if (ModList.get().isLoaded("jei")) {
            try {
                fromJei = collect(stack, maxCards);
            } catch (NoClassDefFoundError | Exception e) {
                PackAiMod.LOGGER.debug("JEI recipe cards skipped: {}", e.toString());
            }
        }
        if (!fromJei.isEmpty()) {
            return fromJei;
        }
        // ponytail: JEI layout/API miss → vanilla crafting still paints a 3x3 card
        return fromVanillaCrafting(stack, maxCards);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeCard> collect(ItemStack stack, int maxCards) {
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return List.of();
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IIngredientManager ingredients = runtime.getIngredientManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> asOutput = focuses.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack.copy());

        List<IRecipeCategory<?>> categories = new ArrayList<>(recipes.createRecipeCategoryLookup()
                .limitFocus(List.of(asOutput))
                .get()
                .toList());
        categories.removeIf(c -> RecipeCategoryPrefs.isHidden(JeiCategoryCatalog.categoryUid(c)));
        categories.sort(Comparator
                .comparingInt((IRecipeCategory<?> c) -> RecipeCategoryPrefs.sortKey(
                        JeiCategoryCatalog.categoryUid(c), c.getTitle().getString()))
                .thenComparingInt(c -> CraftPriority.speedTier(c.getTitle().getString()))
                .thenComparing(c -> c.getTitle().getString()));

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();

        for (IRecipeCategory<?> category : categories) {
            if (out.size() >= maxCards) {
                break;
            }
            String catTitle = Plainify.stripMcFormat(category.getTitle().getString());
            RecipeType type = category.getRecipeType();
            if (JeiUniversalSpam.isSpamCategory(type, catTitle)) {
                continue;
            }
            List<?> found = recipes.createRecipeLookup(type)
                    .limitFocus(List.of(asOutput))
                    .get()
                    .limit(MAX_SCAN_PER_CAT)
                    .toList();
            for (Object recipe : found) {
                if (out.size() >= maxCards) {
                    break;
                }
                JeiRecipeLayoutCollector.CollectedLayout layout = null;
                try {
                    layout = JeiRecipeLayoutCollector.collect(category, recipe, ingredients);
                } catch (Exception ignored) {
                    // still tryCrafting below
                }
                boolean keep = layout != null
                        ? (JeiFocusMatch.outputMatchesFocus(layout, stack)
                        || JeiFocusMatch.craftingResultMatches(recipe, stack))
                        : JeiFocusMatch.craftingResultMatches(recipe, stack);
                if (!keep) {
                    continue;
                }
                if (layout != null
                        && PackAiConfig.hideUpgradeRecipes()
                        && JeiFocusMatch.focusAppearsAsInputAndOutput(layout, stack)) {
                    continue;
                }
                if (layout != null && involvesSpam(layout)) {
                    continue;
                }
                RecipeCard card = tryCrafting(recipe, catTitle);
                if ((card == null || card.isEmpty()) && layout != null) {
                    card = fromLayout(layout, catTitle, ingredients, stack);
                }
                if (card == null || card.isEmpty()) {
                    continue;
                }
                String sig = signature(card);
                if (!seen.add(sig)) {
                    continue;
                }
                out.add(card);
            }
        }
        return List.copyOf(out);
    }

    private static List<RecipeCard> fromVanillaCrafting(ItemStack stack, int maxCards) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || maxCards <= 0) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();
        try {
            for (CraftingRecipe recipe : mc.level.getRecipeManager().getAllRecipesFor(
                    net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
                if (out.size() >= maxCards) {
                    break;
                }
                if (!JeiFocusMatch.craftingResultMatches(recipe, stack)) {
                    continue;
                }
                RecipeCard card = tryCrafting(recipe, "Crafting");
                if (card == null || card.isEmpty()) {
                    continue;
                }
                if (!seen.add(signature(card))) {
                    continue;
                }
                out.add(card);
            }
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("Vanilla crafting cards skipped: {}", e.toString());
            return List.of();
        }
        return List.copyOf(out);
    }

    private static RecipeCard fromLayout(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            String catTitle,
            IIngredientManager ingredients,
            ItemStack prefer
    ) {
        List<ItemStack> inputs = stacks(layout, RecipeIngredientRole.INPUT, 12, prefer);
        List<ItemStack> outputs = stacks(layout, RecipeIngredientRole.OUTPUT, 4, prefer);
        List<ItemStack> catalysts = stacks(layout, RecipeIngredientRole.CATALYST, 3, prefer);
        List<FluidStack> fluidIn = fluids(layout, RecipeIngredientRole.INPUT, 6);
        List<FluidStack> fluidOut = fluids(layout, RecipeIngredientRole.OUTPUT, 4);
        List<RecipeExtra> otherIn = others(layout, RecipeIngredientRole.INPUT, ingredients, 6);
        List<RecipeExtra> otherOut = others(layout, RecipeIngredientRole.OUTPUT, ingredients, 4);
        if (inputs.isEmpty() && outputs.isEmpty()
                && fluidIn.isEmpty() && fluidOut.isEmpty()
                && otherIn.isEmpty() && otherOut.isEmpty()) {
            return null;
        }
        if (isCraftingTitle(catTitle) && !inputs.isEmpty() && catalysts.isEmpty()
                && fluidIn.isEmpty() && otherIn.isEmpty()) {
            List<ItemStack> grid = emptyNine();
            int n = Math.min(9, inputs.size());
            for (int i = 0; i < n; i++) {
                grid.set(i, inputs.get(i).copy());
            }
            ItemStack out = outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0);
            return RecipeCard.crafting3x3(catTitle, grid, out);
        }
        return RecipeCard.flow(catTitle, inputs, catalysts, outputs, fluidIn, fluidOut, otherIn, otherOut);
    }

    private static boolean isCraftingTitle(String title) {
        if (title == null) {
            return false;
        }
        String lowered = title.toLowerCase();
        return lowered.contains("crafting") || title.contains("工作台") || title.contains("合成");
    }

    private static RecipeCard tryCrafting(Object recipe, String catTitle) {
        if (!(recipe instanceof CraftingRecipe crafting)) {
            return null;
        }
        ItemStack result;
        try {
            result = crafting.getResultItem();
        } catch (Exception e) {
            return null;
        }
        if (result == null) {
            result = ItemStack.EMPTY;
        }
        if (crafting instanceof ShapedRecipe shaped) {
            int w = Math.max(1, shaped.getWidth());
            NonNullList<Ingredient> ings = shaped.getIngredients();
            List<ItemStack> grid = emptyNine();
            for (int i = 0; i < ings.size(); i++) {
                int row = i / w;
                int col = i % w;
                if (row >= 3 || col >= 3) {
                    continue;
                }
                grid.set(row * 3 + col, firstOf(ings.get(i)));
            }
            return RecipeCard.crafting3x3(catTitle, grid, result);
        }
        if (crafting instanceof ShapelessRecipe shapeless) {
            NonNullList<Ingredient> ings = shapeless.getIngredients();
            List<ItemStack> grid = emptyNine();
            int n = Math.min(9, ings.size());
            for (int i = 0; i < n; i++) {
                grid.set(i, firstOf(ings.get(i)));
            }
            return RecipeCard.crafting3x3(catTitle, grid, result);
        }
        return null;
    }

    private static List<ItemStack> stacks(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            RecipeIngredientRole role,
            int max,
            ItemStack prefer
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ItemStack> out = new ArrayList<>();
        // One sample per JEI slot — tag alts must not become AND inputs.
        for (ItemStack stack : layout.itemStacksOnePerSlot(role, prefer)) {
            if (out.size() >= max) {
                break;
            }
            ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
            String id = key == null ? stack.getHoverName().getString() : key.toString();
            if (!seen.add(id)) {
                continue;
            }
            out.add(stack.copy());
        }
        return out;
    }

    private static List<FluidStack> fluids(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            RecipeIngredientRole role,
            int max
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<FluidStack> out = new ArrayList<>();
        for (FluidStack fluid : layout.fluidsOnePerSlot(role)) {
            if (out.size() >= max) {
                break;
            }
            ResourceLocation key = Registry.FLUID.getKey(fluid.getFluid());
            String id = (key == null ? fluid.getDisplayName().getString() : key.toString()) + "#" + fluid.getAmount();
            if (!seen.add(id)) {
                continue;
            }
            out.add(fluid.copy());
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeExtra> others(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            RecipeIngredientRole role,
            IIngredientManager manager,
            int max
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeExtra> out = new ArrayList<>();
        for (JeiRecipeLayoutCollector.CollectedIngredient typed : layout.othersOnePerSlot(role)) {
            if (out.size() >= max) {
                break;
            }
            if (typed.type() == null || typed.ingredient() == null) {
                continue;
            }
            try {
                IIngredientHelper helper = manager.getIngredientHelper((mezz.jei.api.ingredients.IIngredientType) typed.type());
                Object ingredient = typed.ingredient();
                if (!helper.isValidIngredient(ingredient)) {
                    continue;
                }
                String name = Plainify.stripMcFormat(String.valueOf(helper.getDisplayName(ingredient)));
                if (name.isBlank() || "null".equalsIgnoreCase(name)) {
                    continue;
                }
                long amount = amountOf(helper, ingredient);
                int tint = firstColor(helper.getColors(ingredient));
                String softId = JeiSoftIngredients.put(typed, manager);
                String key = name + "#" + amount + "#" + softId;
                if (!seen.add(key.isBlank() ? name : key)) {
                    continue;
                }
                out.add(new RecipeExtra(name, amount, tint, softId));
            } catch (Throwable ignored) {
                // skip unknown ingredient types
            }
        }
        return out;
    }

    private static long amountOf(IIngredientHelper helper, Object ingredient) {
        if (helper == null || ingredient == null) {
            return 0L;
        }
        try {
            Object value = helper.getClass().getMethod("getAmount", Object.class).invoke(helper, ingredient);
            if (value instanceof Number number) {
                return number.longValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // JEI 11 on 1.19.2 may not expose amount for every ingredient helper.
        }
        return 0L;
    }

    private static int firstColor(Iterable<?> colors) {
        if (colors == null) {
            return 0xFF6EC6FF;
        }
        for (Object color : colors) {
            if (color instanceof Integer i) {
                return 0xFF000000 | (i & 0xFFFFFF);
            }
            if (color instanceof Number number) {
                return 0xFF000000 | (number.intValue() & 0xFFFFFF);
            }
        }
        return 0xFF6EC6FF;
    }

    private static boolean involvesSpam(JeiRecipeLayoutCollector.CollectedLayout layout) {
        for (RecipeIngredientRole role : List.of(
                RecipeIngredientRole.INPUT,
                RecipeIngredientRole.OUTPUT,
                RecipeIngredientRole.CATALYST)) {
            for (ItemStack stack : layout.itemStacks(role)) {
                ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
                if (key != null && JeiUniversalSpam.isSpamItemId(key.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ItemStack firstOf(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] stacks = ingredient.getItems();
        if (stacks == null || stacks.length == 0) {
            return ItemStack.EMPTY;
        }
        return stacks[0].copy();
    }

    private static List<ItemStack> emptyNine() {
        List<ItemStack> grid = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }

    private static String signature(RecipeCard card) {
        StringBuilder sb = new StringBuilder(card.categoryTitle()).append('|').append(card.layout());
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            for (ItemStack stack : card.grid()) {
                sb.append(';').append(idOf(stack));
            }
        } else {
            for (ItemStack stack : card.inputs()) {
                sb.append(";i=").append(idOf(stack));
            }
            for (ItemStack stack : card.catalysts()) {
                sb.append(";c=").append(idOf(stack));
            }
            for (FluidStack fluid : card.fluidInputs()) {
                sb.append(";fi=").append(fluidId(fluid));
            }
            for (RecipeExtra other : card.otherInputs()) {
                sb.append(";oi=").append(other.label()).append('#').append(other.amount());
            }
        }
        for (ItemStack stack : card.outputs()) {
            sb.append(";o=").append(idOf(stack));
        }
        for (FluidStack fluid : card.fluidOutputs()) {
            sb.append(";fo=").append(fluidId(fluid));
        }
        for (RecipeExtra other : card.otherOutputs()) {
            sb.append(";oo=").append(other.label()).append('#').append(other.amount());
        }
        return sb.toString();
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "-";
        }
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        return key == null ? stack.getHoverName().getString() : key.toString();
    }

    private static String fluidId(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            return "-";
        }
        ResourceLocation key = Registry.FLUID.getKey(fluid.getFluid());
        return (key == null ? fluid.getDisplayName().getString() : key.toString()) + "#" + fluid.getAmount();
    }
}
