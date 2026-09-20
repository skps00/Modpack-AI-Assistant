package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * Plan B v3 CostWindow／usage display harness (S1–S5, S7). Run with -ea.
 */
public final class CostWindowCheck {
    private static final Pattern PLAYER_VISIBLE_FORBIDDEN =
            Pattern.compile("(src:)|(\\.js\\b)|(\\.json\\b)|(kubejs[/\\\\])");

    private CostWindowCheck() {}

    public static void main(String[] args) throws Exception {
        dayRolloverS1();
        deepSeekPeakS2();
        usageSummaryS3();
        widthS4();
        resetMessageS5();
        playerVisibleKeysS7();
        unreadableProbe();
        System.out.println("CostWindowCheck OK");
    }

    /** S1: inject dayClock → record → flip day → 0. */
    static void dayRolloverS1() throws Exception {
        DailyTokenUsage.resetForTest();
        DailyTokenUsage.dayClock = () -> LocalDate.of(2026, 9, 17);
        Path tmp = Files.createTempDirectory("packai-usage-rollover");
        assert DailyTokenUsage.record(tmp, 1000) == 1000;
        assert DailyTokenUsage.todayUsed(tmp) == 1000;
        DailyTokenUsage.dayClock = () -> LocalDate.of(2026, 9, 18);
        assert DailyTokenUsage.todayUsed(tmp) == 0 : "cross-UTC-day must read 0";
        DailyTokenUsage.resetForTest();
        System.out.println("dayRolloverS1 OK");
    }

    /** S2: five peak cases + openai negative control. */
    static void deepSeekPeakS2() {
        String ds = "https://api.deepseek.com";
        Instant mon02 = CostWindow.utcAt(2026, 9, 14, 2); // Monday
        Instant mon12 = CostWindow.utcAt(2026, 9, 14, 12);
        Instant sat02 = CostWindow.utcAt(2026, 9, 12, 2); // Saturday
        assert CostWindow.isDeepSeekPeak(mon02, ds, false) : "Mon 02:00";
        assert !CostWindow.isDeepSeekPeak(mon12, ds, false) : "Mon 12:00";
        assert !CostWindow.isDeepSeekPeak(sat02, ds, false) : "Sat 02:00";
        assert !CostWindow.isDeepSeekPeak(mon02, ds, true) : "usingLocal";
        assert !CostWindow.isDeepSeekPeak(mon02, "https://api.openai.com/v1", false) : "openai neg";
        System.out.println("deepSeekPeakS2 OK");
    }

    /** S3: formatCount compact + 不限. */
    static void usageSummaryS3() {
        String s = CostWindow.usageSummary(34235, 10000);
        assert s.contains("34k") : s;
        assert s.contains("10k") : s;
        assert s.startsWith("UTC ") : s;
        String unlim = CostWindow.usageSummary(34235, 0);
        assert unlim.contains("不限") : unlim;
        assert unlim.contains("34k") : unlim;
        System.out.println("usageSummaryS3 OK " + s);
    }

    /** S4: compact summary fits ~340px budget. */
    static void widthS4() {
        String s = CostWindow.usageSummary(34235, 10000);
        assert CostWindow.fitsValueMax(s, 340) : s;
        String full =
                s
                        + "（下次重置 00:00 UTC ~ 本地 08:00） DeepSeek 高峰時段（較貴）";
        // full row may truncate via fitWidth; core counts must always fit
        assert CostWindow.fitsValueMax(s, 340);
        assert TokenUsage.formatCount(34235).equals("34k");
        assert full.length() > 0;
        System.out.println("widthS4 OK coreLen=" + s.length());
    }

    /** S5: fixed epoch + Asia/Shanghai → 00:00 UTC + 08:00. */
    static void resetMessageS5() {
        // 2026-09-17 12:00 UTC → next midnight = 2026-09-18 00:00 UTC = 08:00 Shanghai
        long epoch = CostWindow.utcAt(2026, 9, 17, 12).toEpochMilli();
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        assert "00:00 UTC".equals(CostWindow.nextResetUtcLabel());
        assert "08:00".equals(CostWindow.nextResetLocal(epoch, shanghai))
                : CostWindow.nextResetLocal(epoch, shanghai);
        String msg = ReplyLang.dailyTokenLimitReached("zh_tw", 10000, 34235, epoch, shanghai);
        assert msg.contains("00:00 UTC") : msg;
        assert msg.contains("08:00") : msg;
        // existing 2-%s template still present as used/limit digits
        assert msg.contains("34235") && msg.contains("10000") : msg;
        System.out.println("resetMessageS5 OK");
    }

    /** S7: new player-visible lang values have no forbidden tokens. */
    static void playerVisibleKeysS7() {
        String[] keys = {
            "packai.settings.usage.summary",
            "packai.settings.usage.peak",
            "packai.settings.usage.unreadable",
            "packai.reply.daily_token_limit_reset"
        };
        for (String lang : new String[] {"en_us", "zh_cn", "zh_tw"}) {
            for (String key : keys) {
                String v = ReplyLang.tr(lang, key, "A", "B", "C");
                assert v != null && !v.isBlank() : lang + " " + key;
                assert !PLAYER_VISIBLE_FORBIDDEN.matcher(v).find()
                        : "PLAYER_VISIBLE_FORBIDDEN " + lang + " " + key + "=" + v;
            }
        }
        System.out.println("playerVisibleKeysS7 OK");
    }

    static void unreadableProbe() throws Exception {
        Path tmp = Files.createTempDirectory("packai-usage-bad");
        assert !CostWindow.isUsageUnreadable(tmp) : "missing file = 0 used, readable";
        assert CostWindow.isUsageUnreadable(null);
        Path file = DailyTokenUsage.usageFile(tmp);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "NOT_JSON{{{", StandardCharsets.UTF_8);
        assert CostWindow.isUsageUnreadable(tmp) : "corrupt must be unreadable";
        System.out.println("unreadableProbe OK");
    }
}
