package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.skps9.packai.PackAiMod;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import com.skps9.packai.logic.RecipeCard;

/**
 * Lightweight item (output) → JEI category uid index. No recipe bodies stored.
 * Built incrementally on the client thread; queries scan only indexed categories.
 */
public final class JeiCategoryIndex {
    public static final JeiCategoryIndex INSTANCE = new JeiCategoryIndex();

    public static final int MAX_CATEGORIES = 512;
    public static final int MAX_RECIPES = 400_000;
    private static final long TICK_BUDGET_NS = 2_000_000L;

    private static volatile boolean enabled = true;

    @FunctionalInterface
    interface CardSupplier {
        List<RecipeCard> cards(Item item, List<ResourceLocation> categoryUids, String role, int cap);
    }

    static CardSupplier cardSupplier = JeiCategoryIndex::defaultCards;

    private final Map<Item, LinkedHashSet<ResourceLocation>> itemToCategories = new HashMap<>();

    private volatile boolean ready;
    private volatile boolean building;
    private long buildStartMs;
    private int categoriesTotal;
    private int recipesIndexed;
    private int failedCategories;

    private List<IRecipeCategory<?>> buildCategories = List.of();
    private int buildCatIdx;
    private List<?> buildRecipes = List.of();
    private int buildRecipeIdx;

    private IRecipeManager buildManager;
    private IIngredientManager buildIngredients;

    private boolean capCategoriesLogged;
    private boolean capRecipesLogged;

    private JeiCategoryIndex() {}

    public boolean ready() {
        return ready;
    }

    public static List<RecipeCard> cardsViaIndex(Item item, String role, int cap) {
        return INSTANCE.query(item, role, cap);
    }

    List<RecipeCard> query(Item item, String role, int cap) {
        if (!enabled) {
            return List.of();
        }
        String itemId = itemId(item);
        String r = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if (item == null || itemId.isEmpty() || cap <= 0 || r.isEmpty()) {
            return List.of();
        }
        if (!ready) {
            PackAiMod.LOGGER.info("Pack AI id_index not_ready item={} role={}", itemId, r);
            return List.of();
        }
        LinkedHashSet<ResourceLocation> cats = itemToCategories.get(item);
        if (cats == null || cats.isEmpty()) {
            PackAiMod.LOGGER.info("Pack AI id_index miss item={} role={}", itemId, r);
            return List.of();
        }
        List<ResourceLocation> ordered = List.copyOf(cats);
        List<RecipeCard> cards = cardSupplier.cards(item, ordered, r, cap);
        if (cards == null || cards.isEmpty()) {
            PackAiMod.LOGGER.info("Pack AI id_index miss item={} role={}", itemId, r);
            return List.of();
        }
        PackAiMod.LOGGER.info(
                "Pack AI id_index hit item={} role={} cats={} cards={}",
                itemId, r, ordered.size(), cards.size());
        return cards;
    }

