package com.skps9.packai.logic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KB-2: one-item GitHub raw fetch with ETag cache. Offline-safe — never throws into ask.
 * URL scheme: {@code <knowledgeUrl>/items/<ns>__<path>.json} (item id only; no query).
 */
public final class KnowledgeRemote {
    public static final int CONNECT_TIMEOUT_SEC = 3;
    public static final int READ_TIMEOUT_SEC = 5;
    public static final int MAX_BODY_BYTES = 256 * 1024;
    public static final String DEFAULT_BASE =
            "https://raw.githubusercontent.com/skps00/packai-knowledge/main";

    /** Injected for headless tests — never hits the network when set. */
    static volatile Fetcher fetcherOverride;
    /** Test hook: override cache MB cap (null = live PackAiConfig / default). */
    static volatile Integer cacheMaxMbOverride;

    private static final ConcurrentHashMap<String, Object> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final AtomicInteger FETCH_ATTEMPTS = new AtomicInteger();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
            .build();

    private KnowledgeRemote() {}

    @FunctionalInterface
    public interface Fetcher {
        /** @param ifNoneMatch prior ETag or null/blank */
        FetchResult get(String url, String ifNoneMatch) throws Exception;
    }

    public record FetchResult(int status, byte[] body, String etag) {
        public FetchResult {
            body = body == null ? new byte[0] : body;
            etag = etag == null ? "" : etag;
        }
    }

    public enum Source {
        LOCAL, CACHE, REMOTE, MISS
    }

    public enum TestStatus {
        OK, NO_NETWORK, DISABLED
    }

    /** Test hook: how many times the fetcher was invoked (real or fake). */
    public static int fetchAttempts() {
        return FETCH_ATTEMPTS.get();
    }

    public static void resetFetchAttempts() {
        FETCH_ATTEMPTS.set(0);
    }

    public static void resetForTest() {
        fetcherOverride = null;
        cacheMaxMbOverride = null;
        IN_FLIGHT.clear();
        resetFetchAttempts();
    }

    /**
     * Build GET URL for one item. Path contains only the sanitized item stem — never
     * player / pack / machine data.
     */
    public static String itemUrl(String base, String itemId) {
        String root = normalizeBase(base);
        String stem = cacheStem(itemId);
        if (stem.isEmpty()) {
            return "";
        }
        return root + "/items/" + stem + ".json";
    }

