package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Daily LLM token ledger under {@code <gameDir>/config/packai-usage.json}.
 * Single-writer (process lock); one YYYY-MM-DD key per day; 64 KiB hard cap.
 */
public final class DailyTokenUsage {
    public static final String FILENAME = "packai-usage.json";
    public static final int FILE_MAX_BYTES = 64 * 1024;
    public static final int DEFAULT_LIMIT = 0;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Object LOCK = new Object();

    /** Testable clock — production uses UTC calendar date. */
    static Supplier<LocalDate> dayClock = () -> LocalDate.now(ZoneOffset.UTC);

    private DailyTokenUsage() {}

    public static Path usageFile(Path gameDir) {
        return gameDir == null ? null : gameDir.resolve("config").resolve(FILENAME);
    }

    /** 0 = unlimited (never blocks). */
    public static boolean overLimit(Path gameDir, int limit) {
        if (limit <= 0) {
            return false;
        }
        return todayUsed(gameDir) >= limit;
    }

    public static int todayUsed(Path gameDir) {
        synchronized (LOCK) {
            Map<String, Integer> map = readMap(usageFile(gameDir));
            Integer n = map.get(todayKey());
            return n == null || n < 0 ? 0 : n;
        }
    }

    /**
     * Add {@code tokens} to today's total (same-day overwrite of the sum).
     * Returns new today total. Non-positive tokens are ignored (returns current).
     */
    public static int record(Path gameDir, int tokens) {
        if (gameDir == null || tokens <= 0) {
            return todayUsed(gameDir);
        }
        synchronized (LOCK) {
            Path file = usageFile(gameDir);
            Map<String, Integer> map = readMap(file);
            String day = todayKey();
            int prev = map.getOrDefault(day, 0);
            if (prev < 0) {
                prev = 0;
            }
            int next = prev + tokens;
            map.put(day, next);
            writeMap(file, map);
            return next;
        }
    }

    /**
     * Billable count: clamp each field to ≥0, then {@code max(total, prompt+completion)}.
     * Missing (-1) fields count as 0 for the sum path; total alone still wins via max.
     */
    public static int billable(TokenUsage u) {
        if (u == null || !u.isPresent()) {
            return 0;
        }
        int p = clamp0(u.promptTokens());
        int c = clamp0(u.completionTokens());
        int t = clamp0(u.totalTokens());
        long sum = (long) p + (long) c;
        long bill = Math.max(t, sum);
        return bill > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bill;
    }

    /** Ceiling of {@code chars / 3} (plan: estimate when usage missing). */
    public static int estimateFromChars(int chars) {
        if (chars <= 0) {
            return 0;
        }
        return (chars + 2) / 3;
    }

    static String todayKey() {
        return dayClock.get().toString();
    }

    static void resetForTest() {
        dayClock = () -> LocalDate.now(ZoneOffset.UTC);
    }

    private static int clamp0(int n) {
        return n < 0 ? 0 : n;
    }

    private static Map<String, Integer> readMap(Path file) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        if (file == null || !Files.isRegularFile(file)) {
            return out;
        }
        try {
            byte[] raw = Files.readAllBytes(file);
            if (raw.length == 0 || raw.length > FILE_MAX_BYTES) {
                return out;
            }
            String text = new String(raw, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return out;
            }
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                return out;
            }
            JsonObject o = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null
                        || !e.getValue().isJsonPrimitive()
                        || !e.getValue().getAsJsonPrimitive().isNumber()) {
                    continue;
                }
                int n = e.getValue().getAsInt();
                if (n >= 0) {
                    out.put(e.getKey(), n);
                }
            }
        } catch (Throwable ignored) {
            return new LinkedHashMap<>();
        }
        return out;
    }

    private static void writeMap(Path file, Map<String, Integer> map) {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            LinkedHashMap<String, Integer> trimmed = trimToFit(map);
            JsonObject o = new JsonObject();
            for (Map.Entry<String, Integer> e : trimmed.entrySet()) {
                o.addProperty(e.getKey(), e.getValue());
            }
            byte[] bytes = GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > FILE_MAX_BYTES) {
                // Last resort: keep only today.
                JsonObject only = new JsonObject();
                String day = todayKey();
                only.addProperty(day, trimmed.getOrDefault(day, 0));
                bytes = GSON.toJson(only).getBytes(StandardCharsets.UTF_8);
            }
            Path tmp = file.resolveSibling(FILENAME + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable t) {
            try {
                // Windows often rejects ATOMIC_MOVE across volumes — plain replace.
                Path tmp = file.resolveSibling(FILENAME + ".tmp");
                if (Files.isRegularFile(tmp)) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable ignored) {
                // ledger failure never blocks Ask
            }
        }
    }

    /** Drop oldest YYYY-MM-DD keys until JSON ≤ FILE_MAX_BYTES. */
    private static LinkedHashMap<String, Integer> trimToFit(Map<String, Integer> map) {
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>(map);
        List<String> keys = new ArrayList<>(copy.keySet());
        keys.sort(Comparator.naturalOrder());
        while (keys.size() > 1) {
            JsonObject probe = new JsonObject();
            for (String k : keys) {
                probe.addProperty(k, copy.get(k));
            }
            if (GSON.toJson(probe).getBytes(StandardCharsets.UTF_8).length <= FILE_MAX_BYTES) {
                break;
            }
            String drop = keys.remove(0);
            copy.remove(drop);
        }
        LinkedHashMap<String, Integer> ordered = new LinkedHashMap<>();
        for (String k : keys) {
            ordered.put(k, copy.get(k));
        }
        return ordered;
    }
}
