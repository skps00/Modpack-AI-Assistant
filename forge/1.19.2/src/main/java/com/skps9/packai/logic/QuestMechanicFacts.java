package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FTB quest title / description / tasks text → C-tier mechanic hints.
 * Clip only; never dump a whole SNBT into the prompt.
 */
public final class QuestMechanicFacts {
    public static final int MAX_FACTS_PER_ITEM = 5;
    public static final int MAX_CLIP = 300;
    public static final String DISCLAIMER = "任務描述（可能未涵蓋全部機制）";

    private static final Pattern ITEM_LIKE = Pattern.compile(
            "#?[a-z0-9_]+:[a-z0-9_./-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY = Pattern.compile(
            "\\b(description|title|tasks)\\s*:", Pattern.CASE_INSENSITIVE);

    private QuestMechanicFacts() {}

    /** Fixture parse — no disk. {@code relPath} like {@code quests/chapters/demo.snbt}. */
    public static List<String> factsForItem(String snbt, String relPath, String itemId) {
        return factsFrom(snbt, relPath, itemId, MAX_FACTS_PER_ITEM);
    }

    public static List<String> factsForItem(Path gameDir, String itemId) {
        if (itemId == null || itemId.isBlank() || gameDir == null) {
            return List.of();
        }
        Path root = gameDir.resolve("config").resolve("ftbquests").resolve("quests");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Path> files = new ArrayList<>();
        try {
            Files.walk(root).forEach(p -> {
                if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".snbt")) {
                    files.add(p);
                }
            });
        } catch (Exception e) {
            return List.of();
        }
        files.sort((a, b) -> a.toString().replace('\\', '/').compareTo(b.toString().replace('\\', '/')));
        String want = itemId.toLowerCase(Locale.ROOT);
        for (Path p : files) {
            if (out.size() >= MAX_FACTS_PER_ITEM) {
                break;
            }
            String rel;
            try {
                rel = "quests/" + root.relativize(p).toString().replace('\\', '/');
            } catch (Exception e) {
                rel = p.getFileName().toString();
            }
            String text;
            try {
                if (Files.size(p) > 4_000_000) {
                    continue;
                }
                text = Files.readString(p, StandardCharsets.UTF_8);
            } catch (Exception e) {
                continue;
            }
            if (!mentions(text, want)) {
                continue;
            }
            for (String fact : factsFrom(text, rel, want, MAX_FACTS_PER_ITEM - out.size())) {
                if (seen.add(fact)) {
                    out.add(fact);
                }
                if (out.size() >= MAX_FACTS_PER_ITEM) {
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    static List<String> factsFrom(String snbt, String relPath, String itemId, int cap) {
        if (snbt == null || itemId == null || itemId.isBlank() || cap <= 0) {
            return List.of();
        }
        String want = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Matcher m = KEY.matcher(snbt);
        while (m.find() && out.size() < cap) {
            int colon = m.end();
            int valueStart = skipWs(snbt, colon);
            if (valueStart >= snbt.length()) {
                continue;
            }
            String block = readValue(snbt, valueStart);
            if (block.isEmpty() || !mentions(block, want)) {
                continue;
            }
            String clip = KubeJsMechanicScan.clip(block.replace('\r', ' ').replace('\n', ' '), MAX_CLIP);
            String rel = relPath == null ? "quests/unknown.snbt" : relPath.replace('\\', '/');
            if (rel.startsWith("ftbquests/")) {
                rel = rel.substring("ftbquests/".length());
            }
            String fact = "item:" + want + " -[quest_text]-> " + DISCLAIMER + ": " + clip
                    + " (source:ftbquests/" + rel + " tier:C)";
            if (seen.add(fact)) {
                out.add(fact);
            }
        }
        return List.copyOf(out);
    }

    static boolean mentions(String text, String itemId) {
        if (text == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        String needle = itemId.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from <= t.length() - needle.length()) {
            int i = t.indexOf(needle, from);
            if (i < 0) {
                return false;
            }
            boolean left = i == 0 || !isIdChar(t.charAt(i - 1));
            int end = i + needle.length();
            boolean right = end >= t.length() || !isIdChar(t.charAt(end));
            if (left && right) {
                return true;
            }
            from = i + 1;
        }
        return false;
    }

    private static boolean isIdChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '/' || c == '.' || c == ':';
    }

    private static String readValue(String s, int from) {
        char c = s.charAt(from);
        if (c == '"' || c == '\'') {
            int end = KubeJsMechanicScan.skipQuoted(s, from, c);
            return unquote(s.substring(from, Math.min(s.length(), end)));
        }
        if (c == '[') {
            int end = matching(s, from, '[', ']');
            return collectStrings(s.substring(from, Math.min(s.length(), end + 1)));
        }
        if (c == '{') {
            int end = matching(s, from, '{', '}');
            return collectStrings(s.substring(from, Math.min(s.length(), end + 1)));
        }
        int i = from;
        while (i < s.length() && s.charAt(i) != '\n' && s.charAt(i) != ',') {
            i++;
        }
        return s.substring(from, i).trim();
    }

    private static String collectStrings(String block) {
        StringBuilder sb = new StringBuilder();
        Matcher ids = ITEM_LIKE.matcher(block);
        // Keep human text + ids. Pull quoted strings first.
        int i = 0;
        int n = block.length();
        while (i < n) {
            char c = block.charAt(i);
            if (c == '"' || c == '\'') {
                int end = KubeJsMechanicScan.skipQuoted(block, i, c);
                String q = unquote(block.substring(i, Math.min(n, end)));
                if (!q.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(q);
                }
                i = end;
                continue;
            }
            i++;
        }
        if (sb.length() == 0) {
            while (ids.find()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(ids.group());
            }
        }
        return sb.toString().trim();
    }

    private static String unquote(String raw) {
        String s = raw.trim();
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s.replace("\\\"", "\"").replace("\\n", " ").trim();
    }

    private static int matching(String s, int open, char openCh, char closeCh) {
        int depth = 0;
        int i = open;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                i = KubeJsMechanicScan.skipQuoted(s, i, c);
                continue;
            }
            if (c == openCh) {
                depth++;
            } else if (c == closeCh) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return n - 1;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }
}
