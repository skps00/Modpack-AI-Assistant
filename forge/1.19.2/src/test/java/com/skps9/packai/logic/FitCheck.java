package com.skps9.packai.logic;

import java.util.function.ToIntFunction;

import com.skps9.packai.client.gui.Fit;

/** Headless check for {@link Fit#fit} (code-point cuts + binary search). */
public final class FitCheck {
    private FitCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    private static final ToIntFunction<String> ASCII_W = s -> s.length() * 6;

    private static final ToIntFunction<String> EMOJI_W = s -> {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += 12;
            i += Character.charCount(cp);
        }
        return w;
    };

    private static void assertLenLe(String result, String original, String label) {
        if (result.length() > original.length()) {
            fail(label + " result longer than input");
        }
    }

    private static void assertNoLoneHighSurrogate(String result, String label) {
        if (result.isEmpty()) {
            return;
        }
        if (Character.isHighSurrogate(result.charAt(result.length() - 1))) {
            fail(label + " ends with isolated high surrogate");
        }
    }

    public static void main(String[] args) {
        // 1. Short string fits (incl. empty)
        String hello = "hello";
        String r1a = Fit.fit(hello, 100, ASCII_W);
        if (!r1a.equals(hello)) {
            fail("1 hello want原樣 got=" + r1a);
        }
        String r1b = Fit.fit("", 100, ASCII_W);
        if (!r1b.equals("")) {
            fail("1 empty want=\"\" got=" + r1b);
        }
        assertLenLe(r1a, hello, "1a");
        assertLenLe(r1b, "", "1b");

        // 2. avail <= 0
        if (!Fit.fit("x", 0, ASCII_W).equals("")) {
            fail("2 avail=0");
        }
        if (!Fit.fit("x", -1, ASCII_W).equals("")) {
            fail("2 avail<0");
        }

        // 3. avail narrower than "..." (ellW = 18)
        String r3 = Fit.fit("hello", 10, ASCII_W);
        if (!r3.equals("")) {
            fail("3 want=\"\" got=" + r3);
        }

        // 4. Long string — width budget + ellipsis suffix
        String longStr = "abcdefghijklmnop";
        int avail4 = 50;
        String r4 = Fit.fit(longStr, avail4, ASCII_W);
        if (ASCII_W.applyAsInt(r4) > avail4) {
            fail("4 width " + ASCII_W.applyAsInt(r4) + " > " + avail4);
        }
        if (!r4.endsWith("...")) {
            fail("4 missing ellipsis: " + r4);
        }
        assertLenLe(r4, longStr, "4");

        // 5. Emoji — no split surrogate at end
        String emoji = "aaa🙂bbb";
        String r5 = Fit.fit(emoji, 70, EMOJI_W);
        if (EMOJI_W.applyAsInt(emoji) <= 70) {
            fail("5 expected truncation");
        }
        assertNoLoneHighSurrogate(r5, "5");
        assertLenLe(r5, emoji, "5");

        // 6. 5000 chars @ 200px — no exception, satisfies (4)
        StringBuilder sb = new StringBuilder(5000);
        for (int i = 0; i < 5000; i++) {
            sb.append('a');
        }
        String big = sb.toString();
        String r6;
        try {
            r6 = Fit.fit(big, 200, ASCII_W);
        } catch (Exception e) {
            fail("6 threw " + e);
            return;
        }
        if (ASCII_W.applyAsInt(r6) > 200) {
            fail("6 width exceed");
        }
        if (!r6.endsWith("...")) {
            fail("6 missing ellipsis");
        }
        assertLenLe(r6, big, "6");

        // 7. Length bound (cross-check prior results)
        assertLenLe(r3, "hello", "7-r3");

        System.out.println("check FitCheck OK");
    }
}
