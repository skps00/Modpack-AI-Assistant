package com.skps9.packai.client.autotest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.emi.EmiFavoriteBridge;
import com.skps9.packai.client.jei.JeiBookmarkBridge;
import com.skps9.packai.client.routes.ItemManagerBridge;
import com.skps9.packai.client.routes.RouteStore;
import com.skps9.packai.client.routes.RouteStoreCodec;
import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;
import com.skps9.packai.logic.ObtainGraphRuntime;
import com.skps9.packai.logic.ObtainPathService;
import com.skps9.packai.logic.ObtainPaths;
import com.skps9.packai.logic.RouteMarks;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Autotest lane: route library save + JEI/EMI bookmark injection (no GUI). */
public final class RouteMarksAutotest {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final JeiBookmarkBridge JEI = new JeiBookmarkBridge();
    private static final EmiFavoriteBridge EMI = new EmiFavoriteBridge();
    /** ~60s at 20 TPS; worker cold build can exceed the old 10s tick-budget wait. */
    private static final int GRAPH_READY_MAX_TICKS = 1200;

    private static final List<PendingCase> pending = new ArrayList<>();

    private RouteMarksAutotest() {}

    public static void recordCase(
            Minecraft mc, List<JsonObject> artifact, String caseId, String itemId) {
        String item = itemId == null ? "" : itemId;
        if (ObtainGraphRuntime.INSTANCE.ready()) {
            commitCase(mc, artifact, caseId, item);
            return;
        }
        ObtainGraphRuntime.INSTANCE.requestClientRebuildIfNeeded();
        pending.add(new PendingCase(mc, artifact, caseId, item));
    }

    /** Client tick: resolve {@link #pending} when the obtain graph becomes ready or times out. */
    public static void tickPending() {
        if (pending.isEmpty()) {
            return;
        }
        List<PendingCase> done = new ArrayList<>();
        for (PendingCase p : pending) {
            if (ObtainGraphRuntime.INSTANCE.ready()) {
                commitCase(p.mc, p.artifact, p.caseId, p.itemId);
                done.add(p);
                continue;
            }
            p.ticksWaited++;
            if (p.ticksWaited >= GRAPH_READY_MAX_TICKS) {
                p.artifact.add(graphNotReadyRow(p.caseId, p.itemId));
                writeArtifact(p.mc, p.artifact);
                done.add(p);
            }
        }
        pending.removeAll(done);
    }

    private static void commitCase(
            Minecraft mc, List<JsonObject> artifact, String caseId, String itemId) {
        JsonObject row = runRouteMarks(mc, caseId, itemId);
        artifact.add(row);
        writeArtifact(mc, artifact);
    }

    private static JsonObject graphNotReadyRow(String caseId, String itemId) {
        JsonObject o = new JsonObject();
        o.addProperty("case", caseId == null ? "" : caseId);
        o.addProperty("item", itemId);
        o.add("error", JsonNull.INSTANCE);
        o.addProperty("graphReady", false);
        o.addProperty("paths", 0);
        o.addProperty("steps", 0);
        o.addProperty("saved", false);
        return o;
    }

    private static final class PendingCase {
        final Minecraft mc;
        final List<JsonObject> artifact;
        final String caseId;
        final String itemId;
        int ticksWaited;

        PendingCase(Minecraft mc, List<JsonObject> artifact, String caseId, String itemId) {
            this.mc = mc;
            this.artifact = artifact;
            this.caseId = caseId;
            this.itemId = itemId;
        }
    }

    static JsonObject runRouteMarks(Minecraft mc, String caseId, String itemId) {
        JsonObject o = new JsonObject();
        o.addProperty("case", caseId == null ? "" : caseId);
        o.addProperty("item", itemId);
        o.add("error", JsonNull.INSTANCE);
        try {
            boolean graphReady = ObtainGraphRuntime.INSTANCE.ready();
            o.addProperty("graphReady", graphReady);
            if (!graphReady) {
                o.addProperty("paths", 0);
                o.addProperty("steps", 0);
                o.addProperty("saved", false);
                return o;
            }
            ObtainPathService.Result r = ObtainPathService.compute(itemId, "en_us", 400);
            int pathCount = r.paths() == null ? 0 : r.paths().size();
            o.addProperty("paths", pathCount);
            if (pathCount == 0) {
                o.addProperty("steps", 0);
                o.addProperty("saved", false);
                return o;
            }
            ObtainPaths.Path path = r.paths().get(0);
            int steps = path.steps() == null ? 0 : path.steps().size();
            o.addProperty("steps", steps);

            SavedRoute sr = RouteStoreCodec.fromPath(
                    itemId, Instant.now().toString(), RouteStore.modsStamp(), path);
            RouteStore.save(sr);
            boolean saved = false;
            for (SavedRoute loaded : RouteStore.load()) {
                if (loaded != null && itemId.equals(loaded.itemId())) {
                    saved = true;
                    break;
                }
            }
            o.addProperty("saved", saved);

            List<ItemStack> stacks = stacksFromMarks(r.paths());
            Path gameDir = gameDir(mc);

            String hashBefore = jeiConfigHash(gameDir);
            int injected = JEI.inject(stacks);
            List<String> jeiTraceLines = JeiBookmarkBridge.trace();
            String hashAfterInject = jeiConfigHash(gameDir);
            List<String> expectedUids = expectedUids(stacks);
            boolean orderMatches = orderMatches(expectedUids, JEI.currentOrder());
            int cleared = JEI.clear();
            String hashAfterClear = jeiConfigHash(gameDir);

            JsonObject ji = new JsonObject();
            ji.addProperty("profile", JeiBookmarkBridge.profileCode());
            ji.addProperty("capability", JEI.capability().name());
            ji.addProperty("injected", injected);
            ji.addProperty("orderMatches", orderMatches);
            ji.addProperty("cleared", cleared);
            ji.addProperty("fileHashBefore", hashBefore);
            ji.addProperty("fileHashAfterInject", hashAfterInject);
            ji.addProperty("fileHashAfterClear", hashAfterClear);
            JsonArray jeiTrace = new JsonArray();
            int traceLimit = Math.min(jeiTraceLines.size(), 40);
            for (int i = 0; i < traceLimit; i++) {
                jeiTrace.add(jeiTraceLines.get(i));
            }
            ji.add("jeiTrace", jeiTrace);
            o.add("ji", ji);

            JsonObject emi = new JsonObject();
            ItemManagerBridge.Capability emiCap = EMI.capability();
            emi.addProperty("capability", emiCap.name());
            if (emiCap == ItemManagerBridge.Capability.NONE) {
                emi.addProperty("injected", 0);
                emi.addProperty("cleared", 0);
            } else {
                int emiInjected = EMI.inject(stacks);
                int emiCleared = EMI.clear();
                emi.addProperty("injected", emiInjected);
                emi.addProperty("cleared", emiCleared);
            }
            o.add("emi", emi);
        } catch (Throwable t) {
            o.addProperty("error", errorText(t));
        }
        return o;
    }

