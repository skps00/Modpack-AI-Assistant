package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KB-2 remote fetch / ETag / LRU / privacy. Fake fetcher only — no real network.
 * Run with -ea (gradle / -ea NOT claimed run by agent when shell denied).
 */
public final class AskKnowledgeRemoteCheck {
    private AskKnowledgeRemoteCheck() {}

    static final String ENTRY = """
            {
              "item": "create:goggles",
              "use": [{"trigger": "wear", "effect": "overlay",
                       "source": "mod:tooltip", "tier": "B"}]
            }
            """;

    public static void main(String[] args) throws Exception {
        etag200Then304();
        remoteOffNoFetch();
        timeoutMalformedOversizedFallback();
        cacheLruEvict();
        urlPrivacy();
        testConnectionStatuses();
        remoteWriteNeverMissingBody();
        System.out.println("AskKnowledgeRemoteCheck OK");
    }

    /** 200+ETag → cache; second call If-None-Match + 304 → cache, no re-parse fail. */
    private static void etag200Then304() throws Exception {
        KnowledgeStore.resetForTest();
        KnowledgeRemote.resetForTest();
        Path game = Files.createTempDirectory("packai-kb2-etag");
        AtomicReference<String> lastIfNone = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        KnowledgeRemote.fetcherOverride = (url, ifNone) -> {
            calls.incrementAndGet();
            lastIfNone.set(ifNone);
            assert url.endsWith("/items/create__goggles.json") : url;
            assert !url.contains("?") : url;
            if (ifNone != null && ifNone.contains("etag-v1")) {
                return new KnowledgeRemote.FetchResult(304, new byte[0], "etag-v1");
            }
            return new KnowledgeRemote.FetchResult(
                    200, ENTRY.getBytes(StandardCharsets.UTF_8), "\"etag-v1\"");
        };

        KnowledgeRemote.Source s1 = KnowledgeRemote.ensureRemote(
                game, "create:goggles", true, KnowledgeRemote.DEFAULT_BASE);
        assert s1 == KnowledgeRemote.Source.REMOTE : s1;
        Path body = KnowledgeRemote.cacheBodyFile(game, "create:goggles");
        Path etag = KnowledgeRemote.cacheEtagFile(game, "create:goggles");
        assert Files.isRegularFile(body) : body;
        assert Files.isRegularFile(etag) : etag;
        assert Files.readString(etag, StandardCharsets.UTF_8).contains("etag-v1");

        List<String> facts = KnowledgeLookup.factsForItem(
                game, "create:goggles", 8, true, true, "1.19.2", "forge", "",
                KnowledgeRemote.DEFAULT_BASE);
        assert !facts.isEmpty() : facts;
        assert facts.get(0).contains("tier:B") : facts;

        KnowledgeRemote.Source s2 = KnowledgeRemote.ensureRemote(
                game, "create:goggles", true, KnowledgeRemote.DEFAULT_BASE);
        assert s2 == KnowledgeRemote.Source.CACHE : s2;
        assert lastIfNone.get() != null && lastIfNone.get().contains("etag-v1")
                : lastIfNone.get();
        assert calls.get() == 2 : calls.get();

        List<String> facts2 = KnowledgeLookup.factsForItem(
                game, "create:goggles", 8, true, true, "1.19.2", "forge", "",
                KnowledgeRemote.DEFAULT_BASE);
        assert !facts2.isEmpty() : facts2;
    }

