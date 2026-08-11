package com.skps9.packai.logic;

import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * OpenAI-compatible chat {@code usage} (prompt / completion / total).
 * Missing fields stay {@code -1}; {@link #NONE} = no usage object at all.
 */
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
    public static final TokenUsage NONE = new TokenUsage(-1, -1, -1);

    public boolean isPresent() {
        return promptTokens >= 0 || completionTokens >= 0 || totalTokens >= 0;
    }

    /** Parse {@code usage} from a chat/completions JSON root; missing → {@link #NONE}. */
    public static TokenUsage fromResponse(JsonObject root) {
        if (root == null || !root.has("usage") || !root.get("usage").isJsonObject()) {
            return NONE;
        }
        JsonObject u = root.getAsJsonObject("usage");
        return new TokenUsage(
                readNonNeg(u, "prompt_tokens"),
                readNonNeg(u, "completion_tokens"),
                readNonNeg(u, "total_tokens"));
    }

    private static int readNonNeg(JsonObject u, String key) {
        if (u == null || !u.has(key)) {
            return -1;
        }
        JsonElement el = u.get(key);
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            return -1;
        }
        int n = el.getAsInt();
        return n < 0 ? -1 : n;
    }

    /** Compact count for UI: {@code 400}, {@code 1.2k}, or {@code —} when unknown. */
    public static String formatCount(int n) {
        if (n < 0) {
            return "—";
        }
        if (n < 1000) {
            return Integer.toString(n);
        }
        double k = n / 1000.0;
        if (n < 10_000) {
            String s = String.format(Locale.ROOT, "%.1fk", k);
            return s.endsWith(".0k") ? s.substring(0, s.length() - 3) + "k" : s;
        }
        return String.format(Locale.ROOT, "%.0fk", k);
    }

    public String formatIn() {
        return formatCount(promptTokens);
    }

    public String formatOut() {
        return formatCount(completionTokens);
    }
}
