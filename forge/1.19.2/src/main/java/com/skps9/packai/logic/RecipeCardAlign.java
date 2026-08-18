package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pick recipe cards that match the assistant reply (station / output / extra labels).
 * Headless — no Minecraft types. Empty pick = do not attach a mismatched Crafting strip.
 */
public final class RecipeCardAlign {
    private static final Pattern CARD_INDEX = Pattern.compile(
            "\\[\\[\\s*recipe_card\\s*:\\s*(\\d+)\\s*\\]\\]",
            Pattern.CASE_INSENSITIVE);
    /** Chinese station / craft prose without ASCII arrows (制成／祭坛／可在X). */
    private static final Pattern SPECIFIC_PROSE = Pattern.compile(
            "制成|做成|製成|作出|"
                    + "祭坛|祭壇|组装|組裝|火炉|火爐|仪式|儀式|酿造台|釀造台|"
                    + "[与與].{1,32}制成|[与與].{1,32}製成|"
                    + "可在(?!任务|任務)|可于(?!任务|任務)|可於(?!任务|任務)");

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "how", "to", "do", "i", "you", "what", "is", "are",
            "recipe", "recipes", "craft", "crafting", "use", "uses", "used", "using",
            "as", "material", "ingredient", "output", "input", "item", "items",
            "with", "from", "for", "and", "or", "of", "in", "on", "this", "that",
            "role", "card", "jei",
            "怎么", "如何", "用途", "用来", "用來", "作为", "作為", "材料", "配方",
            "合成", "制作", "製作", "产物", "產物", "输入", "輸入", "输出", "輸出",
            "的", "与", "與", "和", "或", "用", "从", "從", "这个", "這個");

    private RecipeCardAlign() {}

    /**
     * Catalog / card fingerprint for scoring. {@code index} is the collected-list index
     * ({@code [[recipe_card:N]]}).
     */
    public record Fingerprint(
            int index,
            String category,
            List<String> outputs,
            List<String> stations,
            List<String> extras
    ) {
        public Fingerprint {
            category = category == null ? "" : category;
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            stations = stations == null ? List.of() : List.copyOf(stations);
            extras = extras == null ? List.of() : List.copyOf(extras);
        }
    }

    /**
     * Indices into {@code cards} that match the reply. Marker {@code [[recipe_card:N]]}
     * always included when in range. Score hits follow. Empty = no safe attach.
     */
    public static List<Integer> pickIndices(String reply, List<Fingerprint> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (int n : markerIndices(reply)) {
            if (indexIn(cards, n)) {
                out.add(n);
            }
        }
        String text = reply == null ? "" : reply;
        LinkedHashSet<Integer> strong = new LinkedHashSet<>();
        boolean anyMachine = false;
        for (Fingerprint fp : cards) {
            if (fp == null) {
                continue;
            }
            if (strongMatch(text, fp)) {
                strong.add(fp.index());
                if (!isGenericCraft(fp.category())) {
                    anyMachine = true;
                }
            }
        }
        if (anyMachine) {
            for (Fingerprint fp : cards) {
                if (fp != null && isGenericCraft(fp.category())) {
                    strong.remove(fp.index());
                }
            }
        }
        out.addAll(strong);
        if (!strong.isEmpty() || replyLooksSpecific(text)) {
            return List.copyOf(out);
        }
        for (Fingerprint fp : cards) {
            if (fp != null && score(text, fp) > 0) {
                out.add(fp.index());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Reply names a station / product (arrows or 制成／祭坛／可在). Mismatched
     * auto-cards should be omitted — do not dump first-N Crafting uses.
     */
    public static boolean replyLooksSpecific(String reply) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        if (reply.contains("→") || reply.contains("->") || reply.contains("⇒")) {
            return true;
        }
        return SPECIFIC_PROSE.matcher(reply).find();
    }

    /** Best catalog-line index for a show_recipe_card query (name / station / number). */
    public static int bestLineIndex(String query, List<String> catalogLines) {
        if (query != null) {
            String q = query.trim();
            if (q.matches("\\d+")) {
                try {
                    return Integer.parseInt(q);
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        if (catalogLines == null || catalogLines.isEmpty()) {
            return -1;
        }
        String q = query == null ? "" : query;
        int best = -1;
        int bestScore = 0;
        for (int i = 0; i < catalogLines.size(); i++) {
            String line = catalogLines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            int s = overlapScore(q, line);
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        return bestScore > 0 ? best : -1;
    }

    /** Output / station / extra id in reply. Generic "Crafting" category alone is not a hit. */
    static boolean strongMatch(String reply, Fingerprint fp) {
        if (fp == null) {
            return false;
        }
        if (containsLabel(reply, fp.outputs())
                || containsLabel(reply, fp.stations())
                || containsLabel(reply, fp.extras())) {
            return true;
        }
        return !isGenericCraft(fp.category()) && containsLabel(reply, List.of(fp.category()));
    }

    static boolean containsLabel(String reply, List<String> labels) {
        if (reply == null || labels == null) {
            return false;
        }
        String r = reply.toLowerCase(Locale.ROOT);
        for (String raw : labels) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String l = raw.toLowerCase(Locale.ROOT).trim();
            if (l.length() >= 2 && r.contains(l)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isGenericCraft(String category) {
        if (category == null || category.isBlank()) {
            return true;
        }
        String c = category.toLowerCase(Locale.ROOT).trim();
        return c.equals("crafting")
                || c.equals("合成")
                || c.equals("工作台")
                || c.equals("工作臺")
                || c.contains("crafting table")
                || c.contains("minecraft:crafting");
    }

    static int score(String reply, Fingerprint fp) {
        if (fp == null) {
            return 0;
        }
        int n = 0;
        n += overlapScore(reply, fp.category());
        for (String o : fp.outputs()) {
            n += overlapScore(reply, o);
        }
        for (String s : fp.stations()) {
            n += overlapScore(reply, s);
        }
        for (String e : fp.extras()) {
            n += overlapScore(reply, e);
        }
        return n;
    }

    static int overlapScore(String reply, String label) {
        if (reply == null || label == null || label.isBlank()) {
            return 0;
        }
        String r = reply.toLowerCase(Locale.ROOT);
        String l = label.toLowerCase(Locale.ROOT).trim();
        if (l.length() >= 2 && r.contains(l)) {
            return l.length() >= 4 ? 3 : 2;
        }
        int hits = 0;
        for (String tok : tokens(l)) {
            if (tok.length() >= 2 && r.contains(tok)) {
                hits++;
            }
        }
        return hits;
    }

    static List<Integer> markerIndices(String reply) {
        List<Integer> out = new ArrayList<>();
        if (reply == null || reply.isBlank()) {
            return out;
        }
        Matcher m = CARD_INDEX.matcher(reply);
        while (m.find()) {
            try {
                out.add(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out;
    }

    static Set<String> tokens(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String n = raw.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        for (String p : n.split("[^\\p{L}\\p{N}]+")) {
            if (p.isBlank() || STOP.contains(p) || p.length() < 2) {
                continue;
            }
            out.add(p);
        }
        return out;
    }

    private static boolean indexIn(List<Fingerprint> cards, int n) {
        for (Fingerprint fp : cards) {
            if (fp != null && fp.index() == n) {
                return true;
            }
        }
        return false;
    }
}
