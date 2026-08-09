package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.CraftPriority;
import com.skps9.packai.logic.ItemVariantKeys;
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
    static final int MAX_FLOW_INPUT_SLOTS = 81;
    static final int MAX_CRAFTING_3X3_SLOTS = 9;
    /** JEI slot stride in px (item 16 + 2 padding). Used to detect multi-cell layouts. */
    static final int JEI_SLOT_STRIDE = 18;

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
        // ponytail: Quests/Analyzer can fill JEI first → still merge vanilla craft if missing
        List<RecipeCard> raw = ensureCoreCraft(stack, fromJei, maxCards);
        return tagSource(raw, stack);
    }

    /**
     * If JEI returned only Quests/Analyzer (or empty), prepend vanilla crafting cards
     * so multi-select axes still get a 3×3 under their section.
     */
    static List<RecipeCard> ensureCoreCraft(ItemStack stack, List<RecipeCard> fromJei, int maxCards) {
        List<RecipeCard> jei = fromJei == null ? List.of() : fromJei;
        boolean hasCore = false;
        for (RecipeCard c : jei) {
            if (c != null && CraftPriority.isCoreCraftCategory(c.categoryTitle())) {
                hasCore = true;
                break;
            }
        }
        if (hasCore) {
            return jei.size() > maxCards ? List.copyOf(jei.subList(0, maxCards)) : List.copyOf(jei);
        }
        List<RecipeCard> vanilla = fromVanillaCrafting(stack, maxCards);
        if (vanilla.isEmpty()) {
            return jei.size() > maxCards ? List.copyOf(jei.subList(0, maxCards)) : List.copyOf(jei);
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();
        for (RecipeCard c : vanilla) {
            if (out.size() >= maxCards || c == null || c.isEmpty()) {
                continue;
            }
            if (seen.add(signature(c))) {
                out.add(c);
            }
        }
        for (RecipeCard c : jei) {
            if (out.size() >= maxCards || c == null || c.isEmpty()) {
                continue;
            }
            if (seen.add(signature(c))) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    /** Stamp Ask/JEI focus id so RecipeEmbed sections always match selected items. */
    private static List<RecipeCard> tagSource(List<RecipeCard> cards, ItemStack focus) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        String src = registryId(focus);
        if (src.isEmpty()) {
            return List.copyOf(cards);
        }
        List<RecipeCard> out = new ArrayList<>(cards.size());
        for (RecipeCard c : cards) {
            out.add(c == null ? null : c.withSourceItemId(src));
        }
        return List.copyOf(out);
    }

    private static String registryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
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
        // Ask cards: core craft/smelt before Analyzer/Quests even if custom order prefers quests.
        categories.sort(Comparator
                .comparingInt((IRecipeCategory<?> c) -> CraftPriority.isCoreCraftCategory(
                        c.getTitle().getString()) ? 0 : 1)
                .thenComparingInt(c -> RecipeCategoryPrefs.sortKey(
                        JeiCategoryCatalog.categoryUid(c), c.getTitle().getString()))
                .thenComparingInt(c -> CraftPriority.speedTier(c.getTitle().getString()))
                .thenComparing(c -> c.getTitle().getString()));

        RegistryAccess ra = registryAccess();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> aligned = new ArrayList<>();
        List<RecipeCard> fallback = new ArrayList<>();
        boolean filterVariant = ItemVariantKeys.hasVariantKeys(stack);

        for (IRecipeCategory<?> category : categories) {
            if (aligned.size() >= maxCards && (!filterVariant || fallback.size() >= maxCards)) {
                break;
            }
            IRecipeCategory cat = category;
            String catTitle = Plainify.stripMcFormat(category.getTitle().getString());
            RecipeType type = category.getRecipeType();
            if (JeiUniversalSpam.isSpamCategory(type, catTitle)) {
                continue;
            }
            // JEI shows Cooking Pot etc. via recipe-type catalysts (tab + under recipe), not setRecipe slots.
            List<ItemStack> typeCats = recipeTypeCatalysts(recipes, type, 3);

            List<?> found = recipes.createRecipeLookup(type)
                    .limitFocus(List.of(asOutput))
                    .get()
                    .limit(MAX_SCAN_PER_CAT)
                    .toList();

            for (Object recipe : found) {
                if (aligned.size() >= maxCards && (!filterVariant || fallback.size() >= maxCards * 3)) {
                    break;
                }
                try {
                    IIngredientSupplier supplier = recipes.getRecipeIngredients(cat, recipe);
                    if (!JeiFocusMatch.roleMatchesFocus(supplier, stack, RecipeIngredientRole.OUTPUT, recipe)) {
                        continue;
                    }
                    if (PackAiConfig.hideUpgradeRecipes()
                            && JeiFocusMatch.focusAppearsAsInputAndOutput(supplier, stack)) {
                        continue;
                    }
                    if (involvesSpam(supplier)) {
                        continue;
                    }
                    RecipeCard card = tryCrafting(recipe, catTitle, ra);
                    if (card == null || card.isEmpty()) {
                        JeiRecipeLayoutCollector.CollectedLayout layout = null;
                        try {
                            layout = JeiRecipeLayoutCollector.collect(category, recipe, ingredients);
                        } catch (Exception ignored) {
                            // fall through to supplier
                        }
                        if (layout != null) {
                            card = fromLayout(layout, catTitle, ingredients, stack, typeCats);
                        }
                        if (card == null || card.isEmpty()) {
                            card = fromSupplier(supplier, catTitle, ingredients, stack, typeCats);
                        }
                    }
                    if (card == null || card.isEmpty()) {
                        continue;
                    }
                    // Hard reject wrong OUTPUT registry id (never keep other-mod "扳手").
                    if (!cardOutputMatchesFocus(card, stack)) {
                        continue;
                    }
                    card = JeiLayoutDraw.attach(
                            card, recipes, category, recipe, focuses.createFocusGroup(List.of(asOutput)));
                    String sig = signature(card);
                    if (!seen.add(sig)) {
                        continue;
                    }
                    boolean variantOk = !filterVariant
                            || JeiFocusMatch.recipeMentionsVariant(supplier, stack)
                            || JeiFocusMatch.cardMentionsVariant(card, stack);
                    if (variantOk) {
                        aligned.add(card);
                    } else {
                        fallback.add(card);
                    }
                } catch (Exception ignored) {
                    // skip broken recipe wrappers
                }
            }
        }
        List<RecipeCard> chosen = aligned.isEmpty() ? fallback : aligned;
        if (chosen.size() > maxCards) {
            return List.copyOf(chosen.subList(0, maxCards));
        }
        return List.copyOf(chosen);
    }

    private static List<RecipeCard> fromVanillaCrafting(ItemStack stack, int maxCards) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.level == null || maxCards <= 0) {
            return List.of();
        }
        RegistryAccess ra = mc.level.registryAccess();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();
        try {
            for (RecipeHolder<?> holder : mc.level.getRecipeManager()
                    .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
                if (out.size() >= maxCards) {
                    break;
                }
                ItemStack result = resultOf(holder, ra);
                if (result.isEmpty() || !result.is(stack.getItem())) {
                    continue;
                }
                RecipeCard card = tryCrafting(holder, "Crafting", ra);
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
            int h = Math.max(1, shaped.getHeight());
            if (w > 3 || h > 3 || shaped.getIngredients().size() > MAX_CRAFTING_3X3_SLOTS) {
                return null;
            }
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
            if (ings.size() > MAX_CRAFTING_3X3_SLOTS) {
                return null;
            }
            List<ItemStack> grid = emptyNine();
            int n = Math.min(MAX_CRAFTING_3X3_SLOTS, ings.size());
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
            IIngredientManager ingredients,
            ItemStack prefer,
            List<ItemStack> typeCatalysts
    ) {
        // Supplier flattens alts; after collapse, treat size >9 as large (Create mechanical).
        List<ItemStack> inputsRaw = stacksKeepSlots(supplier, RecipeIngredientRole.INPUT, MAX_FLOW_INPUT_SLOTS, prefer);
        int inputSlots = inputsRaw.size();
        boolean large = inputSlots > MAX_CRAFTING_3X3_SLOTS;
        List<ItemStack> inputs = large
                ? inputsRaw
                : stacks(supplier, RecipeIngredientRole.INPUT, 12, prefer);
        List<ItemStack> outputs = stacks(supplier, RecipeIngredientRole.OUTPUT, 4, prefer);
        List<ItemStack> layoutCats = stacks(supplier, RecipeIngredientRole.CATALYST, 3, prefer);
        List<ItemStack> catalysts = mergeItemStacksById(layoutCats, typeCatalysts, 3);
        List<FluidStack> fluidIn = fluids(supplier, RecipeIngredientRole.INPUT, 6);
        List<FluidStack> fluidOut = fluids(supplier, RecipeIngredientRole.OUTPUT, 4);
        List<RecipeExtra> otherIn = others(supplier, RecipeIngredientRole.INPUT, ingredients, 6);
        List<RecipeExtra> otherOut = others(supplier, RecipeIngredientRole.OUTPUT, ingredients, 4);
        if (inputs.isEmpty() && outputs.isEmpty()
                && fluidIn.isEmpty() && fluidOut.isEmpty()
                && otherIn.isEmpty() && otherOut.isEmpty()) {
            return null;
        }
        String title = catTitle == null ? "" : catTitle;
        if (large) {
            title = titleLargeGrid(title, inputSlots, inputsRaw.size());
        }
        // Gate on layoutCats only — type catalyst (crafting table) must not force FLOW.
        if (!large
                && fitsCrafting3x3(title, inputSlots)
                && !inputs.isEmpty()
                && layoutCats.isEmpty()
                && fluidIn.isEmpty()
                && otherIn.isEmpty()) {
            List<ItemStack> grid = emptyNine();
            int n = Math.min(MAX_CRAFTING_3X3_SLOTS, inputs.size());
            for (int i = 0; i < n; i++) {
                grid.set(i, inputs.get(i).copy());
            }
            ItemStack out = outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0);
            return RecipeCard.crafting3x3(title, grid, out);
        }
        title = titleWithMachine(title, catalysts);
        return RecipeCard.flow(title, inputs, catalysts, outputs, fluidIn, fluidOut, otherIn, otherOut);
    }

    private static RecipeCard fromLayout(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            String catTitle,
            IIngredientManager ingredients,
            ItemStack prefer,
            List<ItemStack> typeCatalysts
    ) {
        int totalSlots = countNonEmptyItemSlots(layout, RecipeIngredientRole.INPUT);
        List<JeiRecipeLayoutCollector.PlacedStack> placedRaw =
                layout.placedItemStacksOnePerSlot(RecipeIngredientRole.INPUT, prefer, MAX_FLOW_INPUT_SLOTS);
        List<JeiRecipeLayoutCollector.PlacedStack> placedPanel =
                layout.placedVisibleItemStacks(prefer, MAX_FLOW_INPUT_SLOTS);
        boolean large = totalSlots > MAX_CRAFTING_3X3_SLOTS;
        List<ItemStack> inputs = new ArrayList<>();
        for (JeiRecipeLayoutCollector.PlacedStack p : placedRaw) {
            inputs.add(p.stack().copy());
        }
        if (!large) {
            // compact: id-dedupe for small cards
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            List<ItemStack> compact = new ArrayList<>();
            for (ItemStack stack : inputs) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                String id = key == null ? stack.getHoverName().getString() : key.toString();
                if (seen.add(id)) {
                    compact.add(stack);
                }
                if (compact.size() >= 12) {
                    break;
                }
            }
            inputs = compact;
        }
        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack stack : layout.itemStacksOnePerSlot(RecipeIngredientRole.OUTPUT, prefer)) {
            if (outputs.size() >= 4) {
                break;
            }
            outputs.add(stack.copy());
        }
        List<ItemStack> layoutCats = new ArrayList<>();
        for (ItemStack stack : layout.itemStacksOnePerSlot(RecipeIngredientRole.CATALYST, prefer)) {
            if (layoutCats.size() >= 3) {
                break;
            }
            layoutCats.add(stack.copy());
        }
        List<ItemStack> catalysts = mergeItemStacksById(layoutCats, typeCatalysts, 3);
        List<FluidStack> fluidIn = fluidsFromLayout(layout, RecipeIngredientRole.INPUT, 6);
        List<FluidStack> fluidOut = fluidsFromLayout(layout, RecipeIngredientRole.OUTPUT, 4);
        List<RecipeExtra> otherIn = othersFromLayout(layout, RecipeIngredientRole.INPUT, ingredients, 6);
        List<RecipeExtra> otherOut = othersFromLayout(layout, RecipeIngredientRole.OUTPUT, ingredients, 4);
        if (inputs.isEmpty() && outputs.isEmpty()
                && fluidIn.isEmpty() && fluidOut.isEmpty()
                && otherIn.isEmpty() && otherOut.isEmpty()) {
            return null;
        }
        String title = catTitle == null ? "" : catTitle;
        if (large) {
            title = titleLargeGrid(title, totalSlots, placedRaw.size());
        }
        if (!large
                && fitsCrafting3x3(title, totalSlots)
                && !inputs.isEmpty()
                && layoutCats.isEmpty()
                && fluidIn.isEmpty()
                && otherIn.isEmpty()) {
            List<ItemStack> grid = emptyNine();
            int n = Math.min(MAX_CRAFTING_3X3_SLOTS, inputs.size());
            for (int i = 0; i < n; i++) {
                grid.set(i, inputs.get(i).copy());
            }
            ItemStack out = outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0);
            return RecipeCard.crafting3x3(title, grid, out);
        }
        List<JeiRecipeLayoutCollector.PlacedStack> shapedSrc =
                preferMultiRolePanel(title, placedPanel) ? placedPanel : placedRaw;
        if (hasUsefulPositions(shapedSrc) || preferMultiRolePanel(title, placedPanel)) {
            List<RecipeCard.PlacedItem> placed = new ArrayList<>();
            List<ItemStack> panelCats = new ArrayList<>();
            for (JeiRecipeLayoutCollector.PlacedStack p : shapedSrc) {
                RecipeCard.SlotKind kind = slotKindOf(p.role());
                placed.add(new RecipeCard.PlacedItem(p.stack(), p.x(), p.y(), kind));
                if (kind == RecipeCard.SlotKind.CATALYST) {
                    panelCats.add(p.stack());
                }
            }
            catalysts = mergeItemStacksById(catalysts, panelCats, 3);
            title = titleWithMachine(title, catalysts);
            // Keep full catalysts on card for header icon; SHAPED UI skips footer machines.
            return RecipeCard.shaped(title, placed, catalysts, outputs, fluidIn, fluidOut, otherIn, otherOut);
        }
        title = titleWithMachine(title, catalysts);
        return RecipeCard.flow(title, inputs, catalysts, outputs, fluidIn, fluidOut, otherIn, otherOut);
    }

    /**
     * JEI recipe-type catalysts (furnace, Cooking Pot, …) shown on category tab / under recipe.
     * Not present in {@code setRecipe} slots for many cooking mods.
     */
    static List<ItemStack> recipeTypeCatalysts(IRecipeManager recipes, RecipeType<?> type, int max) {
        if (recipes == null || type == null || max <= 0) {
            return List.of();
        }
        try {
            List<ItemStack> out = new ArrayList<>();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (ItemStack stack : recipes.createRecipeCatalystLookup(type).getItemStack().toList()) {
                if (out.size() >= max) {
                    break;
                }
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                String id = idOf(stack);
                if (id.equals("-") || !seen.add(id)) {
                    continue;
                }
                out.add(stack.copy());
            }
            return List.copyOf(out);
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI type catalysts skipped: {}", t.toString());
            return List.of();
        }
    }

    static List<ItemStack> mergeItemStacksById(List<ItemStack> primary, List<ItemStack> extra, int max) {
        List<ItemStack> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (List<ItemStack> src : List.of(
                primary == null ? List.<ItemStack>of() : primary,
                extra == null ? List.<ItemStack>of() : extra)) {
            for (ItemStack stack : src) {
                if (out.size() >= max) {
                    return List.copyOf(out);
                }
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                String id = idOf(stack);
                if (id.equals("-") || !seen.add(id)) {
                    continue;
                }
                out.add(stack.copy());
            }
        }
        return List.copyOf(out);
    }

    /** Append first machine hover name when title does not already name it. */
    static String titleWithMachine(String title, List<ItemStack> catalysts) {
        String base = title == null ? "" : title.trim();
        if (catalysts == null || catalysts.isEmpty()) {
            return base;
        }
        ItemStack machine = catalysts.get(0);
        if (machine == null || machine.isEmpty()) {
            return base;
        }
        String name = Plainify.stripMcFormat(machine.getHoverName().getString());
        if (name.isBlank()) {
            return base;
        }
        String lower = base.toLowerCase(Locale.ROOT);
        if (lower.contains(name.toLowerCase(Locale.ROOT))) {
            return base;
        }
        if (base.isBlank()) {
            return name;
        }
        return base + " · " + name;
    }

    static RecipeCard.SlotKind slotKindOf(RecipeIngredientRole role) {
        if (role == RecipeIngredientRole.CATALYST) {
            return RecipeCard.SlotKind.CATALYST;
        }
        if (role == RecipeIngredientRole.OUTPUT) {
            return RecipeCard.SlotKind.OUTPUT;
        }
        if (role == RecipeIngredientRole.RENDER_ONLY) {
            return RecipeCard.SlotKind.RENDER;
        }
        return RecipeCard.SlotKind.INPUT;
    }

    static boolean preferMultiRolePanel(String title, List<JeiRecipeLayoutCollector.PlacedStack> panel) {
        if (panel == null || panel.size() < 2) {
            return false;
        }
        if (isVanillaSizedCraftingTitle(title)) {
            return false;
        }
        return true;
    }

    static boolean hasUsefulPositions(List<JeiRecipeLayoutCollector.PlacedStack> placed) {
        if (placed == null || placed.size() < 2) {
            return false;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (JeiRecipeLayoutCollector.PlacedStack p : placed) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        return (maxX - minX) >= JEI_SLOT_STRIDE || (maxY - minY) >= JEI_SLOT_STRIDE;
    }

    private static List<FluidStack> fluidsFromLayout(
            JeiRecipeLayoutCollector.CollectedLayout layout, RecipeIngredientRole role, int max
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<FluidStack> out = new ArrayList<>();
        for (FluidStack fluid : layout.fluidsOnePerSlot(role)) {
            if (out.size() >= max) {
                break;
            }
            ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            String id = (key == null ? "?" : key.toString()) + "#" + fluid.getAmount();
            if (!seen.add(id)) {
                continue;
            }
            out.add(fluid.copy());
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeExtra> othersFromLayout(
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
                IIngredientHelper helper = manager.getIngredientHelper(
                        (mezz.jei.api.ingredients.IIngredientType) typed.type());
                Object ingredient = typed.ingredient();
                if (!helper.isValidIngredient(ingredient)) {
                    continue;
                }
                String name = Plainify.stripMcFormat(String.valueOf(helper.getDisplayName(ingredient)));
                if (name.isBlank() || "null".equalsIgnoreCase(name)) {
                    continue;
                }
                long amount = helper.getAmount(ingredient);
                int tint = firstColor(helper.getColors(ingredient));
                // ponytail: layout path has type+ingredient, not ITypedIngredient — label-only soft
                String softId = "";
                String key = name + "#" + amount;
                if (!seen.add(key.isBlank() ? name : key)) {
                    continue;
                }
                out.add(new RecipeExtra(name, amount, tint, softId));
            } catch (Throwable ignored) {
                // skip unknown
            }
        }
        return out;
    }

    static boolean fitsCrafting3x3(String title, int inputSlots) {
        if (inputSlots <= 0 || inputSlots > MAX_CRAFTING_3X3_SLOTS) {
            return false;
        }
        return isVanillaSizedCraftingTitle(title);
    }

    static boolean isVanillaSizedCraftingTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String t = title.toLowerCase(Locale.ROOT);
        if (t.contains("mechanical")
                || t.contains("动力")
                || t.contains("動力")
                || t.contains("automated")
                || t.contains("sequenced")
                || t.contains("assembly")
                || t.contains("装配")
                || t.contains("裝配")) {
            return false;
        }
        return t.contains("crafting") || title.contains("工作台") || title.contains("合成");
    }

    static String titleWithSlotCount(String title, int slots) {
        String base = title == null || title.isBlank() ? "?" : title.trim();
        if (slots <= 0) {
            return base;
        }
        return base + " · " + slots + " slots";
    }

    /** True slot count in title; when grid icons truncated, honest open-JEI note. */
    static String titleLargeGrid(String title, int totalSlots, int shownSlots) {
        String base = titleWithSlotCount(title, totalSlots);
        if (shownSlots < totalSlots) {
            return base + " · grid truncated — open JEI";
        }
        return base;
    }

    private static int countNonEmptyItemSlots(
            JeiRecipeLayoutCollector.CollectedLayout layout, RecipeIngredientRole role
    ) {
        int n = 0;
        for (JeiRecipeLayoutCollector.CollectedSlot slot : layout.slots(role)) {
            if (!layout.itemsInSlot(slot).isEmpty()) {
                n++;
            }
        }
        return n;
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

    private static List<ItemStack> stacks(
            IIngredientSupplier supplier, RecipeIngredientRole role, int max, ItemStack prefer
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ItemStack> flat = new ArrayList<>();
        for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
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
            flat.add(s);
        }
        // JEI supplier flattens tag-slot alts — collapse consecutive OR-groups.
        List<ItemStack> collapsed = IngredientReqHints.collapseAlternatives(flat, prefer);
        if (collapsed.size() > max) {
            return List.copyOf(collapsed.subList(0, max));
        }
        return collapsed;
    }

    /**
     * Prefer slot multiplicity for large grids: collapse tag OR-groups but keep duplicate ids
     * across different slots (Create mechanical diamond).
     */
    private static List<ItemStack> stacksKeepSlots(
            IIngredientSupplier supplier, RecipeIngredientRole role, int max, ItemStack prefer
    ) {
        List<ItemStack> flat = new ArrayList<>();
        for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
            Optional<ItemStack> opt = typed.getItemStack();
            if (opt.isEmpty() || opt.get().isEmpty()) {
                continue;
            }
            flat.add(opt.get().copy());
        }
        List<ItemStack> collapsed = IngredientReqHints.collapseAlternatives(flat, prefer);
        if (collapsed.size() > max) {
            return List.copyOf(collapsed.subList(0, max));
        }
        return collapsed;
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

    /** True when card lists focus registry id as an output (or has no item outputs). */
    private static boolean cardOutputMatchesFocus(RecipeCard card, ItemStack focus) {
        if (card == null || focus == null || focus.isEmpty()) {
            return true;
        }
        ResourceLocation focusKey = BuiltInRegistries.ITEM.getKey(focus.getItem());
        if (focusKey == null) {
            return true;
        }
        String want = focusKey.toString();
        boolean anyOut = false;
        if (card.outputs() != null) {
            for (ItemStack out : card.outputs()) {
                if (out == null || out.isEmpty()) {
                    continue;
                }
                anyOut = true;
                if (out.is(focus.getItem())) {
                    return true;
                }
            }
        }
        String pid = card.primaryOutputId();
        if (pid != null && !pid.isEmpty()) {
            return want.equalsIgnoreCase(pid);
        }
        // Fluid/soft-only cards: keep.
        return !anyOut;
    }

    private static String signature(RecipeCard card) {
        StringBuilder sb = new StringBuilder(card.categoryTitle()).append('|').append(card.layout());
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            for (ItemStack s : card.grid()) {
                sb.append(';').append(idOf(s));
            }
        } else if (card.layout() == RecipeCard.Layout.SHAPED) {
            for (RecipeCard.PlacedItem p : card.placedInputs()) {
                sb.append(";p=").append(p.x()).append(',').append(p.y()).append('=').append(idOf(p.stack()));
            }
            for (ItemStack s : card.catalysts()) {
                sb.append(";c=").append(idOf(s));
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
