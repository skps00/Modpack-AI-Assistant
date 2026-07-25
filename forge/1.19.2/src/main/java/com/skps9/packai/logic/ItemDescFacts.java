package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic item description / score / trigger / strategy-body facts from KubeJS-style scripts.
 * Not tied to any one modpack — covers tooltip registries, Organ builders, strategy maps.
 */
public final class ItemDescFacts {
    private static final int MAX_PER_ITEM = 12;
    private static final int MAX_EFFECT_PER_ITEM = 4;
    private static final int BODY_CHARS = 2200;
    private static final int FN_BODY_CHARS = 2500;

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

    /** {@code const organRightClickedOnlyStrategies = {} } */
    private static final Pattern STRATEGIES_MAP = Pattern.compile(
            "(?:(?:const|let|var)\\s+)?([A-Za-z_][A-Za-z0-9_]*[Ss]trategies[A-Za-z0-9_]*)\\s*=\\s*\\{",
            Pattern.CASE_INSENSITIVE);

    /** {@code 'kubejs:id': function (event, organ) { ... }} */
    private static final Pattern MAP_ENTRY = Pattern.compile(
            "['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]\\s*:\\s*function\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    /** {@code .addOnlyStrategy('entity_tick', FurnaceCoreEntityTickDefer} */
    private static final Pattern STRATEGY_FN = Pattern.compile(
            "\\.add(?:Only)?Strategy\\(\\s*['\"]([a-z0-9_]+)['\"]\\s*,\\s*([A-Za-z_][A-Za-z0-9_]*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ORGAN_STRATEGY_MODEL = Pattern.compile(
            "(?:OrganStrategyModel|_OrganStrategyModel)\\s*\\(\\s*['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GIVE = Pattern.compile(
            "(?:\\.give|giveInHand|popItem(?:FromFace)?)\\s*\\(\\s*(?:Item\\.of\\()?['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EFFECT = Pattern.compile(
            "(?:potionEffects\\.add|\\.addEffect)\\s*\\(\\s*['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REPLACE_ITEM = Pattern.compile(
            "replaceItem\\s*=\\s*Item\\.of\\(\\s*['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]",
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
        LinkedHashSet<String> out = new LinkedHashSet<>();
        parseTooltipAndRegister(text, tr, out);
        parseStrategyMapEntries(text, out);
        parseStrategyNamedFns(text, out);
        return List.copyOf(out);
    }

    private static void parseTooltipAndRegister(
            String text, Function<String, String> tr, LinkedHashSet<String> out) {
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
    }

    /**
     * NFWC1-style strategy tables: {@code fooStrategies = { 'id': function … }}.
     * Event inferred from map name / same-file ItemEvents usage (dispatch).
     */
    private static void parseStrategyMapEntries(String text, LinkedHashSet<String> out) {
        Map<String, String> mapEvents = inferStrategyMapEvents(text);
        Matcher mapM = STRATEGIES_MAP.matcher(text);
        int mapGuard = 0;
        boolean anyMap = false;
        while (mapM.find() && mapGuard++ < 80) {
            anyMap = true;
            String mapName = mapM.group(1);
            String event = mapEvents.getOrDefault(mapName, inferEventFromMapName(mapName));
            String mapBody = extractBraceBody(text, mapM.end() - 1);
            if (mapBody == null || mapBody.length() < 8) {
                continue;
            }
            Matcher m = MAP_ENTRY.matcher(mapBody);
            int guard = 0;
            while (m.find() && guard++ < 200) {
                String itemId = m.group(1).toLowerCase(Locale.ROOT);
                if (PackIndex.isNoiseItemId(itemId)) {
                    continue;
                }
                String body = extractBraceBody(mapBody, m.end());
                if (body == null || body.length() < 8) {
                    continue;
                }
                addEffectFacts(out, itemId, event, body);
            }
        }
        if (!anyMap) {
            Matcher m = MAP_ENTRY.matcher(text);
            int guard = 0;
            while (m.find() && guard++ < 200) {
                String itemId = m.group(1).toLowerCase(Locale.ROOT);
                if (PackIndex.isNoiseItemId(itemId)) {
                    continue;
                }
                String body = extractBraceBody(text, m.end());
                if (body == null || body.length() < 8) {
                    continue;
                }
                addEffectFacts(out, itemId, "active", body);
            }
        }
    }

    /** Same-file: event handler body mentions {@code FooStrategies[} → bind event. */
    private static Map<String, String> inferStrategyMapEvents(String text) {
        Map<String, String> out = new HashMap<>();
        Pattern header = Pattern.compile(
                "(?:ItemEvents|BlockEvents|PlayerEvents|EntityEvents)\\.(\\w+)\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        Matcher h = header.matcher(text);
        while (h.find()) {
            String method = h.group(1).toLowerCase(Locale.ROOT);
            String via = switch (method) {
                case "rightclicked" -> "item_right_clicked";
                case "leftclicked", "firstleftclicked" -> "item_left_clicked";
                case "broken" -> "block_broken";
                case "foodeaten" -> "food_eaten";
                case "tick" -> "entity_tick";
                case "entityinteracted" -> "entity_interact";
                default -> method;
            };
            String body = extractBraceBody(text, h.end());
            if (body == null) {
                continue;
            }
            Matcher ref = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*[Ss]trategies[A-Za-z0-9_]*)\\s*\\[")
                    .matcher(body);
            while (ref.find()) {
                out.putIfAbsent(ref.group(1), via);
            }
        }
        return out;
    }

    static String inferEventFromMapName(String mapName) {
        if (mapName == null) {
            return "active";
        }
        String n = mapName.toLowerCase(Locale.ROOT);
        if (n.contains("right") || n.contains("rclick")) {
            return "item_right_clicked";
        }
        if (n.contains("broken") || n.contains("break")) {
            return "block_broken";
        }
        if (n.contains("food") || n.contains("eat")) {
            return "food_eaten";
        }
        if (n.contains("tick")) {
            return "entity_tick";
        }
        if (n.contains("key")) {
            return "key_active";
        }
        return "active";
    }

    /**
     * NFWC2-style {@code new OrganStrategyModel('id').addOnlyStrategy('ev', Fn)} —
     * resolve {@code Fn} body in the same file.
     */
    private static void parseStrategyNamedFns(String text, LinkedHashSet<String> out) {
        Matcher m = ORGAN_STRATEGY_MODEL.matcher(text);
        int guard = 0;
        while (m.find() && guard++ < 200) {
            String itemId = m.group(1).toLowerCase(Locale.ROOT);
            if (PackIndex.isNoiseItemId(itemId)) {
                continue;
            }
            int from = m.end();
            int to = Math.min(text.length(), from + BODY_CHARS);
            Matcher next = ORGAN_STRATEGY_MODEL.matcher(text);
            if (next.find(from) && next.start() < to) {
                to = next.start();
            }
            String chunk = text.substring(from, to);
            Matcher sm = STRATEGY_FN.matcher(chunk);
            while (sm.find()) {
                String event = sm.group(1).toLowerCase(Locale.ROOT);
                String fn = sm.group(2);
                String body = findNamedFunctionBody(text, fn);
                if (body == null || body.length() < 8) {
                    continue;
                }
                addEffectFacts(out, itemId, event, body);
            }
        }
    }

    private static void addEffectFacts(
            LinkedHashSet<String> out, String itemId, String event, String body) {
        int added = 0;
        Matcher rm = REPLACE_ITEM.matcher(body);
        while (rm.find() && added < MAX_EFFECT_PER_ITEM) {
            String id = rm.group(1).toLowerCase(Locale.ROOT);
            if (PackIndex.isNoiseItemId(id) || id.equals(itemId)) {
                continue;
            }
            String fact = "item:" + itemId + " -[on:" + event + "]-> becomes:" + id;
            if (out.add(fact)) {
                added++;
            }
        }
        Matcher gm = GIVE.matcher(body);
        while (gm.find() && added < MAX_EFFECT_PER_ITEM) {
            String id = gm.group(1).toLowerCase(Locale.ROOT);
            if (PackIndex.isNoiseItemId(id) || id.equals(itemId)) {
                continue;
            }
            String fact = "item:" + itemId + " -[on:" + event + "]-> gives:" + id;
            if (out.add(fact)) {
                added++;
            }
        }
        Matcher em = EFFECT.matcher(body);
        while (em.find() && added < MAX_EFFECT_PER_ITEM) {
            String id = em.group(1).toLowerCase(Locale.ROOT);
            if (PackIndex.isNoiseItemId(id)) {
                continue;
            }
            String fact = "item:" + itemId + " -[on:" + event + "]-> effect:" + id;
            if (out.add(fact)) {
                added++;
            }
        }
    }

    static String findNamedFunctionBody(String text, String fnName) {
        if (text == null || fnName == null || fnName.isBlank()) {
            return null;
        }
        Pattern[] pats = {
                Pattern.compile(
                        "function\\s+" + Pattern.quote(fnName) + "\\s*\\(",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile(
                        "(?:const|let|var)\\s+" + Pattern.quote(fnName) + "\\s*=\\s*function\\s*\\(",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile(
                        "(?:const|let|var)\\s+" + Pattern.quote(fnName) + "\\s*=\\s*\\([^)]*\\)\\s*=>",
                        Pattern.CASE_INSENSITIVE),
        };
        for (Pattern p : pats) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return extractBraceBody(text, m.end());
            }
        }
        return null;
    }

    /** Extract `{ ... }` body starting at/after {@code from} (skips to first `{`). */
    static String extractBraceBody(String text, int from) {
        if (text == null || from < 0 || from >= text.length()) {
            return "";
        }
        int brace = text.indexOf('{', from);
        if (brace < 0 || brace - from > 120) {
            return "";
        }
        int depth = 0;
        int limit = Math.min(text.length(), brace + FN_BODY_CHARS);
        for (int j = brace; j < limit; j++) {
            char c = text.charAt(j);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(brace, j + 1);
                }
            }
        }
        return text.substring(brace, limit);
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
