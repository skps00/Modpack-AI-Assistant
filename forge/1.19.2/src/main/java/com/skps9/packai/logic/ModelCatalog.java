package com.skps9.packai.logic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.client.Minecraft;

/**
 * Fetches live model ids from cloud {@code /models} and Ollama {@code /api/tags}.
 * Falls back to a small built-in list when offline / unauthorized.
 */
public final class ModelCatalog {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_MODELS = 48;

    private static final List<String> CLOUD_FALLBACK = List.of(
            "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat",
            "gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"
    );
    private static final List<String> OLLAMA_FALLBACK = List.of(
            "llama3.2", "llama3.1", "qwen2.5", "mistral", "phi3"
    );

    private static volatile List<String> cloudCache = List.of();
    private static volatile List<String> ollamaCache = List.of();
    private static volatile long cloudFetchedAt;
    private static volatile long ollamaFetchedAt;
    private static final AtomicBoolean refreshing = new AtomicBoolean(false);

    private ModelCatalog() {}

    /** Options for the current UI backend (cloud vs ollama), including the configured model. */
    public static List<String> optionsForUi() {
        boolean ollama = PackAiConfig.uiUsesOllamaModel();
        List<String> base = ollama ? cachedOrFallback(true) : cachedOrFallback(false);
        return withCurrent(base, PackAiConfig.uiModel());
    }

    /** Drop caches (e.g. after API key / base URL change). */
    public static void invalidate() {
        cloudCache = List.of();
        ollamaCache = List.of();
        cloudFetchedAt = 0;
        ollamaFetchedAt = 0;
    }

    /**
     * Refresh in background; runs {@code onClientDone} on the game thread when finished
     * (or immediately if a refresh is already in flight / cache still fresh).
     */
    public static void refreshAsync(Runnable onClientDone) {
        refreshAsync(false, onClientDone);
    }

    /** Like {@link #refreshAsync(Runnable)} but ignores TTL when {@code force} is true. */
    public static void refreshAsync(boolean force, Runnable onClientDone) {
        boolean needCloud = force || (!PackAiConfig.uiUsesOllamaModel() && isStale(cloudFetchedAt, cloudCache));
        boolean needOllama = force || (PackAiConfig.uiUsesOllamaModel() && isStale(ollamaFetchedAt, ollamaCache));
        // In auto, refresh both so mode switch is ready
        if ("auto".equals(PackAiConfig.resolvedMode()) || force) {
            needCloud = force || isStale(cloudFetchedAt, cloudCache);
            needOllama = force || isStale(ollamaFetchedAt, ollamaCache);
        }
        if (!needCloud && !needOllama) {
            // Cache still fresh — do not invoke onClientDone (would rebuildWidgets→init→refresh loop).
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        final boolean fetchCloud = needCloud;
        final boolean fetchOllama = needOllama;
        CompletableFuture.runAsync(() -> {
            try {
                if (fetchCloud) {
                    refreshCloudBlocking();
                }
                if (fetchOllama) {
                    refreshOllamaBlocking();
                }
            } finally {
                refreshing.set(false);
            }
        }).whenComplete((v, err) -> {
            if (err != null) {
                PackAiMod.LOGGER.debug("Model catalog refresh failed: {}", err.toString());
            }
            if (onClientDone != null) {
                Minecraft.getInstance().execute(onClientDone);
            }
        });
    }

    private static boolean isStale(long fetchedAt, List<String> cache) {
        if (cache == null || cache.isEmpty()) {
            return true;
        }
        return System.currentTimeMillis() - fetchedAt > TTL_MS;
    }

    private static List<String> cachedOrFallback(boolean ollama) {
        List<String> cached = ollama ? ollamaCache : cloudCache;
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        return ollama ? OLLAMA_FALLBACK : CLOUD_FALLBACK;
    }

    private static List<String> withCurrent(List<String> base, String current) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (current != null && !current.isBlank()) {
            set.add(current.trim());
        }
        set.addAll(base);
        List<String> out = new ArrayList<>(set);
        if (out.size() > MAX_MODELS) {
            return out.subList(0, MAX_MODELS);
        }
        return out;
    }

    private static void refreshCloudBlocking() {
        String key = LlmClient.resolveApiKey();
        if (key.isEmpty()) {
            return;
        }
        String base = LlmClient.normalizeApiBaseUrl(PackAiConfig.API_BASE_URL.get());
        if (base.isEmpty()) {
            base = "https://api.openai.com/v1";
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/models"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + key)
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() >= 400) {
                PackAiMod.LOGGER.debug("Cloud /models HTTP {}", res.statusCode());
                return;
            }
            List<String> ids = parseOpenAiModels(res.body());
            if (!ids.isEmpty()) {
                cloudCache = List.copyOf(ids);
                cloudFetchedAt = System.currentTimeMillis();
                PackAiMod.LOGGER.info("Pack AI cloud model list updated: {} models", ids.size());
            }
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("Cloud /models failed: {}", e.toString());
        }
    }

    private static void refreshOllamaBlocking() {
        String base = LlmClient.normalizeApiBaseUrl(PackAiConfig.OLLAMA_BASE_URL.get());
        if (base.isEmpty()) {
            base = "http://127.0.0.1:11434/v1";
        }
        String tagsUrl = base.endsWith("/v1")
                ? base.substring(0, base.length() - 3) + "/api/tags"
                : base + "/api/tags";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tagsUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() >= 400) {
                PackAiMod.LOGGER.debug("Ollama /api/tags HTTP {}", res.statusCode());
                return;
            }
            List<String> ids = parseOllamaTags(res.body());
            if (!ids.isEmpty()) {
                ollamaCache = List.copyOf(ids);
                ollamaFetchedAt = System.currentTimeMillis();
                PackAiMod.LOGGER.info("Pack AI ollama model list updated: {} models", ids.size());
            }
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("Ollama /api/tags failed: {}", e.toString());
        }
    }

    static List<String> parseOpenAiModels(String body) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("data") || !root.get("data").isJsonArray()) {
                return List.of();
            }
            JsonArray data = root.getAsJsonArray("data");
            for (JsonElement el : data) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject m = el.getAsJsonObject();
                if (!m.has("id")) {
                    continue;
                }
                String id = m.get("id").getAsString();
                if (id != null && !id.isBlank() && looksLikeChatModel(id)) {
                    out.add(id.trim());
                }
                if (out.size() >= MAX_MODELS) {
                    break;
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return new ArrayList<>(out);
    }

    static List<String> parseOllamaTags(String body) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("models") || !root.get("models").isJsonArray()) {
                return List.of();
            }
            for (JsonElement el : root.getAsJsonArray("models")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject m = el.getAsJsonObject();
                String name = m.has("name") ? m.get("name").getAsString() : null;
                if (name == null || name.isBlank()) {
                    continue;
                }
                name = name.trim();
                // Prefer short name without :latest duplicate
                if (name.endsWith(":latest")) {
                    String shortName = name.substring(0, name.length() - ":latest".length());
                    out.add(shortName);
                } else {
                    out.add(name);
                }
                if (out.size() >= MAX_MODELS) {
                    break;
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return new ArrayList<>(out);
    }

    /** Drop obvious embedding / audio / moderation models from OpenAI-style catalogs. */
    private static boolean looksLikeChatModel(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("embed") || s.contains("whisper") || s.contains("tts")
                || s.contains("dall-e") || s.contains("moderation") || s.contains("transcribe")) {
            return false;
        }
        return true;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
