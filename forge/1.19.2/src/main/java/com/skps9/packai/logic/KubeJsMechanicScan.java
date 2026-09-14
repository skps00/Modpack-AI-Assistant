package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Pack-local KubeJS mechanic facts (use / conditional drops). Never a shared library.
 * Parse is Minecraft-free so {@code -ea} checks can run on fixture strings.
 */
public final class KubeJsMechanicScan {
    public static final String NONE_MARK = "mechanic:none";
    public static final int MAX_FACTS_PER_ITEM = 8;
    public static final int MAX_CLIP = 200;
    public static final int DEFAULT_CACHE_FILES = 500;
    public static final int DEFAULT_CACHE_MB = 5;
    /** Exact cap key the python check looks for. */
    static final String CURIOS_CAP = "ForgeCaps['curios:inventory']";
    /** Old KubeJS onEvent( call. Chance: Math.random() * Math.random() * 100 &lt;= n. */
    static final String ON_EVENT_CALL = "onEvent(";

    static final String[] SCRIPT_FOLDERS = {
            "startup_scripts", "server_scripts", "client_scripts"
    };
    private static final Set<String> FAMILIES = Set.of(
            "ItemEvents", "BlockEvents", "EntityEvents", "PlayerEvents", "ServerEvents");
    private static final Map<String, Set<String>> WHITELIST = whitelist();
    private static final Map<String, String> OLD_EVENTS = oldEvents();
    private static final Set<String> SKIP_PREFIX = Set.of("ForgeEvents", "ForgeModEvents", "LootJS");
    // ponytail: ForgeEvents / LootJS skipped (LootJS already in PackIndex). Ceiling = miss
    // mechanics that only live on ForgeEvents.onEvent; upgrade = opt-in class-name whitelist.

