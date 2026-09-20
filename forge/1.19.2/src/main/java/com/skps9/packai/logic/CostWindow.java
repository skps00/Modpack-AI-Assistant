package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Pure helpers for daily usage／cost UI: compact counts, next UTC reset in a zone, DeepSeek peak.
 * Does not mutate {@link DailyTokenUsage}.
 */
public final class CostWindow {
    /** Testable clock — production uses wall UTC instant. */
    static Supplier<Instant> nowUtc = Instant::now;

    private CostWindow() {}

    static void resetForTest() {
        nowUtc = Instant::now;
    }

    /**
     * Compact row core: {@code UTC 34k / 10k} or {@code UTC 34k / 不限} when limit≤0.
     * Reuses {@link TokenUsage#formatCount(int)} (Locale.ROOT).
     */
    public static String usageSummary(int used, int limit) {
        String u = TokenUsage.formatCount(Math.max(0, used));
        String lim = limit <= 0 ? "不限" : TokenUsage.formatCount(limit);
        return "UTC " + u + " / " + lim;
    }

    /** Next UTC calendar midnight as {@code HH:mm} in {@code zone} (explicit — not systemDefault). */
    public static String nextResetLocal(long nowEpochMillis, ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        Instant now = Instant.ofEpochMilli(nowEpochMillis);
        LocalDate utcDay = now.atZone(ZoneOffset.UTC).toLocalDate();
        Instant nextMidnightUtc = utcDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        ZonedDateTime local = nextMidnightUtc.atZone(zone);
        return String.format(Locale.ROOT, "%02d:%02d", local.getHour(), local.getMinute());
    }

    public static String nextResetUtcLabel() {
        return "00:00 UTC";
    }

    /**
     * DeepSeek peak: Mon–Fri 01:00–04:00 and 06:00–10:00 UTC (half-open hour windows).
     * {@code usingLocal} or non-deepseek {@code apiBase} → false.
     * Rule: {@code apiBase != null && apiBase.toLowerCase(Locale.ROOT).contains("deepseek")}.
     */
    public static boolean isDeepSeekPeak(Instant now, String apiBase, boolean usingLocal) {
        if (usingLocal) {
            return false;
        }
        if (apiBase == null || !apiBase.toLowerCase(Locale.ROOT).contains("deepseek")) {
            return false;
        }
        Instant t = now == null ? nowUtc.get() : now;
        ZonedDateTime z = t.atZone(ZoneOffset.UTC);
        DayOfWeek dow = z.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        int hour = z.getHour();
        return (hour >= 1 && hour < 4) || (hour >= 6 && hour < 10);
    }

    /**
     * Ledger probe without changing {@link DailyTokenUsage}: missing file = readable (0 used);
     * null gameDir / corrupt / oversize / non-object JSON = unreadable (UI must not show silent 0).
     */
    public static boolean isUsageUnreadable(Path gameDir) {
        if (gameDir == null) {
            return true;
        }
        Path file = DailyTokenUsage.usageFile(gameDir);
        if (file == null) {
            return true;
        }
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            byte[] raw = Files.readAllBytes(file);
            if (raw.length > DailyTokenUsage.FILE_MAX_BYTES) {
                return true;
            }
            if (raw.length == 0) {
                return false;
            }
            String text = new String(raw, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return false;
            }
            JsonElement root = JsonParser.parseString(text);
            return root == null || !root.isJsonObject();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Approx UI width budget check: compact summary must fit ~340px (~56 default-font cells). */
    public static boolean fitsValueMax(String summary, int valueMaxPxApprox) {
        if (summary == null) {
            return true;
        }
        // ponytail: MC font ~6px/ASCII cell; upgrade = real Font.width if headless ever gets atlas
        int approxPx = summary.length() * 6;
        return approxPx <= valueMaxPxApprox;
    }

    /** Instant at {@code hour:00} UTC on the given calendar day (test helper). */
    static Instant utcAt(int year, int month, int day, int hour) {
        return ZonedDateTime.of(
                        LocalDate.of(year, month, day), LocalTime.of(hour, 0), ZoneOffset.UTC)
                .toInstant();
    }
}