    private static List<RecipeCard> defaultCards(
            Item item, List<ResourceLocation> categoryUids, String role, int cap
    ) {
        ItemStack unit = new ItemStack(item);
        if (unit.isEmpty()) {
            return List.of();
        }
        RecipeIngredientRole jeiRole = "uses".equals(role)
                ? RecipeIngredientRole.INPUT
                : RecipeIngredientRole.OUTPUT;
        String itemId = itemId(item);
        List<RecipeCard> raw = JeiRecipeCards.collectInCategoryUids(unit, categoryUids, jeiRole, cap);
        if (raw.isEmpty()) {
            return List.of();
        }
        List<RecipeCard> out = new ArrayList<>(raw.size());
        for (RecipeCard c : raw) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            RecipeCard withSrc = c.sourceItemId() == null || c.sourceItemId().isBlank()
                    ? c.withSourceItemId(itemId) : c;
            out.add(withSrc);
        }
        return List.copyOf(out);
    }

    public void clear() {
        building = false;
        ready = false;
        itemToCategories.clear();
        buildCategories = List.of();
        buildCatIdx = 0;
        buildRecipes = List.of();
        buildRecipeIdx = 0;
        buildManager = null;
        buildIngredients = null;
        recipesIndexed = 0;
        categoriesTotal = 0;
        failedCategories = 0;
        capCategoriesLogged = false;
        capRecipesLogged = false;
    }

    /** Client thread: start or continue incremental index build when JEI is available. */
    public void requestBuild(IRecipeManager rm, IIngredientManager ingredients) {
        if (rm == null || ingredients == null) {
            return;
        }
        if (ready || building) {
            return;
        }
        building = true;
        ready = false;
        buildStartMs = System.currentTimeMillis();
        recipesIndexed = 0;
        failedCategories = 0;
        buildManager = rm;
        buildIngredients = ingredients;
        buildCatIdx = 0;
        buildRecipeIdx = 0;
        try {
            buildCategories = new ArrayList<>(rm.createRecipeCategoryLookup()
                    .includeHidden()
                    .get()
                    .toList());
        } catch (Throwable t) {
            building = false;
            PackAiMod.LOGGER.warn("Pack AI id_index build start failed: {}", t.toString());
            return;
        }
        categoriesTotal = buildCategories.size();
        if (categoriesTotal > MAX_CATEGORIES && !capCategoriesLogged) {
            capCategoriesLogged = true;
            PackAiMod.LOGGER.info(
                    "Pack AI id_index cap categories={} limit={}", categoriesTotal, MAX_CATEGORIES);
        }
        if (buildCategories.size() > MAX_CATEGORIES) {
            buildCategories = List.copyOf(buildCategories.subList(0, MAX_CATEGORIES));
        }
        advanceCategoryIfNeeded();
    }

    /** Client thread END tick slice. */
    public void tick() {
        if (!building) {
            return;
        }
        long deadline = System.nanoTime() + TICK_BUDGET_NS;
        while (building && System.nanoTime() < deadline) {
            if (!stepOneRecipe()) {
                finishBuild();
                break;
            }
        }
    }

    private boolean stepOneRecipe() {
        if (buildCatIdx >= buildCategories.size()) {
            return false;
        }
        if (buildRecipeIdx >= buildRecipes.size()) {
            buildCatIdx++;
            advanceCategoryIfNeeded();
            if (buildCatIdx >= buildCategories.size()) {
                return false;
            }
        }
        if (buildRecipeIdx >= buildRecipes.size()) {
            return false;
        }
        if (recipesIndexed >= MAX_RECIPES) {
            if (!capRecipesLogged) {
                capRecipesLogged = true;
                PackAiMod.LOGGER.info(
                        "Pack AI id_index cap recipes={} limit={}", recipesIndexed, MAX_RECIPES);
            }
            return false;
        }
        IRecipeCategory<?> category = buildCategories.get(buildCatIdx);
        Object recipe = buildRecipes.get(buildRecipeIdx);
        buildRecipeIdx++;
        indexRecipeOutput(category, recipe);
        recipesIndexed++;
        return true;
    }

    private void advanceCategoryIfNeeded() {
        buildRecipes = List.of();
        buildRecipeIdx = 0;
        while (buildCatIdx < buildCategories.size()) {
            IRecipeCategory<?> category = buildCategories.get(buildCatIdx);
            if (category == null) {
                buildCatIdx++;
                continue;
            }
            ResourceLocation uid = categoryUid(category);
            RecipeType<?> type;
            try {
                type = category.getRecipeType();
            } catch (Throwable t) {
                failedCategories++;
                buildCatIdx++;
                continue;
            }
            if (type == null) {
                failedCategories++;
                buildCatIdx++;
                continue;
            }
            List<?> found;
            try {
                found = buildManager.createRecipeLookup(type)
                        .includeHidden()
                        .get()
                        .toList();
            } catch (Throwable t) {
                failedCategories++;
                buildCatIdx++;
                continue;
            }
            buildRecipes = found;
            if (buildRecipes.isEmpty()) {
                buildCatIdx++;
                continue;
            }
            return;
        }
    }

    private void indexRecipeOutput(IRecipeCategory<?> category, Object recipe) {
        if (category == null || recipe == null) {
            return;
        }
        ResourceLocation uid = categoryUid(category);
        if (uid == null) {
            return;
        }
        try {
            if (recipe instanceof CraftingRecipe crafting) {
                ItemStack out = crafting.getResultItem();
                if (!out.isEmpty()) {
                    noteOutput(out.getItem(), uid);
                }
                return;
            }
            JeiRecipeLayoutCollector.CollectedLayout layout = JeiRecipeLayoutCollector.collect(
                    category, recipe, buildIngredients);
            for (ItemStack stack : layout.itemStacks(RecipeIngredientRole.OUTPUT)) {
                if (!stack.isEmpty()) {
                    noteOutput(stack.getItem(), uid);
                }
            }
        } catch (Throwable ignored) {
            // per-recipe failure — skip
        }
    }

    private void noteOutput(Item item, ResourceLocation categoryUid) {
        if (item == null || categoryUid == null) {
            return;
        }
        itemToCategories
                .computeIfAbsent(item, k -> new LinkedHashSet<>())
                .add(categoryUid);
    }

    private void finishBuild() {
        building = false;
        ready = true;
        long ms = Math.max(0L, System.currentTimeMillis() - buildStartMs);
        PackAiMod.LOGGER.info(
                "Pack AI id_index ready cats={} items={} recipes={} ms={}",
                buildCategories.size(),
                itemToCategories.size(),
                recipesIndexed,
                ms);
        buildManager = null;
        buildIngredients = null;
        buildCategories = List.of();
    }

    private static ResourceLocation categoryUid(IRecipeCategory<?> category) {
        String uid = JeiCategoryCatalog.categoryUid(category);
        if (uid == null || uid.isBlank()) {
            return null;
        }
        try {
            return new ResourceLocation(uid);
        } catch (Exception e) {
            return null;
        }
    }

    private static String itemId(Item item) {
        if (item == null) {
            return "";
        }
        ResourceLocation key = Registry.ITEM.getKey(item);
        return key == null ? "" : key.toString();
    }

    static Comparator<RecipeCard> stableCardOrder() {
        return Comparator
                .comparing((RecipeCard c) -> c.categoryTitle() == null ? "" : c.categoryTitle())
                .thenComparing(c -> c.primaryOutputId() == null ? "" : c.primaryOutputId())
                .thenComparing(c -> c.sourceItemId() == null ? "" : c.sourceItemId())
                .thenComparing(c -> c.focusRole() == null ? "" : c.focusRole().name());
    }

    static void setEnabledForTest(boolean on) {
        enabled = on;
    }

    static void resetForTest() {
        enabled = true;
        cardSupplier = JeiCategoryIndex::defaultCards;
        INSTANCE.clear();
    }

    static void harnessInstallReady(Map<Item, LinkedHashSet<ResourceLocation>> map) {
        INSTANCE.clear();
        if (map != null) {
            INSTANCE.itemToCategories.putAll(map);
        }
        INSTANCE.ready = true;
        INSTANCE.building = false;
    }

    static void harnessSetCardSupplier(CardSupplier supplier) {
        cardSupplier = supplier == null ? JeiCategoryIndex::defaultCards : supplier;
    }
}