    /** knowledgeRemote=false → zero fetch attempts (negative control). */
    private static void remoteOffNoFetch() throws Exception {
        KnowledgeStore.resetForTest();
        KnowledgeRemote.resetForTest();
        Path game = Files.createTempDirectory("packai-kb2-off");
        AtomicInteger calls = new AtomicInteger();
        KnowledgeRemote.fetcherOverride = (url, ifNone) -> {
            calls.incrementAndGet();
            throw new AssertionError("fetcher must not run when remote=false");
        };
        KnowledgeRemote.resetFetchAttempts();
        List<String> facts = KnowledgeLookup.factsForItem(
                game, "create:goggles", 8, true, false, "1.19.2", "forge", "",
                KnowledgeRemote.DEFAULT_BASE);
        assert facts.isEmpty() : facts;
        assert calls.get() == 0 : calls.get();
        assert KnowledgeRemote.fetchAttempts() == 0 : KnowledgeRemote.fetchAttempts();

        KnowledgeRemote.Source s = KnowledgeRemote.ensureRemote(
                game, "create:goggles", false, KnowledgeRemote.DEFAULT_BASE);
        assert s == KnowledgeRemote.Source.MISS : s;
        assert calls.get() == 0 : calls.get();
    }

    /** timeout / malformed / oversized → silent local fallback, no throw. */
    private static void timeoutMalformedOversizedFallback() throws Exception {
        KnowledgeStore.resetForTest();
        KnowledgeRemote.resetForTest();
        Path game = Files.createTempDirectory("packai-kb2-fail");
        Path auth = KnowledgeStore.knowledgeDir(game);
        Files.createDirectories(auth);
        Files.writeString(auth.resolve("local.json"),
                "{\"item\":\"mod:local\",\"use\":[{\"trigger\":\"t\",\"effect\":\"e\","
                        + "\"source\":\"author\",\"tier\":\"A\"}]}",
                StandardCharsets.UTF_8);

        // Timeout → still serve author local; remote miss silent.
        KnowledgeRemote.fetcherOverride = (url, ifNone) -> {
            throw new java.net.http.HttpTimeoutException("timeout");
        };
        KnowledgeRemote.Source st = KnowledgeRemote.ensureRemote(
                game, "mod:missing", true, KnowledgeRemote.DEFAULT_BASE);
        assert st == KnowledgeRemote.Source.MISS : st;
        List<String> local = KnowledgeLookup.factsForItem(
                game, "mod:local", 8, true, true, "1.19.2", "forge", "",
                KnowledgeRemote.DEFAULT_BASE);
        assert local.stream().anyMatch(f -> f.contains("source:author")) : local;

        // Malformed JSON → no cache write.
        KnowledgeRemote.fetcherOverride = (url, ifNone) ->
                new KnowledgeRemote.FetchResult(200, "{not-json".getBytes(StandardCharsets.UTF_8), "e");
        KnowledgeRemote.Source sm = KnowledgeRemote.ensureRemote(
                game, "mod:bad", true, KnowledgeRemote.DEFAULT_BASE);
        assert sm == KnowledgeRemote.Source.MISS : sm;
        assert !Files.exists(KnowledgeRemote.cacheBodyFile(game, "mod:bad"));

        // Oversized → no cache.
        byte[] big = new byte[KnowledgeRemote.MAX_BODY_BYTES + 8];
        for (int i = 0; i < big.length; i++) {
            big[i] = 'x';
        }
        KnowledgeRemote.fetcherOverride = (url, ifNone) ->
                new KnowledgeRemote.FetchResult(200, big, "e");
        KnowledgeRemote.Source so = KnowledgeRemote.ensureRemote(
                game, "mod:big", true, KnowledgeRemote.DEFAULT_BASE);
        assert so == KnowledgeRemote.Source.MISS : so;
        assert !Files.exists(KnowledgeRemote.cacheBodyFile(game, "mod:big"));
    }

