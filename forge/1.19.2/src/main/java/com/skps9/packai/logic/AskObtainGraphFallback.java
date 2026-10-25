package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * When JEI focus prefetch finds zero output cards, supply obtain paths from
 * {@link ObtainGraph} (item id, NBT-agnostic).
 */
public final class AskObtainGraphFallback {
    static final int EDGE_CAP = 6;
    public static final String PLACEMENT = "graph_fallback";

    static volatile boolean enabled = true;

    /** Harness: graph lines appended on last successful fallback (-1 = none). */
    static volatile int lastGraphLogged = -1;

    /** Harness: placements from last fallback run. */
    static final List<String> lastPlacements = new ArrayList<>();

    private AskObtainGraphFallback() {}

    /**
     * @return number of output cards appended from the graph (0 if skipped or none)
     */
    static int tryAppend(
            AskLoopState loop,
            String focusItemId,
            List<RecipeCard> merged,
            LinkedHashSet<String> seen,
            int jeiOutputAdded
    ) {
        lastGraphLogged = -1;
        if (!enabled || jeiOutputAdded > 0 || loop == null || focusItemId == null || focusItemId.isBlank()) {
            return 0;
        }
        if (!ObtainGraphRuntime.INSTANCE.ready()) {
            return 0;
        }
        ObtainGraph graph = ObtainGraphRuntime.INSTANCE.graph();
        if (graph == null) {
            return 0;
        }
        Item focusItem = resolveItem(focusItemId.trim());
        if (focusItem == null || focusItem == Items.AIR) {
            return 0;
        }
        List<ObtainGraph.Edge> edges = graph.byOutput().get(focusItem);
        if (edges == null || edges.isEmpty()) {
            return 0;
        }
        List<ObtainGraph.Edge> picked = pickEdges(edges);
        if (picked.isEmpty()) {
            return 0;
        }
        String lang = loop.lang();
        int added = 0;
        for (ObtainGraph.Edge edge : picked) {
            if (edge == null) {
                continue;
            }
            String key = edgeKey(edge);
            if (!key.isEmpty() && !seen.add(key)) {
                continue;
            }
            RecipeCard card = toRecipeCard(edge, focusItemId.trim(), lang);
            if (card == null || card.isEmpty()) {
                continue;
            }
            merged.add(card);
            lastPlacements.add(PLACEMENT);
            added++;
            AskTrace.card(
                    "check.cards",
                    card.categoryTitle(),
                    card.primaryOutputId(),
                    card.outputs() == null ? 0 : card.outputs().size(),
                    false,
                    card.sourceItemId(),
                    PLACEMENT,
                    "role=" + card.promptRole());
        }
        if (added > 0) {
            lastGraphLogged = added;
        }
        return added;
    }

    static List<ObtainGraph.Edge> pickEdges(List<ObtainGraph.Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        List<ObtainGraph.Edge> sorted = new ArrayList<>(edges);
        sorted.sort(
                Comparator.comparingInt(ObtainGraph.Edge::outputCount)
                        .reversed()
                        .thenComparing(e -> e.recipeId() == null ? "" : e.recipeId().toString()));
        int n = Math.min(EDGE_CAP, sorted.size());
        return List.copyOf(sorted.subList(0, n));
    }

    static RecipeCard toRecipeCard(ObtainGraph.Edge edge, String focusItemId, String lang) {
        if (edge == null || edge.output() == null || edge.output() == Items.AIR) {
            return null;
        }
        String category = categoryTitle(edge, lang);
        List<ItemStack> inputs = inputStacks(edge);
        int outCount = Math.max(1, edge.outputCount());
        ItemStack output = new ItemStack(edge.output(), outCount);
        return RecipeCard.flow(
                        category,
                        inputs,
                        List.of(),
                        List.of(output),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())
                .withSourceItemId(focusItemId)
                .withFocusRole(RecipeCard.FocusRole.OUTPUT);
    }

    private static String categoryTitle(ObtainGraph.Edge edge, String lang) {
        String action = edge.actionLabel();
        if (action != null && !action.isBlank()) {
            return action.trim();
        }
        String resolved = MachineLabels.resolve(edge.actionLabel(), edge.typeId(), lang);
        if (resolved != null && !resolved.isBlank()) {
            return resolved.trim();
        }
        if (edge.typeId() != null) {
            return edge.typeId().toString();
        }
        return "?";
    }

    private static List<ItemStack> inputStacks(ObtainGraph.Edge edge) {
        List<List<Item>> groups = edge.inputGroups();
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        List<Integer> counts = edge.inputCounts();
        List<ItemStack> out = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            List<Item> group = groups.get(i);
            Item pick = firstItem(group);
            if (pick == null || pick == Items.AIR) {
                continue;
            }
            int count = 1;
            if (counts != null && i < counts.size() && counts.get(i) != null) {
                count = Math.max(1, counts.get(i));
            }
            out.add(new ItemStack(pick, count));
        }
        return out;
    }

    private static Item firstItem(List<Item> group) {
        if (group == null) {
            return null;
        }
        for (Item it : group) {
            if (it != null && it != Items.AIR) {
                return it;
            }
        }
        return null;
    }

    private static Item resolveItem(String itemId) {
        try {
            return Registry.ITEM.get(new ResourceLocation(itemId));
        } catch (Exception e) {
            return null;
        }
    }

    static String edgeKey(ObtainGraph.Edge edge) {
        if (edge == null || edge.recipeId() == null) {
            return "";
        }
        return edge.recipeId().toString().toLowerCase(Locale.ROOT);
    }

    static void setEnabledForTest(boolean on) {
        enabled = on;
    }

    static void resetForTest() {
        enabled = true;
        lastGraphLogged = -1;
        lastPlacements.clear();
    }
}
