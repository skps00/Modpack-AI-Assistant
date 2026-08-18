package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JEI Information / 信息 pages → FACT. No item-id hardcodes.
 * Live JEI dump lines {@code jei_info_acquire:} / {@code jei_info_use:};
 * KubeJS {@code JEIEvents.information} → graph edges {@code via:jei_info}.
 */
public final class JeiInfoFacts {
    public static final String MARK_ACQUIRE = "jei_info_acquire:";
    public static final String MARK_USE = "jei_info_use:";

    public enum Kind {
        ACQUIRE,
        PURPOSE
    }

    private static final Pattern REGISTRY_ID = Pattern.compile(
            "[a-z0-9_]+:[a-z0-9_./-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]{1,240})['\"]");
    private static final Pattern ADD_ITEM_CALL = Pattern.compile(
            "\\.addItem\\s*\\(|\\.add\\s*\\(\\s*['\"]item['\"]\\s*,",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CARRY = Pattern.compile(
            "(?:携带|攜帶|手持|穿戴|while\\s+(?:holding|wearing|carrying)|carry(?:ing)?)\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSPECIFIED_MISS = Pattern.compile(
            "(?m)^[^\\n]*(?:未标明|未標明|does not specify which|doesn't specify which|"
                    + "index does not say|index doesn't say)[^\\n]*\\r?\\n?");

    private JeiInfoFacts() {}

    public static boolean isInfoCategory(String uid, String title) {
        String u = uid == null ? "" : uid.toLowerCase(Locale.ROOT);
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (u.contains("information") || t.contains("information")
                || u.contains("info_category") || t.contains("info_category")) {
            return true;
        }
        String raw = title == null ? "" : title;
        return raw.contains("信息") || raw.contains("資訊") || raw.contains("资讯");
    }

    /**
     * Info page about other outputs (drops) while focus is the tool → use.
     * Focus listed as an output → obtain. Carry-X-to-get-Y text with focus as X → use.
     */
    public static Kind classify(String focusId, List<String> outputIds, String text) {
        if (focusId == null || focusId.isBlank()) {
            return Kind.ACQUIRE;
        }
        List<String> outs = new ArrayList<>();
        if (outputIds != null) {
            for (String id : outputIds) {
                if (id != null && !id.isBlank()) {
                    outs.add(id.toLowerCase(Locale.ROOT).trim());
                }
            }
        }
        boolean focusOut = false;
        boolean otherOut = false;
        for (String id : outs) {
            if (sameItem(id, focusId)) {
                focusOut = true;
            } else {
                otherOut = true;
            }
        }
        if (otherOut && !focusOut) {
            return Kind.PURPOSE;
        }
        if (focusOut) {
            return Kind.ACQUIRE;
        }
        if (isCarryToGet(text) && mentionsFocusAsCarried(text, focusId)) {
            return Kind.PURPOSE;
        }
        return Kind.ACQUIRE;
    }

    public static boolean isCarryToGet(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (!CARRY.matcher(text).find()) {
            return false;
        }
        return text.contains("获得") || text.contains("獲得") || text.contains("取得")
                || text.toLowerCase(Locale.ROOT).contains("obtain")
                || text.toLowerCase(Locale.ROOT).contains("drop")
                || text.contains("概率") || text.contains("機率") || text.contains("%");
    }

    public static boolean mentionsFocus(String text, String focusId) {
        if (text == null || text.isBlank() || focusId == null || focusId.isBlank()) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        String id = focusId.toLowerCase(Locale.ROOT).trim();
        if (t.contains(id)) {
            return true;
        }
        String path = pathOf(id);
        return path.length() >= 2 && t.contains(path);
    }

    public static boolean mentionsFocusAsCarried(String text, String focusId) {
        if (text == null || focusId == null || focusId.isBlank()) {
            return false;
        }
        Matcher m = CARRY.matcher(text);
        if (!m.find()) {
            return mentionsFocus(text, focusId) && isCarryToGet(text);
        }
        int from = m.end();
        int to = Math.min(text.length(), from + 64);
        String token = text.substring(from, to).trim();
        int cut = 0;
        while (cut < token.length()) {
            char c = token.charAt(cut);
            if (Character.isWhitespace(c) || c == '击' || c == '擊' || c == ',' || c == '，'
                    || c == '.' || c == '。' || c == ';' || c == '；') {
                break;
            }
            cut++;
        }
        token = token.substring(0, cut).replace("'", "").replace("\"", "").trim();
        if (token.isEmpty()) {
            return mentionsFocus(text, focusId);
        }
        return sameItem(token, focusId) || mentionsFocus(token, focusId);
    }

    public static String graphFact(String itemId, Kind kind, String text, List<String> outputIds) {
        String id = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT).trim();
        String note = text == null ? "" : text.trim();
        String edge = kind == Kind.PURPOSE ? " -[script_use]-> " : " -[loot]-> ";
        StringBuilder sb = new StringBuilder();
        sb.append("item:").append(id).append(edge).append("via:jei_info + text:").append(note);
        if (outputIds != null && !outputIds.isEmpty()) {
            LinkedHashSet<String> outs = new LinkedHashSet<>();
            for (String o : outputIds) {
                if (o != null && !o.isBlank()) {
                    outs.add(o.toLowerCase(Locale.ROOT).trim());
                }
            }
            if (!outs.isEmpty()) {
                sb.append(" + outputs:").append(String.join(",", outs));
            }
        }
        return sb.toString();
    }