    private static List<ItemStack> stacksFromMarks(List<ObtainPaths.Path> paths) {
        RouteMarks.Result marks = RouteMarks.build(paths);
        Set<Item> seen = new LinkedHashSet<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (RouteMarks.Mark mark : marks.marks()) {
            if (mark == null || mark.item() == null) {
                continue;
            }
            if (!seen.add(mark.item())) {
                continue;
            }
            stacks.add(new ItemStack(mark.item()));
            if (stacks.size() >= RouteMarks.MAX_MARKS) {
                break;
            }
        }
        return stacks;
    }

    private static List<String> expectedUids(List<ItemStack> stacks) {
        List<String> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                out.add(JeiBookmarkBridge.uid(stack));
            }
        }
        return out;
    }

    private static boolean orderMatches(List<String> expected, List<String> currentOrder) {
        if (expected.isEmpty()) {
            return true;
        }
        Set<String> want = new HashSet<>(expected);
        List<String> filtered = new ArrayList<>();
        for (String id : currentOrder) {
            if (want.contains(id)) {
                filtered.add(id);
            }
        }
        return filtered.equals(expected);
    }

    private static final int JEI_BOOKMARK_SCAN_LIMIT = 2000;

    static String jeiConfigHash(Path gameDir) {
        if (gameDir == null) {
            return "absent";
        }
        List<String> segments = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(gameDir)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return "bookmarks.json".equals(name) || "bookmarks.ini".equals(name);
                    })
                    .sorted()
                    .limit(JEI_BOOKMARK_SCAN_LIMIT)
                    .toList();
            for (Path file : files) {
                String relPath = gameDir.relativize(file).toString().replace('\\', '/');
                long size = Files.size(file);
                String fileSha = sha1Hex(Files.readAllBytes(file));
                segments.add(relPath + ":" + size + ":" + fileSha);
            }
        } catch (IOException e) {
            PackAiMod.LOGGER.debug("[packai-autotest] jei bookmark hash skip {}", gameDir, e);
        }
        if (segments.isEmpty()) {
            return "absent";
        }
        segments.sort(Comparator.naturalOrder());
        return digestSegments(segments);
    }

    private static String digestSegments(List<String> segments) {
        if (segments.isEmpty()) {
            return "absent";
        }
        String joined = String.join("\n", segments);
        String full = sha1Hex(joined.getBytes(StandardCharsets.UTF_8));
        return full.length() <= 16 ? full : full.substring(0, 16);
    }

    private static String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            return "0000000000000000";
        }
    }

    static void writeArtifact(Minecraft mc, List<JsonObject> rows) {
        Path dir = gameDir(mc);
        if (dir == null) {
            return;
        }
        Path autotest = dir.resolve("packai").resolve("autotest");
        Path dest = autotest.resolve("route-marks.json");
        JsonArray arr = new JsonArray();
        for (JsonObject row : rows) {
            arr.add(row);
        }
        String body = GSON.toJson(arr);
        try {
            Files.createDirectories(autotest);
            Path tmp = autotest.resolve("route-marks.json.tmp");
            Files.writeString(tmp, body, StandardCharsets.UTF_8);
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException t) {
            PackAiMod.LOGGER.warn("[packai-autotest] route-marks.json write 失敗", t);
        }
    }

    private static Path gameDir(Minecraft mc) {
        if (mc == null || mc.gameDirectory == null) {
            return null;
        }
        return mc.gameDirectory.toPath();
    }

    private static String errorText(Throwable t) {
        String name = t.getClass().getSimpleName();
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return name;
        }
        return name + ": " + msg;
    }
}
