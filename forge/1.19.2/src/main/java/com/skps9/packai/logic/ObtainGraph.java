package com.skps9.packai.logic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Immutable output→recipe edge index. Pure: only Recipe accessors; no world / static globals.
 * Determinism: sort by {@code recipeId.toString()} before per-item truncation.
 */
public final class ObtainGraph {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final int MAX_EDGES_PER_ITEM = 32;
    public static final int MAX_EDGES = 200_000;
    public static final int MAX_FLUID_EDGES_PER_FLUID = 4;
    /** LD4: flip {@link ObtainPaths.Step#complete(ObtainGraph)} only when unresolved fluid-edge ratio ≤ this. */
    public static final double COMPLETE_FLIP_MAX_UNRESOLVED_RATIO = 0.10;

    public record FluidReq(String fluidId, int amountMb) {}

    public record Requirement(String kind, String id, int amount, String unit) {}

    public record FluidEdge(
            ResourceLocation recipeId,
            ResourceLocation typeId,
            String fluidId,
            int amountMb,
            List<List<Item>> itemGroups,
            List<FluidReq> fluidInputs,
            String actionLabel) {}

    public record Edge(
            ResourceLocation recipeId,
            ResourceLocation typeId,
            Item output,
            int outputCount,
            List<List<Item>> inputGroups,
            /** Display label from recipe toast symbol; null when unavailable. */
            String actionLabel,
            List<FluidReq> fluids,
            List<Requirement> requirements,
            /** True when at least one ingredient group could not be resolved (e.g. empty tag). */
            boolean inputsPartial) {
        /** Harness / legacy call sites without explicit requirements. */
        public Edge(
                ResourceLocation recipeId,
                ResourceLocation typeId,
                Item output,
                int outputCount,
                List<List<Item>> inputGroups,
                String actionLabel,
                List<FluidReq> fluids,
                boolean inputsPartial) {
            this(
                    recipeId,
                    typeId,
                    output,
                    outputCount,
                    inputGroups,
                    actionLabel,
                    fluids,
                    List.of(),
                    inputsPartial);
        }
    }

    private final Map<Item, List<Edge>> byOutput;
    private final Map<String, List<FluidEdge>> fluidProducers;
    private final Set<Item> knownItems;
    private final int edgeCount;
    private final int edgesWithFluids;
    private final int fluidOnlyEdges;
    private final int distinctFluids;
    private final int containerSuspects;
    private final int fluidUnresolvedEdges;
    private final boolean completeFlipEnabled;

    private ObtainGraph(
            Map<Item, List<Edge>> byOutput,
            Map<String, List<FluidEdge>> fluidProducers,
            Set<Item> knownItems,
            int edgeCount,
            int edgesWithFluids,
            int fluidOnlyEdges,
            int distinctFluids,
            int containerSuspects,
            int fluidUnresolvedEdges,
            boolean completeFlipEnabled) {
        this.byOutput = byOutput;
        this.fluidProducers = fluidProducers;
        this.knownItems = knownItems;
        this.edgeCount = edgeCount;
        this.edgesWithFluids = edgesWithFluids;
        this.fluidOnlyEdges = fluidOnlyEdges;
        this.distinctFluids = distinctFluids;
        this.containerSuspects = containerSuspects;
        this.fluidUnresolvedEdges = fluidUnresolvedEdges;
        this.completeFlipEnabled = completeFlipEnabled;
    }

    public Map<Item, List<Edge>> byOutput() {
        return byOutput;
    }

    public Map<String, List<FluidEdge>> fluidProducers() {
        return fluidProducers;
    }

    /**
     * Pick a fluid producer for {@code fluidId}, excluding producers whose item inputs contain
     * {@code consumer} (cycle guard). Tie-break: {@link #edgePriority} then recipe id.
     */
    public FluidEdge pickFluidProducer(String fluidId, Item consumer) {
        if (fluidId == null || fluidId.isBlank()) {
            return null;
        }
        List<FluidEdge> candidates = fluidProducers.getOrDefault(fluidId, List.of());
        if (candidates.isEmpty()) {
            return null;
        }
        for (FluidEdge fe : candidates) {
            if (!producerInputsContain(fe, consumer)) {
                return fe;
            }
        }
        return null;
    }

