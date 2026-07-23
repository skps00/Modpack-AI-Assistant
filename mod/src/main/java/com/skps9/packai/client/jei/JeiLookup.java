package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.CraftPriority;
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
    /** Many near-identical recipes sharing spam outputs → treat category as universal. */
    private static final int UNIVERSAL_MIN_RAW = 20;
    private static final int UNIVERSAL_SAME_OUT_PCT = 80;

    private JeiLookup() {}

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
            for (Object recipe : found) {
                try {
                    IIngredientSupplier supplier = recipes.getRecipeIngredients(cat, recipe);
                    if (!JeiFocusMatch.roleMatchesFocus(supplier, focusStack, matchRole)) {
                        continue;
                    }
                    if (involvesSpamItem(supplier)) {
                        spam++;
                        bumpOutIds(outIdCounts, supplier);
                        continue;
                    }
                    unique.add(formatRecipe(recipe, supplier, catTitle, lang));
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
            String header = ReplyLang.jeiCatCount(
                    lang, catTitle, useful, unique.size() != useful ? unique.size() : null, spam, hitCap, MAX_SCAN_PER_CAT);
            if (header.endsWith("\n")) {
                header = header.substring(0, header.length() - 1);
            }
            section.append(header).append("：\n");
            for (String detail : unique) {
                section.append("  - ").append(detail).append('\n');
            }
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

    private static String formatRecipe(Object recipe, IIngredientSupplier supplier, String catTitle, String lang) {
        List<String> inputs = labelsFromRecipeOrSupplier(recipe, supplier, RecipeIngredientRole.INPUT, 8);
        List<String> outputs = labels(supplier.getIngredients(RecipeIngredientRole.OUTPUT), 4);
        List<String> catalysts = labels(supplier.getIngredients(RecipeIngredientRole.CATALYST), 2);
        String join = ReplyLang.sourceJoin(lang);
        String in = inputs.isEmpty() ? ReplyLang.jeiNoMats(lang) : String.join(join, inputs);
        String out = outputs.isEmpty() ? ReplyLang.jeiNoOut(lang) : String.join(join, outputs);
        if (!catalysts.isEmpty()) {
            return ReplyLang.jeiMachineLine(lang, String.join(join, catalysts), in, out);
        }
        return in + " → " + out;
    }

    private static List<String> labelsFromRecipeOrSupplier(
            Object recipe,
            IIngredientSupplier supplier,
            RecipeIngredientRole role,
            int max
    ) {
        List<net.minecraft.world.item.crafting.Ingredient> crafting =
                role == RecipeIngredientRole.INPUT ? craftingIngredients(recipe) : null;
        if (crafting != null && !crafting.isEmpty()) {
            LinkedHashSet<String> uniq = new LinkedHashSet<>();
            String lang = ReplyLang.current();
            for (net.minecraft.world.item.crafting.Ingredient ing : crafting) {
                if (uniq.size() >= max) {
                    break;
                }
                if (ing == null || ing.isEmpty()) {
                    continue;
                }
                String label = IngredientReqHints.labelForIngredient(ing, lang);
                if (!label.isEmpty()) {
                    uniq.add(label);
                }
            }
            if (!uniq.isEmpty()) {
                return new ArrayList<>(uniq);
            }
        }
        return labels(supplier.getIngredients(role), max);
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

    private static List<String> labels(List<ITypedIngredient<?>> ingredients, int max) {
        Set<String> uniq = new LinkedHashSet<>();
        String lang = ReplyLang.current();
        boolean nbtMatters = !"never".equals(PackAiConfig.ingredientNbtPolicy());
        for (ITypedIngredient<?> typed : ingredients) {
            if (uniq.size() >= max) {
                break;
            }
            Optional<ItemStack> stack = typed.getItemStack();
            if (stack.isPresent() && !stack.get().isEmpty()) {
                // Without Ingredient: still filter sample noise via skip patterns / tooltip flag.
                uniq.add(IngredientReqHints.richLabel(stack.get(), lang, nbtMatters));
            }
        }
        return new ArrayList<>(uniq);
    }
}
