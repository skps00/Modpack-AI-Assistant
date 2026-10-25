package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.ReplyLang;

import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * JEI recipes that repeat for almost every block (facades, frames, covers, camo, …).
 * Item spam: namespace + exact ids (ATM8 382 jars audit). Category titles still use path keywords.
 */
public final class JeiUniversalSpam {
    private JeiUniversalSpam() {}

    private static final String INTEGRATED_DYNAMICS_FACADE = "integrateddynamics:facade";

    /**
     * Item ids that mean “wrap / skin this block” rather than a unique recipe.
     * ATM8 382 jars: name hits on facade/framed_/cover/camo/disguise/mimic — only {@code framedblocks:*}
     * (141) is all-spam noise; {@code ae2:*_covered_cable} 36, {@code quark:*_framed_glass} 34,
     * {@code chipped:framed_*_planks} 12 (in {@code #minecraft:planks}), {@code create:*framed_glass*} 9,
     * {@code functionalstorage:framed_*} 7, botania framed 4, etc. are not spam (exact-id rule only).
     */
    public static boolean isSpamItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String s = itemId.toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String namespace = s.substring(0, colon);
        if ("framedblocks".equals(namespace)) {
            return true;
        }
        // integrateddynamics:facade (1); framedblocks:framed_cube covered by namespace rule above
        return INTEGRATED_DYNAMICS_FACADE.equals(s);
    }

    /**
     * OUTPUT / CATALYST: any spam variant discards the recipe.
     * INPUT: only when every expanded tag variant is spam (one framed plank in a plank tag must not drop the recipe).
     */
    public static boolean recipeInvolvesSpam(
            List<String> inputIds, List<String> outputIds, List<String> catalystIds) {
        if (anySpamItemId(outputIds)) {
            return true;
        }
        if (anySpamItemId(catalystIds)) {
            return true;
        }
        return inputsAllSpam(inputIds);
    }

    /** Tag for spam_skip log when {@link #recipeInvolvesSpam} is true. */
    public static String spamSkipRolesTag(
            List<String> inputIds, List<String> outputIds, List<String> catalystIds) {
        if (anySpamItemId(outputIds)) {
            return "OUT";
        }
        if (anySpamItemId(catalystIds)) {
            return "CAT";
        }
        if (inputsAllSpam(inputIds)) {
            return "IN:all";
        }
        return "IN:some";
    }

    public static boolean layoutInvolvesSpam(JeiRecipeLayoutCollector.CollectedLayout layout) {
        return layoutInvolvesSpam(layout, "", "");
    }

    public static boolean layoutInvolvesSpam(
            JeiRecipeLayoutCollector.CollectedLayout layout, String focusItemId, String catUid) {
        if (layout == null) {
            return false;
        }
        List<String> inputs = itemIds(layout.itemStacks(RecipeIngredientRole.INPUT));
        List<String> outputs = itemIds(layout.itemStacks(RecipeIngredientRole.OUTPUT));
        List<String> catalysts = itemIds(layout.itemStacks(RecipeIngredientRole.CATALYST));
        if (!recipeInvolvesSpam(inputs, outputs, catalysts)) {
            return false;
        }
        String focus = focusItemId == null || focusItemId.isBlank() ? "?" : focusItemId;
        String cat = catUid == null || catUid.isBlank() ? "?" : catUid;
        String recipeOut = outputs.isEmpty() ? "?" : outputs.get(0);
        String roles = spamSkipRolesTag(inputs, outputs, catalysts);
        PackAiMod.LOGGER.info(
                "Pack AI spam_skip item={} cat={} recipeOut={} roles={}",
                focus, cat, recipeOut, roles);
        return true;
    }

    static boolean inputsAllSpam(List<String> inputIds) {
        if (inputIds == null || inputIds.isEmpty()) {
            return false;
        }
        for (String id : inputIds) {
            if (!isSpamItemId(id)) {
                return false;
            }
        }
        return true;
    }

    static boolean anySpamItemId(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        for (String id : ids) {
            if (isSpamItemId(id)) {
                return true;
            }
        }
        return false;
    }

    /** Pre-fix INPUT rule: any spam variant in INPUT discarded the whole recipe. */
    static boolean recipeInvolvesSpamAnyInputVariant(
            List<String> inputIds, List<String> outputIds, List<String> catalystIds) {
        if (anySpamItemId(outputIds) || anySpamItemId(catalystIds) || anySpamItemId(inputIds)) {
            return true;
        }
        return false;
    }

    private static List<String> itemIds(List<ItemStack> stacks) {
        List<String> out = new ArrayList<>();
        if (stacks == null) {
            return out;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key != null) {
                out.add(key.toString());
            }
        }
        return out;
    }

    @SuppressWarnings("rawtypes")
    public static boolean isSpamCategory(RecipeType type, String catTitle) {
        String uid = "";
        try {
            Object u = type.getUid();
            if (u != null) {
                uid = u.toString().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // JEI uid shape varies
        }
        String t = catTitle == null ? "" : catTitle.toLowerCase(Locale.ROOT);
        return containsSpamKeyword(uid) || containsSpamKeyword(t);
    }

    /**
     * Categories that are not placeable machine workstations (quests, info tabs, …).
     * Used to keep Machine brief off quest-book icons and similar false catalysts.
     */
    @SuppressWarnings("rawtypes")
    public static boolean isNonMachineCategory(RecipeType type, String catTitle) {
        String uid = "";
        try {
            Object u = type.getUid();
            if (u != null) {
                uid = u.toString().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // JEI uid shape varies
        }
        String t = catTitle == null ? "" : catTitle.toLowerCase(Locale.ROOT);
        return containsNonMachineKeyword(uid) || containsNonMachineKeyword(t);
    }

    private static boolean containsSpamKeyword(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        return s.contains("facade")
                || s.contains("framed block")
                || s.contains("framedblocks")
                || s.contains("framed_")
                || s.contains("camo")
                || s.contains("cover")
                || s.contains("disguise")
                || s.contains("mimic");
    }

    private static boolean containsNonMachineKeyword(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        // Quests (FTB / Heracles / localized 任務書 tabs) — never Machine.
        if (s.contains("quest") || s.contains("任務") || s.contains("heracles")) {
            return true;
        }
        if (s.contains("ftbquests") || s.contains("ftb_quest")
                || (s.contains("ftb") && s.contains("quest"))) {
            return true;
        }
        // Info / ponder-only tabs — not automation workstations.
        return s.contains("information")
                || s.contains("info_category")
                || s.contains("ponder");
    }

    public static String skipReasonLabel() {
        return skipReasonLabel(ReplyLang.current());
    }

    public static String skipReasonLabel(String replyLang) {
        String lang = replyLang == null || replyLang.isBlank() ? "zh_tw" : replyLang.trim();
        return ReplyLang.spamSkipLabel(lang);
    }
}
