package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits assistant answer text around {@code {{RECIPE}}} / {@code {{RECIPE:n}}} markers
 * so the UI can interleave JEI recipe cards. Markers are never shown to the player.
 */
public final class RecipeEmbed {
    private static final Pattern MARKER = Pattern.compile(
            "\\{\\{\\s*RECIPE(?:\\s*:\\s*(\\d+))?\\s*\\}\\}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SOURCES = Pattern.compile("(?m)(【來源】|\\[Sources\\])");

    public enum Kind {
        TEXT,
        CARD
    }

    /** One display chunk: plain text or a recipe-card index into the message's card list. */
    public record Part(Kind kind, String text, int cardIndex) {
        public static Part text(String text) {
            return new Part(Kind.TEXT, text == null ? "" : text, -1);
        }

        public static Part card(int index) {
            return new Part(Kind.CARD, "", index);
        }

        public boolean isCard() {
            return kind == Kind.CARD;
        }
    }

    private RecipeEmbed() {}

    /**
     * Plan UI segments for {@code answer} given {@code cardCount} available cards (0-based).
     * No markers → cards after the first paragraph (and before 【來源】/[Sources] when present).
     */
    public static List<Part> parts(String answer, int cardCount) {
        String raw = answer == null ? "" : answer;
        if (cardCount <= 0) {
            String cleaned = stripMarkers(raw).trim();
            return cleaned.isEmpty() ? List.of() : List.of(Part.text(cleaned));
        }
        Matcher m = MARKER.matcher(raw);
        if (!m.find()) {
            return fallback(raw, cardCount);
        }
        return fromMarkers(raw, cardCount);
    }

    /** Remove all recipe markers from text. */
    public static String stripMarkers(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return MARKER.matcher(text).replaceAll("")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static List<Part> fromMarkers(String raw, int cardCount) {
        List<Part> out = new ArrayList<>();
        boolean[] used = new boolean[cardCount];
        int nextAuto = 0;
        Matcher m = MARKER.matcher(raw);
        int last = 0;
        while (m.find()) {
            String before = tidyChunk(raw.substring(last, m.start()), true, true);
            if (!before.isEmpty()) {
                out.add(Part.text(before));
            }
            int idx = -1;
            if (m.group(1) != null) {
                try {
                    idx = Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    idx = -1;
                }
            } else {
                while (nextAuto < cardCount && used[nextAuto]) {
                    nextAuto++;
                }
                if (nextAuto < cardCount) {
                    idx = nextAuto++;
                }
            }
            if (idx >= 0 && idx < cardCount && !used[idx]) {
                used[idx] = true;
                out.add(Part.card(idx));
            }
            last = m.end();
        }
        String tail = tidyChunk(raw.substring(last), true, true);
        if (!tail.isEmpty()) {
            out.add(Part.text(tail));
        }
        appendUnused(out, used);
        return mergeAdjacentText(out);
    }

    private static List<Part> fallback(String raw, int cardCount) {
        int sourcesAt = indexOfSources(raw);
        String main = sourcesAt >= 0 ? raw.substring(0, sourcesAt) : raw;
        String sources = sourcesAt >= 0 ? raw.substring(sourcesAt) : "";
        int split = firstParagraphEnd(main);
        String before = tidyChunk(main.substring(0, split), true, true);
        String after = tidyChunk(main.substring(split), true, true);
        List<Part> out = new ArrayList<>();
        if (!before.isEmpty()) {
            out.add(Part.text(before));
        }
        for (int i = 0; i < cardCount; i++) {
            out.add(Part.card(i));
        }
        StringBuilder rest = new StringBuilder();
        if (!after.isEmpty()) {
            rest.append(after);
        }
        if (!sources.isEmpty()) {
            if (!rest.isEmpty()) {
                rest.append("\n\n");
            }
            rest.append(sources.trim());
        }
        if (!rest.isEmpty()) {
            out.add(Part.text(rest.toString()));
        }
        return out;
    }

    private static void appendUnused(List<Part> out, boolean[] used) {
        List<Integer> unused = new ArrayList<>();
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                unused.add(i);
            }
        }
        if (unused.isEmpty()) {
            return;
        }
        if (!out.isEmpty() && !out.get(out.size() - 1).isCard()) {
            Part last = out.remove(out.size() - 1);
            int src = indexOfSources(last.text());
            if (src >= 0) {
                String before = tidyChunk(last.text().substring(0, src), true, true);
                String after = last.text().substring(src).trim();
                if (!before.isEmpty()) {
                    out.add(Part.text(before));
                }
                for (int i : unused) {
                    out.add(Part.card(i));
                }
                if (!after.isEmpty()) {
                    out.add(Part.text(after));
                }
                return;
            }
            out.add(last);
        }
        for (int i : unused) {
            out.add(Part.card(i));
        }
    }

    private static List<Part> mergeAdjacentText(List<Part> parts) {
        List<Part> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (Part p : parts) {
            if (p.isCard()) {
                flushText(out, buf);
                out.add(p);
            } else if (p.text() != null && !p.text().isEmpty()) {
                if (!buf.isEmpty()) {
                    buf.append('\n');
                }
                buf.append(p.text());
            }
        }
        flushText(out, buf);
        return out;
    }

    private static void flushText(List<Part> out, StringBuilder buf) {
        if (buf.isEmpty()) {
            return;
        }
        out.add(Part.text(buf.toString().trim()));
        buf.setLength(0);
    }

    private static int firstParagraphEnd(String main) {
        if (main == null || main.isEmpty()) {
            return 0;
        }
        int nn = main.indexOf("\n\n");
        if (nn >= 0) {
            return nn;
        }
        int n = main.indexOf('\n');
        if (n >= 0) {
            return n;
        }
        return main.length();
    }

    private static int indexOfSources(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        Matcher m = SOURCES.matcher(text);
        return m.find() ? m.start() : -1;
    }

    private static String tidyChunk(String s, boolean trimStart, boolean trimEnd) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String t = s.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        if (trimStart) {
            t = t.replaceAll("^\\s+", "");
        }
        if (trimEnd) {
            t = t.replaceAll("\\s+$", "");
        }
        return t;
    }
}
