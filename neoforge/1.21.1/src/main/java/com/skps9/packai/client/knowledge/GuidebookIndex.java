package com.skps9.packai.client.knowledge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.GuidebookEntry;
import com.skps9.packai.logic.GuidebookIndexCache;
import com.skps9.packai.logic.GuidebookPins;
import com.skps9.packai.logic.PatchouliEntryScan;
import com.skps9.packai.logic.RecipeJsonOutputs;
import com.skps9.packai.logic.ReplyLang;

/**
 * In-memory guidebook index with disk cache under {@code config/packai/guidebook-index/}.
 * Async build: client thread snapshots ResourceManager JSON; daemon parses (no Patchouli API).
 */
public final class GuidebookIndex {
    public static final GuidebookIndex INSTANCE = new GuidebookIndex();

    private final AtomicBoolean building = new AtomicBoolean(false);
    private volatile boolean ready;
    private volatile GuidebookIndexCache.Meta loadedMeta;
    private volatile Map<String, GuidebookEntry> byKey = Map.of();
    private volatile Map<String, List<String>> itemToKeys = Map.of();
    private volatile Map<String, List<String>> titleTokenToKeys = Map.of();
    private volatile Map<String, List<String>> categoryToKeys = Map.of();

    private GuidebookIndex() {}

    public boolean isReady() {
        return ready && !byKey.isEmpty();
    }

    public void invalidate() {
        ready = false;
        loadedMeta = null;
        byKey = Map.of();
        itemToKeys = Map.of();
        titleTokenToKeys = Map.of();
        categoryToKeys = Map.of();
    }

