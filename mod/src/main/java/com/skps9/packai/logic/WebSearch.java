package com.skps9.packai.logic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;

/**
 * Optional Minecraft-mod web search (Tavily / Serper). No new deps — HttpClient + Gson only.
 * Results unrelated to mods are dropped.
 */
public final class WebSearch {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final int MAX_RESULTS = 4;
    private static final int SNIPPET_CAP = 280;

    /** Host / URL fragments that look like Minecraft mod documentation. */
    private static final String[] MOD_HOST_HINTS = {
            "curseforge.com", "modrinth.com", "minecraft.wiki", "minecraft.fandom.com",
            "ftb.team", "feed-the-beast", "wiki.gg", "github.com", "gitlab.com",
            "blamejared.com", "kubejs.com", "jez.gg", "neoforged.net", "minecraftforge.net",
            "fabricmc.net", "docs.minecraftforge", "wiki.fabricmc"
    };

    private WebSearch() {}

    public record Hit(String title, String url, String snippet) {}

    /**
     * @return mod-related hits, or empty if disabled / no key / failure
     */
    public static List<Hit> search(String question, List<String> focusMods, ItemRef heldItem) {
        if (!PackAiConfig.webSearchEnabled()) {
            return List.of();
        }
        String query = buildQuery(question, focusMods, heldItem);
        String tavily = resolveTavilyKey();
        String serper = resolveSerperKey();
        try {
            List<Hit> raw;
            if (!tavily.isEmpty()) {
                raw = tavily(query, tavily);
            } else if (!serper.isEmpty()) {
                raw = serper(query, serper);
            } else {
                return List.of();
            }
            return filterModOnly(raw);
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("Web search skipped: {}", e.toString());
            return List.of();
        }
    }

    public static String formatForLlm(List<Hit> hits) {
        return formatForLlm(hits, false);
    }

    public static String formatForLlm(List<Hit> hits, boolean partialPack) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (partialPack) {
            sb.append("【網搜｜僅 Minecraft mod；整合包其他部分可能已魔改，此題目未見本地覆寫，僅供參考】\n");
        } else {
            sb.append("【網搜｜僅 Minecraft mod 相關，整合包魔改時可能不準】\n");
        }
        int i = 1;
        for (Hit h : hits) {
            sb.append(i++).append(". ").append(plain(h.title()));
            if (h.snippet() != null && !h.snippet().isBlank()) {
                sb.append(" — ").append(plain(h.snippet()));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    static String buildQuery(String question, List<String> focusMods, ItemRef heldItem) {
        StringBuilder sb = new StringBuilder();
        if (question != null && !question.isBlank()) {
            sb.append(question.trim());
        }
        if (heldItem != null && heldItem.isPresent()) {
            sb.append(' ').append(heldItem.id());
        }
        if (focusMods != null) {
            int n = 0;
            for (String mid : focusMods) {
                if (mid == null || mid.isBlank() || n >= 3) {
                    break;
                }
                sb.append(' ').append(mid.trim());
                n++;
            }
        }
        sb.append(" Minecraft mod");
        return sb.toString().trim();
    }

    static List<Hit> filterModOnly(List<Hit> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Hit> out = new ArrayList<>();
        for (Hit h : raw) {
            if (h == null) {
                continue;
            }
            if (isModRelated(h.url(), h.title(), h.snippet())) {
                out.add(h);
            }
            if (out.size() >= MAX_RESULTS) {
                break;
            }
        }
        return out;
    }

    static boolean isModRelated(String url, String title, String snippet) {
        String blob = ((url == null ? "" : url) + " " + (title == null ? "" : title)
                + " " + (snippet == null ? "" : snippet)).toLowerCase(Locale.ROOT);
        if (blob.contains("minecraft") || blob.contains("modpack") || blob.contains("neoforge")
                || blob.contains("forge") || blob.contains("fabric") || blob.contains("quilt")) {
            return true;
        }
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        for (String hint : MOD_HOST_HINTS) {
            if (u.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static List<Hit> tavily(String query, String apiKey) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("api_key", apiKey);
        body.addProperty("query", query);
        body.addProperty("search_depth", "basic");
        body.addProperty("max_results", MAX_RESULTS);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tavily.com/search"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 400) {
            PackAiMod.LOGGER.debug("Tavily HTTP {}", res.statusCode());
            return List.of();
        }
        JsonObject data = GSON.fromJson(res.body(), JsonObject.class);
        List<Hit> out = new ArrayList<>();
        JsonArray results = data.getAsJsonArray("results");
        if (results == null) {
            return out;
        }
        for (JsonElement el : results) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            out.add(new Hit(
                    text(o, "title"),
                    text(o, "url"),
                    clip(firstNonBlank(text(o, "content"), text(o, "snippet")))
            ));
            if (out.size() >= MAX_RESULTS) {
                break;
            }
        }
        return out;
    }

    private static List<Hit> serper(String query, String apiKey) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("q", query);
        body.addProperty("num", MAX_RESULTS);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://google.serper.dev/search"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-API-KEY", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 400) {
            PackAiMod.LOGGER.debug("Serper HTTP {}", res.statusCode());
            return List.of();
        }
        JsonObject data = GSON.fromJson(res.body(), JsonObject.class);
        List<Hit> out = new ArrayList<>();
        JsonArray organic = data.getAsJsonArray("organic");
        if (organic == null) {
            return out;
        }
        for (JsonElement el : organic) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            out.add(new Hit(
                    text(o, "title"),
                    text(o, "link"),
                    clip(text(o, "snippet"))
            ));
            if (out.size() >= MAX_RESULTS) {
                break;
            }
        }
        return out;
    }

    public static String resolveTavilyKey() {
        String k = env("PACKAI_TAVILY_API_KEY");
        if (!k.isEmpty()) {
            return k;
        }
        k = env("TAVILY_API_KEY");
        if (!k.isEmpty()) {
            return k;
        }
        String cfg = PackAiConfig.TAVILY_API_KEY.get();
        return cfg == null ? "" : LlmClient.sanitizeApiKey(cfg);
    }

    public static String resolveSerperKey() {
        String k = env("PACKAI_SERPER_API_KEY");
        if (!k.isEmpty()) {
            return k;
        }
        k = env("SERPER_API_KEY");
        if (!k.isEmpty()) {
            return k;
        }
        String cfg = PackAiConfig.SERPER_API_KEY.get();
        return cfg == null ? "" : LlmClient.sanitizeApiKey(cfg);
    }

    private static String env(String name) {
        return LlmClient.sanitizeApiKey(System.getenv(name));
    }

    private static String text(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > SNIPPET_CAP ? t.substring(0, SNIPPET_CAP) + "…" : t;
    }

    private static String plain(String s) {
        return Plainify.forMinecraftUi(s == null ? "" : s);
    }
}
