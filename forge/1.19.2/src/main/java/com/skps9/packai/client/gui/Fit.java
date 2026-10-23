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
        String base = s;
        while (base.length() > 0 && widthOf.applyAsInt(base) + ellW > avail) {
            base = base.substring(0, base.length() - 1);
        }
        return base.isEmpty() ? ELLIPSIS : base + ELLIPSIS;
    }
}
