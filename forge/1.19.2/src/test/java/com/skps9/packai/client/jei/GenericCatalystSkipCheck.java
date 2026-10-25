package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.List;

/** Pure skip rule for long JEI type-catalyst lists. Run with -ea / -I tmp-check.gradle. */
public final class GenericCatalystSkipCheck {
    private GenericCatalystSkipCheck() {}

    public static void main(String[] args) {
        try {
            nineCatalystsSkip();
            eightCatalystsBoundary();
            zeroCatalystsNoSkip();
            negativeControlAtLeastZeroThreshold();
        } finally {
            JeiLookup.harnessGenericCatalystSkipThresholdExclusive = -1;
        }
        System.out.println("GenericCatalystSkipCheck OK");
    }

    private static void nineCatalystsSkip() {
        assert JeiLookup.shouldSkipGenericCatalystCategory(fakeCatalysts(9));
        System.out.println("nineCatalystsSkip OK");
    }

    private static void eightCatalystsBoundary() {
        assert !JeiLookup.shouldSkipGenericCatalystCategory(fakeCatalysts(8));
        System.out.println("eightCatalystsBoundary OK");
    }

    private static void zeroCatalystsNoSkip() {
        assert !JeiLookup.shouldSkipGenericCatalystCategory(fakeCatalysts(0));
        assert !JeiLookup.shouldSkipGenericCatalystCategory((List<?>) null);
        System.out.println("zeroCatalystsNoSkip OK");
    }

    /**
     * Exclusive threshold 0 ({@code count > 0}) wrongly skips eight catalysts — proves gate is {@code > 8}.
     */
    private static void negativeControlAtLeastZeroThreshold() {
        JeiLookup.harnessGenericCatalystSkipThresholdExclusive = 0;
        boolean failed = false;
        try {
            assert !JeiLookup.shouldSkipGenericCatalystCategory(fakeCatalysts(8))
                    : "threshold >= 0 gate must not skip eight catalysts";
        } catch (AssertionError e) {
            failed = true;
        }
        assert failed : "negative control: threshold >= 0 must FAILED (toothless check)";
        System.out.println("negativeControlAtLeastZeroThreshold OK");
    }

    private static List<Object> fakeCatalysts(int n) {
        List<Object> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add("catalyst-" + i);
        }
        return out;
    }
}
