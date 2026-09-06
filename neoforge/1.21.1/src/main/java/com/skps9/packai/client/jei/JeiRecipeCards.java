package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.CraftPriority;
import com.skps9.packai.logic.ItemVariantKeys;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.RecipeCard;
import com.skps9.packai.logic.RecipeCardAlign;
import com.skps9.packai.logic.RecipeCategoryPrefs;
import com.skps9.packai.logic.RecipeExtra;
import com.skps9.packai.logic.RecipeIoSummary;
import com.skps9.packai.logic.RecipeUnlockGates;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.IIngredientType;
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
    /** Max MAINTENANCE (anvil repair/enchant/grindstone-style) cards per item. Optional tier — never displaces normal OUTPUT/INPUT cards. */
    private static final int MAX_MAINTENANCE = 2;
    static final int MAX_FLOW_INPUT_SLOTS = 81;
    static final int MAX_CRAFTING_3X3_SLOTS = 9;
    /** JEI slot stride in px (item 16 + 2 padding). Used to detect multi-cell layouts. */
    static final int JEI_SLOT_STRIDE = 18;

    private JeiRecipeCards() {}

    /** Normal cards + trailing optional MAINTENANCE cards (anvil repair/enchant/grindstone self-recipes). */
    public record ItemParts(List<RecipeCard> normal, List<RecipeCard> maintenance) {}

    public static List<RecipeCard> forItem(ItemStack stack) {
        return forItem(stack, DEFAULT_MAX_CARDS, DEFAULT_MAX_CARDS);
    }

    /** Same cap for OUTPUT and INPUT (compat). */
    public static List<RecipeCard> forItem(ItemStack stack, int maxCards) {
        return forItem(stack, maxCards, maxCards);
    }

    /**
     * @param maxOutput max OUTPUT (obtain) cards
     * @param maxInput  max INPUT (uses) cards
     */
    public static List<RecipeCard> forItem(ItemStack stack, int maxOutput, int maxInput) {
        return forItemParts(stack, maxOutput, maxInput).normal();
    }

    /**
     * @param maxOutput max OUTPUT (obtain) cards
     * @param maxInput  max INPUT (uses) cards
     */
    public static ItemParts forItemParts(ItemStack stack, int maxOutput, int maxInput) {
        if (stack == null || stack.isEmpty() || (maxOutput <= 0 && maxInput <= 0)) {
            return new ItemParts(List.of(), List.of());
        }
        // Ask focus may carry inventory stack size; recipe UI must use unit count.
        ItemStack unit = stack.copy();
        if (unit.getCount() != 1) {
            unit.setCount(1);
        }
        List<RecipeCard> fromJei = List.of();
        List<RecipeCard> maintenance = List.of();
        if (ModList.get().isLoaded("jei")) {
            try {
                fromJei = collect(unit, maxOutput, maxInput);
            } catch (NoClassDefFoundError | Exception e) {
                PackAiMod.LOGGER.debug("JEI recipe cards skipped: {}", e.toString());
            }
            try {
                maintenance = collectMaintenance(unit, MAX_MAINTENANCE, new LinkedHashSet<>());
            } catch (NoClassDefFoundError | Exception e) {
                PackAiMod.LOGGER.debug("JEI maintenance cards skipped: {}", e.toString());
                maintenance = List.of();
            }
        }
        // ponytail: Quests/Analyzer can fill JEI first → still merge vanilla craft if missing
        List<RecipeCard> raw = ensureCoreCraft(unit, fromJei, maxOutput, maxInput);
        List<RecipeCard> normal = tagSource(mergeVanillaUses(unit, raw, maxOutput, maxInput), unit);
        return new ItemParts(normal, maintenance);
    }

    /** Max cards = OUTPUT cap + INPUT cap. */
    private static int totalCap(int maxOutput, int maxInput) {
        return Math.max(0, maxOutput) + Math.max(0, maxInput);
    }

    /**
     * If JEI returned only Quests/Analyzer (or empty), prepend vanilla crafting cards
     * so multi-select axes still get a 3×3 under their section.
     */
    static List<RecipeCard> ensureCoreCraft(
            ItemStack stack, List<RecipeCard> fromJei, int maxOutput, int maxInput
    ) {
        List<RecipeCard> jei = fromJei == null ? List.of() : fromJei;
        int cap = totalCap(maxOutput, maxInput);
        boolean hasCore = false;
        for (RecipeCard c : jei) {
            if (c != null && CraftPriority.isCoreCraftCategory(c.categoryTitle())) {
                hasCore = true;
                break;
            }
        }
        if (hasCore) {
            // JEI may keep CRAFTING_3X3 after failed createRecipeLayoutDrawable; try vanilla+attach.
            return upgradeCraftingLayouts(stack, jei, maxOutput, maxInput);
        }
        List<RecipeCard> vanilla = fromVanillaCrafting(stack, maxOutput);
        if (vanilla.isEmpty()) {
            return jei.size() > cap ? List.copyOf(jei.subList(0, cap)) : List.copyOf(jei);
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();
        // Vanilla OUTPUT cards must not consume INPUT budget (merge must not steal INPUT slots).
        int vanillaCap = Math.min(cap, Math.max(0, maxOutput));
        for (RecipeCard c : vanilla) {
            if (out.size() >= vanillaCap || c == null || c.isEmpty()) {
                continue;
            }
            if (seen.add(signature(c))) {
                out.add(c);
            }
        }
        for (RecipeCard c : jei) {
            if (out.size() >= cap || c == null || c.isEmpty()) {
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
    private static List<RecipeCard> collect(ItemStack stack, int maxOutput, int maxInput) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();
        // Per-role budget: OUTPUT (obtain) and INPUT (uses) use separate caps.
        out.addAll(collectRole(stack, RecipeIngredientRole.OUTPUT, maxOutput, seen));
        out.addAll(collectRole(stack, RecipeIngredientRole.INPUT, maxInput, seen));
        return List.copyOf(out);
    }

    /** OUTPUT/INPUT cards for a JEI typed ingredient (entity, gas, …) — not an ItemStack. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<RecipeCard> forTyped(IIngredientType type, Object ingredient, int maxOutput, int maxInput) {
        if (type == null || ingredient == null || (maxOutput <= 0 && maxInput <= 0)) {
            return List.of();
        }
        if (!ModList.get().isLoaded("jei")) {
            return List.of();
        }
        try {
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            List<RecipeCard> out = new ArrayList<>();
            if (maxOutput > 0) {
                out.addAll(collectRole(ItemStack.EMPTY, RecipeIngredientRole.OUTPUT, maxOutput, seen,
                        typedFocus(RecipeIngredientRole.OUTPUT, type, ingredient)));
            }
            if (maxInput > 0) {
                out.addAll(collectRole(ItemStack.EMPTY, RecipeIngredientRole.INPUT, maxInput, seen,
                        typedFocus(RecipeIngredientRole.INPUT, type, ingredient)));
            }
            return List.copyOf(out);
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI typed recipe cards skipped: {}", e.toString());
            return List.of();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IFocus<?> typedFocus(RecipeIngredientRole role, IIngredientType type, Object ingredient) {
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty() || type == null || ingredient == null || role == null) {
            return null;
        }
        return opt.get().getJeiHelpers().getFocusFactory().createFocus(role, type, ingredient);
    }

    private static List<RecipeCard> collectRole(
            ItemStack stack,
            RecipeIngredientRole role,
            int maxCards,
            LinkedHashSet<String> seen
    ) {
        return collectRole(stack, role, maxCards, seen, null, false);
    }

    private static List<RecipeCard> collectRole(
            ItemStack stack,
            RecipeIngredientRole role,
            int maxCards,
            LinkedHashSet<String> seen,
            IFocus<?> typedFocus
    ) {
        return collectRole(stack, role, maxCards, seen, typedFocus, false);
    }

    /** Maintenance-only JEI pass: anvil/repair self-recipes (OUTPUT + INPUT role scans). */
    private static List<RecipeCard> collectMaintenance(
            ItemStack stack, int maxCards, LinkedHashSet<String> seen
    ) {
        if (maxCards <= 0) {
            return List.of();
        }
        try {
            List<RecipeCard> out = new ArrayList<>(
                    collectRole(stack, RecipeIngredientRole.OUTPUT, maxCards, seen, null, true));
            if (out.size() < maxCards) {
                // JEI anvil/repair rows appear on the INPUT side (item being repaired /
                // enchanted). Scan INPUT role too (self-recipe filter still applies).
                for (RecipeCard c : collectRole(stack, RecipeIngredientRole.INPUT,
                        maxCards - out.size(), seen, null, true)) {
                    out.add(c);
                }
            }
            return out;
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI maintenance cards skipped: {}", e.toString());
            return List.of();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeCard> collectRole(
            ItemStack stack,
            RecipeIngredientRole role,
            int maxCards,
            LinkedHashSet<String> seen,
            IFocus<?> typedFocus,
            boolean upgradeOnly
    ) {
        if (maxCards <= 0 || role == null) {
            return List.of();
        }
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return List.of();
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IIngredientManager ingredients = runtime.getIngredientManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
        IFocus<?> focus = typedFocus;
        if (focus == null) {
            if (stack == null || stack.isEmpty()) {
                return List.of();
            }
            focus = focuses.createFocus(role, VanillaTypes.ITEM_STACK, stack.copy());
        }
        if (stack == null) {
            stack = ItemStack.EMPTY;
        }
        RecipeCard.FocusRole cardRole = role == RecipeIngredientRole.INPUT
                ? RecipeCard.FocusRole.INPUT
                : RecipeCard.FocusRole.OUTPUT;

        List<IRecipeCategory<?>> categories = new ArrayList<>(recipes.createRecipeCategoryLookup()
                .limitFocus(List.of(focus))
                .includeHidden()
                .get()
                .toList());
        categories.removeIf(c -> RecipeCategoryPrefs.isHidden(JeiCategoryCatalog.categoryUid(c)));
        // Ask cards: ease-first (craft/loot before quest); user category drag order = tie-break.
        categories.sort(Comparator
                .comparingInt((IRecipeCategory<?> c) -> CraftPriority.askEaseBand(c.getTitle().getString()))
                .thenComparingInt(c -> RecipeCategoryPrefs.sortKey(
                        JeiCategoryCatalog.categoryUid(c), c.getTitle().getString()))
                .thenComparingInt(c -> CraftPriority.speedTier(c.getTitle().getString()))
                .thenComparing(c -> c.getTitle().getString()));

        RegistryAccess ra = registryAccess();
        List<RecipeCard> aligned = new ArrayList<>();
        List<RecipeCard> fallback = new ArrayList<>();
        LinkedHashSet<String> questSigs = new LinkedHashSet<>();
        boolean filterVariant = ItemVariantKeys.hasVariantKeys(stack);
        boolean maintProbeDone = false;

        for (IRecipeCategory<?> category : categories) {
            if (roleScanDone(role, aligned, fallback, maxCards, filterVariant)) {
                break;
            }
            IRecipeCategory cat = category;
            String catTitle = Plainify.stripMcFormat(category.getTitle().getString());
            String catUid = JeiCategoryCatalog.categoryUid(category);
            boolean questCat = CraftPriority.isQuestCategory(catTitle, catUid);
            RecipeType type = category.getRecipeType();
            if (JeiUniversalSpam.isSpamCategory(type, catTitle)) {
                continue;
            }
            // JEI shows Cooking Pot etc. via recipe-type catalysts (tab + under recipe), not setRecipe slots.
            List<ItemStack> typeCats = recipeTypeCatalysts(recipes, type, 3);

            List<?> found = recipes.createRecipeLookup(type)
                    .limitFocus(List.of(focus))
                    .includeHidden()
                    .get()
                    .limit(MAX_SCAN_PER_CAT)
                    .toList();
            if (upgradeOnly) {
                int selfRefKept = 0;
                for (Object r : found) {
                    try {
                        IIngredientSupplier tmp = recipes.getRecipeIngredients(cat, r);
                        if (JeiFocusMatch.focusAppearsAsInputAndOutput(tmp, stack)) {
                            selfRefKept++;
                        }
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
                PackAiMod.LOGGER.info(
                        "Pack AI maintScan cat={} uid={} found={} selfRef={}",
                        catTitle, catUid, found.size(), selfRefKept);
                if (!maintProbeDone) {
                    maintProbeDone = true;
                    boolean catHasAnvil = categories.stream().anyMatch(x -> JeiCategoryCatalog.categoryUid(x).toLowerCase(Locale.ROOT).contains("anvil") || Plainify.stripMcFormat(x.getTitle().getString()).toLowerCase(Locale.ROOT).contains("砧"));
                    PackAiMod.LOGGER.info("Pack AI maintProbe categoriesHasAnvil={} categoriesCount={}", catHasAnvil, categories.size());
                    // Wave-14 diagnostic: why does the vanilla anvil category never appear under focus?
                    List<IRecipeCategory<?>> allCats = new ArrayList<>(recipes.createRecipeCategoryLookup()
                            .includeHidden().get().toList());
                    java.util.List<String> anvilLike = new java.util.ArrayList<>();
                    for (IRecipeCategory<?> c : allCats) {
                        String tt = Plainify.stripMcFormat(c.getTitle().getString()).toLowerCase(Locale.ROOT);
                        String uu = JeiCategoryCatalog.categoryUid(c).toLowerCase(Locale.ROOT);
                        if (tt.contains("砧") || tt.contains("anvil") || uu.contains("anvil")) {
                            anvilLike.add(JeiCategoryCatalog.categoryUid(c) + "|" + Plainify.stripMcFormat(c.getTitle().getString()));
                            try {
                                List<?> rows = recipes.createRecipeLookup(c.getRecipeType())
                                        .includeHidden().get().limit(400).toList();
                                int selfRef = 0;
                                for (Object r : rows) {
                                    try {
                                        IIngredientSupplier tmp = recipes.getRecipeIngredients((IRecipeCategory) c, r);
                                        if (tmp != null && JeiFocusMatch.focusAppearsAsInputAndOutput(tmp, stack)) {
                                            selfRef++;
                                        }
                                    } catch (Exception ignored) {
                                        // ignore
                                    }
                                }
                                PackAiMod.LOGGER.info("Pack AI maintProbe anvilType={} title={} total={} selfRef={}",
                                        JeiCategoryCatalog.categoryUid(c), Plainify.stripMcFormat(c.getTitle().getString()),
                                        rows.size(), selfRef);
                                int fSelfRef = 0;
                                int fFound = 0;
                                try {
                                    List<?> frows = recipes.createRecipeLookup(c.getRecipeType())
                                            .limitFocus(List.of(focus))
                                            .includeHidden()
                                            .get()
                                            .limit(200)
                                            .toList();
                                    fFound = frows.size();
                                    for (Object fr : frows) {
                                        try {
                                            IIngredientSupplier ftmp = recipes.getRecipeIngredients((IRecipeCategory) c, fr);
                                            if (ftmp != null && JeiFocusMatch.focusAppearsAsInputAndOutput(ftmp, stack)) {
                                                fSelfRef++;
                                            }
                                        } catch (Exception ignored) {
                                            // ignore
                                        }
                                    }
                                } catch (Exception ignored) {
                                    // ignore
                                }
                                PackAiMod.LOGGER.info("Pack AI maintProbe anvilType={} focusedFound={} fSelfRef={}",
                                        JeiCategoryCatalog.categoryUid(c), fFound, fSelfRef);
                            } catch (Exception e) {
                                PackAiMod.LOGGER.info("Pack AI maintProbe anvilType={} error={}",
                                        JeiCategoryCatalog.categoryUid(c), e.toString());
                            }
                        }
                    }
                    if (anvilLike.isEmpty()) {
                        PackAiMod.LOGGER.info("Pack AI maintProbe noAnvilLikeCategory totalCats={}",
                                allCats.size());
                    }
                }
            }

            for (Object recipe : found) {
                if (roleScanDone(role, aligned, fallback, maxCards, filterVariant)) {
                    break;
                }
                try {
                    IIngredientSupplier supplier = recipes.getRecipeIngredients(cat, recipe);
                    if (!JeiFocusMatch.roleMatchesFocus(supplier, stack, role, recipe)) {
                        continue;
                    }
                    boolean selfRef = JeiFocusMatch.focusAppearsAsInputAndOutput(supplier, stack);
                    if (upgradeOnly != selfRef) {
                        continue;
                    }
                    if (involvesSpam(supplier)) {
                        continue;
                    }
                    // Prefer JEI xy layout (SHAPED+drawable) over tryCrafting CRAFTING_3X3 smash.
                    JeiRecipeLayoutCollector.CollectedLayout layout = null;
                    try {
                        layout = JeiRecipeLayoutCollector.collect(category, recipe, ingredients);
                    } catch (Exception ignored) {
                        // fall through to tryCrafting / supplier
                    }
                    RecipeCard card = layout != null
                            ? fromLayout(layout, catTitle, ingredients, stack, typeCats)
                            : null;
                    if (card == null || card.isEmpty()) {
                        if (role == RecipeIngredientRole.OUTPUT) {
                            card = tryCrafting(recipe, catTitle, ra);
                        }
                    }
                    if (card == null || card.isEmpty()) {
                        card = fromSupplier(supplier, catTitle, ingredients, stack, typeCats);
                    }
                    if (card == null || card.isEmpty()) {
                        continue;
                    }
                    // Hard reject wrong role registry id (never keep other-mod "扳手").
                    // When layout/recipe already matched focus as OUTPUT (keep=true above),
                    // do not drop the card if smash/layout lost the item in outputs list
                    // (Create mixing / multi-output panels — Bug C enchanted_golden_apple).
                    if (role == RecipeIngredientRole.OUTPUT) {
                        if (!cardOutputMatchesFocus(card, stack) && layout == null) {
                            continue;
                        }
                    } else if (!cardInputMatchesFocus(card, stack)) {
                        continue;
                    }
                    if (upgradeOnly) {
                        RecipeCard.FocusRole selfRole = JeiCategoryCatalog.VANILLA_ANVIL_UID.equals(catUid)
                                ? RecipeCard.FocusRole.MAINTENANCE
                                : RecipeCard.FocusRole.UPGRADE;
                        card = card.withFocusRole(selfRole);
                    } else {
                        card = card.withFocusRole(cardRole);
                    }
                    card = JeiLayoutDraw.attach(
                            card, recipes, category, recipe, focuses.createFocusGroup(List.of(focus)));
                    List<String> notes = JeiReqNotes.harvest(category, recipe, card.jeiLayout());
                    if (!notes.isEmpty()) {
                        card = card.withReqNotes(notes);
                    }
                    List<String> unlocks = RecipeUnlockGates.labelsForRecipe(recipe);
                    if (!unlocks.isEmpty()) {
                        card = card.withUnlockGates(unlocks);
                    }
                    // FTB QuestCategory title is "Quests"/「任務」— real name lives on WrappedQuest.
                    if (questCat) {
                        card = applyQuestRecipeMeta(card, recipe);
                    }
                    String sig = signature(card);
                    if (seen != null && !seen.add(sig)) {
                        continue;
                    }
                    if (questCat) {
                        questSigs.add(sig);
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
        if (upgradeOnly) {
            boolean sawAnvil = false;
            for (IRecipeCategory<?> c : categories) {
                String tt = Plainify.stripMcFormat(c.getTitle().getString()).toLowerCase(Locale.ROOT);
                String uu = JeiCategoryCatalog.categoryUid(c).toLowerCase(Locale.ROOT);
                if (tt.contains("砧") || tt.contains("anvil") || uu.contains("anvil")) {
                    sawAnvil = true;
                    break;
                }
            }
            if (upgradeOnly && !sawAnvil) {
                StringBuilder titles = new StringBuilder();
                for (IRecipeCategory<?> c : categories) {
                    if (titles.length() > 0) {
                        titles.append(" | ");
                    }
                    titles.append(Plainify.stripMcFormat(c.getTitle().getString()));
                }
                PackAiMod.LOGGER.info("Pack AI maintScan noAnvilCategory totalCats={} titles={}",
                        categories.size(),
                        titles.toString());
            }
        }
        List<RecipeCard> chosen = aligned.isEmpty() ? fallback : aligned;
        chosen = dedupeMirror(chosen);
        if (role == RecipeIngredientRole.INPUT) {
            // Diversity pick runs inside chosen; return cap stays maxCards (do not inflate to 6).
            return pickWithCategoryDiversity(chosen, questSigs, maxCards);
        }
        return pickWithQuestReserve(chosen, questSigs, maxCards);
    }

    /**
     * INPUT: keep scanning categories until several distinct stations exist.
     * Ease-first otherwise fills the pool with Crafting and machines never enter.
     */
    static boolean roleScanDone(
            RecipeIngredientRole role,
            List<RecipeCard> aligned,
            List<RecipeCard> fallback,
            int maxCards,
            boolean filterVariant
    ) {
        if (role == RecipeIngredientRole.INPUT) {
            int n = aligned == null ? 0 : aligned.size();
            if (n >= Math.max(24, maxCards * 8)) {
                return true;
            }
            return distinctNonGenericCategories(aligned) >= Math.max(6, maxCards) && n >= maxCards;
        }
        int a = aligned == null ? 0 : aligned.size();
        int f = fallback == null ? 0 : fallback.size();
        return a >= maxCards * 3 && (!filterVariant || f >= maxCards * 3);
    }

    static int distinctCategories(List<RecipeCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (RecipeCard c : cards) {
            if (c == null || c.categoryTitle() == null) {
                continue;
            }
            String k = c.categoryTitle().trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty()) {
                cats.add(k);
            }
        }
        return cats.size();
    }

    /** Distinct stations excluding vanilla-like Crafting — ease-first titles must not stop the scan. */
    static int distinctNonGenericCategories(List<RecipeCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (RecipeCard c : cards) {
            if (c == null || c.categoryTitle() == null) {
                continue;
            }
            String k = c.categoryTitle().trim();
            if (k.isEmpty() || RecipeCardAlign.isGenericCraft(k)) {
                continue;
            }
            cats.add(k.toLowerCase(Locale.ROOT));
        }
        return cats.size();
    }

    /**
     * INPUT uses: do not fill the cap with vanilla Crafting. Round-robin by category
     * so machine / altar recipes stay in the pool for reply-align / show_recipe_card.
     */
    static List<RecipeCard> pickWithCategoryDiversity(
            List<RecipeCard> full, Set<String> questSigs, int maxCards
    ) {
        if (full == null || full.isEmpty() || maxCards <= 0) {
            return List.of();
        }
        if (full.size() <= maxCards) {
            return List.copyOf(full);
        }
        LinkedHashMap<String, List<RecipeCard>> byCat = new LinkedHashMap<>();
        for (RecipeCard c : full) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            String k = c.categoryTitle() == null ? "?" : c.categoryTitle().trim().toLowerCase(Locale.ROOT);
            byCat.computeIfAbsent(k, x -> new ArrayList<>()).add(c);
        }
        List<String> catOrder = new ArrayList<>(byCat.keySet());
        catOrder.sort(Comparator.comparing(RecipeCardAlign::isGenericCraft));
        List<RecipeCard> out = new ArrayList<>();
        LinkedHashSet<String> taken = new LinkedHashSet<>();
        boolean added = true;
        while (out.size() < maxCards && added) {
            added = false;
            for (String cat : catOrder) {
                if (out.size() >= maxCards) {
                    break;
                }
                List<RecipeCard> group = byCat.get(cat);
                if (group == null) {
                    continue;
                }
                for (RecipeCard c : group) {
                    if (taken.add(signature(c))) {
                        out.add(c);
                        added = true;
                        break;
                    }
                }
            }
        }
        if (questSigs != null && !questSigs.isEmpty()) {
            boolean anyQuest = false;
            for (RecipeCard c : out) {
                if (questSigs.contains(signature(c))) {
                    anyQuest = true;
                    break;
                }
            }
            if (!anyQuest) {
                for (RecipeCard c : full) {
                    if (c != null && questSigs.contains(signature(c)) && taken.add(signature(c))) {
                        if (out.size() >= maxCards) {
                            out.set(out.size() - 1, c);
                        } else {
                            out.add(c);
                        }
                        break;
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * Ease-order puts quest cats last — soft-cap often drops them. Keep up to maxCards-1
     * non-quest, then reserve 1 slot for a real quest card when any exist in the pool.
     */
    static List<RecipeCard> pickWithQuestReserve(
            List<RecipeCard> full, Set<String> questSigs, int maxCards
    ) {
        if (full == null || full.isEmpty() || maxCards <= 0) {
            return List.of();
        }
        Set<String> qsigs = questSigs == null ? Set.of() : questSigs;
        if (full.size() <= maxCards) {
            return List.copyOf(full);
        }
        List<RecipeCard> nonQuest = new ArrayList<>();
        List<RecipeCard> quest = new ArrayList<>();
        for (RecipeCard c : full) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (qsigs.contains(signature(c))) {
                quest.add(c);
            } else {
                nonQuest.add(c);
            }
        }
        if (quest.isEmpty()) {
            return List.copyOf(full.subList(0, maxCards));
        }
        List<RecipeCard> out = new ArrayList<>(maxCards);
        LinkedHashSet<String> taken = new LinkedHashSet<>();
        int nonBudget = Math.max(0, maxCards - 1);
        for (RecipeCard c : nonQuest) {
            if (out.size() >= nonBudget) {
                break;
            }
            if (taken.add(signature(c))) {
                out.add(c);
            }
        }
        for (RecipeCard c : quest) {
            if (out.size() >= maxCards) {
                break;
            }
            if (taken.add(signature(c))) {
                out.add(c);
            }
        }
        if (out.size() < maxCards) {
            for (RecipeCard c : nonQuest) {
                if (out.size() >= maxCards) {
                    break;
                }
                if (taken.add(signature(c))) {
                    out.add(c);
                }
            }
        }
        return List.copyOf(out);
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
                try {
                    ItemStack result = resultOf(holder, ra);
                    if (result.isEmpty() || !result.is(stack.getItem())) {
                        continue;
                    }
                    RecipeCard card = tryCrafting(holder, "Crafting", ra);
                    if (card == null || card.isEmpty()) {
                        continue;
                    }
                    // ensureCoreCraft bypasses JEI collect — still attach official crafting drawable.
                    card = attachJeiCraftingLayout(card, holder, stack);
                    List<String> unlocks = RecipeUnlockGates.labelsForRecipe(holder);
                    if (!unlocks.isEmpty()) {
                        card = card.withUnlockGates(unlocks);
                    }
                    if (!seen.add(signature(card))) {
                        continue;
                    }
                    out.add(card);
                } catch (Throwable t) {
                    PackAiMod.LOGGER.debug("Vanilla craft card skipped: {}", t.toString());
                }
            }
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("Vanilla crafting cards skipped: {}", e.toString());
            return List.copyOf(out);
        }
        return List.copyOf(out);
    }

    /**
     * Vanilla crafting USES (item as ingredient). JEI {@code limitFocus(INPUT)} without
     * {@code includeHidden} often drops shapeless U whose output is hidden / unindexed.
     */
    private static List<RecipeCard> fromVanillaCraftingUses(ItemStack stack, int maxCards) {
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
                try {
                    if (!JeiFocusMatch.craftingInputsAccept(holder, stack)) {
                        continue;
                    }
                    ItemStack result = resultOf(holder, ra);
                    if (!result.isEmpty() && result.is(stack.getItem())) {
                        // vanilla craft output == focus — self-use noise; never a USE card
                        continue;
                    }
                    RecipeCard card = tryCrafting(holder, "Crafting", ra);
                    if (card == null || card.isEmpty()) {
                        continue;
                    }
                    card = card.withFocusRole(RecipeCard.FocusRole.INPUT);
                    card = attachJeiCraftingLayout(card, holder, stack);
                    List<String> unlocks = RecipeUnlockGates.labelsForRecipe(holder);
                    if (!unlocks.isEmpty()) {
                        card = card.withUnlockGates(unlocks);
                    }
                    if (!seen.add(signature(card))) {
                        continue;
                    }
                    out.add(card);
                } catch (Throwable t) {
                    PackAiMod.LOGGER.debug("Vanilla craft-use card skipped: {}", t.toString());
                }
            }
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("Vanilla crafting-use cards skipped: {}", e.toString());
            return List.copyOf(out);
        }
        return List.copyOf(out);
    }

    /**
     * If JEI INPUT filled with non-craft cats (altar / quest) and skipped table U, prepend
     * vanilla shapeless/shaped uses. Does not steal OUTPUT slots.
     */
    static List<RecipeCard> mergeVanillaUses(
            ItemStack stack, List<RecipeCard> cards, int maxOutput, int maxInput
    ) {
        List<RecipeCard> src = cards == null ? List.of() : cards;
        if (maxInput <= 0) {
            return List.copyOf(src);
        }
        boolean hasCoreUse = false;
        for (RecipeCard c : src) {
            if (c != null && c.isInputUse() && CraftPriority.isCoreCraftCategory(c.categoryTitle())) {
                hasCoreUse = true;
                break;
            }
        }
        if (hasCoreUse) {
            return List.copyOf(src);
        }
        List<RecipeCard> vanilla = fromVanillaCraftingUses(stack, maxInput);
        if (vanilla.isEmpty()) {
            return List.copyOf(src);
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RecipeCard> out = new ArrayList<>();
        List<RecipeCard> existingIn = new ArrayList<>();
        for (RecipeCard c : src) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (c.isInputUse()) {
                existingIn.add(c);
            } else if (seen.add(signature(c))) {
                out.add(c);
            }
        }
        int inBudget = maxInput;
        for (RecipeCard c : vanilla) {
            if (inBudget <= 0 || c == null || c.isEmpty()) {
                continue;
            }
            if (seen.add(signature(c))) {
                out.add(c);
                inBudget--;
            }
        }
        for (RecipeCard c : existingIn) {
            if (inBudget <= 0) {
                break;
            }
            if (seen.add(signature(c))) {
                out.add(c);
                inBudget--;
            }
        }
        int cap = totalCap(maxOutput, maxInput);
        if (out.size() > cap) {
            return List.copyOf(out.subList(0, cap));
        }
        return List.copyOf(out);
    }

    /**
     * Vanilla fallback cards skip {@link #collect}; resolve JEI crafting category and attach
     * {@code IRecipeLayoutDrawable} so UI matches cooking (arrow/background), not harvest {@code ->}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecipeCard attachJeiCraftingLayout(RecipeCard card, Object recipe, ItemStack focus) {
        if (card == null || card.isEmpty() || recipe == null || JeiLayoutDraw.hasLayout(card)) {
            return card;
        }
        if (!ModList.get().isLoaded("jei")) {
            return card;
        }
        try {
            Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
            if (opt.isEmpty()) {
                return card;
            }
            IJeiRuntime runtime = opt.get();
            IRecipeManager recipes = runtime.getRecipeManager();
            IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
            RecipeIngredientRole attachRole = card.isInputUse()
                    ? RecipeIngredientRole.INPUT
                    : RecipeIngredientRole.OUTPUT;
            IFocus<ItemStack> attachFocus = focuses.createFocus(
                    attachRole, VanillaTypes.ITEM_STACK,
                    focus == null || focus.isEmpty() ? ItemStack.EMPTY : focus.copy());
            var focusGroup = focuses.createFocusGroup(List.of(attachFocus));
            for (IRecipeCategory<?> category : recipes.createRecipeCategoryLookup()
                    .includeHidden()
                    .get()
                    .toList()) {
                String title = Plainify.stripMcFormat(category.getTitle().getString());
                if (!isVanillaSizedCraftingTitle(title)) {
                    continue;
                }
                RecipeCard attached = JeiLayoutDraw.attach(
                        card, recipes, category, recipe, focusGroup);
                if (JeiLayoutDraw.hasLayout(attached)) {
                    return attached;
                }
            }
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI craft layout attach skipped: {}", e.toString());
        }
        return card;
    }

    /** Replace CRAFTING_3X3 harvest-only cards with vanilla+JEI-drawable twins when possible. */
    private static List<RecipeCard> upgradeCraftingLayouts(
            ItemStack stack, List<RecipeCard> jei, int maxOutput, int maxInput
    ) {
        int cap = totalCap(maxOutput, maxInput);
        List<RecipeCard> src = jei.size() > cap ? jei.subList(0, cap) : jei;
        boolean needs = false;
        for (RecipeCard c : src) {
            if (c != null
                    && c.layout() == RecipeCard.Layout.CRAFTING_3X3
                    && !JeiLayoutDraw.hasLayout(c)) {
                needs = true;
                break;
            }
        }
        if (!needs) {
            return List.copyOf(src);
        }
        List<RecipeCard> vanilla = fromVanillaCrafting(stack, maxOutput);
        if (vanilla.isEmpty()) {
            return List.copyOf(src);
        }
        List<RecipeCard> out = new ArrayList<>(src.size());
        for (RecipeCard c : src) {
            if (c != null
                    && c.layout() == RecipeCard.Layout.CRAFTING_3X3
                    && !JeiLayoutDraw.hasLayout(c)) {
                String sig = signature(c);
                RecipeCard better = null;
                for (RecipeCard v : vanilla) {
                    if (v != null && JeiLayoutDraw.hasLayout(v) && sig.equals(signature(v))) {
                        better = v;
                        break;
                    }
                }
                out.add(better != null ? better : c);
            } else {
                out.add(c);
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
        // JEI x/y first (vanilla crafting + cooking) so attach draws official background/arrow.
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
            List<RecipeCard.PlacedFluid> placedFluids = placedFluidsFromLayout(layout, 8);
            // Keep full catalysts on card for header icon; SHAPED UI skips footer machines.
            return RecipeCard.shaped(
                    title, placed, catalysts, outputs, fluidIn, fluidOut, otherIn, otherOut, placedFluids);
        }
        // Fallback smash into 3×3 when JEI coords useless but title looks like table crafting.
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
        title = titleWithMachine(title, catalysts);
        return RecipeCard.flow(title, inputs, catalysts, outputs, fluidIn, fluidOut, otherIn, otherOut)
                .withPlacedFluids(placedFluidsFromLayout(layout, 8));
    }

    private static List<RecipeCard.PlacedFluid> placedFluidsFromLayout(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            int max
    ) {
        List<RecipeCard.PlacedFluid> out = new ArrayList<>();
        for (JeiRecipeLayoutCollector.PlacedFluidStack p : layout.placedVisibleFluids(max)) {
            if (p == null || p.fluid() == null || p.fluid().isEmpty()) {
                continue;
            }
            out.add(new RecipeCard.PlacedFluid(
                    p.fluid(), p.x(), p.y(), p.width(), p.height(), slotKindOf(p.role())));
        }
        return out;
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
            // includeHidden: ingredientVisibility must not wipe furnace when pack hides items.
            for (ItemStack stack : recipes.createRecipeCatalystLookup(type).includeHidden().getItemStack().toList()) {
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
        // Include vanilla crafting — JEI table panel has INPUT+OUTPUT for drawable parity.
        return panel != null && panel.size() >= 2;
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
                out.add(new RecipeExtra(name, amount, tint, softId, honestResourceId(helper, ingredient)));
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
                out.add(new RecipeExtra(name, amount, tint, softId, honestResourceId(helper, ingredient)));
            } catch (Throwable ignored) {
                // skip unknown ingredient types
            }
        }
        return out;
    }

    private static String honestResourceId(IIngredientHelper helper, Object ingredient) {
        if (helper == null || ingredient == null) {
            return "";
        }
        try {
            Object loc = helper.getResourceLocation(ingredient);
            if (loc == null) {
                return "";
            }
            String s = loc.toString().trim();
            return RecipeIoSummary.looksLikeResourceId(s) ? s : "";
        } catch (Throwable ignored) {
            return "";
        }
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
        // Keep only when there is at least one output of any kind (item, fluid, or
        // other). A card with zero outputs is a broken/sample JEI layout, not a real
        // obtain path — do not let the model answer a recipe from it.
        boolean anyFluid = card.fluidOutputs() != null && !card.fluidOutputs().isEmpty();
        boolean anyOther = card.otherOutputs() != null && !card.otherOutputs().isEmpty();
        return !anyOut && (anyFluid || anyOther);
    }

    /** True when card lists focus as an input slot / grid cell (or has no item inputs). */
    private static boolean cardInputMatchesFocus(RecipeCard card, ItemStack focus) {
        if (card == null || focus == null || focus.isEmpty()) {
            return true;
        }
        boolean anyIn = false;
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3 && card.grid() != null) {
            for (ItemStack in : card.grid()) {
                if (in == null || in.isEmpty()) {
                    continue;
                }
                anyIn = true;
                if (in.is(focus.getItem())) {
                    return true;
                }
            }
        }
        if (card.placedInputs() != null) {
            for (RecipeCard.PlacedItem p : card.placedInputs()) {
                if (p == null || p.stack() == null || p.stack().isEmpty()) {
                    continue;
                }
                if (p.kind() != null && p.kind() != RecipeCard.SlotKind.INPUT) {
                    continue;
                }
                anyIn = true;
                if (p.stack().is(focus.getItem())) {
                    return true;
                }
            }
        }
        if (card.inputs() != null) {
            for (ItemStack in : card.inputs()) {
                if (in == null || in.isEmpty()) {
                    continue;
                }
                anyIn = true;
                if (in.is(focus.getItem())) {
                    return true;
                }
            }
        }
        // Fluid/soft-only or catalyst-only uses: keep when JEI already role-matched.
        return !anyIn;
    }

    /**
     * Soft-reflect FTB {@code WrappedQuest}: set {@link RecipeCard#categoryTitle()} to the quest
     * display name and {@link RecipeCard#questOpenId()} for caption / title-strip open_book.
     */
    static RecipeCard applyQuestRecipeMeta(RecipeCard card, Object recipe) {
        if (card == null || recipe == null) {
            return card;
        }
        try {
            var questField = recipe.getClass().getField("quest");
            Object quest = questField.get(recipe);
            if (quest == null) {
                return card;
            }
            Object titleComp = quest.getClass().getMethod("getTitle").invoke(quest);
            String title = "";
            if (titleComp != null) {
                Object gs = titleComp.getClass().getMethod("getString").invoke(titleComp);
                title = Plainify.stripMcFormat(gs == null ? "" : gs.toString());
            }
            Object idObj = quest.getClass().getMethod("getCodeString").invoke(quest);
            String id = idObj == null ? "" : idObj.toString().trim();
            RecipeCard out = card;
            if (!title.isBlank()) {
                out = out.withCategoryTitle(title);
            }
            if (!id.isEmpty()) {
                out = out.withQuestOpenId(id);
            }
            return out;
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("Quest recipe meta skipped: {}", t.toString());
            return card;
        }
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

    /**
     * Recipe CONTENT signature — multiset of inputs + outputs, deliberately ignoring JEI
     * layout/positions/catalysts/empty cells, so a machine replicator (動力合成器/攪拌機/
     * auto-crafter) that replays a Crafting-Table recipe collapses onto the real card.
     * Different real inputs (e.g. wood A vs wood B variants) still keep separate signatures.
     */
    private static String contentSignature(RecipeCard card) {
        java.util.TreeMap<String, Integer> freq = new java.util.TreeMap<>();
        java.util.function.Consumer<String> add = s -> {
            if (s != null && !s.isEmpty() && !"-".equals(s)) {
                freq.merge(s, 1, Integer::sum);
            }
        };
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            for (ItemStack stack : card.grid()) {
                add.accept(idOf(stack)); // '-' empties skipped by add()
            }
        } else if (card.layout() == RecipeCard.Layout.SHAPED) {
            for (RecipeCard.PlacedItem p : card.placedInputs()) {
                add.accept(idOf(p.stack()));
            }
        } else {
            for (ItemStack stack : card.inputs()) {
                add.accept(idOf(stack));
            }
        }
        for (FluidStack fluid : card.fluidInputs()) {
            add.accept(fluidId(fluid));
        }
        for (RecipeExtra other : card.otherInputs()) {
            add.accept("oi:" + other.label() + "#" + other.amount());
        }
        for (ItemStack stack : card.outputs()) {
            add.accept("o:" + idOf(stack));
        }
        for (FluidStack fluid : card.fluidOutputs()) {
            add.accept("fo:" + fluidId(fluid));
        }
        for (RecipeExtra other : card.otherOutputs()) {
            add.accept("oo:" + other.label() + "#" + other.amount());
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : freq.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('#').append(e.getValue());
        }
        return sb.toString();
    }

    /** Dedupe recipe mirrors: identical recipe content (output + inputs) shows once.
     *  Core-craft (Crafting Table etc.) wins over machine replicators; config-listed mirror
     *  categories are skipped whenever the same content exists elsewhere. Quest/loot families
     *  only dedupe among themselves (never absorbed by craft cards). */
    static List<RecipeCard> dedupeMirror(List<RecipeCard> chosen) {
        if (chosen == null || chosen.isEmpty()) {
            return List.of();
        }
        if (chosen.size() <= 1) {
            return chosen;
        }
        LinkedHashMap<String, RecipeCard> best = new LinkedHashMap<>();
        for (RecipeCard c : chosen) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            String t = c.categoryTitle() == null ? "?" : c.categoryTitle();
            String family = CraftPriority.isQuestCategory(t) ? "q"
                    : CraftPriority.isLootCategory(t) ? "l"
                    : "c";
            String key = family + '|' + contentSignature(c);
            RecipeCard old = best.get(key);
            if (old == null || betterThan(c, old)) {
                best.put(key, c);
            }
        }
        List<RecipeCard> out = new ArrayList<>();
        for (RecipeCard c : chosen) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            String t = c.categoryTitle() == null ? "?" : c.categoryTitle();
            String family = CraftPriority.isQuestCategory(t) ? "q"
                    : CraftPriority.isLootCategory(t) ? "l"
                    : "c";
            if (best.get(family + '|' + contentSignature(c)) == c) {
                out.add(c);
            }
        }
        // ---- Fix 13 diagnostic: when nothing was deduped but two cards share the same
        // ---- output multiset, they are likely craft-vs-machine mirror twins whose content
        // ---- signatures diverged. Print layout + signature so we can normalize correctly.
        int dropped = chosen.size() - out.size();
        if (dropped == 0 && chosen.size() >= 2) {
            java.util.LinkedHashMap<String, java.util.List<String>> outBucket = new java.util.LinkedHashMap<>();
            for (RecipeCard c : chosen) {
                if (c == null || c.isEmpty()) {
                    continue;
                }
                String fam = CraftPriority.isQuestCategory(c.categoryTitle()) ? "q"
                        : CraftPriority.isLootCategory(c.categoryTitle()) ? "l"
                        : "c";
                StringBuilder os = new StringBuilder(fam).append('|');
                java.util.TreeMap<String, Integer> of = new java.util.TreeMap<>();
                java.util.function.Consumer<String> addO = s -> {
                    if (s != null && !s.isEmpty() && !"-".equals(s)) {
                        of.merge(s, 1, Integer::sum);
                    }
                };
                for (ItemStack stack : c.outputs()) {
                    addO.accept(idOf(stack));
                }
                for (FluidStack fluid : c.fluidOutputs()) {
                    addO.accept(fluidId(fluid));
                }
                for (RecipeExtra other : c.otherOutputs()) {
                    addO.accept(other.label() + "#" + other.amount());
                }
                for (java.util.Map.Entry<String, Integer> e : of.entrySet()) {
                    os.append(e.getKey()).append('#').append(e.getValue()).append(';');
                }
                String key = os.toString();
                outBucket.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(
                        c.categoryTitle() + " layout=" + c.layout() + " sig=" + contentSignature(c));
            }
            for (java.util.Map.Entry<String, java.util.List<String>> e : outBucket.entrySet()) {
                if (e.getValue().size() >= 2) {
                    PackAiMod.LOGGER.info("dedupeMirror no-drop but shared-output bucket {} -> {}", e.getKey(), e.getValue());
                }
            }
        }
        return out;
    }

    /**
     * R5.3 emission-only mirror coalesce (does <b>not</b> change catalog {@link #dedupeMirror}).
     * Same family + {@link #emissionContentSignature} (count-aware ingredient+output multiset,
     * same role as catalog {@link #contentSignature} but fixes Fix-13 slot-vs-stack×N divergence)
     * → one card; losers' category titles become 「（亦可用 …）」 on the winner.
     * Different signatures stay separate cards.
     */
    public static List<RecipeCard> coalesceMirrorEmission(List<RecipeCard> chosen) {
        if (chosen == null || chosen.isEmpty()) {
            return List.of();
        }
        if (chosen.size() <= 1) {
            return chosen;
        }
        LinkedHashMap<String, RecipeCard> best = new LinkedHashMap<>();
        LinkedHashMap<String, ArrayList<String>> alsoMachines = new LinkedHashMap<>();
        for (RecipeCard c : chosen) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            String t = c.categoryTitle() == null ? "?" : c.categoryTitle();
            String family = CraftPriority.isQuestCategory(t) ? "q"
                    : CraftPriority.isLootCategory(t) ? "l"
                    : "c";
            String key = family + '|' + emissionContentSignature(c);
            RecipeCard old = best.get(key);
            if (old == null) {
                best.put(key, c);
                alsoMachines.put(key, new ArrayList<>());
            } else if (betterThan(c, old)) {
                String oldTitle = old.categoryTitle() == null ? "?" : old.categoryTitle().trim();
                ArrayList<String> also = alsoMachines.get(key);
                if (!oldTitle.isEmpty() && !"?".equals(oldTitle)) {
                    also.add(oldTitle);
                }
                best.put(key, c);
            } else {
                String nuTitle = t.trim();
                if (!nuTitle.isEmpty() && !"?".equals(nuTitle)) {
                    alsoMachines.get(key).add(nuTitle);
                }
            }
        }
        List<RecipeCard> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (RecipeCard c : chosen) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            String t = c.categoryTitle() == null ? "?" : c.categoryTitle();
            String family = CraftPriority.isQuestCategory(t) ? "q"
                    : CraftPriority.isLootCategory(t) ? "l"
                    : "c";
            String key = family + '|' + emissionContentSignature(c);
            if (best.get(key) != c || !seen.add(key)) {
                continue;
            }
            ArrayList<String> also = alsoMachines.get(key);
            if (also != null && !also.isEmpty()) {
                LinkedHashSet<String> uniq = new LinkedHashSet<>();
                String primary = c.categoryTitle() == null ? "" : c.categoryTitle().trim();
                int cut = primary.indexOf("（亦可用 ");
                if (cut > 0) {
                    primary = primary.substring(0, cut).trim();
                }
                for (String m : also) {
                    if (m == null || m.isBlank()) {
                        continue;
                    }
                    String mt = m.trim();
                    int mc = mt.indexOf("（亦可用 ");
                    if (mc > 0) {
                        mt = mt.substring(0, mc).trim();
                    }
                    if (!mt.isEmpty() && !mt.equals(primary)) {
                        uniq.add(mt);
                    }
                }
                if (!uniq.isEmpty()) {
                    c = c.withCategoryTitle(primary + "（亦可用 " + String.join("、", uniq) + "）");
                }
            }
            out.add(c);
        }
        return out;
    }

    /**
     * Emission ingredient+output multiset — same idea as {@link #contentSignature}, but
     * counts {@link ItemStack#getCount()} so two iron slots ≡ one iron×2 (Fix 13).
     */
    private static String emissionContentSignature(RecipeCard card) {
        java.util.TreeMap<String, Integer> freq = new java.util.TreeMap<>();
        java.util.function.BiConsumer<String, Integer> add = (s, n) -> {
            if (s != null && !s.isEmpty() && !"-".equals(s) && n != null && n > 0) {
                freq.merge(s, n, Integer::sum);
            }
        };
        if (card.layout() == RecipeCard.Layout.CRAFTING_3X3) {
            for (ItemStack stack : card.grid()) {
                if (stack != null && !stack.isEmpty()) {
                    add.accept(idOf(stack), Math.max(1, stack.getCount()));
                }
            }
        } else if (card.layout() == RecipeCard.Layout.SHAPED) {
            for (RecipeCard.PlacedItem p : card.placedInputs()) {
                if (p != null && p.stack() != null && !p.stack().isEmpty()) {
                    add.accept(idOf(p.stack()), Math.max(1, p.stack().getCount()));
                }
            }
        } else {
            for (ItemStack stack : card.inputs()) {
                if (stack != null && !stack.isEmpty()) {
                    add.accept(idOf(stack), Math.max(1, stack.getCount()));
                }
            }
        }
        for (FluidStack fluid : card.fluidInputs()) {
            add.accept(fluidId(fluid), 1);
        }
        for (RecipeExtra other : card.otherInputs()) {
            add.accept("oi:" + other.label() + "#" + other.amount(), 1);
        }
        for (ItemStack stack : card.outputs()) {
            if (stack != null && !stack.isEmpty()) {
                add.accept("o:" + idOf(stack), Math.max(1, stack.getCount()));
            }
        }
        for (FluidStack fluid : card.fluidOutputs()) {
            add.accept("fo:" + fluidId(fluid), 1);
        }
        for (RecipeExtra other : card.otherOutputs()) {
            add.accept("oo:" + other.label() + "#" + other.amount(), 1);
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : freq.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append('#').append(e.getValue());
        }
        return sb.toString();
    }

    /** True when {@code nu} should replace {@code old} as the canonical card for a content sig.
     *  Config-listed mirror loses to any non-mirror; core-craft beats non-core; ties keep earlier. */
    private static boolean betterThan(RecipeCard nu, RecipeCard old) {
        String nt = nu.categoryTitle() == null ? "?" : nu.categoryTitle();
        String ot = old.categoryTitle() == null ? "?" : old.categoryTitle();
        boolean nuMirror = PackAiConfig.isMirrorReplicatorCategory(nt);
        boolean oldMirror = PackAiConfig.isMirrorReplicatorCategory(ot);
        if (oldMirror && !nuMirror) {
            return true;
        }
        if (!oldMirror && nuMirror) {
            return false;
        }
        boolean nuCore = CraftPriority.isCoreCraftCategory(nt);
        boolean oldCore = CraftPriority.isCoreCraftCategory(ot);
        return !oldCore && nuCore;
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
