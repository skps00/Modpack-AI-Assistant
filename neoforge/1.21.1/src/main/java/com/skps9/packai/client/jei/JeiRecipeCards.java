package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.CraftPriority;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.RecipeCategoryPrefs;
import com.skps9.packai.logic.RecipeExtra;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Structured JEI recipe cards for the assistant UI (3×3 crafting or machine flow).
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
        if (!ModList.get().isLoaded("jei")) {
            return List.of();
        }
        try {
            return collect(stack, maxCards);
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI recipe cards skipped: {}", e.toString());
            return List.of();
        }
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

        RegistryAccess ra = registryAccess();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();

        for (IRecipeCategory<?> category : categories) {
            if (out.size() >= maxCards) {
                break;
            }
            IRecipeCategory cat = category;
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
                try {
                    IIngredientSupplier supplier = recipes.getRecipeIngredients(cat, recipe);
                    if (!JeiFocusMatch.outputMatchesFocus(supplier, stack)) {
                        continue;
                    }
                    if (involvesSpam(supplier)) {
                        continue;
                    }
                    RecipeCard card = tryCrafting(recipe, catTitle, ra);
                    if (card == null || card.isEmpty()) {
                        card = fromSupplier(supplier, catTitle, ingredients);
                    }
                    if (card == null || card.isEmpty()) {
                        continue;
                    }
                    String sig = signature(card);
                    if (!seen.add(sig)) {
                        continue;
                    }
                    out.add(card);
                } catch (Exception ignored) {
                    // skip broken recipe wrappers
                }
            }
        }
        return List.copyOf(out);
    }

    private static RecipeCard tryCrafting(Object recipe, String catTitle, RegistryAccess ra) {
        if (!(recipe instanceof RecipeHolder<?> holder)) {
            return null;
        }
        Object value = holder.value();
        if (!(value instanceof CraftingRecipe)) {
            return null;
        }
        ItemStack result = resultOf(holder, ra);
        if (value instanceof ShapedRecipe shaped) {
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
        if (value instanceof ShapelessRecipe shapeless) {
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

    private static RecipeCard fromSupplier(
            IIngredientSupplier supplier,
            String catTitle,
            IIngredientManager ingredients
    ) {
        List<ItemStack> inputs = stacks(supplier, RecipeIngredientRole.INPUT, 12);
        List<ItemStack> outputs = stacks(supplier, RecipeIngredientRole.OUTPUT, 4);
        List<ItemStack> catalysts = stacks(supplier, RecipeIngredientRole.CATALYST, 3);
        List<FluidStack> fluidIn = fluids(supplier, RecipeIngredientRole.INPUT, 6);
        List<FluidStack> fluidOut = fluids(supplier, RecipeIngredientRole.OUTPUT, 4);
        List<RecipeExtra> otherIn = others(supplier, RecipeIngredientRole.INPUT, ingredients, 6);
        List<RecipeExtra> otherOut = others(supplier, RecipeIngredientRole.OUTPUT, ingredients, 4);
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
        String t = title.toLowerCase();
        return t.contains("crafting") || title.contains("工作台") || title.contains("合成");
    }

    private static ItemStack resultOf(RecipeHolder<?> holder, RegistryAccess ra) {
        try {
            Object value = holder.value();
            if (value instanceof CraftingRecipe crafting && ra != null) {
                ItemStack r = crafting.getResultItem(ra);
                return r == null || r.isEmpty() ? ItemStack.EMPTY : r.copy();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack firstOf(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] stacks = ingredient.getItems();
        if (stacks == null || stacks.length == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack s = stacks[0];
        return s == null || s.isEmpty() ? ItemStack.EMPTY : s.copy();
    }

    private static List<ItemStack> emptyNine() {
        List<ItemStack> g = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            g.add(ItemStack.EMPTY);
        }
        return g;
    }

    private static List<ItemStack> stacks(IIngredientSupplier supplier, RecipeIngredientRole role, int max) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ItemStack> out = new ArrayList<>();
        for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
            if (out.size() >= max) {
                break;
            }
            Optional<ItemStack> opt = typed.getItemStack();
            if (opt.isEmpty() || opt.get().isEmpty()) {
                continue;
            }
            ItemStack s = opt.get().copy();
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
            String id = key == null ? s.getHoverName().getString() : key.toString();
            if (!seen.add(id)) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    private static List<FluidStack> fluids(IIngredientSupplier supplier, RecipeIngredientRole role, int max) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<FluidStack> out = new ArrayList<>();
        for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
            if (out.size() >= max) {
                break;
            }
            Optional<FluidStack> opt = typed.getIngredient(NeoForgeTypes.FLUID_STACK);
            if (opt.isEmpty() || opt.get().isEmpty()) {
                continue;
            }
            FluidStack f = opt.get().copy();
            ResourceLocation key = BuiltInRegistries.FLUID.getKey(f.getFluid());
            String id = (key == null ? f.getHoverName().getString() : key.toString()) + "#" + f.getAmount();
            if (!seen.add(id)) {
                continue;
            }
            out.add(f);
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeExtra> others(
            IIngredientSupplier supplier,
            RecipeIngredientRole role,
            IIngredientManager manager,
            int max
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeExtra> out = new ArrayList<>();
        for (ITypedIngredient typed : supplier.getIngredients(role)) {
            if (out.size() >= max) {
                break;
            }
            Optional<ItemStack> asItem = typed.getItemStack();
            if (asItem.isPresent() && !asItem.get().isEmpty()) {
                continue;
            }
            Optional<FluidStack> asFluid = typed.getIngredient(NeoForgeTypes.FLUID_STACK);
            if (asFluid.isPresent() && !asFluid.get().isEmpty()) {
                continue;
            }
            try {
                IIngredientHelper helper = manager.getIngredientHelper(typed.getType());
                Object ingredient = typed.getIngredient();
                if (!helper.isValidIngredient(ingredient)) {
                    continue;
                }
                String name = Plainify.stripMcFormat(String.valueOf(helper.getDisplayName(ingredient)));
                if (name.isBlank() || "null".equalsIgnoreCase(name)) {
                    continue;
                }
                long amount = helper.getAmount(ingredient);
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

    private static int firstColor(Iterable<?> colors) {
        if (colors == null) {
            return 0xFF6EC6FF;
        }
        for (Object c : colors) {
            if (c instanceof Integer i) {
                return 0xFF000000 | (i & 0xFFFFFF);
            }
            if (c instanceof Number n) {
                return 0xFF000000 | (n.intValue() & 0xFFFFFF);
            }
        }
        return 0xFF6EC6FF;
    }

    private static boolean involvesSpam(IIngredientSupplier supplier) {
        for (RecipeIngredientRole role : List.of(
                RecipeIngredientRole.INPUT,
                RecipeIngredientRole.OUTPUT,
                RecipeIngredientRole.CATALYST)) {
            for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
                Optional<ItemStack> stack = typed.getItemStack();
                if (stack.isEmpty() || stack.get().isEmpty()) {
                    continue;
                }
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.get().getItem());
                if (key != null && JeiUniversalSpam.isSpamItemId(key.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String signature(RecipeCard card) {
        StringBuilder sb = new StringBuilder(card.categoryTitle()).append('|').append(card.layout());
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            for (ItemStack s : card.grid()) {
                sb.append(';').append(idOf(s));
            }
        } else {
            for (ItemStack s : card.inputs()) {
                sb.append(";i=").append(idOf(s));
            }
            for (ItemStack s : card.catalysts()) {
                sb.append(";c=").append(idOf(s));
            }
            for (FluidStack f : card.fluidInputs()) {
                sb.append(";fi=").append(fluidId(f));
            }
            for (RecipeExtra o : card.otherInputs()) {
                sb.append(";oi=").append(o.label()).append('#').append(o.amount());
            }
        }
        for (ItemStack s : card.outputs()) {
            sb.append(";o=").append(idOf(s));
        }
        for (FluidStack f : card.fluidOutputs()) {
            sb.append(";fo=").append(fluidId(f));
        }
        for (RecipeExtra o : card.otherOutputs()) {
            sb.append(";oo=").append(o.label()).append('#').append(o.amount());
        }
        return sb.toString();
    }

    private static String idOf(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return "-";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
        return key == null ? s.getHoverName().getString() : key.toString();
    }

    private static String fluidId(FluidStack f) {
        if (f == null || f.isEmpty()) {
            return "-";
        }
        ResourceLocation key = BuiltInRegistries.FLUID.getKey(f.getFluid());
        return (key == null ? f.getHoverName().getString() : key.toString()) + "#" + f.getAmount();
    }

    private static RegistryAccess registryAccess() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.registryAccess();
        }
        return RegistryAccess.EMPTY;
    }
}
