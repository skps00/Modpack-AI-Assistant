package com.skps9.packai.client.routes;

import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;
import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;
import com.skps9.packai.logic.ObtainGraph;
import com.skps9.packai.logic.ObtainPaths;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Harness for {@link RouteStoreCodec}. */
public final class RouteStoreCodecCheck {
    private RouteStoreCodecCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            casePutOverwriteSameItemId();
            casePutCapTwentyDropsOldest();
            caseDecodeBadJsonEmpty();
            caseCapByBytesShrinks();
            caseFromPathDirectionAndInputs();
            caseCreatedAtSortNewestFirst();
            System.out.println("RouteStoreCodecCheck OK");
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void casePutOverwriteSameItemId() {
        SavedRoute old =
                route("minecraft:stick", "2020-01-01T00:00:00Z", false, List.of(step("a", "b", "c", null, List.of())));
        SavedRoute neu =
                route("minecraft:stick", "2021-01-01T00:00:00Z", true, List.of(step("x", "y", "z", null, List.of())));
        List<SavedRoute> out = RouteStoreCodec.put(List.of(old), neu);
        if (out.size() != 1) {
            fail("overwrite size " + out.size());
        }
        if (!out.get(0).createdAt().equals("2021-01-01T00:00:00Z") || !out.get(0).complete()) {
            fail("overwrite must keep new row: " + out.get(0));
        }
    }

    private static void casePutCapTwentyDropsOldest() {
        List<SavedRoute> seed = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            seed.add(route("minecraft:item_" + i, isoDay(i), true, List.of()));
        }
        SavedRoute add = route("minecraft:new", "2099-12-31T00:00:00Z", true, List.of());
        List<SavedRoute> out = RouteStoreCodec.put(seed, add);
        if (out.size() != 20) {
            fail("cap 20 got " + out.size());
        }
        if (out.get(0).itemId().equals("minecraft:new") == false) {
            fail("newest must be first: " + out.get(0).itemId());
        }
        boolean hasOldest = false;
        for (SavedRoute r : out) {
            if ("minecraft:item_0".equals(r.itemId())) {
                hasOldest = true;
            }
        }
        if (hasOldest) {
            fail("oldest item_0 must be dropped");
        }
    }

    private static void caseDecodeBadJsonEmpty() {
        if (!RouteStoreCodec.decode("{").isEmpty()) {
            fail("bad json");
        }
        if (!RouteStoreCodec.decode("").isEmpty()) {
            fail("empty");
        }
        String mixed =
                "[{\"itemId\":\"minecraft:coal\",\"createdAt\":\"2020-01-02T00:00:00Z\",\"complete\":true,"
                        + "\"modsStamp\":\"ab\",\"steps\":[]},"
                        + "null,"
                        + "{\"itemId\":\"\",\"createdAt\":\"x\",\"complete\":false,\"modsStamp\":\"x\",\"steps\":[]}]";
        List<SavedRoute> rows = RouteStoreCodec.decode(mixed);
        if (rows.size() != 1 || !"minecraft:coal".equals(rows.get(0).itemId())) {
            fail("per-entry tolerance got " + rows);
        }
    }

    private static void caseCapByBytesShrinks() {
        List<SavedRoute> huge = new ArrayList<>();
        String blob = "x".repeat(20_000);
        for (int i = 0; i < 30; i++) {
            huge.add(
                    route(
                            "minecraft:heavy_" + i,
                            isoDay(100 + i),
                            true,
                            List.of(step("p:r" + i, "minecraft:crafting", "minecraft:stone", blob, List.of(blob)))));
        }
        List<SavedRoute> capped = RouteStoreCodec.capByBytes(huge);
        if (capped.isEmpty()) {
            fail("must keep at least one");
        }
        if (RouteStoreCodec.encode(capped).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > RouteStoreCodec.MAX_FILE_BYTES) {
            fail("still over cap");
        }
        if (capped.size() >= huge.size()) {
            fail("must shrink");
        }
    }

    private static void caseFromPathDirectionAndInputs() {
        Item product = Items.IRON_PICKAXE;
        Item mid = Items.IRON_INGOT;
        Item raw = Items.RAW_IRON;
        ObtainGraph.Edge e1 = edge("packai:pick", product, mid);
        ObtainGraph.Edge e2 = edge("packai:ingot", mid, raw);
        List<List<Item>> g1 = List.of(List.of(mid));
        List<List<Item>> g2 = List.of(List.of(raw, Items.COAL));
        ObtainPaths.Path path =
                new ObtainPaths.Path(
                        product,
                        List.of(new ObtainPaths.Step(e1, g1), new ObtainPaths.Step(e2, g2)),
                        true);
        SavedRoute saved =
                RouteStoreCodec.fromPath("minecraft:iron_pickaxe", "2022-06-01T12:00:00Z", "stamp1", path);
        if (saved.steps().size() != 2) {
            fail("steps " + saved.steps().size());
        }
        StepDto first = saved.steps().get(0);
        StepDto second = saved.steps().get(1);
        if (!"packai:pick".equals(first.recipeId()) || !"minecraft:iron_pickaxe".equals(first.output())) {
            fail("first step product->raw order broken: " + first);
        }
        if (!"minecraft:iron_ingot".equals(first.inputs().get(0))) {
            fail("inputs group0: " + first.inputs());
        }
        if (!"minecraft:raw_iron".equals(second.inputs().get(0))) {
            fail("inputs must be group[0] not alt: " + second.inputs());
        }
        if (!saved.complete()) {
            fail("complete flag");
        }
    }

    private static void caseCreatedAtSortNewestFirst() {
        SavedRoute a = route("minecraft:a", "2020-01-03T00:00:00Z", true, List.of());
        SavedRoute b = route("minecraft:b", "2020-01-01T00:00:00Z", true, List.of());
        SavedRoute c = route("minecraft:c", "2020-01-02T00:00:00Z", true, List.of());
        List<SavedRoute> out = RouteStoreCodec.put(RouteStoreCodec.put(List.of(a), b), c);
        if (out.size() != 3) {
            fail(String.valueOf(out.size()));
        }
        if (!"minecraft:a".equals(out.get(0).itemId())) {
            fail("newest first got " + out.get(0).itemId());
        }
        if (!"minecraft:c".equals(out.get(1).itemId())) {
            fail("middle " + out.get(1).itemId());
        }
        if (!"minecraft:b".equals(out.get(2).itemId())) {
            fail("oldest last " + out.get(2).itemId());
        }
    }

    private static String isoDay(int day) {
        return String.format("2020-01-%02dT00:00:00Z", (day % 28) + 1);
    }

    private static SavedRoute route(String itemId, String createdAt, boolean complete, List<StepDto> steps) {
        return new SavedRoute(itemId, createdAt, complete, "stamp", steps);
    }

    private static StepDto step(
            String recipeId, String typeId, String output, String actionLabel, List<String> inputs) {
        return new StepDto(recipeId, typeId, output, actionLabel, inputs, List.of());
    }

    private static ObtainGraph.Edge edge(String id, Item out, Item in) {
        return new ObtainGraph.Edge(
                new ResourceLocation(id),
                new ResourceLocation("minecraft", "crafting"),
                out,
                1,
                List.of(List.of(in)),
                null,
                List.of(),
                false);
    }
}
