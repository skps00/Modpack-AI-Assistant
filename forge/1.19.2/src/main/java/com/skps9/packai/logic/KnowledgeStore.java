package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * File layer for pack-author knowledge and GitHub cache copies.
 * Lazy: first lookup loads; not on the game-start thread.
 */
public final class KnowledgeStore {
    public static final int DEFAULT_CACHE_MAX_MB = 32;
    public static final int MAX_FILE_BYTES = 4_000_000;

    private static final Object LOCK = new Object();
    private static Path loadedDir;
    private static Map<String, KnowledgeEntry> index;

    private KnowledgeStore() {}

    public static Path knowledgeDir(Path gameDir) {
        return gameDir.resolve("config").resolve("packai").resolve("knowledge");
    }

    public static Path cacheDir(Path gameDir) {
        return gameDir.resolve("config").resolve("packai").resolve("knowledge-cache");
    }

    public static void resetForTest() {
        synchronized (LOCK) {
            loadedDir = null;
            index = null;
        }
    }

    public static KnowledgeEntry get(Path gameDir, String itemId) {
        if (gameDir == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        Map<String, KnowledgeEntry> map = ensureLoaded(gameDir);
        if (map == null) {
            return null;
        }
        return map.get(itemId.trim().toLowerCase(Locale.ROOT));
    }

    // ponytail: first-query snapshot; ceiling = miss files dropped after load; upgrade = mtime/watch
    static Map<String, KnowledgeEntry> ensureLoaded(Path gameDir) {
        synchronized (LOCK) {
            if (index != null && gameDir.equals(loadedDir)) {
                return index;
            }
            Map<String, KnowledgeEntry> map = new LinkedHashMap<>();
            Path cache = cacheDir(gameDir);
            evictCache(cache, liveCacheMaxMb());
            loadDir(cache, map);
            loadDir(knowledgeDir(gameDir), map);
            loadedDir = gameDir;
            index = map;
            return index;
        }
    }

    /** Cache-dir size cap. Oldest mtime deleted first. Call before any cache write. */
    public static void evictCache(Path dir, int maxMb) {
        long cap = Math.max(1L, (long) Math.max(0, maxMb) * 1024L * 1024L);
        evictCacheBytes(dir, cap);
    }

    static void evictCacheBytes(Path dir, long maxBytes) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        long cap = Math.max(1L, maxBytes);
        List<Path> files = listJson(dir);
        files.sort(Comparator
                .comparingLong(KnowledgeStore::mtime)
                .thenComparing(p -> p.getFileName().toString()));
        long total = 0;
        for (Path p : files) {
            total += size(p);
        }
        int i = 0;
        while (i < files.size() && total > cap) {
            Path p = files.get(i);
            long sz = size(p);
            try {
                Files.deleteIfExists(p);
                total -= sz;
            } catch (Exception e) {
                break;
            }
            i++;
        }
    }

    private static void loadDir(Path dir, Map<String, KnowledgeEntry> map) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        for (Path p : listJson(dir)) {
            String text;
            try {
                if (size(p) > MAX_FILE_BYTES) {
                    continue;
                }
                text = Files.readString(p, StandardCharsets.UTF_8);
            } catch (Exception e) {
                continue;
            }
            for (KnowledgeEntry e : KnowledgeEntry.parseAll(text)) {
                if (e != null && e.item != null && !e.item.isBlank()) {
                    map.put(e.item, e);
                }
            }
        }
    }

    private static List<Path> listJson(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    files.add(p);
                }
            }
        } catch (Exception e) {
            return files;
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return files;
    }

    private static int liveCacheMaxMb() {
        try {
            Class<?> c = Class.forName("com.skps9.packai.config.PackAiConfig");
            Object v = c.getMethod("knowledgeCacheMaxMb").invoke(null);
            if (v instanceof Integer i) {
                return i;
            }
        } catch (Throwable ignored) {
            // headless
        }
        return DEFAULT_CACHE_MAX_MB;
    }

    private static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return 0L;
        }
    }
}
