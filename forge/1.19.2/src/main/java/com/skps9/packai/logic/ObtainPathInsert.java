package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.world.item.Item;

/**
 * Obtain-path body insert: when recipe cards are shown, skip obtain prose entirely;
 * otherwise insert a capped slice of the primary path only.
 */
public final class ObtainPathInsert {
    /** Max lines inserted into the answer body when {@code cardsOut == 0}. */
    static final int MAX_BODY_INSERT_LINES = 3;

    private static final Pattern OBTAIN_STEP_BODY = Pattern.compile(
            "^(.*?[:：]\\s*)(.+?)(\\s*(?:->|→)\\s*.+)$");

    private ObtainPathInsert() {}

    /**
     * When {@code cardsOut > 0}, returns empty (no obtain heading/lines). When zero cards,
     * keeps the first complete path (else first path), merges repeated input labels, caps lines,
     * and may append {@code packai.obtain.too_many} for alternative routes.
     */
    public static List<ObtainPathText.Line> filterLinesForCards(
            List<ObtainPathText.Line> lines,
            List<ObtainPaths.Path> paths,
            int cardsOut,
            String lang) {
        if (cardsOut > 0) {
            return List.of();
        }
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        ObtainPaths.Path primary = pickPrimaryPath(paths);
        List<ObtainPathText.Line> primaryLines = primary == null
                ? List.copyOf(lines)
                : linesForPath(lines, primary);
        if (primaryLines.isEmpty()) {
            return List.of();
        }
        int altPaths = countAlternativePaths(paths, primary);
        int contentCap = altPaths > 0 ? MAX_BODY_INSERT_LINES - 1 : MAX_BODY_INSERT_LINES;
        boolean primaryTruncated = primaryLines.size() > contentCap;
        List<ObtainPathText.Line> out = new ArrayList<>(MAX_BODY_INSERT_LINES);
        int n = Math.min(contentCap, primaryLines.size());
        for (int i = 0; i < n; i++) {
            ObtainPathText.Line ln = primaryLines.get(i);
            if (ln == null) {
                continue;
            }
            String text = ln.text() == null ? "" : mergeRepeatedInputsInLine(ln.text());
            out.add(new ObtainPathText.Line(ln.key(), text));
        }
        if (altPaths > 0 && primaryTruncated && out.size() < MAX_BODY_INSERT_LINES) {
            String more = formatAltRouteCount(altPaths);
            String text = ReplyLang.tr(lang, "packai.obtain.too_many", more);
            out.add(new ObtainPathText.Line("M|too_many", text));
        }
        return List.copyOf(out);
    }

    /** Harness negative control — no card skip / slim. */
    static List<ObtainPathText.Line> filterLinesForCardsDisabled(
            List<ObtainPathText.Line> lines,
            List<ObtainPaths.Path> paths,
            int cardsOut,
            String lang) {
        return lines == null ? List.of() : lines;
    }

    static ObtainPaths.Path pickPrimaryPath(List<ObtainPaths.Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        for (ObtainPaths.Path path : paths) {
            if (path != null && path.complete()) {
                return path;
            }
        }
        return paths.get(0);
    }

    static int countAlternativePaths(List<ObtainPaths.Path> paths, ObtainPaths.Path primary) {
        if (paths == null || paths.isEmpty() || primary == null) {
            return 0;
        }
        int n = 0;
        for (ObtainPaths.Path path : paths) {
            if (path != null && path != primary) {
                n++;
            }
        }
        return n;
    }

    static String formatAltRouteCount(int altPaths) {
        if (altPaths <= 3) {
            return Integer.toString(altPaths);
        }
        return "3+" + (altPaths - 3);
    }

