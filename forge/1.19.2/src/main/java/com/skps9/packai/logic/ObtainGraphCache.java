package com.skps9.packai.logic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Disk cache for {@link ObtainGraph} ({@code config/packai/obtain_graph_cache.json}).
 * Fail-soft: corrupt IO never propagates to callers.
 */
public final class ObtainGraphCache {
    public static final int SCHEMA_VERSION = 1;
    public static final int BUILDER_VERSION = 1;
    public static final int MAX_CACHE_BYTES = 8 * 1024 * 1024;

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public enum LoadKind {
        HIT,
        MISS,
        STALE,
        CORRUPT
    }

    public record LoadResult(LoadKind kind, ObtainGraph graph) {
        public LoadResult {
            graph = kind == LoadKind.HIT ? graph : null;
        }
    }

    private ObtainGraphCache() {}

    public static Path cacheFile(Path gameDir) {
        return gameDir.resolve("config/packai/obtain_graph_cache.json");
    }

    public static Path defaultCacheFile() {
        return cacheFile(FMLPaths.GAMEDIR.get());
    }

    /** Current environment fingerprint (modsStamp + versions + schema + builder + datapack hash). */
    public static String computeFingerprint(Path gameDir) {
        String modsStamp = ObtainGraphRuntime.modsStamp();
        String mcVersion = SharedConstants.getCurrentVersion().getName();
        String forgeVersion = forgeVersionString();
        String datapackHash = datapackDirHash(gameDir);
        return joinFingerprint(modsStamp, mcVersion, forgeVersion, datapackHash);
    }

    static String joinFingerprint(
            String modsStamp, String mcVersion, String forgeVersion, String datapackHash) {
        return modsStamp
                + "|"
                + mcVersion
                + "|"
                + forgeVersion
                + "|"
                + SCHEMA_VERSION
                + "|"
                + BUILDER_VERSION
                + "|"
                + datapackHash;
    }

    public static boolean fingerprintMatches(String stored, String current) {
        if (stored == null || current == null) {
            return false;
        }
        return stored.equals(current);
    }

    public static LoadResult tryLoad(String wantFingerprint, int wantRecipeCount) {
        return tryLoad(defaultCacheFile(), wantFingerprint, wantRecipeCount, MAX_CACHE_BYTES);
    }

    static LoadResult tryLoad(Path file, String wantFingerprint, int wantRecipeCount, long maxBytesIgnored) {
        if (file == null || wantFingerprint == null) {
            LOGGER.info("obtain.cache_load=miss");
            return new LoadResult(LoadKind.MISS, null);
        }
        if (!Files.isRegularFile(file)) {
            LOGGER.info("obtain.cache_load=miss");
            return new LoadResult(LoadKind.MISS, null);
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (!root.has("schemaVersion")
                    || root.get("schemaVersion").getAsInt() != SCHEMA_VERSION) {
                LOGGER.info("obtain.cache.corrupt reason=schema");
                LOGGER.info("obtain.cache_load=corrupt");
                return new LoadResult(LoadKind.CORRUPT, null);
            }
            String fp = root.has("fingerprint") ? root.get("fingerprint").getAsString() : "";
            if (!fingerprintMatches(fp, wantFingerprint)) {
                LOGGER.info("obtain.cache_load=miss");
                return new LoadResult(LoadKind.MISS, null);
            }
            int recipeCount = root.has("recipeCount") ? root.get("recipeCount").getAsInt() : -1;
            if (recipeCount != wantRecipeCount) {
                LOGGER.info("obtain.cache.stale disk={} live={}", recipeCount, wantRecipeCount);
                LOGGER.info("obtain.cache_load=stale");
                return new LoadResult(LoadKind.STALE, null);
            }
            ObtainGraph graph = decodeGraph(root);
            if (graph == null) {
                LOGGER.info("obtain.cache.corrupt reason=decode");
                LOGGER.info("obtain.cache_load=corrupt");
                return new LoadResult(LoadKind.CORRUPT, null);
            }
            LOGGER.info("obtain.cache_load=hit edges={}", graph.edgeCount());
            return new LoadResult(LoadKind.HIT, graph);
        } catch (Throwable t) {
            LOGGER.info("obtain.cache.corrupt", t);
            LOGGER.info("obtain.cache_load=corrupt");
            return new LoadResult(LoadKind.CORRUPT, null);
        }
    }

    public static void tryWrite(ObtainGraph graph, String fingerprint, int recipeCount) {
        tryWrite(defaultCacheFile(), graph, fingerprint, recipeCount, MAX_CACHE_BYTES);
    }

