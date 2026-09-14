package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Silent unknown-item log. One line per item id; fields: ts, item, reason, seen.
 */
public final class UnknownItemLog {
    public static final int DEFAULT_MAX_LINES = 2000;
    public static final int DEFAULT_MAX_MB = 1;
    public static final String FILENAME = "unknown_items.jsonl";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Object LOCK = new Object();
    private static final Pattern REASON = Pattern.compile("^[a-z0-9_]{1,32}$");
    static Supplier<Instant> clock = Instant::now;

    private UnknownItemLog() {}

    public static Path logFile(Path gameDir) {
        return gameDir.resolve("config").resolve("packai").resolve(FILENAME);
    }

    public static void record(Path gameDir, String item, String reason) {
        record(gameDir, item, reason, liveMaxLines(), liveMaxBytes());
    }

    static void record(Path gameDir, String item, String reason, int maxLines, long maxBytes) {
        if (gameDir == null || item == null || item.isBlank()) {
            return;
        }
        String id = item.trim().toLowerCase(Locale.ROOT);
        String why = sanitizeReason(reason);
        synchronized (LOCK) {
            try {
                Path file = logFile(gameDir);
                Files.createDirectories(file.getParent());
                Map<String, Row> rows = readAll(file);
                Row prev = rows.get(id);
                Row next = new Row();
                next.ts = clock.get().toString();
                next.item = id;
                next.reason = why;
                next.seen = prev == null ? 1 : prev.seen + 1;
                rows.remove(id);
                rows.put(id, next);
                List<Row> list = new ArrayList<>(rows.values());
                list.sort(Comparator.comparing((Row r) -> r.ts).thenComparing(r -> r.item));
                int linesCap = Math.max(1, maxLines);
                long bytesCap = Math.max(1L, maxBytes);
                while (list.size() > linesCap) {
                    list.remove(0);
                }
                while (encodedSize(list) > bytesCap && list.size() > 1) {
                    list.remove(0);
                }
                StringBuilder sb = new StringBuilder();
                for (Row r : list) {
                    sb.append(encode(r)).append('\n');
                }
                Path tmp = file.resolveSibling(FILENAME + ".tmp");
                Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception e) {
                    Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {
                // never throw into Ask
            }
        }
    }

    static List<Row> readRows(Path file) {
        return new ArrayList<>(readAll(file).values());
    }

    private static Map<String, Row> readAll(Path file) {
        LinkedHashMap<String, Row> map = new LinkedHashMap<>();
        if (file == null || !Files.isRegularFile(file)) {
            return map;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return map;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Row r = decode(line);
            if (r == null || r.item.isBlank()) {
                continue;
            }
            map.remove(r.item);
            map.put(r.item, r);
        }
        return map;
    }

    static String encode(Row r) {
        JsonObject o = new JsonObject();
        o.addProperty("ts", r.ts);
        o.addProperty("item", r.item);
        o.addProperty("reason", r.reason);
        o.addProperty("seen", r.seen);
        return GSON.toJson(o);
    }

    static Row decode(String line) {
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            Row r = new Row();
            r.ts = KnowledgeEntry.str(o, "ts");
            r.item = KnowledgeEntry.str(o, "item").trim().toLowerCase(Locale.ROOT);
            r.reason = sanitizeReason(KnowledgeEntry.str(o, "reason"));
            r.seen = o.has("seen") && o.get("seen").isJsonPrimitive()
                    ? Math.max(1, o.get("seen").getAsInt()) : 1;
            return r;
        } catch (Throwable t) {
            return null;
        }
    }

    static String sanitizeReason(String reason) {
        if (reason == null) {
            return "miss";
        }
        String s = reason.trim().toLowerCase(Locale.ROOT);
        return REASON.matcher(s).matches() ? s : "miss";
    }

    private static long encodedSize(List<Row> list) {
        long n = 0;
        for (Row r : list) {
            n += encode(r).length() + 1L;
        }
        return n;
    }

    private static int liveMaxLines() {
        try {
            Class<?> c = Class.forName("com.skps9.packai.config.PackAiConfig");
            Object v = c.getMethod("unknownMaxLines").invoke(null);
            if (v instanceof Integer i) {
                return i;
            }
        } catch (Throwable ignored) {
            // headless
        }
        return DEFAULT_MAX_LINES;
    }

    private static long liveMaxBytes() {
        int mb = DEFAULT_MAX_MB;
        try {
            Class<?> c = Class.forName("com.skps9.packai.config.PackAiConfig");
            Object v = c.getMethod("unknownMaxMb").invoke(null);
            if (v instanceof Integer i) {
                mb = i;
            }
        } catch (Throwable ignored) {
            // headless
        }
        return Math.max(1L, (long) Math.max(1, mb) * 1024L * 1024L);
    }

    static final class Row {
        String ts = "";
        String item = "";
        String reason = "miss";
        int seen = 1;
    }
}
