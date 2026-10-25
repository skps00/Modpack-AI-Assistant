package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.jei.AskJeiClient;
import com.skps9.packai.client.jei.JeiRecipeCards;
import com.skps9.packai.client.service.AskService;

import net.minecraft.world.item.ItemStack;

/**
 * Before the first LLM round: prefetch JEI output + uses cards for the ask focus item
 * into {@link AskLoopState#catalogCards()} so facts always carry obtain paths.
 */
public final class AskFocusPrefetch {
    static final int SCAN_CAP = 24;
    static final int USES_CAP = 3;
    public static final String PLACEMENT = "focus_prefetch";

    static volatile boolean enabled = true;

    @FunctionalInterface
    public interface JeiCards {
        List<RecipeCard> fetch(ItemStack stack, int maxOutput, int maxInput, long deadlineMs) throws Exception;
    }

    static JeiCards jei = AskJeiClient::recipeCardsForItem;

    /** Last successful prefetch placements (harness only). */
    static final List<String> lastPlacements = new ArrayList<>();

    private AskFocusPrefetch() {}

    public static void run(AskLoopState loop, String focusItemId, AskToolEnv env, long deadlineMs) {
        if (!enabled || loop == null || focusItemId == null || focusItemId.isBlank()) {
            return;
        }
        String itemId = focusItemId.trim();
        ItemStack stack = RenderRecipeCardsAskTool.resolveRenderItemStack(itemId, env);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        lastPlacements.clear();
        long t0 = System.currentTimeMillis();
        int outAdded = 0;
        int usesAdded = 0;
        try {
            List<RecipeCard> outputPool = nz(jei.fetch(stack, SCAN_CAP, 0, deadlineMs));
            List<RecipeCard> outputs = RenderRecipeCardsAskTool.filterRole(outputPool, "output", "");
            outputs = JeiRecipeCards.coalesceMirrorEmission(outputs);

            List<RecipeCard> usesPool = nz(jei.fetch(stack, 0, SCAN_CAP, deadlineMs));
            List<RecipeCard> uses = RenderRecipeCardsAskTool.filterRole(usesPool, "uses", "");
            uses = JeiRecipeCards.coalesceMirrorEmission(uses);
            uses = RenderRecipeCardsAskTool.pickUsesWithCategoryDiversity(uses, USES_CAP);

            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (RecipeCard c : loop.catalogCards()) {
                if (c != null && !c.isEmpty()) {
                    seen.add(recipeIdKey(c));
                }
            }
            List<RecipeCard> merged = new ArrayList<>(loop.catalogCards());
            outAdded = appendNew(merged, seen, outputs, itemId);
            usesAdded = appendNew(merged, seen, uses, itemId);
            if (outAdded > 0 || usesAdded > 0) {
                loop.setCatalogCards(merged);
                loop.setRecipeCardLines(AskService.buildCatalogLines(merged, loop.lang()));
            }
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("Pack AI prefetch focus={} failed: {}", itemId, t.toString());
            return;
        }
        long ms = Math.max(0L, System.currentTimeMillis() - t0);
        boolean nbt = stack.hasTag() || ItemVariantKeys.hasVariantKeys(stack);
        PackAiMod.LOGGER.info(
                "Pack AI prefetch focus={} nbt={} output={} uses={} ms={}",
                itemId, nbt, outAdded, usesAdded, ms);
    }

    private static int appendNew(
            List<RecipeCard> merged,
            LinkedHashSet<String> seen,
            List<RecipeCard> batch,
            String focusItemId
    ) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (RecipeCard c : batch) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            String key = recipeIdKey(c);
            if (!key.isEmpty() && !seen.add(key)) {
                continue;
            }
            RecipeCard withSrc = c.sourceItemId() == null || c.sourceItemId().isBlank()
                    ? c.withSourceItemId(focusItemId) : c;
            merged.add(withSrc);
            added++;
            lastPlacements.add(PLACEMENT);
            AskTrace.card(
                    "check.cards",
                    withSrc.categoryTitle(),
                    withSrc.primaryOutputId(),
                    withSrc.outputs() == null ? 0 : withSrc.outputs().size(),
                    false,
                    withSrc.sourceItemId(),
                    PLACEMENT,
                    "role=" + withSrc.promptRole());
        }
        return added;
    }

    static String recipeIdKey(RecipeCard c) {
        if (c == null) {
            return "";
        }
        String id = RecipeUnlockGates.recipeIdOf(c.jeiLayout());
        if (id != null && !id.isBlank()) {
            return id.toLowerCase(Locale.ROOT);
        }
        String cat = c.categoryTitle() == null ? "" : c.categoryTitle();
        String out = c.primaryOutputId() == null ? "" : c.primaryOutputId();
        String src = c.sourceItemId() == null ? "" : c.sourceItemId();
        return (cat + "|" + out + "|" + src + "|" + c.focusRole()).toLowerCase(Locale.ROOT);
    }

    private static List<RecipeCard> nz(List<RecipeCard> list) {
        return list == null ? List.of() : list;
    }

    static void setJeiForTest(JeiCards replacement) {
        jei = replacement == null ? AskJeiClient::recipeCardsForItem : replacement;
    }

    static void resetJeiForTest() {
        jei = AskJeiClient::recipeCardsForItem;
    }

    static void setEnabledForTest(boolean on) {
        enabled = on;
    }
}
