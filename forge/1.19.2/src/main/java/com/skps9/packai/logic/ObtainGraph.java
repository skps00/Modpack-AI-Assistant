package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Immutable output→recipe edge index. Pure: only Recipe accessors; no world / static globals.
 * Determinism: sort by {@code recipeId.toString()} before per-item truncation.
 */
public final class ObtainGraph {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final int MAX_EDGES_PER_ITEM = 32;
    public static final int MAX_EDGES = 200_000;

    public record Edge(
            ResourceLocation recipeId,
            ResourceLocation typeId,
            Item output,
            int outputCount,
            List<List<Item>> inputGroups,
            /** Display label from recipe toast symbol; null when unavailable. */
            String actionLabel) {}

    private final Map<Item, List<Edge>> byOutput;
    private final Set<Item> knownItems;
    private final int edgeCount;

    private ObtainGraph(Map<Item, List<Edge>> byOutput, Set<Item> knownItems, int edgeCount) {
        this.byOutput = byOutput;
        this.knownItems = knownItems;
        this.edgeCount = edgeCount;
    }

    public Map<Item, List<Edge>> byOutput() {
        return byOutput;
    }

    public Set<Item> knownItems() {
        return knownItems;
    }

    public int edgeCount() {
        return edgeCount;
    }

    public List<Edge> edgesFor(Item item) {
        if (item == null) {
            return List.of();
        }
        return byOutput.getOrDefault(item, List.of());
    }

    @Override
    public String toString() {
        return "ObtainGraph{edges=" + edgeCount + ", items=" + byOutput.size() + "}";
    }

    /** Harness-only convenience: {@code feed(all)+finish()}. */
    public static ObtainGraph build(List<Recipe<?>> all) {
        Builder b = new Builder(0L);
        b.feed(all);
        return b.finish();
    }

    public static final class Builder {
        private final long generation;
        private final List<Edge> accumulated = new ArrayList<>();

        public Builder(long generation) {
            this.generation = generation;
        }

        public long generation() {
            return generation;
        }

        public void feed(List<Recipe<?>> batch) {
            feed(batch, generation);
        }

        /**
         * Accumulate a recipe batch. When {@code feedGeneration} != builder generation,
         * the batch is dropped (mid-rebuild replaceRecipes guard).
         */
        public void feed(List<Recipe<?>> batch, long feedGeneration) {
            if (batch == null || batch.isEmpty()) {
                return;
            }
            if (feedGeneration != generation) {
                LOGGER.info("obtain.graph.stale_gen dropped={}", batch.size());
                return;
            }
            for (Recipe<?> recipe : batch) {
                Edge edge = tryEdge(recipe);
                if (edge != null) {
                    accumulated.add(edge);
                }
            }
        }

        public ObtainGraph finish() {
            Map<Item, List<Edge>> grouped = new LinkedHashMap<>();
            for (Edge edge : accumulated) {
                grouped.computeIfAbsent(edge.output(), k -> new ArrayList<>()).add(edge);
            }

            List<Item> items = new ArrayList<>(grouped.keySet());
            items.sort(Comparator.comparing(ObtainGraph::itemKey));

            Map<Item, List<Edge>> out = new LinkedHashMap<>();
            Set<Item> known = new LinkedHashSet<>();
            int total = 0;
            int droppedItems = 0;

            for (Item item : items) {
                if (total >= MAX_EDGES) {
                    droppedItems++;
                    continue;
                }
                List<Edge> list = grouped.get(item);
                // Craft first, then smelt, then machines — deterministic (priority, recipeId).
                list.sort(Comparator
                        .comparingInt((Edge e) -> edgePriority(e.typeId()))
                        .thenComparing(e -> e.recipeId().toString()));
                List<Edge> deduped = dedupeByRecipeId(list);
                int kept = deduped.size();
                if (kept > MAX_EDGES_PER_ITEM) {
                    LOGGER.info(
                            "obtain.graph.capped item={} dropped={}",
                            itemKey(item),
                            kept - MAX_EDGES_PER_ITEM);
                    kept = MAX_EDGES_PER_ITEM;
                }
                if (total + kept > MAX_EDGES) {
                    kept = MAX_EDGES - total;
                }
                if (kept <= 0) {
                    droppedItems++;
                    continue;
                }
                List<Edge> slice = List.copyOf(deduped.subList(0, kept));
                out.put(item, slice);
                total += slice.size();
                known.add(item);
                for (Edge e : slice) {
                    for (List<Item> group : e.inputGroups()) {
                        known.addAll(group);
                    }
                }
            }
            if (droppedItems > 0) {
                LOGGER.info(
                        "obtain.graph.global_cap dropped_items={} kept_edges={}",
                        droppedItems,
                        total);
            }

            return new ObtainGraph(Map.copyOf(out), Set.copyOf(known), total);
        }
    }

