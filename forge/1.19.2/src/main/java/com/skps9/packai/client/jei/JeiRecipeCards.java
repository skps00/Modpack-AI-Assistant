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
    /** Cap for FLOW/SHAPED slots (Create mechanical max 9×9=81). Beyond: title marks truncated. */
    static final int MAX_FLOW_INPUT_SLOTS = 81;
    /** Vanilla-sized crafting grid only — larger / irregular layouts use SHAPED or FLOW. */
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
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
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

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> aligned = new ArrayList<>();
        List<RecipeCard> fallback = new ArrayList<>();
        boolean filterVariant = ItemVariantKeys.hasVariantKeys(stack);

        for (IRecipeCategory<?> category : categories) {
            if (aligned.size() >= maxCards && (!filterVariant || fallback.size() >= maxCards)) {
                break;
            }
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
                    card = fromLayout(layout, catTitle, ingredients, stack, typeCats);
                }
                if (card == null || card.isEmpty()) {
                    continue;
                }
                // Hard reject wrong OUTPUT registry id (never keep other-mod "扳手").
                if (!cardOutputMatchesFocus(card, stack)) {
                    continue;
                }
                card = JeiLayoutDraw.attach(card, recipes, category, recipe);
                String sig = signature(card);
                if (!seen.add(sig)) {
                    continue;
                }
                boolean variantOk = !filterVariant
                        || (layout != null && JeiFocusMatch.recipeMentionsVariant(layout, stack))
                        || JeiFocusMatch.cardMentionsVariant(card, stack);
                if (variantOk) {
                    aligned.add(card);
                } else {
                    fallback.add(card);
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
            ItemStack prefer,
            List<ItemStack> typeCatalysts
    ) {
        int inputSlots = countNonEmptyItemSlots(layout, RecipeIngredientRole.INPUT);
        boolean large = inputSlots > MAX_CRAFTING_3X3_SLOTS;
        List<JeiRecipeLayoutCollector.PlacedStack> placedRaw =
                layout.placedItemStacksOnePerSlot(RecipeIngredientRole.INPUT, prefer, MAX_FLOW_INPUT_SLOTS);
        List<JeiRecipeLayoutCollector.PlacedStack> placedPanel =
                layout.placedVisibleItemStacks(prefer, MAX_FLOW_INPUT_SLOTS);
        // Large grids: keep one sample per JEI slot (no id-dedupe) so Create diamonds stay usable.
        List<ItemStack> inputs = large
                ? stacksOnePerSlot(layout, RecipeIngredientRole.INPUT, MAX_FLOW_INPUT_SLOTS, prefer)
                : stacks(layout, RecipeIngredientRole.INPUT, 12, prefer);
        List<ItemStack> outputs = stacks(layout, RecipeIngredientRole.OUTPUT, 4, prefer);
        // Layout CATALYST slots only — type catalysts (Cooking Pot) are separate JEI API.
        List<ItemStack> layoutCats = stacks(layout, RecipeIngredientRole.CATALYST, 3, prefer);
        List<ItemStack> catalysts = mergeItemStacksById(layoutCats, typeCatalysts, 3);
        List<FluidStack> fluidIn = fluids(layout, RecipeIngredientRole.INPUT, 6);
        List<FluidStack> fluidOut = fluids(layout, RecipeIngredientRole.OUTPUT, 4);
        List<RecipeExtra> otherIn = others(layout, RecipeIngredientRole.INPUT, ingredients, 6);
        List<RecipeExtra> otherOut = others(layout, RecipeIngredientRole.OUTPUT, ingredients, 4);
        if (inputs.isEmpty() && outputs.isEmpty()
                && fluidIn.isEmpty() && fluidOut.isEmpty()
                && otherIn.isEmpty() && otherOut.isEmpty()) {
            return null;
        }
        String title = catTitle == null ? "" : catTitle;
        if (large) {
            title = titleLargeGrid(title, inputSlots, placedRaw.size());
        }
        // Only smash into 3×3 when JEI really has ≤9 input slots and looks like table crafting.
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
        // Cooking / machine / Create: keep JEI x/y (multi-role when useful), not FLOW dump.
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

    /** Map JEI role → card slot kind (null / unknown → INPUT). */
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

    /**
     * Non-crafting categories: use multi-role JEI coords whenever ≥2 visible item slots
     * (cooking needs catalyst / container / output structure, not a flat ingredient strip).
     */
    static boolean preferMultiRolePanel(String title, List<JeiRecipeLayoutCollector.PlacedStack> panel) {
        if (panel == null || panel.size() < 2) {
            return false;
        }
        if (isVanillaSizedCraftingTitle(title)) {
            return false;
        }
        return true;
    }

    /** True when JEI slot coords span more than one cell (diamond / large grid). */
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

    /** True for vanilla-sized crafting titles — not Create mechanical / 动力合成 / assembly. */
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
            int h = Math.max(1, shaped.getHeight());
            // Oversize shaped (Create mechanical etc.) — leave to fromLayout FLOW.
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
        if (crafting instanceof ShapelessRecipe shapeless) {
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

    private static List<ItemStack> stacks(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            RecipeIngredientRole role,
            int max,
            ItemStack prefer
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ItemStack> out = new ArrayList<>();
        // One sample per JEI slot — tag alts must not become AND inputs; id-dedupe for compact cards.
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

    /** One sample per JEI slot — keep duplicates (same item in many slots). */
    private static List<ItemStack> stacksOnePerSlot(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            RecipeIngredientRole role,
            int max,
            ItemStack prefer
    ) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : layout.itemStacksOnePerSlot(role, prefer)) {
            if (out.size() >= max) {
                break;
            }
            if (stack == null || stack.isEmpty()) {
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

    /** True when card lists focus registry id as an output (or has no item outputs). */
    private static boolean cardOutputMatchesFocus(RecipeCard card, ItemStack focus) {
        if (card == null || focus == null || focus.isEmpty()) {
            return true;
        }
        ResourceLocation focusKey = Registry.ITEM.getKey(focus.getItem());
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
            for (ItemStack stack : card.grid()) {
                sb.append(';').append(idOf(stack));
            }
        } else if (card.layout() == RecipeCard.Layout.SHAPED) {
            for (RecipeCard.PlacedItem p : card.placedInputs()) {
                sb.append(";p=").append(p.x()).append(',').append(p.y()).append('=').append(idOf(p.stack()));
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
