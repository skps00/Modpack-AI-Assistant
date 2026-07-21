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
    private static final Pattern ID = Pattern.compile("(?:^|\\n)\\s*id:\\s*\"([^\"]+)\"", Pattern.MULTILINE);

    /**
     * @param questId FTB/Heracles id for open_book (may be blank)
     * @param system  ftbquests | heracles
     */
    public record Hit(
            String chapter,
            String title,
            String description,
            String source,
            List<String> items,
            int score,
            boolean active,
            String questId,
            String system
    ) {
        Hit withScore(int s) {
            return new Hit(chapter, title, description, source, items, s, active, questId, system);
        }
    }

    private QuestGuide() {}

    public static boolean isOverride(String question, boolean flag) {
        if (flag) {
            return true;
        }
        return question != null && OVERRIDE.matcher(question).find();
    }

    public static List<Hit> indexAndMatch(Path gameDir, List<String> scanners, String question, String heldItemId) {
        return match(index(gameDir, scanners), question, heldItemId);
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
                    // Skip FTB reward tables — not player-facing quests
                    String pathLower = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                    if (isRewardTablePath(pathLower, name)) {
                        return;
                    }
                    try {
                        if (Files.size(p) > 500_000) {
                            return;
                        }
                        String text = Files.readString(p, StandardCharsets.UTF_8);
                        hits.addAll(parseFile(gameDir, p, text));
                    } catch (IOException ignored) {
                        // skip
                    }
                });
            } catch (IOException ignored) {
                // skip
            }
        }
        return hits;
    }

    /** FTB Quests reward_tables — ignore for guide & open buttons. */
    static boolean isRewardTablePath(String pathLower, String fileNameLower) {
        return pathLower.contains("/reward_tables/")
                || pathLower.contains("/reward_table/")
                || pathLower.contains("\\reward_tables\\")
                || pathLower.contains("\\reward_table\\")
                || fileNameLower.contains("reward_table");
    }

    public static List<Hit> match(List<Hit> all, String question, String heldItemId) {
        return matchResult(all, question, heldItemId, List.of()).hits();
    }

    public record MatchResult(List<Hit> hits, int totalMatched) {}

    public static MatchResult matchResult(
            List<Hit> all,
            String question,
            String heldItemId,
            List<String> extraItemIds
    ) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        String held = heldItemId == null ? "" : heldItemId.toLowerCase(Locale.ROOT);
        List<String> extras = extraItemIds == null ? List.of() : extraItemIds;
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
            for (String extra : extras) {
                if (extra == null || extra.isBlank()) {
                    continue;
                }
                String el = extra.toLowerCase(Locale.ROOT);
                if (h.items.stream().anyMatch(i -> i.equalsIgnoreCase(el))) {
                    score += 8;
                } else if (blob.contains(el) || blob.contains(el.replace(':', '_'))) {
                    score += 3;
                }
            }
            for (String tok : q.split("[^a-z0-9_\\u4e00-\\u9fff]+")) {
                if (tok.length() >= 2 && blob.contains(tok)) {
                    score += 2;
                }
            }
            if (score > 0) {
                scored.add(h.withScore(score));
            }
        }
        scored.sort(Comparator.comparingInt(Hit::score).reversed().thenComparing(Hit::title));
        int total = scored.size();
        List<Hit> top = total > MAX_HITS ? new ArrayList<>(scored.subList(0, MAX_HITS)) : scored;
        return new MatchResult(top, total);
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
        return formatGuide(hits, conflict, localPlain, totalHint, false);
    }

    /**
     * @param rich fuller plain-language description (no ids / paths)
     */
    public static String formatGuide(List<Hit> hits, boolean conflict, String localPlain, int totalHint, boolean rich) {
        StringBuilder sb = new StringBuilder();
        sb.append(rich
                ? "【任務內容】根據整合包任務書：\n"
                : "【任務導引】任務書裡有相關內容。可點下方按鈕開啟任務：\n");
        int i = 1;
        int descCap = rich ? 400 : 120;
        for (Hit h : hits) {
            String chapter = Plainify.humanizeText(empty(h.chapter, "（未命名）"));
            String title = Plainify.humanizeText(empty(h.title, "（未命名）"));
            sb.append(i++).append(". 章節：").append(chapter)
                    .append("　任務：").append(title).append('\n');
            if (h.description != null && !h.description.isBlank()) {
                String d = Plainify.humanizeText(h.description);
                if (d.length() > descCap) {
                    d = d.substring(0, descCap) + "…";
                }
                if (!d.isBlank()) {
                    sb.append("   說明：").append(d).append('\n');
                }
            } else if (rich) {
                sb.append("   說明：請點下方按鈕在任務書中查看。\n");
            }
            if (rich && h.items != null && !h.items.isEmpty()) {
                int n = Math.min(6, h.items.size());
                sb.append("   可能需要：");
                for (int j = 0; j < n; j++) {
                    if (j > 0) {
                        sb.append("、");
                    }
                    sb.append("「").append(Plainify.displayName(h.items.get(j))).append("」");
                }
                if (h.items.size() > n) {
                    sb.append("等");
                }
                sb.append('\n');
            }
        }
        if (totalHint > hits.size()) {
            sb.append("還有其他相關任務，可在任務書裡搜尋。\n");
        }
        if (!rich) {
            sb.append("若只是卡住／看不懂，請先照任務說明做。\n");
        }
        sb.append("【來源】整合包任務書");
        if (conflict) {
            sb.append("\n【警告】任務內容可能已過時，以下依整合包實際配方：\n");
            if (localPlain != null) {
                sb.append(localPlain);
            }
        }
        return sb.toString();
    }

    public static List<Hit> matchForOffline(List<Hit> all, String question, String heldItemId) {
        return matchForOfflineResult(all, question, heldItemId, List.of()).hits();
    }

    public static MatchResult matchForOfflineResult(
            List<Hit> all,
            String question,
            String heldItemId,
            List<String> extraItemIds
    ) {
        MatchResult scored = matchResult(all, question, heldItemId, extraItemIds);
        if (!scored.hits().isEmpty()) {
            return scored;
        }
        List<String> bag = new ArrayList<>();
        if (heldItemId != null && !heldItemId.isBlank()) {
            bag.add(heldItemId);
        }
        if (extraItemIds != null) {
            bag.addAll(extraItemIds);
        }
        if (!bag.isEmpty()) {
            List<Hit> byHeld = new ArrayList<>();
            for (Hit h : all) {
                int score = 0;
                String blob = (h.chapter + " " + h.title + " " + h.description).toLowerCase(Locale.ROOT);
                for (String item : bag) {
                    if (item == null || item.isBlank()) {
                        continue;
                    }
                    String el = item.toLowerCase(Locale.ROOT);
                    boolean itemHit = h.items.stream().anyMatch(i -> i.equalsIgnoreCase(el));
                    if (itemHit) {
                        score += 5;
                    } else if (blob.contains(el) || blob.contains(el.replace(':', '_'))) {
                        score += 2;
                    }
                }
                if (score > 0) {
                    byHeld.add(h.withScore(score));
                }
            }
            byHeld.sort(Comparator.comparingInt(Hit::score).reversed());
            int total = byHeld.size();
            List<Hit> top = total > MAX_HITS ? new ArrayList<>(byHeld.subList(0, MAX_HITS)) : byHeld;
            if (!top.isEmpty()) {
                return new MatchResult(top, total);
            }
        }
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean questAsk = q.contains("任務") || q.contains("quest") || q.contains("下一步");
        if (questAsk && !all.isEmpty()) {
            int n = Math.min(MAX_HITS, all.size());
            List<Hit> sample = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                sample.add(all.get(i).withScore(1));
            }
            return new MatchResult(sample, all.size());
        }
        return new MatchResult(List.of(), 0);
    }

    private static List<Hit> parseFile(Path gameDir, Path file, String text) {
        List<Hit> out = new ArrayList<>();
        String rel;
        try {
            rel = gameDir.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            rel = file.getFileName().toString();
        }
        String system = rel.toLowerCase(Locale.ROOT).contains("heracles") ? "heracles" : "ftbquests";
        String chapter = file.getParent() == null ? "" : file.getParent().getFileName().toString();
        String fileStem = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");

        record PosTitle(int start, String title) {}
        List<PosTitle> titles = new ArrayList<>();
        Matcher tm = TITLE.matcher(text);
        while (tm.find()) {
            titles.add(new PosTitle(tm.start(), tm.group(1)));
        }

        List<String> descs = new ArrayList<>();
        Matcher dm = DESC.matcher(text);
        while (dm.find()) {
            descs.add(dm.group(1));
        }

        if (titles.isEmpty()) {
            LinkedHashSet<String> items = itemsInRange(text, 0, text.length());
            if (items.isEmpty() && text.length() < 40) {
                return out;
            }
            String id = nearestId(text, text.length());
            if (id.isEmpty()) {
                id = fileStem;
            }
            out.add(new Hit(chapter, fileStem, descs.isEmpty() ? "" : descs.get(0),
                    rel, new ArrayList<>(items), 0, false, id, system));
            return out;
        }

        int n = Math.min(20, titles.size());
        for (int i = 0; i < n; i++) {
            PosTitle pt = titles.get(i);
            int sliceStart = Math.max(0, pt.start() - 120);
            int sliceEnd = i + 1 < titles.size() ? titles.get(i + 1).start() : text.length();
            String slice = text.substring(sliceStart, Math.min(sliceEnd, text.length()));
            String desc = i < descs.size() ? descs.get(i) : "";
            String id = nearestId(slice, Math.min(pt.start() - sliceStart + 1, slice.length()));
            if (id.isEmpty()) {
                id = nearestId(text, pt.start());
            }
            if (id.isEmpty() && n == 1) {
                id = fileStem;
            }
            List<String> itemList = new ArrayList<>(itemsInRange(text, pt.start(), sliceEnd));
            out.add(new Hit(chapter, pt.title(), desc, rel, itemList, 0, false, id, system));
        }
        return out;
    }

    private static LinkedHashSet<String> itemsInRange(String text, int start, int end) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        int a = Math.max(0, start);
        int b = Math.min(text.length(), Math.max(a, end));
        Matcher im = ITEM.matcher(text.substring(a, b));
        while (im.find()) {
            items.add(im.group(1).toLowerCase(Locale.ROOT));
        }
        return items;
    }

    /** Nearest {@code id: "..."} appearing before {@code beforePos}. */
    private static String nearestId(String text, int beforePos) {
        int limit = Math.min(Math.max(0, beforePos), text.length());
        String head = text.substring(0, limit);
        Matcher m = ID.matcher(head);
        String last = "";
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }

    private static String empty(String s, String fb) {
        return s == null || s.isBlank() ? fb : s;
    }
}