    static void tryWrite(Path file, ObtainGraph graph, String fingerprint, int recipeCount, long maxBytes) {
        if (file == null || graph == null || fingerprint == null) {
            LOGGER.info("obtain.cache_write=error");
            return;
        }
        try {
            JsonObject root = encodeGraph(graph, fingerprint, recipeCount);
            String json = GSON.toJson(root);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxBytes) {
                LOGGER.info("obtain.cache.skip size={}", bytes.length);
                LOGGER.info("obtain.cache_write=skip");
                return;
            }
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, json, StandardCharsets.UTF_8);
            LOGGER.info("obtain.cache_write=ok bytes={}", bytes.length);
        } catch (Throwable t) {
            LOGGER.info("obtain.cache_write=error", t);
        }
    }

    static JsonObject encodeGraph(ObtainGraph graph, String fingerprint, int recipeCount) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("fingerprint", fingerprint);
        root.addProperty("recipeCount", recipeCount);
        root.addProperty("builtAt", Long.toString(System.currentTimeMillis()));
        root.addProperty("edgeCount", graph.edgeCount());
        root.addProperty("edgesWithFluids", graph.edgesWithFluids());
        root.addProperty("fluidOnlyEdges", graph.fluidOnlyEdges());
        root.addProperty("distinctFluids", graph.distinctFluids());
        root.addProperty("containerSuspects", graph.containerSuspects());
        root.addProperty("fluidUnresolvedEdges", graph.fluidUnresolvedEdges());
        root.addProperty("completeFlipEnabled", graph.completeFlipEnabled());

        JsonArray items = new JsonArray();
        List<String> known = new ArrayList<>();
        for (Item item : graph.knownItems()) {
            known.add(ObtainGraph.itemKey(item));
        }
        known.sort(String::compareTo);
        for (String id : known) {
            items.add(id);
        }
        root.add("items", items);

        JsonArray edges = new JsonArray();
        List<Item> outputs = new ArrayList<>(graph.byOutput().keySet());
        outputs.sort(Comparator.comparing(ObtainGraph::itemKey));
        for (Item output : outputs) {
            for (ObtainGraph.Edge e : graph.byOutput().get(output)) {
                edges.add(encodeEdge(e));
            }
        }
        root.add("edges", edges);

        JsonArray fluidProducers = new JsonArray();
        List<String> fluidIds = new ArrayList<>(graph.fluidProducers().keySet());
        fluidIds.sort(String::compareTo);
        for (String fluidId : fluidIds) {
            JsonObject block = new JsonObject();
            block.addProperty("fluidId", fluidId);
            JsonArray feList = new JsonArray();
            for (ObtainGraph.FluidEdge fe : graph.fluidProducers().get(fluidId)) {
                feList.add(encodeFluidEdge(fe));
            }
            block.add("edges", feList);
            fluidProducers.add(block);
        }
        root.add("fluidProducers", fluidProducers);
        return root;
    }

    private static JsonObject encodeEdge(ObtainGraph.Edge e) {
        JsonObject o = new JsonObject();
        o.addProperty("recipeId", e.recipeId().toString());
        o.addProperty("typeId", e.typeId().toString());
        o.addProperty("output", ObtainGraph.itemKey(e.output()));
        o.addProperty("outputCount", e.outputCount());
        o.add("inputGroups", encodeItemGroups(e.inputGroups()));
        if (e.actionLabel() != null) {
            o.addProperty("actionLabel", e.actionLabel());
        }
        o.add("fluids", encodeFluidReqs(e.fluids()));
        o.add("requirements", encodeRequirements(e.requirements()));
        o.addProperty("inputsPartial", e.inputsPartial());
        return o;
    }

    private static JsonObject encodeFluidEdge(ObtainGraph.FluidEdge fe) {
        JsonObject o = new JsonObject();
        o.addProperty("recipeId", fe.recipeId().toString());
        o.addProperty("typeId", fe.typeId().toString());
        o.addProperty("fluidId", fe.fluidId());
        o.addProperty("amountMb", fe.amountMb());
        o.add("itemGroups", encodeItemGroups(fe.itemGroups()));
        o.add("fluidInputs", encodeFluidReqs(fe.fluidInputs()));
        if (fe.actionLabel() != null) {
            o.addProperty("actionLabel", fe.actionLabel());
        }
        return o;
    }

    private static JsonArray encodeItemGroups(List<List<Item>> groups) {
        JsonArray arr = new JsonArray();
        if (groups == null) {
            return arr;
        }
        for (List<Item> group : groups) {
            JsonArray g = new JsonArray();
            if (group != null) {
                for (Item item : group) {
                    g.add(ObtainGraph.itemKey(item));
                }
            }
            arr.add(g);
        }
        return arr;
    }

    private static JsonArray encodeFluidReqs(List<ObtainGraph.FluidReq> fluids) {
        JsonArray arr = new JsonArray();
        if (fluids == null) {
            return arr;
        }
        for (ObtainGraph.FluidReq fr : fluids) {
            if (fr == null) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("fluidId", fr.fluidId());
            o.addProperty("amountMb", fr.amountMb());
            arr.add(o);
        }
        return arr;
    }

    private static JsonArray encodeRequirements(List<ObtainGraph.Requirement> reqs) {
        JsonArray arr = new JsonArray();
        if (reqs == null) {
            return arr;
        }
        for (ObtainGraph.Requirement r : reqs) {
            if (r == null) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("kind", r.kind());
            o.addProperty("id", r.id());
            o.addProperty("amount", r.amount());
            if (r.unit() != null) {
                o.addProperty("unit", r.unit());
            }
            arr.add(o);
        }
        return arr;
    }

    static ObtainGraph decodeGraph(JsonObject root) {
        try {
            int edgeCount = root.get("edgeCount").getAsInt();
            int edgesWithFluids = root.get("edgesWithFluids").getAsInt();
            int fluidOnlyEdges = root.get("fluidOnlyEdges").getAsInt();
            int distinctFluids = root.get("distinctFluids").getAsInt();
            int containerSuspects = root.get("containerSuspects").getAsInt();
            int fluidUnresolvedEdges = root.get("fluidUnresolvedEdges").getAsInt();
            boolean completeFlipEnabled = root.get("completeFlipEnabled").getAsBoolean();

            Set<Item> knownItems = new LinkedHashSet<>();
            if (root.has("items")) {
                for (JsonElement el : root.getAsJsonArray("items")) {
                    Item item = resolveItem(el.getAsString());
                    if (item != null) {
                        knownItems.add(item);
                    }
                }
            }

            Map<Item, List<ObtainGraph.Edge>> byOutput = new LinkedHashMap<>();
            if (root.has("edges")) {
                for (JsonElement el : root.getAsJsonArray("edges")) {
                    ObtainGraph.Edge edge = decodeEdge(el.getAsJsonObject());
                    if (edge == null) {
                        continue;
                    }
                    byOutput.computeIfAbsent(edge.output(), k -> new ArrayList<>()).add(edge);
                    knownItems.add(edge.output());
                }
            }
            Map<Item, List<ObtainGraph.Edge>> frozen = new LinkedHashMap<>();
            for (Map.Entry<Item, List<ObtainGraph.Edge>> e : byOutput.entrySet()) {
                frozen.put(e.getKey(), List.copyOf(e.getValue()));
            }

            Map<String, List<ObtainGraph.FluidEdge>> fluidProducers = new LinkedHashMap<>();
            if (root.has("fluidProducers")) {
                for (JsonElement blockEl : root.getAsJsonArray("fluidProducers")) {
                    JsonObject block = blockEl.getAsJsonObject();
                    String fluidId = block.get("fluidId").getAsString();
                    List<ObtainGraph.FluidEdge> list = new ArrayList<>();
                    if (block.has("edges")) {
                        for (JsonElement feEl : block.getAsJsonArray("edges")) {
                            ObtainGraph.FluidEdge fe = decodeFluidEdge(feEl.getAsJsonObject());
                            if (fe != null) {
                                list.add(fe);
                            }
                        }
                    }
                    fluidProducers.put(fluidId, List.copyOf(list));
                }
            }

            return ObtainGraph.fromCached(
                    frozen,
                    fluidProducers,
                    knownItems,
                    edgeCount,
                    edgesWithFluids,
                    fluidOnlyEdges,
                    distinctFluids,
                    containerSuspects,
                    fluidUnresolvedEdges,
                    completeFlipEnabled);
        } catch (Throwable t) {
            LOGGER.info("obtain.cache.corrupt reason=graph", t);
            return null;
        }
    }

    private static ObtainGraph.Edge decodeEdge(JsonObject o) {
        try {
            ResourceLocation recipeId = new ResourceLocation(o.get("recipeId").getAsString());
            ResourceLocation typeId = new ResourceLocation(o.get("typeId").getAsString());
            Item output = resolveItem(o.get("output").getAsString());
            if (output == null) {
                return null;
            }
            int outputCount = o.get("outputCount").getAsInt();
            List<List<Item>> inputGroups = decodeItemGroups(o.getAsJsonArray("inputGroups"));
            String actionLabel = o.has("actionLabel") && !o.get("actionLabel").isJsonNull()
                    ? o.get("actionLabel").getAsString()
                    : null;
            List<ObtainGraph.FluidReq> fluids = decodeFluidReqs(o.getAsJsonArray("fluids"));
            List<ObtainGraph.Requirement> requirements = decodeRequirements(o.getAsJsonArray("requirements"));
            boolean inputsPartial = o.has("inputsPartial") && o.get("inputsPartial").getAsBoolean();
            return new ObtainGraph.Edge(
                    recipeId,
                    typeId,
                    output,
                    outputCount,
                    inputGroups,
                    actionLabel,
                    fluids,
                    requirements,
                    inputsPartial);
        } catch (Throwable t) {
            return null;
        }
    }

    private static ObtainGraph.FluidEdge decodeFluidEdge(JsonObject o) {
        try {
            ResourceLocation recipeId = new ResourceLocation(o.get("recipeId").getAsString());
            ResourceLocation typeId = new ResourceLocation(o.get("typeId").getAsString());
            String fluidId = o.get("fluidId").getAsString();
            int amountMb = o.get("amountMb").getAsInt();
            List<List<Item>> itemGroups = decodeItemGroups(o.getAsJsonArray("itemGroups"));
            List<ObtainGraph.FluidReq> fluidInputs = decodeFluidReqs(o.getAsJsonArray("fluidInputs"));
            String actionLabel = o.has("actionLabel") && !o.get("actionLabel").isJsonNull()
                    ? o.get("actionLabel").getAsString()
                    : null;
            return new ObtainGraph.FluidEdge(
                    recipeId, typeId, fluidId, amountMb, itemGroups, fluidInputs, actionLabel);
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<List<Item>> decodeItemGroups(JsonArray arr) {
        List<List<Item>> groups = new ArrayList<>();
        if (arr == null) {
            return List.of();
        }
        for (JsonElement gEl : arr) {
            JsonArray g = gEl.getAsJsonArray();
            List<Item> group = new ArrayList<>();
            for (JsonElement idEl : g) {
                Item item = resolveItem(idEl.getAsString());
                if (item != null) {
                    group.add(item);
                }
            }
            groups.add(List.copyOf(group));
        }
        return List.copyOf(groups);
    }

    private static List<ObtainGraph.FluidReq> decodeFluidReqs(JsonArray arr) {
        List<ObtainGraph.FluidReq> out = new ArrayList<>();
        if (arr == null) {
            return List.of();
        }
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            out.add(new ObtainGraph.FluidReq(o.get("fluidId").getAsString(), o.get("amountMb").getAsInt()));
        }
        return List.copyOf(out);
    }

    private static List<ObtainGraph.Requirement> decodeRequirements(JsonArray arr) {
        List<ObtainGraph.Requirement> out = new ArrayList<>();
        if (arr == null) {
            return List.of();
        }
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            String unit = o.has("unit") && !o.get("unit").isJsonNull() ? o.get("unit").getAsString() : "";
            out.add(new ObtainGraph.Requirement(
                    o.get("kind").getAsString(),
                    o.get("id").getAsString(),
                    o.get("amount").getAsInt(),
                    unit));
        }
        return List.copyOf(out);
    }

    private static Item resolveItem(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        return ForgeRegistries.ITEMS.getValue(key);
    }

    static String datapackDirHash(Path gameDir) {
        try {
            if (gameDir == null) {
                return "unknown";
            }
            Path dp = gameDir.resolve("datapacks");
            if (!Files.isDirectory(dp)) {
                return "empty";
            }
            List<String> lines = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(dp)) {
                walk.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(p -> dp.relativize(p).toString()))
                        .forEach(p -> {
                            try {
                                String rel = dp.relativize(p).toString().replace('\\', '/');
                                lines.add(rel + ":" + Files.size(p));
                            } catch (IOException ignored) {
                                // skip
                            }
                        });
            }
            if (lines.isEmpty()) {
                return "empty";
            }
            lines.sort(String::compareTo);
            return sha1Hex(String.join("\n", lines));
        } catch (Throwable t) {
            LOGGER.info("obtain.cache datapack hash failed", t);
            return "unknown";
        }
    }

    private static String forgeVersionString() {
        try {
            Class<?> modListCl = Class.forName("net.minecraftforge.fml.ModList");
            Object modList = modListCl.getMethod("get").invoke(null);
            Object opt = modListCl.getMethod("getModContainerById", String.class).invoke(modList, "forge");
            if (opt == null) {
                return "unknown";
            }
            boolean present = (boolean) opt.getClass().getMethod("isPresent").invoke(opt);
            if (!present) {
                return "unknown";
            }
            Object container = opt.getClass().getMethod("get").invoke(opt);
            Object info = container.getClass().getMethod("getModInfo").invoke(container);
            Object ver = info.getClass().getMethod("getVersion").invoke(info);
            return ver == null ? "unknown" : ver.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static String sha1Hex(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
