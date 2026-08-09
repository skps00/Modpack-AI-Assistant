package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.CraftPriority;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.RecipeCategoryPrefs;
import com.skps9.packai.logic.ReplyLang;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Full JEI scan (R / U / catalyst), then compact text for the LLM.
 * Skips universal per-block spam (facades, framed blocks, covers, …).
 */
public final class JeiLookup {
    private static final int MAX_SCAN_PER_CAT = 2000;
    /** Cap ingredient lines listed per category in the LLM JEI block (rest → see JEI). */
    static final int MAX_LISTED_PER_CAT = 3;
    static final int MAX_INPUT_LABELS_SMALL = 8;
    /** Unique Name×N lines for large grids (Create 9×9 unique types). */
    static final int MAX_INPUT_LABELS_LARGE = 81;
    /** Many near-identical recipes sharing spam outputs → treat category as universal. */
    private static final int UNIVERSAL_MIN_RAW = 20;
    private static final int UNIVERSAL_SAME_OUT_PCT = 80;

    private JeiLookup() {}

    /**
     * Cap recipe detail lines for the LLM. Prefer shorter lines first (simpler crafts).
     * When truncated, appends {@code moreLine} (already localized).
     */
    public static List<String> capListedDetails(List<String> details, int max, String moreLine) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        int cap = Math.max(1, max);
        List<String> sorted = new ArrayList<>(details);
        sorted.sort(Comparator.comparingInt(String::length).thenComparing(s -> s));
        if (sorted.size() <= cap) {
            return List.copyOf(sorted);
        }
        List<String> out = new ArrayList<>(sorted.subList(0, cap));
        if (moreLine != null && !moreLine.isBlank()) {
            out.add(moreLine);
        }
        return out;
    }

    /**
     * @return plain-text JEI facts for the LLM, or null if JEI unavailable / empty stack
     */
    public static String summarize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!ModList.get().isLoaded("jei")) {
            return null;
        }
        try {
            return summarizeUnsafe(stack);
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI lookup skipped: {}", e.toString());
            return null;
        }
    }

    /** True when JEI lists this stack as a recipe-type / layout catalyst (machine / workstation). */
    public static boolean isUsedAsCatalyst(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ModList.get().isLoaded("jei")) {
            return false;
        }
        try {
            return isUsedAsCatalystUnsafe(stack);
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI catalyst check skipped: {}", e.toString());
            return false;
        }
    }

    /**
     * Compact catalyst-only JEI text for the Machine section (no header totals).
     * @return null when not usable / empty
     */
    public static String machineBrief(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ModList.get().isLoaded("jei")) {
            return null;
        }
        try {
            return machineBriefUnsafe(stack);
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI machine brief skipped: {}", e.toString());
            return null;
        }
    }

    private static boolean isUsedAsCatalystUnsafe(ItemStack stack) {
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return false;
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> asCatalyst = focuses.createFocus(
                RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, stack.copy());
        List<IRecipeCategory<?>> categories = new ArrayList<>(recipes.createRecipeCategoryLookup()
                .limitFocus(List.of(asCatalyst))
                .get()
                .toList());
        for (IRecipeCategory<?> category : categories) {
            String uid = JeiCategoryCatalog.categoryUid(category);
            if (RecipeCategoryPrefs.isHidden(uid)) {
                continue;
            }
            RecipeType<?> type = category.getRecipeType();
            String catTitle = category.getTitle().getString();
            if (JeiUniversalSpam.isSpamCategory(type, catTitle)) {
                continue;
            }
            long n = recipes.createRecipeLookup(type)
                    .limitFocus(List.of(asCatalyst))
                    .get()
                    .limit(1L)
                    .count();
            if (n > 0) {
                return true;
            }
        }
        return false;
    }

    private static String machineBriefUnsafe(ItemStack stack) {
        String lang = ReplyLang.current();
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return null;
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> asCatalyst = focuses.createFocus(
                RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, stack.copy());
        StringBuilder sb = new StringBuilder();
        int[] totals = {0, 0};
        appendSection(sb, recipes, asCatalyst, stack, RecipeIngredientRole.CATALYST,
                ReplyLang.jeiSectionCatalyst(lang), totals, lang);
        if (totals[0] == 0) {
            return null;
        }
        String out = sb.toString().trim();
        // Keep Machine block short — full R/U stays in how-to-get JEI dump.
        int max = Math.min(1200, PackAiConfig.maxJeiChars());
        if (out.length() > max) {
            out = out.substring(0, max) + ReplyLang.jeiTruncated(lang, totals[0]);
        }
        return out;
    }

    private static String summarizeUnsafe(ItemStack stack) {
        String lang = ReplyLang.current();
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return null;
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();

        IFocus<ItemStack> asOutput = focuses.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack.copy());
        IFocus<ItemStack> asInput = focuses.createFocus(
                RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack.copy());
        IFocus<ItemStack> asCatalyst = focuses.createFocus(
                RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, stack.copy());

        String itemName = stack.getHoverName().getString();
        String itemId = "";
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key != null) {
            itemId = key.toString();
        }
        String skipLabel = JeiUniversalSpam.skipReasonLabel(lang);
        StringBuilder sb = new StringBuilder();
        sb.append(ReplyLang.jeiHeader(lang, itemName, itemId, skipLabel));
        sb.append(CraftPriority.preferenceHint(lang)).append('\n');

        int[] totals = {0, 0}; // useful, skipped
        appendSection(sb, recipes, asOutput, stack, RecipeIngredientRole.OUTPUT,
                ReplyLang.jeiSectionRecipes(lang), totals, lang);
        appendSection(sb, recipes, asInput, stack, RecipeIngredientRole.INPUT,
                ReplyLang.jeiSectionUses(lang), totals, lang);
        appendSection(sb, recipes, asCatalyst, stack, RecipeIngredientRole.CATALYST,
                ReplyLang.jeiSectionCatalyst(lang), totals, lang);

        if (totals[0] == 0 && totals[1] == 0) {
            return ReplyLang.jeiEmpty(lang, itemName);
        }
        if (totals[0] == 0) {
            sb.append(ReplyLang.jeiZeroUseful(lang, totals[1]));
        } else {
            sb.append(ReplyLang.jeiTotals(lang, totals[0], totals[1]));
        }

        String out = sb.toString().trim();
        int maxChars = PackAiConfig.maxJeiChars();
        if (out.length() > maxChars) {
            out = out.substring(0, maxChars) + ReplyLang.jeiTruncated(lang, totals[0]);
        }
        PackAiMod.LOGGER.debug(
                "JEI summarize {} useful={} skipped={} chars={}",
                itemId.isEmpty() ? itemName : itemId, totals[0], totals[1], out.length());
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void appendSection(
            StringBuilder sb,
            IRecipeManager recipes,
            IFocus<ItemStack> focus,
            ItemStack focusStack,
            RecipeIngredientRole matchRole,
            String title,
            int[] totals,
            String lang
    ) {
        List<IRecipeCategory<?>> categories = new ArrayList<>(recipes.createRecipeCategoryLookup()
                .limitFocus(List.of(focus))
                .get()
                .toList());
        categories.removeIf(c -> {
            String uid = JeiCategoryCatalog.categoryUid(c);
            return RecipeCategoryPrefs.isHidden(uid);
        });
        categories.sort(Comparator
                .comparingInt((IRecipeCategory<?> c) -> RecipeCategoryPrefs.sortKey(
                        JeiCategoryCatalog.categoryUid(c), c.getTitle().getString()))
                .thenComparingInt(c -> CraftPriority.speedTier(c.getTitle().getString()))
                .thenComparing(c -> c.getTitle().getString()));
        if (categories.isEmpty()) {
            return;
        }

        String skipLabel = JeiUniversalSpam.skipReasonLabel(lang);
        StringBuilder section = new StringBuilder();
        boolean anyUseful = false;
        List<String> includedCats = new ArrayList<>();
        for (IRecipeCategory<?> category : categories) {
            RecipeType type = category.getRecipeType();
            IRecipeCategory cat = category;
            String catTitle = category.getTitle().getString();

            if (JeiUniversalSpam.isSpamCategory(type, catTitle)) {
                long n = recipes.createRecipeLookup(type)
                        .limitFocus(List.of(focus))
                        .get()
                        .limit(MAX_SCAN_PER_CAT + 1L)
                        .count();
                int skipped = (int) Math.min(n, MAX_SCAN_PER_CAT);
                totals[1] += skipped;
                section.append(ReplyLang.jeiSkipped(lang, catTitle, skipped, skipLabel));
                continue;
            }

            List<?> found = recipes.createRecipeLookup(type)
                    .limitFocus(List.of(focus))
                    .get()
                    .limit(MAX_SCAN_PER_CAT + 1L)
                    .toList();
            boolean hitCap = found.size() > MAX_SCAN_PER_CAT;
            if (hitCap) {
                found = found.subList(0, MAX_SCAN_PER_CAT);
            }

            LinkedHashSet<String> unique = new LinkedHashSet<>();
            Map<String, Integer> outIdCounts = new HashMap<>();
            int spamOut = 0;
            int spam = 0;
            int useful = 0;
            List<ItemStack> typeCats = JeiRecipeCards.recipeTypeCatalysts(recipes, type, 2);
            for (Object recipe : found) {
                try {
                    IIngredientSupplier supplier = recipes.getRecipeIngredients(cat, recipe);
                    if (!JeiFocusMatch.roleMatchesFocus(supplier, focusStack, matchRole, recipe)) {
                        continue;
                    }
                    if (PackAiConfig.hideUpgradeRecipes()
                            && JeiFocusMatch.focusAppearsAsInputAndOutput(supplier, focusStack)) {
                        continue;
                    }
                    if (involvesSpamItem(supplier)) {
                        spam++;
                        bumpOutIds(outIdCounts, supplier);
                        continue;
                    }
                    unique.add(formatRecipe(recipe, supplier, catTitle, lang, focusStack, typeCats));
                    useful++;
                    bumpOutIds(outIdCounts, supplier);
                } catch (Exception e) {
                    unique.add(catTitle);
                    useful++;
                }
            }

            for (Map.Entry<String, Integer> e : outIdCounts.entrySet()) {
                if (JeiUniversalSpam.isSpamItemId(e.getKey())) {
                    spamOut += e.getValue();
                }
            }

            // Category mostly spam outputs (e.g. dozens of ae2:facade variants)
            if (found.size() >= UNIVERSAL_MIN_RAW
                    && spamOut * 100 >= found.size() * UNIVERSAL_SAME_OUT_PCT) {
                totals[1] += found.size();
                section.append(ReplyLang.jeiSkipped(lang, catTitle, found.size(), skipLabel));
                continue;
            }

            String dominant = dominantKey(outIdCounts);
            int dominantCount = dominant == null ? 0 : outIdCounts.getOrDefault(dominant, 0);
            if (found.size() >= UNIVERSAL_MIN_RAW
                    && dominant != null
                    && JeiUniversalSpam.isSpamItemId(dominant)
                    && dominantCount * 100 >= found.size() * UNIVERSAL_SAME_OUT_PCT) {
                totals[1] += found.size();
                section.append(ReplyLang.jeiSkipped(lang, catTitle, found.size(), dominant));
                continue;
            }

            totals[0] += useful;
            totals[1] += spam;
            if (useful == 0) {
                if (spam > 0) {
                    section.append(ReplyLang.jeiSkippedGeneric(lang, catTitle, spam));
                }
                continue;
            }

            anyUseful = true;
            includedCats.add(Plainify.stripMcFormat(catTitle) + "(" + useful + ")");
            String header = ReplyLang.jeiCatCount(
                    lang, catTitle, useful, unique.size() != useful ? unique.size() : null, spam, hitCap, MAX_SCAN_PER_CAT);
            if (header.endsWith("\n")) {
                header = header.substring(0, header.length() - 1);
            }
            section.append(header).append("：\n");
            int omitted = Math.max(0, unique.size() - MAX_LISTED_PER_CAT);
            String more = omitted > 0 ? ReplyLang.jeiCatMore(lang, omitted, catTitle) : null;
            for (String detail : capListedDetails(new ArrayList<>(unique), MAX_LISTED_PER_CAT, more)) {
                section.append("  - ").append(detail).append('\n');
            }
        }

        if (!includedCats.isEmpty()) {
            PackAiMod.LOGGER.debug("JEI {} cats: {}", title, String.join(", ", includedCats));
        }

        if (anyUseful || section.length() > 0) {
            sb.append(title).append("：\n").append(section);
        }
    }

    private static boolean involvesSpamItem(IIngredientSupplier supplier) {
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

    private static void bumpOutIds(Map<String, Integer> counts, IIngredientSupplier supplier) {
        for (ITypedIngredient<?> typed : supplier.getIngredients(RecipeIngredientRole.OUTPUT)) {
            Optional<ItemStack> stack = typed.getItemStack();
            if (stack.isEmpty() || stack.get().isEmpty()) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.get().getItem());
            if (key == null) {
                continue;
            }
            counts.merge(key.toString(), 1, Integer::sum);
        }
    }

    private static String dominantKey(Map<String, Integer> counts) {
        String best = null;
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > n) {
                n = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static String formatRecipe(
            Object recipe,
            IIngredientSupplier supplier,
            String catTitle,
            String lang,
            ItemStack focusStack,
            List<ItemStack> typeCatalysts
    ) {
        int inputSlots = countItemSlots(supplier, RecipeIngredientRole.INPUT);
        int maxIn = inputSlots > 9 ? MAX_INPUT_LABELS_LARGE : MAX_INPUT_LABELS_SMALL;
        List<String> inputs = labelsFromRecipeOrSupplier(
                recipe, supplier, RecipeIngredientRole.INPUT, maxIn, focusStack, inputSlots > 9);
        List<String> outputs = labels(supplier.getIngredients(RecipeIngredientRole.OUTPUT), 4, focusStack);
        List<ItemStack> layoutCats = new ArrayList<>();
        for (ITypedIngredient<?> typed : supplier.getIngredients(RecipeIngredientRole.CATALYST)) {
            Optional<ItemStack> opt = typed.getItemStack();
            if (opt.isEmpty() || opt.get().isEmpty()) {
                continue;
            }
            layoutCats.add(opt.get());
        }
        List<ItemStack> catStacks = JeiRecipeCards.mergeItemStacksById(layoutCats, typeCatalysts, 2);
        List<String> catalysts = new ArrayList<>();
        for (ItemStack stack : catStacks) {
            if (catalysts.size() >= 2) {
                break;
            }
            String name = Plainify.stripMcFormat(stack.getHoverName().getString());
            if (!name.isBlank()) {
                catalysts.add(name);
            }
        }
        String join = ReplyLang.sourceJoin(lang);
        String in = inputs.isEmpty() ? ReplyLang.jeiNoMats(lang) : String.join(join, inputs);
        String out = outputs.isEmpty() ? ReplyLang.jeiNoOut(lang) : String.join(join, outputs);
        String prefix = inputSlots > 9 ? ("[" + inputSlots + " slots] ") : "";
        if (!catalysts.isEmpty()) {
            return prefix + ReplyLang.jeiMachineLine(lang, String.join(join, catalysts), in, out);
        }
        return prefix + in + " → " + out;
    }

    private static int countItemSlots(IIngredientSupplier supplier, RecipeIngredientRole role) {
        List<ItemStack> flat = new ArrayList<>();
        for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
            Optional<ItemStack> opt = typed.getItemStack();
            if (opt.isEmpty() || opt.get().isEmpty()) {
                continue;
            }
            flat.add(opt.get());
        }
        return IngredientReqHints.collapseAlternatives(flat, ItemStack.EMPTY).size();
    }

    private static List<String> labelsFromRecipeOrSupplier(
            Object recipe,
            IIngredientSupplier supplier,
            RecipeIngredientRole role,
            int max,
            ItemStack prefer,
            boolean keepCounts
    ) {
        List<net.minecraft.world.item.crafting.Ingredient> crafting =
                role == RecipeIngredientRole.INPUT ? craftingIngredients(recipe) : null;
        if (crafting != null && !crafting.isEmpty() && !keepCounts) {
            LinkedHashSet<String> uniq = new LinkedHashSet<>();
            String lang = ReplyLang.current();
            for (net.minecraft.world.item.crafting.Ingredient ing : crafting) {
                if (uniq.size() >= max) {
                    break;
                }
                if (ing == null || ing.isEmpty()) {
                    continue;
                }
                String label = IngredientReqHints.labelForIngredient(ing, lang, prefer);
                if (!label.isEmpty()) {
                    uniq.add(label);
                }
            }
            if (!uniq.isEmpty()) {
                return new ArrayList<>(uniq);
            }
        }
        if (keepCounts) {
            return labelsKeepCounts(supplier.getIngredients(role), max, prefer);
        }
        return labels(supplier.getIngredients(role), max, prefer);
    }

    static List<String> formatCountedLabels(LinkedHashMap<String, Integer> counts, int max) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (out.size() >= max) {
                break;
            }
            int n = e.getValue() == null ? 1 : e.getValue();
            out.add(n > 1 ? e.getKey() + "×" + n : e.getKey());
        }
        return out;
    }

    private static List<String> labelsKeepCounts(List<ITypedIngredient<?>> ingredients, int max, ItemStack prefer) {
        List<ItemStack> flat = new ArrayList<>();
        for (ITypedIngredient<?> typed : ingredients) {
            Optional<ItemStack> stack = typed.getItemStack();
            if (stack.isEmpty() || stack.get().isEmpty()) {
                continue;
            }
            flat.add(stack.get().copy());
        }
        String lang = ReplyLang.current();
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        int i = 0;
        while (i < flat.size()) {
            int j = i + 1;
            while (j < flat.size() && IngredientReqHints.sharesCollapsibleTag(flat.subList(i, j + 1))) {
                j++;
            }
            String label = IngredientReqHints.labelForAlternatives(flat.subList(i, j), prefer, lang);
            if (!label.isEmpty()) {
                counts.merge(label, 1, Integer::sum);
            }
            i = j;
        }
        return formatCountedLabels(counts, max);
    }

    private static List<net.minecraft.world.item.crafting.Ingredient> craftingIngredients(Object recipe) {
        if (recipe == null) {
            return null;
        }
        try {
            Object value = recipe;
            if (recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder) {
                value = holder.value();
            }
            if (value instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting) {
                var list = crafting.getIngredients();
                if (list == null || list.isEmpty()) {
                    return null;
                }
                return List.copyOf(list);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static List<String> labels(List<ITypedIngredient<?>> ingredients, int max, ItemStack prefer) {
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        List<ItemStack> flat = new ArrayList<>();
        for (ITypedIngredient<?> typed : ingredients) {
            Optional<ItemStack> stack = typed.getItemStack();
            if (stack.isEmpty() || stack.get().isEmpty()) {
                continue;
            }
            ItemStack s = stack.get();
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(s.getItem());
            String id = key == null ? s.getHoverName().getString() : key.toString();
            if (!seenIds.add(id)) {
                continue;
            }
            flat.add(s.copy());
        }
        String lang = ReplyLang.current();
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        // Collapse consecutive tag alts into one OR-label each.
        int i = 0;
        while (i < flat.size() && uniq.size() < max) {
            int j = i + 1;
            while (j < flat.size() && IngredientReqHints.sharesCollapsibleTag(flat.subList(i, j + 1))) {
                j++;
            }
            String label = IngredientReqHints.labelForAlternatives(flat.subList(i, j), prefer, lang);
            if (!label.isEmpty()) {
                uniq.add(label);
            }
            i = j;
        }
        return new ArrayList<>(uniq);
    }
}