    private static boolean producerInputsContain(FluidEdge fe, Item consumer) {
        if (fe == null || consumer == null) {
            return false;
        }
        for (List<Item> group : fe.itemGroups()) {
            if (group != null && group.contains(consumer)) {
                return true;
            }
        }
        return false;
    }

    public Set<Item> knownItems() {
        return knownItems;
    }

    public int edgeCount() {
        return edgeCount;
    }

    public int edgesWithFluids() {
        return edgesWithFluids;
    }

    public int fluidOnlyEdges() {
        return fluidOnlyEdges;
    }

    public int distinctFluids() {
        return distinctFluids;
    }

    public int containerSuspects() {
        return containerSuspects;
    }

    /** Edges with non-empty {@link Edge#fluids()} (runtime denominator for LD4). */
    public int fluidEdges() {
        return edgesWithFluids;
    }

    public int fluidUnresolvedEdges() {
        return fluidUnresolvedEdges;
    }

    public boolean completeFlipEnabled() {
        return completeFlipEnabled;
    }

    public List<Edge> edgesFor(Item item) {
        if (item == null) {
            return List.of();
        }
        return byOutput.getOrDefault(item, List.of());
    }

    /**
     * Canonical SHA-1 (first 16 hex) over sorted edge lines
     * {@code outputId|recipeId|outputCount|inputs|inputsPartial|requirements} for determinism checks.
     */
    public String canonicalContentFingerprint() {
        List<String> lines = new ArrayList<>();
        for (List<Edge> edges : byOutput.values()) {
            for (Edge edge : edges) {
                lines.add(canonicalEdgeLine(edge));
            }
        }
        lines.sort(String::compareTo);
        return sha1Hex16(String.join("\n", lines));
    }

    static String canonicalEdgeLine(Edge edge) {
        String outputId = itemKey(edge.output());
        String recipeId = edge.recipeId().toString();
        String inputs = canonicalInputGroups(edge.inputGroups());
        String requirements = canonicalRequirements(edge.requirements());
        return outputId
                + "|"
                + recipeId
                + "|"
                + edge.outputCount()
                + "|"
                + inputs
                + "|"
                + edge.inputsPartial()
                + "|"
                + requirements;
    }

    private static String canonicalRequirements(List<Requirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return "";
        }
        List<Requirement> sorted = new ArrayList<>(requirements);
        sorted.sort(
                Comparator.comparing(Requirement::kind, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(Requirement::id, Comparator.nullsFirst(String::compareTo))
                        .thenComparingInt(Requirement::amount)
                        .thenComparing(Requirement::unit, Comparator.nullsFirst(String::compareTo)));
        List<String> parts = new ArrayList<>();
        for (Requirement r : sorted) {
            if (r == null) {
                continue;
            }
            String kind = r.kind() != null ? r.kind() : "";
            String id = r.id() != null ? r.id() : "";
            String unit = r.unit() != null ? r.unit() : "";
            parts.add(kind + ":" + id + ":" + r.amount() + ":" + unit);
        }
        return String.join(",", parts);
    }

    private static String canonicalInputGroups(List<List<Item>> groups) {
        if (groups == null || groups.isEmpty()) {
            return "";
        }
        List<String> groupParts = new ArrayList<>();
        for (List<Item> group : groups) {
            List<String> ids = new ArrayList<>();
            if (group != null) {
                for (Item item : group) {
                    ids.add(itemKey(item));
                }
            }
            ids.sort(String::compareTo);
            groupParts.add(String.join(",", ids));
        }
        return String.join(";", groupParts);
    }

