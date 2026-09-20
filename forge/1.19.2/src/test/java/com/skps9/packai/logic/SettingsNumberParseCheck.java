package com.skps9.packai.logic;

import com.skps9.packai.client.gui.settings.SettingsRegistry;

/**
 * Plan A S1: {@link SettingsRegistry#parseNumberInput} pure-function cases. Run with -ea.
 */
public final class SettingsNumberParseCheck {
    private SettingsNumberParseCheck() {}

    public static void main(String[] args) {
        assertEq(50000, SettingsRegistry.parseNumberInput("50000", 10000, 0, 100_000_000));
        assertEq(100_000_000, SettingsRegistry.parseNumberInput("999999999", 10000, 0, 100_000_000));
        assertEq(10000, SettingsRegistry.parseNumberInput("", 10000, 0, 100_000_000));
        assertEq(10000, SettingsRegistry.parseNumberInput("abc", 10000, 0, 100_000_000));
        assertEq(10000, SettingsRegistry.parseNumberInput("-5", 10000, 0, 100_000_000));
        assertEq(1000, SettingsRegistry.parseNumberInput("5", 12000, 1000, 12000));
        System.out.println("SettingsNumberParseCheck OK");
    }

    private static void assertEq(int want, int got) {
        assert want == got : "want=" + want + " got=" + got;
    }
}
