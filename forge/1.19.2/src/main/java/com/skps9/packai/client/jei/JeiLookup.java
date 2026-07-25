package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.CraftPriority;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.RecipeCategoryPrefs;
import com.skps9.packai.logic.ReplyLang;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fml.ModList;

/**
 * JEI 11 R/U/catalyst summary for LLM (best-effort without IIngredientSupplier).
 */
public final class JeiLookup {
    private static final int MAX_SCAN_PER_CAT = 200;
    private static final int MAX_LINES_PER_SECTION = 24;

    private JeiLookup() {}

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

    @SuppressWarnings({"rawtypes", "unchecked"})
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
        var key = Registry.ITEM.getKey(stack.getItem());
        if (key != null) {
            itemId = key.toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ReplyLang.jeiHeader(lang, itemName, itemId, JeiUniversalSpam.skipReasonLabel(lang)));
        sb.append(CraftPriority.preferenceHint(lang)).append('\n');

        int[] totals = {0, 0};
        appendSection(sb, recipes, asOutput, stack, ReplyLang.jeiSectionRecipes(lang), totals, lang);
        appendSection(sb, recipes, asInput, stack, ReplyLang.jeiSectionUses(lang), totals, lang);
        appendSection(sb, recipes, asCatalyst, stack, ReplyLang.jeiSectionCatalyst(lang), totals, lang);

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
            ItemStack stack,
            String sectionTitle,
            int[] totals,
            String lang
    ) {
        List<IRecipeCategory<?>> categories = new ArrayList<>(recipes.createRecipeCategoryLookup()
                .limitFocus(List.of(focus))
                .get()
                .toList());
        categories.removeIf(c -> RecipeCategoryPrefs.isHidden(JeiCategoryCatalog.categoryUid(c)));
        categories.sort(Comparator
                .comparingInt((IRecipeCategory<?> c) -> RecipeCategoryPrefs.sortKey(
                        JeiCategoryCatalog.categoryUid(c), c.getTitle().getString()))
                .thenComparingInt(c -> CraftPriority.speedTier(c.getTitle().getString()))
                .thenComparing(c -> c.getTitle().getString()));

        int lines = 0;
        boolean header = false;
        for (IRecipeCategory<?> category : categories) {
            String catTitle = Plainify.stripMcFormat(category.getTitle().getString());
            RecipeType type = category.getRecipeType();
            if (JeiUniversalSpam.isSpamCategory(type, catTitle)) {
                totals[1]++;
                continue;
            }
            List<?> found = recipes.createRecipeLookup(type)
                    .limitFocus(List.of(focus))
                    .get()
                    .limit(MAX_SCAN_PER_CAT)
                    .toList();
            if (found.isEmpty()) {
                continue;
            }
            if (!header) {
                sb.append('\n').append(sectionTitle).append('\n');
                header = true;
            }
            int useful = 0;
            for (Object recipe : found) {
                if (lines >= MAX_LINES_PER_SECTION) {
                    break;
                }
                String line = formatRecipe(recipe, catTitle, lang);
                if (line == null) {
                    totals[1]++;
                    continue;
                }
                sb.append("- ").append(line).append('\n');
                useful++;
                totals[0]++;
                lines++;
            }
            if (useful == 0 && !found.isEmpty()) {
                sb.append("- ").append(catTitle).append(" ×").append(found.size()).append('\n');
                totals[0]++;
                lines++;
            }
        }
    }

    private static String formatRecipe(Object recipe, String catTitle, String lang) {
        if (recipe instanceof CraftingRecipe crafting) {
            try {
                ItemStack result = crafting.getResultItem();
                String outName = result == null || result.isEmpty()
                        ? "?"
                        : Plainify.stripMcFormat(result.getHoverName().getString());
                Set<String> ins = new LinkedHashSet<>();
                for (Ingredient ing : crafting.getIngredients()) {
                    String lab = IngredientReqHints.labelForIngredient(ing, lang);
                    if (lab != null && !lab.isBlank()) {
                        ins.add(lab);
                    }
                }
                if (ins.isEmpty()) {
                    return catTitle + " → " + outName;
                }
                return catTitle + ": " + String.join(ReplyLang.sourceJoin(lang), ins) + " → " + outName;
            } catch (Exception e) {
                return catTitle;
            }
        }
        return catTitle;
    }
}
