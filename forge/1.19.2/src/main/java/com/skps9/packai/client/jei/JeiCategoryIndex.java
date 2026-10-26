package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

import com.skps9.packai.logic.AskLoopState;
import com.skps9.packai.logic.AskToolEnv;
import com.skps9.packai.logic.ItemVariantKeys;
import com.skps9.packai.logic.RecipeCard;

/**
 * Lightweight item (output) → JEI category uid index. No recipe bodies stored.
 * Built incrementally on the client thread; queries scan only indexed categories.
 */
public final class JeiCategoryIndex {
    public static final JeiCategoryIndex INSTANCE = new JeiCategoryIndex();

    public static final int MAX_CATEGORIES = 512;
    public static final int MAX_RECIPES = 400_000;
    public static final int MAX_REFOCUS_CATEGORIES = 6;
    public static final int MAX_SAMPLES_PER_CATEGORY = 8;
    private static final Pattern VARIETY_WORD = Pattern.compile("[A-Za-z]{4,}");
    private static final long TICK_BUDGET_NS = 6_000_000L;
    private static final long REFOCUS_DEADLINE_MS = 5_000L;

    private static volatile boolean enabled = true;
    private static volatile boolean refocusEnabled = true;
    private static volatile boolean refocusNamespaceFirst = true;
    private static volatile boolean refocusVariantMatchEnabled = true;
    private static String harnessRefocusMatchText;
    private static Item harnessRefocusItem;
    private static String harnessRefocusItemId;
    private static LongSupplier nanoTimeSource = System::nanoTime;
    /** Test harness: add to fake clock after each indexed recipe step. */
    private static long harnessNanoAdvancePerStep;
    /** Test harness: when false, {@link #forceBuildComplete} does not drain the build. */
    private static boolean harnessForceBuildUnlimited = true;

    @FunctionalInterface
    interface CardSupplier {
        List<RecipeCard> cards(Item item, List<ResourceLocation> categoryUids, String role, int cap);
    }

    @FunctionalInterface
    interface RefocusScanner {
        List<RecipeCard> scan(
                ItemStack sampleStack,
                ResourceLocation categoryUid,
                String role,
                int cap,
                long deadlineMs
        );
    }

    static CardSupplier cardSupplier = JeiCategoryIndex::defaultCards;
    static RefocusScanner refocusScanner = JeiCategoryIndex::defaultRefocusScan;

    private final Map<Item, LinkedHashSet<ResourceLocation>> itemToCategories = new HashMap<>();
    private final Map<Item, Map<ResourceLocation, List<ItemStack>>> itemCategorySamples = new HashMap<>();
    private final Map<ResourceLocation, Integer> categoryRecipeCounts = new HashMap<>();

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

    private boolean harnessSynthetic;
    private int harnessSyntheticTotal;
    private int harnessSyntheticDone;

    private JeiCategoryIndex() {}

    public static long tickBudgetNanos() {
        return TICK_BUDGET_NS;
    }

    public boolean ready() {
        return ready;
    }

    public static List<RecipeCard> cardsViaIndex(Item item, String role, int cap) {
        return cardsViaIndex(item, role, cap, null);
    }

    public static List<RecipeCard> cardsViaIndex(Item item, String role, int cap, String variantMatchText) {
        return INSTANCE.query(item, role, cap, variantMatchText);
    }

    public static String refocusMatchText(AskLoopState loop, ItemStack stack) {
        return AskToolEnv.refocusMatchText(loop, stack);
    }

    public static String refocusMatchText(AskToolEnv env) {
        return env == null ? "" : env.refocusMatchText();
    }

    List<RecipeCard> query(Item item, String role, int cap) {
        return query(item, role, cap, null);
    }

    List<RecipeCard> query(Item item, String role, int cap, String variantMatchText) {
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
        String matchText = variantMatchText != null ? variantMatchText : effectiveRefocusMatchText();
        List<RecipeCard> cards;
        if (refocusEnabled) {
            cards = refocusCards(item, cats, r, cap, matchText);
        } else {
            List<ResourceLocation> ordered = List.copyOf(cats);
            cards = cardSupplier.cards(item, ordered, r, cap);
        }
        if (cards == null || cards.isEmpty()) {
            PackAiMod.LOGGER.info("Pack AI id_index miss item={} role={}", itemId, r);
            return List.of();
        }
        PackAiMod.LOGGER.info(
                "Pack AI id_index hit item={} role={} cats={} cards={}",
                itemId, r, cats.size(), cards.size());
        return cards;
    }

