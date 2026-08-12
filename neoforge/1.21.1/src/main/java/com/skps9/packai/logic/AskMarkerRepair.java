package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FACT-grounded post-LLM marker re-attach／repair.
 * Only restores exact {@code {{item:}}}/{@code [[item:]]}/{@code [[recipe:]]}/{@code {{RECIPE}}}
 * strings that already appeared in this turn's FACT (or unique damaged→FACT match).
 * Never invents registry ids or pack hardcodes.
 */
public final class AskMarkerRepair {
    private static final String REGISTRY_REF = "[a-z0-9_]+(?::[a-z0-9_./-]+)+";
    private static final String FLAT_SNBT = "\\{[^}]*\\}";
    private static final Pattern RECIPE_MARKER = Pattern.compile(
            "(?:\\{\\{\\s*RECIPE(?:\\s*:\\s*(\\d+|" + REGISTRY_REF + "))?\\s*\\}\\}"
                    + "|\\[\\[\\s*recipe(?:_card)?\\s*:\\s*(\\d+|" + REGISTRY_REF + ")\\s*\\]\\])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_MARKER = Pattern.compile(
            "(?:\\[\\[\\s*item\\s*:\\s*(" + REGISTRY_REF + ")(" + FLAT_SNBT + ")?(?:\\s*[×xX*]\\s*(\\d+))?\\s*\\]\\]"
                    + "|\\{\\{\\s*item\\s*:\\s*(" + REGISTRY_REF + ")(" + FLAT_SNBT + ")?(?:\\s*[×xX*]\\s*(\\d+))?\\s*\\}\\}"
                    + "|\\{\\s*item\\s*:\\s*(" + REGISTRY_REF + ")(" + FLAT_SNBT + ")?(?:\\s*[×xX*]\\s*(\\d+))?\\s*\\})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_MARKER = Pattern.compile(
            RECIPE_MARKER.pattern() + "|" + ITEM_MARKER.pattern(),
            Pattern.CASE_INSENSITIVE);
    /** Empty / whitespace-only item marker shell LLM left behind. */
    private static final Pattern EMPTY_ITEM = Pattern.compile(
            "(?:\\{\\{\\s*item\\s*:\\s*\\}\\}|\\[\\[\\s*item\\s*:\\s*\\]\\]|\\{\\s*item\\s*:\\s*\\})",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_REINSERT = 16;

    private AskMarkerRepair() {}

    /**
     * Legal markers for this turn = exact UI markers found in FACT lines (order preserved).
     * {@code cards} / {@code suggestedItemIds} kept for plan API parity — <b>unused</b>;
     * never synthesize embeds from bare ids (invent ban).
     */
    public static List<String> collectAllowed(
            Iterable<String> factLines,
            @SuppressWarnings("unused") List<RecipeCard> cards,
            @SuppressWarnings("unused") List<String> suggestedItemIds
    ) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        collectMarkersInto(factLines, ordered);
        // ponytail: cards / suggestedItemIds reserved for API parity with plan —
        // never synthesize {{item:}} / [[recipe:]] from bare ids (invent ban).
        return List.copyOf(ordered);
    }