    public void ensureAsync() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) {
            return;
        }
        ensureAsync(mc.gameDirectory.toPath());
    }

    public void ensureAsync(Path gameDir) {
        if (gameDir == null) {
            return;
        }
        GuidebookIndexCache.Meta want = currentMeta();
        if (want.modFp().isEmpty()) {
            return;
        }
        if (ready && GuidebookIndexCache.metaMatches(loadedMeta, want) && !byKey.isEmpty()) {
            return;
        }
        if (!building.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                runEnsure(gameDir, want);
            } catch (Exception e) {
                PackAiMod.LOGGER.debug("GuidebookIndex ensure failed: {}", e.toString());
            } finally {
                building.set(false);
            }
        }, "packai-guidebook-index");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Wait for index on worker threads only. Never block the client render thread —
     * that deadlocks {@code mc.execute} snapshot (ensureReady regression).
     */
    public boolean awaitReady(long timeoutMs) {
        ensureAsync();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.isSameThread()) {
            return isReady();
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMs));
        while (!isReady() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return isReady();
    }

    /**
     * Lookup entries linked to {@code itemId}. Empty when not ready or miss.
     * Scope filtering is caller's job (WP3).
     */
    public List<GuidebookEntry> lookupByItem(String itemId) {
        if (!isReady() || itemId == null || itemId.isBlank()) {
            return List.of();
        }
        GuidebookIndexCache.Meta want = currentMeta();
        if (GuidebookIndexCache.shouldRebuild(loadedMeta, want)) {
            invalidate();
            ensureAsync();
            return List.of();
        }
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        List<String> keys = itemToKeys.get(id);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<GuidebookEntry> out = new ArrayList<>();
        for (String k : keys) {
            GuidebookEntry e = byKey.get(k);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    /** All indexed entries (for title search candidate pool). */
    public List<GuidebookEntry> allEntries() {
        if (!isReady()) {
            return List.of();
        }
        return List.copyOf(byKey.values());
    }

    public Map<String, GuidebookEntry> byKeyView() {
        return byKey;
    }

    public Map<String, List<String>> categoryMapView() {
        return categoryToKeys;
    }

    /**
     * Title+token search. When {@code applyScope} and itemNs non-empty, filter by guidebookScope.
     * When itemNs empty, scope ns filter is skipped (no-focus path).
     */
    public List<GuidebookEntry> searchByTitle(
            String query, int minScore, String scope, String itemNs, boolean applyScope
    ) {
        if (!isReady() || query == null || query.isBlank()) {
            return List.of();
        }
        GuidebookIndexCache.Meta want = currentMeta();
        if (GuidebookIndexCache.shouldRebuild(loadedMeta, want)) {
            invalidate();
            ensureAsync();
            return List.of();
        }
        List<GuidebookEntry> pool = new ArrayList<>(byKey.values());
        if (applyScope && itemNs != null && !itemNs.isBlank()) {
            pool = GuidebookPins.filterScope(pool, scope, itemNs);
        }
        return GuidebookPins.rankByTitle(pool, query, minScore);
    }

    private void runEnsure(Path gameDir, GuidebookIndexCache.Meta want) {
        Path file = GuidebookIndexCache.cacheFile(gameDir, want);
        GuidebookIndexCache.Document disk = GuidebookIndexCache.load(file);
        if (!GuidebookIndexCache.shouldRebuild(disk.meta(), want) && !disk.entries().isEmpty()) {
            applyDocument(disk);
            PackAiMod.LOGGER.info(
                    "GuidebookIndex loaded from disk entries={} key={}",
                    disk.entries().size(),
                    GuidebookIndexCache.cacheKey(want));
            return;
        }
        List<RawJson> snapshot = new ArrayList<>();
        List<RawJson> assets = snapshotResources();
        if (assets != null && !assets.isEmpty()) {
            snapshot.addAll(assets);
        }
        // Ars Nouveau etc. ship Patchouli under data/ — client RM often misses these.
        snapshot.addAll(snapshotJarDataBooks(gameDir));
        if (snapshot.isEmpty()) {
            PackAiMod.LOGGER.info("GuidebookIndex snapshot skipped — empty index");
            applyDocument(new GuidebookIndexCache.Document(want, List.of()));
            return;
        }
        List<GuidebookEntry> built = buildFromSnapshot(snapshot, want.lang(), gameDir);
        built = GuidebookIndexCache.enrichLinksIn(built);
        GuidebookIndexCache.Document doc = new GuidebookIndexCache.Document(want, built);
        applyDocument(doc);
        boolean saved = GuidebookIndexCache.save(file, doc);
        PackAiMod.LOGGER.info(
                "GuidebookIndex built entries={} saved={} reason=rebuild key={}",
                built.size(),
                saved,
                GuidebookIndexCache.cacheKey(want));
    }

    private void applyDocument(GuidebookIndexCache.Document doc) {
        List<GuidebookEntry> entries = GuidebookIndexCache.enrichLinksIn(doc.entries());
        Map<String, GuidebookEntry> map = new LinkedHashMap<>();
        for (GuidebookEntry e : entries) {
            if (e == null) {
                continue;
            }
            String key = e.stableKey();
            if (!key.isBlank()) {
                map.put(key, e);
            }
        }
        byKey = Map.copyOf(map);
        itemToKeys = Map.copyOf(GuidebookIndexCache.buildItemMap(entries));
        titleTokenToKeys = Map.copyOf(GuidebookIndexCache.buildTitleTokenMap(entries));
        categoryToKeys = Map.copyOf(GuidebookIndexCache.buildCategoryMap(entries));
        loadedMeta = doc.meta();
        ready = !byKey.isEmpty();
    }

    /** Client-thread ResourceManager snapshot (JSON strings only). */
    private static List<RawJson> snapshotResources() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }
        if (mc.isSameThread()) {
            return snapshotResourcesOnClient(mc);
        }
        AtomicReference<List<RawJson>> ref = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        mc.execute(() -> {
            try {
                ref.set(snapshotResourcesOnClient(mc));
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                PackAiMod.LOGGER.info("GuidebookIndex snapshot timed out");
                return List.of();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        List<RawJson> got = ref.get();
        return got == null ? List.of() : got;
    }

    private static List<RawJson> snapshotResourcesOnClient(Minecraft mc) {
        try {
            if (mc.getResourceManager() == null) {
                return List.of();
            }
            Map<ResourceLocation, Resource> found;
            try {
                found = mc.getResourceManager().listResources(
                        "patchouli_books",
                        loc -> {
                            String p = loc.getPath();
                            return p.contains("/entries/") && p.endsWith(".json");
                        });
            } catch (Throwable t) {
                return List.of();
            }
            if (found == null || found.isEmpty()) {
                return List.of();
            }
            List<RawJson> out = new ArrayList<>(found.size());
            for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
                ResourceLocation loc = e.getKey();
                try (var in = e.getValue().open();
                        var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[4096];
                    int n;
                    while ((n = reader.read(buf)) >= 0) {
                        sb.append(buf, 0, n);
                    }
                    String path = loc.getNamespace() + ":" + loc.getPath();
                    out.add(new RawJson(loc.getNamespace(), loc.getPath(), path, sb.toString()));
                } catch (Throwable ignored) {
                    // skip bad resource
                }
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    /**
     * Scan mods jars for datapack Patchouli entry JSON under data/ns/patchouli_books/.../entries/.
     * Needed when books ship only under datapack paths (Ars Nouveau worn_notebook).
     */
    private static List<RawJson> snapshotJarDataBooks(Path gameDir) {
        if (gameDir == null) {
            return List.of();
        }
        Path mods = gameDir.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return List.of();
        }
        List<RawJson> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path jar : ds) {
                try (ZipFile zf = new ZipFile(jar.toFile())) {
                    Enumeration<? extends ZipEntry> en = zf.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry entry = en.nextElement();
                        if (entry == null || entry.isDirectory()) {
                            continue;
                        }
                        String name = entry.getName().replace('\\', '/');
                        if (!name.startsWith("data/")
                                || !name.contains("/patchouli_books/")
                                || !name.contains("/entries/")
                                || !name.endsWith(".json")) {
                            continue;
                        }
                        int slash = name.indexOf('/', "data/".length());
                        if (slash < 0) {
                            continue;
                        }
                        String ns = name.substring("data/".length(), slash).toLowerCase(Locale.ROOT);
                        String pathAfterNs = name.substring(slash + 1);
                        try (var in = zf.getInputStream(entry);
                                var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                            StringBuilder sb = new StringBuilder();
                            char[] buf = new char[4096];
                            int n;
                            while ((n = reader.read(buf)) >= 0) {
                                sb.append(buf, 0, n);
                            }
                            out.add(new RawJson(ns, pathAfterNs, ns + ":" + pathAfterNs, sb.toString()));
                        } catch (Throwable ignored) {
                            // skip bad zip entry
                        }
                    }
                } catch (Throwable ignored) {
                    // soft-skip bad jar
                }
            }
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("GuidebookIndex jar data scan failed: {}", t.toString());
        }
        return out;
    }

    private static List<GuidebookEntry> buildFromSnapshot(
            List<RawJson> snapshot, String preferredLang, Path gameDir) {
        record Ranked(int langRank, GuidebookEntry entry, List<String> recipeIds) {}
        Map<String, Ranked> best = new LinkedHashMap<>();
        for (RawJson raw : snapshot) {
            if (raw == null || raw.json() == null || raw.json().isBlank()) {
                continue;
            }
            PatchouliEntryScan.PathInfo path =
                    PatchouliEntryScan.parseEntryPath(raw.namespace(), raw.path());
            int rank = GuidebookIndexCache.langRank(path.lang(), preferredLang);
            if (rank < 0) {
                continue;
            }
            JsonObject obj;
            try {
                obj = JsonParser.parseString(raw.json()).getAsJsonObject();
            } catch (Throwable t) {
                continue;
            }
            GuidebookEntry entry = PatchouliEntryScan.toEntry(obj, raw.namespace(), raw.fullPath());
            String dedupe = entry.bookNs() + "|" + entry.bookId() + "|" + entry.entryId();
            if (entry.bookId().isEmpty() && entry.entryId().isEmpty()) {
                continue;
            }
            List<String> recipeIds = PatchouliEntryScan.collectCraftingRecipeIds(obj);
            Ranked prev = best.get(dedupe);
            if (prev == null || rank < prev.langRank()) {
                best.put(dedupe, new Ranked(rank, entry, recipeIds));
            }
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (Ranked r : best.values()) {
            wanted.addAll(r.recipeIds());
        }
        Map<String, String> outputs = RecipeJsonOutputs.resolve(gameDir, wanted);
        List<GuidebookEntry> out = new ArrayList<>(best.size());
        for (Ranked r : best.values()) {
            GuidebookEntry e = r.entry();
            if (!outputs.isEmpty() && !r.recipeIds().isEmpty()) {
                e = e.withLinkedItems(
                        PatchouliEntryScan.mergeRecipeResults(e.linkedItems(), r.recipeIds(), outputs));
            }
            out.add(e);
            if (out.size() >= GuidebookIndexCache.MAX_ENTRIES) {
                break;
            }
        }
        out.sort(Comparator.comparing(GuidebookEntry::stableKey));
        return out;
    }

    private static GuidebookIndexCache.Meta currentMeta() {
        String mc = SharedConstants.getCurrentVersion().getName();
        String lang = ReplyLang.current();
        if (lang == null || lang.isBlank()) {
            lang = "en_us";
        }
        List<String> lines = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            lines.add(info.getModId() + "@" + info.getVersion());
        }
        return new GuidebookIndexCache.Meta(
                mc, "neoforge", lang, GuidebookIndexCache.fingerprintMods(lines));
    }

    private record RawJson(String namespace, String path, String fullPath, String json) {}
}