    /** Over MB cap → oldest unused (mtime) removed. */
    private static void cacheLruEvict() throws Exception {
        KnowledgeStore.resetForTest();
        KnowledgeRemote.resetForTest();
        Path game = Files.createTempDirectory("packai-kb2-lru");
        Path cache = KnowledgeStore.cacheDir(game);
        Files.createDirectories(cache);
        Path old = cache.resolve("old__item.json");
        Path neu = cache.resolve("new__item.json");
        byte[] blob = ("{\"item\":\"old:item\",\"use\":[{\"trigger\":\"x\",\"effect\":\"y\","
                + "\"source\":\"s\",\"tier\":\"B\"}]}").getBytes(StandardCharsets.UTF_8);
        // pad to make size meaningful
        byte[] pad = new byte[200];
        for (int i = 0; i < pad.length; i++) {
            pad[i] = 'p';
        }
        Files.write(old, concat(blob, pad));
        Files.write(neu, concat(blob, pad));
        Files.setLastModifiedTime(old, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(neu, FileTime.fromMillis(9_000));
        long one = Files.size(neu);
        KnowledgeStore.evictCacheBytes(cache, one + 10);
        assert !Files.exists(old) : "oldest unused must evict";
        assert Files.exists(neu) : "newer kept";
    }

    /** URL contains only item stem — structural privacy (player_* stems OK). */
    private static void urlPrivacy() throws Exception {
        String url = KnowledgeRemote.itemUrl(
                KnowledgeRemote.DEFAULT_BASE, "create:goggles");
        assert url.equals(KnowledgeRemote.DEFAULT_BASE + "/items/create__goggles.json") : url;
        assert KnowledgeRemote.isSafeItemUrl(url) : url;
        assert !url.contains("?") : url;
        assert !url.contains("pack=") : url;
        String masked = KnowledgeRemote.maskUrl(url);
        assert masked.equals("…/items/create__goggles.json") : masked;
        assert !masked.contains("githubusercontent") : masked;

        // Legitimate *player* stems must pass (old contains("player") rejected these).
        for (String id : List.of(
                "minecraft:player_head",
                "minecraft:player_wall_head",
                "some_ns:player_things")) {
            String u = KnowledgeRemote.itemUrl(KnowledgeRemote.DEFAULT_BASE, id);
            assert KnowledgeRemote.isSafeItemUrl(u) : u;
            assert u.contains("__player_") || u.endsWith("player_things.json")
                    || u.contains("player_head") || u.contains("player_wall_head")
                    || u.contains("player_things") : u;
        }
        assert KnowledgeRemote.isSafeItemUrl(
                KnowledgeRemote.DEFAULT_BASE + "/items/minecraft__player_head.json");
        assert KnowledgeRemote.isSafeItemUrl(
                KnowledgeRemote.DEFAULT_BASE + "/items/minecraft__player_wall_head.json");
        assert KnowledgeRemote.isSafeItemUrl(
                KnowledgeRemote.DEFAULT_BASE + "/items/some_ns__player_things.json");

        // Reject query / pack / extra segments / uppercase.
        assert !KnowledgeRemote.isSafeItemUrl(
                KnowledgeRemote.DEFAULT_BASE + "/items/x.json?player=steve");
        assert !KnowledgeRemote.isSafeItemUrl(
                KnowledgeRemote.DEFAULT_BASE + "/items/a/b.json");
        assert !KnowledgeRemote.isSafeItemUrl(
                KnowledgeRemote.DEFAULT_BASE + "/items/PLAYER.json");
        assert !KnowledgeRemote.isSafeItemUrl(
                KnowledgeRemote.DEFAULT_BASE + "/items/Foo__Bar.json");

        KnowledgeStore.resetForTest();
        KnowledgeRemote.resetForTest();
        Path game = Files.createTempDirectory("packai-kb2-priv");
        List<String> seen = new ArrayList<>();
        KnowledgeRemote.fetcherOverride = (u, ifNone) -> {
            seen.add(u);
            assert KnowledgeRemote.isSafeItemUrl(u) : u;
            assert u.contains("/items/");
            assert !u.contains("?");
            String after = u.substring(u.indexOf("/items/") + "/items/".length());
            assert after.matches("[a-z0-9_.-]+__[a-z0-9_.-]+\\.json") : after;
            throw new java.net.UnknownHostException("dns");
        };
        KnowledgeRemote.ensureRemote(
                game, "foo:bar_baz", true, KnowledgeRemote.DEFAULT_BASE);
        assert !seen.isEmpty();
        // player_head must reach fetcher (not silent bad_url MISS).
        seen.clear();
        KnowledgeRemote.ensureRemote(
                game, "minecraft:player_head", true, KnowledgeRemote.DEFAULT_BASE);
        assert seen.stream().anyMatch(u -> u.contains("minecraft__player_head")) : seen;
    }

    /**
     * Full cache + new body: never return REMOTE when body file is missing.
     * Evict-before-write + post-write verify.
     */
    private static void remoteWriteNeverMissingBody() throws Exception {
        KnowledgeStore.resetForTest();
        KnowledgeRemote.resetForTest();
        Path game = Files.createTempDirectory("packai-kb2-evict");
        Path cache = KnowledgeStore.cacheDir(game);
        Files.createDirectories(cache);
        KnowledgeRemote.cacheMaxMbOverride = 1; // 1 MiB cap
        // Fill cache nearly full with old files so post-write eviction (old bug) would
        // drop a freshly written body.
        byte[] filler = new byte[200_000];
        for (int i = 0; i < filler.length; i++) {
            filler[i] = 'p';
        }
        String tinyEntry = "{\"item\":\"pad:x\",\"use\":[{\"trigger\":\"t\",\"effect\":\"e\","
                + "\"source\":\"s\",\"tier\":\"B\"}]}";
        byte[] blob = concat(tinyEntry.getBytes(StandardCharsets.UTF_8), filler);
        for (int i = 0; i < 6; i++) {
            Path p = cache.resolve("pad__old" + i + ".json");
            Files.write(p, blob);
            Files.setLastModifiedTime(p, FileTime.fromMillis(1_000L + i));
        }
        byte[] incoming = ENTRY.getBytes(StandardCharsets.UTF_8);
        KnowledgeRemote.fetcherOverride = (url, ifNone) ->
                new KnowledgeRemote.FetchResult(200, incoming, "etag-new");

        KnowledgeRemote.Source src = KnowledgeRemote.ensureRemote(
                game, "create:goggles", true, KnowledgeRemote.DEFAULT_BASE);
        Path body = KnowledgeRemote.cacheBodyFile(game, "create:goggles");
        if (src == KnowledgeRemote.Source.REMOTE) {
            assert Files.isRegularFile(body) : "REMOTE must have readable body";
            assert Files.size(body) > 0 : body;
            String text = Files.readString(body, StandardCharsets.UTF_8);
            assert text.contains("create:goggles") : text;
        } else {
            assert src == KnowledgeRemote.Source.MISS : "only REMOTE or MISS, got " + src;
        }
        // Negative: never REMOTE-with-missing-body
        assert !(src == KnowledgeRemote.Source.REMOTE && !Files.isRegularFile(body));
        KnowledgeRemote.resetForTest();
    }

    private static void testConnectionStatuses() {
        KnowledgeRemote.resetForTest();
        assert KnowledgeRemote.testConnection(false, true, KnowledgeRemote.DEFAULT_BASE)
                == KnowledgeRemote.TestStatus.DISABLED;
        assert KnowledgeRemote.testConnection(true, false, KnowledgeRemote.DEFAULT_BASE)
                == KnowledgeRemote.TestStatus.DISABLED;

        KnowledgeRemote.fetcherOverride = (url, ifNone) ->
                new KnowledgeRemote.FetchResult(404, new byte[0], "");
        assert KnowledgeRemote.testConnection(true, true, KnowledgeRemote.DEFAULT_BASE)
                == KnowledgeRemote.TestStatus.OK;

        KnowledgeRemote.fetcherOverride = (url, ifNone) -> {
            throw new java.net.UnknownHostException("x");
        };
        assert KnowledgeRemote.testConnection(true, true, KnowledgeRemote.DEFAULT_BASE)
                == KnowledgeRemote.TestStatus.NO_NETWORK;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