    /** Extract well-formed UI markers from texts in appearance order (deduped). */
    public static List<String> collectFromTexts(Iterable<String> texts) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        collectMarkersInto(texts, ordered);
        return List.copyOf(ordered);
    }

    /**
     * After {@link AskReplyScrub}, before {@link RecipeEmbed}: restore missing FACT markers;
     * repair damaged shells only when unique against the allowlist.
     */
    public static String repair(String answer, List<String> allowedExact) {
        if (allowedExact == null || allowedExact.isEmpty()) {
            return answer == null ? "" : answer;
        }
        String body = answer == null ? "" : answer;
        Map<String, String> byKey = indexByKey(allowedExact);
        body = upgradeBareToFactNbt(body, byKey, allowedExact);
        body = repairEmptyShells(body, allowedExact);
        body = reinsertMissing(body, allowedExact, byKey);
        return body;
    }

    private static void collectMarkersInto(Iterable<String> texts, LinkedHashSet<String> out) {
        if (texts == null || out == null) {
            return;
        }
        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            Matcher m = ANY_MARKER.matcher(text);
            while (m.find()) {
                String exact = m.group();
                if (exact != null && !exact.isBlank()) {
                    out.add(exact);
                }
            }
        }
    }

    private static Map<String, String> indexByKey(List<String> allowedExact) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String exact : allowedExact) {
            String key = markerKey(exact);
            if (key.isEmpty() || map.containsKey(key)) {
                continue;
            }
            map.put(key, exact);
        }
        return map;
    }

    /** Presence key ignores count suffix; keeps flat SNBT. */
    static String markerKey(String exactMarker) {
        if (exactMarker == null || exactMarker.isBlank()) {
            return "";
        }
        Matcher item = ITEM_MARKER.matcher(exactMarker);
        if (item.find()) {
            String id = firstNonNull(item.group(1), firstNonNull(item.group(4), item.group(7)));
            String snbt = firstNonNull(item.group(2), firstNonNull(item.group(5), item.group(8)));
            String payload = id == null ? "" : id;
            if (snbt != null && !snbt.isBlank()) {
                payload = payload + snbt;
            }
            return "item:" + RecipeEmbed.normalizeRegistryRef(payload);
        }
        Matcher recipe = RECIPE_MARKER.matcher(exactMarker);
        if (recipe.find()) {
            String ref = firstNonNull(recipe.group(1), recipe.group(2));
            if (ref == null || ref.isBlank()) {
                return "recipe:";
            }
            return "recipe:" + RecipeEmbed.normalizeRegistryRef(ref);
        }
        return "";
    }

    private static String upgradeBareToFactNbt(
            String body, Map<String, String> byKey, List<String> allowedExact
    ) {
        Matcher m = ITEM_MARKER.matcher(body);
        StringBuffer sb = new StringBuffer();
        boolean any = false;
        while (m.find()) {
            String exactInAnswer = m.group();
            String id = firstNonNull(m.group(1), firstNonNull(m.group(4), m.group(7)));
            String snbt = firstNonNull(m.group(2), firstNonNull(m.group(5), m.group(8)));
            if (id == null || id.isBlank()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(exactInAnswer));
                continue;
            }
            if (snbt != null && !snbt.isBlank()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(exactInAnswer));
                continue;
            }
            String bare = RecipeEmbed.bareRegistryId(id);
            String unique = uniqueAllowedForBare(bare, allowedExact);
            if (unique == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(exactInAnswer));
                continue;
            }
            String factKey = markerKey(unique);
            // Only upgrade when FACT form carries SNBT (or differs) and answer lacks it.
            if (factKey.equals(markerKey(exactInAnswer)) || !unique.toLowerCase(Locale.ROOT).contains("{")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(exactInAnswer));
                continue;
            }
            any = true;
            m.appendReplacement(sb, Matcher.quoteReplacement(unique));
            byKey.putIfAbsent(factKey, unique);
        }
        if (!any) {
            return body;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String uniqueAllowedForBare(String bareId, List<String> allowedExact) {
        if (bareId == null || bareId.isBlank()) {
            return null;
        }
        String hit = null;
        for (String exact : allowedExact) {
            Matcher item = ITEM_MARKER.matcher(exact);
            if (!item.find()) {
                continue;
            }
            String id = firstNonNull(item.group(1), firstNonNull(item.group(4), item.group(7)));
            if (id == null || !bareId.equals(RecipeEmbed.bareRegistryId(id))) {
                continue;
            }
            if (hit != null) {
                return null; // not unique
            }
            hit = exact;
        }
        return hit;
    }

    private static String repairEmptyShells(String body, List<String> allowedExact) {
        if (!EMPTY_ITEM.matcher(body).find()) {
            return body;
        }
        if (allowedExact.size() != 1) {
            // Ambiguous — strip empty shells only (no invent).
            return EMPTY_ITEM.matcher(body).replaceAll("");
        }
        String only = allowedExact.get(0);
        return EMPTY_ITEM.matcher(body).replaceAll(Matcher.quoteReplacement(only));
    }

    private static String reinsertMissing(
            String body, List<String> allowedExact, Map<String, String> byKey
    ) {
        LinkedHashSet<String> present = new LinkedHashSet<>();
        Matcher m = ANY_MARKER.matcher(body);
        while (m.find()) {
            String key = markerKey(m.group());
            if (!key.isEmpty()) {
                present.add(key);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String exact : allowedExact) {
            String key = markerKey(exact);
            if (key.isEmpty() || present.contains(key)) {
                continue;
            }
            missing.add(exact);
            present.add(key);
            if (missing.size() >= MAX_REINSERT) {
                break;
            }
        }
        if (missing.isEmpty()) {
            return body;
        }
        String block = String.join(" ", missing);
        Matcher src = ReplySources.HEADER.matcher(body);
        if (src.find()) {
            int at = src.start();
            String before = body.substring(0, at).stripTrailing();
            String after = body.substring(at);
            if (before.isEmpty()) {
                return block + "\n\n" + after;
            }
            return before + "\n\n" + block + "\n\n" + after;
        }
        if (body.isBlank()) {
            return block;
        }
        // Lead icons (gateway pearl) — prepend so body has figures, not footer-only.
        return block + "\n\n" + body.stripLeading();
    }

    private static String firstNonNull(String a, String b) {
        if (a != null) {
            return a;
        }
        return b;
    }
}