    /** {@code create:goggles} → {@code create__goggles}; {@code a:b/c} → {@code a__b_c}. */
    public static String cacheStem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        int c = id.indexOf(':');
        String ns;
        String path;
        if (c <= 0) {
            ns = "minecraft";
            path = id;
        } else {
            ns = id.substring(0, c);
            path = id.substring(c + 1);
        }
        return sanitizeSegment(ns) + "__" + sanitizeSegment(path);
    }

    static String sanitizeSegment(String s) {
        if (s == null || s.isBlank()) {
            return "x";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '_' || ch == '-' || ch == '.') {
                sb.append(ch);
            } else {
                sb.append('_');
            }
        }
        return sb.length() == 0 ? "x" : sb.toString();
    }

    public static Path cacheBodyFile(Path gameDir, String itemId) {
        return KnowledgeStore.cacheDir(gameDir).resolve(cacheStem(itemId) + ".json");
    }

    public static Path cacheEtagFile(Path gameDir, String itemId) {
        return KnowledgeStore.cacheDir(gameDir).resolve(cacheStem(itemId) + ".etag");
    }

    /** Mask host/base — log only {@code …/items/<stem>.json}. */
    public static String maskUrl(String url) {
        if (url == null || url.isBlank()) {
            return "…";
        }
        int i = url.indexOf("/items/");
        if (i >= 0) {
            return "…/items/" + url.substring(i + "/items/".length());
        }
        return "…";
    }

    /**
     * Privacy predicate: accept only {@code <base>/items/<ns>__<stem>.json} where both halves
     * match {@code [a-z0-9_.-]+}. Reject query strings, {@code pack=}, extra path segments,
     * or uppercase (player / pack names cannot appear in this shape).
     */
    public static boolean isSafeItemUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (url.indexOf('?') >= 0 || url.contains("pack=")) {
            return false;
        }
        int i = url.indexOf("/items/");
        if (i < 0) {
            return false;
        }
        String after = url.substring(i + "/items/".length());
        if (after.isEmpty() || after.indexOf('/') >= 0 || after.indexOf('\\') >= 0) {
            return false;
        }
        return after.matches("[a-z0-9_.-]+__[a-z0-9_.-]+\\.json");
    }

    /**
     * If local author miss and remote on: conditional GET → write cache body+etag.
     * Never throws. At most one in-flight fetch per item id.
     *
     * @return source of the entry after attempt (CACHE if 304 / prior file; REMOTE if 200)
     */
    public static Source ensureRemote(Path gameDir, String itemId, boolean remoteEnabled, String baseUrl) {
        if (gameDir == null || itemId == null || itemId.isBlank() || !remoteEnabled) {
            return Source.MISS;
        }
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        String stem = cacheStem(id);
        if (stem.isEmpty()) {
            return Source.MISS;
        }
        Object lock = IN_FLIGHT.computeIfAbsent(stem, k -> new Object());
        synchronized (lock) {
            try {
                return doEnsure(gameDir, id, stem, baseUrl);
            } catch (Throwable t) {
                logMiss("error", itemUrl(baseUrl, id));
                return Source.MISS;
            } finally {
                IN_FLIGHT.remove(stem, lock);
            }
        }
    }

    private static Source doEnsure(Path gameDir, String id, String stem, String baseUrl) {
        Path bodyFile = cacheBodyFile(gameDir, id);
        Path etagFile = cacheEtagFile(gameDir, id);
        String url = itemUrl(baseUrl, id);
        if (url.isEmpty()) {
            return Source.MISS;
        }
        // Privacy: structural path only — never substring-"player" (blocks player_head).
        if (!isSafeItemUrl(url)) {
            logMiss("bad_url", url);
            return Source.MISS;
        }
        String priorEtag = readEtag(etagFile);
        FETCH_ATTEMPTS.incrementAndGet();
        FetchResult res;
        try {
            res = activeFetcher().get(url, priorEtag.isBlank() ? null : priorEtag);
        } catch (Throwable t) {
            logMiss(reasonOf(t), url);
            return Files.isRegularFile(bodyFile) ? touchAndCache(bodyFile) : Source.MISS;
        }
        int status = res.status();
        if (status == 304) {
            if (Files.isRegularFile(bodyFile)) {
                return touchAndCache(bodyFile);
            }
            logMiss("304_nocache", url);
            return Source.MISS;
        }
        if (status != 200) {
            logMiss("http_" + status, url);
            return Files.isRegularFile(bodyFile) ? touchAndCache(bodyFile) : Source.MISS;
        }
        byte[] body = res.body();
        if (body.length == 0 || body.length > MAX_BODY_BYTES) {
            logMiss(body.length == 0 ? "empty" : "oversized", url);
            return Source.MISS;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        List<KnowledgeEntry> parsed = KnowledgeEntry.parseAll(text);
        if (parsed.isEmpty()) {
            logMiss("malformed", url);
            return Source.MISS;
        }
        try {
            Path cacheDir = KnowledgeStore.cacheDir(gameDir);
            Files.createDirectories(cacheDir);
            // Evict BEFORE write, reserving room for the incoming body so we never
            // delete the file we are about to report as REMOTE.
            long cap = Math.max(1L, (long) liveCacheMaxMb() * 1024L * 1024L);
            long room = Math.max(1L, cap - (long) body.length);
            KnowledgeStore.evictCacheBytes(cacheDir, room);
            Files.write(bodyFile, body);
            String etag = res.etag();
            if (etag != null && !etag.isBlank()) {
                Files.writeString(etagFile, etag.trim(), StandardCharsets.UTF_8);
            }
            // Verify body survived and is readable — else MISS + drop orphan etag.
            if (!Files.isRegularFile(bodyFile)) {
                Files.deleteIfExists(etagFile);
                logMiss("evicted", url);
                return Source.MISS;
            }
            byte[] check = Files.readAllBytes(bodyFile);
            if (check.length == 0) {
                Files.deleteIfExists(bodyFile);
                Files.deleteIfExists(etagFile);
                logMiss("empty_after_write", url);
                return Source.MISS;
            }
            KnowledgeStore.invalidate();
            logHit(Source.REMOTE, id);
            return Source.REMOTE;
        } catch (Throwable t) {
            try {
                Files.deleteIfExists(etagFile);
            } catch (Throwable ignored) {
                // best-effort
            }
            logMiss("write", url);
            return Source.MISS;
        }
    }

    private static Source touchAndCache(Path bodyFile) {
        try {
            Files.setLastModifiedTime(bodyFile, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (Exception ignored) {
            // best-effort LRU touch
        }
        KnowledgeStore.invalidate();
        logHit(Source.CACHE, bodyFile.getFileName().toString());
        return Source.CACHE;
    }

    /**
     * Settings "test connection": off-thread. Disabled / OK / no network.
     * 2xx–4xx counts as reachable (404 = network OK).
     */
    public static TestStatus testConnection(boolean enabled, boolean remote, String baseUrl) {
        if (!enabled || !remote) {
            return TestStatus.DISABLED;
        }
        String base = normalizeBase(baseUrl);
        // Probe path uses only a fixed item stem — no player data.
        String url = base + "/items/minecraft__stone.json";
        FETCH_ATTEMPTS.incrementAndGet();
        try {
            FetchResult res = activeFetcher().get(url, null);
            int s = res.status();
            if (s >= 200 && s < 500) {
                return TestStatus.OK;
            }
            return TestStatus.NO_NETWORK;
        } catch (Throwable t) {
            return TestStatus.NO_NETWORK;
        }
    }

    static Fetcher defaultFetcher() {
        return (url, ifNoneMatch) -> {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(READ_TIMEOUT_SEC))
                    .GET()
                    .header("User-Agent", "PackAI-Knowledge/0.1 (offline-safe; item-id only)");
            if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
                b.header("If-None-Match", ifNoneMatch.trim());
            }
            HttpResponse<byte[]> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body() == null ? new byte[0] : resp.body();
            if (body.length > MAX_BODY_BYTES) {
                // Treat as oversized without caching.
                return new FetchResult(200, new byte[MAX_BODY_BYTES + 1], "");
            }
            String etag = resp.headers().firstValue("ETag").orElse("");
            return new FetchResult(resp.statusCode(), body, etag);
        };
    }

    private static Fetcher activeFetcher() {
        Fetcher o = fetcherOverride;
        return o != null ? o : defaultFetcher();
    }

    private static String readEtag(Path etagFile) {
        try {
            if (etagFile != null && Files.isRegularFile(etagFile)) {
                return Files.readString(etagFile, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {
            // miss
        }
        return "";
    }

    static String normalizeBase(String base) {
        String b = base == null || base.isBlank() ? DEFAULT_BASE : base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b.isEmpty() ? DEFAULT_BASE : b;
    }

    private static int liveCacheMaxMb() {
        Integer o = cacheMaxMbOverride;
        if (o != null && o > 0) {
            return o;
        }
        try {
            Class<?> c = Class.forName("com.skps9.packai.config.PackAiConfig");
            Object v = c.getMethod("knowledgeCacheMaxMb").invoke(null);
            if (v instanceof Integer i) {
                return i;
            }
        } catch (Throwable ignored) {
            // headless
        }
        return KnowledgeStore.DEFAULT_CACHE_MAX_MB;
    }

    private static String reasonOf(Throwable t) {
        if (t == null) {
            return "error";
        }
        String n = t.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (n.contains("timeout") || n.contains("timed")) {
            return "timeout";
        }
        if (n.contains("unknownhost") || n.contains("connect")) {
            return "dns";
        }
        return "error";
    }

    private static void logMiss(String reason, String url) {
        try {
            Class<?> mod = Class.forName("com.skps9.packai.PackAiMod");
            Object logger = mod.getField("LOGGER").get(null);
            logger.getClass().getMethod("info", String.class, Object.class, Object.class)
                    .invoke(logger, "Pack AI knowledge remote miss={} url={}", reason, maskUrl(url));
        } catch (Throwable ignored) {
            // headless / no logger
        }
    }

    private static void logHit(Source source, String idOrName) {
        try {
            Class<?> mod = Class.forName("com.skps9.packai.PackAiMod");
            Object logger = mod.getField("LOGGER").get(null);
            logger.getClass().getMethod("info", String.class, Object.class, Object.class)
                    .invoke(logger, "Pack AI knowledge remote status=ok source={} id={}",
                            source.name().toLowerCase(Locale.ROOT), idOrName);
        } catch (Throwable ignored) {
            // headless
        }
    }
}