    private static String effectiveRefocusMatchText() {
        if (harnessRefocusMatchText != null) {
            return harnessRefocusMatchText;
        }
        return refocusMatchText(AskToolEnv.current());
    }

    record RefocusSamplePick(ItemStack stack, boolean matched) {
        String pickKind() {
            return matched ? "variant" : "first";
        }
    }

    static RefocusSamplePick pickRefocusSample(List<ItemStack> samples, String matchText) {
        if (samples == null || samples.isEmpty()) {
            return new RefocusSamplePick(null, false);
        }
        ItemStack first = null;
        for (ItemStack s : samples) {
            if (s != null && !s.isEmpty()) {
                first = s;
                break;
            }
        }
        if (first == null) {
            return new RefocusSamplePick(null, false);
        }
        if (!refocusVariantMatchEnabled || matchText == null || matchText.isBlank()) {
            return new RefocusSamplePick(first, false);
        }
        List<String> words = varietyWords(matchText);
        if (words.isEmpty()) {
            return new RefocusSamplePick(first, false);
        }
        for (ItemStack s : samples) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            String hover = s.getHoverName().getString().toLowerCase(Locale.ROOT);
            for (String w : words) {
                if (hover.contains(w)) {
                    return new RefocusSamplePick(s, s != first);
                }
            }
        }
        return new RefocusSamplePick(first, false);
    }

    static List<String> varietyWords(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Matcher m = VARIETY_WORD.matcher(text);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(out);
    }

    private List<RecipeCard> refocusCards(
            Item item, LinkedHashSet<ResourceLocation> cats, String role, int cap, String matchText
    ) {
        String itemId = itemId(item);
        Map<ResourceLocation, List<ItemStack>> samples = itemCategorySamples.get(item);
        String itemNs = namespaceOfItemId(itemId);
        List<ResourceLocation> allOrdered = orderRefocusCategories(itemNs, cats);
        String catsLog = allOrdered.stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.joining(", "));
        PackAiMod.LOGGER.info("Pack AI id_index candidates item={} cats=[{}]", itemId, catsLog);
        List<ResourceLocation> ordered = allOrdered.stream()
                .limit(MAX_REFOCUS_CATEGORIES)
                .toList();
        long deadline = System.currentTimeMillis() + REFOCUS_DEADLINE_MS;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();
        for (ResourceLocation uid : ordered) {
            if (out.size() >= cap) {
                break;
            }
            List<ItemStack> sampleList = samples == null ? null : samples.get(uid);
            RefocusSamplePick pick = pickRefocusSample(sampleList, matchText);
            ItemStack sample = pick.stack();
            if (sample == null || sample.isEmpty()) {
                continue;
            }
            List<RecipeCard> batch;
            try {
                batch = refocusScanner.scan(sample, uid, role, cap - out.size(), deadline);
            } catch (Throwable t) {
                PackAiMod.LOGGER.debug(
                        "Pack AI id_index refocus skipped item={} cat={}: {}", itemId, uid, t.toString());
                continue;
            }
            if (batch == null || batch.isEmpty()) {
                continue;
            }
            String sampleHover = sample.getHoverName().getString();
            PackAiMod.LOGGER.info(
                    "Pack AI id_index refocus item={} cat={} sample={} matched={} pick={} cards={}",
                    itemId, uid, sampleHover, pick.matched(), pick.pickKind(), batch.size());
            for (RecipeCard c : batch) {
                if (c == null || c.isEmpty() || out.size() >= cap) {
                    continue;
                }
                String key = recipeIdKey(c);
                if (!key.isEmpty() && !seen.add(key)) {
                    continue;
                }
                RecipeCard withSrc = c.sourceItemId() == null || c.sourceItemId().isBlank()
                        ? c.withSourceItemId(itemId) : c;
                out.add(withSrc);
            }
            break;
        }
        if (out.isEmpty()) {
            return List.of();
        }
        out.sort(stableCardOrder());
        return List.copyOf(out);
    }

    private List<ResourceLocation> orderRefocusCategories(String itemNs, LinkedHashSet<ResourceLocation> cats) {
        Comparator<ResourceLocation> byRecipeCount = Comparator.comparingInt(uid ->
                categoryRecipeCounts.getOrDefault(uid, Integer.MAX_VALUE));
        Comparator<ResourceLocation> cmp = refocusNamespaceFirst
                ? Comparator.comparingInt((ResourceLocation uid) -> sameNamespace(itemNs, uid) ? 0 : 1)
                        .thenComparing(byRecipeCount)
                : byRecipeCount;
        return cats.stream().sorted(cmp).toList();
    }

    private static boolean sameNamespace(String itemNs, ResourceLocation categoryUid) {
        if (itemNs == null || itemNs.isEmpty() || categoryUid == null) {
            return false;
        }
        return itemNs.equals(categoryUid.getNamespace());
    }

    private static String namespaceOfItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return "";
        }
        int colon = itemId.indexOf(':');
        return colon < 0 ? itemId : itemId.substring(0, colon);
    }

    private static List<RecipeCard> defaultRefocusScan(
            ItemStack sampleStack,
            ResourceLocation categoryUid,
            String role,
            int cap,
            long deadlineMs
    ) {
        if (sampleStack == null || sampleStack.isEmpty() || categoryUid == null || cap <= 0) {
            return List.of();
        }
        RecipeIngredientRole jeiRole = "uses".equals(role)
                ? RecipeIngredientRole.INPUT
                : RecipeIngredientRole.OUTPUT;
        int maxOut = "uses".equals(role) ? 0 : cap;
        int maxIn = "uses".equals(role) ? cap : 0;
        try {
            List<RecipeCard> pool = AskJeiClient.recipeCardsForItem(
                    sampleStack.copy(), maxOut, maxIn, deadlineMs);
            if (pool == null || pool.isEmpty()) {
                return List.of();
            }
            String want = categoryUid.toString().toLowerCase(Locale.ROOT);
            List<RecipeCard> out = new ArrayList<>();
            for (RecipeCard c : pool) {
                if (c == null || c.isEmpty() || out.size() >= cap) {
                    continue;
                }
                if (recipeCardCategoryUid(c).equals(want)) {
                    out.add(c);
                }
            }
            return List.copyOf(out);
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static String recipeCardCategoryUid(RecipeCard card) {
        if (card == null) {
            return "";
        }
        Object layout = card.jeiLayout();
        if (layout != null) {
            try {
                if (layout instanceof mezz.jei.api.gui.IRecipeLayoutDrawable drawable) {
                    var cat = drawable.getRecipeCategory();
                    if (cat != null) {
                        String uid = JeiCategoryCatalog.categoryUid(cat);
                        if (uid != null && !uid.isBlank()) {
                            return uid.toLowerCase(Locale.ROOT);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // fall through to title
            }
        }
        String title = card.categoryTitle();
        return title == null ? "" : title.toLowerCase(Locale.ROOT);
    }

    private static String recipeIdKey(RecipeCard c) {
        if (c == null) {
            return "";
        }
        String cat = c.categoryTitle() == null ? "" : c.categoryTitle();
        String out = c.primaryOutputId() == null ? "" : c.primaryOutputId();
        String src = c.sourceItemId() == null ? "" : c.sourceItemId();
        return (cat + "|" + out + "|" + src + "|" + c.focusRole()).toLowerCase(Locale.ROOT);
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
        itemCategorySamples.clear();
        categoryRecipeCounts.clear();
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
        harnessSynthetic = false;
        harnessSyntheticTotal = 0;
        harnessSyntheticDone = 0;
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

    /**
     * Client thread: autotest-only synchronous drain (no per-tick budget).
     * Normal gameplay must never call this.
     */
    public void forceBuildComplete(IRecipeManager rm, IIngredientManager ingredients) {
        requestBuild(rm, ingredients);
        runForceBuildDrain();
    }

    private void runForceBuildDrain() {
        long startMs = System.currentTimeMillis();
        drainBuildUnbudgeted();
        long ms = Math.max(0L, System.currentTimeMillis() - startMs);
        PackAiMod.LOGGER.info(
                "Pack AI id_index force_build recipes={} ms={}", recipesIndexed, ms);
    }

    private void drainBuildUnbudgeted() {
        if (!harnessForceBuildUnlimited) {
            return;
        }
        while (building) {
            if (!stepOneRecipe()) {
                finishBuild();
                break;
            }
        }
    }

    /** Client thread END tick slice. */
    public void tick() {
        if (!building) {
            return;
        }
        long t0 = nanoTimeSource.getAsLong();
        while (building) {
            if (nanoTimeSource.getAsLong() - t0 >= TICK_BUDGET_NS) {
                break;
            }
            if (!stepOneRecipe()) {
                finishBuild();
                break;
            }
        }
    }

    private boolean stepOneRecipe() {
        if (harnessSynthetic) {
            if (harnessSyntheticDone >= harnessSyntheticTotal) {
                return false;
            }
            harnessSyntheticDone++;
            recipesIndexed++;
            if (harnessNanoAdvancePerStep > 0L) {
                harnessAdvanceNano(harnessNanoAdvancePerStep);
            }
            return harnessSyntheticDone < harnessSyntheticTotal;
        }
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
        if (harnessNanoAdvancePerStep > 0L) {
            harnessAdvanceNano(harnessNanoAdvancePerStep);
        }
        return true;
    }

    private static long harnessNano;

    private static void harnessAdvanceNano(long delta) {
        if (delta <= 0L) {
            return;
        }
        harnessNano += delta;
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
            if (uid != null) {
                categoryRecipeCounts.put(uid, found.size());
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
                    noteOutput(out.getItem(), uid, out);
                }
                return;
            }
            JeiRecipeLayoutCollector.CollectedLayout layout = JeiRecipeLayoutCollector.collect(
                    category, recipe, buildIngredients);
            for (ItemStack stack : layout.itemStacks(RecipeIngredientRole.OUTPUT)) {
                if (!stack.isEmpty()) {
                    noteOutput(stack.getItem(), uid, stack);
                }
            }
        } catch (Throwable ignored) {
            // per-recipe failure — skip
        }
    }

    private void noteOutput(Item item, ResourceLocation categoryUid, ItemStack outputStack) {
        if (item == null || categoryUid == null) {
            return;
        }
        itemToCategories
                .computeIfAbsent(item, k -> new LinkedHashSet<>())
                .add(categoryUid);
        if (outputStack == null || outputStack.isEmpty()) {
            return;
        }
        Map<ResourceLocation, List<ItemStack>> byCat = itemCategorySamples
                .computeIfAbsent(item, k -> new HashMap<>());
        List<ItemStack> list = byCat.computeIfAbsent(categoryUid, k -> new ArrayList<>());
        if (list.size() >= MAX_SAMPLES_PER_CATEGORY) {
            return;
        }
        String hoverKey = outputStack.getHoverName().getString().toLowerCase(Locale.ROOT);
        for (ItemStack existing : list) {
            if (existing.getHoverName().getString().toLowerCase(Locale.ROOT).equals(hoverKey)) {
                return;
            }
        }
        list.add(outputStack.copy());
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
        if (harnessRefocusItem != null && item == harnessRefocusItem && harnessRefocusItemId != null) {
            return harnessRefocusItemId;
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
        refocusEnabled = true;
        refocusNamespaceFirst = true;
        refocusVariantMatchEnabled = true;
        harnessRefocusMatchText = null;
        harnessRefocusItem = null;
        harnessRefocusItemId = null;
        cardSupplier = JeiCategoryIndex::defaultCards;
        refocusScanner = JeiCategoryIndex::defaultRefocusScan;
        nanoTimeSource = System::nanoTime;
        harnessNanoAdvancePerStep = 0L;
        harnessNano = 0L;
        harnessForceBuildUnlimited = true;
        INSTANCE.clear();
    }

    static void harnessUseAdvancingNanoClock(long advancePerStepNanos) {
        harnessNano = 0L;
        harnessNanoAdvancePerStep = advancePerStepNanos;
        nanoTimeSource = () -> harnessNano;
    }

    static void harnessSetForceBuildUnlimited(boolean unlimited) {
        harnessForceBuildUnlimited = unlimited;
    }

    static void harnessInstallSyntheticBuild(int recipeCount) {
        INSTANCE.clear();
        INSTANCE.harnessSynthetic = true;
        INSTANCE.harnessSyntheticTotal = Math.max(0, recipeCount);
        INSTANCE.harnessSyntheticDone = 0;
        INSTANCE.building = true;
        INSTANCE.ready = false;
        INSTANCE.buildStartMs = System.currentTimeMillis();
        INSTANCE.categoriesTotal = 1;
        INSTANCE.recipesIndexed = 0;
    }

    static int harnessRecipesIndexed() {
        return INSTANCE.recipesIndexed;
    }

    static boolean harnessBuilding() {
        return INSTANCE.building;
    }

    static void harnessRunForceBuildDrainForTest() {
        INSTANCE.runForceBuildDrain();
    }

    static void harnessInstallReady(Map<Item, LinkedHashSet<ResourceLocation>> map) {
        harnessInstallReady(map, Map.of(), Map.of());
    }

    static void harnessInstallReady(
            Map<Item, LinkedHashSet<ResourceLocation>> map,
            Map<Item, Map<ResourceLocation, ItemStack>> samples,
            Map<ResourceLocation, Integer> recipeCounts
    ) {
        Map<Item, Map<ResourceLocation, List<ItemStack>>> lists = new HashMap<>();
        if (samples != null) {
            for (Map.Entry<Item, Map<ResourceLocation, ItemStack>> e : samples.entrySet()) {
                Map<ResourceLocation, List<ItemStack>> perCat = new HashMap<>();
                for (Map.Entry<ResourceLocation, ItemStack> s : e.getValue().entrySet()) {
                    perCat.put(s.getKey(), new ArrayList<>(List.of(s.getValue())));
                }
                lists.put(e.getKey(), perCat);
            }
        }
        harnessInstallSampleLists(map, lists, recipeCounts);
    }

    static void harnessInstallSampleLists(
            Map<Item, LinkedHashSet<ResourceLocation>> map,
            Map<Item, Map<ResourceLocation, List<ItemStack>>> samples,
            Map<ResourceLocation, Integer> recipeCounts
    ) {
        INSTANCE.clear();
        if (map != null) {
            INSTANCE.itemToCategories.putAll(map);
        }
        if (samples != null) {
            for (Map.Entry<Item, Map<ResourceLocation, List<ItemStack>>> e : samples.entrySet()) {
                Map<ResourceLocation, List<ItemStack>> copy = new HashMap<>();
                for (Map.Entry<ResourceLocation, List<ItemStack>> s : e.getValue().entrySet()) {
                    List<ItemStack> stacks = new ArrayList<>();
                    if (s.getValue() != null) {
                        for (ItemStack st : s.getValue()) {
                            if (st != null && !st.isEmpty()) {
                                stacks.add(st.copy());
                            }
                        }
                    }
                    copy.put(s.getKey(), stacks);
                }
                INSTANCE.itemCategorySamples.put(e.getKey(), copy);
            }
        }
        if (recipeCounts != null) {
            INSTANCE.categoryRecipeCounts.putAll(recipeCounts);
        }
        INSTANCE.ready = true;
        INSTANCE.building = false;
    }

    static void harnessSetCardSupplier(CardSupplier supplier) {
        cardSupplier = supplier == null ? JeiCategoryIndex::defaultCards : supplier;
    }

    static void harnessSetRefocusScanner(RefocusScanner scanner) {
        refocusScanner = scanner == null ? JeiCategoryIndex::defaultRefocusScan : scanner;
    }

    static void harnessSetRefocusEnabled(boolean on) {
        refocusEnabled = on;
    }

    static void harnessSetRefocusNamespaceFirst(boolean on) {
        refocusNamespaceFirst = on;
    }

    static void harnessSetRefocusVariantMatchEnabled(boolean on) {
        refocusVariantMatchEnabled = on;
    }

    static void harnessSetRefocusMatchText(String text) {
        harnessRefocusMatchText = text;
    }

    static void harnessSetRefocusItemId(Item item, String id) {
        harnessRefocusItem = item;
        harnessRefocusItemId = id;
    }

    static List<ResourceLocation> harnessRefocusCategoryOrder(
            String itemId, LinkedHashSet<ResourceLocation> cats
    ) {
        return INSTANCE.orderRefocusCategories(namespaceOfItemId(itemId), cats);
    }
}