    private static String sha1Hex16(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    /** Non-energy requirements block path completion (machine cost does not). */
    static boolean hasBlockingRequirement(List<Requirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return false;
        }
        for (Requirement r : requirements) {
            if (r != null && r.kind() != null && !"energy".equals(r.kind())) {
                return true;
            }
        }
        return false;
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
        private static final int PROBE_LOG_CAP = 200;

        /** Harness-only: wall ms of the most recent {@link #finish()}. */
        static volatile long lastFinishMs;

        private final long generation;
        private final BuildContext ctx;
        private final List<Edge> accumulated = new ArrayList<>();
        private final List<FluidEdge> accumulatedFluidEdges = new ArrayList<>();
        private Set<ResourceLocation> probeItems = Set.of();
        private long bailEmptyResult;
        private long bailNoInputs;
        private long bailSelfVariant;
        private final Map<String, Long> bailEmptyResultByType = new HashMap<>();
        private final Map<String, Long> bailNoInputsByType = new HashMap<>();
        private int probeLogCount;
        private boolean probeTruncatedLogged;
        private long feedStartNs;
        private long fedRecipes;
        private final ConcurrentHashMap<String, Integer> resourceByAccessor = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Integer> resourceByType = new ConcurrentHashMap<>();

        public Builder(long generation) {
            this(generation, BuildContext.headless());
        }

        public Builder(long generation, BuildContext ctx) {
            if (ctx == null) {
                throw new IllegalArgumentException("BuildContext required");
            }
            this.generation = generation;
            this.ctx = ctx;
        }

        public void probeItems(Set<ResourceLocation> items) {
            this.probeItems = items != null ? Set.copyOf(items) : Set.of();
        }

        public long generation() {
            return generation;
        }

        public long bailEmptyResult() {
            return bailEmptyResult;
        }

        public long bailNoInputs() {
            return bailNoInputs;
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
                if (ctx.census()) {
                    RecipeIo.censusRequirements(recipe, resourceByAccessor, resourceByType);
                }
                Edge edge = tryEdge(recipe);
                probeRecipe(recipe, edge != null);
                if (edge != null) {
                    accumulated.add(edge);
                }
                if (feedStartNs == 0L) {
                    feedStartNs = System.nanoTime();
                }
                fedRecipes++;
                if (fedRecipes % 4096 == 0) {
                    LOGGER.info(
                            "obtain.graph.progress fed={} elapsedMs={}",
                            fedRecipes,
                            (System.nanoTime() - feedStartNs) / 1_000_000L);
                }
            }
        }

        public ObtainGraph finish() {
            long finishStart = System.nanoTime();
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

            LOGGER.info(
                    "obtain.graph.bail emptyResult={} noInputs={} selfVariant={}",
                    bailEmptyResult,
                    bailNoInputs,
                    bailSelfVariant);
            LOGGER.info("obtain.graph.bail.types top={}", formatBailTypesTop());

            if (!resourceByAccessor.isEmpty() && !resourceByType.isEmpty()) {
                LOGGER.info("obtain.graph.resources accessors={}", topN(resourceByAccessor, 12));
                LOGGER.info("obtain.graph.resources types={}", topN(resourceByType, 10));
            }

            Map<String, List<FluidEdge>> fluidProducers = buildFluidProducers(accumulatedFluidEdges);

            FluidLayerStats fluidStats = computeFluidLayerStats(out);
            CompleteFlipMetrics flip = computeCompleteFlipMetrics(out, fluidProducers, fluidStats.edgesWithFluids, ctx);
            lastFinishMs = (System.nanoTime() - finishStart) / 1_000_000L;
            return new ObtainGraph(
                    Map.copyOf(out),
                    Map.copyOf(fluidProducers),
                    Set.copyOf(known),
                    total,
                    fluidStats.edgesWithFluids,
                    fluidStats.fluidOnlyEdges,
                    fluidStats.distinctFluids,
                    fluidStats.containerSuspects,
                    flip.unresolvedEdges,
                    flip.flipEnabled);
        }

        private static CompleteFlipMetrics computeCompleteFlipMetrics(
                Map<Item, List<Edge>> byOutput,
                Map<String, List<FluidEdge>> fluidProducers,
                int fluidEdges,
                BuildContext ctx) {
            int unresolved = 0;
            for (List<Edge> edges : byOutput.values()) {
                for (Edge e : edges) {
                    if (e.fluids() == null || e.fluids().isEmpty()) {
                        continue;
                    }
                    if (edgeFluidsAllUnresolved(e, fluidProducers, ctx.naturalFluids())) {
                        unresolved++;
                    }
                }
            }
            double ratio = fluidEdges == 0 ? 0.0 : (double) unresolved / (double) fluidEdges;
            LOGGER.info(
                    "obtain.fluid.unresolved_ratio edges={} unresolved={} ratio={}",
                    fluidEdges,
                    unresolved,
                    String.format(java.util.Locale.ROOT, "%.4f", ratio));
            boolean flipEnabled = ratio <= COMPLETE_FLIP_MAX_UNRESOLVED_RATIO;
            if (!flipEnabled) {
                LOGGER.info(
                        "obtain.fluid.complete_flip=skipped ratio={}",
                        String.format(java.util.Locale.ROOT, "%.4f", ratio));
            }
            return new CompleteFlipMetrics(unresolved, flipEnabled);
        }

