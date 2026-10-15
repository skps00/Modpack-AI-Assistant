package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.config.PackAiConfig;

/**
 * Headless-safe clarify arg parse + pending record (no MC types).
 * {@link ClarifyAskTool#run} maps {@link #TOOL_OK}/{@link #TOOL_LIMIT}/{@link #TOOL_MISS}
 * onto player-facing tool replies.
 */
public final class ClarifyArgs {
    public static final String TOOL_OK = "ok";
    public static final String TOOL_LIMIT = "limit";
    public static final String TOOL_MISS = "miss";

    public static final int MAX_CANDIDATES = 4;
    public static final int RAW_ARGS_MAX_CHARS = 2048;

    private ClarifyArgs() {}

    /** Queued clarify payload on {@link AskLoopState}. */
    public record PendingClarify(String guess, List<String> candidates, String why) {
        public PendingClarify {
            guess = guess == null ? "" : guess.trim();
            candidates = candidates == null || candidates.isEmpty()
                    ? List.of()
                    : List.copyOf(candidates);
            why = why == null ? "" : why.trim();
        }

        public boolean hasMaterial() {
            return !guess.isBlank() || !candidates.isEmpty();
        }
    }

    /**
     * Parse raw native-tool args JSON into {@code loop.pendingClarify}.
     * Does not increment the session counter (AskEngine exits own that).
     *
     * @return {@link #TOOL_OK}, {@link #TOOL_LIMIT}, or {@link #TOOL_MISS}
     */
    public static String record(AskLoopState loop, String rawArgsJson) {
        if (loop == null) {
            return TOOL_MISS;
        }
        if (!PackAiConfig.clarifyEnabled()) {
            return TOOL_MISS;
        }
        if (ChatSession.clarifyAsked()) {
            loop.setClarifyRefused(true);
            return TOOL_LIMIT;
        }
        PendingClarify pending = parse(rawArgsJson);
        if (pending == null || !pending.hasMaterial()) {
            return TOOL_MISS;
        }
        loop.setPendingClarify(pending);
        loop.setClarifyRefused(false);
        return TOOL_OK;
    }

    /** Parse guess / candidates / why. Null or empty material → null. */
    public static PendingClarify parse(String rawArgsJson) {
        if (rawArgsJson == null || rawArgsJson.isBlank()) {
            return null;
        }
        if (rawArgsJson.length() > RAW_ARGS_MAX_CHARS) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(rawArgsJson);
            if (el == null || !el.isJsonObject()) {
                return null;
            }
            JsonObject o = el.getAsJsonObject();
            String guess = stringField(o, "guess");
            String why = stringField(o, "why");
            List<String> candidates = stringListField(o, "candidates", MAX_CANDIDATES);
            PendingClarify p = new PendingClarify(guess, candidates, why);
            return p.hasMaterial() ? p : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Keep raw args JSON for fingerprint / tool run when the tool is in
     * {@link AskToolLoop#RAW_ARGS_TOOLS}. Invalid object or oversized → {@code ""}.
     */
    public static String preserveRawArgsJson(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return "";
        }
        String trimmed = argsJson.trim();
        if (trimmed.length() > RAW_ARGS_MAX_CHARS) {
            return "";
        }
        try {
            JsonElement el = JsonParser.parseString(trimmed);
            if (el == null || !el.isJsonObject()) {
                return "";
            }
            return trimmed;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String stringField(JsonObject o, String key) {
        if (o == null || key == null || !o.has(key)) {
            return "";
        }
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return "";
        }
        try {
            return e.getAsString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static List<String> stringListField(JsonObject o, String key, int max) {
        if (o == null || key == null || !o.has(key) || max <= 0) {
            return List.of();
        }
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            return List.of();
        }
        JsonArray arr = e.getAsJsonArray();
        ArrayList<String> out = new ArrayList<>(Math.min(arr.size(), max));
        for (JsonElement item : arr) {
            if (out.size() >= max) {
                break;
            }
            if (item == null || item.isJsonNull() || !item.isJsonPrimitive()) {
                continue;
            }
            try {
                String s = item.getAsString().trim();
                if (!s.isBlank()) {
                    out.add(s);
                }
            } catch (Exception ignored) {
                // skip bad element
            }
        }
        return out;
    }

    /** Namespace from {@code ns:path}, else blank. */
    public static String modOf(String idOrLabel) {
        if (idOrLabel == null || idOrLabel.isBlank()) {
            return "";
        }
        String s = idOrLabel.trim();
        int colon = s.indexOf(':');
        if (colon <= 0) {
            return "";
        }
        return s.substring(0, colon).toLowerCase(Locale.ROOT);
    }
}