    static List<ObtainPathText.Line> linesForPath(
            List<ObtainPathText.Line> all, ObtainPaths.Path path) {
        if (all == null || all.isEmpty() || path == null || path.steps() == null) {
            return List.of();
        }
        LinkedHashSet<String> wanted = new LinkedHashSet<>();
        for (ObtainPaths.Step step : path.steps()) {
            if (step == null || step.edge() == null || step.edge().recipeId() == null) {
                continue;
            }
            wanted.add("R|" + step.edge().recipeId());
        }
        List<ObtainPathText.Line> ordered = new ArrayList<>(wanted.size() + 1);
        for (String key : wanted) {
            for (ObtainPathText.Line ln : all) {
                if (ln != null && key.equals(ln.key())) {
                    ordered.add(ln);
                    break;
                }
            }
        }
        if (!path.complete()) {
            for (ObtainPathText.Line ln : all) {
                if (ln != null && "M|partial".equals(ln.key())) {
                    ordered.add(ln);
                    break;
                }
            }
        }
        return List.copyOf(ordered);
    }

    static String mergeRepeatedInputsInLine(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher m = OBTAIN_STEP_BODY.matcher(text.trim());
        if (!m.matches()) {
            return text;
        }
        String merged = mergeConsecutiveInputTokens(m.group(2));
        return m.group(1) + merged + m.group(3);
    }

    static String mergeConsecutiveInputTokens(String inputs) {
        if (inputs == null || inputs.isBlank()) {
            return inputs == null ? "" : inputs;
        }
        String[] parts = inputs.split(", ");
        if (parts.length <= 1) {
            return inputs;
        }
        List<String> out = new ArrayList<>(parts.length);
        String prevLabel = null;
        int run = 0;
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            ParsedToken tok = parseInputToken(part);
            if (prevLabel != null && prevLabel.equals(tok.label)) {
                run += tok.count;
            } else {
                if (prevLabel != null) {
                    out.add(formatInputToken(prevLabel, run));
                }
                prevLabel = tok.label;
                run = tok.count;
            }
        }
        if (prevLabel != null) {
            out.add(formatInputToken(prevLabel, run));
        }
        return String.join(", ", out);
    }

    private static final class ParsedToken {
        final String label;
        final int count;

        ParsedToken(String label, int count) {
            this.label = label;
            this.count = count;
        }
    }

    static ParsedToken parseInputToken(String part) {
        String trimmed = part.trim();
        int multIdx = trimmed.lastIndexOf('×');
        if (multIdx < 0) {
            multIdx = trimmed.lastIndexOf('x');
        }
        if (multIdx > 0 && multIdx < trimmed.length() - 1) {
            String tail = trimmed.substring(multIdx + 1).trim();
            if (tail.matches("\\d+")) {
                return new ParsedToken(trimmed.substring(0, multIdx).trim(), Integer.parseInt(tail));
            }
        }
        return new ParsedToken(trimmed, 1);
    }

    static String formatInputToken(String label, int count) {
        if (count <= 1) {
            return label;
        }
        return label + "×" + count;
    }

    /**
     * Recipe ids for obtain lines covered by catalog cards: match card primary output to path steps.
     */
    public static Set<String> recipeIdsFromCards(
            List<RecipeCard> cards, List<ObtainPaths.Path> paths) {
        if (cards == null || cards.isEmpty() || paths == null || paths.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (RecipeCard card : cards) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            String wantOut = card.primaryOutputId();
            for (ObtainPaths.Path path : paths) {
                if (path == null || path.steps() == null) {
                    continue;
                }
                for (ObtainPaths.Step step : path.steps()) {
                    if (step == null || step.edge() == null || step.edge().recipeId() == null) {
                        continue;
                    }
                    Item outItem = step.edge().output();
                    String stepOut = outItem == null ? "" : ObtainGraph.itemKey(outItem);
                    if (!wantOut.isEmpty() && !wantOut.equalsIgnoreCase(stepOut)) {
                        continue;
                    }
                    out.add(step.edge().recipeId().toString());
                }
            }
        }
        return Set.copyOf(out);
    }

    public static List<String> lineTexts(List<ObtainPathText.Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (ObtainPathText.Line ln : lines) {
            if (ln != null && ln.text() != null && !ln.text().isBlank()) {
                out.add(ln.text());
            }
        }
        return out;
    }
}
