package com.skps9.packai.client.patchouli;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import com.skps9.packai.client.knowledge.GuidebookIndex;
import com.skps9.packai.compat.PatchouliBridge;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.GuidebookEntry;
import com.skps9.packai.logic.GuidebookPins;
import com.skps9.packai.logic.PatchouliEntryScan;

/**
 * Resolve Patchouli guide text: index item path first; Phase B title search on miss / no-item;
 * soft-dep API only on item index miss. No Ask-path {@code listResources} scan.
 */
public final class PatchouliGuideLookup {
    private PatchouliGuideLookup() {}

    public static String lookup(ItemStack stack) {
        return lookup(stack, null);
    }

    /**
     * @param question optional Ask question for B2 title search / guide-intent gate
     */
    public static String lookup(ItemStack stack, String question) {
        String scope = PackAiConfig.guidebookScope();
        GuidebookIndex.INSTANCE.ensureAsync();
        // Worker Ask thread may wait; client thread never blocks (deadlock with snapshot).
        GuidebookIndex.INSTANCE.awaitReady(3000);

        boolean hasItem = stack != null && !stack.isEmpty();
        String itemId = "";
        String itemNs = "";
        if (hasItem) {
            var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key != null) {
                itemId = key.toString();
                itemNs = GuidebookPins.itemNamespace(itemId);
            } else {
                hasItem = false;
            }
        }

        List<GuidebookEntry> hits = new ArrayList<>();
        if (GuidebookIndex.INSTANCE.isReady() && hasItem && !itemId.isBlank()) {
            hits.addAll(GuidebookPins.filterScope(
                    GuidebookIndex.INSTANCE.lookupByItem(itemId), scope, itemNs));
        }

        // B2: title search only when item path empty
        if (hits.isEmpty() && GuidebookIndex.INSTANCE.isReady()
                && question != null && !question.isBlank()) {
            if (hasItem) {
                hits.addAll(GuidebookIndex.INSTANCE.searchByTitle(
                        question, GuidebookPins.HIGH_TITLE_SCORE, scope, itemNs, true));
            } else if (GuidebookPins.hasGuideIntent(question)) {
                // no itemNs → ignore same_mod ns filter
                hits.addAll(GuidebookIndex.INSTANCE.searchByTitle(
                        question, GuidebookPins.HIGH_NO_ITEM_SCORE, scope, "", false));
            }
        }

        // B3 related hop (default off)
        if (!hits.isEmpty() && PackAiConfig.guidebookRelatedHop()) {
            hits = GuidebookPins.expandRelated(
                    hits,
                    GuidebookIndex.INSTANCE.byKeyView(),
                    GuidebookIndex.INSTANCE.categoryMapView(),
                    scope,
                    itemNs,
                    GuidebookPins.MAX_RELATED_EXTRA);
        }

        String fromIndex = GuidebookPins.formatPins(hits, itemId);
        if (fromIndex != null && !fromIndex.isBlank()) {
            return GuidebookPins.resolveGuideBody(fromIndex);
        }

        // API fallback only when we had a focus stack (item path miss)
        if (hasItem) {
            return GuidebookPins.resolveGuideBody(
                    PatchouliBridge.lookupGuideText(stack, scope, itemNs));
        }
        return "";
    }

    /**
     * @deprecated Ask must not full-scan; kept for debug only. Returns empty.
     */
    @Deprecated
    public static String scanResources(String itemId) {
        return "";
    }

    public static String formatScoped(
            List<GuidebookEntry> entries, String itemId, String scope
    ) {
        String itemNs = GuidebookPins.itemNamespace(itemId);
        return GuidebookPins.formatPins(
                GuidebookPins.filterScope(entries, scope, itemNs),
                itemId,
                PatchouliEntryScan.DEFAULT_MAX_ENTRIES,
                PatchouliEntryScan.DEFAULT_MAX_CHARS);
    }
}