    public static final String RELATED_SEP = " | related:";

    public static String dumpLine(Kind kind, String text) {
        return dumpLine(kind, text, List.of());
    }

    /**
     * JEI info body for FACT / dump lines: literal {@code \n} becomes space so
     * {@link #splitFromDump} stays one line per mark (player scrub still turns leftover {@code \n}
     * in the visible bubble into real breaks).
     */
    public static String normalizeInfoText(String text) {
        String t = AskReplyScrub.unescapeLiteralNewlines(text == null ? "" : text);
        t = t.replace('\r', ' ').replace('\n', ' ');
        return t.replaceAll("[ \\t]{2,}", " ").trim();
    }

    public static String dumpLine(Kind kind, String text, List<String> relatedIds) {
        String note = normalizeInfoText(text);
        String line = (kind == Kind.PURPOSE ? MARK_USE : MARK_ACQUIRE) + " " + note;
        if (relatedIds == null || relatedIds.isEmpty()) {
            return line;
        }
        LinkedHashSet<String> rel = new LinkedHashSet<>();
        for (String id : relatedIds) {
            if (id != null && !id.isBlank()) {
                rel.add(id.toLowerCase(Locale.ROOT).trim());
            }
        }
        if (rel.isEmpty()) {
            return line;
        }
        return line + RELATED_SEP + String.join(",", rel);
    }

    public static String stripRelated(String dumpText) {
        if (dumpText == null || dumpText.isBlank()) {
            return dumpText == null ? "" : dumpText;
        }
        int i = dumpText.indexOf(RELATED_SEP);
        return i < 0 ? dumpText : dumpText.substring(0, i).trim();
    }

