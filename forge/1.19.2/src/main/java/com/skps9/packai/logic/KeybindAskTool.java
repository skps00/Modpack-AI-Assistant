package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.client.context.KeybindReader;

/**
 * Live keybind lookup: which key maps to which action, conflicts, unbound.
 *
 * <p>Generic-token coverage: tokens that match ≥{@link #GENERIC_TOKEN_COVERAGE_PCT}% of rows
 * (e.g. {@code key} hit 235/273 rawKey prefixes) are dropped so English fallbacks like
 * {@code "Which key opens the map?"} do not dump almost every bind.
 */
public final class KeybindAskTool implements AskTool {
    private static final int LIMIT = 10;
    /** Drop tokens hitting this % of rows (live: {@code key} → 235/273 ≈ 86%). */
    private static final int GENERIC_TOKEN_COVERAGE_PCT = 40;
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fff]+");
    private static final String DESC =
            "Live keybind lookup. Call this FIRST whenever the player asks which key does something, what a key is bound to, whether keys conflict, or which keys are unbound - even when the question also names an item, block or mod feature (for example a question like which key is bound to the jetpack thruster is a keybind question, NOT an item question). Do not keep retrying item search for such questions. query=action/feature keyword (prefer a distinctive word from the mod's own label; for Chinese questions pass the feature keyword). key=key name like M or SPACE. namespace=modid like jei. Returns ranked matches, conflicts and unbound.";
    private static final String EMPTY_QUERY_HINT =
            "未指定查詢（query 為空）：請用 query=<該模組標籤裡最獨特嘅關鍵詞> 或 key=按鍵名（例 M）或 namespace=modid（例 jei）再查一次。";

    @Override
    public String name() {
        return "keybind_lookup";
    }

    @Override
    public String description() {
        return DESC;
    }

    @Override
    public String llmDescription() {
        return DESC;
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"key\":{\"type\":\"string\"},\"namespace\":{\"type\":\"string\"}},\"required\":[\"query\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        try {
            List<KeybindReader.Row> rows = KeybindReader.snapshot();
            if (rows == null || rows.isEmpty()) {
                return toolMissNote("");
            }
            // Models often pass item/machine (other-tool habit); question = player text last.
            String query = jsonString(args, "query");
            if (query.isBlank()) {
                query = jsonString(args, "machine");
            }
            if (query.isBlank()) {
                query = jsonString(args, "item");
            }
            if (query.isBlank() && args != null && args.question != null) {
                query = args.question.trim();
            }
            String keyFilter = jsonString(args, "key");
            String nsFilter = jsonString(args, "namespace");
            if (query.isBlank() && keyFilter.isBlank() && nsFilter.isBlank()) {
                return emptyQueryGuidance(rows);
            }
            List<String> queryTok = List.of();
            if (!query.isBlank()) {
                // ponytail: CJK questions drop Latin tokens (e.g. "mod"⊂"Mode") — ceiling: code-switched "開 mod list" needs Latin-only query or key=
                queryTok = filterGenericTokens(scoreTokens(query), rows);
                if (queryTok.isEmpty()) {
                    return emptyQueryGuidance(rows);
                }
            }
            List<Scored> scored = new ArrayList<>();
            for (KeybindReader.Row row : rows) {
                if (row == null) {
                    continue;
                }
                if (!nsFilter.isBlank()
                        && (row.namespace() == null
                        || !row.namespace().equalsIgnoreCase(nsFilter))) {
                    continue;
                }
                int keyScore = 0;
                if (!keyFilter.isBlank()) {
                    keyScore = keyMatchScore(row, keyFilter);
                    if (keyScore <= 0) {
                        continue;
                    }
                }
                int queryScore = 0;
                if (!query.isBlank()) {
                    queryScore = scoreRow(row, queryTok);
                    if (queryScore <= 0) {
                        continue;
                    }
                }
                int sortScore = !query.isBlank() ? queryScore : keyScore;
                scored.add(new Scored(row, sortScore));
            }
            if (scored.isEmpty()) {
                return emptyWithStats(rows);
            }
            scored.sort(Comparator
                    .comparingInt((Scored s) -> s.score).reversed()
                    .thenComparing(s -> s.row.label() == null ? "" : s.row.label(), String::compareTo));
            List<KeybindReader.Row> hits = new ArrayList<>(scored.size());
            for (Scored s : scored) {
                hits.add(s.row);
            }
            return formatHits(hits);
        } catch (Throwable t) {
            return toolMissNote("");
        }
    }

    /** Whitespace tokens + CJK 2/3-grams from continuous CJK runs. */
    static List<String> tokens(String query) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return List.of();
        }
        for (String part : query.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        Matcher m = CJK_RUN.matcher(query);
        while (m.find()) {
            String run = m.group();
            for (int n = 2; n <= 3; n++) {
                for (int i = 0; i + n <= run.length(); i++) {
                    out.add(run.substring(i, i + n));
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** Tokens used for scoring; if query has CJK, only CJK-bearing tokens count. */
    static List<String> scoreTokens(String query) {
        List<String> all = tokens(query);
        if (query == null || !CJK_RUN.matcher(query).find()) {
            return all;
        }
        List<String> cjkOnly = new ArrayList<>();
        for (String t : all) {
            if (t != null && CJK_RUN.matcher(t).find()) {
                cjkOnly.add(t);
            }
        }
        return cjkOnly;
    }

    /**
     * Drop tokens (len≥2) that hit ≥{@link #GENERIC_TOKEN_COVERAGE_PCT}% of rows via
     * label/rawKey contains — same case folding as {@link #scoreRow}.
     * Length-1 tokens kept (exact-only in scoreRow).
     */
    static List<String> filterGenericTokens(List<String> tokenList, List<KeybindReader.Row> rows) {
        if (tokenList == null || tokenList.isEmpty()) {
            return List.of();
        }
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>(tokenList);
        }
        int n = rows.size();
        List<String> kept = new ArrayList<>();
        for (String token : tokenList) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            if (token.length() < 2) {
                kept.add(token);
                continue;
            }
            String tLower = token.toLowerCase(Locale.ROOT);
            int hitCount = 0;
            for (KeybindReader.Row row : rows) {
                if (row == null) {
                    continue;
                }
                String labelLower = (row.label() == null ? "" : row.label()).toLowerCase(Locale.ROOT);
                String rkLower = (row.rawKey() == null ? "" : row.rawKey()).toLowerCase(Locale.ROOT);
                if (labelLower.contains(tLower) || rkLower.contains(tLower)) {
                    hitCount++;
                }
            }
            if (hitCount * 100 >= n * GENERIC_TOKEN_COVERAGE_PCT) {
                continue;
            }
            kept.add(token);
        }
        return kept;
    }

    static int scoreRow(KeybindReader.Row row, List<String> tokenList) {
        if (row == null || tokenList == null || tokenList.isEmpty()) {
            return 0;
        }
        String label = row.label() == null ? "" : row.label();
        String labelLower = label.toLowerCase(Locale.ROOT);
        String kd = row.keyDisplay() == null ? "" : row.keyDisplay();
        String rk = row.rawKey() == null ? "" : row.rawKey();
        String rkLower = rk.toLowerCase(Locale.ROOT);
        String ns = row.namespace() == null ? "" : row.namespace();
        int score = 0;
        for (String token : tokenList) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            String tLower = token.toLowerCase(Locale.ROOT);
            if (kd.equalsIgnoreCase(token)) {
                score += 100;
            }
            if (rk.equalsIgnoreCase(token)) {
                score += 80;
            }
            if (token.length() >= 2 && labelLower.contains(tLower)) {
                score += 10 * token.length();
            }
            if (token.length() >= 2 && rkLower.contains(tLower)) {
                score += 5;
            }
            if (ns.equalsIgnoreCase(token)) {
                score += 20;
            }
        }
        return score;
    }

    /** Exact keyDisplay/rawKey outrank contains; 0 = no match. */
    static int keyMatchScore(KeybindReader.Row row, String keyFilter) {
        if (row == null || keyFilter == null || keyFilter.isBlank()) {
            return 0;
        }
        String kd = row.keyDisplay() == null ? "" : row.keyDisplay();
        String rk = row.rawKey() == null ? "" : row.rawKey();
        if (kd.equalsIgnoreCase(keyFilter) || rk.equalsIgnoreCase(keyFilter)) {
            return 100;
        }
        String kl = keyFilter.toLowerCase(Locale.ROOT);
        if (kd.toLowerCase(Locale.ROOT).contains(kl) || rk.toLowerCase(Locale.ROOT).contains(kl)) {
            return 10;
        }
        return 0;
    }

    private String emptyQueryGuidance(List<KeybindReader.Row> rows) {
        return toolMissNote("") + "\n" + EMPTY_QUERY_HINT + "\n" + statsLine(rows);
    }

    private String emptyWithStats(List<KeybindReader.Row> rows) {
        return toolMissNote("") + "\n" + statsLine(rows);
    }

    private static String statsLine(List<KeybindReader.Row> rows) {
        int n = rows.size();
        int unbound = 0;
        Set<String> conflictGroups = new HashSet<>();
        for (KeybindReader.Row r : rows) {
            if (r == null) {
                continue;
            }
            if (r.unbound()) {
                unbound++;
            }
            if (r.conflict()) {
                String kd = r.keyDisplay();
                if (kd != null && !kd.isBlank()) {
                    conflictGroups.add(kd);
                }
            }
        }
        return "已載入 " + n + " 個按鍵功能：" + conflictGroups.size()
                + " 組撞鍵、" + unbound + " 個未綁。可用 key=按鍵名（例 M）或 namespace=modid（例 jei）收窄。";
    }

    private static String formatHits(List<KeybindReader.Row> hits) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(LIMIT, hits.size());
        for (int i = 0; i < shown; i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(formatLine(hits.get(i)));
        }
        int extra = hits.size() - shown;
        if (extra > 0) {
            sb.append('\n').append("…另有 ").append(extra).append(" 項");
        }
        return sb.toString();
    }

    static String formatLine(KeybindReader.Row row) {
        String label = row.label() == null ? "" : row.label();
        String body;
        if (row.unbound()) {
            body = "- " + label + " → （未綁）";
        } else {
            String kd = row.keyDisplay() == null ? "" : row.keyDisplay();
            body = "- " + label + " → " + kd;
        }
        if (row.conflict()) {
            body = body + "  ⚠撞鍵";
        }
        return body;
    }

    private static String jsonString(AskToolArgs args, String key) {
        if (args == null || key == null) {
            return "";
        }
        String argsJson = args.argumentsJson;
        if (argsJson == null || argsJson.isBlank()) {
            return "";
        }
        try {
            JsonObject o = JsonParser.parseString(argsJson).getAsJsonObject();
            if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
                String v = o.get(key).getAsString();
                return v == null ? "" : v.trim();
            }
        } catch (Exception ignored) {
            // malformed — fall through
        }
        return "";
    }

    private static final class Scored {
        final KeybindReader.Row row;
        final int score;

        Scored(KeybindReader.Row row, int score) {
            this.row = row;
            this.score = score;
        }
    }
}