    private static final Pattern ITEM_LIKE = Pattern.compile(
            "#?[a-z0-9_]+:[a-z0-9_./-]+", Pattern.CASE_INSENSITIVE);
    /** let/var/const x = Math.random()*Math.random()*100  then x <= N (maybe later line). */
    private static final Pattern CHANCE_VAR_DOUBLE = Pattern.compile(
            "(?:let|var|const)\\s+(\\w+)\\s*=\\s*Math\\.random\\s*\\(\\s*\\)\\s*\\*\\s*Math\\.random\\s*\\(\\s*\\)\\s*\\*\\s*100(?!\\d)");
    /** x = Math.random()*100  then x <= N or x < N. */
    private static final Pattern CHANCE_VAR_SINGLE = Pattern.compile(
            "(\\w+)\\s*=\\s*Math\\.random\\s*\\(\\s*\\)\\s*\\*\\s*100(?!\\d)");
    /** Math.random()*100 < N → <N% (0–100 scale). */
    private static final Pattern CHANCE_PCT100_LT = Pattern.compile(
            "Math\\.random\\s*\\(\\s*\\)\\s*\\*\\s*100(?!\\d)\\s*<\\s*(\\d+(?:\\.\\d+)?)");
    /** Math.random() < 0.0N / 1 → convert 0–1 scale to percent. */
    private static final Pattern CHANCE_LT_UNIT = Pattern.compile(
            "Math\\.random\\s*\\(\\s*\\)\\s*<\\s*(0(?:\\.\\d+)?|1(?:\\.0+)?)");
    private static final Pattern CHANCE_DOUBLE = Pattern.compile(
            "Math\\.random\\s*\\(\\s*\\)\\s*\\*\\s*Math\\.random\\s*\\(\\s*\\)\\s*\\*\\s*100\\s*<=\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern CHANCE_LT = Pattern.compile(
            "Math\\.random\\s*\\(\\s*\\)\\s*<\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern CHANCE_PCT = Pattern.compile("(\\d+(?:\\.\\d+)?)%");
    private static final Pattern CHANCE_FIELD = Pattern.compile(
            "\\b(?:chance|weight)\\s*[:=]\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern TEXT_CALL = Pattern.compile(
            "(?:Text|Component)\\.\\w+\\s*\\(\\s*['\"]([^'\"]{1,200})['\"]");
    private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]{1,240})['\"]");
    private static final Pattern INCLUDES_ID = Pattern.compile(
            "\\.includes\\s*\\(\\s*['\"](#?[a-z0-9_]+:[a-z0-9_./-]+)['\"]",
            Pattern.CASE_INSENSITIVE);

    private static final LinkedHashSet<String> UNKNOWN_EVENTS = new LinkedHashSet<>();
    private static final AtomicBoolean CACHE_WARNED = new AtomicBoolean(false);
    private static final int MAX_SCRIPT_BYTES = 2_000_000;

    private KubeJsMechanicScan() {}

    public static List<String> unknownEvents() {
        synchronized (UNKNOWN_EVENTS) {
            return List.copyOf(UNKNOWN_EVENTS);
        }
    }

    static void resetUnknown() {
        synchronized (UNKNOWN_EVENTS) {
            UNKNOWN_EVENTS.clear();
        }
    }

    /** Fixture parse — no disk. {@code relPath} like {@code server_scripts/foo.js}. */
    public static List<String> factsForItem(String source, String relPath, String itemId) {
        return factsFrom(parseHandlers(source), relPath, itemId, MAX_FACTS_PER_ITEM);
    }

    /** Live pack scan. Cache under {@code config/packai/mechanic-cache/}. Soft-fail. */
    public static List<String> factsForItem(Path gameDir, String itemId) {
        return factsForItem(gameDir, itemId, DEFAULT_CACHE_FILES, DEFAULT_CACHE_MB);
    }

    public static List<String> factsForItem(Path gameDir, String itemId, int maxFiles, int maxMb) {
        if (itemId == null || itemId.isBlank() || gameDir == null) {
            return List.of();
        }
        List<Handler> all = new ArrayList<>();
        Path kube = gameDir.resolve("kubejs");
        if (!Files.isDirectory(kube)) {
            return List.of();
        }
        Path cacheDir = gameDir.resolve("config").resolve("packai").resolve("mechanic-cache");
        for (String folder : SCRIPT_FOLDERS) {
            Path root = kube.resolve(folder);
            if (!Files.isDirectory(root)) {
                continue;
            }
            List<Path> files = new ArrayList<>();
            try {
                Files.walk(root).forEach(p -> {
                    if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".js")) {
                        files.add(p);
                    }
                });
            } catch (Exception ignored) {
                continue;
            }
            files.sort(Comparator.comparing(p -> p.toString().replace('\\', '/')));
            for (Path p : files) {
                String rel = "kubejs/" + folder + "/" + relUnder(root, p);
                all.addAll(loadHandlers(p, rel, cacheDir, maxFiles, maxMb));
            }
        }
        return factsFrom(all, "", itemId, MAX_FACTS_PER_ITEM);
    }

    public static List<String> honestMerge(List<String> kjs, List<String> quest) {
        List<String> out = new ArrayList<>();
        if (kjs != null) {
            out.addAll(kjs);
        }
        if (quest != null) {
            out.addAll(quest);
        }
        if (out.isEmpty()) {
            out.add(NONE_MARK);
        }
        return List.copyOf(out);
    }

    static List<Handler> parseHandlers(String source) {
        List<Handler> out = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n) {
                char n1 = source.charAt(i + 1);
                if (n1 == '/') {
                    i = skipLineComment(source, i);
                    continue;
                }
                if (n1 == '*') {
                    i = skipBlockComment(source, i);
                    continue;
                }
            }
            if (c == '\'' || c == '"') {
                i = skipQuoted(source, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(source, i);
                continue;
            }
            if (isIdentStart(c) && (i == 0 || !isIdentPart(source.charAt(i - 1)))) {
                int start = i;
                String ident = readIdent(source, i);
                i += ident.length();
                if (SKIP_PREFIX.contains(ident)) {
                    i = skipAfterPrefix(source, i);
                    continue;
                }
                if (FAMILIES.contains(ident) && i < n && source.charAt(i) == '.') {
                    i++;
                    String method = readIdent(source, i);
                    i += method.length();
                    i = skipWs(source, i);
                    if (i < n && source.charAt(i) == '(') {
                        Handler h = parseCall(source, start, ident + "." + method, ident, method, i);
                        if (h != null) {
                            out.add(h);
                        }
                        int close = matchingParen(source, i);
                        i = Math.min(n, close + 1);
                        continue;
                    }
                    continue;
                }
                if (source.startsWith(ON_EVENT_CALL, start)) {
                    int open = start + ON_EVENT_CALL.length() - 1;
                    Handler h = parseOldCall(source, start, open);
                    if (h != null) {
                        out.add(h);
                    }
                    int close = matchingParen(source, open);
                    i = Math.min(n, close + 1);
                    continue;
                }
                continue;
            }
            i++;
        }
        return out;
    }

    private static Handler parseCall(
            String src, int start, String event, String family, String method, int openParen
    ) {
        boolean known = isWhitelisted(family, method);
        if (!known) {
            noteUnknown(event);
        }
        int close = matchingParen(src, openParen);
        if (close <= openParen) {
            return null;
        }
        int arrow = findArrow(src, openParen, close);
        int bodyOpen = -1;
        if (arrow >= 0) {
            bodyOpen = indexOfCodeChar(src, '{', arrow + 2, close);
        }
        if (bodyOpen < 0) {
            int fn = indexOfIdent(src, "function", openParen + 1, close);
            if (fn >= 0) {
                bodyOpen = indexOfCodeChar(src, '{', fn, close);
            }
        }
        if (bodyOpen < 0) {
            return null;
        }
        int bodyClose = matchingBrace(src, bodyOpen);
        String body = src.substring(bodyOpen, Math.min(src.length(), bodyClose + 1));
        String filter = src.substring(openParen + 1, arrow >= 0 ? arrow : bodyOpen);
        Handler h = new Handler();
        h.event = event;
        h.line = lineAt(src, start);
        h.known = known;
        fillFilter(h, family, filter);
        fillBody(h, body);
        return h;
    }

    private static Handler parseOldCall(String src, int start, int openParen) {
        int close = matchingParen(src, openParen);
        if (close <= openParen) {
            return null;
        }
        String first = firstQuoted(src, openParen + 1, close);
        if (first == null || first.isBlank()) {
            return null;
        }
        String key = first.toLowerCase(Locale.ROOT);
        String event = OLD_EVENTS.get(key);
        boolean known = event != null;
        if (!known) {
            if (key.startsWith("server.") && key.contains("loot")) {
                event = "ServerEvents.entityLootTables";
                known = true;
            } else {
                event = "onEvent:" + first;
                noteUnknown(first);
            }
        }
        String family = event.startsWith("onEvent:") ? "" : event.split("\\.", 2)[0];
        int arrow = findArrow(src, openParen, close);
        int bodyOpen = arrow >= 0 ? indexOfCodeChar(src, '{', arrow + 2, close) : -1;
        if (bodyOpen < 0) {
            return null;
        }
        int bodyClose = matchingBrace(src, bodyOpen);
        Handler h = new Handler();
        h.event = event;
        h.line = lineAt(src, start);
        h.known = known;
        String filter = src.substring(openParen + 1, arrow >= 0 ? arrow : bodyOpen);
        fillFilter(h, family, filter);
        fillBody(h, src.substring(bodyOpen, Math.min(src.length(), bodyClose + 1)));
        return h;
    }

    private static void fillFilter(Handler h, String family, String filter) {
        List<String> ids = quotedIds(filter);
        // ponytail: `#tag` kept as the tag id; not expanded to member items (no registry here).
        if ("ItemEvents".equals(family)) {
            h.filterIds.addAll(ids);
        } else if ("BlockEvents".equals(family)) {
            if (!ids.isEmpty()) {
                h.blockFilter = ids.get(0);
            }
            h.filterIds.addAll(ids);
        } else if ("EntityEvents".equals(family)) {
            if (!ids.isEmpty()) {
                h.entityFilter = ids.get(0);
            }
            h.filterIds.addAll(ids);
        } else {
            h.filterIds.addAll(ids);
        }
        h.mentioned.addAll(ids);
    }

    private static void fillBody(Handler h, String body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        for (String id : quotedIds(body)) {
            if (!h.mentioned.contains(id)) {
                h.mentioned.add(id);
            }
        }
        addCond(h, body, "mainHandItem");
        addCond(h, body, "offHandItem");
        if (body.contains(".hasEffect(")) {
            h.conditions.add(clip("hasEffect", 80));
        }
        if (body.contains("ticksUsingItem")) {
            h.conditions.add("ticksUsingItem");
        }
        boolean curiosCap = body.contains(CURIOS_CAP);
        boolean curiosWord = body.toLowerCase(Locale.ROOT).contains("curios");
        if (curiosCap || curiosWord) {
            Matcher inc = INCLUDES_ID.matcher(body);
            boolean any = false;
            while (inc.find()) {
                h.conditions.add("curios:" + inc.group(1).toLowerCase(Locale.ROOT));
                any = true;
            }
            if (!any) {
                String id = nearbyItem(body, Math.max(0, body.toLowerCase(Locale.ROOT).indexOf("curios")));
                h.conditions.add(id == null ? "curios" : "curios:" + id);
            }
        }
        addFieldCond(h, body, "entity.type", "entity.type");
        addFieldCond(h, body, "block.id", "block.id");
        addFieldCond(h, body, "dimension", "dimension");
        harvestCall(h, body, "give", true);
        harvestCall(h, body, "drop", true);
        harvestCall(h, body, "addEffect", false);
        harvestCall(h, body, "openMenu", false);
        harvestCall(h, body, "runCommand", false);
        harvestCall(h, body, "spawn", false);
        harvestCall(h, body, "tell", false);
        if (h.giveIds.isEmpty() && body.contains(".give(")) {
            for (String id : quotedIds(body)) {
                if (id.startsWith("#") || h.filterIds.contains(id)) {
                    continue;
                }
                if (looksLikeItem(id)) {
                    h.giveIds.add(id);
                    h.effects.add("give:" + id);
                }
            }
        }
        h.chance = extractChance(body);
        h.note = extractNote(body);
    }

    private static void harvestCall(Handler h, String body, String name, boolean itemish) {
        int from = 0;
        String needle = "." + name + "(";
        while (from < body.length()) {
            int at = indexOfCode(body, needle, from);
            if (at < 0) {
                at = indexOfCode(body, name + "(", from);
                if (at < 0 || (at > 0 && isIdentPart(body.charAt(at - 1)))) {
                    break;
                }
            }
            int open = body.indexOf('(', at);
            if (open < 0) {
                break;
            }
            int close = matchingParen(body, open);
            String args = body.substring(open, Math.min(body.length(), close + 1));
            List<String> ids = quotedIds(args);
            if (itemish) {
                for (String id : ids) {
                    if (looksLikeItem(id) && !h.giveIds.contains(id)) {
                        h.giveIds.add(id);
                    }
                }
            }
            String extra = ids.isEmpty() ? "" : ":" + String.join(",", ids);
            String eff = name + extra;
            if (!h.effects.contains(eff)) {
                h.effects.add(clip(eff, 120));
            }
            from = close + 1;
        }
    }

    private static String extractChance(String body) {
        Matcher m = CHANCE_VAR_DOUBLE.matcher(body);
        if (m.find()) {
            String n = namedCmp(body, m.group(1), "<=");
            if (n != null) {
                return "<=" + n;
            }
        }
        m = CHANCE_VAR_SINGLE.matcher(body);
        if (m.find()) {
            String var = m.group(1);
            String n = namedCmp(body, var, "<=");
            if (n != null) {
                return "<=" + n;
            }
            n = namedCmp(body, var, "<");
            if (n != null) {
                return "<" + n + "%";
            }
        }
        m = CHANCE_PCT100_LT.matcher(body);
        if (m.find()) {
            return "<" + m.group(1) + "%";
        }
        m = CHANCE_LT_UNIT.matcher(body);
        if (m.find()) {
            return unitLtPercent(m.group(1));
        }
        m = CHANCE_DOUBLE.matcher(body);
        if (m.find()) {
            return "<=" + m.group(1);
        }
        m = CHANCE_LT.matcher(body);
        if (m.find()) {
            return "<" + m.group(1);
        }
        m = CHANCE_FIELD.matcher(body);
        if (m.find()) {
            return m.group(1);
        }
        m = CHANCE_PCT.matcher(body);
        if (m.find()) {
            return m.group(1) + "%";
        }
        return "";
    }

    /** ponytail: per-handler Pattern.compile; ceiling = huge bodies. Upgrade = precompile op templates. */
    private static String namedCmp(String body, String var, String op) {
        Matcher m = Pattern.compile(
                "\\b" + Pattern.quote(var) + "\\s*" + Pattern.quote(op) + "\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static String unitLtPercent(String raw) {
        double pct = Double.parseDouble(raw) * 100.0;
        long rounded = Math.round(pct);
        if (Math.abs(pct - rounded) < 1e-6) {
            return "<" + rounded + "%";
        }
        return "<" + pct + "%";
    }

    private static String extractNote(String body) {
        List<String> notes = new ArrayList<>();
        Matcher t = TEXT_CALL.matcher(body);
        while (t.find()) {
            notes.add(t.group(1).trim());
        }
        if (notes.isEmpty()) {
            Matcher q = QUOTED.matcher(body);
            List<String> plain = new ArrayList<>();
            while (q.find()) {
                String s = q.group(1).trim();
                if (s.length() < 8 || looksLikeItem(s) || s.startsWith("#")) {
                    continue;
                }
                plain.add(s);
            }
            plain.sort((a, b) -> Integer.compare(b.length(), a.length()));
            for (int i = 0; i < plain.size() && i < 2; i++) {
                notes.add(plain.get(i));
            }
        }
        if (notes.isEmpty()) {
            return "";
        }
        return clip(String.join(" | ", notes.subList(0, Math.min(2, notes.size()))), MAX_CLIP);
    }

    static List<String> factsFrom(List<Handler> handlers, String relPath, String itemId, int cap) {
        if (itemId == null || itemId.isBlank() || handlers == null) {
            return List.of();
        }
        String want = itemId.toLowerCase(Locale.ROOT).trim();
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Handler h : handlers) {
            if (!handlerHits(h, want)) {
                continue;
            }
            String rel = h.rel != null && !h.rel.isBlank() ? h.rel : relPath;
            String src = sourceTag(rel, h.line);
            String fam = familyOf(h.event);
            boolean useEvent = "ItemEvents".equals(fam)
                    || "BlockEvents".equals(fam)
                    || "PlayerEvents".equals(fam);
            boolean involved = h.filterIds.stream().anyMatch(id -> idEquals(id, want))
                    || mentionedItem(h, want)
                    || h.conditions.stream().anyMatch(c ->
                            c.toLowerCase(Locale.ROOT).contains(want));
            if (useEvent && involved) {
                String use = formatUse(want, h, src);
                if (seen.add(use) && out.size() < cap) {
                    out.add(use);
                }
            }
            for (String give : h.giveIds) {
                String dropSubj = subjectForDrop(h, want, give);
                String drop = formatDrop(dropSubj, give, h, src);
                if (seen.add(drop) && out.size() < cap) {
                    out.add(drop);
                }
            }
            if (out.size() >= cap) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static String subjectForDrop(Handler h, String want, String give) {
        if (h.filterIds.stream().anyMatch(id -> idEquals(id, want))) {
            return want;
        }
        if (h.conditions.stream().anyMatch(c -> c.toLowerCase(Locale.ROOT).contains(want))) {
            return want;
        }
        if (mentionedItem(h, want) && !idEquals(want, give)) {
            return want;
        }
        for (String c : h.conditions) {
            if (c.startsWith("curios:") && c.length() > 7) {
                return c.substring(7);
            }
        }
        if (!h.filterIds.isEmpty()) {
            return h.filterIds.get(0);
        }
        return want;
    }

    private static boolean handlerHits(Handler h, String want) {
        if (h.filterIds.stream().anyMatch(id -> idEquals(id, want))) {
            return true;
        }
        if (mentionedItem(h, want)) {
            return true;
        }
        for (String c : h.conditions) {
            if (c.toLowerCase(Locale.ROOT).contains(want)) {
                return true;
            }
        }
        for (String g : h.giveIds) {
            if (idEquals(g, want)) {
                return true;
            }
        }
        return idEquals(h.entityFilter, want) || idEquals(h.blockFilter, want);
    }

    private static boolean mentionedItem(Handler h, String want) {
        for (String id : h.mentioned) {
            if (idEquals(id, want)) {
                return true;
            }
        }
        return false;
    }

    private static String formatUse(String item, Handler h, String source) {
        StringBuilder sb = new StringBuilder();
        sb.append("item:").append(item).append(" -[use]-> 觸發:").append(h.event);
        appendParts(sb, h);
        sb.append(" (").append(source).append(" tier:A)");
        return clip(sb.toString(), 400);
    }

    private static String formatDrop(String item, String give, Handler h, String source) {
        StringBuilder sb = new StringBuilder();
        sb.append("item:").append(item).append(" -[drops]-> item:").append(give);
        sb.append(" 當 ").append(h.event);
        if (!h.entityFilter.isEmpty()) {
            sb.append("/").append(h.entityFilter);
        } else if (!h.blockFilter.isEmpty()) {
            sb.append("/").append(h.blockFilter);
        }
        appendParts(sb, h);
        sb.append(" (").append(source).append(" tier:A)");
        return clip(sb.toString(), 400);
    }

    private static void appendParts(StringBuilder sb, Handler h) {
        if (!h.conditions.isEmpty()) {
            sb.append(" 條件:").append(clip(String.join(",", h.conditions), 120));
        }
        if (!h.effects.isEmpty()) {
            sb.append(" 效果:").append(clip(String.join(",", h.effects), 120));
        }
        if (h.chance != null && !h.chance.isEmpty()) {
            sb.append(" 機率:").append(h.chance);
        }
        if (h.note != null && !h.note.isEmpty()) {
            sb.append(" note:").append(h.note);
        }
    }

    private static String sourceTag(String rel, int line) {
        String r = rel == null ? "" : rel.replace('\\', '/');
        if (r.startsWith("./")) {
            r = r.substring(2);
        }
        if (r.startsWith("kubejs/")) {
            return "source:" + r + ":" + line;
        }
        if (r.isEmpty()) {
            return "source:kubejs/script.js:" + line;
        }
        return "source:kubejs/" + r + ":" + line;
    }

    private static List<Handler> loadHandlers(
            Path file, String rel, Path cacheDir, int maxFiles, int maxMb
    ) {
        String src;
        try {
            if (Files.size(file) > MAX_SCRIPT_BYTES) {
                return List.of();
            }
            src = Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return List.of();
        }
        String hash = sha256(rel + "\0" + src);
        List<Handler> cached = readCache(cacheDir, hash);
        if (cached != null) {
            for (Handler h : cached) {
                h.rel = rel;
            }
            return cached;
        }
        List<Handler> parsed = parseHandlers(src);
        for (Handler h : parsed) {
            h.rel = rel;
        }
        writeCache(cacheDir, hash, rel, parsed, maxFiles, maxMb);
        return parsed;
    }

    private static List<Handler> readCache(Path dir, String hash) {
        if (dir == null || hash == null || hash.length() < 12) {
            return null;
        }
        Path f = dir.resolve("kjs-" + hash.substring(0, 12) + ".json");
        if (!Files.isRegularFile(f)) {
            return null;
        }
        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (!hash.equals(o.has("hash") ? o.get("hash").getAsString() : "")) {
                return null;
            }
            List<Handler> out = new ArrayList<>();
            JsonArray arr = o.getAsJsonArray("handlers");
            if (arr == null) {
                return List.of();
            }
            for (JsonElement el : arr) {
                out.add(Handler.fromJson(el.getAsJsonObject()));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCache(
            Path dir, String hash, String rel, List<Handler> handlers, int maxFiles, int maxMb
    ) {
        if (dir == null || hash == null || hash.length() < 12) {
            return;
        }
        try {
            Files.createDirectories(dir);
            JsonObject o = new JsonObject();
            o.addProperty("hash", hash);
            o.addProperty("rel", rel);
            JsonArray arr = new JsonArray();
            for (Handler h : handlers) {
                arr.add(h.toJson());
            }
            o.add("handlers", arr);
            Path f = dir.resolve("kjs-" + hash.substring(0, 12) + ".json");
            Files.writeString(f, o.toString(), StandardCharsets.UTF_8);
            evictCache(dir, maxFiles, maxMb);
        } catch (Exception e) {
            warnCache(e);
        }
    }

    static void evictCache(Path dir, int maxFiles, int maxMb) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        int filesCap = Math.max(1, maxFiles);
        long bytesCap = Math.max(1L, (long) Math.max(1, maxMb) * 1024L * 1024L);
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    files.add(p);
                }
            }
        } catch (Exception e) {
            warnCache(e);
            return;
        }
        files.sort(Comparator
                .comparingLong(KubeJsMechanicScan::mtime)
                .thenComparing(p -> p.getFileName().toString()));
        long total = 0;
        for (Path p : files) {
            total += size(p);
        }
        int i = 0;
        while (i < files.size() && (files.size() - i > filesCap || total > bytesCap)) {
            Path p = files.get(i);
            long sz = size(p);
            try {
                Files.deleteIfExists(p);
                total -= sz;
            } catch (Exception e) {
                warnCache(e);
                break;
            }
            i++;
        }
    }

    private static void warnCache(Throwable t) {
        if (!CACHE_WARNED.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> c = Class.forName("com.skps9.packai.PackAiMod");
            Object logger = c.getField("LOGGER").get(null);
            logger.getClass()
                    .getMethod("warn", String.class, Object.class)
                    .invoke(logger, "Pack AI mechanic-cache write failed: {}",
                            t == null ? "" : t.toString());
        } catch (Throwable ignored) {
            System.err.println("Pack AI mechanic-cache write failed: " + t);
        }
    }

    private static Map<String, Set<String>> whitelist() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("ItemEvents", Set.of(
                "rightClicked", "firstRightClicked", "clientRightClicked", "entityInteracted",
                "dropped", "foodEaten", "canPickUp", "crafted", "smelted", "tooltip",
                "modifyTooltips", "dynamicTooltips", "modification"));
        m.put("BlockEvents", Set.of("rightClicked", "leftClicked", "placed", "broken"));
        m.put("EntityEvents", Set.of("death", "hurt", "spawned", "checkSpawn"));
        m.put("PlayerEvents", Set.of("tick", "inventoryChanged", "chestOpened", "advancement"));
        m.put("ServerEvents", Set.of(
                "entityLootTables", "genericLootTables", "chestLootTables", "blockLootTables", "tags"));
        return m;
    }

    private static Map<String, String> oldEvents() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("item.right_click", "ItemEvents.rightClicked");
        m.put("item.first_right_click", "ItemEvents.firstRightClicked");
        m.put("item.entity_interact", "ItemEvents.entityInteracted");
        m.put("item.dropped", "ItemEvents.dropped");
        m.put("item.food_eaten", "ItemEvents.foodEaten");
        m.put("item.can_pick_up", "ItemEvents.canPickUp");
        m.put("item.crafted", "ItemEvents.crafted");
        m.put("item.smelted", "ItemEvents.smelted");
        m.put("item.tooltip", "ItemEvents.tooltip");
        m.put("item.modification", "ItemEvents.modification");
        m.put("block.right_click", "BlockEvents.rightClicked");
        m.put("block.left_click", "BlockEvents.leftClicked");
        m.put("block.place", "BlockEvents.placed");
        m.put("block.placed", "BlockEvents.placed");
        m.put("block.break", "BlockEvents.broken");
        m.put("entity.death", "EntityEvents.death");
        m.put("entity.hurt", "EntityEvents.hurt");
        m.put("entity.spawned", "EntityEvents.spawned");
        m.put("entity.check_spawn", "EntityEvents.checkSpawn");
        m.put("player.tick", "PlayerEvents.tick");
        m.put("player.inventory.changed", "PlayerEvents.inventoryChanged");
        m.put("player.chest.opened", "PlayerEvents.chestOpened");
        m.put("player.advancement", "PlayerEvents.advancement");
        return m;
    }

    private static boolean isWhitelisted(String family, String method) {
        Set<String> s = WHITELIST.get(family);
        return s != null && s.contains(method);
    }

    private static void noteUnknown(String event) {
        if (event == null || event.isBlank()) {
            return;
        }
        synchronized (UNKNOWN_EVENTS) {
            UNKNOWN_EVENTS.add(event);
        }
    }

    private static void addCond(Handler h, String body, String token) {
        int at = body.indexOf(token);
        if (at < 0) {
            return;
        }
        String id = nearbyItem(body, at);
        h.conditions.add(id == null ? token : token + ":" + id);
    }

    private static void addFieldCond(Handler h, String body, String token, String label) {
        if (!body.contains(token)) {
            return;
        }
        String id = nearbyItem(body, body.indexOf(token));
        h.conditions.add(id == null ? label : label + ":" + id);
    }

    private static String nearbyItem(String body, int from) {
        if (from < 0) {
            return null;
        }
        int a = Math.max(0, from);
        int b = Math.min(body.length(), from + 160);
        Matcher m = ITEM_LIKE.matcher(body.substring(a, b));
        return m.find() ? m.group().toLowerCase(Locale.ROOT) : null;
    }

    private static List<String> quotedIds(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher m = QUOTED.matcher(text);
        while (m.find()) {
            String raw = m.group(1).trim().toLowerCase(Locale.ROOT);
            if (raw.startsWith("#") && raw.contains(":")) {
                if (!out.contains(raw)) {
                    out.add(raw);
                }
            } else if (looksLikeItem(raw) && !out.contains(raw)) {
                out.add(raw);
            }
        }
        return out;
    }

    private static String firstQuoted(String src, int from, int to) {
        if (from < 0 || to <= from || from >= src.length()) {
            return null;
        }
        Matcher m = QUOTED.matcher(src.substring(from, Math.min(to, src.length())));
        return m.find() ? m.group(1) : null;
    }

    static boolean looksLikeItem(String id) {
        if (id == null || id.length() < 3 || id.indexOf(':') <= 0) {
            return false;
        }
        return ITEM_LIKE.matcher(id).matches() && !isNoiseItemId(id.replaceFirst("^#", ""));
    }

    static boolean isNoiseItemId(String id) {
        if (id == null || id.indexOf(':') <= 0) {
            return true;
        }
        String path = id.substring(id.indexOf(':') + 1);
        return path.isEmpty()
                || path.equals("item")
                || path.equals("block")
                || path.equals("empty")
                || path.equals("air")
                || path.equals("entity")
                || path.equals("tag")
                || path.startsWith("loot_table");
    }

    private static boolean idEquals(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    private static String familyOf(String event) {
        if (event == null) {
            return "";
        }
        int d = event.indexOf('.');
        return d < 0 ? event : event.substring(0, d);
    }

    static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\r', ' ').replace('\n', ' ').trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max);
    }

    static int lineAt(String src, int offset) {
        int line = 1;
        int n = Math.min(Math.max(0, offset), src.length());
        for (int i = 0; i < n; i++) {
            if (src.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    static int matchingBrace(String s, int open) {
        return matchingPair(s, open, '{', '}');
    }

    static int matchingParen(String s, int open) {
        return matchingPair(s, open, '(', ')');
    }

    private static int matchingPair(String s, int open, char openCh, char closeCh) {
        if (s == null || open < 0 || open >= s.length()) {
            return open;
        }
        int depth = 0;
        int i = open;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n) {
                char n1 = s.charAt(i + 1);
                if (n1 == '/') {
                    i = skipLineComment(s, i);
                    continue;
                }
                if (n1 == '*') {
                    i = skipBlockComment(s, i);
                    continue;
                }
            }
            if (c == '\'' || c == '"') {
                i = skipQuoted(s, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(s, i);
                continue;
            }
            if (c == openCh) {
                depth++;
                i++;
                continue;
            }
            if (c == closeCh) {
                depth--;
                if (depth == 0) {
                    return i;
                }
                i++;
                continue;
            }
            i++;
        }
        return n - 1;
    }

    static int skipQuoted(String s, int i, char q) {
        int n = s.length();
        i++;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == q) {
                return i + 1;
            }
            i++;
        }
        return n;
    }

    static int skipTemplate(String s, int i) {
        int n = s.length();
        i++;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '`') {
                return i + 1;
            }
            if (c == '$' && i + 1 < n && s.charAt(i + 1) == '{') {
                int end = matchingBrace(s, i + 1);
                i = end + 1;
                continue;
            }
            i++;
        }
        return n;
    }

    static int skipLineComment(String s, int i) {
        int n = s.length();
        while (i < n && s.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    static int skipBlockComment(String s, int i) {
        int n = s.length();
        i += 2;
        while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
            i++;
        }
        return Math.min(n, i + 2);
    }

    private static int skipWs(String s, int i) {
        int n = s.length();
        while (i < n && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int skipAfterPrefix(String s, int i) {
        i = skipWs(s, i);
        if (i < s.length() && s.charAt(i) == '.') {
            i++;
            i += readIdent(s, i).length();
            i = skipWs(s, i);
        }
        if (i < s.length() && s.charAt(i) == '(') {
            return matchingParen(s, i) + 1;
        }
        return i;
    }

    private static String readIdent(String s, int i) {
        int n = s.length();
        int j = i;
        while (j < n && isIdentPart(s.charAt(j))) {
            j++;
        }
        return s.substring(i, j);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || Character.isDigit(c);
    }

    private static int findArrow(String s, int open, int close) {
        int i = open + 1;
        int paren = 1;
        int brace = 0;
        int bracket = 0;
        while (i < close && i < s.length()) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < s.length()) {
                char n1 = s.charAt(i + 1);
                if (n1 == '/') {
                    i = skipLineComment(s, i);
                    continue;
                }
                if (n1 == '*') {
                    i = skipBlockComment(s, i);
                    continue;
                }
            }
            if (c == '\'' || c == '"') {
                i = skipQuoted(s, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(s, i);
                continue;
            }
            if (c == '(') {
                paren++;
            } else if (c == ')') {
                paren--;
            } else if (c == '{') {
                brace++;
            } else if (c == '}') {
                brace--;
            } else if (c == '[') {
                bracket++;
            } else if (c == ']') {
                bracket--;
            } else if (c == '=' && i + 1 < s.length() && s.charAt(i + 1) == '>'
                    && paren == 1 && brace == 0 && bracket == 0) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static int indexOfCodeChar(String s, char want, int from, int to) {
        int i = from;
        int n = Math.min(s.length(), to);
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n) {
                char n1 = s.charAt(i + 1);
                if (n1 == '/') {
                    i = skipLineComment(s, i);
                    continue;
                }
                if (n1 == '*') {
                    i = skipBlockComment(s, i);
                    continue;
                }
            }
            if (c == '\'' || c == '"') {
                i = skipQuoted(s, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(s, i);
                continue;
            }
            if (c == want) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static int indexOfIdent(String s, String ident, int from, int to) {
        int i = from;
        int n = Math.min(s.length(), to);
        int len = ident.length();
        while (i + len <= n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n) {
                char n1 = s.charAt(i + 1);
                if (n1 == '/') {
                    i = skipLineComment(s, i);
                    continue;
                }
                if (n1 == '*') {
                    i = skipBlockComment(s, i);
                    continue;
                }
            }
            if (c == '\'' || c == '"') {
                i = skipQuoted(s, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(s, i);
                continue;
            }
            if (s.startsWith(ident, i)
                    && (i == 0 || !isIdentPart(s.charAt(i - 1)))
                    && (i + len >= s.length() || !isIdentPart(s.charAt(i + len)))) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static int indexOfCode(String s, String needle, int from) {
        int i = from;
        int n = s.length();
        int len = needle.length();
        while (i + len <= n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n) {
                char n1 = s.charAt(i + 1);
                if (n1 == '/') {
                    i = skipLineComment(s, i);
                    continue;
                }
                if (n1 == '*') {
                    i = skipBlockComment(s, i);
                    continue;
                }
            }
            if (c == '\'' || c == '"') {
                i = skipQuoted(s, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(s, i);
                continue;
            }
            if (s.startsWith(needle, i)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static String relUnder(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.getFileName().toString();
        }
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return 0L;
        }
    }

    static final class Handler {
        String event = "";
        int line;
        String rel = "";
        List<String> filterIds = new ArrayList<>();
        String entityFilter = "";
        String blockFilter = "";
        List<String> conditions = new ArrayList<>();
        List<String> effects = new ArrayList<>();
        List<String> giveIds = new ArrayList<>();
        String chance = "";
        String note = "";
        List<String> mentioned = new ArrayList<>();
        boolean known = true;

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("event", event);
            o.addProperty("line", line);
            o.addProperty("rel", rel);
            o.addProperty("entityFilter", entityFilter);
            o.addProperty("blockFilter", blockFilter);
            o.addProperty("chance", chance);
            o.addProperty("note", note);
            o.add("filterIds", strArr(filterIds));
            o.add("conditions", strArr(conditions));
            o.add("effects", strArr(effects));
            o.add("giveIds", strArr(giveIds));
            o.add("mentioned", strArr(mentioned));
            return o;
        }

        static Handler fromJson(JsonObject o) {
            Handler h = new Handler();
            h.event = str(o, "event");
            h.line = o.has("line") ? o.get("line").getAsInt() : 1;
            h.rel = str(o, "rel");
            h.entityFilter = str(o, "entityFilter");
            h.blockFilter = str(o, "blockFilter");
            h.chance = str(o, "chance");
            h.note = str(o, "note");
            h.filterIds = strList(o, "filterIds");
            h.conditions = strList(o, "conditions");
            h.effects = strList(o, "effects");
            h.giveIds = strList(o, "giveIds");
            h.mentioned = strList(o, "mentioned");
            return h;
        }

        private static String str(JsonObject o, String k) {
            return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
        }

        private static JsonArray strArr(List<String> list) {
            JsonArray a = new JsonArray();
            for (String s : list) {
                a.add(s);
            }
            return a;
        }

        private static List<String> strList(JsonObject o, String k) {
            List<String> out = new ArrayList<>();
            if (!o.has(k) || !o.get(k).isJsonArray()) {
                return out;
            }
            for (JsonElement e : o.getAsJsonArray(k)) {
                out.add(e.getAsString());
            }
            return out;
        }
    }
}