    private static List<Edge> dedupeByRecipeId(List<Edge> sorted) {
        List<Edge> out = new ArrayList<>(sorted.size());
        ResourceLocation prev = null;
        for (Edge e : sorted) {
            if (prev != null && prev.equals(e.recipeId())) {
                continue;
            }
            out.add(e);
            prev = e.recipeId();
        }
        return out;
    }

    private static Edge tryEdge(Recipe<?> recipe) {
        if (recipe == null) {
            return null;
        }
        ResourceLocation id = null;
        try {
            if (recipe.isSpecial()) {
                return null;
            }
            id = recipe.getId();
            ItemStack result = recipe.getResultItem();
            if (result == null || result.isEmpty()) {
                return null;
            }
            Item output = result.getItem();
            if (output == null || output == Items.AIR) {
                return null;
            }

            NonNullList<Ingredient> ings = recipe.getIngredients();
            List<List<Item>> groups = new ArrayList<>();
            if (ings != null) {
                for (Ingredient ing : ings) {
                    if (ing == null || ing.isEmpty()) {
                        continue;
                    }
                    LinkedHashSet<Item> distinct = new LinkedHashSet<>();
                    for (ItemStack stack : ing.getItems()) {
                        if (stack == null || stack.isEmpty()) {
                            continue;
                        }
                        Item it = stack.getItem();
                        if (it == null || it == Items.AIR || it == Items.BARRIER) {
                            continue;
                        }
                        distinct.add(it);
                    }
                    if (distinct.isEmpty()) {
                        // Empty-tag sentinel (BARRIER) filtered the whole group → incomplete.
                        return null;
                    }
                    List<Item> sortedGroup = new ArrayList<>(distinct);
                    sortedGroup.sort(Comparator.comparing(ObtainGraph::itemKey));
                    groups.add(List.copyOf(sortedGroup));
                }
            }

            // Skip sole-group variant conversion (group members include the output itself).
            if (groups.size() == 1 && groups.get(0).contains(output)) {
                return null;
            }

            ResourceLocation typeId = typeKey(recipe.getType());
            // Toast label only for machine types (priority 2); craft/smelt stay null.
            String actionLabel = edgePriority(typeId) == 2 ? captureActionLabel(recipe) : null;
            return new Edge(
                    id,
                    typeId,
                    output,
                    result.getCount(),
                    List.copyOf(groups),
                    actionLabel);
        } catch (Throwable t) {
            LOGGER.info(
                    "obtain.graph.skip recipe={} err={}",
                    id != null ? id : "?",
                    t.getClass().getSimpleName());
            return null;
        }
    }

    private static ResourceLocation typeKey(RecipeType<?> type) {
        if (type == null) {
            return new ResourceLocation("minecraft", "unknown");
        }
        ResourceLocation key = Registry.RECIPE_TYPE.getKey(type);
        return key != null ? key : new ResourceLocation("minecraft", "unknown");
    }

    /**
     * Lower = preferred for DFS / truncate. Crafting 0, furnace-family 1, else 2.
     * Path from {@link RecipeType} id (shaped/shapeless share {@code crafting}).
     */
    static int edgePriority(ResourceLocation typeId) {
        String path = typeId == null ? "" : typeId.getPath();
        if ("crafting".equals(path)
                || "crafting_shaped".equals(path)
                || "crafting_shapeless".equals(path)) {
            return 0;
        }
        if ("smelting".equals(path)
                || "blasting".equals(path)
                || "smoking".equals(path)
                || "campfire_cooking".equals(path)) {
            return 1;
        }
        return 2;
    }

    private static final int ACTION_LABEL_CAP = 40;

    /**
     * Toast symbol via {@link OfficialDisplay}; unknown-item when unresolved / key-shaped.
     * Null when toast unavailable. Length-capped.
     */
    private static String captureActionLabel(Recipe<?> recipe) {
        try {
            ItemStack toast = recipe.getToastSymbol();
            if (toast == null || toast.isEmpty()) {
                return null;
            }
            Item toastItem = toast.getItem();
            if (toastItem == null || toastItem == Items.AIR) {
                return null;
            }
            String id = itemKey(toastItem);
            String name = OfficialDisplay.officialName(id);
            if (name == null || name.isBlank() || OfficialDisplay.isTranslationKeyShaped(name)) {
                name = ReplyLang.unknownItem(ReplyLang.current());
            }
            name = Plainify.stripMcFormat(name);
            if (name == null || name.isBlank()) {
                return ReplyLang.unknownItem(ReplyLang.current());
            }
            name = name.trim();
            if (name.length() > ACTION_LABEL_CAP) {
                return name.substring(0, ACTION_LABEL_CAP);
            }
            return name;
        } catch (Throwable t) {
            return null;
        }
    }

    static String itemKey(Item item) {
        if (item == null) {
            return "";
        }
        ResourceLocation key = Registry.ITEM.getKey(item);
        return key == null ? "" : key.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ObtainGraph that)) {
            return false;
        }
        return edgeCount == that.edgeCount && Objects.equals(byOutput, that.byOutput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byOutput, edgeCount);
    }
}
