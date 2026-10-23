package com.skps9.packai.client.gui;

import java.util.function.ToIntFunction;

/** Truncate text to fit a pixel budget using a width function (e.g. font::width). */
public final class Fit {
    private static final String ELLIPSIS = "...";

    private Fit() {}

    public static String fit(String s, int avail, ToIntFunction<String> widthOf) {
        if (avail <= 0) {
            return "";
        }
        if (s == null) {
            s = "";
        }
        if (widthOf.applyAsInt(s) <= avail) {
            return s;
        }
        int ellW = widthOf.applyAsInt(ELLIPSIS);
        if (ellW > avail) {
            return "";
        }
        int cpCount = s.codePointCount(0, s.length());
        int[] cuts = new int[cpCount + 1];
        cuts[0] = 0;
        for (int i = 1; i <= cpCount; i++) {
            cuts[i] = s.offsetByCodePoints(0, i);
        }
        int lo = 0;
        int hi = cpCount;
        int best = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            String base = s.substring(0, cuts[mid]);
            if (widthOf.applyAsInt(base) + ellW <= avail) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        String base = s.substring(0, cuts[best]);
        return base.isEmpty() ? ELLIPSIS : base + ELLIPSIS;
    }
}
