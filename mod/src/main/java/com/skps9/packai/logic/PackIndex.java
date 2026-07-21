package com.skps9.packai.logic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Path index + light pack graph + snippet retrieve. */
public final class PackIndex {
    private static final Set<String> EXTS = Set.of(".js", ".zs", ".groovy", ".json", ".snbt", ".txt", ".md", ".toml");
    private static final Pattern REMOVE = Pattern.compile(
            "event\\.remove\\(\\s*\\{([^}]*)\\}\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM = Pattern.compile("['\"]([a-z0-9_]+:[a-z0-9_./-]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHAPELESS = Pattern.compile(
            "event\\.shapeless\\(\\s*([^,\\n]+)\\s*,\\s*\\[([\\s\\S]*?)\\]\\s*\\)", Pattern.CASE_INSENSITIVE);

    private final List<String> paths = new ArrayList<>();
    private final Map<String, List<Integer>> inverted = new HashMap<>();
    private final Map<String, String> textCache = new HashMap<>();
    private final Set<String> removedItems = new HashSet<>();
    private final List<String> graphFacts = new ArrayList<>();
    private Path root;

    public void build(Path gameDir, List<String> scanners) {
        paths.clear();
        inverted.clear();
        textCache.clear();
        removedItems.clear();
        graphFacts.clear();
        this.root = gameDir;
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            return;
        }
        List<Path> roots = new ArrayList<>();
        if (scanners.contains("kubejs")) {
            roots.add(gameDir.resolve("kubejs"));
        }
        if (scanners.contains("crafttweaker")) {
            roots.add(gameDir.resolve("scripts"));
        }
        if (scanners.contains("groovyscript")) {
            roots.add(gameDir.resolve("groovy"));
        }
        if (scanners.contains("datapacks")) {
            roots.add(gameDir.resolve("datapacks"));
            roots.add(gameDir.resolve("global_packs"));
        }
        if (scanners.contains("ftbquests")) {
            roots.add(gameDir.resolve("config/ftbquests"));
        }
        if (scanners.contains("heracles")) {
            roots.add(gameDir.resolve("config/heracles"));
        }
        roots.add(gameDir.resolve("overrides"));

        for (Path r : roots) {
            if (!Files.isDirectory(r)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(r)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    boolean ok = EXTS.stream().anyMatch(name::endsWith);
                    if (!ok) {
                        return;
                    }
                    try {
                        if (Files.size(p) > 400_000) {
                            return;
                        }
                        String rel = gameDir.relativize(p).toString().replace('\\', '/');
                        String pl = rel.toLowerCase(Locale.ROOT);
                        if (QuestGuide.isRewardTablePath(pl, name)) {
                            return;
                        }
                        addPath(rel);
                    } catch (IOException ignored) {
                        // skip
                    }
                });
            } catch (IOException ignored) {
                // skip
            }
        }
    }

    public RetrieveResult retrieve(String question, String heldItemId, List<String> focusMods) {
        return retrieve(question, heldItemId, focusMods, List.of());
    }

    public RetrieveResult retrieve(
            String question,
            String heldItemId,
            List<String> focusMods,
            List<String> extraItemIds
    ) {
        List<String> tokens = tokenize(question, heldItemId);
        if (extraItemIds != null) {
            for (String id : extraItemIds) {
                for (String t : tokenize(null, id)) {
                    if (!tokens.contains(t)) {
                        tokens.add(t);
                    }
                }
            }
        }
        Set<Integer> cand = new HashSet<>();
        for (String t : tokens) {
            List<Integer> ids = inverted.get(t);
            if (ids != null) {
                cand.addAll(ids);
            }
        }
        for (String mid : focusMods) {
            List<Integer> ids = inverted.get(mid.toLowerCase(Locale.ROOT));
            if (ids != null) {
                cand.addAll(ids);
            }
        }
        if (cand.isEmpty()) {
            for (int i = 0; i < Math.min(paths.size(), 40); i++) {
                cand.add(i);
            }
        }

        record Scored(int score, int idx) {}
        List<Scored> scored = new ArrayList<>();
        for (int idx : cand) {
            String rel = paths.get(idx);
            String pl = rel.toLowerCase(Locale.ROOT);
            int score = 0;
            for (String t : tokens) {
                if (pl.contains(t)) {
                    score += 2;
                }
            }
            for (String mid : focusMods) {
                if (pl.contains(mid.toLowerCase(Locale.ROOT))) {
                    score += 3;
                }
            }
            if (score > 0 || tokens.isEmpty()) {
                scored.add(new Scored(score, idx));
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        List<String> snippets = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        int topScore = 0;
        int read = 0;
        for (Scored s : scored) {
            if (read >= 40) {
                break;
            }
            String rel = paths.get(s.idx);
            if (!pathMatchesFocus(rel, focusMods) && !focusMods.isEmpty()) {
                // still allow pack trees
                String pl = rel.toLowerCase(Locale.ROOT);
                if (!(pl.contains("/kubejs/") || pl.contains("ftbquests") || pl.contains("/overrides/"))) {
                    continue;
                }
            }
            String text = readText(rel);
            read++;
            if (text == null) {
                continue;
            }
            ingestGraph(rel, text);
            int score = s.score;
            String lower = text.toLowerCase(Locale.ROOT);
            for (String t : tokens) {
                if (lower.contains(t)) {
                    score += 3;
                }
            }
            if (score <= 0 && !tokens.isEmpty()) {
                continue;
            }
            if (snippets.size() < 3) {
                String clip = text.length() > 600 ? text.substring(0, 600) : text;
                snippets.add("// file: " + rel + "\n" + clip);
                sources.add(rel);
                topScore = Math.max(topScore, score);
            }
        }
        boolean high = topScore >= 12 && !snippets.isEmpty();
        return new RetrieveResult(snippets, sources, topScore, high, Set.copyOf(removedItems), List.copyOf(graphFacts));
    }

    public List<String> paths() {
        return List.copyOf(paths);
    }

    public boolean touchesFocus(List<String> focusMods, String heldItemId) {
        String held = heldItemId == null ? "" : heldItemId.toLowerCase(Locale.ROOT);
        for (String rel : paths) {
            String pl = rel.toLowerCase(Locale.ROOT);
            if (pl.contains("/kubejs/") || pl.contains("/scripts/") || pl.contains("/groovy/")) {
                for (String mid : focusMods) {
                    if (pl.contains(mid.toLowerCase(Locale.ROOT))) {
                        return true;
                    }
                }
                if (!held.isEmpty() && pl.contains(held.replace(':', '_'))) {
                    return true;
                }
            }
            if (pl.startsWith("datapacks/") || pl.startsWith("overrides/") || pl.startsWith("openloader/")) {
                for (String mid : focusMods) {
                    if (pl.contains("/" + mid.toLowerCase(Locale.ROOT) + "/")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void addPath(String rel) {
        int idx = paths.size();
        paths.add(rel);
        for (String tok : rel.toLowerCase(Locale.ROOT).split("[/._\\-]+")) {
            if (tok.length() > 1) {
                inverted.computeIfAbsent(tok, k -> new ArrayList<>()).add(idx);
            }
        }
    }

    private String readText(String rel) {
        if (textCache.containsKey(rel)) {
            return textCache.get(rel);
        }
        if (root == null) {
            return null;
        }
        try {
            String text = Files.readString(root.resolve(rel), StandardCharsets.UTF_8);
            textCache.put(rel, text);
            return text;
        } catch (IOException e) {
            return null;
        }
    }

    private void ingestGraph(String rel, String text) {
        Matcher rm = REMOVE.matcher(text);
        while (rm.find()) {
            Matcher im = ITEM.matcher(rm.group(1));
            while (im.find()) {
                String id = im.group(1).toLowerCase(Locale.ROOT);
                removedItems.add(id);
                graphFacts.add("item:" + id + " -[removed]-> true");
            }
        }
        Matcher sm = SHAPELESS.matcher(text);
        while (sm.find() && graphFacts.size() < 80) {
            Matcher outM = ITEM.matcher(sm.group(1));
            String out = outM.find() ? outM.group(1).toLowerCase(Locale.ROOT) : null;
            if (out == null) {
                continue;
            }
            Matcher im = ITEM.matcher(sm.group(2));
            while (im.find()) {
                String need = im.group(1).toLowerCase(Locale.ROOT);
                graphFacts.add("item:" + out + " -[recipe_needs]-> item:" + need);
            }
        }
    }

    private static boolean pathMatchesFocus(String rel, List<String> focusMods) {
        String pl = rel.toLowerCase(Locale.ROOT);
        if (pl.contains("/kubejs/") || pl.contains("/overrides/") || pl.contains("ftbquests") || pl.contains("readme")) {
            for (String mid : focusMods) {
                if (pl.contains(mid.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return pl.contains("/kubejs/"); // keep kubejs for later text score
        }
        for (String mid : focusMods) {
            if (pl.contains(mid.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return focusMods.isEmpty();
    }

    private static List<String> tokenize(String question, String held) {
        List<String> tokens = new ArrayList<>();
        if (question != null) {
            for (String t : question.toLowerCase(Locale.ROOT).split("[^a-z0-9_:./\\-\\u4e00-\\u9fff]+")) {
                if (t.length() >= 2) {
                    tokens.add(t);
                }
            }
        }
        if (held != null && held.contains(":")) {
            tokens.add(held.toLowerCase(Locale.ROOT));
            tokens.add(held.substring(0, held.indexOf(':')).toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    public record RetrieveResult(
            List<String> snippets,
            List<String> sources,
            int topScore,
            boolean highConfidence,
            Set<String> removedItems,
            List<String> graphFacts
    ) {}
}