        /** True when every fluid req has no producer and is not a configured natural source. */
        private static boolean edgeFluidsAllUnresolved(
                Edge e, Map<String, List<FluidEdge>> fluidProducers, Set<String> naturalFluids) {
            for (FluidReq req : e.fluids()) {
                if (req == null || req.fluidId() == null || req.fluidId().isBlank()) {
                    continue;
                }
                if (naturalFluids.contains(req.fluidId())) {
                    return false;
                }
                List<FluidEdge> prod = fluidProducers.get(req.fluidId());
                if (prod != null && !prod.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private record CompleteFlipMetrics(int unresolvedEdges, boolean flipEnabled) {}

        private static FluidLayerStats computeFluidLayerStats(Map<Item, List<Edge>> byOutput) {
            int edgesWithFluids = 0;
            int fluidOnlyEdges = 0;
            Set<String> fluidIds = new HashSet<>();
            int containerSuspects = 0;
            Map<Item, Boolean> containerMemo = new HashMap<>();

            for (List<Edge> edges : byOutput.values()) {
                for (Edge e : edges) {
                    if (e.fluids().isEmpty()) {
                        continue;
                    }
                    edgesWithFluids++;
                    if (e.inputGroups().isEmpty()) {
                        fluidOnlyEdges++;
                    }
                    for (FluidReq f : e.fluids()) {
                        fluidIds.add(f.fluidId());
                    }
                    if (edgeHasFluidContainerInput(e, containerMemo)) {
                        containerSuspects++;
                    }
                }
            }
            return new FluidLayerStats(edgesWithFluids, fluidOnlyEdges, fluidIds.size(), containerSuspects);
        }

        private static boolean edgeHasFluidContainerInput(Edge e, Map<Item, Boolean> containerMemo) {
            for (List<Item> group : e.inputGroups()) {
                for (Item item : group) {
                    if (item == null) {
                        continue;
                    }
                    Boolean cached = containerMemo.get(item);
                    boolean holds;
                    if (cached != null) {
                        holds = cached;
                    } else {
                        try {
                            holds = FluidUtil.getFluidContained(new ItemStack(item)).isPresent();
                        } catch (Throwable t) {
                            holds = false;
                        }
                        containerMemo.put(item, holds);
                    }
                    if (holds) {
                        return true;
                    }
                }
            }
            return false;
        }

        private record FluidLayerStats(
                int edgesWithFluids, int fluidOnlyEdges, int distinctFluids, int containerSuspects) {}

        private static Map<String, List<FluidEdge>> buildFluidProducers(List<FluidEdge> raw) {
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<String, List<FluidEdge>> grouped = new LinkedHashMap<>();
            for (FluidEdge fe : raw) {
                if (fe == null || fe.fluidId() == null || fe.fluidId().isBlank()) {
                    continue;
                }
                grouped.computeIfAbsent(fe.fluidId(), k -> new ArrayList<>()).add(fe);
            }
            Map<String, List<FluidEdge>> out = new LinkedHashMap<>();
            for (Map.Entry<String, List<FluidEdge>> e : grouped.entrySet()) {
                List<FluidEdge> list = new ArrayList<>(e.getValue());
                list.sort(Comparator
                        .comparingInt((FluidEdge fe) -> edgePriority(fe.typeId()))
                        .thenComparing(fe -> fe.recipeId().toString()));
                int kept = Math.min(MAX_FLUID_EDGES_PER_FLUID, list.size());
                out.put(e.getKey(), List.copyOf(list.subList(0, kept)));
            }
            return out;
        }

        private static String topN(Map<String, Integer> counts, int limit) {
            return counts.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(limit)
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(","));
        }

        private String formatBailTypesTop() {
            Map<String, Long> combined = new HashMap<>();
            for (Map.Entry<String, Long> e : bailEmptyResultByType.entrySet()) {
                combined.merge(e.getKey(), e.getValue(), Long::sum);
            }
            for (Map.Entry<String, Long> e : bailNoInputsByType.entrySet()) {
                combined.merge(e.getKey(), e.getValue(), Long::sum);
            }
            return combined.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(12)
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(","));
        }

        private void recordBailEmpty(Recipe<?> recipe) {
            bailEmptyResult++;
            String typeStr = recipe.getType().toString();
            bailEmptyResultByType.merge(typeStr, 1L, Long::sum);
        }

        private void recordBailNoInputs(Recipe<?> recipe) {
            bailNoInputs++;
            String typeStr = recipe.getType().toString();
            bailNoInputsByType.merge(typeStr, 1L, Long::sum);
        }

        private void probeRecipe(Recipe<?> recipe, boolean built) {
            if (probeItems.isEmpty() || recipe == null) {
                return;
            }
            ResourceLocation recipeId = null;
            try {
                recipeId = recipe.getId();
            } catch (Throwable ignored) {
                // silent
            }
            String className = recipe.getClass().getName();
            Map<ResourceLocation, String> hits = new LinkedHashMap<>();
            collectProbeHits(recipe, hits);
            for (Map.Entry<ResourceLocation, String> e : hits.entrySet()) {
                if (probeLogCount >= PROBE_LOG_CAP) {
                    if (!probeTruncatedLogged) {
                        probeTruncatedLogged = true;
                        LOGGER.info("obtain.graph.probe.truncated");
                    }
                    return;
                }
                LOGGER.info(
                        "obtain.graph.probe item={} recipe={} class={} accessor={} built={}",
                        e.getKey(),
                        recipeId != null ? recipeId : "?",
                        className,
                        e.getValue(),
                        built);
                probeLogCount++;
            }
        }

        private void collectProbeHits(Recipe<?> recipe, Map<ResourceLocation, String> hits) {
            try {
                ItemStack direct = recipe.getResultItem();
                noteProbeStacks(direct, "getResultItem", hits);
            } catch (Throwable ignored) {
                // silent
            }
            if (allProbeItemsFound(hits)) {
                return;
            }
            try {
                Object listObj = recipe.getClass().getMethod("getRollableResults").invoke(recipe);
                if (listObj instanceof List<?> list) {
                    for (Object out : list) {
                        if (out == null) {
                            continue;
                        }
                        Object st = out.getClass().getMethod("getStack").invoke(out);
                        if (st instanceof ItemStack s) {
                            noteProbeStacks(s, "getRollableResults", hits);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // silent
            }
            if (allProbeItemsFound(hits)) {
                return;
            }
            invokeProbeMethod(recipe, "getOutputItems", hits);
            if (allProbeItemsFound(hits)) {
                return;
            }
            invokeProbeMethod(recipe, "getResults", hits);
        }

        private void invokeProbeMethod(Recipe<?> recipe, String methodName, Map<ResourceLocation, String> hits) {
            try {
                Object obj = recipe.getClass().getMethod(methodName).invoke(recipe);
                noteProbeObject(obj, methodName, hits);
            } catch (Throwable ignored) {
                // silent
            }
        }

        private void noteProbeObject(Object obj, String accessor, Map<ResourceLocation, String> hits) {
            if (obj == null) {
                return;
            }
            if (obj instanceof ItemStack stack) {
                noteProbeStacks(stack, accessor, hits);
                return;
            }
            if (obj instanceof ItemStack[] arr) {
                for (ItemStack stack : arr) {
                    noteProbeStacks(stack, accessor, hits);
                }
                return;
            }
            if (obj instanceof List<?> list) {
                for (Object el : list) {
                    if (el instanceof ItemStack stack) {
                        noteProbeStacks(stack, accessor, hits);
                    }
                }
            }
        }

        private void noteProbeStacks(ItemStack stack, String accessor, Map<ResourceLocation, String> hits) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            Item item = stack.getItem();
            if (item == null || item == Items.AIR) {
                return;
            }
            ResourceLocation key = Registry.ITEM.getKey(item);
            if (key == null || !probeItems.contains(key) || hits.containsKey(key)) {
                return;
            }
            hits.put(key, accessor);
        }

        private boolean allProbeItemsFound(Map<ResourceLocation, String> hits) {
            return hits.size() >= probeItems.size();
        }

        private Edge tryEdge(Recipe<?> recipe) {
            if (recipe == null) {
                return null;
            }
            ResourceLocation id = null;
            try {
                id = recipe.getId();
                ResourceLocation typeId = typeKey(recipe.getType());
                String actionLabel = captureActionLabel(recipe, ctx);
                List<List<Item>> groups = RecipeIo.inputGroups(recipe);
                List<FluidReq> fluidInputs = captureFluids(recipe);
                captureFluidProducerEdges(recipe, id, typeId, groups, fluidInputs, actionLabel);

                ItemStack result = captureResultStack(recipe);
                if (result == null || result.isEmpty()) {
                    recordBailEmpty(recipe);
                    return null;
                }
                Item output = result.getItem();
                if (output == null || output == Items.AIR) {
                    return null;
                }

                boolean skippedGroup = RecipeIo.skippedInputSlots();
                List<Requirement> requirements = RecipeIo.requirements(recipe);

                if (groups.size() == 1 && groups.get(0).contains(output)) {
                    bailSelfVariant++;
                    return null;
                }

                List<FluidReq> fluids = fluidInputs;
                if (groups.isEmpty() && fluids.isEmpty() && requirements.isEmpty()) {
                    recordBailNoInputs(recipe);
                    return null;
                }
                boolean inputsPartial =
                        skippedGroup
                                || RecipeIo.hasUnrepresentableInputs(recipe)
                                || hasBlockingRequirement(requirements);
                return new Edge(
                        id,
                        typeId,
                        output,
                        result.getCount(),
                        List.copyOf(groups),
                        actionLabel,
                        fluids,
                        List.copyOf(requirements),
                        inputsPartial);
            } catch (Throwable t) {
                LOGGER.info(
                        "obtain.graph.skip recipe={} class={} err={}",
                        id != null ? id : "?",
                        recipe.getClass().getName(),
                        t.getClass().getSimpleName());
                return null;
            }
        }

        private void captureFluidProducerEdges(
                Recipe<?> recipe,
                ResourceLocation id,
                ResourceLocation typeId,
                List<List<Item>> groups,
                List<FluidReq> fluidInputs,
                String actionLabel) {
            if (id == null) {
                return;
            }
            List<List<Item>> itemGroups = groups != null ? List.copyOf(groups) : List.of();
            List<FluidReq> inputsCopy = fluidInputs != null ? List.copyOf(fluidInputs) : List.of();
            for (RecipeIo.FluidOut f : RecipeIo.fluidOutputs(recipe)) {
                if (f == null || f.fluidId() == null || f.fluidId().isBlank()) {
                    continue;
                }
                accumulatedFluidEdges.add(new FluidEdge(
                        id,
                        typeId,
                        f.fluidId(),
                        f.amountMb(),
                        itemGroups,
                        inputsCopy,
                        actionLabel));
            }
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

    private static ItemStack captureResultStack(Recipe<?> recipe) {
        List<RecipeIo.ItemOut> outs = RecipeIo.outputs(recipe);
        if (outs.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return outs.get(0).stack();
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

    private static List<FluidReq> captureFluids(Recipe<?> recipe) {
        List<FluidReq> out = new ArrayList<>();
        for (RecipeIo.FluidOut f : RecipeIo.fluidInputs(recipe)) {
            out.add(new FluidReq(f.fluidId(), f.amountMb()));
        }
        return List.copyOf(out);
    }

    /** Pack resource listing natural fluid ids (T3 honesty); load once, fail-soft. */
    public static final class FluidSources {
        private static volatile Set<String> naturalIds = null;

        private FluidSources() {}

        public static boolean isNaturalFluid(String fluidId) {
            if (fluidId == null || fluidId.isBlank()) {
                return false;
            }
            return naturalIds().contains(fluidId);
        }

        public static Set<String> naturalIds() {
            Set<String> cached = naturalIds;
            if (cached != null) {
                return cached;
            }
            Set<String> loaded = loadNaturalIds();
            naturalIds = loaded;
            return loaded;
        }

        private static Set<String> loadNaturalIds() {
            Set<String> external = loadNaturalIdsFromConfigFile();
            if (external != null) {
                return external;
            }
            return loadNaturalIdsFromClasspath();
        }

        private static Set<String> loadNaturalIdsFromConfigFile() {
            try {
                Path path = FMLPaths.GAMEDIR.get().resolve("config/packai/fluid_sources.json");
                if (!Files.isRegularFile(path)) {
                    return null;
                }
                String text = Files.readString(path, StandardCharsets.UTF_8);
                Set<String> parsed = parseNaturalArray(text);
                return parsed != null ? parsed : null;
            } catch (Throwable t) {
                LOGGER.info("obtain.fluid_sources.config err={}", t.getClass().getSimpleName());
                return null;
            }
        }

        private static Set<String> loadNaturalIdsFromClasspath() {
            try (InputStream in =
                    ObtainGraph.class.getClassLoader().getResourceAsStream("assets/packai/fluid_sources.json")) {
                if (in == null) {
                    return Set.copyOf(new LinkedHashSet<>());
                }
                JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                Set<String> parsed = parseNaturalFromJson(root);
                return parsed != null ? parsed : Set.copyOf(new LinkedHashSet<>());
            } catch (Throwable t) {
                LOGGER.info("obtain.fluid_sources.load err={}", t.getClass().getSimpleName());
                return Set.copyOf(new LinkedHashSet<>());
            }
        }

        private static Set<String> parseNaturalArray(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                JsonElement root = JsonParser.parseString(json);
                Set<String> out = parseNaturalFromJson(root);
                return out != null ? Set.copyOf(out) : null;
            } catch (Throwable t) {
                LOGGER.info("obtain.fluid_sources.parse err={}", t.getClass().getSimpleName());
                return null;
            }
        }

        private static Set<String> parseNaturalFromJson(JsonElement root) {
            if (root == null || !root.isJsonObject()) {
                return null;
            }
            JsonObject o = root.getAsJsonObject();
            if (!o.has("natural") || !o.get("natural").isJsonArray()) {
                return null;
            }
            Set<String> out = new LinkedHashSet<>();
            JsonArray arr = o.getAsJsonArray("natural");
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    String id = el.getAsString();
                    if (id != null && !id.isBlank()) {
                        out.add(id);
                    }
                }
            }
            return out;
        }
    }

    /**
     * Toast symbol via {@link OfficialDisplay}; unknown-item when unresolved / key-shaped.
     * Null when toast unavailable. Length-capped.
     */
    private static String captureActionLabel(Recipe<?> recipe, BuildContext ctx) {
        try {
            Method m = recipe.getClass().getMethod("getToastSymbol");
            if (m.getDeclaringClass() == Recipe.class) {
                return null;
            }
            ItemStack toast = recipe.getToastSymbol();
            if (toast == null || toast.isEmpty()) {
                return null;
            }
            Item toastItem = toast.getItem();
            if (toastItem == null || toastItem == Items.AIR) {
                return null;
            }
            String id = itemKey(toastItem);
            String name = OfficialDisplay.officialName(id, ctx.langCode());
            if (name == null || name.isBlank() || OfficialDisplay.isTranslationKeyShaped(name)) {
                name = ReplyLang.unknownItem(ctx.langCode());
            }
            name = Plainify.stripMcFormat(name);
            if (name == null || name.isBlank()) {
                return ReplyLang.unknownItem(ctx.langCode());
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

    /**
     * Rehydrate an immutable graph from disk cache (no recipe replay). Harness + {@link ObtainGraphCache}.
     */
    public static ObtainGraph fromCached(
            Map<Item, List<Edge>> byOutput,
            Map<String, List<FluidEdge>> fluidProducers,
            Set<Item> knownItems,
            int edgeCount,
            int edgesWithFluids,
            int fluidOnlyEdges,
            int distinctFluids,
            int containerSuspects,
            int fluidUnresolvedEdges,
            boolean completeFlipEnabled) {
        return new ObtainGraph(
                byOutput == null ? Map.of() : Map.copyOf(byOutput),
                fluidProducers == null ? Map.of() : Map.copyOf(fluidProducers),
                knownItems == null ? Set.of() : Set.copyOf(knownItems),
                edgeCount,
                edgesWithFluids,
                fluidOnlyEdges,
                distinctFluids,
                containerSuspects,
                fluidUnresolvedEdges,
                completeFlipEnabled);
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
