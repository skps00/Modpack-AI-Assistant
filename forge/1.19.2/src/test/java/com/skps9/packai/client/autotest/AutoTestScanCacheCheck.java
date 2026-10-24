package com.skps9.packai.client.autotest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/** Headless checks for incremental {@link AutoTestHarness} trace scan cache. Run with -ea. */
public final class AutoTestScanCacheCheck {
    private static final LocalDateTime SINCE = LocalDateTime.parse("2020-01-01T00:00:00");
    private static final String ITEM = "minecraft:stick";

    private AutoTestScanCacheCheck() {}

    public static void main(String[] args) throws Exception {
        incrementalCache();
        appendOnlyRead();
        negativeFullRead();
        System.out.println("AutoTestScanCacheCheck OK");
    }

    private static void incrementalCache() throws Exception {
        AutoTestHarness.clearScanCacheForTest();
        Path file = Files.createTempFile("packai-autotest-scan-", ".jsonl");
        try {
            writeLines(
                    file,
                    line("2020-01-02T12:00:01", "other"),
                    line("2020-01-02T12:00:02", "display.body.final"),
                    line("2020-01-02T12:00:03", "render.cards.final", ITEM, 2));

            var hit1 = AutoTestHarness.scanTraceFileForTest(file, SINCE, ITEM);
            assert hit1.display : hit1;
            assert hit1.cards : hit1;
            assert hit1.cardsOut == 2 : hit1;
            int opensAfterFirst = AutoTestHarness.scanChannelOpensForTest();
            assert opensAfterFirst == 1 : opensAfterFirst;

            var hit2 = AutoTestHarness.scanTraceFileForTest(file, SINCE, ITEM);
            assert hit2.display && hit2.cards && hit2.cardsOut == 2 : hit2;
            assert AutoTestHarness.lastScanCacheHitForTest() : "scan 2 should hit cache";
            assert AutoTestHarness.scanChannelOpensForTest() == opensAfterFirst : "scan 2 must not open channel";

            var hit3 = AutoTestHarness.scanTraceFileForTest(file, SINCE, ITEM);
            assert hit3.display && hit3.cards && hit3.cardsOut == 2 : hit3;
            assert AutoTestHarness.lastScanCacheHitForTest() : "scan 3 should hit cache";
            assert AutoTestHarness.scanChannelOpensForTest() == opensAfterFirst : "scan 3 must not open channel";
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void appendOnlyRead() throws Exception {
        AutoTestHarness.clearScanCacheForTest();
        Path file = Files.createTempFile("packai-autotest-scan-", ".jsonl");
        try {
            writeLines(
                    file,
                    line("2020-01-02T12:00:01", "other"),
                    line("2020-01-02T12:00:02", "display.body.final"),
                    line("2020-01-02T12:00:03", "render.cards.final", ITEM, 2));
            AutoTestHarness.scanTraceFileForTest(file, SINCE, ITEM);
            int opensAfterWarm = AutoTestHarness.scanChannelOpensForTest();

            Files.writeString(
                    file,
                    line("2020-01-02T12:00:04", "display.body.final") + "\n",
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
            var hit = AutoTestHarness.scanTraceFileForTest(file, SINCE, ITEM);
            assert !AutoTestHarness.lastScanCacheHitForTest() : "append should miss size cache";
            assert AutoTestHarness.scanChannelOpensForTest() == opensAfterWarm + 1 : "one incremental open";
            assert hit.display && hit.cards && hit.cardsOut == 2 : hit;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void negativeFullRead() throws Exception {
        AutoTestHarness.clearScanCacheForTest();
        Path file = Files.createTempFile("packai-autotest-scan-", ".jsonl");
        try {
            writeLines(
                    file,
                    line("2020-01-02T12:00:01", "other"),
                    line("2020-01-02T12:00:02", "display.body.final"),
                    line("2020-01-02T12:00:03", "render.cards.final", ITEM, 1));
            AutoTestHarness.scanTraceFileForTest(file, SINCE, ITEM);
            int opensAfterFirst = AutoTestHarness.scanChannelOpensForTest();

            AutoTestHarness.setForceFullFileScanForTest(true);
            AutoTestHarness.scanTraceFileForTest(file, SINCE, ITEM);
            boolean noExtraReadsOnRepeat =
                    AutoTestHarness.scanChannelOpensForTest() == opensAfterFirst;
            assert !noExtraReadsOnRepeat
                    : "negative control: full-file rescan must fail no-extra-read assert";
        } finally {
            AutoTestHarness.clearScanCacheForTest();
            Files.deleteIfExists(file);
        }
    }

    private static void writeLines(Path file, String... lines) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        Files.writeString(file, sb, StandardCharsets.UTF_8);
    }

    private static String line(String ts, String event) {
        return "{\"ts\":\"" + ts + "\",\"event\":\"" + event + "\"}";
    }

    private static String line(String ts, String event, String item, int cardsOut) {
        return "{\"ts\":\""
                + ts
                + "\",\"event\":\""
                + event
                + "\",\"item\":\""
                + item
                + "\",\"cardsOut\":"
                + cardsOut
                + "}";
    }
}
