package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/** Headless DailyTokenUsage. Run with -ea. */
public final class DailyTokenUsageCheck {
    private DailyTokenUsageCheck() {}

    public static void main(String[] args) throws Exception {
        billableMath();
        estimateCeil();
        recordAndOverLimit();
        sameDayOverwrite();
        fileCapKeepsToday();
        System.out.println("DailyTokenUsageCheck OK");
    }

    private static void billableMath() {
        assert DailyTokenUsage.billable(TokenUsage.NONE) == 0;
        assert DailyTokenUsage.billable(new TokenUsage(10, 20, -1)) == 30 : "p+c";
        assert DailyTokenUsage.billable(new TokenUsage(-1, -1, 50)) == 50 : "total alone";
        // total wins when larger than p+c (providers sometimes report only total)
        assert DailyTokenUsage.billable(new TokenUsage(1, 1, 100)) == 100 : "max(total,p+c)";
        // p+c wins when total is the -1 sentinel coerced to 0
        assert DailyTokenUsage.billable(new TokenUsage(40, 10, -1)) == 50;
        // Negative control: must NOT use raw total==-1 as identity that skips p+c
        assert DailyTokenUsage.billable(new TokenUsage(3, 4, -1)) == 7;
    }

    private static void estimateCeil() {
        assert DailyTokenUsage.estimateFromChars(0) == 0;
        assert DailyTokenUsage.estimateFromChars(1) == 1;
        assert DailyTokenUsage.estimateFromChars(3) == 1;
        assert DailyTokenUsage.estimateFromChars(4) == 2;
    }

    private static void recordAndOverLimit() throws Exception {
        DailyTokenUsage.resetForTest();
        DailyTokenUsage.dayClock = () -> LocalDate.of(2026, 9, 15);
        Path tmp = Files.createTempDirectory("packai-usage-over");
        assert !DailyTokenUsage.overLimit(tmp, 0);
        assert !DailyTokenUsage.overLimit(tmp, 100);
        assert DailyTokenUsage.todayUsed(tmp) == 0;
        assert DailyTokenUsage.record(tmp, 60) == 60;
        assert DailyTokenUsage.todayUsed(tmp) == 60;
        assert !DailyTokenUsage.overLimit(tmp, 100);
        assert DailyTokenUsage.record(tmp, 50) == 110;
        assert DailyTokenUsage.overLimit(tmp, 100);
        Path file = DailyTokenUsage.usageFile(tmp);
        assert Files.isRegularFile(file);
        String body = Files.readString(file, StandardCharsets.UTF_8);
        assert body.contains("\"2026-09-15\"") : body;
        assert !body.contains("apiKey") : body;
    }

    private static void sameDayOverwrite() throws Exception {
        DailyTokenUsage.resetForTest();
        DailyTokenUsage.dayClock = () -> LocalDate.of(2026, 9, 15);
        Path tmp = Files.createTempDirectory("packai-usage-day");
        DailyTokenUsage.record(tmp, 10);
        DailyTokenUsage.record(tmp, 5);
        String body = Files.readString(DailyTokenUsage.usageFile(tmp), StandardCharsets.UTF_8);
        // one key for the day (value is sum 15)
        int hits = 0;
        int idx = 0;
        while ((idx = body.indexOf("2026-09-15", idx)) >= 0) {
            hits++;
            idx += 10;
        }
        assert hits == 1 : body;
        assert DailyTokenUsage.todayUsed(tmp) == 15 : body;
    }

    private static void fileCapKeepsToday() throws Exception {
        DailyTokenUsage.resetForTest();
        DailyTokenUsage.dayClock = () -> LocalDate.of(2026, 9, 20);
        Path tmp = Files.createTempDirectory("packai-usage-cap");
        // Many day keys with fat values → trimToFit must keep today under FILE_MAX_BYTES.
        Path file = DailyTokenUsage.usageFile(tmp);
        Files.createDirectories(file.getParent());
        StringBuilder sb = new StringBuilder("{");
        for (int d = 1; d <= 200; d++) {
            if (d > 1) {
                sb.append(',');
            }
            LocalDate day = LocalDate.of(2025, 1, 1).plusDays(d);
            sb.append('"').append(day).append("\":").append(1_000_000 + d);
        }
        sb.append('}');
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        assert Files.size(file) > 1000;
        int after = DailyTokenUsage.record(tmp, 42);
        assert after == 42 : after;
        byte[] raw = Files.readAllBytes(DailyTokenUsage.usageFile(tmp));
        assert raw.length <= DailyTokenUsage.FILE_MAX_BYTES : raw.length;
        assert DailyTokenUsage.todayUsed(tmp) == 42;
        String body = new String(raw, StandardCharsets.UTF_8);
        assert body.contains("2026-09-20") : body;
    }
}
