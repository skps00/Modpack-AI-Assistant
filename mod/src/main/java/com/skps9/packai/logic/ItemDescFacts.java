package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic item description / score / trigger facts from KubeJS-style scripts.
 * Not tied to any one modpack — covers tooltip registries, Organ builders, strategy maps.
 */
public final class ItemDescFacts {
    private static final int MAX_PER_ITEM = 8;
    private static final int BODY_CHARS = 2200;

    /** Item binding for tooltip / organ / strategy blocks. */
    private static final Pattern ITEM_BLOCK = Pattern.compile(
            "(?:RegistryOrganTooltip\\s*\\(\\s*new\\s+MultiStateTooltip"
                    + "|new\\s+MultiStateTooltip"
                    + "|RegistryOrgan"
                    + "|new\\s+Organ"
                    + "|OrganStrategyModel"
                    + "|_OrganStrategyModel)\\s*\\(\\s*['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TRANSLATABLE = Pattern.compile(
            "(?:Text|Component)\\.translatable\\(\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ADD_SCORE = Pattern.compile(
            "\\.addScore\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*(-?[0-9.]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STRATEGY_EVENT = Pattern.compile(
            "\\.add(?:Only)?Strategy\\(\\s*['\"]([a-z0-9_]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    private ItemDescFacts() {}

    /**
     * Parse script text into graph-style facts, resolving translation keys when possible.
     *
     * @param translate key → localized string (may return null)
     */
    public static List<String> parse(String text, Function<String, String> translate) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Function<String, String> tr = translate == null ? k -> null : translate;
        List<String> out = new ArrayList<>();
        Matcher m = ITEM_BLOCK.matcher(text);
        int guard = 0;
        while (m.find() && guard++ < 400) {
            String itemId = m.group(1).toLowerCase(Locale.ROOT);
            if (PackIndex.isNoiseItemId(itemId)) {
                continue;
            }
            int from = m.end();
            int to = Math.min(text.length(), from + BODY_CHARS);
            Matcher next = ITEM_BLOCK.matcher(text);
            if (next.find(from) && next.start() < to) {
                to = next.start();
            }
            String body = text.substring(from, to);
            LinkedHashSet<String> facts = new LinkedHashSet<>();
            Matcher tm = TRANSLATABLE.matcher(body);
            while (tm.find() && facts.size() < MAX_PER_ITEM) {
                String key = tm.group(1);
                String resolved = tr.apply(key);
                if (resolved == null || resolved.isBlank()) {
                    continue;
                }
                String clip = resolved.length() > 160 ? resolved.substring(0, 160) + "…" : resolved;
                facts.add("item:" + itemId + " -[desc]-> " + clip.replace('\n', ' ').trim());
            }
            Matcher sm = ADD_SCORE.matcher(body);
            while (sm.find() && facts.size() < MAX_PER_ITEM) {
                String score = sm.group(1).toLowerCase(Locale.ROOT);
                String val = sm.group(2);
                facts.add("item:" + itemId + " -[score]-> " + score + "=" + val);
            }
            Matcher em = STRATEGY_EVENT.matcher(body);
            LinkedHashSet<String> events = new LinkedHashSet<>();
            while (em.find() && events.size() < 6) {
                events.add(em.group(1).toLowerCase(Locale.ROOT));
            }
            for (String ev : events) {
                if (facts.size() >= MAX_PER_ITEM) {
                    break;
                }
                facts.add("item:" + itemId + " -[triggers]-> " + ev);
            }
            out.addAll(facts);
        }
        return List.copyOf(out);
    }

    /** Merge parsed facts into {@code descByItem}. */
    public static void mergeInto(Map<String, List<String>> descByItem, List<String> facts) {
        if (descByItem == null || facts == null) {
            return;
        }
        for (String f : facts) {
            if (f == null || !f.startsWith("item:")) {
                continue;
            }
            int sep = f.indexOf(" -[");
            if (sep < 6) {
                continue;
            }
            String id = f.substring(5, sep).toLowerCase(Locale.ROOT);
            List<String> list = descByItem.computeIfAbsent(id, k -> new ArrayList<>());
            if (list.size() >= MAX_PER_ITEM || list.contains(f)) {
                continue;
            }
            list.add(f);
        }
    }
}
