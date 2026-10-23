package com.skps9.packai.logic;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

/**
 * Headless checks for {@link ObtainGraphCache#decodeGraph} edge field guards and count mismatch warn path.
 */
public final class ObtainGraphCacheDecodeCheck {
    private ObtainGraphCacheDecodeCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static void eq(Object want, Object got, String what) {
        if (want == null ? got != null : !want.equals(got)) {
            fail(what + ": want=" + want + " got=" + got);
        }
    }

    private static int countDecodedEdges(ObtainGraph graph) {
        int n = 0;
        for (List<ObtainGraph.Edge> list : graph.byOutput().values()) {
            n += list.size();
        }
        return n;
    }

    private static JsonObject graphShell(int edgeCount) {
        JsonObject root = new JsonObject();
        root.addProperty("edgeCount", edgeCount);
        root.addProperty("edgesWithFluids", 0);
        root.addProperty("fluidOnlyEdges", 0);
        root.addProperty("distinctFluids", 0);
        root.addProperty("containerSuspects", 0);
        root.addProperty("fluidUnresolvedEdges", 0);
        root.addProperty("completeFlipEnabled", false);
        JsonArray items = new JsonArray();
        items.add("minecraft:brick");
        root.add("items", items);
        root.add("fluidProducers", new JsonArray());
        return root;
    }

    private static JsonObject baseEdge() {
        JsonObject o = new JsonObject();
        o.addProperty("recipeId", "packai:decode_check");
        o.addProperty("typeId", "minecraft:crafting");
        o.addProperty("output", "minecraft:brick");
        o.addProperty("outputCount", 1);
        JsonArray groups = new JsonArray();
        JsonArray g0 = new JsonArray();
        g0.add("minecraft:clay_ball");
        groups.add(g0);
        o.add("inputGroups", groups);
        JsonArray counts = new JsonArray();
        counts.add(4);
        o.add("inputCounts", counts);
        o.add("fluids", new JsonArray());
        o.add("requirements", new JsonArray());
        o.addProperty("inputsPartial", false);
        return o;
    }

    private static ObtainGraph decodeOneEdge(JsonObject edge, int headerEdgeCount) {
        JsonObject root = graphShell(headerEdgeCount);
        JsonArray edges = new JsonArray();
        edges.add(edge);
        root.add("edges", edges);
        return ObtainGraphCache.decodeGraph(root);
    }

    /** Normal edge with inputCounts array → one edge decoded. */
    private static void caseNormalInputCountsArray() {
        ObtainGraph graph = decodeOneEdge(baseEdge(), 1);
        if (graph == null) {
            fail("case1: decode returned null");
        }
        eq(1, countDecodedEdges(graph), "case1 edge count");
        List<ObtainGraph.Edge> list = graph.edgesFor(Items.BRICK);
        if (list.isEmpty()) {
            fail("case1: no edges for brick");
        }
        eq(List.of(4), list.get(0).inputCounts(), "case1 inputCounts");
    }

    /** inputCounts JSON null → no throw, edge kept, counts empty (downstream treats as 1). */
    private static void caseInputCountsNull() {
        JsonObject edge = baseEdge();
        edge.add("inputCounts", JsonNull.INSTANCE);
        ObtainGraph graph = decodeOneEdge(edge, 1);
        if (graph == null) {
            fail("case2: decode returned null");
        }
        eq(1, countDecodedEdges(graph), "case2 edge count");
        eq(List.of(1), graph.edgesFor(Items.BRICK).get(0).inputCounts(), "case2 inputCounts fallback 1");
    }

    /** inputCounts string → no throw, edge kept. */
    private static void caseInputCountsString() {
        JsonObject edge = baseEdge();
        edge.addProperty("inputCounts", "x");
        ObtainGraph graph = decodeOneEdge(edge, 1);
        if (graph == null) {
            fail("case3: decode returned null");
        }
        eq(1, countDecodedEdges(graph), "case3 edge count");
        eq(List.of(1), graph.edgesFor(Items.BRICK).get(0).inputCounts(), "case3 inputCounts fallback 1");
    }

    /** inputGroups missing → no throw. */
    private static void caseInputGroupsMissing() {
        JsonObject edge = baseEdge();
        edge.remove("inputGroups");
        ObtainGraph graph = decodeOneEdge(edge, 1);
        if (graph == null) {
            fail("case4: decode returned null");
        }
        eq(1, countDecodedEdges(graph), "case4 edge count");
        eq(List.of(), graph.edgesFor(Items.BRICK).get(0).inputGroups(), "case4 inputGroups empty");
    }

    /** header edgeCount=2 but one edge in array → decode ok, one edge. */
    private static void caseHeaderCountMismatch() {
        ObtainGraph graph = decodeOneEdge(baseEdge(), 2);
        if (graph == null) {
            fail("case5: decode returned null");
        }
        eq(1, countDecodedEdges(graph), "case5 decoded edge count");
        eq(2, graph.edgeCount(), "case5 header edgeCount preserved");
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        caseNormalInputCountsArray();
        caseInputCountsNull();
        caseInputCountsString();
        caseInputGroupsMissing();
        caseHeaderCountMismatch();
        System.out.println("check ObtainGraphCacheDecodeCheck OK");
    }
}
