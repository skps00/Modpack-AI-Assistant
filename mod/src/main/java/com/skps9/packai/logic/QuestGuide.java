package com.skps9.packai.logic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** FTB Quests / Heracles file matching and guide text. */
public final class QuestGuide {
    public static final int MAX_HITS = 3;
    private static final Pattern OVERRIDE = Pattern.compile(
            "(任務書?\\s*(好像)?(不對|錯了|有誤|過時)|quest\\s*wrong|quest\\s*outdated|wrong\\s*quest)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TITLE = Pattern.compile("(?:title|Title)\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DESC = Pattern.compile("(?:description|Description)\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ITEM = Pattern.compile("\\b([a-z0-9_]+:[a-z0-9_./-]+)\\b", Pattern.CASE_INSENSITIVE);

    public record Hit(String chapter, String title, String description, String source, List<String> items, int score, boolean active) {}

    private QuestGuide() {}

    public static boolean isOverride(String question, boolean flag) {
        if (flag) {
            return true;
        }
        return question != null && OVERRIDE.matcher(question).find();
    }

    public static List<Hit> indexAndMatch(Path gameDir, List<String> scanners, String question, String heldItemId) {
        List<Hit> all = index(gameDir, scanners);
        return match(all, question, heldItemId);
    }

    public static List<Hit> index(Path gameDir, List<String> scanners) {
        List<Hit> hits = new ArrayList<>();
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            return hits;
        }
        List<Path> roots = new ArrayList<>();
        if (scanners.contains("ftbquests")) {
            roots.add(gameDir.resolve("config/ftbquests"));
        }
        if (scanners.contains("heracles")) {
            roots.add(gameDir.resolve("config/heracles"));
        }
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!(name.endsWith(".snbt") || name.endsWith(".json") || name.endsWith(".txt"))) {
                        return;
                    }
                    try {
                        if (Files.size(p) > 500_000) {
                            return;
                        }
                        String text = Files.readString(p, StandardCharsets.UTF_8);
                        hits.addAll(parseFile(gameDir, p, text));
                    } catch (IOException ignored) {
                        // skip unreadable
                    }
                });
            } catch (IOException ignored) {
                // skip
            }
        }
        return hits;
    }

    public static List<Hit> match(List<Hit> all, String question, String heldItemId) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String held = heldItemId == null ? "" : heldItemId.toLowerCase(Locale.ROOT);
        List<Hit> scored = new ArrayList<>();
        for (Hit h : all) {
            int score = 0;
            String blob = (h.chapter + " " + h.title + " " + h.description).toLowerCase(Locale.ROOT);
            if (!held.isEmpty() && h.items.stream().anyMatch(i -> i.equalsIgnoreCase(held))) {
                score += 10;
            }
            if (!held.isEmpty() && blob.contains(held)) {
                score += 6;
            }
            for (String tok : q.split("[^a-z0-9_\\u4e00-\\u9fff]+")) {
                if (tok.length() >= 2 && blob.contains(tok)) {
                    score += 2;
                }
            }
            if (score > 0) {
                scored.add(new Hit(h.chapter, h.title, h.description, h.source, h.items, score, false));
            }
        }
        scored.sort(Comparator.comparingInt(Hit::score).reversed().thenComparing(Hit::title));
        if (scored.size() > MAX_HITS) {
            return new ArrayList<>(scored.subList(0, MAX_HITS));
        }
        return scored;
    }

    public static boolean conflict(List<Hit> hits, Set<String> removedItems) {
        if (removedItems == null || removedItems.isEmpty()) {
            return false;
        }
        for (Hit h : hits) {
            for (String item : h.items) {
                if (removedItems.contains(item.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String formatGuide(List<Hit> hits, boolean conflict, String localPlain, int totalHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("【任務導引】這個整合包的任務書裡有相關內容。\n請打開任務書，查看：\n");
        int i = 1;
        for (Hit h : hits) {
            sb.append(i++).append(". 章節：").append(empty(h.chapter, "（未命名）"))
                    .append("　任務：").append(empty(h.title, "（未命名）")).append('\n');
            if (h.description != null && !h.description.isBlank()) {
                String d = h.description.length() > 120 ? h.description.substring(0, 120) + "…" : h.description;
                sb.append("   摘要：").append(d).append('\n');
            }
        }
        if (totalHint > hits.size()) {
            sb.append("還有其他相關任務，可在任務書搜尋關鍵字。\n");
        }
        sb.append("若只是卡住／看不懂，請先照任務說明做；不必另外查 wiki。\n");
        LinkedHashSet<String> src = new LinkedHashSet<>();
        hits.forEach(h -> src.add(h.source));
        sb.append("【來源】").append(String.join("、", src));
        if (conflict) {
            sb.append("\n【警告】任務書可能已過時（與本地魔改衝突），以下依整合包實際設定：\n");
            if (localPlain != null) {
                sb.append(localPlain);
            }
        }
        return sb.toString();
    }

    private static List<Hit> parseFile(Path gameDir, Path file, String text) {
        List<Hit> out = new ArrayList<>();
        Matcher tm = TITLE.matcher(text);
        List<String> titles = new ArrayList<>();
        while (tm.find()) {
            titles.add(tm.group(1));
        }
        Matcher dm = DESC.matcher(text);
        List<String> descs = new ArrayList<>();
        while (dm.find()) {
            descs.add(dm.group(1));
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        Matcher im = ITEM.matcher(text);
        while (im.find()) {
            items.add(im.group(1).toLowerCase(Locale.ROOT));
        }
        String rel;
        try {
            rel = gameDir.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            rel = file.getFileName().toString();
        }
        String chapter = file.getParent() == null ? "" : file.getParent().getFileName().toString();
        if (titles.isEmpty()) {
            if (items.isEmpty() && text.length() < 40) {
                return out;
            }
            titles.add(file.getFileName().toString().replaceFirst("\\.[^.]+$", ""));
        }
        int n = Math.min(20, titles.size());
        for (int i = 0; i < n; i++) {
            String desc = i < descs.size() ? descs.get(i) : (descs.isEmpty() ? "" : descs.get(0));
            out.add(new Hit(chapter, titles.get(i), desc, rel, new ArrayList<>(items), 0, false));
        }
        return out;
    }

    private static String empty(String s, String fb) {
        return s == null || s.isBlank() ? fb : s;
    }
}
