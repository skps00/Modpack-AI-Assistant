package com.skps9.packai.client.routes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;
import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;

/** Caps for {@link RouteStoreCodec#capByBytes} and {@link RouteStore#loadFromFile}. */
public final class RouteStoreCapsCheck {
    private RouteStoreCapsCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        try {
            caseSingleRouteStepCapKeepsOthers();
            caseFileCapDropsOldestKeepsOne();
            caseDecodeLegacyWithoutTruncatedField();
            caseLoadOversizeRenamesCorrupt();
            caseNegativeControlLegacyClear();
            System.out.println("RouteStoreCapsCheck OK");
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void caseSingleRouteStepCapKeepsOthers() {
        String blob = "w".repeat(400);
        List<StepDto> steps = new ArrayList<>(300);
        for (int i = 0; i < 300; i++) {
            steps.add(step("p:r" + i, "minecraft:crafting", "minecraft:stone", blob, List.of(blob)));
        }
        SavedRoute huge = route("minecraft:huge", "2099-01-03T00:00:00Z", true, steps);
        SavedRoute small = route("minecraft:stick", "2099-01-02T00:00:00Z", true, List.of());
        List<SavedRoute> out = RouteStoreCodec.capByBytes(List.of(huge, small));
        if (out.isEmpty()) {
            fail("must not clear library on single oversize route");
        }
        if (out.size() != 2) {
            fail("expected both routes, got " + out.size());
        }
        SavedRoute trimmed = out.get(0);
        if (!"minecraft:huge".equals(trimmed.itemId())) {
            fail("newest first: " + trimmed.itemId());
        }
        if (!trimmed.truncated()) {
            fail("huge route must be truncated");
        }
        if (trimmed.steps().size() != RouteStoreCodec.MAX_STEPS_PER_ROUTE) {
            fail("steps want=" + RouteStoreCodec.MAX_STEPS_PER_ROUTE + " got=" + trimmed.steps().size());
        }
        boolean hasStick = false;
        for (SavedRoute r : out) {
            if ("minecraft:stick".equals(r.itemId())) {
                hasStick = true;
            }
        }
        if (!hasStick) {
            fail("small route must survive");
        }
    }

    private static void caseFileCapDropsOldestKeepsOne() {
        String blob = "z".repeat(95_000);
        List<SavedRoute> routes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            routes.add(
                    route(
                            "minecraft:heavy_" + i,
                            String.format("2020-01-%02dT00:00:00Z", i + 1),
                            true,
                            List.of(step("p:x" + i, "minecraft:crafting", "minecraft:stone", blob, List.of(blob)))));
        }
        List<SavedRoute> out = RouteStoreCodec.capByBytes(routes);
        if (out.isEmpty()) {
            fail("must keep at least one route");
        }
        if (out.size() >= routes.size()) {
            fail("must drop oldest when over file cap");
        }
        if (!"minecraft:heavy_2".equals(out.get(0).itemId())) {
            fail("newest heavy_2 first, got " + out.get(0).itemId());
        }
    }

    private static void caseDecodeLegacyWithoutTruncatedField() {
        String json =
                "[{\"itemId\":\"minecraft:coal\",\"createdAt\":\"2020-01-02T00:00:00Z\",\"complete\":true,"
                        + "\"modsStamp\":\"ab\",\"steps\":[]}]";
        List<SavedRoute> rows = RouteStoreCodec.decode(json);
        if (rows.size() != 1 || rows.get(0).truncated()) {
            fail("legacy decode truncated=" + (rows.isEmpty() ? "empty" : rows.get(0).truncated()));
        }
    }

    private static void caseLoadOversizeRenamesCorrupt() throws IOException {
        Path dir = Files.createTempDirectory("packai-route-cap");
        Path file = dir.resolve("test-world.json");
        byte[] payload = new byte[RouteStoreCodec.MAX_FILE_BYTES + 1];
        Files.write(file, payload);
        List<SavedRoute> loaded = RouteStore.loadFromFile(file, "test-world");
        if (!loaded.isEmpty()) {
            fail("oversize load must return empty");
        }
        if (Files.isRegularFile(file)) {
            fail("oversize file must be renamed away");
        }
        try (var stream = Files.list(dir)) {
            boolean corrupt = stream.anyMatch(p -> p.getFileName().toString().contains(".corrupt-"));
            if (!corrupt) {
                fail("expected .corrupt- backup in " + dir);
            }
        }
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // temp cleanup best-effort
            }
        });
    }

    /** Mirrors pre-fix {@code capByBytes} that cleared the library on a single oversize route. */
    private static List<SavedRoute> capByBytesLegacyClear(List<SavedRoute> routes) {
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }
        List<SavedRoute> list = new ArrayList<>(routes);
        while (RouteStoreCodec.encode(list).getBytes(StandardCharsets.UTF_8).length
                        > RouteStoreCodec.MAX_FILE_BYTES
                && list.size() > 1) {
            list.remove(list.size() - 1);
        }
        if (RouteStoreCodec.encode(list).getBytes(StandardCharsets.UTF_8).length
                > RouteStoreCodec.MAX_FILE_BYTES) {
            return List.of();
        }
        return List.copyOf(list);
    }

    private static void caseNegativeControlLegacyClear() {
        String blob = "w".repeat(400);
        List<StepDto> steps = new ArrayList<>(300);
        for (int i = 0; i < 300; i++) {
            steps.add(step("p:r" + i, "minecraft:crafting", "minecraft:stone", blob, List.of(blob)));
        }
        SavedRoute huge = route("minecraft:huge", "2099-01-03T00:00:00Z", true, steps);
        SavedRoute small = route("minecraft:stick", "2099-01-02T00:00:00Z", true, List.of());
        List<SavedRoute> legacy = capByBytesLegacyClear(List.of(huge, small));
        if (!legacy.isEmpty()) {
            fail("negative control: legacy capByBytes must clear on single oversize route");
        }
    }

    private static SavedRoute route(String itemId, String createdAt, boolean complete, List<StepDto> steps) {
        return new SavedRoute(itemId, createdAt, complete, "stamp", steps);
    }

    private static StepDto step(
            String recipeId, String typeId, String output, String actionLabel, List<String> inputs) {
        return new StepDto(recipeId, typeId, output, actionLabel, inputs, List.of());
    }
}