    public static List<String> relatedFromDumpLine(String dumpLine) {
        if (dumpLine == null) {
            return List.of();
        }
        int nl = dumpLine.indexOf('\n');
        String line = nl < 0 ? dumpLine : dumpLine.substring(0, nl);
        int i = line.indexOf(RELATED_SEP);
        if (i < 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String p : line.substring(i + RELATED_SEP.length()).split(",")) {
            String id = p.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    public static List<String> parseKubeJs(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        if (!ADD_ITEM_CALL.matcher(source).find()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Matcher m = ADD_ITEM_CALL.matcher(source);
        int guard = 0;
        while (m.find() && guard++ < 80 && out.size() < 80) {
            int open = source.indexOf('(', m.start());
            if (open < 0) {
                continue;
            }
            String body = parenBody(source, open);
            if (body == null || body.length() < 6) {
                continue;
            }
            List<String> ids = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            Matcher q = QUOTED.matcher(body);
            while (q.find()) {
                String raw = q.group(1).trim();
                if (raw.isEmpty() || "item".equalsIgnoreCase(raw)) {
                    continue;
                }
                if (REGISTRY_ID.matcher(raw).matches()) {
                    String id = raw.toLowerCase(Locale.ROOT);
                    if (!PackIndex.isNoiseItemId(id)) {
                        ids.add(id);
                    }
                } else if (raw.length() >= 2 && raw.length() <= 240) {
                    notes.add(raw);
                }
            }
            if (ids.isEmpty() || notes.isEmpty()) {
                continue;
            }
            String note = normalizeInfoText(String.join(" ", notes));
            if (note.length() < 2) {
                continue;
            }
            for (String id : ids) {
                Kind kind = classify(id, List.of(), note);
                String line = graphFact(id, kind, note, List.of());
                if (seen.add(line)) {
                    out.add(line);
                }
            }
        }
        return List.copyOf(out);
    }

    public static List<String> factsForFocus(List<String> parsed, String focusId) {
        if (parsed == null || parsed.isEmpty() || focusId == null || focusId.isBlank()) {
            return List.of();
        }
        String id = focusId.toLowerCase(Locale.ROOT).trim();
        String lootPref = "item:" + id + " -[loot]-> ";
        String usePref = "item:" + id + " -[script_use]-> ";
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String fact : parsed) {
            if (fact == null || !fact.contains("via:jei_info")) {
                continue;
            }
            if (fact.startsWith(lootPref) || fact.startsWith(usePref)) {
                out.add(fact);
                continue;
            }
            String note = textFromFact(fact);
            if (!mentionsFocus(note, id)) {
                continue;
            }
            Kind kind = classify(id, outputsFromFact(fact), note);
            out.add(graphFact(id, kind, note, outputsFromFact(fact)));
        }
        return List.copyOf(out);
    }

    public static String textFromFact(String fact) {
        if (fact == null) {
            return "";
        }
        int t = fact.indexOf("text:");
        if (t < 0) {
            return "";
        }
        String rest = fact.substring(t + "text:".length());
        int plus = rest.indexOf(" + ");
        if (plus >= 0) {
            rest = rest.substring(0, plus);
        }
        return rest.trim();
    }

    public static List<String> outputsFromFact(String fact) {
        if (fact == null) {
            return List.of();
        }
        int o = fact.indexOf("outputs:");
        if (o < 0) {
            return List.of();
        }
        String rest = fact.substring(o + "outputs:".length()).trim();
        int plus = rest.indexOf(" + ");
        if (plus >= 0) {
            rest = rest.substring(0, plus);
        }
        List<String> out = new ArrayList<>();
        for (String p : rest.split(",")) {
            String id = p.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    public static Split splitFromDump(String jeiSummary) {
        List<String> acquire = new ArrayList<>();
        List<String> use = new ArrayList<>();
        if (jeiSummary == null || jeiSummary.isBlank()) {
            return new Split(List.of(), List.of());
        }
        for (String raw : jeiSummary.split("\n")) {
            String line = raw.trim();
            if (line.startsWith(MARK_ACQUIRE)) {
                String t = stripRelated(line.substring(MARK_ACQUIRE.length()).trim());
                if (!t.isEmpty()) {
                    acquire.add(t);
                }
            } else if (line.startsWith(MARK_USE)) {
                String t = stripRelated(line.substring(MARK_USE.length()).trim());
                if (!t.isEmpty()) {
                    use.add(t);
                }
            }
        }
        return new Split(List.copyOf(acquire), List.copyOf(use));
    }

    public static boolean hasAny(String jeiSummary) {
        Split s = splitFromDump(jeiSummary);
        return !s.acquire().isEmpty() || !s.use().isEmpty();
    }

    public static List<String> mergeUnique(List<String> base, List<String> extra) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (base != null) {
            for (String s : base) {
                if (s != null && !s.isBlank()) {
                    out.add(s);
                }
            }
        }
        if (extra != null) {
            for (String s : extra) {
                if (s != null && !s.isBlank() && !alreadyContains(out, s)) {
                    out.add(s);
                }
            }
        }
        return List.copyOf(out);
    }

    public static String stripUnspecifiedMiss(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer == null ? "" : answer;
        }
        return UNSPECIFIED_MISS.matcher(answer).replaceAll("");
    }

    public static boolean looksUnspecifiedMiss(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        return UNSPECIFIED_MISS.matcher(answer).find();
    }

    public static boolean sameItem(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.toLowerCase(Locale.ROOT).trim();
        String y = b.toLowerCase(Locale.ROOT).trim();
        if (x.equals(y)) {
            return true;
        }
        return pathOf(x).equals(pathOf(y)) && !pathOf(x).isEmpty();
    }

    static String pathOf(String id) {
        if (id == null) {
            return "";
        }
        String s = id.toLowerCase(Locale.ROOT).trim();
        int c = s.indexOf(':');
        return c >= 0 ? s.substring(c + 1) : s;
    }

    private static boolean alreadyContains(LinkedHashSet<String> have, String extra) {
        if (have.contains(extra)) {
            return true;
        }
        for (String s : have) {
            if (s.contains(extra) || extra.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private static String parenBody(String src, int openParen) {
        int depth = 0;
        for (int i = openParen; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return src.substring(openParen + 1, i);
                }
            }
        }
        return null;
    }

    public record Split(List<String> acquire, List<String> use) {
        public Split {
            acquire = acquire == null ? List.of() : List.copyOf(acquire);
            use = use == null ? List.of() : List.copyOf(use);
        }

        public boolean isEmpty() {
            return acquire.isEmpty() && use.isEmpty();
        }
    }
}
