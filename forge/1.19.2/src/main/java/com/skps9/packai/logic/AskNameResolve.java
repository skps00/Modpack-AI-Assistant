package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Empty-hand name core from a how-to / summon question, then substring-match
 * pack labels / quest titles. No alias table, no pack-specific ids.
 */
public final class AskNameResolve {
    public record Label(String id, String label) {}

    private static final List<String> STRIP = List.of(
            "怎样召唤", "怎么召唤", "如何召唤", "怎樣召喚", "怎麼召喚", "如何召喚",
            "how to summon", "how do i summon", "how do you summon",
            "怎样", "怎么", "如何", "怎樣", "怎麼",
            "召唤", "召喚", "summoning", "summoned", "summon");

    private AskNameResolve() {}

    /** Strip how-to / summon / sentence punct; leftover is the asked name. */
    public static String nameCore(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        // Keep ?／？ until after phrase strip — some display names are literally "???".
        String q = question.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[!！。.,，、\\s]+", "");
        boolean changed = true;
        while (changed && !q.isEmpty()) {
            changed = false;
            for (String raw : STRIP) {
                String n = raw.replace(" ", "");
                if (n.isEmpty() || q.length() < n.length()) {
                    continue;
                }
                if (q.startsWith(n)) {
                    q = q.substring(n.length());
                    changed = true;
                } else {
                    String trail = q.replaceAll("[?？]+$", "");
                    if (trail.endsWith(n)) {
                        String leftover = trail.substring(0, trail.length() - n.length());
                        String marks = q.substring(trail.length());
                        if (leftover.isEmpty() && isPunctuationName(marks)) {
                            q = marks;
                        } else {
                            q = leftover;
                        }
                        changed = true;
                    }
                }
            }
        }
        q = q.trim();
        if (isPunctuationName(q)) {
            return q;
        }
        return q.replaceAll("[?？]+", "").trim();
    }

    /** True for leftover like {@code ???} — not a lone trailing sentence {@code ?}. */
    public static boolean isPunctuationName(String core) {
        if (core == null || core.length() < 2) {
            return false;
        }
        for (int i = 0; i < core.length(); i++) {
            char c = core.charAt(i);
            if (Character.isLetterOrDigit(c) || Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                return false;
            }
        }
        return true;
    }

    public static boolean coreUseful(String core) {
        if (core == null || core.isBlank()) {
            return false;
        }
        if (isPunctuationName(core)) {
            return true;
        }
        return core.length() >= 4;
    }

    /** Best catalog id whose label equals / starts with / contains the name core. */
    public static String resolveId(String question, List<Label> catalog) {
        String core = nameCore(question);
        if (!coreUseful(core) || catalog == null || catalog.isEmpty()) {
            return "";
        }
        String bestId = "";
        int best = 99;
        for (Label row : catalog) {
            if (row == null || row.id() == null || row.id().isBlank()) {
                continue;
            }
            int s = matchScore(core, row.label());
            if (s < best) {
                best = s;
                bestId = row.id();
            }
        }
        return best >= 99 ? "" : bestId;
    }

    static int matchScore(String core, String label) {
        if (core == null || core.isEmpty() || label == null || label.isBlank()) {
            return 99;
        }
        String nl = label.toLowerCase(Locale.ROOT).trim();
        if (nl.equals(core)) {
            return 4;
        }
        if (isPunctuationName(core)) {
            return 99;
        }
        if (nl.startsWith(core) || (core.startsWith(nl) && nl.length() >= 4)) {
            return 5;
        }
        if (nl.contains(core) || (core.contains(nl) && nl.length() >= 4)) {
            return 6;
        }
        return 99;
    }

    public static boolean labelMatches(String core, String label) {
        return matchScore(core, label) < 99;
    }

    /**
     * Generic spawn-egg → entity id hint (Minecraft convention). Not a pack alias table.
     */
    public static List<String> relatedHintIds(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return List.of();
        }
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        if (!id.endsWith("_spawn_egg") || id.length() <= 10) {
            return List.of();
        }
        String entity = id.substring(0, id.length() - "_spawn_egg".length());
        if (entity.indexOf(':') <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>(2);
        out.add(entity);
        out.add(entity.substring(entity.indexOf(':') + 1));
        return List.copyOf(out);
    }

    private static boolean hasHan(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
